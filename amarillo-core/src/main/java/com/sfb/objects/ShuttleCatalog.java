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
        /**
         * Short form used in a launched shuttle's name, e.g. "Admin", "GAS". Falls back to
         * the display name. It names what the craft IS, never the role it is playing —
         * that is the whole point (J3.0, FD7.0, J2.0 all rely on a role being unreadable
         * from outside).
         */
        public final String shortName;
        /**
         * The SSD designation — "AAS", "Stinger-1", "HAAS-E". What the craft is CALLED, at
         * the length a list can afford to print.
         * <p>
         * Distinct from {@link #shortName}, which is tuned for a map counter and is terser
         * still ("St-1"): a counter has a few pixels, a hangar row has a line and the name
         * is the headline of it. Falls back to shortName, then to the display name, so a
         * catalogue entry that does not bother saying still names the craft sensibly.
         */
        public final String designation;
        public final String kind;          // "fighter" (costs BPV) or "shuttle" (carried free)
        public final List<String> factions;
        public final int year;             // first year of service
        public final int speed;
        public final int hull;
        public final int crippled;         // damage that cripples it; 0 = no crippled state
        public final int bpv;              // 0 for anything not bought with BPV
        /** J3.18: may this be charged as a Wild Weasel? Fighters never may. */
        public final boolean canWeasel;
        /**
         * J2.222: may this be armed as a suicide shuttle? Admin, minesweeping, MRS, SWAC
         * and minelaying shuttles may; fighters, HTS and GAS may not — a third list again
         * distinct from the other two.
         */
        public final boolean canSuicide;
        /**
         * FD7.11: rack spaces of drones this may carry as a scatter pack; 0 = not a
         * qualified shuttle. One field answers both whether and how much, and the two
         * eligibility lists are NOT the same — a GAS may weasel but not scatter-pack, a
         * fighter the reverse.
         */
        public final int scatterPackSize;

        Entry(String type, String name, String kind, List<String> factions,
              int year, int speed, int hull, int crippled, int bpv,
              boolean canWeasel, boolean canSuicide, int scatterPackSize,
              String shortName, String designation) {
            this.shortName = shortName == null || shortName.isBlank() ? name : shortName;
            this.designation = designation == null || designation.isBlank()
                    ? this.shortName : designation;
            this.canWeasel = canWeasel;
            this.canSuicide = canSuicide;
            this.scatterPackSize = scatterPackSize;
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

        /** FD7.11: true if this type may be armed and launched as a scatter pack. */
    public boolean canScatterPack() {
        return scatterPackSize > 0;
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

    /**
     * One era of a fighter line: the year it begins, and which type fills each role.
     * <p>
     * Roles are named rather than positional ("standard", "heavy", "ew") because a carrier
     * declares how many of each it carries and the era decides what they are. An era that has
     * no type for a role leaves it absent, and {@link #typeFor} falls back to the standard —
     * which is how a Hydran RN carries nine Stinger-1s before the Stinger-E existed and six
     * Stinger-2s, two Stinger-Hs and one Stinger-E after, from a single declaration.
     */
    public static final class LineEra {
        public static final String STANDARD = "standard";

        public final int from;
        private final Map<String, String> byRole;

        LineEra(int from, Map<String, String> byRole) {
            this.from = from;
            this.byRole = Collections.unmodifiableMap(new LinkedHashMap<>(byRole));
        }

        /** The type filling {@code role} in this era, falling back to the standard fighter. */
        public String typeFor(String role) {
            String t = byRole.get(role);
            return t != null ? t : byRole.get(STANDARD);
        }

        /** True if this era has a type of its own for the role (no fallback needed). */
        public boolean hasOwnTypeFor(String role) {
            return byRole.containsKey(role);
        }

        public Map<String, String> roles() {
            return byRole;
        }

        @Override
        public String toString() {
            return "from Y" + from + " " + byRole;
        }
    }

    /** Fighter lines by name, each a list of eras in ascending year order. */
    private static final Map<String, List<LineEra>> fighterLines = new LinkedHashMap<>();

    /** Load the catalogue from a shuttles.json file. Replaces anything already loaded. */
    public static void load(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
        registry.clear();
        fighterLines.clear();
        JsonNode lines = root.path("fighterLines");
        for (java.util.Iterator<String> it = lines.fieldNames(); it.hasNext(); ) {
            String lineName = it.next();
            List<LineEra> eras = new ArrayList<>();
            for (JsonNode eraNode : lines.path(lineName)) {
                Map<String, String> byRole = new LinkedHashMap<>();
                for (java.util.Iterator<String> rf = eraNode.fieldNames(); rf.hasNext(); ) {
                    String role = rf.next();
                    if (!"from".equals(role))
                        byRole.put(role, eraNode.path(role).asText());
                }
                eras.add(new LineEra(eraNode.path("from").asInt(0), byRole));
            }
            eras.sort(java.util.Comparator.comparingInt(e -> e.from));
            fighterLines.put(lineName.toLowerCase(), eras);
        }
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
                    n.path("bpv").asInt(0),
                    n.path("canWeasel").asBoolean(false),
                    n.path("canSuicide").asBoolean(false),
                    n.path("scatterPackSize").asInt(0),
                    n.path("shortName").asText(null),
                    n.path("designation").asText(null));
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
    /**
     * Where to look when nobody has loaded the catalogue explicitly. Mirrors
     * OrionCartelTable: the working directory differs between the server (repo root) and a
     * test run (module directory), and forgetting to load is not an acceptable failure —
     * every role eligibility answer depends on this file, so an unloaded catalogue silently
     * makes every shuttle ineligible for everything.
     */
    private static final String[] DEFAULT_PATHS = {
        "data/shuttles/shuttles.json",
        "../data/shuttles/shuttles.json",
    };

    /** Load from the standard location if nothing has loaded it yet. */
    private static synchronized void ensureLoaded() {
        if (loaded)
            return;
        for (String path : DEFAULT_PATHS) {
            File f = new File(path);
            if (f.exists()) {
                try {
                    load(f);
                    return;
                } catch (IOException e) {
                    System.err.println("Failed to read shuttle catalogue at " + path
                            + ": " + e.getMessage());
                }
            }
        }
        System.err.println("Shuttle catalogue not found on any default path — every shuttle"
                + " will report itself ineligible for every special role");
    }

    public static Entry get(String type) {
        ensureLoaded();
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

    /**
     * The name stem a craft of this type is numbered from — "HAAS-E" giving "HAAS-E-1".
     * <p>
     * Here rather than in Shuttles because the designation it prefers is a catalogue field, and
     * two callers now need the same answer: the bay built from a literal list, and a complement
     * resolved from a fighter line.
     */
    public static String displayNameOf(String type) {
        if (type == null || type.isEmpty())
            return "Shuttle";
        Entry e = get(type);
        if (e != null && e.designation != null)
            return e.designation;
        switch (type.toLowerCase()) {
            case "suicide":     return "Suicide";
            case "scatterpack": return "ScatterPack";
            default:
                return Character.toUpperCase(type.charAt(0)) + type.substring(1);
        }
    }

    // -------------------------------------------------------------------------
    // Fighter lines (J4.4): which fighter fills a role in a given year
    // -------------------------------------------------------------------------

    /** Every era of a named line, ascending by year; empty if the line is unknown. */
    public static List<LineEra> lineEras(String lineName) {
        ensureLoaded();
        if (lineName == null)
            return Collections.emptyList();
        return fighterLines.getOrDefault(lineName.toLowerCase(), Collections.emptyList());
    }

    /** Every line name the catalogue knows. */
    public static java.util.Set<String> lineNames() {
        ensureLoaded();
        return Collections.unmodifiableSet(fighterLines.keySet());
    }

    /**
     * The era of {@code lineName} in force in {@code year} — the latest one whose year has
     * arrived — or null if the line is unknown or had not started yet.
     * <p>
     * A year of 0 means "unspecified", which happens for a ship built outside any scenario.
     * Those get the EARLIEST era rather than nothing, because a carrier with an empty bay is a
     * worse answer than a carrier with its original fighters.
     */
    public static LineEra eraFor(String lineName, int year) {
        List<LineEra> eras = lineEras(lineName);
        if (eras.isEmpty())
            return null;
        if (year <= 0)
            return eras.get(0);
        LineEra current = null;
        for (LineEra e : eras) {
            if (e.from <= year)
                current = e;
            else
                break;
        }
        return current;
    }

    /**
     * The fighter type filling {@code role} on {@code lineName} in {@code year}, or null if
     * the line is unknown or has not begun. Falls back to the line's standard fighter where
     * the era has no type for the role.
     */
    public static String fighterFor(String lineName, String role, int year) {
        LineEra era = eraFor(lineName, year);
        return era == null ? null : era.typeFor(role);
    }
}
