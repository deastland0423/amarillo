package com.sfb.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which fighters the Commander's Options offer as "Fighters Ready" (S4.10-S4.12).
 * <p>
 * The list must hold only fighters a deck crew can actually do something to. A Hydran
 * Stinger-E (R1.F7) carries one Ph-G and two permanently-fitted EW pods, so there is nothing
 * to load onto it and it is ready at every weapon status — offering it was a checkbox that
 * changed no outcome, and it was the FIRST fighter the RN+ lists, so it sat at the top of the
 * list inviting a pointless click.
 * <p>
 * Asked of the server rather than the client because "is there anything to arm" is a rules
 * question, and the client filtering on a fighter's type name would be the view deciding one.
 */
class CoiFighterReadinessEndpointTest {

    @BeforeEach
    void loadShips() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        com.sfb.objects.ShipLineCatalog.loadDefault("../data");
    }

    /** The Hydran RN+, whose standard complement is six Stinger-2s, two -Hs and one -E. */
    private Map<String, Object> rnPlusCoi() {
        return coiFor("Hydran", "RN+", "HMS Bravery");
    }

    /** The Kzinti CVS: eleven HAAS and one HAAS-E, whose pods sit on its two drone rails. */
    private Map<String, Object> cvsCoi() {
        return coiFor("Kzinti", "CVS", "KHS Watchful");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> coiFor(String faction, String type, String shipName) {
        com.sfb.scenario.ScenarioSpec spec = new com.sfb.scenario.ScenarioSpec();
        spec.mapCols = 42;
        spec.mapRows = 32;
        // Y173, so the Kzinti carriers fly the HAAS this test is named for: a complement is
        // resolved from the year (J4.4) and a CVS at its own Y170 service year flies the AAS.
        // The Hydran RN+ is in its Y170 era either way.
        spec.year = 173;
        com.sfb.scenario.ScenarioSpec.SideSpec side = new com.sfb.scenario.ScenarioSpec.SideSpec();
        side.faction = faction;
        side.name = faction + "s";
        side.ships = new java.util.ArrayList<>();
        com.sfb.scenario.ScenarioSpec.ShipSetup ship = new com.sfb.scenario.ScenarioSpec.ShipSetup();
        ship.type = type;
        ship.shipName = shipName;
        ship.startHex = "1016";
        ship.startHeading = "C";
        side.ships.add(ship);
        spec.sides = new java.util.ArrayList<>(List.of(side));

        // coiDataFor answers side by side, each side holding its own ships.
        List<Map<String, Object>> sides = new GameController(null, null).coiDataFor(spec);
        return sides.stream()
                .flatMap(sd -> ((List<Map<String, Object>>) sd.get("ships")).stream())
                .filter(s -> shipName.equals(s.get("shipName")))
                .findFirst().orElseThrow(() -> new AssertionError(
                        type + " not in the COI data: " + sides));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fightersOffered() {
        return offeredIn(rnPlusCoi());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> offeredIn(Map<String, Object> shipCoi) {
        return (List<Map<String, Object>>) shipCoi.get("fighters");
    }

    private List<String> typesOffered(Map<String, Object> shipCoi) {
        return offeredIn(shipCoi).stream().map(f -> String.valueOf(f.get("type"))).toList();
    }

    @Test
    void theStingerEIsNotOfferedBecauseThereIsNothingToArm() {
        List<String> types = fightersOffered().stream()
                .map(f -> String.valueOf(f.get("type"))).toList();

        assertFalse(types.contains("Stinger_E"),
                "a Stinger-E has nothing to arm and must not be offered: " + types);
    }

    @Test
    void theFightersThatCanBeArmedAreStillAllOffered() {
        List<String> types = fightersOffered().stream()
                .map(f -> String.valueOf(f.get("type"))).toList();

        assertEquals(8, types.size(), "six Stinger-2s and two Stinger-Hs: " + types);
        assertEquals(6, types.stream().filter("Stinger2"::equals).count());
        assertEquals(2, types.stream().filter("StingerH"::equals).count());
    }

    /**
     * And the count offered agrees with what WS-3 actually arms. S4.13 "arms the lot", which
     * is eight here, not nine — so a maximum taken from the list size stays truthful.
     */
    @Test
    void theAllowanceAtWsThreeMatchesWhatCanBeArmed() {
        assertEquals(8, fightersOffered().size());
    }

    /**
     * The Kzinti HAAS-E must be left out for the same reason, reached differently: it HAS two
     * drone rails, and both carry EW pods (J4.962), so there is still nothing a deck crew can
     * load. Offering it crashed the submit endpoint — armFully asked a podded rail to take a
     * drone and DroneRail.loadDrone refused.
     */
    @Test
    void theKzintiHaasEIsNotOfferedEither() {
        List<String> types = typesOffered(cvsCoi());

        assertFalse(types.contains("Haas_E"),
                "a HAAS-E has pods on both rails and nothing to arm: " + types);
        assertEquals(11, types.stream().filter("Haas"::equals).count(),
                "its eleven drone-armed sisters are still offered: " + types);
    }
}
