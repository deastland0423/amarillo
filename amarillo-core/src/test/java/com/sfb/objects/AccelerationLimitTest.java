package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.samples.FederationShips;

/**
 * C2.21 and C12.33's acceleration cap, and R1.7's exception to it.
 *
 * <h2>The rule, in two sentences from two places</h2>
 * C2.21: "the player may increase the ship's practical speed by a number which is equal to the
 * previous turn's speed, or ten, whichever is greater." C12.33: "The lowest speed during the
 * previous 32 impulses determines the maximum speed to which the ship can accelerate." Together
 * that is {@code max(lowest + floor, lowest * 2)}, held to 31 by C2.16.
 *
 * <h2>TWO exceptions, which the first draft of this file conflated</h2>
 * The sheets state the limit in two forms, and only one of them is C2.21's shape:
 * <ul>
 * <li><b>Q-ship:</b> "can accelerate by no more than five movement points or double its current
 *     speed. It can disengage by acceleration." — C2.21 with five for ten.</li>
 * <li><b>Plain freighter:</b> "cannot accelerate by more than 3 movement points per turn. This
 *     ship cannot disengage by acceleration." — a HARD cap, no doubling arm at all.</li>
 * </ul>
 * Reading the Q-ship sentence as though it were on every sheet let an F-S at speed 20 reach 31
 * where its sheet allows 23. So {@code accelDoubling} and {@code disengageByAccel} are facts of
 * their own, and both forms are pinned below.
 *
 * <p>Fifteen ship files declared {@code auxiliary.maxAccel} and nothing read it, because
 * ShipSpec had no such field and Jackson dropped it at parse time. A freighter could go from a
 * standing start to flank in one allocation.
 */
public class AccelerationLimitTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static Ship warship() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");
        return ship;
    }

    private static Ship fromLibrary(String faction, String type) {
        ShipSpec spec = ShipLibrary.get(faction, type);
        assertNotNull("fixture: " + faction + " " + type + " should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ship.setName(type);
        return ship;
    }

    /** Put the ship's speed history where the cap reads it from (C12.33's 32 impulses). */
    private static void withRecentSpeed(Ship ship, int speed) {
        ship.setSpeed(speed);
        ship.cleanUp();   // cleanUp rolls speedPreviousTurn / speedTwoTurnsAgo
        ship.setSpeed(speed);
        ship.cleanUp();
    }

    // ---------------------------------------------------------------- C2.21, the general rule

    /** From a standing start, ten — the floor arm of the maximum. */
    @Test
    public void aWarshipFromAStandingStartMayReachTen() {
        assertEquals(10, warship().getMaxAccelerationSpeed());
    }

    /** C2.21's own worked example: speed three becomes at most thirteen. */
    @Test
    public void speedThreeBecomesThirteen() {
        Ship ship = warship();
        withRecentSpeed(ship, 3);

        assertEquals("C2.21: three plus ten, because ten is the greater increase",
                13, ship.getMaxAccelerationSpeed());
    }

    /** And the second half of that example: thirteen becomes 26, by doubling. */
    @Test
    public void speedThirteenDoublesToTwentySix() {
        Ship ship = warship();
        withRecentSpeed(ship, 13);

        assertEquals("C2.21: once past ten, the increase is the current speed itself",
                26, ship.getMaxAccelerationSpeed());
    }

    /** C2.16 holds everything to 31 however generous the doubling gets. */
    @Test
    public void theCapIsThirtyOne() {
        Ship ship = warship();
        withRecentSpeed(ship, 20);

        assertEquals(31, ship.getMaxAccelerationSpeed());
    }

    /** C12.33: the LOWEST speed of the last two turns governs, not the latest. */
    @Test
    public void aMidBattleDipHoldsTheShipToTheDip() {
        Ship ship = warship();
        ship.setSpeed(20);
        ship.cleanUp();
        ship.setSpeed(4);       // dropped last turn
        ship.cleanUp();

        assertEquals("C12.33: the dip governs, so four plus ten rather than twenty doubled",
                14, ship.getMaxAccelerationSpeed());
    }

    // ---------------------------------------------------------------- R1.7, the exception

    @Test
    public void aWarshipHasTheOrdinaryFloorOfTen() {
        assertEquals(10, warship().getMaxAccel());
    }

    /**
     * THE TEST THIS WORK EXISTS FOR: a freighter's declared limit is actually read.
     *
     * Before this, {@code auxiliary.maxAccel} was dead data in fifteen files and a freighter
     * accelerated exactly like a cruiser.
     */
    @Test
    public void aQShipDeclaredFloorIsRead() {
        assertEquals("R1.7: five, from auxiliary.maxAccel",
                5, fromLibrary("Federation", "L-Q").getMaxAccel());
    }

    /**
     * And it is read as a VALUE, not as a flag meaning "freighter".
     *
     * The plain freighters declare three and four rather than five, which is the argument for
     * carrying the number through rather than branching on hull type. A note in BACKLOG.md said
     * eleven files declared "maxAccel: 5"; it is fifteen files, and they declare 5, 4 and 3.
     */
    @Test
    public void theFloorVariesByHullAndIsCarriedAsANumber() {
        assertEquals("the small freighter is slower still",
                3, fromLibrary("Neutral", "F-S").getMaxAccel());
        assertEquals(4, fromLibrary("Neutral", "F-L").getMaxAccel());
        assertEquals(3, fromLibrary("Neutral", "F-S").getMaxAccelerationSpeed());
    }

    /** And the floor is what changes the answer, from a standing start. */
    @Test
    public void aQShipFromAStandingStartReachesOnlyFive() {
        Ship freighter = fromLibrary("Federation", "L-Q");

        assertEquals("a freighter cannot go to flank in one allocation",
                5, freighter.getMaxAccelerationSpeed());
    }

    /**
     * A Q-SHIP gets the doubling arm, because its sheet grants it: "no more than five movement
     * points or double its current speed". At speed six, doubling (12) beats the floor (11).
     */
    @Test
    public void aQShipPastItsFloorDoublesLikeAnyoneElse() {
        Ship qship = fromLibrary("Federation", "L-Q");
        withRecentSpeed(qship, 6);

        assertEquals("or double its current speed", 12, qship.getMaxAccelerationSpeed());
    }

    /**
     * THE CORRECTION: a PLAIN FREIGHTER has a hard cap and never doubles.
     *
     * "This ship cannot accelerate by more than 3 movement points per turn" — and that is the
     * whole sentence. The first draft of this work let an F-S at speed 20 reach 31 by doubling;
     * its sheet allows 23.
     */
    @Test
    public void aPlainFreighterNeverDoubles() {
        Ship freighter = fromLibrary("Neutral", "F-S");
        withRecentSpeed(freighter, 20);

        assertEquals("a hard cap: twenty plus three, not twenty doubled",
                23, freighter.getMaxAccelerationSpeed());
    }

    /** Same rule on the large freighter, with its own number. */
    @Test
    public void theLargeFreighterIsAHardCapToo() {
        Ship freighter = fromLibrary("Neutral", "F-L");
        withRecentSpeed(freighter, 20);

        assertEquals(24, freighter.getMaxAccelerationSpeed());
    }

    /**
     * EVERY Q-ship doubles and may disengage by acceleration — all thirteen of them.
     *
     * <h2>Why this is a guard and not a comment</h2>
     * Both properties default to permissive, so these thirteen hulls say nothing at all in their
     * JSON and rely entirely on that default. Nothing would report it if someone added
     * {@code accelDoubling: false} to one of them, or if a future hull were copied from a
     * freighter instead of from a sibling Q-ship — the ship would simply be slower than its
     * sheet, quietly, for the rest of the project.
     *
     * <h2>The reason, from the owner (2026-10-19)</h2>
     * "All the Q-ships are secret military conversions. So they can all double and they can all
     * disengage by acceleration." Which is why only the two PLAIN freighters carry the
     * restrictions: a Q-ship is a warship wearing a freighter's hull.
     */
    @Test
    public void everyQShipDoublesAndMayDisengageByAcceleration() {
        int checked = 0;
        for (ShipSpec spec : ShipLibrary.all()) {
            String type = spec.type;
            if (type == null || !(type.equals("L-Q") || type.equals("S-Q")))
                continue;
            Ship qship = ShipLibrary.createShip(spec);
            String who = spec.faction + " " + type;
            assertEquals(who + " should carry R1.7's five-point floor", 5, qship.getMaxAccel());
            assertTrue(who + " must keep C2.21's doubling arm — a Q-ship is a warship in a"
                    + " freighter's hull", qship.getMaxAccelerationSpeed() >= 5);
            withRecentSpeed(qship, 6);
            assertEquals(who + " must double past its floor", 12,
                    qship.getMaxAccelerationSpeed());
            assertTrue(who + " must be able to disengage by acceleration",
                    qship.canDisengageByAcceleration());
            checked++;
        }
        assertEquals("fixture: all fifteen Q-ships should have been checked — if this number"
                + " moves, a hull was added or renamed and wants looking at", 15, checked);
    }

    // ---------------------------------------------------------------- C7.1, the other sentence

    /**
     * "This ship cannot disengage by acceleration" on a freighter, against "It can disengage by
     * acceleration" on the Q-ship converted from it. Stated on both sheets, so it is data and
     * not something to derive from warp power.
     */
    @Test
    public void aFreighterMayNotDisengageByAccelerationButAQShipMay() {
        assertFalse("the freighter sheet denies it",
                fromLibrary("Neutral", "F-S").canDisengageByAcceleration());
        assertFalse(fromLibrary("Neutral", "F-L").canDisengageByAcceleration());
        assertTrue("the Q-ship sheet allows it",
                fromLibrary("Federation", "L-Q").canDisengageByAcceleration());
        assertTrue("and so does an ordinary warship, by C7.11",
                warship().canDisengageByAcceleration());
    }

    /**
     * A Q-ship and a warship converge once both are doubling, which is what makes the Q-ship's
     * exception a FLOOR — where the freighter's is a different rule and never converges.
     */
    @Test
    public void aQShipConvergesWithAWarshipButAFreighterNever() {
        Ship warship = warship();
        Ship qship = fromLibrary("Federation", "L-Q");
        withRecentSpeed(warship, 20);
        withRecentSpeed(qship, 20);

        assertEquals("past the floor both are doubling, and both hit C2.16's 31",
                warship.getMaxAccelerationSpeed(), qship.getMaxAccelerationSpeed());

        Ship freighter = fromLibrary("Neutral", "F-S");
        withRecentSpeed(freighter, 20);
        assertNotEquals("the freighter has no doubling arm, so it stays behind",
                warship.getMaxAccelerationSpeed(), freighter.getMaxAccelerationSpeed());
    }
}
