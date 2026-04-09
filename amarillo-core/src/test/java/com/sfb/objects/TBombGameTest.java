package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.TurnTracker;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;

/**
 * Integration-level tests for tBomb placement via Game.placeTBomb().
 *
 * Uses a minimal game setup — one ship, no full allocation cycle — by
 * directly adding the ship and advancing to ACTIVITY phase via advancePhase().
 * SpaceMine unit tests (activation, detection) live in SpaceMineTest.
 */
public class TBombGameTest {

    private Game  game;
    private Ship  ship;

    /**
     * Builds a minimal game with one ship at (10, 10) facing hex 1,
     * advanced to ACTIVITY phase at impulse 1.
     */
    @Before
    public void setUp() {
        TurnTracker.reset();

        game = new Game();

        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("Enterprise");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);  // facing "up" / toward lower y

        // Give the ship transporter energy (5 uses worth)
        ship.getTransporters().bankEnergy(1.0);

        // Give it tBombs and dummy tBombs
        ship.setTBombs(3);
        ship.setDummyTBombs(2);

        game.getShips().add(ship);

        // Advance from MOVEMENT → ACTIVITY
        // (moveSeekers, moveShuttles, processMines are no-ops with no content)
        game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    // -------------------------------------------------------------------------
    // Phase guard
    // -------------------------------------------------------------------------

    @Test
    public void placeTBomb_failsWhenNotActivityPhase() {
        // Advance to DIRECT_FIRE — no longer ACTIVITY
        game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());

        Location target = new Location(10, 11); // range 1
        ActionResult result = game.placeTBomb(ship, target, true);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Activity phase"));
    }

    // -------------------------------------------------------------------------
    // Range check
    // -------------------------------------------------------------------------

    @Test
    public void placeTBomb_succeedsAtRange5() {
        Location target = new Location(10, 15); // 5 hexes away (same column)
        ActionResult result = game.placeTBomb(ship, target, true);

        assertTrue(result.isSuccess());
    }

    @Test
    public void placeTBomb_failsAtRange6() {
        Location target = new Location(10, 16); // 6 hexes away
        ActionResult result = game.placeTBomb(ship, target, true);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("out of transporter range"));
    }

    // -------------------------------------------------------------------------
    // Transporter energy
    // -------------------------------------------------------------------------

    @Test
    public void placeTBomb_failsWithNoTransporterEnergy() {
        // Drain all banked energy by exhausting available uses
        while (ship.getTransporters().availableUses() > 0) {
            ship.getTransporters().useTransporter();
        }

        Location target = new Location(10, 11);
        ActionResult result = game.placeTBomb(ship, target, true);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("transporter energy"));
    }

    // -------------------------------------------------------------------------
    // Inventory checks
    // -------------------------------------------------------------------------

    @Test
    public void placeTBomb_failsWhenNoRealTBombs() {
        ship.setTBombs(0);

        Location target = new Location(10, 11);
        ActionResult result = game.placeTBomb(ship, target, true);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("No tBombs"));
    }

    @Test
    public void placeTBomb_failsWhenNoDummyTBombs() {
        ship.setDummyTBombs(0);

        Location target = new Location(10, 11);
        ActionResult result = game.placeTBomb(ship, target, false);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("No dummy tBombs"));
    }

    // -------------------------------------------------------------------------
    // Happy path — real tBomb
    // -------------------------------------------------------------------------

    @Test
    public void placeTBomb_real_succeedsAndPlacesMine() {
        Location target = new Location(10, 11);

        ActionResult result = game.placeTBomb(ship, target, true);

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals(1, game.getMines().size());
        SpaceMine mine = game.getMines().get(0);
        assertEquals(target, mine.getLocation());
        assertTrue(mine.isReal());
        assertEquals(ship, mine.getLayingShip());
    }

    @Test
    public void placeTBomb_real_decrementsInventory() {
        int startingTBombs = ship.getTBombs();
        int startingDummies = ship.getDummyTBombs();

        game.placeTBomb(ship, new Location(10, 11), true);

        assertEquals(startingTBombs - 1, ship.getTBombs());
        assertEquals(startingDummies,    ship.getDummyTBombs()); // unchanged
    }

    @Test
    public void placeTBomb_real_consumesTransporterEnergy() {
        int usesBefore = ship.getTransporters().availableUses();

        game.placeTBomb(ship, new Location(10, 11), true);

        assertEquals(usesBefore - 1, ship.getTransporters().availableUses());
    }

    // -------------------------------------------------------------------------
    // Happy path — dummy tBomb
    // -------------------------------------------------------------------------

    @Test
    public void placeTBomb_dummy_succeedsAndPlacesDummyMine() {
        Location target = new Location(10, 12);

        ActionResult result = game.placeTBomb(ship, target, false);

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals(1, game.getMines().size());
        SpaceMine mine = game.getMines().get(0);
        assertFalse(mine.isReal());
        assertEquals(target, mine.getLocation());
    }

    @Test
    public void placeTBomb_dummy_decrementsDummyInventory() {
        int startingTBombs  = ship.getTBombs();
        int startingDummies = ship.getDummyTBombs();

        game.placeTBomb(ship, new Location(10, 12), false);

        assertEquals(startingTBombs,      ship.getTBombs());      // unchanged
        assertEquals(startingDummies - 1, ship.getDummyTBombs());
    }

    // -------------------------------------------------------------------------
    // Multiple placements
    // -------------------------------------------------------------------------

    @Test
    public void placeTBomb_multipleMinesAccumulate() {
        // Bank enough energy for 3 uses
        ship.getTransporters().bankEnergy(0.4);
        ship.setTBombs(5);

        game.placeTBomb(ship, new Location(10, 11), true);
        game.placeTBomb(ship, new Location(10, 12), true);
        game.placeTBomb(ship, new Location(11, 11), true);

        assertEquals(3, game.getMines().size());
    }

    // -------------------------------------------------------------------------
    // Mine starts inactive
    // -------------------------------------------------------------------------

    @Test
    public void placedMine_startsInactive() {
        game.placeTBomb(ship, new Location(10, 11), true);

        SpaceMine mine = game.getMines().get(0);
        assertFalse(mine.isActive());
    }

    @Test
    public void placedMine_activatesAfter2ImpulsesAndLayerGone() {
        game.placeTBomb(ship, new Location(10, 11), true);

        SpaceMine mine = game.getMines().get(0);
        int placedImpulse = mine.getPlacedOnImpulse();

        // Move the ship far away (range > 1)
        ship.setLocation(new Location(10, 20));

        // Simulate 2 impulses elapsed, layer at range 9
        mine.tryActivate(placedImpulse + 2, 9);

        assertTrue(mine.isActive());
    }

    @Test
    public void placedMine_doesNotActivateWhileLayerIsClose() {
        game.placeTBomb(ship, new Location(10, 11), true);

        SpaceMine mine = game.getMines().get(0);
        int placedImpulse = mine.getPlacedOnImpulse();

        // 5 impulses elapsed but layer is still adjacent (range 1)
        mine.tryActivate(placedImpulse + 5, 1);

        assertFalse(mine.isActive());
    }
}
