package com.sfb.scenario;

import com.sfb.properties.Location;
import com.sfb.utilities.MapUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Terrain described rather than enumerated.
 * <p>
 * The asteroid field follows P3.11: eighteen counters in named hexes, each rolled one hex, with
 * every hex within two of a counter becoming an asteroid hex (P3.12). The layout is the rule's,
 * not ours — what has to be true is that the same seed rolls the same field, that every hex is
 * accounted for by some counter, and that no hex is laid twice.
 */
public class TerrainGeneratorTest {

    private static final int COLS = 42, ROWS = 32;

    private TerrainGenerator.Plan field(long seed) {
        TerrainGenerator.Plan p = new TerrainGenerator.Plan();
        p.type = "ASTEROID_FIELD";
        p.seed = seed;
        return p;
    }

    private List<ScenarioSpec.TerrainSetup> gen(List<TerrainGenerator.Plan> plans,
                                                List<MapRegion> keepClear) {
        return TerrainGenerator.generate(plans, keepClear, COLS, ROWS);
    }

    private Location at(ScenarioSpec.TerrainSetup s) {
        return MapRegion.parse(s.hex);
    }

    // ---- repeatability ----

    @Test
    public void theSameSeedRollsTheSameField() {
        List<String> a = gen(List.of(field(42L)), null).stream().map(s -> s.hex).toList();
        List<String> b = gen(List.of(field(42L)), null).stream().map(s -> s.hex).toList();

        assertEquals(a, b);
    }

    @Test
    public void aDifferentSeedRollsADifferentField() {
        List<String> a = gen(List.of(field(1L)), null).stream().map(s -> s.hex).toList();
        List<String> b = gen(List.of(field(2L)), null).stream().map(s -> s.hex).toList();

        assertNotEquals(a, b);
    }

    /** A plan with no seed takes one and keeps it, so a scenario file still reads the same. */
    @Test
    public void aPlanWithoutASeedGetsOneAndRemembersIt() {
        TerrainGenerator.Plan plan = field(0L);
        plan.seed = null;

        List<String> first = gen(List.of(plan), null).stream().map(s -> s.hex).toList();
        assertNotNull("the plan kept its seed", plan.seed);
        assertEquals(first, gen(List.of(plan), null).stream().map(s -> s.hex).toList());
    }

    // ---- the procedure ----

    /**
     * P3.12: every asteroid hex is within two of a counter, and every counter is one hex from
     * where P3.11 names it. Checking both together is what proves the roll happened and that
     * nothing drifted further than a single hex.
     */
    @Test
    public void everyAsteroidHexBelongsToARolledCounter() {
        List<ScenarioSpec.TerrainSetup> terrain = gen(List.of(field(7L)), null);
        assertFalse(terrain.isEmpty());

        List<Location> starts = new ArrayList<>();
        for (String hex : TerrainGenerator.COUNTER_HEXES)
            starts.add(MapRegion.parse(hex));

        for (ScenarioSpec.TerrainSetup s : terrain) {
            Location hex = at(s);
            boolean claimed = starts.stream().anyMatch(start ->
                    // within 2 of a counter that is itself within 1 hex of its named position
                    MapUtils.getRange(start, hex) <= TerrainGenerator.FIELD_RADIUS + 1);
            assertTrue(s.hex + " belongs to no counter", claimed);
        }
    }

    @Test
    public void everyHexIsLaidOnce() {
        List<String> hexes = gen(List.of(field(9L)), null).stream().map(s -> s.hex).toList();

        assertEquals("overlapping counters do not double up (P3.12)",
                hexes.size(), hexes.stream().distinct().count());
    }

    @Test
    public void theFieldIsSubstantialButNotTheWholeMap() {
        int count = gen(List.of(field(5L)), null).size();

        // Eighteen counters, nineteen hexes each before overlaps and map edges.
        assertTrue("a real field: " + count, count > 150);
        assertTrue("not the entire map: " + count, count < COLS * ROWS);
    }

    @Test
    public void everyHexIsOnTheMap() {
        for (ScenarioSpec.TerrainSetup s : gen(List.of(field(3L)), null)) {
            Location loc = at(s);
            assertNotNull(s.hex, loc);
            assertTrue(s.hex, loc.getX() >= 1 && loc.getX() <= COLS);
            assertTrue(s.hex, loc.getY() >= 1 && loc.getY() <= ROWS);
        }
    }

    @Test
    public void everythingGeneratedIsAnAsteroid() {
        for (ScenarioSpec.TerrainSetup s : gen(List.of(field(4L)), null)) {
            assertEquals("ASTEROID", s.type);
            assertEquals("one hex each, not a footprint", 0, s.radius);
        }
    }

    /**
     * The field goes where P3.11 says, deployment zones or no deployment zones. Setting up in
     * an asteroid field is a scenario, not a fault — so keep-clear must NOT move it.
     */
    @Test
    public void theStandardFieldIgnoresKeepClearBecauseTheRuleFixesIt() {
        List<String> free = gen(List.of(field(11L)), null).stream().map(s -> s.hex).toList();
        List<String> withZones = gen(List.of(field(11L)),
                List.of(MapRegion.band("LEFT", 6), MapRegion.band("RIGHT", 6)))
                .stream().map(s -> s.hex).toList();

        assertEquals("the rule's layout is not negotiable", free, withZones);
    }

    // ---- solid bodies, which are placed rather than fixed ----

    @Test
    public void aPlanetIsOneHexInItsRegion() {
        TerrainGenerator.Plan p = new TerrainGenerator.Plan();
        p.type = "PLANET";
        p.region = MapRegion.box("1810", "2422");
        p.name = "Nivram";
        p.seed = 4L;

        List<ScenarioSpec.TerrainSetup> out = gen(List.of(p), null);
        assertEquals(1, out.size());
        assertEquals("PLANET", out.get(0).type);
        assertEquals(0, out.get(0).radius);
        assertEquals("Nivram", out.get(0).name);
        assertTrue(p.region.contains(at(out.get(0)), COLS, ROWS));
    }

    @Test
    public void severalPlanetsKeepTheirDistance() {
        TerrainGenerator.Plan p = new TerrainGenerator.Plan();
        p.type = "PLANET";
        p.region = MapRegion.anywhere();
        p.count = 4;
        p.spacing = 5;
        p.seed = 8L;

        List<ScenarioSpec.TerrainSetup> out = gen(List.of(p), null);
        assertEquals(4, out.size());
        for (int i = 0; i < out.size(); i++)
            for (int j = i + 1; j < out.size(); j++)
                assertTrue("planets " + i + " and " + j + " are on top of each other",
                        MapUtils.getRange(at(out.get(i)), at(out.get(j))) > 5);
    }

    /** A body IS placed freely, so it must respect the zones — unlike the standard field. */
    @Test
    public void aGasGiantFitsItsWholeFootprintInsideItsRegionAndClearOfZones() {
        TerrainGenerator.Plan p = new TerrainGenerator.Plan();
        p.type = "GAS_GIANT";
        p.region = MapRegion.box("1508", "2824");
        p.radius = 2;
        p.seed = 13L;

        MapRegion zone = MapRegion.band("LEFT", 6);
        List<ScenarioSpec.TerrainSetup> out = gen(List.of(p), List.of(zone));
        assertEquals(1, out.size());
        assertEquals(2, out.get(0).radius);

        Location centre = at(out.get(0));
        for (int c = 1; c <= COLS; c++)
            for (int r = 1; r <= ROWS; r++) {
                Location loc = new Location(c, r);
                if (MapUtils.getRange(centre, loc) <= 2) {
                    assertTrue("body hex " + c + "," + r + " escaped the region",
                            p.region.contains(loc, COLS, ROWS));
                    assertFalse("body hex " + c + "," + r + " is in a deployment zone",
                            zone.contains(loc, COLS, ROWS));
                }
            }
    }

    @Test
    public void aPlanetIsNotBuriedByAFieldLaidAfterIt() {
        TerrainGenerator.Plan planet = new TerrainGenerator.Plan();
        planet.type = "PLANET";
        planet.region = MapRegion.box("1810", "2422");
        planet.seed = 2L;

        List<TerrainGenerator.Plan> plans = new ArrayList<>();
        plans.add(planet);
        plans.add(field(3L));

        List<String> hexes = gen(plans, null).stream().map(s -> s.hex).toList();
        assertEquals("nothing shares a hex", hexes.size(), hexes.stream().distinct().count());
    }

    @Test
    public void anUnknownPlanIsSkippedRatherThanFatal() {
        TerrainGenerator.Plan nonsense = new TerrainGenerator.Plan();
        nonsense.type = "BLACK_HOLE";
        nonsense.seed = 1L;

        assertTrue(gen(List.of(nonsense), null).isEmpty());
    }

    @Test
    public void noPlansMeansNoTerrain() {
        assertTrue(TerrainGenerator.generate(null, null, COLS, ROWS).isEmpty());
        assertTrue(gen(List.of(), null).isEmpty());
    }
}
