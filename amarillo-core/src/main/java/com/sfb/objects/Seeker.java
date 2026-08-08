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

	public SeekerType getSeekerType();

	public void setSeekerType(SeekerType type);

	public boolean isSelfGuiding();

	public void setSelfGuiding(boolean selfGuiding);

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
	 * Mark this seeker as identified by an enemy ship.
	 * Identified seekers can be prioritized for point defense.
	 */
	public void identify();

	public boolean isIdentified();
}
