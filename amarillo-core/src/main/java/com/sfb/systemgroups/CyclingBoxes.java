package com.sfb.systemgroups;

import java.util.List;

/**
 * The questions you may ask a bank of SSD boxes that are used one at a time and then cool off.
 *
 * Laboratories, transporters and tractor beams all work this way: a box is spent and is then
 * unavailable for the longer of the next turn or eight impulses. G4.22 and G4.451 spell it out
 * for laboratories, and G7.13 gives a tractor beam the same shape.
 * <p>
 * Each of the three used to answer these questions under a different name, and "available" was
 * the worst of it. It meant SURVIVED DAMAGE on transporters and tractors, FREE RIGHT NOW on
 * laboratories, and on {@code Transporters.availableUses} it meant free-now capped by the energy
 * to power them — a figure that must never be published, because an opponent could read the
 * battery state back out of it. Three questions wearing one word is how a reader comes to pick
 * the wrong one, and the names gave no warning.
 * <p>
 * So this interface names the QUESTIONS; it deliberately does not unify the storage, because the
 * storage genuinely differs. A tractor beam can be IN USE, holding something across impulses,
 * which no lab or transporter box can — so {@link Tractors} keeps its own {@link TractorBeam}
 * objects while the other two share a {@link BoxCycle}. Forcing all three into one bank would
 * recreate the "three things a single counter cannot hold at once" mistake that BoxCycle's own
 * header exists to prevent, except that for tractors there are four.
 * <p>
 * <b>Every count here is public knowledge.</b> A box on the SSD is public and so is what it is
 * doing — a hit-and-run raid can name a specific box (D7.835) precisely because its state is
 * seen. The ENERGY behind the boxes is not, which is why no question on this interface takes
 * energy into account. A reader that needs the energy cap too has to ask the system for it by a
 * name that says so, and must not publish the answer.
 */
public interface CyclingBoxes {

	/** Boxes printed on the SSD, damaged or not. The denominator of a damage gauge. */
	int printedBoxes();

	/** Boxes that survived damage, whether or not they are busy right now. */
	int intactBoxes();

	/**
	 * Boxes that can take a NEW job at this impulse: intact, not spent this turn, and past the
	 * quarter-turn delay since their last use.
	 * <p>
	 * Never greater than {@link #intactBoxes()}, and the difference between the two is what is
	 * cooling off. That difference is worth showing rather than hiding: "no boxes left" and
	 * "boxes still cooling" are the same number to a bare count, and the second one reads to a
	 * player as a bug in the game.
	 *
	 * @param absoluteImpulse the game clock, since the cycle is measured in impulses
	 */
	int readyBoxes(int absoluteImpulse);

	/**
	 * Boxes occupied right now by a job that spans impulses.
	 * <p>
	 * Only a tractor beam can be in this state: it holds its unit until something releases it
	 * (G7.0), where a lab or a transporter box finishes within the impulse it was used in. Zero
	 * for everything else, and that is not a stub awaiting work — it is the honest answer.
	 */
	default int holdingBoxes() {
		return 0;
	}

	/**
	 * What each box on the SSD is doing, in printed order — the counts above, itemised.
	 *
	 * <p>Five states rather than {@link BoxCycle.State}'s three, because a readout has to
	 * describe boxes that {@code BoxCycle} cannot: a DESTROYED one is gone from its list
	 * entirely (that is how {@code intactBoxes} falls), and a HOLDING one is a tractor's
	 * business and not the bank's. The two enums are kept apart deliberately — the inner one
	 * is the cycle rule and belongs to whoever enforces it; this one is the picture.
	 */
	enum BoxState {
		/** Undamaged and free to take a job right now. */
		UNUSED,
		/** Spent during this turn, and so unavailable for the rest of it (G4.22). */
		USED_THIS_TURN,
		/** Spent late last turn and still inside the quarter-turn delay (G4.451). */
		COOLING_DOWN,
		/** Holding a unit across impulses. Tractor beams only (G7.0). */
		HOLDING,
		/** Shot off the SSD. Still printed, and still numbered, but gone. */
		DESTROYED
	}

	/**
	 * One box: its printed NUMBER, what it is doing, and what it holds if anything.
	 *
	 * <p>The number is the identity and not the position. A hit-and-run raid names a box
	 * (D7.835) and a scout channel holds one across four attempts (G24.251), so a readout
	 * that numbered boxes by where they sit in a list would point at the wrong one the
	 * moment a neighbour was shot off.
	 *
	 * @param holding the name of the unit held, or null — only ever set on HOLDING
	 */
	record BoxReadout(int number, BoxState state, String holding) {
	}

	/**
	 * Every box printed on the SSD, destroyed ones included, in printed order.
	 * <p>
	 * Public, like the counts: the whole reason box identity is modelled is that an attacker
	 * can pick one out (D7.835), which he can only do because he can see what each is doing.
	 */
	List<BoxReadout> boxReadout(int absoluteImpulse);
}
