package com.sfb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sfb.commands.Command;
import com.sfb.exceptions.CapacitorException;
import com.sfb.scenario.CoiLoadout;
import com.sfb.scenario.ScenarioLoader;
import com.sfb.scenario.ScenarioSpec;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DirectFire;

import com.sfb.objects.Drone;
import com.sfb.objects.Marker;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.SpaceMine;
import com.sfb.objects.Seeker;
import com.sfb.objects.DroneController;
import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.objects.Unit;
import com.sfb.properties.TerrainType;
import com.sfb.systems.Energy;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.systemgroups.HullBoxes;
import com.sfb.systemgroups.PowerSystems;
import com.sfb.systems.SpecialFunctions;
import com.sfb.utilities.ArcUtils;
import com.sfb.utilities.DiceRoller;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.utilities.MapUtils;
import com.sfb.utilities.MovementUtil;
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
    private final List<com.sfb.objects.shuttles.Shuttle> activeShuttles = new ArrayList<>(); // non-seeker shuttles on
                                                                                             // the map
    private final List<SpaceMine> mines = new ArrayList<>();
    private final List<Terrain> terrain = new ArrayList<>();
    private final Set<Location> asteroidHexes = new HashSet<>();
    private final Set<Location> planetHexes = new HashSet<>();

    static final int[][] ASTEROID_DAMAGE = {
            // Speed bracket: 0=1-6, 1=7-14, 2=15-25, 3=26+ (P3.2)
            { 0, 0, 0, 0 }, // die 1
            { 0, 0, 0, 5 }, // die 2
            { 0, 0, 3, 10 }, // die 3
            { 0, 2, 6, 15 }, // die 4
            { 0, 6, 10, 20 }, // die 5
            { 0, 10, 15, 30 }, // die 6
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
    private final SeekerMover     seekerMover     = new SeekerMover(this, seekers, activeShuttles, pendingVolleys, prevLocations);
    private final ShuttleMover    shuttleMover    = new ShuttleMover(this, activeShuttles);
    private final TractorResolver tractorResolver = new TractorResolver(this, ships, seekers, activeShuttles, prevLocations);
    private final Set<String> firedPairsThisPhase = new HashSet<>();
    private ImpulsePhase reinforcementReturnPhase = ImpulsePhase.ACTIVITY;
    private ImpulsePhase dacChoiceReturnPhase = ImpulsePhase.ACTIVITY;
    private ImpulsePhase controlOverflowReturnPhase = ImpulsePhase.ACTIVITY;
    private final List<PendingDacChoice> pendingDacChoices = new ArrayList<>();
    private final List<PendingControlOverflow> pendingControlOverflows = new ArrayList<>();
    // UIM: tracks which disruptors on each ship fired under UIM this impulse.
    // Burnout is rolled once per ship at END_OF_IMPULSE (6E), not per firing.
    private final Map<Ship, List<com.sfb.weapons.Disruptor>> uimUsedThisImpulse = new HashMap<>();

    public static class PendingTractorAuction {
        public final Ship attacker;
        public final Unit target;           // always a Ship; non-Ship targets resolved immediately
        public final int  attackerBid;      // effective tractor points bid
        public final int  rangeMultiplier;  // 1 for range 0-1; 2 for range 2; 3 for range 3 (G7.6)
        PendingTractorAuction(Ship attacker, Unit target, int bid, int rangeMultiplier) {
            this.attacker        = attacker;
            this.target          = target;
            this.attackerBid     = bid;
            this.rangeMultiplier = rangeMultiplier;
        }
    }

    public PendingTractorAuction getPendingTractorAuction() { return tractorResolver.pendingTractorAuction; }

    private ImpulsePhase currentPhase = ImpulsePhase.MOVEMENT;
    private List<String> lastInternalDamageLog = new ArrayList<>();
    private List<String> lastSeekerLog = new ArrayList<>();
    private List<String> lastLockOnLog = new ArrayList<>();
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
        asteroidHexes.clear();
        planetHexes.clear();

        for (Terrain t : ScenarioLoader.loadTerrain(scenario))
            addTerrain(t);

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

        TurnTracker.reset();
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
        int impulse1 = TurnTracker.getImpulse() + 1; // impulse after nextImpulse() call below
        for (Ship ship : ships) {
            if (ship.getCloakingDevice() != null)
                ship.getCloakingDevice().newTurn(impulse1);
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
        performLockOnRolls();
        List<String> orphanLog = releaseOrphanedDrones();
        if (!orphanLog.isEmpty())
            lastSeekerLog.addAll(orphanLog);
        awaitingAllocation = false;
        tractorResolver.clearRotations();
        // Advance to impulse 1 now — the Initial Activity Phase is part of the new
        // turn, so the counter (and per-impulse bookkeeping) must be current while
        // players act in it. GameStateDto reads TurnTracker during this phase.
        TurnTracker.nextImpulse();
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

    private List<Ship> getTractorLinkedShips(Ship mover) {
        return tractorResolver.getTractorLinkedShips(mover);
    }

    /**
     * Sensor Lock-On Phase (D6.1): each ship rolls 1d6 per other unit on the map.
     * Roll ≤ sensor rating → lock-on achieved. Sensor 6 is automatic (always
     * succeeds).
     * Per D6.113, each ship gets only one roll per turn.
     */
    private void performLockOnRolls() {
        lastLockOnLog.clear();
        DiceRoller dice = new DiceRoller();
        for (Ship ship : ships) {
            ship.clearLockOns();
            if (!ship.isActiveFireControl())
                continue; // D6.1143: no fire control = no lock-on
            int sensorRating = ship.getSpecialFunctions().getSensor();

            // Roll for each other ship
            for (Ship target : ships) {
                if (target == ship)
                    continue;
                if (target.getCloakingDevice() != null && target.getCloakingDevice().breaksLockOn()) {
                    lastLockOnLog.add(
                            ship.getName() + " cannot acquire lock-on to " + target.getName() + " (fully cloaked)");
                    continue;
                }
                rollLockOn(ship, target, sensorRating, dice);
            }

            // Roll for each seeker already on the map; controller always has lock-on to its
            // own
            for (Seeker seeker : seekers) {
                if (!(seeker instanceof Unit))
                    continue;
                Unit seekerUnit = (Unit) seeker;
                if (seeker.getController() == ship) {
                    ship.addLockOn(seekerUnit); // own seeker — automatic
                    continue;
                }
                rollLockOn(ship, seekerUnit, sensorRating, dice);
            }

            // Roll for each active shuttle on the map (fighters, admin shuttles, etc.)
            for (com.sfb.objects.shuttles.Shuttle shuttle : activeShuttles) {
                if (shuttle.getOwner() == ship.getOwner()) {
                    ship.addLockOn(shuttle); // own-side shuttle — automatic
                    continue;
                }
                rollLockOn(ship, shuttle, sensorRating, dice);
            }
        }
    }

    private void rollLockOn(Ship ship, Unit target, int sensorRating, DiceRoller dice) {
        if (sensorRating >= 6) {
            ship.addLockOn(target);
        } else {
            int roll = dice.rollOneDie();
            if (roll <= sensorRating) {
                ship.addLockOn(target);
                lastLockOnLog.add(ship.getName() + " acquired lock-on to " + target.getName()
                        + " (roll " + roll + " \u2264 " + sensorRating + ")");
            } else {
                lastLockOnLog.add(ship.getName() + " failed lock-on to " + target.getName()
                        + " (roll " + roll + " > " + sensorRating + ")");
            }
        }
    }

    public List<String> drainLastLockOnLog() {
        List<String> copy = new ArrayList<>(lastLockOnLog);
        lastLockOnLog.clear();
        return copy;
    }

    /**
     * Mid-turn lock-on re-check for a specific target (D6.113).
     * Called when a condition changes for {@code target} — e.g. it uncloaks,
     * emerges from behind a planet, etc.
     *
     * Each other ship with active fire control that does NOT already have
     * lock-on rolls to re-acquire. Ships that already have lock-on keep it
     * (no need to re-roll — they haven't lost it).
     *
     * If the target is cloaked, all lock-ons to it are removed immediately.
     * (Future: G13.332/G13.333 will replace this with a cloaked re-acquisition
     * roll instead of a hard remove.)
     *
     * @param target The ship whose conditions just changed.
     * @return Log lines describing the re-check results.
     */
    public List<String> checkLockOnsForUnit(Ship target) {
        List<String> log = new ArrayList<>();

        // If the target is cloaked, no one can lock onto it (D6.111)
        // Future hook: replace this block with cloaked lock-on attempt (G13.332)
        boolean targetCloaked = target.getCloakingDevice() != null
                && target.getCloakingDevice().breaksLockOn();
        if (targetCloaked) {
            for (Ship attacker : ships) {
                if (attacker == target)
                    continue;
                if (attacker.hasLockOn(target)) {
                    attacker.removeLockOn(target);
                    log.add(attacker.getName() + " lost lock-on to " + target.getName() + " (cloaked)");
                }
            }
            return log;
        }

        // Target is visible — ships without lock-on roll to re-acquire (D6.113)
        DiceRoller dice = new DiceRoller();
        for (Ship attacker : ships) {
            if (attacker == target)
                continue;
            if (!attacker.isActiveFireControl())
                continue;
            if (attacker.hasLockOn(target))
                continue; // already locked on — keep it

            int sensorRating = attacker.getSpecialFunctions().getSensor();
            int roll = sensorRating >= 6 ? 1 : dice.rollOneDie();
            if (roll <= sensorRating) {
                attacker.addLockOn(target);
                log.add(attacker.getName() + " re-acquired lock-on to " + target.getName());
            } else {
                log.add(attacker.getName() + " failed to re-acquire lock-on to " + target.getName()
                        + " (rolled " + roll + ", needs ≤" + sensorRating + ")");
            }
        }
        return log;
    }

    /**
     * Lock-on acquisition for a newly launched seeker (D6.121 / D6.113).
     * <p>
     * The launcher automatically has lock-on to the seeker it just launched.
     * Every other ship with active fire control rolls 1d6 per its sensor rating
     * to acquire lock-on on the new unit (same mechanic as
     * {@link #checkLockOnsForUnit}).
     *
     * @param launcher The ship that launched the new seeker.
     * @param newUnit  The newly launched seeker (drone, plasma, suicide shuttle,
     *                 scatter pack).
     * @return Log lines describing the acquisition results.
     */
    private List<String> checkLockOnsForNewUnit(Ship launcher, Unit newUnit) {
        List<String> log = new ArrayList<>();

        // Launcher always has lock-on to its own seeker
        launcher.addLockOn(newUnit);

        // All other ships with active fire control roll to acquire lock-on
        DiceRoller dice = new DiceRoller();
        for (Ship ship : ships) {
            if (ship == launcher)
                continue;
            if (!ship.isActiveFireControl())
                continue;

            int sensorRating = ship.getSpecialFunctions().getSensor();
            int roll = sensorRating >= 6 ? 1 : dice.rollOneDie();
            if (roll <= sensorRating) {
                ship.addLockOn(newUnit);
                log.add(ship.getName() + " acquired lock-on to " + newUnit.getName()
                        + " (rolled " + roll + ", needs ≤" + sensorRating + ")");
            }
        }
        return log;
    }

    /**
     * Compute the effective range from attacker to target (D6.21 + D6.123).
     * Formula: (noLockOn ? trueRange * 2 : trueRange) + scannerAdjustment +
     * cloakBonus
     */
    public int getEffectiveRange(Ship attacker, Unit target) {
        int trueRange = MapUtils.getRange(attacker, target);
        boolean hasLock = attacker.hasLockOn(target);
        int base = hasLock ? trueRange : trueRange * 2;
        int scanner = attacker.getSpecialFunctions().getScanner();
        int cloakBonus = 0;
        if (target instanceof Ship) {
            com.sfb.systemgroups.CloakingDevice cloak = ((Ship) target).getCloakingDevice();
            if (cloak != null)
                cloakBonus = cloak.getCloakBonus(TurnTracker.getImpulse());
        }
        return base + scanner + cloakBonus;
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
        pendingAccelDisengage.clear();
        for (Ship ship : ships) {
            if (ship.getLocation() == null || ship.isDisengaged())
                continue;
            int originalWarp = ship.getPowerSystems().getOriginalWarp();
            if (originalWarp == 0)
                continue; // no warp engines
            int currentWarp = ship.getPowerSystems().getWarpEnginePower();
            int threshold = Math.min((int) Math.ceil(originalWarp * 0.5), 15);
            if (ship.getSpeed() >= ship.getMaxAccelerationSpeed() && currentWarp >= threshold)
                pendingAccelDisengage.add(ship);
        }

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
                List<String> mineLog = processMines();
                lastSeekerLog.addAll(mineLog);
                log.addAll(lastSeekerLog);
                if (!pendingVolleys.isEmpty()) {
                    reinforcementReturnPhase = ImpulsePhase.ACTIVITY;
                    currentPhase = ImpulsePhase.REINFORCEMENT;
                } else {
                    dacChoiceReturnPhase = ImpulsePhase.ACTIVITY;
                    lastInternalDamageLog = new ArrayList<>();
                    resolveInternalDamage();
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
                    resolveInternalDamage();
                    log.addAll(lastInternalDamageLog);
                    if (currentPhase != ImpulsePhase.DAC_CHOICE)
                        currentPhase = ImpulsePhase.END_OF_IMPULSE;
                }
                break;
            case REINFORCEMENT:
                log.addAll(applyPendingVolleys());
                dacChoiceReturnPhase = reinforcementReturnPhase;
                lastInternalDamageLog = new ArrayList<>();
                resolveInternalDamage();
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
                    int eoi = TurnTracker.getImpulse();
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
                if (TurnTracker.getLocalImpulse() >= 32) {
                    ActionResult boardingResult = endTurn();
                    if (!boardingResult.getMessage().isEmpty())
                        log.add(boardingResult.getMessage());
                } else {
                    TurnTracker.nextImpulse();
                    movedThisImpulse.clear();
                    prevLocations.clear();
                    movedShuttlesThisImpulse.clear();

                    // TAC earn on Speed-4 schedule: impulses 2, 8, 16, 24 (C5.231)
                    int localImp = TurnTracker.getLocalImpulse();
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
                    cd.updateState(TurnTracker.getImpulse());
                    com.sfb.systemgroups.CloakingDevice.CloakState after = cd.getState();

                    if (before != com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED
                            && after == com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED) {
                        // Ship just became fully cloaked — remove all lock-ons (D6.111)
                        for (Ship attacker : ships) {
                            attacker.removeLockOn(ship);
                        }
                        log.add(ship.getName() + " is now fully cloaked — all lock-ons lost.");
                        log.addAll(releaseOrphanedDrones());

                    } else if (before == com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED
                            && after != com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED) {
                        // Ship just became visible again — roll re-acquisition (D6.113)
                        log.add(ship.getName() + " is decloaking — rolling re-acquisition.");
                        log.addAll(checkLockOnsForUnit(ship));
                        // FC auto-activates on decloak if energy was paid this turn
                        if (ship.isFcPaidThisTurn() && !ship.isActiveFireControl() && !ship.isFcActivating()) {
                            ship.setActiveFireControl(true);
                            log.add(ship.getName() + " fire control active (decloaked).");
                        }
                    }
                }
                // FC activation countdown check (D6.633)
                int absNow = TurnTracker.getImpulse();
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
        return (TurnTracker.getImpulse() - 1) / 32 + 1;
    }

    public int getCurrentImpulse() {
        return TurnTracker.getLocalImpulse();
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
        if (maxTurns > 0 && TurnTracker.getTurn() > maxTurns)
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

    /** Round to nearest integer using SFB S2.24 rule (0.5+ → up). */
    private static int sfbRound(double v) {
        return (int) Math.floor(v + 0.5);
    }

    private static String levelOfVictory(int myScore, int theirScore) {
        if (theirScore == 0)
            return myScore > 0 ? "Astounding Victory" : "Draw";
        double pct = 100.0 * myScore / theirScore;
        if (pct >= 500)
            return "Astounding Victory";
        if (pct >= 300)
            return "Decisive Victory";
        if (pct >= 200)
            return "Substantive Victory";
        if (pct >= 150)
            return "Tactical Victory";
        if (pct >= 110)
            return "Marginal Victory";
        if (pct >= 91)
            return "Draw";
        if (pct >= 67)
            return "Marginal Defeat";
        if (pct >= 50)
            return "Tactical Defeat";
        if (pct >= 33)
            return "Brutal Defeat";
        if (pct >= 20)
            return "Crushing Defeat";
        return "Devastating Defeat";
    }

    public Scoreboard calculateVictoryPoints() {
        // Gather all ships (active + destroyed; disengaged/captured still in ships
        // list)
        java.util.List<Ship> allShips = new java.util.ArrayList<>(ships);
        allShips.addAll(destroyedShips);

        java.util.List<ShipVpRow> rows = new java.util.ArrayList<>();

        for (Ship ship : allShips) {
            String teamName = ship.getOwner() != null ? ship.getOwner().getTeamName() : "Unknown";

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

            // Determine status — apply greatest-applies rule (S2.21)
            String status;
            int vpScored;
            if (ship.isDestroyed()) {
                status = "DESTROYED";
                vpScored = sfbRound(gabpv * 1.00);
            } else if (ship.isCaptured()) {
                status = "CAPTURED";
                vpScored = sfbRound(gabpv * 2.00);
            } else if (ship.isDisengaged()) {
                status = "DISENGAGED";
                vpScored = sfbRound(gabpv * 0.25);
            } else if (ship.isCrippled()) {
                status = "CRIPPLED";
                vpScored = sfbRound(gabpv * 0.50);
            } else if (ship.isDamaged()) {
                status = "DAMAGED";
                vpScored = sfbRound(gabpv * 0.10);
            } else {
                status = "INTACT";
                vpScored = 0;
            }

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
            teams.add(new TeamScore(team, myScore, theirScore, levelOfVictory(myScore, theirScore)));
        }

        return new Scoreboard(rows, teams);
    }

    public int getAbsoluteImpulse() {
        return TurnTracker.getImpulse();
    }

    // --- Queries ---

    public List<Ship> getShips() {
        return ships;
    }

    public List<Player> getPlayers() {
        return players;
    }

    /**
     * Try to transfer control of a seeker to the first teammate of formerController
     * that has both free control channels and lock-on to the seeker's target.
     * On success: updates controller, releases from formerController if still held
     * there,
     * and returns a log string. Returns null if no valid teammate was found.
     */
    private String autoTransferSeekerControl(Seeker seeker, Ship formerController) {
        Unit target = seeker.getTarget();
        if (target == null || formerController == null)
            return null;
        for (Ship candidate : ships) {
            if (candidate == formerController)
                continue;
            if (!isSameTeam(candidate, formerController))
                continue;
            if (!candidate.hasLockOn(target))
                continue;
            if (!candidate.acquireControl(seeker))
                continue;
            // Release from the former controller if it still holds this seeker
            Unit current = seeker.getController();
            if (current instanceof DroneController && current != candidate)
                ((DroneController) current).releaseControl(seeker);
            seeker.setController(candidate);
            seeker.setSelfGuiding(false);
            String seekerName = seeker instanceof Unit ? ((Unit) seeker).getName() : "seeker";
            return "  Control of " + seekerName + " transferred from "
                    + formerController.getName() + " to " + candidate.getName();
        }
        return null;
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

    int nextSeekerSeq() { return ++seekerSeq; }

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
                seekers.remove(seeker);
                if (seeker instanceof com.sfb.objects.shuttles.Shuttle)
                    activeShuttles.remove((com.sfb.objects.shuttles.Shuttle) seeker);
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

    public ActionResult transferSeekerControl(String seekerName, String toShipName) {
        Seeker seeker = null;
        for (Seeker s : seekers) {
            if ((s instanceof Drone
                    || s instanceof com.sfb.objects.shuttles.SuicideShuttle
                    || s instanceof com.sfb.objects.shuttles.ScatterPack)
                    && ((Unit) s).getName().equalsIgnoreCase(seekerName)) {
                seeker = s;
                break;
            }
        }
        if (seeker == null)
            return ActionResult.fail("Seeker not found: " + seekerName);
        if (seeker instanceof Drone && ((Drone) seeker).isSelfGuiding())
            return ActionResult.fail("Self-guiding drones cannot be transferred");

        Ship toShip = ships.stream()
                .filter(s -> s.getName().equalsIgnoreCase(toShipName))
                .findFirst().orElse(null);
        if (toShip == null)
            return ActionResult.fail("Ship not found: " + toShipName);

        Unit currentController = seeker.getController();
        if (!(currentController instanceof DroneController))
            return ActionResult.fail("Seeker has no valid controller");

        if (!(currentController instanceof Ship) || !isSameTeam(toShip, (Ship) currentController))
            return ActionResult.fail(toShipName + " is not on the same team as the current controller");

        Unit target = seeker.getTarget();
        if (target == null)
            return ActionResult.fail("Seeker has no target");
        if (!toShip.hasLockOn(target))
            return ActionResult.fail(toShipName + " does not have lock-on to " + target.getName());

        if (!toShip.acquireControl(seeker))
            return ActionResult.fail(toShipName + " is at control capacity");

        ((DroneController) currentController).releaseControl(seeker);
        seeker.setController(toShip);
        if (seeker instanceof Drone)
            ((Drone) seeker).setSelfGuiding(false);

        String label = seeker instanceof com.sfb.objects.shuttles.ScatterPack ? "Scatter pack"
                : seeker instanceof com.sfb.objects.shuttles.SuicideShuttle ? "Suicide shuttle"
                        : "Drone";
        return ActionResult
                .ok(label + " control transferred from " + currentController.getName() + " to " + toShipName);
    }

    public List<com.sfb.objects.shuttles.Shuttle> getActiveShuttles() {
        return activeShuttles;
    }

    /**
     * Returns all ships that may move on the current impulse and have not yet
     * moved this impulse.
     */
    public List<Ship> getMovableShips() {
        int impulse = TurnTracker.getLocalImpulse();
        List<Ship> movable = new ArrayList<>();
        for (Ship ship : ships) {
            if (ship.movesThisImpulse(impulse) && !movedThisImpulse.contains(ship)) {
                movable.add(ship);
            }
        }
        // Slower ships move first; ties broken by worst turn mode first (F > E > ... >
        // AA).
        movable.sort(Comparator.comparingInt(Ship::getSpeed)
                .thenComparingInt(s -> -s.getTurnMode().ordinal()));
        return movable;
    }

    public boolean hasMovedThisImpulse(Ship ship) {
        return movedThisImpulse.contains(ship);
    }

    public boolean canMoveThisImpulse(Ship ship) {
        if (currentPhase != ImpulsePhase.MOVEMENT)
            return false;
        if (!ship.movesThisImpulse(TurnTracker.getLocalImpulse()))
            return false;
        if (movedThisImpulse.contains(ship))
            return false;
        // Enforce order: ship may only move if no higher-priority ship is still waiting
        List<Ship> movable = getMovableShips();
        return movable.isEmpty() || movable.get(0) == ship;
    }

    /** Returns the ship that must move next, or null if none need to move. */
    public Ship nextMovableShip() {
        List<Ship> movable = getMovableShips();
        return movable.isEmpty() ? null : movable.get(0);
    }

    public boolean canFireThisPhase() {
        return currentPhase == ImpulsePhase.DIRECT_FIRE;
    }

    // --- Command execution ---

    public ActionResult execute(Command command) {
        return command.execute(this);
    }

    // --- Movement actions ---

    private ActionResult moveOrderError(Ship ship) {
        if (currentPhase != ImpulsePhase.MOVEMENT)
            return ActionResult.fail("Not the movement phase (current: " + currentPhase.getLabel() + ")");
        if (movedThisImpulse.contains(ship))
            return ActionResult.fail(ship.getName() + " has already moved this impulse");
        if (!ship.movesThisImpulse(TurnTracker.getLocalImpulse()))
            return ActionResult.fail(ship.getName() + " does not move on impulse " + TurnTracker.getLocalImpulse());
        Ship first = nextMovableShip();
        if (first != null && first != ship)
            return ActionResult.fail("Move " + first.getName() + " first (speed " + first.getSpeed() + ")");
        return ActionResult.fail(ship.getName() + " cannot move this impulse");
    }

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

        String result;
        if (confirm) {
            String teamName = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
            Set<String> badDirs = teamName != null
                    ? destructionDirectionsByTeam.getOrDefault(teamName, new HashSet<>())
                    : new HashSet<>();
            String exitDir = String.valueOf((char) ('A' + ((ship.getFacing() - 1) / 4)));
            tractorResolver.releaseAllLinksInvolving(ship); // G7.28
            if (badDirs.contains(exitDir)) {
                ship.setBattleStatus(com.sfb.properties.BattleStatus.DESTROYED);
                ship.setLocation(null);
                destroyedShips.add(ship);
                ships.remove(ship);
                gameEndResult = checkEndConditions();
                result = ship.getName() + " destroyed — disengaged by acceleration in direction " + exitDir
                        + " (destruction zone)";
            } else {
                ship.setDisengaged(true);
                ship.setLocation(null);
                result = ship.getName() + " has disengaged by acceleration (C7.1)";
            }
        } else {
            result = ship.getName() + " remained in the battle";
        }

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
        if (ship.getLocation() == null || ship.isDisengaged())
            return false;
        for (Ship other : ships) {
            if (other == ship || isSameTeam(ship, other))
                continue;
            if (other.getLocation() == null)
                continue;
            if (MapUtils.getRange(ship, other) <= 50)
                return false;
        }
        for (Seeker s : seekers) {
            if (ship.equals(s.getTarget()))
                return false;
        }
        return true;
    }

    /**
     * Disengage by separation (C7.2) — player-initiated after confirming
     * eligibility.
     */
    public List<String> getDestructionDirections(Ship ship) {
        String teamName = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
        if (teamName == null)
            return List.of();
        Set<String> dirs = destructionDirectionsByTeam.get(teamName);
        return dirs != null ? new ArrayList<>(dirs) : List.of();
    }

    // -------------------------------------------------------------------------
    // Wild Weasel (J3.0)
    // -------------------------------------------------------------------------

    /**
     * Launch a charged Wild Weasel decoy from ship's shuttle bay (J3.111).
     * The WW appears at the ship's current hex, all seekers targeting the ship
     * retarget to the WW, the ship gains +6 ECM (J3.23), and lock-ons are cleared.
     */
    /**
     * Drop a chaff pack from a fighter or shuttle (D11.3).
     * Must be called during the Activity phase (6B6 Seeking Weapons Stage).
     * On a roll of 1–4, all seekers currently targeting the shuttle lose tracking
     * and are removed.
     * Applies an 8-impulse lockout on direct-fire and seeker weapons (D11.41–42).
     */
    public ActionResult dropChaff(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (currentPhase != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Chaff can only be dropped during the Activity phase (D11.31)");
        if (shuttle instanceof com.sfb.objects.shuttles.SuicideShuttle
                || shuttle instanceof com.sfb.objects.shuttles.ScatterPack
                || shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle)
            return ActionResult.fail("SP, SS, and WW shuttles cannot drop chaff (D11.312)");
        if (shuttle.getChaffPacks() <= 0)
            return ActionResult.fail(shuttle.getName() + " has no chaff packs remaining");
        if (shuttle.isChaffLockedOut(TurnTracker.getImpulse()))
            return ActionResult.fail(shuttle.getName() + " is in chaff lockout and cannot drop another pack");

        int roll = new com.sfb.utilities.DiceRoller().rollOneDie();
        shuttle.applyChaffLockout(TurnTracker.getImpulse());

        if (roll >= 5) {
            return ActionResult.ok(shuttle.getName() + " dropped chaff (roll " + roll + ") — no effect; "
                    + shuttle.getChaffPacks() + " pack(s) remaining");
        }

        // Roll 1–4: all seekers targeting this shuttle lose tracking
        List<Seeker> distracted = seekers.stream()
                .filter(s -> shuttle.equals(s.getTarget()))
                .collect(java.util.stream.Collectors.toList());

        StringBuilder sb = new StringBuilder(shuttle.getName() + " dropped chaff (roll " + roll
                + ") — " + distracted.size() + " seeker(s) distracted");

        for (Seeker s : distracted) {
            if (s.getController() instanceof DroneController)
                ((DroneController) s.getController()).releaseControl(s);
            sb.append("\n  ").append(s instanceof Unit ? ((Unit) s).getName() : "seeker").append(" — lost tracking");
        }
        seekers.removeAll(distracted);

        sb.append("; ").append(shuttle.getChaffPacks()).append(" pack(s) remaining");
        return ActionResult.ok(sb.toString());
    }

    public ActionResult launchWildWeasel(Ship ship, String shuttleName, int facing, int speed) {
        // Find the charged admin shuttle in any bay
        com.sfb.objects.shuttles.AdminShuttle foundShuttle = null;
        com.sfb.systemgroups.ShuttleBay foundShuttleBay = null;
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
            for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                if (s instanceof com.sfb.objects.shuttles.AdminShuttle
                        && s.getName().equalsIgnoreCase(shuttleName)
                        && ((com.sfb.objects.shuttles.AdminShuttle) s).isWwReady()) {
                    foundShuttle = (com.sfb.objects.shuttles.AdminShuttle) s;
                    foundShuttleBay = bay;
                    break;
                }
            }
            if (foundShuttle != null)
                break;
        }
        if (foundShuttle == null)
            return ActionResult.fail(shuttleName + " is not a charged Wild Weasel");
        if (ship.hasActiveWildWeasel())
            return ActionResult.fail(ship.getName() + " already has an active Wild Weasel");
        if (ship.isTractored())
            return ActionResult.fail("Cannot launch Wild Weasel while held in a tractor beam (G7.98)");
        if (ship.getSpeed() > 4)
            return ActionResult.fail("Cannot launch Wild Weasel — ship speed " + ship.getSpeed()
                    + " exceeds maneuver rate limit of 4 (J3.131)");
        if (!foundShuttleBay.canLaunch(foundShuttle, getAbsoluteImpulse()))
            return ActionResult.fail("Shuttle bay is not ready to launch");

        int wwFacing = (facing >= 1 && facing <= 24) ? facing : ship.getFacing();
        int wwSpeed = Math.max(0, Math.min(6, speed));

        com.sfb.objects.shuttles.WildWeaselShuttle ww = new com.sfb.objects.shuttles.WildWeaselShuttle(ship);
        ww.setName(foundShuttle.getName());
        ww.setParentShipName(ship.getName());
        ww.setOwner(ship.getOwner());
        foundShuttleBay.launch(foundShuttle, wwSpeed, wwFacing, getAbsoluteImpulse());
        ww.setLocation(ship.getLocation());
        ww.setFacing(wwFacing);
        ww.setCurrentSpeed(wwSpeed);
        ww.setSpeed(wwSpeed);
        activeShuttles.add(ww);
        ship.setActiveWildWeasel(ww);

        // Retarget all seekers aimed at this ship to the WW (J3.111)
        for (Seeker seeker : seekers) {
            if (seeker.getTarget() == ship)
                seeker.setTarget(ww);
        }

        // Deactivate fire control and clear all lock-ons (J3.132, J3.13)
        ship.setActiveFireControl(false);
        ship.clearLockOns();

        return ActionResult.ok(ship.getName() + " launched Wild Weasel " + ww.getName()
                + " — fire control deactivated, all lock-ons lost, +6 ECM active");
    }

    /**
     * Void the active Wild Weasel for this ship (J3.13x). Seekers retarget back
     * to the ship. Called when the WW is destroyed or the ship voids it by firing.
     */
    public void voidWildWeasel(Ship ship) {
        com.sfb.objects.shuttles.WildWeaselShuttle ww = ship.getActiveWildWeasel();
        if (ww == null)
            return;

        // Retarget seekers from WW back to parent ship
        for (Seeker seeker : seekers) {
            if (seeker.getTarget() == ww)
                seeker.setTarget(ship);
        }

        activeShuttles.remove(ww);
        ship.setActiveWildWeasel(null);
    }

    /**
     * Set FC to passive immediately (D6.632). Any impulse, any phase.
     */
    public ActionResult goPassiveFireControl(Ship ship) {
        if (!ship.isActiveFireControl() && !ship.isFcActivating())
            return ActionResult.fail(ship.getName() + " fire control is already passive");
        ship.goPassiveFc();
        return ActionResult.ok(ship.getName() + " fire control set to passive");
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

    /**
     * Announce emergency deceleration (C8.0). Ship stops at the end of the
     * 2nd subsequent impulse's movement segment. Must be announced during the
     * Activity phase (Impulse Activity Segment per C8.10).
     */
    public ActionResult emergencyDeceleration(Ship ship) {
        if (getCurrentPhase() != ImpulsePhase.DIRECT_FIRE && getCurrentPhase() != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Emergency deceleration must be announced during the Activity phase (C8.10)");
        if (ship.isDecelerating())
            return ActionResult.fail(ship.getName() + " has already announced emergency deceleration");
        if (ship.getSpeed() == 0)
            return ActionResult.fail(ship.getName() + " is already stopped");
        if (ship.isImmobile(getAbsoluteImpulse()))
            return ActionResult.fail(ship.getName() + " is in the post-deceleration period");
        ship.announceEmergencyDeceleration(getAbsoluteImpulse());
        return ActionResult.ok(ship.getName() + " announces emergency deceleration — stops at end of impulse "
                + ship.getDecelerationEndsAtImpulse());
    }

    public ActionResult disengageBySeparation(Ship ship) {
        if (!canDisengageBySeparation(ship))
            return ActionResult.fail(ship.getName() + " does not meet separation disengagement conditions");
        ship.setDisengaged(true);
        ship.setLocation(null);
        return ActionResult.ok(ship.getName() + " has disengaged by separation (C7.2)");
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
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        int moveDir = MapUtils.getTrueBearing(1, ship.getFacing());
        // Planet blocking — check destination before moving (P2.0)
        Location nextHex = MapUtils.getAdjacentHex(ship.getLocation(), moveDir, mapCols, mapRows);

        // G7.36: pre-validate linked ships — refuse if any would be dragged into a planet
        List<Ship> linked = getTractorLinkedShips(ship);
        for (Ship s : linked) {
            Location sNext = MapUtils.getAdjacentHex(s.getLocation(), moveDir, mapCols, mapRows);
            if (sNext != null && isPlanetHex(sNext))
                return ActionResult.fail(s.getName() + " cannot be tractor-dragged into a planet (G7.36)");
        }

        if (nextHex == null) {
            // Determine which edge the ship is exiting
            Location cur = ship.getLocation();
            String exitEdge;
            if (cur.getX() <= 1)
                exitEdge = "LEFT";
            else if (cur.getX() >= mapCols)
                exitEdge = "RIGHT";
            else if (cur.getY() <= 1)
                exitEdge = "TOP";
            else
                exitEdge = "BOTTOM";

            String teamName = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
            Set<String> teamEdges = teamName != null ? destructionEdgesByTeam.getOrDefault(teamName, new HashSet<>())
                    : new HashSet<>();
            tractorResolver.releaseAllLinksInvolving(ship); // G7.28/G7.273
            if (teamEdges.contains(exitEdge)) {
                // Destruction edge — ship is destroyed, not disengaged
                ship.setBattleStatus(com.sfb.properties.BattleStatus.DESTROYED);
                ship.setLocation(null);
                destroyedShips.add(ship);
                ships.remove(ship);
                gameEndResult = checkEndConditions();
                return ActionResult.ok(ship.getName() + " has been destroyed (exited a destruction edge)");
            }

            // Safe edge — mark as disengaged and remove from play
            ship.setDisengaged(true);
            ship.setLocation(null);
            movedThisImpulse.add(ship);
            return ActionResult.ok(ship.getName() + " has disengaged (exited the map)");
        }
        if (isPlanetHex(nextHex)) {
            // Ship collides with planet — destroyed (P2.0)
            tractorResolver.releaseAllLinksInvolving(ship);
            ship.setBattleStatus(com.sfb.properties.BattleStatus.DESTROYED);
            ship.setLocation(null);
            destroyedShips.add(ship);
            ships.remove(ship);
            movedThisImpulse.add(ship);
            gameEndResult = checkEndConditions();
            return ActionResult.ok(ship.getName() + " collided with a planet — ship destroyed (P2.0)");
        }
        com.sfb.properties.Location prevLoc = ship.getLocation();
        boolean moved = ship.goForward(mapCols, mapRows);
        if (moved) {
            prevLocations.putIfAbsent(ship, prevLoc);
            movedThisImpulse.add(ship);
            StringBuilder log = new StringBuilder(ship.getName() + " moved forward");
            if (isAsteroidHex(ship.getLocation()))
                log.append("\n").append(applyAsteroidCollision(ship));
            List<String> collisions = checkSeekerCollisions(ship);
            if (!collisions.isEmpty())
                log.append("\n").append(String.join("\n", collisions));

            // G7.36: drag tractor-linked ships in the mover's direction;
            // do NOT add them to movedThisImpulse so they can move on their own impulse
            for (Ship s : linked) {
                Location sPrev = s.getLocation();
                Location sNext = MapUtils.getAdjacentHex(s.getLocation(), moveDir, mapCols, mapRows);
                if (sNext == null) {
                    s.setDisengaged(true);
                    s.setLocation(null);
                    log.append("\n").append(s.getName()).append(" dragged off map — disengaged");
                } else {
                    s.dragForwardInDirection(moveDir, mapCols, mapRows);
                    prevLocations.putIfAbsent(s, sPrev);
                    log.append("; ").append(s.getName()).append(" towed");
                    if (isAsteroidHex(s.getLocation()))
                        log.append("\n").append(applyAsteroidCollision(s));
                    List<String> sColl = checkSeekerCollisions(s);
                    if (!sColl.isEmpty())
                        log.append("\n").append(String.join("\n", sColl));
                }
            }

            // G7.5: drag tractored drones and shuttles in the same direction
            if (ship.getTractors() != null) {
                for (com.sfb.objects.Unit held : new ArrayList<>(ship.getTractors().getTractoredUnits())) {
                    if (held instanceof Ship) continue;
                    Location heldPrev = held.getLocation();
                    Location heldNext = MapUtils.getAdjacentHex(held.getLocation(), moveDir, mapCols, mapRows);
                    if (heldNext == null || isPlanetHex(heldNext)) {
                        // Links persist across turns now — release explicitly so the
                        // dead unit doesn't occupy a beam or hold the rotation phase open
                        ship.getTractors().releaseTractor(held);
                        held.setLocation(null);
                        seekers.removeIf(s -> s == held);
                        activeShuttles.removeIf(s -> s == held);
                        log.append("\n").append(held.getName())
                           .append(heldNext == null ? " dragged off map — destroyed" : " dragged into planet — destroyed");
                    } else {
                        held.dragForwardInDirection(moveDir, mapCols, mapRows);
                        prevLocations.putIfAbsent(held, heldPrev);
                        log.append("; ").append(held.getName()).append(" towed");
                    }
                }
            }
            return ActionResult.ok(log.toString());
        }
        return ActionResult.fail(ship.getName() + " could not move forward");
    }

    public ActionResult turnLeft(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        boolean moved = ship.turnLeft();
        if (moved) {
            movedThisImpulse.add(ship);
            if (isAsteroidHex(ship.getLocation())) {
                String hit = applyAsteroidCollision(ship);
                return ActionResult.ok(ship.getName() + " turned left\n" + hit);
            }
        }
        return moved ? ActionResult.ok(ship.getName() + " turned left")
                : ActionResult.fail(ship.getName() + " cannot turn left yet (turn mode)");
    }

    public ActionResult turnRight(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        boolean moved = ship.turnRight();
        if (moved) {
            movedThisImpulse.add(ship);
            if (isAsteroidHex(ship.getLocation())) {
                String hit = applyAsteroidCollision(ship);
                return ActionResult.ok(ship.getName() + " turned right\n" + hit);
            }
        }
        return moved ? ActionResult.ok(ship.getName() + " turned right")
                : ActionResult.fail(ship.getName() + " cannot turn right yet (turn mode)");
    }

    public ActionResult sideslipLeft(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        com.sfb.properties.Location prevLocSl = ship.getLocation();
        boolean moved = ship.sideslipLeft();
        if (moved) {
            prevLocations.putIfAbsent(ship, prevLocSl);
            movedThisImpulse.add(ship);
            StringBuilder log = new StringBuilder(ship.getName() + " sideslipped left");
            if (isAsteroidHex(ship.getLocation()))
                log.append("\n").append(applyAsteroidCollision(ship));
            // G7.36: drag tractor-linked ships in the same sideslip direction
            int slDir = MapUtils.getTrueBearing(21, ship.getFacing());
            for (Ship s : getTractorLinkedShips(ship)) {
                Location sPrev = s.getLocation();
                s.dragSideslipInDirection(slDir, mapCols, mapRows);
                prevLocations.putIfAbsent(s, sPrev);
                log.append("; ").append(s.getName()).append(" towed");
                if (isAsteroidHex(s.getLocation()))
                    log.append("\n").append(applyAsteroidCollision(s));
            }
            return ActionResult.ok(log.toString());
        }
        return ActionResult.fail(ship.getName() + " cannot sideslip (must move first)");
    }

    public ActionResult sideslipRight(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        com.sfb.properties.Location prevLocSr = ship.getLocation();
        boolean moved = ship.sideslipRight();
        if (moved) {
            prevLocations.putIfAbsent(ship, prevLocSr);
            movedThisImpulse.add(ship);
            StringBuilder log = new StringBuilder(ship.getName() + " sideslipped right");
            if (isAsteroidHex(ship.getLocation()))
                log.append("\n").append(applyAsteroidCollision(ship));
            // G7.36: drag tractor-linked ships in the same sideslip direction
            int srDir = MapUtils.getTrueBearing(5, ship.getFacing());
            for (Ship s : getTractorLinkedShips(ship)) {
                Location sPrev = s.getLocation();
                s.dragSideslipInDirection(srDir, mapCols, mapRows);
                prevLocations.putIfAbsent(s, sPrev);
                log.append("; ").append(s.getName()).append(" towed");
                if (isAsteroidHex(s.getLocation()))
                    log.append("\n").append(applyAsteroidCollision(s));
            }
            return ActionResult.ok(log.toString());
        }
        return ActionResult.fail(ship.getName() + " cannot sideslip (must move first)");
    }

    /**
     * Attempt a High Energy Turn (C6.0). The ship snaps to a new facing,
     * spending reserve warp energy and rolling for possible breakdown (C6.5).
     *
     * @param ship           The acting ship.
     * @param absoluteFacing New facing (0–5).
     */
    public ActionResult performHet(Ship ship, int absoluteFacing) {
        if (currentPhase != ImpulsePhase.MOVEMENT)
            return ActionResult.fail("HETs can only be performed during the Movement phase");
        if (ship.isCaptured())
            return ActionResult.fail("Captured ships cannot perform HETs (D7.55)");

        // Note: cloaked ships CAN HET; docked ships cannot, but docking is not yet
        // implemented.

        int currentImpulse = TurnTracker.getImpulse();

        // C6.37: cannot HET on impulse 1
        if (currentImpulse == 1)
            return ActionResult.fail("HETs cannot be performed on impulse 1 (C6.37)");

        // Breakdown immobility check
        if (ship.isImmobile(currentImpulse))
            return ActionResult.fail(ship.getName() + " is immobile until impulse "
                    + ship.getImmobileUntilImpulse() + " (breakdown)");

        // G9.421: skeleton crew requires a second crew unit to perform a HET
        if (ship.getCrew().isSkeleton() && ship.getCrew().getAvailableCrewUnits() < 2)
            return ActionResult.fail(ship.getName() + " is on skeleton crew with only "
                    + ship.getCrew().getAvailableCrewUnits()
                    + " crew unit(s) — a second crew unit is required for HET (G9.421)");

        // C6.36: 4-impulse gap between HETs
        int gap = currentImpulse - ship.getLastHetImpulse();
        if (gap < 4)
            return ActionResult.fail("Must wait at least 4 impulses between HETs — "
                    + (4 - gap) + " impulse(s) remaining (C6.36)");

        // C6.34: max 4 HETs per turn
        if (ship.getHetsThisTurn() >= 4)
            return ActionResult.fail("Maximum 4 HETs per turn reached (C6.34)");

        // C6.2: costs reserve warp energy
        int hetCost = (int) Math.ceil(ship.getPerformanceData().getHetCost());
        if (!ship.getPowerSystems().useReserveWarp(hetCost))
            return ActionResult.fail("Not enough reserve warp power for HET — need "
                    + hetCost + ", have " + ship.getPowerSystems().getReserveWarp() + " (C6.2)");

        // Update tracking before the roll so breakdown log has accurate values
        ship.setLastHetImpulse(currentImpulse);
        ship.incrementHetsThisTurn();

        int breakdownRoll = ship.rollAndPerformHet(absoluteFacing);
        boolean success = breakdownRoll < ship.getPerformanceData().getBreakdownChance();
        StringBuilder log = new StringBuilder();

        if (success) {
            log.append(ship.getName()).append(" HET → facing ").append(absoluteFacing)
                    .append(" (roll: ").append(breakdownRoll).append(")");
        } else {
            // Breakdown: apply effects and queue 2 internal DAC hits
            int internalHits = ship.applyBreakdown(currentImpulse);
            for (int i = 0; i < internalHits; i++)
                pendingInternalDamage.add(new PendingDamage(ship, 1));
            log.append(ship.getName())
                    .append(" BREAKDOWN during HET! (roll: ").append(breakdownRoll)
                    .append(") Speed→0, random facing, immobile for 16 impulses,")
                    .append(" crew -1/3, warp -1/5, 2 internal DAC hits pending.");
        }

        List<String> result = new ArrayList<>();
        result.add(log.toString());
        return ActionResult.ok(log.toString());
    }

    // --- Tactical Maneuver (C5.0) ---

    public ActionResult performTacticalTurn(Ship ship, int newFacing, boolean preferSublight) {
        if (currentPhase != ImpulsePhase.MOVEMENT)
            return ActionResult.fail("Tactical Maneuvers can only be made during the Movement phase");
        if (ship.getSpeed() != 0)
            return ActionResult.fail("Tactical Maneuvers require speed 0 (C5.41)");
        int localImpulse = TurnTracker.getLocalImpulse();
        if (localImpulse < 2)
            return ActionResult.fail("Tactical Maneuvers cannot be made on Impulse 1 (C5.11)");

        // Validate exactly 60° change
        int diff = ((newFacing - ship.getFacing()) % 24 + 24) % 24;
        if (diff != 4 && diff != 20)
            return ActionResult.fail("Tactical Maneuver must be exactly 60° (one step left or right)");

        // Consume the appropriate TAC type
        String type;
        if (preferSublight) {
            if (!ship.isSublightTacAvailable())
                return ActionResult.fail("No sublight Tactical Maneuver available (C5.12)");
            ship.consumeSublightTac();
            type = "Sublight";
        } else {
            if (ship.getTacAvailable() > 0) {
                ship.consumeWarpTac();
                type = "Warp";
            } else if (ship.isSublightTacAvailable()) {
                ship.consumeSublightTac();
                type = "Sublight";
            } else {
                return ActionResult.fail("No Tactical Maneuver available — earn one on a Speed-4 impulse (C5.231)");
            }
        }

        ship.performHet(newFacing);
        return ActionResult.ok(ship.getName() + " " + type + " Tactical Maneuver → facing " + newFacing);
    }

    // --- Fighter HET (C6.42) ---

    public ActionResult performFighterHet(com.sfb.objects.shuttles.Shuttle shuttle, int absoluteFacing) {
        if (!(shuttle instanceof com.sfb.objects.shuttles.Fighter))
            return ActionResult.fail("Only fighters can perform HETs (C6.42)");
        if (currentPhase != ImpulsePhase.MOVEMENT)
            return ActionResult.fail("HETs can only be performed during the Movement phase");
        if (shuttle.isCrippled())
            return ActionResult.fail("Crippled fighters cannot perform HETs (J1.336)");
        com.sfb.objects.shuttles.Fighter fighter = (com.sfb.objects.shuttles.Fighter) shuttle;
        boolean performed = fighter.performTacticalManeuver(absoluteFacing);
        if (!performed)
            return ActionResult.fail(fighter.getName() + " has already used its HET this turn (C6.42)");
        return ActionResult.ok(fighter.getName() + " HET → facing " + absoluteFacing);
    }

    // --- Shuttle movement ---

    /**
     * Returns shuttles that move this impulse, have not yet moved, and all
     * ships have already moved (shuttles move after all ships).
     */
    public List<com.sfb.objects.shuttles.Shuttle> getMovableShuttles() {
        if (!getMovableShips().isEmpty())
            return java.util.Collections.emptyList();
        int impulse = TurnTracker.getLocalImpulse();
        List<com.sfb.objects.shuttles.Shuttle> movable = new ArrayList<>();
        for (com.sfb.objects.shuttles.Shuttle s : activeShuttles) {
            if (!s.isPlayerControlled())
                continue;
            if (MovementUtil.moveThisImpulse(impulse, s.getSpeed())
                    && !movedShuttlesThisImpulse.contains(s)) {
                movable.add(s);
            }
        }
        return movable;
    }

    public boolean canMoveShuttleThisImpulse(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (currentPhase != ImpulsePhase.MOVEMENT)
            return false;
        if (!shuttle.isPlayerControlled())
            return false;
        if (!getMovableShips().isEmpty())
            return false;
        if (!MovementUtil.moveThisImpulse(TurnTracker.getLocalImpulse(), shuttle.getSpeed()))
            return false;
        return !movedShuttlesThisImpulse.contains(shuttle);
    }

    public ActionResult moveShuttleForward(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        shuttle.goForward(mapCols, mapRows);
        if (shuttle.getLocation() == null) {
            activeShuttles.remove(shuttle);
            return ActionResult.fail(shuttle.getName() + " moved off the map");
        }
        movedShuttlesThisImpulse.add(shuttle);
        return ActionResult.ok(shuttle.getName() + " moved forward");
    }

    public ActionResult turnShuttleLeft(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        boolean turned = shuttle.turnLeft();
        if (turned)
            movedShuttlesThisImpulse.add(shuttle);
        return turned ? ActionResult.ok(shuttle.getName() + " turned left")
                : ActionResult.fail(shuttle.getName() + " cannot turn left yet (turn mode)");
    }

    public ActionResult turnShuttleRight(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        boolean turned = shuttle.turnRight();
        if (turned)
            movedShuttlesThisImpulse.add(shuttle);
        return turned ? ActionResult.ok(shuttle.getName() + " turned right")
                : ActionResult.fail(shuttle.getName() + " cannot turn right yet (turn mode)");
    }

    public ActionResult sideslipShuttleLeft(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        boolean moved = shuttle.sideslipLeft();
        if (moved)
            movedShuttlesThisImpulse.add(shuttle);
        return moved ? ActionResult.ok(shuttle.getName() + " sideslipped left")
                : ActionResult.fail(shuttle.getName() + " cannot sideslip (must move first)");
    }

    public ActionResult sideslipShuttleRight(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        boolean moved = shuttle.sideslipRight();
        if (moved)
            movedShuttlesThisImpulse.add(shuttle);
        return moved ? ActionResult.ok(shuttle.getName() + " sideslipped right")
                : ActionResult.fail(shuttle.getName() + " cannot sideslip (must move first)");
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
        int shieldFacing = target.getRelativeShieldFacing(attacker);
        int shieldNumber = (shieldFacing % 2 == 0) ? shieldFacing / 2 : (shieldFacing + 1) / 2;
        return Math.max(1, Math.min(6, shieldNumber));
    }

    /**
     * Returns the shield(s) of {@code target} that face toward {@code attacker}.
     * Size 1: attacker is directly in front of a shield face.
     * Size 2: attacker is on the seam between two adjacent shields — caller must
     * ask the player which shield to use.
     */
    public java.util.List<Integer> getShieldCandidates(Marker attacker, Ship target) {
        int shieldFacing = target.getRelativeShieldFacing(attacker);
        if (shieldFacing % 2 != 0) {
            // Odd = center of a shield face → single candidate
            return java.util.List.of((shieldFacing + 1) / 2);
        }
        // Even = seam between two adjacent shields → two candidates
        // e.g. 2 → shields 1 & 2, 12 → shields 6 & 1
        int upper = shieldFacing / 2;
        int lower = (upper % 6) + 1;
        return java.util.List.of(upper, lower);
    }

    /**
     * Mark shield damage from one firing volley (6D2 — Direct-Fire Weapons Fire
     * Stage).
     * Bleed-through is queued as pending internal damage; it will not be resolved
     * until resolveInternalDamage() is called at the end of the Direct-Fire segment
     * (6D4).
     *
     * @return A FireResult with the bleed-through amount and an empty internal log
     *         (log is populated later when resolveInternalDamage() runs).
     */
    public FireResult markShieldDamage(Ship target, int shieldNumber, int totalDamage) {
        return markShieldDamage(target, shieldNumber, totalDamage, null);
    }

    public FireResult markShieldDamage(Ship target, int shieldNumber, int totalDamage, Ship attacker) {
        int bleed = target.damageShield(shieldNumber, totalDamage);
        if (bleed > 0) {
            pendingInternalDamage.add(new PendingDamage(target, bleed, attacker));
        }
        return new FireResult(bleed, new ArrayList<>());
    }

    /**
     * Apply Hellbore enveloping damage (E10.4) to a ship.
     *
     * Step A: consume general shield reinforcement against the total damage.
     * Step B: find the weakest shield(s); apply one equal damage group to each.
     * Fractional groups: round up for weak shields when fraction >= 0.5,
     * down otherwise (E10.412).
     * Step C: distribute the remaining group across non-weakest shields one point
     * at a time, weakest first.
     *
     * @return log lines describing how damage was distributed.
     */
    List<String> applyHellboreEnvelopingDamage(Ship target, int damage) {
        List<String> log = new ArrayList<>();
        if (damage <= 0)
            return log;

        // Step A: consume general reinforcement
        int genReinf = target.getShields().getGeneralReinforcement();
        if (genReinf > 0) {
            int absorbed = Math.min(damage, genReinf);
            target.getShields().clearGeneralReinforcement();
            damage -= absorbed;
            log.add("  Enveloping — general reinforcement absorbed " + absorbed);
            if (damage <= 0)
                return log;
        }

        // Collect current shield strengths (1-indexed), including specific
        // reinforcement
        int[] strength = new int[6];
        for (int i = 0; i < 6; i++)
            strength[i] = target.getShields().getShieldStrength(i + 1);

        // Step B: find the weakest shield strength
        int minStrength = strength[0];
        for (int s : strength)
            if (s < minStrength)
                minStrength = s;

        int weakCount = 0;
        for (int s : strength)
            if (s == minStrength)
                weakCount++;

        int stepCDamage;
        if (weakCount == 6) {
            // All shields equal — skip Step B, distribute everything in Step C
            stepCDamage = damage;
        } else {
            int groups = 1 + weakCount;
            int perGroup = damage / groups;
            int remainder = damage % groups;
            // Round up for weak shields when fractional part >= 0.5 (E10.412)
            int eachWeak = (remainder * 2 >= groups) ? perGroup + 1 : perGroup;
            stepCDamage = damage - weakCount * eachWeak;

            for (int i = 0; i < 6; i++) {
                if (strength[i] == minStrength) {
                    int bleed = target.damageShield(i + 1, eachWeak);
                    strength[i] = Math.max(0, strength[i] - eachWeak); // track for Step C ordering
                    if (bleed > 0)
                        pendingInternalDamage.add(new PendingDamage(target, bleed));
                    log.add("  Enveloping step B — shield #" + (i + 1) + " (weakest)  " + eachWeak);
                }
            }
        }

        // Step C: distribute remaining damage one point at a time, weakest first
        if (stepCDamage > 0) {
            // Only distribute to non-weakest shields (or all, if Step B was skipped)
            boolean[] eligible = new boolean[6];
            for (int i = 0; i < 6; i++)
                eligible[i] = (weakCount == 6) || (target.getShields().getShieldStrength(i + 1) > 0
                        || stepCDamage > 0);

            for (int pt = 0; pt < stepCDamage; pt++) {
                // Find shield with lowest current strength (track externally)
                int minNow = Integer.MAX_VALUE;
                for (int i = 0; i < 6; i++)
                    if (strength[i] < minNow)
                        minNow = strength[i];
                // Pick first shield at that strength
                for (int i = 0; i < 6; i++) {
                    if (strength[i] == minNow) {
                        target.damageShield(i + 1, 1);
                        strength[i] = Math.max(0, strength[i] - 1);
                        break;
                    }
                }
            }
            log.add("  Enveloping step C — " + stepCDamage + " pts distributed weakest-first");
        }

        log.add("  Enveloping total: " + damage + " across all shields");
        return log;
    }

    /**
     * Apply weapon damage to any unit — routes to the correct damage path based on
     * type.
     * Ships: damage goes through shields first, bleed-through queued as internal
     * damage.
     * Drones: damage applied directly to hull; drone destroyed and removed when
     * hull reaches 0.
     *
     * @return A log entry describing what happened.
     */
    public String applyDamageToUnit(int damage, Unit target, int shieldNumber) {
        if (target instanceof Ship) {
            if (damage == com.sfb.weapons.ADD.HIT) {
                return "ADD has no effect on ships";
            }
            Ship ship = (Ship) target;
            FireResult result = markShieldDamage(ship, shieldNumber, damage);
            return "Hit " + ship.getName() + " shield " + shieldNumber
                    + " for " + damage + " damage"
                    + (result.getBleed() > 0 ? " (" + result.getBleed() + " bleed)" : "");
        } else if (target instanceof Drone) {
            Drone drone = (Drone) target;
            if (damage == com.sfb.weapons.ADD.HIT) {
                seekers.remove(drone);
                if (drone.getController() instanceof DroneController)
                    ((DroneController) drone.getController()).releaseControl(drone);
                return "HIT — " + drone.getName() + " destroyed";
            }
            int remaining = drone.getHull() - damage;
            drone.setHull(Math.max(0, remaining));
            if (drone.getHull() <= 0) {
                seekers.remove(drone);
                if (drone.getController() instanceof DroneController)
                    ((DroneController) drone.getController()).releaseControl(drone);
                return drone.getName() + " destroyed (" + damage + " damage)";
            }
            return drone.getName() + " hit for " + damage
                    + " — " + drone.getHull() + " hull remaining";
        } else if (target instanceof com.sfb.objects.shuttles.Shuttle) {
            com.sfb.objects.shuttles.Shuttle shuttle = (com.sfb.objects.shuttles.Shuttle) target;
            boolean isSeeker = shuttle instanceof Seeker;
            if (damage == com.sfb.weapons.ADD.HIT) {
                int roll = new com.sfb.utilities.DiceRoller().rollOneDie();
                shuttle.setCurrentHull(Math.max(0, shuttle.getCurrentHull() - roll));
                if (shuttle.getCurrentHull() <= 0) {
                    if (isSeeker) {
                        seekers.remove((Seeker) shuttle);
                        if (shuttle instanceof com.sfb.objects.shuttles.SuicideShuttle) {
                            com.sfb.objects.shuttles.SuicideShuttle ss = (com.sfb.objects.shuttles.SuicideShuttle) shuttle;
                            if (ss.getController() instanceof DroneController)
                                ((DroneController) ss.getController()).releaseControl(ss);
                        } else if (shuttle instanceof com.sfb.objects.shuttles.ScatterPack) {
                            com.sfb.objects.shuttles.ScatterPack sp = (com.sfb.objects.shuttles.ScatterPack) shuttle;
                            if (sp.getController() instanceof DroneController)
                                ((DroneController) sp.getController()).releaseControl(sp);
                        }
                    } else {
                        activeShuttles.remove(shuttle);
                    }
                    return "HIT — " + shuttle.getName() + " destroyed (" + roll + " hull damage)";
                }
                StringBuilder addLog = new StringBuilder(
                        "HIT — " + shuttle.getName() + " took " + roll + " hull damage ("
                                + shuttle.getCurrentHull() + " remaining)");
                if (shuttle instanceof com.sfb.objects.shuttles.Fighter) {
                    com.sfb.objects.shuttles.Fighter f = (com.sfb.objects.shuttles.Fighter) shuttle;
                    if (f.shouldCripple())
                        addLog.append("\n  ").append(f.applyCripplingEffects());
                }
                return addLog.toString();
            }
            shuttle.setCurrentHull(Math.max(0, shuttle.getCurrentHull() - damage));
            if (shuttle.getCurrentHull() <= 0) {
                if (isSeeker) {
                    seekers.remove((Seeker) shuttle);
                    if (shuttle instanceof com.sfb.objects.shuttles.SuicideShuttle) {
                        com.sfb.objects.shuttles.SuicideShuttle ss = (com.sfb.objects.shuttles.SuicideShuttle) shuttle;
                        if (ss.getController() instanceof DroneController)
                            ((DroneController) ss.getController()).releaseControl(ss);
                    } else if (shuttle instanceof com.sfb.objects.shuttles.ScatterPack) {
                        com.sfb.objects.shuttles.ScatterPack sp = (com.sfb.objects.shuttles.ScatterPack) shuttle;
                        if (sp.getController() instanceof DroneController)
                            ((DroneController) sp.getController()).releaseControl(sp);
                    }
                } else {
                    activeShuttles.remove(shuttle);
                }
                return shuttle.getName() + " destroyed (" + damage + " damage)";
            }
            StringBuilder hitLog = new StringBuilder(shuttle.getName() + " hit for " + damage
                    + " — " + shuttle.getCurrentHull() + " hull remaining");
            if (shuttle instanceof com.sfb.objects.shuttles.Fighter) {
                com.sfb.objects.shuttles.Fighter f = (com.sfb.objects.shuttles.Fighter) shuttle;
                if (f.shouldCripple())
                    hitLog.append("\n  ").append(f.applyCripplingEffects());
            }
            return hitLog.toString();
        } else if (target instanceof PlasmaTorpedo) {
            PlasmaTorpedo torp = (PlasmaTorpedo) target;
            if (damage == com.sfb.weapons.ADD.HIT)
                return "ADD has no effect on plasma torpedoes";
            int before = torp.getCurrentStrength();
            torp.applyPhaserDamage(damage);
            int after = torp.getCurrentStrength();
            if (after <= 0) {
                seekers.remove(torp);
                return torp.getName() + " destroyed by phaser fire (" + damage + " pts)";
            }
            return torp.getName() + " hit for " + damage + " phaser pts — strength " + before + " → " + after;
        }
        return "Damage to unknown unit type ignored";
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
        if (attacker instanceof Ship && ((Ship) attacker).isCaptured())
            return attacker.getName() + " cannot fire — ship is captured (D7.55)";
        if (attacker instanceof Ship && ((Ship) attacker).getCrew().isSkeleton())
            return attacker.getName() + " cannot fire — undermanned (G9.42)";
        if (attacker instanceof Ship && ((Ship) attacker).isInBreakdownLockout(TurnTracker.getImpulse()))
            return attacker.getName() + " cannot fire — breakdown lockout for 8 impulses (C6.5471)";
        // G7.91: tractored ship can only fire direct-fire weapons at the holding ship
        if (attacker instanceof Ship && ((Ship) attacker).isTractored() && target instanceof Ship) {
            Unit holder = ((Ship) attacker).getTractoringUnit();
            if (target != holder)
                return attacker.getName() + " cannot fire at " + target.getName()
                        + " — tractored ships may only fire direct-fire weapons at the holding ship (G7.91)";
        }
        if (attacker instanceof Ship && !((Ship) attacker).isActiveFireControl()) {
            if (range > 5)
                return attacker.getName()
                        + " cannot fire — target out of passive fire control range (5 hexes max, D19.23)";
        }
        if (attacker instanceof com.sfb.objects.shuttles.Shuttle) {
            com.sfb.objects.shuttles.Shuttle s = (com.sfb.objects.shuttles.Shuttle) attacker;
            if (!s.canFireDirect(TurnTracker.getImpulse()))
                return attacker.getName() + " cannot fire yet — 8 impulses must pass since launch";
            if (s.isChaffLockedOut(TurnTracker.getImpulse()))
                return attacker.getName() + " cannot fire — chaff lockout for 8 impulses (D11.41)";
        }
        ActionResult cloakBlock = attacker instanceof Ship ? cloakActionBlock((Ship) attacker) : null;
        if (cloakBlock != null)
            return cloakBlock.getMessage();

        // Each attacker may fire at a given target only once per Direct-Fire segment
        String firePair = attacker.getName() + "→" + target.getName();
        if (!firedPairsThisPhase.add(firePair))
            return attacker.getName() + " has already fired at " + target.getName() + " this segment";

        // Firing while WW active voids the WW (J3.132)
        if (attacker instanceof Ship && ((Ship) attacker).hasActiveWildWeasel())
            voidWildWeasel((Ship) attacker);

        // Only phasers can damage plasma torpedoes (FP7.0)
        if (target instanceof PlasmaTorpedo) {
            boolean allPhasers = selected.stream().allMatch(w -> w instanceof com.sfb.weapons.PhaserWeapon);
            if (!allPhasers)
                return "Only phasers can damage plasma torpedoes (FP7.0)";
        }

        StringBuilder log = new StringBuilder();
        log.append(attacker.getName()).append("  \u2192  ").append(target.getName())
                .append("   range ").append(range);
        if (adjustedRange != range)
            log.append("  (effective ").append(adjustedRange).append(")");
        log.append("   shield #").append(shieldNumber).append("\n");

        int totalDamage = 0;
        int envelopingHellboreDamage = 0;
        boolean addHit = false;
        boolean fusionSuicideFired = false;

        Ship attackerShip = attacker instanceof Ship ? (Ship) attacker : null;
        com.sfb.systemgroups.DERFACS derfacs = attackerShip != null ? attackerShip.getDerfacs() : null;
        boolean hasDerfacs = derfacs != null && derfacs.isFunctional();

        int currentImpulse = TurnTracker.getImpulse();
        com.sfb.systemgroups.UIM activeUim = (useUim && attackerShip != null)
                ? attackerShip.getActiveUim(currentImpulse)
                : null;
        boolean uimInUse = activeUim != null;
        java.util.List<com.sfb.weapons.Disruptor> uimFiredDisruptors = new java.util.ArrayList<>();

        // D6.34/D6.35: net ECM = target ECM − attacker ECCM; shift = floor(√net)
        Ship targetShip = target instanceof Ship ? (Ship) target : null;
        int targetEcm = targetShip != null ? targetShip.getEcmAllocated() : 0;
        int attackerEccm = attackerShip != null && attackerShip.isActiveFireControl()
                ? attackerShip.getEccmAllocated()
                : 0;
        int netEcm = Math.max(0, targetEcm - attackerEccm);
        int ecmShift = (int) Math.floor(Math.sqrt(netEcm));
        if (ecmShift > 0)
            log.append("  ECM shift: +").append(ecmShift).append(" (target ECM ").append(targetEcm)
                    .append(", attacker ECCM ").append(attackerEccm).append(")\n");

        for (Weapon w : selected) {
            w.setEcmShift(ecmShift);
            if (!w.isFunctional()) {
                log.append("  ").append(w.getName()).append("  destroyed — cannot fire\n");
                continue;
            }
            try {
                boolean isFusionSuicide = w instanceof com.sfb.weapons.Fusion
                        && ((com.sfb.weapons.Fusion) w).getArmingType() == com.sfb.properties.WeaponArmingType.SPECIAL;
                int dmg;
                if (uimInUse && w instanceof com.sfb.weapons.Disruptor) {
                    com.sfb.weapons.Disruptor d = (com.sfb.weapons.Disruptor) w;
                    if (d.isUimLocked(currentImpulse)) {
                        log.append("  ").append(w.getName()).append("  UIM-locked\n");
                        continue;
                    }
                    dmg = d.fireUim(range, adjustedRange);
                    uimFiredDisruptors.add(d);
                } else if (hasDerfacs && w instanceof com.sfb.weapons.Disruptor) {
                    dmg = ((com.sfb.weapons.Disruptor) w).fireDerfacs(range, adjustedRange);
                } else if (directFire && w instanceof com.sfb.weapons.Hellbore) {
                    dmg = ((com.sfb.weapons.Hellbore) w).fireDirect(adjustedRange);
                } else {
                    dmg = ((DirectFire) w).fire(range, adjustedRange);
                }
                if (isFusionSuicide)
                    fusionSuicideFired = true;
                String rollStr = w.getLastRoll() > 0 ? "  (die " + w.getLastRoll() + ")" : "";
                if (dmg == ADD.HIT) {
                    addHit = true;
                    log.append("  ").append(w.getName()).append(rollStr).append("  HIT\n");
                } else if (!directFire && w instanceof com.sfb.weapons.Hellbore) {
                    envelopingHellboreDamage += dmg;
                    log.append("  ").append(w.getName()).append(rollStr)
                            .append(dmg > 0 ? "  HIT  " + dmg + " (enveloping)" : "  MISS").append("\n");
                } else {
                    totalDamage += dmg;
                    log.append("  ").append(w.getName()).append(rollStr)
                            .append(dmg > 0
                                    ? "  HIT  " + dmg
                                            + (directFire && w instanceof com.sfb.weapons.Hellbore ? " (direct)" : "")
                                    : "  MISS")
                            .append("\n");
                }
            } catch (WeaponUnarmedException ex) {
                log.append("  ").append(w.getName()).append("  unarmed\n");
            } catch (TargetOutOfRangeException ex) {
                log.append("  ").append(w.getName()).append("  out of range\n");
            } catch (CapacitorException ex) {
                log.append("  ").append(w.getName()).append("  no capacitor energy\n");
            } finally {
                w.setEcmShift(0);
            }
        }

        // UIM: accumulate disruptors that fired under UIM this impulse.
        if (uimInUse && !uimFiredDisruptors.isEmpty() && attackerShip != null) {
            uimUsedThisImpulse
                    .computeIfAbsent(attackerShip, k -> new ArrayList<>())
                    .addAll(uimFiredDisruptors);
        }

        if (target instanceof Ship) {
            // Queue the volley — damage applied after defenders spend reserve power.
            // (When Base is implemented add: || target instanceof Base)
            log.append("  Total damage: ").append(totalDamage)
                    .append(" — queued, resolves in Reinforcement phase\n");
            pendingVolleys.add(new PendingVolley(
                    attacker.getName(), attackerShip, target,
                    shieldNumber, totalDamage, envelopingHellboreDamage,
                    addHit, fusionSuicideFired, log.toString(), null));
        } else {
            // Non-ship targets (seekers, shuttles) have no shields — apply immediately.
            if (fusionSuicideFired && attackerShip != null) {
                pendingInternalDamage.add(new PendingDamage(attackerShip, 1));
                log.append("  Fusion suicide overload — 1 internal damage to ")
                        .append(attackerShip.getName()).append("\n");
            }
            if (addHit) {
                String dmgLog = applyDamageToUnit(ADD.HIT, target, shieldNumber);
                log.append("  ADD result: ").append(dmgLog).append("\n");
            }
            if (totalDamage > 0) {
                String dmgLog = applyDamageToUnit(totalDamage, target, shieldNumber);
                log.append("  ").append(dmgLog).append("\n");
            }
        }

        return log.toString();
    }

    /**
     * Apply all pending fire volleys (called when transitioning out of
     * REINFORCEMENT).
     * Defenders must have already submitted any reinforcement decisions before this
     * runs.
     */
    /**
     * All damage arriving through the same shield facing in the same phase is one
     * volley (C3.14): shields absorb the combined total and bleed-through runs
     * through the DAC once.  EPT volleys (envelopingTorp != null) are always
     * separate and distribute across all 6 shields.  Enveloping Hellbore damage
     * is always a separate volley per E10.43 and is not combined.
     */
    private List<String> applyPendingVolleys() {
        List<String> log = new ArrayList<>();

        // Helper: one accumulated group per (target identity, shieldNumber)
        class ShieldGroup {
            final Unit        target;
            final int         shieldNumber;
            final StringBuilder pvLog = new StringBuilder();
            int               totalDamage = 0;
            Ship              lastAttacker = null;
            final List<Integer> hellboreDamages = new ArrayList<>();

            ShieldGroup(Unit target, int shieldNumber) {
                this.target      = target;
                this.shieldNumber = shieldNumber;
            }
        }

        List<ShieldGroup>  groups  = new ArrayList<>();
        List<PendingVolley> eptVolleys = new ArrayList<>();

        for (PendingVolley pv : pendingVolleys) {
            // EPT: distribute to all shields — always separate
            if (pv.envelopingTorp != null) { eptVolleys.add(pv); continue; }

            // Non-Ship targets (seekers, shuttles) have no shields — apply immediately
            if (!(pv.target instanceof Ship)) {
                StringBuilder pvLog = new StringBuilder(pv.attackerLog);
                if (pv.fusionSuicideFired && pv.attackerShip != null) {
                    pendingInternalDamage.add(new PendingDamage(pv.attackerShip, 1));
                    pvLog.append("  Fusion suicide overload — 1 internal damage to ")
                         .append(pv.attackerShip.getName()).append("\n");
                }
                if (pv.addHit) {
                    pvLog.append("  ADD result: ")
                         .append(applyDamageToUnit(ADD.HIT, pv.target, pv.shieldNumber)).append("\n");
                }
                if (pv.totalDamage > 0) {
                    pvLog.append("  ")
                         .append(applyDamageToUnit(pv.totalDamage, pv.target, pv.shieldNumber))
                         .append("\n");
                }
                log.add(pvLog.toString());
                continue;
            }

            // Find or create the group for this (target, shield facing)
            ShieldGroup g = null;
            for (ShieldGroup candidate : groups)
                if (candidate.target == pv.target && candidate.shieldNumber == pv.shieldNumber)
                    { g = candidate; break; }
            if (g == null) { g = new ShieldGroup(pv.target, pv.shieldNumber); groups.add(g); }

            // Per-volley effects accumulated into the group log
            g.pvLog.append(pv.attackerLog);
            if (pv.fusionSuicideFired && pv.attackerShip != null) {
                pendingInternalDamage.add(new PendingDamage(pv.attackerShip, 1));
                g.pvLog.append("  Fusion suicide overload — 1 internal damage to ")
                       .append(pv.attackerShip.getName()).append("\n");
            }
            if (pv.addHit) {
                g.pvLog.append("  ADD result: ")
                       .append(applyDamageToUnit(ADD.HIT, pv.target, pv.shieldNumber)).append("\n");
            }

            g.totalDamage += pv.totalDamage;
            if (pv.attackerShip != null) g.lastAttacker = pv.attackerShip;
            // Hellbore enveloping stays per-volley (E10.43)
            if (pv.envelopingHellboreDamage > 0)
                g.hellboreDamages.add(pv.envelopingHellboreDamage);
        }

        // Apply each group: combined shield damage → single bleed-through chain
        for (ShieldGroup g : groups) {
            Ship target = (Ship) g.target;
            int bleed = target.damageShield(g.shieldNumber, g.totalDamage);
            if (bleed > 0) {
                g.pvLog.append("  BLEED-THROUGH: ").append(bleed)
                       .append(" (resolves at end of segment)\n");
                pendingInternalDamage.add(new PendingDamage(target, bleed, g.lastAttacker));
            }
            // Enveloping Hellbore: each volley is its own separate bleed chain (E10.43)
            for (int envDmg : g.hellboreDamages) {
                g.pvLog.append("  Hellbore enveloping volley: ").append(envDmg).append("\n");
                for (String line : applyHellboreEnvelopingDamage(target, envDmg))
                    g.pvLog.append(line).append("\n");
            }
            log.add(g.pvLog.toString());
        }

        // EPT volleys: spread damage across all 6 shields (always separate)
        for (PendingVolley pv : eptVolleys) {
            StringBuilder pvLog = new StringBuilder(pv.attackerLog);
            if (pv.fusionSuicideFired && pv.attackerShip != null) {
                pendingInternalDamage.add(new PendingDamage(pv.attackerShip, 1));
                pvLog.append("  Fusion suicide overload — 1 internal damage to ")
                     .append(pv.attackerShip.getName()).append("\n");
            }
            if (pv.addHit) {
                pvLog.append("  ADD result: ")
                     .append(applyDamageToUnit(ADD.HIT, pv.target, pv.shieldNumber)).append("\n");
            }
            int[] spread = pv.envelopingTorp.computeEnvelopingDamage(pv.totalDamage);
            for (int i = 0; i < 6; i++)
                if (spread[i] > 0) markShieldDamage((Ship) pv.target, i + 1, spread[i]);
            if (pv.envelopingHellboreDamage > 0) {
                pvLog.append("  Hellbore enveloping volley: ").append(pv.envelopingHellboreDamage).append("\n");
                for (String line : applyHellboreEnvelopingDamage((Ship) pv.target, pv.envelopingHellboreDamage))
                    pvLog.append(line).append("\n");
            }
            log.add(pvLog.toString());
        }

        pendingVolleys.clear();
        firedPairsThisPhase.clear();
        return log;
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

    /**
     * Resolve all queued internal damage (6D4 — Direct-Fire Weapons Damage
     * Resolution Stage). Stops early and transitions to DAC_CHOICE if the
     * defender must pick which system is hit. Remaining items stay in
     * {@code pendingInternalDamage} so resolution resumes after the choice.
     */
    private void resolveInternalDamage() {
        if (lastInternalDamageLog == null)
            lastInternalDamageLog = new ArrayList<>();
        while (!pendingInternalDamage.isEmpty()) {
            PendingDamage pd = pendingInternalDamage.remove(0);
            if (!pd.isContinuation)
                pd.target.resetPhaserDacGroup();
            Ship.DamageResult result = pd.target.applyInternalDamage(pd.bleed, pd.attacker);
            lastInternalDamageLog.add("=== Internal damage — " + pd.target.getName() + " ===");
            lastInternalDamageLog.addAll(result.log);
            if (result.choiceRequired) {
                pendingDacChoices.add(new PendingDacChoice(
                        pd.target, pd.attacker, result.choiceType,
                        result.choiceRoll, result.options, result.remainingBleed));
                currentPhase = ImpulsePhase.DAC_CHOICE;
                cleanupDestroyedShips();
                return;
            }
        }
        cleanupDestroyedShips();
        checkControlOverflow();
    }

    private void cleanupDestroyedShips() {
        ships.removeIf(s -> {
            if (s.isDestroyed()) {
                tractorResolver.releaseAllLinksInvolving(s);
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

        if ("shuttle".equals(pending.dacType)) {
            // Parse "bay:<b>:space:<s>"
            String[] parts = chosenSystem.split(":");
            int bayIdx = Integer.parseInt(parts[1]);
            int spaceIdx = Integer.parseInt(parts[3]);

            com.sfb.systemgroups.ShuttleBay bay = pending.targetShip.getShuttles().getBays().get(bayIdx);
            int killedCrews = bay.getSpaces().get(spaceIdx).getDeckCrews();
            com.sfb.objects.shuttles.Shuttle was = bay.destroySpace(spaceIdx);

            String occupant = was != null ? was.getName() : "empty space";
            String choiceLog = "  shuttle DAC hit: bay " + bayIdx + " space " + spaceIdx
                    + " (" + occupant + ") DESTROYED";
            lastInternalDamageLog.add(choiceLog);

            if (killedCrews > 0) {
                pending.targetShip.getCrew().killDeckCrews(killedCrews);
                lastInternalDamageLog.add("  → " + killedCrews + " deck crew(s) killed in bay destruction");
            }

            if (was != null && was.isArmed()) {
                lastInternalDamageLog.add("  → armed shuttle destroyed — chain reaction! (D12.10)");

                // Chain reaction: one additional space destroyed in the same bay (player
                // chooses)
                java.util.List<String> chainOpts = new java.util.ArrayList<>();
                java.util.List<com.sfb.systemgroups.ShuttleSpace> baySpaces = bay.getSpaces();
                for (int s = 0; s < baySpaces.size(); s++) {
                    if (!baySpaces.get(s).isDestroyed())
                        chainOpts.add("bay:" + bayIdx + ":space:" + s);
                }
                if (!chainOpts.isEmpty()) {
                    pendingDacChoices.add(0, new PendingDacChoice(
                            pending.targetShip, pending.attackerShip, "shuttle",
                            -1, chainOpts, 0, bayIdx));
                }

                // One random internal damage point on the ship (separate volley, D12.10)
                pendingInternalDamage.add(0, new PendingDamage(
                        pending.targetShip, 1, pending.attackerShip));
            }
        } else {
            String hitLabel = pending.targetShip.applyDacChoiceHit(
                    pending.dacType, chosenSystem, pending.attackerShip);
            String choiceLog = "  internal [" + pending.roll + "]: " + pending.dacType
                    + " — player chose " + chosenSystem
                    + (hitLabel != null ? " → " + hitLabel : " (no effect)");
            lastInternalDamageLog.add(choiceLog);

            // Prepend remaining bleed for this ship so resolveInternalDamage picks it up
            if (pending.remainingBleed > 0)
                pendingInternalDamage.add(0, new PendingDamage(
                        pending.targetShip, pending.remainingBleed, pending.attackerShip, true));
        }

        resolveInternalDamage();

        // resolveInternalDamage() leaves currentPhase as DAC_CHOICE when it returns
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

    // --- Lab seeker identification ---

    /**
     * Attempt to identify a list of enemy seekers using the acting ship's labs.
     * Each attempt costs 1 lab. Roll 1d6; result must be STRICTLY GREATER than
     * range to succeed.
     * Pseudo-plasma torps cannot be identified (attempt always fails to reveal
     * pseudo status).
     */
    public ActionResult identifySeekers(Ship actingShip, List<String> seekerNames) {
        if (currentPhase != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Lab identification can only be attempted during the Activity phase");
        if (actingShip.getLabs().getAvailableLab() <= 0)
            return ActionResult.fail(actingShip.getName() + " has no available labs");
        if (seekerNames == null || seekerNames.isEmpty())
            return ActionResult.fail("No seekers selected");
        int availLabs = actingShip.getLabs().getAvailableLab();
        if (seekerNames.size() > availLabs)
            return ActionResult
                    .fail("Selected " + seekerNames.size() + " seekers but only " + availLabs + " labs available");

        StringBuilder log = new StringBuilder(actingShip.getName() + " lab identification attempt\n");
        DiceRoller dice = new DiceRoller();

        for (String seekerName : seekerNames) {
            Seeker seeker = seekers.stream()
                    .filter(s -> ((com.sfb.objects.Marker) s).getName().equals(seekerName))
                    .findFirst().orElse(null);
            if (seeker == null) {
                log.append("  ").append(seekerName).append(" — not found\n");
                continue;
            }
            // Cannot attempt to ID own seekers
            Unit seekerShip = seeker.getController();
            if (seekerShip instanceof Ship && ((Ship) seekerShip).getFaction() == actingShip.getFaction()) {
                log.append("  ").append(seekerName).append(" — cannot ID friendly seeker\n");
                continue;
            }

            actingShip.getLabs().decrementLab();
            int range = MapUtils.getRange(actingShip, (com.sfb.objects.Marker) seeker);
            int roll = dice.rollOneDie();
            log.append("  ").append(seekerName)
                    .append("  range ").append(range)
                    .append("  (die ").append(roll).append(")");

            if (roll > range) {
                // Pseudo-plasma: identify() call is harmless but we don't announce type
                seeker.identify();
                log.append("  — IDENTIFIED\n");
            } else {
                log.append("  — FAILED\n");
            }
        }
        return ActionResult.ok(log.toString());
    }

    // --- Drone launching ---

    public boolean canLaunchThisPhase() {
        return currentPhase == ImpulsePhase.ACTIVITY;
    }

    /**
     * Returns a failure message if the ship's cloak is restricting actions, or null
     * if clear.
     */
    private ActionResult cloakActionBlock(Ship ship) {
        com.sfb.systemgroups.CloakingDevice cloak = ship.getCloakingDevice();
        if (cloak != null && cloak.isRestrictingActions())
            return ActionResult.fail(ship.getName() + " cannot act while cloaking device is active");
        return null;
    }

    /**
     * Launch one drone from the given rack at the given target.
     * The drone is placed at the launching ship's location, faced toward the
     * target, and added to the active seekers list.
     *
     * @return ActionResult describing success or reason for failure.
     */
    public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Drones can only be launched during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch seeking weapons — breakdown lockout for 8 impulses (C6.5473)");
        // G7.943: tractored ship may only launch seeking weapons at the holding ship
        if (launcher.isTractored() && target != launcher.getTractoringUnit())
            return ActionResult.fail("Tractored ships may only launch seeking weapons at the holding ship (G7.943)");
        if (!rack.isFunctional())
            return ActionResult.fail(rack.getName() + " is destroyed");
        if (!rack.canFire())
            return ActionResult.fail(rack.getName() + " cannot launch yet (once per turn, 8-impulse delay)");
        if (rack.isEmpty())
            return ActionResult.fail(rack.getName() + " has no drones loaded");
        if (!launcher.isActiveFireControl()) {
            // PFC: only self-guiding drones may be launched (D19.221)
            if (!rack.getAmmo().get(0).isSelfGuiding())
                return ActionResult.fail("Passive fire control — cannot launch non-self-guiding drones (D19.22)");
            // Self-guiding drones acquire their own lock-on after launch — no pre-launch
            // lock-on needed
        } else if (!launcher.hasLockOn(target)) {
            return ActionResult.fail("No sensor lock-on to target — cannot launch seeking weapons (D6.121)");
        }

        return launchDrone(launcher, target, rack, rack.getAmmo().get(0), 0);
    }

    /**
     * Launch a specific drone from the given rack at the given target.
     */
    public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack, Drone drone, int facing) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Drones can only be launched during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch seeking weapons — breakdown lockout for 8 impulses (C6.5473)");
        // G7.943: tractored ship may only launch seeking weapons at the holding ship
        if (launcher.isTractored() && target != launcher.getTractoringUnit())
            return ActionResult.fail("Tractored ships may only launch seeking weapons at the holding ship (G7.943)");
        if (!rack.isFunctional())
            return ActionResult.fail(rack.getName() + " is destroyed");
        if (!rack.canFire())
            return ActionResult.fail(rack.getName() + " cannot launch yet (once per turn, 8-impulse delay)");
        if (!rack.getAmmo().contains(drone))
            return ActionResult.fail("Drone is not in " + rack.getName());
        if (!launcher.isActiveFireControl() && !drone.isSelfGuiding())
            return ActionResult.fail("Passive fire control — cannot launch non-self-guiding drones (D19.22)");
        if (!drone.isSelfGuiding()) {
            if (!launcher.acquireControl(drone)) {
                // Over limit — force-add and queue overflow interrupt for player to resolve
                launcher.forceAcquireControl(drone);
            }
        }

        // J3.41: launching a seeking weapon voids the launcher's own WW
        if (launcher.hasActiveWildWeasel())
            voidWildWeasel(launcher);

        rack.getAmmo().remove(drone);
        rack.recordLaunch();
        drone.setName(launcher.getName() + "-Drone-" + (++seekerSeq));
        drone.setLocation(launcher.getLocation());
        drone.setFacing(facing > 0 ? facing : MapUtils.getBearing(launcher, target));
        // J3.201: redirect to WW if target ship has an active/exploding WW (not
        // post-explosion)
        Unit droneTarget = target;
        if (target instanceof Ship) {
            com.sfb.objects.shuttles.WildWeaselShuttle ww = ((Ship) target).getActiveWildWeasel();
            if (ww != null && !ww.isPostExplosion())
                droneTarget = ww;
        }
        drone.setTarget(droneTarget);
        if (drone.getController() == null)
            drone.setController(launcher);
        drone.setLauncherName(launcher.getName());
        drone.setLaunchImpulse(TurnTracker.getImpulse());
        drone.setSeekerType(Seeker.SeekerType.DRONE);
        seekers.add(drone);
        List<String> lockLog = checkLockOnsForNewUnit(launcher, drone);

        String msg = launcher.getName() + " launched " + drone.getDroneType()
                + " drone at " + target.getName();
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        checkControlOverflow();
        return ActionResult.ok(msg);
    }

    /**
     * Launch a plasma torpedo from the given launcher at the target.
     * The launcher must be armed. The torpedo is placed at the launcher's
     * location, faced toward the target, and added to the active seekers list.
     */
    public ActionResult launchPlasma(Ship launcher, Unit target, PlasmaLauncher weapon, boolean fastLoad, int facing) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Plasma can only be launched during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch plasma — breakdown lockout for 8 impulses (C6.5473)");
        // G7.91: tractored ship cannot fire plasma torpedoes at non-holding ships
        if (launcher.isTractored() && target instanceof Ship && target != launcher.getTractoringUnit())
            return ActionResult.fail("Tractored ships may only fire plasma at the holding ship (G7.91)");
        if (!weapon.isFunctional())
            return ActionResult.fail(weapon.getName() + " is destroyed");
        if (fastLoad) {
            if (!weapon.canFastLoad())
                return ActionResult.fail(weapon.getName() + " is not eligible for fast-load (FP1.93)");
            if (!launcher.getPowerSystems().useBattery(2))
                return ActionResult.fail("Not enough battery for fast-load — requires 2 points (FP1.93)");
            weapon.applyFastLoad();
        }
        if (!weapon.isArmed())
            return ActionResult.fail(weapon.getName() + " is not armed");
        // Validate launch facing is within the launcher's allowed directions
        // (ship-relative arc)
        if (facing > 0) {
            int launchDirs = weapon.getLaunchDirections() != 0 ? weapon.getLaunchDirections() : weapon.getArcs();
            int relFacing = MapUtils.getRelativeBearing(facing, launcher.getFacing());
            if (!ArcUtils.inArc(relFacing, launchDirs))
                return ActionResult
                        .fail(weapon.getName() + " cannot launch in direction " + facing + " — outside launcher arc");
        }

        PlasmaTorpedo torpedo = weapon.launch();
        if (torpedo == null)
            return ActionResult.fail(weapon.getName() + " failed to launch");

        // J3.41: launching a seeking weapon voids the launcher's own WW
        if (launcher.hasActiveWildWeasel())
            voidWildWeasel(launcher);

        torpedo.setName(launcher.getName() + "-Plasma-" + (++seekerSeq));
        torpedo.setLocation(launcher.getLocation());
        torpedo.setFacing(facing > 0 ? facing : MapUtils.getBearing(launcher, target));
        // J3.201: redirect to WW if target ship has an active/exploding WW (not
        // post-explosion)
        Unit torpTarget = target;
        if (target instanceof Ship) {
            com.sfb.objects.shuttles.WildWeaselShuttle ww = ((Ship) target).getActiveWildWeasel();
            if (ww != null && !ww.isPostExplosion())
                torpTarget = ww;
        }
        torpedo.setTarget(torpTarget);
        torpedo.setController(launcher);
        torpedo.setLaunchImpulse(TurnTracker.getImpulse());
        torpedo.setSeekerType(Seeker.SeekerType.PLASMA);
        seekers.add(torpedo);
        List<String> lockLog = checkLockOnsForNewUnit(launcher, torpedo);

        String msg = launcher.getName() + " launched plasma-"
                + torpedo.getPlasmaType() + " at " + target.getName();
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        return ActionResult.ok(msg);
    }

    public ActionResult launchPseudoPlasma(Ship launcher, Unit target, PlasmaLauncher weapon, int facing) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Plasma can only be launched during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch plasma — breakdown lockout for 8 impulses (C6.5473)");
        if (!weapon.isFunctional())
            return ActionResult.fail(weapon.getName() + " is destroyed");
        if (!weapon.canLaunchPseudo())
            return ActionResult.fail(weapon.getName() + " cannot launch pseudo plasma now");
        PlasmaTorpedo torpedo = weapon.launchPseudo();
        if (torpedo == null)
            return ActionResult.fail(weapon.getName() + " failed to launch pseudo plasma");

        torpedo.setName(launcher.getName() + "-Pseudo-" + (++seekerSeq));
        torpedo.setLocation(launcher.getLocation());
        torpedo.setFacing(facing > 0 ? facing : MapUtils.getBearing(launcher, target));
        torpedo.setTarget(target);
        torpedo.setController(launcher);
        torpedo.setLaunchImpulse(TurnTracker.getImpulse());
        torpedo.setSeekerType(Seeker.SeekerType.PLASMA);
        seekers.add(torpedo);
        List<String> lockLog = checkLockOnsForNewUnit(launcher, torpedo);

        String msg = launcher.getName() + " launched pseudo plasma-"
                + torpedo.getPlasmaType() + " at " + target.getName() + " [PSEUDO]";
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        return ActionResult.ok(msg);
    }

    // -------------------------------------------------------------------------
    // Shuttle launch
    // -------------------------------------------------------------------------

    /**
     * Launch a standard (admin/GAS) shuttle from a bay.
     * The shuttle moves independently on the map but is not a seeker.
     */
    public ActionResult launchShuttle(Ship launcher, com.sfb.systemgroups.ShuttleBay bay,
            com.sfb.objects.shuttles.Shuttle shuttle, int speed, int facing) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Shuttles can only be launched during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInPostHetWindow(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch shuttles within 4 impulses of a HET (C6.38)");
        if (launcher.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch shuttles — breakdown lockout for 8 impulses (C6.5472)");
        if (!bay.canLaunch(shuttle, TurnTracker.getImpulse()))
            return ActionResult.fail("Shuttle bay on cooldown — once every 2 impulses");

        com.sfb.objects.shuttles.Shuttle launched = bay.launch(shuttle, speed, facing, TurnTracker.getImpulse());
        if (launched == null)
            return ActionResult.fail("Shuttle not found in bay");

        launched.setLocation(launcher.getLocation());
        launched.setParentShipName(launcher.getName());
        launched.setOwner(launcher.getOwner());
        launched.setLaunchImpulse(TurnTracker.getImpulse());
        activeShuttles.add(launched);
        return ActionResult.ok(launcher.getName() + " launched shuttle " + launched.getName());
    }

    /**
     * Launch a fully-armed suicide shuttle at a target.
     * Requires lock-on. Speed capped at shuttle's maxSpeed.
     */
    public ActionResult launchSuicideShuttle(Ship launcher, com.sfb.systemgroups.ShuttleBay bay,
            com.sfb.objects.shuttles.SuicideShuttle shuttle, Unit target, int facing, int speed) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Shuttles can only be launched during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInPostHetWindow(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch shuttles within 4 impulses of a HET (C6.38)");
        if (launcher.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch shuttles — breakdown lockout for 8 impulses (C6.5472)");
        if (!shuttle.isFullyArmed())
            return ActionResult.fail("Suicide shuttle is not fully armed (needs 3 turns)");
        if (!bay.canLaunch(TurnTracker.getImpulse()))
            return ActionResult.fail("Shuttle bay on cooldown — once every 2 impulses");
        if (!launcher.hasLockOn(target))
            return ActionResult.fail("No lock-on to target — cannot launch suicide shuttle");
        launcher.forceAcquireControl(shuttle);

        // J3.41: launching a seeking weapon voids the launcher's own WW
        if (launcher.hasActiveWildWeasel())
            voidWildWeasel(launcher);

        bay.launch(shuttle, Math.min(speed, shuttle.getMaxSpeed()), facing, TurnTracker.getImpulse());
        shuttle.setName(launcher.getName() + "-Suicide-" + (++seekerSeq));
        shuttle.setLocation(launcher.getLocation());
        // J3.201: redirect to WW if target ship has an active/exploding WW (not
        // post-explosion)
        Unit ssTarget = target;
        if (target instanceof Ship) {
            com.sfb.objects.shuttles.WildWeaselShuttle ww = ((Ship) target).getActiveWildWeasel();
            if (ww != null && !ww.isPostExplosion())
                ssTarget = ww;
        }
        shuttle.setTarget(ssTarget);
        shuttle.setController(launcher);
        shuttle.setLaunchImpulse(TurnTracker.getImpulse());
        seekers.add(shuttle);
        List<String> lockLog = checkLockOnsForNewUnit(launcher, shuttle);

        String msg = launcher.getName() + " launched suicide shuttle at " + target.getName()
                + " (warhead " + shuttle.getWarheadDamage() + ")";
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        checkControlOverflow();
        return ActionResult.ok(msg);
    }

    /**
     * Launch a scatter pack at a target hex.
     * Requires lock-on. Releases its drones after 8 impulses.
     */
    public ActionResult launchScatterPack(Ship launcher, com.sfb.systemgroups.ShuttleBay bay,
            com.sfb.objects.shuttles.ScatterPack pack, Unit target, int facing, int speed) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Shuttles can only be launched during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInPostHetWindow(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch shuttles within 4 impulses of a HET (C6.38)");
        if (launcher.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot launch shuttles — breakdown lockout for 8 impulses (C6.5472)");
        if (pack.getPayload().isEmpty())
            return ActionResult.fail("Scatter pack has no drones loaded");
        if (!bay.canLaunch(TurnTracker.getImpulse()))
            return ActionResult.fail("Shuttle bay on cooldown — once every 2 impulses");
        if (!launcher.hasLockOn(target))
            return ActionResult.fail("No lock-on to target — cannot launch scatter pack");

        launcher.forceAcquireControl(pack);

        bay.launch(pack, Math.min(speed, pack.getMaxSpeed()), facing, TurnTracker.getImpulse());
        pack.setName(launcher.getName() + "-Pack-" + (++seekerSeq));
        pack.setLocation(launcher.getLocation());
        pack.setTarget(target);
        pack.setController(launcher);
        pack.setLaunchImpulse(TurnTracker.getImpulse());
        seekers.add(pack);
        List<String> lockLog = checkLockOnsForNewUnit(launcher, pack);

        String msg = launcher.getName() + " launched scatter pack ("
                + pack.getPayload().size() + " drones) at " + target.getName();
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        checkControlOverflow();
        return ActionResult.ok(msg);
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

    /**
     * Release any non-self-guiding drone whose controlling ship no longer has
     * lock-on to its target (D6.122). Sets target and controller to null so
     * the drone flies straight until endurance expires.
     */
    private List<String> releaseOrphanedDrones() {
        List<String> log = new ArrayList<>();
        List<Seeker> toRemove = new ArrayList<>();
        for (Seeker s : seekers) {
            if (s instanceof Drone) {
                Drone drone = (Drone) s;
                if (drone.isSelfGuiding()) {
                    // Self-guiding drones have their own lock-on — foiled by full cloak
                    if (isTargetFullyCloaked(drone)) {
                        log.add("  Drone lost tracking — " + drone.getTarget().getName() + " is fully cloaked");
                        toRemove.add(drone);
                    }
                } else {
                    // Controller-guided drones: released when controller loses lock-on
                    Unit target = drone.getTarget();
                    Unit controller = drone.getController();
                    if (target == null || !(controller instanceof Ship))
                        continue;
                    Ship controlShip = (Ship) controller;
                    if (!controlShip.hasLockOn(target)) {
                        String xfer = autoTransferSeekerControl(drone, controlShip);
                        if (xfer != null) {
                            log.add(xfer);
                        } else {
                            log.add("  Drone released — " + controlShip.getName()
                                    + " lost lock-on to " + target.getName() + ", no teammate available");
                            controlShip.releaseControl(drone);
                            toRemove.add(drone);
                        }
                    }
                }
            } else if (s instanceof PlasmaTorpedo) {
                // Self-guiding — foiled by full cloak
                if (isTargetFullyCloaked(s)) {
                    log.add("  Plasma torpedo lost tracking — " + s.getTarget().getName() + " is fully cloaked");
                    toRemove.add(s);
                }
            }
        }
        seekers.removeAll(toRemove);
        return log;
    }

    /** Extracts the lowest-numbered direction from an arc bitmask. */
    private boolean isTargetFullyCloaked(Seeker s) {
        Unit target = s.getTarget();
        if (!(target instanceof Ship))
            return false;
        com.sfb.systemgroups.CloakingDevice cloak = ((Ship) target).getCloakingDevice();
        return cloak != null && cloak.breaksLockOn();
    }

    /**
     * After a ship moves voluntarily, check whether any seeker now shares its hex.
     * Handles the case where the target ship moves onto a drone rather than
     * the drone moving onto the ship.
     */
    private List<String> checkSeekerCollisions(Ship ship) {
        return seekerMover.checkSeekerCollisions(ship);
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
        if (t.getTerrainType() == TerrainType.ASTEROID)
            asteroidHexes.add(t.getLocation());
        else if (t.getTerrainType() == TerrainType.PLANET)
            planetHexes.add(t.getLocation());
    }

    public List<Terrain> getTerrain() {
        return terrain;
    }

    public boolean isAsteroidHex(Location loc) {
        return loc != null && asteroidHexes.contains(loc);
    }

    public boolean isPlanetHex(Location loc) {
        return loc != null && planetHexes.contains(loc);
    }



    /**
     * Roll asteroid collision damage and apply to the appropriate shield (P3.2).
     * Shield hit is determined by the direction the ship entered the hex
     * (entryDirection relative to facing → shield 1-6).
     * Returns a log line describing the result.
     */
    private String applyAsteroidCollision(Ship ship) {
        int entryDir = ship.getEntryDirection();
        int relBearing = entryDir == 0 ? 1 : MapUtils.getRelativeBearing(entryDir, ship.getFacing());
        int shieldNum = (relBearing - 1) / 4 + 1;

        int speed = ship.getSpeed();
        int bracket = speed <= 6 ? 0 : speed <= 14 ? 1 : speed <= 25 ? 2 : 3;
        int roll = new DiceRoller().rollOneDie();
        int damage = ASTEROID_DAMAGE[roll - 1][bracket];
        String base = "  " + ship.getName() + " enters asteroid hex"
                + " (speed " + speed + ", die " + roll + ", shield " + shieldNum + ")";
        if (damage == 0)
            return base + " — no damage";
        markShieldDamage(ship, shieldNum, damage);
        return base + " — " + damage + " to shield " + shieldNum;
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
        if (currentPhase != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Transporter actions can only be performed during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(actingShip);
        if (cloakBlock != null)
            return cloakBlock;
        if (actingShip.isInBreakdownLockout(TurnTracker.getImpulse()))
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
        java.util.List<Integer> candidates = getShieldCandidates(targetMarker, actingShip);
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
        SpaceMine mine = SpaceMine.createTBomb(actingShip, TurnTracker.getImpulse(), isReal, range == 1);
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
        if (currentPhase != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Mines can only be dropped during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(actingShip);
        if (cloakBlock != null)
            return cloakBlock;
        if (actingShip.isInPostHetWindow(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot drop mines from bay within 4 impulses of a HET (C6.38)");
        if (actingShip.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot drop mines from bay — breakdown lockout for 8 impulses (C6.5472)");

        if (actingShip.getLocation() == null)
            return ActionResult.fail("Ship has no location");

        // Find an available shuttle bay
        int currentImpulse = TurnTracker.getImpulse();
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
        if (!cloak.activate(TurnTracker.getImpulse()))
            return ActionResult.fail(ship.getName() + " cannot cloak now (already cloaking or cloaked this turn)");
        return ActionResult.ok(ship.getName() + " begins cloaking — fading out");
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
        if (!cloak.deactivate(TurnTracker.getImpulse()))
            return ActionResult.fail(ship.getName() + " cannot decloak now (already inactive or uncloaked this turn)");
        return ActionResult.ok(ship.getName() + " begins decloaking — fading in");
    }


    /**
     * Process all mines each movement phase: attempt to activate inactive mines,
     * then check active mines for detection and detonation.
     */
    private List<String> processMines() {
        List<String> log = new ArrayList<>();
        if (mines.isEmpty())
            return log;

        int currentImpulse = TurnTracker.getImpulse();
        DiceRoller dice = new DiceRoller();

        // All units currently on the map
        List<Unit> allUnits = new ArrayList<>(ships);
        for (Seeker s : seekers) {
            if (s instanceof Unit)
                allUnits.add((Unit) s);
        }

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
                    int shieldNum = getShieldNumber(mine, ship);
                    FireResult result = markShieldDamage(ship, shieldNum, mineDamage);
                    log.add("    " + ship.getName() + " shield #" + shieldNum
                            + " hit for " + mineDamage
                            + (result.getBleed() > 0 ? "  bleed " + result.getBleed() : ""));
                } else {
                    String dmgLog = applyDamageToUnit(mineDamage, unit, 0);
                    log.add("    " + dmgLog);
                }
            }
            detonated.add(mine);
        }

        mines.removeAll(detonated);
        return log;
    }

    // --- Hit & Run raids ---

    /**
     * Build the list of systems on a ship that can be targeted by a Hit &amp; Run
     * raid.
     * Only includes functional/damageable systems.
     */
    public List<SystemTarget> getTargetableSystems(Ship target) {
        List<SystemTarget> systems = new ArrayList<>();

        // Individual weapons
        for (Weapon w : target.getWeapons().fetchAllWeapons()) {
            if (w.isFunctional()) {
                systems.add(new SystemTarget(w));
            }
        }

        // Power
        PowerSystems ps = target.getPowerSystems();
        if (ps.getAvailableLWarp() > 0 || ps.getAvailableRWarp() > 0 || ps.getAvailableCWarp() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.WARP, "Warp Engines"));
        }
        if (ps.getAvailableImpulse() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.IMPULSE, "Impulse Engines"));
        }

        // Special functions
        SpecialFunctions sf = target.getSpecialFunctions();
        if (sf.canDamageSensor()) {
            systems.add(new SystemTarget(SystemTarget.Type.SENSORS, "Sensors"));
        }
        if (sf.canDamageScanner()) {
            systems.add(new SystemTarget(SystemTarget.Type.SCANNERS, "Scanners"));
        }

        // Transporters
        if (target.getTransporters().getAvailableTrans() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.TRANSPORTERS, "Transporters"));
        }

        // Crew
        if (target.getCrew().getAvailableCrewUnits() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.CREW, "Crew"));
        }

        // Cloaking device
        com.sfb.systemgroups.CloakingDevice cloak = target.getCloakingDevice();
        if (cloak != null && cloak.isFunctional()) {
            systems.add(new SystemTarget(SystemTarget.Type.CLOAKING_DEVICE, "Cloaking Device"));
        }

        // DERFACS
        com.sfb.systemgroups.DERFACS derfacsTarget = target.getDerfacs();
        if (derfacsTarget != null && derfacsTarget.isFunctional()) {
            systems.add(new SystemTarget(SystemTarget.Type.DERFACS, "DERFACS"));
        }

        // UIM
        int currentImpulseHR = TurnTracker.getImpulse();
        com.sfb.systemgroups.UIM activeUimHR = target.getActiveUim(currentImpulseHR);
        if (activeUimHR != null) {
            systems.add(new SystemTarget(SystemTarget.Type.UIM, "UIM"));
        }

        // Hull
        HullBoxes h = target.getHullBoxes();
        if (h.getAvailableFhull() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.FHULL, "Forward Hull"));
        }
        if (h.getAvailableAhull() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.AHULL, "Aft Hull"));
        }
        if (h.getAvailableChull() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.CHULL, "Center Hull"));
        }

        return systems;
    }

    /**
     * Shared transporter precondition check used by both boarding and H&R actions.
     *
     * <p>
     * Validates phase, cloak, range, lock-on, boarding party count, transporter
     * availability, and shield passability. Auto-lowers the acting ship's facing
     * shield if needed. Spends transporter energy on success.
     *
     * @return null if all preconditions pass (energy already spent), or a failure
     *         {@link ActionResult} describing the first violated condition.
     */
    private ActionResult checkAndSpendTransporterResources(
            Ship actingShip, Ship target, int numParties) {
        if (currentPhase != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Transporter actions can only be performed during the Activity phase");
        ActionResult cloakBlock = cloakActionBlock(actingShip);
        if (cloakBlock != null)
            return cloakBlock;

        // Range check
        int range = getRange(actingShip, target);
        if (range > 5)
            return ActionResult.fail("Target is out of transporter range (" + range + " hexes, max 5)");

        // Lock-on check (D6.124)
        if (!actingShip.hasLockOn(target))
            return ActionResult.fail("No sensor lock-on to " + target.getName()
                    + " — cannot use transporters (D6.124)");

        // Resource checks
        int availableParties = actingShip.getCrew().getAvailableBoardingParties();
        if (numParties > availableParties)
            return ActionResult.fail("Not enough boarding parties (have " + availableParties
                    + ", need " + numParties + ")");
        int availableTrans = actingShip.getTransporters().getAvailableTrans();
        if (numParties > availableTrans)
            return ActionResult.fail("Not enough transporters (have " + availableTrans
                    + ", need " + numParties + ")");
        int availableUses = actingShip.getTransporters().availableUses();
        if (numParties > availableUses)
            return ActionResult.fail("Not enough transporter energy (have " + availableUses
                    + " use(s), need " + numParties + ")");

        // Shield checks — acting ship's shield facing target must be passable
        int actingShieldNum = getShieldNumber(target, actingShip);
        if (!actingShip.getShields().isTransportable(actingShieldNum)) {
            boolean lowered = actingShip.getShields().lowerShield(actingShieldNum);
            if (!lowered)
                return ActionResult.fail("Cannot lower shield #" + actingShieldNum
                        + " on " + actingShip.getName()
                        + " — must wait 8 impulses since last toggle");
        }

        // Target's shield facing the acting ship must already be passable
        int targetShieldNum = getShieldNumber(actingShip, target);
        if (!target.getShields().isTransportable(targetShieldNum))
            return ActionResult.fail(target.getName() + " shield #" + targetShieldNum
                    + " is active — cannot beam through");

        // Spend transporter energy
        for (int i = 0; i < numParties; i++)
            actingShip.getTransporters().useTransporter();

        return null; // all clear
    }

    /**
     * Transport crew units from one unit to another (G8.32 non-combat rate).
     *
     * <p>
     * Non-combat rate: 2 crew units per transporter use. Destination may be
     * any Unit; shield check is skipped for non-Ship destinations. Lock-on to
     * the destination is required (G8.17).
     *
     * @param source ship sending crew (must have transporters)
     * @param dest   receiving unit (ship, shuttle, or other)
     * @param amount number of crew units to transport (≥ 1)
     */
    public ActionResult transportCrew(Ship source, Unit dest, int amount) {
        if (currentPhase != ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Transporter actions can only be performed during the Activity phase");

        ActionResult cloakBlock = cloakActionBlock(source);
        if (cloakBlock != null)
            return cloakBlock;

        if (amount <= 0)
            return ActionResult.fail("Must transport at least 1 crew unit");

        int range = getRange(source, dest);
        if (range > 5)
            return ActionResult.fail("Destination is out of transporter range (" + range + " hexes, max 5)");

        if (!source.hasLockOn(dest))
            return ActionResult.fail("No sensor lock-on to " + dest.getName() + " — cannot use transporters (G8.17)");

        int available = source.getCrew().getAvailableCrewUnits();
        if (amount > available)
            return ActionResult.fail("Not enough crew units (have " + available + ", need " + amount + ")");

        // Non-combat rate: 2 crew per transporter use (G8.32)
        int usesNeeded = (int) Math.ceil(amount / 2.0);
        int availTrans = source.getTransporters().getAvailableTrans();
        if (usesNeeded > availTrans)
            return ActionResult.fail("Not enough transporters (have " + availTrans + ", need " + usesNeeded + ")");
        int availUses = source.getTransporters().availableUses();
        if (usesNeeded > availUses)
            return ActionResult
                    .fail("Not enough transporter energy (have " + availUses + " use(s), need " + usesNeeded + ")");

        // Source shield facing dest must be passable (auto-lower if possible)
        int srcShieldNum = getShieldNumber(dest, source);
        if (!source.getShields().isTransportable(srcShieldNum)) {
            boolean lowered = source.getShields().lowerShield(srcShieldNum);
            if (!lowered)
                return ActionResult.fail("Cannot lower shield #" + srcShieldNum + " on " + source.getName()
                        + " — must wait 8 impulses since last toggle");
        }

        // Destination shield facing source must already be passable (ships only)
        if (dest instanceof Ship) {
            Ship destShip = (Ship) dest;
            int destShieldNum = getShieldNumber(source, destShip);
            if (!destShip.getShields().isTransportable(destShieldNum))
                return ActionResult.fail(dest.getName() + " shield #" + destShieldNum
                        + " is active — cannot beam through (G8.21)");
        }

        // Spend transporters
        for (int i = 0; i < usesNeeded; i++)
            source.getTransporters().useTransporter();

        // Move crew
        source.getCrew().setAvailableCrewUnits(available - amount);
        if (dest instanceof Ship) {
            Ship destShip = (Ship) dest;
            destShip.getCrew().setAvailableCrewUnits(destShip.getCrew().getAvailableCrewUnits() + amount);
        } else {
            dest.getPersonnel().addCrew(amount);
        }

        StringBuilder log = new StringBuilder();
        log.append("=== Transport Crew: ").append(source.getName())
                .append(" → ").append(dest.getName()).append(" ===\n");
        log.append("  ").append(amount).append(" crew unit(s) transported using ")
                .append(usesNeeded).append(" transporter(s) (non-combat rate G8.32)\n");
        if (dest instanceof Ship && ((Ship) dest).isCaptured() && !((Ship) dest).getCrew().isSkeleton()) {
            log.append("  Skeleton crew established — ").append(dest.getName()).append(" is now operable\n");
        }
        return ActionResult.ok(log.toString());
    }

    /**
     * Transport boarding parties onto an enemy ship (D7.31).
     *
     * <p>
     * Same preconditions as H&amp;R: Activity phase, range ≤ 5, lock-on,
     * enough boarding parties and transporter energy, shields passable.
     * The acting ship's facing shield is auto-lowered if needed.
     *
     * <p>
     * On success the parties are deducted from the acting ship and added to
     * the target's enemy troop count. Combat resolves at end of turn via
     * {@link #performBoardingCombat(Ship)}.
     *
     * @param actingShip the ship sending boarding parties
     * @param target     the ship being boarded
     * @param normal     number of normal boarding parties to transport
     * @param commandos  number of commandos to transport
     */
    public ActionResult performBoardingAction(Ship actingShip, Ship target,
            int normal, int commandos) {
        int numParties = normal + commandos;
        if (numParties <= 0)
            return ActionResult.fail("Must send at least one boarding party");
        if (actingShip.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot use transporters — breakdown lockout for 8 impulses (C6.5474)");

        // Check commandos available separately
        if (commandos > actingShip.getCrew().getFriendlyTroops().commandos)
            return ActionResult.fail("Not enough commandos (have "
                    + actingShip.getCrew().getFriendlyTroops().commandos
                    + ", need " + commandos + ")");

        ActionResult check = checkAndSpendTransporterResources(actingShip, target, numParties);
        if (check != null)
            return check;

        // Deduct from acting ship
        actingShip.getCrew().getFriendlyTroops().commandos -= commandos;
        actingShip.getCrew().getFriendlyTroops().removeCasualties(normal); // removes normal first

        // Place on target; record attacker for ownership transfer if capture occurs
        // (D7.50)
        target.addEnemyBoardingParties(normal);
        target.addEnemyCommandos(commandos);
        if (target.getBoardingAttacker() == null)
            target.setBoardingAttacker(actingShip.getOwner());

        StringBuilder log = new StringBuilder();
        log.append("=== Boarding Action: ").append(actingShip.getName())
                .append("  →  ").append(target.getName()).append(" ===\n");
        log.append("  Transported: ").append(normal).append(" BP(s)");
        if (commandos > 0)
            log.append(" + ").append(commandos).append(" commando(s)");
        log.append("\n");
        log.append("  Enemy troops now aboard ").append(target.getName())
                .append(": ").append(target.getEnemyTroops()).append("\n");
        log.append("  Combat resolves at end of turn (Final Activity Phase).\n");

        return ActionResult.ok(log.toString());
    }

    /**
     * Execute a Hit &amp; Run boarding raid.
     *
     * <p>
     * Pre-conditions checked here: range ≤ 5, enough boarding parties and
     * transporter energy, target shield passable. The acting ship's facing
     * shield is lowered automatically if it has remaining strength (triggering
     * the 8-impulse lockout).
     *
     * @param actingShip    The ship sending boarding parties.
     * @param target        The ship being raided.
     * @param targetSystems One {@link SystemTarget} per boarding party sent.
     * @return ActionResult with a full raid log, or failure message.
     */
    public ActionResult performHitAndRun(Ship actingShip, Ship target,
            List<SystemTarget> targetSystems) {
        ActionResult cloakBlock = cloakActionBlock(actingShip);
        if (cloakBlock != null)
            return cloakBlock;
        if (actingShip.isInBreakdownLockout(TurnTracker.getImpulse()))
            return ActionResult.fail("Cannot use transporters — breakdown lockout for 8 impulses (C6.5474)");
        if (targetSystems.isEmpty())
            return ActionResult.fail("No boarding parties assigned");

        ActionResult check = checkAndSpendTransporterResources(actingShip, target, targetSystems.size());
        if (check != null)
            return check;

        int actingShieldNum = getShieldNumber(target, actingShip);

        // Defending crew quality modifier (D7.73)
        int crewMod = 0;
        com.sfb.systemgroups.Crew.CrewQuality cq = target.getCrew().getCrewQuality();
        if (cq == com.sfb.systemgroups.Crew.CrewQuality.OUTSTANDING)
            crewMod = +1;
        else if (cq == com.sfb.systemgroups.Crew.CrewQuality.POOR)
            crewMod = -1;

        // Roll and apply results
        DiceRoller dice = new DiceRoller();
        StringBuilder log = new StringBuilder();
        log.append("=== Hit & Run Raid: ").append(actingShip.getName())
                .append("  →  ").append(target.getName()).append(" ===\n");
        log.append("  ").append(actingShip.getName()).append(" shield #")
                .append(actingShieldNum).append(" lowered\n");

        int partiesLost = 0;
        for (SystemTarget st : targetSystems) {
            com.sfb.properties.BoardingPartyQuality quality = st.getAttackerQuality();
            int roll = Math.min(6, Math.max(1, dice.rollOneDie() + crewMod));

            // Guard check (D7.831) — if a guard is assigned, resolve guard table first
            if (target.isGuarded(st.getType())) {
                com.sfb.properties.BoardingPartyQuality guardQuality = target.getGuardQuality(st.getType());
                HarGuardResult guardResult = resolveGuardTable(roll, guardQuality);
                log.append("  Guard present (").append(guardQuality).append(")  roll ").append(roll)
                        .append(" → ").append(guardResult).append("\n");
                if (guardResult == HarGuardResult.BP_DESTROYED) {
                    partiesLost++;
                    log.append("    Boarding party destroyed by guard\n");
                    continue;
                } else if (guardResult == HarGuardResult.BP_RETURNS) {
                    log.append("    Boarding party repelled — returns safely\n");
                    continue;
                }
                // CONDUCT_HR: fall through to normal H&R roll with a fresh die
                roll = Math.min(6, Math.max(1, dice.rollOneDie() + crewMod));
                log.append("    Guard repelled — conducting H&R roll ").append(roll).append("\n");
            }

            // Normal H&R resolution (D7.81)
            HarResult result = resolveHarTable(roll, quality);
            boolean systemHit = (result == HarResult.SYSTEM_BP_RETURNS || result == HarResult.BOTH_DESTROYED);
            boolean partyLost = (result == HarResult.BOTH_DESTROYED || result == HarResult.BP_DESTROYED);

            String hitResult;
            if (systemHit) {
                boolean damaged = applyHitAndRunHit(target, st);
                hitResult = damaged ? st.getDisplayName() + " DAMAGED"
                        : st.getDisplayName() + " already destroyed";
            } else {
                hitResult = st.getDisplayName() + " not damaged";
            }

            log.append("  Roll ").append(roll).append(" [").append(quality).append("]: ")
                    .append(hitResult)
                    .append(",  boarding party ").append(partyLost ? "lost" : "safe").append("\n");
            if (partyLost)
                partiesLost++;
        }

        if (partiesLost > 0) {
            int remaining = actingShip.getCrew().getAvailableBoardingParties() - partiesLost;
            actingShip.getCrew().setAvailableBoardingParties(Math.max(0, remaining));
        }

        log.append("  Boarding parties lost: ").append(partiesLost)
                .append(" / ").append(targetSystems.size()).append(" sent");

        return ActionResult.ok(log.toString());
    }

    /**
     * Apply a single Hit &amp; Run hit to the given system on the target ship.
     * Returns true if the system was actually damaged (false if already destroyed).
     */
    private boolean applyHitAndRunHit(Ship target, SystemTarget system) {
        switch (system.getType()) {
            case WEAPON: {
                Weapon w = system.getWeapon();
                if (!w.isFunctional())
                    return false;
                w.damage();
                return true;
            }
            case WARP: {
                PowerSystems ps = target.getPowerSystems();
                if (ps.damageLWarp())
                    return true;
                if (ps.damageRWarp())
                    return true;
                return ps.damageCWarp();
            }
            case IMPULSE:
                return target.getPowerSystems().damageImpulse();
            case SENSORS:
                // Overflow (controlUsed > new limit) is resolved via CONTROL_OVERFLOW interrupt
                return target.getSpecialFunctions().damageSensor();
            case SCANNERS:
                return target.getSpecialFunctions().damageScanner();
            case TRANSPORTERS:
                return target.getTransporters().damage();
            case CREW: {
                int current = target.getCrew().getAvailableCrewUnits();
                if (current <= 0)
                    return false;
                target.getCrew().setAvailableCrewUnits(current - 1);
                return true;
            }
            case FHULL:
                return target.getHullBoxes().damageFhull();
            case AHULL:
                return target.getHullBoxes().damageAhull();
            case CHULL:
                return target.getHullBoxes().damageChull();
            case CLOAKING_DEVICE: {
                com.sfb.systemgroups.CloakingDevice cloak = target.getCloakingDevice();
                if (cloak == null || !cloak.isFunctional())
                    return false;
                cloak.damage(TurnTracker.getImpulse());
                return true;
            }
            case DERFACS: {
                com.sfb.systemgroups.DERFACS d = target.getDerfacs();
                if (d == null || !d.isFunctional())
                    return false;
                d.damage();
                return true;
            }
            case UIM: {
                com.sfb.systemgroups.UIM uimHit = target.getActiveUim(TurnTracker.getImpulse());
                if (uimHit == null)
                    return false;
                uimHit.damage();
                return true;
            }
            default:
                return false;
        }
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

    /**
     * Resolve one round of boarding party combat on {@code defender} (D7.4).
     *
     * <p>
     * Called during the Final Activity Phase (D7.32) for each ship that has
     * enemy boarding parties on board.
     *
     * <p>
     * Step 3 (specific allocation) is skipped — casualty points go straight
     * to Step 4. The {@link BoardingCombatResult} carries all intermediate
     * values so Step 3 can be inserted later.
     *
     * @param defender the ship being boarded
     */
    public BoardingCombatResult performBoardingCombat(Ship defender) {
        com.sfb.objects.TroopCount attackers = defender.getEnemyTroops();
        com.sfb.objects.TroopCount defenders = defender.getCrew().getFriendlyTroops();

        StringBuilder log = new StringBuilder();
        log.append("=== Boarding Combat: ").append(defender.getName()).append(" ===\n");
        log.append("  Attackers: ").append(attackers).append("\n");
        log.append("  Defenders: ").append(defenders).append("\n");

        // D7.422: Klingon security station die roll modifier for defender
        int securityMod = klingonSecurityMod(defender);
        if (securityMod > 0)
            log.append("  Security station modifier: +").append(securityMod).append("\n");

        // Step 1 — combat power (D7.41)
        int attackerPower = attackers.total();
        int defenderPower = defenders.total();

        // Step 2 — roll casualty points (D7.42); groups of up to 10
        int attackerPts = rollCasualtyPoints(attackerPower, 0, log, "Attacker");
        int defenderPts = rollCasualtyPoints(defenderPower, securityMod, log, "Defender");

        // Step 3 — specific allocation: DEFERRED (future hook)
        // int attackerPtsAfterStep3 = attackerPts; // will be reduced by captured rooms
        // int defenderPtsAfterStep3 = defenderPts;

        // Step 4 — apply casualties (D7.44)
        // Attacker casualty points kill defender BPs; then capture control rooms if BPs
        // exhausted.
        int defenderBPsLost = 0;
        int controlRoomsCaptured = 0;

        // 4a: kill defender BPs
        int defBPsToRemove = Math.min(attackerPts, defenderPower);
        defenderBPsLost = defenders.removeCasualties(defBPsToRemove);
        int remainingAttackerPts = attackerPts - defenderBPsLost;
        log.append("  Defender loses ").append(defenderBPsLost).append(" BP(s). Remaining: ")
                .append(defenders).append("\n");

        // 4b: excess points capture control rooms (simplified D7.361 fallback)
        if (remainingAttackerPts > 0) {
            com.sfb.systemgroups.ControlSpaces cs = defender.getControlSpaces();
            for (com.sfb.systemgroups.ControlSpaces.RoomType room : com.sfb.systemgroups.ControlSpaces.RoomType
                    .values()) {
                while (remainingAttackerPts >= com.sfb.systemgroups.ControlSpaces.captureCost(room)
                        && cs.captureRoom(room)) {
                    remainingAttackerPts -= com.sfb.systemgroups.ControlSpaces.captureCost(room);
                    controlRoomsCaptured++;
                    log.append("  Control room captured: ").append(room).append("\n");
                }
            }
        }

        // Defender casualty points kill attacker BPs
        int attackerBPsLost = Math.min(defenderPts, attackerPower);
        attackers.removeCasualties(attackerBPsLost);
        log.append("  Attacker loses ").append(attackerBPsLost).append(" BP(s). Remaining: ")
                .append(attackers).append("\n");

        // Capture check (D7.50)
        boolean shipCaptured = defender.getControlSpaces().allControlRoomsCaptured();
        if (shipCaptured) {
            defender.setCaptured(true);
            capturedThisTurn.add(defender);
            applyD753CaptureEffects(defender, log);
        } else if (attackers.isEmpty()) {
            // All attackers killed — no longer boarding, clear attacker record
            defender.setBoardingAttacker(null);
        }

        return new BoardingCombatResult(attackerPts, defenderPts,
                defenderBPsLost, attackerBPsLost, controlRoomsCaptured, shipCaptured,
                log.toString());
    }

    /**
     * D7.53: Immediate effects applied the moment a ship is captured.
     */
    private void applyD753CaptureEffects(Ship defender, StringBuilder log) {
        log.append("  *** ").append(defender.getName()).append(" CAPTURED ***\n");

        // D7.50: transfer ownership to the capturing player
        Player captor = defender.getBoardingAttacker();
        Player originalOwner = defender.getOwner();
        if (captor != null && captor != originalOwner) {
            if (originalOwner != null)
                originalOwner.getPlayerUnits().remove(defender);
            captor.getPlayerUnits().add(defender);
            defender.setOwner(captor);
            log.append("  D7.50: ").append(defender.getName())
                    .append(" ownership transferred to ").append(captor.getName()).append("\n");
        }

        // D7.512: original crew become prisoners; ship frozen until skeleton crew
        // arrives
        int prisonerCount = defender.getCrew().getAvailableCrewUnits();
        if (prisonerCount > 0) {
            defender.getCrew().addCapturedCrew(prisonerCount);
            defender.getCrew().setAvailableCrewUnits(0);
            log.append("  D7.512: ").append(prisonerCount).append(" crew unit(s) taken prisoner\n");
        }

        // D7.532: stop ECM/EW
        defender.setEcmAllocated(0);
        defender.setEccmAllocated(0);

        // D7.531: release/orphan all seeking weapons controlled by this ship
        int seekersReleased = 0;
        for (Seeker s : seekers) {
            if (defender.equals(s.getController())) {
                s.setController(null);
                seekersReleased++;
            }
        }
        if (seekersReleased > 0)
            log.append("  D7.531: ").append(seekersReleased).append(" seeking weapon(s) released\n");

        // D7.537: cancel all in-progress heavy weapon arming
        int weaponsReset = 0;
        for (com.sfb.weapons.Weapon w : defender.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.HeavyWeapon) {
                com.sfb.weapons.HeavyWeapon hw = (com.sfb.weapons.HeavyWeapon) w;
                if (hw.isArmed() || hw.getArmingTurn() > 0) {
                    hw.reset();
                    weaponsReset++;
                }
            }
        }
        if (weaponsReset > 0)
            log.append("  D7.537: ").append(weaponsReset).append(" weapon(s) disarmed\n");
    }

    /**
     * Ships captured during the most recent endTurn() call. Cleared at the start of
     * each endTurn().
     */
    public List<Ship> getCapturedThisTurn() {
        return Collections.unmodifiableList(capturedThisTurn);
    }

    /**
     * Roll casualty points for one side using groups of up to 10 (D7.42).
     * The {@code dieMod} is added to each group's roll (clamped 1–6).
     */
    private int rollCasualtyPoints(int totalBPs, int dieMod, StringBuilder log, String side) {
        if (totalBPs == 0) {
            log.append("  ").append(side).append(": 0 BPs — no roll\n");
            return 0;
        }
        DiceRoller dice = new DiceRoller();
        int totalPoints = 0;
        int remaining = totalBPs;
        int groupNum = 1;
        while (remaining > 0) {
            int groupSize = Math.min(remaining, 10);
            int roll = Math.min(6, Math.max(1, dice.rollOneDie() + dieMod));
            int pts = D7421_TABLE[roll - 1][groupSize - 1];
            totalPoints += pts;
            log.append("  ").append(side).append(" group ").append(groupNum)
                    .append(" (").append(groupSize).append(" BPs): roll ").append(roll)
                    .append(" → ").append(pts).append(" pt(s)\n");
            remaining -= groupSize;
            groupNum++;
        }
        log.append("  ").append(side).append(" total casualty pts: ").append(totalPoints).append("\n");
        return totalPoints;
    }

    /**
     * D7.421 Marine Casualty Resolution Table.
     * Index: [dieRoll-1][groupSize-1], values are casualty points.
     *
     * <pre>
     * Roll  1  2  3  4  5  6  7  8  9 10
     *   1   0  0  0  0  1  1  1  1  2  2
     *   2   0  0  1  1  1  2  2  2  2  2
     *   3   0  1  1  1  2  2  2  2  3  3
     *   4   0  1  1  2  2  2  3  3  3  4
     *   5   1  1  2  2  3  3  4  4  5  5
     *   6   1  1  2  2  3  4  4  5  5  6
     * </pre>
     */
    static final int[][] D7421_TABLE = {
            { 0, 0, 0, 0, 1, 1, 1, 1, 2, 2 }, // roll 1
            { 0, 0, 1, 1, 1, 2, 2, 2, 2, 2 }, // roll 2
            { 0, 1, 1, 1, 2, 2, 2, 2, 3, 3 }, // roll 3
            { 0, 1, 1, 2, 2, 2, 3, 3, 3, 4 }, // roll 4
            { 1, 1, 2, 2, 3, 3, 4, 4, 5, 5 }, // roll 5
            { 1, 1, 2, 2, 3, 4, 4, 5, 5, 6 }, // roll 6
    };

    /**
     * Klingon security station die-roll modifier for defender (D7.422).
     * +1 per undestroyed, uncaptured security station box, max +2.
     */
    int klingonSecurityMod(Ship ship) {
        if (ship.getFaction() != com.sfb.properties.Faction.Klingon)
            return 0;
        int stations = ship.getControlSpaces().getAvailableSecurity()
                - ship.getControlSpaces().getCapturedSecurity();
        return Math.min(2, Math.max(0, stations));
    }

    // -------------------------------------------------------------------------
    // H&R table resolution helpers
    // -------------------------------------------------------------------------

    enum HarResult {
        SYSTEM_BP_RETURNS, // System destroyed, BP returns safely
        BOTH_DESTROYED, // Both system and BP destroyed
        BP_DESTROYED, // BP destroyed, system ok
        BP_RETURNS // BP returns safely, system ok
    }

    enum HarGuardResult {
        BP_DESTROYED, // Attacking BP destroyed by guard
        BP_RETURNS, // Attacking BP repelled, returns safely
        CONDUCT_HR // Guard fails to stop raid — proceed to normal H&R roll
    }

    /**
     * Resolve the D7.81 Hit-and-Run table for the given die roll and attacker
     * quality.
     * Roll is already clamped 1-6 with crew quality modifier applied.
     */
    HarResult resolveHarTable(int roll, com.sfb.properties.BoardingPartyQuality quality) {
        switch (quality) {
            case OUTSTANDING:
                if (roll <= 2)
                    return HarResult.SYSTEM_BP_RETURNS;
                if (roll == 3)
                    return HarResult.BOTH_DESTROYED;
                if (roll == 4)
                    return HarResult.BP_DESTROYED;
                return HarResult.BP_RETURNS;
            case COMMANDO:
                if (roll == 1)
                    return HarResult.SYSTEM_BP_RETURNS;
                if (roll <= 3)
                    return HarResult.BOTH_DESTROYED;
                if (roll == 4)
                    return HarResult.BP_DESTROYED;
                return HarResult.BP_RETURNS;
            case POOR:
                // Roll 1 on POOR column is a blank (treated as BOTH_DESTROYED — best POOR can
                // do)
                if (roll == 1)
                    return HarResult.BOTH_DESTROYED;
                if (roll <= 4)
                    return HarResult.BP_DESTROYED;
                return HarResult.BP_RETURNS;
            default: // NORMAL
                if (roll == 1)
                    return HarResult.SYSTEM_BP_RETURNS;
                if (roll == 2)
                    return HarResult.BOTH_DESTROYED;
                if (roll <= 5)
                    return HarResult.BP_DESTROYED;
                return HarResult.BP_RETURNS;
        }
    }

    /**
     * Resolve the D7.831 guard table for the given die roll and guard quality.
     */
    HarGuardResult resolveGuardTable(int roll, com.sfb.properties.BoardingPartyQuality guardQuality) {
        switch (guardQuality) {
            case OUTSTANDING:
                if (roll <= 2)
                    return HarGuardResult.BP_DESTROYED;
                if (roll <= 4)
                    return HarGuardResult.BP_RETURNS;
                return HarGuardResult.CONDUCT_HR;
            case COMMANDO:
                if (roll <= 2)
                    return HarGuardResult.BP_DESTROYED;
                if (roll == 3)
                    return HarGuardResult.BP_RETURNS;
                return HarGuardResult.CONDUCT_HR;
            case POOR:
                if (roll <= 4)
                    return HarGuardResult.BP_DESTROYED;
                if (roll == 5)
                    return HarGuardResult.BP_RETURNS;
                return HarGuardResult.CONDUCT_HR;
            default: // NORMAL
                if (roll <= 3)
                    return HarGuardResult.BP_DESTROYED;
                if (roll <= 5)
                    return HarGuardResult.BP_RETURNS;
                return HarGuardResult.CONDUCT_HR;
        }
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
     * destroyed. Queued by resolveInternalDamage(); cleared when the player
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
    private static class PendingDamage {
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
