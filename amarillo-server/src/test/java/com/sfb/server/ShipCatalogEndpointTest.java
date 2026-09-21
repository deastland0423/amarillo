package com.sfb.server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The fleet builder's shelf. Prices here come from FleetValidator rather than straight from the
 * JSON, so what the picker shows and what the validator charges cannot drift apart — the two
 * places a scout's economic value and a carrier's fighters could disagree.
 */
class ShipCatalogEndpointTest {

    /**
     * The endpoint loads from "data", which is where the server runs. Surefire runs from the
     * module, so seed both from here first; the endpoint's own calls then find them loaded and
     * leave them alone.
     */
    @org.junit.jupiter.api.BeforeEach
    void loadShips() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        com.sfb.objects.ShipLineCatalog.loadDefault("../data");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ships(String... factions) {
        List<String> filter = factions.length == 0 ? null : List.of(factions);
        return (List<Map<String, Object>>) new GameController(null, null).listShips(filter).getBody();
    }

    private Map<String, Object> find(String faction, String type) {
        return ships().stream()
                .filter(s -> faction.equals(s.get("faction")) && type.equals(s.get("type")))
                .findFirst().orElseThrow(() -> new AssertionError(faction + " " + type + " not listed"));
    }

    /** No filter is the whole library; a filter narrows it, and repeats to name several. */
    @Test
    void theListingCanBeNarrowedByEmpire() {
        List<Map<String, Object>> klingon = ships("Klingon");
        assertFalse(klingon.isEmpty());
        assertTrue(klingon.stream().allMatch(s -> "Klingon".equals(s.get("faction"))), "only Klingons");
        assertTrue(klingon.size() < ships().size(), "narrower than the whole library");

        List<Map<String, Object>> allied = ships("Klingon", "Lyran");
        assertTrue(allied.size() > klingon.size(), "two empires is more than one");
        assertTrue(allied.stream().allMatch(
                s -> "Klingon".equals(s.get("faction")) || "Lyran".equals(s.get("faction"))));
    }

    @Test
    void anUnknownEmpireListsNothingRatherThanEverything() {
        assertTrue(ships("Andromedan").isEmpty());
    }

    @Test
    void everyShipInTheLibraryIsListed() {
        assertEquals(com.sfb.objects.ShipLibrary.all().size(), ships().size());
    }

    @Test
    void shipsCarryWhatAPickerNeeds() {
        Map<String, Object> d7c = find("Klingon", "D7C");

        assertEquals("CA", d7c.get("line"));
        assertEquals("Heavy Cruiser", d7c.get("lineName"));
        assertEquals(3, d7c.get("sizeClass"));
        assertEquals(Boolean.TRUE, d7c.get("isLeader"));
        assertTrue((Integer) d7c.get("bpv") > 0);
    }

    /** A carrier's shelf price includes the fighters it comes with. */
    @Test
    void aCarrierIsPricedWithItsFighters() {
        Map<String, Object> cv = find("Kzinti", "CV");

        assertTrue((Integer) cv.get("fighterBpv") > 0, cv.toString());
        assertEquals((Integer) cv.get("bpv") + (Integer) cv.get("fighterBpv"), cv.get("cost"));
        assertEquals(Boolean.TRUE, cv.get("isTrueCarrier"));
    }

    /** A scout is shelved at the economic value it is actually bought for (G24.35). */
    @Test
    void aScoutIsPricedAtItsEconomicValue() {
        Map<String, Object> sc = find("Federation", "SC");

        assertEquals(Boolean.TRUE, sc.get("isScout"));
        assertTrue((Integer) sc.get("cost") > (Integer) sc.get("bpv"),
                "economic value should exceed the combat BPV: " + sc);
    }

    /** Every listed line is one the catalogue knows, so the picker can always group by it. */
    @Test
    void everyShipHasADisplayableLine() {
        for (Map<String, Object> s : ships()) {
            String line = String.valueOf(s.get("line"));
            assertFalse(line.isBlank(), s + " has no line");
            assertNotEquals(line, s.get("lineName"), s + " has no display name for " + line);
        }
    }
}
