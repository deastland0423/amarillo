package com.sfb.objects;

import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Movement costs are thirds and quarters. The server charges for a speed by multiplying
 * (GameSession: energy = speed x cost) and the ship reads it back by dividing, and neither
 * number is exact in binary — so the round trip lands a hair either side of the speed asked
 * for. Truncating the low side used to cost a hex: a war cruiser paying for speed 7 flew at 6,
 * and a frigate written as 0.33333333 could not reach speed 30 at all.
 * <p>
 * These pin the round trip at every speed, for every movement cost the ship files use.
 */
public class MovementCostRoundingTest {

    private Ship shipCosting(double moveCost) {
        Map<String, Object> m = new HashMap<>(KlingonShips.getD7());
        m.put("movecost", moveCost);
        m.put("weapons", new ArrayList<>());
        Ship ship = new Ship();
        ship.init(m);
        // C2.2 acceleration history, so the speed cap never masks a rounding fault
        ship.setSpeedPreviousTurn(31);
        ship.setSpeedTwoTurnsAgo(31);
        return ship;
    }

    /** Every speed a ship can buy comes back as the speed it paid for. */
    private void assertRoundTrip(double moveCost) {
        for (int speed = 1; speed <= 30; speed++) {
            Ship ship = shipCosting(moveCost);
            Energy e = new Energy();
            e.setWarpMovement(speed * moveCost);   // exactly what GameSession charges
            ship.allocateEnergy(e);
            ship.startTurn();

            assertEquals("cost " + moveCost + ", paid for speed " + speed,
                    speed, ship.getSpeed());
        }
    }

    @Test
    public void warCruisersGetTheSpeedTheyPayFor() {
        assertRoundTrip(2.0 / 3.0);
        assertRoundTrip(0.6666667);        // the spellings the ship files use
        assertRoundTrip(0.6666666667);
        assertRoundTrip(0.666666667);
    }

    @Test
    public void frigatesGetTheSpeedTheyPayFor() {
        assertRoundTrip(1.0 / 3.0);
        assertRoundTrip(0.3333333);
        assertRoundTrip(0.333333333);
        assertRoundTrip(0.3333333333);
        assertRoundTrip(0.33333333);
    }

    @Test
    public void theExactCostsAreUnaffected() {
        assertRoundTrip(0.75);
        assertRoundTrip(0.5);
        assertRoundTrip(1.0);
        assertRoundTrip(1.5);
        assertRoundTrip(2.0);
    }

    /** The tolerance must not gift movement: short of a full hex still rounds down. */
    @Test
    public void energyShortOfAHexBuysNothingExtra() {
        for (double cost : new double[] { 2.0 / 3.0, 0.6666667, 0.75, 0.5, 1.0 / 3.0 }) {
            Ship ship = shipCosting(cost);
            Energy e = new Energy();
            e.setWarpMovement(3.999 * cost);   // very nearly a fourth hex, but not quite
            ship.allocateEnergy(e);
            ship.startTurn();

            assertEquals("cost " + cost + " must not round a partial hex up", 3, ship.getSpeed());
        }
    }
}
