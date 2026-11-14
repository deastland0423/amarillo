import type { BoxBank, BoxState, SsdBox } from '../types/gameState';

/**
 * Drawing the SSD boxes that cool off: laboratories, transporters and tractor beams.
 *
 * These are the one group of systems a coloured bar cannot describe. A bar shows two numbers
 * and these keep three — printed, intact, and free to work right now — so a bank with every
 * box undamaged but spent reads as full health while being useless to its owner. Drawing one
 * mark per printed box is the only readout with room for all of it, and it is also how the
 * paper sheet does it.
 *
 * Worth more than looks: a box is identified by its printed NUMBER, which is why a hit-and-run
 * raid can name one (D7.835) and a scout channel can hold one across four attempts (G24.251).
 * Drawn individually and numbered, the panel becomes the natural place to eventually CLICK one.
 */

/** The row label. The DTO sends the SSD's own wording; the panel has 4.4rem for it. */
export function bankLabel(system: string): string {
  switch (system) {
    case 'Laboratories':  return 'Labs';
    case 'Tractor Beams': return 'Tractors';
    default:              return system;
  }
}

export interface BoxLook {
  /** Interior colour, or 'transparent' for a box that is drawn hollow. */
  fill:   string;
  border: string;
  /** Draw a diagonal strike through it: the box is gone, not merely busy. */
  struck: boolean;
  /** Hover text, which is where the five states are actually NAMED for the player. */
  title:  string;
}

const READY     = '#56d364';   // the green shieldStrengthColor uses for a healthy bar
const SPENT     = '#6e7681';   // the panel's muted grey: spent, and back next turn
const COOLING   = '#f0c040';   // the yellow of a hurt shield: spent, and NOT back next turn
const HOLDING   = '#22d3ee';   // the cyan the energy dialog already gives tractors
const WRECKED   = '#f85149';

/**
 * How one box is drawn, and what hovering it says.
 *
 * The distinction worth the extra colour is SPENT against COOLING. Both mean "cannot use this
 * now", so a count folds them together; but the first clears at the turn boundary and the
 * second has already survived one, which is the difference between waiting two impulses and
 * waiting ten. A player deciding whether to spend his last transporter needs it.
 */
export function boxLook(box: SsdBox): BoxLook {
  switch (box.state) {
    case 'UNUSED':
      return { fill: READY, border: READY, struck: false, title: 'unused' };
    case 'USED_THIS_TURN':
      return { fill: 'transparent', border: SPENT, struck: false,
               title: 'used this turn (G4.22)' };
    case 'COOLING_DOWN':
      return { fill: 'transparent', border: COOLING, struck: false,
               title: 'cooling down — used late last turn (G4.451)' };
    case 'HOLDING':
      // The name matters more than the state here: on a tug with four links, "holding" alone
      // leaves the player counting beams to work out which tow he is about to break.
      return { fill: HOLDING, border: HOLDING, struck: false,
               title: box.holding != null ? `holding ${box.holding}` : 'holding' };
    case 'DESTROYED':
      return { fill: 'transparent', border: WRECKED, struck: true, title: 'destroyed' };
  }
}

/** How many boxes of the bank are in each state. Counted from the BOXES, never the flat */
/** fields, so the words beside the drawing cannot disagree with the drawing. */
export function countStates(bank: BoxBank): Record<BoxState, number> {
  const counts: Record<BoxState, number> = {
    UNUSED: 0, USED_THIS_TURN: 0, COOLING_DOWN: 0, HOLDING: 0, DESTROYED: 0,
  };
  for (const b of bank.boxes) counts[b.state]++;
  return counts;
}

/**
 * The words beside the boxes: "3", or "1 of 3 free · 1 holding · 1 destroyed".
 *
 * An undamaged, untouched bank says only its size, because every box is already drawn green
 * and a count of them would be the same fact twice. Everything else is named, in the order a
 * player cares: what he can still do, then why he cannot do more.
 */
export function bankSummary(bank: BoxBank): string {
  const c = countStates(bank);
  if (c.UNUSED === bank.printed) return String(bank.printed);

  const parts = [`${c.UNUSED} of ${bank.printed} free`];
  if (c.HOLDING)        parts.push(`${c.HOLDING} holding`);
  if (c.USED_THIS_TURN) parts.push(`${c.USED_THIS_TURN} used`);
  if (c.COOLING_DOWN)   parts.push(`${c.COOLING_DOWN} cooling`);
  if (c.DESTROYED)      parts.push(`${c.DESTROYED} destroyed`);
  return parts.join(' · ');
}

/**
 * The banks worth drawing, in the order the DTO sent them.
 *
 * A bank the hull does not have is already absent — the server leaves it out rather than
 * sending a zero — so this only has to survive an older server that sends nothing at all.
 */
export function banksToDraw(banks: BoxBank[] | undefined): BoxBank[] {
  return (banks ?? []).filter(b => b.printed > 0);
}
