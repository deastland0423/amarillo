package com.sfb.scenario;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Fleets become a scenario, because everything downstream already works from one — ship
 * loading, Commander's Options, victory scoring and the lobby's own display. A battle between
 * bought fleets has to arrive in the same shape as a hand-authored one or none of that applies.
 */
public class FleetsToScenarioTest {

    private FleetSpec fleet(String name, String faction, String... types) {
        FleetSpec spec = new FleetSpec();
        spec.name = name;
        spec.factions = new ArrayList<>(List.of(faction));
        spec.year = 180;
        spec.budget = 1000;
        for (String t : types) {
            FleetSpec.ShipEntry e = new FleetSpec.ShipEntry();
            e.type = t;
            e.name = t;
            spec.ships.add(e);
        }
        spec.flagship = types.length > 0 ? types[0] : null;
        return spec;
    }

    private ScenarioSpec twoSides() {
        return FleetsToScenario.build(List.of(
                new FleetsToScenario.Entry(fleet("Klingon patrol", "Klingon", "D7", "D6", "F5"), "Klingons"),
                new FleetsToScenario.Entry(fleet("Fed patrol", "Federation", "CA", "DD"), "Federation")),
                FleetsToScenario.Conditions.defaults(180, 1000));
    }

    @Test
    public void everyFleetBecomesASide() {
        ScenarioSpec spec = twoSides();

        assertEquals(2, spec.sides.size());
        assertEquals(2, spec.numPlayers);
        assertEquals(180, spec.year);
        assertEquals("Klingons", spec.sides.get(0).name);
        assertEquals("Klingon", spec.sides.get(0).faction);
        assertEquals(3, spec.sides.get(0).ships.size());
        assertEquals(2, spec.sides.get(1).ships.size());
    }

    @Test
    public void shipsKeepTheirTypeAndTheNameTheyWereGiven() {
        ScenarioSpec.ShipSetup first = twoSides().sides.get(0).ships.get(0);

        assertEquals("D7", first.type);
        assertEquals("D7", first.shipName);
        assertEquals("Klingon", first.faction);
    }

    /** Speed Max is ScenarioSpec's own convention for 16, and the agreed default. */
    @Test
    public void everyShipStartsAtSpeedMax() {
        for (ScenarioSpec.SideSpec side : twoSides().sides)
            for (ScenarioSpec.ShipSetup ship : side.ships)
                assertEquals("Speed Max", 16, ship.startSpeed);
    }

    @Test
    public void theWeaponStatusIsTheOneAgreedForTheBattle() {
        ScenarioSpec spec = FleetsToScenario.build(
                List.of(new FleetsToScenario.Entry(fleet("A", "Klingon", "D7"), "Klingons")),
                new FleetsToScenario.Conditions(180, 1000, 42, 32, 3));

        assertEquals(3, spec.sides.get(0).ships.get(0).weaponStatus);
    }

    // ---- the starting line ----

    private int col(String hex) { return Integer.parseInt(hex.substring(0, 2)); }
    private int row(String hex) { return Integer.parseInt(hex.substring(2)); }

    @Test
    public void hexesAreWellFormedAndOnTheMap() {
        ScenarioSpec spec = twoSides();
        for (ScenarioSpec.SideSpec side : spec.sides)
            for (ScenarioSpec.ShipSetup ship : side.ships) {
                assertEquals("CCRR notation: " + ship.startHex, 4, ship.startHex.length());
                assertTrue(ship.startHex, col(ship.startHex) >= 1 && col(ship.startHex) <= spec.mapCols);
                assertTrue(ship.startHex, row(ship.startHex) >= 1 && row(ship.startHex) <= spec.mapRows);
            }
    }

    @Test
    public void opposingFleetsStartApartAndFacingEachOther() {
        ScenarioSpec spec = twoSides();
        int left = col(spec.sides.get(0).ships.get(0).startHex);
        int right = col(spec.sides.get(1).ships.get(0).startHex);

        assertTrue("fleets start well apart: " + left + " vs " + right, right - left > 20);
        assertEquals("the left fleet faces right", "C", spec.sides.get(0).ships.get(0).startHeading);
        assertEquals("the right fleet faces left", "F", spec.sides.get(1).ships.get(0).startHeading);
    }

    @Test
    public void oneFleetsShipsDoNotShareAHex() {
        ScenarioSpec spec = twoSides();
        for (ScenarioSpec.SideSpec side : spec.sides) {
            List<String> hexes = side.ships.stream().map(s -> s.startHex).toList();
            assertEquals("no two ships in one hex: " + hexes, hexes.size(), Set.copyOf(hexes).size());
        }
    }

    /** Three and four fleets are a real thing, and nothing here assumes two. */
    @Test
    public void severalFleetsEachGetTheirOwnGround() {
        List<FleetsToScenario.Entry> entries = new ArrayList<>();
        for (int i = 1; i <= 4; i++)
            entries.add(new FleetsToScenario.Entry(
                    fleet("Fleet " + i, "Klingon", "D7", "F5"), "Team " + i));

        ScenarioSpec spec = FleetsToScenario.build(entries, FleetsToScenario.Conditions.defaults(180, 1000));

        assertEquals(4, spec.sides.size());
        List<Integer> columns = spec.sides.stream()
                .map(s -> col(s.ships.get(0).startHex)).toList();
        assertEquals("each fleet on its own column: " + columns, 4, Set.copyOf(columns).size());
    }

    /** Allies field separate fleets under one flag. */
    @Test
    public void severalFleetsMayShareATeam() {
        ScenarioSpec spec = FleetsToScenario.build(List.of(
                new FleetsToScenario.Entry(fleet("Klingon main", "Klingon", "D7"), "Coalition"),
                new FleetsToScenario.Entry(fleet("Lyran allies", "Lyran", "CA"), "Coalition"),
                new FleetsToScenario.Entry(fleet("Fed patrol", "Federation", "CA"), "Federation")),
                FleetsToScenario.Conditions.defaults(180, 1000));

        assertEquals(3, spec.sides.size());
        assertEquals("Coalition", spec.sides.get(0).name);
        assertEquals("Coalition", spec.sides.get(1).name);
        assertEquals("Klingon", spec.sides.get(0).faction);
        assertEquals("Lyran", spec.sides.get(1).faction);
    }

    // ---- deployment zones ----

    @Test
    public void everySideGetsGroundToSetUpOn() {
        for (ScenarioSpec.SideSpec side : twoSides().sides)
            assertNotNull(side.name + " has no deployment zone", side.deploymentZone);
    }

    /** The default zone is a band around the column the fleet would have lined up on. */
    @Test
    public void theDefaultZoneContainsTheFleetsOwnStartingLine() {
        ScenarioSpec spec = twoSides();

        for (ScenarioSpec.SideSpec side : spec.sides)
            for (ScenarioSpec.ShipSetup ship : side.ships)
                assertTrue(ship.shipName + " starts outside its own zone at " + ship.startHex,
                        side.deploymentZone.contains(
                                col(ship.startHex), row(ship.startHex), spec.mapCols, spec.mapRows));
    }

    @Test
    public void opposingDefaultZonesDoNotOverlap() {
        ScenarioSpec spec = twoSides();
        MapRegion a = spec.sides.get(0).deploymentZone;
        MapRegion b = spec.sides.get(1).deploymentZone;

        for (int c = 1; c <= spec.mapCols; c++)
            for (int r = 1; r <= spec.mapRows; r++)
                assertFalse("both fleets may use " + c + "," + r,
                        a.contains(c, r, spec.mapCols, spec.mapRows)
                        && b.contains(c, r, spec.mapCols, spec.mapRows));
    }

    @Test
    public void aHostDrawnZoneIsUsedInsteadOfTheDefault() {
        MapRegion drawn = MapRegion.band("LEFT", 6);
        ScenarioSpec spec = FleetsToScenario.build(List.of(
                new FleetsToScenario.Entry(fleet("A", "Klingon", "D7"), "Klingons", drawn)),
                FleetsToScenario.Conditions.defaults(180, 1000));

        assertSame(drawn, spec.sides.get(0).deploymentZone);
    }

    // ---- terrain ----

    @Test
    public void aBattleWithNoTerrainChosenHasNone() {
        assertNull(twoSides().terrain);
    }

    @Test
    public void theChosenTerrainIsOnTheMapBeforeAnyoneDeploys() {
        TerrainGenerator.Plan field = new TerrainGenerator.Plan();
        field.type = "ASTEROID_FIELD";
        field.seed = 55L;

        ScenarioSpec spec = FleetsToScenario.build(
                List.of(new FleetsToScenario.Entry(fleet("A", "Klingon", "D7"), "Klingons")),
                new FleetsToScenario.Conditions(180, 1000, 42, 32, 2, List.of(field)));

        assertNotNull("the hexes are in the spec, not merely planned", spec.terrain);
        assertFalse(spec.terrain.isEmpty());
        assertNull("the plan is spent", spec.terrainPlan);
    }

    /**
     * The field goes where P3.11 puts it, deployment zones included. Setting up among asteroids
     * is ordinary; it is the solid bodies that deployment refuses, when a ship is put down.
     */
    @Test
    public void anAsteroidFieldMayReachIntoADeploymentZone() {
        TerrainGenerator.Plan field = new TerrainGenerator.Plan();
        field.type = "ASTEROID_FIELD";
        field.seed = 55L;

        ScenarioSpec spec = FleetsToScenario.build(List.of(
                new FleetsToScenario.Entry(fleet("A", "Klingon", "D7", "D6"), "Klingons"),
                new FleetsToScenario.Entry(fleet("B", "Federation", "CA", "DD"), "Federation")),
                new FleetsToScenario.Conditions(180, 1000, 42, 32, 2, List.of(field)));

        boolean anyInAZone = spec.terrain.stream().anyMatch(t ->
                spec.sides.stream().anyMatch(side -> side.deploymentZone.contains(
                        col(t.hex), row(t.hex), spec.mapCols, spec.mapRows)));
        assertTrue("a quarter-map field should reach a zone somewhere", anyInAZone);

        // And none of it blocks setup: asteroids are enterable.
        assertTrue("asteroids are not no-entry", Deployment.noEntryHexes(spec).isEmpty());
    }

    // ---- an authored situation, with the fleets brought to it ----

    /** "Assault on Nivram": a planet, a defender who sets up around it, an attacker who does not. */
    private ScenarioSpec nivram() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.id = "nivram-assault";
        spec.name = "Assault on Nivram";
        spec.mapCols = 42;
        spec.mapRows = 32;

        ScenarioSpec.TerrainSetup planet = new ScenarioSpec.TerrainSetup();
        planet.type = "PLANET";
        planet.hex = "2116";
        planet.name = "Nivram";
        spec.terrain = new ArrayList<>(List.of(planet));

        ScenarioSpec.SideSpec defender = new ScenarioSpec.SideSpec();
        defender.name = "Defender";
        defender.bringYourOwn = true;
        defender.deploymentZone = MapRegion.circle("2116", 3);

        ScenarioSpec.SideSpec attacker = new ScenarioSpec.SideSpec();
        attacker.name = "Attacker";
        attacker.bringYourOwn = true;
        attacker.deploymentZone = MapRegion.band("LEFT", 6);

        spec.sides = new ArrayList<>(List.of(defender, attacker));
        spec.victoryConditions = new ScenarioSpec.VictoryConditions();
        spec.victoryConditions.type = "SPECIAL";
        spec.victoryConditions.notes = "The defender must keep Nivram.";
        return spec;
    }

    @Test
    public void aBroughtFleetStandsInTheGroundTheScenarioGaveItsSide() {
        ScenarioSpec spec = FleetsToScenario.fill(nivram(), List.of(
                new FleetsToScenario.Entry(fleet("Home fleet", "Federation", "CA", "DD"), "Defender"),
                new FleetsToScenario.Entry(fleet("Raiders", "Klingon", "D7", "D6"), "Attacker")),
                FleetsToScenario.Conditions.defaults(180, 1000));

        ScenarioSpec.SideSpec defender = spec.sides.get(0);
        assertEquals("Defender", defender.name);
        assertEquals(2, defender.ships.size());
        for (ScenarioSpec.ShipSetup ship : defender.ships)
            assertTrue(ship.shipName + " is not within 3 hexes of Nivram: " + ship.startHex,
                    defender.deploymentZone.contains(
                            col(ship.startHex), row(ship.startHex), spec.mapCols, spec.mapRows));

        ScenarioSpec.SideSpec attacker = spec.sides.get(1);
        for (ScenarioSpec.ShipSetup ship : attacker.ships)
            assertTrue(ship.startHex + " is not in the attacker's band",
                    attacker.deploymentZone.contains(
                            col(ship.startHex), row(ship.startHex), spec.mapCols, spec.mapRows));
    }

    /** Fleets go to the side they name, not the order they arrive in. */
    @Test
    public void aFleetGoesToTheSideItNames() {
        ScenarioSpec spec = FleetsToScenario.fill(nivram(), List.of(
                new FleetsToScenario.Entry(fleet("Raiders", "Klingon", "D7"), "Attacker"),
                new FleetsToScenario.Entry(fleet("Home fleet", "Federation", "CA"), "Defender")),
                FleetsToScenario.Conditions.defaults(180, 1000));

        assertEquals("Defender", spec.sides.get(0).name);
        assertEquals("Federation", spec.sides.get(0).faction);
        assertEquals("Klingon", spec.sides.get(1).faction);
    }

    /** What the scenario says is its own — the battle does not get to replace it. */
    @Test
    public void anAuthoredScenarioKeepsItsTerrainAndItsVictoryConditions() {
        TerrainGenerator.Plan field = new TerrainGenerator.Plan();
        field.type = "ASTEROID_FIELD";
        field.seed = 3L;

        ScenarioSpec spec = FleetsToScenario.fill(nivram(), List.of(
                new FleetsToScenario.Entry(fleet("A", "Federation", "CA"), "Defender"),
                new FleetsToScenario.Entry(fleet("B", "Klingon", "D7"), "Attacker")),
                new FleetsToScenario.Conditions(180, 1000, 42, 32, 2, List.of(field)));

        assertEquals("the host's asteroids did not bury Nivram", 1, spec.terrain.size());
        assertEquals("PLANET", spec.terrain.get(0).type);
        assertEquals("SPECIAL", spec.victoryConditions.type);
        assertEquals("Assault on Nivram", spec.name);
    }

    /** A scenario that names no terrain leaves the choice to whoever is running the battle. */
    @Test
    public void aScenarioWithNoTerrainTakesTheHostsChoice() {
        ScenarioSpec bare = nivram();
        bare.terrain = null;

        TerrainGenerator.Plan field = new TerrainGenerator.Plan();
        field.type = "ASTEROID_FIELD";
        field.seed = 3L;

        ScenarioSpec spec = FleetsToScenario.fill(bare, List.of(
                new FleetsToScenario.Entry(fleet("A", "Federation", "CA"), "Defender"),
                new FleetsToScenario.Entry(fleet("B", "Klingon", "D7"), "Attacker")),
                new FleetsToScenario.Conditions(180, 1000, 42, 32, 2, List.of(field)));

        assertFalse(spec.terrain.isEmpty());
        assertEquals("ASTEROID", spec.terrain.get(0).type);
    }

    /** A side the scenario wrote out itself keeps its own ships. */
    @Test
    public void anAuthoredSideIsNotOverwritten() {
        ScenarioSpec spec = nivram();
        ScenarioSpec.SideSpec garrison = new ScenarioSpec.SideSpec();
        garrison.name = "Garrison";
        garrison.faction = "Federation";
        garrison.bringYourOwn = false;
        ScenarioSpec.ShipSetup base = new ScenarioSpec.ShipSetup();
        base.type = "FF";
        base.shipName = "USS Watchman";
        base.startHex = "2016";
        base.startHeading = "A";
        base.startSpeed = 0;
        garrison.ships = new ArrayList<>(List.of(base));
        spec.sides.add(garrison);

        ScenarioSpec filled = FleetsToScenario.fill(spec, List.of(
                new FleetsToScenario.Entry(fleet("A", "Federation", "CA"), "Defender"),
                new FleetsToScenario.Entry(fleet("B", "Klingon", "D7"), "Attacker")),
                FleetsToScenario.Conditions.defaults(180, 1000));

        ScenarioSpec.SideSpec kept = filled.sides.get(2);
        assertEquals(1, kept.ships.size());
        assertEquals("USS Watchman", kept.ships.get(0).shipName);
        assertEquals("2016", kept.ships.get(0).startHex);
    }

    /**
     * A side waiting for a fleet lists no ships at all, and the loader has to cope: a situation
     * read straight off disk has nulls where the ships will go, and iterating one is how
     * "Cannot invoke List.iterator() because side.ships is null" happens.
     */
    @Test
    public void aSideWithNoShipsListedCanStillBeLoaded() {
        ScenarioSpec waiting = nivram();
        for (ScenarioSpec.SideSpec side : waiting.sides)
            side.ships = null;   // as Jackson leaves it when the file omits the array

        List<List<com.sfb.objects.Ship>> loaded = ScenarioLoader.loadShips(waiting);
        assertEquals("a list per side, empty but present", 2, loaded.size());
        assertTrue(loaded.get(0).isEmpty());
        assertTrue(loaded.get(1).isEmpty());
    }

    /** A side nobody brought a fleet for ends up empty, so nothing downstream has to ask. */
    @Test
    public void aSideNobodyFilledIsEmptyRatherThanNull() {
        ScenarioSpec waiting = nivram();
        for (ScenarioSpec.SideSpec side : waiting.sides)
            side.ships = null;

        ScenarioSpec spec = FleetsToScenario.fill(waiting, List.of(
                new FleetsToScenario.Entry(fleet("Only one", "Federation", "CA"), "Defender")),
                FleetsToScenario.Conditions.defaults(180, 1000));

        assertEquals(1, spec.sides.get(0).ships.size());
        assertNotNull("the attacker nobody brought", spec.sides.get(1).ships);
        assertTrue(spec.sides.get(1).ships.isEmpty());
        assertEquals(2, ScenarioLoader.loadShips(spec).size());
    }

    /** The pick-up battle is a scenario too: every side brings its own, and nothing else is said. */
    @Test
    public void thePickupBattleIsJustTheLeastSpecifiedScenario() {
        List<FleetsToScenario.Entry> entries = List.of(
                new FleetsToScenario.Entry(fleet("A", "Klingon", "D7"), "Klingons"),
                new FleetsToScenario.Entry(fleet("B", "Federation", "CA"), "Federation"));

        ScenarioSpec template = FleetsToScenario.pickupTemplate(entries,
                FleetsToScenario.Conditions.defaults(180, 1000));

        assertEquals(2, template.sides.size());
        for (ScenarioSpec.SideSpec side : template.sides) {
            assertTrue(side.name + " should be waiting for a fleet", side.bringYourOwn);
            assertTrue(side.ships.isEmpty());
            assertNotNull(side.deploymentZone);
        }
        assertNull("nothing on the map until the host says so", template.terrain);
    }

    /** A lone fleet has nobody to line up against, and is placed rather than crashing. */
    @Test
    public void oneFleetIsPlacedInTheMiddle() {
        ScenarioSpec spec = FleetsToScenario.build(
                List.of(new FleetsToScenario.Entry(fleet("Solo", "Klingon", "D7"), "Klingons")),
                FleetsToScenario.Conditions.defaults(180, 1000));

        int column = col(spec.sides.get(0).ships.get(0).startHex);
        assertTrue("near the middle: " + column, column > 15 && column < 27);
    }
}
