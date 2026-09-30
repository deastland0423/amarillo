package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * FD2.54 LIMITED DAMAGE: what a dogfight drone does depends on what it hits.
 * <p>
 * "Dogfight drones score two points of damage on size class 4 and larger targets (ships, bases,
 * monsters, asteroids, planets). This is because the tiny warhead is designed to score a direct
 * hit on a fighter engine instead of damaging the shields of a ship." Four points on size class
 * 5, and the printed eight only on size class 6 and 7 — shuttles, fighters, satellites, mines.
 * <p>
 * Nothing implemented this. {@code Drone.impact()} returned the raw warhead whatever it struck, so
 * a type-VI hit a ship for eight — four times its due, on a drone costing half a rack space. That
 * made dogfight drones strictly better than type-Is against ships, which is the exact opposite of
 * what the rule is for.
 */
public class DogfightDroneDamageTest {

    private Drone dogfight() {
        return new Drone(DroneType.TypeVI);
    }

    private Drone standard() {
        return new Drone(DroneType.TypeI);
    }

    private Unit atSizeClass(int sizeClass) {
        Unit u = new Unit() { };
        u.setSizeClass(sizeClass);
        return u;
    }

    /** The bug: a ship is size class 4 or larger, so two points and not eight. */
    @Test
    public void aDogfightDroneScoresTwoOnAShip() {
        Drone vi = dogfight();
        assertEquals("the printed warhead is still eight", 8, vi.impact());

        for (int sizeClass = 1; sizeClass <= 4; sizeClass++)
            assertEquals("size class " + sizeClass + " is a ship-sized target (FD2.54)",
                    2, vi.impact(atSizeClass(sizeClass)));
    }

    /** Size class 5 — PFs, interceptors, GBDPs — take four. */
    @Test
    public void aDogfightDroneScoresFourOnSizeClassFive() {
        assertEquals(4, dogfight().impact(atSizeClass(5)));
    }

    /** And the full warhead only against what it was built for. */
    @Test
    public void aDogfightDroneScoresEightOnAShuttleOrFighter() {
        assertEquals(8, dogfight().impact(atSizeClass(6)));
        assertEquals(8, dogfight().impact(atSizeClass(7)));
    }

    /** Every other drone ignores its target: a type-I does twelve to anything. */
    @Test
    public void anOrdinaryDroneDoesNotCareWhatItHits() {
        Drone one = standard();
        for (int sizeClass = 1; sizeClass <= 7; sizeClass++)
            assertEquals("size class " + sizeClass,
                    12, one.impact(atSizeClass(sizeClass)));
    }

    /** The whole type-VI family is limited, not just the slow one. */
    @Test
    public void everyDogfightDroneIsLimited() {
        for (DroneType t : new DroneType[] {
                DroneType.TypeVI, DroneType.TypeVIM, DroneType.TypeVIF }) {
            assertTrue(t + " should be a dogfight drone", t.isDogfightDrone());
            assertEquals(t + " vs a ship", 2, new Drone(t).impact(atSizeClass(3)));
            assertEquals(t + " vs a fighter", 8, new Drone(t).impact(atSizeClass(6)));
        }
    }

    /** A null target falls back to the warhead rather than throwing. */
    @Test
    public void noTargetMeansTheWarhead() {
        assertEquals(8, dogfight().impact(null));
        assertEquals(12, standard().impact(null));
    }

    /**
     * The point of the rule, stated as a comparison: against a ship a dogfight drone is now WORSE
     * than the standard drone it displaces, which is what FD2.54 intends. Before the fix it was
     * better, and cheaper.
     */
    @Test
    public void againstAShipADogfightDroneIsTheWeakerChoice() {
        Unit ship = atSizeClass(3);
        assertTrue("2 against 12", dogfight().impact(ship) < standard().impact(ship));

        Unit fighter = atSizeClass(6);
        assertTrue("but 8 against 12 is a fair trade for half the rack space",
                dogfight().impact(fighter) > dogfight().impact(ship));
    }
}
