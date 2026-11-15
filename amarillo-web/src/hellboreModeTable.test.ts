import { describe, expect, it } from 'vitest';
import {
  getWeaponFullTable,
  HELLBORE_DF_DMG, HELLBORE_ENV_DMG, HELLBORE_OVLD_DF, HELLBORE_OVLD_ENV,
} from './weaponDamageTables';

/**
 * The HELLBORE COMBAT RESOLUTION TABLE, transcribed from the paper SSD (owner's clip,
 * 2026-11-14).
 *
 * <pre>
 * RANGE         0-1   2   3-4   5-8  9-15  16-22  23-40
 * HIT#           11  10     9     8     7      6      5
 * BASE DAMAGE    20  17    15    13    10      8      4
 * O/L DAMAGE     30  25    22    19
 * </pre>
 *
 * THREE rows. The first version of this had five, printing enveloping and direct fire each in
 * standard and overloaded form, on the reasoning that two independent choices cross here. The
 * sheet does not, and the data says why: direct fire is exactly half, so those rows repeated a
 * rule instead of adding a number.
 */

const PAPER_BANDS = ['0–1', '2', '3–4', '5–8', '9–15', '16–22', '23–40'];

const PAPER_ROWS: Record<string, (number | null)[]> = {
  'Hit (2d6)':    [11, 10, 9, 8, 7, 6, 5],
  'Base damage':  [20, 17, 15, 13, 10, 8, 4],
  'O/L damage':   [30, 25, 22, 19, null, null, null],
};

function modes(arming: string | null = null, directFire = false) {
  const t = getWeaponFullTable('Hellbore-A', arming, directFire);
  if (!t?.modes) throw new Error('a hellbore must produce a multi-mode table');
  return t.modes;
}

describe('the printed hellbore table', () => {
  it('reproduces the sheet’s bands', () => {
    expect(modes().labels).toEqual(PAPER_BANDS);
  });

  it.each(Object.keys(PAPER_ROWS))('matches row by row: %s', label => {
    const line = modes().lines.find(l => l.label === label);
    expect(line, `${label} is missing`).toBeDefined();
    expect(line!.cells).toEqual(PAPER_ROWS[label]);
  });

  it('has exactly the sheet’s three rows and no more', () => {
    expect(modes().lines.map(l => l.label)).toEqual(Object.keys(PAPER_ROWS));
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
});

describe('where the overload stops', () => {
  it('prints NA past range 8, not a zero', () => {
    // setOverload caps the weapon at 8. NA says "this mode cannot fire at that range"; a 0
    // would claim it fires and achieves nothing, which is a different and wrong statement.
    const ovld = modes().lines.find(l => l.label === 'O/L damage')!;
    const i = modes().labels.indexOf('9–15');
    expect(ovld.cells[i]).toBeNull();
    expect(ovld.cells.slice(i).every(c => c === null)).toBe(true);
  });

  it('leaves the base row running all the way to 40', () => {
    const base = modes().lines.find(l => l.label === 'Base damage')!;
    expect(base.cells[base.cells.length - 1]).toBe(4);
  });
});

describe('why direct fire is not a row', () => {
  it('is exactly half the base damage, rounded down, in every band', () => {
    // The licence for leaving it off the table (E10.7, owner's call 2026-11-14). It holds for
    // the overload row too. If this ever fails, the omission has started hiding a real number
    // and the row has to come back.
    HELLBORE_ENV_DMG.forEach((base, i) => {
      expect(HELLBORE_DF_DMG[i], `band ${i}: ${base}`).toBe(Math.floor(base / 2));
    });
    HELLBORE_OVLD_ENV.forEach((base, i) => {
      expect(HELLBORE_OVLD_DF[i], `ovld hex ${i}: ${base}`).toBe(Math.floor(base / 2));
    });
  });

  it('is still honoured by the single-mode shape the hover readout reads', () => {
    // GameBoard's hover table has not been taught `modes` and reads `bands`, which must keep
    // honouring directFire — dropping the ROW from the panel must not drop the CAPABILITY.
    const env = getWeaponFullTable('Hellbore-A', null, false)!;
    const df  = getWeaponFullTable('Hellbore-A', null, true)!;
    expect(env.bands[0].damage[0]).toBe(20);
    expect(df.bands[0].damage[0]).toBe(10);
  });

  it('does not mention direct fire anywhere in the printed rows', () => {
    const labels = modes().lines.map(l => l.label.toLowerCase()).join(' ');
    expect(labels).not.toContain('direct');
    expect(labels).not.toContain('env');
  });
});

describe('what the table does not depend on', () => {
  it('is the same table however it is armed and whatever directFire says', () => {
    const base = JSON.stringify(modes());
    for (const arming of ['STANDARD', 'OVERLOAD', null])
      for (const df of [true, false])
        expect(JSON.stringify(modes(arming, df)), `${arming}/${df}`).toBe(base);
  });
});
