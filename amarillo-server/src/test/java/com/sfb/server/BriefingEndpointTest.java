package com.sfb.server;

import com.sfb.Player;
import com.sfb.scenario.ScenarioSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The /player-aid/briefing endpoint: the scenario page, reachable once the battle has started.
 *
 * <h2>Why it exists</h2>
 * All of this was already in the scenario file and none of it could be read during play. The
 * description and special rules are shown by the pre-game picker and nowhere else, and the
 * disengagement edges never left core at all — they were loaded into {@code Game} and used by
 * {@code ShipMover}, and no DTO or endpoint carried them. The owner, holding a Stasis Box and
 * needing to know which edge was his, had nowhere to look (2026-11-07).
 *
 * <h2>The one piece of real logic</h2>
 * The scenario file states the edges that DESTROY a ship; a player wants the one he may leave
 * by. The endpoint sends both, and the complement is computed in exactly one place — here —
 * rather than in the panel, so a client cannot come to a different answer from the rule
 * {@code ShipMover} is enforcing.
 */
class BriefingEndpointTest {

    private GameController controller;
    private GameSession session;
    private String gameId;
    private String HOST;
    private String P2;

    @BeforeEach
    void setUp() {
        GameSessionService service = new GameSessionService();
        controller = new GameController(service, null);
        session = service.createSession("Alice");
        gameId  = session.getId();
        HOST    = session.getPlayers().keySet().iterator().next();
        P2      = service.joinSession(gameId, "Bob");

        session.adoptRestored(spec());

        Player alice = new Player();
        alice.setName("Alice");
        alice.setTeamName("United Federation of Planets");
        session.getPlayers().get(HOST).setCorePlayer(alice);

        Player bob = new Player();
        bob.setName("Bob");
        bob.setTeamName("Kzinti Hegemony");
        session.getPlayers().get(P2).setCorePlayer(bob);
    }

    /** SH47.41's shape: one way out each, in opposite corners of the map. */
    private static ScenarioSpec spec() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "The Stasis Box";
        spec.description = "A Federation destroyer and a Kzinti light cruiser, one asteroid field.";
        spec.specialRules = new ArrayList<>(List.of(
                "MAP (SH47.41): The Federation can only disengage from the 42xx map edge."));
        spec.victoryConditions = new ScenarioSpec.VictoryConditions();
        spec.victoryConditions.type = "SPECIAL";
        spec.victoryConditions.notes = "SH47.5: the player who disengages with a Stasis Box wins.";

        spec.sides = new ArrayList<>();
        spec.sides.add(side("United Federation of Planets", "Federation", "USS Xerxes",
                Set.of("TOP", "BOTTOM", "LEFT")));
        spec.sides.add(side("Kzinti Hegemony", "Kzinti", "KHS Mystic",
                Set.of("TOP", "BOTTOM", "RIGHT")));
        return spec;
    }

    private static ScenarioSpec.SideSpec side(String name, String faction, String shipName,
            Set<String> deadly) {
        ScenarioSpec.SideSpec s = new ScenarioSpec.SideSpec();
        s.name = name;
        s.faction = faction;
        s.destructionEdges = new java.util.HashSet<>(deadly);
        ScenarioSpec.ShipSetup ship = new ScenarioSpec.ShipSetup();
        ship.type = "DD";
        ship.shipName = shipName;
        s.ships = new ArrayList<>(List.of(ship));
        return s;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> briefing(String token) {
        ResponseEntity<?> response = controller.getBriefing(gameId, token);
        assertEquals(200, response.getStatusCode().value());
        return (Map<String, Object>) response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sideNamed(Map<String, Object> briefing, String name) {
        for (Map<String, Object> s : (List<Map<String, Object>>) briefing.get("sides"))
            if (name.equals(s.get("name")))
                return s;
        throw new AssertionError("no side called " + name);
    }

    // ---------------------------------------------------------------- the edges

    /** The answer the panel was built to give: which way out is mine. */
    @Test
    void theExitEdgeIsTheComplementOfTheDeadlyOnes() {
        Map<String, Object> fed = sideNamed(briefing(HOST), "United Federation of Planets");

        assertEquals(List.of("RIGHT"), fed.get("exitEdges"));
        assertEquals(Set.of("BOTTOM", "LEFT", "TOP"),
                new java.util.HashSet<>((java.util.Collection<String>) fed.get("destructionEdges")));
    }

    /** And the other side's is its own, not a copy of the viewer's. */
    @Test
    void eachSideGetsItsOwnEdges() {
        Map<String, Object> kzinti = sideNamed(briefing(HOST), "Kzinti Hegemony");

        assertEquals(List.of("LEFT"), kzinti.get("exitEdges"));
    }

    /**
     * A side with no destruction edges may leave by any of the four. The common case: most
     * scenarios state none at all, and a panel that showed "no exit" there would be worse than
     * no panel.
     */
    @Test
    void aSideWithNoDeadlyEdgesMayLeaveByAnyOfThem() {
        ScenarioSpec open = spec();
        open.sides.get(0).destructionEdges = new java.util.HashSet<>();
        session.adoptRestored(open);

        Map<String, Object> fed = sideNamed(briefing(HOST), "United Federation of Planets");

        assertEquals(List.of("TOP", "BOTTOM", "LEFT", "RIGHT"), fed.get("exitEdges"));
    }

    // ---------------------------------------------------------------- whose side is whose

    @Test
    void theCallersOwnSideIsMarked() {
        Map<String, Object> asFed = briefing(HOST);
        assertEquals(Boolean.TRUE, sideNamed(asFed, "United Federation of Planets").get("yours"));
        assertEquals(Boolean.FALSE, sideNamed(asFed, "Kzinti Hegemony").get("yours"));

        Map<String, Object> asKzinti = briefing(P2);
        assertEquals(Boolean.TRUE, sideNamed(asKzinti, "Kzinti Hegemony").get("yours"));
        assertEquals(Boolean.FALSE,
                sideNamed(asKzinti, "United Federation of Planets").get("yours"));
    }

    /**
     * Without a token nothing is yours, and nothing else changes. A scenario is a printed page
     * both captains read before play, so the rules and both sides' edges are common knowledge
     * — the token decides only which side gets the badge.
     */
    @Test
    void anAnonymousCallerStillGetsTheWholeBriefing() {
        Map<String, Object> anon = briefing(null);

        assertEquals(Boolean.FALSE, sideNamed(anon, "Kzinti Hegemony").get("yours"));
        assertEquals(List.of("LEFT"), sideNamed(anon, "Kzinti Hegemony").get("exitEdges"));
        assertEquals("The Stasis Box", anon.get("name"));
    }

    // ---------------------------------------------------------------- the rest of the page

    @Test
    void theDescriptionRulesAndVictoryConditionsComeThrough() {
        Map<String, Object> b = briefing(HOST);

        assertEquals("The Stasis Box", b.get("name"));
        assertTrue(String.valueOf(b.get("description")).contains("asteroid field"));
        assertEquals("SPECIAL", b.get("victoryType"));
        assertTrue(String.valueOf(b.get("victoryNotes")).contains("Stasis Box"));
        assertEquals(1, ((List<?>) b.get("specialRules")).size());
    }

    /**
     * A game with no scenario answers with the same SHAPE rather than a 404, so the panel has
     * one thing to render. A straight duel has a briefing too; it is just empty.
     */
    @Test
    void aGameWithoutAScenarioStillAnswers() {
        GameSessionService service = new GameSessionService();
        GameController plain = new GameController(service, null);
        GameSession duel = service.createSession("Carol");
        String token = duel.getPlayers().keySet().iterator().next();

        ResponseEntity<?> response = plain.getBriefing(duel.getId(), token);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("", body.get("name"));
        assertTrue(((List<?>) body.get("sides")).isEmpty());
    }

    @Test
    void anUnknownGameIsNotFound() {
        assertEquals(404, controller.getBriefing("NOPE", HOST).getStatusCode().value());
    }
}
