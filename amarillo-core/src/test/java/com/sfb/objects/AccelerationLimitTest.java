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
 * <h2>Why R1.7 is one number and not a second rule</h2>
 * A freighter "can accelerate by no more than 5 movement points or double the current speed" —
 * the same expression with five in place of ten, which is what C12.33 means by "Some unit types
 * have different acceleration limits". Fifteen ship files declared {@code auxiliary.maxAccel: 5}
 * and nothing read it, because ShipSpec had no such field and Jackson dropped it at parse time.
 * A freighter could go from a standing start to flank in one allocation.
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
    public void aFreighterDeclaredFloorIsRead() {
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
    public void aFreighterFromAStandingStartReachesOnlyFive() {
        Ship freighter = fromLibrary("Federation", "L-Q");

        assertEquals("a freighter cannot go to flank in one allocation",
                5, freighter.getMaxAccelerationSpeed());
    }

    /**
     * R1.7's doubling arm needs no separate code: it is the other half of the same maximum.
     * At speed six, doubling (12) beats the five-point floor (11).
     */
    @Test
    public void aFreighterPastItsFloorDoublesLikeAnyoneElse() {
        Ship freighter = fromLibrary("Federation", "L-Q");
        withRecentSpeed(freighter, 6);

        assertEquals("R1.7 again: or double the current speed",
                12, freighter.getMaxAccelerationSpeed());
    }

    /** The two hulls differ only where the floor bites, which is the point of the exception. */
    @Test
    public void theDifferenceIsTheFloorAndNothingElse() {
        Ship warship = warship();
        Ship freighter = fromLibrary("Federation", "L-Q");
        withRecentSpeed(warship, 20);
        withRecentSpeed(freighter, 20);

        assertEquals("past the floor both are doubling, and both hit C2.16's 31",
                warship.getMaxAccelerationSpeed(), freighter.getMaxAccelerationSpeed());
    }
}
