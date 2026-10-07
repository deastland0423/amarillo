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
     * Destroying the racks takes neither the reloads nor the capacity. <b>This test said the
     * opposite when it was written</b>, and the rule text is explicit the other way.
     *
     * <p><b>FD2.442</b> measures the storage against "the capacity of the ship's <b>original</b>
     * drone racks", and <b>FD2.423</b> says what that word is doing there: "If all drone racks are
     * destroyed and then one or more are repaired, <b>the repaired racks can load the remaining
     * reload drones</b> within the limits of the rules." The reloads are "stored in various
     * locations around the ship" — not behind the launcher that fires them.
     *
     * <p>The first version of this removed a rack from the weapon group by hand, which is not
     * something the engine ever does: {@code Weapon.damage()} clears {@code functional} and the rack
     * stays. So it asserted a fall that only its own surgery could produce, and taught the wrong
     * rule while passing. This damages them the way the game does, and then repairs one to collect
     * the reloads, which is FD2.423's sentence end to end.
     */
    @Test
    public void reloadsAndCapacityOutliveTheRacks() {
        ReloadStockpile pile = ship.reloadStockpile();
        double capacity = pile.capacitySpaces();
        int held = pile.held().size();
        assertTrue("fixture: there is something to lose", capacity > 0 && held > 0);

        for (DroneRack rack : racks())
            rack.damage();
        for (DroneRack rack : racks())
            assertFalse("fixture: every rack is out", rack.isFunctional());

        assertEquals("capacity is the ORIGINAL racks' (FD2.442)",
                capacity, ship.reloadStockpile().capacitySpaces(), 0.001);
        assertEquals("and the reloads are elsewhere on the ship (FD2.423)",
                held, ship.reloadStockpile().held().size());

        // FD2.423's second sentence: repair one and it can load what is left.
        racks().get(0).repair();
        assertTrue(racks().get(0).isFunctional());
        DroneType type = ship.reloadStockpile().held().get(0).getDroneType();
        assertEquals("the repaired rack draws on the surviving stockpile",
                1, ship.reloadStockpile().take(type, 1).size());
    }

    /**
     * Capacity must not WANDER as the reload sets are shuffled about.
     *
     * <p>Slice 2 left capacity reading {@code rack.getNumberOfReloads()}, which answers
     * {@code reloads.isEmpty() ? numberOfReloads : reloads.size()} — a number that depends on a
     * mutable list other code prunes. {@code completePendingReload} ends with
     * {@code reloads.removeIf(List::isEmpty)}, and after the storage moved every one of those lists
     * IS empty, so finishing a reload deletes them all and the answer switches to the other branch.
     *
     * <p>For most racks the two branches agree. The type-G is where they do not: FD3.72 gives it
     * "two sets of reloads, ONE OF WHICH IS ENTIRELY ANTI-DRONES", so {@code setAmmo} builds one
     * mirrored set against a declared count of two — and the ship's drone capacity would double the
     * moment a reload finished.
     */
    @Test
    public void capacityDoesNotWanderWhenTheReloadSetsAreShuffled() {
        Ship g = new Ship();
        g.init(KlingonShips.getD7());
        for (Weapon w : new ArrayList<>(g.getWeapons().fetchAllWeapons()))
            if (w instanceof DroneRack)
                g.getWeapons().fetchAllWeapons().remove(w);

        DroneRack typeG = new DroneRack(DroneRack.DroneRackType.TYPE_G);
        typeG.setDesignator("Rack 1");
        g.getWeapons().addWeapon(typeG);
        List<Drone> load = new ArrayList<>();
        for (int i = 0; i < typeG.getSpaces(); i++)
            load.add(new Drone(DroneType.TypeI));
        typeG.setAmmo(load);

        double before = g.reloadStockpile().capacitySpaces();
        assertTrue("fixture: the type-G stocks drone reloads", before > 0);

        // A real reload, which is what reaches the pruning: the drones leave the (now empty) sets,
        // the rack takes them in at 8C, and the leftover empty lists are swept up.
        typeG.getAmmo().remove(0);            // make room, as firing would
        List<Drone> staged = g.reloadStockpile().take(DroneType.TypeI, 1);
        assertEquals("fixture: a drone to load", 1, staged.size());
        typeG.stagePendingReload(staged, 0);
        typeG.completePendingReload();

        assertEquals("the ship's reload capacity is a property of its racks, not of a list",
                before, g.reloadStockpile().capacitySpaces(), 0.001);
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
