package com.sfb.objects;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.sfb.scenario.CoiLoadout;

/**
 * {@code pointCost} in four terms, and the figures the COI dialog has to reproduce from them.
 *
 * <h2>Why the decomposition is a test and not just a refactor</h2>
 * The dialog prices drones a player is ASSEMBLING (FD10.2) — three armor modules on a type-IV, a
 * chosen engine for a multi-warhead drone's submunitions — and no catalogue holds the result, so
 * no single figure can be sent for one. The parts are sent instead, and the view adds them up.
 *
 * <p>That only stays honest while the parts still add up TO the price. {@code pointCost} is now
 * defined as their sum, which makes it true by construction today; this pins it against the day
 * someone adds a fifth kind of cost and reaches for {@code pointCost} alone.
 *
 * <h2>And the second half is the cross-tier pin</h2>
 * The figures below are asserted again, by hand, in {@code coiDroneCost.test.ts} — same drones,
 * same years, same numbers. Two tiers quoting one set of published values is what keeps the
 * mirror from drifting; if this file's numbers change, that one fails and says so.
 */
public class DroneCostDecompositionTest {

    private static final double EXACT = 1e-9;

    private static void sumsToThePrice(String name, DroneSpec spec) {
        assertEquals(name + ": the four terms must add up to the price",
                spec.pointCost(),
                spec.engineCost() + spec.payloadCost() + spec.frameCost()
                        + spec.submunitionUpgradeCost(),
                EXACT);
    }

    // ------------------------------------------------------------------ the decomposition

    /** Every drone history fielded, which is where a missing term would show up first. */
    @Test
    public void everyCatalogueDroneIsTheSumOfItsParts() {
        for (DroneType dt : DroneType.values())
            sumsToThePrice(dt.name(), dt.spec);
    }

    /**
     * And the built ones, across all four kinds of term: a payload module, a frame addition, the
     * frame FD10.24 grows, and FD8.44's submunition engines.
     */
    @Test
    public void composedDronesAreTheSumOfTheirPartsToo() {
        for (String name : List.of(
                "TypeI+SWORDFISH_PHASER3",
                "TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF",
                "TypeIV+EXPLOSIVE_HALF",
                "TypeIII+EXTENDED_RANGE",
                "TypeI+EXTERNAL_ARMOR_HALF",
                "TypeI+MW_SMALL+SPEED_20"))
            sumsToThePrice(name, DroneLoad.parse(name).spec());
    }

    /** FD2.11's six published prices, which is what makes the terms the right terms. */
    @Test
    public void fd211sPublishedPricesStillFallOut() {
        assertEquals(1.0, DroneType.TypeI.spec.pointCost(), EXACT);
        assertEquals(1.5, DroneType.TypeII.spec.pointCost(), EXACT);
        assertEquals(2.0, DroneType.TypeIII.spec.pointCost(), EXACT);
        assertEquals(2.0, DroneType.TypeIV.spec.pointCost(), EXACT);
        assertEquals(2.5, DroneType.TypeV.spec.pointCost(), EXACT);
        assertEquals(0.5, DroneType.TypeVI.spec.pointCost(), EXACT);
    }

    // ------------------------------------------------- the figures the view must reproduce

    private static double surcharge(String name, int year) {
        return CoiLoadout.surchargeOn(DroneLoad.parse(name), year);
    }

    /** A type-I-slow is the baseline, so it is free by definition (FD2.22). */
    @Test
    public void aTypeIslowCostsNothing() {
        assertEquals(0.0, surcharge("TypeI", 180), EXACT);
    }

    /**
     * S3.221's exception in one drone: a type-IF is standard issue in Y180 and the ship's own
     * bill carries it, while in Y179 fast speed is still Restricted and the player buys the last
     * half point out of the 20%.
     */
    @Test
    public void aFastDroneCostsTheBudgetOnlyBeforeItIsStandardIssue() {
        assertEquals(0.0, surcharge("TypeIF", 180), EXACT);
        assertEquals(0.50, surcharge("TypeIF", 179), EXACT);
    }

    /** FD10.51: a swordfish module is a point where the explosive it displaces is half. */
    @Test
    public void aSwordfishModuleCostsHalfAPoint() {
        assertEquals(0.50, surcharge("TypeI+SWORDFISH_PHASER3", 180), EXACT);
    }

    /** FD2.11's trades: rearranging a payload within its own spaces is free. */
    @Test
    public void anArrangedPayloadInTheSameSpacesIsFree() {
        assertEquals(0.0,
                surcharge("TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF", 180), EXACT);
    }

    /**
     * FD10.48: a half-filled type-IV is billed as a type-IV. One half-space explosive in a
     * two-space frame is 1.25 points of parts against 2.00 of displaced type-I-slows, so priced
     * from the parts it came out at −0.75 — a credit for carrying a worse drone, which FD2.11's
     * table never gives. The warhead was bought before it was discarded, so it is paid for.
     */
    @Test
    public void anUnderFilledPayloadIsBilledAsThoughItWereFull() {
        assertEquals(0.0, surcharge("TypeIV+EXPLOSIVE_HALF", 180), EXACT);
    }

    /** The extreme of the same rule: a drone launched with nothing aboard still cost its price. */
    @Test
    public void anEmptyPayloadSectionIsBilledAsThoughItWereFull() {
        assertEquals(0.0, surcharge("TypeIV", 180), EXACT);
        assertEquals(1.00, DroneLoad.parse("TypeIV").spec().payloadCost(), EXACT);
    }

    /**
     * FD10.24: a type-III given extended range "become[s] type-III-XX, which are two-space
     * drones", and FD10.52 prices its engine from the two-space column. The one case where the
     * view has to choose between two sets of figures, so the number it must land on is pinned.
     */
    @Test
    public void aTypeIiiWithExtendedRangeIsPricedOnTheGrownFrame() {
        assertEquals(0.50, surcharge("TypeIII+EXTENDED_RANGE", 180), EXACT);
    }

    /** FD8.44: the bus pays for every submunition's engine, over a type-VI's speed-12 slowest. */
    @Test
    public void aMultiWarheadDronePaysForEachSubmunitionsEngine() {
        assertEquals(2.75, surcharge("TypeI+MW_SMALL+SPEED_20", 180), EXACT);
    }

    /** FD12.132: external armor costs points on the frame and speed on the drone. */
    @Test
    public void externalArmorIsAFrameCost() {
        assertEquals(0.25, surcharge("TypeI+EXTERNAL_ARMOR_HALF", 180), EXACT);
    }

    /**
     * A rack is the plain sum of its drones, and that is the claim that broke before.
     *
     * <p>The gutted type-IV used to report −0.75 and the rack total was clamped at zero, so a
     * swordfish drone in the next space along rode home free: one rack space spending what
     * another had notionally saved. Billed per FD10.48 the type-IV is zero rather than negative,
     * there is nothing to spend, and the swordfish's half point is charged whether it sits
     * beside the type-IV or in a rack of its own.
     */
    @Test
    public void aRackIsThePlainSumOfItsDrones() {
        List<DroneLoad> together = List.of(
                DroneLoad.parse("TypeIV+EXPLOSIVE_HALF"),
                DroneLoad.parse("TypeI+SWORDFISH_PHASER3"));

        assertEquals("the gutted type-IV must not pay for the swordfish beside it",
                0.50, CoiLoadout.rackUpgradeCost(together, 180), EXACT);
        assertEquals(0.0,
                CoiLoadout.rackUpgradeCost(List.of(DroneLoad.parse("TypeIV+EXPLOSIVE_HALF")), 180),
                EXACT);
        assertEquals(0.50,
                CoiLoadout.rackUpgradeCost(
                        List.of(DroneLoad.parse("TypeI+SWORDFISH_PHASER3")), 180),
                EXACT);
    }

    /**
     * The invariant that lets {@code rackUpgradeCost} be a plain sum: no legal drone is worth
     * less than the type-I-slows it displaces. FD2.11's exchange table has no negative line, so
     * this is the rule rather than an accident of the charts.
     *
     * <p>Swept rather than argued, because the argument is only as good as the chart it reads:
     * every module prices at 0.50 a payload space or dearer and every modular catalogue drone
     * ships explosive at exactly 0.50, so a filled drone cannot undercut its stock price — true
     * today, and one cheap module away from false. Three modifiers deep over every drone the
     * parser will build, in the years that move FD10.65's ladder. Dropping the floor in
     * {@code DroneSpec.payloadCost} turns this red.
     */
    @Test
    public void noLegalDroneIsWorthLessThanWhatItDisplaces() {
        List<String> mods = DroneLoad.modifierNames();
        int[] years = {160, 166, 170, 175, 179, 180, 185};
        int built = 0;

        for (DroneType dt : DroneType.values()) {
            List<String> texts = new java.util.ArrayList<>();
            texts.add(dt.name());
            for (int i = 0; i < mods.size(); i++) {
                texts.add(dt.name() + "+" + mods.get(i));
                for (int j = i; j < mods.size(); j++) {
                    texts.add(dt.name() + "+" + mods.get(i) + "+" + mods.get(j));
                    for (int k = j; k < mods.size(); k++)
                        texts.add(dt.name() + "+" + mods.get(i) + "+" + mods.get(j)
                                + "+" + mods.get(k));
                }
            }
            for (String text : texts) {
                if (DroneLoad.refusalFor(text) != null)
                    continue;
                DroneLoad drone = DroneLoad.parse(text);
                built++;
                for (int year : years) {
                    double surcharge = CoiLoadout.surchargeOn(drone, year);
                    if (surcharge < -EXACT)
                        throw new AssertionError(text + " in Y" + year + " is worth " + surcharge
                                + " less than the type-I-slows it displaces, which FD2.11 has no"
                                + " line for — and a rack could spend it on the drone beside it");
                }
            }
        }
        assertEquals("the sweep must actually be building drones", 3704, built);
    }
}
