package com.sfb.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.dto.GameStateDto;
import com.sfb.commands.MoveCommand;
import com.sfb.commands.ShuttleMoveCommand;
import com.sfb.objects.*;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.systems.Energy;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;
import javafx.application.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * GameFacade implementation that talks to the Amarillo Spring Boot server.
 *
 * Actions are sent as REST POSTs. State is kept current by polling
 * GET /api/games/{id}/state every 500 ms and updating the local object graph.
 *
 * Ship objects are constructed from ShipLibrary on first encounter so their
 * weapons and systems are fully initialized. Location, facing, speed and
 * shield strengths are updated in-place on each poll so canvas references
 * stay stable.
 *
 * Complex geometry queries (range, bearing, shield number) work locally because
 * they only need ship position/facing data, which is always current.
 */
public class ServerGameClient implements GameFacade {

    // -------------------------------------------------------------------------
    // Connection info
    // -------------------------------------------------------------------------

    private final String     baseUrl;   // e.g. "http://localhost:8080"
    private final String     gameId;
    private final String     playerToken;

    // -------------------------------------------------------------------------
    // HTTP + JSON
    // -------------------------------------------------------------------------

    private final HttpClient   http   = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Live object maps — keyed by name for stable references
    // -------------------------------------------------------------------------

    private final List<Ship>                    ships         = new CopyOnWriteArrayList<>();
    private final Map<String, Ship>             shipByName    = new ConcurrentHashMap<>();
    private final List<com.sfb.objects.Shuttle> activeShuttles = new CopyOnWriteArrayList<>();
    private final List<Seeker>                  seekers       = new CopyOnWriteArrayList<>();
    private final List<SpaceMine>               mines         = new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // Game phase state (updated from each poll)
    // -------------------------------------------------------------------------

    private volatile Game.ImpulsePhase currentPhase  = Game.ImpulsePhase.MOVEMENT;
    private volatile int               currentTurn    = 1;
    private volatile int               currentImpulse = 1;
    private final    List<String>      movableNow     = new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // State change callback — SFBMapApp registers this to trigger a re-render
    // -------------------------------------------------------------------------

    private Consumer<Void> onStateChanged = null;

    public void setOnStateChanged(Consumer<Void> callback) {
        this.onStateChanged = callback;
    }

    // -------------------------------------------------------------------------
    // Polling
    // -------------------------------------------------------------------------

    private final ScheduledExecutorService poller =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "state-poller");
                t.setDaemon(true);
                return t;
            });

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public ServerGameClient(String baseUrl, String gameId, String playerToken) {
        this.baseUrl     = baseUrl.replaceAll("/$", "");
        this.gameId      = gameId;
        this.playerToken = playerToken;

        // Ensure ShipLibrary is loaded (server already loaded it, but the client
        // needs its own copy to construct Ship objects with full systems).
        if (!ShipLibrary.isLoaded())
            ShipLibrary.loadAllSpecs("data/factions");

        // Start polling — initial fetch is immediate, then every 500 ms.
        poller.scheduleAtFixedRate(this::pollState, 0, 500, TimeUnit.MILLISECONDS);
    }

    /** Stop polling — call when the window closes. */
    public void shutdown() {
        poller.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Polling logic
    // -------------------------------------------------------------------------

    private void pollState() {
        try {
            String url  = baseUrl + "/api/games/" + gameId + "/state";
            String body = getJson(url);
            if (body == null) return;

            GameStateDto dto = mapper.readValue(body, GameStateDto.class);
            applyState(dto);

            if (onStateChanged != null)
                Platform.runLater(() -> onStateChanged.accept(null));

        } catch (Exception e) {
            System.err.println("State poll error: " + e.getMessage());
        }
    }

    private void applyState(GameStateDto dto) {
        currentTurn    = dto.turn;
        currentImpulse = dto.impulse;
        currentPhase   = phaseFromLabel(dto.phase);

        movableNow.clear();
        if (dto.movableNow != null) movableNow.addAll(dto.movableNow);

        Set<String> seenShips = new HashSet<>();
        List<com.sfb.objects.Shuttle> newShuttles = new ArrayList<>();
        List<Seeker>                  newSeekers  = new ArrayList<>();
        List<SpaceMine>               newMines    = new ArrayList<>();

        if (dto.mapObjects != null) {
            for (GameStateDto.MapObjectDto obj : dto.mapObjects) {
                if (obj instanceof GameStateDto.ShipDto)
                    applyShip((GameStateDto.ShipDto) obj, seenShips);
                else if (obj instanceof GameStateDto.ShuttleDto)
                    newShuttles.add(buildShuttle((GameStateDto.ShuttleDto) obj));
                else if (obj instanceof GameStateDto.DroneDto)
                    newSeekers.add(buildDrone((GameStateDto.DroneDto) obj));
                else if (obj instanceof GameStateDto.PlasmaTorpedoDto)
                    newSeekers.add(buildPlasma((GameStateDto.PlasmaTorpedoDto) obj));
                else if (obj instanceof GameStateDto.MineDto)
                    newMines.add(buildMine((GameStateDto.MineDto) obj));
            }
        }

        // Remove ships that are no longer in the state
        shipByName.keySet().retainAll(seenShips);
        ships.removeIf(s -> !seenShips.contains(s.getName()));

        activeShuttles.clear();
        activeShuttles.addAll(newShuttles);
        seekers.clear();
        seekers.addAll(newSeekers);
        mines.clear();
        mines.addAll(newMines);
    }

    private void applyShip(GameStateDto.ShipDto dto, Set<String> seenShips) {
        seenShips.add(dto.name);

        Ship ship = shipByName.computeIfAbsent(dto.name, n -> {
            Ship s = buildShipFromLibrary(dto);
            ships.add(s);
            return s;
        });

        // Update mutable position fields
        if (dto.location != null) ship.setLocation(parseLocation(dto.location));
        ship.setFacing(dto.facing);
        ship.setSpeed(dto.speed);

        // Update shield strengths from server state
        if (dto.shields != null) {
            for (GameStateDto.ShieldDto sd : dto.shields) {
                ship.getShields().setShieldValue(sd.shieldNum, sd.current);
            }
        }
    }

    private Ship buildShipFromLibrary(GameStateDto.ShipDto dto) {
        ShipSpec spec = ShipLibrary.get(dto.faction, dto.hull);
        Ship ship;
        if (spec != null) {
            ship = ShipLibrary.createShip(spec);
        } else {
            ship = new Ship();
            ship.setName(dto.name);
        }
        if (dto.location != null) ship.setLocation(parseLocation(dto.location));
        ship.setFacing(dto.facing);
        ship.setSpeed(dto.speed);
        if (dto.faction != null) {
            try { ship.setFaction(Faction.valueOf(dto.faction)); }
            catch (IllegalArgumentException ignored) {}
        }
        return ship;
    }

    private com.sfb.objects.Shuttle buildShuttle(GameStateDto.ShuttleDto dto) {
        com.sfb.objects.AdminShuttle s = new com.sfb.objects.AdminShuttle();
        s.setName(dto.name);
        if (dto.location != null) s.setLocation(parseLocation(dto.location));
        s.setFacing(dto.facing);
        return s;
    }

    private Drone buildDrone(GameStateDto.DroneDto dto) {
        Drone d = new Drone();
        d.setName(dto.name);
        if (dto.location != null) d.setLocation(parseLocation(dto.location));
        d.setFacing(dto.facing);
        d.setSpeed(dto.speed);
        return d;
    }

    private PlasmaTorpedo buildPlasma(GameStateDto.PlasmaTorpedoDto dto) {
        PlasmaTorpedo p = PlasmaTorpedo.forRendering();
        p.setName(dto.name);
        if (dto.location != null) p.setLocation(parseLocation(dto.location));
        p.setFacing(dto.facing);
        p.setSpeed(dto.speed);
        return p;
    }

    private SpaceMine buildMine(GameStateDto.MineDto dto) {
        SpaceMine m = SpaceMine.forRendering();
        m.setName(dto.name);
        if (dto.location != null) m.setLocation(parseLocation(dto.location));
        return m;
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    /** Parse "<x|y>" back to a Location. */
    private static Location parseLocation(String s) {
        // format: <x|y>
        String inner = s.replaceAll("[<>]", "");
        String[] parts = inner.split("\\|");
        return new Location(Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()));
    }

    private static Game.ImpulsePhase phaseFromLabel(String label) {
        if (label == null) return Game.ImpulsePhase.MOVEMENT;
        for (Game.ImpulsePhase p : Game.ImpulsePhase.values())
            if (p.getLabel().equalsIgnoreCase(label)) return p;
        return Game.ImpulsePhase.MOVEMENT;
    }

    // -------------------------------------------------------------------------
    // REST helpers
    // -------------------------------------------------------------------------

    private String getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? res.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private ActionResult postAction(String type, String shipName, String action) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("type", type);
            if (shipName != null) body.put("shipName", shipName);
            if (action   != null) body.put("action",   action);

            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/games/" + gameId + "/action"))
                    .header("Content-Type", "application/json")
                    .header("X-Player-Token", playerToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            Map<?, ?> result = mapper.readValue(res.body(), Map.class);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            String msg = Objects.toString(result.get("message"), "");

            // Immediately poll to refresh state after an action
            poller.schedule(this::pollState, 0, TimeUnit.MILLISECONDS);

            return success ? ActionResult.ok(msg) : ActionResult.fail(msg);
        } catch (Exception e) {
            return ActionResult.fail("Network error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // GameFacade — state reads
    // -------------------------------------------------------------------------

    @Override public void setup() { /* server already started */ }

    @Override public List<Ship>   getShips()          { return Collections.unmodifiableList(ships); }
    @Override public List<Seeker> getSeekers()         { return Collections.unmodifiableList(seekers); }
    @Override public List<SpaceMine> getMines()        { return Collections.unmodifiableList(mines); }
    @Override public List<com.sfb.objects.Shuttle> getActiveShuttles() {
        return Collections.unmodifiableList(activeShuttles);
    }

    @Override public Game.ImpulsePhase getCurrentPhase()  { return currentPhase; }
    @Override public int getCurrentTurn()                  { return currentTurn; }
    @Override public int getCurrentImpulse()               { return currentImpulse; }
    @Override public int getAbsoluteImpulse()              { return currentImpulse; }

    @Override public boolean canFireThisPhase()    { return currentPhase == Game.ImpulsePhase.DIRECT_FIRE; }
    @Override public boolean canLaunchThisPhase()  { return currentPhase == Game.ImpulsePhase.ACTIVITY; }

    @Override public List<Ship> getMovableShips() {
        List<Ship> result = new ArrayList<>();
        for (String name : movableNow) {
            Ship s = shipByName.get(name);
            if (s != null) result.add(s);
        }
        return result;
    }

    @Override public List<com.sfb.objects.Shuttle> getMovableShuttles() {
        // Shuttle movement is handled by the server; for now return empty.
        // Full shuttle move support via REST comes in a follow-up.
        return Collections.emptyList();
    }

    // Energy allocation is auto-handled server-side for now
    @Override public boolean isAwaitingAllocation()    { return false; }
    @Override public Ship    nextShipNeedingAllocation() { return null; }

    // -------------------------------------------------------------------------
    // GameFacade — geometry queries (pure local computation)
    // -------------------------------------------------------------------------

    @Override public int getRange(Unit a, Unit b)               { return MapUtils.getRange(a, b); }
    @Override public int getEffectiveRange(Ship a, Unit b)      { return MapUtils.getRange(a, b); }
    @Override public int getShieldNumber(Marker a, Ship t) {
        int facing = t.getRelativeShieldFacing(a);
        int num = (facing % 2 == 0) ? facing / 2 : (facing + 1) / 2;
        return Math.max(1, Math.min(6, num));
    }
    @Override public List<Weapon> getBearingWeapons(Ship a, Unit t) {
        return a.fetchAllBearingWeapons(t);
    }
    @Override public List<SystemTarget> getTargetableSystems(Ship s) {
        // Hit & Run not yet implemented via server — return empty list
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // GameFacade — actions (REST)
    // -------------------------------------------------------------------------

    @Override public ActionResult advancePhase() {
        return postAction("ADVANCE_PHASE", null, null);
    }

    @Override public ActionResult moveShip(Ship ship, MoveCommand.Action action) {
        return postAction("MOVE", ship.getName(), action.name());
    }

    // Shuttle and complex actions — stubs until corresponding server endpoints exist
    @Override public ActionResult moveShuttle(com.sfb.objects.Shuttle s, ShuttleMoveCommand.Action a) {
        return ActionResult.fail("Shuttle movement via server not yet implemented");
    }
    @Override public ActionResult allocateEnergy(Ship s, Energy e) {
        return ActionResult.fail("Energy allocation via server not yet implemented");
    }
    @Override public ActionResult launchShuttle(Ship ship, ShuttleBay bay, com.sfb.objects.Shuttle s, int speed, int facing) {
        return ActionResult.fail("Shuttle launch via server not yet implemented");
    }
    @Override public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack, Drone drone) {
        return ActionResult.fail("Drone launch via server not yet implemented");
    }
    @Override public ActionResult launchPlasma(Ship attacker, Unit target, PlasmaLauncher weapon, boolean pseudo) {
        return ActionResult.fail("Plasma launch via server not yet implemented");
    }
    @Override public ActionResult fire(Ship attacker, Unit target, List<Weapon> weapons, int range, int adjusted, int shield) {
        return ActionResult.fail("Fire via server not yet implemented");
    }
    @Override public ActionResult hitAndRun(Ship acting, Ship target, List<SystemTarget> targets) {
        return ActionResult.fail("Hit & run via server not yet implemented");
    }
    @Override public ActionResult placeTBomb(Ship ship, Location loc, boolean isReal) {
        return ActionResult.fail("tBomb placement via server not yet implemented");
    }
    @Override public ActionResult cloak(Ship ship)   { return ActionResult.fail("Cloak via server not yet implemented"); }
    @Override public ActionResult uncloak(Ship ship) { return ActionResult.fail("Uncloak via server not yet implemented"); }
}
