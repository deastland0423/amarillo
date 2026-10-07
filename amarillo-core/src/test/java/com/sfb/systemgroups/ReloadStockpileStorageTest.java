package com.sfb.systemgroups;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Where the reload drones LIVE: one list on the ship, not a set behind each rack.
 *
 * <h2>The rule</h2>
 * <b>FD2.422</b>: "A unit's total stockpile (FD2.43) is <b>not directly associated with any
 * particular rack</b> and can be loaded onto any rack on the ship." {@link ReloadStockpileTest}
 * covers what the stockpile DOES; this covers the storage having moved, which is a separate claim
 * and was a separate slice.
 *
 * <h2>Why moving it mattered, beyond tidiness</h2>
 * While the drones sat in the racks, the DTO reported a pool PER RACK. Each rack's pool was its own
 * drones, so that was honest — until the first slice made every rack draw on the whole ship. Then a
 * two-rack ship offered the same stockpile twice, and a player could spend it twice: the server's
 * {@code take()} is authoritative, so the first rack got its drones and the second silently came up
 * short. Two places holding one pile is the bug; one list cannot be double-spent.
 */
public class ReloadStockpileStorageTest {

    private Ship ship;

    @Before
    public void setUp() {
        ship = new Ship();
        ship.init(KlingonShips.getD7());
        ship.setName("IKS Testbed");
    }

    private List<DroneRack> racks() {
        List<DroneRack> out = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                out.add(rack);
        return out;
    }

    private int dronesInRackSets() {
        int n = 0;
        for (DroneRack rack : racks())
            for (List<Drone> set : rack.getReloads())
                n += set.size();
        return n;
    }

    /**
     * The move itself. The racks are where a loadout is AUTHORED — FD2.45 mirrors the rack's load
     * into its reload set — so the drones start there; once the stockpile is touched they are the
     * ship's, and no rack holds any.
     */
    @Test
    public void theDronesEndUpOnTheShipAndNotInTheRacks() {
        int authored = dronesInRackSets();
        assertTrue("fixture: a D7's racks are authored with reloads", authored > 0);

        int held = ship.reloadStockpile().held().size();

        assertEquals("every authored drone is now the ship's", authored, held);
        assertEquals("and none is left behind a rack", 0, dronesInRackSets());
    }

    /**
     * One pile, not one per caller. The accessor hands back the same stockpile every time, because it
     * is storage now rather than a view: a fresh object per call would mean a drone taken through one
     * was still there through the next, which is the double-spend the move exists to prevent.
     */
    @Test
    public void everyCallerGetsTheSameStockpile() {
        ReloadStockpile first = ship.reloadStockpile();
        int before = first.held().size();
        DroneType type = first.held().get(0).getDroneType();

        assertEquals(1, first.take(type, 1).size());

        assertSame("the ship has one stockpile", first, ship.reloadStockpile());
        assertEquals("and a second caller sees the drone gone",
                before - 1, ship.reloadStockpile().held().size());
    }

    /**
     * Capacity is a rule about RACKS — FD2.43: "drones equal to the number of spaces held by all of
     * their racks" — so moving the drones must not change it. The empty set lists left behind are
     * what the capacity sum counts: a set is the rack's statement that it HAS a set.
     */
    @Test
    public void movingTheDronesDoesNotChangeTheCapacity() {
        double before = ship.reloadStockpile().capacitySpaces();
        assertTrue("fixture: the hull stocks something", before > 0);

        ship.reloadStockpile().held();      // forces the move
        assertEquals("fixture: the move really happened", 0, dronesInRackSets());

        assertEquals("capacity still reads off the racks",
                before, ship.reloadStockpile().capacitySpaces(), 0.001);
    }

    /**
     * Authoring can happen AFTER the first draw, and must not be stranded.
     * <p>
     * The Y175 refit adds a type-G a third set of reloads (FD3.72) and the COI edits a loadout, both
     * through the rack. If the move were a one-off migration those drones would sit in a set nobody
     * reads again. It is not: the stockpile draws in anything new whenever it is next touched.
     */
    @Test
    public void aSetAuthoredAfterTheFirstDrawStillArrives() {
        ReloadStockpile pile = ship.reloadStockpile();
        int held = pile.held().size();                   // the move happens here
        assertEquals(0, dronesInRackSets());

        DroneRack rack = racks().get(0);
        int loaded = (int) rack.getAmmo().stream().filter(d -> d.getDroneType() != null).count();
        assertTrue("fixture: the rack must be loaded for the refit to mirror anything", loaded > 0);
        rack.addReloadSets(1);                           // FD3.72, long after the first draw

        assertEquals("the new set is in the ship's stockpile, not stranded behind the rack",
                held + loaded, ship.reloadStockpile().held().size());
        assertEquals(0, dronesInRackSets());
    }

    /**
     * A rack destroyed lowers the ship's capacity, which is why the racks are re-read on every call
     * rather than captured when the stockpile is built.
     * <p>
     * What it does NOT do is destroy the drones. FD2.423 ties the stockpile's destruction to the last
     * Excess Damage box, not to a rack, and that is not built yet — so the honest state here is a
     * stockpile holding more than its capacity, which is also what an S3.2 purchase will look like
     * when it arrives.
     */
    @Test
    public void losingARackLowersTheCapacity() {
        ReloadStockpile pile = ship.reloadStockpile();
        double before = pile.capacitySpaces();
        int held = pile.held().size();

        DroneRack doomed = racks().get(0);
        ship.getWeapons().fetchAllWeapons().remove(doomed);

        assertTrue("capacity falls with the rack",
                ship.reloadStockpile().capacitySpaces() < before);
        assertEquals("the drones themselves are untouched (FD2.423 is not built)",
                held, ship.reloadStockpile().held().size());
    }

    /**
     * A put-back always fits, and an addition above capacity does not.
     * <p>
     * FD2.442 does allow a stockpile over its capacity — "Extra drones purchased under (S3.2) can be
     * added to this type of storage in excess of its capacity (but do not increase its capacity)" —
     * but that is a purchase, and purchases are not built. Until they are, refusing is what stops
     * {@code put} from quietly becoming the operation nobody wrote.
     */
    @Test
    public void putTakesBackWhatWasDrawnAndNoMore() {
        ReloadStockpile pile = ship.reloadStockpile();
        DroneType type = pile.held().get(0).getDroneType();
        Drone drawn = pile.take(type, 1).get(0);

        assertTrue("the drone it just handed out goes back", pile.put(drawn));
        assertFalse("but the pile is full again, so a second does not",
                pile.put(new Drone(type)));
    }
}
