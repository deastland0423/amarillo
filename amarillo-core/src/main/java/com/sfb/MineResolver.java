package com.sfb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sfb.Game.ActionResult;
import com.sfb.Game.FireResult;
import com.sfb.objects.Marker;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.SpaceMine;
import com.sfb.objects.Unit;
import com.sfb.properties.Location;
import com.sfb.utilities.DiceRoller;
import com.sfb.utilities.MapUtils;

/**
 * Mine warfare (M2.0): T-bomb placement by transporter, bay-dropped mines and
 * NSMs, and per-impulse mine activation/detection/detonation. Extracted from
 * Game to keep Game focused on state and turn sequencing. Shares Game's mines,
 * ships, seekers, and prevLocations collections by reference; shield geometry
 * and damage application go through Game's public damage API.
 */
class MineResolver {

    private final Game game;
    private final List<SpaceMine> mines;
    private final List<Ship> ships;
    private final List<Seeker> seekers;
    private final List<com.sfb.objects.shuttles.Shuttle> activeShuttles;
    private final Map<Unit, Location> prevLocations;

    MineResolver(Game game, List<SpaceMine> mines, List<Ship> ships,
            List<Seeker> seekers, List<com.sfb.objects.shuttles.Shuttle> activeShuttles,
            Map<Unit, Location> prevLocations) {
        this.game           = game;
        this.mines          = mines;
        this.ships          = ships;
        this.seekers        = seekers;
        this.activeShuttles = activeShuttles;
        this.prevLocations  = prevLocations;
    }

    /**
     * Place a tBomb (real or dummy) on the map via transporter.
     *
     * <p>
     * Validates the same transporter preconditions as a Hit &amp; Run raid:
     * Activity phase, range ≤ 5, acting ship's facing shield passable, and
     * enough transporter energy. Decrements the appropriate tBomb count on
     * the acting ship.
     */
    public ActionResult placeTBomb(Ship actingShip, com.sfb.properties.Location targetHex, boolean isReal) {
        return placeTBomb(actingShip, targetHex, isReal, -1);
    }

    /**
     * @param shieldChoice -1 = auto-select first candidate (no prompt);
     *                     0 = ask player if on a seam (returns SHIELD_CHOICE
     *                     sentinel);
     *                     >0 = player's explicit choice
     */
    public ActionResult placeTBomb(Ship actingShip, com.sfb.properties.Location targetHex, boolean isReal,
            int shieldChoice) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Transporter actions can only be performed during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(actingShip);
        if (cloakBlock != null)
            return cloakBlock;
        if (actingShip.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot use transporters — breakdown lockout for 8 impulses (C6.5474)");

        // Range check — build a temporary marker at the target hex
        Marker targetMarker = new Marker();
        targetMarker.setLocation(targetHex);
        int range = MapUtils.getRange(actingShip, targetMarker);
        if (range > 5) {
            return ActionResult.fail("Target hex is out of transporter range (" + range + " hexes, max 5)");
        }

        // Transporter energy
        if (actingShip.getTransporters().availableUses() < 1) {
            return ActionResult.fail("No transporter energy available");
        }

        // Acting ship's facing shield toward target hex must be passable.
        // On a seam, the player must choose which shield to lower.
        java.util.List<Integer> candidates = game.getShieldCandidates(targetMarker, actingShip);
        int actingShieldNum;
        if (candidates.size() == 2 && shieldChoice == 0) {
            // Web client hasn't chosen yet — ask them
            return ActionResult.fail("SHIELD_CHOICE:" + candidates.get(0) + "," + candidates.get(1));
        } else if (shieldChoice > 0 && candidates.contains(shieldChoice)) {
            actingShieldNum = shieldChoice;
        } else {
            // shieldChoice == -1 (auto) or invalid: pick first candidate
            actingShieldNum = candidates.get(0);
        }
        if (!actingShip.getShields().isTransportable(actingShieldNum)) {
            boolean lowered = actingShip.getShields().lowerShield(actingShieldNum);
            if (!lowered) {
                return ActionResult.fail("Cannot lower shield #" + actingShieldNum
                        + " — must wait 8 impulses since last toggle");
            }
        }

        // Check inventory
        if (isReal && actingShip.getTBombs() < 1) {
            return ActionResult.fail("No tBombs remaining");
        }
        if (!isReal && actingShip.getDummyTBombs() < 1) {
            return ActionResult.fail("No dummy tBombs remaining");
        }

        // Spend inventory and transporter energy
        if (isReal) {
            actingShip.setTBombs(actingShip.getTBombs() - 1);
        } else {
            actingShip.setDummyTBombs(actingShip.getDummyTBombs() - 1);
        }
        actingShip.getTransporters().useTransporter();

        // Place the mine
        SpaceMine mine = SpaceMine.createTBomb(actingShip, game.getAbsoluteImpulse(), isReal, range == 1);
        mine.setLocation(targetHex);
        mines.add(mine);

        return ActionResult.ok(actingShip.getName() + " placed a "
                + (isReal ? "tBomb" : "dummy tBomb")
                + " at " + targetHex);
    }

    /**
     * Drop a mine from a shuttle bay into the ship's current hex (M2.1).
     * Works for T-Bombs (real or dummy) and NSMs. Consumes one shuttle-bay use.
     * Arming is range-based: the mine becomes active once the laying ship moves
     * more than 2 hexes away (M2.34).
     *
     * @param actingShip the ship dropping the mine
     * @param mineType   "TBOMB", "DUMMY_TBOMB", or "NSM"
     */
    public ActionResult dropMine(Ship actingShip, String mineType) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Mines can only be dropped during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(actingShip);
        if (cloakBlock != null)
            return cloakBlock;
        if (actingShip.isInPostHetWindow(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot drop mines from bay within 4 impulses of a HET (C6.38)");
        if (actingShip.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot drop mines from bay — breakdown lockout for 8 impulses (C6.5472)");

        if (actingShip.getLocation() == null)
            return ActionResult.fail("Ship has no location");

        // Find an available shuttle bay
        int currentImpulse = game.getAbsoluteImpulse();
        com.sfb.systemgroups.ShuttleBay availableBay = null;
        for (com.sfb.systemgroups.ShuttleBay bay : actingShip.getShuttles().getBays()) {
            if (bay.canLaunch(currentImpulse)) {
                availableBay = bay;
                break;
            }
        }
        if (availableBay == null)
            return ActionResult.fail("No shuttle bay available to drop a mine this impulse");

        // Check inventory and deduct
        boolean isNsm = "NSM".equalsIgnoreCase(mineType);
        boolean isDummy = "DUMMY_TBOMB".equalsIgnoreCase(mineType);
        if (isNsm) {
            if (actingShip.getNuclearSpaceMines() < 1)
                return ActionResult.fail("No nuclear space mines remaining");
            actingShip.setNuclearSpaceMines(actingShip.getNuclearSpaceMines() - 1);
        } else if (isDummy) {
            if (actingShip.getDummyTBombs() < 1)
                return ActionResult.fail("No dummy T-Bombs remaining");
            actingShip.setDummyTBombs(actingShip.getDummyTBombs() - 1);
        } else {
            if (actingShip.getTBombs() < 1)
                return ActionResult.fail("No T-Bombs remaining");
            actingShip.setTBombs(actingShip.getTBombs() - 1);
        }

        // Consume the bay slot and place the mine
        availableBay.markUsed(currentImpulse);
        SpaceMine mine = isNsm
                ? SpaceMine.createDroppedNSM(actingShip, currentImpulse)
                : SpaceMine.createDroppedTBomb(actingShip, currentImpulse, !isDummy);
        mine.setLocation(actingShip.getLocation());
        mines.add(mine);

        return ActionResult.ok(actingShip.getName() + " dropped a "
                + (isNsm ? "Nuclear Space Mine" : isDummy ? "dummy T-Bomb" : "T-Bomb")
                + " at " + actingShip.getLocation());
    }

    /**
     * Process all mines each movement phase: attempt to activate inactive mines,
     * then check active mines for detection and detonation.
     */
    List<String> processMines() {
        List<String> log = new ArrayList<>();
        if (mines.isEmpty())
            return log;

        int currentImpulse = game.getAbsoluteImpulse();
        DiceRoller dice = new DiceRoller();

        // All units currently on the map
        List<Unit> allUnits = new ArrayList<>(ships);
        for (Seeker s : seekers) {
            if (s instanceof Unit)
                allUnits.add((Unit) s);
        }
        // Shuttles trigger mines too (J3.26 treats them as such for detection;
        // J1.6223 relies on this for recovered shuttles pulled through a field)
        allUnits.addAll(activeShuttles);

        List<SpaceMine> detonated = new ArrayList<>();

        for (SpaceMine mine : mines) {
            if (mine.getLocation() == null)
                continue;

            // Try to arm inactive mines; skip detection the impulse they arm
            if (!mine.isActive()) {
                int layerRange = mine.getLayingShip() != null
                        ? MapUtils.getRange(mine, mine.getLayingShip())
                        : Integer.MAX_VALUE;
                mine.tryActivate(currentImpulse, layerRange);
                if (!mine.isActive())
                    continue;
                log.add("  " + mine.getMineType().label + " at " + mine.getLocation() + " is now ARMED");
                continue; // cannot trigger until the next impulse
            }

            // Find units within range 1
            List<Unit> inRange = new ArrayList<>();
            for (Unit unit : allUnits) {
                if (unit.getLocation() == null)
                    continue;
                if (MapUtils.getRange(mine, unit) <= 1)
                    inRange.add(unit);
            }

            if (inRange.isEmpty())
                continue;

            // Detection check — only units that moved INTO range 1 this impulse.
            // A unit standing still in range, or moving within range, does not trigger.
            boolean triggered = false;
            for (Unit unit : inRange) {
                com.sfb.properties.Location prev = prevLocations.get(unit);
                if (prev == null)
                    continue; // did not move this impulse
                if (prev != null && MapUtils.getRange(mine.getLocation(), prev) <= 1)
                    continue; // was already in range
                int roll = dice.rollOneDie();
                if (mine.detectsUnit(unit.getSpeed(), roll)) {
                    log.add("  tBomb detection: " + unit.getName()
                            + " (roll " + roll + " ≤ speed " + unit.getSpeed() + ") — TRIGGERED");
                    triggered = true;
                    break;
                } else {
                    log.add("  tBomb detection: " + unit.getName()
                            + " (roll " + roll + " > speed " + unit.getSpeed() + ") — no trigger");
                }
            }

            if (!triggered) {
                // Units in range but not detected — reveal and remove dummy if applicable
                if (!mine.isReal() && !mine.isRevealed()) {
                    mine.reveal();
                    log.add("  Dummy tBomb at " + mine.getLocation()
                            + " revealed — no explosion");
                    detonated.add(mine);
                }
                continue;
            }

            if (!mine.isReal()) {
                mine.reveal();
                log.add("  Dummy tBomb at " + mine.getLocation()
                        + " revealed — no explosion");
                detonated.add(mine);
                continue;
            }

            // Real mine — detonate
            int mineDamage = mine.getMineType().damage;
            log.add("  " + mine.getMineType().label + " DETONATED at " + mine.getLocation() + "!");
            for (Unit unit : inRange) {
                if (unit instanceof Ship) {
                    Ship ship = (Ship) unit;
                    int shieldNum = game.getShieldNumber(mine, ship);
                    FireResult result = game.markShieldDamage(ship, shieldNum, mineDamage);
                    log.add("    " + ship.getName() + " shield #" + shieldNum
                            + " hit for " + mineDamage
                            + (result.getBleed() > 0 ? "  bleed " + result.getBleed() : ""));
                } else {
                    String dmgLog = game.applyDamageToUnit(mineDamage, unit, 0);
                    log.add("    " + dmgLog);
                }
            }
            detonated.add(mine);
        }

        mines.removeAll(detonated);
        return log;
    }
}
