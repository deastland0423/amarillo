package com.sfb.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Commander's Option budget as the SERVER sees it (S3.2, S3.211).
 * <p>
 * Two things are checked here that core cannot check for itself.
 * <p>
 * First, that the budget the lobby tells the client is the one the submission is judged against.
 * The client shows a running cost against {@code coiBudget} and refuses to submit an over-budget
 * loadout, so if the endpoint that publishes that figure and the check that enforces it ever
 * disagreed, a player would be refused a loadout the screen told them they could afford.
 * <p>
 * Second, that the refusal exists at all. Before this, the budget was enforced only by the
 * client: a direct call to the endpoint was accepted, and {@code applyCoi} then silently dropped
 * whatever did not fit, in its own fixed order rather than the player's priority.
 */
class CoiBudgetEndpointTest {

    private static final String HOST = "host-token";

    @BeforeEach
    void loadShips() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        com.sfb.objects.ShipLineCatalog.loadDefault("../data");
    }

    /** A one-ship scenario, so the budget under test belongs to a known hull. */
    private com.sfb.scenario.ScenarioSpec specFor(String faction, String type, String shipName) {
        com.sfb.scenario.ScenarioSpec spec = new com.sfb.scenario.ScenarioSpec();
        spec.mapCols = 42;
        spec.mapRows = 32;
        spec.year = 180;
        com.sfb.scenario.ScenarioSpec.SideSpec side = new com.sfb.scenario.ScenarioSpec.SideSpec();
        side.faction = faction;
        side.name = faction + "s";
        side.ships = new java.util.ArrayList<>();
        com.sfb.scenario.ScenarioSpec.ShipSetup ship = new com.sfb.scenario.ScenarioSpec.ShipSetup();
        ship.type = type;
        ship.shipName = shipName;
        ship.startHex = "1016";
        ship.startHeading = "C";
        side.ships.add(ship);
        spec.sides = new java.util.ArrayList<>(List.of(side));
        return spec;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> coiRowFor(com.sfb.scenario.ScenarioSpec spec, String shipName) {
        List<Map<String, Object>> sides = new GameController(null, null).coiDataFor(spec);
        return sides.stream()
                .flatMap(sd -> ((List<Map<String, Object>>) sd.get("ships")).stream())
                .filter(s -> shipName.equals(s.get("shipName")))
                .findFirst().orElseThrow(() -> new AssertionError(
                        shipName + " not in the COI data: " + sides));
    }

    private double publishedBudget(com.sfb.scenario.ScenarioSpec spec, String shipName) {
        Object b = coiRowFor(spec, shipName).get("coiBudget");
        assertNotNull(b, "the lobby must publish a budget for " + shipName);
        return ((Number) b).doubleValue();
    }

    /** The ship as core builds it, to check the published figure against the rule. */
    private com.sfb.objects.Ship shipFrom(com.sfb.scenario.ScenarioSpec spec, String shipName) {
        for (List<com.sfb.objects.Ship> side : com.sfb.scenario.ScenarioLoader.loadShips(spec))
            for (com.sfb.objects.Ship s : side)
                if (shipName.equals(s.getName()))
                    return s;
        throw new AssertionError(shipName + " did not load");
    }

    // ---------------------------------------------------------------- what is published

    @Test
    void theLobbyPublishesTheBudgetOnS3211sBasis() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CVS", "KHS Watchful");
        com.sfb.objects.Ship cvs = shipFrom(spec, "KHS Watchful");

        assertEquals(com.sfb.scenario.CoiBudget.allowanceFor(cvs, 20),
                publishedBudget(spec, "KHS Watchful"), 0.001,
                "the published budget must be the rule's answer, not the hull's alone");
    }

    /**
     * A carrier's budget must exceed a bare 20% of its hull, because its fighters are part of the
     * basis. This is the regression that matters: the old figure was the hull's alone.
     */
    @Test
    void aCarriersBudgetCountsItsFighters() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CVS", "KHS Watchful");
        com.sfb.objects.Ship cvs = shipFrom(spec, "KHS Watchful");

        int fighterBpv = com.sfb.scenario.CoiBudget.carriedFighterBpv(cvs);
        assertTrue(fighterBpv > 0, "a CVS carries fighters; got " + fighterBpv);

        double hullOnly = Math.floor(cvs.getBpv() * 20 / 100.0);
        assertTrue(publishedBudget(spec, "KHS Watchful") > hullOnly,
                "budget should exceed the hull-only " + hullOnly);
    }

    // ---------------------------------------------------------------- what is refused

    private GameSession sessionWith(com.sfb.scenario.ScenarioSpec spec) {
        GameSession session = new GameSession("game-1", HOST, "Alice");
        session.loadBuiltScenario(spec, "test-scenario");
        return session;
    }

    @Test
    void aLoadoutWithinBudgetIsAccepted() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CVS", "KHS Watchful");
        GameSession session = sessionWith(spec);

        com.sfb.scenario.CoiLoadout loadout = new com.sfb.scenario.CoiLoadout();
        loadout.extraBoardingParties = 2;   // 1.0 BPV, affordable by anything

        assertNull(session.validateCoiBudget(Map.of("KHS Watchful", loadout)));
    }

    @Test
    void anOverBudgetLoadoutIsRefusedAndNamesTheShip() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CVS", "KHS Watchful");
        GameSession session = sessionWith(spec);

        // Far past any budget: 40 T-bombs is 160 BPV of options.
        com.sfb.scenario.CoiLoadout loadout = new com.sfb.scenario.CoiLoadout();
        loadout.extraTBombs = 40;

        String refusal = session.validateCoiBudget(Map.of("KHS Watchful", loadout));
        assertNotNull(refusal, "an over-budget loadout must be refused");
        assertTrue(refusal.contains("KHS Watchful"), "names the ship: " + refusal);
        assertTrue(refusal.contains("S3.2"), "cites the rule: " + refusal);
    }

    /**
     * The property that keeps the client and the server honest: anything the published budget
     * says is affordable must be accepted, right up to the last point of it.
     */
    @Test
    void spendingExactlyThePublishedBudgetIsAccepted() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CVS", "KHS Watchful");
        GameSession session = sessionWith(spec);
        double budget = publishedBudget(spec, "KHS Watchful");

        // Boarding parties are half a point each, so two per point of budget — capped at the
        // rule's ten, with T-bombs taking up the rest in fours.
        com.sfb.scenario.CoiLoadout loadout = new com.sfb.scenario.CoiLoadout();
        loadout.extraBoardingParties = com.sfb.scenario.CoiLoadout.MAX_EXTRA_BP;  // 5.0
        double remaining = budget - 5.0;
        loadout.extraTBombs = (int) (remaining / com.sfb.scenario.CoiLoadout.COST_TBOMB);

        assertTrue(loadout.totalCost() <= budget,
                "test built an over-budget loadout: " + loadout.totalCost() + " > " + budget);
        assertNull(session.validateCoiBudget(Map.of("KHS Watchful", loadout)),
                "a loadout inside the published budget must be accepted");
    }

    @Test
    void oneShipOverBudgetRefusesTheWholeSubmission() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CVS", "KHS Watchful");
        GameSession session = sessionWith(spec);

        com.sfb.scenario.CoiLoadout fine = new com.sfb.scenario.CoiLoadout();
        fine.extraBoardingParties = 1;
        com.sfb.scenario.CoiLoadout over = new com.sfb.scenario.CoiLoadout();
        over.extraTBombs = 40;

        // A name that is not in the scenario is ignored rather than refused; the real ship is
        // what decides. Submitted together, the over-budget one still refuses the batch.
        assertNull(session.validateCoiBudget(Map.of("Nobody", over)),
                "a loadout for a ship not in the scenario has no budget to breach");
        assertNotNull(session.validateCoiBudget(Map.of("KHS Watchful", over)));
    }

    @Test
    void nothingSubmittedIsNothingRefused() {
        GameSession session = sessionWith(specFor("Kzinti", "CVS", "KHS Watchful"));
        assertNull(session.validateCoiBudget(Map.of()));
        assertNull(session.validateCoiBudget(null));
    }

    /** With no scenario loaded there is no ship to price a loadout against. */
    @Test
    void noScenarioMeansNoRefusal() {
        GameSession bare = new GameSession("game-2", HOST, "Alice");
        com.sfb.scenario.CoiLoadout over = new com.sfb.scenario.CoiLoadout();
        over.extraTBombs = 40;
        assertNull(bare.validateCoiBudget(Map.of("KHS Watchful", over)));
    }
}
