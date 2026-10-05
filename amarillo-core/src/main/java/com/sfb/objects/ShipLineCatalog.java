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
        public final List<Double> moveCosts;  // costs ships of this line may pay; may be empty

        /**
         * Position in the catalogue file, and therefore the order these read best in to a
         * player: biggest warship first down to the smallest, then the civilian hulls.
         * <p>
         * The file's own order is the single source of truth, which is why this is an index
         * rather than a hand-kept number in the JSON — reordering the file reorders the screen,
         * with nothing to renumber and no way for the two to disagree. The fleet builder used to
         * sort groups alphabetically, which put Heavy Battlecruiser above Heavy Cruiser and the
         * freighters in the middle of the warships.
         */
        public final int order;

        /**
         * True for a non-combatant line — the freighters. The fleet-builder shelf draws its
         * dividing rule above the first civilian group, so these must stay contiguous at the end
         * of the file; {@code ShipLineCatalogTest} pins that.
         */
        public final boolean civilian;

        Entry(String code, String name, List<Double> moveCosts, int order, boolean civilian) {
            this.code = code;
            this.name = name;
            this.moveCosts = List.copyOf(moveCosts);
            this.order = order;
            this.civilian = civilian;
        }

        @Override
        public String toString() {
            return code + " (" + name + ")";
        }
    }

    /**
     * One catalogued SERIES — a generation of hulls within an empire, used as an OUTER grouping on
     * the fleet-builder shelf. Only the Romulans have them: Eagle, Kestrel, Hawk.
     * <p>
     * Separate from {@link Entry} because the two answer different questions. A line is what the
     * leader rules compare (S8.36) and every ship has one; a series is presentation only, nothing
     * reads it as a rule, and almost no ship has one. Sharing a type would invite a future rule to
     * ask a series for a {@code moveCost}.
     */
    public static final class Series {
        public final String code;      // as written in a ship file's "series", e.g. "KESTREL"
        public final String faction;   // whose generations these are
        public final String name;      // display name, e.g. "Kestrel series"
        public final String about;     // one line of history, for a tooltip
        /** Position in the catalogue file, and so the order the sections read in. */
        public final int order;

        Series(String code, String faction, String name, String about, int order) {
            this.code = code;
            this.faction = faction;
            this.name = name;
            this.about = about;
            this.order = order;
        }

        @Override
        public String toString() {
            return code + " (" + name + ")";
        }
    }

    private static final Map<String, Entry> registry = new LinkedHashMap<>();
    private static final Map<String, Series> seriesRegistry = new LinkedHashMap<>();
    private static boolean loaded = false;

    private ShipLineCatalog() {
    }

    /** Load the catalogue from a shiplines.json file. Replaces anything already loaded. */
    public static void load(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        registry.clear();
        seriesRegistry.clear();
        int order = 0;
        for (JsonNode n : root.path("lines")) {
            List<Double> costs = new ArrayList<>();
            for (JsonNode c : n.path("moveCosts"))
                costs.add(c.asDouble());
            Entry e = new Entry(n.path("code").asText(), n.path("name").asText(), costs,
                    order++, n.path("civilian").asBoolean(false));
            registry.put(e.code.toLowerCase(), e);
        }
        int seriesOrder = 0;
        for (JsonNode n : root.path("series")) {
            Series series = new Series(
                    n.path("code").asText(), n.path("faction").asText(),
                    n.path("name").asText(), n.path("about").asText(), seriesOrder++);
            seriesRegistry.put(series.code.toUpperCase(), series);
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

    /**
     * The movement costs ships of this line may pay; empty when the catalogue publishes none.
     * <p>
     * A cost does not identify a line on its own — BCH and CA both pay 1, CL and CW both pay
     * two thirds — but it cross-checks one, which is what {@code ShipLineCatalogTest} uses it
     * for. More than one is legitimate: the older Federation light cruisers pay 3/4 where the
     * newer ones pay 2/3, and both are CL.
     */
    public static List<Double> moveCostsOf(String code) {
        Entry e = get(code);
        return e == null ? List.of() : e.moveCosts;
    }

    /** True if {@code cost} is one this line permits, within rounding. */
    public static boolean permitsMoveCost(String code, double cost) {
        for (double c : moveCostsOf(code))
            if (Math.abs(c - cost) < 0.001)
                return true;
        return false;
    }

    /**
     * Where this line sorts on screen, or a value after every catalogued line when the code is
     * unknown — an uncatalogued line lands in a trailing "Other" group rather than at the top.
     */
    public static int orderOf(String code) {
        Entry e = get(code);
        return e == null ? Integer.MAX_VALUE : e.order;
    }

    /** True if this line is a non-combatant (the freighters). Unknown codes are not. */
    public static boolean isCivilian(String code) {
        Entry e = get(code);
        return e != null && e.civilian;
    }

    /** The series for a code, or null if it is not catalogued. */
    public static Series series(String code) {
        return code == null ? null : seriesRegistry.get(code.toUpperCase());
    }

    /** Display name for a series code, falling back to the code when uncatalogued. */
    public static String seriesNameOf(String code) {
        Series s = series(code);
        return s == null ? code : s.name;
    }

    /**
     * Where a series sorts on screen, or a value after every catalogued one when unknown — so an
     * uncatalogued series lands in a trailing section rather than at the top.
     */
    public static int seriesOrderOf(String code) {
        Series s = series(code);
        return s == null ? Integer.MAX_VALUE : s.order;
    }

    /** Every series, in catalogue order. */
    public static List<Series> allSeries() {
        return Collections.unmodifiableList(new ArrayList<>(seriesRegistry.values()));
    }

    /** Every line, in catalogue order. */
    public static List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }
}
