package com.sfb.weapons;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;

/**
 * Fighter-mounted Hellbore (R9.F4 / J4.834).
 * Single-shot: fires once, then the fighter must return to its carrier bay to reload.
 * Always armed — no ship energy arming cycle. Range limited to 10 hexes.
 * Enveloping and direct-fire modes both available (same as ship Hellbore).
 */
public class FighterHellbore extends Hellbore {

    private boolean spent = false;

    public FighterHellbore() {
        setType("FighterHellbore");
        setMinRange(1);
        setMaxRange(10);
        setArmed(true);
        setArmingTurn(2);
    }

    /** True if this hellbore has not been fired since last reload. */
    public boolean isSpent() { return spent; }

    /** Called when the fighter docks at its carrier bay. */
    public void reload() {
        spent = false;
        setArmed(true);
        setArmingTurn(2);
        setMinRange(1);
        setMaxRange(10);
    }

    @Override
    public boolean isArmed() { return !spent; }

    @Override
    public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
        if (spent)
            throw new WeaponUnarmedException("FighterHellbore is spent — return to carrier to reload.");
        setArmed(true); // guard against rolling-delay reset clearing the field
        int damage = super.fire(range);
        spent = true;
        return damage;
    }

    @Override
    public int fireDirect(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
        if (spent)
            throw new WeaponUnarmedException("FighterHellbore is spent — return to carrier to reload.");
        setArmed(true);
        int damage = super.fireDirect(range);
        spent = true;
        return damage;
    }

    // No ship energy arming cycle
    @Override public boolean arm(int energy) { return false; }
    @Override public void applyAllocationEnergy(Double energy, WeaponArmingType type) { /* no-op */ }

    // Preserve maxRange=10 after any reset()
    @Override
    public void reset() {
        setArmed(true);
        setArmingTurn(2);
        setMinRange(1);
        setMaxRange(10);
    }
}
