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
 * FD2.442's reload storage as ONE stockpile for the ship.
 *
 * <h2>The rule, and what was wrong</h2>
 * <b>FD2.422</b>: "A unit's total stockpile (FD2.43) is <b>not directly associated with any
 * particular rack and can be loaded onto any rack on the ship</b> (or a scatterpack, or on fighter
 * ready racks assuming the unit also had fighters)." <b>FD2.43</b> sets the size of it: "drones equal
 * to the number of spaces held by <b>all</b> of their racks."
 *
 * <p>Reloads were held per rack AND searched per rack, so a Klingon D7's rack 1 could not draw a
 * drone that happened to be sitting in rack 2's set. Forty lines below that code, the scatter-pack
 * path walked every rack by hand under the comment "Collect requested drones from reload stockpile
 * across all racks" — the same behaviour, implemented twice, and only the second one right.
 *
 * <p>The drones still physically live in the racks' sets. This is the pool's API over them, so the
 * storage can move without every caller moving too; see {@link ReloadStockpile} for why that is
 * sequenced rather than done at once.
 */
public class ReloadStockpileTest {

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

    /** Fixture check: the hull this test leans on really does have more than one rack. */
    @Test
    public void theFixtureHasSeveralRacks() {
        assertTrue("a D7 should carry drone racks: " + racks().size(), racks().size() >= 2);
        assertTrue("and a stockpile to draw on", ship.reloadStockpile().exists());
    }

    /**
     * FD2.43: capacity is the spaces of ALL the racks, times the sets they carry — not one rack's
     * worth. A ship with two four-space racks at one set each stocks eight spaces, and reading it off
     * a single rack would understate the ship by however many racks it has.
     */
    @Test
    public void capacityIsEveryRacksSpacesTogether() {
        double expected = 0;
        for (DroneRack rack : racks())
            expected += rack.getSpaces() * (double) rack.getNumberOfReloads();

        assertEquals(expected, ship.reloadStockpile().capacitySpaces(), 0.001);
        assertTrue("fixture: the sum should exceed any single rack's share",
                expected > racks().get(0).getSpaces());
    }

    /**
     * The fix. A drone sitting in rack 2's set is available to rack 1, because FD2.422 says the
     * stockpile is not associated with any particular rack.
     *
     * <p>Set up so it can only pass for the right reason: rack 1's own sets are emptied completely,
     * and the wanted drone is placed in rack 2 alone. Before this change the take would have found
     * nothing.
     */
    @Test
    public void aRackMayDrawFromAnotherRacksShare() {
        List<DroneRack> racks = racks();
        assertTrue(racks.size() >= 2);

        for (List<Drone> set : racks.get(0).getReloads())
            set.clear();
        for (List<Drone> set : racks.get(1).getReloads())
            set.clear();
        racks.get(1).getReloads().get(0).add(new Drone(DroneType.TypeIV));

        // Nothing is asked of rack 1 at all — the stockpile is the ship's, and that is the point.
        List<Drone> taken = ship.reloadStockpile().take(DroneType.TypeIV, 1);

        assertEquals("the drone in the OTHER rack's set is part of this ship's stockpile",
                1, taken.size());
        assertEquals(DroneType.TypeIV, taken.get(0).getDroneType());
    }

    /** What is taken is gone from the stockpile, wherever it had been sitting. */
    @Test
    public void takingRemovesFromTheStockpile() {
        ReloadStockpile pile = ship.reloadStockpile();
        int before = pile.held().size();
        assertTrue("fixture: the stockpile should start stocked", before > 0);

        DroneType type = pile.held().get(0).getDroneType();
        List<Drone> taken = pile.take(type, 1);

        assertEquals(1, taken.size());
        assertEquals("one fewer in the ship's stockpile",
                before - 1, ship.reloadStockpile().held().size());
    }

    /**
     * A short answer, not a refusal. The caller is choosing from a stockpile it can see, so fewer
     * than asked means someone drew first rather than that the request was malformed.
     */
    @Test
    public void takingMoreThanIsThereReturnsWhatThereIs() {
        ReloadStockpile pile = ship.reloadStockpile();
        int available = pile.heldByType().getOrDefault(DroneType.TypeI, 0);
        assertTrue("fixture: a D7 stocks type-Is", available > 0);

        List<Drone> taken = pile.take(DroneType.TypeI, available + 5);

        assertEquals("every one there was, and no more", available, taken.size());
        assertEquals(0, (int) ship.reloadStockpile().heldByType().getOrDefault(DroneType.TypeI, 0));
    }

    /**
     * Putting one back matters because of an ordering the scatter-pack loader depends on: it takes a
     * drone, offers it to the pack, and must put it back if the pack refuses. The loop was originally
     * written offer-first precisely because the other order "destroyed ordnance to enforce a limit",
     * so the put-back is what makes take-first safe.
     */
    @Test
    public void aDroneCanGoBackWhereItCameFrom() {
        ReloadStockpile pile = ship.reloadStockpile();
        int before = pile.held().size();
        Drone taken = pile.take(pile.held().get(0).getDroneType(), 1).get(0);
        assertEquals(before - 1, ship.reloadStockpile().held().size());

        assertTrue("there was room for it a moment ago", ship.reloadStockpile().put(taken));
        assertEquals("nothing destroyed to enforce a limit",
                before, ship.reloadStockpile().held().size());
    }

    /** Asking for a type the ship does not stock takes nothing and breaks nothing. */
    @Test
    public void askingForAbsentStockTakesNothing() {
        List<Drone> taken = ship.reloadStockpile().take(DroneType.TypeVI, 3);
        assertTrue("a D7's racks hold no type-VIs to find: " + taken, taken.isEmpty());
    }

    /** Null and zero are no-ops rather than exceptions — a UI can ask for nothing. */
    @Test
    public void nullAndZeroAreHarmless() {
        assertTrue(ship.reloadStockpile().take(null, 2).isEmpty());
        assertTrue(ship.reloadStockpile().take(DroneType.TypeI, 0).isEmpty());
        assertFalse(ship.reloadStockpile().put(null));
    }

    /**
     * FD3.43: type-D and type-H racks have no formal reloads — their magazines ARE the reload
     * capacity, already loaded — so they contribute nothing to the stockpile's size. Counted in with
     * zero they would read as a rack whose share had been spent.
     */
    @Test
    public void aMagazineRackAddsNoCapacity() {
        Ship bare = new Ship();
        bare.init(KlingonShips.getD7());
        for (Weapon w : new ArrayList<>(bare.getWeapons().fetchAllWeapons()))
            if (w instanceof DroneRack)
                bare.getWeapons().fetchAllWeapons().remove(w);

        DroneRack typeD = new DroneRack(DroneRack.DroneRackType.TYPE_D);
        typeD.setDesignator("Rack 1");
        bare.getWeapons().addWeapon(typeD);

        assertEquals("FD3.43: no formal reloads", 0, typeD.getNumberOfReloads());
        assertEquals("so the ship's stockpile is empty rather than partly spent",
                0.0, bare.reloadStockpile().capacitySpaces(), 0.001);
        assertFalse(bare.reloadStockpile().exists());
    }
}
