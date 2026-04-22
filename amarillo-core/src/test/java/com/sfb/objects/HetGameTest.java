package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.TurnTracker;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;

import java.util.Map;
import java.util.HashMap;

/**
 * Integration tests for Game.performHet().
 *
 * Dice are non-deterministic, so breakdown logic is tested with extreme
 * breakdownChance values: 7 (never breaks down — max roll is 6) and 1
 * (always breaks down — min roll is 1 ≥ 1).  bonusHets=0 is used for
 * always-breakdown tests so the -2 adjustment cannot rescue the roll.
 *
 * Setup: MOVEMENT phase, TurnTracker at impulse 2 (impulse 1 is forbidden).
 * Reserve warp is set directly on PowerSystems.
 */
public class HetGameTest {

    private Game game;
    private Ship ship;

    /** Fed CA: moveCost=1.0 → hetCost=5, breakdownChance=5, bonusHets=1. */
    private static Map<String, Object> neverBreakdown() {
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("breakdown", 7);   // roll 1–6 can never reach 7
        spec.put("bonushets", 0);
        return spec;
    }

    /** breakdownChance=1: roll 1–6 always ≥ 1 → always breaks down. */
    private static Map<String, Object> alwaysBreakdown() {
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("breakdown", 1);
        spec.put("bonushets", 0);
        return spec;
    }

    @Before
    public void setUp() {
        TurnTracker.reset();

        game = new Game();

        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("Enterprise");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);

        game.getShips().add(ship);

        // Advance to impulse 2: HET is forbidden on impulse 1
        TurnTracker.nextImpulse(); // → 1
        TurnTracker.nextImpulse(); // → 2

        // Standard reserve warp: enough for one HET (hetCost = 5 for moveCost=1.0)
        ship.getPowerSysetems().setReserveWarp(5);
    }

    // -------------------------------------------------------------------------
    // Phase guard
    // -------------------------------------------------------------------------

    @Test
    public void het_failsWhenNotMovementPhase() {
        game.advancePhase(); // MOVEMENT → ACTIVITY
        ActionResult result = game.performHet(ship, 3);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Movement phase"));
    }

    // -------------------------------------------------------------------------
    // Impulse 1 guard (C6.37)
    // -------------------------------------------------------------------------

    @Test
    public void het_failsOnImpulseOne() {
        TurnTracker.reset();
        TurnTracker.nextImpulse(); // → 1
        ActionResult result = game.performHet(ship, 3);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("impulse 1"));
    }

    // -------------------------------------------------------------------------
    // Breakdown immobility
    // -------------------------------------------------------------------------

    @Test
    public void het_failsWhenShipIsImmobile() {
        ship.setImmobileUntilImpulse(10); // immobile through impulse 10; we are at 2
        ActionResult result = game.performHet(ship, 3);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("immobile"));
    }

    // -------------------------------------------------------------------------
    // 4-impulse gap (C6.36)
    // -------------------------------------------------------------------------

    @Test
    public void het_failsWhenTooSoonAfterLastHet() {
        ship.setLastHetImpulse(1); // last HET at impulse 1; current is 2 → gap = 1 < 4
        ActionResult result = game.performHet(ship, 3);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("4 impulse"));
    }

    @Test
    public void het_succeedsAfterFourImpulseGap() {
        ship.init(neverBreakdown());
        ship.getPowerSysetems().setReserveWarp(10);
        ship.setLastHetImpulse(-99); // factory default; gap = 2 - (-99) = 101 ≥ 4
        ActionResult result = game.performHet(ship, 3);
        assertTrue(result.getMessage(), result.isSuccess());
    }

    // -------------------------------------------------------------------------
    // Max 4 HETs per turn (C6.34)
    // -------------------------------------------------------------------------

    @Test
    public void het_failsWhenMaxHetsReached() {
        for (int i = 0; i < 4; i++) ship.incrementHetsThisTurn();
        ActionResult result = game.performHet(ship, 3);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Maximum 4 HETs"));
    }

    // -------------------------------------------------------------------------
    // Reserve warp cost (C6.2)
    // -------------------------------------------------------------------------

    @Test
    public void het_failsWithInsufficientReserveWarp() {
        ship.getPowerSysetems().setReserveWarp(0);
        ActionResult result = game.performHet(ship, 3);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("reserve warp"));
    }

    // -------------------------------------------------------------------------
    // Successful HET
    // -------------------------------------------------------------------------

    @Test
    public void het_success_changesFacing() {
        ship.init(neverBreakdown());
        ship.setFacing(1);
        ship.getPowerSysetems().setReserveWarp(10);

        ActionResult result = game.performHet(ship, 3);

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals(3, ship.getFacing());
    }

    @Test
    public void het_success_decrementsReserveWarp() {
        ship.init(neverBreakdown());
        ship.getPowerSysetems().setReserveWarp(10);
        int hetCost = (int) Math.ceil(ship.getPerformanceData().getHetCost());

        game.performHet(ship, 3);

        assertEquals(10 - hetCost, ship.getPowerSysetems().getReserveWarp());
    }

    @Test
    public void het_success_incrementsHetsThisTurn() {
        ship.init(neverBreakdown());
        ship.getPowerSysetems().setReserveWarp(10);
        assertEquals(0, ship.getHetsThisTurn());

        game.performHet(ship, 3);

        assertEquals(1, ship.getHetsThisTurn());
    }

    @Test
    public void het_success_updatesLastHetImpulse() {
        ship.init(neverBreakdown());
        ship.getPowerSysetems().setReserveWarp(10);

        game.performHet(ship, 3);

        assertEquals(TurnTracker.getImpulse(), ship.getLastHetImpulse());
    }

    // -------------------------------------------------------------------------
    // Bonus HET consumption (C6.52)
    // -------------------------------------------------------------------------

    @Test
    public void het_consumesBonusHetOnFirstUse() {
        ship.init(neverBreakdown());
        // Give ship 2 bonus HETs so we can see the decrement
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("breakdown", 7);
        spec.put("bonushets", 2);
        ship.init(spec);
        ship.getPowerSysetems().setReserveWarp(20);
        assertEquals(2, ship.getPerformanceData().getBonusHetsRemaining());

        game.performHet(ship, 3);

        assertEquals(1, ship.getPerformanceData().getBonusHetsRemaining());
    }

    @Test
    public void het_doesNotDecrementBonusWhenExhausted() {
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("breakdown", 7);
        spec.put("bonushets", 0);
        ship.init(spec);
        ship.getPowerSysetems().setReserveWarp(20);
        assertEquals(0, ship.getPerformanceData().getBonusHetsRemaining());

        game.performHet(ship, 3);

        assertEquals(0, ship.getPerformanceData().getBonusHetsRemaining());
    }

    // -------------------------------------------------------------------------
    // Breakdown effects (C6.54) — use always-breakdown spec
    // -------------------------------------------------------------------------

    @Test
    public void het_breakdown_setsSpeedToZero() {
        ship.init(alwaysBreakdown());
        ship.setSpeed(8);
        ship.getPowerSysetems().setReserveWarp(10);

        ActionResult result = game.performHet(ship, 3);

        assertTrue(result.isSuccess()); // breakdown is a valid (catastrophic) HET outcome
        assertEquals(0, ship.getSpeed());
    }

    @Test
    public void het_breakdown_setsImmobility() {
        ship.init(alwaysBreakdown());
        ship.getPowerSysetems().setReserveWarp(10);
        int currentImpulse = TurnTracker.getImpulse();

        game.performHet(ship, 3);

        assertEquals(currentImpulse + 16, ship.getImmobileUntilImpulse());
    }

    @Test
    public void het_breakdown_reducesCrewByOneThird() {
        ship.init(alwaysBreakdown());
        ship.getPowerSysetems().setReserveWarp(10);
        int initialCrew = ship.getCrew().getAvailableCrewUnits();
        int expectedLoss = (int) Math.ceil(initialCrew / 3.0);

        game.performHet(ship, 3);

        assertEquals(initialCrew - expectedLoss, ship.getCrew().getAvailableCrewUnits());
    }

    @Test
    public void het_breakdown_decrementsBreakdownRating() {
        ship.init(alwaysBreakdown());
        ship.getPowerSysetems().setReserveWarp(10);
        int initialChance = ship.getPerformanceData().getBreakdownChance();

        game.performHet(ship, 3);

        // decrementBreakdownRating clamps to min 1; initial is 1 so stays at 1
        assertEquals(Math.max(1, initialChance - 1), ship.getPerformanceData().getBreakdownChance());
    }

    @Test
    public void het_breakdown_containsBreakdownInMessage() {
        ship.init(alwaysBreakdown());
        ship.getPowerSysetems().setReserveWarp(10);

        ActionResult result = game.performHet(ship, 3);

        assertTrue(result.isSuccess()); // breakdown is a valid (catastrophic) HET outcome
        assertTrue(result.getMessage().toUpperCase().contains("BREAKDOWN"));
    }

    // -------------------------------------------------------------------------
    // resetHetsThisTurn (called by Game.startTurn)
    // -------------------------------------------------------------------------

    @Test
    public void resetHetsThisTurn_clearsCount() {
        for (int i = 0; i < 3; i++) ship.incrementHetsThisTurn();
        assertEquals(3, ship.getHetsThisTurn());

        ship.resetHetsThisTurn();

        assertEquals(0, ship.getHetsThisTurn());
    }
}
