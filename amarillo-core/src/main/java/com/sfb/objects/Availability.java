package com.sfb.objects;

/**
 * FD10.6: how hard a drone or one of its parts is to come by.
 *
 * <blockquote>FD10.61: "GENERAL AVAILABILITY: These are the most easily-produced and widely
 * available drones and drone components."<br>
 * FD10.62: "RESTRICTED AVAILABILITY: These types of drones and drone components are not as
 * widely available as General items."<br>
 * FD10.63: "LIMITED AVAILABILITY: These are the most expensive, hardest to build, and least
 * likely to be available drones."</blockquote>
 *
 * <h2>A tier, not a date</h2>
 * The obvious reading of "available from Y165" is a yes/no with a year on it, and that is what
 * {@code DroneType.availableFromYear} has always been. FD10.65 says otherwise: a medium-speed
 * drone is <b>Limited in Y165, Restricted in Y166, and General in Y167</b>. It is available in
 * all three years; what changes is how many of them a ship may carry.
 *
 * <p>So Y165 is the year our data records, and it is the year the thing first exists — but
 * treating it as the year it becomes freely available makes a Y165 fleet able to field a full
 * load of drones that were, historically, barely obtainable.
 *
 * <h2>The tiers NEST, and the caps count downward</h2>
 * <blockquote>FD10.64: "The allowed percentage of Restricted Availability drones includes the
 * limit on Limited Availability drones. EXAMPLE: Kzinti ships can have 50% General Availability,
 * 30% Restricted (and/or General) Availability, and 20% Limited (and/or Restricted and/or
 * General) Availability drones."</blockquote>
 *
 * <p>So a Limited drone also consumes Restricted allowance, and {@link #atLeastAsRestrictedAs}
 * is the comparison a cap check needs: "how many drones are at least this hard to get".
 *
 * <h2>Caps are not here</h2>
 * FD10.622's 25% and FD10.632's 10%, the Kzinti and big-carrier doubling, and the {@code
 * dPercent} hulls are a count across a whole ship measured in warhead spaces (FD10.641). That
 * belongs with the fleet rules, beside the PPD limit and the cartel quota, not on an enum
 * describing one part of one drone.
 */
public enum Availability {

    /** FD10.611: explosive modules, type-I and type-IV frames, extended range. */
    GENERAL(0),

    /** FD10.621: probe modules, ECM, armored drones, ATG guidance. Capped at 25% (FD10.622). */
    RESTRICTED(1),

    /**
     * FD10.631: multi-warhead, swordfish, slug and spearfish drones, external armor, and the
     * type-III frame. Capped at 10% (FD10.632).
     */
    LIMITED(2);

    /** Higher is harder to obtain. Ordering only; no rule assigns these numbers. */
    private final int severity;

    Availability(int severity) {
        this.severity = severity;
    }

    /**
     * Whether this tier is at least as hard to obtain as another — the test a cap needs, since
     * FD10.64 has a Limited drone count against the Restricted allowance too.
     */
    public boolean atLeastAsRestrictedAs(Availability other) {
        return this.severity >= other.severity;
    }

    /**
     * The more restrictive of two tiers.
     *
     * <p>FD10.65's own example: "A type-I drone with medium speed in Y166 (restricted) and a
     * Swordfish module (limited) would be a limited availability drone." A drone is as hard to
     * get as its hardest part.
     */
    public static Availability moreRestrictive(Availability a, Availability b) {
        return a.severity >= b.severity ? a : b;
    }
}
