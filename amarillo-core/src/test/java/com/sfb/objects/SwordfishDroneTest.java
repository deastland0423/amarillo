package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * FD11.0: building a swordfish drone. Firing the phaser it carries is FD11.2.
 *
 * <p>"While the Klingons developed swordfish drones in Y174 to confuse Kzinti and Federation
 * drone defenses..." — a drone that shoots instead of exploding, which is why its warhead
 * strength is zero and that is not a bug.
 */
public class SwordfishDroneTest {

    /** FD10.45: the modules carry phasers — a phaser-3 at one space, a phaser-2 at two. */
    @Test
    public void bothSizesCarryAPhaserRatherThanAWarhead() {
        assertEquals(1.0, PayloadModule.SWORDFISH_PHASER3.spaces, 0.0001);
        assertEquals(2.0, PayloadModule.SWORDFISH_PHASER2.spaces, 0.0001);
        assertEquals("a phaser is not an explosive module",
                0, PayloadModule.SWORDFISH_PHASER3.strength);
        assertEquals(0, PayloadModule.SWORDFISH_PHASER2.strength);
        assertEquals(Availability.LIMITED, PayloadModule.SWORDFISH_PHASER3.availability);
        assertEquals(Availability.LIMITED, PayloadModule.SWORDFISH_PHASER2.availability);
    }

    /**
     * FD11.3 prices the modules by DIFFERENCE from an explosive, and FD10.51 prices them
     * outright. The two agree, which is the check worth making because they are separate
     * statements that could have disagreed.
     *
     * <blockquote>"The cost of a phaser-3 module is 1/2 point more than a one-PS explosive
     * module. The cost of a phaser-2 module is one point more than a two-PS explosive
     * module."</blockquote>
     */
    @Test
    public void fd113sDifferencesAgreeWithFd1051sChart() {
        assertEquals("one-PS explosive 0.50 plus a half",
                PayloadModule.EXPLOSIVE_1.cost + 0.50,
                PayloadModule.SWORDFISH_PHASER3.cost, 0.0001);
        assertEquals("two-PS explosive 1.00 plus one",
                PayloadModule.EXPLOSIVE_2.cost + 1.00,
                PayloadModule.SWORDFISH_PHASER2.cost, 0.0001);

        assertEquals("and the chart's own figures", 1.00,
                PayloadModule.SWORDFISH_PHASER3.cost, 0.0001);
        assertEquals(2.00, PayloadModule.SWORDFISH_PHASER2.cost, 0.0001);
    }

    /** A one-space swordfish drone: the phaser is the whole payload. */
    @Test
    public void aOneSpaceSwordfishDroneExplodesForNothing() {
        DroneSpec sf = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                PayloadModule.SWORDFISH_PHASER3);
        assertEquals("the damage comes from the phaser, not the impact",
                0, sf.warheadStrength());
        assertEquals("0.50 engine plus 1.00 module", 1.5, sf.pointCost(), 0.0001);
        assertEquals(Availability.LIMITED, sf.availabilityIn(180));
    }

    /** The two-space module fills a type-IV frame, carrying a phaser-2. */
    @Test
    public void theLargeModuleFillsATwoSpaceFrame() {
        DroneSpec sf = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.SWORDFISH_PHASER2);
        assertEquals(2.0, sf.rackSpaces(), 0.0001);
        assertEquals("1.00 engine plus 2.00 module", 3.0, sf.pointCost(), 0.0001);
        assertEquals("FD10.641: a Limited module over both spaces", 2.0,
                sf.spacesByAvailability(180).get(Availability.LIMITED), 0.0001);
    }

    /**
     * FD10.45/FD11.11: "Swordfish modules must be in the front position."
     *
     * <p>Which space it sits in is not tracked — the constraint is that a legal arrangement
     * exists, so one forward-only module is fine and a second is not. The two-space module is
     * forward by filling the whole payload, which is the same allowance FD10.432's plural
     * grants the large MW.
     */
    @Test
    public void aSwordfishTakesTheFrontAndWillNotShareIt() {
        // One, with an ordinary warhead behind it.
        DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.SWORDFISH_PHASER3, PayloadModule.EXPLOSIVE_1);

        // Two forward-only modules cannot both be in front.
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    PayloadModule.SWORDFISH_PHASER3, PayloadModule.MW_SMALL);
            fail("a swordfish and an MW module both want the front (FD10.402)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.402"));
        }
    }

    /**
     * FD10.443's aside applies here too: "This also applies to swordfish and spearfish
     * modules" — an ECM module cannot sit with armor, and the same reasoning covers these.
     * What IS legal is a swordfish beside an ECM module, since neither is armor.
     */
    @Test
    public void aSwordfishMayShareADroneWithEcm() {
        DroneSpec both = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.SWORDFISH_PHASER3, PayloadModule.ECM);
        assertEquals(0, both.warheadStrength());
        assertEquals("1.00 engine, 1.00 swordfish, 0.50 ECM", 2.5, both.pointCost(), 0.0001);
    }

    /** And one can be built and loaded. */
    @Test
    public void aSwordfishDroneCanBeBuiltAndLoaded() {
        DroneSpec spec = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                PayloadModule.SWORDFISH_PHASER3);
        Drone d = new Drone(DroneType.TypeI, spec);
        assertEquals(DroneType.TypeI, d.getDroneType());
        assertEquals(0, d.impact());
        assertEquals(1.0, d.getRackSize(), 0.0001);
        assertEquals(4, d.getHull());
    }
}
