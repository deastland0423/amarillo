package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.Ship;

/**
 * Gatling Phaser
 * 
 * @author Daniel Eastland
 *
 */
public class PhaserG extends VariableDamageWeapon implements DirectFire, PhaserWeapon {

	/** A gatling phaser fires four times a turn, and all four may be in one impulse. */
	public static final int SHOTS_PER_TURN = 4;

	// Range bands: [0] 0, [1] 1, [2] 2, [3] 3, [4] 4-8, [5] 9-15 (identical to Phaser3)
	private static final int[][] bandHitChart = {
			{ 4, 4, 4, 3, 1, 1 }, // Roll 1
			{ 4, 4, 4, 2, 1, 0 }, // Roll 2
			{ 4, 4, 4, 1, 0, 0 }, // Roll 3
			{ 4, 4, 3, 0, 0, 0 }, // Roll 4
			{ 4, 3, 2, 0, 0, 0 }, // Roll 5
			{ 3, 3, 1, 0, 0, 0 }, // Roll 6
	};

	public PhaserG() {
		setDacHitLocaiton("phaser");
		setType("PhaserG");
		setDisplayName("Phaser-G");
		setMinRange(0);
		setMaxRange(15);
		setMaxShotsPerTurn(SHOTS_PER_TURN);
		// No gap between shots, ON PURPOSE: a gatling may put all four into a single
		// impulse (owner's ruling 2026-09-26). This sits oddly beside the ADD's gap of 1,
		// which exists to stop exactly that — so it is pinned by PhaserGRateOfFireTest
		// rather than left looking like an oversight.
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
			// The only way a Ph-G runs out: four shots a turn, with no gap between them.
			// The message used to describe a Ph-1's eight-impulse wait, which this weapon
			// has never had.
			throw new WeaponUnarmedException(getName() + " has fired all "
					+ getMaxShotsPerTurn() + " of its shots this turn");
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
		return 0.25;
	}

	static int rangeBand(int range) {
		if (range <= 0)  return 0;
		if (range <= 1)  return 1;
		if (range <= 2)  return 2;
		if (range <= 3)  return 3;
		if (range <= 8)  return 4;
		return 5; // 9-15, and out of range
	}

	/** J1.3321: when a fighter carrying a Ph-G is crippled, reduce it to Ph-3 (max 1 shot/turn). */
	public void reduceToPhaserThree() {
		setMaxShotsPerTurn(1);
	}

	public boolean isReducedToPhaserThree() {
		return getMaxShotsPerTurn() == 1;
	}

	/**
	 * Give it back its four shots — the reverse of J1.3321, for a fighter repaired out of its
	 * crippled state (J4.818).
	 */
	public void restoreFromPhaserThree() {
		setMaxShotsPerTurn(SHOTS_PER_TURN);
	}

	/**
	 * G24.1342: a gatling blinds a scout channel only when it fires MORE THAN ONCE in one
	 * impulse — "and will blind a channel every impulse it does so".
	 * <p>
	 * So a scout may spend all four shots across four separate impulses and keep its channel,
	 * and loses one the moment it doubles up. Owner's reading, 2026-10-05, and the rule only
	 * makes sense that way: G24.1341 exempts the phaser-3 and a phaser-G is a gatling of
	 * phaser-3 shots, so if a single shot blinded, G24.1342's sentence about firing more than
	 * once would say nothing that "everything not listed above" had not already said.
	 * <p>
	 * This class previously inherited {@code Weapon}'s default of true and blinded on EVERY
	 * shot, which made a gatling-armed scout blind itself for no reason. It extends
	 * VariableDamageWeapon rather than {@link Phaser3}, so it never picked up that exemption.
	 */
	@Override
	public boolean blindsScoutChannels() {
		return getShotsThisImpulse() > 1;
	}
}
