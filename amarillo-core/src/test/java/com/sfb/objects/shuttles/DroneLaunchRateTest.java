package com.sfb.objects.shuttles;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;

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
 * J4.242 then exempts particular fighters, and the TAAS is the one we have: it may violate
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
        assertNull(new Aas().droneLaunchRefusal(target("A", 11), standard(), 5));
    }

    @Test
    public void aSecondStandardDroneAtTheSameTargetIsStillRefused() {
        Aas aas = new Aas();
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
        Aas aas = new Aas();
        Ship t = target("A", 11);
        aas.recordDroneFired(t, standard(), 5);

        assertNull("same target, and one of the pair is a type-VI",
                aas.droneLaunchRefusal(t, dogfight(), 6));
    }

    @Test
    public void itCountsIfTheFirstOneWasTheDogfightDrone() {
        Aas aas = new Aas();
        Ship t = target("A", 11);
        aas.recordDroneFired(t, dogfight(), 5);

        assertNull("B says one OR BOTH", aas.droneLaunchRefusal(t, standard(), 6));
    }

    @Test
    public void aDifferentTargetBreaksConditionAEvenWithADogfightDrone() {
        Aas aas = new Aas();
        aas.recordDroneFired(target("A", 11), standard(), 5);

        String why = aas.droneLaunchRefusal(target("B", 12), dogfight(), 6);

        assertNotNull("B is satisfied but A is not, and both are required", why);
        assertTrue(why, why.contains("J4.241"));
    }

    /** J4.241 lifts the spacing as well as the count: "two per turn (or within 1/4 turn)". */
    @Test
    public void aQualifyingSecondDroneNeedNotWaitTheQuarterTurn() {
        Aas aas = new Aas();
        Ship t = target("A", 11);
        aas.recordDroneFired(t, dogfight(), 5);

        assertNull("the very next impulse", aas.droneLaunchRefusal(t, standard(), 6));
    }

    @Test
    public void aThirdDroneIsNeverAllowed() {
        Aas aas = new Aas();
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
        Taas taas = new Taas();
        Ship t = target("A", 11);
        taas.recordDroneFired(t, standard(), 5);

        assertNull("J4.242 waives B for a TAAS in any case",
                taas.droneLaunchRefusal(t, standard(), 6));
    }

    @Test
    public void aTaasMaySplitItsPairIfTheyLeaveOnDifferentImpulses() {
        Taas taas = new Taas();
        taas.recordDroneFired(target("A", 11), standard(), 5);

        assertNull("J4.242 waives A when they do not go on the same impulse",
                taas.droneLaunchRefusal(target("B", 12), standard(), 6));
    }

    @Test
    public void aTaasStillMayNotSplitThemOnTheSameImpulse() {
        Taas taas = new Taas();
        taas.recordDroneFired(target("A", 11), standard(), 5);

        String why = taas.droneLaunchRefusal(target("B", 12), standard(), 5);

        assertNotNull("the exemption is conditional on the impulse differing", why);
        assertTrue(why, why.contains("J4.241"));
    }

    @Test
    public void anAasHasNoneOfThoseExemptions() {
        Aas aas = new Aas();

        assertFalse(aas.mayLaunchAtDifferentTargets());
        assertFalse(aas.mayLaunchTwoStandardDrones());
    }

    @Test
    public void aTaasHasBoth() {
        Taas taas = new Taas();

        assertTrue(taas.mayLaunchAtDifferentTargets());
        assertTrue(taas.mayLaunchTwoStandardDrones());
    }
}
