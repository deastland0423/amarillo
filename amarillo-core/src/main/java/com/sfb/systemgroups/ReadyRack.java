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

    private final List<Drone> drones = new ArrayList<>();

    private ReadyRack(String servesFighterType, int capacity, DroneType stock) {
        this.servesFighterType = servesFighterType;
        this.capacity = capacity;
        for (int i = 0; i < capacity; i++)
            drones.add(new Drone(stock));   // J4.8223/J4.886: racks start full
    }

    /**
     * The rack a box would have been built with for this occupant, or null if the fighter
     * carries no drones (J4.822 only presumes a rack where one was needed).
     */
    public static ReadyRack forFighter(Shuttle occupant) {
        if (!(occupant instanceof com.sfb.objects.shuttles.Fighter fighter))
            return null;   // only a fighter box gets a rack (J4.822)
        int rails = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail)
                rails++;
        if (rails == 0)
            return null;

        DroneType stock = fighter.getDefaultDroneType();
        if (stock == null)
            return null;
        return new ReadyRack(fighter.getClass().getSimpleName(), rails, stock);
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
