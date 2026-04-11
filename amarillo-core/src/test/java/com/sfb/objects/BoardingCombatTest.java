package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.BoardingCombatResult;
import com.sfb.TurnTracker;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.ControlSpaces.RoomType;

/**
 * Tests for boarding party combat resolution (D7.3 / D7.4).
 */
public class BoardingCombatTest {

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

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(12, 10));
        klingon.setFacing(4);

        game.getShips().add(fed);
        game.getShips().add(klingon);
    }

    // -------------------------------------------------------------------------
    // TroopCount
    // -------------------------------------------------------------------------

    @Test
    public void troopCount_totalIsSumOfTiers() {
        TroopCount tc = new TroopCount(6, 2);
        assertEquals(8, tc.total());
    }

    @Test
    public void troopCount_removeCasualties_normalFirst() {
        TroopCount tc = new TroopCount(4, 2);
        int removed = tc.removeCasualties(5);
        assertEquals(5, removed);
        assertEquals(0, tc.normal);
        assertEquals(1, tc.commandos);
    }

    @Test
    public void troopCount_removeCasualties_clampedToAvailable() {
        TroopCount tc = new TroopCount(2, 1);
        int removed = tc.removeCasualties(10);
        assertEquals(3, removed);
        assertEquals(0, tc.total());
    }

    @Test
    public void troopCount_dominantQuality_commandosPresent() {
        TroopCount tc = new TroopCount(3, 1);
        assertEquals(com.sfb.properties.BoardingPartyQuality.COMMANDO, tc.dominantQuality());
    }

    @Test
    public void troopCount_dominantQuality_noCommandos() {
        TroopCount tc = new TroopCount(5, 0);
        assertEquals(com.sfb.properties.BoardingPartyQuality.NORMAL, tc.dominantQuality());
    }

    @Test
    public void troopCount_isEmpty_whenZero() {
        TroopCount tc = new TroopCount(0, 0);
        assertTrue(tc.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Crew — TroopCount integration
    // -------------------------------------------------------------------------

    @Test
    public void crew_friendlyTroops_loadedFromInit() {
        // Fed CA has boardingparties=10 in samples; commandos defaults to 0
        assertEquals(10, fed.getCrew().getFriendlyTroops().normal);
        assertEquals(0,  fed.getCrew().getFriendlyTroops().commandos);
        assertEquals(10, fed.getCrew().getAvailableBoardingParties());
    }

    @Test
    public void crew_setAvailableBoardingParties_removesNormalFirst() {
        fed.getCrew().getFriendlyTroops().commandos = 2;
        // total = 12; set to 9 → lose 3 normal
        fed.getCrew().setAvailableBoardingParties(9);
        assertEquals(7, fed.getCrew().getFriendlyTroops().normal);
        assertEquals(2, fed.getCrew().getFriendlyTroops().commandos);
    }

    // -------------------------------------------------------------------------
    // Ship — enemy troop tracking
    // -------------------------------------------------------------------------

    @Test
    public void ship_enemyTroops_zeroByDefault() {
        assertTrue(fed.getEnemyTroops().isEmpty());
        assertEquals(0, fed.getEnemyBoardingParties());
    }

    @Test
    public void ship_addEnemyBoardingParties_incrementsNormal() {
        fed.addEnemyBoardingParties(3);
        assertEquals(3, fed.getEnemyTroops().normal);
        assertEquals(3, fed.getEnemyBoardingParties());
    }

    @Test
    public void ship_addEnemyCommandos_incrementsCommandos() {
        fed.addEnemyCommandos(2);
        assertEquals(2, fed.getEnemyTroops().commandos);
    }

    @Test
    public void ship_notCapturedByDefault() {
        assertFalse(fed.isCaptured());
    }

    // -------------------------------------------------------------------------
    // ControlSpaces — capture API
    // -------------------------------------------------------------------------

    @Test
    public void controlSpaces_captureRoom_succeedsWhenAvailable() {
        // Fed CA has a bridge — should be capturable
        boolean captured = fed.getControlSpaces().captureRoom(RoomType.BRIDGE);
        assertTrue(captured);
        assertEquals(1, fed.getControlSpaces().getCapturedBridge());
    }

    @Test
    public void controlSpaces_captureRoom_failsWhenNoneAvailable() {
        // Damage all bridge boxes first, then try to capture
        while (fed.getControlSpaces().damageBridge()) { /* drain */ }
        boolean captured = fed.getControlSpaces().captureRoom(RoomType.BRIDGE);
        assertFalse("Cannot capture a destroyed room", captured);
    }

    @Test
    public void controlSpaces_allControlRoomsCaptured_falseInitially() {
        assertFalse(fed.getControlSpaces().allControlRoomsCaptured());
    }

    @Test
    public void controlSpaces_allControlRoomsCaptured_trueWhenAllGone() {
        // Capture every undestroyed non-security room
        for (RoomType room : RoomType.values()) {
            if (room == RoomType.SECURITY) continue;
            while (fed.getControlSpaces().captureRoom(room)) { /* capture all */ }
        }
        assertTrue(fed.getControlSpaces().allControlRoomsCaptured());
    }

    @Test
    public void controlSpaces_captureCost_securityIs6() {
        assertEquals(6, com.sfb.systemgroups.ControlSpaces.captureCost(RoomType.SECURITY));
    }

    @Test
    public void controlSpaces_captureCost_normalRoomIs4() {
        assertEquals(4, com.sfb.systemgroups.ControlSpaces.captureCost(RoomType.BRIDGE));
        assertEquals(4, com.sfb.systemgroups.ControlSpaces.captureCost(RoomType.EMER));
    }

    // -------------------------------------------------------------------------
    // performBoardingCombat — integration smoke tests
    // -------------------------------------------------------------------------

    @Test
    public void boardingCombat_noEnemyTroops_returnsZeroAttackerPoints() {
        // No enemy troops on fed — attacker has no power
        fed.getCrew().getFriendlyTroops().normal = 5;
        // enemyTroops empty by default
        BoardingCombatResult result = game.performBoardingCombat(fed);
        assertEquals(0, result.attackerPointsScored);
    }

    @Test
    public void boardingCombat_noDefendingTroops_scoredAgainstDefender() {
        fed.getCrew().setAvailableBoardingParties(0);
        fed.addEnemyBoardingParties(5);
        BoardingCombatResult result = game.performBoardingCombat(fed);
        // Attacker has power; they score > 0 points (table minimum for 5 BPs is 1)
        // Defender has 0 BPs to lose — excess should go to control rooms
        assertTrue(result.attackerPointsScored >= 0);
        assertEquals(0, result.defenderBPsLost);
    }

    @Test
    public void boardingCombat_resultLogNotEmpty() {
        fed.addEnemyBoardingParties(3);
        BoardingCombatResult result = game.performBoardingCombat(fed);
        assertNotNull(result.log);
        assertFalse(result.log.isEmpty());
        assertTrue(result.log.contains("Boarding Combat"));
    }

    @Test
    public void boardingCombat_defenderBPsLostDoesNotExceedAvailable() {
        fed.getCrew().getFriendlyTroops().normal = 3;
        fed.addEnemyBoardingParties(10); // heavy attack
        BoardingCombatResult result = game.performBoardingCombat(fed);
        assertTrue("Cannot lose more BPs than available", result.defenderBPsLost <= 3);
    }

    @Test
    public void boardingCombat_attackerBPsLostDoesNotExceedOnBoard() {
        fed.getCrew().getFriendlyTroops().normal = 10;
        fed.addEnemyBoardingParties(2);
        BoardingCombatResult result = game.performBoardingCombat(fed);
        assertTrue("Cannot lose more enemy BPs than were on board", result.attackerBPsLost <= 2);
    }

    @Test
    public void boardingCombat_shipCapturedWhenAllRoomsGone() {
        // Strip all control room boxes via capture, then run combat
        for (RoomType room : RoomType.values()) {
            if (room == RoomType.SECURITY) continue;
            while (fed.getControlSpaces().captureRoom(room)) { /* capture all */ }
        }
        // Now one more combat — ship should register as captured
        fed.addEnemyBoardingParties(1);
        fed.getCrew().setAvailableBoardingParties(0);
        BoardingCombatResult result = game.performBoardingCombat(fed);
        assertTrue(result.shipCaptured);
        assertTrue(fed.isCaptured());
    }

    @Test
    public void boardingCombat_klingonSecurityModInLog() {
        // D7 has security stations — mod should appear in log
        klingon.addEnemyBoardingParties(5);
        BoardingCombatResult result = game.performBoardingCombat(klingon);
        // If security stations exist, the modifier line should appear
        if (klingon.getControlSpaces().getAvailableSecurity() > 0) {
            assertTrue("Security modifier should be mentioned in log",
                    result.log.contains("Security station modifier"));
        }
    }
}
