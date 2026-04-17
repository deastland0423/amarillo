package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.systems.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Integration tests for Game.submitAllocation() — the energy allocation step
 * that happens at the start of each turn before impulse 1 begins.
 *
 * These tests build ships directly from sample data so no filesystem access
 * (ShipLibrary JSON files) is required.
 */
public class GameAllocateTest {

    private Game  game;
    private Ship  ship;

    @Before
    public void setUp() {
        game = new Game();
        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");
        ship.setActiveFireControl(true);  // pre-set as WS-2 would

        // Seed C2.2 history so the acceleration cap never interferes with allocation tests
        ship.setSpeedPreviousTurn(31);
        ship.setSpeedTwoTurnsAgo(31);

        // Add ship and put game into allocation state
        game.getShips().add(ship);
        game.startTurn();
    }

    // -------------------------------------------------------------------------
    // Allocation state preconditions
    // -------------------------------------------------------------------------

    @Test
    public void startTurn_setsAwaitingAllocation() {
        assertTrue(game.isAwaitingAllocation());
    }

    @Test
    public void startTurn_allShipsInPendingQueue() {
        assertEquals(1, game.getAllocationQueue().size());
        assertEquals("USS Enterprise", game.getAllocationQueue().get(0).getName());
    }

    // -------------------------------------------------------------------------
    // Speed allocation
    // -------------------------------------------------------------------------

    @Test
    public void allocate_setsRequestedSpeed() {
        Energy e = makeBaseEnergy();
        e.setWarpMovement(16 * ship.getPerformanceData().getMovementCost());

        game.submitAllocation(ship, e);

        assertEquals(16, ship.getSpeed());
    }

    @Test
    public void allocate_speedZero_shipStops() {
        Energy e = makeBaseEnergy();
        e.setWarpMovement(0);

        game.submitAllocation(ship, e);

        assertEquals(0, ship.getSpeed());
    }

    // -------------------------------------------------------------------------
    // Fire control
    // -------------------------------------------------------------------------

    @Test
    public void allocate_withFireControl_activeFireControlTrue() {
        Energy e = makeBaseEnergy();
        e.setFireControl(ship.getFireControlCost());

        game.submitAllocation(ship, e);

        assertTrue(ship.isActiveFireControl());
    }

    @Test
    public void allocate_withoutFireControl_activeFireControlFalse() {
        Energy e = makeBaseEnergy();
        e.setFireControl(0);  // no fire control paid

        game.submitAllocation(ship, e);

        assertFalse(ship.isActiveFireControl());
    }

    // -------------------------------------------------------------------------
    // Allocation queue management
    // -------------------------------------------------------------------------

    @Test
    public void submitAllocation_removesShipFromQueue() {
        game.submitAllocation(ship, makeBaseEnergy());

        assertTrue(game.getAllocationQueue().isEmpty());
    }

    @Test
    public void submitAllocation_lastShip_clearsAwaitingFlag() {
        game.submitAllocation(ship, makeBaseEnergy());

        assertFalse(game.isAwaitingAllocation());
    }

    @Test
    public void submitAllocation_withTwoShips_firstSubmitStillAwaiting() {
        Ship ship2 = new Ship();
        ship2.init(FederationShips.getFedFfg());
        ship2.setName("USS Reliant");
        game.getShips().add(ship2);
        game.startTurn();  // re-queue both ships

        game.submitAllocation(ship, makeBaseEnergy());

        assertTrue("Still awaiting second ship's allocation", game.isAwaitingAllocation());
        assertEquals(1, game.getAllocationQueue().size());
        assertEquals("USS Reliant", game.getAllocationQueue().get(0).getName());
    }

    @Test
    public void submitAllocation_returnsSuccess() {
        Game.ActionResult result = game.submitAllocation(ship, makeBaseEnergy());

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("USS Enterprise"));
    }

    // -------------------------------------------------------------------------
    // Impulse advancement
    // -------------------------------------------------------------------------

    @Test
    public void lastAllocation_advancesToImpulse1() {
        int impulseBefore = game.getCurrentImpulse();
        game.submitAllocation(ship, makeBaseEnergy());
        // After all ships allocate, beginImpulses() fires and advances to impulse 1
        assertEquals(impulseBefore + 1, game.getCurrentImpulse());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Minimum valid energy allocation: life support + fire control, speed 0,
     * active shields, no capacitor top-off. Enough to avoid validation errors.
     */
    private Energy makeBaseEnergy() {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        return e;
    }
}
