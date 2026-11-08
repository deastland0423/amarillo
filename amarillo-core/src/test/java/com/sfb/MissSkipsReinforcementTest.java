package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * (D3.34) A shot that scored nothing does not stop the game to ask about reinforcement.
 *
 * <h2>What the owner saw</h2>
 * The Mystic fired at the Xerxes, missed, and the Reinforcement window opened anyway — asking
 * both players whether they wished to spend reserve power defending against zero damage. The
 * phase opened whenever the volley queue was non-empty, and a volley is queued for every shot
 * at a ship, hit or miss.
 *
 * <h2>Why the volley is still queued</h2>
 * It carries more than its damage: (E4.43) overload feedback owed to the FIRING ship, fusion
 * suicide self-damage (E7.421), and the log line that tells both players a shot was taken and
 * missed. So the fix skips the PHASE, not the volley — and the volley must still land on the
 * way past, which is the half of this that would fail silently.
 */
public class MissSkipsReinforcementTest {

    /** A queue holding only a scoreless volley asks nobody anything. */
    @Test
    public void aMissDoesNotOpenTheReinforcementPhase() {
        Game game = battle();
        toDirectFire(game);
        Ship xerxes = shipNamed(game, "USS Xerxes");

        game.queueVolley(new Game.PendingVolley(
                "KHS Mystic", shipNamed(game, "KHS Mystic"), xerxes,
                1, 0, 0, false, false, "  Total damage: 0\n", null, 0, 1));

        game.advancePhase();

        assertNotEquals("a miss must not interrupt the impulse",
                Game.ImpulsePhase.REINFORCEMENT, game.getCurrentPhase());
    }

    /** And the volley still resolves, rather than sitting in the queue for the next shot. */
    @Test
    public void aMissStillResolvesAndLeavesTheQueueEmpty() {
        Game game = battle();
        toDirectFire(game);
        Ship xerxes = shipNamed(game, "USS Xerxes");

        game.queueVolley(new Game.PendingVolley(
                "KHS Mystic", shipNamed(game, "KHS Mystic"), xerxes,
                1, 0, 0, false, false, "  Total damage: 0\n", null, 0, 1));

        game.advancePhase();

        assertTrue("the volley must be applied, not abandoned — a queue that never empties"
                        + " would send the NEXT shot into a phase it did not earn",
                game.getPendingVolleys().isEmpty());
    }

    /**
     * (E4.43) The feedback a miss owes its FIRER still lands. This is the half that would have
     * failed silently if the volley had simply been dropped instead of the phase skipped.
     */
    @Test
    public void overloadFeedbackStillReachesTheFiringShip() {
        Game game = battle();
        toDirectFire(game);
        Ship mystic = shipNamed(game, "KHS Mystic");
        int before = mystic.getShields().getShieldStrength(1);

        game.queueVolley(new Game.PendingVolley(
                "KHS Mystic", mystic, shipNamed(game, "USS Xerxes"),
                1, 0, 0, false, false, "  Total damage: 0\n", null, 3, 1));

        game.advancePhase();

        assertEquals("three points of feedback on the firer's own shield",
                before - 3, mystic.getShields().getShieldStrength(1));
    }

    /** A volley that DID score still opens the phase — the decision is real there. */
    @Test
    public void aHitStillOpensTheReinforcementPhase() {
        Game game = battle();
        toDirectFire(game);

        game.queueVolley(new Game.PendingVolley(
                "KHS Mystic", shipNamed(game, "KHS Mystic"), shipNamed(game, "USS Xerxes"),
                1, 8, 0, false, false, "  Total damage: 8\n", null, 0, 1));

        game.advancePhase();

        assertEquals("eight points incoming is a real decision",
                Game.ImpulsePhase.REINFORCEMENT, game.getCurrentPhase());
    }

    /** A miss alongside a hit does not suppress the hit's prompt. */
    @Test
    public void oneScorelessVolleyDoesNotSuppressARealOne() {
        Game game = battle();
        toDirectFire(game);
        Ship mystic = shipNamed(game, "KHS Mystic");
        Ship xerxes = shipNamed(game, "USS Xerxes");

        game.queueVolley(new Game.PendingVolley(
                "KHS Mystic", mystic, xerxes, 1, 0, 0, false, false, "miss\n", null, 0, 1));
        game.queueVolley(new Game.PendingVolley(
                "KHS Mystic", mystic, xerxes, 2, 5, 0, false, false, "hit\n", null, 0, 1));

        game.advancePhase();

        assertEquals(Game.ImpulsePhase.REINFORCEMENT, game.getCurrentPhase());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Park the game in the fire phase, which must happen BEFORE a volley is queued.
     *
     * The first draft queued first and then advanced, and the two "a hit still prompts" cases
     * failed: the end of MOVEMENT is also a reinforcement entry, so the volley was consumed
     * there and the queue was empty by the time DIRECT_FIRE ended. A fixture that reaches the
     * phase it means to test is half of what these assert.
     */
    private static void toDirectFire(Game game) {
        while (game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE)
            game.advancePhase();
    }

    private static Game battle() {
        Game game = new Game();

        Player fed = new Player();
        fed.setName("Captain Dan");
        fed.setTeamName("Federation");
        Player kli = new Player();
        kli.setName("Captain Dave");
        kli.setTeamName("Kzinti");
        game.getPlayers().add(fed);
        game.getPlayers().add(kli);

        game.getShips().add(ship(FederationShips.getFedCa(), "USS Xerxes", 10, 10, fed));
        game.getShips().add(ship(KlingonShips.getD7(), "KHS Mystic", 10, 12, kli));

        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            game.submitAllocation(s, e);
        }
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
