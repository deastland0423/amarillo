package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack.DroneRackType;

/**
 * Type-G, slice 3: the rack actually fires (FD3.70, E5.0).
 * <p>
 * "The G rack can carry four spaces of drones, and it is equipped with <b>targeting system
 * for anti-drones (E5.0)</b>." So the shot is an ADD's shot — the same range band, the same
 * to-hit numbers — and the two must ask ONE table rather than keep a copy each.
 * <p>
 * This is also where a rack stops being categorically un-fireable. It now implements
 * {@link DirectFire}, and what keeps an ordinary rack out of a volley is
 * {@link DirectFire#canBeFiredAtTarget()} rather than its class — which is what
 * DamageResolver's own comment asked for long before the rule was built.
 */
public class TypeGAntiDroneFireTest {

    private static final int Y175 = 175;

    private TurnTracker clock;
    private DroneRack rack;

    @Before
    public void setUp() {
        clock = new TurnTracker();
        rack = new DroneRack(DroneRackType.TYPE_G);
        rack.setDesignator("Rack 1");
        rack.setClock(clock);
        rack.loadAntiDrones(4, Y175);
        impulses(9);
    }

    private void impulses(int n) {
        for (int i = 0; i < n; i++)
            clock.nextImpulse();
    }

    // ---------------------------------------------------------------- one table, two weapons

    @Test
    public void theRackShootsOnTheAddsChart() {
        assertFalse("never at range 0", ADD.engagesAt(0));
        assertTrue(ADD.engagesAt(1));
        assertTrue(ADD.engagesAt(3));
        assertFalse("and never past three", ADD.engagesAt(4));

        assertTrue("range 1 hits on 1-2", ADD.hitsAt(1, 2));
        assertFalse(ADD.hitsAt(1, 3));
        assertTrue("range 3 hits on 1-4", ADD.hitsAt(3, 4));
        assertFalse(ADD.hitsAt(3, 5));
    }

    @Test
    public void aShotIsEitherAHitOrAMiss() throws Exception {
        int result = rack.fire(2);

        assertTrue("an anti-drone either connects or it does not",
                result == ADD.HIT || result == 0);
        assertEquals("and it spent a round", 3, rack.getAddAmmo());
    }

    @Test(expected = TargetOutOfRangeException.class)
    public void itCannotReachBeyondRangeThree() throws Exception {
        rack.fire(4);
    }

    @Test(expected = WeaponUnarmedException.class)
    public void anEmptyRackHasNothingToFire() throws Exception {
        DroneRack empty = new DroneRack(DroneRackType.TYPE_G);
        empty.setClock(clock);
        empty.fire(2);
    }

    /** FD3.1: an anti-drone fires at REAL range, so a scanner shift changes nothing. */
    @Test
    public void aScannerShiftDoesNotPushTheTargetAway() throws Exception {
        int before = rack.getAddAmmo();

        rack.fire(3, 9);   // real range 3, adjusted 9 — out of band if adjusted counted

        assertEquals("it fired, so it used the real range", before - 1, rack.getAddAmmo());
    }

    // ---------------------------------------------------------------- the capability

    @Test
    public void aLoadedTypeGMayBeFiredAtATarget() {
        assertTrue(rack instanceof DirectFire);
        assertTrue(((DirectFire) rack).canBeFiredAtTarget());
    }

    @Test
    public void anOrdinaryRackMayNot() {
        DroneRack typeA = new DroneRack(DroneRackType.TYPE_A);
        typeA.setClock(clock);
        typeA.getAmmo().add(new Drone(DroneType.TypeI));

        assertTrue("it reaches the fire path", typeA instanceof DirectFire);
        assertFalse("but it has no anti-drone targeting system",
                ((DirectFire) typeA).canBeFiredAtTarget());
    }

    @Test
    public void norDoesATypeGWithNoRoundsLeft() throws Exception {
        for (int i = 0; i < 4; i++) {
            rack.fire(2);
            impulses(1);
        }

        assertEquals(0, rack.getAddAmmo());
        assertFalse(((DirectFire) rack).canBeFiredAtTarget());
    }

    // ---------------------------------------------------------------- the mode still binds

    @Test
    public void firingCommitsTheRackForTheTurn() throws Exception {
        rack.fire(2);

        assertEquals(DroneRack.RackMode.ANTI_DRONE, rack.getModeThisTurn());
        assertFalse("no drone launch this turn (FD3.71)", rack.canFire());
    }

    @Test
    public void aRackThatLaunchedADroneRefusesToFire() {
        rack.recordLaunch();

        try {
            rack.fire(2);
            fail("a rack in drone mode should refuse an anti-drone");
        } catch (WeaponUnarmedException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD3.71"));
        } catch (TargetOutOfRangeException e) {
            fail("wrong refusal: " + e.getMessage());
        }
    }

    /** And every shot is still one per impulse, as slice 2 established. */
    @Test
    public void oneRoundPerImpulse() throws Exception {
        rack.fire(2);

        try {
            rack.fire(2);
            fail("a second round in the same impulse should be refused");
        } catch (WeaponUnarmedException expected) {
            // the rack is committed and already fired this impulse
        }

        assertEquals("the refused shot spent nothing", 3, rack.getAddAmmo());

        impulses(1);
        rack.fire(2);
        assertEquals("but the next impulse is free", 2, rack.getAddAmmo());
    }
}
