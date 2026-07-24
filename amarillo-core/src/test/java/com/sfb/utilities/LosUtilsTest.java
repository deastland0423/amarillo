package com.sfb.utilities;

import com.sfb.Game;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Exact LOS geometry (P2.321/P2.322): interior crossings block; edge-collinear
 * and corner-touching lines are legal; endpoints' own hexes never block.
 *
 * Grid facts used (flat-top, even columns shifted down a half-row):
 *  - (11,10)→(13,10) is the horizontal line along the SHARED EDGE between
 *    hexes (12,9) and (12,10) — the classic "fire along the edge" case.
 *  - (10,10)→(14,10) (both even columns) runs straight through the CENTER
 *    of (12,10).
 */
public class LosUtilsTest {

    private static Set<Location> hexes(Location... locs) {
        return Set.of(locs);
    }

    // -------------------------------------------------------------------------
    // Interior crossings — blocked
    // -------------------------------------------------------------------------

    @Test
    public void throughCenter_blocked() {
        assertTrue(LosUtils.blocked(new Location(10, 10), new Location(14, 10),
                hexes(new Location(12, 10))));
    }

    @Test
    public void diagonalThroughInterior_blocked() {
        // (11,10)→(13,11): passes through the interior of (12,10)
        assertTrue(LosUtils.blocked(new Location(11, 10), new Location(13, 11),
                hexes(new Location(12, 10))));
    }

    @Test
    public void verticalColumn_blocked() {
        assertTrue(LosUtils.blocked(new Location(11, 8), new Location(11, 12),
                hexes(new Location(11, 10))));
    }

    // -------------------------------------------------------------------------
    // Edge and corner grazes — legal (P2.321: fire along the edge)
    // -------------------------------------------------------------------------

    @Test
    public void alongSharedEdge_notBlocked_eitherSide() {
        Location a = new Location(11, 10), b = new Location(13, 10);
        // The line rides the edge between (12,9) and (12,10): legal whichever
        // side the planet is on
        assertFalse("surface below the line", LosUtils.blocked(a, b, hexes(new Location(12, 10))));
        assertFalse("surface above the line", LosUtils.blocked(a, b, hexes(new Location(12, 9))));
    }

    @Test
    public void farOffPath_notBlocked() {
        assertFalse(LosUtils.blocked(new Location(10, 10), new Location(14, 10),
                hexes(new Location(12, 14))));
    }

    // -------------------------------------------------------------------------
    // Endpoint and degenerate handling
    // -------------------------------------------------------------------------

    @Test
    public void endpointHexes_neverBlock() {
        // Both endpoint hexes are in the blocker set — they are not "between"
        // the units (P2.322)
        assertFalse(LosUtils.blocked(new Location(10, 10), new Location(11, 10),
                hexes(new Location(10, 10), new Location(11, 10))));
    }

    @Test
    public void sameHex_neverBlocked() {
        assertFalse(LosUtils.blocked(new Location(10, 10), new Location(10, 10),
                hexes(new Location(10, 10))));
    }

    @Test
    public void adjacentHexes_nothingBetween() {
        assertFalse(LosUtils.blocked(new Location(10, 10), new Location(10, 11),
                hexes(new Location(12, 10))));
    }

    // -------------------------------------------------------------------------
    // Game-level: surface blocks, atmosphere is transparent
    // -------------------------------------------------------------------------

    @Test
    public void gasGiantSurface_blocks_atmosphereRingDoesNot() {
        Game game = new Game();
        game.addTerrain(new Terrain(TerrainType.GAS_GIANT, 20, 15, 3));

        // Straight through the giant's bulk: blocked
        assertTrue(game.losBlocked(new Location(16, 15), new Location(24, 15)));

        // Same span four rows up: comfortably clear of the footprint
        assertFalse(game.losBlocked(new Location(16, 10), new Location(24, 10)));

        // Vertical chord through the ring hex (20,12) only: (20,11)→(20,13)
        // crosses (20,12)'s interior dead-center. (20,12) is ATMOSPHERE
        // (distance 3) — transparent per P2.321, so unblocked even though the
        // very same chord against a blocker set containing (20,12) blocks.
        assertTrue("sanity: the chord really does cross (20,12)",
                LosUtils.blocked(new Location(20, 11), new Location(20, 13),
                        hexes(new Location(20, 12))));
        assertTrue("(20,12) is the atmosphere ring", game.isPlanetAtmosphereHex(new Location(20, 12)));
        assertFalse("atmosphere does not block sight",
                game.losBlocked(new Location(20, 11), new Location(20, 13)));
    }

    @Test
    public void classM_blocksOnlyItsOwnHex() {
        Game game = new Game();
        game.addTerrain(new Terrain(TerrainType.PLANET, 12, 10));
        assertTrue(game.losBlocked(new Location(10, 10), new Location(14, 10)));
        assertFalse("edge graze is legal fire (P2.321)",
                game.losBlocked(new Location(11, 10), new Location(13, 10)));
        assertFalse(game.losBlocked(new Location(10, 12), new Location(14, 12)));
    }
}
