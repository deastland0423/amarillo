package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.CloakingDevice.CloakState;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.ESG;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ESG-vs-cloak interactions (G23.62). A ship cannot operate an ESG while cloaked or
 * fading, and activating a cloak drops any active field (G23.622); but a cloaked ship
 * is still hit by an ESG field as if it were not cloaked (G23.621).
 */
public class EsgCloakTest {

    private ESG addEsg(Ship ship, int energy) {
        ESG esg = new ESG();
        esg.setDesignator("A");
        ship.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(energy);
        return esg;
    }

    private ESG esgOf(Ship ship) {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof ESG) return (ESG) w;
        }
        return null;
    }

    private Energy alloc(Ship ship, boolean cloakPaid, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warp);
        e.setCloakPaid(cloakPaid);
        return e;
    }

    private void advanceToActivity(Game game) {
        for (int guard = 0; guard < 80; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY) return;
            game.advancePhase();
        }
        fail("never reached ACTIVITY");
    }

    private Ship romWithEsg(Game game, int energy) {
        Ship rom = new Ship();
        rom.init(RomulanShips.getRomKr());
        rom.setName("RIS Talon");
        rom.setLocation(new Location(10, 10));
        rom.setFacing(1);
        rom.setSpeedPreviousTurn(31);
        rom.setSpeedTwoTurnsAgo(31);
        addEsg(rom, energy);
        game.getShips().add(rom);
        return rom;
    }

    @Test
    public void cannotAnnounceEsgWhileCloaked() {
        Game game = new Game();
        Ship rom = romWithEsg(game, 3);
        game.startTurn();
        game.submitAllocation(rom, alloc(rom, false, 0.0));
        advanceToActivity(game);
        rom.getCloakingDevice().setState(CloakState.FULLY_CLOAKED);

        Game.ActionResult r = game.announceEsg(rom, "A", 1, 0);

        assertFalse("a cloaked ship cannot operate an ESG (G23.622)", r.isSuccess());
        assertTrue(r.getMessage().contains("cloaked or fading"));
        assertFalse(esgOf(rom).isAnnounced());
    }

    @Test
    public void activatingCloak_dropsActiveEsgField() {
        Game game = new Game();
        Ship rom = romWithEsg(game, 3);
        ESG esg = esgOf(rom);
        esg.activate(2, 0); // field already up
        game.startTurn();
        game.submitAllocation(rom, alloc(rom, true, 0.0)); // cloak cost paid
        advanceToActivity(game);
        assertTrue(esg.isActive());

        Game.ActionResult r = game.cloak(rom);

        assertTrue(r.isSuccess());
        assertFalse("the field must drop to cloak (G23.622)", esg.isActive());
        assertTrue(r.getMessage().contains("dropped to cloak"));
    }

    @Test
    public void cloakedShip_isStillHitByEsgField() {
        Game game = new Game();

        Ship esgShip = new Ship();
        esgShip.init(FederationShips.getFedCa());
        esgShip.setName("Sphere");
        esgShip.setLocation(new Location(10, 10));
        esgShip.setFacing(13); // south — closes toward the target
        ESG esg = new ESG();
        esg.setDesignator("A");
        esgShip.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(2);
        esg.activate(2, 0); // radius-2, strength 7
        game.getShips().add(esgShip);

        Ship target = new Ship();
        target.init(RomulanShips.getRomKr());
        target.setName("Cloaked");
        target.setLocation(new Location(10, 13)); // range 3, becomes range 2 after the sweep
        target.setFacing(1);                       // shield #1 faces the ESG ship
        target.setSpeedPreviousTurn(31);
        target.setSpeedTwoTurnsAgo(31);
        target.getCloakingDevice().setState(CloakState.FULLY_CLOAKED);
        game.getShips().add(target);

        game.startTurn();
        game.submitAllocation(esgShip, alloc(esgShip, false, 16.0));
        game.submitAllocation(target,  alloc(target, true, 0.0)); // stays cloaked, stationary

        int shieldBefore = target.getShields().getShieldStrength(1);

        for (int guard = 0; guard < 60; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT && game.canMoveThisImpulse(esgShip)) break;
            game.advancePhase();
        }
        assertTrue(game.moveForward(esgShip).isSuccess());
        assertEquals(new Location(10, 11), esgShip.getLocation());
        game.advancePhase(); // resolve the field

        assertEquals("the cloaked ship takes the field's 7 on its facing shield (G23.621)",
                shieldBefore - 7, target.getShields().getShieldStrength(1));
    }
}
