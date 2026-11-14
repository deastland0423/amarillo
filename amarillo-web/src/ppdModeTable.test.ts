import { describe, expect, it } from 'vitest';
import { getWeaponFullTable } from './weaponDamageTables';

/**
 * The PPD's table (E11.3), transcribed from PpdSplash.java's own chart.
 *
 * <pre>
 * RANGE              0-3   4-10  11-15  16-20  21-25  26-30  31-40
 * HIT (2d6)           -      9      8      7      6      5      4
 * DAMAGE              0      6      5      4      3      2      1
 *   facing            0      4      3      2      1      1      1
 *   adj (strong)      0      1      1      1      1      1      0
 *   adj (weak)        0      1      1      1      1      0      0
 * SIDE-ON strong      0      3      3      2      2      1      1
 * SIDE-ON weak        0      3      2      2      1      1      0
 * </pre>
 *
 * The PPD was the only heavy weapon whose SSD entry produced NO table at all — there was never
 * a branch for it, although this chart's data has sat in weaponDamageTables.ts, commented and
 * correct, the whole time.
 *
 * It is also the chart that most needs printing, because it runs BACKWARDS against every other
 * weapon in the game: nothing inside range 4, a peak at 4-10, and it still reaches 40. A player
 * reading it with a disruptor's assumptions has it exactly inverted.
 */

const PAPER_BANDS = ['0–3', '4–10', '11–15', '16–20', '21–25', '26–30', '31–40'];

const PAPER_ROWS: Record<string, (number | null)[]> = {
  'Hit (2d6)':       [null, 9, 8, 7, 6, 5, 4],
  'Damage':          [0, 6, 5, 4, 3, 2, 1],
  '· facing':        [0, 4, 3, 2, 1, 1, 1],
  '· adj (strong)':  [0, 1, 1, 1, 1, 1, 0],
  '· adj (weak)':    [0, 1, 1, 1, 1, 0, 0],
  'Side-on, strong': [0, 3, 3, 2, 2, 1, 1],
  'Side-on, weak':   [0, 3, 2, 2, 1, 1, 0],
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
  it('splits into three elements that sum to the Damage row', () => {
    // "The Damage row is a statistical total and is never what lands on a single shield." If
    // these ever stop summing, one of the two is wrong and the table is lying about a shot.
    const row = (l: string) => modes().lines.find(x => x.label === l)!.cells as number[];
    const total = row('Damage');
    const parts = [row('· facing'), row('· adj (strong)'), row('· adj (weak)')];
    for (let i = 0; i < total.length; i++)
      expect(parts[0][i] + parts[1][i] + parts[2][i], `band ${i}`).toBe(total[i]);
  });

  it('drops the weaker neighbour before the stronger one', () => {
    // E11.352: at two points ignore the weaker adjacent shield, at one ignore both. So the
    // weak row must reach zero no later than the strong row does.
    const weak   = modes().lines.find(l => l.label === '· adj (weak)')!.cells as number[];
    const strong = modes().lines.find(l => l.label === '· adj (strong)')!.cells as number[];
    const firstZero = (a: number[]) => a.findIndex((v, i) => i > 0 && v === 0);
    expect(firstZero(weak)).toBeLessThan(firstZero(strong));
  });

  it('keeps the facing shield scoring at every range the weapon reaches', () => {
    const facing = modes().lines.find(l => l.label === '· facing')!.cells as number[];
    for (let i = 1; i < facing.length; i++)
      expect(facing[i], `band ${i}`).toBeGreaterThan(0);
  });

  it('splits side-on fire between two shields, summing to the same total', () => {
    // E11.351: a line of fire along a hex side hits no single shield.
    const row = (l: string) => modes().lines.find(x => x.label === l)!.cells as number[];
    const total = row('Damage');
    const alt   = [row('Side-on, strong'), row('Side-on, weak')];
    for (let i = 0; i < total.length; i++)
      expect(alt[0][i] + alt[1][i], `band ${i}`).toBe(total[i]);
  });

  it('gives the odd point to the stronger shield', () => {
    const strong = modes().lines.find(l => l.label === 'Side-on, strong')!.cells as number[];
    const weak   = modes().lines.find(l => l.label === 'Side-on, weak')!.cells as number[];
    for (let i = 0; i < strong.length; i++)
      expect(strong[i], `band ${i}`).toBeGreaterThanOrEqual(weak[i]);
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
