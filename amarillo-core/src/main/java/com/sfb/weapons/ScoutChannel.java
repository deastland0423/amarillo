package com.sfb.weapons;

/**
 * A scout function channel / special sensor (G24.0) — a "Commander's Level" system, not
 * a firing weapon. A channel performs one scout function per turn (EW lending, breaking
 * lock-ons, etc. — later slices). Slice 1 models it purely as a resource:
 *
 * <ul>
 *   <li>It occupies the DAC hit location of the weapon it <em>replaced</em> (G24.17): a
 *       channel that replaced a disruptor dies on a "torp" hit, one that replaced a
 *       phaser on a "phaser" hit. That comes for free from {@link Weapon}'s hit location.</li>
 *   <li>It must be <em>powered</em> (1 energy, at Energy Allocation) to operate (G24.14).</li>
 *   <li>Firing a blinding weapon <em>blinds</em> it for 32 impulses (G24.13).</li>
 * </ul>
 */
public class ScoutChannel extends Weapon {

    /** Impulses a firing blinds a channel (G24.13). */
    public static final int BLIND_DURATION = 32;

    /** Most EW (ECM+ECCM combined) one channel can lend to a unit (G24.2112). */
    public static final int MAX_LEND = 6;

    private boolean powered = false;
    private int blindedUntilImpulse = -1; // absolute impulse the blinding lifts; <= now = clear

    // EW lending (G24.21): this channel carries EW the scout generated to one recipient.
    private String lendTarget; // recipient ship name, or null if not lending
    private int lentEcm;        // ECM points carried this turn
    private int lentEccm;       // ECCM points carried this turn

    public ScoutChannel() {
        setType("ScoutChannel");
        // dacHitLocation is assigned from the ship JSON — the location of the replaced weapon (G24.17).
    }

    /** A channel never fires (G24.0). */
    @Override
    public boolean canFire() {
        return false;
    }

    /** A channel firing can't blind another channel — it doesn't fire. */
    @Override
    public boolean blindsScoutChannels() {
        return false;
    }

    // --- Power (G24.14) ---

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    // --- Blinding (G24.13) ---

    /**
     * Blind this channel for 32 impulses (G24.13). If it is already blinded, the blinding
     * is extended by 32 from its recovery point (G24.131 surplus-firing handling).
     */
    public void blind(int currentImpulse) {
        blindedUntilImpulse = Math.max(currentImpulse, blindedUntilImpulse) + BLIND_DURATION;
    }

    public boolean isBlinded(int currentImpulse) {
        return currentImpulse < blindedUntilImpulse;
    }

    public int getBlindedUntilImpulse() {
        return blindedUntilImpulse;
    }

    /** True if the channel can perform a function this impulse: undamaged, powered, unblinded. */
    public boolean isOperational(int currentImpulse) {
        return isFunctional() && powered && !isBlinded(currentImpulse);
    }

    // --- EW lending (G24.21) ---

    /**
     * Assign this channel to lend {@code ecm}/{@code eccm} EW to {@code target} (G24.21).
     * A channel carries at most {@link #MAX_LEND} EW total (G24.2112).
     */
    public void setLend(String target, int ecm, int eccm) {
        this.lendTarget = target;
        this.lentEcm  = Math.max(0, ecm);
        this.lentEccm = Math.max(0, eccm);
    }

    public void clearLend() {
        lendTarget = null;
        lentEcm = 0;
        lentEccm = 0;
    }

    public String getLendTarget() { return lendTarget; }
    public int getLentEcm()       { return lentEcm; }
    public int getLentEccm()      { return lentEccm; }
}
