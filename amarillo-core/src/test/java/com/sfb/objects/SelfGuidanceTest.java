package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

/**
 * F3.42: which seeking weapons can guide themselves, and the difference between being able to
 * and doing it.
 *
 * <blockquote>"Plasma torpedoes (FP4.0), drones with active terminal guidance (FD5.2), and
 * type-VI drones (FD5.1) are <b>capable of</b> guiding themselves if released from control by
 * the controlling unit."</blockquote>
 *
 * <blockquote>F3.421: "A seeking weapon capable of self-guiding is assumed to be <b>under the
 * control of a guiding unit</b> until control over it has been released."</blockquote>
 *
 * <h2>Why these are two questions</h2>
 * They used to be one settable boolean, and the two meanings collided. Handing a drone to a
 * consort cleared the flag to record "somebody controls it now" — which also erased the ATG the
 * drone was built with, permanently, because nothing in the engine ever set the flag back. A
 * type-III transferred once could never guide itself again, and would self-destruct on release.
 *
 * <p>Now the capability is derived from what the drone is made of and cannot be assigned at all,
 * and "is it guiding itself" is the capability plus an empty controller.
 */
public class SelfGuidanceTest {

    // ------------------------------------------------------------ the three routes to it

    /** FD5.11/FD5.14: energy-seeking guidance, "used only on 1/2-space dogfight drones". */
    @Test
    public void theTypeVIGuidesItselfByItsFrame() {
        assertTrue(DroneType.TypeVI.spec.isSelfGuidingCapable());
        assertTrue(DroneType.TypeVIM.spec.isSelfGuidingCapable());
        assertTrue(DroneType.TypeVIF.spec.isSelfGuidingCapable());
    }

    /** FD10.23/FD2.221: "Type-III drones already have ATG." */
    @Test
    public void theTypeIiiGuidesItselfByItsOwnGuidancePackage() {
        assertTrue(DroneType.TypeIII.spec.isSelfGuidingCapable());
        assertTrue(DroneType.TypeIIIM.spec.isSelfGuidingCapable());
        assertTrue(DroneType.TypeIIIF.spec.isSelfGuidingCapable());
    }

    /** FD5.22: "Any drone other than a type-VI may be equipped with ATG." Half a point. */
    @Test
    public void anyOtherDroneCanBuyIt() {
        DroneSpec plain = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                PayloadModule.EXPLOSIVE_1);
        assertFalse("a type-I guides nothing itself", plain.isSelfGuidingCapable());

        DroneSpec homing = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.ACTIVE_TERMINAL_GUIDANCE), PayloadModule.EXPLOSIVE_1);
        assertTrue("a type-I-H does", homing.isSelfGuidingCapable());
        assertEquals("FD2.221: half a point, and the warhead is not reduced",
                0.50, homing.pointCost() - plain.pointCost(), 0.0001);
        assertEquals(12, homing.warheadStrength());
    }

    /** And the ordinary drones do not, which is what makes the control rules bite. */
    @Test
    public void theStandardDronesNeedGuiding() {
        for (DroneType t : new DroneType[] { DroneType.TypeI, DroneType.TypeIM, DroneType.TypeIF,
                DroneType.TypeII, DroneType.TypeIV, DroneType.TypeIVM, DroneType.TypeIVF,
                DroneType.TypeV })
            assertFalse(t.name(), t.spec.isSelfGuidingCapable());
    }

    /** Every declared value in the enum agrees with the derivation that replaced it. */
    @Test
    public void theEnumsOwnFlagIsTheDerivedOne() {
        for (DroneType t : DroneType.values())
            assertEquals(t.name(), t.spec.isSelfGuidingCapable(), t.selfGuiding);
    }

    // ------------------------------------------------------------------- who may carry ATG

    /**
     * FD5.22 excludes the type-VI, and FD5.14 says why: energy-seeking guidance "cannot be used
     * on any other type of seeking weapon", and the dogfight drone has nothing else.
     */
    @Test
    public void aTypeVICannotBeFittedWithAtg() {
        try {
            new DroneSpec(DroneFrame.HALF_SPACE, Propulsion.SPEED_12, java.util.List.of(),
                    Set.of(FrameAddition.ACTIVE_TERMINAL_GUIDANCE), null);
            fail("ATG on a dogfight drone (FD5.22)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD5.22"));
        }
    }

    /** FD10.23: "ATG cannot be added to type-III drone, which already has a special type." */
    @Test
    public void aTypeIiiCannotBuyWhatItAlreadyHas() {
        try {
            DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12,
                    Set.of(FrameAddition.TYPE_III_GUIDANCE,
                            FrameAddition.ACTIVE_TERMINAL_GUIDANCE),
                    PayloadModule.EXPLOSIVE_1);
            fail("ATG on a type-III (FD10.23)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.23"));
        }
    }

    // ------------------------------------------------- capability versus what it is doing

    /**
     * F3.421, which is the whole point: a capable weapon under control is NOT guiding itself,
     * and the capability is still there the moment it is let go.
     */
    @Test
    public void aControlledDroneKeepsTheCapabilityWithoutExercisingIt() {
        Drone d = new Drone(DroneType.TypeIII);
        assertTrue("capable from the moment it is built", d.isSelfGuidingCapable());
        assertFalse("but it has not closed on anything yet (FD5.12)", d.isSelfGuiding());
        d.acquireOwnLockOn();
        assertTrue("now it is guiding itself, with nobody holding it", d.isSelfGuiding());

        Ship ship = new Ship();
        ship.setName("Guide");
        d.setController(ship);
        assertFalse("under control it is not guiding itself", d.isSelfGuiding());
        assertTrue("but it is still a type-III", d.isSelfGuidingCapable());
        assertTrue("and it still has its own lock-on", d.hasOwnLockOn());

        d.setController(null);
        assertTrue("released, it takes over again (F3.42)", d.isSelfGuiding());
    }

    /**
     * All three terms are load-bearing, and the middle one is the new one: FD5.12's "These
     * drones require guidance <b>until they achieve their own lock-on</b>". A type-VI released
     * before it closes is capable, unheld, and still lost — it has nothing to steer by.
     */
    @Test
    public void aCapableDroneReleasedTooEarlyHasNothingToSteerBy() {
        Drone early = new Drone(DroneType.TypeVI);
        assertTrue(early.isSelfGuidingCapable());
        assertNull(early.getController());
        assertFalse("no lock-on of its own yet", early.hasOwnLockOn());
        assertFalse("so it is not guiding itself", early.isSelfGuiding());
    }

    /** FD5.12 and FD5.21 put the same threshold on both families: eight hexes. */
    @Test
    public void eightHexesIsWhereADroneTakesOver() {
        assertEquals(8, Drone.SELF_GUIDANCE_RANGE);

        Drone dogfight = new Drone(DroneType.TypeVI);
        assertTrue("FD5.12", dogfight.wouldAcquireAt(8));
        assertFalse("and not a hex further", dogfight.wouldAcquireAt(9));

        Drone homing = new Drone(DroneType.TypeIII);
        assertTrue("FD5.21", homing.wouldAcquireAt(8));
        assertFalse(homing.wouldAcquireAt(9));

        Drone plain = new Drone(DroneType.TypeI);
        assertFalse("a drone with no guidance of its own never takes over, at any range",
                plain.wouldAcquireAt(1));
    }

    /** A plain drone released is a drone with nowhere to go, capability being the difference. */
    @Test
    public void aPlainDroneIsNeverSelfGuiding() {
        Drone d = new Drone(DroneType.TypeI);
        assertFalse(d.isSelfGuidingCapable());
        assertFalse("even with nobody holding it", d.isSelfGuiding());
    }

    /** F3.42 names plasma torpedoes first, with no condition attached. */
    @Test
    public void aPlasmaTorpedoIsAlwaysCapable() {
        PlasmaTorpedo t = new PlasmaTorpedo(com.sfb.properties.PlasmaType.R,
                com.sfb.properties.WeaponArmingType.STANDARD);
        assertTrue(t.isSelfGuidingCapable());
        assertTrue(t.isSelfGuiding());
    }

    /**
     * And the seeking shuttles are not on F3.42's list, so they are not capable. They used to
     * say so with an override apiece; now they inherit it from {@link Seeker}'s default, which
     * is where "no" belongs when it is the answer for everything that has not said otherwise.
     */
    @Test
    public void seekingShuttlesGuideNothingThemselves() {
        assertFalse(new com.sfb.objects.shuttles.ScatterPack(
                new com.sfb.objects.shuttles.AdminShuttle()).isSelfGuidingCapable());
        assertFalse(new com.sfb.objects.shuttles.SuicideShuttle(
                new com.sfb.objects.shuttles.AdminShuttle()).isSelfGuidingCapable());
    }
}
