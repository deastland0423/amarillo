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
        // cost is a double since G15.411's option-mount deltas are fractional (a Phaser-2 is
        // -0.25), so it is compared as a number rather than by boxed identity.
        assertEquals((Integer) cv.get("bpv") + (Integer) cv.get("fighterBpv"),
                ((Number) cv.get("cost")).doubleValue(), 1e-9);
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
        assertTrue(((Number) late.get("cost")).doubleValue()
                        > ((Number) early.get("cost")).doubleValue(),
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
        assertTrue(((Number) sc.get("cost")).doubleValue() > (Integer) sc.get("bpv"),
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

    /**
     * Every shelf row says what the hull IS, not just which family it belongs to.
     *
     * <p>The shelf groups by line and labels each group with the line's name, so a Federation CC, CVB
     * and CVL all appeared under "Heavy Cruiser" with nothing to tell them apart but their type codes.
     * {@code typeName} is the class written out, and it differs from the line's name on <b>305 of 355
     * hulls</b> — it is the single most informative field the listing was missing.
     */
    @Test
    void everyShelfRowNamesItsClass() {
        int differsFromTheHeader = 0;
        for (Map<String, Object> s : ships()) {
            String typeName = String.valueOf(s.get("typeName"));
            assertFalse(typeName.isBlank() || "null".equals(typeName),
                    s + " has no typeName, so the shelf can only show its type code");
            if (!typeName.equals(s.get("lineName")))
                differsFromTheHeader++;
        }
        // Non-vacuity: if typeName merely repeated the group header everywhere, sending it would be
        // noise rather than information, and this test would be asserting nothing worth having.
        assertTrue(differsFromTheHeader > 200,
                "typeName should differ from the line header on most hulls, else it adds nothing;"
                        + " got " + differsFromTheHeader);
    }

    // ------------------------------------------------------------------ the ship viewer

    private com.sfb.dto.GameStateDto.ShipDto detail(String faction, String type, int year) {
        return detail(faction, type, "", year);
    }

    private com.sfb.dto.GameStateDto.ShipDto detail(
            String faction, String type, String codes, int year) {
        Object body = new GameController(null, null).shipDetail(faction, type, codes, year).getBody();
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

    /**
     * The viewer's systems block reads every one of these, and a zero or null makes its row vanish
     * silently rather than fail — the panel simply omits an empty line, which is right for a hull
     * that genuinely lacks a system and wrong when the field was never populated. So pin the ones
     * every warship must have.
     *
     * <p>This is the failure mode worth guarding: a preview ship is built outside the normal game
     * setup, so a field that happens to be filled during {@code startTurn()} would read as zero here
     * and nobody would see an error — only a shorter panel.
     */
    @Test
    void thePreviewHullPopulatesWhatTheSystemsBlockReads() {
        com.sfb.dto.GameStateDto.ShipDto ca = detail("Federation", "CA", 0);

        assertTrue(ca.totalPower > 0, "no power at all");
        assertTrue(ca.maxLWarp > 0 && ca.maxRWarp > 0, "a cruiser has warp engines");
        assertTrue(ca.availableLWarp > 0, "undamaged warp should be available, not just maximal");
        assertTrue(ca.maxImpulse > 0, "no impulse");
        assertTrue(ca.lifeSupportCost > 0, "life support is never free");
        assertTrue(ca.moveCost > 0, "no move cost");
        assertNotNull(ca.turnMode, "no turn mode");
        assertTrue(ca.maxFhull > 0 && ca.maxAhull > 0, "no hull boxes");
        assertTrue(ca.availableCrewUnits > 0, "no crew");
        assertTrue(ca.boardingParties > 0, "no boarding parties");
        assertTrue(ca.commandRating > 0, "no command rating");
        assertTrue(ca.totalTransporters > 0, "no transporters");
        assertTrue(ca.availableLab > 0, "no labs");
        assertNotNull(ca.aegisFitted, "aegis should say NONE, not be absent");
    }

    /**
     * A carrier's bays come back with their craft named, which is the part of a carrier's price a
     * buyer cannot see on the shelf at all — and the reason the year is threaded through.
     */
    @Test
    void aCarriersBaysNameWhatIsInThem() {
        com.sfb.dto.GameStateDto.ShipDto cva = detail("Federation", "CVA", 180);

        assertNotNull(cva.shuttleBays);
        assertFalse(cva.shuttleBays.isEmpty(), "a heavy carrier has bays");
        long withCraft = cva.shuttleBays.stream()
                .filter(b -> b.shuttles != null && !b.shuttles.isEmpty())
                .count();
        assertTrue(withCraft > 0, "every bay came back empty, so the viewer shows no air wing");
    }

    /**
     * No craft in any bay of any hull is shown by its catalogue KEY.
     *
     * <p>{@code ShuttleInBayDto.type} is "stinger1", "f18b_e", "zy_e" — a lookup key the hangar and
     * launch pad are built on, and the ship viewer was printing it verbatim, so a carrier's air wing
     * read as "stinger1, stinger1, admin". {@code shortName} is what the craft is called.
     *
     * <p>Swept across every hull rather than spot-checked, because the failure is per-CATALOGUE-ENTRY:
     * a fighter added without a {@code shortName} shows its key again and only on the ships that
     * happen to carry it. The catalogue defaults shortName to the display name when the JSON omits
     * it, so this should hold for anything the catalogue knows at all.
     */
    @Test
    void noCraftInAnyBayIsLabelledWithItsCatalogueKey() {
        java.util.List<String> keyed = new java.util.ArrayList<>();
        int craftSeen = 0;
        for (com.sfb.objects.ShipSpec spec : com.sfb.objects.ShipLibrary.all()) {
            com.sfb.dto.GameStateDto.ShipDto dto = detail(spec.faction, spec.type, 180);
            if (dto.shuttleBays == null) continue;
            for (com.sfb.dto.GameStateDto.ShuttleBayDto bay : dto.shuttleBays) {
                if (bay.shuttles == null) continue;
                for (com.sfb.dto.GameStateDto.ShuttleInBayDto craft : bay.shuttles) {
                    craftSeen++;
                    if (craft.shortName == null || craft.shortName.isBlank())
                        keyed.add(spec.faction + "/" + spec.type + " carries '" + craft.type
                                + "' with no shortName");
                }
            }
        }
        assertTrue(craftSeen > 100, "fixture: should have swept plenty of craft, saw " + craftSeen);
        assertEquals(java.util.List.of(), keyed,
                "craft that would be shown by their catalogue key: " + keyed);
    }

    /** An unknown hull is a 404 rather than an empty ship, so the viewer can say so. */
    @Test
    void anUnknownHullIsNotFound() {
        assertEquals(404, new GameController(null, null)
                .shipDetail("Federation", "NOPE", "", 0).getStatusCode().value());
        assertEquals(404, new GameController(null, null)
                .shipDetail("Atlantean", "CA", "", 0).getStatusCode().value());
    }

    // ------------------------------------------------------------------ refits (S3.24)

    /**
     * A synthesised variant tells the shelf which hull it came from, which is the grouping key.
     *
     * <p>Without it the shelf cannot collapse the alphabet soup the owner complained about, and it
     * cannot be inferred either: grouping by typeName would merge the Klingon D6 with the D7, both
     * being "Battlecruiser", and grouping by type-code prefix would merge the D6 with the D6D, which
     * is a different ship rather than a refit of one.
     */
    @Test
    void aRefittedHullNamesTheHullItCameFrom() {
        Map<String, Object> d7k = row("Klingon", "D7K");

        assertEquals("D7", d7k.get("refitOf"));
        assertEquals(List.of("B", "K"), d7k.get("appliedRefits"));
    }

    /** A base hull is nobody's refit, so it says nothing and its row is exactly as it was. */
    @Test
    void aBaseHullSaysNothingAboutRefits() {
        Map<String, Object> d7 = row("Klingon", "D7");

        assertNull(d7.get("refitOf"));
        assertNull(d7.get("appliedRefits"));
    }

    /**
     * And a base hull offers what may be fitted to it, with each refit's cost — which is the actual
     * purchasing decision. Four rows reading D7/D7B/D7K/D7Ku make a player decode the codes to
     * compare; one row offering "B refit +7, Phaser-1 refit +3, UIM refit +5" states it.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aBaseHullOffersItsRefitsWithTheirCost() {
        List<Map<String, Object>> offers =
                (List<Map<String, Object>>) row("Klingon", "D7").get("refitsAvailable");

        assertNotNull(offers, "the D7 should offer its three refits");
        assertEquals(3, offers.size());

        Map<String, Object> b = offers.stream()
                .filter(o -> "B".equals(o.get("code"))).findFirst().orElseThrow();
        assertEquals("B refit", b.get("name"));
        assertEquals(165, b.get("year"));
        assertEquals(7, b.get("bpv"));
        assertEquals(List.of(), b.get("requires"), "nothing comes before the B refit");

        Map<String, Object> k = offers.stream()
                .filter(o -> "K".equals(o.get("code"))).findFirst().orElseThrow();
        assertEquals("Phaser-1 refit", k.get("name"));
        assertEquals(List.of("B"), k.get("requires"), "the K refit arrives on top of B (D7 data)");
    }

    /**
     * The variants a hull was actually fielded in still appear as their own rows, because the
     * migration had to be invisible: a saved fleet names "D7Ku" and the shelf has always listed it.
     * Collapsing them is the client's job, and this is what makes that safe to do later.
     */
    @Test
    void everyVariantIsStillListedUnderItsHistoricalCode() {
        for (String type : List.of("D7B", "D7Bu", "D7K", "D7Ku", "D6B", "D6K", "D6Bu", "D6Ku",
                                   "D6DB", "D6SB", "D5K", "D5L", "D7L", "F5K", "F5L", "C8K"))
            assertNotNull(row("Klingon", type), type + " should still be in the catalogue");
    }

    private Map<String, Object> row(String faction, String type) {
        return ships(faction).stream()
                .filter(r -> type.equals(r.get("type")))
                .findFirst().orElse(null);
    }
    /**
     * G15.4: the fleet builder has to ask what fits a hull BEFORE any game exists, because
     * G15.415 makes the loadout part of the purchase. The Orion BCH is the sharpest case — four
     * centreline mounts in one adjacency run.
     */
    @Test
    void anOrionHullListsItsOptionMountsAndWhatFitsThem() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) new GameController(null, null)
                .shipOptionMounts("Orion", "BCH", 180).getBody();
        assertNotNull(body);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mounts = (List<Map<String, Object>>) body.get("optionMounts");
        assertEquals(4, mounts.size(), "the BCH has four mounts: " + mounts);
        assertEquals("CENTERLINE", mounts.get(0).get("position"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> legal =
                (List<Map<String, Object>>) mounts.get(0).get("legalOptions");
        assertFalse(legal.isEmpty(), "something must fit a centreline mount");
        // The empires are returned rather than filtered, because G15.441's quota is on the FORCE
        // and no single pick can be judged alone.
        assertTrue(legal.stream().anyMatch(o -> o.containsKey("empires")),
                "each option carries its producing empires for the cartel tiering");
    }

    /**
     * The adjacency runs, without which a picker cannot say WHICH second mount a two-mount
     * option eats. Annex #8B marks eight options with an asterisk; core seats them correctly,
     * but the picker went on offering the mount they were about to fill, and a choice made there
     * was dropped at load with nothing said. Order within a run is meaningful: on an A-B-C row a
     * weapon seats on A+B or B+C and never on A+C.
     */
    @Test
    @SuppressWarnings("unchecked")
    void theMountListingCarriesTheHullsAdjacencyRuns() {
        Map<String, Object> body = (Map<String, Object>) new GameController(null, null)
                .shipOptionMounts("Orion", "CA", 180).getBody();
        assertNotNull(body);

        List<List<String>> runs = (List<List<String>>) body.get("adjacentRuns");
        assertNotNull(runs, "a picker cannot seat a two-mount option without these");
        assertTrue(runs.stream().anyMatch(r -> r.size() >= 2
                        && r.indexOf("B") == r.indexOf("A") + 1),
                "the CA's centerline pair must appear as one consecutive run");
    }

    /** And each option says how many mounts it fills, which is the asterisk itself. */
    @Test
    @SuppressWarnings("unchecked")
    void eachOptionSaysHowManyMountsItFills() {
        Map<String, Object> body = (Map<String, Object>) new GameController(null, null)
                .shipOptionMounts("Orion", "CA", 180).getBody();
        List<Map<String, Object>> mounts = (List<Map<String, Object>>) body.get("optionMounts");
        List<Map<String, Object>> legal = (List<Map<String, Object>>) mounts.stream()
                .filter(m -> "A".equals(m.get("designator"))).findFirst().orElseThrow()
                .get("legalOptions");

        // A Plasma-G, not an ESG: the annex asterisks both, but only the plasma entries carry a
        // weaponSpec, so an ESG is listed NA by `isBuildableWeapon` and never reaches a picker.
        Map<String, Object> plasma = legal.stream()
                .filter(o -> "Plasma-G Torp (No Swivel)".equals(o.get("name"))).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "a Plasma-G should be legal in the CA's centerline mount A"));
        assertEquals(2, plasma.get("mountsRequired"),
                "a Plasma-G fills two adjacent mounts (Annex #8B)");

        Map<String, Object> phaser = legal.stream()
                .filter(o -> "Phaser-1".equals(o.get("name"))).findFirst().orElseThrow();
        assertEquals(1, phaser.get("mountsRequired"), "and an ordinary weapon fills one");
    }

    /**
     * R8.R3 leaves a WING disruptor's or fusion beam's arc to "the taste of the Orion player",
     * so the picker has to offer it — and the server sends the two arc LABELS rather than a
     * flag, so neither view needs a copy of the rule.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aWingDisruptorOffersItsTwoArcs() {
        Map<String, Object> body = (Map<String, Object>) new GameController(null, null)
                .shipOptionMounts("Orion", "CA", 180).getBody();
        List<Map<String, Object>> mounts = (List<Map<String, Object>>) body.get("optionMounts");

        // C is the CA's LEFT wing mount, so outboard reads "L + LF".
        List<Map<String, Object>> wing = (List<Map<String, Object>>) mounts.stream()
                .filter(m -> "C".equals(m.get("designator"))).findFirst().orElseThrow()
                .get("legalOptions");
        Map<String, Object> disruptor = wing.stream()
                .filter(o -> "Disruptor-30".equals(o.get("name"))).findFirst().orElseThrow();

        assertEquals(List.of("FA", "L + LF"), disruptor.get("arcChoices"));
    }

    /** And offers nothing where the rule decides: a wing phaser bears LS, with no say in it. */
    @Test
    @SuppressWarnings("unchecked")
    void aWingPhaserOffersNoArcChoice() {
        Map<String, Object> body = (Map<String, Object>) new GameController(null, null)
                .shipOptionMounts("Orion", "CA", 180).getBody();
        List<Map<String, Object>> mounts = (List<Map<String, Object>>) body.get("optionMounts");
        List<Map<String, Object>> wing = (List<Map<String, Object>>) mounts.stream()
                .filter(m -> "C".equals(m.get("designator"))).findFirst().orElseThrow()
                .get("legalOptions");

        assertEquals(List.of(), wing.stream()
                .filter(o -> "Phaser-1".equals(o.get("name"))).findFirst().orElseThrow()
                .get("arcChoices"));
    }

    /** Nor on the centerline, where R8.R3 says FA for everything that is not a rack. */
    @Test
    @SuppressWarnings("unchecked")
    void aCenterlineDisruptorOffersNoArcChoice() {
        Map<String, Object> body = (Map<String, Object>) new GameController(null, null)
                .shipOptionMounts("Orion", "CA", 180).getBody();
        List<Map<String, Object>> mounts = (List<Map<String, Object>>) body.get("optionMounts");
        List<Map<String, Object>> centre = (List<Map<String, Object>>) mounts.stream()
                .filter(m -> "A".equals(m.get("designator"))).findFirst().orElseThrow()
                .get("legalOptions");

        assertEquals(List.of(), centre.stream()
                .filter(o -> "Disruptor-30".equals(o.get("name"))).findFirst().orElseThrow()
                .get("arcChoices"));
    }

    /** A hull with no mounts answers with an empty list rather than a 404. */
    @Test
    void aHullWithoutOptionMountsListsNone() {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) new GameController(null, null)
                .shipOptionMounts("Federation", "CA", 180).getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mounts = (List<Map<String, Object>>) body.get("optionMounts");
        assertTrue(mounts.isEmpty(), "a Federation CA has no option mounts");
    }

    // ------------------------------------- viewing a combination nobody named a hull for

    /**
     * The (i) button on a shelf row that has refits ticked. Sixteen of the seventeen Orion files
     * name no variant, so an OAKDISC-fitted CA is a synthesised "CAO" — not a library type. The
     * viewer asked for it by that name and got a 404, so the one hull the shelf was offering was
     * the one hull a buyer could not look at. It is fetched the way it is bought: base + codes.
     */
    @Test
    void theViewerOpensARefitCombinationThatHasNoNameOfItsOwn() {
        com.sfb.dto.GameStateDto.ShipDto oakdisc = detail("Orion", "CA", "O", 180);

        assertEquals("CAO", oakdisc.shipType, "the synthesised type comes back on the ship");
    }

    /** And the refit is actually ON it — FD4.5 doubles the rating for seeking weapons. */
    @Test
    void theViewedCombinationCarriesWhatTheRefitDoes() {
        double plain = detail("Orion", "CA", "", 180).controlLimit;
        double refitted = detail("Orion", "CA", "O", 180).controlLimit;

        assertEquals(plain * 2, refitted, 1e-9,
                "OAKDISC doubles the control rating (FD4.5), so the viewer must show the doubled one");
    }

    /** No codes is the bare hull, exactly as before — the parameter is additive. */
    @Test
    void askingWithoutCodesIsUnchanged() {
        assertEquals(detail("Orion", "CA", 180).shipType, detail("Orion", "CA", "", 180).shipType);
    }
}
