package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.List;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneFrame;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.DroneSpec;
import com.sfb.objects.DroneType;
import com.sfb.objects.PayloadModule;
import com.sfb.objects.Propulsion;
import com.sfb.objects.Ship;
import com.sfb.samples.KzintiShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * The speed of the drones a ship sails with, and who pays for it (S2.11, S3.221).
 *
 * <h2>What this closes</h2>
 * {@code WeaponFactory} fills every rack with type-I-slows and, until now, no year rule ever
 * looked at them again — so a Y185 Kzinti CVA went into battle with speed-8 drones against ships
 * moving 31, drones {@code DroneType}'s own header calls "obsolete once medium-speed drones
 * arrive in Y165". That default is the loadout most players actually use, because it is what they
 * get by never opening the drone dialog, and it was the worst one on offer.
 *
 * <h2>FD2.454's worked example is the spine of this class</h2>
 * The rulebook does this arithmetic once, for a whole ship, and it happens to be a ship we have:
 * "a Kzinti CS has a BPV of 116. However, it has four drone racks, each of which holds four
 * drones. Equipped with medium-speed drones, the BPV increases by eight points (1/2-point per
 * drone) to 124. The BPV of a Kzinti CS armed with fast drones is 132." Three of the tests below
 * are that sentence, so the model is checked against the rulebook's own figures rather than
 * against my reading of the charts.
 */
public class DroneSpeedYearTest {

    private static final double EXACT = 1e-9;

    /** FD2.454's own ship: BPV 116, four type-A racks, four one-space drones apiece. */
    private static Ship kzintiCs() {
        Ship ship = new Ship();
        ship.init(KzintiShips.getKzinCS());
        return ship;
    }

    private static List<DroneRack> racksOf(Ship ship) {
        List<DroneRack> racks = new java.util.ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r)
                racks.add(r);
        return racks;
    }

    /** Every drone the ship has anywhere in a rack, loaded or in a reload set. */
    private static List<Drone> allDronesOf(Ship ship) {
        List<Drone> out = new java.util.ArrayList<>();
        for (DroneRack rack : racksOf(ship)) {
            out.addAll(rack.getAmmo());
            for (List<Drone> set : rack.getReloads())
                out.addAll(set);
        }
        return out;
    }

    private static ScenarioSpec year(int y) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = y;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        return spec;
    }

    // ------------------------------------------------------------ FD2.454's worked example

    /**
     * Y166: medium speed is Restricted, not General (FD10.65), so nothing is standard issue yet
     * and the CS is the 116-point ship its data file states.
     */
    @Test
    public void beforeMediumSpeedIsGeneralTheShipIsItsPrintedValue() {
        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 166);

        assertEquals(116, cs.getBpv());
        assertEquals("nothing owed", 0, cs.getDroneSpeedBpv());
        for (Drone d : allDronesOf(cs))
            assertSame(DroneType.TypeI, d.getDroneType());
    }

    /**
     * Y167, medium speed General: "the BPV increases by eight points (1/2-point per drone) to
     * 124." Sixteen loaded drones at half a point.
     */
    @Test
    public void mediumSpeedCostsTheKzintiCsEightPoints() {
        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 167);

        assertEquals("FD2.454: 116 + 8 = 124", 8, cs.getDroneSpeedBpv());
        for (Drone d : allDronesOf(cs)) {
            assertSame(DroneType.TypeIM, d.getDroneType());
            assertEquals(20, d.getSpeed());
        }
    }

    /** Y180, fast speed General: "The BPV of a Kzinti CS armed with fast drones is 132." */
    @Test
    public void fastSpeedCostsTheKzintiCsSixteenPoints() {
        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 180);

        assertEquals("FD2.454: 116 + 16 = 132", 16, cs.getDroneSpeedBpv());
        for (Drone d : allDronesOf(cs)) {
            assertSame(DroneType.TypeIF, d.getDroneType());
            assertEquals(32, d.getSpeed());
        }
    }

    /**
     * FD2.45: "The cost of improved drones (FD2.22) is paid only for a single loading of each
     * launch rack... the reloads... are presumed to be of the same type without additional cost."
     *
     * <p>Which is why the sixteen above is sixteen and not thirty-two: a type-A rack carries one
     * reload set, so the ship holds thirty-two fast drones and pays for half of them. FD2.454's
     * figures only come out if the reloads are upgraded AND free, so this pins both halves.
     */
    @Test
    public void reloadsFollowTheRackAndAreChargedForNothing() {
        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 180);

        int loaded = 0, stored = 0;
        for (DroneRack rack : racksOf(cs)) {
            loaded += rack.getAmmo().size();
            for (List<Drone> set : rack.getReloads())
                stored += set.size();
        }
        assertEquals("four racks of four", 16, loaded);
        assertEquals("and a reload set behind each", 16, stored);
        assertEquals("charged for the loading alone", 16, cs.getDroneSpeedBpv());
    }

    // ------------------------------------------------------------ the two pockets

    /**
     * S3.211 takes the option percentage of "the ship, its refits, and its fighters, but not
     * including the cost of mandatory drone speed upgrades" — so the charge must not reach the
     * basis. {@code CoiBudget} has carried a note to this effect since before the charge existed.
     */
    @Test
    public void theChargeStaysOutOfTheOptionBudgetBasis() {
        Ship slow = kzintiCs();
        ScenarioLoader.applyDroneSpeed(slow, 166);
        Ship fast = kzintiCs();
        ScenarioLoader.applyDroneSpeed(fast, 180);

        assertEquals("the same hull buys the same options however fast its drones fly",
                CoiBudget.effectiveAdjustedCombatBpv(slow),
                CoiBudget.effectiveAdjustedCombatBpv(fast), EXACT);
        assertEquals(CoiBudget.allowanceFor(slow, 20), CoiBudget.allowanceFor(fast, 20), EXACT);
    }

    /**
     * And the other side of it: S2.14 lists "the cost of drone speed upgrades (FD2.22)" among
     * what the Gross Adjusted BPV includes, so an enemy scores the fast drones.
     *
     * <p>Read off the real scoreboard rather than summed here, because the sum is the thing that
     * could be wrong: {@code Game.calculateVictoryPoints} builds the GABPV from several terms and
     * a test that added them up itself would pass whichever one got left out.
     */
    @Test
    public void theChargeDoesCountTowardsTheVictoryBasis() {
        com.sfb.Game game = new com.sfb.Game();
        com.sfb.Player kzinti = new com.sfb.Player();
        kzinti.setTeamName("Kzintis");
        com.sfb.Player enemy = new com.sfb.Player();
        enemy.setTeamName("Klingons");

        Ship cs = kzintiCs();
        cs.setName("KHS Quasar");
        cs.setOwner(kzinti);
        ScenarioLoader.applyDroneSpeed(cs, 180);
        Ship d7 = new Ship();
        d7.init(com.sfb.samples.KlingonShips.getD7());
        d7.setName("IKV Hammer");
        d7.setOwner(enemy);

        game.getShips().add(cs);
        game.getShips().add(d7);

        int gabpv = game.calculateVictoryPoints().rows().stream()
                .filter(r -> r.shipName().equals("KHS Quasar"))
                .findFirst().orElseThrow().gabpv();
        assertEquals("FD2.454's 132, scored", 132, gabpv);
    }

    /**
     * S3.221's exception, which is the whole reason the charge is a function of the year:
     * "Faster drones available as limited or restricted weapons in years prior to their general
     * introduction are purchased as Commander's Options."
     *
     * <p>In Y180 a rack of type-IFs is standard issue and costs the budget nothing. In Y179, with
     * fast speed still Restricted, the ship is charged for medium and the player buys the last
     * half point per drone out of the 20%.
     */
    @Test
    public void speedBoughtAheadOfGeneralAvailabilityComesOutOfTheBudget() {
        CoiLoadout fourFast = new CoiLoadout();
        fourFast.setDroneRackLoadout(0, List.of(
                DroneType.TypeIF, DroneType.TypeIF, DroneType.TypeIF, DroneType.TypeIF));

        assertEquals("standard issue in Y180", 0.0, fourFast.droneUpgradeCost(180), EXACT);
        assertEquals("Restricted in Y179: half a point each over medium",
                2.0, fourFast.droneUpgradeCost(179), EXACT);
        assertEquals("and in Y166, with medium Restricted too, the whole point each",
                4.0, fourFast.droneUpgradeCost(166), EXACT);
    }

    /**
     * FD2.454 lets a player decline the year's speed — "might for tactical reasons have some
     * slower drones on board" — and S2.11 names the cases: "asteroids, webs, bases". The charge
     * has to come back off, or the ship pays for speed it is not carrying.
     */
    @Test
    public void keepingSlowerDronesTakesTheChargeBackOff() {
        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 180);
        assertEquals(16, cs.getDroneSpeedBpv());

        CoiLoadout slowOneRack = new CoiLoadout();
        slowOneRack.setDroneRackLoadout(0, List.of(
                DroneType.TypeI, DroneType.TypeI, DroneType.TypeI, DroneType.TypeI));
        ScenarioLoader.applyCoi(cs, slowOneRack, year(180));

        assertEquals("twelve fast drones left, at a point each", 12, cs.getDroneSpeedBpv());
        assertEquals("and the slow ones cost the budget nothing either",
                0.0, slowOneRack.droneUpgradeCost(180), EXACT);
    }

    /** Setting the charge rather than adding to it, so the step above cannot double-charge. */
    @Test
    public void theChargeIsIdempotent() {
        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 180);
        ScenarioLoader.chargeDroneSpeed(cs, 180);
        ScenarioLoader.chargeDroneSpeed(cs, 180);

        assertEquals(16, cs.getDroneSpeedBpv());
        assertEquals("and the hull BPV is not where it lives", 116, cs.getBpv());
    }

    // ------------------------------------------------------------ the scenario's own cap

    /**
     * A scenario may cap what flies, and a ship must not be fitted past it.
     *
     * <h2>What this is here for</h2>
     * Found in a browser, not in a test. {@code nivram_assault} is Y180 with
     * {@code commanderOptions.maxDroneSpeed: 20}, and two correct-looking things disagreed: the
     * loader read Y180 and fitted speed-32 drones, while the picker honoured the cap and offered
     * nothing above 20. The ship sailed with drones its own scenario forbade, the dialog had no
     * catalogue row to name them with, and every chip read "TypeIF (undefineddmg, spd undefined)".
     *
     * <p>The cap is not a rule of the game but a restriction on what flies, so it narrows the
     * fitting rather than changing what the year makes standard: a capped ship is charged for the
     * speed it actually carries, because {@code chargeDroneSpeed} prices the rack.
     */
    @Test
    public void aScenarioCapNarrowsWhatTheYearWouldHaveFitted() {
        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 180, 20);

        for (Drone d : allDronesOf(cs)) {
            assertSame("Y180 would fit type-IFs; the cap says 20", DroneType.TypeIM,
                    d.getDroneType());
            assertEquals(20, d.getSpeed());
        }
        assertEquals("and it pays for the speed it has, not the speed it could have had",
                8, cs.getDroneSpeedBpv());
    }

    /** No cap is the year's own answer, which is the ordinary case. */
    @Test
    public void noCapLeavesTheYearInCharge() {
        Ship capped = kzintiCs();
        ScenarioLoader.applyDroneSpeed(capped, 180, null);
        Ship plain = kzintiCs();
        ScenarioLoader.applyDroneSpeed(plain, 180);

        assertEquals(16, capped.getDroneSpeedBpv());
        assertEquals(16, plain.getDroneSpeedBpv());
    }

    /**
     * A cap below the frame's own slowest engine cannot invent a drone the chart has no row for.
     * FD2.227's type-VI is speed 12 and there is no speed-8 version of it to fall to, so a cap of
     * 8 leaves it where it is rather than throwing.
     */
    @Test
    public void aCapCannotFitADroneTheChartDoesNotPrice() {
        Ship cs = kzintiCs();
        DroneRack rack = racksOf(cs).get(0);
        rack.setAmmo(new java.util.ArrayList<>(List.of(
                new Drone(DroneType.TypeVI), new Drone(DroneType.TypeVI))));

        ScenarioLoader.applyDroneSpeed(cs, 180, 8);

        assertSame(DroneType.TypeVI, rack.getAmmo().get(0).getDroneType());
        assertEquals(12, rack.getAmmo().get(0).getSpeed());
    }

    /**
     * A cap does not smuggle in the one upgrade the year rules refuse to fit.
     *
     * <p>Moderate speed is not on FD2.22's list of improvements — a type-II is a different drone,
     * with two turns of endurance where the type-I has three — so a cap of 12 means "nothing
     * faster than 12", not "fit speed-12 drones". Narrowing the fitting must not widen it.
     */
    @Test
    public void aCapOfTwelveDoesNotTurnTypeIsIntoTypeIIs() {
        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 180, 12);

        for (Drone d : allDronesOf(cs)) {
            assertSame("a type-II is not an upgrade the year fits", DroneType.TypeI,
                    d.getDroneType());
            assertEquals(96, d.getEndurance());
        }
        assertEquals("and nothing is owed for a speed it did not take", 0, cs.getDroneSpeedBpv());
    }

    /**
     * And the cap never makes a ship SLOWER than it already is: a rack that somehow holds a
     * faster drone keeps it, because this fits drones rather than confiscating them. The COI
     * step is where an over-cap loadout is refused, with the clause.
     */
    @Test
    public void theCapFitsRatherThanConfiscates() {
        Ship cs = kzintiCs();
        DroneRack rack = racksOf(cs).get(0);
        rack.setAmmo(new java.util.ArrayList<>(List.of(new Drone(DroneType.TypeIF))));

        ScenarioLoader.applyDroneSpeed(cs, 180, 20);

        assertSame(DroneType.TypeIF, rack.getAmmo().get(0).getDroneType());
    }

    /**
     * End to end, through the loader a scenario actually goes through: the cap in the spec
     * reaches the rack, which is the wiring the browser found missing.
     */
    @Test
    public void theCapInTheScenarioSpecReachesTheRack() {
        ScenarioSpec spec = year(180);
        spec.commanderOptions.maxDroneSpeed = 20;
        spec.sides = new java.util.ArrayList<>();
        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = "Kzinti";
        side.name = "Kzintis";
        side.ships = new java.util.ArrayList<>();
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.type = "CS";
        setup.shipName = "KHS Quasar";
        setup.startHex = "1016";
        setup.startHeading = "A";
        side.ships.add(setup);
        spec.sides.add(side);

        // The real library, because this test is about the wiring and a sample builder would
        // bypass the loader it is testing. Relative to amarillo-core, as every other
        // library-using test in the suite spells it.
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        java.util.List<Ship> loaded = ScenarioLoader.loadShips(spec).get(0);
        assertEquals("the fixture must actually load a ship", 1, loaded.size());
        Ship cs = loaded.get(0);

        for (Drone d : allDronesOf(cs))
            assertEquals("the scenario caps drones at 20", 20, d.getSpeed());
    }

    // ------------------------------------------------------------ the undated browse

    /**
     * A year of 0 is "nobody has picked a date yet", which the catalogue endpoint passes when the
     * shelf is browsed undated — the guard {@code applyEsgCapacitors} keeps for the same reason.
     */
    @Test
    public void anUndatedShipIsLeftExactlyAsItsDataStatesIt() {
        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 0);

        assertEquals(116, cs.getBpv());
        assertEquals(0, cs.getDroneSpeedBpv());
        for (Drone d : allDronesOf(cs))
            assertSame(DroneType.TypeI, d.getDroneType());
    }

    // ------------------------------------------------------------ FD10.65's dates

    /** FD10.65: "Medium speed drones are Limited in Y165, Restricted in Y166, General in Y167." */
    @Test
    public void mediumSpeedBecomesStandardIssueInY167() {
        assertSame(Propulsion.SPEED_8, Propulsion.generalIn(166, DroneFrame.ONE_SPACE));
        assertSame(Propulsion.SPEED_20, Propulsion.generalIn(167, DroneFrame.ONE_SPACE));
    }

    /** FD10.65: "Fast drones are Limited in Y178, Restricted in Y179, General in Y180." */
    @Test
    public void fastSpeedBecomesStandardIssueInY180() {
        assertSame(Propulsion.SPEED_20, Propulsion.generalIn(179, DroneFrame.ONE_SPACE));
        assertSame(Propulsion.SPEED_32, Propulsion.generalIn(180, DroneFrame.ONE_SPACE));
    }

    /**
     * And moderate speed never becomes standard issue, although FD10.65 says it "became General
     * Availability items in Y120".
     *
     * <p>FD2.22's list of improvements has four entries and moderate is not one of them: the
     * moderate engine is what makes a type-II a type-II, and FD2.1 gives that drone two turns of
     * endurance where the type-I has three. Reading FD10.65's availability ladder as a fitting
     * order billed a Y125 Kzinti CS eight points to swap three turns of legs for two.
     */
    @Test
    public void moderateSpeedIsADroneRatherThanAnUpgrade() {
        assertSame(Propulsion.SPEED_8, Propulsion.generalIn(125, DroneFrame.ONE_SPACE));

        Ship cs = kzintiCs();
        ScenarioLoader.applyDroneSpeed(cs, 125);
        assertEquals("nothing owed in Y125", 0, cs.getDroneSpeedBpv());
        for (Drone d : allDronesOf(cs))
            assertSame("and the type-Is keep their three turns", DroneType.TypeI,
                    d.getDroneType());

        // Still purchasable, out of the option budget, at FD10.52's price.
        CoiLoadout transitional = new CoiLoadout();
        transitional.setDroneRackLoadout(0, List.of(DroneType.TypeII, DroneType.TypeII));
        assertEquals(1.0, transitional.droneUpgradeCost(125), EXACT);
    }

    // ------------------------------------------------------------ the cost chart

    /** FD10.52 prints a dash where an engine is not made for a frame; the baseline is what is. */
    @Test
    public void theBaselineIsTheSlowestEngineTheChartPricesForTheFrame() {
        assertSame(Propulsion.SPEED_8, Propulsion.baselineFor(DroneFrame.ONE_SPACE));
        assertSame(Propulsion.SPEED_8, Propulsion.baselineFor(DroneFrame.TWO_SPACE));
        assertSame(Propulsion.SPEED_8, Propulsion.baselineFor(DroneFrame.TYPE_III_XX));
        assertSame("FD2.227: a type-VI slow is Speed 12, not Speed 8",
                Propulsion.SPEED_12, Propulsion.baselineFor(DroneFrame.HALF_SPACE));
    }

    /**
     * FD2.223 and FD2.224 in points: "Medium speed... costs 1/2 point per drone", "Fast speed...
     * costs one point per drone".
     */
    @Test
    public void theSurchargesAreTheOnesFd222States() {
        assertEquals(0.50, Propulsion.SPEED_20.surchargeOn(DroneFrame.ONE_SPACE), EXACT);
        assertEquals(1.00, Propulsion.SPEED_32.surchargeOn(DroneFrame.ONE_SPACE), EXACT);
    }

    /**
     * FD2.226: "Type-VI speed upgrades cost 1/2 the normal amount (based on type-I upgrade
     * costs). Type-IV drones pay the same speed upgrade cost as a type-I drone."
     *
     * <p>Neither needs a special case, because FD10.52's chart is indexed by the frame and the
     * columns already say it.
     */
    @Test
    public void theTypeIvPaysTypeIRatesAndTheTypeViPaysHalf() {
        assertEquals("a type-IV pays what a type-I pays",
                Propulsion.SPEED_32.surchargeOn(DroneFrame.ONE_SPACE),
                Propulsion.SPEED_32.surchargeOn(DroneFrame.TWO_SPACE), EXACT);
        assertEquals("and a type-VI half of it",
                Propulsion.SPEED_32.surchargeOn(DroneFrame.ONE_SPACE) / 2,
                Propulsion.SPEED_32.surchargeOn(DroneFrame.HALF_SPACE), EXACT);
        assertEquals(0.25, Propulsion.SPEED_20.surchargeOn(DroneFrame.HALF_SPACE), EXACT);
    }

    /**
     * A half-space frame before Y120 has no engine in general service at all, and its slowest is
     * speed-12 regardless — so the baseline answers, rather than a dash.
     */
    @Test
    public void aHalfSpaceFrameNeverFallsBelowItsOwnSlowestEngine() {
        assertSame(Propulsion.SPEED_12, Propulsion.generalIn(50, DroneFrame.HALF_SPACE));
        assertEquals(0.0, DroneType.TypeVI.spec.speedCostIn(50), EXACT);
    }

    /**
     * FD10.54 on the type-III, which is the trap avoided by keying the baseline on the FRAME
     * rather than on the drone's family: "It actually costs 0.5 points to trade a type-I drone
     * for a type-III, but this results in a Speed 8 type-III drone. (Technically, there is no
     * such thing, but it is theoretically possible to build one.) Trading a Speed 8 type-I drone
     * for a Speed 12 type-III costs 1.0 points, not 0.5 points."
     *
     * <p>FD2.227 says a type-III slow IS speed 12, and it would have been easy to read that as
     * making its engine free. It is not: the drone pays the frame surcharge AND the moderate
     * engine, and FD10.54's one point is the two of them together.
     *
     * <p>WHO pays still moves with the year, as it does for every other drone. Before moderate
     * speed is standard issue the whole point is a Commander's Option, which is the case FD10.54
     * is describing. In Y180, speed-12 being well inside what S3.221 calls general availability,
     * the ship carries the engine and the budget is left with the frame.
     */
    @Test
    public void aTypeIiiPaysForItsSpeedTwelveEngineLikeAnyOtherOneSpaceDrone() {
        CoiLoadout one = new CoiLoadout();
        one.setDroneRackLoadout(0, List.of(DroneType.TypeIII));

        assertEquals("FD10.54's 1.0 points, in a year when no speed is standard issue",
                1.0, one.droneUpgradeCost(166), EXACT);
        assertEquals("the frame surcharge alone once the ship covers the engine",
                0.50, one.droneUpgradeCost(180), EXACT);
        assertEquals("which is the half point the ship is carrying",
                0.50, DroneType.TypeIII.spec.speedCostIn(180), EXACT);
    }

    // ------------------------------------------------------------ through the catalogue

    /** A type-I at speed 32 is a type-IF, and the catalogue is asked rather than guessed at. */
    @Test
    public void aDroneUpgradedInPlaceBecomesItsCatalogueSibling() {
        assertSame(DroneType.TypeIF, DroneType.TypeI.atSpeed(Propulsion.SPEED_32));
        assertSame(DroneType.TypeIVM, DroneType.TypeIV.atSpeed(Propulsion.SPEED_20));
        assertSame(DroneType.TypeIIIF, DroneType.TypeIII.atSpeed(Propulsion.SPEED_32));
        assertSame(DroneType.TypeVIM, DroneType.TypeVI.atSpeed(Propulsion.SPEED_20));
    }

    /**
     * And the point of going through the catalogue: the sibling brings its ENDURANCE, which no
     * rule derives from frame, propulsion or payload.
     *
     * <p>A medium-speed standard drone is a type-IM with three turns of fuel (96 impulses), not a
     * type-II with two (64). Refitting a type-II's engine in place would have produced a drone
     * the chart has no row for.
     */
    @Test
    public void theSiblingBringsItsEnduranceRatherThanInheritingTheOldOne() {
        assertEquals(64, DroneType.TypeII.endurance);
        assertSame(DroneType.TypeIM, DroneType.TypeII.atSpeed(Propulsion.SPEED_20));
        assertEquals(96, DroneType.TypeIM.endurance);

        Drone upgraded = new Drone(DroneType.TypeII.atSpeed(Propulsion.SPEED_20));
        assertEquals(96, upgraded.getEndurance());
    }

    /** Where the catalogue has no entry, there is no sibling rather than an invented one. */
    @Test
    public void aSpeedTheFamilyNeverFlewHasNoSibling() {
        assertNull("FD10.52 has no speed-8 half-space engine",
                DroneType.TypeVI.atSpeed(Propulsion.SPEED_8));
        assertNotNull(DroneType.TypeVI.atSpeed(Propulsion.SPEED_32));
    }

    /**
     * A drone built in FD10.4's workshop keeps its payload and swaps only the engine — the
     * catalogue has no entry for an ECM drone, so the basis becomes the sibling and the spec
     * carries the module across.
     */
    @Test
    public void aComposedDroneKeepsItsPayloadThroughTheUpgrade() {
        Ship cs = kzintiCs();
        DroneRack rack = racksOf(cs).get(0);
        DroneLoad ecm = DroneLoad.parse("TypeIV+ECM");
        rack.setAmmo(new java.util.ArrayList<>(List.of(ecm.build(), ecm.build())));

        ScenarioLoader.applyDroneSpeed(cs, 180);

        Drone grown = rack.getAmmo().get(0);
        assertSame("the basis follows the speed", DroneType.TypeIVF, grown.getDroneType());
        assertEquals(32, grown.getSpeed());
        assertEquals("and the module is still aboard",
                List.of(PayloadModule.ECM), grown.getSpec().payload());
        assertEquals("and it still fills the two rack spaces its frame takes",
                2.0, grown.getRackSize(), EXACT);
    }

    /**
     * FD2.22's surcharge is the same number whether the engine arrived by the year's fitting
     * order or out of a player's budget, so the two paths agree on what a drone is worth.
     */
    @Test
    public void theSpecAndTheChartAgreeOnWhatTheUpgradeCost() {
        DroneSpec fast = DroneType.TypeI.spec.withPropulsion(Propulsion.SPEED_32);

        assertEquals(32, fast.speed());
        assertEquals(1.00, fast.speedCostIn(180), EXACT);
        assertEquals("in Y179 the ship covers only the medium step",
                0.50, fast.speedCostIn(179), EXACT);
        assertEquals("and the whole drone is priced by FD10.5's charts",
                DroneType.TypeIF.spec.pointCost(), fast.pointCost(), EXACT);
    }
}
