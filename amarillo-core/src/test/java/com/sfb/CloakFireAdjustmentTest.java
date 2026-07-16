package com.sfb;

import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.samples.FederationShips;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.CloakingDevice.CloakState;
import com.sfb.systemgroups.Energy;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * G13.37 fire adjustment chart wiring: every weapon (and seeker warhead) that
 * hits a FULLY cloaked ship rolls the chart. Fades intentionally do not — we
 * default to the G13.362 procedure (normal EW, no chart) until G13.361 lands.
 * The chart math itself is unit-tested in CloakingDeviceTest.
 */
public class CloakFireAdjustmentTest {

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
    }

    private static List<Weapon> singlePhaser() {
        Phaser1 ph = new Phaser1();
        ph.setArcs(ArcUtils.FH);
        ph.setDesignator("1");
        return java.util.Collections.singletonList(ph);
    }

    // -------------------------------------------------------------------------
    // Direct fire
    // -------------------------------------------------------------------------

    @Test
    public void directFire_atFullyCloakedShip_rollsTheChart() {
        game.getClock().nextImpulse();
        rom.getCloakingDevice().setState(CloakState.FULLY_CLOAKED);

        // Adjusted range passed as 1 (not the realistic 7) so the phaser always
        // does > 0 damage — the chart trigger is the target's cloak state
        game.fireWeapons(fed, rom, singlePhaser(), 1, 1, 1);

        assertEquals(1, game.getPendingVolleys().size());
        assertTrue("attacker log shows the G13.37 roll",
                game.getPendingVolleys().get(0).attackerLog.contains("G13.37"));
    }

    @Test
    public void directFire_atUncloakedShip_noChart() {
        game.getClock().nextImpulse();

        game.fireWeapons(fed, rom, singlePhaser(), 1, 1, 1);

        assertEquals(1, game.getPendingVolleys().size());
        assertFalse("no G13.37 roll against an uncloaked target",
                game.getPendingVolleys().get(0).attackerLog.contains("G13.37"));
    }

    @Test
    public void directFire_duringFadeOut_noChart_g13362Default() {
        game.getClock().nextImpulse();
        rom.getCloakingDevice().setState(CloakState.FADING_OUT);

        game.fireWeapons(fed, rom, singlePhaser(), 1, 3, 1);

        assertEquals(1, game.getPendingVolleys().size());
        assertFalse("fade periods use normal EW, no chart (G13.362 default)",
                game.getPendingVolleys().get(0).attackerLog.contains("G13.37"));
    }

    // -------------------------------------------------------------------------
    // Seeker impact (G13.35)
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

    @Test
    public void seekerImpact_onFullyCloakedShip_rollsTheChart() {
        game.startTurn();
        game.submitAllocation(rom, makeAllocation(rom, true, 20.0)); // fast → retention certain
        game.submitAllocation(fed, makeAllocation(fed, false, 4.0));

        // Plasma inbound from range 18 — retains through the cloak, then impacts
        PlasmaTorpedo torp = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
        torp.setName("Test-Plasma");
        torp.setLocation(new Location(28, 10));
        torp.setTarget(rom);
        torp.setSeekerType(Seeker.SeekerType.PLASMA);
        game.getSeekers().add(torp);

        for (int guard = 0; guard < 400
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        assertTrue(game.cloak(rom).isSuccess());

        StringBuilder allLog = new StringBuilder();
        boolean impacted = false;
        for (int guard = 0; guard < 400 && !impacted; guard++) {
            allLog.append(game.advancePhase().getMessage()).append('\n');
            impacted = allLog.indexOf("impacted") >= 0;
        }
        assertTrue("plasma reached the cloaked target", impacted);
        assertEquals("target still fully cloaked at impact",
                CloakState.FULLY_CLOAKED, rom.getCloakingDevice().getState());
        assertTrue("impact rolled the G13.37 chart", allLog.indexOf("G13.37") >= 0);
    }
}
