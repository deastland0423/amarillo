package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.Game.ImpulsePhase;
import com.sfb.properties.BoardingPartyQuality;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Crew.CrewQuality;

/**
 * Tests for Hit-and-Run raid resolution (D7.8):
 * - precondition validation
 * - boarding party quality on SystemTarget
 * - guard assignment API on Ship (D7.83)
 * - crew quality on Crew (D7.73)
 * - integration smoke tests (raid runs without error)
 */
public class HitAndRunTest {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(11, 10)); // adjacent — range 1
        klingon.setFacing(4);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        // This test drives shields/transporters directly without startTurn(),
        // so inject the game's clock by hand (startTurn would normally do this)
        fed.attachClock(game.getClock());
        klingon.attachClock(game.getClock());

        // Advance to ACTIVITY phase (MOVEMENT → ACTIVITY)
        game.advancePhase();
        assertEquals(ImpulsePhase.ACTIVITY, game.getCurrentPhase());

        // Give the Fed ship transporter energy for use
        fed.getTransporters().bankEnergy(5.0);

        // Establish lock-on so transporter precondition passes
        fed.addLockOn(klingon);

        // Lower klingon's shield facing the Fed ship so transporters can beam through
        int shieldNum = game.getShieldNumber(fed, klingon);
        klingon.getShields().lowerShield(shieldNum);
    }

    // -------------------------------------------------------------------------
    // Precondition tests
    // -------------------------------------------------------------------------

    @Test
    public void raid_failsWhenNotInActivityPhase() {
        // Move past ACTIVITY
        game.advancePhase(); // → DIRECT_FIRE
        List<SystemTarget> targets = oneTarget(SystemTarget.Type.SENSORS);
        ActionResult result = game.performHitAndRun(fed, klingon, targets);
        assertFalse("Raid outside Activity phase should fail", result.isSuccess());
    }

    @Test
    public void raid_failsWhenOutOfRange() {
        klingon.setLocation(new Location(20, 20)); // far away
        List<SystemTarget> targets = oneTarget(SystemTarget.Type.SENSORS);
        ActionResult result = game.performHitAndRun(fed, klingon, targets);
        assertFalse("Raid at range > 5 should fail", result.isSuccess());
    }

    @Test
    public void raid_failsWithNoLockOn() {
        fed.clearLockOns();
        List<SystemTarget> targets = oneTarget(SystemTarget.Type.SENSORS);
        ActionResult result = game.performHitAndRun(fed, klingon, targets);
        assertFalse("Raid without lock-on should fail", result.isSuccess());
    }

    @Test
    public void raid_failsWithNoBoardingParties() {
        fed.getCrew().setAvailableBoardingParties(0);
        List<SystemTarget> targets = oneTarget(SystemTarget.Type.SENSORS);
        ActionResult result = game.performHitAndRun(fed, klingon, targets);
        assertFalse("Raid with no boarding parties should fail", result.isSuccess());
    }

    @Test
    public void raid_failsWithNoTransporterEnergy() {
        // Reset energy that was banked in setUp
        Ship freshFed = new Ship();
        freshFed.init(FederationShips.getFedCa());
        freshFed.setName("NCC-1701-B");
        freshFed.setLocation(new Location(10, 10));
        freshFed.setFacing(1);
        freshFed.addLockOn(klingon);
        // no bankEnergy call → zero uses available
        game.getShips().add(freshFed);

        List<SystemTarget> targets = oneTarget(SystemTarget.Type.SENSORS);
        ActionResult result = game.performHitAndRun(freshFed, klingon, targets);
        assertFalse("Raid with no transporter energy should fail", result.isSuccess());
    }

    @Test
    public void raid_failsWithEmptyTargetList() {
        ActionResult result = game.performHitAndRun(fed, klingon, new ArrayList<>());
        assertFalse("Raid with no target systems should fail", result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // SystemTarget quality
    // -------------------------------------------------------------------------

    @Test
    public void systemTarget_defaultQualityIsNormal() {
        SystemTarget st = new SystemTarget(SystemTarget.Type.SENSORS, "Sensors");
        assertEquals(BoardingPartyQuality.NORMAL, st.getAttackerQuality());
    }

    @Test
    public void systemTarget_explicitQualityIsPreserved() {
        SystemTarget st = new SystemTarget(SystemTarget.Type.SENSORS, "Sensors",
                BoardingPartyQuality.COMMANDO);
        assertEquals(BoardingPartyQuality.COMMANDO, st.getAttackerQuality());
    }

    @Test
    public void systemTarget_weaponDefaultQualityIsNormal() {
        com.sfb.weapons.Weapon w = klingon.getWeapons().fetchAllWeapons().get(0);
        SystemTarget st = new SystemTarget(w);
        assertEquals(BoardingPartyQuality.NORMAL, st.getAttackerQuality());
    }

    @Test
    public void systemTarget_weaponExplicitQuality() {
        com.sfb.weapons.Weapon w = klingon.getWeapons().fetchAllWeapons().get(0);
        SystemTarget st = new SystemTarget(w, BoardingPartyQuality.OUTSTANDING);
        assertEquals(BoardingPartyQuality.OUTSTANDING, st.getAttackerQuality());
    }

    // -------------------------------------------------------------------------
    // Guard API (D7.83) — guards are real boarding parties from the roster
    // -------------------------------------------------------------------------

    private SystemTarget sensors() {
        return new SystemTarget(SystemTarget.Type.SENSORS, "Sensors");
    }

    @Test
    public void ship_noGuardsByDefault() {
        assertFalse(klingon.getGuardPosts().isGuarded(SystemTarget.Type.SENSORS));
        assertEquals(0, klingon.getGuardPosts().totalPosted());
    }

    @Test
    public void ship_assignGuard_postsAndCostsABoardingParty() {
        int before = klingon.getCrew().getAvailableBoardingParties();
        assertNull(klingon.getGuardPosts().assign(sensors(), BoardingPartyQuality.NORMAL));
        assertTrue(klingon.getGuardPosts().isGuarded(SystemTarget.Type.SENSORS));
        assertEquals("Posted guard leaves the roster (D7.834)",
                before - 1, klingon.getCrew().getAvailableBoardingParties());
    }

    @Test
    public void ship_assignGuard_secondOnSameSystemRefused() {
        assertNull(klingon.getGuardPosts().assign(sensors(), BoardingPartyQuality.NORMAL));
        String err = klingon.getGuardPosts().assign(sensors(), BoardingPartyQuality.NORMAL);
        assertNotNull("One guard per system (D7.833)", err);
        assertTrue(err.contains("D7.833"));
    }

    @Test
    public void ship_assignGuard_onlyRealBpTiersAllowed() {
        assertNotNull("OUTSTANDING is a table column, not a roster tier",
                klingon.getGuardPosts().assign(sensors(), BoardingPartyQuality.OUTSTANDING));
    }

    @Test
    public void ship_removeGuard_returnsBpToRoster() {
        int before = klingon.getCrew().getAvailableBoardingParties();
        klingon.getGuardPosts().assign(sensors(), BoardingPartyQuality.NORMAL);
        assertNull(klingon.getGuardPosts().release(sensors()));
        assertFalse(klingon.getGuardPosts().isGuarded(SystemTarget.Type.SENSORS));
        assertEquals(before, klingon.getCrew().getAvailableBoardingParties());
    }

    @Test
    public void ship_guardsPersistAcrossTurnCleanup() {
        klingon.getGuardPosts().assign(sensors(), BoardingPartyQuality.NORMAL);
        klingon.cleanUp();
        assertTrue("Guards persist until re-tasked (D7.83)",
                klingon.getGuardPosts().isGuarded(SystemTarget.Type.SENSORS));
    }

    @Test
    public void ship_guardOnOneTypeDoesNotAffectOther() {
        klingon.getGuardPosts().assign(sensors(), BoardingPartyQuality.NORMAL);
        assertFalse(klingon.getGuardPosts().isGuarded(SystemTarget.Type.SCANNERS));
    }

    @Test
    public void ship_poolGuards_cappedAtBoxCount() {
        // D7 transporters: post one guard per box, then one more must fail
        SystemTarget pool = new SystemTarget(SystemTarget.Type.TRANSPORTERS, "Transporters");
        int boxes = klingon.getTransporters().getAvailableTrans();
        for (int i = 0; i < boxes; i++)
            assertNull("guard " + (i + 1), klingon.getGuardPosts().assign(pool, BoardingPartyQuality.NORMAL));
        assertNotNull("One guard per box (D7.833)",
                klingon.getGuardPosts().assign(pool, BoardingPartyQuality.NORMAL));
        assertEquals(boxes, klingon.getGuardPosts().poolGuardCount(SystemTarget.Type.TRANSPORTERS));
    }

    // -------------------------------------------------------------------------
    // Crew quality API (D7.73)
    // -------------------------------------------------------------------------

    @Test
    public void crew_defaultQualityIsNormal() {
        assertEquals(CrewQuality.NORMAL, fed.getCrew().getCrewQuality());
    }

    @Test
    public void crew_setQuality_outstanding() {
        fed.getCrew().setCrewQuality(CrewQuality.OUTSTANDING);
        assertEquals(CrewQuality.OUTSTANDING, fed.getCrew().getCrewQuality());
    }

    @Test
    public void crew_setQuality_poor() {
        klingon.getCrew().setCrewQuality(CrewQuality.POOR);
        assertEquals(CrewQuality.POOR, klingon.getCrew().getCrewQuality());
    }

    // -------------------------------------------------------------------------
    // Integration smoke tests (dice-dependent — just verify no crash + structure)
    // -------------------------------------------------------------------------

    @Test
    public void raid_unguarded_returnsSuccessResult() {
        List<SystemTarget> targets = oneTarget(SystemTarget.Type.SENSORS);
        ActionResult result = game.performHitAndRun(fed, klingon, targets);
        assertTrue("Unguarded raid in range with resources should succeed", result.isSuccess());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("Hit & Run Raid"));
    }

    @Test
    public void raid_guarded_doesNotThrow() {
        klingon.getGuardPosts().assign(sensors(), BoardingPartyQuality.NORMAL);
        List<SystemTarget> targets = oneTarget(SystemTarget.Type.SENSORS);
        ActionResult result = game.performHitAndRun(fed, klingon, targets);
        // Result may be success or failure depending on dice, but it must not throw
        assertNotNull(result);
        assertTrue(result.getMessage().contains("Guard present"));
    }

    @Test
    public void raid_commandoParty_doesNotThrow() {
        List<SystemTarget> targets = new ArrayList<>();
        targets.add(new SystemTarget(SystemTarget.Type.SENSORS, "Sensors",
                BoardingPartyQuality.COMMANDO));
        ActionResult result = game.performHitAndRun(fed, klingon, targets);
        assertTrue(result.isSuccess());
    }

    @Test
    public void raid_multipleParties_logMentionsAll() {
        // Fed CA has 10 boarding parties; send 2
        List<SystemTarget> targets = new ArrayList<>();
        targets.add(new SystemTarget(SystemTarget.Type.SENSORS, "Sensors"));
        targets.add(new SystemTarget(SystemTarget.Type.SCANNERS, "Scanners"));
        ActionResult result = game.performHitAndRun(fed, klingon, targets);
        assertTrue(result.isSuccess());
        assertTrue("Log should mention sensors", result.getMessage().contains("Sensors"));
        assertTrue("Log should mention scanners", result.getMessage().contains("Scanners"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<SystemTarget> oneTarget(SystemTarget.Type type) {
        List<SystemTarget> list = new ArrayList<>();
        list.add(new SystemTarget(type, type.name()));
        return list;
    }
}
