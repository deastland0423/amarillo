package com.sfb;

import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Planetary rings (P2.223): enterable asteroid-like bands around a gas giant,
 * generated from {inner, outer} hex-distance bands, kept out of the no-entry
 * body footprint. Multi-band with gaps supported.
 */
public class RingTerrainTest {

    private Game game;

    @Before
    public void setUp() {
        game = new Game();
    }

    private Terrain giant(int col, int row, int radius, List<int[]> bands) {
        Terrain t = new Terrain(TerrainType.GAS_GIANT, col, row, radius);
        t.setRingBands(bands);
        return t;
    }

    private int countHexes(java.util.function.Predicate<Location> test) {
        int n = 0;
        for (int c = 1; c <= 42; c++)
            for (int r = 1; r <= 32; r++)
                if (test.test(new Location(c, r)))
                    n++;
        return n;
    }

    @Test
    public void singleBand_generatesAnnulusOfRingHexes() {
        // radius 2 body, ring band at distance 4..5
        game.addTerrain(giant(20, 15, 2, List.of(new int[] { 4, 5 })));
        // Ring of distance 4 = 24 hexes, distance 5 = 30 → 54 enterable ring hexes
        assertEquals(54, countHexes(game::isRingHex));
        // A distance-4 hex is a ring hex, and NOT a no-entry planet hex
        Location r4 = new Location(20, 11); // 4 straight up
        assertTrue(game.isRingHex(r4));
        assertFalse("rings are enterable — not in the no-entry footprint",
                game.isPlanetHex(r4));
    }

    @Test
    public void ringHexes_excludeBodyFootprint() {
        // Band overlaps the body (inner 2 <= radius 3): body hexes win, only
        // the distance-4..5 shell becomes ring
        game.addTerrain(giant(20, 15, 3, List.of(new int[] { 2, 5 })));
        // Nothing at distance ≤3 is a ring hex (body claims it)
        assertFalse(game.isRingHex(new Location(20, 15))); // center
        assertFalse(game.isRingHex(new Location(20, 12))); // dist 3 — body
        assertTrue(game.isRingHex(new Location(20, 11)));  // dist 4 — ring
    }

    @Test
    public void multiBand_withGap_leavesOpenSpaceBetween() {
        // Inner ring 4..5, gap at 6, outer ring 7..8 (SH35-style)
        game.addTerrain(giant(20, 15, 3,
                List.of(new int[] { 4, 5 }, new int[] { 7, 8 })));
        assertTrue(game.isRingHex(new Location(20, 11)));  // dist 4 — inner ring
        assertFalse("gap between bands is open space", game.isRingHex(new Location(20, 9))); // dist 6
        assertTrue(game.isRingHex(new Location(20, 8)));   // dist 7 — outer ring
    }

    @Test
    public void ringDamageTable_matchesP2223() {
        // Ring Material Damage Table (P2.223): lighter than asteroids.
        // die 1 → all zero; die 6 → 0/5/7/15 across speed brackets
        assertArrayEquals(new int[] { 0, 0, 0, 0 }, Game.RING_DAMAGE[0]);
        assertArrayEquals(new int[] { 0, 5, 7, 15 }, Game.RING_DAMAGE[5]);
        assertArrayEquals(new int[] { 0, 1, 3, 7 }, Game.RING_DAMAGE[3]);
        // Rings hit lighter than asteroids at a given cell (die 6, 26+ speed)
        assertTrue(Game.RING_DAMAGE[5][3] < Game.ASTEROID_DAMAGE[5][3]);
    }

    @Test
    public void noRings_whenBandsEmpty() {
        game.addTerrain(new Terrain(TerrainType.GAS_GIANT, 20, 15, 3));
        assertEquals(0, countHexes(game::isRingHex));
    }
}
