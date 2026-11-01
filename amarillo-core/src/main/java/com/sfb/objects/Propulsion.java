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
    SPEED_8(8, Chart.UNAVAILABLE, 0.50, 1.00, Chart.ALWAYS_GENERAL, Chart.ALWAYS_GENERAL),

    /** The speed of the transitional type-II and type-V, and of a "slow" type-III (FD2.227). */
    SPEED_12(12, 0.25, 1.00, 1.50, 100, 120),

    /** FD2.223: medium speed, "the speed is increased to twenty". */
    SPEED_20(20, 0.50, 1.00, 1.50, 166, 167),

    /** FD2.224: fast speed, "the speed is increased to 32". */
    SPEED_32(32, 0.75, 1.50, 2.00, 179, 180);

    /**
     * A dash in FD10.52's chart: this engine is not made for that frame.
     *
     * <p>Held in a nested class because an enum's constants are initialised before its static
     * fields, so a constant cannot name one directly.
     */
    private static final class Chart {
        static final double UNAVAILABLE = -1;
        /** Speed-8 is the free baseline every ship already has (FD2.22). */
        static final int ALWAYS_GENERAL = 0;
    }

    /** A dash in FD10.52's chart: this engine is not made for that frame. */
    public static final double UNAVAILABLE = Chart.UNAVAILABLE;

    /** Hexes per turn (FD2.1). */
    public final int speed;

    private final double costHalf, costOne, costTwo;
    private final int restrictedFrom, generalFrom;

    Propulsion(int speed, double costHalf, double costOne, double costTwo,
            int restrictedFrom, int generalFrom) {
        this.speed = speed;
        this.costHalf = costHalf;
        this.costOne = costOne;
        this.costTwo = costTwo;
        this.restrictedFrom = restrictedFrom;
        this.generalFrom = generalFrom;
    }

    /**
     * FD10.65/FD10.651: how obtainable this engine is in a given year.
     *
     * <blockquote>"Medium speed drones are Limited in Y165, Restricted in Y166, General in
     * Y167. Fast drones are Limited in Y178, Restricted in Y179, General in Y180... Moderate
     * speed drones were invented in Y77 and were Limited Availability until Y100, then became
     * Restricted Availability. They became General Availability items in Y120."</blockquote>
     *
     * <p>A speed that has not been invented yet is not Limited, it is nothing, so callers ask
     * {@link #existsIn} first. Speed-8 is the baseline every ship's BPV already pays for
     * (FD2.22) and is General from the beginning.
     */
    public Availability availabilityIn(int year) {
        if (year >= generalFrom)
            return Availability.GENERAL;
        if (year >= restrictedFrom)
            return Availability.RESTRICTED;
        return Availability.LIMITED;
    }

    /** The year this engine first exists at all, which is its Limited year (FD10.651). */
    public int firstYear() {
        return switch (this) {
            case SPEED_8 -> 0;
            case SPEED_12 -> 77;
            case SPEED_20 -> 165;
            case SPEED_32 -> 178;
        };
    }

    /** Whether this engine has been invented yet (FD10.651). */
    public boolean existsIn(int year) {
        return year >= firstYear();
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
            // FD10.24: "Speed upgrade costs for type-III-XX drones are the same as for the
            // two-space drone (FD10.52)." Said outright because the frame it grew from is a
            // one-space drone, and the chart would otherwise be read from the wrong column.
            case TWO_SPACE, TYPE_III_XX -> costTwo;
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
            case TWO_SPACE, TYPE_III_XX -> costTwo != UNAVAILABLE;
        };
    }

    /**
     * The slowest engine FD10.52's chart builds for this frame, which is the speed a ship's
     * stated BPV already covers.
     *
     * <blockquote>FD2.22: "All costs are based on additions to the basic cost of a type-I-slow
     * drone. The BPV of all ships includes type-I-slow drones; the surcharges for improvements
     * below must be paid in all cases (except X-ships)."</blockquote>
     *
     * <p>Speed-8 for the frames that have one; speed-12 for the half-space, whose speed-8 row
     * the chart prints as a dash. FD2.227 says the same thing in words -- "type-III or type-VI
     * slow drones are Speed 12 as noted in (FD2.1), not Speed 8".
     *
     * <h2>Why the FRAME and not the drone's family</h2>
     * Half of that sentence is a frame fact and half of it is not, and reading it as one would
     * cost the game half a point per type-III. The type-VI's speed-12 is free because nothing
     * slower is built for a half-space frame. The type-III's is not, and FD10.54 settles it
     * outright: "It actually costs 0.5 points to trade a type-I drone for a type-III, but this
     * results in a Speed 8 type-III drone. (Technically, there is no such thing, but it is
     * theoretically possible to build one.) Trading a Speed 8 type-I drone for a Speed 12
     * type-III costs 1.0 points, not 0.5 points." So a type-III pays for moderate speed like
     * every other one-space drone, and the baseline is keyed on the frame alone.
     */
    public static Propulsion baselineFor(DroneFrame frame) {
        // values() runs slowest-first, so the first that fits is the cheapest the chart prices.
        for (Propulsion p : values())
            if (p.fits(frame))
                return p;
        throw new IllegalArgumentException("FD10.52 prices no engine for a " + frame + " drone");
    }

    /**
     * FD2.22's surcharge for this engine over the frame's free baseline: what is paid to fly
     * something faster than the drones the ship's BPV came with.
     *
     * <p>Checks out against FD2.454's worked example, which is the one place the rulebook does
     * this arithmetic for a whole ship: a Kzinti CS has a "BPV of 116. However, it has four
     * drone racks, each of which holds four drones. Equipped with medium-speed drones, the BPV
     * increases by eight points (1/2-point per drone) to 124. The BPV of a Kzinti CS armed with
     * fast drones is 132." Sixteen one-space drones at 0.50 and at 1.00 apiece -- which is
     * speed-20 and speed-32 priced against speed-8 in the one-space column, and nothing else.
     */
    public double surchargeOn(DroneFrame frame) {
        return costOn(frame) - baselineFor(frame).costOn(frame);
    }

    /**
     * The fastest engine in general service in a given year that this frame is built for
     * (FD10.65) -- the speed a ship is normally assumed to sail with.
     *
     * <blockquote>S2.11: "The BPV of all ships (unless otherwise noted) does not include the
     * drone speed upgrade charges; ships are required to pay these charges based on the date of
     * the scenario. Players may use slower drones in special cases (asteroids, webs, bases)
     * where faster drones are not appropriate."</blockquote>
     *
     * <p>GENERAL, not merely invented, because a faster drone inside its limited or restricted
     * window is a Commander's Option rather than standard issue. S3.221: "Drone speed upgrade
     * charges are not included within the limit on Commander's Options... Exception: Faster
     * drones available as limited or restricted weapons in years prior to their general
     * introduction are purchased as Commander's Options."
     *
     * <p>Never slower than {@link #baselineFor}, which is what answers a half-space frame
     * before Y120: no engine is in general service yet and its slowest is speed-12 regardless.
     */
    public static Propulsion generalIn(int year, DroneFrame frame) {
        Propulsion best = baselineFor(frame);
        for (Propulsion p : values())
            if (p.isImprovement() && p.fits(frame) && p.speed > best.speed
                    && p.availabilityIn(year) == Availability.GENERAL)
                best = p;
        return best;
    }

    /**
     * Whether FD2.22 counts this engine as an IMPROVEMENT -- one of the things a ship is normally
     * fitted with once its year comes, rather than a different drone a player might buy.
     *
     * <h2>Speed-12 is not on FD2.22's list, and that is not an oversight</h2>
     * The rule enumerates its improvements and there are four: "(FD2.221) Active terminal
     * guidance... (FD2.222) Extended range... (FD2.223) Medium speed (the speed is increased to
     * twenty)... (FD2.224) Fast speed (the speed is increased to 32)". Moderate speed is absent,
     * because the moderate engine is not an upgrade bolted to a type-I -- it is what makes a
     * type-II a type-II, and FD2.1 gives that drone TWO turns of endurance where the type-I has
     * three. Trading up to it costs a third of the drone's legs, which is a tactical choice and
     * not standard issue.
     *
     * <p>FD10.65 does say moderate speed "became General Availability items in Y120", and that
     * is about how easy the engine is to GET -- the same ladder FD10.6 runs every drone component
     * up. It does not put a type-II in every rack from Y120, and reading it that way billed a
     * Y125 Kzinti CS eight points to swap three turns of endurance for two.
     *
     * <p>So it still has a price ({@link #surchargeOn} prices what FD10.52 prices) and a player
     * may still order one out of their option budget. What it does not do is arrive uninvited.
     */
    public boolean isImprovement() {
        return this == SPEED_20 || this == SPEED_32;
    }
}
