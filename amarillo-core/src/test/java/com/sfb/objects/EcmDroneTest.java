package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * FD10.44: building an ECM drone. What one DOES is FD9.0 and is not here.
 *
 * <p>The construction rules are four facts and two refusals, and the interesting part is that
 * one of the "rules" turns out not to be one: FD10.441's free substitution is arithmetic, not a
 * dispensation.
 */
public class EcmDroneTest {

    /** FD10.442: "the module occupies one payload space", and FD10.51 offers no other size. */
    @Test
    public void anEcmModuleIsOnePayloadSpaceAndNoOther() {
        assertEquals(1.0, PayloadModule.ECM.spaces, 0.0001);
        assertEquals("a jammer carries no warhead", 0, PayloadModule.ECM.strength);
        assertEquals("and toughens nothing", 0, PayloadModule.ECM.hullBonus);
        assertEquals("FD10.621 puts ECM drones in Restricted Availability",
                Availability.RESTRICTED, PayloadModule.ECM.availability);
    }

    /**
     * FD10.441: "There is no cost to replace an explosive module with an ECM module."
     *
     * <p>Not a waiver. FD10.51 prices a one-PS ECM module and a one-PS explosive at 0.50 each,
     * so swapping them changes nothing — the same shape as FD12.31's free armor substitution.
     */
    @Test
    public void swappingAnExplosiveForEcmIsFreeBecauseThePricesMatch() {
        assertEquals(PayloadModule.EXPLOSIVE_1.cost, PayloadModule.ECM.cost, 0.0001);

        DroneSpec plain = DroneType.TypeI.spec;
        DroneSpec jammer = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                PayloadModule.ECM);
        assertEquals("a type-I that jams instead of exploding costs the same",
                plain.pointCost(), jammer.pointCost(), 0.0001);
    }

    /** An ECM drone on a one-space frame has given up its entire warhead to do it. */
    @Test
    public void aOneSpaceEcmDroneHasNoWarheadLeft() {
        DroneSpec jammer = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                PayloadModule.ECM);
        assertEquals("the whole payload is the jammer", 0, jammer.warheadStrength());
        assertEquals("and it is still a one-space drone in the rack",
                1.0, jammer.rackSpaces(), 0.0001);
        assertEquals("with the frame's hull and nothing added", 4, jammer.hull());
    }

    /**
     * On a two-space frame it keeps half a punch. FD10.443's own aside: "an ECM module in a
     * two-space drone with an explosive module could not protect a ship as the explosive module
     * would detonate on the target ship. It could protect a wave of drones that it was launched
     * with" — which is a USE rule, and the reason this combination is legal to build.
     */
    @Test
    public void aTwoSpaceEcmDroneKeepsAWarheadInTheOtherSpace() {
        DroneSpec escort = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ECM, PayloadModule.EXPLOSIVE_1);
        assertEquals("twelve points from the explosive half", 12, escort.warheadStrength());
        assertEquals(2.0, escort.rackSpaces(), 0.0001);
        assertEquals("1.00 engine, 0.50 ECM, 0.50 explosive",
                2.0, escort.pointCost(), 0.0001);
    }

    /** FD10.442: "No more than one such module can be placed on any drone." */
    @Test
    public void onlyOneEcmModulePerDrone() {
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    PayloadModule.ECM, PayloadModule.ECM);
            fail("two ECM modules on one drone (FD10.442)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.442"));
        }
    }

    /**
     * FD10.443: "The ECM module can, within the limits of other modules, be combined with any
     * other module in the game <b>except an (internal or external) armor module</b>. ECM drones
     * will not work with armor modules."
     *
     * <p>Both kinds, which is why the check asks {@code isArmor()} and the external additions
     * separately rather than looking for anything that adds hull.
     */
    @Test
    public void ecmRefusesToShareADroneWithArmorOfEitherKind() {
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    PayloadModule.ECM, PayloadModule.ARMOR_1);
            fail("ECM beside internal armor (FD10.443)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.443"));
        }

        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    Set.of(FrameAddition.EXTERNAL_ARMOR_HALF),
                    PayloadModule.ECM, PayloadModule.EXPLOSIVE_1);
            fail("ECM beside external armor (FD10.443)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.443"));
        }
    }

    /**
     * FD10.403's worked example of a drone that cannot exist, now that two of its three parts
     * are built: "a two-space drone with a probe module, an ECM module, and an external armor
     * module (FD12.13) cannot exist; while (FD10.422) permits an ECM module and an external
     * armor module to be associated with a probe module, (FD10.443) prohibits the association
     * of an ECM module with an armor module."
     *
     * <p>The probe half is unbuilt, so this checks the clause that does the refusing.
     */
    @Test
    public void theRulebooksImpossibleDroneIsStillImpossibleWithoutItsProbe() {
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    Set.of(FrameAddition.EXTERNAL_ARMOR_1), PayloadModule.ECM);
            fail("FD10.403's example, less the probe module");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.443"));
        }
    }

    /** An ECM drone is Restricted, and drags its whole drone there (FD10.641). */
    @Test
    public void anEcmDroneIsRestrictedAvailability() {
        DroneSpec escort = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ECM, PayloadModule.EXPLOSIVE_1);

        assertEquals(Availability.RESTRICTED, escort.availabilityIn(180));
        var bill = escort.spacesByAvailability(180);
        assertEquals("only the ECM module's own space, though — the explosive stays General",
                1.0, bill.get(Availability.RESTRICTED), 0.0001);
        assertEquals(1.0, bill.get(Availability.GENERAL), 0.0001);
    }

    /** And one can be loaded into a rack, which is the whole point of the workshop. */
    @Test
    public void anEcmDroneCanBeBuiltAndLoaded() {
        DroneSpec escort = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ECM, PayloadModule.EXPLOSIVE_1);
        Drone d = new Drone(DroneType.TypeIV, escort);

        assertEquals("it is a modified type-IV", DroneType.TypeIV, d.getDroneType());
        assertEquals("with one explosive module's worth of warhead", 12, d.impact());
        assertEquals(6, d.getHull());
        assertEquals(List.of(PayloadModule.ECM, PayloadModule.EXPLOSIVE_1),
                d.getSpec().allModules());
    }
}
