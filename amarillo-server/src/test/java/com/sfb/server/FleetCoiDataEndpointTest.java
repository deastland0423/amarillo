package com.sfb.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * What a fleet under construction may buy (S8.12), for the builder's own Commander's Options
 * dialog.
 *
 * <h2>Why this is a POST of the whole fleet</h2>
 * The fleet being edited is not saved and has no game behind it, so the spec has to travel in the
 * body. The answer is built by converting it into the scenario it would become and asking the one
 * COI builder, rather than a second implementation that would be free to disagree with the
 * endpoint a player meets later.
 */
class FleetCoiDataEndpointTest {

    @BeforeEach
    void setUp() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static com.sfb.scenario.FleetSpec fleetAt(int year, String... types) {
        com.sfb.scenario.FleetSpec spec = new com.sfb.scenario.FleetSpec();
        spec.name = "Test force";
        spec.year = year;
        spec.budget = 2000;
        spec.factions = new ArrayList<>(List.of("Kzinti"));
        spec.ships = new ArrayList<>();
        for (String type : types) {
            com.sfb.scenario.FleetSpec.ShipEntry e = new com.sfb.scenario.FleetSpec.ShipEntry();
            e.faction = "Kzinti";
            e.type = type;
            e.name = "KHS " + type;
            spec.ships.add(e);
        }
        spec.flagship = spec.ships.isEmpty() ? null : spec.ships.get(0).name;
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> shipRow(com.sfb.scenario.FleetSpec spec, String shipName) {
        ResponseEntity<List<Map<String, Object>>> response =
                new GameController(null, null).fleetCoiData(spec);
        assertEquals(200, response.getStatusCode().value(), "the endpoint must answer");
        List<Map<String, Object>> sides = response.getBody();
        assertNotNull(sides);
        assertEquals(1, sides.size(), "a fleet is one side");

        return ((List<Map<String, Object>>) sides.get(0).get("ships")).stream()
                .filter(s -> shipName.equals(s.get("shipName")))
                .findFirst().orElseThrow(() ->
                        new AssertionError(shipName + " not in " + sides));
    }

    // ---------------------------------------------------------------- it answers at all

    @Test
    void aFleetUnderConstructionIsToldWhatItMayBuy() {
        Map<String, Object> cs = shipRow(fleetAt(180, "CS"), "KHS CS");

        assertNotNull(cs.get("coiBudget"), "S3.2's allowance must reach the builder");
        assertTrue(((Number) cs.get("coiBudget")).doubleValue() > 0);
        assertNotNull(cs.get("droneRacks"), "and the racks it may load");
    }

    /** Every ship of the fleet is answered for, not just the first. */
    @SuppressWarnings("unchecked")
    @Test
    void everyShipInTheFleetGetsItsOwnAllowance() {
        ResponseEntity<List<Map<String, Object>>> response =
                new GameController(null, null).fleetCoiData(fleetAt(180, "CS", "FF"));
        List<Map<String, Object>> ships =
                (List<Map<String, Object>>) response.getBody().get(0).get("ships");

        assertEquals(2, ships.size(), "both hulls must be priced: " + ships);
        for (Map<String, Object> s : ships)
            assertNotNull(s.get("coiBudget"), s.get("shipName") + " needs an allowance");
    }

    // ---------------------------------------------------------------- the fleet's year binds

    /**
     * S8.131: the fleet's date decides what it is carrying, so the racks the builder shows are
     * the year's. Type-I-slow through Y166, medium from Y167, fast from Y180 (FD10.65).
     */
    @SuppressWarnings("unchecked")
    @Test
    void theRacksShowTheDroneSpeedTheFleetsYearFits() {
        for (Object[] row : new Object[][] {{160, "TypeI"}, {175, "TypeIM"}, {180, "TypeIF"}}) {
            Map<String, Object> cs = shipRow(fleetAt((Integer) row[0], "CS"), "KHS CS");
            List<Map<String, Object>> racks =
                    (List<Map<String, Object>>) cs.get("droneRacks");
            assertFalse(racks.isEmpty(), "a CS has drone racks");

            List<String> ammo = (List<String>) racks.get(0).get("defaultAmmo");
            assertTrue(ammo.contains(row[1]),
                    "Y" + row[0] + " should load " + row[1] + ", not " + ammo);
        }
    }

    /** And the ready-made loadouts come with it, priced for that year (S3.2). */
    @SuppressWarnings("unchecked")
    @Test
    void theReadyMadeLoadoutsComeWithIt() {
        Map<String, Object> cs = shipRow(fleetAt(180, "CS"), "KHS CS");
        List<Map<String, Object>> racks = (List<Map<String, Object>>) cs.get("droneRacks");

        List<Map<String, Object>> presets =
                (List<Map<String, Object>>) racks.get(0).get("presets");
        assertNotNull(presets, "the builder gets the same presets the lobby does");
        assertFalse(presets.isEmpty(), "a Kzinti rack has something to offer");
    }

    // ---------------------------------------------------------------- and what it leaves out

    /**
     * S8.134 settles weapon status when the battle is arranged, by die roll or agreement, so it
     * is a condition of the fight rather than part of what a fleet costs. At the default status
     * there is nothing to arm, and the builder is not asked to choose.
     */
    @Test
    void weaponStatusIsNotTheFleetBuildersBusiness() {
        Map<String, Object> cs = shipRow(fleetAt(180, "CS"), "KHS CS");
        Object ws = cs.get("weaponStatus");
        assertNotNull(ws);
        assertTrue(((Number) ws).intValue() < 3,
                "a fleet is bought below WS-3, so no arming modes are offered: " + ws);
    }

    /** An empty fleet is answered rather than refused; a builder starts empty. */
    @SuppressWarnings("unchecked")
    @Test
    void anEmptyFleetIsAnsweredWithNoShips() {
        ResponseEntity<List<Map<String, Object>>> response =
                new GameController(null, null).fleetCoiData(fleetAt(180));
        assertEquals(200, response.getStatusCode().value());
        List<Map<String, Object>> sides = response.getBody();
        assertNotNull(sides);
        if (!sides.isEmpty())
            assertTrue(((List<Map<String, Object>>) sides.get(0).get("ships")).isEmpty(),
                    "no ships bought yet means nothing to price");
    }
}
