package com.sfb.objects;

/**
 * FD10.53: things bolted to a drone's FRAME rather than filling a payload space, each with a
 * flat price.
 *
 * <blockquote>FD10.22: "Under the point cost system, the frame is effectively free. Exception:
 * type-III, which costs 1/2 point for standard 25-turn range and special guidance. The
 * propulsion (speed) and payload (e.g., warhead) modules, extended range, and any advanced
 * guidance cost points."</blockquote>
 *
 * <p>So the frame is free and these are what make it cost anything. FD10.53 prints all three at
 * half a point, which is why they are a table rather than three fields — a fourth addition in a
 * later product is a constant here and nothing else.
 *
 * <h2>Only the type-III's surcharge does anything yet</h2>
 * ATG and extended range are declared because FD10.53 prices them and the type-III surcharge
 * would otherwise look like a lone special case. Nothing builds a drone with either: ATG has
 * behaviour in FD5.2 and extended range changes the frame's size under FD10.24, and both belong
 * to the slice that implements those rules rather than to this one, which only prices things.
 */
public enum FrameAddition {

    /**
     * FD10.23: "If ATG is added, it is added to the frame (cost = 1/2 point). ATG cannot be
     * added to type-III drone, which already has a special type of guidance." Priced here,
     * unbuilt: see FD5.2 for what it does.
     */
    ACTIVE_TERMINAL_GUIDANCE(0.50),

    /**
     * FD2.222: doubles the endurance, and FD10.24 makes a type-III that takes it a TWO-space
     * drone with one payload space. Priced here, unbuilt, because that frame change is the
     * interesting part and belongs with it.
     */
    EXTENDED_RANGE(0.50),

    /**
     * The type-III's own surcharge, "for standard 25-turn range and special guidance"
     * (FD10.22). The one addition the current roster uses, and the reason an extra type-III
     * costs 2.0 where an extra type-II costs 1.5 (FD2.11) on the same frame at the same speed.
     */
    TYPE_III_GUIDANCE(0.50);

    /** Points, from FD10.53's chart. */
    public final double cost;

    FrameAddition(double cost) {
        this.cost = cost;
    }
}
