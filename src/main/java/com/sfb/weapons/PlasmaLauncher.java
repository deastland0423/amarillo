package com.sfb.weapons;

import com.sfb.constants.Constants;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.utilities.DiceRoller;

public class PlasmaLauncher extends Weapon implements HeavyWeapon, Launcher, DirectFire {

	/*
	 * Arming chart (std/env/shotgun).
	 * Type F: 1-1-3:0(1) 
	 * Type G: 2-2-3:1        <---- not sure
	 * Type S: 2-2-4:2 (2-2-8)
	 * Type R: 2-2-5:- (2-2-10)
	 * 
	 * 
	 * 
	 * 
	 */


	// Bolt hit chart: index = range, value = max d6 roll that hits
	// Range 0-5: 1-4, Range 6-10: 1-3, Range 11-20: 1-2, Range 21-30: 1-1
	private static final int[] BOLT_HIT_CHART = {
		4, 4, 4, 4, 4, 4,       // range 0-5
		3, 3, 3, 3, 3,           // range 6-10
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, // range 11-20
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1  // range 21-30
	};

	PlasmaType launcherType        = null;		// The type of launcher, which will also be the max size plasma that can be launched.
	PlasmaType plasmaType		= null;			// The type of plasma in the launcher.
	int        armingTurn  = 0;
	WeaponArmingType armingType = null;		// Type of arming: Standard = normal, Overload = enveloping, Special = shotgun
	boolean    armed       = false;
	boolean    rolling = false;			// Set to true if the torpedo has had 3 turns of arming but is not given the final burst of arming energy.
	int[]      launchDirections = new int[] {};	// The initial direction the plasma can face upon launch.
	
	public PlasmaLauncher(PlasmaType launcherType) {
		this.launcherType = launcherType;
		setDacHitLocaiton("torp");
		setType("Plasma");
		setMaxRange(BOLT_HIT_CHART.length - 1); // bolt range 0–30
	}
	
	public PlasmaType getLauncherType() {
		return this.launcherType;
	}

	/** The type of plasma currently loaded (null if unarmed). */
	public PlasmaType getPlasmaType() {
		return this.plasmaType;
	}

	/**
	 * The warhead strength that would be delivered at range 0 if fired now.
	 * Returns 0 if not armed.
	 */
	public int getArmedStrength() {
		if (!armed || plasmaType == null) return 0;
		return new PlasmaTorpedo(plasmaType, armingType != null ? armingType : WeaponArmingType.STANDARD)
				.getCurrentStrength();
	}

	@Override
	public boolean setStandard() {
		this.armingType = WeaponArmingType.STANDARD;
		return true;
	}

	@Override
	public boolean setOverload() {
		this.armingType = WeaponArmingType.OVERLOAD;
		
		return true;
	}

	@Override
	public boolean setSpecial() {
		this.armingType = WeaponArmingType.SPECIAL;
		return true;
	}
	
	/**
	 * Arm the plasma as enveloping, doing double damage
	 * but spreading the damage evenly to all shields.
	 * @return True if the request is valid, false otherwise.
	 */
	public boolean setEnveloping() {
		return setOverload();
	}
	
	/**
	 * Arm the plasma as a shotgun, creating a number of
	 * Plasma-F warheads that must be independently targeted.
	 * 
	 * @param energy
	 * @return
	 * @throws WeaponUnarmedException
	 */
	public boolean setShotgun() {
		return setSpecial();
	}

	@Override
	public boolean hold(int energy) throws WeaponUnarmedException {
		// Can't hold a weapon that is not armed or is not standard armed.
		if (!armed || armingType != WeaponArmingType.STANDARD) {
			return false;
		}
		
		int requiredEnergy = 0;
		
		// Calculate the energy required to hold each type
		// of plasma
		switch(plasmaType) {
		case F:
			if (launcherType == PlasmaType.F) {
				requiredEnergy = 0;
			} else {
				requiredEnergy = 1;
			}
			break;
		case G:
			requiredEnergy = 1;
			break;
		case S:
			requiredEnergy = 2;
			break;
		case R:
			return false;
		default:
			return false;
		}
		
		// Return true if the proper energy was supplied, false otherwise.
		return energy == requiredEnergy;
	}

	@Override
	public int getArmingTurn() {
		return this.armingTurn;
	}

	@Override
	public boolean isArmed() {
		return this.armed;
	}

	@Override
	public WeaponArmingType getArmingType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void reset() {
		armingTurn = 0;
		armed      = false;
		rolling    = false;
		armingType = null;
		plasmaType = null;
		setStandard();
	}

	@Override
	public boolean arm(int energy) {
		boolean okayToArm = false;
		
		switch(launcherType) {
		case F:
			// On the first 2 turns of arming, we only have the option
			// to put in 1 point of energy.
			if (armingTurn < 2) {
				if (energy == Constants.fArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
					plasmaType = PlasmaType.F;
					setStandard();
				}
			// On the 3rd (or subsequent) turn of arming, we can either finish
			// arming or go into "rolling" mode.
			} else {
				if (energy == Constants.fArmingCost[1]) {
					armingTurn++;
					okayToArm = true;
					rolling = false;
					plasmaType = PlasmaType.F;
				} else if (energy == Constants.fArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
					rolling = true;
					plasmaType = PlasmaType.F;
				}
			}
			break;
		case G:
			// On the first turn of arming, the only options are to
			// put in energy for the first turn of a G or an F.
			if (armingTurn == 0) {
				// Arm as a G torpedo
				if (energy == Constants.gArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
					plasmaType = PlasmaType.G;
					setStandard();
				// Arm as an F torpedo
				} else if (energy == Constants.fArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
					plasmaType = PlasmaType.F;
					setStandard();
				}
			// Second turn of arming
			} else if (armingTurn == 1) {
				if (plasmaType == PlasmaType.G && energy == Constants.gArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
				} else if (plasmaType == PlasmaType.F && energy == Constants.fArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
				}
				//TODO: Handle fast-loading later, when you have the rules.
			// Third turn of arming
			} else if (armingTurn >= 2) {
				// Finish as a G
				if (plasmaType == PlasmaType.G && energy == Constants.gArmingCost[1]) {
					armingTurn++;
					okayToArm = true;
					rolling = false;
				// Finish as enveloping G
				} else if (plasmaType == PlasmaType.G && energy == (Constants.gArmingCost[1] * 2)) {
					armingTurn++;
					okayToArm = true;
					rolling = false;
					setEnveloping();
				// Continue rolling G
				} else if (plasmaType == PlasmaType.G && energy == Constants.gArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
					rolling = true;
				// Finish as F
				} else if (plasmaType == PlasmaType.F && energy == Constants.fArmingCost[1]) {
					armingTurn++;
					okayToArm = true;
					rolling = false;
				// Continue rolling F
				} else if (plasmaType == PlasmaType.F && energy == Constants.fArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
					rolling = true;
				}
			}
			break;
		case S:
			if (armingTurn == 0) {
				// Start arming an S (or G)
				if (energy == Constants.sArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
					setStandard();
					plasmaType = PlasmaType.S;
				// Start arming an F
				} else if (energy == Constants.fArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
					setStandard();
					plasmaType = PlasmaType.F;
				}
			} else if (armingTurn == 1) {
				// Continue arming an S (or G)
				if (plasmaType == PlasmaType.S && energy == Constants.sArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
				// Continue arming an F
				} else if (plasmaType == PlasmaType.F && energy == Constants.fArmingCost[0]) {
					armingTurn++;
					okayToArm = true;
				}
			}
			break;
		case R:
			break;
		default:
			break;
		}
		
		// If we have armed for at least 3 turns, are not rolling, and have met energy requirements
		// then we can consider the weapon armed and ready to fire.
		if (armingTurn >= 3 && !rolling && okayToArm) {
			armed = true;
		}
		
		
		
		return okayToArm;
	}

	@Override
	public int energyToArm() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void applyAllocationEnergy(Double energy, WeaponArmingType type) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Fire the plasma as a direct-fire bolt.
	 * Damage = half the table value at this range (rounded down).
	 * Enveloping arming does NOT double damage for bolts.
	 * Returns damage on a hit, 0 on a miss.
	 * Throws TargetOutOfRangeException if the torpedo would do 0 damage at this range.
	 */
	@Override
	public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
		if (!armed) throw new WeaponUnarmedException("Plasma launcher is not armed");
		if (range >= BOLT_HIT_CHART.length) throw new TargetOutOfRangeException("Target out of range for plasma bolt");

		PlasmaTorpedo temp = new PlasmaTorpedo(plasmaType, WeaponArmingType.STANDARD);
		for (int i = 0; i < range; i++) temp.incrementDistance();
		int tableValue = temp.getCurrentStrength();
		int boltDamage = tableValue / 2;

		if (boltDamage <= 0) throw new TargetOutOfRangeException("Plasma bolt has no strength at range " + range);

		int roll = new DiceRoller().rollOneDie();
		int hit  = roll <= BOLT_HIT_CHART[range] ? boltDamage : 0;
		reset();
		return hit;
	}

	/**
	 * Create and return a PlasmaTorpedo ready to be placed on the map.
	 * The launcher must be armed before calling this.
	 * Resets the launcher state after launch.
	 */
	public PlasmaTorpedo launch() {
		if (!armed) return null;
		PlasmaTorpedo torpedo = new PlasmaTorpedo(plasmaType, armingType);
		reset();
		return torpedo;
	}

	@Override
	public Seeker launch(int weaponNumber) {
		return launch();
	}
	
	
}
