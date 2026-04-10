package com.sfb.systemgroups;

import com.sfb.weapons.Disruptor;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for UIM (Ubitron Interface Module) — D6.5.
 */
public class UIMTest {

    // -------------------------------------------------------------------------
    // isFunctional
    // -------------------------------------------------------------------------

    @Test
    public void activeUim_isFunctional() {
        UIM uim = new UIM(); // standbyActiveAt = -1 → already active
        assertTrue(uim.isFunctional(0));
        assertTrue(uim.isFunctional(99));
    }

    @Test
    public void damagedUim_isNotFunctional() {
        UIM uim = new UIM();
        uim.damage();
        assertFalse(uim.isFunctional(0));
    }

    @Test
    public void standbyUim_notFunctionalBeforeActivationImpulse() {
        UIM uim = new UIM();
        uim.scheduleActivation(10);
        assertFalse(uim.isFunctional(9));
        assertFalse(uim.isFunctional(0));
    }

    @Test
    public void standbyUim_functionalAtAndAfterActivationImpulse() {
        UIM uim = new UIM();
        uim.scheduleActivation(10);
        assertTrue(uim.isFunctional(10));
        assertTrue(uim.isFunctional(20));
    }

    // -------------------------------------------------------------------------
    // damage / isDamaged
    // -------------------------------------------------------------------------

    @Test
    public void undamagedUim_isNotDamaged() {
        assertFalse(new UIM().isDamaged());
    }

    @Test
    public void damage_marksUimPermanentlyDamaged() {
        UIM uim = new UIM();
        uim.damage();
        assertTrue(uim.isDamaged());
        assertFalse(uim.isFunctional(999));
    }

    // -------------------------------------------------------------------------
    // scheduleActivation
    // -------------------------------------------------------------------------

    @Test
    public void scheduleActivation_setsStandbyDelay() {
        UIM uim = new UIM();
        uim.scheduleActivation(20);
        assertFalse(uim.isFunctional(19));
        assertTrue(uim.isFunctional(20));
    }

    // -------------------------------------------------------------------------
    // checkBurnout (D6.521) — rolls 1d6, ≤2 = burnout
    // We can't control dice, so we run many trials and verify the contract
    // holds: burnout → UIM is damaged and disruptors are locked.
    // -------------------------------------------------------------------------

    @Test
    public void checkBurnout_whenBurnout_uimIsDamaged() {
        // Run enough times to guarantee at least one burnout (p=1/3 per roll)
        UIM uim = new UIM();
        Disruptor d = armedDisruptor();
        boolean burnedOut = false;
        for (int i = 0; i < 50 && !burnedOut; i++) {
            uim = new UIM();
            d = armedDisruptor();
            burnedOut = uim.checkBurnout(5, List.of(d));
        }
        assertTrue("Should have burned out within 50 rolls", burnedOut);
        assertTrue(uim.isDamaged());
    }

    @Test
    public void checkBurnout_whenBurnout_disruptorsAreLocked() {
        UIM uim = new UIM();
        Disruptor d = armedDisruptor();
        boolean burnedOut = false;
        for (int i = 0; i < 50 && !burnedOut; i++) {
            uim = new UIM();
            d = armedDisruptor();
            burnedOut = uim.checkBurnout(5, List.of(d));
        }
        assertTrue("Should have burned out within 50 rolls", burnedOut);
        // Locked for 32 impulses: impulse 5 + 32 = 37
        assertTrue(d.isUimLocked(36));
        assertFalse(d.isUimLocked(37));
    }

    @Test
    public void checkBurnout_whenNoBurnout_uimIsNotDamaged() {
        // Run enough times to guarantee at least one non-burnout (p=2/3 per roll)
        boolean survived = false;
        for (int i = 0; i < 50 && !survived; i++) {
            UIM uim = new UIM();
            Disruptor d = armedDisruptor();
            boolean burnout = uim.checkBurnout(5, List.of(d));
            if (!burnout) {
                survived = true;
                assertFalse(uim.isDamaged());
                assertFalse(d.isUimLocked(5));
            }
        }
        assertTrue("Should have survived at least once within 50 rolls", survived);
    }

    @Test
    public void checkBurnout_emptyDisruptorList_noLockApplied() {
        UIM uim = new UIM();
        // Even if burnout occurs, an empty list means no disruptors to lock
        boolean burnedOut = false;
        for (int i = 0; i < 50 && !burnedOut; i++) {
            uim = new UIM();
            burnedOut = uim.checkBurnout(5, Collections.emptyList());
        }
        assertTrue("Should have burned out within 50 rolls", burnedOut);
        assertTrue(uim.isDamaged()); // UIM still burns out
    }

    // -------------------------------------------------------------------------
    // Ship-level UIM management (via KlingonShips.getD7 → has 4 UIMs)
    // -------------------------------------------------------------------------

    @Test
    public void ship_withUim_hasActiveUim() {
        com.sfb.objects.Ship d7 = buildD7();
        assertNotNull(d7.getActiveUim(0));
    }

    @Test
    public void ship_withoutUim_hasNoActiveUim() {
        com.sfb.objects.Ship fed = buildFedCa();
        assertNull(fed.getActiveUim(0));
    }

    @Test
    public void ship_hasUim_trueForD7() {
        assertTrue(buildD7().hasUim());
    }

    @Test
    public void ship_hasUim_falseForFedCa() {
        assertFalse(buildFedCa().hasUim());
    }

    @Test
    public void activateNextStandby_schedulesNextUimAfter8Impulses() {
        com.sfb.objects.Ship d7 = buildD7();
        UIM active = d7.getActiveUim(0);
        assertNotNull(active);

        // Simulate burnout of the active UIM at impulse 10
        active.damage();
        d7.activateNextStandby(active, 10);

        // During the 8-impulse delay, no UIM should be active (D6.542)
        assertNull("No UIM should be active during standby delay at impulse 10", d7.getActiveUim(10));
        assertNull("No UIM should be active during standby delay at impulse 17", d7.getActiveUim(17));

        // At impulse 18 (10 + 8), the next standby becomes active
        UIM next = d7.getActiveUim(18);
        assertNotNull("Next UIM should be active at impulse 18", next);
        assertNotSame(active, next);
    }

    @Test
    public void onlyOneUimActiveAtStart() {
        com.sfb.objects.Ship d7 = buildD7();
        long activeCount = d7.getUims().stream().filter(u -> u.isFunctional(0)).count();
        assertEquals("Only one UIM should be active at game start", 1, activeCount);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Disruptor armedDisruptor() {
        Disruptor d = new Disruptor(30);
        d.arm(2);
        return d;
    }

    private com.sfb.objects.Ship buildD7() {
        com.sfb.objects.Ship ship = new com.sfb.objects.Ship();
        ship.init(com.sfb.samples.KlingonShips.getD7());
        return ship;
    }

    private com.sfb.objects.Ship buildFedCa() {
        com.sfb.objects.Ship ship = new com.sfb.objects.Ship();
        ship.init(com.sfb.samples.FederationShips.getFedCa());
        return ship;
    }
}
