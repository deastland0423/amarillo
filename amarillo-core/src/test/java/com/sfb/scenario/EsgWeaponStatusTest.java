package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ESG;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ESG generators start a scenario charged by weapon status (G23.23):
 * WS-0/I = 0, WS-2 = 2 per ESG, WS-3 = 5 per ESG.
 */
public class EsgWeaponStatusTest {

    private Ship shipWithEsg() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ESG esg = new ESG();
        esg.setDesignator("A");
        ship.getWeapons().addWeapon(esg);
        return ship;
    }

    private ESG esgOf(Ship ship) {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof ESG) return (ESG) w;
        }
        return null;
    }

    @Test
    public void ws3_startsWithFiveEnergy() {
        Ship ship = shipWithEsg();
        ScenarioLoader.applyWeaponStatus(ship, 3);
        assertEquals(5, esgOf(ship).getStoredEnergy());
    }

    @Test
    public void ws2_startsWithTwoEnergy() {
        Ship ship = shipWithEsg();
        ScenarioLoader.applyWeaponStatus(ship, 2);
        assertEquals(2, esgOf(ship).getStoredEnergy());
    }

    @Test
    public void ws0_startsEmpty() {
        Ship ship = shipWithEsg();
        ScenarioLoader.applyWeaponStatus(ship, 0);
        assertEquals(0, esgOf(ship).getStoredEnergy());
    }

    @Test
    public void esgGetsCapacitorsInY167PlusScenario() {
        Ship ship = shipWithEsg();
        ScenarioLoader.applyEsgCapacitors(ship, 168); // even a pre-167 hull, if the battle is Y167+
        assertTrue("Y168 scenario fits capacitors (G23.24)", esgOf(ship).hasCapacitor());
        assertEquals(7, esgOf(ship).maxStorage());
    }

    @Test
    public void esgHasNoCapacitorInPreY167Scenario() {
        Ship ship = shipWithEsg();
        ScenarioLoader.applyEsgCapacitors(ship, 120);
        assertFalse("pre-capacitor era (G23.245)", esgOf(ship).hasCapacitor());
        assertEquals(5, esgOf(ship).maxStorage());
    }
}
