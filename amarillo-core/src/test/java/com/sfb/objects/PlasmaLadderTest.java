package com.sfb.objects;

import com.sfb.properties.PlasmaType;
import com.sfb.samples.RomulanShips;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.PlasmaRack;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Which plasma rows a ship's SSD prints — the download ladder, and the one type that is not
 * on it.
 *
 * <p>Two rules pull in opposite directions, which is the whole reason this is computed in core
 * rather than derived by the client from the launchers it can see.
 *
 * <ul>
 *   <li><b>FP1.13</b> — a launcher "may be used to load a less powerful, but not a more
 *       powerful, torpedo than that for which it is rated". So one plasma-R launcher earns
 *       four rows, which is why a War Eagle's sheet prints R, S, G and F.</li>
 *   <li><b>FP1.133</b> — "Type-F and larger launchers cannot download a type-D torpedo." So
 *       type-D is never reached by downloading. A D row means the ship CARRIES type-D, in a
 *       plasma rack (FP10.0), and nothing about the launcher implies it.</li>
 * </ul>
 */
public class PlasmaLadderTest {

	private Ship ship(java.util.Map<String, Object> spec) {
		Ship s = new Ship();
		s.init(spec);
		return s;
	}

	private Ship withWeapon(java.util.Map<String, Object> spec, com.sfb.weapons.Weapon extra) {
		Ship s = ship(spec);
		s.getWeapons().addWeapon(extra);
		return s;
	}

	// ---------------------------------------------------------------- the ladder

	@Test
	public void aWarEaglesPlasmaRPrintsFourRows() {
		// The owner's own capture: one launcher, four warhead rows.
		assertEquals(List.of(PlasmaType.R, PlasmaType.S, PlasmaType.G, PlasmaType.F),
				ship(RomulanShips.getRomWe()).plasmaTypesAvailable());
	}

	@Test
	public void aKrPrintsItsLauncherAndEverythingBelow() {
		List<PlasmaType> rows = ship(RomulanShips.getRomKr()).plasmaTypesAvailable();

		assertEquals("the KR carries plasma-G, so G and F and nothing above",
				List.of(PlasmaType.G, PlasmaType.F), rows);
	}

	@Test
	public void theLadderIsStrongestFirstAndNeverSkipsARung() {
		// The order is the sheet's and the rows below are contiguous: a type-S launcher cannot
		// print S and F while omitting G, because it can load a G.
		PlasmaType[] ladder = { PlasmaType.R, PlasmaType.S, PlasmaType.G, PlasmaType.F };
		for (int top = 0; top < ladder.length; top++) {
			Ship s = withWeapon(RomulanShips.getRomKr(), new PlasmaLauncher(ladder[top]));
			List<PlasmaType> rows = s.plasmaTypesAvailable();
			List<PlasmaType> expected = new java.util.ArrayList<>();
			// The KR's own plasma-G is still aboard, so the ladder starts at the stronger of
			// the two launchers.
			int start = Math.min(top, 2);
			for (int i = start; i < ladder.length; i++)
				expected.add(ladder[i]);
			assertEquals("a type-" + ladder[top] + " launcher added to a KR", expected, rows);
		}
	}

	@Test
	public void theStrongestLauncherSetsTheTopRow() {
		// A ship with two launchers prints one ladder, headed by the bigger of them.
		Ship s = withWeapon(RomulanShips.getRomKr(), new PlasmaLauncher(PlasmaType.R));

		assertEquals(List.of(PlasmaType.R, PlasmaType.S, PlasmaType.G, PlasmaType.F),
				s.plasmaTypesAvailable());
	}

	@Test
	public void aShipWithNoPlasmaAtAllPrintsNoRows() {
		assertTrue(ship(com.sfb.samples.FederationShips.getFedCa()).plasmaTypesAvailable()
				.isEmpty());
	}

	// ---------------------------------------------------------------- type-D, FP1.133

	@Test
	public void aLauncherNeverReachesTypeDHoweverLargeItIs() {
		// The rule that makes this worth computing in core: the biggest launcher in the game
		// still cannot download a type-D, so no ladder ever ends in D on its own.
		assertFalse("a plasma-R launcher must not imply a type-D row",
				ship(RomulanShips.getRomWe()).plasmaTypesAvailable().contains(PlasmaType.D));
	}

	@Test
	public void aPlasmaRackIsWhatPutsTypeDOnTheSheet() {
		Ship racked = withWeapon(RomulanShips.getRomKr(), new PlasmaRack());

		assertEquals("D goes last, below the smallest downloadable type",
				List.of(PlasmaType.G, PlasmaType.F, PlasmaType.D),
				racked.plasmaTypesAvailable());
	}

	@Test
	public void aRackAloneOnAShipWithNoLauncherPrintsOnlyTypeD() {
		// Nothing to download from, but the ship can still put a type-D into space.
		Ship s = withWeapon(com.sfb.samples.FederationShips.getFedCa(), new PlasmaRack());

		assertEquals(List.of(PlasmaType.D), s.plasmaTypesAvailable());
	}

	// ---------------------------------------------------------------- ordering by rank

	@Test
	public void theLadderIsNotTheEnumsDeclarationOrder() {
		// PlasmaType declares D, F, G, K, S, R — alphabetical-ish and meaningless. Ordinal
		// would put D first and R last, inverting the sheet. This is why the ladder is an
		// explicit array, and this test is what notices if someone "simplifies" it.
		List<PlasmaType> rows = ship(RomulanShips.getRomWe()).plasmaTypesAvailable();

		assertEquals("strongest first", PlasmaType.R, rows.get(0));
		assertEquals("weakest last", PlasmaType.F, rows.get(rows.size() - 1));
		assertTrue("the enum's own order disagrees, which is the point",
				PlasmaType.D.ordinal() < PlasmaType.R.ordinal());
	}

	@Test
	public void typeKIsNotOnTheSheetsLadder() {
		// Type-K exists in the enum for another product's weapon and is not part of the basic
		// download ladder, so it must never appear as a row.
		assertFalse(ship(RomulanShips.getRomWe()).plasmaTypesAvailable().contains(PlasmaType.K));
	}
}
