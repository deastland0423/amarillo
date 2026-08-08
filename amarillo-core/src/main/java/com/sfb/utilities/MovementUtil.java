package com.sfb.utilities;

import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MovementUtil {

	private static final Logger log = LogManager.getLogger(MovementUtil.class);

	/**
	 * Given a map of impulses at which a speed is set for a unit during a turn,
	 * calculate the number of hexes moved over the entire turn.
	 * 
	 * @param speedsMoved A map containing each impulse number that speed for a unit
	 *                    is set.
	 * @return The total hexes moved over the turn.
	 */
	public static int totalHexesMoved(TreeMap<Integer, Integer> speedsMoved) {
		int totalHexesMoved = 0;

		int startingImpulse = 1;
		int nextStartImpulse = 0;

		// Get the speed for impulse 1 (should always be in the map) and create initial
		// value.
		// Then take this "starter batch" out of the map for the looping logic below.
		Integer speed = speedsMoved.get(1);
		if (speed == null) {
			throw new IndexOutOfBoundsException("There must be an entry with a key of 1 in the speedsMoved map.");
		}
		speedsMoved.remove(1);

		for (Entry<Integer, Integer> entry : speedsMoved.entrySet()) {

			nextStartImpulse = entry.getKey();

			log.info("Speed from impulse " + startingImpulse + " to impulse " + (nextStartImpulse - 1) + " is: " + speed);

			int localHexesMoved = hexesMoved(startingImpulse, nextStartImpulse - 1, speed);
			log.info("HEXED MOVED: " + localHexesMoved);
			totalHexesMoved += localHexesMoved;
			startingImpulse = nextStartImpulse;
			speed = entry.getValue();
		}

		nextStartImpulse = 32;
		log.info("Speed from impulse " + startingImpulse + " to impulse " + nextStartImpulse + " is: " + speed);
		int localHexesMoved = hexesMoved(startingImpulse, nextStartImpulse, speed);
		log.info("HEXES MOVED: " + localHexesMoved);
		totalHexesMoved += localHexesMoved;

		return totalHexesMoved;
	}

	/**
	 * Calculates the number of hexes moved, given a starting/ending impulse and a
	 * speed.
	 * 
	 * @param startImpulse First impulse at given speed
	 * @param endImpulse   Last impulse at given speed
	 * @param speed        the speed
	 * @return total number of hexes moved
	 */
	public static int hexesMoved(int startImpulse, int endImpulse, int speed) {

		int totalMoves = 0;

		for (int i = startImpulse; i <= endImpulse; i++) {
			if (moveThisImpulse(i, speed)) {
				totalMoves++;
			}
		}

		return totalMoves;

	}

	/**
	 * Returns TRUE if a unit at a given speed moves on the provided impulse.
	 * 
	 * @param impulse The impulse being checked
	 * @param speed   The speed of the unit
	 * @return TRUE if the movement chart for @impulse shows movement for a speed
	 *         of @speed
	 */
	public static boolean moveThisImpulse(int impulse, int speed) {
		return ImpulseUtil.doesMove(impulse, speed);
	}

	public static void main(String[] args) {

		TreeMap<Integer, Integer> testMap = new TreeMap<>();

		testMap.put(1, 20);
		testMap.put(5, 15);
		testMap.put(15, 20);

		int totalHexesMoved = MovementUtil.totalHexesMoved(testMap);

		System.out.println();
		System.out.println("TOTAL HEXES MOVED: " + totalHexesMoved);
	}
}
