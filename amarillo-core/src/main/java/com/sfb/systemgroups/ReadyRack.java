package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Drone;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * The drone ready rack built into a fighter box (J4.822).
 * <p>
 * "Each shuttle box that originally contained a fighter that can carry drones is presumed to
 * have a ready rack to store reloads." It is the drone fighter's answer to the Hydran fusion
 * capacitor: per box, tied to the kind of fighter that box was built for, destroyed with the
 * box, and the only place that box's fighter can be armed from.
 * <p>
 * Two things differ from the capacitor and are easy to get wrong:
 * <ul>
 *   <li>It holds ONE reload — "the same drones that the fighter it is designed to service
 *       carries" (J4.8222) — where a fusion capacitor holds two.</li>
 *   <li>J4.8222 makes it type-specific: a rack built to service an AAS cannot arm some other
 *       fighter that happens to be sitting in the box.</li>
 * </ul>
 * J4.8223 gives the resting state: racks full, fighters unloaded. The fighters are loaded when
 * a strike is called, and the crews refill the racks while the strike is away.
 */
public class ReadyRack {

    /** The fighter class this rack services (J4.8222); a rack cannot arm anything else. */
    private final String servesFighterType;

    /** How many drones a full rack holds: one reload for the fighter it serves. */
    private final int capacity;

    /**
     * The shape of a full rack — one entry per slot, of what that slot was built to hold.
     * <p>
     * Kept because a part-empty rack has to be able to say WHAT it is short of, not just how
     * many: a TAAS rack missing a light slot and a standard one wants a type-VI and a type-I,
     * and a refill that fetched two type-Is would bring one the fighter cannot load.
     */
    private final List<DroneType> design;

    private final List<Drone> drones = new ArrayList<>();

    private ReadyRack(String servesFighterType, List<DroneType> stock) {
        this.servesFighterType = servesFighterType;
        this.capacity = stock.size();
        this.design = List.copyOf(stock);
        for (DroneType type : stock)
            drones.add(new Drone(type));   // J4.8223/J4.886: racks start full
    }

    /**
     * The rack a box would have been built with for this occupant, or null if the fighter
     * carries no drones (J4.822 only presumes a rack where one was needed).
     */
    public static ReadyRack forFighter(Shuttle occupant) {
        if (!(occupant instanceof com.sfb.objects.shuttles.Fighter fighter))
            return null;   // only a fighter box gets a rack (J4.822)

        // One drone per rail, of what THAT rail is designed to carry. Per rail rather than
        // per fighter because a Kzinti TAAS has two standard rails and two light ones, and a
        // Type-I will not go in a light rail at all — a rack stocked from one answer would
        // hold two drones its own fighter cannot take.
        //
        // A rail carrying an EW pod is skipped. J4.962: "An EWP replaces one drone carried by
        // the fighter" — so that rail has no drone to reload, and a rack that stocked one
        // anyway would show a HAAS-E holding two Type-Is it can never load and charge its
        // carrier two spaces of stores for them. A fighter whose every rail is podded needs
        // no rack at all, which falls out of the empty check below.
        List<DroneType> stock = new ArrayList<>();
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.getDesignDrone() != null
                    && !rail.hasEwPod())
                stock.add(rail.getDesignDrone());
        if (stock.isEmpty())
            return null;
        return new ReadyRack(fighter.getClass().getSimpleName(), stock);
    }

    public String getServesFighterType() { return servesFighterType; }

    public int capacity() { return capacity; }

    public int count() { return drones.size(); }

    public boolean isFull() { return drones.size() >= capacity; }

    public boolean isEmpty() { return drones.isEmpty(); }

    /** J4.8222: this rack services one kind of fighter and no other. */
    public boolean serves(Shuttle fighter) {
        return fighter != null && servesFighterType.equals(fighter.getClass().getSimpleName());
    }

    /** Take one drone out to load onto the fighter, or null if the rack is empty. */
    public Drone take() {
        return drones.isEmpty() ? null : drones.remove(drones.size() - 1);
    }

    /**
     * Take a drone this rail can actually carry, largest first, or null if it holds none.
     * <p>
     * Needed even when the rack was stocked correctly: a standard rail will happily take a
     * Type-VI, and if it does, the Type-I meant for it is left with only a light rail to go
     * in. Serving the biggest drone that fits keeps the awkward ones moving first.
     */
    public Drone takeFor(DroneRail rail) {
        Drone best = null;
        for (Drone d : drones)
            if (rail.accepts(d) && (best == null || d.getRackSize() > best.getRackSize()))
                best = d;
        if (best != null)
            drones.remove(best);
        return best;
    }

    /** One entry per slot, of what a full rack of this shape holds (J4.8222). */
    public List<DroneType> design() {
        return design;
    }

    /**
     * The slots this rack is short of, largest first — what a refill should go and fetch.
     * <p>
     * A multiset difference rather than a count: a rack holding a spare type-VI where a
     * type-I belongs is short of the type-I, however full it looks. Largest first because
     * those are the ones a part-stocked hold runs out of.
     */
    public List<DroneType> slotsMissing() {
        List<DroneType> missing = new ArrayList<>(design);
        for (Drone held : drones)
            missing.remove(held.getDroneType());   // removes one matching slot, not all
        missing.sort((a, b) -> Double.compare(b.rack, a.rack));
        return missing;
    }

    /** Spaces the drones in here take up — what the fighter's load is measured in (FD7.211). */
    public double spaces() {
        double total = 0;
        for (Drone d : drones)
            total += d.getRackSize();
        return total;
    }

    /** Put a drone back — unloading a fighter, or a deck crew refilling from stores. */
    public boolean put(Drone drone) {
        if (drone == null || isFull())
            return false;
        drones.add(drone);
        return true;
    }

    /** What is in it, for the owner's readout. Never handed to an enemy: G4.233. */
    public List<Drone> contents() {
        return java.util.Collections.unmodifiableList(drones);
    }

    /** J4.831's rule for capacitors applies here too: the box dies, its stores die with it. */
    public void destroy() {
        drones.clear();
    }
}
