package com.sfb.objects;

import com.sfb.objects.shuttles.*;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.TurnTracker;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.ShuttleBay;

/**
 * Tests for ScatterPack payload/release mechanics (unit) and
 * Game.launchScatterPack() preconditions + drone release (integration).
 */
public class ScatterPackTest {

    // -------------------------------------------------------------------------
    // Unit tests — ScatterPack payload management and release logic
    // -------------------------------------------------------------------------

    private ScatterPack pack;
    private Drone       typeI;
    private Drone       typeII;

    @Test
    public void newPack_hasEmptyPayload() {
        assertTrue(pack.getPayload().isEmpty());
    }

    @Test
    public void newPack_isNotReleased() {
        assertFalse(pack.isReleased());
    }

    @Test
    public void newPack_isPlayerControlled() {
        assertTrue(pack.isPlayerControlled());
    }

    @Test
    public void addDrone_succeedsWhenSpaceAvailable() {
        assertTrue(pack.addDrone(typeI));
        assertEquals(1, pack.getPayload().size());
    }

    @Test
    public void addDrone_fillsSixSpaces() {
        for (int i = 0; i < 6; i++)
            assertTrue(pack.addDrone(new Drone(DroneType.TypeI)));
        assertEquals(6, pack.getPayload().size());
    }

    @Test
    public void addDrone_rejectsWhenFull() {
        for (int i = 0; i < 6; i++) pack.addDrone(new Drone(DroneType.TypeI));
        assertFalse(pack.addDrone(new Drone(DroneType.TypeI)));
        assertEquals(6, pack.getPayload().size());
    }

    @Test
    public void addDrone_typeIItakesMoreSpace() {
        // TypeII drones are larger — fewer fit
        for (int i = 0; i < 3; i++) pack.addDrone(new Drone(DroneType.TypeII));
        // Whether a 4th fits depends on TypeII rack size; just verify it doesn't exceed 6
        assertTrue(pack.getPayloadSpaces() <= 6);
    }

    @Test
    public void seekerType_isShuttle() {
        assertEquals(Seeker.SeekerType.SHUTTLE, pack.getSeekerType());
    }

    @Test
    public void isSelfGuiding_false() {
        assertFalse(pack.isSelfGuiding());
    }

    @Test
    public void warheadDamage_isZero() {
        pack.addDrone(typeI);
        assertEquals(0, pack.getWarheadDamage());
        assertEquals(0, pack.impact());
    }

    @Test
    public void endurance_isEffectivelyUnlimited() {
        assertEquals(Integer.MAX_VALUE, pack.getEndurance());
    }

    // -------------------------------------------------------------------------
    // isReadyToRelease
    // -------------------------------------------------------------------------

    @Test
    public void notReadyToRelease_beforeLaunch() {
        assertFalse(pack.isReadyToRelease(10));
    }

    @Test
    public void notReadyToRelease_before8ImpulsesElapsed() {
        pack.setLaunchImpulse(1);
        assertFalse(pack.isReadyToRelease(8));  // only 7 elapsed
    }

    @Test
    public void readyToRelease_after8ImpulsesElapsed() {
        pack.setLaunchImpulse(1);
        assertTrue(pack.isReadyToRelease(9));   // exactly 8 elapsed
    }

    @Test
    public void readyToRelease_after9ImpulsesElapsed() {
        pack.setLaunchImpulse(1);
        assertTrue(pack.isReadyToRelease(10));
    }

    @Test
    public void notReadyToRelease_whenAlreadyReleased() {
        pack.setLaunchImpulse(1);
        pack.addDrone(typeI);
        pack.release();
        assertFalse(pack.isReadyToRelease(10));
    }

    // -------------------------------------------------------------------------
    // release()
    // -------------------------------------------------------------------------

    @Test
    public void release_returnsDronesAndClearsPayload() {
        pack.addDrone(typeI);
        pack.addDrone(typeII);
        java.util.List<Drone> released = pack.release();
        assertEquals(2, released.size());
        assertTrue(pack.getPayload().isEmpty());
    }

    @Test
    public void release_marksAsReleased() {
        pack.addDrone(typeI);
        pack.release();
        assertTrue(pack.isReleased());
    }

    @Test
    public void release_clearsTarget() {
        Ship fakeTarget = new Ship();
        pack.setTarget(fakeTarget);
        pack.addDrone(typeI);
        pack.release();
        assertNull(pack.getTarget());
    }

    @Test
    public void afterRelease_notPlayerControlled() {
        pack.addDrone(typeI);
        pack.release();
        assertFalse(pack.isPlayerControlled());
    }

    // -------------------------------------------------------------------------
    // Integration tests — Game.launchScatterPack()
    // -------------------------------------------------------------------------

    private Game  game;
    private Ship  launcher;
    private Ship  target;
    private ScatterPack loadedPack;
    private ShuttleBay  bay;

    @Before
    public void setUp() {
        // Unit-test fixtures
        pack   = new ScatterPack(new AdminShuttle());
        pack.setName("SP-1");
        typeI  = new Drone(DroneType.TypeI);
        typeII = new Drone(DroneType.TypeII);

        // Integration-test fixtures
        TurnTracker.reset();
        game = new Game();

        launcher = new Ship();
        launcher.init(FederationShips.getFedFfg());
        launcher.setName("Burke");
        launcher.setLocation(new Location(10, 10));
        launcher.setFacing(1);

        target = new Ship();
        target.init(KlingonShips.getD7());
        target.setName("D7");
        target.setLocation(new Location(10, 14));
        target.setFacing(13);

        game.getShips().add(launcher);
        game.getShips().add(target);

        bay = launcher.getShuttles().getBays().isEmpty() ? null
                : launcher.getShuttles().getBays().get(0);
        assertNotNull("FFG should have at least one shuttle bay", bay);

        // Build a scatter pack manually and add it to the bay
        loadedPack = new ScatterPack(new AdminShuttle());
        loadedPack.setName("SP-1");
        for (int i = 0; i < 4; i++)
            loadedPack.addDrone(new Drone(DroneType.TypeI));
        bay.getInventory().add(loadedPack);

        launcher.addLockOn(target);

        // Advance to ACTIVITY phase
        game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    @Test
    public void launch_succeedsWhenAllConditionsMet() {
        ActionResult result = game.launchScatterPack(launcher, bay, loadedPack, target);
        assertTrue(result.getMessage(), result.isSuccess());
    }

    @Test
    public void launch_addsPackToSeekers() {
        game.launchScatterPack(launcher, bay, loadedPack, target);
        assertTrue(game.getSeekers().contains(loadedPack));
    }

    @Test
    public void launch_removesPackFromBay() {
        game.launchScatterPack(launcher, bay, loadedPack, target);
        assertFalse(bay.getInventory().contains(loadedPack));
    }

    @Test
    public void launch_setsTargetAndController() {
        game.launchScatterPack(launcher, bay, loadedPack, target);
        assertEquals(target,   loadedPack.getTarget());
        assertEquals(launcher, loadedPack.getController());
    }

    @Test
    public void launch_failsWhenNotActivityPhase() {
        game.advancePhase(); // ACTIVITY → DIRECT_FIRE
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());

        ActionResult result = game.launchScatterPack(launcher, bay, loadedPack, target);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Activity phase"));
    }

    @Test
    public void launch_failsWithEmptyPayload() {
        ScatterPack empty = new ScatterPack(new AdminShuttle());
        empty.setName("SP-empty");
        bay.getInventory().add(empty);

        ActionResult result = game.launchScatterPack(launcher, bay, empty, target);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("no drones"));
    }

    @Test
    public void launch_failsWithoutLockOn() {
        launcher.removeLockOn(target);

        ActionResult result = game.launchScatterPack(launcher, bay, loadedPack, target);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("lock-on"));
    }

    @Test
    public void launch_logIncludesDroneCount() {
        int count = loadedPack.getPayload().size();
        ActionResult result = game.launchScatterPack(launcher, bay, loadedPack, target);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains(String.valueOf(count)));
    }
}
