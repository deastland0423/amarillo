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
import com.sfb.properties.WeaponArmingType;
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

    private volatile Game.ImpulsePhase currentPhase        = Game.ImpulsePhase.MOVEMENT;
    private volatile int               currentTurn          = 1;
    private volatile int               currentImpulse       = 1;
    private volatile boolean           awaitingAllocation   = false;
    private final    List<String>      pendingAllocation    = new CopyOnWriteArrayList<>();
    private final    List<String>      movableNow           = new CopyOnWriteArrayList<>();
    private final    Set<String>       myShipNames          = ConcurrentHashMap.newKeySet();

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

        // Ensure ShipLibrary is loaded. Try several candidate paths so the client
        // works whether launched from the project root, the client subdir, or an IDE.
        if (!ShipLibrary.isLoaded()) {
            for (String path : new String[]{
                    "data/factions",           // run from project root
                    "../data/factions",        // run from amarillo-client subdir
                    "../../data/factions"}) {  // run from deeper subdir
                ShipLibrary.loadAllSpecs(path);
                if (ShipLibrary.isLoaded()) break;
            }
        }

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
            System.out.println("[poll] ships in DTO mapObjects: " +
                (dto.mapObjects == null ? "null" : dto.mapObjects.size()) +
                ", local ships after apply: will update");
            applyState(dto);
            System.out.println("[poll] local ship count after apply: " + ships.size());

            if (onStateChanged != null)
                Platform.runLater(() -> onStateChanged.accept(null));

        } catch (Exception e) {
            System.err.println("State poll error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void applyState(GameStateDto dto) {
        currentTurn    = dto.turn;
        currentImpulse = dto.impulse;
        currentPhase   = phaseFromLabel(dto.phase);

        // Sync TurnTracker so cloak fade steps render correctly
        if (dto.absoluteImpulse > 0) {
            int current = com.sfb.TurnTracker.getImpulse();
            for (int i = current; i < dto.absoluteImpulse; i++)
                com.sfb.TurnTracker.nextImpulse();
        }

        movableNow.clear();
        if (dto.movableNow != null) movableNow.addAll(dto.movableNow);

        myShipNames.clear();
        if (dto.myShips != null) myShipNames.addAll(dto.myShips);

        awaitingAllocation = dto.awaitingAllocation;
        pendingAllocation.clear();
        if (dto.pendingAllocation != null) pendingAllocation.addAll(dto.pendingAllocation);

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

        // Sync fire control and scanner
        ship.setActiveFireControl(dto.activeFireControl);

        // Update phaser capacitor
        try { ship.getWeapons().chargePhaserCapacitor(
                dto.phaserCapacitor - ship.getWeapons().getPhaserCapacitorEnergy());
        } catch (Exception ignored) {}

        // Sync cloak state and transition impulse
        if (dto.cloakState != null && ship.getCloakingDevice() != null) {
            try {
                com.sfb.systemgroups.CloakingDevice.CloakState cs =
                    com.sfb.systemgroups.CloakingDevice.CloakState.valueOf(dto.cloakState);
                ship.getCloakingDevice().setState(cs);
                ship.getCloakingDevice().setTransitionImpulse(dto.cloakTransitionImpulse);
            } catch (IllegalArgumentException ignored) {}
        }

        // Sync all weapon states (arming + lastImpulseFired)
        if (dto.weapons != null) {
            for (GameStateDto.WeaponDto wd : dto.weapons) {
                ship.getWeapons().fetchAllWeapons().stream()
                    .filter(w -> wd.name.equals(w.getName()))
                    .findFirst().ifPresent(w -> {
                        w.setLastImpulseFired(wd.lastImpulseFired);
                        if (w instanceof com.sfb.weapons.HeavyWeapon) {
                            com.sfb.weapons.HeavyWeapon hw = (com.sfb.weapons.HeavyWeapon) w;
                            hw.setArmingTurn(wd.armingTurn);
                            hw.setArmed(wd.armed);
                        }
                    });
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
            // Initialize shields so rendering doesn't NPE — build a minimal map from DTO
            Map<String, Object> shieldInit = new java.util.HashMap<>();
            if (dto.shields != null) {
                for (GameStateDto.ShieldDto sd : dto.shields)
                    shieldInit.put("shield" + sd.shieldNum, sd.max);
            }
            ship.getShields().init(shieldInit);
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
                    .header("X-Player-Token", playerToken)
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
        if (movableNow.isEmpty()) return Collections.emptyList();
        // If the globally next ship belongs to another player, we must wait.
        String nextGlobal = movableNow.get(0);
        if (!myShipNames.isEmpty() && !myShipNames.contains(nextGlobal))
            return Collections.emptyList();
        // It's our turn — return only our ships from the queue.
        List<Ship> result = new ArrayList<>();
        for (String name : movableNow) {
            if (!myShipNames.isEmpty() && !myShipNames.contains(name)) continue;
            Ship s = shipByName.get(name);
            if (s != null) result.add(s);
        }
        return result;
    }

    /** Names of ships owned by this player. */
    public Set<String> getMyShipNames() { return myShipNames; }

    /** The first ship in the global movement queue, regardless of ownership. Null if none. */
    public Ship getNextInQueue() {
        if (movableNow.isEmpty()) return null;
        return shipByName.get(movableNow.get(0));
    }

    /** True when there are ships to move this impulse but none belong to this player. */
    public boolean isWaitingForOtherPlayer() {
        return !movableNow.isEmpty() && getMovableShips().isEmpty();
    }

    @Override public List<com.sfb.objects.Shuttle> getMovableShuttles() {
        // Shuttle movement is handled by the server; for now return empty.
        // Full shuttle move support via REST comes in a follow-up.
        return Collections.emptyList();
    }

    @Override public boolean isAwaitingAllocation() { return awaitingAllocation; }
    @Override public Ship nextShipNeedingAllocation() {
        // Return the first pending ship that belongs to this player
        for (String name : pendingAllocation) {
            if (myShipNames.isEmpty() || myShipNames.contains(name)) {
                return shipByName.get(name);
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // GameFacade — geometry queries (pure local computation)
    // -------------------------------------------------------------------------

    @Override public int getRange(Unit a, Unit b) { return MapUtils.getRange(a, b); }

    @Override public int getEffectiveRange(Ship a, Unit b) {
        int trueRange = MapUtils.getRange(a, b);
        // Fully cloaked ships break all lock-ons — always double range.
        // Otherwise, active fire control means lock-on is maintained — no doubling.
        boolean fullyOrFadingCloaked = false;
        int cloakBonus = 0;
        if (b instanceof Ship) {
            com.sfb.systemgroups.CloakingDevice cloak = ((Ship) b).getCloakingDevice();
            if (cloak != null) {
                com.sfb.systemgroups.CloakingDevice.CloakState cs = cloak.getState();
                fullyOrFadingCloaked = (cs == com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED);
                cloakBonus = cloak.getCloakBonus(com.sfb.TurnTracker.getImpulse());
            }
        }
        boolean doubled = !a.isActiveFireControl() || fullyOrFadingCloaked;
        int base = doubled ? trueRange * 2 : trueRange;
        int scanner = a.getSpecialFunctions().getScanner();
        return base + scanner + cloakBonus;
    }
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
    @Override public ActionResult allocateEnergy(Ship ship, Energy e) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",      "ALLOCATE");
            body.put("shipName",  ship.getName());

            // Speed: warp movement energy / moveCost
            double moveCost = ship.getPerformanceData().getMovementCost();
            int warpSpeed = moveCost > 0 ? (int)(e.getWarpMovement() / moveCost) : 0;
            int totalSpeed = warpSpeed + e.getImpulseMovement();
            body.put("speed", totalSpeed);

            // Phaser capacitor — topOff if any energy was allocated
            body.put("topOffCap", e.getPhaserCapacitor() > 0);

            // Shield mode
            double activeCost   = ship.getActiveShieldCost();
            double minimumCost  = ship.getMinimumShieldCost();
            double allocShields = e.getActivateShields();
            String shieldMode;
            if (allocShields >= activeCost)        shieldMode = "ACTIVE";
            else if (allocShields >= minimumCost)  shieldMode = "MINIMUM";
            else                                   shieldMode = "OFF";
            body.put("shieldMode", shieldMode);

            // Heavy weapon arming
            Map<String, String> arming = new LinkedHashMap<>();
            for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
                if (!(w instanceof com.sfb.weapons.HeavyWeapon)) continue;
                WeaponArmingType type = e.getArmingType().get(w);
                if (type == null) {
                    arming.put(w.getName(), "SKIP");
                } else {
                    switch (type) {
                        case OVERLOAD: arming.put(w.getName(), "OVERLOAD"); break;
                        case SPECIAL:  arming.put(w.getName(), "ROLL");     break;
                        default:       arming.put(w.getName(), "STANDARD"); break;
                    }
                }
            }
            body.put("weaponArming", arming);
            body.put("cloakPaid",   e.isCloakPaid());

            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/games/" + gameId + "/action"))
                    .header("Content-Type", "application/json")
                    .header("X-Player-Token", playerToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("[allocate] response " + res.statusCode() + ": " + res.body());
            Map<?, ?> result = mapper.readValue(res.body(), Map.class);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            String msg = Objects.toString(result.get("message"), "");

            if (success) {
                // Remove from local pending list immediately so the dialog loop terminates
                pendingAllocation.remove(ship.getName());
            }

            poller.schedule(this::pollState, 0, TimeUnit.MILLISECONDS);
            return success ? ActionResult.ok(msg) : ActionResult.fail(msg);
        } catch (Exception ex) {
            System.err.println("[allocate] error: " + ex.getMessage());
            return ActionResult.fail("Network error: " + ex.getMessage());
        }
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
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",          "FIRE");
            body.put("shipName",      attacker.getName());
            body.put("targetName",    target.getName());
            body.put("weaponNames",   weapons.stream().map(Weapon::getName).collect(java.util.stream.Collectors.toList()));
            body.put("range",         range);
            body.put("adjustedRange", adjusted);
            body.put("shieldNumber",  shield);

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

            poller.schedule(this::pollState, 0, TimeUnit.MILLISECONDS);
            return success ? ActionResult.ok(msg) : ActionResult.fail(msg);
        } catch (Exception e) {
            return ActionResult.fail("Network error: " + e.getMessage());
        }
    }
    @Override public ActionResult hitAndRun(Ship acting, Ship target, List<SystemTarget> targets) {
        return ActionResult.fail("Hit & run via server not yet implemented");
    }
    @Override public ActionResult placeTBomb(Ship ship, Location loc, boolean isReal) {
        return ActionResult.fail("tBomb placement via server not yet implemented");
    }
    @Override public ActionResult cloak(Ship ship)   { return postAction("CLOAK",   ship.getName(), null); }
    @Override public ActionResult uncloak(Ship ship) { return postAction("UNCLOAK", ship.getName(), null); }
}
