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
 * <p>Slice 1: no capacitor (G23.24), immediate activation (the 4-impulse
 * announcement of G23.31 is deferred), and simple damage-splitting.
 */
public class ESG extends Weapon {

    /** G23.42 field-strength chart: STRENGTH[radius][energy], energy 1..5 (index 0 = none). */
    private static final int[][] STRENGTH = {
        { 0, 4, 8, 12, 16, 20 }, // radius 0
        { 0, 4, 7, 11, 15, 18 }, // radius 1
        { 0, 3, 7, 10, 13, 17 }, // radius 2
        { 0, 3, 6,  9, 12, 15 }, // radius 3
    };

    public static final int MAX_ENERGY     = 5;  // most an ESG can hold (G23.22)
    public static final int MAX_RADIUS     = 3;
    public static final int FIELD_DURATION = 32; // impulses a field stays active (G23.32)

    private final boolean hasCapacitor;   // slice 1: false ("ESG without capacitor")
    private int storedEnergy = 0;         // energy accumulated in the generator, 0..5 (G23.211/.22)

    // Active-field state
    private boolean active = false;
    private int radius = 0;
    private int strength = 0;
    private int activatedImpulse = -1;

    public ESG(boolean hasCapacitor) {
        this.hasCapacitor = hasCapacitor;
        setType("ESG");
        setDacHitLocaiton("esg"); // destroyed on 'drone' hits (G23.14) — DAC wiring deferred
    }

    public ESG() {
        this(false);
    }

    /** Field strength for a radius + stored-energy amount (G23.42). */
    public static int strengthFor(int radius, int energy) {
        if (radius < 0 || radius > MAX_RADIUS || energy < 1 || energy > MAX_ENERGY) {
            return 0;
        }
        return STRENGTH[radius][energy];
    }

    /** Add allocated energy to the generator, capped at {@link #MAX_ENERGY}. */
    public void addEnergy(int points) {
        storedEnergy = Math.max(0, Math.min(MAX_ENERGY, storedEnergy + points));
    }

    public int getStoredEnergy() {
        return storedEnergy;
    }

    public void setStoredEnergy(int energy) {
        storedEnergy = Math.max(0, Math.min(MAX_ENERGY, energy));
    }

    /**
     * Form the field at {@code radius} (G23.3). Strength comes off the chart for
     * the currently-stored energy, and all of that energy is released (G23.222).
     * A field only forms if it would have positive strength.
     */
    public void activate(int radius, int currentImpulse) {
        this.radius = Math.max(0, Math.min(MAX_RADIUS, radius));
        this.strength = strengthFor(this.radius, storedEnergy);
        this.active = strength > 0;
        this.activatedImpulse = currentImpulse;
        this.storedEnergy = 0;
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
