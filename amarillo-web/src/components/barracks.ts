import type { ShipObject } from '../types/gameState';

/** (G28.31) "Each barracks (BAR) box on the SSD can hold up to ten boarding parties." */
export const BOARDING_PARTIES_PER_BARRACKS = 10;

/**
 * (G28.32, G28.33) How many boarding parties die with the next barracks box to be destroyed.
 *
 * Mirrors `Ship.partiesInTheEmptiestBarracksBox`. The server is authoritative; this exists only
 * so the DAC dialog can tell the player what the button costs before they press it.
 *
 * G28.32 lets the owner "divide the non-guards among the barracks spaces as he sees fit, up to
 * the limit of ten per barracks box", and the DAC lets him pick the box that takes the hit, so
 * the parties are PACKED into as few boxes as possible and the EMPTIEST box is lost first.
 * Fifteen parties in three boxes sit 10/5/0, and successive hits cost 0, then 5, then 10.
 *
 * Packing saves nobody from a determined attacker — shelter is `min(roster, boxes x 10)` whatever
 * the arrangement — it DEFERS the loss, and a hit that costs nothing today may be the last hit of
 * the scenario.
 *
 * Extracted from `DacChoiceDialog` and pinned by tests because this is a MIRROR, and a mirror
 * drifts silently: it spent a day computing an even spread after the rule had become packing, and
 * nothing failed. The server kills the right marines either way, so the only symptom is a dialog
 * quoting a price that was never charged.
 */
export function partiesLostWithNextBarracksBox(ship: ShipObject | null | undefined): number {
  const boxes = ship?.availableBarracks ?? 0;
  const parties = ship?.boardingParties ?? 0;
  if (boxes <= 0 || parties <= 0) return 0;

  const sheltered = Math.min(parties, boxes * BOARDING_PARTIES_PER_BARRACKS);
  const fullBoxes = Math.floor(sheltered / BOARDING_PARTIES_PER_BARRACKS);
  const remainder = sheltered % BOARDING_PARTIES_PER_BARRACKS;
  const occupied  = fullBoxes + (remainder > 0 ? 1 : 0);

  if (occupied < boxes) return 0;        // a box stands empty, and that is the one that dies
  if (remainder > 0)    return remainder; // no empties left, so the part-filled box is cheapest
  return BOARDING_PARTIES_PER_BARRACKS;   // every surviving box is full
}

/** The DAC dialog's one-line price for destroying a barracks box. */
export function barracksLossLabel(ship: ShipObject | null | undefined): string {
  const n = partiesLostWithNextBarracksBox(ship);
  if (n <= 0) return 'empty';
  return `${n} boarding part${n === 1 ? 'y' : 'ies'} lost with it (G28.33)`;
}
