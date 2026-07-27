package com.sfb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sfb.commands.Command;
import com.sfb.scenario.CoiLoadout;
import com.sfb.scenario.ScenarioLoader;
import com.sfb.scenario.ScenarioSpec;

import com.sfb.objects.Drone;
import com.sfb.objects.Marker;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.SpaceMine;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.objects.Unit;
import com.sfb.properties.TerrainType;
import com.sfb.systemgroups.Energy;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.properties.RetrievalMethod;
import com.sfb.properties.SystemTarget;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaLauncher;
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
        INITIAL_ACTIVITY("Initial Activity"),
        MOVEMENT("Movement"),
        ACTIVITY("Activity"),
        DIRECT_FIRE("Direct Fire"),
        REINFORCEMENT("Reinforcement"),
        DAC_CHOICE("DAC Choice"),
        CONTROL_OVERFLOW("Control Overflow"),
        END_OF_IMPULSE("End of Impulse");

        private final String label;

        ImpulsePhase(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // --- State ---
    // Per-game impulse clock. Deep system references are injected via
    // Ship.attachClock() in startTurn(); resolvers read it through the public
    // getters. Never a static — see TurnTracker javadoc.
    private final TurnTracker clock = new TurnTracker();

    private final List<Player> players = new ArrayList<>();
    private int mapCols = 42; // map width in hexes
    private int mapRows = 32; // map height in hexes
    private int maxTurns = 0; // 0 = no turn limit
    private Map<String, Set<String>> destructionEdgesByTeam = new HashMap<>(); // teamName → destruction edges
    private Map<String, Set<String>> destructionDirectionsByTeam = new HashMap<>(); // teamName → destruction directions
                                                                                    // (A–F) for accel disengage
    private GameEndResult gameEndResult = null;

    private final List<Ship> ships = new ArrayList<>();
    private final List<Ship> destroyedShips = new ArrayList<>(); // ships removed from play; kept for VP scoring
    private final List<Ship> pendingAccelDisengage = new ArrayList<>(); // ships awaiting player YES/NO at end of turn
                                                                        // (C7.1)
    private final List<Seeker> seekers = new ArrayList<>();
    private final List<Ship> capturedThisTurn = new ArrayList<>(); // ships captured in the current endTurn()
    private int seekerSeq = 0; // monotonic counter for unique seeker names
    // Secondary-effect lines from unit removal (chasers losing tracking);
    // drained into the next advancePhase() log
    private final List<String> removalLog = new ArrayList<>();
    private final List<com.sfb.objects.shuttles.Shuttle> activeShuttles = new ArrayList<>(); // non-seeker shuttles on
                                                                                             // the map
    private final List<SpaceMine> mines = new ArrayList<>();
    private final List<Terrain> terrain = new ArrayList<>();
    private final List<com.sfb.objects.Objective> objectives = new ArrayList<>(); // capturable scenario objects
    private final Set<Location> asteroidHexes = new HashSet<>();
    private final Set<Location> planetHexes = new HashSet<>();          // full footprint — no-entry
    private final Set<Location> planetSurfaceHexes = new HashSet<>();    // blocks LOS (P2.321)
    private final Set<Location> planetAtmosphereHexes = new HashSet<>(); // large-giant outer ring (P2.222)
    private final Set<Location> ringHexes = new HashSet<>();             // planetary rings — enterable (P2.223)

    static final int[][] ASTEROID_DAMAGE = {
            // Speed bracket: 0=1-6, 1=7-14, 2=15-25, 3=26+ (P3.2)
            { 0, 0, 0, 0 }, // die 1
            { 0, 0, 0, 5 }, // die 2
            { 0, 0, 3, 10 }, // die 3
            { 0, 2, 6, 15 }, // die 4
            { 0, 6, 10, 20 }, // die 5
            { 0, 10, 15, 30 }, // die 6
    };

    static final int[][] RING_DAMAGE = {
            // Ring Material Damage Table (P2.223) — lighter than asteroids at
            // low speed, comparable at high. Speed brackets as ASTEROID_DAMAGE.
            { 0, 0, 0, 0 },  // die 1
            { 0, 0, 0, 2 },  // die 2
            { 0, 0, 1, 5 },  // die 3
            { 0, 1, 3, 7 },  // die 4
            { 0, 3, 5, 10 }, // die 5
            { 0, 5, 7, 15 }, // die 6
    };
    private final Set<Ship> movedThisImpulse = new HashSet<>();
    // Pre-move location of each unit that moved this impulse — used by
    // processMines()
    // to detect units that crossed INTO range 1 (as opposed to standing still
    // there).
    private final Map<Unit, com.sfb.properties.Location> prevLocations = new HashMap<>();
    private final Set<com.sfb.objects.shuttles.Shuttle> movedShuttlesThisImpulse = new HashSet<>();
    private final List<PendingDamage> pendingInternalDamage = new ArrayList<>();
    private final List<PendingVolley> pendingVolleys = new ArrayList<>();
    private final SeekerMover seekerMover = new SeekerMover(this, seekers, activeShuttles, pendingVolleys,
            prevLocations);
    private final ShuttleMover shuttleMover = new ShuttleMover(this, activeShuttles, prevLocations);
    private final TractorResolver tractorResolver = new TractorResolver(this, ships, seekers, activeShuttles,
            prevLocations);
    private final Set<String> firedPairsThisPhase = new HashSet<>();
    private ImpulsePhase reinforcementReturnPhase = ImpulsePhase.ACTIVITY;
    private ImpulsePhase dacChoiceReturnPhase = ImpulsePhase.ACTIVITY;
    private ImpulsePhase controlOverflowReturnPhase = ImpulsePhase.ACTIVITY;
    private final List<PendingDacChoice> pendingDacChoices = new ArrayList<>();
    private final List<PendingControlOverflow> pendingControlOverflows = new ArrayList<>();
    // UIM: tracks which disruptors on each ship fired under UIM this impulse.
    // Burnout is rolled once per ship at END_OF_IMPULSE (6E), not per firing.
    private final Map<Ship, List<com.sfb.weapons.Disruptor>> uimUsedThisImpulse = new HashMap<>();
    private final DamageResolver damageResolver = new DamageResolver(this, seekers, activeShuttles,
            pendingVolleys, pendingInternalDamage, pendingDacChoices, firedPairsThisPhase, uimUsedThisImpulse);
    private final BoardingResolver boardingResolver = new BoardingResolver(this, seekers, capturedThisTurn);
    private final LaunchCoordinator launchCoordinator = new LaunchCoordinator(this, seekers, activeShuttles);
    private final MineResolver mineResolver = new MineResolver(this, mines, ships, seekers, activeShuttles,
            prevLocations);
    private final SeekerControl seekerControl = new SeekerControl(this, ships, seekers);
    private final LockOnResolver lockOnResolver = new LockOnResolver(this, ships, seekers, activeShuttles);
    private final ShipMover shipMover = new ShipMover(this, ships, seekers, activeShuttles,
            movedThisImpulse, prevLocations, movedShuttlesThisImpulse, destroyedShips,
            destructionEdgesByTeam, pendingInternalDamage, tractorResolver, seekerMover);
    private final DisengagementResolver disengagementResolver = new DisengagementResolver(this, ships,
            seekers, destroyedShips, pendingAccelDisengage, destructionDirectionsByTeam, tractorResolver);

    public static class PendingTractorAuction {
        public final Ship attacker;
        public final Unit target; // always a Ship; non-Ship targets resolved immediately
        public final int attackerBid; // effective tractor points bid
        public final int rangeMultiplier; // 1 for range 0-1; 2 for range 2; 3 for range 3 (G7.6)

        PendingTractorAuction(Ship attacker, Unit target, int bid, int rangeMultiplier) {
            this.attacker = attacker;
            this.target = target;
            this.attackerBid = bid;
            this.rangeMultiplier = rangeMultiplier;
        }
    }

    public PendingTractorAuction getPendingTractorAuction() {
        return tractorResolver.pendingTractorAuction;
    }

    private ImpulsePhase currentPhase = ImpulsePhase.MOVEMENT;
    private List<String> lastInternalDamageLog = new ArrayList<>();
    private List<String> lastSeekerLog = new ArrayList<>();
    private boolean inProgress = false;
    private boolean awaitingAllocation = false;
    private final List<Ship> allocationQueue = new ArrayList<>();

    // --- Setup ---

    /**
     * /**
     * Populate the game from a ScenarioSpec with pre-built ships and COI loadouts.
     *
     * Intended flow:
     * 1. Call ScenarioLoader.loadShips(spec) to build ships.
     * 2. Show the COI dialog against those ships to collect loadouts.
     * 3. Call this method — COI is applied, players registered, game starts.
     *
     * @param scenario    the scenario specification
     * @param sideShips   pre-built ships grouped by side (same order as
     *                    scenario.sides)
     * @param coiLoadouts COI selections per ship; ships absent from the map get no
     *                    COI applied
     */
    public void setupFromScenario(ScenarioSpec scenario,
            List<List<Ship>> sideShips,
            Map<Ship, CoiLoadout> coiLoadouts) {
        mapCols = scenario.mapCols > 0 ? scenario.mapCols : 42;
        mapRows = scenario.mapRows > 0 ? scenario.mapRows : 32;
        maxTurns = scenario.maxTurns >= 0 ? scenario.maxTurns : 0;
        destructionEdgesByTeam.clear();
        destructionDirectionsByTeam.clear();
        if (scenario.sides != null) {
            for (ScenarioSpec.SideSpec side : scenario.sides) {
                if (side.destructionEdges != null && !side.destructionEdges.isEmpty())
                    destructionEdgesByTeam.put(side.name, side.destructionEdges);
                if (side.destructionDirections != null && !side.destructionDirections.isEmpty())
                    destructionDirectionsByTeam.put(side.name, side.destructionDirections);
            }
        }

        ships.clear();
        players.clear();
        seekers.clear();
        activeShuttles.clear();
        mines.clear();
        terrain.clear();
        objectives.clear();
        asteroidHexes.clear();
        planetHexes.clear();
        planetSurfaceHexes.clear();
        planetAtmosphereHexes.clear();
        ringHexes.clear();

        for (Terrain t : ScenarioLoader.loadTerrain(scenario))
            addTerrain(t);
        for (com.sfb.objects.Objective o : ScenarioLoader.loadObjectives(scenario))
            addObjective(o);

        for (int i = 0; i < scenario.sides.size(); i++) {
            ScenarioSpec.SideSpec side = scenario.sides.get(i);
            List<Ship> shipList = i < sideShips.size() ? sideShips.get(i) : new ArrayList<>();

            Player player = new Player();
            player.setName(side.name);
            try {
                player.setFaction(Faction.valueOf(side.faction));
            } catch (IllegalArgumentException ignored) {
                // Faction not in enum yet — player faction left null
            }
            players.add(player);

            for (Ship ship : shipList) {
                if (coiLoadouts != null) {
                    ScenarioLoader.applyCoi(ship, coiLoadouts.get(ship), scenario);
                }
                ships.add(ship);
            }
        }

        clock.reset();
        inProgress = true;
        startTurn();
    }

    /**
     * Convenience overload — loads ships from the ShipLibrary and starts the game
     * with no COI selections. Useful for automated tests and quick-start scenarios.
     */
    public void setupFromScenario(ScenarioSpec scenario) {
        if (!ShipLibrary.isLoaded())
            ShipLibrary.loadAllSpecs("data/factions");
        setupFromScenario(scenario, ScenarioLoader.loadShips(scenario), null);
    }

    /**
     * Instantiate a Ship from a ShipSpec loaded from the library.
     * The caller is responsible for setting location, facing, speed, and owner.
     */
    public Ship createShip(ShipSpec spec) {
        Ship ship = new Ship();
        ship.init(spec.toInitMap());
        return ship;
    }

    // --- Turn progression ---

    /**
     * Begin the energy allocation phase. Queues every ship for allocation and
     * blocks the impulse loop until all ships have submitted. The UI calls
     * submitAllocation() for each ship in turn; once the queue is empty,
     * beginImpulses() is called automatically.
     */
    public void startTurn() {
        // (Re-)inject this game's clock into every ship system that reads it.
        // Idempotent; also covers ships and shuttles added since last turn.
        for (Ship ship : ships)
            ship.attachClock(clock);
        for (com.sfb.objects.shuttles.Shuttle s : activeShuttles)
            s.attachClock(clock);
        allocationQueue.clear();
        allocationQueue.addAll(ships);
        awaitingAllocation = true;
        for (Ship ship : ships) {
            ship.getLabs().resetForTurn();
            ship.resetHetsThisTurn();
        }
    }

    /**
     * Returns the next ship waiting for energy allocation, or null if all
     * ships have been allocated this turn.
     */
    public Ship nextShipNeedingAllocation() {
        return allocationQueue.isEmpty() ? null : allocationQueue.get(0);
    }

    public boolean isAwaitingAllocation() {
        return awaitingAllocation;
    }

    public List<Ship> getAllocationQueue() {
        return Collections.unmodifiableList(allocationQueue);
    }

    /**
     * Submit the player's energy allocation for one ship. When the last ship
     * is submitted, automatically finalises all ships and advances to impulse 1.
     */
    public ActionResult submitAllocation(Ship ship, Energy allocation) {
        ship.allocateEnergy(allocation);
        // Pass cloak payment flag to the device before beginImpulses evaluates it
        if (ship.getCloakingDevice() != null)
            ship.getCloakingDevice().setCostPaid(allocation.isCloakPaid());
        allocationQueue.remove(ship);
        if (allocationQueue.isEmpty()) {
            beginImpulses();
        }
        return ActionResult.ok(ship.getName() + " energy allocated");
    }

    /**
     * Finalise all ships' startTurn() and advance to impulse 1.
     * Called automatically once every ship has submitted an allocation.
     */
    private void beginImpulses() {
        for (Ship ship : ships) {
            ship.startTurn();
        }
        // G7.42: links that persisted from last turn must be maintained (paid for)
        // now, before they can slow anyone (pseudo-speed) or be rotated.
        List<String> tractorMaintLog = tractorResolver.maintainLinksAtTurnStart();
        if (!tractorMaintLog.isEmpty())
            lastSeekerLog.addAll(tractorMaintLog);
        computeTractorPseudoSpeeds();
        // Notify cloak devices that a new turn has started — triggers involuntary
        // fade-in for any device whose cost was not paid this turn
        int impulse1 = clock.getImpulse() + 1; // impulse after nextImpulse() call below
        for (Ship ship : ships) {
            com.sfb.systemgroups.CloakingDevice cd = ship.getCloakingDevice();
            if (cd == null)
                continue;
            com.sfb.systemgroups.CloakingDevice.CloakState before = cd.getState();
            cd.newTurn(impulse1);
            if (before != com.sfb.systemgroups.CloakingDevice.CloakState.FADING_IN
                    && cd.getState() == com.sfb.systemgroups.CloakingDevice.CloakState.FADING_IN)
                lastSeekerLog.add("  " + ship.getName()
                        + " did not pay the cloak cost — involuntary fade-in (G13)");
            // G13: FC stays passive while the cloak is operating (fading out or
            // fully cloaked) — Ship.startTurn() above reactivates paid FC
            // unconditionally, so re-suppress it here. A fading-in ship is
            // decloaking and may run active FC (same as uncloak()).
            if (cd.getState() == com.sfb.systemgroups.CloakingDevice.CloakState.FADING_OUT
                    || cd.getState() == com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED)
                ship.goPassiveFc();
        }
        // J3.131: WW is voided if the protected ship exceeds maneuver rate 4
        for (Ship ship : ships) {
            if (ship.hasActiveWildWeasel() && !ship.getActiveWildWeasel().isPostExplosion()
                    && ship.getSpeed() > 4) {
                lastSeekerLog.add("  " + ship.getName() + " speed " + ship.getSpeed()
                        + " exceeds WW maneuver limit — Wild Weasel voided (J3.131)");
                voidWildWeasel(ship);
            }
        }
        lockOnResolver.performLockOnRolls();
        List<String> orphanLog = seekerControl.releaseOrphanedDrones();
        if (!orphanLog.isEmpty())
            lastSeekerLog.addAll(orphanLog);
        awaitingAllocation = false;
        tractorResolver.clearRotations();
        // Advance to impulse 1 now — the Initial Activity Phase is part of the new
        // turn, so the counter (and per-impulse bookkeeping) must be current while
        // players act in it. GameStateDto reads TurnTracker during this phase.
        clock.nextImpulse();
        movedThisImpulse.clear();
        prevLocations.clear();
        movedShuttlesThisImpulse.clear();
        // The Initial Activity Phase only hosts tractor rotations (G7.7); skip the
        // empty phase (and its all-players Ready round-trip) when nothing is held.
        currentPhase = tractorResolver.anyTractorLinksExist()
                ? ImpulsePhase.INITIAL_ACTIVITY
                : ImpulsePhase.MOVEMENT;
    }

    private void computeTractorPseudoSpeeds() {
        tractorResolver.computeTractorPseudoSpeeds();
    }

    /** Drain and return the lock-on roll log accumulated since the last call. */
    public List<String> drainLastLockOnLog() {
        return lockOnResolver.drainLastLockOnLog();
    }

    /** Mid-turn lock-on re-check for a target whose conditions changed (D6.113). */
    public List<String> checkLockOnsForUnit(Ship target) {
        return lockOnResolver.checkLockOnsForUnit(target);
    }

    /** Lock-on acquisition for a newly launched seeker (D6.121/D6.113). */
    List<String> checkLockOnsForNewUnit(Ship launcher, Unit newUnit) {
        return lockOnResolver.checkLockOnsForNewUnit(launcher, newUnit);
    }

    /** Effective range from attacker to target (D6.21 + D6.123). */
    public int getEffectiveRange(Ship attacker, Unit target) {
        return lockOnResolver.getEffectiveRange(attacker, target);
    }

    /** Retention probability vs a cloaked ship (G13.331) — package-private hook. */
    int retentionProbability(Ship attacker, Ship cloaked) {
        return lockOnResolver.retentionProbability(attacker, cloaked);
    }

    /** Reacquisition probability vs a cloaked ship (G13.333) — package-private hook. */
    int reacquisitionProbability(Ship attacker, Ship cloaked) {
        return lockOnResolver.reacquisitionProbability(attacker, cloaked);
    }

    /** True when either unit holds the other in a tractor beam (G7.412). */
    boolean tractorLinkBetween(Ship a, com.sfb.objects.Unit b) {
        return tractorResolver.linkExistsBetween(a, b);
    }

    /** Outcome of a D6.372 lock-strength roll: blocked flag + dice-log line. */
    static final class D637Result {
        final boolean blocked;
        final String line;
        D637Result(boolean blocked, String line) {
            this.blocked = blocked;
            this.line = line;
        }
    }

    /**
     * D6.34 net ECM shift for a tractor/transporter action (D6.372), with the
     * exemptions that make it zero: non-ship targets have no ECM; friendly
     * units ignore generated/lent EW (D6.373/D6.3146); a tractor link makes
     * lock-on automatic in both directions (G7.412).
     */
    int d637Shift(Ship actor, Unit target) {
        if (!(target instanceof Ship))
            return 0;
        Ship tship = (Ship) target;
        if (isSameTeam(actor, tship))
            return 0;
        if (tractorLinkBetween(actor, target))
            return 0;
        // P3.33: asteroid/ring hexes between actor and target add natural ECM
        // (the SH35.452 "ring ECM affects tractor lock-on" case)
        int terrainEcm = terrainEcmAlongLine(actor.getLocation(), target.getLocation());
        int targetEcm = tship.getEcmAllocated() + tship.getWwEcmBonus() + terrainEcm;
        int eccm = actor.isActiveFireControl() ? actor.getEccmAllocated() : 0;
        return (int) Math.floor(Math.sqrt(Math.max(0, targetEcm - eccm)));
    }

    /**
     * D6.372: roll one die per individual tractor/transporter action and add
     * the net ECM shift; a total over six means the lock-on is not strong
     * enough and the system cannot be used. Returns null when no roll is
     * needed (shift 0 — a bare d6 cannot exceed six).
     */
    D637Result rollD637(Ship actor, Unit target, String systemName) {
        int shift = d637Shift(actor, target);
        if (shift <= 0)
            return null;
        int die = new com.sfb.utilities.DiceRoller().rollOneDie();
        boolean blocked = die + shift > 6;
        String line = systemName + " vs " + target.getName() + " ECM: die " + die
                + " + shift " + shift + " = " + (die + shift)
                + (blocked ? " > 6 — cannot achieve lock (D6.372)" : " — lock achieved (D6.372)");
        return new D637Result(blocked, line);
    }

    /**
     * End-of-turn cleanup. Resets per-turn weapon states, shield reinforcement,
     * etc. Then starts the next turn's energy allocation.
     */
    /** Log lines from the most recent end-of-turn boarding combat resolution. */
    private final List<String> lastBoardingLog = new ArrayList<>();

    public List<String> getLastBoardingLog() {
        return lastBoardingLog;
    }

    public ActionResult endTurn() {
        lastBoardingLog.clear();
        capturedThisTurn.clear();

        // Final Activity Phase (D7.32): resolve boarding party combat on every
        // ship that has enemy troops aboard before per-turn cleanup.
        for (Ship ship : ships) {
            if (!ship.getEnemyTroops().isEmpty()) {
                BoardingCombatResult result = performBoardingCombat(ship);
                lastBoardingLog.add(result.log);
            }
        }
        for (Ship ship : ships) {
            ship.cleanUp();
        }

        // C7.1: identify ships eligible for disengagement by acceleration.
        // Players must confirm YES/NO before the next turn's EA begins.
        disengagementResolver.queueAccelDisengageCandidates();

        if (pendingAccelDisengage.isEmpty()) {
            startTurn();
            if (gameEndResult == null)
                gameEndResult = checkEndConditions();
        }
        // else: startTurn() is deferred until all confirmAccelDisengage() calls are
        // processed

        String msg = lastBoardingLog.isEmpty() ? "" : String.join("\n", lastBoardingLog);
        return ActionResult.ok(msg);
    }

    /**
     * Advance to the next phase. Cycles MOVEMENT → ACTIVITY → DIRECT_FIRE →
     * END_OF_IMPULSE, then rolls over to the next impulse (or next turn after
     * impulse 32).
     */
    public ActionResult advancePhase() {
        // Idempotent clock injection — covers ships/shuttles added by any path
        // (scenario load, tests that skip startTurn, mid-turn launches).
        for (Ship ship : ships)
            ship.attachClock(clock);
        for (com.sfb.objects.shuttles.Shuttle s : activeShuttles)
            s.attachClock(clock);
        List<String> log = new ArrayList<>();
        switch (currentPhase) {
            case INITIAL_ACTIVITY:
                // Impulse counter and per-impulse state were already advanced in
                // beginImpulses(); this is purely the phase transition.
                currentPhase = ImpulsePhase.MOVEMENT;
                break;
            case MOVEMENT:
                lastSeekerLog = moveSeekers();
                lastSeekerLog.addAll(moveShuttles());
                List<String> mineLog = mineResolver.processMines();
                lastSeekerLog.addAll(mineLog);
                // P2.32x: all movement for the impulse is in — evaluate planet
                // LOS at the phase boundary (transitions only; the same-step
                // passing exemption falls out of checking nowhere else)
                lastSeekerLog.addAll(lockOnResolver.sweepPlanetLos());
                lastSeekerLog.addAll(seekerControl.sweepSelfGuidedLos());
                lastSeekerLog.addAll(seekerControl.releaseOrphanedDrones());
                log.addAll(lastSeekerLog);
                if (!pendingVolleys.isEmpty()) {
                    reinforcementReturnPhase = ImpulsePhase.ACTIVITY;
                    currentPhase = ImpulsePhase.REINFORCEMENT;
                } else {
                    dacChoiceReturnPhase = ImpulsePhase.ACTIVITY;
                    lastInternalDamageLog = new ArrayList<>();
                    damageResolver.resolveInternalDamage();
                    log.addAll(lastInternalDamageLog);
                    if (currentPhase != ImpulsePhase.DAC_CHOICE)
                        currentPhase = ImpulsePhase.ACTIVITY;
                }
                break;
            case ACTIVITY:
                currentPhase = ImpulsePhase.DIRECT_FIRE;
                break;
            case DIRECT_FIRE:
                if (!pendingVolleys.isEmpty()) {
                    // Defenders need a chance to spend reserve power before damage lands
                    reinforcementReturnPhase = ImpulsePhase.END_OF_IMPULSE;
                    currentPhase = ImpulsePhase.REINFORCEMENT;
                } else {
                    firedPairsThisPhase.clear();
                    dacChoiceReturnPhase = ImpulsePhase.END_OF_IMPULSE;
                    lastInternalDamageLog = new ArrayList<>();
                    damageResolver.resolveInternalDamage();
                    log.addAll(lastInternalDamageLog);
                    if (currentPhase != ImpulsePhase.DAC_CHOICE)
                        currentPhase = ImpulsePhase.END_OF_IMPULSE;
                }
                break;
            case REINFORCEMENT:
                log.addAll(damageResolver.applyPendingVolleys());
                dacChoiceReturnPhase = reinforcementReturnPhase;
                lastInternalDamageLog = new ArrayList<>();
                damageResolver.resolveInternalDamage();
                log.addAll(lastInternalDamageLog);
                if (currentPhase != ImpulsePhase.DAC_CHOICE)
                    currentPhase = reinforcementReturnPhase;
                break;
            case DAC_CHOICE:
                // DAC_CHOICE is exited via submitDacChoice(), not ADVANCE_PHASE.
                return ActionResult.fail("A DAC system choice is pending — submit your selection first");
            case CONTROL_OVERFLOW:
                // CONTROL_OVERFLOW is exited via submitControlOverflowChoice(), not
                // ADVANCE_PHASE.
                return ActionResult.fail("A control channel overflow is pending — release or transfer a seeker first");

            case END_OF_IMPULSE:
                // 6E: Roll UIM burnout once per ship that used UIM this impulse (D6.521)
                if (!uimUsedThisImpulse.isEmpty()) {
                    int eoi = clock.getImpulse();
                    for (Map.Entry<Ship, List<com.sfb.weapons.Disruptor>> entry : uimUsedThisImpulse.entrySet()) {
                        Ship uimShip = entry.getKey();
                        com.sfb.systemgroups.UIM activeUim = uimShip.getActiveUim(eoi);
                        if (activeUim == null)
                            continue;
                        boolean burnout = activeUim.checkBurnout(eoi, entry.getValue());
                        int burnoutRoll = activeUim.getLastBurnoutRoll();
                        if (burnout) {
                            log.add(uimShip.getName() + ": UIM BURNOUT! (roll " + burnoutRoll
                                    + ") Disruptors locked for 32 impulses.");
                            uimShip.activateNextStandby(activeUim, eoi);
                        } else {
                            log.add(uimShip.getName() + ": UIM burnout check — no burnout (roll " + burnoutRoll + ")");
                        }
                    }
                    uimUsedThisImpulse.clear();
                }
                // Roll over to next impulse (or next turn)
                if (clock.getLocalImpulse() >= 32) {
                    ActionResult boardingResult = endTurn();
                    if (!boardingResult.getMessage().isEmpty())
                        log.add(boardingResult.getMessage());
                } else {
                    clock.nextImpulse();
                    movedThisImpulse.clear();
                    prevLocations.clear();
                    movedShuttlesThisImpulse.clear();

                    // TAC earn on Speed-4 schedule: impulses 2, 8, 16, 24 (C5.231)
                    int localImp = clock.getLocalImpulse();
                    if (localImp == 2 || localImp == 8 || localImp == 16 || localImp == 24) {
                        for (Ship s : ships) {
                            if (s.getSpeed() == 0 && s.getTacBudget() > 0) {
                                String earnMsg = s.updateTacEarning();
                                if (earnMsg != null)
                                    log.add(earnMsg);
                            }
                        }
                    }
                }
                autoRaiseShields();
                // Advance cloak fade states now that the impulse has incremented.
                // If a ship transitions to FULLY_CLOAKED this impulse, clear all
                // lock-ons other ships hold on it — it can no longer be targeted.
                for (Ship ship : ships) {
                    com.sfb.systemgroups.CloakingDevice cd = ship.getCloakingDevice();
                    if (cd == null)
                        continue;
                    com.sfb.systemgroups.CloakingDevice.CloakState before = cd.getState();
                    cd.updateState(clock.getImpulse());
                    com.sfb.systemgroups.CloakingDevice.CloakState after = cd.getState();

                    if (before != com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED
                            && after == com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED) {
                        // Fade-out complete — each ship holding a lock-on rolls to
                        // retain it (G13.331); failures lose it and their guided
                        // drones release (D6.122). Self-guiding plasma rolls its
                        // own retention at sensor 6 (G13.3343/G13.3344).
                        log.add(ship.getName() + " has completed fade-out — rolling lock-on retention (G13.331)");
                        log.addAll(lockOnResolver.rollRetention(ship));
                        log.addAll(seekerControl.rollPlasmaCloakRetention(ship));
                        log.addAll(seekerControl.releaseOrphanedDrones());
                    }
                    // Leaving FULLY_CLOAKED never happens in updateState() — only
                    // uncloak(), CloakingDevice.newTurn() (cost lapse, handled in
                    // beginImpulses()), and damage() do that, and each site runs
                    // the re-acquisition / fire-control consequences itself.
                }
                // FC activation countdown check (D6.633)
                int absNow = clock.getImpulse();
                for (Ship ship : ships) {
                    if (ship.isFcActivating() && ship.updateFcActivation(absNow)) {
                        log.add(ship.getName() + " fire control now fully active");
                        // If WW was exploding when activation started, void it now (D6.65/J3.2112)
                        if (ship.hasActiveWildWeasel()) {
                            log.add(ship.getName() + " Wild Weasel voided — fire control active");
                            voidWildWeasel(ship);
                        }
                    }
                }
                // Emergency deceleration completion check (C8.101)
                for (Ship ship : ships) {
                    if (ship.isDecelerating() && absNow >= ship.getDecelerationEndsAtImpulse()) {
                        ship.completeEmergencyDeceleration(absNow);
                        log.add(ship.getName()
                                + " has stopped (emergency deceleration complete — post-decel period: 16 impulses)");
                    }
                }
                currentPhase = ImpulsePhase.MOVEMENT;
                break;
        }
        // Secondary effects of units leaving play (chasers losing tracking)
        log.addAll(removalLog);
        removalLog.clear();
        // Self-healing: drop any tractor link whose held unit left play this
        // phase (impacted, shot down, expired) — no matter which path removed it
        log.addAll(tractorResolver.releaseDeadLinks());
        String message = log.isEmpty() ? "" : String.join("\n", log);
        return ActionResult.ok(message);
    }

    /**
     * Automatically raise any voluntarily-lowered shields that have met the
     * 8-impulse lockout.
     */
    private void autoRaiseShields() {
        for (Ship ship : ships) {
            for (int s = 1; s <= 6; s++) {
                if (!ship.getShields().isShieldActive(s)) {
                    ship.getShields().raiseShield(s);
                }
            }
        }
    }

    public ImpulsePhase getCurrentPhase() {
        return currentPhase;
    }

    public int getCurrentTurn() {
        return (clock.getImpulse() - 1) / 32 + 1;
    }

    public int getCurrentImpulse() {
        return clock.getLocalImpulse();
    }

    public int getMapCols() {
        return mapCols;
    }

    public int getMapRows() {
        return mapRows;
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    public GameEndResult getGameEnd() {
        return gameEndResult;
    }

    /**
     * Check whether the game has ended.
     * Returns null if the game is still ongoing, or a GameEndResult describing the
     * outcome.
     */
    public GameEndResult checkEndConditions() {
        // Turn limit
        if (maxTurns > 0 && clock.getTurn() > maxTurns)
            return new GameEndResult(null, "Turn limit reached (" + maxTurns + " turns)");

        // Collect teams that still have ships on the map
        java.util.Set<String> activeTeams = new java.util.LinkedHashSet<>();
        for (Ship ship : ships) {
            if (ship.getLocation() != null && !ship.isDisengaged()) {
                Player owner = ship.getOwner();
                if (owner != null && owner.getTeamName() != null)
                    activeTeams.add(owner.getTeamName());
            }
        }
        if (activeTeams.size() == 1)
            return new GameEndResult(activeTeams.iterator().next(),
                    "All opposing ships destroyed, disengaged, or captured");
        if (activeTeams.isEmpty())
            return new GameEndResult(null, "All ships eliminated — draw");

        return null; // game ongoing
    }

    public record GameEndResult(String winnerTeam, String reason) {
    }

    // -------------------------------------------------------------------------
    // Victory point calculation (S2.21 / S2.23)
    // -------------------------------------------------------------------------

    public record ShipVpRow(
            String shipName, String teamName, int gabpv,
            String status, // "DESTROYED" | "CAPTURED" | "DISENGAGED" | "CRIPPLED" | "DAMAGED" | "INTACT"
            int vpScored // points scored AGAINST this ship by the enemy
    ) {
    }

    public record TeamScore(String teamName, int vpScored, int vpAgainst, String levelOfVictory) {
    }

    public record Scoreboard(java.util.List<ShipVpRow> rows, java.util.List<TeamScore> teams) {
    }

    public Scoreboard calculateVictoryPoints() {
        // Gather all ships (active + destroyed; disengaged/captured still in ships
        // list)
        java.util.List<Ship> allShips = new java.util.ArrayList<>(ships);
        allShips.addAll(destroyedShips);

        java.util.List<ShipVpRow> rows = new java.util.ArrayList<>();

        for (Ship ship : allShips) {
            // Captured ships now belong to the captor (D7.50) — attribute the row
            // to the ORIGINAL side so the captor's team is the one scoring it.
            String teamName = ship.isCaptured() && ship.getCapturedFromTeam() != null
                    ? ship.getCapturedFromTeam()
                    : ship.getOwner() != null ? ship.getOwner().getTeamName() : "Unknown";

            // GABPV: base BPV (already includes y175 refit) + fighter BPV
            int fighterBpv = 0;
            for (com.sfb.objects.shuttles.Shuttle s : ship.getShuttles().getAllShuttles()) {
                if (s instanceof com.sfb.objects.shuttles.Fighter f)
                    fighterBpv += f.getBpv();
            }
            // Also count fighters that launched and are on the map as active shuttles
            for (com.sfb.objects.shuttles.Shuttle s : activeShuttles) {
                if (s instanceof com.sfb.objects.shuttles.Fighter f
                        && ship.getName().equals(s.getParentShipName())) {
                    fighterBpv += f.getBpv();
                }
            }
            int gabpv = ship.getBattlePointValue() + fighterBpv;

            // Scoring math lives in VictoryCalculator (S2.21 order, S2.24 rounding)
            String status = VictoryCalculator.status(ship).name();
            int vpScored = VictoryCalculator.pointsFor(ship, gabpv);

            rows.add(new ShipVpRow(ship.getName(), teamName, gabpv, status, vpScored));
        }

        // Sum VPs per team: a team scores the VPs from ships belonging to OTHER teams
        java.util.Map<String, Integer> vpByTeam = new java.util.LinkedHashMap<>();
        java.util.Set<String> allTeams = new java.util.LinkedHashSet<>();
        for (ShipVpRow row : rows)
            allTeams.add(row.teamName());
        for (String t : allTeams)
            vpByTeam.put(t, 0);

        for (ShipVpRow row : rows) {
            for (String scorer : allTeams) {
                if (!scorer.equals(row.teamName()))
                    vpByTeam.merge(scorer, row.vpScored(), Integer::sum);
            }
        }

        java.util.List<TeamScore> teams = new java.util.ArrayList<>();
        for (String team : allTeams) {
            int myScore = vpByTeam.get(team);
            int theirScore = allTeams.stream()
                    .filter(t -> !t.equals(team))
                    .mapToInt(vpByTeam::get).sum();
            teams.add(new TeamScore(team, myScore, theirScore,
                    VictoryCalculator.victoryLevel(myScore, theirScore).getLabel()));
        }

        return new Scoreboard(rows, teams);
    }

    /**
     * This game's impulse clock. Exposed for tests and future save/restore;
     * production code must not advance it outside advancePhase()/beginImpulses().
     */
    public TurnTracker getClock() {
        return clock;
    }

    public int getAbsoluteImpulse() {
        return clock.getImpulse();
    }

    // --- Queries ---

    public List<Ship> getShips() {
        return ships;
    }

    public List<Player> getPlayers() {
        return players;
    }

    /** True if both units are owned by players on the same team. */
    public boolean isSameTeam(com.sfb.objects.Unit a, com.sfb.objects.Unit b) {
        if (!(a instanceof Ship) || !(b instanceof Ship))
            return false;
        Player pa = ((Ship) a).getOwner();
        Player pb = ((Ship) b).getOwner();
        if (pa == null || pb == null)
            return false;
        String ta = pa.getTeamName();
        String tb = pb.getTeamName();
        return ta != null && ta.equals(tb);
    }

    public List<Seeker> getSeekers() {
        return seekers;
    }

    int nextSeekerSeq() {
        return ++seekerSeq;
    }

    /** Queue a CONTROL_OVERFLOW interrupt for any ship over its control limit. */
    void checkControlOverflow() {
        for (Ship ship : ships) {
            if (ship.getControlUsed() > ship.getControlCapacity()) {
                boolean alreadyQueued = pendingControlOverflows.stream()
                        .anyMatch(p -> p.ship == ship);
                if (!alreadyQueued)
                    pendingControlOverflows.add(new PendingControlOverflow(ship));
            }
        }
        if (!pendingControlOverflows.isEmpty() && currentPhase != ImpulsePhase.CONTROL_OVERFLOW) {
            controlOverflowReturnPhase = currentPhase;
            currentPhase = ImpulsePhase.CONTROL_OVERFLOW;
        }
    }

    public List<PendingControlOverflow> getPendingControlOverflows() {
        return pendingControlOverflows;
    }

    /**
     * Resolve one step of a control overflow: either release a seeker
     * (self-destruct
     * unless self-guiding) or transfer it to an allied ship.
     *
     * @param seekerName name of the seeker to act on
     * @param toShipName null/blank = release; non-blank = transfer to this ship
     */
    public ActionResult submitControlOverflowChoice(String seekerName, String toShipName) {
        if (pendingControlOverflows.isEmpty())
            return ActionResult.fail("No control overflow pending");

        PendingControlOverflow overflow = pendingControlOverflows.get(0);
        Ship ship = overflow.ship;

        Seeker seeker = ship.getControlledSeekers().stream()
                .filter(s -> s instanceof Unit && ((Unit) s).getName().equalsIgnoreCase(seekerName))
                .findFirst().orElse(null);
        if (seeker == null)
            return ActionResult.fail("Seeker not found in " + ship.getName() + "'s control channels: " + seekerName);

        String log;
        if (toShipName != null && !toShipName.isBlank()) {
            Ship toShip = ships.stream()
                    .filter(s -> s.getName().equalsIgnoreCase(toShipName))
                    .findFirst().orElse(null);
            if (toShip == null)
                return ActionResult.fail("Ship not found: " + toShipName);
            if (!isSameTeam(ship, toShip))
                return ActionResult.fail(toShipName + " is not on the same team");
            Unit target = seeker.getTarget();
            if (target == null)
                return ActionResult.fail("Seeker has no target");
            if (!toShip.hasLockOn(target))
                return ActionResult.fail(toShipName + " does not have lock-on to " + target.getName());
            if (!toShip.acquireControl(seeker))
                return ActionResult.fail(toShipName + " is at control capacity");
            ship.releaseControl(seeker);
            seeker.setController(toShip);
            if (seeker instanceof Drone)
                ((Drone) seeker).setSelfGuiding(false);
            log = ((Unit) seeker).getName() + " control transferred to " + toShipName;
        } else {
            ship.releaseControl(seeker);
            boolean selfGuiding = seeker instanceof Drone && ((Drone) seeker).isSelfGuiding();
            if (selfGuiding) {
                log = ((Unit) seeker).getName() + " released — continuing self-guided";
            } else {
                removeSeekerFromPlay(seeker);
                log = ((Unit) seeker).getName() + " released — self-destructed";
            }
        }

        if (ship.getControlUsed() <= ship.getControlCapacity())
            pendingControlOverflows.remove(overflow);

        // Re-scan in case other ships are also over limit
        checkControlOverflow();

        if (pendingControlOverflows.isEmpty())
            currentPhase = controlOverflowReturnPhase;

        return ActionResult.ok(log);
    }

    /** Voluntarily transfer control of a seeker to an allied ship (FD1.7). */
    public ActionResult transferSeekerControl(String seekerName, String toShipName) {
        return seekerControl.transferSeekerControl(seekerName, toShipName);
    }

    /**
     * The single exit for a seeker leaving play — impact, destruction by fire,
     * chaff distraction, endurance expiry, death drag, whatever removes it.
     * Frees the controller's channel, drops any tractor beam holding it,
     * clears lock-ons on it, empties its position, and removes it from the
     * unit lists. Idempotent, so batch callers may pass duplicates. Callers
     * log WHY the seeker left play; this method only makes it gone.
     */
    void removeSeekerFromPlay(Seeker seeker) {
        seekers.remove(seeker);
        if (seeker.getController() instanceof com.sfb.objects.DroneController)
            ((com.sfb.objects.DroneController) seeker.getController()).releaseControl(seeker);
        if (seeker instanceof com.sfb.objects.shuttles.Shuttle)
            activeShuttles.remove((com.sfb.objects.shuttles.Shuttle) seeker);
        if (seeker instanceof Unit) {
            Unit unit = (Unit) seeker;
            tractorResolver.releaseLinksHolding(unit);
            for (Ship ship : ships)
                ship.removeLockOn(unit);
            unit.setLocation(null);
            clearChasersOf(unit, "target destroyed");
        }
    }

    /**
     * The single exit for a non-seeker shuttle leaving play by destruction.
     * Seeking shuttles delegate to {@link #removeSeekerFromPlay}. Landing and
     * recovery are survival paths with their own handling — they only share
     * {@link #clearChasersOf}.
     */
    void removeShuttleFromPlay(com.sfb.objects.shuttles.Shuttle shuttle, String chaserReason) {
        if (shuttle instanceof Seeker) {
            removeSeekerFromPlay((Seeker) shuttle);
            return;
        }
        activeShuttles.remove(shuttle);
        tractorResolver.releaseLinksHolding(shuttle);
        for (Ship ship : ships)
            ship.removeLockOn(shuttle);
        shuttle.setLocation(null);
        clearChasersOf(shuttle, chaserReason);
    }

    /**
     * Seekers chasing a unit that just left play lose tracking and
     * self-destruct (their guidance has nothing to home on). Without this,
     * chasers ghost-chase a stale location or NPE on a nulled one. Log lines
     * go to removalLog, drained by the next advancePhase(). Wild Weasels never
     * pass through here — their chasers follow J3.21x explosion rules instead.
     */
    void clearChasersOf(Unit gone, String reason) {
        List<Seeker> chasing = new ArrayList<>();
        for (Seeker sk : seekers)
            if (gone.equals(sk.getTarget()))
                chasing.add(sk);
        for (Seeker sk : chasing) {
            removalLog.add((sk instanceof Unit ? ((Unit) sk).getName() : "seeker")
                    + " lost tracking — " + reason);
            removeSeekerFromPlay(sk);
        }
    }

    public List<com.sfb.objects.shuttles.Shuttle> getActiveShuttles() {
        return activeShuttles;
    }

    public List<Ship> getMovableShips() {
        return shipMover.getMovableShips();
    }

    public boolean hasMovedThisImpulse(Ship ship) {
        return shipMover.hasMovedThisImpulse(ship);
    }

    public boolean canMoveThisImpulse(Ship ship) {
        return shipMover.canMoveThisImpulse(ship);
    }

    public Ship nextMovableShip() {
        return shipMover.nextMovableShip();
    }

    public boolean canFireThisPhase() {
        return currentPhase == ImpulsePhase.DIRECT_FIRE;
    }

    // --- Command execution ---

    public ActionResult execute(Command command) {
        return command.execute(this);
    }

    // --- Movement actions ---

    /**
     * Declare or cancel disengagement by acceleration (C7.1).
     * Must be called during the energy allocation phase.
     * When declared, the ship's speed must equal its maximum accelerable speed this
     * turn.
     */
    public List<Ship> getPendingAccelDisengage() {
        return Collections.unmodifiableList(pendingAccelDisengage);
    }

    /**
     * Player confirms or declines disengagement by acceleration for a ship (C7.1).
     * Called after endTurn() has identified eligible ships. Once all pending ships
     * are resolved, the next turn's EA begins automatically.
     */
    public ActionResult confirmAccelDisengage(Ship ship, boolean confirm) {
        if (!pendingAccelDisengage.contains(ship))
            return ActionResult.fail(ship.getName() + " is not awaiting accel disengage confirmation");

        pendingAccelDisengage.remove(ship);

        String result = disengagementResolver.resolveAccelDisengage(ship, confirm);

        if (pendingAccelDisengage.isEmpty()) {
            startTurn();
            if (gameEndResult == null)
                gameEndResult = checkEndConditions();
        }

        return ActionResult.ok(result);
    }

    /**
     * Check whether the given ship currently qualifies for disengagement by
     * separation (C7.2):
     * no enemy ship within 50 hexes, and no in-flight seekers targeting it.
     */
    public boolean canDisengageBySeparation(Ship ship) {
        return disengagementResolver.canDisengageBySeparation(ship);
    }

    /**
     * Disengage by separation (C7.2) — player-initiated after confirming
     * eligibility.
     */
    public List<String> getDestructionDirections(Ship ship) {
        return disengagementResolver.getDestructionDirections(ship);
    }

    // -------------------------------------------------------------------------
    // Wild Weasel (J3.0)
    // -------------------------------------------------------------------------

    /** Drop a chaff pack from a shuttle (D11.3). */
    public ActionResult dropChaff(com.sfb.objects.shuttles.Shuttle shuttle) {
        return launchCoordinator.dropChaff(shuttle);
    }

    /** Launch a charged Wild Weasel (J3.1). */
    public ActionResult launchWildWeasel(Ship ship, String shuttleName, int facing, int speed) {
        return launchCoordinator.launchWildWeasel(ship, shuttleName, facing, speed);
    }

    /** Void the active Wild Weasel for this ship (J3.13x). */
    public void voidWildWeasel(Ship ship) {
        launchCoordinator.voidWildWeasel(ship);
    }

    /**
     * Drop any seekers whose controller no longer holds lock-on to their target
     * (D6.122). Package-private hook for resolvers acting after a mid-turn
     * fire-control drop; Game's own sites call seekerControl directly.
     */
    List<String> releaseOrphanedDrones() {
        return seekerControl.releaseOrphanedDrones();
    }

    /**
     * Mid-turn EW adjustment (D6.315) — the Fire Decision Step. Drops are free
     * and irrevocable; additions cost 1 battery per point (reserve power,
     * D6.312) and expire at end of turn. Circuit switch lockouts (D6.316) are
     * enforced by the ship's EW circuit bank. Changes are announced (D6.32)
     * and effective immediately for this impulse's direct fire.
     */
    public ActionResult adjustEw(Ship ship, int newEcm, int newEccm) {
        if (currentPhase != ImpulsePhase.DIRECT_FIRE)
            return ActionResult.fail("EW can only be adjusted during the Direct Fire phase (D6.315)");
        int oldEcm = ship.getEcmAllocated();
        int oldEccm = ship.getEccmAllocated();
        if (newEcm == oldEcm && newEccm == oldEccm)
            return ActionResult.fail("No EW change requested");
        int cost = ship.ewAdjustBatteryCost(newEcm, newEccm);
        if (cost > ship.getPowerSystems().getBatteryPower())
            return ActionResult.fail("Need " + cost + " reserve power for added EW points, have "
                    + ship.getPowerSystems().getBatteryPower() + " battery (D6.312)");
        String err = ship.adjustEw(newEcm, newEccm, clock.getImpulse());
        if (err != null)
            return ActionResult.fail(err);
        if (cost > 0)
            ship.getPowerSystems().useBattery(cost);
        return ActionResult.ok(ship.getName() + " adjusts EW: ECM " + oldEcm + " → " + newEcm
                + ", ECCM " + oldEccm + " → " + newEccm
                + (cost > 0 ? " (" + cost + " reserve power; D6.312)" : " (D6.315)"));
    }

    /**
     * Set FC to passive immediately (D6.632). Any impulse, any phase.
     * Dropping FC loses every lock-on (D6.62) and releases guided drones (D6.122).
     */
    public ActionResult goPassiveFireControl(Ship ship) {
        if (!ship.isActiveFireControl() && !ship.isFcActivating())
            return ActionResult.fail(ship.getName() + " fire control is already passive");
        ship.goPassiveFc();
        List<String> log = new ArrayList<>();
        log.add(ship.getName() + " fire control set to passive — all lock-ons lost");
        log.addAll(seekerControl.releaseOrphanedDrones());
        return ActionResult.ok(String.join("\n", log));
    }

    /**
     * Begin the 4-impulse activation countdown (D6.633).
     * Voids any active WW immediately unless it is currently in its explosion
     * period (D6.65/J3.2112).
     */
    public ActionResult beginActivatingFireControl(Ship ship) {
        if (ship.isActiveFireControl())
            return ActionResult.fail(ship.getName() + " fire control is already active");
        if (!ship.isFcPaidThisTurn())
            return ActionResult.fail(ship.getName() + " — no fire control energy allocated this turn");
        // Void WW unless it is in its explosion period (D6.65)
        if (ship.hasActiveWildWeasel()) {
            com.sfb.objects.shuttles.WildWeaselShuttle ww = ship.getActiveWildWeasel();
            if (!ww.isExploding()) {
                voidWildWeasel(ship);
            }
            // If exploding: WW will be voided when activation completes (handled in
            // nextImpulse check)
        }
        boolean started = ship.beginFcActivation(getAbsoluteImpulse());
        if (!started)
            return ActionResult.fail(ship.getName() + " could not begin FC activation");
        return ActionResult.ok(ship.getName() + " fire control activating — fully active in 4 impulses");
    }

    public ActionResult emergencyDeceleration(Ship ship) {
        return shipMover.emergencyDeceleration(ship);
    }

    public ActionResult disengageBySeparation(Ship ship) {
        return disengagementResolver.disengageBySeparation(ship);
    }

    /**
     * Concede — mark all ships belonging to the named player as DESTROYED and
     * remove them from active play. Triggers end-condition check immediately.
     */
    public ActionResult concede(String playerName) {
        List<Ship> toDestroy = ships.stream()
                .filter(s -> s.getOwner() != null && playerName.equals(s.getOwner().getName()))
                .toList();
        if (toDestroy.isEmpty())
            return ActionResult.fail("No active ships found for player: " + playerName);

        List<String> names = new ArrayList<>();
        for (Ship ship : toDestroy) {
            tractorResolver.releaseAllLinksInvolving(ship);
            ship.setBattleStatus(com.sfb.properties.BattleStatus.DESTROYED);
            ship.setLocation(null);
            destroyedShips.add(ship);
            names.add(ship.getName());
        }
        ships.removeAll(toDestroy);

        gameEndResult = checkEndConditions();
        return ActionResult.ok(playerName + " has conceded. Ships lost: " + String.join(", ", names));
    }

    public ActionResult moveForward(Ship ship) {
        return shipMover.moveForward(ship);
    }

    public ActionResult turnLeft(Ship ship) {
        return shipMover.turnLeft(ship);
    }

    public ActionResult turnRight(Ship ship) {
        return shipMover.turnRight(ship);
    }

    public ActionResult sideslipLeft(Ship ship) {
        return shipMover.sideslipLeft(ship);
    }

    public ActionResult sideslipRight(Ship ship) {
        return shipMover.sideslipRight(ship);
    }

    public ActionResult performHet(Ship ship, int absoluteFacing) {
        return shipMover.performHet(ship, absoluteFacing);
    }

    // --- Tactical Maneuver (C5.0) ---

    public ActionResult performTacticalTurn(Ship ship, int newFacing, boolean preferSublight) {
        return shipMover.performTacticalTurn(ship, newFacing, preferSublight);
    }

    // --- Fighter HET (C6.42) ---

    public ActionResult performFighterHet(com.sfb.objects.shuttles.Shuttle shuttle, int absoluteFacing) {
        return shipMover.performFighterHet(shuttle, absoluteFacing);
    }

    // --- Shuttle movement ---

    public List<com.sfb.objects.shuttles.Shuttle> getMovableShuttles() {
        return shipMover.getMovableShuttles();
    }

    public boolean canMoveShuttleThisImpulse(com.sfb.objects.shuttles.Shuttle shuttle) {
        return shipMover.canMoveShuttleThisImpulse(shuttle);
    }

    public ActionResult moveShuttleForward(com.sfb.objects.shuttles.Shuttle shuttle) {
        return shipMover.moveShuttleForward(shuttle);
    }

    public ActionResult turnShuttleLeft(com.sfb.objects.shuttles.Shuttle shuttle) {
        return shipMover.turnShuttleLeft(shuttle);
    }

    public ActionResult turnShuttleRight(com.sfb.objects.shuttles.Shuttle shuttle) {
        return shipMover.turnShuttleRight(shuttle);
    }

    public ActionResult sideslipShuttleLeft(com.sfb.objects.shuttles.Shuttle shuttle) {
        return shipMover.sideslipShuttleLeft(shuttle);
    }

    public ActionResult sideslipShuttleRight(com.sfb.objects.shuttles.Shuttle shuttle) {
        return shipMover.sideslipShuttleRight(shuttle);
    }

    // --- Weapons fire ---

    /**
     * Returns all weapons on the attacker that bear on the target.
     */
    public List<Weapon> getBearingWeapons(Ship attacker, Unit target) {
        return attacker.fetchAllBearingWeapons(target);
    }

    /**
     * Compute the range between two units.
     */
    public int getRange(Unit attacker, Unit target) {
        return MapUtils.getRange(attacker, target);
    }

    // -------------------------------------------------------------------------
    // Tractor beams (G7.0)
    // -------------------------------------------------------------------------

    public ActionResult establishTractor(Ship holder, String targetName, int bid) {
        return tractorResolver.establishTractor(holder, targetName, bid);
    }

    public ActionResult submitNegativeTractorBid(Ship defender, int defenderNewBid) {
        return tractorResolver.submitNegativeTractorBid(defender, defenderNewBid);
    }

    public ActionResult releaseTractor(Ship holder, String targetName) {
        return tractorResolver.releaseTractor(holder, targetName);
    }

    public ActionResult rotateTractored(Ship holder, String targetName, int destCol, int destRow) {
        if (currentPhase != ImpulsePhase.INITIAL_ACTIVITY)
            return ActionResult.fail("Tractor rotation is only allowed during the Initial Activity Phase (G7.7)");
        return tractorResolver.rotateTractored(holder, targetName, destCol, destRow);
    }

    public int getShieldNumber(Marker attacker, Ship target) {
        return damageResolver.getShieldNumber(attacker, target);
    }

    /**
     * Returns the shield(s) of {@code target} that face toward {@code attacker}.
     * Size 1: attacker is directly in front of a shield face.
     * Size 2: attacker is on the seam between two adjacent shields — caller must
     * ask the player which shield to use.
     */
    public java.util.List<Integer> getShieldCandidates(Marker attacker, Ship target) {
        return damageResolver.getShieldCandidates(attacker, target);
    }

    /**
     * Mark shield damage from one firing volley (6D2 — Direct-Fire Weapons Fire
     * Stage).
     * Bleed-through is queued as pending internal damage; it will not be resolved
     * until damageResolver.resolveInternalDamage() is called at the end of the
     * Direct-Fire segment
     * (6D4).
     *
     * @return A FireResult with the bleed-through amount and an empty internal log
     *         (log is populated later when damageResolver.resolveInternalDamage()
     *         runs).
     */
    public FireResult markShieldDamage(Ship target, int shieldNumber, int totalDamage) {
        return markShieldDamage(target, shieldNumber, totalDamage, null);
    }

    public FireResult markShieldDamage(Ship target, int shieldNumber, int totalDamage, Ship attacker) {
        return damageResolver.markShieldDamage(target, shieldNumber, totalDamage, attacker);
    }

    /**
     * Apply weapon damage to any unit — routes to the correct damage path.
     */
    public String applyDamageToUnit(int damage, Unit target, int shieldNumber) {
        return damageResolver.applyDamageToUnit(damage, target, shieldNumber);
    }

    /**
     * Fire a list of direct-fire weapons at a target, apply damage, and return a
     * combat log entry.
     *
     * @param attacker      The firing ship.
     * @param target        The unit being fired upon.
     * @param selected      Weapons chosen by the player (must implement
     *                      DirectFire).
     * @param range         True hex range to the target.
     * @param adjustedRange Range after scanner modifier.
     * @param shieldNumber  Shield facing the attacker (0 for non-ship targets).
     * @return A formatted combat log string describing every shot and the total
     *         damage applied.
     */
    public String fireWeapons(Unit attacker, Unit target, List<Weapon> selected,
            int range, int adjustedRange, int shieldNumber) {
        return fireWeapons(attacker, target, selected, range, adjustedRange, shieldNumber, false, false);
    }

    public String fireWeapons(Unit attacker, Unit target, List<Weapon> selected,
            int range, int adjustedRange, int shieldNumber, boolean useUim) {
        return fireWeapons(attacker, target, selected, range, adjustedRange, shieldNumber, useUim, false);
    }

    public String fireWeapons(Unit attacker, Unit target, List<Weapon> selected,
            int range, int adjustedRange, int shieldNumber, boolean useUim, boolean directFire) {
        return damageResolver.fireWeapons(attacker, target, selected, range, adjustedRange, shieldNumber,
                useUim, directFire);
    }

    /**
     * Returns the current list of pending fire volleys (for the game-state DTO).
     */
    public List<PendingVolley> getPendingVolleys() {
        return Collections.unmodifiableList(pendingVolleys);
    }

    /**
     * D4.0 Shield reinforcement: defender spends reserve (battery) power before
     * damage is applied. Called during the REINFORCEMENT phase.
     *
     * @param targetShip   the ship being reinforced
     * @param shieldNumber 1-based shield facing to reinforce
     * @param power        energy points to spend from battery
     */
    public ActionResult submitReinforcement(Ship targetShip, int shieldNumber, int power) {
        if (currentPhase != ImpulsePhase.REINFORCEMENT)
            return ActionResult.fail("Reinforcement can only be submitted during the Reinforcement phase");
        if (power < 0)
            return ActionResult.fail("Reserve power must be non-negative");
        if (targetShip.getShields().getBaseShieldStrength(shieldNumber) == 0)
            return ActionResult.fail("Shield #" + shieldNumber + " is at zero strength and cannot be reinforced");
        int available = targetShip.getPowerSystems().getBatteryPower();
        int toSpend = Math.min(power, available);
        if (toSpend > 0) {
            targetShip.getPowerSystems().useBattery(toSpend);
            targetShip.getShields().reinforceShield(shieldNumber, toSpend);
        }
        return ActionResult.ok(targetShip.getName() + " shield #" + shieldNumber
                + " reinforced by " + toSpend + " (of " + power + " requested; "
                + available + " available)");
    }

    void cleanupDestroyedShips() {
        ships.removeIf(s -> {
            if (s.isDestroyed()) {
                tractorResolver.releaseAllLinksInvolving(s);
                // Carried objectives drop free or are annihilated (SH47.475/SH35.454)
                lastInternalDamageLog.addAll(dropObjectivesFrom(s, false));
                lastInternalDamageLog.add(s.getName() + " has been destroyed and removed from play.");
                destroyedShips.add(s);
                return true;
            }
            return false;
        });
    }

    /**
     * Defender submits their DAC system choice. Applies the hit, then resumes
     * internal-damage resolution. Automatically advances out of DAC_CHOICE when
     * all choices and remaining bleed have been resolved.
     */
    public ActionResult submitDacChoice(String chosenSystem) {
        if (currentPhase != ImpulsePhase.DAC_CHOICE || pendingDacChoices.isEmpty())
            return ActionResult.fail("No DAC choice is pending");
        PendingDacChoice pending = pendingDacChoices.get(0);
        if (!pending.options.contains(chosenSystem))
            return ActionResult.fail("Invalid choice: " + chosenSystem + ". Valid: " + pending.options);

        pendingDacChoices.remove(0);
        lastInternalDamageLog = new ArrayList<>();

        damageResolver.applyDacChoice(pending, chosenSystem);

        damageResolver.resolveInternalDamage();

        // damageResolver.resolveInternalDamage() leaves currentPhase as DAC_CHOICE when
        // it returns
        // without adding new choices (it only changes phase when it *adds* a choice or
        // sets CONTROL_OVERFLOW). So the right exit test is: still in DAC_CHOICE AND
        // no choices remain → transition back to the phase that triggered the damage.
        if (currentPhase == ImpulsePhase.DAC_CHOICE && pendingDacChoices.isEmpty())
            currentPhase = dacChoiceReturnPhase;

        return ActionResult.ok(String.join("; ", lastInternalDamageLog));
    }

    public List<PendingDacChoice> getPendingDacChoices() {
        return Collections.unmodifiableList(pendingDacChoices);
    }

    /**
     * Returns the internal damage log from the most recent resolution step.
     * The UI should read this after advancing past DIRECT_FIRE.
     */
    public List<String> getLastInternalDamageLog() {
        return lastInternalDamageLog;
    }

    /**
     * Current internal-damage log (package hook for DamageResolver). Always
     * accessed through this method — never a cached reference — because
     * advancePhase() and submitDacChoice() replace the list each resolution step.
     */
    List<String> internalDamageLog() {
        if (lastInternalDamageLog == null)
            lastInternalDamageLog = new ArrayList<>();
        return lastInternalDamageLog;
    }

    /** Package hook for ShipMover — game-end evaluation stays Game-owned. */
    void refreshGameEnd() {
        gameEndResult = checkEndConditions();
    }

    /** Package hook for DamageResolver — phase transitions stay Game-owned. */
    void enterDacChoicePhase() {
        currentPhase = ImpulsePhase.DAC_CHOICE;
    }

    // --- Lab seeker identification ---

    /** Attempt lab identification of enemy seekers (G4.0). */
    public ActionResult identifySeekers(Ship actingShip, List<String> seekerNames) {
        return seekerControl.identifySeekers(actingShip, seekerNames);
    }

    // --- Drone launching ---

    public boolean canLaunchThisPhase() {
        return currentPhase == ImpulsePhase.ACTIVITY;
    }

    /**
     * Returns a failure message if the ship's cloak is restricting actions, or null
     * if clear.
     */
    ActionResult cloakActionBlock(Ship ship) {
        com.sfb.systemgroups.CloakingDevice cloak = ship.getCloakingDevice();
        if (cloak != null && cloak.isRestrictingActions())
            return ActionResult.fail(ship.getName() + " cannot act while cloaking device is active");
        return null;
    }

    /** Launch the next drone from the given rack (FD1.0). */
    public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack) {
        return launchCoordinator.launchDrone(launcher, target, rack);
    }

    /** Launch a specific drone from the given rack (FD1.0). */
    public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack, Drone drone, int facing) {
        return launchCoordinator.launchDrone(launcher, target, rack, drone, facing);
    }

    /** Launch an armed plasma torpedo (FP1.0). */
    public ActionResult launchPlasma(Ship launcher, Unit target, PlasmaLauncher weapon, boolean fastLoad, int facing) {
        return launchCoordinator.launchPlasma(launcher, target, weapon, fastLoad, facing);
    }

    /** Launch a pseudo plasma torpedo (FP1.4). */
    public ActionResult launchPseudoPlasma(Ship launcher, Unit target, PlasmaLauncher weapon, int facing) {
        return launchCoordinator.launchPseudoPlasma(launcher, target, weapon, facing);
    }

    // -------------------------------------------------------------------------
    // Shuttle launch
    // -------------------------------------------------------------------------

    /** Launch a standard (admin/GAS) shuttle from a bay (J1.5). */
    public ActionResult launchShuttle(Ship launcher, com.sfb.systemgroups.ShuttleBay bay,
            com.sfb.objects.shuttles.Shuttle shuttle, int speed, int facing) {
        return launchCoordinator.launchShuttle(launcher, bay, shuttle, speed, facing);
    }

    /** Launch a fully-armed suicide shuttle (J2.2). */
    public ActionResult launchSuicideShuttle(Ship launcher, com.sfb.systemgroups.ShuttleBay bay,
            com.sfb.objects.shuttles.SuicideShuttle shuttle, Unit target, int facing, int speed) {
        return launchCoordinator.launchSuicideShuttle(launcher, bay, shuttle, target, facing, speed);
    }

    /**
     * Declare the J1.621 special recovery procedure for a held friendly shuttle.
     */
    public ActionResult beginShuttleRecovery(Ship ship, String shuttleName) {
        return launchCoordinator.beginRecovery(ship, shuttleName);
    }

    /**
     * Package hook for ShuttleMover: pull a recovered shuttle aboard, or null if
     * the bay is not ready.
     */
    String completeRecovery(Ship ship, com.sfb.objects.shuttles.Shuttle shuttle) {
        return launchCoordinator.completeRecovery(ship, shuttle);
    }

    /** Land a friendly shuttle aboard this ship unassisted (J1.61). */
    public ActionResult landShuttle(Ship ship, String shuttleName) {
        return launchCoordinator.landShuttle(ship, shuttleName);
    }

    /** Launch a scatter pack (FD7.0). */
    public ActionResult launchScatterPack(Ship launcher, com.sfb.systemgroups.ShuttleBay bay,
            com.sfb.objects.shuttles.ScatterPack pack, Unit target, int facing, int speed) {
        return launchCoordinator.launchScatterPack(launcher, bay, pack, target, facing, speed);
    }

    /**
     * Auto-drift non-player-controlled shuttles (e.g. released ScatterPack).
     * Player-controlled shuttles (admin, GAS, HTS) are moved manually by the
     * player.
     * Called automatically when leaving the MOVEMENT phase.
     */
    private List<String> moveShuttles() {
        return shuttleMover.moveShuttles();
    }

    private List<String> moveSeekers() {
        return seekerMover.moveSeekers();
    }

    /**
     * Returns the seeker activity log from the most recent MOVEMENT phase.
     * The UI should read this after advancing past MOVEMENT.
     */
    public List<String> getLastSeekerLog() {
        return lastSeekerLog;
    }

    // --- Mines ---

    public List<SpaceMine> getMines() {
        return mines;
    }

    // -------------------------------------------------------------------------
    // Terrain
    // -------------------------------------------------------------------------

    public void addTerrain(Terrain t) {
        terrain.add(t);
        if (t.getTerrainType() == TerrainType.ASTEROID) {
            asteroidHexes.add(t.getLocation());
        } else if (t.getTerrainType() == TerrainType.PLANET
                || t.getTerrainType() == TerrainType.GAS_GIANT) {
            // Expand the footprint: every hex within the radius is no-entry
            // (planetHexes — entering without landing/atmospheric-flight rules
            // is a catastrophic landing, P2.224). Large gas giants (7+ across,
            // P2.222) split into a pure-atmosphere outer ring and an interior
            // surface; only SURFACE hexes will block line of sight (P2.321).
            Location c = t.getLocation();
            int r = t.getRadius();
            for (int col = c.getX() - r - 1; col <= c.getX() + r + 1; col++) {
                for (int row = c.getY() - r - 1; row <= c.getY() + r + 1; row++) {
                    Location hex = new Location(col, row);
                    int dist = com.sfb.utilities.MapUtils.getRange(c, hex);
                    if (dist > r)
                        continue;
                    planetHexes.add(hex);
                    if (t.isLargeGasGiant() && dist == r)
                        planetAtmosphereHexes.add(hex);
                    else
                        planetSurfaceHexes.add(hex);
                }
            }
            // Planetary rings (P2.223): enterable asteroid-like hexes in each
            // banded distance range. Kept OUT of planetHexes so ships fly
            // through them (taking ring-material collision, P2.223); any hex
            // that lands inside the body footprint is skipped — the body wins.
            for (int[] band : t.getRingBands()) {
                int inner = band[0], outer = band[1];
                for (int col = c.getX() - outer - 1; col <= c.getX() + outer + 1; col++) {
                    for (int row = c.getY() - outer - 1; row <= c.getY() + outer + 1; row++) {
                        Location hex = new Location(col, row);
                        int dist = com.sfb.utilities.MapUtils.getRange(c, hex);
                        if (dist >= inner && dist <= outer && !planetHexes.contains(hex))
                            ringHexes.add(hex);
                    }
                }
            }
        }
    }

    public List<Terrain> getTerrain() {
        return terrain;
    }

    public List<com.sfb.objects.Objective> getObjectives() {
        return objectives;
    }

    public void addObjective(com.sfb.objects.Objective o) {
        objectives.add(o);
    }

    /**
     * Bring a free objective aboard a ship (visible-capture MVP). The objective
     * must permit the retrieval method, and the ship must satisfy the same
     * preconditions as the underlying system: lock-on (D6.124/G7.412) and range
     * (transporter ≤5, tractor ≤3). On success the objective becomes CARRIED.
     * (Method-specific extras — shields-down, the SH47 multi-turn study, the
     * SH35 J1.621 shuttle-rotation tractor — are deferred refinements.)
     */
    public ActionResult pickUpObjective(Ship ship, String objectiveName, RetrievalMethod method) {
        if (currentPhase != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Objectives can only be retrieved during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(ship);
        if (cloakBlock != null)
            return cloakBlock;
        com.sfb.objects.Objective obj = objectives.stream()
                .filter(o -> o.getName().equalsIgnoreCase(objectiveName))
                .findFirst().orElse(null);
        if (obj == null)
            return ActionResult.fail("Objective not found: " + objectiveName);
        if (obj.isCarried())
            return ActionResult.fail(objectiveName + " is already aboard "
                    + obj.getCarrier().getName());
        if (!obj.allows(method))
            return ActionResult.fail(objectiveName + " cannot be retrieved by " + method
                    + " (allowed: " + obj.getAllowedRetrieval() + ")");
        int range = com.sfb.utilities.MapUtils.getRange(ship.getLocation(), obj.getLocation());
        int maxRange = method == RetrievalMethod.TRACTOR ? 3 : 5;
        if (range > maxRange)
            return ActionResult.fail(objectiveName + " is out of " + method + " range ("
                    + range + " hexes, max " + maxRange + ")");
        // Lock-on to the hex is required for either method. A tractor link is
        // automatic lock-on (G7.412), but an objective isn't a Unit to link, so
        // require the ship to have active fire control for the sensor solution.
        if (!ship.isActiveFireControl())
            return ActionResult.fail(ship.getName() + " needs active fire control to retrieve "
                    + objectiveName + " (D6.124/G7.41)");
        obj.setCarrier(ship);
        return ActionResult.ok(ship.getName() + " retrieves " + objectiveName
                + " by " + method.toString().toLowerCase());
    }

    /**
     * Drop every objective a departing ship was carrying (destroyed, captured,
     * or disengaged). Survivors fall free into the ship's last hex (SH47.475);
     * others are annihilated with it (SH35.454). Returns log lines.
     */
    List<String> dropObjectivesFrom(Ship ship, boolean shipSurvivesForCargo) {
        List<String> log = new ArrayList<>();
        for (com.sfb.objects.Objective o : new ArrayList<>(objectives)) {
            if (o.getCarrier() != ship)
                continue;
            if (o.isSurvivesCarrierDestruction() && !shipSurvivesForCargo) {
                o.setCarrier(null);
                o.setLocation(ship.getLocation());
                log.add(o.getName() + " drifts free at " + ship.getLocation()
                        + " (its carrier " + ship.getName() + " is gone)");
            } else if (!o.isSurvivesCarrierDestruction()) {
                objectives.remove(o);
                log.add(o.getName() + " was annihilated with " + ship.getName());
            }
        }
        return log;
    }

    public boolean isAsteroidHex(Location loc) {
        return loc != null && asteroidHexes.contains(loc);
    }

    /** Planetary ring hex (P2.223) — enterable, asteroid-like collision + ECM. */
    public boolean isRingHex(Location loc) {
        return loc != null && ringHexes.contains(loc);
    }

    /**
     * P3.33 / P2.223: natural ECM the target gains from terrain hexes on the
     * center-to-center line between {@code from} and {@code to} (inclusive of
     * both endpoint hexes, per P3.33). Each asteroid hex = 1 point, each ring
     * hex = ½ (the total's ½ fraction rounds up, P2.223). The caller folds this
     * into the target's ECM; the attacker's ECCM counters it as usual (P3.33).
     * Applies to fire, seeking weapons, and tractor/transporter — NOT lock-on
     * (P3.31). Counted in half-points so the round-up is exact.
     */
    int terrainEcmAlongLine(Location from, Location to) {
        if (from == null || to == null || (asteroidHexes.isEmpty() && ringHexes.isEmpty()))
            return 0;
        int halves = 0;
        for (Location hex : com.sfb.utilities.MapUtils.hexLine(from, to)) {
            if (isAsteroidHex(hex))
                halves += 2;
            else if (isRingHex(hex))
                halves += 1;
        }
        return (halves + 1) / 2; // ceil(halves / 2) — P2.223 rounds ½ up
    }

    /**
     * Result of a terrain-collision roll: the terrain kind, the die, and the
     * damage points. The caller applies the damage in its entity-specific way
     * (ship shield / drone hull / plasma strength / shuttle hull).
     */
    static final class TerrainHit {
        final String terrainName;
        final int die;          // the raw die rolled (before the nimble shift)
        final boolean nimble;   // whether the C11.21 nimble −1 was applied
        final int damage;
        TerrainHit(String terrainName, int die, boolean nimble, int damage) {
            this.terrainName = terrainName;
            this.die = die;
            this.nimble = nimble;
            this.damage = damage;
        }
    }

    /**
     * C11.21: nimble units subtract 1 from the collision die (lower die = less
     * damage on the tables), for both asteroid (P3.221) and ring (P2.223).
     * C11.33: a poor crew negates a ship's nimble benefit. Not yet modeled:
     * C11.31 loss-when-crippled/breakdown/warp, and the separate G21 crew /
     * P3.222 EM shifts. Package-private for direct unit testing.
     */
    static int nimbleAdjustedDie(int die, boolean nimble, com.sfb.systemgroups.Crew.CrewQuality crew) {
        boolean effective = nimble && crew != com.sfb.systemgroups.Crew.CrewQuality.POOR;
        return effective ? Math.max(1, die - 1) : die;
    }

    /**
     * One shared collision roll for anything entering an asteroid (P3.2) or
     * planetary ring (P2.223) hex — ships, seeking weapons, and shuttles all
     * route through here. Picks the table by hex kind, brackets the speed,
     * rolls, and applies the C11.21 nimble die-shift. Returns null when
     * {@code loc} is neither asteroid nor ring.
     *
     * @param nimble true for nimble ships and ALL shuttles/fighters (C11 note)
     * @param crew   crew quality for the C11.33 poor-crew negation, or null
     */
    TerrainHit rollTerrainCollision(Location loc, int speed, boolean nimble,
            com.sfb.systemgroups.Crew.CrewQuality crew) {
        boolean asteroid = isAsteroidHex(loc);
        boolean ring = !asteroid && isRingHex(loc);
        if (!asteroid && !ring)
            return null;
        int[][] table = asteroid ? ASTEROID_DAMAGE : RING_DAMAGE;
        int bracket = speed <= 6 ? 0 : speed <= 14 ? 1 : speed <= 25 ? 2 : 3;
        int rawDie = new com.sfb.utilities.DiceRoller().rollOneDie();
        int die = nimbleAdjustedDie(rawDie, nimble, crew);
        int damage = table[die - 1][bracket];
        return new TerrainHit(asteroid ? "asteroid" : "ring", rawDie, die != rawDie, damage);
    }

    public boolean isPlanetHex(Location loc) {
        return loc != null && planetHexes.contains(loc);
    }

    /** Solid planetary surface — blocks line of sight (P2.321/P2.322). */
    public boolean isPlanetSurfaceHex(Location loc) {
        return loc != null && planetSurfaceHexes.contains(loc);
    }

    /**
     * P2.322: true when planetary surface lies strictly between the two hexes
     * (center-to-center line through any part of a surface hex, but not along
     * its edge or corner). Atmosphere hexes never block sight (P2.321).
     */
    public boolean losBlocked(Location a, Location b) {
        return com.sfb.utilities.LosUtils.blocked(a, b, planetSurfaceHexes);
    }

    /** Fast-path guard: LOS sweeps are no-ops on maps without planetary surface. */
    boolean anyPlanetSurface() {
        return !planetSurfaceHexes.isEmpty();
    }

    /** Pure-atmosphere ring of a large gas giant (P2.222) — no-entry, but see-through. */
    public boolean isPlanetAtmosphereHex(Location loc) {
        return loc != null && planetAtmosphereHexes.contains(loc);
    }

    /** Place a T-bomb (real or dummy) via transporter (M2.31). */
    public ActionResult placeTBomb(Ship actingShip, com.sfb.properties.Location targetHex, boolean isReal) {
        return mineResolver.placeTBomb(actingShip, targetHex, isReal, -1);
    }

    /** Place a T-bomb with an explicit shield-seam choice (M2.31). */
    public ActionResult placeTBomb(Ship actingShip, com.sfb.properties.Location targetHex, boolean isReal,
            int shieldChoice) {
        return mineResolver.placeTBomb(actingShip, targetHex, isReal, shieldChoice);
    }

    /** Drop a mine or NSM from a shuttle bay into the current hex (M2.1). */
    public ActionResult dropMine(Ship actingShip, String mineType) {
        return mineResolver.dropMine(actingShip, mineType);
    }

    // -------------------------------------------------------------------------
    // Cloaking
    // -------------------------------------------------------------------------

    /**
     * Begin cloaking. The cost must have been paid during energy allocation.
     * Transitions the device to FADING_OUT.
     */
    public ActionResult cloak(Ship ship) {
        if (currentPhase != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Cloaking device can only be activated during the Activity phase");
        com.sfb.systemgroups.CloakingDevice cloak = ship.getCloakingDevice();
        if (cloak == null)
            return ActionResult.fail(ship.getName() + " has no cloaking device");
        if (!cloak.isCostPaidThisTurn())
            return ActionResult.fail(ship.getName() + " did not allocate energy for the cloaking device");
        if (!cloak.activate(clock.getImpulse()))
            return ActionResult.fail(ship.getName() + " cannot cloak now (already cloaking or cloaked this turn)");
        // G13.131: an operating cloak precludes active fire control — and through
        // it tractors (G7.41) and transporters (D6.124). Dropping FC also loses
        // the ship's own lock-ons (D6.62, G13.133) and releases its guided drones
        // (D6.122). FC reactivates when the ship leaves full cloak (uncloak(),
        // or turn start if the cost lapses).
        ship.goPassiveFc();
        List<String> log = new ArrayList<>();
        log.add(ship.getName() + " begins cloaking — fading out; fire control passive, all lock-ons lost");
        log.addAll(seekerControl.releaseOrphanedDrones());
        return ActionResult.ok(String.join("\n", log));
    }

    /**
     * Begin decloaking. Transitions the device to FADING_IN.
     */
    public ActionResult uncloak(Ship ship) {
        if (currentPhase != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Cloaking device can only be deactivated during the Activity phase");
        com.sfb.systemgroups.CloakingDevice cloak = ship.getCloakingDevice();
        if (cloak == null)
            return ActionResult.fail(ship.getName() + " has no cloaking device");
        boolean wasFullyCloaked =
                cloak.getState() == com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED;
        if (!cloak.deactivate(clock.getImpulse()))
            return ActionResult.fail(ship.getName() + " cannot decloak now (already inactive or uncloaked this turn)");
        List<String> log = new ArrayList<>();
        log.add(ship.getName() + " begins decloaking — fading in");
        log.addAll(decloakConsequences(ship, wasFullyCloaked));
        return ActionResult.ok(String.join("\n", log));
    }

    /**
     * Shared consequences of leaving full cloak, voluntary (uncloak) or forced
     * (cloak damaged mid-cloak): re-acquisition rolls (D6.113) and fire-control
     * reactivation if FC energy was paid this turn.
     *
     * G13.132: reactivation is not instant — it runs the normal 4-impulse
     * activation countdown (D6.633), and is assumed wanted unless the player
     * declines (they can cancel any time via goPassiveFireControl, D6.632).
     * The cloak restrictions persist until fade-in completes regardless
     * (G13.51, enforced by cloakActionBlock).
     */
    List<String> decloakConsequences(Ship ship, boolean wasFullyCloaked) {
        List<String> log = new ArrayList<>();
        if (wasFullyCloaked) {
            // Ship is targetable again — others roll re-acquisition (D6.113)
            log.addAll(checkLockOnsForUnit(ship));
        }
        // An active Wild Weasel forces FC to stay passive (J3.132) — activating
        // would void it (D6.65), so that must remain the player's explicit choice.
        boolean wwForcesPassive = ship.hasActiveWildWeasel()
                && !ship.getActiveWildWeasel().isPostExplosion();
        if (ship.isFcPaidThisTurn() && !ship.isActiveFireControl() && !ship.isFcActivating()
                && !wwForcesPassive) {
            ActionResult activation = beginActivatingFireControl(ship);
            if (activation.isSuccess())
                log.add(activation.getMessage() + " (G13.132)");
        }
        return log;
    }

    // --- Hit & Run raids ---

    /** All H&R-targetable systems on the given ship (D7.8). */
    public List<SystemTarget> getTargetableSystems(Ship target) {
        return boardingResolver.getTargetableSystems(target);
    }

    /**
     * Wire-format code → SystemTarget on the ship: "WEAPON:<name>",
     * "TRACTOR:<beam>", or a type name. Shared by raids and guard posting.
     */
    public SystemTarget parseRaidTargetCode(Ship ship, String code) {
        return boardingResolver.parseTargetCode(ship, code);
    }

    // --- Guards (D7.83) ---

    /**
     * Post a boarding party as a guard on the coded target (D7.83). Guards
     * are assigned at the start of the turn, so only during Energy Allocation
     * (D7.834); the BP leaves the roster while posted.
     */
    public ActionResult assignGuard(Ship ship, String targetCode, boolean commando) {
        if (!isAwaitingAllocation())
            return ActionResult.fail("Guards are posted during Energy Allocation (D7.83)");
        SystemTarget target = boardingResolver.parseTargetCode(ship, targetCode);
        if (target == null)
            return ActionResult.fail("Unknown guard target: " + targetCode);
        String err = ship.getGuardPosts().assign(target,
                commando ? com.sfb.properties.BoardingPartyQuality.COMMANDO
                         : com.sfb.properties.BoardingPartyQuality.NORMAL);
        if (err != null)
            return ActionResult.fail(err);
        return ActionResult.ok(ship.getName() + " posts a " + (commando ? "commando " : "")
                + "guard on " + target.getDisplayName() + " (D7.83)");
    }

    /** Withdraw a guard, returning the BP to the roster. EA phase only (D7.83). */
    public ActionResult removeGuard(Ship ship, String targetCode) {
        if (!isAwaitingAllocation())
            return ActionResult.fail("Guard re-tasking happens during Energy Allocation (D7.83)");
        SystemTarget target = boardingResolver.parseTargetCode(ship, targetCode);
        if (target == null)
            return ActionResult.fail("Unknown guard target: " + targetCode);
        String err = ship.getGuardPosts().release(target);
        if (err != null)
            return ActionResult.fail(err);
        return ActionResult.ok(ship.getName() + " withdraws the guard on " + target.getDisplayName());
    }

    /** Transport crew units between units at the non-combat rate (G8.32). */
    public ActionResult transportCrew(Ship source, Unit dest, int amount) {
        return boardingResolver.transportCrew(source, dest, amount);
    }

    /** Transport boarding parties onto an enemy ship (D7.31). */
    public ActionResult performBoardingAction(Ship actingShip, Ship target,
            int normal, int commandos) {
        return boardingResolver.performBoardingAction(actingShip, target, normal, commandos);
    }

    /** Execute a Hit & Run boarding raid (D7.8). */
    public ActionResult performHitAndRun(Ship actingShip, Ship target,
            List<SystemTarget> targetSystems) {
        return boardingResolver.performHitAndRun(actingShip, target, targetSystems);
    }

    // -------------------------------------------------------------------------
    // Boarding party combat (D7.3 / D7.4)
    // -------------------------------------------------------------------------

    /**
     * Result of one round of boarding party combat (D7.4).
     *
     * <p>
     * Step 3 (specific allocation) is not yet implemented — casualty points
     * are applied directly to boarding parties in Step 4. The fields here expose
     * all intermediate values so Step 3 can be added later without changing the
     * overall structure.
     */
    public static class BoardingCombatResult {
        public final int attackerPointsScored; // casualty pts scored against defender
        public final int defenderPointsScored; // casualty pts scored against attacker
        public final int defenderBPsLost; // friendly BPs removed from defender
        public final int attackerBPsLost; // enemy BPs removed from board
        public final int controlRoomsCaptured; // rooms captured this round (Step 4 fallback)
        public final boolean shipCaptured; // D7.50 condition met
        public final String log;

        public BoardingCombatResult(int attackerPts, int defenderPts,
                int defenderBPsLost, int attackerBPsLost,
                int controlRoomsCaptured, boolean shipCaptured, String log) {
            this.attackerPointsScored = attackerPts;
            this.defenderPointsScored = defenderPts;
            this.defenderBPsLost = defenderBPsLost;
            this.attackerBPsLost = attackerBPsLost;
            this.controlRoomsCaptured = controlRoomsCaptured;
            this.shipCaptured = shipCaptured;
            this.log = log;
        }
    }

    /** Resolve one round of boarding party combat on the defender (D7.4). */
    public BoardingCombatResult performBoardingCombat(Ship defender) {
        return boardingResolver.performBoardingCombat(defender);
    }

    /**
     * Ships captured during the most recent endTurn() call. Cleared at the start of
     * each endTurn().
     */
    public List<Ship> getCapturedThisTurn() {
        return Collections.unmodifiableList(capturedThisTurn);
    }

    // -------------------------------------------------------------------------
    // H&R table resolution helpers
    // -------------------------------------------------------------------------

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
        private final String message;

        private ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ActionResult ok(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        /**
         * True when the server accepted the ready signal but is waiting for other
         * players.
         */
        public boolean isWaiting() {
            return success && message != null && message.startsWith("WAITING:");
        }
    }

    /**
     * A volley of direct fire that has been calculated but not yet applied.
     * Queued during DIRECT_FIRE; applied after defenders submit reinforcement.
     */
    public static class PendingVolley {
        public final String attackerName;
        public final Ship attackerShip; // null for non-Ship attackers
        public final Unit target;
        public final int shieldNumber; // 0 for EPT enveloping seeker hits
        public final int totalDamage;
        public final int envelopingHellboreDamage;
        public final boolean addHit;
        public final boolean fusionSuicideFired;
        public final String attackerLog; // per-weapon roll results visible to attacker
        public final PlasmaTorpedo envelopingTorp; // non-null only for EPT seeker hits

        public PendingVolley(String attackerName, Ship attackerShip, Unit target,
                int shieldNumber, int totalDamage, int envelopingHellboreDamage,
                boolean addHit, boolean fusionSuicideFired, String attackerLog,
                PlasmaTorpedo envelopingTorp) {
            this.attackerName = attackerName;
            this.attackerShip = attackerShip;
            this.target = target;
            this.shieldNumber = shieldNumber;
            this.totalDamage = totalDamage;
            this.envelopingHellboreDamage = envelopingHellboreDamage;
            this.addHit = addHit;
            this.fusionSuicideFired = fusionSuicideFired;
            this.attackerLog = attackerLog;
            this.envelopingTorp = envelopingTorp;
        }
    }

    /**
     * A DAC hit that requires the defending player to choose which system is
     * destroyed. Queued by damageResolver.resolveInternalDamage(); cleared when the
     * player
     * submits via submitDacChoice().
     */
    public static class PendingDacChoice {
        public final String targetShipName;
        public final String dacType; // "phaser" | "drone" | "torp" | "weapon" | "warp" | "shuttle"
        public final int roll;
        public final java.util.List<String> options; // selectable weapon names / warp ids / space ids
        /**
         * For "shuttle" chain reactions: the bay index this choice is scoped to (-1 =
         * any bay).
         */
        public final int bayIndex;
        final Ship targetShip;
        final Ship attackerShip;
        final int remainingBleed;

        PendingDacChoice(Ship target, Ship attacker, String dacType, int roll,
                java.util.List<String> options, int remainingBleed) {
            this(target, attacker, dacType, roll, options, remainingBleed, -1);
        }

        PendingDacChoice(Ship target, Ship attacker, String dacType, int roll,
                java.util.List<String> options, int remainingBleed, int bayIndex) {
            this.targetShipName = target.getName();
            this.targetShip = target;
            this.attackerShip = attacker;
            this.dacType = dacType;
            this.roll = roll;
            this.options = options;
            this.remainingBleed = remainingBleed;
            this.bayIndex = bayIndex;
        }
    }

    public static class PendingControlOverflow {
        public final Ship ship;

        PendingControlOverflow(Ship ship) {
            this.ship = ship;
        }
    }

    /**
     * Bleed-through damage waiting to be resolved at end of Direct-Fire segment
     * (6D4).
     */
    static class PendingDamage {
        final Ship target;
        final int bleed;
        final Ship attacker; // null for self-damage (HET breakdown, fusion suicide, mines, etc.)
        final boolean isContinuation; // true = leftover bleed after a DAC choice; false = new volley

        PendingDamage(Ship target, int bleed) {
            this(target, bleed, null, false);
        }

        PendingDamage(Ship target, int bleed, Ship attacker) {
            this(target, bleed, attacker, false);
        }

        PendingDamage(Ship target, int bleed, Ship attacker, boolean isContinuation) {
            this.target = target;
            this.bleed = bleed;
            this.attacker = attacker;
            this.isContinuation = isContinuation;
        }
    }

    /**
     * The result of applying damage: bleed-through amount and internal damage log.
     */
    public static class FireResult {
        private final int bleed;
        private final List<String> internalLog;

        public FireResult(int bleed, List<String> internalLog) {
            this.bleed = bleed;
            this.internalLog = internalLog;
        }

        public int getBleed() {
            return bleed;
        }

        public List<String> getInternalLog() {
            return internalLog;
        }
    }
}
