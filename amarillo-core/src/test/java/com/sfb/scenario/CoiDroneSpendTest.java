package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KzintiShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * A drone loadout is a Commander's Option, and it has to be BILLED like one (S2.20 B).
 *
 * <h2>The hole this closes</h2>
 * {@code applyCoi} spends a running {@code spent} down the ship's allowance and finishes with
 * {@code ship.setCoiSpend(spent)}. Every other item added to it; the drone racks did not — the
 * section was commented "free". The cost was not missing, exactly: {@code CoiLoadout.totalCost}
 * has counted it since the year went in, and {@code CoiBudget.refusalFor} uses that to refuse an
 * over-budget loadout at the endpoint. So the budget could not be overspent, which is why this
 * stayed quiet.
 *
 * <p>What it got wrong was the SCORE. S2.20 step B awards the enemy "the BPV of Commander's
 * Option Items purchased", and {@code getCoiSpend} is the figure that reaches the scoreboard
 * through {@code VictoryCalculator}. A player could arm every rack with swordfish drones and be
 * scored as though the ship had sailed with the type-I-slows its BPV already includes.
 *
 * <p>Deliberately asserted on {@code getCoiSpend} rather than on {@code droneUpgradeCost}: the
 * arithmetic was never the broken part, the wiring was, and a test of the arithmetic would have
 * passed throughout.
 */
public class CoiDroneSpendTest {

    private static final double EXACT = 0.0001;

    /** A Federation FFG: 75 BPV, one four-space type-G rack. 20% is a 15-point allowance. */
    private static Ship ffg() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedFfg());
        return ship;
    }

    private static DroneRack rackOf(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r)
                return r;
        throw new AssertionError("fixture needs a drone rack");
    }

    private static ScenarioSpec year(int y) {
        return year(y, 20);
    }

    private static ScenarioSpec year(int y, int budgetPercent) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = y;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        spec.commanderOptions.budgetPercent = budgetPercent;
        return spec;
    }

    private static List<DroneLoad> loads(String... drones) {
        List<DroneLoad> out = new ArrayList<>();
        for (String d : drones)
            out.add(DroneLoad.parse(d));
        return out;
    }

    private static CoiLoadout rackLoad(String... drones) {
        CoiLoadout out = new CoiLoadout();
        out.setDroneRackLoads(0, loads(drones));
        return out;
    }

    private static String notesOf(Ship ship) {
        return String.join(" | ", ship.getSetupNotes());
    }

    // ------------------------------------------------------------------ it is billed

    /**
     * FD10.51 prices a swordfish module at a point where the explosive it displaces costs half,
     * against a type-I-slow baseline of a point per space: half a point, and now half a point of
     * the enemy's victory total.
     */
    @Test
    public void aDroneLoadoutIsBilledToTheShipsOptionSpend() {
        Ship ship = ffg();
        ScenarioLoader.applyCoi(ship,
                rackLoad("TypeI+SWORDFISH_PHASER3", "TypeI", "TypeI", "TypeI"), year(180));

        assertEquals("the swordfish module's surcharge, on the ship's COI bill",
                0.50, ship.getCoiSpend(), EXACT);
    }

    /** And it ADDS to the items that were already counted rather than replacing them. */
    @Test
    public void theDroneBillAddsToTheOtherOptionsRatherThanReplacingThem() {
        Ship ship = ffg();
        CoiLoadout loadout = rackLoad("TypeI+SWORDFISH_PHASER3", "TypeI", "TypeI", "TypeI");
        loadout.extraTBombs = 1;

        ScenarioLoader.applyCoi(ship, loadout, year(180));

        assertEquals("4.00 for the T-bomb and 0.50 for the drone",
                4.50, ship.getCoiSpend(), EXACT);
    }

    /** Four racks, so the sum is the sum: each rack's surcharge, not the first one's. */
    @Test
    public void eachRackIsBilledSeparately() {
        Ship ship = new Ship();
        ship.init(KzintiShips.getKzinCS());
        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoads(0, loads("TypeI+SWORDFISH_PHASER3", "TypeI", "TypeI", "TypeI"));
        loadout.setDroneRackLoads(1, loads("TypeI+SWORDFISH_PHASER3", "TypeI", "TypeI", "TypeI"));

        ScenarioLoader.applyCoi(ship, loadout, year(180));

        assertEquals("half a point in each of two racks", 1.00, ship.getCoiSpend(), EXACT);
    }

    /**
     * S3.221's exception, which is the clause that makes this a function of the year: "Faster
     * drones available as limited or restricted weapons in years prior to their general
     * introduction are purchased as Commander's Options."
     *
     * <p>Fast drones are Restricted in Y179 and General in Y180 (FD10.651). So the SAME rack is
     * bought by the player one year and issued to the ship the next: in Y180 the charge is on
     * the hull's own bill ({@code Ship.getDroneSpeedBpv}, S2.11) and the budget owes nothing.
     */
    @Test
    public void speedBoughtAheadOfItsGeneralIntroductionIsOnTheOptionBill() {
        Ship early = ffg();
        ScenarioLoader.applyCoi(early,
                rackLoad("TypeIF", "TypeIF", "TypeIF", "TypeIF"), year(179));

        assertEquals("half a point per drone, fast speed being Restricted in Y179",
                2.00, early.getCoiSpend(), EXACT);

        Ship standard = ffg();
        ScenarioLoader.applyCoi(standard,
                rackLoad("TypeIF", "TypeIF", "TypeIF", "TypeIF"), year(180));

        assertEquals("and nothing a year later, when the ship is charged for them instead",
                0.0, standard.getCoiSpend(), EXACT);
    }

    // ------------------------------------------------------------------ and never backwards

    /**
     * FD2.22 calls these "surcharges", and nothing in the rules hands points back for arming a
     * ship worse than it came. A type-IV carrying one half-space explosive is the easy way to be
     * under: two rack spaces, 1.25 points of drone, 2.00 points of type-I-slows displaced.
     *
     * <p>Unclamped it would be a credit of 1.50 — which would then buy boarding parties.
     */
    @Test
    public void aLoadoutCheaperThanItsBaselineEarnsNoRefund() {
        Ship ship = ffg();
        ScenarioLoader.applyCoi(ship,
                rackLoad("TypeIV+EXPLOSIVE_HALF", "TypeIV+EXPLOSIVE_HALF"), year(180));

        assertEquals("under the baseline is free, not paid for",
                0.0, ship.getCoiSpend(), EXACT);
    }

    /**
     * And the clamp is asked of each rack rather than of the sum, which is a different claim: an
     * under-filled rack must not pay for the rack beside it. Rack 0 is 1.50 under, rack 1 is
     * half a point over, and the answer is half a point rather than nothing.
     */
    @Test
    public void aCheapRackDoesNotSubsidiseAnExpensiveOneBesideIt() {
        Ship ship = new Ship();
        ship.init(KzintiShips.getKzinCS());
        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoads(0, loads("TypeIV+EXPLOSIVE_HALF", "TypeIV+EXPLOSIVE_HALF"));
        loadout.setDroneRackLoads(1, loads("TypeI+SWORDFISH_PHASER3", "TypeI", "TypeI", "TypeI"));

        ScenarioLoader.applyCoi(ship, loadout, year(180));

        assertEquals("the swordfish is still paid for in full",
                0.50, ship.getCoiSpend(), EXACT);
    }

    // ------------------------------------------------------------------ and only when it loads

    /** A rack nobody touched keeps what it came with (S2.11) and costs the budget nothing. */
    @Test
    public void anUntouchedRackCostsNothing() {
        Ship ship = ffg();
        ScenarioLoader.applyCoi(ship, new CoiLoadout(), year(180));

        assertEquals(0.0, ship.getCoiSpend(), EXACT);
    }

    /**
     * This loop skips racks, and a skipped rack must not be charged for — which is why the cost
     * is asked per rack rather than of the whole loadout. Five drones will not fit four spaces,
     * so nothing is loaded and nothing is owed.
     */
    @Test
    public void aRackThatDoesNotFitIsNotCharged() {
        Ship ship = ffg();
        ScenarioLoader.applyCoi(ship, rackLoad("TypeI+SWORDFISH_PHASER3", "TypeI", "TypeI",
                "TypeI", "TypeI"), year(180));

        assertEquals("the rack was refused, so the enemy scores nothing for it",
                0.0, ship.getCoiSpend(), EXACT);
        assertTrue(notesOf(ship), notesOf(ship).contains("exceeds rack size"));
    }

    /** Same for a rack refused on the year: fast drones do not exist in Y166 (FD10.651). */
    @Test
    public void aRackRefusedOnTheYearIsNotCharged() {
        Ship ship = ffg();
        ScenarioLoader.applyCoi(ship, rackLoad("TypeIF", "TypeIF", "TypeIF", "TypeIF"),
                year(166));

        assertEquals(0.0, ship.getCoiSpend(), EXACT);
        assertTrue(notesOf(ship), notesOf(ship).contains("not available in year 166"));
    }

    /**
     * The boundary, which is the one the other items in {@code applyCoi} all use: a loadout
     * costing exactly what is left IS affordable. Two swordfish modules are a point, and 2% of
     * the FFG's 75 is a one-point allowance.
     */
    @Test
    public void aLoadoutThatExactlyFitsTheBudgetIsBought() {
        Ship ship = ffg();
        ScenarioLoader.applyCoi(ship, rackLoad("TypeI+SWORDFISH_PHASER3",
                "TypeI+SWORDFISH_PHASER3", "TypeI", "TypeI"), year(180, 2));

        assertEquals("a point of allowance, a point spent", 1.00, ship.getCoiSpend(), EXACT);
        assertEquals("and the drones are aboard", 4, rackOf(ship).getAmmo().size());
        assertFalse("with nothing refused for cost: " + notesOf(ship),
                notesOf(ship).contains("budget is left"));
    }

    /**
     * The gate itself, which matters for a loadout written into a scenario FILE: those never
     * pass through the endpoint's {@code CoiBudget.refusalFor}, so {@code applyCoi} is the only
     * thing standing between them and a free upgrade.
     *
     * <p>A nought-percent allowance is the cheapest way to say "there is no room for this", and
     * what it proves is that the rack is left alone rather than loaded on credit.
     */
    @Test
    public void aLoadoutWithNoBudgetBehindItIsSkippedRatherThanGivenAway() {
        Ship ship = ffg();
        List<Drone> before = new ArrayList<>(rackOf(ship).getAmmo());

        ScenarioLoader.applyCoi(ship,
                rackLoad("TypeI+SWORDFISH_PHASER3", "TypeI", "TypeI", "TypeI"), year(180, 0));

        assertEquals(0.0, ship.getCoiSpend(), EXACT);
        assertEquals("the rack still holds what the ship arrived with",
                before.size(), rackOf(ship).getAmmo().size());
        assertTrue(notesOf(ship), notesOf(ship).contains("budget is left"));
    }
}
