package com.sfb.utilities;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.sfb.objects.Marker;
import com.sfb.properties.Location;

/**
 * Unit tests for MapUtils hex math.
 *
 * Coordinate system: x = column (1-based), y = row (1-based), y increases southward.
 * Facings: 1=North, 5=NE, 9=SE, 13=South, 17=SW, 21=NW (cardinal steps of 4).
 * Bearings 1-24 in fine increments; 1=N, 13=S, 4/10=E/W spine.
 */
public class MapUtilsTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Marker at(int x, int y) { return new Marker(x, y); }

    // ── getRange ───────────────────────────────────────────────────────────────

    @Test
    public void testRangeSameHex() {
        assertEquals(0, MapUtils.getRange(at(5, 5), at(5, 5)));
    }

    @Test
    public void testRangeSameColumn() {
        // Same x: range = |y2 - y1|
        assertEquals(3, MapUtils.getRange(at(5, 5), at(5, 2)));
        assertEquals(4, MapUtils.getRange(at(5, 5), at(5, 9)));
    }

    @Test
    public void testRangePurelyHorizontal() {
        // Exactly on the E/W spine: range = xDiff
        // From odd x=5, y=5 moving right 2 cols (still same row for even offset)
        assertEquals(2, MapUtils.getRange(at(5, 5), at(7, 5)));
        // From even x=6, y=5 moving right 2 cols
        assertEquals(2, MapUtils.getRange(at(6, 5), at(8, 5)));
    }

    @Test
    public void testRangeNortheastDiagonal() {
        // Moving right and up, within the side span
        assertEquals(1, MapUtils.getRange(at(5, 5), at(6, 5)));
        assertEquals(2, MapUtils.getRange(at(5, 5), at(7, 4)));
    }

    @Test
    public void testRangeAboveSideSpan() {
        // xDiff = 2, target above side span: range = xDiff + yDiff from spine
        assertEquals(4, MapUtils.getRange(at(6, 10), at(8, 7)));
    }

    @Test
    public void testRangeBelowSideSpan() {
        assertEquals(4, MapUtils.getRange(at(6, 10), at(8, 13)));
    }

    // ── getBearing ─────────────────────────────────────────────────────────────

    @Test
    public void testBearingSameHex() {
        assertEquals(0, MapUtils.getBearing(at(5, 5), at(5, 5)));
    }

    @Test
    public void testBearingNorth() {
        assertEquals(1, MapUtils.getBearing(at(5, 5), at(5, 3)));
    }

    @Test
    public void testBearingSouth() {
        assertEquals(13, MapUtils.getBearing(at(5, 5), at(5, 8)));
    }

    @Test
    public void testBearingEast() {
        // Even xDiff, same y → bearing 4
        assertEquals(4, MapUtils.getBearing(at(5, 5), at(7, 5)));
    }

    @Test
    public void testBearingWest() {
        assertEquals(10, MapUtils.getBearing(at(7, 5), at(5, 5)));
    }

    @Test
    public void testBearingNortheastSpine() {
        // On the arc hex (NE direction, bearing 5)
        // From odd x=5, y=5: NE arc hex at x=6, y=4
        assertEquals(5, MapUtils.getBearing(at(5, 5), at(6, 4)));
    }

    @Test
    public void testBearingNorthwestSpine() {
        // From odd x=5, y=5: NW arc hex at x=4, y=4
        assertEquals(21, MapUtils.getBearing(at(5, 5), at(4, 4)));
    }

    @Test
    public void testBearingSoutheastSpine() {
        // From odd x=5, y=5: SE arc hex at x=6, y=5
        assertEquals(9, MapUtils.getBearing(at(5, 5), at(6, 5)));
    }

    @Test
    public void testBearingSouthwestSpine() {
        // From odd x=5, y=5: SW arc hex at x=4, y=5
        assertEquals(17, MapUtils.getBearing(at(5, 5), at(4, 5)));
    }

    // ── getRelativeBearing / getTrueBearing ────────────────────────────────────

    @Test
    public void testRelativeBearingFacingNorth() {
        // Ship facing 1 (N): relative = true
        assertEquals(5, MapUtils.getRelativeBearing(5, 1));
        assertEquals(13, MapUtils.getRelativeBearing(13, 1));
    }

    @Test
    public void testRelativeBearingFacingSouth() {
        // Ship facing 13 (S): true bearing 1 (N) is directly behind → relative 12
        assertEquals(13, MapUtils.getRelativeBearing(1, 13));
    }

    @Test
    public void testTrueBearingFacingNorth() {
        assertEquals(9, MapUtils.getTrueBearing(9, 1));
    }

    @Test
    public void testTrueBearingFacingEast() {
        // Facing 5 (NE): relative 13 (straight ahead) → true 17
        assertEquals(17, MapUtils.getTrueBearing(13, 5));
    }

    @Test
    public void testTrueBearingWrapAround() {
        // Facing 21 (NW): relative 9 → true 29 % 24 = 5
        assertEquals(5, MapUtils.getTrueBearing(9, 21));
    }

    @Test
    public void testRelativeAndTrueBearingAreInverse() {
        // getTrueBearing(getRelativeBearing(b, f), f) == b  for cardinal facings
        int[] facings = {1, 5, 9, 13, 17, 21};
        int[] bearings = {1, 4, 5, 9, 10, 13, 17, 21};
        for (int f : facings) {
            for (int b : bearings) {
                int rel = MapUtils.getRelativeBearing(b, f);
                int back = MapUtils.getTrueBearing(rel, f);
                assertEquals("facing=" + f + " bearing=" + b, b, back);
            }
        }
    }

    // ── getAbsoluteShieldFacing ────────────────────────────────────────────────

    @Test
    public void testAbsoluteShieldFacingNorth() {
        // Target directly above → shield facing 1
        assertEquals(1, MapUtils.getAbsoluteShieldFacing(at(5, 5), at(5, 2)));
    }

    @Test
    public void testAbsoluteShieldFacingSouth() {
        assertEquals(7, MapUtils.getAbsoluteShieldFacing(at(5, 5), at(5, 9)));
    }

    @Test
    public void testAbsoluteShieldFacingEast() {
        // Even xDiff, same y → 4
        assertEquals(4, MapUtils.getAbsoluteShieldFacing(at(5, 5), at(7, 5)));
    }

    @Test
    public void testAbsoluteShieldFacingWest() {
        assertEquals(10, MapUtils.getAbsoluteShieldFacing(at(7, 5), at(5, 5)));
    }

    @Test
    public void testAbsoluteShieldFacingNortheast() {
        // From odd x=5, y=5: adjacent NE hex at (6,4) is below the NE spine (6,3),
        // so it falls in the shield 2 zone → facing 3
        assertEquals(3, MapUtils.getAbsoluteShieldFacing(at(5, 5), at(6, 4)));
    }

    @Test
    public void testAbsoluteShieldFacingNESpine() {
        // The actual NE spine hex from odd x=5, y=5 is at (6,3) → facing 2 (border 1/2)
        assertEquals(2, MapUtils.getAbsoluteShieldFacing(at(5, 5), at(6, 3)));
    }

    @Test
    public void testAbsoluteShieldFacingNorthwest() {
        // From odd x=5, y=5: adjacent NW hex at (4,4) is below the NW spine (4,3),
        // so it falls in the shield 6 zone → facing 11
        assertEquals(11, MapUtils.getAbsoluteShieldFacing(at(5, 5), at(4, 4)));
    }

    @Test
    public void testAbsoluteShieldFacingNWSpine() {
        // The actual NW spine hex from odd x=5, y=5 is at (4,3) → facing 12 (border 6/1)
        assertEquals(12, MapUtils.getAbsoluteShieldFacing(at(5, 5), at(4, 3)));
    }

    // ── getRelativeShieldFacing ────────────────────────────────────────────────

    @Test
    public void testRelativeShieldFacingNorthFacingNorth() {
        // Absolute 1 (N), ship facing 1: relative = 1
        assertEquals(1, MapUtils.getRelativeShieldFacing(1, 1));
    }

    @Test
    public void testRelativeShieldFacingNorthFacingSouth() {
        // Absolute 1 (N), ship facing 13 (S): attacker is behind → relative 7
        assertEquals(7, MapUtils.getRelativeShieldFacing(1, 13));
    }

    @Test
    public void testRelativeShieldFacingEastFacingNorth() {
        // Absolute 5 (SE), ship facing 1: relative 5
        assertEquals(5, MapUtils.getRelativeShieldFacing(5, 1));
    }

    @Test
    public void testRelativeShieldFacingAllSixFacings() {
        // Absolute shield facing 1 (N), ship rotated through all 6 facings
        // Each 60° rotation shifts the relative facing by 2 positions
        assertEquals(1,  MapUtils.getRelativeShieldFacing(1, 1));
        assertEquals(11, MapUtils.getRelativeShieldFacing(1, 5));
        assertEquals(9,  MapUtils.getRelativeShieldFacing(1, 9));
        assertEquals(7,  MapUtils.getRelativeShieldFacing(1, 13));
        assertEquals(5,  MapUtils.getRelativeShieldFacing(1, 17));
        assertEquals(3,  MapUtils.getRelativeShieldFacing(1, 21));
    }

    // ── getAdjacentHex ─────────────────────────────────────────────────────────

    @Test
    public void testAdjacentNorth() {
        Location result = MapUtils.getAdjacentHex(new Location(5, 5), 1);
        assertEquals(5, result.getX());
        assertEquals(4, result.getY());
    }

    @Test
    public void testAdjacentSouth() {
        Location result = MapUtils.getAdjacentHex(new Location(5, 5), 13);
        assertEquals(5, result.getX());
        assertEquals(6, result.getY());
    }

    @Test
    public void testAdjacentNEFromOddColumn() {
        // Odd x=5: bearing 5 → x+1, y-1
        Location result = MapUtils.getAdjacentHex(new Location(5, 5), 5);
        assertEquals(6, result.getX());
        assertEquals(4, result.getY());
    }

    @Test
    public void testAdjacentNEFromEvenColumn() {
        // Even x=6: bearing 5 → x+1, y (no y shift)
        Location result = MapUtils.getAdjacentHex(new Location(6, 5), 5);
        assertEquals(7, result.getX());
        assertEquals(5, result.getY());
    }

    @Test
    public void testAdjacentSEFromOddColumn() {
        // Odd x=5: bearing 9 → x+1, y
        Location result = MapUtils.getAdjacentHex(new Location(5, 5), 9);
        assertEquals(6, result.getX());
        assertEquals(5, result.getY());
    }

    @Test
    public void testAdjacentSEFromEvenColumn() {
        // Even x=6: bearing 9 → x+1, y+1
        Location result = MapUtils.getAdjacentHex(new Location(6, 5), 9);
        assertEquals(7, result.getX());
        assertEquals(6, result.getY());
    }

    @Test
    public void testAdjacentSWFromOddColumn() {
        // Odd x=5: bearing 17 → x-1, y
        Location result = MapUtils.getAdjacentHex(new Location(5, 5), 17);
        assertEquals(4, result.getX());
        assertEquals(5, result.getY());
    }

    @Test
    public void testAdjacentSWFromEvenColumn() {
        // Even x=6: bearing 17 → x-1, y+1
        Location result = MapUtils.getAdjacentHex(new Location(6, 5), 17);
        assertEquals(5, result.getX());
        assertEquals(6, result.getY());
    }

    @Test
    public void testAdjacentNWFromOddColumn() {
        // Odd x=5: bearing 21 → x-1, y-1
        Location result = MapUtils.getAdjacentHex(new Location(5, 5), 21);
        assertEquals(4, result.getX());
        assertEquals(4, result.getY());
    }

    @Test
    public void testAdjacentNWFromEvenColumn() {
        // Even x=6: bearing 21 → x-1, y
        Location result = MapUtils.getAdjacentHex(new Location(6, 5), 21);
        assertEquals(5, result.getX());
        assertEquals(5, result.getY());
    }

    @Test
    public void testAdjacentInvalidBearingReturnsNull() {
        Location result = MapUtils.getAdjacentHex(new Location(5, 5), 3);
        assertEquals(null, result);
    }
}
