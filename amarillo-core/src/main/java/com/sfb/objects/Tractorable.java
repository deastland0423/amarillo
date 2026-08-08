package com.sfb.objects;

import com.sfb.properties.Location;

/**
 * The minimal contract a tractor beam needs from whatever it holds. A held
 * thing has a name (for logs), a map hex the beam works on (range, pull-in),
 * and a back-reference to the {@link Ship} tractoring it so the beam and its
 * captive stay in agreement (G7.42 persistence, J1.6221 link-break cleanup).
 *
 * <p>Implemented by {@link Unit} (ships, seekers, shuttles — the classic
 * targets) and by {@link Objective} (SH35 probe canisters, brought aboard with
 * the friendly-shuttle tractor rotation system J1.621). Objectives are inert:
 * they have no EW, no shields, and are never subject to death dragging
 * (G7.54 / SH35.452), so the beam holds them but none of the Unit-only tractor
 * physics applies.
 */
public interface Tractorable {

    String getName();

    Location getLocation();

    void setLocation(Location location);

    /** The ship currently tractoring this thing, or null. */
    Unit getTractoringUnit();

    /** Record that {@code holder} has taken this thing in a tractor beam. */
    void applyTractor(Unit holder);

    /** Break the tractor back-reference (the beam clears its own side). */
    void releaseTractor();
}
