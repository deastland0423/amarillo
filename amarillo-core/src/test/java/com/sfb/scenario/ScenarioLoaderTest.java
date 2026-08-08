package com.sfb.scenario;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.DroneRack;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class ScenarioLoaderTest {

    // ---- parseHex ----

    @Test
    public void parseHex_standardNotation() {
        Location loc = ScenarioLoader.parseHex("0515");
        assertEquals(5, loc.getX());
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
        assertFalse("WS-0 must not activate fire control", ship.isActiveFireControl());
    }

    @Test
    public void weaponStatus1_capsChargedEmpty() {
        Ship ship = makeShip();
        ScenarioLoader.applyWeaponStatus(ship, 1);
        assertTrue(ship.isCapacitorsCharged());
        assertEquals(0.0, ship.getWeapons().getPhaserCapacitorEnergy(), 0.001);
        assertTrue("WS-1 must activate fire control", ship.isActiveFireControl());
    }

    @Test
    public void weaponStatus2_capsChargedFull() {
        Ship ship = makeShip();
        ScenarioLoader.applyWeaponStatus(ship, 2);
        assertTrue(ship.isCapacitorsCharged());
        double capMax = ship.getWeapons().getAvailablePhaserCapacitor();
        assertEquals(capMax, ship.getWeapons().getPhaserCapacitorEnergy(), 0.001);
        assertTrue("WS-2 must activate fire control", ship.isActiveFireControl());
    }

    @Test
    public void weaponStatus3_capsChargedFull() {
        Ship ship = makeShip();
        ScenarioLoader.applyWeaponStatus(ship, 3);
        assertTrue(ship.isCapacitorsCharged());
        double capMax = ship.getWeapons().getAvailablePhaserCapacitor();
        assertEquals(capMax, ship.getWeapons().getPhaserCapacitorEnergy(), 0.001);
        assertTrue("WS-3 must activate fire control", ship.isActiveFireControl());
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
        assertEquals("CA+", enterprise.hull);
        assertEquals("USS Enterprise", enterprise.shipName);
        assertEquals("1201", enterprise.startHex);
        assertEquals("D", enterprise.startHeading);
        assertEquals(16, enterprise.startSpeed);
        assertEquals(2, enterprise.weaponStatus);
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
        assertEquals(1, enterprise.getLocation().getY());
        assertEquals(13, enterprise.getFacing()); // heading D = 13
        assertEquals(16, enterprise.getSpeed());
        assertTrue(enterprise.isCapacitorsCharged());
        double cap = enterprise.getWeapons().getAvailablePhaserCapacitor();
        assertEquals(cap, enterprise.getWeapons().getPhaserCapacitorEnergy(), 0.001);
    }

    // ---- applyCoi ----

    /** FedCA: BPV=125, 10 BPs, 0 commandos. Budget @20% = floor(25) = 25 BPV. */
    private ScenarioSpec makeSpec() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = 175;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        return spec;
    }

    @Test
    public void coi_extraBoardingParties_added() {
        Ship ship = makeShip();
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraBoardingParties = 4; // cost 2.0 BPV — well within budget
        ScenarioLoader.applyCoi(ship, loadout, makeSpec());
        assertEquals(14, ship.getCrew().getFriendlyTroops().normal);
    }

    @Test
    public void coi_extraBoardingParties_capped_at_10() {
        Ship ship = makeShip();
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraBoardingParties = 99; // over limit, capped to 10
        ScenarioLoader.applyCoi(ship, loadout, makeSpec());
        // 10 extra × 0.5 = 5 BPV, within budget; result = 10 base + 10 extra = 20
        assertEquals(20, ship.getCrew().getFriendlyTroops().normal);
    }

    @Test
    public void coi_convertBpToCommando() {
        Ship ship = makeShip();
        CoiLoadout loadout = new CoiLoadout();
        loadout.convertBpToCommando = 2; // cost 1.0 BPV
        ScenarioLoader.applyCoi(ship, loadout, makeSpec());
        assertEquals(8, ship.getCrew().getFriendlyTroops().normal);
        assertEquals(2, ship.getCrew().getFriendlyTroops().commandos);
    }

    @Test
    public void coi_extraCommandoSquads_added() {
        Ship ship = makeShip();
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraCommandoSquads = 2; // cost 2.0 BPV
        ScenarioLoader.applyCoi(ship, loadout, makeSpec());
        assertEquals(2, ship.getCrew().getFriendlyTroops().commandos);
    }

    @Test
    public void coi_tBombs_add_with_free_dummy() {
        Ship ship = makeShip(); // FedCA starts with 0 T-bombs by default
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraTBombs = 3; // cost 12.0 BPV, within 25 BPV budget; adds 3 T-bombs + 3 free dummies
        ScenarioLoader.applyCoi(ship, loadout, makeSpec());
        assertEquals(3, ship.getTBombs());
        assertEquals(3, ship.getDummyTBombs()); // 1 free dummy per purchased T-bomb
    }

    @Test
    public void coi_tBombs_skipped_when_over_budget() {
        Ship ship = makeShip();
        // Use a 5% budget → floor(125 * 0.05) = 6 BPV; 2 T-bombs = 8 BPV > 6 — skipped
        ScenarioSpec spec = makeSpec();
        spec.commanderOptions.budgetPercent = 5;
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraTBombs = 2;
        ScenarioLoader.applyCoi(ship, loadout, spec);
        assertEquals(0, ship.getTBombs());
        assertEquals(0, ship.getDummyTBombs());
    }

    @Test
    public void coi_tBombs_blocked_when_scenario_disallows() {
        Ship ship = makeShip();
        ScenarioSpec spec = makeSpec();
        spec.commanderOptions.allowTBombs = false;
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraTBombs = 1;
        ScenarioLoader.applyCoi(ship, loadout, spec);
        assertEquals(0, ship.getTBombs());
    }

    @Test
    public void coi_droneRackLoadout_replaces_default_ammo() {
        Ship ship = makeShip(); // FedCA has drone racks
        // Find how many drone racks the ship has
        List<DroneRack> racks = new java.util.ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack)
                racks.add((DroneRack) w);
        }
        assumeTrue("Ship must have at least one drone rack", !racks.isEmpty());

        CoiLoadout loadout = new CoiLoadout();
        // Load rack 0 with TypeIM drones (medium-speed TypeI, available Y165)
        loadout.setDroneRackLoadout(0, Arrays.asList(DroneType.TypeIM, DroneType.TypeIM,
                DroneType.TypeIM, DroneType.TypeIM));
        ScenarioLoader.applyCoi(ship, loadout, makeSpec());

        DroneRack rack0 = racks.get(0);
        List<Drone> ammo = rack0.getAmmo();
        assertFalse("Rack should have drones after COI", ammo.isEmpty());
        for (Drone d : ammo) {
            assertEquals(DroneType.TypeIM, d.getDroneType());
        }
    }

    @Test
    public void coi_droneRackLoadout_rejects_unavailable_year() {
        Ship ship = makeShip();
        ScenarioSpec spec = makeSpec();
        spec.year = 160; // TypeIM not available until Y165

        List<DroneRack> racks = new java.util.ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack)
                racks.add((DroneRack) w);
        }
        assumeTrue("Ship must have at least one drone rack", !racks.isEmpty());

        List<Drone> originalAmmo = new java.util.ArrayList<>(racks.get(0).getAmmo());

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoadout(0, Arrays.asList(DroneType.TypeIM));
        ScenarioLoader.applyCoi(ship, loadout, spec);

        // Rack ammo should be unchanged (TypeIM rejected)
        assertEquals(originalAmmo.size(), racks.get(0).getAmmo().size());
    }

    @Test
    public void coi_droneRackLoadout_rejects_speed_cap_violation() {
        Ship ship = makeShip();
        ScenarioSpec spec = makeSpec();
        spec.commanderOptions.maxDroneSpeed = 12; // cap at speed 12, TypeIM=20 rejected

        List<DroneRack> racks = new java.util.ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack)
                racks.add((DroneRack) w);
        }
        assumeTrue("Ship must have at least one drone rack", !racks.isEmpty());

        List<Drone> originalAmmo = new java.util.ArrayList<>(racks.get(0).getAmmo());

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoadout(0, Arrays.asList(DroneType.TypeIM, DroneType.TypeIM,
                DroneType.TypeIM, DroneType.TypeIM));
        ScenarioLoader.applyCoi(ship, loadout, spec);

        assertEquals(originalAmmo.size(), racks.get(0).getAmmo().size());
    }

    @Test
    public void coi_tBombs_capped_by_size_class() {
        // FedCA is size class 3 → MAX_TBOMBS[3] = 4; requesting 6 should be capped to 4
        Ship ship = makeShip();
        assertEquals(3, ship.getSizeClass());
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraTBombs = 6;
        ScenarioLoader.applyCoi(ship, loadout, makeSpec());
        assertEquals(4, ship.getTBombs());
        assertEquals(4, ship.getDummyTBombs());
    }

    @Test
    public void weaponStatus2_heavyWeaponsPartiallyArmed() {
        Ship ship = makeShip(); // FedCA has photon torpedoes (totalArmingTurns = 2)
        ScenarioLoader.applyWeaponStatus(ship, 2);
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof com.sfb.weapons.HeavyWeapon))
                continue;
            if (w instanceof com.sfb.weapons.Fusion)
                continue;
            if (w instanceof com.sfb.weapons.Disruptor)
                continue;
            com.sfb.weapons.HeavyWeapon hw = (com.sfb.weapons.HeavyWeapon) w;
            assertFalse("WS-2: weapon should not be fully armed", hw.isArmed());
            assertEquals("WS-2: armingTurn should be totalArmingTurns - 1",
                    hw.totalArmingTurns() - 1, hw.getArmingTurn());
        }
    }

    @Test
    public void weaponStatus3_heavyWeaponsArmed() {
        Ship ship = makeShip(); // FedCA has photon torpedoes
        ScenarioLoader.applyWeaponStatus(ship, 3);
        boolean anyHeavyArmed = false;
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.HeavyWeapon) {
                com.sfb.weapons.HeavyWeapon hw = (com.sfb.weapons.HeavyWeapon) w;
                if (hw.isArmed()) {
                    anyHeavyArmed = true;
                    break;
                }
            }
        }
        assertTrue("WS-3: at least one heavy weapon should start armed", anyHeavyArmed);
    }

    @Test
    public void weaponStatus3_armingTurnIsMax() {
        Ship ship = makeShip();
        ScenarioLoader.applyWeaponStatus(ship, 3);
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof com.sfb.weapons.HeavyWeapon))
                continue;
            if (w instanceof com.sfb.weapons.Fusion)
                continue;
            com.sfb.weapons.HeavyWeapon hw = (com.sfb.weapons.HeavyWeapon) w;
            if (hw.isArmed()) {
                assertEquals("Armed weapon armingTurn should equal totalArmingTurns",
                        hw.totalArmingTurns(), hw.getArmingTurn());
            }
        }
    }

    @Test
    public void coi_null_loadout_is_noop() {
        Ship ship = makeShip();
        int normalBefore = ship.getCrew().getFriendlyTroops().normal;
        ScenarioLoader.applyCoi(ship, null, makeSpec());
        assertEquals(normalBefore, ship.getCrew().getFriendlyTroops().normal);
    }

    // ---- helpers ----

    private Ship makeShip() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        return ship;
    }

    /**
     * Skip test gracefully if a precondition isn't met (avoids CI failures for
     * missing files).
     */
    private static void assumeTrue(String msg, boolean condition) {
        org.junit.Assume.assumeTrue(msg, condition);
    }
}
