package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.TurnTracker;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.RomulanShips;

/**
 * Tests for mid-turn lock-on re-checks (D6.113) and cloak-triggered
 * lock-on changes (D6.111).
 */
public class LockOnTest {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        TurnTracker.reset();
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setActiveFireControl(true);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(12, 10));
        klingon.setFacing(4);

        game.getShips().add(fed);
        game.getShips().add(klingon);
    }

    // -------------------------------------------------------------------------
    // checkLockOnsForUnit — basic re-acquisition
    // -------------------------------------------------------------------------

    @Test
    public void checkLockOnsForUnit_shipWithHighSensor_acquiresLockOn() {
        // Fed CA sensor = 6 → automatic lock-on (roll of 1 always succeeds)
        fed.clearLockOns();
        assertFalse(fed.hasLockOn(klingon));

        game.checkLockOnsForUnit(klingon);

        assertTrue("Fed should re-acquire lock-on with sensor 6", fed.hasLockOn(klingon));
    }

    @Test
    public void checkLockOnsForUnit_noFireControl_noLockOn() {
        fed.setActiveFireControl(false);
        fed.clearLockOns();

        game.checkLockOnsForUnit(klingon);

        assertFalse("Ship without active fire control should not acquire lock-on",
                fed.hasLockOn(klingon));
    }

    @Test
    public void checkLockOnsForUnit_alreadyHasLockOn_keepsIt() {
        fed.addLockOn(klingon);

        game.checkLockOnsForUnit(klingon);

        assertTrue("Existing lock-on should be retained without re-roll", fed.hasLockOn(klingon));
    }

    @Test
    public void checkLockOnsForUnit_doesNotGrantLockOnToSelf() {
        game.checkLockOnsForUnit(klingon);
        assertFalse("Ship should never get lock-on to itself", klingon.hasLockOn(klingon));
    }

    @Test
    public void checkLockOnsForUnit_returnsLogLines() {
        fed.clearLockOns();
        List<String> log = game.checkLockOnsForUnit(klingon);
        assertFalse("Log should not be empty when re-acquisition is attempted", log.isEmpty());
    }

    @Test
    public void checkLockOnsForUnit_targetGetsNoLockOnToAttacker() {
        // The target being checked should not gain lock-on to others as a side effect
        klingon.clearLockOns();
        game.checkLockOnsForUnit(klingon);
        // klingon has no fire control set — should not gain lock-on to fed
        assertFalse(klingon.hasLockOn(fed));
    }

    // -------------------------------------------------------------------------
    // checkLockOnsForUnit — cloaked target
    // -------------------------------------------------------------------------

    @Test
    public void checkLockOnsForUnit_cloakedTarget_removesExistingLockOns() {
        Ship romulan = buildRomulan();
        fed.addLockOn(romulan);
        assertTrue(fed.hasLockOn(romulan));

        fullyCloak(romulan);
        List<String> log = game.checkLockOnsForUnit(romulan);

        assertFalse("Lock-on should be removed when target is fully cloaked",
                fed.hasLockOn(romulan));
        assertFalse("Log should mention lock-on loss", log.isEmpty());
    }

    @Test
    public void checkLockOnsForUnit_cloakedTarget_doesNotGrantNewLockOn() {
        Ship romulan = buildRomulan();
        fed.clearLockOns();
        fullyCloak(romulan);

        game.checkLockOnsForUnit(romulan);

        assertFalse("Cannot acquire lock-on to a fully cloaked ship",
                fed.hasLockOn(romulan));
    }

    @Test
    public void checkLockOnsForUnit_uncloakedTarget_canAcquireLockOn() {
        Ship romulan = buildRomulan();
        fed.clearLockOns();
        // Romulan not cloaked — should not break lock-on
        assertFalse(romulan.getCloakingDevice().breaksLockOn());

        game.checkLockOnsForUnit(romulan);

        assertTrue("Should acquire lock-on to a visible (uncloaked) ship",
                fed.hasLockOn(romulan));
    }

    @Test
    public void checkLockOnsForUnit_cloakedTarget_logMentionsLoss() {
        Ship romulan = buildRomulan();
        fed.addLockOn(romulan);
        fullyCloak(romulan);

        List<String> log = game.checkLockOnsForUnit(romulan);

        assertTrue("Log should mention cloaked lock-on loss",
                log.stream().anyMatch(l -> l.contains("cloaked")));
    }

    // -------------------------------------------------------------------------
    // Ship.hasLockOn / addLockOn / removeLockOn / clearLockOns
    // -------------------------------------------------------------------------

    @Test
    public void ship_hasNoLockOnByDefault() {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        assertFalse(s.hasLockOn(klingon));
    }

    @Test
    public void ship_addLockOn_thenHasIt() {
        fed.addLockOn(klingon);
        assertTrue(fed.hasLockOn(klingon));
    }

    @Test
    public void ship_removeLockOn_removesIt() {
        fed.addLockOn(klingon);
        fed.removeLockOn(klingon);
        assertFalse(fed.hasLockOn(klingon));
    }

    @Test
    public void ship_clearLockOns_removesAll() {
        Ship romulan = buildRomulan();
        fed.addLockOn(klingon);
        fed.addLockOn(romulan);
        fed.clearLockOns();
        assertFalse(fed.hasLockOn(klingon));
        assertFalse(fed.hasLockOn(romulan));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Ship buildRomulan() {
        Ship romulan = new Ship();
        romulan.init(RomulanShips.getRomKr());
        romulan.setName("RIS Shadowhawk");
        romulan.setLocation(new Location(14, 10));
        romulan.setFacing(4);
        game.getShips().add(romulan);
        return romulan;
    }

    private void fullyCloak(Ship ship) {
        com.sfb.systemgroups.CloakingDevice cd = ship.getCloakingDevice();
        assertNotNull("Ship must have a cloaking device", cd);
        cd.setCostPaid(true);
        cd.activate(0);
        for (int i = 1; i <= 10; i++) {
            cd.updateState(i);
        }
        assertTrue("Ship should be fully cloaked after activation + delay",
                cd.breaksLockOn());
    }
}
