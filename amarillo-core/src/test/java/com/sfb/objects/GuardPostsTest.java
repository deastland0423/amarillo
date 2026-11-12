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

    // -------------------------------------------------------------------------
    // Internal-damage casualties (D7.21)
    // -------------------------------------------------------------------------

    /**
     * (D7.21) "The BP to be lost is selected by the owning player and can be any of his own BPs
     * (including guards)." We do not model that choice, so the pick is the one the owner would
     * plausibly make: the guard doing the least good goes first, and a pool post is worth less
     * than a post on a specific system because it only ever intercepts a raid with probability
     * guards/boxes (D7.8374).
     */
    @Test
    public void d7_21_takesAPoolGuardBeforeAWeaponGuard() {
        assertNull(fed.getGuardPosts().assign(weaponTarget(0), BoardingPartyQuality.NORMAL));
        assertNull(fed.getGuardPosts().assign(transporters(), BoardingPartyQuality.NORMAL));
        assertEquals("fixture: two guards posted", 2, fed.getGuardPosts().totalPosted());

        String log = fed.getGuardPosts().killOneGuardAsCasualty();

        assertNotNull("a guard is a valid casualty (D7.21)", log);
        assertTrue(log, log.contains("TRANSPORTERS"));
        assertFalse("the pool post is the cheapest party on the ship to lose",
                fed.getGuardPosts().isGuarded(SystemTarget.Type.TRANSPORTERS));
        assertTrue("so the weapon keeps its guard",
                fed.getGuardPosts().isGuarded(fed.getWeapons().fetchAllWeapons().get(0)));
        assertEquals("and the casualty is DESTROYED, not released to the roster", 1,
                fed.getGuardPosts().totalPosted());
    }

    /** (D7.8374) Among pool posts, the one spread over the most boxes is doing the least good. */
    @Test
    public void d7_21_amongPoolGuardsTheWorstOddsGoFirst() {
        GuardPosts posts = fed.getGuardPosts();
        assertNull(posts.assign(transporters(), BoardingPartyQuality.NORMAL));
        assertNull(posts.assign(new SystemTarget(SystemTarget.Type.FHULL, "F Hull"),
                BoardingPartyQuality.NORMAL));

        int transporterBoxes = posts.poolBoxCount(SystemTarget.Type.TRANSPORTERS);
        int hullBoxes = posts.poolBoxCount(SystemTarget.Type.FHULL);
        assertTrue("fixture: the hull must be the wider pool or this compares nothing - "
                + hullBoxes + " hull vs " + transporterBoxes + " transporters",
                hullBoxes > transporterBoxes);

        String log = posts.killOneGuardAsCasualty();

        assertTrue(log, log.contains("FHULL"));
        assertTrue("the narrower pool, where one guard covers more of the target space, keeps"
                + " its guard", posts.isGuarded(SystemTarget.Type.TRANSPORTERS));
    }

    /**
     * (D7.833) "no more than one boarding party ... to guard one object, area, or group of
     * people" - and the object is usually the scenario. Giving that guard up to save a marine
     * is the one trade nobody would make, so an objective post is taken LAST.
     */
    @Test
    public void d7_21_takesAnObjectiveGuardLastOfAll() {
        GuardPosts posts = fed.getGuardPosts();
        assertNull(posts.assign(SystemTarget.forObjective("Prototype Core", null),
                BoardingPartyQuality.NORMAL));
        assertNull(posts.assign(weaponTarget(0), BoardingPartyQuality.NORMAL));

        String first = posts.killOneGuardAsCasualty();
        assertNotNull(first);
        assertFalse("the weapon guard goes before the objective guard: " + first,
                first.contains("Prototype Core"));
        assertEquals("the objective is still guarded",
                java.util.Set.of("Prototype Core"), posts.postedObjectives());

        // Only when there is nothing else left does the object lose its guard.
        String last = posts.killOneGuardAsCasualty();
        assertNotNull(last);
        assertTrue(last, last.contains("Prototype Core"));
        assertTrue(posts.postedObjectives().isEmpty());
        assertEquals(0, posts.totalPosted());
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
