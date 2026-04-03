package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.Main;
import com.sfb.constants.Constants;
import com.sfb.objects.Unit;

public class Shields implements Systems {

	// Shield strength
	private int[] shieldValues = new int[] { 0, 0, 0, 0, 0, 0 }; // The baseline strength of the various shields.
	private int[] currentShieldValues = new int[] { 0, 0, 0, 0, 0, 0 }; // The current strength of the various shields.

	// Shield reinforcement
	private int[] specificReinforcement = new int[] { 0, 0, 0, 0, 0, 0 }; // Reinforcement applied to individual shields.
	private int generalReinforcement = 0; // General reinforcement on all shields.

	// Shield manipulation
	private boolean[] shieldActive; // Indicates what shields are inactive (lowered).
	private int[] impulseShieldToggled; // The last turn a shield was toggled between active/inactive (raised/lowered).

	private Unit owningUnit = null;

	public Shields() {

	}

	public Shields(Unit owner) {
		this.owningUnit = owner;
	}

	/**
	 * Get the strength of a specific shield, including reinforcement (if any)
	 * 
	 * @param shieldNumber The shield to be checked.
	 * @return The value of the shield.
	 */
	public int getShieldStrength(int shieldNumber) {
		return currentShieldValues[shieldNumber - 1] + specificReinforcement[shieldNumber - 1];
	}

	/**
	 * Get the original (maximum) strength of a specific shield.
	 * 
	 * @param shieldNumber The shield to be checked (1-6).
	 * @return The original maximum value of the shield.
	 */
	public int getMaxShieldStrength(int shieldNumber) {
		return shieldValues[shieldNumber - 1];
	}

	// NOTE: Not sure if this is a method that should be exposed.
	/**
	 * Set the value of a current shield to a specified value.
	 * 
	 * @param shieldNumber The shield to be altered.
	 * @param value        The new strength of the shield.
	 * @return True if the the new value is within the original shield strength,
	 *         false otherwise.
	 */
	public boolean setShieldValue(int shieldNumber, int value) {
		if (value > shieldValues[shieldNumber - 1]) {
			return false;
		}

		currentShieldValues[shieldNumber - 1] = value;
		return true;
	}

	@Override
	public void init(Map<String, Object> values) {
		currentShieldValues[0] = shieldValues[0] = values.get("shield1") == null ? 0 : (Integer) values.get("shield1");
		currentShieldValues[1] = shieldValues[1] = values.get("shield2") == null ? 0 : (Integer) values.get("shield2");
		currentShieldValues[2] = shieldValues[2] = values.get("shield3") == null ? 0 : (Integer) values.get("shield3");
		currentShieldValues[3] = shieldValues[3] = values.get("shield4") == null ? 0 : (Integer) values.get("shield4");
		currentShieldValues[4] = shieldValues[4] = values.get("shield5") == null ? 0 : (Integer) values.get("shield5");
		currentShieldValues[5] = shieldValues[5] = values.get("shield6") == null ? 0 : (Integer) values.get("shield6");

		// All shields start active
		shieldActive = new boolean[] { true, true, true, true, true, true };
		// Set turnShieldToggled to -8 so the shields are available for toggling in the
		// first impulse.
		impulseShieldToggled = new int[] { -8, -8, -8, -8, -8, -8 };
	}

	@Override
	public void cleanUp() {
		specificReinforcement = new int[] { 0, 0, 0, 0, 0, 0 };
		generalReinforcement = 0;
	}

	public int[] getShieldValues() {
		return this.currentShieldValues;
	}

	/**
	 * Add reinforcement to the specified shield.
	 * 
	 * @param shieldNumber The shield to be reinforced.
	 * @param amount       The energy added to be added to the shield.
	 * @return True if this is a valid request (the shield has strength > 0), false
	 *         otherwise.
	 */
	public boolean reinforceShield(int shieldNumber, int amount) {
		if (!shieldActive[shieldNumber - 1]) {
			return false;
		}

		if (currentShieldValues[shieldNumber - 1] == 0) {
			return false;
		}

		// Add the new amount to the current reinforcement for that shield.
		int currentReinforcement = specificReinforcement[shieldNumber - 1];
		specificReinforcement[shieldNumber - 1] = currentReinforcement + amount;

		return true;
	}

	/**
	 * Set specific reinforcement values for all shields.
	 * 
	 * @param reinforcementValues
	 */
	public void reinforceAllShields(int[] reinforcementValues) {
		this.specificReinforcement = reinforcementValues;
	}

	/**
	 * Add general reinforcement to all shields.
	 * 
	 * @param amount The amount of reinforcement to add.
	 */
	public void addGeneralRenforcement(int amount) {
		generalReinforcement += amount;
	}

	/**
	 * Clear all general reinforcement.
	 */
	public void clearGeneralReinforcement() {
		generalReinforcement = 0;
	}

	/**
	 * Apply damage to the specified shield, returning any damage that gets through.
	 * 
	 * @param shieldNumber The shield to be damaged.
	 * @param amount       The amount of damage to apply to the shield.
	 * @return Amount of damage remaining, if the shield strength was insufficent to
	 *         stop all damage.
	 */
	public int damageShield(int shieldNumber, int amount) {
		// A lowered shield offers no protection — all damage bleeds through
		if (!shieldActive[shieldNumber - 1]) {
			return amount;
		}

		int remainingDamage = amount;

		// Remove general reinforcement first. If reinforcement eliminates all
		// damage, return 0.
		if (remainingDamage > generalReinforcement) {
			remainingDamage -= generalReinforcement;
			generalReinforcement = 0;
		} else {
			generalReinforcement -= remainingDamage;
			remainingDamage = 0;
		}

		// Remove specific reinforcement. If reinforcement eliminates remaining
		// damage, return 0.
		if (remainingDamage > specificReinforcement[shieldNumber - 1]) {
			remainingDamage -= specificReinforcement[shieldNumber - 1];
			specificReinforcement[shieldNumber - 1] = 0;
		} else {
			specificReinforcement[shieldNumber - 1] -= remainingDamage;
			remainingDamage = 0;
		}

		// Remove shield boxes from the facing shield. If this stops remaining damage,
		// return 0. Otherwise return the remaining damage.
		if (remainingDamage > currentShieldValues[shieldNumber - 1]) {
			remainingDamage -= currentShieldValues[shieldNumber - 1];
			currentShieldValues[shieldNumber - 1] = 0;
		} else {
			currentShieldValues[shieldNumber - 1] -= remainingDamage;
			remainingDamage = 0;
		}

		return remainingDamage;
	}

	// Repair a number of shield boxes. If this would exceed the maximum
	// shield value, return false. Otherwise true.
	/**
	 * Repair a number of boxes on a specified damaged shield.
	 * 
	 * @param shieldNumber The shield to be repaired.
	 * @param amount       The number of shield boxes to be repaired.
	 * @return True if the repair was legal, false otherwise.
	 */
	public boolean repairShield(int shieldNumber, int amount) {
		int currentValue = currentShieldValues[shieldNumber - 1];
		int maxValue = shieldValues[shieldNumber - 1];

		if (currentValue + amount > maxValue) {
			return false;
		}

		currentShieldValues[shieldNumber - 1] += amount;
		return true;
	}

	@Override
	public int fetchOriginalTotalBoxes() {
		int totalCount = 0;
		for (int i = 0; i < shieldValues.length; i++) {
			totalCount += shieldValues[i];
		}

		return totalCount;
	}

	@Override
	public int fetchRemainingTotalBoxes() {
		int totalCount = 0;
		for (int i = 0; i < currentShieldValues.length; i++) {
			totalCount += currentShieldValues[i];
		}

		return totalCount;
	}

	/**
	 * Activate (raise) the specified shield.
	 * This is only possible if the shield is inactive (lowered) and the shield
	 * status hasn't been changed
	 * within 1/4 turn.
	 * 
	 * @param shieldNumber The shield to be raised.
	 * @return
	 */
	public boolean raiseShield(int shieldNumber) {
		if (shieldActive[shieldNumber - 1] == false
				&& ((Main.getTurnTracker().getImpulse()
						- impulseShieldToggled[shieldNumber - 1]) >= (Constants.IMPULSES_PER_TURN / 4))) {
			shieldActive[shieldNumber - 1] = true;
			impulseShieldToggled[shieldNumber - 1] = Main.getTurnTracker().getImpulse();
			return true;
		}
		return false;

	}

	/**
	 * Deactivate (lower) the specified shield.
	 * This is only possible if the shield is active (raised) and the shield status
	 * hasn't been changed within 1/4 turn.
	 * 
	 * @param shieldNumber The shield to be lowered.
	 * @return
	 */
	public boolean lowerShield(int shieldNumber) {
		if (shieldActive[shieldNumber - 1] == true
				&& ((Main.getTurnTracker().getImpulse()
						- impulseShieldToggled[shieldNumber - 1]) >= (Constants.IMPULSES_PER_TURN / 4))) {
			shieldActive[shieldNumber - 1] = false;
			impulseShieldToggled[shieldNumber - 1] = Main.getTurnTracker().getImpulse();
			return true;
		}
		return false;

	}

	/**
	 * Returns true if the specified shield is currently active (raised).
	 *
	 * @param shieldNumber The shield to check (1-6).
	 */
	public boolean isShieldActive(int shieldNumber) {
		return shieldActive[shieldNumber - 1];
	}

	/**
	 * Returns true if a transporter can pass through this shield — i.e. the shield
	 * is either inactive (lowered) or has been reduced to 0 strength.
	 *
	 * @param shieldNumber The shield to check (1-6).
	 */
	public boolean isTransportable(int shieldNumber) {
		return !shieldActive[shieldNumber - 1] || currentShieldValues[shieldNumber - 1] == 0;
	}

	@Override
	public Unit fetchOwningUnit() {
		return this.owningUnit;
	}
}
