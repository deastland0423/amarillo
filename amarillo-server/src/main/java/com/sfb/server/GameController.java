package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for game session management.
 *
 *   POST  /api/games              — create a game (host)
 *   POST  /api/games/{id}/join    — join a game
 *   POST  /api/games/{id}/start   — start the game (host only)
 *   GET   /api/games/{id}/status  — current session info
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameSessionService    sessionService;
    private final SimpMessagingTemplate broker;

    public GameController(GameSessionService sessionService, SimpMessagingTemplate broker) {
        this.sessionService = sessionService;
        this.broker         = broker;
    }

    private GameStateDto buildState(GameSession session) {
        GameStateDto dto = new GameStateDto(session.getGame());
        dto.readyCount   = session.getReadyCount();
        dto.playerCount  = session.getPlayerCount();
        return dto;
    }

    private void broadcastState(GameSession session) {
        broker.convertAndSend(
            "/topic/games/" + session.getId() + "/state",
            buildState(session)
        );
    }

    private void broadcastLobby(GameSession session) {
        broker.convertAndSend(
            "/topic/games/" + session.getId() + "/lobby",
            new LobbyStateDto(session)
        );
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
                "gameId",    session.getId(),
                "hostToken", session.getHostToken(),
                "message",   "Game created — share the gameId with other players"
        ));
    }

    // -------------------------------------------------------------------------
    // Join game
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/join")
    public ResponseEntity<Map<String, String>> joinGame(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String playerName = body.getOrDefault("name", "Player");
        String token = sessionService.joinSession(id, playerName);

        if (token == null)
            return ResponseEntity.notFound().build();

        broadcastLobby(sessionService.getSession(id));

        return ResponseEntity.ok(Map.of(
                "playerToken", token,
                "message",     "Joined game " + id + " as " + playerName
        ));
    }

    // -------------------------------------------------------------------------
    // Start game (host only)
    // -------------------------------------------------------------------------

    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, String>> startGame(
            @PathVariable String id,
            @RequestHeader("X-Player-Token") String token) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();
        if (!session.isHost(token))
            return ResponseEntity.status(403).body(Map.of("error", "Only the host can start the game"));
        if (session.isStarted())
            return ResponseEntity.badRequest().body(Map.of("error", "Game already started"));

        session.start();
        broadcastLobby(session);
        broadcastState(session);

        return ResponseEntity.ok(Map.of(
                "message", "Game started — impulse 1 begins now"
        ));
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

        List<Map<String, String>> list = session.getPlayers().entrySet().stream()
                .map(e -> Map.of(
                        "name",  e.getValue().getName(),
                        "token", e.getKey()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(list);
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
        if (!session.isStarted())
            return ResponseEntity.badRequest().body(Map.of("error", "Start the game before assigning ships"));

        String playerToken = body.get("playerToken");
        String shipName    = body.get("shipName");
        if (playerToken == null || shipName == null)
            return ResponseEntity.badRequest().body(Map.of("error", "playerToken and shipName are required"));

        String result = session.assignShip(playerToken, shipName);
        if (result != null)
            return ResponseEntity.badRequest().body(Map.of("error", result));

        broadcastLobby(session);
        return ResponseEntity.ok(Map.of("message", shipName + " assigned successfully"));
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
        if (!session.hasPlayer(token))
            return ResponseEntity.status(403).body(Map.of("error", "Invalid player token"));
        if (!session.isStarted())
            return ResponseEntity.badRequest().body(Map.of("error", "Game has not started yet"));

        if (request.getShipName() != null && !session.ownsShip(token, request.getShipName()))
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "You do not own ship: " + request.getShipName()
            ));

        request.setPlayerToken(token);
        ActionResult result = session.executeAction(request);
        if (result.isSuccess())
            broadcastState(session);
        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "message", result.getMessage()
        ));
    }

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String id) {

        GameSession session = sessionService.getSession(id);
        if (session == null)
            return ResponseEntity.notFound().build();

        List<Map<String, Object>> playerList = session.getPlayers().entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("name",  e.getValue().getName());
                    m.put("role",  session.isHost(e.getKey()) ? "host" : "player");
                    m.put("ships", e.getValue().getShipNames());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "gameId",   session.getId(),
                "started",  session.isStarted(),
                "players",  playerList
        ));
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

        GameStateDto dto = buildState(session);

        // Populate myShips if the caller identifies themselves
        if (token != null && session.hasPlayer(token)) {
            GameSession.PlayerInfo info = session.getPlayers().get(token);
            dto.myShips = info.getShipNames();
        }

        return ResponseEntity.ok(dto);
    }
}
