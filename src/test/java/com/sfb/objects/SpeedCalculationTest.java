package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.sfb.samples.SampleShips;

/**
 * Verifies that the warp/impulse speed rules are applied correctly:
 *   - Warp energy drives speed up to 30 (each moveCost energy = 1 hex)
 *   - 1 impulse point buys exactly +1 speed at flat cost (allowing speed 31)
 *   - No ship can exceed speed 31
 */
public class SpeedCalculationTest {

    /** Clone the D7 spec and override movement-related fields for isolation. */
    private Map<String, Object> spec(int lWarp, int rWarp, int impulse, double moveCost) {
        Map<String, Object> m = new HashMap<>(SampleShips.getD7());
        m.put("lwarp",   lWarp);
        m.put("rwarp",   rWarp);
        m.put("impulse", impulse);
        m.put("apr",     0);           // remove APR so only warp+impulse are in play
        m.put("movecost", moveCost);
        m.put("weapons", new ArrayList<>());  // no weapons — simplifies setup
        return m;
    }

    private Ship buildAndStart(Map<String, Object> spec) {
        Ship ship = new Ship();
        ship.init(spec);
        ship.allocateEnergy(ship.buildAutoAllocation());
        ship.startTurn();
        return ship;
    }

    // -------------------------------------------------------------------------
    // Warp energy caps at speed 30
    // -------------------------------------------------------------------------

    @Test
    public void warpAloneGivesUpToSpeed30() {
        // 15+15 warp, 0 impulse, moveCost=1 → exactly 30
        Ship ship = buildAndStart(spec(15, 15, 0, 1.0));
        assertEquals(30, ship.getSpeed());
    }

    @Test
    public void excessWarpIsCappedAtSpeed30() {
        // 20+20 warp, 0 impulse, moveCost=1 → would be 40 uncapped; capped at 30
        Ship ship = buildAndStart(spec(20, 20, 0, 1.0));
        assertEquals(30, ship.getSpeed());
    }

    @Test
    public void warpCappedWithFractionalMoveCost() {
        // moveCost=0.5, 10+10 warp, 0 impulse → warpMovement=min(20,15)=15, speed=15/0.5=30
        Ship ship = buildAndStart(spec(10, 10, 0, 0.5));
        assertEquals(30, ship.getSpeed());
    }

    // -------------------------------------------------------------------------
    // 1 impulse point = flat +1 speed regardless of moveCost
    // -------------------------------------------------------------------------

    @Test
    public void oneImpulsePointGivesPlusOneSpeed() {
        // 30 warp, 1 impulse, moveCost=1 → speed 31
        Ship ship = buildAndStart(spec(15, 15, 1, 1.0));
        assertEquals(31, ship.getSpeed());
    }

    @Test
    public void impulseIsFlatPlusOneWithHalfMoveCost() {
        // moveCost=0.5, 8+7 warp, 1 impulse → warpSpeed=30, +1 impulse = 31
        Ship ship = buildAndStart(spec(8, 7, 1, 0.5));
        assertEquals(31, ship.getSpeed());
    }

    @Test
    public void impulseIsFlatPlusOneWithOneThirdMoveCost() {
        // moveCost=1/3, 5+5 warp, 1 impulse → warpMovement=min(10,10)=10, speed=30, +1=31
        Ship ship = buildAndStart(spec(5, 5, 1, 1.0 / 3.0));
        assertEquals(31, ship.getSpeed());
    }

    @Test
    public void noImpulseCannotReachSpeed31() {
        // moveCost=0.5, lots of warp, 0 impulse → max is 30
        Ship ship = buildAndStart(spec(20, 20, 0, 0.5));
        assertEquals(30, ship.getSpeed());
    }

    // -------------------------------------------------------------------------
    // Hard cap: speed never exceeds 31
    // -------------------------------------------------------------------------

    @Test
    public void speedNeverExceeds31WithExcessWarpAndImpulse() {
        // 25+25 warp, 5 impulse, moveCost=1 → would be far above 31 uncapped
        Ship ship = buildAndStart(spec(25, 25, 5, 1.0));
        assertEquals(31, ship.getSpeed());
    }

    // -------------------------------------------------------------------------
    // Partial speeds — ensure the formula works below the cap
    // -------------------------------------------------------------------------

    @Test
    public void lowWarpProducesCorrectPartialSpeed() {
        // moveCost=1, 4+4 warp, 0 impulse → speed 8
        Ship ship = buildAndStart(spec(4, 4, 0, 1.0));
        assertEquals(8, ship.getSpeed());
    }

    @Test
    public void lowWarpPlusImpulseAddsOne() {
        // moveCost=1, 4+4 warp, 1 impulse → speed 9
        Ship ship = buildAndStart(spec(4, 4, 1, 1.0));
        assertEquals(9, ship.getSpeed());
    }
}
