package com.sfb.scenario;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * FD2.45's "single loading", for the two rack types that keep their reloads inside themselves.
 *
 * <blockquote>FD2.45: "The cost of improved drones (FD2.22) is paid only for a single loading of
 * each launch rack and/or fighter; the reloads ... are presumed to be of the same type without
 * additional cost. There is a partial exception in the case of H-racks (FD3.84). D-racks are
 * another exception."</blockquote>
 *
 * <h2>Why a rack ever needs this</h2>
 * For every ordinary rack the rule costs nothing to obey: the reloads sit in a separate list
 * ({@code DroneRack.getReloads()}) that nothing prices, so "one loading paid" falls out of where
 * the drones are kept. A magazine launcher has no such list — FD3.43, "while type-D (and type-H)
 * drone racks do not have formal reloads" — because the spare magazines ARE the reloads, already
 * loaded and launch-ready. All twelve spaces of a type-D come back from one call, and a bill that
 * walked them charged the free magazines along with the paid one.
 *
 * <h2>Which loading is the paid one</h2>
 * The rules do not have the player nominate it, but FD2.452 settles it anyway: "The reload drones
 * are presumed to be of the same cost (or lower) as the drones in the rack." A free reload may be
 * no dearer than what was paid for, so the paid magazine has to be the dearest one in the
 * launcher. Without that the exception is an exploit — fill one magazine with slow drones, call
 * that the paid one, and fly two magazines of expensive ones for nothing.
 *
 * <h2>What this actually computes, and where it is not exact</h2>
 * We do not model which drone sits in which magazine; a rack holds a flat list. So "the dearest
 * magazine" is approximated by <b>the dearest {@code paidSpaces} worth of drones anywhere in the
 * rack</b>, which is never less than the dearest real magazine — any one magazine is itself a
 * candidate subset — and so can only err toward charging. That direction is the deliberate one:
 * a load the player arranged to cheat the rule is billed as though they had not.
 *
 * <p>The two bills that use this — the ship's drone-speed charge and the Commander's Option
 * surcharge — pick independently, each dearest in its own currency, rather than agreeing on one
 * magazine and charging both against it. That is the same conservative choice. A single pick by
 * total drone cost would let a magazine of dear but slow special drones carry the title and leave
 * the fast drones beside it unbilled for speed, which is the hole FD2.452 exists to close.
 *
 * <h2>Exactly, not greedily</h2>
 * It is a knapsack, and the greedy "dearest first, skip what will not fit" reading of it is
 * wrong in a way a player can arrange: with four spaces to fill, one type-IV worth a point beats
 * any single dogfight drone, but eight type-VIs in those same four spaces are worth four. Both
 * the capacity and the drone sizes are multiples of half a space (FD2.1, FD3.5), so the exact
 * answer is a sixteen-cell table rather than an argument.
 */
final class PaidLoading {

    private PaidLoading() {
    }

    /** Half a space is the smallest a drone comes in (FD3.5's type-VI), so it is the DP's unit. */
    private static final double UNIT = 0.5;

    /**
     * What the dearest loading of {@code paidSpaces} spaces out of {@code load} is worth.
     *
     * @param load       every drone in the rack, free magazines included
     * @param paidSpaces {@code DroneRack.paidLoadingSpaces()} — the spaces FD2.45 charges for
     * @param spaces     how much rack a drone takes (FD2.1)
     * @param price      what that drone costs the bill being drawn up; a drone worth nothing, or
     *                   less than nothing, is simply left out rather than credited (FD10.48)
     */
    static <T> double dearestLoading(List<T> load, double paidSpaces,
            ToDoubleFunction<T> spaces, ToDoubleFunction<T> price) {
        int capacity = (int) Math.round(paidSpaces / UNIT);
        if (load == null || load.isEmpty() || capacity <= 0)
            return 0;

        // best[w] = the dearest loading that fits in w half-spaces. Zero-filled, because a
        // loading is never obliged to use the room it has.
        double[] best = new double[capacity + 1];
        for (T item : load) {
            double worth = price.applyAsDouble(item);
            if (worth <= 0)
                continue;
            int size = (int) Math.round(spaces.applyAsDouble(item) / UNIT);
            if (size <= 0 || size > capacity)
                continue;
            // Descending, so each drone is offered to the magazine once and not reloaded
            // into it out of its own shadow.
            for (int w = capacity; w >= size; w--)
                best[w] = Math.max(best[w], best[w - size] + worth);
        }
        return best[capacity];
    }
}
