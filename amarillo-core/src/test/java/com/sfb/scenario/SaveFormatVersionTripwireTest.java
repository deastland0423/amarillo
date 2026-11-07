package com.sfb.scenario;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.Game;

/**
 * A DELIBERATE change-detector on the two save version numbers. If this test is failing, nothing
 * is broken — you have bumped a version, and there is a decision waiting for you.
 *
 * <h2>Read this before changing the numbers below</h2>
 * Bumping a save version is nearly free in itself: the check runs one way only, so it stops an
 * OLDER build from reading the file you just wrote. What it does NOT do is help the new build
 * read an OLD file — nothing branches on the version to migrate, so after a breaking change every
 * existing save still loads and comes back subtly wrong, bumped or not.
 *
 * <p>So the bump is the moment to decide which of three things you are doing:
 * <ul>
 *   <li><b>Carry old saves forward.</b> Read the version in {@code applyState} and convert the
 *       old shape. This is what the number is for, and it is not built yet.</li>
 *   <li><b>Declare old saves dead.</b> Add a floor as well as a ceiling, and refuse anything
 *       below N with a sentence that says which build to use. Honest, and often right
 *       mid-development.</li>
 *   <li><b>Neither</b> — which is what happens by default, and is the worst of the three,
 *       because the old saves still load.</li>
 * </ul>
 *
 * <p>And commit a GOLDEN SAVE in the same breath: a real save file, written by the build BEFORE
 * the bump, committed and loaded by a test. Every save test in this repo writes and reads with
 * today's code, so writer and reader move together and a renamed key passes — a committed file
 * cannot move, which is the entire point of it. Note that it cannot live in {@code data/saves},
 * which is gitignored as player data, and that its embedded scenario still NAMES hulls rather
 * than copying them, so it will legitimately start reporting changed hulls as the ship data
 * evolves. Decide which facts it asserts accordingly.
 *
 * <h2>Why this is a test and not a comment</h2>
 * The owner asked to be told if this ever happened. A comment beside the constant can be skimmed
 * past by anyone editing the line below it; a red build cannot. Satisfying it is one line once
 * the real decision is made, and whoever bumps the version is exactly the person who should be
 * making it.
 *
 * <p>This is therefore NOT a test to "fix" by updating the number and moving on.
 */
public class SaveFormatVersionTripwireTest {

    /** The versions this repo has shipped since the format was written. Both are still 1. */
    private static final int EXPECTED_STATE_VERSION = 1;
    private static final int EXPECTED_ENVELOPE_VERSION = 1;

    private static final String WHAT_TO_DO =
            " has been bumped. That is probably correct — but read the class comment on"
            + " SaveFormatVersionTripwireTest before updating this test: a bump wants a decision"
            + " about OLD saves (migrate them, or refuse them with a floor), and a golden save"
            + " fixture committed from the build before the bump. Then update the expected value"
            + " here, in the same commit.";

    @Test
    public void theStateFormatVersionHasNotChangedWithoutADecision() {
        assertEquals("Game.SAVE_FORMAT_VERSION" + WHAT_TO_DO,
                EXPECTED_STATE_VERSION, Game.SAVE_FORMAT_VERSION);
    }

    @Test
    public void theEnvelopeVersionHasNotChangedWithoutADecision() {
        assertEquals("SavedGame.ENVELOPE_VERSION" + WHAT_TO_DO,
                EXPECTED_ENVELOPE_VERSION, SavedGame.ENVELOPE_VERSION);
    }

    /**
     * The gap the golden save would close, pinned here so the reasoning is not lost: there is
     * currently NO migration path. Nothing reads either version to convert an old file, so the
     * numbers are a gate and nothing more.
     *
     * <p>If this assertion ever has to change because a migration exists, the golden save is no
     * longer optional — a migration with no old file to test it against is a guess.
     */
    @Test
    public void thereIsStillNoMigrationPath() {
        // A state claiming to be from version 1 and a state claiming version 0 (the fallback for
        // a file that does not say) are treated identically: read with today's reader, no
        // conversion. The day that stops being true, say so here.
        java.util.Map<String, Object> saysVersionOne = new java.util.LinkedHashMap<>();
        saysVersionOne.put("formatVersion", 1);
        java.util.Map<String, Object> saysNothing = new java.util.LinkedHashMap<>();

        assertEquals(1, Game.stateFormatVersion(saysVersionOne));
        assertEquals("a file that does not say reads as 0, and is still not converted",
                0, Game.stateFormatVersion(saysNothing));

        Game one = new Game();
        Game none = new Game();
        assertEquals("neither version is branched on, so both load the same way",
                one.applyState(saysVersionOne), none.applyState(saysNothing));
    }
}
