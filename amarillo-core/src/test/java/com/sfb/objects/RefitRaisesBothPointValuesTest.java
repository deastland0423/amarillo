package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.scenario.ScenarioLoader;

/**
 * A refit's cost is added to the ECONOMIC value as well as the combat one.
 *
 * <p>Owner's ruling 2026-10-11: "When an upgrade is applied to a ship that has both BPV and EPV,
 * the increase in points applies to both values."
 *
 * <p>Before this, only the combat value moved. A D7E with the B refit read 124/140 where its base
 * reads 120/140 — so the gap between the two ratings widened by the cost of every refit the ship
 * ever took, and a heavily refitted hull drifted further out the more it was upgraded. The gap is
 * a property of the ship, not an accumulator.
 *
 * <p>Two places add points and both had to change: {@code RefitResolver} for a named refit, which
 * works on the SPEC, and {@code ScenarioLoader.applyYearUpgrades} for a Y175 block's
 * {@code refitCost}, which works on the built SHIP.
 */
public class RefitRaisesBothPointValuesTest {

    @Before
    public void load() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private ShipSpec spec(String type) {
        ShipSpec s = ShipLibrary.get("Klingon", type);
        assertNotNull("fixture: " + type + " should be in the library", s);
        return s;
    }

    @Test
    public void aNamedRefitRaisesBothValues() {
        ShipSpec base   = spec("D7E");
        ShipSpec refit  = spec("D7EB");

        assertEquals("fixture: the D7E is a split-value ship", 120, base.bpv);
        assertEquals(140, base.epv);

        assertEquals("the B refit costs 4", base.bpv + 4, refit.bpv);
        assertEquals("and costs the same 4 economically", base.epv + 4, refit.epv);
    }

    /** The point of it: the gap belongs to the ship and must not widen with each refit. */
    @Test
    public void theGapBetweenTheTwoRatingsIsPreserved() {
        for (String[] pair : new String[][] {{"D7E","D7EB"}, {"D6E","D6EB"},
                                             {"F5J","F5JB"}, {"D6J","D6JB"}}) {
            ShipSpec base  = spec(pair[0]);
            ShipSpec refit = spec(pair[1]);
            assertEquals(pair[1] + " should keep " + pair[0] + "'s spread of "
                            + (base.epv - base.bpv),
                    base.epv - base.bpv, refit.epv - refit.bpv);
        }
    }

    /**
     * A hull with no economic rating of its own must not acquire one. ShipSpec.epv is a primitive,
     * so zero is the only way it can say "not a split-value ship", and adding to that zero would
     * invent a rating of four points.
     */
    @Test
    public void aShipWithNoEconomicRatingDoesNotGainOne() {
        ShipSpec base = spec("D7");
        assertEquals("fixture: the plain D7 states no epv", 0, base.epv);

        assertEquals("and its refits must not give it one", 0, spec("D7K").epv);
    }

    /**
     * The other half: a Y175 block's refitCost, applied to the built ship rather than the spec.
     *
     * <p>The D5J rather than the D6J, because the D6J's Y175 block is nested INSIDE its B refit —
     * so a D6J that never took the B refit never reaches this path at all, and the first version
     * of this test failed on a fixture that could not have worked. The D5J declares its Y175
     * upgrade at the top level, which is the case this code serves.
     */
    @Test
    public void theY175RefitCostRaisesBothValues() {
        ShipSpec base = spec("D5J");
        Ship early = ShipLibrary.createShip(base);
        ScenarioLoader.outfitForYear(early, "Klingon", 170, base);

        Ship late = ShipLibrary.createShip(base);
        ScenarioLoader.outfitForYear(late, "Klingon", 175, base);

        int cost = late.getBattlePointValue() - early.getBattlePointValue();
        assertTrue("fixture: the Y175 refit should cost something", cost > 0);
        assertEquals("the economic rating rises by the same amount",
                cost, late.getEconomicBpv() - early.getEconomicBpv());
    }

    /**
     * Across the whole library: where a hull states both ratings, the ECONOMIC one is the larger.
     *
     * <p>Annex #1's own key reads "Split BPVs are economic/combat ratings", and the owner's gloss
     * (2026-11-06) says why that ordering is not arbitrary: "A ship with special equipment
     * replacing weapons is still just as expensive to build as the original ship (EPV) but isn't
     * as effective in combat (BPV)." The Orion freighters are the convention in miniature --
     * F-L 61/18, worth carrying and not worth fighting.
     *
     * <p>Two hulls were entered backwards before this existed, both on 2026-11-05: the Klingon FWV
     * and the Orion BS, which read 200/75 as bpv/epv and so claimed a pirate base out-fights a
     * dreadnought. Neither was a reading error in the chart -- both were the cell copied in the
     * order it is printed. A sweep is the only thing that catches that, because each file on its
     * own looks perfectly sensible.
     *
     * <p>Equal values pass: the Kzinti SDF states 90/90, which is a ship whose two ratings simply
     * coincide. Zero passes too -- a primitive epv has no other way to say "not a split-value
     * ship", which {@link #aShipWithNoEconomicRatingDoesNotGainOne()} covers.
     */
    @Test
    public void theEconomicRatingIsNeverBelowTheCombatOne() {
        java.util.List<String> backwards = new java.util.ArrayList<>();
        int split = 0;
        for (ShipSpec s : ShipLibrary.all()) {
            if (s.epv <= 0)
                continue;
            split++;
            if (s.epv < s.bpv)
                backwards.add(s.faction + "/" + s.type + " states bpv " + s.bpv + ", epv " + s.epv
                        + " -- the chart prints " + s.epv + "/" + s.bpv + " and means the reverse");
        }
        assertTrue("fixture: the library should hold split-value hulls", split > 20);
        assertTrue("hulls with the two point values the wrong way round:" + nlJoin(backwards),
                backwards.isEmpty());
    }

    /** One hull per line, so a sweep that finds several reports all of them at once. */
    private static String nlJoin(java.util.List<String> lines) {
        String sep = System.lineSeparator() + "  ";
        return lines.isEmpty() ? "" : sep + String.join(sep, lines);
    }
}
