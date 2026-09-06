package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;
import com.sfb.utilities.DiceRoller;

/**
 * Photon Torpedo, the primary weapon of the Federation. Photons take two turns
 * to arm and can be overloaded for extra damage
 * or set to proximity for greater accuracy.
 * 
 * @author Daniel Eastland
 *
 */
public class Photon extends HitOrMissWeapon implements DirectFire, HeavyWeapon {

	// Hit Charts are the chance (on a d6) that the weapon will hit at a given
	// range.
	// Array index is the range. For example hitChart[3] will return the chance the
	// weapon will hit at range 3.
	// STANDARD
	private final static int[] hitChart = { 0, 0, 5, 4, 4, 3, 3, 3, 3, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
			1, 1, 1, 1, 1 };
	// OVERLOAD
	private final static int[] overloadHitChart = { 6, 6, 5, 4, 4, 3, 3, 3, 3 };
	// SPECIAL
	private final static int[] proximityHitChart = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
			3, 3, 3, 3, 3, 3, 3, 3 };

	/** Warp energy that must be paid into the tube on each of the two arming turns (E4.21). */
	public static final double STANDARD_PER_TURN = 2.0;
	/** Overload energy a torpedo can take on top of that, i.e. 100% overload (E4.41/E4.411). */
	public static final double MAX_OVERLOAD = 4.0;
	/** Least total energy a torpedo carrying overload energy can be fired with (E4.414). */
	public static final double MIN_OVERLOAD_TOTAL = 4.5;

	private WeaponArmingType armingType = WeaponArmingType.STANDARD; // By default, photons are armed normally.
	private int armingTurn = 0; // Number of turns the weapon has been arming.
	private double armingEnergy = 0; // Amount of total energy stored in the weapon.
	private boolean armed = false; // True if the weapon is armed and ready to fire.
	private boolean held = false; // True if the weapon is in 'hold' mode.

	/**
	 * Constructor for a new Photon object.
	 */
	public Photon() {
		setDacHitLocaiton("torp");
		setType("Photon");
		reset();
	}

	/**
	 * @param range The range to the target of the weapon.
	 * 
	 * @return The amount of damage done by the weapon (0 on a miss).
	 *         Returns -1 if the weapon can not be fired at the target due
	 *         to range or arming restrictions.
	 * 
	 * @throws WeaponUnarmedException
	 * @throws TargetOutOfRangeException
	 */
	@Override
	public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
		// If the weapon isn't armed, it can't fire.
		if (!isArmed()) {
			throw new WeaponUnarmedException("Weapon is unarmed.");
		}

		// E4.414: a torpedo carrying overload energy is an overload for all purposes, and one
		// holding less than 4.5 points cannot be fired at all until more energy goes in.
		if (!isFirableOverload())
			return -1;

		// If the weapon is out of range, it can't fire.
		if (range > getMaxRange() || range < getMinRange()) {
			throw new TargetOutOfRangeException("Target not in weapon range.");
		}

		int damage = 0;
		// Roll to hit.
		DiceRoller diceRoller = new DiceRoller();

		// Based on arming type, calculate damage (0 on a miss).
		int roll = diceRoller.rollOneDie();
		setLastRoll(roll);
		switch (armingType) {
			case STANDARD:
				if (roll <= hitChart[range]) {
					damage = 8;
				}
				break;
			case OVERLOAD:
				// Overloaded photons can't fire at targets above range 8.
				if (roll <= overloadHitChart[range]) {
					damage = (int) (armingEnergy * 2);
				}
				break;
			case SPECIAL:
				if (roll <= proximityHitChart[range]) {
					damage = 4;
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
	 * Fire with scanner adjustment. The to-hit roll uses adjustedRange;
	 * damage is fixed (or energy-based for overload), so realRange is unused here.
	 */
	@Override
	public int fire(int realRange, int adjustedRange)
			throws WeaponUnarmedException, TargetOutOfRangeException, CapacitorException {
		if (!isArmed())
			throw new WeaponUnarmedException("Weapon is unarmed.");

		// E4.414: a torpedo carrying overload energy is an overload for all purposes, and one
		// holding less than 4.5 points cannot be fired at all until more energy goes in.
		if (!isFirableOverload())
			return -1;
		if (realRange > getMaxRange() || realRange < getMinRange())
			throw new TargetOutOfRangeException("Target not in weapon range.");

		int clampedAdj = Math.min(adjustedRange, getHitChart().length - 1);
		int damage = 0;
		DiceRoller diceRoller = new DiceRoller();

		int roll = diceRoller.rollOneDie();
		setLastRoll(roll);
		int adjusted = roll + getEcmShift();
		switch (armingType) {
			case STANDARD:
				if (adjusted <= hitChart[clampedAdj])
					damage = 8;
				break;
			case OVERLOAD:
				int clampedAdjOvl = Math.min(adjustedRange, overloadHitChart.length - 1);
				if (adjusted <= overloadHitChart[clampedAdjOvl])
					damage = (int) (armingEnergy * 2);
				break;
			case SPECIAL:
				int clampedAdjPrx = Math.min(adjustedRange, proximityHitChart.length - 1);
				if (adjusted <= proximityHitChart[clampedAdjPrx])
					damage = 4;
				break;
			default:
				break;
		}

		reset();
		registerFire();
		return damage;
	}

	/**
	 *
	 * @return True if the weapon is armed. False otherwise.
	 */
	@Override
	public boolean isArmed() {
		return armed;
	}

	// Right now I only support integer values of photon overloading in this method.
	// Later I will implement 1/4 point increments of overloading.
	@Override
	public boolean hold(int energy) throws WeaponUnarmedException {
		boolean result = false;

		if (!isArmed()) {
			throw new WeaponUnarmedException("Weapon is not armed.");
		}

		switch (armingType) {
			case STANDARD:
				// For 1 energy, the standard photon is held.
				if (energy == 1) {
					result = true;
					// For more than 1 energy, the photon is held for 1 and then overloaded
					// With whatever excess energy remains.
				} else if (energy > 1) {
					int excessArmingEnergy = energy - 1;
					setOverload();
					armingEnergy += excessArmingEnergy;
					energy = 0; // In case 'case OVERLOAD' executes next.
					result = true;
				}
				break;
			case OVERLOAD:
				if (energy == 2) {
					result = true;
					// If excess energy is put into holding, add
					// it to the total overload torp energy.
					// This allows gradual arming of overloaded photons.
				} else if (energy > 2) {
					int excessArmingEnergy = energy - 2;
					armingEnergy += excessArmingEnergy;
					result = true;
				}
				break;
			case SPECIAL:
				if (energy == 1) {
					result = true;
				}
				break;
			default:
				break;
		}

		held = result;
		return result;
	}

	/**
	 * Arm the tube for one turn (E4.21). Two points of warp energy are mandatory and anything
	 * beyond them is overload energy, which irrevocably commits the torpedo to being an
	 * overload (E4.411, E4.414). A turn therefore accepts at most six points: the mandatory
	 * two plus the four that take it to 100% overload (E4.41). Energy is recorded in
	 * half-point steps, anything short of the next half point counting as the lower level
	 * (E4.414). A torpedo already sitting armed in the tube can still be given overload
	 * energy (E4.411); its holding cost is charged separately and never counts (E4.412).
	 *
	 * @param energy warp energy allocated to this tube this turn
	 * @return true if the energy was accepted
	 */
	public boolean armWithEnergy(double energy) {
		double paid = Math.floor(energy * 2) / 2.0;   // half-point steps, rounded down (E4.414)
		if (paid <= 0)
			return false;

		if (isArmed()) {
			// Overloading a torpedo that is already loaded (E4.411).
			if (armingType == WeaponArmingType.SPECIAL)
				return false;                          // proximity cannot be overloaded (E4.34)
			double room = MAX_OVERLOAD - overloadEnergy();
			double add  = Math.min(paid, room);
			if (add <= 0)
				return false;
			setOverload();
			armingEnergy += add;
			return true;
		}

		if (paid < STANDARD_PER_TURN)
			return false;                              // the two-point charge is mandatory (E4.21)
		if (armingType == WeaponArmingType.SPECIAL && paid > STANDARD_PER_TURN)
			return false;                              // proximity cannot be overloaded (E4.34)

		double overload = Math.min(paid - STANDARD_PER_TURN, MAX_OVERLOAD - overloadEnergy());
		if (overload > 0)
			setOverload();                             // any overload energy commits it (E4.414)
		armingEnergy += STANDARD_PER_TURN + overload;
		armingTurn++;
		if (armingTurn >= 2)
			armed = true;
		return true;
	}

	/** Overload energy in the tube: whatever exceeds the mandatory two per arming turn (E4.411). */
	public double overloadEnergy() {
		return Math.max(0, armingEnergy - STANDARD_PER_TURN * armingTurn);
	}

	/**
	 * Damage an overloaded torpedo scores on its own ship's facing shield when fired at a true
	 * range of zero or one (E4.43, E4.431), read off the E4.413 table by total energy. It is
	 * not subtracted from the warhead (E4.432). Zero for standard and proximity torpedoes,
	 * which cannot be fired that close at all (E4.14).
	 */
	public int feedbackDamage() {
		if (armingType != WeaponArmingType.OVERLOAD || armingEnergy < MIN_OVERLOAD_TOTAL)
			return 0;
		if (armingEnergy <= 5.0) return 1;
		if (armingEnergy <= 6.0) return 2;
		if (armingEnergy <= 7.0) return 3;
		return 4;
	}

	/** True once the tube holds enough to be fired as the overload it is committed to (E4.414). */
	public boolean isFirableOverload() {
		return armingType != WeaponArmingType.OVERLOAD || armingEnergy >= MIN_OVERLOAD_TOTAL;
	}

	@Override
	public boolean arm(int energy) {
		return armWithEnergy(energy);
	}

	@Override
	public WeaponArmingType getArmingType() {
		return armingType;
	}

	@Override
	public boolean setOverload() {
		// Can't switch from PROXIMITY to OVERLOAD. But you can
		// switch from STANDARD to OVERLOAD.
		if (armingType == WeaponArmingType.SPECIAL) {
			return false;
		}

		// Set the arming type.
		armingType = WeaponArmingType.OVERLOAD;
		setMinRange(0);
		setMaxRange(8);
		return true;
	}

	@Override public boolean supportsOverload()   { return true; }
	@Override public boolean supportsProximity()  { return true; }

	/** STANDARD hold costs 1; OVERLOAD hold costs 2; SPECIAL cannot be held (handled by rules). */
	@Override
	public int holdEnergyCost() {
		if (!armed) return 0;
		return armingType == WeaponArmingType.OVERLOAD ? 2 : 1;
	}

	/**
	 * Set the photons to proximity (arming type SPECIAL) which will do half damage
	 * but is
	 * much more likely to hit at long range. Proximity photons can not be used
	 * under range 9.
	 * 
	 * @return True if the weapon is in a valid state to be proximity armed, false
	 *         otherwise.
	 */
	public boolean setProximity() {
		return setSpecial();
	}

	@Override
	public boolean setStandard() {
		// Can't un-overload once arming has started.
		if (armingType == WeaponArmingType.OVERLOAD && armingTurn > 0) {
			return false;
		}
		armingType = WeaponArmingType.STANDARD;
		setMinRange(2);
		setMaxRange(30);
		return true;
	}

	@Override
	public boolean setSpecial() {
		// Can't switch from overload.
		if (armingType == WeaponArmingType.OVERLOAD) {
			return false;
		}
		armingType = WeaponArmingType.SPECIAL;
		setMinRange(9);
		setMaxRange(30);
		return true;
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
	public void reset() {
		armingTurn = 0;
		armingEnergy = 0;
		held = false;
		armed = false;
		setStandard();
	}

	/**
	 * Find out how much energy with which the weapon is currently armed.
	 * 
	 * @return The amount of arming energy in the weapon.
	 */
	public double getArmingEnergy() {
		return this.armingEnergy;
	}

	public void setArmingEnergy(double energy) {
		this.armingEnergy = energy;
	}

	@Override
	public int totalArmingTurns() {
		return 2;
	}

	@Override
	public int energyToArm() {
		int energyRequired = 0;

		switch (armingType) {
			case STANDARD:
				energyRequired = 2;
				break;
			case SPECIAL:
				energyRequired = 2;
				break;
			case OVERLOAD:
				energyRequired = 4; // Later, this may change to reflect advanced Photon arming rules
				break;
			default:
				break;
		}

		return energyRequired;
	}

	public boolean isHeld() {
		return held;
	}

	@Override
	public int[] getHitChart() {
		switch (armingType) {
			case STANDARD:
				return hitChart;
			case OVERLOAD:
				return overloadHitChart;
			case SPECIAL:
				return proximityHitChart;
			default:
				return hitChart;
		}
	}

	@Override
	public void cleanUp() {
		super.cleanUp();
	}

	@Override
	public void applyAllocationEnergy(Double energy, WeaponArmingType type) {
		// If energy is null, then just discharge/reset the
		// weapon and leave it idle.
		if (energy == null) {
			reset();
			return;
		}

		// For photons, zero energy will also disarm the weapon.
		if (energy == 0) {
			reset();
			return;
		}

		// If energy is negative, that means to discharge the weapon,
		// and then begin a new arming cycle.
		if (energy < 1) {
			reset();
		}

		// Otherwise, process the energy for the weapon.
		int energySupplied = Math.abs(energy.intValue());

		// If the weapon is armed, apply any mode switch then hold.
		if (isArmed()) {
			if (type == WeaponArmingType.SPECIAL && armingType != WeaponArmingType.SPECIAL)
				setSpecial();
			else if (type == WeaponArmingType.STANDARD && armingType != WeaponArmingType.STANDARD)
				setStandard();
			try {
				hold(energySupplied);
			} catch (WeaponUnarmedException e) {
				// We check for armed before calling hold(), so
				// this should never be caught.
			}

			// If the weapon is not armed, set it to the desired type and arm it.
		} else {
			switch (type) {
				case OVERLOAD: setOverload(); break;
				case SPECIAL:  setSpecial();  break;
				default:       /* already STANDARD from reset() */ break;
			}
			// The allocated amount decides the strength: two points arms it as a standard
			// torpedo, anything more is overload energy (E4.21/E4.411).
			armWithEnergy(Math.abs(energy));
		}

	}

}
