package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.utilities.ArcUtils;

public class WeaponTest {
	
	@Test
	public void testArc() {
		Weapon wep = getWeapon();
		wep.setArcs(ArcUtils.FA);
		
		// Verify that arc 1 is available.
		assertTrue(wep.inArc(3));
		
		// Verify that arc 8 is not available.
		assertFalse(wep.inArc(8));
	}

	private Weapon getWeapon() {
		Weapon weapon = new Photon();
		
		return weapon;
	}

}
