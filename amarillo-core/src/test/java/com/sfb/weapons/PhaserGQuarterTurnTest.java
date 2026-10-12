package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.exceptions.WeaponUnarmedException;

/**
 * E2.151's quarter-turn window, which the per-turn cap does not imply.
 *
 * <p>"A gatling phaser cannot fire more than four shots per turn, nor can it fire more than four
 * shots within a 1/4-turn period. The 1/4-turn period can include some shots fired during the
 * final portion of one turn and some shots fired during the early portion of the subsequent
 * turn."
 *
 * <p>Two caps, and the second is the one a turn boundary hides: {@code shotsThisTurn} is zeroed by
 * {@code cleanUp}, so without a window that survives the boundary a gatling fires four on
 * impulses 29-32 and four more on 1-4 — eight inside a period the rule allows four. That was a
 * real gap here until 2026-10-11, found while fixing the targets half of the same sentence.
 *
 * <p>The window is eight impulses, and E1.52 fixes the arithmetic by worked example: a weapon
 * fired on Impulse #25 waits through #26 to #32 "and then could fire on Impulse #1 (eight impulses
 * later)". So a gap of exactly eight is legal, which is the off-by-one these tests pin.
 */
public class PhaserGQuarterTurnTest {

	private TurnTracker clock;
	private PhaserG phaser;

	@Before
	public void setUp() {
		clock = new TurnTracker();
		phaser = new PhaserG();
		phaser.setClock(clock);
		clock.nextImpulse();
	}

	/** Walk the clock forward, ending a turn the way the engine does. */
	private void advance(int impulses) {
		for (int i = 0; i < impulses; i++) {
			clock.nextImpulse();
			if (clock.getLocalImpulse() == 1)
				phaser.cleanUp();   // what Game does to every weapon at a turn boundary
		}
	}

	@Test
	public void fourShotsInOneImpulseStillFit() throws Exception {
		for (int shot = 1; shot <= 4; shot++) {
			assertTrue("shot " + shot + " of four", phaser.canFire());
			phaser.fire(5);
		}
		assertFalse(phaser.canFire());
		assertEquals(4, phaser.shotsInQuarterTurn());
	}

	/** The window is what refuses here — the turn cap has nothing to say yet. */
	@Test
	public void aFifthShotSevenImpulsesLaterIsRefused() throws Exception {
		for (int shot = 1; shot <= 4; shot++)
			phaser.fire(5);

		advance(7);

		assertEquals("all four are still inside the window", 4, phaser.shotsInQuarterTurn());
		assertFalse("seven impulses is not a quarter turn", phaser.canFire());
	}

	/** E1.52: eight impulses later is clear, not one short. */
	@Test
	public void theEighthImpulseClearsTheWindow() throws Exception {
		for (int shot = 1; shot <= 4; shot++)
			phaser.fire(5);

		advance(8);

		assertEquals("the oldest shots have aged out", 0, phaser.shotsInQuarterTurn());
	}

	/**
	 * The clause this test file exists for: a turn ending is not an amnesty.
	 *
	 * <p>Four shots at the end of one turn leave nothing for the start of the next, though
	 * cleanUp has zeroed the per-turn counter in between.
	 */
	@Test
	public void theWindowSurvivesTheTurnBoundary() throws Exception {
		advance(28);                      // impulse 29 of turn 1
		assertEquals(29, clock.getLocalImpulse());
		for (int shot = 1; shot <= 4; shot++)
			phaser.fire(5);

		advance(4);                       // impulse 1 of turn 2 — cleanUp has run

		assertEquals("a fresh turn's allowance", 0, phaser.getShotsThisTurn());
		assertEquals("but the window remembers", 4, phaser.shotsInQuarterTurn());
		assertFalse("so the gatling is still spent", phaser.canFire());
	}

	/** And it opens again once those shots age out, part way into the new turn. */
	@Test
	public void theNewTurnsShotsArriveAsTheOldOnesAgeOut() throws Exception {
		advance(28);
		for (int shot = 1; shot <= 4; shot++)
			phaser.fire(5);

		advance(8);                       // impulse 5 of turn 2

		assertEquals(5, clock.getLocalImpulse());
		assertEquals(0, phaser.shotsInQuarterTurn());
		assertTrue("a full turn's worth is available again", phaser.canFire());
		for (int shot = 1; shot <= 4; shot++)
			phaser.fire(5);
		assertFalse(phaser.canFire());
	}

	/** Spreading four shots across a turn never trips the window. */
	@Test
	public void shotsSpacedOutAreNeverRefused() throws Exception {
		for (int shot = 1; shot <= 4; shot++) {
			assertTrue("shot " + shot + " eight impulses after the last", phaser.canFire());
			phaser.fire(5);
			advance(8);
		}
	}

	/** The two caps are different facts, so they give different reasons. */
	@Test
	public void theWindowSaysSomethingDifferentFromTheTurnCap() throws Exception {
		advance(28);
		for (int shot = 1; shot <= 4; shot++)
			phaser.fire(5);
		advance(4);                       // new turn, per-turn counter zeroed

		try {
			phaser.fire(5);
			fail("the window should refuse this");
		} catch (WeaponUnarmedException expected) {
			assertTrue("a player told \"all 4 this turn\" on impulse 1 would think it a bug: "
					+ expected.getMessage(),
					expected.getMessage().contains("quarter turn"));
			assertTrue("and it should cite the rule: " + expected.getMessage(),
					expected.getMessage().contains("E2.151"));
		}
	}

	/** An ordinary weapon is untouched: the cap is off unless a weapon asks for it. */
	@Test
	public void nothingElseGetsAWindow() {
		Phaser1 ordinary = new Phaser1();
		ordinary.setClock(clock);
		assertEquals("E1.50 is minImpulseGap's job, not this one",
				0, ordinary.getMaxShotsPerQuarterTurn());
	}
}
