package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

/**
 * FD12.0 and FD13.0: armored drones, and the slug drones that are nothing else.
 *
 * <blockquote>FD12.0: "There is not one single type of armored drone; virtually any drone can be
 * armored. The term 'armor' is somewhat misleading. The effect of an increased damage rating is
 * created by shielding and evasiveness..."</blockquote>
 *
 * <h2>Two armors, and they are not variants of one thing</h2>
 * Internal armor fills a payload space and is Restricted (FD10.621). External armor bolts on
 * "in excess of the normal payload space limit" (FD12.13), costs speed instead of space, and is
 * Limited (FD10.631). Same protection, different currency, four times harder to get — which is
 * why they are separate constants in separate enums rather than a placement flag.
 */
public class ArmoredDroneTest {

    /** FD12.21: "The 1/2-PS module adds two damage points; the one-PS armor module adds four." */
    @Test
    public void internalArmorBuysHullWithPayloadSpace() {
        DroneSpec half = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_HALF, PayloadModule.EXPLOSIVE_HALF);
        assertEquals("FD12.11's common one-space armored drone", 6, half.hull());
        assertEquals("and half a warhead", 6, half.warheadStrength());

        DroneSpec full = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_1, PayloadModule.EXPLOSIVE_1);
        assertEquals("FD12.11's common two-space one: six plus four", 10, full.hull());
        assertEquals(12, full.warheadStrength());
    }

    /** FD12.31: "Replacing explosive modules with armor modules is done without cost." */
    @Test
    public void internalArmorIsAZeroCostSubstitution() {
        DroneSpec plain = DroneType.TypeIV.spec;
        DroneSpec armored = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_1, PayloadModule.EXPLOSIVE_1);
        assertEquals("the same points, because FD10.51 prices armor and explosive alike at a"
                + " given size — the rule is a consequence of the chart, not a waiver",
                plain.pointCost(), armored.pointCost(), 0.0001);
    }

    // ------------------------------------------------------------------ external armor

    /**
     * FD12.13: external armor goes on "in excess of the normal payload space limit", so a
     * one-space drone keeps a FULL warhead and gains hull anyway — which internal armor on the
     * same frame cannot do, having nowhere to sit.
     */
    @Test
    public void externalArmorCostsNoPayloadSpaceAtAll() {
        DroneSpec external = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.EXTERNAL_ARMOR_HALF), PayloadModule.EXPLOSIVE_1);

        assertEquals("a full twelve-point warhead", 12, external.warheadStrength());
        assertEquals("and four plus two hull", 6, external.hull());
        assertEquals("still one rack space", 1.0, external.rackSpaces(), 0.0001);
    }

    /** FD12.132's chart, every cell of it. */
    @Test
    public void externalArmorSlowsTheDroneByTheChart() {
        int[][] expected = {
            // SPEED_32, SPEED_20, SPEED_12, SPEED_8
            { 20, 12, 8, 4 },   // 1sp + 1/2
            { 12,  8, 4, 2 },   // 1sp + 1
            { 26, 16, 10, 6 },  // 2sp + 1/2
            { 20, 12, 8, 4 },   // 2sp + 1
        };
        DroneFrame[] frames = { DroneFrame.ONE_SPACE, DroneFrame.ONE_SPACE,
                                DroneFrame.TWO_SPACE, DroneFrame.TWO_SPACE };
        FrameAddition[] armors = { FrameAddition.EXTERNAL_ARMOR_HALF, FrameAddition.EXTERNAL_ARMOR_1,
                                   FrameAddition.EXTERNAL_ARMOR_HALF, FrameAddition.EXTERNAL_ARMOR_1 };
        Propulsion[] speeds = { Propulsion.SPEED_32, Propulsion.SPEED_20,
                                Propulsion.SPEED_12, Propulsion.SPEED_8 };

        for (int row = 0; row < expected.length; row++)
            for (int col = 0; col < speeds.length; col++) {
                DroneSpec spec = DroneSpec.of(frames[row], speeds[col], Set.of(armors[row]),
                        frames[row] == DroneFrame.TWO_SPACE
                                ? new PayloadModule[] { PayloadModule.EXPLOSIVE_1,
                                                        PayloadModule.EXPLOSIVE_1 }
                                : new PayloadModule[] { PayloadModule.EXPLOSIVE_1 });
                assertEquals(frames[row] + " + " + armors[row] + " at " + speeds[col],
                        expected[row][col], spec.speed());
            }
    }

    /**
     * FD10.631 puts external armor in Limited Availability where FD10.621 puts internal armor
     * in Restricted. The same two hull points, a tier harder to obtain.
     */
    @Test
    public void externalArmorIsHarderToComeByThanInternal() {
        assertEquals(Availability.RESTRICTED, PayloadModule.ARMOR_HALF.availability);
        assertEquals(Availability.LIMITED, FrameAddition.EXTERNAL_ARMOR_HALF.availability);

        DroneSpec external = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.EXTERNAL_ARMOR_HALF), PayloadModule.EXPLOSIVE_1);
        assertEquals("and it drags the whole drone to Limited (FD10.641)",
                Availability.LIMITED, external.availabilityIn(180));
        assertEquals("every space of it, since it is a frame modification",
                1.0, external.spacesByAvailability(180).get(Availability.LIMITED), 0.0001);
    }

    /** FD12.32 / FD10.51: a quarter point for the small module, a half for the large. */
    @Test
    public void externalArmorCostsWhatTheChartSays() {
        assertEquals(0.25, FrameAddition.EXTERNAL_ARMOR_HALF.cost, 0.0001);
        assertEquals(0.50, FrameAddition.EXTERNAL_ARMOR_1.cost, 0.0001);

        DroneSpec external = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.EXTERNAL_ARMOR_HALF), PayloadModule.EXPLOSIVE_1);
        assertEquals("a type-I plus a quarter — and UNLIKE internal armor, external armor is"
                + " not a free substitution, because it replaces nothing",
                DroneType.TypeI.spec.pointCost() + 0.25, external.pointCost(), 0.0001);
    }

    // ------------------------------------------------------------------ FD12.131's two refusals

    /** FD12.131: "No more than one external armor module can be added to any one drone." */
    @Test
    public void onlyOneExternalArmorModule() {
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    Set.of(FrameAddition.EXTERNAL_ARMOR_HALF, FrameAddition.EXTERNAL_ARMOR_1),
                    PayloadModule.EXPLOSIVE_1, PayloadModule.EXPLOSIVE_1);
            fail("two external armor modules on one drone (FD12.131)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD12.131"));
        }
    }

    /** FD12.131: "External armor cannot be placed on a drone equipped with internal armor." */
    @Test
    public void externalArmorRefusesToShareWithInternal() {
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    Set.of(FrameAddition.EXTERNAL_ARMOR_HALF),
                    PayloadModule.ARMOR_1, PayloadModule.EXPLOSIVE_1);
            fail("internal and external armor on one drone (FD12.131)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD12.131"));
        }
    }

    /** FD10.21: on a type-VI "only the speed can be varied", and FD12.132 has no row for one. */
    @Test
    public void aDogfightDroneCannotBeArmored() {
        try {
            new DroneSpec(DroneFrame.HALF_SPACE, Propulsion.SPEED_12,
                    java.util.List.of(), Set.of(FrameAddition.EXTERNAL_ARMOR_HALF));
            fail("a type-VI takes no armor (FD10.21)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.21"));
        }
    }

    // ------------------------------------------------------------------ FD13.0

    /**
     * FD13.0: "Slug drones are not a specific type of drone, but simply an armored drone that
     * carries nothing but armor modules (i.e., no warhead)... If a slug drone reaches the hex of
     * its target, it simply self-destructs without causing any damage."
     *
     * <p>No rule needed: the warhead is the sum of the explosive modules (FD10.41), and a drone
     * carrying none sums to nothing.
     */
    @Test
    public void aSlugDroneIsAllArmorAndNoWarhead() {
        DroneSpec slug = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_1, PayloadModule.ARMOR_1);

        assertEquals("six from the frame and eight from two full armor modules", 14, slug.hull());
        assertEquals("and it explodes for nothing", 0, slug.warheadStrength());

        Drone loaded = new Drone(DroneType.TypeIV, slug);
        assertEquals("which is what reaches the target", 0, loaded.impact());
        assertEquals(14, loaded.getHull());
    }

    /**
     * And FD10.631 lists slug drones under Limited Availability in their own right — but a slug
     * built from INTERNAL armor is only Restricted by its parts. The rulebook names the finished
     * article; the composition does not know it has built one.
     *
     * <p>Recorded rather than special-cased: naming a drone by its contents is a step this model
     * does not take anywhere, and FD13.0 itself says a slug drone "is not a specific type".
     */
    @Test
    public void aSlugDroneOfInternalArmorIsOnlyRestrictedByItsParts() {
        DroneSpec slug = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_1, PayloadModule.ARMOR_1);
        assertEquals("by its parts, which is what this model knows",
                Availability.RESTRICTED, slug.availabilityIn(180));
    }
}
