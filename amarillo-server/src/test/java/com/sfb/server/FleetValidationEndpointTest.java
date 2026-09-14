package com.sfb.server;

import com.sfb.scenario.FleetSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fleet validation endpoint's own logic: resolving types through ShipLibrary, naming
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

    private FleetSpec request(String flagship, String... types) {
        FleetSpec spec = new FleetSpec();
        spec.factions = new java.util.ArrayList<>(List.of("Federation"));
        spec.year = 175;
        spec.budget = 500;
        spec.flagship = flagship;
        for (String t : types)
            spec.ships.add(shipOf(null, t));
        return spec;
    }

    /** One entry; a null faction means "the force's own", which is the common case. */
    private FleetSpec.ShipEntry shipOf(String faction, String type) {
        FleetSpec.ShipEntry e = new FleetSpec.ShipEntry();
        e.faction = faction;
        e.type = type;
        e.name = type;   // the picker names ships by type until a player renames them
        return e;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validate(FleetSpec spec) {
        return (Map<String, Object>) // the endpoint is stateless: it touches neither the session service nor the broker
        new GameController(null, null).validateFleet(spec).getBody();
    }

    /** A Klingon request, for the leader rules their two leader lines exercise. */
    private FleetSpec klingon(String flagship, String... types) {
        FleetSpec r = request(flagship, types);
        r.factions = new java.util.ArrayList<>(List.of("Klingon"));
        r.budget = 900;
        r.year = 180;
        return r;
    }

    /**
     * S8.36 against real hulls. The D7C leads the CA line and the F5C the DD line, so one of
     * them rides free and the other must find two non-leader consorts of its own line — from
     * the D6/D7 cruisers or the F5 destroyers, never from each other's pool.
     */
    @Test
    void twoLeadersAreLegalWhenOneOfThemHasItsConsorts() {
        Map<String, Object> body = validate(klingon("D6", "D6", "D7C", "F5C", "D7", "D7B"));

        assertEquals(Boolean.TRUE, body.get("legal"), body.toString());
    }

    @Test
    void twoLeadersWithNoConsortsAtAllAreRefused() {
        Map<String, Object> body = validate(klingon("D6", "D6", "D7C", "F5C"));

        assertEquals(Boolean.FALSE, body.get("legal"), body.toString());
        assertTrue(body.toString().contains("S8.36"), body.toString());
    }

    /**
     * Destroyers cannot stand in for a cruiser leader's consorts. Both leaders here are of the
     * CA line, so only one can ride free and the other has nothing to lead — three F5s
     * notwithstanding.
     */
    @Test
    void theWrongLineCannotServeALeader() {
        Map<String, Object> body = validate(klingon("F5", "F5", "D7C", "D7L", "F5B", "F5K"));

        assertEquals(Boolean.FALSE, body.get("legal"), body.toString());
        assertTrue(body.toString().contains("S8.36"), body.toString());
    }

    /**
     * S8.362's own worked example, in real hulls: "an F5L cannot be included in a fleet with a
     * D7C and a D5L unless the D7C is accompanied by two other D7/D6 combat ships and the D5L
     * is accompanied by two combat D5 hulls." Ours uses the F5C, which is the same shape — a
     * size class 4 leader below both of them, so it is the one that rides free.
     */
    @Test
    void theRulebooksThreeLeaderExampleIsLegalWhenBothLargerOnesAreAccompanied() {
        Map<String, Object> body = validate(klingon("D6",
                "D6", "D7C", "D5L", "F5C", "D7", "D5", "D5"));

        assertEquals(Boolean.TRUE, body.get("legal"), body.toString());
    }

    @Test
    void theSameExampleFailsWhenTheD5lIsOneConsortShort() {
        Map<String, Object> body = validate(klingon("D6",
                "D6", "D7C", "D5L", "F5C", "D7", "D5"));

        assertEquals(Boolean.FALSE, body.get("legal"), body.toString());
        assertTrue(body.toString().contains("S8.36"), body.toString());
        assertTrue(body.toString().contains("CW"), "the war cruiser line is the short one: " + body);
    }

    /** A Kzinti request, for the carrier group rules that only their hulls exercise. */
    private FleetSpec kzinti(String flagship, String... types) {
        FleetSpec r = request(flagship, types);
        r.factions = new java.util.ArrayList<>(List.of("Kzinti"));
        r.budget = 900;
        return r;
    }

    /**
     * S8.315 against real data: a size class 3 carrier needs two escorts of its own empire,
     * at least one of them size class 4. Until the Kzinti had an EFF there was no size class 4
     * escort in the game, so no carrier could legally take the field at all.
     */
    @Test
    void aKzintiCarrierGroupIsLegalWithAMecAndAnEff() {
        Map<String, Object> body = validate(kzinti("CC", "CC", "CV", "MEC", "EFF"));

        assertEquals(Boolean.TRUE, body.get("legal"), body.toString());
    }

    @Test
    void theCarrierNeedsASizeClassFourAmongItsEscorts() {
        // Two escorts, but both size class 3 — S8.315 wants one of them small.
        Map<String, Object> body = validate(kzinti("CC", "CC", "CV", "MEC", "MEC"));

        assertEquals(Boolean.FALSE, body.get("legal"), body.toString());
        assertTrue(body.toString().contains("size class 4"), body.toString());
    }

    /** S8.311: the escort cannot be bought on its own. */
    @Test
    void anEffWithoutACarrierIsRefused() {
        Map<String, Object> body = validate(kzinti("CC", "CC", "EFF"));

        assertEquals(Boolean.FALSE, body.get("legal"), body.toString());
        assertTrue(body.toString().contains("without a carrier"), body.toString());
    }

    @Test
    void aLegalFleetComesBackLegalWithItsCost() {
        Map<String, Object> body = validate(request("CC", "CC", "DD"));

        assertEquals(Boolean.TRUE, body.get("legal"), body.toString());
        assertEquals(2, body.get("shipCount"));
        assertTrue((Integer) body.get("cost") > 0, body.toString());
    }

    @Test
    void anUnknownTypeIsReportedRatherThanIgnored() {
        Map<String, Object> body = validate(request("CC", "CC", "NOT-A-TYPE"));

        assertEquals(Boolean.FALSE, body.get("legal"));
        assertTrue(body.get("unknownShips").toString().contains("NOT-A-TYPE"), body.toString());
    }

    @Test
    void overBudgetIsRefusedWithTheRuleNamed() {
        FleetSpec r = request("CC", "CC", "CA", "DD");
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
