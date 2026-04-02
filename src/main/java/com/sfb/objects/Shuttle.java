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
	
	private Weapons weapons = new Weapons(this);	// The weapons carried by the shuttle.
	
	public Shuttle() {
		setTurnMode(TurnMode.Shuttle);
	}
	
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
	
}
