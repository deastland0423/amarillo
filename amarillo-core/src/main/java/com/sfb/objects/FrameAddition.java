package com.sfb.objects;

/**
 * FD10.53: things bolted to a drone's FRAME rather than filling a payload space, each with a
 * flat price.
 *
 * <blockquote>FD10.22: "Under the point cost system, the frame is effectively free. Exception:
 * type-III, which costs 1/2 point for standard 25-turn range and special guidance. The
 * propulsion (speed) and payload (e.g., warhead) modules, extended range, and any advanced
 * guidance cost points."</blockquote>
 *
 * <p>So the frame is free and these are what make it cost anything. FD10.53 prints all three at
 * half a point, which is why they are a table rather than three fields — a fourth addition in a
 * later product is a constant here and nothing else.
 *
 * <h2>Only the type-III's surcharge does anything yet</h2>
 * ATG and extended range are declared because FD10.53 prices them and the type-III surcharge
 * would otherwise look like a lone special case. Nothing builds a drone with either: ATG has
 * behaviour in FD5.2 and extended range changes the frame's size under FD10.24, and both belong
 * to the slice that implements those rules rather than to this one, which only prices things.
 */
public enum FrameAddition {

    /**
     * FD10.23: "If ATG is added, it is added to the frame (cost = 1/2 point). ATG cannot be
     * added to type-III drone, which already has a special type of guidance." Priced here,
     * unbuilt: see FD5.2 for what it does.
     */
    ACTIVE_TERMINAL_GUIDANCE(0.50, Availability.RESTRICTED, 0),

    /**
     * FD2.222: doubles the endurance, and FD10.24 makes a type-III that takes it a TWO-space
     * drone with one payload space. Priced here, unbuilt, because that frame change is the
     * interesting part and belongs with it.
     */
    EXTENDED_RANGE(0.50, Availability.GENERAL, 0),

    /**
     * The type-III's own surcharge, "for standard 25-turn range and special guidance"
     * (FD10.22). The one addition the current roster uses, and the reason an extra type-III
     * costs 2.0 where an extra type-II costs 1.5 (FD2.11) on the same frame at the same speed.
     */
    TYPE_III_GUIDANCE(0.50, Availability.LIMITED, 0),

    /**
     * FD12.13: half a payload space of armor bolted to the OUTSIDE, "i.e., in excess of the
     * normal payload space limit".
     *
     * <p>A frame addition and not a payload module, which is FD12.32's own word for it —
     * "adding external armor (which is, technically, a frame cost addition)" — and FD10.47 says
     * why: it takes "up no payload spaces but slow[s] down the drone". So a one-space drone can
     * carry a full explosive warhead AND armor, which internal armor would make impossible.
     *
     * <p>It pays for that in speed, by FD12.132's chart, and in availability: FD10.631 puts
     * external armor in Limited where internal armor is merely Restricted (FD10.621). The same
     * two damage points of protection (FD12.21), four times harder to obtain.
     */
    EXTERNAL_ARMOR_HALF(0.25, Availability.LIMITED, 2),

    /** FD12.13's larger external module: four more hull (FD12.21), and a steeper speed cost. */
    EXTERNAL_ARMOR_1(0.50, Availability.LIMITED, 4);

    /** Points, from FD10.53's chart. */
    public final double cost;

    /**
     * FD10.6's tier. Extended range is General (FD10.611), ATG is Restricted (FD10.621), and
     * the type-III frame is Limited (FD10.631) — which is why a type-III is the one drone in
     * the standard roster that is hard to come by at all.
     */
    public final Availability availability;

    /**
     * FD12.21: "The effect of an armor module is to increase the number of damage points
     * required to destroy the drone. The 1/2-PS module adds two damage points; the one-PS armor
     * module adds four damage points." Zero for everything that is not armor.
     */
    public final int hullBonus;

    /** True for the two external armor modules, which FD12.131 allows only one of. */
    public boolean isExternalArmor() {
        return this == EXTERNAL_ARMOR_HALF || this == EXTERNAL_ARMOR_1;
    }

    /**
     * FD12.132: what this addition does to the drone's speed, or the speed unchanged.
     *
     * <blockquote>"External armor reduces the speed of the drone according to the chart
     * below... Note that 'drone type' is shown as the size of the drone (one space or two
     * space) plus the size of the armor (1/2-PS or one-PS). Type-III-XX drones are treated as
     * one-space drones."</blockquote>
     *
     * <p>A table rather than a multiplier, because it is not one: a half-space module takes a
     * one-space drone from 32 to 20 but a two-space drone from 32 to 26, and the ratios are
     * nothing in particular. The two-space drone with a full module lands on exactly the
     * one-space drone with a half, which is the only regularity in it.
     */
    public int speedWith(DroneFrame frame, Propulsion propulsion) {
        if (!isExternalArmor())
            return propulsion.speed;
        boolean small = this == EXTERNAL_ARMOR_HALF;
        return switch (frame) {
            case ONE_SPACE -> small
                    ? switch (propulsion) { case SPEED_32 -> 20; case SPEED_20 -> 12;
                                            case SPEED_12 -> 8;  case SPEED_8  -> 4; }
                    : switch (propulsion) { case SPEED_32 -> 12; case SPEED_20 -> 8;
                                            case SPEED_12 -> 4;  case SPEED_8  -> 2; };
            case TWO_SPACE -> small
                    ? switch (propulsion) { case SPEED_32 -> 26; case SPEED_20 -> 16;
                                            case SPEED_12 -> 10; case SPEED_8  -> 6; }
                    : switch (propulsion) { case SPEED_32 -> 20; case SPEED_20 -> 12;
                                            case SPEED_12 -> 8;  case SPEED_8  -> 4; };
            // FD12.132's chart has no half-space row, and FD10.21 says why: on a type-VI
            // "only the speed can be varied". DroneSpec refuses the combination before here.
            case HALF_SPACE -> propulsion.speed;
        };
    }

    FrameAddition(double cost, Availability availability, int hullBonus) {
        this.cost = cost;
        this.availability = availability;
        this.hullBonus = hullBonus;
    }
}
