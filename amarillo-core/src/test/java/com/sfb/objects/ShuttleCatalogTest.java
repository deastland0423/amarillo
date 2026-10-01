package com.sfb.objects;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import com.sfb.objects.shuttles.CataloguedFighter;

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

    /**
     * Every catalogued type builds to a craft that REPORTS that same type.
     * <p>
     * This used to demand a distinct Java CLASS per type, and it earned its keep: when the TAAS
     * was added, case "haas" was given {@code CataloguedFighter.of("taas")} by copy-paste, so every Highly Advanced
     * Attack Shuttle in the game was quietly a Tactically Advanced one while "taas" had no case at
     * all and fell through to an admin shuttle. Two fighters wrong, found only because their BPVs
     * were 8 and 9 (2026-09-27).
     * <p>
     * A fighter is a catalogue ROW now, so they all share one class and distinctness is no test of
     * anything. Identity is: a built craft carries the catalogue key it was built from. That is
     * strictly stronger — it would have caught the original bug too, since a "haas" row building a
     * Fighter would have reported "taas" — and it is the same key the DTO, the ready racks and the
     * fighter lines all identify a craft by.
     */
    @Test
    public void everyTypeBuildsToSomethingThatKnowsWhatItIs() {
        java.util.List<String> wrong = new java.util.ArrayList<>();

        for (com.sfb.objects.ShuttleCatalog.Entry e : com.sfb.objects.ShuttleCatalog.all()) {
            com.sfb.objects.shuttles.Shuttle built =
                    com.sfb.systemgroups.ShuttleBay.buildShuttle(e.type, e.type);
            if (!e.type.equalsIgnoreCase(built.getCatalogType()))
                wrong.add(e.type + " built a craft reporting '" + built.getCatalogType()
                        + "' (a " + built.getClass().getSimpleName() + ")");
        }

        assertTrue("a catalogued type must build to a craft that knows it is that type: "
                + String.join(" | ", wrong), wrong.isEmpty());
    }

    /**
     * And the non-fighter craft keep a class each, since they hold real behaviour.
     * <p>
     * Named so the collapse of the fighter classes cannot quietly take these with it: an
     * administrative shuttle, a GAS and an HTS are not fighters, and the three role craft —
     * scatter pack, suicide shuttle, wild weasel — are seekers.
     */
    @Test
    public void theNonFighterCraftStillHaveClassesOfTheirOwn() {
        assertEquals("AdminShuttle",
                com.sfb.systemgroups.ShuttleBay.buildShuttle("admin", "a")
                        .getClass().getSimpleName());
        assertEquals("GASShuttle",
                com.sfb.systemgroups.ShuttleBay.buildShuttle("gas", "g")
                        .getClass().getSimpleName());
        assertEquals("HTSShuttle",
                com.sfb.systemgroups.ShuttleBay.buildShuttle("hts", "h")
                        .getClass().getSimpleName());
    }

    // -------------------------------------------------------------------------
    // What a craft is CALLED
    // -------------------------------------------------------------------------

    /**
     * The hangar panel and the map counter must call one fighter one thing.
     * <p>
     * They did not: LaunchCoordinator read the catalogue, while the bay used a hardcoded
     * switch in Shuttles that predated the Kzinti fighters. A craft sat in its box as
     * "Aas-1" and launched as "AAS-7" — the same fighter, a case apart.
     */
    @Test
    public void aBayNamesACraftByTheSameCatalogueTheLaunchDoes() throws Exception {
        Ship cv = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cv.json")));

        boolean sawFighter = false;
        for (com.sfb.objects.shuttles.Shuttle s : cv.getShuttles().getAllShuttles()) {
            String designation = s.getName().replaceAll("-[0-9]+$", "");
            if (!"AAS".equals(designation))
                continue;
            sawFighter = true;
        }
        assertTrue("a CV's fighters should be named AAS-n, not Fighter-n", sawFighter);
    }

    @Test
    public void everyCataloguedTypeCarriesADesignation() {
        for (ShuttleCatalog.Entry e : ShuttleCatalog.all()) {
            assertNotNull(e.type, e.designation);
            assertFalse("a blank designation would name a craft nothing",
                    e.designation.isBlank());
            assertFalse("an underscore is a JSON key showing through, not a designation: "
                    + e.type + " -> " + e.designation, e.designation.contains("_"));
        }
    }

    /**
     * The acronym types are the ones the old switch got wrong, so they are the ones pinned.
     * A designation is what the SSD prints, and the SSD does not print "Haas_e".
     */
    @Test
    public void theAcronymFightersAreNotTitleCased() {
        assertEquals("AAS", ShuttleCatalog.get("aas").designation);
        assertEquals("HAAS", ShuttleCatalog.get("haas").designation);
        assertEquals("HAAS-E", ShuttleCatalog.get("haas_e").designation);
        assertEquals("TAAS", ShuttleCatalog.get("taas").designation);
    }

    /**
     * Designation and shortName are different jobs and are ALLOWED to differ — a map counter
     * has a few pixels, a hangar row has a line. They need not: "AAS" is already as short as
     * it goes, and forcing a difference would be inventing one.
     * <p>
     * The exact spelling of any one of them is the ship owner's taste and deliberately not
     * pinned here; an earlier version of this test asserted "Stinger-1" and broke the moment
     * he preferred "Stinger1", which is a test failing for an opinion rather than a defect.
     * What IS pinned is that both resolve to something printable.
     */
    @Test
    public void everyEntryHasBothLabelsAndNeitherIsBlank() {
        for (ShuttleCatalog.Entry e : ShuttleCatalog.all()) {
            assertFalse(e.type + " has no counter label", e.shortName.isBlank());
            assertFalse(e.type + " has no designation", e.designation.isBlank());
        }
    }

    /**
     * The fallback chain, which is the part that is logic rather than taste: designation, then
     * the counter label, then the display name. An entry that says nothing still names its
     * craft rather than naming it null.
     */
    @Test
    public void designationFallsBackThroughShortNameToTheDisplayName() {
        assertEquals("said outright, so used outright", "Flown Thing",
                entry("Full Name", "Short", "Flown Thing").designation);
        assertEquals("nothing said, so the counter label stands in", "Short",
                entry("Full Name", "Short", null).designation);
        assertEquals("blank counts as nothing said", "Short",
                entry("Full Name", "Short", "  ").designation);
        assertEquals("neither said, so the display name stands in", "Full Name",
                entry("Full Name", null, null).designation);
    }

    /** A catalogue entry with only the three fields this test cares about. */
    private static ShuttleCatalog.Entry entry(String name, String shortName,
            String designation) {
        return new ShuttleCatalog.Entry("probe", name, "shuttle", java.util.List.of("any"),
                0, 0, 0, 0, 0, false, false, 0, shortName, designation, null);
    }
}
