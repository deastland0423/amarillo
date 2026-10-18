package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;

/**
 * E1.5 FIRING RATES — the quarter-turn gap spans the turn boundary.
 *
 * <h2>The rule, in its own words</h2>
 * E1.50 BASIC RULE: "No weapon may be fired twice within a period of one-fourth of a turn. (Note
 * that, in most cases, this involves firing the weapon on two consecutive turns.) For example, if
 * a specific phaser were fired during Impulse #29 of one turn, it could not be fired again before
 * Impulse #5 of the next turn. This rule is NOT to be interpreted as meaning that a weapon can be
 * fired more than once per turn. It is intended to eliminate the unrealistic tactic of firing a
 * 'full broadside' on Impulse #32 of one turn and then repeating it on Impulse #1 of the next."
 *
 * <p>E1.52 counts it out: a weapon fired on Impulse #25 "must wait through Impulses #26 (one),
 * #27 (two), #28 (three), #29 (four), #30 (five), #31 (six), #32 (seven), and then could fire on
 * Impulse #1 (eight impulses later) of the next turn." The rulebook names #25 the "Impulse of
 * Decision" and #1 the "Impulse of Truth" for exactly this reason.
 *
 * <h2>The bug this pins</h2>
 * {@code Weapon.cleanUp()} cleared {@code lastImpulseFired} at the start of every turn, so every
 * weapon began each turn free of the gap — which is the broadside E1.50 exists to forbid. Found
 * in a playtest: a disruptor fired on Turn 1 Impulse 30 was offered again on Turn 2 Impulse 1,
 * three impulses later.
 *
 * <p>The comment that stood over that line argued the clearing was "right for a phaser, which
 * would otherwise be locked out of the first eight impulses after firing late in the turn
 * before". That is the rule, not a problem with it — and the rule's own worked example is a
 * phaser. Only the drone rack had been given the correct behaviour, through FD3.0, which says
 * the same thing again for racks and so hid the fact that the general rule was missing.
 */
public class FiringRateAcrossTurnsTest {

    private TurnTracker clock;
    private Phaser1 phaser;

    @Before
    public void setUp() {
        clock = new TurnTracker();
        phaser = new Phaser1();
        phaser.setClock(clock);
    }

    /** Move the clock to an absolute impulse. */
    private void clockTo(int absoluteImpulse) {
        while (clock.getImpulse() < absoluteImpulse)
            clock.nextImpulse();
    }

    /** What the engine does between turns. */
    private void endOfTurn() {
        phaser.cleanUp();
    }

    // ---------------------------------------------------------------- the rulebook's examples

    /**
     * E1.50's own example: a phaser fired on Impulse #29 "could not be fired again before
     * Impulse #5 of the next turn".
     */
    @Test
    public void aPhaserFiredOnImpulse29CannotFireBeforeImpulse5OfTheNextTurn() {
        clockTo(29);
        phaser.setLastImpulseFired(29);
        endOfTurn();

        // Impulse #1 of the next turn is absolute 33, and #4 is absolute 36.
        for (int local = 1; local <= 4; local++) {
            clockTo(32 + local);
            assertFalse("impulse #" + local + " of the next turn is still inside the gap",
                    phaser.canFire());
        }

        clockTo(37); // Impulse #5 of the next turn
        assertTrue("E1.50 names impulse #5 as the first it may fire again", phaser.canFire());
    }

    /**
     * E1.52's count: fired on #25, waits through #26-#32, may fire on #1 of the next turn —
     * the "Impulse of Decision" and the "Impulse of Truth".
     */
    @Test
    public void theImpulseOfDecisionIsTwentyFive() {
        clockTo(25);
        phaser.setLastImpulseFired(25);
        endOfTurn();

        clockTo(33); // Impulse #1 of the next turn, eight impulses later
        assertTrue("fired on #25, a weapon may fire again on #1 of the next turn",
                phaser.canFire());
    }

    /** And one impulse later than the Impulse of Decision is one impulse too late. */
    @Test
    public void firingOnImpulse26CostsTheImpulseOfTruth() {
        clockTo(26);
        phaser.setLastImpulseFired(26);
        endOfTurn();

        clockTo(33);
        assertFalse("fired on #26, it cannot fire on #1 of the next turn", phaser.canFire());
        clockTo(34);
        assertTrue("but it can on #2", phaser.canFire());
    }

    // ---------------------------------------------------------------- the reported case

    /** The playtest: fired Turn 1 Impulse 30, offered again on Turn 2 Impulse 1. */
    @Test
    public void theFullBroadsideAcrossTheTurnBoundaryIsRefused() {
        clockTo(30);
        phaser.setLastImpulseFired(30);
        endOfTurn();

        clockTo(33);
        assertFalse("three impulses is not a quarter turn", phaser.canFire());

        clockTo(38); // Impulse #6 of the next turn
        assertTrue("eight impulses after firing, it is free again", phaser.canFire());
    }

    /** The worst case E1.50 names outright: #32 then #1. */
    @Test
    public void impulse32ThenImpulse1IsRefused() {
        clockTo(32);
        phaser.setLastImpulseFired(32);
        endOfTurn();

        clockTo(33);
        assertFalse("this is the exact tactic E1.50 was written to eliminate", phaser.canFire());
    }

    // ---------------------------------------------------------------- what the turn DOES reset

    /**
     * The per-turn shot COUNTER still resets, and must: E1.50 says plainly that it "is NOT to be
     * interpreted as meaning that a weapon can be fired more than once per turn", so the two
     * limits are separate and only one of them is about the clock.
     */
    @Test
    public void theShotCounterStillResetsWithTheTurn() throws Exception {
        // Fired through registerFire rather than by setting counters, so this exercises the
        // real path: it stamps the timestamp AND advances the per-turn counter together.
        clockTo(5);
        phaser.fire(1);
        assertEquals("fixture: one shot taken", 1, phaser.getShotsThisTurn());
        assertFalse("already fired this turn", phaser.canFire());

        endOfTurn();
        clockTo(33);

        assertEquals("the per-turn counter is not carried over", 0, phaser.getShotsThisTurn());
        assertTrue("and long past the gap, it may fire again", phaser.canFire());
    }

    /** A weapon that never fired is never blocked, whatever the clock says. */
    @Test
    public void aWeaponThatHasNeverFiredIsAlwaysReady() {
        clockTo(33);
        endOfTurn();

        assertTrue(phaser.canFire());
    }
}
