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

    // ------------------------------------------------- type-D plasma torpedoes (J4.825)

    /**
     * Type-D plasma torpedoes in the hold, as a count.
     * <p>
     * The SAME pool as the drones, not a second store. J4.7 gives a carrier one holding
     * measured in spaces, and J4.825 makes a type-D "the same size as a one-space drone" and
     * puts it under the drone storage rules - so a torpedo occupies a space of this hold like
     * anything else in it. A count rather than objects because FP9.18 denies them variants:
     * one type-D is any other.
     */
    /**
     * Plasma in the hold, measured in SPACES rather than torpedoes, because a type-D and a type-K
     * are not the same size. A type-D is one space (FP9.21); a type-K is half of one (FP13.32:
     * "a plasma-K capsule is half of the size of a plasma-D").
     * <p>
     * Spaces rather than two counters is the owner's model (2026-10-05) and it is the one FP13.33
     * describes: "for each plasma-D replaced by a plasma-K on a fighter's ready rack/launch rail,
     * TWO plasma-Ks can replace one plasma-D in a carrier's reload storage. Unlike drones, a
     * player controlling a plasma carrier can choose the mix... irrespective of the number of
     * plasma-Ds and/or plasma-Ks that are loaded." A free mix of two sizes in one pool is exactly
     * a space total, and it is the same arithmetic the drone side already does with
     * {@code Drone.getRackSize()} — a type-K is to a type-D what a type-VI is to a type-I.
     */
    private double plasmaSpaces;

    /** Spaces one torpedo of this type occupies in the hold. */
    public static double spacesPerTorpedo(com.sfb.properties.PlasmaType type) {
        return type == com.sfb.properties.PlasmaType.K ? 0.5 : 1.0;
    }

    /** How many torpedoes of this type the hold could still hand out. */
    public int plasmaCount(com.sfb.properties.PlasmaType type) {
        return (int) Math.floor(plasmaSpaces / spacesPerTorpedo(type));
    }

    /** Type-Ds the hold could hand out. Unchanged meaning for every existing caller. */
    public int plasmaDCount() { return plasmaCount(com.sfb.properties.PlasmaType.D); }

    /**
     * Stock type-D torpedoes into the spaces left, up to {@code spaces} of them.
     * <p>
     * Stocked as type-Ds because that is what a carrier's supply IS: FP13.33 has the player
     * declare some of it as type-Ks before the scenario, which is a COI choice and not a
     * stocking one.
     *
     * @return how many were stocked
     */
    public int stockPlasmaDs(double spaces) {
        int room = (int) Math.floor(Math.min(spaces, capacitySpaces() - spacesHeld()));
        if (room <= 0)
            return 0;
        plasmaSpaces += room;
        return room;
    }

    /** Take one torpedo of this type out, or false if the hold has not the space for it. */
    public boolean takePlasma(com.sfb.properties.PlasmaType type) {
        double cost = spacesPerTorpedo(type);
        if (plasmaSpaces < cost)
            return false;
        plasmaSpaces -= cost;
        return true;
    }

    /** Put one torpedo of this type back - it never left the ship. */
    public void putPlasma(com.sfb.properties.PlasmaType type) {
        plasmaSpaces += spacesPerTorpedo(type);
    }

    /** Take one type-D out, or false if there are none. */
    public boolean takePlasmaD() { return takePlasma(com.sfb.properties.PlasmaType.D); }

    /** Put one type-D back - it never left the ship. */
    public void putPlasmaD() { putPlasma(com.sfb.properties.PlasmaType.D); }

    /** Spaces still in the hold — what a rack can still be refilled from. */
    /**
     * Spaces in use, drones and torpedoes together - they share the hold (J4.7, J4.825).
     */
    public double spacesHeld() {
        double total = plasmaSpaces;    // already in spaces: D = 1, K = 0.5 (FP9.21, FP13.32)
        for (Drone d : reserve)
            total += d.getRackSize();
        return total;
    }

    public int count() { return reserve.size(); }

    /**
     * Nothing in the hold at all - drones AND torpedoes, since they share it (J4.825).
     * <p>
     * Counting only the drones made this answer "empty" on a ship whose entire stores were
     * type-D torpedoes, which is how the Romulan KRV's deck crews found no refilling to do
     * beside sixty spaces of them.
     */
    public boolean isEmpty() { return reserve.isEmpty() && plasmaSpaces <= 0; }

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
