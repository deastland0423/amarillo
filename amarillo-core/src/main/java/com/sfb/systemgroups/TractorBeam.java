package com.sfb.systemgroups;

import com.sfb.objects.Unit;

/**
 * A single tractor beam box. Beams are individual objects (like weapons)
 * because each carries distinct state: whether it has been used this turn
 * (G7.13 — one link per beam per turn, not freed by release), and which unit
 * it currently holds. This lets damage and hit-and-run raids name a specific
 * beam (D7.835) and break exactly that link.
 */
public class TractorBeam {

    private final int number;          // 1-based SSD position, for display ("Tractor #2")
    private boolean functional = true;
    private boolean usedThisTurn = false; // G7.13
    private Unit heldUnit = null;

    TractorBeam(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public boolean isFunctional() {
        return functional;
    }

    public boolean isUsedThisTurn() {
        return usedThisTurn;
    }

    public Unit getHeldUnit() {
        return heldUnit;
    }

    /** Free to initiate a new link this turn (G7.13). */
    public boolean isAvailableForNewLink() {
        return functional && !usedThisTurn && heldUnit == null;
    }

    void hold(Unit unit) {
        this.heldUnit = unit;
        this.usedThisTurn = true;
    }

    /** Drop the link; the beam stays used for the rest of the turn (G7.13). */
    void dropLink() {
        this.heldUnit = null;
    }

    /** Expend this beam's per-turn use with no link formed (failed D6.372 attempt). */
    void markUsed() {
        this.usedThisTurn = true;
    }

    void setFunctional(boolean functional) {
        this.functional = functional;
    }

    /** Turn boundary: only beams still holding a persistent link (G7.42) count as used. */
    void resetForTurn() {
        this.usedThisTurn = heldUnit != null;
    }

    /** Display label, e.g. "Tractor #2 — holding IKV Saber". */
    public String describe() {
        return "Tractor #" + number + (heldUnit != null ? " — holding " + heldUnit.getName() : "");
    }
}
