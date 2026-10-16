package com.sfb.server;

import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.scenario.SavedGame;
import com.sfb.scenario.ScenarioSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Continuing a saved battle, possibly with different people than fought it.
 *
 * <h2>The seat question, and the owner's answer to it</h2>
 * Ownership runs {@code seat token → PlayerInfo → corePlayer → Ship.owner}, and {@code start()}
 * was the only place that chain was ever built. A save records the right-hand end — which TEAM
 * owned which ship — and nothing about the left: tokens are per-session and were never in the
 * file. So a restored battle was correct in the engine and unplayable through the server, with
 * every player shown no ships and treated as a spectator.
 *
 * <p>The owner's decision was to load the battle and then REASSIGN people to units, so that a
 * saved game can be continued by new players. That is what these tests pin: a save opens as a new
 * game with every ship unclaimed, and the host hands them out with the same controls a new game
 * uses.
 *
 * <h2>The case that would have gone quietly wrong</h2>
 * Ships change hands. D7.50 gives a captured hull to its captor, so the team holding a ship when
 * the save was taken may not be the side the scenario started it on. Offering the scenario's view
 * would hand every capture back to the player it was taken from, undoing something somebody
 * fought for — so the teams offered are the ones that held each ship AT SAVE TIME.
 */
class ResumeSavedGameTest {

    private GameSessionService service;

    @BeforeEach
    void setUp() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        service = new GameSessionService();
    }

    // ---------------------------------------------------------------- opening a save

    @Test
    void aSaveOpensAsANewGameWithItsOwnCode() {
        GameSessionService.SessionFromSave opened = openBattle();

        assertNotNull(opened.session());
        assertEquals(List.of(), opened.problems());
        assertTrue(opened.session().isStarted(), "a restored battle is already under way");
        assertTrue(opened.session().isRestored());
        assertEquals(2, opened.session().getGame().getShips().size());
    }

    /** The clock and the damage come with it — this is a continuation, not a restart. */
    @Test
    void theBattleComesBackWhereItWasLeft() {
        GameSessionService.SessionFromSave opened = openBattle();

        assertEquals(1, opened.session().getGame().getClock().getTurn());
        Ship hood = shipNamed(opened.session(), "USS Hood");
        assertTrue(hood.getShields().getShieldStrength(2) < hood.getShields()
                .getMaxShieldStrength(2), "the Hood was damaged before the save");
    }

    /** Nobody commands anything until the host says so. */
    @Test
    void everyShipStartsUnclaimed() {
        GameSession session = openBattle().session();

        assertEquals(2, session.getUnassignedShipNames().size());
        assertTrue(session.getAssignedShipsFor(session.getHostToken()).isEmpty());
        // Before anything is claimed a restored battle behaves exactly like a fresh one: with no
        // ship owned by anybody, every viewer sees all of them. That is the existing
        // solo/dev-mode rule in getEffectiveShipNamesForPlayer, not something special to saves,
        // and it stops applying the moment the first ship is assigned.
        assertEquals(2, session.getEffectiveShipNamesForPlayer(session.getHostToken()).size(),
                "an unclaimed battle is omniscient, the same as a fresh one");
    }

    // ---------------------------------------------------------------- reassigning people

    /**
     * The fix for the seat question: assignment now wires the chain immediately when the game is
     * already started, because {@code start()} cannot run again.
     */
    @Test
    void assigningAShipInARestoredBattleGivesTheSeatCommandOfIt() {
        GameSession session = openBattle().session();
        String host = session.getHostToken();

        assertNull(session.assignShip(host, "USS Hood"));

        assertEquals(List.of("USS Hood"), session.getEffectiveShipNamesForPlayer(host));
        Ship hood = shipNamed(session, "USS Hood");
        assertNotNull(hood.getOwner());
        assertEquals("Starfleet", hood.getOwner().getTeamName());
        assertEquals("Starfleet", session.getTeamNameFor(host),
                "a seat with no team is treated as a spectator and shown a redacted battle");
    }

    /** And the whole-side control works just as it does in a new game. */
    @Test
    void aWholeSideCanBeHandedToAPlayerInARestoredBattle() {
        GameSession session = openBattle().session();
        String dave = service.joinSession(session.getId(), "Dave", "account-dave");

        GameSession.SideAssignmentResult result = session.assignSide(dave, "Empire");

        assertNull(result.error());
        assertEquals(List.of("IKV Blade"), result.assigned());
        assertEquals(List.of("IKV Blade"), session.getEffectiveShipNamesForPlayer(dave));
        assertEquals("Empire", session.getTeamNameFor(dave));
    }

    /** Different people than fought it — the point of the whole design. */
    @Test
    void aBattleCanBeContinuedByPeopleWhoWereNotInIt() {
        GameSession session = openBattle().session();
        String erin = service.joinSession(session.getId(), "Erin", "account-erin");
        String frank = service.joinSession(session.getId(), "Frank", "account-frank");

        assertNull(session.assignSide(erin, "Starfleet").error());
        assertNull(session.assignSide(frank, "Empire").error());

        assertEquals(List.of("USS Hood"), session.getEffectiveShipNamesForPlayer(erin));
        assertEquals(List.of("IKV Blade"), session.getEffectiveShipNamesForPlayer(frank));
        assertTrue(session.getUnassignedShipNames().isEmpty());
    }

    /**
     * Taking a ship off one player must correct the LOSER's unit list too, not just the ship's
     * pointer — otherwise it answers to both and the loser is still shown a ship they cannot
     * order.
     */
    @Test
    void movingAShipBetweenPlayersLeavesItWithOnlyOne() {
        GameSession session = openBattle().session();
        String host = session.getHostToken();
        String dave = service.joinSession(session.getId(), "Dave", "account-dave");
        assertNull(session.assignShip(host, "USS Hood"));

        // The host changes their mind. assignShip refuses to steal, so the move is two explicit
        // steps — release, then assign — and nothing is taken from anybody implicitly.
        assertEquals("Ship already assigned to another player",
                session.assignShip(dave, "USS Hood"),
                "a ship must not be takeable out from under its holder");

        assertNull(session.releaseShip("USS Hood"));
        assertNull(session.assignShip(dave, "USS Hood"));

        assertEquals(List.of("USS Hood"), session.getEffectiveShipNamesForPlayer(dave));
        assertTrue(session.getEffectiveShipNamesForPlayer(host).isEmpty(),
                "the previous holder must not still be shown it");
    }

    // ---------------------------------------------------------------- captures

    /**
     * THE CASE THAT WOULD HAVE GONE QUIETLY WRONG. The Klingon ship was captured by Starfleet
     * before the save, so at save time BOTH hulls belonged to Starfleet. Reassignment must offer
     * it as Starfleet's, not hand it back to the Empire because that is the side the scenario
     * started it on.
     */
    @Test
    void aCapturedShipKeepsTheTeamThatHeldItWhenSaved() throws Exception {
        Game2 built = battle();
        // D7.50: the Blade is taken, and its owner becomes the capturing side.
        Ship blade = null;
        for (Ship ship : built.game.getShips())
            if ("IKV Blade".equals(ship.getName()))
                blade = ship;
        Player starfleet = built.game.getPlayers().stream()
                .filter(p -> "Starfleet".equals(p.getTeamName())).findFirst().orElseThrow();
        blade.getOwner().getPlayerUnits().remove(blade);
        blade.setOwner(starfleet);
        starfleet.getPlayerUnits().add(blade);

        SavedGame saved = SavedGame.fromJsonString(
                SavedGame.of(built.game, built.spec, "after a capture").toJsonString());
        GameSession session = service.openSavedGame("Dan", "account-dan", saved).session();

        // Handing Starfleet to one player should bring the captured hull with it.
        String host = session.getHostToken();
        GameSession.SideAssignmentResult result = session.assignSide(host, "Starfleet");

        assertNull(result.error());
        assertTrue(result.assigned().contains("IKV Blade"),
                "the captured ship belongs to its captor: " + result.assigned());
        assertTrue(result.assigned().contains("USS Hood"));
        assertEquals("Starfleet",
                shipNamed(session, "IKV Blade").getOwner().getTeamName());
    }

    // ---------------------------------------------------------------- refusals

    @Test
    void aSaveWithNoScenarioOpensNothingAndSaysWhy() {
        SavedGame broken = new SavedGame();
        broken.state = Map.of();

        GameSessionService.SessionFromSave opened =
                service.openSavedGame("Dan", "account-dan", broken);

        assertNull(opened.session());
        assertFalse(opened.problems().isEmpty());
    }

    // ---------------------------------------------------------------- helpers

    /** A built battle and the spec it came from, since a Game does not remember its spec. */
    private record Game2(com.sfb.Game game, ScenarioSpec spec) {
    }

    private GameSessionService.SessionFromSave openBattle() {
        try {
            Game2 built = battle();
            SavedGame saved = SavedGame.fromJsonString(
                    SavedGame.of(built.game, built.spec, "resume fixture").toJsonString());
            return service.openSavedGame("Dan", "account-dan", saved);
        } catch (Exception e) {
            throw new AssertionError("fixture", e);
        }
    }

    /**
     * A battle a turn in, with owners wired the way {@code start()} wires them — because
     * ownership is NOT something setupFromScenario does, and the save has to have some to carry.
     */
    private Game2 battle() {
        ScenarioSpec spec = spec();
        com.sfb.Game game = new com.sfb.Game();
        game.setupFromScenario(spec);
        for (int i = 0; i < 40; i++)
            game.getClock().nextImpulse();

        Ship hood = null;
        Ship blade = null;
        for (Ship ship : game.getShips()) {
            if ("USS Hood".equals(ship.getName()))
                hood = ship;
            if ("IKV Blade".equals(ship.getName()))
                blade = ship;
        }
        hood.getShields().damageShield(2, 6);
        blade.getShields().damageShield(4, 3);

        own(game, "Starfleet", "Dan", hood);
        own(game, "Empire", "Dave", blade);
        return new Game2(game, spec);
    }

    private static void own(com.sfb.Game game, String team, String name, Ship... ships) {
        Player player = new Player();
        player.setName(name);
        player.setTeamName(team);
        for (Ship ship : ships) {
            player.getPlayerUnits().add(ship);
            ship.setOwner(player);
        }
        game.getPlayers().add(player);
    }

    private static ScenarioSpec spec() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Resume fixture";
        spec.year = 180;
        spec.mapCols = 42;
        spec.mapRows = 32;

        ScenarioSpec.SideSpec fed = new ScenarioSpec.SideSpec();
        fed.faction = "Federation";
        fed.name = "Starfleet";
        fed.ships = new ArrayList<>(List.of(ship("Federation", "CA", "USS Hood", "1010")));

        ScenarioSpec.SideSpec klingon = new ScenarioSpec.SideSpec();
        klingon.faction = "Klingon";
        klingon.name = "Empire";
        klingon.ships = new ArrayList<>(List.of(ship("Klingon", "D7", "IKV Blade", "1016")));

        spec.sides = new ArrayList<>(List.of(fed, klingon));
        return spec;
    }

    private static ScenarioSpec.ShipSetup ship(String faction, String type, String name,
            String hex) {
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.faction = faction;
        setup.type = type;
        setup.shipName = name;
        setup.startHex = hex;
        setup.startHeading = "A";
        return setup;
    }

    private static Ship shipNamed(GameSession session, String name) {
        for (Ship ship : session.getGame().getShips())
            if (name.equals(ship.getName()))
                return ship;
        throw new AssertionError("no ship called " + name);
    }
}
