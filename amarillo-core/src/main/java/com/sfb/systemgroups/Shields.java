package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.constants.Constants;
import com.sfb.objects.Unit;

public class Shields implements Systems {

	// Per-game impulse clock, injected via Ship.attachClock().
	private com.sfb.TurnTracker clock = new com.sfb.TurnTracker();

	public void setClock(com.sfb.TurnTracker clock) {
		this.clock = clock;
	}

	// Shield strength
	/**
	 * How many shields this unit has: 6 for anything that flies, 1 for a small or medium ground
	 * base (P2.73 — "a single shield (covering all arcs) as part of their design").
	 *
	 * <p>The ARRAY stays six long whatever this says, because every other field here is indexed
	 * by shield number and widening them all to serve one case would be a poor trade. What the
	 * count does is tell the damage resolver which shield a hit lands on: with one shield, every
	 * bearing resolves to shield 1 and the other five slots are never addressed.
	 *
	 * <p>NOT the arc the shield covers. That is a property of where the base is sited — 180° on a
	 * planet, 360° centred on a large asteroid, 180° on an asteroid hex side — and belongs to the
	 * deployment rather than to the hull.
	 */
	private int shieldCount = 6;

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
	/** How many shields this unit has — 6 normally, 1 for a small or medium ground base. */
	public int getShieldCount() {
		return shieldCount;
	}

	/** P2.73: one shield covering all arcs, so every hit resolves against shield 1. */
	public boolean hasSingleShield() {
		return shieldCount == 1;
	}

	public int getShieldStrength(int shieldNumber) {
		return currentShieldValues[shieldNumber - 1] + specificReinforcement[shieldNumber - 1];
	}

	/**
	 * Get the base strength of a specific shield, without reinforcement.
	 * Use this for the public view visible to enemy players.
	 *
	 * @param shieldNumber The shield to be checked.
	 * @return The current shield value excluding any reinforcement.
	 */
	public int getBaseShieldStrength(int shieldNumber) {
		return currentShieldValues[shieldNumber - 1];
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
		shieldCount = values.get("shieldCount") == null ? 6 : (Integer) values.get("shieldCount");
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

	public int getGeneralReinforcement() {
		return generalReinforcement;
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
				&& ((clock.getImpulse()
						- impulseShieldToggled[shieldNumber - 1]) >= (Constants.IMPULSES_PER_TURN / 4))) {
			shieldActive[shieldNumber - 1] = true;
			impulseShieldToggled[shieldNumber - 1] = clock.getImpulse();
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
				&& ((clock.getImpulse()
						- impulseShieldToggled[shieldNumber - 1]) >= (Constants.IMPULSES_PER_TURN / 4))) {
			shieldActive[shieldNumber - 1] = false;
			impulseShieldToggled[shieldNumber - 1] = clock.getImpulse();
			return true;
		}
		return false;

	}

	/**
	 * Returns the absolute impulse at which the specified shield was last toggled.
	 * Used to compute how many impulses remain before the shield can be raised
	 * again.
	 */
	public int getImpulseShieldToggled(int shieldNumber) {
		return impulseShieldToggled[shieldNumber - 1];
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

	// -------------------------------------------------------------------------
	// Saved game (see Systems.exportState)
	// -------------------------------------------------------------------------

	/**
	 * What the battle did to the shields.
	 *
	 * {@code shieldValues} — the undamaged baseline — is deliberately NOT here: it comes from the
	 * hull's {@code "shields"} array, and saving it as well would let a save and the ship data
	 * disagree about what a shield is worth. The same goes for {@code shieldCount}, which the
	 * array's LENGTH declares.
	 *
	 * <p>{@code impulseShieldToggled} is included and is easy to mistake for scratch state: it is
	 * the impulse each shield was last raised or lowered on, and C13.3's delay is measured from
	 * it, so dropping it would hand a reloaded ship a free instant raise.
	 */
	@Override
	public Map<String, Object> exportState() {
		Map<String, Object> state = new java.util.LinkedHashMap<>();
		state.put("current", currentShieldValues.clone());
		state.put("specificReinforcement", specificReinforcement.clone());
		state.put("generalReinforcement", generalReinforcement);
		state.put("active", shieldActive == null ? null : shieldActive.clone());
		state.put("impulseToggled", impulseShieldToggled == null
				? null : impulseShieldToggled.clone());
		return state;
	}

	@Override
	public void applyState(Map<String, Object> state) {
		if (state == null)
			return;
		int[] current = StateIo.intArray(state.get("current"));
		if (current != null)
			currentShieldValues = StateIo.sized(current, currentShieldValues.length);
		int[] specific = StateIo.intArray(state.get("specificReinforcement"));
		if (specific != null)
			specificReinforcement = StateIo.sized(specific, specificReinforcement.length);
		if (state.get("generalReinforcement") instanceof Number n)
			generalReinforcement = n.intValue();
		boolean[] active = StateIo.boolArray(state.get("active"));
		if (active != null)
			shieldActive = active;
		int[] toggled = StateIo.intArray(state.get("impulseToggled"));
		if (toggled != null)
			impulseShieldToggled = toggled;
	}
}
