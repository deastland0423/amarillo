package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;

/**
 * FD5.131: once a dogfight drone has a lock-on of its own, the usual ways of shaking a seeker
 * off stop working on it.
 *
 * <blockquote>"Dogfight drones that have gained their own lock-ons (<b>even if still under
 * control of another unit</b>) cannot be distracted by electronic warfare (D6.3), wild weasels
 * (J3.2), cloaking devices (G13.3345), SWACS (J9.2), or scouts (G24.22) and (G24.23)."
 * </blockquote>
 *
 * <p>FD5.133 is the other side of it, and the reason the lock-on is the test rather than the
 * drone type: those same things "could divert a dogfight drone that did not yet have its own
 * lock-on".
 *
 * <h2>Where the rest of the list lives</h2>
 * EW is covered more broadly by D6.38, which puts "type-VI (dogfight) drones" among the systems
 * electronic warfare does not affect at all, with no lock-on condition — {@code
 * SeekerMover.computeSeekerEcmShift} has answered zero for a warp seeker since long before this.
 * Cloaks are G13.3345, tested in {@code CloakRetentionTest}. Scouts are tested in {@code
 * AttractDronesTest}. SWACS (J9.2) do not exist in this engine yet. This file is the weasel.
 *
 * <p>And FD5.132 is the exception that keeps the thing beatable: "Dogfight drones <b>can</b> be
 * distracted by chaff (D11.0)." That is asserted in {@code ChaffTargetsTest}, because it would
 * be far too easy to make a drone that nothing at all can stop.
 */
public class DogfightDroneNonDistractionTest {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(14, 10));
        klingon.setFacing(13);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            game.submitAllocation(s, e);
        }
    }

    /** A dogfight drone chasing the Federation ship, with or without its own lock-on. */
    private Drone dogfightDroneChasingFed(boolean locked) {
        Drone d = new Drone(DroneType.TypeVI);
        d.setName("DFD-" + (locked ? "locked" : "green"));
        d.setLocation(new Location(13, 10));
        d.setTarget(fed);
        d.setSeekerType(Seeker.SeekerType.DRONE);
        if (locked)
            d.acquireOwnLockOn();
        game.getSeekers().add(d);
        return d;
    }

    /** J3.11: a weasel has to be charged before it is any use (two impulses of deck work). */
    private String chargedShuttleOf(Ship ship) {
        com.sfb.objects.shuttles.Shuttle sh =
                ship.getShuttles().getBays().get(0).launchableInventory().get(0);
        sh.incrementWwCharge();
        sh.incrementWwCharge();
        return sh.getName();
    }

    private void launchWeasel() {
        String name = chargedShuttleOf(fed);
        for (int guard = 0; guard < 400; guard++) {
            if (game.canLaunchThisPhase())
                break;
            game.advancePhase();
        }
        Game.ActionResult r = game.launchWildWeasel(fed, name, 1, 0);
        assertTrue(r.getMessage(), r.isSuccess());
        assertNotNull("fixture: the weasel is out", fed.getActiveWildWeasel());
    }

    // ------------------------------------------------------------------- J3.2, the weasel

    /** FD5.131: a weasel does not draw off a dogfight drone that already has its own lock-on. */
    @Test
    public void aWeaselCannotDrawOffALockedOnDogfightDrone() {
        Drone locked = dogfightDroneChasingFed(true);
        launchWeasel();

        assertEquals("FD5.131: still coming for the ship", fed, locked.getTarget());
    }

    /** FD5.133: one that has not locked on yet is drawn off like anything else (J3.111). */
    @Test
    public void aWeaselStillDrawsOffADogfightDroneThatHasNotLockedOn() {
        Drone green = dogfightDroneChasingFed(false);
        launchWeasel();

        assertEquals("J3.111: diverted to the weasel",
                fed.getActiveWildWeasel(), green.getTarget());
    }

    /** And an ordinary drone is drawn off whatever it has done, since FD5.131 is type-VI only. */
    @Test
    public void aWeaselStillDrawsOffAnOrdinaryDrone() {
        Drone plain = new Drone(DroneType.TypeI);
        plain.setName("Drone-1");
        plain.setLocation(new Location(13, 10));
        plain.setTarget(fed);
        plain.setSeekerType(Seeker.SeekerType.DRONE);
        game.getSeekers().add(plain);

        launchWeasel();

        assertEquals(fed.getActiveWildWeasel(), plain.getTarget());
    }

    // --------------------------------------------------------- and it does not come back

    /**
     * FD5.131: "Type-VIs targeted on a WW will not revert to their original target if the WW
     * is voided or destroyed."
     *
     * <p>A drone that was already chasing the weasel when it won its own lock-on knows the
     * weasel and nothing else. J3.221 leaves the voided shuttle in space — "the shuttle
     * continues to move as per its original instructions (J3.111) until it is destroyed or
     * recovered by any means" — so it keeps after the weasel rather than turning on the ship.
     */
    @Test
    public void aDogfightDroneDoesNotRevertFromAVoidedWeasel() {
        Drone green = dogfightDroneChasingFed(false);
        launchWeasel();
        com.sfb.objects.shuttles.WildWeaselShuttle ww = fed.getActiveWildWeasel();
        assertEquals("fixture: on the weasel", ww, green.getTarget());

        green.acquireOwnLockOn();           // it locks on to the weasel it is chasing
        game.voidWildWeasel(fed);

        assertNotEquals("FD5.131: it does not come back for the ship", fed, green.getTarget());
        assertEquals("J3.221: and the weasel is still there to chase", ww, green.getTarget());
        assertTrue("which is only true because voiding is not destruction (J3.221)",
                game.getActiveShuttles().contains((com.sfb.objects.shuttles.Shuttle) ww));
        assertTrue(game.getSeekers().contains((Seeker) green));
    }

    /** Everything else does revert, which is the J3.13x behaviour this must not disturb. */
    @Test
    public void anOrdinaryDroneStillRevertsFromAVoidedWeasel() {
        Drone plain = new Drone(DroneType.TypeI);
        plain.setName("Drone-2");
        plain.setLocation(new Location(13, 10));
        plain.setTarget(fed);
        plain.setSeekerType(Seeker.SeekerType.DRONE);
        game.getSeekers().add(plain);

        launchWeasel();
        assertEquals(fed.getActiveWildWeasel(), plain.getTarget());

        game.voidWildWeasel(fed);

        assertEquals("J3.13x: back onto the ship", fed, plain.getTarget());
        assertTrue(game.getSeekers().contains((Seeker) plain));
    }
}
