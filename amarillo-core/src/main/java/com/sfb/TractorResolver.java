package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.Game.ActionResult;
import com.sfb.utilities.MapUtils;

/**
 * Handles all tractor beam logic: pseudo-speed computation, auction resolution,
 * establish/release, and movement coupling helpers. Extracted from Game to keep
 * Game focused on state and action routing.
 */
class TractorResolver {

    private final List<Ship>    ships;
    private final List<Seeker>  seekers;
    private final List<Shuttle> activeShuttles;

    Game.PendingTractorAuction pendingTractorAuction = null;

    TractorResolver(Game game, List<Ship> ships, List<Seeker> seekers, List<Shuttle> activeShuttles) {
        this.ships          = ships;
        this.seekers        = seekers;
        this.activeShuttles = activeShuttles;
    }

    // -------------------------------------------------------------------------
    // Turn-start: speed adjustment
    // -------------------------------------------------------------------------

    void computeTractorPseudoSpeeds() {
        for (Ship holder : ships) {
            if (holder.getTractors() == null) continue;
            List<Unit> held = holder.getTractors().getTractoredUnits();
            if (held.isEmpty()) continue;
            for (Unit target : held) {
                if (!(target instanceof Ship)) {
                    // G7.5: drones/shuttles have effective speed 0 when tractored
                    target.setSpeed(0);
                    continue;
                }
                Ship heldShip = (Ship) target;
                double combined = holder.getPerformanceData().getMovementCost()
                        + heldShip.getPerformanceData().getMovementCost();
                int holderPseudo = (int) (holder.getEnergyAllocated().getWarpMovement() / combined);
                int heldPseudo   = (int) (heldShip.getEnergyAllocated().getWarpMovement() / combined);
                if (holderPseudo < holder.getSpeed())   holder.setSpeed(holderPseudo);
                if (heldPseudo   < heldShip.getSpeed()) heldShip.setSpeed(heldPseudo);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Movement coupling helper (called from Game.moveForward)
    // -------------------------------------------------------------------------

    // Returns all ships linked to mover via tractor (ships mover holds, plus
    // mover's holder and any other ships that holder holds). Does not include mover.
    List<Ship> getTractorLinkedShips(Ship mover) {
        List<Ship> linked = new ArrayList<>();
        for (Unit held : mover.getTractors().getTractoredUnits()) {
            if (held instanceof Ship) linked.add((Ship) held);
        }
        if (mover.isTractored() && mover.getTractoringUnit() instanceof Ship) {
            Ship holderShip = (Ship) mover.getTractoringUnit();
            if (!linked.contains(holderShip)) linked.add(holderShip);
            for (Unit other : holderShip.getTractors().getTractoredUnits()) {
                if (other instanceof Ship && other != mover && !linked.contains(other))
                    linked.add((Ship) other);
            }
        }
        return linked;
    }

    // -------------------------------------------------------------------------
    // Establish / release
    // -------------------------------------------------------------------------

    ActionResult establishTractor(Ship holder, String targetName, int bid) {
        if (holder.getTractors().getTractors() == 0)
            return ActionResult.fail(holder.getName() + " has no tractor beams");
        if (holder.getTractors().getAvailableTractors() == 0)
            return ActionResult.fail(holder.getName() + " has no undamaged tractor beams");
        if (bid < 1)
            return ActionResult.fail("Must bid at least 1 effective tractor point");
        if (pendingTractorAuction != null)
            return ActionResult.fail("A tractor auction is already in progress");

        Unit target = ships.stream()
                .filter(s -> s.getName().equalsIgnoreCase(targetName))
                .<Unit>map(s -> s).findFirst().orElse(null);
        if (target == null)
            target = seekers.stream()
                    .filter(s -> s instanceof Unit && ((Unit) s).getName().equalsIgnoreCase(targetName))
                    .map(s -> (Unit) s).findFirst().orElse(null);
        if (target == null)
            target = activeShuttles.stream()
                    .filter(s -> s.getName().equalsIgnoreCase(targetName))
                    .<Unit>map(s -> s).findFirst().orElse(null);
        if (target == null)
            return ActionResult.fail("Target not found: " + targetName);
        if (target == holder)
            return ActionResult.fail("Cannot tractor yourself");

        int range           = MapUtils.getRange(holder, target);
        if (range > 3)
            return ActionResult.fail("Target is out of tractor range (max 3 hexes; see G7.6)");
        int rangeMultiplier = Math.max(1, range);
        int totalEnergy     = holder.getTractors().getRemainingTractorEnergy()
                            + holder.getPowerSystems().getBatteryPower();
        int maxEffectiveBid = totalEnergy / rangeMultiplier;
        if (bid > maxEffectiveBid)
            return ActionResult.fail("Effective bid " + bid + " exceeds max of " + maxEffectiveBid
                    + " at range " + range + " (pool "
                    + holder.getTractors().getRemainingTractorEnergy() + " + battery "
                    + holder.getPowerSystems().getBatteryPower()
                    + " = " + totalEnergy + " energy / " + rangeMultiplier + ")");
        if (!holder.hasLockOn(target))
            return ActionResult.fail(holder.getName() + " does not have lock-on to " + targetName + " (G7.412)");
        if (!holder.isActiveFireControl())
            return ActionResult.fail(holder.getName() + " does not have active fire control (G7.41)");
        if (holder.getTractors().getTractoredUnits().contains(target))
            return ActionResult.fail(holder.getName() + " is already tractoring " + targetName);

        // Non-Ship targets (drones, shuttles) cannot resist — resolve immediately (G7.5)
        if (!(target instanceof Ship)) {
            spendTractorEnergy(holder, rangeMultiplier);
            holder.getTractors().linkUnit(target);
            return ActionResult.ok(holder.getName() + " tractors " + targetName
                    + (rangeMultiplier > 1 ? " at range " + range + " (G7.5/G7.6)" : " (G7.5)"));
        }

        pendingTractorAuction = new Game.PendingTractorAuction(holder, target, bid, rangeMultiplier);
        return ActionResult.ok(holder.getName() + " bids " + bid + " effective tractor"
                + (rangeMultiplier > 1 ? " (" + (bid * rangeMultiplier) + " energy at range " + range + "; G7.6)" : "")
                + " on " + targetName + " — awaiting defender response (G7.42)");
    }

    ActionResult submitNegativeTractorBid(Ship defender, int defenderNewBid) {
        if (pendingTractorAuction == null)
            return ActionResult.fail("No tractor auction in progress");
        if (pendingTractorAuction.target != defender)
            return ActionResult.fail("You are not the target of the pending tractor auction");
        if (defenderNewBid < 0)
            return ActionResult.fail("Bid cannot be negative");
        int maxDefBid = defender.getTractors().getRemainingTractorEnergy()
                      + defender.getPowerSystems().getBatteryPower();
        if (defenderNewBid > maxDefBid)
            return ActionResult.fail("Bid " + defenderNewBid + " exceeds available energy (pool "
                    + defender.getTractors().getRemainingTractorEnergy() + " + battery "
                    + defender.getPowerSystems().getBatteryPower() + ")");

        return resolveAuction(defenderNewBid);
    }

    ActionResult releaseTractor(Ship holder, String targetName) {
        Unit target = ships.stream()
                .filter(s -> s.getName().equalsIgnoreCase(targetName))
                .<Unit>map(s -> s).findFirst().orElse(null);
        if (target == null)
            target = seekers.stream()
                    .filter(s -> s instanceof Unit && ((Unit) s).getName().equalsIgnoreCase(targetName))
                    .map(s -> (Unit) s).findFirst().orElse(null);
        if (target == null)
            target = activeShuttles.stream()
                    .filter(s -> s.getName().equalsIgnoreCase(targetName))
                    .<Unit>map(s -> s).findFirst().orElse(null);
        if (target == null)
            return ActionResult.fail("Target not found: " + targetName);
        if (!holder.getTractors().getTractoredUnits().contains(target))
            return ActionResult.fail(holder.getName() + " is not tractoring " + targetName);

        holder.getTractors().releaseTractor(target);
        return ActionResult.ok(holder.getName() + " released tractor beam on " + targetName + " (G7.33)");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private ActionResult resolveAuction(int defenderNewBid) {
        Ship attacker       = pendingTractorAuction.attacker;
        Ship target         = (Ship) pendingTractorAuction.target;
        int  attackerBid    = pendingTractorAuction.attackerBid;
        int  mult           = pendingTractorAuction.rangeMultiplier;
        int  defAccumulated = target.getTractors().getNegativeTractorAccumulated();
        int  defenderTotal  = defAccumulated + defenderNewBid;

        String result;
        if (attackerBid > defenderTotal) {
            // ── ATTACKER WINS ──
            int effectiveSpend = defenderTotal + 1;
            spendTractorEnergy(attacker, effectiveSpend * mult);
            spendTractorEnergy(target,   defenderNewBid);
            target.getTractors().addNegativeTractorAccumulated(defenderNewBid);

            attacker.getTractors().linkUnit(target);
            attacker.addLockOn(target);
            target.addLockOn(attacker);

            result = attacker.getName() + " tractors " + target.getName()
                    + " (bid " + attackerBid + " vs defense " + defenderTotal
                    + "; attacker spent " + (effectiveSpend * mult) + " energy)";
        } else {
            // ── DEFENDER WINS (or ties) ──
            int defenderNeeded = Math.max(0, attackerBid - defAccumulated);
            int defenderSpend  = Math.min(defenderNewBid, defenderNeeded);

            spendTractorEnergy(attacker, attackerBid * mult);
            spendTractorEnergy(target,   defenderSpend);
            target.getTractors().addNegativeTractorAccumulated(defenderSpend);

            result = target.getName() + " resists " + attacker.getName() + "'s tractor"
                    + " (bid " + attackerBid + " vs defense "
                    + Math.max(defAccumulated, attackerBid)
                    + "; attacker spent " + (attackerBid * mult) + " energy)";
        }

        pendingTractorAuction = null;
        return ActionResult.ok(result);
    }

    private void spendTractorEnergy(Ship ship, int amount) {
        if (amount <= 0) return;
        int fromBattery = ship.getTractors().spendEnergy(amount);
        if (fromBattery > 0)
            ship.getPowerSystems().useBattery(fromBattery);
    }
}
