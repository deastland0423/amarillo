package com.sfb.weapons;

// These weapons simply hit or miss depending on a roll.
// They then do damage, often depending on the range.
public abstract class HitOrMissWeapon extends Weapon {

	public abstract int[] getHitChart();

	/** Roll 1d6, record it as lastRoll, and return the value. */
	protected int rollAndRecord() {
		int roll = new com.sfb.utilities.DiceRoller().rollOneDie();
		setLastRoll(roll);
		return roll;
	}
}
