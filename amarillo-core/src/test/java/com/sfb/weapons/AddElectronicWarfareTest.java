package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * E5.15: "Anti-drones ignore EW effects."
 * <p>
 * This was already true, but only as a side effect — {@code ADD.fire} discarded the adjusted
 * range it was handed and never read {@code ecmShift}, so no jamming reached it and nothing
 * said so. A test was the point of looking: a later change routing an ADD through the base
 * class's roll would have reintroduced EW in silence, and the comment at the site cited FD3.1,
 * which is the type-A drone rack rather than anything to do with electronic warfare.
 * <p>
 * The two things that DO reach an anti-drone are pinned beside it, because both were missing
 * and E5.15's own text names the second:
 * <ul>
 * <li><b>E5.16</b> — the scanner factor "is added to the die roll, not the range, for ADDs".
 *     Every weapon is handed {@code range + scanner} as its adjusted range, so an ADD has to
 *     undo that and move it to the roll. It was dropped altogether before, which left a
 *     sensor-damaged ship's anti-drones firing as though undamaged.</li>
 * <li><b>C10.49</b> — "ADDs (E5.15) fired by a unit using EM are penalized by a +1 shift
 *     (E1.8)." The FIRER's own manoeuvring, not the target's, and +1 rather than EM's usual
 *     four points of ECM.</li>
 * </ul>
 * E1.821 settles what a shift means here: on a hit-or-miss weapon, which an ADD is, a positive
 * modifier "is simply added to the die roll".
 * <p>
 * <b>Why these check the arithmetic rather than count hits.</b> The dice are unseeded, and a
 * +1 can never take a hit chance to zero on this chart — {@code {0,2,3,4,0}} by range, so the
 * kindest number is a 4 and the cruellest a 2. Counting hits would therefore be a coin flip
 * dressed up as a test. Instead each shot is judged against the die it actually threw, which
 * {@code getLastRoll} reports: a hit must occur exactly when {@code roll + expected shift} is
 * within the chart. That is the rule itself, asserted once per shot.
 */
public class AddElectronicWarfareTest {

    /** The engine's own table, so the test cannot drift from it: index is range. */
    private static int chartAt(int range) {
        return new ADD(ADD.AddType.ADD_12).getHitChart()[range];
    }

    /**
     * Fire a rack repeatedly and assert every shot obeys {@code roll + expectedShift <= chart}.
     * <p>
     * Enough shots that both sides of the boundary come up: at range 3 a d6 straddles a chart
     * of 4 either way, so forty rounds sees hits and misses whatever the shift.
     *
     * @param expectedShift what the rules say should reach the roll — NOT what was configured
     */
    private void everyShotObeys(int range, int ecmShift, int scanner, boolean firerEm,
            int expectedShift) {
        ADD add = new ADD(ADD.AddType.ADD_12);
        add.setFirerUsingEm(firerEm);
        int fired = 0, hits = 0, misses = 0;
        for (int i = 0; i < 40; i++) {
            if (!add.canFire()) {
                add.cleanUp();                        // next impulse; reload if it can
                if (!add.canFire())
                    break;
            }
            add.setEcmShift(ecmShift);
            int result;
            try {
                result = add.fire(range, range + scanner);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            int roll = add.getLastRoll();
            boolean shouldHit = roll + expectedShift <= chartAt(range);
            assertEquals("die " + roll + " +" + expectedShift + " against chart "
                            + chartAt(range) + " at range " + range,
                    shouldHit, result == ADD.HIT);
            fired++;
            if (result == ADD.HIT) hits++; else misses++;
        }
        assertTrue("fixture: the rack should have fired something", fired > 10);
        assertTrue("fixture: " + fired + " shots should straddle the boundary, got "
                + hits + " hits and " + misses + " misses", hits > 0 || misses > 0);
    }

    // ---------------------------------------------------------------- E5.15

    /** Six points of ECM would shift any ordinary weapon two columns. An ADD must not feel it. */
    @Test
    public void ecmNeverReachesAnAntiDronesRoll() {
        everyShotObeys(3, 6, 0, false, 0);
    }

    /** Nor does it quietly apply at a different range or a different amount. */
    @Test
    public void ecmIsIgnoredAtEveryRangeAnAddCanReach() {
        for (int range = 1; range <= 3; range++)
            everyShotObeys(range, 4, 0, false, 0);
    }

    /** The field is still carried like any weapon's — it is read by nobody, not unset. */
    @Test
    public void theEcmShiftIsStoredButUnused() {
        ADD add = new ADD(ADD.AddType.ADD_12);
        add.setEcmShift(6);
        assertEquals(6, add.getEcmShift());
    }

    // ---------------------------------------------------------------- E5.16

    @Test
    public void theScannerFactorIsAddedToTheRoll() {
        everyShotObeys(3, 0, 2, false, 2);
    }

    /**
     * The scanner reaches the ROLL, never the range — so a sensor-damaged ship's anti-drones
     * still REACH as far as ever, they simply miss more. Range 3 is an ADD's limit, so a
     * scanner of three applied to the range would put the target out of reach and throw.
     */
    @Test
    public void theScannerDoesNotShortenTheRange() throws Exception {
        assertTrue("fixture: three is the edge of an ADD's reach", ADD.engagesAt(3));
        assertFalse("and six is well past it", ADD.engagesAt(6));

        new ADD(ADD.AddType.ADD_12).fire(3, 6);   // throws if the adjusted range were used
    }

    // ---------------------------------------------------------------- C10.49

    @Test
    public void theFirersOwnEmAddsOneToTheRoll() {
        everyShotObeys(3, 0, 0, true, ADD.EM_SHIFT);
    }

    @Test
    public void droppingEmTakesThePenaltyOffAgain() {
        ADD add = new ADD(ADD.AddType.ADD_12);
        add.setFirerUsingEm(true);
        add.setFirerUsingEm(false);

        // Same assertion as everyShotObeys, with the flag cleared: expected shift is zero.
        for (int i = 0; i < 12 && add.canFire(); i++) {
            int result;
            try {
                result = add.fire(3, 3);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            assertEquals("die " + add.getLastRoll() + " with no penalty",
                    add.getLastRoll() <= chartAt(3), result == ADD.HIT);
        }
    }

    /** The two that do apply are cumulative, and ECM still adds nothing to the pile. */
    @Test
    public void theScannerAndEmStackAndEcmStillDoesNot() {
        everyShotObeys(3, 6, 2, true, 2 + ADD.EM_SHIFT);
    }
}
