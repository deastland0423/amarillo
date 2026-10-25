package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

/**
 * FD10.42: building a probe drone. What one DOES is FD6.0 and is not here.
 *
 * <p>Three construction clauses, and the third is the interesting one: the probe is the module
 * that may share a drone with armor, where FD10.443 bars the ECM module from doing so.
 */
public class ProbeDroneTest {

    /** FD10.42: "All probe modules occupy one payload space", and FD10.51 offers no other. */
    @Test
    public void aProbeModuleIsOnePayloadSpace() {
        assertEquals(1.0, PayloadModule.PROBE.spaces, 0.0001);
        assertEquals("a probe carries no warhead", 0, PayloadModule.PROBE.strength);
        assertEquals("and toughens nothing", 0, PayloadModule.PROBE.hullBonus);
        assertEquals("FD10.621: probe modules are Restricted Availability",
                Availability.RESTRICTED, PayloadModule.PROBE.availability);
    }

    /**
     * FD10.421: "There is no cost to replace an explosive module with a probe module." The
     * price match again — a one-PS probe and a one-PS explosive are both 0.50 on FD10.51.
     */
    @Test
    public void swappingAnExplosiveForAProbeIsFreeBecauseThePricesMatch() {
        assertEquals(PayloadModule.EXPLOSIVE_1.cost, PayloadModule.PROBE.cost, 0.0001);
        DroneSpec probe = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                PayloadModule.PROBE);
        assertEquals(DroneType.TypeI.spec.pointCost(), probe.pointCost(), 0.0001);
    }

    /** FD10.42: "no more than one can be placed on a given drone". */
    @Test
    public void onlyOneProbeModulePerDrone() {
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    PayloadModule.PROBE, PayloadModule.PROBE);
            fail("two probe modules on one drone (FD10.42)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.42"));
        }
    }

    /**
     * FD10.422: "The probe module can be combined with other modules on a two-space drone <b>or
     * have external armor added to its drone frame</b>. (The most common two-space probe drone
     * had armor to improve survivability.)"
     *
     * <p>The clause that makes the probe different from the ECM module, which FD10.443 bars
     * from armor of either kind. Both of FD10.422's permissions are built here.
     */
    @Test
    public void aProbeMayShareADroneWithArmorWhereAnEcmModuleMayNot() {
        // Internal armor, in the other payload space of a two-space frame.
        DroneSpec withInternal = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.PROBE, PayloadModule.ARMOR_1);
        assertEquals("four hull from the armor module, over the frame's six",
                10, withInternal.hull());
        assertEquals("and no warhead at all", 0, withInternal.warheadStrength());

        // External armor, bolted to the frame — FD10.422's "most common" probe drone.
        DroneSpec withExternal = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.EXTERNAL_ARMOR_1),
                PayloadModule.PROBE, PayloadModule.EXPLOSIVE_1);
        assertEquals("keeps its warhead, because external armor costs no payload space",
                12, withExternal.warheadStrength());
        assertEquals(10, withExternal.hull());
        assertEquals("and pays in speed instead (FD12.132)", 4, withExternal.speed());

        // Swap the probe for an ECM module and the same drone becomes illegal.
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    PayloadModule.ECM, PayloadModule.ARMOR_1);
            fail("ECM beside armor (FD10.443)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.443"));
        }
    }

    /**
     * FD10.403's worked impossibility, in full at last — all three modules built.
     *
     * <blockquote>"For example, a two-space drone with a probe module, an ECM module, and an
     * external armor module (FD12.13) cannot exist; while (FD10.422) permits an ECM module and
     * an external armor module to be associated with a probe module, (FD10.443) prohibits the
     * association of an ECM module with an armor module."</blockquote>
     *
     * <p>Worth building precisely because the rulebook explains WHY it fails, and the reason is
     * not the probe. Each pair is legal on its own; the drone is impossible only as a trio.
     */
    @Test
    public void theRulebooksImpossibleDroneIsImpossibleForTheReasonItGives() {
        // Probe + external armor: FD10.422 permits it.
        DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.EXTERNAL_ARMOR_HALF),
                PayloadModule.PROBE, PayloadModule.EXPLOSIVE_1);

        // Probe + ECM: FD10.423 discusses what to do with it, so it exists.
        DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.PROBE, PayloadModule.ECM);

        // All three: refused, and on the ECM/armor clause rather than anything about probes.
        try {
            DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                    Set.of(FrameAddition.EXTERNAL_ARMOR_HALF),
                    PayloadModule.PROBE, PayloadModule.ECM);
            fail("FD10.403's drone cannot exist");
        } catch (IllegalArgumentException expected) {
            assertTrue("and the reason must be FD10.443, not the probe: " + expected.getMessage(),
                    expected.getMessage().contains("FD10.443"));
        }
    }

    /**
     * FD10.423 exists, which means probe + ECM on a two-space frame must be buildable: "If an
     * ECM module is included with the probe module on a two space frame, the launching player
     * must designate on launch..." A rule about what to do with a drone presupposes the drone.
     */
    @Test
    public void probeAndEcmTogetherAreLegalToBuild() {
        DroneSpec both = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.PROBE, PayloadModule.ECM);
        assertEquals("neither module is a warhead", 0, both.warheadStrength());
        assertEquals("1.00 engine, 0.50 probe, 0.50 ECM", 2.0, both.pointCost(), 0.0001);
        assertEquals(Availability.RESTRICTED, both.availabilityIn(180));
    }

    /** And one can be built and loaded, which is the whole of the workshop's job. */
    @Test
    public void aProbeDroneCanBeBuiltAndLoaded() {
        DroneSpec spec = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.PROBE, PayloadModule.EXPLOSIVE_1);
        Drone d = new Drone(DroneType.TypeIV, spec);

        assertEquals(DroneType.TypeIV, d.getDroneType());
        assertEquals("the explosive half still goes off", 12, d.impact());
        assertEquals(6, d.getHull());
        assertEquals(2.0, d.getRackSize(), 0.0001);
    }
}
