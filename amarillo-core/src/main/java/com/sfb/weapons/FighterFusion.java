package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.utilities.DiceRoller;

/**
 * Fusion beam as carried by Hydran fighters (J4.83).
 * Each instance has its own charge pool (2 charges). Charges are not shared
 * between weapons. Single shot (1 charge) reaches range 0-3; double shot
 * (2 charges) reaches range 0-10 at the same damage. The 1/4-turn cooldown
 * (8-impulse gap) is enforced by the base Weapon class.
 */
public class FighterFusion extends VariableDamageWeapon implements DirectFire {

    public enum ShotMode {
        SINGLE, DOUBLE
    }


    // Range bands: [0] 0, [1] 1, [2] 2, [3] 3-10
    private static final int[][] bandHitChart = {
            { 13, 8, 6, 4 }, // Roll 1
            { 11, 8, 5, 3 }, // Roll 2
            { 10, 7, 4, 2 }, // Roll 3
            { 9, 6, 3, 1 }, // Roll 4
            { 8, 5, 3, 1 }, // Roll 5
            { 8, 4, 2, 0 }, // Roll 6
    };

    static int rangeBand(int range) {
        if (range <= 0) return 0;
        if (range <= 1) return 1;
        if (range <= 2) return 2;
        return 3; // 3-10
    }

    private static final int SINGLE_MAX_RANGE = 3;
    private static final int DOUBLE_MAX_RANGE = 10;

    private int chargesRemaining = 2;
    private ShotMode pendingShotMode = ShotMode.SINGLE;

    public FighterFusion() {
        setDacHitLocaiton("torp");
        setType("FighterFusion");
        setMinRange(0);
        setMaxRange(DOUBLE_MAX_RANGE);
        setMinImpulseGap(8);
    }

    public int getChargesRemaining() {
        return chargesRemaining;
    }

    public ShotMode getPendingShotMode() {
        return pendingShotMode;
    }

    /**
     * Set before calling fire() to choose single (1 charge, range 0-3) or double (2
     * charges, range 0-10).
     */
    public void setShotMode(ShotMode mode) {
        this.pendingShotMode = mode;
    }

    /** J1.3324: discharge all remaining charges when the fighter is crippled. */
    public void drainCharges() {
        chargesRemaining = 0;
    }

    @Override
    public boolean canFire() {
        return chargesRemaining > 0 && super.canFire();
    }

    public boolean canFireDouble() {
        return chargesRemaining >= 2 && super.canFire();
    }

    @Override
    public int fire(int range)
            throws WeaponUnarmedException, TargetOutOfRangeException, CapacitorException {

        int chargesToUse = (pendingShotMode == ShotMode.DOUBLE) ? 2 : 1;
        int effectiveMax = (pendingShotMode == ShotMode.DOUBLE) ? DOUBLE_MAX_RANGE : SINGLE_MAX_RANGE;

        if (chargesRemaining < chargesToUse) {
            throw new WeaponUnarmedException(
                    pendingShotMode == ShotMode.DOUBLE
                            ? "Not enough charges for double shot."
                            : "FighterFusion has no charges remaining.");
        }

        if (!super.canFire()) {
            throw new WeaponUnarmedException("FighterFusion is on cooldown.");
        }

        if (range > effectiveMax) {
            throw new TargetOutOfRangeException(
                    "Target at range " + range + " is beyond " +
                            (pendingShotMode == ShotMode.DOUBLE ? "double" : "single") +
                            " shot max range " + effectiveMax + ".");
        }

        DiceRoller roller = new DiceRoller();
        int roll = roller.rollOneDie();
        setLastRoll(roll);
        int damage = lookupWithShift(bandHitChart, roll, rangeBand(range));

        chargesRemaining -= chargesToUse;
        registerFire();
        return damage;
    }
}
