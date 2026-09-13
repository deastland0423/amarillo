package com.sfb.objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The lines ships are grouped into, read from data/shiplines/shiplines.json.
 * <p>
 * A ship's <em>type</em> is the SSD's own designation for that exact variant — "D7C", "CA+".
 * Its <em>line</em> is the family that variant belongs to, and it is the line that the fleet
 * construction rules care about: a leader needs consorts of its own kind, and "its own kind"
 * spans a line rather than a hull. The Klingon D7C leads the CA line, which is also where the
 * D6 and the D7 sit; a Federation CC leads the same line under a different flag.
 * <p>
 * Kept as data rather than an enum on purpose. No rule ever names a particular line — every
 * use is an equality test between two ships or a label in a picker — so the vocabulary can grow
 * by editing this file, while {@code ShipLineCatalogTest} keeps a typo from quietly inventing a
 * line nothing else belongs to.
 */
public final class ShipLineCatalog {

    /** One catalogued line. */
    public static final class Entry {
        public final String code;   // as written in a ship file's "line", e.g. "CA"
        public final String name;   // display name, e.g. "Heavy Cruiser"

        Entry(String code, String name) {
            this.code = code;
            this.name = name;
        }

        @Override
        public String toString() {
            return code + " (" + name + ")";
        }
    }

    private static final Map<String, Entry> registry = new LinkedHashMap<>();
    private static boolean loaded = false;

    private ShipLineCatalog() {
    }

    /** Load the catalogue from a shiplines.json file. Replaces anything already loaded. */
    public static void load(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        registry.clear();
        for (JsonNode n : root.path("lines")) {
            Entry e = new Entry(n.path("code").asText(), n.path("name").asText());
            registry.put(e.code.toLowerCase(), e);
        }
        loaded = true;
    }

    /** Convenience: load from the usual path under a data root. */
    public static void loadDefault(String dataRoot) throws IOException {
        load(new File(dataRoot, "shiplines/shiplines.json"));
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** The entry for a line code, or null if it is not catalogued. */
    public static Entry get(String code) {
        return code == null ? null : registry.get(code.toLowerCase());
    }

    /** True if {@code code} is a line this catalogue knows. */
    public static boolean isKnown(String code) {
        return get(code) != null;
    }

    /** Display name for a code, falling back to the code itself when uncatalogued. */
    public static String nameOf(String code) {
        Entry e = get(code);
        return e == null ? code : e.name;
    }

    public static List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }
}
