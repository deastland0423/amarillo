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
    EXPLOSIVE_HALF(0.5, 6, 0),

    /** FD10.41: one payload space, strength twelve. The usual meaning of "explosive module". */
    EXPLOSIVE_1(1.0, 12, 0),

    /**
     * FD10.41's two-space explosive. "There is no difference between two 1/2-space and one
     * 1.0-space module", so this and a pair of {@link #EXPLOSIVE_1}s are the same drone — both
     * are offered because the chart offers both and a loadout may be recorded either way.
     */
    EXPLOSIVE_2(2.0, 24, 0),

    /** FD12.11/FD12.21: the smaller internal armor module. No warhead, four more hull... */
    ARMOR_HALF(0.5, 0, 2),

    /** ...and the larger, at one payload space. FD12.21: "adds four damage points". */
    ARMOR_1(1.0, 0, 4),

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
    DOGFIGHT_WARHEAD(0.5, 8, 0);

    /** Payload spaces consumed (FD10.4), and the chart column this module is priced in. */
    public final double spaces;

    /** Points contributed to the drone's single combined explosion (FD10.41). Zero for armor. */
    public final int strength;

    /** Damage points added to the drone's own hull (FD12.21). Zero for everything but armor. */
    public final int hullBonus;

    PayloadModule(double spaces, int strength, int hullBonus) {
        this.spaces = spaces;
        this.strength = strength;
        this.hullBonus = hullBonus;
    }
}
