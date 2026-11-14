import { describe, expect, it } from 'vitest';
import { costTwelfths, energyForSpeed, speedForEnergy } from './moveCost';

/**
 * Movement cost in twelfths.
 *
 * The bug: `Math.floor(20 / 0.6666666666666666)` is 29. That division was the energy dialog's
 * speed slider, and on twelve hulls it refused the ship's top speed — while the server's own
 * gate, which has a tolerance, would have accepted the allocation. Core guards the same
 * division with `+ 1e-6` and the server's gate with `+ 0.001`; the slider was written later by
 * someone who did not know to. Integers end the pattern instead of adding a third epsilon.
 */

const TWO_THIRDS  = { moveCost: 2 / 3,  moveCostTwelfths: 8 };
const ONE_THIRD   = { moveCost: 1 / 3,  moveCostTwelfths: 4 };
const THREE_QTRS  = { moveCost: 0.75,   moveCostTwelfths: 9 };
const ONE         = { moveCost: 1,      moveCostTwelfths: 12 };

describe('the hulls the slider used to short-change', () => {
  /**
   * `0.6666667` is the spelling the SHIP FILES use, and what the DTO carried before this work.
   * It is the value the regression has to be stated against: the fix to core now sends the
   * nearest double to 8/12 instead, which happens to divide correctly at these numbers — so
   * asserting against the new value would quietly test nothing.
   */
  const AS_SHIPPED = 0.6666667;

  const cases = [
    { hulls: 'Hydran NAC, NEC, NSC, NVL, HR, TR', warp: 20, speed: 30 },
    { hulls: 'Lyran CL, Orion AR, CR, SAL',       warp: 20, speed: 30 },
    { hulls: 'Kzinti CL, CVE',                    warp: 18, speed: 27 },
  ];

  it.each(cases)('$hulls: $warp warp buys speed $speed', ({ warp, speed }) => {
    expect(speedForEnergy(TWO_THIRDS, warp)).toBe(speed);
  });

  it.each(cases)('$hulls: the old division offered one less', ({ warp, speed }) => {
    expect(Math.floor(warp / AS_SHIPPED)).toBe(speed - 1);
  });

  it('is fixed twice over, and deliberately so', () => {
    // Core now sends 8/12 rather than 0.6666667, which divides correctly at these numbers all
    // by itself. That is luck, not a design: it holds for the costs and capacities we happen
    // to have. The integer path is what makes it true for the ones we do not.
    expect(Math.floor(20 / (2 / 3))).toBe(30);          // the better double, by luck
    expect(Math.floor(20 / AS_SHIPPED)).toBe(29);       // the shipped one, wrong
    expect(speedForEnergy(TWO_THIRDS, 20)).toBe(30);    // integers, right either way
  });
});

describe('energy for a speed', () => {
  it('is exact where the rules say it is a whole number', () => {
    // The user's own statement of the rule: a two-thirds hull zeroes out on multiples of 3.
    expect(energyForSpeed(TWO_THIRDS, 3)).toBe(2);
    expect(energyForSpeed(TWO_THIRDS, 9)).toBe(6);
    expect(energyForSpeed(TWO_THIRDS, 30)).toBe(20);
    expect(energyForSpeed(ONE_THIRD, 3)).toBe(1);
    expect(energyForSpeed(ONE_THIRD, 30)).toBe(10);
    expect(energyForSpeed(THREE_QTRS, 4)).toBe(3);
  });

  it('is exact with strict equality, not "close enough"', () => {
    // toBe on a float is the whole point: 2.0000001 would pass any tolerance-based check and
    // is precisely the value this exists to stop.
    for (let speed = 3; speed <= 30; speed += 3)
      expect(energyForSpeed(TWO_THIRDS, speed)).toBe((speed / 3) * 2);
  });

  it('still produces the honest repeating value off the multiples', () => {
    // No claim that every speed is exact — 2/3 of one hex is not a terminating decimal and
    // pretending otherwise would be a different lie.
    expect(energyForSpeed(TWO_THIRDS, 1)).toBeCloseTo(0.6667, 4);
    expect(energyForSpeed(TWO_THIRDS, 2)).toBeCloseTo(1.3333, 4);
  });
});

describe('speed for an energy', () => {
  it('round-trips every speed at every cost the data uses', () => {
    for (const ship of [TWO_THIRDS, ONE_THIRD, THREE_QTRS, ONE,
                        { moveCost: 0.25, moveCostTwelfths: 3 },
                        { moveCost: 0.5,  moveCostTwelfths: 6 },
                        { moveCost: 1.5,  moveCostTwelfths: 18 },
                        { moveCost: 2,    moveCostTwelfths: 24 }])
      for (let speed = 1; speed <= 30; speed++)
        expect(speedForEnergy(ship, energyForSpeed(ship, speed)), `cost ${ship.moveCost} speed ${speed}`)
          .toBe(speed);
  });

  it('does not gift a hex: short of a full one still rounds down', () => {
    // The counterpart risk. A fix that rounds up turns a rounding bug into a cheating bug.
    expect(speedForEnergy(TWO_THIRDS, energyForSpeed(TWO_THIRDS, 4) - 0.01)).toBe(3);
    expect(speedForEnergy(ONE, 3.99)).toBe(3);
    expect(speedForEnergy(THREE_QTRS, 2.99)).toBe(3);
  });

  it('absorbs the float noise an old save or an older server may still carry', () => {
    // 0.6666667 was the stored spelling, so 3 x it is 2.0000001 and 4 x it is 2.6666668.
    expect(speedForEnergy(TWO_THIRDS, 3 * 0.6666667)).toBe(3);
    expect(speedForEnergy(ONE_THIRD, 9 * 0.3333333)).toBe(9);   // 2.9999997, must not be 8
  });

  it('buys nothing with nothing', () => {
    expect(speedForEnergy(TWO_THIRDS, 0)).toBe(0);
  });
});

describe('reading the cost', () => {
  it('prefers the twelfths the server sends', () => {
    expect(costTwelfths(TWO_THIRDS)).toBe(8);
  });

  it('falls back to the decimal for a server too old to send them', () => {
    // Safe to round, for the same reason core's parser is strict: the decimal is always within
    // a rounding error of a real twelfth, because every cost in the game is one.
    expect(costTwelfths({ moveCost: 0.6666667 })).toBe(8);
    expect(costTwelfths({ moveCost: 0.3333333 })).toBe(4);
    expect(costTwelfths({ moveCost: 1.5 })).toBe(18);
  });

  it('treats a zero or missing twelfths as absent rather than as a free ship', () => {
    // Jackson sends an int field as 0 when it is not set, so a stale server reaches here with
    // moveCostTwelfths: 0 rather than with the key missing. Dividing by it would be infinite.
    expect(costTwelfths({ moveCost: 0.6666667, moveCostTwelfths: 0 })).toBe(8);
    expect(speedForEnergy({ moveCost: 0.6666667, moveCostTwelfths: 0 }, 20)).toBe(30);
  });
});
