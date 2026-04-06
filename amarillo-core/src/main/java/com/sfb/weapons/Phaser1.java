package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.Ship;
import com.sfb.utilities.DiceRoller;

public class Phaser1 extends VariableDamageWeapon implements DirectFire {

	// The damage chart for this weapon.
	private static final int[][] hitChart = {
			// Ranges 0-75
			{9,8,7,6,5,5,4,4,4,3,3,3,3,3,3,3,2,2,2,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}, // Roll 1
			{8,7,6,5,5,4,3,3,3,2,2,2,2,2,2,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}, // Roll 2
			{7,5,5,4,4,4,3,3,3,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}, // Roll 3
			{6,4,4,4,4,3,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}, // Roll 4
			{5,4,4,4,3,3,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}, // Roll 5
			{4,4,3,3,2,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}  // Roll 6
			};
	
	/**
	 * Constructor
	 */
	public Phaser1() {
		setDacHitLocaiton("phaser");
		setType("Phaser1");
		setMinRange(0);
		setMaxRange(75);
	}

	/**
	 * @param range The range from the shooter to the target
	 * 
	 * @return The damage done by the weapon at that range.
	 * @throws TargetOutOfRangeException 
	 * @throws CapacitorException 
	 */
	@Override
	public int fire(int range) throws TargetOutOfRangeException, CapacitorException, WeaponUnarmedException {

		if (!canFire()) {
			throw new WeaponUnarmedException("Phaser not ready — must wait 8 impulses between shots.");
		}

		// If this phaser is mounted on a ship, drain the capacitor
		// the amount needed to fire this phaser.
		if (fetchOwningShip() instanceof Ship) {
			Ship firingShip = (Ship)fetchOwningShip();
			firingShip.drainCapacitor(energyToFire());
		}
		
		// Can not damage targets beyond maximum range (75 for ph-1)
		if (range > getMaxRange()) {
			throw new TargetOutOfRangeException("Target not in weapon range.");
		}
		// Roll the 1d6 to determing damage
		DiceRoller diceRoller = new DiceRoller();
		int roll = diceRoller.rollOneDie();
		
		// Return the value that matches the die roll and the range.
		registerFire();
		return hitChart[roll - 1][range];
	}
	
	/**
	 * Fetch the energy needed from the capacitor to fire this weapon.
	 * 
	 * @return The energy needed to fire the weapon.
	 */
	public double energyToFire() {
		return 1;
	}

}
