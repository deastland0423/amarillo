package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;

/**
 * A bank of SSD boxes that are used one at a time and then have to cool off.
 *
 * Several systems work this way and the rule is the same for each: a specific box is used
 * on a given attempt, and that box is unavailable until the next turn OR eight impulses
 * later, whichever is longer. G4.22 and G4.451 spell it out for laboratories; transporters
 * and tractors follow the same shape.
 * <p>
 * Both halves of that rule are needed and neither implies the other. Once per turn alone
 * would let a box work on impulse 32 and again on impulse 1 — a quarter turn apart in the
 * fiction, two consecutive impulses at the table, which is the trick the delay exists to
 * stop. The eight-impulse delay alone would free a box at impulse 9 of the same turn.
 * <p>
 * Keeping a per-box impulse also separates three things that a single counter cannot hold
 * at once: how many boxes the SSD has, how many survive damage, and how many are free to
 * take a job right now. Merging them meant a destroyed box came back at the start of the
 * next turn, and a box merely USED counted as missing for cripple calculations.
 */
public class BoxCycle {

    /** Impulses in a quarter turn (G4.451). */
    public static final int QUARTER_TURN = 8;

    private static final int IMPULSES_PER_TURN = 32;

    /** A box that has never been used; older than any impulse that can occur. */
    public static final int NEVER = Integer.MIN_VALUE / 2;

    /** Boxes printed on the SSD, for cripple calculations. */
    private int total;

    /**
     * One entry per UNDAMAGED box: the absolute impulse it was last used, or {@link #NEVER}.
     * Its size is the number of functioning boxes; damage removes an entry, repair adds one.
     */
    private final List<Integer> lastUsed = new ArrayList<>();

    /** Build a bank of {@code boxes} undamaged, unused boxes. */
    public void init(int boxes) {
        total = Math.max(0, boxes);
        lastUsed.clear();
        for (int i = 0; i < total; i++)
            lastUsed.add(NEVER);
    }

    /** Boxes printed on the SSD, damaged or not. */
    public int total() {
        return total;
    }

    /** Boxes that still exist: undamaged, whether or not they are busy. */
    public int functioning() {
        return lastUsed.size();
    }

    /** Boxes free to take a job at this impulse. */
    public int available(int absoluteImpulse) {
        int free = 0;
        for (int i = 0; i < lastUsed.size(); i++)
            if (isFree(i, absoluteImpulse))
                free++;
        return free;
    }

    /**
     * The rule itself, for systems that hold their own box state rather than a bank of
     * them. A tractor beam is the case: it can be IN USE, holding something across
     * impulses, which no other system can, so it keeps its own object and borrows this.
     *
     * @param lastUsed the absolute impulse the box was last used, or {@link #NEVER}
     */
    public static boolean cycleFree(int lastUsed, int absoluteImpulse) {
        if (lastUsed == NEVER)
            return true;
        // One use per box per turn, however long ago in the turn it was...
        if (turnOf(lastUsed) == turnOf(absoluteImpulse))
            return false;
        // ...and the eight-impulse delay on top, which is what carries across the boundary.
        return absoluteImpulse - lastUsed >= QUARTER_TURN;
    }

    /** True if box {@code index} may be put to work at this impulse. */
    public boolean isFree(int index, int absoluteImpulse) {
        if (index < 0 || index >= lastUsed.size())
            return false;
        return cycleFree(lastUsed.get(index), absoluteImpulse);
    }

    /** True if this box has been used during the turn {@code absoluteImpulse} falls in. */
    public boolean usedThisTurn(int index, int absoluteImpulse) {
        if (index < 0 || index >= lastUsed.size())
            return false;
        int last = lastUsed.get(index);
        return last != NEVER && turnOf(last) == turnOf(absoluteImpulse);
    }

    /** How many boxes have been used during the turn {@code absoluteImpulse} falls in. */
    public int usesThisTurn(int absoluteImpulse) {
        int used = 0;
        for (int i = 0; i < lastUsed.size(); i++)
            if (usedThisTurn(i, absoluteImpulse))
                used++;
        return used;
    }

    /**
     * Put a box to work at this impulse and return which one, or -1 if none is free. The
     * index matters to a caller that holds its box across several attempts — a scout
     * channel gets four out of one lab (G24.251) — and must keep stamping the same one.
     */
    public int use(int absoluteImpulse) {
        for (int i = 0; i < lastUsed.size(); i++)
            if (isFree(i, absoluteImpulse)) {
                lastUsed.set(i, absoluteImpulse);
                return i;
            }
        return -1;
    }

    /** Re-stamp a box already held, so its delay runs from this use rather than the first. */
    public void markUsed(int index, int absoluteImpulse) {
        if (index >= 0 && index < lastUsed.size())
            lastUsed.set(index, absoluteImpulse);
    }

    /**
     * Destroy one box, taking the least valuable first: one already used this turn, then
     * one still cooling off from last turn, then an unused one. No player decision — the
     * order is always the same, and it is the order a player would choose anyway.
     *
     * @return false if there was nothing left to destroy
     */
    public boolean damage() {
        if (lastUsed.isEmpty())
            return false;

        int worst = 0;
        for (int i = 1; i < lastUsed.size(); i++)
            if (lastUsed.get(i) > lastUsed.get(worst))
                worst = i;
        lastUsed.remove(worst);
        return true;
    }

    /**
     * Restore repaired boxes, up to the SSD count. A restored box is free immediately;
     * G4.31 has it assume its function at the start of the next turn, which is not modelled.
     */
    public boolean repair(int value) {
        if (lastUsed.size() + value > total)
            return false;

        for (int i = 0; i < value; i++)
            lastUsed.add(NEVER);
        return true;
    }

    /** The turn an absolute impulse falls in; matches Game.getCurrentTurn(). */
    public static int turnOf(int absoluteImpulse) {
        return (absoluteImpulse - 1) / IMPULSES_PER_TURN;
    }
}
