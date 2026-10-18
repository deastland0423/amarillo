package com.sfb.server;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active game sessions.
 * Single instance held by Spring (singleton bean).
 *
 * <h2>Accounts and seats</h2>
 * Two different identifiers meet here, and keeping them apart is the whole point:
 * <ul>
 *   <li>an <b>account id</b> is durable and belongs to a person — it outlives a game, and the
 *       same one may hold seats in several games at once;</li>
 *   <li>a <b>seat token</b> is per-game and is what the session keys every piece of per-player
 *       state on (ready flags, COI, deployment, assignments). Ships bind to the seat.</li>
 * </ul>
 * One opaque string used to do both jobs, which made rejoining a game destructive: {@code
 * joinSession} minted a fresh token every time, so a player who came back by game code arrived
 * as a stranger while their ships stayed bound to the token they left behind. Both {@code
 * App.tsx} and {@code Lobby.tsx} carry warnings about it. With an account id presented, a join
 * now RETURNS THE SEAT that account already holds, which is what makes coming back safe — and
 * is the precondition for a game that can be saved and resumed another day.
 *
 * <p>The account id stays optional. Passing none behaves exactly as before, so a caller that
 * does not care about identity (most tests) is unaffected.
 */
@Service
public class GameSessionService {

    private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();

    /**
     * How many times to re-roll a colliding game code before giving up.
     *
     * A code is four hex characters (~65k values), so by the birthday bound collisions start to
     * appear in the low hundreds of concurrent games — long before the space is anywhere near
     * full. Ten rolls makes the odds of failing to find a free one negligible at any population
     * this server will see, and the loop below is what stops a collision being silent.
     */
    private static final int CODE_ATTEMPTS = 10;

    // -------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------

    /**
     * How long a session may sit completely untouched before a later create may sweep it away.
     *
     * Deliberately generous. A battle left overnight and picked up the next evening is an
     * ordinary way to play this game, so anything measured in hours would throw away games
     * people still want. Two days is long enough that reaching it means the game really was
     * abandoned, and short enough that a server running for months does not hold every battle
     * ever started.
     *
     * <p>This is a backstop, not the mechanism. Ending a game explicitly is what a host should
     * do, and the sweep only catches the ones nobody ever closed.
     */
    private static final long ABANDONED_AFTER_MILLIS = java.time.Duration.ofDays(2).toMillis();

    /**
     * End a game and free it. Returns the session that was removed, or null if there was none.
     *
     * <h2>Why this had to exist</h2>
     * Nothing ever removed a session. Every game ever hosted stayed in the map, which is a slow
     * leak and, worse, left ghosts: after a battle was restored from a save the pre-save game was
     * still alive and still joinable, so a player clicking Resume landed in a copy of the battle
     * that diverged silently from the one everybody else was playing.
     *
     * <p>Removing it from the map is the whole job — nothing outside holds a session, the
     * controller looks each one up by id on every call, so an ended game simply stops existing
     * and later requests for it answer 404, which clients already treat as "that game is gone".
     */
    public GameSession endSession(String sessionId) {
        if (sessionId == null)
            return null;
        return sessions.remove(sessionId);
    }

    /**
     * Forget games nobody has touched in a long time.
     *
     * Swept lazily from {@link #createSession}, rather than from a background thread, because
     * the only moment the map grows is when a session is added — so that is the only moment it
     * needs pruning, and it costs no scheduler, no thread and nothing to shut down.
     *
     * @return how many were removed
     */
    int sweepAbandoned() {
        long cutoff = System.currentTimeMillis() - ABANDONED_AFTER_MILLIS;
        int removed = 0;
        for (Map.Entry<String, GameSession> entry : sessions.entrySet())
            if (entry.getValue().getLastTouched() < cutoff
                    && sessions.remove(entry.getKey(), entry.getValue()))
                removed++;
        return removed;
    }

    /** Every live session, newest activity first — for an "are these still going?" listing. */
    public java.util.List<GameSession> liveSessions() {
        java.util.List<GameSession> out = new java.util.ArrayList<>(sessions.values());
        out.sort((a, b) -> Long.compare(b.getLastTouched(), a.getLastTouched()));
        return out;
    }

    /** Create a new game session with no durable identity recorded for the host. */
    public GameSession createSession(String hostName) {
        return createSession(hostName, null);
    }

    /**
     * Create a new game session. Returns the session with the host already registered.
     *
     * <p>The insert is {@code putIfAbsent} and retried, NOT {@code put}. A plain put on a
     * colliding code silently replaced a live game — every player in it still holding tokens for
     * a session that no longer existed, their battle simply gone. That the window is small is no
     * comfort: the consequence was total and there was nothing to tell anyone it had happened.
     *
     * @param accountId durable identity of the host, or null if unknown
     */
    public GameSession createSession(String hostName, String accountId) {
        // The map only grows here, so this is the only place it needs pruning.
        sweepAbandoned();
        String hostToken = generateToken();
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String sessionId = generateCode();
            GameSession session = new GameSession(sessionId, hostToken, hostName, accountId);
            if (sessions.putIfAbsent(sessionId, session) == null)
                return session;
        }
        // Every roll collided. Far better to refuse loudly than to hand back a session that is
        // about to be overwritten, or to overwrite somebody else's.
        throw new IllegalStateException(
                "could not find a free game code after " + CODE_ATTEMPTS + " attempts");
    }

    /**
     * Open a saved battle as a new session.
     *
     * <h2>Why a NEW session rather than loading into an existing one</h2>
     * A restored battle cannot be poured into a session that already holds a game:
     * {@code setupFromScenario} appends ships rather than clearing, so it would duplicate the
     * fleet, and {@code GameSession.game} is set once at construction. A session of its own also
     * means a fresh code and fresh seats, which is the right outcome rather than a cost — a saved
     * battle may be continued by different people than fought it, so everybody joins and the host
     * hands out the ships.
     *
     * @return the new session, or null when the save could not be rebuilt at all
     */
    public SessionFromSave openSavedGame(String hostName, String accountId,
            com.sfb.scenario.SavedGame saved) {
        com.sfb.scenario.SavedGame.Restored restored = saved.restore();
        if (restored.game() == null)
            return new SessionFromSave(null, restored.problems());

        String hostToken = generateToken();
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String sessionId = generateCode();
            GameSession session = new GameSession(sessionId, hostToken, hostName, accountId,
                    restored.game());
            if (sessions.putIfAbsent(sessionId, session) == null) {
                session.adoptRestored(saved.scenario);
                return new SessionFromSave(session, restored.problems());
            }
        }
        throw new IllegalStateException(
                "could not find a free game code after " + CODE_ATTEMPTS + " attempts");
    }

    /** A session opened from a save, and anything worth telling the player about the load. */
    public record SessionFromSave(GameSession session, java.util.List<String> problems) {
    }

    /** Join an existing session anonymously — a fresh seat every time. */
    public String joinSession(String sessionId, String playerName) {
        return joinSession(sessionId, playerName, null);
    }

    /**
     * Join an existing session, or take back the seat this account already holds.
     *
     * @param accountId durable identity of the joiner, or null to always take a new seat
     * @return the player's seat token, or null if there is no such session
     */
    public String joinSession(String sessionId, String playerName, String accountId) {
        GameSession session = sessions.get(sessionId);
        if (session == null) return null;

        // The seat is the ships. Returning the existing one is what makes a rejoin non-destructive
        // — including for the HOST, who would otherwise come back as a guest in their own game.
        if (accountId != null) {
            String held = session.seatFor(accountId);
            if (held != null) return held;
        }

        String token = generateToken();
        session.addPlayer(token, playerName, accountId);
        return token;
    }

    public GameSession getSession(String sessionId) {
        GameSession session = sessions.get(sessionId);
        // Looking a game up is the one thing every request does, so it is where "still in use"
        // is cheapest and most reliably observed.
        if (session != null)
            session.touch();
        return session;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Short uppercase game code, e.g. "A3F7".
     *
     * Package-private and overridable ONLY so a test can force a collision. The codes come from
     * {@code UUID.randomUUID()}, which a test cannot steer, and proving the retry above by
     * creating enough games to collide by chance would be a test that passes or fails on a coin
     * flip — the failure mode this repo has been bitten by before. A subclass returning a fixed
     * code makes the guarantee exact.
     */
    String generateCode() {
        return UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    /** Full UUID for player seat tokens */
    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}
