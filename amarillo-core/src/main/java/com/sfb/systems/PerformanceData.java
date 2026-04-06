package com.sfb.systems;

import java.util.Map;

public class PerformanceData {

	// Performance statistics for this spaceframe.
	
	// Base values
	private double  movementCost		= 0;
	private boolean nimble				= false;	// Nimble ships can do cheap EM
	private int     breakdownChance		= 0;		// Chance on a d6 that a breakdown will occur when performing an HET maneuver.
	private int     bonusHets			= 0;		// Number of times this ship get's a -2 to the HET breakdown roll.
	
	// Calculated values
	private double hetCost				= 0;
	private double erraticCost			= 0;

	// Real-time values
	private int bonusHetsRemaining		= 0;		// Number of HETs remaining with the bonus to the breakdown roll

	public PerformanceData() {
		
	}
	
	public void init(Map<String, Object> values) {
		
		movementCost    = values.get("movecost")    == null ? 1     : (Double)values.get("movecost");
		nimble          = values.get("nimble")      == null ? false : true;
		breakdownChance = values.get("breakdown")   == null ? 4     : (Integer)values.get("breakdown");
		bonusHets       = values.get("bonushets")   == null ? 0     : (Integer)values.get("bonushets");
		
		// Calculate cost of HET
		hetCost = movementCost * 5;

		// Calculate cost of EM
		if (nimble) {
			erraticCost = movementCost * 3;
		} else {
			erraticCost = movementCost * 6;
		}
	}
	
	public void cleanUp() {
		//TODO: Figure out what to do here, if anything.
	}
	
	public double getMovementCost() {
		return this.movementCost;
	}
	
	public boolean isNimble() {
		return this.nimble;
	}
	
	public int getBreakdownChance() {
		return this.breakdownChance;
	}
	
	public int getBonusHets() {
		return this.bonusHets;
	}
	
	public int getBonusHetsRemaining() {
		return bonusHetsRemaining;
	}
	
	public void useBonusHet() {
		this.bonusHetsRemaining--;
	}

	public double getHetCost() {
		return this.hetCost;
	}
	
	public double getErraticCost() {
		return this.erraticCost;
	}

}
