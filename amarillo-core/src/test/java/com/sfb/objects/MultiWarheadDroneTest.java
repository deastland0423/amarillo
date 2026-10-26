package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * FD8.0: building a multi-warhead drone. What the submunitions DO on release is FD8.2.
 *
 * <p>The MW module is also where FORWARD placement first matters, so this is where that rule
 * is tested — four of the five remaining modules will need it (FD10.432, FD11.11, FD14.11,
 * FD15.11) and MW is the first built.
 */
public class MultiWarheadDroneTest {

    /** FD10.431: "The smaller occupies one payload space and carries three submunitions." */
    @Test
    public void theSmallModuleIsOneSpaceAndThreeSubmunitions() {
        assertEquals(1.0, PayloadModule.MW_SMALL.spaces, 0.0001);
        assertEquals(3, PayloadModule.MW_SMALL.submunitions);
        assertEquals("type-VI drones carried instead of an explosive warhead",
                0, PayloadModule.MW_SMALL.strength);
        assertEquals(2.50, PayloadModule.MW_SMALL.cost, 0.0001);
        assertEquals("FD10.631 lists multi-warhead drones under Limited",
                Availability.LIMITED, PayloadModule.MW_SMALL.availability);
    }

    /** "The larger occupies two payload spaces and carries five submunitions." */
    @Test
    public void theLargeModuleIsTwoSpacesAndFiveSubmunitions() {
        assertEquals(2.0, PayloadModule.MW_LARGE.spaces, 0.0001);
        assertEquals(5, PayloadModule.MW_LARGE.submunitions);
        assertEquals(3.50, PayloadModule.MW_LARGE.cost, 0.0001);
    }

    /**
     * FD8.11's Kzinti type-IV-MW: a two-space frame whose entire payload is the module. It
     * explodes for nothing of its own — the five type-VI drones are the weapon.
     */
    @Test
    public void theKzintiTypeFourMwFillsItsWholePayload() {
        DroneSpec mw = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.MW_LARGE);
        assertEquals("no warhead of its own", 0, mw.warheadStrength());
        assertEquals(2.0, mw.rackSpaces(), 0.0001);
        assertEquals("1.00 engine plus 3.50 module", 4.5, mw.pointCost(), 0.0001);
        assertEquals(Availability.LIMITED, mw.availabilityIn(180));
    }

    /**
     * FD10.432's own worked example: "It would be possible to place a small
     * (three-submunition) module on a two-space drone along with an explosive module, allowing
     * the carrier drone to damage the target after releasing its submunitions."
     */
    @Test
    public void aSmallModuleLeavesRoomForAWarheadOnATwoSpaceDrone() {
        DroneSpec carrier = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.MW_SMALL, PayloadModule.EXPLOSIVE_1);
        assertEquals("the carrier still hits for twelve after releasing",
                12, carrier.warheadStrength());
        assertEquals(3, carrier.allModules().stream()
                .mapToInt(m -> m.submunitions).sum());
    }

    /** FD10.432: "No more than one MW module can be on a single drone." */
    @Test
    public void onlyOneMultiWarheadModulePerDrone() {
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    PayloadModule.MW_SMALL, PayloadModule.MW_SMALL);
            fail("two MW modules on one drone (FD10.432)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.432"));
        }
    }

    // ------------------------------------------------------------------ forward placement

    /**
     * FD10.432: an MW module "must be in the forward payload space(s)".
     *
     * <p>Which space it actually occupies is not tracked. The rule is enforced as a capacity
     * question instead — a drone is refused when its forward-only modules could not all fit in
     * front — because no rule asks where a module is, only that it be forward.
     */
    @Test
    public void aSmallModuleFitsTheSingleForwardSpace() {
        // On its own, on either frame.
        DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8, PayloadModule.MW_SMALL);
        DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.MW_SMALL, PayloadModule.EXPLOSIVE_1);
    }

    /**
     * And the plural in "forward payload space(s)" is the rule permitting a two-space module:
     * it is forward by occupying everything, so there is no rear space to be wrong about.
     */
    @Test
    public void aTwoSpaceModuleIsForwardByFillingTheWholePayload() {
        DroneSpec mw = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.MW_LARGE);
        assertEquals(2.0, mw.frame().payloadSpaces, 0.0001);
        assertEquals("the module spans both spaces", 2.0,
                mw.allModules().get(0).spaces, 0.0001);
    }

    /**
     * A module too large for the frame is refused on CAPACITY, which runs before the forward
     * check — "it does not fit" is the more fundamental complaint than "it cannot be forward",
     * and an earlier ordering reported the second for a drone with the first problem.
     */
    @Test
    public void aTwoSpaceModuleWillNotFitAOneSpaceFrame() {
        try {
            DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8, PayloadModule.MW_LARGE);
            fail("a two-space module cannot fit a one-space frame");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("exceeds"));
        }
    }

    /**
     * TWO separate forward-only modules on one drone is the case the FD10.402 check exists for,
     * and it is NOT REACHABLE YET: the MW module is the only forward-only module built, and
     * FD10.432 caps it at one per drone, so the pair cannot be assembled.
     *
     * <p>Recorded rather than faked. The check becomes live with swordfish (FD11.11), spearfish
     * (FD14.11) or starfish (FD15.11) — an MW module plus any of those is the first drone it
     * will refuse, and this test is where that goes.
     */
    @Test
    public void twoForwardOnlyModulesIsUnreachableUntilAnotherOneExists() {
        long forwardOnly = java.util.Arrays.stream(PayloadModule.values())
                .filter(m -> m.requiresForward).count();
        assertEquals("only the two MW sizes demand the front so far", 2, forwardOnly);
        assertTrue("and both are MW modules, which FD10.432 allows only one of",
                java.util.Arrays.stream(PayloadModule.values())
                        .filter(m -> m.requiresForward)
                        .allMatch(m -> m.submunitions > 0));
    }

    /** FD10.631 again, and FD10.641: a Limited module drags its own space to Limited. */
    @Test
    public void aCarrierDroneSplitsAcrossTiers() {
        DroneSpec carrier = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.MW_SMALL, PayloadModule.EXPLOSIVE_1);
        var bill = carrier.spacesByAvailability(180);
        assertEquals("the MW module's space", 1.0, bill.get(Availability.LIMITED), 0.0001);
        assertEquals("and the explosive stays General", 1.0,
                bill.get(Availability.GENERAL), 0.0001);
    }

    /** And one can be built and loaded. */
    @Test
    public void anMwDroneCanBeBuiltAndLoaded() {
        DroneSpec spec = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.MW_LARGE);
        Drone d = new Drone(DroneType.TypeIV, spec);
        assertEquals(DroneType.TypeIV, d.getDroneType());
        assertEquals("it carries drones, not a warhead", 0, d.impact());
        assertEquals(2.0, d.getRackSize(), 0.0001);
    }
}
