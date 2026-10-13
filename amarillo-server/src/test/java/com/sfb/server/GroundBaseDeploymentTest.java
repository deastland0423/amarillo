package com.sfb.server;

import com.sfb.scenario.MapRegion;
import com.sfb.scenario.ScenarioSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A ground base is sited by the SCENARIO and must never reach the deployment screen (R1.14).
 *
 * <h2>The bug this pins</h2>
 * Reported from a live game: "the Nivram Battery bases are scattered around the map instead of
 * anchored to the planet. I shouldn't need to place them" — and then the game could not be
 * launched at all, even with both players through COI.
 *
 * <p>Both symptoms were the same mistake. The batteries were listed among the ships a player must
 * set down, and there was no legal place to put one. They carry no {@code startHex}, so
 * auto-arrange invented positions and spread them over the map; and the hex they actually belong
 * in is the PLANET'S, which {@code Deployment.check} refuses as no-entry (P2.224). Since
 * deployment is only complete when every assigned ship is placed somewhere legal, it could never
 * be completed, and the launch was blocked behind it.
 *
 * <p>The siting itself was never wrong — {@code setupFromScenario} puts them on their planet face
 * once the terrain is down. It simply runs at game start, which is after the screen that was
 * demanding they be placed by hand.
 */
class GroundBaseDeploymentTest {

    private GameSession session;
    private GameController controller;
    private String hostToken;

    @BeforeEach
    void setUp() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        GameSessionService service = new GameSessionService();
        controller = new GameController(service, null);

        session = service.createSession("dan");
        hostToken = session.getPlayers().keySet().iterator().next();

        session.loadBuiltScenario(spec(), "nivram-like");
        session.assignShip(hostToken, "IKV Escort");
        session.assignShip(hostToken, "Nivram Battery A");
        session.assignShip(hostToken, "Nivram Battery B");
    }

    /** A planet, two batteries on it, one ordinary ship, and ground to set that ship down on. */
    private ScenarioSpec spec() {
        ScenarioSpec s = new ScenarioSpec();
        s.name = "Nivram-like";
        s.year = 180;
        s.mapCols = 42;
        s.mapRows = 32;

        ScenarioSpec.TerrainSetup planet = new ScenarioSpec.TerrainSetup();
        planet.type = "PLANET";
        planet.hex = "2116";
        planet.name = "Nivram";
        s.terrain = new ArrayList<>(List.of(planet));

        ScenarioSpec.ShipSetup cruiser = new ScenarioSpec.ShipSetup();
        cruiser.faction = "Klingon";
        cruiser.type = "D7";
        cruiser.shipName = "IKV Escort";
        cruiser.startHex = "2118";
        cruiser.startHeading = "A";

        List<ScenarioSpec.ShipSetup> ships = new ArrayList<>(List.of(cruiser));
        for (String face : List.of("A", "B")) {
            ScenarioSpec.ShipSetup base = new ScenarioSpec.ShipSetup();
            base.faction = "Neutral";
            base.type = "GBD2";
            base.shipName = "Nivram Battery " + face;
            base.planet = "Nivram";
            base.planetFace = face;
            ships.add(base);
        }

        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = "Klingon";
        side.name = "Defender";
        side.deploymentZone = MapRegion.circle("2116", 3);
        side.ships = ships;
        s.sides = new ArrayList<>(List.of(side));
        return s;
    }

    @SuppressWarnings("unchecked")
    private List<String> shipsOnTheDeploymentScreen() {
        return (List<String>) controller.getDeployment(session.getId(), hostToken)
                .getBody().get("ships");
    }

    // ---------------------------------------------------------------- the screen

    @Test
    void aGroundBaseIsNotOfferedForPlacement() {
        List<String> offered = shipsOnTheDeploymentScreen();

        assertTrue(offered.contains("IKV Escort"), "the cruiser still needs placing: " + offered);
        assertFalse(offered.contains("Nivram Battery A"),
                "a ground base is sited by the scenario, not by a player: " + offered);
        assertFalse(offered.contains("Nivram Battery B"), offered.toString());
    }

    /** It is still THEIRS — the player commands it in play, they just do not position it. */
    @Test
    void theBaseIsStillOwnedByItsSide() {
        assertTrue(session.getAssignedShipsFor(hostToken).contains("Nivram Battery A"),
                "ownership and deployability are different questions");
    }

    // ---------------------------------------------------------------- the launch

    /**
     * The reported symptom. With the batteries in the required set, deployment could never be
     * finished, so the game could not start.
     */
    @Test
    void placingOnlyTheRealShipsCompletesDeployment() {
        assertFalse(session.isDeploymentComplete(hostToken), "fixture: nothing placed yet");

        List<String> problems = session.submitDeployment(hostToken, List.of(
                new com.sfb.scenario.Deployment.Placement("IKV Escort", "2118", "A", 0)));

        assertTrue(problems.isEmpty(), problems.toString());
        assertTrue(session.isDeploymentComplete(hostToken),
                "the batteries must not hold deployment open");
        assertNull(session.setDeploymentDone(hostToken, true),
                "and the player must be able to say they are finished");
    }

    /** Auto-arrange must not invent positions for them — that is what scattered them. */
    @Test
    void autoArrangeLeavesTheBasesAlone() {
        List<String> problems = session.autoArrange(hostToken);

        assertTrue(problems.isEmpty(), problems.toString());
        assertFalse(session.deploymentFor(hostToken).containsKey("Nivram Battery A"),
                "auto-arrange placed a ground base: " + session.deploymentFor(hostToken).keySet());
        assertTrue(session.deploymentFor(hostToken).containsKey("IKV Escort"));
    }

    /** A client holding a stale list may still send one; drop it rather than refuse the setup. */
    @Test
    void aStaleSubmissionNamingABaseIsIgnoredRatherThanRefused() {
        List<String> problems = session.submitDeployment(hostToken, List.of(
                new com.sfb.scenario.Deployment.Placement("IKV Escort", "2118", "A", 0),
                new com.sfb.scenario.Deployment.Placement("Nivram Battery A", "2119", "A", 0)));

        assertTrue(problems.isEmpty(), problems.toString());
        assertFalse(session.deploymentFor(hostToken).containsKey("Nivram Battery A"),
                "the scenario decides where it stands");
        assertTrue(session.isDeploymentComplete(hostToken));
    }
}
