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
                       List<PayloadModule> payload, Set<FrameAddition> additions,
                       Propulsion submunitionPropulsion) {

    public DroneSpec {
        if (frame == null || propulsion == null)
            throw new IllegalArgumentException("a drone needs a frame and a propulsion section");
        payload = payload == null ? List.of() : List.copyOf(payload);
        additions = additions == null ? Set.of() : Set.copyOf(additions);

        // FD10.24: a type-III given extended range IS a type-III-XX, so resolve that before
        // anything measures the drone. Naming the frame directly is allowed, but only with the
        // two additions that would have produced it -- otherwise the second rack space and the
        // long legs would arrive without their half-point surcharges being paid.
        if (frame == DroneFrame.TYPE_III_XX && !isTypeIiiXx(additions))
            throw new IllegalArgumentException("a " + frame + " is a type-III frame with"
                    + " extended range, and must declare both (FD10.24)");
        DroneFrame built = effectiveFrame(frame, additions);

        // FD10.52 prints a dash rather than a price where an engine is not made for a
        // frame: there is no speed-8 dogfight drone (FD2.227).
        if (!propulsion.fits(built))
            throw new IllegalArgumentException(
                    propulsion + " is not made for a " + built + " drone (FD10.52)");

        // FD10.21: the half-space frame "cannot be modified"; FD2.51 says the same of the
        // type-VI. Offering it a payload is a mistake in the code rather than an illegal
        // loadout, so it throws rather than being quietly dropped.
        if (!frame.modular && !payload.isEmpty())
            throw new IllegalArgumentException(
                    frame + " takes no payload modules (FD10.21): only the speed can be varied");

        // FD12.131: "No more than one external armor module can be added to any one drone.
        // External armor cannot be placed on a drone equipped with internal armor."
        long external = additions.stream().filter(FrameAddition::isExternalArmor).count();
        if (external > 1)
            throw new IllegalArgumentException(
                    "a drone may carry only one external armor module (FD12.131)");
        if (external > 0) {
            if (payload.stream().anyMatch(PayloadModule::isArmor))
                throw new IllegalArgumentException(
                        "external armor cannot go on a drone with internal armor (FD12.131)");
            // FD12.132's chart has no half-space row, and FD10.21 says only the speed can be
            // varied on a type-VI. Nothing in the rules arms one.
            if (frame == DroneFrame.HALF_SPACE)
                throw new IllegalArgumentException(
                        frame + " takes no armor: only the speed can be varied (FD10.21)");
        }

        // FD2.222: "Any type of drone, EXCEPT a dogfight drone (FD2.5), can be modified to
        // have double endurance." The only named exception in the rule, and the second time
        // FD10.21's "only the speed can be varied" is said from the other end.
        if (additions.contains(FrameAddition.EXTENDED_RANGE) && frame == DroneFrame.HALF_SPACE)
            throw new IllegalArgumentException(
                    "a dogfight drone cannot be given extended range (FD2.222)");

        // Who may be fitted with active terminal guidance, and who already has guidance of
        // their own. Two rules, both phrased as a drone that cannot be given ATG because it
        // is already self-guiding by another route.
        if (additions.contains(FrameAddition.ACTIVE_TERMINAL_GUIDANCE)) {
            // FD5.22: "Any drone other than a type-VI may be equipped with ATG", and FD5.14
            // says why -- energy-seeking guidance "is used only on 1/2-space dogfight drones".
            if (frame == DroneFrame.HALF_SPACE)
                throw new IllegalArgumentException(
                        "a type-VI already guides itself and takes no ATG (FD5.22, FD5.14)");
            // FD10.23: "ATG cannot be added to type-III drone, which already has a special
            // type of guidance." FD2.221 says the same: "Type-III drones already have ATG".
            if (additions.contains(FrameAddition.TYPE_III_GUIDANCE))
                throw new IllegalArgumentException(
                        "a type-III already has a guidance system of its own (FD10.23)");
        }

        // FD10.442: "No more than one such module can be placed on any drone."
        long ecm = payload.stream().filter(m -> m == PayloadModule.ECM).count();
        if (ecm > 1)
            throw new IllegalArgumentException(
                    "a drone may carry only one ECM module (FD10.442)");

        // FD10.42: "no more than one can be placed on a given drone". Said of the probe in its
        // own words rather than folded in with the ECM limit above, because the two are
        // separate clauses that happen to agree and could stop agreeing.
        if (payload.stream().filter(m -> m == PayloadModule.PROBE).count() > 1)
            throw new IllegalArgumentException(
                    "a drone may carry only one probe module (FD10.42)");

        // FD10.443: "The ECM module can... be combined with any other module in the game except
        // an (internal or external) armor module. ECM drones will not work with armor modules."
        if (ecm > 0 && (payload.stream().anyMatch(PayloadModule::isArmor) || external > 0))
            throw new IllegalArgumentException(
                    "an ECM module cannot share a drone with armor (FD10.443)");

        // FD10.432: "No more than one MW module can be on a single drone".
        if (payload.stream().filter(m -> m.submunitions > 0).count() > 1)
            throw new IllegalArgumentException(
                    "a drone may carry only one multi-warhead module (FD10.432)");

        // FD8.24: the submunitions have an engine of their own, and it is bought separately.
        //
        // "The speed of the submunition will, like the bus vehicle, be the appropriate speed
        // for the year in which the scenario is set. It could be a higher speed if that was
        // available as a restricted item (FD10.65). Note that the cost of speed upgrades for
        // the submunitions is paid separately from and in addition to the cost of the bus
        // vehicle; see (S3.2). This makes MW drones extremely expensive."
        //
        // So this is a second propulsion choice rather than a property of the module: FD8.11
        // buys "five type-VI drones (any speed available for the year)". ONE field and not
        // three or five, because FD8.44 requires them to agree -- "All submunitions on a given
        // MW drone MUST receive the same speed upgrade. If one is to be made a fast type-VI,
        // all must be made fast." Making that unsayable beats checking for it.
        //
        // Nothing links it to the bus's own speed. The worked examples in FD8.41/.42/.45 pair
        // them ("fast-speed frame with fast-speed submunitions"), but that is a player buying
        // both, not a rule, and FD8.24 explicitly allows the submunitions to be faster.
        List<PayloadModule> aboard = frame.modular ? payload : frame.fixedPayload;
        boolean carriesSubmunitions = aboard.stream().anyMatch(m -> m.submunitions > 0);
        if (submunitionPropulsion != null && !carriesSubmunitions)
            throw new IllegalArgumentException(
                    "only a drone carrying submunitions has a submunition speed (FD8.21)");
        if (carriesSubmunitions) {
            // Nothing bought means they fly at the type-VI's own baseline, which FD2.227 puts
            // at 12 rather than 8. That is why FD8.45's "slow speed (both bus and
            // submunitions)" type-I-MW pays nothing at all for its submunitions, and why
            // speed 8 is not a choice here: FD10.52 prints a dash in that column.
            if (submunitionPropulsion == null)
                submunitionPropulsion = Propulsion.SPEED_12;
            if (!submunitionPropulsion.fits(DroneFrame.HALF_SPACE))
                throw new IllegalArgumentException(submunitionPropulsion
                        + " is not made for a type-VI submunition (FD10.52, FD2.227)");
        }

        double used = 0;
        for (PayloadModule m : payload)
            used += m.spaces;
        if (used > built.payloadSpaces)
            throw new IllegalArgumentException(
                    "payload of " + used + " spaces exceeds " + built + "'s "
                            + built.payloadSpaces + " (FD10.4)");

        // FD10.402: some modules "can only be placed in the forward space", and FD10.432,
        // FD11.11, FD14.11 and FD15.11 each say it of their own.
        //
        // WHICH space a module occupies is not tracked, by design. No rule asks where a module
        // IS; they ask that it BE forward, so it is enough to refuse a drone whose forward-only
        // modules could not all fit in front. A frame has exactly one forward space, EXCEPT
        // that a module spanning the entire payload is forward by occupying everything — which
        // is why FD10.432 writes "forward payload space(s)" with the plural.
        List<PayloadModule> forwardOnly = payload.stream()
                .filter(m -> m.requiresForward).toList();
        double forwardSpaces = forwardOnly.stream().mapToDouble(m -> m.spaces).sum();
        if (forwardSpaces > 1.0) {
            boolean spansWholePayload = forwardOnly.size() == 1
                    && forwardOnly.get(0).spaces == built.payloadSpaces;
            if (!spansWholePayload)
                throw new IllegalArgumentException(
                        "more than one payload space of forward-only modules, which cannot all"
                        + " be forward (FD10.402)");
        }

    }

    /** A drone built on a frame that chooses its own payload. */
    public static DroneSpec of(DroneFrame frame, Propulsion propulsion, PayloadModule... modules) {
        return new DroneSpec(frame, propulsion, List.of(modules), Set.of(), null);
    }

    /** The same, with something bolted to the frame (FD10.53). */
    public static DroneSpec of(DroneFrame frame, Propulsion propulsion,
            Set<FrameAddition> additions, PayloadModule... modules) {
        return new DroneSpec(frame, propulsion, List.of(modules), additions, null);
    }

    /** A drone on a frame that carries what it carries — the type-VI (FD10.21). */
    public static DroneSpec fixed(DroneFrame frame, Propulsion propulsion) {
        return new DroneSpec(frame, propulsion, List.of(), Set.of(), null);
    }

    /**
     * The same drone with its submunitions upgraded — FD8.11's "five type-VI drones (any
     * speed available for the year)", chosen rather than taken as they come.
     *
     * <p>Separate from the factories because it is the one part of a drone that is neither a
     * module nor an addition: it buys engines for drones that are themselves the payload.
     */
    public DroneSpec withSubmunitionSpeed(Propulsion submunitionSpeed) {
        return new DroneSpec(frame, propulsion, payload, additions, submunitionSpeed);
    }

    /** Everything aboard: the chosen modules, or the frame's own on a non-modular frame. */
    public List<PayloadModule> allModules() {
        return frame.modular ? payload : frame.fixedPayload;
    }

    /**
     * The frame this drone actually has, which is not always the one it was built on.
     *
     * <blockquote>FD10.24: "Type-III drones are one-space drones, but when given extended range
     * (FD2.222), they <b>become</b> type-III-XX, which are two-space drones with one payload
     * space."</blockquote>
     *
     * <p>Every measurement of the drone -- its rack footprint, its payload capacity, its hull,
     * and which column of FD10.52 prices its engine -- reads this rather than the declared
     * frame. A caller asks for a type-III with extended range, which is how the rules put it,
     * and gets the frame that transformation produces.
     */
    public DroneFrame effectiveFrame() {
        return effectiveFrame(frame, additions);
    }

    private static DroneFrame effectiveFrame(DroneFrame frame, Set<FrameAddition> additions) {
        if (frame == DroneFrame.ONE_SPACE && isTypeIiiXx(additions))
            return DroneFrame.TYPE_III_XX;
        return frame;
    }

    private static boolean isTypeIiiXx(Set<FrameAddition> additions) {
        return additions.contains(FrameAddition.TYPE_III_GUIDANCE)
                && additions.contains(FrameAddition.EXTENDED_RANGE);
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
        return effectiveFrame().rackSpaces;     // FD10.24: a type-III-XX takes two
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
        int total = effectiveFrame().hull;
        for (PayloadModule m : allModules())
            total += m.hullBonus;
        for (FrameAddition a : additions)
            total += a.hullBonus;       // FD12.21, for external armor
        return total;
    }

    /**
     * Hexes per turn (FD2.1) — the propulsion section's, less whatever external armor costs.
     *
     * <p>FD12.132 is why this is not simply {@code propulsion.speed}: external armor "reduces
     * the speed of the drone according to the chart below", and the chart is steep. A fast
     * one-space drone with a full external module drops from 32 to 12.
     */
    public int speed() {
        for (FrameAddition a : additions)
            if (a.isExternalArmor())
                return a.speedWith(effectiveFrame(), propulsion);
        return propulsion.speed;
    }

    /**
     * Whether FD10.641's carve-out has already spoken for this addition's availability.
     *
     * <blockquote>"A type-III frame (Limited Availability) to which the extended range frame
     * modification (General Availability) is added becomes a two-space <b>Restricted</b>
     * Availability drone (FD10.24)."</blockquote>
     *
     * <p>Restricted is less restrictive than Limited, so this is not a combination of the two
     * tiers -- it REPLACES them. Folding either addition back in afterwards would drag the drone
     * to Limited and undo the exception, so the frame's own tier stands for both.
     */
    private boolean supersededByTheFrame(FrameAddition a) {
        return effectiveFrame() == DroneFrame.TYPE_III_XX
                && (a == FrameAddition.TYPE_III_GUIDANCE || a == FrameAddition.EXTENDED_RANGE);
    }

    /**
     * Whether this drone is a type-III-XX: FD10.24's "type-III drones... when given extended
     * range (FD2.222), they become type-III-XX".
     *
     * <p>Asked of the ADDITIONS rather than declared, because a type-III-XX is not something a
     * player buys. It is what a type-III turns into, and the type-III itself is the
     * {@code TYPE_III_GUIDANCE} surcharge FD10.22 charges half a point for.
     */
    public boolean isTypeIiiXx() {
        return effectiveFrame() == DroneFrame.TYPE_III_XX;
    }

    /**
     * FD2.222: "Extended range costs 1/2 point per drone and <b>doubles the endurance</b>."
     *
     * <p>Endurance is the one number a drone's composition did NOT derive when this model was
     * built, because nothing computed it: 96 impulses for a type-I, 64 for the transitional
     * type-II, 800 for a type-III, 32 for a dogfight drone are traits of the family. This rule
     * is the exception — it computes endurance from a frame modification — so the base figure
     * stays declared and the doubling happens here, which is why this takes the base as an
     * argument instead of reading one.
     *
     * <h2>The type-III-XX is STATED, not doubled</h2>
     * The same sentence goes on: "(Type-III drones become IIIXX with <b>an endurance of 100
     * turns</b> and size of two spaces)." A type-III's endurance is 25 turns — FD10.22 calls it
     * "standard 25-turn range" and Annex #2's FD2.1 chart prints 25 — so 100 turns is four
     * times the base, not twice it. The rulebook states the figure in the same breath as the
     * rule that would otherwise derive it, and a stated number wins.
     */
    public int enduranceFrom(int baseEndurance) {
        Integer stated = effectiveFrame().enduranceOverride;
        if (stated != null)
            return stated;
        if (!additions.contains(FrameAddition.EXTENDED_RANGE))
            return baseEndurance;
        return baseEndurance * 2;
    }

    /**
     * F3.42: whether this drone is CAPABLE of guiding itself — which is not the same question
     * as whether it is doing so right now.
     *
     * <blockquote>"Plasma torpedoes (FP4.0), drones with active terminal guidance (FD5.2), and
     * type-VI drones (FD5.1) are <b>capable of</b> guiding themselves if released from control
     * by the controlling unit."</blockquote>
     *
     * <p>F3.421 keeps the two apart: "A seeking weapon capable of self-guiding is assumed to be
     * <b>under the control of a guiding unit</b> until control over it has been released." So a
     * type-III handed from one ship to another is still a type-III; the capability is a
     * property of what the drone is made of, and nothing that happens in flight can take it
     * away. That is why this is derived here rather than stored on the drone.
     *
     * <p>Three routes to it, and the rules spell out all three:
     * <ul>
     *   <li>the half-space frame — FD5.11's energy seeker, which FD5.14 says "is used only on
     *       1/2-space dogfight drones";</li>
     *   <li>the ATG addition — FD5.2, bought for half a point (FD2.221);</li>
     *   <li>the type-III frame — FD10.23, "which already has a special type of guidance", and
     *       FD2.221, "Type-III drones already have ATG".</li>
     * </ul>
     */
    public boolean isSelfGuidingCapable() {
        if (effectiveFrame() == DroneFrame.HALF_SPACE)
            return true;
        return additions.contains(FrameAddition.ACTIVE_TERMINAL_GUIDANCE)
                || additions.contains(FrameAddition.TYPE_III_GUIDANCE);
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
     * <h2>FD10.66: the unit of account is the SPACE, not the module</h2>
     * <blockquote>"Drones with a payload of two types (e.g., a 1/2 space of armor and a 1/2
     * space of explosive), are considered to be <b>one space of the more restricted type</b>
     * for purposes of the percentages. (Note; <b>there are no 'half spaces' of drone
     * warheads</b>, mixed spaces are counted as the more restricted type of the two
     * halves.)"</blockquote>
     *
     * <p>So two half-space modules share a payload space and that space is billed once, at the
     * worse of the two tiers. FD10.66's own example: "a type-IV drone with an MW module, a half
     * space explosive module, and a half space armor module would be considered as a space of
     * Restricted and a space of Limited availability" — the MW is the Limited space, and the
     * General explosive half is dragged up by the Restricted armor half it shares a space with.
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
        Availability worst = effectiveFrame().availability;
        worst = Availability.moreRestrictive(worst, propulsion.availabilityIn(year));
        // FD8.24: the submunitions' speed is a restricted item in its own right (FD10.65).
        // Cannot bite today -- every module that HAS submunitions is itself Limited, which is
        // already the worst tier there is -- but a cheaper carrier module would make it bite.
        if (submunitionPropulsion != null)
            worst = Availability.moreRestrictive(
                    worst, submunitionPropulsion.availabilityIn(year));
        for (PayloadModule m : allModules())
            worst = Availability.moreRestrictive(worst, m.availability);
        for (FrameAddition a : additions)
            if (!supersededByTheFrame(a))
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
        Availability floor = effectiveFrame().availability;
        floor = Availability.moreRestrictive(floor, propulsion.availabilityIn(year));
        // FD10.641 counts "speed upgrades" among the frame modifications that set the floor for
        // every space, and the submunitions' engines are a speed upgrade (FD8.44).
        if (submunitionPropulsion != null)
            floor = Availability.moreRestrictive(
                    floor, submunitionPropulsion.availabilityIn(year));
        for (FrameAddition a : additions)
            if (!supersededByTheFrame(a))
                floor = Availability.moreRestrictive(floor, a.availability);

        Map<Availability, Double> out = new EnumMap<>(Availability.class);
        // FD10.66: the unit of account is the PAYLOAD SPACE, not the module. Two half-space
        // modules share a space and that space counts once, at the worse of the two tiers.
        Availability pendingHalf = null;
        for (PayloadModule m : allModules()) {
            Availability tier = Availability.moreRestrictive(floor, m.availability);
            if (m.spaces == 0.5) {
                if (pendingHalf == null) {
                    pendingHalf = tier;
                } else {
                    out.merge(Availability.moreRestrictive(pendingHalf, tier), 1.0, Double::sum);
                    pendingHalf = null;
                }
            } else {
                out.merge(tier, m.spaces, Double::sum);
            }
        }
        // A half with no partner still occupies a space; nothing shares it, so it is its own.
        if (pendingHalf != null)
            out.merge(pendingHalf, 1.0, Double::sum);
        return out;
    }

    /**
     * Whether this drone can exist at all in a given year, which is a different question from
     * how obtainable it is. FD10.651 dates each engine's invention; a drone built around one
     * that has not been invented is not Limited, it is impossible.
     */
    public boolean existsIn(int year) {
        // FD8.11 sells submunitions at "any speed available for the year", so an MW drone whose
        // type-VIs outrun their own invention date is as impossible as one whose bus does.
        if (submunitionPropulsion != null && !submunitionPropulsion.existsIn(year))
            return false;
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
        // FD10.24: "Speed upgrade costs for type-III-XX drones are the same as for the
        // two-space drone (FD10.52)", which falls out of pricing the effective frame.
        double total = propulsion.costOn(effectiveFrame());
        for (PayloadModule m : allModules())
            total += m.cost;
        for (FrameAddition a : additions)
            total += a.cost;
        total += submunitionUpgradeCost();      // FD8.44, paid per submunition
        return total;
    }

    /** How many type-VI drones this one carries as its payload (FD8.21). */
    public int submunitionCount() {
        int total = 0;
        for (PayloadModule m : allModules())
            total += m.submunitions;
        return total;
    }

    /**
     * FD8.44: what the submunitions' engines cost, on top of everything else.
     *
     * <blockquote>"The submunitions of an MW drone can only receive speed upgrades, and the
     * cost of this upgrade will be added to the total cost of the drone. <b>The cost for each
     * submunition is half the cost for a type-I drone receiving the speed
     * upgrade.</b>"</blockquote>
     *
     * <p>FD10.43 says it from the other end: "The actual submunitions are part of the cost of
     * the MW module; but their speed upgrades will cost extra (FD8.44)." The module's 2.50 or
     * 3.50 buys the drones and nothing about how fast they fly.
     *
     * <h2>The halving is FD10.52's half-space column, not arithmetic on FD8.44's sentence</h2>
     * A submunition is a type-VI, and FD2.226 — "Type-VI speed upgrades cost 1/2 the normal
     * amount" — is already in the chart, which is why {@link Propulsion#costOn} is read at
     * HALF_SPACE here. The two readings agree at medium and fast and DISAGREE at speed 12,
     * where the chart is right: halving a type-I's 0.50 upgrade would charge 0.25 a submunition
     * for speed 12, making FD8.41's "standard Speed 12 type-III with Speed 12 submunitions"
     * cost 4.75 where the rule says four points. Speed 12 IS the type-VI's baseline (FD2.227),
     * so there is no upgrade there to pay for.
     *
     * <p>Against the published tables: three submunitions at medium is 3 x 0.25 = 0.75 and at
     * fast 3 x 0.50 = 1.50 (FD8.441); five is 1.25 and 2.50 (FD8.442). Both exact, and so are
     * all nine drone totals FD8.41, FD8.42 and FD8.45 work out longhand.
     */
    public double submunitionUpgradeCost() {
        if (submunitionPropulsion == null)
            return 0;
        double baseline = Propulsion.SPEED_12.costOn(DroneFrame.HALF_SPACE);
        double perSubmunition = submunitionPropulsion.costOn(DroneFrame.HALF_SPACE) - baseline;
        return submunitionCount() * perSubmunition;
    }
}
