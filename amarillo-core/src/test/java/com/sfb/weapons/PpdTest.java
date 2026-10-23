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

    /** E11.611: "No more than four points of energy can be allocated on the first turn." */
    @Test
    public void moreThanFourPointsOnTheFirstTurnIsRefused() {
        assertFalse(ppd.arm(6));
        assertFalse("and the charge is untouched", ppd.isArmed());
        assertEquals(0, ppd.pulsesCharged());
    }

    // ------------------------------------------------------------------ overloads (E11.6)

    /**
     * E11.61: eight points on the second turn instead of four. "The damage is not increased;
     * instead, the weapon fires six pulses over six impulses rather than four over four."
     */
    @Test
    public void anOverloadBuysPulsesNotDamage() {
        assertTrue(ppd.arm(4));
        assertTrue("eight points is legal on the SECOND turn", ppd.arm(8));

        assertTrue(ppd.isOverloaded());
        assertEquals("six pulses, not four", 6, ppd.pulsesCharged());
        assertEquals("and the chart is untouched — damage is unchanged",
                6, PpdSplash.totalDamage(7));
    }

    /**
     * E11.64: "The maximum true range for an overloaded PPD is eight hexes." A severe trade for
     * the two extra pulses — it gives up the long reach the weapon exists for.
     */
    @Test
    public void anOverloadedDeviceReachesOnlyEightHexes() {
        ppd.arm(4);
        ppd.arm(8);

        assertEquals(8, ppd.getMaxRange());
        try {
            ppd.fire(10);
            fail("an overloaded PPD cannot reach ten hexes");
        } catch (TargetOutOfRangeException expected) {
            // E11.64
        } catch (WeaponUnarmedException e) {
            fail("the device was armed: " + e.getMessage());
        }
    }

    /** And still fires inside that reach, so the restriction is a range bound, not a block. */
    @Test
    public void anOverloadedDeviceFiresWithinEight() throws Exception {
        ppd.arm(4);
        ppd.arm(8);

        ppd.fire(7);

        assertTrue(ppd.isPulsing());
        assertEquals("five pulses still owed of six", 5, ppd.pulsesRemaining());
    }

    /** E11.612: six points on the second turn is an "underloaded overload" — one extra pulse. */
    @Test
    public void anUnderloadedOverloadGainsOnlyOnePulse() {
        ppd.arm(4);
        assertTrue(ppd.arm(6));

        assertTrue(ppd.isOverloaded());
        assertEquals(5, ppd.pulsesCharged());
    }

    /**
     * E11.65: "If the PPD was not fully loaded on the first turn, adding more than four points
     * of energy on the second turn is still treated as an overloaded weapon, even though it
     * will have fewer total pulses." Two plus six is four pulses — no more than a standard
     * charge — and still an overload, with the eight-hex reach that costs.
     */
    @Test
    public void anOverloadStaysOneEvenWithFewerPulsesThanStandard() {
        ppd.arm(2);
        assertTrue(ppd.arm(6));

        assertEquals("the same four pulses a standard charge gets", 4, ppd.pulsesCharged());
        assertTrue("but it is an overload all the same", ppd.isOverloaded());
        assertEquals("and pays the eight-hex reach for nothing", 8, ppd.getMaxRange());
    }

    /** E11.62: "Overloaded PPDs cannot be held." */
    @Test
    public void anOverloadCannotBeHeld() throws Exception {
        ppd.arm(4);
        ppd.arm(8);

        assertFalse("an overload must be fired or lost", ppd.hold(2));
    }

    /** A standard charge still can be, so the refusal is about the overload and not holding. */
    @Test
    public void astandardChargeStillHolds() throws Exception {
        fullCharge();

        assertTrue(ppd.hold(2));
    }

    /** The arming type reports what the energy made it, rather than a mode somebody set. */
    @Test
    public void theArmingTypeFollowsTheEnergy() {
        fullCharge();
        assertEquals(com.sfb.properties.WeaponArmingType.STANDARD, ppd.getArmingType());

        PPD other = new PPD();
        other.arm(4);
        other.arm(8);
        assertEquals(com.sfb.properties.WeaponArmingType.OVERLOAD, other.getArmingType());
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

    /**
     * THE OTHER HALF OF E11.521, and the half that would break silently. "If the true range
     * subsequently increases beyond the minimum while the weapon is still firing, it will cause
     * damage during the impulses after the range increases."
     *
     * <p>So the myopic zone is a pause, not an ending: a target that dives inside range 4 and
     * comes back out again is still held, and the pulses that remain score as if nothing had
     * happened. A model that merely "kept the lock" without resuming damage would pass the test
     * above and fail here.
     */
    @Test
    public void damageResumesWhenTheTargetLeavesTheMyopicZone() throws Exception {
        lockedWithPulsesLeft(10);
        assertTrue("fixture: expected a lock", ppd.isWaveLocked());
        assertTrue("fixture: expected at least two pulses to spend", ppd.pulsesRemaining() >= 2);

        assertEquals("inside the zone it scores nothing", 0, ppd.pulse(2));
        // The lock itself, not just the damage. Without this the test passes about five times
        // in six against a device that DROPS the lock in the zone, because the pulse that
        // follows simply re-rolls a hit at range 10 — which is not the same weapon at all.
        assertTrue("the lock is held through the zone", ppd.isWaveLocked());

        assertEquals("and the moment it is outside again it scores in full", 6, ppd.pulse(10));
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

    // ------------------------------------------------------- E11.63, overloading a held charge

    /**
     * E11.63: "Held PPDs can also be overloaded by allocating overload energy during Energy
     * Allocation, but this energy would be in addition to the holding energy."
     *
     * <p>Two to hold and four more on top of a complete charge: eight points becomes twelve,
     * which is exactly E11.61's overload at six pulses.
     */
    @Test
    public void aheldChargeCanBeOverloadedAtAllocation() {
        fullCharge();

        ppd.applyAllocationEnergy(6.0, null);   // 2 holding + 4 overload

        assertTrue(ppd.isArmed());
        assertTrue(ppd.isOverloaded());
        assertEquals(6, ppd.pulsesCharged());
        assertEquals("and it pays the eight-hex reach for them", 8, ppd.getMaxRange());
    }

    /** Two of those points buys the one extra pulse of E11.612 rather than two. */
    @Test
    public void asmallerTopUpBuysProportionallyFewerPulses() {
        fullCharge();

        ppd.applyAllocationEnergy(4.0, null);   // 2 holding + 2 overload

        assertTrue(ppd.isOverloaded());
        assertEquals(5, ppd.pulsesCharged());
    }

    /**
     * THE REGRESSION. An amount the rule cannot spend used to DESTROY the charge: a player
     * following E11.63 and allocating holding plus a full eight points of overload lost two
     * turns of arming to it. The holding cost was paid, so the charge survives untouched.
     */
    @Test
    public void anUnspendableTopUpDoesNotDestroyTheCharge() {
        fullCharge();

        ppd.applyAllocationEnergy(10.0, null);  // 2 holding + 8, more than an overload can hold

        assertTrue("the charge is still there", ppd.isArmed());
        assertEquals("and unchanged", 4, ppd.pulsesCharged());
        assertFalse(ppd.isOverloaded());
    }

    /**
     * E11.25's other route to the same place: an UNDERLOADED charge held for another turn with
     * more energy comes up to strength, and that is not an overload.
     */
    @Test
    public void aheldUnderloadedChargeCanBeBroughtUp() {
        ppd.arm(2);
        ppd.arm(2);
        assertEquals("fixture: two pulses", 2, ppd.pulsesCharged());

        ppd.applyAllocationEnergy(6.0, null);   // 2 holding + 4 arming

        assertEquals("a plain full charge now", 4, ppd.pulsesCharged());
        assertFalse("and not an overload — eight points is what a full charge costs",
                ppd.isOverloaded());
    }

    /** Failing to pay the holding cost loses the charge, as it always did (E1.24, E11.22). */
    @Test
    public void notPayingTheHoldingCostStillLosesTheCharge() {
        fullCharge();

        ppd.applyAllocationEnergy(1.0, null);

        assertFalse(ppd.isArmed());
    }

    /**
     * E11.62/E11.63: an overload cannot be held, so one still armed at the end of the turn is
     * ejected — "the PPD must be fired or ejected by the end of the turn".
     */
    @Test
    public void anUnfiredOverloadIsEjectedAtEndOfTurn() {
        ppd.arm(4);
        ppd.arm(8);
        assertTrue("fixture: an overload is armed", ppd.isArmed());

        ppd.cleanUp();

        assertFalse("it could not be held, so it is gone", ppd.isArmed());
    }

    /** A standard charge survives the turn boundary, since it CAN be held. */
    @Test
    public void anUnfiredStandardChargeSurvivesTheTurn() {
        fullCharge();

        ppd.cleanUp();

        assertTrue(ppd.isArmed());
        assertEquals(4, ppd.pulsesCharged());
    }

    /**
     * A device armed DIRECTLY — by a scenario's weapon status, or by a saved game on restore —
     * gets a full standard charge.
     *
     * <p>Without this it had no arming energy, so pulsesCharged() was zero and firing produced
     * a run of NO pulses: a weapon that reported itself armed, spent its charge and did
     * nothing. Found when a server test armed one this way and the run never started.
     */
    @Test
    public void adirectlyArmedDeviceHasAFullCharge() {
        ppd.setArmed(true);

        assertTrue(ppd.isArmed());
        assertEquals("a standard charge, not an empty one", 4, ppd.pulsesCharged());
    }

    /** And disarming clears the charge rather than leaving energy behind it. */
    @Test
    public void disarmingClearsTheCharge() {
        fullCharge();

        ppd.setArmed(false);

        assertEquals(0, ppd.pulsesCharged());
    }

    /** E11.6: overload is not built, so the device refuses to be put in that mode. */
    @Test
    public void overloadIsRefusedRatherThanFaked() {
        assertTrue(ppd.setStandard());
        assertFalse(ppd.setOverload());
    }

    // ------------------------------------------------------------------ E11.53

    /**
     * "If the number of electronic warfare shifts changes in favor of the target (D6.35), the
     * firing ship must roll again for a wave-lock."
     *
     * <p>A locked run that meets a worse shift stops being automatic: the lock is dropped and
     * that pulse has to roll like the first one did.
     */
    @Test
    public void aworseEwShiftForcesAnotherRoll() throws Exception {
        lockedWithPulsesLeft(10);
        assertEquals("fixture: locked at no shift", 0, ppd.lockedAtShift());

        ppd.setEcmShift(2);
        ppd.pulse(10);

        // Whether the re-roll succeeds is 2d6; what matters is that it was not automatic.
        assertTrue("the lock was re-rolled, not assumed",
                !ppd.isWaveLocked() || ppd.lockedAtShift() == 2);
    }

    /**
     * THE DISTINCTION THE RULE'S EXAMPLE IS ABOUT. Four net ECM rising to six is still two
     * shifts, so there is no new roll — it is the SHIFT that matters, not the points. Modelled
     * by comparing shifts, so a change that does not move the shift cannot reach this code.
     */
    @Test
    public void anUnchangedShiftCostsNothing() throws Exception {
        ppd.setEcmShift(1);
        lockedWithPulsesLeft(10);
        assertEquals(1, ppd.lockedAtShift());

        ppd.setEcmShift(1);

        assertEquals("still automatic at the same shift", 6, ppd.pulse(10));
        assertTrue(ppd.isWaveLocked());
    }

    /** And an improvement for the FIRING ship forces nothing either — only the target's favour. */
    @Test
    public void abetterShiftForTheFirerCostsNothing() throws Exception {
        ppd.setEcmShift(2);
        lockedWithPulsesLeft(10);
        assertEquals(2, ppd.lockedAtShift());

        ppd.setEcmShift(0);

        assertEquals("a friendlier shift does not disturb the lock", 6, ppd.pulse(10));
        assertTrue(ppd.isWaveLocked());
    }

    // ------------------------------------------------------------------ E11.54 vs E11.34

    /**
     * The two kinds of losing a lock, which must not be confused.
     *
     * <p>E11.34: conditions changed, so roll again on the pulses that remain — the run lives.
     * E11.54: "the wave-lock is broken and all subsequent pulses are lost. Do not roll for a
     * new wave-lock" — the run is over. A weapon that treated them alike would either never
     * stop or never recover.
     */
    @Test
    public void losingALockAndBreakingOneAreDifferentThings() throws Exception {
        lockedWithPulsesLeft(10);
        ppd.loseWaveLock();
        assertFalse(ppd.isWaveLocked());
        assertTrue("E11.34 leaves the run alive to roll again", ppd.isPulsing());

        lockedWithPulsesLeft(10);
        ppd.shutDown();
        assertFalse("E11.54 ends it outright", ppd.isPulsing());
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
