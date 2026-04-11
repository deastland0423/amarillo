package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game.HarGuardResult;
import com.sfb.Game.HarResult;
import com.sfb.objects.Ship;
import com.sfb.objects.TroopCount;
import com.sfb.properties.BoardingPartyQuality;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Crew.CrewQuality;

/**
 * Deterministic coverage for the internal boarding/H&R tables and
 * helpers that cannot be reliably hit through dice-dependent integration tests.
 *
 * Uses package-private access to Game's table lookup methods.
 */
public class BoardingTableTest {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        TurnTracker.reset();
        game    = new Game();
        fed     = new Ship();
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

    // =========================================================================
    // D7.421 table spot-checks
    // =========================================================================

    @Test
    public void d7421_roll1_group1_is0() {
        assertEquals(0, Game.D7421_TABLE[0][0]);
    }

    @Test
    public void d7421_roll1_group5_is1() {
        assertEquals(1, Game.D7421_TABLE[0][4]);
    }

    @Test
    public void d7421_roll1_group10_is2() {
        assertEquals(2, Game.D7421_TABLE[0][9]);
    }

    @Test
    public void d7421_roll3_group2_is1() {
        assertEquals(1, Game.D7421_TABLE[2][1]);
    }

    @Test
    public void d7421_roll4_group4_is2() {
        assertEquals(2, Game.D7421_TABLE[3][3]);
    }

    @Test
    public void d7421_roll6_group6_is4() {
        assertEquals(4, Game.D7421_TABLE[5][5]);
    }

    @Test
    public void d7421_roll6_group10_is6() {
        assertEquals(6, Game.D7421_TABLE[5][9]);
    }

    @Test
    public void d7421_roll5_group9_is5() {
        assertEquals(5, Game.D7421_TABLE[4][8]);
    }

    @Test
    public void d7421_tableHas6Rows() {
        assertEquals(6, Game.D7421_TABLE.length);
    }

    @Test
    public void d7421_eachRowHas10Columns() {
        for (int i = 0; i < 6; i++) {
            assertEquals("Row " + (i+1) + " should have 10 columns",
                    10, Game.D7421_TABLE[i].length);
        }
    }

    // =========================================================================
    // D7.81 H&R table — every roll for every quality column
    // =========================================================================

    // --- NORMAL column ---
    @Test public void har_normal_roll1_systemAndBPReturns() {
        assertEquals(HarResult.SYSTEM_BP_RETURNS, game.resolveHarTable(1, BoardingPartyQuality.NORMAL));
    }
    @Test public void har_normal_roll2_bothDestroyed() {
        assertEquals(HarResult.BOTH_DESTROYED,    game.resolveHarTable(2, BoardingPartyQuality.NORMAL));
    }
    @Test public void har_normal_roll3_bpDestroyed() {
        assertEquals(HarResult.BP_DESTROYED,      game.resolveHarTable(3, BoardingPartyQuality.NORMAL));
    }
    @Test public void har_normal_roll5_bpDestroyed() {
        assertEquals(HarResult.BP_DESTROYED,      game.resolveHarTable(5, BoardingPartyQuality.NORMAL));
    }
    @Test public void har_normal_roll6_bpReturns() {
        assertEquals(HarResult.BP_RETURNS,        game.resolveHarTable(6, BoardingPartyQuality.NORMAL));
    }

    // --- COMMANDO column ---
    @Test public void har_commando_roll1_systemAndBPReturns() {
        assertEquals(HarResult.SYSTEM_BP_RETURNS, game.resolveHarTable(1, BoardingPartyQuality.COMMANDO));
    }
    @Test public void har_commando_roll2_bothDestroyed() {
        assertEquals(HarResult.BOTH_DESTROYED,    game.resolveHarTable(2, BoardingPartyQuality.COMMANDO));
    }
    @Test public void har_commando_roll3_bothDestroyed() {
        assertEquals(HarResult.BOTH_DESTROYED,    game.resolveHarTable(3, BoardingPartyQuality.COMMANDO));
    }
    @Test public void har_commando_roll4_bpDestroyed() {
        assertEquals(HarResult.BP_DESTROYED,      game.resolveHarTable(4, BoardingPartyQuality.COMMANDO));
    }
    @Test public void har_commando_roll5_bpReturns() {
        assertEquals(HarResult.BP_RETURNS,        game.resolveHarTable(5, BoardingPartyQuality.COMMANDO));
    }
    @Test public void har_commando_roll6_bpReturns() {
        assertEquals(HarResult.BP_RETURNS,        game.resolveHarTable(6, BoardingPartyQuality.COMMANDO));
    }

    // --- OUTSTANDING column ---
    @Test public void har_outstanding_roll1_systemAndBPReturns() {
        assertEquals(HarResult.SYSTEM_BP_RETURNS, game.resolveHarTable(1, BoardingPartyQuality.OUTSTANDING));
    }
    @Test public void har_outstanding_roll2_systemAndBPReturns() {
        assertEquals(HarResult.SYSTEM_BP_RETURNS, game.resolveHarTable(2, BoardingPartyQuality.OUTSTANDING));
    }
    @Test public void har_outstanding_roll3_bothDestroyed() {
        assertEquals(HarResult.BOTH_DESTROYED,    game.resolveHarTable(3, BoardingPartyQuality.OUTSTANDING));
    }
    @Test public void har_outstanding_roll4_bpDestroyed() {
        assertEquals(HarResult.BP_DESTROYED,      game.resolveHarTable(4, BoardingPartyQuality.OUTSTANDING));
    }
    @Test public void har_outstanding_roll5_bpReturns() {
        assertEquals(HarResult.BP_RETURNS,        game.resolveHarTable(5, BoardingPartyQuality.OUTSTANDING));
    }
    @Test public void har_outstanding_roll6_bpReturns() {
        assertEquals(HarResult.BP_RETURNS,        game.resolveHarTable(6, BoardingPartyQuality.OUTSTANDING));
    }

    // --- POOR column ---
    @Test public void har_poor_roll1_bothDestroyed() {
        assertEquals(HarResult.BOTH_DESTROYED,    game.resolveHarTable(1, BoardingPartyQuality.POOR));
    }
    @Test public void har_poor_roll2_bpDestroyed() {
        assertEquals(HarResult.BP_DESTROYED,      game.resolveHarTable(2, BoardingPartyQuality.POOR));
    }
    @Test public void har_poor_roll4_bpDestroyed() {
        assertEquals(HarResult.BP_DESTROYED,      game.resolveHarTable(4, BoardingPartyQuality.POOR));
    }
    @Test public void har_poor_roll5_bpReturns() {
        assertEquals(HarResult.BP_RETURNS,        game.resolveHarTable(5, BoardingPartyQuality.POOR));
    }
    @Test public void har_poor_roll6_bpReturns() {
        assertEquals(HarResult.BP_RETURNS,        game.resolveHarTable(6, BoardingPartyQuality.POOR));
    }

    // =========================================================================
    // D7.831 Guard table — every roll for every quality column
    // =========================================================================

    // --- NORMAL guard ---
    @Test public void guard_normal_roll1_bpDestroyed() {
        assertEquals(HarGuardResult.BP_DESTROYED, game.resolveGuardTable(1, BoardingPartyQuality.NORMAL));
    }
    @Test public void guard_normal_roll3_bpDestroyed() {
        assertEquals(HarGuardResult.BP_DESTROYED, game.resolveGuardTable(3, BoardingPartyQuality.NORMAL));
    }
    @Test public void guard_normal_roll4_bpReturns() {
        assertEquals(HarGuardResult.BP_RETURNS,   game.resolveGuardTable(4, BoardingPartyQuality.NORMAL));
    }
    @Test public void guard_normal_roll5_bpReturns() {
        assertEquals(HarGuardResult.BP_RETURNS,   game.resolveGuardTable(5, BoardingPartyQuality.NORMAL));
    }
    @Test public void guard_normal_roll6_conductHR() {
        assertEquals(HarGuardResult.CONDUCT_HR,   game.resolveGuardTable(6, BoardingPartyQuality.NORMAL));
    }

    // --- COMMANDO guard ---
    @Test public void guard_commando_roll2_bpDestroyed() {
        assertEquals(HarGuardResult.BP_DESTROYED, game.resolveGuardTable(2, BoardingPartyQuality.COMMANDO));
    }
    @Test public void guard_commando_roll3_bpReturns() {
        assertEquals(HarGuardResult.BP_RETURNS,   game.resolveGuardTable(3, BoardingPartyQuality.COMMANDO));
    }
    @Test public void guard_commando_roll4_conductHR() {
        assertEquals(HarGuardResult.CONDUCT_HR,   game.resolveGuardTable(4, BoardingPartyQuality.COMMANDO));
    }

    // --- OUTSTANDING guard ---
    @Test public void guard_outstanding_roll2_bpDestroyed() {
        assertEquals(HarGuardResult.BP_DESTROYED, game.resolveGuardTable(2, BoardingPartyQuality.OUTSTANDING));
    }
    @Test public void guard_outstanding_roll3_bpReturns() {
        assertEquals(HarGuardResult.BP_RETURNS,   game.resolveGuardTable(3, BoardingPartyQuality.OUTSTANDING));
    }
    @Test public void guard_outstanding_roll4_bpReturns() {
        assertEquals(HarGuardResult.BP_RETURNS,   game.resolveGuardTable(4, BoardingPartyQuality.OUTSTANDING));
    }
    @Test public void guard_outstanding_roll5_conductHR() {
        assertEquals(HarGuardResult.CONDUCT_HR,   game.resolveGuardTable(5, BoardingPartyQuality.OUTSTANDING));
    }

    // --- POOR guard ---
    @Test public void guard_poor_roll1_bpDestroyed() {
        assertEquals(HarGuardResult.BP_DESTROYED, game.resolveGuardTable(1, BoardingPartyQuality.POOR));
    }
    @Test public void guard_poor_roll4_bpDestroyed() {
        assertEquals(HarGuardResult.BP_DESTROYED, game.resolveGuardTable(4, BoardingPartyQuality.POOR));
    }
    @Test public void guard_poor_roll5_bpReturns() {
        assertEquals(HarGuardResult.BP_RETURNS,   game.resolveGuardTable(5, BoardingPartyQuality.POOR));
    }
    @Test public void guard_poor_roll6_conductHR() {
        assertEquals(HarGuardResult.CONDUCT_HR,   game.resolveGuardTable(6, BoardingPartyQuality.POOR));
    }

    // =========================================================================
    // klingonSecurityMod
    // =========================================================================

    @Test
    public void securityMod_federationShip_isZero() {
        assertEquals(0, game.klingonSecurityMod(fed));
    }

    @Test
    public void securityMod_klingonNoStations_isZero() {
        // Damage all security stations
        while (klingon.getControlSpaces().damageSecurity()) { /* drain */ }
        assertEquals(0, game.klingonSecurityMod(klingon));
    }

    @Test
    public void securityMod_klingonOneStation_isOne() {
        // Damage all but one security station
        int stations = klingon.getControlSpaces().getAvailableSecurity();
        for (int i = 0; i < stations - 1; i++) klingon.getControlSpaces().damageSecurity();
        assertEquals(1, game.klingonSecurityMod(klingon));
    }

    @Test
    public void securityMod_klingonTwoOrMoreStations_isCappedAtTwo() {
        // D7 has security stations — if >= 2, mod should be exactly 2
        int stations = klingon.getControlSpaces().getAvailableSecurity();
        if (stations >= 2) {
            assertEquals(2, game.klingonSecurityMod(klingon));
        }
    }

    @Test
    public void securityMod_capturedStationsDoNotCount() {
        int stations = klingon.getControlSpaces().getAvailableSecurity();
        if (stations >= 1) {
            klingon.getControlSpaces().captureRoom(
                    com.sfb.systemgroups.ControlSpaces.RoomType.SECURITY);
            int expected = Math.min(2, stations - 1);
            assertEquals(expected, game.klingonSecurityMod(klingon));
        }
    }

    // =========================================================================
    // rollCasualtyPoints via performBoardingCombat — multi-group path (>10 BPs)
    // =========================================================================

    @Test
    public void boardingCombat_23AttackerBPs_usesThreeGroups() {
        // 23 = 10 + 10 + 3 groups; the log should contain "group 3"
        fed.addEnemyBoardingParties(23);
        fed.getCrew().setAvailableBoardingParties(0); // no defender resistance
        Game.BoardingCombatResult result = game.performBoardingCombat(fed);
        assertTrue("Log should mention group 3 for 23 BPs",
                result.log.contains("Attacker group 3"));
    }

    @Test
    public void boardingCombat_10AttackerBPs_usesSingleGroup() {
        fed.addEnemyBoardingParties(10);
        fed.getCrew().setAvailableBoardingParties(0);
        Game.BoardingCombatResult result = game.performBoardingCombat(fed);
        assertTrue(result.log.contains("Attacker group 1"));
        assertFalse("Should not have group 2 for exactly 10 BPs",
                result.log.contains("Attacker group 2"));
    }

    @Test
    public void boardingCombat_11AttackerBPs_usesTwoGroups() {
        fed.addEnemyBoardingParties(11);
        fed.getCrew().setAvailableBoardingParties(0);
        Game.BoardingCombatResult result = game.performBoardingCombat(fed);
        assertTrue(result.log.contains("Attacker group 2"));
        assertFalse(result.log.contains("Attacker group 3"));
    }

    // =========================================================================
    // Crew.setAvailableBoardingParties — gain (reinforce) path
    // =========================================================================

    @Test
    public void crew_setAvailableBoardingParties_gainIncreasesNormal() {
        fed.getCrew().getFriendlyTroops().normal = 3;
        fed.getCrew().setAvailableBoardingParties(5); // gain 2
        assertEquals(5, fed.getCrew().getFriendlyTroops().normal);
    }

    // =========================================================================
    // ControlSpaces.uncapturedRooms
    // =========================================================================

    @Test
    public void uncapturedRooms_allIntactInitially() {
        int rooms = fed.getControlSpaces().uncapturedRooms();
        assertTrue("Fed CA should have at least 1 uncaptured control room", rooms > 0);
    }

    @Test
    public void uncapturedRooms_decreasesAfterCapture() {
        int before = fed.getControlSpaces().uncapturedRooms();
        fed.getControlSpaces().captureRoom(
                com.sfb.systemgroups.ControlSpaces.RoomType.EMER);
        int after = fed.getControlSpaces().uncapturedRooms();
        assertEquals(before - 1, after);
    }

    @Test
    public void uncapturedRooms_notAffectedByDamageToOtherType() {
        int before = fed.getControlSpaces().uncapturedRooms();
        // Damage security (not counted in uncapturedRooms — no offensive potential)
        fed.getControlSpaces().damageSecurity();
        assertEquals(before, fed.getControlSpaces().uncapturedRooms());
    }
}
