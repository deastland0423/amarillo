package com.sfb.weapons;

import com.sfb.TurnTracker;
import com.sfb.objects.Unit;
import com.sfb.utilities.ArcUtils;

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
	private int     arcs = ArcUtils.FULL;	// Bitmask of the 24 directions (1-24) into which the weapon can fire.
	private boolean functional = true;		// True if the weapon is undamaged, false otherwise.
	private int     lastImpulseFired  = -9;	// The last impulse on which this weapon was fired.
	private int     lastTurnFired     = -1;	// The last turn on which this weapon was fired. -1 = never fired. (used by Fusion)
	private int     maxShotsPerTurn   = 1;	// How many times this weapon may fire per turn (default 1).
	private int     minImpulseGap     = 8;	// Minimum global impulses between shots (default 8).
	private int     shotsThisTurn     = 0;	// Shots fired so far this turn; reset by cleanUp().
	
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
	 * Returns the arc bitmask for this weapon.
	 */
	public int getArcs() {
		return arcs;
	}

	/**
	 * Set the arc bitmask for this weapon (use ArcUtils constants or ArcUtils.mask()).
	 */
	public void setArcs(int arcMask) {
		this.arcs = arcMask;
	}

	/**
	 * Check to see if the weapon can hit a target within the provided arc.
	 *
	 * @param targetArc The 1-based bearing (1-24) to the target.
	 * @return True if the target is within the weapon arcs, false otherwise.
	 */
	public boolean inArc(int targetArc) {
		return ArcUtils.inArc(targetArc, arcs);
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
	 * Two conditions must both be met:
	 *   1. Has not exceeded maxShotsPerTurn this turn.
	 *   2. At least minImpulseGap global impulses since last fired.
	 */
	public double energyToFire() {
		return 1.0;
	}

	public boolean canFire() {
		int currentImpulse = TurnTracker.getImpulse();
		return shotsThisTurn < maxShotsPerTurn
				&& (currentImpulse - lastImpulseFired) >= minImpulseGap;
	}

	/**
	 * Register that this weapon fired on the current impulse and turn.
	 */
	protected void registerFire() {
		lastImpulseFired = TurnTracker.getImpulse();
		lastTurnFired    = TurnTracker.getTurn();
		shotsThisTurn++;
	}

	/**
	 * End of turn cleanup. Resets per-turn shot counter.
	 */
	public void cleanUp() {
		shotsThisTurn = 0;
	}

	public int getMaxShotsPerTurn() {
		return maxShotsPerTurn;
	}

	public void setMaxShotsPerTurn(int maxShotsPerTurn) {
		this.maxShotsPerTurn = maxShotsPerTurn;
	}

	public int getMinImpulseGap() {
		return minImpulseGap;
	}

	public void setMinImpulseGap(int minImpulseGap) {
		this.minImpulseGap = minImpulseGap;
	}

	public int getShotsThisTurn() {
		return shotsThisTurn;
	}
}
