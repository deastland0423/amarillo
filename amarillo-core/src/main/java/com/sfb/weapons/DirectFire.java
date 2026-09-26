package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;

public interface DirectFire {

	/**
	 * Whether this weapon can be fired AT a target on this impulse.
	 *
	 * Almost every direct-fire weapon simply is one, so the answer is yes and the usual
	 * armed/arc/range checks decide the rest. The type-G drone rack is the exception: it is
	 * a launcher that becomes an anti-drone weapon for a turn (FD3.71), so it answers for
	 * itself and answers false whenever it is launching drones instead, out of rounds, or
	 * not a type-G at all.
	 *
	 * Guards that used to read {@code instanceof DirectFire} ask this as well, which is what
	 * lets a rack through without letting every rack through.
	 */
	default boolean canBeFiredAtTarget() {
		return true;
	}


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
