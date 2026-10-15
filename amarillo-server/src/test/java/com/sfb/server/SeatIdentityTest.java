package com.sfb.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Accounts and seats: a durable identity may take back the seat it already holds.
 *
 * <h2>What this separates</h2>
 * A <b>seat token</b> is per-game and is what every piece of per-player state keys on — ready
 * flags, COI, deployment, assignments — and ships bind to it. An <b>account id</b> is durable and
 * belongs to a person; it outlives a game and may hold seats in several at once.
 *
 * <p>One string used to do both jobs, which made coming back destructive: {@code joinSession}
 * minted a fresh token on every call, so a player returning by game code arrived as a stranger
 * while their ships stayed bound to the token they left. {@code App.tsx} and {@code Lobby.tsx}
 * both carry warnings not to confuse Join with Resume, which is the shape of a workaround for
 * exactly this. With an account presented, a join now returns the held seat instead.
 *
 * <p>The account id is optional throughout. A caller that presents none must behave exactly as
 * before, because most of this suite does.
 */
class SeatIdentityTest {

    private GameSessionService service;

    @BeforeEach
    void setUp() {
        service = new GameSessionService();
    }

    // ---------------------------------------------------------------- taking a seat back

    @Test
    void anAccountRejoiningGetsTheSameSeat() {
        GameSession s = service.createSession("Dan", "account-dan");
        String first = service.joinSession(s.getId(), "Dave", "account-dave");

        String again = service.joinSession(s.getId(), "Dave", "account-dave");

        assertEquals(first, again, "the same account must come back to the same seat");
        assertEquals(2, s.getPlayers().size(), "and must NOT be seated twice");
    }

    /** One Klingon cruiser, placed, so there is something to own. */
    private static com.sfb.scenario.ScenarioSpec oneShip() {
        com.sfb.scenario.ScenarioSpec spec = new com.sfb.scenario.ScenarioSpec();
        spec.name = "Seat fixture";
        spec.year = 180;
        spec.mapCols = 42;
        spec.mapRows = 32;

        com.sfb.scenario.ScenarioSpec.ShipSetup ship =
                new com.sfb.scenario.ScenarioSpec.ShipSetup();
        ship.faction = "Klingon";
        ship.type = "D7";
        ship.shipName = "IKV Raider";
        ship.startHex = "2118";
        ship.startHeading = "A";

        com.sfb.scenario.ScenarioSpec.SideSpec side =
                new com.sfb.scenario.ScenarioSpec.SideSpec();
        side.faction = "Klingon";
        side.name = "Raiders";
        side.ships = new java.util.ArrayList<>(java.util.List.of(ship));
        spec.sides = new java.util.ArrayList<>(java.util.List.of(side));
        return spec;
    }

    /** The whole point: the ships are bound to the seat, so the seat is the fleet. */
    @Test
    void rejoiningKeepsTheShipsThatWereAssignedToThatSeat() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        GameSession s = service.createSession("Dan", "account-dan");
        String dave = service.joinSession(s.getId(), "Dave", "account-dave");

        s.loadBuiltScenario(oneShip(), "fixture");
        assertNull(s.assignShip(dave, "IKV Raider"));

        String back = service.joinSession(s.getId(), "Dave", "account-dave");

        assertEquals(dave, back);
        assertTrue(s.getAssignedShipsFor(back).contains("IKV Raider"),
                "a rejoin that lost the ships would be the bug this prevents");
    }

    /**
     * A host who leaves their own game and comes back by code must still be the host — otherwise
     * they return as a guest with no scenario picker and no way to start the game they created.
     */
    @Test
    void theHostComesBackAsTheHost() {
        GameSession s = service.createSession("Dan", "account-dan");

        String back = service.joinSession(s.getId(), "Dan", "account-dan");

        assertEquals(s.getHostToken(), back);
        assertTrue(s.isHost(back));
        assertEquals(1, s.getPlayers().size(), "the host must not become a second player");
    }

    // ---------------------------------------------------------------- staying separate

    @Test
    void differentAccountsGetDifferentSeats() {
        GameSession s = service.createSession("Dan", "account-dan");

        String a = service.joinSession(s.getId(), "Dave", "account-dave");
        String b = service.joinSession(s.getId(), "Erin", "account-erin");

        assertNotEquals(a, b);
        assertEquals(3, s.getPlayers().size());
    }

    /**
     * Two browsers signed in as two people is the owner's playtest routine, and it must keep
     * producing two independent seats in one game.
     */
    @Test
    void oneAccountMayHoldSeatsInSeveralGamesAtOnce() {
        GameSession one = service.createSession("Dan", "account-dan");
        GameSession two = service.createSession("Dan", "account-dan");

        assertNotEquals(one.getId(), two.getId());
        assertEquals(one.getHostToken(), one.seatFor("account-dan"));
        assertEquals(two.getHostToken(), two.seatFor("account-dan"));
        assertNotEquals(one.seatFor("account-dan"), two.seatFor("account-dan"),
                "a seat belongs to one game; the account is what spans them");
    }

    // ---------------------------------------------------------------- the anonymous path

    /** Everything that does not care about identity must be untouched. */
    @Test
    void withoutAnAccountEveryJoinIsANewSeat() {
        GameSession s = service.createSession("Dan");

        String a = service.joinSession(s.getId(), "Dave");
        String b = service.joinSession(s.getId(), "Dave");

        assertNotEquals(a, b, "no identity was offered, so there is nothing to recognise");
        assertEquals(3, s.getPlayers().size());
        assertNull(s.seatFor(null));
        assertNull(s.accountInSeat(a), "an anonymous seat has no account behind it");
    }

    @Test
    void anUnknownAccountHoldsNoSeat() {
        GameSession s = service.createSession("Dan", "account-dan");

        assertNull(s.seatFor("account-stranger"));
        assertEquals("account-dan", s.accountInSeat(s.getHostToken()));
    }

    @Test
    void joiningAGameThatDoesNotExistStillReturnsNull() {
        assertNull(service.joinSession("ZZZZ", "Dave", "account-dave"));
    }

    // ---------------------------------------------------------------- game codes

    /**
     * A service whose code generator is rigged, so a collision is certain rather than likely.
     *
     * Creating enough games to collide by chance would be a test that passes or fails on a coin
     * flip — four hex characters is ~65k values, so a few hundred games collide *probably*, not
     * *always*. Forcing the code makes the guarantee exact.
     */
    private static class FixedCodeService extends GameSessionService {
        private final java.util.Deque<String> codes;

        FixedCodeService(String... codes) {
            this.codes = new java.util.ArrayDeque<>(java.util.List.of(codes));
        }

        @Override
        String generateCode() {
            // Last code repeats forever once the script runs out.
            return codes.size() > 1 ? codes.poll() : codes.peek();
        }
    }

    /**
     * The defect this pins: a colliding code used to REPLACE a live game. {@code sessions.put}
     * overwrote it, and every player in that game went on holding tokens for a session that no
     * longer existed — their battle simply gone, with nothing to say so.
     */
    @Test
    void aCollidingCodeIsRetriedRatherThanOverwritingALiveGame() {
        // "AAAA" twice, so the SECOND creation's first roll collides and must be re-rolled.
        // Handing out two distinct codes would never exercise the retry at all — which is what
        // the first draft of this test did, and it went on passing with the defect restored.
        GameSessionService rigged = new FixedCodeService("AAAA", "AAAA", "BBBB");

        GameSession first  = rigged.createSession("Dan", "account-dan");
        GameSession second = rigged.createSession("Dave", "account-dave");

        assertEquals("AAAA", first.getId());
        assertEquals("BBBB", second.getId(), "the collision must be re-rolled, not taken");
        assertSame(first, rigged.getSession("AAAA"), "the first game must still be there");
        assertSame(second, rigged.getSession("BBBB"));
    }

    /** When every re-roll collides, refuse loudly rather than hand back a doomed session. */
    @Test
    void exhaustingTheCodeSpaceThrowsRatherThanReplacing() {
        GameSessionService rigged = new FixedCodeService("AAAA");
        GameSession first = rigged.createSession("Dan", "account-dan");

        assertThrows(IllegalStateException.class,
                () -> rigged.createSession("Dave", "account-dave"));
        assertSame(first, rigged.getSession("AAAA"),
                "the live game survives a failed creation");
    }

    /** Smoke test over the real generator — not the collision proof, just that ids are distinct. */
    @Test
    void ordinaryCreationYieldsDistinctRetrievableGames() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 200; i++)
            ids.add(service.createSession("Host " + i, "account-" + i).getId());

        for (String id : ids)
            assertNotNull(service.getSession(id), "game " + id + " was lost after creation");
    }
}
