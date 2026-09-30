package com.sfb.utilities;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * When a unit next moves, off the impulse movement chart (C2.0).
 * <p>
 * The chart has always been in here and nothing outside core could see it, so a player had no
 * way to know their own movement cadence. That matters well beyond asteroids: whether a seeker
 * can be outrun, whether overload range will be reached on the right impulse, when a HET is
 * worth spending — all of them are "when do I move next?" questions.
 * <p>
 * P3.25 is the sharpest case, because fire into an asteroid hex counts only on the impulse
 * IMMEDIATELY before entry and the rule's failure is silent twice over: nothing tells you the
 * fire was wasted, and P3.251 notes you never learn whether it would have helped.
 */
public class NextMovingImpulseTest {

    /** Speed 32 moves on every impulse, so the answer is always the very next one. */
    @Test
    public void theFastestUnitMovesEveryImpulse() {
        for (int impulse = 1; impulse <= 32; impulse++) {
            int expected = impulse == 32 ? 1 : impulse + 1;
            assertEquals("from impulse " + impulse,
                    expected, ImpulseUtil.nextMovingImpulse(impulse, 32));
            assertEquals(1, ImpulseUtil.impulsesUntilNextMove(impulse, 32));
        }
    }

    /** A stationary unit never moves, and says so rather than naming an impulse. */
    @Test
    public void speedZeroNeverMoves() {
        assertEquals(0, ImpulseUtil.nextMovingImpulse(5, 0));
        assertEquals(0, ImpulseUtil.impulsesUntilNextMove(5, 0));
        assertEquals("a negative speed is not a movement schedule either",
                0, ImpulseUtil.nextMovingImpulse(5, -3));
    }

    /**
     * It looks STRICTLY ahead: the wait is never zero. A unit that moves on the current impulse
     * has either already done so or is about to, and neither answers "when do I move next?".
     * <p>
     * The impulse NUMBER can still repeat, which is not a contradiction — it means the wait is a
     * full turn. Speed 1 is the case: it moves only on impulse 32, so asked at impulse 32 the
     * answer is impulse 32, thirty-two impulses away.
     */
    @Test
    public void theWaitIsNeverZero() {
        for (int speed = 1; speed <= 32; speed++)
            for (int impulse = 1; impulse <= 32; impulse++) {
                int ahead = ImpulseUtil.impulsesUntilNextMove(impulse, speed);
                assertTrue("speed " + speed + " at impulse " + impulse + " gave " + ahead,
                        ahead >= 1 && ahead <= 32);
                if (ImpulseUtil.nextMovingImpulse(impulse, speed) == impulse)
                    assertEquals("the same impulse can only mean a full turn's wait",
                            32, ahead);
            }
    }

    /** The slowest unit in the game moves once a turn, on impulse 32, and nowhere else. */
    @Test
    public void speedOneMovesOnlyOnTheLastImpulse() {
        for (int impulse = 1; impulse <= 31; impulse++)
            assertFalse("speed 1 should not move on impulse " + impulse,
                    ImpulseUtil.doesMove(impulse, 1));
        assertTrue(ImpulseUtil.doesMove(32, 1));

        assertEquals(32, ImpulseUtil.nextMovingImpulse(1, 1));
        assertEquals("thirty-one impulses of waiting", 31,
                ImpulseUtil.impulsesUntilNextMove(1, 1));
        assertEquals("and a full turn from impulse 32 itself", 32,
                ImpulseUtil.impulsesUntilNextMove(32, 1));
    }

    /** Every answer it gives is a real entry on the chart. */
    @Test
    public void theAnswerIsAlwaysAnImpulseThatMoves() {
        for (int speed = 1; speed <= 32; speed++)
            for (int impulse = 1; impulse <= 32; impulse++) {
                int next = ImpulseUtil.nextMovingImpulse(impulse, speed);
                assertTrue("speed " + speed + " gave " + next, next >= 1 && next <= 32);
                assertTrue("speed " + speed + " does not move on impulse " + next,
                        ImpulseUtil.doesMove(next, speed));
            }
    }

    /** And the count agrees with the impulse, wrapping included. */
    @Test
    public void theCountAndTheImpulseAgree() {
        for (int speed = 1; speed <= 32; speed++)
            for (int impulse = 1; impulse <= 32; impulse++) {
                int next = ImpulseUtil.nextMovingImpulse(impulse, speed);
                int ahead = ImpulseUtil.impulsesUntilNextMove(impulse, speed);
                assertEquals("speed " + speed + " from impulse " + impulse,
                        next, ((impulse - 1 + ahead) % 32) + 1);
            }
    }

    /**
     * It wraps into the next turn rather than giving up at 32, and needs no special case for it:
     * impulse 32 moves every speed, so a slow unit late in a turn is told about impulse 32
     * itself.
     */
    @Test
    public void itWrapsPastTheEndOfTheTurn() {
        assertEquals("impulse 32 moves every speed", 32,
                ImpulseUtil.nextMovingImpulse(31, 1));
        // From impulse 32 the search runs into the next turn.
        int next = ImpulseUtil.nextMovingImpulse(32, 3);
        assertTrue("should be early in the next turn, was " + next, next >= 1 && next <= 32);
        assertTrue(ImpulseUtil.doesMove(next, 3));
    }

    /**
     * The P3.25 question, which is the one that prompted this: am I one impulse out? A speed-16
     * ship at impulse 15 moves on 16, so fire on 15 counts.
     */
    @Test
    public void oneImpulseOutIsTheAnswerP3_25Needs() {
        assertTrue("speed 16 moves on impulse 16", ImpulseUtil.doesMove(16, 16));
        assertEquals(16, ImpulseUtil.nextMovingImpulse(15, 16));
        assertEquals("one impulse out, so fire now counts (P3.25)",
                1, ImpulseUtil.impulsesUntilNextMove(15, 16));
    }
}
