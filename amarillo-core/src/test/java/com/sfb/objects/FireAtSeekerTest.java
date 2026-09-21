package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;

/**
 * Tests for applying weapon damage to seekers (drones and plasma torpedoes).
 *
 * Covers:
 * - applyDamageToUnit routing to drone hull
 * - Drone destruction and removal from seekers list
 * - Correct seeker targeted when multiple seekers are present
 * (regression guard for the empty-name bug that caused all fire to hit
 * whichever seeker was first in the list)
 */
public class FireAtSeekerTest {

    private Game game;
    private Ship attacker;
    private Ship target;

    @Before
    public void setUp() {
        game = new Game();

        attacker = new Ship();
        attacker.init(FederationShips.getFedCa());
        attacker.setName("Enterprise");
        attacker.setLocation(new Location(10, 10));
        attacker.setFacing(1);
        attacker.setActiveFireControl(true);

        target = new Ship();
        target.init(KlingonShips.getD7());
        target.setName("IKV Saber");
        target.setLocation(new Location(10, 14));
        target.setFacing(13);

        game.getShips().add(attacker);
        game.getShips().add(target);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Build a named drone at a given location and add it to the game's seekers. */
    private Drone addDrone(String name, int col, int row) {
        Drone drone = new Drone(DroneType.TypeI); // hull = 4
        drone.setName(name);
        drone.setLocation(new Location(col, row));
        drone.setController(attacker);
        drone.setSeekerType(Seeker.SeekerType.DRONE);
        drone.setLaunchImpulse(game.getClock().getImpulse());
        game.getSeekers().add(drone);
        return drone;
    }

    // -------------------------------------------------------------------------
    // Drone hull damage
    // -------------------------------------------------------------------------

    /**
     * G4.2: an unidentified drone's remaining hull is not the shooter's to know — DroneDto
     * sends 0 to an enemy and publishes damageTaken instead. The combat log is ONE list
     * broadcast to every player, so it cannot be redacted per viewer; it reports the damage
     * done and stops there. The owner reads what is left off the drone's own panel.
     * <p>
     * Found in a playtest: "hit for 2 — 2 hull remaining" against a drone nobody had
     * identified. A shuttle is deliberately different and keeps its hull in the log — its
     * craft type is public, so the hull behind the hits was never a secret.
     */
    @Test
    public void damageLog_doesNotRevealAnUnidentifiedDronesHull() {
        Drone drone = addDrone("Enterprise-Drone-1", 10, 11);   // TypeI, 4 hull

        String log = game.applyDamageToUnit(2, drone, 0);

        assertTrue(log, log.contains("hit for 2"));
        // Deliberately not "does not contain 2": the damage dealt is 2 as well, and here so
        // is the hull left, which is exactly how a loose assertion would pass while leaking.
        assertFalse("the shared log must not carry a drone's remaining hull (G4.2)",
                log.contains("hull remaining"));
        assertEquals("the hull still goes down, it is simply not announced", 2, drone.getHull());
    }

    @Test
    public void partialDamage_reducesHull() {
        Drone drone = addDrone("Enterprise-Drone-1", 10, 11);
        int initialHull = drone.getHull(); // TypeI = 4

        game.applyDamageToUnit(2, drone, 0);

        assertEquals(initialHull - 2, drone.getHull());
    }

    @Test
    public void partialDamage_droneRemainsInSeekers() {
        Drone drone = addDrone("Enterprise-Drone-1", 10, 11);

        game.applyDamageToUnit(2, drone, 0);

        assertTrue("Drone with remaining hull should still be in seekers list",
                game.getSeekers().contains(drone));
    }

    @Test
    public void lethalDamage_droneRemovedFromSeekers() {
        Drone drone = addDrone("Enterprise-Drone-1", 10, 11);

        game.applyDamageToUnit(drone.getHull(), drone, 0); // exact lethal hit

        assertFalse("Destroyed drone must be removed from seekers list",
                game.getSeekers().contains(drone));
    }

    @Test
    public void excessDamage_droneRemovedFromSeekers() {
        Drone drone = addDrone("Enterprise-Drone-1", 10, 11);

        game.applyDamageToUnit(drone.getHull() + 10, drone, 0); // overkill

        assertFalse("Drone destroyed by overkill must be removed from seekers",
                game.getSeekers().contains(drone));
    }

    @Test
    public void lethalDamage_droneHullClampedToZero() {
        Drone drone = addDrone("Enterprise-Drone-1", 10, 11);

        game.applyDamageToUnit(drone.getHull() + 5, drone, 0);

        assertEquals("Hull must not go negative", 0, drone.getHull());
    }

    @Test
    public void destroyedDrone_logMentionsDestroyed() {
        Drone drone = addDrone("Enterprise-Drone-1", 10, 11);

        String log = game.applyDamageToUnit(drone.getHull(), drone, 0);

        assertTrue("Log should say the drone was destroyed", log.contains("destroyed"));
    }

    @Test
    public void hitDrone_logMentionsDamage() {
        Drone drone = addDrone("Enterprise-Drone-1", 10, 11);

        String log = game.applyDamageToUnit(1, drone, 0);

        assertTrue("Log should report the damage amount", log.contains("1"));
    }

    // -------------------------------------------------------------------------
    // Correct seeker targeted when multiple seekers present
    // Regression guard: before unique names, all seekers had name "" and
    // any name lookup returned the first seeker in the list.
    // -------------------------------------------------------------------------

    @Test
    public void damageAppliedToCorrectDrone_otherDroneUntouched() {
        Drone drone1 = addDrone("Enterprise-Drone-1", 10, 11);
        Drone drone2 = addDrone("IKV-Drone-2", 10, 12);

        int hull1Before = drone1.getHull();
        int hull2Before = drone2.getHull();

        // Damage drone2 specifically by passing the object reference
        game.applyDamageToUnit(2, drone2, 0);

        assertEquals("Untargeted drone1 must be undamaged", hull1Before, drone1.getHull());
        assertEquals("Targeted drone2 hull reduced", hull2Before - 2, drone2.getHull());
    }

    @Test
    public void destroyingOneDrone_doesNotRemoveOther() {
        Drone drone1 = addDrone("Enterprise-Drone-1", 10, 11);
        Drone drone2 = addDrone("IKV-Drone-2", 10, 12);

        game.applyDamageToUnit(drone1.getHull(), drone1, 0); // destroy drone1

        assertFalse("drone1 should be gone", game.getSeekers().contains(drone1));
        assertTrue("drone2 must survive", game.getSeekers().contains(drone2));
    }

    // -------------------------------------------------------------------------
    // Plasma torpedo damage
    // -------------------------------------------------------------------------

    @Test
    public void damageToPlasma_reducesStrength() {
        PlasmaTorpedo plasma = new PlasmaTorpedo(
                com.sfb.properties.PlasmaType.F,
                com.sfb.properties.WeaponArmingType.STANDARD);
        plasma.setName("RIS-Plasma-1");
        plasma.setLocation(new Location(10, 11));
        plasma.setController(target);
        plasma.setSeekerType(Seeker.SeekerType.PLASMA);
        plasma.setLaunchImpulse(game.getClock().getImpulse());
        game.getSeekers().add(plasma);

        int strengthBefore = plasma.getCurrentStrength();
        game.applyDamageToUnit(20, plasma, 0);

        assertTrue("Phaser damage to plasma should reduce its current strength",
                plasma.getCurrentStrength() < strengthBefore);
    }

    @Test
    public void damageToPlasma_logConfirmsDamageRouted() {
        PlasmaTorpedo plasma = new PlasmaTorpedo(
                com.sfb.properties.PlasmaType.F,
                com.sfb.properties.WeaponArmingType.STANDARD);
        plasma.setName("RIS-Plasma-1");
        plasma.setLocation(new Location(10, 11));
        plasma.setController(target);
        plasma.setSeekerType(Seeker.SeekerType.PLASMA);
        plasma.setLaunchImpulse(game.getClock().getImpulse());
        game.getSeekers().add(plasma);

        String log = game.applyDamageToUnit(10, plasma, 0);

        assertFalse("Plasma damage must not fall through to 'unknown unit type' handler",
                log.contains("unknown"));
        assertTrue("Log should reference the plasma's name",
                log.contains("RIS-Plasma-1"));
    }
}
