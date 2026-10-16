package com.sfb.scenario;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.sfb.Game;

/**
 * A battle on disk: the scenario it was fought in, plus what the battle did to it.
 *
 * <h2>Why the scenario is EMBEDDED and not referenced</h2>
 * A save has to rebuild the ships before it can overlay their damage, and the obvious way to do
 * that is to name the scenario file. It does not work. Half the battles this game plays are
 * assembled from saved fleets by {@code FleetsToScenario}, and the resulting {@code ScenarioSpec}
 * never existed as a file — there is nothing to name. A save that only held an id could restore a
 * hand-written scenario and nothing else.
 *
 * <p>So the whole spec travels inside the save, which also makes the file self-contained: it can
 * be copied to another machine, or kept after the scenario it came from is edited, and still
 * describe the battle it was.
 *
 * <h2>What is still not self-contained, and the hazard that leaves</h2>
 * The spec names HULLS, and the hulls are read from {@code data/factions} at load time. This repo
 * edits ship JSON constantly, so a hull changed between sessions quietly restores as a different
 * ship — same name, different weapon. The answer is a fingerprint of each resolved
 * {@code ShipSpec} stored here and compared on load; it is not built yet, and this comment is
 * where it belongs when it is.
 *
 * <h2>The load order is the format</h2>
 * <pre>
 *   SavedGame saved = SavedGame.fromJson(file);
 *   Game game = new Game();
 *   game.setupFromScenario(saved.scenario);   // build the pieces
 *   List&lt;String&gt; problems = game.applyState(saved.state);   // overlay the battle
 * </pre>
 * {@link #restore()} does exactly that and is the only thing callers should need.
 */
public class SavedGame {

    /**
     * Bumped when the envelope changes. Distinct from {@link Game#SAVE_FORMAT_VERSION}, which
     * versions the STATE inside: the two can move independently, and a reader needs to know
     * which one it cannot understand.
     */
    public static final int ENVELOPE_VERSION = 1;

    public int envelopeVersion = ENVELOPE_VERSION;

    /** ISO-8601, for showing a player which save is which. */
    public String savedAt;

    /** Whatever the player called it, or a generated name. */
    public String label;

    /** Turn number when the save was taken — shown in a save list, never trusted on load. */
    public int turn;

    /** The scenario the battle was fought in, embedded in full. */
    public ScenarioSpec scenario;

    /** What the battle did, as {@link Game#exportState()} wrote it. */
    public Map<String, Object> state;

    /**
     * Ship name → a fingerprint of the hull it was built from, as it stood when the save was
     * taken (see {@code ShipSpec.fingerprint()}).
     *
     * The one thing an embedded scenario still cannot make self-contained. The spec names HULLS,
     * and hulls are read from {@code data/factions} at load time, so editing one between sessions
     * makes a restored ship quietly become a different ship with the same name. Recording the
     * fingerprint turns that silent substitution into a sentence the player can read.
     */
    public Map<String, String> hullFingerprints = new java.util.LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Taking a save
    // -------------------------------------------------------------------------

    /**
     * Snapshot a game in progress.
     *
     * @param scenario the spec this game was built from — the caller has to keep it, because a
     *                 {@code Game} does not remember the spec it was made from
     */
    public static SavedGame of(Game game, ScenarioSpec scenario, String label) {
        SavedGame saved = new SavedGame();
        saved.envelopeVersion = ENVELOPE_VERSION;
        saved.savedAt = java.time.Instant.now().toString();
        saved.label = label;
        saved.turn = game.getClock().getTurn();
        saved.scenario = scenario;
        saved.state = game.exportState();
        saved.hullFingerprints = fingerprintHulls(scenario);
        return saved;
    }

    /** What came back from a load: the game, and anything worth telling the player. */
    public record Restored(Game game, List<String> problems) {
    }

    /**
     * Rebuild the battle: construct from the scenario, then overlay the state.
     *
     * A save with no scenario cannot be rebuilt at all, and says so rather than handing back an
     * empty game that looks like a battle nobody is fighting.
     */
    public Restored restore() {
        List<String> problems = new ArrayList<>();
        if (envelopeVersion > ENVELOPE_VERSION)
            problems.add("this save file was written by a newer version of the game (envelope "
                    + envelopeVersion + ", this build reads " + ENVELOPE_VERSION + ")");
        if (scenario == null) {
            problems.add("this save has no scenario in it and cannot be rebuilt");
            return new Restored(null, problems);
        }

        // Checked BEFORE the game is built, so the warning names the ship whether or not the
        // rest of the load goes cleanly.
        problems.addAll(changedHulls());

        Game game = new Game();
        game.setupFromScenario(scenario);
        problems.addAll(game.applyState(state));
        return new Restored(game, problems);
    }

    // -------------------------------------------------------------------------
    // Hull fingerprints
    // -------------------------------------------------------------------------

    /** Fingerprint every hull the scenario names, keyed by the ship's name. */
    private static Map<String, String> fingerprintHulls(ScenarioSpec scenario) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (scenario == null || scenario.sides == null)
            return out;
        for (ScenarioSpec.SideSpec side : scenario.sides) {
            if (side.ships == null)
                continue;
            for (ScenarioSpec.ShipSetup setup : side.ships) {
                if (setup.shipName == null)
                    continue;
                com.sfb.objects.ShipSpec spec = hullFor(setup, side);
                if (spec != null)
                    out.put(setup.shipName, spec.fingerprint());
            }
        }
        return out;
    }

    /**
     * Which hulls have changed since the save was taken.
     *
     * <h2>Three things this deliberately does not do</h2>
     * It does not refuse the load. A changed hull may be a BPV correction that affects nothing in
     * progress, and a player who has lost an evening's battle is not helped by a refusal — they
     * are helped by being told which ship to look at.
     *
     * <p>It says nothing when either fingerprint is unknown. An older save has none recorded, and
     * a hull that could not be fingerprinted reports an empty string; warning in those cases
     * would mean every old save complains about every ship, which trains people to ignore it.
     *
     * <p>And it does not try to say WHAT changed. The fingerprint is a hash, so it knows only
     * that something did — naming the ship is the useful half, and the player has the SSD.
     */
    private List<String> changedHulls() {
        List<String> problems = new ArrayList<>();
        if (hullFingerprints == null || hullFingerprints.isEmpty())
            return problems;

        Map<String, String> now = fingerprintHulls(scenario);
        for (Map.Entry<String, String> saved : hullFingerprints.entrySet()) {
            String wasSaved = saved.getValue();
            String isNow = now.get(saved.getKey());
            if (wasSaved == null || wasSaved.isEmpty() || isNow == null || isNow.isEmpty())
                continue;
            if (!wasSaved.equals(isNow))
                problems.add(saved.getKey() + ": its hull data has changed since this save was"
                        + " taken, so it may not be the ship it was — check its SSD");
        }
        return problems;
    }

    /** The hull a scenario entry names, preferring its own faction over its side's. */
    private static com.sfb.objects.ShipSpec hullFor(ScenarioSpec.ShipSetup setup,
            ScenarioSpec.SideSpec side) {
        String faction = setup.faction != null ? setup.faction : side.faction;
        if (faction == null || setup.type == null)
            return null;
        return com.sfb.objects.ShipLibrary.get(faction, setup.type);
    }

    // -------------------------------------------------------------------------
    // Files
    // -------------------------------------------------------------------------

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static SavedGame fromJson(File file) throws IOException {
        return MAPPER.readValue(file, SavedGame.class);
    }

    public void toJson(File file) throws IOException {
        MAPPER.writeValue(file, this);
    }

    /** Round-trip through the JSON encoder without touching disk — what the tests lean on. */
    public String toJsonString() throws IOException {
        return MAPPER.writeValueAsString(this);
    }

    public static SavedGame fromJsonString(String json) throws IOException {
        return MAPPER.readValue(json, SavedGame.class);
    }
}
