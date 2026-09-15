package com.sfb.scenario;

import com.sfb.properties.Location;
import com.sfb.utilities.MapUtils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Terrain described rather than enumerated. The two properties that matter: the same plan lays
 * out the same map every time, and nothing lands where a fleet has to set up.
 */
public class TerrainGeneratorTest {

    private static final int COLS = 42, ROWS = 32;

    private TerrainGenerator.Plan field(MapRegion region, double density, long seed) {
        TerrainGenerator.Plan p = new TerrainGenerator.Plan();
        p.type = "ASTEROID_FIELD";
        p.region = region;
        p.density = density;
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
    public void theSameSeedLaysOutTheSameMap() {
        List<ScenarioSpec.TerrainSetup> a =
                gen(List.of(field(MapRegion.box("1005", "3028"), 0.1, 42L)), null);
        List<ScenarioSpec.TerrainSetup> b =
                gen(List.of(field(MapRegion.box("1005", "3028"), 0.1, 42L)), null);

        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++)
            assertEquals("hex " + i, a.get(i).hex, b.get(i).hex);
    }

    @Test
    public void adifferentSeedLaysOutADifferentMap() {
        List<String> a = gen(List.of(field(MapRegion.box("1005", "3028"), 0.1, 1L)), null)
                .stream().map(s -> s.hex).toList();
        List<String> b = gen(List.of(field(MapRegion.box("1005", "3028"), 0.1, 2L)), null)
                .stream().map(s -> s.hex).toList();

        assertNotEquals(a, b);
    }

    /** A plan with no seed takes one and keeps it, so a scenario file still reads the same. */
    @Test
    public void aPlanWithoutASeedGetsOneAndRemembersIt() {
        TerrainGenerator.Plan plan = field(MapRegion.box("1005", "2020"), 0.1, 0L);
        plan.seed = null;

        List<String> first = gen(List.of(plan), null).stream().map(s -> s.hex).toList();
        assertNotNull("the plan kept its seed", plan.seed);

        List<String> again = gen(List.of(plan), null).stream().map(s -> s.hex).toList();
        assertEquals(first, again);
    }

    // ---- where things land ----

    @Test
    public void asteroidsStayInsideTheirRegion() {
        MapRegion region = MapRegion.box("1005", "2020");

        for (ScenarioSpec.TerrainSetup s : gen(List.of(field(region, 0.2, 7L)), null)) {
            assertEquals("ASTEROID", s.type);
            assertTrue(s.hex + " is outside the field's region",
                    region.contains(at(s), COLS, ROWS));
        }
    }

    @Test
    public void densityDecidesRoughlyHowMany() {
        MapRegion region = MapRegion.box("1005", "3024");   // 21 x 20 = 420 hexes
        int count = gen(List.of(field(region, 0.10, 3L)), null).size();

        assertEquals("about a tenth of 420", 42, count);
    }

    @Test
    public void everyAsteroidGetsItsOwnHex() {
        List<String> hexes = gen(List.of(field(MapRegion.anywhere(), 0.15, 9L)), null)
                .stream().map(s -> s.hex).toList();

        assertEquals("no hex used twice", hexes.size(), hexes.stream().distinct().count());
    }

    // ---- the point of keep-clear ----

    @Test
    public void nothingIsPlacedWhereAFleetMustSetUp() {
        List<MapRegion> zones = List.of(MapRegion.band("LEFT", 6), MapRegion.band("RIGHT", 6));

        List<ScenarioSpec.TerrainSetup> terrain =
                gen(List.of(field(MapRegion.anywhere(), 0.3, 11L)), zones);

        assertFalse("something was generated", terrain.isEmpty());
        for (ScenarioSpec.TerrainSetup s : terrain)
            for (MapRegion zone : zones)
                assertFalse(s.hex + " landed in a deployment zone",
                        zone.contains(at(s), COLS, ROWS));
    }

    @Test
    public void aRegionEntirelyKeptClearProducesNothing() {
        MapRegion left = MapRegion.band("LEFT", 6);
        assertTrue(gen(List.of(field(left, 0.5, 5L)), List.of(left)).isEmpty());
    }

    // ---- solid bodies ----

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

    /** A giant's whole body must be clear, not merely its centre hex. */
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
    public void plansAreAppliedInOrderAndDoNotOverlap() {
        TerrainGenerator.Plan planet = new TerrainGenerator.Plan();
        planet.type = "PLANET";
        planet.region = MapRegion.box("1810", "2422");
        planet.seed = 2L;

        List<TerrainGenerator.Plan> plans = new ArrayList<>();
        plans.add(planet);
        plans.add(field(MapRegion.anywhere(), 0.3, 3L));

        List<String> hexes = gen(plans, null).stream().map(s -> s.hex).toList();
        assertEquals("the field did not bury the planet",
                hexes.size(), hexes.stream().distinct().count());
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
