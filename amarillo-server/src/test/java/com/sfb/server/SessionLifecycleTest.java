package com.sfb.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A game can end, and a forgotten one does not live for ever.
 *
 * <h2>What was wrong</h2>
 * {@code sessions.remove} appeared NOWHERE. Every game ever hosted stayed in the map, which is a
 * slow leak — but the sharper cost was GHOSTS. Opening a save creates a new game, and the
 * pre-save game carries on existing, still joinable, with its state intact. A player who clicks
 * Resume lands in it: the same battle, looking identical, diverging silently from the one
 * everybody else moved to. The restored-battle screen warns about this in words, which is a
 * label over a lifecycle problem rather than a fix for it.
 *
 * <h2>Two mechanisms, and which is which</h2>
 * Ending a game explicitly is what a host should do, and it is the one that solves the ghost.
 * The sweep is only a backstop for games nobody ever closed, with a deliberately generous
 * threshold: a battle left overnight and resumed the next evening is an ordinary way to play, so
 * anything measured in hours would throw away games people still want.
 */
class SessionLifecycleTest {

    private GameSessionService service;

    @BeforeEach
    void setUp() {
        service = new GameSessionService();
    }

    // ---------------------------------------------------------------- ending a game

    @Test
    void anEndedGameStopsExisting() {
        GameSession session = service.createSession("Dan", "account-dan");
        String id = session.getId();
        assertNotNull(service.getSession(id));

        assertSame(session, service.endSession(id));

        assertNull(service.getSession(id), "an ended game must not answer to its code");
    }

    /** A token for an ended game is simply a token for nothing, which clients already handle. */
    @Test
    void aSeatInAnEndedGameLeadsNowhere() {
        GameSession session = service.createSession("Dan", "account-dan");
        String guest = service.joinSession(session.getId(), "Dave", "account-dave");
        assertNotNull(guest);

        service.endSession(session.getId());

        assertNull(service.getSession(session.getId()));
        assertNull(service.joinSession(session.getId(), "Dave", "account-dave"),
                "and it cannot be rejoined");
    }

    @Test
    void endingSomethingThatIsNotThereIsHarmless() {
        assertNull(service.endSession("ZZZZ"));
        assertNull(service.endSession(null));
    }

    /**
     * THE GHOST. A battle is saved, reopened as a new game, and the old one is ended — after
     * which there is only one battle anybody can reach, which is the whole point.
     */
    @Test
    void endingTheOldGameRemovesTheGhostAfterASaveIsReopened() {
        GameSession original = service.createSession("Dan", "account-dan");
        String oldId = original.getId();

        // Stand in for opening a save: a second, separate session for the same battle.
        GameSession restored = service.createSession("Dan", "account-dan");
        assertNotEquals(oldId, restored.getId(), "a restored battle is a new game");
        assertNotNull(service.getSession(oldId), "and the old one is still alive until ended");

        service.endSession(oldId);

        assertNull(service.getSession(oldId), "no copy left for Resume to walk back into");
        assertNotNull(service.getSession(restored.getId()), "the battle people moved to stays");
    }

    // ---------------------------------------------------------------- the sweep

    @Test
    void aGameInUseIsNeverSweptAway() {
        GameSession session = service.createSession("Dan", "account-dan");

        assertEquals(0, service.sweepAbandoned());
        assertNotNull(service.getSession(session.getId()));
    }

    /**
     * Only a session untouched past the threshold goes. Driven by setting the timestamp rather
     * than by waiting two days, which is the only way to test a duration without pretending.
     */
    @Test
    void aGameNobodyHasTouchedForDaysIsSweptAway() {
        GameSession stale = service.createSession("Dan", "account-dan");
        GameSession fresh = service.createSession("Dave", "account-dave");
        ageByDays(stale, 3);

        assertEquals(1, service.sweepAbandoned());

        assertNull(service.getSession(stale.getId()));
        assertNotNull(service.getSession(fresh.getId()), "a recent game must survive the sweep");
    }

    /**
     * A battle left overnight and picked up the next evening is ordinary. The threshold has to
     * be generous enough that an ordinary way of playing is never mistaken for abandonment.
     */
    @Test
    void aGameLeftOvernightSurvives() {
        GameSession overnight = service.createSession("Dan", "account-dan");
        ageByHours(overnight, 20);

        assertEquals(0, service.sweepAbandoned());
        assertNotNull(service.getSession(overnight.getId()));
    }

    /** Looking a game up counts as using it — which is what every request does. */
    @Test
    void touchingAGameKeepsItAlive() {
        GameSession session = service.createSession("Dan", "account-dan");
        ageByDays(session, 3);

        service.getSession(session.getId());   // somebody is still playing

        assertEquals(0, service.sweepAbandoned(), "it was used just now");
        assertNotNull(service.getSession(session.getId()));
    }

    /** The sweep runs when the map grows, since that is the only moment it can grow. */
    @Test
    void creatingAGameSweepsTheForgottenOnes() {
        GameSession stale = service.createSession("Dan", "account-dan");
        ageByDays(stale, 3);

        service.createSession("Erin", "account-erin");

        assertNull(service.getSession(stale.getId()),
                "a create is what prunes, so no scheduler is needed");
    }

    // ---------------------------------------------------------------- helpers

    private static void ageByDays(GameSession session, int days) {
        ageByMillis(session, java.time.Duration.ofDays(days).toMillis());
    }

    private static void ageByHours(GameSession session, int hours) {
        ageByMillis(session, java.time.Duration.ofHours(hours).toMillis());
    }

    /** Reach in and backdate the activity stamp; waiting out a two-day threshold is not a test. */
    private static void ageByMillis(GameSession session, long millis) {
        try {
            java.lang.reflect.Field field = GameSession.class.getDeclaredField("lastTouched");
            field.setAccessible(true);
            field.setLong(session, System.currentTimeMillis() - millis);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("lastTouched is what the sweep reads", e);
        }
    }
}
