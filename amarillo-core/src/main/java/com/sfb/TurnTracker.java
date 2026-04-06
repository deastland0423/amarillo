package com.sfb;

import com.sfb.constants.Constants;

/**
 * This will be the main class that tracks time (and therefore events).
 * Possibly, it will be the main. Not sure yet.
 * 
 * @author deastland
 *
 */
public class TurnTracker {

	private static int impulse = 0;				// Total impulse count for the game thus far.
	
	public static void reset() {
		impulse = 0;
	}
	
	/**
	 * Fetch the global impulse count.
	 * @return The number of impulses since the start of the game.
	 */
	public static int getImpulse() {
		return impulse;
	}
	
	/**
	 * Increment the impulse counter.
	 */
	public static void nextImpulse() {
		impulse++;
	}
	
	/**
	 * Get the turn number.
	 * @return The number of turns since the start of the game.
	 */
	public static int getTurn() {
		return (int)((impulse - 1)/ Constants.IMPULSES_PER_TURN);
				
	}
	
	/**
	 * Fetch the current impulse within the current turn.
	 * @return The turn-centric impulse.
	 */
	public static int getLocalImpulse() {
		
		int localImpulse = 0;
		
		// On the 0th turn, just use the impulse.
		if (getTurn() == 0) {
			localImpulse = impulse;
		// Otherwise, div by 32 to get the impulse within the turn.
		} else {
			localImpulse =  impulse % Constants.IMPULSES_PER_TURN;
		}
		
		// If the div is 0, we're actually on the 32nd impulse.
		if (localImpulse == 0) {
			localImpulse = 32;
		}
		
		
		return localImpulse;
	}
}
