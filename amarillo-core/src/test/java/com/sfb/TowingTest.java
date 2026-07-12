package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Towing hazards and beam economy, implemented as prerequisites for the
 * J1.621 recovery procedure (rulings confirmed 2026-07-10):
 *
 * G7.54 death dragging — a shuttle towed by a ship moving faster than twice
 * its rated max speed is destroyed when the ship moves (G7.541), in the hex
 * it occupied before the movement. Crippled: twice crippled max (G7.542).
 * Drones exempt (G7.53). Fighter breakaway (G7.543) deferred.
 *
 * G7.13 — each tractor beam may initiate only one link per turn; releasing a
 * link does not free the beam until the next turn.
 */
public class TowingTest {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setActiveFireControl(true);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(30, 20));
        klingon.setFacing(1);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
    }

    private Energy makeAllocation(Ship ship, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warp);
        return e;
    }

    private void allocate(double fedWarp) {
        game.submitAllocation(fed,     makeAllocation(fed, fedWarp));
        game.submitAllocation(klingon, makeAllocation(klingon, 0.0));
    }

    private void advanceUntilCanMove(Ship ship) {
        for (int guard = 0; guard < 60; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT
                    && game.canMoveThisImpulse(ship))
                return;
            game.advancePhase();
        }
        fail("never movable");
    }

    /** Launch a stock admin shuttle (max speed 6) and tractor it mid-turn. */
    private Shuttle launchAndGrabShuttle() {
        // Reach ACTIVITY to launch
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        ShuttleBay bay = fed.getShuttles().getBays().get(0);
        Shuttle shuttle = bay.getInventory().get(0);
        assertTrue(game.launchShuttle(fed, bay, shuttle, 0, 1).isSuccess());
        fed.getTractors().initForTurn(4);
        assertTrue(fed.getTractors().linkUnit(shuttle));
        return shuttle;
    }

    // -------------------------------------------------------------------------
    // G7.54 — death dragging
    // -------------------------------------------------------------------------

    @Test
    public void deathDrag_shipMovingOverTwiceShuttleMax_destroysShuttleOnMove() {
        allocate(30.0); // speed 30 > 2 × admin shuttle max (6) → doomed on first move
        Shuttle shuttle = launchAndGrabShuttle();
        Location before = shuttle.getLocation();
        assertNotNull(before);

        advanceUntilCanMove(fed);
        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("Log must report the death drag: " + r.getMessage(),
                r.getMessage().contains("death-dragged"));
        assertNull("Shuttle destroyed in place (G7.541)", shuttle.getLocation());
        assertFalse(game.getActiveShuttles().contains(shuttle));
        assertFalse("Link released on destruction", shuttle.isTractored());
    }

    @Test
    public void safeTow_atExactlyTwiceShuttleMax_survives() {
        allocate(12.0); // speed 12 == 2 × max 6 — the limit is "faster than"
        Shuttle shuttle = launchAndGrabShuttle();

        advanceUntilCanMove(fed);
        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("At exactly twice max the shuttle survives the tow: " + r.getMessage(),
                r.getMessage().contains("towed"));
        assertNotNull(shuttle.getLocation());
        assertTrue(shuttle.isTractored());
    }

    @Test
    public void deathDrag_neverAppliesToDrones() {
        allocate(30.0);
        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("Held-1");
        drone.setLocation(new Location(11, 10));
        game.getSeekers().add(drone);
        fed.getTractors().initForTurn(4);
        assertTrue(fed.getTractors().linkUnit(drone));

        advanceUntilCanMove(fed);
        Game.ActionResult r = game.moveForward(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("Drones are towed at any speed (G7.53): " + r.getMessage(),
                r.getMessage().contains("towed"));
        assertNotNull(drone.getLocation());
        assertTrue(game.getSeekers().contains(drone));
    }

    @Test
    public void sideslip_dragsHeldDroneToo() {
        allocate(12.0);
        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("Held-1");
        drone.setLocation(new Location(11, 10));
        game.getSeekers().add(drone);
        fed.getTractors().initForTurn(4);
        assertTrue(fed.getTractors().linkUnit(drone));

        advanceUntilCanMove(fed);
        assertTrue(game.moveForward(fed).isSuccess()); // fed (10,9), drone (11,9)
        advanceUntilCanMove(fed);
        Game.ActionResult r = game.sideslipRight(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        // Facing 1 slip right = direction 5: fed (10,9)→(11,9); drone (11,9)→(12,8)
        assertEquals(new Location(11, 9), fed.getLocation());
        assertEquals("Held units must follow sideslips (G7.5)",
                new Location(12, 8), drone.getLocation());
    }

    // -------------------------------------------------------------------------
    // G7.36 — the link is rigid through TURNS too (bug found in play 2026-07-12:
    // turns displaced the mover one hex but dragged nothing, stretching the link)
    // -------------------------------------------------------------------------

    @Test
    public void turn_dragsLinkedShipPreservingRange() {
        allocate(12.0); // fed moves; klingon (held) plotted speed 0
        klingon.setLocation(new com.sfb.properties.Location(10, 8)); // range 2 dead ahead
        fed.getTractors().initForTurn(4);
        assertTrue(fed.getTractors().linkUnit(klingon));
        assertEquals(2, game.getRange(fed, klingon));

        // Move forward until turn mode is satisfied, then turn right
        boolean turned = false;
        for (int guard = 0; guard < 30 && !turned; guard++) {
            advanceUntilCanMove(fed);
            Location shipPrev = fed.getLocation();
            Location heldPrev = klingon.getLocation();
            Game.ActionResult r = game.turnRight(fed);
            if (r.isSuccess()) {
                turned = true;
                // The held ship must be displaced in the same absolute direction
                int dir = -1;
                for (int d : new int[] { 1, 5, 9, 13, 17, 21 })
                    if (fed.getLocation().equals(com.sfb.utilities.MapUtils.getAdjacentHex(
                            shipPrev, d, game.getMapCols(), game.getMapRows()))) {
                        dir = d;
                        break;
                    }
                assertTrue("Turn must displace the ship one hex", dir > 0);
                assertEquals("Rigid link: held ship follows the turn's displacement (G7.36)",
                        com.sfb.utilities.MapUtils.getAdjacentHex(heldPrev, dir,
                                game.getMapCols(), game.getMapRows()),
                        klingon.getLocation());
                assertTrue("Log reports the tow: " + r.getMessage(),
                        r.getMessage().contains("towed"));
            } else {
                assertTrue(game.moveForward(fed).isSuccess());
            }
        }
        assertTrue("Ship never satisfied turn mode", turned);
        assertEquals("Range preserved through the turn", 2, game.getRange(fed, klingon));
    }

    @Test
    public void turn_dragsHeldDroneToo() {
        allocate(12.0);
        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("Held-1");
        drone.setLocation(new Location(11, 10));
        game.getSeekers().add(drone);
        fed.getTractors().initForTurn(4);
        assertTrue(fed.getTractors().linkUnit(drone));

        boolean turned = false;
        for (int guard = 0; guard < 30 && !turned; guard++) {
            advanceUntilCanMove(fed);
            Location shipPrev = fed.getLocation();
            Location dronePrev = drone.getLocation();
            Game.ActionResult r = game.turnRight(fed);
            if (r.isSuccess()) {
                turned = true;
                assertNotEquals("Held drone must follow the turn (G7.5)",
                        dronePrev, drone.getLocation());
            } else {
                assertTrue(game.moveForward(fed).isSuccess());
            }
        }
        assertTrue(turned);
    }

    // -------------------------------------------------------------------------
    // G7.5 — a held seeker is held FAST (bug found in play 2026-07-12: a drone
    // tractored mid-turn kept its speed, closed the last hex, and impacted)
    // -------------------------------------------------------------------------

    @Test
    public void tractoredDrone_neitherMovesNorImpacts() {
        allocate(0.0);
        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("Incoming-1");
        drone.setLocation(new Location(10, 9)); // range 1, closing on fed at (10,10)
        drone.setTarget(fed);
        drone.setSpeed(12);
        drone.setEndurance(10);
        game.getSeekers().add(drone);

        fed.getTractors().initForTurn(4);
        assertTrue(fed.getTractors().linkUnit(drone)); // mid-turn grab, like the UI does

        Location held = drone.getLocation();
        for (int i = 0; i < 32; i++) // 8 full impulses
            game.advancePhase();

        assertEquals("Held drone must not move itself (G7.5)", held, drone.getLocation());
        assertTrue("Held drone must not impact", game.getSeekers().contains(drone));

        // Release — the hunt resumes
        assertTrue(game.releaseTractor(fed, "Incoming-1").isSuccess());
        boolean acted = false;
        for (int i = 0; i < 32 && !acted; i++) {
            game.advancePhase();
            acted = !game.getSeekers().contains(drone) // impacted fed at range 1
                    || !held.equals(drone.getLocation()); // or moved
        }
        assertTrue("Released drone must resume moving/attacking", acted);
    }

    @Test
    public void tractoredShuttle_cannotFlyOutOfTheBeam() {
        allocate(0.0);
        Shuttle shuttle = launchAndGrabShuttle();
        shuttle.setSpeed(6);
        shuttle.setCurrentSpeed(6);

        assertFalse("Held shuttle is not movable (G7.5)",
                game.canMoveShuttleThisImpulse(shuttle));
        assertFalse(game.getMovableShuttles().contains(shuttle));
    }

    // -------------------------------------------------------------------------
    // G7.13 — one link per beam per turn
    // -------------------------------------------------------------------------

    private Drone placeDrone(String name, int col, int row) {
        Drone d = new Drone(DroneType.TypeI);
        d.setName(name);
        d.setLocation(new Location(col, row));
        game.getSeekers().add(d);
        fed.addLockOn(d);
        return d;
    }

    @Test
    public void beams_cannotBeReusedAfterReleaseSameTurn() {
        // FedCA has 3 tractor beams; use each once via grab-and-release
        fed.getTractors().initForTurn(6);
        fed.getPowerSystems().setBatteryPower(0);
        for (int i = 1; i <= 3; i++) {
            Drone d = placeDrone("Drone-" + i, 11, 10);
            assertTrue("Grab " + i + " uses a fresh beam",
                    game.establishTractor(fed, d.getName(), 1).isSuccess());
            assertTrue(game.releaseTractor(fed, d.getName()).isSuccess());
        }

        Drone d4 = placeDrone("Drone-4", 11, 10);
        Game.ActionResult r = game.establishTractor(fed, "Drone-4", 1);

        assertFalse("All three beams already used this turn", r.isSuccess());
        assertTrue("Message cites G7.13: " + r.getMessage(),
                r.getMessage().contains("G7.13"));
        assertFalse(d4.isTractored());
    }

    @Test
    public void beamUsage_resetsAtNextTurn() {
        fed.getTractors().initForTurn(6);
        fed.getPowerSystems().setBatteryPower(0);
        for (int i = 1; i <= 3; i++) {
            Drone d = placeDrone("Drone-" + i, 11, 10);
            assertTrue(game.establishTractor(fed, d.getName(), 1).isSuccess());
            assertTrue(game.releaseTractor(fed, d.getName()).isSuccess());
        }
        assertFalse(game.establishTractor(fed, placeDrone("Drone-4", 11, 10).getName(), 1).isSuccess());

        // Next turn: fresh beam usage
        game.startTurn();
        game.submitAllocation(fed,     makeAllocation(fed, 0.0));
        game.submitAllocation(klingon, makeAllocation(klingon, 0.0));
        fed.getTractors().initForTurn(2);
        fed.setActiveFireControl(true);
        Drone d5 = placeDrone("Drone-5", 11, 10);

        Game.ActionResult r = game.establishTractor(fed, "Drone-5", 1);

        assertTrue("Beams are fresh on the new turn: " + r.getMessage(), r.isSuccess());
        assertTrue(d5.isTractored());
    }
}
