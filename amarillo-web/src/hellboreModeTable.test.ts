import { describe, expect, it } from 'vitest';
import { getWeaponFullTable } from './weaponDamageTables';

/**
 * The hellbore's table: one to-hit line, four damage lines.
 *
 * <pre>
 * RANGE          0-1    2    3-4   5-8  9-15 16-22 23-40
 * HIT (2d6)       11    10     9     8     7     6     5
 * DMG, ENV        20    17    15    13    10     8     4
 * DMG, DIRECT     10     8     7     6     5     4     2
 * DMG, ENV OVLD   30    25    22    19     0     0     0
 * DMG, DIR OVLD   15    12    11     9     0     0     0
 * </pre>
 *
 * Two independent choices cross here: ENVELOPING vs DIRECT is how the shot is aimed, STANDARD
 * vs OVERLOAD is what was paid for it. The old table showed one of the four -- overload only if
 * the weapon was already armed that way, direct fire only if the caller passed a flag the SSD
 * panel never passes -- so the panel could only ever show enveloping standard.
 */

const PAPER_BANDS = ['0–1', '2', '3–4', '5–8',
                     '9–15', '16–22', '23–40'];

const PAPER_ROWS: Record<string, number[]> = {
  'Hit (2d6)':     [11, 10, 9, 8, 7, 6, 5],
  'Dmg, env':      [20, 17, 15, 13, 10, 8, 4],
  'Dmg, direct':   [10, 8, 7, 6, 5, 4, 2],
  'Dmg, env ovld': [30, 25, 22, 19, 0, 0, 0],
  'Dmg, dir ovld': [15, 12, 11, 9, 0, 0, 0],
};

function modes(arming: string | null = null, directFire = false) {
  const t = getWeaponFullTable('Hellbore-A', arming, directFire);
  if (!t?.modes) throw new Error('a hellbore must produce a multi-mode table');
  return t.modes;
}

describe('the printed hellbore table', () => {
  it('reproduces the data’s own bands', () => {
    expect(modes().labels).toEqual(PAPER_BANDS);
  });

  it.each(Object.keys(PAPER_ROWS))('matches row by row: %s', label => {
    const line = modes().lines.find(l => l.label === label);
    expect(line, `${label} is missing`).toBeDefined();
    expect(line!.cells).toEqual(PAPER_ROWS[label]);
  });

  it('marks the hit line as two dice, which changes how a span reads', () => {
    // 2-8 on 2d6 is far better than eight-of-twelve suggests, so the cell has to say which
    // dice it means. The renderer prints the asterisk and the footnote off this flag.
    const hit = modes().lines.find(l => l.label === 'Hit (2d6)')!;
    expect(hit.twoDice).toBe(true);
    expect(modes().lines.filter(l => l.kind === 'damage').every(l => !l.twoDice)).toBe(true);
  });

  it('has ONE hit line, because overloading a hellbore buys damage and nothing else', () => {
    // Deliberate, and worth pinning: a reader who knows the disruptor table would expect a
    // HIT (OVLD) row. The overload path in core uses the standard hit numbers, so a second
    // row would be the same numbers twice and imply an accuracy gain that is not there.
    expect(modes().lines.filter(l => l.kind === 'hit')).toHaveLength(1);
  });

  it('stops the overload rows at range 8, where the weapon stops reaching', () => {
    // setOverload caps it at 8. The 9-15 band is the first past that, and both overload rows
    // have to fall to 0 there or the table promises a shot that throws out of range.
    const i = modes().labels.indexOf('9–15');
    expect(modes().lines.find(l => l.label === 'Dmg, env ovld')!.cells[i]).toBe(0);
    expect(modes().lines.find(l => l.label === 'Dmg, dir ovld')!.cells[i]).toBe(0);
    // ...while the standard rows carry on to 40.
    expect(modes().lines.find(l => l.label === 'Dmg, env')!.cells[i]).toBe(10);
  });

  it('is the same table however it is armed and whatever directFire says', () => {
    const base = JSON.stringify(modes());
    for (const arming of ['STANDARD', 'OVERLOAD', null])
      for (const df of [true, false])
        expect(JSON.stringify(modes(arming, df)), `${arming}/${df}`).toBe(base);
  });

  it('still answers the old single-mode shape for callers that want it', () => {
    // GameBoard's hover table has not been taught `modes` and reads `bands`, which must keep
    // honouring directFire or that readout silently switches to enveloping numbers.
    const env = getWeaponFullTable('Hellbore-A', null, false)!;
    const df  = getWeaponFullTable('Hellbore-A', null, true)!;
    expect(env.bands[0].damage[0]).toBe(20);
    expect(df.bands[0].damage[0]).toBe(10);
  });
});
