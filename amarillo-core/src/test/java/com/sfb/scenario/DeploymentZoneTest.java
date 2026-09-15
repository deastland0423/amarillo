package com.sfb.scenario;

import com.sfb.properties.Location;
import com.sfb.utilities.MapUtils;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The ground a fleet may set up on. Off-map is never legal whatever the shape, and a circle is
 * a circle in hexes rather than in squares — which is why it asks MapUtils rather than doing
 * its own arithmetic on the coordinates.
 */
public class DeploymentZoneTest {

    private static final int COLS = 42, ROWS = 32;

    @Test
    public void anywhereIsTheWholeMapAndNoMoreThanThat() {
        DeploymentZone z = DeploymentZone.anywhere();

        assertTrue(z.contains(1, 1, COLS, ROWS));
        assertTrue(z.contains(COLS, ROWS, COLS, ROWS));
        assertFalse("off the right edge", z.contains(COLS + 1, 10, COLS, ROWS));
        assertFalse("off the bottom", z.contains(10, ROWS + 1, COLS, ROWS));
        assertFalse("column zero is not a hex", z.contains(0, 10, COLS, ROWS));
    }

    // ---- circle ----

    @Test
    public void aCircleReachesItsRadiusAndStops() {
        DeploymentZone z = DeploymentZone.circle("2016", 3);

        assertTrue("the centre itself", z.contains(20, 16, COLS, ROWS));
        assertTrue(z.contains(23, 16, COLS, ROWS));
        assertFalse("one hex too far", z.contains(24, 16, COLS, ROWS));
    }

    /**
     * The point of asking MapUtils: hex distance is not the square distance the coordinates
     * suggest, and odd and even columns differ.
     */
    @Test
    public void aCircleIsMeasuredInHexesNotInCoordinates() {
        DeploymentZone z = DeploymentZone.circle("2016", 2);
        Location centre = new Location(20, 16);

        for (int col = 16; col <= 24; col++)
            for (int row = 12; row <= 20; row++) {
                boolean withinRange = MapUtils.getRange(centre, new Location(col, row)) <= 2;
                assertEquals("hex " + col + "," + row,
                        withinRange, z.contains(col, row, COLS, ROWS));
            }
    }

    @Test
    public void aRadiusOfZeroIsTheOneHex() {
        DeploymentZone z = DeploymentZone.circle("2016", 0);

        assertTrue(z.contains(20, 16, COLS, ROWS));
        assertFalse(z.contains(20, 17, COLS, ROWS));
    }

    // ---- band ----

    @Test
    public void aBandCountsInFromItsOwnEdge() {
        DeploymentZone left = DeploymentZone.band("LEFT", 6);
        assertTrue(left.contains(1, 16, COLS, ROWS));
        assertTrue("the sixth column is still in", left.contains(6, 16, COLS, ROWS));
        assertFalse(left.contains(7, 16, COLS, ROWS));

        DeploymentZone right = DeploymentZone.band("RIGHT", 6);
        assertTrue(right.contains(COLS, 16, COLS, ROWS));
        assertTrue(right.contains(COLS - 5, 16, COLS, ROWS));
        assertFalse(right.contains(COLS - 6, 16, COLS, ROWS));
    }

    @Test
    public void topAndBottomBandsCountRows() {
        assertTrue(DeploymentZone.band("TOP", 4).contains(20, 4, COLS, ROWS));
        assertFalse(DeploymentZone.band("TOP", 4).contains(20, 5, COLS, ROWS));

        assertTrue(DeploymentZone.band("BOTTOM", 4).contains(20, ROWS - 3, COLS, ROWS));
        assertFalse(DeploymentZone.band("BOTTOM", 4).contains(20, ROWS - 4, COLS, ROWS));
    }

    /** Opposing bands of six on a 42-wide map leave thirty hexes between them. */
    @Test
    public void opposingBandsDoNotMeet() {
        DeploymentZone left = DeploymentZone.band("LEFT", 6);
        DeploymentZone right = DeploymentZone.band("RIGHT", 6);

        for (int col = 1; col <= COLS; col++)
            assertFalse("no hex belongs to both: column " + col,
                    left.contains(col, 16, COLS, ROWS) && right.contains(col, 16, COLS, ROWS));
    }

    // ---- box ----

    @Test
    public void aBoxCoversItsCornersEitherWayRound() {
        DeploymentZone z = DeploymentZone.box("0510", "1020");

        assertTrue(z.contains(5, 10, COLS, ROWS));
        assertTrue(z.contains(10, 20, COLS, ROWS));
        assertTrue(z.contains(7, 15, COLS, ROWS));
        assertFalse(z.contains(4, 15, COLS, ROWS));
        assertFalse(z.contains(7, 21, COLS, ROWS));

        DeploymentZone reversed = DeploymentZone.box("1020", "0510");
        assertTrue("corners given the other way round", reversed.contains(7, 15, COLS, ROWS));
    }

    // ---- malformed input is refused, not guessed at ----

    @Test
    public void aZoneWithNothingToMeasureAgainstContainsNothing() {
        assertFalse(DeploymentZone.circle(null, 3).contains(20, 16, COLS, ROWS));
        assertFalse(DeploymentZone.circle("nonsense", 3).contains(20, 16, COLS, ROWS));
        assertFalse(DeploymentZone.band("SIDEWAYS", 3).contains(20, 16, COLS, ROWS));
        assertFalse(DeploymentZone.box("0510", null).contains(7, 15, COLS, ROWS));
    }

    @Test
    public void eachShapeCanSayWhatItExpected() {
        assertTrue(DeploymentZone.circle("2016", 3).describe().contains("2016"));
        assertTrue(DeploymentZone.band("LEFT", 6).describe().contains("left"));
        assertTrue(DeploymentZone.box("0510", "1020").describe().contains("0510"));
        assertTrue(DeploymentZone.anywhere().describe().contains("anywhere"));
    }
}
