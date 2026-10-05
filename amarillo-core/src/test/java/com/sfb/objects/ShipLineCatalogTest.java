package com.sfb.objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Ship lines are data rather than an enum, which buys the freedom to add one without touching
 * Java and costs the compiler's spell-check. This is what replaces it: a line a ship file
 * claims but the catalogue has never heard of fails the build instead of quietly becoming a
 * family of one, which would let a leader pass its consort check by having no peers at all.
 */
public class ShipLineCatalogTest {

    private static final File CATALOG = new File("../data/shiplines/shiplines.json");
    private static final File FACTIONS = new File("../data/factions");

    @Before
    public void loadCatalogue() throws Exception {
        assumeTrue("shiplines.json must exist", CATALOG.exists());
        ShipLineCatalog.load(CATALOG);
        // The series guards below walk ShipLibrary. Loading it HERE and not relying on another
        // test class having done so: the registry is static and never cleared, so without this
        // those guards pass vacuously when the class runs alone and meaningfully in a full build —
        // order-dependent, which is worse than either.
        ShipLibrary.loadAllSpecs(FACTIONS.getPath());
    }

    @Test
    public void theCatalogueLoads() {
        assertTrue(ShipLineCatalog.isLoaded());
        assertFalse("it is not empty", ShipLineCatalog.all().isEmpty());
    }

    @Test
    public void everyEntryIsNamedAndUnique() {
        Set<String> codes = new HashSet<>();
        for (ShipLineCatalog.Entry e : ShipLineCatalog.all()) {
            assertFalse("a line needs a code", e.code == null || e.code.isBlank());
            assertFalse(e.code + " needs a display name", e.name == null || e.name.isBlank());
            assertTrue("duplicate line code: " + e.code, codes.add(e.code.toLowerCase()));
        }
    }

    /**
     * The drift guard. Every line named by a ship file must be one the catalogue publishes;
     * a typo ("CW " or "Ca") shows up here rather than as a ship that matches nothing.
     */
    @Test
    public void everyLineUsedByAShipIsCatalogued() throws Exception {
        assumeTrue("data/factions must exist", FACTIONS.isDirectory());

        Map<String, List<String>> unknown = new TreeMap<>();
        int classified = 0;

        for (File faction : FACTIONS.listFiles(File::isDirectory)) {
            for (File f : faction.listFiles(n -> n.getName().endsWith(".json"))) {
                JsonNode root = new ObjectMapper().readTree(f);
                String line = root.path("line").asText(null);
                if (line == null || line.isBlank())
                    continue;   // not yet classified; completeness is a separate concern
                classified++;
                if (!ShipLineCatalog.isKnown(line))
                    unknown.computeIfAbsent(line, k -> new ArrayList<>())
                            .add(faction.getName() + "/" + f.getName());
            }
        }

        assertTrue("some ships should be classified by now", classified > 0);
        assertTrue("lines used by ships but absent from shiplines.json: " + unknown,
                unknown.isEmpty());
    }

    /**
     * Movement cost has to be one the line permits. Neither field derives the other — BCH and
     * CA both pay 1, CL and CW both pay two thirds — but a ship paying a cost outside its
     * line's bracket has one of them wrong, which is how twelve ships were found carrying a
     * heavy cruiser's cost while claiming to be dreadnoughts and light cruisers.
     * <p>
     * A line may permit several: the older Federation light cruisers pay 3/4 where the newer
     * ones pay 2/3, and both are CL. What it must not do is permit a cost from another
     * bracket, which is what keeps the check worth running.
     */
    @Test
    public void everyShipsMoveCostAgreesWithItsLine() throws Exception {
        assumeTrue("data/factions must exist", FACTIONS.isDirectory());

        List<String> wrong = new ArrayList<>();
        for (File faction : FACTIONS.listFiles(File::isDirectory)) {
            for (File f : faction.listFiles(n -> n.getName().endsWith(".json"))) {
                JsonNode root = new ObjectMapper().readTree(f);
                String line = root.path("line").asText(null);
                if (line == null || line.isBlank())
                    continue;
                if (ShipLineCatalog.moveCostsOf(line).isEmpty())
                    continue;   // catalogue publishes no cost for this line
                double actual = root.path("moveCost").asDouble(0);
                if (!ShipLineCatalog.permitsMoveCost(line, actual))
                    wrong.add(faction.getName() + "/" + f.getName() + " is " + line
                            + " (pays " + ShipLineCatalog.moveCostsOf(line) + ") but pays " + actual);
            }
        }

        String indent = System.lineSeparator() + "  ";
        assertTrue("move cost disagrees with line:" + indent + String.join(indent, wrong),
                wrong.isEmpty());
    }

    /** A ship's line travels from its file through the spec into the built ship. */
    @Test
    public void theLineSurvivesTheTripFromFileToShip() {
        ShipSpec spec = new ShipSpec();
        spec.faction = "Klingon";
        spec.type = "D7C";
        spec.line = "CA";
        spec.turnMode = "D";

        Ship ship = new Ship();
        ship.init(spec.toInitMap());

        assertEquals("D7C", ship.getType());
        assertEquals("CA", ship.getLine());
    }

    // ------------------------------------------------------------ display order (fleet builder)

    /**
     * The catalogue's own file order is the fleet-builder shelf's display order, so it has to be
     * a dense sequence with no repeats.
     * <p>
     * The shelf used to sort groups by the line CODE, which read as nonsense to a player: "BCH"
     * sorts above "CA", so Heavy Battlecruiser came before Heavy Cruiser, and the freighters
     * landed between Destroyer and Frigate. Order now comes from this file (owner's ordering,
     * 2026-10-03) and {@code ShipLineCatalog.Entry.order} is the index, so reordering the file
     * reorders the screen with nothing to renumber.
     */
    @Test
    public void everyLineHasADistinctPlaceInTheDisplayOrder() {
        List<ShipLineCatalog.Entry> all = ShipLineCatalog.all();
        assumeTrue(!all.isEmpty());

        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            ShipLineCatalog.Entry e = all.get(i);
            assertEquals(e.code + " should hold position " + i + " in catalogue order",
                    i, e.order);
            assertTrue(e.code + " repeats display order " + e.order, seen.add(e.order));
        }
    }

    /**
     * Civilian lines must be contiguous at the END of the catalogue.
     * <p>
     * The shelf marks only the FIRST civilian group and draws its dividing rule there, so a
     * civilian line placed among the warships would put the warship/freighter rule in the middle
     * of the warships — and a second block of them further down would get no rule at all. This is
     * the invariant that lets the UI carry one boolean instead of a notion of sections.
     */
    @Test
    public void civilianLinesComeLastAndStayTogether() {
        List<ShipLineCatalog.Entry> all = ShipLineCatalog.all();
        assumeTrue(!all.isEmpty());

        boolean inCivilians = false;
        List<String> wrong = new ArrayList<>();
        for (ShipLineCatalog.Entry e : all) {
            if (e.civilian) {
                inCivilians = true;
            } else if (inCivilians) {
                wrong.add(e.code + " (a warship line) sits below a civilian line");
            }
        }
        assertEquals("civilian lines must be contiguous at the end of shiplines.json: " + wrong,
                List.of(), wrong);
    }

    /** An uncatalogued line sorts after every known one rather than to the top of the shelf. */
    @Test
    public void anUnknownLineSortsLastAndIsNotCivilian() {
        assertEquals(Integer.MAX_VALUE, ShipLineCatalog.orderOf("NOPE"));
        assertEquals(Integer.MAX_VALUE, ShipLineCatalog.orderOf(null));
        assertFalse(ShipLineCatalog.isCivilian("NOPE"));
        assertFalse(ShipLineCatalog.isCivilian(null));
    }

    // ------------------------------------------------------------ series (outer shelf grouping)

    /**
     * Every series a ship declares must be one the catalogue knows, or the shelf shows a section
     * headed by a raw code and nothing says why.
     * <p>
     * Stricter than the line check needs to be, because a series is NOT a rule: nothing reads it,
     * so a typo would cost only a label and would never otherwise surface. That is exactly the
     * kind of silence worth a guard — see the Romulan generations, added 2026-10-05.
     */
    @Test
    public void everySeriesDeclaredByAShipIsCatalogued() throws Exception {
        List<String> wrong = new ArrayList<>();
        int declared = 0;
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.series == null || spec.series.isBlank())
                continue;
            declared++;
            if (ShipLineCatalog.series(spec.series) == null)
                wrong.add(spec.faction + " " + spec.type + " declares series '" + spec.series
                        + "', which shiplines.json does not list");
        }
        assertEquals("uncatalogued series: " + wrong, List.of(), wrong);
        // Non-vacuity: if nobody declares a series, the loop above proves nothing and should say
        // so rather than reading green. The Romulans supply 38 of them.
        assertTrue("some ship should declare a series for this to mean anything", declared > 0);
    }

    /** And each series is named, unique, and densely ordered, like the lines. */
    @Test
    public void everySeriesHasADistinctPlaceAndAName() {
        List<ShipLineCatalog.Series> all = ShipLineCatalog.allSeries();
        assumeTrue(!all.isEmpty());

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < all.size(); i++) {
            ShipLineCatalog.Series s = all.get(i);
            assertFalse("a series needs a code", s.code == null || s.code.isBlank());
            assertFalse(s.code + " needs a display name", s.name == null || s.name.isBlank());
            assertFalse(s.code + " needs a faction", s.faction == null || s.faction.isBlank());
            assertEquals(s.code + " should hold position " + i, i, s.order);
            assertTrue("series code " + s.code + " is declared twice", codes.add(s.code));
        }
    }

    /**
     * A series belongs to ONE empire, and only that empire's ships may claim it. The shelf shows
     * several factions at once, so a stray series on another navy's hull would file it under a
     * generation it has nothing to do with.
     */
    @Test
    public void noShipClaimsAnotherEmpiresSeries() {
        List<String> wrong = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            ShipLineCatalog.Series s = ShipLineCatalog.series(spec.series);
            if (s != null && !s.faction.equalsIgnoreCase(spec.faction))
                wrong.add(spec.faction + " " + spec.type + " claims " + s.code
                        + ", which belongs to the " + s.faction + "s");
        }
        assertEquals("ships claiming another empire's series: " + wrong, List.of(), wrong);
    }

    /** An uncatalogued series sorts last and keeps its code as its label, rather than throwing. */
    @Test
    public void anUnknownSeriesSortsLastAndKeepsItsCode() {
        assertEquals(Integer.MAX_VALUE, ShipLineCatalog.seriesOrderOf("NOPE"));
        assertEquals(Integer.MAX_VALUE, ShipLineCatalog.seriesOrderOf(null));
        assertEquals("NOPE", ShipLineCatalog.seriesNameOf("NOPE"));
        assertNull(ShipLineCatalog.series(null));
    }

    /** An unclassified ship reports no line rather than inventing one. */
    @Test
    public void anUnclassifiedShipHasNoLine() {
        ShipSpec spec = new ShipSpec();
        spec.faction = "Klingon";
        spec.type = "D7";
        spec.turnMode = "D";

        Ship ship = new Ship();
        ship.init(spec.toInitMap());

        assertNull(ship.getLine());
    }
}
