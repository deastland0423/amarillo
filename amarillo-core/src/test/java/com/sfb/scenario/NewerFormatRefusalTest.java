package com.sfb.scenario;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.Game;

/**
 * A save written by a NEWER build is refused, not loaded with a warning.
 *
 * <h2>Why refusing is right, when warning usually is not</h2>
 * This codebase prefers to load what it can and report the rest — a changed hull, a craft in
 * flight that could not be rebuilt. That is correct when the damage is NAMED: the player reads
 * "USS Hood: its hull data has changed" and knows exactly what they are looking at.
 *
 * <p>A newer format is the opposite case. {@code StateIo} takes the keys it knows and ignores the
 * rest, so a save holding systems this build has never heard of comes back as a complete,
 * plausible battle with those systems SILENTLY ABSENT — and nothing can name what is missing,
 * because this build does not know the names. A warning cannot help, because by the time anyone
 * reads it the wrong battle is already on screen. So the file is refused and the player chooses.
 *
 * <p>Both versions are checked, and they are genuinely two: the ENVELOPE is {@code SavedGame} —
 * scenario, fingerprints, label — and the STATE is what {@code Game.exportState} writes inside.
 */
public class NewerFormatRefusalTest {

    // ---------------------------------------------------------------- fixtures

    private static ScenarioSpec spec() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Refusal fixture";
        spec.year = 180;
        spec.mapCols = 42;
        spec.mapRows = 32;

        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.faction = "Federation";
        setup.type = "CA";
        setup.shipName = "USS Hood";
        setup.startHex = "1010";
        setup.startHeading = "A";

        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = "Federation";
        side.name = "Starfleet";
        side.ships = new ArrayList<>(List.of(setup));
        spec.sides = new ArrayList<>(List.of(side));
        return spec;
    }

    private static SavedGame saved() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        Game game = new Game();
        game.setupFromScenario(spec());
        return SavedGame.of(game, spec(), "refusal");
    }

    // ---------------------------------------------------------------- the refusal

    /** A newer ENVELOPE is refused: no game comes back, and the reason says what to do. */
    @Test
    public void aNewerEnvelopeIsRefused() {
        SavedGame save = saved();
        save.envelopeVersion = SavedGame.ENVELOPE_VERSION + 1;

        SavedGame.Restored restored = save.restore();

        assertNull("nothing should be rebuilt from a file we may not understand", restored.game());
        assertTrue(restored.problems().toString(),
                restored.problems().stream().anyMatch(p -> p.contains("newer version")));
        assertTrue("the refusal has to say what the player can do: " + restored.problems(),
                restored.problems().stream().anyMatch(p -> p.contains("refusing to load it")));
    }

    /** A newer STATE format is refused the same way — the two versions move independently. */
    @Test
    public void aNewerStateFormatIsRefused() {
        SavedGame save = saved();
        save.state.put("formatVersion", Game.SAVE_FORMAT_VERSION + 1);

        SavedGame.Restored restored = save.restore();

        assertNull(restored.game());
        assertTrue(restored.problems().toString(),
                restored.problems().stream().anyMatch(p -> p.contains("newer version")));
    }

    /** And the ordinary save still loads, so the guard has not closed the door on everything. */
    @Test
    public void aSaveFromThisBuildLoadsNormally() {
        SavedGame.Restored restored = saved().restore();

        assertNotNull(restored.game());
        assertEquals(List.of(), restored.problems());
    }

    // ---------------------------------------------------------------- the override

    /**
     * The player may overrule it. Named rather than a boolean flag on {@code restore} so the
     * call site says WHICH guard is being waived — a guard added later is not waived with it.
     */
    @Test
    public void thePlayerCanOpenItAnyway() {
        SavedGame save = saved();
        save.envelopeVersion = SavedGame.ENVELOPE_VERSION + 1;

        SavedGame.Restored restored = save.restoreIgnoringFormatVersion();

        assertNotNull("forcing should rebuild what it can", restored.game());
        assertEquals(1, restored.game().getShips().size());
        assertTrue("and still say what was risked: " + restored.problems(),
                restored.problems().stream().anyMatch(p -> p.contains("newer version")));
        assertFalse("but not repeat the refusal, which no longer applies",
                restored.problems().stream().anyMatch(p -> p.contains("refusing to load it")));
    }

    /**
     * Forcing a newer STATE version must not report the same fact twice. {@code applyState} adds
     * its own version problem, and the two layers used to word it differently — so a forced load
     * showed the player one sentence and then the same sentence again.
     */
    @Test
    public void aForcedLoadSaysTheVersionProblemOnce() {
        SavedGame save = saved();
        save.state.put("formatVersion", Game.SAVE_FORMAT_VERSION + 1);

        List<String> problems = save.restoreIgnoringFormatVersion().problems();

        long versionLines = problems.stream().filter(p -> p.contains("newer version")).count();
        assertEquals("said once, not once per layer: " + problems, 1, versionLines);
    }

    /**
     * Forcing is NOT a general "load anything" switch. A save with no scenario cannot be rebuilt
     * whatever the player chooses, and must still refuse — otherwise the escape hatch becomes a
     * way to get an empty game that looks like a battle.
     */
    @Test
    public void forcingDoesNotRescueASaveWithNoScenario() {
        SavedGame save = saved();
        save.envelopeVersion = SavedGame.ENVELOPE_VERSION + 1;
        save.scenario = null;

        SavedGame.Restored restored = save.restoreIgnoringFormatVersion();

        assertNull(restored.game());
        assertTrue(restored.problems().toString(), restored.problems().stream()
                .anyMatch(p -> p.contains("no scenario")));
    }

    // ---------------------------------------------------------------- the query

    /** What the save LIST asks, so a row can be greyed before anybody presses anything. */
    @Test
    public void readabilityCanBeAskedWithoutLoading() {
        SavedGame fine = saved();
        assertTrue(fine.isReadableByThisBuild());
        assertEquals(List.of(), fine.formatProblems());

        SavedGame future = saved();
        future.state.put("formatVersion", Game.SAVE_FORMAT_VERSION + 1);
        assertFalse(future.isReadableByThisBuild());
        assertEquals(1, future.formatProblems().size());

        SavedGame bothWrong = saved();
        bothWrong.envelopeVersion = SavedGame.ENVELOPE_VERSION + 1;
        bothWrong.state.put("formatVersion", Game.SAVE_FORMAT_VERSION + 1);
        assertEquals("both halves reported, so the player knows which build to use",
                2, bothWrong.formatProblems().size());
    }

    /**
     * The readability query must not leak into the FILE.
     *
     * <p>{@code isReadableByThisBuild()} is bean-shaped, so Jackson serialised it as a
     * {@code readableByThisBuild} field in every save — and then refused to read any save back,
     * because this class deliberately does not ignore unknown properties. Sixteen tests failed
     * at once, which is how it was caught; this one names the hazard so the next accessor added
     * here does not repeat it.
     */
    @Test
    public void theReadabilityQueryIsNotWrittenIntoTheFile() throws Exception {
        String json = saved().toJsonString();

        assertFalse("a derived query must not become a saved field: " + json,
                json.contains("readableByThisBuild"));
        assertNotNull("and the file still reads back", SavedGame.fromJsonString(json).restore().game());
    }

    /** An OLDER save is not a problem: reading one is the whole point of versioning it. */
    @Test
    public void anOlderFormatIsNotRefused() {
        SavedGame save = saved();
        save.envelopeVersion = SavedGame.ENVELOPE_VERSION - 1;
        save.state.put("formatVersion", Game.SAVE_FORMAT_VERSION - 1);

        assertTrue(save.isReadableByThisBuild());
        assertNotNull(save.restore().game());
    }
}
