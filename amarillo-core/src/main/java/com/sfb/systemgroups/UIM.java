package com.sfb.systemgroups;

import com.sfb.objects.Unit;
import com.sfb.utilities.DiceRoller;
import com.sfb.weapons.Disruptor;

import java.util.List;
import java.util.Map;

/**
 * UIM — Ubitron Interface Module.
 *
 * Ship-level targeting enhancement for disruptors (D6.5).
 * Only one UIM is active at a time; up to 3 cold standbys.
 * After the active UIM burns out, the next standby becomes active
 * after an 8-impulse delay.
 *
 * Burnout (D6.52): roll 1d6 at end of every impulse UIM was used;
 * roll of 1 or 2 = burnout. Burned-out UIM is permanently lost.
 * All disruptors that fired under it that impulse are locked out
 * for 32 impulses. Next standby has an 8-impulse activation delay.
 */
public class UIM implements Systems {

    private static final int BURNOUT_THRESHOLD  = 2;  // roll ≤ 2 burns out
    private static final int LOCKOUT_DURATION   = 32; // impulses disruptors are locked
    private static final int STANDBY_DELAY      = 8;  // impulses before standby becomes active

    private boolean damaged         = false;
    private int     standbyActiveAt = -1; // absolute impulse this unit becomes active (−1 = already active)
    private Unit    owningUnit      = null;

    public UIM() {}

    public UIM(Unit owner) {
        this.owningUnit = owner;
    }

    /** A standby UIM scheduled to become active after a burnout. */
    public UIM(Unit owner, int activeAtImpulse) {
        this.owningUnit    = owner;
        this.standbyActiveAt = activeAtImpulse;
    }

    // -------------------------------------------------------------------------
    // Systems interface
    // -------------------------------------------------------------------------

    @Override public void init(Map<String, Object> values) {}
    @Override public int  fetchOriginalTotalBoxes()   { return 1; }
    @Override public int  fetchRemainingTotalBoxes()  { return damaged ? 0 : 1; }
    @Override public void cleanUp() {}
    @Override public Unit fetchOwningUnit()           { return owningUnit; }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** True if functional and active (not in standby delay). */
    public boolean isFunctional(int currentImpulse) {
        if (damaged) return false;
        if (standbyActiveAt < 0) return true;      // already active
        return currentImpulse >= standbyActiveAt;  // standby delay elapsed
    }

    /** Permanently damages this UIM (e.g. via Hit and Run raid). */
    public void damage() { this.damaged = true; }

    public boolean isDamaged() { return damaged; }

    /**
     * Place this standby UIM into a delayed activation state (D6.542).
     * It will become active at the given absolute impulse.
     */
    public void scheduleActivation(int absoluteImpulse) {
        this.standbyActiveAt = absoluteImpulse;
    }

    // -------------------------------------------------------------------------
    // Burnout (D6.52)
    // -------------------------------------------------------------------------

    /**
     * Roll for burnout after an impulse where this UIM was used.
     * On burnout: marks this UIM damaged and locks all supplied disruptors
     * for 32 impulses.
     *
     * @param currentImpulse  The absolute impulse at which the UIM fired.
     * @param firedDisruptors Disruptors that fired under this UIM this impulse.
     * @return true if burnout occurred.
     */
    public boolean checkBurnout(int currentImpulse, List<Disruptor> firedDisruptors) {
        int roll = new DiceRoller().rollOneDie();
        if (roll <= BURNOUT_THRESHOLD) {
            damaged = true;
            int lockUntil = currentImpulse + LOCKOUT_DURATION;
            for (Disruptor d : firedDisruptors) {
                d.setUimLocked(lockUntil);
            }
            return true;
        }
        return false;
    }
}
