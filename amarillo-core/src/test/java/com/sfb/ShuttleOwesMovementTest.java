package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * A shuttle that still owes a move this impulse, as the PHASE GATE must see it.
 *
 * <h2>The bug this pins, found in a playtest</h2>
 * A Federation shuttle launched at speed 6 moved three times in a turn instead of six, and
 * skipped impulse 32 — while the Kzinti shuttle in the same battle behaved perfectly. The
 * difference was who pressed Ready first.
 *
 * <p>{@code getMovableShuttles()} answers "may this shuttle move RIGHT NOW", and correctly
 * returns nothing while any ship still owes a move: C1.313's order of precedence puts ships (2)
 * and nimble ships (3) ahead of fighters and shuttles (4). The phase gate asked its question
 * through that list, so whenever the OPPONENT'S ship had not moved, the gate could not see the
 * player's own shuttle — readying early passed a check that was vacuously true, and the impulse
 * ended with the shuttle's move silently skipped. Whoever readied last was unaffected, because
 * by then the ships had moved and the list told the truth.
 *
 * <p>Two questions, and they need two methods. "May it move now" belongs to the move UI, which
 * must not offer a move out of order. "Does it still owe a move" belongs to the gate.
 */
public class ShuttleOwesMovementTest {

    /**
     * The heart of it: the opponent's ship is pending, so nothing of this shuttle's shows in the
     * movable list — and the gate must still know the shuttle owes a move.
     */
    @Test
    public void aShuttleOwesItsMoveEvenWhileTheEnemyShipIsStillPending() {
        Game game = battleAtImpulse(6);          // speed 6 moves on impulse 6
        AdminShuttle shuttle = shuttleIn(game);

        assertFalse("fixture: a ship must still be pending for this to mean anything",
                game.getMovableShips().isEmpty());
        assertTrue("C1.313: the shuttle's turn has not come while ships owe moves",
                game.getMovableShuttles().isEmpty());

        assertTrue("but it still owes the move, and the gate has to see it",
                game.getShuttlesOwingMovement().contains(shuttle));
    }

    /** And once the ships have moved, both questions agree. */
    @Test
    public void onceTheShipsHaveMovedBothListsAgree() {
        Game game = battleAtImpulse(6);
        AdminShuttle shuttle = shuttleIn(game);

        for (Ship s : new java.util.ArrayList<>(game.getMovableShips()))
            game.moveForward(s);

        assertTrue("its turn has come", game.getMovableShuttles().contains(shuttle));
        assertTrue("and it still owes the move", game.getShuttlesOwingMovement().contains(shuttle));
    }

    /** Having moved, it owes nothing more this impulse. */
    @Test
    public void aShuttleThatHasMovedOwesNothing() {
        Game game = battleAtImpulse(6);
        AdminShuttle shuttle = shuttleIn(game);
        for (Ship s : new java.util.ArrayList<>(game.getMovableShips()))
            game.moveForward(s);

        assertTrue(game.moveShuttleForward(shuttle).isSuccess());

        assertTrue("it has had its move", game.getShuttlesOwingMovement().isEmpty());
        assertTrue(game.getMovableShuttles().isEmpty());
    }

    /** On an impulse its speed does not move it, it owes nothing. */
    @Test
    public void aShuttleOwesNothingOnAnImpulseItDoesNotMove() {
        Game game = battleAtImpulse(7);          // speed 6 moves on 6, 11, 16, 22, 27, 32
        assertTrue("speed six does not move on impulse seven",
                game.getShuttlesOwingMovement().isEmpty());
    }

    /**
     * Speed 6 owes a move on exactly the six impulses the chart gives it — including 32, which
     * the playtest reported as skipped.
     */
    @Test
    public void speedSixOwesAMoveOnAllSixOfItsImpulses() {
        java.util.List<Integer> owed = new java.util.ArrayList<>();
        for (int impulse = 1; impulse <= 32; impulse++) {
            Game game = battleAtImpulse(impulse);
            if (!game.getShuttlesOwingMovement().isEmpty())
                owed.add(impulse);
        }
        assertEquals("the C2.0 chart for speed six, impulse 32 included",
                java.util.List.of(6, 11, 16, 22, 27, 32), owed);
    }

    /**
     * A landed shuttle owes nothing (P2.45). Worth its own case: a gate that demanded a move
     * from a craft that cannot fly could never be satisfied and would freeze the phase outright
     * — a worse failure than the one being fixed.
     */
    @Test
    public void aLandedShuttleOwesNothingAndCannotFreezeThePhase() {
        Game game = battleAtImpulse(6);
        shuttleIn(game).setLandingPhase(com.sfb.properties.LandingPhase.LANDED);

        assertTrue(game.getShuttlesOwingMovement().isEmpty());
    }

    /** A shuttle held in a beam owes nothing either (G7.5) — it cannot fly out. */
    @Test
    public void aTractoredShuttleOwesNothing() {
        Game game = battleAtImpulse(6);
        AdminShuttle shuttle = shuttleIn(game);
        shipNamed(game, "USS Xerxes").getTractors().linkUnit(shuttle, game.getAbsoluteImpulse());

        assertTrue("held fast, so it owes no movement",
                game.getShuttlesOwingMovement().isEmpty());
    }

    // ---------------------------------------------------------------- helpers

    /** Two ships and one player-controlled shuttle at speed 6, parked at the named impulse. */
    private static Game battleAtImpulse(int impulse) {
        Game game = new Game();

        Player fed = new Player();
        fed.setName("Captain Dan");
        fed.setTeamName("Federation");
        Player kli = new Player();
        kli.setName("Captain Dave");
        kli.setTeamName("Klingons");
        game.getPlayers().add(fed);
        game.getPlayers().add(kli);

        game.getShips().add(ship(FederationShips.getFedCa(), "USS Xerxes", 10, 10, fed));
        game.getShips().add(ship(KlingonShips.getD7(), "KHS Mystic", 20, 20, kli));

        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setWarpMovement(12);
            game.submitAllocation(s, e);
        }
        // Speeds chosen so a ship is pending on the shuttle's impulses, which is the condition
        // that hid it from the gate.
        shipNamed(game, "USS Xerxes").setSpeed(8);
        shipNamed(game, "KHS Mystic").setSpeed(12);

        AdminShuttle shuttle = new AdminShuttle();
        shuttle.setName("Xerxes-Shuttle-1");
        shuttle.setParentShipName("USS Xerxes");
        shuttle.setLocation(new Location(10, 10));
        shuttle.setFacing(1);
        shuttle.setSpeed(6);
        game.getActiveShuttles().add(shuttle);

        while (game.getCurrentImpulse() != impulse
                || game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        return game;
    }

    private static AdminShuttle shuttleIn(Game game) {
        return (AdminShuttle) game.getActiveShuttles().get(0);
    }

    private static Ship ship(Map<String, Object> spec, String name, int x, int y, Player owner) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        s.setSpeedPreviousTurn(31);
        s.setSpeedTwoTurnsAgo(31);
        s.setOwner(owner);
        owner.getPlayerUnits().add(s);
        return s;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("no ship called " + name);
    }
}
