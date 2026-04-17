package com.sfb.constants;

/**
 * Values that will not change throughout the execution of the program.
 * Later, some may be moved to a config file.
 * 
 * @author Daniel Eastland
 *
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

	// Common weapons arcs
	public static final class Arcs {
		// Basic arcs
		public static final int[] LF = new int[] { 21, 22, 23, 24, 1 };
		public static final int[] RF = new int[] { 1, 2, 3, 4, 5 };
		public static final int[] R = new int[] { 5, 6, 7, 8, 9 };
		public static final int[] L = new int[] { 17, 18, 19, 20, 21 };
		public static final int[] RR = new int[] { 9, 10, 11, 12, 13 };
		public static final int[] LR = new int[] { 13, 14, 15, 16, 17 };
		// Common combined arcs
		public static final int[] FA = new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 };
		public static final int[] RA = new int[] { 9, 10, 11, 12, 13, 14, 15, 16, 17 };
		public static final int[] LS = new int[] { 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 1 };
		public static final int[] RS = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 };
		public static final int[] FH = new int[] { 19, 20, 21, 22, 23, 24, 1, 2, 3, 4, 5, 6, 7 };
		public static final int[] RH = new int[] { 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 };
		public static final int[] FULL = new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
				14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24 };
		// Plasma arcs
		public static final int[] FP = new int[] { 19, 20, 21, 22, 23, 24, 1, 2, 3, 4, 5, 6, 7 };
		public static final int[] RP = new int[] { 23, 24, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
		public static final int[] LP = new int[] { 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 1, 2, 3 };
	}

	// Replaced by ImpulseUtil.doesMove() — kept only for reference.
	@Deprecated
	public static int[][] IMPULSE_CHART = {
			/* Impulse */ {},
			/* 1 */ { 32 },
			/* 2 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16 },
			/* 3 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 15, 14, 13, 12, 11 },
			/* 4 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 21, 20, 19, 18, 17, 16, 10, 9, 8 },
			/* 5 */ { 32, 31, 30, 29, 28, 27, 26, 23, 22, 21, 20, 15, 14, 13, 7 },
			/* 6 */ { 32, 31, 30, 29, 28, 27, 25, 24, 23, 22, 19, 18, 17, 16, 12, 11, 6 },
			/* 7 */ { 32, 31, 30, 29, 28, 26, 25, 24, 23, 21, 20, 19, 15, 14, 10, 5 },
			/* 8 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 22, 21, 20, 18, 17, 16, 13, 12, 9, 8, 4 },
			/* 9 */ { 32, 31, 30, 29, 27, 26, 25, 23, 22, 19, 18, 15, 11 },
			/* 10 */ { 32, 31, 30, 29, 28, 27, 26, 24, 23, 21, 20, 17, 16, 14, 13, 10, 7 },
			/* 11 */ { 32, 31, 30, 28, 27, 25, 24, 22, 21, 19, 18, 15, 12, 9, 6, 3 },
			/* 12 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 20, 19, 17, 16, 14, 11, 8 },
			/* 13 */ { 32, 31, 30, 29, 28, 26, 25, 23, 21, 20, 18, 15, 13, 10, 5 },
			/* 14 */ { 32, 31, 30, 29, 28, 27, 26, 24, 23, 22, 21, 19, 17, 16, 14, 12, 7 },
			/* 15 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 22, 20, 18, 15, 13, 11, 9 },
			/* 16 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 14, 12, 10, 8, 6, 4, 2 },
			/* 17 */ { 32, 31, 29, 27, 25, 23, 21, 19, 17 },
			/* 18 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 22, 20, 18, 16, 15, 13, 11, 9 },
			/* 19 */ { 32, 31, 30, 29, 28, 27, 26, 24, 23, 22, 21, 19, 17, 14, 12, 7 },
			/* 20 */ { 32, 31, 30, 29, 28, 26, 25, 24, 23, 21, 20, 18, 16, 15, 13, 10, 8, 5 },
			/* 21 */ { 32, 31, 30, 29, 28, 27, 26, 25, 23, 22, 20, 19, 17, 14, 11 },
			/* 22 */ { 32, 31, 30, 28, 27, 25, 24, 22, 21, 19, 18, 16, 15, 12, 9, 6, 3 },
			/* 23 */ { 32, 31, 30, 29, 28, 27, 26, 24, 23, 21, 20, 17, 14, 13, 10, 7 },
			/* 24 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 20, 19, 18, 16, 15, 12, 11, 8, 4 },
			/* 25 */ { 32, 31, 30, 29, 27, 26, 25, 22, 21, 18, 17, 13, 9 },
			/* 26 */ { 32, 31, 30, 29, 28, 26, 25, 24, 23, 21, 20, 19, 16, 15, 14, 10, 5 },
			/* 27 */ { 32, 31, 30, 29, 28, 27, 25, 24, 23, 22, 19, 18, 17, 12, 11, 6 },
			/* 28 */ { 32, 31, 30, 29, 28, 27, 26, 24, 23, 22, 21, 20, 16, 15, 14, 13, 8, 7 },
			/* 29 */ { 32, 31, 30, 29, 28, 27, 26, 25, 21, 20, 19, 18, 17, 10, 9 },
			/* 30 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 16, 15, 14, 13, 12, 11 },
			/* 31 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17 },
			/* 32 */ { 32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6,
					5, 4, 3, 2, 1 },
	};
}
