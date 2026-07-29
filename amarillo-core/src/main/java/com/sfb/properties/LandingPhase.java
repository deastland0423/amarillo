package com.sfb.properties;

/**
 * Where a unit is in the planet landing/take-off procedure (P2.4). A Class-M
 * planet holds both an atmosphere level and the surface within its single hex
 * (P2.63), so the phases live on the unit rather than in separate hexes.
 *
 * <ul>
 *   <li>{@link #NONE} — in normal space.</li>
 *   <li>{@link #DESCENDING} — entered the planet hex at speed ≤ 1 and is flying
 *       down through its atmosphere; lands the next turn (P2.4112/P2.4113).</li>
 *   <li>{@link #LANDED} — on the surface, on a designated hex side; cannot move
 *       except to take off (P2.45).</li>
 *   <li>{@link #CLIMBING} — has taken off from the surface and is flying up
 *       through the atmosphere; may leave the planet hex the next turn (P2.412).</li>
 * </ul>
 */
public enum LandingPhase {
    NONE,
    DESCENDING,
    LANDED,
    CLIMBING;

    /** Flying within the planet hex (either direction) — for atmosphere effects. */
    public boolean isInAtmosphere() {
        return this == DESCENDING || this == CLIMBING;
    }
}
