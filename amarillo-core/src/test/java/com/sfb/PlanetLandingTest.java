package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.properties.LandingPhase;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * P2.4 planet landing — Increment 1: entering a planet hex. A shuttle moving
 * into a planet hex at speed ≤ 1 enters the atmosphere and begins its descent,
 * designated on the hex side it came from (P2.611); at speed &gt; 1 it crashes
 * (P2.812). Descent-to-landed and take-off are later increments.
 */
public class PlanetLandingTest {

    private Game game;
    private Ship fed;
    private Player fedPlayer;

    @Before
    public void setUp() {
        game = new Game();
        fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(20, 20)); // parked far from the planet
        fed.setFacing(1);
        fed.setOwner(fedPlayer);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed);

        // Single-hex Class-M planet at (10,7)
        game.addTerrain(new Terrain(TerrainType.PLANET, 10, 7));

        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(fed.getLifeSupportCost());
        e.setFireControl(fed.getFireControlCost());
        e.setActivateShields(fed.getActiveShieldCost());
        e.setWarpMovement(0.0); // fed stays put — no movable ship blocks the shuttle's impulse
        game.submitAllocation(fed, e);
    }

    private AdminShuttle shuttleAt(int col, int row, int facing, int speed) {
        AdminShuttle s = new AdminShuttle();
        s.setName("Galileo");
        s.setOwner(fedPlayer);
        s.setLocation(new Location(col, row));
        s.setFacing(facing);
        s.setSpeed(speed);
        game.getActiveShuttles().add(s);
        return s;
    }

    private void advanceToMovement(int minImpulse) {
        for (int guard = 0; guard < 400; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT
                    && game.getCurrentImpulse() >= minImpulse)
                return;
            game.advancePhase();
        }
        fail("Never reached MOVEMENT at impulse >= " + minImpulse);
    }

    private void advanceToActivity(int minImpulse) {
        for (int guard = 0; guard < 400; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY
                    && game.getCurrentImpulse() >= minImpulse)
                return;
            game.advancePhase();
        }
        fail("Never reached ACTIVITY at impulse >= " + minImpulse);
    }

    @Test
    public void shuttleEntersPlanetAtSpeed1_beginsDescentOnEntrySide() {
        // Just south of the planet at (10,8), heading north into (10,7), speed 1.
        AdminShuttle shuttle = shuttleAt(10, 8, 1, 1);
        advanceToMovement(32); // speed-1 units move on impulse 32

        Game.ActionResult r = game.moveShuttleForward(shuttle);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(10, 7), shuttle.getLocation());
        assertEquals(LandingPhase.DESCENDING, shuttle.getLandingPhase());
        assertEquals("came from the south → designated on the D (south) face",
                4, shuttle.getLandedHexSide());
        assertTrue(r.getMessage(), r.getMessage().contains("atmosphere"));
        assertTrue("stays in play, now in the atmosphere",
                game.getActiveShuttles().contains(shuttle));
    }

    @Test
    public void inAtmosphereShuttleLandsTheTurnAfterEntry() {
        AdminShuttle shuttle = shuttleAt(10, 7, 1, 0);
        shuttle.setLandingPhase(LandingPhase.DESCENDING);
        shuttle.setLandedHexSide(4); // D
        shuttle.setAtmosphereEnteredTurn(game.getCurrentTurn() - 1); // entered last turn

        java.util.List<String> log = game.landDescendingShuttles();

        assertEquals(LandingPhase.LANDED, shuttle.getLandingPhase());
        assertTrue(log.toString(), log.stream().anyMatch(l -> l.contains("landed")));
    }

    @Test
    public void inAtmosphereShuttleDoesNotLandOnItsEntryTurn() {
        AdminShuttle shuttle = shuttleAt(10, 7, 1, 0);
        shuttle.setLandingPhase(LandingPhase.DESCENDING);
        shuttle.setAtmosphereEnteredTurn(game.getCurrentTurn()); // entered this same turn

        game.landDescendingShuttles();

        assertEquals("Step 3 descent is a separate turn — no same-turn landing",
                LandingPhase.DESCENDING, shuttle.getLandingPhase());
    }

    @Test
    public void landedShuttleCannotMove() {
        // P2.45: a landed unit cannot expend power for movement (except take-off).
        AdminShuttle shuttle = shuttleAt(12, 12, 1, 1);
        shuttle.setLandingPhase(LandingPhase.LANDED);
        advanceToMovement(32);

        Game.ActionResult r = game.moveShuttleForward(shuttle);

        assertFalse("a landed shuttle cannot fly (P2.45)", r.isSuccess());
    }

    // ---- Take-off (P2.412) --------------------------------------------------

    @Test
    public void declareTakeoff_liftsLandedShuttleIntoClimb() {
        AdminShuttle shuttle = shuttleAt(10, 7, 1, 0);
        shuttle.setLandingPhase(LandingPhase.LANDED);
        shuttle.setLandedHexSide(4);
        advanceToActivity(1);

        Game.ActionResult r = game.declareTakeoff(shuttle);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(LandingPhase.CLIMBING, shuttle.getLandingPhase());
        assertEquals(game.getCurrentTurn(), shuttle.getTakeoffTurn());
    }

    @Test
    public void declareTakeoff_failsWhenNotLanded() {
        AdminShuttle shuttle = shuttleAt(12, 12, 1, 1); // in open space
        advanceToActivity(1);

        Game.ActionResult r = game.declareTakeoff(shuttle);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("not landed"));
    }

    @Test
    public void climbingShuttleCannotLeaveOnTheTakeoffTurn() {
        AdminShuttle shuttle = shuttleAt(10, 7, 1, 1); // in the planet hex, facing out
        shuttle.setLandingPhase(LandingPhase.CLIMBING);
        shuttle.setTakeoffTurn(game.getCurrentTurn()); // lifted off this same turn
        advanceToMovement(32);

        Game.ActionResult r = game.moveShuttleForward(shuttle);

        assertFalse("cannot lift off and leave the same turn (P2.412 is multi-turn)", r.isSuccess());
        assertEquals(new Location(10, 7), shuttle.getLocation());
    }

    @Test
    public void climbingShuttleDepartsToSpaceTheTurnAfterTakeoff() {
        AdminShuttle shuttle = shuttleAt(10, 7, 1, 1); // in the planet hex, facing north (out)
        shuttle.setLandingPhase(LandingPhase.CLIMBING);
        shuttle.setTakeoffTurn(game.getCurrentTurn() - 1); // lifted off last turn
        advanceToMovement(32);

        Game.ActionResult r = game.moveShuttleForward(shuttle);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(10, 6), shuttle.getLocation());
        assertEquals(LandingPhase.NONE, shuttle.getLandingPhase());
        assertTrue(r.getMessage(), r.getMessage().contains("climbed"));
    }

    @Test
    public void shuttleEntersPlanetAtSpeedTwo_crashes() {
        AdminShuttle shuttle = shuttleAt(10, 8, 1, 2);
        advanceToMovement(16); // speed-2 units move on impulse 16

        Game.ActionResult r = game.moveShuttleForward(shuttle);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("crash"));
        assertFalse("crashed shuttle is removed from play",
                game.getActiveShuttles().contains(shuttle));
        assertEquals(LandingPhase.NONE, shuttle.getLandingPhase());
    }
}
