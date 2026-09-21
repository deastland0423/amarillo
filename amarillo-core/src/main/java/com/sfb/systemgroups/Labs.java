package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.Marker;
import com.sfb.objects.Unit;
import com.sfb.utilities.DiceRoller;
import com.sfb.utilities.MapUtils;

/**
 * The laboratory boxes (G4.0): scientific research, identifying seeking weapons, emergency
 * damage repair and tactical intelligence.
 * <p>
 * Boxes are tracked individually, because a lab is not simply available or not — it carries
 * a cooling-off period. G4.22: "Each lab can make one such attempt a turn, and not within a
 * quarter turn of any use in a previous turn." G4.451 spells out what that means: a lab used
 * during the last eight impulses of a turn cannot be used again during the first eight
 * impulses of the next. So each box remembers the absolute impulse it was last used, and
 * availability is computed from that rather than stored.
 * <p>
 * Keeping a per-box impulse also separates three things this class used to hold in one
 * counter: how many boxes the SSD has, how many survive (damage), and how many are free to
 * take a job right now (usage). Merging them meant a destroyed box came back at the start of
 * the next turn, and a lab merely USED counted as a missing box for cripple calculations.
 */
public class Labs implements Systems {

	/** Impulses in a quarter turn (G4.451). */
	public static final int QUARTER_TURN = BoxCycle.QUARTER_TURN;

	/**
	 * The boxes themselves. Labs were the first system to need per-box cycling, so the
	 * model was built here; it is shared now, because transporters and tractors follow the
	 * same rule (a box is used, then unavailable until the next turn or eight impulses,
	 * whichever is longer).
	 */
	private final BoxCycle boxes = new BoxCycle();

	private Unit owningUnit;

	public Labs(Unit owner) {
		this.owningUnit = owner;
	}

	// Initialize the operations systems to the SSD values.
	@Override
	public void init(Map<String, Object> values) {
		boxes.init(values.get("lab") == null ? 0 : (Integer) values.get("lab"));
	}

	/// FETCH ///

	/** Boxes that still exist — undamaged, whether or not they are busy. */
	public int getFunctioningLabs() {
		return boxes.functioning();
	}

	/**
	 * Boxes free to take a job at this impulse: undamaged, unused this turn (G4.22), and
	 * past the quarter-turn delay since their last use (G4.451).
	 */
	public int availableLabs(int absoluteImpulse) {
		return boxes.available(absoluteImpulse);
	}

	/** True if box {@code index} may be put to work at this impulse. */
	public boolean isFree(int index, int absoluteImpulse) {
		return boxes.isFree(index, absoluteImpulse);
	}

	/**
	 * Put a box to work at this impulse and return which one, or -1 if none is free. The
	 * index matters to a caller that holds its box across several attempts (a scout channel
	 * gets four, G24.251) and must keep stamping the same one.
	 */
	public int useLab(int absoluteImpulse) {
		return boxes.use(absoluteImpulse);
	}

	/**
	 * Re-stamp a box already held by this caller, so the delay runs from its latest use
	 * rather than from when it was first claimed.
	 */
	public void markUsed(int index, int absoluteImpulse) {
		boxes.markUsed(index, absoluteImpulse);
	}

	// Total operations boxes on the SSD (cripple calculations).
	@Override
	public int fetchOriginalTotalBoxes() {
		return boxes.total();
	}

	/**
	 * Boxes still undamaged (cripple calculations). Deliberately NOT the count of free
	 * ones: spending a lab is not the same as losing it, and this used to report a busy
	 * ship as a damaged one.
	 */
	@Override
	public int fetchRemainingTotalBoxes() {
		return boxes.functioning();
	}

	/// DAMAGE ///

	/**
	 * Destroy one lab box. The one lost is whichever was used most recently, since that is
	 * the box a player would mark off — losing a box already spent this turn costs least.
	 */
	public boolean damage() {
		return boxes.damage();
	}

	/// REPAIR ///

	/**
	 * Restore repaired boxes. G4.31 has a repaired lab assume its function at the start of
	 * the NEXT turn; that delay is not modelled — a restored box is free immediately.
	 */
	public boolean repair(int value) {
		return boxes.repair(value);
	}

	@Override
	public void cleanUp() { }

	@Override
	public Unit fetchOwningUnit() {
		return this.owningUnit;
	}

	/**
	 * Given a target, calculate the total research points for a ship during
	 * a given turn.
	 *
	 * @param target The target that is being targeted by the ship's labs.
	 * @return The total number of research points.
	 */
	public int calculateResearchPoints(Marker target) {
		int range = MapUtils.getRange(owningUnit, target);

		return calculateResearchPoints(range);
	}

	/**
	 * Given the range to a target, calculate the total research points
	 * for a ship during a given turn.
	 *
	 * @param range The minimum range to the target achieved during the turn.
	 * @return The total number of research points.
	 */
	public int calculateResearchPoints(int range) {
		return calculateResearchPoints(range, new DiceRoller().rollOneDie());
	}

	/** Package-visible seam: the G4.11 chart with the die supplied, for tests. */
	public int calculateResearchPoints(int range, int roll) {
		// Labs don't work beyond range 9 (the chart's range-10 column is all zeroes).
		if (range > 9) {
			return 0;
		}

		// G4.11's chart, which runs 10 in the top-left corner down to 0, one step for each
		// point of die roll and each hex of range: a LOW roll is the good one. The code
		// had this the other way up (roll + 4 - range), so a 6 gathered most and a 1 least,
		// and every cell of the chart was wrong. The single test on it asked only that
		// range 8 yield under 3 points, which is true whichever way round the die runs.
		int researchPerLab = Math.max(0, 11 - roll - range);

		// G4.11: multiplied by the number of FUNCTIONING lab boxes, not the number idle —
		// research is a whole-turn activity of every surviving box.
		return researchPerLab * boxes.functioning();
	}

}
