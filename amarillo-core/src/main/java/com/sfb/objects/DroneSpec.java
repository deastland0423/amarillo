package com.sfb.objects;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public record DroneSpec(DroneFrame frame, Propulsion propulsion,
                       List<PayloadModule> payload, Set<FrameAddition> additions) {

    public DroneSpec {
        if (frame == null || propulsion == null)
            throw new IllegalArgumentException("a drone needs a frame and a propulsion section");
        payload = payload == null ? List.of() : List.copyOf(payload);
        additions = additions == null ? Set.of() : Set.copyOf(additions);

        // FD10.52 prints a dash rather than a price where an engine is not made for a
        // frame: there is no speed-8 dogfight drone (FD2.227).
        if (!propulsion.fits(frame))
            throw new IllegalArgumentException(
                    propulsion + " is not made for a " + frame + " drone (FD10.52)");

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
        return new DroneSpec(frame, propulsion, List.of(modules), Set.of());
    }

    /** The same, with something bolted to the frame (FD10.53). */
    public static DroneSpec of(DroneFrame frame, Propulsion propulsion,
            Set<FrameAddition> additions, PayloadModule... modules) {
        return new DroneSpec(frame, propulsion, List.of(modules), additions);
    }

    /** A drone on a frame that carries what it carries — the type-VI (FD10.21). */
    public static DroneSpec fixed(DroneFrame frame, Propulsion propulsion) {
        return new DroneSpec(frame, propulsion, List.of(), Set.of());
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

    /**
     * FD10.6: how hard this drone is to obtain in a given year — the most restrictive of its
     * parts.
     *
     * <blockquote>FD10.65: "A type-I drone with medium speed in Y166 (restricted) and a
     * Swordfish module (limited) would be a limited availability drone."</blockquote>
     *
     * <p>FD10.641 says the same for frame modifications: "Frame modifications, e.g., speed
     * upgrades, external armor, extended range, ATG guidance, or type-III frames... apply to
     * the entire drone unless a warhead is of a more restricted type."
     *
     * <h2>What this does NOT do</h2>
     * FD10.65's third example splits a two-space drone across two tiers — "a type-IV fast speed
     * drone in Y179 (restricted) with an explosive module (general) and a swordfish module
     * (limited) is a space of restricted (the speed upgrade) and a space of limited (the
     * swordfish module)". That is a per-SPACE accounting and only matters once something counts
     * spaces against a cap, so it belongs with the caps rather than here. This answers the
     * simpler question the examples above ask: what tier is this drone.
     *
     * <p>Type-VI drones are excluded from the calculation altogether (FD10.643), which this
     * cannot express either — a tier is a property of the drone, and being left out of the sum
     * is a property of the sum.
     */
    public Availability availabilityIn(int year) {
        Availability worst = frame.availability;
        worst = Availability.moreRestrictive(worst, propulsion.availabilityIn(year));
        for (PayloadModule m : allModules())
            worst = Availability.moreRestrictive(worst, m.availability);
        for (FrameAddition a : additions)
            worst = Availability.moreRestrictive(worst, a.availability);
        return worst;
    }

    /**
     * FD10.641: this drone's warhead spaces, split across the tiers they count against.
     *
     * <blockquote>"Frame modifications, e.g., speed upgrades, external armor, extended range,
     * ATG guidance, or type-III frames... apply to the entire drone <b>unless a warhead is of a
     * more restricted type</b>. A type-IV drone with Medium Speed in Y166 counts as two spaces
     * of Restricted Availability even if both of its payload spaces are General Availability
     * items. Such a Restricted Availability drone might have a Limited Availability module in
     * one of its payload spaces, in which case it would be considered as <b>one space of
     * Restricted and one space of Limited</b> availability."</blockquote>
     *
     * <p>So a drone is not one tier but a bill of them: the frame modifications set a floor that
     * applies to every space, and any module harder to obtain than that floor raises its own
     * space above it. {@link #availabilityIn} answers the simpler question — what tier is this
     * drone — which FD10.65's first example asks and a ship-wide count does not.
     *
     * <h2>One combination this gets wrong, and cannot yet get right</h2>
     * FD10.641 ends with a carve-out that inverts the whole scheme: "a type-III frame (Limited
     * Availability) to which the extended range frame modification (General Availability) is
     * added becomes a two-space <b>Restricted</b> Availability drone (FD10.24)". Limited plus
     * General making Restricted is less restrictive than either input, so it is a named
     * exception rather than anything derivable. It needs the type-III-XX frame change FD10.24
     * describes, which is unbuilt, so the combination cannot be constructed and the exception
     * is recorded rather than implemented.
     */
    public Map<Availability, Double> spacesByAvailability(int year) {
        Availability floor = frame.availability;
        floor = Availability.moreRestrictive(floor, propulsion.availabilityIn(year));
        for (FrameAddition a : additions)
            floor = Availability.moreRestrictive(floor, a.availability);

        Map<Availability, Double> out = new EnumMap<>(Availability.class);
        for (PayloadModule m : allModules())
            out.merge(Availability.moreRestrictive(floor, m.availability), m.spaces, Double::sum);
        return out;
    }

    /**
     * Whether this drone can exist at all in a given year, which is a different question from
     * how obtainable it is. FD10.651 dates each engine's invention; a drone built around one
     * that has not been invented is not Limited, it is impossible.
     */
    public boolean existsIn(int year) {
        return propulsion.existsIn(year);
    }

    /**
     * What this drone costs to buy outright, from FD10.5's three charts: the propulsion section
     * at this frame's size (FD10.52), every payload module at its own size (FD10.51), and
     * whatever is bolted to the frame (FD10.53). The frame itself is free (FD10.22).
     *
     * <h2>This is the OPEN-MARKET price, not the Commander's Option charge</h2>
     * <blockquote>FD10.54: "There is a chance for confusion between these costs and those shown
     * in (FD2.1). The costs here are for an extra drone ('bought on the open market' so to
     * speak), while the costs in (FD2.1) are the costs of TRADING IN type-I-slow drones for the
     * improved drones."</blockquote>
     *
     * <p>A ship's BPV already includes type-I-slow drones (FD2.22), so what a player pays at
     * the COI is the DIFFERENCE between this and the baseline those drones would have cost.
     * {@code CoiLoadout} does that subtraction; this only knows the absolute figure.
     *
     * <p>FD2.11's own table of extra-drone costs is the oracle: type-I 1.0, type-II 1.5,
     * type-III 2.0, type-IV 2.0, type-V 2.5, type-VI 0.5. All six fall out of these charts, and
     * so do its six replacement costs — including the one that looks wrong, where trading a
     * type-I for a type-III costs 0.5 rather than 1.0 because that price buys the speed-8
     * type-III FD10.54 calls a Basic Set erratum.
     */
    public double pointCost() {
        double total = propulsion.costOn(frame);
        for (PayloadModule m : allModules())
            total += m.cost;
        for (FrameAddition a : additions)
            total += a.cost;
        return total;
    }
}
