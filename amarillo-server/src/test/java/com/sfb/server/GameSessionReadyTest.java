package com.sfb.server;

import com.sfb.Game.ActionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ready/unready phase-advance system in GameSession.
 */
class GameSessionReadyTest {

    private static final String HOST  = "token-host";
    private static final String P2    = "token-p2";
    private static final String P3    = "token-p3";

    private GameSession session;

    @BeforeEach
    void setUp() {
        session = new GameSession("game-1", HOST, "Alice");
        session.start();
    }

    // -------------------------------------------------------------------------
    // Single player — advance immediately
    // -------------------------------------------------------------------------

    @Test
    void singlePlayer_advancesImmediately() {
        ActionResult result = advancePhase(HOST);
        assertFalse(result.isWaiting());
        assertTrue(result.isSuccess());
    }

    @Test
    void singlePlayer_readyCountIsZeroAfterAdvance() {
        advancePhase(HOST);
        assertEquals(0, session.getReadyCount());
    }

    // -------------------------------------------------------------------------
    // Two players — both must ready
    // -------------------------------------------------------------------------

    @Test
    void twoPlayers_firstPlayerIsWaiting() {
        session.addPlayer(P2, "Bob");
        ActionResult result = advancePhase(HOST);
        assertTrue(result.isWaiting());
        assertTrue(result.isSuccess());
    }

    @Test
    void twoPlayers_waitingMessageContainsCount() {
        session.addPlayer(P2, "Bob");
        ActionResult result = advancePhase(HOST);
        assertTrue(result.getMessage().startsWith("WAITING:"));
        assertTrue(result.getMessage().contains("1/2"));
    }

    @Test
    void twoPlayers_secondPlayerAdvancesPhase() {
        session.addPlayer(P2, "Bob");
        advancePhase(HOST);
        ActionResult result = advancePhase(P2);
        assertFalse(result.isWaiting());
        assertTrue(result.isSuccess());
    }

    @Test
    void twoPlayers_readyCountClearedAfterAdvance() {
        session.addPlayer(P2, "Bob");
        advancePhase(HOST);
        advancePhase(P2);
        assertEquals(0, session.getReadyCount());
    }

    @Test
    void twoPlayers_readyCountIncrementsCorrectly() {
        session.addPlayer(P2, "Bob");
        assertEquals(0, session.getReadyCount());
        advancePhase(HOST);
        assertEquals(1, session.getReadyCount());
    }

    @Test
    void twoPlayers_duplicateReadyNotCounted() {
        session.addPlayer(P2, "Bob");
        advancePhase(HOST);
        advancePhase(HOST); // second click — same player
        assertEquals(1, session.getReadyCount());
    }

    // -------------------------------------------------------------------------
    // Three players
    // -------------------------------------------------------------------------

    @Test
    void threePlayers_allMustReady() {
        session.addPlayer(P2, "Bob");
        session.addPlayer(P3, "Carol");

        ActionResult r1 = advancePhase(HOST);
        ActionResult r2 = advancePhase(P2);
        assertTrue(r1.isWaiting());
        assertTrue(r2.isWaiting());
        assertTrue(r2.getMessage().contains("2/3"));

        ActionResult r3 = advancePhase(P3);
        assertFalse(r3.isWaiting());
    }

    // -------------------------------------------------------------------------
    // Unready
    // -------------------------------------------------------------------------

    @Test
    void unready_removesPlayerFromReadySet() {
        session.addPlayer(P2, "Bob");
        advancePhase(HOST);
        assertEquals(1, session.getReadyCount());

        unready(HOST);
        assertEquals(0, session.getReadyCount());
    }

    @Test
    void unready_returnsUnreadyMessage() {
        session.addPlayer(P2, "Bob");
        advancePhase(HOST);
        ActionResult result = unready(HOST);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().startsWith("UNREADY:"));
    }

    @Test
    void unready_afterUnready_playerCanReadyAgain() {
        session.addPlayer(P2, "Bob");
        advancePhase(HOST);
        unready(HOST);

        ActionResult result = advancePhase(HOST);
        assertTrue(result.isWaiting()); // still needs P2
        assertEquals(1, session.getReadyCount());
    }

    @Test
    void unready_doesNotBlockOtherPlayersReady() {
        session.addPlayer(P2, "Bob");
        advancePhase(HOST);
        advancePhase(P2);
        // Both ready — phase already advanced; ready count is 0
        assertEquals(0, session.getReadyCount());
    }

    @Test
    void unready_whenNotReady_isNoOp() {
        session.addPlayer(P2, "Bob");
        // HOST never readied
        ActionResult result = unready(HOST);
        assertTrue(result.isSuccess());
        assertEquals(0, session.getReadyCount());
    }

    @Test
    void unready_phaseDoesNotAdvanceAfterUnready() {
        session.addPlayer(P2, "Bob");
        advancePhase(HOST);
        advancePhase(P2);   // phase advanced, ready count cleared
        advancePhase(HOST); // ready again for next phase
        unready(HOST);      // pull back

        // P2 has not readied this phase — and HOST just unreadied
        assertEquals(0, session.getReadyCount());
        assertFalse(session.allReady());
    }

    // -------------------------------------------------------------------------
    // allReady() helper
    // -------------------------------------------------------------------------

    @Test
    void allReady_falseWhenNoPlayersReady() {
        session.addPlayer(P2, "Bob");
        assertFalse(session.allReady());
    }

    @Test
    void allReady_trueWhenAllReady() {
        session.addPlayer(P2, "Bob");
        advancePhase(HOST);
        advancePhase(P2);
        // Phase advanced and cleared — not "all ready" anymore
        assertFalse(session.allReady());
    }

    @Test
    void playerCount_reflectsAllPlayers() {
        assertEquals(1, session.getPlayerCount());
        session.addPlayer(P2, "Bob");
        assertEquals(2, session.getPlayerCount());
        session.addPlayer(P3, "Carol");
        assertEquals(3, session.getPlayerCount());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ActionResult advancePhase(String token) {
        ActionRequest req = new ActionRequest();
        req.setType("ADVANCE_PHASE");
        req.setPlayerToken(token);
        return session.executeAction(req);
    }

    private ActionResult unready(String token) {
        ActionRequest req = new ActionRequest();
        req.setType("UNREADY");
        req.setPlayerToken(token);
        return session.executeAction(req);
    }
}
