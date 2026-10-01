import { describe, expect, it } from 'vitest';
import { CHANNEL_LEND_MAX, channelLendCeiling } from './scoutLending';

/**
 * G24.2112's six points are shared between the two steppers, which is the whole reason this
 * is a function rather than a constant. These pin the three cases the control actually hits:
 * an untouched channel, a channel with the six spent, and the self-protection case where
 * G24.283 bars ECCM entirely.
 */
describe('channelLendCeiling', () => {
  it('lets either kind take the whole six when nothing is committed', () => {
    expect(channelLendCeiling(0, 0)).toBe(CHANNEL_LEND_MAX);
  });

  it('stops at what is already held once the six are spent', () => {
    // 4 ECM + 2 ECCM = 6. Neither stepper may go up, and both must still show their own value,
    // so the ceiling equals the current figure rather than dropping below it.
    expect(channelLendCeiling(4, 6)).toBe(4);
    expect(channelLendCeiling(2, 6)).toBe(2);
  });

  it('offers only the unspent remainder to the other kind', () => {
    // 4 ECM committed: ECCM may still climb to 2, and ECM itself back up to the full six.
    expect(channelLendCeiling(0, 4)).toBe(2);
    expect(channelLendCeiling(4, 4)).toBe(CHANNEL_LEND_MAX);
  });

  it('never returns a ceiling below the points already held', () => {
    // Defensive: the pair total should never exceed six, but if a stale draft ever said so,
    // a ceiling under the current value would make the stepper render an out-of-range figure.
    expect(channelLendCeiling(3, 9)).toBe(3);
  });
});
