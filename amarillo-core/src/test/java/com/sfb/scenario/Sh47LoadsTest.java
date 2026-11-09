package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;

/**
 * SH47 "The Stasis Box" loads and sets up — the second published scenario in {@code
 * data/scenarios}, and the first one meant to be PLAYED rather than measured.
 *
 * <p>The file was a deliberate SUBSET, and is now most of the scenario. The box hunt landed on
 * 2026-11-07: eighteen face-down counters, two of them Stasis Boxes, four withdrawn from the
 * stack unseen so that neither player knows whether two boxes exist, one, or none. The tests
 * below pin the counters AND the fact that no box is on the map until somebody searches for one
 * — a file that quietly shipped the boxes as ordinary visible objectives would load, play, and
 * not be SH47.
 *
 * <p>What is still missing is the two NSM counters, because nothing can pre-place a mine and
 * SH47.48 carries clauses of its own. The bag therefore deals 2 boxes among 18 counters rather
 * than 2 boxes and 2 mines, which makes the field a shade safer than the printed scenario.
 *
 * <p>What IS the printed scenario: a Fed DD and a Kzinti CL at opposite corners of a standard
 * P3.1 asteroid field, speed 4 at WS-0, 20% Commander's Options, slow drones, no refits, and
 * per-side disengagement limited to one map edge each.
 */
public class Sh47LoadsTest {

    private static ScenarioSpec spec;

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        spec = new ObjectMapper().readValue(new File("../data/scenarios/sh47.json"), ScenarioSpec.class);
    }

    /** Re-read per test: loading EXPANDS the terrain plan into the spec, which is destructive. */
    private static ScenarioSpec fresh() throws Exception {
        return new ObjectMapper().readValue(new File("../data/scenarios/sh47.json"), ScenarioSpec.class);
    }

    @Test
    public void theFileParsesIntoASpec() {
        assertEquals("SH47", spec.id);
        assertEquals("The Stasis Box", spec.name);
        assertEquals(152, spec.year);
        assertEquals(2, spec.numPlayers);
        assertEquals(2, spec.sides.size());
    }

    /**
     * (SH47.2/SH47.64) The map is 42x30, and the scenario is what says so: every set-up hex it
     * names is a corner of the printed map — 4230 and 0101 for the two players, 0130 and 4201
     * for the third and fourth of the variation. {@link ScenarioSpec} defaults to 32 ROWS, so
     * without this the Xerxes would stand two rows short of her corner and the 42xx edge she
     * is allowed to disengage from would be two rows longer than the map she is playing on.
     */
    @Test
    public void theMapIsTheThirtyRowPrintedMap() {
        assertEquals(42, spec.mapCols);
        assertEquals(30, spec.mapRows);
        assertEquals("SH47.41: the map is fixed; it does not float", "STANDARD", spec.mapType);
    }

    /** (SH47.2) Both hulls are real library ships, at their printed stations and postures. */
    @Test
    public void bothShipsBuildAtTheirPrintedStations() throws Exception {
        List<List<Ship>> bySide = ScenarioLoader.loadShips(fresh());

        assertEquals(2, bySide.size());
        assertEquals(1, bySide.get(0).size());
        assertEquals(1, bySide.get(1).size());

        Ship xerxes = bySide.get(0).get(0);
        Ship mystic = bySide.get(1).get(0);

        // The PRINTED NAME has to be in there; the registry prefix is the file's business.
        // SH47.2 writes "DD Xerxes" and "CL Mystic", as ADB writes every ship name, but every
        // other scenario in data/scenarios uses prefixes — USS, IKV, HMS, KHS, IKS — and "USS
        // Xerxes" is the same destroyer. This asserted the bare string and failed the build the
        // moment the file was brought into line with its neighbours, which is a test holding a
        // cosmetic field still rather than protecting a rule. The STATIONS below are what this
        // test is named for and what SH47.2 actually fixes.
        assertTrue("SH47.2 names her Xerxes, however she is prefixed: " + xerxes.getName(),
                xerxes.getName().contains("Xerxes"));
        assertTrue("SH47.2 names her Mystic, however she is prefixed: " + mystic.getName(),
                mystic.getName().contains("Mystic"));
        assertEquals("SH47.2: DD Xerxes in 4230", "<42|30>", xerxes.getLocation().toString());
        assertEquals("SH47.2: CL Mystic in 0101", "<1|1>", mystic.getLocation().toString());
    }

    /**
     * (SH47.2) "speed 4, WS-0" for both — the patrol posture that makes this a gentle
     * introduction. WS-0 means nothing is armed when the two ships sight each other.
     */
    @Test
    public void bothShipsStartSlowAndUnarmed() {
        for (ScenarioSpec.SideSpec side : spec.sides) {
            ScenarioSpec.ShipSetup ship = side.ships.get(0);
            assertEquals(ship.shipName + " starts at speed 4", 4, ship.startSpeed);
            assertEquals(ship.shipName + " starts at WS-0", 0, ship.weaponStatus);
            assertTrue("SH47.44: no unit in this scenario has been refitted",
                    ship.refits == null || ship.refits.isEmpty());
        }
    }

    /**
     * (SH47.41) The one restriction here the engine actually enforces, and the interesting
     * thing about it is that it is the OPPOSITE model to SH80's.
     *
     * <p>SH80.41 restricts the DIRECTION of disengagement on a floating map, so it uses
     * direction fields and must not use edges. SH47.41 fixes the map and names map edges
     * outright — "the Federation can only disengage from 42xx map edge" — so it uses edge
     * fields and must not use directions. The same pairs of fields, opposite verdicts, and the
     * scenario text is what decides which.
     *
     * <h2>Stated as a permit-list since 2026-11-09, and destructionEdges is now EMPTY</h2>
     * This used to assert the other three edges were DESTRUCTION, which was the only
     * enforcement the engine had and was harsher than the printed text: SH47.41 never says an
     * illegal exit destroys the ship, where SH80.41 does say so when it means it. The permitted
     * edge is now stated outright and a wrong edge is REFUSED. If anyone is tempted to put the
     * destruction edges back, the scenario text is the reason not to.
     */
    @Test
    public void eachSideMayLeaveByOneMapEdgeOnly() {
        ScenarioSpec.SideSpec federation = spec.sides.get(0);
        ScenarioSpec.SideSpec kzinti     = spec.sides.get(1);

        assertEquals("SH47.41: the Federation can only disengage from the 42xx (right) edge",
                Set.of("RIGHT"), federation.exitEdges);
        assertEquals("SH47.41: the Kzinti can only disengage from the 01xx (left) edge",
                Set.of("LEFT"), kzinti.exitEdges);

        for (ScenarioSpec.SideSpec side : spec.sides) {
            assertTrue(side.name + " restricts by map EDGE on a fixed map, not by direction",
                    side.destructionDirections.isEmpty());
            assertTrue(side.name + ": a wrong edge is refused, not fatal — SH47.41 never says"
                    + " an illegal exit destroys the ship",
                    side.destructionEdges.isEmpty());
        }
    }

    /**
     * (SH47.41) The owner's ruling, 2026-11-09: "the Kzinti can only disengage from 01xx map
     * edge" restricts ALL disengagement, not merely which edge counts. So neither side may use
     * C7.1 acceleration or C7.2 separation at all — a ship out of options must cross its own
     * edge.
     *
     * <p>This was an open question in BACKLOG for two days and it took a playtest to force it:
     * on 2026-11-07 the Mystic carried a Stasis Box off the map BY ACCELERATION, which SH47.5
     * would reward and SH47.41 plainly forbids. Until the per-scenario disengagement fields
     * existed there was no way to say so in the file.
     */
    @Test
    public void neitherSideMayDisengageByAccelerationOrSeparation() {
        for (ScenarioSpec.SideSpec side : spec.sides)
            assertEquals(side.name + " may leave only by its map edge (SH47.41)",
                    Set.of(com.sfb.properties.DisengagementMethod.MAP_EDGE),
                    side.disengagement);
    }

    /**
     * And the ruling REACHES the engine, which is a different claim from the file saying it.
     * A scenario field that nothing consults is how this project has twice shipped a rule that
     * passed its own test while being unreachable in play.
     *
     * <h2>The seating step, which this test had to learn the hard way</h2>
     * Every per-team scenario rule — these two, {@code destructionEdges} and
     * {@code destructionDirections} alike — is keyed on {@code ship.getOwner().getTeamName()},
     * and {@code Game.setupFromScenario} attaches NO OWNER to the ships it builds. Core alone
     * has a side's name and a side's ships and never joins them; it is {@code GameSession} that
     * does it, setting a seated player's team from the side that lists the ship.
     *
     * <p>So this test seats owners the way a session does before asking. The first draft did
     * not, and failed — correctly. That is worth knowing rather than working around: a battle
     * run through core with no players gets NO per-team restriction of any kind, which is fine
     * for the two-browser game that exists today and a trap for anyone adding a single-player
     * or AI path later. Recorded in BACKLOG under the per-scenario disengagement entry.
     */
    @Test
    public void theEngineDeniesAccelerationAndSeparationToBothSides() throws Exception {
        ScenarioSpec loaded = fresh();
        com.sfb.Game game = new com.sfb.Game();
        game.setupFromScenario(loaded);
        seatPlayers(game, loaded);

        assertFalse("fixture: the scenario put ships on the map", game.getShips().isEmpty());
        for (com.sfb.objects.Ship ship : game.getShips()) {
            assertNotNull("fixture: the ship must be seated, or nothing is being tested",
                    ship.getOwner());
            assertFalse(ship.getName() + " must not be able to outrun this battle (SH47.41)",
                    game.sideMayDisengageBy(ship,
                            com.sfb.properties.DisengagementMethod.ACCELERATION));
            assertFalse(ship.getName() + " must not be able to separate away (SH47.41)",
                    game.sideMayDisengageBy(ship,
                            com.sfb.properties.DisengagementMethod.SEPARATION));
            assertTrue(ship.getName() + " may still cross its own map edge",
                    game.sideMayDisengageBy(ship,
                            com.sfb.properties.DisengagementMethod.MAP_EDGE));
            assertEquals(ship.getName() + " has exactly one edge to run for (SH47.41)",
                    1, game.allowedExitEdges(ship).size());
        }

        // And the two sides' edges are opposite, which is the shape of the scenario: each ship
        // starts in the corner it must return to, so the box is in the middle either way.
        assertEquals(Set.of("RIGHT"),
                game.allowedExitEdges(shipNamed(game, "USS Xerxes")));
        assertEquals(Set.of("LEFT"),
                game.allowedExitEdges(shipNamed(game, "KHS Mystic")));
    }

    private static com.sfb.objects.Ship shipNamed(com.sfb.Game game, String name) {
        for (com.sfb.objects.Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("no ship called " + name);
    }

    /**
     * Attach an owner to every ship whose team name is the side that lists it — what
     * {@code GameSession} does when a player claims a hull, and the step core does not do for
     * itself. Matched by ship name, which is unique across a scenario.
     */
    private static void seatPlayers(com.sfb.Game game, ScenarioSpec spec) {
        java.util.Map<String, String> teamOfShip = new java.util.HashMap<>();
        for (ScenarioSpec.SideSpec side : spec.sides)
            if (side.ships != null)
                for (ScenarioSpec.ShipSetup setup : side.ships)
                    if (setup.shipName != null)
                        teamOfShip.put(setup.shipName, side.name);

        java.util.Map<String, com.sfb.Player> bySide = new java.util.HashMap<>();
        for (com.sfb.objects.Ship ship : game.getShips()) {
            String team = teamOfShip.get(ship.getName());
            assertNotNull("fixture: " + ship.getName() + " is on no side", team);
            com.sfb.Player owner = bySide.computeIfAbsent(team, name -> {
                com.sfb.Player p = new com.sfb.Player();
                p.setName(name);
                p.setTeamName(name);
                return p;
            });
            ship.setOwner(owner);
            owner.getPlayerUnits().add(ship);
        }
    }

    /**
     * (SH47.46) The field is described, not enumerated — and the description is the rule. The
     * plan expands into P3.11's eighteen counters, rolled, each spreading asteroid hexes two
     * out (P3.12).
     *
     * <p>This is the clause the subset gets for free. SH47.46's shuffle changes what four
     * counters ARE and never where they sit; the rule says so outright, since removing a box
     * leaves its asteroid hexes behind. So the geometry the engine lays out already IS the
     * scenario's field, and only the identities are missing.
     */
    @Test
    public void theAsteroidFieldExpandsFromThePlan() throws Exception {
        ScenarioSpec s = fresh();
        assertEquals("one plan in the file", 1, s.terrainPlan.size());
        assertEquals("ASTEROID_FIELD", s.terrainPlan.get(0).type);
        assertNull("no seed: the field should differ from playing to playing", s.terrainPlan.get(0).seed);

        ScenarioLoader.expandTerrainPlans(s);

        assertNull("the plan is spent once expanded", s.terrainPlan);
        assertNotNull(s.terrain);
        assertTrue("eighteen counters spreading two hexes each is a large field, not a handful: "
                + s.terrain.size(), s.terrain.size() > 100);
        for (ScenarioSpec.TerrainSetup t : s.terrain)
            assertEquals("ASTEROID", t.type);
    }

    /**
     * Neither ship starts in the field. Not a coincidence and not luck: P3.11's counters run
     * from column 05 to 38 and row 05 to 30, so after the one-hex roll and the two-hex spread
     * the field cannot reach column 42 or row 01. Both corners the scenario chose are outside
     * it, which is why the ships can begin at speed 4 without taking collision damage on
     * impulse one.
     */
    @Test
    public void bothCornersAreClearOfTheField() throws Exception {
        ScenarioSpec s = fresh();
        ScenarioLoader.expandTerrainPlans(s);

        for (ScenarioSpec.TerrainSetup t : s.terrain) {
            assertNotEquals("an asteroid landed on the Xerxes", "4230", t.hex);
            assertNotEquals("an asteroid landed on the Mystic", "0101", t.hex);
        }
    }

    /** (SH47.432) "All drones are 'slow,' speed-8." */
    @Test
    public void dronesAreSlow() {
        assertEquals(Integer.valueOf(8), spec.commanderOptions.maxDroneSpeed);
    }

    /** (SH47.431) "up to 20% of its combat BPV", with T-bombs and extra marines named. */
    @Test
    public void commandersOptionsAreTwentyPercent() {
        assertEquals(20, spec.commanderOptions.budgetPercent);
        assertTrue(spec.commanderOptions.allowTBombs);
        assertTrue(spec.commanderOptions.allowCommandos);
    }

    /** (SH47.42/.421/.423) No warp booster packs, no MRS shuttle, no PFs. */
    @Test
    public void thereAreNoBoosterPacksNoMrsAndNoPfs() {
        assertFalse("SH47.42", spec.shuttleRules.warpBoosterPacks);
        assertFalse("SH47.42, which a megapack also depends on", spec.shuttleRules.megapacks);
        assertFalse("SH47.421: no ship here is qualified to carry one", spec.shuttleRules.mrsShuttles);
        assertFalse("SH47.423: no PFs in the basic version", spec.shuttleRules.pfs);
    }

    // -------------------------------------------------------------------------
    // What is deliberately NOT here
    // -------------------------------------------------------------------------

    /**
     * The point of the subset, asserted rather than left to a comment.
     *
     * <p>SH47's boxes are hidden among face-down counters, and four counters are discarded
     * unseen so neither player knows whether two boxes exist, one, or none. An {@code
     * Objective} has no revealed state, so the only way to put a box in this file today is to
     * put it in VISIBLE — which loads, plays, and throws away the entire scenario. The hunt is
     * the scenario; an acknowledged gap is better than a working wrong answer.
     *
     * <p>When hidden identity arrives, delete this test and assert the boxes instead.
     */
    @Test
    public void noObjectivesUntilCountersCanBeHidden() {
        assertTrue("the Stasis Boxes must NOT be present as visible objectives",
                spec.objectives == null || spec.objectives.isEmpty());

        assertTrue("and the rules text must say so where players will read it",
                spec.specialRules.stream().anyMatch(r -> r.contains("STASIS BOX (SH47.47)")
                        && r.contains("NOT IN THIS FILE")));
        assertTrue(spec.specialRules.stream().anyMatch(r -> r.contains("NSMs (SH47.48)")
                && r.contains("NOT IN THIS FILE")));
    }

    /** Every clause the players enforce for themselves reaches them. */
    @Test
    public void theProseRulesAreCarriedForThePlayersToRead() {
        assertEquals(7, spec.specialRules.size());
        assertTrue("SH47.45's period of peace, which nothing in code can express",
                spec.specialRules.stream().anyMatch(r -> r.startsWith("COMBAT (SH47.45)")));
    }

    /**
     * (SH47.5) Not STANDARD: the scenario is not decided by a BPV ratio. "The player who
     * successfully disengages with a Stasis Box wins", and failing that the player who scored
     * the most internal hits LOSES for provoking an incident — which nothing tallies, because
     * no side's internal hits scored are counted anywhere. Free text, and honestly so.
     */
    @Test
    public void victoryIsSpecialAndSpelledOut() {
        assertEquals("SPECIAL", spec.victoryConditions.type);
        assertNotNull(spec.victoryConditions.notes);
        assertTrue("the incident clause is the half that is not modelled",
                spec.victoryConditions.notes.contains("internal hits"));
    }

    // ---------------------------------------------------------------- the counters

    /**
     * SH47.46's counters reach the Game. The field has always been right — P3.11's eighteen
     * rolled centres with their radius-2 blobs — but the generator computed each centre into a
     * local and threw it away, so there was nowhere for a Stasis Box to be until 2026-11-07.
     */
    @Test
    public void theFieldLaysEighteenSearchableCounters() throws Exception {
        Game game = new Game();
        game.setupFromScenario(fresh());

        assertEquals("P3.11 lays eighteen counters", 18, game.getSearchMarkers().size());
    }

    /**
     * Two boxes in a bag of eighteen with four withdrawn unseen — so a game may hold two, one
     * or none, and nobody can know which until they go and look.
     *
     * <p>Run many times because the whole point is that it VARIES. Asserting one deal would
     * pass just as well against a generator that always dealt the same hand, which is the one
     * thing SH47.46 is written to prevent.
     */
    @Test
    public void theBoxCountIsNotKnowableInAdvance() throws Exception {
        java.util.Set<Long> counts = new java.util.HashSet<>();
        java.util.Set<String> places = new java.util.HashSet<>();
        for (int run = 0; run < 300; run++) {
            Game game = new Game();
            game.setupFromScenario(fresh());
            long boxes = game.getSearchMarkers().stream()
                    .filter(m -> m.getKind()
                            == com.sfb.objects.SearchMarker.PayloadKind.OBJECTIVE)
                    .count();
            assertTrue("never more than the bag held: " + boxes, boxes <= 2);
            counts.add(boxes);
            game.getSearchMarkers().stream()
                    .filter(m -> m.getKind()
                            == com.sfb.objects.SearchMarker.PayloadKind.OBJECTIVE)
                    .forEach(m -> places.add(m.getLocation().toString()));
        }
        assertTrue("the number of boxes must vary between games: " + counts, counts.size() > 1);
        assertTrue("and so must where they are: " + places.size(), places.size() > 2);
    }

    /** Nothing is on the map to find until somebody searches for it. */
    @Test
    public void noBoxIsOnTheMapAtSetup() throws Exception {
        Game game = new Game();
        game.setupFromScenario(fresh());

        assertTrue("a box exists only once a counter is turned over",
                game.getObjectives().isEmpty());
    }

    /**
     * And the SPEC carries positions but never the answer. The lobby broadcasts the spec to
     * both players so the deployment screen can show them the map, which makes everything in
     * it public by construction — so the draw happens when the Game is built, not here.
     */
    @Test
    public void theSpecPublishesWhereTheCountersAreAndNothingElse() throws Exception {
        ScenarioSpec spec = fresh();
        new Game().setupFromScenario(spec);   // expands the plan into the spec

        assertEquals(18, spec.searchMarkers.size());
        assertEquals("the bag's SHAPE is printed in the rulebook and is public",
                2, spec.searchBag.objectives);
        assertEquals(4, spec.searchBag.withdrawUnseen);
        assertEquals("Stasis Box", spec.searchBag.objectiveTemplate.name);

        // Nothing in the spec says which counter is which. If an assignment ever appears here,
        // it is on the wire to both players the moment the lobby lists the scenario.
        assertTrue("a hidden objective must not be pre-placed in the spec",
                spec.objectives == null || spec.objectives.isEmpty());
    }

    /** SH47.475 travels with the box: it survives the ship carrying it. */
    @Test
    public void aBoxSurvivesTheShipCarryingIt() throws Exception {
        ScenarioSpec spec = fresh();
        assertTrue("SH47.475, and the opposite of SH35.454",
                spec.searchBag.objectiveTemplate.survivesDestruction);
    }
}
