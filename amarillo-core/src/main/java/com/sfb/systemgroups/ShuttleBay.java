package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.AdminShuttle;
import com.sfb.objects.GASShuttle;
import com.sfb.objects.HTSShuttle;
import com.sfb.objects.Shuttle;
import com.sfb.objects.Unit;

/**
 * A single shuttle bay on a ship.
 *
 * Each bay has its own shuttle inventory and enforces a 2-impulse launch
 * cooldown (one launch per 2 impulses). Analogous to a DroneRack.
 */
public class ShuttleBay {

    private static final int LAUNCH_COOLDOWN = 2; // impulses between launches

    private final Unit        owner;
    private final List<Shuttle> inventory = new ArrayList<>();
    private int               lastLaunchImpulse = -LAUNCH_COOLDOWN; // ready from impulse 1

    public ShuttleBay(Unit owner) {
        this.owner = owner;
    }

    // -------------------------------------------------------------------------
    // Inventory
    // -------------------------------------------------------------------------

    public void addShuttle(Shuttle shuttle) {
        inventory.add(shuttle);
    }

    public List<Shuttle> getInventory() {
        return inventory;
    }

    public int getCapacity() {
        return inventory.size(); // current count; max is determined by SSD boxes
    }

    // -------------------------------------------------------------------------
    // Launch
    // -------------------------------------------------------------------------

    public boolean canLaunch(int currentImpulse) {
        return (currentImpulse - lastLaunchImpulse) >= LAUNCH_COOLDOWN;
    }

    /**
     * Launch the given shuttle from this bay.
     * Caller is responsible for checking canLaunch() first.
     * @return the shuttle, removed from inventory, or null if not found.
     */
    public Shuttle launch(Shuttle shuttle, int speed, int facing, int currentImpulse) {
        if (!inventory.remove(shuttle)) return null;
        shuttle.setSpeed(Math.min(speed, shuttle.getMaxSpeed()));
        shuttle.setFacing(facing);
        lastLaunchImpulse = currentImpulse;
        return shuttle;
    }

    // -------------------------------------------------------------------------
    // Landing
    // -------------------------------------------------------------------------

    public boolean land(Shuttle shuttle) {
        shuttle.setCurrentSpeed(0);
        shuttle.setLocation(null);
        shuttle.setFacing(0);
        inventory.add(shuttle);
        return true;
    }

    // -------------------------------------------------------------------------
    // Factory: build shuttle from type string
    // -------------------------------------------------------------------------

    public static Shuttle buildShuttle(String type, String name) {
        Shuttle s;
        switch (type.toLowerCase()) {
            case "gas":     s = new GASShuttle();   break;
            case "hts":     s = new HTSShuttle();   break;
            case "suicide": s = new com.sfb.objects.SuicideShuttle(new com.sfb.objects.AdminShuttle()); break;
            case "admin":
            default:        s = new AdminShuttle();  break;
        }
        s.setName(name);
        return s;
    }

    public Unit getOwner() { return owner; }
}
