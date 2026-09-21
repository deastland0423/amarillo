package com.sfb.objects;

import com.sfb.objects.shuttles.*;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.ShuttleBay;

/**
 * Tests for SuicideShuttle arming mechanics (unit) and
 * Game.launchSuicideShuttle()
 * preconditions (integration).
 */
public class SuicideShuttleTest {

    // -------------------------------------------------------------------------
    // Unit tests — SuicideShuttle arming
    // -------------------------------------------------------------------------

    private SuicideShuttle shuttle;

    @Before
    public void setUp() {
        shuttle = new SuicideShuttle(new AdminShuttle());
        shuttle.setName("SS-1");
    }

    @Test
    public void newShuttle_isNotArmed() {
        assertFalse(shuttle.isArmed());
        assertEquals(0, shuttle.getArmingTurnsComplete());
    }

    @Test
    public void newShuttle_warheadIsZero() {
        assertEquals(0, shuttle.getWarheadDamage());
    }

    @Test
    public void arm_acceptsOneToThreeEnergy() {
        assertTrue(shuttle.arm(1));
        assertTrue(shuttle.arm(2));
        assertTrue(shuttle.arm(3));
    }

    /**
     * The allocation form offers "same again" on the next arming turn, which needs the RATE,
     * not the running total. Divided by the turns the total gives the average, and that stops
     * being the rate the moment the player varies it - so 1 then 3 must read as 3, not 2.
     */
    @Test
    public void lastArmingEnergy_isTheRateNotTheAverage() {
        assertEquals("nothing paid yet", 0, shuttle.getLastArmingEnergy());

        shuttle.arm(1);
        assertEquals(1, shuttle.getLastArmingEnergy());

        shuttle.arm(3);
        assertEquals("the most recent turn, not the 2 an average would give",
                3, shuttle.getLastArmingEnergy());
        assertEquals("and the total is still the total", 4, shuttle.getTotalEnergy());
    }

    /** A refused arming turn charges nothing, so it must not move the remembered rate. */
    @Test
    public void lastArmingEnergy_ignoresARefusedArmingTurn() {
        shuttle.arm(2);
        assertFalse(shuttle.arm(0));
        assertFalse(shuttle.arm(4));

        assertEquals(2, shuttle.getLastArmingEnergy());
    }

    // -------------------------------------------------------------------------
    // Upkeep at end of turn (owner's ruling): no holding energy is owed until the shuttle
    // is fully armed. Three turns of 1-3 energy, then 1 a turn to hold. A turn that pays
    // nothing mid-arming loses ALL the arming.
    //
    // None of this was tested before, which is how the code came to charge twice: cleanUp
    // reverted anything part-armed that had not paid a HOLD, and arming energy did not count.
    // -------------------------------------------------------------------------

    /** Paying this turn's arming energy is all a part-armed shuttle owes. */
    @Test
    public void partArmed_armingEnergyIsTheUpkeep() {
        shuttle.arm(3);

        assertTrue("arming energy paid for this turn", shuttle.isUpkeepPaid());
        assertFalse("and no hold is owed yet", shuttle.owesHold());
    }

    /** A part-armed shuttle that is paid nothing has met nothing. */
    @Test
    public void partArmed_payingNothing_leavesUpkeepUnmet() {
        shuttle.arm(2);
        shuttle.resetUpkeep();            // the turn rolls over

        assertFalse(shuttle.isUpkeepPaid());
        assertTrue("still part-armed, so still at risk", shuttle.isArmed());
    }

    /** Fully armed, the 1-point hold is what it owes - arming can pay no more. */
    @Test
    public void fullyArmed_owesTheHold() {
        shuttle.arm(3);
        shuttle.arm(3);
        shuttle.arm(3);
        shuttle.resetUpkeep();

        assertTrue(shuttle.owesHold());
        assertFalse("a fourth arming turn is refused", shuttle.arm(3));
        assertFalse("so it cannot pay its way by arming", shuttle.isUpkeepPaid());

        shuttle.payHold();
        assertTrue(shuttle.isUpkeepPaid());
        assertEquals("3+3+3 = 9 energy, an 18-point warhead", 18, shuttle.getWarheadDamage());
    }

    @Test
    public void arm_rejectsZeroEnergy() {
        assertFalse(shuttle.arm(0));
        assertEquals(0, shuttle.getArmingTurnsComplete());
    }

    @Test
    public void arm_rejectsFourOrMoreEnergy() {
        assertFalse(shuttle.arm(4));
        assertEquals(0, shuttle.getArmingTurnsComplete());
    }

    @Test
    public void arm_threeTimesArmsShuttle() {
        shuttle.arm(1);
        shuttle.arm(2);
        shuttle.arm(3);
        assertTrue(shuttle.isArmed());
        assertEquals(3, shuttle.getArmingTurnsComplete());
    }

    @Test
    public void arm_twoTimesNotYetArmed() {
        shuttle.arm(2);
        shuttle.arm(2);
        assertFalse(shuttle.isFullyArmed());
        assertEquals(2, shuttle.getArmingTurnsComplete());
    }

    @Test
    public void arm_rejectsWhenAlreadyArmed() {
        shuttle.arm(1);
        shuttle.arm(1);
        shuttle.arm(1);
        assertTrue(shuttle.isArmed());
        assertFalse(shuttle.arm(1)); // already armed
        assertEquals(3, shuttle.getArmingTurnsComplete());
    }

    @Test
    public void warheadDamage_isTwiceTotalEnergy() {
        shuttle.arm(2); // 2
        shuttle.arm(3); // 5
        shuttle.arm(1); // 6
        assertEquals(12, shuttle.getWarheadDamage()); // 6 * 2
    }

    @Test
    public void warheadDamage_maxAt9Energy() {
        shuttle.arm(3);
        shuttle.arm(3);
        shuttle.arm(3);
        assertEquals(18, shuttle.getWarheadDamage()); // max: 9 * 2
    }

    @Test
    public void impact_returnsWarheadDamage() {
        shuttle.arm(2);
        shuttle.arm(2);
        shuttle.arm(2);
        assertEquals(shuttle.getWarheadDamage(), shuttle.impact());
    }

    @Test
    public void seekerType_isShuttle() {
        assertEquals(Seeker.SeekerType.SHUTTLE, shuttle.getSeekerType());
    }

    @Test
    public void isSelfGuiding_false() {
        assertFalse(shuttle.isSelfGuiding());
    }

    @Test
    public void endurance_isEffectivelyUnlimited() {
        assertEquals(Integer.MAX_VALUE, shuttle.getEndurance());
    }

    // -------------------------------------------------------------------------
    // Integration tests — Game.launchSuicideShuttle()
    // -------------------------------------------------------------------------

    private Game game;
    private Ship launcher;
    private Ship target;
    private SuicideShuttle armedShuttle;
    private ShuttleBay bay;

    @Before
    public void setUpGame() {
        game = new Game();

        launcher = new Ship();
        launcher.init(FederationShips.getFedCa());
        launcher.setName("Enterprise");
        launcher.setLocation(new Location(10, 10));
        launcher.setFacing(1);

        target = new Ship();
        target.init(KlingonShips.getD7());
        target.setName("D7");
        target.setLocation(new Location(10, 12));
        target.setFacing(13);

        game.getShips().add(launcher);
        game.getShips().add(target);

        // Build an armed suicide shuttle in a bay
        armedShuttle = new SuicideShuttle(new AdminShuttle());
        armedShuttle.setName("SS-1");
        armedShuttle.arm(3);
        armedShuttle.arm(3);
        armedShuttle.arm(3);
        assertTrue(armedShuttle.isArmed());

        bay = launcher.getShuttles().getBays().isEmpty()
                ? null
                : launcher.getShuttles().getBays().get(0);

        // If the ship has no bays (shouldn't happen for FedCa), create one
        if (bay == null) {
            fail("FedCa has no shuttle bays — check ship definition");
        }
        // Through the bay, not through getInventory(): that hands back a fresh copy, so
        // clear()/add() on it changed nothing and launch_removesShuttleFromBay was asserting
        // the absence of a shuttle that had never been there.
        bay.replaceShuttle(bay.getInventory().get(0), armedShuttle);

        // Lock on to target (required by rules)
        launcher.addLockOn(target);

        // Advance to ACTIVITY phase (MOVEMENT → ACTIVITY)
        game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    @Test
    public void launch_succeedsWhenAllConditionsMet() {
        ActionResult result = game.launchSuicideShuttle(launcher, bay, armedShuttle, target, 1, 6);
        assertTrue(result.getMessage(), result.isSuccess());
    }

    @Test
    public void launch_addsSuicideShuttleToSeekers() {
        game.launchSuicideShuttle(launcher, bay, armedShuttle, target, 1, 6);
        assertTrue(game.getSeekers().contains(armedShuttle));
    }

    @Test
    public void launch_removesShuttleFromBay() {
        game.launchSuicideShuttle(launcher, bay, armedShuttle, target, 1, 6);
        assertFalse(bay.getInventory().contains(armedShuttle));
    }

    @Test
    public void launch_setsTargetAndController() {
        game.launchSuicideShuttle(launcher, bay, armedShuttle, target, 1, 6);
        assertEquals(target, armedShuttle.getTarget());
        assertEquals(launcher, armedShuttle.getController());
    }

    @Test
    public void launch_failsWhenNotActivityPhase() {
        // Advance past ACTIVITY to DIRECT_FIRE
        game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());

        ActionResult result = game.launchSuicideShuttle(launcher, bay, armedShuttle, target, 1, 6);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Activity phase"));
    }

    @Test
    public void launch_failsWhenNotArmed() {
        SuicideShuttle unarmed = new SuicideShuttle(new AdminShuttle());
        unarmed.setName("SS-unarmed");
        unarmed.arm(1); // only 1 of 3 turns done
        bay.getInventory().add(unarmed);

        ActionResult result = game.launchSuicideShuttle(launcher, bay, unarmed, target, 1, 6);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not fully armed"));
    }

    @Test
    public void launch_failsWithoutLockOn() {
        launcher.removeLockOn(target);

        ActionResult result = game.launchSuicideShuttle(launcher, bay, armedShuttle, target, 1, 6);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("lock-on"));
    }

    @Test
    public void launch_logIncludesWarheadDamage() {
        ActionResult result = game.launchSuicideShuttle(launcher, bay, armedShuttle, target, 1, 6);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains(String.valueOf(armedShuttle.getWarheadDamage())));
    }

    // -------------------------------------------------------------------------
    // End of turn: what actually happens to the shuttle (Shuttles.cleanUp)
    // -------------------------------------------------------------------------

    private SuicideShuttle putInBay(int... armingTurns) {
        SuicideShuttle ss = new SuicideShuttle(new AdminShuttle());
        ss.setName("SS-upkeep");
        for (int energy : armingTurns)
            ss.arm(energy);
        // Through the bay's own API: getInventory() hands back a copy, so adding to that
        // list changes nothing - which is exactly the bug these tests found in cleanUp.
        bay.replaceShuttle(bay.getInventory().get(0), ss);
        return ss;
    }

    private Shuttle inBay() {
        return bay.getInventory().get(0);
    }

    /**
     * The bug this ruling settled: a part-armed shuttle paid its arming energy and was
     * reverted anyway, because cleanUp wanted a HOLD that is not owed until full arming.
     */
    @Test
    public void partArmed_paidItsArmingEnergy_survivesTheTurn() {
        putInBay(3);                       // one arming turn paid, this turn

        launcher.getShuttles().cleanUp();

        assertTrue("still a suicide shuttle", inBay() instanceof SuicideShuttle);
        assertEquals(1, ((SuicideShuttle) inBay()).getArmingTurnsComplete());
    }

    /** And a turn that pays nothing loses all of it, not just that turn's worth. */
    @Test
    public void partArmed_paidNothing_losesAllTheArming() {
        SuicideShuttle ss = putInBay(3, 3);   // two turns in
        ss.resetUpkeep();                     // and then a turn where nothing was paid

        launcher.getShuttles().cleanUp();

        assertFalse("reverted to a plain admin shuttle", inBay() instanceof SuicideShuttle);
        assertTrue(inBay() instanceof AdminShuttle);
        assertEquals("the name survives the reversion", "SS-upkeep", inBay().getName());
    }

    /** Fully armed, the hold is what keeps it. */
    @Test
    public void fullyArmed_holdPaid_survivesTheTurn() {
        SuicideShuttle ss = putInBay(3, 3, 3);
        ss.resetUpkeep();
        ss.payHold();

        launcher.getShuttles().cleanUp();

        assertTrue(inBay() instanceof SuicideShuttle);
        assertEquals(18, ((SuicideShuttle) inBay()).getWarheadDamage());
    }

    @Test
    public void fullyArmed_holdUnpaid_isLost() {
        SuicideShuttle ss = putInBay(3, 3, 3);
        ss.resetUpkeep();

        launcher.getShuttles().cleanUp();

        assertFalse(inBay() instanceof SuicideShuttle);
    }
}
