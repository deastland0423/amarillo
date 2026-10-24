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
 * <p>Costs come from FD10.52's chart and are indexed by the FRAME's size, so a speed-20 engine
 * is 1.00 on a one-space drone and 0.50 on a half-space one. AVAILABILITY is still absent: the
 * Annex timeline phases each speed through limited, restricted and general service in separate
 * years, and that is its own slice.
 */
public enum Propulsion {

    /**
     * FD10.32: "the original drones... the baseline for drone cost calculations."
     *
     * <p>FD10.52 has a DASH in its 1/2-SP column, not a price: there is no speed-8 dogfight
     * drone, which FD2.227 says in words — "type-III or type-VI slow drones are Speed 12 as
     * noted in (FD2.1), not Speed 8".
     */
    SPEED_8(8, Chart.UNAVAILABLE, 0.50, 1.00),

    /** The speed of the transitional type-II and type-V, and of a "slow" type-III (FD2.227). */
    SPEED_12(12, 0.25, 1.00, 1.50),

    /** FD2.223: medium speed, "the speed is increased to twenty". */
    SPEED_20(20, 0.50, 1.00, 1.50),

    /** FD2.224: fast speed, "the speed is increased to 32". */
    SPEED_32(32, 0.75, 1.50, 2.00);

    /**
     * A dash in FD10.52's chart: this engine is not made for that frame.
     *
     * <p>Held in a nested class because an enum's constants are initialised before its static
     * fields, so a constant cannot name one directly.
     */
    private static final class Chart {
        static final double UNAVAILABLE = -1;
    }

    /** A dash in FD10.52's chart: this engine is not made for that frame. */
    public static final double UNAVAILABLE = Chart.UNAVAILABLE;

    /** Hexes per turn (FD2.1). */
    public final int speed;

    private final double costHalf, costOne, costTwo;

    Propulsion(int speed, double costHalf, double costOne, double costTwo) {
        this.speed = speed;
        this.costHalf = costHalf;
        this.costOne = costOne;
        this.costTwo = costTwo;
    }

    /**
     * FD10.52: what this engine costs on that frame.
     *
     * <p>The chart is indexed by the FRAME's size, not the payload's, which is what makes
     * FD2.226 — "Type-VI speed upgrades cost 1/2 the normal amount" — disappear as a special
     * case: a speed-20 engine is 1.00 on a one-space frame and 0.50 on a half-space one, so the
     * upgrade from speed-12 costs 0.50 and 0.25 respectively without anyone halving anything.
     */
    public double costOn(DroneFrame frame) {
        double cost = switch (frame) {
            case HALF_SPACE -> costHalf;
            case ONE_SPACE -> costOne;
            case TWO_SPACE -> costTwo;
        };
        if (cost == UNAVAILABLE)
            throw new IllegalArgumentException(
                    this + " is not made for a " + frame + " drone (FD10.52)");
        return cost;
    }

    /** Whether FD10.52 prices this engine for that frame at all. */
    public boolean fits(DroneFrame frame) {
        return switch (frame) {
            case HALF_SPACE -> costHalf != UNAVAILABLE;
            case ONE_SPACE -> costOne != UNAVAILABLE;
            case TWO_SPACE -> costTwo != UNAVAILABLE;
        };
    }
}
