package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ESG;
import com.sfb.weapons.Hellbore;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Hellbore-vs-ESG (G23.84), the classic Hydran-vs-Lyran exchange. A hellbore fired at
 * a ship generating an active ESG hits the field automatically; the field absorbs up to
 * its strength and any remainder envelops the ship undiminished (G23.841). Mirrors the
 * rulebook example: an overloaded hellbore (22 at range 4) vs a radius-3 strength-15
 * field — 15 collapses the field, 7 carry through to the ship.
 */
public class EsgHellboreTest {

    private Game game;
    private Ship attacker;
    private Ship lyran;
    private ESG  esg;

    @Before
    public void setUp() {
        game = new Game();

        attacker = new Ship();
        attacker.init(FederationShips.getFedCa());
        attacker.setName("Hydran");
        attacker.setLocation(new Location(10, 14));
        attacker.setFacing(1);

        lyran = new Ship();
        lyran.init(FederationShips.getFedCa());
        lyran.setName("Lyran");
        lyran.setLocation(new Location(10, 10)); // range 4 from the attacker
        lyran.setFacing(1);
        esg = new ESG();
        esg.setDesignator("A");
        lyran.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(5);
        esg.activate(3, 0); // radius 3, chart[3][5] = strength 15

        game.getShips().add(attacker);
        game.getShips().add(lyran);
        game.getClock().nextImpulse();
    }

    private List<Weapon> hellbore(boolean overload) {
        Hellbore h = new Hellbore();
        if (overload) h.setOverload();
        h.setArmingTurn(2); // armed
        h.setDesignator("H");
        return java.util.Collections.singletonList(h);
    }

    @Test
    public void overloadedHellbore_collapsesField_andCarriesRemainderToShip() {
        assertEquals(15, esg.getStrength());
        game.fireWeapons(attacker, lyran, hellbore(true), 4, 4, 1); // overload = 22 at range 4

        assertFalse("field collapsed (15 absorbed)", esg.isActive());
        assertTrue("7 points carried to the ship (22 - 15, G23.841)",
                game.getPendingVolleys().get(0).attackerLog.contains("7 carried to ship"));
    }

    @Test
    public void standardHellbore_exactlyEliminatesField_noCarryover() {
        game.fireWeapons(attacker, lyran, hellbore(false), 4, 4, 1); // standard = 15 at range 4

        assertFalse("field eliminated (15 vs 15)", esg.isActive());
        assertTrue("no carryover to the ship",
                game.getPendingVolleys().get(0).attackerLog.contains("absorbed (G23.841)"));
    }

    @Test
    public void weakerHellbore_isFullyAbsorbed_fieldSurvives() {
        // Re-raise the field bigger: radius 1, energy 5 = strength 18 > standard's 15.
        esg.deactivate();
        esg.setStoredEnergy(5);
        esg.activate(1, 0);
        assertEquals(18, esg.getStrength());

        game.fireWeapons(attacker, lyran, hellbore(false), 4, 4, 1); // standard = 15

        assertTrue("field survives", esg.isActive());
        assertEquals("18 - 15", 3, esg.getStrength());
    }
}
