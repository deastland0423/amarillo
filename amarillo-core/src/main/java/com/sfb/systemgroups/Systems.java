package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.Unit;

// Shipboard systems are any group of SSD boxes that perform
// a number of functions (hull, weapons, controll, etc.) and
// are logically grouped together.
public interface Systems {

	/**
	 * Initialization of the systems data.
	 * 
	 * @param values A mapping containing the system name and an object with the
	 *               system data.
	 */
	public void init(Map<String, Object> values);

	/**
	 * Get the total number of SSD boxes for this system group.
	 * 
	 * @return The number of SSD boxes in this group before any damage.
	 */
	public int fetchOriginalTotalBoxes();

	/**
	 * Get the total number of remaining SSD boxes for this system group.
	 * 
	 * @return The number of remaining, undamaged SSD boxes in this group.
	 */
	public int fetchRemainingTotalBoxes();

	/**
	 * Perform end-of-turn housekeeping duties, if any, so the ship will be ready
	 * for the next energy allocation.
	 */
	public void cleanUp();

	/**
	 * Return the unit on which this system is installed.
	 *
	 * @return The owning unit of this system.
	 */
	public Unit fetchOwningUnit();

	/**
	 * The inverse of {@link #init}: this group's MUTABLE state, for a saved game.
	 *
	 * <h2>What belongs in here, and what does not</h2>
	 * A saved game is a scenario plus accumulated damage. Everything a hull IS — how many boxes
	 * it has, what its shields are worth undamaged, which weapons it carries — is rebuilt from
	 * the ship JSON, the refit set, the scenario year and the Commander's Options choices, so
	 * none of that belongs here. What belongs here is only what the BATTLE did: which boxes are
	 * gone, what a shield is down to, what is charged, what has been used this turn.
	 *
	 * <p>Keyed by name into a plain map rather than a typed record per group, so it is symmetric
	 * with {@code init} and serialises as JSON with nothing further to write.
	 *
	 * <h2>Why the default is empty</h2>
	 * There are about thirty of these groups, and converting them all at once would be one
	 * unreviewable change. A group that has not been converted contributes nothing, which would
	 * be a silent hole were it not for the round-trip test: it compares a damaged ship's whole
	 * {@code ShipDto} across a save and reload, so anything the DTO can see and this cannot
	 * export fails the build. Treat a group's absence here as unfinished, never as "nothing to
	 * save".
	 *
	 * @return the mutable state, or an empty map for a group not yet converted
	 */
	default Map<String, Object> exportState() {
		return Map.of();
	}

	/**
	 * Restore what {@link #exportState} captured, onto a group already built from its hull data.
	 *
	 * Must tolerate a map that is missing keys — a save written before a group was converted, or
	 * before a field was added to it, has to keep loading. Read each key defensively and leave
	 * the built-from-data value in place when it is absent.
	 */
	default void applyState(Map<String, Object> state) {
		// Nothing to restore for a group that exports nothing.
	}
}
