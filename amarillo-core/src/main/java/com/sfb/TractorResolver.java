package com.sfb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.Game.ActionResult;
import com.sfb.utilities.MapUtils;

/**
 * Handles all tractor beam logic: pseudo-speed computation, auction resolution,
 * establish/release, and movement coupling helpers. Extracted from Game to keep
 * Game focused on state and action routing.
 */
class TractorResolver {

    private final Game           game;
    private final List<Ship>     ships;
    private final List<Seeker>   seekers;
    private final List<Shuttle>  activeShuttles;
    // Game's pre-move-location map — rotation must record moves here so
    // processMines() can see rotated units as having entered a mine's radius.
    private final Map<Unit, Location> prevLocations;
    private final Set<String>    rotatedThisTurn = new HashSet<>();

    Game.PendingTractorAuction pendingTractorAuction = null;

    TractorResolver(Game game, List<Ship> ships, List<Seeker> seekers, List<Shuttle> activeShuttles,
                    Map<Unit, Location> prevLocations) {
        this.game           = game;
        this.ships          = ships;
        this.seekers        = seekers;
        this.activeShuttles = activeShuttles;
        this.prevLocations  = prevLocations;
    }

    void clearRotations() {
        rotatedThisTurn.clear();
    }

    /**
     * Self-healing sweep, run at the end of every phase advance: drop any
     * tractor link whose held unit has left play (impacted, shot down,
     * expired, off-map) — regardless of which code path removed it. The beam
     * stays used for the turn (G7.13). Held SHIPS are additionally handled at
     * their remove-from-play sites via releaseAllLinksInvolving (G7.28).
     */
    List<String> releaseDeadLinks() {
        List<String> log = new ArrayList<>();
        for (Ship holder : ships) {
            if (holder.getTractors() == null)
                continue;
            for (Unit held : new ArrayList<>(holder.getTractors().getTractoredUnits())) {
                boolean inPlay = held.getLocation() != null
                        && (held instanceof Ship
                                ? ships.contains(held)
                                : seekers.stream().anyMatch(s -> s == held)
                                        || activeShuttles.stream().anyMatch(s -> s == held));
                if (!inPlay) {
                    holder.getTractors().releaseTractor(held);
                    log.add(holder.getName() + " tractor link released — "
                            + held.getName() + " is no longer in play");
                }
            }
        }
        return log;
    }

    /** Drop every tractor link holding the given unit — it is leaving play. */
    void releaseLinksHolding(Unit gone) {
        for (Ship holder : ships) {
            if (holder.getTractors() == null)
                continue;
            if (holder.getTractors().getTractoredUnits().contains(gone))
                holder.getTractors().releaseTractor(gone);
        }
    }

    /** True if any ship currently holds at least one unit in a tractor beam. */
    boolean anyTractorLinksExist() {
        for (Ship s : ships) {
            if (s.getTractors() != null && !s.getTractors().getTractoredUnits().isEmpty())
                return true;
        }
        return false;
    }

    /**
     * Turn-start link maintenance (G7.42, simplified): links persist across the
     * turn boundary, and the holder must pay 1 effective tractor point (× range
     * multiplier, G7.6) per held unit from the new turn's pool or the link is
     * released. The defender's counter-auction (full G7.42) is not yet implemented.
     * Links to vanished units (null location) or beyond range 3 are released.
     */
    List<String> maintainLinksAtTurnStart() {
        List<String> log = new ArrayList<>();
        for (Ship holder : ships) {
            if (holder.getTractors() == null) continue;
            for (Unit held : new ArrayList<>(holder.getTractors().getTractoredUnits())) {
                int range = (holder.getLocation() == null || held.getLocation() == null)
                        ? Integer.MAX_VALUE
                        : MapUtils.getRange(holder.getLocation(), held.getLocation());
                if (range > 3) {
                    holder.getTractors().releaseTractor(held);
                    log.add("  " + holder.getName() + "'s tractor on " + held.getName()
                            + " released — target out of range");
                    continue;
                }
                int cost      = Math.max(1, range);
                int available = holder.getTractors().getRemainingTractorEnergy()
                              + holder.getPowerSystems().getBatteryPower();
                if (available >= cost) {
                    spendTractorEnergy(holder, cost);
                    log.add("  " + holder.getName() + " maintains tractor on " + held.getName()
                            + " (" + cost + " energy; G7.42)");
                } else {
                    holder.getTractors().releaseTractor(held);
                    log.add("  " + holder.getName() + " cannot pay " + cost
                            + " to maintain tractor on " + held.getName() + " — link released (G7.42)");
                }
            }
        }
        return log;
    }

    /**
     * Break every tractor link involving a ship leaving play (destroyed,
     * disengaged, or conceded) — both links it holds and links held on it.
     */
    void releaseAllLinksInvolving(Ship gone) {
        if (gone.getTractors() != null) {
            for (Unit held : new ArrayList<>(gone.getTractors().getTractoredUnits()))
                gone.getTractors().releaseTractor(held);
        }
        for (Ship s : ships) {
            if (s == gone || s.getTractors() == null) continue;
            if (s.getTractors().getTractoredUnits().contains(gone))
                s.getTractors().releaseTractor(gone);
        }
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
                    // G7.5: held drones/shuttles cannot move themselves — enforced by
                    // the isTractored() guards in SeekerMover/ShuttleMover, NOT by
                    // zeroing speed here (which was never restored on release,
                    // leaving freed drones dead in space).
                    continue;
                }
                Ship heldShip = (Ship) target;
                double combined = holder.getPerformanceData().getMovementCost()
                        + heldShip.getPerformanceData().getMovementCost();
                int holderPseudo = (int) (holder.getEnergyAllocated().getWarpMovement() / combined);
                int heldPseudo   = (int) (heldShip.getEnergyAllocated().getWarpMovement() / combined);
                // Remember the plotted speed so the UI can show "16 (8)"
                if (holderPseudo < holder.getSpeed()) {
                    if (holder.getTractorTrueSpeed() < 0)
                        holder.setTractorTrueSpeed(holder.getSpeed());
                    holder.setSpeed(holderPseudo);
                }
                if (heldPseudo < heldShip.getSpeed()) {
                    if (heldShip.getTractorTrueSpeed() < 0)
                        heldShip.setTractorTrueSpeed(heldShip.getSpeed());
                    heldShip.setSpeed(heldPseudo);
                }
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
        // G13: an operating cloak precludes tractor use (in any fade state —
        // the FC check below only covers it once FC has actually dropped)
        ActionResult cloakBlock = game.cloakActionBlock(holder);
        if (cloakBlock != null)
            return cloakBlock;
        if (holder.getTractors().getTractors() == 0)
            return ActionResult.fail(holder.getName() + " has no tractor beams");
        if (holder.getTractors().getAvailableTractors() == 0)
            return ActionResult.fail(holder.getName() + " has no undamaged tractor beams");
        if (holder.getTractors().getBeamsAvailableThisTurn() <= 0)
            return ActionResult.fail(holder.getName()
                    + " has no unused tractor beams remaining this turn (G7.13)");
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
    // G7.7 Rotation (Initial Activity Phase)
    // -------------------------------------------------------------------------

    ActionResult rotateTractored(Ship holder, String targetName, int destCol, int destRow) {
        // G13: rotation is active tractor use — blocked while the cloak operates
        ActionResult cloakBlock = game.cloakActionBlock(holder);
        if (cloakBlock != null)
            return cloakBlock;
        if (holder.getTractors() == null)
            return ActionResult.fail(holder.getName() + " has no tractor system");

        Unit target = holder.getTractors().getTractoredUnits().stream()
                .filter(u -> u.getName().equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (target == null)
            return ActionResult.fail(holder.getName() + " is not tractoring " + targetName);

        if (rotatedThisTurn.contains(target.getName()))
            return ActionResult.fail(targetName + " has already been rotated this turn (G7.713)");

        // G7.716: cannot rotate a unit held by multiple ships
        long holderCount = ships.stream()
                .filter(s -> s.getTractors() != null && s.getTractors().getTractoredUnits().contains(target))
                .count();
        if (holderCount > 1)
            return ActionResult.fail(targetName + " is held by multiple ships — cannot rotate (G7.716)");

        // Validate destination bounds
        if (destCol < 1 || destRow < 1 || destCol > game.getMapCols() || destRow > game.getMapRows())
            return ActionResult.fail("Destination " + destCol + "|" + destRow + " is off the map (G7.715)");

        Location destHex = new Location(destCol, destRow);

        if (game.isPlanetHex(destHex))
            return ActionResult.fail("Cannot rotate into a planet hex (G7.715)");

        // Destination must be adjacent to the target (exactly 1 hex)
        int distToTarget = MapUtils.getRange(target.getLocation(), destHex);
        if (distToTarget != 1)
            return ActionResult.fail("Destination must be adjacent to " + targetName
                    + " (range " + distToTarget + " — G7.711)");

        // Destination must remain within tractor range of holder (≤3)
        int distToHolder = MapUtils.getRange(holder.getLocation(), destHex);
        if (distToHolder > 3)
            return ActionResult.fail("Destination is out of tractor range from "
                    + holder.getName() + " (G7.714)");

        // Energy cost: 3 × the POST-rotation range multiplier (G7.711/G7.712) —
        // the beam must cover the new distance, so pushing farther costs more and
        // pulling closer costs less. Range 0 and range 1 are the same strength.
        int rangeMultiplier = Math.max(1, distToHolder);
        int energyCost      = 3 * rangeMultiplier;
        int available       = holder.getTractors().getRemainingTractorEnergy()
                            + holder.getPowerSystems().getBatteryPower();
        if (available < energyCost)
            return ActionResult.fail("Need " + energyCost + " tractor energy to rotate to range "
                    + distToHolder + ", have " + available + " (G7.711/G7.712)");

        // The destination is adjacent (validated above), so exactly one of the six
        // hex bearings leads from the target's hex to it. Held units must move in
        // that same direction — a raw column/row delta is NOT shape-preserving on
        // this offset hex grid when the held unit sits in a different column parity.
        Location fromHex = target.getLocation();
        int rotationDir = -1;
        for (int dir : new int[] { 1, 5, 9, 13, 17, 21 }) {
            if (destHex.equals(MapUtils.getAdjacentHex(fromHex, dir))) {
                rotationDir = dir;
                break;
            }
        }

        prevLocations.putIfAbsent(target, fromHex); // mines must see this as movement
        target.setLocation(destHex);
        spendTractorEnergy(holder, energyCost);
        rotatedThisTurn.add(target.getName());

        // G7.717: small units (drones/shuttles) tractored by the rotated ship
        // maintain their relative position — move each one hex in the rotation direction.
        StringBuilder msg = new StringBuilder(holder.getName() + " rotated " + targetName
                + " → " + destHex + " (cost " + energyCost + " energy; G7.711)");
        if (target instanceof Ship && rotationDir != -1) {
            Ship targetShip = (Ship) target;
            if (targetShip.getTractors() != null) {
                for (Unit held : new ArrayList<>(targetShip.getTractors().getTractoredUnits())) {
                    if (held instanceof Ship) continue; // ships keep their own hex (not dragged)
                    if (held.getLocation() == null) continue;
                    Location newHeldLoc = MapUtils.getAdjacentHex(
                            held.getLocation(), rotationDir, game.getMapCols(), game.getMapRows());
                    if (newHeldLoc == null || game.isPlanetHex(newHeldLoc)) {
                        // Same fate as being tractor-dragged there by movement (G7.274):
                        // the unit is destroyed, the link released, and the log says so
                        targetShip.getTractors().releaseTractor(held);
                        if (held instanceof com.sfb.objects.Seeker)
                            game.removeSeekerFromPlay((com.sfb.objects.Seeker) held);
                        else if (held instanceof Shuttle)
                            game.removeShuttleFromPlay((Shuttle) held, "target destroyed");
                        else
                            held.setLocation(null);
                        msg.append("; ").append(held.getName())
                           .append(newHeldLoc == null ? " rotated off map — destroyed"
                                                      : " rotated into planet — destroyed");
                    } else {
                        prevLocations.putIfAbsent(held, held.getLocation());
                        held.setLocation(newHeldLoc);
                        msg.append("; ").append(held.getName()).append(" moved with it");
                    }
                }
            }
        }

        return ActionResult.ok(msg.toString());
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
