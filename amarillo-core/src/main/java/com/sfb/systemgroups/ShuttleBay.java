package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sfb.objects.shuttles.Aas;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Haas;
import com.sfb.objects.shuttles.Haas_E;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.GASShuttle;
import com.sfb.objects.shuttles.HTSShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.objects.shuttles.Stinger2;
import com.sfb.objects.shuttles.StingerH;
import com.sfb.objects.Unit;
import com.sfb.weapons.FighterHellbore;
import com.sfb.weapons.Weapon;

/**
 * A single shuttle bay on a ship.
 *
 * Each bay has a fixed number of spaces (slots). Spaces can hold a shuttle,
 * a bay-mounted drone rack (D12.3), or nothing. Spaces can be permanently
 * destroyed by DAC hits. Chain reactions are confined to a single bay (D12.112).
 *
 * Each bay has a standard hatch (one launch per 2 impulses) and optionally
 * one or more launch tubes (J1.54). Each tube has its own 2-impulse cooldown
 * and can only launch fighters. Recovery always uses the standard hatch (J1.541).
 */
public class ShuttleBay {

    private static final int LAUNCH_COOLDOWN = 2;

    private final Unit owner;
    private final List<ShuttleSpace> spaces = new ArrayList<>();
    private int lastLaunchImpulse = -LAUNCH_COOLDOWN;

    // Launch tubes (J1.54) — each has its own cooldown
    private int launchTubeCount = 0;
    private int[] lastTubeImpulse = new int[0];

    public ShuttleBay(Unit owner) {
        this.owner = owner;
    }

    // -------------------------------------------------------------------------
    // Space management
    // -------------------------------------------------------------------------

    public void addSpace(ShuttleSpace space) {
        spaces.add(space);
    }

    /** Add an empty space (no shuttle). */
    public void addEmptySpace() {
        spaces.add(new ShuttleSpace());
    }

    public List<ShuttleSpace> getSpaces() {
        return spaces;
    }

    /** Total spaces in the bay (fixed at construction; never changes). */
    public int getTotalSpaces() {
        return spaces.size();
    }

    /** Spaces permanently destroyed by DAC hits. */
    public int getDestroyedSpaces() {
        return (int) spaces.stream().filter(ShuttleSpace::isDestroyed).count();
    }

    /** Spaces that are empty (not destroyed, not occupied). */
    public int getEmptySpaceCount() {
        return (int) spaces.stream().filter(ShuttleSpace::isEmpty).count();
    }

    /** Shuttles currently in inventory (launched shuttles are absent). */
    public List<Shuttle> getInventory() {
        List<Shuttle> inv = new ArrayList<>();
        for (ShuttleSpace s : spaces)
            if (s.getShuttle() != null)
                inv.add(s.getShuttle());
        return inv;
    }

    /** Original total spaces — for DAC box tracking. */
    public int getCapacity() {
        return spaces.size();
    }

    /** Remaining undestroyed spaces — for DAC remaining-box tracking. */
    public int getRemainingSpaces() {
        return (int) spaces.stream().filter(s -> !s.isDestroyed()).count();
    }

    // -------------------------------------------------------------------------
    // Shuttle placement (used during init and landing)
    // -------------------------------------------------------------------------

    /** Add a shuttle into the first available empty space (or a new space if none). */
    public void addShuttle(Shuttle shuttle) {
        for (ShuttleSpace space : spaces) {
            if (space.isEmpty()) {
                space.setShuttle(shuttle);
                return;
            }
        }
        // No empty space — add a new one (should only happen during init)
        spaces.add(new ShuttleSpace(shuttle));
    }

    /**
     * Replace one shuttle in-bay with another (e.g. admin → ScatterPack).
     * Uses identity comparison so the correct space is updated even if two
     * shuttles have the same name. Returns true if the old shuttle was found.
     */
    public boolean replaceShuttle(Shuttle oldShuttle, Shuttle newShuttle) {
        for (ShuttleSpace space : spaces) {
            if (space.getShuttle() == oldShuttle) {
                space.setShuttle(newShuttle);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Launch tubes
    // -------------------------------------------------------------------------

    public void setLaunchTubeCount(int n) {
        launchTubeCount = n;
        lastTubeImpulse = new int[n];
        Arrays.fill(lastTubeImpulse, -LAUNCH_COOLDOWN);
    }

    public int getLaunchTubeCount() {
        return launchTubeCount;
    }

    public int getAvailableTubeCount(int currentImpulse) {
        int count = 0;
        for (int last : lastTubeImpulse)
            if (currentImpulse - last >= LAUNCH_COOLDOWN)
                count++;
        return count;
    }

    // -------------------------------------------------------------------------
    // Launch
    // -------------------------------------------------------------------------

    public boolean canLaunch(int currentImpulse) {
        return (currentImpulse - lastLaunchImpulse) >= LAUNCH_COOLDOWN;
    }

    public boolean canLaunch(Shuttle shuttle, int currentImpulse) {
        if (isLaunchTubeEligible(shuttle) && getAvailableTubeCount(currentImpulse) > 0)
            return true;
        return canLaunch(currentImpulse);
    }

    public void markUsed(int currentImpulse) {
        lastLaunchImpulse = currentImpulse;
    }

    /**
     * Launch the given shuttle. Removes it from its space (space stays, now empty).
     * Returns the shuttle, or null if not found in any space.
     */
    public Shuttle launch(Shuttle shuttle, int speed, int facing, int currentImpulse) {
        ShuttleSpace space = findSpace(shuttle);
        if (space == null) return null;

        space.setShuttle(null);
        shuttle.setSpeed(Math.min(speed, shuttle.getMaxSpeed()));
        shuttle.setFacing(facing);

        if (isLaunchTubeEligible(shuttle)) {
            for (int i = 0; i < launchTubeCount; i++) {
                if (currentImpulse - lastTubeImpulse[i] >= LAUNCH_COOLDOWN) {
                    lastTubeImpulse[i] = currentImpulse;
                    return shuttle;
                }
            }
        }
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
        if (shuttle instanceof Fighter) {
            for (Weapon w : shuttle.getWeapons().fetchAllWeapons()) {
                if (w instanceof FighterHellbore)
                    ((FighterHellbore) w).reload();
            }
        }
        // Place in first empty undestroyed space
        for (ShuttleSpace space : spaces) {
            if (space.isEmpty()) {
                space.setShuttle(shuttle);
                return true;
            }
        }
        return false; // no room
    }

    // -------------------------------------------------------------------------
    // DAC damage
    // -------------------------------------------------------------------------

    /**
     * Destroy a specific space by index. Returns the shuttle that was in the
     * space (null if empty), so the caller can check isArmed() for chain reaction.
     */
    public Shuttle destroySpace(int spaceIndex) {
        if (spaceIndex < 0 || spaceIndex >= spaces.size()) return null;
        return spaces.get(spaceIndex).destroy();
    }

    /**
     * Find the space containing the given shuttle. Returns null if not found.
     */
    public ShuttleSpace findSpace(Shuttle shuttle) {
        for (ShuttleSpace space : spaces)
            if (space.getShuttle() == shuttle)
                return space;
        return null;
    }

    /**
     * Index of the given space, or -1 if not in this bay.
     */
    public int indexOf(ShuttleSpace space) {
        return spaces.indexOf(space);
    }

    // -------------------------------------------------------------------------
    // Factory: build shuttle from type string
    // -------------------------------------------------------------------------

    public static Shuttle buildShuttle(String type, String name) {
        Shuttle s;
        switch (type.toLowerCase()) {
            case "gas":
                s = new GASShuttle();
                break;
            case "hts":
                s = new HTSShuttle();
                break;
            case "stinger1":
                s = new Stinger1();
                break;
            case "stinger2":
                s = new Stinger2();
                break;
            case "stingerh":
                s = new StingerH();
                break;
            case "aas":
                s = new Aas();
                break;
            case "haas":
                s = new Haas();
                break;
            case "haas_e":
                s = new Haas_E();
                break;
            case "admin":
            default:
                s = new AdminShuttle();
                break;
        }
        s.setName(name);
        return s;
    }

    public Unit getOwner() {
        return owner;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isLaunchTubeEligible(Shuttle shuttle) {
        return shuttle instanceof Fighter;
    }
}
