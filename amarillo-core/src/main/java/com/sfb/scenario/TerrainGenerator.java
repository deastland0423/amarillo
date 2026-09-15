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
 * Scatters terrain, so a map can be described rather than enumerated.
 * <p>
 * An asteroid field is nine hand-written hexes in data/scenarios/training.json, and would be
 * nine more in the next scenario. A plan says what is wanted — a field in this region, at about
 * this density — and produces the hexes, for a scenario file and for a battle assembled from
 * fleets alike.
 * <p>
 * Generation is seeded and therefore repeatable: every player must be looking at the same map,
 * and a battle reloaded later must be the battle that was played. A plan with no seed gets one
 * at first use and keeps it, so a scenario file that omits it still lays out the same way every
 * time it is read.
 * <p>
 * Nothing is placed where it would be unfair or unplayable: keep-clear regions — the deployment
 * zones, in practice — are left empty, because a fleet cannot set up inside an asteroid field
 * it did not choose.
 */
public final class TerrainGenerator {

    /** What to scatter, and where. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Plan {
        /** "ASTEROID_FIELD", "PLANET" or "GAS_GIANT". */
        public String type;
        /** Where it may go. Null means the whole map. */
        public MapRegion region;
        /** ASTEROID_FIELD: share of the region's hexes to fill, 0–1. */
        public double density = 0.12;
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
    }

    private TerrainGenerator() {
    }

    /**
     * Turn plans into the terrain entries a ScenarioSpec carries.
     *
     * @param keepClear regions nothing may be placed in — the deployment zones
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
                case "ASTEROID_FIELD" -> scatterAsteroids(plan, rng, keepClear, used, out, mapCols, mapRows);
                case "PLANET"         -> placeBodies(plan, rng, keepClear, used, out, mapCols, mapRows, 0);
                case "GAS_GIANT"      -> placeBodies(plan, rng, keepClear, used, out, mapCols, mapRows,
                                                     Math.max(0, plan.radius));
                default -> System.err.println("TerrainGenerator: unknown plan type '" + plan.type + "' — skipped");
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------

    private static void scatterAsteroids(Plan plan, Random rng, List<MapRegion> keepClear,
                                         Set<String> used, List<ScenarioSpec.TerrainSetup> out,
                                         int mapCols, int mapRows) {
        List<Location> candidates = openHexes(plan.region, keepClear, used, mapCols, mapRows);
        if (candidates.isEmpty())
            return;

        double density = Math.max(0, Math.min(1, plan.density));
        int wanted = (int) Math.round(candidates.size() * density);
        if (wanted <= 0)
            return;

        Collections.shuffle(candidates, rng);
        for (Location loc : candidates.subList(0, Math.min(wanted, candidates.size()))) {
            out.add(setup("ASTEROID", loc, 0, null));
            used.add(key(loc));
        }
    }

    /**
     * Planets and gas giants: solid bodies that need room around them, so they are placed one
     * at a time and each claims its footprint plus a gap.
     */
    private static void placeBodies(Plan plan, Random rng, List<MapRegion> keepClear,
                                    Set<String> used, List<ScenarioSpec.TerrainSetup> out,
                                    int mapCols, int mapRows, int radius) {
        for (int i = 0; i < Math.max(0, plan.count); i++) {
            List<Location> candidates = openHexes(plan.region, keepClear, used, mapCols, mapRows);
            // A body needs its whole footprint clear, not just its centre.
            candidates.removeIf(c -> !footprintIsOpen(c, radius, plan.region, keepClear, used,
                                                      mapCols, mapRows));
            if (candidates.isEmpty())
                return;

            Location centre = candidates.get(rng.nextInt(candidates.size()));
            out.add(setup(radius > 0 ? "GAS_GIANT" : "PLANET", centre, radius, plan.name));

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
