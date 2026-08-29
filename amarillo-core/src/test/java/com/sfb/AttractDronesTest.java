package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Attracting drones with a scout channel (G24.23): the channel draws an enemy drone onto the
 * scout itself. There is no die roll — the attraction always works — but it needs active fire
 * control and a lock-on to the drone (G24.161), the drone within fifteen hexes, and the scout
 * within thirty-five hexes of the unit controlling it (G24.234). One channel attracts one drone
 * per turn (G24.231), and the retarget survives the channel afterwards (G24.232).
 */
public class AttractDronesTest {

    private Ship scout(Game game) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName("Scout");
        s.setLocation(new Location(10, 10));
        s.setActiveFireControl(true);
        s.getWeapons().addWeapon(channel("1"));
        s.getWeapons().addWeapon(channel("2"));
        game.getShips().add(s);
        return s;
    }

    private ScoutChannel channel(String designator) {
        ScoutChannel c = new ScoutChannel();
        c.setDesignator(designator);
        c.setDacHitLocaiton("torp");
        c.setPowered(true);
        return c;
    }

    private ScoutChannel channelOf(Ship scout, String designator) {
        for (ScoutChannel c : scout.getScoutChannels())
            if (designator.equals(c.getDesignator()))
                return c;
        throw new IllegalStateException("no channel " + designator);
    }

    /** An enemy ship guiding drones, parked next to the scout unless moved. */
    private Ship enemyShip(Game game, String name, int x, int y) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName(name);
        s.setLocation(new Location(x, y));
        game.getShips().add(s);
        return s;
    }

    private Drone enemyDrone(Game game, String name, int x, int y, Ship controller, Ship target) {
        Drone d = new Drone(DroneType.TypeI);
        d.setName(name);
        d.setLocation(new Location(x, y));
        d.setController(controller);
        d.setTarget(target);
        game.getSeekers().add(d);
        return d;
    }

    @Test
    public void attractsAnEnemyDrone_ontoTheScout() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship victim = enemyShip(game, "USS Prey", 11, 10);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12, enemy, victim); // range 2
        scout.addLockOn(drone);

        Game.ActionResult r = game.attractDrone(scout, "1", "Drone-1");

        assertTrue(r.getMessage(), r.isSuccess());
        assertSame("the drone now tracks the scout (G24.23)", scout, drone.getTarget());
        assertTrue(r.getMessage(), r.getMessage().contains("away from USS Prey"));
        assertEquals(ScoutChannel.Function.ATTRACT_DRONES, channelOf(scout, "1").getTurnFunction());
    }

    /** G24.234: control of the drone is not taken, only its target changes. */
    @Test
    public void leavesControlWithTheEnemy() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12, enemy, null);
        scout.addLockOn(drone);

        assertTrue(game.attractDrone(scout, "1", "Drone-1").isSuccess());

        assertSame("controller unchanged (G24.234)", enemy, drone.getController());
    }

    /** G24.231: one drone per channel per turn — a second needs another channel. */
    @Test
    public void oneDronePerChannelPerTurn() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone first = enemyDrone(game, "Drone-1", 10, 12, enemy, null);
        Drone second = enemyDrone(game, "Drone-2", 10, 13, enemy, null);
        scout.addLockOn(first);
        scout.addLockOn(second);

        assertTrue(game.attractDrone(scout, "1", "Drone-1").isSuccess());
        Game.ActionResult r = game.attractDrone(scout, "1", "Drone-2");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("already attracted"));
        assertNull("second drone untouched", second.getTarget());

        assertTrue("a second channel can take it (G24.231)",
                game.attractDrone(scout, "2", "Drone-2").isSuccess());
        assertSame(scout, second.getTarget());
    }

    /** G24.12: a channel already doing something else this turn cannot attract. */
    @Test
    public void refusesWhenTheChannelHasAnotherFunction() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12, enemy, null);
        scout.addLockOn(drone);
        game.breakDroneLockOn(scout, "1", "Drone-1", 4); // fails to break, but commits the channel

        Game.ActionResult r = game.attractDrone(scout, "1", "Drone-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("one function per turn"));
    }

    /** G24.232: the attraction outlives the channel that made it. */
    @Test
    public void staysAttracted_whenTheChannelIsBlinded() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship victim = enemyShip(game, "USS Prey", 11, 10);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12, enemy, victim);
        scout.addLockOn(drone);
        assertTrue(game.attractDrone(scout, "1", "Drone-1").isSuccess());

        channelOf(scout, "1").blind(game.getAbsoluteImpulse());

        assertSame("blinding does not send it back (G24.232)", scout, drone.getTarget());
    }

    @Test
    public void requiresAnOperationalChannel() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12, enemy, null);
        scout.addLockOn(drone);
        channelOf(scout, "1").setPowered(false); // G24.14

        Game.ActionResult r = game.attractDrone(scout, "1", "Drone-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("not operational"));
    }

    @Test
    public void requiresActiveFireControlAndALockOn() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12, enemy, null);

        Game.ActionResult noLock = game.attractDrone(scout, "1", "Drone-1");
        assertFalse(noLock.isSuccess());
        assertTrue(noLock.getMessage(), noLock.getMessage().contains("lock-on"));

        scout.addLockOn(drone);
        scout.setActiveFireControl(false);
        Game.ActionResult passive = game.attractDrone(scout, "1", "Drone-1");
        assertFalse(passive.isSuccess());
        assertTrue(passive.getMessage(), passive.getMessage().contains("active fire control"));
    }

    @Test
    public void refusesBeyondFifteenHexes() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone drone = enemyDrone(game, "Drone-1", 10, 26, enemy, null); // range 16
        scout.addLockOn(drone);

        Game.ActionResult r = game.attractDrone(scout, "1", "Drone-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("out of range"));
    }

    /** G24.234: the transfer needs the scout inside the drone's control range of its controller. */
    @Test
    public void refusesWhenTheControllerIsBeyondThirtyFiveHexes() {
        Game game = new Game();
        Ship scout = scout(game);
        scout.setLocation(new Location(2, 16));
        Ship enemy = enemyShip(game, "Kzin", 40, 16);          // 38 hexes from the scout
        Drone drone = enemyDrone(game, "Drone-1", 2, 18, enemy, null); // but only 2 from it
        scout.addLockOn(drone);

        Game.ActionResult r = game.attractDrone(scout, "1", "Drone-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("G24.234"));
        assertNull("drone keeps its own target", drone.getTarget());
    }

    /** G24.233: plasma torpedoes, plasma-D included, ignore the attraction. */
    @Test
    public void refusesAPlasmaTorpedo() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        PlasmaTorpedo torp = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
        torp.setName("Plasma-1");
        torp.setLocation(new Location(10, 12));
        torp.setController(enemy);
        game.getSeekers().add(torp);
        scout.addLockOn(torp);

        Game.ActionResult r = game.attractDrone(scout, "1", "Plasma-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("G24.233"));
    }

    @Test
    public void refusesAFriendlyDrone() {
        Game game = new Game();
        Ship scout = scout(game);
        Player teamA = new Player();
        teamA.setTeamName("A");
        scout.setOwner(teamA);
        Ship ally = enemyShip(game, "USS Ally", 12, 10);
        ally.setOwner(teamA);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12, ally, null);
        scout.addLockOn(drone);

        Game.ActionResult r = game.attractDrone(scout, "1", "Drone-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("friendly"));
    }

    /** G7.943: a drone held in a tractor beam cannot be drawn off it. */
    @Test
    public void refusesADroneHeldInATractor() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12, enemy, null);
        drone.applyTractor(enemy);
        scout.addLockOn(drone);

        Game.ActionResult r = game.attractDrone(scout, "1", "Drone-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("G7.943"));
    }

    /** FD1.8: a seeking shuttle is drawn off just like a drone. */
    @Test
    public void attractsASeekingShuttle() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship victim = enemyShip(game, "USS Prey", 11, 10);
        com.sfb.objects.shuttles.AdminShuttle base = new com.sfb.objects.shuttles.AdminShuttle();
        base.setName("Kamikaze");
        com.sfb.objects.shuttles.SuicideShuttle shuttle =
                new com.sfb.objects.shuttles.SuicideShuttle(base);
        shuttle.setLocation(new Location(10, 12));
        shuttle.setTarget(victim);
        game.getSeekers().add(shuttle);
        game.getActiveShuttles().add(shuttle);
        scout.addLockOn(shuttle);

        Game.ActionResult r = game.attractDrone(scout, "1", "Kamikaze");

        assertTrue(r.getMessage(), r.isSuccess());
        assertSame(scout, shuttle.getTarget());
    }

    /**
     * G24.235: an unidentified enemy shuttle may be tried — a plain one simply does not answer,
     * which is itself the answer. The channel is spent on it either way.
     */
    @Test
    public void aPlainShuttleDoesNotRespond_andIsRevealed() {
        Game game = new Game();
        Ship scout = scout(game);
        com.sfb.objects.shuttles.AdminShuttle shuttle = new com.sfb.objects.shuttles.AdminShuttle();
        shuttle.setName("Bluff");
        shuttle.setLocation(new Location(10, 12));
        game.getActiveShuttles().add(shuttle);
        scout.addLockOn(shuttle);

        Game.ActionResult r = game.attractDrone(scout, "1", "Bluff");

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("not a seeking weapon"));
        assertTrue("revealed as no threat (G24.235)", shuttle.isIdentified());
        assertEquals("the channel is still spent (G24.231)",
                ScoutChannel.Function.ATTRACT_DRONES, channelOf(scout, "1").getTurnFunction());
    }

    /** The per-turn commitment clears at the next Energy Allocation. */
    @Test
    public void channelIsFreedNextTurn() {
        Game game = new Game();
        Ship scout = scout(game);
        Ship enemy = enemyShip(game, "Kzin", 12, 10);
        Drone drone = enemyDrone(game, "Drone-1", 10, 12, enemy, null);
        scout.addLockOn(drone);
        assertTrue(game.attractDrone(scout, "1", "Drone-1").isSuccess());

        scout.startTurn();

        assertEquals(ScoutChannel.Function.NONE, channelOf(scout, "1").getTurnFunction());
        assertNull(channelOf(scout, "1").getAttractedDrone());
    }
}
