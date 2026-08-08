package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.SpaceMine;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.ESG;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ESG mine-sweeping (G23.61): a field that sweeps over an active mine detonates it.
 * The mine's strength is absorbed by the field; any overflow spills onto the ESG
 * ship's facing shield, and the explosion harms no other unit.
 */
public class EsgMineTest {

    private Ship makeEsgShip(Game game, int radius, int energy) {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("Sphere");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(13); // south — will close toward the mine
        ESG esg = new ESG();
        esg.setDesignator("A");
        ship.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(energy);
        esg.activate(radius, 0);
        game.getShips().add(ship);
        return ship;
    }

    private ESG esgOf(Ship ship) {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof ESG) return (ESG) w;
        }
        return null;
    }

    private SpaceMine armedTBombAt(Game game, Ship layer, int x, int y) {
        SpaceMine mine = SpaceMine.createDroppedTBomb(layer, 0, true);
        mine.setLocation(new Location(x, y));
        mine.tryActivate(3, 2); // placed impulse 0 → armed by impulse 2 (M3.223)
        game.getMines().add(mine);
        assertTrue("mine is armed", mine.isActive());
        return mine;
    }

    private Energy alloc(Ship ship, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warp);
        return e;
    }

    /** Move the ESG ship one hex south so its ring sweeps onto the mine at (10,13). */
    private void sweep(Game game, Ship esgShip) {
        game.submitAllocation(esgShip, alloc(esgShip, 16.0));
        for (int guard = 0; guard < 60; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT && game.canMoveThisImpulse(esgShip)) {
                break;
            }
            game.advancePhase();
        }
        assertTrue(game.moveForward(esgShip).isSuccess());
        assertEquals(new Location(10, 11), esgShip.getLocation());
        game.advancePhase(); // leaving MOVEMENT resolves the fields
    }

    @Test
    public void weakField_absorbsMine_thenOverflowsToShipShield() {
        Game game = new Game();
        Ship esgShip = makeEsgShip(game, 2, 2); // radius-2, strength 7
        armedTBombAt(game, esgShip, 10, 13);    // tBomb = 10 damage, range 2 after the sweep
        game.startTurn();

        int shield1 = esgShip.getShields().getShieldStrength(1);
        sweep(game, esgShip);

        assertFalse("field spent itself detonating the mine", esgOf(esgShip).isActive());
        assertEquals("10-dmg mine, 7 absorbed by the field, 3 overflow to shield #1",
                shield1 - 3, esgShip.getShields().getShieldStrength(1));
        assertTrue("mine consumed", game.getMines().isEmpty());
    }

    @Test
    public void strongField_fullyAbsorbsMine_noShipDamage() {
        Game game = new Game();
        Ship esgShip = makeEsgShip(game, 2, 5); // radius-2, strength 17
        armedTBombAt(game, esgShip, 10, 13);
        game.startTurn();

        int shield1 = esgShip.getShields().getShieldStrength(1);
        sweep(game, esgShip);

        assertTrue("field survives", esgOf(esgShip).isActive());
        assertEquals("17 - 10", 7, esgOf(esgShip).getStrength());
        assertEquals("no overflow to the ship", shield1, esgShip.getShields().getShieldStrength(1));
        assertTrue("mine consumed", game.getMines().isEmpty());
    }
}
