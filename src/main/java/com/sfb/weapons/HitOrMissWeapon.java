package com.sfb.weapons;

// These weapons simply hit or miss depending on a roll.
// They then do damage, often depending on the range.
public abstract class HitOrMissWeapon extends Weapon {

	public abstract int[] getHitChart();

}
