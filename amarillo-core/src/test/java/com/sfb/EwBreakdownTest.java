package com.sfb;

import com.sfb.properties.EwBreakdown;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The two things about EW that are easy to get subtly wrong, pinned.
 * <p>
 * First, points are not the shift. Natural ECM accumulates a point per asteroid hex
 * (P3.33), but the die-roll modifier is the D6.34 Step 5 chart — the square root with
 * fractions dropped — so three hexes of field is still only +1, and it takes a fourth to
 * reach +2. Anyone "simplifying" the conversion to a straight sum would be changing the
 * game substantially, and these numbers say so.
 * <p>
 * Second, D6.3146: which of the five sources a FRIENDLY unit's action has to burn through.
 */
public class EwBreakdownTest {

    // ---------------------------------------------------------------- D6.34 Step 5 chart

    @Test
    public void theChartIsTheSquareRootWithFractionsDropped() {
        // The rulebook tabulates: 1-3 = 1, 4-8 = 2, 9-15 = 3, 16-24 = 4, 25-35 = 5.
        assertEquals("no ECM, no shift", 0, Game.netEcmShift(0));
        assertEquals(1, Game.netEcmShift(1));
        assertEquals("three points of ECM is still only +1", 1, Game.netEcmShift(3));
        assertEquals("the fourth point is what escalates it to +2", 2, Game.netEcmShift(4));
        assertEquals(2, Game.netEcmShift(8));
        assertEquals(3, Game.netEcmShift(9));
        assertEquals(3, Game.netEcmShift(15));
        assertEquals(4, Game.netEcmShift(16));
        assertEquals(4, Game.netEcmShift(24));
        assertEquals(5, Game.netEcmShift(25));
        assertEquals(5, Game.netEcmShift(35));
    }

    @Test
    public void ecmFullyCounteredLeavesNoRollAtAll() {
        // D6.34 Step 4: ECCM at or above the ECM means there is no EW effect, and a shift
        // of 0 means rollD637 never rolls — enough ECCM guarantees the system works.
        assertEquals(0, Game.netEcmShift(6 - 6));
        assertEquals("over-countering does not go negative", 0, Game.netEcmShift(2 - 6));
    }

    // ---------------------------------------------------------------- D6.3146 split

    /** One of each, so any source landing in the wrong bucket shows up. */
    private EwBreakdown oneOfEach() {
        return new EwBreakdown(1, 2, 4, 8, 16, 32);
    }

    @Test
    public void againstAnEnemyEverySourceCounts() {
        assertEquals(1 + 2 + 4 + 8 + 16 + 32, oneOfEach().total());
        assertEquals(oneOfEach().total(), oneOfEach().totalAgainst(false));
    }

    @Test
    public void againstAFriendlyOnlyNaturalAndOffensiveCount() {
        // D6.3146: ignore GENERATED (D6.3141), BUILT-IN (D6.3142) and LENT (D6.3144);
        // do NOT ignore NATURAL (D6.3143) or OFFENSIVE (D6.3145).
        assertEquals("natural 4 + offensive 32", 36, oneOfEach().totalFriendly());
        assertEquals(36, oneOfEach().totalAgainst(true));
    }

    @Test
    public void theWeaselIsNotAFriendlyUnitsProblem() {
        // Whatever the weasel question turns out to be against direct fire, a Wild Weasel
        // is a lending source (D6.3144) and so is ignored between friendly units.
        EwBreakdown wwOnly = new EwBreakdown(0, 0, 0, 0, 6, 0);
        assertEquals(6, wwOnly.total());
        assertEquals(0, wwOnly.totalFriendly());
    }

    @Test
    public void nothingIsNothing() {
        assertEquals(0, EwBreakdown.NONE.total());
        assertEquals(0, EwBreakdown.NONE.totalFriendly());
        assertEquals("none", EwBreakdown.NONE.describe());
    }

    @Test
    public void describeNamesOnlyWhatIsPresent() {
        String d = new EwBreakdown(0, 0, 3, 0, 0, 2).describe();
        assertTrue(d, d.contains("3 natural"));
        assertTrue(d, d.contains("2 offensive"));
        assertFalse("silent about sources that contribute nothing: " + d, d.contains("generated"));
    }
}
