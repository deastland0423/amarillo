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
 * MapObjectDto (type, name, location) ← mirrors Marker
 * ShipDto (shipType, faction, shields, cloak)
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
            @JsonSubTypes.Type(value = ObjectiveDto.class, name = "OBJECTIVE"),
            @JsonSubTypes.Type(value = WildWeaselDto.class, name = "WILD_WEASEL"),
    })
    public static abstract class MapObjectDto {
        public String name;
        public String location; // "<x|y>" or null if off-map
        /**
         * Name of the ship holding this in a tractor beam, else null. On the base object
         * because a beam can hold anything: a shuttle, a drone, a plasma torpedo, a probe
         * canister. Ships report the same fact as {@code tractoredByName} and are drawn by
         * their own pass, so they leave this null and are not drawn twice.
         */
        public String tractoredBy;
    }

    public static class TerrainDto extends MapObjectDto {
        public String terrainType; // "ASTEROID" | "PLANET" | "GAS_GIANT"
        public int radius;         // footprint radius in hexes (0 = single hex)
        public String tokenArt;    // optional per-instance counter art (null → per-type default)
        public int[][] rings;      // planetary ring bands as {inner, outer} hex-distance pairs (P2.223)
    }

    public static class ObjectiveDto extends MapObjectDto {
        public String carrierName;           // null when free on the map; else the carrying ship
        public java.util.List<String> retrieval; // permitted retrieval methods
        public String ownerTeam;             // current controlling team (secured owner, or carrier's), else null
        public boolean secured;              // carried off a valid edge — permanent, out of play
        public boolean beingRecovered;       // J1.621 rotation pull-in underway
        public int side;                     // planet hex side 1..6 it sits on, or 0 (SH50.46)
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
        public String designator;
        /**
         * Null means NOT DISCLOSED, which is what an enemy sees: whether a heavy weapon is
         * armed, and how, is the thing a player most wants to hide. A primitive would have
         * reported every enemy weapon as unarmed, trading a leak for a lie.
         */
        public Boolean armed;
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
        public boolean photonTube;   // photon: dialled by energy, not by mode (E4.21/E4.411)
        public double armingEnergy;  // photon: warp energy already in the tube (E4.413)
        public int holdCost; // energy to hold per turn; 0 = hold not supported
        public boolean canOverload; // weapon supports OVERLOAD mode
        public boolean canSuicide; // weapon supports SPECIAL/SUICIDE mode (Fusion only)
        public boolean cooldown; // Fusion only: fired last turn → cannot arm/fire this turn (E7.x)
        // ESG generator (G23.0)
        // Scout function channel (G24.0)
        public boolean scoutChannel;   // true if this "weapon" is a scout channel / special sensor
        public boolean channelPowered;   // powered this turn (G24.14)
        public boolean channelBlinded;   // blinded by weapons fire this impulse (G24.13)
        public String  channelLendTarget; // unit this channel is lending EW to, or null (G24.21)
        public int     channelLentEcm;    // ECM points this channel is lending (G24.21)
        public int     channelLentEccm;   // ECCM points this channel is lending (G24.21)
        public String  channelFunction;   // this turn's committed function: NONE/LEND_EW/BREAK_LOCKON/IDENTIFY (G24.12)
        public int     channelBreakAttempts;    // break-lock-on attempts spent this turn (G24.221)
        public int     channelIdentifyAttempts; // identify attempts spent this turn (G24.251)
        public String  channelAttractedDrone;   // drone this channel drew onto the scout (G24.231)
        public boolean esg;            // true if this weapon is an ESG
        public boolean esgHasCapacitor;// G23.24 capacitor: holds up to 7, releases a chosen 1–5
        public int esgStoredEnergy;    // energy held in the generator (0–maxStorage)
        public int esgMaxEnergy;       // storage cap: 7 with a capacitor, else 5
        public boolean esgActive;      // a field is currently up
        public int esgRadius;          // active field radius (0–3); -1 hidden from opponents while announced
        public int esgStrength;        // active field strength; 0 when hidden (always secret to opponents)
        public boolean esgAnnounced;   // a release is announced but not yet formed (G23.31) — public
        public int esgReleaseIn;       // impulses until the announced field forms (drives the map glow)
        public boolean canProximity; // weapon supports PROXIMITY (prox) mode (Photon only)
        public boolean overloadFinalTurnOnly; // OVERLOAD only choosable on the final arming turn
        public int totalArmingTurns; // turns to fully arm (0 for instant)
        public boolean isRolling; // PlasmaLauncher only: currently in rolling mode
        public int rollingCost; // PlasmaLauncher only: energy to keep rolling (always sent for plasma)
        public boolean canEpt; // PlasmaLauncher only: can fire as Enveloping Plasma Torpedo
        public boolean canFastLoad; // PlasmaLauncher only: FP1.93 fast-load eligible (G/S/R on turn 2)
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
        /**
         * The speed a launch is ACTUALLY capped at — maxSpeed less any point given to
         * erratic maneuvers (C10.13). The launch uses this, so anything showing the raw
         * maximum would promise a speed the game then quietly refuses.
         */
        public int effectiveMaxSpeed;
        public boolean canLaunch; // true if hatch or tube is available for this shuttle right now
        // suicide only: arming has BEGUN (D12.123 counts a part-armed shuttle as armed).
        // Not the same as fully armed at three turns, which is what decides whether it owes
        // the 1-point hold — read armingTurnsComplete for that.
        public boolean armed;
        public int armingTurnsComplete; // suicide only: 0-3
        public int lastArmingEnergy; // suicide only: energy paid on the most recent arming turn
        public int warheadDamage; // suicide only: totalEnergy * 2
        public List<String> payload; // scatterpack only: live drone type names (e.g. "TypeIM")
        public List<String> pendingPayload; // scatterpack only: drones staged for end-of-turn loading
        public int maxDroneSpaces; // scatterpack only: max rack spaces (default 6)
        public double committedSpaces; // scatterpack only: payload + pending spaces already used
        /**
         * The special role this shuttle is prepared for, or null. Sent so the launch list
         * can leave it out: a prepared shuttle cannot launch as an ordinary one.
         */
        public String specialRole;
        public int wwChargeCount; // admin only: 0=uncharged, 1=primed, 2=ready to launch
        public boolean wwReady; // admin only: true when wwChargeCount >= 2
    }

    public static class ShuttleSpaceDto {
        public int spaceIndex;
        public boolean destroyed;
        public boolean empty;
        public boolean armed;
        public ShuttleInBayDto shuttle; // null if empty or destroyed
    }

    public static class ShuttleBayDto {
        public int bayIndex;
        public boolean canLaunch;
        public int launchTubeCount;
        public int availableTubes;
        public int totalSpaces;
        public int destroyedSpaces;
        public int emptySpaces;
        public List<ShuttleInBayDto> shuttles; // occupied spaces only (for launch UI)
        public List<ShuttleSpaceDto> spaces; // all spaces (for DAC damage UI)
    }

    public static class ShipDto extends MapObjectDto {
        /** The SSD Type line, e.g. "CA+". Named shipType because Jackson owns "type" here. */
        public String shipType;
        public String faction;
        public int facing;
        public int speed;
        public int tractorTrueSpeed; // plotted speed before tractor pseudo-speed (G7.34); -1 = not limited
        public List<ShieldDto> shields;
        public String cloakState;
        public int cloakFadeStep;
        public int cloakTransitionImpulse;
        public double phaserCapacitor;
        public double phaserCapacitorMax;
        public boolean capacitorsCharged;
        public boolean activeFireControl;
        public boolean usingEm;      // Erratic Maneuvers in force (C10.0)
        public double erraticCost;   // what EM costs this ship (C10.11/C10.12); 0 = cannot
        public boolean paidForEm;    // bought EM at allocation, so it may be announced (C10.11)
        public boolean emPending;    // announced this impulse, in force at its end (C10.311)
        public int scannerBonus;
        public int sensorRating;
        public int ecmAllocated;
        public int eccmAllocated;
        // EW lending is announced as it happens and is never secret (G24.211 note, G24.2115),
        // so these are sent for every ship, not just the viewer's own.
        // What this turn's allocation quietly cost — a photon tube left unfunded is discharged
        // (E4.21/E4.22). Allocation is secret, so this is sent only to the ship's own player.
        public List<String> allocationNotes = new ArrayList<>();
        /** COI selections that could not be applied. Owner-only, like allocation notes. */
        public List<String> setupNotes = new ArrayList<>();
        public int lentEcm;          // ECM received from friendly scouts (D6.3144)
        public int lentEccm;         // ECCM received from friendly scouts (D6.3144)
        public int offensiveEw;      // enemy jamming imposed on this ship's own fire (G24.219)
        /** Everything jamming fire AT this ship: generated + lent (weasel included) + built-in. */
        public int ecmTotal;
        /** Everything this ship can burn through with: generated + lent. */
        public int eccmTotal;
        /** Where the ECM comes from, e.g. "2 generated + 6 lent" — public by D6.32. */
        public String ecmSources;
        public boolean leader;       // leader variant (S8.36)
        public boolean escort;       // carrier escort, needs a carrier group (S8.311)
        public boolean trueCarrier;  // fighters count against the force's limit (S8.321)
        public boolean bch;          // heavy battlecruiser; one per fleet (S8.333)
        public int scoutEwPool;      // EW points this scout generated to lend this turn (G24.211)
        public int scoutEwLent;      // of the pool, how many are currently lent out (G24.2111)
        public int scoutEwRemaining; // still available to commit; dropped points are lost (G24.2122)
        public List<WeaponDto> weapons;
        public List<DroneRackDto> droneRacks;
        public List<ShuttleBayDto> shuttleBays;
        public int tBombs;
        public int dummyTBombs;
        public int nuclearSpaceMines;
        public int transporterUses;
        public int boardingParties;
        public int commandos;
        public int availableLab;   // boxes free to take a job THIS impulse (G4.22, G4.451)
        public int functioningLab; // boxes that exist at all; the difference is cooling off
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
        public boolean canDoubleEngines; // Orion engine doubling available (G15.2)
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
        public boolean decelerating; // true during the 2-impulse deceleration period
        public int decelerationEndsAtImpulse; // absolute impulse when ship stops; -1 if not decelerating
        public boolean wildWeaselActive; // true while a WW decoy is on the map for this ship
        public int wwEcmBonus; // +6 while WW is active (J3.23), else 0
        // Tractor beam state (G7.0)
        public boolean tractored; // true if held in another ship's tractor beam
        public String tractoredByName; // name of the holding ship, or null
        public int tractorEnergy; // total tractor energy allocated in EA this turn
        public int tractorEnergyRemaining; // unspent tractor pool energy
        public int negativeTractorAccumulated; // cumulative negative-tractor spent this turn (G7.35)
        public java.util.List<String> tractoredTargetNames; // names of ships this ship is currently tractoring
        // Active Fire Control state (D6.6)
        public boolean fireControlActivating; // true during 4-impulse countdown to going active
        public int fcActivatingUntil; // absolute impulse when activation completes; -1 if not activating
        public boolean fcPaidThisTurn; // true if FC energy was allocated this turn
        // Tactical Maneuvers (C5.0)
        public int tacAvailable; // earned warp TACs ready to use (0 or 1)
        public int tacBudget; // warp TACs still to be earned this turn
        public boolean sublightTacAvailable; // true if sublight TAC paid and not yet used
    }

    // -------------------------------------------------------------------------
    // Shuttle
    // -------------------------------------------------------------------------

    public static class ShuttleDto extends MapObjectDto {
        public boolean beingRecovered;
        public int facing;
        public int speed;
        public int maxSpeed;
        public int effectiveMaxSpeed;      // after any point given to EM (C10.13)
        public boolean usingEm;            // Erratic Maneuvers in force (C10.0)
        public boolean emSpeedCommitted;   // the point of speed is spent for the turn (C10.131)
        public boolean isFighter;          // a fighter, as opposed to an admin/other shuttle
        /** What the craft IS — "Admin Shuttle", "General Assault Shuttle". Never the role. */
        public String shuttleTypeName;
        public String parentPlayer; // name of the player who owns this shuttle
        public String parentShipName; // name of the ship that launched this shuttle
        public List<WeaponDto> weapons; // non-null for fighters; null for plain shuttles
        public boolean crippled; // true if crippling effects have been applied (J1.33)
        // Damage. Public to everyone: hits on a shuttle are there to see, and unlike a
        // drone the hull behind them gives nothing away — the craft type is public too
        // ("Admin Shuttle"), so its hull was never a secret.
        public int hull;         // undamaged hull remaining
        public int maxHull;      // hull the craft starts with
        public int damageTaken;
        public int launchImpulse; // when it left the bay; a launch is watched by everyone
        public boolean hetUsed; // fighters only: true if tactical maneuver used this turn
        // Planet landing (P2.4) + cargo hold, for the surface-cargo UI
        public String landingPhase;     // NONE | DESCENDING | LANDED | CLIMBING
        public int landedHexSide;       // 1..6 (A..F) when on a planet, else 0
        public int holdCrew;            // crew units currently in the hold
        public int holdSpacesUsed;      // personnel spaces occupied
        public int personnelCapacity;   // personnel-space capacity of the hold
        public boolean isIdentified;    // true once an enemy lab or scout identified it (G4.2)
        /**
         * G4.233: a successful identification reveals whether the shuttle is following a
         * seeking course and, if it is, its target — and NOTHING about drones aboard
         * or a suicide bomb. So this pair is all an enemy ever learns about a suicide
         * shuttle or a loaded scatter pack; it still arrives typed as a plain shuttle.
         */
        public boolean seekingCourse;
        public String seekingTargetName;
        /**
         * Manned or unmanned, the other half of what G4.233 reveals. A Boolean rather than
         * a boolean because here it is FALSE that carries the information: a primitive
         * would report every unidentified shuttle as unmanned to any reader who forgot to
         * check isIdentified first. Null means "not established".
         */
        public Boolean manned;
    }

    // -------------------------------------------------------------------------
    // Suicide shuttle (seeker)
    // -------------------------------------------------------------------------

    /**
     * What a seeking shuttle IS, as opposed to what it is doing.
     *
     * Seen by an enemy, a suicide shuttle and an unreleased scatter pack arrive as a plain
     * ShuttleDto, which carries all of this. Seen by their OWNER they take their own DTOs,
     * which carried only the role — so a player's own pack read "Faction: ?  From: ?" with
     * no hull, while the enemy's view of the same pack was complete.
     *
     * A shared base rather than the same five fields copied into both, so the compiler
     * keeps them in step.
     */
    public static abstract class SeekingShuttleDto extends MapObjectDto {
        public String parentShipName;
        public String parentPlayer;
        public int hull;
        public int maxHull;
        public int damageTaken;
    }

    public static class SuicideShuttleDto extends SeekingShuttleDto {
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

    public static class ScatterPackDto extends SeekingShuttleDto {
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
        public int hull; // current hull remaining — 0 to an enemy, see damageTaken
        public int damageTaken; // always public: hits on a drone are visible
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
    public List<String> pendingAllocation; // ship names not yet allocated this turn
    public List<String> pendingAccelDisengage; // ship names awaiting player YES/NO for C7.1 accel disengage
    public List<MapObjectDto> mapObjects;
    public int readyCount; // players who have clicked Ready this phase
    public int playerCount; // total players in the session
    // Fire declaration round (D6.315) — session-level, injected at broadcast.
    // Only WHO has responded is public; commit contents stay sealed server-side.
    public boolean fireDeclarationOpen;
    public String fireDeclarationCaller;
    public List<String> fireDeclarationResponded = new ArrayList<>();
    public boolean fireDeclarationSpent; // this impulse's round already resolved
    // Launch declaration round (Annex #2, Impulse Activity Segment) — a separate round from
    // the fire one above, with its own call, and the same secrecy: only WHO has answered.
    public boolean activityDeclarationOpen;
    public String activityDeclarationCaller;
    public List<String> activityDeclarationResponded = new ArrayList<>();
    public boolean activityDeclarationSpent;
    public List<String> combatLog = new ArrayList<>(); // fire/damage events since last broadcast
    public ScoreboardDto scoreboard; // live standings, present in every broadcast
    public List<PendingVolleyDto> pendingVolleys = new ArrayList<>(); // incoming fire queued for reinforcement
    public List<PendingDacChoiceDto> pendingDacChoices = new ArrayList<>();
    public List<PendingBlindChoiceDto> pendingBlindChoices = new ArrayList<>();
    public List<PendingAttractChoiceDto> pendingAttractChoices = new ArrayList<>();
    public List<PendingControlOverflowDto> pendingControlOverflows = new ArrayList<>();

    /** A scout is trying to attract an unidentified shuttle; its owner must answer (G24.235). */
    public static class PendingAttractChoiceDto {
        public String shuttleName;
        public String scoutName;
        public String channelDesignator;
        public String ownerShipName;  // the ship that launched it — who gets asked
    }
    public PendingTractorAuctionDto pendingTractorAuction = null;

    public static class PendingTractorAuctionDto {
        public String attackerName;
        public String targetName;
        public int attackerBid; // effective tractor points
        public int rangeMultiplier; // 1 for range 0-1; 2 for range 2; 3 for range 3 (G7.6)
        public int defenderAccumulated; // existing negative-tractor on target (for defender's UI)
        public int defenderMaxBid; // target's remaining pool + battery
    }

    public static class PendingVolleyDto {
        public String attackerName;
        public String targetShipName;
        public int shieldNumber;
        public int totalDamage;
        public int envelopingHellboreDamage;
        public boolean addHit;
    }

    public static class PendingDacChoiceDto {
        public String targetShipName;
        public String dacType; // "phaser" | "drone" | "torp" | "weapon" | "warp"
        public int roll;
        public List<String> options; // weapon names or warp engine ids
    }

    /** One pending scout-channel blind the firing player must assign (G24.13/.131). */
    public static class PendingBlindChoiceDto {
        public String scoutName;
        public List<BlindChannelOptionDto> channels = new ArrayList<>();

        /** A powered channel the player may sacrifice, with its current role so they can decide. */
        public static class BlindChannelOptionDto {
            public String designator;
            public String function;       // NONE / LEND_EW / BREAK_LOCKON / IDENTIFY / OFFENSIVE_EW
            public String target;         // lend / O-EW target, or null
            public int    lentEcm;
            public int    lentEccm;
            public int    breakAttempts;    // of 3 (G24.221)
            public int    identifyAttempts; // of 4 (G24.251)
            public boolean blinded;         // already blinded (still a valid, expendable target)
        }
    }

    public static class PendingControlOverflowDto {
        public String shipName;
        public int overLimitCount; // how many seekers must be released or transferred
        public List<SeekerChoiceDto> seekers = new ArrayList<>();

        public static class SeekerChoiceDto {
            public String name;
            public String label; // e.g. "Drone (Type I)", "Suicide Shuttle"
            public String targetName;
            public List<String> transferOptions = new ArrayList<>(); // allied ships eligible to take control
        }
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
        public int coiSpend; // Commander's Option points this ship bought (awarded to the enemy, S2.20 B)
    }

    public static class TeamScoreDto {
        public String teamName;
        public int vpScored;
        public int vpAgainst;
        public String levelOfVictory;
        public int coiForfeited; // total COI this side handed to the enemy (S2.20 B)
    }

    /**
     * One objective's current control and its point value. The controlling side
     * scores {@code points} at scenario end (generic objective scoring); scenarios
     * with special/conditional scoring are handled separately.
     */
    public static class ObjectiveStandingDto {
        public String name;
        public String ownerTeam;   // controlling team, or null if free/unclaimed
        public String state;       // "SECURED" | "CARRIED" | "FREE"
        public int    points;      // VP its controller scores at scenario end (0 = none)
    }

    public static class ScoreboardDto {
        public List<ShipVpRowDto> ships = new ArrayList<>();
        public List<TeamScoreDto> teams = new ArrayList<>();
        public List<ObjectiveStandingDto> objectives = new ArrayList<>();
    }

    // -------------------------------------------------------------------------
    // Constructor — builds from live Game state
    // -------------------------------------------------------------------------

    public GameStateDto() {
    }

    /** Omniscient snapshot — solo/unassigned (dev) mode only. */
    public GameStateDto(Game game) {
        this(game, null);
    }

    /**
     * Snapshot through one player's eyes. {@code viewerTeam} null = see
     * everything. For enemy units the builder hides what the tabletop hides:
     * seeking shuttles (suicide shuttles, unreleased scatter packs) render as
     * plain shuttles until identified, plasma keeps its type/pseudo/target
     * secret until identified, drone types stay unknown until identified, and
     * enemy bay contents are not sent at all. Wild Weasels are public — their
     * interference announces them the moment they launch.
     */
    public GameStateDto(Game game, String viewerTeam) {
        this.mapCols = game.getMapCols();
        this.mapRows = game.getMapRows();
        this.maxTurns = game.getMaxTurns();
        Game.GameEndResult end = game.getGameEnd();
        this.gameOver = end != null;
        this.winnerTeam = end != null ? end.winnerTeam() : null;
        this.endReason = end != null ? end.reason() : null;
        {
            // Live standings (S2.21): computed every broadcast so players can
            // check the current score mid-battle, not only at game end.
            Game.Scoreboard sb = game.calculateVictoryPoints();
            ScoreboardDto dto = new ScoreboardDto();
            for (Game.ShipVpRow row : sb.rows()) {
                ShipVpRowDto r = new ShipVpRowDto();
                r.shipName = row.shipName();
                r.teamName = row.teamName();
                r.gabpv = row.gabpv();
                r.status = row.status();
                r.vpScored = row.vpScored();
                r.coiSpend = row.coiSpend();
                dto.ships.add(r);
            }
            for (Game.TeamScore ts : sb.teams()) {
                TeamScoreDto t = new TeamScoreDto();
                t.teamName = ts.teamName();
                t.vpScored = ts.vpScored();
                t.vpAgainst = ts.vpAgainst();
                t.levelOfVictory = ts.levelOfVictory();
                t.coiForfeited = ts.coiForfeited();
                dto.teams.add(t);
            }
            // Objective control + point value (the controller scores `points` at end)
            for (com.sfb.objects.Objective o : game.getObjectives()) {
                ObjectiveStandingDto os = new ObjectiveStandingDto();
                os.name = o.getName();
                com.sfb.Player owner = o.getCurrentOwner();
                os.ownerTeam = owner != null ? owner.getTeamName() : null;
                os.state = o.isSecured() ? "SECURED" : o.isCarried() ? "CARRIED" : "FREE";
                os.points = o.getPoints();
                dto.objectives.add(os);
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
            mapObjects.add(fromShip(ship, game, hiddenFrom(viewerTeam, ship.getOwner())));

        for (com.sfb.objects.shuttles.Shuttle shuttle : game.getActiveShuttles()) {
            if (shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle)
                // Public by rule: a weasel's interference announces it at launch
                mapObjects.add(fromWildWeasel((com.sfb.objects.shuttles.WildWeaselShuttle) shuttle));
            else if (shuttle instanceof com.sfb.objects.shuttles.ScatterPack)
                // Only released packs live here — the release was visible to all
                mapObjects.add(fromScatterPack((com.sfb.objects.shuttles.ScatterPack) shuttle));
            else
                mapObjects.add(fromShuttle(shuttle, hiddenFrom(viewerTeam, shuttle.getOwner())));
        }

        for (Seeker seeker : game.getSeekers()) {
            if (seeker instanceof Drone) {
                Drone d = (Drone) seeker;
                mapObjects.add(fromDrone(d,
                        hiddenFrom(viewerTeam, ownerOfController(d.getController())) && !d.isIdentified()));
            } else if (seeker instanceof PlasmaTorpedo) {
                PlasmaTorpedo torp = (PlasmaTorpedo) seeker;
                mapObjects.add(fromPlasma(torp,
                        hiddenFrom(viewerTeam, ownerOfController(torp.getController())),
                        torp.isIdentified()));
            } else if (seeker instanceof com.sfb.objects.shuttles.SuicideShuttle) {
                com.sfb.objects.shuttles.SuicideShuttle ss = (com.sfb.objects.shuttles.SuicideShuttle) seeker;
                // G4.233: identification does NOT reveal a suicide bomb, so an enemy sees a
                // plain shuttle whether or not it has been identified — with the seeking
                // course and target added once it has. This used to open the whole DTO on
                // identification, handing over the warhead and the arming turns.
                if (hiddenFrom(viewerTeam, ss.getOwner()))
                    mapObjects.add(fromShuttle(ss, true));
                else
                    mapObjects.add(fromSuicideShuttle(ss));
            } else if (seeker instanceof com.sfb.objects.shuttles.ScatterPack) {
                com.sfb.objects.shuttles.ScatterPack pack = (com.sfb.objects.shuttles.ScatterPack) seeker;
                // G4.233 again: "not if it is carrying drones". Releasing them is what makes
                // a pack public, not being identified.
                if (hiddenFrom(viewerTeam, pack.getOwner()) && !pack.isReleased())
                    mapObjects.add(fromShuttle(pack, true));
                else
                    mapObjects.add(fromScatterPack(pack));
            }
        }

        for (SpaceMine mine : game.getMines())
            mapObjects.add(fromMine(mine));

        for (Terrain t : game.getTerrain())
            mapObjects.add(fromTerrain(t));

        for (com.sfb.objects.Objective o : game.getObjectives())
            mapObjects.add(fromObjective(o));

        // Aggregate volleys by (target, shieldNumber) so the reinforcement dialog
        // shows the combined incoming total per shield facing. EPT volleys
        // (envelopingTorp != null) always stay separate. Enveloping Hellbore
        // damage is summed for display but applied separately (E10.43).
        java.util.LinkedHashMap<String, PendingVolleyDto> volleyMap = new java.util.LinkedHashMap<>();
        for (Game.PendingVolley pv : game.getPendingVolleys()) {
            String targetName = pv.target != null ? pv.target.getName() : "";
            if (pv.envelopingTorp != null) {
                // EPT: always a distinct entry
                PendingVolleyDto d = new PendingVolleyDto();
                d.attackerName = pv.attackerName;
                d.targetShipName = targetName;
                d.shieldNumber = pv.shieldNumber;
                d.totalDamage = pv.totalDamage;
                d.envelopingHellboreDamage = pv.envelopingHellboreDamage;
                d.addHit = pv.addHit;
                pendingVolleys.add(d);
                continue;
            }
            String key = targetName + ":" + pv.shieldNumber;
            PendingVolleyDto existing = volleyMap.get(key);
            if (existing == null) {
                PendingVolleyDto d = new PendingVolleyDto();
                d.attackerName = pv.attackerName;
                d.targetShipName = targetName;
                d.shieldNumber = pv.shieldNumber;
                d.totalDamage = pv.totalDamage;
                d.envelopingHellboreDamage = pv.envelopingHellboreDamage;
                d.addHit = pv.addHit;
                volleyMap.put(key, d);
            } else {
                existing.totalDamage += pv.totalDamage;
                existing.envelopingHellboreDamage += pv.envelopingHellboreDamage;
                existing.addHit = existing.addHit || pv.addHit;
                if (!existing.attackerName.contains(pv.attackerName))
                    existing.attackerName += ", " + pv.attackerName;
            }
        }
        pendingVolleys.addAll(volleyMap.values());

        for (Game.PendingDacChoice dc : game.getPendingDacChoices()) {
            PendingDacChoiceDto d = new PendingDacChoiceDto();
            d.targetShipName = dc.targetShipName;
            d.dacType = dc.dacType;
            d.roll = dc.roll;
            d.options = new ArrayList<>(dc.options);
            pendingDacChoices.add(d);
        }

        // Surface the first pending blind choice (they resolve one at a time). The options are the
        // scout's currently-unblinded powered channels — you can't double-blind (G24.131) — each
        // with its role so the player can pick which is most expendable.
        for (Game.PendingBlindChoice bc : game.getPendingBlindChoices()) {
            com.sfb.objects.Ship s = game.getShips().stream()
                    .filter(sh -> sh.getName().equals(bc.scoutName)).findFirst().orElse(null);
            if (s == null) break;
            PendingBlindChoiceDto d = new PendingBlindChoiceDto();
            d.scoutName = bc.scoutName;
            for (com.sfb.weapons.ScoutChannel c : game.unblindedPoweredChannels(s)) {
                PendingBlindChoiceDto.BlindChannelOptionDto o = new PendingBlindChoiceDto.BlindChannelOptionDto();
                o.designator = c.getDesignator();
                o.function = c.getTurnFunction().name();
                o.target = c.getLendTarget();
                o.lentEcm = c.getLentEcm();
                o.lentEccm = c.getLentEccm();
                o.breakAttempts = c.getBreakAttempts();
                o.identifyAttempts = c.getIdentifyAttempts();
                o.blinded = false; // options are unblinded by definition
                d.channels.add(o);
            }
            pendingBlindChoices.add(d);
            break; // one at a time
        }

        // G24.235: only the shuttle's owner is asked, and the answer is theirs to lie about.
        for (Game.PendingAttractChoice ac : game.getPendingAttractChoices()) {
            PendingAttractChoiceDto d = new PendingAttractChoiceDto();
            d.shuttleName = ac.shuttleName;
            d.scoutName = ac.scoutName;
            d.channelDesignator = ac.channelDesignator;
            d.ownerShipName = game.getActiveShuttles().stream()
                    .filter(sh -> sh.getName().equals(ac.shuttleName))
                    .map(com.sfb.objects.shuttles.Shuttle::getParentShipName)
                    .findFirst().orElse(null);
            pendingAttractChoices.add(d);
            break; // one at a time
        }

        for (Game.PendingControlOverflow ov : game.getPendingControlOverflows()) {
            com.sfb.objects.Ship ovShip = ov.ship;
            PendingControlOverflowDto dto = new PendingControlOverflowDto();
            dto.shipName = ovShip.getName();
            dto.overLimitCount = ovShip.getControlUsed() - ovShip.getControlCapacity();
            for (com.sfb.objects.Seeker s : ovShip.getControlledSeekers()) {
                PendingControlOverflowDto.SeekerChoiceDto sc = new PendingControlOverflowDto.SeekerChoiceDto();
                if (s instanceof com.sfb.objects.Unit)
                    sc.name = ((com.sfb.objects.Unit) s).getName();
                sc.label = seekerLabel(s);
                com.sfb.objects.Unit target = s.getTarget();
                sc.targetName = target != null ? target.getName() : null;
                // Allied ships that can accept control (lock-on + spare capacity)
                if (target != null) {
                    for (com.sfb.objects.Ship ally : game.getShips()) {
                        if (ally == ovShip)
                            continue;
                        if (!game.isSameTeam(ovShip, ally))
                            continue;
                        if (!ally.hasLockOn(target))
                            continue;
                        if (ally.getControlUsed() >= ally.getControlCapacity())
                            continue;
                        sc.transferOptions.add(ally.getName());
                    }
                }
                dto.seekers.add(sc);
            }
            pendingControlOverflows.add(dto);
        }

        Game.PendingTractorAuction pta = game.getPendingTractorAuction();
        if (pta != null) {
            PendingTractorAuctionDto d = new PendingTractorAuctionDto();
            Ship ptaTarget = (Ship) pta.target; // auction only created for Ship targets
            d.attackerName = pta.attacker.getName();
            d.targetName = pta.target.getName();
            d.attackerBid = pta.attackerBid;
            d.rangeMultiplier = pta.rangeMultiplier;
            d.defenderAccumulated = ptaTarget.getTractors().getNegativeTractorAccumulated();
            d.defenderMaxBid = ptaTarget.getTractors().getRemainingTractorEnergy()
                    + ptaTarget.getPowerSystems().getBatteryPower();
            this.pendingTractorAuction = d;
        }
    }

    private static String seekerLabel(com.sfb.objects.Seeker s) {
        if (s instanceof com.sfb.objects.Drone) {
            com.sfb.objects.Drone d = (com.sfb.objects.Drone) s;
            String type = d.getDroneType() != null ? d.getDroneType().toString() : "?";
            return "Drone (Type " + type + ")";
        }
        if (s instanceof com.sfb.objects.shuttles.ScatterPack)
            return "Scatter Pack";
        if (s instanceof com.sfb.objects.shuttles.SuicideShuttle)
            return "Suicide Shuttle";
        return "Seeker";
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    /**
     * True when the viewer must not see this unit's secrets: a real viewer is
     * set, the unit has an owner, and the owner is on a different team.
     * Null viewer = omniscient (solo/dev); unowned units are public.
     */
    /** The ship holding this unit in a beam, or null. */
    private static String holderName(com.sfb.objects.Unit unit) {
        return unit.getTractoringUnit() != null ? unit.getTractoringUnit().getName() : null;
    }

    /** Name a contributing EW source, skipping the ones contributing nothing. */
    private static void appendEwSource(StringBuilder sb, int points, String label) {
        if (points <= 0)
            return;
        if (sb.length() > 0)
            sb.append(" + ");
        sb.append(points).append(' ').append(label);
    }

    private static boolean hiddenFrom(String viewerTeam, com.sfb.Player owner) {
        return viewerTeam != null && owner != null && !viewerTeam.equals(owner.getTeamName());
    }

    /** Owner of a seeker's controller, when the controller is a ship. */
    private static com.sfb.Player ownerOfController(Object controller) {
        return controller instanceof Ship ? ((Ship) controller).getOwner() : null;
    }

    private static ShipDto fromShip(Ship ship, Game game, boolean hideSecrets) {
        ShipDto dto = new ShipDto();
        dto.name = ship.getName();
        dto.location = ship.getLocation() != null ? ship.getLocation().toString() : null;
        dto.facing = ship.getFacing();
        dto.speed = ship.getSpeed();
        dto.tractorTrueSpeed = ship.getTractorTrueSpeed();
        dto.shipType = ship.getType();
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
            sd.impulsesUntilRaiseable = Math.max(0, toggled + delay - game.getAbsoluteImpulse());
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
        dto.usingEm = ship.isUsingEm();
        dto.erraticCost = ship.getPerformanceData().getErraticCost();
        dto.paidForEm = ship.hasPaidForEm();
        dto.emPending = ship.hasPendingEmAnnouncement(game.getAbsoluteImpulse());
        dto.scannerBonus = ship.getSpecialFunctions().getScanner();
        dto.sensorRating = ship.getSpecialFunctions().getSensor();
        dto.ecmAllocated = ship.getEcmAllocated();
        dto.eccmAllocated = ship.getEccmAllocated();
        if (!hideSecrets)
            dto.allocationNotes = new ArrayList<>(ship.getAllocationNotes());
            dto.setupNotes = new ArrayList<>(ship.getSetupNotes());
        dto.leader = ship.isLeader();
        dto.escort = ship.isEscort();
        dto.trueCarrier = ship.isTrueCarrier();
        dto.bch = ship.isBCH();
        dto.lentEcm = ship.getLentEcm();
        // Totals computed here rather than re-added in the UI: the panel used to sum
        // allocated + lent and stop, so a weasel's six points and an Orion's stealth ECM
        // never appeared and the shooter learned of them from the dice log.
        int lentTotal = ship.getLentEcmTotal();   // scouts AND weasel, capped at six (D6.392)
        dto.ecmTotal = ship.getEcmAllocated() + lentTotal + ship.getStealthEcm();
        dto.eccmTotal = ship.getEccmAllocated() + ship.getLentEccm();
        StringBuilder src = new StringBuilder();
        appendEwSource(src, ship.getEcmAllocated(), "generated");
        appendEwSource(src, lentTotal, "lent");
        appendEwSource(src, ship.getStealthEcm(), "stealth");
        dto.ecmSources = src.length() == 0 ? null : src.toString();
        dto.lentEccm = ship.getLentEccm();
        dto.offensiveEw = ship.getOffensiveEw();
        dto.scoutEwPool = ship.getScoutEwPool();
        dto.scoutEwLent = ship.getScoutEwLent();
        dto.scoutEwRemaining = ship.getScoutEwRemaining();
        dto.tBombs = ship.getTBombs();
        dto.dummyTBombs = ship.getDummyTBombs();
        dto.nuclearSpaceMines = ship.getNuclearSpaceMines();
        // Counts batteries as well as banked energy: a ship that allocated nothing can
        // still beam by drawing reserve power (H7.x), so "uses available" must say so or
        // the UI will cap actions the ship could actually perform.
        dto.transporterUses = game.transporterUsesAvailable(ship);
        dto.boardingParties = ship.getCrew().getAvailableBoardingParties();
        dto.commandos = ship.getCrew().getFriendlyTroops().commandos;
        // What the player can actually commit this impulse, not the box count: a lab used
        // late last turn is still cooling off (G4.451).
        dto.availableLab = ship.getLabs().availableLabs(game.getAbsoluteImpulse());
        // Sent so the client can tell "no labs left" from "labs still cooling off", which
        // otherwise look identical and read as a bug.
        dto.functioningLab = ship.getLabs().getFunctioningLabs();
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
        dto.uimFunctional = ship.getActiveUim(game.getAbsoluteImpulse()) != null;
        dto.canDoubleEngines = ship.canDoubleEngines();
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
        dto.decelerating = ship.isDecelerating();
        dto.decelerationEndsAtImpulse = ship.getDecelerationEndsAtImpulse();
        dto.wildWeaselActive = ship.hasActiveWildWeasel();
        dto.wwEcmBonus = ship.getWwEcmBonus();
        dto.tractored = ship.isTractored();
        dto.tractoredByName = ship.isTractored() && ship.getTractoringUnit() != null
                ? ship.getTractoringUnit().getName()
                : null;
        dto.tractorEnergy = ship.getTractors().getTotalTractorEnergy();
        dto.tractorEnergyRemaining = ship.getTractors().getRemainingTractorEnergy();
        dto.negativeTractorAccumulated = ship.getTractors().getNegativeTractorAccumulated();
        dto.tractoredTargetNames = ship.getTractors().getTractoredUnits().stream()
                .map(com.sfb.objects.Unit::getName)
                .collect(java.util.stream.Collectors.toList());
        dto.fireControlActivating = ship.isFcActivating();
        dto.fcActivatingUntil = ship.getFcActivatingUntil();
        dto.fcPaidThisTurn = ship.isFcPaidThisTurn();
        dto.tacAvailable = ship.getTacAvailable();
        dto.tacBudget = ship.getTacBudget();
        dto.sublightTacAvailable = ship.isSublightTacAvailable();

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
            wd.designator = w.getDesignator();
            wd.lastImpulseFired = w.getLastImpulseFired();
            wd.functional = w.isFunctional();
            wd.arcLabel = w.getArcLabel();
            wd.arcMask = w.getArcs();
            boolean armedIfNeeded = !(w instanceof com.sfb.weapons.HeavyWeapon)
                    || ((com.sfb.weapons.HeavyWeapon) w).isArmed();
            wd.readyToFire = w.isFunctional() && armedIfNeeded && w.canFire();
            // Assigned for EVERY weapon, not only the ones that arm. It is a Boolean now,
            // where null means "not disclosed", so leaving it unset on a phaser made the
            // client treat the viewer's own weapons as an enemy's.
            wd.armed = false;
            if (w instanceof com.sfb.weapons.HeavyWeapon) {
                com.sfb.weapons.HeavyWeapon hw = (com.sfb.weapons.HeavyWeapon) w;
                wd.armed = hw.isArmed();
                wd.armingTurn = hw.getArmingTurn();
                wd.armingType = hw.getArmingType() != null ? hw.getArmingType().name() : null;
                wd.isHeavy = true;
                wd.armingCost = hw.energyToArm();
                if (hw instanceof com.sfb.weapons.Photon) {
                    wd.photonTube = true;
                    wd.armingEnergy = ((com.sfb.weapons.Photon) hw).getArmingEnergy();
                }
                wd.holdCost = hw.holdEnergyCost();
                wd.canOverload = hw.supportsOverload();
                wd.canSuicide = hw.supportsSuicide();
                wd.canProximity = hw.supportsProximity();
                wd.overloadFinalTurnOnly = hw.overloadFinalTurnOnly();
                wd.totalArmingTurns = hw.totalArmingTurns();
            }
            if (w instanceof com.sfb.weapons.Fusion) {
                wd.cooldown = ((com.sfb.weapons.Fusion) w).isOnCooldown();
            }
            if (w instanceof com.sfb.weapons.ScoutChannel) {
                com.sfb.weapons.ScoutChannel c = (com.sfb.weapons.ScoutChannel) w;
                wd.scoutChannel = true;
                wd.channelPowered = c.isPowered();
                wd.channelBlinded = c.isBlinded(game.getAbsoluteImpulse());
                wd.channelLendTarget = c.getLendTarget();
                wd.channelLentEcm = c.getLentEcm();
                wd.channelLentEccm = c.getLentEccm();
                wd.channelFunction = c.getTurnFunction().name();
                wd.channelBreakAttempts = c.getBreakAttempts();
                wd.channelIdentifyAttempts = c.getIdentifyAttempts();
                wd.channelAttractedDrone = c.getAttractedDrone();
            }
            if (w instanceof com.sfb.weapons.ESG) {
                com.sfb.weapons.ESG esg = (com.sfb.weapons.ESG) w;
                wd.esg = true;
                wd.esgHasCapacitor = esg.hasCapacitor();
                wd.esgMaxEnergy = esg.maxStorage();
                wd.esgActive = esg.isActive();
                wd.esgAnnounced = esg.isAnnounced();
                wd.esgReleaseIn = esg.announceCountdown(game.getAbsoluteImpulse());
                // G23.46: once a field is ACTIVE its size AND strength are known to every
                // player. Secret to opponents are only the generator's stored/allocated
                // energy and a *pending* announcement's radius (G23.311).
                if (esg.isActive()) {
                    wd.esgRadius = esg.getRadius();
                    wd.esgStrength = esg.getStrength(); // public (G23.46)
                } else {
                    wd.esgRadius = -1;
                    wd.esgStrength = 0;
                }
                if (hideSecrets) {
                    wd.esgStoredEnergy = 0;
                } else {
                    wd.esgStoredEnergy = esg.getStoredEnergy();
                    if (esg.isAnnounced()) {
                        wd.esgRadius = esg.getAnnouncedRadius(); // owner sees where it will form
                    }
                }
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
                wd.canFastLoad = pl.canFastLoad();
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

        // Rack loadouts and bay contents are the owner's secrets — COI drone
        // choices, WW charges, suicide-shuttle arming, scatter-pack payloads.
        // Enemy viewers get empty lists (the SSD itself is public knowledge;
        // what is LOADED is not).
        dto.droneRacks = new ArrayList<>();
        dto.shuttleBays = new ArrayList<>();
        if (hideSecrets) {
            redactForEnemy(dto, ship, game.getAbsoluteImpulse());
            return dto;
        }
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
            bd.totalSpaces = bay.getTotalSpaces();
            bd.destroyedSpaces = bay.getDestroyedSpaces();
            bd.emptySpaces = bay.getEmptySpaceCount();
            bd.shuttles = new ArrayList<>();
            bd.spaces = new ArrayList<>();
            List<com.sfb.systemgroups.ShuttleSpace> baySpaces = bay.getSpaces();
            for (int j = 0; j < baySpaces.size(); j++) {
                com.sfb.systemgroups.ShuttleSpace space = baySpaces.get(j);
                ShuttleSpaceDto spaceDto = new ShuttleSpaceDto();
                spaceDto.spaceIndex = j;
                spaceDto.destroyed = space.isDestroyed();
                spaceDto.empty = space.isEmpty();
                com.sfb.objects.shuttles.Shuttle s = space.getShuttle();
                if (s != null) {
                    ShuttleInBayDto sd = new ShuttleInBayDto();
                    sd.name = s.getName();
                    sd.type = s.getClass().getSimpleName().replace("Shuttle", "").toLowerCase();
                    sd.maxSpeed = s.getMaxSpeed();
                    sd.effectiveMaxSpeed = s.effectiveMaxSpeed();
                    sd.canLaunch = bay.canLaunch(s, game.getAbsoluteImpulse());
                    if (s instanceof com.sfb.objects.shuttles.SuicideShuttle) {
                        com.sfb.objects.shuttles.SuicideShuttle ss = (com.sfb.objects.shuttles.SuicideShuttle) s;
                        sd.armed = ss.isArmed();
                        sd.armingTurnsComplete = ss.getArmingTurnsComplete();
                        sd.lastArmingEnergy = ss.getLastArmingEnergy();
                        sd.warheadDamage = ss.getWarheadDamage();
                    } else if (s instanceof com.sfb.objects.shuttles.ScatterPack) {
                        // BEFORE the weasel branch. A pack built from an admin shuttle keeps
                        // that catalogue type, and canBecomeWildWeasel() reads the catalogue
                        // (J3.18) — so the pack answered TRUE, took the weasel branch, and
                        // never reported its payload. The launch list needs a payload, so a
                        // perfectly good pack could not be launched at all.
                        com.sfb.objects.shuttles.ScatterPack sp = (com.sfb.objects.shuttles.ScatterPack) s;
                        sd.payload = sp.getPayload().stream()
                                .map(d -> d.getDroneType() != null ? d.getDroneType().name() : "Unknown")
                                .collect(java.util.stream.Collectors.toList());
                        sd.pendingPayload = sp.getPendingPayload().stream()
                                .map(d -> d.getDroneType() != null ? d.getDroneType().name() : "Unknown")
                                .collect(java.util.stream.Collectors.toList());
                        sd.maxDroneSpaces = sp.getMaxDroneSpaces();
                        sd.committedSpaces = sp.getPayloadSpaces() + sp.getPendingSpaces();
                    } else if (s.canBecomeWildWeasel()) {
                        sd.wwChargeCount = s.getWwChargeCount();
                        sd.wwReady = s.isWwReady();
                    }
                    sd.specialRole = s.specialRole();
                    spaceDto.armed = s.isArmed();
                    spaceDto.shuttle = sd;
                    bd.shuttles.add(sd);
                }
                bd.spaces.add(spaceDto);
            }
            dto.shuttleBays.add(bd);
        }

        return dto;
    }

    /**
     * @param hideSecrets true when the viewer is not on this shuttle's side. What a shuttle
     *                    carries is not public: G4.233 gives up whether it is manned and
     *                    whether it is seeking, and stops there.
     */
    private static ShuttleDto fromShuttle(com.sfb.objects.shuttles.Shuttle shuttle,
            boolean hideSecrets) {
        ShuttleDto dto = new ShuttleDto();
        dto.name = shuttle.getName();
        dto.location = shuttle.getLocation() != null ? shuttle.getLocation().toString() : null;
        dto.facing = shuttle.getFacing();
        dto.speed = shuttle.getSpeed();
        dto.maxSpeed = shuttle.getMaxSpeed();
        dto.effectiveMaxSpeed = shuttle.effectiveMaxSpeed();   // after any EM commitment
        dto.usingEm = shuttle.isUsingEm();
        dto.emSpeedCommitted = shuttle.isEmSpeedCommitted();
        dto.parentPlayer = shuttle.getOwner() != null ? shuttle.getOwner().getName() : null;
        dto.parentShipName = shuttle.getParentShipName();
        dto.crippled = shuttle.isCrippled();
        dto.hull = shuttle.getCurrentHull();
        dto.maxHull = shuttle.getHull();
        dto.damageTaken = Math.max(0, shuttle.getHull() - shuttle.getCurrentHull());
        dto.launchImpulse = shuttle.getLaunchImpulse();
        dto.tractoredBy = holderName(shuttle);
        // Every shuttle's weapons, not just a fighter's. An admin shuttle builds itself a
        // 360-degree Ph-3, and both core and the fire endpoint have always been willing to
        // fire it - the client simply never heard about it, so the shuttle could not be
        // picked as an attacker and its phaser was unreachable from the game.
        dto.weapons = buildWeaponDtos(shuttle.getWeapons());
        dto.isFighter = shuttle instanceof com.sfb.objects.shuttles.Fighter;
        // The type is a visible property of the craft; the ROLE it is playing is not, and
        // is never sent. The hover used to call every non-fighter an "Admin Shuttle".
        com.sfb.objects.ShuttleCatalog.Entry ce = shuttle.getCatalogType() == null ? null
                : com.sfb.objects.ShuttleCatalog.get(shuttle.getCatalogType());
        dto.shuttleTypeName = ce != null ? ce.name : null;
        if (shuttle instanceof com.sfb.objects.shuttles.Fighter) {
            com.sfb.objects.shuttles.Fighter fighter = (com.sfb.objects.shuttles.Fighter) shuttle;
            dto.hetUsed = fighter.isTacticalManeuverUsed();
        }
        dto.beingRecovered = shuttle.isBeingRecovered();
        dto.landingPhase = shuttle.getLandingPhase().name();
        dto.landedHexSide = shuttle.getLandedHexSide();
        // What is aboard is hidden; how much it COULD carry is a property of the craft,
        // like its speed, and stays public.
        dto.holdCrew = hideSecrets ? 0 : shuttle.getHold().getCrew();
        dto.holdSpacesUsed = hideSecrets ? 0 : shuttle.personnelSpacesUsed();
        dto.personnelCapacity = shuttle.getPersonnelCapacity();
        dto.isIdentified = shuttle.isIdentified();
        if (shuttle.isIdentified())
            // G4.233: "reveals if the shuttle is manned or unmanned".
            dto.manned = shuttle.isManned();
        if (shuttle.isIdentified() && shuttle instanceof Seeker) {
            // G4.233: identification reveals the seeking course and its target (as for a
            // drone, G4.231). It reveals nothing about the payload, which is why an
            // identified suicide shuttle and an identified scatter pack read exactly alike.
            dto.seekingCourse = true;
            com.sfb.objects.Unit t = ((Seeker) shuttle).getTarget();
            dto.seekingTargetName = t != null ? t.getName() : null;
        }
        return dto;
    }

    /**
     * Everything an enemy may not know about a ship, in one place.
     *
     * The rule this enforces: the CLIENT never decides what to hide. It renders what it is
     * given, and a missing value means unknown. Two systems deciding — a partial redaction
     * here and a polite client that declines to display the rest — is how a secret ends up
     * on the wire with only good manners protecting it, which is where this started: an
     * opponent could read whether your disruptors were armed straight out of the DTO.
     *
     * Bay contents, drone rack loads, allocation notes and ESG stored energy are withheld
     * by not being built at all, above. What is left here is the fields that ARE built and
     * then have to be blanked.
     *
     * Public by ruling, and deliberately untouched: shield box strength, every damaged or
     * remaining system box, ECM and ECCM both generated and lent, whether a weapon is
     * destroyed, how often it has fired this turn, and command rating (which decides fleet
     * legality and does nothing in a battle).
     */
    private static void redactForEnemy(ShipDto dto, Ship ship, int absoluteImpulse) {
        // Specific reinforcement is not visible until it absorbs something; the box count
        // is. current carries the reinforcement, baseStrength does not.
        if (dto.shields != null)
            for (ShieldDto sd : dto.shields)
                sd.current = sd.baseStrength;

        // Energy held rather than spent: the CHARGE in the batteries, reserve warp, the
        // phaser capacitors. Note what stays: availableBattery is the count of battery
        // BOXES still undamaged, which is public like every other box on the SSD. A ship
        // with five battery boxes and three points in them shows five and keeps the three.
        dto.batteryCharge = 0;
        dto.batteryPower = 0;
        dto.reserveWarp = 0;
        dto.phaserCapacitor = 0;          // the SSD maximum stays public
        dto.capacitorsCharged = false;

        // Having BOUGHT Erratic Maneuvers is an intention; using them is a manoeuvre
        // everyone can see (C10.11 versus C10.0), so usingEm and the announcement stay.
        dto.paidForEm = false;

        // Mines carried, and how many of them are bluffs.
        dto.tBombs = 0;
        dto.dummyTBombs = 0;
        dto.nuclearSpaceMines = 0;

        // Who he has lock-on to.
        dto.lockOnTargets = new ArrayList<>();

        // Tactical manoeuvre budget, and the availability that would give it away.
        dto.tacBudget = 0;
        dto.tacAvailable = 0;          // an int: earned TACs ready to use
        dto.sublightTacAvailable = false;

        // Transporter uses left this turn are public, because every use is seen: it is
        // the boxes still undamaged, less the uses already made. NOT our owner-side figure,
        // which is min(boxes, energy / cost) and counts battery power — published as-is an
        // opponent could have read the battery state off the transporter count.
        dto.transporterUses = Math.max(0,
            dto.availableTransporters - ship.getTransporters().usesMadeThisTurn(absoluteImpulse));

        // Tractor energy, allocated and unspent. The BOXES are public and so is what they
        // are doing — a beam in operation is plain to see, and a hit-and-run raid can pick
        // out a specific tractor box precisely because its state is known. The energy
        // pool behind them is not.
        dto.tractorEnergy = 0;
        dto.tractorEnergyRemaining = 0;

        // How much lending capacity a scout has left. What it is actually lending, and to
        // whom, is public.
        dto.scoutEwPool = 0;
        dto.scoutEwRemaining = 0;

        // Whether a weapon is armed, and how. Capability stays public — arcs, whether it
        // CAN overload, shots per turn and the like are printed on the SSD.
        if (dto.weapons != null)
            for (WeaponDto wd : dto.weapons) {
                // Only a weapon that ARMS has arming to hide. A phaser has none, and
                // whether it has fired this impulse is public, so blanking its readiness
                // would have concealed something an opponent is entitled to see.
                if (wd.isHeavy) {
                    wd.armed = null;       // null: not disclosed, as opposed to unarmed
                    wd.armingType = null;
                    wd.armingTurn = 0;
                    wd.totalArmingTurns = 0;
                    wd.armingEnergy = 0;
                    wd.readyToFire = false;   // derived from armed, so it cannot be shown
                    wd.plasmaType = null;     // which torpedo is in the tube
                    wd.pseudoPlasmaReady = false;
                    wd.isRolling = false;
                    wd.chargesRemaining = 0;
                }
                // Ammunition remaining, hidden for the same reason drone rack loads are.
                wd.addShots = 0;
                wd.addReloads = 0;      // addCapacity is on the SSD and stays
            }
    }

    private static List<WeaponDto> buildWeaponDtos(com.sfb.systemgroups.Weapons wGroup) {
        List<WeaponDto> list = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : wGroup.fetchAllWeapons()) {
            WeaponDto wd = new WeaponDto();
            wd.name = w.getName();
            wd.designator = w.getDesignator();
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
        describeCraft(dto, ss);
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
        describeCraft(dto, pack);
        return dto;
    }

    /** A drone's hull at launch, which its type decides. */
    private static int maxHullOf(Drone drone) {
        return drone.getDroneType() != null ? drone.getDroneType().hull : drone.getHull();
    }

    /** Which ship launched it, and how battered it is — the same for either role. */
    private static void describeCraft(SeekingShuttleDto dto,
            com.sfb.objects.shuttles.Shuttle shuttle) {
        dto.parentShipName = shuttle.getParentShipName();
        dto.parentPlayer = shuttle.getOwner() != null ? shuttle.getOwner().getName() : null;
        dto.hull = shuttle.getCurrentHull();
        dto.maxHull = shuttle.getHull();
        dto.damageTaken = Math.max(0, shuttle.getHull() - shuttle.getCurrentHull());
    }

    private static DroneDto fromDrone(Drone drone, boolean hideSecrets) {
        DroneDto dto = new DroneDto();
        dto.tractoredBy = holderName(drone);
        dto.name = drone.getName();
        dto.location = drone.getLocation() != null ? drone.getLocation().toString() : null;
        dto.facing = drone.getFacing();
        dto.speed = drone.getSpeed();
        if (hideSecrets) {
            // Type, warhead, and endurance are unknown until identified (labs).
            dto.droneType = "?";
            dto.warheadDamage = 0;
            // Damage taken IS public — hits on a drone are there to see — but the hull
            // behind it is not, and the two together would give the type away: every
            // DroneType has its own hull, so maxHull = hull + damage identifies it. Hence
            // "3 of ?", which is exactly what the client already draws: you know it has
            // taken three, not whether that leaves a Type-I on its last point or a Type-IV
            // with three to go.
            //
            // This used to send damageTaken = 0 and maxHull = the current hull, which made
            // every enemy drone read as untouched.
            dto.damageTaken = maxHullOf(drone) - drone.getHull();
            dto.hull = 0;
            dto.maxHull = 0;
        } else {
            dto.droneType = drone.getDroneType() != null ? drone.getDroneType().toString() : "?";
            dto.warheadDamage = drone.getWarheadDamage();
            dto.hull = drone.getHull();
            dto.maxHull = drone.getDroneType() != null ? drone.getDroneType().hull : drone.getHull();
            dto.damageTaken = dto.maxHull - drone.getHull();
        }
        // G4.231: what a drone is chasing is revealed by identification, not before. The
        // field always said so in its comment and was assigned anyway, so an enemy could
        // read off which ship every drone was aimed at.
        dto.targetName = hideSecrets || drone.getTarget() == null
                ? null : drone.getTarget().getName();
        dto.controllerFaction = controllerFaction(drone.getController());
        dto.controllerName = drone.getController() != null ? drone.getController().getName() : null;
        dto.launcherName = drone.getLauncherName();
        dto.endurance = hideSecrets ? 0 : drone.getEndurance();
        dto.launchImpulse = drone.getLaunchImpulse();
        dto.isIdentified = drone.isIdentified();
        return dto;
    }

    /**
     * @param enemyView  true when the viewer is not on the torpedo's side
     * @param identified true once a lab or scout channel has identified it (G4.2)
     */
    private static PlasmaTorpedoDto fromPlasma(PlasmaTorpedo torp, boolean enemyView, boolean identified) {
        // No tractoredBy: a tractor beam cannot hold a plasma torpedo — it is energy, not
        // a physical object. Ships, shuttles, drones and canisters can all be held.
        PlasmaTorpedoDto dto = new PlasmaTorpedoDto();
        dto.name = torp.getName();
        dto.location = torp.getLocation() != null ? torp.getLocation().toString() : null;
        dto.facing = torp.getFacing();
        dto.speed = torp.getSpeed();
        dto.currentStrength = torp.getCurrentStrength();
        dto.controllerFaction = controllerFaction(torp.getController());
        dto.controllerName = torp.getController() != null ? torp.getController().getName() : null;
        if (enemyView) {
            // G4.232: a lab "can only reveal the target of a plasma torpedo" and cannot
            // distinguish a real torpedo from a pseudo (FP1.4). Identification therefore
            // buys the target and nothing else — type and pseudo status stay hidden even
            // after it, which is what makes a pseudo worth launching. Strength is always
            // known (FP1.32) and is sent above either way.
            dto.plasmaType = "?";
            dto.pseudo = false;
            dto.targetName = identified && torp.getTarget() != null
                    ? torp.getTarget().getName() : null;
        } else {
            dto.plasmaType = torp.getPlasmaType() != null ? torp.getPlasmaType().name() : null;
            dto.pseudo = torp.isPseudoPlasma();
            dto.targetName = torp.getTarget() != null ? torp.getTarget().getName() : null;
        }
        dto.distanceTraveled = torp.getDistanceTraveled();
        dto.damageTaken = torp.getDamageTaken();
        dto.launchImpulse = torp.getLaunchImpulse();
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
        dto.exploding = ww.isExploding();
        dto.postExplosion = ww.isPostExplosion();
        return dto;
    }

    private static TerrainDto fromTerrain(Terrain t) {
        TerrainDto dto = new TerrainDto();
        dto.name = t.getName();
        dto.location = t.getLocation() != null ? t.getLocation().toString() : null;
        dto.terrainType = t.getTerrainType().name();
        dto.radius = t.getRadius();
        dto.tokenArt = t.getTokenArt();
        if (!t.getRingBands().isEmpty())
            dto.rings = t.getRingBands().toArray(new int[0][]);
        return dto;
    }

    private static ObjectiveDto fromObjective(com.sfb.objects.Objective o) {
        ObjectiveDto dto = new ObjectiveDto();
        dto.name = o.getName();
        // Effective location: the carrier's hex when carried, else its own
        com.sfb.properties.Location loc = o.getEffectiveLocation();
        dto.location = loc != null ? loc.toString() : null;
        dto.carrierName = o.getCarrier() != null ? o.getCarrier().getName() : null;
        dto.retrieval = o.getAllowedRetrieval().stream().map(Enum::name)
                .collect(java.util.stream.Collectors.toList());
        dto.secured = o.isSecured();
        dto.tractoredBy = o.getTractoringUnit() != null ? o.getTractoringUnit().getName() : null;
        dto.beingRecovered = o.isBeingRecovered();
        dto.side = o.getSide();
        com.sfb.Player owner = o.getCurrentOwner();
        dto.ownerTeam = owner != null ? owner.getTeamName() : null;
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
