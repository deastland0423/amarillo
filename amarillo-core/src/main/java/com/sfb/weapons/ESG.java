package com.sfb.weapons;

/**
 * Expanding Sphere Generator (G23.0) — a Lyran (and Orion option-mount) weapon.
 *
 * <p>Despite the name it does not expand: the field FORMS at a chosen radius
 * (G23.44) and is HOLLOW (G23.54) — only the ring of hexes at that radius is
 * dangerous. The field moves with the ship (G23.45) and damages any unit that
 * enters a ring hex (G23.51). This class holds the generator's stored energy and
 * the active field's state; {@code EsgResolver} does the per-impulse ring
 * processing.
 *
 * <p>Slice 2 adds the G23.31 announcement window: releasing a field must be
 * announced 4 impulses (1/8 turn) ahead — the radius/strength stay the owner's
 * secret until the field forms (G23.311) — plus voluntary deactivation
 * (G23.47), the 8-impulse cancellation lockout (G23.33), and the 32-impulse /
 * second-subsequent-turn reactivation lockout (G23.323). No capacitor yet
 * (G23.24, Slice 3).
 */
public class ESG extends Weapon {

    /** G23.42 field-strength chart: STRENGTH[radius][energy], energy 1..5 (index 0 = none). */
    private static final int[][] STRENGTH = {
        { 0, 4, 8, 12, 16, 20 }, // radius 0
        { 0, 4, 7, 11, 15, 18 }, // radius 1
        { 0, 3, 7, 10, 13, 17 }, // radius 2
        { 0, 3, 6,  9, 12, 15 }, // radius 3
    };

    public static final int MAX_ENERGY     = 5;  // most a single field can use (chart tops out at 5, G23.42)
    public static final int MAX_CAPACITOR  = 7;  // most a capacitor can hold (G23.242)
    public static final int MAX_RADIUS     = 3;
    public static final int FIELD_DURATION = 32; // impulses a field stays active (G23.32)

    /** Advance notice required to release a field (G23.31): 4 impulses = 1/8 turn. */
    public static final int ANNOUNCE_DELAY       = 4;
    /** Re-announce lockout after publicly cancelling an announcement (G23.33). */
    public static final int CANCEL_LOCKOUT       = 8;
    /** Reactivation lockout after a field is dropped (G23.323). */
    public static final int REACTIVATION_LOCKOUT = 32;

    private boolean hasCapacitor;         // G23.24: capacitor holds ≤7 and releases 1–5, keeping the rest
    private int storedEnergy = 0;         // energy held (0..maxStorage(), G23.211/.22/.242)

    // Active-field state
    private boolean active = false;
    private int radius = 0;
    private int strength = 0;
    private int activatedImpulse = -1;

    // Announcement state (G23.31) — an intention to release, pending its 4-impulse notice.
    private boolean announced = false;
    private int announcedRadius = 0;         // recorded at announcement, secret until release (G23.311)
    private int announcedReleaseAmount = MAX_ENERGY; // energy to release (capacitor only; else all, G23.242)
    private int releaseImpulse = -1;         // absolute impulse the field forms (announce + ANNOUNCE_DELAY)

    // Lockouts (absolute impulses; 0 = none pending)
    private int announceAllowedImpulse = 0;     // earliest re-announce after a cancellation (G23.33)
    private int reactivationAllowedImpulse = 0;  // earliest a field may re-form after a drop (G23.323)

    public ESG(boolean hasCapacitor) {
        this.hasCapacitor = hasCapacitor;
        setType("ESG");
        setDacHitLocaiton("esg"); // destroyed on 'drone' hits (G23.14) — DAC wiring deferred
    }

    public ESG() {
        this(false);
    }

    /** Set whether this ESG has a capacitor (G23.24) — resolved from ship year/spec at build time. */
    public void setHasCapacitor(boolean hasCapacitor) {
        this.hasCapacitor = hasCapacitor;
    }

    /** Most energy the generator can hold: 7 with a capacitor, else 5 (G23.242/.22). */
    public int maxStorage() {
        return hasCapacitor ? MAX_CAPACITOR : MAX_ENERGY;
    }

    /** Field strength for a radius + stored-energy amount (G23.42). */
    public static int strengthFor(int radius, int energy) {
        if (radius < 0 || radius > MAX_RADIUS || energy < 1 || energy > MAX_ENERGY) {
            return 0;
        }
        return STRENGTH[radius][energy];
    }

    /**
     * Add allocated energy to the generator, capped at {@link #maxStorage()}. Energy
     * cannot be stored in an active field unless it has a capacitor (G23.243) — an
     * active no-capacitor generator ignores it.
     */
    public void addEnergy(int points) {
        if (active && !hasCapacitor) {
            return;
        }
        storedEnergy = Math.max(0, Math.min(maxStorage(), storedEnergy + points));
    }

    public int getStoredEnergy() {
        return storedEnergy;
    }

    public void setStoredEnergy(int energy) {
        storedEnergy = Math.max(0, Math.min(maxStorage(), energy));
    }

    /**
     * Form the field at {@code radius} (G23.3), releasing up to {@code amount} points
     * (never more than 5 — the chart's max, G23.42). A capacitor keeps the unspent
     * remainder (G23.242); a plain generator loses it ("all power must be used",
     * G23.223). A field only forms if it would have positive strength.
     */
    private void formField(int radius, int amount, int currentImpulse) {
        int used = Math.max(0, Math.min(Math.min(amount, storedEnergy), MAX_ENERGY));
        this.radius = Math.max(0, Math.min(MAX_RADIUS, radius));
        this.strength = strengthFor(this.radius, used);
        this.active = strength > 0;
        this.activatedImpulse = currentImpulse;
        this.storedEnergy = hasCapacitor ? Math.max(0, storedEnergy - used) : 0;
    }

    /**
     * Form the field releasing <em>all</em> stored energy (≤5 effective). Used by the
     * immediate/no-capacitor path and tests; the announcement path uses {@link #release}.
     */
    public void activate(int radius, int currentImpulse) {
        formField(radius, MAX_ENERGY, currentImpulse);
    }

    // --- Announcement window (G23.31) ---

    /** A release is announced but the field has not yet formed. */
    public boolean isAnnounced() {
        return announced && isFunctional();
    }

    /** The chosen radius while announced (secret to opponents until release, G23.311). */
    public int getAnnouncedRadius() {
        return announcedRadius;
    }

    public int getReleaseImpulse() {
        return releaseImpulse;
    }

    /** Impulses remaining until an announced field forms (G23.31); 0 if not announced. */
    public int announceCountdown(int currentImpulse) {
        return announced ? Math.max(0, releaseImpulse - currentImpulse) : 0;
    }

    /**
     * Earliest impulse at which a new release may be <em>announced</em>. Honors the
     * G23.33 cancellation lockout and back-dates the G23.323 reactivation lockout by
     * the 4-impulse notice (the delay is to activation, not announcement).
     */
    public int earliestAnnounceImpulse() {
        return Math.max(announceAllowedImpulse, reactivationAllowedImpulse - ANNOUNCE_DELAY);
    }

    /** True if an announcement may be made this impulse (energy present, no field/announcement, lockouts clear). */
    public boolean canAnnounce(int currentImpulse) {
        return isFunctional() && !active && !announced
                && storedEnergy >= 1
                && currentImpulse >= earliestAnnounceImpulse();
    }

    /**
     * Record an intention to release a field at {@code radius}, forming 4 impulses
     * later (G23.31). The radius is recorded but stays secret until release (G23.311);
     * energy is <em>not</em> released until the field forms (G23.222/.46). A capacitor
     * releases {@code amount} (1–5) and keeps the rest; a plain generator dumps all its
     * stored energy regardless (G23.242).
     */
    public void announce(int radius, int amount, int currentImpulse) {
        this.announcedRadius        = Math.max(0, Math.min(MAX_RADIUS, radius));
        this.announcedReleaseAmount = hasCapacitor ? Math.max(1, Math.min(MAX_ENERGY, amount)) : MAX_ENERGY;
        this.releaseImpulse         = currentImpulse + ANNOUNCE_DELAY;
        this.announced              = true;
    }

    /** Announce a full-energy release (no-capacitor / immediate path). */
    public void announce(int radius, int currentImpulse) {
        announce(radius, MAX_ENERGY, currentImpulse);
    }

    /** The energy amount the pending announcement will release (capacitor only; else all). */
    public int getAnnouncedReleaseAmount() {
        return announcedReleaseAmount;
    }

    /** Most energy a single release can use right now: min(stored, 5) (G23.242/.42). */
    public int maxReleasable() {
        return Math.min(storedEnergy, MAX_ENERGY);
    }

    /** True once the announced release impulse has arrived (G23.31). */
    public boolean readyToRelease(int currentImpulse) {
        return announced && currentImpulse >= releaseImpulse;
    }

    /**
     * Form the previously-announced field (G23.44/.46). Strength comes off the chart
     * for the stored energy, all of which is released (G23.222). If no energy was
     * available the field never forms and counts as dropped this impulse (G23.3121).
     */
    public void release(int currentImpulse) {
        formField(announcedRadius, announcedReleaseAmount, currentImpulse);
        this.announced      = false;
        this.releaseImpulse = -1;
        if (!active) {
            recordDrop(currentImpulse); // G23.3121: no-field activation still counts as a drop
        }
    }

    /**
     * Publicly cancel a pending announcement before the field forms (G23.33). Energy
     * is retained; a fresh announcement is locked out for 8 impulses.
     */
    public void cancelAnnouncement(int currentImpulse) {
        this.announced              = false;
        this.releaseImpulse         = -1;
        this.announceAllowedImpulse = currentImpulse + CANCEL_LOCKOUT;
    }

    /**
     * Record that an active field has dropped, arming the reactivation lockout (G23.323):
     * no new field before 32 impulses have passed <em>and</em> not before the start of
     * the second subsequent turn from the point of activation.
     */
    public void recordDrop(int currentImpulse) {
        int activatedTurn = (activatedImpulse - 1) / FIELD_DURATION + 1;      // 1-based turn of activation
        int secondSubsequentTurnStart = (activatedTurn + 1) * FIELD_DURATION + 1;
        this.reactivationAllowedImpulse = Math.max(currentImpulse + REACTIVATION_LOCKOUT,
                                                   secondSubsequentTurnStart);
    }

    /** Score damage against the field, reducing its strength; deactivate at 0 (G23.511). */
    public void absorbDamage(int points) {
        strength -= points;
        if (strength <= 0) {
            deactivate();
        }
    }

    public void deactivate() {
        active = false;
        strength = 0;
    }

    /** True if a field is up and the generator is undamaged. */
    public boolean isActive() {
        return active && isFunctional();
    }

    /** True once the field has run its 32-impulse life (G23.32). */
    public boolean isExpired(int currentImpulse) {
        return active && currentImpulse - activatedImpulse >= FIELD_DURATION;
    }

    public int getRadius()        { return radius; }
    public int getStrength()      { return strength; }
    public boolean hasCapacitor() { return hasCapacitor; }
    public int getActivatedImpulse() { return activatedImpulse; }
}
