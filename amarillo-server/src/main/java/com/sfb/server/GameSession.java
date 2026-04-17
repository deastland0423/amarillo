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

    /**
     * COI selections submitted by each player, keyed by player token then ship name.
     * Collected during the pre-game lobby; applied when start() is called.
     */
    private final Map<String, Map<String, com.sfb.scenario.CoiLoadout>> pendingCoi = new LinkedHashMap<>();

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
    public boolean allReady()    { return readyPlayers.size() >= players.size(); }

    /** Clear ready flags — called automatically when the phase actually advances. */
    private void clearReady()    { readyPlayers.clear(); }

    // -------------------------------------------------------------------------
    // Ship assignment
    // -------------------------------------------------------------------------

    /**
     * Assigns a ship to a player by setting ship.owner on the core Game object.
     * Must be called after start() so ships exist.
     * Returns an error string on failure, null on success.
     */
    public String assignShip(String playerToken, String shipName) {
        if (!started)
            return "Game must be started before assigning ships";

        PlayerInfo target = players.get(playerToken);
        if (target == null)
            return "Player token not found";

        Ship ship = findShip(shipName);
        if (ship == null)
            return "Ship not found: " + shipName;

        // Check not already owned by someone else
        if (ship.getOwner() != null) {
            boolean ownedByTarget = target.getCorePlayer() != null
                    && ship.getOwner() == target.getCorePlayer();
            if (ownedByTarget)
                return "Ship already assigned to this player";
            return "Ship already assigned to another player";
        }

        // Lazily create a core Player for this PlayerInfo if needed
        if (target.getCorePlayer() == null) {
            Player p = new Player();
            p.setName(target.getName());
            game.getPlayers().add(p);
            target.setCorePlayer(p);
        }

        ship.setOwner(target.getCorePlayer());
        target.getCorePlayer().getPlayerUnits().add(ship);
        return null;
    }

    /**
     * Returns true if the token owns the named ship.
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
        if (ship == null) return false;

        return ship.getOwner() == p.getCorePlayer();
    }

    // -------------------------------------------------------------------------
    // Game lifecycle
    // -------------------------------------------------------------------------

    /**
     * Submit COI selections for one or more ships owned by the given player.
     * shipLoadouts maps ship name → CoiLoadout.
     * Can be called multiple times before start(); later calls overwrite earlier ones.
     */
    public void submitCoi(String playerToken, Map<String, com.sfb.scenario.CoiLoadout> shipLoadouts) {
        pendingCoi.put(playerToken, new LinkedHashMap<>(shipLoadouts));
    }

    public void start(String scenarioId) throws java.io.IOException {
        com.sfb.objects.ShipLibrary.loadAllSpecs("data/factions");
        com.sfb.scenario.ScenarioSpec spec =
                com.sfb.scenario.ScenarioSpec.fromJson("data/scenarios/" + scenarioId.toLowerCase() + ".json");

        // Build ships, then map COI loadouts from ship-name keys to Ship objects
        java.util.List<java.util.List<com.sfb.objects.Ship>> sideShips =
                com.sfb.scenario.ScenarioLoader.loadShips(spec);

        // Flatten ship name → CoiLoadout from all players' submissions
        Map<String, com.sfb.scenario.CoiLoadout> byName = new LinkedHashMap<>();
        for (Map<String, com.sfb.scenario.CoiLoadout> playerMap : pendingCoi.values()) {
            byName.putAll(playerMap);
        }

        // Build Ship → CoiLoadout map for setupFromScenario
        Map<com.sfb.objects.Ship, com.sfb.scenario.CoiLoadout> coiMap = new LinkedHashMap<>();
        for (java.util.List<com.sfb.objects.Ship> side : sideShips) {
            for (com.sfb.objects.Ship ship : side) {
                com.sfb.scenario.CoiLoadout loadout = byName.get(ship.getName());
                if (loadout != null) coiMap.put(ship, loadout);
            }
        }

        game.setupFromScenario(spec, sideShips, coiMap.isEmpty() ? null : coiMap);
        started = true;
    }

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
                readyPlayers.add(token);
                int ready = readyPlayers.size();
                int total = players.size();
                if (!allReady()) {
                    return ActionResult.ok("WAITING:" + ready + "/" + total);
                }
                clearReady();
                return game.execute(new AdvancePhaseCommand());
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
                e.setWarpMovement(warpSpeed * moveCost);
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
                    switch (choice.toUpperCase()) {
                        case "OVERLOAD":
                            e.getArmingEnergy().put(w, (double) Constants.gArmingCost[0] * 2);
                            e.getArmingType().put(w, WeaponArmingType.OVERLOAD);
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
                            e.getArmingEnergy().put(w, (double) ((HeavyWeapon) w).energyToArm());
                            e.getArmingType().put(w, WeaponArmingType.STANDARD);
                            break;
                        default: // STANDARD
                            e.getArmingEnergy().put(w, (double) Constants.gArmingCost[0]);
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

                // Shield reinforcement
                e.setGeneralReinforcement(Math.max(0, request.getGeneralReinforcement()));
                int[] specReinf = request.getSpecificReinforcement();
                if (specReinf != null && specReinf.length == 6) {
                    e.setSpecificReinforcement(specReinf);
                }

                // Drone rack reloads — stage each requested rack
                List<String> reloadNames = request.getDroneReloads();
                if (reloadNames != null && !reloadNames.isEmpty()) {
                    int deckCrewsLeft = ship.getCrew().getAvailableDeckCrews();
                    for (String rackName : reloadNames) {
                        DroneRack rack = (DroneRack) ship.getWeapons().fetchAllWeapons().stream()
                                .filter(w -> w instanceof DroneRack && w.getName().equalsIgnoreCase(rackName))
                                .findFirst().orElse(null);
                        if (rack == null || !rack.isFunctional() || rack.getReloads().isEmpty()) continue;
                        double cost = DroneRack.reloadCost(rack.getReloads().get(0));
                        if (cost > deckCrewsLeft) continue;
                        rack.stagePendingReload(rack.getReloads().get(0));
                        deckCrewsLeft -= cost;
                    }
                }

                return game.submitAllocation(ship, e);
            }

            case "FIRE": {
                Ship attacker = findShip(request.getShipName());
                if (attacker == null)
                    return ActionResult.fail("Attacker not found: " + request.getShipName());

                Unit target = findUnit(request.getTargetName());
                if (target == null)
                    return ActionResult.fail("Target not found: " + request.getTargetName());

                List<String> weaponNames = request.getWeaponNames();
                if (weaponNames == null || weaponNames.isEmpty())
                    return ActionResult.fail("No weapons specified");

                List<Weapon> weapons = new ArrayList<>();
                for (String wName : weaponNames) {
                    Weapon w = attacker.getWeapons().fetchAllWeapons().stream()
                            .filter(x -> x.getName().equalsIgnoreCase(wName))
                            .findFirst().orElse(null);
                    if (w == null)
                        return ActionResult.fail("Weapon not found on attacker: " + wName);
                    weapons.add(w);
                }

                return game.execute(new FireCommand(
                        attacker, target, weapons,
                        request.getRange(), request.getAdjustedRange(), request.getShieldNumber(),
                        request.isUseUim()));
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

    /** Find any Unit (ship or seeker) by name. */
    private Unit findUnit(String name) {
        if (name == null) return null;
        Ship ship = findShip(name);
        if (ship != null) return ship;
        return game.getSeekers().stream()
                .filter(s -> s instanceof Unit && name.equalsIgnoreCase(((Unit) s).getName()))
                .map(s -> (Unit) s)
                .findFirst().orElse(null);
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
