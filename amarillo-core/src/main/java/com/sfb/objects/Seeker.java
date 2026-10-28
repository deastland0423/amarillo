package com.sfb.objects;

/**
 * Seeking weapons are units that have particular behaviors and properties. This
 * is the interface to represent that.
 * 
 * @author deastland
 *
 */
public interface Seeker {

	/**
	 * Set the target that the seeker will pursue
	 * 
	 * @param target The unit that the seeker will attempt to impact.
	 */
	public void setTarget(Unit target);

	/**
	 * Get the target of the seeker.
	 * 
	 * @return The unit that is the target of the seeker.
	 */
	public Unit getTarget();

	/**
	 * F4.0: the ballistic course this weapon is flying, or null when it is pursuing a unit in
	 * the ordinary way. A weapon has one or the other, never both — a ballistic weapon's
	 * {@link #getTarget()} is null, which is what makes it uncontrolled (F4.12).
	 */
	public BallisticCourse getBallisticCourse();

	public void setBallisticCourse(BallisticCourse course);

	default boolean isBallistic() { return getBallisticCourse() != null; }

	/**
	 * WHERE THIS WEAPON IS GOING, which since F4.0 is a hex and not necessarily a unit.
	 * <p>
	 * This is the seam the movers steer by. Before ballistic targeting they asked
	 * {@link #getTarget()} and took its location, which meant every mover had to be told
	 * separately that a target can now be a bare hex. They ask this instead: a pursuing
	 * weapon reports its target's current hex, a ballistic one reports the fixed hex it was
	 * aimed at, and the movement code never learns the difference.
	 * <p>
	 * Null means the weapon has nowhere to go — an orphaned drone that lost guidance, or a
	 * target that has left the map — and the movers destroy it. A ballistic weapon can never
	 * report null, which is correct: it needs no guidance and cannot be orphaned (F4.12).
	 */
	default com.sfb.properties.Location destination() {
		BallisticCourse course = getBallisticCourse();
		if (course != null)
			return course.getTargetHex();
		Unit target = getTarget();
		return target == null ? null : target.getLocation();
	}

	public SeekerType getSeekerType();

	public void setSeekerType(SeekerType type);

	/**
	 * F3.42: whether this weapon is CAPABLE of guiding itself if released — a property of
	 * what it is, which nothing that happens in flight can change.
	 *
	 * <blockquote>"Plasma torpedoes (FP4.0), drones with active terminal guidance (FD5.2), and
	 * type-VI drones (FD5.1) are capable of guiding themselves if released from control by the
	 * controlling unit."</blockquote>
	 */
	default boolean isSelfGuidingCapable() { return false; }

	/**
	 * Whether this weapon is guiding itself RIGHT NOW, which is the capability plus the fact
	 * that nobody is holding it.
	 *
	 * <blockquote>F3.421: "A seeking weapon capable of self-guiding is assumed to be under the
	 * control of a guiding unit until control over it has been released. Once control over a
	 * self-guiding seeking weapon is released... the seeking weapon controls itself."</blockquote>
	 *
	 * <p>Derived rather than stored. It was a settable flag, and the two meanings collided: a
	 * drone handed to a consort had its flag cleared to mean "somebody controls it now", which
	 * also erased the ATG it was built with — permanently, since nothing ever set the flag
	 * back. Asking the two questions separately makes that unsayable.
	 */
	/**
	 * Whether this weapon has yet won the lock-on of its OWN that self-guidance runs on.
	 *
	 * <p>True for everything with no acquisition step — a plasma torpedo guides itself from
	 * launch and F3.42 attaches no condition to it. A drone has to earn it: FD5.12 and FD5.21
	 * both put the threshold at eight hexes.
	 */
	default boolean hasOwnLockOn() { return true; }

	/**
	 * Whether this weapon is guiding itself RIGHT NOW: it can, it has a lock-on of its own to
	 * steer by, and nobody is holding it.
	 *
	 * <blockquote>F3.421: "A seeking weapon capable of self-guiding is assumed to be under the
	 * control of a guiding unit until control over it has been released. Once control over a
	 * self-guiding seeking weapon is released... the seeking weapon controls itself."</blockquote>
	 *
	 * <p>Derived rather than stored. It was a settable flag, and the two meanings collided: a
	 * drone handed to a consort had its flag cleared to mean "somebody controls it now", which
	 * also erased the ATG it was built with — permanently, since nothing ever set the flag
	 * back. Asking the questions separately makes that unsayable.
	 *
	 * <p>The middle term is why a type-VI released early is lost rather than free: it is
	 * capable and unheld, but it has nothing of its own to steer by until it closes to eight.
	 */
	default boolean isSelfGuiding() {
		return isSelfGuidingCapable() && hasOwnLockOn() && getController() == null;
	}

	default boolean isWarpSeeker() { return false; }

	default void setWarpSeeker(boolean warpSeeker) {}

	/** Built-in ECCM points carried by the weapon itself (D6.393). Plasma = 3, others = 0. */
	default int getBuiltInEccm() { return 0; }

	public int getEndurance();

	public void setEndurance(int endurance);

	public int getLaunchImpulse();

	public void setLaunchImpulse(int launchImpulse);

	public enum SeekerType {
		PLASMA("Plasma"),
		DRONE("Drone"),
		MISSILE("Missile"),
		SHUTTLE("Shuttle");

		private final String displayName;

		SeekerType(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return displayName;
		}
	}

	/**
	 * Find out the strength of the seeker's warhead
	 * 
	 * @return The amount of damage the seeker will do on impact.
	 */
	public int getWarheadDamage();

	public void setWarheadDamage(int warheadDamage);

	public void setController(Unit controllingUnit);

	public Unit getController();

	/**
	 * The seeker impacts its target, doing damage.
	 *
	 * @return The damage done by the seeker to its target.
	 */
	public int impact();

	/**
	 * The damage this seeker does to a PARTICULAR target, which for most seekers is simply its
	 * warhead — a photon does not care what it hits.
	 * <p>
	 * Overridden where the rules make the warhead depend on the target: FD2.54 scales a dogfight
	 * drone by the target's size class, because "the tiny warhead is designed to score a direct
	 * hit on a fighter engine instead of damaging the shields of a ship".
	 */
	default int impact(com.sfb.objects.Unit target) {
		return impact();
	}

	/**
	 * Mark this seeker as identified by an enemy ship.
	 * Identified seekers can be prioritized for point defense.
	 */
	public void identify();

	public boolean isIdentified();

	// -------------------------------------------------------------------------
	// Saved game (see Game.exportState)
	// -------------------------------------------------------------------------

	/**
	 * This weapon in flight, for a saved game.
	 *
	 * <h2>Why a seeker is harder to save than a ship</h2>
	 * A ship is rebuilt from the scenario and the save only overlays what the battle did to it. A
	 * seeker does not exist until somebody launches it, so there is nothing to overlay: the save
	 * has to describe it completely enough to CREATE it, which is why every implementation writes
	 * its own kind and the type it was built from.
	 *
	 * <p>{@link #SAVE_KIND} tells the loader which class to build. Everything else — target,
	 * controller, ground target — is written as a NAME and resolved in the second pass of
	 * {@code Game.applyState}, once every unit it might point at exists.
	 */
	java.util.Map<String, Object> exportState();

	/** The key under which {@link #exportState} records which class to rebuild. */
	String SAVE_KIND = "kind";
}
