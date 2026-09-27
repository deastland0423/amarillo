package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.Weapon;

/**
 * Every Hydran ship builds, with the weapons and fighters its file names.
 * <p>
 * The data guards prove these files PARSE; they do not prove a ship comes out of them. The
 * Hydrans are the first faction to lean on fusion beams, hellbores, gatling phasers and
 * three kinds of Stinger all at once, and a weapon the factory has no case for is dropped in
 * silence — the ship simply sails with fewer guns than its SSD.
 */
public class HydranShipsLoadTest {

    private static final File DIR = new File("../data/factions/hydran");

    @Test
    public void everyHydranShipBuildsWithEveryWeaponItsFileNames() throws Exception {
        List<String> problems = new ArrayList<>();
        int ships = 0;

        for (File f : DIR.listFiles(n -> n.getName().endsWith(".json"))) {
            ShipSpec spec = ShipSpec.fromJson(f);
            assertNotNull(f.getName() + " should parse", spec);
            Ship ship = ShipLibrary.createShip(spec);
            ships++;

            int declared = spec.weapons == null ? 0 : spec.weapons.size();
            int built = ship.getWeapons().fetchAllWeapons().size();
            if (built != declared)
                problems.add(f.getName() + ": " + declared + " weapons in the file, "
                        + built + " on the ship");

            for (Weapon w : ship.getWeapons().fetchAllWeapons())
                if (w.getName() == null || w.getName().isBlank())
                    problems.add(f.getName() + ": a weapon built with no name");
        }

        assertTrue("the Hydran faction should have ships", ships > 5);
        assertTrue("Hydran ships lost weapons between the file and the ship: "
                + String.join(" | ", problems), problems.isEmpty());
    }

    /** The Stingers are the point of a Hydran fleet; they must reach the bays. */
    @Test
    public void theStingersReachTheirBays() throws Exception {
        int fighters = 0;
        for (File f : DIR.listFiles(n -> n.getName().endsWith(".json"))) {
            Ship ship = ShipLibrary.createShip(ShipSpec.fromJson(f));
            for (ShuttleBay bay : ship.getShuttles().getBays())
                for (Shuttle s : bay.getInventory())
                    if (s instanceof Fighter) {
                        fighters++;
                        assertFalse(f.getName() + ": a fighter with no weapons",
                                s.getWeapons().fetchAllWeapons().isEmpty());
                    }
        }
        assertTrue("Hydran ships should carry Stingers", fighters > 10);
    }
}
