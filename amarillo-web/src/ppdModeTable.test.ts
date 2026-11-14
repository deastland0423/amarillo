import { describe, expect, it } from 'vitest';
import { getWeaponFullTable } from './weaponDamageTables';

/**
 * The PPD's table, transcribed from the PLASMATIC PULSAR DEVICE COMBAT TABLE printed on an ISC
 * SSD (owner's clip, 2026-11-14).
 *
 * <pre>
 * RANGE    0-3   4-10   11-15  16-20  21-25  26-30  31-40
 * HIT*      -      9       8      7      6      5      4
 * DAMAGE    0      6       5      4      3      2      1
 * SPLASH    0   1+4+1   1+3+1  1+2+1  1+1+1  1+1+0  0+1+0
 * ALT       0    3+3     3+2    2+2    2+1    1+1    1+0
 * </pre>
 *
 * FOUR rows, which is the point. The first draft of this expanded SPLASH and ALT into a row per
 * shield — facing, stronger adjacent, weaker adjacent, then two more for the hex-side split —
 * and said the same thing in seven rows instead of four. The sheet writes the spread as one
 * composite cell, adjacent + facing + adjacent, so its SHAPE reads at a glance with the big
 * number in the middle where the facing shield is.
 *
 * The PPD was the only heavy weapon whose SSD entry produced NO table at all, although this
 * data has sat in weaponDamageTables.ts the whole time, commented and mirroring PpdSplash.java.
 *
 * It is also the chart that most needs printing, because it runs BACKWARDS against every other
 * weapon in the game: nothing inside range 4, a peak at 4-10, and it still reaches 40.
 */

const PAPER_BANDS = ['0–3', '4–10', '11–15', '16–20', '21–25', '26–30', '31–40'];

const PAPER_ROWS: Record<string, (number | string | null)[]> = {
  'Hit (2d6)': [null, 9, 8, 7, 6, 5, 4],
  'Damage':    [0, 6, 5, 4, 3, 2, 1],
  'Splash':    ['0', '1+4+1', '1+3+1', '1+2+1', '1+1+1', '1+1+0', '0+1+0'],
  'Alt':       ['0', '3+3', '3+2', '2+2', '2+1', '1+1', '1+0'],
};

function modes(arming: string | null = null) {
  const t = getWeaponFullTable('PPD-A', arming);
  if (!t?.modes) throw new Error('a PPD must produce a multi-mode table');
  return t.modes;
}

describe('the printed PPD table', () => {
  it('produces a table at all, which it never used to', () => {
    expect(getWeaponFullTable('PPD-A', null)).not.toBeNull();
  });

  it('reproduces the data’s own bands', () => {
    expect(modes().labels).toEqual(PAPER_BANDS);
  });

  it.each(Object.keys(PAPER_ROWS))('matches row by row: %s', label => {
    const line = modes().lines.find(l => l.label === label);
    expect(line, `${label} is missing`).toBeDefined();
    expect(line!.cells).toEqual(PAPER_ROWS[label]);
  });

  it('marks the hit line as two dice, which changes what the span means', () => {
    // A wave-lock is 2d6 (E11.32), so the cell reads "2-9", not "1-9" — and 2-9 on two dice is
    // a far better chance than eight-of-twelve looks. The renderer prints the asterisk and the
    // footnote off this flag, so without it the player reads the PPD as much less accurate
    // than it is. Found by mutation: every other assertion here passed with it set false.
    const hit = modes().lines.find(l => l.label === 'Hit (2d6)')!;
    expect(hit.twoDice).toBe(true);
    expect(modes().lines.filter(l => l.kind === 'damage').every(l => !l.twoDice)).toBe(true);
  });

  it('has no row the chart does not have', () => {
    expect(modes().lines.map(l => l.label).sort()).toEqual(Object.keys(PAPER_ROWS).sort());
  });
});

describe('the direction, which is the thing a player gets wrong', () => {
  it('shows NA inside range 4, where every other weapon is at its best', () => {
    const hit = modes().lines.find(l => l.label === 'Hit (2d6)')!;
    expect(hit.cells[0]).toBeNull();
    expect(modes().lines.find(l => l.label === 'Damage')!.cells[0]).toBe(0);
  });

  it('peaks at 4-10 and decays outward, not inward', () => {
    const dmg = modes().lines.find(l => l.label === 'Damage')!.cells as number[];
    expect(dmg[1]).toBe(6);                       // the peak is the SECOND band
    for (let i = 2; i < dmg.length; i++)
      expect(dmg[i], `band ${i}`).toBeLessThan(dmg[i - 1]);
  });

  it('still scores at range 40, where a disruptor stopped at 22', () => {
    const last = modes().labels.length - 1;
    expect(modes().labels[last]).toBe('31–40');
    expect(modes().lines.find(l => l.label === 'Damage')!.cells[last]).toBe(1);
  });
});

describe('the splash, which is not optional (E11.35)', () => {
  /** "1+4+1" -> [1, 4, 1]; a bare "0" -> [0]. */
  const parts = (cell: string) => cell.split('+').map(Number);
  const row   = (l: string) => modes().lines.find(x => x.label === l)!.cells as string[];
  const total = () => modes().lines.find(x => x.label === 'Damage')!.cells as number[];

  it('writes the spread as one cell, adjacent + facing + adjacent', () => {
    // The facing shield is the MIDDLE number, which is why the composite is worth keeping: the
    // shape of 1+4+1 against 1+1+1 is the whole difference between the two ranges.
    expect(row('Splash')[1]).toBe('1+4+1');
    expect(parts(row('Splash')[1])[1]).toBe(4);     // 4 on the facing shield at 4-10
    expect(parts(row('Splash')[4])[1]).toBe(1);     // 1 by 21-25, spread evenly
  });

  it('sums to the Damage row in every band', () => {
    // "The Damage row is a statistical total and is never what lands on a single shield." If
    // the two ever stop agreeing, one of them is lying about a shot.
    const t = total();
    row('Splash').forEach((cell, i) => {
      expect(parts(cell).reduce((a, b) => a + b, 0), `band ${i}`).toBe(t[i]);
    });
  });

  it('drops the weaker neighbour before the stronger, and keeps the facing shield longest', () => {
    // E11.352: at two points ignore the weaker adjacent shield, at one ignore both. The sheet
    // shows that as 1+1+0 and then 0+1+0 — so the facing shield is the last to stop scoring.
    expect(row('Splash')[5]).toBe('1+1+0');
    expect(row('Splash')[6]).toBe('0+1+0');
    for (let i = 1; i < row('Splash').length; i++)
      expect(parts(row('Splash')[i])[1], `band ${i} facing`).toBeGreaterThan(0);
  });

  it('splits side-on fire between two shields, summing to the same total', () => {
    // E11.351: a line of fire along a hex side hits no single shield.
    const t = total();
    row('Alt').forEach((cell, i) => {
      expect(parts(cell).reduce((a, b) => a + b, 0), `band ${i}`).toBe(t[i]);
    });
  });

  it('gives the odd point to the stronger shield', () => {
    for (const cell of row('Alt').slice(1)) {
      const [strong, weak] = parts(cell);
      expect(strong, cell).toBeGreaterThanOrEqual(weak);
    }
  });

  it('declares the composite rows as TEXT, not as damage', () => {
    // The renderer prints both the same way today, so this is a contract rather than a visible
    // behaviour — which is exactly why it needs stating. A damage cell is a number something
    // may total; "1+4+1" is not, and a future preview that sums the damage rows must be able
    // to tell them apart without parsing. Found by mutation: nothing else noticed the swap.
    const kind = (l: string) => modes().lines.find(x => x.label === l)!.kind;
    expect(kind('Splash')).toBe('text');
    expect(kind('Alt')).toBe('text');
    expect(kind('Damage')).toBe('damage');
    expect(kind('Hit (2d6)')).toBe('hit');
  });

  it('prints a bare 0 where the weapon scores nothing, not "0+0+0"', () => {
    expect(row('Splash')[0]).toBe('0');
    expect(row('Alt')[0]).toBe('0');
  });
});

describe('what the table deliberately does not say', () => {
  it('has ONE hit line, because overloading buys pulses and nothing else', () => {
    // E11.61: six pulses over six impulses instead of four over four. Same chart per pulse, so
    // a second hit row would imply an accuracy gain that is not there — the hellbore ruling.
    expect(modes().lines.filter(l => l.kind === 'hit')).toHaveLength(1);
  });

  it('is the same table however the weapon is armed', () => {
    const base = JSON.stringify(modes());
    for (const arming of ['STANDARD', 'OVERLOAD', null])
      expect(JSON.stringify(modes(arming)), `${arming}`).toBe(base);
  });

  it('says in its note that the figures are PER PULSE', () => {
    // Without this the Damage row reads as a whole turn's shooting and understates the weapon
    // fourfold. The pulse count lives here rather than in a row for the same reason.
    const t = getWeaponFullTable('PPD-A', null)!;
    expect(t.note).toContain('per pulse');
    expect(t.note).toContain('4 pulses');
  });

  it('still answers the old single-mode shape for the hover readout', () => {
    const t = getWeaponFullTable('PPD-A', null)!;
    expect(t.bands[0].label).toBe('0–3');
    expect(t.bands[1].damage[0]).toBe(6);
    expect(t.bands[1].hitOn).toBe(9);
    expect(t.bands[1].twoDice).toBe(true);
  });
});
