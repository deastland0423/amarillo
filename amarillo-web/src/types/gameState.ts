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
  armed:             boolean;
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
  holdCost:          number;   // energy to hold per turn; 0 = hold not supported
  canOverload:            boolean;  // weapon supports OVERLOAD mode
  canSuicide:             boolean;  // weapon supports SPECIAL/SUICIDE mode (Fusion only)
  canProximity:           boolean;  // weapon supports PROXIMITY mode (Photon only)
  overloadFinalTurnOnly:  boolean;  // OVERLOAD only choosable on the final arming turn
  totalArmingTurns:  number;
  isRolling:         boolean;
  rollingCost:       number;  // always sent for plasma; 0 for non-plasma
  canEpt:            boolean; // plasma only: can fire as Enveloping Plasma Torpedo
  eptCost:           number;  // plasma only: energy cost for EPT on final arming turn
  maxShotsPerTurn:   number;
  shotsThisTurn:     number;
  minImpulseGap:     number;
  chargesRemaining?: number;  // FighterFusion only
  canFireDouble?:    boolean; // FighterFusion only
  addShots?:         number;  // ADD only: shots remaining in current load
  addReloads?:       number;  // ADD only: reserve shots remaining
  addCapacity?:      number;  // ADD only: shots per full load
}

export interface ReloadPoolEntry {
  droneType: string;   // e.g. "TYPE_I"
  rackSize:  number;   // deck crew cost per drone
  count:     number;   // how many available
}

export interface ShuttleInBayState {
  name:                string;
  type:                string;   // "admin" | "gas" | "hts" | "stinger1" | "stinger2" | "stingerh" | "suicide" | "scatterpack"
  maxSpeed:            number;
  canLaunch:           boolean;  // hatch or tube available for this shuttle right now
  armed?:              boolean;  // suicide only
  armingTurnsComplete?: number; // suicide only
  warheadDamage?:      number;  // suicide only
  payloadCount?:       number;  // scatterpack only
}

export interface ShuttleBayState {
  bayIndex:        number;
  canLaunch:       boolean;
  launchTubeCount: number;
  availableTubes:  number;
  shuttles:        ShuttleInBayState[];
}

export interface DroneRackState {
  name:                 string;
  functional:           boolean;
  canFire:              boolean;
  drones:               { droneType: string; warheadDamage: number; speed: number; endurance: number }[];
  reloadCount:          number;
  reloadDeckCrewCost:   number;
  reloadingThisTurn:    boolean;
  reloadPool:           ReloadPoolEntry[];
  launchDirectionsMask: number;  // valid launch facings bitmask; 0 = unrestricted
}

interface MapObjectBase {
  name:     string;
  location: string | null;
}

export interface ShipObject extends MapObjectBase {
  type:    'SHIP';
  hull:    string;
  faction: string;
  facing:  number;
  speed:   number;
  shields: ShieldState[];
  // Weapons
  weapons:          WeaponState[];
  droneRacks:       DroneRackState[];
  shuttleBays:      ShuttleBayState[];
  phaserCapacitor:    number;
  phaserCapacitorMax: number;
  capacitorsCharged:  boolean;
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
  // Crew
  availableCrewUnits:  number;
  minimumCrew:         number;
  availableDeckCrews:  number;
  crewQuality:         string;   // "POOR" | "NORMAL" | "OUTSTANDING"
  availableTransporters?:   number;
  transporterEnergyCost?:   number;
  uimFunctional:            boolean;  // true if ship has a functional UIM this impulse
  cloakState?:              string;   // "NONE" | "INACTIVE" | "FADING_OUT" | "FULLY_CLOAKED" | "FADING_IN"
  cloakFadeStep?:           number;   // 1–5 during fade transitions
  cloakTransitionImpulse?:  number;
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
}

export interface ShuttleObject extends MapObjectBase {
  type:           'SHUTTLE' | 'SUICIDE_SHUTTLE' | 'SCATTER_PACK';
  facing:         number;
  speed:          number;
  maxSpeed:       number;
  parentShipName: string | null;
  weapons?:       WeaponState[];  // non-null for fighters
  crippled?:      boolean;
  hetUsed?:       boolean;        // fighters only: true if tactical maneuver used this turn
  isIdentified?:  boolean;        // SUICIDE_SHUTTLE and SCATTER_PACK only
}

export interface DroneObject extends MapObjectBase {
  type:              'DRONE';
  facing:            number;
  speed:             number;
  droneType:         string;       // revealed when identified
  warheadDamage:     number;       // revealed when identified
  hull:              number;
  damageTaken:       number;       // maxHull - hull; always public
  maxHull:           number;       // revealed when identified
  endurance:         number;       // revealed when identified
  targetName:        string | null; // revealed when identified
  controllerFaction: string;
  controllerName:    string | null;
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
  terrainType: 'ASTEROID' | 'PLANET';
}

export type MapObject =
  | ShipObject
  | ShuttleObject
  | DroneObject
  | PlasmaObject
  | MineObject
  | TerrainObject;

export interface GameState {
  turn:               number;
  impulse:            number;
  phase:              string;
  awaitingAllocation: boolean;
  pendingAllocation:  string[];
  movableNow:         string[];
  mapObjects:         MapObject[];
  myShips:            string[] | null;
  readyCount:         number;
  playerCount:        number;
  combatLog:          string[];   // fire/damage events since last broadcast; empty most of the time
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
