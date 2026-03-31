package com.sfb.objects;

import com.sfb.properties.TurnMode;

/**
 * A drone is a seeking weapon that is essentially a missile. It consists of a
 * warhead and an engine.
 * 
 * @author Daniel Eastland
 * @version 1.0
 */
public class Drone extends Unit implements Seeker {

	private Unit target; // The target of the drone.
	private Unit controller; // The ship controlling this drone.
	private DroneType type; // The type of drone. This determines the drone's properties and behavior.
	private boolean selfGuiding; // True if the weapon does not need control channels to operate, false
																// otherwise.
	private int endurance; // The number of impulses this weapon will continue to operate.
	private int launchImpulse; // The (absolute) impulse this drone was launched.
	private int warheadDamage; // The damage dealt if the weapon hits its target.
	private double rackSize; // The number of spaces the drone takes up in a rack.
	private int hull; // The hull damage needed to kill the drone.
	private Seeker.SeekerType seekerType; // The type of seeker.

	public Drone() {
		setTurnMode(TurnMode.Seeker);
	}

	public Drone(DroneType type) {
		this();
		this.type = type;

		switch (type) {
			case TypeI:
				this.endurance = 96;
				this.setSpeed(8);
				this.warheadDamage = 12;
				this.rackSize = 1;
				this.hull = 4;
				break;
			case TypeII:
				this.endurance = 64;
				this.setSpeed(12);
				this.warheadDamage = 12;
				this.rackSize = 1;
				this.hull = 4;
				break;
			case TypeIII:
				this.endurance = 800;
				this.setSpeed(12);
				this.warheadDamage = 12;
				this.rackSize = 3;
				this.selfGuiding = true;
				this.hull = 4;
				break;
			case TypeIV:
				this.endurance = 96;
				this.setSpeed(8);
				this.warheadDamage = 24;
				this.rackSize = 2;
				this.hull = 6;
				break;
			case TypeV:
				this.endurance = 64;
				this.setSpeed(12);
				this.warheadDamage = 24;
				this.rackSize = 2;
				this.hull = 6;
				break;
			case TypeVI:
				this.endurance = 32;
				this.setSpeed(12);
				this.warheadDamage = 8;
				this.rackSize = 0.5;
				this.hull = 3;
				break;
			default:
				// Default to Type I if no type is specified.
				this.endurance = 96;
				this.setSpeed(8);
				this.warheadDamage = 12;
				this.rackSize = 1;
				this.hull = 4;
				break;
		}
	}

	public enum DroneType {
		TypeI("Type I"),
		TypeII("Type II"),
		TypeIII("Type III"),
		TypeIV("Type IV"),
		TypeV("Type V"),
		TypeVI("Type VI");

		private final String displayName;

		DroneType(String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}

	public void setTarget(Unit target) {
		this.target = target;
	}

	public Unit getTarget() {
		return this.target;
	}

	public void setController(Unit controllingUnit) {
		this.controller = controllingUnit;
	}

	public Unit getController() {
		return this.controller;
	}

	public double getRackSize() {
		return rackSize;
	}

	public void setRackSize(double rackSize) {
		this.rackSize = rackSize;
	}

	public int getHull() {
		return hull;
	}

	public void setHull(int hull) {
		this.hull = hull;
	}

	public DroneType getDroneType() {
		return type;
	}

	public void setDroneType(DroneType type) {
		this.type = type;
	}

	@Override
	public boolean isSelfGuiding() {
		return selfGuiding;
	}

	@Override
	public void setSelfGuiding(boolean selfGuiding) {
		this.selfGuiding = selfGuiding;
	}

	@Override
	public int getEndurance() {
		return endurance;
	}

	@Override
	public void setEndurance(int endurance) {
		this.endurance = endurance;
	}

	@Override
	public int getLaunchImpulse() {
		return this.launchImpulse;
	}

	@Override
	public void setLaunchImpulse(int launchImpulse) {
		this.launchImpulse = launchImpulse;
	}

	@Override
	public int getWarheadDamage() {
		return this.warheadDamage;
	}

	@Override
	public void setWarheadDamage(int warheadDamage) {
		this.warheadDamage = warheadDamage;
	}

	@Override
	public Seeker.SeekerType getSeekerType() {
		return this.seekerType;
	}

	@Override
	public void setSeekerType(Seeker.SeekerType seekerType) {
		this.seekerType = seekerType;
	}

	@Override
	public int impact() {
		// TODO Work out what happens here. We will need to destroy this object.

		return this.warheadDamage;
	}

}
