package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

import com.sfb.constants.Constants;

/**
 * FD10.24: "Type-III drones are one-space drones, but when given extended range (FD2.222), they
 * become type-III-XX, which are two-space drones with one payload space. Speed upgrade costs for
 * type-III-XX drones are the same as for the two-space drone (FD10.52)."
 *
 * <h2>Why this is a FRAME and not a modified drone</h2>
 * The rulebook's own word: FD10.24 is headed "TYPE-III-XX SPECIAL" inside the frames section,
 * and FD10.4254 writes the limit on wild boar targeting as "(type-IIIXX <b>frame</b> only)". Six
 * numbers differ from a plain type-III, two of them exceptions to rules that hold everywhere
 * else, and all six are properties of the airframe — so they are declared on the frame rather
 * than special-cased at each place they are read.
 */
public class TypeIiiXxFrameTest {

    private static final Set<FrameAddition> TYPE_III =
            Set.of(FrameAddition.TYPE_III_GUIDANCE);
    private static final Set<FrameAddition> TYPE_III_XX =
            Set.of(FrameAddition.TYPE_III_GUIDANCE, FrameAddition.EXTENDED_RANGE);

    /** A type-III, as FD2.1's chart has it: one space, four hull, 25 turns. */
    private static DroneSpec typeIii() {
        return DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12, TYPE_III,
                PayloadModule.EXPLOSIVE_1);
    }

    /** The same drone with extended range, which FD10.24 says is a different airframe. */
    private static DroneSpec typeIiiXx() {
        return DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12, TYPE_III_XX,
                PayloadModule.EXPLOSIVE_1);
    }

    // ----------------------------------------------------------------- the transformation

    /**
     * A caller asks for a type-III with extended range — the way FD2.222 words it — and gets
     * the frame that transformation produces. Naming the frame is not how it is reached.
     */
    @Test
    public void aTypeIiiWithExtendedRangeBecomesTheXxFrame() {
        assertEquals(DroneFrame.ONE_SPACE, typeIii().effectiveFrame());
        assertEquals(DroneFrame.TYPE_III_XX, typeIiiXx().effectiveFrame());
        assertTrue(typeIiiXx().isTypeIiiXx());

        assertEquals("the declared frame is untouched; only what it BECAME differs",
                DroneFrame.ONE_SPACE, typeIiiXx().frame());
    }

    /** Extended range on anything else is still just extended range (FD2.222). */
    @Test
    public void onlyTheTypeIiiFrameTransforms() {
        DroneSpec typeIx = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.EXTENDED_RANGE), PayloadModule.EXPLOSIVE_1);
        assertEquals(DroneFrame.ONE_SPACE, typeIx.effectiveFrame());
        assertFalse(typeIx.isTypeIiiXx());
        assertEquals("one rack space still", 1.0, typeIx.rackSpaces(), 0.0001);
    }

    // ------------------------------------------------------------------- the six numbers

    /** FD10.24: "two-space drones with one payload space." */
    @Test
    public void itIsTwoRackSpacesWithOnePayloadSpace() {
        assertEquals(1.0, typeIii().rackSpaces(), 0.0001);
        assertEquals(2.0, typeIiiXx().rackSpaces(), 0.0001);
        assertEquals(1, DroneFrame.TYPE_III_XX.payloadSpaces);
    }

    /**
     * The payload space does NOT grow with the frame, which is the whole oddity of FD10.24 —
     * extended range consumes the space the bigger frame brings. A second module is refused.
     */
    @Test
    public void theSecondSpaceIsNotPayload() {
        try {
            DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12, TYPE_III_XX,
                    PayloadModule.EXPLOSIVE_1, PayloadModule.EXPLOSIVE_1);
            fail("a type-III-XX has one payload space (FD10.4)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("exceeds"));
        }
        assertEquals("so its warhead is a type-III's, not a type-IV's",
                12, typeIiiXx().warheadStrength());
    }

    /**
     * FD2.11: "Unarmored type-III drones are always destroyed by four damage points whether they
     * are single space drones, or two space type-IIIXX drones."
     *
     * <p>The first drone in the game whose hull does not follow its frame size. Every other
     * two-space drone has six.
     */
    @Test
    public void hullStaysFourThoughTheDroneIsNowTwoSpaces() {
        assertEquals(4, typeIii().hull());
        assertEquals(4, typeIiiXx().hull());
        assertEquals("while an ordinary two-space frame gives six", 6, DroneFrame.TWO_SPACE.hull);
    }

    /** FD2.222: "(Type-III drones become IIIXX with an endurance of 100 turns...)" */
    @Test
    public void enduranceIsTheStatedHundredTurns() {
        assertEquals(25 * Constants.IMPULSES_PER_TURN,
                typeIii().enduranceFrom(DroneType.TypeIII.endurance));
        assertEquals(100 * Constants.IMPULSES_PER_TURN,
                typeIiiXx().enduranceFrom(DroneType.TypeIII.endurance));
    }

    /**
     * And the general rule is general again. {@code enduranceFrom} doubles, full stop; the 100
     * turns arrive as the frame's declared override, not as a branch inside the rule.
     */
    @Test
    public void theGeneralRuleNoLongerKnowsAboutTheTypeIii() {
        assertNull("ordinary frames state no endurance", DroneFrame.ONE_SPACE.enduranceOverride);
        assertNull(DroneFrame.TWO_SPACE.enduranceOverride);
        assertEquals(Integer.valueOf(100 * Constants.IMPULSES_PER_TURN),
                DroneFrame.TYPE_III_XX.enduranceOverride);

        DroneSpec plainX = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.EXTENDED_RANGE), PayloadModule.EXPLOSIVE_1);
        assertEquals("doubled, as FD2.222 says for everything else",
                192, plainX.enduranceFrom(96));
    }

    /**
     * FD10.24: "Speed upgrade costs for type-III-XX drones are the same as for the two-space
     * drone (FD10.52)." Which falls out of pricing the effective frame rather than being said
     * twice: speed-12 is 1.00 on a one-space frame and 1.50 on a two-space one.
     */
    @Test
    public void theEngineIsPricedFromTheTwoSpaceColumn() {
        // 1.00 engine + 0.50 explosive + 0.50 type-III surcharge
        assertEquals(2.00, typeIii().pointCost(), 0.0001);
        // 1.50 engine + 0.50 explosive + 0.50 surcharge + 0.50 extended range
        assertEquals(3.00, typeIiiXx().pointCost(), 0.0001);

        assertEquals("the engine difference is the two-space column, not the X surcharge",
                Propulsion.SPEED_12.costOn(DroneFrame.TWO_SPACE),
                Propulsion.SPEED_12.costOn(DroneFrame.TYPE_III_XX), 0.0001);
    }

    /**
     * FD10.641: "a type-III frame (Limited Availability) to which the extended range frame
     * modification (General Availability) is added becomes a two-space <b>Restricted</b>
     * Availability drone (FD10.24)."
     *
     * <p>Less restrictive than one of its own parts, so it cannot be a combination — the frame's
     * tier replaces both. The plain type-III is Limited, and stays so.
     */
    @Test
    public void theCarveOutMakesItRestrictedThoughItsPartsAreLimitedAndGeneral() {
        assertEquals(Availability.LIMITED, FrameAddition.TYPE_III_GUIDANCE.availability);
        assertEquals(Availability.GENERAL, FrameAddition.EXTENDED_RANGE.availability);

        assertEquals("the plain type-III is Limited", Availability.LIMITED,
                typeIii().availabilityIn(180));
        assertEquals("and adding extended range makes it LESS restricted",
                Availability.RESTRICTED, typeIiiXx().availabilityIn(180));
    }

    /** The same carve-out in the per-space bill FD10.641 is actually about. */
    @Test
    public void itCountsAsTwoSpacesOfRestricted() {
        var bill = typeIiiXx().spacesByAvailability(180);
        assertEquals("one payload space, at the frame's own tier",
                1.0, bill.get(Availability.RESTRICTED), 0.0001);
        assertNull("and nothing left at Limited", bill.get(Availability.LIMITED));
    }

    /**
     * A warhead more restricted than the frame still raises its own space (FD10.641), and a
     * Limited speed upgrade still drags the floor back down. The carve-out speaks for the
     * type-III frame and extended range, not for everything else on the drone.
     */
    @Test
    public void theCarveOutDoesNotCoverTheRestOfTheDrone() {
        DroneSpec fast = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_32, TYPE_III_XX,
                PayloadModule.EXPLOSIVE_1);
        assertEquals("fast drones are Limited in Y178 (FD10.651)",
                Availability.LIMITED, fast.availabilityIn(178));
        assertEquals("and General by Y180, leaving the frame's Restricted to stand",
                Availability.RESTRICTED, fast.availabilityIn(180));
    }

    // ------------------------------------------------------------------- how it is named

    /** Naming the frame directly works, but only with the additions that would produce it. */
    @Test
    public void namingTheFrameRequiresThePartsThatMakeIt() {
        DroneSpec named = DroneSpec.of(DroneFrame.TYPE_III_XX, Propulsion.SPEED_12, TYPE_III_XX,
                PayloadModule.EXPLOSIVE_1);
        assertEquals(DroneFrame.TYPE_III_XX, named.effectiveFrame());
        assertEquals("and it costs exactly what the derived one costs",
                typeIiiXx().pointCost(), named.pointCost(), 0.0001);

        try {
            DroneSpec.of(DroneFrame.TYPE_III_XX, Propulsion.SPEED_12, PayloadModule.EXPLOSIVE_1);
            fail("a bare type-III-XX skips both half-point surcharges (FD10.24)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.24"));
        }
    }

    /** External armor on one reads FD12.132's two-space row, since that is what it is. */
    @Test
    public void externalArmorUsesTheTwoSpaceSpeedRow() {
        DroneSpec armored = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12,
                Set.of(FrameAddition.TYPE_III_GUIDANCE, FrameAddition.EXTENDED_RANGE,
                        FrameAddition.EXTERNAL_ARMOR_HALF),
                PayloadModule.EXPLOSIVE_1);
        assertEquals("a two-space drone at speed 12 with half a space of armor", 10,
                armored.speed());
        assertEquals("while a one-space drone drops to 8",
                8, DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12,
                        Set.of(FrameAddition.EXTERNAL_ARMOR_HALF), PayloadModule.EXPLOSIVE_1)
                        .speed());
    }

    /** And one can be built and loaded, carrying all six numbers with it. */
    @Test
    public void aTypeIiiXxCanBeBuiltAndLoaded() {
        Drone d = new Drone(DroneType.TypeIII, typeIiiXx());
        assertEquals(2.0, d.getRackSize(), 0.0001);
        assertEquals(4, d.getHull());
        assertEquals(12, d.impact());
        assertEquals(12, d.getSpeed());
        assertEquals(100 * Constants.IMPULSES_PER_TURN, d.getEndurance());
        assertTrue("and it is self-guiding, as every type-III is", d.isSelfGuiding());
    }

    /** Nothing in the catalogue is a type-III-XX, so no existing drone moved. */
    @Test
    public void theCatalogueIsUnchanged() {
        for (DroneType t : DroneType.values())
            assertFalse(t.name(), t.spec.isTypeIiiXx());
        assertEquals(1.0, DroneType.TypeIII.rack, 0.0001);
        assertEquals(4, DroneType.TypeIII.hull);
    }
}
