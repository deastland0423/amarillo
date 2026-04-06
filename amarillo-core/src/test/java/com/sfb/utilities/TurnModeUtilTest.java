package com.sfb.utilities;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.properties.TurnMode;

public class TurnModeUtilTest {
	
	@Test
	public void testTurnModes() {
		
		assertEquals(3, TurnModeUtil.getTurnMode(TurnMode.A, 19));
		
		assertEquals(4, TurnModeUtil.getTurnMode(TurnMode.B, 19));
		
		assertEquals(4, TurnModeUtil.getTurnMode(TurnMode.C, 19));

		assertEquals(6, TurnModeUtil.getTurnMode(TurnMode.D, 32));

		assertEquals(7, TurnModeUtil.getTurnMode(TurnMode.E, 32));
	
	}
	


}
