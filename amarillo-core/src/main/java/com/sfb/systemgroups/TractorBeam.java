package com.sfb.systemgroups;

import com.sfb.objects.Tractorable;

/**
 * A single tractor beam box. Beams are individual objects (like weapons)
 * because each carries distinct state: when it was last used (G7.13 — one link per beam
 * per turn, not freed by release, and then the quarter-turn delay on top), and which unit
 * it currently holds. This lets damage and hit-and-run raids name a specific beam (D7.835)
 * and break exactly that link.
 * <p>
 * A beam has three states where most systems have two. It can be unused; it can be spent,
 * cooling off like a lab or a transporter box; or it can be IN USE, holding something
 * across impulses, which no other system does. The first two are the shared cycle
 * ({@link BoxCycle#cycleFree}); the third is this class's own business.
 */
public class TractorBeam {

    private final int number;          // 1-based SSD position, for display ("Tractor #2")
    private boolean functional = true;
    /** Absolute impulse this beam was last used, or {@link BoxCycle#NEVER} (G7.13). */
    private int lastUsedImpulse = BoxCycle.NEVER;
    private Tractorable heldUnit = null;

    TractorBeam(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public boolean isFunctional() {
        return functional;
    }

    /** Spent during the turn this impulse falls in. */
    public boolean isUsedThisTurn(int absoluteImpulse) {
        return lastUsedImpulse != BoxCycle.NEVER
            && BoxCycle.turnOf(lastUsedImpulse) == BoxCycle.turnOf(absoluteImpulse);
    }

    /** Holding something right now — the state only a tractor has. */
    public boolean isInUse() {
        return heldUnit != null;
    }

    public Tractorable getHeldUnit() {
        return heldUnit;
    }

    /**
     * Free to initiate a new link: undamaged, not holding anything, and past the cycle —
     * the next turn AND eight impulses since it was last used, whichever is longer
     * (G7.13). It used to free up at the turn boundary alone, so a beam used on impulse 30
     * was ready again on impulse 1.
     */
    public boolean isAvailableForNewLink(int absoluteImpulse) {
        return functional && heldUnit == null
            && BoxCycle.cycleFree(lastUsedImpulse, absoluteImpulse);
    }

    void hold(Tractorable unit, int absoluteImpulse) {
        this.heldUnit = unit;
        this.lastUsedImpulse = absoluteImpulse;
    }

    /** Drop the link; the beam stays used for the rest of the turn (G7.13). */
    void dropLink() {
        this.heldUnit = null;
    }

    /** Expend this beam's use with no link formed (failed D6.372 attempt). */
    void markUsed(int absoluteImpulse) {
        this.lastUsedImpulse = absoluteImpulse;
    }

    void setFunctional(boolean functional) {
        this.functional = functional;
    }

    /**
     * A beam holding a persistent link across the turn boundary (G7.42) keeps holding it,
     * and there is nothing else to reset: availability is worked out from the impulse it
     * was last used, so the turn boundary takes care of itself.
     */
    void keepHoldingAcrossTurn(int absoluteImpulse) {
        if (heldUnit != null)
            this.lastUsedImpulse = absoluteImpulse;
    }

    /** Display label, e.g. "Tractor #2 — holding IKV Saber". */
    public String describe() {
        return "Tractor #" + number + (heldUnit != null ? " — holding " + heldUnit.getName() : "");
    }
}
