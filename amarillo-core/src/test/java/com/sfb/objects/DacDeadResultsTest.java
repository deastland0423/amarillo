package com.sfb.objects;

import com.sfb.samples.FederationShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Regression tests for DAC results that the chart produces but the hit
 * switch silently skipped: "trans", "lab", "probe" (all indestructible by
 * combat damage), and the explicit column-13 "excess" entry (which could
 * cascade into a spurious SHIP DESTROYED while excess boxes remained).
 * Also covers "tractor" depletion (fixed alongside the TractorBeam
 * extraction).
 */
public class DacDeadResultsTest {

    private Ship fed; // FedCA: trans 3, lab 8, probe 1, excess 6, tractor 3

    @Before
    public void setUp() {
        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
    }

    @Test
    public void transHit_destroysATransporter() {
        int before = fed.getTransporters().getAvailableTrans();
        assertEquals("trans HIT", fed.applySystemHitForTest("trans"));
        assertEquals(before - 1, fed.getTransporters().getAvailableTrans());
    }

    @Test
    public void transHit_depletes_thenAdvancesDac() {
        for (int i = 0; i < 3; i++)
            assertNotNull("hit " + (i + 1), fed.applySystemHitForTest("trans"));
        assertNull("No boxes left — DAC must advance", fed.applySystemHitForTest("trans"));
    }

    @Test
    public void labHit_destroysALab() {
        int before = fed.getLabs().getAvailableLab();
        assertEquals("lab HIT", fed.applySystemHitForTest("lab"));
        assertEquals(before - 1, fed.getLabs().getAvailableLab());
    }

    @Test
    public void labHit_depletes_thenAdvancesDac() {
        for (int i = 0; i < 8; i++)
            assertNotNull("hit " + (i + 1), fed.applySystemHitForTest("lab"));
        assertNull(fed.applySystemHitForTest("lab"));
    }

    @Test
    public void probeHit_destroysTheLauncher() {
        assertEquals(1, fed.getProbes().availableProbes());
        assertEquals("probe HIT", fed.applySystemHitForTest("probe"));
        assertEquals(0, fed.getProbes().availableProbes());
        assertNull("Launcher gone — DAC must advance", fed.applySystemHitForTest("probe"));
    }

    @Test
    public void excessHit_consumesAnExcessBox() {
        int before = fed.getSpecialFunctions().getExcessDamage();
        assertEquals(6, before);
        String label = fed.applySystemHitForTest("excess");
        assertNotNull(label);
        assertTrue(label.contains("excess damage"));
        assertEquals(before - 1, fed.getSpecialFunctions().getExcessDamage());
    }

    @Test
    public void excessHit_depletes_thenAdvancesDac_shipNotDestroyedHere() {
        for (int i = 0; i < 6; i++)
            assertNotNull("hit " + (i + 1), fed.applySystemHitForTest("excess"));
        assertNull("Empty excess track returns null (C3.14 destruction is the caller's call)",
                fed.applySystemHitForTest("excess"));
    }

    @Test
    public void tractorHit_depletes_thenAdvancesDac() {
        for (int i = 0; i < 3; i++)
            assertNotNull("hit " + (i + 1), fed.applySystemHitForTest("tractor"));
        assertNull(fed.applySystemHitForTest("tractor"));
    }
}
