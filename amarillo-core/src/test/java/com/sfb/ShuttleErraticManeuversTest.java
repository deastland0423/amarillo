package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Erratic Maneuvers for shuttles and fighters (C10.13 / C10.131).
 * <p>
 * A shuttle buys EM with one movement point rather than with energy, and a movement point
 * is a point of speed. C10.131 makes that a commitment for the whole turn: "the shuttle
 * cannot cancel this written commitment and accelerate to its full speed during the turn…
 * This does not mean that the shuttle cannot turn its EM off (or on) during a turn, only
 * that it cannot regain the point of speed dedicated to Erratic Maneuvers during that
 * turn." So the speed is spent even while EM is switched off, which is why the commitment
 * is state of its own rather than a function of {@code isUsingEm()}.
 * <p>
 * The benefits need no shuttle-specific code: EM state lives on Unit, so the four points
 * of ECM (C10.41) already apply, and C11.1 makes every shuttle nimble, which exempts it
 * from the Turn Mode and HET penalties of C10.55.
 */
public class ShuttleErraticManeuversTest {

    private Game game;
    private Ship carrier;
    private Player fedPlayer;

    @Before
    public void setUp() {
        game = new Game();
        fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");

        carrier = new Ship();
        carrier.init(FederationShips.getFedCa());
        carrier.setName("USS Enterprise");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        carrier.setOwner(fedPlayer);
        carrier.setSpeedPreviousTurn(31);
        carrier.setSpeedTwoTurnsAgo(31);
        carrier.setActiveFireControl(true);
        game.getShips().add(carrier);

        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(carrier.getLifeSupportCost());
        e.setFireControl(carrier.getFireControlCost());
        e.setActivateShields(carrier.getActiveShieldCost());
        e.setWarpMovement(0.0);
        game.submitAllocation(carrier, e);
    }

    private <T extends Shuttle> T onMap(T s, String name, int speed) {
        s.setName(name);
        s.setOwner(fedPlayer);
        s.setLocation(new Location(10, 10));
        s.setFacing(1);
        s.setSpeed(speed);
        s.setCurrentSpeed(speed);
        game.getActiveShuttles().add(s);
        return s;
    }

    // ---------------------------------------------------------------- the cost (C10.13)

    @Test
    public void committingCostsExactlyOnePointOfSpeed() {
        Stinger1 f = onMap(new Stinger1(), "Alpha 1", 12);
        int rated = f.getMaxSpeed();

        assertTrue(game.commitShuttleEmSpeed(f).isSuccess());

        assertEquals("C10.13: one movement point, which for a shuttle is one of speed",
                rated - 1, f.effectiveMaxSpeed());
        assertEquals("its rating is untouched — it is the usable maximum that drops",
                rated, f.getMaxSpeed());
    }

    @Test
    public void aShuttleAlreadyAboveTheNewMaximumIsSlowedAtOnce() {
        Stinger1 f = onMap(new Stinger1(), "Alpha 1", 12);
        assertEquals(12, f.getCurrentSpeed());

        game.commitShuttleEmSpeed(f);

        assertEquals("it cannot keep flying at a speed it no longer has the points for",
                f.effectiveMaxSpeed(), f.getCurrentSpeed());
    }

    // ---------------------------------------------------------------- C10.131 commitment

    @Test
    public void switchingEmOffDoesNotGiveTheSpeedBack() {
        Stinger1 f = onMap(new Stinger1(), "Alpha 1", 12);
        int rated = f.getMaxSpeed();
        game.commitShuttleEmSpeed(f);

        assertTrue(game.announceErraticManeuvers(f, true).isSuccess());
        for (int guard = 0; guard < 40 && !f.isUsingEm(); guard++)
            game.advancePhase();
        assertTrue("EM in force", f.isUsingEm());

        assertTrue(game.announceErraticManeuvers(f, false).isSuccess());
        for (int guard = 0; guard < 40 && f.isUsingEm(); guard++)
            game.advancePhase();
        assertFalse("EM switched off", f.isUsingEm());

        assertEquals("C10.131: the point of speed is spent for the turn regardless — it "
                + "cannot be regained by cancelling EM", rated - 1, f.effectiveMaxSpeed());
    }

    @Test
    public void emCannotBeStartedWithoutCommittingTheSpeedFirst() {
        Stinger1 f = onMap(new Stinger1(), "Alpha 1", 12);

        Game.ActionResult r = game.announceErraticManeuvers(f, true);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("C10.13"));
    }

    // ---------------------------------------------------------------- who may not (C10.132/133)

    @Test
    public void aShuttleOnASeekingCourseCannotUseEm() {
        SuicideShuttle ss = onMap(new SuicideShuttle(new AdminShuttle()), "Kamikaze", 6);

        Game.ActionResult r = game.commitShuttleEmSpeed(ss);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("C10.132"));
    }

    @Test
    public void aWildWeaselCannotUseEm() {
        WildWeaselShuttle ww = onMap(new WildWeaselShuttle(carrier), "Decoy", 4);

        Game.ActionResult r = game.commitShuttleEmSpeed(ww);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("C10.133"));
    }

    // ---------------------------------------------------------------- the benefits carry over

    @Test
    public void aShuttleUnderEmGetsTheSameFourPointsOfEcm() {
        Stinger1 f = onMap(new Stinger1(), "Alpha 1", 12);
        game.commitShuttleEmSpeed(f);
        assertTrue(game.announceErraticManeuvers(f, true).isSuccess());
        for (int guard = 0; guard < 40 && !f.isUsingEm(); guard++)
            game.advancePhase();

        // C10.41 is written about units, and the state lives on Unit, so this needed no
        // shuttle-specific code at all. The fighter's own two built-in points are ignored
        // here because the carrier is on its side (D6.3146).
        assertEquals("four points of natural ECM, as for a ship",
                4, game.ewAgainst(carrier, f).natural());
    }

    @Test
    public void aShuttleIsNimbleSoItKeepsItsTurnMode() {
        Stinger1 f = onMap(new Stinger1(), "Alpha 1", 12);
        game.commitShuttleEmSpeed(f);
        // Measured AFTER the commitment: Turn Mode is a function of speed, and giving up a
        // point of speed can cross a band in the table on its own. What is under test is
        // whether EM adds a hex on top of that, which it must not for a nimble unit.
        int before = f.getTurnHexes();

        assertTrue(game.announceErraticManeuvers(f, true).isSuccess());
        for (int guard = 0; guard < 40 && !f.isUsingEm(); guard++)
            game.advancePhase();

        assertEquals("C10.55 exempts nimble units, and C11.1 makes every shuttle nimble",
                before, f.getTurnHexes());
    }

    @Test
    public void aFighterCannotHetWhileUnderEm() {
        Stinger1 f = onMap(new Stinger1(), "Alpha 1", 12);
        game.commitShuttleEmSpeed(f);
        assertTrue(game.announceErraticManeuvers(f, true).isSuccess());
        for (int guard = 0; guard < 40 && !f.isUsingEm(); guard++)
            game.advancePhase();
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();

        Game.ActionResult r = game.performFighterHet(f, 9);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("C10.135"));
    }
}
