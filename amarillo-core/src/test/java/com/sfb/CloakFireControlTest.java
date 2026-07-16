package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.CloakingDevice;
import com.sfb.systemgroups.CloakingDevice.CloakState;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Cloak ↔ fire-control interaction (G13):
 * an operating cloak drops fire control to passive, which precludes tractors
 * (G7.41) and transporters (D6.124); FC reactivates on decloak. Tractor use is
 * additionally hard-blocked in every fade state via cloakActionBlock.
 */
public class CloakFireControlTest {

    private Game game;
    private Ship rom;   // KR — has a cloaking device
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

    private Energy makeAllocation(Ship ship, boolean cloakPaid) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(4.0);
        e.setCloakPaid(cloakPaid);
        return e;
    }

    /** Submit both allocations (rom first) — triggers beginImpulses(). */
    private void submitAllocations(boolean romCloakPaid) {
        game.submitAllocation(rom, makeAllocation(rom, romCloakPaid));
        game.submitAllocation(fed, makeAllocation(fed, false));
    }

    private void advanceToPhase(Game.ImpulsePhase target) {
        for (int guard = 0; guard < 400 && game.getCurrentPhase() != target; guard++)
            game.advancePhase();
        assertEquals(target, game.getCurrentPhase());
    }

    private void advanceUntilFullyCloaked() {
        CloakingDevice cd = rom.getCloakingDevice();
        for (int guard = 0; guard < 100 && cd.getState() != CloakState.FULLY_CLOAKED; guard++)
            game.advancePhase();
        assertEquals(CloakState.FULLY_CLOAKED, cd.getState());
    }

    private void advanceToTurnEnd() {
        for (int guard = 0; guard < 600 && !game.isAwaitingAllocation(); guard++)
            game.advancePhase();
        assertTrue(game.isAwaitingAllocation());
    }

    // -------------------------------------------------------------------------
    // Cloak activation drops FC
    // -------------------------------------------------------------------------

    @Test
    public void cloak_dropsFireControlToPassive() {
        submitAllocations(true);
        advanceToPhase(Game.ImpulsePhase.ACTIVITY);

        assertTrue("FC paid → active at turn start", rom.isActiveFireControl());
        Game.ActionResult r = game.cloak(rom);
        assertTrue(r.getMessage(), r.isSuccess());
        assertFalse("cloaking drops FC to passive", rom.isActiveFireControl());
    }

    @Test
    public void cloak_clearsOwnLockOns() {
        submitAllocations(true);
        advanceToPhase(Game.ImpulsePhase.ACTIVITY);

        assertTrue("KR sensor 6 → auto lock-on at turn start", rom.hasLockOn(fed));
        assertTrue(game.cloak(rom).isSuccess());
        assertFalse("cloaking ship loses its own lock-ons (D6.62, G13.133)",
                rom.hasLockOn(fed));
    }

    @Test
    public void cloakedShip_cannotEstablishTractor_viaFcCheck() {
        submitAllocations(true);
        advanceToPhase(Game.ImpulsePhase.ACTIVITY);
        assertTrue(game.cloak(rom).isSuccess());

        rom.getTractors().initForTurn(5);
        rom.addLockOn(fed); // even with a (stale) lock-on, FC is passive
        Game.ActionResult r = game.establishTractor(rom, "USS Enterprise", 2);
        assertFalse(r.isSuccess());
    }

    @Test
    public void cloakBlock_stopsTractorEvenIfFcForcedActive() {
        submitAllocations(true);
        advanceToPhase(Game.ImpulsePhase.ACTIVITY);
        assertTrue(game.cloak(rom).isSuccess());

        // Bypass the FC consequence to prove the hard block stands on its own
        rom.setActiveFireControl(true);
        rom.addLockOn(fed);
        rom.getTractors().initForTurn(5);
        Game.ActionResult r = game.establishTractor(rom, "USS Enterprise", 2);
        assertFalse(r.isSuccess());
        assertTrue("blocked by the cloak, not a later check: " + r.getMessage(),
                r.getMessage().contains("cloaking device"));
    }

    @Test
    public void fullyCloaked_cannotRotateTractored() {
        // Link before the last allocation so the Initial Activity Phase opens
        rom.getTractors().initForTurn(5);
        rom.getTractors().linkUnit(fed);
        submitAllocations(true);
        assertEquals(Game.ImpulsePhase.INITIAL_ACTIVITY, game.getCurrentPhase());

        rom.getCloakingDevice().setState(CloakState.FULLY_CLOAKED);
        Game.ActionResult r = game.rotateTractored(rom, "USS Enterprise", 11, 9);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("cloaking device"));
    }

    // -------------------------------------------------------------------------
    // Decloak restores FC and rolls re-acquisition
    // -------------------------------------------------------------------------

    @Test
    public void uncloak_reactivatesFcAndRollsReacquisition() {
        submitAllocations(true);
        advanceToPhase(Game.ImpulsePhase.ACTIVITY);
        assertTrue(game.cloak(rom).isSuccess());
        advanceUntilFullyCloaked();

        assertFalse("full cloak breaks lock-on (D6.111)", fed.hasLockOn(rom));
        assertFalse(rom.isActiveFireControl());

        advanceToPhase(Game.ImpulsePhase.ACTIVITY);
        Game.ActionResult r = game.uncloak(rom);
        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("FC paid this turn → reactivates on decloak", rom.isActiveFireControl());
        // Fed CA sensor rating 6 → re-acquisition is automatic (D6.113)
        assertTrue("re-acquisition rolled on decloak", fed.hasLockOn(rom));
    }

    @Test
    public void uncloakDuringFadeOut_reactivatesFcWithoutReacquisition() {
        submitAllocations(true);
        advanceToPhase(Game.ImpulsePhase.ACTIVITY);
        assertTrue(game.cloak(rom).isSuccess());
        // Still FADING_OUT — lock-ons were never broken
        assertTrue(fed.hasLockOn(rom));

        Game.ActionResult r = game.uncloak(rom);
        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(CloakState.FADING_IN, rom.getCloakingDevice().getState());
        assertTrue(rom.isActiveFireControl());
        assertTrue(fed.hasLockOn(rom));
    }

    // -------------------------------------------------------------------------
    // Turn boundary
    // -------------------------------------------------------------------------

    @Test
    public void turnStart_fcStaysPassiveWhileFullyCloaked() {
        submitAllocations(true);
        advanceToPhase(Game.ImpulsePhase.ACTIVITY);
        assertTrue(game.cloak(rom).isSuccess());
        advanceUntilFullyCloaked();
        advanceToTurnEnd();

        submitAllocations(true); // pays cloak again — stays cloaked
        assertEquals(CloakState.FULLY_CLOAKED, rom.getCloakingDevice().getState());
        assertFalse("Ship.startTurn() must not reactivate FC under cloak",
                rom.isActiveFireControl());
        assertTrue("uncloaked ship's FC unaffected", fed.isActiveFireControl());
    }

    @Test
    public void turnStart_unpaidCloakCost_forcesInvoluntaryFadeIn() {
        submitAllocations(true);
        advanceToPhase(Game.ImpulsePhase.ACTIVITY);
        assertTrue(game.cloak(rom).isSuccess());
        advanceUntilFullyCloaked();
        advanceToTurnEnd();

        submitAllocations(false); // cloak cost lapses
        assertEquals(CloakState.FADING_IN, rom.getCloakingDevice().getState());
        assertTrue("fading-in ship with FC paid runs active FC",
                rom.isActiveFireControl());
    }
}
