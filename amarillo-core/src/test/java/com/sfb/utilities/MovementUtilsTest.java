package com.sfb.utilities;

import static org.junit.Assert.assertEquals;

import java.util.TreeMap;

import org.junit.Test;

public class MovementUtilsTest {

	@Test
	public void testTotalHexesMoved() {
		TreeMap<Integer, Integer> testMap = new TreeMap<>();
		
		testMap.put(1,  20);
		testMap.put(5, 15);
		testMap.put(15, 20);
		
		int totalHexesMoved = MovementUtil.totalHexesMoved(testMap);
		
		assertEquals(totalHexesMoved, 19);
	}
}
