package com.sfb;

import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Multi-hex planet footprints (P2.2). A footprint of radius r covers every hex
 * within hex-distance r of the center (diameter 2r+1); class-M planets always
 * fill exactly one hex (P2.211); large gas giants (7+ across, P2.222) have a
 * pure-atmosphere outer ring around a surface interior.
 */
public class TerrainFootprintTest {

    private Game game;

    @Before
    public void setUp() {
        game = new Game();
    }

    private int countHexes(java.util.function.Predicate<Location> test) {
        int n = 0;
        for (int col = 1; col <= 42; col++)
            for (int row = 1; row <= 32; row++)
                if (test.test(new Location(col, row)))
                    n++;
        return n;
    }

    @Test
    public void classM_occupiesExactlyOneHex() {
        game.addTerrain(new Terrain(TerrainType.PLANET, 20, 15));
        assertEquals(1, countHexes(game::isPlanetHex));
        assertTrue(game.isPlanetSurfaceHex(new Location(20, 15)));
        assertEquals(0, countHexes(game::isPlanetAtmosphereHex));
    }

    @Test
    public void smallGasGiant_allSurface_noAtmosphereRing() {
        // Radius 2 → 5 across (Uranus, P2.221) — under 7, so single-layer (P2.222)
        game.addTerrain(new Terrain(TerrainType.GAS_GIANT, 20, 15, 2));
        assertEquals("disk of radius 2 = 1+6+12 hexes", 19, countHexes(game::isPlanetHex));
        assertEquals(19, countHexes(game::isPlanetSurfaceHex));
        assertEquals(0, countHexes(game::isPlanetAtmosphereHex));
    }

    @Test
    public void largeGasGiant_atmosphereRingAroundSurface() {
        // Radius 3 → 7 across: outermost ring is pure atmosphere (P2.222)
        game.addTerrain(new Terrain(TerrainType.GAS_GIANT, 20, 15, 3));
        assertEquals("disk of radius 3 = 37 hexes", 37, countHexes(game::isPlanetHex));
        assertEquals("interior disk of radius 2", 19, countHexes(game::isPlanetSurfaceHex));
        assertEquals("ring at distance 3 = 18 hexes", 18, countHexes(game::isPlanetAtmosphereHex));
        // Center is surface; a ring hex is atmosphere but still no-entry
        assertTrue(game.isPlanetSurfaceHex(new Location(20, 15)));
        Location ringHex = new Location(20, 12); // 3 straight up the column
        assertTrue(game.isPlanetHex(ringHex));
        assertTrue(game.isPlanetAtmosphereHex(ringHex));
        assertFalse(game.isPlanetSurfaceHex(ringHex));
    }

    @Test
    public void footprint_usesHexDistance_notSquareBox() {
        // A radius-3 disk on this offset grid is NOT a 7×7 box (49) — it's 37.
        // Guards against a raw row/col-delta expansion (the classic offset-grid
        // parity trap this codebase's conventions warn about).
        game.addTerrain(new Terrain(TerrainType.GAS_GIANT, 20, 15, 3));
        assertEquals(37, countHexes(game::isPlanetHex));
        assertFalse("box corner must be outside the disk",
                game.isPlanetHex(new Location(17, 12)));
    }

    @Test
    public void saturnSized_radius5_footprint() {
        // Saturn: 11 hexes across (P2.221) → r=5 → 1+6+12+18+24+30 = 91 hexes
        game.addTerrain(new Terrain(TerrainType.GAS_GIANT, 20, 15, 5));
        assertEquals(91, countHexes(game::isPlanetHex));
        assertEquals("ring = 30 atmosphere hexes", 30, countHexes(game::isPlanetAtmosphereHex));
    }

    @Test
    public void movementBlocked_acrossWholeFootprint() {
        game.addTerrain(new Terrain(TerrainType.GAS_GIANT, 20, 15, 3));
        // The existing movement/rotation guards key off isPlanetHex — the ring
        // hex (atmosphere) must be no-entry just like the surface (P2.224)
        assertTrue(game.isPlanetHex(new Location(20, 12)));
        assertTrue(game.isPlanetHex(new Location(20, 15)));
        assertFalse(game.isPlanetHex(new Location(20, 11)));
    }
}
