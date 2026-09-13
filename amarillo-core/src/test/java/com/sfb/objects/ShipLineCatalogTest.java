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
