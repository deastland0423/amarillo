package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.sfb.properties.TurnMode;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;

public class ShipTest {
	
	@Test
	public void initTest() {
		// Create a new ship
		Ship testShip = new Ship();
		
		testShip.init(KlingonShips.getD7());
		
//		// Load the ship with the values from the map.
//		testShip.init(getInitMap());
		
		// Verify number of fhull
		assertEquals(4, testShip.getHullBoxes().getAvailableFhull());
		
		// Verify ahull
		assertEquals(7, testShip.getHullBoxes().getAvailableAhull());
		
		// Do a point of damage to the FHull
		testShip.getHullBoxes().damageFhull();
		
		// Verify the new smaller fhull total
		assertEquals(3, testShip.getHullBoxes().getAvailableFhull());
		
		// Verify number of chull
		assertEquals(0, testShip.getHullBoxes().getAvailableChull());
		
		// Verify lwarp
		assertEquals(15, testShip.getPowerSysetems().getAvailableLWarp());
		
		// Verify impulse
		assertEquals(5, testShip.getPowerSysetems().getAvailableImpulse());
		
		// Do some damage to the impulse.
		assertTrue(testShip.getPowerSysetems().damageImpulse());
		
		// Check that the impulse power is reduced.
		assertEquals(4, testShip.getPowerSysetems().getAvailableImpulse());
		
		// Repair impulse
		assertTrue(testShip.getPowerSysetems().repairImpulse(1));
		// Verify impulse is back to original value.
		assertEquals(5, testShip.getPowerSysetems().getAvailableImpulse());
		
		// Try to repair again, and it fails because there are no undamaged systems.
		assertFalse(testShip.getPowerSysetems().repairImpulse(1));
		
		// Get security stations
		assertEquals(2, testShip.getControlSpaces().getAvailableSecurity());
		
		// Get aux control
		assertEquals(2, testShip.getControlSpaces().getAvailableAuxcon());
		
		// Get probes
		assertEquals(1, testShip.getProbes().availableProbes());
		
		// Arm the probe for information.
		testShip.getProbes().get(0).setToInformation();
		assertTrue(testShip.getProbes().get(0).arm(1));
		
		// Not fully armed, so return -1 when trying to fire.
		assertEquals(-1, testShip.getProbes().get(0).fire(3));
		
		// Arm a second time
		assertTrue(testShip.getProbes().get(0).arm(1));
		
		// Fire fully armed probe.
		assertEquals(20, testShip.getProbes().get(0).fire(3));
		
		// Check the turn mode
		assertEquals(TurnMode.B, testShip.getTurnMode());
		
		System.out.println(testShip);
	}
	

}
