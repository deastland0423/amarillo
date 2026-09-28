package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRail;

/**
 * The carrier's supply of spare drones for its fighters (J4.7).
 * <p>
 * "Carriers that operate fighters carrying drones are presumed to have a supply of drones on
 * board. These drones are used to rearm the ship's fighters. The Kzinti CV has 150 'spaces'
 * of spare drones for its fighters." Other carriers are listed in Annex #7G; the supply is
 * inside the carrier's BPV and is not bought separately.
 *
 * <h2>Spaces, not drones</h2>
 * The rule counts SPACES, so the pool holds twice as many type-VIs as type-Is — the same
 * measure {@link Drone#getRackSize()} already returns, and the same one J4.821 prices deck
 * crew work in.
 *
 * <h2>One pool, not a tier behind the racks</h2>
 * J4.72 is the fact that shapes this class: drones "held in ready racks (J4.89) or loaded on
 * the fighters count as part of the ship's storage". So the 150 is a TOTAL, not a reserve
 * stacked behind the racks — a full rack is storage that has already been moved forward. What
 * this object holds is the part of that total still in the hold, and moving a drone from here
 * to a {@link ReadyRack} does not change what the ship owns, only where it is.
 * <p>
 * That is why nothing here creates or destroys drones once stocked: every transfer is a hand
 * off, and a supply that can be conjured is a supply that is infinite.
 *
 * <h2>Detonators out</h2>
 * J4.71: stored drones are kept inert, do not explode, and add nothing to the ship's final
 * explosion. So this pool is deliberately invisible to chain reactions (D12.0) — there is no
 * hook here for {@code DamageResolver} to find, and that is the rule, not an omission.
 */
public class DroneStore {

    /** Spaces this carrier's supply runs to (J4.7), racks and loaded fighters included. */
    private final double capacitySpaces;

    /** The part of that supply still in the hold. */
    private final List<Drone> reserve = new ArrayList<>();

    public DroneStore(double capacitySpaces) {
        this.capacitySpaces = Math.max(0, capacitySpaces);
    }

    /**
     * Stock the hold up to {@code spaces}, dealing round a pattern of drone types.
     * <p>
     * The pattern is the multiset of what the ship's drone rails are designed to carry, so a
     * carrier of mixed-rail fighters gets a proportionate mix rather than a hold full of one
     * type its light rails cannot use. FD2.45/J4.23/FD10.6 govern what a player may buy
     * INSTEAD; until a loadout says otherwise, the quartermaster stocks what the fighters
     * were built around.
     *
     * @return spaces actually stocked
     */
    public double stock(List<DroneType> pattern, double spaces) {
        if (pattern == null || pattern.isEmpty() || spaces <= 0)
            return 0;
        double before = spacesHeld();
        double ceiling = Math.min(before + spaces, capacitySpaces);
        // Round-robin rather than pattern-at-a-time, so a hold that runs out part way through
        // is still proportionate. A full lap with nothing fitting means the gap left is
        // smaller than the smallest drone in the pattern, and the hold is as full as it gets.
        int misses = 0;
        for (int i = 0; misses < pattern.size(); i++) {
            DroneType type = pattern.get(i % pattern.size());
            if (spacesHeld() + type.rack <= ceiling) {
                reserve.add(new Drone(type));
                misses = 0;
            } else {
                misses++;
            }
        }
        return spacesHeld() - before;
    }

    /** Spaces this carrier's supply runs to in total (J4.7). */
    public double capacitySpaces() { return capacitySpaces; }

    /** Spaces still in the hold — what a rack can still be refilled from. */
    public double spacesHeld() {
        double total = 0;
        for (Drone d : reserve)
            total += d.getRackSize();
        return total;
    }

    public int count() { return reserve.size(); }

    public boolean isEmpty() { return reserve.isEmpty(); }

    /**
     * Take the largest drone the hold has that this rail's slot can carry, or null if it
     * holds none that fit.
     * <p>
     * Largest first for the reason {@link ReadyRack#takeFor} does it: a standard rail will
     * accept a type-VI, and if it takes one, the type-I it was stocked for has only a light
     * rail left to go in, where it does not fit at all.
     */
    public Drone takeFor(DroneRail rail) {
        Drone best = null;
        for (Drone d : reserve)
            if (rail.accepts(d) && (best == null || d.getRackSize() > best.getRackSize()))
                best = d;
        if (best != null)
            reserve.remove(best);
        return best;
    }

    /** Take one drone of this exact type, or null if the hold has none. */
    public Drone take(DroneType type) {
        for (Drone d : reserve)
            if (d.getDroneType() == type) {
                reserve.remove(d);
                return d;
            }
        return null;
    }

    /** Take any drone at all, largest first, or null when the hold is empty. */
    public Drone take() {
        Drone best = null;
        for (Drone d : reserve)
            if (best == null || d.getRackSize() > best.getRackSize())
                best = d;
        if (best != null)
            reserve.remove(best);
        return best;
    }

    /**
     * Put a drone back in the hold — a rack being struck down, or a loadout being changed.
     * <p>
     * Bounded by the capacity even though conservation should make that unreachable: the
     * bound is what makes a leak show up as a refusal here rather than as a hold that quietly
     * grew past what the ship owns.
     */
    public boolean put(Drone drone) {
        if (drone == null || spacesHeld() + drone.getRackSize() > capacitySpaces)
            return false;
        reserve.add(drone);
        return true;
    }

    /** What is in the hold, for the owner's readout. Never handed to an enemy: G4.233. */
    public List<Drone> contents() {
        return Collections.unmodifiableList(reserve);
    }
}
