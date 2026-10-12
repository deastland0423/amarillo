package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.ControlSpaces;

/**
 * Annex #7E, the Damage Conversion Chart: "Flag Bridge .... Security Station".
 *
 * <p>G6.12 says why it matters: "Hits designated by the DAMAGE ALLOCATION CHART as 'flag bridge'
 * hits will be scored against the security stations of a Klingon ship. For this reason, no Klingon
 * ship has a 'flag bridge'." The exception is the diplomatic ships, D7N and D5N, which do have one
 * — and they need no special case, because a ship with a flag bridge spends it first and only
 * converts once it is gone.
 *
 * <p>Before this, a flag hit on a hull with no flag boxes returned null and the DAC advanced to
 * the next system, so <b>every Klingon ship in the game had security stations that could not be
 * damaged</b>. Nothing else in the engine damages them — {@code damageSecurity()} existed with no
 * callers at all — which is what makes the assertion below deterministic despite the DAC rolling
 * dice: without the conversion, no amount of damage can touch a security station.
 *
 * <p>It also matters beyond bookkeeping. G6.0 mutiny begins when the LAST security station is
 * destroyed, so a mutiny rule built on an undamageable system would have been unreachable in play
 * — the failure this project has now hit four times.
 */
public class FlagBridgeHitsSecurityTest {

    private Ship ship;

    @Before
    public void setUp() {
        ship = new Ship();
        ship.init(KlingonShips.getD7());
    }

    @Test
    public void aKlingonHullHasSecurityStationsAndNoFlagBridge() {
        ControlSpaces control = ship.getControlSpaces();

        assertTrue("fixture: a Klingon hull carries security stations",
                control.getSecurity() > 0);
        assertEquals("G6.12: no Klingon ship has a flag bridge", 0, control.getFlag());
    }

    @Test
    public void flagBridgeHitsDestroyTheSecurityStations() {
        ControlSpaces control = ship.getControlSpaces();
        int before = control.getAvailableSecurity();
        assertTrue("fixture: there should be stations to lose", before > 0);

        // The DAC result is named rather than rolled for. An earlier version of this test threw
        // 300 points of internal damage at the ship and asserted a security box had gone, which
        // passed twice and then failed: applyInternalDamage stops the moment the excess-damage
        // boxes run out (C3.14), so whether a flag result comes up first is a coin flip, and
        // DiceRoller calls Math.random() with nothing to seed.
        String label = ship.tryApplySystemHit("flag", null);

        assertEquals("a flag-bridge hit is scored on a security station (Annex #7E)",
                "security HIT", label);
        assertEquals("and costs it a box", before - 1, control.getAvailableSecurity());
    }

    @Test
    public void onceTheSecurityStationsAreGoneTheHitFallsThrough() {
        ControlSpaces control = ship.getControlSpaces();
        for (int i = control.getAvailableSecurity(); i > 0; i--)
            assertEquals("security HIT", ship.tryApplySystemHit("flag", null));

        assertEquals("fixture: all of them are gone", 0, control.getAvailableSecurity());
        assertNull("with nothing left to convert to, the DAC advances as it always did",
                ship.tryApplySystemHit("flag", null));
    }

    /**
     * A ship that HAS a flag bridge spends it before any converts — so the conversion cannot
     * cost a diplomatic ship (D7N, D5N) its security stations early.
     */
    @Test
    public void aFlagBridgeIsSpentBeforeTheConversionApplies() {
        ControlSpaces control = new ControlSpaces();
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        values.put("bridge", 2);
        values.put("flag", 1);
        values.put("security", 2);
        control.init(values);

        assertTrue("the flag bridge takes the first hit", control.damageFlag());
        assertEquals("and the security stations are untouched",
                2, control.getAvailableSecurity());

        assertFalse("once it is gone, flag hits no longer land there", control.damageFlag());
        assertTrue("and convert to a security station instead", control.damageSecurity());
        assertEquals(1, control.getAvailableSecurity());
    }
}
