package com.sfb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sfb.Game.ActionResult;
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
        // C10.514 bars laying a mine and C10.521 bars the transporter that lays this one, so EM
        // stops a T-bomb twice over. The MINE clause is named because it is the specific one,
        // and because the rulebook misdirects here: M2.11 cites "(C10.53)", which is DOCKING.
        ActionResult emBlock = game.emActionBlock(actingShip, "lay a mine", "C10.514");
        if (emBlock != null)
            return emBlock;
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
        if (actingShip.getTransporters().availableUses(game.getAbsoluteImpulse()) < 1) {
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
        // The energy limit is real now: this used to call useTransporter() and throw the
        // answer away, so a ship with no transporter energy laid mines regardless.
        if (!game.spendTransporterEnergy(actingShip, 1))
            return ActionResult.fail(actingShip.getName()
                    + " has no transporter energy left, and no battery power to draw on");

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
        // C10.514. This is the path a mine RACK will also use (M9.0 lays "in the hex occupied by
        // the ship"), so the guard is in place before the racks arrive rather than after.
        ActionResult emBlock = game.emActionBlock(actingShip, "lay a mine", "C10.514");
        if (emBlock != null)
            return emBlock;
        if (actingShip.isInPostHetWindow(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot drop mines from bay within 4 impulses of a HET (C6.38)");
        if (actingShip.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot drop mines from bay — breakdown lockout for 8 impulses (C6.5472)");

        if (actingShip.getLocation() == null)
            return ActionResult.fail("Ship has no location");

        int currentImpulse = game.getAbsoluteImpulse();
        int currentTurn = game.getClock().getTurn();
        boolean isNsm = "NSM".equalsIgnoreCase(mineType);
        boolean isDummy = "DUMMY_TBOMB".equalsIgnoreCase(mineType);

        // (M9.21) THE RATE, and it splits on the SHIP rather than on the rack.
        //
        // "Ships can lay one mine from each mine rack during each turn. Only minesweepers and
        // minelayers use these rates to lay mines. Other ships converted (S7.0) to carry mine
        // racks drop mines through the shuttle hatch at the rate of one per turn per shuttle
        // bay, regardless of the number of racks in the bay."
        //
        // So for a minesweeper or minelayer a rack IS a launcher: no bay is consumed, no
        // quarter-turn spacing, no M2.111 conflict with launching a shuttle. For everyone else
        // -- including an S7.0 conversion, which M9.16 says is "not considered a minesweeper for
        // any purpose" -- the rack is only an inventory and the hatch is the bottleneck. The
        // owner ruled (2026-10-19) that the Romulan large Q-ship, being neither a minesweeper
        // (M2.78) nor a minelayer (M2.79), takes the bay rate: its two racks hold eight NSMs and
        // it can still only put one of them over the side per turn.
        com.sfb.systemgroups.MineRack sourceRack = null;
        if (actingShip.hasMineRacks()) {
            boolean perRack = actingShip.laysOneMinePerRack();
            boolean anyHeldIt = false;
            boolean anyFreeThisTurn = false;
            for (com.sfb.systemgroups.MineRack rack : actingShip.getMineRacks()) {
                if (rack.isDestroyed() || rack.isOutOfService(currentTurn))
                    continue;
                if (!rack.has(isNsm, isDummy))
                    continue;
                anyHeldIt = true;
                if (perRack && rack.laidThisTurn(currentTurn))
                    continue;
                anyFreeThisTurn = true;
                sourceRack = rack;
                break;
            }
            if (sourceRack == null && anyHeldIt && !anyFreeThisTurn)
                return ActionResult.fail(actingShip.getName()
                        + " has already laid a mine from every loaded rack this turn (M9.21)");
        }

        // (M9.21) The converted hull's limit is "one per turn per shuttle bay, REGARDLESS of the
        // number of racks in the bay" -- a per-TURN rate, where the hatch cooldown below is only
        // two impulses. Without this a one-bay hull could put sixteen mines over the side in a
        // turn and satisfy every other check on the way.
        //
        // Counted per ship rather than per rack, because the rule counts bays: two racks behind
        // one bay is one mine a turn, from whichever rack. The minelayer's rate is the other
        // branch entirely and is capped by the racks themselves, so this does not apply to it.
        if (sourceRack != null && !actingShip.laysOneMinePerRack()) {
            int bays = actingShip.getShuttles().getBays().size();
            if (actingShip.rackMinesLaidOn(currentTurn) >= bays)
                return ActionResult.fail(actingShip.getName() + " has already laid "
                        + actingShip.rackMinesLaidOn(currentTurn)
                        + " mine" + (actingShip.rackMinesLaidOn(currentTurn) == 1 ? "" : "s")
                        + " from its racks this turn, one for each shuttle bay (M9.21)");
        }

        // The hatch, which a minesweeper or minelayer firing its own rack does not use.
        boolean needsBay = sourceRack == null || !actingShip.laysOneMinePerRack();
        com.sfb.systemgroups.ShuttleBay availableBay = null;
        if (needsBay) {
            for (com.sfb.systemgroups.ShuttleBay bay : actingShip.getShuttles().getBays()) {
                if (bay.canLaunch(currentImpulse)) {
                    availableBay = bay;
                    break;
                }
            }
            if (availableBay == null)
                return ActionResult.fail("No shuttle bay available to drop a mine this impulse");
        }

        // Check inventory and deduct. A rack is drawn on FIRST where one holds the mine: M9.12
        // gives rack mines no reloads, so rack stock is the scarcer kind and spending the loose
        // allocation first would strand it.
        if (sourceRack != null) {
            sourceRack.takeOne(isNsm, isDummy, currentTurn);
            if (!actingShip.laysOneMinePerRack())
                actingShip.recordRackMineLaid(currentTurn); // M9.21's per-bay-per-turn tally
        } else if (isNsm) {
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

        // Consume the bay slot, where one was needed, and place the mine
        if (availableBay != null)
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
                // M2.32 DESTROYED MINELAYER: "If the laying unit is destroyed or displaced, it
                // is no longer within the safety range and the mine becomes armed at that
                // point." A range of MAX_VALUE is how that is said here — the mine outlives its
                // layer and arms rather than waiting forever for a ship that will never move
                // away. The saved-game format leans on this: a restored mine whose layer did
                // not survive the battle resolves to null and is NOT reported as a problem.
                int layerRange = mine.getLayingShip() != null
                        ? MapUtils.getRange(mine, mine.getLayingShip())
                        : Integer.MAX_VALUE;
                mine.tryActivate(currentImpulse, layerRange);
                if (!mine.isActive())
                    continue;
                log.add("  " + mine.getMineType().label + " at " + mine.getLocation() + " is now ARMED");
                continue; // cannot trigger until the next impulse
            }

            // Find units within range 1 that this mine will even look at.
            //
            // (M2.14) A mine with size-class settings ignores every other class outright — the
            // rule's own example is a mine "set to ignore drones but accept ships, thereby
            // preventing the enemy from clearing the mines without risk by detonating them with
            // drones". So the filter belongs HERE, before the detection roll: an ignored unit
            // does not get a roll it could fail, and must not consume the trigger either.
            // Empty settings accept everything (M2.141), which is every mine laid in play.
            List<Unit> inRange = new ArrayList<>();
            for (Unit unit : allUnits) {
                if (unit.getLocation() == null)
                    continue;
                if (!mine.accepts(unit.getSizeClass()))
                    continue;
                if (MapUtils.getRange(mine, unit) <= 1)
                    inRange.add(unit);
            }

            if (inRange.isEmpty())
                continue;

            // Detection check — any unit that MOVED this impulse while within the
            // detection range (M2.5). This includes a unit that enters range AND
            // one that moves from one in-range hex to another (e.g. sliding past
            // an adjacent mine): each impulse it moves in range earns a detection
            // roll. A unit that did not move this impulse does not trigger.
            boolean triggered = false;
            for (Unit unit : inRange) {
                com.sfb.properties.Location prev = prevLocations.get(unit);
                if (prev == null)
                    continue; // did not move this impulse — stationary units don't trigger
                int roll = dice.rollOneDie();
                /*
                 * C10.46/C2.451: a mine reacts to EFFECTIVE speed, which folds in the cost of
                 * Erratic Maneuvers. C10.461 then falls straight out of the arithmetic rather
                 * than needing a rule of its own: a mine triggers on a roll BELOW the effective
                 * speed (M2.40), so seven or more is automatic — and six is exactly what EM adds
                 * to a non-nimble ship. At any practical speed above zero it therefore "will
                 * always trigger a mine", exactly as the rule says, "because the six points of EM
                 * movement energy are added to speed for this purpose". A nimble ship adds three
                 * (C10.462) and a shuttle one (C10.463), both of which merely shorten the odds —
                 * which is the rule's own point that "either nimbleness or cloaking may protect a
                 * slow ship from detonating every mine".
                 *
                 * Speed zero needs no special case: a stationary unit never ENTERS a mine hex.
                 */
                int speed = unit.effectiveSpeed();
                if (mine.detectsUnit(speed, roll)) {
                    log.add("  " + mine.getMineType().label + " detection: " + unit.getName()
                            + " (roll " + roll + " < speed " + speed + ") — TRIGGERED");
                    triggered = true;
                    break;
                } else {
                    log.add("  " + mine.getMineType().label + " detection: " + unit.getName()
                            + " (roll " + roll + " ≥ speed " + speed
                            + ") — detected in time and avoided");
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
            // The blast itself is (D5.41) and is shared with an exploding ship, which applies
            // damage in the same words -- on the shield facing the thing that went off. A mine
            // is always radius one; a ship explosion is radius zero or one by its strength.
            for (Ship ship : game.applyBlast(mine, 1, mineDamage, log)) {
                // G13.552 is written for MINE explosions specifically -- "a cloaked ship is
                // exposed and is locked onto during the impulse in which it is inside the area
                // of a mine explosion" -- so it stays with the mine rather than moving into the
                // shared blast. (D5.42) ignores the cloak for blast EFFECTS either way, but says
                // nothing about exposing the ship.
                log.addAll(game.flashcubeLockOn(ship));
            }
            detonated.add(mine);
        }

        mines.removeAll(detonated);
        return log;
    }
}
