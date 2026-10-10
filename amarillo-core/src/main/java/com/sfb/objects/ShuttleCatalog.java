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
import com.sfb.objects.shuttles.Fighter;

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
    /**
     * What a craft is built WITH: its armament and the handful of fitted systems that are not
     * weapons (J4.8x, J4.96x, D11.0).
     *
     * One record rather than six more constructor parameters, and on the catalogue rather than in
     * a Java class per fighter. Sixteen fighter classes used to hold this, every one of them a
     * constructor and nothing else, with their stats duplicated here and kept honest only by a
     * test — which is how three of them came to carry their base fighter's BPV. A fighter is a
     * ROW now, and a new faction's suite is a data change.
     *
     * @param weapons                     recipes, built by {@link WeaponFactory} exactly as a
     *                                    ship's are
     * @param ewPods                      J4.962: pods fitted ON RAILS, each displacing a drone
     * @param fixedEwPods                 J4.964: pods built in and not removable — the Hydran
     *                                    Stinger-E, whose pods replaced its fusions outright
     * @param chaffPacks                  D11.0: chaff aboard, already a plain count on Shuttle
     * @param twoSeater                   J4.43: two seats, which every EW fighter has. NOT
     *                                    derived from the pods: J4.43 permits a two-seat fighter
     *                                    that is no EW fighter, and an EW fighter that has lost
     *                                    its pods is still one
     * @param mayLaunchAtDifferentTargets J4.242's exception, named for the F-15 and TAAS
     * @param mayLaunchTwoStandardDrones  J4.242's other half, the same ships
     */
    public record Loadout(
            List<ShipSpec.WeaponSpec> weapons,
            int ewPods,
            int fixedEwPods,
            int chaffPacks,
            boolean twoSeater,
            boolean mayLaunchAtDifferentTargets,
            boolean mayLaunchTwoStandardDrones) {

        static final Loadout NONE =
                new Loadout(List.of(), 0, 0, 0, false, false, false);
    }

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

        /** How this craft is armed and fitted; {@link Loadout#NONE} for a row that says nothing. */
        public final Loadout loadout;

        /**
         * Counter art for this craft, as a path under {@code amarillo-web/public/tokens}, or null
         * to be drawn as its faction's generic shuttle.
         *
         * <p>On the ROW rather than on a class, because a fighter is a catalogue row and not a
         * class (J4.4) — the same reason its stats and armament live here. It is the craft's
         * equivalent of {@code ShipSpec.tokenArt}, and it exists because the Stinger art had been
         * drawn and sat unusable: nothing outside ShipSpec could name a picture, so every craft
         * in the game drew its faction's generic shuttle counter.
         */
        public final String tokenArt;

        Entry(String type, String name, String kind, List<String> factions,
              int year, int speed, int hull, int crippled, int bpv,
              boolean canWeasel, boolean canSuicide, int scatterPackSize,
              String shortName, String designation, Loadout loadout, String tokenArt) {
            this.tokenArt = tokenArt == null || tokenArt.isBlank() ? null : tokenArt;
            this.loadout = loadout == null ? Loadout.NONE : loadout;
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
     * Roles are named rather than positional (superiority / attack / assault / ew) because a carrier
     * declares how many of each it carries and the era decides what they are. An era that has
     * no type for a role leaves it absent, and {@link #typeFor} falls back to the superiority —
     * which is how a Hydran RN carries nine Stinger-1s before the Stinger-E existed and six
     * Stinger-2s, two Stinger-Hs and one Stinger-E after, from a single declaration.
     */
    public static final class LineEra {
        /**
         * The role every other falls back to (see {@link #typeFor}). Named SUPERIORITY after the
         * annex's own term: "two will be superiority fighters and two will be assault fighters".
         * Load-bearing in code as well as data, so {@code FighterRoleNameTest} pins that it is
         * itself a role and that every era declares one.
         */
        public static final String STANDARD = "superiority";

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

    /**
     * Type keys claimed by more than one file in the last load, mapped to a description of which
     * file lost. Empty when the folder is clean.
     * <p>
     * Only possible since the catalogue was split per faction (2026-10-06). One file cannot hold
     * two entries for {@code f18}; seven files can, and the loser would vanish in silence — which
     * is precisely how a Hydran hull once disappeared from {@code ShipLibrary}. The detection ships
     * with the split rather than after it.
     */
    private static final Map<String, String> duplicates = new LinkedHashMap<>();

    /** Type keys claimed twice in the last load. Empty when the data is clean. */
    public static Map<String, String> duplicateKeys() {
        return java.util.Collections.unmodifiableMap(duplicates);
    }

    /**
     * Load every {@code *.json} in a directory into ONE catalogue.
     * <p>
     * The registry is cleared once here and NOT between files — the same choice
     * {@code ShipLibrary.loadAllSpecs} documents, and the whole point of the split: each file adds
     * its own empire's craft and fighter lines to a shared registry. A per-file clear would leave
     * whichever file sorted last as the entire catalogue.
     * <p>
     * Files are taken in name order so a duplicate always reports the same winner, which keeps the
     * failure reproducible rather than filesystem-dependent.
     */
    public static void loadAll(File dir) throws IOException {
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
        if (files == null)
            throw new IOException("Not a shuttle catalogue directory: " + dir);
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        registry.clear();
        fighterLines.clear();
        duplicates.clear();
        for (File f : files)
            loadInto(f);
        loaded = true;
    }

    /**
     * Load one file, REPLACING anything already loaded.
     * <p>
     * Kept for a caller that genuinely wants a single file in isolation. Everything in the game
     * should use {@link #loadAll} or {@link #loadDefault} instead: since the split, one file is one
     * empire, and loading it alone leaves every other empire's craft uncatalogued — which reads as
     * "that fighter does not exist" rather than as an error.
     */
    public static void load(File file) throws IOException {
        registry.clear();
        fighterLines.clear();
        duplicates.clear();
        loadInto(file);
    }

    /** Add one file's contents to whatever is already loaded. */
    private static void loadInto(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(file);
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
            String lineKey = lineName.toLowerCase();
            if (fighterLines.containsKey(lineKey))
                duplicates.put("fighterLine:" + lineKey,
                        file.getName() + " re-declares a fighter line already loaded");
            fighterLines.put(lineKey, eras);
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
                    n.path("designation").asText(null),
                    loadoutFrom(n),
                    n.path("tokenArt").asText(null));
            String key = e.type.toLowerCase();
            if (registry.containsKey(key))
                duplicates.put(key, file.getName() + " re-declares type '" + e.type
                        + "', already catalogued; the earlier entry is replaced");
            registry.put(key, e);
        }
        loaded = true;
    }

    /**
     * Read a craft's armament and fitted systems out of its catalogue row.
     * <p>
     * Weapon recipes use the SAME shape a ship file uses — {@code type}, {@code designator},
     * {@code arcs}, plus whatever that weapon needs — so {@link WeaponFactory} builds a fighter's
     * phaser exactly as it builds a cruiser's. A row with no {@code weapons} array yields
     * {@link Loadout#NONE}, which is right for an administrative shuttle.
     */
    private static Loadout loadoutFrom(JsonNode n) {
        List<ShipSpec.WeaponSpec> weapons = new ArrayList<>();
        for (JsonNode w : n.path("weapons")) {
            ShipSpec.WeaponSpec ws = new ShipSpec.WeaponSpec();
            ws.type = w.path("type").asText(null);
            ws.designator = w.path("designator").asText(null);
            List<String> arcs = new ArrayList<>();
            for (JsonNode a : w.path("arcs"))
                arcs.add(a.asText());
            ws.arcs = arcs;
            ws.railType = w.path("railType").asText(null);
            ws.rackType = w.path("rackType").asText(null);
            ws.plasmaType = w.path("plasmaType").asText(null);
            // A fighter's plasma launches forward (launchDirections ["1"]), and this parse
            // dropped the key silently until a fighter had a launcher to need it.
            if (w.has("launchDirections")) {
                List<String> dirs = new ArrayList<>();
                for (JsonNode dir : w.path("launchDirections"))
                    dirs.add(dir.asText());
                ws.launchDirections = dirs;
            }
            ws.range = w.path("range").asInt(0);
            ws.spaces = w.path("spaces").asInt(0);
            ws.addType = w.path("addType").asText(null);
            weapons.add(ws);
        }
        return new Loadout(
                weapons,
                n.path("ewPods").asInt(0),
                n.path("fixedEwPods").asInt(0),
                n.path("chaffPacks").asInt(0),
                n.path("twoSeater").asBoolean(false),
                n.path("mayLaunchAtDifferentTargets").asBoolean(false),
                n.path("mayLaunchTwoStandardDrones").asBoolean(false));
    }

    /** Convenience: load the whole catalogue folder under a data root. */
    public static void loadDefault(String dataRoot) throws IOException {
        loadAll(new File(dataRoot, "shuttles"));
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
        "data/shuttles",
        "../data/shuttles",
    };

    /** Load from the standard location if nothing has loaded it yet. */
    private static synchronized void ensureLoaded() {
        if (loaded)
            return;
        for (String path : DEFAULT_PATHS) {
            File f = new File(path);
            if (f.isDirectory()) {
                try {
                    // loadAll, not load: the catalogue is a FOLDER of per-faction files, and
                    // load() would take whichever single file it was handed as the whole thing.
                    loadAll(f);
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
