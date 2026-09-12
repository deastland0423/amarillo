package com.sfb.objects.shuttles;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Crippling is a shuttle property, not a fighter one (J1.33): every shuttle has a damage level
 * at which it degrades rather than simply dying, and the speed halving in J1.331 applies to all
 * of them. Fighters add their own weapon effects on top (J1.332).
 */
public class ShuttleCripplingTest {

    @Test
    public void aPlainShuttleCripplesAtItsThreshold() {
        AdminShuttle s = new AdminShuttle();
        assertEquals(4, s.getCrippledHull());

        s.setCurrentHull(s.getHull() - 3);
        assertFalse("three damage is not yet enough", s.shouldCripple());

        s.setCurrentHull(s.getHull() - 4);
        assertTrue("four is (J1.33)", s.shouldCripple());
    }

    /** J1.331: the speed halving applies to any shuttle, not only fighters. */
    @Test
    public void cripplingAPlainShuttleHalvesItsSpeed() {
        AdminShuttle s = new AdminShuttle();
        s.setCurrentSpeed(6);              // in flight at its rated speed
        s.setCurrentHull(s.getHull() - 4);

        String log = s.applyCripplingEffects();

        assertNotNull(log);
        assertTrue(log, log.contains("CRIPPLED"));
        assertTrue(s.isCrippled());
        // maxSpeed keeps the undamaged rating on purpose: ShipMover derives the halving from
        // isCrippled() at movement time. What crippling does here is clamp the current speed.
        assertEquals("speed 6 halved on the spot (J1.331)", 3, s.getCurrentSpeed());
        assertEquals("the rating itself is untouched", 6, s.getMaxSpeed());
    }

    /** A threshold of zero means no crippled state — destroyed outright, never crippled. */
    @Test
    public void aShuttleWithNoThresholdIsNeverCrippled() {
        AdminShuttle s = new AdminShuttle();
        s.setCrippledHull(0);

        assertFalse("undamaged", s.shouldCripple());
        s.setCurrentHull(0);
        assertFalse("and still not, however much it takes", s.shouldCripple());
    }

    /** Fighters keep their own thresholds and add weapon effects on top (J1.332). */
    @Test
    public void fightersStillCrippleTheirOwnWay() {
        Stinger2 f = new Stinger2();
        assertEquals(7, f.getCrippledHull());

        f.setCurrentHull(f.getHull() - 7);
        assertTrue(f.shouldCripple());
        assertNotNull(f.applyCripplingEffects());
        assertTrue(f.isCrippled());
    }

    /** The thresholds the catalogue publishes are the ones the classes build with. */
    @Test
    public void everyShuttleTypeHasItsThreshold() {
        assertEquals(4, new AdminShuttle().getCrippledHull());
        assertEquals(6, new GASShuttle().getCrippledHull());
        assertEquals(8, new HTSShuttle().getCrippledHull());
        assertEquals(6, new Stinger1().getCrippledHull());
        assertEquals(7, new Stinger2().getCrippledHull());
        assertEquals(7, new StingerH().getCrippledHull());
        assertEquals(6, new Aas().getCrippledHull());
        assertEquals(8, new Haas().getCrippledHull());
        assertEquals(8, new Haas_E().getCrippledHull());
    }
}
