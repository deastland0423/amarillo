package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * J1.61 unassisted landing: a friendly manned shuttle in the ship's hex flies
 * itself through the hatch. Ship must not be moving faster than the shuttle,
 * an empty box must exist, and the hatch cooldown is shared with launches
 * (J1.50 — one operation per 2 impulses; confirmed by user 2026-07-10).
 */
public class LandingTest {

    private Game game;
    private Ship fed;
    private Ship klingon;
    private Player fedPlayer;
    private Player klingonPlayer;

    @Before
    public void setUp() {
        game = new Game();

        fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");
        klingonPlayer = new Player();
        klingonPlayer.setTeamName("Klingons");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setOwner(fedPlayer);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(30, 20));
        klingon.setFacing(1);
        klingon.setOwner(klingonPlayer);
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

    private void advanceToActivity(int minImpulse) {
        for (int guard = 0; guard < 80; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY
                    && game.getCurrentImpulse() >= minImpulse)
                return;
            game.advancePhase();
        }
        fail("Never reached ACTIVITY at impulse >= " + minImpulse);
    }

    /** Launch the first stock shuttle from fed's first bay at speed 0. */
    private Shuttle launchStockShuttle() {
        ShuttleBay bay = fed.getShuttles().getBays().get(0);
        Shuttle shuttle = bay.getInventory().get(0);
        Game.ActionResult r = game.launchShuttle(fed, bay, shuttle, 0, 1);
        assertTrue("Launch must succeed: " + r.getMessage(), r.isSuccess());
        return shuttle;
    }

    // -------------------------------------------------------------------------

    @Test
    public void launchThenLand_roundTrip_respectingHatchCooldown() {
        allocate(0.0);
        advanceToActivity(1);
        Shuttle shuttle = launchStockShuttle();
        ShuttleBay bay = fed.getShuttles().getBays().get(0);
        int spacesFree = bay.getEmptySpaceCount();
        assertTrue(spacesFree > 0);

        // Same Activity phase: the hatch just cycled for the launch (J1.50)
        Game.ActionResult tooSoon = game.landShuttle(fed, shuttle.getName());
        assertFalse("Hatch cooldown is shared between launch and landing",
                tooSoon.isSuccess());
        assertTrue(tooSoon.getMessage().contains("cooldown"));

        // Two impulses later the hatch is ready
        advanceToActivity(game.getCurrentImpulse() + 2);
        Game.ActionResult r = game.landShuttle(fed, shuttle.getName());

        assertTrue(r.getMessage(), r.isSuccess());
        assertFalse("Landed shuttle leaves the map", game.getActiveShuttles().contains(shuttle));
        assertNull(shuttle.getLocation());
        assertTrue("Landed shuttle is back in the bay inventory (same object — state retained)",
                bay.getInventory().contains(shuttle));
        assertEquals(spacesFree - 1, bay.getEmptySpaceCount());
    }

    @Test
    public void recoveredShuttleDisembarksItsHoldIntoTheShip() {
        allocate(0.0);
        advanceToActivity(1);
        Shuttle shuttle = launchStockShuttle();
        shuttle.getHold().addCrew(1); // a survey team riding home in the shuttle
        advanceToActivity(game.getCurrentImpulse() + 2); // hatch ready

        Game.ActionResult r = game.landShuttle(fed, shuttle.getName());

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("survey team disembarked into the ship (SH50.46)", 1, fed.getManifest().getCrew());
        assertEquals("hold emptied on recovery", 0, shuttle.getHold().getCrew());
        assertTrue("log notes it: " + r.getMessage(), r.getMessage().contains("disembarked"));
    }

    @Test
    public void land_failsWhenNotInShipsHex() {
        allocate(0.0);
        advanceToActivity(1);
        Shuttle shuttle = launchStockShuttle();
        shuttle.setLocation(new Location(12, 10)); // wandered off
        advanceToActivity(game.getCurrentImpulse() + 2);

        Game.ActionResult r = game.landShuttle(fed, shuttle.getName());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("same hex"));
        assertTrue(game.getActiveShuttles().contains(shuttle));
    }

    @Test
    public void land_failsWhenShipMovingFasterThanShuttle() {
        allocate(10.0); // fed speed 10; shuttle launched at speed 0
        advanceToActivity(1);
        Shuttle shuttle = launchStockShuttle();
        // Keep the shuttle co-hexed with the (moving) ship for the check
        shuttle.setLocation(fed.getLocation());
        advanceToActivity(game.getCurrentImpulse() + 2);
        shuttle.setLocation(fed.getLocation());

        Game.ActionResult r = game.landShuttle(fed, shuttle.getName());

        assertFalse("Ship faster than shuttle cannot recover it unassisted (J1.61)",
                r.isSuccess());
        assertTrue(r.getMessage().contains("faster"));
    }

    @Test
    public void land_refusesEnemyShuttle() {
        allocate(0.0);
        advanceToActivity(1);
        Shuttle shuttle = launchStockShuttle();
        shuttle.setOwner(klingonPlayer); // suddenly hostile
        advanceToActivity(game.getCurrentImpulse() + 2);

        Game.ActionResult r = game.landShuttle(fed, shuttle.getName());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("friendly"));
    }

    @Test
    public void land_refusesWildWeasel() {
        allocate(0.0);
        advanceToActivity(1);
        WildWeaselShuttle ww = new WildWeaselShuttle(fed);
        ww.setName("Enterprise-WW");
        ww.setOwner(fedPlayer);
        ww.setLocation(fed.getLocation());
        game.getActiveShuttles().add(ww);

        Game.ActionResult r = game.landShuttle(fed, ww.getName());

        assertFalse("Active WWs need a tractor to land (J1.611)", r.isSuccess());
        assertTrue(r.getMessage().contains("J1.611"));
    }

    @Test
    public void land_seekersChasingShuttleLoseTracking() {
        allocate(0.0);
        advanceToActivity(1);
        Shuttle shuttle = launchStockShuttle();

        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("Chaser-1");
        drone.setLocation(new Location(15, 10));
        drone.setTarget(shuttle);
        game.getSeekers().add(drone);

        advanceToActivity(game.getCurrentImpulse() + 2);
        Game.ActionResult r = game.landShuttle(fed, shuttle.getName());

        assertTrue(r.getMessage(), r.isSuccess());
        assertFalse("Seeker chasing the landed shuttle is removed",
                game.getSeekers().contains(drone));
        assertTrue("Log explains the lost tracking: " + r.getMessage(),
                r.getMessage().contains("lost tracking"));
    }
}
