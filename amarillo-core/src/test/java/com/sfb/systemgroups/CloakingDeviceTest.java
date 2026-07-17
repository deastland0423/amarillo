package com.sfb.systemgroups;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import com.sfb.systemgroups.CloakingDevice.CloakState;

public class CloakingDeviceTest {

    private CloakingDevice cloak;

    @Before
    public void setUp() {
        cloak = new CloakingDevice(20);
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    public void testInitialStateIsInactive() {
        assertEquals(CloakState.INACTIVE, cloak.getState());
    }

    @Test
    public void testInitiallyFunctional() {
        assertTrue(cloak.isFunctional());
    }

    @Test
    public void testInitiallyNotRestrictingActions() {
        assertFalse(cloak.isRestrictingActions());
    }

    @Test
    public void testInitiallyNoLockOnBreak() {
        assertFalse(cloak.breaksLockOn());
    }

    @Test
    public void testPowerToActivate() {
        assertEquals(20, cloak.getPowerToActivate());
    }

    // -------------------------------------------------------------------------
    // Activation guards
    // -------------------------------------------------------------------------

    @Test
    public void testActivateFailsWithoutCostPaid() {
        assertFalse(cloak.activate(1));
        assertEquals(CloakState.INACTIVE, cloak.getState());
    }

    @Test
    public void testActivateSucceedsWhenCostPaid() {
        cloak.setCostPaid(true);
        assertTrue(cloak.activate(1));
        assertEquals(CloakState.FADING_OUT, cloak.getState());
    }

    @Test
    public void testCannotActivateTwiceInOneTurn() {
        cloak.setCostPaid(true);
        assertTrue(cloak.activate(1));
        assertFalse(cloak.activate(5));  // same turn, different impulse
        assertEquals(CloakState.FADING_OUT, cloak.getState());
    }

    @Test
    public void testCannotActivateWhenAlreadyFadingOut() {
        cloak.setCostPaid(true);
        cloak.activate(1);
        cloak.setCostPaid(true);  // simulate next turn paying again
        assertFalse(cloak.activate(2));
    }

    @Test
    public void testCannotActivateWhenFullyCloaked() {
        cloak.setCostPaid(true);
        cloak.activate(1);
        for (int i = 2; i <= 6; i++) cloak.updateState(i);
        assertEquals(CloakState.FULLY_CLOAKED, cloak.getState());

        cloak.setCostPaid(true);
        assertFalse(cloak.activate(7));
    }

    // -------------------------------------------------------------------------
    // Fade-out sequence
    // -------------------------------------------------------------------------

    @Test
    public void testFadeOutAdvancesToFullyCloakedAfter5Impulses() {
        cloak.setCostPaid(true);
        cloak.activate(1);
        assertEquals(CloakState.FADING_OUT, cloak.getState());

        // Impulses 2-5: still fading (step 2-5, elapsed 2-5, not yet > 5)
        for (int i = 2; i <= 5; i++) {
            cloak.updateState(i);
            assertEquals(CloakState.FADING_OUT, cloak.getState());
        }

        // Impulse 6: elapsed = 6 > 5 → fully cloaked
        cloak.updateState(6);
        assertEquals(CloakState.FULLY_CLOAKED, cloak.getState());
    }

    @Test
    public void testFadeStepDuringFadeOut() {
        cloak.setCostPaid(true);
        cloak.activate(10);

        // Bonus starts on the activation impulse (G13.302)
        assertEquals(1, cloak.getFadeStep(10));
        assertEquals(2, cloak.getFadeStep(11));
        assertEquals(3, cloak.getFadeStep(12));
        assertEquals(4, cloak.getFadeStep(13));
        assertEquals(5, cloak.getFadeStep(14));
    }

    @Test
    public void testCloakBonusDuringFadeOut() {
        cloak.setCostPaid(true);
        cloak.activate(10);

        // Bonus starts on the activation impulse (G13.302)
        assertEquals(1, cloak.getCloakBonus(10));
        assertEquals(2, cloak.getCloakBonus(11));
        assertEquals(3, cloak.getCloakBonus(12));
        assertEquals(4, cloak.getCloakBonus(13));
        assertEquals(5, cloak.getCloakBonus(14));
    }

    // -------------------------------------------------------------------------
    // Fully cloaked state
    // -------------------------------------------------------------------------

    @Test
    public void testFullyCloakedBreaksLockOn() {
        activateAndFullyCloak(1);
        assertTrue(cloak.breaksLockOn());
    }

    @Test
    public void testFullyCloakedRestrictsActions() {
        activateAndFullyCloak(1);
        assertTrue(cloak.isRestrictingActions());
    }

    @Test
    public void testFullyCloakedCloakBonusIsFive() {
        activateAndFullyCloak(1);
        assertEquals(5, cloak.getCloakBonus(10));
    }

    @Test
    public void testFadeStepIsZeroWhenFullyCloaked() {
        activateAndFullyCloak(1);
        assertEquals(0, cloak.getFadeStep(10));
    }

    // -------------------------------------------------------------------------
    // Deactivation guards
    // -------------------------------------------------------------------------

    @Test
    public void testDeactivateFailsWhenInactive() {
        assertFalse(cloak.deactivate(1));
    }

    @Test
    public void testDeactivateFailsWhenAlreadyFadingIn() {
        activateAndFullyCloak(1);
        assertTrue(cloak.deactivate(10));
        assertFalse(cloak.deactivate(11));  // already fading in
    }

    @Test
    public void testCannotDeactivateTwiceInOneTurn() {
        activateAndFullyCloak(1);
        cloak.newTurn(10);
        assertTrue(cloak.deactivate(10));
        assertFalse(cloak.deactivate(10));
    }

    @Test
    public void testDeactivateFromFadingOut() {
        cloak.setCostPaid(true);
        cloak.activate(1);
        assertEquals(CloakState.FADING_OUT, cloak.getState());
        assertTrue(cloak.deactivate(3));
        assertEquals(CloakState.FADING_IN, cloak.getState());
    }

    // -------------------------------------------------------------------------
    // G13.115 — reversing a fade retraces it, not a fresh 5-impulse fade
    // -------------------------------------------------------------------------

    @Test
    public void reversedFadeIn_matchesRulebookExample() {
        // G13.115 example: cloak dropped on impulse 10 (+5), fading in on 11
        // (+4) and 12 (+3); reversed on 13 → 13 (+4), 14 (+5), fully cloaked 15
        activateAndFullyCloak(1);
        cloak.deactivate(10);
        assertEquals(5, cloak.getCloakBonus(10));
        cloak.updateState(11);
        assertEquals(4, cloak.getCloakBonus(11));
        cloak.updateState(12);
        assertEquals(3, cloak.getCloakBonus(12));

        // New turn so the once-per-turn flags allow the reversal
        cloak.newTurn(13);
        assertTrue(cloak.activate(13));
        assertEquals(CloakState.FADING_OUT, cloak.getState());
        assertEquals(4, cloak.getCloakBonus(13));
        cloak.updateState(14);
        assertEquals(5, cloak.getCloakBonus(14));
        cloak.updateState(15);
        assertEquals(CloakState.FULLY_CLOAKED, cloak.getState());
    }

    @Test
    public void reversedFadeOut_fadesBackTheSameNumberOfImpulses() {
        // Fade-out impulses 1 (+1) and 2 (+2); reversed on 3 → 3 (+1), inactive 4
        cloak.setCostPaid(true);
        cloak.activate(1);
        assertEquals(1, cloak.getCloakBonus(1));
        cloak.updateState(2);
        assertEquals(2, cloak.getCloakBonus(2));

        assertTrue(cloak.deactivate(3));
        assertEquals(CloakState.FADING_IN, cloak.getState());
        assertEquals(1, cloak.getCloakBonus(3));
        cloak.updateState(4);
        assertEquals(CloakState.INACTIVE, cloak.getState());
    }

    @Test
    public void reversedFade_cannotBeReversedAgainSameTurn() {
        // G13.115: once reversed, that fade cannot be reversed again — the
        // combined action consumes both the turn's cloak and uncloak
        cloak.setCostPaid(true);
        cloak.activate(1);
        assertTrue(cloak.deactivate(3));  // reversal
        assertFalse(cloak.activate(4));   // blocked: already cloaked this turn
    }

    // -------------------------------------------------------------------------
    // Fade-in sequence
    // -------------------------------------------------------------------------

    @Test
    public void testFadeInAdvancesToInactiveAfter5Impulses() {
        activateAndFullyCloak(1);
        cloak.deactivate(10);
        assertEquals(CloakState.FADING_IN, cloak.getState());

        // Impulses 11-14: still fading in (elapsed 2-5, not yet > 5)
        for (int i = 11; i <= 14; i++) {
            cloak.updateState(i);
            assertEquals(CloakState.FADING_IN, cloak.getState());
        }

        // Impulse 15: elapsed = 6 > 5 → inactive
        cloak.updateState(15);
        assertEquals(CloakState.INACTIVE, cloak.getState());
    }

    @Test
    public void testCloakBonusDuringFadeIn() {
        activateAndFullyCloak(1);
        cloak.deactivate(10);

        // Fade-in bonus starts on the deactivation impulse
        assertEquals(5, cloak.getCloakBonus(10));
        assertEquals(4, cloak.getCloakBonus(11));
        assertEquals(3, cloak.getCloakBonus(12));
        assertEquals(2, cloak.getCloakBonus(13));
        assertEquals(1, cloak.getCloakBonus(14));
    }

    @Test
    public void testFadeStepDuringFadeIn() {
        activateAndFullyCloak(1);
        cloak.deactivate(10);

        // Fade-in step starts on the deactivation impulse
        assertEquals(1, cloak.getFadeStep(10));
        assertEquals(2, cloak.getFadeStep(11));
        assertEquals(3, cloak.getFadeStep(12));
        assertEquals(4, cloak.getFadeStep(13));
        assertEquals(5, cloak.getFadeStep(14));
    }

    @Test
    public void testNoLockOnBreakDuringFadeIn() {
        activateAndFullyCloak(1);
        cloak.deactivate(10);
        assertFalse(cloak.breaksLockOn());
    }

    @Test
    public void testActionsRestrictedDuringFadeIn() {
        activateAndFullyCloak(1);
        cloak.deactivate(10);
        assertTrue(cloak.isRestrictingActions());
    }

    @Test
    public void testActionsAllowedAfterFullFadeIn() {
        activateAndFullyCloak(1);
        cloak.deactivate(10);
        for (int i = 11; i <= 15; i++) cloak.updateState(i);
        assertFalse(cloak.isRestrictingActions());
    }

    // -------------------------------------------------------------------------
    // Involuntary decloak
    // -------------------------------------------------------------------------

    @Test
    public void testInvoluntaryDecloakWhenCostNotPaid() {
        activateAndFullyCloak(1);
        // Simulate allocation next turn where player does not check the cloak box
        cloak.setCostPaid(false);
        cloak.newTurn(33);
        assertEquals(CloakState.FADING_IN, cloak.getState());
    }

    @Test
    public void testNoInvoluntaryDecloakWhenCostPaid() {
        activateAndFullyCloak(1);
        // Simulate allocation next turn where player pays the cloak cost
        cloak.setCostPaid(true);
        cloak.newTurn(33);
        assertEquals(CloakState.FULLY_CLOAKED, cloak.getState());
    }

    @Test
    public void testNoInvoluntaryDecloakWhenAlreadyInactive() {
        // Cost not paid, but device is inactive — nothing should happen
        cloak.newTurn(1);
        assertEquals(CloakState.INACTIVE, cloak.getState());
    }

    // -------------------------------------------------------------------------
    // Damage
    // -------------------------------------------------------------------------

    @Test
    public void testDamageRendersDeviceNonFunctional() {
        cloak.damage(1);
        assertFalse(cloak.isFunctional());
        assertEquals(0, cloak.fetchRemainingTotalBoxes());
    }

    @Test
    public void testDamageWhileFullyCloakedForcesEmergencyFadeIn() {
        activateAndFullyCloak(1);
        assertEquals(CloakState.FULLY_CLOAKED, cloak.getState());
        cloak.damage(10);
        assertEquals(CloakState.FADING_IN, cloak.getState());
    }

    @Test
    public void testDamageWhileFadingOutForcesEmergencyFadeIn() {
        cloak.setCostPaid(true);
        cloak.activate(1);
        assertEquals(CloakState.FADING_OUT, cloak.getState());
        cloak.damage(3);
        assertEquals(CloakState.FADING_IN, cloak.getState());
    }

    @Test
    public void testDamageWhileInactiveDoesNotChangeFadeState() {
        cloak.damage(1);
        assertEquals(CloakState.INACTIVE, cloak.getState());
    }

    @Test
    public void testDamageWhileAlreadyFadingInDoesNotChangeFadeState() {
        activateAndFullyCloak(1);
        cloak.deactivate(10);
        assertEquals(CloakState.FADING_IN, cloak.getState());
        cloak.damage(11);
        assertEquals(CloakState.FADING_IN, cloak.getState());
    }

    @Test
    public void testSecondDamageCallIsIgnored() {
        cloak.damage(1);
        cloak.damage(2);  // should be a no-op
        assertFalse(cloak.isFunctional());
        assertEquals(CloakState.INACTIVE, cloak.getState());
    }

    // -------------------------------------------------------------------------
    // Inactive state bonus / step
    // -------------------------------------------------------------------------

    @Test
    public void testCloakBonusIsZeroWhenInactive() {
        assertEquals(0, cloak.getCloakBonus(1));
    }

    @Test
    public void testFadeStepIsZeroWhenInactive() {
        assertEquals(0, cloak.getFadeStep(1));
    }

    // -------------------------------------------------------------------------
    // G13.37 Fire Adjustment Chart
    // -------------------------------------------------------------------------

    @Test
    public void fireAdjustment_die1And2_fullDamage() {
        assertEquals(9, CloakingDevice.fireAdjustedDamage(9, 1));
        assertEquals(9, CloakingDevice.fireAdjustedDamage(9, 2));
    }

    @Test
    public void fireAdjustment_die3And4_halfDamage_roundsHalfUp() {
        assertEquals(4, CloakingDevice.fireAdjustedDamage(8, 3)); // 4.0
        assertEquals(3, CloakingDevice.fireAdjustedDamage(5, 3)); // 2.5 → 3 (G13.371)
        assertEquals(1, CloakingDevice.fireAdjustedDamage(1, 4)); // 0.5 → 1
    }

    @Test
    public void fireAdjustment_die5And6_quarterDamage_roundsHalfUp() {
        assertEquals(2, CloakingDevice.fireAdjustedDamage(8, 5)); // 2.0
        assertEquals(2, CloakingDevice.fireAdjustedDamage(6, 5)); // 1.5 → 2
        assertEquals(1, CloakingDevice.fireAdjustedDamage(5, 6)); // 1.25 → 1
        assertEquals(0, CloakingDevice.fireAdjustedDamage(1, 6)); // 0.25 → 0
    }

    @Test
    public void fireAdjustment_die7Plus_noDamage() {
        // Unreachable with a plain d6 today — needs positive modifiers (G13.62)
        assertEquals(0, CloakingDevice.fireAdjustedDamage(9, 7));
    }

    @Test
    public void fireAdjustment_labels() {
        assertEquals("full damage", CloakingDevice.fireAdjustmentLabel(2));
        assertEquals("1/2 damage",  CloakingDevice.fireAdjustmentLabel(3));
        assertEquals("1/4 damage",  CloakingDevice.fireAdjustmentLabel(6));
        assertEquals("no damage",   CloakingDevice.fireAdjustmentLabel(7));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Activate and advance until FULLY_CLOAKED (activation impulse = step 1, takes 5 more). */
    private void activateAndFullyCloak(int startImpulse) {
        cloak.setCostPaid(true);
        cloak.activate(startImpulse);
        for (int i = startImpulse + 1; i <= startImpulse + 5; i++) {
            cloak.updateState(i);
        }
        assertEquals(CloakState.FULLY_CLOAKED, cloak.getState());
    }
}
