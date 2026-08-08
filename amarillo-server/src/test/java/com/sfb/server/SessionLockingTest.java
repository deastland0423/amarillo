package com.sfb.server;

import com.sfb.Game.ActionResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the per-session lock: the normal end-of-phase pattern in
 * a two-player game is both players clicking Ready within milliseconds of each
 * other. Without the lock, both threads can add their token, both pass the
 * allReady() check, and the phase advances twice — silently skipping a phase.
 * With the lock the clicks serialize: exactly one WAITING, exactly one advance.
 */
class SessionLockingTest {

    private static final String HOST = "token-host";
    private static final String P2   = "token-p2";

    private static final int ROUNDS = 200;

    @Test
    void simultaneousReadyClicks_advancePhaseExactlyOnce() throws Exception {
        GameSession session = new GameSession("game-1", HOST, "Alice");
        session.addPlayer(P2, "Bob");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<ActionResult> a = pool.submit(() -> {
                    barrier.await();
                    return advance(session, HOST);
                });
                Future<ActionResult> b = pool.submit(() -> {
                    barrier.await();
                    return advance(session, P2);
                });
                ActionResult ra = a.get();
                ActionResult rb = b.get();

                int advances = (ra.isWaiting() ? 0 : 1) + (rb.isWaiting() ? 0 : 1);
                assertEquals(1, advances, "round " + round
                        + ": exactly one of two simultaneous Ready clicks may advance the phase"
                        + " (0 = lost ready, 2 = double advance / skipped phase)");
                assertEquals(0, session.getReadyCount(),
                        "round " + round + ": ready set cleared after the advance");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private ActionResult advance(GameSession session, String token) {
        ActionRequest req = new ActionRequest();
        req.setType("ADVANCE_PHASE");
        req.setPlayerToken(token);
        return session.executeAction(req);
    }
}
