package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Unit;

public class Transporters implements Systems {

	private static final double ENERGY_PER_USE = 0.2;
	/** Slack for binary floating point: 0.2 and 1.0 do not divide exactly. */
	private static final double EPSILON = 1e-9;

	/**
	 * The boxes. A transporter is used one box at a time and that box then cools off until
	 * the next turn or eight impulses later, whichever is longer — the same cycle
	 * laboratories follow (G4.451), which is why the model is shared.
	 */
	private final BoxCycle boxes = new BoxCycle();

	private double bankedEnergy   = 0.0; // energy allocated this turn, ready to spend
	private double energyUsed     = 0.0; // energy spent this turn

	private Unit owningShip;
	
	public Transporters(Unit owner) {
		this.owningShip = owner;
	}
	
	@Override
	public void init(Map<String, Object> values) {
		boxes.init(values.get("trans") == null ? 0 : (Integer) values.get("trans"));
	}

	/**
	 * Each surviving box and what it is doing, for a raid's target list (D7.835). The state
	 * is public: every use of a transporter is seen, so an attacker knows which boxes are
	 * spent — and will pick one that is not.
	 */
	public List<String> describeBoxes(int absoluteImpulse) {
		List<String> out = new ArrayList<>();
		for (int number : boxes.numbers()) {
			BoxCycle.State state = boxes.stateOf(number, absoluteImpulse);
			out.add("Transporter #" + number + " ("
				+ (state == BoxCycle.State.USED_THIS_TURN ? "used this turn"
				 : state == BoxCycle.State.COOLING_DOWN ? "cooling down"
				 : "unused") + ")");
		}
		return out;
	}

	/** The numbers of the boxes still undamaged, in SSD order. */
	public List<Integer> boxNumbers() {
		return boxes.numbers();
	}

	/**
	 * Destroy one NAMED box, for a raid that picked it deliberately (D7.835). Unlike
	 * {@link #damage()}, which gives up the least valuable box, this destroys the one the
	 * attacker chose.
	 */
	public boolean destroyBox(int number) {
		return boxes.damageBox(number);
	}

	/** Boxes surviving damage, busy or not. Public knowledge, like every box on the SSD. */
	public int getAvailableTrans() {
		return boxes.functioning();
	}

	/** Energy cost per transporter use. */
	public static double energyPerUse() {
		return ENERGY_PER_USE;
	}

	/** Bank energy allocated at the start of the turn (or drawn mid-turn from batteries). */
	public void bankEnergy(double energy) {
		bankedEnergy += energy;
	}

	/**
	 * How many uses remain: boxes free to work at this impulse, capped by the energy there
	 * is to power them. Both limits are real, and they are not the same — a ship can
	 * have energy and no free box, or a free box and nothing to power it with.
	 */
	public int availableUses(int absoluteImpulse) {
		int affordable = (int) ((bankedEnergy - energyUsed) / ENERGY_PER_USE + EPSILON);
		return Math.min(boxes.available(absoluteImpulse), affordable);
	}

	/**
	 * Use one transporter box and the energy it costs. Returns false if no box is free
	 * this impulse, or there is not the energy for it.
	 */
	public boolean useTransporter(int absoluteImpulse) {
		// A point of energy is five uses at 0.2, but 1.0 - 0.8 is 0.19999999999999996 in
		// binary floating point, so a bare comparison loses the fifth use. Compare with a
		// tolerance, as the movement cost does for the same reason.
		if (bankedEnergy - energyUsed < ENERGY_PER_USE - EPSILON) return false;
		if (boxes.use(absoluteImpulse) < 0) return false;
		energyUsed += ENERGY_PER_USE;
		return true;
	}

	/**
	 * How many boxes have been used this turn. Every use is watched at the table, so this
	 * is public knowledge even though the energy behind it is not.
	 */
	public int freeBoxes(int absoluteImpulse) {
		return boxes.available(absoluteImpulse);
	}

	/**
	 * How many boxes have been used this turn. Every use is watched at the table, so this
	 * is public knowledge even though the energy behind it is not.
	 */
	public int usesMadeThisTurn(int absoluteImpulse) {
		return boxes.usesThisTurn(absoluteImpulse);
	}

	/** Energy banked this turn but not yet spent. */
	public double getBankedEnergy() {
		return bankedEnergy - energyUsed;
	}
	
	@Override
	public int fetchOriginalTotalBoxes() {
		return boxes.total();
	}

	@Override
	public int fetchRemainingTotalBoxes() {
		return boxes.functioning();
	}

	/**
	 * Destroy one transporter box. No player decision: the least valuable goes first —
	 * one already used this turn, then one still cooling off from last turn, then an
	 * unused one. That is the order a player would choose anyway, so asking would only
	 * slow the damage step down.
	 */
	public boolean damage() {
		return boxes.damage();
	}

	public boolean repair(int numberToRepair) {
		return boxes.repair(numberToRepair);
	}
	
	@Override
	public void cleanUp() {
		bankedEnergy = 0.0;
		energyUsed   = 0.0;
	}

	@Override
	public Unit fetchOwningUnit() {
		return this.owningShip;
	}

}
