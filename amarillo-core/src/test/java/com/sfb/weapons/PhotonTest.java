package com.sfb.weapons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;
import com.sfb.utilities.ArcUtils;

public class PhotonTest {

	@Test
	public void testArming() {
		// Create new photon object
		Photon photon = new Photon();
		
		// Verify that it's initial arming turn is 0.
		assertEquals(photon.getArmingTurn(), 0);
		
		// Set armingType to STANDARD
		assertTrue(photon.setStandard());

		// Verify arming type is STANDARD
		assertEquals(photon.getArmingType(), WeaponArmingType.STANDARD);

		// First round of arming, with proper energy
		assertTrue(photon.arm(2));
		
		// Second round of arming, improper energy.
		assertFalse(photon.arm(1));
		
		// Second round of arming, proper energy.
		assertTrue(photon.arm(2));
		
		// Verify that the weapon is armed.
		assertTrue(photon.isArmed());
		
		// Try to arm an already armed weapon.
		assertTrue(photon.arm(2));
		
		// Set arming to OVERLOAD, this is legal
		assertTrue(photon.setOverload());
		
		// Set arming to PROXIMITY, this is NOT legal.
		assertFalse(photon.setProximity());
		
		// Verify that the photons have been armed with 4 points of energy.
		assertEquals(photon.getArmingEnergy(), 6, 0.24);
		
		// Reset the photon
		photon.reset();
		
		// Verify that default values are in place.
		assertEquals(photon.getArmingTurn(), 0);
		assertFalse(photon.isArmed());
		assertEquals(photon.getArmingType(), WeaponArmingType.STANDARD);
	}
	
	@Test
	public void testFiring() throws WeaponUnarmedException, TargetOutOfRangeException {
		Photon photon = getStandardPhoton();
		int range = 30;
        int damage = photon.fire(range);
		
        // Verify that the photon either misses (0 damage)
        // or hits (8 damage).
        assertTrue(damage == 0 || damage == 8);
		
		photon = getOverloadPhoton();
		range = 9;
		
		// Weapon can't fire beyond range 8
		try {
			photon.fire(range);
		} catch (TargetOutOfRangeException e) {
			assertEquals(e.getMessage(), "Target not in weapon range.");
		}

		range = 4;
		
		damage = photon.fire(range);

		assertTrue(damage == 0 || damage == 16);

		try {
			photon.fire(range);
		} catch (WeaponUnarmedException e) {
			assertEquals(e.getMessage(), "Weapon is unarmed.");
		}
		
		photon = getOverloadPhoton();

		// Verify that the photon either misses (0 damage)
		// or hits (16 damage)
		damage = photon.fire(range);
		assertTrue(damage == 0 || damage == 16);
		
		// Try to fire a proximity torpedo at
		// short range (illegal action)
		photon = getProximityPhoton();
		range = 4;

		// Prox torps can't fire under range 9
		try {
			photon.fire(range);
		} catch (TargetOutOfRangeException e) {
			assertEquals(e.getMessage(), "Target not in weapon range.");
		}
		
		// Fire proximity torpedo at long range.
		range = 30;
		damage = photon.fire(range);
		// Verify that the photon either misses (0 damage)
		// or hits (4 damage)
		assertTrue(damage == 4 || damage == 0);
		System.out.println("Damage: " + damage);
		
		
	}
	
	@Test
	public void testArcs() {
		Photon photon = getStandardPhoton();
		
		photon.setArcs(ArcUtils.FA);
		
		System.out.println("InArc: " + photon.inArc(7));
		
	}
	
	
	// Helpers to create test objects.s
	
	private Photon getStandardPhoton() {
		Photon photon = new Photon();
		photon.setStandard();
		photon.arm(2);
		photon.arm(2);
		
		return photon;
	}

	private Photon getProximityPhoton() {
		Photon photon = new Photon();
		photon.setProximity();
		photon.arm(2);
		photon.arm(2);
		
		return photon;
	}

	private Photon getOverloadPhoton() {
		Photon photon = new Photon();
		photon.setOverload();
		photon.arm(4);
		photon.arm(4);
		
		return photon;
	}


}
