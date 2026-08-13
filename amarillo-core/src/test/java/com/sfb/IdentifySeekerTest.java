package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Identifying enemy seekers with a scout channel + lab (G24.25): the scout needs active fire
 * control and a lock-on to the seeker (G24.161), the seeker within 15 hexes (G24.252), an
 * operational channel not doing another function (G24.12), and a free lab (one per identifying
 * channel, G24.251). Four attempts per channel/turn on any target(s), any impulse(s); a 1–3
 * identifies it (G24.252). Unlike breaking, the seeker stays in play — it's just revealed.
 */
public class IdentifySeekerTest {

    private Ship scout(Game game, int labs) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName("Scout");
        s.setLocation(new Location(10, 10));
        s.setActiveFireControl(true);
        s.getLabs().init(Map.of("lab", labs));
        ScoutChannel c1 = new ScoutChannel();
        c1.setDesignator("1");
        c1.setDacHitLocaiton("torp");
        c1.setPowered(true);
        s.getWeapons().addWeapon(c1);
        ScoutChannel c2 = new ScoutChannel();
        c2.setDesignator("2");
        c2.setDacHitLocaiton("torp");
        c2.setPowered(true);
        s.getWeapons().addWeapon(c2);
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
    public void identifies_onRollThreeOrLess_marksTheSeeker() {
        Game game = new Game();
        Ship scout = scout(game, 2);
        Drone drone = enemyDrone(game, "Bogey", 10, 12);
        scout.addLockOn(drone);

        Game.ActionResult r = game.identifySeeker(scout, "1", "Bogey", 3);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage().contains("identified"));
        assertTrue("seeker is now identified (G24.252)", drone.isIdentified());
        assertTrue("and stays in play (not removed)", game.getSeekers().contains(drone));
    }

    @Test
    public void failsToIdentify_onRollFourOrMore() {
        Game game = new Game();
        Ship scout = scout(game, 2);
        Drone drone = enemyDrone(game, "Bogey", 10, 12);
        scout.addLockOn(drone);

        Game.ActionResult r = game.identifySeeker(scout, "1", "Bogey", 4);

        assertTrue(r.isSuccess());
        assertTrue(r.getMessage().contains("failed"));
        assertFalse(drone.isIdentified());
    }

    @Test
    public void requiresActiveFireControl() {
        Game game = new Game();
        Ship scout = scout(game, 2);
        scout.setActiveFireControl(false);
        Drone drone = enemyDrone(game, "Bogey", 10, 12);
        scout.addLockOn(drone);

        Game.ActionResult r = game.identifySeeker(scout, "1", "Bogey", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("active fire control"));
    }

    @Test
    public void requiresLockOn() {
        Game game = new Game();
        Ship scout = scout(game, 2);
        enemyDrone(game, "Bogey", 10, 12); // no lock-on

        Game.ActionResult r = game.identifySeeker(scout, "1", "Bogey", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("lock-on"));
    }

    @Test
    public void refusesBeyondFifteenHexes() {
        Game game = new Game();
        Ship scout = scout(game, 2);
        Drone drone = enemyDrone(game, "Bogey", 10, 26); // range 16
        scout.addLockOn(drone);

        Game.ActionResult r = game.identifySeeker(scout, "1", "Bogey", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("out of range"));
    }

    @Test
    public void refusesWithoutAnAvailableLab() {
        Game game = new Game();
        Ship scout = scout(game, 0); // no labs
        Drone drone = enemyDrone(game, "Bogey", 10, 12);
        scout.addLockOn(drone);

        Game.ActionResult r = game.identifySeeker(scout, "1", "Bogey", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("lab"));
    }

    @Test
    public void eachIdentifyingChannelNeedsItsOwnLab() {
        Game game = new Game();
        Ship scout = scout(game, 1); // only one lab
        Drone a = enemyDrone(game, "A", 10, 12);
        Drone b = enemyDrone(game, "B", 10, 13);
        scout.addLockOn(a);
        scout.addLockOn(b);

        assertTrue(game.identifySeeker(scout, "1", "A", 4).isSuccess()); // channel 1 claims the lab
        Game.ActionResult r = game.identifySeeker(scout, "2", "B", 4);   // channel 2 has no lab
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("lab"));
    }

    @Test
    public void alreadyIdentified_isRejected() {
        Game game = new Game();
        Ship scout = scout(game, 2);
        Drone drone = enemyDrone(game, "Bogey", 10, 12);
        drone.identify();
        scout.addLockOn(drone);

        Game.ActionResult r = game.identifySeeker(scout, "1", "Bogey", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("already identified"));
    }

    @Test
    public void fourAttemptsPerTurn_sameTargetSameImpulseAllowed() {
        Game game = new Game();
        Ship scout = scout(game, 2);
        Drone drone = enemyDrone(game, "Bogey", 10, 12);
        scout.addLockOn(drone);

        // repeat attempts on the same seeker in the same impulse are allowed (G24.252)
        for (int i = 0; i < 4; i++)
            assertTrue("attempt " + i, game.identifySeeker(scout, "1", "Bogey", 4).isSuccess());
        Game.ActionResult r = game.identifySeeker(scout, "1", "Bogey", 4);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("all 4"));
    }

    @Test
    public void identifyingChannel_cannotAlsoLendOrBreak() {
        Game game = new Game();
        Ship scout = scout(game, 2);
        scout.setScoutEwPool(6);
        Drone drone = enemyDrone(game, "Bogey", 10, 12);
        scout.addLockOn(drone);

        assertTrue(game.identifySeeker(scout, "1", "Bogey", 4).isSuccess()); // commits channel 1 to IDENTIFY
        assertFalse(game.assignChannelLend(scout, "1", "Scout", 3, 0).isSuccess());
        Game.ActionResult r = game.breakDroneLockOn(scout, "1", "Bogey", 3);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("identifying"));
    }

    @Test
    public void identifyingASeekingShuttle_marksIt_withoutRemovingOrInerting() {
        Game game = new Game();
        Ship scout = scout(game, 2);
        com.sfb.objects.shuttles.AdminShuttle base = new com.sfb.objects.shuttles.AdminShuttle();
        base.setName("Ghost");
        com.sfb.objects.shuttles.SuicideShuttle shuttle =
                new com.sfb.objects.shuttles.SuicideShuttle(base);
        shuttle.setLocation(new Location(10, 12));
        shuttle.setSpeed(6);
        game.getSeekers().add(shuttle);
        game.getActiveShuttles().add(shuttle);
        scout.addLockOn(shuttle);

        Game.ActionResult r = game.identifySeeker(scout, "1", "Ghost", 2);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(shuttle.isIdentified());
        assertTrue("still seeking, not inerted", game.getSeekers().contains(shuttle));
        assertEquals("speed untouched by identification", 6, shuttle.getSpeed());
    }
}
