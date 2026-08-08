package com.sfb.weapons;

import com.sfb.objects.Seeker;

public interface Launcher {

	/**
	 * 
	 * @param weaponNumber Which weapon in the launcher ammo rack is to be launched. For large
	 * plasma torpedoes, this will be ignored.
	 * 
	 * @return The seeking weapon that was launched.
	 */
	public Seeker launch(int weaponNumber);

}
