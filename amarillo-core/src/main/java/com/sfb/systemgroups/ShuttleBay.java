package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sfb.objects.AdminShuttle;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Fighter;
import com.sfb.objects.GASShuttle;
import com.sfb.objects.HTSShuttle;
import com.sfb.objects.ScatterPack;
import com.sfb.objects.Shuttle;
import com.sfb.objects.Stinger1;
import com.sfb.objects.Stinger2;
import com.sfb.objects.StingerH;
import com.sfb.objects.SuicideShuttle;
import com.sfb.objects.Unit;
import com.sfb.weapons.FighterHellbore;
import com.sfb.weapons.Weapon;

/**
 * A single shuttle bay on a ship.
 *
 * Each bay has a standard hatch (one launch per 2 impulses, landing/mines only) and
 * optionally one or more launch tubes (J1.54). Each tube has its own 2-impulse
 * cooldown and can only launch fighters (not admin variants, HTS, GAS, etc.).
 * Recovery always goes through the standard hatch (J1.541).
 */
public class ShuttleBay {

    private static final int LAUNCH_COOLDOWN = 2; // impulses between launches

    private final Unit          owner;
    private final List<Shuttle> inventory         = new ArrayList<>();
    private int                 lastLaunchImpulse = -LAUNCH_COOLDOWN; // standard hatch, ready from impulse 1

    // Launch tubes (J1.54) — each has its own cooldown
    private int   launchTubeCount = 0;
    private int[] lastTubeImpulse = new int[0];

    public ShuttleBay(Unit owner) {
        this.owner = owner;
    }

    // -------------------------------------------------------------------------
    // Launch tube configuration
    // -------------------------------------------------------------------------

    /** Set the number of launch tubes for this bay (J1.54). Call after construction. */
    public void setLaunchTubeCount(int n) {
        launchTubeCount = n;
        lastTubeImpulse = new int[n];
        Arrays.fill(lastTubeImpulse, -LAUNCH_COOLDOWN); // all tubes ready from impulse 1
    }

    public int getLaunchTubeCount() { return launchTubeCount; }

    /** Number of launch tubes currently off cooldown. */
    public int getAvailableTubeCount(int currentImpulse) {
        int count = 0;
        for (int last : lastTubeImpulse)
            if (currentImpulse - last >= LAUNCH_COOLDOWN) count++;
        return count;
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
        return inventory.size();
    }

    // -------------------------------------------------------------------------
    // Launch
    // -------------------------------------------------------------------------

    /** True if the standard hatch is ready. */
    public boolean canLaunch(int currentImpulse) {
        return (currentImpulse - lastLaunchImpulse) >= LAUNCH_COOLDOWN;
    }

    /**
     * True if this shuttle can be launched by any available mechanism.
     * Fighters may use a launch tube; all other types require the standard hatch.
     */
    public boolean canLaunch(Shuttle shuttle, int currentImpulse) {
        if (isLaunchTubeEligible(shuttle) && getAvailableTubeCount(currentImpulse) > 0)
            return true;
        return canLaunch(currentImpulse);
    }

    /** Consume this bay's standard-hatch launch slot (e.g. dropping a mine). */
    public void markUsed(int currentImpulse) {
        lastLaunchImpulse = currentImpulse;
    }

    /**
     * Launch the given shuttle.
     * For fighters, automatically uses an available launch tube if one exists;
     * otherwise falls back to the standard hatch.
     * Caller is responsible for checking canLaunch(shuttle, impulse) first.
     *
     * @return the shuttle removed from inventory, or null if not found.
     */
    public Shuttle launch(Shuttle shuttle, int speed, int facing, int currentImpulse) {
        if (!inventory.remove(shuttle)) return null;
        shuttle.setSpeed(Math.min(speed, shuttle.getMaxSpeed()));
        shuttle.setFacing(facing);

        if (isLaunchTubeEligible(shuttle)) {
            // Prefer an available launch tube (J1.54)
            for (int i = 0; i < launchTubeCount; i++) {
                if (currentImpulse - lastTubeImpulse[i] >= LAUNCH_COOLDOWN) {
                    lastTubeImpulse[i] = currentImpulse;
                    return shuttle;
                }
            }
        }
        // Standard hatch
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
        // Reload single-shot fighter weapons on docking (R9.F4 / J4.834)
        if (shuttle instanceof Fighter) {
            for (Weapon w : shuttle.getWeapons().fetchAllWeapons()) {
                if (w instanceof FighterHellbore) ((FighterHellbore) w).reload();
            }
        }
        inventory.add(shuttle);
        return true;
    }

    // -------------------------------------------------------------------------
    // Factory: build shuttle from type string
    // -------------------------------------------------------------------------

    public static Shuttle buildShuttle(String type, String name) {
        Shuttle s;
        switch (type.toLowerCase()) {
            case "gas":     s = new GASShuttle();                  break;
            case "hts":     s = new HTSShuttle();                  break;
            case "suicide": s = new SuicideShuttle(new AdminShuttle()); break;
            case "scatterpack": {
                ScatterPack pack = new ScatterPack(new AdminShuttle());
                for (int i = 0; i < 4; i++)
                    pack.addDrone(new Drone(DroneType.TypeI));
                s = pack;
                break;
            }
            case "stinger1": s = new Stinger1();  break;
            case "stinger2": s = new Stinger2();  break;
            case "stingerh": s = new StingerH();  break;
            case "admin":
            default:         s = new AdminShuttle(); break;
        }
        s.setName(name);
        return s;
    }

    public Unit getOwner() { return owner; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * True if this shuttle may use a launch tube (J1.542).
     * Only standard fighters qualify — admin variants, HTS, GAS, etc. do not.
     */
    private static boolean isLaunchTubeEligible(Shuttle shuttle) {
        return shuttle instanceof Fighter;
    }
}
