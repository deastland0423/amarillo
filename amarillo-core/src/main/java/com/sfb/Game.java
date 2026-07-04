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
import com.sfb.scenario.CoiLoadout;
import com.sfb.scenario.ScenarioLoader;
import com.sfb.scenario.ScenarioSpec;

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
import com.sfb.systemgroups.Energy;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
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
    private final DamageResolver damageResolver = new DamageResolver(this, seekers, activeShuttles,
            pendingVolleys, pendingInternalDamage, pendingDacChoices, firedPairsThisPhase, uimUsedThisImpulse);
    private final BoardingResolver boardingResolver = new BoardingResolver(this, seekers, capturedThisTurn);
    private final LaunchCoordinator launchCoordinator = new LaunchCoordinator(this, seekers, activeShuttles);
    private final MineResolver mineResolver = new MineResolver(this, mines, ships, seekers, prevLocations);
    private final SeekerControl seekerControl = new SeekerControl(this, ships, seekers);

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
        List<String> orphanLog = seekerControl.releaseOrphanedDrones();
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
    List<String> checkLockOnsForNewUnit(Ship launcher, Unit newUnit) {
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
                List<String> mineLog = mineResolver.processMines();
                lastSeekerLog.addAll(mineLog);
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
                        log.addAll(seekerControl.releaseOrphanedDrones());

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

    /** Voluntarily transfer control of a seeker to an allied ship (FD1.7). */
    public ActionResult transferSeekerControl(String seekerName, String toShipName) {
        return seekerControl.transferSeekerControl(seekerName, toShipName);
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
     * until damageResolver.resolveInternalDamage() is called at the end of the Direct-Fire segment
     * (6D4).
     *
     * @return A FireResult with the bleed-through amount and an empty internal log
     *         (log is populated later when damageResolver.resolveInternalDamage() runs).
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

        // damageResolver.resolveInternalDamage() leaves currentPhase as DAC_CHOICE when it returns
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



    // --- Hit & Run raids ---

    /** All H&R-targetable systems on the given ship (D7.8). */
    public List<SystemTarget> getTargetableSystems(Ship target) {
        return boardingResolver.getTargetableSystems(target);
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
     * destroyed. Queued by damageResolver.resolveInternalDamage(); cleared when the player
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
