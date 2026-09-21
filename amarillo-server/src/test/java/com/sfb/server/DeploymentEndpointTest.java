package com.sfb.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Setting a fleet down: whose ships, which ground, and when everyone is finished.
 * <p>
 * The two things worth pinning are that a placement nobody may see stays unseen — the room is
 * told a count and nothing else — and that Done can be taken back, because there is no
 * advantage in changing your mind about a secret and clicking it early should not cost the
 * battle.
 */
class DeploymentEndpointTest {

    private GameController controller;
    private GameSession session;
    private String hostToken;

    @BeforeEach
    void setUp() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        GameSessionService service = new GameSessionService();
        controller = new GameController(service, null);

        session = service.createSession("dan");
        hostToken = session.getPlayers().keySet().iterator().next();

        // A battle with ground to stand on: two fleets, left and right bands.
        com.sfb.scenario.FleetSpec fleet = new com.sfb.scenario.FleetSpec();
        fleet.name = "Klingons";
        fleet.factions = new java.util.ArrayList<>(List.of("Klingon"));
        fleet.year = 180;
        fleet.budget = 1000;
        for (String type : List.of("D7", "D6")) {
            com.sfb.scenario.FleetSpec.ShipEntry e = new com.sfb.scenario.FleetSpec.ShipEntry();
            e.type = type;
            e.name = type;
            fleet.ships.add(e);
        }
        fleet.flagship = "D7";

        com.sfb.scenario.ScenarioSpec spec = com.sfb.scenario.FleetsToScenario.build(
                List.of(new com.sfb.scenario.FleetsToScenario.Entry(
                        fleet, "Klingons", com.sfb.scenario.MapRegion.band("LEFT", 6))),
                com.sfb.scenario.FleetsToScenario.Conditions.defaults(180, 1000));
        session.loadBuiltScenario(spec, "fleet-battle");

        session.assignShip(hostToken, "D7");
        session.assignShip(hostToken, "D6");
    }

    private PlacementRequestBuilder place(String ship, String hex, String heading) {
        return new PlacementRequestBuilder(ship, hex, heading);
    }

    private static class PlacementRequestBuilder {
        final GameController.PlacementRequest req = new GameController.PlacementRequest();
        PlacementRequestBuilder(String ship, String hex, String heading) {
            req.shipName = ship;
            req.hex = hex;
            req.heading = heading;
        }
    }

    private ResponseEntity<Map<String, Object>> submit(String token, PlacementRequestBuilder... ps) {
        GameController.DeploymentRequest req = new GameController.DeploymentRequest();
        for (PlacementRequestBuilder p : ps)
            req.placements.add(p.req);
        return controller.submitDeployment(session.getId(), token, req);
    }

    private Map<String, Object> deployment(String token) {
        return controller.getDeployment(session.getId(), token).getBody();
    }

    private ResponseEntity<Map<String, Object>> done(String token, boolean done) {
        return controller.setDeploymentDone(session.getId(), token,
                Map.of("done", done));
    }

    // ---- the ground ----

    @Test
    void aPlayerIsToldWhichShipsAreTheirsAndWhereTheyMayStand() {
        Map<String, Object> d = deployment(hostToken);

        assertEquals(Boolean.TRUE, d.get("required"));
        assertEquals(List.of("D7", "D6"), d.get("ships"));
        LobbyStateDto.ZoneDto zone = (LobbyStateDto.ZoneDto) d.get("zone");
        assertNotNull(zone);
        assertTrue(zone.describe.contains("left edge"), zone.describe);
        assertFalse(zone.hexes.isEmpty());
    }

    @Test
    void aShipInsideTheZoneIsAccepted() {
        assertEquals(200, submit(hostToken, place("D7", "0316", "C")).getStatusCode().value());
        assertEquals(1, ((List<?>) deployment(hostToken).get("placements")).size());
    }

    @Test
    void aShipOutsideTheZoneIsRefusedAndToldWhy() {
        ResponseEntity<Map<String, Object>> r = submit(hostToken, place("D7", "3016", "C"));

        assertEquals(400, r.getStatusCode().value());
        assertTrue(r.getBody().toString().contains("deployment area"), r.getBody().toString());
    }

    @Test
    void aShipThatIsNotYoursCannotBePlaced() {
        ResponseEntity<Map<String, Object>> r = submit(hostToken, place("Someone Else", "0316", "C"));

        assertEquals(400, r.getStatusCode().value());
        assertTrue(r.getBody().toString().contains("not yours"), r.getBody().toString());
    }

    // ---- a setup is replaced wholesale, so a ship can simply be moved ----

    @Test
    void aShipCanBeMovedByPlacingItSomewhereElse() {
        submit(hostToken, place("D7", "0316", "C"));
        submit(hostToken, place("D7", "0520", "F"));

        List<?> placed = (List<?>) deployment(hostToken).get("placements");
        assertEquals(1, placed.size(), "moved, not duplicated");
        assertTrue(placed.toString().contains("0520"), placed.toString());
        assertTrue(placed.toString().contains("F"), placed.toString());
    }

    // ---- finishing ----

    @Test
    void youCannotFinishWithShipsStillInHand() {
        submit(hostToken, place("D7", "0316", "C"));

        ResponseEntity<Map<String, Object>> r = done(hostToken, true);
        assertEquals(400, r.getStatusCode().value());
        assertEquals(Boolean.FALSE, deployment(hostToken).get("done"));
    }

    @Test
    void everyShipDownMeansYouMayFinish() {
        submit(hostToken, place("D7", "0316", "C"), place("D6", "0318", "C"));

        assertEquals(Boolean.TRUE, deployment(hostToken).get("complete"));
        assertEquals(200, done(hostToken, true).getStatusCode().value());
        assertEquals(Boolean.TRUE, deployment(hostToken).get("done"));
    }

    @Test
    void doneCanBeTakenBack() {
        submit(hostToken, place("D7", "0316", "C"), place("D6", "0318", "C"));
        done(hostToken, true);

        assertEquals(200, done(hostToken, false).getStatusCode().value());
        assertEquals(Boolean.FALSE, deployment(hostToken).get("done"));
    }

    /** Taking a ship back off the map un-finishes you, rather than leaving a stale Done. */
    @Test
    void removingAShipAfterFinishingUnfinishesYou() {
        submit(hostToken, place("D7", "0316", "C"), place("D6", "0318", "C"));
        done(hostToken, true);
        assertEquals(Boolean.TRUE, deployment(hostToken).get("done"));

        submit(hostToken, place("D7", "0316", "C"));   // D6 taken back
        assertEquals(Boolean.FALSE, deployment(hostToken).get("done"));
    }

    // ---- secrecy ----

    /**
     * The room hears a count. It never hears a hex — checked against the serialised broadcast
     * rather than the object, because what leaves the server is the JSON.
     */
    @Test
    void theBroadcastCarriesProgressButNotPositions() throws Exception {
        submit(hostToken, place("D7", "0316", "C"));

        LobbyStateDto lobby = new LobbyStateDto(session);
        LobbyStateDto.PlayerDto me = lobby.players.get(0);

        assertEquals(1, me.shipsPlaced);
        assertFalse(me.deploymentDone);
        assertTrue(lobby.deploymentRequired);

        String wire = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(lobby);
        assertTrue(wire.contains("shipsPlaced"), "progress is broadcast");
        assertFalse(wire.contains("placements"), "placements reached the broadcast: " + wire);

        // The ship setups still carry the starting line they were assembled on, not the hex
        // the player chose. Searching the whole document for "0316" proves nothing — the
        // zone's own hex list contains it, as it must.
        LobbyStateDto.ShipDto d7 = lobby.sides.get(0).ships.stream()
                .filter(sh -> sh.shipName.equals("D7")).findFirst().orElseThrow();
        assertNotEquals("0316", d7.startHex, "the chosen hex leaked into the broadcast setup");
    }

    // ---- a scenario that names every hex wants none of this ----

    /**
     * A hand-written scenario fixes every starting hex, so there is nothing to place and
     * nothing to wait for. Built here rather than read from data/scenarios, which resolves
     * against the repo root the server runs from and not the module surefire does.
     */
    @Test
    void aScenarioWithoutZonesNeedsNoDeployment() {
        GameSessionService service = new GameSessionService();
        GameController c = new GameController(service, null);
        GameSession s = service.createSession("dan");

        com.sfb.scenario.ScenarioSpec spec = new com.sfb.scenario.ScenarioSpec();
        spec.mapCols = 42;
        spec.mapRows = 32;
        com.sfb.scenario.ScenarioSpec.SideSpec side = new com.sfb.scenario.ScenarioSpec.SideSpec();
        side.faction = "Klingon";
        side.name = "Klingons";
        side.ships = new java.util.ArrayList<>();
        com.sfb.scenario.ScenarioSpec.ShipSetup ship = new com.sfb.scenario.ScenarioSpec.ShipSetup();
        ship.type = "D7";
        ship.shipName = "D7";
        ship.startHex = "1016";
        ship.startHeading = "C";
        ship.startSpeed = 16;
        side.ships.add(ship);
        spec.sides = new java.util.ArrayList<>(java.util.List.of(side));   // no deploymentZone
        s.loadBuiltScenario(spec, "written");

        assertFalse(s.isDeploymentRequired());
        assertTrue(s.allDeploymentDone(), "nothing to wait for");
        assertEquals(Boolean.FALSE,
                c.getDeployment(s.getId(), s.getPlayers().keySet().iterator().next())
                        .getBody().get("required"));
    }
}
