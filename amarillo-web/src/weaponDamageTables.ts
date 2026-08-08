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

/** Preview for a plasma bolt at a given range. plasmaType = "F" | "G" | "S" | "R" | "D". */
export function getPlasmaBoltPreview(plasmaType: string | null, range: number): DmgRow[] {
  if (range >= PLASMA_BOLT_HIT_CHART.length)
    return [{ roll: '1–6', damage: 0 }];
  const strength  = plasmaStrengthAtRange(plasmaType, range);
  const boltDmg   = Math.floor(strength / 2);
  const hitOn     = PLASMA_BOLT_HIT_CHART[range];
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

  if (n.includes('photon')) {
    const hitChart = mode === 'OVERLOAD' ? PHOTON_OVLD_HIT_CHART
                   : mode === 'SPECIAL'  ? PHOTON_PROX_HIT_CHART
                   : PHOTON_HIT_CHART;
    const damage   = mode === 'OVERLOAD' ? 16 : mode === 'SPECIAL' ? 4 : 8;
    const maxR     = hitChart.length - 1;
    return hitOrMissPreview(hitChart[clampRange(adjustedRange, maxR)], damage);
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
