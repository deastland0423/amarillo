package com.sfb.objects.shuttles;

import com.sfb.objects.*;

import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.PhaserG;
import com.sfb.weapons.Weapon;

/**
 * Base class for all fighter units (J4.0).
 * Fighters are shuttles with improved combat capabilities: fixed weapons
 * powered
 * by an onboard engine, a crippling threshold, and one free Tactical Maneuver
 * per turn (J4.12 — no energy cost, no breakdown roll).
 */
public abstract class Fighter extends Shuttle {

    /** Damage points needed to cripple this fighter (J1.33). */
    private int crippledHull;
    private int bpv;

    private int ecm = 2;
    private int eccm = 2;

    private boolean tacticalManeuverUsed = false;
    private boolean twoSeater = false; // True if this fighter has two seats (e.g. Kzinti AAS_E EW fighter)

    public Fighter() {
        setChaffPacks(1); // D11.11: most fighters carry one chaff pack
    }

    public int getEcm() {
        return ecm;
    }

    public void setEcm(int ecm) {
        this.ecm = ecm;
    }

    public int getEccm() {
        return eccm;
    }

    public void setEccm(int eccm) {
        this.eccm = eccm;
    }

    public int getCrippledHull() {
        return crippledHull;
    }

    public void setCrippledHull(int crippledHull) {
        this.crippledHull = crippledHull;
    }

    public int getBpv() {
        return bpv;
    }

    public void setBpv(int bpv) {
        this.bpv = bpv;
    }

    /** True if this fighter has taken enough damage to be crippled (J1.33). */
    public boolean shouldCripple() {
        return !isCrippled() && (getHull() - getCurrentHull()) >= crippledHull;
    }

    /**
     * Perform a Tactical Maneuver (J4.12): change facing freely, once per turn.
     * No energy cost. No breakdown roll.
     *
     * @param absoluteFacing New facing (1-24).
     * @return True if the maneuver was performed, false if already used this turn.
     */
    public boolean performTacticalManeuver(int absoluteFacing) {
        if (tacticalManeuverUsed)
            return false;
        performHet(absoluteFacing);
        tacticalManeuverUsed = true;
        return true;
    }

    public boolean isTacticalManeuverUsed() {
        return tacticalManeuverUsed;
    }

    public boolean isTwoSeater() {
        return twoSeater;
    }

    public void setTwoSeater(boolean twoSeater) {
        this.twoSeater = twoSeater;
    }

    /**
     * J1.331 + J1.332: speed halved; all non-phaser weapons cease to operate;
     * FighterFusion charges drained (J1.3324).
     */
    @Override
    public String applyCripplingEffects() {
        String baseLine = super.applyCripplingEffects();
        if (baseLine == null)
            return null; // already crippled

        StringBuilder sb = new StringBuilder(baseLine);
        for (Weapon w : getWeapons().fetchAllWeapons()) {
            if (w instanceof PhaserG) {
                ((PhaserG) w).reduceToPhaserThree(); // J1.3321: Ph-G → Ph-3
            } else if (w instanceof Phaser1 || w instanceof Phaser2 || w instanceof Phaser3) {
                // other phasers remain unchanged (J1.332)
            } else {
                if (w instanceof com.sfb.weapons.FighterFusion)
                    ((com.sfb.weapons.FighterFusion) w).drainCharges(); // J1.3324
                w.damage(); // ceases to operate (J1.332)
            }
        }
        sb.append("; non-phaser weapons offline, Ph-G reduced to Ph-3");
        return sb.toString();
    }

    /**
     * True if this fighter has been on the map long enough to fire direct-fire
     * weapons (8 impulses).
     */
    public boolean canFireDirect(int currentImpulse) {
        return super.canFireDirect(currentImpulse);
    }

    @Override
    public void startTurn() {
        tacticalManeuverUsed = false;
        getWeapons().cleanUp();
    }
}
