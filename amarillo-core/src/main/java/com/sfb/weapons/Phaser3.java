package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.Ship;

/**
 * Type-3 Defensive Phaser
 * 
 * @author Daniel Eastland
 *
 */
public class Phaser3 extends VariableDamageWeapon implements DirectFire {


	// Range bands: [0] 0, [1] 1, [2] 2, [3] 3, [4] 4-8, [5] 9-15
	private static final int[][] bandHitChart = {
			{ 4, 4, 4, 3, 1, 1 }, // Roll 1
			{ 4, 4, 4, 2, 1, 0 }, // Roll 2
			{ 4, 4, 4, 1, 0, 0 }, // Roll 3
			{ 4, 4, 3, 0, 0, 0 }, // Roll 4
			{ 4, 3, 2, 0, 0, 0 }, // Roll 5
			{ 3, 3, 1, 0, 0, 0 } // Roll6
	};

	public Phaser3() {
		setDacHitLocaiton("phaser");
		setType("Phaser3");
		setMinRange(0);
		setMaxRange(15);
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

		// Weapon can not hit anything past range 15
		if (range > getMaxRange()) {
			throw new TargetOutOfRangeException("Target is out of weapon range.");
		}

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
		return 0.5;
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
		if (range <= 8)
			return 4;
		if (range <= 15)
			return 5;
		return 6; // Out of range
	}
}
