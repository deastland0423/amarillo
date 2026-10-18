package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Seeker;


public class SpecialFunctions {

	int[] damageControl				= {0};					// Array of values representing the DamCon track on the SSD.
	int[] scanner					= {0};					// Array of values representing the scanner track on the SSD.
	int[] sensor					= {0};					// Array of values representing the sensor track on the SSD.
	int excessDamage				= 0;					// Base amount of excess damage on the SSD.

	int uim							= 0;					// Ubitron Interface Module (usually only on Klingon ships)
	int derfacs						= 0;					// DERFACS (usually only on Klingon and Lyran ships)

	int availableDamageControl		= 0;					// Pointer to the current DamageControl value. Moves with damage/repair.
	int availableScanner			= 0;					// Pointer to the current scanner value. Moves with damage/repair.
	int availableSensor				= 0;					// Pointer to the current sensor value. Moves with damage/repair.
	int availableExcessDamage		= 0;					// Amount of excess damage remaining.
	
	int availableUim				= 0;					// Number of UIM systems remaining.
	int availableDerfacs			= 0;					// Number of DERFACS systems remaining.
	
	// Control Channels
	double controlModifier			= 1;					// Multiplier for seeker control. Some ships have 2 x sensor rating, others 1/2.
	int controlChannels				= 0;					// Number of total control channels
	int controlUsed					= 0;					// Amount of seeker control currently occupied.
	List<Seeker> controlledSeekers	= new ArrayList<>();	// List of seekers controlled by this ship.
	private int scoutControlBonus = 0;					// +6 from a scout channel controlling seekers (G24.24)
	
	public SpecialFunctions() {}
	
	public void init(Map<String, Object> values) {
		// Damage Control Track
		int[] damageControlValues = values.get("damcon") == null ? new int[] {0} : (int[])values.get("damcon");
		damageControl = new int[damageControlValues.length];
		System.arraycopy(damageControlValues, 0, this.damageControl, 0, damageControlValues.length);
		availableDamageControl = 0;
		
		// Scanners Track
		int[] scannerValues = values.get("scanner") == null ? new int[] {0} : (int[])values.get("scanner");
		scanner = new int[scannerValues.length];
		System.arraycopy(scannerValues, 0, this.scanner, 0, scannerValues.length);
		availableScanner = 0;
		
		// Sensors Track
		int[] sensorValues = values.get("sensor") == null ? new int[] {0} : (int[])values.get("sensor");
		sensor = new int[sensorValues.length];
		System.arraycopy(sensorValues, 0, this.sensor, 0, sensorValues.length);
		availableSensor = 0;

		// Excess Damage
		availableExcessDamage = excessDamage = values.get("excess") == null ? 0 : (Integer)values.get("excess");

		// UIM / DERFACS counts
		availableUim     = uim     = values.get("uim")     == null ? 0 : (Integer) values.get("uim");
		availableDerfacs = derfacs = values.get("derfacs")  instanceof Integer ? (Integer) values.get("derfacs") : (Boolean.TRUE.equals(values.get("derfacs")) ? 1 : 0);

		// Control channels is (sensor rating) * (control modifier)
		controlModifier = values.get("controlmod") == null ? 1 : (Double)values.get("controlmod");
		controlChannels = (int)(sensor[availableSensor] * controlModifier);

	}
	
	public void cleanUp() {
		//TODO: Figure out what needs to happen here.
	}
	
	///// FETCH VALUES /////
	public int getDamageControl() {
		return damageControl[availableDamageControl];
	}
	
	public int getScanner() {
		return scanner[availableScanner];
	}
	
	public int getSensor() {
		return sensor[availableSensor];
	}
	
	public int getOriginalExcessDamage() {
		return excessDamage;
	}
	
	public int getExcessDamage() {
		return availableExcessDamage;
	}
	
	public int getDerfacs() {
		return availableDerfacs;
	}
	
	public boolean hasDerfacs() {
		return availableDerfacs != 0;
	}
	
	public int getUim() {
		return availableUim;
	}

	/**
	 * Strip the UIM entirely, as a pre-Y165 scenario does (D6.5: "available Y165 and later").
	 * <p>
	 * Zeroes the design count as well as the available one, so this is NOT damage - the module
	 * was never fitted, and a repair must not bring back a device that does not exist yet.
	 * Called only from {@code Ship.removeUims}, which clears the UIM objects in the same breath;
	 * {@code Ship.hasUim} reads this count while {@code Ship.getActiveUim} walks that list, so
	 * the two have to be taken away together or they disagree.
	 */
	public void removeUim() {
		uim = 0;
		availableUim = 0;
	}
	
	public boolean hasUim() {
		return availableUim != 0;
	}
	
	public int getControlLimit() {
		return (int)(sensor[availableSensor] * controlModifier) + scoutControlBonus;
	}

	/** +6 while a scout channel controls seekers (G24.24), 0 otherwise. Kept in sync by Ship. */
	public void setScoutControlBonus(int bonus) {
		this.scoutControlBonus = Math.max(0, bonus);
	}

	public int getScoutControlBonus() {
		return scoutControlBonus;
	}

	public int getControlUsed() {
		return controlUsed;
	}

	/** Claim a control channel for a seeker. Returns false if at limit. */
	public boolean acquireControl(Seeker seeker) {
		if (controlUsed >= getControlLimit()) return false;
		controlledSeekers.add(seeker);
		controlUsed++;
		return true;
	}

	/** Claim a control channel unconditionally (may push over limit — caller must queue overflow). */
	public void forceAcquireControl(Seeker seeker) {
		controlledSeekers.add(seeker);
		controlUsed++;
	}

	/** Release a control channel when a seeker is destroyed, impacts, or expires. */
	public void releaseControl(Seeker seeker) {
		if (controlledSeekers.remove(seeker)) {
			controlUsed = Math.max(0, controlUsed - 1);
		}
	}

	/** All seekers currently occupying a control channel on this ship. */
	public List<Seeker> getControlledSeekers() {
		return java.util.Collections.unmodifiableList(controlledSeekers);
	}
	
	
	///// DAMAGE /////

	/** Returns true if the scanner track can still advance (i.e. not fully destroyed). */
	public boolean canDamageScanner() {
		return availableScanner < scanner.length - 1;
	}

	/** Returns true if the sensor track can still advance (i.e. not fully destroyed). */
	public boolean canDamageSensor() {
		return availableSensor < sensor.length - 1;
	}

	public boolean damageScanner() {
		// If we are at the last position in the track, no further damage can be done.
		if (availableScanner == this.scanner.length - 1) {
			return false;
		}
		
		// Move the pointer to the next value in the track.
		availableScanner++;
		return true;
	}
	
	/**
	 * Advance the sensor damage track one step.
	 * Returns true if damage occurred; false if already fully damaged.
	 * If the new limit is below controlUsed, the ship is over its control limit —
	 * callers must call Game.checkControlOverflow() to queue the interrupt.
	 */
	public boolean damageSensor() {
		if (availableSensor == this.sensor.length - 1)
			return false;
		availableSensor++;
		controlChannels = (int)(sensor[availableSensor] * controlModifier);
		return true;
	}
	
	public boolean damageDamCon() {
		// If we are at the last position in the track, no further damage can be done.
		if (availableDamageControl == this.damageControl.length - 1) {
			return false;
		}
		
		// Move the pointer to the next value in the track.
		availableDamageControl++;
		return true;
	}
	
	// Apply excess damage. Return true if ship is still intact.
	// Return false if the ship is destroyed!!!!!
	public boolean damageExcessDamage() {
		if (availableExcessDamage == 0) {
			return false;
		}
		
		availableExcessDamage--;
		return true;
	}
	
	////// REPAIR /////
	public boolean repairDamCon() {
		// If we are at the first position in the track, no repairs can be made.
		if (availableDamageControl == 0) {
			return false;
		}
		
		// Move the pointer to the previous value in the track.
		availableDamageControl--;
		return true;
	}
	
	public boolean repairSensor() {
		// If we are at the first position in the track, no repairs can be made.
		if (availableSensor == 0) {
			return false;
		}

		// Move the pointer to the previous value in the track.
		availableSensor--;
		controlChannels = (int)(sensor[availableSensor] * controlModifier);
		return true;
	}
	
	public boolean repairScanner() {
		// If we are at the first position in the track, no repairs can be made.
		if (availableScanner == 0) {
			return false;
		}
		
		// Move the pointer to the previous value in the track.
		availableScanner--;
		return true;
	}
	
	public boolean repairExcessDamage(int amount) {
		if (availableExcessDamage + amount > excessDamage) {
			return false;
		}
		
		availableExcessDamage += amount;
		return true;
	}
	

	// -------------------------------------------------------------------------
	// Saved game
	// -------------------------------------------------------------------------

	/**
	 * The damage tracks, the spare-system counts, and the seekers this ship is guiding.
	 *
	 * <h2>What this class turned out to be hiding</h2>
	 * The round-trip test named this gap {@code .controlUsed} and blamed ControlSpaces, which
	 * was wrong on both counts: the field lives here, and it was the least of what was missing.
	 * SpecialFunctions was not saved AT ALL, so the SENSOR, SCANNER and DAMAGE CONTROL tracks
	 * were rebuilt from the hull and a reloaded ship came back repaired. Sensors are the worst
	 * of the three — the rating drives EW (D6.3), lock-on and how many seekers the ship can
	 * guide — so a save was handing back the most valuable thing a damaged ship had lost.
	 *
	 * <p>What is saved is the POINTER into each track, not the track: the steps themselves are
	 * the hull's and are rebuilt with it. {@code controlChannels} travels too, because
	 * {@code damageSensor} recomputes it as the track advances.
	 *
	 * <h2>controlUsed is not saved, deliberately</h2>
	 * It is a COUNT of {@code controlledSeekers}, kept in lockstep by acquire and release. Two
	 * records of one fact is what let a weapon's firing history contradict itself, so only the
	 * list travels — as NAMES, resolved by Game.applyState once the seekers exist — and the
	 * count is rebuilt from it. A count restored on its own could outlive the seekers it counted
	 * and leave a ship unable to guide a drone it was no longer flying.
	 */
	public Map<String, Object> exportState() {
		Map<String, Object> state = new java.util.LinkedHashMap<>();
		state.put("availableDamageControl", availableDamageControl);
		state.put("availableScanner", availableScanner);
		state.put("availableSensor", availableSensor);
		state.put("availableExcessDamage", availableExcessDamage);
		state.put("availableUim", availableUim);
		state.put("availableDerfacs", availableDerfacs);
		state.put("controlChannels", controlChannels);
		state.put("scoutControlBonus", scoutControlBonus);

		java.util.List<String> flown = new java.util.ArrayList<>();
		for (Seeker seeker : controlledSeekers)
			if (seeker instanceof com.sfb.objects.Unit unit && unit.getName() != null)
				flown.add(unit.getName());
		state.put("controlledSeekers", flown);
		return state;
	}

	public void applyState(Map<String, Object> state) {
		if (state == null)
			return;
		availableDamageControl = StateIo.integer(state, "availableDamageControl",
				availableDamageControl);
		availableScanner = StateIo.integer(state, "availableScanner", availableScanner);
		availableSensor  = StateIo.integer(state, "availableSensor", availableSensor);
		availableExcessDamage = StateIo.integer(state, "availableExcessDamage",
				availableExcessDamage);
		availableUim     = StateIo.integer(state, "availableUim", availableUim);
		availableDerfacs = StateIo.integer(state, "availableDerfacs", availableDerfacs);
		controlChannels  = StateIo.integer(state, "controlChannels", controlChannels);
		scoutControlBonus = StateIo.integer(state, "scoutControlBonus", scoutControlBonus);
	}

	/**
	 * Put back a control channel a save says was already spent.
	 *
	 * Not {@link #acquireControl}, for the reason a fighter's equivalent is not: that method
	 * ASKS whether the ship may take a seeker now, and a save is a record of a decision already
	 * made under the conditions of its own moment. Re-running the limit check judges it against
	 * the present — including a sensor track the battle has since shot down — and would silently
	 * drop the channels of a ship that is over its limit, when what the rules want there is the
	 * CONTROL_OVERFLOW interrupt that lets the player choose.
	 */
	public void restoreControl(Seeker seeker) {
		if (seeker == null)
			return;
		controlledSeekers.add(seeker);
		controlUsed++;
	}
}
