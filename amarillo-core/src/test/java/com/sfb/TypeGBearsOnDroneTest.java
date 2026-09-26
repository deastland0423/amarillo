package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * A loaded type-G must appear among the weapons that bear on a drone.
 * <p>
 * Found in a playtest: the rack held four anti-drone rounds, read "ready" in the ship panel,
 * and a drone sat three hexes away — and the Fire Orders panel offered ten weapons, none of
 * them the rack. Everything downstream was right; the rack never reached the list.
 * <p>
 * Two reasons, both in {@link com.sfb.systemgroups.Weapons#fetchAllBearingWeapons}:
 * <ul>
 * <li>It tests {@code range <= weapon.getMaxRange()}, and a drone rack never set one. Zero
 *     is right for a rack that only launches — it can do damage at no range at all — but a
 *     type-G reaches as far as the anti-drone it fires (E5.0).</li>
 * <li>It tested {@code weapon.canFire()}, which asks a rack whether it may LAUNCH. For a
 *     type-G committed to anti-drone mode that is false for the rest of the turn (FD3.71),
 *     so the rack would have vanished from the list after its first shot.</li>
 * </ul>
 */
public class TypeGBearsOnDroneTest {

    private Game game;
    private Ship fed;
    private Drone target;

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedOcl());     // carries a type-G
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        game.getShips().add(fed);
        // Game injects its clock at startTurn; this fixture adds the ship straight to
        // the list, so the rack would otherwise tick on a private clock of its own.
        fed.attachClock(game.getClock());

        rack().setAmmo(new java.util.ArrayList<>());
        rack().loadAntiDrones(4, 175);

        target = new Drone(DroneType.TypeI);
        target.setName("IKV Ambush-Drone-1");
        target.setLocation(new Location(10, 7));   // three hexes dead ahead
        target.setFacing(13);
        game.getSeekers().add(target);
    }

    private DroneRack rack() {
        for (Weapon w : fed.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack)
                return (DroneRack) w;
        throw new AssertionError("fixture needs a drone rack");
    }

    private boolean rackBearsOnTarget() {
        for (Weapon w : fed.fetchAllBearingWeapons(target))
            if (w instanceof DroneRack)
                return true;
        return false;
    }

    // ---------------------------------------------------------------- reach

    @Test
    public void aLoadedTypeGBearsOnADroneThreeHexesAway() {
        assertEquals("premise: the drone is at range 3", 3,
                com.sfb.utilities.MapUtils.getRange(fed, target));
        assertEquals("premise: the rack has rounds", 4, rack().getAddAmmo());

        assertTrue("a type-G with anti-drones must be offered against a drone",
                rackBearsOnTarget());
    }

    @Test
    public void theTypeGReachesExactlyAsFarAsAnAntiDrone() {
        assertEquals(ADD.maxRange(), rack().getMaxRange());

        target.setLocation(new Location(10, 6));   // range 4
        assertFalse("past the anti-drone's band, it bears on nothing",
                rackBearsOnTarget());
    }

    /** A rack that only launches still reaches nothing — that is what keeps it out. */
    @Test
    public void anOrdinaryRackHasNoReachAtAll() {
        DroneRack typeA = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        assertEquals(0, typeA.getMaxRange());
    }

    @Test
    public void anEmptyTypeGBearsOnNothing() {
        rack().setAddAmmo(0);

        assertFalse("no rounds, nothing to offer", rackBearsOnTarget());
    }

    // ---------------------------------------------------------------- reach is for FIRING

    /**
     * The reach given to a type-G is the anti-drone's, and it must not touch launching.
     *
     * A drone flies itself for dozens of hexes; the rack that threw it has no say in how far.
     * Nothing on the launch path reads getMaxRange today — every caller is a direct-fire one
     * — and this test is here so that stays true, because the coupling would be invisible:
     * launches would simply stop being offered past three hexes.
     */
    @Test
    public void theRangeBandDoesNotLimitWhatTheRackCanLaunchAt() {
        Ship distant = new Ship();
        distant.init(com.sfb.samples.KlingonShips.getD7());
        distant.setName("IKV Ambush");
        distant.setLocation(new Location(10, 24));      // fourteen hexes away
        distant.setFacing(13);
        game.getShips().add(distant);
        distant.attachClock(game.getClock());

        assertTrue("premise: far outside the anti-drone band",
                com.sfb.utilities.MapUtils.getRange(fed, distant) > ADD.maxRange());

        rack().getAmmo().add(new Drone(DroneType.TypeI));
        fed.setActiveFireControl(true);
        fed.addLockOn(distant);
        game.advancePhase();                            // MOVEMENT -> ACTIVITY

        // Facing 0: point it at the target, since the seeker's own forward arc must hold
        // the thing it is chasing. That rule is not this test's subject — range is.
        Game.ActionResult r = game.launchDrone(
                fed, distant, rack(), rack().getAmmo().get(0), 0);

        assertTrue("a rack launches as far as the drone flies, not as far as it shoots: "
                + r.getMessage(), r.isSuccess());
    }

    // ---------------------------------------------------------------- readiness

    /**
     * The second bug, which would have bitten one shot later. Firing an anti-drone commits
     * the rack to that mode, so canFire() — "may I launch a drone?" — is false for the rest
     * of the turn. The rack must still be offered on the next impulse.
     */
    @Test
    public void itIsStillOfferedAfterItHasAlreadyFiredThisTurn() {
        game.getClock().nextImpulse();
        assertTrue(rack().recordAntiDroneFire());

        assertFalse("premise: it may not launch a drone now", rack().canFire());

        game.getClock().nextImpulse();
        assertTrue("but it may fire another anti-drone, so it still bears",
                rackBearsOnTarget());
    }

    /** And a rack that launched a drone this turn is correctly out for the rest of it. */
    @Test
    public void aRackThatLaunchedADroneBearsOnNothing() {
        rack().recordLaunch();

        assertFalse("FD3.71: drone mode for the turn", rackBearsOnTarget());
    }
}
