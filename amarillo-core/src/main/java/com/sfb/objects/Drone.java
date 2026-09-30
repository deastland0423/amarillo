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
	private boolean selfGuiding; // True if the weapon does not need control channels to operate.
	private boolean warpSeeker; // True if the drone uses warp-energy tracking (FD2.56) — TypeVI variants.
	private int endurance; // The number of impulses this weapon will continue to operate.
	private int launchImpulse; // The (absolute) impulse this drone was launched.
	private int warheadDamage; // The damage dealt if the weapon hits its target.
	private double rackSize; // The number of spaces the drone takes up in a rack.
	private int hull; // The hull damage needed to kill the drone.
	private Seeker.SeekerType seekerType; // The type of seeker.
	private boolean identified = false; // True if an enemy ship has identified this seeker.
	private String launcherName; // Name of the ship that originally launched this drone (stable, even when
																// inert).

	public Drone() {
		setTurnMode(TurnMode.Seeker);
		setSizeClass(7);
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
		this.selfGuiding = config.selfGuiding;
		this.warpSeeker = config.warpSeeker;
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
	public boolean isWarpSeeker() {
		return warpSeeker;
	}

	@Override
	public void setWarpSeeker(boolean warpSeeker) {
		this.warpSeeker = warpSeeker;
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

	public String getLauncherName() {
		return launcherName;
	}

	public void setLauncherName(String name) {
		this.launcherName = name;
	}

	@Override
	public void applyTractor(Unit tractoringUnit) {
		setTractoringUnit(tractoringUnit);
		setTractored(true);
	}

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

	/** FD2.54: what a dogfight drone does to a size class 4 or larger target — a ship. */
	public static final int DOGFIGHT_DAMAGE_VS_SHIP = 2;

	/** FD2.54: and to a size class 5 target — a PF, interceptor or GBDP. */
	public static final int DOGFIGHT_DAMAGE_VS_SIZE_5 = 4;

	/**
	 * FD2.54 LIMITED DAMAGE: a dogfight drone's warhead depends on what it hits.
	 * <p>
	 * "Dogfight drones score two points of damage on size class 4 and larger targets (ships,
	 * bases, monsters, asteroids, planets). This is because the tiny warhead is designed to score
	 * a direct hit on a fighter engine instead of damaging the shields of a ship." Four points on
	 * size class 5, and the full eight only on size class 6 and 7 — shuttles, fighters, defence
	 * satellites, mines.
	 * <p>
	 * Size class numbers run the other way from size: class 1 is a dreadnought and class 7 a
	 * shuttle, so "size class 4 and larger" is classes 1 to 4.
	 * <p>
	 * Every other drone ignores its target: a type-I does twelve points to anything.
	 */
	@Override
	public int impact(Unit target) {
		if (target == null || type == null || !type.isDogfightDrone())
			return impact();
		int sizeClass = target.getSizeClass();
		if (sizeClass <= 4)
			return DOGFIGHT_DAMAGE_VS_SHIP;
		if (sizeClass == 5)
			return DOGFIGHT_DAMAGE_VS_SIZE_5;
		return impact();
	}

}
