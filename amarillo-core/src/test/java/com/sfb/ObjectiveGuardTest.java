package com.sfb;

import com.sfb.objects.Objective;
import com.sfb.objects.Ship;
import com.sfb.properties.BoardingPartyQuality;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Posting a boarding party to guard a carried object or individual (D7.83).
 *
 * <h2>A general rule, not SH47's</h2>
 * D7.83 reads "a player may designate some of his boarding parties to be guarding specific
 * INDIVIDUALS OR OBJECTS", and a box on the SSD is only the commonest kind of one. SH47.476
 * then leans on it outright — "If the Stasis Box is aboard one ship, the other ship may attempt
 * a hit-and-run raid to seize the box (it can be guarded)" — but nothing there is particular to
 * that scenario. A defecting admiral, a crate of contraband and a diplomatic pouch all want the
 * same post, and get it from the same code.
 *
 * <h2>Guarded through the ordinary action</h2>
 * There is no new action and no new phase rule: {@code assignGuard} parses an
 * {@code OBJECTIVE:<name>} target code the way it already parses {@code WEAPON:} and
 * {@code TRACTOR:}, so Energy-Allocation timing (D7.83), the roster accounting, the
 * one-per-object limit (D7.833), release on capture (D7.834) and the harsher raid chart
 * (D7.831) all come for free.
 */
public class ObjectiveGuardTest {

    // ---------------------------------------------------------------- posting

    @Test
    public void aBoardingPartyCanGuardACarriedObject() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        int before = fed.getGuardPosts().totalPosted();

        Game.ActionResult result = game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals(before + 1, fed.getGuardPosts().totalPosted());
        assertTrue(fed.getGuardPosts().postedObjectives().contains("Stasis Box"));
    }

    /** (D7.833) "No more than one boarding party can be assigned to guard one object." */
    @Test
    public void onlyOneGuardPerObject() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        assertTrue(game.assignGuard(fed, "OBJECTIVE:Stasis Box", false).isSuccess());

        Game.ActionResult second = game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);
        assertFalse(second.isSuccess());
        assertTrue("the refusal should cite the rule: " + second.getMessage(),
                second.getMessage().contains("D7.833"));
    }

    /**
     * A guard goes on what this ship is CARRYING. Not a box in somebody else's hold, and not
     * one still lying in an asteroid hex — a marine cannot stand over either.
     */
    @Test
    public void onlyWhatThisShipIsCarryingCanBeGuarded() {
        Game game = gameWithCarriedBox();
        Ship blade = shipNamed(game, "IKV Saber");

        assertFalse("the Klingon does not have it",
                game.assignGuard(blade, "OBJECTIVE:Stasis Box", false).isSuccess());

        Objective free = new Objective("Loose Canister", 14, 14);
        game.addObjective(free);
        assertFalse("and a box on the map is aboard nobody",
                game.assignGuard(shipNamed(game, "USS Enterprise"),
                        "OBJECTIVE:Loose Canister", false).isSuccess());
    }

    @Test
    public void anUnknownObjectiveIsRefused() {
        Game game = gameWithCarriedBox();
        assertFalse(game.assignGuard(shipNamed(game, "USS Enterprise"),
                "OBJECTIVE:Nonexistent Thing", false).isSuccess());
    }

    /** (D7.83) Guards are posted during Energy Allocation, like every other guard. */
    @Test
    public void guardsArePostedInEnergyAllocationOnly() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        submitAllocations(game);          // the round closes; impulses begin

        Game.ActionResult result = game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("Energy Allocation"));
    }

    // A commando post is not tested here: quality handling is entirely in the shared
    // assign/roster path, which the other target types already cover, and a Fed CA carries no
    // commando squads to post without reaching past the data to invent one.

    @Test
    public void aGuardCanBeWithdrawn() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        int before = fed.getGuardPosts().totalPosted();
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        assertTrue(game.removeGuard(fed, "OBJECTIVE:Stasis Box").isSuccess());
        assertEquals("the boarding party is back on the roster",
                before, fed.getGuardPosts().totalPosted());
        assertTrue(fed.getGuardPosts().postedObjectives().isEmpty());
    }

    // ---------------------------------------------------------------- the raid (D7.831)

    /**
     * (D7.831) A raid against a guarded object meets the guard, exactly as one against a
     * guarded compartment does — the rule names "a guarded object, person, or compartment" in
     * one breath.
     */
    @Test
    public void aRaidOnAGuardedObjectMeetsTheGuard() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        assertNotNull("the guard must be found by the raid path",
                fed.getGuardPosts().intercept(SystemTarget.forObjective("Stasis Box")));
        assertNull("and an unguarded object must not be",
                fed.getGuardPosts().intercept(SystemTarget.forObjective("Something Else")));
    }

    /** (D7.834) "When the ship is captured, all guards are transferred to boarding party status." */
    @Test
    public void captureReturnsAnObjectGuardToThePool() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        assertEquals("the object guard is among those released",
                1, fed.getGuardPosts().releaseAll());
        assertTrue(fed.getGuardPosts().postedObjectives().isEmpty());
    }

    // ---------------------------------------------------------------- stranded guards

    /**
     * A marine must not be left standing over an empty compartment.
     *
     * <p>A box leaves a hold four ways — beamed out, jettisoned, stolen, or gone down with the
     * ship — and the post is reconciled by ONE sweep at the turn boundary rather than by a
     * release call on each of those paths, because a call that has to be written at every site
     * is one that gets forgotten at the fifth. The boundary is also D7.83's own answer: posts
     * "cannot be changed until the next turn", so one lapses when the next turn's assignments
     * are made rather than the instant its charge is carried off.
     */
    @Test
    public void aGuardWhoseChargeHasGoneIsReturnedAtTheTurnBoundary() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        int roster = fed.getGuardPosts().totalPosted();
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);
        assertEquals(roster + 1, fed.getGuardPosts().totalPosted());

        // Jettisoned into space: the hold is empty and the post means nothing.
        named(game, "Stasis Box").setCarrier(null);
        named(game, "Stasis Box").setLocation(fed.getLocation());

        runToTurnEnd(game);

        assertTrue("the post should be gone", fed.getGuardPosts().postedObjectives().isEmpty());
        assertEquals("and the boarding party back on the roster",
                roster, fed.getGuardPosts().totalPosted());
    }

    /** A guard over something still in the hold is left exactly where it is. */
    @Test
    public void aGuardStillWithItsChargeStaysPosted() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        runToTurnEnd(game);

        assertTrue("nothing left the hold, so nothing is released",
                fed.getGuardPosts().postedObjectives().contains("Stasis Box"));
    }

    // ---------------------------------------------------------------- helpers

    private static Game gameWithCarriedBox() {
        Game game = new Game();

        Player fedPlayer = new Player();
        fedPlayer.setName("Captain Dan");
        fedPlayer.setTeamName("Federation");
        Player kliPlayer = new Player();
        kliPlayer.setName("Captain Dave");
        kliPlayer.setTeamName("Klingons");
        game.getPlayers().add(fedPlayer);
        game.getPlayers().add(kliPlayer);

        Ship fed = ship(FederationShips.getFedCa(), "USS Enterprise", 10, 10, fedPlayer);
        Ship blade = ship(KlingonShips.getD7(), "IKV Saber", 20, 20, kliPlayer);
        game.getShips().add(fed);
        game.getShips().add(blade);

        // Aboard from the start, so the test is about the guard and not about the beam.
        Objective box = new Objective("Stasis Box", 10, 10);
        box.getAllowedRetrieval().add(com.sfb.properties.RetrievalMethod.TRANSPORTER);
        box.setCargoSpaces(2.0);
        box.setCarrier(fed);
        box.setLocation(null);
        game.addObjective(box);

        game.startTurn();    // guards are posted during Energy Allocation (D7.83)
        return game;
    }

    private static void submitAllocations(Game game) {
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            game.submitAllocation(s, e);
        }
    }

    private static void runToTurnEnd(Game game) {
        submitAllocations(game);
        while (game.getCurrentImpulse() != 32
                || game.getCurrentPhase() != Game.ImpulsePhase.END_OF_IMPULSE)
            game.advancePhase();
        game.endTurn();
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

    private static Objective named(Game game, String name) {
        for (Objective o : game.getObjectives())
            if (name.equals(o.getName()))
                return o;
        throw new AssertionError("no objective called " + name);
    }

    @SuppressWarnings("unused")
    private static BoardingPartyQuality unused() { return BoardingPartyQuality.NORMAL; }
}
