import type {
  CoiDroneAddition, CoiDroneModule, CoiDroneType, CoiFighterLoadout, CoiFighterRail,
  CoiShipData,
} from '../api/gameApi';
import { rackSurcharge } from './coiDroneCost';

/**
 * The arithmetic behind the fighter loadout surface (FD10.646, J4.23).
 *
 * <h2>Why this is a module and not component state</h2>
 * Everything here can say NO to something a player asked for, and the project keeps that kind of
 * logic where vitest can reach it — there is no testing-library in this app, so anything living
 * inside a `.tsx` is untested by construction.
 *
 * <h2>What this derives and what it is told</h2>
 * It is TOLD, per rail, whether a type-III frame and a multi-warhead drone may ride there:
 * {@link CoiFighterRail.acceptsTypeIii} and {@link CoiFighterRail.acceptsMultiWarhead} come from
 * `DroneRail.accepts` in core, which is the same gate that refuses the submission. What it
 * DERIVES is only whether the drone the player just composed HAS a type-III frame or
 * submunitions — a property of the composition rather than a rule about rails.
 *
 * <h2>Bands, not squadrons</h2>
 * The decision is per fighter TYPE: a carrier has one to four of them (one hull in the game has
 * four), while squadrons are cut at twelve slots in EW-first order (J4.461) so their boundaries
 * fall across type rather than along it. The squadron rides along as a badge because J4.221 and
 * J4.93 resolve by it, but it is not what the surface is organised by.
 */

// ---------------------------------------------------------------------------- the composition

/** A drone in `DroneLoad` text: a catalogue name, then what was done to it (FD10.2). */
export interface ParsedLoad {
  basis:     string;
  modifiers: string[];
}

export function parseLoad(text: string): ParsedLoad {
  const parts = (text ?? '').split('+').map(p => p.trim()).filter(p => p.length > 0);
  return { basis: parts[0] ?? '', modifiers: parts.slice(1) };
}

/** The catalogues the dialog was given, which is all this module knows about drones. */
export interface DroneCatalogue {
  types:     CoiDroneType[];
  modules?:  CoiDroneModule[];
  additions?: CoiDroneAddition[];
}

/**
 * FD10.53's type-III guidance, which is what FD9.35 keeps off an ordinary rail.
 *
 * Two ways to have one and both count: the catalogue's own type-III, whose family is
 * SELF_GUIDING, and the guidance bolted onto another frame as an addition. Checked by FAMILY
 * rather than by the name, because "TypeIII" and "TypeIIIF" are one drone at two speeds and
 * "TypeIV" starts with "TypeI".
 */
export function hasTypeIiiFrame(load: ParsedLoad, catalogue: DroneCatalogue): boolean {
  const basis = catalogue.types.find(t => t.name === load.basis);
  if (basis?.family === 'SELF_GUIDING') return true;
  return load.modifiers.includes('TYPE_III_GUIDANCE');
}

/** FD8.0: a bus carrying type-VI submunitions instead of a warhead. */
export function isMultiWarhead(load: ParsedLoad, catalogue: DroneCatalogue): boolean {
  return load.modifiers.some(name =>
    (catalogue.modules ?? []).some(m => m.name === name && m.submunitions > 0));
}

/**
 * Why this rail will not carry this drone, or null if it will.
 *
 * The three refusals are three different rules, and they are reported separately because a
 * player who is told "that will not fit" when the real answer is "that rail cannot guide one"
 * will go looking for a smaller drone and never find the one that works.
 */
export function railRefusal(
  rail: CoiFighterRail, text: string, catalogue: DroneCatalogue,
): string | null {
  const load = parseLoad(text);
  const basis = catalogue.types.find(t => t.name === load.basis);
  if (!basis) return `${load.basis || 'that'} is not a drone this scenario offers`;

  if (hasTypeIiiFrame(load, catalogue) && !rail.acceptsTypeIii)
    return `FD9.35: only a special or heavy rail can use a type-III frame —`
      + ` this one is ${rail.railType.toLowerCase()}`;
  if (isMultiWarhead(load, catalogue) && !rail.acceptsMultiWarhead)
    return `J4.23: a ${rail.railType.toLowerCase()} rail will not carry a multi-warhead drone`;
  if (basis.rack > rail.spaces)
    return `it takes ${basis.rack} spaces and a ${rail.railType.toLowerCase()} rail holds`
      + ` ${rail.spaces}`;
  return null;
}

// ---------------------------------------------------------------------------- the bands

export interface FighterBand {
  /** The catalogue key the band groups by. */
  type:        string;
  /** What the header shows. */
  designation: string;
  /** The shape every fighter in the band has, taken from the first — they are one type. */
  rails:       CoiFighterRail[];
  fighters:    CoiFighterLoadout[];
}

/**
 * Group a squadron into one band per fighter type, in the order the bays hold them.
 *
 * Insertion order rather than sorted, so the band a player sees first is the one their carrier
 * has most of — the complement is listed superiority first in every hull in the data.
 */
export function typeBands(fighters: CoiFighterLoadout[]): FighterBand[] {
  const bands: FighterBand[] = [];
  for (const f of fighters ?? []) {
    if (!f.rails || f.rails.length === 0) continue;
    let band = bands.find(b => b.type === f.type);
    if (!band) {
      band = { type: f.type, designation: f.designation || f.type, rails: f.rails, fighters: [] };
      bands.push(band);
    }
    band.fighters.push(f);
  }
  return bands;
}

/** Every override the player has made so far: fighter name -> drone per rail, or null. */
export type LoadoutOverrides = Record<string, (string | null)[]>;

/** What this fighter's rails are carrying, counting the player's changes (FD2.31 otherwise). */
export function loadOf(fighter: CoiFighterLoadout, overrides: LoadoutOverrides): string[] {
  const chosen = overrides[fighter.name] ?? [];
  return fighter.rails.map((rail, i) => chosen[i] ?? rail.drone);
}

/**
 * The band's prevailing load, rail by rail — what the header's pickers show.
 *
 * The commonest value at each position rather than the first fighter's, so a band where eight
 * of twelve fighters were changed together reads as "the band flies this, and four differ"
 * rather than "the band flies what fighter one happens to have".
 */
export function bandDefault(band: FighterBand, overrides: LoadoutOverrides): string[] {
  return band.rails.map((rail, i) => {
    const tally = new Map<string, number>();
    for (const f of band.fighters) {
      const load = loadOf(f, overrides)[i];
      if (load != null) tally.set(load, (tally.get(load) ?? 0) + 1);
    }
    let best = rail.drone;
    let bestCount = 0;
    for (const [load, count] of tally)
      if (count > bestCount) { best = load; bestCount = count; }
    return best;
  });
}

/** Whether this fighter is one of the exceptions — what the grid marks. */
export function differsFromBand(
  fighter: CoiFighterLoadout, band: FighterBand, overrides: LoadoutOverrides,
): boolean {
  const mine = loadOf(fighter, overrides);
  const theirs = bandDefault(band, overrides);
  return mine.some((load, i) => load !== theirs[i]);
}

/** Whether anything at all has been changed from what the hull issued (FD2.31). */
export function differsFromIssue(
  fighter: CoiFighterLoadout, overrides: LoadoutOverrides,
): boolean {
  return loadOf(fighter, overrides)
    .some((load, i) => load !== fighter.rails[i]?.drone);
}

// ---------------------------------------------------------------------------- editing

/** Put one drone on one rail of one fighter, leaving every other rail as it was. */
export function setRail(
  overrides: LoadoutOverrides, fighter: CoiFighterLoadout, railIndex: number, drone: string,
): LoadoutOverrides {
  const row = [...(overrides[fighter.name] ?? [])];
  while (row.length < fighter.rails.length) row.push(null);
  row[railIndex] = drone;
  return { ...overrides, [fighter.name]: row };
}

/**
 * Put one drone on the same rail of every fighter in the band — the header picker.
 *
 * Applied to every fighter rather than only the unchanged ones, which is the "default +
 * exceptions" shape read the way a player means it: setting the band's standard rail to a probe
 * drone is a statement about the whole band, and an exception somebody made earlier on THAT rail
 * was about that rail. Exceptions on other rails survive, because only this position is touched.
 */
export function setBandRail(
  overrides: LoadoutOverrides, band: FighterBand, railIndex: number, drone: string,
): LoadoutOverrides {
  let next = overrides;
  for (const f of band.fighters) next = setRail(next, f, railIndex, drone);
  return next;
}

/** Put a fighter back to what the hull issued it (FD2.31). */
export function resetFighter(
  overrides: LoadoutOverrides, fighter: CoiFighterLoadout,
): LoadoutOverrides {
  const next = { ...overrides };
  delete next[fighter.name];
  return next;
}

// ---------------------------------------------------------------------------- the wire

/**
 * What to POST: only the fighters that differ from what they were issued, and within those,
 * only the rails that differ.
 *
 * <h2>The nulls are load-bearing</h2>
 * A rail left as issued travels as a null rather than being dropped, because the server reads
 * the list by POSITION. Compacting the gaps would slide a type-III down onto a standard rail
 * that J4.23 refuses it on — and the refusal is per fighter, so the whole fighter would be
 * rejected rather than the one rail.
 *
 * <h2>And an unchanged fighter is left out</h2>
 * Absent means "flies what the hull issued it", which is what FD2.31 already paid for. Sending
 * the issued drones back explicitly would be the same loadout and a different bill: FD2.45
 * charges for a loading, and naming one is asking for it.
 */
export function toWire(
  overrides: LoadoutOverrides, fighters: CoiFighterLoadout[],
): Record<string, (string | null)[]> | undefined {
  const out: Record<string, (string | null)[]> = {};
  for (const f of fighters ?? []) {
    if (!differsFromIssue(f, overrides)) continue;
    out[f.name] = loadOf(f, overrides)
      .map((load, i) => (load === f.rails[i]?.drone ? null : load));
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

/** Seed the editor from choices already recorded against a fleet (S8.12). */
export function seedOverrides(
  saved: Record<string, (string | null)[]> | undefined,
): LoadoutOverrides {
  const out: LoadoutOverrides = {};
  for (const [name, rails] of Object.entries(saved ?? {}))
    out[name] = [...rails];
  return out;
}

/**
 * FD2.45's bill for the squadron, mirrored for the budget readout.
 *
 * <blockquote>"The cost of improved drones (FD2.22) is paid only for a single loading of each
 * launch rack <b>and/or fighter</b>; the reloads (and drones stored on a carrier for use by its
 * fighters) are presumed to be of the same type without additional cost."</blockquote>
 *
 * So a fighter's rails are one loading and its ready rack is free, which is why this prices the
 * WIRE rather than the whole load: the rails the player did not touch were paid for with the
 * hull (FD2.31), and the server bills exactly the entries this sends.
 *
 * <p>Priced with no rack, as {@code rackSurcharge} wants for anything that is not one of
 * FD2.45's two magazine launchers — and no fighter has one, a rail holding what it holds.
 */
export function fighterLoadoutCost(
  overrides: LoadoutOverrides, fighters: CoiFighterLoadout[], ship: CoiShipData,
): number {
  const wire = toWire(overrides, fighters) ?? {};
  let total = 0;
  for (const rails of Object.values(wire))
    total += rackSurcharge(rails.filter((r): r is string => r != null), ship);
  return total;
}
