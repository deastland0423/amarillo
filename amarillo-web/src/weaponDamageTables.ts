/**
 * Static weapon damage tables mirroring the Java weapon classes.
 * These tables are stable reference data safe to keep in the frontend.
 */

// ── Phasers ──────────────────────────────────────────────────────────────────
// Indexed [roll-1][range]; roll 1 = best outcome, roll 6 = worst

export const PHASER1_TABLE: readonly number[][] = [
  [9,8,7,6,5,5,4,4,4,3,3,3,3,3,3,3,2,2,2,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],
  [8,7,6,5,5,4,3,3,3,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [7,5,5,4,4,4,3,3,3,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [6,4,4,4,4,3,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [5,4,4,4,3,3,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [4,4,3,3,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
] as const;

export const PHASER2_TABLE: readonly number[][] = [
  [6,5,5,4,3,3,3,3,3,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],
  [6,5,4,4,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [6,4,4,4,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [5,4,4,3,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [5,4,3,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [5,3,3,3,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
] as const;

// Phaser-3 and PhaserG share the same damage table
export const PHASER3_TABLE: readonly number[][] = [
  [4,4,4,3,1,1,1,1,1,1,1,1,1,1,1,1],
  [4,4,4,2,1,1,1,1,1,0,0,0,0,0,0,0],
  [4,4,4,1,0,0,0,0,0,0,0,0,0,0,0,0],
  [4,4,3,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [4,3,2,0,0,0,0,0,0,0,0,0,0,0,0,0],
  [3,3,1,0,0,0,0,0,0,0,0,0,0,0,0,0],
] as const;

// ── Fusion Beam ──────────────────────────────────────────────────────────────
export const FUSION_TABLE: readonly number[][] = [
  [13, 8, 6, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2],
  [11, 8, 5, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1],
  [10, 7, 4, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [ 9, 6, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [ 8, 5, 3, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  [ 8, 4, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
] as const;

export const FUSION_OVERLOAD_TABLE: readonly number[][] = [
  [19, 12, 9, 6, 6, 6, 6, 6, 6],
  [16, 12, 7, 4, 4, 4, 4, 4, 4],
  [15, 10, 6, 3, 3, 3, 3, 3, 3],
  [13,  9, 4, 1, 1, 1, 1, 1, 1],
  [12,  7, 4, 1, 1, 1, 1, 1, 1],
  [12,  6, 3, 0, 0, 0, 0, 0, 0],
] as const;

export const FUSION_SUICIDE_TABLE: readonly number[][] = [
  [26, 16, 12, 8, 8, 8, 8, 8, 8],
  [22, 16, 10, 6, 6, 6, 6, 6, 6],
  [20, 14,  8, 4, 4, 4, 4, 4, 4],
  [18, 12,  6, 2, 2, 2, 2, 2, 2],
  [16, 10,  6, 2, 2, 2, 2, 2, 2],
  [16,  8,  4, 0, 0, 0, 0, 0, 0],
] as const;

// FighterFusion uses the same values as standard Fusion but max range 10
export const FIGHTER_FUSION_TABLE: readonly number[][] = [
  [13, 8, 6, 4, 4, 4, 4, 4, 4, 4, 4],
  [11, 8, 5, 3, 3, 3, 3, 3, 3, 3, 3],
  [10, 7, 4, 2, 2, 2, 2, 2, 2, 2, 2],
  [ 9, 6, 3, 1, 1, 1, 1, 1, 1, 1, 1],
  [ 8, 5, 3, 1, 1, 1, 1, 1, 1, 1, 1],
  [ 8, 4, 2, 0, 0, 0, 0, 0, 0, 0, 0],
] as const;

// ── Photon Torpedo (hit-or-miss; d6 ≤ chart value = hit) ─────────────────────
export const PHOTON_HIT_CHART:      readonly number[] = [0,0,5,4,4,3,3,3,3,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1];
export const PHOTON_OVLD_HIT_CHART: readonly number[] = [6,6,5,4,4,3,3,3,3];
export const PHOTON_PROX_HIT_CHART: readonly number[] = [0,0,0,0,0,0,0,0,0,4,4,4,4,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3];

// ── Disruptor (hit-or-miss; fixed damage if hit) ─────────────────────────────
export const DISRUPTOR_HIT_CHART:      readonly number[] = [0,5,5,4,4,4,4,4,4,4,4,4,4,4,4,4,3,3,3,3,3,3,3,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2];
export const DISRUPTOR_OVLD_HIT_CHART: readonly number[] = [6,5,5,4,4,4,4,4,4];
export const DISRUPTOR_DMG_CHART:      readonly number[] = [0,5,4,4,4,3,3,3,3,3,3,3,3,3,3,3,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1];
export const DISRUPTOR_OVLD_DMG_CHART: readonly number[] = [10,10,8,8,8,6,6,6,6];

// ── Hellbore (2d6 roll; range-band lookup) ────────────────────────────────────
// Band edges: [0]=range 0–1, [1]=2, [2]=3–4, [3]=5–8, [4]=9–15, [5]=16–22, [6]=23–40
export const HELLBORE_BANDS     = [[0,1],[2,2],[3,4],[5,8],[9,15],[16,22],[23,40]] as const;
export const HELLBORE_HIT_NUMS  = [11,10,9,8,7,6,5]        as const; // ≤ this on 2d6 = hit
export const HELLBORE_ENV_DMG   = [20,17,15,13,10,8,4]     as const;
export const HELLBORE_DF_DMG    = [10, 8, 7, 6, 5, 4, 2]   as const;
export const HELLBORE_OVLD_ENV  = [30,30,25,22,22,19,19,19,19] as const; // indexed by range 0–8
export const HELLBORE_OVLD_DF   = [15,15,12,11,11, 9, 9, 9, 9] as const;

// ── Plasma Bolt ──────────────────────────────────────────────────────────────
// Strength-by-range tables, mirroring PlasmaTorpedo.java PLASMA_X_DAMAGE_BY_RANGE
// Index = range (hexes). Bolt damage = table[range] / 2.

export const PLASMA_R_BY_RANGE: readonly number[] = [50,50,50,50,50,50,50,50,50,50,50,35,35,35,35,35,25,25,25,25,25,20,20,20,20,25,10,10,10,5,1,0];
export const PLASMA_S_BY_RANGE: readonly number[] = [30,30,30,30,30,30,30,30,30,30,30,22,22,22,22,22,15,15,15,15,15,10,10,10,5,1,0];
export const PLASMA_G_BY_RANGE: readonly number[] = [20,20,20,20,20,20,20,20,20,20,20,15,15,15,15,15,10,10,10,5,1,0];
export const PLASMA_F_BY_RANGE: readonly number[] = [20,20,20,20,20,20,15,15,15,15,15,10,10,5,5,1,0];
export const PLASMA_D_BY_RANGE: readonly number[] = [10,10,10,10,10,10,8,8,8,8,8,5,5,2,2,1,0];

// Bolt hit chart: d6 ≤ this value = hit. Index = range 0–30.
export const PLASMA_BOLT_HIT_CHART: readonly number[] = [
  4,4,4,4,4,4,   // range 0–5
  3,3,3,3,3,     // range 6–10
  2,2,2,2,2,2,2,2,2,2, // range 11–20
  1,1,1,1,1,1,1,1,1,1, // range 21–30
];

function plasmaStrengthAtRange(plasmaType: string | null, range: number): number {
  const table = plasmaType === 'R' ? PLASMA_R_BY_RANGE
              : plasmaType === 'S' ? PLASMA_S_BY_RANGE
              : plasmaType === 'G' ? PLASMA_G_BY_RANGE
              : plasmaType === 'D' ? PLASMA_D_BY_RANGE
              : PLASMA_F_BY_RANGE; // F or unknown
  const r = Math.max(0, Math.min(range, table.length - 1));
  return table[r];
}

/**
 * Preview for a plasma bolt. plasmaType = "F" | "G" | "S" | "R" | "D".
 *
 * TWO ranges, because the rules use two: FP8.42 bases the to-hit on the EFFECTIVE range while
 * FP8.43 bases the damage on the TRUE range ("one-half of the warhead strength of the
 * corresponding plasma torpedo... at the true range to the target"). A scanner makes a bolt
 * harder to land, not weaker when it lands.
 *
 * This took one range for both and used it for each, so with a scanner in play the preview showed
 * a BETTER chance of hitting than the shot actually had - the mirror image of the bug the Java
 * launcher had, which read damage off the adjusted range. Both are now the same split.
 * `adjustedRange` defaults to `range` for callers with no scanner to apply.
 */
export function getPlasmaBoltPreview(
  plasmaType: string | null,
  range: number,
  adjustedRange: number = range,
): DmgRow[] {
  if (adjustedRange >= PLASMA_BOLT_HIT_CHART.length)
    return [{ roll: '1–6', damage: 0 }];
  const strength  = plasmaStrengthAtRange(plasmaType, range);
  const boltDmg   = Math.floor(strength / 2);
  const hitOn     = PLASMA_BOLT_HIT_CHART[adjustedRange];
  return hitOrMissPreview(hitOn, boltDmg);
}

// ── Preview output type ───────────────────────────────────────────────────────

export interface DmgRow {
  roll:   string;    // die face label, e.g. "1", "1–3", "≤8 (2d6)"
  damage: number;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function clampRange(range: number, max: number): number {
  return Math.max(0, Math.min(range, max));
}

function rollTablePreview(table: readonly number[][], range: number, maxRange: number): DmgRow[] {
  const r = clampRange(range, maxRange);
  return table.map((row, i) => ({ roll: String(i + 1), damage: row[r] }));
}

function hitOrMissPreview(hitOn: number, damage: number): DmgRow[] {
  if (hitOn <= 0) return [{ roll: '1–6', damage: 0 }];
  if (hitOn >= 6) return [{ roll: '1–6', damage }];
  return [
    { roll: `1–${hitOn}`,       damage },
    { roll: `${hitOn + 1}–6`,   damage: 0 },
  ];
}

function hellboreBand(range: number): number {
  for (let i = 0; i < HELLBORE_BANDS.length; i++) {
    const [lo, hi] = HELLBORE_BANDS[i];
    if (range >= lo && range <= hi) return i;
  }
  return HELLBORE_BANDS.length - 1;
}

// ── Main export ───────────────────────────────────────────────────────────────

/**
 * Returns per-die damage preview rows for the given weapon at the given range.
 * `adjustedRange` is used for the hit-roll chart in hit-or-miss weapons (photon/disruptor).
 * `range` is used for damage lookup in all weapons.
 * Returns null for weapon types without a static preview (e.g. plasma bolts).
 */
export function getWeaponDamagePreview(
  weaponName:    string,
  armingType:    string | null,
  range:         number,
  adjustedRange: number,
  directFire   = false,
): DmgRow[] | null {
  const n    = weaponName.toLowerCase();
  const mode = armingType ?? 'STANDARD';

  if (n.includes('phaser1'))
    return rollTablePreview(PHASER1_TABLE, range, 75);

  if (n.includes('phaser2'))
    return rollTablePreview(PHASER2_TABLE, range, 50);

  if (n.includes('phaser3') || n.includes('phaserg'))
    return rollTablePreview(PHASER3_TABLE, range, 15);

  if (n.includes('fighterfusion'))
    return rollTablePreview(FIGHTER_FUSION_TABLE, range, 10);

  if (n.includes('fusion')) {
    const table  = mode === 'OVERLOAD' ? FUSION_OVERLOAD_TABLE
                 : mode === 'SPECIAL'  ? FUSION_SUICIDE_TABLE
                 : FUSION_TABLE;
    const maxR   = (mode === 'OVERLOAD' || mode === 'SPECIAL') ? 8 : 24;
    return rollTablePreview(table, range, maxR);
  }

  /**
   * A fighter-mounted photon (the Federation A-10 and A-20). Before the general photon branch,
   * which would otherwise claim it — "fighterphoton" contains "photon" — and would quote a
   * reach of thirty.
   *
   * Same hit charts as a ship's, standard or proximity: only the REACH is cut, to the twelve
   * J1.31 names for a shuttle-mounted photon. Never overloaded — J4.852 says so outright
   * ("overloads are not allowed") — so the mode selects between standard and proximity only.
   */
  if (n.includes('fighterphoton')) {
    const hitChart = mode === 'SPECIAL' ? PHOTON_PROX_HIT_CHART : PHOTON_HIT_CHART;
    const damage   = mode === 'SPECIAL' ? 4 : 8;
    return hitOrMissPreview(hitChart[clampRange(adjustedRange, 12)], damage);
  }

  if (n.includes('photon')) {
    const hitChart = mode === 'OVERLOAD' ? PHOTON_OVLD_HIT_CHART
                   : mode === 'SPECIAL'  ? PHOTON_PROX_HIT_CHART
                   : PHOTON_HIT_CHART;
    const damage   = mode === 'OVERLOAD' ? 16 : mode === 'SPECIAL' ? 4 : 8;
    const maxR     = hitChart.length - 1;
    return hitOrMissPreview(hitChart[clampRange(adjustedRange, maxR)], damage);
  }

  /**
   * A fighter-mounted disruptor (the Kzinti DAS). Before the general disruptor branch, which
   * would otherwise claim it — "fighterdisruptor" contains "disruptor".
   *
   * Same hit and damage charts as a ship's: only the REACH is cut, to the ten every fighter
   * weapon works to. And never overloaded — overload is an energy decision and a fighter has no
   * energy to make it with, so the mode is ignored here rather than offered.
   */
  if (n.includes('fighterdisruptor')) {
    const maxR = 10;
    const r    = clampRange(range, maxR);
    const adjR = clampRange(adjustedRange, maxR);
    return hitOrMissPreview(DISRUPTOR_HIT_CHART[adjR], DISRUPTOR_DMG_CHART[r]);
  }

  if (n.includes('disruptor')) {
    const hitChart = mode === 'OVERLOAD' ? DISRUPTOR_OVLD_HIT_CHART : DISRUPTOR_HIT_CHART;
    const dmgChart = mode === 'OVERLOAD' ? DISRUPTOR_OVLD_DMG_CHART : DISRUPTOR_DMG_CHART;
    const maxR     = hitChart.length - 1;
    const r        = clampRange(range, maxR);
    const adjR     = clampRange(adjustedRange, maxR);
    return hitOrMissPreview(hitChart[adjR], dmgChart[r]);
  }

  if (n.includes('hellbore')) {
    if (mode === 'OVERLOAD') {
      const r   = clampRange(range, 8);
      const dmg = directFire ? HELLBORE_OVLD_DF[r] : HELLBORE_OVLD_ENV[r];
      const b   = hellboreBand(range);
      return [
        { roll: `≤${HELLBORE_HIT_NUMS[b]} (2d6)`, damage: dmg },
        { roll: `>${HELLBORE_HIT_NUMS[b]} (2d6)`, damage: 0  },
      ];
    }
    const b   = hellboreBand(range);
    const dmg = directFire ? HELLBORE_DF_DMG[b] : HELLBORE_ENV_DMG[b];
    return [
      { roll: `≤${HELLBORE_HIT_NUMS[b]} (2d6)`, damage: dmg },
      { roll: `>${HELLBORE_HIT_NUMS[b]} (2d6)`, damage: 0  },
    ];
  }

  return null;
}

// ── The whole table, not one column of it ─────────────────────────────────────

/**
 * One range band: the hexes over which a weapon's numbers do not change.
 *
 * The tables above are stored PER HEX — a phaser-1 is 76 columns, one for each range out to 75 —
 * because that is the shape a shot needs. The SSD prints bands instead, and so does the rulebook,
 * because consecutive hexes mostly share a value: those 76 columns are 11 bands. They are
 * COLLAPSED from the per-hex data rather than typed out again, so the printed table and the
 * firing preview cannot drift apart. That is the whole reason these live beside the tables and
 * not in a component.
 */
export interface RangeBand {
  /** "0", "6-8", "51-75" */
  label: string;
  /** Six entries for a roll table (die 1..6); one for a hit-chart weapon. */
  damage: readonly number[];
  /** Hit-chart weapons only: roll this or less to hit. Null for a roll table. */
  hitOn: number | null;
  /** Hit-chart weapons only: 2d6 rather than 1d6 (the hellbore). */
  twoDice?: boolean;
}

export interface FullTable {
  /** 'roll' = six die faces per band; 'hit' = one to-hit number and one damage per band. */
  kind:  'roll' | 'hit';
  bands: RangeBand[];
  /** Appended to the heading — "overload", "type-S bolt". */
  note?: string;
}

function bandLabel(lo: number, hi: number): string {
  return lo === hi ? String(lo) : lo + '–' + hi;
}

/**
 * Collapse a per-hex series into bands, using `same` to decide when two hexes agree.
 *
 * Trailing dead range is dropped rather than shown: a phaser-1's table runs to 75 because that is
 * its reach, and a final band reading "and 0 beyond" tells a player nothing the maximum range did
 * not already.
 */
function collapse(count: number, same: (a: number, b: number) => boolean,
                  make: (r: number) => Omit<RangeBand, 'label'>,
                  isEmpty: (r: number) => boolean): RangeBand[] {
  let last = count - 1;
  while (last > 0 && isEmpty(last)) last--;

  const bands: RangeBand[] = [];
  let start = 0;
  for (let r = 1; r <= last + 1; r++)
    if (r === last + 1 || !same(start, r)) {
      bands.push({ label: bandLabel(start, r - 1), ...make(start) });
      start = r;
    }
  return bands;
}

function rollTableBands(table: readonly number[][], maxRange: number): RangeBand[] {
  const cols = Math.min(maxRange + 1, table[0].length);
  return collapse(
    cols,
    (a, b) => table.every(row => row[a] === row[b]),
    r => ({ damage: table.map(row => row[r]), hitOn: null }),
    r => table.every(row => row[r] === 0));
}

function hitChartBands(hit: readonly number[], dmg: readonly number[],
                       twoDice = false): RangeBand[] {
  const cols = Math.min(hit.length, dmg.length);
  return collapse(
    cols,
    (a, b) => hit[a] === hit[b] && dmg[a] === dmg[b],
    r => ({ damage: [dmg[r]], hitOn: hit[r], twoDice }),
    r => hit[r] <= 0 || dmg[r] === 0);
}

/**
 * Everything a weapon can do, at every range — what the SSD prints beside the ship, and what this
 * client had only ever shown one column of.
 *
 * Asked for by a player mid-game, 2026-10-09: the fire pad shows what a weapon does to the target
 * you have already picked, and he wanted to know what a weapon was CAPABLE of before picking one.
 * The paper game answers that for free, because the tables are printed on the sheet.
 *
 * The branching mirrors `getWeaponDamagePreview` deliberately, name test for name test and in the
 * same order — `fighterdisruptor` has to be asked before `disruptor` in both. A weapon family
 * added there belongs here too, and a name neither recognises returns null and shows nothing,
 * which is the right answer rather than a wrong table.
 */
export function getWeaponFullTable(
  weaponName:  string,
  armingType:  string | null,
  directFire = false,
  plasmaType: string | null = null,
): FullTable | null {
  const n    = weaponName.toLowerCase();
  const mode = armingType ?? 'STANDARD';
  const ovld = mode === 'OVERLOAD';

  if (n.includes('phaser1')) return { kind: 'roll', bands: rollTableBands(PHASER1_TABLE, 75) };
  if (n.includes('phaser2')) return { kind: 'roll', bands: rollTableBands(PHASER2_TABLE, 50) };
  if (n.includes('phaser3') || n.includes('phaserg'))
    return { kind: 'roll', bands: rollTableBands(PHASER3_TABLE, 15) };
  if (n.includes('fighterfusion'))
    return { kind: 'roll', bands: rollTableBands(FIGHTER_FUSION_TABLE, 10) };

  if (n.includes('fusion')) {
    const table = mode === 'SUICIDE' ? FUSION_SUICIDE_TABLE
                : ovld               ? FUSION_OVERLOAD_TABLE
                :                      FUSION_TABLE;
    const maxR  = mode === 'STANDARD' ? 24 : 12;
    return { kind: 'roll', bands: rollTableBands(table, maxR),
             note: mode === 'STANDARD' ? undefined : mode.toLowerCase() };
  }

  if (n.includes('fighterphoton'))
    return { kind: 'hit',
             bands: hitChartBands(PHOTON_HIT_CHART, PHOTON_HIT_CHART.map(() => 8)) };

  if (n.includes('photon')) {
    const hit    = mode === 'OVERLOAD'  ? PHOTON_OVLD_HIT_CHART
                 : mode === 'PROXIMITY' ? PHOTON_PROX_HIT_CHART
                 :                        PHOTON_HIT_CHART;
    const damage = mode === 'OVERLOAD' ? 16 : mode === 'PROXIMITY' ? 4 : 8;
    return { kind: 'hit', bands: hitChartBands(hit, hit.map(() => damage)),
             note: mode === 'STANDARD' ? undefined : mode.toLowerCase() };
  }

  if (n.includes('fighterdisruptor'))
    return { kind: 'hit', bands: hitChartBands(DISRUPTOR_HIT_CHART.slice(0, 11),
                                               DISRUPTOR_DMG_CHART.slice(0, 11)) };

  if (n.includes('disruptor'))
    return { kind: 'hit',
             bands: hitChartBands(ovld ? DISRUPTOR_OVLD_HIT_CHART : DISRUPTOR_HIT_CHART,
                                  ovld ? DISRUPTOR_OVLD_DMG_CHART : DISRUPTOR_DMG_CHART),
             note: ovld ? 'overload' : undefined };

  if (n.includes('hellbore')) {
    // Already banded in the data, so these are read across rather than collapsed. Overload is
    // indexed by hex instead, which is why it takes the other path.
    if (ovld)
      return { kind: 'hit',
               bands: hitChartBands(
                   HELLBORE_OVLD_ENV.map((_, i) => HELLBORE_HIT_NUMS[hellboreBand(i)]),
                   directFire ? HELLBORE_OVLD_DF : HELLBORE_OVLD_ENV, true),
               note: directFire ? 'overload, direct fire' : 'overload' };
    return {
      kind: 'hit',
      note: directFire ? 'direct fire' : undefined,
      bands: HELLBORE_BANDS.map(([lo, hi], i) => ({
        label:   bandLabel(lo, hi),
        damage:  [directFire ? HELLBORE_DF_DMG[i] : HELLBORE_ENV_DMG[i]],
        hitOn:   HELLBORE_HIT_NUMS[i],
        twoDice: true,
      })),
    };
  }

  if (plasmaType) {
    // The BOLT, which is what a launcher can be asked for at any range (FP8.4) and the only part
    // of a plasma weapon that answers to a damage table at all. The torpedo's strength by range
    // belongs to the seeker in flight, not to the launcher sitting on the ship.
    const strength = plasmaType === 'R' ? PLASMA_R_BY_RANGE
                   : plasmaType === 'S' ? PLASMA_S_BY_RANGE
                   : plasmaType === 'G' ? PLASMA_G_BY_RANGE
                   : plasmaType === 'D' ? PLASMA_D_BY_RANGE
                   :                      PLASMA_F_BY_RANGE;
    const cols = Math.min(strength.length, PLASMA_BOLT_HIT_CHART.length);
    return {
      kind: 'hit',
      note: 'type-' + plasmaType + ' bolt',
      bands: hitChartBands(PLASMA_BOLT_HIT_CHART.slice(0, cols),
                           strength.slice(0, cols).map(s => Math.floor(s / 2))),
    };
  }

  return null;
}
