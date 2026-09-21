package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * P3.25 clearing a path: firing into an asteroid hex you are about to enter, so the rocks do
 * less damage when you get there.
 * <p>
 * The whole rule is the timing. Because of the Sequence of Play the shot lands on the impulse
 * before the move, so the benefit is good only if the firing ship enters that very hex on the
 * very next impulse - it is lost if the ship goes anywhere else first, spent on the first
 * entry, never shared with another ship (P3.253), and never carried to another hex (P3.251).
 * <p>
 * The reduction itself is exercised through {@code Game.applyTerrainCollision} at a fixed
 * cleared amount rather than through a live phaser, because phaser damage is rolled and the
 * dice have no seam to seed. What the fire scores is the rolled part; what the clearance does
 * with it is not, and that is what these pin.
 */
public class AsteroidClearPathTest {

    private Game game;
    private Ship fed;

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeed(20);           // speed bracket 15-25, where the table bites
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        fed.setActiveFireControl(true);
        game.getShips().add(fed);

        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));

        game.startTurn();
        // The impulse clock does not run until allocation is in — without this every
        // "wait for the next impulse" below would spin forever.
        com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
        e.setLifeSupport(fed.getLifeSupportCost());
        e.setFireControl(fed.getFireControlCost());
        e.setActivateShields(fed.getActiveShieldCost());
        e.setWarpMovement(0.0);
        game.submitAllocation(fed, e);
    }

    /** Advance until the absolute impulse has moved on by {@code n}. Bounded, never hangs. */
    private void waitImpulses(int n) {
        int target = game.getAbsoluteImpulse() + n;
        for (int guard = 0; guard < 2000; guard++) {
            if (game.getAbsoluteImpulse() >= target)
                return;
            game.advancePhase();
        }
        fail("impulse clock never reached " + target
                + " (stuck at " + game.getAbsoluteImpulse() + ", phase " + game.getCurrentPhase() + ")");
    }

    /** Put the ship in the hex it is deemed to have just entered. */
    private void arriveAt(Location hex) {
        fed.setLocation(hex);
    }

    // ---------------------------------------------------------------- the rulebook example

    /**
     * The worked example from P3.251: a cruiser fires a phaser-1 into an asteroid hex and
     * scores 8. On the next impulse it enters, and the die indicates 10 points. "This is
     * reduced to 2 by the phaser fire."
     */
    @Test
    public void rulebookExample_eightClearedOffATenRoll_leavesTwo() {
        Location target = new Location(10, 9);
        game.recordAsteroidClearance(fed, target, 8);

        waitImpulses(1);             // the next impulse, when it enters

        arriveAt(target);
        String line = game.applyTerrainCollision(fed);

        assertTrue("expected a collision line: " + line, line.contains("enters asteroid hex"));
        assertTrue("the fire must be credited (P3.25): " + line, line.contains("cleared by fire"));

        // The die is unseedable, but the log carries both sides of the subtraction, so the
        // arithmetic can be checked exactly whatever it rolled: rolled 10 leaves 2, and the
        // floor at zero holds for the lighter rolls.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+) \u2192 (\\d+)").matcher(line);
        assertTrue("expected a 'rolled \u2192 taken' figure in: " + line, m.find());
        int rolled = Integer.parseInt(m.group(1));
        int taken = Integer.parseInt(m.group(2));

        assertEquals("P3.251: each point cleared takes one point off, floored at zero"
                        + " (rolled " + rolled + ")",
                Math.max(0, rolled - 8), taken);
    }

    // ---------------------------------------------------------------- the timing

    @Test
    public void clearanceIsLost_ifTheShipEntersAnyOtherHexFirst() {
        Location target = new Location(10, 9);
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 11, 10)); // a different rock
        game.recordAsteroidClearance(fed, target, 8);

        // It goes somewhere else instead — P3.25: "the benefit is lost if the firing unit
        // enters any other hex before entering the hex into which it fired".
        arriveAt(new Location(11, 10));
        game.applyTerrainCollision(fed);

        assertEquals("entering another hex must void the clearance",
                0, game.pendingAsteroidClearance(fed, target));
    }

    @Test
    public void clearanceIsVoid_ifNotTheImpulseImmediatelyPrior() {
        Location target = new Location(10, 9);
        game.recordAsteroidClearance(fed, target, 8);

        // Let TWO impulses pass — P3.25: "The fire will have no effect if not on the impulse
        // immediately prior to entry."
        waitImpulses(2);

        arriveAt(target);
        String line = game.applyTerrainCollision(fed);

        assertTrue("expected a collision line: " + line, line.contains("enters asteroid hex"));
        assertFalse("fire two impulses early must do nothing (P3.25): " + line,
                line.contains("cleared by fire"));
    }

    @Test
    public void clearanceIsSpentOnce_notOnEveryEntry() {
        Location target = new Location(10, 9);
        game.recordAsteroidClearance(fed, target, 8);
        waitImpulses(1);

        arriveAt(target);
        game.applyTerrainCollision(fed);   // spends it

        assertEquals("P3.25: the benefit applies only to the first entry",
                0, game.pendingAsteroidClearance(fed, target));
    }

    @Test
    public void oneShipCannotClearAPathForAnother() {
        Location target = new Location(10, 9);
        Ship other = new Ship();
        other.init(FederationShips.getFedCa());
        other.setName("USS Hood");
        other.setFacing(1);
        other.setSpeed(20);
        other.setSpeedPreviousTurn(31);
        other.setSpeedTwoTurnsAgo(31);
        game.getShips().add(other);

        game.recordAsteroidClearance(fed, target, 8);
        waitImpulses(1);

        other.setLocation(target);
        String line = game.applyTerrainCollision(other);

        assertTrue("expected a collision line: " + line, line.contains("enters asteroid hex"));
        assertFalse("P3.253: a path cleared by one ship does nothing for another: " + line,
                line.contains("cleared by fire"));
    }

    @Test
    public void clearanceDoesNotCarryOverToAnotherHex() {
        Location fired = new Location(10, 9);
        Location entered = new Location(11, 9);
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 11, 9));
        game.recordAsteroidClearance(fed, fired, 8);
        waitImpulses(1);

        arriveAt(entered);
        String line = game.applyTerrainCollision(fed);

        assertTrue("expected a collision line: " + line, line.contains("enters asteroid hex"));
        assertFalse("P3.251: there is no carry-over to other hexes: " + line,
                line.contains("cleared by fire"));
    }

    // ---------------------------------------------------------------- accumulation and ECM

    @Test
    public void severalWeaponsIntoTheSameHexOnOneImpulse_addUp() {
        Location target = new Location(10, 9);
        game.recordAsteroidClearance(fed, target, 5);
        game.recordAsteroidClearance(fed, target, 3);

        assertEquals("fire from several weapons on one impulse accumulates",
                8, game.pendingAsteroidClearance(fed, target));
    }

    /**
     * P3.25: "The target asteroid hex does not provide itself any ECM benefit, but hexes
     * fired from or through will." Shooting the rock in front of you is not made harder by
     * that rock — but it IS made harder by rocks you shoot across.
     */
    @Test
    public void theTargetHexGivesItselfNoEcm() {
        Location shooter = new Location(10, 12);
        Location target = new Location(10, 9);

        int plain = game.terrainEcmAlongLine(shooter, target);
        int clearing = game.terrainEcmForClearingFire(shooter, target);

        assertTrue("the ordinary count includes the target hex", plain >= 1);
        assertEquals("the target hex must be exempt when it IS the target (P3.25)",
                plain - 1, clearing);
    }

    @Test
    public void hexesFiredThroughStillCount() {
        // Rocks between the shooter and the target hex.
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 10));
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 11));
        Location shooter = new Location(10, 12);
        Location target = new Location(10, 9);

        assertTrue("hexes fired through still give ECM (P3.25/P3.33)",
                game.terrainEcmForClearingFire(shooter, target) >= 1);
    }

    // ---------------------------------------------------------------- the firing action

    @Test
    public void firingAtAHexWithNoAsteroids_isRefused() {
        advanceToDirectFire();
        Game.ActionResult r = game.clearAsteroidPath(fed, new Location(20, 20), java.util.List.of());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("no asteroids in that hex"));
    }

    @Test
    public void firingOutsideTheDirectFirePhase_isRefused() {
        // startTurn leaves us in allocation, well short of a Direct Fire phase.
        Game.ActionResult r = game.clearAsteroidPath(fed, new Location(10, 9), java.util.List.of());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("Direct Fire phase"));
    }

    private void advanceToDirectFire() {
        for (int guard = 0; guard < 400; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.DIRECT_FIRE)
                return;
            game.advancePhase();
        }
        fail("never reached a Direct Fire phase");
    }
}
