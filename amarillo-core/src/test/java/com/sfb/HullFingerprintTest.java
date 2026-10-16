package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.scenario.SavedGame;
import com.sfb.scenario.ScenarioSpec;

/**
 * Noticing that a hull changed under a saved game.
 *
 * <h2>The hazard</h2>
 * A save is a scenario plus accumulated damage, and the scenario names HULLS which are read from
 * {@code data/factions} at load time. This repo edits ship JSON constantly, so without a check,
 * editing a hull between sessions makes a restored ship quietly become a DIFFERENT SHIP with the
 * same name — same designation, different weapon, and nothing to say so. That is the worst shape
 * a bug can have in a saved campaign: not a crash, a substitution.
 *
 * <p>So a save records a fingerprint per ship and compares it on load. The comparison WARNS and
 * never refuses: a changed hull may be a BPV correction that affects nothing in progress, and a
 * player who has lost an evening's battle is not helped by a refusal — they are helped by being
 * told which ship to look at.
 */
public class HullFingerprintTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    // ---------------------------------------------------------------- the fingerprint itself

    @Test
    public void aHullFingerprintsTheSameWayTwice() {
        ShipSpec ca = ShipLibrary.get("Federation", "CA");

        assertEquals(ca.fingerprint(), ca.fingerprint());
        assertFalse("a fingerprint should not be empty", ca.fingerprint().isEmpty());
    }

    @Test
    public void differentHullsFingerprintDifferently() {
        assertNotEquals(ShipLibrary.get("Federation", "CA").fingerprint(),
                ShipLibrary.get("Klingon", "D7").fingerprint());
    }

    /** The point of it: a change to what the ship CAN DO must move the fingerprint. */
    @Test
    public void changingWhatTheShipCanDoChangesTheFingerprint() {
        ShipSpec ca = ShipLibrary.get("Federation", "CA");
        String before = ca.fingerprint();

        int wasBpv = ca.bpv;
        ca.bpv = wasBpv + 1;
        assertNotEquals("a BPV change is a change", before, ca.fingerprint());
        ca.bpv = wasBpv;

        assertEquals("and putting it back restores the fingerprint", before, ca.fingerprint());
    }

    /**
     * Description-only fields are excluded on purpose. A warning that fires because somebody drew
     * new token art is a warning people learn to ignore, and then the real one goes unread.
     */
    @Test
    public void cosmeticFieldsDoNotChangeTheFingerprint() {
        ShipSpec ca = ShipLibrary.get("Federation", "CA");
        String before = ca.fingerprint();

        String wasArt = ca.tokenArt;
        String wasNote = ca.note;
        String wasTypeName = ca.typeName;
        ca.tokenArt = "somewhere/else.png";
        ca.note = "a note nobody reads";
        ca.typeName = "Renamed For Display";

        assertEquals("art, notes and display names are not rules", before, ca.fingerprint());

        ca.tokenArt = wasArt;
        ca.note = wasNote;
        ca.typeName = wasTypeName;
    }

    // ---------------------------------------------------------------- through a save

    @Test
    public void aSaveRecordsAFingerprintForEveryShip() {
        SavedGame saved = SavedGame.of(gameFor(spec()), spec(), "fingerprinted");

        assertEquals(2, saved.hullFingerprints.size());
        assertTrue(saved.hullFingerprints.containsKey("USS Hood"));
        assertTrue(saved.hullFingerprints.containsKey("IKV Blade"));
        for (String print : saved.hullFingerprints.values())
            assertFalse(print.isEmpty());
    }

    @Test
    public void anUnchangedHullLoadsWithoutComment() {
        SavedGame saved = SavedGame.of(gameFor(spec()), spec(), "unchanged");

        assertEquals(List.of(), saved.restore().problems());
    }

    /**
     * THE TEST THIS WHOLE FEATURE EXISTS FOR. The hull is genuinely edited between the save and
     * the load, exactly as it would be by somebody tuning ship data between sessions.
     */
    @Test
    public void aHullEditedBetweenSessionsIsReportedByName() {
        SavedGame saved = SavedGame.of(gameFor(spec()), spec(), "stale");

        ShipSpec d7 = ShipLibrary.get("Klingon", "D7");
        int wasBpv = d7.bpv;
        try {
            d7.bpv = wasBpv + 7;   // somebody corrects a BPV between sessions

            List<String> problems = saved.restore().problems();

            assertTrue("the changed ship must be named: " + problems,
                    problems.stream().anyMatch(p -> p.contains("IKV Blade")));
            assertFalse("the untouched ship must not be: " + problems,
                    problems.stream().anyMatch(p -> p.contains("USS Hood")));
        } finally {
            d7.bpv = wasBpv;
        }
    }

    /** It warns; it does not refuse. A lost evening is not improved by a refusal. */
    @Test
    public void aChangedHullStillLoadsTheBattle() {
        SavedGame saved = SavedGame.of(gameFor(spec()), spec(), "still playable");

        ShipSpec d7 = ShipLibrary.get("Klingon", "D7");
        int wasBpv = d7.bpv;
        try {
            d7.bpv = wasBpv + 7;

            SavedGame.Restored restored = saved.restore();

            assertNotNull("the battle must still come back", restored.game());
            assertEquals(2, restored.game().getShips().size());
            assertFalse(restored.problems().isEmpty());
        } finally {
            d7.bpv = wasBpv;
        }
    }

    // ---------------------------------------------------------------- staying quiet when it should

    /**
     * An older save has no fingerprints recorded. Warning about every ship in that case would
     * mean every old save complains about everything, which is how a warning becomes noise.
     */
    @Test
    public void anOlderSaveWithNoFingerprintsSaysNothing() {
        SavedGame saved = SavedGame.of(gameFor(spec()), spec(), "from before fingerprints");
        saved.hullFingerprints.clear();

        assertEquals(List.of(), saved.restore().problems());
    }

    @Test
    public void anUnknownFingerprintIsNotTreatedAsAMismatch() {
        SavedGame saved = SavedGame.of(gameFor(spec()), spec(), "partly unknown");
        saved.hullFingerprints.put("IKV Blade", "");

        assertEquals("an empty fingerprint means unknown, not changed",
                List.of(), saved.restore().problems());
    }

    /** And it survives the trip through JSON, since that is how a save actually travels. */
    @Test
    public void fingerprintsSurviveTheJsonRoundTrip() throws Exception {
        SavedGame saved = SavedGame.of(gameFor(spec()), spec(), "through json");
        Map<String, String> expected = new java.util.LinkedHashMap<>(saved.hullFingerprints);

        SavedGame reread = SavedGame.fromJsonString(saved.toJsonString());

        assertEquals(expected, reread.hullFingerprints);

        ShipSpec d7 = ShipLibrary.get("Klingon", "D7");
        int wasBpv = d7.bpv;
        try {
            d7.bpv = wasBpv + 7;
            assertTrue("and still detect a change after a round trip",
                    reread.restore().problems().stream().anyMatch(p -> p.contains("IKV Blade")));
        } finally {
            d7.bpv = wasBpv;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static ScenarioSpec spec() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Fingerprint fixture";
        spec.year = 180;
        spec.mapCols = 42;
        spec.mapRows = 32;

        ScenarioSpec.SideSpec fed = new ScenarioSpec.SideSpec();
        fed.faction = "Federation";
        fed.name = "Starfleet";
        fed.ships = new ArrayList<>(List.of(ship("Federation", "CA", "USS Hood", "1010")));

        ScenarioSpec.SideSpec klingon = new ScenarioSpec.SideSpec();
        klingon.faction = "Klingon";
        klingon.name = "Empire";
        klingon.ships = new ArrayList<>(List.of(ship("Klingon", "D7", "IKV Blade", "1016")));

        spec.sides = new ArrayList<>(List.of(fed, klingon));
        return spec;
    }

    private static ScenarioSpec.ShipSetup ship(String faction, String type, String name,
            String hex) {
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.faction = faction;
        setup.type = type;
        setup.shipName = name;
        setup.startHex = hex;
        setup.startHeading = "A";
        return setup;
    }

    private static Game gameFor(ScenarioSpec spec) {
        Game game = new Game();
        game.setupFromScenario(spec);
        return game;
    }
}
