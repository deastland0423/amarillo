package com.sfb.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * What the Commander's Options dialog is told about a squadron's rails (FD10.646, J4.23).
 *
 * <h2>Why the dialog is told rather than working it out</h2>
 * Which drones a rail will carry is J4.23 and FD9.35, and both live in {@code
 * DroneRail.accepts}. A client that re-derived them would be a second copy of the rule, free
 * to drift from the one that refuses the submission — and a dialog that offers a drone the
 * server then drops is worse than one that never offered it. So the payload carries answers
 * ({@code acceptsTypeIii}, {@code acceptsMultiWarhead}) rather than the rail type alone.
 *
 * <h2>The ordering this pins</h2>
 * A loadout addresses rails by POSITION, in {@code ReadyRack.stockedRails} order, because the
 * designators are not unique — a Federation F-14B carries four special rails designated E, F,
 * E, F. So the order the payload sends IS the address, and a podded rail missing from one end
 * and present at the other would put a type-III on a rail that refuses it.
 */
class FighterLoadoutEndpointTest {

    @BeforeEach
    void setUp() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        com.sfb.objects.ShuttleCatalog.loadDefault("../data");
    }

    private static com.sfb.scenario.FleetSpec fleetAt(int year, String faction, String... types) {
        com.sfb.scenario.FleetSpec spec = new com.sfb.scenario.FleetSpec();
        spec.name = "Test force";
        spec.year = year;
        spec.budget = 3000;
        spec.factions = new ArrayList<>(List.of(faction));
        spec.ships = new ArrayList<>();
        for (String type : types) {
            com.sfb.scenario.FleetSpec.ShipEntry e = new com.sfb.scenario.FleetSpec.ShipEntry();
            e.faction = faction;
            e.type = type;
            e.name = "Ship " + type;
            spec.ships.add(e);
        }
        spec.flagship = spec.ships.get(0).name;
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> shipRow(com.sfb.scenario.FleetSpec spec, String shipName) {
        ResponseEntity<List<Map<String, Object>>> response =
                new GameController(null, null).fleetCoiData(spec);
        assertEquals(200, response.getStatusCode().value(), "the endpoint must answer");
        List<Map<String, Object>> sides = response.getBody();
        assertNotNull(sides);
        return ((List<Map<String, Object>>) sides.get(0).get("ships")).stream()
                .filter(s -> shipName.equals(s.get("shipName")))
                .findFirst().orElseThrow(() -> new AssertionError(shipName + " not in " + sides));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadoutsOf(Map<String, Object> ship) {
        Object rows = ship.get("fighterLoadouts");
        assertNotNull(rows, "every ship row must carry the key, even if the list is empty");
        return (List<Map<String, Object>>) rows;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> railsOf(Map<String, Object> fighter) {
        return (List<Map<String, Object>>) fighter.get("rails");
    }

    private static Map<String, Object> firstOfType(List<Map<String, Object>> rows, String type) {
        return rows.stream().filter(f -> type.equals(f.get("type"))).findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " in " + rows));
    }

    // ---------------------------------------------------------------- it describes the squadron

    /** A Kzinti carrier in Y180 flies the TADS: two standard rails, two light, two special. */
    @Test
    void aCarrierSendsEveryFighterThatHasRails() {
        List<Map<String, Object>> rows = loadoutsOf(shipRow(fleetAt(180, "Kzinti", "CVS"), "Ship CVS"));
        assertEquals(12, rows.size(), "eleven TADS and the EW fighter beside them");

        Map<String, Object> tads = firstOfType(rows, "tads");
        assertEquals("TADS", tads.get("designation"), "what the band header SHOWS");
        assertNotNull(tads.get("name"), "the address a loadout is keyed by");
        assertNotNull(tads.get("squadron"), "J4.46's organisation, for the badge");

        List<Map<String, Object>> rails = railsOf(tads);
        assertEquals(6, rails.size());
        assertEquals(List.of("STANDARD", "STANDARD", "LIGHT", "LIGHT", "SPECIAL", "SPECIAL"),
                rails.stream().map(r -> r.get("railType")).toList());
    }

    /**
     * J4.962 takes a podded rail out of the rack, so it is not an address a loadout can use.
     *
     * <blockquote>J4.962: "An EWP replaces one drone carried by the fighter."</blockquote>
     *
     * <p>A TADS-E has the same six rails as a TADS and two pods on them, so it sends four
     * entries. This is the case that makes position-addressing worth pinning: a client that
     * assumed six would hang its last two choices off the end of the list.
     */
    @Test
    void aPoddedRailIsNotAnAddress() {
        List<Map<String, Object>> rows = loadoutsOf(shipRow(fleetAt(180, "Kzinti", "CVS"), "Ship CVS"));
        Map<String, Object> ew = firstOfType(rows, "tads_e");
        assertEquals(4, railsOf(ew).size(),
                "six rails less the two the EW pods took (J4.962)");
    }

    /**
     * Each rail says what it is carrying, read from the rail BEFORE the rack.
     *
     * <p>Not from the rack alone, which was the first attempt: a fighter armed by its weapon
     * status (S4.10-S4.12) has taken its drones OUT of the rack (J4.8224), so a Kzinti TADS
     * at WS-2 holds five on its rails and one in the box. Reading the rack reported five of
     * its six rails carrying nothing.
     *
     * <p>The speed is what makes this testable. S2.11 fits the year's drones, so in Y180 a
     * rail is carrying a type-I-FAST while its DESIGN is still the bare type-I the frame was
     * built around — so the two possible answers are different strings, and a payload that
     * fell back to the design would say "TypeI" where the fighter is holding a "TypeIF". That
     * is the same gap a purchased module falls through, and the one a player would read as
     * their ECM drone having vanished.
     */
    @Test
    void eachRailSaysWhatItIsCarryingRatherThanWhatItWasDesignedFor() {
        List<Map<String, Object>> rows = loadoutsOf(shipRow(fleetAt(180, "Kzinti", "CVS"), "Ship CVS"));
        List<Map<String, Object>> rails = railsOf(firstOfType(rows, "tads"));

        for (Map<String, Object> r : rails)
            assertNotNull(r.get("drone"), "a stocked rail always has something in its slot");
        assertEquals("TypeIF", rails.get(0).get("drone"),
                "S2.11: a Y180 standard rail carries the year's drone, not the frame's");
        assertTrue(((String) rails.get(2).get("drone")).startsWith("TypeVI"),
                "a light rail is issued a dogfight drone: " + rails.get(2).get("drone"));
        assertTrue(((String) rails.get(4).get("drone")).startsWith("TypeI"),
                "FD10.646: a SPECIAL rail is issued an ordinary drone too, not a type-III: "
                        + rails.get(4).get("drone"));
    }

    /**
     * Every fighter of a type is issued the same drones, which is what a player sees first.
     *
     * <h2>The bug this pins</h2>
     * Reported from the panel: "the last few fighters do not have the mandatory speed upgrade
     * and so are different". They were the ones the weapon status had not armed. An armed
     * fighter answers from its RAIL, which carries the year's drone; an unarmed one answers
     * from its ready rack, matched against the rail's DESIGN — and the design was still naming
     * the frame's type-I-slow because nothing refitted it (S2.11). The match failed, the
     * payload fell back to the design, and half the squadron reported a drone it was not
     * carrying.
     *
     * <p>Asserted across the whole band rather than on one fighter, because that is exactly
     * what the first version of this suite got wrong: it checked the FIRST TADS, which is
     * armed, and passed while six of its squadron-mates were wrong.
     */
    @Test
    void everyFighterOfATypeIsIssuedTheSameDrones() {
        List<Map<String, Object>> rows = loadoutsOf(shipRow(fleetAt(180, "Kzinti", "CVS"), "Ship CVS"));

        Map<String, List<String>> byType = new java.util.LinkedHashMap<>();
        for (Map<String, Object> f : rows) {
            List<String> load = railsOf(f).stream().map(r -> (String) r.get("drone")).toList();
            String type = (String) f.get("type");
            List<String> first = byType.putIfAbsent(type, load);
            if (first != null)
                assertEquals(first, load,
                        f.get("name") + " is issued something its squadron-mates are not");
        }
        assertEquals(List.of("TypeIF", "TypeIF", "TypeVIF", "TypeVIF", "TypeIF", "TypeIF"),
                byType.get("tads"),
                "S2.11: a Y180 TADS flies fast drones on every rail, armed or not");
    }

    /**
     * Each fighter names the counter the MAP draws for it, so the loadout grid and the board
     * show the same object.
     *
     * <p>And names nothing where nothing has been drawn. 22 of the game's 78 fighters have no
     * counter — the whole Federation line and the Hydran Stinger-3 — so the field has to be
     * able to say so, and the grid falls back to a plain shape exactly as the map does. A
     * primitive would have arrived as "" on every row and the client could not have told.
     */
    @Test
    void eachFighterNamesTheCounterTheMapDrawsForIt() {
        Map<String, Object> tads = firstOfType(
                loadoutsOf(shipRow(fleetAt(180, "Kzinti", "CVS"), "Ship CVS")), "tads");
        assertEquals("kzinti/tads.png", tads.get("tokenArt"),
                "the same art HexGrid loads from /tokens/");

        List<Map<String, Object>> fed =
                loadoutsOf(shipRow(fleetAt(180, "Federation", "CVS"), "Ship CVS"));
        assertFalse(fed.isEmpty(), "fixture: the Federation carrier has a squadron");
        assertNull(fed.get(0).get("tokenArt"),
                "the Federation fighters have no counter drawn yet, and must say so");
    }

    // ---------------------------------------------------------------- and what each rail allows

    /**
     * FD9.35 and J4.23, sent as answers. The standard rail is the whole of the exception:
     * it carries special type-I drones and refuses the type-III frame and the MW drone.
     */
    @Test
    void theRailsSayWhatTheyWillAcceptSoTheClientNeedNotGuess() {
        List<Map<String, Object>> rails =
                railsOf(firstOfType(loadoutsOf(shipRow(fleetAt(180, "Kzinti", "CVS"), "Ship CVS")),
                        "tads"));

        Map<String, Object> standard = rails.get(0);
        assertEquals(Boolean.FALSE, standard.get("acceptsTypeIii"), "FD9.35");
        assertEquals(Boolean.FALSE, standard.get("acceptsMultiWarhead"), "J4.23");

        Map<String, Object> light = rails.get(2);
        assertEquals(Boolean.FALSE, light.get("acceptsTypeIii"),
                "a light rail is half a space and takes nothing but a type-VI");

        Map<String, Object> special = rails.get(4);
        assertEquals(Boolean.TRUE, special.get("acceptsTypeIii"), "J4.233");
        assertEquals(Boolean.TRUE, special.get("acceptsMultiWarhead"), "J4.23");
    }

    // ---------------------------------------------------------------- and the ships without one

    /**
     * The hull the whole surface exists for: a Klingon carrier conversion keeps no drone rack
     * at all, so its squadron is the only place a special drone can go.
     */
    @Test
    void aKlingonCarriersOnlyDroneCapacityIsItsSquadron() {
        Map<String, Object> ship = shipRow(fleetAt(180, "Klingon", "D7V"), "Ship D7V");
        assertTrue(((List<?>) ship.get("droneRacks")).isEmpty(),
                "the conversion strips the D7's three racks");
        assertFalse(loadoutsOf(ship).isEmpty(),
                "and every drone it can carry is on a fighter");
    }

    /** A ship with no fighters sends an empty list, not a missing key. */
    @Test
    void aShipWithNoFightersSendsAnEmptyList() {
        assertTrue(loadoutsOf(shipRow(fleetAt(180, "Kzinti", "CM"), "Ship CM")).isEmpty());
    }
}
