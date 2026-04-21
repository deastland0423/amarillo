package com.sfb.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.sfb.Game;
import com.sfb.objects.*;
import com.sfb.systemgroups.CloakingDevice;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.DroneRack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of all game state, broadcast over WebSocket after
 * each action.
 *
 * Map objects form a hierarchy that mirrors the core object model:
 *
 *   MapObjectDto          (type, name, location)     ← mirrors Marker
 *     ShipDto             (hull, faction, shields, cloak)
 *     ShuttleDto          (parentShip, speed, facing)
 *     DroneDto            (droneType, warhead, target, faction)
 *     PlasmaTorpedoDto    (currentStrength, controllerFaction)
 *     MineDto             (active, revealed)
 *
 * Adding a new map object type in the future (base, asteroid, monster, etc.)
 * means adding a new subclass and a @JsonSubTypes entry — nothing else changes.
 */
public class GameStateDto {

    // -------------------------------------------------------------------------
    // Polymorphic base — mirrors Marker
    // -------------------------------------------------------------------------

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ShipDto.class,             name = "SHIP"),
        @JsonSubTypes.Type(value = ShuttleDto.class,          name = "SHUTTLE"),
        @JsonSubTypes.Type(value = SuicideShuttleDto.class,   name = "SUICIDE_SHUTTLE"),
        @JsonSubTypes.Type(value = ScatterPackDto.class,      name = "SCATTER_PACK"),
        @JsonSubTypes.Type(value = DroneDto.class,            name = "DRONE"),
        @JsonSubTypes.Type(value = PlasmaTorpedoDto.class,    name = "PLASMA"),
        @JsonSubTypes.Type(value = MineDto.class,             name = "MINE"),
    })
    public static abstract class MapObjectDto {
        public String name;
        public String location; // "<x|y>" or null if off-map
    }

    // -------------------------------------------------------------------------
    // Ship
    // -------------------------------------------------------------------------

    public static class ShieldDto {
        public int     shieldNum;
        public int     current;      // includes specific reinforcement (owner-only display)
        public int     baseStrength; // without reinforcement (public display)
        public int     max;
        public boolean active;
    }

    public static class WeaponDto {
        public String  name;
        public boolean armed;
        public int     armingTurn;
        public String  armingType;        // "STANDARD", "OVERLOAD", "SPECIAL", or null
        public int     lastImpulseFired;  // for canFire() checks client-side
        public boolean readyToFire;       // functional + armed (if heavy) + impulse gap satisfied
        public String  arcLabel;          // e.g. "FA", "FX + 13", "LF + L + RR + 5"       // functional + armed (if heavy) + impulse gap satisfied
        public boolean functional;
        public String  plasmaType;        // PlasmaLauncher only: currently arming torpedo type ("F","G","S","R") or null
        public String  launcherType;      // PlasmaLauncher only: fixed launcher type ("F","G","S","R") or null
        public boolean pseudoPlasmaReady; // PlasmaLauncher only: can still fire a pseudo?
        public boolean isHeavy;           // true for HeavyWeapon (disruptors, plasma, photon)
        // Energy-allocation helpers for heavy weapons
        public int     armingCost;        // energy to arm/hold this turn
        public int     totalArmingTurns;  // turns to fully arm (0 for instant)
        public boolean isRolling;         // PlasmaLauncher only: currently in rolling mode
        public int     rollingCost;       // PlasmaLauncher only: energy to keep rolling (always sent for plasma)
        public boolean canEpt;            // PlasmaLauncher only: can fire as Enveloping Plasma Torpedo
        public int     eptCost;           // PlasmaLauncher only: energy cost for EPT on final arming turn
        public int     maxShotsPerTurn;   // how many times this weapon may fire per turn
        public int     shotsThisTurn;     // shots already fired this turn
        public int     minImpulseGap;     // minimum global impulses between shots (0 = same-impulse multi-shot ok)
    }

    public static class DroneInRackDto {
        public String droneType;    // "I", "II", etc.
        public int    warheadDamage;
        public int    speed;
        public int    endurance;
    }

    /** One entry per distinct drone type in the reload pool. */
    public static class ReloadPoolEntryDto {
        public String droneType;  // e.g. "TYPE_I", "TYPE_IV"
        public double rackSize;   // spaces this drone type consumes
        public int    count;      // how many drones of this type are available
    }

    public static class DroneRackDto {
        public String              name;
        public boolean             functional;
        public boolean             canFire;
        public List<DroneInRackDto>    drones;          // currently loaded drones
        public int                     reloadCount;     // number of reload sets remaining
        public double                  reloadDeckCrewCost; // legacy: cost for first full set (0 if none)
        public boolean                 reloadingThisTurn;  // already staged for reload this turn
        public List<ReloadPoolEntryDto> reloadPool;     // available drones by type with counts
    }

    public static class ShuttleInBayDto {
        public String  name;
        public String  type;             // "admin", "gas", "hts", "suicide", "scatterpack"
        public int     maxSpeed;
        public boolean armed;            // suicide only: true when armingTurnsComplete >= 3
        public int     armingTurnsComplete; // suicide only: 0-3
        public int     warheadDamage;    // suicide only: totalEnergy * 2
        public int     payloadCount;     // scatterpack only: drones loaded
    }

    public static class ShuttleBayDto {
        public int                    bayIndex;
        public boolean                canLaunch;
        public List<ShuttleInBayDto>  shuttles;
    }

    public static class ShipDto extends MapObjectDto {
        public String               hull;
        public String               faction;
        public int                  facing;
        public int                  speed;
        public List<ShieldDto>      shields;
        public String               cloakState;
        public int                  cloakFadeStep;
        public int                  cloakTransitionImpulse;
        public double               phaserCapacitor;
        public double               phaserCapacitorMax;
        public boolean              capacitorsCharged;
        public boolean              activeFireControl;
        public int                  scannerBonus;
        public List<WeaponDto>      weapons;
        public List<DroneRackDto>   droneRacks;
        public List<ShuttleBayDto>  shuttleBays;
        public int                  tBombs;
        public int                  dummyTBombs;
        public int                  nuclearSpaceMines;
        public int                  transporterUses;
        public int                  boardingParties;
        public int                  commandos;
        // Crew
        public int                  availableCrewUnits;
        public int                  minimumCrew;
        public int                  availableDeckCrews;
        public String               crewQuality;       // "POOR" | "NORMAL" | "OUTSTANDING"
        public int                  availableTransporters;
        public double               transporterEnergyCost;
        // Hull box damage state
        public int                  availableFhull;
        public int                  availableAhull;
        public int                  availableChull;
        // Power system damage state
        public int                  availableLWarp;
        public int                  availableRWarp;
        public int                  availableCWarp;
        public int                  availableImpulse;
        public int                  availableApr;
        public int                  availableAwr;
        public int                  availableBattery;
        // Control space damage state (current / max)
        public int                  availableBridge;
        public int                  maxBridge;
        public int                  availableFlag;
        public int                  maxFlag;
        public int                  availableEmer;
        public int                  maxEmer;
        public int                  availableAuxcon;
        public int                  maxAuxcon;
        public int                  availableSecurity;
        public int                  maxSecurity;
        // Weapon damaged flags — stored alongside existing WeaponDto.destroyed field
        // Energy Allocation helper fields
        public boolean uimFunctional;     // true if ship has a functional UIM this impulse
        public int    totalPower;        // total power available for allocation
        public double moveCost;          // warp energy per speed point
        public double lifeSupportCost;   // housekeeping cost
        public int    fireControlCost;   // always 1
        public int    activeShieldCost;  // energy to keep shields fully active
        public double minimumShieldCost; // energy for minimum shields
        public int    batteryCharge;     // current battery energy available to draw
        public int    cloakCost;         // energy to maintain cloak (0 if no cloaking device)
        public int    maxSpeedNextTurn;  // C2.2 acceleration cap for this turn's EA
        public int    commandRating;
    }

    // -------------------------------------------------------------------------
    // Shuttle
    // -------------------------------------------------------------------------

    public static class ShuttleDto extends MapObjectDto {
        public int    facing;
        public int    speed;
        public String parentPlayer; // name of the player who owns this shuttle
    }

    // -------------------------------------------------------------------------
    // Suicide shuttle (seeker)
    // -------------------------------------------------------------------------

    public static class SuicideShuttleDto extends MapObjectDto {
        public int    facing;
        public int    speed;
        public String controllerName;   // name of the controlling ship
        public String targetName;
        public int    warheadDamage;    // totalEnergy * 2
        public int    armingTurnsComplete;
    }

    // -------------------------------------------------------------------------
    // Scatter pack (seeker — moves toward target, releases drones after 8 impulses)
    // -------------------------------------------------------------------------

    public static class ScatterPackDto extends MapObjectDto {
        public int    facing;
        public int    speed;
        public String controllerName;
        public String targetName;
        public int    payloadCount;    // number of drones still loaded
        public boolean released;       // true after drones have been deployed
    }

    // -------------------------------------------------------------------------
    // Drone
    // -------------------------------------------------------------------------

    public static class DroneDto extends MapObjectDto {
        public int     facing;
        public int     speed;
        public String  droneType;          // "I", "II", etc. — revealed on identification
        public int     warheadDamage;      // revealed on identification
        public int     hull;               // current hull remaining
        public int     damageTaken;        // maxHull - hull — always public (visible on the drone)
        public int     maxHull;            // hull at launch (from DroneType) — revealed on identification
        public int     endurance;          // revealed on identification
        public String  targetName;         // revealed on identification
        public String  controllerFaction;
        public String  controllerName;     // name of the controlling ship — always public
        public int     launchImpulse;      // always public
        public boolean isIdentified;       // true once identified by an enemy
    }

    // -------------------------------------------------------------------------
    // Plasma torpedo
    // -------------------------------------------------------------------------

    public static class PlasmaTorpedoDto extends MapObjectDto {
        public int     facing;
        public int     speed;
        public int     currentStrength;   // always public
        public String  controllerFaction;
        public String  controllerName;    // name of the launching ship — always public
        public String  plasmaType;        // "F", "G", "S", "R" — never revealed to enemy
        public int     distanceTraveled;
        public boolean pseudo;            // never revealed to enemy
        public double  damageTaken;
        public int     launchImpulse;     // always public
        public String  targetName;        // revealed on identification
        public boolean isIdentified;      // true once identified by an enemy
    }

    // -------------------------------------------------------------------------
    // Space mine / tBomb
    // -------------------------------------------------------------------------

    public static class MineDto extends MapObjectDto {
        public boolean active;
        public boolean revealed;
    }

    // -------------------------------------------------------------------------
    // Top-level fields
    // -------------------------------------------------------------------------

    public int               turn;
    public int               impulse;
    public int               absoluteImpulse;
    public String            phase;
    public List<String>      movableNow;
    public List<String>      myShips;          // ships owned by the requesting player (null = all ships)
    public boolean           awaitingAllocation;
    public List<String>      pendingAllocation; // ship names not yet allocated this turn
    public List<MapObjectDto> mapObjects;
    public int               readyCount;       // players who have clicked Ready this phase
    public int               playerCount;      // total players in the session
    public List<String>      combatLog = new ArrayList<>();  // fire/damage events since last broadcast

    // -------------------------------------------------------------------------
    // Constructor — builds from live Game state
    // -------------------------------------------------------------------------

    public GameStateDto() {}

    public GameStateDto(Game game) {
        this.turn            = game.getCurrentTurn();
        this.impulse         = game.getCurrentImpulse();
        this.absoluteImpulse = game.getAbsoluteImpulse();
        this.phase           = game.getCurrentPhase().getLabel();

        this.movableNow = new ArrayList<>();
        for (Ship s : game.getMovableShips())
            movableNow.add(s.getName());

        this.awaitingAllocation = game.isAwaitingAllocation();
        this.pendingAllocation = new ArrayList<>();
        if (game.isAwaitingAllocation()) {
            for (Ship s : game.getAllocationQueue())
                pendingAllocation.add(s.getName());
        }

        this.mapObjects = new ArrayList<>();

        for (Ship ship : game.getShips())
            mapObjects.add(fromShip(ship, game));

        for (com.sfb.objects.Shuttle shuttle : game.getActiveShuttles())
            mapObjects.add(fromShuttle(shuttle));

        for (Seeker seeker : game.getSeekers()) {
            if (seeker instanceof Drone)
                mapObjects.add(fromDrone((Drone) seeker));
            else if (seeker instanceof PlasmaTorpedo)
                mapObjects.add(fromPlasma((PlasmaTorpedo) seeker));
            else if (seeker instanceof com.sfb.objects.SuicideShuttle)
                mapObjects.add(fromSuicideShuttle((com.sfb.objects.SuicideShuttle) seeker));
            else if (seeker instanceof com.sfb.objects.ScatterPack)
                mapObjects.add(fromScatterPack((com.sfb.objects.ScatterPack) seeker));
        }

        for (SpaceMine mine : game.getMines())
            mapObjects.add(fromMine(mine));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private static ShipDto fromShip(Ship ship, Game game) {
        ShipDto dto     = new ShipDto();
        dto.name        = ship.getName();
        dto.location    = ship.getLocation() != null ? ship.getLocation().toString() : null;
        dto.facing      = ship.getFacing();
        dto.speed       = ship.getSpeed();
        dto.hull        = ship.getHullType();
        dto.faction     = ship.getFaction() != null ? ship.getFaction().name() : "Federation";

        dto.shields = new ArrayList<>();
        for (int s = 1; s <= 6; s++) {
            ShieldDto sd  = new ShieldDto();
            sd.shieldNum  = s;
            sd.current      = ship.getShields().getShieldStrength(s);
            sd.baseStrength = ship.getShields().getBaseShieldStrength(s);
            sd.max          = ship.getShields().getMaxShieldStrength(s);
            sd.active       = ship.getShields().isShieldActive(s);
            dto.shields.add(sd);
        }

        CloakingDevice cloak = ship.getCloakingDevice();
        if (cloak != null) {
            dto.cloakState             = cloak.getState().name();
            dto.cloakFadeStep          = cloak.getFadeStep(game.getAbsoluteImpulse());
            dto.cloakTransitionImpulse = cloak.getTransitionImpulse();
        } else {
            dto.cloakState             = "NONE";
            dto.cloakFadeStep          = 0;
            dto.cloakTransitionImpulse = -1;
        }

        dto.phaserCapacitor    = ship.getWeapons().getPhaserCapacitorEnergy();
        dto.phaserCapacitorMax = ship.getWeapons().getAvailablePhaserCapacitor();
        dto.capacitorsCharged  = ship.isCapacitorsCharged();
        dto.activeFireControl  = ship.isActiveFireControl();
        dto.scannerBonus       = ship.getSpecialFunctions().getScanner();
        dto.tBombs                = ship.getTBombs();
        dto.dummyTBombs           = ship.getDummyTBombs();
        dto.nuclearSpaceMines     = ship.getNuclearSpaceMines();
        dto.transporterUses       = ship.getTransporters().availableUses();
        dto.boardingParties       = ship.getCrew().getAvailableBoardingParties();
        dto.commandos             = ship.getCrew().getFriendlyTroops().commandos;
        dto.availableCrewUnits    = ship.getCrew().getAvailableCrewUnits();
        dto.minimumCrew           = ship.getCrew().getMinimumCrew();
        dto.availableDeckCrews    = ship.getCrew().getAvailableDeckCrews();
        dto.crewQuality           = ship.getCrew().getCrewQuality().name();
        dto.availableTransporters    = ship.getTransporters().getAvailableTrans();
        dto.transporterEnergyCost    = com.sfb.constants.Constants.TRANS_ENERGY;

        // Hull box damage state
        com.sfb.systemgroups.HullBoxes hb = ship.getHullBoxes();
        dto.availableFhull  = hb.getAvailableFhull();
        dto.availableAhull  = hb.getAvailableAhull();
        dto.availableChull  = hb.getAvailableChull();

        // Power system damage state
        com.sfb.systemgroups.PowerSystems ps = ship.getPowerSysetems();
        dto.availableLWarp   = ps.getAvailableLWarp();
        dto.availableRWarp   = ps.getAvailableRWarp();
        dto.availableCWarp   = ps.getAvailableCWarp();
        dto.availableImpulse = ps.getAvailableImpulse();
        dto.availableApr     = ps.getAvailableApr();
        dto.availableAwr     = ps.getAvailableAwr();
        dto.availableBattery = ps.getAvailableBattery();

        // Energy Allocation helper fields
        dto.totalPower        = ps.getTotalAvailablePower();
        dto.moveCost          = ship.getPerformanceData().getMovementCost();
        dto.lifeSupportCost   = ship.getLifeSupportCost();
        dto.fireControlCost   = ship.getFireControlCost();
        dto.activeShieldCost  = ship.getActiveShieldCost();
        dto.minimumShieldCost = ship.getMinimumShieldCost();
        dto.batteryCharge     = ps.getBatteryPower();
        dto.cloakCost         = cloak != null ? cloak.getPowerToActivate() : 0;
        dto.maxSpeedNextTurn  = ship.getMaxAccelerationSpeed();
        dto.commandRating     = ship.getCommandRating();
        dto.uimFunctional     = ship.getActiveUim(com.sfb.TurnTracker.getImpulse()) != null;

        // Control space damage state
        com.sfb.systemgroups.ControlSpaces cs = ship.getControlSpaces();
        dto.availableBridge  = cs.getAvailableBridge();
        dto.maxBridge        = cs.getBridge();
        dto.availableFlag    = cs.getAvailableFlag();
        dto.maxFlag          = cs.getFlag();
        dto.availableEmer    = cs.getAvailableEmer();
        dto.maxEmer          = cs.getEmer();
        dto.availableAuxcon  = cs.getAvailableAuxcon();
        dto.maxAuxcon        = cs.getAuxcon();
        dto.availableSecurity = cs.getAvailableSecurity();
        dto.maxSecurity      = cs.getSecurity();

        dto.weapons = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            WeaponDto wd = new WeaponDto();
            wd.name             = w.getName();
            wd.lastImpulseFired = w.getLastImpulseFired();
            wd.functional       = w.isFunctional();
            wd.arcLabel         = w.getArcLabel();
            boolean armedIfNeeded = !(w instanceof com.sfb.weapons.HeavyWeapon)
                    || ((com.sfb.weapons.HeavyWeapon) w).isArmed();
            wd.readyToFire      = w.isFunctional() && armedIfNeeded && w.canFire();
            if (w instanceof com.sfb.weapons.HeavyWeapon) {
                com.sfb.weapons.HeavyWeapon hw = (com.sfb.weapons.HeavyWeapon) w;
                wd.armed           = hw.isArmed();
                wd.armingTurn      = hw.getArmingTurn();
                wd.armingType      = hw.getArmingType() != null ? hw.getArmingType().name() : null;
                wd.isHeavy         = true;
                wd.armingCost      = hw.energyToArm();
                wd.totalArmingTurns = hw.totalArmingTurns();
            }
            if (w instanceof com.sfb.weapons.PlasmaLauncher) {
                com.sfb.weapons.PlasmaLauncher pl = (com.sfb.weapons.PlasmaLauncher) w;
                wd.plasmaType        = pl.getPlasmaType()     != null ? pl.getPlasmaType().name()     : null;
                wd.launcherType      = pl.getLauncherType()   != null ? pl.getLauncherType().name()   : null;
                wd.pseudoPlasmaReady = pl.isPseudoPlasmaReady();
                wd.isRolling         = pl.isRolling();
                wd.rollingCost       = pl.rollingCost(); // always sent so UI can show Roll button on final turn
                wd.canEpt            = pl.canEpt();
                wd.eptCost           = pl.eptCost();
            }
            wd.maxShotsPerTurn  = w.getMaxShotsPerTurn();
            wd.shotsThisTurn    = w.getShotsThisTurn();
            wd.minImpulseGap    = w.getMinImpulseGap();
            dto.weapons.add(wd);
        }

        dto.droneRacks = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof DroneRack)) continue;
            DroneRack rack = (DroneRack) w;
            DroneRackDto rd = new DroneRackDto();
            rd.name       = rack.getName();
            rd.functional = rack.isFunctional();
            rd.canFire    = rack.canFire();
            rd.drones     = new ArrayList<>();
            for (Drone d : rack.getAmmo()) {
                DroneInRackDto dd = new DroneInRackDto();
                dd.droneType    = d.getDroneType() != null ? d.getDroneType().toString() : "?";
                dd.warheadDamage = d.getWarheadDamage();
                dd.speed        = d.getSpeed();
                dd.endurance    = d.getEndurance();
                rd.drones.add(dd);
            }
            rd.reloadCount       = rack.getNumberOfReloads();
            rd.reloadingThisTurn = rack.isReloadingThisTurn();
            rd.reloadDeckCrewCost = rack.getReloads().isEmpty() ? 0
                    : DroneRack.reloadCost(rack.getReloads().get(0));
            // Build flat pool: count available drones by type across all reload sets
            Map<String, ReloadPoolEntryDto> poolMap = new LinkedHashMap<>();
            for (List<Drone> set : rack.getReloads()) {
                for (Drone d : set) {
                    String type = d.getDroneType() != null ? d.getDroneType().toString() : "?";
                    ReloadPoolEntryDto entry = poolMap.computeIfAbsent(type, t -> {
                        ReloadPoolEntryDto e = new ReloadPoolEntryDto();
                        e.droneType = t;
                        e.rackSize  = d.getRackSize();
                        e.count     = 0;
                        return e;
                    });
                    entry.count++;
                }
            }
            rd.reloadPool = new ArrayList<>(poolMap.values());
            dto.droneRacks.add(rd);
        }

        dto.shuttleBays = new ArrayList<>();
        List<ShuttleBay> bays = ship.getShuttles().getBays();
        for (int i = 0; i < bays.size(); i++) {
            ShuttleBay bay = bays.get(i);
            ShuttleBayDto bd = new ShuttleBayDto();
            bd.bayIndex  = i;
            bd.canLaunch = bay.canLaunch(game.getAbsoluteImpulse());
            bd.shuttles  = new ArrayList<>();
            for (com.sfb.objects.Shuttle s : bay.getInventory()) {
                ShuttleInBayDto sd = new ShuttleInBayDto();
                sd.name     = s.getName();
                sd.type     = s.getClass().getSimpleName().replace("Shuttle", "").toLowerCase();
                sd.maxSpeed = s.getMaxSpeed();
                if (s instanceof com.sfb.objects.SuicideShuttle) {
                    com.sfb.objects.SuicideShuttle ss = (com.sfb.objects.SuicideShuttle) s;
                    sd.armed               = ss.isArmed();
                    sd.armingTurnsComplete = ss.getArmingTurnsComplete();
                    sd.warheadDamage       = ss.getWarheadDamage();
                } else if (s instanceof com.sfb.objects.ScatterPack) {
                    sd.payloadCount = ((com.sfb.objects.ScatterPack) s).getPayload().size();
                }
                bd.shuttles.add(sd);
            }
            dto.shuttleBays.add(bd);
        }

        return dto;
    }

    private static ShuttleDto fromShuttle(com.sfb.objects.Shuttle shuttle) {
        ShuttleDto dto   = new ShuttleDto();
        dto.name         = shuttle.getName();
        dto.location     = shuttle.getLocation() != null ? shuttle.getLocation().toString() : null;
        dto.facing       = shuttle.getFacing();
        dto.speed        = shuttle.getSpeed();
        dto.parentPlayer = shuttle.getOwner() != null ? shuttle.getOwner().getName() : null;
        return dto;
    }

    private static SuicideShuttleDto fromSuicideShuttle(com.sfb.objects.SuicideShuttle ss) {
        SuicideShuttleDto dto    = new SuicideShuttleDto();
        dto.name                 = ss.getName();
        dto.location             = ss.getLocation() != null ? ss.getLocation().toString() : null;
        dto.facing               = ss.getFacing();
        dto.speed                = ss.getSpeed();
        dto.warheadDamage        = ss.getWarheadDamage();
        dto.armingTurnsComplete  = ss.getArmingTurnsComplete();
        dto.controllerName       = ss.getController() != null ? ((com.sfb.objects.Unit) ss.getController()).getName() : null;
        dto.targetName           = ss.getTarget() != null ? ss.getTarget().getName() : null;
        return dto;
    }

    private static ScatterPackDto fromScatterPack(com.sfb.objects.ScatterPack pack) {
        ScatterPackDto dto    = new ScatterPackDto();
        dto.name              = pack.getName();
        dto.location          = pack.getLocation() != null ? pack.getLocation().toString() : null;
        dto.facing            = pack.getFacing();
        dto.speed             = pack.getSpeed();
        dto.payloadCount      = pack.getPayload().size();
        dto.released          = pack.isReleased();
        dto.controllerName    = pack.getController() != null ? ((com.sfb.objects.Unit) pack.getController()).getName() : null;
        dto.targetName        = pack.getTarget() != null ? pack.getTarget().getName() : null;
        return dto;
    }

    private static DroneDto fromDrone(Drone drone) {
        DroneDto dto          = new DroneDto();
        dto.name              = drone.getName();
        dto.location          = drone.getLocation() != null ? drone.getLocation().toString() : null;
        dto.facing            = drone.getFacing();
        dto.speed             = drone.getSpeed();
        dto.droneType         = drone.getDroneType() != null ? drone.getDroneType().toString() : "?";
        dto.warheadDamage     = drone.getWarheadDamage();
        dto.hull              = drone.getHull();
        dto.maxHull           = drone.getDroneType() != null ? drone.getDroneType().hull : drone.getHull();
        dto.damageTaken       = dto.maxHull - drone.getHull();
        dto.targetName        = drone.getTarget() != null ? drone.getTarget().getName() : null;
        dto.controllerFaction = controllerFaction(drone.getController());
        dto.controllerName    = drone.getController() != null ? drone.getController().getName() : null;
        dto.endurance         = drone.getEndurance();
        dto.launchImpulse     = drone.getLaunchImpulse();
        dto.isIdentified      = drone.isIdentified();
        return dto;
    }

    private static PlasmaTorpedoDto fromPlasma(PlasmaTorpedo torp) {
        PlasmaTorpedoDto dto  = new PlasmaTorpedoDto();
        dto.name              = torp.getName();
        dto.location          = torp.getLocation() != null ? torp.getLocation().toString() : null;
        dto.facing            = torp.getFacing();
        dto.speed             = torp.getSpeed();
        dto.currentStrength   = torp.getCurrentStrength();
        dto.controllerFaction = controllerFaction(torp.getController());
        dto.controllerName    = torp.getController() != null ? torp.getController().getName() : null;
        dto.plasmaType        = torp.getPlasmaType() != null ? torp.getPlasmaType().name() : null;
        dto.distanceTraveled  = torp.getDistanceTraveled();
        dto.pseudo            = torp.isPseudoPlasma();
        dto.damageTaken       = torp.getDamageTaken();
        dto.launchImpulse     = torp.getLaunchImpulse();
        dto.targetName        = torp.getTarget() != null ? torp.getTarget().getName() : null;
        dto.isIdentified      = torp.isIdentified();
        return dto;
    }

    private static MineDto fromMine(SpaceMine mine) {
        MineDto dto  = new MineDto();
        dto.name     = mine.getName();
        dto.location = mine.getLocation() != null ? mine.getLocation().toString() : null;
        dto.active   = mine.isActive();
        dto.revealed = mine.isRevealed();
        return dto;
    }

    private static String controllerFaction(Unit controller) {
        if (controller instanceof Ship) {
            com.sfb.properties.Faction f = ((Ship) controller).getFaction();
            return f != null ? f.name() : null;
        }
        return null;
    }
}
