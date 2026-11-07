package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.SpaceMine.MineType;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;

/**
 * Unit tests for SpaceMine (tBombs) — activation, detection, and dummy reveal.
 *
 * These tests exercise SpaceMine in isolation; Game-level integration
 * (transporter preconditions, mine processing loop) is covered in
 * TBombGameTest.
 */
public class SpaceMineTest {

    private Ship layingShip;

    @Before
    public void setUp() {
        layingShip = new Ship();
        layingShip.init(KlingonShips.getD7());
        layingShip.setName("D7");
        layingShip.setLocation(new Location(10, 10));
    }

    // -------------------------------------------------------------------------
    // Factory and initial state
    // -------------------------------------------------------------------------

    @Test
    public void createTBomb_isRealByDefault() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 1, true, false);
        assertTrue(mine.isReal());
        assertEquals(MineType.T_BOMB, mine.getMineType());
    }

    @Test
    public void createDummyTBomb_isNotReal() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 1, false, false);
        assertFalse(mine.isReal());
    }

    @Test
    public void createNSMine_isAlwaysReal() {
        SpaceMine mine = SpaceMine.createDroppedNSM(layingShip, 1);
        assertTrue(mine.isReal());
        assertEquals(MineType.NSM, mine.getMineType());
    }

    @Test
    public void newMine_isInactive() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 1, true, false);
        assertFalse(mine.isActive());
    }

    @Test
    public void newMine_isNotRevealed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 1, false, false);
        assertFalse(mine.isRevealed());
    }

    /**
     * The warheads, each quoted from the rule that states it — they come from DIFFERENT rules
     * rather than one table, which is how the NSM sat at 25 while the T-bomb's ten was right.
     *
     * <p>(M2.501) "A large explosive mine causes 35 points of damage to the shield facing the
     * mine hex." (M3.31) "A T-bomb operates as an (M2.0) large mine, with the exception that
     * the explosion is equal to ten damage points."
     */
    @Test
    public void mineType_damageValues() {
        assertEquals("M3.31: a transporter bomb is ten", 10, MineType.T_BOMB.damage);
        assertEquals("M2.501: a large mine is thirty-five", 35, MineType.NSM.damage);
    }

    // -------------------------------------------------------------------------
    // tryActivate — time condition
    // -------------------------------------------------------------------------

    @Test
    public void notActivated_whenLessThan2ImpulsesElapsed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        // Only 1 impulse elapsed — not enough
        mine.tryActivate(11, 5);
        assertFalse(mine.isActive());
    }

    @Test
    public void notActivated_atExactly1ImpulseElapsed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        mine.tryActivate(11, 5);
        assertFalse(mine.isActive());
    }

    @Test
    public void activated_whenAtLeast2ImpulsesElapsed_andLayerGone() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        // 2 impulses elapsed, layer at range 5
        mine.tryActivate(12, 5);
        assertTrue(mine.isActive());
    }

    @Test
    public void activated_onExactly2ImpulsesElapsed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        mine.tryActivate(12, 2);
        assertTrue(mine.isActive());
    }

    // -------------------------------------------------------------------------
    // tryActivate — transporter mines arm on timer only (M3.223), layer irrelevant
    // -------------------------------------------------------------------------

    @Test
    public void transporterMine_activates_regardlessOfLayerRange_whenTimeElapsed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        // 3 impulses elapsed, layer still adjacent (range 1) — must still arm
        mine.tryActivate(13, 1);
        assertTrue(mine.isActive());
    }

    @Test
    public void transporterMine_activates_whenLayerAtRange0() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        // Layer in same hex — timer-only rule still arms it
        mine.tryActivate(13, 0);
        assertTrue(mine.isActive());
    }

    // -------------------------------------------------------------------------
    // tryActivate — dropped mine arms on range (M2.34), not timer
    // -------------------------------------------------------------------------

    @Test
    public void droppedMine_notActivated_whenLayerWithin1Hex() {
        SpaceMine mine = SpaceMine.createDroppedTBomb(layingShip, 10, true);
        mine.tryActivate(999, 1);
        assertFalse(mine.isActive());
    }

    @Test
    public void droppedMine_activates_whenLayerAt2Hexes() {
        SpaceMine mine = SpaceMine.createDroppedTBomb(layingShip, 10, true);
        mine.tryActivate(999, 2);
        assertTrue(mine.isActive());
    }

    @Test
    public void droppedMine_activates_whenLayerBeyond2Hexes() {
        SpaceMine mine = SpaceMine.createDroppedTBomb(layingShip, 10, true);
        mine.tryActivate(999, 3);
        assertTrue(mine.isActive());
    }

    @Test
    public void droppedNSM_activates_whenLayerBeyond2Hexes() {
        SpaceMine mine = SpaceMine.createDroppedNSM(layingShip, 10);
        mine.tryActivate(999, 3);
        assertTrue(mine.isActive());
    }

    // -------------------------------------------------------------------------
    // tryActivate — permanence
    // -------------------------------------------------------------------------

    @Test
    public void activeState_isPermanent() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        mine.tryActivate(12, 5);
        assertTrue(mine.isActive());

        // Calling again with any range — stays armed
        mine.tryActivate(13, 0);
        assertTrue(mine.isActive());
    }

    // -------------------------------------------------------------------------
    // detectsUnit
    // -------------------------------------------------------------------------

    @Test
    public void detectsUnit_returnsFalse_whenMineInactive() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        // Mine not yet activated
        assertFalse(mine.detectsUnit(8, 1));
    }

    /**
     * (M2.40) A LOW roll triggers. "If the result is LESS THAN the unit's effective speed
     * (C2.451), the mine has triggered. If the result is GREATER THAN OR EQUAL TO the unit's
     * speed, the mine has been detected in time and avoided."
     *
     * <p>These three tests used to assert the opposite, which is how a rules bug lived here
     * from the first client/server commit until 2026-11-07: the code read
     * {@code diceRoll > speed} and the tests agreed with it. Tests written from the code rather
     * than the rule cannot catch the code being wrong — they only pin it in place.
     */
    @Test
    public void detectsUnit_rollBelowSpeed_triggers() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        mine.tryActivate(12, 5);

        // speed 5 → triggers on 1-4
        assertTrue(mine.detectsUnit(5, 2));
        assertTrue(mine.detectsUnit(5, 3));
        assertTrue(mine.detectsUnit(5, 4));
    }

    /** "Detected in time and avoided" — the roll that equals the speed is an ESCAPE. */
    @Test
    public void detectsUnit_rollEqualToOrAboveSpeed_isAvoided() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        mine.tryActivate(12, 5);

        assertFalse("roll 3 against speed 3 is avoided", mine.detectsUnit(3, 3));
        assertFalse("and anything above it", mine.detectsUnit(3, 4));
        assertFalse(mine.detectsUnit(3, 6));
    }

    /**
     * Creeping is SAFE, which is the whole point the inverted version got backwards: M2.40 notes
     * that "either nimbleness or cloaking may protect a slow ship from detonating every mine".
     * At speed two only a one gets through — and that one is (M2.42), not the speed comparison.
     */
    @Test
    public void detectsUnit_aCreepingShipIsNearlySafe() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        mine.tryActivate(12, 5);

        assertTrue("M2.42: a one always triggers", mine.detectsUnit(2, 1));
        for (int roll = 2; roll <= 6; roll++)
            assertFalse("speed two escapes on a " + roll, mine.detectsUnit(2, roll));
    }

    /**
     * (M2.42) "A die roll result of 'one' always results in the triggering of an active mine,
     * regardless of the speed of the triggering unit." Needed as its own case: nothing is less
     * than one, so a unit at speed one would otherwise be untouchable.
     */
    @Test
    public void detectsUnit_aRollOfOneAlwaysTriggers() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        mine.tryActivate(12, 5);

        assertTrue(mine.detectsUnit(1, 1));
        assertTrue(mine.detectsUnit(2, 1));
        assertTrue(mine.detectsUnit(5, 1));
    }

    /**
     * (M2.47) "At speeds of seven or more the die roll would have been automatic." SEVEN, not
     * six — and it needs no special case, because every die roll is less than seven. The old
     * code short-circuited at six, which made a six rolled against speed six a detonation when
     * the rule makes it an escape.
     */
    @Test
    public void detectsUnit_sevenOrMoreIsAutomaticAndSixIsNot() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        mine.tryActivate(12, 5);

        for (int roll = 1; roll <= 6; roll++)
            assertTrue("speed seven is automatic, roll " + roll, mine.detectsUnit(7, roll));
        for (int roll = 1; roll <= 6; roll++)
            assertTrue("and anything faster", mine.detectsUnit(12, roll));

        assertFalse("but a six against speed six escapes", mine.detectsUnit(6, 6));
        assertTrue("while a five does not", mine.detectsUnit(6, 5));
    }

    /**
     * (M2.43) A STATIONARY unit is not this method's problem, and that is why there is no test
     * asserting what it answers for speed zero.
     *
     * <p>"Impulses in which a moving unit does not actually move (i.e., change hexes) do not
     * require a die roll, unless the unit is conducting Erratic Maneuvers (C10.46)." So the
     * guard lives in the CALLER — {@code MineResolver} only rolls for a unit that changed hex
     * this impulse — and the four tests that used to sit here were all written from the
     * inverted comparison rather than from M2.40. One of them was named
     * {@code detectsUnit_stationary_rollOf1_notDetected} and asserted the opposite of its own
     * name, with a comment explaining that speed zero meant "always detected". That is a fair
     * description of what the code did and no description at all of the rule.
     */
    @Test
    public void aStationaryUnitIsTheCallersProblem() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true, false);
        mine.tryActivate(12, 5);

        // Asserted only so the claim above cannot rot: nothing is below zero, so the speed
        // comparison can never trigger, and M2.42's one is the only way through.
        for (int roll = 2; roll <= 6; roll++)
            assertFalse("speed zero cannot be caught by the comparison, roll " + roll,
                    mine.detectsUnit(0, roll));
    }

    // -------------------------------------------------------------------------
    // Dummy reveal
    // -------------------------------------------------------------------------

    @Test
    public void reveal_marksRevealedOnDummy() {
        SpaceMine dummy = SpaceMine.createTBomb(layingShip, 1, false, false);
        dummy.reveal();
        assertTrue(dummy.isRevealed());
    }

    @Test
    public void reveal_hasNoEffectOnRealMine() {
        SpaceMine real = SpaceMine.createTBomb(layingShip, 1, true, false);
        real.reveal();
        assertFalse(real.isRevealed());  // real mines cannot be "revealed"
    }

    @Test
    public void nsmIsAlwaysRealAndCannotBeRevealed() {
        SpaceMine nsm = SpaceMine.createDroppedNSM(layingShip, 1);
        nsm.reveal();
        assertFalse(nsm.isRevealed());
    }
}
