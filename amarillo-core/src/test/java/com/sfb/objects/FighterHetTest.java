package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.TurnTracker;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;

/**
 * Tests for fighter Tactical Maneuver (HET) per C6.42:
 *   - Only fighters may HET; non-fighter shuttles cannot.
 *   - One HET per turn maximum.
 *   - Crippled fighters cannot HET (J1.336).
 *   - No energy cost, no breakdown roll.
 *   - Flag resets on startTurn().
 *
 * Also covers Game.performFighterHet() integration path.
 */
public class FighterHetTest {

    private Game game;
    private Stinger1 fighter;
    private AdminShuttle plainShuttle;

    @Before
    public void setUp() {
        TurnTracker.reset();
        TurnTracker.nextImpulse(); // → impulse 1

        game = new Game();

        // Add a ship so the game has valid state
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("Carrier");
        ship.setLocation(new Location(5, 5));
        game.getShips().add(ship);

        fighter = new Stinger1();
        fighter.setName("Stinger-1");
        fighter.setLocation(new Location(10, 10));
        fighter.setFacing(1);
        game.getActiveShuttles().add(fighter);

        plainShuttle = new AdminShuttle();
        plainShuttle.setName("Admin-1");
        plainShuttle.setLocation(new Location(11, 10));
        plainShuttle.setFacing(1);
        game.getActiveShuttles().add(plainShuttle);
    }

    // -------------------------------------------------------------------------
    // Fighter.performTacticalManeuver() unit tests
    // -------------------------------------------------------------------------

    @Test
    public void tacticalManeuver_changesFacing() {
        fighter.setFacing(1);
        fighter.performTacticalManeuver(9);
        assertEquals(9, fighter.getFacing());
    }

    @Test
    public void tacticalManeuver_returnsTrueOnFirstUse() {
        assertTrue(fighter.performTacticalManeuver(5));
    }

    @Test
    public void tacticalManeuver_returnsFalseOnSecondUse() {
        fighter.performTacticalManeuver(5);
        assertFalse(fighter.performTacticalManeuver(9));
    }

    @Test
    public void tacticalManeuver_facingUnchangedOnSecondUse() {
        fighter.performTacticalManeuver(5);
        fighter.performTacticalManeuver(9); // should be ignored
        assertEquals(5, fighter.getFacing());
    }

    @Test
    public void tacticalManeuver_flagSetAfterUse() {
        assertFalse(fighter.isTacticalManeuverUsed());
        fighter.performTacticalManeuver(5);
        assertTrue(fighter.isTacticalManeuverUsed());
    }

    @Test
    public void tacticalManeuver_flagResetByStartTurn() {
        fighter.performTacticalManeuver(5);
        assertTrue(fighter.isTacticalManeuverUsed());
        fighter.startTurn();
        assertFalse(fighter.isTacticalManeuverUsed());
    }

    // -------------------------------------------------------------------------
    // Game.performFighterHet() — phase guard
    // -------------------------------------------------------------------------

    @Test
    public void game_fighterHet_failsWhenNotMovementPhase() {
        game.advancePhase(); // MOVEMENT → ACTIVITY
        ActionResult result = game.performFighterHet(fighter, 5);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Movement phase"));
    }

    // -------------------------------------------------------------------------
    // Game.performFighterHet() — non-fighter guard
    // -------------------------------------------------------------------------

    @Test
    public void game_fighterHet_failsForNonFighterShuttle() {
        ActionResult result = game.performFighterHet(plainShuttle, 5);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Only fighters"));
    }

    // -------------------------------------------------------------------------
    // Game.performFighterHet() — crippled guard (J1.336)
    // -------------------------------------------------------------------------

    @Test
    public void game_fighterHet_failsWhenCrippled() {
        fighter.applyCripplingEffects(); // sets crippled=true internally
        ActionResult result = game.performFighterHet(fighter, 5);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Crippled"));
    }

    // -------------------------------------------------------------------------
    // Game.performFighterHet() — once per turn (C6.42)
    // -------------------------------------------------------------------------

    @Test
    public void game_fighterHet_failsOnSecondAttemptSameTurn() {
        game.performFighterHet(fighter, 5);
        ActionResult second = game.performFighterHet(fighter, 9);
        assertFalse(second.isSuccess());
        assertTrue(second.getMessage().contains("already used"));
    }

    // -------------------------------------------------------------------------
    // Game.performFighterHet() — success path
    // -------------------------------------------------------------------------

    @Test
    public void game_fighterHet_success_changesFacing() {
        fighter.setFacing(1);
        ActionResult result = game.performFighterHet(fighter, 13);
        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals(13, fighter.getFacing());
    }

    @Test
    public void game_fighterHet_success_marksTacticalManeuverUsed() {
        game.performFighterHet(fighter, 5);
        assertTrue(fighter.isTacticalManeuverUsed());
    }

    @Test
    public void game_fighterHet_success_noReserveWarpCost() {
        // Fighters have no reserve warp — verify no exception and no cost applied
        ActionResult result = game.performFighterHet(fighter, 5);
        assertTrue(result.getMessage(), result.isSuccess());
    }
}
