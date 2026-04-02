package com.sfb.systemgroups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;

public class PowerSystemsTest {

	@Test
	public void test() {
		// Get the test power system (Fed CA)
		// CA has 15 LWarp, 15RWarp, 4 Impulse, 0 APR, and 4 Batteries
		Ship testShip = new Ship();
		testShip.init(FederationShips.getFedCa());

		PowerSystems testPs = testShip.getPowerSysetems();
//		PowerSystems testPs = TestObjects.testPowerSystems;
		
		// CA has 4 APR
		assertEquals(0, testPs.getAvailableApr());
		
		// CA has 4 battery
		assertEquals(3, testPs.getAvailableBattery());
		
		// CA has 38 total power
		assertEquals(34, testPs.getTotalAvailablePower());
		
		// No CWarp on the ship, this should return false
		assertFalse(testPs.damageCWarp());

		// Damage the left warp engine 3 times
		assertTrue(testPs.damageLWarp());
		assertTrue(testPs.damageLWarp());
		assertTrue(testPs.damageLWarp());

		// Check to see that there are 12 remaining boxes
		assertEquals(testPs.getAvailableLWarp(), 12);
		
		// Check that total available power is 3 less
		assertEquals(31, testPs.getTotalAvailablePower());
		
		// Try to damage an 5th APR.
		// Should faile because there are none
		assertFalse(testPs.damageApr());
	}
	
	@Test
	public void testBatteries() {
		// Get the test power system (Fed CA)
		// CA has 15 LWarp, 15RWarp, 4 Impulse, 4 APR, and 4 Batteries
		Ship testShip = new Ship();
		testShip.init(FederationShips.getFedCa());

		PowerSystems testPs = testShip.getPowerSysetems();

		// Put 3 power into battery
		assertTrue(testPs.chargeBattery(3));
		// Check battery power
		assertEquals(testPs.getBatteryPower(), 3);
		// Put 1 more power into battery (fail because no more room in batteries)
		
		// Use 3 battery power
		assertTrue(testPs.useBattery(3));
		// Remaining battery power should be 0
		assertEquals(0, testPs.getBatteryPower());

	}

}
