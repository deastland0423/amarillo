package com.sfb.objects;

import com.sfb.objects.ShipSpec.RefitSpec;
import com.sfb.objects.ShipSpec.VariantSpec;
import com.sfb.objects.ShipSpec.WeaponSpec;
import com.sfb.objects.ShipSpec.WeaponSwap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a refitted hull from a base hull and a set of refit codes (S3.24).
 *
 * <h2>What problem this solves</h2>
 * Every refitted ship used to be its own file. That cost more than clutter:
 * <ul>
 *   <li><b>It was lossy.</b> The Lyran CWB is a CW with the +, the phaser AND the power-pack refits,
 *       and says none of them in its name — so "power pack without the phaser refit" could not be
 *       written down at all. Refits as independent switches can express every combination.</li>
 *   <li><b>It bundled silently.</b> A reader comparing CW to CWB saw ten changes with no way to tell
 *       which belonged to which refit. Now each refit states its own diff and the costs add up:
 *       113 + 2 (+) + 4 (p) + 9 (B) = 128, exactly CWB's published BPV.</li>
 *   <li><b>It multiplied.</b> 128 of 427 hull files were a combination of refits on another file.</li>
 * </ul>
 *
 * <h2>Why the resulting NAME is declared and not derived</h2>
 * A D7 with the K refit is a D7K; a <b>D7C</b> with the same refit is a <b>D7L</b>, because D7CK
 * would collide confusingly with D7K (owner, 2026-10-07). The Lyran CWB names none of its three
 * refits. So there is no algebra that produces the right code, and {@link VariantSpec} carries the
 * historical one. An unnamed combination — one history never fielded but the rules permit under
 * S3.24 — gets a derived code so it can still be bought and referred to.
 *
 * <h2>Order independence</h2>
 * Refits are applied in the order the hull declares them, not the order asked for, so a set of codes
 * always produces the same ship. {@code requires} exists for the one real dependency in the data:
 * the Klingon K refit arrives on top of the B refit's shields, which is why a D6K has B's shield
 * boxes as well as its own phasers.
 */
public final class RefitResolver {

    private RefitResolver() { }

    /** The refit a hull declares under this code, or null. */
    public static RefitSpec refit(ShipSpec base, String code) {
        if (base == null || base.refits == null || code == null)
            return null;
        for (RefitSpec r : base.refits)
            if (code.equalsIgnoreCase(r.code))
                return r;
        return null;
    }

    /**
     * Expand a set of refit codes with everything they require (transitively), in the hull's own
     * declared order.
     * <p>
     * Asking for K alone on a D6 therefore gets B as well, which is what the data says a D6K is —
     * rather than a half-refitted ship nobody ever built.
     */
    public static List<String> withRequirements(ShipSpec base, List<String> asked) {
        Set<String> want = new LinkedHashSet<>();
        if (asked != null)
            for (String code : asked)
                addWithRequirements(base, code, want);
        List<String> ordered = new ArrayList<>();
        if (base.refits != null)
            for (RefitSpec r : base.refits)
                if (want.stream().anyMatch(c -> c.equalsIgnoreCase(r.code)))
                    ordered.add(r.code);
        return ordered;
    }

    private static void addWithRequirements(ShipSpec base, String code, Set<String> into) {
        RefitSpec r = refit(base, code);
        if (r == null || into.contains(r.code))
            return;
        if (r.requires != null)
            for (String need : r.requires)
                addWithRequirements(base, need, into);
        into.add(r.code);
    }

    /**
     * The variant this hull declares for exactly this set of refits, or null when history never
     * named it.
     */
    public static VariantSpec variantFor(ShipSpec base, List<String> codes) {
        if (base == null || base.variants == null)
            return null;
        Set<String> want = lower(codes);
        for (VariantSpec v : base.variants)
            if (lower(v.refits).equals(want))
                return v;
        return null;
    }

    private static Set<String> lower(List<String> codes) {
        Set<String> out = new LinkedHashSet<>();
        if (codes != null)
            for (String c : codes)
                if (c != null)
                    out.add(c.toLowerCase());
        return out;
    }

    /**
     * Apply refits to a copy of the base hull.
     *
     * <p>The base is never modified: the library holds one spec per hull and hands out variants built
     * from it, so a mutation here would refit the base ship itself and every variant after the first
     * would compound.
     *
     * @param asked refit codes; requirements are added, so K alone on a D6 brings B with it
     * @return the refitted spec, or the base's own copy when nothing is asked for
     */
    public static ShipSpec apply(ShipSpec base, List<String> asked) {
        List<String> codes = withRequirements(base, asked);
        ShipSpec out = deepCopy(base);
        if (codes.isEmpty())
            return out;
        out.refitOf = base.type;
        out.appliedRefits = new ArrayList<>(codes);

        for (String code : codes) {
            RefitSpec r = refit(base, code);
            if (r == null)
                continue;
            out.bpv += r.bpv;
            if (r.epv != null)
                out.epv = r.epv;
            if (r.shields != null)
                out.shields = r.shields.clone();
            if (r.y175Upgrades != null)
                out.y175Upgrades = MAPPER.convertValue(r.y175Upgrades, ShipSpec.Y175Upgrades.class);
            setSpecFields(out, r.set);
            setFields(out.power, r.power, "power");
            setFields(out.auxiliary, r.auxiliary, "auxiliary");
            setFields(out.control, r.control, "control");
            if (r.addWeapons != null) {
                if (out.weapons == null)
                    out.weapons = new ArrayList<>();
                out.weapons.addAll(copyOf(r.addWeapons));
            }
            if (r.replaceWeapons != null)
                for (WeaponSwap swap : r.replaceWeapons)
                    swap(out, swap);
        }

        // CONVERSIONS LAST, whatever order the hull declares its refits in.
        //
        // A conversion reads how many of something the ship has NOW, so it has to see the finished
        // ship. The Federation DN declares its AWR refit before its + refit, and applied in that
        // order the AWR converted the hull's two APRs and the + refit then handed it four fresh
        // ones back — a DNa+ with both, at 209 points instead of 211. Ordering by declaration made
        // the author responsible for something they cannot reasonably be expected to think about.
        for (String code : codes) {
            RefitSpec r = refit(base, code);
            if (r != null)
                convertPower(out, r.convertPower);
        }

        // The name history gave it, or a derived one for a combination nobody fielded.
        VariantSpec named = variantFor(base, codes);
        if (named != null) {
            out.type = named.type;
            if (named.typeName != null)
                out.typeName = named.typeName;
            if (named.serviceYear != null)
                out.serviceYear = named.serviceYear;
            if (named.name != null)
                out.name = named.name;
        } else {
            out.type = canonicalCode(base.type, codes);
            out.typeName = base.typeName + " (" + describe(base, codes) + ")";
        }
        // A refit cannot be fielded before it exists (S3.24); the hull arrives when its latest does.
        for (String code : codes) {
            RefitSpec r = refit(base, code);
            if (r != null && r.year > out.serviceYear)
                out.serviceYear = r.year;
        }
        return out;
    }

    /**
     * The code for a combination history never named: the base, then the markers in the house
     * order — capitals, then lower case, then plus signs.
     *
     * <p>Not the order the hull declares its refits in, which would give "DN+pB" for one ship and
     * "DNpB+" for its neighbour depending on how each file was written. The owner's rule
     * (2026-10-08) is one order everywhere, and {@code ShipFileNamingTest} holds the data to it;
     * this holds the derived codes to the same thing.
     */
    static String canonicalCode(String baseType, List<String> codes) {
        StringBuilder caps = new StringBuilder();
        StringBuilder low = new StringBuilder();
        int pluses = 0;
        for (String code : codes)
            for (char c : code.toCharArray()) {
                if (c == '+') pluses++;
                else if (Character.isLowerCase(c)) low.append(c);
                else caps.append(c);
            }
        return baseType + caps + low + "+".repeat(pluses);
    }

    /** "B and Phaser-1 refit", for a combination with no historical name. */
    private static String describe(ShipSpec base, List<String> codes) {
        List<String> names = new ArrayList<>();
        for (String code : codes) {
            RefitSpec r = refit(base, code);
            names.add(r != null && r.name != null ? r.name.replace(" refit", "") : code);
        }
        return String.join(" and ", names) + " refit";
    }

    /**
     * A deep copy of a spec, by a Jackson round trip.
     *
     * <p>Not a hand-written copy constructor, deliberately. {@link ShipSpec} has grown a field most
     * weeks this project has existed — crew quality, D%, cargo drones, aegis weapons — and a
     * hand-written copier would silently stop copying each new one, so every refitted hull would
     * quietly lose whatever was added last. A round trip through the same mapper that loads the file
     * cannot fall behind the class.
     */
    static ShipSpec deepCopy(ShipSpec spec) {
        return MAPPER.convertValue(spec, ShipSpec.class);
    }

    private static List<WeaponSpec> copyOf(List<WeaponSpec> weapons) {
        List<WeaponSpec> out = new ArrayList<>();
        for (WeaponSpec w : weapons)
            out.add(MAPPER.convertValue(w, WeaponSpec.class));
        return out;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Convert one power system into another, and charge per unit (the Federation AWR refit).
     *
     * <p>Reads how many the ship has NOW, which is the point: the + refit adds two APRs to a DN, so
     * the AWR refit that follows converts four rather than two and costs four points rather than
     * two. Applied after {@code set}, so a refit may rebuild the power block and convert in one go.
     */
    private static void convertPower(ShipSpec spec, ShipSpec.PowerConversion conv) {
        if (conv == null || conv.from == null || conv.to == null || spec.power == null)
            return;
        try {
            java.lang.reflect.Field from = ShipSpec.PowerSpec.class.getField(conv.from);
            java.lang.reflect.Field to = ShipSpec.PowerSpec.class.getField(conv.to);
            int count = from.getInt(spec.power);
            if (count <= 0)
                return;
            to.setInt(spec.power, to.getInt(spec.power) + count);
            from.setInt(spec.power, 0);
            spec.bpv += count * conv.bpvEach;
        } catch (NoSuchFieldException ex) {
            throw new IllegalStateException("a refit converts power." + conv.from + " to power."
                    + conv.to + ", and one of those is not a field of PowerSpec", ex);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("cannot convert power." + conv.from, ex);
        }
    }

    /**
     * Replace whole fields of the hull itself, by name (a refit's {@code set} block).
     *
     * <p>Jackson converts each value to the field's own type, so a refit can hand over a crew block,
     * a hull-box block or a list of shuttle bays and it arrives as the right object. An unknown name
     * throws rather than being ignored, for the same reason as everywhere else here: a refit that
     * means to rebuild {@code crewData} and writes {@code crew} would otherwise do nothing at all.
     */
    private static void setSpecFields(ShipSpec spec, java.util.Map<String, Object> changes) {
        if (changes == null || changes.isEmpty())
            return;
        for (java.util.Map.Entry<String, Object> e : changes.entrySet()) {
            try {
                java.lang.reflect.Field f = ShipSpec.class.getField(e.getKey());
                Object v = e.getValue() == null ? null
                        : MAPPER.convertValue(e.getValue(),
                                MAPPER.getTypeFactory().constructType(f.getGenericType()));
                f.set(spec, v);
            } catch (NoSuchFieldException ex) {
                throw new IllegalStateException("a refit sets '" + e.getKey()
                        + "', which is not a field of ShipSpec", ex);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("cannot set " + e.getKey(), ex);
            }
        }
    }

    /**
     * Set the named fields on one of the nested spec blocks.
     *
     * <p>By reflection, for the same reason the copy is a round trip: the blocks gain fields and a
     * switch over their names would go stale. An unknown field is an error rather than a shrug — a
     * refit that means to change {@code apr} and writes {@code APR} would otherwise do nothing at
     * all, and nothing downstream would look wrong.
     */
    private static void setFields(Object block, java.util.Map<String, Object> changes, String what) {
        if (changes == null || changes.isEmpty())
            return;
        if (block == null)
            throw new IllegalStateException("a refit changes " + what
                    + " but the hull declares no " + what + " block");
        for (java.util.Map.Entry<String, Object> e : changes.entrySet()) {
            try {
                java.lang.reflect.Field f = block.getClass().getField(e.getKey());
                Object v = e.getValue();
                // null means the refit REMOVES the field. The Federation AWR refit replaces a
                // hull's auxiliary power reactors with warp reactors, so it sets awr and clears
                // apr; written as a primitive that is zero, and a ship left holding both would
                // have twice the power it should.
                if (f.getType() == int.class)
                    f.setInt(block, v == null ? 0 : ((Number) v).intValue());
                else if (f.getType() == double.class)
                    f.setDouble(block, v == null ? 0 : ((Number) v).doubleValue());
                else if (f.getType() == boolean.class)
                    f.setBoolean(block, Boolean.TRUE.equals(v));
                else
                    f.set(block, v);
            } catch (NoSuchFieldException ex) {
                throw new IllegalStateException(
                        "a refit sets " + what + "." + e.getKey() + ", which is not a field of "
                        + block.getClass().getSimpleName(), ex);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException("cannot set " + what + "." + e.getKey(), ex);
            }
        }
    }

    /**
     * Apply one weapon change: a new type, changed properties, or both.
     * <p>
     * Order and arcs are untouched, because a refit upgrades the gun in its mount rather than
     * rearranging the ship.
     */
    private static void swap(ShipSpec spec, WeaponSwap swap) {
        if (spec.weapons == null || swap == null || swap.from == null)
            return;
        for (WeaponSpec w : spec.weapons) {
            if (!swap.from.equalsIgnoreCase(w.type))
                continue;
            if (swap.designators != null && !swap.designators.isEmpty()
                    && swap.designators.stream().noneMatch(d -> d.equalsIgnoreCase(w.designator)))
                continue;
            if (swap.set != null)
                setFields(w, swap.set, "weapon " + w.type + " " + w.designator);
            if (swap.to != null)
                w.type = swap.to;
        }
    }
}
