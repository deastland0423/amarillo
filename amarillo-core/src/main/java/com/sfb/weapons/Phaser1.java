package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.Ship;

public class Phaser1 extends VariableDamageWeapon implements DirectFire {


	// Range bands: [0] 0, [1] 1, [2] 2, [3] 3, [4] 4, [5] 5, [6] 6-8, [7] 9-15, [8]
	// 16-25, [9] 26-50, [10] 51-75
	private static final int[][] bandHitChart = {
			{ 9, 8, 7, 6, 5, 5, 4, 3, 2, 1, 1 }, // Roll 1
			{ 8, 7, 6, 5, 5, 4, 3, 2, 1, 1, 0 }, // Roll 2
			{ 7, 5, 5, 4, 4, 4, 3, 1, 0, 0, 0 }, // Roll 3
			{ 6, 4, 4, 4, 4, 3, 2, 0, 0, 0, 0 }, // Roll 4
			{ 5, 4, 4, 4, 3, 3, 1, 0, 0, 0, 0 }, // Roll 5
			{ 4, 4, 3, 3, 2, 2, 0, 0, 0, 0, 0 } // Roll6
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
			Ship firingShip = (Ship) fetchOwningShip();
			firingShip.drainCapacitor(energyToFire());
		}

		// Can not damage targets beyond maximum range (75 for ph-1)
		if (range > getMaxRange()) {
			throw new TargetOutOfRangeException("Target not in weapon range.");
		}
		// Roll the 1d6 to determing damage
		int roll = rollAndRecord();
		registerFire();
		return lookupWithShift(bandHitChart, roll, rangeBand(range));
	}

	/**
	 * Fetch the energy needed from the capacitor to fire this weapon.
	 * 
	 * @return The energy needed to fire the weapon.
	 */
	public double energyToFire() {
		return 1;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	static int rangeBand(int range) {
		if (range <= 0)
			return 0;
		if (range <= 1)
			return 1;
		if (range <= 2)
			return 2;
		if (range <= 3)
			return 3;
		if (range <= 4)
			return 4;
		if (range <= 5)
			return 5;
		if (range <= 8)
			return 6;
		if (range <= 15)
			return 7;
		if (range <= 25)
			return 8;
		if (range <= 50)
			return 9;
		if (range <= 75)
			return 10;
		return 11; // Out of range
	}

}
