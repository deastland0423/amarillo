import { describe, expect, it } from 'vitest';
import { getWeaponFullTable } from './weaponDamageTables';

/**
 * The fusion beam's tables, transcribed from the Captain's Master Rulebook (E7.31, E7.41,
 * E7.42) and matching the paper SSD's layout, which prints them as separate boxes.
 *
 * <pre>
 * FUSION BEAM TABLE                      FUSION OVERLOAD
 * DIE   RANGE                            DIE   RANGE
 * ROLL  0   1   2  3-10 11-15 16-24      ROLL  0   1   2  3-8
 *  1   13   8   6   4    3     2          1   19  12   9   6
 *  2   11   8   5   3    2     1          2   16  12   7   4
 *  3   10   7   4   2    1     0          3   15  10   6   3
 *  4    9   6   3   1    1     0          4   13   9   4   1
 *  5    8   5   3   1    0     0          5   12   7   4   1
 *  6    8   4   2   0    0     0          6   12   6   3   0
 * </pre>
 *
 * Fusion is the one weapon whose modes cannot share a table. Every mode is a full six-die-face
 * grid, and the bands do not line up: standard runs to 24, an overload stops at 8 (E7.411) and
 * bands 3-8 where standard bands 3-10. There is no set of columns that fits both — which is
 * why the sheet prints two boxes and why this is a stacked shape rather than more rows.
 *
 * Before this, the branch picked ONE table by how the weapon happened to be armed, so a player
 * could never see what overloading would buy him without first paying for it. Same fault the
 * photon's proximity chart had.
 */

const BASIC: Record<number, number[]> = {
  1: [13, 8, 6, 4, 3, 2],
  2: [11, 8, 5, 3, 2, 1],
  3: [10, 7, 4, 2, 1, 0],
  4: [9, 6, 3, 1, 1, 0],
  5: [8, 5, 3, 1, 0, 0],
  6: [8, 4, 2, 0, 0, 0],
};
const BASIC_BANDS = ['0', '1', '2', '3–10', '11–15', '16–24'];

const OVLD: Record<number, number[]> = {
  1: [19, 12, 9, 6],
  2: [16, 12, 7, 4],
  3: [15, 10, 6, 3],
  4: [13, 9, 4, 1],
  5: [12, 7, 4, 1],
  6: [12, 6, 3, 0],
};
const OVLD_BANDS = ['0', '1', '2', '3–8'];

const SUICIDE: Record<number, number[]> = {
  1: [26, 16, 12, 8],
  2: [22, 16, 10, 6],
  3: [20, 14, 8, 4],
  4: [18, 12, 6, 2],
  5: [16, 10, 6, 2],
  6: [16, 8, 4, 0],
};

function tables(arming: string | null = null) {
  const t = getWeaponFullTable('Fusion-A', arming);
  if (!t?.rollTables) throw new Error('a fusion beam must produce stacked tables');
  return t.rollTables;
}

const named = (title: string, arming: string | null = null) => {
  const sub = tables(arming).find(x => x.title === title);
  if (!sub) throw new Error(`no table titled ${title}`);
  return sub;
};

/** The band's column of six die-face results, read back as a row per die roll. */
const rowsOf = (title: string) => {
  const sub = named(title);
  const out: Record<number, number[]> = {};
  for (let face = 1; face <= 6; face++)
    out[face] = sub.bands.map(b => b.damage[face - 1]);
  return out;
};

describe('the three tables, printed separately as the sheet prints them', () => {
  it('shows all three at once, whatever the weapon is armed with', () => {
    expect(tables().map(t => t.title))
      .toEqual(['Fusion beam', 'Overload', 'Suicide overload']);
  });

  it('shows the same three however it is armed', () => {
    // The fault this fixes: a player could not compare what overloading buys without first
    // paying for it, because the branch selected a table by the arming type.
    const base = JSON.stringify(tables());
    for (const arming of ['STANDARD', 'OVERLOAD', 'SUICIDE', null])
      expect(JSON.stringify(tables(arming)), `${arming}`).toBe(base);
  });

  it('gives each table its OWN bands, which is why they cannot be merged', () => {
    expect(named('Fusion beam').bands.map(b => b.label)).toEqual(BASIC_BANDS);
    expect(named('Overload').bands.map(b => b.label)).toEqual(OVLD_BANDS);
    expect(named('Suicide overload').bands.map(b => b.label)).toEqual(OVLD_BANDS);
  });
});

describe('the printed numbers', () => {
  it.each([1, 2, 3, 4, 5, 6])('fusion beam table, die roll %i', face => {
    expect(rowsOf('Fusion beam')[face]).toEqual(BASIC[face]);
  });

  it.each([1, 2, 3, 4, 5, 6])('overload table, die roll %i', face => {
    expect(rowsOf('Overload')[face]).toEqual(OVLD[face]);
  });

  it.each([1, 2, 3, 4, 5, 6])('suicide overload table, die roll %i', face => {
    expect(rowsOf('Suicide overload')[face]).toEqual(SUICIDE[face]);
  });
});

describe('the ranges, which are rules and not presentation', () => {
  it('reaches 24 standard and stops at 8 overloaded (E7.411, E7.422)', () => {
    const last = (title: string) => named(title).bands[named(title).bands.length - 1].label;
    expect(last('Fusion beam')).toBe('16–24');
    expect(last('Overload')).toBe('3–8');
    expect(last('Suicide overload')).toBe('3–8');
  });

  it('keeps a die roll of 6 scoring at point blank in every mode', () => {
    // The weapon's character: even the worst roll hurts at range 0, which is the whole of the
    // Hydran closing tactic. It is the far bands where a 6 scores nothing.
    for (const title of ['Fusion beam', 'Overload', 'Suicide overload'])
      expect(rowsOf(title)[6][0], title).toBeGreaterThan(0);
  });
});

describe('the relationships, which are real but not a reason to drop a table', () => {
  it('overload is floor(basic × 1.5) and suicide is basic × 2, band for band', () => {
    // E7.41 "increased by 50% (round fractions down)" and E7.42 "double the damage points
    // shown on the basic chart". Both hold exactly — and the sheet STILL prints them, because
    // the bands differ and the arithmetic is error-prone mid-game. The hellbore's direct-fire
    // row was dropped for being derivable; this is the case where that reasoning does not
    // apply, and the difference is the band structure.
    for (let face = 1; face <= 6; face++)
      for (let col = 0; col < 4; col++) {
        const basic = BASIC[face][col];
        expect(OVLD[face][col], `ovld die ${face} col ${col}`).toBe(Math.floor(basic * 1.5));
        expect(SUICIDE[face][col], `suicide die ${face} col ${col}`).toBe(basic * 2);
      }
  });
});

describe('the fallback the hover readout still reads', () => {
  it('answers for the mode in hand, since GameBoard has not been taught the stacked shape', () => {
    const std  = getWeaponFullTable('Fusion-A', 'STANDARD')!;
    const ovld = getWeaponFullTable('Fusion-A', 'OVERLOAD')!;
    const sui  = getWeaponFullTable('Fusion-A', 'SUICIDE')!;

    expect(std.bands[0].damage[0]).toBe(13);
    expect(ovld.bands[0].damage[0]).toBe(19);
    expect(sui.bands[0].damage[0]).toBe(26);
    expect(ovld.note).toBe('overload');
  });

  it('caps the overloaded fallback at range 8, not at 12', () => {
    // The old code passed 12 here, which is neither range. It was never WRONG — the overload
    // array is only nine long and rollTableBands clamps to it — so this pins the behaviour
    // the array was silently providing, now that the number says 8 and means it.
    const ovld = getWeaponFullTable('Fusion-A', 'OVERLOAD')!;
    expect(ovld.bands[ovld.bands.length - 1].label).toBe('3–8');
  });
});

describe('the fighter fusion, which is a different weapon and must not be caught', () => {
  it('keeps its own single table at range 10', () => {
    const f = getWeaponFullTable('FighterFusion-A', null)!;
    expect(f.rollTables).toBeUndefined();
    expect(f.bands[f.bands.length - 1].label).toBe('3–10');
  });
});
