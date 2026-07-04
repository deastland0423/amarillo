package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Behavioral safety net for ship movement, built ahead of the ShipMover
 * extraction. Pins the composite behaviors of Game.moveForward() and friends:
 * impulse-chart gating, movedThisImpulse bookkeeping, planet/asteroid terrain,
 * map-edge disengagement, and tractor drag coupling (G7.36/G7.5).
 *
 * These tests assert CURRENT behavior so the extraction can be verified as a
 * pure move. They use an advance-until-movable helper rather than hardcoded
 * impulse numbers so they are robust to speed/impulse-chart details.
 */
public class ShipMovementTest {

    private Game game;
    private Ship fed;     // FedCA at (10,10) facing 1 (direction A = -y)
    private Ship klingon; // D7 at (11,10) facing 1

    @Before
    public void setUp() {
        TurnTracker.reset();
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(11, 10));
        klingon.setFacing(1);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Energy makeAllocation(Ship ship, double warpMovement) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warpMovement);
        return e;
    }

    /** Submit allocations (fed first) and land in the impulse loop. */
    private void allocate(double fedWarp, double klingonWarp) {
        game.submitAllocation(fed,     makeAllocation(fed,     fedWarp));
        game.submitAllocation(klingon, makeAllocation(klingon, klingonWarp));
    }

    /**
     * Advance phases until the given ship is movable in a MOVEMENT phase.
     * Robust to the impulse chart (speed 32 is the only speed that moves on
     * impulse 1) and to the INITIAL_ACTIVITY phase when tractor links exist.
     */
    private void advanceUntilCanMove(Ship ship) {
        for (int guard = 0; guard < 60; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT
                    && game.canMoveThisImpulse(ship))
                return;
            game.advancePhase();
        }
        fail("Ship " + ship.getName() + " never became movable (speed "
                + ship.getSpeed() + ", impulse " + game.getCurrentImpulse()
                + ", phase " + game.getCurrentPhase() + ")");
    }

    /** Link klingon into fed's tractor with a small pool (pre-allocation). */
    private void linkKlingon() {
        fed.getTractors().initForTurn(5);
        fed.getPowerSystems().setBatteryPower(0);
        fed.getTractors().linkUnit(klingon);
    }

    private Drone makeHeldDrone(int col, int row) {
        Drone d = new Drone(DroneType.TypeI);
        d.setName("Drone-1");
        d.setLocation(new Location(col, row));
        game.getSeekers().add(d);
        fed.getTractors().initForTurn(5);
        fed.getPowerSystems().setBatteryPower(0);
        fed.getTractors().linkUnit(d);
        return d;
    }

    // -------------------------------------------------------------------------
    // Basic movement and impulse gating
    // -------------------------------------------------------------------------

    @Test
    public void moveForward_advancesOneHexInFacingDirection() {
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(10, 9), fed.getLocation()); // facing 1 = -y
        assertTrue(game.hasMovedThisImpulse(fed));
    }

    @Test
    public void moveForward_secondMoveSameImpulse_refused() {
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);
        assertTrue(game.moveForward(fed).isSuccess());

        Game.ActionResult second = game.moveForward(fed);

        assertFalse("A ship may move only once per impulse", second.isSuccess());
        assertEquals(new Location(10, 9), fed.getLocation());
    }

    @Test
    public void moveForward_refusedWhenNotScheduledToMove() {
        allocate(30.0, 0.0); // klingon speed 0 — never scheduled
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.moveForward(klingon);

        assertFalse("Speed-0 ship must not be movable", r.isSuccess());
        assertEquals(new Location(11, 10), klingon.getLocation());
    }

    @Test
    public void turnLeft_movesAndRotatesFacing() {
        // Turn-mode records are continuous across turns (C1.341); with straight
        // movement seeded from the previous turn the first-hex turn is legal.
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.turnLeft(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("Facing 1 turned left becomes 21", 21, fed.getFacing());
        assertTrue(game.hasMovedThisImpulse(fed));
        // A turn moves the ship one hex in the NEW facing: dir 21 from (10,10) → (9,10)
        assertEquals(new Location(9, 10), fed.getLocation());
    }

    // -------------------------------------------------------------------------
    // Terrain
    // -------------------------------------------------------------------------

    @Test
    public void moveForward_intoPlanet_destroysShip() {
        game.addTerrain(new Terrain(TerrainType.PLANET, 10, 9)); // fed's next hex
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("Log must report destruction", r.getMessage().contains("destroyed"));
        assertNull("Destroyed ship has no location", fed.getLocation());
        assertFalse("Destroyed ship leaves the ships list", game.getShips().contains(fed));
    }

    @Test
    public void moveForward_intoAsteroidHex_rollsCollision() {
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(10, 9), fed.getLocation()); // asteroids don't block
        assertTrue("Log must record the collision check (P3.2): " + r.getMessage(),
                r.getMessage().contains("enters asteroid hex"));
    }

    // -------------------------------------------------------------------------
    // Map edges
    // -------------------------------------------------------------------------

    @Test
    public void moveForward_offSafeMapEdge_disengages() {
        fed.setLocation(new Location(10, 1)); // next hex is off the top edge
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("Log must report disengagement: " + r.getMessage(),
                r.getMessage().contains("disengaged"));
        assertNull("Disengaged ship has no location", fed.getLocation());
    }

    // -------------------------------------------------------------------------
    // Tractor drag coupling (G7.36 / G7.5)
    // -------------------------------------------------------------------------

    @Test
    public void moveForward_dragsTractorLinkedShip() {
        linkKlingon();
        allocate(30.0, 0.0); // anchored: fed pseudo-speed 15, klingon 0

        advanceUntilCanMove(fed);
        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(10, 9), fed.getLocation());
        assertEquals("Held ship must be dragged in the mover's direction",
                new Location(11, 9), klingon.getLocation());
    }

    @Test
    public void moveForward_refusedWhenLinkedShipWouldHitPlanet() {
        game.addTerrain(new Terrain(TerrainType.PLANET, 11, 9)); // klingon's drag hex
        linkKlingon();
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.moveForward(fed);

        assertFalse("G7.36 pre-validation must refuse the move", r.isSuccess());
        assertEquals(new Location(10, 10), fed.getLocation());
        assertEquals(new Location(11, 10), klingon.getLocation());
    }

    @Test
    public void moveForward_towsHeldDrone() {
        Drone drone = makeHeldDrone(12, 10);
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(10, 9), fed.getLocation());
        assertEquals("Held drone must be towed in the mover's direction",
                new Location(12, 9), drone.getLocation());
        assertTrue("Log must mention the tow: " + r.getMessage(),
                r.getMessage().contains("towed"));
    }

    @Test
    public void moveForward_heldDroneDraggedIntoPlanet_destroyedAndReleased() {
        Drone drone = makeHeldDrone(12, 10);
        game.addTerrain(new Terrain(TerrainType.PLANET, 12, 9)); // drone's drag hex
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess()); // the SHIP still moves
        assertEquals(new Location(10, 9), fed.getLocation());
        assertNull("Drone must be destroyed", drone.getLocation());
        assertFalse("Drone must leave the seekers list", game.getSeekers().contains(drone));
        assertFalse("Tractor link must be released", drone.isTractored());
        assertTrue("Log must report the destruction: " + r.getMessage(),
                r.getMessage().contains("destroyed"));
    }

    @Test
    public void sideslip_dragsTractorLinkedShip() {
        linkKlingon();
        allocate(30.0, 0.0);

        // A sideslip cannot be the first movement of the turn — move straight once
        advanceUntilCanMove(fed);
        assertTrue(game.moveForward(fed).isSuccess()); // fed (10,9), klingon (11,9)

        advanceUntilCanMove(fed);
        Game.ActionResult r = game.sideslipRight(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        // Facing 1, slip right = direction 5. From (10,9) even column → (11,9);
        // klingon dragged direction 5 from (11,9) odd column → (12,8).
        assertEquals(new Location(11, 9), fed.getLocation());
        assertEquals("Held ship must be dragged in the slip direction",
                new Location(12, 8), klingon.getLocation());
    }

    // -------------------------------------------------------------------------
    // Emergency deceleration (C8.0)
    // -------------------------------------------------------------------------

    @Test
    public void emergencyDeceleration_marksShipDecelerating() {
        allocate(30.0, 0.0);
        advanceUntilCanMove(fed);
        game.advancePhase(); // emergency decel is announced in the ACTIVITY phase (C8.10)
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());

        Game.ActionResult r = game.emergencyDeceleration(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("Ship must be flagged as decelerating", fed.isDecelerating());
    }
}
