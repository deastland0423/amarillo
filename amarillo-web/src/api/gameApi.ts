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
  /**
   * False when this build cannot OPEN the file — it would not parse, or a newer build wrote it.
   * `error` says which. A newer-format save parses perfectly and would load as a battle with
   * everything this build does not know silently missing, so it is refused too.
   */
  readable: boolean;
  error?: string;
  /**
   * True when the refusal is only about the format version, so the player may overrule it and
   * accept an approximate battle. Absent or false means there is nothing to offer.
   */
  canIgnoreFormatVersion?: boolean;
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
  /**
   * @deprecated What this ship spent, as of whatever year it was last priced in. Derived, so
   * wrong the moment the fleet is used in another year; the server reads it only for files
   * written before `coi` existed. Write `coi` instead.
   */
  coiSpend?: number;
  /**
   * The Commander's Options this ship was BOUGHT with (S8.12), as choices.
   *
   * The same shape the COI endpoint takes, because it is the same purchase: S8.12 lists
   * Commander's Options under the general conditions for building a force, beside "units are
   * purchased for their combat BPV". `FleetValidator.totalCost` has always charged the spending
   * against the fleet budget — this is what it was charging for.
   */
  coi?: CoiShipChoices;
  /**
   * Orion option-mount choices bought with the ship (G15.4), mount designator → Annex #8B name.
   * G15.415 puts a mount change in a shipyard "taking several weeks or months", so the loadout
   * is part of the purchase and is saved with the fleet — and is part of the PRICE, since
   * G15.411 adds each option's cost to the ship's BPV.
   */
  optionMounts?: Record<string, string>;
  /** R8.R3's wing arc per mount: designator → "FORWARD" | "OUTBOARD". Absent means FORWARD. */
  optionMountArcs?: Record<string, string>;
  /**
   * Refit codes ticked on the BASE type (S3.24), for a combination nobody named a hull for — a
   * CR bought with the Plus refit is {type: "CR", refits: ["+"]}, because "CR+" is synthesised
   * by RefitResolver and is not a library type. `FleetSpec.ShipEntry.refits` on the server.
   *
   * <p>Declared late: `addShip` wrote the field through a spread, which skips the excess
   * property check, so TypeScript never knew about a field the wire has carried all along.
   */
  refits?: string[];
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

/**
 * One ship's standing against FD10.622 and FD10.632, as the server computes it.
 *
 * Both tiers carry their own ceiling because FD10.64 NESTS the caps: a Limited drone spends
 * Restricted allowance as well as its own, so the same two drones can be "2 of 2 restricted" and
 * "2 of 0 limited" at once and one figure could not say which cap bit.
 *
 * Every field is sent for every ship, breach or not — the gauge is the point, not the alarm.
 */
export interface DroneAvailability {
  shipName:          string;
  /** FD10.641's denominator: the warhead spaces the hull's racks HOLD, loaded or not. */
  countedSpaces:     number;
  restrictedUsed:    number;
  restrictedAllowed: number;
  restrictedPercent: number;
  limitedUsed:       number;
  limitedAllowed:    number;
  limitedPercent:    number;
  /**
   * FD10.644's second basis: the spaces of racks that hold nothing but dogfight drones, which
   * are "counted separately from all other drones" and have ceilings of their own at the same
   * percentages. Zero on every hull but the Klingon D6C today.
   *
   * Optional because a server from before FD10.644 was counted does not send these keys, and
   * the figures above are still true without them. Read them with `?? 0`, never as a number.
   */
  dogfightSpaces?:             number;
  /**
   * How much of `countedSpaces` is the squadron's rails rather than the hull's racks
   * (FD10.641 counts "fighter ready racks or launch rails"). For the gauge's tooltip only.
   */
  fighterSpaces?:              number;
  dogfightRestrictedUsed?:     number;
  dogfightRestrictedAllowed?:  number;
  dogfightLimitedUsed?:        number;
  dogfightLimitedAllowed?:     number;
  /** Whether this hull is on FD10.622's exception list — Kzinti, a big carrier, or "D%". */
  relaxed:           boolean;
}

export interface FleetValidation {
  legal:         boolean;
  cost?:         number;   // hulls and fighters
  totalCost?:    number;   // and Commander's Options
  budget?:       number;
  shipCount?:    number;
  violations:    FleetViolation[];
  unknownShips?: string[];
  /**
   * The drone caps as a gauge rather than a verdict (FD10.622/FD10.632).
   *
   * Absent when the fleet could not be resolved at all, and from an older server. The violations
   * list carries the refusal; this is what the Commander's Options form shows while the player is
   * still choosing, so the cap speaks before it is breached.
   */
  droneAvailability?: DroneAvailability[];
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

/**
 * A ready-made loadout for one rack, from core (`DroneRackPresets`).
 *
 * Every figure here was decided on the server: which drones are legal in this rack in this year
 * (FD3.5, FD10.65, FD3.0) and what the load costs the ship's option budget (S3.2). Only General
 * Availability drones are ever offered, because FD10.62's percentage caps are a property of the
 * whole SHIP and a per-rack button cannot judge them.
 *
 * It carries no label: the dialog already writes drone lists, so `presetLabel` makes one.
 */
export interface CoiRackPreset {
  drones:     string[];   // drone names, in the same form a loadout sends back
  antiDrones: number;     // FD3.70 rounds sharing the magazine
  surcharge:  number;     // BPV against this ship's Commander's Options
}

export interface CoiDroneRack {
  index:        number;
  designator:   string;
  spaces:       number;
  reloadCount:  number;
  /**
   * FD2.45's "single loading" in spaces — what the Commander's Option budget is charged for,
   * which is the whole rack for every type but two. A type-D keeps its reloads inside itself as
   * the spare magazines (FD3.43), so FD3.44 charges it for one magazine of its three and FD3.84
   * charges a type-H for two of its five.
   *
   * Optional because a server that predates it sends nothing, and the fallback then has to be
   * the old behaviour — bill the whole load — rather than a zero that would bill none of it.
   */
  paidLoadingSpaces?: number;
  defaultAmmo:  string[];  // drone type names before any COI loadout
  canLoadTypeVI: boolean;  // only TYPE_E, TYPE_G, TYPE_H
  canLoadAntiDrones?: boolean; // FD3.70: only a type-G has the anti-drone targeting system
  antiDroneSpace?:    number;  // spaces one anti-drone round costs (0.5)
  presets?:           CoiRackPreset[];  // absent on a server that predates them
}

/**
 * One rail on one fighter (J4.23), in the order a loadout addresses them.
 *
 * <p>Order is the address and it is NOT the designator: a Federation F-14B carries four special
 * rails designated E, F, E, F. Podded rails are not in the list at all (J4.962) and neither are
 * plasma rails (J4.825), so a TADS-E has four entries where a TADS has six.
 */
export interface CoiFighterRail {
  /** "STANDARD" | "LIGHT" | "SPECIAL" | "HEAVY". Shown; never used to derive what fits. */
  railType:   string;
  designator: string;
  /** What will fit on it (FD7.211). A light rail is half a space and takes only a type-VI. */
  spaces:     number;
  /**
   * FD9.35 and J4.23, asked of the rail in core and sent as answers.
   *
   * The client must not re-derive these from `railType`. They are the same rules that refuse
   * the submission, and a second copy here would be free to drift from the one that matters —
   * a dialog offering a drone the server then drops is worse than one that never offered it.
   */
  acceptsTypeIii:      boolean;
  acceptsMultiWarhead: boolean;
  /** What it is carrying as issued (FD2.31), in `DroneLoad` text — "TypeIF", "TypeI+ECM". */
  drone:      string;
}

/**
 * One fighter's rails, for the loadout surface (FD10.646).
 *
 * A separate list from {@link CoiShipData.fighters}, which answers "what can a deck crew arm"
 * (S4.10-S4.12). A fighter can have rails without needing arming and the other way about.
 */
export interface CoiFighterLoadout {
  /** The address a loadout is keyed by — the same one `armedFighters` uses. */
  name:        string;
  /** The CATALOGUE KEY, which is what a band groups by. */
  type:        string;
  /** What a band header SHOWS: "TADS-E", not "tads_e". */
  designation: string;
  /**
   * The counter the MAP draws for this craft, relative to `/tokens/`.
   *
   * Null where the art has not been drawn — the Federation line and the Hydran Stinger-3,
   * 22 of 78 fighters — and the grid falls back to a plain shape, exactly as the map does.
   */
  tokenArt?:   string | null;
  /**
   * J4.46's organisation, for the badge.
   *
   * Shown rather than navigated by: J4.461 cuts squadrons at twelve slots in EW-first order, so
   * the boundary falls ACROSS fighter type rather than along it — a Kzinti CVA's assault
   * fighters are all in its second squadron while its superiority fighters straddle both. The
   * decision a player is making here is per type, so type is what the bands are.
   */
  squadron:    string | null;
  rails:       CoiFighterRail[];
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
  selfGuiding: boolean;   // CAPABLE of guiding itself once it closes to 8 hexes (F3.42, FD5.12)
  dogfight:    boolean;   // J4.241's DFD — the type-VI family
  /**
   * FD2.54: what it does to a SHIP, which for a dogfight drone is not its printed warhead — two
   * points rather than eight, "because the tiny warhead is designed to score a direct hit on a
   * fighter engine instead of damaging the shields of a ship". Equal to `damage` for every other
   * drone. Sent rather than derived so the picker never holds a rules number.
   */
  damageVsShip: number;
  /**
   * Payload spaces this drone has to fill (FD10.4), and whether its payload can be chosen at
   * all — FD10.21's half-space dogfight frame "cannot be modified", so `modular` is false for
   * the type-VIs and they take no modules and no additions.
   */
  payloadSpaces?: number;
  modular?:       boolean;
  /**
   * Which of the catalogue's modules and additions THIS drone will accept, each decided on the
   * server by building the candidate and seeing whether the rules allow it. A combination can
   * still be illegal where each part alone is fine (FD10.443 bars ECM beside armor), and that is
   * refused on submission with its clause number rather than enumerated here.
   */
  legalModules?:   string[];
  legalAdditions?: string[];
  /**
   * FD10.24: rack spaces this drone fills once given extended range, which for a type-III is
   * TWO — "they become type-III-XX, which are two-space drones with one payload space". The
   * only modification that changes a drone's footprint, so the figure is sent rather than
   * derived. Absent when extended range is not legal for the drone at all (FD2.222).
   */
  rackWithExtendedRange?: number;
  /**
   * FD2.22's surcharge in its four terms, which are the four `DroneSpec.pointCost` is defined
   * as. Sent in parts because the dialog prices drones the player ASSEMBLES (FD10.2) and no
   * catalogue holds one of those; `coiDroneCost.ts` adds them to the modules' own costs.
   *
   * `speedCredit` is what the SHIP's BPV carries instead of the budget (S2.11/S3.221), already
   * resolved for the scenario's year — so nothing on this side ever sees a date.
   *
   * All optional together: a server from before this sends none of them, and the cost mirror
   * then stays silent rather than guessing.
   */
  engineCost?:   number;
  payloadCost?:  number;
  frameCost?:    number;
  speedCredit?:  number;
  /**
   * The engine and the credit for the frame FD10.24 grows a type-III into. Present exactly when
   * `rackWithExtendedRange` is.
   */
  engineCostWithExtendedRange?:  number;
  speedCreditWithExtendedRange?: number;
}

/** FD10.4: one payload module a drone may carry, as the options endpoint advertises it. */
export interface CoiDroneModule {
  name:  string;
  label: string;
  /** Payload spaces it fills (FD10.4) and FD10.51's price. */
  spaces: number;
  cost:   number;
  /** FD10.6's tier IN THIS SCENARIO'S YEAR, resolved on the server. */
  availability: string;
  /** FD10.41: what it contributes to the drone's single explosion. Zero for an ECM or probe. */
  strength:  number;
  /** FD12.21, and zero for everything but armor. */
  hullBonus: number;
  /**
   * FD10.402: it must sit in the forward payload space, so a drone cannot carry more than one
   * payload space of them — unless one module spans the whole payload, which is forward by
   * filling everything (FD10.432's "space(s)").
   */
  requiresForward: boolean;
  /** FD10.443: armor, which an ECM module cannot share a drone with. */
  armor: boolean;
  /** FD10.431: type-VI drones carried instead of a warhead — three or five. */
  submunitions: number;
}

/** FD10.53: something bolted to the frame, which fills no payload space. */
export interface CoiDroneAddition {
  name:  string;
  label: string;
  cost:  number;
  availability: string;
  hullBonus:    number;
  /** FD12.13's external armor, which costs speed instead of space (FD12.132). */
  externalArmor: boolean;
}

/** FD8.11: an engine for the submunitions, bought separately from the bus's own (FD8.24). */
export interface CoiSubmunitionSpeed {
  name:  string;
  speed: number;
  availability: string;
  /**
   * FD8.44: what the bus pays for EACH submunition it carries, over the speed-12 that FD2.227
   * makes a type-VI's slowest. Multiplied by the module's own `submunitions` count.
   */
  surchargePerSubmunition?: number;
}


export interface CoiOptionChoice {
  name: string;
  cost: number;   // BPV delta (Annex #8B); may be negative/fractional
  empires?: string[] | null;   // producing empires; null/absent = universal (cartel-exempt, G15.44)
  /**
   * How many ADJACENT mounts this option fills — Annex #8B's asterisk, which marks both ESGs,
   * the four plasma-G/S entries, the PPD and the stasis field generator. Absent means one.
   * The server has sent it since the endpoint was written; nothing read it until the picker
   * started greying out the mount a two-mount weapon eats.
   */
  mountsRequired?: number;
  /**
   * R8.R3's wing arc choice, as the two ARC LABELS to pick between — e.g. ["FA", "L + LF"] for
   * a disruptor in a left wing mount. Empty or absent wherever the rule leaves no choice, which
   * is everywhere except a disruptor or fusion beam in a wing.
   *
   * <p>Labels rather than a flag, computed by core, so the picker shows what it is actually
   * choosing without a second copy of the rule in the view. Index 0 is FORWARD, 1 is OUTBOARD.
   */
  arcChoices?: string[];
}

export interface Cartel {
  name:          string;
  /**
   * The empires whose weapons the cartel draws on without limit. Usually one (G15.441); the
   * Cluster has four and no operating zone, which is how G15.442's 90/10 falls out of the
   * ordinary caps rather than needing a rule of its own. @see OrionCartel
   */
  home:          string[];
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
  /**
   * The hull's runs of CONSECUTIVE option mounts (G15.4). An Annex #8B option marked with an
   * asterisk — the ESGs, the plasma-G/S entries, the PPD, the SFG — fills TWO adjacent mounts,
   * and which second mount it eats depends on what is free: on the Battle Raider's ["A","B","C"]
   * a weapon chosen at B takes A or C. Order is meaningful; a run is not a set.
   */
  adjacentRuns?: string[][];
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
  /**
   * S2.11's mandatory drone speed charge: what the ship pays for the drones its year flies.
   *
   * Deliberately NOT part of `coiBudgetBasis` (S3.211 excludes "the cost of mandatory drone
   * speed upgrades" from the figure the percentage is taken of), so it needs a field of its own
   * or it is invisible. Optional, so a stale server simply shows nothing.
   */
  droneSpeedBpv?:      number;
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
  /**
   * FD10.646/J4.23: every fighter aboard that has rails a drone goes on, and what each rail
   * will carry. Empty on a ship with no drone-armed fighters; optional so a stale server
   * simply offers no loadout surface rather than rendering an empty one.
   */
  fighterLoadouts?:    CoiFighterLoadout[];
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
  /**
   * FD10.4's third axis. The picker has always had two {EM} the numeral carries the family and
   * the suffix carries speed {EM} and the payload was never one, because no loadout could name a
   * module. Absent from a server that predates them, so the editor is simply not offered.
   */
  availableDroneModules?:     CoiDroneModule[];
  availableDroneAdditions?:   CoiDroneAddition[];
  availableSubmunitionSpeeds?: CoiSubmunitionSpeed[];
  convertibleShuttles: { name: string; types: string[] }[];
  maxPreparedShuttles: number;        // WS2=1, WS3=2, else 0
  optionMounts?:       CoiOptionMount[];  // Orion only (G15.4)
  /** Runs of CONSECUTIVE option mounts, for the two-mount options (G15.4). @see HullOptionMounts */
  adjacentMountRuns?:  string[][];
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
/**
 * One ship's Commander's Option choices (S3.2), in the form they are written down.
 *
 * Named rather than left inline because it is now recorded in two places: posted to the COI
 * endpoint when a battle is arranged, and saved against a ship in a fleet file (S8.12). Core's
 * `com.sfb.scenario.CoiChoices` is the same shape on the other side of the wire — field for
 * field, which is what lets a fleet file be read by the same `toLoadout`.
 *
 * Everything here is what the player ASKED for. Nothing derived is carried: the cost is computed
 * from the choices at the year of whatever battle they are used in (S8.132, S8.133).
 */
export interface CoiShipChoices {
  extraBoardingParties?: number;
  convertBpToCommando?:  number;
  extraCommandoSquads?:  number;
  extraTBombs?:          number;
  extraDeckCrews?:       number;
  fighterFacilityType?:  string;
  armedFighters?:        string[];
  droneRackLoadouts?:    Record<string, string[]>;
  /**
   * FD10.646: fighter name -> the drone on each of its rails, in `CoiFighterRail` order.
   *
   * A null entry is a rail left as issued, and the nulls are load-bearing: the list is
   * addressed by POSITION, so compacting them would slide a type-III down onto a standard rail
   * that J4.23 refuses it on. A fighter that was not changed is left out altogether.
   */
  fighterLoadouts?:      Record<string, (string | null)[]>;
  antiDroneLoadouts?:    Record<string, number>;   // rackIndex -> anti-drone rounds (FD3.70)
  weaponArmingModes?:    Record<string, 'STANDARD' | 'OVERLOAD' | 'SPECIAL' | 'ROLLING'>;
  photonOverload?:       Record<string, number>;  // free WS-III overload energy per tube (S4.32)
  specialShuttlePrep?:   CoiShuttlePrepEntry[];
  optionMounts?:         Record<string, string>;  // mount designator → option name (G15.4)
  optionMountArcs?:      Record<string, string>;  // designator → "FORWARD"|"OUTBOARD" (R8.R3)
  cartel?:               string;                   // the fleet's cartel, echoed per ship (G15.44)
}

export interface CoiSubmission {
  [shipName: string]: CoiShipChoices;
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
  /**
   * One hull in full. `codes` carries refits for a combination nobody named a hull for (S3.24):
   * the caller sends the BASE type and what it ticked, because "CAO" is not a library type.
   */
  shipDetail(faction: string, type: string, year?: number, codes?: string[]): Promise<ShipObject> {
    const parts: string[] = [];
    if (year && year > 0) parts.push(`year=${year}`);
    if (codes && codes.length > 0) parts.push(`codes=${encodeURIComponent(codes.join(','))}`);
    const query = parts.length > 0 ? `?${parts.join('&')}` : '';
    return request(`/api/games/ships/${encodeURIComponent(faction)}`
      + `/${encodeURIComponent(type)}${query}`);
  },

  validateFleet(spec: FleetSpec): Promise<FleetValidation> {
    return request('/api/games/fleets/validate', {
      method: 'POST',
      body: JSON.stringify(spec),
    });
  },

  /**
   * What each ship of the fleet being EDITED may buy with its option points (S8.12).
   *
   * Posts the whole spec because the fleet is not saved yet and has no game behind it. The server
   * converts it into the scenario it would become and asks the one COI builder, so the builder
   * and the pre-battle lobby cannot disagree about what is on offer.
   */
  fleetCoiData(spec: FleetSpec): Promise<CoiSideData[]> {
    return request('/api/games/fleets/coi-data', {
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
    /**
     * Present when a written situation's own date overruled the year the host asked for
     * (S8.131). The situation is a historical moment, so its date binds the fleets brought to
     * it -- but being judged against a year you did not type would otherwise be a mystery.
     */
    yearNote?: string;
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
  /**
   * @param ignoreFormatVersion open a save a NEWER build wrote, accepting that anything this
   *   build does not understand is lost. Only ever set from the player saying so.
   */
  openSave(saveId: string, hostName: string,
           ignoreFormatVersion = false): Promise<OpenedSave> {
    return request(`/api/games/saves/${saveId}/open`, {
      method: 'POST',
      body: JSON.stringify({
        name: hostName,
        accountId: accountId(),
        ...(ignoreFormatVersion ? { ignoreFormatVersion: 'true' } : {}),
      }),
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
  /**
   * A hull with refits applied on the fly (S3.24), as a shelf row. For combinations nobody
   * NAMED a hull for — most Orion refits — which the catalogue cannot list and which a fleet
   * therefore records as base type plus codes.
   */
  hullWithRefits(faction: string, type: string, codes: string[], year: number)
      : Promise<CatalogShip & { baseType: string; appliedRefits: string[] }> {
    return request(
      `/api/games/ships/${encodeURIComponent(faction)}/${encodeURIComponent(type)}`
      + `/refit?codes=${encodeURIComponent(codes.join(','))}&year=${year}`);
  },

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
