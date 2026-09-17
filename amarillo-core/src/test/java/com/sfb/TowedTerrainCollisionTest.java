package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * P3.2 collision for units dragged through terrain in a tractor beam.
 * <p>
 * A drone flying through an asteroid hex under its own power lost hull from the day the
 * terrain work landed, but the same drone under tow took nothing: {@code dragHeldSmallUnits}
 * carefully handled death-dragging (G7.54), dragging off-map and dragging into a planet,
 * and never rolled for the rocks it was hauled through.
 * <p>
 * It is also dragged at the TOW's speed rather than its own - a drone sitting at speed 0
 * in a beam is not gently parked - which is what P3.2's damage brackets key off and what
 * J1.6223 states outright for a recovery tow.
 */
public class TowedTerrainCollisionTest {

    private Game game;
    private Ship fed;

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed);

        game.startTurn();
    }

    private Energy allocationFor(Ship s, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(s.getLifeSupportCost());
        e.setFireControl(s.getFireControlCost());
        e.setActivateShields(s.getActiveShieldCost());
        e.setWarpMovement(warp);
        return e;
    }

    private void advanceUntilCanMove(Ship ship) {
        for (int guard = 0; guard < 200; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT
                    && game.canMoveThisImpulse(ship))
                return;
            game.advancePhase();
        }
        fail(ship.getName() + " never became movable (speed " + ship.getSpeed() + ")");
    }

    /** A drone in the ship's own hex, held in a tractor beam, at rest. */
    private Drone towedDroneInShipHex() {
        Drone d = new Drone(DroneType.TypeI);
        d.setName("Drone-1");
        d.setLocation(new Location(10, 10)); // same hex as the ship, so it is dragged along
        d.setSpeed(0);                       // at rest: only the tow is moving it
        game.getSeekers().add(d);

        fed.getTractors().initForTurn(5);
        fed.addLockOn(d);
        fed.setActiveFireControl(true);
        Game.ActionResult r = game.establishTractor(fed, "Drone-1", 1);
        assertTrue("could not establish the tractor: " + r.getMessage(), r.isSuccess());
        assertTrue(d.isTractored());
        return d;
    }

    @Test
    public void towedDrone_draggedIntoAsteroidHex_rollsCollision() {
        // The ship moves from (10,10) to (10,9); the drone is dragged the same way.
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));
        towedDroneInShipHex();
        game.submitAllocation(fed, allocationFor(fed, 16.0)); // enough warp to be moving fast
        advanceUntilCanMove(fed);

        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("The towed drone must roll for collision too (P3.2): " + r.getMessage(),
                r.getMessage().contains("Drone-1 enters asteroid hex"));
        assertFalse("the drone is not rolling at its own speed 0 — it is towed at the "
                        + "ship's speed (J1.6223): " + r.getMessage(),
                r.getMessage().contains("Drone-1 enters asteroid hex (speed 0"));
    }

    /**
     * The tow is what decides the damage bracket. At the ship's speed the asteroid table
     * bites; at the drone's own speed of 0 it never would.
     */
    @Test
    public void towedDrone_takesRealDamageAcrossManyTows() {
        boolean sawRealDamage = false;

        for (int trial = 0; trial < 60 && !sawRealDamage; trial++) {
            setUp(); // a fresh game each trial — the die is not seeded
            game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));
            Drone drone = towedDroneInShipHex();
            game.submitAllocation(fed, allocationFor(fed, 16.0));
            advanceUntilCanMove(fed);

            int hullBefore = drone.getHull();
            game.moveForward(fed);
            if (drone.getHull() < hullBefore)
                sawRealDamage = true;
        }

        assertTrue("Across 60 tows through an asteroid hex a drone must sometimes take real "
                + "hull damage — being towed through the rocks is not free", sawRealDamage);
    }
}
