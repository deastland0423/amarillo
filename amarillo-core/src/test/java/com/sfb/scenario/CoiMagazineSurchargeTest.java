package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.DroneLoad;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * FD3.44 and FD3.84 on the OTHER bill: what a magazine launcher's special drones cost the
 * Commander's Option budget (S3.2).
 *
 * <h2>Why there are two of these</h2>
 * A drone costs twice over, against two different purses. Its SPEED is the ship's own charge and
 * never touches the 20% (S3.221) — that half is {@code MagazineRackSpeedChargeTest}. What a
 * player pays to put something better than a type-I-slow in the rack (FD2.22) comes out of the
 * budget, and that is this one. The magazine exception applies to both, because FD2.45 is one
 * sentence about both: "the cost of improved drones (FD2.22) is paid only for a single loading
 * of each launch rack".
 *
 * <p>The speed half was fixed first because it is charged automatically, to every hull that
 * mounts a type-D, whether or not anyone opens the options dialog. This half only bites a player
 * who loads a magazine launcher with special drones by hand — but when it bites it is the same
 * factor of three, and on a 92-point Kzinti tug the budget it eats is eighteen points.
 *
 * @see PaidLoading
 */
public class CoiMagazineSurchargeTest {

    private static final double EXACT = 0.0001;

    /** FD10.51's swordfish module: a point where the half-space explosive it displaces is a half. */
    private static final String SPECIAL = "TypeI+SWORDFISH_PHASER3";

    /** The surcharge one of those is worth over the type-I-slow already in the ship's BPV. */
    private static final double SPECIAL_SURCHARGE = 0.50;

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private static List<DroneLoad> fill(String drone, int count) {
        return Collections.nCopies(count, DroneLoad.parse(drone));
    }

    private static Ship bought(String faction, String type, int year) {
        FleetSpec spec = new FleetSpec();
        spec.year = year;
        spec.budget = 4000;
        spec.factions.add(faction);
        FleetSpec.ShipEntry entry = new FleetSpec.ShipEntry();
        entry.faction = faction;
        entry.type = type;
        spec.ships.add(entry);

        FleetLoader.Resolution r = FleetLoader.resolve(spec);
        assertTrue("fixture: " + type + " should resolve — " + r.unknown(), r.isComplete());
        return r.ships().get(0);
    }

    private static List<DroneRack> racksOf(Ship ship) {
        List<DroneRack> racks = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                racks.add(rack);
        return racks;
    }

    private static ScenarioSpec year(int y) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = y;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        return spec;
    }

    // ---------------------------------------------------------------- the rack on its own

    /**
     * Twelve swordfish drones in a type-D. FD3.44 charges for one magazine per launcher, so four
     * spaces of the twelve are paid for and the other eight are reloads.
     */
    @Test
    public void aTypeDRackIsChargedForOneMagazineOfSpecialDrones() {
        List<DroneLoad> load = fill(SPECIAL, 12);
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_D);

        assertEquals("what the rule asks: four spaces at a half",
                4 * SPECIAL_SURCHARGE, CoiLoadout.rackUpgradeCost(rack, load, 183), EXACT);
        assertEquals("and what was charged before the rack was in the question",
                12 * SPECIAL_SURCHARGE, CoiLoadout.rackUpgradeCost(load, 183), EXACT);
    }

    /** FD3.84's type-H: two magazines of the five, so eight spaces of twenty. */
    @Test
    public void aTypeHRackIsChargedForTwoMagazinesOfFive() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_H);

        assertEquals(8 * SPECIAL_SURCHARGE,
                CoiLoadout.rackUpgradeCost(rack, fill(SPECIAL, 20), 183), EXACT);
    }

    /**
     * And nobody else changes. An ordinary rack keeps its reloads in a list of its own that no
     * bill ever walks, so FD2.45 was already satisfied for it by where the drones are kept.
     */
    @Test
    public void anOrdinaryRackIsStillChargedForEverythingInIt() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_C);
        List<DroneLoad> load = fill(SPECIAL, rack.getSpaces());

        assertEquals(rack.getSpaces() * SPECIAL_SURCHARGE,
                CoiLoadout.rackUpgradeCost(rack, load, 183), EXACT);
        assertEquals("the rack-less reading agrees, which is why it is still safe to call",
                rack.getSpaces() * SPECIAL_SURCHARGE,
                CoiLoadout.rackUpgradeCost(load, 183), EXACT);
    }

    /**
     * FD2.452 picks which magazine is the paid one — "the reload drones are presumed to be of
     * the same cost (or lower) as the drones in the rack" — so it has to be the dearest.
     * Otherwise the exception is an exploit: load one magazine cheaply, declare that the paid
     * one, and fly two magazines of special drones for nothing.
     *
     * <p>Eight cheap drones FIRST in the list and four dear ones behind them, so the answer
     * cannot come from the ordering. Three readings, three different numbers, which is the
     * point of choosing these two drones: 8.00 if every magazine is billed, 2.00 if the first
     * four spaces found are taken, and 4.00 for the rule.
     *
     * <p>Not the ordinary type-I for the cheap eight, and that IS the test rather than a
     * detail. A type-I-slow is in the ship's BPV already (FD2.31) and costs the budget nothing,
     * so eight of those and four swordfish come to 2.00 whether the cheap ones are billed or
     * not — and the assertion would have held against a charge that had never heard of FD3.44.
     * A type-III is worth a half here (FD2.227 makes its slow speed 12, so it is an upgrade on
     * the type-I the hull paid for), and a swordfish module on top of one is worth a point.
     */
    @Test
    public void theDearestMagazineIsTheOneThatIsPaidFor() {
        List<DroneLoad> load = new ArrayList<>(fill("TypeIII", 8));
        load.addAll(fill("TypeIII+SWORDFISH_PHASER3", 4));

        assertEquals("the four swordfish drones are the magazine that is paid for", 4.0,
                CoiLoadout.rackUpgradeCost(new DroneRack(DroneRack.DroneRackType.TYPE_D),
                        load, 183), EXACT);
        assertEquals("where billing every magazine reads twice that", 8.0,
                CoiLoadout.rackUpgradeCost(load, 183), EXACT);
    }

    // ---------------------------------------------------------------- through a hull

    /**
     * The loadout names its racks by INDEX, so the exception can only be applied by a caller
     * holding the ship — which is the whole reason {@code droneUpgradeCost} grew a second
     * reading rather than simply changing.
     */
    @Test
    public void theShipIsWhatTellsAMagazineLauncherFromAnOrdinaryRack() {
        Ship tgt = bought("Kzinti", "TGT", 183);
        assertEquals("fixture: two type-D launchers", 2, racksOf(tgt).size());

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoads(0, fill(SPECIAL, 12));
        loadout.setDroneRackLoads(1, fill(SPECIAL, 12));

        assertEquals("twenty-four spaces billed where FD3.44 asks for eight",
                24 * SPECIAL_SURCHARGE, loadout.droneUpgradeCost(183), EXACT);
        assertEquals("one magazine per launcher", 8 * SPECIAL_SURCHARGE,
                loadout.droneUpgradeCost(tgt, 183), EXACT);
        assertEquals("and the figure the budget is checked against moves with it",
                8 * SPECIAL_SURCHARGE, loadout.totalCost(tgt, 183), EXACT);
    }

    /**
     * A rack index with no rack behind it is charged in full rather than waved through.
     * {@code applyCoi} refuses the index and loads nothing, so pricing it at zero would be a
     * disagreement about a loadout that does not exist — and the safe side of that disagreement
     * is the one that does not quietly enlarge the budget.
     */
    @Test
    public void anIndexWithNoRackBehindItIsStillPriced() {
        Ship tgt = bought("Kzinti", "TGT", 183);

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoads(7, fill(SPECIAL, 4));

        assertEquals(4 * SPECIAL_SURCHARGE, loadout.droneUpgradeCost(tgt, 183), EXACT);
    }

    /**
     * End to end, which is the number that matters: {@code applyCoi} bills the ship and
     * {@code setCoiSpend} hands that figure to the ENEMY as victory points (S2.20 step B). On a
     * 92-point hull with eighteen points of options to spend, the difference between four and
     * twelve is most of the budget.
     */
    @Test
    public void theSpendRecordedOnTheShipIsTheMagazineFigure() {
        Ship tgt = bought("Kzinti", "TGT", 183);

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoads(0, fill(SPECIAL, 12));
        loadout.setDroneRackLoads(1, fill(SPECIAL, 12));
        ScenarioLoader.applyCoi(tgt, loadout, year(183));

        assertEquals("two launchers, one magazine each, four spaces of swordfish apiece",
                8 * SPECIAL_SURCHARGE, tgt.getCoiSpend(), EXACT);
        for (DroneRack rack : racksOf(tgt))
            assertEquals("and all twelve are aboard, paid for or not",
                    12, rack.getAmmo().size());
    }

    /**
     * And the budget refusal reads the same bill. A Kzinti battle station carries two type-D
     * launchers; filling both with swordfish drones costs 12 points unbilled by magazine and 4
     * billed by it, and {@link CoiBudget#refusalFor} quotes whichever it used.
     */
    @Test
    public void theBudgetRefusalQuotesTheMagazineFigure() {
        Ship bs = bought("Kzinti", "BS", 183);

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoads(0, fill(SPECIAL, 12));
        loadout.setDroneRackLoads(1, fill(SPECIAL, 12));
        // Boarding parties to take it past the allowance, so there is a message to read.
        loadout.extraBoardingParties = CoiLoadout.MAX_EXTRA_BP;

        double allowance = CoiBudget.allowanceFor(bs, 20);
        loadout.extraTBombs = (int) Math.ceil(allowance / CoiLoadout.COST_TBOMB);

        String refusal = CoiBudget.refusalFor(bs, loadout, 20, 183);
        assertTrue("a loadout this big should be refused", refusal != null);
        assertTrue("the quoted spend should be the magazine one, not three times it: " + refusal,
                refusal.contains(CoiBudget.trim(loadout.totalCost(bs, 183))));
    }
}
