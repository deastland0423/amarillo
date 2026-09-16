package com.sfb.scenario;

import com.sfb.scenario.Deployment.Placement;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Placing a fleet: what is legal, and the tidy layout offered as a starting point.
 */
public class DeploymentTest {

    private static final int COLS = 42, ROWS = 32;

    private List<String> fleet(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++)
            out.add("Ship " + i);
        return out;
    }

    // ---- what is legal ----

    @Test
    public void aShipInsideItsZoneIsFine() {
        MapRegion zone = MapRegion.band("LEFT", 6);
        List<Placement> placed = List.of(
                new Placement("Kongo", "0316", "C"),
                new Placement("Saladin", "0518", "C"));

        assertTrue(Deployment.check(placed, zone, COLS, ROWS).isEmpty());
    }

    @Test
    public void aShipOutsideItsZoneIsRefusedAndToldWhy() {
        MapRegion zone = MapRegion.band("LEFT", 6);
        List<Placement> placed = List.of(new Placement("Kongo", "2016", "C"));

        List<String> problems = Deployment.check(placed, zone, COLS, ROWS);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("Kongo"));
        assertTrue("says where it should have been: " + problems.get(0),
                problems.get(0).contains("left edge"));
    }

    @Test
    public void everyFaultIsReportedNotJustTheFirst() {
        MapRegion zone = MapRegion.band("LEFT", 6);
        List<Placement> placed = List.of(
                new Placement("Kongo", "2016", "C"),
                new Placement("Saladin", "3016", "C"));

        assertEquals(2, Deployment.check(placed, zone, COLS, ROWS).size());
    }

    @Test
    public void aShipMustFaceSomewhere() {
        List<Placement> placed = List.of(new Placement("Kongo", "0316", ""));

        List<String> problems = Deployment.check(placed, MapRegion.anywhere(), COLS, ROWS);
        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("facing")));
    }

    /** Ships may share a hex in play, so deployment has no business forbidding it. */
    @Test
    public void shipsMayShareAHex() {
        List<Placement> placed = List.of(
                new Placement("Kongo", "0316", "C"),
                new Placement("Saladin", "0316", "C"));

        assertTrue(Deployment.check(placed, MapRegion.anywhere(), COLS, ROWS).isEmpty());
    }

    @Test
    public void aFleetIsNotDeployedUntilEveryShipIsDown() {
        MapRegion zone = MapRegion.band("LEFT", 6);
        List<String> ships = List.of("Kongo", "Saladin");

        assertFalse(Deployment.isComplete(ships,
                List.of(new Placement("Kongo", "0316", "C")), zone, COLS, ROWS));
        assertTrue(Deployment.isComplete(ships,
                List.of(new Placement("Kongo", "0316", "C"),
                        new Placement("Saladin", "0318", "C")), zone, COLS, ROWS));
    }

    @Test
    public void anIllegalPlacementMeansTheFleetIsNotDeployed() {
        MapRegion zone = MapRegion.band("LEFT", 6);

        assertFalse(Deployment.isComplete(List.of("Kongo"),
                List.of(new Placement("Kongo", "2016", "C")), zone, COLS, ROWS));
    }

    // ---- solid ground ----

    private ScenarioSpec withPlanet(String hex, int radius) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.mapCols = COLS;
        spec.mapRows = ROWS;
        ScenarioSpec.TerrainSetup t = new ScenarioSpec.TerrainSetup();
        t.type = radius > 0 ? "GAS_GIANT" : "PLANET";
        t.hex = hex;
        t.radius = radius;
        spec.terrain = new ArrayList<>(List.of(t));
        return spec;
    }

    @Test
    public void aPlanetsOwnHexIsNoEntry() {
        assertEquals(Set.of("2016"), Deployment.noEntryHexes(withPlanet("2016", 0)));
    }

    @Test
    public void aGasGiantBlocksItsWholeBody() {
        Set<String> blocked = Deployment.noEntryHexes(withPlanet("2016", 2));

        assertTrue("the centre", blocked.contains("2016"));
        assertTrue("a body hex", blocked.contains("2018"));
        assertFalse("clear of the body", blocked.contains("2022"));
    }

    /** Asteroids are enterable at a price, so they never block setup. */
    @Test
    public void asteroidsAreNotNoEntry() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.mapCols = COLS;
        spec.mapRows = ROWS;
        ScenarioSpec.TerrainSetup rock = new ScenarioSpec.TerrainSetup();
        rock.type = "ASTEROID";
        rock.hex = "2016";
        spec.terrain = new ArrayList<>(List.of(rock));

        assertTrue(Deployment.noEntryHexes(spec).isEmpty());
    }

    @Test
    public void aShipCannotBeSetDownInsideAPlanet() {
        Set<String> blocked = Deployment.noEntryHexes(withPlanet("2016", 1));
        List<Placement> placed = List.of(new Placement("Kongo", "2016", "C"));

        List<String> problems = Deployment.check(placed, MapRegion.anywhere(), blocked, COLS, ROWS);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("planet"));
        assertFalse(Deployment.isComplete(List.of("Kongo"), placed,
                MapRegion.anywhere(), blocked, COLS, ROWS));
    }

    @Test
    public void aShipBesideAPlanetIsFine() {
        Set<String> blocked = Deployment.noEntryHexes(withPlanet("2016", 1));
        List<Placement> placed = List.of(new Placement("Kongo", "2020", "C"));

        assertTrue(Deployment.check(placed, MapRegion.anywhere(), blocked, COLS, ROWS).isEmpty());
    }

    /** A ship outside its zone is told that, not told about a planet it is nowhere near. */
    @Test
    public void theZoneComplaintComesFirst() {
        Set<String> blocked = Deployment.noEntryHexes(withPlanet("2016", 1));
        List<Placement> placed = List.of(new Placement("Kongo", "3016", "C"));

        List<String> problems = Deployment.check(placed, MapRegion.band("LEFT", 6), blocked, COLS, ROWS);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("deployment area"));
    }

    // ---- the offered layout ----

    @Test
    public void autoArrangePlacesEveryShipLegally() {
        MapRegion zone = MapRegion.band("LEFT", 6);
        List<Placement> placed = Deployment.autoArrange(fleet(6), zone, COLS, ROWS);

        assertEquals(6, placed.size());
        assertTrue(Deployment.check(placed, zone, COLS, ROWS).toString(),
                Deployment.check(placed, zone, COLS, ROWS).isEmpty());
        assertTrue(Deployment.isComplete(fleet(6), placed, zone, COLS, ROWS));
    }

    @Test
    public void autoArrangeGivesEachShipItsOwnHex() {
        List<Placement> placed = Deployment.autoArrange(
                fleet(6), MapRegion.band("LEFT", 6), COLS, ROWS);

        long distinct = placed.stream().map(Placement::hex).distinct().count();
        assertEquals("no two ships stacked by the tidy layout", placed.size(), distinct);
    }

    @Test
    public void autoArrangeFacesTheMiddleOfTheMap() {
        assertEquals("C", Deployment.autoArrange(fleet(2), MapRegion.band("LEFT", 6), COLS, ROWS)
                .get(0).heading());
        assertEquals("F", Deployment.autoArrange(fleet(2), MapRegion.band("RIGHT", 6), COLS, ROWS)
                .get(0).heading());
    }

    @Test
    public void autoArrangeStartsEverythingAtSpeedMax() {
        for (Placement p : Deployment.autoArrange(fleet(3), MapRegion.anywhere(), COLS, ROWS))
            assertEquals(16, p.speed());
    }

    /** A zone smaller than the fleet still places everyone, pattern or no pattern. */
    @Test
    public void autoArrangeCopesWithACrampedZone() {
        MapRegion tiny = MapRegion.box("0510", "0612");   // 2 columns x 3 rows = 6 hexes
        List<Placement> placed = Deployment.autoArrange(fleet(6), tiny, COLS, ROWS);

        assertEquals(6, placed.size());
        assertTrue(Deployment.check(placed, tiny, COLS, ROWS).toString(),
                Deployment.check(placed, tiny, COLS, ROWS).isEmpty());
        assertEquals("each in its own hex", 6,
                placed.stream().map(Placement::hex).distinct().count());
    }

    /** More ships than hexes: place what fits rather than inventing illegal ground. */
    @Test
    public void autoArrangeStopsWhenTheZoneRunsOut() {
        MapRegion oneHex = MapRegion.circle("0510", 0);
        List<Placement> placed = Deployment.autoArrange(fleet(3), oneHex, COLS, ROWS);

        assertEquals(1, placed.size());
        assertTrue(Deployment.check(placed, oneHex, COLS, ROWS).isEmpty());
    }

    @Test
    public void autoArrangeOfNothingIsNothing() {
        assertTrue(Deployment.autoArrange(List.of(), MapRegion.anywhere(), COLS, ROWS).isEmpty());
    }
}
