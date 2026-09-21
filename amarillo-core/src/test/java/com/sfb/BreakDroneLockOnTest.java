package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Breaking enemy drone lock-ons with a scout channel (G24.22): the scout needs active fire
 * control and a lock-on to the drone (G24.161), the drone within 15 hexes (G24.222), and an
 * operational channel not doing another function (G24.12). Three attempts per channel per
 * turn, at most one per drone per impulse (G24.221); a 1–3 removes the drone (G24.223).
 */
public class BreakDroneLockOnTest {

    private Ship scout(Game game) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName("Scout");
        s.setLocation(new Location(10, 10));
        s.setActiveFireControl(true);
        ScoutChannel c = new ScoutChannel();
        c.setDesignator("1");
        c.setDacHitLocaiton("torp");
        c.setPowered(true);
        s.getWeapons().addWeapon(c);
        game.getShips().add(s);
        return s;
    }

    private Drone enemyDrone(Game game, String name, int x, int y) {
        Drone d = new Drone(DroneType.TypeI);
        d.setName(name);
        d.setLocation(new Location(x, y));
        game.getSeekers().add(d);
        return d;
    }

    @Test
    public void breaksLockOn_onRollThreeOrLess_removesTheDrone() {
        Game game = new Game();
        Ship scout = scout(game);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12); // range 2
        scout.addLockOn(drone);

        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-1", 3);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage().contains("broke"));
        assertFalse("drone removed from play (G24.223)", game.getSeekers().contains(drone));
    }

    @Test
    public void failsToBreak_onRollFourOrMore_keepsTheDrone() {
        Game game = new Game();
        Ship scout = scout(game);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12);
        scout.addLockOn(drone);

        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-1", 4);

        assertTrue(r.isSuccess());
        assertTrue(r.getMessage().contains("failed"));
        assertTrue("drone still in play", game.getSeekers().contains(drone));
    }

    @Test
    public void breakingASeekingShuttle_makesItInert_notRemoved() {
        Game game = new Game();
        Ship scout = scout(game);
        com.sfb.objects.shuttles.AdminShuttle base = new com.sfb.objects.shuttles.AdminShuttle();
        base.setName("Kamikaze");
        com.sfb.objects.shuttles.SuicideShuttle shuttle =
                new com.sfb.objects.shuttles.SuicideShuttle(base);
        shuttle.setLocation(new Location(10, 12)); // range 2
        shuttle.setSpeed(6);
        game.getSeekers().add(shuttle);
        game.getActiveShuttles().add(shuttle);
        scout.addLockOn(shuttle);

        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Kamikaze", 3);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("shuttle goes inert, not removed (G24.223)", r.getMessage().contains("inert"));
        assertFalse("no longer seeking", game.getSeekers().contains(shuttle));
        assertTrue("stays on the map as a shuttle", game.getActiveShuttles().contains(shuttle));
        assertEquals("dropped to speed 0, holds its hex", 0, shuttle.getSpeed());
        assertNull("guidance cleared", shuttle.getTarget());
    }

    @Test
    public void requiresActiveFireControl() {
        Game game = new Game();
        Ship scout = scout(game);
        scout.setActiveFireControl(false);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12);
        scout.addLockOn(drone);

        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-1", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("active fire control"));
    }

    @Test
    public void requiresLockOnToTheDrone() {
        Game game = new Game();
        Ship scout = scout(game);
        enemyDrone(game, "Drone-1", 10, 12); // no lock-on

        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-1", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("lock-on"));
    }

    @Test
    public void refusesBeyondFifteenHexes() {
        Game game = new Game();
        Ship scout = scout(game);
        Drone drone = enemyDrone(game, "Drone-1", 10, 26); // range 16
        scout.addLockOn(drone);

        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-1", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("out of range"));
    }

    @Test
    public void refusesAFriendlyDrone() {
        Game game = new Game();
        Ship scout = scout(game);
        Player teamA = new Player();
        teamA.setTeamName("A");
        scout.setOwner(teamA);

        Ship ally = new Ship();
        ally.init(FederationShips.getFedCa());
        ally.setName("Ally");
        ally.setOwner(teamA);
        game.getShips().add(ally);

        Drone drone = enemyDrone(game, "Drone-1", 10, 12);
        drone.setController(ally); // friendly-controlled
        scout.addLockOn(drone);

        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-1", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("friendly"));
    }

    @Test
    public void warpSeekerDrone_isImmune() {
        Game game = new Game();
        Ship scout = scout(game);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12);
        drone.setWarpSeeker(true);
        scout.addLockOn(drone);

        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-1", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("immune"));
    }

    @Test
    public void atMostOneAttemptPerDronePerImpulse() {
        Game game = new Game();
        Ship scout = scout(game);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12);
        scout.addLockOn(drone);

        assertTrue(game.breakDroneLockOn(scout, "1", "Drone-1", 6).isSuccess()); // fails to break, attempt used
        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-1", 6);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("this impulse"));
    }

    @Test
    public void atMostThreeAttemptsPerTurn() {
        Game game = new Game();
        Ship scout = scout(game);
        // three different drones can be tried in one impulse (G24.221)
        for (int i = 1; i <= 3; i++) {
            Drone d = enemyDrone(game, "Drone-" + i, 10, 12);
            scout.addLockOn(d);
            assertTrue(game.breakDroneLockOn(scout, "1", "Drone-" + i, 6).isSuccess());
        }
        Drone fourth = enemyDrone(game, "Drone-4", 10, 12);
        scout.addLockOn(fourth);
        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-4", 6);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("all 3"));
    }

    @Test
    public void aChannelBreakingLockOns_cannotAlsoLendEw() {
        Game game = new Game();
        Ship scout = scout(game);
        scout.setScoutEwPool(6);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12);
        scout.addLockOn(drone);

        assertTrue(game.breakDroneLockOn(scout, "1", "Drone-1", 6).isSuccess()); // commits channel to breaking
        Game.ActionResult r = game.assignChannelLend(scout, "1", "Scout", 3, 0);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("break"));
    }

    @Test
    public void aLendingChannel_cannotAlsoBreakLockOns() {
        Game game = new Game();
        Ship scout = scout(game);
        scout.setScoutEwPool(6);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12);
        scout.addLockOn(drone);

        assertTrue(game.assignChannelLend(scout, "1", "Scout", 3, 0).isSuccess()); // commits channel to lending
        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Drone-1", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("lending"));
    }
}
