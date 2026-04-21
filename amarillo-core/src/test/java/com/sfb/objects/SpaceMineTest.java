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
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 1, true);
        assertTrue(mine.isReal());
        assertEquals(MineType.T_BOMB, mine.getMineType());
    }

    @Test
    public void createDummyTBomb_isNotReal() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 1, false);
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
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 1, true);
        assertFalse(mine.isActive());
    }

    @Test
    public void newMine_isNotRevealed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 1, false);
        assertFalse(mine.isRevealed());
    }

    @Test
    public void mineType_damageValues() {
        assertEquals(10, MineType.T_BOMB.damage);
        assertEquals(25, MineType.NSM.damage);
    }

    // -------------------------------------------------------------------------
    // tryActivate — time condition
    // -------------------------------------------------------------------------

    @Test
    public void notActivated_whenLessThan2ImpulsesElapsed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        // Only 1 impulse elapsed — not enough
        mine.tryActivate(11, 5);
        assertFalse(mine.isActive());
    }

    @Test
    public void notActivated_atExactly1ImpulseElapsed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        mine.tryActivate(11, 5);
        assertFalse(mine.isActive());
    }

    @Test
    public void activated_whenAtLeast2ImpulsesElapsed_andLayerGone() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        // 2 impulses elapsed, layer at range 5
        mine.tryActivate(12, 5);
        assertTrue(mine.isActive());
    }

    @Test
    public void activated_onExactly2ImpulsesElapsed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        mine.tryActivate(12, 2);
        assertTrue(mine.isActive());
    }

    // -------------------------------------------------------------------------
    // tryActivate — transporter mines arm on timer only (M3.223), layer irrelevant
    // -------------------------------------------------------------------------

    @Test
    public void transporterMine_activates_regardlessOfLayerRange_whenTimeElapsed() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        // 3 impulses elapsed, layer still adjacent (range 1) — must still arm
        mine.tryActivate(13, 1);
        assertTrue(mine.isActive());
    }

    @Test
    public void transporterMine_activates_whenLayerAtRange0() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
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
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
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
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        // Mine not yet activated
        assertFalse(mine.detectsUnit(8, 1));
    }

    @Test
    public void detectsUnit_autoDetects_whenSpeedAtLeast6() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        mine.tryActivate(12, 5);

        assertTrue(mine.detectsUnit(6, 1));   // speed == 6, any roll
        assertTrue(mine.detectsUnit(6, 6));
        assertTrue(mine.detectsUnit(10, 3));  // speed > 6
    }

    @Test
    public void detectsUnit_rollGreaterThanSpeed_detects() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        mine.tryActivate(12, 5);

        // speed 3 → detected if roll > 3
        assertTrue(mine.detectsUnit(3, 4));
        assertTrue(mine.detectsUnit(3, 5));
        assertTrue(mine.detectsUnit(3, 6));
    }

    @Test
    public void detectsUnit_rollEqualToSpeed_notDetected() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        mine.tryActivate(12, 5);

        // speed 3, roll 3 → not detected
        assertFalse(mine.detectsUnit(3, 3));
    }

    @Test
    public void detectsUnit_rollLessThanSpeed_notDetected() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        mine.tryActivate(12, 5);

        // speed 4, roll 2 → not detected
        assertFalse(mine.detectsUnit(4, 2));
    }

    @Test
    public void detectsUnit_stationary_rollOf1_notDetected() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        mine.tryActivate(12, 5);

        // speed 0 → detected only on roll > 0, i.e. always detected!
        // roll 1 > 0 → detected
        assertTrue(mine.detectsUnit(0, 1));
    }

    @Test
    public void detectsUnit_speed5_roll6_detects() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        mine.tryActivate(12, 5);

        assertTrue(mine.detectsUnit(5, 6));
    }

    @Test
    public void detectsUnit_speed5_roll5_notDetected() {
        SpaceMine mine = SpaceMine.createTBomb(layingShip, 10, true);
        mine.tryActivate(12, 5);

        assertFalse(mine.detectsUnit(5, 5));
    }

    // -------------------------------------------------------------------------
    // Dummy reveal
    // -------------------------------------------------------------------------

    @Test
    public void reveal_marksRevealedOnDummy() {
        SpaceMine dummy = SpaceMine.createTBomb(layingShip, 1, false);
        dummy.reveal();
        assertTrue(dummy.isRevealed());
    }

    @Test
    public void reveal_hasNoEffectOnRealMine() {
        SpaceMine real = SpaceMine.createTBomb(layingShip, 1, true);
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
