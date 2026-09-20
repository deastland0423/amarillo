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
     * One box, identified by its position on the SSD.
     *
     * The NUMBER is the identity, not the position in this list: destroying a box removes
     * it, and everything after would otherwise shift. A hit-and-run raid names a box
     * (D7.835), and a scout channel holds one across four attempts (G24.251) — both would
     * quietly follow the wrong box if identity moved when a neighbour was shot off.
     */
    private static final class Box {
        final int number;
        int lastUsed = NEVER;

        Box(int number) {
            this.number = number;
        }
    }

    private final List<Box> boxes = new ArrayList<>();

    /** Build a bank of {@code count} undamaged, unused boxes, numbered from 1. */
    public void init(int count) {
        total = Math.max(0, count);
        boxes.clear();
        for (int i = 1; i <= total; i++)
            boxes.add(new Box(i));
    }

    /** Boxes printed on the SSD, damaged or not. */
    public int total() {
        return total;
    }

    /** Boxes that still exist: undamaged, whether or not they are busy. */
    public int functioning() {
        return boxes.size();
    }

    /** The numbers of the boxes that still exist, in SSD order. */
    public List<Integer> numbers() {
        List<Integer> out = new ArrayList<>();
        for (Box b : boxes)
            out.add(b.number);
        return out;
    }

    /** Boxes free to take a job at this impulse. */
    public int available(int absoluteImpulse) {
        int free = 0;
        for (Box b : boxes)
            if (cycleFree(b.lastUsed, absoluteImpulse))
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

    private Box find(int number) {
        for (Box b : boxes)
            if (b.number == number)
                return b;
        return null;
    }

    /** True if the numbered box may be put to work at this impulse. */
    public boolean isFree(int number, int absoluteImpulse) {
        Box b = find(number);
        return b != null && cycleFree(b.lastUsed, absoluteImpulse);
    }

    /** True if the numbered box was used during the turn this impulse falls in. */
    public boolean usedThisTurn(int number, int absoluteImpulse) {
        Box b = find(number);
        return b != null && b.lastUsed != NEVER
            && turnOf(b.lastUsed) == turnOf(absoluteImpulse);
    }

    /** How many boxes were used during the turn this impulse falls in. */
    public int usesThisTurn(int absoluteImpulse) {
        int used = 0;
        for (Box b : boxes)
            if (b.lastUsed != NEVER && turnOf(b.lastUsed) == turnOf(absoluteImpulse))
                used++;
        return used;
    }

    /** What a box is doing, for a raid's target list or a readout. */
    public enum State { UNUSED, USED_THIS_TURN, COOLING_DOWN }

    public State stateOf(int number, int absoluteImpulse) {
        Box b = find(number);
        if (b == null || b.lastUsed == NEVER)
            return State.UNUSED;
        if (turnOf(b.lastUsed) == turnOf(absoluteImpulse))
            return State.USED_THIS_TURN;
        return cycleFree(b.lastUsed, absoluteImpulse) ? State.UNUSED : State.COOLING_DOWN;
    }

    /**
     * Put a box to work at this impulse and return its NUMBER, or -1 if none is free. The
     * number matters to a caller that holds its box across several attempts — a scout
     * channel gets four out of one lab (G24.251) — and must keep stamping the same one.
     */
    public int use(int absoluteImpulse) {
        for (Box b : boxes)
            if (cycleFree(b.lastUsed, absoluteImpulse)) {
                b.lastUsed = absoluteImpulse;
                return b.number;
            }
        return -1;
    }

    /** Re-stamp a box already held, so its delay runs from this use rather than the first. */
    public void markUsed(int number, int absoluteImpulse) {
        Box b = find(number);
        if (b != null)
            b.lastUsed = absoluteImpulse;
    }

    /**
     * Destroy one box, taking the least valuable first: one already used this turn, then
     * one still cooling off from last turn, then an unused one. No player decision — the
     * order is always the same, and it is the order a player would choose anyway.
     *
     * @return false if there was nothing left to destroy
     */
    public boolean damage() {
        if (boxes.isEmpty())
            return false;

        Box worst = boxes.get(0);
        for (Box b : boxes)
            if (b.lastUsed > worst.lastUsed)
                worst = b;
        boxes.remove(worst);
        return true;
    }

    /**
     * Destroy one NAMED box, for a hit-and-run raid that picked it deliberately (D7.835).
     * A raider wants an unused one, since a box already spent this turn costs its owner
     * far less.
     *
     * @return false if that box does not exist or is already destroyed
     */
    public boolean damageBox(int number) {
        Box b = find(number);
        if (b == null)
            return false;
        boxes.remove(b);
        return true;
    }

    /**
     * Restore repaired boxes, up to the SSD count. A restored box is free immediately;
     * G4.31 has it assume its function at the start of the next turn, which is not modelled.
     */
    public boolean repair(int value) {
        if (boxes.size() + value > total)
            return false;

        // Reuse the lowest numbers not currently present, so the SSD reads sensibly.
        for (int i = 0; i < value; i++)
            for (int n = 1; n <= total; n++)
                if (find(n) == null) {
                    boxes.add(new Box(n));
                    break;
                }
        boxes.sort((a, b) -> Integer.compare(a.number, b.number));
        return true;
    }

    /** The turn an absolute impulse falls in; matches Game.getCurrentTurn(). */
    public static int turnOf(int absoluteImpulse) {
        return (absoluteImpulse - 1) / IMPULSES_PER_TURN;
    }
}
