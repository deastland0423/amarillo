package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.ESG;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ESG-vs-planet (G23.653): a field whose ring reaches a planet's footprint is spread
 * over too wide an area — it collapses entirely and does no damage to the planet.
 */
public class EsgPlanetTest {

    private Energy alloc(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(0.0);
        return e;
    }

    @Test
    public void fieldStrikingAPlanet_collapses() {
        Game game = new Game();
        // Planet at (10,13) radius 1 — its footprint includes (10,12).
        game.addTerrain(new Terrain(TerrainType.PLANET, 10, 13, 1));
        assertTrue(game.isPlanetHex(new Location(10, 12)));

        Ship esgShip = new Ship();
        esgShip.init(FederationShips.getFedCa());
        esgShip.setName("Sphere");
        esgShip.setLocation(new Location(10, 10));
        esgShip.setFacing(1);
        ESG esg = new ESG();
        esg.setDesignator("A");
        esgShip.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(5);
        esg.activate(2, 0); // radius-2 ring includes (10,12) — a planet hex
        game.getShips().add(esgShip);
        assertTrue(esg.isActive());

        game.startTurn();
        game.submitAllocation(esgShip, alloc(esgShip));

        for (int guard = 0; guard < 60 && esg.isActive(); guard++) {
            game.advancePhase();
        }

        assertFalse("field spread over the planet and collapsed (G23.653)", esg.isActive());
        assertEquals(0, esg.getStrength());
    }
}
