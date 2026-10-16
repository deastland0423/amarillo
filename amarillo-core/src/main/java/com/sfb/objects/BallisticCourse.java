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
     * The ground base this weapon is aimed at, or null for an ordinary ballistic course.
     * <p>
     * P2.713 is the exception to F4.21's "will normally never explode": "weapons fired
     * ballistically at ground targets DO explode on impact." So the hex is not the end of the
     * journey — it is where the warhead goes off.
     * <p>
     * It is a reference to the BASE and not merely its hex, because a hex is not enough to say
     * which base is meant. A radius-zero planet holds all six faces' bases in its own hex, and
     * nothing about the position distinguishes them: see {@code Ship.planetFace}.
     */
    private final Unit groundTarget;

    /**
     * The adjacent hexes entered without ever entering the target hex. A SET, not a count:
     * F4.11 says "a third hex adjacent to the target hex", and a weapon that passes through
     * the same adjacent hex three times (which a tractor under F4.5 can arrange) has not
     * entered three of them.
     */
    private final Set<Location> adjacentEntered = new HashSet<>();

    public BallisticCourse(Location targetHex) {
        this(targetHex, null);
    }

    /**
     * A course aimed at a ground base (P2.713), which explodes against it on arrival rather
     * than evading the hex.
     */
    public BallisticCourse(Location targetHex, Unit groundTarget) {
        if (targetHex == null)
            throw new IllegalArgumentException("A ballistic course needs a target hex (F4.11)");
        this.targetHex = targetHex;
        this.groundTarget = groundTarget;
    }

    /** The ground base this weapon will detonate against (P2.713), or null. */
    public Unit getGroundTarget() {
        return groundTarget;
    }

    /** P2.713: this course ends in an explosion rather than an evasion. */
    public boolean isGroundAttack() {
        return groundTarget != null;
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

    // -------------------------------------------------------------------------
    // Saved game (see Game.exportState)
    // -------------------------------------------------------------------------

    /**
     * This course, for a saved game.
     *
     * {@code adjacentEntered} is saved as the HEXES and not as a count, because F4.11 counts
     * DISTINCT hexes: re-entering a hex already visited does not advance the weapon towards
     * detonation. Restoring a bare count of two would let the next re-entry of an already-counted
     * hex finish the job, which the rule does not allow.
     *
     * <p>The ground target is written as a NAME. It is final on this class, so a restored course
     * has to be constructed with the unit already resolved — which is why seekers are revived in
     * the second pass of {@code Game.applyState}, once every unit exists.
     */
    public java.util.Map<String, Object> exportState() {
        java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("targetX", targetHex.getX());
        state.put("targetY", targetHex.getY());
        state.put("evading", evading);
        state.put("groundTarget", groundTarget == null ? null : groundTarget.getName());

        java.util.List<java.util.Map<String, Object>> entered = new java.util.ArrayList<>();
        for (Location hex : adjacentEntered) {
            java.util.Map<String, Object> one = new java.util.LinkedHashMap<>();
            one.put("x", hex.getX());
            one.put("y", hex.getY());
            entered.add(one);
        }
        state.put("adjacentEntered", entered);
        return state;
    }

    /**
     * Rebuild a course from a save. Returns null when the state does not describe one.
     *
     * @param groundTarget the unit named by {@code groundTarget}, already resolved by the caller
     */
    public static BallisticCourse fromState(java.util.Map<String, Object> state,
            Unit groundTarget) {
        if (state == null)
            return null;
        if (!(state.get("targetX") instanceof Number x)
                || !(state.get("targetY") instanceof Number y))
            return null;

        BallisticCourse course = new BallisticCourse(
                new Location(x.intValue(), y.intValue()), groundTarget);
        course.evading = com.sfb.systemgroups.StateIo.flag(state, "evading", false);
        if (state.get("adjacentEntered") instanceof java.util.List<?> entered)
            for (Object item : entered)
                if (item instanceof java.util.Map<?, ?> raw
                        && raw.get("x") instanceof Number hx
                        && raw.get("y") instanceof Number hy)
                    course.adjacentEntered.add(new Location(hx.intValue(), hy.intValue()));
        return course;
    }
}
