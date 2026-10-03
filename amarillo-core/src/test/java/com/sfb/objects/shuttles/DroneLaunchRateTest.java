package com.sfb.objects.shuttles;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;

/**
 * How many drones a fighter may let go, and how close together (J4.24, J4.241, J4.242).
 * <p>
 * J4.24 is the baseline: one a turn, and never two within a quarter turn — a span measured
 * from the launch, so it reaches across the turn boundary.
 * <p>
 * J4.241 buys a second: "any fighter can launch two drones per turn (or within 1/4 turn) if:
 * A-both are launched at the same target; and B-one (or both) of them is a dogfight
 * (type-VI) drone." The AND is the whole rule. Read as an OR it would let a fighter split
 * two standard drones between two targets, which is the case the rule exists to forbid.
 * <p>
 * J4.242 then exempts particular fighters. The TAAS is the one the BEHAVIOUR tests below are
 * written against; the roster section near the end covers which other fighters hold which
 * exemption, the F-14's B-without-A being the one most easily misread. It may violate
 * B in any case, and A whenever the two do not leave on the same impulse.
 */
public class DroneLaunchRateTest {

    private static Ship target(String name, int col) {
        Ship s = new Ship();
        s.init(com.sfb.samples.FederationShips.getFedCa());
        s.setName(name);
        s.setLocation(new Location(col, 10));
        return s;
    }

    private static Drone standard() {
        return new Drone(DroneType.TypeI);
    }

    private static Drone dogfight() {
        return new Drone(DroneType.TypeVI);
    }

    // -------------------------------------------------------------------------
    // J4.24: the baseline
    // -------------------------------------------------------------------------

    @Test
    public void theFirstDroneOfATurnIsFree() {
        assertNull(CataloguedFighter.of("aas").droneLaunchRefusal(target("A", 11), standard(), 5));
    }

    @Test
    public void aSecondStandardDroneAtTheSameTargetIsStillRefused() {
        Fighter aas = CataloguedFighter.of("aas");
        Ship t = target("A", 11);
        aas.recordDroneFired(t, standard(), 5);

        String why = aas.droneLaunchRefusal(t, standard(), 6);

        assertNotNull("same target satisfies A, but neither is a dogfight drone", why);
        assertTrue(why, why.contains("J4.241"));
    }

    // -------------------------------------------------------------------------
    // J4.241: A and B, both of them
    // -------------------------------------------------------------------------

    @Test
    public void aDogfightDroneAtTheSameTargetBuysTheSecondLaunch() {
        Fighter aas = CataloguedFighter.of("aas");
        Ship t = target("A", 11);
        aas.recordDroneFired(t, standard(), 5);

        assertNull("same target, and one of the pair is a type-VI",
                aas.droneLaunchRefusal(t, dogfight(), 6));
    }

    @Test
    public void itCountsIfTheFirstOneWasTheDogfightDrone() {
        Fighter aas = CataloguedFighter.of("aas");
        Ship t = target("A", 11);
        aas.recordDroneFired(t, dogfight(), 5);

        assertNull("B says one OR BOTH", aas.droneLaunchRefusal(t, standard(), 6));
    }

    @Test
    public void aDifferentTargetBreaksConditionAEvenWithADogfightDrone() {
        Fighter aas = CataloguedFighter.of("aas");
        aas.recordDroneFired(target("A", 11), standard(), 5);

        String why = aas.droneLaunchRefusal(target("B", 12), dogfight(), 6);

        assertNotNull("B is satisfied but A is not, and both are required", why);
        assertTrue(why, why.contains("J4.241"));
    }

    /** J4.241 lifts the spacing as well as the count: "two per turn (or within 1/4 turn)". */
    @Test
    public void aQualifyingSecondDroneNeedNotWaitTheQuarterTurn() {
        Fighter aas = CataloguedFighter.of("aas");
        Ship t = target("A", 11);
        aas.recordDroneFired(t, dogfight(), 5);

        assertNull("the very next impulse", aas.droneLaunchRefusal(t, standard(), 6));
    }

    @Test
    public void aThirdDroneIsNeverAllowed() {
        Fighter aas = CataloguedFighter.of("aas");
        Ship t = target("A", 11);
        aas.recordDroneFired(t, standard(), 5);
        aas.recordDroneFired(t, dogfight(), 6);

        String why = aas.droneLaunchRefusal(t, standard(), 7);

        assertNotNull(why);
        assertTrue(why, why.contains("two drones"));
    }

    // -------------------------------------------------------------------------
    // J4.242: the TAAS
    // -------------------------------------------------------------------------

    @Test
    public void aTaasMayLaunchTwoStandardDronesAtOneTarget() {
        Fighter taas = CataloguedFighter.of("taas");
        Ship t = target("A", 11);
        taas.recordDroneFired(t, standard(), 5);

        assertNull("J4.242 waives B for a TAAS in any case",
                taas.droneLaunchRefusal(t, standard(), 6));
    }

    @Test
    public void aTaasMaySplitItsPairIfTheyLeaveOnDifferentImpulses() {
        Fighter taas = CataloguedFighter.of("taas");
        taas.recordDroneFired(target("A", 11), standard(), 5);

        assertNull("J4.242 waives A when they do not go on the same impulse",
                taas.droneLaunchRefusal(target("B", 12), standard(), 6));
    }

    @Test
    public void aTaasStillMayNotSplitThemOnTheSameImpulse() {
        Fighter taas = CataloguedFighter.of("taas");
        taas.recordDroneFired(target("A", 11), standard(), 5);

        String why = taas.droneLaunchRefusal(target("B", 12), standard(), 5);

        assertNotNull("the exemption is conditional on the impulse differing", why);
        assertTrue(why, why.contains("J4.241"));
    }

    @Test
    public void anAasHasNoneOfThoseExemptions() {
        Fighter aas = CataloguedFighter.of("aas");

        assertFalse(aas.mayLaunchAtDifferentTargets());
        assertFalse(aas.mayLaunchTwoStandardDrones());
    }

    @Test
    public void aTaasHasBoth() {
        Fighter taas = CataloguedFighter.of("taas");

        assertTrue(taas.mayLaunchAtDifferentTargets());
        assertTrue(taas.mayLaunchTwoStandardDrones());
    }

    // -------------------------------------------------------------------------
    // J4.242's roster: WHICH fighters have WHICH exemption
    // -------------------------------------------------------------------------

    /**
     * The subtle one. J4.242: "the F-14 (which can ignore restriction B...)" — B only. It
     * may launch two standard drones, but they must still go at the SAME target, because the
     * rule grants it nothing against condition A.
     * <p>
     * Easy to get wrong in both directions: the F-15 two lines later gets both, and a reader
     * skimming the sentence grants the F-14 the pair. These fighters went years with neither
     * flag set, which nothing caught, because the flags live in hand-authored data.
     */
    @Test
    public void theF14IgnoresRestrictionBAndOnlyB() {
        for (String type : new String[] { "f14", "f14a", "f14b", "f14_e" }) {
            Fighter f = CataloguedFighter.of(type);
            assertNotNull(type + " is not in the catalogue", f);
            assertTrue(type + ": J4.242 waives B for the F-14",
                    f.mayLaunchTwoStandardDrones());
            assertFalse(type + ": J4.242 does NOT waive A for the F-14 — its pair still"
                    + " goes at one target", f.mayLaunchAtDifferentTargets());
        }
    }

    /**
     * J4.242: "the F-15 and TAAS (which can violate A if the drones are not launched on the
     * same impulse, and which can violate B in any case)". Both, like the TAAS above — and
     * the A exemption is conditional on the impulse, which
     * {@code aTaasStillMayNotSplitThemOnTheSameImpulse} pins.
     */
    @Test
    public void theF15GetsBothExemptions() {
        for (String type : new String[] { "f15", "f15c", "f15_e" }) {
            Fighter f = CataloguedFighter.of(type);
            assertNotNull(type + " is not in the catalogue", f);
            assertTrue(type + ": A", f.mayLaunchAtDifferentTargets());
            assertTrue(type + ": B", f.mayLaunchTwoStandardDrones());
        }
    }

    /** J4.242: "the Z-Y (which can violate both)". */
    @Test
    public void theZyGetsBothExemptions() {
        for (String type : new String[] { "zy", "zy_e", "zyb", "zyc" }) {
            Fighter f = CataloguedFighter.of(type);
            assertNotNull(type + " is not in the catalogue", f);
            assertTrue(type + ": A", f.mayLaunchAtDifferentTargets());
            assertTrue(type + ": B", f.mayLaunchTwoStandardDrones());
        }
    }

    /**
     * The A-10 gets NEITHER, which its cross-reference makes easy to assume otherwise.
     * J4.242 ends "The A-10 is covered under (J10.43)", and J10.43 turns out to be about
     * something else entirely: "MULTIPLE WEAPONS: There is no restriction or interaction
     * between firing weapons of different types... These limits also apply to the A-10." That
     * is about firing its photon AND its drones in one impulse, not about the two-drone
     * conditions, so the A-10 keeps J4.241 in full.
     */
    @Test
    public void theA10GetsNeitherExemption() {
        for (String type : new String[] { "a10", "a10_e" }) {
            Fighter f = CataloguedFighter.of(type);
            assertNotNull(type + " is not in the catalogue", f);
            assertFalse(type + ": J10.43 is about multiple weapon types, not J4.241's A",
                    f.mayLaunchAtDifferentTargets());
            assertFalse(type + ": nor its B", f.mayLaunchTwoStandardDrones());
        }
    }

    // -------------------------------------------------------------------------
    // J4.241's hard cap, which survives every exemption
    // -------------------------------------------------------------------------

    /**
     * "No fighter can exceed the per-turn rate within a 1/4-turn period (of two consecutive
     * turns)." Not enforced by a rule of its own here: it falls out of J4.24's spacing
     * being measured from the LAST LAUNCH rather than from the turn boundary, so the first
     * drone of a new turn still owes the quarter turn.
     * <p>
     * Pinned because that is a load-bearing accident. A future change that reset the
     * spacing at startTurn would satisfy every other test in this file and quietly let a
     * TAAS put four drones up inside eight impulses.
     */
    @Test
    public void twoInOneTurnDoesNotBuyTwoMoreImmediatelyInTheNext() {
        Fighter taas = CataloguedFighter.of("taas");
        Ship t = target("A", 11);
        taas.recordDroneFired(t, standard(), 31);
        taas.recordDroneFired(t, standard(), 32);   // its J4.242 pair, legally

        taas.startTurn();                           // a new turn, the count resets

        assertNotNull("one impulse later is not a quarter turn",
                taas.droneLaunchRefusal(t, standard(), 33));
        assertNotNull("nor is seven", taas.droneLaunchRefusal(t, standard(), 39));
        assertNull("eight impulses from the last launch, and it may go again",
                taas.droneLaunchRefusal(t, standard(), 40));
    }
}
