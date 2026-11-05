package com.sfb.server;

import com.sfb.scenario.FleetSpec;
import com.sfb.scenario.CoiChoices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The drone caps on the wire (FD10.622/FD10.632): a verdict carries the GAUGE as well as the no.
 *
 * <h2>Why the figures are sent at all</h2>
 * The Commander's Options form has to show how close the next drone takes a ship, and the sum
 * behind that is four rules deep — FD10.641's per-space tier bill, FD10.66's half-space sharing,
 * FD10.643's type-G exception, FD10.622's Klingon clause. A browser-side copy would be a second
 * rules engine with no test able to prove it had drifted, so core computes and the view renders.
 *
 * <p>Which makes these fields the contract between them. The caps themselves are pinned in core
 * by {@code DroneAvailabilityLimitTest} and {@code FleetDroneAvailabilityTest}; this is the wire
 * layer, and the one thing it can get wrong that core cannot is sending a figure the form then
 * reads as something else.
 */
class FleetDroneAvailabilityEndpointTest {

    @BeforeEach
    void loadShips() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
    }

    /** A Federation FFG: one type-G rack of four spaces, and no relaxed cap to its name. */
    private FleetSpec ffgFleet(int year, String... rackZero) {
        FleetSpec spec = new FleetSpec();
        spec.factions = new java.util.ArrayList<>(List.of("Federation"));
        spec.year = year;
        spec.budget = 2000;
        spec.flagship = "FFG";

        FleetSpec.ShipEntry e = new FleetSpec.ShipEntry();
        e.type = "FFG";
        e.name = "FFG";
        if (rackZero.length > 0) {
            e.coi = new CoiChoices();
            e.coi.droneRackLoadouts.put("0", List.of(rackZero));
        }
        spec.ships.add(e);
        return spec;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowFor(FleetSpec spec) {
        Map<String, Object> body = (Map<String, Object>)
                new GameController(null, null).validateFleet(spec).getBody();
        assertNotNull(body);
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) body.get("droneAvailability");
        assertNotNull(rows, "the verdict must carry the gauge: " + body);
        assertEquals(1, rows.size(), "one ship, one row");
        return rows.get(0);
    }

    /**
     * Every field the form reads, on a ship carrying nothing special.
     *
     * <p>Asserted by name because the form indexes them by name, and a renamed or missing key
     * arrives in TypeScript as {@code undefined} rather than as an error — which would read as
     * "no cap" and quietly unlock a control that is meant to lock.
     */
    @Test
    void anOrdinaryShipReportsItsCeilingsAndSpendsNoneOfThem() {
        Map<String, Object> row = rowFor(ffgFleet(180, "TypeI", "TypeI", "TypeI", "TypeI"));

        assertEquals("FFG", row.get("shipName"));
        assertEquals(4.0, ((Number) row.get("countedSpaces")).doubleValue(), 1e-9);
        assertEquals(0.0, ((Number) row.get("restrictedUsed")).doubleValue(), 1e-9);
        assertEquals(1L, ((Number) row.get("restrictedAllowed")).longValue());
        assertEquals(25, ((Number) row.get("restrictedPercent")).intValue());
        assertEquals(0.0, ((Number) row.get("limitedUsed")).doubleValue(), 1e-9);
        assertEquals(0L, ((Number) row.get("limitedAllowed")).longValue(),
                "10% of four spaces rounds down to none (FD10.641)");
        assertEquals(10, ((Number) row.get("limitedPercent")).intValue());
        assertEquals(false, row.get("relaxed"));
    }

    /**
     * The figures follow the DRAFT loadout, which is the whole point of posting a hypothetical
     * fleet. One type-III is a Limited space (FD10.631) against an allowance of none.
     */
    @Test
    void theGaugeMovesWithWhatTheFleetBought() {
        Map<String, Object> row = rowFor(ffgFleet(180, "TypeIII", "TypeI", "TypeI", "TypeI"));

        assertEquals(1.0, ((Number) row.get("limitedUsed")).doubleValue(), 1e-9);
        assertEquals(0L, ((Number) row.get("limitedAllowed")).longValue());
        assertEquals(1.0, ((Number) row.get("restrictedUsed")).doubleValue(), 1e-9,
                "FD10.64 nests the caps, so it spends Restricted allowance too");
    }

    /**
     * And the refusal travels in the same response, filed under its own clause.
     *
     * <p>Two readers of one verdict: the roster shows the violation, the options form shows the
     * figures. They have to agree, and they do because both come out of the same resolve.
     */
    @SuppressWarnings("unchecked")
    @Test
    void theSameVerdictAlsoRefusesTheFleet() {
        Map<String, Object> body = (Map<String, Object>) new GameController(null, null)
                .validateFleet(ffgFleet(180, "TypeIII", "TypeI", "TypeI", "TypeI")).getBody();
        assertNotNull(body);
        assertEquals(false, body.get("legal"));

        List<Map<String, Object>> violations =
                (List<Map<String, Object>>) body.get("violations");
        assertTrue(violations.stream().anyMatch(v -> "FD10.632".equals(v.get("rule"))
                        && "ERROR".equals(v.get("severity"))
                        && "FFG".equals(v.get("shipName"))),
                "the breach must arrive as an ERROR naming the ship: " + violations);
    }

    /**
     * FD10.643's type-G exception, end to end: "one restricted availability drone space and
     * three spaces of type-VI or ADDs or a combination of the two".
     *
     * <p>The six type-VIs supply three of the four counted spaces, which is what makes the ECM
     * drone legal. Before the exception was built this same request answered with one counted
     * space and refused the loadout the rulebook prints as an example.
     */
    @Test
    void aTypeGCountsItsDogfightDronesTowardTheDenominator() {
        Map<String, Object> row = rowFor(ffgFleet(180, "TypeI+ECM",
                "TypeVI", "TypeVI", "TypeVI", "TypeVI", "TypeVI", "TypeVI"));

        assertEquals(4.0, ((Number) row.get("countedSpaces")).doubleValue(), 1e-9);
        assertEquals(1.0, ((Number) row.get("restrictedUsed")).doubleValue(), 1e-9);
        assertEquals(1L, ((Number) row.get("restrictedAllowed")).longValue());
    }

    /** A Kzinti hull is on FD10.622's exception list, and says so for the form's tooltip. */
    @Test
    void aKzintiHullReportsItsDoubledCeilings() {
        FleetSpec spec = new FleetSpec();
        spec.factions = new java.util.ArrayList<>(List.of("Kzinti"));
        spec.year = 180;
        spec.budget = 2000;
        spec.flagship = "CS";
        FleetSpec.ShipEntry e = new FleetSpec.ShipEntry();
        e.type = "CS";
        e.name = "CS";
        spec.ships.add(e);

        Map<String, Object> row = rowFor(spec);
        assertEquals(true, row.get("relaxed"));
        assertEquals(50, ((Number) row.get("restrictedPercent")).intValue());
        assertEquals(20, ((Number) row.get("limitedPercent")).intValue());
    }
}
