import type { CatalogShip } from '../api/gameApi';

/** A hull and the refitted versions of it the catalogue lists. */
export interface HullFamily {
  base: CatalogShip;
  variants: CatalogShip[];
}

/**
 * Collapse a line group's ships into HULL FAMILIES.
 *
 * ## The problem
 *
 * A refitted hull used to be its own shelf row, so the Lyrans showed 71 rows where they have 26
 * ships — CW, CW+, CW+p and CWB one under another, four near-identical long names a reader had to
 * decode "+p" against "B" to tell apart. The owner's word for it was alphabet soup.
 *
 * Worse than the clutter: those four rows *are* the purchasing decision, and spread out like that
 * they hide it. The question is "is the power pack worth 15 points?" — and it only reads as a
 * question when the variants sit together with their costs beside them.
 *
 * ## Why the family must come from the data
 *
 * `refitOf` is sent for exactly this, and it cannot be inferred:
 *
 * - Grouping by `typeName` merges the Klingon **D6 with the D7** — both are "Battlecruiser".
 * - Grouping by the type code's prefix merges the **D6 with the D6D**, which is a different ship
 *   rather than a refit of one, and misses **D7L** entirely, whose base is the D7C.
 *
 * Both of those were tried before the server grew the field. Only the hull knows, so the hull says.
 *
 * ## The out-of-service case
 *
 * A variant whose base is not in service in the scenario year (S8.131) has no row to hang under, so
 * it stands on its own rather than disappearing: a D7K is legal in Y180 whether or not a bare D7 is
 * still being built.
 */
export function familiesOf(ships: CatalogShip[]): HullFamily[] {
  const byBase = new Map<string, { base: CatalogShip | null; variants: CatalogShip[] }>();
  for (const ship of ships) {
    const key = ship.faction + '/' + (ship.refitOf ?? ship.type);
    if (!byBase.has(key))
      byBase.set(key, { base: null, variants: [] });
    const fam = byBase.get(key)!;
    if (ship.refitOf) fam.variants.push(ship);
    else fam.base = ship;
  }

  const out: HullFamily[] = [];
  for (const fam of byBase.values()) {
    if (fam.base) out.push({ base: fam.base, variants: fam.variants });
    else for (const v of fam.variants) out.push({ base: v, variants: [] });
  }
  return out;
}

/**
 * The hull a family resolves to for a given set of ticked refits, or null when there is none.
 *
 * Null means the combination is legal under S3.24 and the data can now express it, but no catalogue
 * row describes it — the server lists the variants a hull was NAMED in, not every combination of its
 * switches. So `CW` with the power pack but *not* the phaser refit has nothing to buy yet, which is
 * the next slice. The caller disables the row rather than silently adding a different ship.
 *
 * Compared as a SET, because the order refits were ticked in is not a fact about the hull.
 */
export function resolveFamily(fam: HullFamily, codes: string[]): CatalogShip | null {
  if (codes.length === 0) return fam.base;
  const want = [...codes].sort().join(',');
  return fam.variants.find(
    v => [...(v.appliedRefits ?? [])].sort().join(',') === want) ?? null;
}

/**
 * Ticking a refit also ticks what it requires.
 *
 * The Klingon K refit arrives on top of the B refit — a D6K has B's shields as well as its own
 * phaser-1s — so offering K on its own would offer a ship that does not exist. Pulling the
 * requirement in is friendlier than refusing the click, and it is the hull's own data that says so.
 *
 * Un-ticking leaves requirements alone: a player who unticks K has not asked to lose B.
 */
export function toggleRefit(chosen: string[], code: string, requires: string[]): string[] {
  if (chosen.includes(code))
    return chosen.filter(c => c !== code);
  return [...new Set([...chosen, code, ...requires])];
}

/**
 * Does a hull answer to what the player typed?
 *
 * Three fields, because those are the three things a buyer knows a ship by and each is the only
 * one that works for some searches: the TYPE code ("CWL"), the class ("war cruiser" — which
 * differs from the line name on 305 of 355 hulls), and the LINE ("police"). Dropping any one of
 * them silently loses a whole way of searching, which is why this is pinned by tests rather than
 * left inline in the component.
 *
 * Substring rather than prefix, so "cw" reaches the CWE and the CWL. An empty needle matches
 * everything, so the caller need not special-case "no search".
 */
export function matchesSearch(
  ship: Pick<CatalogShip, 'type' | 'typeName' | 'lineName'>, search: string): boolean {
  const needle = search.trim().toLowerCase();
  if (needle === '') return true;
  return (ship.type ?? '').toLowerCase().includes(needle)
    || (ship.typeName ?? '').toLowerCase().includes(needle)
    || (ship.lineName ?? '').toLowerCase().includes(needle);
}
