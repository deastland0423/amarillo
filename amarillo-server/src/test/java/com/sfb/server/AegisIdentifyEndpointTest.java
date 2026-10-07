package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.properties.AegisLevel;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D13.3 identification reaches a player: the candidates endpoint and the {@code AEGIS_IDENTIFY}
 * action.
 *
 * <h2>Why this is not the aegis FIRE endpoint with a flag</h2>
 * {@code /fire-targets?aegis=true} answers D13.21/D13.23 — size class 6 or smaller, within six
 * hexes, <b>with a lock-on</b>, and a weapon that bears. None of that governs identification:
 * D13.3 is the sensor suite reading an incoming seeker, needs no lock-on and no weapon, and its
 * only distance rule is its own table. A seeker you can see but cannot shoot must still be
 * identifiable, since identifying it is how you decide whether shooting is worth a firing.
 *
 * <h2>Tested at the layer that gates it</h2>
 * Through {@code controller} and {@code session.executeAction}, not by calling
 * {@code Game.identifyWithAegis}. Twice in this project a feature passed every test and was
 * unreachable in play because the tests entered below the controller's ownership and phase checks.
 * Core's own rules are covered in core; what is pinned here is that a player can actually do it.
 */
class AegisIdentifyEndpointTest {

    private GameController controller;
    private GameSession session;
    private Game game;
    private Ship aegisShip;
    private Ship enemyCruiser;
    private CataloguedFighter fighter;
    private String gameId;
    private String HOST;
    private String P2;

    @BeforeEach
    void setUp() {
        GameSessionService service = new GameSessionService();
        controller = new GameController(service, null);
        session = service.createSession("Alice");
        gameId = session.getId();
        HOST = session.getPlayers().keySet().iterator().next();
        P2 = service.joinSession(gameId, "Bob");
        game = session.getGame();

        // The hull is not the point: D13.0 makes aegis a property of the HULL and never of being an
        // escort, so reaching for a real escort would be asserting the data instead of the rule.
        aegisShip = new Ship();
        aegisShip.init(FederationShips.getFedCa());
        aegisShip.setName("USS Enterprise");
        aegisShip.setLocation(new Location(10, 10));
        aegisShip.setFacing(1);
        aegisShip.setAegisFitted(AegisLevel.FULL);

        enemyCruiser = new Ship();
        enemyCruiser.init(KlingonShips.getD7());
        enemyCruiser.setName("IKV Saber");
        enemyCruiser.setLocation(new Location(10, 8));
        enemyCruiser.setFacing(13);

        game.getShips().add(aegisShip);
        game.getShips().add(enemyCruiser);

        // A shuttle, and that is deliberate. D13.32: "This procedure can be used against shuttles
        // that are SUSPECTED to be seeking weapons" — which is the point of the rule, since an
        // admin shuttle and a suicide shuttle look alike until someone looks.
        fighter = CataloguedFighter.of("haas");
        fighter.setName("HAAS-1");
        fighter.setLocation(new Location(10, 9));        // one hex out: automatic
        fighter.setFacing(13);
        java.util.List<CataloguedFighter> bobsShuttles = new java.util.ArrayList<>();
        bobsShuttles.add(fighter);
        game.getActiveShuttles().add(fighter);

        // Four more, for the impulse-cap test. An attempt at one hex is automatic (D13.31) and an
        // identified unit stops being a candidate, so spending four attempts needs four targets —
        // there is no un-identifying one, and leaning on a failed roll would make the test a coin
        // flip. Spread one hex apart, all well inside the chart.
        for (int i = 2; i <= 5; i++) {
            CataloguedFighter extra = CataloguedFighter.of("haas");
            extra.setName("HAAS-" + i);
            extra.setLocation(new Location(10, 9));
            extra.setFacing(13);
            game.getActiveShuttles().add(extra);
            bobsShuttles.add(extra);
        }

        Player alice = new Player();
        alice.setName("Alice");
        alice.setTeamName("Federation");
        alice.getPlayerUnits().add(aegisShip);
        session.getPlayers().get(HOST).setCorePlayer(alice);
        Player bob = new Player();
        bob.setName("Bob");
        bob.setTeamName("Klingon");
        bob.getPlayerUnits().add(enemyCruiser);
        for (CataloguedFighter f : bobsShuttles)
            bob.getPlayerUnits().add(f);
        session.getPlayers().get(P2).setCorePlayer(bob);

        // setOwner as well as the player's unit list, and the privacy tests below are why. The DTO
        // decides whether a viewer is an enemy from the UNIT's owner; a ship that was only added to
        // a player's list has none, every view counts as its own, redactForEnemy never runs and a
        // privacy assertion passes while disclosing everything.
        aegisShip.setOwner(alice);
        enemyCruiser.setOwner(bob);
        for (CataloguedFighter f : bobsShuttles)
            f.setOwner(bob);

        game.startTurn();
        game.submitAllocation(aegisShip, allocationFor(aegisShip));
        game.submitAllocation(enemyCruiser, allocationFor(enemyCruiser));
        aegisShip.setActiveFireControl(true);
    }

    private Energy allocationFor(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        return e;
    }

    /** D13.31 happens in the Ship System Functions Stage (6B4), which is our ACTIVITY phase. */
    private void reachActivity() {
        for (int guard = 0; guard < 24
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase(),
                "fixture: identification happens in the Ship System Functions Stage (6B4)");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> idTargets(String token, String attacker) {
        Object body = controller.getAegisIdTargets(gameId, token, attacker).getBody();
        assertInstanceOf(List.class, body, "unexpected body: " + body);
        return (List<Map<String, Object>>) body;
    }

    private List<String> names(String token) {
        return idTargets(token, "USS Enterprise").stream()
                .map(r -> (String) r.get("name")).toList();
    }

    private ActionRequest identify(String token, String ship, String target) {
        ActionRequest req = new ActionRequest();
        req.setType("AEGIS_IDENTIFY");
        req.setPlayerToken(token);
        req.setShipName(ship);
        req.setTargetName(target);
        return req;
    }

    /** The view one SIDE gets. The DTO is built per team, which is what carries the redaction. */
    private GameStateDtoHolder view(String team) {
        return new GameStateDtoHolder(new com.sfb.dto.GameStateDto(game, team));
    }

    /** Thin wrapper so a test can name the ship it means without repeating the stream. */
    private static final class GameStateDtoHolder {
        private final com.sfb.dto.GameStateDto dto;
        GameStateDtoHolder(com.sfb.dto.GameStateDto dto) { this.dto = dto; }
        com.sfb.dto.GameStateDto.ShipDto ship(String name) {
            for (com.sfb.dto.GameStateDto.MapObjectDto o : dto.mapObjects)
                if (name.equals(o.name) && o instanceof com.sfb.dto.GameStateDto.ShipDto s)
                    return s;
            return fail("no ship " + name + " in this view");
        }
    }

    // ---------------------------------------------------------------------- the candidate list

    /**
     * The shuttle is offered and the cruiser beside it is not. Both are the same enemy's and both
     * are two hexes away; one is a seeking-weapon candidate and the other is a ship.
     */
    @Test
    void theShuttleIsOfferedAndTheCruiserIsNot() {
        reachActivity();
        List<String> names = idTargets(HOST, "USS Enterprise").stream()
                .map(r -> (String) r.get("name")).toList();

        assertTrue(names.contains("HAAS-1"), "the shuttle should be a candidate: " + names);
        assertFalse(names.contains("IKV Saber"), "a ship is not a seeking weapon: " + names);
    }

    /** D13.35 and D13.412: a limited system cannot identify at all, so it is offered nothing. */
    @Test
    void aLimitedSystemIsOfferedNothing() {
        reachActivity();
        assertFalse(idTargets(HOST, "USS Enterprise").isEmpty(), "fixture: a full system has rows");

        aegisShip.setAegisFitted(AegisLevel.LIMITED);

        assertTrue(idTargets(HOST, "USS Enterprise").isEmpty(),
                "a limited aegis cannot identify seeking weapons (D13.412)");
    }

    /**
     * D13.31's table, carried on the row rather than left to the client: 0-3 automatic, 4 on a 1-4,
     * 5 on a 1-3, 6 on a 1, and beyond six not allowed at all. "Automatic" is spelled as 6 because
     * no die beats it.
     */
    @Test
    void theRowSaysWhatDieItNeeds() {
        reachActivity();
        int[][] expected = { { 9, 6 }, { 6, 4 }, { 5, 3 }, { 4, 1 } };   // {y, needs}
        for (int[] pair : expected) {
            fighter.setLocation(new Location(10, pair[0]));
            Map<String, Object> row = idTargets(HOST, "USS Enterprise").stream()
                    .filter(r -> "HAAS-1".equals(r.get("name"))).findFirst().orElseThrow();
            assertEquals(pair[1], row.get("needs"),
                    "at range " + row.get("range") + " the chart says " + pair[1]);
        }

        // And one hex further out it drops off entirely (D13.31: "7+ not allowed").
        fighter.setLocation(new Location(10, 3));
        assertFalse(names(HOST).contains("HAAS-1"),
                "beyond six hexes there is no attempt to offer: " + names(HOST));
    }

    /**
     * An identified unit leaves the list. Nothing forbids attempting one again, but there is
     * nothing to learn and only six attempts a turn, so offering it would be offering a mistake.
     */
    @Test
    void anIdentifiedShuttleDropsOffTheList() {
        reachActivity();
        assertFalse(idTargets(HOST, "USS Enterprise").isEmpty(), "fixture: it starts unidentified");

        fighter.identify();

        assertFalse(names(HOST).contains("HAAS-1"),
                "there is nothing left to learn about it: " + names(HOST));
        assertTrue(names(HOST).contains("HAAS-2"),
                "and its unidentified neighbours are still offered");
    }

    // ------------------------------------------------------------------------------ the action

    /** The attempt goes through, spends one of the six, and says so in the owner's next view. */
    @Test
    void theActionIdentifiesAndSpendsAnAttempt() {
        reachActivity();
        assertEquals(6, view("Federation").ship("USS Enterprise").aegisIdAttemptsThisTurn,
                "fixture: six a turn, none spent (D13.31)");

        ActionResult r = session.executeAction(identify(HOST, "USS Enterprise", "HAAS-1"));

        assertTrue(r.isSuccess(), r.getMessage());
        assertTrue(fighter.isIdentified(), "one hex out is automatic (D13.31)");
        assertEquals(5, view("Federation").ship("USS Enterprise").aegisIdAttemptsThisTurn);
        assertEquals(3, view("Federation").ship("USS Enterprise").aegisIdAttemptsThisImpulse,
                "four an impulse, one spent (D13.32)");
    }

    /**
     * D13.32's impulse cap bites before D13.31's turn allowance, and the refusal reaches the player
     * with the rule number rather than as a silent no-op.
     */
    @Test
    void theFifthAttemptInOneImpulseIsRefused() {
        reachActivity();
        for (int i = 1; i <= 4; i++)
            assertTrue(session.executeAction(
                            identify(HOST, "USS Enterprise", "HAAS-" + i)).isSuccess(),
                    "attempt " + i + " of four should be allowed");

        ActionResult fifth = session.executeAction(identify(HOST, "USS Enterprise", "HAAS-5"));

        assertFalse(fifth.isSuccess(), "D13.32 allows no more than four in one impulse");
        assertTrue(fifth.getMessage().contains("D13.32"),
                "the refusal should cite the rule: " + fifth.getMessage());
        assertEquals(0, view("Federation").ship("USS Enterprise").aegisIdAttemptsThisImpulse);
        assertEquals(2, view("Federation").ship("USS Enterprise").aegisIdAttemptsThisTurn,
                "and the turn allowance has two left for a later impulse");
    }

    /** A limited system is refused the action itself, not merely left off the candidate list. */
    @Test
    void aLimitedSystemIsRefusedTheAction() {
        reachActivity();
        aegisShip.setAegisFitted(AegisLevel.LIMITED);

        ActionResult r = session.executeAction(identify(HOST, "USS Enterprise", "HAAS-1"));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("D13.35"), r.getMessage());
        assertFalse(fighter.isIdentified());
    }

    // ------------------------------------------------------------------------------- the DTO

    /**
     * A limited system says "not applicable" rather than nought.
     * <p>
     * The reason these are {@code Integer}: a primitive always serialises, so every ship in the
     * game would report 0 attempts and a pad reading it could not tell a limited system from a full
     * one that had spent all six. That exact trap has shipped bugs here before.
     */
    @Test
    void aSystemThatCannotIdentifySaysSoRatherThanZero() {
        reachActivity();
        assertNotNull(view("Federation").ship("USS Enterprise").aegisIdAttemptsThisTurn);

        aegisShip.setAegisFitted(AegisLevel.LIMITED);
        assertNull(view("Federation").ship("USS Enterprise").aegisIdAttemptsThisTurn,
                "a limited system has no allowance to report (D13.412)");

        aegisShip.setAegisFitted(AegisLevel.NONE);
        assertNull(view("Federation").ship("USS Enterprise").aegisIdAttemptsThisTurn);
    }

    /**
     * And an enemy is told none of it. The COUNT names the system — six a turn is a full aegis and
     * nothing is a limited one — so it is the same disclosure D13.51 withholds, one step further on.
     */
    @Test
    void anEnemyIsNotToldHowManyAttemptsAreLeft() {
        reachActivity();
        assertNotNull(view("Federation").ship("USS Enterprise").aegisIdAttemptsThisTurn,
                "fixture: the owner can see it, so a null below means withheld and not absent");

        com.sfb.dto.GameStateDto.ShipDto theirs = view("Klingon").ship("USS Enterprise");

        assertNull(theirs.aegisIdAttemptsThisTurn);
        assertNull(theirs.aegisIdAttemptsThisImpulse);
    }

    /**
     * D13.321's modifier is reported, and D13.322 is why it takes two impulses to appear.
     * <p>
     * "Attempts during the same impulse are all rolled simultaneously and do not count as
     * 'previous' to each other" — so a second attempt in the SAME impulse gets no modifier, and the
     * row must not claim one. Only an attempt in an earlier impulse promotes.
     */
    @Test
    void theRepeatModifierAppearsOnlyAfterAnEarlierImpulse() {
        reachActivity();
        java.util.function.Predicate<String> repeatFor = name ->
                (Boolean) idTargets(HOST, "USS Enterprise").stream()
                        .filter(r -> name.equals(r.get("name")))
                        .findFirst().orElseThrow().get("repeat");

        assertFalse(repeatFor.test("HAAS-1"), "nothing has been attempted yet");

        // Recorded through core's own accounting rather than by firing the action, because the
        // claim here is what the ENDPOINT reports. Going through the action would identify the
        // target at this range and drop it off the list, and moving it out to where the roll can
        // fail would make the test a coin flip.
        int turn = game.getClock().getTurn();
        aegisShip.recordAegisIdAttempt(turn, game.getAbsoluteImpulse(), "HAAS-1");

        assertFalse(repeatFor.test("HAAS-1"),
                "the same impulse gives no modifier (D13.322)");

        game.advancePhase();
        reachActivity();                                 // the next impulse's 6B4

        assertTrue(repeatFor.test("HAAS-1"),
                "now the earlier attempt is the immediately previous one (D13.321)");
        assertFalse(repeatFor.test("HAAS-2"),
                "and only for the seeker it was aimed at");
    }
}
