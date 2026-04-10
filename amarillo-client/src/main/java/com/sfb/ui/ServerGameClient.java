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
    private volatile int               readyCount           = 0;
    private volatile int               playerCount          = 1;

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
        readyCount     = dto.readyCount;
        playerCount    = dto.playerCount > 0 ? dto.playerCount : 1;

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
                else if (obj instanceof GameStateDto.SuicideShuttleDto)
                    newSeekers.add(buildSuicideShuttle((GameStateDto.SuicideShuttleDto) obj));
                else if (obj instanceof GameStateDto.ScatterPackDto)
                    newSeekers.add(buildScatterPack((GameStateDto.ScatterPackDto) obj));
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
        ship.setTBombs(dto.tBombs);
        ship.setDummyTBombs(dto.dummyTBombs);
        ship.getCrew().setAvailableBoardingParties(dto.boardingParties);
        // Sync transporter energy: bank exactly enough for the reported available uses
        ship.getTransporters().cleanUp();
        ship.getTransporters().bankEnergy(dto.transporterUses * 0.2);

        // Sync hull box damage
        ship.getHullBoxes().setAvailableFhull(dto.availableFhull);
        ship.getHullBoxes().setAvailableAhull(dto.availableAhull);
        ship.getHullBoxes().setAvailableChull(dto.availableChull);

        // Sync power system damage
        ship.getPowerSysetems().setAvailableLWarp(dto.availableLWarp);
        ship.getPowerSysetems().setAvailableRWarp(dto.availableRWarp);
        ship.getPowerSysetems().setAvailableCWarp(dto.availableCWarp);
        ship.getPowerSysetems().setAvailableImpulse(dto.availableImpulse);
        ship.getPowerSysetems().setAvailableBattery(dto.availableBattery);

        // Sync control space damage
        ship.getControlSpaces().setAvailableBridge(dto.availableBridge);
        ship.getControlSpaces().setAvailableEmer(dto.availableEmer);
        ship.getControlSpaces().setAvailableAuxcon(dto.availableAuxcon);

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
                        if (w instanceof PlasmaLauncher) {
                            PlasmaLauncher pl = (PlasmaLauncher) w;
                            if (wd.plasmaType != null) {
                                try {
                                    pl.setPlasmaType(com.sfb.properties.PlasmaType.valueOf(wd.plasmaType));
                                } catch (IllegalArgumentException ignored) {}
                            } else {
                                pl.setPlasmaType(null);
                            }
                            pl.setPseudoPlasmaReady(wd.pseudoPlasmaReady);
                        }
                    });
            }
        }

        // Sync drone rack ammo lists
        if (dto.droneRacks != null) {
            for (GameStateDto.DroneRackDto rd : dto.droneRacks) {
                ship.getWeapons().fetchAllWeapons().stream()
                    .filter(w -> w instanceof DroneRack && rd.name.equals(w.getName()))
                    .map(w -> (DroneRack) w)
                    .findFirst().ifPresent(rack -> {
                        List<com.sfb.objects.Drone> newAmmo = new java.util.ArrayList<>();
                        if (rd.drones != null) {
                            for (GameStateDto.DroneInRackDto dd : rd.drones) {
                                try {
                                    com.sfb.objects.DroneType dt = com.sfb.objects.DroneType.valueOf(dd.droneType);
                                    newAmmo.add(new com.sfb.objects.Drone(dt));
                                } catch (IllegalArgumentException ignored) {
                                    newAmmo.add(new com.sfb.objects.Drone());
                                }
                            }
                        }
                        rack.getAmmo().clear();
                        rack.getAmmo().addAll(newAmmo);
                    });
            }
        }

        // Sync shuttle bay inventories
        if (dto.shuttleBays != null) {
            List<com.sfb.systemgroups.ShuttleBay> bays = ship.getShuttles().getBays();
            for (GameStateDto.ShuttleBayDto bd : dto.shuttleBays) {
                if (bd.bayIndex < 0 || bd.bayIndex >= bays.size()) continue;
                com.sfb.systemgroups.ShuttleBay bay = bays.get(bd.bayIndex);
                bay.getInventory().clear();
                if (bd.shuttles != null) {
                    for (GameStateDto.ShuttleInBayDto sd : bd.shuttles) {
                        com.sfb.objects.Shuttle s = com.sfb.systemgroups.ShuttleBay.buildShuttle(
                                sd.type != null ? sd.type : "admin", sd.name);
                        if (s instanceof com.sfb.objects.SuicideShuttle) {
                            com.sfb.objects.SuicideShuttle ss = (com.sfb.objects.SuicideShuttle) s;
                            for (int i = 0; i < sd.armingTurnsComplete && i < 3; i++)
                                ss.arm(sd.warheadDamage > 0 ? (sd.warheadDamage / 2 / Math.max(1, sd.armingTurnsComplete)) : 1);
                        } else if (s instanceof com.sfb.objects.ScatterPack) {
                            com.sfb.objects.ScatterPack pack = (com.sfb.objects.ScatterPack) s;
                            // Sync payload count — add Type-I drones to match server state
                            pack.getPayload().clear();
                            for (int i = 0; i < sd.payloadCount; i++)
                                pack.addDrone(new com.sfb.objects.Drone(com.sfb.objects.DroneType.TypeI));
                        }
                        bay.getInventory().add(s);
                    }
                }
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
        // Reuse existing shuttle object by name so selectedShuttle references stay valid
        com.sfb.objects.Shuttle existing = activeShuttles.stream()
                .filter(s -> dto.name.equals(s.getName())).findFirst().orElse(null);
        com.sfb.objects.Shuttle s = existing != null ? existing : new com.sfb.objects.AdminShuttle();
        s.setName(dto.name);
        if (dto.location != null) s.setLocation(parseLocation(dto.location));
        s.setFacing(dto.facing);
        s.setSpeed(dto.speed);
        return s;
    }

    private Drone buildDrone(GameStateDto.DroneDto dto) {
        com.sfb.objects.DroneType dt = null;
        try { if (dto.droneType != null) dt = com.sfb.objects.DroneType.valueOf(dto.droneType); }
        catch (IllegalArgumentException ignored) {}
        Drone d = dt != null ? new Drone(dt) : new Drone();
        d.setName(dto.name);
        if (dto.location != null) d.setLocation(parseLocation(dto.location));
        d.setFacing(dto.facing);
        d.setSpeed(dto.speed);
        // Set controller so the canvas can color by faction
        if (dto.controllerName != null) {
            Ship controller = shipByName.get(dto.controllerName);
            if (controller != null) d.setController(controller);
        }
        // Set target for tooltip display
        if (dto.targetName != null) {
            Ship tgt = shipByName.get(dto.targetName);
            if (tgt != null) d.setTarget(tgt);
        }
        return d;
    }

    private PlasmaTorpedo buildPlasma(GameStateDto.PlasmaTorpedoDto dto) {
        PlasmaTorpedo p = PlasmaTorpedo.forRendering();
        p.setName(dto.name);
        if (dto.location != null) p.setLocation(parseLocation(dto.location));
        p.setFacing(dto.facing);
        p.setSpeed(dto.speed);
        if (dto.plasmaType != null) {
            try { p.setPlasmaType(com.sfb.properties.PlasmaType.valueOf(dto.plasmaType)); }
            catch (IllegalArgumentException ignored) {}
        }
        p.setDistanceTraveled(dto.distanceTraveled);
        p.setPseudoPlasma(dto.pseudo);
        p.setDamageTaken(dto.damageTaken);
        return p;
    }

    private com.sfb.objects.SuicideShuttle buildSuicideShuttle(GameStateDto.SuicideShuttleDto dto) {
        com.sfb.objects.SuicideShuttle ss = new com.sfb.objects.SuicideShuttle(new com.sfb.objects.AdminShuttle());
        ss.setName(dto.name);
        if (dto.location != null) ss.setLocation(parseLocation(dto.location));
        ss.setFacing(dto.facing);
        ss.setSpeed(dto.speed);
        if (dto.controllerName != null) {
            Ship ctrl = shipByName.get(dto.controllerName);
            if (ctrl != null) ss.setController(ctrl);
        }
        if (dto.targetName != null) {
            Ship tgt = shipByName.get(dto.targetName);
            if (tgt != null) ss.setTarget(tgt);
        }
        return ss;
    }

    private com.sfb.objects.ScatterPack buildScatterPack(GameStateDto.ScatterPackDto dto) {
        com.sfb.objects.ScatterPack pack = new com.sfb.objects.ScatterPack(new com.sfb.objects.AdminShuttle());
        pack.setName(dto.name);
        if (dto.location != null) pack.setLocation(parseLocation(dto.location));
        pack.setFacing(dto.facing);
        pack.setSpeed(dto.speed);
        if (dto.controllerName != null) {
            Ship ctrl = shipByName.get(dto.controllerName);
            if (ctrl != null) pack.setController(ctrl);
        }
        if (dto.targetName != null) {
            Ship tgt = shipByName.get(dto.targetName);
            if (tgt != null) pack.setTarget(tgt);
        }
        return pack;
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
    @Override public int     getReadyCount()       { return readyCount; }
    @Override public int     getPlayerCount()      { return playerCount; }

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
        // Return active shuttles owned by this player — server enforces actual move eligibility
        return activeShuttles.stream()
                .filter(s -> {
                    // If parentPlayer is tracked we could filter; for now return all active shuttles
                    // the player owns (server will reject moves from opponents)
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());
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
        // Compute locally — the ship object is fully synced from the server so
        // all system functional states are current.
        List<SystemTarget> result = new ArrayList<>();

        for (Weapon w : s.getWeapons().fetchAllWeapons()) {
            if (w.isFunctional())
                result.add(new SystemTarget(w));
        }

        com.sfb.systemgroups.PowerSystems ps = s.getPowerSysetems();
        if (ps.getAvailableLWarp() > 0 || ps.getAvailableRWarp() > 0 || ps.getAvailableCWarp() > 0)
            result.add(new SystemTarget(SystemTarget.Type.WARP,         "Warp Engines"));
        if (ps.getAvailableImpulse() > 0)
            result.add(new SystemTarget(SystemTarget.Type.IMPULSE,      "Impulse Engines"));

        com.sfb.systems.SpecialFunctions sf = s.getSpecialFunctions();
        if (sf.canDamageSensor())
            result.add(new SystemTarget(SystemTarget.Type.SENSORS,      "Sensors"));
        if (sf.canDamageScanner())
            result.add(new SystemTarget(SystemTarget.Type.SCANNERS,     "Scanners"));

        if (s.getTransporters().getAvailableTrans() > 0)
            result.add(new SystemTarget(SystemTarget.Type.TRANSPORTERS, "Transporters"));
        if (s.getCrew().getAvailableCrewUnits() > 0)
            result.add(new SystemTarget(SystemTarget.Type.CREW,         "Crew"));

        com.sfb.systemgroups.CloakingDevice cloak = s.getCloakingDevice();
        if (cloak != null && cloak.isFunctional())
            result.add(new SystemTarget(SystemTarget.Type.CLOAKING_DEVICE, "Cloaking Device"));

        com.sfb.systemgroups.DERFACS derfacs = s.getDerfacs();
        if (derfacs != null && derfacs.isFunctional())
            result.add(new SystemTarget(SystemTarget.Type.DERFACS,      "DERFACS"));

        com.sfb.systemgroups.HullBoxes h = s.getHullBoxes();
        if (h.getAvailableFhull() > 0)
            result.add(new SystemTarget(SystemTarget.Type.FHULL,        "Forward Hull"));
        if (h.getAvailableAhull() > 0)
            result.add(new SystemTarget(SystemTarget.Type.AHULL,        "Aft Hull"));
        if (h.getAvailableChull() > 0)
            result.add(new SystemTarget(SystemTarget.Type.CHULL,        "Center Hull"));

        return result;
    }

    // -------------------------------------------------------------------------
    // GameFacade — actions (REST)
    // -------------------------------------------------------------------------

    @Override public ActionResult advancePhase() {
        return postAction("ADVANCE_PHASE", null, null);
    }

    @Override public ActionResult unready() {
        return postAction("UNREADY", null, null);
    }

    @Override public ActionResult moveShip(Ship ship, MoveCommand.Action action) {
        return postAction("MOVE", ship.getName(), action.name());
    }

    // Shuttle and complex actions — stubs until corresponding server endpoints exist
    @Override public ActionResult moveShuttle(com.sfb.objects.Shuttle s, ShuttleMoveCommand.Action a) {
        return postAction("MOVE_SHUTTLE", s.getName(), a.name());
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
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",     "LAUNCH_SHUTTLE");
            body.put("shipName", ship.getName());
            body.put("action",   s.getName());   // shuttle name
            body.put("speed",    speed);
            body.put("range",    facing);        // facing packed in range field

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
    @Override public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack, Drone drone) {
        try {
            // Match by drone type — the dialog's drone reference may be stale if a poll
            // fired between dialog open and button click, rebuilding the ammo list.
            com.sfb.objects.DroneType targetType = drone.getDroneType();
            int droneIndex = -1;
            List<com.sfb.objects.Drone> ammo = rack.getAmmo();
            for (int i = 0; i < ammo.size(); i++) {
                if (ammo.get(i).getDroneType() == targetType) { droneIndex = i; break; }
            }
            if (droneIndex < 0)
                return ActionResult.fail("No drone of type " + targetType + " found in rack");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",        "LAUNCH_DRONE");
            body.put("shipName",    launcher.getName());
            body.put("targetName",  target.getName());
            body.put("weaponNames", List.of(rack.getName()));
            body.put("range",       droneIndex); // reused as drone index

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
    @Override public ActionResult launchPlasma(Ship attacker, Unit target, PlasmaLauncher weapon, boolean pseudo) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",        "LAUNCH_PLASMA");
            body.put("shipName",    attacker.getName());
            body.put("targetName",  target.getName());
            body.put("weaponNames", List.of(weapon.getName()));
            body.put("pseudo",      pseudo);

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
    @Override public ActionResult fire(Ship attacker, Unit target, List<Weapon> weapons, int range, int adjusted, int shield, boolean useUim) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",          "FIRE");
            body.put("shipName",      attacker.getName());
            body.put("targetName",    target.getName());
            body.put("weaponNames",   weapons.stream().map(Weapon::getName).collect(java.util.stream.Collectors.toList()));
            body.put("range",         range);
            body.put("adjustedRange", adjusted);
            body.put("shieldNumber",  shield);
            body.put("useUim",        useUim);

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
    @Override public ActionResult launchScatterPack(Ship launcher, com.sfb.objects.ScatterPack pack, Unit target) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",       "LAUNCH_SCATTER_PACK");
            body.put("shipName",   launcher.getName());
            body.put("action",     pack.getName());
            body.put("targetName", target.getName());

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

    @Override public ActionResult launchSuicideShuttle(Ship launcher, com.sfb.objects.SuicideShuttle shuttle, Unit target) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",       "LAUNCH_SUICIDE_SHUTTLE");
            body.put("shipName",   launcher.getName());
            body.put("action",     shuttle.getName());   // shuttle name in action field
            body.put("targetName", target.getName());

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
        try {
            List<String> systemCodes = new ArrayList<>();
            for (SystemTarget st : targets) {
                if (st.getType() == SystemTarget.Type.WEAPON) {
                    systemCodes.add("WEAPON:" + st.getWeapon().getName());
                } else {
                    systemCodes.add(st.getType().name());
                }
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",        "HIT_AND_RUN");
            body.put("shipName",    acting.getName());
            body.put("targetName",  target.getName());
            body.put("weaponNames", systemCodes);

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
    @Override public ActionResult placeTBomb(Ship ship, Location loc, boolean isReal) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type",     "PLACE_TBOMB");
            body.put("shipName", ship.getName());
            body.put("action",   loc.getX() + "|" + loc.getY());
            body.put("pseudo",   !isReal); // pseudo=true means dummy tBomb

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
    @Override public ActionResult cloak(Ship ship)   { return postAction("CLOAK",   ship.getName(), null); }
    @Override public ActionResult uncloak(Ship ship) { return postAction("UNCLOAK", ship.getName(), null); }
}
