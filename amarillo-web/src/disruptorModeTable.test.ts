import { describe, expect, it } from 'vitest';
import { getWeaponFullTable } from './weaponDamageTables';

/**
 * The DISRUPTOR TABLE as the paper SSD prints it, transcribed.
 *
 * This is the strongest assertion available for this data: the printed sheet is the authority,
 * and every row below was read off it rather than derived from our own charts. If the mirror in
 * weaponDamageTables.ts ever drifts from Disruptor.java, or from the rulebook, these fail.
 *
 * <pre>
 * RANGE           0     1     2    3-4   5-8  9-15  16-22 23-30 31-40
 * HIT (STD)       NA   1-5   1-5   1-4   1-4   1-4   1-3   1-2   1-2
 * HIT (UIM)       NA   1-5   1-5   1-4   1-4   1-4   1-4   1-2   1-2
 * HIT (DERFACS)   NA   1-5   1-5   1-4   1-4   1-4   1-3   1-3   1-2
 * HIT (OVERLOAD) 1-6   1-5   1-5   1-4   1-4    NA    NA    NA    NA
 * HIT (OL/UIM)   1-6   1-5   1-5   1-5   1-5    NA    NA    NA    NA
 * DAMAGE, STD     0     5     4     4     3     3     2     2     1
 * DAMAGE, OVLD   10    10     8     8     6     0     0     0     0
 * </pre>
 */

/** The bands the sheet prints. En dashes, because that is what bandLabel emits. */
const PAPER_BANDS = ['0', '1', '2', '3–4', '5–8', '9–15',
                     '16–22', '23–30', '31–40'];

const NA = null;

const PAPER_ROWS: Record<string, (number | null)[]> = {
  'Hit (std)':       [NA, 5, 5, 4, 4, 4, 3, 2, 2],
  'Hit (UIM)':       [NA, 5, 5, 4, 4, 4, 4, 2, 2],
  'Hit (DERFACS)':   [NA, 5, 5, 4, 4, 4, 3, 3, 2],
  'Hit (ovld)':      [6, 5, 5, 4, 4, NA, NA, NA, NA],
  'Hit (ovld/UIM)':  [6, 5, 5, 5, 5, NA, NA, NA, NA],
  'Damage, std':     [0, 5, 4, 4, 3, 3, 2, 2, 1],
  'Damage, ovld':    [10, 10, 8, 8, 6, 0, 0, 0, 0],
};

function table(fit: { uim?: boolean; derfacs?: boolean }) {
  const t = getWeaponFullTable('Disruptor-A', null, false, null, fit);
  if (!t?.modes) throw new Error('a disruptor must produce a multi-mode table');
  return t.modes;
}

describe('the printed disruptor table', () => {
  it('bands exactly as the sheet does, for a ship with both systems', () => {
    expect(table({ uim: true, derfacs: true }).labels).toEqual(PAPER_BANDS);
  });

  it('bands the same way for a ship with NEITHER, which is the self-computing claim', () => {
    // The edges are the union of every line's change points, so dropping the DERFACS line
    // could in principle merge 23-30 into 31-40. It does not, because the standard DAMAGE
    // line changes at 31 too. Computing the bands rather than hard-coding them is only safe
    // if that holds, so it is asserted rather than assumed.
    expect(table({}).labels).toEqual(PAPER_BANDS);
  });

  it.each(Object.keys(PAPER_ROWS))('matches the sheet, row by row: %s', label => {
    const line = table({ uim: true, derfacs: true }).lines.find(l => l.label === label);
    expect(line, `${label} is missing from the table`).toBeDefined();
    expect(line!.cells).toEqual(PAPER_ROWS[label]);
  });

  it('prints the UIM lines only for a ship that has one', () => {
    const without = table({ derfacs: true }).lines.map(l => l.label);
    expect(without).not.toContain('Hit (UIM)');
    expect(without).not.toContain('Hit (ovld/UIM)');
    expect(without).toContain('Hit (DERFACS)');
  });

  it('prints the DERFACS line only for a ship that has one', () => {
    const without = table({ uim: true }).lines.map(l => l.label);
    expect(without).not.toContain('Hit (DERFACS)');
    expect(without).toContain('Hit (UIM)');
  });

  it('gives a bare ship the standard and overload lines and nothing else', () => {
    expect(table({}).lines.map(l => l.label))
      .toEqual(['Hit (std)', 'Hit (ovld)', 'Damage, std', 'Damage, ovld']);
  });

  it('has no DERFACS overload line, because DERFACS does not improve overloads', () => {
    // fireDerfacs falls through to the STANDARD overload chart (the Java says so in as many
    // words), and the sheet prints no HIT (OL/DERFACS) row either. A line here would quietly
    // promise a gain that the dice will not pay.
    const labels = table({ uim: true, derfacs: true }).lines.map(l => l.label);
    expect(labels.filter(l => l.includes('DERFACS'))).toEqual(['Hit (DERFACS)']);
  });

  it('shows BOTH arming modes whatever the weapon is currently armed as', () => {
    // The single-mode table switched on armingType, so a standard-loaded disruptor never
    // showed what overloading would buy -- which is the decision being made, at allocation,
    // before the weapon is armed at all.
    for (const arming of [null, 'STANDARD', 'OVERLOAD']) {
      const t = getWeaponFullTable('Disruptor-A', arming, false, null, { uim: true });
      const labels = t!.modes!.lines.map(l => l.label);
      expect(labels, `arming=${arming}`).toContain('Hit (std)');
      expect(labels, `arming=${arming}`).toContain('Hit (ovld)');
    }
  });
});
