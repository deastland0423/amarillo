package com.sfb.systemgroups;

import java.util.Map;

public class PerformanceData {

	/**
	 * Movement cost is held in TWELFTHS, as a whole number, and that is what makes it exact.
	 *
	 * Every cost in the game is a whole twelfth — 1/4 is 3, 1/3 is 4, 1/2 is 6, 2/3 is 8,
	 * 3/4 is 9, 1 is 12, 1.5 is 18, 2 is 24 — so a speed costs {@code speed * twelfths / 12}
	 * and lands on the number the rulebook says it does. Speed 3 on a two-thirds hull is
	 * 24/12, which is 2.0 and not 2.0000001.
	 * <p>
	 * That matters because the JSON cannot say two-thirds. It says {@code 0.6666667} on 77
	 * hulls and {@code 0.3333333} on 72, and multiplying those gives a number a hair off
	 * every real total. The old answer was a tolerance at each place that compared or divided
	 * — {@code + 1e-6} here, {@code + 0.001} there, a rounding of the sum in the web budget —
	 * which works right up until somebody writes a new one of those places and does not know
	 * to. Somebody did: the energy dialog's speed slider divides by the cost, and on twelve
	 * hulls it was quietly refusing the ship's top speed.
	 * <p>
	 * Twelfths do NOT extend to energy generally, and were not meant to: a transporter use
	 * costs 0.2, which is a fifth and 2.4 twelfths. The two never meet in a multiplication,
	 * only in the allocation total, which is rounded once where it is summed.
	 */
	private static final int PER_POINT = 12;

	/** Tolerance when reading the JSON: 0.33333333 and 1.0/3.0 must both mean four twelfths. */
	private static final double TWELFTH_SLACK = 0.001;

	/** Finer than any real fraction of a hex, coarser than any accumulated float error. */
	private static final double PRECISION = 1e4;

	private int movementTwelfths = PER_POINT;

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
		double declared = values.get("movecost") == null ? 1 : (Double) values.get("movecost");
		movementTwelfths = toTwelfths(declared);
		// The canonical value from here on is the whole number of twelfths; this is simply the
		// nearest double to it, so a hull written 0.6666667 now reports 0.6666666666666666.
		movementCost    = movementTwelfths / (double) PER_POINT;
		nimble          = values.get("nimble")     != null;
		breakdownChance = values.get("breakdown")  == null ? 4     : (Integer) values.get("breakdown");
		bonusHets       = values.get("bonushets")  == null ? 1     : (Integer) values.get("bonushets");

		bonusHetsRemaining = bonusHets;

		hetCost     = movementTwelfths * 5.0 / PER_POINT;
		erraticCost = movementTwelfths * (nimble ? 3.0 : 6.0) / PER_POINT;
	}

	/**
	 * Read a declared cost as a whole number of twelfths, refusing anything that is not one.
	 *
	 * Deliberately strict. Rounding quietly would re-cost a hull on a typo — 0.7 is nearer
	 * two-thirds than anything else, and a ship that silently moved cheaper than its SSD says
	 * is the kind of fault nobody finds by playing. The slack is wide enough for every
	 * spelling of a third anyone writes (0.3333333, 0.33333333, 1.0/3.0) and far too narrow
	 * to admit a cost the rules do not have.
	 */
	private static int toTwelfths(double declared) {
		double exact = declared * PER_POINT;
		int rounded = (int) Math.round(exact);
		if (rounded < 0 || Math.abs(exact - rounded) > TWELFTH_SLACK)
			throw new IllegalArgumentException("movecost " + declared
					+ " is not a whole twelfth — every movement cost in the game is"
					+ " (1/4=3, 1/3=4, 1/2=6, 2/3=8, 3/4=9, 1=12, 1.5=18, 2=24)");
		return rounded;
	}

	/**
	 * Warp energy to buy this speed, exactly. {@code speed * twelfths / 12}, which for a
	 * two-thirds hull makes speed 3 cost 2.0 rather than 2.0000001 and speed 30 cost 20.0
	 * rather than 20.000001.
	 */
	public double movementEnergyFor(int speed) {
		return speed * (double) movementTwelfths / PER_POINT;
	}

	/**
	 * Speed this much warp energy buys, rounding DOWN: short of a full hex buys nothing.
	 * <p>
	 * The tolerance stays, and is not redundant — {@link #movementEnergyFor} makes what we
	 * CHARGE exact, but energy reaches this from elsewhere too: a saved game written before
	 * this change, a test that multiplies the cost itself, reserve power drawn mid-turn.
	 * <p>
	 * <b>It had to get WIDER, which was a surprise worth recording.</b> The old code divided
	 * by the same truncated cost that had been multiplied by, so the error cancelled and
	 * {@code 1e-6} was ample: {@code 9.999999 / 0.3333333} is 30 on the nose. Dividing by the
	 * EXACT twelfths removes the cancellation and exposes the error instead, and it grows with
	 * the speed — at speed 30 a one-third hull arrives 3e-6 of a hex short. A tolerance of
	 * 1e-6 then turned speed 30 into 29, which is the fault this whole change set out to fix,
	 * reintroduced one layer down.
	 * <p>
	 * So the energy is rounded to a ten-thousandth of a twelfth first, exactly as the web
	 * budget rounds its total: fine enough that 3.999 hexes still buys 3, coarse enough to
	 * absorb anything float error can accumulate.
	 */
	public int speedAffordable(double warpEnergy) {
		if (movementTwelfths <= 0)
			return 0;
		double energyTwelfths = Math.round(warpEnergy * PER_POINT * PRECISION) / PRECISION;
		return (int) (energyTwelfths / movementTwelfths);
	}

	/** The cost as a whole number of twelfths, for a client that must divide by it exactly. */
	public int getMovementTwelfths() {
		return movementTwelfths;
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
