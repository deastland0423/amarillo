package com.sfb.objects;

import com.sfb.properties.PlasmaType;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The PLASMA TORPEDO WARHEAD STRENGTH TABLE, transcribed from the Captain's Master Rulebook
 * (the FP section's PLASMA TORPEDO TABLE, and XFP1.53's cleaner reprint of the same numbers)
 * and cross-checked against the table printed on a War Eagle's SSD and a Romulan KR's.
 *
 * <pre>
 * RANGE  0-5 6-10 11-12 13-14 15 16-18 19 20 21-23 24 25 26-28 29 30
 * R       50   50    35    35 35    25 25 25    20 20 20    10  5  1
 * S       30   30    22    22 22    15 15 15    10  5  1     0  0  0
 * G       20   20    15    15 15    10  5  1     0  0  0     0  0  0
 * F       20   15    10     5  1     0  0  0     0  0  0     0  0  0
 * D       10    8     5     2  1     0  0  0     0  0  0     0  0  0
 * </pre>
 *
 * <h2>The bug this was written for</h2>
 * The type-R row read {@code ... 20, 20, 20, 20, 25, 10 ...}: twenty-five damage at range 25,
 * between four 20s and a 10. A plasma warhead decays as it flies and can never strengthen, so
 * the value was impossible on its face — and it was in core, not just the client mirror, so a
 * type-R torpedo that had flown 25 hexes really did score 25 points instead of 20.
 *
 * It survived because nothing ever read the table as a whole. Every test asked about a single
 * range, and never about a range anyone cared to check: 25 hexes is a torpedo that has chased
 * its target almost to the end of its 30-hex life.
 *
 * <p>{@link #everyPlasmaTableDecaysAndNeverStrengthens} is the guard that generalises, and it
 * is the one that would have caught this without anybody knowing the right answer.
 */
public class PlasmaWarheadStrengthTest {

	/** Strength at each range 0..30, as the sheet prints the bands. */
	private static Map<PlasmaType, int[]> paperTable() {
		Map<PlasmaType, int[]> t = new LinkedHashMap<>();
		t.put(PlasmaType.R, expand(50, 50, 35, 35, 35, 25, 25, 25, 20, 20, 20, 10, 5, 1));
		t.put(PlasmaType.S, expand(30, 30, 22, 22, 22, 15, 15, 15, 10, 5, 1, 0, 0, 0));
		t.put(PlasmaType.G, expand(20, 20, 15, 15, 15, 10, 5, 1, 0, 0, 0, 0, 0, 0));
		t.put(PlasmaType.F, expand(20, 15, 10, 5, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		t.put(PlasmaType.D, expand(10, 8, 5, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0));
		return t;
	}

	/** The sheet's fourteen bands, widened to one value per hex 0..30. */
	private static int[] expand(int... byBand) {
		int[][] bands = { {0,5}, {6,10}, {11,12}, {13,14}, {15,15}, {16,18}, {19,19},
		                  {20,20}, {21,23}, {24,24}, {25,25}, {26,28}, {29,29}, {30,30} };
		assertEquals("a row must give one value per printed band", bands.length, byBand.length);
		int[] out = new int[31];
		for (int b = 0; b < bands.length; b++)
			for (int r = bands[b][0]; r <= bands[b][1]; r++)
				out[r] = byBand[b];
		return out;
	}

	/** Strength of a torpedo of this type that has flown {@code range} hexes. */
	private int strengthAt(PlasmaType type, int range) {
		PlasmaTorpedo t = new PlasmaTorpedo(type, com.sfb.properties.WeaponArmingType.STANDARD);
		for (int i = 0; i < range; i++)
			t.incrementDistance();
		return t.getCurrentStrength();
	}

	@Test
	public void everyTypeMatchesThePrintedTableAtEveryRange() {
		for (Map.Entry<PlasmaType, int[]> row : paperTable().entrySet()) {
			PlasmaType type = row.getKey();
			for (int range = 0; range <= 30; range++)
				assertEquals("type-" + type + " at range " + range,
						row.getValue()[range], strengthAt(type, range));
		}
	}

	@Test
	public void theTypeRTorpedoScoresTwentyAtRangeTwentyFive() {
		// The specific cell that was wrong, named so the regression cannot come back quietly.
		assertEquals(20, strengthAt(PlasmaType.R, 25));
		assertEquals("...and 20 either side of it, which is what made 25 impossible",
				20, strengthAt(PlasmaType.R, 24));
		assertEquals(10, strengthAt(PlasmaType.R, 26));
	}

	@Test
	public void everyPlasmaTableDecaysAndNeverStrengthens() {
		// The guard that generalises. A warhead loses strength as it flies and can never gain
		// it, so this would have caught the type-R fault without anyone knowing that range 25
		// should read 20 — only that it could not read more than range 24 did.
		for (PlasmaType type : PlasmaType.values()) {
			int previous = Integer.MAX_VALUE;
			for (int range = 0; range <= 40; range++) {
				int here = strengthAt(type, range);
				assertTrue("type-" + type + " strengthens between range " + (range - 1)
						+ " and " + range + ": " + previous + " then " + here,
						here <= previous);
				previous = here;
			}
		}
	}

	@Test
	public void everyTypeIsSpentByTheEndOfItsReach() {
		// Nothing should still be scoring past 30 hexes; the type-R's 30 is the longest reach
		// in the game (FP2.11) and the rest fall off well before it.
		for (PlasmaType type : PlasmaType.values())
			assertEquals("type-" + type + " still scores past its maximum range",
					0, strengthAt(type, 31));
	}

	@Test
	public void theTypesRankInTheOrderTheDownloadLadderImplies() {
		// A launcher may fire any type at or below its own size, so the rows on an SSD are a
		// ladder: a bigger torpedo is never the weaker choice at any range.
		PlasmaType[] strongestFirst = { PlasmaType.R, PlasmaType.S, PlasmaType.G, PlasmaType.F };
		for (int range = 0; range <= 30; range++)
			for (int i = 1; i < strongestFirst.length; i++)
				assertTrue("type-" + strongestFirst[i - 1] + " is weaker than type-"
						+ strongestFirst[i] + " at range " + range,
						strengthAt(strongestFirst[i - 1], range)
								>= strengthAt(strongestFirst[i], range));
	}
}
