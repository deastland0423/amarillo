package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

import com.sfb.constants.Constants;

/**
 * FD2.222: "Extended range costs 1/2 point per drone and doubles the endurance. Drones with
 * extended range have an X appended to their designation; this is not related to X-ships. (Type-
 * III drones become IIIXX with an endurance of 100 turns and size of two spaces). Any type of
 * drone, EXCEPT a dogfight drone (FD2.5), can be modified to have double endurance. Extended
 * range drones become available Y93."
 *
 * <p>Four rules in one paragraph. Three are here; the size change is FD10.24's and is its own
 * work, and the Y93 date needs a year on {@code FrameAddition}, which nothing has yet.
 *
 * <h2>Why this is the first rule to DERIVE endurance</h2>
 * {@code DroneSpec}'s javadoc says endurance stays declared because "no clause computes it from
 * frame or propulsion". This clause computes it from a frame modification, which is the one case
 * that argument did not cover — so the base figure is still declared per family and the doubling
 * lives in {@code enduranceFrom}.
 */
public class ExtendedRangeDroneTest {

    private static final Set<FrameAddition> XR = Set.of(FrameAddition.EXTENDED_RANGE);

    /** FD2.11's chart in Annex #2: a type-I lasts three turns, and X makes it six. */
    @Test
    public void extendedRangeDoublesAnOrdinaryDronesEndurance() {
        assertEquals("three turns, as declared", 3 * Constants.IMPULSES_PER_TURN,
                DroneType.TypeI.endurance);

        DroneSpec x = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8, XR,
                PayloadModule.EXPLOSIVE_1);
        assertEquals(6 * Constants.IMPULSES_PER_TURN,
                x.enduranceFrom(DroneType.TypeI.endurance));
    }

    /**
     * FD10.33's aside says the same thing from the propulsion section: "Endurance for these
     * fast-burning drone motors is two turns (<b>four with extended range</b>)." That is the
     * transitional type-II and type-V, whose 64 impulses become 128.
     */
    @Test
    public void fd1033sTwoTurnMotorsBecomeFour() {
        assertEquals(2 * Constants.IMPULSES_PER_TURN, DroneType.TypeII.endurance);
        DroneSpec x = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12, XR,
                PayloadModule.EXPLOSIVE_1);
        assertEquals("four turns", 4 * Constants.IMPULSES_PER_TURN,
                x.enduranceFrom(DroneType.TypeII.endurance));
    }

    /** A type-IV is the same three turns on a bigger frame, so X doubles it the same way. */
    @Test
    public void theHeavyFrameDoublesTheSameWay() {
        DroneSpec x = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8, XR,
                PayloadModule.EXPLOSIVE_1, PayloadModule.EXPLOSIVE_1);
        assertEquals(6 * Constants.IMPULSES_PER_TURN,
                x.enduranceFrom(DroneType.TypeIV.endurance));
        assertEquals("and it is still a 24-point warhead (FD2.221: no warhead reduction)",
                24, x.warheadStrength());
    }

    /**
     * FD2.222's parenthesis: "(Type-III drones become IIIXX with <b>an endurance of 100 turns</b>
     * and size of two spaces)."
     *
     * <p><b>Four times the base, not twice.</b> A type-III lasts 25 turns — FD10.22 calls it
     * "standard 25-turn range" and the FD2.1 chart prints 25 — so doubling gives 50 and the rule
     * says 100. The stated figure wins over the derivation in the same sentence.
     */
    @Test
    public void theTypeIiiXxEnduranceIsStatedAtAHundredTurnsRatherThanDoubled() {
        assertEquals("25 turns, as FD10.22 and the FD2.1 chart both say",
                25 * Constants.IMPULSES_PER_TURN, DroneType.TypeIII.endurance);

        DroneSpec xx = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12,
                Set.of(FrameAddition.TYPE_III_GUIDANCE, FrameAddition.EXTENDED_RANGE),
                PayloadModule.EXPLOSIVE_1);
        assertTrue(xx.isTypeIiiXx());
        assertEquals(100 * Constants.IMPULSES_PER_TURN,
                xx.enduranceFrom(DroneType.TypeIII.endurance));
        assertNotEquals("and emphatically not the doubled 50",
                50 * Constants.IMPULSES_PER_TURN,
                xx.enduranceFrom(DroneType.TypeIII.endurance));
    }

    /** A type-III without the X is an ordinary type-III: the pair is what makes the XX. */
    @Test
    public void aTypeIiiKeepsItsOwnEnduranceWithoutExtendedRange() {
        DroneSpec plain = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12,
                Set.of(FrameAddition.TYPE_III_GUIDANCE), PayloadModule.EXPLOSIVE_1);
        assertFalse(plain.isTypeIiiXx());
        assertEquals(25 * Constants.IMPULSES_PER_TURN,
                plain.enduranceFrom(DroneType.TypeIII.endurance));
    }

    /**
     * FD2.222: "Any type of drone, EXCEPT a dogfight drone (FD2.5), can be modified to have
     * double endurance." The type-VI's 32 impulses are what they are.
     */
    @Test
    public void aDogfightDroneCannotHaveExtendedRange() {
        try {
            new DroneSpec(DroneFrame.HALF_SPACE, Propulsion.SPEED_12,
                    java.util.List.of(), XR, null);
            fail("extended range on a type-VI (FD2.222)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD2.222"));
        }
    }

    /** FD10.53 prices it, and it was priced before it did anything. That half still holds. */
    @Test
    public void extendedRangeCostsHalfAPointPerDrone() {
        assertEquals(0.50, FrameAddition.EXTENDED_RANGE.cost, 0.0001);
        DroneSpec plain = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                PayloadModule.EXPLOSIVE_1);
        DroneSpec x = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8, XR,
                PayloadModule.EXPLOSIVE_1);
        assertEquals(0.50, x.pointCost() - plain.pointCost(), 0.0001);
    }

    /** And a long-legged drone can be built and loaded, which is where endurance is read. */
    @Test
    public void anExtendedRangeDroneCanBeBuiltAndLoaded() {
        DroneSpec x = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_20, XR,
                PayloadModule.EXPLOSIVE_1);
        Drone d = new Drone(DroneType.TypeIM, x);
        assertEquals("the basis type says three turns; the X doubles it",
                6 * Constants.IMPULSES_PER_TURN, d.getEndurance());
        assertEquals(20, d.getSpeed());
        assertEquals(12, d.impact());
    }

    /** Nothing in the catalogue carries X, so no declared endurance moved. */
    @Test
    public void theCatalogueDronesAreUnchanged() {
        assertEquals(3 * Constants.IMPULSES_PER_TURN, DroneType.TypeI.endurance);
        assertEquals(2 * Constants.IMPULSES_PER_TURN, DroneType.TypeII.endurance);
        assertEquals(25 * Constants.IMPULSES_PER_TURN, DroneType.TypeIII.endurance);
        assertEquals(3 * Constants.IMPULSES_PER_TURN, DroneType.TypeIV.endurance);
        assertEquals(2 * Constants.IMPULSES_PER_TURN, DroneType.TypeV.endurance);
        assertEquals(1 * Constants.IMPULSES_PER_TURN, DroneType.TypeVI.endurance);
    }
}
