package com.sfb.weapons;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;
import com.sfb.utilities.DiceRoller;

public class Disruptor extends HitOrMissWeapon implements DirectFire, HeavyWeapon {

	// STANDARD
	private final static int[] hitChart = { 0, 5, 5, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2,
			2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 };
	// OVERLOAD
	private final static int[] overloadHitChart = { 6, 5, 5, 4, 4, 4, 4, 4, 4 };

	// SPECIAL FIRE MODES
	private final static int[] derfacsHitChart = { 0, 5, 5, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3,
			3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 };
	private final static int[] uimHitChart = { 0, 5, 5, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 2, 2,
			2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 };
	private final static int[] uimOverloadHitChart = { 6, 5, 5, 5, 5, 5, 5, 5, 5 };

	// STANDARD DAMAGE
	private final static int[] damageChart = { 0, 5, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2,
			2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
	// OVERLOAD DAMAGE
	private final static int[] overloadDamageChart = { 10, 10, 8, 8, 8, 6, 6, 6, 6 };

	private WeaponArmingType armingType = WeaponArmingType.STANDARD;
	private int disruptorRange; // Maximum range for this model of disruptor (15, 22, 30, etc.)
	private int armingTurn = 0; // Number of turns the weapon has been arming.
	private double armingEnergy = 0; // Amount of total energy stored in the weapon.
	private boolean armed = false; // True if the weapon is armed and ready to fire.

	// Default will have range 30
	public Disruptor() {
		this(30);
	}

	// This is the only constructor we want to use.
	public Disruptor(int designRange) {
		setDisruptorRange(designRange);
		setMinRange(1);
		setMaxRange(getDisruptorRange());
		setDacHitLocaiton("torp");
		setType("Disruptor" + getDisruptorRange());
	}

	@Override
	public void cleanUp() {
		reset();
	}

	@Override
	public boolean setStandard() {
		// If the weapon is already armed as overloaded, it is too late
		// to set it to standard.
		if (isArmed() && armingType == WeaponArmingType.OVERLOAD) {
			return false;
		}
		this.armingType = WeaponArmingType.STANDARD;
		setMinRange(1);
		setMaxRange(getDisruptorRange());
		return true;
	}

	@Override
	public boolean setOverload() {
		this.armingType = WeaponArmingType.OVERLOAD;
		setMinRange(0);
		setMaxRange(8);
		return true;
	}

	/**
	 * Disruptors can't be held. This is a stub.
	 * 
	 * @param energy Energy applied.
	 * 
	 * @return False.
	 */
	@Override
	public boolean hold(int energy) {
		return false;
	}

	@Override
	public int getArmingTurn() {
		return armingTurn;
	}

	@Override
	public void setArmingTurn(int turn) { this.armingTurn = turn; }

	@Override
	public void setArmed(boolean armed) { this.armed = armed; }

	@Override
	public boolean isArmed() {
		return armed;
	}

	@Override
	public WeaponArmingType getArmingType() {
		return armingType;
	}

	@Override
	public void reset() {
		setStandard();
		armingEnergy = 0;
		armingTurn = 0;
		armed = false;

	}

	/**
	 * Fire the diruptors using the default targeting system.
	 * 
	 * @param range The range to the target.
	 * @return Damage dealt by the weapon to the target (0 if a miss) or an
	 *         exception if illegal condition (range, arming, etc.).
	 * @throws WeaponUnarmedException
	 * @throws TargetOutOfRangeException
	 */
	@Override
	public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
		// If the dirutptor isn't armed, it can't fire.
		if (!isArmed()) {
			throw new WeaponUnarmedException("Weapon is unarmed.");
		}

		// If the target is out of range, it can't fire.
		if (range > getMaxRange() || range < getMinRange()) {
			throw new TargetOutOfRangeException("Target not in weapon range. [" + getMinRange() + "|" + getMaxRange() + "]");
		}

		int damage = 0;
		// Roll to hit.
		DiceRoller diceRoller = new DiceRoller();

		// Based on arming type, calculate damage (0 on a miss).
		switch (armingType) {
			case STANDARD:
				// Calculate hit/damage for the range.
				if (diceRoller.rollOneDie() <= hitChart[range]) {
					damage = damageChart[range];
				}
				break;
			case OVERLOAD:
				// Calculate hit/damage for the range
				if (diceRoller.rollOneDie() <= overloadHitChart[range]) {
					damage = overloadDamageChart[range];
				}
				break;
			default:
				break;
		}

		// Once fired, the weapon is no longer armed.
		reset();

		registerFire();
		return damage;
	}

	/**
	 * Fire with scanner adjustment. Hit check uses adjustedRange;
	 * damage is looked up using realRange (scanner doesn't reduce actual damage).
	 */
	@Override
	public int fire(int realRange, int adjustedRange)
			throws WeaponUnarmedException, TargetOutOfRangeException {
		if (!isArmed()) throw new WeaponUnarmedException("Weapon is unarmed.");
		if (realRange > getMaxRange() || realRange < getMinRange())
			throw new TargetOutOfRangeException("Target not in weapon range. [" + getMinRange() + "|" + getMaxRange() + "]");

		int damage = 0;
		DiceRoller diceRoller = new DiceRoller();

		switch (armingType) {
			case STANDARD: {
				int adjIdx = Math.min(adjustedRange, hitChart.length - 1);
				if (diceRoller.rollOneDie() <= hitChart[adjIdx])
					damage = damageChart[realRange];
				break;
			}
			case OVERLOAD: {
				int adjIdx = Math.min(adjustedRange, overloadHitChart.length - 1);
				if (diceRoller.rollOneDie() <= overloadHitChart[adjIdx])
					damage = overloadDamageChart[realRange];
				break;
			}
			default: break;
		}

		reset();
		registerFire();
		return damage;
	}

	/**
	 * Fire the disruptors using the UIM targeting system.
	 * 
	 * @param range The range to the target.
	 * @return Damage dealt by the weapon to the target (0 if a miss) or an
	 *         exception if illegal condition (range, arming, etc.).
	 * @throws WeaponUnarmedException
	 * @throws TargetOutOfRangeException
	 */
	public int fireUim(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
		// If the dirutptor isn't armed, it can't fire.
		if (!isArmed()) {
			throw new WeaponUnarmedException("Weapon is unarmed.");
		}

		// If the target is out of range, it can't fire.
		if (range > getMaxRange() || range < getMinRange()) {
			throw new TargetOutOfRangeException("Target not in weapon range.");
		}

		int damage = 0;
		// Roll to hit.
		DiceRoller diceRoller = new DiceRoller();

		// Based on arming type, calculate damage (0 on a miss).
		switch (armingType) {
			case STANDARD:
				// Calculate hit/damage for the range.
				if (diceRoller.rollOneDie() <= uimHitChart[range]) {
					damage = damageChart[range];
				}
				break;
			case OVERLOAD:
				// Calculate hit/damage for the range
				if (diceRoller.rollOneDie() <= uimOverloadHitChart[range]) {
					damage = overloadDamageChart[range];
				}
				break;
			default:
				break;
		}

		// Once fired, the weapon is no longer armed.
		reset();

		registerFire();
		return damage;
	}

	/**
	 * Fire using the DERFACS targeting system. Hit check uses adjustedRange against
	 * the DERFACS hit chart (standard) or the standard overload chart (overload);
	 * damage is looked up at realRange.
	 */
	public int fireDerfacs(int realRange, int adjustedRange)
			throws WeaponUnarmedException, TargetOutOfRangeException {
		if (!isArmed()) throw new WeaponUnarmedException("Weapon is unarmed.");
		if (realRange > getMaxRange() || realRange < getMinRange())
			throw new TargetOutOfRangeException("Target not in weapon range.");

		int damage = 0;
		DiceRoller diceRoller = new DiceRoller();

		switch (armingType) {
			case STANDARD: {
				int adjIdx = Math.min(adjustedRange, derfacsHitChart.length - 1);
				if (diceRoller.rollOneDie() <= derfacsHitChart[adjIdx])
					damage = damageChart[realRange];
				break;
			}
			case OVERLOAD: {
				// DERFACS does not improve overload accuracy; use standard overload chart
				int adjIdx = Math.min(adjustedRange, overloadHitChart.length - 1);
				if (diceRoller.rollOneDie() <= overloadHitChart[adjIdx])
					damage = overloadDamageChart[realRange];
				break;
			}
			default: break;
		}

		reset();
		registerFire();
		return damage;
	}

	@Override
	public boolean arm(int energy) {
		// Putting 2 more energy into an armed standard
		// disruptor will overload it. But otherwise you can
		// not arm an armed disruptor.
		if (isArmed()) {
			if (armingEnergy == 2 && energy == 2) {
				setOverload();
			} else {
				return false;
			}
			// If the weapon is not armed, arm to the proper level for the energy.
		} else if (energy == 2) {
			setStandard();
		} else if (energy == 4) {
			setOverload();
		} else {
			return false;
		}

		// If we've made it this far, the weapon is good to arm.
		armingEnergy += energy;
		armingTurn++;
		armed = true;
		return true;
	}

	/**
	 * No special mode for disruptors, just standard and overload.
	 * 
	 * @return False.
	 */
	@Override
	public boolean setSpecial() {
		return false;
	}

	/**
	 * Return the maximum operational range of this type of disruptor.
	 * 
	 * @return Maximum range of this disruptor armed with a standard load.
	 */
	public int getDisruptorRange() {
		return this.disruptorRange;
	}

	/**
	 * Set the maximum operational range of this type of disruptor.
	 * 
	 * @param range The range to cap out this disruptor.
	 */
	public void setDisruptorRange(int range) {
		this.disruptorRange = range;
	}

	@Override
	public int totalArmingTurns() {
		return 1;
	}

	@Override
	public int energyToArm() {
		// Standard disruptors require 2 energy to arm.
		if (armingType == WeaponArmingType.STANDARD) {
			return 2;
			// Overloaded disruptors require 4 energy to arm.
		} else {
			return 4;
		}
	}

	@Override
	public void applyAllocationEnergy(Double energy, WeaponArmingType type) {
		arm(energy.intValue());
	}

	@Override
	public int[] getHitChart() {
		switch (armingType) {
			case STANDARD:
				return hitChart;
			case OVERLOAD:
				return overloadHitChart;
			default:
				return hitChart;
		}
	}

}
