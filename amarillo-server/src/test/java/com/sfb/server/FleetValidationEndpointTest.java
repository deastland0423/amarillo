package com.sfb.server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fleet validation endpoint's own logic: resolving hulls through ShipLibrary, naming
 * duplicates apart, and reporting what the core validator found. The rules themselves are
 * pinned by FleetValidatorTest in core — this is the wire layer around them.
 */
class FleetValidationEndpointTest {

    /**
     * The endpoint loads "data/factions" relative to the repo root, which is where the server
     * runs (the pom sets that working directory). Surefire runs from the module, so load the
     * library from here first; ShipLibrary keeps what it has when a path does not resolve, so
     * the endpoint's own call is then a no-op.
     */
    @org.junit.jupiter.api.BeforeEach
    void loadShips() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
    }

    private GameController.FleetValidationRequest request(String flagship, String... hulls) {
        GameController.FleetValidationRequest r = new GameController.FleetValidationRequest();
        r.faction = "Federation";
        r.year = 175;
        r.budget = 500;
        r.flagship = flagship;
        r.hulls = List.of(hulls);
        return r;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validate(GameController.FleetValidationRequest r) {
        return (Map<String, Object>) // the endpoint is stateless: it touches neither the session service nor the broker
        new GameController(null, null).validateFleet(r).getBody();
    }

    @Test
    void aLegalFleetComesBackLegalWithItsCost() {
        Map<String, Object> body = validate(request("CC", "CC", "DD"));

        assertEquals(Boolean.TRUE, body.get("legal"), body.toString());
        assertEquals(2, body.get("shipCount"));
        assertTrue((Integer) body.get("cost") > 0, body.toString());
    }

    @Test
    void anUnknownHullIsReportedRatherThanIgnored() {
        Map<String, Object> body = validate(request("CC", "CC", "NOT-A-HULL"));

        assertEquals(Boolean.FALSE, body.get("legal"));
        assertTrue(body.get("unknownHulls").toString().contains("NOT-A-HULL"), body.toString());
    }

    @Test
    void overBudgetIsRefusedWithTheRuleNamed() {
        GameController.FleetValidationRequest r = request("CC", "CC", "CA", "DD");
        r.budget = 50;

        Map<String, Object> body = validate(r);

        assertEquals(Boolean.FALSE, body.get("legal"));
        assertTrue(body.get("violations").toString().contains("S8.11"), body.toString());
    }

    /** Several ships of one hull are normal, and each must be counted and charged for. */
    @Test
    void duplicateHullsAreCountedSeparately() {
        Map<String, Object> one = validate(request("CC", "CC", "DD"));
        Map<String, Object> two = validate(request("CC", "CC", "DD", "DD"));

        assertEquals(2, one.get("shipCount"));
        assertEquals(3, two.get("shipCount"));
        assertTrue((Integer) two.get("cost") > (Integer) one.get("cost"),
                "a second DD must cost something: " + one.get("cost") + " then " + two.get("cost"));
    }
}
