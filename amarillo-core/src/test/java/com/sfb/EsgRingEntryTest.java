package com.sfb;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ESG ring-entry detection (G23.51/.571): a unit is hit when it lands on OR
 * crosses the ring, including the same-impulse case where both the target and
 * the ESG ship move toward each other and the range jumps the ring in one step.
 */
public class EsgRingEntryTest {

    @Test
    public void landingOnTheRing_isEntry() {
        assertTrue("range 3 → 2 onto a radius-2 ring", EsgResolver.entersRing(3, 2, 2));
        assertTrue("range 1 → 2 onto the ring from inside", EsgResolver.entersRing(1, 2, 2));
    }

    @Test
    public void crossingTheRing_isEntry_reportedBug() {
        // Lyran ESG (r2) 1308→1307, Hydran 1205→1206: range 3 → 1 crosses radius 2
        // in one impulse because both moved (G23.571 — cannot "jump" the field).
        assertTrue(EsgResolver.entersRing(3, 1, 2));
        assertTrue("crossing outward also strikes the ring", EsgResolver.entersRing(1, 3, 2));
    }

    @Test
    public void stayingOffTheRing_isNotEntry() {
        assertFalse("stayed outside", EsgResolver.entersRing(4, 3, 2));
        assertFalse("stayed inside the hollow", EsgResolver.entersRing(1, 1, 2));
        assertFalse("stayed inside, still short of the ring", EsgResolver.entersRing(0, 1, 2));
    }

    @Test
    public void alreadyOnTheRing_isNotReEntry() {
        assertFalse("on the ring at impulse start, moved off inward", EsgResolver.entersRing(2, 1, 2));
        assertFalse("on the ring at impulse start, moved off outward", EsgResolver.entersRing(2, 3, 2));
        assertFalse("stationary on the ring", EsgResolver.entersRing(2, 2, 2));
    }
}
