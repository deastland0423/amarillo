/**
 * Thin wrappers around the Amarillo REST API.
 * All paths are relative — Vite proxies /api → localhost:8080 in dev.
 */

import type { ShipObject } from '../types/gameState';
import { accountId } from '../game/playerIdentity';

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
  /**
   * "true" when the server recognised this account and handed back a seat it already held,
   * rather than seating a new player. Strings because the endpoint returns a Map<String,String>.
   */
  resumed?: string;
  /** "true" when the returned seat is the host's — a resumed host must get the host screen. */
  isHost?: string;
}

export interface GameStatus {
  gameId: string;
  started: boolean;
  /**
   * True for a battle restored from a save rather than newly set up.
   *
   * Needed for ROUTING: a restored battle is already `started`, so a client that only knows
   * `started` sends the host straight to the map — where there is no way to hand out the ships,
   * because assignment lives on the pre-game screen.
   */
  restored?: boolean;
  /** How many ships nobody has claimed; with `restored`, this is what decides where to land. */
  unassignedShips?: number;
  players: Array<{
    name: string;
    role: 'host' | 'player';
    ships: string[];
  }>;
}

/** One row in the list of saved battles. */
export interface SavedBattle {
  id: string;
  label?: string;
  savedAt?: string;
  turn?: number;
  scenario?: string;
  /** False when this build could not parse the file; `error` says why. */
  readable: boolean;
  error?: string;
}

export interface OpenedSave {
  gameId: string;
  hostToken: string;
  turn: number;
  unassignedShips: string[];
  /** Things the load could not fully restore — a changed hull, a craft in flight. */
  problems: string[];
  message: string;
}


// ---------------------------------------------------------------------------
// Fleet building (S8.0)
// ---------------------------------------------------------------------------

/** A ship on the shelf. Prices come from the validator, not the JSON, so they cannot drift. */
export interface CatalogShip {
  faction:       string;
  type:          string;   // SSD Type designation, e.g. "D7C"
  name:          string;
  /**
   * The class written out: "Command Cruiser", "Heavy Carrier", "Galactic Survey Cruiser". Differs
   * from `lineName` on 305 of 355 hulls — a CC and a CVB both sit on the CA line — so this is what
   * tells a reader what the hull actually is, rather than which family it belongs to.
   */
  typeName:      string;
  line:          string;   // hull family, e.g. "CA"
  lineName:      string;   // "Heavy Cruiser"
  lineOrder:     number;   // display order from shiplines.json; big warships first, civilians last
  lineCivilian:  boolean;  // a freighter line: the shelf rules off above the first of these
  // An OUTER grouping, absent on almost every ship. Romulan Eagle/Kestrel/Hawk so far.
  series?:       string;   // "KESTREL"
  seriesName?:   string;   // "Kestrel series"
  seriesOrder?:  number;   // catalogue order of the series section
  seriesAbout?:  string;   // one line of history, for the section tooltip
  sizeClass:     number;
  serviceYear:   number;
  commandRating: number;
  bpv:           number;   // combat BPV
  fighterBpv:    number;   // fighters it comes with
  cost:          number;   // what it actually costs (economic for scouts, plus fighters)
  coiAllowance:  number;   // most it may spend on Commander's Options (S3.2)
  /**
   * J4.61/J4.62: "CAPABLE" flies its own fighters, "CASUAL" only services other ships', absent is
   * neither. NOT the same question as `requiresEscort`, which is S8.315 fleet legality — a casual
   * carrier brings no escort obligation, and a hull can require escorts without being a carrier.
   */
  carrierClass?: string;
  isScout:       boolean;
  isLeader:      boolean;
  isEscort:      boolean;
  requiresEscort: boolean;   // S8.315: cannot be fielded without escorts
  isBCH:         boolean;
  /**
   * REFITS (S3.24). On a refitted hull: the base it was built from, and which refits made it.
   * On a BASE hull: what may be fitted to it, with each refit's year and BPV.
   *
   * `refitOf` is the shelf's grouping key, and it cannot be inferred. Grouping by `typeName` would
   * merge the Klingon D6 with the D7, both being "Battlecruiser"; grouping by type-code prefix
   * would merge the D6 with the D6D, which is a different ship rather than a refit of one. Only the
   * data knows, which is why it says.
   */
  refitOf?:        string;
  appliedRefits?:  string[];
  refitsAvailable?: RefitOffer[];
}

/** One refit a base hull may be fitted with (S3.24). */
export interface RefitOffer {
  /** The marker history wrote on the hull: "B", "K", "u", "+", "p". */
  code: string;
  /** For a player: "B refit", "Phaser-1 refit". */
  name: string;
  /**
   * What to write on the shelf button, when the hull's data says. Usually absent — the shelf then
   * falls back to the name with a trailing " refit" stripped, which reads well for "Phaser-1
   * refit" and badly for "+ refit", whose bare "+" collides with the cost beside it.
   */
  label?: string | null;
  /** The year it became available — a refit cannot be fielded before it exists. */
  year: number;
  /** What it adds to the hull's BPV. This is the purchasing decision. */
  bpv: number;
  /** Other refit codes it arrives on top of. */
  requires: string[];
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
    /**
     * The counter this hull is drawn as, resolved from the ship library at broadcast time, or
     * absent to fall back to a faction circle. Here so the deployment map can draw a fleet as
     * it will actually look, rather than as circles that transform the moment battle starts.
     */
    tokenArt?:    string | null;
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
  /**
   * Orion option-mount choices bought with the ship (G15.4), mount designator → Annex #8B name.
   * G15.415 puts a mount change in a shipyard "taking several weeks or months", so the loadout
   * is part of the purchase and is saved with the fleet — and is part of the PRICE, since
   * G15.411 adds each option's cost to the ship's BPV.
   */
  optionMounts?: Record<string, string>;
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
  /**
   * The Orion cartel this force flies for (G15.44, R8.1). On the FLEET because the quota is on
   * the force: "The limits below apply to the entire Orion battle force."
   */
  cartel?:   string;
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
    // Either key. Error bodies are meant to carry `error`, but the action endpoint's
    // "You do not own ship" 403 sent `message` — so the one refusal a player is most likely to
    // meet arrived as a bare "HTTP 403" with no reason at all, and a playtester could only report
    // the status. Reading both means a server that uses the other key still explains itself.
    throw new Error(body.error ?? body.message ?? `HTTP ${res.status}`);
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
  canLoadAntiDrones?: boolean; // FD3.70: only a type-G has the anti-drone targeting system
  antiDroneSpace?:    number;  // spaces one anti-drone round costs (0.5)
}

export interface CoiDroneType {
  name:   string;
  speed:  number;
  damage: number;
  rack:   number;   // spaces consumed per drone
  /**
   * What KIND of drone it is, from core. The suffix in the NAME is speed and nothing else — none
   * is 8 or 12, M is 20, F is 32 — so a Type-I and a Type-IM are the same drone at two speeds and
   * nothing in the name says so. Group by this, never by parsing the name: "TypeIV" starts with
   * "TypeI", so prefix matching gets heavy drones wrong immediately.
   */
  family:      string;
  familyLabel: string;
  /**
   * Impulses before it runs out. Varies WITHIN a family — a Type-II is a standard drone with 64
   * instead of 96 — so it belongs on each drone rather than a family heading. A dogfight drone
   * has 32, which is one turn, and is the trap most worth surfacing.
   */
  endurance:   number;
  selfGuiding: boolean;   // needs no controller (FD1.7)
  dogfight:    boolean;   // J4.241's DFD — the type-VI family
  /**
   * FD2.54: what it does to a SHIP, which for a dogfight drone is not its printed warhead — two
   * points rather than eight, "because the tiny warhead is designed to score a direct hit on a
   * fighter engine instead of damaging the shields of a ship". Equal to `damage` for every other
   * drone. Sent rather than derived so the picker never holds a rules number.
   */
  damageVsShip: number;
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

/**
 * A hull's option mounts as the FLEET BUILDER needs them (G15.4): asked of a hull on the shelf,
 * before any game exists, because G15.415 makes the loadout part of the purchase.
 *
 * Same shape as CoiOptionMount minus currentOption, which only a ship in a battle has.
 */
export interface HullOptionMounts {
  faction:      string;
  type:         string;
  optionMounts: {
    designator:     string;
    position:       string;   // "CENTERLINE" | "WING"
    arcs:           string[];
    legalOptions:   CoiOptionChoice[];
  }[];
}

export interface CoiShipData {
  shipName:            string;
  bpv:                 number;
  weaponStatus:        number;
  coiBudget:           number;
  /**
   * Where the budget came from (S3.211). The basis is the "Effective Adjusted Combat BPV" —
   * hull plus fighters — which for a carrier is well above `bpv`, so the budget needs to be
   * able to say why it is bigger than a share of the hull.
   */
  coiBudgetPercent?:   number;
  coiBudgetBasis?:     number;
  coiFighterBpv?:      number;
  allowTBombs:         boolean;
  allowCommandos:      boolean;
  maxTBombs:           number;
  /** S3.2/J4.816: extra deck crews this ship may hire; 0 unless it is a capable carrier. */
  maxExtraDeckCrews?:  number;
  deckCrews?:          number;
  extraDeckCrewCost?:  number;
  /** S4.10-S4.12: the fighters aboard, and how many may start ready (-1 = a crew budget). */
  /**
   * Fighters the deck crews could arm (S4.10-S4.12). `type` is the catalogue KEY, used to tell
   * whether a squadron is mixed; `designation` is what to SHOW — "HAAS-E", not "haas_e".
   */
  fighters?:           { name: string; type: string; designation?: string }[];
  maxArmedFighters?:   number;
  maxDroneSpeed:       number | null;
  heavyWeapons:        CoiHeavyWeapon[];
  droneRacks:          CoiDroneRack[];
  /**
   * J4.62/J4.621: a casual carrier's fighter facilities — an escort with the apparatus to service
   * a carrier's fighters and none of its own. Absent on every other ship, so the control is simply
   * not rendered rather than rendered empty.
   *
   * `models` are the fighters the hull's LINE fields in this scenario's year, filtered to those a
   * facility can be built for: J4.898 lets EW pods be loaded in any box at all, so a pure EW
   * fighter has no facility of its own and offering it would equip the ship with nothing.
   */
  fighterFacilities?:  {
    count:       number;
    line:        string;
    defaultType: string | null;
    models:      { role: string; type: string; name: string }[];
  };
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
    extraDeckCrews?:       number;
    armedFighters?:        string[];
    droneRackLoadouts?:    Record<string, string[]>;
    antiDroneLoadouts?:    Record<string, number>;   // rackIndex -> anti-drone rounds (FD3.70)
    weaponArmingModes?:    Record<string, 'STANDARD' | 'OVERLOAD' | 'SPECIAL' | 'ROLLING'>;
    photonOverload?:       Record<string, number>;  // free WS-III overload energy per tube (S4.32)
    specialShuttlePrep?:   CoiShuttlePrepEntry[];
    optionMounts?:         Record<string, string>;  // mount designator → option name (G15.4)
    cartel?:               string;                   // the fleet's cartel, echoed per ship (G15.44)
  };
}

/** One seeking weapon this ship's aegis may try to identify (D13.3). */
export interface AegisIdCandidate {
  /** The wire identity the server keys on. */
  name: string;
  kind: string;
  range: number;
  /** D13.31: the highest die that identifies at this range. 6 means automatic. */
  needs: number;
  /** D13.321: a -1 applies, this ship having tried this same seeker in an earlier impulse. */
  repeat: boolean;
  /** Which of the viewer's own units it is pointed at — an inference, never its real target. */
  closingOn: string | null;
}

export const gameApi = {
  createGame(hostName: string): Promise<CreateGameResponse> {
    return request('/api/games', {
      method: 'POST',
      // The durable account id goes up so this browser can later come back to its own seat
      // rather than being handed a new one. See `game/playerIdentity.ts`.
      body: JSON.stringify({ name: hostName, accountId: accountId() }),
    });
  },

  joinGame(gameId: string, playerName: string): Promise<JoinGameResponse> {
    return request(`/api/games/${gameId}/join`, {
      method: 'POST',
      body: JSON.stringify({ name: playerName, accountId: accountId() }),
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
   *
   * `year` is NOT a filter — the shelf still hides out-of-service hulls client-side. It is
   * there because a carrier's price depends on it: S8.131 makes the date decide which fighters
   * it flies and S8.11 charges for them, so a Kzinti CVS is 243 points in Y170 and 315 in Y183.
   * Omit it and every ship is quoted as its own service year built it, which is what browsing
   * without a date should show.
   */
  listShips(factions?: string[], year?: number): Promise<CatalogShip[]> {
    const parts = (factions ?? []).map(f => `faction=${encodeURIComponent(f)}`);
    if (year && year > 0) parts.push(`year=${year}`);
    const query = parts.length ? '?' + parts.join('&') : '';
    return request(`/api/games/ships${query}`);
  },

  /**
   * One hull in full, for the fleet builder's ship viewer.
   *
   * Returns the same `ShipObject` the battle map renders, because the viewer renders it with the
   * same `SsdPanel` — a catalogue-shaped response would be a second description of a ship and the
   * two would drift. Nothing here is secret: SSDs are printed in the rulebook, which is why that
   * panel has always worked on an enemy ship.
   *
   * `year` matters for the same reason it does on the shelf, and more visibly: S8.131 makes the
   * date decide which fighters a carrier flies, so the bays come back with a different air wing in
   * Y168 and Y183.
   */
  shipDetail(faction: string, type: string, year?: number): Promise<ShipObject> {
    const query = year && year > 0 ? `?year=${year}` : '';
    return request(`/api/games/ships/${encodeURIComponent(faction)}`
      + `/${encodeURIComponent(type)}${query}`);
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

  /**
   * Hand a whole side to one player.
   *
   * `heldByOthers` names ships left where they were because another player already holds them —
   * a Commander's Options loadout is keyed to a token, so they are reported rather than seized.
   */
  assignSide(gameId: string, hostToken: string, playerToken: string, team: string):
      Promise<{ assigned: string[]; heldByOthers: string[]; message: string }> {
    return request(`/api/games/${gameId}/assign-side`, {
      method: 'POST',
      headers: { 'X-Player-Token': hostToken },
      body: JSON.stringify({ playerToken, team }),
    });
  },

  /**
   * End a game for everybody and free it on the server. Host only.
   *
   * Not the same as leaving: Leave keeps the seat so a player can come back, while this removes
   * the game entirely. Afterwards every request for it answers 404, which the client already
   * reads as "that game is gone" when clearing a stale saved seat.
   */
  endGame(gameId: string, hostToken: string): Promise<{ ended: string; message: string }> {
    return request(`/api/games/${gameId}/end`, {
      method: 'POST',
      headers: { 'X-Player-Token': hostToken },
    });
  },

  // ---- Saved battles ----

  listSaves(): Promise<SavedBattle[]> {
    return request('/api/games/saves');
  },

  /**
   * Take a save of a game in progress. Host only, and only between turns — the snapshot is
   * designed for a turn boundary, where the per-impulse state it deliberately does not carry is
   * empty. The server refuses otherwise and says which moment to use.
   */
  saveGame(gameId: string, hostToken: string, label?: string):
      Promise<{ id: string; label: string; turn: number; message: string }> {
    return request(`/api/games/${gameId}/save`, {
      method: 'POST',
      headers: { 'X-Player-Token': hostToken },
      body: JSON.stringify(label ? { label } : {}),
    });
  },

  /**
   * Open a saved battle as a NEW game, with this caller as its host.
   *
   * The ships come back unclaimed on purpose: a save records which TEAM held each ship, never
   * which person, so whoever is in the room gets assigned to them. That is what lets a battle be
   * continued by different players than fought it.
   */
  openSave(saveId: string, hostName: string): Promise<OpenedSave> {
    return request(`/api/games/saves/${saveId}/open`, {
      method: 'POST',
      body: JSON.stringify({ name: hostName, accountId: accountId() }),
    });
  },

  deleteSave(saveId: string): Promise<{ deleted: string }> {
    return request(`/api/games/saves/${saveId}`, { method: 'DELETE' });
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
   * The same question, narrowed to what this ship's AEGIS may engage (D13.21/D13.23): size class
   * 6 or smaller, within six hexes, with a lock-on.
   *
   * Narrowed on the server rather than filtered here, because the eligibility IS the rule — and
   * because "size-6 and smaller" counts the SFB way, where class 1 is a starbase and the numbers
   * grow as the hull shrinks. A client filter written from the words would have let through a PF
   * at class 5.
   */
  getAegisTargets(
    gameId: string,
    playerToken: string,
    attacker: string,
  ): Promise<import('../components/FireOrdersPad').FireCandidate[]> {
    return request(
      `/api/games/${gameId}/fire-targets?aegis=true&attacker=${encodeURIComponent(attacker)}`,
      { headers: { 'X-Player-Token': playerToken } },
    );
  },

  /**
   * What this ship's aegis may attempt to IDENTIFY (D13.3) — a different question from what it may
   * shoot, and so a different endpoint.
   *
   * Aegis FIRE needs a lock-on, a weapon that bears and a target of size class 6 or smaller
   * (D13.21/D13.23). Identification needs none of that: it is the sensor suite reading an incoming
   * seeker, and its only distance rule is its own table, which stops at six hexes. A seeker you can
   * see but cannot shoot must still be identifiable, since identifying it is how you decide whether
   * shooting is worth one of only four firings.
   *
   * Each row carries `needs`, the die that identifies at this range, and `repeat`, whether
   * D13.321's -1 applies because this ship's last attempt in an EARLIER impulse was at this same
   * seeker. Both computed on the server: the table is a rule, and D13.322's "attempts during the
   * same impulse do not count as previous to each other" is a subtlety no client should reproduce.
   */
  getAegisIdTargets(
    gameId: string,
    playerToken: string,
    attacker: string,
  ): Promise<AegisIdCandidate[]> {
    return request(
      `/api/games/${gameId}/aegis-id-targets?attacker=${encodeURIComponent(attacker)}`,
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

  /**
   * What fits each of a hull's option mounts in a given year (G15.4). NOT filtered by cartel:
   * G15.441's quota is on the whole force, so no single option is legal or illegal alone - the
   * producing empires come back instead, for ./cartelQuota to tier.
   */
  hullOptionMounts(faction: string, type: string, year: number): Promise<HullOptionMounts> {
    return request(
      `/api/games/ships/${encodeURIComponent(faction)}/${encodeURIComponent(type)}`
      + `/option-mounts?year=${year}`);
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

  /**
   * J4.922: point a fighter at the unit it receives lent EW from, or pass null to stop. One
   * source at a time, and a change the player made is held for eight impulses — both enforced
   * in core, which is why this only carries the name.
   */
  designateEwSource(
    gameId: string,
    playerToken: string,
    fighterName: string,
    sourceName: string | null,
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({
        type:         'DESIGNATE_EW_SOURCE',
        shipName:     fighterName,
        ewSourceName: sourceName ?? '',
      }),
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
