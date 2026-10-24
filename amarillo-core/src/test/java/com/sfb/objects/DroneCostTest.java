package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * FD10.5's cost charts, checked against FD2.11's published prices.
 *
 * <h2>Why FD2.11 is the oracle and not the arithmetic</h2>
 * The charts in FD10.51, FD10.52 and FD10.53 price a drone's parts; FD2.11 prices six finished
 * drones and six trades between them. Those are two independent statements in the rulebook, and
 * the second was written without reference to this model — so if the composition reproduces all
 * twelve, the charts have been read correctly, and if it does not, the error is here rather
 * than in a number someone typed.
 *
 * <blockquote>FD10.54: "Using these charts, any drone can be built and the cost calculated...
 * There is a chance for confusion between these costs and those shown in (FD2.1). The costs
 * here are for an extra drone ('bought on the open market' so to speak), while the costs in
 * (FD2.1) are the costs of trading in type-I-slow drones for the improved drones."</blockquote>
 */
public class DroneCostTest {

    private static final double EXACT = 0.0001;

    /** FD2.11, "The cost (S3.2) of an extra:" — all six, in the order the rulebook prints them. */
    @Test
    public void everyPublishedDronePriceFallsOutOfTheCharts() {
        assertEquals("type-I: 0.50 engine + 0.50 warhead",
                1.0, DroneType.TypeI.spec.pointCost(), EXACT);
        assertEquals("type-II: a speed-12 engine at 1.00, same warhead",
                1.5, DroneType.TypeII.spec.pointCost(), EXACT);
        assertEquals("type-III: the type-II plus FD10.53's 0.50 guidance surcharge",
                2.0, DroneType.TypeIII.spec.pointCost(), EXACT);
        assertEquals("type-IV: 1.00 engine on a two-space frame + two 0.50 warheads",
                2.0, DroneType.TypeIV.spec.pointCost(), EXACT);
        assertEquals("type-V: the type-IV with a 1.50 speed-12 engine",
                2.5, DroneType.TypeV.spec.pointCost(), EXACT);
        assertEquals("type-VI: 0.25 half-space engine + 0.25 warhead",
                0.5, DroneType.TypeVI.spec.pointCost(), EXACT);
    }

    /**
     * FD10.54's own worked example, spelled out because it is the one the rulebook chose to
     * show: "a standard type-V drone would cost 2.5 points (one for the double warhead and 1.5
     * for a Speed 12 engine)."
     */
    @Test
    public void theRulebooksWorkedExampleAddsUpTheSameWay() {
        DroneSpec typeV = DroneType.TypeV.spec;
        assertEquals("1.5 for the speed-12 engine on a two-space frame",
                1.5, typeV.propulsion().costOn(typeV.frame()), EXACT);
        double warheads = 0;
        for (PayloadModule m : typeV.allModules())
            warheads += m.cost;
        assertEquals("one for the double warhead", 1.0, warheads, EXACT);
        assertEquals(2.5, typeV.pointCost(), EXACT);
    }

    /**
     * FD2.11, "The cost (S3.2) of replacing:" — the trades, which are differences between the
     * prices above. A ship's BPV includes type-I-slow drones (FD2.22), so a trade costs what
     * the new drone costs less what the old ones did.
     */
    @Test
    public void everyPublishedReplacementCostIsADifferenceOfTwoPrices() {
        double typeI = DroneType.TypeI.spec.pointCost();

        assertEquals("one type-I with one type-II is 0.50",
                0.50, DroneType.TypeII.spec.pointCost() - typeI, EXACT);
        assertEquals("two type-Is with one type-IV is 0.00",
                0.00, DroneType.TypeIV.spec.pointCost() - 2 * typeI, EXACT);
        assertEquals("two type-Is with one type-V is 0.50",
                0.50, DroneType.TypeV.spec.pointCost() - 2 * typeI, EXACT);
        assertEquals("one type-I with two type-VIs is 0.00",
                0.00, 2 * DroneType.TypeVI.spec.pointCost() - typeI, EXACT);
    }

    /**
     * The entry in FD2.11 that looks like an error and is not: "one type-I with one type-III
     * is 0.50", when an extra type-III costs 2.0 and an extra type-I costs 1.0.
     *
     * <blockquote>FD2.11's note: "It costs 1.0 points to trade a Speed 8 type-I drone for a
     * Speed 12 type-III drone. For 0.5 points, you would get a Speed 8 type-III drone."</blockquote>
     *
     * <p>FD10.54 calls the speed-8 type-III a Basic Set erratum — "technically, there is no
     * such thing, but it is theoretically possible to build one". The model builds it, and it
     * prices at exactly the 0.5 difference, which is the strongest evidence available that the
     * charts have been read right: it reproduces a number the rulebook itself finds confusing.
     */
    @Test
    public void theSpeedEightTypeThreeExplainsFD211sConfusingLine() {
        DroneSpec speed8TypeIII = DroneSpec.of(
                DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                java.util.Set.of(FrameAddition.TYPE_III_GUIDANCE),
                PayloadModule.EXPLOSIVE_1);

        assertEquals("0.50 engine + 0.50 warhead + 0.50 guidance",
                1.5, speed8TypeIII.pointCost(), EXACT);
        assertEquals("which is 0.50 more than a type-I, exactly as FD2.11 prints",
                0.50, speed8TypeIII.pointCost() - DroneType.TypeI.spec.pointCost(), EXACT);
        assertEquals("while the real speed-12 type-III costs 1.0 more, as its note says",
                1.00, DroneType.TypeIII.spec.pointCost() - DroneType.TypeI.spec.pointCost(),
                EXACT);
    }

    /**
     * FD2.226 — "Type-VI speed upgrades cost 1/2 the normal amount (based on type-I upgrade
     * costs)" — with nothing halving anything.
     *
     * <p>FD10.52's chart is indexed by the FRAME, so a speed-20 engine is 1.00 on a one-space
     * drone and 0.50 on a half-space one. The upgrade from speed-12 therefore costs 0.50 and
     * 0.25 respectively, and the rule falls out of the table instead of being a special case
     * somebody has to remember.
     */
    @Test
    public void theTypeVISpeedUpgradeIsHalfPriceWithoutAnyoneHalvingIt() {
        // "Based on type-I upgrade costs" — so each frame is measured from its OWN baseline
        // drone, not from a common speed. The type-I's baseline is speed-8; the type-VI has no
        // speed-8 at all (FD2.227), so its baseline is the speed-12 it is printed at.
        //
        // Worth stating because the obvious comparison is wrong: on a one-space frame FD10.52
        // prices speed-12 and speed-20 identically at 1.00, so measuring the "medium upgrade"
        // from a type-II gives zero. An earlier draft of this test did exactly that and failed.
        double oneSpaceUpgrade = DroneType.TypeIM.spec.pointCost()
                - DroneType.TypeI.spec.pointCost();
        double halfSpaceUpgrade = DroneType.TypeVIM.spec.pointCost()
                - DroneType.TypeVI.spec.pointCost();

        assertEquals("type-I-slow to medium, which is FD2.223's half point",
                0.50, oneSpaceUpgrade, EXACT);
        assertEquals("and half that on the type-VI's frame", 0.25, halfSpaceUpgrade, EXACT);
        assertEquals("which is FD2.226's rule, as a consequence",
                oneSpaceUpgrade / 2, halfSpaceUpgrade, EXACT);
    }

    /**
     * FD2.223 and FD2.224's surcharges over the free type-I-slow baseline: medium costs half a
     * point per drone, fast costs one.
     */
    @Test
    public void theSpeedSurchargesMatchTheProseRules() {
        double baseline = DroneType.TypeI.spec.pointCost();
        assertEquals("FD2.223: medium speed costs 1/2 point per drone",
                0.50, DroneType.TypeIM.spec.pointCost() - baseline, EXACT);
        assertEquals("FD2.224: fast speed costs one point per drone",
                1.00, DroneType.TypeIF.spec.pointCost() - baseline, EXACT);
    }

    /**
     * FD12.31 and FD10.441 say an explosive module may be replaced by an armor or ECM module
     * "without cost". That is not a waiver — at a given payload size the prices are equal, so
     * the swap is free arithmetically.
     */
    @Test
    public void swappingAnExplosiveForArmorIsFreeBecauseThePricesMatch() {
        assertEquals(PayloadModule.EXPLOSIVE_HALF.cost, PayloadModule.ARMOR_HALF.cost, EXACT);
        assertEquals(PayloadModule.EXPLOSIVE_1.cost, PayloadModule.ARMOR_1.cost, EXACT);

        DroneSpec plain = DroneType.TypeIV.spec;
        DroneSpec armored = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_HALF, PayloadModule.ARMOR_HALF,
                PayloadModule.ARMOR_HALF, PayloadModule.EXPLOSIVE_HALF);
        assertEquals("the owner's armored type-IV costs what a standard one does",
                plain.pointCost(), armored.pointCost(), EXACT);
    }

    /**
     * FD2.22 at the Commander's Option: a rack arrives full of type-I-slow drones the BPV
     * already paid for, and the player pays the difference for anything better.
     *
     * <p>Four medium drones in a four-space rack is FD2.223's half a point each.
     */
    @Test
    public void theCoiChargesTheDifferenceOverTheDronesTheShipAlreadyHad() {
        com.sfb.scenario.CoiLoadout loadout = new com.sfb.scenario.CoiLoadout();
        loadout.setDroneRackLoadout(0, java.util.List.of(
                DroneType.TypeIM, DroneType.TypeIM, DroneType.TypeIM, DroneType.TypeIM));

        assertEquals("FD2.223: 1/2 point per drone, four drones",
                2.0, loadout.droneUpgradeCost(), EXACT);
        assertEquals("and it reaches the budget", 2.0, loadout.totalCost(), EXACT);
    }

    /** Loading the drones the ship came with costs nothing at all (FD2.31). */
    @Test
    public void reloadingWithWhatItAlreadyHadIsFree() {
        com.sfb.scenario.CoiLoadout loadout = new com.sfb.scenario.CoiLoadout();
        loadout.setDroneRackLoadout(0, java.util.List.of(
                DroneType.TypeI, DroneType.TypeI, DroneType.TypeI, DroneType.TypeI));
        assertEquals(0.0, loadout.droneUpgradeCost(), EXACT);
    }

    /**
     * FD2.11's free trades, driven through the COI rather than computed directly. Both are free
     * because the SPACES match, which is why the baseline is measured in spaces and not drones:
     * "two type-Is with one type-IV is 0.00", "one type-I with two type-VIs is 0.00".
     */
    @Test
    public void theFreeTradesAreFreeThroughTheLoadoutToo() {
        com.sfb.scenario.CoiLoadout heavy = new com.sfb.scenario.CoiLoadout();
        heavy.setDroneRackLoadout(0, java.util.List.of(DroneType.TypeIV, DroneType.TypeIV));
        assertEquals("two type-IVs fill four spaces for what four type-Is cost",
                0.0, heavy.droneUpgradeCost(), EXACT);

        com.sfb.scenario.CoiLoadout dogfight = new com.sfb.scenario.CoiLoadout();
        dogfight.setDroneRackLoadout(0, java.util.List.of(
                DroneType.TypeVI, DroneType.TypeVI, DroneType.TypeVI, DroneType.TypeVI));
        assertEquals("and four type-VIs fill two spaces for what two type-Is cost",
                0.0, dogfight.droneUpgradeCost(), EXACT);
    }

    /** Every rack is charged, because FD2.45 prices "a single loading of EACH launch rack". */
    @Test
    public void eachRackIsChargedSeparately() {
        com.sfb.scenario.CoiLoadout loadout = new com.sfb.scenario.CoiLoadout();
        loadout.setDroneRackLoadout(0, java.util.List.of(DroneType.TypeIF, DroneType.TypeIF));
        loadout.setDroneRackLoadout(1, java.util.List.of(DroneType.TypeIM, DroneType.TypeIM));

        assertEquals("two fast at one point each, plus two medium at a half",
                3.0, loadout.droneUpgradeCost(), EXACT);
    }

    /**
     * FD10.52 prints a DASH, not a price, for a speed-8 half-space engine — FD2.227's "type-III
     * or type-VI slow drones are Speed 12... not Speed 8". So the combination is refused rather
     * than priced at zero.
     */
    @Test
    public void thereIsNoSpeedEightDogfightDrone() {
        assertFalse(Propulsion.SPEED_8.fits(DroneFrame.HALF_SPACE));
        assertTrue(Propulsion.SPEED_12.fits(DroneFrame.HALF_SPACE));

        try {
            DroneSpec.fixed(DroneFrame.HALF_SPACE, Propulsion.SPEED_8);
            fail("a speed-8 dogfight drone is a dash in FD10.52, not a drone");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.52"));
        }
    }
}
