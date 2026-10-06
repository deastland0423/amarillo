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
        return shipsInYear(0, factions);
    }

    /**
     * The catalogue in a given year. A carrier's price moves with the date — S8.131 makes it
     * decide which fighters it flies and S8.11 charges for them — and year 0 means "no date
     * chosen", which quotes each ship as its own service year built it.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> shipsInYear(int year, String... factions) {
        List<String> filter = factions.length == 0 ? null : List.of(factions);
        return (List<Map<String, Object>>) new GameController(null, null)
                .listShips(filter, year).getBody();
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
        assertEquals(Boolean.TRUE, cv.get("requiresEscort"));
    }

    /**
     * S8.131: the date decides which fighters a carrier flies, and S8.11 charges for them, so
     * the shelf price has to move with the year the fleet is being built in.
     * <p>
     * This is the invariant the whole endpoint exists for. It used to fail: the shelf always
     * built a carrier at its hull's own service year, so a Y183 fleet was quoted a Kzinti CVS
     * at its Y170 AAS price and the validator charged the same — both wrong together, which is
     * exactly how it went unnoticed.
     */
    @Test
    void aCarrierCostsMoreInALaterYear() {
        Map<String, Object> early = shipsInYear(170, "Kzinti").stream()
                .filter(s -> "CVS".equals(s.get("type"))).findFirst().orElseThrow();
        Map<String, Object> late = shipsInYear(183, "Kzinti").stream()
                .filter(s -> "CVS".equals(s.get("type"))).findFirst().orElseThrow();

        assertEquals(early.get("bpv"), late.get("bpv"), "the hull does not change");
        assertTrue((Integer) late.get("fighterBpv") > (Integer) early.get("fighterBpv"),
                "TADSC cost more than AAS: " + early.get("fighterBpv")
                        + " -> " + late.get("fighterBpv"));
        assertTrue((Integer) late.get("cost") > (Integer) early.get("cost"),
                "so the shelf price rises: " + early.get("cost") + " -> " + late.get("cost"));
        assertTrue((Double) late.get("coiAllowance") > (Double) early.get("coiAllowance"),
                "and S3.211's allowance with it");
    }

    /** No date chosen quotes each ship as its own service year built it, not an empty bay. */
    @Test
    void withNoYearTheShelfShowsTheServiceYearComplement() {
        Map<String, Object> undated = find("Kzinti", "CVS");
        Map<String, Object> atService = shipsInYear(170, "Kzinti").stream()
                .filter(s -> "CVS".equals(s.get("type"))).findFirst().orElseThrow();

        assertEquals(atService.get("cost"), undated.get("cost"));
        assertTrue((Integer) undated.get("fighterBpv") > 0);
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

    // ------------------------------------------------------------------ the ship viewer

    private com.sfb.dto.GameStateDto.ShipDto detail(String faction, String type, int year) {
        Object body = new GameController(null, null).shipDetail(faction, type, year).getBody();
        assertInstanceOf(com.sfb.dto.GameStateDto.ShipDto.class, body,
                faction + " " + type + " did not come back as a ship");
        return (com.sfb.dto.GameStateDto.ShipDto) body;
    }

    /**
     * The viewer's whole reason for existing: the shelf row cannot tell a player what a hull
     * actually is. The row carries price, size class, command rating and some flags; what a buyer
     * wants is the armament and the hull behind it, and none of that is in the listing.
     */
    @Test
    void theDetailViewCarriesWhatTheShelfRowCannot() {
        com.sfb.dto.GameStateDto.ShipDto ca = detail("Federation", "CA", 0);

        assertEquals("CA", ca.shipType);
        assertEquals("Federation", ca.faction);
        assertNotNull(ca.shields, "no shields");
        assertEquals(6, ca.shields.size(), "six shield facings");
        assertNotNull(ca.weapons, "no weapons");
        assertTrue(ca.weapons.size() >= 10, "a heavy cruiser's armament: " + ca.weapons.size());
        assertTrue(ca.moveCost > 0, "move cost should be known");

        // And confirm the gap is real rather than assumed — none of these reach the shelf row.
        Map<String, Object> row = find("Federation", "CA");
        assertFalse(row.containsKey("shields"), "shelf row unexpectedly carries shields");
        assertFalse(row.containsKey("weapons"), "shelf row unexpectedly carries weapons");
    }

    /**
     * The preview hull is BERTHED, and this is the bug the test exists to catch rather than a
     * detail. {@code SsdPanel} draws its arc diagram around the ship's real hex — column parity
     * makes an approximate centre wrong — and a ship with no location renders as "off the map, no
     * bearing to draw". A catalogue entry has no hex of its own, so the endpoint gives it one.
     */
    @Test
    void thePreviewHullHasAHexAndAFacingSoTheDiagramCanDraw() {
        com.sfb.dto.GameStateDto.ShipDto ca = detail("Federation", "CA", 0);

        assertNotNull(ca.location, "SsdPanel would show 'off the map' with no location");
        assertTrue(ca.location.matches("<\\d+\\|\\d+>"), "unexpected location: " + ca.location);
        assertEquals(1, ca.facing, "facing 1 is up, as a printed SSD is drawn");

        // Far enough from every edge that the 5-hex diagram is never clipped by the board.
        String[] parts = ca.location.replaceAll("[<>]", "").split("\\|");
        int col = Integer.parseInt(parts[0]), row = Integer.parseInt(parts[1]);
        int margin = 5;   // SsdPanel's RADIUS
        assertTrue(col >= margin && row >= margin, "too close to the top-left: " + ca.location);
        assertTrue(col <= 42 - margin && row <= 32 - margin,
                "too close to the bottom-right of the default 42x32 board: " + ca.location);
    }

    /**
     * S8.131: the date decides which fighters a carrier flies, so the same hull honestly shows a
     * different air wing in different years — which is precisely what a buyer cannot discover
     * before paying. A hull with no fighters is unaffected by the year, which is the control.
     */
    @Test
    void aCarriersAirWingFollowsTheYear() {
        com.sfb.dto.GameStateDto.ShipDto early = detail("Federation", "CVA", 168);
        com.sfb.dto.GameStateDto.ShipDto late = detail("Federation", "CVA", 183);

        assertNotNull(early.shuttleBays);
        assertFalse(early.shuttleBays.isEmpty(), "a heavy carrier should have bays");
        assertEquals(early.shuttleBays.size(), late.shuttleBays.size(),
                "the year changes what is in the bays, not how many there are");
    }

    /** An unknown hull is a 404 rather than an empty ship, so the viewer can say so. */
    @Test
    void anUnknownHullIsNotFound() {
        assertEquals(404, new GameController(null, null)
                .shipDetail("Federation", "NOPE", 0).getStatusCode().value());
        assertEquals(404, new GameController(null, null)
                .shipDetail("Atlantean", "CA", 0).getStatusCode().value());
    }
}
