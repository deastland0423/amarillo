package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Aas;
import com.sfb.properties.Location;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * A drone-armed fighter putting its own drones on the map (J1.31, J4.24).
 * <p>
 * Until now it could not: every seeker-launch path was typed to Ship and the server resolved
 * its launcher with findShip, so an AAS carried two Type-Is it had no way to release. The
 * fighter half was largely built — DroneController, the lock-on set, the one-per-turn flag —
 * and simply had nothing wired to it, including anything that ever filled the lock-ons.
 * <p>
 * A fighter is not a small ship, and the gates differ: no breakdown lockout and no cloak, but
 * one drone a turn (J4.24) and the half-turn wait after its own launch (J1.341).
 */
public class FighterDroneLaunchTest {

    private Game game;
    private Ship enemy;
    private Aas aas;

    @Before
    public void setUp() {
        game = new Game();

        Player kz = new Player();
        kz.setTeamName("Kzinti");
        Player fed = new Player();
        fed.setTeamName("Federation");

        enemy = new Ship();
        enemy.init(com.sfb.samples.FederationShips.getFedCa());
        enemy.setName("USS Target");
        enemy.setLocation(new Location(12, 10));
        enemy.setFacing(13);
        enemy.setOwner(fed);
        game.getShips().add(enemy);

        aas = new Aas();
        aas.setName("AAS-1");
        aas.setLocation(new Location(10, 10));
        aas.setFacing(1);
        aas.setOwner(kz);
        game.getActiveShuttles().add(aas);

        game.startTurn();
        // Out of the bay long enough that J1.341 is satisfied.
        aas.setLaunchImpulse(game.getAbsoluteImpulse() - 100);
        loadBothRails();
    }

    private void loadBothRails() {
        for (DroneRail rail : rails())
            if (rail.getDrone() == null)
                rail.loadDrone(new Drone(DroneType.TypeI));
    }

    private java.util.List<DroneRail> rails() {
        java.util.List<DroneRail> out = new java.util.ArrayList<>();
        for (Weapon w : aas.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                out.add(rail);
        return out;
    }

    /** Puts the game in the one phase a launch is legal in. */
    private void intoActivity() {
        int guard = 0;
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY && guard++ < 40)
            game.advancePhase();
    }

    private Game.ActionResult launch() {
        intoActivity();
        aas.addLockOn(enemy);
        return game.launchFighterDrone(aas, enemy, rails().get(0), 0);
    }

    // -------------------------------------------------------------------------

    @Test
    public void aFighterCanPutItsOwnDroneOnTheMap() {
        int before = game.getSeekers().size();

        Game.ActionResult result = launch();

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals("the drone is on the map", before + 1, game.getSeekers().size());
        assertNull("and off the rail it came from", rails().get(0).getDrone());
    }

    @Test
    public void theFighterGuidesTheDroneItLaunched() {
        launch();

        com.sfb.objects.Seeker drone = game.getSeekers().get(game.getSeekers().size() - 1);
        assertSame("J4.25: a fighter guides its own", aas, drone.getController());
        assertEquals(1, aas.getControlUsed());
    }

    /** J4.24: one drone a turn, however many rails it is carrying. */
    @Test
    public void onlyOneDroneLeavesInATurn() {
        assertTrue(launch().isSuccess());

        Game.ActionResult second = game.launchFighterDrone(aas, enemy, rails().get(1), 0);

        assertFalse(second.isSuccess());
        assertTrue(second.getMessage(), second.getMessage().contains("J4.24"));
        assertNotNull("the second drone stays on its rail", rails().get(1).getDrone());
    }

    @Test
    public void theNextTurnGivesItAnotherLaunch() {
        launch();
        aas.startTurn();

        assertFalse("the turn's launch is spent no longer", aas.isDronesFiredThisTurn());
    }

    /** D6.121: no lock-on, no seeking weapon. */
    @Test
    public void aFighterWithNoLockOnCannotLaunch() {
        intoActivity();
        aas.getLockOns().clear();

        Game.ActionResult result = game.launchFighterDrone(aas, enemy, rails().get(0), 0);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("D6.121"));
        assertNotNull("and the drone is still on the rail", rails().get(0).getDrone());
    }

    /** J1.341: half a turn after its own launch before it may release one. */
    @Test
    public void aJustLaunchedFighterMustWaitOutTheHalfTurn() {
        intoActivity();
        aas.addLockOn(enemy);
        aas.setLaunchImpulse(game.getAbsoluteImpulse());

        Game.ActionResult result = game.launchFighterDrone(aas, enemy, rails().get(0), 0);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("J1.341"));
    }

    @Test
    public void anEmptyRailLaunchesNothing() {
        intoActivity();
        aas.addLockOn(enemy);
        rails().get(0).setAmmo(new java.util.ArrayList<>());

        Game.ActionResult result = game.launchFighterDrone(aas, enemy, rails().get(0), 0);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("empty"));
    }

    /** J1.332: a crippled fighter drops its external weapons, drones included. */
    @Test
    public void aCrippledFighterHasNoDronesToLaunch() {
        intoActivity();
        aas.addLockOn(enemy);
        aas.setCurrentHull(1);             // past the crippling threshold (J1.33)
        aas.applyCripplingEffects();       // as DamageResolver does when it crosses it
        assertTrue("fixture must actually be crippled", aas.isCrippled());

        Game.ActionResult result = game.launchFighterDrone(aas, enemy, rails().get(0), 0);

        assertFalse(result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // Lock-on at the moment of launch
    // -------------------------------------------------------------------------

    /**
     * A fighter takes its lock-ons as it leaves the bay, not at the next turn boundary.
     * <p>
     * The turn-start sweep runs once, in startTurn, and a fighter still in its bay is not
     * on the map to be swept. So a craft launched on impulse 5 held nothing until the next
     * turn began — while J1.341 clears it to launch a drone on impulse 21, which is usually
     * the SAME turn. Launch on 5, be refused on 21 for want of a lock-on, with nothing
     * having gone wrong.
     * <p>
     * There is no roll being skipped: J1.31 gives a shuttle sensor rating six, automatic
     * under D6.11, and J1.344 has it launch with active fire control unless its owner says
     * otherwise.
     */
    @Test
    public void aFighterHasItsLockOnsTheImpulseItLaunches() {
        Game g = new Game();
        Player kz = new Player();
        kz.setTeamName("Kzinti");
        Player fed = new Player();
        fed.setTeamName("Federation");

        Ship carrier;
        try {
            carrier = com.sfb.objects.ShipLibrary.createShip(
                    com.sfb.objects.ShipSpec.fromJson(
                            new java.io.File("../data/factions/kzinti/cv.json")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        carrier.setName("KHS Sabre");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        carrier.setOwner(kz);
        g.getShips().add(carrier);

        Ship victim = new Ship();
        victim.init(com.sfb.samples.FederationShips.getFedCa());
        victim.setName("USS Bystander");
        victim.setLocation(new Location(13, 10));
        victim.setFacing(13);
        victim.setOwner(fed);
        g.getShips().add(victim);

        g.startTurn();   // the one lock-on sweep of the turn happens HERE, bay still shut

        com.sfb.systemgroups.ShuttleBay bay = carrier.getShuttles().getBays().get(0);
        com.sfb.objects.shuttles.Shuttle craft = null;
        for (com.sfb.objects.shuttles.Shuttle sh : bay.getInventory())
            if (craft == null && sh instanceof Aas)
                craft = sh;
        assertNotNull("fixture needs a fighter in the bay", craft);

        while (g.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            g.advancePhase();

        Game.ActionResult launched = g.launchShuttle(carrier, bay, craft, 8, 1);
        assertTrue(launched.getMessage(), launched.isSuccess());

        Aas flier = (Aas) craft;
        assertTrue("it must hold a lock-on the moment it is on the map, not next turn",
                flier.hasLockOn(victim));
    }

    /**
     * J1.6202: a shuttle held in a tractor "may not fire, launch, or guide any weapon".
     * <p>
     * Flatter than the ship rule beside it: G7.91 still lets a tractored SHIP shoot back at
     * whatever has hold of it, and a shuttle gets no such allowance. Worth pinning because
     * only being SHUT DOWN for recovery (J1.622) was blocked — merely being held was not,
     * so a fighter under tow could keep throwing drones.
     */
    @Test
    public void aFighterUnderTowCannotLaunch() {
        intoActivity();
        aas.addLockOn(enemy);
        aas.applyTractor(enemy);

        Game.ActionResult result = game.launchFighterDrone(aas, enemy, rails().get(0), 0);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("J1.6202"));
        assertNotNull("and the drone stays on the rail", rails().get(0).getDrone());
    }
}
