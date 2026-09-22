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
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.properties.WeaponArmingType;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.HeavyWeapon;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

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
            this.name = name;
        }

        public String getToken() {
            return token;
        }

        public String getName() {
            return name;
        }

        public Player getCorePlayer() {
            return corePlayer;
        }

        void setCorePlayer(Player p) {
            this.corePlayer = p;
        }

        /** Convenience: ships this player owns, sourced from the core Player object. */
        public List<String> getShipNames() {
            if (corePlayer == null)
                return List.of();
            return corePlayer.getPlayerUnits().stream()
                    .map(u -> u instanceof Ship ? ((Ship) u).getName() : null)
                    .filter(n -> n != null)
                    .toList();
        }
    }

    private final String id;
    private final Game game;
    private final String hostToken;

    /**
     * Serializes all work on this session: actions, lobby changes, and state
     * snapshots run strictly one at a time, so the (thread-unsafe) Game never
     * sees two callers at once and every broadcast snapshot is consistent.
     * Per-session — different games never block each other. Fair, so
     * near-simultaneous clicks resolve first-come-first-served. Reentrant, so
     * the controller can hold it across an action plus its broadcast while
     * executeAction() takes it again internally.
     */
    private final ReentrantLock lock = new ReentrantLock(true);

    /** token → PlayerInfo */
    private final Map<String, PlayerInfo> players = new LinkedHashMap<>();

    /** Tokens of players who have clicked "Ready" for the current phase. */
    private final Set<String> readyPlayers = new HashSet<>();

    // ---- Fire declaration round (D6.315 written orders) ----
    // One round per impulse: the first FIRE call convenes everyone; each player
    // seals a commit (fire plan and/or EW changes) or passes; all responses in
    // → EW applies first, then all fire resolves against the new EW.

    /** A player's sealed orders — invisible to others until the reveal. */
    private static class DeclarationCommit {
        final List<ActionRequest.FireOrder> fireOrders;
        final List<ActionRequest.EwAdjustment> ewAdjustments;
        DeclarationCommit(List<ActionRequest.FireOrder> fire, List<ActionRequest.EwAdjustment> ew) {
            this.fireOrders = fire != null ? fire : List.of();
            this.ewAdjustments = ew != null ? ew : List.of();
        }
    }

    private boolean declarationOpen = false;
    private String declarationCallerToken = null;
    private int declarationImpulse = -1;      // absolute impulse the round belongs to
    private boolean declarationSpent = false; // this impulse's round already resolved
    private final Map<String, DeclarationCommit> declarationCommits = new LinkedHashMap<>();

    // ---- Launch declaration round (Annex #2, Impulse Activity Segment) ----
    // Deliberately INDEPENDENT of the fire round above: separate state, separate actions.
    // Both could be open in the same impulse, and one set of fields doing double duty is how
    // they would interfere. Same shape, because the table ritual is the same - somebody calls,
    // everyone commits blind, all of it reveals together.

    private boolean activityOpen = false;
    private String activityCallerToken = null;
    private int activityImpulse = -1;
    private boolean activitySpent = false;
    private final Map<String, List<ActionRequest.ActivityOrder>> activityCommits = new LinkedHashMap<>();

    /**
     * Combat events accumulated since the last broadcast; drained by
     * drainCombatLog().
     */
    private final List<String> pendingCombatLog = new ArrayList<>();

    /**
     * COI selections submitted by each player, keyed by player token then ship
     * name.
     * Collected during the pre-game lobby; applied when start() is called.
     */
    private final Map<String, Map<String, com.sfb.scenario.CoiLoadout>> pendingCoi = new LinkedHashMap<>();

    /** Tokens of players who have submitted (or skipped) their COI. */
    private final Set<String> coiDoneTokens = new HashSet<>();

    /**
     * Where each player has set their ships down, by token then ship name. Secret until
     * everyone is finished: a broadcast carries only how many each player has placed, and a
     * player fetches their own through an authenticated call.
     */
    private final Map<String, Map<String, com.sfb.scenario.Deployment.Placement>> pendingDeployment
            = new LinkedHashMap<>();

    /**
     * Who has said they are finished. Reversible until the last player says it, because there
     * is no advantage in changing your mind about a setup nobody can see, and clicking Done a
     * moment early should not cost the battle.
     */
    private final Set<String> deploymentDoneTokens = new HashSet<>();

    /**
     * shipName → playerToken, recorded before start() so ships can be assigned in
     * the lobby.
     */
    private final Map<String, String> pendingAssignments = new LinkedHashMap<>();

    /** shipName → team name (side display name), built when scenario loads. */
    private final Map<String, String> shipTeamName = new LinkedHashMap<>();

    // Scenario loaded but not yet started
    private boolean scenarioLoaded = false;
    private String loadedScenarioId = null;
    private com.sfb.scenario.ScenarioSpec loadedSpec = null;
    private List<List<com.sfb.objects.Ship>> loadedSideShips = null;

    private boolean started = false;

    public GameSession(String id, String hostToken, String hostName) {
        this.id = id;
        this.game = new Game();
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

    public int getReadyCount() {
        return readyPlayers.size();
    }

    public int getPlayerCount() {
        return players.size();
    }

    /** Append a combat event to the pending log (broadcast on next state push). */
    public void appendCombatLog(String entry) {
        pendingCombatLog.add(entry);
    }

    /** Return all pending combat log entries and clear the list. */
    public List<String> drainCombatLog() {
        List<String> copy = new ArrayList<>(pendingCombatLog);
        pendingCombatLog.clear();
        return copy;
    }

    public boolean allReady() {
        return readyPlayers.size() >= players.size();
    }

    /**
     * Clear ready flags — called automatically when the phase actually advances.
     */
    private void clearReady() {
        readyPlayers.clear();
    }

    // -------------------------------------------------------------------------
    // Ship assignment
    // -------------------------------------------------------------------------

    /**
     * Records a ship assignment in the pre-game lobby.
     * Works before start() — ships are in pendingAssignments until start() resolves
     * them.
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

    /**
     * Validate an Orion fleet's option-mount choices against its cartel quota
     * (G15.44). Returns a violation message, or null if within quota / no cartel.
     */
    public String validateCartelQuota(String cartelName,
                                      Map<String, com.sfb.scenario.CoiLoadout> loadouts) {
        if (cartelName == null || cartelName.isBlank() || loadedSideShips == null) {
            return null;
        }
        com.sfb.objects.OrionCartel cartel =
                com.sfb.objects.OrionCartelTable.loadDefault().get(cartelName);
        if (cartel == null) {
            return null;
        }
        Map<String, com.sfb.objects.Ship> byName = new java.util.HashMap<>();
        for (List<com.sfb.objects.Ship> side : loadedSideShips) {
            for (com.sfb.objects.Ship s : side) {
                byName.put(s.getName(), s);
            }
        }
        int totalMounts = 0;
        List<String> selected = new java.util.ArrayList<>();
        for (Map.Entry<String, com.sfb.scenario.CoiLoadout> e : loadouts.entrySet()) {
            com.sfb.objects.Ship ship = byName.get(e.getKey());
            if (ship == null) {
                continue;
            }
            totalMounts += ship.getOptionMounts().size();
            selected.addAll(e.getValue().optionMounts.values());
        }
        com.sfb.objects.CartelQuota.Result r = com.sfb.objects.CartelQuota.evaluate(
                totalMounts, selected, cartel, com.sfb.objects.OptionMountCatalog.loadDefault());
        return r.withinQuota ? null : String.join("; ", r.violations);
    }

    /** All ship names across all scenario sides, in order. */
    public List<String> getAllShipNames() {
        if (loadedSideShips == null)
            return List.of();
        return loadedSideShips.stream()
                .flatMap(List::stream)
                .map(Ship::getName)
                .toList();
    }

    /** Ships not yet assigned to any player. */
    public List<String> getUnassignedShipNames() {
        if (!scenarioLoaded)
            return List.of();
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
        if (p == null || p.getCorePlayer() == null)
            return false;

        Ship ship = findShip(shipName);
        if (ship != null)
            return ship.getOwner() == p.getCorePlayer();

        // Also accept active shuttle names
        com.sfb.objects.shuttles.Shuttle shuttle = game.getActiveShuttles().stream()
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
    /**
     * What these COI selections would fail to do, per ship, without doing any of it.
     *
     * Runs the REAL applyCoi against a freshly built set of ships and reads the notes off
     * them. A separate implementation of the checks would drift from the one that decides
     * the battle, and running it against the actual ships would spend their drones twice
     * over — so this builds its own and throws them away.
     */
    public Map<String, java.util.List<String>> previewCoi(
            Map<String, com.sfb.scenario.CoiLoadout> loadouts) {
        Map<String, java.util.List<String>> problems = new java.util.LinkedHashMap<>();
        if (loadedSpec == null || loadouts == null || loadouts.isEmpty())
            return problems;

        for (java.util.List<com.sfb.objects.Ship> side
                : com.sfb.scenario.ScenarioLoader.loadShips(loadedSpec))
            for (com.sfb.objects.Ship ship : side) {
                com.sfb.scenario.CoiLoadout loadout = loadouts.get(ship.getName());
                if (loadout == null)
                    continue;
                com.sfb.scenario.ScenarioLoader.applyCoi(ship, loadout, loadedSpec);
                if (!ship.getSetupNotes().isEmpty())
                    problems.put(ship.getName(), new java.util.ArrayList<>(ship.getSetupNotes()));
            }
        return problems;
    }

    public void submitCoi(String playerToken, Map<String, com.sfb.scenario.CoiLoadout> shipLoadouts) {
        pendingCoi.put(playerToken, new LinkedHashMap<>(shipLoadouts));
        coiDoneTokens.add(playerToken);
    }

    public boolean isCoiDone(String token) {
        return coiDoneTokens.contains(token);
    }

    public boolean allCoiDone() {
        return coiDoneTokens.containsAll(players.keySet());
    }

    // -------------------------------------------------------------------------
    // Deployment: each player sets their own ships down, in secret
    // -------------------------------------------------------------------------

    /**
     * True when this battle expects players to place their own ships — that is, when a side was
     * given ground to set up on. A hand-written scenario that names every hex does not, and its
     * players go straight from Commander's Options to the first turn.
     */
    public boolean isDeploymentRequired() {
        if (loadedSpec == null || loadedSpec.sides == null)
            return false;
        return loadedSpec.sides.stream().anyMatch(s -> s.deploymentZone != null);
    }

    /** The side a player belongs to, found through any ship they were given. */
    private com.sfb.scenario.ScenarioSpec.SideSpec sideFor(String token) {
        if (loadedSpec == null || loadedSpec.sides == null)
            return null;
        List<String> mine = getAssignedShipsFor(token);
        for (com.sfb.scenario.ScenarioSpec.SideSpec side : loadedSpec.sides) {
            if (side.ships == null)
                continue;
            for (com.sfb.scenario.ScenarioSpec.ShipSetup ship : side.ships)
                if (mine.contains(ship.shipName))
                    return side;
        }
        return null;
    }

    /** The ground this player may set up on, or null if they have no ships yet. */
    public com.sfb.scenario.MapRegion deploymentZoneFor(String token) {
        com.sfb.scenario.ScenarioSpec.SideSpec side = sideFor(token);
        return side != null ? side.deploymentZone : null;
    }

    /** Hexes no ship may be set down on, whoever they belong to. */
    public java.util.Set<String> noEntryHexes() {
        return com.sfb.scenario.Deployment.noEntryHexes(loadedSpec);
    }

    /** This player's own placements — never broadcast, only fetched by the player themselves. */
    public Map<String, com.sfb.scenario.Deployment.Placement> deploymentFor(String token) {
        return new LinkedHashMap<>(pendingDeployment.getOrDefault(token, Map.of()));
    }

    /**
     * Record where a player has put their ships, replacing whatever they had before.
     *
     * @return everything wrong with the setup; empty means it was accepted
     */
    public List<String> submitDeployment(String token,
                                         List<com.sfb.scenario.Deployment.Placement> placements) {
        List<String> mine = getAssignedShipsFor(token);
        List<String> problems = new java.util.ArrayList<>();

        for (com.sfb.scenario.Deployment.Placement p : placements)
            if (!mine.contains(p.shipName()))
                problems.add(p.shipName() + " is not yours to place");
        if (!problems.isEmpty())
            return problems;

        problems.addAll(com.sfb.scenario.Deployment.check(
                placements, deploymentZoneFor(token), noEntryHexes(),
                loadedSpec != null ? loadedSpec.mapCols : 42,
                loadedSpec != null ? loadedSpec.mapRows : 32));
        if (!problems.isEmpty())
            return problems;

        Map<String, com.sfb.scenario.Deployment.Placement> byShip = new LinkedHashMap<>();
        for (com.sfb.scenario.Deployment.Placement p : placements)
            byShip.put(p.shipName(), p);
        pendingDeployment.put(token, byShip);

        // A setup that no longer covers every ship cannot still be finished.
        if (!byShip.keySet().containsAll(mine))
            deploymentDoneTokens.remove(token);
        return problems;
    }

    /**
     * Lay this player's ships out for them, somewhere legal in their own ground. Somewhere to
     * start from rather than somewhere to finish — the core works out the pattern, and the
     * player moves whatever they do not like.
     */
    public List<String> autoArrange(String token) {
        List<com.sfb.scenario.Deployment.Placement> placements =
                com.sfb.scenario.Deployment.autoArrange(
                        getAssignedShipsFor(token), deploymentZoneFor(token), noEntryHexes(),
                        loadedSpec != null ? loadedSpec.mapCols : 42,
                        loadedSpec != null ? loadedSpec.mapRows : 32);
        return submitDeployment(token, placements);
    }

    /** True once every one of this player's ships is somewhere legal. */
    public boolean isDeploymentComplete(String token) {
        List<String> mine = getAssignedShipsFor(token);
        if (mine.isEmpty())
            return true;   // nothing to place
        return com.sfb.scenario.Deployment.isComplete(
                mine, new java.util.ArrayList<>(deploymentFor(token).values()),
                deploymentZoneFor(token), noEntryHexes(),
                loadedSpec != null ? loadedSpec.mapCols : 42,
                loadedSpec != null ? loadedSpec.mapRows : 32);
    }

    /** Say you are finished, or that you are not after all. */
    public String setDeploymentDone(String token, boolean done) {
        if (!done) {
            deploymentDoneTokens.remove(token);
            return null;
        }
        if (!isDeploymentComplete(token))
            return "Every ship must be set down somewhere legal first";
        deploymentDoneTokens.add(token);
        return null;
    }

    public boolean isDeploymentDone(String token) {
        return deploymentDoneTokens.contains(token);
    }

    public boolean allDeploymentDone() {
        return !isDeploymentRequired() || deploymentDoneTokens.containsAll(players.keySet());
    }

    /**
     * Write the placements into the scenario and rebuild the ships from it.
     * <p>
     * Into the spec rather than onto the ships, because the spec is what builds them: startHex,
     * startHeading and startSpeed are read by ScenarioLoader, and going through it keeps one
     * path from "where a ship starts" to "where the ship is".
     */
    private void applyDeployments() {
        if (!isDeploymentRequired() || loadedSpec == null || loadedSpec.sides == null)
            return;

        Map<String, com.sfb.scenario.Deployment.Placement> all = new LinkedHashMap<>();
        for (Map<String, com.sfb.scenario.Deployment.Placement> byShip : pendingDeployment.values())
            all.putAll(byShip);
        if (all.isEmpty())
            return;

        for (com.sfb.scenario.ScenarioSpec.SideSpec side : loadedSpec.sides) {
            if (side.ships == null)
                continue;
            for (com.sfb.scenario.ScenarioSpec.ShipSetup setup : side.ships) {
                com.sfb.scenario.Deployment.Placement p = all.get(setup.shipName);
                if (p == null)
                    continue;   // never placed: it keeps the starting line it was given
                setup.startHex = p.hex();
                setup.startHeading = p.heading();
                setup.startSpeed = p.speed();
            }
        }
        loadedSideShips = com.sfb.scenario.ScenarioLoader.loadShips(loadedSpec);
    }

    // -------------------------------------------------------------------------
    // Game lifecycle
    // -------------------------------------------------------------------------

    /**
     * Load a scenario into the lobby without starting the game clock.
     * Ships become available for assignment immediately after this call.
     */
    public void loadScenario(String scenarioId) throws java.io.IOException {
        com.sfb.objects.ShipLibrary.loadAllSpecs("data/factions");
        adopt(com.sfb.scenario.ScenarioSpec.fromJson(
                "data/scenarios/" + scenarioId.toLowerCase() + ".json"), scenarioId);
    }

    /**
     * Take a scenario that was built rather than read — a battle between bought fleets
     * (S8.0). From here on it is indistinguishable from a hand-authored one, which is the
     * point: assignment, Commander's Options, victory and the lobby all work from the spec.
     */
    public void loadBuiltScenario(com.sfb.scenario.ScenarioSpec spec, String id) {
        com.sfb.objects.ShipLibrary.loadAllSpecs("data/factions");
        adopt(spec, id);
    }

    private void adopt(com.sfb.scenario.ScenarioSpec spec, String scenarioId) {
        loadedSpec = spec;
        loadedSideShips = com.sfb.scenario.ScenarioLoader.loadShips(loadedSpec);
        loadedScenarioId = scenarioId;
        scenarioLoaded = true;
        // Reset any prior assignments/COI when a new scenario is loaded
        pendingAssignments.clear();
        coiDoneTokens.clear();
        pendingCoi.clear();
        pendingDeployment.clear();
        deploymentDoneTokens.clear();
        // Build shipName → team name index from scenario sides
        shipTeamName.clear();
        for (int i = 0; i < loadedSpec.sides.size(); i++) {
            String teamName = loadedSpec.sides.get(i).name;
            if (teamName == null || teamName.isBlank())
                teamName = "Team " + (i + 1);
            for (com.sfb.objects.Ship ship : loadedSideShips.get(i))
                shipTeamName.put(ship.getName(), teamName);
        }
    }

    /**
     * Start the game clock. Scenario must already be loaded and all players
     * must have submitted (or skipped) their COI.
     */
    public void start() throws java.io.IOException {
        if (!scenarioLoaded)
            throw new IllegalStateException("Load a scenario before starting");

        // Where the players put their ships, before anything is built from the spec.
        applyDeployments();

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
                if (loadout != null)
                    coiMap.put(ship, loadout);
            }
        }

        game.setupFromScenario(loadedSpec, loadedSideShips, coiMap.isEmpty() ? null : coiMap);

        // Resolve pending assignments to live Ship objects
        for (Map.Entry<String, String> entry : pendingAssignments.entrySet()) {
            String shipName = entry.getKey();
            String pToken = entry.getValue();
            PlayerInfo info = players.get(pToken);
            if (info == null)
                continue;
            Ship ship = findShip(shipName);
            if (ship == null)
                continue;
            if (info.getCorePlayer() == null) {
                Player p = new Player();
                p.setName(info.getName());
                p.setTeamName(shipTeamName.getOrDefault(shipName, "Team 1"));
                game.getPlayers().add(p);
                info.setCorePlayer(p);
            }
            ship.setOwner(info.getCorePlayer());
            info.getCorePlayer().getPlayerUnits().add(ship);
        }

        started = true;
    }

    public boolean isScenarioLoaded() {
        return scenarioLoaded;
    }

    public String getLoadedScenarioId() {
        return loadedScenarioId;
    }

    /** The parsed spec of the loaded scenario, or null — lobby display uses it. */
    public com.sfb.scenario.ScenarioSpec getLoadedSpec() {
        return loadedSpec;
    }

    // -------------------------------------------------------------------------
    // Action dispatch
    // -------------------------------------------------------------------------

    /**
     * Execute an action on behalf of a player.
     * Ownership is validated before this is called by the controller.
     * Serialized on the session lock — safe to call from concurrent threads.
     */
    public ActionResult executeAction(ActionRequest request) {
        lock.lock();
        try {
            ActionResult result = doExecuteAction(request);
            autoAdvanceEndOfImpulse();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 6E is pure bookkeeping — no player action is gated to END_OF_IMPULSE, so
     * a Done round-trip there would carry no decision. Whenever an action
     * leaves the game sitting in it (Direct Fire exit, or a Reinforcement/DAC
     * interrupt returning into it), advance immediately. The end-of-impulse
     * log still lands in the combat log; players read it during the next
     * Movement phase, which keeps its Ready gate.
     */
    private void autoAdvanceEndOfImpulse() {
        int guard = 0;
        while (game.getCurrentPhase() == Game.ImpulsePhase.END_OF_IMPULSE && guard++ < 4) {
            ActionResult r = game.execute(new AdvancePhaseCommand());
            if (!r.isSuccess())
                break;
            if (r.getMessage() != null && !r.getMessage().isBlank())
                appendCombatLog(r.getMessage());
            // End-of-turn boarding combat inside this advance can capture ships
            transferCapturedShipOwnership();
        }
    }

    private ActionResult doExecuteAction(ActionRequest request) {
        switch (request.getType().toUpperCase()) {

            case "ADVANCE_PHASE": {
                String token = request.getPlayerToken();
                // An open fire declaration must be answered before the phase moves
                if (game.getCurrentPhase() == Game.ImpulsePhase.DIRECT_FIRE) {
                    refreshDeclarationState();
                    if (declarationOpen) {
                        // A player who has already sealed their orders is not being asked
                        // for anything — the round simply has not resolved yet, because
                        // someone else has still to answer. Report that as an ordinary
                        // wait, the same shape as readying up, so the client offers Cancel
                        // instead of showing an error for something already done.
                        if (declarationCommits.containsKey(token))
                            return ActionResult.ok("WAITING:" + declarationCommits.size()
                                    + "/" + players.size());
                        return ActionResult.fail(getFireDeclarationCallerName()
                                + " has declared fire — answer the declaration before the"
                                + " phase can advance");
                    }
                }
                // And an open launch declaration, the same way (Annex #2, 6B)
                if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY) {
                    refreshActivityState();
                    if (activityOpen) {
                        if (activityCommits.containsKey(token))
                            return ActionResult.ok("WAITING:" + activityCommits.size()
                                    + "/" + players.size());
                        return ActionResult.fail(getActivityDeclarationCallerName()
                                + " has called for launches — answer the declaration"
                                + " before the phase can advance");
                    }
                }
                // During movement phase, reject ready if this player still has ships or
                // shuttles to move
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
                            // Was "MUST_MOVE:<name>"; nothing on the client translated it,
                            // so the raw token reached the screen.
                            return ActionResult.fail(pending
                                    + " has not moved yet — every ship and shuttle must move"
                                    + " before the phase can advance");
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
                if (phaseResult.isSuccess() && phaseResult.getMessage() != null
                        && !phaseResult.getMessage().isBlank()) {
                    appendCombatLog(phaseResult.getMessage());
                }
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

            case "PERFORM_TACTICAL_TURN": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                boolean sublight = "SUBLIGHT".equalsIgnoreCase(request.getAction());
                return game.performTacticalTurn(ship, request.getFacing(), sublight);
            }

            case "CONFIRM_ACCEL_DISENGAGE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.confirmAccelDisengage(ship, request.isDeclare());
            }

            case "DISENGAGE_SEPARATION": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.disengageBySeparation(ship);
            }

            case "CONCEDE": {
                String token = request.getPlayerToken();
                PlayerInfo pi = players.get(token);
                if (pi == null)
                    return ActionResult.fail("Unknown player token");
                return game.concede(pi.getName());
            }

            case "ALLOCATE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                if (!game.isAwaitingAllocation())
                    return ActionResult.fail("Not currently in allocation phase");

                Energy e = new Energy();

                // Orion engine doubling (G15.2) — declared here; only Orion ships
                // that can double may (G15.28).
                boolean anyDouble = request.isDoubleLwarp() || request.isDoubleRwarp()
                        || request.isDoubleCwarp() || request.isDoubleImpulse();
                if (anyDouble && !ship.canDoubleEngines())
                    return ActionResult.fail(ship.getName()
                            + " cannot double its engines (G15.2 — Orion warships only)");
                e.setDoubleLwarp(request.isDoubleLwarp());
                e.setDoubleRwarp(request.isDoubleRwarp());
                e.setDoubleCwarp(request.isDoubleCwarp());
                e.setDoubleImpulse(request.isDoubleImpulse());

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
                double warpEngineCapacity = ship.getPowerSystems().getAvailableLWarp()
                        + ship.getPowerSystems().getAvailableRWarp()
                        + ship.getPowerSystems().getAvailableCWarp()
                        // doubled warp engines output 2x their boxes (G15.2)
                        + (request.isDoubleLwarp() ? ship.getPowerSystems().getAvailableLWarp() : 0)
                        + (request.isDoubleRwarp() ? ship.getPowerSystems().getAvailableRWarp() : 0)
                        + (request.isDoubleCwarp() ? ship.getPowerSystems().getAvailableCWarp() : 0);
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
                } else if (ship.isCapacitorsCharged()) {
                    double capNeeded = ship.getWeapons().getAvailablePhaserCapacitor()
                            - ship.getWeapons().getPhaserCapacitorEnergy();
                    // Player may charge any amount up to what the capacitor can hold
                    // (partial refill). A negative capacitorCharge means "use the
                    // legacy top-off flag" (fill to full).
                    double requested = request.getCapacitorCharge() >= 0
                            ? request.getCapacitorCharge()
                            : (request.isTopOffCap() ? capNeeded : 0);
                    e.setPhaserCapacitor(Math.max(0, Math.min(requested, capNeeded)));
                }

                // ESG generators (G23.21) — add allocated energy per ESG designator
                if (request.getEsgEnergy() != null) {
                    for (Map.Entry<String, Integer> entry : request.getEsgEnergy().entrySet()) {
                        e.setEsgEnergy(entry.getKey(), Math.max(0, entry.getValue()));
                    }
                }

                // Scout function channels (G24.14) — power the requested channels (1 energy each),
                // plus the ship-level pool of EW points the scout generates for lending (G24.211).
                if (request.getPoweredChannels() != null) {
                    e.setPoweredChannels(request.getPoweredChannels());
                }
                e.setScoutEwPoints(request.getScoutEwPoints());

                // Heavy weapon arming
                Map<String, String> arming = request.getWeaponArming();
                for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (!(w instanceof HeavyWeapon))
                        continue;
                    String choice = arming != null ? arming.get(w.getName()) : null;
                    HeavyWeapon hw = (HeavyWeapon) w;

                    // A photon still in its arming cycle is dialled by energy, not by a mode
                    // (E4.21/E4.411): two points arms it as a standard torpedo, every point
                    // above that is overload energy, and six is a 100% overload. The client
                    // sends the amount; core records what actually lands in the tube.
                    Map<String, Double> dial = request.getPhotonArming();
                    Double dialled = dial != null ? dial.get(w.getName()) : null;

                    // A loaded torpedo is dialled too (E4.411/E4.412): the holding cost keeps it
                    // in the tube, and anything above that overloads it where it sits. Holding
                    // energy never counts toward the overload, and an overloaded torpedo costs
                    // two a turn to hold thereafter rather than one (E4.413).
                    if (dialled != null && w instanceof com.sfb.weapons.Photon && hw.isArmed()) {
                        double amount = dialled;
                        double holdCost = hw.holdEnergyCost();
                        if (amount <= 0) {
                            e.getArmingEnergy().put(w, 0.0);   // discharge it (E4.22)
                            continue;
                        }
                        if (amount < holdCost)
                            return ActionResult.fail(w.getName() + ": holding a loaded torpedo costs "
                                    + (int) holdCost + " (E4.22/E4.413)");
                        double room = com.sfb.weapons.Photon.MAX_OVERLOAD
                                - ((com.sfb.weapons.Photon) w).overloadEnergy();
                        if (amount > holdCost + room)
                            return ActionResult.fail(w.getName() + ": at most " + (int) (holdCost + room)
                                    + " — the hold plus the " + (int) room
                                    + " of overload it can still take (E4.41)");
                        if ("PROX".equalsIgnoreCase(choice) && amount > holdCost)
                            return ActionResult.fail(w.getName()
                                    + ": a proximity torpedo cannot be overloaded (E4.34)");
                        e.getArmingEnergy().put(w, amount);
                        e.getArmingType().put(w, "PROX".equalsIgnoreCase(choice)
                                ? WeaponArmingType.SPECIAL
                                : amount > holdCost ? WeaponArmingType.OVERLOAD : hw.getArmingType());
                        continue;
                    }

                    if (dialled != null && w instanceof com.sfb.weapons.Photon && !hw.isArmed()) {
                        double amount = dialled;
                        if (amount <= 0) {
                            e.getArmingEnergy().put(w, 0.0);   // discharge / don't arm
                            continue;
                        }
                        if (amount < com.sfb.weapons.Photon.STANDARD_PER_TURN)
                            return ActionResult.fail(w.getName() + ": a photon needs two points of warp"
                                    + " energy to continue arming (E4.21)");
                        double perTurnMax = com.sfb.weapons.Photon.STANDARD_PER_TURN
                                + com.sfb.weapons.Photon.MAX_OVERLOAD;
                        if (amount > perTurnMax)
                            return ActionResult.fail(w.getName() + ": a photon takes at most "
                                    + (int) perTurnMax + " points in a turn — two standard plus four"
                                    + " of overload (E4.41)");
                        e.getArmingEnergy().put(w, amount);
                        // A proximity fuse is recorded with the arming, costs nothing, and
                        // cannot be combined with overload energy (E4.31/E4.34).
                        boolean prox = "PROX".equalsIgnoreCase(choice)
                                && amount == com.sfb.weapons.Photon.STANDARD_PER_TURN;
                        if ("PROX".equalsIgnoreCase(choice) && !prox)
                            return ActionResult.fail(w.getName()
                                    + ": a proximity torpedo cannot be overloaded (E4.34)");
                        e.getArmingType().put(w, prox ? WeaponArmingType.SPECIAL
                                : amount > com.sfb.weapons.Photon.STANDARD_PER_TURN
                                ? WeaponArmingType.OVERLOAD : WeaponArmingType.STANDARD);
                        continue;
                    }

                    if (choice == null)
                        choice = "STANDARD";
                    switch (choice.toUpperCase()) {
                        case "HOLD":
                            e.getArmingEnergy().put(w, (double) hw.holdEnergyCost());
                            e.getArmingType().put(w, hw.getArmingType()); // preserve current mode
                            break;
                        case "PROX":
                            e.getArmingEnergy().put(w, hw.isArmed()
                                    ? (double) hw.holdEnergyCost()
                                    : (double) hw.energyToArm());
                            e.getArmingType().put(w, WeaponArmingType.SPECIAL);
                            break;
                        case "HOLD_PROX":
                            e.getArmingEnergy().put(w, (double) hw.holdEnergyCost());
                            e.getArmingType().put(w, WeaponArmingType.SPECIAL);
                            break;
                        case "HOLD_STD":
                            e.getArmingEnergy().put(w, (double) hw.holdEnergyCost());
                            e.getArmingType().put(w, WeaponArmingType.STANDARD);
                            break;
                        case "OVERLOAD":
                            // Use the OVERLOAD per-turn cost regardless of current armingType.
                            // If already in OVERLOAD mode, energyToArm() returns the overload rate.
                            // If still in STANDARD mode (e.g. WS-2 first game turn), multiply by 2.
                            double ovlEnergy = hw.getArmingType() == com.sfb.properties.WeaponArmingType.OVERLOAD
                                    ? (double) hw.energyToArm()
                                    : (double) hw.energyToArm() * 2;
                            e.getArmingEnergy().put(w, ovlEnergy);
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

                // Warp Tactical Maneuvers (C5.22) — each costs one hex worth of warp energy
                int warpTacs = Math.max(0, Math.min(4, request.getWarpTacticalTurns()));
                if (warpTacs > 0) {
                    if (warpSpeed > 0)
                        return ActionResult
                                .fail("Cannot allocate warp Tactical Maneuvers when moving (speed must be 0)");
                    double tacEnergy = warpTacs * moveCost;
                    if (movementEnergyNeeded + hetEnergy + tacEnergy > warpEngineCapacity + 0.001)
                        return ActionResult.fail("Insufficient warp power for " + warpTacs
                                + " Tactical Maneuver(s) — need " + tacEnergy + ", have "
                                + (warpEngineCapacity - movementEnergyNeeded - hetEnergy));
                    e.setWarpTacticalTurns(warpTacs);
                }

                // Sublight Tactical Maneuver (C5.12) — costs 1 impulse engine point
                if (request.isSublightTacticalTurn()) {
                    if (warpSpeed > 0 || (requestedSpeed > 30))
                        return ActionResult.fail("Cannot allocate sublight Tactical Maneuver when moving");
                    if (ship.getPowerSystems().getAvailableImpulse() < 1)
                        return ActionResult.fail("No impulse engine power available for sublight Tactical Maneuver");
                    e.setImpulseTacticalTurn(1);
                }

                // Shield reinforcement
                e.setGeneralReinforcement(Math.max(0, request.getGeneralReinforcement()));
                int[] specReinf = request.getSpecificReinforcement();
                if (specReinf != null && specReinf.length == 6) {
                    e.setSpecificReinforcement(specReinf);
                }

                // Drone rack reloads — up to 2 rack spaces per rack per turn (FD2.421).
                // Uses assigned crew units, NOT deck crews (FD2.421, FD7.25).
                Map<String, Map<String, Integer>> reloadSelections = request.getDroneReloadSelections();
                if (reloadSelections != null && !reloadSelections.isEmpty()) {
                    for (Map.Entry<String, Map<String, Integer>> entry : reloadSelections.entrySet()) {
                        String rackName = entry.getKey();
                        Map<String, Integer> typeCountMap = entry.getValue();
                        if (typeCountMap == null || typeCountMap.isEmpty())
                            continue;

                        DroneRack rack = (DroneRack) ship.getWeapons().fetchAllWeapons().stream()
                                .filter(w -> w instanceof DroneRack && w.getName().equalsIgnoreCase(rackName))
                                .findFirst().orElse(null);
                        if (rack == null || !rack.isFunctional())
                            continue;

                        // Pass 1: collect candidate Drone objects by reference (without removing yet)
                        List<Drone> candidates = new ArrayList<>();
                        for (Map.Entry<String, Integer> tc : typeCountMap.entrySet()) {
                            String droneType = tc.getKey();
                            int needed = tc.getValue() != null ? tc.getValue() : 0;
                            outer: for (List<Drone> set : rack.getReloads()) {
                                for (Drone d : set) {
                                    if (needed <= 0)
                                        break outer;
                                    if (d.getDroneType() != null
                                            && d.getDroneType().toString().equals(droneType)
                                            && !candidates.contains(d)) {
                                        candidates.add(d);
                                        needed--;
                                    }
                                }
                            }
                        }

                        if (candidates.isEmpty())
                            continue;
                        // Enforce max 2 rack spaces per rack per turn (FD2.421)
                        if (DroneRack.reloadCost(candidates) > 2.0)
                            continue;

                        // Pass 2: remove the chosen drones from their sets, then stage
                        for (Drone d : candidates) {
                            for (List<Drone> set : rack.getReloads()) {
                                if (set.remove(d))
                                    break;
                            }
                        }
                        rack.stagePendingReload(candidates);
                    }
                }

                // Scatter pack loading — uses deck crews (FD7.22): 1 crew per rack space from
                // reload stockpile.
                Map<String, Map<String, Integer>> spLoading = request.getScatterPackLoading();
                if (spLoading != null && !spLoading.isEmpty()) {
                    double deckCrewsLeft = ship.getCrew().getAvailableDeckCrews();
                    for (Map.Entry<String, Map<String, Integer>> spEntry : spLoading.entrySet()) {
                        String shuttleName = spEntry.getKey();
                        Map<String, Integer> typeCountMap = spEntry.getValue();
                        if (typeCountMap == null || typeCountMap.isEmpty())
                            continue;

                        // Find shuttle in ship's bays
                        com.sfb.systemgroups.ShuttleBay foundBay = null;
                        com.sfb.objects.shuttles.Shuttle foundShuttle = null;
                        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
                            for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                                if (s.getName().equalsIgnoreCase(shuttleName)) {
                                    foundBay = bay;
                                    foundShuttle = s;
                                    break;
                                }
                            }
                            if (foundBay != null)
                                break;
                        }
                        if (foundBay == null)
                            continue;
                        if (!(foundShuttle instanceof com.sfb.objects.shuttles.ScatterPack)
                                && !foundShuttle.canBecomeScatterPack())
                            continue;

                        // Convert admin → ScatterPack if needed
                        com.sfb.objects.shuttles.ScatterPack pack;
                        if (foundShuttle instanceof com.sfb.objects.shuttles.ScatterPack) {
                            pack = (com.sfb.objects.shuttles.ScatterPack) foundShuttle;
                        } else {
                            pack = new com.sfb.objects.shuttles.ScatterPack(foundShuttle);
                            foundBay.replaceShuttle(foundShuttle, pack);
                        }

                        // Collect requested drones from reload stockpile across all racks
                        List<DroneRack> allRacks = ship.getWeapons().fetchAllWeapons().stream()
                                .filter(w -> w instanceof DroneRack)
                                .map(w -> (DroneRack) w)
                                .collect(java.util.stream.Collectors.toList());

                        for (Map.Entry<String, Integer> tc : typeCountMap.entrySet()) {
                            com.sfb.objects.DroneType dt;
                            try {
                                dt = com.sfb.objects.DroneType.valueOf(tc.getKey());
                            } catch (IllegalArgumentException ex) {
                                continue;
                            }
                            int needed = tc.getValue() != null ? tc.getValue() : 0;
                            for (int i = 0; i < needed; i++) {
                                if (deckCrewsLeft < dt.rack)
                                    break; // not enough crew for this drone
                                // The pack's own capacity, not a hardcoded six: FD7.21 gives
                                // an admin shuttle six spaces and says other types carry more
                                // or fewer, and the catalogue has been the authority since
                                // role eligibility became data. An MRS at eight would have
                                // silently loaded six here.
                                if (pack.getPayloadSpaces() + pack.getPendingSpaces() + dt.rack
                                        > pack.getMaxDroneSpaces())
                                    break;
                                // Pull from reload stockpile (any rack's reload sets)
                                boolean pulled = false;
                                spOuter: for (DroneRack rack : allRacks) {
                                    for (List<Drone> set : rack.getReloads()) {
                                        for (java.util.Iterator<Drone> it = set.iterator(); it.hasNext();) {
                                            Drone d = it.next();
                                            if (d.getDroneType() == dt) {
                                                it.remove();
                                                pack.addPendingDrone(d);
                                                deckCrewsLeft -= dt.rack;
                                                pulled = true;
                                                break spOuter;
                                            }
                                        }
                                    }
                                }
                                if (!pulled)
                                    break; // no more of this type available
                            }
                        }
                    }
                }

                // Suicide shuttle arming — 1–3 energy per turn for 3 turns (energy from power
                // budget)
                Map<String, Integer> ssArming = request.getSuicideShuttleArming();
                if (ssArming != null && !ssArming.isEmpty()) {
                    for (Map.Entry<String, Integer> entry : ssArming.entrySet()) {
                        String shuttleName = entry.getKey();
                        int energy = entry.getValue() != null ? entry.getValue() : 0;
                        if (energy < 1 || energy > 3)
                            continue;
                        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
                            for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                                if (!s.getName().equalsIgnoreCase(shuttleName))
                                    continue;
                                com.sfb.objects.shuttles.SuicideShuttle ss;
                                if (s instanceof com.sfb.objects.shuttles.SuicideShuttle) {
                                    ss = (com.sfb.objects.shuttles.SuicideShuttle) s;
                                } else if (s.canBecomeSuicide()) {
                                    ss = new com.sfb.objects.shuttles.SuicideShuttle(s);
                                    bay.replaceShuttle(s, ss);
                                } else {
                                    break;
                                }
                                ss.arm(energy);
                                break;
                            }
                        }
                    }
                }

                // Suicide shuttle hold — 1 energy per turn to keep armed shuttle ready
                java.util.Set<String> ssHold = request.getSuicideShuttleHold();
                if (ssHold != null && !ssHold.isEmpty()) {
                    for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
                        for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                            if (s instanceof com.sfb.objects.shuttles.SuicideShuttle
                                    && ssHold.contains(s.getName())) {
                                ((com.sfb.objects.shuttles.SuicideShuttle) s).payHold();
                            }
                        }
                    }
                }

                // Apply shuttle/fighter speeds for shuttles owned by this ship
                Map<String, Integer> shuttleSpeeds = request.getShuttleSpeeds();
                if (shuttleSpeeds != null && !shuttleSpeeds.isEmpty()) {
                    for (com.sfb.objects.shuttles.Shuttle shuttle : game.getActiveShuttles()) {
                        if (!ship.getName().equals(shuttle.getParentShipName()))
                            continue;
                        Integer reqSpeed = shuttleSpeeds.get(shuttle.getName());
                        if (reqSpeed != null) {
                            int clamped = Math.max(0, Math.min(reqSpeed, shuttle.getMaxSpeed()));
                            shuttle.setCurrentSpeed(clamped);
                            shuttle.setSpeed(clamped);
                        }
                    }
                }

                // ECM/ECCM — validate and assign to the ship's EW circuits.
                // Circuit mode commitments persist across turns (D6.312), so an
                // allocation may be rejected if it needs a still-locked switch.
                int ecmReq = Math.max(0, request.getEcm());
                int eccmReq = Math.max(0, request.getEccm());
                // One circuit per point of sensor rating (D6.312), and never more than six
                // points generated in total (D6.310) — a scout's lending pool is generated
                // separately and is not bound by this (G24.31).
                int ewLimit = com.sfb.systemgroups.EwCircuits.generationLimit(
                        ship.getSpecialFunctions().getSensor());
                if (ecmReq + eccmReq > ewLimit)
                    return ActionResult.fail(
                            "ECM + ECCM (" + (ecmReq + eccmReq) + ") exceeds the " + ewLimit
                                    + "-point generation limit (D6.310/D6.312)");
                String ewErr = ship.allocateEw(ecmReq, eccmReq, game.getAbsoluteImpulse() + 1);
                if (ewErr != null)
                    return ActionResult.fail(ewErr);

                // Tractor energy pool (G7.15) — any amount may be pooled; a single
                // range-3 grab costs 3 energy per effective point (G7.6) and auction
                // bids stack beyond that, so there is no per-beam energy cap.
                int tractorReq = Math.max(0, request.getTractorEnergy());
                if (tractorReq > 0) {
                    int beams = ship.getTractors().getAvailableTractors();
                    if (beams == 0)
                        return ActionResult.fail(ship.getName() + " has no functional tractor beams");
                }
                e.setTractors(tractorReq);
                ship.getTractors().initForTurn(tractorReq, game.getAbsoluteImpulse());

                // Wild Weasel charging (J3.12): increment charge for named shuttles, reset
                // others
                java.util.Set<String> wwCharge = request.getWwCharge();
                for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
                    for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                        // J3.18: charge whatever may serve, not only admin shuttles.
                        if (!s.canBecomeWildWeasel())
                            continue;
                        if (wwCharge != null && wwCharge.contains(s.getName()))
                            s.incrementWwCharge();
                        else
                            s.resetWwCharge();
                    }
                }

                // Erratic Maneuvers (C10.11/C10.12): six hexes' worth of this ship's
                // movement cost, or three if it is nimble. Paying buys only the right to
                // announce EM later in the turn (C10.3) — the energy is spent either way,
                // so an amount short of the full cost buys nothing and is refused rather
                // than silently wasted.
                double emReq = Math.max(0, request.getErraticManeuvers());
                if (emReq > 0) {
                    double emCost = ship.getPerformanceData().getErraticCost();
                    if (emCost <= 0)
                        return ActionResult.fail(ship.getName()
                                + " cannot use Erratic Maneuvers");
                    if (emReq < emCost)
                        return ActionResult.fail("Erratic Maneuvers cost "
                                + (int) emCost + " for " + ship.getName()
                                + " — six hexes of movement, three if nimble (C10.11/C10.12)");
                    e.setErraticManuvers(emCost);
                }

                ActionResult allocResult = game.submitAllocation(ship, e);
                // If this was the last allocation, beginImpulses() ran lock-on rolls — drain
                // them
                for (String entry : game.drainLastLockOnLog())
                    appendCombatLog(entry);
                return allocResult;
            }

            // There is no FIRE or FIRE_AT_HEX action. Weapons fire ONLY through the
            // sealed declaration (D6.315) — see COMMIT_FIRE_DECLARATION. An action
            // that resolved a shot on its own would let a player see the board, shoot, and
            // watch the result before anyone else had decided anything.

            case "ANNOUNCE_EM": {
                // C10.3: announce that EM starts or stops. It comes into force at the END
                // of this impulse (C10.311), not now. Works for a ship or a shuttle —
                // Game.announceErraticManeuvers takes a Unit.
                final String emName = request.getShipName();
                com.sfb.objects.Unit emUnit = findShip(emName);
                if (emUnit == null)
                    emUnit = game.getActiveShuttles().stream()
                            .filter(s -> s.getName().equalsIgnoreCase(emName))
                            .findFirst().orElse(null);
                if (emUnit == null)
                    return ActionResult.fail("Unit not found: " + request.getShipName());
                ActionResult emResult = game.announceErraticManeuvers(emUnit, request.isEmOn());
                if (emResult.isSuccess())
                    appendCombatLog(emResult.getMessage());
                return emResult;
            }

            case "COMMIT_SHUTTLE_EM": {
                // C10.13/C10.131: a shuttle buys EM with a point of speed, committed for
                // the whole turn and not recoverable by cancelling EM.
                final String emShuttleName = request.getShipName();
                com.sfb.objects.shuttles.Shuttle emShuttle = game.getActiveShuttles().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(emShuttleName))
                        .findFirst().orElse(null);
                if (emShuttle == null)
                    return ActionResult.fail("Shuttle not found: " + request.getShipName());
                ActionResult commitResult = game.commitShuttleEmSpeed(emShuttle);
                if (commitResult.isSuccess())
                    appendCombatLog(commitResult.getMessage());
                return commitResult;
            }

            case "SUBMIT_REINFORCEMENT": {
                java.util.List<ActionRequest.ReinforcementEntry> entries = request.getReinforcements();
                if (entries == null || entries.isEmpty())
                    return ActionResult.ok("No reinforcement submitted");
                StringBuilder reinLog = new StringBuilder();
                for (ActionRequest.ReinforcementEntry entry : entries) {
                    Ship ship = findShip(entry.getShipName());
                    if (ship == null)
                        return ActionResult.fail("Ship not found: " + entry.getShipName());
                    ActionResult r = game.submitReinforcement(ship, entry.getShieldNumber(), entry.getPower());
                    if (!r.isSuccess())
                        return r;
                    reinLog.append(r.getMessage()).append("\n");
                }
                return ActionResult.ok(reinLog.toString().trim());
            }

            case "SUBMIT_DAC_CHOICE": {
                String chosen = request.getAction();
                if (chosen == null || chosen.isBlank())
                    return ActionResult.fail("No system chosen");
                ActionResult r = game.submitDacChoice(chosen);
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "SUBMIT_BLIND_CHOICE": {
                // Firing player picks which powered scout channel takes a blind (G24.131).
                String channel = request.getChannelDesignator();
                if (channel == null || channel.isBlank())
                    return ActionResult.fail("No channel chosen");
                ActionResult r = game.submitBlindChoice(channel);
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "SUBMIT_CONTROL_OVERFLOW": {
                String seekerName = request.getTargetName();
                String toShipName = request.getShipName(); // null/blank = release
                if (seekerName == null || seekerName.isBlank())
                    return ActionResult.fail("No seeker specified");
                ActionResult r = game.submitControlOverflowChoice(seekerName, toShipName);
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "SUBMIT_ATTRACT_CHOICE": {
                // The shuttle's owner answers a scout's attraction attempt (G24.235).
                if (request.getAttracted() == null)
                    return ActionResult.fail("attracted must be true or false");
                ActionResult r = game.submitAttractChoice(request.getAttracted());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "RELEASE_DRONE_CONTROL": {
                // Voluntarily cut a seeker loose (F3.4) — the answer to a scout that has
                // attracted one of your drones (G24.23).
                ActionResult r = game.releaseSeekerControl(request.getTargetName(), request.getShipName());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "TRANSFER_DRONE_CONTROL": {
                String droneName = request.getTargetName();
                String toShipName = request.getShipName();
                if (droneName == null || droneName.isBlank())
                    return ActionResult.fail("No drone specified");
                if (toShipName == null || toShipName.isBlank())
                    return ActionResult.fail("No target ship specified");
                ActionResult r = game.transferSeekerControl(droneName, toShipName);
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "ESTABLISH_TRACTOR": {
                Ship holder = findShip(request.getShipName());
                if (holder == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.establishTractor(holder, request.getTargetName(), request.getTractorBid());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "NEGATIVE_TRACTOR_BID": {
                Ship defender = findShip(request.getShipName());
                if (defender == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.submitNegativeTractorBid(defender, request.getTractorBid());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "RELEASE_TRACTOR": {
                Ship holder = findShip(request.getShipName());
                if (holder == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.releaseTractor(holder, request.getTargetName());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "ROTATE_TRACTORED": {
                Ship holder = findShip(request.getShipName());
                if (holder == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                if (request.getHexCol() < 1 || request.getHexRow() < 1)
                    return ActionResult.fail("No destination hex specified");
                ActionResult r = game.rotateTractored(holder, request.getTargetName(),
                        request.getHexCol(), request.getHexRow());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "LAUNCH_WILD_WEASEL": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String shuttleName = request.getAction();
                int facing = request.getRange(); // reuse range field for facing (same as LAUNCH_SHUTTLE)
                int speed = request.getSpeed();
                ActionResult wwRes = game.launchWildWeasel(ship, shuttleName, facing, speed);
                if (wwRes.isSuccess())
                    appendCombatLog(ship.getName()
                            + " launched a Wild Weasel"); // weasels are public at launch (user ruling)
                return wwRes;
            }

            case "ASSIGN_GUARD": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String targetCode = request.getAction(); // guard target code in action field
                if (targetCode == null || targetCode.isBlank())
                    return ActionResult.fail("No guard target specified");
                // Deliberately NOT appended to the combat log: guard posts are
                // secret (the raider learns of one by walking into it, D7.831).
                // The acting player sees the result in the action response.
                return game.assignGuard(ship, targetCode, request.isCommando());
            }

            case "REMOVE_GUARD": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String targetCode = request.getAction();
                if (targetCode == null || targetCode.isBlank())
                    return ActionResult.fail("No guard target specified");
                return game.removeGuard(ship, targetCode); // secret — not logged
            }

            case "BEGIN_RECOVERY": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String recName = request.getAction(); // shuttle name in action field
                if (recName == null || recName.isBlank())
                    return ActionResult.fail("No shuttle specified");
                ActionResult r = game.beginShuttleRecovery(ship, recName);
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "LAND_SHUTTLE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String landName = request.getAction(); // shuttle name in action field
                if (landName == null || landName.isBlank())
                    return ActionResult.fail("No shuttle specified");
                ActionResult r = game.landShuttle(ship, landName);
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "LAUNCH_SHUTTLE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                String shuttleName = request.getAction(); // shuttle name passed in action field
                int speed = request.getSpeed();
                int facing = request.getRange(); // reuse range field for facing
                // Find the shuttle in any bay
                ShuttleBay foundBay = null;
                Shuttle foundShuttle = null;
                for (ShuttleBay bay : ship.getShuttles().getBays()) {
                    for (Shuttle s : bay.getInventory()) {
                        if (s.getName().equalsIgnoreCase(shuttleName)) {
                            foundBay = bay;
                            foundShuttle = s;
                            break;
                        }
                    }
                    if (foundBay != null)
                        break;
                }
                if (foundBay == null || foundShuttle == null)
                    return ActionResult.fail("Shuttle not found: " + shuttleName);
                ActionResult launchRes = game.launchShuttle(ship, foundBay, foundShuttle, speed, facing);
                // Public announcement, redacted: the launch is visible to all,
                // the TYPE is not (detail stays in the actor's private response)
                if (launchRes.isSuccess())
                    appendCombatLog(ship.getName() + " launched a "
                            + (foundShuttle instanceof com.sfb.objects.shuttles.Fighter ? "fighter" : "shuttle"));
                return launchRes;
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
                com.sfb.objects.shuttles.ScatterPack foundPack = null;
                for (ShuttleBay bay : launcher.getShuttles().getBays()) {
                    for (Shuttle s : bay.getInventory()) {
                        if (s.getName().equalsIgnoreCase(packName)
                                && s instanceof com.sfb.objects.shuttles.ScatterPack) {
                            foundBay = bay;
                            foundPack = (com.sfb.objects.shuttles.ScatterPack) s;
                            break;
                        }
                    }
                    if (foundBay != null)
                        break;
                }
                if (foundBay == null || foundPack == null)
                    return ActionResult.fail("Scatter pack not found: " + packName);
                ActionResult spRes = game.launchScatterPack(launcher, foundBay, foundPack, target,
                        request.getFacing(), request.getSpeed());
                if (spRes.isSuccess())
                    appendCombatLog(launcher.getName() + " launched a shuttle"); // type is secret
                return spRes;
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
                com.sfb.objects.shuttles.SuicideShuttle foundShuttle = null;
                for (ShuttleBay bay : launcher.getShuttles().getBays()) {
                    for (Shuttle s : bay.getInventory()) {
                        if (s.getName().equalsIgnoreCase(shuttleName)
                                && s instanceof com.sfb.objects.shuttles.SuicideShuttle) {
                            foundBay = bay;
                            foundShuttle = (com.sfb.objects.shuttles.SuicideShuttle) s;
                            break;
                        }
                    }
                    if (foundBay != null)
                        break;
                }
                if (foundBay == null || foundShuttle == null)
                    return ActionResult.fail("Armed suicide shuttle not found: " + shuttleName);
                ActionResult ssRes = game.launchSuicideShuttle(launcher, foundBay, foundShuttle, target,
                        request.getFacing(), request.getSpeed());
                if (ssRes.isSuccess())
                    appendCombatLog(launcher.getName() + " launched a shuttle"); // type is secret
                return ssRes;
            }

            case "PERFORM_FIGHTER_HET": {
                String shuttleName = request.getShipName();
                com.sfb.objects.shuttles.Shuttle shuttle = game.getActiveShuttles().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(shuttleName))
                        .findFirst().orElse(null);
                if (shuttle == null)
                    return ActionResult.fail("Active shuttle not found: " + shuttleName);
                return game.performFighterHet(shuttle, request.getFacing());
            }

            case "DROP_CHAFF": {
                String shuttleName = request.getShipName();
                com.sfb.objects.shuttles.Shuttle shuttle = game.getActiveShuttles().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(shuttleName))
                        .findFirst().orElse(null);
                if (shuttle == null)
                    return ActionResult.fail("Active shuttle not found: " + shuttleName);
                return game.dropChaff(shuttle);
            }

            case "MOVE_SHUTTLE": {
                String shuttleName = request.getShipName(); // shuttle name in shipName field
                com.sfb.objects.shuttles.Shuttle shuttle = game.getActiveShuttles().stream()
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

            case "LOAD_PERSONNEL":
            case "UNLOAD_PERSONNEL": {
                String shuttleName = request.getShipName();
                com.sfb.objects.shuttles.Shuttle shuttle = game.getActiveShuttles().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(shuttleName))
                        .findFirst().orElse(null);
                if (shuttle == null)
                    return ActionResult.fail("Active shuttle not found: " + shuttleName);
                com.sfb.properties.PersonnelType type;
                try {
                    type = request.getAction() != null && !request.getAction().isBlank()
                            ? com.sfb.properties.PersonnelType.valueOf(request.getAction().toUpperCase())
                            : com.sfb.properties.PersonnelType.CREW_UNIT;
                } catch (IllegalArgumentException e) {
                    return ActionResult.fail("Unknown personnel type: " + request.getAction());
                }
                ActionResult r = request.getType().equals("LOAD_PERSONNEL")
                        ? game.loadPersonnelFromPlanet(shuttle, type, Integer.MAX_VALUE)
                        : game.unloadPersonnelToPlanet(shuttle, type, Integer.MAX_VALUE);
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "LAUNCH_DRONE": {
                Ship attacker = findShip(request.getShipName());
                if (attacker == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                Unit target = findUnit(request.getTargetName());
                if (target == null)
                    return ActionResult.fail("Target not found: " + request.getTargetName());
                String rackName = request.getWeaponNames() != null && !request.getWeaponNames().isEmpty()
                        ? request.getWeaponNames().get(0)
                        : null;
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
                ActionResult droneRes = game.execute(
                        new LaunchDroneCommand(attacker, target, rack, drone, request.getFacing()));
                if (droneRes.isSuccess())
                    appendCombatLog(attacker.getName() + " launched a drone"); // type/target are secret
                return droneRes;
            }

            case "LAUNCH_PLASMA": {
                Ship attacker = findShip(request.getShipName());
                if (attacker == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                Unit target = findUnit(request.getTargetName());
                if (target == null)
                    return ActionResult.fail("Target not found: " + request.getTargetName());
                String wName = request.getWeaponNames() != null && !request.getWeaponNames().isEmpty()
                        ? request.getWeaponNames().get(0)
                        : null;
                if (wName == null)
                    return ActionResult.fail("No launcher specified");
                PlasmaLauncher launcher = attacker.getWeapons().fetchAllWeapons().stream()
                        .filter(w -> w instanceof PlasmaLauncher && w.getName().equalsIgnoreCase(wName))
                        .map(w -> (PlasmaLauncher) w)
                        .findFirst().orElse(null);
                if (launcher == null)
                    return ActionResult.fail("Plasma launcher not found: " + wName);
                ActionResult plasmaRes = game.execute(
                        new LaunchPlasmaCommand(attacker, target, launcher, request.isPseudo(), request.isFastLoad(),
                                request.getFacing()));
                if (plasmaRes.isSuccess())
                    appendCombatLog(attacker.getName()
                            + " launched a plasma torpedo"); // type/pseudo/target are secret
                return plasmaRes;
            }

            case "PLACE_TBOMB": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                if (request.getHexCol() < 1 || request.getHexRow() < 1)
                    return ActionResult.fail("No destination hex specified");
                com.sfb.properties.Location loc = new com.sfb.properties.Location(request.getHexCol(),
                        request.getHexRow());
                boolean isReal = !request.isPseudo();
                return game.placeTBomb(ship, loc, isReal, request.getShieldNumber());
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

                // Resolve each "WEAPON:name", "TRACTOR:n", or type-name code
                List<com.sfb.properties.SystemTarget> targetSystems = new ArrayList<>();
                for (String code : systemCodes) {
                    com.sfb.properties.SystemTarget st = game.parseRaidTargetCode(targetShip, code);
                    if (st == null)
                        return ActionResult.fail("Unknown target system: " + code);
                    targetSystems.add(st);
                }
                ActionResult harResult = game
                        .execute(new com.sfb.commands.HitAndRunCommand(actingShip, targetShip, targetSystems));
                if (harResult.isSuccess())
                    appendCombatLog(harResult.getMessage());
                return harResult;
            }

            case "BOARDING_ACTION": {
                Ship actingShip = findShip(request.getShipName());
                if (actingShip == null)
                    return ActionResult.fail("Acting ship not found: " + request.getShipName());
                Ship targetShip = findShip(request.getTargetName());
                if (targetShip == null)
                    return ActionResult.fail("Target ship not found: " + request.getTargetName());
                ActionResult boardResult = game.execute(new com.sfb.commands.BoardingActionCommand(
                        actingShip, targetShip,
                        request.getNormalParties(),
                        request.getCommandoParties()));
                if (boardResult.isSuccess())
                    appendCombatLog(boardResult.getMessage());
                return boardResult;
            }

            case "TRANSPORT_CREW": {
                Ship source = findShip(request.getShipName());
                if (source == null)
                    return ActionResult.fail("Source ship not found: " + request.getShipName());
                com.sfb.objects.Unit dest = findUnit(request.getTargetName());
                if (dest == null)
                    return ActionResult.fail("Destination not found: " + request.getTargetName());
                return game.transportCrew(source, dest, request.getCrewAmount());
            }

            case "IDENTIFY_SEEKERS": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.identifySeekers(ship, request.getSeekerNames());
            }

            case "EMERGENCY_DECEL": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.emergencyDeceleration(ship);
            }

            case "FC_GO_PASSIVE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.goPassiveFireControl(ship);
            }

            case "FC_GO_ACTIVE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.beginActivatingFireControl(ship);
            }

            case "CLOAK": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.execute(new CloakCommand(ship));
            }

            case "UNCLOAK": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                return game.execute(new UncloakCommand(ship));
            }

            case "PICKUP_OBJECTIVE": {
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                com.sfb.properties.RetrievalMethod method;
                try {
                    method = com.sfb.properties.RetrievalMethod.valueOf(
                            request.getRetrievalMethod() != null
                                    ? request.getRetrievalMethod().toUpperCase()
                                    : "TRANSPORTER");
                } catch (IllegalArgumentException e) {
                    return ActionResult.fail("Unknown retrieval method: " + request.getRetrievalMethod());
                }
                ActionResult r = game.pickUpObjective(ship, request.getObjectiveName(), method);
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "ANNOUNCE_ESG": {
                // Announce a release; the field forms 4 impulses later (G23.31).
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.announceEsg(ship, request.getEsgDesignator(),
                        request.getEsgRadius(), request.getEsgReleaseAmount());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "LEND_EW": {
                // Aim one scout channel's EW lend for the turn (G24.21); 0/0 clears it.
                Ship scout = findShip(request.getShipName());
                if (scout == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.assignChannelLend(scout, request.getChannelDesignator(),
                        request.getLendTarget(), request.getLendEcm(), request.getLendEccm());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "BREAK_LOCKON": {
                // Attempt to break an enemy drone's lock-on with a scout channel (G24.22).
                Ship scout = findShip(request.getShipName());
                if (scout == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.breakDroneLockOn(scout, request.getChannelDesignator(),
                        request.getTargetName());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "IDENTIFY_SEEKER": {
                // Attempt to identify an enemy seeker with a scout channel + lab (G24.25).
                Ship scout = findShip(request.getShipName());
                if (scout == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.identifySeeker(scout, request.getChannelDesignator(),
                        request.getTargetName());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "OFFENSIVE_EW": {
                // Commit a scout channel to offensive EW against an enemy (G24.219); reuses
                // lendEcm as the point count. 0 clears it.
                Ship scout = findShip(request.getShipName());
                if (scout == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.assignOffensiveEw(scout, request.getChannelDesignator(),
                        request.getTargetName(), request.getLendEcm());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "ATTRACT_DRONE": {
                // Draw an enemy drone onto the scout with a scout channel (G24.23).
                Ship scout = findShip(request.getShipName());
                if (scout == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.attractDrone(scout, request.getChannelDesignator(),
                        request.getTargetName());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "CONTROL_SEEKERS": {
                // Commit a scout channel to controlling seekers (+6 capacity, G24.24).
                Ship scout = findShip(request.getShipName());
                if (scout == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.assignControlSeekers(scout, request.getChannelDesignator());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "CANCEL_ESG": {
                // Publicly cancel a pending ESG announcement before it forms (G23.33).
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.cancelEsgAnnouncement(ship, request.getEsgDesignator());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "DEACTIVATE_ESG": {
                // Voluntarily drop an active ESG field (G23.47).
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.deactivateEsg(ship, request.getEsgDesignator());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "RECOVER_OBJECTIVE": {
                // SH35.452 J1.621: draw a tractored canister aboard over impulses.
                // (Tractor it first with ESTABLISH_TRACTOR using the canister name.)
                Ship ship = findShip(request.getShipName());
                if (ship == null)
                    return ActionResult.fail("Ship not found: " + request.getShipName());
                ActionResult r = game.beginObjectiveRecovery(ship, request.getObjectiveName());
                if (r.isSuccess())
                    appendCombatLog(r.getMessage());
                return r;
            }

            case "CALL_FIRE_DECLARATION": {
                if (game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE)
                    return ActionResult.fail("Fire declarations happen during the Direct Fire phase");
                refreshDeclarationState();
                if (declarationOpen)
                    return ActionResult.fail("A fire declaration is already open");
                if (declarationSpent)
                    return ActionResult.fail("This impulse's fire declaration has already resolved (one per impulse)");
                PlayerInfo caller = players.get(request.getPlayerToken());
                if (caller == null)
                    return ActionResult.fail("Unknown player");
                declarationOpen = true;
                declarationCallerToken = request.getPlayerToken();
                declarationImpulse = game.getAbsoluteImpulse();
                declarationCommits.clear();
                // Stale readies can't stand as an answer to the call (D6.315)
                readyPlayers.clear();
                appendCombatLog("⚔ " + caller.getName()
                        + " calls a fire declaration — all players commit orders (D6.315)");
                return ActionResult.ok("FIRE_DECLARATION_CALLED");
            }

            case "COMMIT_FIRE_DECLARATION": {
                refreshDeclarationState();
                if (!declarationOpen)
                    return ActionResult.fail("No fire declaration is open");
                String token = request.getPlayerToken();
                PlayerInfo pi = players.get(token);
                if (pi == null)
                    return ActionResult.fail("Unknown player");
                if (declarationCommits.containsKey(token))
                    return ActionResult.fail("Orders already committed — they are sealed");
                // Ownership check now; full rules validation happens at the reveal
                List<String> myNames = pi.getShipNames();
                if (request.getFireOrders() != null)
                    for (ActionRequest.FireOrder o : request.getFireOrders())
                        if (!containsIgnoreCase(myNames, o.getShipName()))
                            return ActionResult.fail("Cannot fire another player's unit: " + o.getShipName());
                if (request.getEwAdjustments() != null)
                    for (ActionRequest.EwAdjustment a : request.getEwAdjustments())
                        if (!containsIgnoreCase(myNames, a.getShipName()))
                            return ActionResult.fail("Cannot adjust another player's EW: " + a.getShipName());
                declarationCommits.put(token,
                        new DeclarationCommit(request.getFireOrders(), request.getEwAdjustments()));
                appendCombatLog(pi.getName() + " has committed orders ("
                        + declarationCommits.size() + "/" + players.size() + ")");
                if (declarationCommits.size() >= players.size())
                    resolveDeclarationRound();
                return ActionResult.ok("COMMITTED:" + declarationCommits.size() + "/" + players.size());
            }

            case "CALL_ACTIVITY_DECLARATION": {
                if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
                    return ActionResult.fail("Launches are declared in the Activity segment");
                refreshActivityState();
                if (activityOpen)
                    return ActionResult.fail("A launch declaration is already open");
                if (activitySpent)
                    return ActionResult.fail("This impulse's launch declaration has already resolved");
                PlayerInfo caller = players.get(request.getPlayerToken());
                if (caller == null)
                    return ActionResult.fail("Unknown player");
                activityOpen = true;
                activityCallerToken = request.getPlayerToken();
                activityImpulse = game.getAbsoluteImpulse();
                activityCommits.clear();
                readyPlayers.clear();
                appendCombatLog("\u2622 " + caller.getName()
                        + " calls for launches \u2014 all players commit orders (Annex #2, 6B)");
                return ActionResult.ok("ACTIVITY_DECLARATION_CALLED");
            }

            case "COMMIT_ACTIVITY_DECLARATION": {
                refreshActivityState();
                if (!activityOpen)
                    return ActionResult.fail("No launch declaration is open");
                String aToken = request.getPlayerToken();
                PlayerInfo api = players.get(aToken);
                if (api == null)
                    return ActionResult.fail("Unknown player");
                if (activityCommits.containsKey(aToken))
                    return ActionResult.fail("Orders already committed \u2014 they are sealed");
                List<String> aMine = api.getShipNames();
                if (request.getActivityOrders() != null)
                    for (ActionRequest.ActivityOrder o : request.getActivityOrders())
                        if (!containsIgnoreCase(aMine, o.getShipName()))
                            return ActionResult.fail("Cannot launch from another player's unit: "
                                    + o.getShipName());
                activityCommits.put(aToken, request.getActivityOrders() != null
                        ? request.getActivityOrders() : List.of());
                appendCombatLog(api.getName() + " has committed launch orders ("
                        + activityCommits.size() + "/" + players.size() + ")");
                if (activityCommits.size() >= players.size())
                    resolveActivityRound();
                return ActionResult.ok("COMMITTED:" + activityCommits.size() + "/" + players.size());
            }

            case "PASS_ACTIVITY_DECLARATION": {
                refreshActivityState();
                if (!activityOpen)
                    return ActionResult.fail("No launch declaration is open");
                String pToken = request.getPlayerToken();
                PlayerInfo ppi = players.get(pToken);
                if (ppi == null)
                    return ActionResult.fail("Unknown player");
                if (activityCommits.containsKey(pToken))
                    return ActionResult.fail("Orders already committed \u2014 they are sealed");
                activityCommits.put(pToken, List.of());
                appendCombatLog(ppi.getName() + " has committed launch orders ("
                        + activityCommits.size() + "/" + players.size() + ")");
                if (activityCommits.size() >= players.size())
                    resolveActivityRound();
                return ActionResult.ok("COMMITTED:" + activityCommits.size() + "/" + players.size());
            }

            case "PASS_FIRE_DECLARATION": {
                refreshDeclarationState();
                if (!declarationOpen)
                    return ActionResult.fail("No fire declaration is open");
                String token = request.getPlayerToken();
                PlayerInfo pi = players.get(token);
                if (pi == null)
                    return ActionResult.fail("Unknown player");
                if (declarationCommits.containsKey(token))
                    return ActionResult.fail("Orders already committed — they are sealed");
                declarationCommits.put(token, new DeclarationCommit(null, null));
                appendCombatLog(pi.getName() + " has committed orders ("
                        + declarationCommits.size() + "/" + players.size() + ")");
                if (declarationCommits.size() >= players.size())
                    resolveDeclarationRound();
                return ActionResult.ok("COMMITTED:" + declarationCommits.size() + "/" + players.size());
            }

            default:
                return ActionResult.fail("Unknown action type: " + request.getType());
        }
    }

    // -------------------------------------------------------------------------
    // ---- Launch declaration helpers (Annex #2, Impulse Activity Segment) ----

    /** A stale round from a previous impulse evaporates; spent-flag resets too. */
    private void refreshActivityState() {
        if (activityImpulse != game.getAbsoluteImpulse()) {
            activityOpen = false;
            activityCallerToken = null;
            activitySpent = false;
            activityCommits.clear();
            activityImpulse = game.getAbsoluteImpulse();
        }
    }

    /**
     * Which Annex #2 stage an order belongs to. Seeking weapons are 6B6, shuttles 6B8 - so
     * in one impulse the drones are away before a weasel goes up, which is the whole reason
     * the round exists rather than resolving on click.
     * <p>
     * Only the two stages this round collects. Sequencing all eleven would slow the game to
     * a crawl for no gain; these are where the outcomes differ.
     */
    private static int activityStage(ActionRequest.ActivityOrder o) {
        String kind = o.getKind() == null ? "" : o.getKind().toUpperCase();
        switch (kind) {
            case "PLASMA":
            case "DRONE":
                return 6;                 // 6B6 Seeking Weapons Stage
            case "SHUTTLE":
            case "WEASEL":
            case "SUICIDE":
            case "SCATTER_PACK":
                return 8;                 // 6B8 Shuttle & PF Functions Stage
            default:
                return 9;                 // unknown: last, and it will fail with a reason
        }
    }

    /**
     * Turn a sealed order into the action that already knows how to perform it.
     * <p>
     * The individual launch actions grew up reusing fields - a shuttle's name arrives in
     * `action`, its facing in `range`, a drone's index in `range` as well. ActivityOrder has
     * properly named fields and this method is the single place that maps them onto what each
     * action reads, so the reuse stays contained instead of spreading into the new round.
     */
    private ActionRequest activityOrderToAction(ActionRequest.ActivityOrder o, String token) {
        ActionRequest r = new ActionRequest();
        r.setPlayerToken(token);
        r.setShipName(o.getShipName());
        r.setTargetName(o.getTargetName());
        String kind = o.getKind() == null ? "" : o.getKind().toUpperCase();
        switch (kind) {
            case "PLASMA":
                r.setType("LAUNCH_PLASMA");
                r.setWeaponNames(List.of(o.getWeaponName() == null ? "" : o.getWeaponName()));
                r.setPseudo(o.isPseudo());
                r.setFastLoad(o.isFastLoad());
                r.setFacing(o.getFacing());
                break;
            case "DRONE":
                r.setType("LAUNCH_DRONE");
                r.setWeaponNames(List.of(o.getWeaponName() == null ? "" : o.getWeaponName()));
                r.setRange(o.getDroneIndex());     // LAUNCH_DRONE reads the index from range
                r.setFacing(o.getFacing());
                break;
            case "SHUTTLE":
            case "WEASEL":
                r.setType("WEASEL".equals(kind) ? "LAUNCH_WILD_WEASEL" : "LAUNCH_SHUTTLE");
                r.setAction(o.getShuttleName());   // both read the name from action
                r.setRange(o.getFacing());         // and the facing from range
                r.setSpeed(o.getSpeed());
                break;
            case "SUICIDE":
            case "SCATTER_PACK":
                r.setType("SUICIDE".equals(kind)
                        ? "LAUNCH_SUICIDE_SHUTTLE" : "LAUNCH_SCATTER_PACK");
                r.setAction(o.getShuttleName());
                break;
            default:
                r.setType("UNKNOWN_LAUNCH_KIND");
                break;
        }
        return r;
    }

    /**
     * The reveal: every player has answered, so the collected launches happen.
     *
     * Sorted into Annex #2 stage order and otherwise left in the order they were drafted -
     * List.sort is stable, so a player's own two drones still go in the order they chose.
     * Each order is REPLAYED as the action that already performs it: those six launch cases
     * are correct, and a resolver with its own copies of them would be two of each.
     * <p>
     * An order that turns out to be illegal fizzles with its reason in the log rather than
     * rejecting the sealed round, the same way a fire order does.
     */
    private void resolveActivityRound() {
        StringBuilder log = new StringBuilder("\u2014 Launch declaration resolves \u2014");
        List<Map.Entry<String, ActionRequest.ActivityOrder>> all = new ArrayList<>();
        for (Map.Entry<String, List<ActionRequest.ActivityOrder>> e : activityCommits.entrySet())
            for (ActionRequest.ActivityOrder o : e.getValue())
                all.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), o));
        all.sort(java.util.Comparator.comparingInt(e -> activityStage(e.getValue())));

        // Closed before resolving: a launch can cascade back into executeAction (a weasel
        // release voids tractors, for instance) and must not find the round still open.
        activityOpen = false;
        activitySpent = true;
        activityCommits.clear();

        for (Map.Entry<String, ActionRequest.ActivityOrder> e : all) {
            ActionResult r = doExecuteAction(activityOrderToAction(e.getValue(), e.getKey()));
            log.append("\n").append(r.getMessage());
        }
        appendCombatLog(log.toString());
    }

    // Fire declaration round helpers (D6.315)
    // -------------------------------------------------------------------------

    /** A stale round from a previous impulse evaporates; spent-flag resets too. */
    private void refreshDeclarationState() {
        if (declarationImpulse != game.getAbsoluteImpulse()) {
            declarationOpen = false;
            declarationCallerToken = null;
            declarationSpent = false;
            declarationCommits.clear();
            declarationImpulse = game.getAbsoluteImpulse();
        }
    }

    /**
     * The reveal: every player has responded. EW adjustments apply first —
     * simultaneous with the fire decisions per D6.315, so all declared fire
     * resolves against the post-adjustment EW. Illegal orders fizzle with a
     * logged reason rather than rejecting the sealed round.
     */
    private void resolveDeclarationRound() {
        StringBuilder log = new StringBuilder("— Fire declaration resolves —");
        for (Map.Entry<String, DeclarationCommit> e : declarationCommits.entrySet()) {
            PlayerInfo pi = players.get(e.getKey());
            String who = pi != null ? pi.getName() : "?";
            for (ActionRequest.EwAdjustment adj : e.getValue().ewAdjustments) {
                Ship ship = findShip(adj.getShipName());
                if (ship == null) {
                    log.append("\n").append(who).append(": ship not found — ").append(adj.getShipName());
                    continue;
                }
                ActionResult r = game.adjustEw(ship, adj.getEcm(), adj.getEccm());
                log.append("\n").append(r.getMessage());
            }
        }
        for (Map.Entry<String, DeclarationCommit> e : declarationCommits.entrySet()) {
            for (ActionRequest.FireOrder o : e.getValue().fireOrders) {
                // One list, drafted order preserved: a volley at a unit and a volley into
                // a hex compete for the same weapons, so whichever was ordered first gets
                // them.
                ActionResult r = o.isAtHex()
                        ? resolveHexFire(o.getShipName(), o.getHexCol(), o.getHexRow(),
                                o.getPlanetSide(), o.getWeaponNames())
                        : resolveFire(o.getShipName(), o.getTargetName(),
                                o.getWeaponNames(), o.getShotModes(),
                                o.getRange(), o.getAdjustedRange(), o.getShieldNumber(),
                                o.isUseUim(), o.isDirectFire());
                log.append("\n").append(r.getMessage());
            }
        }
        declarationOpen = false;
        declarationSpent = true;
        declarationCommits.clear();
        appendCombatLog(log.toString());
    }

    /**
     * Fire aimed at a PLACE rather than a unit: clearing a path through asteroids (P3.25)
     * or bombarding a planet's surface (P2.311/P2.525).
     * <p>
     * One method for both, because the client should not have to know which rule the hex
     * it clicked falls under — the server can see what is there. Reached only from
     * the declaration reveal, so a bombardment is sealed and simultaneous like any other
     * volley rather than resolving the moment it is ordered.
     */
    private ActionResult resolveHexFire(String shipName, int hexCol, int hexRow,
            int planetSide, List<String> weaponNames) {
        Ship firer = findShip(shipName);
        if (firer == null)
            return ActionResult.fail("Ship not found: " + shipName);
        if (hexCol < 1 || hexRow < 1)
            return ActionResult.fail("No target hex given");
        com.sfb.properties.Location hex = new com.sfb.properties.Location(hexCol, hexRow);

        List<com.sfb.weapons.Weapon> hexWeapons = new ArrayList<>();
        for (String wName : weaponNames == null ? java.util.List.<String>of() : weaponNames)
            for (com.sfb.weapons.Weapon w : firer.getWeapons().fetchAllWeapons())
                if (w.getName().equals(wName) && !hexWeapons.contains(w)) {
                    hexWeapons.add(w);
                    break;
                }
        if (hexWeapons.isEmpty())
            return ActionResult.fail("No weapons selected");

        com.sfb.objects.Terrain planet = game.planetCovering(hex);
        return planet != null
                ? game.bombardPlanet(firer, planet, planetSide, hexWeapons)
                : game.clearAsteroidPath(firer, hex, hexWeapons);
    }

    /** Shared FIRE resolution — reached only from the declaration reveal. */
    private ActionResult resolveFire(String attackerName, String targetName,
            List<String> weaponNames, Map<String, String> shotModes,
            int range, int adjustedRange, int shieldNumber, boolean useUim, boolean directFire) {
        Unit attacker = findUnit(attackerName);
        if (attacker == null)
            return ActionResult.fail("Attacker not found: " + attackerName);
        Unit target = findUnit(targetName);
        if (target == null)
            return ActionResult.fail("Target not found: " + targetName);
        if (weaponNames == null || weaponNames.isEmpty())
            return ActionResult.fail("No weapons specified");

        com.sfb.systemgroups.Weapons attackerWeapons = attacker instanceof Ship
                ? ((Ship) attacker).getWeapons()
                : ((Shuttle) attacker).getWeapons();

        // Apply FighterFusion shot modes before resolving weapon list
        if (shotModes != null && !shotModes.isEmpty()) {
            for (Weapon w : attackerWeapons.fetchAllWeapons()) {
                if (w instanceof com.sfb.weapons.FighterFusion) {
                    String mode = shotModes.get(w.getName());
                    if ("DOUBLE".equalsIgnoreCase(mode))
                        ((com.sfb.weapons.FighterFusion) w)
                                .setShotMode(com.sfb.weapons.FighterFusion.ShotMode.DOUBLE);
                    else if ("SINGLE".equalsIgnoreCase(mode))
                        ((com.sfb.weapons.FighterFusion) w)
                                .setShotMode(com.sfb.weapons.FighterFusion.ShotMode.SINGLE);
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
            // fetchAllWeapons() includes drone racks, which are Launchers rather than
            // direct-fire weapons. Refuse here so the player gets a reason instead of core
            // quietly dropping it from the volley. A G-rack firing as an ADD (FD3.7) is not
            // built yet; when it is, the rack will be a DirectFire in that mode and this
            // will let it through without changes.
            if (!(w instanceof com.sfb.weapons.DirectFire))
                return ActionResult.fail(w.getName()
                        + " is a launcher, not a direct-fire weapon — launch it from the"
                        + " Activity phase. (A G-rack firing as an ADD, FD3.7, is not"
                        + " implemented yet.)");
            weapons.add(w);
        }

        return game.execute(new FireCommand(attacker, target, weapons,
                range, adjustedRange, shieldNumber, useUim, directFire));
    }

    private static boolean containsIgnoreCase(List<String> names, String name) {
        if (name == null)
            return false;
        for (String n : names)
            if (n.equalsIgnoreCase(name))
                return true;
        return false;
    }

    // Declaration state exposed for the per-player DTO broadcast
    public boolean isFireDeclarationOpen() {
        return declarationOpen && declarationImpulse == game.getAbsoluteImpulse();
    }

    public String getFireDeclarationCallerName() {
        PlayerInfo pi = declarationCallerToken != null ? players.get(declarationCallerToken) : null;
        return pi != null ? pi.getName() : null;
    }

    public List<String> getFireDeclarationRespondedNames() {
        List<String> names = new ArrayList<>();
        for (String token : declarationCommits.keySet()) {
            PlayerInfo pi = players.get(token);
            if (pi != null)
                names.add(pi.getName());
        }
        return names;
    }

    public boolean isActivityDeclarationOpen() {
        refreshActivityState();
        return activityOpen;
    }

    public String getActivityDeclarationCallerName() {
        PlayerInfo info = activityCallerToken != null ? players.get(activityCallerToken) : null;
        return info != null ? info.getName() : null;
    }

    public List<String> getActivityDeclarationRespondedNames() {
        List<String> names = new ArrayList<>();
        for (String token : activityCommits.keySet()) {
            PlayerInfo info = players.get(token);
            if (info != null)
                names.add(info.getName());
        }
        return names;
    }

    public boolean isActivityDeclarationSpent() {
        refreshActivityState();
        return activitySpent;
    }

    public boolean isFireDeclarationSpent() {
        return declarationSpent && declarationImpulse == game.getAbsoluteImpulse();
    }

    private Ship findShip(String name) {
        if (name == null)
            return null;
        return game.getShips().stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    /** Find any Unit (ship, active shuttle, or seeker) by name. */
    private Unit findUnit(String name) {
        if (name == null)
            return null;
        Ship ship = findShip(name);
        if (ship != null)
            return ship;
        Shuttle shuttle = game.getActiveShuttles().stream()
                .filter(s -> name.equalsIgnoreCase(s.getName()))
                .findFirst().orElse(null);
        if (shuttle != null)
            return shuttle;
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
        if (captured.isEmpty())
            return;

        List<PlayerInfo> playerList = new ArrayList<>(players.values());
        if (playerList.size() != 2)
            return; // only handle 2-player for now

        for (com.sfb.objects.Ship ship : captured) {
            com.sfb.Player currentOwner = ship.getOwner();
            PlayerInfo newOwnerInfo = playerList.stream()
                    .filter(pi -> pi.getCorePlayer() != currentOwner)
                    .findFirst().orElse(null);
            if (newOwnerInfo == null || newOwnerInfo.getCorePlayer() == null)
                continue;

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

    /**
     * Returns the ships effectively controlled by a player.
     * In unassigned (solo) mode, every player controls all ships — mirrors
     * ownsShip().
     */
    public List<String> getEffectiveShipNamesForPlayer(String token) {
        boolean anyAssigned = game.getShips().stream()
                .anyMatch(s -> s.getOwner() != null);
        if (!anyAssigned) {
            return game.getShips().stream().map(Ship::getName).toList();
        }
        PlayerInfo info = players.get(token);
        return info != null ? info.getShipNames() : List.of();
    }

    /**
     * The session lock — see the field javadoc. Held by the controller around every
     * endpoint that touches this session.
     */
    public ReentrantLock getLock() {
        return lock;
    }

    public String getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public String getHostToken() {
        return hostToken;
    }

    public Map<String, PlayerInfo> getPlayers() {
        return players;
    }

    /**
     * Team name for the given player token — from live Player if started, else from
     * shipTeamName map.
     */
    public String getTeamNameFor(String token) {
        PlayerInfo info = players.get(token);
        if (info == null)
            return null;
        if (info.getCorePlayer() != null)
            return info.getCorePlayer().getTeamName();
        // Pre-start: derive from the first ship assigned to this player
        return pendingAssignments.entrySet().stream()
                .filter(e -> e.getValue().equals(token))
                .map(e -> shipTeamName.get(e.getKey()))
                .filter(t -> t != null)
                .findFirst().orElse(null);
    }

    public boolean isStarted() {
        return started;
    }
}
