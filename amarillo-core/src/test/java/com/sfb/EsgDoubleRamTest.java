package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.ESG;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Multiple ESGs on one ship (G23.122/.75): a target on a closing course that strikes
 * two fields of the same ship in one impulse takes their damage as a single combined
 * volley (G23.75), the outer ring resolving first (G23.122). Here an ESG ship carries
 * a radius-2 (strength 7) and radius-1 (strength 4) field; a target closes from range
 * 3 to range 1, entering both, and takes 11 on its facing shield.
 */
public class EsgDoubleRamTest {

    private Game game;
    private Ship esgShip;
    private Ship target;
    private ESG  outer; // radius 2
    private ESG  inner; // radius 1

    @Before
    public void setUp() {
        game = new Game();

        esgShip = new Ship();
        esgShip.init(FederationShips.getFedCa());
        esgShip.setName("Sphere");
        esgShip.setLocation(new Location(10, 10));
        esgShip.setFacing(13); // south — will close toward the target

        outer = new ESG(); outer.setDesignator("A");
        esgShip.getWeapons().addWeapon(outer);
        outer.setStoredEnergy(2); outer.activate(2, 0); // chart[2][2] = 7

        inner = new ESG(); inner.setDesignator("B");
        esgShip.getWeapons().addWeapon(inner);
        inner.setStoredEnergy(1); inner.activate(1, 0); // chart[1][1] = 4

        target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("Target");
        target.setLocation(new Location(10, 13)); // range 3, due south
        target.setFacing(1);                       // facing N, toward the ESG ship

        game.getShips().add(esgShip);
        game.getShips().add(target);
        game.startTurn();
    }

    private Energy alloc(Ship ship, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warp);
        return e;
    }

    @Test
    public void closingTargetStrikesBothFields_takesOneCombinedVolley() {
        game.submitAllocation(esgShip, alloc(esgShip, 16.0));
        game.submitAllocation(target,  alloc(target, 16.0));

        int shieldBefore = target.getShields().getShieldStrength(1);

        // Advance to a movement impulse where both (same speed) can move, then close them
        // toward each other so the range goes 3 -> 1 in a single impulse.
        for (int guard = 0; guard < 60; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT
                    && game.canMoveThisImpulse(esgShip) && game.canMoveThisImpulse(target)) {
                break;
            }
            game.advancePhase();
        }
        assertTrue(game.moveForward(esgShip).isSuccess());
        assertTrue(game.moveForward(target).isSuccess());
        assertEquals(new Location(10, 11), esgShip.getLocation());
        assertEquals(new Location(10, 12), target.getLocation());
        assertEquals("closed to range 1", 1,
                com.sfb.utilities.MapUtils.getRange(esgShip.getLocation(), target.getLocation()));

        game.advancePhase(); // leaving MOVEMENT resolves the fields

        assertEquals("both fields' strengths combined into one shield volley (7 + 4, G23.75)",
                shieldBefore - 11, target.getShields().getShieldStrength(1));
        assertFalse("outer field spent", outer.isActive());
        assertFalse("inner field spent", inner.isActive());
    }
}
