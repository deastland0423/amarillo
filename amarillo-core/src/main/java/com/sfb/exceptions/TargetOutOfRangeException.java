package com.sfb.exceptions;

/**
 * Thrown when a weapon attempts to fire at a target that is 
 * either below minimum range or above maximum range.
 * 
 * @author Daniel Eastland
 *
 */
public class TargetOutOfRangeException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public TargetOutOfRangeException() {}
	
	public TargetOutOfRangeException(String message) {
		super(message);
	}
}
