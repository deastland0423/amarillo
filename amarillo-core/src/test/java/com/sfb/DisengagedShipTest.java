package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A ship that has disengaged (C7.0) has left the battle: it should neither be
 * asked for an energy allocation nor scheduled to move. Reported from live
 * play — a disengaged ship still required an EAF, and then stalled the movement
 * phase when its (pointlessly-allocated) speed came up on the impulse chart.
 */
public class DisengagedShipTest {

    private Game game;
    private Ship engaged;
    private Ship gone;

    @Before
    public void setUp() {
        game = new Game();

        engaged = new Ship();
        engaged.init(FederationShips.getFedCa());
        engaged.setName("Engaged");
        engaged.setLocation(new Location(10, 10));
        engaged.setFacing(1);

        gone = new Ship();
        gone.init(KlingonShips.getD7());
        gone.setName("Gone");
        gone.setFacing(1);
        gone.setDisengaged(true);   // left the map
        gone.setLocation(null);

        game.getShips().add(engaged);
        game.getShips().add(gone);
    }

    @Test
    public void disengagedShip_isNotAskedForAllocation() {
        game.startTurn();

        assertTrue(game.getAllocationQueue().contains(engaged));
        assertFalse("a disengaged ship should not need an allocation",
                game.getAllocationQueue().contains(gone));
        assertEquals(1, game.getAllocationQueue().size());
    }

    @Test
    public void disengagedShip_isNeverScheduledToMove() {
        // Even with a speed that would otherwise put it on the impulse chart.
        gone.setSpeed(16);

        game.startTurn();
        Energy e = new Energy();
        e.setWarpMovement(16.0);
        game.submitAllocation(engaged, e); // last engaged ship → impulses begin

        assertFalse("no longer waiting on allocations", game.isAwaitingAllocation());

        // Sweep the whole turn: the disengaged ship must never be movable, so it
        // can never stall the movement phase.
        for (int i = 0; i < 40; i++) {
            assertFalse("disengaged ship must not be movable",
                    game.getMovableShips().contains(gone));
            game.advancePhase();
        }
    }
}
