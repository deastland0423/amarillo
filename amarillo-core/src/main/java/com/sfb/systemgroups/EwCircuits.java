package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;

/**
 * Electronic warfare circuits (D6.31).
 *
 * A ship has one EW circuit per point of current sensor rating (D6.312); each
 * circuit generates one point of ECM or ECCM while powered. Rules carried by
 * this model:
 *
 * - A given circuit may switch between ECM and ECCM at most once every eight
 *   consecutive impulses (D6.316). The commitment persists across turn
 *   boundaries and across power loss (D6.312).
 * - Points added mid-turn are bought with reserve power and expire at the end
 *   of the turn (D6.312).
 * - Dropped points are irrevocably lost for the turn; re-adding costs reserve
 *   power (D6.315-A).
 * - All powering is per-turn: end-of-turn cleanup unpowers every circuit;
 *   Energy Allocation re-powers them (modes and lockouts persist).
 *
 * The circuit list is capped by the CURRENT sensor rating at each operation,
 * so sensor damage shrinks the usable pool naturally.
 */
public class EwCircuits {

    public enum Mode { ECM, ECCM }

    static final int SWITCH_LOCKOUT_IMPULSES = 8;

    private static class Circuit {
        Mode mode = null;                              // null = never assigned
        // Initialized so canFlip() is true from impulse 0 without overflow
        // (never use Integer.MIN_VALUE here — the subtraction would wrap)
        int lastSwitchImpulse = -SWITCH_LOCKOUT_IMPULSES;
        boolean powered = false;
        boolean reserveBought = false;
    }

    private final List<Circuit> circuits = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public int getEcm()  { return countPowered(Mode.ECM); }
    public int getEccm() { return countPowered(Mode.ECCM); }

    private int countPowered(Mode mode) {
        int n = 0;
        for (Circuit c : circuits)
            if (c.powered && c.mode == mode)
                n++;
        return n;
    }

    /** Battery cost of moving to the given totals: one per point added (D6.312). */
    public int batteryCost(int newEcm, int newEccm) {
        return Math.max(0, newEcm - getEcm()) + Math.max(0, newEccm - getEccm());
    }

    /** Circuits whose mode is flip-locked at the given impulse, with soonest unlock. */
    public int lockedCircuitCount(int impulse) {
        int n = 0;
        for (Circuit c : circuits)
            if (!canFlip(c, impulse))
                n++;
        return n;
    }

    // -------------------------------------------------------------------------
    // Energy Allocation (turn start)
    // -------------------------------------------------------------------------

    /**
     * Assign this turn's allocated EW (D6.310). Prefers circuits already
     * committed to the matching mode, then never-used circuits, then flips
     * unlocked ones. Returns an error message if the allocation would require
     * flipping a circuit still inside its 8-impulse commitment (D6.312) —
     * allocate compatibly and add the rest with reserve power after it
     * unlocks. Null on success.
     */
    public String allocate(int ecm, int eccm, int impulse, int sensorRating) {
        ensureSize(sensorRating);
        List<Circuit> snapshot = snapshot();
        for (Circuit c : circuits) {         // fresh turn — nothing powered yet
            c.powered = false;
            c.reserveBought = false;
        }
        String err = power(Mode.ECM, ecm, impulse, sensorRating, false);
        if (err == null)
            err = power(Mode.ECCM, eccm, impulse, sensorRating, false);
        if (err != null)
            restore(snapshot); // all-or-nothing — the player re-submits
        return err;
    }

    // -------------------------------------------------------------------------
    // Mid-turn adjustment (Fire Decision Step, D6.315)
    // -------------------------------------------------------------------------

    /**
     * Move to the given totals mid-turn. Drops are free and irrevocable;
     * additions are reserve-bought (caller pays battery via batteryCost()).
     * All-or-nothing: on error the circuits are untouched and the message is
     * returned; null on success.
     */
    public String adjust(int newEcm, int newEccm, int impulse, int sensorRating) {
        if (newEcm < 0 || newEccm < 0)
            return "EW values cannot be negative";
        if (newEcm + newEccm > sensorRating)
            return "ECM + ECCM (" + (newEcm + newEccm) + ") exceeds sensor rating (" + sensorRating + ")";
        ensureSize(sensorRating);

        // Attempt for real; roll back on failure — the feasibility check can
        // then never drift from the actual assignment logic.
        List<Circuit> snapshot = snapshot();
        unpowerDownTo(Mode.ECM, newEcm);
        unpowerDownTo(Mode.ECCM, newEccm);
        String err = power(Mode.ECM, newEcm - getEcm(), impulse, sensorRating, true);
        if (err == null)
            err = power(Mode.ECCM, newEccm - getEccm(), impulse, sensorRating, true);
        if (err != null) {
            restore(snapshot);
            return err;
        }
        return null;
    }

    private List<Circuit> snapshot() {
        List<Circuit> copy = new ArrayList<>(circuits.size());
        for (Circuit c : circuits) {
            Circuit s = new Circuit();
            s.mode = c.mode;
            s.lastSwitchImpulse = c.lastSwitchImpulse;
            s.powered = c.powered;
            s.reserveBought = c.reserveBought;
            copy.add(s);
        }
        return copy;
    }

    private void restore(List<Circuit> snapshot) {
        circuits.clear();
        circuits.addAll(snapshot);
    }

    private void unpowerDownTo(Mode mode, int target) {
        int current = countPowered(mode);
        if (current <= target)
            return;
        int toDrop = current - target;
        // Drop reserve-bought first — they expire at end of turn anyway
        for (Circuit c : circuits) {
            if (toDrop == 0) break;
            if (c.powered && c.mode == mode && c.reserveBought) { c.powered = false; c.reserveBought = false; toDrop--; }
        }
        for (Circuit c : circuits) {
            if (toDrop == 0) break;
            if (c.powered && c.mode == mode) { c.powered = false; toDrop--; }
        }
    }

    /**
     * Power {@code count} circuits in the given mode: matching-mode first
     * (no flip, lock state irrelevant), then never-used, then flip unlocked
     * circuits (recording the flip). Returns error if short; null otherwise.
     */
    private String power(Mode mode, int count, int impulse, int sensorRating, boolean reserve) {
        if (count <= 0)
            return null;
        int remaining = count;
        int limit = usable(sensorRating);
        for (int pass = 0; pass < 3 && remaining > 0; pass++) {
            for (int i = 0; i < limit && remaining > 0; i++) {
                Circuit c = circuits.get(i);
                if (c.powered)
                    continue;
                boolean take = switch (pass) {
                    case 0 -> c.mode == mode;                       // committed to this mode
                    case 1 -> c.mode == null;                       // never used
                    default -> c.mode != mode && canFlip(c, impulse); // flip an unlocked circuit
                };
                if (!take)
                    continue;
                if (pass == 1)
                    c.mode = mode;                                  // first assignment — not a flip
                else if (pass == 2) {
                    c.mode = mode;
                    c.lastSwitchImpulse = impulse;                  // flip — 8-impulse commitment
                }
                c.powered = true;
                c.reserveBought = reserve;
                remaining--;
            }
        }
        if (remaining > 0)
            return remaining + " EW point(s) unassignable — circuits still committed to the "
                    + "other mode (one switch per 8 impulses; D6.316)";
        return null;
    }

    // -------------------------------------------------------------------------
    // Turn lifecycle
    // -------------------------------------------------------------------------

    /**
     * End-of-turn cleanup: all power lapses (allocation is per-turn; reserve
     * purchases expire — D6.312). Mode commitments and flip lockouts persist.
     */
    public void cleanUp() {
        for (Circuit c : circuits) {
            c.powered = false;
            c.reserveBought = false;
        }
    }

    /**
     * Test/sync hook: force exact totals, bypassing lockouts and battery.
     * Assigns modes without recording flips.
     */
    public void force(int ecm, int eccm, int sensorRating) {
        ensureSize(Math.max(sensorRating, ecm + eccm));
        for (Circuit c : circuits) { c.powered = false; c.reserveBought = false; }
        int i = 0;
        for (int n = 0; n < ecm; n++, i++)  { circuits.get(i).mode = Mode.ECM;  circuits.get(i).powered = true; }
        for (int n = 0; n < eccm; n++, i++) { circuits.get(i).mode = Mode.ECCM; circuits.get(i).powered = true; }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean canFlip(Circuit c, int impulse) {
        return impulse - c.lastSwitchImpulse >= SWITCH_LOCKOUT_IMPULSES;
    }

    private int usable(int sensorRating) {
        return Math.min(circuits.size(), sensorRating);
    }

    private void ensureSize(int sensorRating) {
        while (circuits.size() < sensorRating)
            circuits.add(new Circuit());
    }
}
