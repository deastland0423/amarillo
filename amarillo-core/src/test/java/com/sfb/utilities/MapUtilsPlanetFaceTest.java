package com.sfb.utilities;

import com.sfb.properties.Location;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Planet-face visibility (SH50.46): which hex sides of a planet a ship can see.
 * A convex hex shows its near hemisphere and hides the far side — 3 sides when
 * looking straight at a side, 2 when looking straight at a vertex.
 */
public class MapUtilsPlanetFaceTest {

    // Sides are 1..6 = A..F, matching absolute shield facing.
    private static final int A = 1, B = 2, C = 3, D = 4, E = 5, F = 6;

    @Test
    public void shipDueNorth_seesTheNearThreeSides() {
        // Planet 1605, ship 1603 (two hexes due north). The worked example:
        // it sees F, A, B and cannot see C, D, E.
        Set<Integer> visible = MapUtils.visiblePlanetSides(new Location(16, 5), new Location(16, 3));
        assertEquals(Set.of(F, A, B), visible);
        assertTrue(MapUtils.isPlanetSideVisible(new Location(16, 5), new Location(16, 3), A));
        assertFalse("far side hidden by the planet's bulk",
                MapUtils.isPlanetSideVisible(new Location(16, 5), new Location(16, 3), D));
    }

    @Test
    public void shipOnAnExactVertex_seesOnlyTheTwoSidesTouchingIt() {
        // Ship due east on the 4-arc (a vertex between B and C): only B and C.
        Set<Integer> visible = MapUtils.visiblePlanetSides(new Location(16, 5), new Location(20, 5));
        assertEquals(Set.of(B, C), visible);
        assertEquals("a vertex shows exactly two sides", 2, visible.size());
    }

    @Test
    public void shipDueSouth_seesTheOppositeNearThree() {
        Set<Integer> visible = MapUtils.visiblePlanetSides(new Location(16, 5), new Location(16, 9));
        assertEquals(Set.of(C, D, E), visible);
        assertFalse(MapUtils.isPlanetSideVisible(new Location(16, 5), new Location(16, 9), A));
    }

    @Test
    public void sameHex_seesNothing() {
        assertTrue(MapUtils.visiblePlanetSides(new Location(16, 5), new Location(16, 5)).isEmpty());
    }
}
