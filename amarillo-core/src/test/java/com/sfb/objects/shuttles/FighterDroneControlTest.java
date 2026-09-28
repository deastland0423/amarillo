package com.sfb.objects.shuttles;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.DroneType;

/**
 * How many seeking weapons a fighter can guide (J4.25, J4.43).
 * <p>
 * The rule is one sentence with three traps in it: "a number of drones equal to the number
 * of non-DFDs (non-dogfight drones, i.e., drones other than type-VI) in its nominal load
 * exclusive of variants, if any (or two drones, whichever is greater)."
 * <p>
 * Type-VIs do not count. It is the NOMINAL load, so swapping one drone for another does not
 * change it. And there is a floor of two. Each of those was got wrong at least once by the
 * hand-written per-class constants this replaced — a TAAS declared four when the rule gives
 * it two, and an EW fighter declared two when J4.43 gives it twelve.
 */
public class FighterDroneControlTest {

    @Test
    public void aStandardTwoRailFighterGuidesTwo() {
        assertEquals("two type-I rails, two non-dogfight drones", 2,
                new Aas().getControlCapacity());
        assertEquals(2, new Haas().getControlCapacity());
    }

    /**
     * The one the old constant got wrong. A TAAS carries two type-Is and two type-VIs; the
     * type-VIs are dogfight drones and J4.25 does not count them.
     */
    @Test
    public void dogfightDronesDoNotCountTowardsControl() {
        Taas taas = new Taas();

        assertEquals("four rails", 4, taas.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.DroneRail).count());
        assertEquals("but only two of them nominally carry a non-DFD", 2,
                taas.getControlCapacity());
    }

    /** J4.43: a two-seat fighter guides twelve, and EW fighters keep that ability. */
    @Test
    public void aTwoSeatFighterGuidesTwelve() {
        Haas_E ew = new Haas_E();

        assertTrue("the EW variant is a two-seater", ew.isTwoSeater());
        assertEquals(Fighter.TWO_SEAT_CONTROL, ew.getControlCapacity());
        assertEquals(12, ew.getControlCapacity());
    }

    /**
     * "Exclusive of variants": the capacity comes off the NOMINAL load, so substituting a
     * type-VI onto a standard rail (J4.2311 allows it freely) does not shrink it. J4.25's
     * own F-15 example is exactly this case.
     */
    @Test
    public void substitutingADogfightDroneDoesNotShrinkTheCapacity() {
        Aas aas = new Aas();
        for (com.sfb.weapons.Weapon w : aas.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.DroneRail rail)
                rail.loadDrone(new com.sfb.objects.Drone(DroneType.TypeVI));

        assertEquals("still two: the nominal load is what counts", 2,
                aas.getControlCapacity());
    }

    /** A fighter that carries no drones at all guides nothing; the floor is not for it. */
    @Test
    public void aFighterWithNoRailsGuidesNothing() {
        assertEquals(0, new Stinger1().getControlCapacity());
    }

    /**
     * The owner's worked example: every rail that is not a LIGHT one is a channel, because
     * the light rail is the one built for dogfight drones (J4.232). Two standard, two heavy
     * and two light rails guide four.
     */
    @Test
    public void everyRailThatIsNotALightOneIsAChannel() {
        Fighter mixed = new Fighter() {
            {
                addRail(com.sfb.weapons.DroneRail.DroneRailType.STANDARD, "A");
                addRail(com.sfb.weapons.DroneRail.DroneRailType.STANDARD, "B");
                addRail(com.sfb.weapons.DroneRail.DroneRailType.HEAVY, "C");
                addRail(com.sfb.weapons.DroneRail.DroneRailType.HEAVY, "D");
                addRail(com.sfb.weapons.DroneRail.DroneRailType.LIGHT, "E");
                addRail(com.sfb.weapons.DroneRail.DroneRailType.LIGHT, "F");
            }

            private void addRail(com.sfb.weapons.DroneRail.DroneRailType type, String tag) {
                com.sfb.weapons.DroneRail rail = new com.sfb.weapons.DroneRail(type);
                rail.setDesignator(tag);
                getWeapons().addWeapon(rail);
            }
        };

        assertEquals("four non-light rails, four channels", 4, mixed.getControlCapacity());
    }

    /** A special rail carries a type-I or type-III (J4.233), so it is a channel too. */
    @Test
    public void aSpecialRailCountsAsWell() {
        com.sfb.weapons.DroneRail special =
                new com.sfb.weapons.DroneRail(com.sfb.weapons.DroneRail.DroneRailType.SPECIAL);
        assertNotEquals("a special rail is not a light one",
                com.sfb.weapons.DroneRail.DroneRailType.LIGHT, special.getRailType());
    }

    // -------------------------------------------------------------------------
    // J4.24: how OFTEN, as opposed to how many
    // -------------------------------------------------------------------------

    /**
     * "A fighter can always launch one drone per turn... but cannot launch two drones on
     * consecutive turns within 1/4 turn (eight impulses) of each other."
     * <p>
     * Two limits, and the per-turn flag only catches one. A flag that resets at the turn
     * boundary lets a fighter fire on impulse 30 and again on impulse 2 — four impulses
     * apart, across the very boundary the flag is watching.
     */
    @Test
    public void twoDronesMayNotLeaveWithinAQuarterTurn() {
        Aas aas = new Aas();

        aas.recordDroneFired(null, new com.sfb.objects.Drone(DroneType.TypeI), 30);

        assertEquals("eight impulses from the launch", 8, aas.impulsesUntilNextDrone(30));
        assertEquals("four impulses later, across a turn boundary, four still to wait",
                4, aas.impulsesUntilNextDrone(34));
        assertEquals(0, aas.impulsesUntilNextDrone(38));
    }

    @Test
    public void aFighterThatHasNeverLaunchedIsNotWaiting() {
        assertEquals(0, new Aas().impulsesUntilNextDrone(1));
    }

    /** The per-turn flag still clears at the turn boundary; the spacing does not. */
    @Test
    public void theTurnFlagClearsButTheSpacingOutlivesIt() {
        Aas aas = new Aas();
        aas.recordDroneFired(null, new com.sfb.objects.Drone(DroneType.TypeI), 30);
        assertTrue(aas.isDronesFiredThisTurn());

        aas.startTurn();

        assertFalse("a new turn, a new launch allowed by the per-turn rule",
                aas.isDronesFiredThisTurn());
        assertEquals("but the quarter turn is measured from the launch, not the turn",
                4, aas.impulsesUntilNextDrone(34));
    }
}
