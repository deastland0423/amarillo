package com.sfb.scenario;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.weapons.PlasmaRack;
import com.sfb.weapons.Weapon;

/**
 * The two things a plasma rack gets at SETUP rather than in play: the Y175 reload refit
 * (FP10.312) and its weapon-status activation (FP10.25).
 *
 * <h2>Why this is its own test class</h2>
 * Both live in {@link ScenarioLoader}, which {@code ShipLibrary.createShip} does not go through —
 * so a rack built straight from its ship file has one reload set and nothing activated, and that
 * is correct. A test that only ever built ships the short way would have reported the refit
 * working when nothing called it. The Romulan K5D is the fixture because it is the first hull
 * with plasma racks and no fighters: four racks, nothing else to confuse the reading.
 */
public class PlasmaRackScenarioSetupTest {

    private ShipSpec k5dSpec;

    @Before
    public void load() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        k5dSpec = ShipLibrary.get("Romulan", "K5D");
        assertNotNull("fixture: the K5D should be in the library", k5dSpec);
    }

    private Ship k5d() {
        return ShipLibrary.createShip(k5dSpec);
    }

    private static List<PlasmaRack> racksOf(Ship ship) {
        List<PlasmaRack> racks = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof PlasmaRack rack)
                racks.add(rack);
        return racks;
    }

    /** The K5D carries four, which is what makes FP10.242 bite on it (two in offensive mode). */
    @Test
    public void theK5dCarriesFourRacks() {
        assertEquals(4, racksOf(k5d()).size());
        assertEquals("sixteen torpedoes, so eight points to activate them all (FP9.22)",
                8.0, k5d().plasmaActivationWanted(), 0.001);
    }

    // ---------------------------------------------------------------- FP10.312, the Y175 refit

    /** FP10.312: "Each rack comes with one set of reloads (four torpedoes)." */
    @Test
    public void beforeY175ARackHasOneSetOfReloads() {
        Ship ship = k5d();
        ScenarioLoader.applyYearUpgrades(ship, "Romulan", 174, k5dSpec);

        for (PlasmaRack rack : racksOf(ship))
            assertEquals(1, rack.getReloadSets());
    }

    /**
     * FP10.312: "Along with the Y175 drone rack refits, each plasma rack has two sets of reloads;
     * there is no extra cost for this."
     * <p>
     * No extra cost is asserted too, because every other entry in the Y175 block is priced and a
     * refit that quietly charged BPV would look exactly like one that did not.
     */
    @Test
    public void fromY175ARackHasTwoSetsAndCostsNothingExtra() {
        Ship ship = k5d();
        int bpvBefore = ship.getBattlePointValue();

        ScenarioLoader.applyYearUpgrades(ship, "Romulan", 175, k5dSpec);

        for (PlasmaRack rack : racksOf(ship))
            assertEquals(2, rack.getReloadSets());
        assertEquals("FP10.312: no extra cost", bpvBefore, ship.getBattlePointValue());
    }

    /**
     * It is NOT faction-specific, unlike the Y175 refits it travels with — those switch on the
     * navy, and a plasma rack's second set does not. FP10.15 gives the weapon to the Gorns, ISC,
     * Romulans and Orions, so the refit has to reach all four; checked by asking for it as a Gorn
     * on a hull that is in fact Romulan, which is exactly what the faction switch would have
     * stopped.
     */
    @Test
    public void theRefitIsNotFactionSpecific() {
        Ship ship = k5d();

        ScenarioLoader.applyYearUpgrades(ship, "Gorn", 180, k5dSpec);

        for (PlasmaRack rack : racksOf(ship))
            assertEquals(2, rack.getReloadSets());
    }

    // ---------------------------------------------------------------- FP10.25, weapon status

    /**
     * FP10.25: "Status 0 — torpedoes inactive. Status 1 — torpedoes inactive."
     * <p>
     * Pinned, but honestly: a rack arrives full of torpedoes (J4.886) with none ACTIVATED, so the
     * constructor already agrees with the rule here and removing the WS-0 wiring fails nothing.
     * The value of this test is that it fixes the two readings apart — a full rack is not an armed
     * one — and it would catch a later change that activated torpedoes at construction, which is
     * exactly the shortcut someone reaching for "racks start full" would take.
     */
    @Test
    public void atWeaponStatusZeroAndOneNothingIsActive() {
        for (int ws : new int[]{0, 1}) {
            Ship ship = k5d();
            ScenarioLoader.applyWeaponStatus(ship, ws);

            for (PlasmaRack rack : racksOf(ship)) {
                assertEquals("WS-" + ws + " leaves them inactive", 0, rack.getActiveTorpedoes());
                assertEquals("but the torpedoes are still aboard",
                        PlasmaRack.CAPACITY, rack.getTorpedoes());
            }
        }
    }

    /** FP10.25: "Status II — one torpedo per rack is active." Per RACK, not per ship. */
    @Test
    public void atWeaponStatusTwoOneTorpedoPerRackIsActive() {
        Ship ship = k5d();
        ScenarioLoader.applyWeaponStatus(ship, 2);

        for (PlasmaRack rack : racksOf(ship))
            assertEquals(1, rack.getActiveTorpedoes());
        assertEquals("four racks, so four torpedoes ready across the ship, and twelve to pay for",
                6.0, ship.plasmaActivationWanted(), 0.001);
    }

    /** FP10.25: "Status III — all torpedoes on racks are active." */
    @Test
    public void atWeaponStatusThreeEveryTorpedoIsActive() {
        Ship ship = k5d();
        ScenarioLoader.applyWeaponStatus(ship, 3);

        for (PlasmaRack rack : racksOf(ship))
            assertEquals(PlasmaRack.CAPACITY, rack.getActiveTorpedoes());
        assertEquals("nothing left to pay for", 0.0, ship.plasmaActivationWanted(), 0.001);
    }

    /**
     * And a rack activated by weapon status can actually fire, which is the point of the whole
     * ladder: FP9.22's gate is what WS-III is buying past.
     */
    @Test
    public void aRackAtWeaponStatusThreeCanLaunchImmediately() {
        Ship ship = k5d();
        ScenarioLoader.applyWeaponStatus(ship, 3);
        PlasmaRack rack = racksOf(ship).get(0);

        assertNull("no refusal", rack.launchRefusal(PlasmaRack.RackMode.DEFENSIVE));
        assertNotNull("and a torpedo comes out", rack.launch(PlasmaRack.RackMode.DEFENSIVE));
    }

    /** Where a rack at WS-0 is refused for exactly the reason FP9.22 gives. */
    @Test
    public void aRackAtWeaponStatusZeroIsRefusedForWantOfActivation() {
        Ship ship = k5d();
        ScenarioLoader.applyWeaponStatus(ship, 0);
        PlasmaRack rack = racksOf(ship).get(0);

        String refusal = rack.launchRefusal(PlasmaRack.RackMode.DEFENSIVE);
        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains("FP9.22"));
    }
}
