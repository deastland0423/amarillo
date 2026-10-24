package com.sfb.scenario;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.properties.Location;
import com.sfb.properties.PlasmaType;
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
        assertTrue("data/scenarios/training.json: a file this test READS is missing. It is tracked in git, so this is not a checkout that lacks it — it has been deleted or moved, and the test cannot run. Failing rather than skipping, because a skip would take the coverage with it and leave the suite green", f.exists());
        ScenarioSpec spec = ScenarioSpec.fromJson(f);
        assertEquals("TRAINING", spec.id);
        assertEquals(175, spec.year);
        assertEquals(2, spec.sides.size());
        assertEquals("Federation", spec.sides.get(0).faction);
        assertEquals(2, spec.sides.get(0).ships.size());
        ScenarioSpec.ShipSetup enterprise = spec.sides.get(0).ships.get(0);
        assertEquals("CA+", enterprise.type);
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
        assertTrue("data/scenarios/training.json: a file this test READS is missing. It is tracked in git, so this is not a checkout that lacks it — it has been deleted or moved, and the test cannot run. Failing rather than skipping, because a skip would take the coverage with it and leave the suite green", scenarioFile.exists());
        ShipLibrary.loadAllSpecs("../data/factions");
        assertTrue("the ship library did not load, so this test would prove nothing",
                ShipLibrary.isLoaded());

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
        Ship ship = makeDroneShip();
        // Find how many drone racks the ship has
        List<DroneRack> racks = new java.util.ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack)
                racks.add((DroneRack) w);
        }
        assertFalse("fixture: makeDroneShip must carry a rack for this test to mean anything",
                racks.isEmpty());

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
        Ship ship = makeDroneShip();
        ScenarioSpec spec = makeSpec();
        spec.year = 160; // TypeIM not available until Y165

        List<DroneRack> racks = new java.util.ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack)
                racks.add((DroneRack) w);
        }
        assertFalse("fixture: makeDroneShip must carry a rack for this test to mean anything",
                racks.isEmpty());

        List<DroneType> before = typesIn(racks.get(0));

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoadout(0, Arrays.asList(DroneType.TypeIM));
        ScenarioLoader.applyCoi(ship, loadout, spec);

        assertEquals("a rejected loadout must leave the rack exactly as it was",
                before, typesIn(racks.get(0)));
    }

    @Test
    public void coi_droneRackLoadout_rejects_speed_cap_violation() {
        Ship ship = makeDroneShip();
        ScenarioSpec spec = makeSpec();
        spec.commanderOptions.maxDroneSpeed = 12; // cap at speed 12, TypeIM=20 rejected

        List<DroneRack> racks = new java.util.ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack)
                racks.add((DroneRack) w);
        }
        assertFalse("fixture: makeDroneShip must carry a rack for this test to mean anything",
                racks.isEmpty());

        List<DroneType> before = typesIn(racks.get(0));

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoadout(0, Arrays.asList(DroneType.TypeIM, DroneType.TypeIM,
                DroneType.TypeIM, DroneType.TypeIM));
        ScenarioLoader.applyCoi(ship, loadout, spec);

        // TYPES, not the count. The FFG's rack holds four drones and the rejected loadout asks
        // for four, so a cap that did nothing would leave the SIZE at four and this test green
        // while the rack quietly filled with speed-20 drones under a speed-12 cap. Confirmed by
        // mutation: deleting the maxSpeed check failed nothing until this compared types.
        assertEquals("a rejected loadout must leave the rack exactly as it was",
                before, typesIn(racks.get(0)));
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

    /**
     * A ship that actually has a DRONE RACK, for the COI tests that load one.
     *
     * <h2>Why this exists, and what it cost not to have it</h2>
     * The three {@code coi_droneRackLoadout_*} tests below used {@link #makeShip}, a bare
     * Federation CA, under a comment reading "FedCA has drone racks". It does not: the CA
     * carries phaser-1s and photons, and its racks arrive with the + refit. So
     * {@code racks.isEmpty()} was always true, the {@code assumeTrue} guarding each test always
     * skipped, and ALL THREE HAD NEVER RUN — they were the whole of this build's "Skipped: 3",
     * a number small enough that nobody reads it.
     *
     * <p>The FFG is the right fixture rather than a refitted CA: it is a sample builder, so no
     * filesystem is touched, and its single type-G rack arrives with four drones already in it.
     * That last part matters for two of the three tests, which assert that a REJECTED loadout
     * leaves the ammunition unchanged — against an empty rack that assertion passes on nothing.
     *
     * <p>The guards are assertions now. A fixture that stops carrying a rack should fail the
     * test that needs one, not quietly excuse it.
     */
    /** What a rack is actually holding, which is what "unchanged" has to mean. */
    private static List<DroneType> typesIn(DroneRack rack) {
        List<DroneType> out = new java.util.ArrayList<>();
        for (Drone d : rack.getAmmo())
            out.add(d.getDroneType());
        return out;
    }

    private Ship makeDroneShip() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedFfg());
        return ship;
    }

    private Ship makeShip() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        return ship;
    }

    /**
     * Skip a test whose precondition is not met.
     *
     * <p><b>Not for a missing FILE.</b> It used to be — the javadoc here said "avoids CI
     * failures for missing files" — but every scenario this suite reads is tracked in git, so
     * absence means deletion rather than a thin checkout, and skipping took four tests' worth
     * of coverage with it while the suite stayed green. Those checks now assert.
     *
     * <p>What is left is genuinely conditional on a FIXTURE's shape rather than on the data
     * tree existing, and each remaining caller is a candidate for the same treatment.
     */
    private static void assumeTrue(String msg, boolean condition) {
        org.junit.Assume.assumeTrue(msg, condition);
    }

    /**
     * The scout test bed (SCOUT_TEST): every ship must actually resolve, because
     * ScenarioLoader skips an unknown hull with a log line rather than failing, and a
     * silently missing scout would make the scenario useless for what it is for.
     */
    @Test
    public void loadScoutTestScenario_bothScoutsAndConsortsPresent() throws Exception {
        File scenarioFile = new File("../data/scenarios/scout_test.json");
        assertTrue("data/scenarios/scout_test.json: a file this test READS is missing. It is tracked in git, so this is not a checkout that lacks it — it has been deleted or moved, and the test cannot run. Failing rather than skipping, because a skip would take the coverage with it and leave the suite green", scenarioFile.exists());
        ShipLibrary.loadAllSpecs("../data/factions");
        assertTrue("the ship library did not load, so this test would prove nothing",
                ShipLibrary.isLoaded());

        ScenarioSpec spec = ScenarioSpec.fromJson(scenarioFile);
        List<List<Ship>> sideShips = ScenarioLoader.loadShips(spec);

        assertEquals(2, sideShips.size());
        assertEquals("both Federation ships resolved", 2, sideShips.get(0).size());
        assertEquals("both Klingon ships resolved", 2, sideShips.get(1).size());

        Ship deGama = sideShips.get(0).get(0);
        assertEquals("USS De Gama", deGama.getName());
        assertEquals("the Federation scout brings its channels (G24.11)",
                8, deGama.getScoutChannels().size());

        Ship peekaboo = sideShips.get(1).get(0);
        assertEquals("IKS Peekaboo", peekaboo.getName());
        assertEquals("the Klingon scout brings its channels",
                4, peekaboo.getScoutChannels().size());

        // Seekers to work on: both consorts carry racks, and the scouts are in range of them.
        assertTrue("Federation consort has drone racks", hasDroneRack(sideShips.get(0).get(1)));
        assertTrue("Klingon consort has drone racks", hasDroneRack(sideShips.get(1).get(1)));

        int range = com.sfb.utilities.MapUtils.getRange(deGama, peekaboo);
        assertTrue("scouts start inside the fifteen-hex function range (G24.2181), was " + range,
                range <= 15);
    }

    private boolean hasDroneRack(Ship ship) {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack)
                return true;
        return false;
    }

    // ---- FP1.23: the plasma-F exception to weapon status ----

    /**
     * FP1.23: a plasma-F is ready at EVERY weapon status, WS-0 included.
     *
     * <p>"Plasma-F torpedoes, held in stasis boxes, can be held almost indefinitely and are
     * always ready at the start of a scenario, except when the ship is 'surprised' (D18.12)."
     * FP1.132 spells out the WS-0 half: "an actual type-F launcher could be holding a torpedo
     * at that status".
     *
     * <p>Found in a playtest on this very hull — the Federation DDL, the Plasma Armed Destroyer,
     * which carries two plasma-F at FP alongside its photons. That pairing is why it is the
     * fixture: the photons obey the ordinary S4.1 ladder on the same ship, in the same call, so
     * a change that armed everything would pass the plasma half and fail here.
     */
    @Test
    public void plasmaF_isReadyAtEveryWeaponStatus() {
        for (int ws = 0; ws <= 3; ws++) {
            Ship ship = plasmaArmedDestroyer();
            ScenarioLoader.applyWeaponStatus(ship, ws);

            List<com.sfb.weapons.PlasmaLauncher> fs = launchers(ship, PlasmaType.F);
            assertEquals("fixture: the Fed DDL carries two plasma-F", 2, fs.size());
            for (com.sfb.weapons.PlasmaLauncher f : fs) {
                assertTrue("WS-" + ws + ": FP1.23 keeps a plasma-F ready", f.isArmed());
                assertEquals("WS-" + ws + ": and fully armed, not part-way",
                        f.totalArmingTurns(), f.getArmingTurn());
            }
        }
    }

    /**
     * And it is the exception, not the rule: the photons on the SAME ship still climb the
     * ordinary ladder. Unarmed at WS-0 and WS-1, one turn short at WS-2 (S4.12), armed at WS-3.
     *
     * <p>Without this the fix could be "arm everything at every status" and look correct.
     */
    @Test
    public void theRestOfTheShipStillObeysTheWeaponStatusLadder() {
        for (int ws : new int[] { 0, 1 }) {
            Ship ship = plasmaArmedDestroyer();
            ScenarioLoader.applyWeaponStatus(ship, ws);
            for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
                if (w instanceof com.sfb.weapons.Photon p)
                    assertFalse("WS-" + ws + ": a photon is not armed", p.isArmed());
        }

        Ship atTwo = plasmaArmedDestroyer();
        ScenarioLoader.applyWeaponStatus(atTwo, 2);
        for (com.sfb.weapons.Weapon w : atTwo.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.Photon p) {
                assertFalse("S4.12: one arming turn short at WS-2", p.isArmed());
                assertEquals(p.totalArmingTurns() - 1, p.getArmingTurn());
            }
    }

    /**
     * FP1.132 draws the line at the LAUNCHER's own type, not at what it is loading: "Launchers
     * larger than type-F ... cannot have a downloaded type-F at WS-0". A bigger tube has no
     * stasis box, so it gets nothing from FP1.23.
     *
     * <p>The plasma-G is attached here rather than borrowed from another hull so the contrast
     * sits on ONE ship in ONE call — the same reason the photons above do.
     */
    @Test
    public void aLargerLauncherGetsNothingFromThePlasmaFException() {
        Ship ship = plasmaArmedDestroyer();
        com.sfb.weapons.PlasmaLauncher g =
                new com.sfb.weapons.PlasmaLauncher(PlasmaType.G);
        g.setDesignator("Z");
        ship.getWeapons().addWeapon(g);

        ScenarioLoader.applyWeaponStatus(ship, 0);

        assertFalse("a plasma-G has no stasis box and is not ready at WS-0", g.isArmed());
        for (com.sfb.weapons.PlasmaLauncher f : launchers(ship, PlasmaType.F))
            assertTrue("while the plasma-F beside it is", f.isArmed());
    }

    /** R13.17 / R2: the Federation DDL, two plasma-F at FP alongside its photons. */
    private static Ship plasmaArmedDestroyer() {
        ShipLibrary.loadAllSpecs("../data/factions");
        com.sfb.objects.ShipSpec spec = ShipLibrary.get("Federation", "DDL");
        assertNotNull("fixture: Federation/DDL must be in the library", spec);
        return ShipLibrary.createShip(spec);
    }

    private static List<com.sfb.weapons.PlasmaLauncher> launchers(Ship ship, PlasmaType type) {
        List<com.sfb.weapons.PlasmaLauncher> out = new java.util.ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.PlasmaLauncher pl && pl.getLauncherType() == type)
                out.add(pl);
        return out;
    }

    /**
     * S4.12 / E4.21: a photon at weapon status II has completed one arming turn, which means
     * two points of warp energy are sitting in the tube. The warhead is made of that stored
     * energy (E4.413), so recording the turn without the energy halves every overload built
     * on top of it.
     */
    @Test
    public void weaponStatusTwo_photonHoldsItsFirstTurnOfEnergy() {
        ShipLibrary.loadAllSpecs("../data/factions");
        assumeTrue("ShipLibrary must load specs", ShipLibrary.isLoaded());
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());

        ScenarioLoader.applyWeaponStatus(ship, 2);

        boolean sawPhoton = false;
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof com.sfb.weapons.Photon)) continue;
            sawPhoton = true;
            com.sfb.weapons.Photon p = (com.sfb.weapons.Photon) w;
            assertEquals("one arming turn complete", 1, p.getArmingTurn());
            assertEquals("and its two points in the tube", 2.0, p.getArmingEnergy(), 0.001);
            assertFalse("not yet armed", p.isArmed());
        }
        assumeTrue("FedCA must carry photons", sawPhoton);
    }

    // -------------------------------------------------------------------------
    // S4.32 — free photon overload energy at WS-III
    // -------------------------------------------------------------------------

    /** A scenario spec naming one ship at the given weapon status. */
    private ScenarioSpec specFor(String shipName, int weaponStatus) {
        ScenarioSpec spec = new ScenarioSpec();
        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.shipName = shipName;
        setup.weaponStatus = weaponStatus;
        side.ships = java.util.List.of(setup);
        spec.sides = java.util.List.of(side);
        return spec;
    }

    private com.sfb.weapons.Photon tube(Ship ship, String designator) {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.Photon && designator.equals(w.getDesignator()))
                return (com.sfb.weapons.Photon) w;
        throw new IllegalStateException("no photon " + designator);
    }

    private Ship federationCruiserAt(int weaponStatus) {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");
        ScenarioLoader.applyWeaponStatus(ship, weaponStatus);
        return ship;
    }

    /**
     * S4.32: the pool may be concentrated. A four-tube cruiser has eight free points, enough
     * for two tubes at a full 100% overload while the other two stay standard.
     */
    @Test
    public void freeOverload_canBeConcentratedOnSomeTubes() {
        Ship ship = federationCruiserAt(3);
        CoiLoadout loadout = new CoiLoadout();
        loadout.photonOverload.put("A", 4.0);
        loadout.photonOverload.put("B", 4.0);

        ScenarioLoader.applyCoi(ship, loadout, specFor("USS Enterprise", 3));

        assertEquals("full overload", 8.0, tube(ship, "A").getArmingEnergy(), 0.001);
        assertEquals(16, (int) (tube(ship, "A").getArmingEnergy() * 2));
        assertEquals(8.0, tube(ship, "B").getArmingEnergy(), 0.001);
        assertEquals("untouched tubes stay standard", 4.0, tube(ship, "C").getArmingEnergy(), 0.001);
        assertEquals(com.sfb.properties.WeaponArmingType.STANDARD, tube(ship, "C").getArmingType());
        assertEquals(com.sfb.properties.WeaponArmingType.OVERLOAD, tube(ship, "A").getArmingType());
    }

    /** Spreading it evenly is the other extreme: every tube half-overloaded, 12 damage each. */
    @Test
    public void freeOverload_canBeSpreadEvenly() {
        Ship ship = federationCruiserAt(3);
        CoiLoadout loadout = new CoiLoadout();
        for (String d : java.util.List.of("A", "B", "C", "D"))
            loadout.photonOverload.put(d, 2.0);

        ScenarioLoader.applyCoi(ship, loadout, specFor("USS Enterprise", 3));

        for (String d : java.util.List.of("A", "B", "C", "D")) {
            assertEquals(6.0, tube(ship, d).getArmingEnergy(), 0.001);
            assertEquals("12 damage each", 12, (int) (tube(ship, d).getArmingEnergy() * 2));
        }
    }

    /** S4.32: the pool is two points per tube and no more — asking for more is trimmed. */
    @Test
    public void freeOverload_isCappedAtTwoPointsPerTube() {
        Ship ship = federationCruiserAt(3);
        CoiLoadout loadout = new CoiLoadout();
        for (String d : java.util.List.of("A", "B", "C", "D"))
            loadout.photonOverload.put(d, 4.0);   // 16 asked for, 8 available

        ScenarioLoader.applyCoi(ship, loadout, specFor("USS Enterprise", 3));

        double total = 0;
        for (String d : java.util.List.of("A", "B", "C", "D"))
            total += tube(ship, d).getArmingEnergy();
        assertEquals("four tubes of standard arming plus the eight-point pool",
                4 * 4.0 + 8.0, total, 0.001);
    }

    /** S4.32: prior-turn arming at WS-II carries no overload energy at all. */
    @Test
    public void freeOverload_isIgnoredBelowWeaponStatusThree() {
        Ship ship = federationCruiserAt(2);
        CoiLoadout loadout = new CoiLoadout();
        loadout.photonOverload.put("A", 4.0);

        ScenarioLoader.applyCoi(ship, loadout, specFor("USS Enterprise", 2));

        com.sfb.weapons.Photon a = tube(ship, "A");
        assertEquals("still just its first arming turn", 2.0, a.getArmingEnergy(), 0.001);
        assertEquals(com.sfb.properties.WeaponArmingType.STANDARD, a.getArmingType());
    }

    /**
     * S4.32 again, by the other door: the WS-2 arming-mode override used to commit a photon to
     * overload with only four points in the tube, which E4.414 leaves unfirable.
     */
    @Test
    public void weaponStatusTwo_overrideCannotStartAPhotonOverloaded() {
        Ship ship = federationCruiserAt(2);
        CoiLoadout loadout = new CoiLoadout();
        loadout.weaponArmingModes.put("A", com.sfb.properties.WeaponArmingType.OVERLOAD);

        ScenarioLoader.applyCoi(ship, loadout, specFor("USS Enterprise", 2));

        com.sfb.weapons.Photon a = tube(ship, "A");
        assertEquals(com.sfb.properties.WeaponArmingType.STANDARD, a.getArmingType());
        assertTrue("and it is a torpedo that can actually be fired", a.isFirableOverload());
    }
}
