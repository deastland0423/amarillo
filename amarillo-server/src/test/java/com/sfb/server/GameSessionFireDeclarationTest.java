package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.Player;
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
 * Fire declaration round (D6.315 written orders): the call convenes all
 * players, commits stay sealed until everyone responds, EW applies before
 * fire at the reveal, and the round is once per impulse.
 */
class GameSessionFireDeclarationTest {

    private static final String HOST = "token-host";
    private static final String P2   = "token-p2";

    private GameSession session;
    private Game game;
    private Ship fed;
    private Ship klingon;

    @BeforeEach
    void setUp() {
        session = new GameSession("game-1", HOST, "Alice");
        session.addPlayer(P2, "Bob");
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
        klingon.setLocation(new Location(11, 10));
        klingon.setFacing(13);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);

        // Ownership: Alice → fed, Bob → klingon (bypassing scenario assignment)
        Player alice = new Player();
        alice.setName("Alice");
        alice.getPlayerUnits().add(fed);
        session.getPlayers().get(HOST).setCorePlayer(alice);
        Player bob = new Player();
        bob.setName("Bob");
        bob.getPlayerUnits().add(klingon);
        session.getPlayers().get(P2).setCorePlayer(bob);

        game.startTurn();
        game.submitAllocation(fed, makeAllocation(fed));
        game.submitAllocation(klingon, makeAllocation(klingon));
        advanceToDirectFire();
    }

    private Energy makeAllocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(4.0);
        return e;
    }

    private void advanceToDirectFire() {
        for (int guard = 0; guard < 400
                && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());
    }

    private ActionRequest request(String type, String token) {
        ActionRequest req = new ActionRequest();
        req.setType(type);
        req.setPlayerToken(token);
        return req;
    }

    private ActionRequest.EwAdjustment ewAdjustment(String shipName, int ecm, int eccm) {
        ActionRequest.EwAdjustment a = new ActionRequest.EwAdjustment();
        a.setShipName(shipName);
        a.setEcm(ecm);
        a.setEccm(eccm);
        return a;
    }

    // -------------------------------------------------------------------------
    // The call
    // -------------------------------------------------------------------------

    @Test
    void call_opensRound_andResetsStaleReadies() {
        // Alice readies through — then Bob calls: her Ready cannot stand
        session.executeAction(request("ADVANCE_PHASE", HOST));
        assertEquals(1, session.getReadyCount());

        ActionResult call = session.executeAction(request("CALL_FIRE_DECLARATION", P2));
        assertTrue(call.isSuccess(), call.getMessage());
        assertTrue(session.isFireDeclarationOpen());
        assertEquals("Bob", session.getFireDeclarationCallerName());
        assertEquals(0, session.getReadyCount());

        ActionResult ready = session.executeAction(request("ADVANCE_PHASE", HOST));
        assertFalse(ready.isSuccess());
        assertTrue(ready.getMessage().startsWith("MUST_RESPOND_DECLARATION"), ready.getMessage());
    }

    @Test
    void call_outsideDirectFire_isRefused() {
        game.advancePhase(); // leave DIRECT_FIRE
        ActionResult call = session.executeAction(request("CALL_FIRE_DECLARATION", HOST));
        assertFalse(call.isSuccess());
    }

    @Test
    void secondCall_whileOpen_isRefused() {
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));
        ActionResult second = session.executeAction(request("CALL_FIRE_DECLARATION", P2));
        assertFalse(second.isSuccess());
        assertTrue(second.getMessage().contains("already open"), second.getMessage());
    }

    // -------------------------------------------------------------------------
    // Commits
    // -------------------------------------------------------------------------

    @Test
    void commit_forEnemyShip_isRefused() {
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));
        ActionRequest commit = request("COMMIT_FIRE_DECLARATION", HOST);
        commit.setEwAdjustments(List.of(ewAdjustment("IKV Saber", 2, 0)));

        ActionResult r = session.executeAction(commit);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("another player"), r.getMessage());
    }

    @Test
    void commit_isSealed_untilAllRespond() {
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));
        ActionRequest commit = request("COMMIT_FIRE_DECLARATION", HOST);
        commit.setEwAdjustments(List.of(ewAdjustment("USS Enterprise", 2, 0)));

        ActionResult r = session.executeAction(commit);
        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(List.of("Alice"), session.getFireDeclarationRespondedNames());
        assertEquals(0, fed.getEcmAllocated(), "EW must not apply before the reveal");
        assertTrue(session.isFireDeclarationOpen());

        ActionResult again = session.executeAction(commit);
        assertFalse(again.isSuccess());
        assertTrue(again.getMessage().contains("sealed"), again.getMessage());
    }

    // -------------------------------------------------------------------------
    // The reveal
    // -------------------------------------------------------------------------

    @Test
    void fullRound_ewAppliesAndFireResolves() {
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));

        // Alice: fire plan (first working weapon at the D7) + EW change
        ActionRequest commit = request("COMMIT_FIRE_DECLARATION", HOST);
        ActionRequest.FireOrder order = new ActionRequest.FireOrder();
        order.setShipName("USS Enterprise");
        order.setTargetName("IKV Saber");
        order.setWeaponNames(List.of(fed.getWeapons().fetchAllWeapons().get(0).getName()));
        order.setRange(1);
        order.setAdjustedRange(1);
        order.setShieldNumber(1);
        commit.setFireOrders(List.of(order));
        commit.setEwAdjustments(List.of(ewAdjustment("USS Enterprise", 0, 2)));
        assertTrue(session.executeAction(commit).isSuccess());

        // Bob passes — the reveal fires
        ActionResult pass = session.executeAction(request("PASS_FIRE_DECLARATION", P2));
        assertTrue(pass.isSuccess(), pass.getMessage());

        assertFalse(session.isFireDeclarationOpen());
        assertTrue(session.isFireDeclarationSpent());
        assertEquals(2, fed.getEccmAllocated(), "EW applied at the reveal");
        assertFalse(game.getPendingVolleys().isEmpty(), "declared fire queued a volley");

        String log = String.join("\n", session.drainCombatLog());
        assertTrue(log.contains("Fire declaration resolves"), log);
    }

    @Test
    void bluff_callAndPass_spendsTheImpulse() {
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));
        session.executeAction(request("PASS_FIRE_DECLARATION", HOST));
        session.executeAction(request("PASS_FIRE_DECLARATION", P2));

        assertFalse(session.isFireDeclarationOpen());
        assertTrue(session.isFireDeclarationSpent());

        ActionResult again = session.executeAction(request("CALL_FIRE_DECLARATION", HOST));
        assertFalse(again.isSuccess());
        assertTrue(again.getMessage().contains("one per impulse"), again.getMessage());
    }

    @Test
    void nextImpulse_allowsANewCall() {
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));
        session.executeAction(request("PASS_FIRE_DECLARATION", HOST));
        session.executeAction(request("PASS_FIRE_DECLARATION", P2));
        assertTrue(session.isFireDeclarationSpent());

        int impulse = game.getAbsoluteImpulse();
        for (int guard = 0; guard < 50 && !(game.getAbsoluteImpulse() > impulse
                && game.getCurrentPhase() == Game.ImpulsePhase.DIRECT_FIRE); guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());

        assertFalse(session.isFireDeclarationSpent());
        ActionResult call = session.executeAction(request("CALL_FIRE_DECLARATION", P2));
        assertTrue(call.isSuccess(), call.getMessage());
    }
}
