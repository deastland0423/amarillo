package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;

/**
 * Every ship file still loads, and every fighter aboard still builds.
 * <p>
 * Written to rule the fighter-row change in or out of a 403 seen in play. A fighter is a catalogue
 * row now, so a ship's bay is filled by {@link ShuttleCatalog} rather than by a Java class — and if
 * a row were malformed, or the catalogue were not found from the server's working directory, ships
 * could come out of {@code loadShips} short of their complement or not at all. Either would show
 * up here rather than as a mystery in the lobby.
 */
public class EveryShipStillLoadsTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    /** Every ship in the library builds, keeps its name, and ends up with no empty-typed craft. */
    @Test
    public void everyShipInTheLibraryBuilds() {
        List<String> problems = new ArrayList<>();
        int ships = 0, fighters = 0;

        for (File faction : new File("../data/factions").listFiles(File::isDirectory)) {
            File[] files = faction.listFiles(f -> f.getName().endsWith(".json"));
            if (files == null)
                continue;
            for (File file : files) {
                Ship ship;
                try {
                    ship = ShipLibrary.createShip(ShipSpec.fromJson(file));
                } catch (Exception e) {
                    problems.add(file.getName() + " failed to build: " + e);
                    continue;
                }
                ships++;
                ship.setName("Test " + file.getName());

                for (Shuttle craft : ship.getShuttles().getAllShuttles()) {
                    if (craft.getCatalogType() == null || craft.getCatalogType().isBlank())
                        problems.add(file.getName() + ": a "
                                + craft.getClass().getSimpleName() + " with no catalogue type");
                    if (craft instanceof Fighter f) {
                        fighters++;
                        if (f.getWeapons().fetchAllWeapons().isEmpty())
                            problems.add(file.getName() + ": " + f.getCatalogType()
                                    + " built with no weapons at all");
                        if (f.getBpv() <= 0)
                            problems.add(file.getName() + ": " + f.getCatalogType()
                                    + " built with no BPV");
                    }
                }
            }
        }

        assertTrue("ship files should have loaded", ships > 50);
        assertTrue("and fighters should be aboard some of them", fighters > 50);
        assertTrue(problems.size() + " problems:\n  " + String.join("\n  ", problems),
                problems.isEmpty());
    }

    /**
     * The catalogue must be findable from either working directory the app runs in — the repo root
     * (the server) or a module directory (a test). ShuttleCatalog falls back to loading itself from
     * a default path, and if that missed, every fighter would silently become an admin shuttle.
     */
    @Test
    public void theCatalogueIsFoundFromEitherWorkingDirectory() {
        assertTrue("from a module directory", new File("../data/shuttles/shuttles.json").exists());
        assertTrue("from the repo root", new File("data/shuttles/shuttles.json").exists()
                || new File("../data/shuttles/shuttles.json").exists());
    }
}
