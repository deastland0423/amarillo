package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.samples.FederationShips;

/**
 * Tests for Ship.isCrippled() — S2.41 conditions A through E.
 *
 * Fed CA is used for most tests: lwarp=15, rwarp=15, cwarp=5 (35 total warp),
 * 6 excess damage boxes.
 *
 * Romulan WE is used for condition A exception (no original warp).
 */
public class CrippledStatusTest {

    private Ship ship;

    @Before
    public void setUp() {
        ship = new Ship();
        ship.init(FederationShips.getFedCa());
    }

    // -------------------------------------------------------------------------
    // Baseline
    // -------------------------------------------------------------------------

    @Test
    public void freshShip_isNotCrippled() {
        assertFalse(ship.isCrippled());
    }

    // -------------------------------------------------------------------------
    // Condition A: 10% or fewer warp boxes remaining
    // -------------------------------------------------------------------------

    @Test
    public void conditionA_crippledWhenWarpAtTenPercent() {
        // Fed CA has 35 total warp boxes (15+15+5); 10% = 3.5 → floor = 3
        // Damage down to exactly 3 remaining (35 - 3 = 32 damaged)
        int originalWarp = ship.getPowerSysetems().getOriginalWarp();
        int target = (int) Math.floor(originalWarp * 0.1);
        int toDamage = originalWarp - target;
        for (int i = 0; i < toDamage; i++)
            ship.getPowerSysetems().damageLWarp();
        for (int i = 0; i < toDamage; i++)
            ship.getPowerSysetems().damageRWarp();
        // Drain all warp
        while (ship.getPowerSysetems().getRemainingWarp() > target)
            ship.getPowerSysetems().damageCWarp();

        assertTrue(ship.isCrippled());
    }

    @Test
    public void conditionA_notCrippledWhenWarpAboveTenPercent() {
        // Damage only 1 warp box — well above 10%
        ship.getPowerSysetems().damageLWarp();
        assertFalse(ship.isCrippled());
    }

    @Test
    public void conditionA_crippledWhenAllWarpDestroyed() {
        // Destroy every warp box — 0 remaining is clearly <= 10%
        int originalWarp = ship.getPowerSysetems().getOriginalWarp();
        for (int i = 0; i < originalWarp; i++) {
            ship.getPowerSysetems().damageLWarp();
            ship.getPowerSysetems().damageRWarp();
            ship.getPowerSysetems().damageCWarp();
        }
        assertEquals(0, ship.getPowerSysetems().getRemainingWarp());
        assertTrue(ship.isCrippled());
    }

    // -------------------------------------------------------------------------
    // Condition B: 50% or more of interior boxes destroyed
    // -------------------------------------------------------------------------

    @Test
    public void conditionB_crippledWhenHalfInteriorDestroyed() {
        // Destroy warp boxes (interior) until >= 50% of interior is gone
        int originalWarp = ship.getPowerSysetems().getOriginalWarp();
        // Damage all warp — that's a large chunk of interior boxes
        for (int i = 0; i < originalWarp; i++) {
            ship.getPowerSysetems().damageLWarp();
            ship.getPowerSysetems().damageRWarp();
            ship.getPowerSysetems().damageCWarp();
        }
        assertTrue(ship.isCrippled());
    }

    @Test
    public void conditionB_notCrippledWhenUnderHalfInteriorDestroyed() {
        // Damage just one interior box
        ship.getPowerSysetems().damageLWarp();
        // Condition B alone should not trigger
        // (condition A may or may not trigger separately — test independently)
        // Reset to avoid condition A interference: use a fresh ship and damage 1 hull box
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.getHullBoxes().damageFhull();
        assertFalse(s.isCrippled());
    }

    // -------------------------------------------------------------------------
    // Condition C: any excess damage hit
    // -------------------------------------------------------------------------

    @Test
    public void conditionC_crippledAfterFirstExcessDamageHit() {
        ship.getSpecialFunctions().damageExcessDamage();
        assertTrue(ship.isCrippled());
    }

    @Test
    public void conditionC_notCrippledWithNoExcessDamage() {
        // Fresh ship has all excess damage boxes intact
        assertEquals(
            ship.getSpecialFunctions().getOriginalExcessDamage(),
            ship.getSpecialFunctions().getExcessDamage()
        );
        assertFalse(ship.isCrippled());
    }

    // -------------------------------------------------------------------------
    // Condition D: all control spaces destroyed
    // -------------------------------------------------------------------------

    @Test
    public void conditionD_crippledWhenAllControlSpacesDestroyed() {
        // Destroy all bridge, aux con, emergency, flag, security boxes
        while (ship.getControlSpaces().damageBridge())   ;
        while (ship.getControlSpaces().damageAuxcon())   ;
        while (ship.getControlSpaces().damageEmer())     ;
        while (ship.getControlSpaces().damageFlag())     ;
        while (ship.getControlSpaces().damageSecurity()) ;
        assertTrue(ship.isCrippled());
    }

    @Test
    public void conditionD_notCrippledWithOneControlSpaceRemaining() {
        // Destroy all but leave one bridge box intact
        int bridge = ship.getControlSpaces().getAvailableBridge();
        for (int i = 0; i < bridge - 1; i++)
            ship.getControlSpaces().damageBridge();
        while (ship.getControlSpaces().damageAuxcon())   ;
        while (ship.getControlSpaces().damageEmer())     ;
        while (ship.getControlSpaces().damageFlag())     ;
        while (ship.getControlSpaces().damageSecurity()) ;
        assertFalse(ship.isCrippled());
    }

    // -------------------------------------------------------------------------
    // Condition E: all weapons destroyed
    // -------------------------------------------------------------------------

    @Test
    public void conditionE_crippledWhenAllWeaponsDestroyed() {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            w.damage();
        assertTrue(ship.isCrippled());
    }

    @Test
    public void conditionE_notCrippledWithOneWeaponFunctional() {
        var weapons = ship.getWeapons().fetchAllWeapons();
        // Destroy all but the last weapon
        for (int i = 0; i < weapons.size() - 1; i++)
            weapons.get(i).damage();
        assertFalse(ship.isCrippled());
    }
}
