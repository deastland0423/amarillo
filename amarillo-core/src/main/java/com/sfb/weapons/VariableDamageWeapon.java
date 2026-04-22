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
}
