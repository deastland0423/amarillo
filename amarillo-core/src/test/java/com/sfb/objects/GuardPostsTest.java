package com.sfb.objects;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.properties.BoardingPartyQuality;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Guard post mechanics (D7.83x): exact interception for weapons/beams,
 * probabilistic interception for pools, D7.832 casualties, D7.8375 group
 * rules, capture release (D7.834), and the EA-phase gate on posting.
 */
public class GuardPostsTest {

    private Ship fed;

    @Before
    public void setUp() {
        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
    }

    private SystemTarget weaponTarget(int i) {
        return new SystemTarget(fed.getWeapons().fetchAllWeapons().get(i));
    }

    private SystemTarget transporters() {
        return new SystemTarget(SystemTarget.Type.TRANSPORTERS, "Transporters");
    }

    // -------------------------------------------------------------------------
    // Interception (D7.831 trigger)
    // -------------------------------------------------------------------------

    @Test
    public void weaponGuard_interceptsOnlyThatWeapon() {
        assertNull(fed.getGuardPosts().assign(weaponTarget(0), BoardingPartyQuality.NORMAL));

        assertNotNull("Guarded weapon intercepts",
                fed.getGuardPosts().intercept(weaponTarget(0)));
        assertNull("A different weapon of the same type is a separate box (D7.8374)",
                fed.getGuardPosts().intercept(weaponTarget(1)));
    }

    @Test
    public void beamGuard_interceptsOnlyThatBeam() {
        SystemTarget beam2 = new SystemTarget(SystemTarget.Type.TRACTOR, 2, "Tractor #2");
        SystemTarget beam1 = new SystemTarget(SystemTarget.Type.TRACTOR, 1, "Tractor #1");
        assertNull(fed.getGuardPosts().assign(beam2, BoardingPartyQuality.NORMAL));

        assertNotNull(fed.getGuardPosts().intercept(beam2));
        assertNull(fed.getGuardPosts().intercept(beam1));
    }

    @Test
    public void poolGuards_fullCoverage_alwaysIntercepts() {
        int boxes = fed.getTransporters().getAvailableTrans();
        for (int i = 0; i < boxes; i++)
            assertNull(fed.getGuardPosts().assign(transporters(), BoardingPartyQuality.NORMAL));

        for (int i = 0; i < 20; i++)
            assertNotNull("guards == boxes must always intercept",
                    fed.getGuardPosts().intercept(transporters()));
    }

    @Test
    public void poolGuards_none_neverIntercepts() {
        for (int i = 0; i < 20; i++)
            assertNull(fed.getGuardPosts().intercept(transporters()));
    }

    // -------------------------------------------------------------------------
    // D7.832 — guarded box destroyed by the raid
    // -------------------------------------------------------------------------

    @Test
    public void guardedWeaponDestroyedByRaid_guardDiesOrIsReleased() {
        int rosterBefore = fed.getCrew().getAvailableBoardingParties();
        fed.getGuardPosts().assign(weaponTarget(0), BoardingPartyQuality.NORMAL);
        GuardPosts.Interception guard = fed.getGuardPosts().intercept(weaponTarget(0));
        assertNotNull(guard);

        fed.getWeapons().fetchAllWeapons().get(0).damage();
        String outcome = fed.getGuardPosts().onGuardedBoxDestroyedByRaid(guard);

        assertEquals("Post is gone either way", 0, fed.getGuardPosts().totalPosted());
        int rosterAfter = fed.getCrew().getAvailableBoardingParties();
        if (outcome.contains("killed")) {
            assertEquals("Dead guard is a permanent loss", rosterBefore - 1, rosterAfter);
        } else {
            assertTrue(outcome.contains("released"));
            assertEquals("Survivor returns to the roster", rosterBefore, rosterAfter);
        }
    }

    @Test
    public void sensorGuard_neverKilled() {
        int rosterBefore = fed.getCrew().getAvailableBoardingParties();
        SystemTarget sensors = new SystemTarget(SystemTarget.Type.SENSORS, "Sensors");
        fed.getGuardPosts().assign(sensors, BoardingPartyQuality.NORMAL);
        GuardPosts.Interception guard = fed.getGuardPosts().intercept(sensors);

        String outcome = fed.getGuardPosts().onGuardedBoxDestroyedByRaid(guard);

        assertTrue("D7.823: " + outcome, outcome.contains("unharmed"));
        assertTrue("Still posted", fed.getGuardPosts().isGuarded(SystemTarget.Type.SENSORS));
        assertEquals(rosterBefore - 1, fed.getCrew().getAvailableBoardingParties());
    }

    // -------------------------------------------------------------------------
    // Combat-damage reconcile (D7.832 / D7.8375)
    // -------------------------------------------------------------------------

    @Test
    public void reconcile_guardedWeaponDestroyedByCombat_rollsCasualty() {
        int rosterBefore = fed.getCrew().getAvailableBoardingParties();
        Weapon w = fed.getWeapons().fetchAllWeapons().get(0);
        fed.getGuardPosts().assign(new SystemTarget(w), BoardingPartyQuality.NORMAL);

        w.damage(); // combat damage, not a raid
        java.util.List<String> log = fed.getGuardPosts().reconcileAfterDamage();

        assertEquals(1, log.size());
        assertEquals(0, fed.getGuardPosts().totalPosted());
        int rosterAfter = fed.getCrew().getAvailableBoardingParties();
        assertTrue("killed (-1) or released (0): " + log.get(0),
                rosterAfter == rosterBefore - 1 || rosterAfter == rosterBefore);
    }

    @Test
    public void reconcile_engineGroupPartiallyDamaged_guardUnaffected() {
        SystemTarget lwarp = new SystemTarget(SystemTarget.Type.WARP_L, "Left Warp Engine");
        assertNull(fed.getGuardPosts().assign(lwarp, BoardingPartyQuality.NORMAL));

        fed.getPowerSystems().damageLWarp(); // one box of many
        java.util.List<String> log = fed.getGuardPosts().reconcileAfterDamage();

        assertTrue("No casualty roll while the group survives (D7.8375)", log.isEmpty());
        assertTrue(fed.getGuardPosts().isGuarded(SystemTarget.Type.WARP_L));
    }

    @Test
    public void reconcile_engineGroupWipedOut_rollsCasualty() {
        SystemTarget lwarp = new SystemTarget(SystemTarget.Type.WARP_L, "Left Warp Engine");
        fed.getGuardPosts().assign(lwarp, BoardingPartyQuality.NORMAL);

        while (fed.getPowerSystems().getAvailableLWarp() > 0)
            fed.getPowerSystems().damageLWarp();
        java.util.List<String> log = fed.getGuardPosts().reconcileAfterDamage();

        assertEquals("Entire group destroyed → D7.832 roll", 1, log.size());
        assertFalse(fed.getGuardPosts().isGuarded(SystemTarget.Type.WARP_L));
    }

    // -------------------------------------------------------------------------
    // Capture (D7.834) and the EA gate
    // -------------------------------------------------------------------------

    @Test
    public void releaseAll_returnsEveryGuardToTheRoster() {
        int rosterBefore = fed.getCrew().getAvailableBoardingParties();
        fed.getGuardPosts().assign(weaponTarget(0), BoardingPartyQuality.NORMAL);
        fed.getGuardPosts().assign(new SystemTarget(SystemTarget.Type.TRACTOR, 1, "Tractor #1"),
                BoardingPartyQuality.NORMAL);
        fed.getGuardPosts().assign(transporters(), BoardingPartyQuality.NORMAL);
        assertEquals(rosterBefore - 3, fed.getCrew().getAvailableBoardingParties());

        int released = fed.getGuardPosts().releaseAll();

        assertEquals(3, released);
        assertEquals(0, fed.getGuardPosts().totalPosted());
        assertEquals("Guards revert to boarding parties on capture (D7.834)",
                rosterBefore, fed.getCrew().getAvailableBoardingParties());
    }

    @Test
    public void gameApi_postingGatedToEnergyAllocation() {
        Game game = new Game();
        Ship klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(30, 20));
        fed.setLocation(new Location(10, 10));
        game.getShips().add(fed);
        game.getShips().add(klingon);

        // Before startTurn there is no allocation window
        assertFalse(game.assignGuard(fed, "SENSORS", false).isSuccess());

        game.startTurn();
        ActionResult during = game.assignGuard(fed, "SENSORS", false);
        assertTrue(during.getMessage(), during.isSuccess());

        // Close the allocation window — posting must now be refused
        game.submitAllocation(fed, makeAllocation(fed));
        game.submitAllocation(klingon, makeAllocation(klingon));
        ActionResult after = game.assignGuard(fed, "SCANNERS", false);
        assertFalse("D7.83: assignments only at the start of the turn", after.isSuccess());
        assertTrue(after.getMessage().contains("D7.83"));
    }

    private Energy makeAllocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(0.0);
        return e;
    }
}
