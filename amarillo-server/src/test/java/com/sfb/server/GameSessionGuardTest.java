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

import static org.junit.jupiter.api.Assertions.*;

/**
 * ASSIGN_GUARD / REMOVE_GUARD wire actions (D7.83). Guard posts are secret:
 * the actions deliberately do NOT write to the shared combat log — the acting
 * player sees the result in the action response only.
 */
class GameSessionGuardTest {

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

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(30, 20));

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn(); // opens the Energy Allocation window (D7.83)
    }

    private ActionResult guard(String type, String targetCode, boolean commando) {
        ActionRequest req = new ActionRequest();
        req.setType(type);
        req.setShipName("USS Enterprise");
        req.setPlayerToken(HOST);
        req.setAction(targetCode);
        req.setCommando(commando);
        return session.executeAction(req);
    }

    @Test
    void assignGuard_postsAndCostsABoardingParty() {
        int before = fed.getCrew().getAvailableBoardingParties();

        ActionResult r = guard("ASSIGN_GUARD", "SENSORS", false);

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(before - 1, fed.getCrew().getAvailableBoardingParties());
        assertEquals(1, fed.getGuardPosts().totalPosted());
    }

    @Test
    void assignGuard_secretly_nothingInSharedCombatLog() {
        guard("ASSIGN_GUARD", "SENSORS", false);
        assertTrue(session.drainCombatLog().isEmpty(),
                "Guard posts must not leak into the broadcast log");
    }

    @Test
    void assignGuard_tractorBeamCode() {
        ActionResult r = guard("ASSIGN_GUARD", "TRACTOR:2", false);
        assertTrue(r.isSuccess(), r.getMessage());
        assertTrue(fed.getGuardPosts().isBeamGuarded(2));
    }

    @Test
    void assignGuard_commandoFlag_drawsFromCommandoPool() {
        int commandosBefore = fed.getCrew().getFriendlyTroops().commandos;
        if (commandosBefore == 0) {
            // No commandos aboard: the request must be refused, not silently downgraded
            assertFalse(guard("ASSIGN_GUARD", "SENSORS", true).isSuccess());
            return;
        }
        assertTrue(guard("ASSIGN_GUARD", "SENSORS", true).isSuccess());
        assertEquals(commandosBefore - 1, fed.getCrew().getFriendlyTroops().commandos);
    }

    @Test
    void removeGuard_returnsBpToRoster() {
        int before = fed.getCrew().getAvailableBoardingParties();
        guard("ASSIGN_GUARD", "SENSORS", false);

        ActionResult r = guard("REMOVE_GUARD", "SENSORS", false);

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(before, fed.getCrew().getAvailableBoardingParties());
        assertEquals(0, fed.getGuardPosts().totalPosted());
    }

    @Test
    void assignGuard_unknownCode_isRefused() {
        ActionResult r = guard("ASSIGN_GUARD", "WARP_CORE_THING", false);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("Unknown guard target"));
    }

    @Test
    void assignGuard_outsideEnergyAllocation_isRefused() {
        // Close the allocation window
        game.submitAllocation(fed,     makeAllocation(fed));
        game.submitAllocation(klingon, makeAllocation(klingon));

        ActionResult r = guard("ASSIGN_GUARD", "SENSORS", false);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("D7.83"));
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
