import type { ShipObject } from '../types/gameState';
import { shieldStrengthColor } from '../types/gameState';

/**
 * Damage gauges for the SSD panel's two-state rows.
 *
 * A system box is either printed and working or shot off the SSD, and both numbers are already
 * in the DTO, so a bar can say everything true about it. That is NOT so for the systems built on
 * {@code BoxCycle} — transporters, tractors and laboratories — which have a third state, a box
 * that survives but is cooling off for the longer of the next turn or eight impulses (G4.22,
 * G4.451). A bar drawn over two of those three numbers would read as undamaged while half the
 * bank was unusable, so those rows stay as text until there is a readout that can say it.
 */

/** One system's surviving boxes against the number printed on its SSD. */
export type Gauge = { caption: string; now: number; max: number };

/**
 * Build the gauges for one row, dropping every system this hull does not have.
 *
 * A hull with no AWR shows nothing rather than an empty bar: zero of zero is not damage, and a
 * column of flat bars for systems that were never printed reads as a wreck. A system that IS
 * printed and has been shot to nothing stays — that is the loudest thing the row can say.
 */
export function gauges(
  ...specs: Array<[string, number | undefined, number | undefined]>
): Gauge[] {
  const out: Gauge[] = [];
  for (const [caption, now, max] of specs)
    if (max != null && max > 0)
      out.push({ caption, now: now ?? 0, max });
  return out;
}

/**
 * The power row's gauges, in SSD order.
 *
 * These specs live here rather than in the panel because two things now read them: the rows
 * themselves and the collapsed section header that summarises them. A header that counted a
 * different set of systems from the rows it hides is the kind of drift nobody notices, since
 * both halves look right on their own.
 */
export function powerGauges(ship: ShipObject): Gauge[] {
  return gauges(
    ['L warp', ship.availableLWarp, ship.maxLWarp],
    ['C warp', ship.availableCWarp, ship.maxCWarp],
    ['R warp', ship.availableRWarp, ship.maxRWarp],
    ['impulse', ship.availableImpulse, ship.maxImpulse],
    ['APR', ship.availableApr, ship.maxApr],
    ['AWR', ship.availableAwr, ship.maxAwr],
    // Boxes, not charge: an enemy sees how many battery boxes survive and never what is in them.
    ['battery', ship.availableBattery, ship.maxBattery],
  );
}

/** The hull row's gauges, in SSD order. */
export function hullGauges(ship: ShipObject): Gauge[] {
  return gauges(
    ['fore', ship.availableFhull, ship.maxFhull],
    ['aft', ship.availableAhull, ship.maxAhull],
    ['centre', ship.availableChull, ship.maxChull],
  );
}

/**
 * The worst-hit gauge, or null when every one of them is untouched.
 *
 * "Worst" is the smallest surviving FRACTION, not the smallest count: two boxes left of three
 * is a healthier ship than two left of twelve, and a summary that named the first would send
 * the reader to the wrong system. Ties keep SSD order, so the answer is stable.
 */
export function worstGauge(list: Gauge[]): Gauge | null {
  let worst: Gauge | null = null;
  for (const g of list) {
    if (g.now >= g.max) continue;
    if (worst === null || g.now / g.max < worst.now / worst.max)
      worst = g;
  }
  return worst;
}

/** A destroyed system box. Not in the shield ramp, because no shield is ever in this state. */
const DESTROYED = '#f85149';

/**
 * Gauge colour, which deliberately diverges from {@link shieldStrengthColor} at zero.
 *
 * A shield at zero is merely DOWN and comes back, so the shield ramp greys it out — quiet,
 * because it is temporary. A system box at zero has been shot off the SSD and is not coming
 * back, which is the loudest state the row has rather than the quietest. Above zero the two
 * share a ramp on purpose, so a crippled engine room reads the same amber in the panel that a
 * failing shield reads on the map.
 */
export function gaugeColour(now: number, max: number): string {
  return now === 0 ? DESTROYED : shieldStrengthColor(now, max);
}
