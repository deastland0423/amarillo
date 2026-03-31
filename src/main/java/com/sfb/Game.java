package com.sfb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.samples.SampleShips;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.Weapon;

/**
 * Authoritative game state. All mutations to ship positions, damage, and turn
 * progress go through here. The UI reads from Game and sends actions to Game —
 * it never mutates ship state directly. This separation is what will allow
 * multiplayer: actions can be sent over a network instead of applied locally.
 */
public class Game {

    /**
     * The four segments of each impulse, in order.
     * Actions are gated by the current phase: movement keys only work in MOVEMENT,
     * the fire dialog only opens in DIRECT_FIRE, etc.
     */
    public enum ImpulsePhase {
        MOVEMENT    ("Movement"),
        ACTIVITY    ("Activity"),
        DIRECT_FIRE ("Direct Fire"),
        END_OF_IMPULSE("End of Impulse");

        private final String label;
        ImpulsePhase(String label) { this.label = label; }
        public String getLabel()   { return label; }
    }

    // --- State ---
    private final List<Player>  players  = new ArrayList<>();
    private final List<Ship>    ships    = new ArrayList<>();
    private final List<Seeker>  seekers  = new ArrayList<>();
    private final Set<Ship>     movedThisImpulse     = new HashSet<>();
    private final List<PendingDamage> pendingInternalDamage = new ArrayList<>();

    private ImpulsePhase currentPhase        = ImpulsePhase.MOVEMENT;
    private List<String> lastInternalDamageLog = new ArrayList<>();
    private boolean inProgress = false;

    // --- Setup ---

    /**
     * Populate the game with two players and four sample ships.
     * Call this once before starting the impulse loop.
     */
    public void setup() {
        Player player1 = new Player();
        player1.setName("Knosset");
        player1.setFaction(Faction.Federation);

        Player player2 = new Player();
        player2.setName("Kumerian");
        player2.setFaction(Faction.Klingon);

        players.add(player1);
        players.add(player2);

        Ship fedCa = new Ship();
        fedCa.init(SampleShips.getFedCa());
        fedCa.setLocation(new Location(12, 1));
        fedCa.setFacing(13);
        fedCa.setSpeed(16);
        fedCa.setOwner(player1);
        ships.add(fedCa);
        player1.getPlayerUnits().add(fedCa);

        Ship fedFfg = new Ship();
        fedFfg.init(SampleShips.getFedFfg());
        fedFfg.setLocation(new Location(16, 1));
        fedFfg.setFacing(13);
        fedFfg.setSpeed(20);
        fedFfg.setOwner(player1);
        ships.add(fedFfg);
        player1.getPlayerUnits().add(fedFfg);

        Ship klnD7 = new Ship();
        klnD7.init(SampleShips.getD7());
        klnD7.setLocation(new Location(12, 30));
        klnD7.setFacing(1);
        klnD7.setSpeed(16);
        klnD7.setOwner(player2);
        ships.add(klnD7);
        player2.getPlayerUnits().add(klnD7);

        Ship klnF5 = new Ship();
        klnF5.init(SampleShips.getF5());
        klnF5.setLocation(new Location(16, 30));
        klnF5.setFacing(1);
        klnF5.setSpeed(20);
        klnF5.setOwner(player2);
        ships.add(klnF5);
        player2.getPlayerUnits().add(klnF5);

        TurnTracker.reset();
        inProgress = true;
        startTurn();  // run energy allocation and turn setup before impulse 1
    }

    // --- Turn progression ---

    /**
     * Energy allocation and turn setup. Each ship builds its auto allocation,
     * applies it, then calls startTurn() to set speed, shields, capacitors, etc.
     * Later this will wait for real player allocations before proceeding.
     */
    public void startTurn() {
        for (Ship ship : ships) {
            ship.allocateEnergy(ship.buildAutoAllocation());
            ship.startTurn();
        }
        TurnTracker.nextImpulse();  // advance to impulse 1
        movedThisImpulse.clear();
    }

    /**
     * End-of-turn cleanup. Resets per-turn weapon states, shield reinforcement,
     * etc. Then starts the next turn's energy allocation.
     */
    public void endTurn() {
        for (Ship ship : ships) {
            ship.cleanUp();
        }
        startTurn();
    }

    /**
     * Advance to the next phase. Cycles MOVEMENT → ACTIVITY → DIRECT_FIRE →
     * END_OF_IMPULSE, then rolls over to the next impulse (or next turn after
     * impulse 32).
     */
    public void advancePhase() {
        switch (currentPhase) {
            case MOVEMENT:
                currentPhase = ImpulsePhase.ACTIVITY;
                break;
            case ACTIVITY:
                currentPhase = ImpulsePhase.DIRECT_FIRE;
                break;
            case DIRECT_FIRE:
                resolveInternalDamage();
                currentPhase = ImpulsePhase.END_OF_IMPULSE;
                break;
            case END_OF_IMPULSE:
                // Roll over to next impulse (or next turn)
                if (TurnTracker.getLocalImpulse() >= 32) {
                    endTurn();
                } else {
                    TurnTracker.nextImpulse();
                    movedThisImpulse.clear();
                }
                currentPhase = ImpulsePhase.MOVEMENT;
                break;
        }
    }

    public ImpulsePhase getCurrentPhase() {
        return currentPhase;
    }

    public int getCurrentTurn() {
        return (TurnTracker.getImpulse() - 1) / 32 + 1;
    }

    public int getCurrentImpulse() {
        return TurnTracker.getLocalImpulse();
    }

    // --- Queries ---

    public List<Ship> getShips() {
        return ships;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public List<Seeker> getSeekers() {
        return seekers;
    }

    /**
     * Returns all ships that may move on the current impulse and have not yet
     * moved this impulse.
     */
    public List<Ship> getMovableShips() {
        List<Ship> movable = new ArrayList<>();
        int impulse = TurnTracker.getLocalImpulse();
        for (Ship ship : ships) {
            if (ship.movesThisImpulse(impulse) && !movedThisImpulse.contains(ship)) {
                movable.add(ship);
            }
        }
        return movable;
    }

    public boolean hasMovedThisImpulse(Ship ship) {
        return movedThisImpulse.contains(ship);
    }

    public boolean canMoveThisImpulse(Ship ship) {
        return currentPhase == ImpulsePhase.MOVEMENT
                && ship.movesThisImpulse(TurnTracker.getLocalImpulse())
                && !movedThisImpulse.contains(ship);
    }

    public boolean canFireThisPhase() {
        return currentPhase == ImpulsePhase.DIRECT_FIRE;
    }

    // --- Movement actions ---

    public ActionResult moveForward(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.goForward();
        if (moved) movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " moved forward")
                     : ActionResult.fail(ship.getName() + " could not move forward");
    }

    public ActionResult turnLeft(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.turnLeft();
        if (moved) movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " turned left")
                     : ActionResult.fail(ship.getName() + " cannot turn left yet (turn mode)");
    }

    public ActionResult turnRight(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.turnRight();
        if (moved) movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " turned right")
                     : ActionResult.fail(ship.getName() + " cannot turn right yet (turn mode)");
    }

    public ActionResult sideslipLeft(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.sideslipLeft();
        if (moved) movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " sideslipped left")
                     : ActionResult.fail(ship.getName() + " cannot sideslip (must move first)");
    }

    public ActionResult sideslipRight(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.sideslipRight();
        if (moved) movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " sideslipped right")
                     : ActionResult.fail(ship.getName() + " cannot sideslip (must move first)");
    }

    // --- Weapons fire ---

    /**
     * Returns all weapons on the attacker that bear on the target.
     */
    public List<Weapon> getBearingWeapons(Ship attacker, Ship target) {
        return attacker.fetchAllBearingWeapons(target);
    }

    /**
     * Compute the range between two ships.
     */
    public int getRange(Ship attacker, Ship target) {
        return MapUtils.getRange(attacker, target);
    }

    /**
     * Compute which shield number on the target is facing the attacker.
     */
    public int getShieldNumber(Ship attacker, Ship target) {
        int shieldFacing = target.getRelativeShieldFacing(attacker);
        int shieldNumber = (shieldFacing % 2 == 0) ? shieldFacing / 2 : (shieldFacing + 1) / 2;
        return Math.max(1, Math.min(6, shieldNumber));
    }

    /**
     * Mark shield damage from one firing volley (6D2 — Direct-Fire Weapons Fire Stage).
     * Bleed-through is queued as pending internal damage; it will not be resolved
     * until resolveInternalDamage() is called at the end of the Direct-Fire segment (6D4).
     *
     * @return A FireResult with the bleed-through amount and an empty internal log
     *         (log is populated later when resolveInternalDamage() runs).
     */
    public FireResult markShieldDamage(Ship target, int shieldNumber, int totalDamage) {
        int bleed = target.damageShield(shieldNumber, totalDamage);
        if (bleed > 0) {
            pendingInternalDamage.add(new PendingDamage(target, bleed));
        }
        return new FireResult(bleed, new ArrayList<>());
    }

    /**
     * Resolve all queued internal damage (6D4 — Direct-Fire Weapons Damage Resolution Stage).
     * Called automatically when advancePhase() moves out of DIRECT_FIRE.
     */
    private void resolveInternalDamage() {
        lastInternalDamageLog = new ArrayList<>();
        for (PendingDamage pd : pendingInternalDamage) {
            List<String> entries = pd.target.applyInternalDamage(pd.bleed);
            lastInternalDamageLog.add("=== Internal damage — " + pd.target.getName() + " ===");
            lastInternalDamageLog.addAll(entries);
        }
        pendingInternalDamage.clear();
    }

    /**
     * Returns the internal damage log from the most recent resolution step.
     * The UI should read this after advancing past DIRECT_FIRE.
     */
    public List<String> getLastInternalDamageLog() {
        return lastInternalDamageLog;
    }

    // --- Status ---

    public boolean isInProgress() {
        return inProgress;
    }

    // -------------------------------------------------------------------------
    // Nested result types — simple value holders the UI can read without
    // needing to reach into game state directly.
    // -------------------------------------------------------------------------

    /**
     * The result of a movement or other action attempt.
     */
    public static class ActionResult {
        private final boolean success;
        private final String  message;

        private ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ActionResult ok(String message)   { return new ActionResult(true,  message); }
        public static ActionResult fail(String message) { return new ActionResult(false, message); }

        public boolean isSuccess() { return success; }
        public String  getMessage() { return message; }
    }

    /**
     * Bleed-through damage waiting to be resolved at end of Direct-Fire segment (6D4).
     */
    private static class PendingDamage {
        final Ship   target;
        final int    bleed;
        PendingDamage(Ship target, int bleed) {
            this.target = target;
            this.bleed  = bleed;
        }
    }

    /**
     * The result of applying damage: bleed-through amount and internal damage log.
     */
    public static class FireResult {
        private final int          bleed;
        private final List<String> internalLog;

        public FireResult(int bleed, List<String> internalLog) {
            this.bleed       = bleed;
            this.internalLog = internalLog;
        }

        public int          getBleed()       { return bleed; }
        public List<String> getInternalLog() { return internalLog; }
    }
}
