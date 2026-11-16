import { describe, expect, it } from 'vitest';
import { getWeaponFullTable } from './weaponDamageTables';

/**
 * The PLASMA TORPEDO WARHEAD STRENGTH TABLE, transcribed from three paper SSDs (owner's
 * captures): a War Eagle with a plasma-R, a Romulan KR with a plasma-S, and a hull carrying a
 * plasma rack.
 *
 * <pre>
 * RANGE  0-5 6-10 11-12 13-14 15 16-18 19 20 21-23 24 25 26-28 29 30
 * TYPE R  50   50    35    35 35    25 25 25    20 20 20    10  5  1
 * TYPE S  30   30    22    22 22    15 15 15    10  5  1
 * TYPE G  20   20    15    15 15    10  5  1     0  0  0
 * TYPE F  20   15    10     5  1     0  0  0     0  0  0
 * TYPE D  10    8     5     2  1     0  0  0     0  0  0
 * BOLT   1-4  1-3          1-2                            1
 * </pre>
 *
 * This was the last heavy weapon without its table, and the comment that kept it that way said
 * the torpedo's strength by range "belongs to the seeker in flight, not to the launcher sitting
 * on the ship". The sheet disproves it: the table is printed on the LAUNCHING ship, because the
 * decision it supports — bolt now, or let the torpedo fly — is made at the launcher.
 */

const BANDS_R = ['0–5', '6–10', '11–12', '13–14', '15', '16–18', '19', '20',
                 '21–23', '24', '25', '26–28', '29', '30'];

const ROWS_R: Record<string, (number | null)[]> = {
  'Type-R': [50, 50, 35, 35, 35, 25, 25, 25, 20, 20, 20, 10, 5, 1],
  'Type-S': [30, 30, 22, 22, 22, 15, 15, 15, 10, 5, 1, 0, 0, 0],
  'Type-G': [20, 20, 15, 15, 15, 10, 5, 1, 0, 0, 0, 0, 0, 0],
  'Type-F': [20, 15, 10, 5, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0],
  'Bolt':   [4, 3, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1],
};

function modes(plasmaType: string, ladder: string[]) {
  const t = getWeaponFullTable('Plasma-A', null, false, plasmaType, { plasmaTypes: ladder });
  if (!t?.modes) throw new Error('a plasma launcher must produce a warhead strength table');
  return t.modes;
}

const we = () => modes('R', ['R', 'S', 'G', 'F']);        // War Eagle
const kr = () => modes('S', ['S', 'G', 'F']);             // Romulan KR
const racked = () => modes('S', ['S', 'G', 'F', 'D']);    // ...plus a plasma rack

describe('the War Eagle’s table, a plasma-R', () => {
  it('reproduces the sheet’s fourteen bands', () => {
    expect(we().labels).toEqual(BANDS_R);
  });

  it.each(Object.keys(ROWS_R))('matches row by row: %s', label => {
    const line = we().lines.find(l => l.label === label);
    expect(line, `${label} is missing`).toBeDefined();
    expect(line!.cells).toEqual(ROWS_R[label]);
  });

  it('prints the whole download ladder from one launcher (FP1.13)', () => {
    // "A launcher may be used to load a less powerful, but not a more powerful, torpedo."
    // One plasma-R launcher, four warhead rows.
    expect(we().lines.map(l => l.label))
      .toEqual(['Type-R', 'Type-S', 'Type-G', 'Type-F', 'Bolt']);
  });

  it('carries the corrected type-R value at range 25', () => {
    // The data bug this work found: 25 damage sat between four 20s and a 10.
    const r = we().lines.find(l => l.label === 'Type-R')!.cells as number[];
    expect(r[we().labels.indexOf('25')]).toBe(20);
    expect(r[we().labels.indexOf('24')]).toBe(20);
    expect(r[we().labels.indexOf('26–28')]).toBe(10);
  });
});

describe('the KR’s table, a plasma-S', () => {
  it('stops where the strongest type stops', () => {
    // The sheet ends at 25 for a plasma-S and at 30 for a plasma-R; a trailing all-zero band
    // would be a column that says nothing.
    expect(kr().labels[kr().labels.length - 1]).toBe('25');
    expect(we().labels[we().labels.length - 1]).toBe('30');
  });

  it('prints three warhead rows, not four', () => {
    expect(kr().lines.map(l => l.label)).toEqual(['Type-S', 'Type-G', 'Type-F', 'Bolt']);
  });

  it('agrees with the War Eagle wherever both print the same type', () => {
    // The rows are properties of the TORPEDO, not of the ship printing them.
    for (const label of ['Type-S', 'Type-G', 'Type-F']) {
      const mine   = kr().lines.find(l => l.label === label)!.cells;
      const theirs = we().lines.find(l => l.label === label)!.cells;
      for (let i = 0; i < mine.length; i++)
        expect(mine[i], `${label} band ${kr().labels[i]}`).toBe(theirs[i]);
    }
  });
});

describe('type-D, which arrives by a different rule (FP1.133)', () => {
  it('is NOT reachable by downloading, so it needs a rack to appear', () => {
    // "Type-F and larger launchers cannot download a type-D torpedo." Same launcher, same
    // ladder, and the only difference is what the hull carries.
    expect(kr().lines.map(l => l.label)).not.toContain('Type-D');
    expect(racked().lines.map(l => l.label)).toContain('Type-D');
  });

  it('sits at the bottom, below type-F', () => {
    const labels = racked().lines.map(l => l.label);
    expect(labels.indexOf('Type-D')).toBeGreaterThan(labels.indexOf('Type-F'));
    expect(labels[labels.length - 1]).toBe('Bolt');
  });

  it('prints the rack torpedo’s own weaker numbers', () => {
    const d = racked().lines.find(l => l.label === 'Type-D')!.cells as number[];
    expect(d.slice(0, 5)).toEqual([10, 8, 5, 2, 1]);
  });
});

describe('the bolt row', () => {
  it('is a hit line, so it prints as the sheet’s span rather than a bare number', () => {
    const bolt = we().lines.find(l => l.label === 'Bolt')!;
    expect(bolt.kind).toBe('hit');
    expect(bolt.twoDice).toBeFalsy();     // one die: the cell reads "1-4", not "2-4"
  });

  it('shares the warhead rows’ columns, which is the comparison it exists for', () => {
    // Bolt now against letting the torpedo fly: the trade is only visible side by side.
    const bolt = we().lines.find(l => l.label === 'Bolt')!;
    expect(bolt.cells.length).toBe(we().labels.length);
    expect(bolt.cells[0]).toBe(4);                       // 1-4 inside range 6
    expect(bolt.cells[we().labels.indexOf('21–23')]).toBe(1);
  });
});

describe('what happens without a ladder from the server', () => {
  it('falls back to the launcher’s own row rather than guessing the hull', () => {
    const t = getWeaponFullTable('Plasma-A', null, false, 'G', {});
    expect(t?.modes?.lines.map(l => l.label)).toEqual(['Type-G', 'Bolt']);
  });

  it('still answers the single-mode shape the hover readout reads', () => {
    const t = getWeaponFullTable('Plasma-A', null, false, 'R', { plasmaTypes: ['R', 'S'] })!;
    // A bolt scores half the warhead, rounded down (FP8.4).
    expect(t.bands[0].damage[0]).toBe(25);
    expect(t.bands[0].hitOn).toBe(4);
  });
});
