package com.sfb.server;

import com.sfb.scenario.ScenarioSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Taking a save of a live game through the REST surface.
 *
 * <h2>Two rules this pins, both of which are design decisions rather than plumbing</h2>
 * A save may only be taken BETWEEN TURNS. That is not an apology for a limitation: almost
 * everything the engine holds by object identity rather than by name — who moved this impulse,
 * pending volleys, pending DAC choices — is empty at a turn boundary and would otherwise have to
 * be serialised faithfully. Refusing mid-turn is what keeps the format small enough to be right,
 * so the refusal has to be reliable and has to say which moment to use.
 *
 * <p>And a save id becomes a FILENAME, so it is held to a shape that cannot climb out of
 * {@code data/saves} — the same guard the saved fleets use, refusing a bad id rather than
 * quietly sanitising it into a different one.
 */
class SaveGameEndpointTest {

    private GameSessionService service;
    private GameController controller;
    private GameSession session;
    private String host;
    private final List<File> written = new ArrayList<>();

    @BeforeEach
    void setUp() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        service = new GameSessionService();
        controller = new GameController(service, null);
        session = service.createSession("Dan", "account-dan");
        host = session.getHostToken();
    }

    /**
     * Delete every save this test could have written, tracked or not.
     *
     * Sweeping the whole {@code test-save-*} prefix rather than only the tracked files, because a
     * test that asserts a save was REFUSED does not track a file it never expected to exist — and
     * if the refusal ever breaks, that untracked file is exactly what gets left in the repo. The
     * directory is gitignored as well, so a leftover cannot be committed either way.
     */
    @AfterEach
    void tidy() {
        for (File f : written)
            if (f.isFile() && !f.delete())
                f.deleteOnExit();
        File dir = new File("data/saves");
        String generatedPrefix = session.getId().toLowerCase() + "-turn-";
        File[] strays = dir.listFiles((d, n) ->
                n.startsWith("test-save-") || n.startsWith(generatedPrefix));
        if (strays != null)
            for (File f : strays)
                if (!f.delete())
                    f.deleteOnExit();
    }

    /** A started battle, sitting in energy allocation — which is to say, at a turn boundary. */
    private void startBattle() {
        session.loadBuiltScenario(spec(), "save-fixture");
        session.assignShip(host, "USS Hood");
        session.assignShip(host, "IKV Blade");
        session.setDeploymentDone(host, true);
        session.submitCoi(host, Map.of());
        try {
            session.start();
        } catch (Exception e) {
            throw new AssertionError("fixture: the battle should start", e);
        }
        assertTrue(session.isStarted(), "fixture: the battle should be under way");
    }

    private ScenarioSpec spec() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Endpoint fixture";
        spec.year = 180;
        spec.mapCols = 42;
        spec.mapRows = 32;

        ScenarioSpec.SideSpec fed = new ScenarioSpec.SideSpec();
        fed.faction = "Federation";
        fed.name = "Starfleet";
        fed.ships = new ArrayList<>(List.of(ship("Federation", "CA", "USS Hood", "1010")));

        ScenarioSpec.SideSpec klingon = new ScenarioSpec.SideSpec();
        klingon.faction = "Klingon";
        klingon.name = "Empire";
        klingon.ships = new ArrayList<>(List.of(ship("Klingon", "D7", "IKV Blade", "1016")));

        spec.sides = new ArrayList<>(List.of(fed, klingon));
        return spec;
    }

    private static ScenarioSpec.ShipSetup ship(String faction, String type, String name,
            String hex) {
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.faction = faction;
        setup.type = type;
        setup.shipName = name;
        setup.startHex = hex;
        setup.startHeading = "A";
        return setup;
    }

    private File savedFile(String id) {
        File f = new File("data/saves", id + ".json");
        written.add(f);
        return f;
    }

    // ---------------------------------------------------------------- taking a save

    @Test
    void aHostCanSaveABattleBetweenTurns() {
        startBattle();

        var response = controller.saveGame(session.getId(), host,
                Map.of("id", "test-save-alpha", "label", "Alpha"));

        assertEquals(200, response.getStatusCode().value(), String.valueOf(response.getBody()));
        assertEquals("test-save-alpha", response.getBody().get("id"));
        assertTrue(savedFile("test-save-alpha").isFile(), "the file should be on disk");
    }

    /** The file has to be a real, re-readable save — not just bytes. */
    @Test
    void theWrittenFileCanBeReadBackAndRebuilt() throws Exception {
        startBattle();
        controller.saveGame(session.getId(), host, Map.of("id", "test-save-beta"));

        com.sfb.scenario.SavedGame reread =
                com.sfb.scenario.SavedGame.fromJson(savedFile("test-save-beta"));

        assertNotNull(reread.scenario, "the scenario must travel inside the save");
        assertEquals("Endpoint fixture", reread.scenario.name);
        assertNotNull(reread.state);

        // ShipLibrary is loaded from the server's working directory, so restore() here finds the
        // hulls the same way the running server would.
        com.sfb.scenario.SavedGame.Restored restored = reread.restore();
        assertNotNull(restored.game());
        assertEquals(List.of(), restored.problems());
        assertEquals(2, restored.game().getShips().size());
    }

    @Test
    void aSaveWithoutAnIdGetsOneFromTheGame() {
        startBattle();

        var response = controller.saveGame(session.getId(), host, null);

        assertEquals(200, response.getStatusCode().value());
        String id = String.valueOf(response.getBody().get("id"));
        assertTrue(id.contains(session.getId().toLowerCase()), "id should name the game: " + id);
        assertTrue(savedFile(id).isFile());
    }

    // ---------------------------------------------------------------- refusals

    /**
     * The turn-boundary rule. Mid-turn, the engine is full of per-impulse state held by object
     * identity, which this format deliberately does not carry.
     */
    @Test
    void savingMidTurnIsRefusedAndSaysWhen() {
        startBattle();
        // Walk off the allocation boundary by allocating for both ships.
        for (com.sfb.objects.Ship ship : session.getGame().getShips())
            session.getGame().submitAllocation(ship, ship.buildAutoAllocation());
        assertFalse(session.getGame().isAwaitingAllocation(), "fixture: the turn is under way");

        var response = controller.saveGame(session.getId(), host, Map.of("id", "test-save-mid"));

        assertEquals(400, response.getStatusCode().value());
        String error = String.valueOf(response.getBody().get("error"));
        assertTrue(error.contains("between turns"), error);
        assertFalse(savedFile("test-save-mid").isFile(), "nothing should have been written");
    }

    @Test
    void savingBeforeTheBattleStartsIsRefused() {
        session.loadBuiltScenario(spec(), "save-fixture");

        var response = controller.saveGame(session.getId(), host, Map.of("id", "test-save-early"));

        assertEquals(400, response.getStatusCode().value());
        assertFalse(savedFile("test-save-early").isFile());
    }

    @Test
    void onlyTheHostCanSave() {
        startBattle();
        String guest = service.joinSession(session.getId(), "Dave", "account-dave");

        var response = controller.saveGame(session.getId(), guest,
                Map.of("id", "test-save-guest"));

        assertEquals(403, response.getStatusCode().value());
        assertFalse(savedFile("test-save-guest").isFile());
    }

    /**
     * A save id becomes a filename. A traversal attempt is REFUSED rather than sanitised, so the
     * caller is told its id was wrong instead of quietly getting a different one.
     */
    @Test
    void anIdThatCouldClimbOutOfTheDirectoryIsRefused() {
        startBattle();

        // An EMPTY id is deliberately absent from this list: blank means "pick one for me", which
        // aSaveWithoutAnIdGetsOneFromTheGame covers. Only ids that could become a path are bad.
        for (String bad : List.of("../escape", "..", "a/b", "with space", "has.dot", "-leading")) {
            var response = controller.saveGame(session.getId(), host, Map.of("id", bad));
            assertEquals(400, response.getStatusCode().value(), "should refuse id: " + bad);
        }
        assertFalse(new File("data/escape.json").exists(), "nothing outside data/saves");
    }

    @Test
    void anUnknownGameIsNotFound() {
        assertEquals(404, controller.saveGame("ZZZZ", host, Map.of()).getStatusCode().value());
    }

    // ---------------------------------------------------------------- listing and deleting

    @Test
    void aSavedBattleAppearsInTheList() {
        startBattle();
        controller.saveGame(session.getId(), host, Map.of("id", "test-save-listed",
                "label", "In the list"));

        List<Map<String, Object>> saves = controller.listSaves().getBody();

        assertTrue(saves.stream().anyMatch(row -> "test-save-listed".equals(row.get("id"))
                        && "In the list".equals(row.get("label"))
                        && Boolean.TRUE.equals(row.get("readable"))),
                "the save should be listed: " + saves);
    }

    @Test
    void deletingASaveRemovesTheFile() {
        startBattle();
        controller.saveGame(session.getId(), host, Map.of("id", "test-save-doomed"));
        assertTrue(savedFile("test-save-doomed").isFile());

        var response = controller.deleteSave("test-save-doomed");

        assertEquals(200, response.getStatusCode().value());
        assertFalse(savedFile("test-save-doomed").isFile());
    }

    @Test
    void deletingSomethingThatIsNotThereIsNotFound() {
        assertEquals(404, controller.deleteSave("test-save-absent").getStatusCode().value());
        assertEquals(400, controller.deleteSave("../etc").getStatusCode().value());
    }
}
