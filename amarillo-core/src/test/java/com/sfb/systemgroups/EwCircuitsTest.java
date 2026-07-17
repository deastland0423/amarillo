package com.sfb.systemgroups;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * EW circuit bank (D6.31): allocation, mid-turn adjustment, the one-switch-
 * per-8-impulses circuit commitment (D6.316/D6.312), reserve-power costing,
 * and cross-turn persistence of mode commitments.
 */
public class EwCircuitsTest {

    private EwCircuits ew;

    @Before
    public void setUp() {
        ew = new EwCircuits();
    }

    // -------------------------------------------------------------------------
    // Allocation (D6.310)
    // -------------------------------------------------------------------------

    @Test
    public void allocate_powersRequestedCounts() {
        assertNull(ew.allocate(4, 2, 1, 6));
        assertEquals(4, ew.getEcm());
        assertEquals(2, ew.getEccm());
    }

    @Test
    public void allocate_failsBeyondCircuitCount() {
        assertNotNull("only 6 circuits exist at sensor 6", ew.allocate(4, 4, 1, 6));
    }

    @Test
    public void allocate_respectsDamagedSensorRating() {
        assertNotNull("sensor 4 → only 4 circuits usable", ew.allocate(4, 2, 1, 4));
        assertNull(ew.allocate(2, 2, 1, 4));
    }

    // -------------------------------------------------------------------------
    // Mid-turn adjustment (D6.315)
    // -------------------------------------------------------------------------

    @Test
    public void drop_isFree() {
        ew.allocate(4, 2, 1, 6);
        assertEquals(0, ew.batteryCost(2, 2));
        assertNull(ew.adjust(2, 2, 3, 6));
        assertEquals(2, ew.getEcm());
        assertEquals(2, ew.getEccm());
    }

    @Test
    public void add_costsOneBatteryPerPoint() {
        ew.allocate(2, 2, 1, 6);
        assertEquals(2, ew.batteryCost(2, 4));
        assertNull(ew.adjust(2, 4, 3, 6));
        assertEquals(4, ew.getEccm());
    }

    @Test
    public void reAddingDroppedPoints_costsBattery() {
        // D6.315-A: dropped points are lost; replacement is reserve power
        ew.allocate(4, 0, 1, 6);
        assertNull(ew.adjust(0, 0, 3, 6));
        assertEquals(4, ew.batteryCost(4, 0));
    }

    @Test
    public void reAddingSameMode_neverNeedsAFlip() {
        // Dropped ECM circuits stay ECM-committed — re-powering them is not a
        // switch, so it works even inside the lockout window
        ew.allocate(6, 0, 1, 6);
        assertNull(ew.adjust(0, 0, 2, 6));
        assertNull(ew.adjust(6, 0, 3, 6));
        assertEquals(6, ew.getEcm());
    }

    @Test
    public void adjust_overSensorRating_fails() {
        ew.allocate(4, 2, 1, 6);
        assertNotNull(ew.adjust(6, 2, 3, 6));
    }

    // -------------------------------------------------------------------------
    // Circuit switch lockout (D6.316)
    // -------------------------------------------------------------------------

    @Test
    public void fullSwitch_thenSwitchBack_blockedWithin8Impulses() {
        ew.allocate(6, 0, 1, 6);
        // The yo-yo: all six circuits flip to ECCM on impulse 3 (first switch —
        // allowed), committing them until impulse 11
        assertNull(ew.adjust(0, 6, 3, 6));
        assertEquals(6, ew.getEccm());

        String err = ew.adjust(6, 0, 5, 6);
        assertNotNull("flip back within 8 impulses must fail", err);
        assertEquals("failed adjust must not change state", 6, ew.getEccm());
        assertEquals(0, ew.getEcm());

        assertNotNull(ew.adjust(6, 0, 10, 6)); // impulse 10: still locked (3+8=11)
        assertNull(ew.adjust(6, 0, 11, 6));    // impulse 11: free again
        assertEquals(6, ew.getEcm());
    }

    @Test
    public void partialSwitch_usesUncommittedCircuitsFirst() {
        // ECM 2 + ECCM 2 leaves two never-used circuits at sensor 6 — moving
        // to ECCM 4 uses them, flipping nothing
        ew.allocate(2, 2, 1, 6);
        assertNull(ew.adjust(0, 4, 3, 6));
        // The two dropped ECM circuits are still ECM-committed and re-powerable
        // immediately — proving no flip was burned on them
        assertNull(ew.adjust(2, 4, 4, 6));
        assertEquals(2, ew.getEcm());
        assertEquals(4, ew.getEccm());
    }

    // -------------------------------------------------------------------------
    // Turn lifecycle (D6.312)
    // -------------------------------------------------------------------------

    @Test
    public void cleanUp_expiresAllPower() {
        ew.allocate(4, 2, 1, 6);
        ew.adjust(4, 4, 3, 6); // two reserve-bought ECCM (hypothetical battery paid)
        ew.cleanUp();
        assertEquals(0, ew.getEcm());
        assertEquals(0, ew.getEccm());
    }

    @Test
    public void switchLockout_persistsAcrossTurnBoundary() {
        ew.allocate(6, 0, 1, 6);
        assertNull(ew.adjust(0, 6, 30, 6)); // switch late in the turn (impulse 30)
        ew.cleanUp();                        // turn ends

        // Next turn's EA (impulse 33): circuits are ECCM-committed until 38
        assertNotNull("EA cannot flip circuits still committed (D6.312)",
                ew.allocate(6, 0, 33, 6));
        assertNull("same-mode allocation is fine", ew.allocate(0, 6, 33, 6));
        assertNull("after the lockout passes, flips are free again",
                ew.allocate(6, 0, 38, 6));
    }
}
