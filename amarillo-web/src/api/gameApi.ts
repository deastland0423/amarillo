/**
 * Thin wrappers around the Amarillo REST API.
 * All paths are relative — Vite proxies /api → localhost:8080 in dev.
 */

export interface GuardTarget {
  code:     string;            // wire code: "WEAPON:name", "TRACTOR:2", "SENSORS", ...
  label:    string;
  kind:     'exact' | 'pool';
  guarded:  boolean;           // exact targets: has a guard posted
  guards?:  number;            // pool targets: guards posted
  boxes?:   number;            // pool targets: current box count
}

export interface GuardOptions {
  normalAvailable:    number;
  commandosAvailable: number;
  totalPosted:        number;
  targets:            GuardTarget[];
}

export interface CreateGameResponse {
  gameId: string;
  hostToken: string;
  message: string;
}

export interface JoinGameResponse {
  playerToken: string;
  message: string;
}

export interface GameStatus {
  gameId: string;
  started: boolean;
  players: Array<{
    name: string;
    role: 'host' | 'player';
    ships: string[];
  }>;
}


// ---------------------------------------------------------------------------
// Fleet building (S8.0)
// ---------------------------------------------------------------------------

/** A ship on the shelf. Prices come from the validator, not the JSON, so they cannot drift. */
export interface CatalogShip {
  faction:       string;
  type:          string;   // SSD Type designation, e.g. "D7C"
  name:          string;
  line:          string;   // hull family, e.g. "CA"
  lineName:      string;   // "Heavy Cruiser"
  sizeClass:     number;
  serviceYear:   number;
  commandRating: number;
  bpv:           number;   // combat BPV
  fighterBpv:    number;   // fighters it comes with
  cost:          number;   // what it actually costs (economic for scouts, plus fighters)
  coiAllowance:  number;   // most it may spend on Commander's Options (S3.2)
  isScout:       boolean;
  isLeader:      boolean;
  isEscort:      boolean;
  isTrueCarrier: boolean;
  isBCH:         boolean;
}

/** The ground a side may set up on, already expanded to hexes by the server. */
export interface LobbyZone {
  describe: string;      // "within 6 hexes of the left edge"
  hexes:    string[];    // CCRR; empty means the whole map, so nothing to mark
}

/** One side of a battle, as the lobby broadcasts it. */
export interface LobbySide {
  name:    string;
  faction: string;
  deploymentZone: LobbyZone | null;
  ships: Array<{
    shipName:     string;
    type:         string;
    startHex:     string;
    startHeading: string;
    startSpeed:   number;
    weaponStatus: number;
    refits:       string[];
  }>;
}

/** A piece of terrain as the lobby broadcasts it, before any game state exists. */
export interface LobbyTerrain {
  terrainType: string;          // "ASTEROID" | "PLANET" | "GAS_GIANT"
  hex:         string;          // CCRR
  name:        string | null;
  radius:      number;
  rings:       number[][];      // {inner, outer} hex-distance bands (P2.223)
}

/** One ship, set down. Speed 16 is Speed Max. */
export interface Placement {
  shipName: string;
  hex:      string;    // CCRR
  heading:  string;    // "A"-"F"
  speed:    number;
}

/** Everything a player needs to set their own fleet down. */
export interface DeploymentState {
  required:   boolean;
  ships:      string[];          // the ships this player must place
  zone:       LobbyZone | null;  // the ground they may use
  noEntry:    string[];          // hexes no ship may occupy — planets and gas giants
  complete:   boolean;           // every ship somewhere legal
  done:       boolean;           // this player has said they are finished
  placements: Placement[];
}

/** A fleet chosen into a battle, and the team flying it. */
export interface FleetSideChoice {
  fleetId: string;
  team?:   string;
}

/** What a host may put on the map. Settled before forces take the field (S8.15). */
export type TerrainChoice = 'OPEN_SPACE' | 'ASTEROID_FIELD' | 'PLANET' | 'GAS_GIANT';

export interface FleetGameSetup {
  sides:         FleetSideChoice[];
  year:          number;
  budget:        number;
  mapCols?:      number;
  mapRows?:      number;
  weaponStatus?: number;
  terrain?:      TerrainChoice;
  /** Omit to have the host roll one; it comes back so the same map can be laid again. */
  terrainSeed?:  number;
  /**
   * A written situation to bring the fleets to — one whose sides are waiting for a fleet.
   * Omit for a straight fight, which is the same thing with nothing specified.
   */
  scenarioId?:   string;
}

export interface FleetShipEntry {
  faction?:  string;   // blank means the fleet's first empire
  type:      string;
  name?:     string;   // blank means the ship file's own name
  coiSpend?: number;
}

/** The saved fleet: what is posted to validate, and what lands in data/fleets. */
export interface FleetSpec {
  id?:       string;
  name:      string;
  author?:   string;
  factions:  string[];   // allied empires this force draws on (S8.6)
  year:      number;
  budget:    number;
  flagship?: string;
  ships:     FleetShipEntry[];
  updated?:  string;
}

export interface FleetViolation {
  rule:     string;                   // e.g. "S8.36"
  severity: 'ERROR' | 'ADVISORY';
  message:  string;
  shipName: string;
}

export interface FleetValidation {
  legal:         boolean;
  cost?:         number;   // hulls and fighters
  totalCost?:    number;   // and Commander's Options
  budget?:       number;
  shipCount?:    number;
  violations:    FleetViolation[];
  unknownShips?: string[];
}

/** A row in the saved-fleet list, revalidated as it was listed. */
export interface FleetSummary {
  id:        string;
  name:      string;
  author:    string;
  factions:  string[];
  year:      number;
  budget:    number;
  shipCount: number;
  updated:   string;
  legal:     boolean;
  totalCost: number;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const { headers: extraHeaders, ...restOptions } = options ?? {};
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...extraHeaders },
    ...restOptions,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error ?? `HTTP ${res.status}`);
  }
  return res.json();
}

export interface PlayerListing {
  name: string;
  token: string;
}

export interface ScenarioShip {
  type:         string;
  shipName:     string;
  startHex:     string;
  startHeading: string;
  startSpeed:   number;
  weaponStatus: number;
  refits:       string[];
}

export interface ScenarioSide {
  faction:             string;
  name:                string;
  ships:               ScenarioShip[];
  reinforcementGroups: number;
}

export interface ScenarioSummary {
  id:               string;
  name:             string;
  year:             number;
  numPlayers:       number;
  mapType:          string;
  description:      string;
  specialRules:     string[];
  victoryType:      string;
  victoryNotes:     string;
  warpBoosterPacks: boolean;
  megapacks:        boolean;
  mrsShuttles:      boolean;
  pfs:              boolean;
  sides:            ScenarioSide[];
  /** Sides waiting for somebody's fleet. Empty means this scenario brings its own ships. */
  openSides:        string[];
  /** True when the scenario has decided its own terrain and the host may not change it. */
  fixedTerrain:     boolean;
}

// ---- COI data types ----

export interface CoiHeavyWeapon {
  designator: string;
  type:       string;    // "Photon", "Disruptor", etc.
  isPlasma:   boolean;
}

export interface CoiDroneRack {
  index:        number;
  designator:   string;
  spaces:       number;
  reloadCount:  number;
  defaultAmmo:  string[];  // drone type names before any COI loadout
  canLoadTypeVI: boolean;  // only TYPE_E, TYPE_G, TYPE_H
}

export interface CoiDroneType {
  name:   string;
  speed:  number;
  damage: number;
  rack:   number;   // spaces consumed per drone
}


export interface CoiOptionChoice {
  name: string;
  cost: number;   // BPV delta (Annex #8B); may be negative/fractional
  empires?: string[] | null;   // producing empires; null/absent = universal (cartel-exempt, G15.44)
}

export interface Cartel {
  name:          string;
  home:          string;
  operatingZone: string[];
}

export interface CoiOptionMount {
  designator:    string;
  position:      string;   // "CENTERLINE" | "WING"
  arcs:          string[];
  currentOption: string | null;  // pre-filled weapon name, if any
  legalOptions:  CoiOptionChoice[];
}

export interface CoiShipData {
  shipName:            string;
  bpv:                 number;
  weaponStatus:        number;
  coiBudget:           number;
  allowTBombs:         boolean;
  allowCommandos:      boolean;
  maxTBombs:           number;
  maxDroneSpeed:       number | null;
  heavyWeapons:        CoiHeavyWeapon[];
  droneRacks:          CoiDroneRack[];
  availableDroneTypes: CoiDroneType[];
  convertibleShuttles: { name: string; types: string[] }[];
  maxPreparedShuttles: number;        // WS2=1, WS3=2, else 0
  optionMounts?:       CoiOptionMount[];  // Orion only (G15.4)
}

export interface CoiSideData {
  faction:      string;
  name:         string;
  ships:        CoiShipData[];
  cartel?:      string | null;  // scenario-fixed Orion cartel (G15.44), or null if the player picks
  cartelPinned?: boolean;
}

export interface CoiShuttlePrepEntry {
  shuttleName:    string;
  type:           string;          // "suicide" | "scatterpack" | "wildweasel"
  energyPerTurn?: number;          // suicide only
  drones?:        string[];        // scatterpack only
}

/** Per-ship COI selections to POST to /api/games/{id}/coi */
export interface CoiSubmission {
  [shipName: string]: {
    extraBoardingParties?: number;
    convertBpToCommando?:  number;
    extraCommandoSquads?:  number;
    extraTBombs?:          number;
    droneRackLoadouts?:    Record<string, string[]>;
    weaponArmingModes?:    Record<string, 'STANDARD' | 'OVERLOAD' | 'SPECIAL' | 'ROLLING'>;
    photonOverload?:       Record<string, number>;  // free WS-III overload energy per tube (S4.32)
    specialShuttlePrep?:   CoiShuttlePrepEntry[];
    optionMounts?:         Record<string, string>;  // mount designator → option name (G15.4)
    cartel?:               string;                   // the fleet's cartel, echoed per ship (G15.44)
  };
}

export const gameApi = {
  createGame(hostName: string): Promise<CreateGameResponse> {
    return request('/api/games', {
      method: 'POST',
      body: JSON.stringify({ name: hostName }),
    });
  },

  joinGame(gameId: string, playerName: string): Promise<JoinGameResponse> {
    return request(`/api/games/${gameId}/join`, {
      method: 'POST',
      body: JSON.stringify({ name: playerName }),
    });
  },

  getStatus(gameId: string): Promise<GameStatus> {
    return request(`/api/games/${gameId}/status`);
  },

  getLobbyState(gameId: string): Promise<unknown> {
    return request(`/api/games/${gameId}/lobby`);
  },

  // --- Fleet building (S8.0) ---

  /**
   * The ship catalogue. Fetched whole and filtered in the browser: a force may draw on several
   * allied empires (S8.6) and the builder switches between them freely, and the lot is ~17KB.
   */
  listShips(factions?: string[]): Promise<CatalogShip[]> {
    const query = factions?.length
      ? '?' + factions.map(f => `faction=${encodeURIComponent(f)}`).join('&')
      : '';
    return request(`/api/games/ships${query}`);
  },

  validateFleet(spec: FleetSpec): Promise<FleetValidation> {
    return request('/api/games/fleets/validate', {
      method: 'POST',
      body: JSON.stringify(spec),
    });
  },

  /** Assemble a battle from saved fleets, in place of naming a scenario file. */
  loadFleetsIntoGame(
    gameId: string, hostToken: string, setup: FleetGameSetup,
  ): Promise<{
    message: string;
    fleets: Array<{ fleetId: string; name: string; legal: boolean }>;
    terrain: string;
    terrainSeed: number;
  }> {
    return request(`/api/games/${gameId}/fleets`, {
      method: 'POST',
      headers: { 'X-Player-Token': hostToken },
      body: JSON.stringify(setup),
    });
  },

  /**
   * COI data for the battle this game is sitting down to. Game-scoped rather than by scenario
   * id, because a battle assembled from fleets is not a file on disk.
   */
  getGameCoiData(gameId: string): Promise<CoiSideData[]> {
    return request(`/api/games/${gameId}/coi-data`);
  },

  // --- Deployment ---

  /** Your own setup and the ground you may use. Authenticated: placements are secret. */
  getDeployment(gameId: string, token: string): Promise<DeploymentState> {
    return request(`/api/games/${gameId}/deployment`, { headers: { 'X-Player-Token': token } });
  },

  /** Set your ships down. Replaces your whole setup, so partial work is fine. */
  submitDeployment(
    gameId: string, token: string, placements: Placement[],
  ): Promise<{ placed: number; complete: boolean }> {
    return request(`/api/games/${gameId}/deployment`, {
      method: 'POST',
      headers: { 'X-Player-Token': token },
      body: JSON.stringify({ placements }),
    });
  },

  /** Lay them out somewhere legal, as a starting point to adjust. */
  autoArrangeDeployment(gameId: string, token: string): Promise<{ complete: boolean }> {
    return request(`/api/games/${gameId}/deployment/auto`, {
      method: 'POST',
      headers: { 'X-Player-Token': token },
    });
  },

  /** Finished, or not after all — reversible until the last player commits. */
  setDeploymentDone(
    gameId: string, token: string, done: boolean,
  ): Promise<{ done: boolean; allDone: boolean }> {
    return request(`/api/games/${gameId}/deployment/done`, {
      method: 'POST',
      headers: { 'X-Player-Token': token },
      body: JSON.stringify({ done }),
    });
  },

  listFleets(): Promise<FleetSummary[]> {
    return request('/api/games/fleets');
  },

  getFleet(id: string): Promise<{ fleet: FleetSpec; validation: FleetValidation }> {
    return request(`/api/games/fleets/${encodeURIComponent(id)}`);
  },

  saveFleet(spec: FleetSpec): Promise<{ id: string; validation: FleetValidation }> {
    return request('/api/games/fleets', {
      method: 'POST',
      body: JSON.stringify(spec),
    });
  },

  deleteFleet(id: string): Promise<{ deleted: string }> {
    return request(`/api/games/fleets/${encodeURIComponent(id)}`, { method: 'DELETE' });
  },

  listScenarios(): Promise<ScenarioSummary[]> {
    return request('/api/games/scenarios');
  },

  loadScenario(gameId: string, hostToken: string, scenarioId: string): Promise<{ message: string }> {
    return request(`/api/games/${gameId}/scenario`, {
      method: 'POST',
      headers: { 'X-Player-Token': hostToken },
      body: JSON.stringify({ scenarioId }),
    });
  },

  startGame(gameId: string, playerToken: string): Promise<{ message: string }> {
    return request(`/api/games/${gameId}/start`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
    });
  },

  /** Host-only: returns the full player list including tokens (needed for ship assignment). */
  getPlayers(gameId: string, hostToken: string): Promise<PlayerListing[]> {
    return request(`/api/games/${gameId}/players`, {
      headers: { 'X-Player-Token': hostToken },
    });
  },

  assignShip(gameId: string, hostToken: string, playerToken: string, shipName: string): Promise<{ message: string }> {
    return request(`/api/games/${gameId}/assign`, {
      method: 'POST',
      headers: { 'X-Player-Token': hostToken },
      body: JSON.stringify({ playerToken, shipName }),
    });
  },

  getGameState(gameId: string, playerToken: string): Promise<unknown> {
    return request(`/api/games/${gameId}/state`, {
      headers: { 'X-Player-Token': playerToken },
    });
  },

  getFireOptions(
    gameId: string,
    playerToken: string,
    attacker: string,
    target: string,
  ): Promise<{ range: number; adjustedRange: number; shieldNumber: number; weaponsInArc: string[] }> {
    return request(
      `/api/games/${gameId}/fire-options?attacker=${encodeURIComponent(attacker)}&target=${encodeURIComponent(target)}`,
      { headers: { 'X-Player-Token': playerToken } },
    );
  },

  /**
   * Every unit this attacker can fire at, with the figures /fire-options gives for one of
   * them. One call per ship rather than one per candidate, so the pad can list targets by
   * name instead of making the player find them on the map.
   */
  getFireTargets(
    gameId: string,
    playerToken: string,
    attacker: string,
  ): Promise<import('../components/FireOrdersPad').FireCandidate[]> {
    return request(
      `/api/games/${gameId}/fire-targets?attacker=${encodeURIComponent(attacker)}`,
      { headers: { 'X-Player-Token': playerToken } },
    );
  },

  /**
   * Every unit this ship may send a seeking weapon at, with range, whether it holds a
   * lock-on, and which plasma launchers bear on it. A drone rack has no arc, so a candidate
   * with no launchers listed is still a perfectly good drone target.
   */
  getLaunchTargets(
    gameId: string,
    playerToken: string,
    attacker: string,
  ): Promise<import('../components/LaunchOrdersPad').LaunchCandidate[]> {
    return request(
      `/api/games/${gameId}/launch-targets?attacker=${encodeURIComponent(attacker)}`,
      { headers: { 'X-Player-Token': playerToken } },
    );
  },

  getCoiData(scenarioId: string): Promise<CoiSideData[]> {
    return request(`/api/games/scenarios/${scenarioId}/coi-data`);
  },

  listCartels(): Promise<Cartel[]> {
    return request(`/api/games/scenarios/cartels`);
  },

  getHarOptions(
    gameId: string,
    playerToken: string,
    attacker: string,
    target: string,
  ): Promise<{ code: string; label: string }[]> {
    return request(
      `/api/games/${gameId}/har-options?attacker=${encodeURIComponent(attacker)}&target=${encodeURIComponent(target)}`,
      { headers: { 'X-Player-Token': playerToken } },
    );
  },

  // Owner-only: guard posts are secret (D7.831) and absent from the broadcast state
  getGuardOptions(
    gameId: string,
    playerToken: string,
    ship: string,
  ): Promise<GuardOptions> {
    return request(
      `/api/games/${gameId}/guard-options?ship=${encodeURIComponent(ship)}`,
      { headers: { 'X-Player-Token': playerToken } },
    );
  },

  submitCoi(gameId: string, playerToken: string, body: CoiSubmission):
      Promise<{ message: string; warnings?: Record<string, string[]> }> {
    return request(`/api/games/${gameId}/coi`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify(body),
    });
  },

  submitAction(
    gameId: string,
    playerToken: string,
    body: Record<string, unknown>,
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify(body),
    });
  },

  boardingAction(
    gameId: string,
    playerToken: string,
    shipName: string,
    targetName: string,
    normalParties: number,
    commandoParties: number,
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({
        type:            'BOARDING_ACTION',
        shipName,
        targetName,
        normalParties,
        commandoParties,
      }),
    });
  },

  identifySeekers(
    gameId: string,
    playerToken: string,
    shipName: string,
    seekerNames: string[],
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({ type: 'IDENTIFY_SEEKERS', shipName, seekerNames }),
    });
  },

  submitReinforcement(
    gameId: string,
    playerToken: string,
    reinforcements: Array<{ shipName: string; shieldNumber: number; power: number }>,
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({ type: 'SUBMIT_REINFORCEMENT', reinforcements }),
    });
  },

  /**
   * C10.3: announce that Erratic Maneuvers start or stop. It comes into force at the END
   * of the current impulse (C10.311), never at the moment of announcement.
   */
  announceEm(
    gameId: string,
    playerToken: string,
    shipName: string,
    on: boolean,
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({ type: 'ANNOUNCE_EM', shipName, emOn: on }),
    });
  },

  placeTBomb(
    gameId: string,
    playerToken: string,
    shipName: string,
    col: number,
    row: number,
    isReal: boolean,
    shieldNumber?: number,
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({
        type:         'PLACE_TBOMB',
        shipName,
        hexCol:       col,
        hexRow:       row,
        pseudo:       !isReal,   // server treats pseudo=true as dummy
        shieldNumber: shieldNumber ?? 0,
      }),
    });
  },
};
