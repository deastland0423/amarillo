package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
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
 * <p>
 * Also pins that the round is the ONLY way to fire - no action resolves a shot on its
 * own, whether at a unit or at a hex.
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
        // Was asserting the literal "MUST_RESPOND_DECLARATION" prefix — a raw token that
        // nothing on the client translated, so it reached the screen as written. The
        // refusal is what matters; the wording is now meant for a person to read.
        assertTrue(ready.getMessage().contains("declared fire"), ready.getMessage());
        assertTrue(ready.getMessage().contains("Bob"), ready.getMessage());
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

    // -------------------------------------------------------------------------
    // Advancing the phase while a declaration is open
    // -------------------------------------------------------------------------

    /**
     * The playtest complaint: seal your orders, press Next, and be told
     * "MUST_RESPOND_DECLARATION: Dizzle" — a raw token, naming the player who called the
     * declaration, telling you to do the thing you had just done.
     * <p>
     * Both halves were wrong. declarationOpen stays true until EVERY player has answered,
     * so a player who had already sealed still tripped the check; and nothing on the client
     * translated the code, so it reached the screen verbatim. A player who has committed is
     * simply waiting, which is the same state as readying up ahead of the others.
     */
    @Test
    void advancingAfterSealingOrders_readsAsWaiting_notAsAnError() {
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));
        assertTrue(session.executeAction(request("PASS_FIRE_DECLARATION", HOST)).isSuccess());

        ActionResult advance = session.executeAction(request("ADVANCE_PHASE", HOST));

        assertTrue(advance.getMessage().startsWith("WAITING"),
                "a sealed player is waiting, not being asked for something: "
                        + advance.getMessage());
        assertFalse(advance.getMessage().contains("MUST_RESPOND"), advance.getMessage());
    }

    @Test
    void advancingWithoutAnswering_saysSoInEnglish() {
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));

        ActionResult advance = session.executeAction(request("ADVANCE_PHASE", P2));

        assertFalse(advance.isSuccess());
        assertFalse(advance.getMessage().contains("MUST_RESPOND_DECLARATION"),
                "the raw code must not reach a player: " + advance.getMessage());
        assertTrue(advance.getMessage().contains("declared fire"), advance.getMessage());
    }

    // -------------------------------------------------------------------------
    // One way to fire
    // -------------------------------------------------------------------------

    /**
     * FIRE resolved a volley the moment it arrived: see the board, shoot, watch the
     * result, all before anyone else had decided anything. No client posted it and no
     * test used it, which is exactly how it survived.
     */
    @Test
    void thereIsNoStandaloneFireAction() {
        ActionRequest fire = request("FIRE", HOST);
        fire.setShipName("USS Enterprise");
        fire.setTargetName("IKV Saber");
        fire.setWeaponNames(List.of(fed.getWeapons().fetchAllWeapons().get(0).getName()));
        fire.setRange(1);

        ActionResult r = session.executeAction(fire);

        assertFalse(r.isSuccess(), "FIRE must not resolve a shot on its own");
        assertTrue(r.getMessage().contains("Unknown action type"), r.getMessage());
        assertTrue(game.getPendingVolleys().isEmpty(), "nothing fired outside the declaration");
    }

    /** Same for firing into a hex (P3.25 / P2.311). */
    @Test
    void thereIsNoStandaloneHexFireAction() {
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));

        ActionRequest fire = request("FIRE_AT_HEX", HOST);
        fire.setShipName("USS Enterprise");
        fire.setHexCol(10);
        fire.setHexRow(9);
        fire.setWeaponNames(List.of(fed.getWeapons().fetchAllWeapons().get(0).getName()));

        ActionResult r = session.executeAction(fire);

        assertFalse(r.isSuccess(), "FIRE_AT_HEX must not resolve a shot on its own");
        assertTrue(r.getMessage().contains("Unknown action type"), r.getMessage());
    }

    /**
     * A volley aimed at a place is an ordinary sealed order: nothing happens when it is
     * committed, and it resolves with everything else at the reveal.
     */
    @Test
    void hexOrder_staysSealed_andResolvesAtTheReveal() {
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));

        ActionRequest commit = request("COMMIT_FIRE_DECLARATION", HOST);
        ActionRequest.FireOrder order = new ActionRequest.FireOrder();
        order.setShipName("USS Enterprise");
        order.setWeaponNames(List.of(fed.getWeapons().fetchAllWeapons().get(0).getName()));
        order.setHexCol(10);
        order.setHexRow(9);
        commit.setFireOrders(List.of(order));
        assertTrue(session.executeAction(commit).isSuccess());

        assertFalse(String.join("\n", session.drainCombatLog()).contains("clear a path"),
                "a sealed order must not fire before the reveal");

        ActionResult pass = session.executeAction(request("PASS_FIRE_DECLARATION", P2));
        assertTrue(pass.isSuccess(), pass.getMessage());

        String log = String.join("\n", session.drainCombatLog());
        assertTrue(log.contains("Fire declaration resolves"), log);
        assertTrue(log.contains("clear a path"), log);
    }
}
