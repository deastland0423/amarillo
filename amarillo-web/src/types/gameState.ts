/**
 * TypeScript mirror of GameStateDto and its nested types.
 * location is "col|row" (e.g. "12|1"), or null if off-map.
 */

export interface ShieldState {
  shieldNum:    number;
  current:      number;  // includes reinforcement — owner-only
  baseStrength: number;  // without reinforcement — public
  max:          number;
  active:       boolean;
  impulsesUntilRaiseable: number; // 0 = can raise now; >0 = impulses remaining in lockout
}

export interface WeaponState {
  name:              string;
  designator?:       string;
  armed:             boolean | null;   // null = not disclosed (an enemy's ship)
  armingTurn:        number;
  armingType:        string | null;   // "STANDARD" | "OVERLOAD" | "SPECIAL" | null
  lastImpulseFired:  number;
  readyToFire:       boolean;  // functional + armed (if heavy) + impulse gap satisfied
  arcLabel:          string;   // e.g. "FA", "FX + 13", "LF + L + RR + 5"
  arcMask:           number;   // 24-bit bitmask: bit N-1 = direction N in arc
  launchDirectionsMask: number; // valid launch facings bitmask (plasma/drone); 0 = use arcMask
  functional:        boolean;
  plasmaType:        string | null;   // currently arming torpedo type, or null
  launcherType:      string | null;   // fixed launcher type: "F" | "G" | "S" | "R" | null
  pseudoPlasmaReady: boolean;
  isHeavy:           boolean;
  // Energy allocation helpers (heavy weapons only)
  armingCost:        number;
  photonTube?:       boolean;  // dialled by energy rather than by mode (E4.21/E4.411)
  armingEnergy?:     number;   // warp energy already in the tube (E4.413)
  holdCost:          number;   // energy to hold per turn; 0 = hold not supported
  canOverload:            boolean;  // weapon supports OVERLOAD mode
  canSuicide:             boolean;  // weapon supports SPECIAL/SUICIDE mode (Fusion only)
  cooldown?:              boolean;  // Fusion only: fired last turn → cannot arm/fire this turn (E7.x)
  scoutChannel?:          boolean;  // scout function channel / special sensor (G24.0)
  channelPowered?:        boolean;  // powered this turn (G24.14)
  channelBlinded?:        boolean;  // blinded by weapons fire (G24.13)
  channelLendTarget?:     string;   // unit this channel is lending EW to, or null (G24.21)
  channelLentEcm?:        number;   // ECM points this channel is lending (G24.21)
  channelLentEccm?:       number;   // ECCM points this channel is lending (G24.21)
  channelFunction?:        string;   // committed function this turn: NONE/LEND_EW/BREAK_LOCKON/IDENTIFY (G24.12)
  channelBreakAttempts?:   number;   // break-lock-on attempts spent this turn (G24.221)
  channelIdentifyAttempts?: number;  // identify attempts spent this turn (G24.251)
  channelAttractedDrone?: string | null; // drone drawn onto the scout (G24.231)
  esg?:                   boolean;  // ESG generator (G23.0)
  esgHasCapacitor?:       boolean;  // G23.24 capacitor: holds up to 7, releases a chosen 1–5
  esgStoredEnergy?:       number;   // energy held (0–maxStorage)
  esgMaxEnergy?:          number;   // storage cap: 7 with a capacitor, else 5
  esgActive?:             boolean;  // a field is currently up
  esgRadius?:             number;   // active field radius (0–3); -1 hidden while announced
  esgStrength?:           number;   // active field strength — public to all players (G23.46)
  esgAnnounced?:          boolean;  // a release is announced but not yet formed (G23.31)
  esgReleaseIn?:          number;   // impulses until the announced field forms
  canProximity:           boolean;  // weapon supports PROXIMITY mode (Photon only)
  overloadFinalTurnOnly:  boolean;  // OVERLOAD only choosable on the final arming turn
  totalArmingTurns:  number;
  isRolling:         boolean;
  rollingCost:       number;  // always sent for plasma; 0 for non-plasma
  canEpt:            boolean; // plasma only: can fire as Enveloping Plasma Torpedo
  eptCost:           number;  // plasma only: energy cost for EPT on final arming turn
  canFastLoad?:      boolean; // plasma only: FP1.93 fast-load (G/S/R on turn 2, costs 2 battery)
  maxShotsPerTurn:   number;
  shotsThisTurn:     number;
  minImpulseGap:     number;
  chargesRemaining?: number;  // FighterFusion only
  canFireDouble?:    boolean; // FighterFusion only
  addShots?:         number;  // ADD only: shots remaining in current load
  addReloads?:       number;  // ADD only: reserve shots remaining
  addCapacity?:      number;  // ADD only: shots per full load
  // Plasma rack (FP10.0). Every one optional, because the server sends null rather than a
  // default on weapons that are not racks AND on a rack an enemy is looking at — so the guard
  // is always `!= null`, never a falsy check: a rack holding 0 torpedoes is a real state and
  // tells you it is empty, while undefined means you are not allowed to know.
  plasmaRack?:           boolean; // true if this weapon is a PL-D rack
  plasmaRackCapacity?:   number;  // FP10.1/FP10.14: always 4 on a non-base
  plasmaRackTorpedoes?:  number;  // owner only: how many are left
  plasmaRackActive?:     number;  // owner only: of those, how many are paid for (FP9.22)
  plasmaRackReloadSets?: number;  // owner only: FP10.312, one or two from Y175
  plasmaRackMode?:       string;  // owner only: UNDECIDED | OFFENSIVE | DEFENSIVE
  plasmaRackBoltUsed?:   boolean; // owner only: FP10.221, the turn's one bolt is spent
}

export interface ReloadPoolEntry {
  droneType: string;   // e.g. "TYPE_I"
  rackSize:  number;   // deck crew cost per drone
  count:     number;   // how many available
}

export interface ShuttleInBayState {
  name:                string;
  type:                string;   // "admin" | "gas" | "hts" | "stinger1" | "stinger2" | "stingerh" | "suicide" | "scatterpack"
  /**
   * What to CALL the craft on screen — "F-18E", "St-1", "GAS" — from the catalogue's shortName.
   * `type` above is a KEY and must never be shown to a player: the ship viewer listed a carrier's
   * air wing as "stinger1" until this arrived. Absent only for a craft the catalogue does not
   * know, so fall back to `type` and accept the ugliness in that case.
   */
  shortName?:          string;
  maxSpeed:            number;
  /** What a launch is actually capped at: maxSpeed less any point given to EM (C10.13). */
  effectiveMaxSpeed:   number;
  canLaunch:           boolean;  // hatch or tube available for this shuttle right now
  // J4.96: pods and this turn's declared split (J4.961). Sent for a craft still in its bay
  // because that is where an EW fighter usually is when the split is declared.
  ewPods?:             number;
  podEcm?:             number;
  podEccm?:            number;
  podEwDeclared?:      boolean;
  podsActive?:         boolean;
  squadronName?:       string | null;
  armed?:              boolean;  // suicide only
  armingTurnsComplete?: number; // suicide only
  lastArmingEnergy?:   number;  // suicide only: energy paid on the most recent arming turn
  warheadDamage?:      number;  // suicide only
  payload?:            string[]; // scatterpack only: live drone type names
  pendingPayload?:     string[]; // scatterpack only: staged for end-of-turn loading
  /**
   * FD7.11: rack spaces this craft may carry as a scatter pack, or ABSENT if it may not be
   * one. Sent for any craft that qualifies, not just one already converted — loading an
   * admin shuttle is how it becomes a pack.
   *
   * Test with != null and never with `?? 6`: this used to be a primitive on the Java side
   * and arrived as 0 on every craft, which `??` does not catch, so the picker offered room
   * for nothing and disabled itself.
   */
  maxDroneSpaces?:     number;
  committedSpaces?:    number;   // scatterpack only: payload + pending spaces already used
  specialRole?:        string | null;  // "Wild Weasel", "suicide shuttle", "scatter pack"
  wwChargeCount?:      number;   // admin only: 0=uncharged, 1=primed, 2=ready
  wwReady?:            boolean;  // admin only: true when wwChargeCount >= 2
  /**
   * J4.82: this fighter's drone rails, one entry each. Absent (not []) on a craft with no
   * rails at all — "carries no drones" and "carries drones and is empty" are different
   * things, so the guard is != null.
   */
  rails?:              FighterRail[];
  /**
   * READY | PARTIAL | EMPTY, or absent when the craft has nothing to arm. Decided in core
   * (FighterArming.armingState) — never re-derived here.
   */
  armingState?:        string;
}

/** One drone rail on a fighter: what fits in it, and what is in it (J4.82, FD7.211). */
export interface FighterRail {
  railType?: string;   // LIGHT | STANDARD | SPECIAL | HEAVY
  drone?:    string;   // DroneType name; absent when the rail is empty
  spaces?:   number;
}

export interface ShuttleSpaceState {
  spaceIndex: number;
  destroyed:  boolean;
  empty:      boolean;
  armed:      boolean;
  shuttle:    ShuttleInBayState | null;
  // J4.831: the fighter box capacitor — charges held, and what it holds when full.
  // 0 capacity means this box is not a Hydran fighter box.
  capacitorCharges?:  number;
  capacitorCapacity?: number;
  // J4.817: deck crews this box could use this turn, how many are posted, and how much work
  // its fighter still needs (in half-actions — a fusion charge is one, a drone space two).
  crewsWanted?:     number;
  /** J4.817: deck crew jobs available in this box — task name to crews each could use. */
  crewJobs?:        Record<string, number>;
  /** What the occupant holds, and the damage it has taken. */
  chargesAboard?:   number;
  damage?:          number;
  // J4.832: points THIS capacitor can still take. Never summed across boxes.
  capacitorRoom?:   number;
  postedCrews?:     number;
  workOutstanding?: number;
  // J4.822: drones in this box's ready rack and what it holds full — the drone fighter's
  // answer to the capacitor. Absent (not 0) on a box that never had a rack, so the guard
  // is != null: a rack emptied by a strike really does read 0.
  readyRackCount?:    number;
  readyRackCapacity?: number;
}

export interface ShuttleBayState {
  bayIndex:        number;
  canLaunch:       boolean;
  launchTubeCount: number;
  availableTubes:  number;
  totalSpaces:     number;
  destroyedSpaces: number;
  emptySpaces:     number;
  shuttles:        ShuttleInBayState[];
  spaces:          ShuttleSpaceState[];
  /** J1.53: positions on this bay's outside balcony. 0 on almost every bay. */
  balconyPositions: number;
  /**
   * The craft parked out there, shaped exactly like the ones in boxes because the owner acts
   * on them in the same ways. Never null from the server, but optional here so an older
   * snapshot in flight does not break the render.
   */
  balcony?:        ShuttleInBayState[];
}

export interface DroneRackState {
  name:                 string;
  functional:           boolean;
  canFire:              boolean;
  drones:               { droneType: string; warheadDamage: number; speed: number; endurance: number }[];
  reloadCount:          number;
  reloadingThisTurn:    boolean;
  reloadPool:           ReloadPoolEntry[];
  launchDirectionsMask: number;  // valid launch facings bitmask; 0 = unrestricted
  antiDrones?:          number;  // type-G only (FD3.70): anti-drone rounds, 1/2 space each
  antiDroneReloads?:    number;  // rounds held in reserve for this rack (FD3.72)
  mode?:                string;  // UNDECIDED | DRONE | ANTI_DRONE for the turn (FD3.71)
  canFireAntiDrone?:    boolean; // an anti-drone round may go this impulse
  spacesFree?:          number;  // drones and anti-drones counted together
}

interface MapObjectBase {
  name:     string;
  location: string | null;
}

export interface ShipObject extends MapObjectBase {
  type:     'SHIP';    // map-object discriminator, not the ship's SSD type
  shipType: string;    // the SSD Type line, e.g. "CA+"
  typeName?: string | null;  // the same class written out, e.g. "Commando Cruiser"
  faction: string;
  facing:  number;
  speed:   number;
  tractorTrueSpeed?: number;   // plotted speed before tractor pseudo-speed (G7.34); -1 = not limited
  shields: ShieldState[];
  // Weapons
  weapons:          WeaponState[];
  droneRacks:       DroneRackState[];
  shuttleBays:      ShuttleBayState[];
  phaserCapacitor:    number;
  phaserCapacitorMax: number;
  capacitorsCharged:  boolean;
  // J4.832: power the fighter box capacitors could still take. 0 = nothing to buy.
  fighterCapacitorRoom?: number;
  /** FP9.22: half points needed to activate every type-D torpedo aboard. Owner only. */
  plasmaActivationRoom?: number;
  /**
   * J4.7: spaces of spare drones this carrier holds for its fighters, and how much is still
   * in the hold. Absent on a ship that declares no storage — which is not a carrier that has
   * run dry, so test with != null.
   */
  droneStorageSpaces?: number;
  droneStorageHeld?:   number;
  /**
   * FD2.445 cargo-box drones, for the ship's OWN racks — a DIFFERENT pool from the two above,
   * which are the FD2.443/J4.7 supply for its FIGHTERS. Absent on a hull that declares none, so
   * test with != null. Capacity falls 50 a box as the cargo boxes die.
   */
  cargoDroneSpaces?:      number;
  cargoDroneSpacesHeld?:  number;
  // Power
  availableLWarp:   number;
  availableRWarp:   number;
  availableCWarp:   number;
  availableImpulse: number;
  availableApr:     number;
  availableAwr:     number;
  maxLWarp:         number;
  maxRWarp:         number;
  maxCWarp:         number;
  maxImpulse:       number;
  maxApr:           number;
  maxAwr:           number;
  availableBattery: number;
  batteryPower:     number;
  skeleton:         boolean;
  reserveWarp:      number;
  hetCost:          number;
  hetsThisTurn?:          number;
  lastHetImpulse?:        number;
  /**
   * Absolute impulse the ship may move again on, after a breakdown or a failed HET (C6.54).
   * Already sent by the server and already read by GameBoard — it was simply never declared here,
   * which is the same ShipDto-versus-ShipObject drift the cargo-drone and aegis fields had.
   */
  immobileUntilImpulse?:  number;
  canDoubleEngines: boolean;   // G15.2 — Orion engine doubling available
  // Hull boxes
  availableFhull:   number;
  availableAhull:   number;
  availableChull:   number;
  maxFhull:         number;
  maxAhull:         number;
  maxChull:         number;
  // Control spaces (current / max)
  availableBridge:   number;
  maxBridge:         number;
  availableFlag:     number;
  maxFlag:           number;
  availableEmer:     number;
  maxEmer:           number;
  availableAuxcon:   number;
  maxAuxcon:         number;
  availableSecurity: number;
  maxSecurity:       number;
  // Misc
  tBombs:           number;
  dummyTBombs:      number;
  nuclearSpaceMines: number;
  boardingParties:  number;
  commandos:        number;
  availableLab:     number;
  functioningLab?:  number;        // lab boxes that exist; availableLab is those free now (G4.451)
  // Crew
  availableCrewUnits:  number;
  minimumCrew:         number;
  availableDeckCrews:  number;
  crewQuality:         string;   // "POOR" | "NORMAL" | "OUTSTANDING"
  availableTransporters?:   number;
  totalTransporters?:       number;
  transporterEnergyCost?:   number;
  totalTractors?:           number;
  uimFunctional:            boolean;  // true if ship has a functional UIM this impulse
  cloakState?:              string;   // "NONE" | "INACTIVE" | "FADING_OUT" | "FULLY_CLOAKED" | "FADING_IN"
  cloakFadeStep?:           number;   // 1–5 during fade transitions
  cloakTransitionImpulse?:  number;
  /** S8.36/C1.x: how large a fleet this hull can lead. 0 on a ship with no command ability. */
  commandRating?:   number;
  /** D6.124 scanner bonus. 0 on most hulls, so it is only worth showing when non-zero. */
  scannerBonus?:    number;
  /** D13: "NONE" | "LIMITED" | "FULL" — what aegis the HULL has, never derived from isEscort. */
  aegisFitted?:     string;
  // Electronic warfare
  sensorRating:     number;
  ecmAllocated:     number;
  eccmAllocated:    number;
  allocationNotes?:  string[]; // what this turn's allocation cost — own ships only
  setupNotes?:       string[]; // COI selections that could not be applied — own ships only
  activeFireControl?: boolean; // false = passive fire control (D19.0)
  lentEcm?:          number;   // ECM received from a friendly scout (D6.3144)
  lentEccm?:         number;   // ECCM received from a friendly scout (D6.3144)
  offensiveEw?:      number;   // enemy jamming on this ship's fire (G24.219)
  ecmTotal?:         number;   // generated + lent (weasel included) + built-in
  eccmTotal?:        number;   // generated + lent
  ecmSources?:       string | null;   // "2 generated + 6 lent"
  /**
   * J4.93/J4.931: this carrier's squadrons and the EW it generated for each this turn.
   * squadronEwLimit is the most it may put into ANY ONE pool, and zero when it may not lend at
   * all — so the form tests one number instead of knowing the carrier rules (J4.931/J4.6).
   */
  /**
   * C2.0: the impulse this unit next moves on, and how many impulses off that is. Zero for
   * anything at speed zero. The wait is never zero when it does move, so `impulsesUntilMove === 1`
   * is "moves next impulse" — which is the test P3.25 needs, since fire into an asteroid hex only
   * counts on the impulse immediately before entry.
   */
  nextMoveImpulse?:   number;
  impulsesUntilMove?: number;
  squadrons?:        { name: string; fighters: number; ecm: number; eccm: number }[];
  squadronEwLimit?:  number;
  scoutEwPool?:     number;   // EW points this scout generated to lend this turn (G24.211)
  scoutEwLent?:     number;   // of the pool, how many are currently lent out (G24.2111)
  scoutEwRemaining?: number;  // still available to commit; dropped points are lost (G24.2122)
  // Energy allocation helpers
  totalPower:        number;
  moveCost:          number;
  lifeSupportCost:   number;
  fireControlCost:   number;
  activeShieldCost:  number;
  minimumShieldCost: number;
  batteryCharge:     number;
  cloakCost:         number;
  maxSpeedNextTurn:  number;   // C2.2 acceleration cap
  lockOnTargets?:    string[];  // names of units this ship currently has lock-on to
  tokenArt?:         string;    // path to PNG token image, e.g. "federation/constitution.png"
  turnMode?:         string;    // e.g. "A", "B", "C"
  turnHexes?:        number;    // hexes required between turns at current speed
  hexesUntilTurn?:   number;    // 0 = may turn now; >0 = hexes still needed
  captured?:              boolean;   // true after all control rooms taken (D7.50)
  disengaged?:            boolean;   // true after ship voluntarily exits the map
  canDisengageBySeparation?: boolean; // true when ship is >50 hexes from all enemies with no seekers targeting it
  destructionDirections?:    string[]; // A–F directions that destroy on accel disengage
  ownerName?:        string;    // controlling player name (may change on capture)
  teamName?:         string;    // display name of the team/side this ship belongs to
  decelerating?:              boolean; // true during 2-impulse ED period (C8.0)
  decelerationEndsAtImpulse?: number;  // absolute impulse when ship stops
  wildWeaselActive?: boolean;   // true while a WW decoy is on the map for this ship
  wwEcmBonus?:       number;    // +6 while WW active, else 0
  tractored?:                  boolean;   // true if held in a tractor beam (G7.0)
  tractoredByName?:            string;    // name of the holding ship
  usingEm?:                    boolean;   // Erratic Maneuvers in force (C10.0)
  transporterUses?:            number;    // activations still affordable, batteries included
  erraticCost?:                number;    // what EM costs this ship (C10.11/C10.12); 0 = cannot
  paidForEm?:                  boolean;   // bought at allocation, so EM may be announced (C10.11)
  emPending?:                  boolean;   // announced this impulse, in force at its end (C10.311)
  tractorEnergy?:              number;    // total tractor energy allocated in EA this turn
  tractorEnergyRemaining?:     number;    // unspent tractor pool energy
  negativeTractorAccumulated?: number;    // cumulative negative-tractor spent this turn (G7.35)
  /**
   * Seeker control channels held and available. A launch past the limit does not fail —
   * something already flying stops being tracked instead.
   */
  controlUsed?:                number;
  controlLimit?:               number;
  availableTractors?:          number;    // number of undamaged tractor beams
  tractoredTargetNames?:       string[];  // names of ships this ship is currently tractoring
  fireControlActivating?: boolean; // true during 4-impulse D6.6 activation countdown
  fcActivatingUntil?:     number;  // absolute impulse when activation completes
  fcPaidThisTurn?:        boolean; // true if FC energy was allocated this turn
  // Tactical Maneuvers (C5.0)
  tacAvailable?:          number;  // earned warp TAC ready to use (0 or 1)
  tacBudget?:             number;  // warp TACs still to be earned this turn
  sublightTacAvailable?:  boolean; // sublight TAC paid and unused
}

export interface ShuttleObject extends MapObjectBase {
  type:           'SHUTTLE' | 'SUICIDE_SHUTTLE' | 'SCATTER_PACK';
  beingRecovered?: boolean;
  facing:         number;
  speed:          number;
  maxSpeed:       number;
  parentShipName: string | null;
  weapons?:       WeaponState[];  // every shuttle: an admin shuttle carries a Ph-3
  isFighter?:     boolean;        // a fighter, as opposed to an admin/other shuttle
  shuttleTypeName?: string | null;  // "Admin Shuttle", "General Assault Shuttle" — never the role
  effectiveMaxSpeed?: number;     // after any point given to Erratic Maneuvers (C10.13)
  usingEm?:           boolean;    // Erratic Maneuvers in force (C10.0)
  emSpeedCommitted?:  boolean;    // the point of speed is spent for the turn (C10.131)
  crippled?:      boolean;
  hull?:          number;    // undamaged hull remaining
  maxHull?:       number;    // hull the craft starts with
  damageTaken?:   number;
  launchImpulse?: number;    // when it left the bay
  /**
   * J1.342/J1.341: impulses this craft must still serve after launch before it may fire
   * direct-fire weapons, and before it may use seeking weapons. 0 means it may. Decided in
   * core — never recomputed here from launchImpulse.
   */
  /**
   * J1.31: this fighter's drone rails. Absent for a craft with none, and absent for an
   * enemy viewer whatever it carries (G4.233 keeps drones aboard off a scan).
   */
  rails?:         FighterRail[];
  /**
   * C2.0: the impulse this unit next moves on, and how many impulses off that is. Zero for
   * anything at speed zero. The wait is never zero when it does move, so `impulsesUntilMove === 1`
   * is "moves next impulse" — which is the test P3.25 needs, since fire into an asteroid hex only
   * counts on the impulse immediately before entry.
   */
  nextMoveImpulse?:   number;
  impulsesUntilMove?: number;
  fireDelayRemaining?:   number;
  seekerDelayRemaining?: number;
  hetUsed?:       boolean;        // fighters only: true if tactical maneuver used this turn
  landingPhase?:      string;        // NONE | DESCENDING | LANDED | CLIMBING (P2.4)
  landedHexSide?:     number;        // 1..6 (A..F) when landed on a planet
  holdCrew?:          number;        // crew units in the hold
  holdSpacesUsed?:    number;        // personnel spaces occupied
  personnelCapacity?: number;        // personnel-space capacity
  isIdentified?:     boolean;        // any shuttle: identified by an enemy lab or scout (G4.2)
  // G4.233: all an identification reveals about a shuttle is whether it is on a seeking
  // course and, if so, its target. Never the drones aboard or a suicide bomb, which is why
  // an identified suicide shuttle and an identified scatter pack arrive here identical.
  seekingCourse?:     boolean;
  seekingTargetName?: string | null;
  manned?:            boolean | null;   // G4.233; null = not established
  // Electronic warfare (J4.47, J4.9x). Public for every fighter, friend or enemy, by the
  // owner's ruling — the same treatment a ship's EW gets. Absent entirely on a craft with no
  // EW of its own, which is why every field is optional rather than defaulting to zero.
  ecmTotal?:          number;        // J4.91: built-in + pods + lent, capped at six
  eccmTotal?:         number;
  ecmSources?:        string | null; // "2 built-in + 2 pods + 2 lent from HAAS-E"
  squadronName?:      string | null; // J4.46
  ewPods?:            number;        // J4.96: pods aboard
  podEcm?:            number;        // J4.961: this turn's declared split
  podEccm?:           number;
  podEwDeclared?:     boolean;       // J4.961: declared, or the even default
  podsActive?:        boolean;       // J4.967
  ewLendDelayRemaining?: number;     // J1.343: impulses before it may LEND
  lentEwSourceName?:  string | null; // J4.93: who is lending right now
  lentEcm?:           number;
  lentEccm?:          number;
  ewLenderName?:      string | null; // the squadron's EW fighter, in range or not
  ewLenderRange?:     number;        // hexes to it
  ewLendRangeLimit?:  number;        // J4.921's three, sent rather than hardcoded
  ewLendRefusal?:     string | null; // why nothing is arriving
  ewLendCandidates?:  string[];      // J4.921: what it could be switched to right now
  ewLendSourceProvisional?: boolean; // J4.922: the game chose it, so it may be replaced freely
  ewLendChangeIn?:    number;        // J4.922: impulses before a change is allowed
  controllerFaction?: string;        // SUICIDE_SHUTTLE and SCATTER_PACK only
  controllerName?:   string | null;  // SUICIDE_SHUTTLE and SCATTER_PACK only
  targetName?:       string | null;  // SUICIDE_SHUTTLE and SCATTER_PACK only
  // SCATTER_PACK only
  payload?:          string[];       // drone type names; empty after release
  released?:         boolean;        // true after drones deployed
  // SUICIDE_SHUTTLE only
  warheadDamage?:    number;
  armingTurnsComplete?: number;
}

export interface DroneObject extends MapObjectBase {
  type:              'DRONE';
  facing:            number;
  speed:             number;
  /**
   * C2.0: the impulse this unit next moves on, and how many impulses off that is. Zero for
   * anything at speed zero. The wait is never zero when it does move, so `impulsesUntilMove === 1`
   * is "moves next impulse" — which is the test P3.25 needs, since fire into an asteroid hex only
   * counts on the impulse immediately before entry.
   */
  nextMoveImpulse?:   number;
  impulsesUntilMove?: number;
  droneType:         string;       // revealed when identified
  warheadDamage:     number;       // revealed when identified
  hull:              number;
  damageTaken:       number;       // maxHull - hull; always public
  maxHull:           number;       // revealed when identified
  endurance:         number;       // revealed when identified
  targetName:        string | null; // revealed when identified
  controllerFaction: string;
  controllerName:    string | null;
  launcherName:      string | null;
  launchImpulse:     number;
  isIdentified:      boolean;
}

export interface PlasmaObject extends MapObjectBase {
  type:              'PLASMA';
  facing:            number;
  speed:             number;
  currentStrength:   number;
  controllerFaction: string;
  controllerName:    string | null;
  pseudo:            boolean;       // never revealed to enemy
  plasmaType:        string | null; // never revealed to enemy
  targetName:        string | null; // revealed when identified
  launchImpulse:     number;
  isIdentified:      boolean;
}

export interface MineObject extends MapObjectBase {
  type:     'MINE';
  active:   boolean;
  revealed: boolean;
}

export interface TerrainObject extends MapObjectBase {
  type:        'TERRAIN';
  terrainType: 'ASTEROID' | 'PLANET' | 'GAS_GIANT';
  radius?:     number;   // footprint radius in hexes (0/absent = single hex)
  tokenArt?:   string;   // per-instance counter art path (null/absent → per-type default)
  rings?:      number[][]; // planetary ring bands as [inner, outer] hex-distance pairs (P2.223)
}

export interface ObjectiveObject extends MapObjectBase {
  type:            'OBJECTIVE';
  carrierName:     string | null;   // null = free on the map; else the carrying ship
  retrieval:       string[];        // permitted retrieval methods
  tractoredBy?:    string | null;   // ship holding it in a beam (still free), else null
  beingRecovered?: boolean;         // J1.621 rotation pull-in underway
  side?:           number;          // planet hex side 1..6 it sits on, or 0 (SH50.46)
}

export interface WildWeaselObject extends MapObjectBase {
  type:           'WILD_WEASEL';
  facing:         number;
  speed:          number;
  parentShipName: string | null;
  parentPlayer:   string | null;
  exploding?:     boolean;
  postExplosion?: boolean;
}

export type MapObject =
  | ShipObject
  | ShuttleObject
  | DroneObject
  | PlasmaObject
  | MineObject
  | TerrainObject
  | ObjectiveObject
  | WildWeaselObject;

export interface ShipVpRow {
  shipName:  string;
  teamName:  string;
  gabpv:     number;
  status:    'INTACT' | 'DAMAGED' | 'CRIPPLED' | 'DISENGAGED' | 'DESTROYED' | 'CAPTURED';
  vpScored:  number;
  coiSpend:  number;  // Commander's Option points this ship bought (awarded to the enemy, S2.20 B)
}

export interface TeamScore {
  teamName:       string;
  vpScored:       number;
  vpAgainst:      number;
  levelOfVictory: string;
  coiForfeited:   number; // total COI this side handed to the enemy (S2.20 B)
}

export interface ObjectiveStanding {
  name:      string;
  ownerTeam: string | null;                    // null = free / unclaimed
  state:     'SECURED' | 'CARRIED' | 'FREE';
  points:    number;                           // VP its controller scores at scenario end
}

export interface Scoreboard {
  ships:      ShipVpRow[];
  teams:      TeamScore[];
  objectives: ObjectiveStanding[];
}

export interface GameState {
  mapCols:            number;
  mapRows:            number;
  maxTurns:           number;   // 0 = no limit
  gameOver:           boolean;
  winnerTeam:         string | null;
  endReason:          string | null;
  scoreboard:         Scoreboard | null;
  turn:               number;
  impulse:            number;
  phase:              string;
  awaitingAllocation:    boolean;
  pendingAllocation:     string[];
  pendingAccelDisengage: string[]; // ship names awaiting player YES/NO for C7.1
  movableNow:         string[];
  mapObjects:         MapObject[];
  myShips:            string[] | null;
  readyCount:         number;
  playerCount:        number;
  // Fire declaration round (D6.315) — who responded is public, commits are sealed
  fireDeclarationOpen:      boolean;
  fireDeclarationCaller:    string | null;
  fireDeclarationResponded: string[];
  fireDeclarationSpent:     boolean;
  // Launch declaration round (Annex #2, 6B) — a separate round from the fire one, with its
  // own call. Only who has answered is public; the orders stay sealed server-side.
  activityDeclarationOpen?:      boolean;
  activityDeclarationCaller?:    string | null;
  activityDeclarationResponded?: string[];
  activityDeclarationSpent?:     boolean;
  combatLog:          string[];   // fire/damage events since last broadcast; empty most of the time
  pendingVolleys:         PendingVolley[];
  pendingDacChoices:       PendingDacChoice[];
  pendingBlindChoices:     PendingBlindChoice[];
  pendingAttractChoices:   PendingAttractChoice[];
  pendingControlOverflows: PendingControlOverflow[];
  pendingTractorAuction:   PendingTractorAuction | null;
}

export interface PendingTractorAuction {
  attackerName:        string;
  targetName:          string;
  attackerBid:         number; // effective tractor points
  rangeMultiplier:     number; // 1 for range 0-1; 2 for range 2; 3 for range 3 (G7.6)
  defenderAccumulated: number; // existing negative-tractor on target
  defenderMaxBid:      number; // target's remaining pool + battery
}

export interface PendingVolley {
  attackerName:             string;
  targetShipName:           string;
  shieldNumber:             number;
  totalDamage:              number;
  envelopingHellboreDamage: number;
  addHit:                   boolean;
}

export interface PendingDacChoice {
  targetShipName: string;
  dacType:        string;  // "phaser" | "drone" | "torp" | "weapon" | "warp" | "shuttle"
  roll:           number;
  options:        string[]; // weapon names, "lwarp"/"cwarp"/"rwarp", or "bay:N:space:N"
  bayIndex:       number;   // shuttle chain reactions: >=0 = scoped bay; -1 = any bay
}

/** A scout is attracting an unidentified shuttle; its owner answers, honestly or not (G24.235). */
export interface PendingAttractChoice {
  shuttleName:       string;
  scoutName:         string;
  channelDesignator: string;
  ownerShipName:     string | null;
}

export interface PendingBlindChoice {
  scoutName: string;
  channels:  BlindChannelOption[];
}

export interface BlindChannelOption {
  designator:       string;
  function:         string;  // NONE / LEND_EW / BREAK_LOCKON / IDENTIFY / OFFENSIVE_EW
  target:           string | null;
  lentEcm:          number;
  lentEccm:         number;
  breakAttempts:    number;
  identifyAttempts: number;
  blinded:          boolean;
}

export interface SeekerChoice {
  name:            string;
  label:           string;
  targetName:      string | null;
  transferOptions: string[];
}

export interface PendingControlOverflow {
  shipName:       string;
  overLimitCount: number;
  seekers:        SeekerChoice[];
}

/** Parse location string → [col, row] (1-indexed), or null.
 *  Accepts "<col|row>" (server format) or plain "col|row". */
export function parseLocation(loc: string | null): [number, number] | null {
  if (!loc) return null;
  const clean = loc.replace(/[<>]/g, '');
  const parts = clean.split('|');
  if (parts.length !== 2) return null;
  const col = parseInt(parts[0], 10);
  const row = parseInt(parts[1], 10);
  if (isNaN(col) || isNaN(row)) return null;
  return [col, row];
}

/** Convert internal 24-step facing to canvas angle (radians, 0=right, CW). */
export function facingToAngle(facing: number): number {
  return (facing - 1) * (2 * Math.PI / 24) - Math.PI / 2;
}

/** Convert internal 24-step facing to SFB letter (A–F). */
export function facingLabel(facing: number): string {
  return 'ABCDEF'[Math.floor(((facing - 1) % 24) / 4)] ?? '?';
}

/**
 * Colour for a shield at this strength. Lives here beside the other display helpers rather
 * than inside HexGrid, so the map and the SSD panel cannot come to disagree about what
 * counts as a hurt shield.
 */
export function shieldStrengthColor(current: number, max: number): string {
  if (max === 0 || current === 0) return '#333333';
  const pct = current / max;
  if (pct > 0.6)  return '#56d364';  // green
  if (pct > 0.25) return '#f0c040';  // yellow
  return '#f85149';                  // red
}

/** Faction display colour. */
export function factionColor(faction: string): string {
  switch (faction?.toLowerCase()) {
    case 'federation': return '#3a7bd5';
    case 'klingon':    return '#d53a3a';
    case 'romulan':    return '#3ab87a';
    case 'kzinti':     return '#d5a03a';
    case 'orion':      return '#ec6fd1';
    case 'hydran':     return '#9b3ad5';
    default:           return '#888888';
  }
}
