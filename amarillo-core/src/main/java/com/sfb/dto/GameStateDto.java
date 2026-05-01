package com.sfb.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.sfb.Game;
import com.sfb.TurnTracker;
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
 * MapObjectDto (type, name, location) ← mirrors Marker
 * ShipDto (hull, faction, shields, cloak)
 * ShuttleDto (parentShip, speed, facing)
 * DroneDto (droneType, warhead, target, faction)
 * PlasmaTorpedoDto (currentStrength, controllerFaction)
 * MineDto (active, revealed)
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
            @JsonSubTypes.Type(value = ShipDto.class, name = "SHIP"),
            @JsonSubTypes.Type(value = ShuttleDto.class, name = "SHUTTLE"),
            @JsonSubTypes.Type(value = SuicideShuttleDto.class, name = "SUICIDE_SHUTTLE"),
            @JsonSubTypes.Type(value = ScatterPackDto.class, name = "SCATTER_PACK"),
            @JsonSubTypes.Type(value = DroneDto.class, name = "DRONE"),
            @JsonSubTypes.Type(value = PlasmaTorpedoDto.class, name = "PLASMA"),
            @JsonSubTypes.Type(value = MineDto.class, name = "MINE"),
            @JsonSubTypes.Type(value = TerrainDto.class, name = "TERRAIN"),
            @JsonSubTypes.Type(value = WildWeaselDto.class, name = "WILD_WEASEL"),
    })
    public static abstract class MapObjectDto {
        public String name;
        public String location; // "<x|y>" or null if off-map
    }

    public static class TerrainDto extends MapObjectDto {
        public String terrainType; // "ASTEROID" | "PLANET"
    }

    public static class WildWeaselDto extends MapObjectDto {
        public int facing;
        public int speed;
        public String parentShipName;
        public String parentPlayer;
        public boolean exploding;
        public boolean postExplosion;
    }

    // -------------------------------------------------------------------------
    // Ship
    // -------------------------------------------------------------------------

    public static class ShieldDto {
        public int shieldNum;
        public int current; // includes specific reinforcement (owner-only display)
        public int baseStrength; // without reinforcement (public display)
        public int max;
        public boolean active;
        public int impulsesUntilRaiseable; // 0 = can raise now; >0 = impulses remaining in lockout
    }

    public static class WeaponDto {
        public String name;
        public boolean armed;
        public int armingTurn;
        public String armingType; // "STANDARD", "OVERLOAD", "SPECIAL", or null
        public int lastImpulseFired; // for canFire() checks client-side
        public boolean readyToFire; // functional + armed (if heavy) + impulse gap satisfied
        public String arcLabel; // e.g. "FA", "FX + 13", "LF + L + RR + 5"
        public int arcMask; // 24-bit bitmask: bit N-1 set = direction N in arc
        public int launchDirectionsMask; // PlasmaLauncher/DroneRack: valid launch facing bitmask; 0 = use arcMask
        public boolean functional;
        public String plasmaType; // PlasmaLauncher only: currently arming torpedo type ("F","G","S","R") or null
        public String launcherType; // PlasmaLauncher only: fixed launcher type ("F","G","S","R") or null
        public boolean pseudoPlasmaReady; // PlasmaLauncher only: can still fire a pseudo?
        public boolean isHeavy; // true for HeavyWeapon (disruptors, plasma, photon)
        // Energy-allocation helpers for heavy weapons
        public int armingCost; // energy to arm (standard, unarmed)
        public int holdCost; // energy to hold per turn; 0 = hold not supported
        public boolean canOverload; // weapon supports OVERLOAD mode
        public boolean canSuicide; // weapon supports SPECIAL/SUICIDE mode (Fusion only)
        public boolean canProximity; // weapon supports PROXIMITY (prox) mode (Photon only)
        public boolean overloadFinalTurnOnly; // OVERLOAD only choosable on the final arming turn
        public int totalArmingTurns; // turns to fully arm (0 for instant)
        public boolean isRolling; // PlasmaLauncher only: currently in rolling mode
        public int rollingCost; // PlasmaLauncher only: energy to keep rolling (always sent for plasma)
        public boolean canEpt; // PlasmaLauncher only: can fire as Enveloping Plasma Torpedo
        public int eptCost; // PlasmaLauncher only: energy cost for EPT on final arming turn
        public int maxShotsPerTurn; // how many times this weapon may fire per turn
        public int shotsThisTurn; // shots already fired this turn
        public int minImpulseGap; // minimum global impulses between shots (0 = same-impulse multi-shot ok)
        public int chargesRemaining; // FighterFusion only: charges left (0-2); ignored for other weapons
        public boolean canFireDouble; // FighterFusion only: true when 2 charges remain
        public int addShots; // ADD only: shots remaining in current load
        public int addReloads; // ADD only: reserve shots remaining
        public int addCapacity; // ADD only: shots per full load (6 or 12)
    }

    public static class DroneInRackDto {
        public String droneType; // "I", "II", etc.
        public int warheadDamage;
        public int speed;
        public int endurance;
    }

    /** One entry per distinct drone type in the reload pool. */
    public static class ReloadPoolEntryDto {
        public String droneType; // e.g. "TYPE_I", "TYPE_IV"
        public double rackSize; // spaces this drone type consumes
        public int count; // how many drones of this type are available
    }

    public static class DroneRackDto {
        public String name;
        public boolean functional;
        public boolean canFire;
        public List<DroneInRackDto> drones;
        public int reloadCount;
        public double reloadDeckCrewCost;
        public boolean reloadingThisTurn;
        public List<ReloadPoolEntryDto> reloadPool;
        public int launchDirectionsMask; // 0 = unrestricted
    }

    public static class ShuttleInBayDto {
        public String name;
        public String type; // "admin", "gas", "hts", "suicide", "scatterpack", "stinger1", etc.
        public int maxSpeed;
        public boolean canLaunch; // true if hatch or tube is available for this shuttle right now
        public boolean armed; // suicide only: true when armingTurnsComplete >= 3
        public int armingTurnsComplete; // suicide only: 0-3
        public int warheadDamage; // suicide only: totalEnergy * 2
        public List<String> payload; // scatterpack only: live drone type names (e.g. "TypeIM")
        public List<String> pendingPayload; // scatterpack only: drones staged for end-of-turn loading
        public int maxDroneSpaces; // scatterpack only: max rack spaces (default 6)
        public double committedSpaces; // scatterpack only: payload + pending spaces already used
        public int wwChargeCount; // admin only: 0=uncharged, 1=primed, 2=ready to launch
        public boolean wwReady; // admin only: true when wwChargeCount >= 2
    }

    public static class ShuttleBayDto {
        public int bayIndex;
        public boolean canLaunch;
        public int launchTubeCount;
        public int availableTubes;
        public List<ShuttleInBayDto> shuttles;
    }

    public static class ShipDto extends MapObjectDto {
        public String hull;
        public String faction;
        public int facing;
        public int speed;
        public List<ShieldDto> shields;
        public String cloakState;
        public int cloakFadeStep;
        public int cloakTransitionImpulse;
        public double phaserCapacitor;
        public double phaserCapacitorMax;
        public boolean capacitorsCharged;
        public boolean activeFireControl;
        public int scannerBonus;
        public int sensorRating;
        public int ecmAllocated;
        public int eccmAllocated;
        public List<WeaponDto> weapons;
        public List<DroneRackDto> droneRacks;
        public List<ShuttleBayDto> shuttleBays;
        public int tBombs;
        public int dummyTBombs;
        public int nuclearSpaceMines;
        public int transporterUses;
        public int boardingParties;
        public int commandos;
        public int availableLab;
        // Crew
        public int availableCrewUnits;
        public int capturedCrew;
        public int minimumCrew;
        public int availableDeckCrews;
        public String crewQuality; // "POOR" | "NORMAL" | "OUTSTANDING"
        public int availableTransporters;
        public int totalTransporters;
        public double transporterEnergyCost;
        public int availableTractors;
        public int totalTractors;
        // Hull box damage state
        public int availableFhull;
        public int availableAhull;
        public int availableChull;
        public int maxFhull;
        public int maxAhull;
        public int maxChull;
        // Power system damage state
        public int availableLWarp;
        public int availableRWarp;
        public int availableCWarp;
        public int availableImpulse;
        public int availableApr;
        public int availableAwr;
        public int maxLWarp;
        public int maxRWarp;
        public int maxCWarp;
        public int maxImpulse;
        public int maxApr;
        public int maxAwr;
        public int availableBattery;
        public int batteryPower;
        // Control space damage state (current / max)
        public int availableBridge;
        public int maxBridge;
        public int availableFlag;
        public int maxFlag;
        public int availableEmer;
        public int maxEmer;
        public int availableAuxcon;
        public int maxAuxcon;
        public int availableSecurity;
        public int maxSecurity;
        // Crew state
        public boolean skeleton;
        // HET state
        public int reserveWarp;
        public int hetCost;
        public int hetsThisTurn;
        public int lastHetImpulse;
        public int immobileUntilImpulse;
        // Weapon damaged flags — stored alongside existing WeaponDto.destroyed field
        // Energy Allocation helper fields
        public boolean uimFunctional; // true if ship has a functional UIM this impulse
        public int totalPower; // total power available for allocation
        public double moveCost; // warp energy per speed point
        public double lifeSupportCost; // housekeeping cost
        public int fireControlCost; // always 1
        public int activeShieldCost; // energy to keep shields fully active
        public double minimumShieldCost; // energy for minimum shields
        public int batteryCharge; // current battery energy available to draw
        public int cloakCost; // energy to maintain cloak (0 if no cloaking device)
        public int maxSpeedNextTurn; // C2.2 acceleration cap for this turn's EA
        public int commandRating;
        public List<String> lockOnTargets; // names of units this ship has lock-on to
        public String tokenArt; // optional path to PNG token image
        // Turn mode display helpers
        public String turnMode; // e.g. "A", "B", "C"
        public int turnHexes; // hexes required between turns at current speed
        public int hexesUntilTurn; // 0 = may turn now; >0 = hexes still needed
        // Capture / disengagement state
        public boolean captured;
        public boolean disengaged;
        public boolean canDisengageBySeparation;
        public java.util.List<String> destructionDirections; // A–F directions that destroy on accel disengage
        public String ownerName; // name of the controlling player (may change on capture)
        public String teamName; // display name of the team/side this ship belongs to
        // Emergency deceleration state (C8.0)
        public boolean decelerating;              // true during the 2-impulse deceleration period
        public int     decelerationEndsAtImpulse; // absolute impulse when ship stops; -1 if not decelerating
        public boolean wildWeaselActive; // true while a WW decoy is on the map for this ship
        public int wwEcmBonus; // +6 while WW is active (J3.23), else 0
        // Active Fire Control state (D6.6)
        public boolean fireControlActivating; // true during 4-impulse countdown to going active
        public int     fcActivatingUntil;     // absolute impulse when activation completes; -1 if not activating
        public boolean fcPaidThisTurn;        // true if FC energy was allocated this turn
    }

    // -------------------------------------------------------------------------
    // Shuttle
    // -------------------------------------------------------------------------

    public static class ShuttleDto extends MapObjectDto {
        public int facing;
        public int speed;
        public int maxSpeed;
        public String parentPlayer; // name of the player who owns this shuttle
        public String parentShipName; // name of the ship that launched this shuttle
        public List<WeaponDto> weapons; // non-null for fighters; null for plain shuttles
        public boolean crippled; // true if crippling effects have been applied (J1.33)
        public boolean hetUsed; // fighters only: true if tactical maneuver used this turn
    }

    // -------------------------------------------------------------------------
    // Suicide shuttle (seeker)
    // -------------------------------------------------------------------------

    public static class SuicideShuttleDto extends MapObjectDto {
        public int facing;
        public int speed;
        public String controllerFaction;
        public String controllerName; // name of the controlling ship
        public String targetName;
        public int warheadDamage; // totalEnergy * 2
        public int armingTurnsComplete;
        public boolean isIdentified;
    }

    // -------------------------------------------------------------------------
    // Scatter pack (seeker — moves toward target, releases drones after 8 impulses)
    // -------------------------------------------------------------------------

    public static class ScatterPackDto extends MapObjectDto {
        public int facing;
        public int speed;
        public String controllerFaction;
        public String controllerName;
        public String targetName;
        public List<String> payload; // drone type names still loaded; empty after release
        public boolean released; // true after drones have been deployed
        public boolean isIdentified;
    }

    // -------------------------------------------------------------------------
    // Drone
    // -------------------------------------------------------------------------

    public static class DroneDto extends MapObjectDto {
        public int facing;
        public int speed;
        public String droneType; // "I", "II", etc. — revealed on identification
        public int warheadDamage; // revealed on identification
        public int hull; // current hull remaining
        public int damageTaken; // maxHull - hull — always public (visible on the drone)
        public int maxHull; // hull at launch (from DroneType) — revealed on identification
        public int endurance; // revealed on identification
        public String targetName; // revealed on identification
        public String controllerFaction;
        public String controllerName; // name of the controlling ship — always public
        public String launcherName; // name of the ship that originally launched this drone (stable, even when
                                    // inert)
        public int launchImpulse; // always public
        public boolean isIdentified; // true once identified by an enemy
    }

    // -------------------------------------------------------------------------
    // Plasma torpedo
    // -------------------------------------------------------------------------

    public static class PlasmaTorpedoDto extends MapObjectDto {
        public int facing;
        public int speed;
        public int currentStrength; // always public
        public String controllerFaction;
        public String controllerName; // name of the launching ship — always public
        public String plasmaType; // "F", "G", "S", "R" — never revealed to enemy
        public int distanceTraveled;
        public boolean pseudo; // never revealed to enemy
        public double damageTaken;
        public int launchImpulse; // always public
        public String targetName; // revealed on identification
        public boolean isIdentified; // true once identified by an enemy
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

    public int mapCols;
    public int mapRows;
    public int maxTurns;
    public boolean gameOver;
    public String winnerTeam; // null = draw or ongoing
    public String endReason; // human-readable explanation, null if ongoing
    public int turn;
    public int impulse;
    public int absoluteImpulse;
    public String phase;
    public List<String> movableNow;
    public List<String> myShips; // ships owned by the requesting player (null = all ships)
    public boolean awaitingAllocation;
    public List<String> pendingAllocation;      // ship names not yet allocated this turn
    public List<String> pendingAccelDisengage;  // ship names awaiting player YES/NO for C7.1 accel disengage
    public List<MapObjectDto> mapObjects;
    public int readyCount; // players who have clicked Ready this phase
    public int playerCount; // total players in the session
    public List<String> combatLog = new ArrayList<>(); // fire/damage events since last broadcast
    public ScoreboardDto scoreboard; // non-null only when gameOver
    public List<PendingVolleyDto> pendingVolleys = new ArrayList<>(); // incoming fire queued for reinforcement

    public static class PendingVolleyDto {
        public String attackerName;
        public String targetShipName;
        public int    shieldNumber;
        public int    totalDamage;
        public int    envelopingHellboreDamage;
        public boolean addHit;
    }

    // -------------------------------------------------------------------------
    // Scoreboard (S2.21 victory points)
    // -------------------------------------------------------------------------

    public static class ShipVpRowDto {
        public String shipName;
        public String teamName;
        public int gabpv;
        public String status; // "INTACT" | "DAMAGED" | "CRIPPLED" | "DISENGAGED" | "DESTROYED" | "CAPTURED"
        public int vpScored; // VPs scored against this ship by the enemy
    }

    public static class TeamScoreDto {
        public String teamName;
        public int vpScored;
        public int vpAgainst;
        public String levelOfVictory;
    }

    public static class ScoreboardDto {
        public List<ShipVpRowDto> ships = new ArrayList<>();
        public List<TeamScoreDto> teams = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Constructor — builds from live Game state
    // -------------------------------------------------------------------------

    public GameStateDto() {
    }

    public GameStateDto(Game game) {
        this.mapCols = game.getMapCols();
        this.mapRows = game.getMapRows();
        this.maxTurns = game.getMaxTurns();
        Game.GameEndResult end = game.getGameEnd();
        this.gameOver = end != null;
        this.winnerTeam = end != null ? end.winnerTeam() : null;
        this.endReason = end != null ? end.reason() : null;
        if (end != null) {
            Game.Scoreboard sb = game.calculateVictoryPoints();
            ScoreboardDto dto = new ScoreboardDto();
            for (Game.ShipVpRow row : sb.rows()) {
                ShipVpRowDto r = new ShipVpRowDto();
                r.shipName = row.shipName();
                r.teamName = row.teamName();
                r.gabpv = row.gabpv();
                r.status = row.status();
                r.vpScored = row.vpScored();
                dto.ships.add(r);
            }
            for (Game.TeamScore ts : sb.teams()) {
                TeamScoreDto t = new TeamScoreDto();
                t.teamName = ts.teamName();
                t.vpScored = ts.vpScored();
                t.vpAgainst = ts.vpAgainst();
                t.levelOfVictory = ts.levelOfVictory();
                dto.teams.add(t);
            }
            this.scoreboard = dto;
        }
        this.turn = game.getCurrentTurn();
        this.impulse = game.getCurrentImpulse();
        this.absoluteImpulse = game.getAbsoluteImpulse();
        this.phase = game.getCurrentPhase().getLabel();

        this.movableNow = new ArrayList<>();
        for (Ship s : game.getMovableShips())
            movableNow.add(s.getName());
        for (com.sfb.objects.shuttles.Shuttle s : game.getMovableShuttles())
            movableNow.add(s.getName());

        this.awaitingAllocation = game.isAwaitingAllocation();
        this.pendingAllocation = new ArrayList<>();
        if (game.isAwaitingAllocation()) {
            for (Ship s : game.getAllocationQueue())
                pendingAllocation.add(s.getName());
        }
        this.pendingAccelDisengage = new ArrayList<>();
        for (Ship s : game.getPendingAccelDisengage())
            pendingAccelDisengage.add(s.getName());

        this.mapObjects = new ArrayList<>();

        for (Ship ship : game.getShips())
            mapObjects.add(fromShip(ship, game));

        for (com.sfb.objects.shuttles.Shuttle shuttle : game.getActiveShuttles()) {
            if (shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle)
                mapObjects.add(fromWildWeasel((com.sfb.objects.shuttles.WildWeaselShuttle) shuttle));
            else if (shuttle instanceof com.sfb.objects.shuttles.ScatterPack)
                mapObjects.add(fromScatterPack((com.sfb.objects.shuttles.ScatterPack) shuttle));
            else
                mapObjects.add(fromShuttle(shuttle));
        }

        for (Seeker seeker : game.getSeekers()) {
            if (seeker instanceof Drone)
                mapObjects.add(fromDrone((Drone) seeker));
            else if (seeker instanceof PlasmaTorpedo)
                mapObjects.add(fromPlasma((PlasmaTorpedo) seeker));
            else if (seeker instanceof com.sfb.objects.shuttles.SuicideShuttle)
                mapObjects.add(fromSuicideShuttle((com.sfb.objects.shuttles.SuicideShuttle) seeker));
            else if (seeker instanceof com.sfb.objects.shuttles.ScatterPack)
                mapObjects.add(fromScatterPack((com.sfb.objects.shuttles.ScatterPack) seeker));
        }

        for (SpaceMine mine : game.getMines())
            mapObjects.add(fromMine(mine));

        for (Terrain t : game.getTerrain())
            mapObjects.add(fromTerrain(t));

        for (Game.PendingVolley pv : game.getPendingVolleys()) {
            PendingVolleyDto d = new PendingVolleyDto();
            d.attackerName             = pv.attackerName;
            d.targetShipName           = pv.target != null ? pv.target.getName() : "";
            d.shieldNumber             = pv.shieldNumber;
            d.totalDamage              = pv.totalDamage;
            d.envelopingHellboreDamage = pv.envelopingHellboreDamage;
            d.addHit                   = pv.addHit;
            pendingVolleys.add(d);
        }
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private static ShipDto fromShip(Ship ship, Game game) {
        ShipDto dto = new ShipDto();
        dto.name = ship.getName();
        dto.location = ship.getLocation() != null ? ship.getLocation().toString() : null;
        dto.facing = ship.getFacing();
        dto.speed = ship.getSpeed();
        dto.hull = ship.getHullType();
        dto.faction = ship.getFaction() != null ? ship.getFaction().name() : "Federation";

        dto.shields = new ArrayList<>();
        for (int s = 1; s <= 6; s++) {
            ShieldDto sd = new ShieldDto();
            sd.shieldNum = s;
            sd.current = ship.getShields().getShieldStrength(s);
            sd.baseStrength = ship.getShields().getBaseShieldStrength(s);
            sd.max = ship.getShields().getMaxShieldStrength(s);
            sd.active = ship.getShields().isShieldActive(s);
            int toggled = ship.getShields().getImpulseShieldToggled(s);
            int delay = com.sfb.constants.Constants.IMPULSES_PER_TURN / 4;
            sd.impulsesUntilRaiseable = Math.max(0, toggled + delay - TurnTracker.getImpulse());
            dto.shields.add(sd);
        }

        CloakingDevice cloak = ship.getCloakingDevice();
        if (cloak != null) {
            dto.cloakState = cloak.getState().name();
            dto.cloakFadeStep = cloak.getFadeStep(game.getAbsoluteImpulse());
            dto.cloakTransitionImpulse = cloak.getTransitionImpulse();
        } else {
            dto.cloakState = "NONE";
            dto.cloakFadeStep = 0;
            dto.cloakTransitionImpulse = -1;
        }

        dto.phaserCapacitor = ship.getWeapons().getPhaserCapacitorEnergy();
        dto.phaserCapacitorMax = ship.getWeapons().getAvailablePhaserCapacitor();
        dto.capacitorsCharged = ship.isCapacitorsCharged();
        dto.activeFireControl = ship.isActiveFireControl();
        dto.scannerBonus = ship.getSpecialFunctions().getScanner();
        dto.sensorRating = ship.getSpecialFunctions().getSensor();
        dto.ecmAllocated = ship.getEcmAllocated();
        dto.eccmAllocated = ship.getEccmAllocated();
        dto.tBombs = ship.getTBombs();
        dto.dummyTBombs = ship.getDummyTBombs();
        dto.nuclearSpaceMines = ship.getNuclearSpaceMines();
        dto.transporterUses = ship.getTransporters().availableUses();
        dto.boardingParties = ship.getCrew().getAvailableBoardingParties();
        dto.commandos = ship.getCrew().getFriendlyTroops().commandos;
        dto.availableLab = ship.getLabs().getAvailableLab();
        dto.availableCrewUnits = ship.getCrew().getAvailableCrewUnits();
        dto.capturedCrew = ship.getCrew().getCapturedCrew();
        dto.minimumCrew = ship.getCrew().getMinimumCrew();
        dto.availableDeckCrews = ship.getCrew().getAvailableDeckCrews();
        dto.crewQuality = ship.getCrew().getCrewQuality().name();
        dto.availableTransporters = ship.getTransporters().getAvailableTrans();
        dto.totalTransporters = ship.getTransporters().fetchOriginalTotalBoxes();
        dto.transporterEnergyCost = com.sfb.constants.Constants.TRANS_ENERGY;
        dto.availableTractors = ship.getTractors().fetchRemainingTotalBoxes();
        dto.totalTractors = ship.getTractors().fetchOriginalTotalBoxes();

        // Hull box damage state
        com.sfb.systemgroups.HullBoxes hb = ship.getHullBoxes();
        dto.availableFhull = hb.getAvailableFhull();
        dto.availableAhull = hb.getAvailableAhull();
        dto.availableChull = hb.getAvailableChull();
        dto.maxFhull = hb.getMaxFhull();
        dto.maxAhull = hb.getMaxAhull();
        dto.maxChull = hb.getMaxChull();

        // Power system damage state
        com.sfb.systemgroups.PowerSystems ps = ship.getPowerSystems();
        dto.availableLWarp = ps.getAvailableLWarp();
        dto.availableRWarp = ps.getAvailableRWarp();
        dto.availableCWarp = ps.getAvailableCWarp();
        dto.availableImpulse = ps.getAvailableImpulse();
        dto.availableApr = ps.getAvailableApr();
        dto.availableAwr = ps.getAvailableAwr();
        dto.maxLWarp = ps.getMaxLWarp();
        dto.maxRWarp = ps.getMaxRWarp();
        dto.maxCWarp = ps.getMaxCWarp();
        dto.maxImpulse = ps.getMaxImpulse();
        dto.maxApr = ps.getMaxApr();
        dto.maxAwr = ps.getMaxAwr();
        dto.availableBattery = ps.getAvailableBattery();
        dto.batteryPower = ps.getBatteryPower();
        dto.reserveWarp = ps.getReserveWarp();

        // Crew state
        dto.skeleton = ship.getCrew().isSkeleton();

        // HET state
        dto.hetCost = (int) Math.ceil(ship.getPerformanceData().getHetCost());
        dto.hetsThisTurn = ship.getHetsThisTurn();
        dto.lastHetImpulse = ship.getLastHetImpulse();
        dto.immobileUntilImpulse = ship.getImmobileUntilImpulse();

        // Energy Allocation helper fields
        dto.totalPower = ps.getTotalAvailablePower();
        dto.moveCost = ship.getPerformanceData().getMovementCost();
        dto.lifeSupportCost = ship.getLifeSupportCost();
        dto.fireControlCost = ship.getFireControlCost();
        dto.activeShieldCost = ship.getActiveShieldCost();
        dto.minimumShieldCost = ship.getMinimumShieldCost();
        dto.batteryCharge = ps.getBatteryPower();
        dto.cloakCost = cloak != null ? cloak.getPowerToActivate() : 0;
        dto.maxSpeedNextTurn = ship.getMaxAccelerationSpeed();
        dto.commandRating = ship.getCommandRating();
        dto.uimFunctional = ship.getActiveUim(com.sfb.TurnTracker.getImpulse()) != null;
        dto.tokenArt = ship.getTokenArt();
        dto.turnMode = ship.getTurnMode() != null ? ship.getTurnMode().name() : null;
        dto.turnHexes = ship.getTurnHexes();
        dto.hexesUntilTurn = Math.max(0, ship.getTurnHexes() - ship.getTurnCount());
        dto.lockOnTargets = ship.getLockOns().stream()
                .map(com.sfb.objects.Unit::getName)
                .collect(java.util.stream.Collectors.toList());
        dto.captured = ship.isCaptured();
        dto.disengaged = ship.isDisengaged();
        dto.canDisengageBySeparation = game.canDisengageBySeparation(ship);
        dto.destructionDirections = game.getDestructionDirections(ship);
        dto.ownerName = ship.getOwner() != null ? ship.getOwner().getName() : null;
        dto.teamName = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
        dto.decelerating              = ship.isDecelerating();
        dto.decelerationEndsAtImpulse = ship.getDecelerationEndsAtImpulse();
        dto.wildWeaselActive = ship.hasActiveWildWeasel();
        dto.wwEcmBonus = ship.getWwEcmBonus();
        dto.fireControlActivating = ship.isFcActivating();
        dto.fcActivatingUntil     = ship.getFcActivatingUntil();
        dto.fcPaidThisTurn        = ship.isFcPaidThisTurn();

        // Control space damage state
        com.sfb.systemgroups.ControlSpaces cs = ship.getControlSpaces();
        dto.availableBridge = cs.getAvailableBridge();
        dto.maxBridge = cs.getBridge();
        dto.availableFlag = cs.getAvailableFlag();
        dto.maxFlag = cs.getFlag();
        dto.availableEmer = cs.getAvailableEmer();
        dto.maxEmer = cs.getEmer();
        dto.availableAuxcon = cs.getAvailableAuxcon();
        dto.maxAuxcon = cs.getAuxcon();
        dto.availableSecurity = cs.getAvailableSecurity();
        dto.maxSecurity = cs.getSecurity();

        dto.weapons = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            WeaponDto wd = new WeaponDto();
            wd.name = w.getName();
            wd.lastImpulseFired = w.getLastImpulseFired();
            wd.functional = w.isFunctional();
            wd.arcLabel = w.getArcLabel();
            wd.arcMask = w.getArcs();
            boolean armedIfNeeded = !(w instanceof com.sfb.weapons.HeavyWeapon)
                    || ((com.sfb.weapons.HeavyWeapon) w).isArmed();
            wd.readyToFire = w.isFunctional() && armedIfNeeded && w.canFire();
            if (w instanceof com.sfb.weapons.HeavyWeapon) {
                com.sfb.weapons.HeavyWeapon hw = (com.sfb.weapons.HeavyWeapon) w;
                wd.armed = hw.isArmed();
                wd.armingTurn = hw.getArmingTurn();
                wd.armingType = hw.getArmingType() != null ? hw.getArmingType().name() : null;
                wd.isHeavy = true;
                wd.armingCost = hw.energyToArm();
                wd.holdCost = hw.holdEnergyCost();
                wd.canOverload = hw.supportsOverload();
                wd.canSuicide = hw.supportsSuicide();
                wd.canProximity = hw.supportsProximity();
                wd.overloadFinalTurnOnly = hw.overloadFinalTurnOnly();
                wd.totalArmingTurns = hw.totalArmingTurns();
            }
            if (w instanceof com.sfb.weapons.PlasmaLauncher) {
                com.sfb.weapons.PlasmaLauncher pl = (com.sfb.weapons.PlasmaLauncher) w;
                wd.plasmaType = pl.getPlasmaType() != null ? pl.getPlasmaType().name() : null;
                wd.launcherType = pl.getLauncherType() != null ? pl.getLauncherType().name() : null;
                wd.pseudoPlasmaReady = pl.isPseudoPlasmaReady();
                wd.isRolling = pl.isRolling();
                wd.rollingCost = pl.rollingCost();
                wd.canEpt = pl.canEpt();
                wd.eptCost = pl.eptCost();
                wd.launchDirectionsMask = pl.getLaunchDirections();
            }
            wd.maxShotsPerTurn = w.getMaxShotsPerTurn();
            wd.shotsThisTurn = w.getShotsThisTurn();
            wd.minImpulseGap = w.getMinImpulseGap();
            if (w instanceof com.sfb.weapons.FighterFusion) {
                com.sfb.weapons.FighterFusion ff = (com.sfb.weapons.FighterFusion) w;
                wd.chargesRemaining = ff.getChargesRemaining();
                wd.canFireDouble = ff.canFireDouble();
            }
            if (w instanceof com.sfb.weapons.ADD) {
                com.sfb.weapons.ADD add = (com.sfb.weapons.ADD) w;
                wd.addShots = add.getShots();
                wd.addReloads = add.getReloadsAvailable();
                wd.addCapacity = add.getCapacity();
            }
            dto.weapons.add(wd);
        }

        dto.droneRacks = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof DroneRack))
                continue;
            DroneRack rack = (DroneRack) w;
            DroneRackDto rd = new DroneRackDto();
            rd.name = rack.getName();
            rd.functional = rack.isFunctional();
            rd.canFire = rack.canFire();
            rd.drones = new ArrayList<>();
            for (Drone d : rack.getAmmo()) {
                DroneInRackDto dd = new DroneInRackDto();
                dd.droneType = d.getDroneType() != null ? d.getDroneType().toString() : "?";
                dd.warheadDamage = d.getWarheadDamage();
                dd.speed = d.getSpeed();
                dd.endurance = d.getEndurance();
                rd.drones.add(dd);
            }
            rd.reloadCount = rack.getNumberOfReloads();
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
                        e.rackSize = d.getRackSize();
                        e.count = 0;
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
            bd.bayIndex = i;
            bd.canLaunch = bay.canLaunch(game.getAbsoluteImpulse());
            bd.launchTubeCount = bay.getLaunchTubeCount();
            bd.availableTubes = bay.getAvailableTubeCount(game.getAbsoluteImpulse());
            bd.shuttles = new ArrayList<>();
            for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                ShuttleInBayDto sd = new ShuttleInBayDto();
                sd.name = s.getName();
                sd.type = s.getClass().getSimpleName().replace("Shuttle", "").toLowerCase();
                sd.maxSpeed = s.getMaxSpeed();
                sd.canLaunch = bay.canLaunch(s, game.getAbsoluteImpulse());
                if (s instanceof com.sfb.objects.shuttles.SuicideShuttle) {
                    com.sfb.objects.shuttles.SuicideShuttle ss = (com.sfb.objects.shuttles.SuicideShuttle) s;
                    sd.armed = ss.isArmed();
                    sd.armingTurnsComplete = ss.getArmingTurnsComplete();
                    sd.warheadDamage = ss.getWarheadDamage();
                } else if (s instanceof com.sfb.objects.shuttles.AdminShuttle && s.canBecomeWildWeasel()) {
                    com.sfb.objects.shuttles.AdminShuttle admin = (com.sfb.objects.shuttles.AdminShuttle) s;
                    sd.wwChargeCount = admin.getWwChargeCount();
                    sd.wwReady = admin.isWwReady();
                } else if (s instanceof com.sfb.objects.shuttles.ScatterPack) {
                    com.sfb.objects.shuttles.ScatterPack sp = (com.sfb.objects.shuttles.ScatterPack) s;
                    sd.payload = sp.getPayload().stream()
                            .map(d -> d.getDroneType() != null ? d.getDroneType().name() : "Unknown")
                            .collect(java.util.stream.Collectors.toList());
                    sd.pendingPayload = sp.getPendingPayload().stream()
                            .map(d -> d.getDroneType() != null ? d.getDroneType().name() : "Unknown")
                            .collect(java.util.stream.Collectors.toList());
                    sd.maxDroneSpaces = sp.getMaxDroneSpaces();
                    sd.committedSpaces = sp.getPayloadSpaces() + sp.getPendingSpaces();
                }
                bd.shuttles.add(sd);
            }
            dto.shuttleBays.add(bd);
        }

        return dto;
    }

    private static ShuttleDto fromShuttle(com.sfb.objects.shuttles.Shuttle shuttle) {
        ShuttleDto dto = new ShuttleDto();
        dto.name = shuttle.getName();
        dto.location = shuttle.getLocation() != null ? shuttle.getLocation().toString() : null;
        dto.facing = shuttle.getFacing();
        dto.speed = shuttle.getSpeed();
        dto.maxSpeed = shuttle.getMaxSpeed();
        dto.parentPlayer = shuttle.getOwner() != null ? shuttle.getOwner().getName() : null;
        dto.parentShipName = shuttle.getParentShipName();
        dto.crippled = shuttle.isCrippled();
        if (shuttle instanceof com.sfb.objects.shuttles.Fighter) {
            com.sfb.objects.shuttles.Fighter fighter = (com.sfb.objects.shuttles.Fighter) shuttle;
            dto.weapons = buildWeaponDtos(shuttle.getWeapons());
            dto.hetUsed = fighter.isTacticalManeuverUsed();
        }
        return dto;
    }

    private static List<WeaponDto> buildWeaponDtos(com.sfb.systemgroups.Weapons wGroup) {
        List<WeaponDto> list = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : wGroup.fetchAllWeapons()) {
            WeaponDto wd = new WeaponDto();
            wd.name = w.getName();
            wd.lastImpulseFired = w.getLastImpulseFired();
            wd.functional = w.isFunctional();
            wd.arcLabel = w.getArcLabel();
            wd.arcMask = w.getArcs();
            wd.readyToFire = w.isFunctional() && w.canFire();
            wd.maxShotsPerTurn = w.getMaxShotsPerTurn();
            wd.shotsThisTurn = w.getShotsThisTurn();
            wd.minImpulseGap = w.getMinImpulseGap();
            if (w instanceof com.sfb.weapons.FighterFusion) {
                com.sfb.weapons.FighterFusion ff = (com.sfb.weapons.FighterFusion) w;
                wd.chargesRemaining = ff.getChargesRemaining();
                wd.canFireDouble = ff.canFireDouble();
            }
            if (w instanceof com.sfb.weapons.ADD) {
                com.sfb.weapons.ADD add = (com.sfb.weapons.ADD) w;
                wd.addShots = add.getShots();
                wd.addReloads = add.getReloadsAvailable();
                wd.addCapacity = add.getCapacity();
            }
            list.add(wd);
        }
        return list;
    }

    private static SuicideShuttleDto fromSuicideShuttle(com.sfb.objects.shuttles.SuicideShuttle ss) {
        SuicideShuttleDto dto = new SuicideShuttleDto();
        dto.name = ss.getName();
        dto.location = ss.getLocation() != null ? ss.getLocation().toString() : null;
        dto.facing = ss.getFacing();
        dto.speed = ss.getSpeed();
        dto.warheadDamage = ss.getWarheadDamage();
        dto.armingTurnsComplete = ss.getArmingTurnsComplete();
        dto.controllerFaction = controllerFaction(ss.getController());
        dto.controllerName = ss.getController() != null ? ((com.sfb.objects.Unit) ss.getController()).getName() : null;
        dto.targetName = ss.getTarget() != null ? ss.getTarget().getName() : null;
        dto.isIdentified = ss.isIdentified();
        return dto;
    }

    private static ScatterPackDto fromScatterPack(com.sfb.objects.shuttles.ScatterPack pack) {
        ScatterPackDto dto = new ScatterPackDto();
        dto.name = pack.getName();
        dto.location = pack.getLocation() != null ? pack.getLocation().toString() : null;
        dto.facing = pack.getFacing();
        dto.speed = pack.getSpeed();
        dto.payload = pack.getPayload().stream()
                .map(d -> d.getDroneType().name())
                .collect(java.util.stream.Collectors.toList());
        dto.released = pack.isReleased();
        dto.controllerFaction = controllerFaction(pack.getController());
        dto.controllerName = pack.getController() != null ? ((com.sfb.objects.Unit) pack.getController()).getName()
                : null;
        dto.targetName = pack.getTarget() != null ? pack.getTarget().getName() : null;
        dto.isIdentified = pack.isIdentified();
        return dto;
    }

    private static DroneDto fromDrone(Drone drone) {
        DroneDto dto = new DroneDto();
        dto.name = drone.getName();
        dto.location = drone.getLocation() != null ? drone.getLocation().toString() : null;
        dto.facing = drone.getFacing();
        dto.speed = drone.getSpeed();
        dto.droneType = drone.getDroneType() != null ? drone.getDroneType().toString() : "?";
        dto.warheadDamage = drone.getWarheadDamage();
        dto.hull = drone.getHull();
        dto.maxHull = drone.getDroneType() != null ? drone.getDroneType().hull : drone.getHull();
        dto.damageTaken = dto.maxHull - drone.getHull();
        dto.targetName = drone.getTarget() != null ? drone.getTarget().getName() : null;
        dto.controllerFaction = controllerFaction(drone.getController());
        dto.controllerName = drone.getController() != null ? drone.getController().getName() : null;
        dto.launcherName = drone.getLauncherName();
        dto.endurance = drone.getEndurance();
        dto.launchImpulse = drone.getLaunchImpulse();
        dto.isIdentified = drone.isIdentified();
        return dto;
    }

    private static PlasmaTorpedoDto fromPlasma(PlasmaTorpedo torp) {
        PlasmaTorpedoDto dto = new PlasmaTorpedoDto();
        dto.name = torp.getName();
        dto.location = torp.getLocation() != null ? torp.getLocation().toString() : null;
        dto.facing = torp.getFacing();
        dto.speed = torp.getSpeed();
        dto.currentStrength = torp.getCurrentStrength();
        dto.controllerFaction = controllerFaction(torp.getController());
        dto.controllerName = torp.getController() != null ? torp.getController().getName() : null;
        dto.plasmaType = torp.getPlasmaType() != null ? torp.getPlasmaType().name() : null;
        dto.distanceTraveled = torp.getDistanceTraveled();
        dto.pseudo = torp.isPseudoPlasma();
        dto.damageTaken = torp.getDamageTaken();
        dto.launchImpulse = torp.getLaunchImpulse();
        dto.targetName = torp.getTarget() != null ? torp.getTarget().getName() : null;
        dto.isIdentified = torp.isIdentified();
        return dto;
    }

    private static WildWeaselDto fromWildWeasel(com.sfb.objects.shuttles.WildWeaselShuttle ww) {
        WildWeaselDto dto = new WildWeaselDto();
        dto.name = ww.getName();
        dto.location = ww.getLocation() != null ? ww.getLocation().toString() : null;
        dto.facing = ww.getFacing();
        dto.speed = ww.getSpeed();
        dto.parentShipName = ww.getParentShipName();
        dto.parentPlayer = ww.getOwner() != null ? ww.getOwner().getName() : null;
        dto.exploding     = ww.isExploding();
        dto.postExplosion = ww.isPostExplosion();
        return dto;
    }

    private static TerrainDto fromTerrain(Terrain t) {
        TerrainDto dto = new TerrainDto();
        dto.name = t.getName();
        dto.location = t.getLocation() != null ? t.getLocation().toString() : null;
        dto.terrainType = t.getTerrainType().name();
        return dto;
    }

    private static MineDto fromMine(SpaceMine mine) {
        MineDto dto = new MineDto();
        dto.name = mine.getName();
        dto.location = mine.getLocation() != null ? mine.getLocation().toString() : null;
        dto.active = mine.isActive();
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
