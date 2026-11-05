import { describe, it, expect } from 'vitest';
import { capRows, dogfightCapRows, overCaps, shipsOverCaps } from './droneAvailability';
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

describe('dogfightCapRows', () => {
  /** A Klingon D6C's shape: eight ordinary spaces in its type-A racks, eight dogfight. */
  function withDogfight(over: Partial<DroneAvailability> = {}): DroneAvailability {
    return figures({
      countedSpaces: 8, restrictedAllowed: 2, limitedAllowed: 1,
      dogfightSpaces: 8, dogfightRestrictedUsed: 0, dogfightRestrictedAllowed: 2,
      dogfightLimitedUsed: 0, dogfightLimitedAllowed: 1,
      ...over,
    });
  }

  it('draws nothing on a hull with no dogfight rack', () => {
    expect(dogfightCapRows(figures())).toEqual([]);
  });

  it('draws both ceilings for a hull that has one', () => {
    const rows = dogfightCapRows(withDogfight());
    expect(rows.map(r => r.rule)).toEqual(['FD10.622', 'FD10.632']);
    expect(rows.map(r => r.allowed)).toEqual([2, 1]);
  });

  /**
   * A server from before FD10.644 was counted sends none of these keys, and `undefined > n` is
   * false — which would read as "inside the cap" rather than as "no reading". The guard is on
   * the BASIS, so a reply without it draws no row at all instead of a row of zeroes that looks
   * like a verdict.
   */
  it('draws nothing when the server sent no dogfight figures at all', () => {
    const stale = figures({ dogfightSpaces: undefined });
    expect(dogfightCapRows(stale)).toEqual([]);
    expect(overCaps(stale)).toBe(false);
  });
});

describe('overCaps and the two pools', () => {
  /**
   * FD10.644: the pools are counted separately, so a breach in either one is a breach — and
   * neither can be hidden by the other having room. This is the comparison that locks Save, and
   * before the dogfight rows fed into it the dialog would have offered to save a fleet the
   * server then refused.
   */
  it('is true when only the dogfight pool is breached', () => {
    expect(overCaps(figures({
      restrictedUsed: 1, dogfightSpaces: 8, dogfightRestrictedUsed: 4,
      dogfightRestrictedAllowed: 2,
    }))).toBe(true);
  });

  it('is false when a full dogfight pool is exactly at its own ceiling', () => {
    expect(overCaps(figures({
      restrictedUsed: 1, dogfightSpaces: 8, dogfightRestrictedUsed: 2,
      dogfightRestrictedAllowed: 2, dogfightLimitedUsed: 0, dogfightLimitedAllowed: 1,
    }))).toBe(false);
  });

  it('does not let a roomy dogfight pool excuse the ordinary one', () => {
    expect(overCaps(figures({
      restrictedUsed: 2, dogfightSpaces: 8, dogfightRestrictedUsed: 0,
      dogfightRestrictedAllowed: 2,
    }))).toBe(true);
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
