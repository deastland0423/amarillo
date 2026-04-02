package com.sfb.systems;

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
	
	public boolean hasUim() {
		return availableUim != 0;
	}
	
	public int getControlLimit() {
		return (int)(sensor[availableSensor] * controlModifier);
	}

	public int getControlUsed() {
		return controlUsed;
	}

	/** Claim a control channel for a drone. Returns false if at limit. */
	public boolean acquireControl(Seeker seeker) {
		if (controlUsed >= getControlLimit()) return false;
		controlledSeekers.add(seeker);
		controlUsed++;
		return true;
	}

	/** Release a control channel when a drone is destroyed, impacts, or expires. */
	public void releaseControl(Seeker seeker) {
		if (controlledSeekers.remove(seeker)) {
			controlUsed = Math.max(0, controlUsed - 1);
		}
	}
	
	
	///// DAMAGE /////
	public boolean damageScanner() {
		// If we are at the last position in the track, no further damage can be done.
		if (availableScanner == this.scanner.length - 1) {
			return false;
		}
		
		// Move the pointer to the next value in the track.
		availableScanner++;
		return true;
	}
	
	public boolean damageSensor() {
		// If we are at the last position in the track, no further damage can be done.
		if (availableSensor == this.sensor.length - 1) {
			return false;
		}
		
		// Move the pointer to the next value in the track.
		availableSensor++;

		// Adjust control channels to reflect the new (lower) sensor rating.
		controlChannels = (int)(sensor[availableSensor] * controlModifier);

		// Release excess seekers if the limit dropped below current usage.
		while (controlUsed > controlChannels && !controlledSeekers.isEmpty()) {
			Seeker released = controlledSeekers.remove(controlledSeekers.size() - 1);
			controlUsed--;
			released.setSelfGuiding(true); // Drone is now free-flying, no longer guided
		}

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
	
}
