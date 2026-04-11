package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

public class ScenarioLoaderTest {

    // ---- parseHex ----

    @Test
    public void parseHex_standardNotation() {
        Location loc = ScenarioLoader.parseHex("0515");
        assertEquals(5,  loc.getX());
        assertEquals(15, loc.getY());
    }

    @Test
    public void parseHex_leadingZeros() {
        Location loc = ScenarioLoader.parseHex("0101");
        assertEquals(1, loc.getX());
        assertEquals(1, loc.getY());
    }

    @Test
    public void parseHex_largeValues() {
        Location loc = ScenarioLoader.parseHex("4226");
        assertEquals(42, loc.getX());
        assertEquals(26, loc.getY());
    }

    // ---- parseHeading ----

    @Test
    public void parseHeading_A_is_1() {
        assertEquals(1, ScenarioLoader.parseHeading("A"));
    }

    @Test
    public void parseHeading_B_is_5() {
        assertEquals(5, ScenarioLoader.parseHeading("B"));
    }

    @Test
    public void parseHeading_C_is_9() {
        assertEquals(9, ScenarioLoader.parseHeading("C"));
    }

    @Test
    public void parseHeading_D_is_13() {
        assertEquals(13, ScenarioLoader.parseHeading("D"));
    }

    @Test
    public void parseHeading_E_is_17() {
        assertEquals(17, ScenarioLoader.parseHeading("E"));
    }

    @Test
    public void parseHeading_F_is_21() {
        assertEquals(21, ScenarioLoader.parseHeading("F"));
    }

    @Test
    public void parseHeading_lowercase_accepted() {
        assertEquals(13, ScenarioLoader.parseHeading("d"));
    }

    // ---- applyWeaponStatus ----

    @Test
    public void weaponStatus0_capsUncharged() {
        Ship ship = makeShip();
        ScenarioLoader.applyWeaponStatus(ship, 0);
        assertFalse(ship.isCapacitorsCharged());
        assertEquals(0.0, ship.getWeapons().getPhaserCapacitorEnergy(), 0.001);
    }

    @Test
    public void weaponStatus1_capsChargedEmpty() {
        Ship ship = makeShip();
        ScenarioLoader.applyWeaponStatus(ship, 1);
        assertTrue(ship.isCapacitorsCharged());
        assertEquals(0.0, ship.getWeapons().getPhaserCapacitorEnergy(), 0.001);
    }

    @Test
    public void weaponStatus2_capsChargedFull() {
        Ship ship = makeShip();
        ScenarioLoader.applyWeaponStatus(ship, 2);
        assertTrue(ship.isCapacitorsCharged());
        double capMax = ship.getWeapons().getAvailablePhaserCapacitor();
        assertEquals(capMax, ship.getWeapons().getPhaserCapacitorEnergy(), 0.001);
    }

    @Test
    public void weaponStatus3_capsChargedFull() {
        Ship ship = makeShip();
        ScenarioLoader.applyWeaponStatus(ship, 3);
        assertTrue(ship.isCapacitorsCharged());
        double capMax = ship.getWeapons().getAvailablePhaserCapacitor();
        assertEquals(capMax, ship.getWeapons().getPhaserCapacitorEnergy(), 0.001);
    }

    // ---- ScenarioSpec JSON parsing ----

    @Test
    public void trainingScenario_parsesFromJson() throws Exception {
        File f = new File("../data/scenarios/training.json");
        assumeTrue("training.json must exist for this test", f.exists());
        ScenarioSpec spec = ScenarioSpec.fromJson(f);
        assertEquals("TRAINING", spec.id);
        assertEquals(175, spec.year);
        assertEquals(2, spec.sides.size());
        assertEquals("Federation", spec.sides.get(0).faction);
        assertEquals(2, spec.sides.get(0).ships.size());
        ScenarioSpec.ShipSetup enterprise = spec.sides.get(0).ships.get(0);
        assertEquals("CA",             enterprise.hull);
        assertEquals("USS Enterprise", enterprise.shipName);
        assertEquals("1201",           enterprise.startHex);
        assertEquals("D",              enterprise.startHeading);
        assertEquals(16,               enterprise.startSpeed);
        assertEquals(2,                enterprise.weaponStatus);
    }

    // ---- full load from JSON (requires ShipLibrary) ----

    @Test
    public void loadTrainingScenario_shipsPopulated() throws Exception {
        File scenarioFile = new File("../data/scenarios/training.json");
        assumeTrue("training.json must exist", scenarioFile.exists());
        ShipLibrary.loadAllSpecs("../data/factions");
        assumeTrue("ShipLibrary must load specs", ShipLibrary.isLoaded());

        ScenarioSpec spec = ScenarioSpec.fromJson(scenarioFile);
        List<List<Ship>> sideShips = ScenarioLoader.loadShips(spec);
        assertEquals(2, sideShips.size());
        assertEquals(2, sideShips.get(0).size());
        assertEquals(2, sideShips.get(1).size());

        Ship enterprise = sideShips.get(0).get(0);
        assertEquals("USS Enterprise", enterprise.getName());
        assertEquals(12, enterprise.getLocation().getX());
        assertEquals(1,  enterprise.getLocation().getY());
        assertEquals(13, enterprise.getFacing());   // heading D = 13
        assertEquals(16, enterprise.getSpeed());
        assertTrue(enterprise.isCapacitorsCharged());
        double cap = enterprise.getWeapons().getAvailablePhaserCapacitor();
        assertEquals(cap, enterprise.getWeapons().getPhaserCapacitorEnergy(), 0.001);
    }

    // ---- helpers ----

    private Ship makeShip() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        return ship;
    }

    /** Skip test gracefully if a precondition isn't met (avoids CI failures for missing files). */
    private static void assumeTrue(String msg, boolean condition) {
        org.junit.Assume.assumeTrue(msg, condition);
    }
}
