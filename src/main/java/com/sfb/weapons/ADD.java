package com.sfb.weapons;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.utilities.DiceRoller;

/**
 * Anti-Drone Defense (ADD) weapon.
 *
 * Fires at any range 1-3; cannot fire at range 0 or 4+.
 * On a hit: destroys drones outright, does 1d6 hull to shuttles/fighters,
 * has no effect on ships.
 *
 * fire() returns ADD.HIT on a hit, 0 on a miss. Callers (applyDamageToUnit)
 * use ADD.HIT to distinguish "hit but no ship damage" from a normal damage
 * value.
 *
 * Auto-reloads at end of any turn where it did not fire, drawing from reserve
 * shots.
 * Reserve shots = numberOfReloads * capacity (each reload = one full rack
 * replacement).
 */
public class ADD extends HitOrMissWeapon implements DirectFire {

    /**
     * Sentinel returned by fire() on a hit — signals "ADD hit" to
     * applyDamageToUnit.
     */
    public static final int HIT = Integer.MAX_VALUE;

    // Range 0 = can't fire, range 1 = hit on 1-2, range 2 = 1-3, range 3 = 1-4,
    // range 4+ = can't fire
    private static final int[] HIT_CHART = { 0, 2, 3, 4, 0 };

    public enum AddType {
        ADD_6, ADD_12
    }

    private final int capacity; // shots per full load (6 or 12)
    private int shots; // shots remaining in current load
    private int reloadsAvailable; // individual shots remaining in reserve

    public ADD(AddType type, int numberOfReloads) {
        setDacHitLocaiton("drone");
        setType("ADD");
        setMinImpulseGap(1);
        setMaxShotsPerTurn(Integer.MAX_VALUE);
        setMaxRange(3);

        switch (type) {
            case ADD_12:
                this.capacity = 12;
                break;
            case ADD_6:
            default:
                this.capacity = 6;
                break;
        }
        this.shots = capacity;
        this.reloadsAvailable = numberOfReloads * capacity;
    }

    @Override
    public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
        if (range == 0 || range >= HIT_CHART.length || HIT_CHART[range] == 0) {
            throw new TargetOutOfRangeException("ADD cannot fire at range " + range);
        }
        if (shots <= 0) {
            throw new WeaponUnarmedException("ADD has no shots remaining");
        }

        shots--;
        registerFire();

        int roll = new DiceRoller().rollOneDie();
        return roll <= HIT_CHART[range] ? HIT : 0;
    }

    @Override
    public boolean canFire() {
        return shots > 0 && super.canFire();
    }

    /**
     * At end of turn: auto-reload from reserve if the ADD did not fire this turn.
     * Must check getShotsThisTurn() before super.cleanUp() resets it.
     */
    @Override
    public void cleanUp() {
        if (getShotsThisTurn() == 0 && shots < capacity && reloadsAvailable > 0) {
            int needed = capacity - shots;
            int reloading = Math.min(needed, reloadsAvailable);
            shots += reloading;
            reloadsAvailable -= reloading;
        }
        super.cleanUp();
    }

    public int getShots() {
        return shots;
    }

    public int getReloadsAvailable() {
        return reloadsAvailable;
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    public int[] getHitChart() {
        return HIT_CHART;
    }
}
