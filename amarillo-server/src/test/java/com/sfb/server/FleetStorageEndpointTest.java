package com.sfb.server;

import com.sfb.scenario.FleetSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Saving, listing, loading and deleting fleets under data/fleets.
 * <p>
 * These write real files, so each test cleans up after itself and every fleet id is prefixed
 * so a stray one is obvious. The ids become filenames, which is why the traversal test matters
 * more than it looks: an id is the one piece of caller-supplied text that reaches the disk.
 */
class FleetStorageEndpointTest {

    private static final String PREFIX = "test-fleet-";
    private final GameController controller = new GameController(null, null);

    /**
     * The endpoints resolve data/ against the JVM's working directory, which is the repo root
     * when the server runs and the module when surefire does. Ships are seeded from here;
     * ShipLibrary keeps what it has when a path does not resolve, so the endpoint's own call
     * is a no-op. Fleets land in the module's own data/fleets, which is why every id is
     * prefixed and swept up afterwards.
     */
    @BeforeEach
    void loadShips() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
    }

    @AfterEach
    void cleanUp() {
        File dir = fleetDir();
        File[] mine = dir.listFiles((d, n) -> n.startsWith(PREFIX));
        if (mine != null)
            for (File f : mine)
                f.delete();
        // Leave no trace: the save created these directories, so remove them if they are
        // empty. A real one under the repo root is somebody's fleets and is never touched.
        String[] left = dir.list();
        if (left != null && left.length == 0) {
            dir.delete();
            dir.getParentFile().delete();
        }
    }

    /** The same relative path the controller uses, so cleanup finds what the save wrote. */
    private File fleetDir() {
        return new File("data/fleets");
    }

    private FleetSpec fleet(String id, String flagship, String... types) {
        FleetSpec spec = new FleetSpec();
        spec.id = id;
        spec.name = "Test fleet " + id;
        spec.author = "tester";
        spec.factions = new java.util.ArrayList<>(List.of("Klingon"));
        spec.year = 180;
        spec.budget = 900;
        spec.flagship = flagship;
        for (String t : types) {
            FleetSpec.ShipEntry e = new FleetSpec.ShipEntry();
            e.type = t;
            e.name = t;
            spec.ships.add(e);
        }
        return spec;
    }

    private Map<String, Object> body(Object response) {
        return (Map<String, Object>) ((org.springframework.http.ResponseEntity<?>) response).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listing() {
        return (List<Map<String, Object>>) controller.listFleets().getBody();
    }

    // ---- round trip ----

    @Test
    void aSavedFleetComesBackWithItsShips() {
        String id = PREFIX + "roundtrip";
        Map<String, Object> saved = body(controller.saveFleet(fleet(id, "D6", "D6", "D7", "D5")));
        assertEquals(id, saved.get("id"));

        Map<String, Object> loaded = body(controller.getFleet(id));
        FleetSpec back = (FleetSpec) loaded.get("fleet");

        assertEquals(3, back.ships.size());
        assertEquals("D6", back.flagship);
        assertEquals(180, back.year);
        assertEquals(900, back.budget);
        assertEquals(List.of("Klingon"), back.factions);
        assertFalse(back.updated == null || back.updated.isBlank(), "a save stamps the time");
    }

    @Test
    void aSavedFleetAppearsInTheListing() {
        String id = PREFIX + "listed";
        controller.saveFleet(fleet(id, "D6", "D6", "D7"));

        Map<String, Object> row = listing().stream()
                .filter(r -> id.equals(r.get("id")))
                .findFirst().orElseThrow(() -> new AssertionError("not listed"));

        assertEquals(2, row.get("shipCount"));
        assertEquals("tester", row.get("author"));
        assertNotNull(row.get("legal"), "the listing says whether it is still legal");
    }

    // ---- the verdict travels with the fleet ----

    @Test
    void anIllegalFleetIsSavedButMarked() {
        // Two leaders besides the flagship, with one CA between them to lead: S8.36 wants two.
        // The flagship must be the D6, since a leader flying the flag is exempt (S8.363).
        String id = PREFIX + "illegal";
        Map<String, Object> saved = body(controller.saveFleet(fleet(id, "D6", "D6", "D7C", "D7L")));

        @SuppressWarnings("unchecked")
        Map<String, Object> validation = (Map<String, Object>) saved.get("validation");
        assertEquals(Boolean.FALSE, validation.get("legal"), validation.toString());

        assertTrue(listing().stream().anyMatch(r -> id.equals(r.get("id"))),
                "an illegal fleet is still worth keeping");
    }

    @Test
    void aLegalFleetSavesClean() {
        String id = PREFIX + "legal";
        Map<String, Object> saved = body(controller.saveFleet(fleet(id, "D6", "D6", "D7", "D5")));

        @SuppressWarnings("unchecked")
        Map<String, Object> validation = (Map<String, Object>) saved.get("validation");
        assertEquals(Boolean.TRUE, validation.get("legal"), validation.toString());
    }

    // ---- ids reach the filesystem, so they are policed ----

    @Test
    void anIdThatWouldClimbOutOfTheDirectoryIsRefused() {
        for (String bad : new String[] { "../secret", "..", "a/b", "a\\b", "", "with space" }) {
            FleetSpec spec = fleet(bad, "D6", "D6");
            spec.name = "";   // so no slug is generated to rescue it
            spec.id = bad;
            var response = controller.saveFleet(spec);
            assertEquals(400, response.getStatusCode().value(), "should refuse id: " + bad);
        }
        assertEquals(400, controller.getFleet("../secret").getStatusCode().value(),
                "a bad id is refused, not merely missing");
    }

    @Test
    void anIdIsMadeFromTheNameWhenNoneIsGiven() {
        FleetSpec spec = fleet(null, "D6", "D6");
        spec.name = PREFIX + "From A Name";
        spec.id = null;

        Map<String, Object> saved = body(controller.saveFleet(spec));
        String id = String.valueOf(saved.get("id"));

        assertEquals(PREFIX + "from-a-name", id);
        assertTrue(new File(fleetDir(), id + ".json").isFile());
    }

    // ---- deletion ----

    @Test
    void aDeletedFleetIsGone() {
        String id = PREFIX + "doomed";
        controller.saveFleet(fleet(id, "D6", "D6"));
        assertEquals(200, controller.deleteFleet(id).getStatusCode().value());

        assertEquals(404, controller.getFleet(id).getStatusCode().value());
        assertFalse(listing().stream().anyMatch(r -> id.equals(r.get("id"))));
    }

    @Test
    void deletingSomethingThatWasNeverThereIsNotFound() {
        assertEquals(404, controller.deleteFleet(PREFIX + "never-existed").getStatusCode().value());
    }

    @Test
    void loadingSomethingThatWasNeverThereIsNotFound() {
        assertEquals(404, controller.getFleet(PREFIX + "never-existed").getStatusCode().value());
    }
}
