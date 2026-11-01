package com.sfb.objects;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One drone a player has chosen to put in a rack: a catalogue drone, or a catalogue drone
 * modified (FD10.2).
 *
 * <h2>Why this exists</h2>
 * {@link Drone} has taken a {@code (DroneType, DroneSpec)} pair since the composition model
 * landed, so the engine has been able to FLY a drone with an ECM module or three armor modules
 * for some time. Nothing could ever ASK for one: a loadout was a {@code List<DroneType>}, and
 * the enum is the fourteen drones history fielded. Everything in FD10.4's workshop was
 * unreachable from the data — built, priced, capped, and unorderable.
 *
 * <p>So this is the loadout line. A {@code CoiLoadout} holds these instead of bare types, and
 * {@link #parse} gives them a textual form that a scenario file, a wire request or a test can
 * all write.
 *
 * <h2>A basis AND a spec, which is how the rules talk</h2>
 * The pair is not redundancy. Three of {@link DroneType}'s fields are not derived from the
 * composition and cannot be — endurance is a trait of the drone family (96 impulses for a
 * type-I, 64 for the transitional type-II, 800 for a type-III), {@code warpSeeker} describes the
 * guidance package the frame "includes" (FD10.21), and the family is what both are read off. A
 * one-space speed-12 frame is a type-II or a type-I with a moderate engine depending on nothing
 * the composition records, and guessing would be inventing a rule to save a field.
 *
 * <p>Which is also the rulebook's own idiom. FD12.12 describes "a type-IV frame carrying three
 * 1/2-PS armor modules", FD2.4421 asks for "the phaser-3 swordfish drone with external armor":
 * a named drone, plus what was done to it. The basis names the drone; the spec says what was
 * done.
 *
 * <h2>What the basis fixes, and what the modification may change</h2>
 * The basis settles the frame and the propulsion section — those ARE the choice of drone — so a
 * modification may only change the payload, bolt things to the frame (FD10.53), and buy engines
 * for submunitions (FD8.44). A spec on a different frame or a different engine is refused,
 * because it would mean the line claimed to be one drone while being another, and the basis's
 * endurance would then be a lie about the thing in the rack.
 *
 * <p>Frame ADDITIONS may only be added, never dropped: a type-III's 800-impulse endurance comes
 * with {@link FrameAddition#TYPE_III_GUIDANCE}, and a "type-III" that had shed it would keep the
 * endurance without the guidance that is the reason for it (FD10.53's frame surcharge).
 *
 * @param basis the catalogue drone this one is, or is built from — never null
 * @param spec  what it is actually made of; the basis's own composition when unmodified
 */
public record DroneLoad(DroneType basis, DroneSpec spec) {

    /**
     * Separates the basis from each modification in the textual form, e.g.
     * {@code TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF}.
     */
    public static final String SEPARATOR = "+";

    public DroneLoad {
        if (basis == null)
            throw new IllegalArgumentException("a drone load names a catalogue drone");
        if (spec == null)
            spec = basis.spec;

        // The basis chose the frame and the engine. A spec that disagrees is not a modified
        // type-IM, it is a different drone wearing the name -- and the endurance and family the
        // basis supplies would be describing something that is not in the rack.
        if (spec.frame() != basis.spec.frame())
            throw new IllegalArgumentException("a " + basis + " is built on a "
                    + basis.spec.frame() + " frame, not a " + spec.frame());
        if (spec.propulsion() != basis.spec.propulsion())
            throw new IllegalArgumentException("a " + basis + " carries a "
                    + basis.spec.propulsion() + " engine, not a " + spec.propulsion()
                    + " -- choose the basis whose speed you want (FD2.22)");
        if (!spec.additions().containsAll(basis.spec.additions()))
            throw new IllegalArgumentException("a " + basis + " cannot shed "
                    + basis.spec.additions() + ": that is what makes it one");
    }

    /** The catalogue drone, exactly as the enum has it. */
    public static DroneLoad of(DroneType type) {
        return new DroneLoad(type, null);
    }

    /** Whether anything was done to the catalogue drone. */
    public boolean isModified() {
        return !spec.equals(basis.spec);
    }

    /** The drone itself, ready for a rack (FD2.31). */
    public Drone build() {
        return new Drone(basis, spec);
    }

    // ------------------------------------------------------------------ what a loader asks

    /** Rack spaces this drone occupies (FD3.0). */
    public double rackSpaces() {
        return spec.rackSpaces();
    }

    /** Hexes per turn, after FD12.132's external-armor penalty if it carries any. */
    public int speed() {
        return spec.speed();
    }

    /** FD10.5's open-market price for this drone. */
    public double pointCost() {
        return spec.pointCost();
    }

    /** FD2.51: a dogfight drone, which only an E, G or H rack will take. */
    public boolean isTypeVI() {
        return basis.isTypeVI();
    }

    /**
     * Whether this drone can be had at all in that year.
     *
     * <p>Two questions where the drone was modified, and deliberately only one where it was
     * not. A catalogue drone's gate stays {@link DroneType#availableIn} and nothing else, so
     * every year check in the engine answers exactly as it did before this class existed —
     * which matters because the two do not quite agree: {@code TypeIII} declares year 0 while
     * the speed-12 engine it flies on was invented in Y77 (FD10.651). That discrepancy is a
     * question about the enum's declared year, not about composed drones, and is left where it
     * was rather than being silently resolved here.
     *
     * <p>A MODIFIED drone is also asked whether its parts have been invented, because a
     * modification can reach past the basis — FD8.11 sells submunitions at "any speed available
     * for the year", so an MW drone whose type-VIs outrun their own invention date is
     * impossible however old its bus is.
     */
    public boolean availableIn(int year) {
        if (!basis.availableIn(year))
            return false;
        return !isModified() || spec.existsIn(year);
    }

    /** FD10.65: how hard this drone is to obtain in that year, at its most restricted part. */
    public Availability availabilityIn(int year) {
        return spec.availabilityIn(year);
    }

    // ------------------------------------------------------------------ the textual form

    /**
     * The drone as one token-and-modifiers string: the basis name, then every module, frame
     * addition and submunition engine, separated by {@value #SEPARATOR}.
     *
     * <p>An unmodified drone is just its name, which is what every loadout written before this
     * class existed already said. That is the point of the format: {@code "TypeIM"} means today
     * what it meant then, so old scenario files, old wire requests and old saves all still read.
     */
    public String format() {
        if (!isModified())
            return basis.name();
        StringBuilder out = new StringBuilder(basis.name());
        for (PayloadModule m : spec.payload())
            out.append(SEPARATOR).append(m.name());
        // Walked in declaration order rather than the set's, so the same drone always spells
        // itself the same way: a Set has no order of its own and a round trip through a file
        // must not depend on one.
        for (FrameAddition a : FrameAddition.values())
            if (spec.additions().contains(a))
                out.append(SEPARATOR).append(a.name());
        if (spec.submunitionPropulsion() != null)
            out.append(SEPARATOR).append(spec.submunitionPropulsion().name());
        return out.toString();
    }

    @Override
    public String toString() {
        return format();
    }

    /**
     * Read back what {@link #format} wrote.
     *
     * <p>Each modifier is looked up by name across {@link PayloadModule},
     * {@link FrameAddition} and {@link Propulsion} and routed to whichever one claims it; a
     * {@code SPEED_} token can only be the submunitions' engine, because the drone's own comes
     * from its basis. {@code DroneLoadTest} fails the build if those three enums ever share a
     * constant name, which is what makes the routing safe rather than lucky.
     *
     * <h2>Naming any module replaces the payload</h2>
     * {@code "TypeIV+ECM"} is a type-IV carrying one ECM module, NOT a standard type-IV with an
     * ECM module added to the two explosive ones it came with — there would be nowhere to put
     * it (FD10.4: two payload spaces). Name no modules and the basis's payload is kept, which
     * is how a frame addition alone is said: {@code "TypeI+ACTIVE_TERMINAL_GUIDANCE"} is an
     * ordinary type-I warhead with advanced guidance bolted on.
     *
     * @throws IllegalArgumentException if the basis is unknown, a modifier is unknown, or the
     *         composition is not a legal drone — {@link DroneSpec} does the last of those
     */
    public static DroneLoad parse(String text) {
        if (text == null || text.isBlank())
            throw new IllegalArgumentException("a drone load needs a name");
        String[] parts = text.trim().split("\\" + SEPARATOR);

        DroneType basis;
        try {
            basis = DroneType.valueOf(parts[0].trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("no such drone as " + parts[0].trim());
        }
        if (parts.length == 1)
            return of(basis);

        List<PayloadModule> modules = new ArrayList<>();
        Set<FrameAddition> additions = EnumSet.noneOf(FrameAddition.class);
        additions.addAll(basis.spec.additions());
        Propulsion submunitions = null;
        boolean payloadNamed = false;

        for (int i = 1; i < parts.length; i++) {
            String token = parts[i].trim();
            PayloadModule module = lookup(PayloadModule.class, token);
            if (module != null) {
                modules.add(module);
                payloadNamed = true;
                continue;
            }
            FrameAddition addition = lookup(FrameAddition.class, token);
            if (addition != null) {
                additions.add(addition);
                continue;
            }
            Propulsion speed = lookup(Propulsion.class, token);
            if (speed != null) {
                submunitions = speed;
                continue;
            }
            throw new IllegalArgumentException("no such drone part as " + token);
        }

        List<PayloadModule> payload = payloadNamed ? modules : basis.spec.payload();
        DroneSpec spec = new DroneSpec(basis.spec.frame(), basis.spec.propulsion(),
                payload, additions, submunitions);
        return new DroneLoad(basis, spec);
    }

    /**
     * Why {@link #parse} will not read this, or null if it will.
     *
     * <p>For a caller that has to carry on — the COI endpoint reports what it could not use
     * as a warning beside the setup it did apply, and a drone the player asked for must not
     * disappear without a word. Every refusal in here is a rules refusal with its clause
     * number in the message, from {@link DroneSpec}'s own checks, so the warning is worth
     * showing rather than a stack trace with the clause filed off.
     */
    public static String refusalFor(String text) {
        try {
            parse(text);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /** {@code valueOf} without the exception, so a token can be offered to each enum in turn. */
    private static <E extends Enum<E>> E lookup(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Every name {@link #parse} will accept as a modifier, for a client building a picker. */
    public static List<String> modifierNames() {
        List<String> names = new ArrayList<>();
        for (PayloadModule m : PayloadModule.values())
            names.add(m.name());
        for (FrameAddition a : FrameAddition.values())
            names.add(a.name());
        for (Propulsion p : Propulsion.values())
            names.add(p.name());
        return names;
    }
}
