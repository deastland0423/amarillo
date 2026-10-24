package com.sfb.objects;

/**
 * FD10.3: the propulsion section, which is the second of FD10.2's three parts and the only one
 * every frame can vary — including the half-space type-VI, where it is the only thing that can
 * be varied at all (FD10.21).
 *
 * <blockquote>FD10.31: "There are four types of propulsion sections for each size or frame. The
 * addition of higher speeds and/or longer range is covered in (FD2.22) and in (FD10.5). A drone
 * cannot be launched without a propulsion module."</blockquote>
 *
 * <h2>Named for the speed, deliberately, and not for the rule's adjectives</h2>
 * FD10.32 calls speed 8 "SLOW" and FD2.223/FD2.224 call 20 "medium" and 32 "fast". Those names
 * do not survive contact with FD2.227:
 *
 * <blockquote>"Note specifically that type-III or type-VI <b>slow</b> drones are Speed 12 as
 * noted in (FD2.1), not Speed 8."</blockquote>
 *
 * <p>So a "slow" type-III moves at 12 while a "slow" type-I moves at 8, and a constant called
 * SLOW would mean two different speeds depending on the frame bolted to it. Naming these by the
 * number makes that impossible to get wrong, and leaves the adjectives to the costing rules
 * (FD2.22) where they belong — the point at which "slow" means "the baseline you do not pay
 * for" rather than any particular velocity.
 *
 * <p>Availability years and upgrade costs are deliberately absent. FD2.22 prices these and the
 * Annex timeline phases them through limited, restricted and general service in separate years;
 * both are their own slice of work, and a half-built version of either here would be a number
 * someone trusted.
 */
public enum Propulsion {

    /** FD10.32: "the original drones... the baseline for drone cost calculations." */
    SPEED_8(8),

    /** The speed of the transitional type-II and type-V, and of a "slow" type-III (FD2.227). */
    SPEED_12(12),

    /** FD2.223: medium speed, "the speed is increased to twenty". */
    SPEED_20(20),

    /** FD2.224: fast speed, "the speed is increased to 32". */
    SPEED_32(32);

    /** Hexes per turn (FD2.1). */
    public final int speed;

    Propulsion(int speed) {
        this.speed = speed;
    }
}
