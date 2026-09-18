package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Lock-on to a shuttle that appears mid-turn, and the log that says what happened.
 * <p>
 * Two paths acquire lock-on and they used to disagree. The turn-start sweep
 * ({@code performLockOnRolls}) gives every ship of the owner AUTOMATIC lock-on to an
 * own-side shuttle. The mid-turn launch path gave it only to the launcher and made every
 * other ship roll, friendly ones included - so a second ship of your own fleet could fail
 * to see your shuttle, and then could not tractor it (G7.412 needs lock-on). Whether that
 * happened depended on nothing more principled than which side of the turn boundary the
 * shuttle launched on.
 * <p>
 * The launch path also had its own copy of the roll that logged successes only, so a
 * failure was completely silent and the player could not tell a failed roll from no roll.
 */
public class LaunchedShuttleLockOnTest {

    private Game game;
    private Ship launcher;
    private Ship consort;
    private Player klingon;

    @Before
    public void setUp() {
        game = new Game();
        klingon = new Player();
        klingon.setTeamName("Klingon");

        launcher = ship("IKS Fury", 10, 10, klingon);
        consort = ship("IKS Barbarous", 11, 10, klingon);
        game.startTurn();
    }

    private Ship ship(String name, int col, int row, Player owner) {
        Ship s = new Ship();
        s.init(KlingonShips.getD7());
        s.setName(name);
        s.setLocation(new Location(col, row));
        s.setFacing(1);
        s.setOwner(owner);
        s.setSpeedPreviousTurn(31);
        s.setSpeedTwoTurnsAgo(31);
        s.setActiveFireControl(true);
        game.getShips().add(s);
        return s;
    }

    /**
     * Walk a sensor track down to 0, where no die can ever satisfy {@code roll <= rating}.
     * That makes these tests discriminating: with sensors at their usual 6 acquisition is
     * automatic for everyone, so an own-side rule would look like it worked even if it were
     * deleted. Both lock-on paths agree own-side shuttles bypass the roll entirely, so a
     * blinded consort must STILL have lock-on to its own side's shuttle.
     */
    private void blindSensors(Ship s) {
        for (int guard = 0; guard < 20 && s.getSpecialFunctions().getSensor() > 0; guard++)
            s.getSpecialFunctions().damageSensor();
        assertEquals("sensor track should be down to 0", 0, s.getSpecialFunctions().getSensor());
    }

    private AdminShuttle launchedShuttle() {
        AdminShuttle sh = new AdminShuttle();
        sh.setName("IKS Fury-Shuttle-1");
        sh.setOwner(klingon);
        sh.setLocation(new Location(10, 10));
        sh.setFacing(1);
        sh.setSpeed(6);
        game.getActiveShuttles().add(sh);
        return sh;
    }

    /**
     * The bug as the user hit it: a shuttle launched by one ship of a fleet, and the ship
     * beside it cannot tractor it because it never acquired lock-on. Own side, in formation,
     * one hex apart.
     */
    @Test
    public void aConsortHasLockOnToItsOwnSidesFreshlyLaunchedShuttle() {
        blindSensors(consort);   // only the own-side rule can succeed now
        AdminShuttle shuttle = launchedShuttle();

        game.checkLockOnsForNewUnit(launcher, shuttle);

        assertTrue("the launcher obviously has it", launcher.hasLockOn(shuttle));
        assertTrue("a ship of the same side must have lock-on to its own shuttle, exactly as "
                        + "the turn-start sweep would have given it",
                consort.hasLockOn(shuttle));
    }

    /**
     * The same shuttle sitting there at turn start — the behaviour the launch path now
     * matches. The sweep runs in beginImpulses(), not startTurn(), so every ship has to
     * allocate before it fires.
     */
    @Test
    public void theTurnStartSweepAgrees() {
        blindSensors(consort);   // only the own-side rule can succeed now
        AdminShuttle shuttle = launchedShuttle();

        allocateAll();      // beginImpulses() runs the sweep once everyone is in

        assertTrue("sweep: own-side shuttle is automatic", consort.hasLockOn(shuttle));
        assertTrue("sweep: launcher too", launcher.hasLockOn(shuttle));
    }

    /** Submit a minimal allocation for every ship, which is what triggers beginImpulses(). */
    private void allocateAll() {
        for (Ship s : new java.util.ArrayList<>(game.getShips())) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            e.setWarpMovement(0.0);
            game.submitAllocation(s, e);
        }
    }

    /**
     * An enemy still has to roll for it — and now says so either way. Sensor ratings vary, so
     * this asserts the log speaks rather than which way the die fell.
     */
    @Test
    public void anEnemyRollsAndTheLogSaysWhatHappened() {
        Player fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");
        Ship fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(12, 10));
        fed.setFacing(1);
        fed.setOwner(fedPlayer);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        fed.setActiveFireControl(true);
        // Track is {6,6,5,3,1,0}; at 6 acquisition is automatic and logs nothing, so walk
        // it down to 5 to force an actual roll with an actual outcome to report.
        fed.getSpecialFunctions().damageSensor();
        fed.getSpecialFunctions().damageSensor();
        game.getShips().add(fed);

        AdminShuttle shuttle = launchedShuttle();
        List<String> log = game.checkLockOnsForNewUnit(launcher, shuttle);

        String all = String.join("\n", log);
        assertTrue("an enemy's attempt must be reported whichever way it goes — a silent "
                        + "failure is what left the player guessing. Log:\n" + all,
                all.contains("USS Enterprise acquired lock-on")
                        || all.contains("USS Enterprise failed lock-on"));
        assertEquals("the log must agree with the outcome",
                all.contains("USS Enterprise acquired lock-on"), fed.hasLockOn(shuttle));
    }

    /** No fire control, no lock-on (D6.1143) — and the sweep says the same. */
    @Test
    public void aConsortWithoutFireControlGetsNothing() {
        consort.setActiveFireControl(false);
        AdminShuttle shuttle = launchedShuttle();

        game.checkLockOnsForNewUnit(launcher, shuttle);

        assertFalse("own side or not, fire control gates lock-on (D6.1143)",
                consort.hasLockOn(shuttle));
    }
}
