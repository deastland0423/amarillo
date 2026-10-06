package com.sfb.objects;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

/**
 * The shuttle catalogue is a FOLDER of per-faction files, and every one of them reaches the registry.
 *
 * <h2>The bug the split makes possible</h2>
 * Until 2026-10-06 the catalogue was one 564-line {@code shuttles.json}, which could not contain two
 * entries for {@code f18}. Seven files can — and the loser would vanish without a word, because
 * {@code loadInto} ends in a plain {@code registry.put}. That is not hypothetical: it is exactly how a
 * Hydran hull disappeared from {@code ShipLibrary} when two files claimed the same faction and type,
 * and it was invisible because <b>every other guard walks the registry</b>, and a craft that never
 * registered cannot fail a test about registered craft. So this one walks the FILES.
 *
 * <h2>And the other half: the loader must not clear between files</h2>
 * {@code loadAll} clears once and then adds, which is the choice {@code ShipLibrary.loadAllSpecs}
 * documents. Get that wrong and whichever file sorts last becomes the whole catalogue — the Romulans
 * would have a fighter programme and nobody else would. The non-vacuity assertions below are there to
 * catch that: a catalogue holding only one empire's craft passes any per-craft check you care to write.
 */
public class ShuttleCatalogSplitTest {

    private static final File DIR = new File("../data/shuttles");

    @BeforeClass
    public static void loadCatalogue() throws Exception {
        ShuttleCatalog.loadAll(DIR);
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    /** No two files claim the same craft type, or the same fighter line. */
    @Test
    public void noTypeOrFighterLineIsClaimedTwice() throws Exception {
        Map<String, String> firstSeen = new LinkedHashMap<>();
        List<String> clashes = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        File[] files = DIR.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
        assertNotNull("the catalogue folder should exist", files);
        for (File f : files) {
            JsonNode root = mapper.readTree(f);
            for (JsonNode craft : root.path("shuttles")) {
                String key = "type " + craft.path("type").asText().toLowerCase();
                String prior = firstSeen.put(key, f.getName());
                if (prior != null)
                    clashes.add(key + " is in both " + prior + " and " + f.getName());
            }
            for (java.util.Iterator<String> it = root.path("fighterLines").fieldNames(); it.hasNext(); ) {
                String key = "fighterLine " + it.next().toLowerCase();
                String prior = firstSeen.put(key, f.getName());
                if (prior != null)
                    clashes.add(key + " is in both " + prior + " and " + f.getName());
            }
        }

        assertTrue("fixture: the folder should hold plenty of craft", firstSeen.size() > 50);
        assertEquals("a key claimed twice means one entry is silently replaced:" + System.lineSeparator()
                + "  " + String.join(System.lineSeparator() + "  ", clashes), List.of(), clashes);
    }

    /** And the loader agrees, which is the check a reader of the data cannot make by eye. */
    @Test
    public void theLoaderReportsNoDuplicates() {
        assertEquals("ShuttleCatalog.loadAll found keys claimed twice: "
                + ShuttleCatalog.duplicateKeys(), Map.of(), ShuttleCatalog.duplicateKeys());
    }

    /**
     * Every empire's craft are present at once — the assertion that catches a loader clearing between
     * files, which no per-craft test can see.
     */
    @Test
    public void everyEmpiresCraftAreLoadedTogether() {
        Map<String, Integer> byFaction = new LinkedHashMap<>();
        for (ShuttleCatalog.Entry e : ShuttleCatalog.all())
            for (String faction : e.factions)
                byFaction.merge(faction, 1, Integer::sum);

        for (String faction : List.of("federation", "klingon", "kzinti", "romulan", "hydran", "gorn"))
            assertTrue(faction + " has no craft in the catalogue, so one file has replaced the others"
                    + " rather than adding to them: " + byFaction,
                    byFaction.getOrDefault(faction, 0) > 0);
        assertTrue("the craft any faction may field should be there too",
                byFaction.getOrDefault("any", 0) >= 3);
    }

    /** Likewise the fighter lines: one per empire, all loaded at once. */
    @Test
    public void everyEmpiresFighterLineIsLoaded() {
        for (String line : List.of("federation-fighter", "klingon-zegurnii", "romulan-gladiator",
                "kzinti-attack", "hydran-stinger", "gorn-fighter"))
            assertNotNull(line + " is missing, so the per-faction files are not accumulating",
                    ShuttleCatalog.lineEras(line));
    }

    /**
     * Nothing in the data still points at the file that was split. A ship bay naming a type the
     * catalogue no longer holds builds an unarmed admin shuttle in its place, which is the quiet
     * failure {@code CatalogueWeaponsBuildTest} exists for — this is the same check one level up.
     */
    @Test
    public void everyTypeNamedByAShipBayIsStillCatalogued() {
        List<String> missing = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.shuttleBays == null) continue;
            for (ShipSpec.ShuttleBaySpec bay : spec.shuttleBays) {
                if (bay.shuttles == null) continue;
                for (String type : bay.shuttles)
                    if (ShuttleCatalog.get(type) == null)
                        missing.add(spec.faction + "/" + spec.type + " names '" + type + "'");
            }
        }
        assertEquals("ship bays naming craft the split folder does not hold: " + missing,
                List.of(), missing);
    }
}
