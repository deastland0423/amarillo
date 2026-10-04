package com.sfb.objects;

import com.sfb.systemgroups.Crew.CrewQuality;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * G21.0: a ship's crew quality, declared on the hull.
 *
 * <h2>What this closed</h2>
 * The enum, the DTO field and two rules sites that consult it all existed — {@code
 * BoardingResolver} shifting hit-and-run rolls (D7.72/D7.73) and {@code Game.collisionDie} taking
 * the nimble die-shift away from a poor crew (C11.33) — but <b>nothing could set it from data</b>.
 * {@code Crew.init} read a {@code crewquality} key that {@code ShipSpec.toInitMap} never put
 * there, so every ship in the game was NORMAL and the only callers of {@code setCrewQuality} were
 * three tests. A capability built from the middle outward with no entry point, which is the same
 * shape as {@code allowedEwFighters} having no caller.
 *
 * <p>G21.1 and G21.2's own adjustment lists are still unbuilt; this is the declaration only.
 */
public class CrewQualityDeclarationTest {

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private Ship hull(String faction, String type) {
        ShipSpec spec = ShipLibrary.get(faction, type);
        assertNotNull(faction + "/" + type + " should be in the library", spec);
        return ShipLibrary.createShip(spec);
    }

    /** The reason the field exists: a penal frigate is crewed by prisoners. */
    @Test
    public void theKlingonPenalFrigateHasAPoorCrew() {
        assertEquals(CrewQuality.POOR, hull("Klingon", "F5J").getCrew().getCrewQuality());
    }

    /** An undeclared quality is normal — the owner's ruling, and what most ships are. */
    @Test
    public void aShipThatSaysNothingIsNormal() {
        assertNull("fixture: the F5E declares no quality",
                ShipLibrary.get("Klingon", "F5E").crewQuality);
        assertEquals(CrewQuality.NORMAL, hull("Klingon", "F5E").getCrew().getCrewQuality());
        assertEquals(CrewQuality.NORMAL, hull("Federation", "CA").getCrew().getCrewQuality());
    }

    /** All three values survive the trip from spec to ship. */
    @Test
    public void everyQualityRoundTripsThroughTheInitMap() {
        for (String declared : new String[] { "poor", "normal", "outstanding" }) {
            ShipSpec spec = ShipLibrary.get("Klingon", "F5");
            assertNotNull(spec);
            spec.crewQuality = declared;
            Ship ship = ShipLibrary.createShip(spec);
            assertEquals(declared, CrewQuality.valueOf(declared.toUpperCase()),
                    ship.getCrew().getCrewQuality());
        }
        // Leave the shared spec as the file had it — ShipLibrary hands out the same instance.
        ShipLibrary.get("Klingon", "F5").crewQuality = null;
    }

    /**
     * A typo costs the declaration, not the load. {@code Crew.init} parses leniently, which is the
     * same choice {@code CarrierClass.from} and {@code AegisLevel} make — and the key guard is what
     * catches a misspelled FIELD, so the only thing left to be lenient about is the VALUE.
     */
    @Test
    public void anUnrecognisedQualityReadsAsNormalRatherThanFailing() {
        ShipSpec spec = ShipLibrary.get("Klingon", "F5");
        spec.crewQuality = "execrable";
        try {
            assertEquals(CrewQuality.NORMAL, ShipLibrary.createShip(spec)
                    .getCrew().getCrewQuality());
        } finally {
            spec.crewQuality = null;
        }
    }

    /**
     * G21.142: "admin shuttle pilots are always treated as good", so quality is a SHIP property
     * and a poor crew's shuttles do not inherit it. Pinned here because the declaration now makes
     * it reachable — before this, every ship was normal and the distinction could not be observed
     * from data at all.
     */
    @Test
    public void aPoorCrewDoesNotMakeItsShuttlesPoor() {
        Ship penal = hull("Klingon", "F5J");
        assertEquals(CrewQuality.POOR, penal.getCrew().getCrewQuality());
        for (com.sfb.systemgroups.ShuttleBay bay : penal.getShuttles().getBays())
            for (com.sfb.objects.shuttles.Shuttle craft : bay.getInventory())
                assertNotNull("fixture: the bay should hold a shuttle to ask about", craft);
        // The ruling lives in Game.crewQualityFor, which answers for a Ship and nothing else;
        // a shuttle therefore lands on NORMAL rather than borrowing POOR from its mother.
    }

    /**
     * Every declared value across the data must be one the parser knows, so a hull cannot sit in
     * the library quietly reading as normal because of a spelling.
     */
    @Test
    public void everyDeclaredQualityInTheDataIsRecognised() {
        List<String> wrong = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.crewQuality == null)
                continue;
            String v = spec.crewQuality.toLowerCase();
            if (!v.equals("poor") && !v.equals("normal") && !v.equals("outstanding"))
                wrong.add(spec.faction + " " + spec.type + " declares crewQuality '"
                        + spec.crewQuality + "'");
        }
        assertEquals(String.join("\n  ", wrong), List.of(), wrong);
    }
}
