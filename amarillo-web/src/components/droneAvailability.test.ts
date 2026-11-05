import { describe, it, expect } from 'vitest';
import { capRows, overCaps, shipsOverCaps } from './droneAvailability';
import type { DroneAvailability } from '../api/gameApi';

/**
 * The browser half of FD10.622/FD10.632: reading the figures, not computing them.
 *
 * Every number here is one core sent. What is under test is the comparison that locks the Save
 * button and the two rows the gauge draws — in particular that a breach of EITHER cap counts,
 * since FD10.64 nests them and the Limited allowance on a small rack is zero.
 */

function figures(over: Partial<DroneAvailability> = {}): DroneAvailability {
  return {
    shipName:          'USS Test',
    countedSpaces:     4,
    restrictedUsed:    0,
    restrictedAllowed: 1,
    restrictedPercent: 25,
    limitedUsed:       0,
    limitedAllowed:    0,
    limitedPercent:    10,
    relaxed:           false,
    ...over,
  };
}

describe('capRows', () => {
  it('reports both caps, Restricted first, breach or not', () => {
    const rows = capRows(figures());
    expect(rows.map(r => r.rule)).toEqual(['FD10.622', 'FD10.632']);
    expect(rows.every(r => !r.over)).toBe(true);
  });

  it('carries each cap its own percentage, which is not always the same pair', () => {
    // A Kzinti hull, or a big carrier, or a "D%" one: FD10.622 and FD10.632 double together.
    const rows = capRows(figures({
      restrictedAllowed: 2, restrictedPercent: 50,
      limitedAllowed: 1, limitedPercent: 20, relaxed: true,
    }));
    expect(rows.map(r => r.percent)).toEqual([50, 20]);
    expect(rows.map(r => r.allowed)).toEqual([2, 1]);
  });

  it('marks a cap over only when it is exceeded, not when it is exactly met', () => {
    // 25% of four spaces is one, and one Restricted space is the whole of it. FD10.641 rounds,
    // so "exactly at the cap" is the normal state of a legal special loadout rather than a
    // boundary nobody reaches.
    expect(capRows(figures({ restrictedUsed: 1 }))[0].over).toBe(false);
    expect(capRows(figures({ restrictedUsed: 1.5 }))[0].over).toBe(true);
  });
});

describe('overCaps', () => {
  it('is false for a ship inside both', () => {
    expect(overCaps(figures({ restrictedUsed: 1 }))).toBe(false);
  });

  /**
   * The case a four-space rack makes routine: 10% of four rounds down to none (FD10.641), so a
   * single type-III is over the Limited cap while the Restricted one still has room. A check
   * that only looked at the worse-sounding tier would pass it.
   */
  it('is true when only the Limited cap is breached', () => {
    expect(overCaps(figures({ restrictedUsed: 1, limitedUsed: 1 }))).toBe(true);
  });

  it('is true when only the Restricted cap is breached', () => {
    expect(overCaps(figures({ restrictedUsed: 2 }))).toBe(true);
  });
});

describe('shipsOverCaps', () => {
  it('names the offenders and leaves the legal ships out', () => {
    expect(shipsOverCaps({
      a: figures({ shipName: 'USS Legal', restrictedUsed: 1 }),
      b: figures({ shipName: 'USS Over', limitedUsed: 1 }),
    })).toEqual(['USS Over']);
  });

  /**
   * No reading is not a clean bill of health.
   *
   * The figures come from a request that may not have answered yet, so an empty result has to
   * mean "nothing known". The dialog may lock Save on a name that appears here; it must never
   * read silence as permission, which is why this returns the empty list rather than throwing
   * or defaulting to legal-looking figures.
   */
  it('says nothing at all when there are no figures', () => {
    expect(shipsOverCaps(undefined)).toEqual([]);
    expect(shipsOverCaps({})).toEqual([]);
  });
});
