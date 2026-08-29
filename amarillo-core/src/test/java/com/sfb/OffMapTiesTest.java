package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.utilities.MapUtils;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A ship that leaves the map keeps its entry in the game for scoring but loses its hex
 * (C7.1), and everything still pointing at it has to let go: lock-ons both ways, and any
 * seeker flying at it. Found while auditing null-location handling — a drone chasing a ship
 * that flew off the edge kept a target with no hex, and the bearing calculation on the next
 * impulse it moved dereferenced it.
 */
public class OffMapTiesTest {

    private Energy alloc(Ship ship, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warp);
        return e;
    }

    @Test
    public void shipFlyingOffTheMap_dropsItsChasersAndLockOns() {
        Game game = new Game();

        Ship runner = new Ship();
        runner.init(FederationShips.getFedCa());
        runner.setName("Runner");
        runner.setLocation(new Location(1, 10));
        runner.setFacing(21);                       // toward the left edge

        Ship hunter = new Ship();
        hunter.init(FederationShips.getFedCa());
        hunter.setName("Hunter");
        hunter.setLocation(new Location(6, 10));
        hunter.setFacing(9);
        hunter.addLockOn(runner);

        game.getShips().add(runner);
        game.getShips().add(hunter);

        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("Drone-1");
        drone.setLocation(new Location(4, 10));
        drone.setFacing(21);
        drone.setController(hunter);
        drone.setTarget(runner);
        game.getSeekers().add(drone);

        game.startTurn();
        game.submitAllocation(runner, alloc(runner, 16.0));
        game.submitAllocation(hunter, alloc(hunter, 16.0));

        // Advance to a movement impulse the runner can act on, then drive it off the edge.
        for (int guard = 0; guard < 60; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT && game.canMoveThisImpulse(runner))
                break;
            game.advancePhase();
        }
        Game.ActionResult r = game.moveForward(runner);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("disengaged"));
        assertTrue(runner.isDisengaged());
        assertNull("off the map (C7.1)", runner.getLocation());

        assertFalse("nothing holds a lock-on to a ship that has left (C7.1)", hunter.hasLockOn(runner));
        assertFalse("the drone loses its target rather than chasing a ship with no hex",
                game.getSeekers().contains(drone));

        // The impulse engine has to keep running over the departed ship without tripping.
        for (int i = 0; i < 40; i++)
            game.advancePhase();
    }

    /** The same tie-cutting on the player-initiated separation path (C7.2). */
    @Test
    public void disengagingBySeparation_clearsLockOnsBothWays() {
        Game game = new Game();

        Player teamA = new Player();
        teamA.setTeamName("A");
        teamA.setName("Alice");

        Ship leaving = new Ship();
        leaving.init(FederationShips.getFedCa());
        leaving.setName("Leaving");
        leaving.setLocation(new Location(10, 10));
        leaving.setOwner(teamA);

        Ship ally = new Ship();
        ally.init(FederationShips.getFedCa());
        ally.setName("Ally");
        ally.setLocation(new Location(12, 10));
        ally.setOwner(teamA);                       // no enemies in play, so C7.2 is satisfied

        game.getShips().add(leaving);
        game.getShips().add(ally);
        ally.addLockOn(leaving);
        leaving.addLockOn(ally);

        assertTrue(game.canDisengageBySeparation(leaving));
        assertTrue(game.disengageBySeparation(leaving).isSuccess());

        assertFalse("held lock-ons drop", ally.hasLockOn(leaving));
        assertFalse("and the departed ship sees nothing either", leaving.hasLockOn(ally));
    }

    /**
     * Second line of defence: the geometry helpers answer "no relationship" for a unit with
     * no hex instead of throwing, the same answer they give for two units in one hex.
     */
    @Test
    public void geometryHelpers_treatAnOffMapUnitAsUnreachable() {
        Ship here = new Ship();
        here.init(FederationShips.getFedCa());
        here.setName("Here");
        here.setLocation(new Location(10, 10));
        here.setFacing(1);

        Ship gone = new Ship();
        gone.init(FederationShips.getFedCa());
        gone.setName("Gone");
        gone.setLocation(null);
        gone.setFacing(1);

        assertEquals(Integer.MAX_VALUE, MapUtils.getRange(here, gone));
        assertEquals(0, MapUtils.getBearing(here, gone));
        assertEquals(0, MapUtils.getGeometricBearing(here, gone));
        assertEquals(0, MapUtils.getAbsoluteArc(here, gone));
        assertEquals(0, MapUtils.getAbsoluteShieldFacing(here, gone));
        // and in the other direction, with the source off the map
        assertEquals(Integer.MAX_VALUE, MapUtils.getRange(gone, here));
        assertEquals(0, MapUtils.getGeometricBearing(gone, here));
    }
}
