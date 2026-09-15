package com.sfb.scenario;

import com.sfb.properties.Location;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * A scenario file can describe its terrain instead of listing it. The hexes are written into
 * the spec, not merely into a loaded Game, because the lobby broadcasts the spec and a player
 * deploying has to see what they are setting up around.
 */
public class TerrainPlanLoadingTest {

    private ScenarioSpec specWithPlan(double density, long seed) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.mapCols = 42;
        spec.mapRows = 32;

        TerrainGenerator.Plan plan = new TerrainGenerator.Plan();
        plan.type = "ASTEROID_FIELD";
        plan.region = MapRegion.box("1508", "2824");
        plan.density = density;
        plan.seed = seed;
        spec.terrainPlan = new ArrayList<>(List.of(plan));
        return spec;
    }

    @Test
    public void aPlanBecomesHexesInTheSpec() {
        ScenarioSpec spec = specWithPlan(0.1, 21L);
        assertNull("nothing enumerated yet", spec.terrain);

        ScenarioLoader.expandTerrainPlans(spec);

        assertNotNull(spec.terrain);
        assertFalse(spec.terrain.isEmpty());
        for (ScenarioSpec.TerrainSetup s : spec.terrain)
            assertEquals("ASTEROID", s.type);
    }

    /** Expanding twice would double the field, so a spent plan is cleared. */
    @Test
    public void expandingTwiceDoesNotDoubleTheField() {
        ScenarioSpec spec = specWithPlan(0.1, 21L);

        ScenarioLoader.expandTerrainPlans(spec);
        int after = spec.terrain.size();
        ScenarioLoader.expandTerrainPlans(spec);

        assertEquals(after, spec.terrain.size());
        assertNull("the plan is spent", spec.terrainPlan);
    }

    /** Hand-written terrain and a plan can live together. */
    @Test
    public void generatedHexesJoinTheOnesWrittenByHand() {
        ScenarioSpec spec = specWithPlan(0.05, 6L);
        ScenarioSpec.TerrainSetup planet = new ScenarioSpec.TerrainSetup();
        planet.type = "PLANET";
        planet.hex = "2016";
        planet.name = "Nivram";
        spec.terrain = new ArrayList<>(List.of(planet));

        ScenarioLoader.expandTerrainPlans(spec);

        assertTrue("the hand-written planet survived",
                spec.terrain.stream().anyMatch(t -> "Nivram".equals(t.name)));
        assertTrue("and the field arrived",
                spec.terrain.stream().anyMatch(t -> "ASTEROID".equals(t.type)));
    }

    /** A scenario that places a cruiser at 1216 must not drop an asteroid on it. */
    @Test
    public void shipsAlreadyPlacedKeepTheirHexes() {
        ScenarioSpec spec = specWithPlan(0.9, 33L);   // dense enough to hit everything otherwise

        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.name = "Federation";
        side.ships = new ArrayList<>();
        for (String hex : List.of("2016", "2018", "2020")) {
            ScenarioSpec.ShipSetup ship = new ScenarioSpec.ShipSetup();
            ship.type = "CA";
            ship.shipName = "Ship " + hex;
            ship.startHex = hex;
            side.ships.add(ship);
        }
        spec.sides = new ArrayList<>(List.of(side));

        ScenarioLoader.expandTerrainPlans(spec);

        List<String> occupied = spec.terrain.stream().map(t -> t.hex).toList();
        for (String shipHex : List.of("2016", "2018", "2020"))
            assertFalse("terrain landed on a ship at " + shipHex, occupied.contains(shipHex));
        assertFalse("a field was still generated", spec.terrain.isEmpty());
    }

    @Test
    public void loadingTerrainExpandsPlansOnTheWayThrough() {
        ScenarioSpec spec = specWithPlan(0.08, 17L);

        List<com.sfb.objects.Terrain> terrain = ScenarioLoader.loadTerrain(spec);

        assertFalse("plans became real terrain objects", terrain.isEmpty());
        for (com.sfb.objects.Terrain t : terrain)
            assertEquals(com.sfb.properties.TerrainType.ASTEROID, t.getTerrainType());
    }

    @Test
    public void aSpecWithNoPlanIsLeftAlone() {
        ScenarioSpec spec = new ScenarioSpec();
        ScenarioLoader.expandTerrainPlans(spec);
        assertNull(spec.terrain);
    }

    /** Every generated hex is on the map, whatever the region asked for. */
    @Test
    public void nothingLandsOffTheMap() {
        ScenarioSpec spec = specWithPlan(0.3, 77L);
        ScenarioLoader.expandTerrainPlans(spec);

        for (ScenarioSpec.TerrainSetup s : spec.terrain) {
            Location loc = MapRegion.parse(s.hex);
            assertNotNull(s.hex, loc);
            assertTrue(s.hex, loc.getX() >= 1 && loc.getX() <= spec.mapCols);
            assertTrue(s.hex, loc.getY() >= 1 && loc.getY() <= spec.mapRows);
        }
    }
}
