package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Game.adjustEw (D6.315): phase gating, battery payment for reserve-bought
 * points (D6.312), and circuit lockout surfacing (D6.316).
 */
public class EwAdjustTest {

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

        Energy e = new Energy();
        e.setLifeSupport(rom.getLifeSupportCost());
        e.setFireControl(rom.getFireControlCost());
        e.setActivateShields(rom.getActiveShieldCost());
        e.setWarpMovement(4.0);
        game.submitAllocation(rom, e);
        Energy f = new Energy();
        f.setLifeSupport(fed.getLifeSupportCost());
        f.setFireControl(fed.getFireControlCost());
        f.setActivateShields(fed.getActiveShieldCost());
        f.setWarpMovement(4.0);
        game.submitAllocation(fed, f);
    }

    private void advanceToDirectFire() {
        for (int guard = 0; guard < 400
                && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());
    }

    @Test
    public void adjustEw_onlyDuringDirectFire() {
        // Right after allocations the game is in MOVEMENT of impulse 1
        Game.ActionResult r = game.adjustEw(rom, 2, 0);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("Direct Fire"));
    }

    @Test
    public void addedPoints_costBattery() {
        advanceToDirectFire();
        int batteryBefore = rom.getPowerSystems().getBatteryPower();

        Game.ActionResult r = game.adjustEw(rom, 2, 0);
        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(2, rom.getEcmAllocated());
        assertEquals(batteryBefore - 2, rom.getPowerSystems().getBatteryPower());
        assertTrue("announcement includes the change (D6.32)",
                r.getMessage().contains("ECM 0 → 2"));
    }

    @Test
    public void insufficientBattery_rejectedWithoutChange() {
        advanceToDirectFire();
        rom.getPowerSystems().setBatteryPower(1);

        Game.ActionResult r = game.adjustEw(rom, 2, 0);
        assertFalse(r.isSuccess());
        assertEquals(0, rom.getEcmAllocated());
        assertEquals(1, rom.getPowerSystems().getBatteryPower());
    }

    @Test
    public void switchback_blockedInsideLockout() {
        advanceToDirectFire();
        // Arm the lockout below the battery layer: commit all six circuits at
        // EA, then flip them all mid-turn via the core call — each circuit is
        // now committed to ECCM for 8 impulses (D6.316)
        assertNull(rom.allocateEw(6, 0, 1));
        assertNull(rom.adjustEw(0, 6, game.getAbsoluteImpulse()));

        // Game action tries to flip four back (cost 4 = full battery, so the
        // battery check passes and the circuit lockout is what rejects it)
        int battery = rom.getPowerSystems().getBatteryPower();
        Game.ActionResult back = game.adjustEw(rom, 4, 2);
        assertFalse("flip back inside the 8-impulse commitment must fail", back.isSuccess());
        assertTrue(back.getMessage(), back.getMessage().contains("D6.316"));
        assertEquals("battery untouched on a rejected adjust",
                battery, rom.getPowerSystems().getBatteryPower());
        assertEquals(6, rom.getEccmAllocated());
    }

    @Test
    public void dropIsFree() {
        advanceToDirectFire();
        assertTrue(game.adjustEw(rom, 2, 0).isSuccess());
        int battery = rom.getPowerSystems().getBatteryPower();

        Game.ActionResult r = game.adjustEw(rom, 0, 0);
        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(0, rom.getEcmAllocated());
        assertEquals("dropping costs nothing", battery, rom.getPowerSystems().getBatteryPower());
    }

    @Test
    public void reserveBoughtEw_expiresAtEndOfTurn() {
        advanceToDirectFire();
        assertTrue(game.adjustEw(rom, 2, 0).isSuccess());
        assertEquals(2, rom.getEcmAllocated());

        for (int guard = 0; guard < 600 && !game.isAwaitingAllocation(); guard++)
            game.advancePhase();
        assertTrue(game.isAwaitingAllocation());
        assertEquals("reserve-bought EW lapses at end of turn (D6.312)",
                0, rom.getEcmAllocated());
    }
}
