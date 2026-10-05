package com.sfb.objects;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Every hull's default ship name is unique across the whole fleet, and every hull has one.
 *
 * <h2>Why this is a guard and not a style note</h2>
 * The owner's call (2026-10-05): "I do want to eliminate duplicate ship names. I think it's more fun
 * when they are all different." Twenty-five names were shared when that was said, almost all a
 * refit pair inheriting its base's name on being copied — Lyran CA+ and CA+p both
 * "Conciliator's Claw", Romulan SKA and SKF both "RIS Arrow", Gorn CA, CA+ and CCF all "GCS
 * Serpenticon". The sweep took the data to 271 hulls with 271 distinct names.
 *
 * <p>It is exactly the kind of thing that accumulates invisibly: a new file is made by copying the
 * nearest hull, and the name is the field least likely to be noticed because nothing depends on it.
 * This fails on the first duplicate rather than after another 271 have piled up — the same reason
 * {@code ShipLibraryIntegrityTest} watches faction+type, though that one costs a whole hull where
 * this one costs only clarity.
 *
 * <p>Checked against the SPEC rather than a built {@code Ship}, because a scenario overrides the
 * name with its own and the field being guarded is the default the file carries.
 */
public class ShipNameUniquenessTest {

    @BeforeClass
    public static void loadData() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    @Test
    public void noTwoHullsShareADefaultName() {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.name == null || spec.name.isBlank())
                continue;
            byName.computeIfAbsent(spec.name, k -> new ArrayList<>())
                    .add(spec.faction + "/" + spec.type);
        }

        List<String> clashes = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byName.entrySet())
            if (e.getValue().size() > 1)
                clashes.add("'" + e.getKey() + "' is used by " + e.getValue());

        assertTrue("some hulls should be named by now", byName.size() > 100);
        assertEquals("ship names must be unique across every faction:" + System.lineSeparator()
                        + "  " + String.join(System.lineSeparator() + "  ", clashes),
                List.of(), clashes);
    }

    /**
     * And every hull carries one. A ship with no name shows as an empty label wherever the type is
     * not also displayed, and the gap would never fail anything else.
     */
    @Test
    public void everyHullHasADefaultName() {
        List<String> nameless = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            if (spec.name == null || spec.name.isBlank())
                nameless.add(spec.faction + "/" + spec.type);

        assertEquals("hulls with no default name: " + nameless, List.of(), nameless);
    }

    /**
     * Names are compared exactly, so a trailing space would make two names "different" while
     * reading identically on screen. Cheap to rule out, and the sort of thing a copied file carries.
     */
    @Test
    public void noNameIsPaddedWithWhitespace() {
        List<String> padded = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.name == null)
                continue;
            if (!spec.name.equals(spec.name.trim()))
                padded.add(spec.faction + "/" + spec.type + " = '" + spec.name + "'");
        }
        assertEquals("names padded with whitespace: " + padded, List.of(), padded);
    }
}
