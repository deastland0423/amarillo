package com.sfb.weapons;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.scenario.ScenarioLoader;

/**
 * FD3.72: what a type-G rack's three reloads actually contain after the Y175 refit.
 *
 * <p>The owner's statement of it, 2026-11-06: "The last reload of a Type-G rack is always ADDs.
 * So in Y175 (three reloads), we should see two sets that match the rack's magazine plus 8 ADDs
 * to represent the last reload."
 *
 * <p>Eight is not a written-down number — a type-G has four spaces and an anti-drone takes half a
 * space (FD3.70), so a full anti-drone set is eight rounds and follows the rack.
 *
 * <p>The AD6's sheet says the same thing from the other side: "RACKS ALWAYS HAD THREE RELOADS.
 * THIRD RELOAD IS ENTIRELY ADDS."
 */
public class TypeGThirdReloadTest {

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private Ship klingon(String type, int year) {
        ShipSpec spec = ShipLibrary.get("Klingon", type);
        assertNotNull("fixture: " + type + " should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, "Klingon", year, spec);
        return ship;
    }

    private DroneRack typeG(Ship ship) {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().getDroneList()) {
            if (w instanceof DroneRack && ((DroneRack) w).acceptsAntiDrones())
                return (DroneRack) w;
        }
        fail("fixture: " + ship.getName() + " should carry a type-G rack");
        return null;
    }

    /** Before the refit: one mirrored set and one anti-drone set — FD3.72's baseline two. */
    @Test
    public void beforeY175ARackHasOneDroneSetAndOneAntiDroneSet() {
        DroneRack rack = typeG(klingon("AD6", 170));

        assertEquals("one set matching the magazine", 1, rack.droneReloadSets());
        assertEquals("and a full anti-drone set beside it", 8, rack.getAddReloads());
    }

    /**
     * After it: TWO mirrored sets and the anti-drone set, which is the owner's three.
     *
     * <p>This is the assertion BACKLOG.md recorded as failing — "all three ships report two
     * reloads". Two is the count of DRONE sets, and the third reload is the eight ADDs, which no
     * list can hold because an anti-drone is not a {@code Drone}.
     */
    @Test
    public void afterY175ThereAreTwoDroneSetsPlusEightAdds() {
        DroneRack rack = typeG(klingon("AD6", 176));

        assertEquals("two sets that match the rack's magazine", 2, rack.droneReloadSets());
        assertEquals("plus 8 ADDs as the last reload", 8, rack.getAddReloads());
    }

    /** And the mirrored sets really hold drones, not just a raised count. */
    @Test
    public void theMirroredSetsHoldRealDrones() {
        DroneRack rack = typeG(klingon("AD6", 176));

        List<List<com.sfb.objects.Drone>> sets = rack.getReloads();
        assertEquals("both mirrored sets are materialised", 2, sets.size());
        for (List<com.sfb.objects.Drone> set : sets) {
            assertEquals("each one matches the magazine's size",
                    rack.getAmmo().size(), set.size());
        }
    }

    /** The AD5 and AF5 carry the same refit and must agree. */
    @Test
    public void theOtherTwoHullsAgree() {
        for (String type : new String[] {"AD5", "AF5"}) {
            DroneRack rack = typeG(klingon(type, 176));
            assertEquals(type + ": two mirrored sets", 2, rack.droneReloadSets());
            assertEquals(type + ": eight ADDs", 8, rack.getAddReloads());
        }
    }
}
