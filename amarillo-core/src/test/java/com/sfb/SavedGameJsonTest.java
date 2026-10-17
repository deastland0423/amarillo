package com.sfb;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sfb.dto.GameStateDto;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.scenario.SavedGame;
import com.sfb.scenario.ScenarioSpec;

/**
 * A battle through an actual JSON encoder, and back — the assumption the whole save format rests
 * on and the one nothing had tested.
 *
 * <h2>Why this test exists separately from the others</h2>
 * {@code StateIo} was written entirely because a save returns through a parser: what goes out as
 * an {@code int[]} comes back a {@code List&lt;Integer&gt;}, an {@code int} may arrive as a
 * {@code Long} or a {@code Double}, and an enum written as a name has to be read back by name.
 * But every earlier round-trip test handed the loader a live Java map, so none of that coercion
 * was ever exercised — the format was correct on an assumption about Jackson that nobody had
 * checked. One test faked the JSON shapes by hand, which is not the same as meeting them.
 *
 * <p>So this goes through {@code ObjectMapper} for real, both in memory and via a file on disk,
 * and compares the rebuilt battle to the original with the same {@link DtoDiff} witness the other
 * round-trip tests use.
 */
public class SavedGameJsonTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * Craft-based units are still unconverted, so they are filtered rather than tolerated — see
     * {@link GameStateRoundTripTest#comparable} for why a tolerated top-level field was too
     * coarse to be worth anything.
     */
    private static final Map<String, String> KNOWN_GAPS = Map.of(
            ".droneRacks", "DroneRack contents and settled mode are not saved yet (slice D3c)",
            ".controlUsed", "ControlSpaces is not converted yet (slice D3c)");

    // ---------------------------------------------------------------- through JSON

    /** The whole point: serialise to a string, read it back, and lose nothing. */
    @Test
    public void aBattleSurvivesARealJsonRoundTrip() throws Exception {
        Game before = battleInProgress();
        SavedGame saved = SavedGame.of(before, spec(), "through json");

        String json = saved.toJsonString();
        SavedGame.Restored restored = SavedGame.fromJsonString(json).restore();

        assertEquals("a clean save should load clean", List.of(), restored.problems());
        assertDiffIsOnlyKnownGaps(before, restored.game());
    }

    /** And via a real file, which is how a player will actually use it. */
    @Test
    public void aBattleSurvivesAFileOnDisk() throws Exception {
        Game before = battleInProgress();
        File file = new File(folder.getRoot(), "battle.json");

        SavedGame.of(before, spec(), "on disk").toJson(file);
        SavedGame.Restored restored = SavedGame.fromJson(file).restore();

        assertTrue("the file should exist and have content", file.length() > 0);
        assertEquals(List.of(), restored.problems());
        assertDiffIsOnlyKnownGaps(before, restored.game());
    }

    // ---------------------------------------------------------------- the coercions StateIo exists for

    /**
     * Shield strengths go out as an {@code int[]} and come back a {@code List&lt;Integer&gt;}.
     * This is the single most load-bearing coercion in the format.
     */
    @Test
    public void shieldArraysSurviveBecomingJsonLists() throws Exception {
        Game before = battleInProgress();
        int[] expected = shipNamed(before, "USS Hood").getShields().getShieldValues().clone();

        Game after = throughJson(before);

        assertArrayEquals(expected,
                shipNamed(after, "USS Hood").getShields().getShieldValues());
    }

    /** An enum written by name must come back as the same constant, not an ordinal's neighbour. */
    @Test
    public void armingTypeSurvivesAsAName() throws Exception {
        Game before = battleInProgress();
        com.sfb.weapons.Photon armed = firstPhoton(shipNamed(before, "USS Hood"));
        armed.setOverload();
        armed.arm(4);
        armed.setArmed(true);
        com.sfb.properties.WeaponArmingType expected = armed.getArmingType();

        Game after = throughJson(before);

        assertEquals(expected, firstPhoton(shipNamed(after, "USS Hood")).getArmingType());
        assertTrue(firstPhoton(shipNamed(after, "USS Hood")).isArmed());
    }

    /** A double must not come back as an int, and vice versa. */
    @Test
    public void fractionalValuesKeepTheirFractions() throws Exception {
        Game before = battleInProgress();
        com.sfb.objects.PlasmaTorpedo torpedo = plasmaInFlight(before);
        torpedo.applyPhaserDamage(3);   // 1 phaser point = 0.5 damage, so this lands on a half
        double expected = torpedo.getDamageTaken();
        assertTrue("fixture: should be a fraction, was " + expected, expected % 1 != 0);

        Game after = throughJson(before);

        assertEquals(expected, plasmaInFlight(after).getDamageTaken(), 0.0001);
    }

    /** A seeker rebuilt from JSON must point at the restored ships, not dangle. */
    @Test
    public void seekerReferencesSurviveJson() throws Exception {
        Game before = battleInProgress();

        Game after = throughJson(before);

        Seeker drone = after.getSeekers().stream()
                .filter(s -> s instanceof com.sfb.objects.Drone)
                .findFirst().orElseThrow(() -> new AssertionError("the drone was lost"));
        assertSame(shipNamed(after, "USS Hood"), drone.getTarget());
    }

    /** A ballistic course's hexes are a list of objects in JSON, and F4.11 counts them distinctly. */
    @Test
    public void ballisticCourseHexesSurviveJson() throws Exception {
        Game before = battleInProgress();
        com.sfb.objects.Drone drone = (com.sfb.objects.Drone) before.getSeekers().stream()
                .filter(s -> s instanceof com.sfb.objects.Drone).findFirst().orElseThrow();
        com.sfb.properties.Location aim = new com.sfb.properties.Location(20, 20);
        com.sfb.objects.BallisticCourse course = new com.sfb.objects.BallisticCourse(aim);
        drone.setTarget(null);
        drone.setBallisticCourse(course);
        course.noteHexEntered(com.sfb.utilities.MapUtils.getAdjacentHex(aim, 1, 42, 32));
        course.noteHexEntered(com.sfb.utilities.MapUtils.getAdjacentHex(aim, 9, 42, 32));

        Game after = throughJson(before);

        com.sfb.objects.Drone restored = (com.sfb.objects.Drone) after.getSeekers().stream()
                .filter(s -> s instanceof com.sfb.objects.Drone).findFirst().orElseThrow();
        assertEquals(aim, restored.getBallisticCourse().getTargetHex());
        assertEquals("two distinct hexes, through JSON", 2,
                restored.getBallisticCourse().getAdjacentHexesEntered());
    }

    // ---------------------------------------------------------------- the envelope

    /**
     * The scenario travels INSIDE the save. A battle assembled from saved fleets has a spec that
     * never existed as a file, so a save holding only an id could not rebuild it at all.
     */
    @Test
    public void theScenarioTravelsInsideTheSave() throws Exception {
        Game before = battleInProgress();

        SavedGame reread = SavedGame.fromJsonString(
                SavedGame.of(before, spec(), "embedded").toJsonString());

        assertNotNull("without the scenario there is nothing to rebuild", reread.scenario);
        assertEquals("Save fixture", reread.scenario.name);
        assertEquals(2, reread.scenario.sides.size());
    }

    /**
     * The turn number on a save is the turn ABOUT TO BE PLAYED, and at a turn boundary that is
     * the only honest answer.
     *
     * Reported from the first real test of the save button: allocating for turn 2, the note said
     * "Saved at turn 0". Both clock accessors are ambiguous at exactly this moment — energy
     * allocation for turn 1 sits at absolute impulse 0 and allocation for turn 2 at impulse 32,
     * and {@code getCurrentTurn()} answers 1 for BOTH, because it reports the turn that has been
     * playing rather than the one about to start. Since a save is only ever taken between turns,
     * counting completed turns and adding one is the number that belongs on it.
     */
    @Test
    public void theTurnOnASaveIsTheOneAboutToBePlayed() {
        Game game = new Game();

        assertEquals("a fresh game is about to play turn 1", 1, game.turnAboutToBePlayed());

        for (int i = 0; i < 32; i++)
            game.getClock().nextImpulse();
        assertEquals("one turn played, so turn 2 is next", 2, game.turnAboutToBePlayed());
        assertEquals("and the two accessors cannot tell this from impulse 0",
                1, game.getCurrentTurn());

        for (int i = 0; i < 32; i++)
            game.getClock().nextImpulse();
        assertEquals(3, game.turnAboutToBePlayed());
    }

    @Test
    public void aSaveIsLabelledWithThatTurn() throws Exception {
        Game before = battleInProgress();
        // battleInProgress advances 40 impulses, so it is mid-turn-2 and turn 2 is "current".
        int expected = before.turnAboutToBePlayed();

        SavedGame reread = SavedGame.fromJsonString(
                SavedGame.of(before, spec(), "turn check").toJsonString());

        assertEquals(expected, reread.turn);
        assertTrue("and it should not be the 0-based clock turn",
                reread.turn > before.getClock().getTurn());
    }

    @Test
    public void theSaveCarriesItsLabelAndTurn() throws Exception {
        Game before = battleInProgress();
        int turn = before.turnAboutToBePlayed();

        SavedGame reread = SavedGame.fromJsonString(
                SavedGame.of(before, spec(), "Turn " + turn + ", before the pass").toJsonString());

        assertEquals("Turn " + turn + ", before the pass", reread.label);
        assertEquals(turn, reread.turn);
        assertNotNull(reread.savedAt);
    }

    /** A save with no scenario is refused, not loaded as an empty battle. */
    @Test
    public void aSaveWithNoScenarioIsRefused() {
        SavedGame broken = new SavedGame();
        broken.state = Map.of();

        SavedGame.Restored restored = broken.restore();

        assertNull(restored.game());
        assertFalse(restored.problems().isEmpty());
    }

    /** A save from a future build is reported rather than half-applied. */
    @Test
    public void aFutureEnvelopeIsReported() throws Exception {
        Game before = battleInProgress();
        SavedGame saved = SavedGame.of(before, spec(), "from the future");
        saved.envelopeVersion = SavedGame.ENVELOPE_VERSION + 1;

        List<String> problems = SavedGame.fromJsonString(saved.toJsonString())
                .restore().problems();

        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("newer")));
    }

    /** Taking a save must not disturb the game being saved. */
    @Test
    public void savingDoesNotChangeTheGame() throws Exception {
        Game game = battleInProgress();
        GameStateDto beforeSave = comparable(game);

        SavedGame.of(game, spec(), "untouched").toJsonString();

        assertEquals("a snapshot must be a read, not a move",
                List.of(), DtoDiff.between(beforeSave, comparable(game)));
    }

    // ---------------------------------------------------------------- helpers

    private void assertDiffIsOnlyKnownGaps(Game before, Game after) {
        List<String> unexpected = new ArrayList<>();
        for (String difference : DtoDiff.between(comparable(before), comparable(after)))
            if (KNOWN_GAPS.keySet().stream().noneMatch(difference::contains))
                unexpected.add(difference);

        assertTrue("these did not survive a JSON round trip:\n  "
                + String.join("\n  ", unexpected), unexpected.isEmpty());
    }

    /** Delegates to the shared helper, which also normalises the unordered collections. */
    private static GameStateDto comparable(Game game) {
        return DtoDiff.comparable(game);
    }

    private static Game throughJson(Game game) throws Exception {
        SavedGame.Restored restored = SavedGame
                .fromJsonString(SavedGame.of(game, spec(), "round trip").toJsonString())
                .restore();
        assertNotNull("the save should have rebuilt a game", restored.game());
        return restored.game();
    }

    /** A Federation CA and a Klingon D7, so there are both photons and drone racks. */
    private static ScenarioSpec spec() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Save fixture";
        spec.year = 180;
        spec.mapCols = 42;
        spec.mapRows = 32;

        ScenarioSpec.SideSpec federation = new ScenarioSpec.SideSpec();
        federation.faction = "Federation";
        federation.name = "Starfleet";
        federation.ships = new ArrayList<>(List.of(ship("Federation", "CA", "USS Hood", "1010")));

        ScenarioSpec.SideSpec romulan = new ScenarioSpec.SideSpec();
        romulan.faction = "Romulan";
        romulan.name = "Empire";
        romulan.ships = new ArrayList<>(List.of(ship("Klingon", "D7", "IKV Blade", "1016")));

        spec.sides = new ArrayList<>(List.of(federation, romulan));
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

    /** A battle with damage, an armed weapon, and a drone and a torpedo in flight. */
    private static Game battleInProgress() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        Game game = new Game();
        game.setupFromScenario(spec());
        for (int i = 0; i < 40; i++)
            game.getClock().nextImpulse();

        Ship hood = shipNamed(game, "USS Hood");
        Ship blade = shipNamed(game, "IKV Blade");
        hood.getShields().damageShield(2, 5);
        hood.getShields().damageShield(4, 11);
        hood.getHullBoxes().damageFhull();
        blade.getShields().damageShield(5, 4);
        blade.getCrew().setAvailableCrewUnits(blade.getCrew().getAvailableCrewUnits() - 2);
        try {
            hood.chargeCapacitor(2);
        } catch (Exception e) {
            throw new AssertionError("fixture: the CA should take capacitor charge", e);
        }

        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        // A drone from the Klingon at the Federation ship.
        com.sfb.weapons.DroneRack rack = blade.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.DroneRack)
                .map(w -> (com.sfb.weapons.DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("fixture: the D7 has racks"));
        blade.setActiveFireControl(true);
        blade.addLockOn(hood);
        Game.ActionResult launched = game.launchDrone(blade, hood, rack);
        assertTrue("fixture: " + launched.getMessage(), launched.isSuccess());

        // And a torpedo in flight, added directly: the point here is the JSON, not the launch
        // rules, and a plasma ship is not needed to prove a torpedo serialises.
        com.sfb.objects.PlasmaTorpedo torpedo = new com.sfb.objects.PlasmaTorpedo(
                com.sfb.properties.PlasmaType.S, com.sfb.properties.WeaponArmingType.STANDARD);
        torpedo.setName("Plasma-1");
        torpedo.setLocation(new com.sfb.properties.Location(10, 13));
        torpedo.setFacing(1);
        torpedo.setSpeed(32);
        torpedo.setTarget(hood);
        torpedo.setController(blade);
        torpedo.setLaunchImpulse(game.getClock().getImpulse());
        game.getSeekers().add(torpedo);

        return game;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship ship : game.getShips())
            if (name.equals(ship.getName()))
                return ship;
        throw new AssertionError("no ship called " + name);
    }

    private static com.sfb.weapons.Photon firstPhoton(Ship ship) {
        for (com.sfb.weapons.Weapon weapon : ship.getWeapons().fetchAllWeapons())
            if (weapon instanceof com.sfb.weapons.Photon photon)
                return photon;
        throw new AssertionError("no photon on " + ship.getName());
    }

    private static com.sfb.objects.PlasmaTorpedo plasmaInFlight(Game game) {
        return game.getSeekers().stream()
                .filter(s -> s instanceof com.sfb.objects.PlasmaTorpedo)
                .map(s -> (com.sfb.objects.PlasmaTorpedo) s)
                .findFirst().orElseThrow(() -> new AssertionError("no torpedo in flight"));
    }
}
