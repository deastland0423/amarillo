package com.sfb.weapons;

import com.sfb.constants.Constants;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.utilities.DiceRoller;
import com.sfb.TurnTracker;

public class PlasmaLauncher extends Weapon implements HeavyWeapon, Launcher, DirectFire {

	/*
	 * Arming chart (std/env/shotgun).
	 * Type F: 1-1-3:0(1)
	 * Type G: 2-2-3:1 <---- not sure
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
			4, 4, 4, 4, 4, 4, // range 0-5
			3, 3, 3, 3, 3, // range 6-10
			2, 2, 2, 2, 2, 2, 2, 2, 2, 2, // range 11-20
			1, 1, 1, 1, 1, 1, 1, 1, 1, 1 // range 21-30
	};

	PlasmaType launcherType = null; // The type of launcher, which will also be the max size plasma that can be
																	// launched.
	PlasmaType plasmaType = null; // The type of plasma in the launcher.
	int armingTurn = 0;
	WeaponArmingType armingType = null; // Type of arming: Standard = normal, Overload = enveloping, Special = shotgun
	boolean armed = false;
	boolean rolling = false; // Set to true if the torpedo has had 3 turns of arming but is not given the
														// final burst of arming energy.
	int launchDirections = 0; // The initial direction the plasma can face upon launch. Expressed as a bitmask
														// of arc indices (0-5), where 0 = front, 1 = front-right, ..., 5 = front-left.
	boolean pseudoPlasmaReady = true; // Set to true if the launcher is in a state where it can fire a pseudo-plasma
																		// plasma torpedo. A launcher can only fire one pseudo-plasma torpedo per game.
	private int pseudoLastImpulseFired = -9; // Tracks when the pseudo was last fired, to prevent firing both pseudo and
																						// real in the same impulse.

	public PlasmaLauncher(PlasmaType launcherType) {
		this.launcherType = launcherType;
		setDacHitLocaiton("torp");
		setType("Plasma");
		setMaxRange(BOLT_HIT_CHART.length - 1); // bolt range 0–30
	}

	public PlasmaType getLauncherType() {
		return this.launcherType;
	}

	public boolean isPseudoPlasmaReady() {
		return pseudoPlasmaReady;
	}

	/**
	 * Returns true if a pseudo-plasma can be launched this impulse.
	 * Requires: pseudo not yet used this game, and the real torpedo has not
	 * already fired this impulse (can't launch both in the same impulse).
	 */
	public boolean canLaunchPseudo() {
		if (!pseudoPlasmaReady)
			return false;
		int currentImpulse = TurnTracker.getImpulse();
		boolean realFiredThisImpulse = (getLastImpulseFired() == currentImpulse);
		boolean pseudoFiredThisImpulse = (pseudoLastImpulseFired == currentImpulse);
		return !realFiredThisImpulse && !pseudoFiredThisImpulse;
	}

	/**
	 * Launch a pseudo-plasma torpedo. Available at any time regardless of
	 * arming state. Does not affect the arming cycle, the 8-impulse delay,
	 * or shotsThisTurn for the real torpedo. One per game per launcher.
	 * Cannot be launched in the same impulse as a real torpedo.
	 *
	 * @return A PlasmaTorpedo marked as pseudo, or null if not allowed.
	 */
	public PlasmaTorpedo launchPseudo() {
		if (!canLaunchPseudo())
			return null;
		// Pseudo always appears as a standard torpedo matching the launcher type,
		// regardless of what (if anything) is currently arming inside.
		PlasmaTorpedo pseudo = new PlasmaTorpedo(launcherType, WeaponArmingType.STANDARD);
		pseudo.setPseudoPlasma(true);
		pseudoPlasmaReady = false;
		pseudoLastImpulseFired = TurnTracker.getImpulse();
		return pseudo;
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
		if (!armed || plasmaType == null)
			return 0;
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

	public int getLaunchDirections() {
		return this.launchDirections;
	}

	public void setLaunchDirections(int launchDirections) {
		this.launchDirections = launchDirections;
	}

	/**
	 * Arm the plasma as enveloping, doing double damage
	 * but spreading the damage evenly to all shields.
	 * 
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
		switch (plasmaType) {
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
	public void setArmingTurn(int turn) { this.armingTurn = turn; }

	@Override
	public void setArmed(boolean armed) { this.armed = armed; }

	public void setPlasmaType(PlasmaType type)       { this.plasmaType = type; }
	public void setPseudoPlasmaReady(boolean ready)  { this.pseudoPlasmaReady = ready; }

	@Override
	public boolean isArmed() {
		return this.armed;
	}

	/** True if the launcher is in rolling mode (continuous low-energy arming). */
	public boolean isRolling() {
		return this.rolling;
	}

	/** True if this launcher type cannot hold an armed torpedo (Plasma-R). */
	public boolean canHold() {
		return launcherType != PlasmaType.R;
	}

	@Override
	public WeaponArmingType getArmingType() {
		return this.armingType;
	}

	@Override
	public void reset() {
		armingTurn = 0;
		armed = false;
		rolling = false;
		armingType = null;
		plasmaType = null;
		setStandard();
	}

	@Override
	public boolean arm(int energy) {
		boolean okayToArm = false;

		switch (launcherType) {
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
					// TODO: Handle fast-loading later, when you have the rules.
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
					// Start arming an S or downgrade to F
					if (energy == Constants.sArmingCost[0]) {
						armingTurn++;
						okayToArm = true;
						setStandard();
						plasmaType = PlasmaType.S;
					} else if (energy == Constants.fArmingCost[0]) {
						armingTurn++;
						okayToArm = true;
						setStandard();
						plasmaType = PlasmaType.F;
					}
				} else if (armingTurn == 1) {
					// Continue arming S or F
					if (plasmaType == PlasmaType.S && energy == Constants.sArmingCost[0]) {
						armingTurn++;
						okayToArm = true;
					} else if (plasmaType == PlasmaType.F && energy == Constants.fArmingCost[0]) {
						armingTurn++;
						okayToArm = true;
					}
				} else if (armingTurn >= 2) {
					// Final turn: finish, envelop, roll, or downgrade
					if (plasmaType == PlasmaType.S && energy == Constants.sArmingCost[1]) {
						// Finish as standard S
						armingTurn++;
						okayToArm = true;
						rolling = false;
					} else if (plasmaType == PlasmaType.S && energy == Constants.sArmingCost[1] * 2) {
						// Finish as enveloping S (EPT)
						armingTurn++;
						okayToArm = true;
						rolling = false;
						setEnveloping();
					} else if (plasmaType == PlasmaType.S && energy == Constants.sArmingCost[0]) {
						// Continue rolling S
						armingTurn++;
						okayToArm = true;
						rolling = true;
					} else if (plasmaType == PlasmaType.F && energy == Constants.fArmingCost[1]) {
						// Finish as F
						armingTurn++;
						okayToArm = true;
						rolling = false;
					} else if (plasmaType == PlasmaType.F && energy == Constants.fArmingCost[0]) {
						// Continue rolling F
						armingTurn++;
						okayToArm = true;
						rolling = true;
					}
				}
				break;
			case R:
				if (armingTurn == 0) {
					// Start arming R (no downgrade — R is already the largest type)
					if (energy == Constants.rArmingCost[0]) {
						armingTurn++;
						okayToArm = true;
						setStandard();
						plasmaType = PlasmaType.R;
					}
				} else if (armingTurn == 1) {
					if (energy == Constants.rArmingCost[0]) {
						armingTurn++;
						okayToArm = true;
					}
				} else if (armingTurn >= 2) {
					if (energy == Constants.rArmingCost[1]) {
						// Finish as standard R
						armingTurn++;
						okayToArm = true;
						rolling = false;
					} else if (energy == Constants.rArmingCost[1] * 2) {
						// Finish as enveloping R (EPT)
						armingTurn++;
						okayToArm = true;
						rolling = false;
						setEnveloping();
					} else if (energy == Constants.rArmingCost[0]) {
						// Continue rolling R
						armingTurn++;
						okayToArm = true;
						rolling = true;
					}
				}
				break;
			default:
				break;
		}

		// If we have armed for at least 3 turns, are not rolling, and have met energy
		// requirements
		// then we can consider the weapon armed and ready to fire.
		if (armingTurn >= 3 && !rolling && okayToArm) {
			armed = true;
		}

		return okayToArm;
	}

	@Override
	public int energyToArm() {
		if (armed) {
			// Hold cost per type (R cannot hold — return 0 as sentinel)
			switch (launcherType) {
				case S: return Constants.sArmingCost[2]; // 2
				case G: return Constants.gArmingCost[2]; // 1
				case F: return Constants.fArmingCost[2]; // 0
				default: return 0; // R
			}
		}
		if (armingTurn < 2) {
			// Turns 1 and 2 cost the same for all types
			switch (launcherType) {
				case R:
				case S: return Constants.sArmingCost[0]; // 2
				case G: return Constants.gArmingCost[0]; // 2
				default: return Constants.fArmingCost[0]; // 1 (F)
			}
		}
		// Final (3rd+) turn standard completion cost
		switch (launcherType) {
			case R: return Constants.rArmingCost[1]; // 5
			case S: return Constants.sArmingCost[1]; // 4
			case G: return Constants.gArmingCost[1]; // 3
			default: return Constants.fArmingCost[1]; // 3 (F)
		}
	}

	/** True if this launcher can fire an Enveloping Plasma Torpedo (G, S, R; not F). */
	public boolean canEpt() {
		return launcherType != PlasmaType.F;
	}

	/** Energy cost to fire as EPT on the final arming turn (double the standard turn-3 cost). */
	public int eptCost() {
		switch (launcherType) {
			case R: return Constants.rArmingCost[1] * 2; // 10
			case S: return Constants.sArmingCost[1] * 2; // 8
			case G: return Constants.gArmingCost[1] * 2; // 6
			default: return 0; // F cannot EPT
		}
	}

	@Override
	public int totalArmingTurns() {
		return 3;
	}

	/**
	 * Energy cost to continue rolling this turn (paid when already rolling).
	 * This is the lower per-turn cost, not the final burst needed to fully arm.
	 */
	public int rollingCost() {
		if (plasmaType == PlasmaType.G)
			return Constants.gArmingCost[0];
		return Constants.fArmingCost[0]; // F, and F-within-G launcher
	}

	@Override
	public void applyAllocationEnergy(Double energy, WeaponArmingType type) {
		if (energy == null || energy <= 0) {
			reset();
			return;
		}
		if (armed) {
			// Already armed — attempt to hold; discharge if hold fails
			try {
				if (!hold(energy.intValue()))
					reset();
			} catch (com.sfb.exceptions.WeaponUnarmedException ex) {
				reset();
			}
			return;
		}
		if (type != null) {
			switch (type) {
				case OVERLOAD:
					setEnveloping();
					break;
				case STANDARD:
					setStandard();
					break;
				default:
					break;
			}
		}
		arm(energy.intValue());
	}

	/**
	 * Fire the plasma as a direct-fire bolt.
	 * Damage = half the table value at this range (rounded down).
	 * Enveloping arming does NOT double damage for bolts.
	 * Returns damage on a hit, 0 on a miss.
	 * Throws TargetOutOfRangeException if the torpedo would do 0 damage at this
	 * range.
	 */
	@Override
	public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
		if (!armed)
			throw new WeaponUnarmedException("Plasma launcher is not armed");
		if (pseudoLastImpulseFired == TurnTracker.getImpulse())
			throw new WeaponUnarmedException("Cannot fire real plasma and pseudo-plasma in the same impulse");
		if (range >= BOLT_HIT_CHART.length)
			throw new TargetOutOfRangeException("Target out of range for plasma bolt");

		PlasmaTorpedo temp = new PlasmaTorpedo(plasmaType, WeaponArmingType.STANDARD);
		for (int i = 0; i < range; i++)
			temp.incrementDistance();
		int tableValue = temp.getCurrentStrength();
		int boltDamage = tableValue / 2;

		if (boltDamage <= 0)
			throw new TargetOutOfRangeException("Plasma bolt has no strength at range " + range);

		int roll = new DiceRoller().rollOneDie();
		int hit = roll <= BOLT_HIT_CHART[range] ? boltDamage : 0;
		registerFire();
		reset();
		return hit;
	}

	/**
	 * Create and return a PlasmaTorpedo ready to be placed on the map.
	 * The launcher must be armed before calling this.
	 * Resets the launcher state after launch.
	 */
	public PlasmaTorpedo launch() {
		if (!armed)
			return null;
		if (pseudoLastImpulseFired == TurnTracker.getImpulse())
			return null;
		PlasmaTorpedo torpedo = new PlasmaTorpedo(plasmaType, armingType);
		reset();
		return torpedo;
	}

	@Override
	public Seeker launch(int weaponNumber) {
		return launch();
	}

}
