package com.sfb.objects;

import java.util.List;

/**
 * A drone as FD10.2 describes one: a frame, a propulsion section and a payload.
 *
 * <blockquote>FD10.22: "Under the point cost system, the frame is effectively free... The
 * propulsion (speed) and payload (e.g., warhead) modules, extended range, and any advanced
 * guidance cost points."</blockquote>
 *
 * <h2>What this is for, and what it is not for yet</h2>
 * It exists so {@link DroneType}'s numbers can be DERIVED from what a drone is made of instead
 * of written down fourteen times. Three of the enum's nine fields come from here —
 * {@code rack} from the frame, {@code hull} from the frame PLUS its armor (FD12.21), and
 * {@code damage} from the payload modules — and the rest stay declared, because nothing
 * in the rules derives them:
 *
 * <ul>
 *   <li>{@code endurance} is a trait of the drone family (96 for a type-I, 64 for the
 *       transitional type-II, 800 for a type-III, 32 for a dogfight drone) and no clause
 *       computes it from frame or propulsion.</li>
 *   <li>{@code selfGuiding} and {@code warpSeeker} describe the guidance package the frame
 *       "includes" (FD10.21) without the rules giving that package a size or a price here.</li>
 * </ul>
 *
 * <p>A model that derived everything would be inventing rules to do it. This derives what the
 * rulebook derives and leaves the rest stated, which is the same line {@code RefitResolver}
 * draws between a refit's computed cost and its declared name.
 *
 * <h2>Not yet: cost, availability, and most modules</h2>
 * FD2.22's surcharges, the Annex's limited/restricted/general availability ladder, and
 * FD10.42-FD10.48's probe, ECM, armor and null modules are each their own work. This class is
 * the shape they will hang off, and carries none of them.
 */
public record DroneSpec(DroneFrame frame, Propulsion propulsion, List<PayloadModule> payload) {

    public DroneSpec {
        if (frame == null || propulsion == null)
            throw new IllegalArgumentException("a drone needs a frame and a propulsion section");
        payload = payload == null ? List.of() : List.copyOf(payload);

        // FD10.21: the half-space frame "cannot be modified"; FD2.51 says the same of the
        // type-VI. Offering it a payload is a mistake in the code rather than an illegal
        // loadout, so it throws rather than being quietly dropped.
        if (!frame.modular && !payload.isEmpty())
            throw new IllegalArgumentException(
                    frame + " takes no payload modules (FD10.21): only the speed can be varied");

        double used = 0;
        for (PayloadModule m : payload)
            used += m.spaces;
        if (used > frame.payloadSpaces)
            throw new IllegalArgumentException(
                    "payload of " + used + " spaces exceeds " + frame + "'s "
                            + frame.payloadSpaces + " (FD10.4)");
    }

    /** A drone built on a frame that chooses its own payload. */
    public static DroneSpec of(DroneFrame frame, Propulsion propulsion, PayloadModule... modules) {
        return new DroneSpec(frame, propulsion, List.of(modules));
    }

    /** A drone on a frame that carries what it carries — the type-VI (FD10.21). */
    public static DroneSpec fixed(DroneFrame frame, Propulsion propulsion) {
        return new DroneSpec(frame, propulsion, List.of());
    }

    /** Everything aboard: the chosen modules, or the frame's own on a non-modular frame. */
    public List<PayloadModule> allModules() {
        return frame.modular ? payload : frame.fixedPayload;
    }

    /**
     * FD10.41: "All of the explosive modules on a given drone are combined into a single
     * explosion." So this is a sum, and a drone carrying nothing explodes for nothing — which
     * is FD10.48's null drone, when it comes.
     *
     * <p>For a dogfight drone this is the NOMINAL warhead only. FD2.54 makes the real figure
     * depend on the target's size class, and that rule lives in {@code Drone.impact(Unit)},
     * where it was already implemented before this model existed.
     */
    public int warheadStrength() {
        int total = 0;
        for (PayloadModule m : allModules())
            total += m.strength;
        return total;
    }

    /** Spaces this drone takes in a rack (FD3.0). */
    public double rackSpaces() {
        return frame.rackSpaces;
    }

    /**
     * Hull boxes the drone has as a target: the frame's own, plus whatever armor adds.
     *
     * <blockquote>FD12.21: "The effect of an armor module is to increase the number of damage
     * points required to destroy the drone. The 1/2-PS module adds two damage points; the
     * one-PS armor module adds four damage points."</blockquote>
     *
     * <p>Not a property of the frame, though it looks like one: every drone in the game today
     * has exactly its frame's hull, because none carries armor. FD12.12's own example — and the
     * owner's — is a type-IV with three 1/2-PS armor modules and a 1/2-PS explosive, four
     * half-spaces filling its two payload spaces exactly, which is six plus six: twelve hull
     * behind a strength-six warhead.
     */
    public int hull() {
        int total = frame.hull;
        for (PayloadModule m : allModules())
            total += m.hullBonus;
        return total;
    }

    /** Hexes per turn (FD2.1). */
    public int speed() {
        return propulsion.speed;
    }
}
