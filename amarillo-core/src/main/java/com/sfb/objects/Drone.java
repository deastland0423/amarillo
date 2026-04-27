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
	private boolean identified = false; // True if an enemy ship has identified this seeker.
	private String launcherName; // Name of the ship that originally launched this drone (stable, even when inert).

	public Drone() {
		setTurnMode(TurnMode.Seeker);
	}

	public Drone(DroneType type) {
		this();
		// Fallback to TypeI if null is passed
		DroneType config = (type != null) ? type : DroneType.TypeI;

		this.type = config;

		// Unified Direct Assignments
		this.endurance = config.endurance;
		this.speed = config.speed; // Protected in Unit
		this.warheadDamage = config.damage;
		this.rackSize = config.rack;
		this.hull = config.hull;
		this.selfGuiding = config.selfGuiding; // Satisfies the internal state for Seeker
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

	public String getLauncherName()              { return launcherName; }
	public void   setLauncherName(String name)   { this.launcherName = name; }

	@Override
	public void identify() {
		this.identified = true;
	}

	@Override
	public boolean isIdentified() {
		return identified;
	}

	@Override
	public int impact() {
		return this.warheadDamage;
	}

}
