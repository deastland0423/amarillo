package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.DroneRack;

/**
 * FD2.2: "Endurance = number of turns the drone remains in play."
 *
 * <p>TURNS IN PLAY, not hexes travelled. A drone knows the impulse it was launched and goes
 * inert at the end of its 96th impulse wherever it happens to be.
 *
 * <h2>Why the distinction is not academic</h2>
 * A drone does not only move under its own power. One held in a tractor beam (G7.5) is skipped
 * by the movement loop entirely but is still in play and still burning fuel, and the terrain
 * that will later push units around the board — black holes, nebulae — says the same. Tying
 * endurance to distance covered would make a drone immortal exactly when something else is
 * doing the moving.
 *
 * <p>It was tied to distance: endurance was spent once per hex, inside the block that runs only
 * on the impulses a drone actually moves. That made it a RANGE, and a speed-dependent one — the
 * error scaled with 32/speed, so it was exact for anything moving 32 and four times too
 * generous for a speed-8 type-I, which lasted twelve turns instead of three.
 */
public class DroneEnduranceTest {

    private Game game;
    private Ship launcher;
    private Ship target;
    private DroneRack rack;

    @Before
    public void setUp() {
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
        assertNotNull("the FFG should carry a drone rack", rack);

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

    /** Run one whole impulse, which moves the seekers once. */
    private void impulse() {
        for (int i = 0; i < 20; i++) {
            boolean wasMovement = game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT;
            game.advancePhase();
            if (wasMovement)
                return;
        }
        fail("fixture: never completed a movement phase");
    }

    private Drone launchSlow() {
        Drone d = rack.getAmmo().get(0);
        assertTrue(game.launchDrone(launcher, target, rack, d, 0).isSuccess());
        d.setSpeed(8);      // one hex every four impulses, so the two clocks diverge
        return d;
    }

    /**
     * The discriminating case: a drone slower than one hex per impulse.
     *
     * <p>Over eight impulses a speed-8 drone covers two hexes. Endurance must fall by eight,
     * not by two — otherwise "96 impulses" silently means "96 hexes".
     */
    @Test
    public void enduranceIsSpentPerImpulseNotPerHex() {
        Drone d = launchSlow();
        int rated = d.getEndurance();
        Location start = d.getLocation();

        int impulses = 8;
        for (int i = 0; i < impulses && game.getSeekers().contains((Seeker) d); i++)
            impulse();

        int spent = rated - d.getEndurance();
        assertEquals("FD2.2: one impulse in play costs one impulse of endurance",
                impulses, spent);

        assertNotEquals("fixture: the drone did move, so this is not vacuous",
                start, d.getLocation());
        assertTrue("fixture: but far fewer hexes than impulses, which is the whole point",
                hexesApart(start, d.getLocation()) < impulses);
    }

    /** A type-I is a three-turn drone (FD2.1's chart), whatever speed it is flying. */
    @Test
    public void aTypeIIsRatedForThreeTurnsOfImpulses() {
        assertEquals(3 * com.sfb.constants.Constants.IMPULSES_PER_TURN,
                DroneType.TypeI.endurance);
        assertEquals(1 * com.sfb.constants.Constants.IMPULSES_PER_TURN,
                DroneType.TypeVI.endurance);
    }

    /**
     * Building a drone through the type SETTER gives the same drone as building it through the
     * constructor. It did not: the setter assigned the type and left endurance, speed, warhead
     * and hull at zero, which only showed once a zero endurance meant "expire immediately".
     */
    @Test
    public void settingTheTypeDerivesEverythingTheTypeDecides() {
        Drone viaSetter = new Drone();
        viaSetter.setDroneType(DroneType.TypeI);
        Drone viaConstructor = new Drone(DroneType.TypeI);

        assertEquals(viaConstructor.getEndurance(), viaSetter.getEndurance());
        assertEquals(viaConstructor.getSpeed(), viaSetter.getSpeed());
        assertEquals(viaConstructor.impact(), viaSetter.impact());
        assertEquals(viaConstructor.getHull(), viaSetter.getHull());
        assertEquals(viaConstructor.getRackSize(), viaSetter.getRackSize(), 0.0001);

        assertTrue("and emphatically not zero", viaSetter.getEndurance() > 0);
    }

    private int hexesApart(Location a, Location b) {
        return com.sfb.utilities.MapUtils.getRange(a, b);
    }
}
