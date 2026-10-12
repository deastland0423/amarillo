package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.Shuttle;
import com.sfb.scenario.ScenarioLoader;

/**
 * J4.6: a declared fighter position with no fighter available holds an ADMIN SHUTTLE.
 *
 * <p>The Klingon diplomatic cruisers, D7N and D5N, each carry a single fighter, and their sheets
 * say it "would be the best available fighter for the year of the scenario". The Zegurnii line
 * begins in Y167, so before then there is no best available fighter — and owner's ruling,
 * 2026-10-11: "If there is no fighter available, that shuttle box will instead contain an admin
 * shuttle." The box is not therefore empty.
 *
 * <p>What made this worth a test rather than a one-line fix is that the wrong answer depended on
 * which hull you asked. A ship is BUILT from its own file, which knows only its service year, so
 * the D7N (Y137) was built with no fighter and the D5N (Y171) was built with a Z-2. The old
 * fallback kept "the complement it was built with", so a Y137 scenario gave the D7N three shuttles
 * and no fourth box, and gave the D5N a Z-2 that would not exist for another thirty years. Two
 * different wrong answers from one line of code, neither visible unless you built both hulls.
 */
public class DiplomaticFighterByYearTest {

    /** The year the Zegurnii line begins, per data/shuttles/klingon.json. */
    private static final int LINE_BEGINS = 167;

    @Before
    public void load() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    /** Build a hull as a scenario of that year would. */
    private Ship build(String type, int year) {
        ShipSpec spec = ShipLibrary.get("Klingon", type);
        assertNotNull("fixture: " + type + " should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, "Klingon", year, spec);
        return ship;
    }

    private List<String> craftAboard(Ship ship) {
        List<String> types = new ArrayList<>();
        for (Shuttle s : ship.getShuttles().getAllShuttles())
            types.add(s.getCatalogType());
        return types;
    }

    private long fightersAboard(Ship ship) {
        return craftAboard(ship).stream().filter(t -> !"admin".equals(t)).count();
    }

    @Test
    public void beforeTheLineBeginsTheFighterBoxHoldsAnAdminShuttle() {
        for (String type : new String[] {"D7N", "D5N"}) {
            Ship ship = build(type, LINE_BEGINS - 1);
            List<String> aboard = craftAboard(ship);

            assertEquals(type + " should carry no fighter in Y" + (LINE_BEGINS - 1)
                    + ", but had " + aboard, 0, fightersAboard(ship));
            assertEquals(type + " should still fill the box with a shuttle: " + aboard,
                    4, aboard.size());
        }
    }

    @Test
    public void onceTheLineBeginsTheFighterArrives() {
        for (String type : new String[] {"D7N", "D5N"}) {
            Ship ship = build(type, LINE_BEGINS);
            List<String> aboard = craftAboard(ship);

            assertEquals(type + " should carry exactly one fighter: " + aboard,
                    1, fightersAboard(ship));
            assertEquals(type + " keeps its three admin shuttles too: " + aboard,
                    4, aboard.size());
        }
    }

    /**
     * The regression that matters: the two hulls must agree, because the thing that used to
     * separate them — their own service years, thirty-four years apart — is not something the
     * scenario's year should be able to see.
     */
    @Test
    public void theAnswerDoesNotDependOnTheHullsOwnServiceYear() {
        for (int year : new int[] {137, 160, 166, 167, 172, 183}) {
            long d7n = fightersAboard(build("D7N", year));
            long d5n = fightersAboard(build("D5N", year));

            assertEquals("D7N (service year 137) and D5N (service year 171) should carry the "
                    + "same number of fighters in Y" + year, d7n, d5n);
            assertEquals("and in Y" + year + " that number should be "
                    + (year >= LINE_BEGINS ? 1 : 0),
                    year >= LINE_BEGINS ? 1 : 0, d7n);
        }
    }

    /** A later year seats a later model: the line is resolved, not frozen at first availability. */
    @Test
    public void aLateScenarioSeatsALateModel() {
        assertTrue("Y183 should seat the Z-YC",
                craftAboard(build("D5N", 183)).contains("zyc"));
        assertFalse("and not the Z-2 the line starts with",
                craftAboard(build("D5N", 183)).contains("z2"));
    }
}
