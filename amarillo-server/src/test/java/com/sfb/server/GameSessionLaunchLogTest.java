package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Launches produce a REDACTED public announcement in the shared combat log —
 * the event is visible to all, the type is not. The detailed message goes
 * only to the actor via the action response.
 */
class GameSessionLaunchLogTest {

    private static final String HOST = "token-host";

    private GameSession session;
    private Game game;
    private Ship fed;
    private Ship klingon;

    @BeforeEach
    void setUp() {
        session = new GameSession("game-1", HOST, "Alice");
        game = session.getGame();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(30, 20));
        klingon.setFacing(1);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
        game.submitAllocation(fed,     makeAllocation(fed));
        game.submitAllocation(klingon, makeAllocation(klingon));

        // Advance to the Activity phase for launches
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        session.drainCombatLog(); // discard phase noise from setup
    }

    @Test
    void shuttleLaunch_publicLineIsRedacted_detailStaysPrivate() {
        String bayShuttle = fed.getShuttles().getBays().get(0).getInventory().get(0).getName();
        ActionRequest req = new ActionRequest();
        req.setType("LAUNCH_SHUTTLE");
        req.setShipName("USS Enterprise");
        req.setPlayerToken(HOST);
        req.setAction(bayShuttle);
        req.setSpeed(4);
        req.setRange(1); // facing rides the range field

        ActionResult r = session.executeAction(req);
        assertTrue(r.isSuccess(), r.getMessage());

        // Private response carries the detail (the anonymous launch name)
        assertTrue(r.getMessage().contains("launched shuttle USS Enterprise-Shuttle-"),
                r.getMessage());

        // Shared log carries only the redacted announcement
        List<String> log = session.drainCombatLog();
        assertEquals(1, log.size());
        assertEquals("USS Enterprise launched a shuttle", log.get(0));
    }

    private Energy makeAllocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(0.0);
        return e;
    }
}
