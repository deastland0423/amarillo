package com.sfb.objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The pre-game view of shuttles and fighters, read from data/shuttles/shuttles.json.
 * <p>
 * A {@link com.sfb.objects.shuttles.Shuttle} answers what a shuttle is doing once it exists;
 * this answers what exists at all — what a bay may be stocked with, whose it is, from which
 * year, and what it costs to buy. Fleet building, force-legality checks and pickers all need
 * those answers before any shuttle has been built.
 * <p>
 * Deliberately not under data/factions: that tree is ships. Suicide shuttles, scatter-packs and
 * wild weasels are deliberately absent too — they are roles an admin shuttle takes on rather
 * than types anyone stocks, and CoiLoadout prices those conversions in Commander's Option
 * points.
 */
public final class ShuttleCatalog {

    /** Faction token meaning every faction may field the type. */
    public static final String ANY_FACTION = "any";

    /** One catalogued type. Immutable; the live object is built by ShuttleBay.buildShuttle. */
    public static final class Entry {
        public final String type;          // key used by ship JSON and the ShuttleBay factory
        public final String name;          // display name
        public final String kind;          // "fighter" (costs BPV) or "shuttle" (carried free)
        public final List<String> factions;
        public final int year;             // first year of service
        public final int speed;
        public final int hull;
        public final int crippled;         // damage that cripples it; 0 = no crippled state
        public final int bpv;              // 0 for anything not bought with BPV

        Entry(String type, String name, String kind, List<String> factions,
              int year, int speed, int hull, int crippled, int bpv) {
            this.type = type;
            this.name = name;
            this.kind = kind;
            this.factions = List.copyOf(factions);
            this.year = year;
            this.speed = speed;
            this.hull = hull;
            this.crippled = crippled;
            this.bpv = bpv;
        }

        /** True if this type costs BPV and is added to its carrier's price. */
        public boolean isFighter() {
            return "fighter".equalsIgnoreCase(kind);
        }

        /** True if {@code faction} may field it — either its own, or it is open to all. */
        public boolean availableTo(String faction) {
            return factions.stream().anyMatch(f ->
                    ANY_FACTION.equalsIgnoreCase(f) || f.equalsIgnoreCase(faction));
        }

        /** True if it is in service by {@code gameYear}. */
        public boolean availableIn(int gameYear) {
            return gameYear >= year;
        }

        @Override
        public String toString() {
            return type + " (" + name + ", bpv " + bpv + ")";
        }
    }

    private static final Map<String, Entry> registry = new LinkedHashMap<>();
    private static boolean loaded = false;

    private ShuttleCatalog() {
    }

    /** Load the catalogue from a shuttles.json file. Replaces anything already loaded. */
    public static void load(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        registry.clear();
        for (JsonNode n : root.path("shuttles")) {
            List<String> factions = new ArrayList<>();
            for (JsonNode f : n.path("factions"))
                factions.add(f.asText());
            Entry e = new Entry(
                    n.path("type").asText(),
                    n.path("name").asText(),
                    n.path("kind").asText("shuttle"),
                    factions,
                    n.path("year").asInt(0),
                    n.path("speed").asInt(0),
                    n.path("hull").asInt(0),
                    n.path("crippled").asInt(0),
                    n.path("bpv").asInt(0));
            registry.put(e.type.toLowerCase(), e);
        }
        loaded = true;
    }

    /** Convenience: load from the usual path under a data root. */
    public static void loadDefault(String dataRoot) throws IOException {
        load(new File(dataRoot, "shuttles/shuttles.json"));
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** The entry for a type key, or null if it is not catalogued. */
    public static Entry get(String type) {
        return type == null ? null : registry.get(type.toLowerCase());
    }

    public static List<Entry> all() {
        return Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }

    /** Everything {@code faction} may field in {@code gameYear} — the picker's list. */
    public static List<Entry> availableFor(String faction, int gameYear) {
        return registry.values().stream()
                .filter(e -> e.availableTo(faction) && e.availableIn(gameYear))
                .collect(Collectors.toList());
    }

    /**
     * What a type costs to buy. Fighters are added to their carrier's price; plain shuttles
     * ride along free, so this is zero for them.
     */
    public static int bpvOf(String type) {
        Entry e = get(type);
        return e == null ? 0 : e.bpv;
    }
}
