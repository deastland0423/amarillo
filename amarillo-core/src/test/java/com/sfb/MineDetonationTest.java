package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.SpaceMine;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.utilities.MapUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A ship that slides past an adjacent T-bomb — moving from one in-range hex to
 * another — must still trigger it (M2.5). Reproduces a live-game report: a
 * speed-19 ship at 1005 moved to 0906 while a T-bomb sat at 1006 (both hexes
 * range 1) and the bomb failed to detonate, because detection wrongly skipped
 * units that were "already in range".
 */
public class MineDetonationTest {

    private Game game;
    private Ship target;
    private Ship layer;

    @Before
    public void setUp() {
        game = new Game();

        // Mine layer, parked far away so it never triggers its own bomb.
        layer = new Ship();
        layer.init(FederationShips.getFedCa());
        layer.setName("Layer");
        layer.setLocation(new Location(20, 20));
        game.getShips().add(layer);

        target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("Target");
        target.setLocation(new Location(10, 5)); // 1005
        target.setFacing(17);                    // bearing E → forward = lower-left
        game.getShips().add(target);

        // An already-active real T-bomb at 1006.
        SpaceMine mine = SpaceMine.createTBomb(layer, 0, true, false);
        mine.setLocation(new Location(10, 6));
        mine.tryActivate(2, 9); // timer met + layer out of zone → armed
        assertTrue("mine should be active for the test", mine.isActive());
        game.getMines().add(mine);

        // Both the start (1005) and the destination (0906) are adjacent to 1006.
        assertEquals(1, MapUtils.getRange(new Location(10, 6), new Location(10, 5)));
        assertEquals(1, MapUtils.getRange(new Location(10, 6), new Location(9, 6)));

        game.startTurn();
    }

    private Energy allocation(Ship ship, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warp);
        return e;
    }

    private void advanceUntilCanMove(Ship ship) {
        for (int guard = 0; guard < 60; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT && game.canMoveThisImpulse(ship))
                return;
            game.advancePhase();
        }
        fail("Target never became movable");
    }

    @Test
    public void slidingFromOneAdjacentHexToAnother_detonatesTheMine() {
        game.submitAllocation(layer,  allocation(layer, 0.0)); // both ships must allocate to start impulses
        game.submitAllocation(target, allocation(target, 19.0));
        assertTrue("speed high enough for automatic detection (M2.5)", target.getSpeed() >= 6);

        advanceUntilCanMove(target);
        assertTrue(game.moveForward(target).isSuccess());
        assertEquals("moved 1005 → 0906", new Location(9, 6), target.getLocation());

        // Leaving MOVEMENT runs mine processing for this impulse.
        game.advancePhase();

        assertTrue("T-bomb should have detonated as the ship slid past it",
                game.getMines().isEmpty());
    }
}
