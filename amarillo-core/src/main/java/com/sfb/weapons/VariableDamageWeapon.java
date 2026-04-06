package com.sfb.weapons;


// These weapons do variable damage at each range determined
// by the die roll.
public abstract class VariableDamageWeapon extends Weapon {

	// 2D chart. Vertical is die roll, horizontal is range.
	private static final int[][] hitChart = {{}};
	
}
