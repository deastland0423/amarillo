package com.sfb.objects;

import com.sfb.properties.TurnMode;
import com.sfb.systemgroups.Weapons;


/**
 * This object represents an base shuttle.
 * @author Daniel Eastland
 *
 */
public abstract class Shuttle extends Unit {

	private int maxSpeed;		// The maximum speed this shuttle can go
	private int hull;			// The maximum hull value of the shuttle

	private int currentSpeed;	// The speed the shuttle is currently travelling
	private int currentHull;	// The number of undamaged hull remaining.
	private boolean crippled = false;

	private String parentShipName; // Name of the ship that launched this shuttle (set at launch time)

	// Absolute impulse (TurnTracker.getImpulse()) when this shuttle was launched onto the map.
	// Default -999 so elapsed is always huge for in-bay shuttles (they always pass any readiness check).
	private int launchImpulse = -999;
	
	private Weapons weapons = new Weapons(this);	// The weapons carried by the shuttle.
	
	public Shuttle() {
		setTurnMode(TurnMode.Shuttle);
	}

	/**
	 * Returns true if this shuttle is controlled by the player (manual movement).
	 * Auto-drifting objects (e.g. released ScatterPack) override this to return false.
	 */
	public boolean isPlayerControlled() { return true; }

	// -------------------------------------------------------------------------
	// Pre-game conversion eligibility (COI special shuttle prep)
	// -------------------------------------------------------------------------

	/** True if this shuttle can be converted to a suicide shuttle before game start. */
	public boolean canBecomeSuicide()     { return false; }

	/** True if this shuttle can be converted to a scatter pack before game start. */
	public boolean canBecomeScatterPack() { return false; }

	/** True if this shuttle can be converted to a wild weasel before game start. */
	public boolean canBecomeWildWeasel()  { return false; }

	public int getMaxSpeed() {
		return maxSpeed;
	}
	
	public void setMaxSpeed(int maxSpeed) {
		this.maxSpeed = maxSpeed;
	}
	
	public int getCurrentSpeed() {
		return currentSpeed;
	}
	
	public void setCurrentSpeed(int currentSpeed) {
		this.currentSpeed = currentSpeed;
	}
	
	public int getHull() {
		return hull;
	}
	
	public void setHull(int maxHull) {
		this.hull = maxHull;
		if (this.currentHull == 0) this.currentHull = maxHull;
	}
	
	public int getCurrentHull() {
		return currentHull;
	}
	
	public void setCurrentHull(int currentHull) {
		this.currentHull = currentHull;
	}
	
	public Weapons getWeapons() {
		return this.weapons;
	}

	public boolean isCrippled() { return crippled; }

	/**
	 * Apply J1.331 speed reduction. Subclasses override to also apply J1.332 weapon effects.
	 * Returns a log line describing what changed, or null if already crippled.
	 */
	public String applyCripplingEffects() {
		if (crippled) return null;
		crippled = true;
		int crippledMax = (int) Math.ceil(maxSpeed / 2.0);
		StringBuilder sb = new StringBuilder(getName() + " CRIPPLED");
		sb.append(" — max speed reduced to ").append(crippledMax);
		if (currentSpeed > crippledMax) {
			currentSpeed = crippledMax;
			setSpeed(crippledMax);
			sb.append(", speed reduced to ").append(crippledMax);
		}
		return sb.toString();
	}

	public String getParentShipName() { return parentShipName; }
	public void   setParentShipName(String name) { this.parentShipName = name; }

	public int  getLaunchImpulse()            { return launchImpulse; }
	public void setLaunchImpulse(int impulse) { this.launchImpulse = impulse; }

	/** True if enough impulses have elapsed since launch to fire direct-fire weapons (8 impulses). */
	public boolean canFireDirect(int currentImpulse) {
		return (currentImpulse - launchImpulse) >= 8;
	}

	/**
	 * True if enough impulses have elapsed since launch to launch seekers (16 impulses).
	 * ScatterPacks are exempt — they ARE the seeker payload.
	 */
	public boolean canLaunchSeeker(int currentImpulse) {
		if (this instanceof ScatterPack) return true;
		return (currentImpulse - launchImpulse) >= 16;
	}

}
