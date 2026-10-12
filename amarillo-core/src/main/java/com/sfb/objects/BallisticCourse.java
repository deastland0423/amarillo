package com.sfb.objects;

import java.util.HashSet;
import java.util.Set;

import com.sfb.properties.Location;
import com.sfb.utilities.MapUtils;

/**
 * A seeking weapon's ballistic course (F4.0): a target HEX rather than a target unit.
 * <p>
 * "Under certain conditions, a seeking weapon can be set on a ballistic course. That is, it is
 * not aimed at a target unit but in a specific direction or at a specific (vacant) point in
 * space." The weapon pursues the hex by the most nearly direct route (F2.2), and on reaching it
 * EVADES it (C1.3223) rather than stopping or exploding.
 * <p>
 * This object exists because the evasion is a small state machine and it would otherwise have
 * been written four times — once per seeker kind — inside {@code SeekerMover}. A seeker holds
 * one of these, or null when it is pursuing a unit in the ordinary way.
 *
 * @see Seeker#destination()
 */
public class BallisticCourse {

    private final Location targetHex;
    private boolean evading;

    /**
     * The adjacent hexes entered without ever entering the target hex. A SET, not a count:
     * F4.11 says "a third hex adjacent to the target hex", and a weapon that passes through
     * the same adjacent hex three times (which a tractor under F4.5 can arrange) has not
     * entered three of them.
     */
    private final Set<Location> adjacentEntered = new HashSet<>();

    public BallisticCourse(Location targetHex) {
        if (targetHex == null)
            throw new IllegalArgumentException("A ballistic course needs a target hex (F4.11)");
        this.targetHex = targetHex;
    }

    public Location getTargetHex() {
        return targetHex;
    }

    /**
     * True once the weapon has reached its hex (or given up on reaching it) and has switched
     * to evasion. An evading weapon no longer steers: see {@link #isEvading()}'s callers in
     * {@code SeekerMover}.
     */
    public boolean isEvading() {
        return evading;
    }

    /**
     * Record a hex the weapon has just entered, and decide whether that begins the evasion
     * (F4.11).
     * <p>
     * Two ways it begins. The weapon enters the target hex — the ordinary case — or it
     * "enters a third hex adjacent to the target hex without having entered the target hex",
     * which is the anti-loop clause: a weapon that can never quite land on the hex would
     * otherwise circle it until its endurance ran out.
     *
     * @return true if this entry started the evasion, so the caller can log it once
     */
    public boolean noteHexEntered(Location hex) {
        if (evading || hex == null)
            return false;
        if (hex.equals(targetHex)) {
            evading = true;
            return true;
        }
        if (MapUtils.getRange(hex, targetHex) == 1) {
            adjacentEntered.add(hex);
            if (adjacentEntered.size() >= 3) {
                evading = true;
                return true;
            }
        }
        return false;
    }

    /** How many distinct hexes adjacent to the target have been entered (F4.11). */
    public int getAdjacentHexesEntered() {
        return adjacentEntered.size();
    }
}
