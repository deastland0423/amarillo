package com.sfb.weapons;

import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;

/**
 * This interface contains the behaviors expected of any SFB weapon
 * that is a heavy weapon (Disruptor, Photon, Hellbore, etc.). Most
 * heavy weapons have multi-turn arming cycles and/or multiple arming
 * modes.
 * 
 * @author Daniel Eastland
 *
 */
public interface HeavyWeapon {

	/**
	 * Set the weapon into its STANDARD arming mode which does
	 * normal damage and can fire out to full range.
	 * @return True if the weapon is in a valid state to be switched to this mode, false otherwise.
	 */
	public boolean setStandard();
	
	/**
	 * Set the weapon into an OVERLOAD state which will do
	 * more damage but can only fire out to range 8.
	 * 
	 * @return True if the weapon is in a valid state to be overloaded, false otherwise.
	 */
	public boolean setOverload();
	
	/**
	 * If the weapon has a special arming type, set it to this status.
	 *  
	 * @return True if the weapon is in a valid state to be switched to this mode, false otherwise.
	 */
	public boolean setSpecial();

	/**
	 *  Hold the weapon in its armed state.
	 * 
	 * @param energy The energy being used to hold the weapon
	 * @return True if the energy is sufficient and the weapon is armed. False otherwise.
	 * @throws WeaponUnarmedException 
	 */
	public boolean hold(int energy) throws WeaponUnarmedException;
	
	/**
	 * Returns the number of turns that the weapon has been arming.
	 */
	public int getArmingTurn();
	
	/**
	 * Check to see if a weapon is ready to fire.
	 * 
	 * @return True if the weapon is completely armed, otherwise false.
	 */
	public boolean isArmed();
	
	/**
	 * Discover what arming type is currently being used by the weapon.
	 * 
	 * @return The current arming type of the weapon.
	 */
	public WeaponArmingType getArmingType();
	
	/**
	 * Empty the tubes and reset all values. This is sometimes 
	 * needed if the weapon is in the wrong arming mode (c.f. OVERLOAD)
	 * and needs to be changed.
	 */
	public void reset();
	
	/**
	 * Arm the weapon by applying arming energy to it. Arming 
	 * success is dependent on the energy provided, the arming type
	 * (STANDARD, OVERLOAD, SPECIAL), and the number of turns
	 * the weapon has already been arming.
	 * 
	 * @param energy The energy dedicated to arming the weapon.
	 * @return True if this is a legal arming request, false otherwise.
	 */
	public boolean arm(int energy);
	
	/**
	 * This will give the minimum energy required for the current arming cycle of this weapon.
	 *
	 * @return The amount of energy required to arm this weapon in its current state.
	 */
	public int energyToArm();

	/**
	 * Returns the total number of turns required to fully arm this weapon in its
	 * current arming mode. Used to display arming progress in the UI.
	 */
	public int totalArmingTurns();
	
	/**
	 * This is where the weapon is affected by energy allocation at the start
	 * of the turn. Weapons can be held, armed, or allowed to discharge.
	 * 
	 * @param energy The amount of energy to put into this weapon. Null means to discharge the
	 * weapon regardless of state. Negative value means to discharge the weapon and then begin 
	 * a new arming cycle.
	 */
	public void applyAllocationEnergy(Double energy, WeaponArmingType type);	
}
