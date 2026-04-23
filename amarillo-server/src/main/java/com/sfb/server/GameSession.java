package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.Player;
import com.sfb.commands.AdvancePhaseCommand;
import com.sfb.commands.CloakCommand;
import com.sfb.commands.FireCommand;
import com.sfb.commands.LaunchDroneCommand;
import com.sfb.weapons.DroneRack;
import com.sfb.commands.LaunchPlasmaCommand;
import com.sfb.commands.MoveCommand;
import com.sfb.commands.ShuttleMoveCommand;
import com.sfb.commands.UncloakCommand;
import com.sfb.objects.Drone;
import com.sfb.objects.Shuttle;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.constants.Constants;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.properties.WeaponArmingType;
import com.sfb.systems.Energy;
import com.sfb.weapons.HeavyWeapon;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A single game instance on the server.
 * Holds the authoritative Game object plus player registry.
 */
public class GameSession {

    public static class PlayerInfo {
        private final String token;
        private final String name;
        private Player corePlayer = null; // set during assignShip

        public PlayerInfo(String token, String name) {
            this.token = token;
            this.name  = name;
        }

        public String getToken()          { return token; }
        public String getName()           { return name; }
        public Player getCorePlayer()     { return corePlayer; }
        void setCorePlayer(Player p)      { this.corePlayer = p; }

        /** Convenience: ships this player owns, sourced from the core Player object. */
        public List<String> getShipNames() {
            if (corePlayer == null) return List.of();
            return corePlayer.getPlayerUnits().stream()
                    .map(u -> u instanceof Ship ? ((Ship) u).getName() : null)
                    .filter(n -> n != null)
                    .toList();
        }
    }

    private final String id;
    private final Game   game;
    private final String hostToken;

    /** token → PlayerInfo */
    private final Map<String, PlayerInfo> players = new LinkedHashMap<>();

    /** Tokens of players who have clicked "Ready" for the current phase. */
    private final Set<String> readyPlayers = new HashSet<>();

    /** Combat events accumulated since the last broadcast; drained by drainCombatLog(). */
    private final List<String> pendingCombatLog = new ArrayList<>();

    /**
     * COI selections submitted by each player, keyed by player token then ship name.
     * Collected during the pre-game lobby; applied when start() is called.
     */
    private final Map<String, Map<String, com.sfb.scenario.CoiLoadout>> pendingCoi = new LinkedHashMap<>();

    /** Tokens of players who have submitted (or skipped) their COI. */
    private final Set<String> coiDoneTokens = new HashSet<>();

    /** shipName → playerToken, recorded before start() so ships can be assigned in the lobby. */
    private final Map<String, String> pendingAssignments = new LinkedHashMap<>();

    // Scenario loaded but not yet started
    private boolean                              scenarioLoaded   = false;
    private String                               loadedScenarioId = null;
    private com.sfb.scenario.ScenarioSpec        loadedSpec       = null;
    private List<List<com.sfb.objects.Ship>>     loadedSideShips  = null;

    private boolean started = false;

    public GameSession(String id, String hostToken, String hostName) {
        this.id        = id;
        this.game      = new Game();
        this.hostToken = hostToken;
        players.put(hostToken, new PlayerInfo(hostToken, hostName));
    }

    // -------------------------------------------------------------------------
    // Player management
    // -------------------------------------------------------------------------

    public PlayerInfo addPlayer(String token, String name) {
        PlayerInfo info = new PlayerInfo(token, name);
        players.put(token, info);
        return info;
    }

    public boolean hasPlayer(String token) {
        return players.containsKey(token);
    }

    public boolean isHost(String token) {
        return hostToken.equals(token);
    }

    // -------------------------------------------------------------------------
    // Ready-state tracking
    // -------------------------------------------------------------------------

    public int getReadyCount()   { return readyPlayers.size(); }
    public int getPlayerCount()  { return players.size(); }

    /** Append a combat event to the pending log (broadcast on next state push). */
    public void appendCombatLog(String entry) { pendingCombatLog.add(entry); }

    /** Return all pending combat log entries and clear the list. */
    public List<String> drainCombatLog() {
        List<String> copy = new ArrayList<>(pendingCombatLog);
        pendingCombatLog.clear();
        return copy;
    }
    public boolean allReady()    { return readyPlayers.size() >= players.size(); }

    /** Clear ready flags — called automatically when the phase actually advances. */
    private void clearReady()    { readyPlayers.clear(); }

    // -------------------------------------------------------------------------
    // Ship assignment
    // -------------------------------------------------------------------------

    /**
     * Records a ship assignment in the pre-game lobby.
     * Works before start() — ships are in pendingAssignments until start() resolves them.
     * Returns an error string on failure, null on success.
     */
    public String assignShip(String playerToken, String shipName) {
        if (!scenarioLoaded)
            return "Load a scenario before assigning ships";

        if (!players.containsKey(playerToken))
            return "Player token not found";

        boolean exists = getAllShipNames().contains(shipName);
        if (!exists)
            return "Ship not found: " + shipName;

        String existing = pendingAssignments.get(shipName);
        if (existing != null && !existing.equals(playerToken))
            return "Ship already assigned to another player";

        pendingAssignments.put(shipName, playerToken);
        return null;
    }

    /** All ship names across all scenario sides, in order. */
    public List<String> getAllShipNames() {
        if (loadedSideShips == null) return List.of();
        return loadedSideShips.stream()
                .flatMap(List::stream)
                .map(Ship::getName)
                .toList();
    }

    /** Ships not yet assigned to any player. */
    public List<String> getUnassignedShipNames() {
        if (!scenarioLoaded) return List.of();
        // Post-start: read from live game objects
        if (started) {
            return game.getShips().stream()
                    .filter(s -> s.getOwner() == null)
                    .map(Ship::getName)
                    .toList();
        }
        // Pre-start: read from pending map
        return getAllShipNames().stream()
                .filter(name -> !pendingAssignments.containsKey(name))
                .toList();
    }

    /** Ships assigned to a specific player token (pre- or post-start). */
    public List<String> getAssignedShipsFor(String playerToken) {
        if (started) {
            PlayerInfo info = players.get(playerToken);
            return info != null ? info.getShipNames() : List.of();
        }
        return pendingAssignments.entrySet().stream()
                .filter(e -> e.getValue().equals(playerToken))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Returns true if the token owns the named ship or shuttle.
     * If no ships have been assigned to anyone yet, all players have open access
     * (dev/solo mode).
     */
    public boolean ownsShip(String token, String shipName) {
        boolean anyAssigned = game.getShips().stream()
                .anyMatch(s -> s.getOwner() != null);
        if (!anyAssigned)
            return true;

        PlayerInfo p = players.get(token);
        if (p == null || p.getCorePlayer() == null) return false;

        Ship ship = findShip(shipName);
        if (ship != null)
            return ship.getOwner() == p.getCorePlayer();

        // Also accept active shuttle names
        com.sfb.objects.Shuttle shuttle = game.getActiveShuttles().stream()
                .filter(s -> s.getName().equalsIgnoreCase(shipName))
                .findFirst().orElse(null);
        if (shuttle != null)
            return shuttle.getOwner() == p.getCorePlayer();

        return false;
    }

    // -------------------------------------------------------------------------
    // Game lifecycle
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // COI tracking
    // -------------------------------------------------------------------------

    /**
     * Submit COI selections and mark this player as COI-done.
     * May be called with an empty map to skip COI.
     */
    public void submitCoi(String playerToken, Map<String, com.sfb.scenario.CoiLoadout> shipLoadouts) {
        pendingCoi.put(playerToken, new LinkedHashMap<>(shipLoadouts));
        coiDoneTokens.add(playerToken);
    }

    public boolean isCoiDone(String token)  { return coiDoneTokens.contains(token); }
    public boolean allCoiDone()             { return coiDoneTokens.containsAll(players.keySet()); }

    // -------------------------------------------------------------------------
    // Game lifecycle
    // -------------------------------------------------------------------------

    /**
     * Load a scenario into the lobby without starting the game clock.
     * Ships become available for assignment immediately after this call.
     */
    public void loadScenario(String scenarioId) throws java.io.IOException {
        com.sfb.objects.ShipLibrary.loadAllSpecs("data/factions");
        loadedSpec       = com.sfb.scenario.ScenarioSpec.fromJson(
                "data/scenarios/" + scenarioId.toLowerCase() + ".json");
        loadedSideShips  = com.sfb.scenario.ScenarioLoader.loadShips(loadedSpec);
        loadedScenarioId = scenarioId;
        scenarioLoaded   = true;
        // Reset any prior assignments/COI when a new scenario is loaded
        pendingAssignments.clear();
        coiDoneTokens.clear();
        pendingCoi.clear();
    }

    /**
     * Start the game clock. Scenario must already be loaded and all players
     * must have submitted (or skipped) their COI.
     */
    public void start() throws java.io.IOException {
        if (!scenarioLoaded)
            throw new IllegalStateException("Load a scenario before starting");

        // Flatten ship name → CoiLoadout from all players' submissions
        Map<String, com.sfb.scenario.CoiLoadout> byName = new LinkedHashMap<>();
        for (Map<String, com.sfb.scenario.CoiLoadout> playerMap : pendingCoi.values()) {
            byName.putAll(playerMap);
        }

        // Build Ship → CoiLoadout map for setupFromScenario
        Map<com.sfb.objects.Ship, com.sfb.scenario.CoiLoadout> coiMap = new LinkedHashMap<>();
        for (List<com.sfb.objects.Ship> side : loadedSideShips) {
            for (com.sfb.objects.Ship ship : side) {
                com.sfb.scenario.CoiLoadout loadout = byName.get(ship.getName());
                if (loadout != null) coiMap.put(ship, loadout);
            }
        }

        game.setupFromScenario(loadedSpec, loadedSideShips, coiMap.isEmpty() ? null : coiMap);

        // Resolve pending assignments to live Ship objects
        for (Map.Entry<String, String> entry : pendingAssignments.entrySet()) {
            String shipName   = entry.getKey();
            String pToken     = entry.getValue();
            PlayerInfo info   = players.get(pToken);
            if (info == null) continue;
            Ship ship = findShip(shipName);
            if (ship == null) continue;
            if (info.getCorePlayer() == null) {
                Player p = new Player();
                p.setName(info.getName());
                game.getPlayers().add(p);
                info.setCorePlayer(p);
            }
            ship.setOwner(info.getCorePlayer());
            info.getCorePlayer().getPlayerUnits().add(ship);
        }

        started = true;
    }

    public boolean isScenarioLoaded()   { return scenarioLoaded; }
    public String  getLoadedScenarioId() { return loadedScenarioId; }

    // -------------------------------------------------------------------------
    // Action dispatch
    // -------------------------------------------------------------------------

    /**
     * Execute an action on behalf of a player.
     * Ownership is validated before this is called by the controller.
     */
    public ActionResult executeAction(ActionRequest request) {
        switch (request.getType().toUpperCase()) {

            case "ADVANCE_PHASE": {
                String token = request.getPlayerToken();
                // During movement phase, reject ready if this player still has ships or shuttles to move
                if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT) {
                    PlayerInfo pi = players.get(token);
                    if (pi != null) {
                        List<String> myNames = pi.getShipNames();
                        // Check ships first
                        String pending = game.getMovableShips().stream()
                                .map(s -> s.getName())
                                .filter(myNames::contains)
                                .findFirst()
                                .orElse(null);
                        // Then check player-controlled shuttles (owned by this player's ships)
                        if (pending == null) {
                            pending = game.getMovableShuttles().stream()
                                    .filter(s -> myNames.contains(s.getParentShipName()))
                                    .map(s -> s.getName())
                                    .findFirst()
                                    .orElse(null);
                        }
                        if (pending != null) {
                            return ActionResult.fail("MUST_MOVE:" + pending);
                        }
                    }
                }
                readyPlayers.add(token);
                int ready = readyPlayers.size();
                int total = players.size();
                if (!allReady()) {
                    return ActionResult.ok("WAITING:" + ready + "/" + total);
                }
                clearReady();
                ActionResult phaseResult = game.execute(new AdvancePhaseCommand());
                transferCapturedShipOwnership();
                return phaseResult;
            }

            case "UNREADY": {
                String token = request.getPlayerToken();
                readyPlayers.remove(token);
                return ActionResult.ok("UNREADY:" + readyPlayers.size() + "/" + players.size());
            }

            case "MOVE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                MoveCommand.Action action;
                try {
                    action = MoveCommand.Action.valueOf(request.getAction().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ActionResult.fail("Unknown move action: " + request.getAction());
                }
                return game.execute(new MoveCommand(ship, action));
            }

            case "PERFORM_HET": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.performHet(ship, request.getFacing());
            }

            case "ALLOCATE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                if (!game.isAwaitingAllocation())
                    return ActionResult.fail("Not currently in allocation phase");

                Energy e = new Energy();

                // Life support and fire control — always full cost
                e.setLifeSupport(ship.getLifeSupportCost());
                e.setFireControl(ship.getFireControlCost());

                // Shields
                String shieldMode = request.getShieldMode();
                if ("MINIMUM".equalsIgnoreCase(shieldMode))
                    e.setActivateShields(ship.getMinimumShieldCost());
                else if ("OFF".equalsIgnoreCase(shieldMode))
                    e.setActivateShields(0);
                else
                    e.setActivateShields(ship.getActiveShieldCost());

                // Movement — convert requested speed to warp energy
                double moveCost = ship.getPerformanceData().getMovementCost();
                int requestedSpeed = request.getSpeed();
                int warpSpeed = Math.min(requestedSpeed, 30);
                double warpEngineCapacity = ship.getPowerSysetems().getAvailableLWarp()
                        + ship.getPowerSysetems().getAvailableRWarp()
                        + ship.getPowerSysetems().getAvailableCWarp();
                double movementEnergyNeeded = warpSpeed * moveCost;
                if (movementEnergyNeeded > warpEngineCapacity + 0.001) {
                    return ActionResult.fail("Insufficient warp engine power for speed " + requestedSpeed
                            + " — need " + movementEnergyNeeded + ", have " + warpEngineCapacity);
                }
                e.setWarpMovement(movementEnergyNeeded);
                e.setImpulseMovement(requestedSpeed > 30 ? 1 : 0);

                // Phaser capacitor
                if (request.isEnergizeCaps() && !ship.isCapacitorsCharged()) {
                    e.setEnergizeCaps(true);
                } else if (request.isTopOffCap() && ship.isCapacitorsCharged()) {
                    double capNeeded = ship.getWeapons().getAvailablePhaserCapacitor()
                            - ship.getWeapons().getPhaserCapacitorEnergy();
                    e.setPhaserCapacitor(Math.max(0, capNeeded));
                }

                // Heavy weapon arming
                Map<String, String> arming = request.getWeaponArming();
                for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (!(w instanceof HeavyWeapon)) continue;
                    String choice = arming != null ? arming.get(w.getName()) : null;
                    if (choice == null) choice = "STANDARD";
                    HeavyWeapon hw = (HeavyWeapon) w;
                    switch (choice.toUpperCase()) {
                        case "HOLD":
                            e.getArmingEnergy().put(w, (double) hw.holdEnergyCost());
                            e.getArmingType().put(w, WeaponArmingType.STANDARD);
                            break;
                        case "OVERLOAD":
                            e.getArmingEnergy().put(w, (double) hw.energyToArm() * 2);
                            e.getArmingType().put(w, WeaponArmingType.OVERLOAD);
                            break;
                        case "SUICIDE":
                            e.getArmingEnergy().put(w, 7.0);
                            e.getArmingType().put(w, WeaponArmingType.SPECIAL);
                            break;
                        case "UPGRADE_OVL":
                            // Armed Fusion standard → hold(1) + arm(2) = 3 total
                            e.getArmingEnergy().put(w, 3.0);
                            e.getArmingType().put(w, WeaponArmingType.OVERLOAD);
                            break;
                        case "UPGRADE_SUICIDE":
                            // Armed Fusion standard → hold(1) + arm(5) = 6 total
                            e.getArmingEnergy().put(w, 6.0);
                            e.getArmingType().put(w, WeaponArmingType.SPECIAL);
                            break;
                        case "SKIP":
                            // No energy allocated — weapon won't arm
                            break;
                        case "ROLL":
                            if (w instanceof PlasmaLauncher)
                                e.getArmingEnergy().put(w, (double) ((PlasmaLauncher) w).rollingCost());
                            e.getArmingType().put(w, WeaponArmingType.SPECIAL);
                            break;
                        case "FINISH":
                            e.getArmingEnergy().put(w, (double) hw.energyToArm());
                            e.getArmingType().put(w, WeaponArmingType.STANDARD);
                            break;
                        case "EPT":
                            // Enveloping Plasma Torpedo — double final-turn cost, OVERLOAD arming type
                            if (w instanceof PlasmaLauncher)
                                e.getArmingEnergy().put(w, (double) ((PlasmaLauncher) w).eptCost());
                            e.getArmingType().put(w, WeaponArmingType.OVERLOAD);
                            break;
                        default: // STANDARD
                            e.getArmingEnergy().put(w, (double) hw.energyToArm());
                            e.getArmingType().put(w, WeaponArmingType.STANDARD);
                            break;
                    }
                }

                e.setCloakPaid(request.isCloakPaid());

                // Transporter energy
                if (request.getTransUses() > 0) {
                    e.setTransporters(request.getTransUses()
                            * com.sfb.systemgroups.Transporters.energyPerUse());
                }

                // Batteries
                e.setBatteryDraw(Math.max(0, request.getBatteryDraw()));
                e.setBatteryRecharge(Math.max(0, request.getBatteryRecharge()));

                // Reserve warp for HETs (C6.2) — must come from warp engines
                double hetEnergy = Math.max(0, request.getHetEnergy());
                if (movementEnergyNeeded + hetEnergy > warpEngineCapacity + 0.001) {
                    return ActionResult.fail("Insufficient warp engine power for speed " + requestedSpeed
                            + " plus HET reserve — need " + (movementEnergyNeeded + hetEnergy)
                            + ", have " + warpEngineCapacity);
                }
                e.setHighEnergyTurns(hetEnergy);

                // Shield reinforcement
                e.setGeneralReinforcement(Math.max(0, request.getGeneralReinforcement()));
                int[] specReinf = request.getSpecificReinforcement();
                if (specReinf != null && specReinf.length == 6) {
                    e.setSpecificReinforcement(specReinf);
                }

                // Drone rack reloads — player picks individual drones by type and count
                Map<String, Map<String, Integer>> reloadSelections = request.getDroneReloadSelections();
                if (reloadSelections != null && !reloadSelections.isEmpty()) {
                    double deckCrewsLeft = ship.getCrew().getAvailableDeckCrews();
                    for (Map.Entry<String, Map<String, Integer>> entry : reloadSelections.entrySet()) {
                        String rackName = entry.getKey();
                        Map<String, Integer> typeCountMap = entry.getValue();
                        if (typeCountMap == null || typeCountMap.isEmpty()) continue;

                        DroneRack rack = (DroneRack) ship.getWeapons().fetchAllWeapons().stream()
                                .filter(w -> w instanceof DroneRack && w.getName().equalsIgnoreCase(rackName))
                                .findFirst().orElse(null);
                        if (rack == null || !rack.isFunctional()) continue;

                        // Pass 1: collect candidate Drone objects by reference (without removing yet)
                        List<Drone> candidates = new ArrayList<>();
                        for (Map.Entry<String, Integer> tc : typeCountMap.entrySet()) {
                            String droneType = tc.getKey();
                            int needed = tc.getValue() != null ? tc.getValue() : 0;
                            outer:
                            for (List<Drone> set : rack.getReloads()) {
                                for (Drone d : set) {
                                    if (needed <= 0) break outer;
                                    if (d.getDroneType() != null
                                            && d.getDroneType().toString().equals(droneType)
                                            && !candidates.contains(d)) {
                                        candidates.add(d);
                                        needed--;
                                    }
                                }
                            }
                        }

                        if (candidates.isEmpty()) continue;
                        double cost = DroneRack.reloadCost(candidates);
                        if (cost > deckCrewsLeft) continue;

                        // Pass 2: remove the chosen drones from their sets, then stage
                        for (Drone d : candidates) {
                            for (List<Drone> set : rack.getReloads()) {
                                if (set.remove(d)) break; // remove by reference identity
                            }
                        }
                        rack.stagePendingReload(candidates);
                        deckCrewsLeft -= cost;
                    }
                }

                // Apply shuttle/fighter speeds for shuttles owned by this ship
                Map<String, Integer> shuttleSpeeds = request.getShuttleSpeeds();
                if (shuttleSpeeds != null && !shuttleSpeeds.isEmpty()) {
                    for (com.sfb.objects.Shuttle shuttle : game.getActiveShuttles()) {
                        if (!ship.getName().equals(shuttle.getParentShipName())) continue;
                        Integer reqSpeed = shuttleSpeeds.get(shuttle.getName());
                        if (reqSpeed != null) {
                            int clamped = Math.max(0, Math.min(reqSpeed, shuttle.getMaxSpeed()));
                            shuttle.setCurrentSpeed(clamped);
                            shuttle.setSpeed(clamped);
                        }
                    }
                }

                ActionResult allocResult = game.submitAllocation(ship, e);
                // If this was the last allocation, beginImpulses() ran lock-on rolls — drain them
                for (String entry : game.drainLastLockOnLog())
                    appendCombatLog(entry);
                return allocResult;
            }

            case "FIRE": {
                // Attacker may be a ship or an active shuttle/fighter
                Unit attacker = findUnit(request.getShipName());
                if (attacker == null)
                    return ActionResult.fail("Attacker not found: " + request.getShipName());

                Unit target = findUnit(request.getTargetName());
                if (target == null)
                    return ActionResult.fail("Target not found: " + request.getTargetName());

                List<String> weaponNames = request.getWeaponNames();
                if (weaponNames == null || weaponNames.isEmpty())
                    return ActionResult.fail("No weapons specified");

                com.sfb.systemgroups.Weapons attackerWeapons =
                        attacker instanceof Ship
                            ? ((Ship) attacker).getWeapons()
                            : ((Shuttle) attacker).getWeapons();

                // Apply FighterFusion shot modes before resolving weapon list
                Map<String, String> shotModes = request.getShotModes();
                if (shotModes != null && !shotModes.isEmpty()) {
                    for (Weapon w : attackerWeapons.fetchAllWeapons()) {
                        if (w instanceof com.sfb.weapons.FighterFusion) {
                            String mode = shotModes.get(w.getName());
                            if ("DOUBLE".equalsIgnoreCase(mode))
                                ((com.sfb.weapons.FighterFusion) w).setShotMode(com.sfb.weapons.FighterFusion.ShotMode.DOUBLE);
                            else if ("SINGLE".equalsIgnoreCase(mode))
                                ((com.sfb.weapons.FighterFusion) w).setShotMode(com.sfb.weapons.FighterFusion.ShotMode.SINGLE);
                        }
                    }
                }

                List<Weapon> weapons = new ArrayList<>();
                for (String wName : weaponNames) {
                    Weapon w = attackerWeapons.fetchAllWeapons().stream()
                            .filter(x -> x.getName().equalsIgnoreCase(wName))
                            .findFirst().orElse(null);
                    if (w == null)
                        return ActionResult.fail("Weapon not found on attacker: " + wName);
                    weapons.add(w);
                }

                ActionResult fireResult = game.execute(new FireCommand(
                        attacker, target, weapons,
                        request.getRange(), request.getAdjustedRange(), request.getShieldNumber(),
                        request.isUseUim(), request.isDirectFire()));
                if (fireResult.isSuccess())
                    appendCombatLog(fireResult.getMessage());
                return fireResult;
            }

            case "LAUNCH_SHUTTLE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String shuttleName = request.getAction(); // shuttle name passed in action field
                int speed    = request.getSpeed();
                int facing   = request.getRange(); // reuse range field for facing
                // Find the shuttle in any bay
                ShuttleBay foundBay = null;
                Shuttle foundShuttle = null;
                for (ShuttleBay bay : ship.getShuttles().getBays()) {
                    for (Shuttle s : bay.getInventory()) {
                        if (s.getName().equalsIgnoreCase(shuttleName)) {
                            foundBay     = bay;
                            foundShuttle = s;
                            break;
                        }
                    }
                    if (foundBay != null) break;
                }
                if (foundBay == null || foundShuttle == null)
                    return ActionResult.fail("Shuttle not found: " + shuttleName);
                return game.launchShuttle(ship, foundBay, foundShuttle, speed, facing);
            }

            case "LAUNCH_SCATTER_PACK": {
                Ship launcher = findShip(request.getShipName());
                if (launcher == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String packName = request.getAction();
                Unit target = findUnit(request.getTargetName());
                if (target == null)
                    return ActionResult.fail("Target not found: " + request.getTargetName());
                ShuttleBay foundBay = null;
                com.sfb.objects.ScatterPack foundPack = null;
                for (ShuttleBay bay : launcher.getShuttles().getBays()) {
                    for (Shuttle s : bay.getInventory()) {
                        if (s.getName().equalsIgnoreCase(packName)
                                && s instanceof com.sfb.objects.ScatterPack) {
                            foundBay  = bay;
                            foundPack = (com.sfb.objects.ScatterPack) s;
                            break;
                        }
                    }
                    if (foundBay != null) break;
                }
                if (foundBay == null || foundPack == null)
                    return ActionResult.fail("Scatter pack not found: " + packName);
                return game.launchScatterPack(launcher, foundBay, foundPack, target);
            }

            case "LAUNCH_SUICIDE_SHUTTLE": {
                Ship launcher = findShip(request.getShipName());
                if (launcher == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String shuttleName = request.getAction();
                Unit target = findUnit(request.getTargetName());
                if (target == null)
                    return ActionResult.fail("Target not found: " + request.getTargetName());
                ShuttleBay foundBay = null;
                com.sfb.objects.SuicideShuttle foundShuttle = null;
                for (ShuttleBay bay : launcher.getShuttles().getBays()) {
                    for (Shuttle s : bay.getInventory()) {
                        if (s.getName().equalsIgnoreCase(shuttleName)
                                && s instanceof com.sfb.objects.SuicideShuttle) {
                            foundBay     = bay;
                            foundShuttle = (com.sfb.objects.SuicideShuttle) s;
                            break;
                        }
                    }
                    if (foundBay != null) break;
                }
                if (foundBay == null || foundShuttle == null)
                    return ActionResult.fail("Armed suicide shuttle not found: " + shuttleName);
                return game.launchSuicideShuttle(launcher, foundBay, foundShuttle, target);
            }

            case "PERFORM_FIGHTER_HET": {
                String shuttleName = request.getShipName();
                com.sfb.objects.Shuttle shuttle = game.getActiveShuttles().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(shuttleName))
                        .findFirst().orElse(null);
                if (shuttle == null)
                    return ActionResult.fail("Active shuttle not found: " + shuttleName);
                return game.performFighterHet(shuttle, request.getFacing());
            }

            case "MOVE_SHUTTLE": {
                String shuttleName = request.getShipName(); // shuttle name in shipName field
                com.sfb.objects.Shuttle shuttle = game.getActiveShuttles().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(shuttleName))
                        .findFirst().orElse(null);
                if (shuttle == null)
                    return ActionResult.fail("Active shuttle not found: " + shuttleName);
                ShuttleMoveCommand.Action action;
                try {
                    action = ShuttleMoveCommand.Action.valueOf(request.getAction().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ActionResult.fail("Unknown shuttle action: " + request.getAction());
                }
                return game.execute(new ShuttleMoveCommand(shuttle, action));
            }

            case "LAUNCH_DRONE": {
                Ship attacker = findShip(request.getShipName());
                if (attacker == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                Unit target = findUnit(request.getTargetName());
                if (target == null)
                    return ActionResult.fail("Target not found: " + request.getTargetName());
                String rackName = request.getWeaponNames() != null && !request.getWeaponNames().isEmpty()
                        ? request.getWeaponNames().get(0) : null;
                if (rackName == null)
                    return ActionResult.fail("No rack specified");
                com.sfb.weapons.DroneRack rack = attacker.getWeapons().fetchAllWeapons().stream()
                        .filter(w -> w instanceof com.sfb.weapons.DroneRack
                                && w.getName().equalsIgnoreCase(rackName))
                        .map(w -> (com.sfb.weapons.DroneRack) w)
                        .findFirst().orElse(null);
                if (rack == null)
                    return ActionResult.fail("Drone rack not found: " + rackName);
                int droneIndex = request.getRange(); // reuse range field as drone index
                if (droneIndex < 0 || droneIndex >= rack.getAmmo().size())
                    return ActionResult.fail("Invalid drone index: " + droneIndex);
                com.sfb.objects.Drone drone = rack.getAmmo().get(droneIndex);
                return game.execute(new LaunchDroneCommand(attacker, target, rack, drone));
            }

            case "LAUNCH_PLASMA": {
                Ship attacker = findShip(request.getShipName());
                if (attacker == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                Unit target = findUnit(request.getTargetName());
                if (target == null)
                    return ActionResult.fail("Target not found: " + request.getTargetName());
                String wName = request.getWeaponNames() != null && !request.getWeaponNames().isEmpty()
                        ? request.getWeaponNames().get(0) : null;
                if (wName == null)
                    return ActionResult.fail("No launcher specified");
                PlasmaLauncher launcher = attacker.getWeapons().fetchAllWeapons().stream()
                        .filter(w -> w instanceof PlasmaLauncher && w.getName().equalsIgnoreCase(wName))
                        .map(w -> (PlasmaLauncher) w)
                        .findFirst().orElse(null);
                if (launcher == null)
                    return ActionResult.fail("Plasma launcher not found: " + wName);
                return game.execute(new LaunchPlasmaCommand(attacker, target, launcher, request.isPseudo()));
            }

            case "PLACE_TBOMB": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String locStr = request.getAction(); // "x|y" packed in action field
                com.sfb.properties.Location loc;
                try {
                    String[] parts = locStr.split("\\|");
                    loc = new com.sfb.properties.Location(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                } catch (Exception e) {
                    return ActionResult.fail("Invalid location: " + locStr);
                }
                boolean isReal = !request.isPseudo();
                return game.placeTBomb(ship, loc, isReal);
            }

            case "DROP_MINE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                // mineType: "TBOMB", "DUMMY_TBOMB", or "NSM" — passed in action field
                String mineType = request.getAction();
                if (mineType == null || mineType.isBlank())
                    return ActionResult.fail("No mine type specified");
                return game.dropMine(ship, mineType);
            }

            case "HIT_AND_RUN": {
                Ship actingShip = findShip(request.getShipName());
                if (actingShip == null)
                    return ActionResult.fail("Acting ship not found: " + request.getShipName());
                Ship targetShip = findShip(request.getTargetName());
                if (targetShip == null)
                    return ActionResult.fail("Target ship not found: " + request.getTargetName());
                List<String> systemCodes = request.getWeaponNames();
                if (systemCodes == null || systemCodes.isEmpty())
                    return ActionResult.fail("No target systems specified");

                // Resolve each "TYPE" or "WEAPON:name" code into a SystemTarget
                List<com.sfb.properties.SystemTarget> targetSystems = new ArrayList<>();
                for (String code : systemCodes) {
                    if (code.startsWith("WEAPON:")) {
                        String weaponName = code.substring(7);
                        com.sfb.weapons.Weapon w = targetShip.getWeapons().fetchAllWeapons().stream()
                                .filter(x -> x.getName().equalsIgnoreCase(weaponName))
                                .findFirst().orElse(null);
                        if (w == null)
                            return ActionResult.fail("Weapon not found on target: " + weaponName);
                        targetSystems.add(new com.sfb.properties.SystemTarget(w));
                    } else {
                        try {
                            com.sfb.properties.SystemTarget.Type type =
                                    com.sfb.properties.SystemTarget.Type.valueOf(code.toUpperCase());
                            targetSystems.add(new com.sfb.properties.SystemTarget(type, code));
                        } catch (IllegalArgumentException e) {
                            return ActionResult.fail("Unknown system type: " + code);
                        }
                    }
                }
                return game.execute(new com.sfb.commands.HitAndRunCommand(actingShip, targetShip, targetSystems));
            }

            case "BOARDING_ACTION": {
                Ship actingShip = findShip(request.getShipName());
                if (actingShip == null)
                    return ActionResult.fail("Acting ship not found: " + request.getShipName());
                Ship targetShip = findShip(request.getTargetName());
                if (targetShip == null)
                    return ActionResult.fail("Target ship not found: " + request.getTargetName());
                return game.execute(new com.sfb.commands.BoardingActionCommand(
                        actingShip, targetShip,
                        request.getNormalParties(),
                        request.getCommandoParties()));
            }

            case "IDENTIFY_SEEKERS": {
                Ship ship = findShip(request.getShipName());
                if (ship == null) return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.identifySeekers(ship, request.getSeekerNames());
            }

            case "CLOAK": {
                Ship ship = findShip(request.getShipName());
                if (ship == null) return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.execute(new CloakCommand(ship));
            }

            case "UNCLOAK": {
                Ship ship = findShip(request.getShipName());
                if (ship == null) return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.execute(new UncloakCommand(ship));
            }

            default:
                return ActionResult.fail("Unknown action type: " + request.getType());
        }
    }

    private Ship findShip(String name) {
        if (name == null) return null;
        return game.getShips().stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /** Find any Unit (ship, active shuttle, or seeker) by name. */
    private Unit findUnit(String name) {
        if (name == null) return null;
        Ship ship = findShip(name);
        if (ship != null) return ship;
        Shuttle shuttle = game.getActiveShuttles().stream()
                .filter(s -> name.equalsIgnoreCase(s.getName()))
                .findFirst().orElse(null);
        if (shuttle != null) return shuttle;
        return game.getSeekers().stream()
                .filter(s -> s instanceof Unit && name.equalsIgnoreCase(((Unit) s).getName()))
                .map(s -> (Unit) s)
                .findFirst().orElse(null);
    }

    /**
     * After each phase advance, transfer ownership of any ships captured during
     * endTurn() boarding combat to the opposing player (D7.503).
     * In a 2-player game the capturing player is unambiguously the opponent.
     */
    private void transferCapturedShipOwnership() {
        List<com.sfb.objects.Ship> captured = game.getCapturedThisTurn();
        if (captured.isEmpty()) return;

        List<PlayerInfo> playerList = new ArrayList<>(players.values());
        if (playerList.size() != 2) return; // only handle 2-player for now

        for (com.sfb.objects.Ship ship : captured) {
            com.sfb.Player currentOwner = ship.getOwner();
            PlayerInfo newOwnerInfo = playerList.stream()
                    .filter(pi -> pi.getCorePlayer() != currentOwner)
                    .findFirst().orElse(null);
            if (newOwnerInfo == null || newOwnerInfo.getCorePlayer() == null) continue;

            // Move ship from old owner's unit list to new owner's unit list
            if (currentOwner != null)
                currentOwner.getPlayerUnits().remove(ship);
            newOwnerInfo.getCorePlayer().getPlayerUnits().add(ship);
            ship.setOwner(newOwnerInfo.getCorePlayer());
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getId()                             { return id; }
    public Game getGame()                             { return game; }
    public String getHostToken()                      { return hostToken; }
    public Map<String, PlayerInfo> getPlayers()       { return players; }
    public boolean isStarted()                        { return started; }
}
