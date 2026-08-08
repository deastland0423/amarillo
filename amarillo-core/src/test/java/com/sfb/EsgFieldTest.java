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
 * ESG field in play (G23.0): the ring damages a ship that ENTERS it. An ESG ship
 * sits at (10,10) with an active radius-2 field; a target slides from range 3 to
 * range 2, entering the ring, and takes the field's strength on its facing
 * shield while the field collapses (G23.51/.511). The generating ship is unhurt
 * by its own field.
 */
public class EsgFieldTest {

    private Game game;
    private Ship esgShip;
    private Ship target;
    private ESG  esg;

    @Before
    public void setUp() {
        game = new Game();

        esgShip = new Ship();
        esgShip.init(FederationShips.getFedCa());
        esgShip.setName("Sphere");
        esgShip.setLocation(new Location(10, 10));
        esgShip.setFacing(1);

        // Give it an active radius-2 ESG field (strength = chart[2][2] = 7).
        esg = new ESG();
        esg.setDesignator("A");
        esgShip.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(2);
        esg.activate(2, 0);

        target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("Target");
        target.setLocation(new Location(10, 13)); // range 3, due south
        target.setFacing(1);                       // facing N, toward the ESG ship

        game.getShips().add(esgShip);
        game.getShips().add(target);

        assertEquals("target starts outside the ring", 3,
                com.sfb.utilities.MapUtils.getRange(new Location(10, 10), new Location(10, 13)));

        game.startTurn();
    }

    private Energy allocation(Ship ship, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warp);
        return e;
    }

    private void advanceUntilCanMove(Ship ship) {
        for (int guard = 0; guard < 60; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT && game.canMoveThisImpulse(ship))
                return;
            game.advancePhase();
        }
        fail("Ship never became movable");
    }

    @Test
    public void shipEnteringTheRing_takesFacingShieldDamage_andCollapsesTheField() {
        game.submitAllocation(esgShip, allocation(esgShip, 0.0));  // stationary → ring stays at (10,10)
        game.submitAllocation(target,  allocation(target, 16.0));

        int shieldBefore   = target.getShields().getShieldStrength(1);
        int esgShipShield1 = esgShip.getShields().getShieldStrength(1);

        advanceUntilCanMove(target);
        assertTrue(game.moveForward(target).isSuccess());
        assertEquals("moved 1013 → 1012 (range 2, on the ring)", new Location(10, 12), target.getLocation());

        // Leaving MOVEMENT processes ESG fields for the impulse.
        game.advancePhase();

        assertEquals("facing shield #1 took the field's strength (chart[2][2]=7)",
                shieldBefore - 7, target.getShields().getShieldStrength(1));
        assertFalse("field spent itself on the ship (G23.511)", esg.isActive());
        assertEquals(0, esg.getStrength());
        assertEquals("the generating ship is unhurt by its own field",
                esgShipShield1, esgShip.getShields().getShieldStrength(1));
    }

    @Test
    public void droneDacHit_destroysTheEsg_andCollapsesItsField() {
        // ESGs are destroyed on 'drone' DAC hits (G23.14).
        assertTrue(esg.isFunctional());
        String label = esgShip.applyDacChoiceHit("drone", esg.getName(), null);

        assertNotNull("ESG is a valid target for a drone DAC hit", label);
        assertFalse("the ESG box is destroyed", esg.isFunctional());
        assertFalse("its active field collapses immediately", esg.isActive());
    }
}
