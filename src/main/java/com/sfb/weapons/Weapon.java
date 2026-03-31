package com.sfb.weapons;

import com.sfb.Main;
import com.sfb.TurnTracker;
import com.sfb.constants.Constants;
import com.sfb.objects.Unit;

/**
 * Parent class for all weapons. Contains common functionality shared by weapons of all types.
 * Class is abstract, as you will never instantiate a "Weapon" object; only a Phaser, Disruptor, etc.
 * 
 * @author Daniel Eastland
 *
 */
public abstract class Weapon {
	
	private String  type;					// The type of weapon (Phaser1, Disruptor30, Photon, ESG, etc.)
	private String  designator;				// The unique designator for the weapon (A, B, C...1, 2, 3...etc.)'
	private String  dacHitLocaiton;			// What DAC 'hit' destroys  this weapon //TODO: should this be an enum?
	private int[]   arcs;					// An array of the arcs into which the weapon can fire. All arcs are a number (1 for straight ahead, etc.)
	private boolean functional = true;		// True if the weapon is undamaged, false otherwise.
	private int     lastImpulseFired = -9;	// The last impulse on which this weapon was fired. (Weapons normally can't fire twice within 8 impulses.)
	private int     lastTurnFired;			// The last turn on which this weapon was fired.
	
	private int     maxRange;				// The maximum distance that this weapon can do damage.
	private int     minRange;				// The range below which this weapon can not fire.
	
	private Unit    owningShip;				// The unit on which this weapon is mounted.
	
	/**
	 * Determine what value on the DAC ('torp', 'drone', etc.) will damage this weapon.
	 * 
	 * @return The DAC string that affects this weapon.
	 */
	public String getDacHitLocaiton() {
		return dacHitLocaiton;
	}
	
	/**
	 * Specifies which weapon type on the Damage Allocation Chart
	 * will destroy this weapon.
	 * 
	 * @param dacHitLocaiton A string representing the DAC weapon type.
	 */
	public void setDacHitLocaiton(String dacHitLocaiton) {
		this.dacHitLocaiton = dacHitLocaiton;
	}
	
	/**
	 * Specify into which arcs this weapon may fire.
	 * 
	 * @return An array of ints representing all of the arcs into which this weapon may fire.
	 */
	public int[] getArcs() {
		return arcs;
	}
	
	/**
	 * Set all of the arcs into which this weapon my fire.
	 * 
	 * @param arcs An array of ints representing all of the arcs 
	 * into which this weapon may fire.
	 */
	public void setArcs(int[] arcs) {
		this.arcs = arcs;
	}
	
	/**
	 * Check to see if the weapon can hit a target
	 * within the provided arc.
	 * 
	 * @param targetArc The arc containing the target.
	 * @return True if the target is within the weapon arcs, false otherwise.
	 */
	public boolean inArc(int targetArc) {
		for (int i=0; i < arcs.length; i++) {
			if (targetArc == arcs[i]) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Checks to see if the weapon is undamaged.
	 * @return True if weapon is undamaged, false otherwise.
	 */
	public boolean isFunctional() {
		return functional;
	}
	
	/**
	 * Apply damage to the weapon, rendering it non-functional.
	 */
	public void damage() {
		functional = false;
	}
	
	/**
	 * Repair a damaged weapon, rendering it functional again.
	 */
	public void repair() {
		functional = true;
	}

	/**
	 * Get the name of the weapon (Phaser1, Photon, etc.).
	 * 
	 * @return The name of the weapon
	 */
	public String geDesignator() {
		return designator;
	}

	/**
	 * Set the unique designator for this weapon.
	 * @param designator Simple designator (A, B, C...1, 2, 3)
	 */
	public void setDesignator(String designator) {
		this.designator = designator;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	public String getName() {
		return type + "-" + designator;
	}

	/**
	 * Find out when the weapon last fired.
	 * @return The last impulse this weapon fired.
	 */
	public int getLastImpulseFired() {
		return lastImpulseFired;
	}

	//TODO: Should this be private only?
	public void setLastImpulseFired(int lastImpulseFired) {
		this.lastImpulseFired = lastImpulseFired;
	}
	
	protected void setLastTurnFired(int turn) {
		this.lastTurnFired = turn;
	}
	
	public int getLastTurnFired() {
		return this.lastTurnFired;
	}
	
	protected void setMaxRange(int range) {
		this.maxRange = range;
	}

	protected void setMinRange(int range) {
		this.minRange = range;
	}
	
	public int getMaxRange() {
		return this.maxRange;
	}
	
	public int getMinRange() {
		return this.minRange;
	}

	public Unit fetchOwningShip() {
		return owningShip;
	}

	public void setOwningShip(Unit owningShip) {
		this.owningShip = owningShip;
	}

	/**
	 * Returns true if this weapon is allowed to fire on the current impulse.
	 * A phaser must wait at least WEAPON_FIRE_DELAY (8) global impulses since
	 * it last fired, which also enforces the once-per-turn rule.
	 */
	public boolean canFire() {
		int currentImpulse = TurnTracker.getImpulse();
		return (currentImpulse - lastImpulseFired) >= Constants.WEAPON_FIRE_DELAY;
	}

	/**
	 * Register that this weapon fired on the current impulse and turn.
	 */
	protected void registerFire() {
		setLastImpulseFired(Main.getTurnTracker().getImpulse());
		setLastTurnFired(Main.getTurnTracker().getTurn());
	}
	
	/**
	 * End of turn cleanup.
	 */
	public void cleanUp() {
		
	}
}
