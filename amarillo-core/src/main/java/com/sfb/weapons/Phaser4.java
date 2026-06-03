package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.Ship;

public class Phaser4 extends VariableDamageWeapon implements DirectFire, PhaserWeapon {

	// Range bands: [0] 0-3, [1] 4-5, [2] 6, [3] 7, [4] 8, [5] 9, [6] 10, [7] 11-13,
	// [8] 14-17, [9] 18-25, [10] 26-40, [11] 41-70, [12] 71-100
	private static final int[][] bandHitChart = {
			{ 20, 20, 20, 15, 12, 10, 8, 6, 5, 4, 3, 2, 1 }, // Roll 1
			{ 20, 20, 15, 12, 11, 9, 8, 6, 4, 3, 2, 1, 0 }, // Roll 2
			{ 20, 15, 12, 11, 10, 8, 7, 5, 4, 2, 1, 0, 0 }, // Roll 3
			{ 20, 15, 11, 10, 9, 8, 6, 4, 3, 1, 0, 0, 0 }, // Roll 4
			{ 15, 12, 10, 9, 8, 7, 5, 3, 2, 0, 0, 0, 0 }, // Roll 5
			{ 15, 10, 9, 8, 7, 6, 5, 3, 1, 0, 0, 0, 0 } // Roll6
	};

	/**
	 * Constructor
	 */
	public Phaser4() {
		setDacHitLocaiton("phaser");
		setType("Phaser4");
		setMinRange(0);
		setMaxRange(100);
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

		// Can not damage targets beyond maximum range (100 for ph-4)
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
		return 2;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	static int rangeBand(int range) {
		if (range <= 3)
			return 0;
		if (range <= 5)
			return 1;
		if (range <= 6)
			return 2;
		if (range <= 7)
			return 3;
		if (range <= 8)
			return 4;
		if (range <= 9)
			return 5;
		if (range <= 10)
			return 6;
		if (range <= 13)
			return 7;
		if (range <= 17)
			return 8;
		if (range <= 25)
			return 9;
		if (range <= 40)
			return 10;
		if (range <= 70)
			return 11;
		if (range <= 100)
			return 12;
		return 13; // Out of range
	}

}
