package com.sfb.utilities;

import static org.junit.Assert.*;

import org.junit.Test;

public class DACTest {
	
	@Test
	public void testDAC() {
		DAC dac = new DAC();
		DiceRoller roller = new DiceRoller();
		
		int numberOfRolls = 20;
		
		for (int i = 0; i < numberOfRolls ; i++) {
			int roll = roller.rollTwoDice();
			
			System.out.println(roll + "|" + dac.fetchNextHit(roll));
		}
	}

}
