package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Availability;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ReadyRack;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;
import com.sfb.weapons.DroneRail;

/**
 * Buying the drones a fighter flies, one fighter at a time (FD10.646, J4.23, FD2.45).
 *
 * <h2>What this opens</h2>
 * FD10.646 settles what a fighter is ISSUED with, and the answer is an ordinary drone even on a
 * special rail: those rails "do not form an exception allowing additional limited availability
 * drones to be purchased, and many of the initial drones in the ready racks for these rails may
 * be of general or restricted types". So every special drone a fighter carries is a purchase,
 * and until now there was no way to make it — the rails were already gated by J4.23 and the
 * availability caps already counted the squadron, but nothing a player could submit named a
 * drone for a fighter.
 *
 * <p>The Klingons make the gap sharpest. Their carrier conversions strip every drone rack off
 * the hull (C8 seven racks to C8VK none, D7 three to D7V none), so the squadron is the whole of
 * that ship's drone capacity and the fighters are the only place a special drone can go at all.
 *
 * <h2>The fixture, and why its "unchanged" rails read as fast drones</h2>
 * S2.11 fits the year's drones before a player chooses anything, and the rail's DESIGN follows
 * them ({@code ReadyRack.redesignFrom}) — so in Y180 an untouched rail is designed around a
 * type-I-FAST, not the type-I-slow its frame was drawn with. An assertion here that expects
 * the baseline is asserting that the refit did not happen.
 *
 * A Kzinti carrier in Y180 flies the TADS, which has one of every rail that matters: two
 * standard, two light and two special. Its EW variant carries two pods, and J4.962 takes those
 * rails out of the rack — which is the misalignment this addressing has to survive.
 */
public class FighterLoadoutCoiTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    // ------------------------------------------------------------------ fixtures

    /** One ship bought into a fleet dated {@code year}, outfitted exactly as a battle would. */
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

    private static ScenarioSpec year(int y) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = y;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        return spec;
    }

    private static List<ShuttleSpace> boxesOf(Ship ship) {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            boxes.addAll(bay.getSpaces());
        return boxes;
    }

    /** The first box holding a fighter of {@code catalogueType}. */
    private static ShuttleSpace boxOf(Ship ship, String catalogueType) {
        for (ShuttleSpace box : boxesOf(ship)) {
            Shuttle s = box.getShuttle();
            if (s instanceof com.sfb.objects.shuttles.Fighter f
                    && catalogueType.equals(f.getCatalogType()))
                return box;
        }
        throw new AssertionError("fixture needs a " + catalogueType + " aboard");
    }

    /** A loadout naming one fighter's rails, in {@link ReadyRack#stockedRails} order. */
    private static CoiLoadout railLoad(String fighterName, String... rails) {
        List<DroneLoad> loads = new ArrayList<>();
        for (String r : rails)
            loads.add(r == null ? null : DroneLoad.parse(r));
        CoiLoadout out = new CoiLoadout();
        out.fighterLoadouts.put(fighterName, loads);
        return out;
    }

    private static String notesOf(Ship ship) {
        return String.join(" | ", ship.getSetupNotes());
    }

    private static List<DroneType> designOf(ShuttleSpace box) {
        assertNotNull("fixture: the box should have a ready rack", box.getReadyRack());
        return box.getReadyRack().design();
    }

    // ------------------------------------------------------------------ it arrives

    /**
     * FD10.646's purchase, made: a special rail stocked with the type-III it exists to carry.
     *
     * <p>Both halves have to move together. The RAIL's design drone is what the fighter will
     * fly, and the ready rack's design is what the box will reload it with — J4.8222 makes the
     * rack hold "the same drones that the fighter it is designed to service carries", so a
     * rack still shaped for type-Is would hand the fighter a drone its special rail was bought
     * to replace.
     */
    @Test
    public void aSpecialRailTakesTheTypeIiiTheOwnerBuysForIt() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxOf(ship, "tads");
        String name = box.getShuttle().getName();

        List<DroneRail> rails = ReadyRack.stockedRails(box.getShuttle());
        assertEquals("fixture: a TADS has six rails that stock a rack", 6, rails.size());
        assertEquals("fixture: the last two are the special ones",
                DroneRail.DroneRailType.SPECIAL, rails.get(5).getRailType());

        ScenarioLoader.applyCoi(ship,
                railLoad(name, null, null, null, null, "TypeIII", "TypeIII"), year(180));

        assertEquals("the special rails now fly a type-III (J4.233)",
                DroneType.TypeIII, rails.get(4).getDesignDrone());
        assertEquals(DroneType.TypeIII, rails.get(5).getDesignDrone());
        assertEquals("and the standard rails are untouched, still the year's drone (S2.11)",
                DroneType.TypeIF, rails.get(0).getDesignDrone());

        assertEquals("the rack is reshaped around them (J4.8222), the rest still flying"
                        + " the year's drones (S2.11)",
                Arrays.asList(DroneType.TypeIF, DroneType.TypeIF, DroneType.TypeVIF,
                        DroneType.TypeVIF, DroneType.TypeIII, DroneType.TypeIII),
                designOf(box));
        List<Drone> held = box.getReadyRack().contents();
        assertEquals("and it is stocked full (J4.886)", 6, held.size());
        assertEquals("with the drone that was bought, not the frame's baseline",
                DroneType.TypeIII, held.get(5).getDroneType());
    }

    /**
     * The composition survives the trip, which the catalogue type alone cannot carry.
     *
     * <p>{@code ReadyRack} stocks itself with {@code new Drone(type)} — a bare type-I — so a
     * rebuild on its own would quietly throw away the ECM module the player paid for. The rack
     * is re-stocked from the loadout afterwards for exactly that reason.
     */
    @Test
    public void theRackHoldsTheDroneThatWasBoughtModulesAndAll() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxOf(ship, "tads");
        String name = box.getShuttle().getName();

        ScenarioLoader.applyCoi(ship, railLoad(name, "TypeI+ECM"), year(180));

        Drone first = box.getReadyRack().contents().get(0);
        assertTrue("FD9.35: an ECM drone, on a rail allowed to carry one",
                first.getSpec().allModules().contains(com.sfb.objects.PayloadModule.ECM));
        assertEquals("still a type-I for anything asking what KIND it is",
                DroneType.TypeI, first.getDroneType());
        assertEquals("the rails the loadout did not name keep their stock drone,"
                        + " which in Y180 is the fast one (S2.11)",
                DroneType.TypeIF, box.getReadyRack().contents().get(1).getDroneType());
    }

    /** A rail the loadout leaves blank is one the player did not touch, not one they got wrong. */
    @Test
    public void aBlankRailIsLeftAsIssued() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxOf(ship, "tads");
        List<DroneRail> rails = ReadyRack.stockedRails(box.getShuttle());

        ScenarioLoader.applyCoi(ship,
                railLoad(box.getShuttle().getName(), null, null, null, null, "TypeIII"),
                year(180));

        assertEquals("the named rail changed", DroneType.TypeIII, rails.get(4).getDesignDrone());
        assertEquals("the one beside it did not",
                DroneType.TypeIF, rails.get(5).getDesignDrone());
        assertEquals("nor did the light rails (FD10.643's dogfight drones)",
                DroneType.TypeVIF, rails.get(2).getDesignDrone());
    }

    /** A fighter nobody named flies what the hull issued it (FD2.31). */
    @Test
    public void aFighterNobodyNamedIsUntouched() {
        Ship ship = bought("Kzinti", "CVS", 180);
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleSpace box : boxesOf(ship))
            if (box.getReadyRack() != null && !box.getReadyRack().carriesPlasma())
                boxes.add(box);
        assertTrue("fixture: the CVS has a squadron", boxes.size() >= 2);

        ShuttleSpace named = boxes.get(0);
        ShuttleSpace other = boxes.get(1);
        ScenarioLoader.applyCoi(ship,
                railLoad(named.getShuttle().getName(), null, null, null, null, "TypeIII"),
                year(180));

        for (DroneType slot : designOf(other))
            assertFalse("only the fighter that was named changed",
                    slot == DroneType.TypeIII);
    }

    // ------------------------------------------------------------------ and it is gated

    /**
     * J4.23, enforced where a player can reach it: a STANDARD rail refuses the type-III frame,
     * and the whole fighter is left as it was.
     *
     * <p>All-or-nothing per fighter, as the rack block is per rack. A half-applied squadron is
     * the state hardest for a player to see — told that one drone was dropped, they would have
     * no way of knowing which of twelve fighters ended up carrying what.
     */
    @Test
    public void aStandardRailRefusesTheTypeIiiAndTheWholeFighterIsLeftAlone() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxOf(ship, "tads");
        List<DroneRail> rails = ReadyRack.stockedRails(box.getShuttle());

        // The LEGAL drone first and the refused one after it, which is the order that tells
        // the two designs apart: an applier working rail by rail would have fitted the ECM
        // drone and then stopped, leaving the fighter half-way between two loadouts.
        ScenarioLoader.applyCoi(ship,
                railLoad(box.getShuttle().getName(), "TypeI+ECM", "TypeIII"),
                year(180));

        assertEquals("FD9.35: the standard rail would not take the type-III frame",
                DroneType.TypeIF, rails.get(1).getDesignDrone());
        assertFalse("and the legal drone beside it is refused with it, not fitted",
                box.getReadyRack().contents().get(0).getSpec().allModules()
                        .contains(com.sfb.objects.PayloadModule.ECM));
        assertEquals("nothing was charged for a loadout that was not applied",
                0.0, ship.getCoiSpend(), 0.0001);
        assertTrue("the player is told which rule refused it: " + notesOf(ship),
                notesOf(ship).contains("J4.23"));
    }

    /** A loadout longer than the fighter has rails is a misaddressed one, not a partial one. */
    @Test
    public void aLoadoutNamingMoreRailsThanTheFighterHasIsRefused() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxOf(ship, "tads_e");
        List<DroneRail> rails = ReadyRack.stockedRails(box.getShuttle());
        assertEquals("fixture: J4.962 takes the two podded rails out of the rack",
                4, rails.size());

        ScenarioLoader.applyCoi(ship,
                railLoad(box.getShuttle().getName(),
                        null, null, null, null, "TypeIII", "TypeIII"),
                year(180));

        for (DroneRail rail : rails)
            assertFalse("nothing was applied off the end of the list",
                    rail.getDesignDrone() == DroneType.TypeIII);
        assertTrue("and the player is told: " + notesOf(ship),
                notesOf(ship).contains("drone rails and the loadout names"));
    }

    /** The scenario's year still decides what exists (FD10.631). */
    @Test
    public void aDroneTheYearHasNotSeenIsRefused() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxOf(ship, "tads");
        List<DroneRail> rails = ReadyRack.stockedRails(box.getShuttle());

        ScenarioLoader.applyCoi(ship,
                railLoad(box.getShuttle().getName(), null, null, null, null, "TypeIIIF"),
                year(168));

        assertEquals("a fast drone in Y168 is not a purchase, it is an anachronism",
                DroneType.TypeIF, rails.get(4).getDesignDrone());
        assertTrue(notesOf(ship), notesOf(ship).contains("not available in year 168"));
    }

    // ------------------------------------------------------------------ and somebody pays

    /**
     * FD2.45: the fighter's rails are the single loading, and its ready rack is free.
     *
     * <blockquote>"The cost of improved drones (FD2.22) is paid only for a single loading of
     * each launch rack <b>and/or fighter</b>; the reloads (and drones stored on a carrier for
     * use by its fighters) are presumed to be of the same type without additional
     * cost."</blockquote>
     *
     * <p>Not a simplification: {@code ShuttleSpace.armOccupantFully} arms a fighter by taking
     * the drones OUT of its rack (J4.8224), so the rails and the rack are two states of one
     * loading. Charging both would double every carrier's bill.
     */
    @Test
    public void theBillIsOneLoadingPerFighterAndTheReloadIsFree() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxOf(ship, "tads");

        CoiLoadout loadout =
                railLoad(box.getShuttle().getName(), null, null, null, null, "TypeIII", "TypeIII");
        double expected = CoiLoadout.rackUpgradeCost(null,
                Arrays.asList(DroneLoad.parse("TypeIII"), DroneLoad.parse("TypeIII")), 180);
        assertTrue("fixture: a type-III costs something over a type-I-slow", expected > 0);

        assertEquals("two rails, charged once each",
                expected, loadout.fighterUpgradeCost(180), 0.0001);

        ScenarioLoader.applyCoi(ship, loadout, year(180));
        assertEquals("and that is what the ship is scored for (S2.20 B)",
                expected, ship.getCoiSpend(), 0.0001);
        assertEquals("six drones are in the rack and two of them were paid for",
                6, box.getReadyRack().contents().size());
    }

    /**
     * And the budget check sees it, which is a separate claim from the bill being right.
     *
     * <p>{@code CoiLoadout.totalCost} is what the COI endpoint refuses an over-budget
     * submission on. A charge the applier levies but the validator cannot see would let a
     * player submit a squadron they cannot afford and have it quietly dropped afterwards —
     * the asymmetry {@code FleetValidator} exists to avoid.
     */
    @Test
    public void theFighterLoadoutIsPartOfWhatTheBudgetCheckSees() {
        CoiLoadout loadout = railLoad("whoever", null, null, null, null, "TypeIII");
        assertTrue("fixture: a type-III is a purchase", loadout.fighterUpgradeCost(180) > 0);
        assertEquals("and the total a budget is measured against includes it",
                loadout.fighterUpgradeCost(180), loadout.totalCost(180), 0.0001);
    }

    /** A rail left as issued was paid for with the hull (FD2.31) and is not charged again. */
    @Test
    public void anUntouchedRailCostsNothing() {
        CoiLoadout loadout = railLoad("whoever", null, null, null, null, null, null);
        assertEquals(0.0, loadout.fighterUpgradeCost(180), 0.0001);
    }

    /** Over budget is skipped and said out loud, as every other Commander's Option is. */
    @Test
    public void aLoadoutBeyondTheBudgetIsSkipped() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxOf(ship, "tads");
        List<DroneRail> rails = ReadyRack.stockedRails(box.getShuttle());

        ScenarioSpec spec = year(180);
        spec.commanderOptions.budgetPercent = 0;
        ScenarioLoader.applyCoi(ship,
                railLoad(box.getShuttle().getName(), null, null, null, null, "TypeIII"), spec);

        assertEquals("nothing was bought", DroneType.TypeIF, rails.get(4).getDesignDrone());
        assertTrue(notesOf(ship), notesOf(ship).contains("of the budget is left"));
    }

    // ------------------------------------------------------------------ and the caps still bite

    /**
     * The purchase lands inside FD10.632's cap, which is the point of making it a purchase.
     *
     * <p>FD10.646 is explicit that special rails "do not form an exception allowing additional
     * limited availability drones to be purchased". A type-III is Limited in every year
     * (FD10.631), so a squadron that fills every special rail with one is over the 20% a Kzinti
     * hull is allowed — and the fleet validator has to see it.
     */
    @Test
    public void typeIiisBoughtForTheWholeSquadronBreachTheLimitedCap() {
        Ship ship = bought("Kzinti", "CVS", 180);
        assertTrue("fixture: as issued, the carrier is legal (FD10.646)",
                DroneAvailabilityLimit.violations(ship, 180).isEmpty());

        CoiLoadout loadout = new CoiLoadout();
        for (ShuttleSpace box : boxesOf(ship)) {
            Shuttle s = box.getShuttle();
            if (!(s instanceof com.sfb.objects.shuttles.Fighter))
                continue;
            List<DroneRail> rails = ReadyRack.stockedRails(s);
            List<DroneLoad> load = new ArrayList<>();
            for (DroneRail rail : rails)
                load.add(rail.getRailType() == DroneRail.DroneRailType.SPECIAL
                        ? DroneLoad.parse("TypeIII") : null);
            loadout.fighterLoadouts.put(s.getName(), load);
        }
        // The budget is not what this test is about; give it room so the caps are the only gate.
        ScenarioSpec spec = year(180);
        spec.commanderOptions.budgetPercent = 100;
        ScenarioLoader.applyCoi(ship, loadout, spec);

        assertTrue("twenty-two spaces of Limited on a hull allowed a fifth of its basis",
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.LIMITED, 180) > 0);
        assertFalse("and FD10.632 refuses it: "
                        + DroneAvailabilityLimit.violations(ship, 180),
                DroneAvailabilityLimit.violations(ship, 180).isEmpty());
    }

    /** A box with no rack has no loading to give, so naming it does nothing and says nothing. */
    @Test
    public void aFighterThatIsNotThereIsSimplyNotFound() {
        Ship ship = bought("Kzinti", "CVS", 180);
        String before = notesOf(ship);
        ScenarioLoader.applyCoi(ship, railLoad("No Such Fighter", "TypeIII"), year(180));
        assertEquals("a name nobody answers to changes nothing", before, notesOf(ship));
        assertEquals(0.0, ship.getCoiSpend(), 0.0001);
    }

    /** The helper both halves address by: podded rails are not in it (J4.962). */
    @Test
    public void stockedRailsLeavesOutWhatNoRackStocks() {
        Ship ship = bought("Kzinti", "CVS", 180);
        for (ShuttleSpace box : boxesOf(ship)) {
            Shuttle s = box.getShuttle();
            if (!(s instanceof com.sfb.objects.shuttles.Fighter))
                continue;
            ReadyRack rack = box.getReadyRack();
            if (rack == null || rack.carriesPlasma())
                continue;
            assertEquals(s.getName() + ": a slot per stocked rail",
                    ReadyRack.stockedRails(s).size(), rack.design().size());
        }
        assertNull("and nothing a rack is stocked for comes from a null occupant",
                ReadyRack.forFighter(null));
        assertTrue(ReadyRack.stockedRails(null).isEmpty());
    }
}
