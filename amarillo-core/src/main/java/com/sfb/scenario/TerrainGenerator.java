package com.sfb.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sfb.properties.Location;
import com.sfb.utilities.MapUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Lays out terrain, so a map can be described rather than enumerated.
 * <p>
 * An asteroid field is nine hand-written hexes in data/scenarios/training.json, and would be
 * nine more in the next scenario. A plan says what is wanted and produces the hexes, for a
 * written scenario and for a battle assembled from fleets alike.
 * <p>
 * The asteroid field is not a matter of taste: P3.11 gives a procedure, and this follows it.
 * Eighteen counters go in named hexes, each is rolled one hex in a random direction, and every
 * hex within two of a counter is an asteroid hex (P3.12). Overlapping counters do not stack —
 * a hex is an asteroid hex or it is not.
 * <p>
 * Rolling is seeded and therefore repeatable: every player must be looking at the same map, and
 * a battle reloaded later must be the battle that was played. A plan with no seed gets one at
 * first use and keeps it, so a scenario file that omits it still lays out the same way every
 * time it is read.
 * <p>
 * Keep-clear regions — the deployment zones, in practice — apply to bodies that are placed
 * freely. They do not apply to the standard field, whose positions the rule fixes: a fleet
 * setting up in an asteroid field is the scenario, not a mistake.
 */
public final class TerrainGenerator {

    /** What to lay out, and where. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plan {
        /** "ASTEROID_FIELD", "PLANET" or "GAS_GIANT". */
        public String type;
        /** PLANET and GAS_GIANT: where it may go. Null means the whole map. */
        public MapRegion region;
        /** PLANET and GAS_GIANT: how many to place. */
        public int count = 1;
        /** GAS_GIANT: body radius (P2.22). */
        public int radius = 1;
        /** Hexes to leave between separately placed bodies. */
        public int spacing = 3;
        /** Set once so the same plan always lays out the same way. */
        public Long seed;
        /** Optional display name for a planet or giant. */
        public String name;
        /** PLANET and GAS_GIANT: put it exactly here, instead of anywhere in the region. */
        public String hex;
        /** GAS_GIANT: planetary ring bands (P2.223), each a hex-distance range from the body. */
        public List<ScenarioSpec.RingBand> rings;
    }

    // -------------------------------------------------------------------------
    // The menu a host picks from for a pick-up battle
    // -------------------------------------------------------------------------

    /** What a host may put on the map. */
    public static final List<String> CHOICES =
            List.of("OPEN_SPACE", "ASTEROID_FIELD", "PLANET", "GAS_GIANT");

    /**
     * Turn a menu choice into plans, rolling for whatever the choice leaves open.
     * <p>
     * A gas giant is the feature of the map it appears on, so it goes in the middle rather than
     * wherever a die puts it — both hand-written giants in data/scenarios sit at 2116. What is
     * rolled is what makes one game differ from the next: how big it is, and whether it has
     * rings. The two rolls are independent, so the very large ringed giant that fills the map
     * turns up about one game in twelve rather than every other time.
     * <p>
     * Seeded, like everything else here: the host rolls once and every player sees the result.
     */
    public static List<Plan> forChoice(String choice, long seed, int mapCols, int mapRows) {
        if (choice == null)
            return List.of();
        Random rng = new Random(seed);

        switch (choice.toUpperCase()) {
            case "OPEN_SPACE":
                return List.of();

            case "ASTEROID_FIELD": {
                Plan p = new Plan();
                p.type = "ASTEROID_FIELD";
                p.seed = seed;
                return List.of(p);
            }

            case "PLANET": {
                Plan p = new Plan();
                p.type = "PLANET";
                p.seed = seed;
                p.hex = centreOf(mapCols, mapRows);
                return List.of(p);
            }

            case "GAS_GIANT": {
                Plan p = new Plan();
                p.type = "GAS_GIANT";
                p.seed = seed;
                p.hex = centreOf(mapCols, mapRows);
                p.radius = rolledGiantRadius(rng.nextInt(6) + 1);
                // An even chance of rings, so that even the largest giant sometimes leaves the
                // rest of the map to the players.
                if (rng.nextInt(2) == 0)
                    p.rings = ringsFor(p.radius);
                return List.of(p);
            }

            default:
                System.err.println("TerrainGenerator: unknown terrain choice '" + choice + "'");
                return List.of();
        }
    }

    /**
     * How big a rolled gas giant is. Spans the threshold that matters: P2.222 gives giants of
     * seven hexes or more across — radius 3 — a no-entry atmosphere ring, so the table runs
     * from below it to the radius 5 of the largest one already in data/scenarios.
     */
    static int rolledGiantRadius(int die) {
        if (die <= 2) return 2;
        if (die <= 4) return 3;
        if (die == 5) return 4;
        return 5;
    }

    /**
     * Two ring bands, standing off the body far enough to be distinct from it and scaled to its
     * size. For a radius 4 giant this gives 6–7 and 9–11, near enough to the 7–8 and 10–12 of
     * the hand-written Proxima IV.
     */
    static List<ScenarioSpec.RingBand> ringsFor(int radius) {
        return List.of(band(radius + 2, radius + 3), band(radius + 5, radius + 7));
    }

    private static ScenarioSpec.RingBand band(int inner, int outer) {
        ScenarioSpec.RingBand b = new ScenarioSpec.RingBand();
        b.inner = inner;
        b.outer = outer;
        return b;
    }

    /** The middle of the map, where a feature belongs. */
    static String centreOf(int mapCols, int mapRows) {
        return String.format("%02d%02d", Math.max(1, mapCols / 2), Math.max(1, mapRows / 2));
    }

    /**
     * Where the eighteen asteroid counters start, before they are rolled (P3.11). The rule
     * names these hexes; they are not a choice.
     */
    static final String[] COUNTER_HEXES = {
            "0505", "0713", "1007", "0522", "0730", "1024",
            "1905", "2113", "2407", "1922", "2128", "2424",
            "3322", "3513", "3807", "3305", "3528", "3824"
    };

    /** A die face, in the order the six hex directions run: 1 = A, 6 = F. */
    /** The six a unit may face; see MapUtils.FACINGS, which is where they live. */
    private static final int[] DIRECTIONS = com.sfb.utilities.MapUtils.FACINGS;

    /** Every hex within this range of a counter is an asteroid hex (P3.12). */
    static final int FIELD_RADIUS = 2;

    private TerrainGenerator() {
    }

    /**
     * Turn plans into the terrain entries a ScenarioSpec carries.
     *
     * @param keepClear regions no freely-placed body may occupy — the deployment zones.
     *                  The standard asteroid field ignores it: P3.11 fixes where it goes.
     */
    public static List<ScenarioSpec.TerrainSetup> generate(
            List<Plan> plans, List<MapRegion> keepClear, int mapCols, int mapRows) {

        List<ScenarioSpec.TerrainSetup> out = new ArrayList<>();
        if (plans == null)
            return out;

        Set<String> used = new LinkedHashSet<>();   // every hex already spoken for
        for (Plan plan : plans) {
            if (plan == null || plan.type == null)
                continue;
            if (plan.seed == null)
                plan.seed = new Random().nextLong();
            Random rng = new Random(plan.seed);

            switch (plan.type.toUpperCase()) {
                case "ASTEROID_FIELD" -> standardField(rng, used, out, mapCols, mapRows);
                case "PLANET"         -> placeBodies(plan, rng, keepClear, used, out, mapCols, mapRows, 0);
                case "GAS_GIANT"      -> placeBodies(plan, rng, keepClear, used, out, mapCols, mapRows,
                                                     Math.max(0, plan.radius));
                default -> System.err.println("TerrainGenerator: unknown plan type '" + plan.type + "' — skipped");
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------

    /**
     * The standard asteroid field (P3.11): eighteen counters in named hexes, each rolled one
     * hex in a random direction, with every hex within two of a counter becoming an asteroid
     * hex (P3.12).
     * <p>
     * Expanded to one entry per hex rather than kept as counters with a radius, because that
     * is what an asteroid hex is downstream — Game keeps a set of hexes and the map draws a
     * token in each, and P3.12 is explicit that a hex near two counters is no different from a
     * hex near one.
     */
    private static void standardField(Random rng, Set<String> used,
                                      List<ScenarioSpec.TerrainSetup> out,
                                      int mapCols, int mapRows) {
        for (String start : COUNTER_HEXES) {
            Location counter = MapRegion.parse(start);
            if (counter == null)
                continue;

            // "Roll one die for each counter and move it in the indicated direction one hex."
            Location rolled = MapUtils.getAdjacentHex(
                    counter, DIRECTIONS[rng.nextInt(DIRECTIONS.length)], mapCols, mapRows);
            if (rolled != null)
                counter = rolled;   // a counter rolled off the map stays where it was

            for (Location hex : within(counter, FIELD_RADIUS, mapCols, mapRows)) {
                if (used.add(key(hex)))
                    out.add(setup("ASTEROID", hex, 0, null));
            }
        }
    }

    /**
     * Planets and gas giants: solid bodies that need room around them, so they are placed one
     * at a time and each claims its footprint plus a gap.
     */
    private static void placeBodies(Plan plan, Random rng, List<MapRegion> keepClear,
                                    Set<String> used, List<ScenarioSpec.TerrainSetup> out,
                                    int mapCols, int mapRows, int radius) {
        // A named hex is where it goes, full stop — a gas giant is the feature of its map and
        // the host chose the middle of it, not a region to be scattered within.
        if (plan.hex != null && !plan.hex.isBlank()) {
            Location fixed = MapRegion.parse(plan.hex);
            if (fixed != null) {
                out.add(bodySetup(plan, fixed, radius));
                for (Location l : within(fixed, radius, mapCols, mapRows))
                    used.add(key(l));
            }
            return;
        }

        for (int i = 0; i < Math.max(0, plan.count); i++) {
            List<Location> candidates = openHexes(plan.region, keepClear, used, mapCols, mapRows);
            // A body needs its whole footprint clear, not just its centre.
            candidates.removeIf(c -> !footprintIsOpen(c, radius, plan.region, keepClear, used,
                                                      mapCols, mapRows));
            if (candidates.isEmpty())
                return;

            Location centre = candidates.get(rng.nextInt(candidates.size()));
            out.add(bodySetup(plan, centre, radius));

            // Claim the body and a berth around it, so the next one lands elsewhere.
            for (Location l : within(centre, radius + Math.max(0, plan.spacing), mapCols, mapRows))
                used.add(key(l));
        }
    }

    private static boolean footprintIsOpen(Location centre, int radius, MapRegion region,
                                           List<MapRegion> keepClear, Set<String> used,
                                           int mapCols, int mapRows) {
        for (Location l : within(centre, radius, mapCols, mapRows)) {
            if (used.contains(key(l)))
                return false;
            if (region != null && !region.contains(l, mapCols, mapRows))
                return false;
            if (isKeptClear(l, keepClear, mapCols, mapRows))
                return false;
        }
        return true;
    }

    /** Every hex of the region that is still free and not deliberately kept empty. */
    private static List<Location> openHexes(MapRegion region, List<MapRegion> keepClear,
                                            Set<String> used, int mapCols, int mapRows) {
        MapRegion r = region != null ? region : MapRegion.anywhere();
        List<Location> out = new ArrayList<>();
        for (int c = 1; c <= mapCols; c++)
            for (int w = 1; w <= mapRows; w++) {
                Location loc = new Location(c, w);
                if (!r.contains(loc, mapCols, mapRows))
                    continue;
                if (used.contains(key(loc)))
                    continue;
                if (isKeptClear(loc, keepClear, mapCols, mapRows))
                    continue;
                out.add(loc);
            }
        return out;
    }

    private static boolean isKeptClear(Location loc, List<MapRegion> keepClear,
                                       int mapCols, int mapRows) {
        if (keepClear == null)
            return false;
        for (MapRegion k : keepClear)
            if (k != null && k.contains(loc, mapCols, mapRows))
                return true;
        return false;
    }

    private static List<Location> within(Location centre, int radius, int mapCols, int mapRows) {
        List<Location> out = new ArrayList<>();
        for (int c = 1; c <= mapCols; c++)
            for (int r = 1; r <= mapRows; r++) {
                Location loc = new Location(c, r);
                if (MapUtils.getRange(centre, loc) <= radius)
                    out.add(loc);
            }
        return out;
    }

    /** A planet or giant, with whatever rings the plan asked for. */
    private static ScenarioSpec.TerrainSetup bodySetup(Plan plan, Location centre, int radius) {
        ScenarioSpec.TerrainSetup s = setup(radius > 0 ? "GAS_GIANT" : "PLANET", centre, radius,
                                            plan.name);
        if (plan.rings != null && !plan.rings.isEmpty() && radius > 0)
            s.rings = new ArrayList<>(plan.rings);
        return s;
    }

    private static ScenarioSpec.TerrainSetup setup(String type, Location loc, int radius, String name) {
        ScenarioSpec.TerrainSetup s = new ScenarioSpec.TerrainSetup();
        s.type = type;
        s.hex = String.format("%02d%02d", loc.getX(), loc.getY());
        s.radius = radius;
        s.name = name;
        return s;
    }

    private static String key(Location loc) {
        return loc.getX() + "," + loc.getY();
    }
}
