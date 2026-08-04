package com.sfb;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * G23.52 multi-target sharing: when several units enter a field at once the strength
 * is scored one point per pass to each survivor, smallest size-class first, capped at
 * what destroys each unit. Caps are passed smallest-first (as EsgResolver sorts them).
 */
public class EsgMultiTargetTest {

    private static final int SHIP = Integer.MAX_VALUE; // a ship is never destroyed (G23.511)

    @Test
    public void rulebookExample_droneShuttleShip_strengthFive() {
        // "A drone, a shuttle, and a ship all strike an ESG with a strength of five...
        //  Two points would damage the drone, two the shuttle, and one the ship."
        int[] dmg = EsgResolver.roundRobin(5, new int[]{ SHIP, SHIP, SHIP });
        assertArrayEquals(new int[]{ 2, 2, 1 }, dmg);
    }

    @Test
    public void evenSplitWhenStrengthDividesEvenly() {
        int[] dmg = EsgResolver.roundRobin(6, new int[]{ SHIP, SHIP, SHIP });
        assertArrayEquals(new int[]{ 2, 2, 2 }, dmg);
    }

    @Test
    public void destroyedUnitStopsAbsorbing_remainderRolls() {
        // A 4-hull drone (smallest) and a ship share a 20-point field: the drone dies at
        // 4 and the other 16 points go to the ship.
        int[] dmg = EsgResolver.roundRobin(20, new int[]{ 4, SHIP });
        assertArrayEquals(new int[]{ 4, 16 }, dmg);
    }

    @Test
    public void strengthLessThanCount_onlyTheSmallestGetHit() {
        // Strength 2 vs five units: one point to each of the two smallest, none to the rest.
        int[] dmg = EsgResolver.roundRobin(2, new int[]{ SHIP, SHIP, SHIP, SHIP, SHIP });
        assertArrayEquals(new int[]{ 1, 1, 0, 0, 0 }, dmg);
    }

    @Test
    public void allEntrantsDestroyed_leavesFieldStrengthUnspent() {
        // Two 1-hull drones vs a 5-point field: 2 points spent, field keeps the other 3.
        int[] dmg = EsgResolver.roundRobin(5, new int[]{ 1, 1 });
        assertArrayEquals(new int[]{ 1, 1 }, dmg);
        assertEquals("only what it took to destroy them", 2, dmg[0] + dmg[1]);
    }

    @Test
    public void singleShip_absorbsWholeField() {
        int[] dmg = EsgResolver.roundRobin(13, new int[]{ SHIP });
        assertArrayEquals(new int[]{ 13 }, dmg);
    }
}
