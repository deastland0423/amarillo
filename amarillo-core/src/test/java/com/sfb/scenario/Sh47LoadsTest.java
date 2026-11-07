package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;

/**
 * SH47 "The Stasis Box" loads and sets up — the second published scenario in {@code
 * data/scenarios}, and the first one meant to be PLAYED rather than measured.
 *
 * <p>The file is a deliberate SUBSET. SH47's own box hunt needs hidden-identity counters, which
 * the engine does not have: eighteen face-down counters of which two are Stasis Boxes and two
 * are nuclear space mines, four removed unseen so neither player knows how many boxes exist at
 * all. None of that is in the file, and the tests below assert its ABSENCE as deliberately as
 * they assert the rest is present — a file that quietly shipped the boxes as ordinary visible
 * objectives would load, play, and not be SH47.
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

        assertEquals("Xerxes", xerxes.getName());
        assertEquals("Mystic", mystic.getName());
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
     * {@code destructionDirections} and must not use edges. SH47.41 fixes the map and names
     * map edges outright — "the Federation can only disengage from 42xx map edge" — so it uses
     * {@code destructionEdges} and must not use directions. The same two fields, opposite
     * verdicts, and the scenario text is what decides which.
     */
    @Test
    public void eachSideMayLeaveByOneMapEdgeOnly() {
        ScenarioSpec.SideSpec federation = spec.sides.get(0);
        ScenarioSpec.SideSpec kzinti     = spec.sides.get(1);

        assertEquals("SH47.41: the Federation can only disengage from the 42xx (right) edge",
                Set.of("TOP", "BOTTOM", "LEFT"), federation.destructionEdges);
        assertEquals("SH47.41: the Kzinti can only disengage from the 01xx (left) edge",
                Set.of("TOP", "BOTTOM", "RIGHT"), kzinti.destructionEdges);

        for (ScenarioSpec.SideSpec side : spec.sides)
            assertTrue(side.name + " restricts by map EDGE on a fixed map, not by direction",
                    side.destructionDirections.isEmpty());
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
}
