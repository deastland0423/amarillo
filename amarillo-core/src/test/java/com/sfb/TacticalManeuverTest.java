package com.sfb;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.systemgroups.PerformanceData;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * (C5.0) A Tactical Maneuver turns the ship. Every time.
 *
 * <h2>What the owner saw</h2>
 * Turn 7 of a live game: the USS Xerxes was ordered to turn, the log said
 * "USS Xerxes Warp Tactical Maneuver → facing 5", and the ship on the map went on pointing
 * where it had been. A second order, a sublight maneuver this time, did the same. The server's
 * own state agreed with the map — facing 9, unchanged — so the log line was simply false.
 *
 * <p>The cause was one call: {@code ShipMover.performTacticalTurn} applied the facing through
 * {@code Ship.performHet}, which is overridden to roll for a C6.5 HET BREAKDOWN and sets the
 * facing only when that roll passes. A Tactical Maneuver has no such roll. So every maneuver
 * was a dice throw whose odds came from the hull's HET rating, and the caller reported success
 * either way, having never looked at the return value.
 *
 * <p>That is why it looked like a Federation bug. The Kzinti CL has breakdown 5 and turned on
 * a roll of 1-4; the Federation DD has breakdown 3 and needed a 1 or a 2. The same order, the
 * same code, two hulls, two success rates — and the one that failed said it had worked.
 *
 * <h2>How these tests pin it</h2>
 * An unseeded die passes a test by coin flip, so none of this asserts against the roll. Instead
 * the ships are given a breakdown rating of 1, which no HET can ever pass (a die is at least 1,
 * and 1 &lt; 1 is false), and their -2 bonus is spent first so nothing can drag the roll under.
 * Any route from a Tactical Maneuver into the HET machinery then leaves the facing alone, and
 * these fail every run rather than two runs in three.
 */
public class TacticalManeuverTest {

    /** The whole bug, in one assertion: the ship ends up where the log says it does. */
    @Test
    public void aTacticalManeuverActuallyTurnsTheShip() {
        Game game = battle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        noHetCanEverSucceed(xerxes);
        toTacWindow(game);

        ActionResult result = game.performTacticalTurn(xerxes, 5, false);

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals("the facing the message claims is the facing the ship has",
                5, xerxes.getFacing());
    }

    /** The sublight maneuver (C5.12) went through the same call, and failed the same way. */
    @Test
    public void aSublightManeuverTurnsTheShipToo() {
        Game game = battle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        noHetCanEverSucceed(xerxes);
        toTacWindow(game);

        ActionResult result = game.performTacticalTurn(xerxes, 21, true);

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals(21, xerxes.getFacing());
    }

    /**
     * A hull with a poor HET rating turns as readily as a good one.
     *
     * The Federation DD's breakdown 3 against the Kzinti CL's 5 is what made this look like a
     * one-faction fault in play, so the two are asserted side by side.
     */
    @Test
    public void theHullsHetRatingDoesNotDecideWhetherItCanTurn() {
        Game game = battle();
        Ship weakHet   = shipNamed(game, "USS Xerxes");
        Ship strongHet = shipNamed(game, "KHS Mystic");
        // The live hulls were breakdown 3 and 5, which under the bug fail 2/3 and 1/3 of the
        // time — a test that would have passed this run by luck. Pushed to the ends of the
        // scale instead, so the weak one can never pass a HET roll and the strong one always
        // can: identical outcomes then mean the rating is not being consulted at all.
        PerformanceData weak = weakHet.getPerformanceData();
        while (weak.getBreakdownChance() > 1) weak.decrementBreakdownRating();
        while (weak.getBonusHetsRemaining() > 0) weak.useBonusHet();
        strongHet.getPerformanceData().init(Map.of("breakdown", 6));
        toTacWindow(game);

        assertTrue(game.performTacticalTurn(weakHet, 5, false).isSuccess());
        assertTrue(game.performTacticalTurn(strongHet, 5, false).isSuccess());

        assertEquals("a hull that can never pass a HET roll turns anyway",
                5, weakHet.getFacing());
        assertEquals("exactly as one that always would",
                5, strongHet.getFacing());
    }

    /**
     * (C6.52) The ship's -2 bonus belongs to a High Energy Turn, and a Tactical Maneuver may
     * not spend it. The old path did, on the first maneuver of the ship's life — which also
     * made that first one succeed far more often than the next, and so hid the bug.
     */
    @Test
    public void aTacticalManeuverDoesNotSpendTheBonusHet() {
        Game game = battle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        int bonusBefore = xerxes.getPerformanceData().getBonusHetsRemaining();
        assumeHasBonus(bonusBefore);
        toTacWindow(game);

        game.performTacticalTurn(xerxes, 5, false);

        assertEquals("the HET bonus is still there for an actual HET",
                bonusBefore, xerxes.getPerformanceData().getBonusHetsRemaining());
    }

    /**
     * And the maneuver is still spent, so a second one in the same window is refused.
     *
     * <p>This ship pays for a warp maneuver and NOT a sublight one, so that the refusal below is
     * C5.232's and nothing else. The first draft bought both and the second request succeeded by
     * falling back to the sublight maneuver — which was a second bug, C5.31, left recorded in
     * BACKLOG at the time rather than pinned here. It is fixed now and has its own tests below;
     * this one keeps its narrow fixture so the two failures stay distinguishable.
     */
    @Test
    public void theManeuverIsConsumed() {
        Game game = battle(false);
        Ship xerxes = shipNamed(game, "USS Xerxes");
        noHetCanEverSucceed(xerxes);
        toTacWindow(game);

        assertTrue(game.performTacticalTurn(xerxes, 5, false).isSuccess());
        ActionResult second = game.performTacticalTurn(xerxes, 9, false);

        assertFalse("one earned maneuver is one maneuver (C5.232)", second.isSuccess());
        assertEquals("and the refused one leaves the facing alone", 5, xerxes.getFacing());
    }

    // ---------------------------------------------------------------- C5.31, same impulse

    /**
     * (C5.31) Both kinds in one TURN, "but NOT during the same impulse".
     *
     * <p>This is the bug {@link #theManeuverIsConsumed} was written around. A ship that paid for
     * both spent its warp maneuver, and the fallback in {@code performTacticalTurn} handed it the
     * sublight one on the next call in the same impulse — two 60° turns on one impulse, which is
     * 120° and a different maneuver entirely.
     */
    @Test
    public void bothKindsCannotBeUsedOnTheSameImpulse() {
        Game game = battle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        noHetCanEverSucceed(xerxes);
        toTacWindow(game);

        assertTrue(game.performTacticalTurn(xerxes, 5, false).isSuccess());
        ActionResult second = game.performTacticalTurn(xerxes, 9, false);

        assertFalse("the sublight maneuver is not a second bite at this impulse (C5.31)",
                second.isSuccess());
        assertTrue("and the refusal says which rule it is: " + second.getMessage(),
                second.getMessage().contains("C5.31"));
        assertEquals("the facing is the one maneuver's, not two", 5, xerxes.getFacing());
    }

    /** Asking for the sublight one BY NAME is refused on that impulse too, not just the fallback. */
    @Test
    public void theSameImpulseBanAppliesToAnExplicitSublightRequest() {
        Game game = battle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        noHetCanEverSucceed(xerxes);
        toTacWindow(game);

        assertTrue(game.performTacticalTurn(xerxes, 5, false).isSuccess());

        assertFalse(game.performTacticalTurn(xerxes, 9, true).isSuccess());
        assertEquals(5, xerxes.getFacing());
    }

    /**
     * And the first half of C5.31 still stands: both kinds in the same turn is LEGAL, a later
     * impulse being all it takes. A ban written as "one maneuver per turn" would pass the test
     * above and fail this one.
     */
    @Test
    public void bothKindsMayBeUsedOnDifferentImpulsesOfTheSameTurn() {
        Game game = battle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        noHetCanEverSucceed(xerxes);
        toTacWindow(game);

        assertTrue(game.performTacticalTurn(xerxes, 5, false).isSuccess());
        toMovementOfNextImpulse(game);

        ActionResult sublight = game.performTacticalTurn(xerxes, 9, true);

        assertTrue(sublight.getMessage(), sublight.isSuccess());
        assertEquals("60° on one impulse and 60° on another", 9, xerxes.getFacing());
    }

    // ---------------------------------------------------------------- C5.13, the free turn

    /**
     * (C5.13) A ship that allocated nothing to any movement function turns once, free, on
     * Impulse #32 — on attitude thrusters.
     *
     * <p>The ship that wants this is the one parked on an objective studying it (SH47.474),
     * whose movement line is empty by design. Before this it could not turn at all: no budget,
     * no sublight point, no maneuver.
     */
    @Test
    public void aShipThatPaidForNoMovementTurnsFreeOnImpulse32() {
        Game game = stationaryBattle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        noHetCanEverSucceed(xerxes);
        toMovementOfImpulse(game, 32);

        ActionResult result = game.performTacticalTurn(xerxes, 5, false);

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals(5, xerxes.getFacing());
    }

    /** One per turn, and the second is refused. */
    @Test
    public void theFreeTurnIsOnlyOnePerTurn() {
        Game game = stationaryBattle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        noHetCanEverSucceed(xerxes);
        toMovementOfImpulse(game, 32);

        assertTrue(game.performTacticalTurn(xerxes, 5, false).isSuccess());
        ActionResult second = game.performTacticalTurn(xerxes, 9, false);

        assertFalse("one free turn per turn (C5.13)", second.isSuccess());
        assertEquals(5, xerxes.getFacing());
    }

    /**
     * Before Impulse #32 it is refused, and the refusal says WHY rather than reporting the
     * generic "no maneuver available" — a player reading that at impulse 20 concludes the ship
     * cannot turn this turn at all, which is the opposite of true.
     */
    @Test
    public void theFreeTurnIsRefusedBeforeImpulse32ButSaysSo() {
        Game game = stationaryBattle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        toTacWindow(game);
        assertTrue("fixture must be short of impulse 32", game.getCurrentImpulse() < 32);

        ActionResult early = game.performTacticalTurn(xerxes, 5, false);

        assertFalse(early.isSuccess());
        assertTrue("the message names the rule and the impulse: " + early.getMessage(),
                early.getMessage().contains("C5.13") && early.getMessage().contains("32"));
        assertEquals(1, xerxes.getFacing());
    }

    /**
     * A ship that DID pay for movement gets no free turn, even with nothing left to spend. The
     * clause is about the allocation form, not about what is unspent at impulse 32.
     */
    @Test
    public void aShipThatPaidForMovementGetsNoFreeTurn() {
        Game game = battle(false);
        Ship xerxes = shipNamed(game, "USS Xerxes");
        noHetCanEverSucceed(xerxes);
        toTacWindow(game);
        assertTrue(game.performTacticalTurn(xerxes, 5, false).isSuccess()); // spends the warp one
        toMovementOfImpulse(game, 32);

        ActionResult free = game.performTacticalTurn(xerxes, 9, false);

        assertFalse("it allocated a movement function, so C5.13 does not apply", free.isSuccess());
        assertEquals(5, xerxes.getFacing());
    }

    /**
     * And the free turn is 60°, like any other Tactical Maneuver. The 180° in C5.13's last
     * paragraph belongs to a SHUTTLE, not to a ship — see the note in BACKLOG.
     */
    @Test
    public void theFreeTurnIsStillSixtyDegrees() {
        Game game = stationaryBattle();
        Ship xerxes = shipNamed(game, "USS Xerxes");
        toMovementOfImpulse(game, 32);

        assertFalse(game.performTacticalTurn(xerxes, 13, false).isSuccess()); // 180°
        assertEquals(1, xerxes.getFacing());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Breakdown rating 1: {@code roll < 1} is false for every face of a die, so no HET can
     * pass, and the -2 bonus is spent first so nothing can pull a roll below it. A Tactical
     * Maneuver that still turns the ship has provably not gone through that machinery.
     */
    private static void noHetCanEverSucceed(Ship ship) {
        PerformanceData pd = ship.getPerformanceData();
        while (pd.getBreakdownChance() > 1)
            pd.decrementBreakdownRating();
        while (pd.getBonusHetsRemaining() > 0)
            pd.useBonusHet();
    }

    private static void assumeHasBonus(int bonus) {
        assertTrue("fixture expects a hull with a bonus HET to spend (C6.52)", bonus > 0);
    }

    /**
     * Park the game in the Movement phase of an impulse that can take a maneuver: not impulse 1
     * (C5.11), and at or past the impulse 2 earn (C5.231).
     */
    private static void toTacWindow(Game game) {
        for (int guard = 0; guard < 400; guard++) {
            if (game.getCurrentImpulse() >= 2
                    && game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT)
                return;
            game.advancePhase();
        }
        fail("never reached the Movement phase of impulse 2");
    }

    /** Advance to the Movement phase of a later impulse than the one we are on. */
    private static void toMovementOfNextImpulse(Game game) {
        int from = game.getCurrentImpulse();
        for (int guard = 0; guard < 400; guard++) {
            game.advancePhase();
            if (game.getCurrentImpulse() > from
                    && game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT)
                return;
        }
        fail("never reached the Movement phase of an impulse after " + from);
    }

    /** Advance to the Movement phase of a given impulse of this turn. */
    private static void toMovementOfImpulse(Game game, int impulse) {
        for (int guard = 0; guard < 4000; guard++) {
            if (game.getCurrentImpulse() == impulse
                    && game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT)
                return;
            game.advancePhase();
        }
        fail("never reached the Movement phase of impulse " + impulse);
    }

    /** Two stationary ships, each having paid for a warp and a sublight maneuver (C5.22/C5.12). */
    private static Game battle() {
        return battle(true);
    }

    /**
     * Two ships whose allocation forms contain NO movement function at all — the C5.13 case.
     * Life support and fire control only, which is what a ship sitting on an objective
     * studying it actually pays for.
     */
    private static Game stationaryBattle() {
        Game game = emptyBattle();
        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            game.submitAllocation(s, e);
        }
        return game;
    }

    /** @param sublightToo whether each ship also pays the impulse point for a sublight TAC. */
    private static Game battle(boolean sublightToo) {
        Game game = emptyBattle();
        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setWarpTacticalTurns(1);
            if (sublightToo)
                e.setImpulseTacticalTurn(1);
            game.submitAllocation(s, e);
        }
        return game;
    }

    /** Two stationary ships facing A, no allocation submitted yet. */
    private static Game emptyBattle() {
        Game game = new Game();

        Player fed = new Player();
        fed.setName("Captain Dan");
        fed.setTeamName("Federation");
        Player kzin = new Player();
        kzin.setName("Captain Dave");
        kzin.setTeamName("Kzinti");
        game.getPlayers().add(fed);
        game.getPlayers().add(kzin);

        game.getShips().add(ship(FederationShips.getFedCa(), "USS Xerxes", 10, 10, fed));
        game.getShips().add(ship(KlingonShips.getD7(), "KHS Mystic", 10, 12, kzin));
        return game;
    }

    private static Ship ship(Map<String, Object> spec, String name, int x, int y, Player owner) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        s.setSpeedPreviousTurn(0);
        s.setSpeedTwoTurnsAgo(0);
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
