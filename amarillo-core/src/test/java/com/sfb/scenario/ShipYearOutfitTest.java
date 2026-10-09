package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A hull is loaded as it was built; the YEAR is what brings it up to date.
 *
 * <h2>What this is guarding</h2>
 * Five separate rules move a ship forward to the date it is fielded in, and for a long time only
 * the scenario loader ran all five. The three other places that build a ship — the shelf
 * catalogue, the ship-detail endpoint behind the SSD viewer, and {@link FleetLoader}, which is
 * what the fleet validator prices — each ran the fighter re-seat alone.
 *
 * <p>The owner found it from the fleet builder: a Federation NCD inspected at Y176 showed the
 * type-A drone racks it was BUILT with, where the Y175 refit gives it type-B. The quieter half
 * was the money. {@link ScenarioLoader#applyYearUpgrades} adds a Y175 block's {@code refitCost}
 * to the hull's BPV, and 46 hulls and refits declare one, so a Y176 fleet was priced as though
 * none of them had been refitted — and then the battle handed the player the refitted ship.
 *
 * <p>{@code ScenarioLoader.outfitForYear} is the single seam they all call now. These tests pin
 * the behaviour that made the bug visible, and the one property that makes the seam dangerous:
 * it mutates BPV, so it must run exactly once per ship.
 */
public class ShipYearOutfitTest {

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static Ship ncdAt(int year) {
        ShipSpec spec = ShipLibrary.get("Federation", "NCD");
        assertNotNull("fixture: the Federation NCD should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, spec.faction, year, spec);
        return ship;
    }

    private static long racksOfType(Ship ship, DroneRack.DroneRackType type) {
        return ship.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(w -> (DroneRack) w)
                .filter(r -> r.getRackType() == type)
                .count();
    }

    /** The report, as reported: an NCD at Y176 carries type-B racks, not the type-A it was built with. */
    @Test
    public void theNcdsDroneRacksAreUpgradedByTheY175Refit() {
        Ship early = ncdAt(174);
        assertEquals("built with four type-A racks", 4, racksOfType(early, DroneRack.DroneRackType.TYPE_A));
        assertEquals("and two type-G", 2, racksOfType(early, DroneRack.DroneRackType.TYPE_G));

        Ship late = ncdAt(176);
        assertEquals("the Y175 refit takes the four type-A racks to type-B",
                4, racksOfType(late, DroneRack.DroneRackType.TYPE_B));
        assertEquals("none left at type-A", 0, racksOfType(late, DroneRack.DroneRackType.TYPE_A));
        assertEquals("the type-G racks are not retyped", 2,
                racksOfType(late, DroneRack.DroneRackType.TYPE_G));
    }

    /** And the refit is charged for: the NCD's block declares 4. */
    @Test
    public void theRefitIsAddedToTheHullsBpv() {
        assertEquals("as built", 119, ncdAt(174).getBpv());
        assertEquals("plus the Y175 refit the NCD declares", 123, ncdAt(176).getBpv());
    }

    /**
     * The property that makes the shared seam dangerous.
     * <p>
     * Outfitting mutates the ship rather than returning a new one, so a caller that runs it twice
     * charges the Y175 refit twice over. Nothing in the code does that today; this is here so that
     * a future caller added to the chain fails loudly rather than quietly inflating every price
     * in a Y175+ fleet by a few points.
     */
    @Test
    public void outfittingTwiceWouldChargeTheRefitTwice() {
        ShipSpec spec = ShipLibrary.get("Federation", "NCD");
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, spec.faction, 176, spec);
        int once = ship.getBpv();
        ScenarioLoader.outfitForYear(ship, spec.faction, 176, spec);

        assertEquals("the first call is the one that counts", 123, once);
        assertTrue("outfitForYear is NOT idempotent — call it exactly once per ship, which is"
                + " what this test exists to record", ship.getBpv() > once);
    }

    /** Below the refit year nothing moves, which keeps the tests above honest. */
    @Test
    public void aDateBelowTheRefitChangesNothing() {
        assertEquals(ncdAt(140).getBpv(), ncdAt(174).getBpv());
        assertEquals(racksOfType(ncdAt(140), DroneRack.DroneRackType.TYPE_A),
                racksOfType(ncdAt(174), DroneRack.DroneRackType.TYPE_A));
    }

    /**
     * FleetLoader is the path the fleet validator prices, so it must agree with a hull outfitted
     * directly. This is the half that was costing money rather than just misreporting.
     */
    @Test
    public void theFleetLoaderPricesAHullAsTheYearLeavesIt() {
        FleetSpec spec = new FleetSpec();
        spec.budget = 1000;
        spec.factions.add("Federation");
        FleetSpec.ShipEntry entry = new FleetSpec.ShipEntry();
        entry.faction = "Federation";
        entry.type = "NCD";
        spec.ships.add(entry);

        spec.year = 174;
        assertEquals("as built", 119,
                FleetValidator.costOf(FleetLoader.resolve(spec).ships().get(0)));
        spec.year = 176;
        assertEquals("with the Y175 refit it will actually fight with", 123,
                FleetValidator.costOf(FleetLoader.resolve(spec).ships().get(0)));

        Ship viaLoader = FleetLoader.resolve(spec).ships().get(0);
        assertEquals("and its racks match a directly outfitted hull",
                racksOfType(ncdAt(176), DroneRack.DroneRackType.TYPE_B),
                racksOfType(viaLoader, DroneRack.DroneRackType.TYPE_B));
    }

    /** Every weapon the library can build survives being outfitted at a late date. */
    @Test
    public void everyHullCanBeOutfittedForALateYear() {
        int checked = 0;
        for (ShipSpec spec : ShipLibrary.all()) {
            Ship ship = ShipLibrary.createShip(spec);
            ScenarioLoader.outfitForYear(ship, spec.faction, 183, spec);
            checked++;
            for (Weapon w : ship.getWeapons().fetchAllWeapons())
                assertNotNull(spec.faction + "/" + spec.type + " has a null weapon after outfitting", w);
        }
        assertTrue("there should be hulls to outfit", checked > 250);
    }
}
