package com.sfb.properties;

/**
 * Where a unit is in the planet landing/take-off procedure (P2.4). A Class-M
 * planet holds both an atmosphere level and the surface within its single hex
 * (P2.63), so the phases live on the unit rather than in separate hexes.
 *
 * <ul>
 *   <li>{@link #NONE} — in normal space.</li>
 *   <li>{@link #IN_ATMOSPHERE} — has entered the planet hex at speed ≤ 1 and is
 *       flying in its atmosphere (P2.4112 Step 2 / atmospheric flight P2.80).</li>
 *   <li>{@link #LANDED} — on the surface, on a designated hex side; cannot move
 *       except to take off (P2.45).</li>
 * </ul>
 */
public enum LandingPhase {
    NONE,
    IN_ATMOSPHERE,
    LANDED
}
