/**
 * TypeScript mirror of GameStateDto and its nested types.
 * location is "col|row" (e.g. "12|1"), or null if off-map.
 */

export interface ShieldState {
  shieldNum: number;
  current:   number;
  max:       number;
  active:    boolean;
}

export interface WeaponState {
  name:              string;
  armed:             boolean;
  armingTurn:        number;
  armingType:        string | null;   // "STANDARD" | "OVERLOAD" | "SPECIAL" | null
  lastImpulseFired:  number;
  functional:        boolean;
  plasmaType:        string | null;
  pseudoPlasmaReady: boolean;
  isHeavy:           boolean;
  // Energy allocation helpers (heavy weapons only)
  armingCost:        number;
  totalArmingTurns:  number;
  isRolling:         boolean;
  rollingCost:       number;
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
  availableBattery: number;
  // Hull boxes
  availableFhull:   number;
  availableAhull:   number;
  availableChull:   number;
  // Control spaces
  availableBridge:  number;
  availableEmer:    number;
  availableAuxcon:  number;
  // Misc
  tBombs:           number;
  dummyTBombs:      number;
  boardingParties:  number;
  availableTransporters?:   number;
  transporterEnergyCost?:   number;
  cloakState?:              string;
  // Energy allocation helpers
  totalPower:        number;
  moveCost:          number;
  lifeSupportCost:   number;
  fireControlCost:   number;
  activeShieldCost:  number;
  minimumShieldCost: number;
  batteryCharge:     number;
  cloakCost:         number;
}

export interface ShuttleObject extends MapObjectBase {
  type:   'SHUTTLE' | 'SUICIDE_SHUTTLE' | 'SCATTER_PACK';
  facing: number;
  speed:  number;
}

export interface DroneObject extends MapObjectBase {
  type:            'DRONE';
  facing:          number;
  speed:           number;
  warheadDamage:   number;
  controllerFaction: string;
}

export interface PlasmaObject extends MapObjectBase {
  type:              'PLASMA';
  facing:            number;
  currentStrength:   number;
  controllerFaction: string;
  pseudo:            boolean;
}

export interface MineObject extends MapObjectBase {
  type:     'MINE';
  active:   boolean;
  revealed: boolean;
}

export type MapObject =
  | ShipObject
  | ShuttleObject
  | DroneObject
  | PlasmaObject
  | MineObject;

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

/** Faction display colour. */
export function factionColor(faction: string): string {
  switch (faction?.toLowerCase()) {
    case 'federation': return '#3a7bd5';
    case 'klingon':    return '#d53a3a';
    case 'romulan':    return '#3ab87a';
    case 'kzinti':     return '#d5a03a';
    case 'orion':      return '#9b3ad5';
    default:           return '#888888';
  }
}
