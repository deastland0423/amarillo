package com.sfb.weapons;

import com.sfb.utilities.DiceRoller;

// These weapons do variable damage at each range determined
// by the die roll.
public abstract class VariableDamageWeapon extends Weapon {

	// 2D chart. Vertical is die roll, horizontal is range.
	private static final int[][] hitChart = {{}};

	/** Roll 1d6, record it as lastRoll, and return the value. */
	protected int rollAndRecord() {
		int roll = new DiceRoller().rollOneDie();
		setLastRoll(roll);
		return roll;
	}

	/**
	 * Look up damage from a 2-D table applying the weapon's ECM shift (D6.34/D6.35).
	 * If adjusted roll <= 6: use table[adjusted-1][range] as normal.
	 * If adjusted roll > 6: each excess point shifts one range bracket right,
	 *   and the roll-6 row of that bracket is used (per the SFB rules example).
	 */
	protected int lookupWithShift(int[][] table, int roll, int range) {
		int adjusted = roll + getEcmShift();
		if (adjusted <= 6) {
			int r = Math.min(range, table[0].length - 1);
			return table[adjusted - 1][r];
		}
		int excess      = adjusted - 6;
		int shiftedRange = Math.min(range + excess, table[0].length - 1);
		return table[5][shiftedRange]; // row 5 = die roll 6
	}
}
