package com.sfb.exceptions;

/**
 * Thrown when a system attempts to draw more energy from a capacitor than 
 * it currently holds.
 * 
 * @author Daniel Eastland
 *
 */
public class CapacitorException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public CapacitorException() {}
	
	public CapacitorException(String message) {
		super(message);
	}
}

