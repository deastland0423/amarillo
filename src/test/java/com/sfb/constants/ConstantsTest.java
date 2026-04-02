package com.sfb.constants;

import org.junit.Test;
import static org.junit.Assert.*;
import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;

public class ConstantsTest {
	
	@Test
	public void testSpeedCheck() {
		
		int impulse = 16;
		StringBuilder speedList = new StringBuilder();
		
		int[] speedsThisTurn = Constants.IMPULSE_CHART[impulse];
		
		for (int i = 0; i < speedsThisTurn.length; i++) {
			speedList.append(speedsThisTurn[i]).append("|");
		}
		
		System.out.println(speedList.toString());
		
		Ship ship = new Ship();
		ship.init(FederationShips.getFedCa());
		
		ship.setSpeed(14);
		
		assertTrue(ship.movesThisImpulse(impulse));
		
		ship.setSpeed(15);
		
		assertFalse(ship.movesThisImpulse(impulse));
		
	}

}
