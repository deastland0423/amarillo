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
// Special fire modes, mirroring the four charts in Disruptor.java. Verified cell by cell against
// the DISRUPTOR TABLE printed on the paper SSD, which prints all five HIT lines on every sheet.
// UIM (D6.5) buys the 16-22 band on standard fire, and ranges 3-8 on overload -- where it halves
// the miss rate on the heaviest shots a disruptor fires. DERFACS buys 23-30, and explicitly does
// NOT improve overload: fireDerfacs falls back to the standard overload chart.
export const DISRUPTOR_UIM_HIT_CHART:      readonly number[] = [0,5,5,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2];
export const DISRUPTOR_UIM_OVLD_HIT_CHART: readonly number[] = [6,5,5,5,5,5,5,5,5];
export const DISRUPTOR_DERFACS_HIT_CHART:  readonly number[] = [0,5,5,4,4,4,4,4,4,4,4,4,4,4,4,4,3,3,3,3,3,3,3,3,3,3,3,3,3,3,3,2,2,2,2,2,2,2,2,2,2];

// ── Hellbore (2d6 roll; range-band lookup) ────────────────────────────────────
// Band edges: [0]=range 0–1, [1]=2, [2]=3–4, [3]=5–8, [4]=9–15, [5]=16–22, [6]=23–40
export const HELLBORE_BANDS     = [[0,1],[2,2],[3,4],[5,8],[9,15],[16,22],[23,40]] as const;
export const HELLBORE_HIT_NUMS  = [11,10,9,8,7,6,5]        as const; // ≤ this on 2d6 = hit
export const HELLBORE_ENV_DMG   = [20,17,15,13,10,8,4]     as const;
export const HELLBORE_DF_DMG    = [10, 8, 7, 6, 5, 4, 2]   as const;
export const HELLBORE_OVLD_ENV  = [30,30,25,22,22,19,19,19,19] as const; // indexed by range 0–8
export const HELLBORE_OVLD_DF   = [15,15,12,11,11, 9, 9, 9, 9] as const;

// ── PPD (E11.32; 2d6 roll for wave-lock, range-band lookup) ──────────────────
// Mirrors PpdSplash.java. NOTE THE DIRECTION: unlike every other chart here, the PPD does
// NOTHING inside range 4, peaks at 4-10 and still reaches 40. A preview that assumed "closer
// is better" would read this exactly backwards.
// Band edges: [0]=0-3 (dead), [1]=4-10, [2]=11-15, [3]=16-20, [4]=21-25, [5]=26-30, [6]=31-40
export const PPD_BANDS      = [[0,3],[4,10],[11,15],[16,20],[21,25],[26,30],[31,40]] as const;
export const PPD_HIT_NUMS   = [0, 9, 8, 7, 6, 5, 4] as const; // 0 = cannot score at that range
export const PPD_TOTAL_DMG  = [0, 6, 5, 4, 3, 2, 1] as const; // statistical total, never one hit
// Splash as [centre, stronger adjacent, weaker adjacent] — E11.352. The trailing zero follows
// whichever neighbour is WEAKER at the time, so it is not a fixed side.
export const PPD_SPLASH     = [[0,0,0],[4,1,1],[3,1,1],[2,1,1],[1,1,1],[1,1,0],[1,0,0]] as const;
// Alternate row, used when the line of fire runs along a hex side (E11.351): two shields, the
// odd point to the stronger. Same totals as above.
export const PPD_ALT        = [[0,0],[3,3],[3,2],[2,2],[2,1],[1,1],[1,0]] as const;

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

function ppdBand(range: number): number {
  for (let i = 0; i < PPD_BANDS.length; i++) {
    const [lo, hi] = PPD_BANDS[i];
    if (range >= lo && range <= hi) return i;
  }
  return PPD_BANDS.length - 1;
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
  /**
   * The printed multi-mode layout: one labelled line per mode of fire, as the paper SSD prints
   * a disruptor. Present only for weapons that have more than one mode worth comparing; when it
   * is set the renderer draws these lines instead of the single hit/damage pair above.
   */
  modes?: ModeTable;
}

/** One labelled line of a printed table: "HIT (UIM)", "DAMAGE, OVLD". A null cell prints NA. */
export interface ModeLine {
  label: string;
  cells: readonly (number | null)[];
  /** Hit lines print "1-4"; damage lines print the number alone. */
  kind: 'hit' | 'damage';
  /** Hit lines only: rolled on 2d6, so the cell reads "2-8" and earns the footnote. */
  twoDice?: boolean;
}

export interface ModeTable {
  labels: string[];        // band headings, left to right
  lines:  ModeLine[];
}

/**
 * Collapse several per-hex series into shared bands.
 *
 * The band edges are the union of every line's change points, which is exactly why the paper
 * SSD's disruptor table bands at 0 | 1 | 2 | 3-4 | 5-8 | 9-15 | 16-22 | 23-30 | 31-40: 3-4 ends
 * at 4 because overload DAMAGE drops there, 5-8 ends because overload stops reaching, 23-30 ends
 * because DERFACS falls off and standard damage does too. Computing it beats hard-coding it --
 * drop the DERFACS line for a ship without one and the bands still come out right, because the
 * damage line changes in the same place.
 */
function bandSeries(
  series: readonly (readonly (number | null)[])[],
  cols: number,
): { labels: string[]; bands: number[][] } {
  const labels: string[] = [];
  const bands: number[][] = [];
  let start = 0;
  for (let i = 1; i <= cols; i++) {
    const changed = i === cols || series.some(s => s[i] !== s[i - 1]);
    if (!changed) continue;
    labels.push(bandLabel(start, i - 1));
    bands.push(series.map(s => s[start] as number));
    start = i;
  }
  return { labels, bands };
}

/**
 * A hit line, padded to the full range, NA wherever the mode cannot fire.
 *
 * A zero in a hit chart IS "cannot fire": it is how every chart in this file already encodes a
 * minimum range -- a standard photon is 0 at ranges 0-1 because E4 gives it a minimum of 2, and
 * a proximity photon is 0 out to 8 for the same reason. Past the end of the chart the mode has
 * run out of reach. Both print NA, which is what the sheet prints.
 */
function hitCells(chart: readonly number[], cols: number): (number | null)[] {
  const out: (number | null)[] = [];
  for (let r = 0; r < cols; r++)
    out.push(r >= chart.length || chart[r] <= 0 ? null : chart[r]);
  return out;
}

/** A damage line. Zero is a REAL value here -- the sheet prints 0, not NA -- so it is kept. */
function damageCells(chart: readonly number[], cols: number): (number | null)[] {
  const out: (number | null)[] = [];
  for (let r = 0; r < cols; r++)
    out.push(r >= chart.length ? 0 : chart[r]);
  return out;
}

/**
 * A damage line for a weapon whose damage does not vary with range, only with mode.
 *
 * A photon does 8, or 16 overloaded, or 4 on proximity, at every range it can reach at all --
 * so the line is the flat figure wherever the mode can fire and 0 where it cannot. Which makes
 * the row worth printing despite being constant: 16 sitting directly above 8 is the comparison
 * the player came for.
 */
function flatDamageCells(hit: readonly number[], damage: number,
                         cols: number): (number | null)[] {
  const out: (number | null)[] = [];
  for (let r = 0; r < cols; r++)
    out.push(r >= hit.length || hit[r] <= 0 ? 0 : damage);
  return out;
}

/**
 * The hellbore's table: one to-hit line, four damage lines.
 *
 * Two independent choices cross here, which is why it needs four damage rows rather than two.
 * ENVELOPING vs DIRECT is how the shot is aimed, and STANDARD vs OVERLOAD is what was paid for
 * it, and the old table showed exactly one of the four -- the overload rows only if the weapon
 * was already armed that way, and the direct-fire rows only if the caller passed a flag the SSD
 * panel never passes. So the panel could only ever show enveloping standard.
 *
 * Accuracy is NOT one of the choices: overloading a hellbore buys damage and nothing else, so a
 * single hit line serves every row. It is stated here because a reader who knows the disruptor
 * table would reasonably expect a HIT (OVLD) line and should know its absence is deliberate.
 */
function hellboreModeTable(): ModeTable {
  const COLS = 41;                                   // ranges 0-40
  const byBand = (values: readonly number[]) =>
    Array.from({ length: COLS }, (_, r) => values[hellboreBand(r)]);
  const lines: ModeLine[] = [
    { label: 'Hit (2d6)', kind: 'hit', twoDice: true,
      cells: hitCells(byBand(HELLBORE_HIT_NUMS), COLS) },
    { label: 'Dmg, env',       kind: 'damage', cells: damageCells(byBand(HELLBORE_ENV_DMG), COLS) },
    { label: 'Dmg, direct',    kind: 'damage', cells: damageCells(byBand(HELLBORE_DF_DMG), COLS) },
    // Overload stops at range 8 (setOverload caps it), so these fall to 0 beyond that.
    { label: 'Dmg, env ovld',  kind: 'damage', cells: damageCells(HELLBORE_OVLD_ENV, COLS) },
    { label: 'Dmg, dir ovld',  kind: 'damage', cells: damageCells(HELLBORE_OVLD_DF, COLS) },
  ];
  const { labels, bands } = bandSeries(lines.map(l => l.cells), COLS);
  return { labels, lines: lines.map((l, i) => ({ ...l, cells: bands.map(b => b[i]) })) };
}

/**
 * The PPD's printed table (E11.3), mirroring PpdSplash.java.
 *
 * <b>It runs BACKWARDS compared with every other chart in this file.</b> The PPD does nothing
 * at all inside range 4, peaks at 4-10, and still reaches 40 — the "echelon tactics" E11.0
 * opens with. A player who reads it with a disruptor's assumptions has it exactly inverted,
 * which is the strongest argument for printing it rather than leaving the weapon blank: it was
 * the only heavy weapon in the game whose SSD entry showed no table whatsoever.
 *
 * <h2>Why the splash rows are not optional detail</h2>
 * E11.35: "This is not an optional use of the weapon. It is the way that the weapon always
 * operates." The Damage row is a statistical TOTAL and is never what lands on one shield, so a
 * table showing only that row would mislead on every shot. The three elements below it are
 * what actually arrives: the centre on the facing shield, the rest on its neighbours (E11.352).
 *
 * The asymmetric rows key on which NEIGHBOUR is stronger, not on a fixed side — at two points
 * "ignore the weaker adjacent shield", at one ignore both. So the trailing zero in 1+1+0 moves
 * with the target's damage and is not a position on the diagram.
 *
 * The last two lines are E11.351: a line of fire along a hex side hits no single shield, and
 * splits between the two with the odd point to the stronger.
 *
 * <h2>One hit line, deliberately</h2>
 * Overloading a PPD buys PULSES, not accuracy or damage per pulse — six over six impulses
 * instead of four over four (E11.61). So there is nothing for a second hit row to say, and the
 * pulse count goes in the note where it cannot be mistaken for a per-shot figure.
 */
function ppdModeTable(): ModeTable {
  const COLS = 41;                                   // ranges 0-40; it does not reach further
  const byBand = (pick: (b: number) => number) =>
    Array.from({ length: COLS }, (_, r) => pick(ppdBand(r)));

  const lines: ModeLine[] = [
    { label: 'Hit (2d6)', kind: 'hit', twoDice: true,
      cells: hitCells(byBand(b => PPD_HIT_NUMS[b]), COLS) },
    { label: 'Damage',        kind: 'damage', cells: damageCells(byBand(b => PPD_TOTAL_DMG[b]), COLS) },
    { label: '· facing',     kind: 'damage', cells: damageCells(byBand(b => PPD_SPLASH[b][0]), COLS) },
    { label: '· adj (strong)', kind: 'damage', cells: damageCells(byBand(b => PPD_SPLASH[b][1]), COLS) },
    { label: '· adj (weak)', kind: 'damage', cells: damageCells(byBand(b => PPD_SPLASH[b][2]), COLS) },
    { label: 'Side-on, strong', kind: 'damage', cells: damageCells(byBand(b => PPD_ALT[b][0]), COLS) },
    { label: 'Side-on, weak', kind: 'damage', cells: damageCells(byBand(b => PPD_ALT[b][1]), COLS) },
  ];

  const { labels, bands } = bandSeries(lines.map(l => l.cells), COLS);
  return { labels, lines: lines.map((l, i) => ({ ...l, cells: bands.map(b => b[i]) })) };
}

/**
 * The photon's printed table: standard, overloaded and proximity, side by side.
 *
 * This replaces a branch on how the weapon happened to be armed, and that branch was WRONG --
 * it tested {@code mode === 'PROXIMITY'} while {@code WeaponArmingType} has no such value, only
 * SPECIAL, so a proximity photon silently showed the standard chart and 8 damage instead of 4.
 * Showing every mode at once does not fix that bug so much as delete the shape of it: there is
 * no mode string left to get wrong.
 *
 * It is also the table where overload matters most. A standard photon CANNOT FIRE inside range
 * 2 (E4 gives it a minimum), while an overloaded one hits automatically there for 16 -- double
 * damage at point blank, from a weapon that otherwise has nothing to say. Reserve warp and
 * batteries make that a live choice right up to the moment of firing, and the old table never
 * showed it.
 */
function photonModeTable(): ModeTable {
  const COLS = PHOTON_HIT_CHART.length;        // 31: ranges 0-30
  const lines: ModeLine[] = [
    { label: 'Hit (std)',  kind: 'hit', cells: hitCells(PHOTON_HIT_CHART, COLS) },
    { label: 'Hit (ovld)', kind: 'hit', cells: hitCells(PHOTON_OVLD_HIT_CHART, COLS) },
    { label: 'Hit (prox)', kind: 'hit', cells: hitCells(PHOTON_PROX_HIT_CHART, COLS) },
    // Flat per mode, so these rows repeat one number -- which is the point. Seeing 16 above 8
    // above 4 is the whole comparison, and it costs a line each.
    { label: 'Damage, std',  kind: 'damage',
      cells: flatDamageCells(PHOTON_HIT_CHART, 8, COLS) },
    { label: 'Damage, ovld', kind: 'damage',
      cells: flatDamageCells(PHOTON_OVLD_HIT_CHART, 16, COLS) },
    { label: 'Damage, prox', kind: 'damage',
      cells: flatDamageCells(PHOTON_PROX_HIT_CHART, 4, COLS) },
  ];
  const { labels, bands } = bandSeries(lines.map(l => l.cells), COLS);
  return { labels, lines: lines.map((l, i) => ({ ...l, cells: bands.map(b => b[i]) })) };
}

/**
 * The disruptor's table exactly as the SSD prints it, with a HIT line per mode this ship can
 * actually use.
 *
 * Both arming modes appear at once, which the single-mode table could not do: it switched on how
 * the weapon happened to be armed, so a standard-loaded disruptor never showed what overloading
 * would buy -- and that is the decision being made, at allocation, before the weapon is armed.
 */
function disruptorModeTable(uim: boolean, derfacs: boolean): ModeTable {
  const COLS = DISRUPTOR_HIT_CHART.length;      // 41: ranges 0-40
  const lines: ModeLine[] = [
    { label: 'Hit (std)', kind: 'hit', cells: hitCells(DISRUPTOR_HIT_CHART, COLS) },
  ];
  if (uim)
    lines.push({ label: 'Hit (UIM)', kind: 'hit',
                 cells: hitCells(DISRUPTOR_UIM_HIT_CHART, COLS) });
  if (derfacs)
    lines.push({ label: 'Hit (DERFACS)', kind: 'hit',
                 cells: hitCells(DISRUPTOR_DERFACS_HIT_CHART, COLS) });
  // Overload reaches range 0 (setOverload drops the minimum) and stops at 8.
  lines.push({ label: 'Hit (ovld)', kind: 'hit',
               cells: hitCells(DISRUPTOR_OVLD_HIT_CHART, COLS) });
  if (uim)
    lines.push({ label: 'Hit (ovld/UIM)', kind: 'hit',
                 cells: hitCells(DISRUPTOR_UIM_OVLD_HIT_CHART, COLS) });
  // DERFACS is deliberately absent from the overload lines: fireDerfacs uses the STANDARD
  // overload chart, so a DERFACS line there would duplicate the one above it. The paper sheet
  // prints it the same way, with no HIT (OL/DERFACS) row.
  lines.push({ label: 'Damage, std', kind: 'damage',
               cells: damageCells(DISRUPTOR_DMG_CHART, COLS) });
  lines.push({ label: 'Damage, ovld', kind: 'damage',
               cells: damageCells(DISRUPTOR_OVLD_DMG_CHART, COLS) });

  const { labels, bands } = bandSeries(lines.map(l => l.cells), COLS);
  return {
    labels,
    lines: lines.map((l, i) => ({ ...l, cells: bands.map(b => b[i]) })),
  };
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
  /** The firing SHIP's targeting kit, which adds printed lines to a disruptor's table. */
  fit: { uim?: boolean; derfacs?: boolean } = {},
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

  if (n.includes('photon'))
    // No mode branch: every mode is on the table. The `bands` below stay as a fallback for a
    // caller that has not been taught the multi-mode shape, and use the STANDARD chart, which
    // is the one the old branch reached for anything it did not recognise anyway.
    return { kind: 'hit',
             bands: hitChartBands(PHOTON_HIT_CHART, PHOTON_HIT_CHART.map(() => 8)),
             modes: photonModeTable() };

  if (n.includes('fighterdisruptor'))
    return { kind: 'hit', bands: hitChartBands(DISRUPTOR_HIT_CHART.slice(0, 11),
                                               DISRUPTOR_DMG_CHART.slice(0, 11)) };

  if (n.includes('disruptor'))
    // Every mode on one table, the way the sheet prints it, so the single-mode `bands` are only
    // a fallback for a caller that has not been taught the new shape.
    return { kind: 'hit',
             bands: hitChartBands(ovld ? DISRUPTOR_OVLD_HIT_CHART : DISRUPTOR_HIT_CHART,
                                  ovld ? DISRUPTOR_OVLD_DMG_CHART : DISRUPTOR_DMG_CHART),
             modes: disruptorModeTable(fit.uim === true, fit.derfacs === true) };

  if (n.includes('hellbore'))
    // Every mode at once, so neither the arming type nor the directFire flag selects any more.
    // The single-mode `bands` stay as a fallback for a caller that has not been taught the
    // multi-mode shape, and still honour directFire for it.
    return {
      kind: 'hit',
      modes: hellboreModeTable(),
      bands: HELLBORE_BANDS.map(([lo, hi], i) => ({
        label:   bandLabel(lo, hi),
        damage:  [directFire ? HELLBORE_DF_DMG[i] : HELLBORE_ENV_DMG[i]],
        hitOn:   HELLBORE_HIT_NUMS[i],
        twoDice: true,
      })),
    };

  if (n.includes('ppd'))
    // Was the only heavy weapon that produced NO table at all: there was never a branch here,
    // although the chart data has sat in this file, commented and correct, the whole time.
    return {
      kind: 'hit',
      note: 'per pulse · 4 pulses, 6 overloaded (E11.61)',
      modes: ppdModeTable(),
      bands: PPD_BANDS.map(([lo, hi], i) => ({
        label:   bandLabel(lo, hi),
        damage:  [PPD_TOTAL_DMG[i]],
        hitOn:   PPD_HIT_NUMS[i],
        twoDice: true,
      })),
    };

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
