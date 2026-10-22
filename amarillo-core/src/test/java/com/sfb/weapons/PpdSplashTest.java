package com.sfb.weapons;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.function.IntUnaryOperator;

import org.junit.Test;

/**
 * E11.3's combat chart and E11.35's splash, which is where the PPD stops resembling any other
 * direct-fire weapon.
 */
public class PpdSplashTest {

    /** Every shield at the same strength, which is the TIE case the rules hand to the target. */
    private static final IntUnaryOperator ALL_EQUAL = s -> 20;

    /** A shield layout where one neighbour of #3 is clearly weaker than the other. */
    private static IntUnaryOperator weakest(int shield) {
        return s -> s == shield ? 1 : 20;
    }

    private static int total(Map<Integer, Integer> hit) {
        return hit.values().stream().mapToInt(Integer::intValue).sum();
    }

    // ------------------------------------------------------------------ the chart

    /**
     * THE THING THAT CATCHES PEOPLE. The PPD is a long-range weapon: nothing inside range 4,
     * strongest at 4-10, and still reaching 40. Every other direct-fire table runs the other
     * way, so a reader who assumes "closer is better" gets this exactly backwards.
     */
    @Test
    public void theChartRunsBackwards() {
        assertEquals("nothing at all inside range 4", 0, PpdSplash.totalDamage(2));
        assertEquals("peak at the near band", 6, PpdSplash.totalDamage(7));
        assertEquals(5, PpdSplash.totalDamage(12));
        assertEquals(4, PpdSplash.totalDamage(18));
        assertEquals(3, PpdSplash.totalDamage(22));
        assertEquals(2, PpdSplash.totalDamage(28));
        assertEquals("still one point at the far edge", 1, PpdSplash.totalDamage(40));
        assertEquals("and nothing beyond it", 0, PpdSplash.totalDamage(41));
    }

    /** Wave-lock gets harder as the range closes, which is the same inversion. */
    @Test
    public void theWaveLockNumberFallsWithRange() {
        assertEquals("no roll is possible inside range 4", 0, PpdSplash.hitNumber(3));
        assertEquals(9, PpdSplash.hitNumber(4));
        assertEquals(8, PpdSplash.hitNumber(15));
        assertEquals(4, PpdSplash.hitNumber(31));
        assertEquals(0, PpdSplash.hitNumber(41));
    }

    /** Band edges, because an off-by-one here silently misprices every shot near a boundary. */
    @Test
    public void theBandEdgesAreWhereTheChartSaysTheyAre() {
        assertEquals(0, PpdSplash.totalDamage(3));
        assertEquals(6, PpdSplash.totalDamage(4));
        assertEquals(6, PpdSplash.totalDamage(10));
        assertEquals(5, PpdSplash.totalDamage(11));
        assertEquals(2, PpdSplash.totalDamage(30));
        assertEquals(1, PpdSplash.totalDamage(31));
    }

    // ------------------------------------------------------- E11.352, against a shield

    /** The rule's own worked example: six points on the #3 shield are 1 / 4 / 1. */
    @Test
    public void theRulebooksOwnExample() {
        Map<Integer, Integer> hit = PpdSplash.againstShield(7, 3, ALL_EQUAL);

        assertEquals("four on the facing shield", Integer.valueOf(4), hit.get(3));
        assertEquals("one on each neighbour", Integer.valueOf(1), hit.get(2));
        assertEquals(Integer.valueOf(1), hit.get(4));
        assertEquals(6, total(hit));
    }

    /** Adjacency wraps: the neighbours of #1 are #6 and #2, not #0. */
    @Test
    public void theSplashWrapsAroundTheShip() {
        Map<Integer, Integer> hit = PpdSplash.againstShield(7, 1, ALL_EQUAL);

        assertEquals(Integer.valueOf(4), hit.get(1));
        assertEquals(Integer.valueOf(1), hit.get(2));
        assertEquals(Integer.valueOf(1), hit.get(6));
    }

    /**
     * E11.352: "In the case of two damage points, ignore the weaker adjacent shield." So the
     * zero in the chart's 1+1+0 row is not a fixed side -- it follows whichever neighbour is
     * weaker, and a model that hard-coded clockwise would be wrong half the time.
     */
    @Test
    public void atTwoPointsTheWeakerNeighbourIsSpared() {
        Map<Integer, Integer> cwWeak = PpdSplash.againstShield(28, 3, weakest(4));
        assertEquals("the centre always lands", Integer.valueOf(1), cwWeak.get(3));
        assertEquals("the stronger neighbour takes the splash", Integer.valueOf(1), cwWeak.get(2));
        assertNull("and the weaker one is ignored entirely", cwWeak.get(4));

        Map<Integer, Integer> ccwWeak = PpdSplash.againstShield(28, 3, weakest(2));
        assertEquals(Integer.valueOf(1), ccwWeak.get(4));
        assertNull("the other way round when the other neighbour is weaker", ccwWeak.get(2));
    }

    /** "In the case of one damage point, ignore both adjacent shields." */
    @Test
    public void atOnePointBothNeighboursAreSpared() {
        Map<Integer, Integer> hit = PpdSplash.againstShield(35, 3, ALL_EQUAL);

        assertEquals(1, hit.size());
        assertEquals(Integer.valueOf(1), hit.get(3));
    }

    /** Inside range 4 there is nothing to spread. */
    @Test
    public void thereIsNoSplashInsideRangeFour() {
        assertTrue(PpdSplash.againstShield(2, 3, ALL_EQUAL).isEmpty());
    }

    // ------------------------------------------------------- E11.351, along a hex side

    /**
     * "Do not determine which shield is hit but divide the damage points evenly between the
     * two." The totals match the splash row -- the same damage, spread differently.
     */
    @Test
    public void aHexSideSplitsBetweenTwoShields() {
        Map<Integer, Integer> hit = PpdSplash.alongHexSide(7, 2, 3, ALL_EQUAL);

        assertEquals(2, hit.size());
        assertEquals(Integer.valueOf(3), hit.get(2));
        assertEquals(Integer.valueOf(3), hit.get(3));
        assertEquals("the same six points as the splash row", 6, total(hit));
    }

    /** "With any odd points going to the stronger shield." */
    @Test
    public void theOddPointGoesToTheStrongerShield() {
        Map<Integer, Integer> hit = PpdSplash.alongHexSide(12, 2, 3, weakest(2));

        assertEquals("the stronger shield takes three of the five", Integer.valueOf(3), hit.get(3));
        assertEquals(Integer.valueOf(2), hit.get(2));
        assertEquals(5, total(hit));
    }

    /** At one point the weaker shield takes nothing at all. */
    @Test
    public void atOnePointOnlyTheStrongerShieldIsTouched() {
        Map<Integer, Integer> hit = PpdSplash.alongHexSide(35, 2, 3, weakest(2));

        assertEquals(1, hit.size());
        assertEquals(Integer.valueOf(1), hit.get(3));
    }

    // ------------------------------------------------------------------ the tie-break

    /**
     * Equal shields are the rulebook's "owner of the target decides". We take the lowest number
     * so the outcome is reproducible and needs no prompt and no die roll -- see PpdSplash's
     * class note for why. Pinned because it is a DECISION of ours, not a reading of the rule:
     * if it ever changes, it should change here deliberately.
     */
    @Test
    public void anEqualShieldTieGoesToTheLowerNumberedShield() {
        assertEquals("the hex-side odd point", Integer.valueOf(3),
                PpdSplash.alongHexSide(12, 2, 3, ALL_EQUAL).get(2));

        Map<Integer, Integer> splash = PpdSplash.againstShield(28, 3, ALL_EQUAL);
        assertEquals("the two-point splash element", Integer.valueOf(1), splash.get(2));
        assertNull(splash.get(4));
    }

    /** The tie-break is on SHIELD NUMBER, so it survives a wrap where 6 meets 1. */
    @Test
    public void theTieBreakSurvivesTheWrap() {
        Map<Integer, Integer> hit = PpdSplash.againstShield(28, 1, ALL_EQUAL);

        assertEquals("neighbours are 2 and 6, so 2 wins", Integer.valueOf(1), hit.get(2));
        assertNull(hit.get(6));
    }

    // ------------------------------------------------------------------ E11.353

    /** A target that never had shields takes the centre element and nothing else. */
    @Test
    public void aShieldlessTargetTakesTheCentreElementOnly() {
        assertEquals("the rule's own example: four points on a drone at range 6",
                4, PpdSplash.centreOnly(6));
        assertEquals(1, PpdSplash.centreOnly(35));
        assertEquals(0, PpdSplash.centreOnly(2));
    }
}
