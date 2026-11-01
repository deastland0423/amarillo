package com.sfb.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A written situation's date is binding (S8.131).
 *
 * <h2>The hole this closes</h2>
 * The lobby already revalidates every fleet against the host's conditions — "The host's
 * conditions govern, not the ones the fleet remembers" — and {@code FleetValidator} already
 * refuses a ship that does not enter service until after the scenario date. Both correct, and
 * between them was one branch: a host who picked a written situation and named no year of their
 * own had each fleet judged against ITS OWN year, while the battle ran at the situation's. A
 * fleet assembled for Y180 could be declared legal and then fight a Y168 scenario carrying
 * hulls that would not exist for another decade.
 *
 * <p>Since S2.11's drone speed is fitted from the scenario's year too, that disagreement now
 * arms a fleet for one decade and judges it in another.
 *
 * <h2>And a situation need not have a date at all</h2>
 * "A convoy meets a raider" is a situation, not a moment. A scenario that states no year leaves
 * the date to the players, who settle it when they build their fleets — which is the same
 * "0 means not said" convention the rest of the scenario code keeps.
 *
 * <p>These write real files under the module's own data/, the way {@code FleetStorageEndpointTest}
 * does, because the thing being tested is the wiring between them and a fixture that bypassed
 * the loader would prove the parts and not the join.
 */
class FleetYearBindingTest {

    private static final String PREFIX = "test-yearbind-";
    /** The Klingon C7 enters service in Y177: legal in Y180, not in Y168. */
    private static final String LATE_HULL = "C7";

    private GameController controller;
    private GameSession session;
    private String hostToken;

    @BeforeEach
    void setUp() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        GameSessionService service = new GameSessionService();
        controller = new GameController(service, null);
        session = service.createSession("dan");
        hostToken = session.getPlayers().keySet().iterator().next();

        writeFleet();
        writeScenario(PREFIX + "dated", 168);
        writeScenario(PREFIX + "undated", 0);
    }

    @AfterEach
    void cleanUp() {
        sweep(new File("data/fleets"));
        sweep(new File("data/scenarios"));
    }

    private void sweep(File dir) {
        File[] mine = dir.listFiles((d, n) -> n.startsWith(PREFIX));
        if (mine != null)
            for (File f : mine)
                f.delete();
        // Leave no trace, as FleetStorageEndpointTest does: these fixtures created the
        // directories, so take them away again when nothing else put anything in them.
        String[] left = dir.list();
        if (left != null && left.length == 0)
            dir.delete();
    }

    /** A fleet of one named hull, so a test can choose whether the date is the deciding fact. */
    private void writeFleet(String id, String hull) throws IOException {
        File dir = new File("data/fleets");
        dir.mkdirs();
        Files.writeString(new File(dir, id + ".json").toPath(), """
                {
                  "id": "%s",
                  "name": "Klingons",
                  "factions": ["Klingon"],
                  "year": 180,
                  "budget": 1000,
                  "flagship": "%s",
                  "ships": [ { "type": "%s", "name": "%s" } ]
                }
                """.formatted(id, hull, hull, hull), StandardCharsets.UTF_8);
    }

    /** One Klingon C7 — a hull from Y177, so the fleet's legality turns entirely on the date. */
    private void writeFleet() throws IOException {
        File dir = new File("data/fleets");
        dir.mkdirs();
        Files.writeString(new File(dir, PREFIX + "klingons.json").toPath(), """
                {
                  "id": "%sklingons",
                  "name": "Late Klingons",
                  "factions": ["Klingon"],
                  "year": 180,
                  "budget": 1000,
                  "flagship": "%s",
                  "ships": [ { "type": "%s", "name": "%s" } ]
                }
                """.formatted(PREFIX, LATE_HULL, LATE_HULL, LATE_HULL), StandardCharsets.UTF_8);
    }

    /** A situation with one side waiting for a fleet, dated or not. */
    private void writeScenario(String id, int year) throws IOException {
        File dir = new File("data/scenarios");
        dir.mkdirs();
        String yearLine = year > 0 ? "\"year\": " + year + "," : "";
        Files.writeString(new File(dir, id + ".json").toPath(), """
                {
                  "id": "%s",
                  "name": "Binding test",
                  %s
                  "numPlayers": 1,
                  "mapCols": 42,
                  "mapRows": 32,
                  "sides": [
                    { "name": "Klingons", "bringYourOwn": true,
                      "deploymentZone": { "shape": "BAND", "edge": "LEFT", "depth": 4 } }
                  ]
                }
                """.formatted(id, yearLine), StandardCharsets.UTF_8);
    }

    private ResponseEntity<Map<String, Object>> loadFleets(String scenarioId, int year) {
        return loadFleets(scenarioId, year, PREFIX + "klingons");
    }

    private ResponseEntity<Map<String, Object>> loadFleets(
            String scenarioId, int year, String fleetId) {
        GameController.FleetGameRequest req = new GameController.FleetGameRequest();
        req.year = year;
        req.budget = 1000;
        req.terrain = "OPEN_SPACE";
        req.terrainSeed = 1L;
        GameController.FleetSideRequest side = new GameController.FleetSideRequest();
        side.fleetId = fleetId;
        side.team = "Klingons";
        req.sides = new java.util.ArrayList<>(List.of(side));
        req.scenarioId = scenarioId;
        return controller.loadFleets(session.getId(), hostToken, req);
    }

    // ------------------------------------------------------------------ a dated situation

    /**
     * The branch that was open: a Y180 fleet, a Y168 situation, and a host who named no year of
     * their own. The fleet used to be judged against its own 180 and waved through.
     */
    @Test
    void aDatedSituationRefusesAFleetFromALaterDecade() {
        ResponseEntity<Map<String, Object>> res = loadFleets(PREFIX + "dated", 0);

        assertEquals(400, res.getStatusCode().value(),
                "a Y177 hull cannot be at a Y168 battle");
        assertTrue(res.getBody().toString().contains("S8.131"),
                "and the refusal should cite the rule: " + res.getBody());
    }

    /** The host naming the situation's own year is the ordinary path, and refuses it too. */
    @Test
    void namingTheSituationsOwnYearRefusesItAsWell() {
        assertEquals(400, loadFleets(PREFIX + "dated", 168).getStatusCode().value());
    }

    /**
     * And the host cannot play a dated situation in a different decade by typing over the year,
     * which is what "binding" means. Said out loud in the response rather than silently applied:
     * being judged against a year you did not ask for would otherwise look like a mystery.
     */
    @Test
    void aHostCannotOverrideADatedSituation() {
        ResponseEntity<Map<String, Object>> res = loadFleets(PREFIX + "dated", 180);

        assertEquals(400, res.getStatusCode().value(),
                "Y180 was asked for, but the situation is set in Y168");
        Object note = res.getBody().get("yearNote");
        assertNotNull(note, "the response must say which year governed: " + res.getBody());
        assertTrue(note.toString().contains("Y168") && note.toString().contains("Y180"),
                "naming both years: " + note);
    }

    // ------------------------------------------------------------------ an undated situation

    /**
     * A situation with no date leaves it to the players: the host's year governs, and the same
     * fleet is welcome.
     */
    @Test
    void anUndatedSituationLeavesTheYearToThePlayers() {
        ResponseEntity<Map<String, Object>> res = loadFleets(PREFIX + "undated", 180);

        assertEquals(200, res.getStatusCode().value(), "Y180 is fine for a Y177 hull: "
                + res.getBody());
        assertNull(res.getBody().get("yearNote"), "nothing was overruled");
    }

    /** And the year they chose is the year the battle is actually fought in (S2.11). */
    @Test
    void thePlayersYearReachesTheBattle() {
        assertEquals(200, loadFleets(PREFIX + "undated", 180).getStatusCode().value());

        assertEquals(180, new LobbyStateDto(session).scenarioYear,
                "the drones and refits are fitted from this");
    }

    /** A dated situation puts ITS year on the battle, not the one the fleet remembers. */
    @Test
    void aDatedSituationPutsItsOwnYearOnTheBattle() throws Exception {
        writeScenario(PREFIX + "dated180", 180);
        ResponseEntity<Map<String, Object>> res = loadFleets(PREFIX + "dated180", 0);

        assertEquals(200, res.getStatusCode().value(), res.getBody().toString());
        assertEquals(180, new LobbyStateDto(session).scenarioYear);
    }

    /**
     * The quiet half of the same surprise, and the one a first pass at this missed: a fleet that
     * is legal in EITHER year, so nothing is refused and only the battle's own date says which
     * year won.
     *
     * <p>Judging the fleets against the situation while building the battle from the host's
     * number would pass every test above — the fleet is checked at Y180 and waved through — and
     * then fight in Y165, with its drones fitted and its refits applied for a decade nobody
     * chose. A D7 is a Y135 hull, so the date decides nothing about legality here and everything
     * about what the ships are.
     */
    @Test
    void aLegalFleetStillFightsInTheSituationsYear() throws Exception {
        writeFleet(PREFIX + "old", "D7");
        writeScenario(PREFIX + "dated180", 180);

        ResponseEntity<Map<String, Object>> res =
                loadFleets(PREFIX + "dated180", 165, PREFIX + "old");

        assertEquals(200, res.getStatusCode().value(), res.getBody().toString());
        assertEquals(180, new LobbyStateDto(session).scenarioYear,
                "Y165 was asked for; the situation is set in Y180");
        assertNotNull(res.getBody().get("yearNote"),
                "and a host whose year was overruled is told so on the way in");
    }
}
