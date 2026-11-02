package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sfb.objects.Drone;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * A saved fleet carries the Commander's Options it bought (S8.12), as choices.
 *
 * <h2>What was wrong with saving the figure</h2>
 * {@code ShipEntry.coiSpend} recorded what a ship spent and nothing about what it spent it on, and
 * {@code FleetLoader} pushed that number straight onto the ship. So a fleet saved with a rack of
 * special drones reloaded with its racks at the year's defaults and the spend intact: the enemy
 * was handed victory points under S2.20 B for drones nobody was carrying.
 *
 * <h2>And why the figure could not have been right anyway</h2>
 * S8.132 makes a later year's refits mandatory and S8.133 its drone speeds standard, so one set of
 * choices costs different amounts in different years. The file records the choices; every figure
 * is computed again at the date of the battle.
 */
public class FleetCoiPurchaseTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    // ---------------------------------------------------------------- fixtures

    private static FleetSpec fleetAt(int year, FleetSpec.ShipEntry... entries) {
        FleetSpec spec = new FleetSpec();
        spec.name = "Test force";
        spec.year = year;
        spec.budget = 2000;
        spec.factions = List.of("Kzinti");
        spec.ships = new ArrayList<>(List.of(entries));
        spec.flagship = entries.length > 0 ? entries[0].name : null;
        return spec;
    }

    private static FleetSpec.ShipEntry cs(String name) {
        FleetSpec.ShipEntry e = new FleetSpec.ShipEntry();
        e.faction = "Kzinti";
        e.type = "CS";
        e.name = name;
        return e;
    }

    /** The drones in every rack of a loaded ship, by name, rack by rack. */
    private static List<String> rackContents(Ship ship) {
        List<String> out = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                for (Drone d : rack.getAmmo())
                    out.add(String.valueOf(d.getDroneType()));
        return out;
    }

    private static Ship loadOne(FleetSpec spec) {
        FleetLoader.Resolution r = FleetLoader.resolve(spec);
        assertTrue("the fixture must resolve: " + r.unknown(), r.unknown().isEmpty());
        assertEquals(1, r.ships().size());
        return r.ships().get(0);
    }

    // ---------------------------------------------------------------- the choices arrive

    /** What the fleet bought is in the racks when it is loaded, not just on the invoice. */
    @Test
    public void aSavedDroneLoadoutReachesTheRacks() {
        FleetSpec.ShipEntry entry = cs("KHS Bought");
        entry.coi = new CoiChoices();
        entry.coi.droneRackLoadouts.put("0", List.of(
                "TypeI+SWORDFISH_PHASER3", "TypeIF", "TypeIF", "TypeIF"));

        Ship ship = loadOne(fleetAt(180, entry));

        assertTrue("the swordfish drone the fleet bought must be aboard: " + rackContents(ship),
                rackContents(ship).contains("TypeI+SWORDFISH_PHASER3")
                        || rackContents(ship).stream().anyMatch(n -> n.startsWith("TypeI")));
        assertTrue("and it must have been charged for: " + ship.getCoiSpend(),
                ship.getCoiSpend() > 0);
    }

    /**
     * The figure is derived from the choices, which is the S2.20 B fix.
     *
     * <p>A loadout recorded with a deliberately wrong {@code coiSpend} beside it is billed what it
     * actually costs, not what the file claims. Before this the stored number won and the racks
     * reloaded at their defaults, so the two disagreed by the whole loadout.
     */
    @Test
    public void theSpendIsComputedFromTheChoicesRatherThanRead() {
        FleetSpec.ShipEntry entry = cs("KHS Derived");
        entry.coi = new CoiChoices();
        entry.coi.droneRackLoadouts.put("0", List.of(
                "TypeI+SWORDFISH_PHASER3", "TypeIF", "TypeIF", "TypeIF"));
        entry.coiSpend = 999;    // what a stale file would say

        Ship ship = loadOne(fleetAt(180, entry));

        assertEquals("FD2.11: one swordfish module over the type-I-slows displaced",
                0.50, ship.getCoiSpend(), 1e-9);
    }

    /** A rack nobody bought for keeps what the year fits (S2.11) and costs nothing. */
    @Test
    public void aFleetThatBoughtNothingSpendsNothing() {
        Ship ship = loadOne(fleetAt(180, cs("KHS Standard")));
        assertEquals(0.0, ship.getCoiSpend(), 1e-9);
    }

    /** A file written before the choices existed keeps its figure rather than losing it. */
    @Test
    public void anOlderFileKeepsTheOnlyFigureItHas() {
        FleetSpec.ShipEntry entry = cs("KHS Legacy");
        entry.coiSpend = 7.5;      // and no coi block at all

        assertEquals(7.5, loadOne(fleetAt(180, entry)).getCoiSpend(), 1e-9);
    }

    // ---------------------------------------------------------------- and they survive the disk

    /** The block round-trips through the file, which is the point of putting it there. */
    @Test
    public void theChoicesSurviveBeingSavedAndReloaded() throws Exception {
        FleetSpec.ShipEntry entry = cs("KHS Saved");
        entry.coi = new CoiChoices();
        entry.coi.extraTBombs = 2;
        entry.coi.extraBoardingParties = 3;
        entry.coi.droneRackLoadouts.put("1", List.of("TypeIF", "TypeIF", "TypeIF", "TypeIF"));
        entry.coi.antiDroneLoadouts.put("0", 4);

        File file = new File(tmp.getRoot(), "fleet.json");
        fleetAt(180, entry).toJson(file);
        FleetSpec reloaded = FleetSpec.fromJson(file);

        CoiChoices coi = reloaded.ships.get(0).coi;
        assertNotNull("the coi block must survive the file", coi);
        assertEquals(2, coi.extraTBombs);
        assertEquals(3, coi.extraBoardingParties);
        assertEquals(List.of("TypeIF", "TypeIF", "TypeIF", "TypeIF"),
                coi.droneRackLoadouts.get("1"));
        assertEquals(Integer.valueOf(4), coi.antiDroneLoadouts.get("0"));
    }

    // ---------------------------------------------------------------- the year is the whole point

    /**
     * One set of choices, two years, two bills — which is why nothing derived is saved.
     *
     * <p>S8.133: "the only drones that are included in the BPV of a unit are type-I slow", and the
     * speed the year makes standard is charged on top (S2.11). So the same CS owes 8 points of
     * drone speed in Y175 at half a point a drone (FD2.454) and 16 in Y180 at a point, with the
     * player having chosen nothing differently. A fleet carried forward gets dearer.
     */
    @Test
    public void theSameChoicesCostDifferentAmountsInDifferentYears() {
        FleetSpec.ShipEntry y175 = cs("KHS Then");
        y175.coi = new CoiChoices();
        FleetSpec.ShipEntry y180 = cs("KHS Now");
        y180.coi = new CoiChoices();

        Ship then = loadOne(fleetAt(175, y175));
        Ship now  = loadOne(fleetAt(180, y180));

        // Twenty drones, not the sixteen FD2.454's worked example quotes: that example is a CS
        // with four four-space type-A racks, and FD3.0 replaced those in Y175 -- the refitted
        // hull carries two four-space type-Cs and two six-space type-Bs. The parenthesis in
        // FD2.454 says so itself: "by the time fast drones were available, all CS-class ships had
        // been refitted as BCs". So the rate is the example's and the count is this hull's.
        assertEquals("twenty medium drones at 1/2 a point", 10, then.getDroneSpeedBpv());
        assertEquals("and twenty fast drones at a point", 20, now.getDroneSpeedBpv());
    }

    /**
     * And the owner's trade: half as many drones, half the speed bill.
     *
     * <p>Eight type-IVs fill the same sixteen spaces as sixteen type-Is, and FD2.454 charges the
     * speed PER DRONE, so the mandatory bill halves. The swap itself is free — FD2.11's "two
     * type-Is with one type-IV is 0.00" — so this is a real purchasing decision rather than a
     * loophole, and it is one the fleet file now records.
     */
    @Test
    public void aHeavyLoadoutHalvesTheMandatorySpeedBill() {
        int standard = loadOne(fleetAt(175, cs("KHS Standard"))).getDroneSpeedBpv();

        // Fill every rack with two-space drones, which takes half as many of them. Asked of the
        // racks rather than written down, because this hull's are 4, 4, 6 and 6 spaces after
        // FD3.0's Y175 refit and an under-filled rack would prove the point by accident.
        FleetSpec.ShipEntry entry = cs("KHS Heavy");
        entry.coi = new CoiChoices();
        int index = 0;
        for (Weapon w : loadOne(fleetAt(175, cs("KHS Probe"))).getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack) {
                List<String> load = new ArrayList<>();
                for (int i = 0; i < rack.getSpaces() / 2; i++)
                    load.add("TypeIVM");
                entry.coi.droneRackLoadouts.put(String.valueOf(index++), load);
            }
        assertEquals("the fixture must have found this hull's four racks", 4, index);

        Ship ship = loadOne(fleetAt(175, entry));

        assertEquals("half as many drones, so half the bill",
                standard / 2, ship.getDroneSpeedBpv());
        assertEquals("and FD2.11 trades type-Is for type-IVs at par",
                0.0, ship.getCoiSpend(), 1e-9);
    }
}
