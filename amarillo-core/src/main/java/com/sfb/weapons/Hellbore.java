package com.sfb.weapons;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;
import com.sfb.utilities.DiceRoller;

/**
 * Hydran Hellbore Cannon (E10.0).
 *
 * Arms over two consecutive turns. Turn 1 always costs 3 energy regardless of
 * mode. On turn 2 the player chooses standard (3 energy) or overload (6 energy,
 * range 0–8, E10.6). Cannot be held — if armed but not fired, armingTurn drops
 * back to 1 at end of turn and the player pays again the following turn
 * (rolling delay E10.22).
 *
 * Default firing mode is enveloping (E10.0): fire() distributes damage across
 * all six shields via Game.applyHellboreEnvelopingDamage(). Direct-fire mode
 * (E10.7): fireDirect() hits the facing shield at half damage, same pattern as
 * PlasmaLauncher.fireBolt().
 */
public class Hellbore extends HitOrMissWeapon implements DirectFire, HeavyWeapon {

    // Range bands: [0] 0–1, [1] 2, [2] 3–4, [3] 5–8, [4] 9–15, [5] 16–22, [6] 23–40
    private static final int[] HIT_NUMBERS    = { 11, 10,  9,  8,  7, 6, 5 };
    private static final int[] ENV_DAMAGE     = { 20, 17, 15, 13, 10, 8, 4 };
    private static final int[] DF_DAMAGE      = { 10,  8,  7,  6,  5, 4, 2 };

    // Overload (E10.6): indexed directly by range 0–8. Same hit numbers; max range 8.
    private static final int[] OVLD_ENV_DAMAGE = { 30, 30, 25, 22, 22, 19, 19, 19, 19 };
    private static final int[] OVLD_DF_DAMAGE  = { 15, 15, 12, 11, 11,  9,  9,  9,  9 };

    private int              armingTurn = 0;
    private boolean          armed      = false;
    private WeaponArmingType armingType = WeaponArmingType.STANDARD;

    public Hellbore() {
        setDacHitLocaiton("drone");
        setType("Hellbore");
        setMinRange(1);   // non-overloaded cannot fire at range 0 (E10.33)
        setMaxRange(40);
    }

    // -------------------------------------------------------------------------
    // Firing
    // -------------------------------------------------------------------------

    /**
     * Fire in enveloping mode (default, E10.0). Returns the enveloping damage value.
     * The caller (Game.fireWeapons) routes this to applyHellboreEnvelopingDamage().
     */
    @Override
    public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
        if (!armed)
            throw new WeaponUnarmedException("Hellbore is not armed.");
        if (range < getMinRange() || range > getMaxRange())
            throw new TargetOutOfRangeException("Target out of Hellbore range.");

        int roll = new DiceRoller().rollTwoDice();
        setLastRoll(roll);
        int damage;
        if (armingType == WeaponArmingType.OVERLOAD) {
            damage = (roll <= HIT_NUMBERS[rangeBand(range)]) ? OVLD_ENV_DAMAGE[range] : 0;
        } else {
            int band = rangeBand(range);
            damage = (roll <= HIT_NUMBERS[band]) ? ENV_DAMAGE[band] : 0;
        }

        reset();
        registerFire();
        return damage;
    }

    /**
     * Fire in direct-fire mode (E10.7) — half damage to the facing shield only.
     * The caller applies the returned damage to the facing shield normally.
     * Same pattern as PlasmaLauncher.fireBolt().
     */
    public int fireDirect(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
        if (!armed)
            throw new WeaponUnarmedException("Hellbore is not armed.");
        if (range < getMinRange() || range > getMaxRange())
            throw new TargetOutOfRangeException("Target out of Hellbore range.");

        int roll = new DiceRoller().rollTwoDice();
        setLastRoll(roll);
        int damage;
        if (armingType == WeaponArmingType.OVERLOAD) {
            damage = (roll <= HIT_NUMBERS[rangeBand(range)]) ? OVLD_DF_DAMAGE[range] : 0;
        } else {
            int band = rangeBand(range);
            damage = (roll <= HIT_NUMBERS[band]) ? DF_DAMAGE[band] : 0;
        }

        reset();
        registerFire();
        return damage;
    }

    // -------------------------------------------------------------------------
    // HeavyWeapon interface
    // -------------------------------------------------------------------------

    /**
     * Advance arming by one turn.
     * Turn 1 (0→1): always 3 energy regardless of mode.
     * Turn 2 (1→2): 3 for STANDARD, 6 for OVERLOAD.
     */
    @Override
    public boolean arm(int energy) {
        if (armingTurn == 0) {
            if (energy != 3) return false;
            armingTurn++;
            return true;
        }
        if (armingTurn == 1) {
            int required = (armingType == WeaponArmingType.OVERLOAD) ? 6 : 3;
            if (energy != required) return false;
            armingTurn++;
            armed = true;
            return true;
        }
        return false;
    }

    /** Hellbores cannot be held (E10.22). Rolling delay is handled in cleanUp(). */
    @Override
    public boolean hold(int energy) throws WeaponUnarmedException {
        return false;
    }

    @Override
    public int energyToArm() {
        // Final turn with overload committed costs 6; everything else is 3.
        return (armingTurn == 1 && armingType == WeaponArmingType.OVERLOAD) ? 6 : 3;
    }

    @Override public int     totalArmingTurns()  { return 2; }
    @Override public int     getArmingTurn()      { return armingTurn; }
    @Override public boolean isArmed()             { return armed; }

    @Override
    public void setArmingTurn(int turn) {
        this.armingTurn = turn;
        if (turn >= 2) armed = true;
    }

    @Override public void setArmed(boolean armed) { this.armed = armed; }

    @Override public WeaponArmingType getArmingType() { return armingType; }

    @Override
    public boolean setStandard() {
        armingType = WeaponArmingType.STANDARD;
        setMinRange(1);
        setMaxRange(40);
        return true;
    }

    @Override
    public boolean setOverload() {
        armingType = WeaponArmingType.OVERLOAD;
        setMinRange(0);
        setMaxRange(8);
        return true;
    }

    @Override public boolean setSpecial()             { return false; }
    @Override public boolean supportsOverload()       { return true;  }
    @Override public boolean overloadFinalTurnOnly()  { return true;  }

    @Override
    public void reset() {
        armingTurn = 0;
        armed      = false;
        armingType = WeaponArmingType.STANDARD;
        setMinRange(1);
        setMaxRange(40);
    }

    @Override
    public void applyAllocationEnergy(Double energy, WeaponArmingType type) {
        if (energy == null || energy <= 0) {
            reset();
            return;
        }
        // Player commits to overload on the final arming turn by sending OVERLOAD type.
        if (type == WeaponArmingType.OVERLOAD) setOverload();
        else                                    setStandard();
        arm(energy.intValue());
    }

    /**
     * Rolling delay (E10.22): if armed but not fired this turn, drop back to
     * armingTurn=1. The existing armingType is preserved — if the player had
     * overloaded they can still choose overload on the next final turn.
     */
    @Override
    public void cleanUp() {
        if (armed && getShotsThisTurn() == 0) {
            armed      = false;
            armingTurn = 1;
        }
        super.cleanUp();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static int rangeBand(int range) {
        if (range <= 1)  return 0;
        if (range <= 2)  return 1;
        if (range <= 4)  return 2;
        if (range <= 8)  return 3;
        if (range <= 15) return 4;
        if (range <= 22) return 5;
        return 6;
    }

    /** Returns the hit-number table (band-indexed, not range-indexed). */
    @Override
    public int[] getHitChart() {
        return HIT_NUMBERS;
    }
}
