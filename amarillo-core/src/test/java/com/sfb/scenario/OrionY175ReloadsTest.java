package com.sfb.scenario;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.OptionCatalogEntry;
import com.sfb.objects.OptionMount;
import com.sfb.objects.OptionMountCatalog;
import com.sfb.objects.OptionMountLoadout;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaRack;
import com.sfb.weapons.Weapon;

/**
 * Y175: an Orion drone rack gains one reload set, and a plasma rack gains its second.
 *
 * <h2>One rule wearing two sets of words</h2>
 * The BR's sheet says racks in its option mounts "GAIN ONE ADDITIONAL RELOAD"; the CR's says
 * they "ARE DOUBLED". Owner, 2026-10-20: those are the same rule. When the CR's sheet was
 * written the Basic Set held only type-A racks, which carry a single reload set, so adding one
 * WAS doubling. <b>The operation is +1.</b> Reading the CR literally would hand a type-C rack
 * four sets where it is owed three, which is why this is a test and not a comment.
 *
 * <h2>Two doors, because the racks are born at different times</h2>
 * A hull-mounted rack exists when {@code outfitForYear} runs, so {@code applyYearUpgrades}
 * reaches it. A rack in an OPTION MOUNT does not exist yet — mounts are filled later, in
 * {@code applyCoi} — so {@code OptionMountLoadout.equip} applies the same rules at the moment
 * it builds one. A test through only the first door would pass while every Orion who actually
 * bought a rack went without.
 */
public class OrionY175ReloadsTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static Ship outfitted(String faction, String type, int year) {
        ShipSpec spec = ShipLibrary.get(faction, type);
        assertNotNull("fixture: " + faction + " " + type + " should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, faction, year, spec);
        return ship;
    }

    private static DroneRack firstRack(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                return rack;
        return null;
    }

    /** What a rack of this type carries before anybody refits it — so the +1 is never hardcoded. */
    private static int baseSetsOf(DroneRack.DroneRackType type) {
        DroneRack fresh = new DroneRack();
        fresh.upgradeRackType(type);
        return fresh.droneReloadSets();
    }

    // ---------------------------------------------------------------- hull-mounted racks

    /**
     * THE TRAP THIS RULE HAD TO DODGE. The Orion CA is the one Orion hull with a
     * {@code y175Upgrades} block, and that block NAMES both racks for a TYPE_C upgrade. Every
     * other faction branch skips a named rack, so that a hull's declared type is not overwritten
     * by a default — and skipping here would have silently cost the CA the reload set this rule
     * gives it, the same shape of bug that once cost the Federation NCD two sets.
     */
    @Test
    public void theCaGetsItsReloadSetEvenThoughItsBlockNamesBothRacks() {
        Ship ca = outfitted("Orion", "CA", 175);
        DroneRack rack = firstRack(ca);

        assertNotNull("fixture: the CA carries drone racks", rack);
        assertEquals("fixture: its block upgrades them to type-C",
                DroneRack.DroneRackType.TYPE_C, rack.getRackType());
        assertEquals("a type-C's own sets, plus the one Y175 grants",
                baseSetsOf(DroneRack.DroneRackType.TYPE_C) + 1, rack.droneReloadSets());
    }

    /** And the year is the gate: in Y174 the same hull has the plain type-A rack it was built with. */
    @Test
    public void beforeY175TheSameHullGetsNothing() {
        Ship ca = outfitted("Orion", "CA", 174);
        DroneRack rack = firstRack(ca);

        assertEquals("fixture: no type upgrade yet",
                DroneRack.DroneRackType.TYPE_A, rack.getRackType());
        assertEquals(baseSetsOf(DroneRack.DroneRackType.TYPE_A), rack.droneReloadSets());
    }

    /**
     * The bonus is Orion's, not everyone's. A Klingon hull's racks upgrade to type-B in Y175 and
     * carry exactly what a type-B carries — no extra set.
     */
    @Test
    public void aKlingonRackDoesNotGetTheOrionReload() {
        Ship d7 = outfitted("Klingon", "D7", 175);
        DroneRack rack = firstRack(d7);

        assertNotNull("fixture: the D7 carries a drone rack", rack);
        assertEquals(baseSetsOf(rack.getRackType()), rack.droneReloadSets());
    }

    // ---------------------------------------------------------------- racks in option mounts

    /**
     * THE CASE THE FIRST DOOR CANNOT REACH. A rack bought into an option mount is built after
     * every Y175 pass has run, so the rule is reapplied where the rack is born. This is the
     * reading the BR's and CR's sheets actually print — they speak of racks "PLACED IN THIS
     * SHIP'S OPTION MOUNTS".
     */
    @Test
    public void aDroneRackBoughtIntoAnOptionMountStillGainsItsSet() {
        Ship br = outfitted("Orion", "BR", 175);
        OptionMountCatalog catalog = OptionMountCatalog.loadDefault();

        Weapon mounted = OptionMountLoadout.equip(br, catalog, "A", "Drone Rack B", 175);

        assertTrue(mounted instanceof DroneRack);
        assertEquals("a type-B's own sets, plus the one Y175 grants",
                baseSetsOf(DroneRack.DroneRackType.TYPE_B) + 1,
                ((DroneRack) mounted).droneReloadSets());
    }

    /** Same mount, same rack, a year earlier: nothing added. */
    @Test
    public void anOptionMountRackBoughtBeforeY175GainsNothing() {
        Ship br = outfitted("Orion", "BR", 174);
        OptionMountCatalog catalog = OptionMountCatalog.loadDefault();

        Weapon mounted = OptionMountLoadout.equip(br, catalog, "A", "Drone Rack B", 174);

        assertEquals(baseSetsOf(DroneRack.DroneRackType.TYPE_B),
                ((DroneRack) mounted).droneReloadSets());
    }

    /**
     * FP10.312 is NOT an Orion rule — "each plasma rack has two sets of reloads" on any hull of
     * any empire — but a plasma rack in an option mount was missing it for the same timing
     * reason, so the door applies it too.
     *
     * <p>Built from a hand-made catalog row rather than the real one: the annex's "Plasma-D Rack"
     * row carries no {@code weaponSpec} yet, so it cannot be equipped. That is a data gap, not a
     * rules one, and this test is what will catch the day it is filled in wrongly.
     */
    @Test
    public void aPlasmaRackInAnOptionMountGetsItsSecondSet() {
        Ship br = outfitted("Orion", "BR", 175);
        OptionMount mount = br.getOptionMounts().get(0);

        Weapon mounted = OptionMountLoadout.equip(br, mount, plasmaRackEntry(), 175);

        assertTrue(mounted instanceof PlasmaRack);
        assertEquals("FP10.312: one set becomes two, and never three",
                PlasmaRack.Y175_RELOAD_SETS, ((PlasmaRack) mounted).getReloadSets());
    }

    /** And before Y175 it holds the single set FP10.312 starts it with. */
    @Test
    public void aPlasmaRackInAnOptionMountKeepsOneSetBeforeY175() {
        Ship br = outfitted("Orion", "BR", 174);
        OptionMount mount = br.getOptionMounts().get(0);

        Weapon mounted = OptionMountLoadout.equip(br, mount, plasmaRackEntry(), 174);

        assertEquals(PlasmaRack.BASE_RELOAD_SETS, ((PlasmaRack) mounted).getReloadSets());
    }

    private static OptionCatalogEntry plasmaRackEntry() {
        OptionCatalogEntry entry = new OptionCatalogEntry();
        entry.name = "Plasma-D Rack";
        entry.cost = 3;
        entry.weaponSpec = new ShipSpec.WeaponSpec();
        entry.weaponSpec.type = "PlasmaRack";
        return entry;
    }
}
