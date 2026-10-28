package com.sfb;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.DroneRack;

/**
 * FD5.12 and FD5.21: eight hexes is where a drone capable of self-guidance stops needing to be
 * told where to go.
 *
 * <blockquote>FD5.12, of the type-VI: "These drones require guidance until they achieve their
 * own lock-on (at a range of eight hexes). At that point, the controlling ship may (but is not
 * required to) release guidance."</blockquote>
 *
 * <blockquote>FD5.21, of ATG: "A drone equipped with active terminal guidance (ATG), after
 * closing to within eight hexes of its assigned target, does not require command guidance from
 * the launching ship... <b>If launched within eight hexes of their target, ATG drones can
 * lock-on immediately.</b>"</blockquote>
 *
 * <p>Tested through {@code game.launchDrone} and the impulse engine rather than on a bare
 * {@code Drone}, because a rule that only holds when you call the object directly is a rule
 * nobody can reach in play.
 */
public class SelfGuidanceAcquisitionTest {

    private Game game;
    private Ship launcher;
    private Ship target;
    private DroneRack rack;

    /** Build a game with the target a given number of hexes up the column from the launcher. */
    private void setUpAt(int targetRow) {
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
        target.setLocation(new Location(10, targetRow));
        target.setFacing(13);

        game.getShips().add(launcher);
        game.getShips().add(target);

        rack = launcher.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(w -> (DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("the FFG should carry a rack"));

        game.startTurn();
        for (Ship s : game.getShips()) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            game.submitAllocation(s, e);
        }
        launcher.addLockOn(target);
        toActivity();
    }

    private void toActivity() {
        for (int guard = 0; guard < 400; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY)
                return;
            game.advancePhase();
        }
        fail("fixture: never reached an Activity phase");
    }

    private void impulse() {
        for (int i = 0; i < 20; i++) {
            boolean wasMovement = game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT;
            game.advancePhase();
            if (wasMovement)
                return;
        }
        fail("fixture: never completed a movement phase");
    }

    /** Load one drone of the given type into the rack and launch it at the target. */
    private Drone launch(DroneType type) {
        Drone d = rack.getAmmo().get(0);
        d.setDroneType(type);
        Game.ActionResult r = game.launchDrone(launcher, target, rack, d, 0);
        assertTrue(r.getMessage(), r.isSuccess());
        return d;
    }

    private int rangeToTarget(Drone d) {
        return com.sfb.utilities.MapUtils.getRange(d.getLocation(), target.getLocation());
    }

    // ------------------------------------------------------------------ launched up close

    /**
     * FD5.21: "If launched within eight hexes of their target, ATG drones can lock-on
     * immediately." Nobody guides it, which is what keeps a fighter with no drone control of
     * its own able to throw a dogfight drone.
     */
    @Test
    public void aCapableDroneLaunchedInsideEightGuidesItselfAtOnce() {
        setUpAt(14);                       // four hexes
        Drone d = launch(DroneType.TypeIII);

        assertEquals("fixture: inside the threshold", 4, rangeToTarget(d));
        assertTrue("FD5.21: lock-on at launch", d.hasOwnLockOn());
        assertNull("so nothing takes hold of it", d.getController());
        assertTrue(d.isSelfGuiding());
        assertEquals("and no control channel is spent", 0, launcher.getControlUsed());
    }

    /** The same drone with no guidance of its own is guided, at close range or any other. */
    @Test
    public void aPlainDroneIsGuidedEvenAtPointBlank() {
        setUpAt(14);
        Drone d = launch(DroneType.TypeI);

        assertFalse(d.hasOwnLockOn());
        assertEquals("the launcher is flying it", launcher, d.getController());
        assertEquals(1, launcher.getControlUsed());
        assertFalse(d.isSelfGuiding());
    }

    // ------------------------------------------------------------------ launched far out

    /**
     * FD5.12: "These drones require guidance until they achieve their own lock-on." Beyond
     * eight hexes a type-III is an ordinary guided drone and costs a channel like one.
     */
    @Test
    public void aCapableDroneLaunchedBeyondEightIsGuidedUntilItCloses() {
        setUpAt(25);                       // fifteen hexes
        Drone d = launch(DroneType.TypeIII);

        assertTrue("fixture: outside the threshold", rangeToTarget(d) > 8);
        assertFalse("it has nothing of its own to steer by yet", d.hasOwnLockOn());
        assertEquals("so the launcher guides it (FD5.12)", launcher, d.getController());
        assertFalse(d.isSelfGuiding());
    }

    /**
     * And it takes over on the impulse it closes to eight, under its own power, with the
     * controlling ship still holding it — FD5.12 says releasing is the player's option, not
     * something that happens automatically.
     */
    @Test
    public void itTakesOverOnTheImpulseItClosesToEight() {
        setUpAt(25);
        Drone d = launch(DroneType.TypeIII);
        assertFalse(d.hasOwnLockOn());

        int guard = 0;
        while (!d.hasOwnLockOn() && guard++ < 200 && game.getSeekers().contains((Seeker) d))
            impulse();

        assertTrue("it should have closed to eight by now", d.hasOwnLockOn());
        assertTrue("and did so at eight hexes or nearer (FD5.12)", rangeToTarget(d) <= 8);
        assertEquals("the launcher is still guiding it until it chooses to let go",
                launcher, d.getController());
        assertFalse(d.isSelfGuiding());

        launcher.releaseControl(d);
        assertTrue("released at that point, it carries on (FD5.12)", d.isSelfGuiding());
    }

    /** A drone with no guidance of its own never acquires, however close it gets. */
    @Test
    public void aPlainDroneNeverTakesOver() {
        setUpAt(25);
        Drone d = launch(DroneType.TypeI);

        int guard = 0;
        while (rangeToTarget(d) > 4 && guard++ < 200 && game.getSeekers().contains((Seeker) d))
            impulse();

        assertTrue("fixture: it did close", rangeToTarget(d) <= 8);
        assertFalse("but a type-I has nothing to take over with", d.hasOwnLockOn());
        assertFalse(d.isSelfGuiding());
    }
}
