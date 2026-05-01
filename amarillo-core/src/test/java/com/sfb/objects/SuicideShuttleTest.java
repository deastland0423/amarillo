package com.sfb.objects;

import com.sfb.objects.shuttles.*;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.TurnTracker;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.ShuttleBay;

/**
 * Tests for SuicideShuttle arming mechanics (unit) and Game.launchSuicideShuttle()
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
        assertFalse(shuttle.isArmed());
        assertEquals(2, shuttle.getArmingTurnsComplete());
    }

    @Test
    public void arm_rejectsWhenAlreadyArmed() {
        shuttle.arm(1); shuttle.arm(1); shuttle.arm(1);
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
        shuttle.arm(3); shuttle.arm(3); shuttle.arm(3);
        assertEquals(18, shuttle.getWarheadDamage()); // max: 9 * 2
    }

    @Test
    public void impact_returnsWarheadDamage() {
        shuttle.arm(2); shuttle.arm(2); shuttle.arm(2);
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

    private Game  game;
    private Ship  launcher;
    private Ship  target;
    private SuicideShuttle armedShuttle;
    private ShuttleBay     bay;

    @Before
    public void setUpGame() {
        TurnTracker.reset();
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
        armedShuttle.arm(3); armedShuttle.arm(3); armedShuttle.arm(3);
        assertTrue(armedShuttle.isArmed());

        bay = launcher.getShuttles().getBays().isEmpty()
                ? null
                : launcher.getShuttles().getBays().get(0);

        // If the ship has no bays (shouldn't happen for FedCa), create one
        if (bay == null) {
            fail("FedCa has no shuttle bays — check ship definition");
        }
        bay.getInventory().clear();
        bay.getInventory().add(armedShuttle);

        // Lock on to target (required by rules)
        launcher.addLockOn(target);

        // Advance to ACTIVITY phase (MOVEMENT → ACTIVITY)
        game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    @Test
    public void launch_succeedsWhenAllConditionsMet() {
        ActionResult result = game.launchSuicideShuttle(launcher, bay, armedShuttle, target);
        assertTrue(result.getMessage(), result.isSuccess());
    }

    @Test
    public void launch_addsSuicideShuttleToSeekers() {
        game.launchSuicideShuttle(launcher, bay, armedShuttle, target);
        assertTrue(game.getSeekers().contains(armedShuttle));
    }

    @Test
    public void launch_removesShuttleFromBay() {
        game.launchSuicideShuttle(launcher, bay, armedShuttle, target);
        assertFalse(bay.getInventory().contains(armedShuttle));
    }

    @Test
    public void launch_setsTargetAndController() {
        game.launchSuicideShuttle(launcher, bay, armedShuttle, target);
        assertEquals(target,   armedShuttle.getTarget());
        assertEquals(launcher, armedShuttle.getController());
    }

    @Test
    public void launch_failsWhenNotActivityPhase() {
        // Advance past ACTIVITY to DIRECT_FIRE
        game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());

        ActionResult result = game.launchSuicideShuttle(launcher, bay, armedShuttle, target);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Activity phase"));
    }

    @Test
    public void launch_failsWhenNotArmed() {
        SuicideShuttle unarmed = new SuicideShuttle(new AdminShuttle());
        unarmed.setName("SS-unarmed");
        unarmed.arm(1); // only 1 of 3 turns done
        bay.getInventory().add(unarmed);

        ActionResult result = game.launchSuicideShuttle(launcher, bay, unarmed, target);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not fully armed"));
    }

    @Test
    public void launch_failsWithoutLockOn() {
        launcher.removeLockOn(target);

        ActionResult result = game.launchSuicideShuttle(launcher, bay, armedShuttle, target);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("lock-on"));
    }

    @Test
    public void launch_logIncludesWarheadDamage() {
        ActionResult result = game.launchSuicideShuttle(launcher, bay, armedShuttle, target);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains(String.valueOf(armedShuttle.getWarheadDamage())));
    }
}
