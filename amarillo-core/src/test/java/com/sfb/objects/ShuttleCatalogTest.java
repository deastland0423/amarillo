package com.sfb.objects;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * The catalogue and the Java classes describe the same things and must not drift. The BPVs of
 * the three Kzinti attack shuttles once disagreed with their own documentation for long enough
 * that nobody noticed; these tests turn that class of mistake into a build failure.
 * <p>
 * The important one is {@link #everyCatalogedTypeBuildsToItself()}: ShuttleBay's factory falls
 * back to an admin shuttle for a key it does not know, so a new fighter whose case was never
 * added would silently launch as a shuttle. Building each catalogued type and checking what
 * comes back catches exactly that.
 */
public class ShuttleCatalogTest {

    @Before
    public void loadCatalogue() throws Exception {
        File f = new File("../data/shuttles/shuttles.json");
        assumeTrue("shuttles.json must exist", f.exists());
        ShuttleCatalog.load(f);
    }

    @Test
    public void theCatalogueLoads() {
        assertTrue(ShuttleCatalog.isLoaded());
        assertFalse("it is not empty", ShuttleCatalog.all().isEmpty());
    }

    /**
     * Every catalogued type must build into something with the stats the catalogue publishes.
     * A missing factory case shows up here as an admin shuttle wearing the wrong numbers.
     */
    @Test
    public void everyCatalogedTypeBuildsToItself() {
        for (ShuttleCatalog.Entry e : ShuttleCatalog.all()) {
            Shuttle built = ShuttleBay.buildShuttle(e.type, "test-" + e.type);
            assertNotNull(e.type + " built nothing", built);

            assertEquals(e.type + ": hull", e.hull, built.getHull());
            assertEquals(e.type + ": speed", e.speed, built.getMaxSpeed());
            assertEquals(e.type + ": crippled threshold", e.crippled, built.getCrippledHull());

            if (e.isFighter()) {
                assertTrue(e.type + " is catalogued as a fighter but did not build one",
                        built instanceof Fighter);
                assertEquals(e.type + ": bpv", e.bpv, ((Fighter) built).getBpv());
            } else {
                assertFalse(e.type + " is catalogued as a plain shuttle but built a fighter",
                        built instanceof Fighter);
            }
        }
    }

    /** A fighter costs BPV; a plain shuttle rides along free. */
    @Test
    public void onlyFightersCarryAPrice() {
        for (ShuttleCatalog.Entry e : ShuttleCatalog.all()) {
            if (e.isFighter())
                assertTrue(e.type + " should cost something", e.bpv > 0);
            else
                assertEquals(e.type + " should be free", 0, e.bpv);
        }
    }

    /** The year filter is what makes a fleet legal for its era. */
    @Test
    public void availabilityFiltersByFactionAndYear() {
        ShuttleCatalog.Entry stinger2 = ShuttleCatalog.get("stinger2");
        assertNotNull(stinger2);

        assertTrue("Hydran, in service", stinger2.availableTo("hydran") && stinger2.availableIn(175));
        assertFalse("not before its year", stinger2.availableIn(169));
        assertFalse("and not Kzinti", stinger2.availableTo("kzinti"));

        ShuttleCatalog.Entry admin = ShuttleCatalog.get("admin");
        assertTrue("admin shuttles are open to everyone", admin.availableTo("tholian"));
        assertTrue(admin.availableTo("hydran"));
    }

    /** The picker's list: what this faction may field this year, and nothing else. */
    @Test
    public void availableForGivesAFactionItsOwnPlusTheCommonTypes() {
        var hydran = ShuttleCatalog.availableFor("hydran", 175);
        var types = hydran.stream().map(e -> e.type).toList();

        assertTrue(types.toString(), types.contains("stinger2"));
        assertTrue("common types are available to all", types.contains("admin"));
        assertFalse("another faction's fighters are not", types.contains("aas"));

        var early = ShuttleCatalog.availableFor("hydran", 140);
        assertTrue("Stinger-1 is in service by 140",
                early.stream().anyMatch(e -> e.type.equals("stinger1")));
        assertFalse("the Stinger-2 is not",
                early.stream().anyMatch(e -> e.type.equals("stinger2")));
    }

    /** Fighter BPV is what a carrier's fighters add to its price — the fleet-builder question. */
    @Test
    public void bpvOfPricesAFightersComplement() {
        assertEquals("a Hydran LN+ carries four Stinger-2s", 40, 4 * ShuttleCatalog.bpvOf("stinger2"));
        assertEquals("and its admin shuttle is free", 0, ShuttleCatalog.bpvOf("admin"));
        assertEquals("an uncatalogued key costs nothing rather than throwing",
                0, ShuttleCatalog.bpvOf("no-such-type"));
    }
}
