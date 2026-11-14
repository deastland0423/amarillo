import type { ShipObject } from '../types/gameState';

/**
 * Movement cost in TWELFTHS, so the client divides in integers and never loses a hex.
 *
 * Every movement cost in the game is a whole twelfth — 1/4 is 3, 1/3 is 4, 1/2 is 6, 2/3 is 8,
 * 3/4 is 9, 1 is 12, 1.5 is 18, 2 is 24 — but none of the thirds survives as a decimal. The
 * server sends 0.6666666666666666 for two-thirds, and `Math.floor(20 / 0.6666666666666666)` is
 * 29 where the answer is 30.
 *
 * <h2>The bug this exists to stop</h2>
 * That division was the energy dialog's speed slider. On twelve hulls — the Hydran NAC, NEC,
 * NSC, NVL, HR and TR, the Lyran CL, the Orion AR, CR and SAL at 20 warp, the Kzinti CL and CVE
 * at 18 — it silently refused the ship's top speed. The server's own gate has a tolerance and
 * would have accepted the allocation its client would not offer.
 *
 * It is the second bug of exactly this shape. Core guards the same division with `+ 1e-6` and
 * the server's gate with `+ 0.001`; the slider was written later by someone who did not know to.
 * Integers end the pattern rather than adding a third epsilon to it.
 */

const PER_POINT = 12;

/**
 * The hull's cost in twelfths, falling back to the decimal for a server too old to send it.
 *
 * The fallback rounds, which is safe for the same reason core's parser is strict: the decimal
 * is always within a rounding error of a real twelfth, because every cost in the game is one.
 */
export function costTwelfths(ship: Pick<ShipObject, 'moveCost' | 'moveCostTwelfths'>): number {
  if (ship.moveCostTwelfths != null && ship.moveCostTwelfths > 0)
    return ship.moveCostTwelfths;
  return Math.max(1, Math.round((ship.moveCost ?? 1) * PER_POINT));
}

/** Warp energy to buy this speed, exactly: speed 3 on a two-thirds hull is 2, not 2.0000001. */
export function energyForSpeed(
  ship: Pick<ShipObject, 'moveCost' | 'moveCostTwelfths'>, speed: number,
): number {
  return (speed * costTwelfths(ship)) / PER_POINT;
}

/**
 * Speed this much energy buys, rounding down.
 *
 * The energy is multiplied up before the division so the quotient is integer arithmetic, and a
 * hair of float noise in the energy itself cannot drop a hex. Short of a full hex still buys
 * nothing: the rounding is applied to the energy, not to the result.
 */
export function speedForEnergy(
  ship: Pick<ShipObject, 'moveCost' | 'moveCostTwelfths'>, energy: number,
): number {
  const twelfths = costTwelfths(ship);
  if (twelfths <= 0) return 0;
  // Energy arrives as a sum of warp boxes, so it is a whole number or a clean fraction of one;
  // rounding to the nearest twelfth removes accumulated noise without gifting a partial hex.
  const energyTwelfths = Math.round(energy * PER_POINT * 1e4) / 1e4;
  return Math.floor(energyTwelfths / twelfths);
}
