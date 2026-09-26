package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack.DroneRackType;

/**
 * The type-E drone rack (FD3.5): eight dogfight drones, four launches a turn, one reload.
 * <p>
 * Rare, and simple — but we had it as an ordinary rack. It inherited {@link Weapon}'s default
 * of one shot per turn, so three of its four launches were unreachable.
 * <p>
 * Its capacity was already right by accident of arithmetic: eight dogfight drones at half a
 * space each is the four spaces we store. The half-space is what makes the number work, so
 * the test says eight drones rather than four spaces.
 */
public class TypeERackTest {

    private DroneRack typeE() {
        return new DroneRack(DroneRackType.TYPE_E);
    }

    // ---------------------------------------------------------------- what it holds

    @Test
    public void itHoldsEightDogfightDrones() {
        DroneRack rack = typeE();

        double used = 0;
        int loaded = 0;
        while (used + DroneType.TypeVI.rack <= rack.getSpaces()) {
            rack.getAmmo().add(new Drone(DroneType.TypeVI));
            used += DroneType.TypeVI.rack;
            loaded++;
        }

        assertEquals("FD3.5: eight dogfight drones", 8, loaded);
        assertEquals("four spaces, at half a space each", 4, rack.getSpaces());
    }

    @Test
    public void itCarriesOneReload() {
        assertEquals(1, typeE().getNumberOfReloads());
    }

    /** FD3.5: "It can carry no other types." Nothing enforced this half of the rule. */
    @Test
    public void itCarriesNothingButDogfightDrones() {
        DroneRack rack = typeE();

        assertTrue("a dogfight drone is what it is for", rack.accepts(DroneType.TypeVI));
        assertTrue(rack.accepts(DroneType.TypeVIM));
        assertFalse("a Type-I has no business in an E rack", rack.accepts(DroneType.TypeI));
        assertFalse(rack.accepts(DroneType.TypeIV));
    }

    /** And the rule the other way about (FD2.51), which we did already enforce. */
    @Test
    public void onlyTheERackAndItsTwoCousinsTakeDogfightDrones() {
        assertTrue(new DroneRack(DroneRackType.TYPE_G).accepts(DroneType.TypeVI));
        assertTrue(new DroneRack(DroneRackType.TYPE_H).accepts(DroneType.TypeVI));

        assertFalse(new DroneRack(DroneRackType.TYPE_A).accepts(DroneType.TypeVI));
        assertFalse("FD3.4 says so outright for the D",
                new DroneRack(DroneRackType.TYPE_D).accepts(DroneType.TypeVI));

        assertTrue("and an ordinary drone is fine in an ordinary rack",
                new DroneRack(DroneRackType.TYPE_A).accepts(DroneType.TypeI));
    }

    // ---------------------------------------------------------------- how often it fires

    @Test
    public void itLaunchesFourTimesInATurn() {
        assertEquals("FD3.5: up to four drones per turn", 4, typeE().getMaxShotsPerTurn());
    }

    /**
     * Four launches, but still a quarter turn apart — which is the whole of the thirty-two
     * impulses. The rate and the gap are not in conflict; they are the same statement.
     */
    @Test
    public void thoseFourAreStillAQuarterTurnApart() {
        TurnTracker clock = new TurnTracker();
        DroneRack rack = typeE();
        rack.setClock(clock);
        for (int i = 0; i < 8; i++)
            rack.getAmmo().add(new Drone(DroneType.TypeVI));

        int launches = 0;
        for (int impulse = 0; impulse < 32; impulse++) {
            clock.nextImpulse();
            if (rack.canFire()) {
                rack.recordLaunch();
                launches++;
            }
        }

        assertEquals("eight impulses apart, across a 32-impulse turn", 4, launches);
    }

    /** A plain rack is unchanged by any of this: one launch a turn. */
    @Test
    public void anOrdinaryRackStillLaunchesOnce() {
        assertEquals(1, new DroneRack(DroneRackType.TYPE_A).getMaxShotsPerTurn());
    }
}
