package com.sfb.exceptions;

/**
 * Thrown when an operation is attempted with an unarmed weapon.
 * 
 * @author Daniel Eastland
 *
 */
public class WeaponUnarmedException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public WeaponUnarmedException() {}
	
	public WeaponUnarmedException(String message) {
		super(message);
	}
}
