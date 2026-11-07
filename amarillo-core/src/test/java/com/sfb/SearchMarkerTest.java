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

    // ------------------------------------------------------------------ mines (SH47.48)

    /**
     * A revealed NSM counter puts a live mine in its hex.
     *
     * <p>It needed no new creation path: a dropped mine arms by M2.34 once its layer is more
     * than a hex away, and a mine with NO layer reads as "the layer is not nearby" — which is
     * the right answer for something somebody left here a month ago, and happens to be exactly
     * SH47.48's "the mines only roll for triggering AFTER they have been revealed".
     */
    @Test
    public void aRevealedMineCounterPutsALiveMineInTheHex() {
        Game game = runningGame();
        game.addSearchMarker(SearchMarker.mined(new Location(10, 10),
                com.sfb.objects.SpaceMine.createPrePlacedNSM(new Location(10, 10),
                        List.of(1, 2, 3, 4, 5, 6))));

        advancePastMovement(game);

        assertTrue("the counter is spent", game.getSearchMarkers().isEmpty());
        assertEquals("and a mine is on the map", 1, game.getMines().size());
        assertEquals(new Location(10, 10), game.getMines().get(0).getLocation());
        assertEquals(com.sfb.objects.SpaceMine.MineType.NSM,
                game.getMines().get(0).getMineType());
        assertTrue("the log should say it is live: " + game.getLastSeekerLog(),
                game.getLastSeekerLog().stream().anyMatch(l -> l.contains("nuclear space mine")));
    }

    /**
     * And the move that UNCOVERS it does not set it off.
     *
     * <p>SH47.48 spells out why this matters: "a unit might uncover a mine after moving into the
     * hex and then use a emergency deceleration to cancel further movement after that point and
     * thereby avoid triggering the mine." That escape exists only if the uncovering move is
     * free, so a mine that detonated on reveal would quietly delete a documented tactic.
     */
    @Test
    public void theMoveThatUncoversAMineDoesNotTriggerIt() {
        Game game = runningGame();
        game.addSearchMarker(SearchMarker.mined(new Location(10, 10),
                com.sfb.objects.SpaceMine.createPrePlacedNSM(new Location(10, 10), null)));

        advancePastMovement(game);

        assertEquals("still there, unexploded", 1, game.getMines().size());
        assertFalse("nothing detonated on the uncovering move: " + game.getLastSeekerLog(),
                game.getLastSeekerLog().stream().anyMatch(l -> l.contains("DETONATED")));
        Ship fed = shipNamed(game, "USS Enterprise");
        for (int shield = 1; shield <= 6; shield++)
            assertEquals("shield #" + shield + " untouched",
                    fed.getShields().getMaxShieldStrength(shield),
                    fed.getShields().getShieldStrength(shield));
    }

    /**
     * (M2.14) The size-class setting, whose stated purpose is to stop an enemy sweeping a
     * minefield with drones. Note which way the numbers run — in SFB a LARGER size class is a
     * SMALLER unit, so SH47.48's "size class 6 or larger" is the set 1..6.
     */
    @Test
    public void aMineIgnoresSizeClassesItWasNotSetFor() {
        com.sfb.objects.SpaceMine mine = com.sfb.objects.SpaceMine.createPrePlacedNSM(
                new Location(5, 5), List.of(1, 2, 3, 4, 5, 6));

        assertTrue("a cruiser is size class 2", mine.accepts(2));
        assertTrue("a fighter is size class 6 and still accepted", mine.accepts(6));
        assertFalse("a drone is 7 and ignored — that is the point of the setting",
                mine.accepts(7));
        assertFalse(mine.accepts(8));
    }

    /** (M2.141) No settings means ALL classes, so every mine laid in play is unaffected. */
    @Test
    public void aMineWithNoSettingsAcceptsEverything() {
        com.sfb.objects.SpaceMine mine =
                com.sfb.objects.SpaceMine.createPrePlacedNSM(new Location(5, 5), null);
        for (int sc = 1; sc <= 8; sc++)
            assertTrue("size class " + sc, mine.accepts(sc));
    }

    /** (M2.144) "The sizes set need not be continuous" — 1-3 and 5-7 is a legal setting. */
    @Test
    public void sizeSettingsNeedNotBeContinuous() {
        com.sfb.objects.SpaceMine mine = com.sfb.objects.SpaceMine.createPrePlacedNSM(
                new Location(5, 5), List.of(1, 2, 3, 5, 6, 7));
        assertTrue(mine.accepts(3));
        assertFalse("the gap is real", mine.accepts(4));
        assertTrue(mine.accepts(5));
    }

    /** A dealt mine is in no list until it is found, exactly like a box. */
    @Test
    public void aHiddenMineIsNotOnTheMapEither() {
        Game game = runningGame();
        game.addSearchMarker(SearchMarker.mined(new Location(15, 15),
                com.sfb.objects.SpaceMine.createPrePlacedNSM(new Location(15, 15), null)));

        assertTrue("no mine exists to be seen, swept or triggered", game.getMines().isEmpty());
        for (GameStateDto.MapObjectDto o : new GameStateDto(game, "Klingons").mapObjects)
            assertFalse("and no MineDto reaches a client",
                    o instanceof GameStateDto.MineDto);
    }

    /** The bag deals mines alongside boxes, and never more than it held. */
    @Test
    public void theBagDealsMinesToo() {
        boolean sawMine = false;
        for (int run = 0; run < 200 && !sawMine; run++) {
            Game game = new Game();
            game.setupFromScenario(minedSpec());
            long mines = game.getSearchMarkers().stream()
                    .filter(m -> m.getKind() == SearchMarker.PayloadKind.MINE).count();
            assertTrue("never more than the bag held: " + mines, mines <= 2);
            if (mines > 0) sawMine = true;
        }
        assertTrue("two mines in a bag of eighteen should turn up", sawMine);
    }

    /** A dealt mine keeps its M2.14 setting across a save, or it starts sweeping on drones. */
    @Test
    public void aSavedMineCounterKeepsItsSizeSetting() {
        Game before = new Game();
        before.addSearchMarker(SearchMarker.mined(new Location(9, 9),
                com.sfb.objects.SpaceMine.createPrePlacedNSM(new Location(9, 9),
                        List.of(1, 2, 3, 4, 5, 6))));

        ScenarioSpec bare = new ScenarioSpec();
        bare.sides = new ArrayList<>();
        Game after = reload(before, bare);

        assertEquals(1, after.getSearchMarkers().size());
        SearchMarker m = after.getSearchMarkers().get(0);
        assertEquals(SearchMarker.PayloadKind.MINE, m.getKind());
        assertTrue(m.getMine().accepts(2));
        assertFalse("the setting must survive, or drones can sweep it", m.getMine().accepts(7));
    }

    /**
     * (M2.14) The setting has to bite in the RESOLVER, not just in the predicate.
     *
     * <h2>Why this test exists at all</h2>
     * The first four size-class tests above call {@code accepts()} directly, and deleting the
     * filter from {@code MineResolver} left every one of them green — the predicate was tested
     * and the behaviour was not. This drives a real drone past a real mine.
     *
     * <h2>How it is made deterministic</h2>
     * No dice are seeded. {@code detectsUnit} triggers UNCONDITIONALLY at effective speed six
     * or more, and a drone flies far faster than that, so an accepted drone always sets the
     * mine off and an ignored one never does. The Klingon sits at speed zero in the mine's own
     * hex: a unit that did not move does not trigger, so the only thing moving in range is the
     * drone.
     */
    @Test
    public void aDroneCannotSweepAMineSetAgainstShips() {
        Game game = minefieldWithDrone(List.of(1, 2, 3, 4, 5, 6));

        assertEquals("the drone must not set it off — M2.141's whole purpose",
                1, game.getMines().size());
    }

    /** And with no setting the same drone DOES sweep it, which is what pins the cause. */
    @Test
    public void aDroneSweepsAMineWithNoSizeSetting() {
        Game game = minefieldWithDrone(null);

        assertTrue("an unset mine accepts all classes (M2.141), so the drone triggers it",
                game.getMines().isEmpty());
    }

    /**
     * A mine, armed, in the hex a drone is launched from, with nothing else moving near it.
     *
     * @param sizeClasses the mine's M2.14 setting, or null for none
     */
    private static Game minefieldWithDrone(List<Integer> sizeClasses) {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        Game game = new Game();

        Player fedPlayer = new Player();
        fedPlayer.setName("Captain Dan");
        fedPlayer.setTeamName("Federation");
        Player kliPlayer = new Player();
        kliPlayer.setName("Captain Dave");
        kliPlayer.setTeamName("Klingons");
        game.getPlayers().add(fedPlayer);
        game.getPlayers().add(kliPlayer);

        // Far apart, so the Federation ship is nowhere near the mine.
        Ship fed = ship(FederationShips.getFedCa(), "USS Enterprise", 40, 5, fedPlayer);
        Ship blade = ship(KlingonShips.getD7(), "IKV Blade", 20, 20, kliPlayer);
        game.getShips().add(fed);
        game.getShips().add(blade);

        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            game.submitAllocation(s, e);     // no warp power: both ships stay at speed 0
        }

        // The mine sits in the Klingon's hex. It arms on the first sweep (null layer reads as
        // "the layer is not nearby", M2.34) and the Klingon never moves, so it cannot be the
        // thing that triggers it.
        game.getMines().add(com.sfb.objects.SpaceMine.createPrePlacedNSM(
                new Location(20, 20), sizeClasses));

        advancePastMovement(game);           // impulse 1: the mine arms
        assertTrue("fixture: the mine must be armed before the drone moves",
                game.getMines().get(0).isActive());

        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
        com.sfb.weapons.DroneRack rack = blade.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.DroneRack)
                .map(w -> (com.sfb.weapons.DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("fixture: the D7 has racks"));
        blade.setActiveFireControl(true);
        blade.addLockOn(fed);
        Game.ActionResult launched = game.launchDrone(blade, fed, rack);
        assertTrue("fixture: the drone must get away — " + launched.getMessage(),
                launched.isSuccess());

        // Impulse 2 onwards: the drone moves out of the mine's hex and through its detection
        // radius. A few impulses, because the drone's first move is what matters and one is
        // enough, but the margin costs nothing.
        for (int i = 0; i < 12; i++)
            game.advancePhase();

        return game;
    }

    // ------------------------------------------------------------------ craft (SH47.472)

    /**
     * (SH47.472) "Shuttles or fighters may examine an inverted counter by ending their movement
     * in a hex containing a counter and rolling a die. If the result is '1' or '2,' the counter
     * is examined; otherwise, it is not."
     *
     * <p>So a craft search BOTH succeeds and fails, and a test that only proved it can succeed
     * would pass just as well against a craft that always succeeds — which would make a shuttle
     * strictly better at this than the cruiser it launched from. Both outcomes are required.
     */
    @Test
    public void aCraftOnACounterSometimesFindsItAndSometimesDoesNot() {
        boolean sawFound = false;
        boolean sawMissed = false;

        for (int run = 0; run < 400 && !(sawFound && sawMissed); run++) {
            Game game = gameWithCraftOnACounter();
            runToImpulse32Movement(game);
            if (counterGone(game))
                sawFound = true;
            else
                sawMissed = true;
        }

        assertTrue("a craft must be able to find the counter (roll 1-2)", sawFound);
        assertTrue("and must be able to fail — otherwise it beats a starship at searching",
                sawMissed);
    }

    /**
     * And it is one chance in three, not one in two or one in one. Checked loosely, because the
     * dice are not seeded and a tight bound would make this test flake; loose is still enough
     * to catch a comparison written the wrong way round, which would give 4-in-6 or 6-in-6.
     */
    @Test
    public void theCraftRollIsOneInThree() {
        int found = 0;
        int runs = 600;
        for (int run = 0; run < runs; run++) {
            Game game = gameWithCraftOnACounter();
            runToImpulse32Movement(game);
            if (counterGone(game))
                found++;
        }
        double rate = found / (double) runs;
        assertTrue("expected about a third, got " + rate, rate > 0.22 && rate < 0.45);
    }

    /**
     * Not before impulse 32. The rule hangs the roll on the craft's movement for the TURN being
     * over, which is why it says "they can only make their rolls on impulse #32 of any given
     * turn" — so a shuttle parked on a counter all turn gets ONE attempt, not thirty-two.
     */
    @Test
    public void aCraftCannotSearchBeforeTheEndOfTheTurn() {
        Game game = gameWithCraftOnACounter();
        int before = game.getSearchMarkers().size();

        // Every impulse up to but not including the last.
        while (game.getCurrentImpulse() < 32)
            game.advancePhase();

        assertEquals("thirty-one impulses of sitting on it must achieve nothing",
                before, game.getSearchMarkers().size());
    }

    /** A ship is unaffected: it searches by entering, on any impulse (SH47.471). */
    @Test
    public void aShipStillSearchesOnAnyImpulse() {
        Game game = gameWithCraftOnACounter();
        // Put a ship on a different counter and advance one impulse only.
        com.sfb.objects.SearchMarker other = game.getSearchMarkers().stream()
                .filter(m -> !m.getLocation().equals(new Location(10, 10)))
                .findFirst().orElseThrow();
        shipNamed(game, "USS Enterprise").setLocation(other.getLocation());

        advancePastMovement(game);

        assertTrue("the ship's counter is gone on impulse 1",
                game.getSearchMarkers().stream().noneMatch(
                        m -> m.getLocation().equals(other.getLocation())));
    }

    /**
     * A shuttle can turn up a MINE as readily as a box, which is a nasty and entirely correct
     * outcome: a shuttle is size class 6, and SH47's NSMs are set to accept 1-6.
     */
    @Test
    public void aCraftCanUncoverAMineToo() {
        for (int run = 0; run < 400; run++) {
            Game game = gameWithCraftOnACounter();
            // Replace the counter under the shuttle with a mine.
            game.getSearchMarkers().removeIf(m -> m.getLocation().equals(new Location(10, 10)));
            game.addSearchMarker(SearchMarker.mined(new Location(10, 10),
                    com.sfb.objects.SpaceMine.createPrePlacedNSM(new Location(10, 10),
                            List.of(1, 2, 3, 4, 5, 6))));

            runToImpulse32Movement(game);
            if (!game.getMines().isEmpty()) {
                assertEquals(new Location(10, 10), game.getMines().get(0).getLocation());
                return;
            }
        }
        throw new AssertionError("no craft ever uncovered the mine in 400 turns");
    }

    /** The log names the craft and the roll — the part a player cannot reconstruct after. */
    @Test
    public void theCraftSearchIsLogged() {
        for (int run = 0; run < 100; run++) {
            Game game = gameWithCraftOnACounter();
            runToImpulse32Movement(game);
            if (game.getLastSeekerLog().stream().anyMatch(
                    l -> l.contains("Scout 1") && l.contains("SH47.472")))
                return;
        }
        throw new AssertionError("the craft search should appear in the impulse log");
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

    /**
     * The standard field with its counters, a ship far away, and an admin shuttle parked on the
     * counter at 10|10. The shuttle is placed rather than launched: a launch would spend the
     * bay, the launch delay and an impulse of movement, none of which this rule cares about.
     */
    private static Game gameWithCraftOnACounter() {
        Game game = new Game();
        game.setupFromScenario(searchSpec(2, 4));

        Player fed = new Player();
        fed.setName("Captain Dan");
        fed.setTeamName("Federation");
        game.getPlayers().add(fed);
        game.getShips().add(ship(FederationShips.getFedCa(), "USS Enterprise", 40, 5, fed));

        // A counter where the shuttle can sit. The standard layout is rolled, so the fixture
        // puts one at a hex it chooses rather than hoping the dice obliged.
        game.getSearchMarkers().removeIf(m -> m.getLocation().equals(new Location(10, 10)));
        game.addSearchMarker(SearchMarker.hiding(new Location(10, 10), box("Stasis Box")));

        com.sfb.objects.shuttles.AdminShuttle scout =
                new com.sfb.objects.shuttles.AdminShuttle();
        scout.setName("Scout 1");
        scout.setLocation(new Location(10, 10));
        scout.setFacing(1);
        game.getActiveShuttles().add(scout);

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

    /**
     * Advance until impulse 32's MOVEMENT phase has been left, which is where the craft roll
     * happens.
     *
     * <p>Advances FIRST and tests after, which is not a style choice: {@code getLocalImpulse}
     * maps impulse 0 to 32 ("if the div is 0, we're actually on the 32nd impulse"), so a game
     * that has not started yet reports impulse 32 and a test-before-advance loop exits without
     * ever running a phase.
     */
    /**
     * Whether the counter UNDER THE SHUTTLE has been searched.
     *
     * <p>Not {@code getSearchMarkers().isEmpty()}, which is what the first draft asked and
     * which can never be true: the fixture lays the full field of eighteen, so seventeen
     * counters remain however well the shuttle does. An assertion that cannot pass is worse
     * than one that cannot fail — it reported a working feature broken.
     */
    private static boolean counterGone(Game game) {
        return game.getSearchMarkers().stream()
                .noneMatch(m -> m.getLocation().equals(new Location(10, 10)));
    }

    private static void runToImpulse32Movement(Game game) {
        for (int guard = 0; guard < 200; guard++) {
            game.advancePhase();
            if (game.getCurrentImpulse() == 32
                    && game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
                return;
        }
        throw new AssertionError("never reached the end of impulse 32");
    }

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

    /** The same, with two NSM counters in the bag as SH47.46 deals them. */
    private static ScenarioSpec minedSpec() {
        ScenarioSpec spec = searchSpec(2, 4);
        spec.searchBag.mines = 2;
        spec.searchBag.mineSizeClasses = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
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
