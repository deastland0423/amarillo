package com.sfb.constants;

/**
 * The Annex charts: values read by several unrelated systems and belonging to none of them.
 *
 * <h2>What belongs here, and what does not</h2>
 * Almost everything here is a TABLE indexed by something — size class for life support, shields
 * and T-bombs, arming turn for the torpedo costs — transcribed from the Annexes and consulted
 * from more than one place. {@link #IMPULSES_PER_TURN} is the other kind: a scalar so global that
 * no single class owns it.
 *
 * <p>A threshold that expresses ONE rule enforced in ONE place does NOT belong here, and that is
 * not an accident of history. This project cites rule numbers at the site that implements them,
 * so a limit like the fifteen hexes of a scout's reach (G24.2181) lives beside the code that
 * applies it, where its citation and its use sit together; pulling the number out here would
 * separate it from both. {@code Game.SCOUT_FUNCTION_RANGE} and {@code Ship.AEGIS_RANGE} are
 * where they are deliberately.
 *
 * <p>Enums are types, not constants, and never belong here. Domain enums live in
 * {@code com.sfb.properties} ({@code Faction}, {@code TurnMode}, {@code PlasmaType}) or nested in
 * the machine whose vocabulary they are, like {@code Game.ImpulsePhase}.
 *
 * @author Daniel Eastland
 */
public class Constants {

	public static final double TRANS_ENERGY = 0.2; // The energy required to activate a single transporter box.
	public static final int IMPULSES_PER_TURN = 32; // The number of impulses in every turn.
	public static final int WEAPON_FIRE_DELAY = (int) (IMPULSES_PER_TURN / 4); // Weapons must wait 1/4 turn before firing
																																							// again.
	public static final double[] LIFE_SUPPORT_COST = { 0.0, 3.0, 1.5, 1.0, 0.5, 0.0 }; // Cost of life support for size
																																											// class [index]. (3 = 1.0, 4 =
																																											// 0.5, etc.)
	public static final double[] MINIMUM_SHIELD_COST = { 0.0, 2.0, 1.0, 1.0, 0.5, 0.5, 0, 0 }; // Cost of minimum shields
																																															// for size class[index]
	public static final int[] ACTIVE_SHIELD_COST = { 0, 7, 4, 2, 1, 1, 0, 0 }; // Cost of active shields for size
																																							// class[index]

	public static final int[] MAX_TBOMBS = { 0, 12, 6, 4, 2, 0, 0, 4 }; // Max T-bombs for size class [index]

	// The cost to arm a torpedo on: 0) First two turns 1) Final turn 2) hold cost.
	public static final int[] fArmingCost = new int[] { 1, 3, 0 };
	public static final int[] gArmingCost = new int[] { 2, 3, 1 };
	public static final int[] sArmingCost = new int[] { 2, 4, 2 };
	public static final int[] rArmingCost = new int[] { 2, 5 };

}
