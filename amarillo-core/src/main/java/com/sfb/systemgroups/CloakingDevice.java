package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.Unit;

/**
 * Cloaking device state machine.
 *
 * States and transitions:
 *   INACTIVE → FADING_OUT  : activate() called (cost paid, not yet cloaked this turn)
 *   FADING_OUT → FULLY_CLOAKED : 5 impulses after activation impulse
 *   FULLY_CLOAKED → FADING_IN  : deactivate() called (not yet uncloaked this turn)
 *   FADING_IN → INACTIVE       : 5 impulses after deactivation impulse
 *
 * Fading bonus (added to effective range):
 *   FADING_OUT impulse k (1–5 after activation): +k
 *   FULLY_CLOAKED: lock-on broken (already doubles range) + +5
 *   FADING_IN impulse k (1–5 after deactivation): +(6 - k)  →  +5 down to +1
 *
 * Action restrictions: any non-INACTIVE state blocks weapons, seekers,
 * tractors, and transporters.
 *
 * Energy: costPaidThisTurn must be set true each turn during energy allocation
 * or the device begins involuntary fade-in on impulse 1.
 */
public class CloakingDevice implements Systems {

    public enum CloakState {
        INACTIVE,
        FADING_OUT,
        FULLY_CLOAKED,
        FADING_IN
    }

    private static final int FADE_IMPULSES = 5;

    private int powerToActivate = 0;
    private CloakState state = CloakState.INACTIVE;
    private boolean damaged = false;

    /** Absolute impulse when the current fade started (activation or deactivation). */
    private int transitionImpulse = -1;

    /** Per-turn flags — reset by newTurn(). */
    private boolean cloakedThisTurn   = false;
    private boolean uncloakedThisTurn = false;
    private boolean costPaidThisTurn  = false;

    private Unit owningUnit = null;

    public CloakingDevice() {}

    public CloakingDevice(int powerToActivate) {
        this.powerToActivate = powerToActivate;
    }

    public CloakingDevice(Unit owner, int powerToActivate) {
        this.owningUnit = owner;
        this.powerToActivate = powerToActivate;
    }

    // -------------------------------------------------------------------------
    // Systems interface
    // -------------------------------------------------------------------------

    @Override
    public void init(Map<String, Object> values) {
        if (values.containsKey("cloakcost"))
            powerToActivate = (int) values.get("cloakcost");
    }

    @Override
    public int fetchOriginalTotalBoxes() { return 1; }

    @Override
    public int fetchRemainingTotalBoxes() { return damaged ? 0 : 1; }

    public boolean isFunctional() { return !damaged; }

    /**
     * Damage the cloaking device (e.g. via Hit & Run raid).
     * If the device is currently active, forces an immediate fade-in.
     *
     * @param currentImpulse The current absolute impulse.
     */
    public void damage(int currentImpulse) {
        if (damaged) return;
        damaged = true;
        // If active in any way, begin emergency fade-in
        if (state != CloakState.INACTIVE && state != CloakState.FADING_IN) {
            state = CloakState.FADING_IN;
            transitionImpulse = currentImpulse;
        }
    }

    @Override
    public void cleanUp() {}

    @Override
    public Unit fetchOwningUnit() { return owningUnit; }

    // -------------------------------------------------------------------------
    // Turn lifecycle
    // -------------------------------------------------------------------------

    /**
     * Call at the start of each new turn (after energy allocation).
     * Resets per-turn flags. If cost was not paid and device is active,
     * begins involuntary fade-in on impulse 1.
     *
     * @param impulse1 The absolute impulse number for impulse 1 of this turn.
     */
    public void newTurn(int impulse1) {
        if (!costPaidThisTurn && state != CloakState.INACTIVE) {
            // Involuntary decloak — begin fade-in immediately
            state = CloakState.FADING_IN;
            transitionImpulse = impulse1;
        }
        cloakedThisTurn   = false;
        uncloakedThisTurn = false;
        // costPaidThisTurn is NOT reset here — it is set explicitly by setCostPaid()
        // during energy allocation each turn and must remain true through the turn
        // so that activate() can verify the cost was paid.
    }

    /**
     * Called each impulse to advance FADING states to their terminal state
     * once the 5-impulse fade period is complete.
     *
     * @param currentImpulse The current absolute impulse number.
     */
    public void updateState(int currentImpulse) {
        if (state == CloakState.FADING_OUT) {
            if (impulsesElapsed(currentImpulse) > FADE_IMPULSES) {
                state = CloakState.FULLY_CLOAKED;
            }
        } else if (state == CloakState.FADING_IN) {
            if (impulsesElapsed(currentImpulse) > FADE_IMPULSES) {
                state = CloakState.INACTIVE;
                transitionImpulse = -1;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Activation / deactivation
    // -------------------------------------------------------------------------

    /**
     * Attempt to begin cloaking. Fails if:
     * - cost not paid this turn
     * - already cloaked or fading out
     * - already cloaked this turn
     *
     * @param currentImpulse Current absolute impulse.
     * @return true if the device began fading out.
     */
    public boolean activate(int currentImpulse) {
        if (!costPaidThisTurn) return false;
        if (cloakedThisTurn)   return false;
        if (state == CloakState.FADING_OUT || state == CloakState.FULLY_CLOAKED) return false;

        state = CloakState.FADING_OUT;
        transitionImpulse = currentImpulse;
        cloakedThisTurn = true;
        return true;
    }

    /**
     * Attempt to begin decloaking. Fails if:
     * - already inactive or fading in
     * - already uncloaked this turn
     *
     * @param currentImpulse Current absolute impulse.
     * @return true if the device began fading in.
     */
    public boolean deactivate(int currentImpulse) {
        if (uncloakedThisTurn) return false;
        if (state == CloakState.INACTIVE || state == CloakState.FADING_IN) return false;

        state = CloakState.FADING_IN;
        transitionImpulse = currentImpulse;
        uncloakedThisTurn = true;
        return true;
    }

    // -------------------------------------------------------------------------
    // Range / lock-on effects
    // -------------------------------------------------------------------------

    /**
     * The additive bonus to effective range from this device.
     * FULLY_CLOAKED also breaks lock-on (handled separately), so this
     * returns +5 to stack on top of the doubled range.
     *
     * @param currentImpulse Current absolute impulse.
     */
    public int getCloakBonus(int currentImpulse) {
        switch (state) {
            case FADING_OUT: {
                int k = Math.min(impulsesElapsed(currentImpulse), FADE_IMPULSES);
                return k;
            }
            case FULLY_CLOAKED:
                return FADE_IMPULSES; // +5; lock-on break doubles range separately
            case FADING_IN: {
                int k = Math.min(impulsesElapsed(currentImpulse), FADE_IMPULSES);
                return FADE_IMPULSES + 1 - k; // +5 down to +1
            }
            default:
                return 0;
        }
    }

    /**
     * Returns true when fully cloaked — lock-on is impossible and
     * existing lock-ons to this ship are broken.
     */
    public boolean breaksLockOn() {
        return state == CloakState.FULLY_CLOAKED;
    }

    // -------------------------------------------------------------------------
    // Action restrictions
    // -------------------------------------------------------------------------

    /**
     * Returns true in any non-INACTIVE state.
     * Blocks: weapons, seekers, tractors, transporters.
     */
    public boolean isRestrictingActions() {
        return state != CloakState.INACTIVE;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public CloakState getState() { return state; }

    /** Directly set cloak state — used to sync client-side state from server. */
    public void setState(CloakState state) { this.state = state; }

    /** Directly set the transition impulse — used to sync client-side state from server. */
    public void setTransitionImpulse(int impulse) { this.transitionImpulse = impulse; }

    public int getTransitionImpulse() { return transitionImpulse; }

    public int getPowerToActivate() { return powerToActivate; }

    public boolean isCostPaidThisTurn() { return costPaidThisTurn; }

    public void setCostPaid(boolean paid) { this.costPaidThisTurn = paid; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int impulsesElapsed(int currentImpulse) {
        if (transitionImpulse < 0) return 0;
        return currentImpulse - transitionImpulse + 1; // activation impulse = step 1
    }

    /**
     * Returns the current fade step (1–5) for FADING_OUT or FADING_IN states.
     * Returns 0 in all other states.
     *
     * @param currentImpulse The current absolute impulse.
     */
    public int getFadeStep(int currentImpulse) {
        if (state != CloakState.FADING_OUT && state != CloakState.FADING_IN) return 0;
        return Math.max(1, Math.min(FADE_IMPULSES, impulsesElapsed(currentImpulse)));
    }
}
