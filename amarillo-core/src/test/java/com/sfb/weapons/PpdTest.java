package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;

/**
 * E11.2 and E11.3: how a PPD arms, and how one firing run plays out pulse by pulse.
 *
 * <h2>Why the dice are not fought here</h2>
 * A wave-lock is a 2d6 roll and the tests must not pass or fail on it. Anything that needs a
 * known lock state sets one by firing until it takes, or asserts over a population rather than
 * a single roll. The one genuinely random thing — whether a given pulse locks — is tested as
 * "a run eventually locks and then stops rolling", which is the behaviour that matters.
 */
public class PpdTest {

    private PPD ppd;

    @Before
    public void setUp() {
        ppd = new PPD();
    }

    /** Arm to the full four-pulse charge the standard cycle buys. */
    private void fullCharge() {
        assertTrue("fixture: first turn of arming", ppd.arm(4));
        assertTrue("fixture: second turn of arming", ppd.arm(4));
        assertTrue("fixture: a full charge should be armed", ppd.isArmed());
    }

    /**
     * Get the device into a locked run with pulses still to come.
     *
     * <p>The wave-lock is an unseeded 2d6, so a single run can legitimately miss all four
     * times — at range 10 that is about one run in twelve hundred. A test that fired once and
     * asserted a lock would fail that often for no reason, which is worse than useless: it
     * teaches people to re-run the suite. So this re-charges and re-fires until it has what
     * the test needs, and fails loudly if the dice are somehow impossible.
     */
    private void lockedWithPulsesLeft(int range) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            ppd.shutDown();
            fullCharge();
            int damage = ppd.fire(range);
            while (damage == 0 && ppd.pulsesRemaining() > 1)
                damage = ppd.pulse(range);
            // isWaveLocked, not merely "that pulse scored". A device that re-rolled a hit
            // every pulse would satisfy the weaker check while the lock was never retained at
            // all — which is exactly the bug the auto-hit test below is meant to catch.
            if (damage > 0 && ppd.isWaveLocked() && ppd.isPulsing())
                return;
        }
        fail("fixture: 100 runs without a wave-lock and a pulse to spare — the dice are broken");
    }

    // ------------------------------------------------------------------ arming (E11.2)

    /** E11.21: four points a turn for two turns, and that buys four pulses. */
    @Test
    public void theStandardChargeIsTwoTurnsAndFourPulses() {
        fullCharge();

        assertEquals(4, ppd.pulsesCharged());
        assertEquals(2, ppd.totalArmingTurns());
    }

    /**
     * E11.25: "arming still requires two consecutive turns with at least two points added on
     * each turn". So one turn of four is not a firing charge, however much energy it holds.
     */
    @Test
    public void oneTurnOfEnergyIsNotACharge() {
        assertTrue(ppd.arm(4));

        assertFalse("a single turn cannot arm a PPD however much is spent", ppd.isArmed());
    }

    /** E11.25: two points a turn buys one pulse a turn — an underloaded device fires fewer. */
    @Test
    public void anUnderloadedDeviceFiresFewerPulses() {
        assertTrue(ppd.arm(2));
        assertTrue(ppd.arm(2));

        assertTrue("two turns at two points is still a charge", ppd.isArmed());
        assertEquals("but only two pulses", 2, ppd.pulsesCharged());
    }

    /** E11.25: a third turn of arming brings an underloaded device up. */
    @Test
    public void athirdTurnCanBringAnUnderloadedDeviceUp() {
        ppd.arm(2);
        ppd.arm(2);
        assertTrue(ppd.arm(4));

        assertEquals(4, ppd.pulsesCharged());
    }

    /**
     * E11.65: more than four points in a turn is an OVERLOAD, which is not implemented. Refused
     * rather than accepted, because accepting it would build a charge this class then fires at
     * the wrong strength.
     */
    @Test
    public void moreThanFourPointsInATurnIsRefused() {
        assertFalse(ppd.arm(6));
        assertFalse("and the charge is untouched", ppd.isArmed());
        assertEquals(0, ppd.pulsesCharged());
    }

    /** A pulse costs exactly two points, so an odd allocation buys nothing. */
    @Test
    public void anOddAllocationIsRefused() {
        assertFalse(ppd.arm(3));
        assertEquals(0, ppd.pulsesCharged());
    }

    /** Nor can a charge grow past four pulses — that is an overload by another route. */
    @Test
    public void aChargeCannotGrowPastFourPulses() {
        fullCharge();

        assertFalse(ppd.arm(2));
        assertEquals(4, ppd.pulsesCharged());
    }

    /** E11.22: holding costs two a turn, and only an armed device has anything to hold. */
    @Test
    public void holdingCostsTwoAndNeedsACharge() throws Exception {
        try {
            ppd.hold(2);
            fail("an unarmed PPD has no charge to hold");
        } catch (WeaponUnarmedException expected) {
            // E11.22 prices the hold of a completed charge; there is nothing here to hold.
        }

        fullCharge();
        assertTrue(ppd.hold(2));
        assertFalse("any other amount is not the holding cost", ppd.hold(3));
    }

    // ------------------------------------------------------------------ firing (E11.3)

    /** E11.312: "The weapon cannot be fired if the true range is three or less." */
    @Test
    public void itCannotBeFiredInsideRangeFour() {
        fullCharge();

        try {
            ppd.fire(3);
            fail("a PPD cannot fire at a true range of three or less");
        } catch (TargetOutOfRangeException expected) {
            assertFalse("and the run never started", ppd.isPulsing());
        } catch (WeaponUnarmedException e) {
            fail("the device was armed: " + e.getMessage());
        }
    }

    /** An unarmed device has nothing to fire. */
    @Test
    public void anUnarmedDeviceCannotFire() {
        try {
            ppd.fire(10);
            fail("an unarmed PPD cannot fire");
        } catch (WeaponUnarmedException expected) {
            // as it should be
        } catch (TargetOutOfRangeException e) {
            fail("range 10 is in band: " + e.getMessage());
        }
    }

    /**
     * E11.30: firing starts a run of four pulses over this impulse and the next three. The
     * first is resolved by the act of firing, so three remain.
     */
    @Test
    public void firingStartsARunOfFourPulses() throws Exception {
        fullCharge();

        ppd.fire(10);

        assertTrue(ppd.isPulsing());
        assertEquals("one pulse was spent firing", 3, ppd.pulsesRemaining());
    }

    /** The charge is spent by firing: a run in progress is not a device ready to fire again. */
    @Test
    public void theChargeIsSpentByFiring() throws Exception {
        fullCharge();

        ppd.fire(10);

        assertFalse("the charge is gone", ppd.isArmed());
        try {
            ppd.fire(10);
            fail("a PPD already pulsing cannot start a second run");
        } catch (WeaponUnarmedException expected) {
            // E11.23: one firing at a time
        }
    }

    /** An underloaded charge fires exactly the pulses it paid for. */
    @Test
    public void anUnderloadedRunIsShorter() throws Exception {
        ppd.arm(2);
        ppd.arm(2);

        ppd.fire(10);

        assertEquals("two pulses bought, one spent", 1, ppd.pulsesRemaining());
    }

    /** Every pulse is consumed, hit or miss, and the run ends when they are gone (E11.331). */
    @Test
    public void theRunEndsWhenThePulsesAreSpent() throws Exception {
        fullCharge();

        ppd.fire(10);
        ppd.pulse(10);
        ppd.pulse(10);
        assertTrue("still one to come", ppd.isPulsing());
        ppd.pulse(10);

        assertFalse("and now the run is over", ppd.isPulsing());
        assertEquals("a spent run scores nothing more", 0, ppd.pulse(10));
    }

    /**
     * E11.331: "Once a wave-lock is achieved, all remaining pulses score damage." The whole
     * point of the weapon — after the lock there is no further roll to fail.
     */
    @Test
    public void onceLockedEveryRemainingPulseScores() throws Exception {
        lockedWithPulsesLeft(10);

        int scored = 0;
        while (ppd.isPulsing()) {
            assertEquals("a locked pulse never misses", 6, ppd.pulse(10));
            scored++;
        }
        assertTrue("fixture: at least one pulse should have remained to prove it", scored > 0);
    }

    /** Damage follows the chart, so a locked run at long range scores the long-range figure. */
    @Test
    public void lockedDamageComesFromTheChart() throws Exception {
        lockedWithPulsesLeft(22);

        assertEquals("three points at range 21-25", 3, ppd.pulse(22));
    }

    /**
     * E11.34: a lock lost to changing conditions is rolled for again on the pulses that remain.
     * The run survives; only the automatic hit is gone.
     */
    @Test
    public void aLostLockIsRolledForAgain() throws Exception {
        lockedWithPulsesLeft(10);

        ppd.loseWaveLock();

        assertFalse(ppd.isWaveLocked());
        assertTrue("but the run continues", ppd.isPulsing());
    }

    /**
     * E11.521: closing inside range 4 mid-run spends the pulse and scores nothing, but does not
     * end the run or drop the lock — the weapon simply cannot reach a target that close.
     */
    @Test
    public void closingInsideRangeFourCostsThePulseAndNoMore() throws Exception {
        lockedWithPulsesLeft(10);
        int before = ppd.pulsesRemaining();

        assertEquals("nothing scores at range 3", 0, ppd.pulse(3));

        assertEquals("the pulse was spent", before - 1, ppd.pulsesRemaining());
        assertTrue("and the lock survives the approach", ppd.isWaveLocked());
    }

    /** E11.362: shutting down loses every remaining pulse — there is no pausing (E11.333). */
    @Test
    public void shuttingDownEndsTheRun() throws Exception {
        fullCharge();
        ppd.fire(10);

        ppd.shutDown();

        assertFalse(ppd.isPulsing());
        assertFalse(ppd.isWaveLocked());
    }

    // ------------------------------------------------------------------ allocation + DAC

    /** E11.12: "PPDs are destroyed on 'drone' hits." */
    @Test
    public void aPpdIsDestroyedOnDroneHits() {
        assertEquals("drone", ppd.getDacHitLocaiton());
    }

    /** Allocating nothing at the start of a turn discharges the charge (E1.24). */
    @Test
    public void anUnpaidChargeIsLost() {
        fullCharge();

        ppd.applyAllocationEnergy(null, null);

        assertFalse(ppd.isArmed());
        assertEquals(0, ppd.pulsesCharged());
    }

    /** Two points at allocation holds an armed device rather than re-arming it (E11.22). */
    @Test
    public void twoPointsHoldsAnArmedCharge() {
        fullCharge();

        ppd.applyAllocationEnergy(2.0, null);

        assertTrue(ppd.isArmed());
        assertEquals("and the charge is unchanged", 4, ppd.pulsesCharged());
    }

    /** E11.6: overload is not built, so the device refuses to be put in that mode. */
    @Test
    public void overloadIsRefusedRatherThanFaked() {
        assertTrue(ppd.setStandard());
        assertFalse(ppd.setOverload());
    }

    // ------------------------------------------------------------------ from ship data

    /**
     * THE GAP THAT MADE THE WEAPON UNUSABLE. Until WeaponFactory had a case for it, a PPD could
     * only be built in Java — so no ship file could declare one, which meant no ISC hull could
     * be entered at all. Every other test in this class constructs the device directly and
     * would have passed throughout.
     */
    @Test
    public void ashipFileCanDeclareAPpd() {
        com.sfb.objects.ShipSpec.WeaponSpec spec = new com.sfb.objects.ShipSpec.WeaponSpec();
        spec.type = "PPD";
        spec.designator = "1";

        com.sfb.weapons.Weapon built = com.sfb.objects.WeaponFactory.build(
                spec, java.util.List.of("FA"));

        assertTrue("a PPD recipe must build a PPD", built instanceof PPD);
        assertEquals("1", built.getDesignator());
        assertEquals("drone", built.getDacHitLocaiton());
    }
}
