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
	private DroneType type; // The standard drone this one is, or is a modification of.
	private com.sfb.objects.DroneSpec spec; // FD10.2: what it is actually built from.
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
		this(type, null);
	}

	/**
	 * A drone BUILT rather than chosen: one of the standard types, modified (FD10.2).
	 *
	 * <p>FD12.12's example, and the owner's: a type-IV frame carrying three 1/2-PS armor modules
	 * and a 1/2-PS explosive — twelve hull behind a strength-six warhead, where a standard
	 * type-IV is six hull and twenty-four. No {@link DroneType} can say that, because the enum
	 * is the fourteen drones history fielded and this is not one of them.
	 *
	 * <h2>Why a BASIS as well as a spec</h2>
	 * The spec settles everything FD10.2 derives — warhead from the payload modules (FD10.41),
	 * rack footprint and hull from the frame plus its armor (FD12.21), speed from the propulsion
	 * section. It does not settle ENDURANCE, and no rule derives that from frame, propulsion or
	 * payload: FD2.1's chart simply states three turns for a type-I, two for a type-II, 25 for a
	 * type-III and one for a type-VI.
	 *
	 * <p>It happens that frame plus propulsion plus guidance predicts all fourteen correctly —
	 * half-space is 32, type-III guidance is 800, speed-12 is 64, everything else 96 — and
	 * FD10.33 even states the speed-12 case as a rule. Deriving it anyway would be generalising
	 * from a roster to a law the rulebook never writes, which is the line slice 1 drew and this
	 * keeps: the basis supplies what the rules state rather than compute.
	 *
	 * <p>So the drone remains a type-IV for every purpose that asks what KIND it is —
	 * {@link #getDroneType}, FD2.54's dogfight test, the DAC — while {@link #getSpec} says what
	 * it is made of.
	 *
	 * @param type the standard drone this is a modification of; TypeI if null
	 * @param built what it is actually made of, or null to use the type's own composition
	 */
	public Drone(DroneType type, com.sfb.objects.DroneSpec built) {
		this();
		// Fallback to TypeI if null is passed
		DroneType config = (type != null) ? type : DroneType.TypeI;

		this.type = config;
		this.spec = (built != null) ? built : config.spec;

		// Stated by the type, because no rule derives them from the composition.
		this.endurance = this.spec.enduranceFrom(config.endurance);   // FD2.222
		this.warpSeeker = config.warpSeeker;

		// Derived from what the drone is made of (FD10.2).
		this.speed = this.spec.speed(); // Protected in Unit
		this.warheadDamage = this.spec.warheadStrength();
		this.rackSize = this.spec.rackSpaces();
		this.hull = this.spec.hull();
	}

	/**
	 * What this drone is made of (FD10.2) — the same as its type's composition unless it was
	 * built as a modification of one.
	 */
	public com.sfb.objects.DroneSpec getSpec() {
		return this.spec;
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

	/**
	 * Change what kind of drone this is — and with it every number the kind decides.
	 *
	 * <p>This used to assign the type field alone, leaving endurance, speed, warhead, hull and
	 * rack size at their defaults: a drone that answered TypeI to {@link #getDroneType} and
	 * ZERO to everything else. Nothing in production builds a drone that way (both constructors
	 * take the type and derive from it), but two fixtures did, and a zero endurance stayed
	 * invisible while endurance was spent per hex — a speed-0 drone never moved, so it never
	 * spent any. Counting endurance per impulse (FD2.2) made those drones expire the instant
	 * they were looked at, which is how this surfaced.
	 */
	public void setDroneType(DroneType type) {
		DroneType config = (type != null) ? type : DroneType.TypeI;
		this.type = config;
		this.spec = config.spec;
		this.endurance = this.spec.enduranceFrom(config.endurance);
		this.warpSeeker = config.warpSeeker;
		this.speed = this.spec.speed();            // Protected in Unit
		this.warheadDamage = this.spec.warheadStrength();
		this.rackSize = this.spec.rackSpaces();
		this.hull = this.spec.hull();
	}

	/**
	 * F3.42, via what the drone is made of: the type-VI frame, the ATG addition, or the
	 * type-III's own guidance. {@link Seeker#isSelfGuiding} turns this into the question of
	 * whether it is guiding itself at this moment, by asking who holds it.
	 */
	@Override
	public boolean isSelfGuidingCapable() {
		return spec != null && spec.isSelfGuidingCapable();
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


	/** F4.0 ballistic course: a target HEX instead of a target unit. Null when pursuing a unit. */
	private BallisticCourse ballisticCourse;

	@Override
	public BallisticCourse getBallisticCourse() {
		return ballisticCourse;
	}

	@Override
	public void setBallisticCourse(BallisticCourse course) {
		this.ballisticCourse = course;
	}

	// -------------------------------------------------------------------------
	// Saved game (see Seeker.exportState)
	// -------------------------------------------------------------------------

	/** What {@link #exportState} writes under {@link Seeker#SAVE_KIND}. */
	public static final String SAVE_KIND_DRONE = "DRONE";

	/**
	 * A drone in flight, completely enough to rebuild it.
	 *
	 * The {@code type} is the DroneType enum, written by NAME, and it is what carries the drone's
	 * speed, warhead, endurance and rack size — so those are not re-saved. The three that ARE
	 * saved despite coming from the type are {@code endurance}, {@code warheadDamage} and
	 * {@code hull}: FD2.44's reloads, a year's upgrades and a COI loadout can all hand a drone
	 * something other than its type's default, and a drone already in flight keeps what it
	 * launched with.
	 *
	 * <p>{@code launcherName} is a name on purpose and always has been — it is kept so an
	 * orphaned drone can still say where it came from after the launching ship is gone, which is
	 * exactly the property a save needs.
	 */
	@Override
	public java.util.Map<String, Object> exportState() {
		java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
		state.put(Seeker.SAVE_KIND, SAVE_KIND_DRONE);
		state.put("name", getName());
		state.put("droneType", type == null ? null : type.name());

		if (getLocation() != null) {
			state.put("x", getLocation().getX());
			state.put("y", getLocation().getY());
		}
		state.put("facing", getFacing());
		state.put("speed", getSpeed());

		// References, by name. Resolved in pass two of Game.applyState.
		state.put("target", getTarget() == null ? null : getTarget().getName());
		state.put("controller", controller == null ? null : controller.getName());
		state.put("launcherName", launcherName);

		state.put("warpSeeker", warpSeeker);
		state.put("endurance", endurance);
		state.put("launchImpulse", launchImpulse);
		state.put("warheadDamage", warheadDamage);
		state.put("rackSize", rackSize);
		state.put("hull", hull);
		state.put("identified", identified);
		state.put("seekerType", seekerType == null ? null : seekerType.name());
		state.put("ballisticCourse",
				ballisticCourse == null ? null : ballisticCourse.exportState());
		return state;
	}

	/**
	 * Rebuild a drone from a save, with its references already resolved by the caller.
	 *
	 * The type comes first and through the constructor, because it is what gives the drone its
	 * defaults; the saved values then overwrite them. An unknown type name returns null rather
	 * than a drone with no behaviour — a save naming a drone this build does not have is worth
	 * reporting, not worth flying.
	 */
	public static Drone fromState(java.util.Map<String, Object> state, Unit target,
			Unit controller, Unit groundTarget) {
		if (state == null)
			return null;
		String typeName = com.sfb.systemgroups.StateIo.text(state, "droneType");
		DroneType droneType = null;
		if (typeName != null) {
			try {
				droneType = DroneType.valueOf(typeName);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		Drone drone = droneType == null ? new Drone() : new Drone(droneType);

		String name = com.sfb.systemgroups.StateIo.text(state, "name");
		if (name != null)
			drone.setName(name);
		if (state.get("x") instanceof Number x && state.get("y") instanceof Number y)
			drone.setLocation(new com.sfb.properties.Location(x.intValue(), y.intValue()));
		drone.setFacing(com.sfb.systemgroups.StateIo.integer(state, "facing", drone.getFacing()));
		drone.setSpeed(com.sfb.systemgroups.StateIo.integer(state, "speed", drone.getSpeed()));

		drone.setTarget(target);
		drone.setController(controller);
		String launcher = com.sfb.systemgroups.StateIo.text(state, "launcherName");
		if (launcher != null)
			drone.setLauncherName(launcher);

		drone.setWarpSeeker(com.sfb.systemgroups.StateIo.flag(state, "warpSeeker",
				drone.isWarpSeeker()));
		drone.setEndurance(com.sfb.systemgroups.StateIo.integer(state, "endurance",
				drone.getEndurance()));
		drone.setLaunchImpulse(com.sfb.systemgroups.StateIo.integer(state, "launchImpulse",
				drone.getLaunchImpulse()));
		drone.warheadDamage = com.sfb.systemgroups.StateIo.integer(state, "warheadDamage",
				drone.warheadDamage);
		drone.rackSize = com.sfb.systemgroups.StateIo.real(state, "rackSize", drone.rackSize);
		drone.hull = com.sfb.systemgroups.StateIo.integer(state, "hull", drone.hull);
		// Exported but not applied in the first draft, which left every restored drone with a
		// null seeker type — the round-trip test caught it. A Drone built bare does not set it.
		String kindName = com.sfb.systemgroups.StateIo.text(state, "seekerType");
		if (kindName != null) {
			try {
				drone.setSeekerType(Seeker.SeekerType.valueOf(kindName));
			} catch (IllegalArgumentException e) {
				drone.setSeekerType(Seeker.SeekerType.DRONE);
			}
		}
		if (com.sfb.systemgroups.StateIo.flag(state, "identified", false))
			drone.identify();

		drone.setBallisticCourse(BallisticCourse.fromState(
				com.sfb.systemgroups.StateIo.child(state, "ballisticCourse"), groundTarget));
		return drone;
	}
}
