package com.sfb.server;

import com.sfb.Game.ActionResult;
import com.sfb.dto.GameStateDto;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.scenario.ScenarioSpec;
import com.sfb.utilities.MapUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for game session management.
 *
 * GET /api/scenarios — list available scenarios
 * POST /api/games — create a game (host)
 * POST /api/games/{id}/join — join a game
 * POST /api/games/{id}/start — start the game (host only); body: { scenarioId }
 * GET /api/games/{id}/status — current session info
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameSessionService sessionService;
    private final SimpMessagingTemplate broker;

    public GameController(GameSessionService sessionService, SimpMessagingTemplate broker) {
        this.sessionService = sessionService;
        this.broker = broker;
    }

    /**
     * Run a request body while holding the session's lock. Every endpoint that
     * reads or mutates a session goes through this, so all work on one game —
     * actions, lobby changes, and DTO snapshots — is serialized. Without it,
     * two players clicking Ready simultaneously can both pass the allReady()
     * check and advance the phase twice, and a broadcast can snapshot a game
     * mid-mutation.
     */
    private <T> T locked(GameSession session, java.util.function.Supplier<T> body) {
        session.getLock().lock();
        try {
            return body.get();
        } finally {
            session.getLock().unlock();
        }
    }

    /** Snapshot through one viewer's eyes — never drains the combat log. */
    private GameStateDto snapshotState(GameSession session, String viewerTeam) {
        GameStateDto dto = new GameStateDto(session.getGame(), viewerTeam);
        dto.readyCount = session.getReadyCount();
        dto.playerCount = session.getPlayerCount();
        dto.fireDeclarationOpen = session.isFireDeclarationOpen();
        dto.fireDeclarationCaller = session.getFireDeclarationCallerName();
        dto.fireDeclarationResponded = session.getFireDeclarationRespondedNames();
        dto.fireDeclarationSpent = session.isFireDeclarationSpent();
        return dto;
    }

    /**
     * Viewer team for a token: null (omniscient) until ships are assigned —
     * solo/dev mode. A tokened player without ships sees only public info.
     */
    private String viewerTeamFor(GameSession session, String token) {
        boolean anyAssigned = session.getGame().getShips().stream()
                .anyMatch(s -> s.getOwner() != null);
        if (!anyAssigned)
            return null;
        String team = token != null ? session.getTeamNameFor(token) : null;
        return team != null ? team : "__spectator__";
    }

    /**
     * Broadcast for WebSocket — drains the combat log so it is delivered
     * exactly once. Once ships are assigned, each player gets a REDACTED
     * snapshot on their own destination: hidden information (seeker shuttle
     * identity, pseudo plasma, bay contents) must never reach the other
     * player's client, however politely the UI declines to render it.
     */
    private void broadcastState(GameSession session) {
        List<String> combatLog = session.drainCombatLog();
        boolean anyAssigned = session.getGame().getShips().stream()
                .anyMatch(s -> s.getOwner() != null);
        if (!anyAssigned) {
            GameStateDto dto = snapshotState(session, null);
            dto.combatLog = combatLog;
            broker.convertAndSend("/topic/games/" + session.getId() + "/state", dto);
            return;
        }
        for (String token : session.getPlayers().keySet()) {
            GameStateDto dto = snapshotState(session, viewerTeamFor(session, token));
            dto.combatLog = combatLog;
            dto.myShips = session.getEffectiveShipNamesForPlayer(token);
            broker.convertAndSend("/topic/games/" + session.getId() + "/state/" + token, dto);
        }
    }

    private void broadcastLobby(GameSession session) {
        broker.convertAndSend(
                "/topic/games/" + session.getId() + "/lobby",
                new LobbyStateDto(session));
    }

    // -------------------------------------------------------------------------
    // Validate a fleet against the patrol-scenario construction rules (S8.0)
    // -------------------------------------------------------------------------

    /** A proposed battle force, named by hull so the client never has to build ships. */
    public static class FleetValidationRequest {
        public String faction;      // empire buying the fleet
        public int year;            // scenario date (S8.13)
        public int budget;          // points agreed for this side (S8.11)
        public String flagship;     // hull of the ship leading it (S8.21)
        public List<String> hulls = new ArrayList<>();  // every ship, flagship included
    }

    /**
     * Check a fleet without committing to it. The rules live in amarillo-core so that a limit
     * is enforced wherever a fleet arrives from, not only where a form happens to check it;
     * this endpoint exists so the builder can show the whole picture as it is assembled.
     */
    @PostMapping("/fleets/validate")
    public ResponseEntity<Map<String, Object>> validateFleet(@RequestBody FleetValidationRequest req) {
        com.sfb.objects.ShipLibrary.loadAllSpecs("data/factions");

        List<com.sfb.objects.Ship> ships = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Map<String, Integer> seen = new java.util.HashMap<>();
        String flagshipName = null;

        for (String hull : req.hulls) {
            com.sfb.objects.ShipSpec spec = com.sfb.objects.ShipLibrary.get(req.faction, hull);
            if (spec == null) {
                unknown.add(hull);
                continue;
            }
            com.sfb.objects.Ship ship = com.sfb.objects.ShipLibrary.createShip(spec);
            // Several ships of one hull are normal; name them apart so violations can point at
            // the offender rather than at an ambiguous hull code.
            int n = seen.merge(hull, 1, Integer::sum);
            ship.setName(n == 1 ? hull : hull + " #" + n);
            ships.add(ship);
            if (flagshipName == null && hull.equalsIgnoreCase(req.flagship))
                flagshipName = ship.getName();
        }

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        if (!unknown.isEmpty()) {
            body.put("legal", false);
            body.put("unknownHulls", unknown);
            body.put("violations", List.of(Map.of(
                    "rule", "",
                    "severity", "ERROR",
                    "message", "No such hull for " + req.faction + ": " + String.join(", ", unknown),
                    "shipName", "")));
            return ResponseEntity.ok(body);
        }

        com.sfb.scenario.FleetValidator.Fleet fleet =
                new com.sfb.scenario.FleetValidator.Fleet(ships, flagshipName, req.budget, req.year);
        List<com.sfb.scenario.FleetValidator.Violation> violations =
                com.sfb.scenario.FleetValidator.validate(fleet);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (com.sfb.scenario.FleetValidator.Violation v : violations) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("rule", v.rule);
            row.put("severity", v.severity.name());
            row.put("message", v.message);
            row.put("shipName", v.shipName == null ? "" : v.shipName);
            rows.add(row);
        }

        body.put("legal", com.sfb.scenario.FleetValidator.isLegal(violations));
        body.put("cost", com.sfb.scenario.FleetValidator.fleetCost(ships));
        body.put("budget", req.budget);
        body.put("shipCount", ships.size());
        body.put("violations", rows);
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------
    // List scenarios
    // -------------------------------------------------------------------------

    @GetMapping("/scenarios")
    public ResponseEntity<List<Map<String, Object>>> listScenarios() {
        File scenarioDir = new File("data/scenarios");
        List<Map<String, Object>> result = new ArrayList<>();
        File[] files = scenarioDir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    ScenarioSpec spec = ScenarioSpec.fromJson(f);
                    Map<String, Object> entry = new java.util.LinkedHashMap<>();
                    entry.put("id", spec.id != null ? spec.id : "");
                    entry.put("name", spec.name != null ? spec.name : "");
                    entry.put("year", spec.year);
                    entry.put("numPlayers", spec.numPlayers);
                    entry.put("mapType", spec.mapType != null ? spec.mapType : "STANDARD");
                    entry.put("description", spec.description != null ? spec.description : "");
                    entry.put("specialRules", spec.specialRules != null ? spec.specialRules : List.of());

                    // Victory conditions summary
                    if (spec.victoryConditions != null) {
                        entry.put("victoryType",
                                spec.victoryConditions.type != null ? spec.victoryConditions.type : "STANDARD");
                        entry.put("victoryNotes",
                                spec.victoryConditions.notes != null ? spec.victoryConditions.notes : "");
                    } else {
                        entry.put("victoryType", "STANDARD");
                        entry.put("victoryNotes", "");
                    }

                    // Shuttle rules
                    if (spec.shuttleRules != null) {
                        entry.put("warpBoosterPacks", spec.shuttleRules.warpBoosterPacks);
                        entry.put("megapacks", spec.shuttleRules.megapacks);
                        entry.put("mrsShuttles", spec.shuttleRules.mrsShuttles);
                        entry.put("pfs", spec.shuttleRules.pfs);
                    }

                    // Sides summary: faction, name, ship list
                    List<Map<String, Object>> sides = new ArrayList<>();
                    if (spec.sides != null) {
                        for (ScenarioSpec.SideSpec side : spec.sides) {
                            Map<String, Object> s = new java.util.LinkedHashMap<>();
                            s.put("faction", side.faction != null ? side.faction : "");
                            s.put("name", side.name != null ? side.name : "");
                            List<Map<String, Object>> ships = new ArrayList<>();
                            if (side.ships != null) {
                                for (ScenarioSpec.ShipSetup ship : side.ships) {
                                    Map<String, Object> sh = new java.util.LinkedHashMap<>();
                                    sh.put("hull", ship.hull != null ? ship.hull : "");
                                    sh.put("shipName", ship.shipName != null ? ship.shipName : "");
                                    sh.put("startHex", ship.startHex != null ? ship.startHex : "");
                                    sh.put("startHeading", ship.startHeading != null ? ship.startHeading : "");
                                    sh.put("startSpeed", ship.startSpeed);
                                    sh.put("weaponStatus", ship.weaponStatus);
                                    sh.put("refits", ship.refits != null ? ship.refits : List.of());
                                    ships.add(sh);
                                }
                            }
                            s.put("ships", ships);
                            int reinforcements = 0;
                            if (side.reinforcements != null)
                                reinforcements = side.reinforcements.size();
                            s.put("reinforcementGroups", reinforcements);
                            sides.add(s);
                        }
                    }
                    entry.put("sides", sides);
                    result.add(entry);
                } catch (Exception e) {
                    System.err.println("Could not parse scenario file: " + f.getName());
                }
            }
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Returns COI-relevant ship data for a scenario: heavy weapons and drone racks
     * per ship, grouped by side. Used by the pre-game COI dialog.
     * Does not start a game — read-only.
     */
    @GetMapping("/scenarios/{scenarioId}/coi-data")
    public ResponseEntity<List<Map<String, Object>>> getCoiData(
            @PathVariable String scenarioId) {

        try {
            com.sfb.objects.ShipLibrary.loadAllSpecs("data/factions");
            ScenarioSpec spec = ScenarioSpec.fromJson(
                    "data/scenarios/" + scenarioId.toLowerCase() + ".json");
            List<List<Ship>> sideShips = com.sfb.scenario.ScenarioLoader.loadShips(spec);

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < spec.sides.size(); i++) {
                ScenarioSpec.SideSpec side = spec.sides.get(i);
                List<Ship> ships = i < sideShips.size() ? sideShips.get(i) : List.of();

                List<Map<String, Object>> shipList = new ArrayList<>();
                for (Ship ship : ships) {
                    Map<String, Object> s = new java.util.LinkedHashMap<>();
                    s.put("shipName", ship.getName());
                    s.put("bpv", ship.getBattlePointValue());

                    // Find this ship's weaponStatus from the spec
                    int ws = side.ships.stream()
                            .filter(ss -> ship.getName().equals(ss.shipName))
                            .mapToInt(ss -> ss.weaponStatus).findFirst().orElse(0);
                    s.put("weaponStatus", ws);

                    // Heavy weapons that can have arming modes chosen at WS-3
                    List<Map<String, Object>> heavy = new ArrayList<>();
                    int droneIndex = 0;
                    List<Map<String, Object>> drones = new ArrayList<>();
                    for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                        if (w instanceof com.sfb.weapons.HeavyWeapon
                                && !(w instanceof com.sfb.weapons.Fusion)
                                && !(w instanceof com.sfb.weapons.Disruptor)
                                && !(w instanceof com.sfb.weapons.DroneRack)) {
                            boolean canHold = !(w instanceof com.sfb.weapons.PlasmaLauncher)
                                    || ((com.sfb.weapons.PlasmaLauncher) w).canHold();
                            if (canHold) {
                                Map<String, Object> hw = new java.util.LinkedHashMap<>();
                                hw.put("designator", w.getDesignator());
                                hw.put("type", w.getType());
                                hw.put("isPlasma", w instanceof com.sfb.weapons.PlasmaLauncher);
                                heavy.add(hw);
                            }
                        }
                        if (w instanceof com.sfb.weapons.DroneRack) {
                            com.sfb.weapons.DroneRack rack = (com.sfb.weapons.DroneRack) w;
                            Map<String, Object> dr = new java.util.LinkedHashMap<>();
                            dr.put("index", droneIndex++);
                            dr.put("designator", w.getDesignator());
                            dr.put("spaces", rack.getSpaces());
                            dr.put("reloadCount", rack.getNumberOfReloads());
                            // Default ammo as list of type names (what's in the rack before any COI)
                            List<String> defAmmo = new ArrayList<>();
                            for (com.sfb.objects.Drone d : rack.getAmmo()) {
                                defAmmo.add(d.getDroneType() != null ? d.getDroneType().name() : "TypeI");
                            }
                            dr.put("defaultAmmo", defAmmo);
                            // Only TYPE_E, TYPE_G, and TYPE_H can load TypeVI variants
                            com.sfb.weapons.DroneRack.DroneRackType rt = rack.getRackType();
                            dr.put("canLoadTypeVI",
                                    rt == com.sfb.weapons.DroneRack.DroneRackType.TYPE_E
                                            || rt == com.sfb.weapons.DroneRack.DroneRackType.TYPE_G
                                            || rt == com.sfb.weapons.DroneRack.DroneRackType.TYPE_H);
                            drones.add(dr);
                        }
                    }
                    s.put("heavyWeapons", heavy);
                    s.put("droneRacks", drones);

                    // Orion option mounts (G15.4) — empty for non-Orion ships. Each
                    // mount lists the options legal for it (position/size/year/etc.),
                    // filtered against the same validator that equips them.
                    if (!ship.getOptionMounts().isEmpty()) {
                        com.sfb.objects.OptionMountCatalog catalog =
                                com.sfb.objects.OptionMountCatalog.loadDefault();
                        List<Map<String, Object>> mounts = new ArrayList<>();
                        for (com.sfb.objects.OptionMount m : ship.getOptionMounts()) {
                            Map<String, Object> mo = new java.util.LinkedHashMap<>();
                            mo.put("designator", m.getDesignator());
                            mo.put("position", m.getPosition().name());
                            mo.put("arcs", m.getArcs());
                            mo.put("currentOption", m.isEmpty() ? null : m.getWeapon().getName());
                            List<Map<String, Object>> legal = new ArrayList<>();
                            for (com.sfb.objects.OptionCatalogEntry e : catalog.all()) {
                                if (com.sfb.objects.OptionMountLoadout.validate(ship, m, e, spec.year) == null) {
                                    Map<String, Object> opt = new java.util.LinkedHashMap<>();
                                    opt.put("name", e.name);
                                    opt.put("cost", e.cost);
                                    opt.put("empires", e.empires);  // null = universal (cartel-exempt)
                                    legal.add(opt);
                                }
                            }
                            mo.put("legalOptions", legal);
                            mounts.add(mo);
                        }
                        s.put("optionMounts", mounts);
                    }

                    // Commander's options budget
                    int budgetPct = spec.commanderOptions != null
                            ? spec.commanderOptions.budgetPercent
                            : 20;
                    s.put("coiBudget", com.sfb.scenario.CoiLoadout.budget(
                            ship.getBattlePointValue(), budgetPct));
                    s.put("allowTBombs", spec.commanderOptions == null
                            || spec.commanderOptions.allowTBombs);
                    s.put("allowCommandos", spec.commanderOptions == null
                            || spec.commanderOptions.allowCommandos);
                    s.put("maxTBombs", com.sfb.constants.Constants.MAX_TBOMBS[ship.getSizeClass()]);
                    s.put("maxDroneSpeed", spec.commanderOptions != null
                            ? spec.commanderOptions.maxDroneSpeed
                            : null);

                    // Available drone types — filtered by scenario year and speed cap
                    Integer maxSpeed = spec.commanderOptions != null
                            ? spec.commanderOptions.maxDroneSpeed
                            : null;
                    List<Map<String, Object>> droneTypes = new ArrayList<>();
                    for (com.sfb.objects.DroneType dt : com.sfb.objects.DroneType.values()) {
                        if (!dt.availableIn(spec.year))
                            continue;
                        if (maxSpeed != null && dt.speed > maxSpeed)
                            continue;
                        Map<String, Object> dtMap = new java.util.LinkedHashMap<>();
                        dtMap.put("name", dt.name());
                        dtMap.put("speed", dt.speed);
                        dtMap.put("damage", dt.damage);
                        dtMap.put("rack", dt.rack);
                        droneTypes.add(dtMap);
                    }
                    s.put("availableDroneTypes", droneTypes);

                    // Shuttles eligible for pre-game conversion, with supported types per shuttle
                    List<Map<String, Object>> convertibleShuttles = new ArrayList<>();
                    for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
                        for (com.sfb.objects.shuttles.Shuttle sh : bay.getInventory()) {
                            List<String> types = new ArrayList<>();
                            if (sh.canBecomeSuicide())
                                types.add("suicide");
                            if (sh.canBecomeScatterPack())
                                types.add("scatterpack");
                            if (sh.canBecomeWildWeasel())
                                types.add("wildweasel");
                            if (!types.isEmpty()) {
                                Map<String, Object> shMap = new java.util.LinkedHashMap<>();
                                shMap.put("name", sh.getName());
                                shMap.put("types", types);
                                convertibleShuttles.add(shMap);
                            }
                        }
                    }
                    s.put("convertibleShuttles", convertibleShuttles);
                    // Max shuttle conversions allowed based on weapon status
                    s.put("maxPreparedShuttles", ws >= 3 ? 2 : ws == 2 ? 1 : 0);

                    shipList.add(s);
                }

                Map<String, Object> sideMap = new java.util.LinkedHashMap<>();
                sideMap.put("faction", side.faction != null ? side.faction : "");
                sideMap.put("name", side.name != null ? side.name : "");
                sideMap.put("cartel", side.cartel);           // scenario-fixed Orion cartel, or null (player picks)
                sideMap.put("cartelPinned", side.cartel != null);
                sideMap.put("ships", shipList);
                result.add(sideMap);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * The Orion cartel table (G15.44): each cartel's home + operating-zone empires.
     * Lets the COI dialog resolve an option's access tier and show the fleet quota.
     */
    @GetMapping("/scenarios/cartels")
    public ResponseEntity<List<Map<String, Object>>> getCartels() {
        com.sfb.objects.OrionCartelTable table = com.sfb.objects.OrionCartelTable.loadDefault();
        List<Map<String, Object>> out = new ArrayList<>();
        for (com.sfb.objects.OrionCartel c : table.all()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("name", c.name);
            m.put("home", c.home);
            m.put("operatingZone", c.operatingZone);
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    // -------------------------------------------------------------------------
    // Create game
    // -------------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<Map<String, String>> createGame(
            @RequestBody Map<String, String> body) {

        String hostName = body.getOrDefault("name", "Host");
        GameSession session = sessionService.createSession(hostName);

        return ResponseEntity.ok(Map.of(
                "gameId", session.getId(),
                "hostToken", session.getHostToken(),
                "message", "Game created — share the gameId with other players"));
    }

    // -------------------------------------------------------------------------
    // Join game
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/join")
    public ResponseEntity<Map<String, String>> joinGame(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String playerName = body.getOrDefault("name", "Player");
        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();

        return locked(session, () -> {
            String token = sessionService.joinSession(id, playerName);
            if (token == null)
                return ResponseEntity.notFound().build();

            broadcastLobby(session);

            return ResponseEntity.ok(Map.of(
                    "playerToken", token,
                    "message", "Joined game " + id + " as " + playerName));
        });
    }

    // -------------------------------------------------------------------------
    // Start game (host only)
    // -------------------------------------------------------------------------

    /**
     * Submit COI selections for this player's ships.
     * Body: { "shipName": { "extraBoardingParties": N, "convertBpToCommando": N,
     * "extraCommandoSquads": N, "extraTBombs": N,
     * "droneRackLoadouts": { "0": ["TypeIM", ...] },
     * "weaponArmingModes": { "A": "OVERLOAD", "B": "SPECIAL" } } }
     * Can be called multiple times before start(); later calls overwrite earlier
     * ones.
     */
    // -------------------------------------------------------------------------
    // Load scenario (host only, before start)
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/scenario")
    public ResponseEntity<Map<String, String>> loadScenario(
            @PathVariable String id,
            @RequestHeader("X-Player-Token") String token,
            @RequestBody Map<String, String> body) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();

        return locked(session, () -> {
            if (!session.isHost(token))
                return ResponseEntity.status(403).body(Map.of("error", "Only the host can select the scenario"));
            if (session.isStarted())
                return ResponseEntity.badRequest().body(Map.of("error", "Game already started"));

            String scenarioId = body.get("scenarioId");
            if (scenarioId == null || scenarioId.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "scenarioId is required"));

            try {
                session.loadScenario(scenarioId);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }

            broadcastLobby(session);
            return ResponseEntity.ok(Map.of("message", "Scenario loaded: " + scenarioId));
        });
    }

    @PostMapping("/{id}/coi")
    public ResponseEntity<Map<String, String>> submitCoi(
            @PathVariable String id,
            @RequestHeader("X-Player-Token") String token,
            @RequestBody Map<String, CoiRequest> body) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();

        return locked(session, () -> {
            if (!session.hasPlayer(token))
                return ResponseEntity.status(403).body(Map.of("error", "Not a player in this game"));
            if (!session.isScenarioLoaded())
                return ResponseEntity.badRequest().body(Map.of("error", "No scenario loaded yet"));
            if (session.isStarted())
                return ResponseEntity.badRequest().body(Map.of("error", "Game already started"));

            Map<String, com.sfb.scenario.CoiLoadout> loadouts = new java.util.LinkedHashMap<>();
            String cartel = null;
            for (Map.Entry<String, CoiRequest> entry : body.entrySet()) {
                loadouts.put(entry.getKey(), entry.getValue().toLoadout());
                if (cartel == null && entry.getValue().cartel != null) {
                    cartel = entry.getValue().cartel;
                }
            }

            // Cartel fleet-quota (G15.44) — reject an over-quota loadout.
            String quotaViolation = session.validateCartelQuota(cartel, loadouts);
            if (quotaViolation != null) {
                return ResponseEntity.badRequest().body(Map.of("error", quotaViolation));
            }

            session.submitCoi(token, loadouts);
            broadcastLobby(session);
            return ResponseEntity.ok(Map.of("message", "COI selections saved"));
        });
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, String>> startGame(
            @PathVariable String id,
            @RequestHeader("X-Player-Token") String token) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();

        return locked(session, () -> {
            if (!session.isHost(token))
                return ResponseEntity.status(403).body(Map.of("error", "Only the host can start the game"));
            if (!session.isScenarioLoaded())
                return ResponseEntity.badRequest().body(Map.of("error", "No scenario loaded yet"));
            if (session.isStarted())
                return ResponseEntity.badRequest().body(Map.of("error", "Game already started"));
            if (!session.allCoiDone())
                return ResponseEntity.badRequest().body(Map.of("error", "Waiting for all players to submit COI"));

            try {
                session.start();
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }

            broadcastLobby(session);
            broadcastState(session);
            return ResponseEntity.ok(Map.of("message", "Game started — impulse 1 begins now"));
        });
    }

    // -------------------------------------------------------------------------
    // Player list (host only — exposes tokens for ship assignment)
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/players")
    public ResponseEntity<List<Map<String, String>>> getPlayers(
            @PathVariable String id,
            @RequestHeader("X-Player-Token") String token) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();
        if (!session.isHost(token))
            return ResponseEntity.status(403).build();

        return locked(session, () -> {
            List<Map<String, String>> list = session.getPlayers().entrySet().stream()
                    .map(e -> Map.of(
                            "name", e.getValue().getName(),
                            "token", e.getKey()))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(list);
        });
    }

    // -------------------------------------------------------------------------
    // Assign ship (host only, after start)
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/assign")
    public ResponseEntity<Map<String, String>> assignShip(
            @PathVariable String id,
            @RequestHeader("X-Player-Token") String token,
            @RequestBody Map<String, String> body) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();
        if (!session.isHost(token))
            return ResponseEntity.status(403).body(Map.of("error", "Only the host can assign ships"));

        String playerToken = body.get("playerToken");
        String shipName = body.get("shipName");
        if (playerToken == null || shipName == null)
            return ResponseEntity.badRequest().body(Map.of("error", "playerToken and shipName are required"));

        return locked(session, () -> {
            String result = session.assignShip(playerToken, shipName);
            if (result != null)
                return ResponseEntity.badRequest().body(Map.of("error", result));

            broadcastLobby(session);
            if (session.isStarted())
                broadcastState(session); // notify GameBoard clients
            return ResponseEntity.ok(Map.of("message", shipName + " assigned successfully"));
        });
    }

    // -------------------------------------------------------------------------
    // Fire options — range, shield, and weapons in arc for a prospective shot
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/fire-options")
    public ResponseEntity<?> getFireOptions(
            @PathVariable String id,
            @RequestHeader(value = "X-Player-Token", required = false) String token,
            @RequestParam String attacker,
            @RequestParam String target) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();

        return locked(session, () -> {
            // Attacker may be a ship or an active shuttle/fighter
            Unit attackerUnit = session.getGame().getShips().stream()
                    .filter(s -> s.getName().equalsIgnoreCase(attacker))
                    .map(s -> (Unit) s)
                    .findFirst().orElse(null);
            if (attackerUnit == null) {
                attackerUnit = session.getGame().getActiveShuttles().stream()
                        .filter(s -> attacker.equalsIgnoreCase(s.getName()))
                        .map(s -> (Unit) s)
                        .findFirst().orElse(null);
            }
            if (attackerUnit == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Attacker not found: " + attacker));

            // Target may be a ship, seeker, or active shuttle
            Unit targetUnit = session.getGame().getShips().stream()
                    .filter(s -> s.getName().equalsIgnoreCase(target))
                    .map(s -> (Unit) s)
                    .findFirst().orElse(null);
            if (targetUnit == null) {
                targetUnit = session.getGame().getSeekers().stream()
                        .filter(s -> s instanceof Unit && target.equalsIgnoreCase(((Unit) s).getName()))
                        .map(s -> (Unit) s)
                        .findFirst().orElse(null);
            }
            if (targetUnit == null) {
                targetUnit = session.getGame().getActiveShuttles().stream()
                        .filter(s -> target.equalsIgnoreCase(s.getName()))
                        .map(s -> (Unit) s)
                        .findFirst().orElse(null);
            }
            if (targetUnit == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Target not found: " + target));

            int range = MapUtils.getRange(attackerUnit, targetUnit);
            // Fighters use raw range (no scanner bonus); ships use effectiveRange
            int adjRange = attackerUnit instanceof Ship
                    ? session.getGame().getEffectiveRange((Ship) attackerUnit, targetUnit)
                    : range;

            // Shield number (1-6) on the target ship facing the attacker
            int shieldNumber = 0;
            if (targetUnit instanceof Ship) {
                Ship targetShip = (Ship) targetUnit;
                int absFacing = MapUtils.getAbsoluteShieldFacing(targetShip, attackerUnit);
                int relFacing = MapUtils.getRelativeShieldFacing(absFacing, targetShip.getFacing());
                shieldNumber = relFacing > 0 ? (int) Math.ceil(relFacing / 2.0) : 1;
                shieldNumber = Math.max(1, Math.min(6, shieldNumber));
            }

            com.sfb.systemgroups.Weapons wGroup = attackerUnit instanceof Ship
                    ? ((Ship) attackerUnit).getWeapons()
                    : ((com.sfb.objects.shuttles.Shuttle) attackerUnit).getWeapons();

            boolean targetIsAddValid = targetUnit instanceof com.sfb.objects.Drone
                    || targetUnit instanceof com.sfb.objects.shuttles.Shuttle;

            List<String> weaponsInArc = wGroup.fetchAllBearingWeapons(attackerUnit, targetUnit).stream()
                    .filter(w -> !(w instanceof com.sfb.weapons.ADD) || targetIsAddValid)
                    .map(w -> w.getName())
                    .collect(Collectors.toList());

            boolean hasLockOn = attackerUnit instanceof Ship
                    && ((Ship) attackerUnit).hasLockOn(targetUnit);

            return ResponseEntity.ok(Map.of(
                    "range", range,
                    "adjustedRange", adjRange,
                    "shieldNumber", shieldNumber,
                    "weaponsInArc", weaponsInArc,
                    "hasLockOn", hasLockOn));
        });
    }

    // -------------------------------------------------------------------------
    // Hit & Run target options — systems on the target ship available for raiding
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/har-options")
    public ResponseEntity<?> getHarOptions(
            @PathVariable String id,
            @RequestHeader(value = "X-Player-Token", required = false) String token,
            @RequestParam String attacker,
            @RequestParam String target) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();

        return locked(session, () -> {
            Ship targetShip = session.getGame().getShips().stream()
                    .filter(s -> s.getName().equalsIgnoreCase(target))
                    .findFirst().orElse(null);
            if (targetShip == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Target not found: " + target));

            List<com.sfb.properties.SystemTarget> systems = session.getGame().getTargetableSystems(targetShip);

            List<Map<String, String>> result = systems.stream()
                    .map(st -> {
                        Map<String, String> m = new java.util.LinkedHashMap<>();
                        if (st.getType() == com.sfb.properties.SystemTarget.Type.WEAPON) {
                            m.put("code", "WEAPON:" + st.getDisplayName());
                        } else if (st.getType() == com.sfb.properties.SystemTarget.Type.TRACTOR) {
                            m.put("code", "TRACTOR:" + st.getIndex());
                        } else {
                            m.put("code", st.getType().name());
                        }
                        m.put("label", st.getDisplayName());
                        return m;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);
        });
    }

    // -------------------------------------------------------------------------
    // Guard options — owner-only: guard posts are secret (D7.831); they are
    // deliberately absent from the broadcast GameStateDto, which both players
    // receive identically. The raider learns of a guard by walking into it.
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/guard-options")
    public ResponseEntity<?> getGuardOptions(
            @PathVariable String id,
            @RequestHeader("X-Player-Token") String token,
            @RequestParam String ship) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();
        if (!session.hasPlayer(token))
            return ResponseEntity.status(403).body(Map.of("error", "Invalid player token"));
        if (!session.ownsShip(token, ship))
            return ResponseEntity.status(403).body(Map.of("error", "You do not own ship: " + ship));

        return locked(session, () -> {
            Ship shipObj = session.getGame().getShips().stream()
                    .filter(s -> s.getName().equalsIgnoreCase(ship))
                    .findFirst().orElse(null);
            if (shipObj == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Ship not found: " + ship));

            com.sfb.objects.GuardPosts posts = shipObj.getGuardPosts();
            List<Map<String, Object>> targets = new ArrayList<>();
            for (com.sfb.properties.SystemTarget st : session.getGame().getTargetableSystems(shipObj)) {
                if (st.getType() == com.sfb.properties.SystemTarget.Type.UIM)
                    continue; // not guardable (UIM system itself deferred)
                Map<String, Object> t = new java.util.LinkedHashMap<>();
                String code;
                boolean guarded;
                if (st.getType() == com.sfb.properties.SystemTarget.Type.WEAPON) {
                    code = "WEAPON:" + st.getDisplayName();
                    guarded = posts.isGuarded(st.getWeapon());
                } else if (st.getType() == com.sfb.properties.SystemTarget.Type.TRACTOR) {
                    code = "TRACTOR:" + st.getIndex();
                    guarded = posts.isBeamGuarded(st.getIndex());
                } else {
                    code = st.getType().name();
                    guarded = posts.isGuarded(st.getType());
                }
                t.put("code", code);
                t.put("label", st.getDisplayName());
                if (com.sfb.objects.GuardPosts.isPoolType(st.getType())) {
                    t.put("kind", "pool");
                    t.put("guards", posts.poolGuardCount(st.getType()));
                    t.put("boxes", posts.poolBoxCount(st.getType()));
                } else {
                    t.put("kind", "exact");
                }
                t.put("guarded", guarded);
                targets.add(t);
            }

            com.sfb.objects.TroopCount troops = shipObj.getCrew().getFriendlyTroops();
            return ResponseEntity.ok(Map.of(
                    "normalAvailable", troops.normal,
                    "commandosAvailable", troops.commandos,
                    "totalPosted", posts.totalPosted(),
                    "targets", targets));
        });
    }

    // -------------------------------------------------------------------------
    // Action
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/action")
    public ResponseEntity<Map<String, Object>> submitAction(
            @PathVariable String id,
            @RequestHeader("X-Player-Token") String token,
            @RequestBody ActionRequest request) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();

        return locked(session, () -> {
            if (!session.hasPlayer(token))
                return ResponseEntity.status(403).body(Map.of("error", "Invalid player token"));
            if (!session.isStarted())
                return ResponseEntity.badRequest().body(Map.of("error", "Game has not started yet"));

            if (request.getShipName() != null && !session.ownsShip(token, request.getShipName()))
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "You do not own ship: " + request.getShipName()));

            request.setPlayerToken(token);
            ActionResult result = session.executeAction(request);
            if (result.isSuccess())
                broadcastState(session);
            return ResponseEntity.ok(Map.of(
                    "success", result.isSuccess(),
                    "message", result.getMessage()));
        });
    }

    // -------------------------------------------------------------------------
    // Lobby state (read-only snapshot — same payload as the WebSocket broadcasts)
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/lobby")
    public ResponseEntity<?> getLobbyState(@PathVariable String id) {
        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();
        return locked(session, () -> ResponseEntity.ok(new LobbyStateDto(session)));
    }

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String id) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();

        return locked(session, () -> {
            List<Map<String, Object>> playerList = session.getPlayers().entrySet().stream()
                    .map(e -> {
                        Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("name", e.getValue().getName());
                        m.put("role", session.isHost(e.getKey()) ? "host" : "player");
                        m.put("ships", e.getValue().getShipNames());
                        return m;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "gameId", session.getId(),
                    "started", session.isStarted(),
                    "players", playerList));
        });
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/state")
    public ResponseEntity<?> getState(
            @PathVariable String id,
            @RequestHeader(value = "X-Player-Token", required = false) String token) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();
        if (!session.isStarted())
            return ResponseEntity.badRequest().body(Map.of("error", "Game has not started"));

        return locked(session, () -> {
            GameStateDto dto = snapshotState(session, viewerTeamFor(session, token));

            // Populate myShips if the caller identifies themselves.
            // In unassigned (solo) mode, the player controls all ships.
            if (token != null && session.hasPlayer(token)) {
                dto.myShips = session.getEffectiveShipNamesForPlayer(token);
            }

            return ResponseEntity.ok(dto);
        });
    }
}
