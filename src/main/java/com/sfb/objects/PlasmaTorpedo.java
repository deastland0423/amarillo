package com.sfb.objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sfb.properties.PlasmaType;
import com.sfb.properties.TurnMode;
import com.sfb.properties.WeaponArmingType;

/**
 * A plasma torpedo is a seeking weapon that tracks its target like a drone,
 * but deals damage that degrades with distance traveled.
 *
 * Damage at impact = max(0, baseStrength - distanceTraveled).
 * When damage reaches 0 the torpedo has exhausted itself and is removed.
 *
 * Enveloping plasma splits damage evenly across all six shields.
 * Shotgun mode is handled by the launcher creating multiple Plasma-F torpedoes.
 */
public class PlasmaTorpedo extends Unit implements Seeker {

    private PlasmaType plasmaType;
    private WeaponArmingType armingType;

    private Unit target;
    private Unit controller;
    private boolean selfGuiding = false;
    private int launchImpulse;
    private int distanceTraveled = 0; // impulses since launch
    private double damageTaken = 0.0; // accumulated phaser damage (1 phaser pt = 0.5 damage)
    private Seeker.SeekerType seekerType = Seeker.SeekerType.PLASMA;
    private boolean identified = false;

    // Damage tables by range for each plasma type. Index = distance traveled in
    // impulses.
    private static final int[] PLASMA_R_DAMAGE_BY_RANGE = { 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 35, 35, 35, 35,
            35, 25, 25, 25, 25, 25, 20, 20, 20, 20, 25, 10, 10, 10, 5, 1, 0 };
    private static final int[] PLASMA_S_DAMAGE_BY_RANGE = { 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 22, 22, 22, 22,
            22, 15, 15, 15, 15, 15, 10, 10, 10, 5, 1, 0 };
    private static final int[] PLASMA_G_DAMAGE_BY_RANGE = { 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 15, 15, 15, 15,
            15, 10, 10, 10, 5, 1, 0 };
    private static final int[] PLASMA_F_DAMAGE_BY_RANGE = { 20, 20, 20, 20, 20, 20, 15, 15, 15, 15, 15, 10, 10, 5, 5,
            1, 0 };
    private static final int[] PLASMA_D_DAMAGE_BY_RANGE = { 10, 10, 10, 10, 10, 10, 8, 8, 8, 8, 8, 5, 5, 2, 2, 1, 0 };

    private static final int SPEED = 32;

    public PlasmaTorpedo(PlasmaType type, WeaponArmingType armingType) {
        this.plasmaType = type;
        this.armingType = armingType;
        this.selfGuiding = true;
        setTurnMode(TurnMode.Seeker);
        setSpeed(SPEED);
    }

    /**
     * Current damage strength.
     * Envelopin table value doubled, then phaser damage subtracted.
     * Standard: table value, then phaser damage subtracted.
     * Returns 0 if the torpedo has traveled beyond its table or been shot down.
     */
    public int getCurrentStrength() {
        int[] table = getDamageTable();

        if (distanceTraveled >= table.length)
            return 0;
        int base = isEnveloping() ? table[distanceTraveled] * 2 : table[distanceTraveled];
        return Math.max(0, base - (int) damageTaken);
    }

    /**
     * Compute per-shield damage distribution for an enveloping impact.
     * Returns an int[6] where index 0 = shield 1 ... index 5 = shield 6.
     * Base damage is divided evenly; remainder points distributed randomly,
     * at most 1 extra per shield.
     */
    public int[] computeEnvelopingDamage() {
        int total = getCurrentStrength();
        int base = total / 6;
        int remainder = total % 6;

        int[] damage = new int[6];

        for (int i = 0; i < 6; i++)
            damage[i] = base;

        // Pick 'remainder' distinct shield indices at random for the +1 bonus
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            indices.add(i);
        Collections.shuffle(indices);

        for (int i = 0; i < remainder; i++)
            damage[indices.get(i)]++;

        return damage;
    }

    /**
     * Apply phaser fire to this torpedo. Every 2 points of phaser damage
     * reduces torpedo strength by 1.
     */
    public void applyPhaserDamage(int phaserPoints) {
        damageTaken += phaserPoints * 0.5;
    }

    public double getDamageTaken() {
        return damageTaken;
    }

    private int[] getDamageTable() {
        switch (plasmaType) {
            case R:
                return PLASMA_R_DAMAGE_BY_RANGE;
            case S:
                return PLASMA_S_DAMAGE_BY_RANGE;
            case G:
                return PLASMA_G_DAMAGE_BY_RANGE;
            case D:
                return PLASMA_D_DAMAGE_BY_RANGE;
            case F:

            default:
                return PLASMA_F_DAMAGE_BY_RANGE;
        }
    }

    public void incrementDistance() {
        distanceTraveled++;
    }

    public int getDistanceTraveled() {
        return distanceTraveled;
    }

    public PlasmaType getPlasmaType() {
        return plasmaType;
    }

    public WeaponArmingType getArmingType() {
        return armingType;
    }

    public boolean isEnveloping() {
        return armingType == WeaponArmingType.OVERLOAD;
    }

    // --- Seeker interface ---

    @Override
    public void setTarget(Unit target) {
        this.target = target;
    }

    @Override
    public Unit getTarget() {
        return target;
    }

    @Override
    public Seeker.SeekerType getSeekerType() {
        return seekerType;
    }

    @Override
    public void setSeekerType(SeekerType type) {
        this.seekerType = type;
    }

    @Override
    public boolean isSelfGuiding() {
        return selfGuiding;
    }

    @Override
    public void setSelfGuiding(boolean sg) {
        this.selfGuiding = sg;
    }

    @Override
    public int getEndurance() {
        return getCurrentStrength();
    }

    @Override
    public void setEndurance(int e) {
        /* not used — derived from distance */ }

    @Override
    public int getLaunchImpulse() {
        return launchImpulse;
    }

    @Override
    public void setLaunchImpulse(int i) {
        this.launchImpulse = i;
    }

    @Override
    public int getWarheadDamage() {
        return getCurrentStrength();
    }

    @Override
    public void setWarheadDamage(int d) {
        /* damage is table-driven — not settable directly */
    }

    @Override
    public void setController(Unit u) {
        this.controller = u;
    }

    @Override
    public Unit getController() {
        return controller;
    }

    @Override
    public void identify() {
        this.identified = true;
    }

    @Override
    public boolean isIdentified() {
        return identified;
    }

    /**
     * Impact the target. Returns current strength as damage.
     * Caller is responsible for applying enveloping spread if isEnveloping().
     */
    @Override
    public int impact() {
        return getCurrentStrength();
    }
}
