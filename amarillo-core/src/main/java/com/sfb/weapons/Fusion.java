package com.sfb.weapons;

import com.sfb.Main;
import com.sfb.constants.Constants;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;
import com.sfb.utilities.DiceRoller;

public class Fusion extends VariableDamageWeapon implements DirectFire, HeavyWeapon {
	
	// The damage chart for this weapon.
	private static final int[][] hitChart = {
		// Range 
		//0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24
		{13,8,6,4,4,4,4,4,4,4, 4, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2}, // Roll 1
		{11,8,5,3,3,3,3,3,3,3, 3, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1}, // Roll 2
		{10,7,4,2,2,2,2,2,2,2, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Roll 3
		{9, 6,3,1,1,1,1,1,1,1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Roll 4
		{8, 5,3,1,1,1,1,1,1,1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, // Roll 5
		{8, 4,2,0,0,0,0,0,0,0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}  // Roll 6
			};
	private static final int[][] overloadHitChart = {
		// Range
		//0  1 2 3 4 5 6 7 8
		{19,12,9,6,6,6,6,6,6}, // Roll 1
		{16,12,7,4,4,4,4,4,4}, // Roll 2
		{15,10,6,3,3,3,3,3,3}, // Roll 3
		{13, 9,4,1,1,1,1,1,1}, // Roll 6
		{12, 7,4,1,1,1,1,1,1}, // Roll 5
		{12, 6,3,0,0,0,0,0,0}  // Roll 4
	};
	
	private static final int[][] suicideOverloadHitChart = {
		// Range
		//0  1  2 3 4 5 6 7 8
		{26,16,12,8,8,8,8,8,8}, // Roll 1
		{22,16,10,6,6,6,6,6,6}, // Roll 2
		{20,14, 8,4,4,4,4,4,4}, // Roll 3
		{18,12, 6,2,2,2,2,2,2}, // Roll 6
		{16,10, 6,2,2,2,2,2,2}, // Roll 5
		{16,8,  4,0,0,0,0,0,0}  // Roll 4
	};

	private WeaponArmingType armingType		= WeaponArmingType.STANDARD;
	private int              armingTurn     = 0;
	private boolean          armed 			= false;						// True if the weapon is armed and ready to fire.
	private boolean          cooldown		= false;						// Weapon must have a cooldown turn between firing turns.


	public Fusion() {
		setDacHitLocaiton("torp");
		setType("Fusion");
		setStandard();
	}
	
	@Override
	public void cleanUp() {
		super.cleanUp();
		// If it is on cooldown and did not fire this turn, deactivate cooldown.
		if (isOnCooldown() && getLastTurnFired() < Main.getTurnTracker().getTurn()) {
			setCooldown(false);
		}
		
		// If the weapon is not armed as STANDARD, it can't be 
		// held and is simply discharged.
		if (!(armingType == WeaponArmingType.STANDARD)) {
			reset();
		}
	}

	@Override
	public boolean setStandard() {
		armingType = WeaponArmingType.STANDARD;
		setMinRange(0);
		setMaxRange(24);
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
	 * Set to suicide overload.  
	 */
	@Override
	public boolean setSpecial() {
		this.armingType = WeaponArmingType.SPECIAL;
		setMinRange(0);
		setMaxRange(8);
		return true;
	}

	/**
	 * Set the fusion to suicide overload mode.
	 * @return True if this is a valid request, false otherwise.
	 */
	public boolean setSuicideOverload() {
		return setSpecial();
	}

	@Override
	public boolean hold(int energy) throws WeaponUnarmedException {

		// Can't hold the weapon if it isn't armed.
		if (!isArmed()) {
			throw new WeaponUnarmedException("Weapon is unamred.");
		}
		
		// Can't hold overloaded (or suicide overloaded) fusions.
		if (this.armingType == WeaponArmingType.OVERLOAD || this.armingType == WeaponArmingType.SPECIAL) {
			return false;
		}
		
		if (energy == 1) {
			return true;
		}
		
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
		armingTurn = 0;
		armed = false;
	}

	@Override
	public boolean arm(int energy) {

		// Can't arm a fusion when on cooldown.
		if (isOnCooldown()) {
			return false;
		}

		// If the weapon is already armed:
		// 1) It can be promoted from standard to overload or suicide
		// 2) It can be promoted from overload to suicide.
		// 3) It can't be promoted from suicide.
		if (isArmed()) {
			switch(armingType) {
			case STANDARD:
				if (energy == 2) {
					setOverload();
				} else if (energy == 5) {
					setSuicideOverload();
				} else {
					return false;
				}
				break;
			case OVERLOAD:
				if (energy == 3) {
					setSuicideOverload();
				} else {
					return false;
				}
				break;
			case SPECIAL:
				return false;
			default:
				break;
			}
		// If the weapon is not armed, arm to the correct
		// level given the energy provided. Otherwise exit with false.
		} else {
			if (energy == 2) {
				setStandard();
			} else if (energy == 4) {
				setOverload();
			} else if (energy == 7) {
				setSuicideOverload();
			} else {
				return false;
			}
		}

		armed = true;
		return true;
	}

	@Override
	public int totalArmingTurns() {
		return 1;
	}

	@Override
	public int energyToArm() {
		if (armingType == WeaponArmingType.STANDARD) {
			return 2;
		} else if (armingType == WeaponArmingType.OVERLOAD) {
			return 4;
		} else {
			return 7;
		}
	}

	@Override
	public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
		// If the weapon isn't armed, it can't be fired.
		if (!isArmed()) {
			throw new WeaponUnarmedException("Weapon is unarmed.");
		}
		
		// Can't fire beyond maximum range.
		if (range > getMaxRange()) {
			throw new TargetOutOfRangeException("Target is out of weapon range.");
		}
		
		// Can't fire twice within a quarter turn
		if (getLastImpulseFired() < Main.getTurnTracker().getImpulse() + (Constants.IMPULSES_PER_TURN / 4)) {
			return -1;
		}
		
		int damage = 0;
		
		// Throw the dice!
		DiceRoller roller = new DiceRoller();
		int roll = roller.rollOneDie();
		
		switch(this.armingType) {
		case STANDARD:
			damage = hitChart[roll - 1][range];
			break;
		case OVERLOAD:
			damage = overloadHitChart[roll - 1][range];
			break;
		case SPECIAL:
			damage = suicideOverloadHitChart[roll - 1][range];
			// This weapon is destroyed after firing in suicide mode.
			damage();
			// TODO: Apply 2 damage to the owning ship
			break;
		default:
			break;
		}
		
		armed = false;
		putOnCooldown();
		reset();

		registerFire();
		return damage;
	}
	
	public boolean isOnCooldown() {
		return cooldown;
	}
	
	public void putOnCooldown() {
		cooldown = true;
	}
	
	private void setCooldown(boolean value) {
		cooldown = value;
	}

	@Override
	public void applyAllocationEnergy(Double energy, WeaponArmingType type) {
		// If energy is null, then just discharge/reset the
		// weapon and leave it idle.
		if (energy == null) {
			reset();
			return;
		}
		
		// If energy is negative, that means to discharge the weapon,
		// and then begin a new arming cycle.
		if (energy < 1) {
			reset();
		}
		
		// Handle the special case where energy is 0
		if (energy == 0) {
			reset();		// For fusions, this is the same as a null value.
			return;
		}
		
		// Otherwise, process the energy for the weapon.
		int energySupplied = Math.abs(energy.intValue());
		
		// If the weapon is armed then the energy
		// can be used to either hold (1 energy) or overload/suicide
		if (isArmed()) {
			try {
				// For 1 energy, the weapon can be held at standard.
				if (energySupplied == 1) {
					hold(energySupplied);
				// For 3 energy, the weapon can be held (1) and overloaded (2).
				} else if (energySupplied == 3) {
					hold(1);
					arm(2);
				// For 6 energy, the weapon can be held (1) and suicided (5)
				} else if (energySupplied == 6) {
					hold(1);
					arm(5);
				}
			} catch (WeaponUnarmedException e) {
				// We check for armed before calling hold(), so 
				// this should never be caught.
			}
		// If not already armed, then arm the weapon.
		} else {
			arm(energySupplied);
		}
		
	}


}
