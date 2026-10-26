package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.Test;

/**
 * FD10.6: how obtainable a drone is, which is a TIER that moves with the year rather than a
 * date it becomes legal.
 *
 * <h2>The misreading this exists to prevent</h2>
 * {@code DroneType.availableFromYear} is 165 for the medium-speed drones, and the obvious
 * reading is "legal from Y165". FD10.65 says a medium drone is <b>Limited in Y165, Restricted
 * in Y166, General in Y167</b> — available in all three, in rising quantity. Collapsing that to
 * a single year lets a Y165 fleet carry a full load of drones that were barely obtainable.
 *
 * <p>FD2.223's "available from Y167" is the same ladder quoted at its top step, which is why
 * the prose rule and our data disagreed by two years without either being wrong.
 */
public class DroneAvailabilityTest {

    /** FD10.651's table, for all three speeds that have one. */
    @Test
    public void everySpeedClimbsTheLadderOnTheYearsFD10651Prints() {
        assertEquals("moderate: Limited from Y77",
                Availability.LIMITED, Propulsion.SPEED_12.availabilityIn(77));
        assertEquals("Restricted from Y100",
                Availability.RESTRICTED, Propulsion.SPEED_12.availabilityIn(100));
        assertEquals("General from Y120",
                Availability.GENERAL, Propulsion.SPEED_12.availabilityIn(120));

        assertEquals(Availability.LIMITED, Propulsion.SPEED_20.availabilityIn(165));
        assertEquals(Availability.RESTRICTED, Propulsion.SPEED_20.availabilityIn(166));
        assertEquals(Availability.GENERAL, Propulsion.SPEED_20.availabilityIn(167));

        assertEquals(Availability.LIMITED, Propulsion.SPEED_32.availabilityIn(178));
        assertEquals(Availability.RESTRICTED, Propulsion.SPEED_32.availabilityIn(179));
        assertEquals(Availability.GENERAL, Propulsion.SPEED_32.availabilityIn(180));
    }

    /** The baseline FD2.22 says every ship's BPV already includes. */
    @Test
    public void slowDronesAreGeneralFromTheBeginning() {
        assertEquals(Availability.GENERAL, Propulsion.SPEED_8.availabilityIn(0));
        assertEquals(Availability.GENERAL, Propulsion.SPEED_8.availabilityIn(180));
    }

    /**
     * Existing and being obtainable are different questions. A medium drone in Y164 is not
     * Limited; there is no such thing yet.
     */
    @Test
    public void anUninventedSpeedDoesNotExistRatherThanBeingHardToGet() {
        assertFalse(Propulsion.SPEED_20.existsIn(164));
        assertTrue(Propulsion.SPEED_20.existsIn(165));
        assertFalse(Propulsion.SPEED_32.existsIn(177));
        assertTrue(Propulsion.SPEED_32.existsIn(178));

        assertFalse("and the drone built around it cannot exist either",
                DroneType.TypeIM.spec.existsIn(164));
        assertTrue(DroneType.TypeIM.spec.existsIn(165));
    }

    /**
     * The years our data carries ARE the Limited years — the first the thing exists — which is
     * why they disagree with FD2.223's and FD2.224's "available from", those being the General
     * years at the top of the same ladder.
     */
    @Test
    public void theYearInTheDataIsTheLimitedYearNotTheGeneralOne() {
        assertEquals(165, DroneType.TypeIM.availableFromYear);
        assertEquals("which is the year it becomes LIMITED, not freely available",
                Availability.LIMITED,
                DroneType.TypeIM.spec.availabilityIn(DroneType.TypeIM.availableFromYear));
        assertEquals("FD2.223's Y167 is the General step of the same ladder",
                Availability.GENERAL, DroneType.TypeIM.spec.availabilityIn(167));

        assertEquals(178, DroneType.TypeIF.availableFromYear);
        assertEquals(Availability.LIMITED,
                DroneType.TypeIF.spec.availabilityIn(178));
        assertEquals("FD2.224's Y180", Availability.GENERAL,
                DroneType.TypeIF.spec.availabilityIn(180));
    }

    /**
     * FD10.65: "A type-I drone with medium speed in Y166 (restricted) and a Swordfish module
     * (limited) would be a limited availability drone." A drone is as hard to get as its
     * hardest part. The swordfish module is unbuilt, so armor stands in for it — FD10.621 makes
     * an armored drone Restricted, and a type-III frame Limited (FD10.631).
     */
    @Test
    public void aDroneIsAsHardToGetAsItsHardestPart() {
        DroneSpec armoredMedium = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_20,
                PayloadModule.ARMOR_HALF, PayloadModule.EXPLOSIVE_HALF);

        assertEquals("in Y167 the speed is General, so the armor decides",
                Availability.RESTRICTED, armoredMedium.availabilityIn(167));
        assertEquals("in Y165 the speed is Limited, and that is worse than the armor",
                Availability.LIMITED, armoredMedium.availabilityIn(165));
    }

    /**
     * FD10.631 puts the type-III frame in Limited Availability, so a type-III is Limited in
     * every year — the one drone in the standard roster that always is.
     */
    @Test
    public void aTypeThreeIsLimitedForever() {
        for (int year : new int[] { 120, 165, 167, 180, 200 })
            assertEquals("Y" + year, Availability.LIMITED,
                    DroneType.TypeIII.spec.availabilityIn(year));
        assertEquals("even the fast one, where the speed has gone General by Y180",
                Availability.LIMITED, DroneType.TypeIIIF.spec.availabilityIn(180));
    }

    /** An ordinary type-I is General whenever it exists; it is the thing everything else is measured against. */
    @Test
    public void theBaselineDroneIsAlwaysGeneral() {
        assertEquals(Availability.GENERAL, DroneType.TypeI.spec.availabilityIn(0));
        assertEquals(Availability.GENERAL, DroneType.TypeI.spec.availabilityIn(180));
        assertEquals("and so is the heavy one: FD10.611 lists frames I and IV together",
                Availability.GENERAL, DroneType.TypeIV.spec.availabilityIn(180));
    }

    /**
     * FD10.641: a drone is a BILL of tiers, not one tier.
     *
     * <blockquote>"Frame modifications... apply to the entire drone unless a warhead is of a
     * more restricted type. A type-IV drone with Medium Speed in Y166 counts as two spaces of
     * Restricted Availability even if both of its payload spaces are General Availability
     * items. Such a Restricted Availability drone might have a Limited Availability module in
     * one of its payload spaces, in which case it would be considered as one space of
     * Restricted and one space of Limited availability."</blockquote>
     */
    @Test
    public void aFrameModificationSetsAFloorUnderEveryPayloadSpace() {
        // The rulebook's own example: type-IV, medium speed in Y166 (Restricted), two General
        // explosive modules. Both spaces are dragged up to Restricted.
        var y166 = DroneType.TypeIVM.spec.spacesByAvailability(166);
        assertEquals("two spaces, all of them Restricted",
                2.0, y166.getOrDefault(Availability.RESTRICTED, 0.0), 0.0001);
        assertNull("and none left General", y166.get(Availability.GENERAL));

        // The same drone a year later, when medium speed has gone General (FD10.65): the floor
        // drops and the explosive modules are General again.
        var y167 = DroneType.TypeIVM.spec.spacesByAvailability(167);
        assertEquals(2.0, y167.getOrDefault(Availability.GENERAL, 0.0), 0.0001);
        assertNull(y167.get(Availability.RESTRICTED));
    }

    /**
     * And the other direction: a module harder to obtain than the frame raises its OWN space
     * and leaves the rest alone. Armor is Restricted (FD10.621), a speed-8 frame is General.
     *
     * <p>This is the half that a single worst-tier-wins answer gets wrong. Before the split,
     * this drone counted as two spaces of Restricted; it is one and one.
     */
    @Test
    public void aRestrictedModuleRaisesOnlyItsOwnSpace() {
        DroneSpec half = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_HALF, PayloadModule.ARMOR_HALF, PayloadModule.EXPLOSIVE_1);

        var bill = half.spacesByAvailability(180);
        assertEquals("the two half-space armor modules",
                1.0, bill.getOrDefault(Availability.RESTRICTED, 0.0), 0.0001);
        assertEquals("and the explosive module stays General",
                1.0, bill.getOrDefault(Availability.GENERAL, 0.0), 0.0001);

        assertEquals("while the DRONE as a whole is still Restricted, which is the question"
                + " FD10.65's first example asks", Availability.RESTRICTED,
                half.availabilityIn(180));
    }

    /**
     * The bill accounts for every space the drone uses — which under FD10.66 is not always the
     * same as the spaces its modules occupy.
     *
     * <p>"There are no 'half spaces' of drone warheads": two half-modules share one space and
     * are billed as one, and a lone half still takes a space of its own. So the bill equals the
     * carried total except where an ODD number of halves rounds the last one up, which today is
     * only the type-VI's fixed warhead — and FD10.643 keeps type-VIs out of the sum anyway.
     */
    @Test
    public void theBillAccountsForEverySpace() {
        for (DroneType t : DroneType.values()) {
            double billed = t.spec.spacesByAvailability(180).values().stream()
                    .mapToDouble(Double::doubleValue).sum();
            double carried = 0;
            long halves = 0;
            for (PayloadModule m : t.spec.allModules()) {
                carried += m.spaces;
                if (m.spaces == 0.5)
                    halves++;
            }
            double expected = halves % 2 == 0 ? carried : carried + 0.5;
            assertEquals(t + " bills every space, rounding an odd half up (FD10.66)",
                    expected, billed, 0.0001);
        }
    }

    /**
     * FD10.66's own worked example, which is the rule that nobody looks for because
     * per-module accounting gives a plausible answer:
     *
     * <blockquote>"a type-IV drone with an MW module, a half space explosive module, and a
     * half space armor module would be considered as a space of Restricted and a space of
     * Limited availability."</blockquote>
     *
     * <p>The MW module is Limited and fills one space. The other space holds two halves — a
     * General explosive and a Restricted armor — and FD10.66 makes it "one space of the more
     * restricted type", so the explosive half is dragged up rather than counting separately.
     */
    @Test
    public void twoHalfModulesShareOneSpaceAtTheWorseTier() {
        DroneSpec mixed = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.MW_SMALL, PayloadModule.EXPLOSIVE_HALF,
                PayloadModule.ARMOR_HALF);

        var bill = mixed.spacesByAvailability(180);
        assertEquals("the MW module", 1.0, bill.get(Availability.LIMITED), 0.0001);
        assertEquals("the shared space, at the armor's tier",
                1.0, bill.get(Availability.RESTRICTED), 0.0001);
        assertNull("and nothing counts as General at all", bill.get(Availability.GENERAL));
    }

    /** Two halves of the SAME tier are still one space, not two halves. */
    @Test
    public void twoHalvesOfOneTierAreStillASingleSpace() {
        DroneSpec twoHalves = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                PayloadModule.EXPLOSIVE_HALF, PayloadModule.EXPLOSIVE_HALF);
        assertEquals("FD10.41: no difference between two 1/2-space and one 1.0-space module",
                1.0, twoHalves.spacesByAvailability(180).get(Availability.GENERAL), 0.0001);
    }

    /**
     * FD10.64: "The allowed percentage of Restricted Availability drones INCLUDES the limit on
     * Limited Availability drones." So a cap check asks "at least this restricted", not "equal
     * to this", and a Limited drone consumes Restricted allowance as well as its own.
     */
    @Test
    public void theTiersNestSoALimitedDroneCountsAgainstRestrictedToo() {
        assertTrue(Availability.LIMITED.atLeastAsRestrictedAs(Availability.RESTRICTED));
        assertTrue(Availability.LIMITED.atLeastAsRestrictedAs(Availability.LIMITED));
        assertTrue(Availability.RESTRICTED.atLeastAsRestrictedAs(Availability.GENERAL));
        assertFalse(Availability.RESTRICTED.atLeastAsRestrictedAs(Availability.LIMITED));
        assertFalse(Availability.GENERAL.atLeastAsRestrictedAs(Availability.RESTRICTED));
    }

    /**
     * ATG is Restricted (FD10.621) and extended range is General (FD10.611), so a frame
     * addition can move a drone's tier or leave it alone.
     */
    @Test
    public void frameAdditionsCarryTheirOwnTier() {
        DroneSpec withAtg = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.ACTIVE_TERMINAL_GUIDANCE), PayloadModule.EXPLOSIVE_1);
        assertEquals(Availability.RESTRICTED, withAtg.availabilityIn(180));

        DroneSpec withRange = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                Set.of(FrameAddition.EXTENDED_RANGE), PayloadModule.EXPLOSIVE_1);
        assertEquals("extended range is General, so it changes nothing",
                Availability.GENERAL, withRange.availabilityIn(180));
    }
}
