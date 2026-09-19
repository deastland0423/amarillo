package com.sfb.systemgroups;

import com.sfb.objects.Ship;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The quarter-turn cycle on lab boxes (G4.22, G4.45, G4.451).
 * <p>
 * G4.22: "Each lab can make one such attempt a turn, and not within a quarter turn of any
 * use in a previous turn." G4.451 gives it in impulses: "If a lab is used for any of these
 * functions during the last eight impulses of a turn, that same lab cannot be used for any
 * of those functions during the first eight impulses of the next turn."
 * <p>
 * Two separate restrictions, and both are needed. Once per turn alone would let a ship
 * identify at impulse 32 and again at impulse 1 — a quarter turn apart in the fiction but
 * two consecutive impulses at the table, which is exactly the trick the delay exists to
 * stop. The eight-impulse delay alone would let a lab work at impulse 1 and impulse 9 of
 * the same turn.
 * <p>
 * Impulses here are absolute: turn 1 is 1–32, turn 2 is 33–64.
 */
public class LabQuarterTurnCycleTest {

    private Labs labs(int boxes) {
        Labs l = new Labs(new Ship());
        Map<String, Object> values = new HashMap<>();
        values.put("lab", boxes);
        l.init(values);
        return l;
    }

    // ---------------------------------------------------------------- once per turn

    @Test
    public void aFreshLabIsAvailable() {
        assertEquals(2, labs(2).availableLabs(1));
    }

    @Test
    public void aBoxUsedIsNotAvailableAgainThatTurn() {
        Labs l = labs(1);
        assertTrue("a box was there to use", l.useLab(1) >= 0);

        assertEquals("one attempt a turn (G4.22)", 0, l.availableLabs(2));
        assertEquals("and still not by the end of it", 0, l.availableLabs(32));
    }

    @Test
    public void usingOneBoxLeavesTheOthers() {
        Labs l = labs(3);
        l.useLab(1);

        assertEquals(2, l.availableLabs(1));
    }

    @Test
    public void useLabReportsWhenNothingIsFree() {
        Labs l = labs(1);
        l.useLab(1);

        assertEquals("nothing left to claim", -1, l.useLab(2));
    }

    // ---------------------------------------------------------------- the delay

    /**
     * The case the rule is written for, and the one that matters in a seeker-heavy game:
     * identify late in a turn, and that box is still cooling off at the start of the next.
     */
    @Test
    public void aBoxUsedLateInATurnIsStillBusyEarlyInTheNext() {
        Labs l = labs(1);
        l.useLab(30);                      // turn 1, impulse 30

        assertEquals("impulse 1 of the next turn is two impulses later, not a quarter turn",
                0, l.availableLabs(33));
        assertEquals("still inside the delay at 37 (seven impulses on)",
                0, l.availableLabs(37));
        assertEquals("free at 38, a full eight impulses after use (G4.451)",
                1, l.availableLabs(38));
    }

    /** Eight impulses is the whole of it: at exactly eight, the box is free. */
    @Test
    public void theDelayIsExactlyEightImpulses() {
        Labs l = labs(1);
        l.useLab(32);                      // the last impulse of turn 1

        assertEquals(0, l.availableLabs(39));   // seven later
        assertEquals(1, l.availableLabs(40));   // eight later
    }

    /**
     * A box used EARLY in a turn is free from the first impulse of the next, because more
     * than eight impulses have passed. The delay is a delay, not a blanket ban on
     * consecutive turns.
     */
    @Test
    public void aBoxUsedEarlyIsFreeAtTheStartOfTheNextTurn() {
        Labs l = labs(1);
        l.useLab(3);                       // turn 1, impulse 3

        assertEquals("thirty impulses later, well past the delay", 1, l.availableLabs(33));
    }

    /**
     * The delay does NOT free a box within its own turn. Both clauses are live: eight
     * impulses after impulse 1 is impulse 9, and that box has already had its attempt.
     */
    @Test
    public void theDelayDoesNotOverrideOncePerTurn() {
        Labs l = labs(1);
        l.useLab(1);

        assertEquals("used this turn, whatever the gap (G4.22)", 0, l.availableLabs(9));
        assertEquals(0, l.availableLabs(32));
        assertEquals("but the next turn, eight impulses on, it is free", 1, l.availableLabs(33));
    }

    // ---------------------------------------------------------------- a held box

    @Test
    public void reStampingABoxRestartsItsDelay() {
        // A scout channel gets four attempts out of one box (G24.251), so the delay has to
        // run from its last attempt, not from when it first claimed the box.
        Labs l = labs(1);
        int box = l.useLab(20);
        l.markUsed(box, 31);

        assertEquals("eight impulses after 31, not after 20", 0, l.availableLabs(38));
        assertEquals(1, l.availableLabs(39));
    }

    // ---------------------------------------------------------------- damage vs. use

    /**
     * Using a lab is not the same as losing one. Both were one counter before, so a ship
     * that spent two labs reported two fewer boxes for cripple calculations, and a box
     * destroyed by damage came back at the start of the next turn.
     */
    @Test
    public void spendingALabIsNotDamage() {
        Labs l = labs(3);
        l.useLab(1);
        l.useLab(1);

        assertEquals("all three boxes still exist", 3, l.fetchRemainingTotalBoxes());
        assertEquals(3, l.getFunctioningLabs());
        assertEquals("they are merely busy", 1, l.availableLabs(1));
    }

    @Test
    public void aDestroyedBoxStaysDestroyed() {
        Labs l = labs(2);
        assertTrue(l.damage());

        assertEquals(1, l.fetchRemainingTotalBoxes());
        assertEquals("a new turn does not rebuild it", 1, l.availableLabs(33));
        assertEquals("nor any turn after that", 1, l.availableLabs(65));
    }

    @Test
    public void damageTakesABoxAlreadySpentBeforeAFreshOne() {
        // Nothing in the rules picks the box, so the choice is the player's, and a player
        // marks off one already used this turn.
        Labs l = labs(2);
        l.useLab(1);
        assertEquals(1, l.availableLabs(1));

        assertTrue(l.damage());

        assertEquals("the fresh box survived", 1, l.availableLabs(1));
        assertEquals(1, l.getFunctioningLabs());
    }

    @Test
    public void damageReportsWhenThereIsNothingLeft() {
        Labs l = labs(1);
        assertTrue(l.damage());
        assertFalse("no boxes remain to destroy", l.damage());
    }

    @Test
    public void repairBringsABoxBack() {
        Labs l = labs(2);
        l.damage();

        assertTrue(l.repair(1));
        assertEquals(2, l.getFunctioningLabs());
        assertFalse("and cannot exceed the SSD count", l.repair(1));
    }

    // ---------------------------------------------------------------- research

    /**
     * G4.11 multiplies by the number of FUNCTIONING lab boxes. It used to multiply by the
     * free ones, so identifying a seeker quietly cut the ship's research for the turn.
     */
    @Test
    public void researchCountsFunctioningBoxesNotIdleOnes() {
        Labs busy = labs(4);
        busy.useLab(1);
        busy.useLab(1);
        busy.useLab(1);
        busy.useLab(1);

        assertEquals("every box researches, busy or not", 0, busy.availableLabs(1));
        // At range 0 the worst die still yields 1 + 4 = 5 points per box, so a zero here
        // could only mean the boxes were not counted at all.
        assertTrue("four boxes should still be researching", busy.calculateResearchPoints(0) > 0);
    }
}
