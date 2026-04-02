package com.sfb;

import static org.junit.Assert.fail;

import org.junit.Test;

import com.sfb.objects.Ship;

public class ShipFactoryTest {
	
	@Test
	public void testCreateShip() {
		ShipFactory factory = ShipFactory.getInstance();
		
		Ship newShip = factory.buildShip("FED_CA", "SS Botany Bay");
		
		System.out.println(newShip);
		
	}


}
