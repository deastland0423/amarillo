package com.sfb;

import com.sfb.constants.Constants;

/**
 * Per-game impulse clock. Each Game owns exactly one instance; every ship
 * system that needs the current impulse or turn holds a reference injected
 * via Ship.attachClock() rather than reading global state.
 *
 * Historical note: this was a static singleton from 2015 until 2026-07-07,
 * which made concurrent games in one JVM impossible (starting a second game
 * reset every other game's clock).
 *
 * @author deastland
 */
public class TurnTracker {

	private int impulse = 0; // Total impulse count for this game so far.

	public void reset() {
		impulse = 0;
	}

	/**
	 * Fetch the absolute impulse count.
	 *
	 * @return The number of impulses since the start of the game.
	 */
	public int getImpulse() {
		return impulse;
	}

	/**
	 * Increment the impulse counter. Only Game.advancePhase()/beginImpulses()
	 * should call this in production code; tests may use it to position the
	 * clock directly.
	 */
	public void nextImpulse() {
		impulse++;
	}

	/**
	 * Put the clock back where a saved game left it.
	 *
	 * One value is the whole clock: the turn and the turn-centric impulse are both DERIVED from
	 * the absolute count below, so there is nothing else to restore and no way for the three to
	 * disagree. Negative input is clamped to zero rather than producing a game before turn zero.
	 *
	 * For loading a save only. Everything in play moves the clock with {@link #nextImpulse()},
	 * and a system that reads the clock holds this same instance by reference, so restoring here
	 * reaches all of them at once.
	 */
	public void restoreImpulse(int absoluteImpulse) {
		impulse = Math.max(0, absoluteImpulse);
	}

	/**
	 * Get the turn number.
	 *
	 * @return The number of turns since the start of the game.
	 */
	public int getTurn() {
		return (int) ((impulse - 1) / Constants.IMPULSES_PER_TURN);
	}

	/**
	 * Fetch the current impulse within the current turn.
	 *
	 * @return The turn-centric impulse (1-32).
	 */
	public int getLocalImpulse() {

		int localImpulse = 0;

		// On the 0th turn, just use the impulse.
		if (getTurn() == 0) {
			localImpulse = impulse;
			// Otherwise, div by 32 to get the impulse within the turn.
		} else {
			localImpulse = impulse % Constants.IMPULSES_PER_TURN;
		}

		// If the div is 0, we're actually on the 32nd impulse.
		if (localImpulse == 0) {
			localImpulse = 32;
		}

		return localImpulse;
	}
}
