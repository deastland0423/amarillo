package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.ESG;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * ESG-vs-terrain grinding (G23.651/.6514): asteroids and ring material on the field's
 * ring wear it down — 2 points per asteroid hex, 1 per ring hex, regardless of speed —
 * and the damage is never carried to the ship (G23.6511). (Planets, by contrast,
 * collapse the whole field, G23.653 — covered by EsgPlanetTest.)
 */
public class EsgAsteroidTest {

    private Game withEsgShip(int energy) {
        Game game = new Game();
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("Sphere");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        ESG esg = new ESG();
        esg.setDesignator("A");
        ship.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(energy);
        esg.activate(2, 0); // radius 2; ring includes (10,12) and (10,8)
        game.getShips().add(ship);
        return game;
    }

    private ESG esgOf(Game game) {
        for (com.sfb.weapons.Weapon w : game.getShips().get(0).getWeapons().fetchAllWeapons()) {
            if (w instanceof ESG) return (ESG) w;
        }
        return null;
    }

    /** Advance to the first ACTIVITY phase — one MOVEMENT-phase field resolution has run. */
    private void oneGrind(Game game) {
        Ship ship = game.getShips().get(0);
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(0.0);
        game.startTurn();
        game.submitAllocation(ship, e);
        for (int guard = 0; guard < 80; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY) return;
            game.advancePhase();
        }
        fail("never reached ACTIVITY");
    }

    @Test
    public void asteroidOnTheRing_costsTwoPoints() {
        Game game = withEsgShip(2); // radius-2 strength 7
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 12)); // range 2 — on the ring
        assertTrue(game.isAsteroidHex(new Location(10, 12)));

        oneGrind(game);

        assertEquals("7 − 2 for one asteroid hex (G23.651)", 5, esgOf(game).getStrength());
        assertTrue(esgOf(game).isActive());
    }

    @Test
    public void twoAsteroids_costFourPoints() {
        Game game = withEsgShip(2); // strength 7
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 12));
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 8));

        oneGrind(game);

        assertEquals("7 − 2 − 2", 3, esgOf(game).getStrength());
    }

    @Test
    public void asteroidsCanGrindTheFieldToCollapse() {
        Game game = withEsgShip(1); // radius-2 strength 3
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 12));
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 8)); // 4 damage vs strength 3

        oneGrind(game);

        assertFalse("ground down to nothing (G23.651)", esgOf(game).isActive());
    }

    @Test
    public void ringMaterialCostsOnePoint() {
        Game game = withEsgShip(2); // strength 7
        // A gas giant at (10,14) r1 with a ring band at distance 2 makes (10,12) — which
        // is also on the ESG ring — ring material (P2.223).
        Terrain giant = new Terrain(TerrainType.GAS_GIANT, 10, 14, 1);
        giant.setRingBands(List.of(new int[]{2, 2}));
        game.addTerrain(giant);
        assertTrue(game.isRingHex(new Location(10, 12)));

        oneGrind(game);

        assertEquals("7 − 1 for one ring hex (G23.6514)", 6, esgOf(game).getStrength());
    }
}
