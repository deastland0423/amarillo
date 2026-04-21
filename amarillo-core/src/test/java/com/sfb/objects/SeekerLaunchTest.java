package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.TurnTracker;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.DroneRack;

/**
 * Tests for seeker launch mechanics:
 *   - Unique name assignment at launch (regression guard for the empty-name bug
 *     that caused any fire command to hit whichever seeker was launched first)
 *   - Lock-on acquisition at launch (checkLockOnsForNewUnit): launcher always
 *     gets lock-on; other ships with fire control roll per sensor rating
 */
public class SeekerLaunchTest {

    private Game  game;
    private Ship  launcher;   // FedFFG — has drone rack, sensor 6
    private Ship  target;     // Klingon D7 — the fire target
    private DroneRack rack;
    private Drone firstDrone;

    @Before
    public void setUp() {
        TurnTracker.reset();
        game = new Game();

        launcher = new Ship();
        launcher.init(FederationShips.getFedFfg());
        launcher.setName("Burke");
        launcher.setLocation(new Location(10, 10));
        launcher.setFacing(1);
        launcher.setActiveFireControl(true);

        target = new Ship();
        target.init(KlingonShips.getD7());
        target.setName("IKV Saber");
        target.setLocation(new Location(10, 14));
        target.setFacing(13);

        game.getShips().add(launcher);
        game.getShips().add(target);

        rack = launcher.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(w -> (DroneRack) w)
                .findFirst().orElse(null);
        assertNotNull("FedFFG must have a drone rack", rack);

        firstDrone = rack.getAmmo().get(0);

        // Advance to ACTIVITY phase (launcher = MOVEMENT → ACTIVITY)
        game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    // -------------------------------------------------------------------------
    // Seeker naming — regression guard
    // -------------------------------------------------------------------------

    @Test
    public void launchedDrone_hasNonEmptyName() {
        ActionResult result = game.launchDrone(launcher, target, rack, firstDrone);
        assertTrue(result.getMessage(), result.isSuccess());

        Seeker launched = game.getSeekers().get(0);
        assertFalse("Launched drone must have a non-empty name — empty name caused all seekers "
                + "to resolve to the first one in the list",
                ((Unit) launched).getName().isEmpty());
    }

    @Test
    public void launchedDrone_nameContainsLauncherShipName() {
        game.launchDrone(launcher, target, rack, firstDrone);
        String name = ((Unit) game.getSeekers().get(0)).getName();
        assertTrue("Drone name should include the launcher's ship name for traceability",
                name.contains("Burke"));
    }

    @Test
    public void launchedDrone_nameContainsDroneLabel() {
        game.launchDrone(launcher, target, rack, firstDrone);
        String name = ((Unit) game.getSeekers().get(0)).getName();
        assertTrue("Drone name should contain 'Drone'", name.contains("Drone"));
    }

    @Test
    public void twoDrones_launchedFromDifferentShips_haveDistinctNames() {
        // Second ship also has a drone rack (FedOCL)
        Ship launcher2 = new Ship();
        launcher2.init(FederationShips.getFedOcl());
        launcher2.setName("Lexington");
        launcher2.setLocation(new Location(12, 10));
        launcher2.setFacing(1);
        game.getShips().add(launcher2);

        DroneRack rack2 = launcher2.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(w -> (DroneRack) w)
                .findFirst().orElse(null);
        assertNotNull("FedOCL must have a drone rack", rack2);
        Drone drone2 = rack2.getAmmo().get(0);

        game.launchDrone(launcher,  target, rack,  firstDrone);
        game.launchDrone(launcher2, target, rack2, drone2);

        assertEquals("Two drones should be in the seekers list", 2, game.getSeekers().size());

        String name1 = ((Unit) game.getSeekers().get(0)).getName();
        String name2 = ((Unit) game.getSeekers().get(1)).getName();

        assertNotEquals("Two launched drones must have distinct names — "
                + "duplicate names caused the wrong seeker to be hit by fire",
                name1, name2);
    }

    @Test
    public void seekerSequence_incrementsAcrossLaunches() {
        // Each drone gets a unique sequence number; verify they differ
        Ship launcher2 = new Ship();
        launcher2.init(FederationShips.getFedOcl());
        launcher2.setName("Lexington");
        launcher2.setLocation(new Location(12, 10));
        launcher2.setFacing(1);
        game.getShips().add(launcher2);

        DroneRack rack2 = launcher2.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(w -> (DroneRack) w)
                .findFirst().orElse(null);
        assertNotNull("FedOCL must have a drone rack", rack2);

        game.launchDrone(launcher,  target, rack,  firstDrone);
        game.launchDrone(launcher2, target, rack2, rack2.getAmmo().get(0));

        String name1 = ((Unit) game.getSeekers().get(0)).getName();
        String name2 = ((Unit) game.getSeekers().get(1)).getName();

        // Extract trailing sequence numbers and confirm they differ
        int seq1 = Integer.parseInt(name1.substring(name1.lastIndexOf('-') + 1));
        int seq2 = Integer.parseInt(name2.substring(name2.lastIndexOf('-') + 1));
        assertNotEquals("Sequence numbers must be distinct", seq1, seq2);
    }

    // -------------------------------------------------------------------------
    // Lock-on at launch (checkLockOnsForNewUnit)
    // -------------------------------------------------------------------------

    @Test
    public void launcher_alwaysHasLockOnAfterDroneLaunch() {
        game.launchDrone(launcher, target, rack, firstDrone);

        assertTrue("Launcher must always have lock-on to its own drone (auto lock-on rule)",
                launcher.hasLockOn(firstDrone));
    }

    @Test
    public void launcherHasLockOn_evenWithNoActiveFireControl() {
        // Auto lock-on for the launcher is not conditional on fire control —
        // it's implicit because the launcher knows where its own seeker went
        launcher.setActiveFireControl(false);
        game.launchDrone(launcher, target, rack, firstDrone);

        assertTrue("Launcher auto lock-on is unconditional, not gated by fire control",
                launcher.hasLockOn(firstDrone));
    }

    @Test
    public void shipWithSensorSix_acquiresLockOnAfterDroneLaunch() {
        // FedFFG has sensor 6, guaranteeing a successful roll
        Ship observer = new Ship();
        observer.init(FederationShips.getFedFfg());
        observer.setName("Observer");
        observer.setLocation(new Location(10, 12));
        observer.setFacing(13);
        observer.setActiveFireControl(true);
        game.getShips().add(observer);

        game.launchDrone(launcher, target, rack, firstDrone);

        assertTrue("Sensor-6 ship should always acquire lock-on to a newly launched drone",
                observer.hasLockOn(firstDrone));
    }

    @Test
    public void shipWithNoFireControl_noLockOnAfterDroneLaunch() {
        Ship observer = new Ship();
        observer.init(KlingonShips.getD7());
        observer.setName("Passive Observer");
        observer.setLocation(new Location(10, 12));
        observer.setFacing(13);
        observer.setActiveFireControl(false); // no fire control — cannot lock on
        game.getShips().add(observer);

        game.launchDrone(launcher, target, rack, firstDrone);

        assertFalse("Ship without active fire control must not acquire lock-on",
                observer.hasLockOn(firstDrone));
    }

    @Test
    public void launchLog_mentionsLockOnAcquisition() {
        Ship observer = new Ship();
        observer.init(FederationShips.getFedFfg());
        observer.setName("Observer");
        observer.setLocation(new Location(10, 12));
        observer.setFacing(13);
        observer.setActiveFireControl(true);
        game.getShips().add(observer);

        ActionResult result = game.launchDrone(launcher, target, rack, firstDrone);

        assertTrue("Launch result message should note observer lock-on acquisition",
                result.getMessage().contains("Observer"));
    }
}
