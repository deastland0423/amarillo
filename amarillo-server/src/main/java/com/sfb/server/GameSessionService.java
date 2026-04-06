package com.sfb.server;

import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active game sessions.
 * Single instance held by Spring (singleton bean).
 */
@Service
public class GameSessionService {

    private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------

    /**
     * Create a new game session. Returns the session with host already registered.
     */
    public GameSession createSession(String hostName) {
        String sessionId  = generateCode();
        String hostToken  = generateToken();
        GameSession session = new GameSession(sessionId, hostToken, hostName);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * Join an existing session. Returns the new player's token, or null if not found.
     */
    public String joinSession(String sessionId, String playerName) {
        GameSession session = sessions.get(sessionId);
        if (session == null) return null;
        String token = generateToken();
        session.addPlayer(token, playerName);
        return token;
    }

    public GameSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Short uppercase game code, e.g. "A3F7" */
    private String generateCode() {
        return UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    /** Full UUID for player tokens */
    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}
