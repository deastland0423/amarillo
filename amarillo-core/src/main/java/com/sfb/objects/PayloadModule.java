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
    EXPLOSIVE_HALF(0.5, 6, 0, 0.25, Availability.GENERAL),

    /** FD10.41: one payload space, strength twelve. The usual meaning of "explosive module". */
    EXPLOSIVE_1(1.0, 12, 0, 0.50, Availability.GENERAL),

    /**
     * FD10.41's two-space explosive. "There is no difference between two 1/2-space and one
     * 1.0-space module", so this and a pair of {@link #EXPLOSIVE_1}s are the same drone — both
     * are offered because the chart offers both and a loadout may be recorded either way.
     */
    EXPLOSIVE_2(2.0, 24, 0, 1.00, Availability.GENERAL),

    /** FD12.11/FD12.21: the smaller internal armor module. No warhead, four more hull... */
    ARMOR_HALF(0.5, 0, 2, 0.25, Availability.RESTRICTED),

    /** ...and the larger, at one payload space. FD12.21: "adds four damage points". */
    ARMOR_1(1.0, 0, 4, 0.50, Availability.RESTRICTED),

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
    DOGFIGHT_WARHEAD(0.5, 8, 0, 0.25, Availability.GENERAL);

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

    PayloadModule(double spaces, int strength, int hullBonus, double cost,
            Availability availability) {
        this.spaces = spaces;
        this.strength = strength;
        this.hullBonus = hullBonus;
        this.cost = cost;
        this.availability = availability;
    }
}
