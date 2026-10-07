package com.sfb.systemgroups;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FD2.442's drone rack reload storage: <b>one stockpile for the whole ship</b>, which any rack may
 * draw from.
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
 * <h2>What was wrong</h2>
 * Reloads were held per rack and searched per rack, so a Klingon D7's rack 1 could not draw a drone
 * that happened to sit in rack 2's set — FD2.422 straightforwardly violated. Meanwhile the
 * scatter-pack path, forty lines further down the same method, already walked every rack by hand
 * under the comment "Collect requested drones from reload stockpile across all racks". One
 * behaviour, two implementations, and only one of them right.
 *
 * <h2>What this is NOT, yet</h2>
 * The drones still physically live in {@link DroneRack}'s reload sets; this is the pool's API over
 * them, not a new place to keep them. That is deliberate sequencing rather than a half-measure: the
 * seam is what lets the storage move later without every caller moving with it, and the DTO still
 * reports reloads per rack because the Energy Allocation dialog presents them that way. Moving the
 * storage changes all three tiers and is its own slice.
 *
 * <p>Still to come behind this API: FD2.4421's conveyor (a cargo drone moves up as one leaves the
 * stockpile — {@code CargoDroneStore.draw()} exists and nothing calls it), S3.2's purchases sitting
 * above capacity, and FD2.423's destruction with the last Excess Damage box.
 *
 * <h2>Type-D and type-H contribute nothing</h2>
 * FD3.43: "While type-D (and type-H) drone racks do not have formal reloads..." — their magazines
 * ARE the reload capacity, already loaded, and FD2.4424 sends their refills straight from the cargo
 * boxes instead. So they are counted out of the capacity sum rather than counted in with zero, which
 * would read as a rack whose stockpile had been spent.
 */
public final class ReloadStockpile {

    private final List<DroneRack> racks;

    private ReloadStockpile(List<DroneRack> racks) {
        this.racks = racks;
    }

    /**
     * The stockpile of the ship owning these weapons.
     * <p>
     * Built from the weapon group rather than held as a field on Ship, because the drones still live
     * on the racks: a cached object would go stale the moment a rack was destroyed or refitted. When
     * the storage moves, this becomes a real field and this factory becomes its accessor.
     */
    public static ReloadStockpile of(Weapons weapons) {
        List<DroneRack> racks = new ArrayList<>();
        if (weapons != null)
            for (Weapon w : weapons.fetchAllWeapons())
                if (w instanceof DroneRack rack)
                    racks.add(rack);
        return new ReloadStockpile(racks);
    }

    /**
     * FD2.43's capacity: the spaces held by all of the ship's racks, times the reload sets they
     * carry — so a ship with two four-space racks at one set each stocks eight spaces.
     * <p>
     * Racks with no formal reloads (type-D, type-H) add nothing, per FD3.43.
     */
    public double capacitySpaces() {
        double total = 0;
        for (DroneRack rack : racks)
            total += rack.getSpaces() * (double) rack.getNumberOfReloads();
        return total;
    }

    /** Every drone in the stockpile, wherever it currently sits. */
    public List<Drone> held() {
        List<Drone> out = new ArrayList<>();
        for (DroneRack rack : racks)
            for (List<Drone> set : rack.getReloads())
                out.addAll(set);
        return out;
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
     * Take up to {@code count} drones of one type out of the stockpile, from wherever they sit.
     *
     * <p>Returns the drones actually found, which may be fewer than asked — the caller is choosing
     * from a stockpile it can see, so a short answer means someone else drew first rather than that
     * the request was wrong.
     *
     * <p>The drones are REMOVED. FD2.422 is what makes this legitimate across racks: the stockpile is
     * not associated with any particular rack, so which set a drone was sitting in is bookkeeping and
     * not a constraint.
     */
    public List<Drone> take(DroneType type, int count) {
        List<Drone> taken = new ArrayList<>();
        if (type == null || count <= 0)
            return taken;
        for (DroneRack rack : racks) {
            for (List<Drone> set : rack.getReloads()) {
                for (java.util.Iterator<Drone> it = set.iterator(); it.hasNext() && taken.size() < count; ) {
                    Drone d = it.next();
                    if (type.equals(d.getDroneType())) {
                        it.remove();
                        taken.add(d);
                    }
                }
            }
            if (taken.size() >= count)
                break;
        }
        return taken;
    }

    /**
     * Put a drone back, into the first set with room.
     * <p>
     * Used when a loading is abandoned rather than as a way to add stock — a drone bought under S3.2
     * goes in above capacity and is a different operation (FD2.442), not yet built. Returns false if
     * there is nowhere to put it, which the caller should treat as a bug rather than a refusal:
     * something was taken from here, so there was room for it a moment ago.
     */
    public boolean put(Drone drone) {
        if (drone == null)
            return false;
        for (DroneRack rack : racks) {
            for (List<Drone> set : rack.getReloads()) {
                if (set.size() < rack.getSpaces()) {
                    set.add(drone);
                    return true;
                }
            }
        }
        return false;
    }

    /** True when the ship has somewhere to keep reloads at all. */
    public boolean exists() {
        return capacitySpaces() > 0;
    }
}
