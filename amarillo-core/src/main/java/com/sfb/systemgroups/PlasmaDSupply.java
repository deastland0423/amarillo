package com.sfb.systemgroups;

/**
 * Somewhere type-D plasma torpedoes are kept, which the other such place may fall back on.
 *
 * <h2>The rule</h2>
 * FP10.31: "Plasma-Ds stored for use by fighters are <b>interchangeable</b> with those stored
 * for use by plasma racks." A ship can hold them in two places — {@link DroneStore}, the
 * carrier's supply for its fighters (J4.7), and {@link PlasmaReloadStore}, the hold its plasma
 * racks reload from (FP10.31) — and a torpedo in either will serve either purpose.
 *
 * <h2>Each draws on its own first</h2>
 * Owner's reading, 2026-10-20: "Pull from the normal place for each (racks, fighters). Only when
 * the normal resource is out, do you pull from the other one instead." So this is a fallback and
 * not a merged pool: a rack empties the rack hold before touching the fighters' supply, and a
 * fighter empties the fighters' supply before touching the racks'. Nobody is asked to choose,
 * which is why the two stores stay separate objects rather than becoming one.
 *
 * <h2>Why the methods say HERE</h2>
 * Both stores implement this and each holds the other as its fallback, so a draw that fell
 * through twice would come straight back to where it started. These two methods are therefore
 * the <b>own-stock-only</b> ones, and a fallback is only ever asked through them — never through
 * the public draw that would fall back again.
 */
public interface PlasmaDSupply {

    /** Type-Ds in THIS store, ignoring anything it could fall back on. */
    int plasmaDHeldHere();

    /**
     * Draw up to {@code wanted} from THIS store only, returning how many there were.
     * <p>
     * Never consults a fallback: this IS the method a fallback is asked through.
     */
    int takePlasmaDHere(int wanted);
}
