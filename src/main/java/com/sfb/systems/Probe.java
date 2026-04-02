package com.sfb.systems;

import com.sfb.properties.ProbeArmingType;
import com.sfb.utilities.ArcUtils;
import com.sfb.utilities.DiceRoller;
import com.sfb.weapons.DirectFire;
import com.sfb.weapons.Weapon;

public class Probe extends Weapon implements DirectFire {

	private int             ammo = 5;		// Total number of probes per launcher
	private int             availableAmmo;	// Current number of probes remaining.
	private ProbeArmingType armingType;		// Probe armed as INFORMATION or WEAPON
	private int             armingTurn;		// The turn in the arming cycle (takes 2 turns to arm)
	private boolean         armed;			// True if the probe is ready to fire.
	private boolean         functional;		// True if the probe box is not destroyed.
	
	// Create a new probe box with the proper initial values.
	public Probe() {
		availableAmmo = ammo;
		setDacHitLocaiton("probe");
		setToInformation();
		armingTurn = 0;
		functional = true;
		armed = false;
	}
	
	/// OPERATIONS ///
	
	// Set the arming mode of the probe.
	// Also resets arming cycle, assuming you aren't allowed to change mid-stream.
	private void setProbeType(ProbeArmingType armingType) {
		this.armingType = armingType;
		this.armingTurn = 0;
		this.armed = false;
	}
	
	// Performs a turn of arming the weapon.
	// Returns the energy cost to perform the arming.
	// Returns -1 if there is no more ammo.
	public boolean arm(int energy) {
		
		boolean okayToArm= false;

		// If the photon has already had 2 turns of arming,
		// no more arming can be done. Exit with false.
		if (isArmed()) {
			return false;
		}
		
		// If no ammo in the probe, it can't be armed.
		if (isEmpty()) {
			return false;
		}
		
		// Check what the arming type is for the weapon.
		// Apply the energy and increment the arming turn if it matches the profile.
		switch(armingType) {
		case INFORMATION:
			if (energy == 1) {
				armingTurn++;
				okayToArm = true;
			} else {
				okayToArm = false;
			}
			break;
		case WEAPON:
			if (energy == 2) {
				armingTurn++;
				okayToArm = true;
			} else {
				okayToArm = false;
			}
			break;
		default:
			break;
		}
		
		if (okayToArm && armingTurn == 2) {
			armed = true;
		}
		
		return okayToArm;
	}
	
	@Override
	public int fire(int range) {
		int damage = 0;
		
		// Unamred, return error.
		if (!armed) {
			return -1;
		}
		
		// Out of range, return error.
		if (range > 6) {
			return -1;
		}
		
		switch(armingType) {
		// Information probes return 20 points.
		case INFORMATION:
			damage = 20;
			break;
		// On a hit, weapon probes do 8 damage.
		case WEAPON:
			DiceRoller roller = new DiceRoller();
			int roll = roller.rollOneDie();
			if (roll <= range) {
				damage = 8;
			}
			break;
		default:
			break;
		}

		armingTurn = 0;
		armed = false;
		availableAmmo--;
		return damage;
	}
	
	/// GETTERS ////
	
	public int getAvailableAmmo() {
		return this.availableAmmo;
	}
	
	public boolean isEmpty() {
		return (availableAmmo == 0);
	}
	
	public boolean isArmed() {
		return this.armed;
	}
	
	private ProbeArmingType getArmingType() {
		return this.armingType;
	}
	
	public boolean isArmedAsInformation() {
		if (getArmingType() == ProbeArmingType.INFORMATION) {
			return true;
		}
		
		return false;
	}
	
	public boolean isArmedAsWeapon() {
		if (getArmingType() == ProbeArmingType.WEAPON) {
			return true;
		}
		
		return false;
	}
	
	public int getArmingTurn() {
		return this.armingTurn;
	}
	
	public boolean isFunctional() {
		return functional;
	}
	
	public void setToInformation() {
		setProbeType(ProbeArmingType.INFORMATION);
		setArcs(ArcUtils.FULL);
	}
	
	public void setToWeapon() {
		setProbeType(ProbeArmingType.WEAPON);
		setArcs(ArcUtils.of(1));
	}

}
