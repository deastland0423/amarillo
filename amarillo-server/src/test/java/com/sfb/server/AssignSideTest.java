package com.sfb.server;

import com.sfb.scenario.ScenarioSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Handing a whole side to one player.
 *
 * <h2>Why a side is the right unit of assignment</h2>
 * Assignment was per ship, two clicks each, and a scenario has usually already decided the answer:
 * a fleet is brought FOR a side, and a scenario's own units are listed under the side that owns
 * them. Nivram lists six phaser batteries under the Defender, so a host was being asked to re-enter
 * the scenario file one ship at a time — twelve clicks to say something the file already said.
 *
 * <p>The per-ship route stays. Splitting one side between two players is a real thing to want, and
 * is the only reason assignment was ever per ship.
 *
 * <h2>The hazard this must not create</h2>
 * A Commander's Options loadout is keyed to a player token. Quietly moving a ship to somebody else
 * after COI would strand the choices its previous owner made for it, so ships another player
 * already holds are REPORTED, never seized.
 */
class AssignSideTest {

    private GameSessionService service;
    private GameSession session;
    private String host;
    private String guest;

    @BeforeEach
    void setUp() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        service = new GameSessionService();
        session = service.createSession("Dan", "account-dan");
        host = session.getHostToken();
        guest = service.joinSession(session.getId(), "Dave", "account-dave");
        session.loadBuiltScenario(spec(), "two-sided");
    }

    /**
     * Two sides. The Defender has a cruiser and two ground batteries sited on a planet — the
     * batteries being exactly the units a host should never have to enter by hand.
     */
    private ScenarioSpec spec() {
        ScenarioSpec s = new ScenarioSpec();
        s.name = "Two sides";
        s.year = 180;
        s.mapCols = 42;
        s.mapRows = 32;

        ScenarioSpec.TerrainSetup planet = new ScenarioSpec.TerrainSetup();
        planet.type = "PLANET";
        planet.hex = "2116";
        planet.name = "Nivram";
        s.terrain = new ArrayList<>(List.of(planet));

        ScenarioSpec.SideSpec defender = new ScenarioSpec.SideSpec();
        defender.faction = "Klingon";
        defender.name = "Defender";
        defender.ships = new ArrayList<>();
        defender.ships.add(ship("Klingon", "D7", "IKV Defender", "2118"));
        for (String face : List.of("A", "B")) {
            ScenarioSpec.ShipSetup base = new ScenarioSpec.ShipSetup();
            base.faction = "Neutral";
            base.type = "GBD2";
            base.shipName = "Nivram Battery " + face;
            base.planet = "Nivram";
            base.planetFace = face;
            defender.ships.add(base);
        }

        ScenarioSpec.SideSpec attacker = new ScenarioSpec.SideSpec();
        attacker.faction = "Kzinti";
        attacker.name = "Attacker";
        attacker.ships = new ArrayList<>(List.of(
                ship("Kzinti", "CA", "KHS Attacker", "1010")));

        s.sides = new ArrayList<>(List.of(defender, attacker));
        return s;
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

    // ---------------------------------------------------------------- the happy path

    @Test
    void oneCallHandsOverEveryShipOnTheSide() {
        GameSession.SideAssignmentResult r = session.assignSide(host, "Defender");

        assertNull(r.error());
        assertEquals(3, r.assigned().size(), "cruiser plus both batteries: " + r.assigned());
        assertTrue(r.heldByOthers().isEmpty());

        List<String> mine = session.getAssignedShipsFor(host);
        assertTrue(mine.contains("IKV Defender"));
        assertTrue(mine.contains("Nivram Battery A"), mine.toString());
        assertTrue(mine.contains("Nivram Battery B"), mine.toString());
    }

    /** The point of it: the scenario already said whose the batteries were. */
    @Test
    void theScenariosOwnUnitsComeAlongWithTheSide() {
        session.assignSide(host, "Defender");

        assertTrue(session.getAssignedShipsFor(host).containsAll(
                List.of("Nivram Battery A", "Nivram Battery B")));
    }

    @Test
    void theOtherSideIsUntouched() {
        session.assignSide(host, "Defender");

        assertFalse(session.getAssignedShipsFor(host).contains("KHS Attacker"));
        assertTrue(session.getAssignedShipsFor(guest).isEmpty());
    }

    @Test
    void eachSideCanGoToADifferentPlayer() {
        assertNull(session.assignSide(host, "Defender").error());
        assertNull(session.assignSide(guest, "Attacker").error());

        assertEquals(3, session.getAssignedShipsFor(host).size());
        assertEquals(List.of("KHS Attacker"), session.getAssignedShipsFor(guest));
    }

    /** Two sides handed out means nothing is left waiting to be claimed. */
    @Test
    void handingOutBothSidesLeavesNothingUnassigned() {
        session.assignSide(host, "Defender");
        session.assignSide(guest, "Attacker");

        assertTrue(session.getUnassignedShipNames().isEmpty(),
                "still unassigned: " + session.getUnassignedShipNames());
    }

    // ---------------------------------------------------------------- it never seizes

    /**
     * The COI hazard. A loadout is keyed to a token, so a ship already held must be left where it
     * is and reported — not quietly moved.
     */
    @Test
    void aShipAnotherPlayerHoldsIsReportedNotTaken() {
        assertNull(session.assignShip(guest, "IKV Defender"));

        GameSession.SideAssignmentResult r = session.assignSide(host, "Defender");

        assertNull(r.error());
        assertEquals(List.of("IKV Defender"), r.heldByOthers());
        assertEquals(2, r.assigned().size(), "the batteries still came over: " + r.assigned());
        assertTrue(session.getAssignedShipsFor(guest).contains("IKV Defender"),
                "the ship must stay with the player who had it");
        assertFalse(session.getAssignedShipsFor(host).contains("IKV Defender"));
    }

    /** Running it twice is harmless — a ship this player already holds is not a conflict. */
    @Test
    void assigningTheSameSideTwiceIsIdempotent() {
        session.assignSide(host, "Defender");
        GameSession.SideAssignmentResult again = session.assignSide(host, "Defender");

        assertNull(again.error());
        assertTrue(again.heldByOthers().isEmpty(), "its own ships are not held by others");
        assertEquals(3, session.getAssignedShipsFor(host).size());
    }

    // ---------------------------------------------------------------- refusals

    @Test
    void anUnknownSideIsRefused() {
        GameSession.SideAssignmentResult r = session.assignSide(host, "Romulans");

        assertNotNull(r.error());
        assertTrue(r.error().contains("Romulans"), r.error());
        assertTrue(session.getAssignedShipsFor(host).isEmpty(), "nothing should have moved");
    }

    @Test
    void anUnknownPlayerIsRefused() {
        assertNotNull(session.assignSide("not-a-token", "Defender").error());
        assertTrue(session.getUnassignedShipNames().contains("IKV Defender"));
    }

    @Test
    void aBlankSideIsRefused() {
        assertNotNull(session.assignSide(host, "").error());
        assertNotNull(session.assignSide(host, null).error());
    }

    @Test
    void withoutAScenarioThereIsNothingToAssign() {
        GameSession bare = service.createSession("Erin", "account-erin");

        assertNotNull(bare.assignSide(bare.getHostToken(), "Defender").error());
    }

    // ---------------------------------------------------------------- the side list

    @Test
    void theSideNamesAreOfferedInScenarioOrder() {
        assertEquals(List.of("Defender", "Attacker"), session.sideNames());
    }
}
