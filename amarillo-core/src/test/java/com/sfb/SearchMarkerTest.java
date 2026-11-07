package com.sfb;

import com.sfb.dto.GameStateDto;
import com.sfb.objects.Objective;
import com.sfb.objects.SearchMarker;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.scenario.ScenarioSpec;
import com.sfb.scenario.TerrainGenerator;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Face-down counters (SH47.46): eighteen on the map, two of them Stasis Boxes, four withdrawn
 * from the stack unseen before any are placed.
 *
 * <h2>What is public and what is not</h2>
 * The counters' POSITIONS are public — SH47.471 has a ship search one by entering its hex, which
 * it could not choose to do if they were invisible. The assignment is the secret, and so is the
 * COUNT: withdraw four of twenty-two and it may be that two boxes exist, or one, or none.
 *
 * <h2>Why a marker rather than a hidden objective</h2>
 * A hidden objective has to be kept out of every list the DTO walks, and that went wrong twice
 * in one day — once through {@code mapObjects}, once through the live scoreboard, each needing
 * its own filter and the second found only by reading the rule text back against the DTO. A
 * counter's payload is not in any list until it is found, so there is nothing to filter.
 */
public class SearchMarkerTest {

    // ------------------------------------------------------------------ the deal

    @Test
    public void theFieldKeepsItsCounterPositionsWhenAsked() {
        TerrainGenerator.Generated g = TerrainGenerator.expand(
                List.of(field(true)), List.of(), 42, 30);

        assertEquals("P3.11 lays eighteen counters", 18, g.counters.size());
        assertFalse("and the field itself is still produced", g.terrain.isEmpty());
    }

    /** Off by default, or every scenario ever written would sprout eighteen counters. */
    @Test
    public void anOrdinaryFieldKeepsNoCounters() {
        assertTrue(TerrainGenerator.expand(List.of(field(false)), List.of(), 42, 30)
                .counters.isEmpty());
    }

    /**
     * The terrain is IDENTICAL whether counters are kept or not — the whole reason this is a
     * cheap change. Twenty-two less four is eighteen, so every position gets a counter and
     * every counter makes its asteroids, which is what SH47.46 means by "the removal of a
     * Stasis Box will NOT cause the asteroids centered on it to disappear."
     */
    @Test
    public void keepingTheCountersChangesNoTerrain() {
        TerrainGenerator.Plan kept = field(true);
        TerrainGenerator.Plan plain = field(false);
        kept.seed = 1234L;
        plain.seed = 1234L;

        assertEquals(TerrainGenerator.expand(List.of(plain), List.of(), 42, 30).terrain.size(),
                TerrainGenerator.expand(List.of(kept), List.of(), 42, 30).terrain.size());
    }

    @Test
    public void everyCounterPositionGetsAMarker() {
        Game game = scenarioGame(2, 4);
        assertEquals("eighteen positions, eighteen counters",
                18, game.getSearchMarkers().size());
    }

    /** Two boxes in the bag, four withdrawn: between none and two survive to the map. */
    @Test
    public void theWithdrawalCanTakeTheBoxes() {
        int sawTwo = 0, sawFewer = 0;
        for (int run = 0; run < 400; run++) {
            long boxes = scenarioGame(2, 4).getSearchMarkers().stream()
                    .filter(m -> m.getKind() == SearchMarker.PayloadKind.OBJECTIVE)
                    .count();
            assertTrue("never more than the bag held: " + boxes, boxes <= 2);
            if (boxes == 2) sawTwo++; else sawFewer++;
        }
        assertTrue("two boxes should usually survive (~66%)", sawTwo > 0);
        assertTrue("and sometimes they should not — that is the point of withdrawing four",
                sawFewer > 0);
    }

    /** Withdraw nothing and the bag arrives intact, which pins the withdrawal as the cause. */
    @Test
    public void withNoWithdrawalEveryBoxReachesTheMap() {
        for (int run = 0; run < 20; run++)
            assertEquals(2, scenarioGame(2, 0).getSearchMarkers().stream()
                    .filter(m -> m.getKind() == SearchMarker.PayloadKind.OBJECTIVE).count());
    }

    /** Each box is named apart, because every action addresses an objective BY NAME. */
    @Test
    public void twoBoxesAreNamedApart() {
        Game game = scenarioGame(2, 0);
        List<String> names = game.getSearchMarkers().stream()
                .filter(m -> m.getKind() == SearchMarker.PayloadKind.OBJECTIVE)
                .map(m -> m.getObjective().getName()).sorted().toList();
        assertEquals(List.of("Stasis Box 1", "Stasis Box 2"), names);
    }

    /** Positions with no bag are ordinary counters — a legitimate scenario, not an error. */
    @Test
    public void countersWithNoBagHideNothing() {
        Game game = scenarioGame(0, 0);
        assertEquals(18, game.getSearchMarkers().size());
        assertTrue(game.getSearchMarkers().stream()
                .allMatch(m -> m.getKind() == SearchMarker.PayloadKind.NOTHING));
    }

    // ------------------------------------------------------------------ privacy

    /**
     * The DTO carries the hex and nothing else, and every counter looks the same. A marker that
     * appeared only where a box was hidden would announce exactly what it is for.
     */
    @Test
    public void everyCounterLooksTheSameToAPlayer() {
        Game game = runningGame();
        game.addSearchMarker(SearchMarker.empty(new Location(14, 14)));
        game.addSearchMarker(SearchMarker.hiding(new Location(15, 15), box("Stasis Box")));

        List<GameStateDto.SearchMarkerDto> sent = markersIn(game, "Federation");
        assertEquals("both counters are sent", 2, sent.size());
        for (GameStateDto.SearchMarkerDto dto : sent)
            assertNotNull("position is public", dto.location);
    }

    /**
     * And the payload reaches NO list the DTO walks. This is the structural claim the whole
     * design rests on, so it is asserted against the serialised snapshot rather than against a
     * field: an objective still in a hold-out list would show up here however carefully each
     * individual builder filtered it.
     */
    @Test
    public void aHiddenBoxIsInNoChannelAtAll() {
        Game game = runningGame();
        game.addSearchMarker(SearchMarker.hiding(new Location(15, 15), box("Stasis Box")));

        for (String viewer : new String[] { "Federation", "Klingons", null }) {
            GameStateDto dto = new GameStateDto(game, viewer);
            for (GameStateDto.MapObjectDto o : dto.mapObjects)
                assertFalse("no ObjectiveDto before anybody has searched (viewer=" + viewer + ")",
                        o instanceof GameStateDto.ObjectiveDto);
            assertTrue("and nothing in the standings either (viewer=" + viewer + ")",
                    dto.scoreboard.objectives.isEmpty());
        }
    }

    // ------------------------------------------------------------------ searching

    @Test
    public void aShipInTheHexExaminesTheCounter() {
        Game game = runningGame();                      // Federation CA at 10|10
        game.addSearchMarker(SearchMarker.hiding(new Location(10, 10), box("Stasis Box")));

        advancePastMovement(game);

        assertTrue("the counter is spent", game.getSearchMarkers().isEmpty());
        assertEquals("and the box is on the map", 1, game.getObjectives().size());
        assertEquals(new Location(10, 10), game.getObjectives().get(0).getLocation());
    }

    /** An empty counter is simply gone — the remaining "?" markers are the unsearched set. */
    @Test
    public void anEmptyCounterLeavesNothingBehind() {
        Game game = runningGame();
        game.addSearchMarker(SearchMarker.empty(new Location(10, 10)));
        game.addSearchMarker(SearchMarker.empty(new Location(30, 5)));

        advancePastMovement(game);

        assertEquals("the searched one goes, the unsearched one stays",
                1, game.getSearchMarkers().size());
        assertEquals(new Location(30, 5), game.getSearchMarkers().get(0).getLocation());
        assertTrue("and it leaves no objective", game.getObjectives().isEmpty());
    }

    /** Both players are told, because the counter is turned over on the table (SH47.473). */
    @Test
    public void theFindIsLoggedAndVisibleToBoth() {
        Game game = runningGame();
        game.addSearchMarker(SearchMarker.hiding(new Location(10, 10), box("Stasis Box")));

        advancePastMovement(game);

        assertTrue("the log should name the finder: " + game.getLastSeekerLog(),
                game.getLastSeekerLog().stream().anyMatch(
                        l -> l.contains("USS Enterprise") && l.contains("Stasis Box")));
        assertEquals(1, objectivesIn(game, "Federation").size());
        assertEquals("the Klingon player sees it too", 1, objectivesIn(game, "Klingons").size());
    }

    @Test
    public void anEmptyHexIsNotSearched() {
        Game game = runningGame();
        game.addSearchMarker(SearchMarker.hiding(new Location(30, 5), box("Stasis Box")));

        advancePastMovement(game);

        assertEquals("nobody is standing on it", 1, game.getSearchMarkers().size());
        assertTrue(game.getObjectives().isEmpty());
    }

    /** Found, and it is an ordinary objective from then on — SH47.474's study needs that. */
    @Test
    public void aFoundBoxBehavesLikeAnyOtherObjective() {
        Game game = runningGame();
        game.addSearchMarker(SearchMarker.hiding(new Location(10, 10), box("Stasis Box")));
        advancePastMovement(game);
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        Ship fed = shipNamed(game, "USS Enterprise");
        fed.setActiveFireControl(true);
        fed.getTransporters().bankEnergy(1.0);
        Game.ActionResult result = game.pickUpObjective(fed, "Stasis Box",
                com.sfb.properties.RetrievalMethod.TRANSPORTER);

        assertTrue(result.getMessage(), result.isSuccess());
        assertSame(fed, game.getObjectives().get(0).getCarrier());
    }

    // ------------------------------------------------------------------ across a save

    /**
     * The hand must not be re-dealt. A reload runs {@code setupFromScenario} first, which draws
     * a fresh assignment, and {@code applyState} then replaces it with the saved one — so the
     * box stays in the hex the players have been circling.
     */
    @Test
    public void theHandSurvivesAReloadRatherThanBeingRedealt() {
        ScenarioSpec spec = searchSpec(2, 0);
        Game before = new Game();
        before.setupFromScenario(spec);

        List<String> dealt = before.getSearchMarkers().stream()
                .filter(m -> m.getKind() == SearchMarker.PayloadKind.OBJECTIVE)
                .map(m -> m.getLocation().toString()).sorted().toList();

        Game after = reload(before, spec);
        List<String> restored = after.getSearchMarkers().stream()
                .filter(m -> m.getKind() == SearchMarker.PayloadKind.OBJECTIVE)
                .map(m -> m.getLocation().toString()).sorted().toList();

        assertEquals("a redeal would move the box", dealt, restored);
        assertEquals(18, after.getSearchMarkers().size());
    }

    /** A counter's identity survives too, or the box comes back as an unnamed nothing. */
    @Test
    public void aSavedCounterKeepsWhatItHides() {
        Game before = new Game();
        before.addSearchMarker(SearchMarker.hiding(new Location(9, 9), box("Stasis Box")));

        ScenarioSpec bare = new ScenarioSpec();
        bare.sides = new ArrayList<>();
        Game after = reload(before, bare);
        assertEquals(1, after.getSearchMarkers().size());
        SearchMarker m = after.getSearchMarkers().get(0);
        assertEquals(SearchMarker.PayloadKind.OBJECTIVE, m.getKind());
        assertEquals("Stasis Box", m.getObjective().getName());
        assertEquals(Double.valueOf(2.0), m.getObjective().getCargoSpaces());
        assertTrue(m.getObjective().allows(com.sfb.properties.RetrievalMethod.TRANSPORTER));
    }

    /** Counters already searched stay searched — the list is authoritative when present. */
    @Test
    public void countersAlreadySearchedDoNotComeBack() {
        Game before = new Game();
        before.addSearchMarker(SearchMarker.empty(new Location(9, 9)));

        Map<String, Object> saved = before.exportState();
        Game after = new Game();
        after.setupFromScenario(searchSpec(2, 0));     // scenario lays eighteen
        after.applyState(saved);                        // the save says one

        assertEquals("the save is the authority on what is left", 1,
                after.getSearchMarkers().size());
    }

    /**
     * A save written before counters existed has no key, and reading that as "every counter was
     * searched" would quietly end the hunt. Absent means leave alone; an empty list means they
     * are all gone. The two look alike and mean opposite things.
     */
    @Test
    public void aPreCounterSaveLeavesTheScenariosOwnDealAlone() {
        ScenarioSpec spec = searchSpec(2, 0);
        Game before = new Game();
        before.setupFromScenario(spec);

        Map<String, Object> saved = before.exportState();
        saved.remove("searchMarkers");

        Game after = new Game();
        after.setupFromScenario(spec);
        after.applyState(saved);

        assertEquals("an older save must not read as 'they were all searched'",
                18, after.getSearchMarkers().size());
    }

    @Test
    public void anEmptyListMeansEveryCounterHasBeenSearched() {
        ScenarioSpec spec = searchSpec(2, 0);
        Game before = new Game();
        before.setupFromScenario(spec);

        Map<String, Object> saved = before.exportState();
        saved.put("searchMarkers", new ArrayList<>());

        Game after = new Game();
        after.setupFromScenario(spec);
        after.applyState(saved);

        assertTrue(after.getSearchMarkers().isEmpty());
    }

    /** A payload kind this build does not have is REPORTED: it takes a box with it. */
    @Test
    public void anUnknownCounterKindIsReported() {
        Map<String, Object> saved = new Game().exportState();
        List<Map<String, Object>> markers = new ArrayList<>();
        markers.add(new java.util.LinkedHashMap<>(Map.of("x", 9, "y", 9, "kind", "WORMHOLE")));
        saved.put("searchMarkers", markers);

        List<String> problems = new Game().applyState(saved);
        assertTrue("the player must be told: " + problems,
                problems.stream().anyMatch(p -> p.contains("WORMHOLE")));
    }

    // ------------------------------------------------------------------ helpers

    private static TerrainGenerator.Plan field(boolean markCounters) {
        TerrainGenerator.Plan plan = new TerrainGenerator.Plan();
        plan.type = "ASTEROID_FIELD";
        plan.markCounters = markCounters;
        return plan;
    }

    private static Objective box(String name) {
        Objective o = new Objective(name, 0, 0);
        o.getAllowedRetrieval().add(com.sfb.properties.RetrievalMethod.TRANSPORTER);
        o.setCargoSpaces(2.0);
        o.setPoints(10);
        return o;
    }

    /** A spec with the standard field, its counters kept, and a bag over them. */
    private static ScenarioSpec searchSpec(int boxes, int withdraw) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Counter fixture";
        spec.year = 152;
        spec.mapCols = 42;
        spec.mapRows = 30;
        spec.sides = new ArrayList<>();
        spec.terrainPlan = new ArrayList<>(List.of(field(true)));
        if (boxes > 0) {
            ScenarioSpec.SearchBag bag = new ScenarioSpec.SearchBag();
            bag.objectives = boxes;
            bag.withdrawUnseen = withdraw;
            ScenarioSpec.ObjectiveSetup template = new ScenarioSpec.ObjectiveSetup();
            template.name = "Stasis Box";
            template.retrieval = new ArrayList<>(List.of("TRANSPORTER"));
            template.cargoSpaces = 2.0;
            spec.searchBag = bag;
        }
        com.sfb.scenario.ScenarioLoader.expandTerrainPlans(spec);
        if (spec.searchBag != null) {
            ScenarioSpec.ObjectiveSetup template = new ScenarioSpec.ObjectiveSetup();
            template.name = "Stasis Box";
            template.retrieval = new ArrayList<>(List.of("TRANSPORTER"));
            template.cargoSpaces = 2.0;
            spec.searchBag.objectiveTemplate = template;
        }
        return spec;
    }

    private static Game scenarioGame(int boxes, int withdraw) {
        Game game = new Game();
        game.setupFromScenario(searchSpec(boxes, withdraw));
        return game;
    }

    private static List<GameStateDto.SearchMarkerDto> markersIn(Game game, String viewer) {
        List<GameStateDto.SearchMarkerDto> out = new ArrayList<>();
        for (GameStateDto.MapObjectDto o : new GameStateDto(game, viewer).mapObjects)
            if (o instanceof GameStateDto.SearchMarkerDto dto)
                out.add(dto);
        return out;
    }

    private static List<GameStateDto.ObjectiveDto> objectivesIn(Game game, String viewer) {
        List<GameStateDto.ObjectiveDto> out = new ArrayList<>();
        for (GameStateDto.MapObjectDto o : new GameStateDto(game, viewer).mapObjects)
            if (o instanceof GameStateDto.ObjectiveDto dto)
                out.add(dto);
        return out;
    }

    private static Game runningGame() {
        Game game = new Game();

        Player fed = new Player();
        fed.setName("Captain Dan");
        fed.setTeamName("Federation");
        Player kli = new Player();
        kli.setName("Captain Dave");
        kli.setTeamName("Klingons");
        game.getPlayers().add(fed);
        game.getPlayers().add(kli);

        game.getShips().add(ship(FederationShips.getFedCa(), "USS Enterprise", 10, 10, fed));
        game.getShips().add(ship(KlingonShips.getD7(), "IKV Saber", 20, 20, kli));

        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            game.submitAllocation(s, e);
        }
        return game;
    }

    private static Ship ship(Map<String, Object> spec, String name, int x, int y, Player owner) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        s.setSpeedPreviousTurn(0);
        s.setSpeedTwoTurnsAgo(0);
        s.setOwner(owner);
        owner.getPlayerUnits().add(s);
        return s;
    }

    private static void advancePastMovement(Game game) {
        while (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
    }

    private static Game reload(Game before, ScenarioSpec spec) {
        Map<String, Object> saved = before.exportState();
        Game after = new Game();
        after.setupFromScenario(spec);
        after.applyState(saved);
        return after;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("no ship called " + name);
    }
}
