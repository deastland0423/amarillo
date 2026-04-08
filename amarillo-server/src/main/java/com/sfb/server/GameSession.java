package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.Player;
import com.sfb.commands.AdvancePhaseCommand;
import com.sfb.commands.CloakCommand;
import com.sfb.commands.FireCommand;
import com.sfb.commands.MoveCommand;
import com.sfb.commands.UncloakCommand;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public void start() {
        game.setup();
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
                return game.execute(new AdvancePhaseCommand());
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
                if (request.isTopOffCap()) {
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
                        request.getRange(), request.getAdjustedRange(), request.getShieldNumber()));
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
