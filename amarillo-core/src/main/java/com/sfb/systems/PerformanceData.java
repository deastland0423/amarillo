package com.sfb.systems;

import java.util.Map;

public class PerformanceData {

	// Base values
	private double  movementCost    = 0;
	private boolean nimble          = false;
	private int     breakdownChance = 0;
	private int     bonusHets       = 0; // total -2 bonuses for breakdown roll (C6.52 / C6.521)

	// Calculated values
	private double hetCost     = 0;
	private double erraticCost = 0;

	// Real-time values
	private int bonusHetsRemaining = 0;

	public PerformanceData() {}

	public void init(Map<String, Object> values) {
		movementCost    = values.get("movecost")   == null ? 1     : (Double)  values.get("movecost");
		nimble          = values.get("nimble")     != null;
		breakdownChance = values.get("breakdown")  == null ? 4     : (Integer) values.get("breakdown");
		bonusHets       = values.get("bonushets")  == null ? 1     : (Integer) values.get("bonushets");

		bonusHetsRemaining = bonusHets;

		hetCost     = movementCost * 5;
		erraticCost = nimble ? movementCost * 3 : movementCost * 6;
	}

	public void cleanUp() {}

	public double getMovementCost()      { return movementCost; }
	public boolean isNimble()            { return nimble; }
	public int getBreakdownChance()      { return breakdownChance; }
	public int getBonusHets()            { return bonusHets; }
	public int getBonusHetsRemaining()   { return bonusHetsRemaining; }
	public double getHetCost()           { return hetCost; }
	public double getErraticCost()       { return erraticCost; }

	public void useBonusHet() {
		if (bonusHetsRemaining > 0) bonusHetsRemaining--;
	}

	/** C6.544: Reduce breakdown rating by 1 each time the ship breaks down (minimum 1). */
	public void decrementBreakdownRating() {
		if (breakdownChance > 1) breakdownChance--;
	}
}
