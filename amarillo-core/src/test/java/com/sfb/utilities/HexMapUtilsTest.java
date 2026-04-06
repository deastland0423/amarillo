package com.sfb.utilities;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.sfb.properties.Location;

public class HexMapUtilsTest {

	@Test
	public void testTrueBearing() {
		int facing = 5;
		int shipBearing = 13;
		
//		System.out.println("True Bearing: " + MapUtils.getTrueBearing(shipBearing, facing));
		
		assertEquals(MapUtils.getTrueBearing(shipBearing, facing), 17);
	}
	
	@Test
	public void testAdjacentHex() {
		Location sourceLocation = new Location(27, 15);
		int facing = 5;
		int relativeBearing = 21;
		
		Location destinationLocation = MapUtils.getAdjacentHex(sourceLocation, MapUtils.getTrueBearing(relativeBearing, facing));
		
//		System.out.println(destinationLocation);
		
		// Vefity that the adjacent hex at relative bearing 21 is <27|14>
		assertEquals(destinationLocation.getX(), 27);
		assertEquals(destinationLocation.getY(), 14);
	}
	
	@Test
	public void testFacingRightChange() {
		int facing = 21;
		
		int newFacing = MapUtils.getTrueBearing(9, facing);
		
		System.out.println("Turn right from " + facing + " has you now facing " + newFacing + ".");
		
	}
}
