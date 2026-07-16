package com.sfb;

import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.CloakingDevice.CloakState;
import com.sfb.systemgroups.Energy;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Lock-on retention against a cloaking ship (G13.33).
 *
 * P = Sensor − EW − RangeFactor + SpeedFactor − 4, roll ≤ P retains (G13.331);
 * EW is 0 until electronic warfare exists. Deterministic setups:
 *  - rom at speed 20 (SF +6), range 1 (RF 0), Fed sensor 6 → P = 8: always retained
 *  - rom at speed 0  (SF −2), range 1 (RF 0), Fed sensor 6 → P = 0: always lost
 * Plasma rolls its own retention at sensor 6 (G13.3343): P = 2 − RF + SF.
 */
public class CloakRetentionTest {

    private Game game;
    private Ship rom;
    private Ship fed;

    @Before
    public void setUp() {
        game = new Game();

        rom = new Ship();
        rom.init(RomulanShips.getRomKr());
        rom.setName("RIS Talon");
        rom.setLocation(new Location(10, 10));
        rom.setFacing(1);
        rom.setSpeedPreviousTurn(31);
        rom.setSpeedTwoTurnsAgo(31);

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(11, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        game.getShips().add(rom);
        game.getShips().add(fed);
        game.startTurn();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Energy makeAllocation(Ship ship, boolean cloakPaid, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warp);
        e.setCloakPaid(cloakPaid);
        return e;
    }

    private void submitAllocations(boolean romCloakPaid, double romWarp) {
        game.submitAllocation(rom, makeAllocation(rom, romCloakPaid, romWarp));
        game.submitAllocation(fed, makeAllocation(fed, false, 4.0));
    }

    private void cloakAndCompleteFadeOut() {
        for (int guard = 0; guard < 400
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        assertTrue(game.cloak(rom).isSuccess());
        for (int guard = 0; guard < 100
                && rom.getCloakingDevice().getState() != CloakState.FULLY_CLOAKED; guard++)
            game.advancePhase();
        assertEquals(CloakState.FULLY_CLOAKED, rom.getCloakingDevice().getState());
    }

    private void advanceToTurnEnd() {
        for (int guard = 0; guard < 600 && !game.isAwaitingAllocation(); guard++)
            game.advancePhase();
        assertTrue(game.isAwaitingAllocation());
    }

    // -------------------------------------------------------------------------
    // G13.331 adjustment tables
    // -------------------------------------------------------------------------

    @Test
    public void rangeFactor_matchesG13331Table() {
        assertEquals(-1, LockOnResolver.rangeFactor(0));
        assertEquals(0,  LockOnResolver.rangeFactor(1));
        assertEquals(0,  LockOnResolver.rangeFactor(4));
        assertEquals(1,  LockOnResolver.rangeFactor(5));
        assertEquals(1,  LockOnResolver.rangeFactor(10));
        assertEquals(2,  LockOnResolver.rangeFactor(11));
        assertEquals(2,  LockOnResolver.rangeFactor(15));
        assertEquals(3,  LockOnResolver.rangeFactor(16));
        assertEquals(3,  LockOnResolver.rangeFactor(20));
        assertEquals(4,  LockOnResolver.rangeFactor(21));
        assertEquals(4,  LockOnResolver.rangeFactor(30));
        assertEquals(5,  LockOnResolver.rangeFactor(31));
        assertEquals(5,  LockOnResolver.rangeFactor(40));
        assertEquals(6,  LockOnResolver.rangeFactor(41));
        assertEquals(6,  LockOnResolver.rangeFactor(99));
    }

    @Test
    public void speedFactor_matchesG13331Table() {
        assertEquals(-2, LockOnResolver.speedFactor(0));
        assertEquals(0,  LockOnResolver.speedFactor(1));
        assertEquals(0,  LockOnResolver.speedFactor(4));
        assertEquals(1,  LockOnResolver.speedFactor(5));
        assertEquals(1,  LockOnResolver.speedFactor(8));
        assertEquals(2,  LockOnResolver.speedFactor(9));
        assertEquals(2,  LockOnResolver.speedFactor(12));
        assertEquals(3,  LockOnResolver.speedFactor(13));
        assertEquals(3,  LockOnResolver.speedFactor(15));
        assertEquals(4,  LockOnResolver.speedFactor(16));
        assertEquals(4,  LockOnResolver.speedFactor(17));
        assertEquals(5,  LockOnResolver.speedFactor(18));
        assertEquals(6,  LockOnResolver.speedFactor(19));
        assertEquals(6,  LockOnResolver.speedFactor(32));
    }

    // -------------------------------------------------------------------------
    // Ship lock-on retention (G13.331)
    // -------------------------------------------------------------------------

    @Test
    public void fastCloseCloaker_lockOnAlwaysRetained() {
        submitAllocations(true, 20.0); // speed 20 → SF +6 → P = 8
        assertTrue(fed.hasLockOn(rom));
        cloakAndCompleteFadeOut();

        assertTrue("P = 8 → retention is certain (G13.331)", fed.hasLockOn(rom));
    }

    @Test
    public void stationaryCloaker_lockOnAlwaysLost() {
        submitAllocations(true, 0.0); // speed 0 → SF −2 → P = 0
        assertTrue(fed.hasLockOn(rom));
        cloakAndCompleteFadeOut();

        assertFalse("P = 0 → retention is impossible (G13.331)", fed.hasLockOn(rom));
    }

    @Test
    public void retainedLockOn_cancelsRangeDoubling() {
        submitAllocations(true, 20.0);
        cloakAndCompleteFadeOut();
        assertTrue(fed.hasLockOn(rom));

        // G13.32: with a retained lock-on, no doubling — true range 1 + scanner 0
        // + cloak bonus 5 (G13.302 still applies)
        assertEquals(6, game.getEffectiveRange(fed, rom));
    }

    @Test
    public void lostLockOn_doublesRangeAndAddsFive() {
        submitAllocations(true, 0.0);
        cloakAndCompleteFadeOut();
        assertFalse(fed.hasLockOn(rom));

        // G13.31: true range 1 doubled + 5 = 7
        assertEquals(7, game.getEffectiveRange(fed, rom));
    }

    @Test
    public void retainedLockOn_survivesTurnBoundary_whenConditionsUnchanged() {
        submitAllocations(true, 20.0);
        cloakAndCompleteFadeOut();
        assertTrue(fed.hasLockOn(rom));

        advanceToTurnEnd();
        submitAllocations(true, 20.0); // same speed, same range → same P: no re-roll (G13.3322)

        assertEquals(CloakState.FULLY_CLOAKED, rom.getCloakingDevice().getState());
        assertTrue("retained lock-on persists across the turn boundary (G13.402)",
                fed.hasLockOn(rom));
    }

    @Test
    public void noRetainedLockOn_cannotBeAcquiredAtTurnStart() {
        submitAllocations(true, 0.0);
        cloakAndCompleteFadeOut();
        assertFalse(fed.hasLockOn(rom));

        advanceToTurnEnd();
        submitAllocations(true, 0.0);

        assertEquals(CloakState.FULLY_CLOAKED, rom.getCloakingDevice().getState());
        assertFalse("no new lock-on on a cloaked ship (G13.301; G13.333 not yet in)",
                fed.hasLockOn(rom));
    }

    // -------------------------------------------------------------------------
    // Plasma retention (G13.3343)
    // -------------------------------------------------------------------------

    /**
     * Torpedo starts at range 18 so it is still ~12 hexes out (RF 2) when the
     * fade-out completes — far enough that it cannot impact rom (and interrupt
     * the phase loop with damage resolution) before the retention roll.
     */
    private PlasmaTorpedo plasmaTargeting(Ship target) {
        PlasmaTorpedo torp = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
        torp.setName("Test-Plasma");
        torp.setLocation(new Location(28, 10));
        torp.setTarget(target);
        torp.setSeekerType(Seeker.SeekerType.PLASMA);
        game.getSeekers().add(torp);
        return torp;
    }

    @Test
    public void plasma_fastCloaker_retainsTracking() {
        submitAllocations(true, 20.0); // P = 2 − RF(≈2) + 6 = 6 → certain
        PlasmaTorpedo torp = plasmaTargeting(rom);
        cloakAndCompleteFadeOut();

        assertTrue("plasma retention certain at P ≥ 6 (G13.3343)", torp.isCloakLockRetained());
        assertTrue("torpedo still in flight", game.getSeekers().contains(torp));
    }

    @Test
    public void plasma_stationaryCloaker_losesTracking() {
        submitAllocations(true, 0.0); // P = 2 − RF(≈2) − 2 < 0 → impossible
        PlasmaTorpedo torp = plasmaTargeting(rom);
        cloakAndCompleteFadeOut();

        assertFalse("torpedo removed from play after failed retention (G13.3343)",
                game.getSeekers().contains(torp));
    }
}
