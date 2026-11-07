package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfb.Game;

/**
 * A save or a fleet is written atomically: the destination holds the previous file or the complete
 * new one, never a fragment.
 *
 * <h2>Why this is worth a test of its own</h2>
 * Both writes used to be {@code mapper.writeValue(file, this)} straight to the destination, which
 * truncates the existing file the instant it opens. A crash, a full disk or a killed server
 * partway through left a file that was neither save — and the previous one was already gone. The
 * round-trip tests could never catch it, because they only ever wrote successfully.
 *
 * <p>So these tests make the write FAIL on purpose, which is the only way to assert the
 * guarantee. A value Jackson cannot serialise stands in for the disk failure: it breaks the write
 * at the same point — after the destination has been chosen, before the bytes are all down.
 */
public class AtomicJsonWriteTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Jackson cannot serialise this: no properties, and no accessors to infer any from. */
    public static class Unserialisable {
        private Unserialisable() {
        }
    }

    // ---------------------------------------------------------------- the guarantee

    /**
     * The heart of it: a write that fails leaves the PREVIOUS file exactly as it was.
     *
     * <p>This is the case that mattered. A player saves over last turn's file, the write dies
     * halfway, and the old save has to still be there — it is the only copy of the battle.
     */
    @Test
    public void aFailedWriteLeavesThePreviousFileIntact() throws Exception {
        File dest = new File(folder.getRoot(), "battle.json");
        Files.writeString(dest.toPath(), "{\"label\":\"the previous save\"}", StandardCharsets.UTF_8);
        long before = dest.length();

        try {
            JsonFiles.writeAtomically(MAPPER, dest, new Unserialisable(), Object.class);
            fail("the fixture should not be serialisable");
        } catch (IOException expected) {
            // what a disk failure looks like from here
        }

        assertTrue("the previous save must still exist", dest.isFile());
        assertEquals("and be untouched, not truncated", before, dest.length());
        assertEquals("{\"label\":\"the previous save\"}",
                Files.readString(dest.toPath(), StandardCharsets.UTF_8));
    }

    /** And a failed write to a NEW name leaves nothing behind — no half-file to load later. */
    @Test
    public void aFailedWriteCreatesNothing() throws Exception {
        File dest = new File(folder.getRoot(), "fresh.json");

        try {
            JsonFiles.writeAtomically(MAPPER, dest, new Unserialisable(), Object.class);
            fail("the fixture should not be serialisable");
        } catch (IOException expected) {
            // expected
        }

        assertFalse("no partial file at the destination", dest.isFile());
    }

    /**
     * The validation re-read: a file that writes but will not parse back never reaches the
     * destination. This is the step that catches a short write, which is the failure the whole
     * mechanism is for — a truncated file serialises fine and simply will not read.
     */
    @Test
    public void aFileThatWillNotParseBackIsRefused() throws Exception {
        File dest = new File(folder.getRoot(), "battle.json");
        Files.writeString(dest.toPath(), "the previous save", StandardCharsets.UTF_8);

        try {
            // Writes a JSON array, then tries to read it back as an object: the write succeeds
            // and the re-read fails, which is where a truncated file fails too.
            JsonFiles.writeAtomically(MAPPER, dest, List.of("a", "b"), ScenarioSpec.class);
            fail("an array should not parse back as a ScenarioSpec");
        } catch (IOException expected) {
            // expected
        }

        assertEquals("the destination is untouched", "the previous save",
                Files.readString(dest.toPath(), StandardCharsets.UTF_8));
    }

    /**
     * No temporary file is left lying around. A {@code .tmp} beside the saves would show up in
     * the save list and be offered to the player.
     *
     * <p>The failing case here has to be one that fails AFTER the temporary file exists, or this
     * test passes for the wrong reason — which it did at first. A serialisation failure throws
     * before the file is created, so the cleanup it was meant to exercise never ran and deleting
     * the {@code finally} block did not fail the test. A re-read failure breaks in the right
     * place.
     */
    @Test
    public void noTemporaryFilesSurvive() throws Exception {
        JsonFiles.writeAtomically(MAPPER, new File(folder.getRoot(), "ok.json"),
                List.of("a", "b"), List.class);
        try {
            JsonFiles.writeAtomically(MAPPER, new File(folder.getRoot(), "bad.json"),
                    List.of("a", "b"), ScenarioSpec.class);
        } catch (IOException expected) {
            // broke after the temporary file was written — the case that needs cleaning up
        }

        List<String> leftovers = new ArrayList<>();
        for (File f : folder.getRoot().listFiles())
            if (f.getName().endsWith(".tmp"))
                leftovers.add(f.getName());
        assertEquals("no .tmp files beside the saves: " + leftovers, List.of(), leftovers);
    }

    /** The ordinary path still works, and replaces what was there. */
    @Test
    public void asuccessfulWriteReplacesTheDestination() throws Exception {
        File dest = new File(folder.getRoot(), "battle.json");
        Files.writeString(dest.toPath(), "old", StandardCharsets.UTF_8);

        JsonFiles.writeAtomically(MAPPER, dest, List.of("new"), List.class);

        assertEquals("[\"new\"]", Files.readString(dest.toPath(), StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- through the real callers

    /** A real save still round-trips, written through the atomic path. */
    @Test
    public void aSavedGameStillWritesAndReads() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        ScenarioSpec spec = oneShip();
        Game game = new Game();
        game.setupFromScenario(spec);

        File file = new File(folder.getRoot(), "real-save.json");
        SavedGame.of(game, spec, "atomic").toJson(file);

        assertTrue(file.length() > 0);
        SavedGame.Restored restored = SavedGame.fromJson(file).restore();
        assertEquals(List.of(), restored.problems());
        assertEquals(1, restored.game().getShips().size());
    }

    /**
     * And a fleet, which shares the mechanism rather than having its own. A saved fleet is the
     * owner's own file too — nothing regenerates it — so it wanted the same guarantee, and
     * fixing the one that surfaced the problem would have left the other broken.
     */
    @Test
    public void aFleetSpecStillWritesAndReads() throws Exception {
        FleetSpec fleet = new FleetSpec();
        fleet.id = "atomic-fleet";
        fleet.name = "Atomic";
        fleet.year = 180;
        fleet.budget = 500;
        fleet.factions = new ArrayList<>(List.of("Federation"));

        File file = new File(folder.getRoot(), "fleet.json");
        fleet.toJson(file);

        assertEquals("atomic-fleet", FleetSpec.fromJson(file).id);
    }

    // ---------------------------------------------------------------- fixture

    private static ScenarioSpec oneShip() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Atomic fixture";
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
}
