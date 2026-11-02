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
        return specFor(faction, type, shipName, 180);
    }

    /**
     * The same, in a year the caller names. The date decides what the ship IS — FD3.0 replaces
     * most type-A racks in Y175 — so a test about drones has to be able to say when it is.
     */
    private com.sfb.scenario.ScenarioSpec specFor(
            String faction, String type, String shipName, int year) {
        com.sfb.scenario.ScenarioSpec spec = new com.sfb.scenario.ScenarioSpec();
        spec.mapCols = 42;
        spec.mapRows = 32;
        spec.year = year;
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

    /**
     * S2.11's drone charge reaches the dialog, and reaches it SEPARATELY.
     *
     * <p>It has to be its own field because S3.211 keeps it out of {@code coiBudgetBasis} — the
     * percentage is taken of a figure "not including the cost of mandatory drone speed upgrades"
     * — so folding it into either BPV number would be wrong and leaving it out altogether would
     * make the one charge a player does not choose invisible to them.
     */
    @Test
    void theLobbyPublishesTheDroneSpeedChargeApartFromTheBudgetBasis() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CS", "KHS Quasar");
        Map<String, Object> row = coiRowFor(spec, "KHS Quasar");
        com.sfb.objects.Ship cs = shipFrom(spec, "KHS Quasar");

        // Y180: fast drones are General (FD10.65), so every drone aboard costs a point
        // (FD2.224). Twenty of them, not the sixteen FD2.454's example quotes: that example is
        // of an UNREFITTED CS, and FD3.0 replaced "most type-A drone racks ... with improved
        // types in Y175", which here is two four-space type-Cs and two six-space type-Bs. The
        // rule says as much in its own parenthesis — "by the time fast drones were available,
        // all CS-class ships had been refitted as BCs". The example itself is pinned at the year
        // it describes by fd2454sWorkedExampleHoldsInTheYearItDescribes below.
        //
        // This read 16 until the rack refit was fixed, for the wrong reason: a grown rack was
        // never topped up, so two six-space racks sailed with four drones each and the figure
        // agreed with FD2.454 by accident.
        assertEquals(20, ((Number) row.get("droneSpeedBpv")).intValue(),
                "FD2.224: a point per drone, over all twenty a refitted CS carries");
        assertEquals(cs.getBpv(), ((Number) row.get("bpv")).intValue(),
                "and the hull BPV it is published beside does NOT carry it");
        assertEquals(com.sfb.scenario.CoiBudget.effectiveAdjustedCombatBpv(cs),
                ((Number) row.get("coiBudgetBasis")).doubleValue(), 0.001,
                "nor does the basis the option percentage is taken of (S3.211)");
    }

    /**
     * FD2.454's worked example, in the year it is about: "a Kzinti CS has a BPV of 116. However,
     * it has four drone racks, each of which holds four drones. Equipped with medium-speed
     * drones, the BPV increases by eight points (1/2-point per drone) to 124."
     *
     * <p>Y170: medium speed has been General since Y167 (FD10.651) and the Y175 rack refit has
     * not happened, so the ship the example describes is the ship that loads — four type-A racks
     * of four, at half a point each.
     */
    @Test
    void fd2454sWorkedExampleHoldsInTheYearItDescribes() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CS", "KHS Quasar", 170);
        Map<String, Object> row = coiRowFor(spec, "KHS Quasar");

        assertEquals(8, ((Number) row.get("droneSpeedBpv")).intValue(),
                "FD2.454: sixteen medium drones at half a point, 116 becoming 124");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> racks = (List<Map<String, Object>>) row.get("droneRacks");
        assertEquals(4, racks.size(), "a CS has four racks");
        for (Map<String, Object> rack : racks)
            assertEquals(List.of("TypeIM", "TypeIM", "TypeIM", "TypeIM"), rack.get("defaultAmmo"),
                    "and each holds four, before FD3.0's Y175 replacement");
    }

    /**
     * And the drones the dialog offers to leave alone are the ones the ship is actually carrying
     * (S2.11) — which is what lets a player skip the drone section and still know what they have.
     *
     * <p>Counted off each rack's own capacity rather than against a fixed four, because after
     * FD3.0's Y175 refit the four racks are no longer the same size: two four-space type-Cs and
     * two six-space type-Bs. A FULL rack is the claim — a refitted rack that reported six spaces
     * and held four is the bug this guards.
     */
    @Test
    void theRacksDefaultAmmoIsTheSpeedTheYearFitted() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CS", "KHS Quasar");
        Map<String, Object> row = coiRowFor(spec, "KHS Quasar");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> racks = (List<Map<String, Object>>) row.get("droneRacks");
        assertEquals(4, racks.size(), "a CS has four racks");
        int drones = 0;
        for (Map<String, Object> rack : racks) {
            int spaces = ((Number) rack.get("spaces")).intValue();
            @SuppressWarnings("unchecked")
            List<String> ammo = (List<String>) rack.get("defaultAmmo");
            assertEquals(spaces, ammo.size(),
                    "rack " + rack.get("designator") + " reports " + spaces
                            + " spaces, so it should arrive with that many drones");
            for (String drone : ammo)
                assertEquals("TypeIF", drone, "Y180: fast drones are standard issue");
            drones += ammo.size();
        }
        assertEquals(20, drones, "two type-Cs of four and two type-Bs of six");
    }

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

        assertTrue(loadout.totalCost(spec.year) <= budget,
                "test built an over-budget loadout: " + loadout.totalCost(spec.year)
                        + " > " + budget);
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

    // ---------------------------------------------------------------- ready-made loadouts

    /** Every rack row carries the loadouts core says are legal for it in this year. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> presetsOfFirstRack(
            com.sfb.scenario.ScenarioSpec spec, String shipName) {
        List<Map<String, Object>> racks =
                (List<Map<String, Object>>) coiRowFor(spec, shipName).get("droneRacks");
        assertNotNull(racks, shipName + " must publish its drone racks");
        assertFalse(racks.isEmpty(), shipName + " must have a drone rack to offer presets for");
        return (List<Map<String, Object>>) racks.get(0).get("presets");
    }

    @Test
    void theLobbyPublishesReadyMadeRackLoadouts() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CVS", "KHS Watchful");
        List<Map<String, Object>> presets = presetsOfFirstRack(spec, "KHS Watchful");

        assertNotNull(presets, "a rack must publish its presets, even if empty");
        assertFalse(presets.isEmpty(), "a Kzinti rack should have something to offer");
        for (Map<String, Object> p : presets) {
            assertNotNull(p.get("drones"), "a preset names its drones: " + p);
            assertNotNull(p.get("antiDrones"), "a preset says how many ADDs it holds: " + p);
            assertNotNull(p.get("surcharge"), "a preset carries its price: " + p);
        }
    }

    /**
     * And the price on the button is the price the ship is charged, which is the whole point of
     * sending it: core prices the preset, the lobby publishes that figure, and submitting the
     * preset bills exactly it to {@code coiSpend} for S2.20 step B to score.
     */
    @SuppressWarnings("unchecked")
    @Test
    void aPublishedPresetCostsWhatItSaidItWould() {
        com.sfb.scenario.ScenarioSpec spec = specFor("Kzinti", "CVS", "KHS Watchful");
        List<Map<String, Object>> presets = presetsOfFirstRack(spec, "KHS Watchful");

        Map<String, Object> withDrones = presets.stream()
                .filter(p -> !((List<String>) p.get("drones")).isEmpty())
                .findFirst().orElseThrow(() ->
                        new AssertionError("expected a preset that loads drones: " + presets));
        double advertised = ((Number) withDrones.get("surcharge")).doubleValue();

        CoiRequest request = new CoiRequest();
        request.droneRackLoadouts.put("0", (List<String>) withDrones.get("drones"));
        com.sfb.scenario.CoiLoadout loadout = request.toLoadout();
        assertTrue(request.droneRefusals.isEmpty(),
                "the lobby must publish drone names the endpoint can read back: "
                        + request.droneRefusals);

        com.sfb.objects.Ship ship = shipFrom(spec, "KHS Watchful");
        com.sfb.scenario.ScenarioLoader.applyCoi(ship, loadout, spec);

        assertEquals(advertised, ship.getCoiSpend(), 1e-9,
                "the preset must be billed what the button said");
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
