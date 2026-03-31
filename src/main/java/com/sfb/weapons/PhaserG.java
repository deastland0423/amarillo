package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.Ship;
import com.sfb.utilities.DiceRoller;

/**
 * Gatling Phaser
 * 
 * @author Daniel Eastland
 *
 */
public class PhaserG extends VariableDamageWeapon implements DirectFire {

	// The damage chart for this weapon.
	private static final int[][] hitChart = {
			// Ranges 0-15
			{ 4, 4, 4, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }, // Roll 1
			{ 4, 4, 4, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 }, // Roll 2
			{ 4, 4, 4, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, // Roll 3
			{ 4, 4, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, // Roll 4
			{ 4, 3, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, // Roll 5
			{ 3, 3, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, // Roll 6
	};

	public PhaserG() {
		setDacHitLocaiton("phaser");
		setType("PhaserG");
		setMinRange(0);
		setMaxRange(15);
		setMaxShotsPerTurn(4);
		setMinImpulseGap(0);
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
		return 0.25;
	}
}
