package com.sfb.systemgroups;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import com.sfb.objects.Ship;

public class LabsTest {
	
	@Test
	public void testLabCount() {
		int numberOfLabs = 8;
		
		Labs labs = getSampleLabs(numberOfLabs);
		
		assertEquals(numberOfLabs, labs.getAvailableLab());
	}
	
	@Test
	public void testLabDamageAndRepair() {
		int numberOfLabs = 2;
		
		Labs labs = getSampleLabs(numberOfLabs);
		
		assertEquals(numberOfLabs, labs.getAvailableLab());
		
		//damage a lab
		labs.damage();
		
		// verify that there is one less lab
		assertEquals(numberOfLabs - 1, labs.getAvailableLab());
		
		// repair a lab
		assertTrue(labs.repair(1));
		
		// verify that labs is back to full
		assertEquals(numberOfLabs, labs.getAvailableLab());
		
		// fail at repairing a lab when all are functional
		assertFalse(labs.repair(1));
	}
	
	@Test
	public void testResearch() {

		int range = 8;
		Labs labs = getSampleLabs(1);

		// At range 8, a single lab can gather no more than 2 points.
		assertTrue(labs.calculateResearchPoints(range) < 3);
		System.out.println(labs.calculateResearchPoints(range));
	}


	public Labs getSampleLabs(int numberOfLabBoxes) {
		Labs labs = new Labs(new Ship());
		Map<String, Object> values = new HashMap<>();
		values.put("lab", new Integer(numberOfLabBoxes));
		labs.init(values);

		return labs;
	}
}
