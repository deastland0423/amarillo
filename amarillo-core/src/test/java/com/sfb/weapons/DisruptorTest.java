package com.sfb.weapons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;

public class DisruptorTest {

	@Test
	public void testArming() throws WeaponUnarmedException, TargetOutOfRangeException {
		// Get an unarmed disruptor.
		Disruptor disruptor = getUnarmedDisruptor();
		
		// Verify that it is unarmed
		assertFalse(disruptor.isArmed());
		
		// verify that STANDARD disruptor takes 2 energy to arm.
		assertEquals(disruptor.energyToArm(), 2);
		
		// Try to arm with the wrong energy
		assertFalse(disruptor.arm(3));
		
		// Arm the disruptor.
		assertTrue(disruptor.arm(2));
		int range = 12;
		
		// Verify that the disruptor is armed
		assertTrue(disruptor.isArmed());
		
		// Fire the disruptor, this should reneder it unarmed.
		disruptor.fire(range);

		// Verify that the disruptor is unarmed.
		assertFalse(disruptor.isArmed());

		// Arm the disruptor again.
		assertTrue(disruptor.arm(2));
		
		// Check that it is armed
		assertTrue(disruptor.isArmed());
		
		// Check that it is STANDARD
		assertEquals(WeaponArmingType.STANDARD, disruptor.getArmingType());
		
		// Arm it again, with 2 more points of energy.
		assertTrue(disruptor.arm(2));
		
		// Verify that the extra 2 energy overloaded it.
		assertEquals(WeaponArmingType.OVERLOAD, disruptor.getArmingType());
		
	}
	
	@Test
	public void testFiring() throws WeaponUnarmedException, TargetOutOfRangeException {
		
		// Get an armed standard disruptor.
		Disruptor standardDis = getStandardDisruptor();
		int range = 30;
		
		// Fire the disruptor
		int damage = standardDis.fire(range);
		
		// Damage will be 2 at range 30 (or 0 on a miss)
		assertTrue(damage == 2 || damage == 0);
		
		// Try to fire the disruptor again (while it is unarmed)
		try {
			damage = standardDis.fire(range);
		} catch (WeaponUnarmedException e) {
			assertEquals("Weapon is unarmed.", e.getMessage());
		}
		
		// Get another standard armed disruptor
		standardDis = getStandardDisruptor();
		
		// Set the range to an illegal value 
		range = 0;
		
		// Fire the weapon with an invalid range.
		try {
			damage = standardDis.fire(range);
		} catch (TargetOutOfRangeException e) {
			assertEquals("Target not in weapon range. [1|30]", e.getMessage());
		}
		
		// Get an armed overloaded disruptor
		Disruptor ovDis = getOverloadedDisruptor();
		
		// Verify that it takes 4 energy to arm an OL disr
		assertEquals(4, ovDis.energyToArm());
		
		// Verify that it is overloaded.
		assertEquals(WeaponArmingType.OVERLOAD, ovDis.getArmingType());
		
		// Fire the disruptor at range 8 target
		range = 8;
		damage = ovDis.fire(range);
		
		// Damage will be 6 at range 8 (or 0 on a miss).
		assertTrue(damage == 6 || damage == 0);

		// Get an armed overloaded disruptor
		ovDis = getOverloadedDisruptor();
		range = 20;

		// Fire overloads at a target outside of range 8.
		try {
			damage = ovDis.fire(range);
		} catch (TargetOutOfRangeException e) {
			assertEquals("Target not in weapon range. [0|8]", e.getMessage());
		}
		
	}
	
	/// Object builders for tests
	
	private Disruptor getUnarmedDisruptor() {
		Disruptor disruptor = new Disruptor(30);
		
		return disruptor;
		
	}
	
	private Disruptor getStandardDisruptor() {
		Disruptor dis = new Disruptor(30);
		dis.setStandard();
		dis.arm(2);
		
		return dis;
	}
	
	private Disruptor getOverloadedDisruptor() {
		Disruptor dis = new Disruptor(30);
		dis.setOverload();
		dis.arm(4);
		
		return dis;
	}

}
