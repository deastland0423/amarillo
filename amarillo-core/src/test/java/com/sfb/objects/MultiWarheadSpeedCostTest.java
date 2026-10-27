package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * FD8.24 and FD8.44: an MW drone pays for TWO sets of engines — the bus vehicle's and, again,
 * one per dogfight drone it carries.
 *
 * <blockquote>"Note that the cost of speed upgrades for the submunitions is paid separately from
 * and in addition to the cost of the bus vehicle; see (S3.2). <b>This makes MW drones extremely
 * expensive.</b>"</blockquote>
 *
 * <p>FD8.4 is unusually generous with worked totals — ten of them across three frames — which
 * makes this the best-oracled rule in the drone model. Every one is checked below, because they
 * are what caught the reading of FD8.44 that was arithmetically faithful and wrong.
 */
public class MultiWarheadSpeedCostTest {

    /** What a ship's BPV already paid for, and so what a trade-in deducts (FD2.22, FD10.54). */
    private static final double TYPE_I = 1.0;

    /** FD8.11/FD8.12: the type-III-MW is a type-III frame carrying three submunitions. */
    private static DroneSpec typeIiiMw(Propulsion bus, Propulsion subs) {
        DroneSpec spec = DroneSpec.of(DroneFrame.ONE_SPACE, bus,
                java.util.Set.of(FrameAddition.TYPE_III_GUIDANCE), PayloadModule.MW_SMALL);
        return subs == null ? spec : spec.withSubmunitionSpeed(subs);
    }

    /** FD8.11: the Kzinti original, a type-IV frame whose whole payload is five type-VIs. */
    private static DroneSpec typeIvMw(Propulsion bus, Propulsion subs) {
        DroneSpec spec = DroneSpec.of(DroneFrame.TWO_SPACE, bus, PayloadModule.MW_LARGE);
        return subs == null ? spec : spec.withSubmunitionSpeed(subs);
    }

    /** FD8.14: "A type-I-MW has three submunitions." */
    private static DroneSpec typeIMw(Propulsion bus, Propulsion subs) {
        DroneSpec spec = DroneSpec.of(DroneFrame.ONE_SPACE, bus, PayloadModule.MW_SMALL);
        return subs == null ? spec : spec.withSubmunitionSpeed(subs);
    }

    // ------------------------------------------------------------------ the published tables

    /**
     * FD8.441: "The cost to upgrade all submunitions in a one space MW module to medium speed is
     * 0.75 points. The cost to upgrade all the submunitions on a one-space MW module to fast
     * speed is 1.5 points."
     */
    @Test
    public void threeSubmunitionsCostFd8441sFigures() {
        assertEquals(3, typeIMw(Propulsion.SPEED_8, null).submunitionCount());
        assertEquals(0.75, typeIMw(Propulsion.SPEED_8, Propulsion.SPEED_20)
                .submunitionUpgradeCost(), 0.0001);
        assertEquals(1.50, typeIMw(Propulsion.SPEED_8, Propulsion.SPEED_32)
                .submunitionUpgradeCost(), 0.0001);
    }

    /**
     * FD8.442: "The cost to upgrade all the submunitions in a two space MW module to medium
     * speed is 1.25. The cost to upgrade all the submunitions on a two-space MW module to fast
     * is 2.5 points."
     */
    @Test
    public void fiveSubmunitionsCostFd8442sFigures() {
        assertEquals(5, typeIvMw(Propulsion.SPEED_8, null).submunitionCount());
        assertEquals(1.25, typeIvMw(Propulsion.SPEED_8, Propulsion.SPEED_20)
                .submunitionUpgradeCost(), 0.0001);
        assertEquals(2.50, typeIvMw(Propulsion.SPEED_8, Propulsion.SPEED_32)
                .submunitionUpgradeCost(), 0.0001);
    }

    /**
     * Both tables are the SAME per-submunition figure, which is the point: 0.75/3 and 1.25/5 are
     * both 0.25, and 1.5/3 and 2.5/5 are both 0.50. FD8.44 states one rule and FD8.441/.442
     * tabulate it twice, so agreeing is evidence the rule was read right rather than fitted.
     */
    @Test
    public void theTwoTablesAreOnePerSubmunitionPrice() {
        assertEquals(0.25, typeIMw(Propulsion.SPEED_8, Propulsion.SPEED_20)
                .submunitionUpgradeCost() / 3, 0.0001);
        assertEquals(0.25, typeIvMw(Propulsion.SPEED_8, Propulsion.SPEED_20)
                .submunitionUpgradeCost() / 5, 0.0001);
        assertEquals(0.50, typeIMw(Propulsion.SPEED_8, Propulsion.SPEED_32)
                .submunitionUpgradeCost() / 3, 0.0001);
        assertEquals(0.50, typeIvMw(Propulsion.SPEED_8, Propulsion.SPEED_32)
                .submunitionUpgradeCost() / 5, 0.0001);
    }

    /**
     * And it is FD10.52's half-space column, read as an upgrade from the type-VI's own baseline.
     *
     * <p>FD8.44 says "half the cost for a type-I drone receiving the speed upgrade", which is
     * FD2.226 — "Type-VI speed upgrades cost 1/2 the normal amount" — already in the chart. The
     * halving is not done here; the chart does it.
     */
    @Test
    public void thePerSubmunitionPriceIsTheHalfSpaceColumn() {
        double busUpgradeToMedium = Propulsion.SPEED_20.costOn(DroneFrame.ONE_SPACE)
                - Propulsion.SPEED_8.costOn(DroneFrame.ONE_SPACE);
        double perSubmunition = Propulsion.SPEED_20.costOn(DroneFrame.HALF_SPACE)
                - Propulsion.SPEED_12.costOn(DroneFrame.HALF_SPACE);
        assertEquals("half a type-I's upgrade, as FD8.44 words it",
                busUpgradeToMedium / 2, perSubmunition, 0.0001);
    }

    // ------------------------------------------------------- FD8.41: the type-III-MW, in full

    /**
     * FD8.41: "A type-III MW drone with slow speed and slow submunitions would cost 3.5 points,
     * and if replacing a type-I drone, the cost of the drone (one point) is deducted... so such
     * replacement would cost 2.5 points. <b>This assumes a Speed 8 type-III drone, which
     * theoretically could exist.</b>"
     *
     * <p>The same erratum FD10.54 explains and {@code DroneCostTest} already pins: the speed-8
     * type-III is a price in the table with no drone behind it.
     */
    @Test
    public void fd841TheSpeed8TypeIiiMw() {
        DroneSpec mw = typeIiiMw(Propulsion.SPEED_8, null);
        assertEquals(3.50, mw.pointCost(), 0.0001);
        assertEquals(2.50, mw.pointCost() - TYPE_I, 0.0001);
    }

    /**
     * FD8.41: "The standard Speed 12 type-III with Speed 12 submunitions would cost four points,
     * three points if replacing a type-I drone."
     *
     * <p><b>The example that decided the implementation.</b> Four points is the speed-8 drone's
     * 3.50 plus 0.50 for the bus engine and NOTHING for the submunitions — so speed-12
     * submunitions are free, because 12 is the type-VI's baseline speed (FD2.227) and not an
     * upgrade at all. Charging half a type-I's speed-12 upgrade, which is what FD8.44's sentence
     * says if read as arithmetic, would make this drone cost 4.75.
     */
    @Test
    public void fd841TheStandardTypeIiiMwPaysNothingForSpeed12Submunitions() {
        DroneSpec mw = typeIiiMw(Propulsion.SPEED_12, Propulsion.SPEED_12);
        assertEquals("no upgrade: speed 12 IS a type-VI's slow (FD2.227)",
                0.0, mw.submunitionUpgradeCost(), 0.0001);
        assertEquals(4.00, mw.pointCost(), 0.0001);
        assertEquals(3.00, mw.pointCost() - TYPE_I, 0.0001);

        assertEquals("and the bus engine is the only difference from the speed-8 drone",
                0.50, mw.pointCost() - typeIiiMw(Propulsion.SPEED_8, null).pointCost(), 0.0001);
    }

    /**
     * FD8.41: "Replacing a type-I drone with a type-III-MW drone (medium-speed frame with
     * medium-speed submunitions) costs 3.75 points... (fast-speed frame with fast-speed
     * submunitions) costs five points."
     */
    @Test
    public void fd841TheMediumAndFastTypeIiiMw() {
        assertEquals(3.75, typeIiiMw(Propulsion.SPEED_20, Propulsion.SPEED_20)
                .pointCost() - TYPE_I, 0.0001);
        assertEquals(5.00, typeIiiMw(Propulsion.SPEED_32, Propulsion.SPEED_32)
                .pointCost() - TYPE_I, 0.0001);
    }

    // -------------------------------------------------------- FD8.42: the type-IV-MW, in full

    /**
     * FD8.42: "A type-IV MW drone with slow speed and slow submunitions would cost 4.5 points,
     * and if replacing two type-I drones, the cost of the drones (two points) is deducted...
     * so such replacement would cost 2.5 points."
     */
    @Test
    public void fd842TheSlowTypeIvMw() {
        DroneSpec mw = typeIvMw(Propulsion.SPEED_8, null);
        assertEquals(4.50, mw.pointCost(), 0.0001);
        assertEquals("a two-space drone displaces TWO type-Is (FD2.1)",
                2.50, mw.pointCost() - 2 * TYPE_I, 0.0001);
    }

    /**
     * FD8.42: "Replacing two type-I drones with a single type-IV-MW (medium-speed frame with
     * medium-speed submunitions) costs 4.25 points... (fast-speed frame with fast-speed
     * submunitions) costs six points."
     */
    @Test
    public void fd842TheMediumAndFastTypeIvMw() {
        assertEquals(4.25, typeIvMw(Propulsion.SPEED_20, Propulsion.SPEED_20)
                .pointCost() - 2 * TYPE_I, 0.0001);
        assertEquals(6.00, typeIvMw(Propulsion.SPEED_32, Propulsion.SPEED_32)
                .pointCost() - 2 * TYPE_I, 0.0001);
    }

    // -------------------------------------------------------- FD8.45: the type-I-MW, in full

    /**
     * FD8.45: "A type-I-MW with slow speed (both bus and submunitions) would cost 3.0 points (or
     * 2.0 points if replacing a type-I). Replacing a type-I drone with a type-I-MW drone
     * (medium-speed frame with medium-speed submunitions) costs 3.25 points... (fast-speed frame
     * with fast-speed submunitions) costs 4.5 points."
     *
     * <p>Three submunitions on a one-space frame, so the whole drone is its warhead — and the
     * cheapest MW drone in the game still costs three type-I drones.
     */
    @Test
    public void fd845TheTypeIMwAtAllThreeSpeeds() {
        assertEquals(3.00, typeIMw(Propulsion.SPEED_8, null).pointCost(), 0.0001);
        assertEquals(2.00, typeIMw(Propulsion.SPEED_8, null).pointCost() - TYPE_I, 0.0001);
        assertEquals(3.25, typeIMw(Propulsion.SPEED_20, Propulsion.SPEED_20)
                .pointCost() - TYPE_I, 0.0001);
        assertEquals(4.50, typeIMw(Propulsion.SPEED_32, Propulsion.SPEED_32)
                .pointCost() - TYPE_I, 0.0001);
    }

    /**
     * What FD8.24's "extremely expensive" means in numbers: six type-VI engines are bought for
     * one drone — the bus's and one per submunition — and together they cost more than the MW
     * module they propel. The fast type-IV-MW is eight points, where the two type-I drones whose
     * rack space it takes are two.
     */
    @Test
    public void theSubmunitionEnginesCanOutweighTheModule() {
        DroneSpec mw = typeIvMw(Propulsion.SPEED_32, Propulsion.SPEED_32);
        assertEquals(8.00, mw.pointCost(), 0.0001);

        double busEngine = Propulsion.SPEED_32.costOn(DroneFrame.TWO_SPACE);
        assertEquals("2.00 bus engine + 2.50 submunition engines", 4.50,
                busEngine + mw.submunitionUpgradeCost(), 0.0001);
        assertTrue("engines 4.50 against a 3.50 module",
                busEngine + mw.submunitionUpgradeCost() > PayloadModule.MW_LARGE.cost);
        assertTrue("and the five submunitions' engines cost more than the bus's own",
                mw.submunitionUpgradeCost() > busEngine);
    }

    // ------------------------------------------------------------------ what is not allowed

    /**
     * FD8.44: "All submunitions on a given MW drone MUST receive the same speed upgrade. If one
     * is to be made a fast type-VI, all must be made fast."
     *
     * <p>Enforced by shape rather than by a check: there is one submunition speed per drone, so
     * a mixed set cannot be expressed. This test records that the rule is handled, since nothing
     * else in the suite would show it.
     */
    @Test
    public void allSubmunitionsShareOneSpeedBecauseThereIsOnlyOneField() {
        DroneSpec mw = typeIvMw(Propulsion.SPEED_8, Propulsion.SPEED_32);
        assertEquals(Propulsion.SPEED_32, mw.submunitionPropulsion());
        assertEquals("all five, at one price", 5 * 0.50, mw.submunitionUpgradeCost(), 0.0001);
    }

    /**
     * FD2.227: "type-III or type-VI slow drones are Speed 12 as noted in (FD2.1), not Speed 8."
     * FD10.52 agrees with a dash in the half-space column, so there is no speed-8 submunition to
     * ask for — FD8.45's "slow speed (both bus and submunitions)" means 12 for the type-VIs.
     */
    @Test
    public void thereAreNoSpeed8Submunitions() {
        try {
            typeIvMw(Propulsion.SPEED_8, Propulsion.SPEED_8);
            fail("a speed-8 type-VI does not exist (FD2.227)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD2.227"));
        }
    }

    /** A submunition speed on a drone with no submunitions is a mistake, not a loadout. */
    @Test
    public void onlyADroneWithSubmunitionsHasASubmunitionSpeed() {
        try {
            DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8, PayloadModule.EXPLOSIVE_1)
                    .withSubmunitionSpeed(Propulsion.SPEED_32);
            fail("an explosive drone carries no type-VIs (FD8.21)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD8.21"));
        }
    }

    /**
     * FD8.24: the submunitions' speed "could be a higher speed if that was available as a
     * restricted item (FD10.65)" — so it is not tied to the bus's. The worked examples pair them
     * because a player bought both, not because a rule requires it.
     */
    @Test
    public void submunitionsMayOutrunTheirBus() {
        DroneSpec mixed = typeIvMw(Propulsion.SPEED_20, Propulsion.SPEED_32);
        assertEquals("the bus still flies at 20", 20, mixed.speed());
        assertEquals(Propulsion.SPEED_32, mixed.submunitionPropulsion());
        assertEquals("1.50 bus + 3.50 module + 2.50 engines", 7.50, mixed.pointCost(), 0.0001);
    }

    /**
     * FD8.11 sells submunitions at "any speed available for the year", which bounds them the
     * same way the bus is bounded: fast drones are invented in Y178 (FD10.651), so a fast
     * submunition in Y170 is not restricted, it is impossible.
     */
    @Test
    public void submunitionsCannotOutrunTheirInventionDate() {
        DroneSpec fast = typeIvMw(Propulsion.SPEED_8, Propulsion.SPEED_32);
        assertFalse("fast submunitions in Y170", fast.existsIn(170));
        assertTrue(fast.existsIn(178));

        assertTrue("while its bus could have flown in Y170 either way",
                typeIvMw(Propulsion.SPEED_8, null).existsIn(170));
    }

    /**
     * FD10.631 puts the MW module at Limited, which is already the worst tier, so the
     * submunitions' own availability cannot change the drone's. Asserted so the day a cheaper
     * carrier module arrives, this is the test that notices.
     */
    @Test
    public void submunitionAvailabilityIsSubsumedByTheModulesOwn() {
        DroneSpec fast = typeIvMw(Propulsion.SPEED_8, Propulsion.SPEED_32);
        assertEquals(Availability.LIMITED, Propulsion.SPEED_32.availabilityIn(178));
        assertEquals(Availability.LIMITED, PayloadModule.MW_LARGE.availability);
        assertEquals(Availability.LIMITED, fast.availabilityIn(178));
        assertEquals("and in Y180, when fast drones are General but the module is not",
                Availability.LIMITED, fast.availabilityIn(180));
    }

    /** Nothing bought, nothing charged: the default is the type-VI as it comes (FD8.45). */
    @Test
    public void theDefaultSubmunitionCostsNothing() {
        DroneSpec mw = typeIvMw(Propulsion.SPEED_8, null);
        assertEquals(Propulsion.SPEED_12, mw.submunitionPropulsion());
        assertEquals(0.0, mw.submunitionUpgradeCost(), 0.0001);
        assertEquals("which is what MultiWarheadDroneTest's figures already assumed",
                4.50, mw.pointCost(), 0.0001);
    }

    /** And an upgraded one can still be built and loaded. */
    @Test
    public void anUpgradedMwDroneCanBeBuiltAndLoaded() {
        DroneSpec spec = typeIvMw(Propulsion.SPEED_32, Propulsion.SPEED_32);
        Drone d = new Drone(DroneType.TypeIV, spec);
        assertEquals(32, d.getSpeed());
        assertEquals("it carries drones, not a warhead", 0, d.impact());
        assertEquals(2.0, d.getRackSize(), 0.0001);
        assertEquals(Propulsion.SPEED_32, d.getSpec().submunitionPropulsion());
    }
}
