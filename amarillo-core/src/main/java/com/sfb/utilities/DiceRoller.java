package com.sfb.utilities;

public class DiceRoller {

	private int die1;
	private int die2;
	
	/**
	 * 
	 * @return The sum of two ramdom 6-sided dice
	 */
	public int rollOneDie() {
		roll();
		return die1;
	}
	
	/**
	 * 
	 * @return The result of one random 6-sided die
	 */
	public int rollTwoDice() {
		roll();
		return die1 + die2;
	}
	
	private void roll() {
		// Roll the dice by setting each of the die to
		// a random value from 1 to 6.
		die1 = (int)(Math.random()*6) + 1;
		die2 = (int)(Math.random()*6) + 1;
	}
}
