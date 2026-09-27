package com.sfb.properties;

/**
 * What kind of carrier a ship is (J4.60), which decides what it may DO with its fighters.
 * <p>
 * Kept apart from the fleet-building question of whether a ship needs escorts (S8.315), and
 * the Hydrans are why: their fighter-carrying ships have a full carrier's capabilities but
 * are fielded without an escort group. One flag answering both questions would have to lie
 * about one of them.
 * <ul>
 *   <li>{@link #CAPABLE} — a fully capable carrier (J4.61), and by J4.623 most Hydran ships
 *       that carry fighters. May buy additional deck crews, lend EW to its fighters, and
 *       gets S4.1's weapon status provisions: fighters armed at WS-0, deck crew work before
 *       WS-2, a Combat Space Patrol.</li>
 *   <li>{@link #CASUAL} — a casual carrier (J4.62): it has fighters aboard but none of the
 *       apparatus. It may not buy extra deck crews.</li>
 *   <li>{@link #NONE} — carries no fighters at all.</li>
 * </ul>
 * A three-state question, so an enum rather than a pair of booleans: "casual and capable" is
 * not a thing a ship can be, and nothing should be able to write it down.
 */
public enum CarrierClass {

    /** No fighters aboard. */
    NONE,

    /** J4.62: fighters, but not the apparatus of a carrier. */
    CASUAL,

    /** J4.61 (and J4.623 for the Hydrans): a carrier in full. */
    CAPABLE;

    /** Whether this ship gets the carrier provisions the rules extend to carriers (S4.1). */
    public boolean isCarrier() {
        return this == CAPABLE;
    }

    /** Lenient parse for ship files; anything unrecognised is NONE. */
    public static CarrierClass from(Object raw) {
        if (raw instanceof CarrierClass c)
            return c;
        if (raw == null)
            return NONE;
        try {
            return valueOf(raw.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
