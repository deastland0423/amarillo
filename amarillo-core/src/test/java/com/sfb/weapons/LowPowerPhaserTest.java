package com.sfb.weapons;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * E2.25 LOW-POWER: firing a bigger phaser as a phaser-3, and why a scout does it.
 *
 * <p>E2.251 — "Any ph-1, ph-2, or ph-4 can be fired as a ph-3 by using only 1/2-unit of
 * power." G24.1343 — "Phasers larger than phaser-3s can be fired as phaser-3s to avoid
 * blinding sensors." G24.34 states the trade: a scout "can fire its phaser-1 or phaser-2
 * weapons as phaser-3s in order to defend itself without blinding its channels… Any ship can
 * use (E2.25), but scouts have an extra reason to."
 *
 * <p>The payoff cost nothing to build: {@code DamageResolver} already tallies
 * {@code blindsScoutChannels()} over the weapons that fired, and {@code PhaserG} was already
 * the precedent for answering it from state recorded at firing time.
 */
public class LowPowerPhaserTest {

	private Game game;
	private Ship ship;

	@Before
	public void setUp() throws Exception {
		game = new Game();
		ship = new Ship();
		ship.init(FederationShips.getFedCa());
		ship.setName("USS Testbed");
		ship.setLocation(new Location(10, 10));
		ship.setFacing(1);
		game.getShips().add(ship);
		ship.attachClock(game.getClock());
		// Fill it to whatever this hull's capacitor actually holds: energy is not what
		// these tests are about, and over-filling throws.
		ship.chargeCapacitor(ship.getWeapons().getAvailablePhaserCapacitor());
	}

	/**
	 * A phaser with no owning ship, so firing draws on no capacitor. The damage-table tests
	 * fire fifty times to sample the die, which would drain any real hull dry — and the table
	 * is what they are about, not the energy.
	 */
	private Phaser1 barePh1() {
		Phaser1 p = new Phaser1();
		p.setClock(game.getClock());
		return p;
	}

	private Phaser1 mountedPh1() {
		Phaser1 p = new Phaser1();
		p.setClock(game.getClock());
		p.setOwningShip(ship);
		return p;
	}

	// ---------------------------------------------------------------- the energy, E2.251

	@Test
	public void aFullPowerPhaserOneCostsAPointAndALowPowerShotCostsHalf() {
		Phaser1 p = mountedPh1();

		assertEquals("full power", 1.0, p.energyToFire(), 0.0001);
		p.setFiringLowPower(true);
		assertEquals("as a ph-3 (E2.251)", 0.5, p.energyToFire(), 0.0001);
	}

	@Test
	public void aPhaserFourAlsoDropsToHalfAPointNotToHalfOfTwo() {
		// E2.251 gives the ph-3's cost, not a proportion of the firer's own.
		Phaser4 p = new Phaser4();
		p.setClock(game.getClock());

		assertEquals(2.0, p.energyToFire(), 0.0001);
		p.setFiringLowPower(true);
		assertEquals(0.5, p.energyToFire(), 0.0001);
	}

	// ---------------------------------------------------------------- the damage table

	@Test
	public void aLowPowerShotResolvesOnThePhaserThreeTable() throws Exception {
		// At range 0 a ph-1 scores 4 to 9 by die roll; a ph-3 scores 3 or 4. Firing fifty
		// shots low-power must never produce a number only the ph-1 table can make.
		for (int i = 0; i < 50; i++) {
			Phaser1 p = barePh1();
			p.setFiringLowPower(true);
			int damage = p.fire(0);
			assertTrue("a ph-3 at range 0 scores 3 or 4, got " + damage,
					damage == 3 || damage == 4);
		}
	}

	@Test
	public void aFullPowerShotIsUntouched() throws Exception {
		// The regression that matters most: the overwhelming majority of shots are normal
		// ones, and they must resolve exactly as they did before this existed.
		boolean sawAboveFour = false;
		for (int i = 0; i < 50; i++) {
			Phaser1 p = barePh1();
			int damage = p.fire(0);
			assertTrue("a ph-1 at range 0 scores 4 to 9, got " + damage,
					damage >= 4 && damage <= 9);
			sawAboveFour |= damage > 4;
		}
		assertTrue("fifty shots should have produced at least one above a ph-3's best",
				sawAboveFour);
	}

	@Test
	public void aLowPowerShotCannotReachPastAPhaserThreesRange() throws Exception {
		// Fired as a ph-3, it reaches as far as a ph-3 (15) and no further — even though the
		// ph-1 itself reaches 75. The weapon's own maximum must not be the one that applies.
		Phaser1 p = mountedPh1();
		p.setFiringLowPower(true);
		try {
			p.fire(20);
			fail("a low-power shot should not reach 20 hexes");
		} catch (com.sfb.exceptions.TargetOutOfRangeException expected) {
			assertTrue(expected.getMessage(), expected.getMessage().contains("E2.251"));
		}

		assertTrue("...while the same phaser at full power reaches it easily",
				mountedPh1().fire(20) >= 0);
	}

	// ---------------------------------------------------------------- the scout payoff

	@Test
	public void aPhaserFiredLowDoesNotBlindAScoutChannel() throws Exception {
		Phaser1 p = mountedPh1();
		p.setFiringLowPower(true);
		p.fire(1);

		assertFalse("G24.1343: fired as a ph-3, so G24.1341 exempts it",
				p.blindsScoutChannels());
	}

	@Test
	public void theSamePhaserFiredNormallyDoesBlind() throws Exception {
		Phaser1 p = mountedPh1();
		p.fire(1);

		assertTrue("G24.1342: everything not on the exempt list blinds",
				p.blindsScoutChannels());
	}

	@Test
	public void aRealPhaserThreeStillNeverBlinds() {
		// The weapon the rule exempts in the first place, unchanged by any of this.
		assertFalse(new Phaser3().blindsScoutChannels());
	}

	// ---------------------------------------------------------------- what it does NOT buy

	@Test
	public void aLowPowerShotStillSpendsThePhasersFiringForTheTurn() throws Exception {
		// E2.25 says this twice — "a low-powered firing counts as the single firing of that
		// phaser for that turn", then "does not imply that ph-1s, ph-2s, or ph-4s can be
		// fired more than once per turn in this mode". A rule stated twice is one somebody
		// tried to read the other way.
		Phaser1 p = mountedPh1();
		p.setFiringLowPower(true);
		p.fire(1);

		assertFalse("the phaser is spent, exactly as a full-power shot would leave it",
				p.canFire());
	}

	@Test
	public void theDeclarationIsPerShotAndDoesNotLinger() throws Exception {
		Phaser1 p = mountedPh1();
		p.setFiringLowPower(true);
		assertTrue(p.isFiringLowPower());
		p.fire(1);

		assertFalse("spent as the shot was taken", p.isFiringLowPower());
		assertEquals("so the next shot is priced at full power again",
				1.0, p.energyToFire(), 0.0001);
	}

	@Test
	public void aDeclarationThatMissesItsRangeIsStillSpent() throws Exception {
		// Otherwise a refused shot would leave the phaser quietly armed for low power and the
		// next one would land on the wrong table.
		Phaser1 p = mountedPh1();
		p.setFiringLowPower(true);
		try {
			p.fire(20);
			fail("expected the range refusal");
		} catch (com.sfb.exceptions.TargetOutOfRangeException expected) {
			// ...
		}

		assertFalse("the declaration went with the attempt", p.isFiringLowPower());
	}

	@Test
	public void aPhaserThatHasNotFiredLowPowerThisImpulseStillBlinds() {
		// firedLowPowerThisImpulse is keyed on the clock, so a shot taken last impulse does
		// not go on excusing this one.
		Phaser1 p = mountedPh1();
		assertFalse(p.firedLowPowerThisImpulse());
		assertTrue(p.blindsScoutChannels());
	}
}
