package com.sfb.systemgroups;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FD2.442's drone rack reload storage: <b>one stockpile for the whole ship</b>, which any rack may
 * draw from, and which is where those drones now physically live.
 *
 * <h2>Why this exists, in the rules' own words</h2>
 * The rulebook says it three times, and the third leaves no room:
 * <ul>
 *   <li><b>FD2.43 STOCKPILE</b> — "All ships are presumed to carry one complete set of reloads
 *       (i.e., drones equal to the number of spaces held by <b>all</b> of their racks)." That is the
 *       capacity rule, and it is a sum across the ship.
 *   <li><b>FD2.31</b> — "<b>The ship</b> has one set of reloads (enough to reload <b>all of its
 *       racks</b> one time) on board."
 *   <li><b>FD2.422</b> — "A unit's total stockpile (FD2.43) is <b>not directly associated with any
 *       particular rack and can be loaded onto any rack on the ship</b> (or a scatterpack, or on
 *       fighter ready racks assuming the unit also had fighters)."
 * </ul>
 *
 * <h2>What was wrong, and what has now moved</h2>
 * Reloads were held per rack and searched per rack, so a Klingon D7's rack 1 could not draw a drone
 * that happened to sit in rack 2's set. The first slice put this API over the top of that storage so
 * every caller asked the ship rather than a rack. This slice moves the storage itself: the drones
 * live in one list here, and {@link DroneRack}'s reload sets no longer hold any.
 *
 * <p>That second step is not tidying. While two places could hold the same drone, the DTO reported a
 * pool PER RACK, so a two-rack ship offered the same stockpile twice and a player could spend it
 * twice over — the server's {@code take()} being authoritative, the second rack would silently come
 * up short. One list cannot be double-spent.
 *
 * <h2>The racks still say how big it is</h2>
 * Capacity is a rule about racks (FD2.43: "spaces held by all of their racks"), so it is still read
 * off them, and a rack's reload sets survive the move as empty lists. That is deliberate: a set is
 * the rack's declaration that it HAS a set, which is what the capacity sum counts, while the drones
 * that were in it belong to the ship. Destroy a rack and the ship's capacity falls accordingly,
 * which is why the racks are re-read on every call rather than captured once.
 *
 * <h2>Authoring still goes through the racks</h2>
 * {@link DroneRack#setAmmo} builds each rack's reload sets as a mirror of what is loaded (FD2.45),
 * the Y175 type-G refit adds a third set the same way (FD3.72), and the COI edits them. All of that
 * is the rack saying what reloads it came with, and it keeps working: {@link #absorb()} draws
 * anything newly sitting in a rack's sets into the ship's list, so a drone authored after the first
 * draw is not stranded. It is called at the head of every method here, which makes the move
 * invisible to the authoring path and means no caller has to know the order it happened in.
 *
 * <h2>Still to come</h2>
 * FD2.4421's conveyor (a cargo drone moves up as one leaves the stockpile — {@code
 * CargoDroneStore.draw()} exists and nothing calls it), S3.2's purchases sitting ABOVE capacity
 * ("Extra drones purchased under (S3.2) can be added to this type of storage in excess of its
 * capacity (but do not increase its capacity)"), and FD2.423's destruction with the last Excess
 * Damage box.
 *
 * <h2>Type-D and type-H contribute nothing</h2>
 * FD3.43: "While type-D (and type-H) drone racks do not have formal reloads..." — their magazines
 * ARE the reload capacity, already loaded, and FD2.4424 sends their refills straight from the cargo
 * boxes instead. So they are counted out of the capacity sum rather than counted in with zero, which
 * would read as a rack whose stockpile had been spent.
 */
public final class ReloadStockpile {

    /**
     * The ship's weapon group, re-read for its racks on every call rather than captured: a rack
     * destroyed mid-battle lowers the ship's reload capacity, and a stale list would not notice.
     */
    private final Weapons weapons;

    /** Where the drones actually are. One list, for the whole ship. */
    private final List<Drone> drones = new ArrayList<>();

    private ReloadStockpile(Weapons weapons) {
        this.weapons = weapons;
    }

    /**
     * A new, empty stockpile for a ship's weapon group.
     * <p>
     * {@link com.sfb.objects.Ship#reloadStockpile()} holds exactly one of these and hands the same
     * one back every time, because it is now storage rather than a view: building a second would
     * split the ship's drones between two piles. The factory stays package-visible to callers only
     * through Ship for that reason.
     */
    public static ReloadStockpile of(Weapons weapons) {
        return new ReloadStockpile(weapons);
    }

    /** The ship's racks, as they are right now. */
    private List<DroneRack> racks() {
        List<DroneRack> racks = new ArrayList<>();
        if (weapons != null)
            for (Weapon w : weapons.fetchAllWeapons())
                if (w instanceof DroneRack rack)
                    racks.add(rack);
        return racks;
    }

    /**
     * Draw into the ship's list anything that has appeared in a rack's reload sets.
     *
     * <p>The rack is where reloads are AUTHORED — {@link DroneRack#setAmmo} mirrors the load into
     * them (FD2.45), {@link DroneRack#addReloadSets} adds the Y175 set (FD3.72), the COI edits
     * them, and the sample builders write them directly. Rather than make each of those know about
     * the ship, this takes what they wrote whenever the stockpile is next touched.
     *
     * <p>The empty set lists are left in place: they are the rack's statement that it HAS that many
     * sets, which is what {@link #capacitySpaces()} counts.
     */
    private void absorb() {
        for (DroneRack rack : racks()) {
            for (List<Drone> set : rack.getReloads()) {
                if (set.isEmpty())
                    continue;
                drones.addAll(set);
                set.clear();
            }
        }
    }

    /**
     * FD2.43's capacity: the spaces held by all of the ship's racks, times the reload sets they
     * carry — so a ship with two four-space racks at one set each stocks eight spaces.
     * <p>
     * Racks with no formal reloads (type-D, type-H) add nothing, per FD3.43.
     */
    public double capacitySpaces() {
        double total = 0;
        for (DroneRack rack : racks())
            total += rack.getSpaces() * (double) rack.getNumberOfReloads();
        return total;
    }

    /** Every drone in the stockpile. */
    public List<Drone> held() {
        absorb();
        return Collections.unmodifiableList(new ArrayList<>(drones));
    }

    /** How many of each type are in the stockpile, for a picker or a readout. */
    public Map<DroneType, Integer> heldByType() {
        Map<DroneType, Integer> counts = new LinkedHashMap<>();
        for (Drone d : held())
            if (d.getDroneType() != null)
                counts.merge(d.getDroneType(), 1, Integer::sum);
        return counts;
    }

    /** Spaces currently held, which is not the capacity once any have been drawn. */
    public double spacesHeld() {
        double total = 0;
        for (Drone d : held())
            total += d.getRackSize();
        return total;
    }

    /**
     * Take up to {@code count} drones of one type out of the stockpile.
     *
     * <p>Returns the drones actually found, which may be fewer than asked — the caller is choosing
     * from a stockpile it can see, so a short answer means someone else drew first rather than that
     * the request was wrong.
     *
     * <p>The drones are REMOVED. FD2.422 is what makes this legitimate for any rack on the ship: the
     * stockpile is not associated with a particular one.
     */
    public List<Drone> take(DroneType type, int count) {
        absorb();
        List<Drone> taken = new ArrayList<>();
        if (type == null || count <= 0)
            return taken;
        for (Iterator<Drone> it = drones.iterator(); it.hasNext() && taken.size() < count; ) {
            Drone d = it.next();
            if (type.equals(d.getDroneType())) {
                it.remove();
                taken.add(d);
            }
        }
        return taken;
    }

    /**
     * Put a drone back.
     * <p>
     * Used when a loading is abandoned rather than as a way to add stock — a drone bought under S3.2
     * goes in ABOVE capacity and is a different operation (FD2.442), not yet built. A put-back
     * always has room, because something was taken from here a moment ago; refusing over capacity
     * is what keeps this from quietly becoming that unbuilt operation.
     */
    public boolean put(Drone drone) {
        absorb();
        if (drone == null)
            return false;
        if (spacesHeld() + drone.getRackSize() > capacitySpaces() + 1e-9)
            return false;
        drones.add(drone);
        return true;
    }

    /** True when the ship has somewhere to keep reloads at all. */
    public boolean exists() {
        return capacitySpaces() > 0;
    }
}
