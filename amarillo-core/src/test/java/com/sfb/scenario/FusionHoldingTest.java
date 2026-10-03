package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.weapons.Fusion;
import com.sfb.weapons.Weapon;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * E7.5: the Hydran fusion holding system, and the pre-Y168 state it replaced.
 *
 * <h2>The rule</h2>
 * <b>E7.23</b>: "When first deployed, armed fusion beams could not be held in an armed state, but
 * had to be fired or discharged (E1.24) shortly after (i.e. on the turn that) they were armed. If
 * the weapon is not fired on the turn it is armed, the energy is lost, but the weapon does not
 * need to cool and can be armed and fired during the next turn."
 * <p>
 * <b>E7.5</b> dates the system to Y168, "installed on virtually all fusion-armed ships by the time
 * the Hydrans entered the General War in Y169", and "there is no cost for this refit" — so it is a
 * free universal refit the data never declares, exactly like the plasma rack's Y175 reload set.
 *
 * <h2>Why it matters, and why it was wrong</h2>
 * Four Hydran hulls in the data are Y134 — Hunter, Lancer, Ranger and Small Q-Ship — and before
 * this they could all hold their fusions in a pre-refit scenario. That let early Hydrans cheat the
 * single thing that defines them: with holding, a fusion ship arms out of range and releases at
 * range 1, which is precisely what E7.23 denies it. Without holding it must arm in the open and
 * fire that turn or lose the energy, so it has to take damage to close.
 */
public class FusionHoldingTest {

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    // ------------------------------------------------------------------ helpers

    private Fusion armedFusion(boolean withHoldingSystem) {
        Fusion fusion = new Fusion();
        fusion.setDesignator("A");
        fusion.setHoldingSystem(withHoldingSystem);
        assertTrue("fixture: a fusion arms for 2 energy", fusion.arm(2));
        assertTrue("fixture: and is then armed", fusion.isArmed());
        return fusion;
    }

    /** One Hydran hull through the REAL loader, at a scenario year. */
    private Ship hydranThroughTheLoader(String type, int year) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = year;

        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.type = type;
        setup.shipName = "HMS Test";
        setup.startHex = "0510";
        setup.startHeading = "A";
        setup.weaponStatus = 3;

        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = "Hydran";
        side.ships = List.of(setup);
        spec.sides = List.of(side);

        List<List<Ship>> sides = ScenarioLoader.loadShips(spec);
        assertEquals("fixture: one side", 1, sides.size());
        assertEquals("fixture: one ship — did " + type + " fail to load?", 1, sides.get(0).size());
        return sides.get(0).get(0);
    }

    private List<Fusion> fusionsOf(Ship ship) {
        List<Fusion> found = new java.util.ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof Fusion fusion)
                found.add(fusion);
        assertFalse("fixture: " + ship.getName() + " should carry fusions", found.isEmpty());
        return found;
    }

    // ------------------------------------------------------------------ the year gate

    @Test
    public void theRefitArrivesInY168() {
        Ship ship = new Ship();
        ship.init(com.sfb.samples.FederationShips.getFedCa());
        Fusion fusion = new Fusion();
        fusion.setDesignator("A");
        ship.getWeapons().addWeapon(fusion);

        ScenarioLoader.applyFusionHolding(ship, 167);
        assertFalse("Y167 is still pre-refit (E7.23)", fusion.hasHoldingSystem());

        ScenarioLoader.applyFusionHolding(ship, 168);
        assertTrue("the Hydrans developed it in Y168 (E7.5)", fusion.hasHoldingSystem());
    }

    /**
     * The loader must set it in BOTH directions. A one-way refit would let the modern default
     * leak into an early scenario, which is the bug this whole class exists for.
     */
    @Test
    public void theLoaderTakesTheSystemAwayAgainForAnEarlyYear() {
        Ship ship = new Ship();
        ship.init(com.sfb.samples.FederationShips.getFedCa());
        Fusion fusion = new Fusion();
        fusion.setDesignator("A");
        ship.getWeapons().addWeapon(fusion);

        ScenarioLoader.applyFusionHolding(ship, 180);
        assertTrue(fusion.hasHoldingSystem());
        ScenarioLoader.applyFusionHolding(ship, 134);
        assertFalse("set explicitly, not merely left alone", fusion.hasHoldingSystem());
    }

    // ------------------------------------------------------------------ holding behaviour

    @Test
    public void aRefittedFusionHoldsForOneEnergy() throws Exception {
        Fusion fusion = armedFusion(true);
        assertEquals("E7.51: one point per turn", 1, fusion.holdEnergyCost());
        assertTrue(fusion.hold(1));
    }

    @Test
    public void aPreRefitFusionCannotBeHeldAtAnyPrice() throws Exception {
        Fusion fusion = armedFusion(false);
        assertFalse("E7.23: no holding system, no holding", fusion.hold(1));
        assertFalse(fusion.hold(2));
    }

    /**
     * A zero hold cost is the whole cross-tier signal: the DTO copies it to {@code holdCost} and
     * the energy dialog shows a Hold button only where that is positive. So the pre-refit fusion
     * disappears from the UI's hold options without the server or client knowing about Y168.
     */
    @Test
    public void aPreRefitFusionQuotesNoHoldCostSoNoUiOffersIt() {
        assertEquals(0, armedFusion(false).holdEnergyCost());
        assertEquals(1, armedFusion(true).holdEnergyCost());
    }

    @Test
    public void theRefitDoesNotMakeOverloadsHoldable() throws Exception {
        Fusion fusion = armedFusion(true);
        assertTrue("fixture: promote the armed standard to overload", fusion.arm(2));
        assertEquals(com.sfb.properties.WeaponArmingType.OVERLOAD, fusion.getArmingType());

        assertFalse("E7.52: overloaded fusion beams cannot be held", fusion.hold(1));
        assertEquals(0, fusion.holdEnergyCost());
    }

    // ------------------------------------------------------------------ end of turn

    @Test
    public void anUnheldPreRefitFusionLosesItsEnergyAtEndOfTurn() {
        Fusion fusion = armedFusion(false);
        fusion.cleanUp();
        assertFalse("E7.23: the energy is lost", fusion.isArmed());
    }

    /**
     * E7.23's mercy, and the clause most easily missed: "the weapon does not need to cool and can
     * be armed and fired during the next turn". Discharging is not firing, so no cooldown.
     */
    @Test
    public void butThatFusionNeedsNoCooldownAndCanArmAgainNextTurn() {
        Fusion fusion = armedFusion(false);
        fusion.cleanUp();

        assertFalse("fixture: the discharge must have happened for this to mean anything",
                fusion.isArmed());
        assertFalse("E7.23: it does not need to cool", fusion.isOnCooldown());
        assertTrue("so it can arm again the next turn", fusion.arm(2));
        assertTrue(fusion.isArmed());
        // Arming an already-armed standard fusion with 2 PROMOTES it to overload and also returns
        // true, so without this the test passes even when the discharge above never happened —
        // which is precisely how it behaved when the discharge was mutated out.
        assertEquals("a fresh standard arming, not a promotion of a charge that never cleared",
                com.sfb.properties.WeaponArmingType.STANDARD, fusion.getArmingType());
    }

    @Test
    public void aRefittedFusionKeepsItsChargeAcrossTheTurnBoundary() {
        Fusion fusion = armedFusion(true);
        fusion.cleanUp();
        assertTrue("E7.5: this is exactly what the refit buys", fusion.isArmed());
    }

    // ------------------------------------------------------------------ the real hulls

    /**
     * The gating layer. A test that called {@code applyFusionHolding} alone would pass even if
     * nothing in {@code buildShip} invoked it — which is how {@code applyWeaponStatus} once sat
     * in the codebase with no caller. So this goes through {@code loadShips} on real ship files.
     */
    @Test
    public void theY134RangerCannotHoldItsFusionsInItsOwnEra() {
        Ship ranger = hydranThroughTheLoader("RN", 134);
        for (Fusion fusion : fusionsOf(ranger))
            assertFalse("a Y134 Ranger predates E7.5 by 34 years",
                    fusion.hasHoldingSystem());
    }

    @Test
    public void theSameRangerHoldsThemInAGeneralWarScenario() {
        Ship ranger = hydranThroughTheLoader("RN", 169);
        for (Fusion fusion : fusionsOf(ranger))
            assertTrue("E7.5 was on virtually every fusion ship by Y169",
                    fusion.hasHoldingSystem());
    }

    /** The Paladin is Y169, so it has never known a scenario without the system. */
    @Test
    public void thePaladinHasItInItsOwnServiceYear() {
        for (Fusion fusion : fusionsOf(hydranThroughTheLoader("PAL", 169)))
            assertTrue(fusion.hasHoldingSystem());
    }

    /**
     * EVERY fusion-armed hull in the library, both sides of the date — enumerated from the data
     * rather than from a list of hull codes.
     * <p>
     * Written this way because the list was wrong within the hour: it began as the four Y134
     * Hydrans and the owner added five more hulls (HR, HR+, SC, TR, TR+) while this was being
     * built, one of them Y168 exactly. A hardcoded roster needs editing every time a ship is
     * entered and silently stops covering the new ones; an invariant over {@code ShipLibrary}
     * covers them the moment their file lands.
     */
    @Test
    public void noFusionHullAnywhereCanHoldBeforeY168() {
        int hullsChecked = 0;
        for (com.sfb.objects.ShipSpec spec : ShipLibrary.all()) {
            Ship early = ShipLibrary.createShip(spec);
            boolean carriesFusions = false;
            for (Weapon w : early.getWeapons().fetchAllWeapons())
                if (w instanceof Fusion)
                    carriesFusions = true;
            if (!carriesFusions)
                continue;

            hullsChecked++;
            String who = spec.faction + "/" + spec.type;

            ScenarioLoader.applyFusionHolding(early, 167);
            for (Weapon w : early.getWeapons().fetchAllWeapons())
                if (w instanceof Fusion fusion)
                    assertFalse(who + " must not hold a fusion in Y167 (E7.23)",
                            fusion.hasHoldingSystem());

            Ship late = ShipLibrary.createShip(spec);
            ScenarioLoader.applyFusionHolding(late, 169);
            for (Weapon w : late.getWeapons().fetchAllWeapons())
                if (w instanceof Fusion fusion)
                    assertTrue(who + " should hold a fusion in Y169 (E7.5)",
                            fusion.hasHoldingSystem());
        }
        assertTrue("the library should hold some fusion-armed hulls to check", hullsChecked > 0);
    }

    /** And the four Y134 Hydrans specifically, through the real loader. */
    @Test
    public void theEarliestHydransCannotHoldInTheirOwnEra() {
        for (String type : new String[] { "HN", "LN", "RN", "S-Q" }) {
            Ship ship = hydranThroughTheLoader(type, 134);
            for (Fusion fusion : fusionsOf(ship))
                assertFalse(type + " is pre-Y168 and must not hold (E7.23)",
                        fusion.hasHoldingSystem());
        }
    }

    /**
     * E7.54: "Fusion-armed fighters use a system similar to this, but this is covered in (J4.83)."
     * Honoured structurally — {@code FighterFusion} does not extend {@code Fusion}, so the year
     * gate cannot reach a Stinger's charges however the loader is called.
     */
    @Test
    public void fighterFusionsAreADifferentSystemEntirely() {
        assertFalse("J4.83 charges, not E7.5 holding",
                Fusion.class.isAssignableFrom(com.sfb.weapons.FighterFusion.class));
    }
}
