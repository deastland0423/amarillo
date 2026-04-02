package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;

public interface DirectFire {

	/**
	 * Fire the weapon, returning the damage done if a hit,
	 * 0 if a miss, and -1 if the fire request was not legal.
	 *
	 * @param range The true range from the shooter to the target
	 * @return The damage done by the weapon at that range
	 * @throws WeaponUnarmedException
	 * @throws TargetOutOfRangeException
	 */
	public abstract int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException, CapacitorException;

	/**
	 * Fire with scanner adjustment.
	 *
	 * Variable-damage weapons use adjustedRange for everything (scanner makes
	 * the target appear farther away, reducing damage). Hit-or-miss weapons use
	 * adjustedRange for the to-hit roll but realRange for damage.
	 *
	 * The default implementation passes adjustedRange to fire(int), which is
	 * correct for variable-damage weapons. Hit-or-miss weapons override this.
	 *
	 * @param realRange     True hex distance to the target
	 * @param adjustedRange realRange + scanner value of the firing ship
	 */
	default int fire(int realRange, int adjustedRange)
			throws WeaponUnarmedException, TargetOutOfRangeException, CapacitorException {
		return fire(adjustedRange);
	}
}
