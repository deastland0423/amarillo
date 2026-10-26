package com.sfb.objects;

/**
 * FD10.4: what goes in a drone's payload spaces.
 *
 * <h2>A module is a SIZE as well as a kind</h2>
 * FD10.51's chart has three columns — 1/2-PS, 1-PS, 2-PS — and a dash where a module does not
 * come in that size. An explosive exists at all three; an ECM module exists at exactly one:
 *
 * <blockquote>FD10.442: "No more than one such module can be placed on any drone; <b>the module
 * occupies one payload space</b> (which can be either space; it need not be forward)."</blockquote>
 *
 * <p>So the sizes are enumerated rather than carried as a number on a kind. Writing "ECM, 0.5
 * spaces" would be inventing a module the chart says does not exist, and the mistake is easy to
 * make because the explosives — the only modules here when this file was first written — happen
 * to come in all three.
 *
 * <h2>Armor contributes HULL, which is why hull is not a property of the frame</h2>
 * <blockquote>FD12.21: "The effect of an armor module is to increase the number of damage points
 * required to destroy the drone. The 1/2-PS module adds two damage points; the one-PS armor
 * module adds four damage points."</blockquote>
 *
 * <p>The owner's example, which is also FD12.12's: a type-IV frame carrying three 1/2-PS armor
 * modules and one 1/2-PS explosive — four half-spaces filling its two payload spaces exactly —
 * is a drone with <b>twelve</b> hull and an explosion strength of <b>six</b>. An earlier version
 * of this model read hull off the frame alone, which is right for every drone in the game today
 * and wrong for that one.
 *
 * <h2>What is not here yet</h2>
 * The probe, ECM, MW, swordfish, spearfish, starfish and stingray modules are each their own FD
 * section with their own behaviour, and FD10.403 spells out which combinations cannot exist at
 * all. None is declared until the thing it does is built — an enum constant nothing can use is a
 * claim this file should not make. Costs are absent for the same reason: FD10.51 prices every
 * module, and pricing is its own slice.
 */
public enum PayloadModule {

    /** FD10.41: half a payload space, explosion strength six. */
    EXPLOSIVE_HALF(0.5, 6, 0, 0.25, Availability.GENERAL, false, 0),

    /** FD10.41: one payload space, strength twelve. The usual meaning of "explosive module". */
    EXPLOSIVE_1(1.0, 12, 0, 0.50, Availability.GENERAL, false, 0),

    /**
     * FD10.41's two-space explosive. "There is no difference between two 1/2-space and one
     * 1.0-space module", so this and a pair of {@link #EXPLOSIVE_1}s are the same drone — both
     * are offered because the chart offers both and a loadout may be recorded either way.
     */
    EXPLOSIVE_2(2.0, 24, 0, 1.00, Availability.GENERAL, false, 0),

    /**
     * FD10.42: a probe module, which FD6.0 explains and this file only has to build.
     *
     * <p>"All probe modules occupy one payload space, but no more than one can be placed on a
     * given drone." One size, like the ECM module, and FD10.51 dashes the other two columns.
     *
     * <p>Half a point, which is what a one-space explosive costs — so FD10.421's "there is no
     * cost to replace an explosive module with a probe module" is the price match again rather
     * than a dispensation.
     *
     * <p><b>The probe is the module that CAN share a drone with armor.</b> FD10.422: "The probe
     * module can be combined with other modules on a two-space drone <b>or have external armor
     * added to its drone frame</b>. (The most common two-space probe drone had armor to improve
     * survivability.)" Where FD10.443 bars ECM from armor of either kind, this is explicitly
     * permitted, and FD10.403's impossible drone — probe, ECM and external armor together —
     * fails on the ECM rather than on the probe.
     *
     * <p>Everything about USING one is elsewhere and stays there: FD10.423 has the launching
     * player designate what an accompanying ECM module protects, FD10.424 decides which
     * guidance system the drone may use, FD10.425 lists the options for a probe on a type-III
     * frame. All three happen at launch.
     */
    PROBE(1.0, 0, 0, 0.50, Availability.RESTRICTED, false, 0),

    /**
     * FD10.44: an ECM module, which FD9.0 explains and this file only has to build.
     *
     * <p>FD10.442: "No more than one such module can be placed on any drone; the module
     * occupies <b>one payload space</b> (which can be either space; it need not be forward)."
     * One size only — FD10.51's chart has a dash in both the 1/2-PS and 2-PS columns.
     *
     * <p>No warhead and no hull: an ECM drone is a jammer that happens to fly. It costs the
     * same 0.50 as the one-space explosive it displaces, which is FD10.441's "there is no cost
     * to replace an explosive module with an ECM module" falling out of the chart rather than
     * being granted.
     *
     * <p><b>What it cannot share a drone with:</b> armor, of either kind. FD10.443 — "The ECM
     * module can, within the limits of other modules, be combined with any other module in the
     * game except an (internal or external) armor module. ECM drones will not work with armor
     * modules." {@code DroneSpec} refuses the combination.
     */
    ECM(1.0, 0, 0, 0.50, Availability.RESTRICTED, false, 0),

    /**
     * FD8.0 / FD10.431: the smaller multi-warhead module — one payload space, three
     * submunitions, which are type-VI drones carried "instead of an explosive warhead".
     *
     * <p>FD8.12 is the Federation's type-III-MW, deployed Y175 and copied immediately. The
     * submunitions themselves are paid for in the module's 2.50 (FD10.43); only their speed
     * upgrades cost extra (FD8.44), which is a cost rule for a thing this file does not model.
     *
     * <p>Limited Availability (FD10.631), forward payload space only (FD10.432), and no drone
     * may carry two.
     */
    MW_SMALL(1.0, 0, 0, 2.50, Availability.LIMITED, true, 3),

    /**
     * FD10.431's larger module: two payload spaces, five submunitions. FD8.11's Kzinti
     * type-IV-MW, deployed Y170 after they watched the Klingon Stingray.
     *
     * <p>It spans the whole payload of a two-space frame, which is how it satisfies FD10.432's
     * "must be in the forward payload space(s)" — the plural is the rule allowing for exactly
     * this.
     */
    MW_LARGE(2.0, 0, 0, 3.50, Availability.LIMITED, true, 5),

    /** FD12.11/FD12.21: the smaller internal armor module. No warhead, four more hull... */
    ARMOR_HALF(0.5, 0, 2, 0.25, Availability.RESTRICTED, false, 0),

    /** ...and the larger, at one payload space. FD12.21: "adds four damage points". */
    ARMOR_1(1.0, 0, 4, 0.50, Availability.RESTRICTED, false, 0),

    /**
     * The type-VI's fixed warhead, which is not a module a player can choose and is here so that
     * {@link DroneFrame#HALF_SPACE} has something to carry.
     *
     * <p><b>Eight, and not six.</b> A half-space explosive module is strength six (FD10.41), but
     * a dogfight drone is not built from one: FD2.54 gives it a warhead that depends on what it
     * hits — two points against size class 4 and larger, four against size class 5, eight against
     * size class 6 and 7. Eight is the SIZE-6/7 figure, recorded here because that is the number
     * {@code DroneType.damage} has always carried and what {@code Drone.impact()} returns as the
     * nominal before {@code Drone.impact(Unit)} applies the rule.
     *
     * <p>So this constant is a nominal, not a strength. The rule lives in {@code Drone}, where it
     * already worked before this model existed and was deliberately left alone.
     */
    DOGFIGHT_WARHEAD(0.5, 8, 0, 0.25, Availability.GENERAL, false, 0);

    /** Payload spaces consumed (FD10.4), and the chart column this module is priced in. */
    public final double spaces;

    /** Points contributed to the drone's single combined explosion (FD10.41). Zero for armor. */
    public final int strength;

    /** Damage points added to the drone's own hull (FD12.21). Zero for everything but armor. */
    public final int hullBonus;

    /**
     * Points, from FD10.51's chart at THIS module's size.
     *
     * <p>The chart is why FD10.441 and FD12.31 can say an explosive may be swapped for an ECM
     * or an armor module "without cost": at a given size the two prices are simply equal, so
     * those clauses are consequences rather than exceptions.
     *
     * <p>The dogfight warhead has no row of its own and is NOT priced as a half-space
     * explosive, which it is not. FD2.11 prices the whole type-VI at 0.5 and FD10.52 puts its
     * half-space speed-12 engine at 0.25, so 0.25 is what is left. The two numbers coincide;
     * the reasoning does not.
     */
    public final double cost;

    /**
     * FD10.6's tier. Explosives are General (FD10.611); an armored drone is Restricted
     * (FD10.621) and EXTERNAL armor is Limited (FD10.631), which is one more reason the two
     * armors are separate things rather than a placement flag.
     *
     * <p>The dogfight warhead is General because FD10.643 keeps type-VI drones out of the
     * availability calculation entirely — the tier is never consulted for one, and marking it
     * anything else would invite someone to count it.
     */
    public final Availability availability;

    /**
     * Whether this module is armor (FD12.0), which several rules turn on — FD10.443 forbids it
     * beside an ECM module, and FD12.131 beside external armor.
     *
     * <p>Asked by name rather than inferred from {@code hullBonus > 0}: adding hull is what
     * armor DOES, not what it IS, and a later module that happens to toughen a drone would
     * silently inherit armor's prohibitions.
     */
    public boolean isArmor() {
        return this == ARMOR_HALF || this == ARMOR_1;
    }

    /**
     * FD10.402: "Some warhead modules can only be placed in the forward space."
     *
     * <p>True for the MW module (FD10.432) and, when they are built, for swordfish (FD11.11),
     * spearfish (FD14.11) and starfish (FD15.11) — four of the five remaining modules.
     *
     * <p><b>WHICH space a module sits in is deliberately not modelled.</b> The owner's call,
     * 2026-10-26: no rule asks where a given module IS, only that a legal arrangement exists,
     * so it is enough to refuse a drone whose forward-only modules could not all fit in front.
     * That makes FD10.47's "the internal armor must be placed in the opposite bay" a placement
     * detail with no effect on legality, which is what it is.
     */
    public final boolean requiresForward;

    /**
     * FD10.431: type-VI drones carried in place of a warhead — three in the small module, five
     * in the large. Zero for everything that is not a multi-warhead module.
     *
     * <p>A construction fact, stated here because it is what distinguishes the two MW sizes.
     * What the submunitions DO on release is FD8.2 and is not modelled.
     */
    public final int submunitions;

    PayloadModule(double spaces, int strength, int hullBonus, double cost,
            Availability availability, boolean requiresForward, int submunitions) {
        this.spaces = spaces;
        this.strength = strength;
        this.hullBonus = hullBonus;
        this.cost = cost;
        this.availability = availability;
        this.requiresForward = requiresForward;
        this.submunitions = submunitions;
    }
}
