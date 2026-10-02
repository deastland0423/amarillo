package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;

/**
 * J4.4: which fighter a carrier flies is decided by the YEAR, not by the ship file.
 * <p>
 * A Kzinti CVS entered service in Y170 with AAS aboard and re-equipped with the HAAS in Y173,
 * the TAAS in Y177, the TADS in Y180 and the TADSC in Y183 — none of which changes its SSD. The
 * ship declares how many of each ROLE it carries and the line supplies the types.
 * <p>
 * The rule that makes one declaration cover every era is the fallback: a role the era has no
 * type for takes the standard fighter. That is how the Hydran RN carries nine Stinger-1s before
 * any EW fighter existed and six Stinger-2s, two Stinger-Hs and one Stinger-E afterwards, from
 * the same six/two/one declaration.
 */
public class FighterLineTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    /**
     * A complement whose middle count is the line's HEAVY-WEAPON role.
     * <p>
     * Named by role rather than hard-coded, because the two are not interchangeable: the Kzinti
     * and Hydran lines put their heavy-weapon fighters (DAS disruptors, Stinger-H hellbores) in
     * {@code assault}, while the Klingon Z-1 — a phaser-2 and drones, no heavy weapon — stays in
     * {@code attack}. Passing the wrong one does not fail loudly; those slots quietly fall back
     * to the superiority fighter, which is exactly what this helper hid when it said "attack"
     * for every line.
     */
    private FighterComplement complement(String line, int superiority, String role, int roleCount,
            int ew) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("superiority", superiority);
        counts.put(role, roleCount);
        counts.put("ew", ew);
        return new FighterComplement(line, counts);
    }

    /** The Kzinti and Hydran lines: their heavy-weapon fighters are assault. */
    private FighterComplement complement(String line, int superiority, int assault, int ew) {
        return complement(line, superiority, "assault", assault, ew);
    }

    /** Types grouped and counted, so order within a role does not matter to the assertion. */
    private Map<String, Integer> tally(List<String> types) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String t : types)
            out.merge(t, 1, Integer::sum);
        return out;
    }

    // ---------------------------------------------------------------- the eras

    @Test
    public void theKzintiLineReEquipsFiveTimes() {
        FighterComplement cvs = complement("kzinti-attack", 11, 0, 1);

        assertEquals(Map.of("aas", 11, "aas_e", 1), tally(cvs.typesFor(170)));
        assertEquals(Map.of("haas", 11, "haas_e", 1), tally(cvs.typesFor(173)));
        assertEquals(Map.of("taas", 11, "taas_e", 1), tally(cvs.typesFor(177)));
        assertEquals(Map.of("tads", 11, "tads_e", 1), tally(cvs.typesFor(180)));
        assertEquals(Map.of("tadsc", 11, "tadsc_e", 1), tally(cvs.typesFor(183)));
    }

    @Test
    public void anEraHoldsUntilTheNextOneArrives() {
        FighterComplement cvs = complement("kzinti-attack", 11, 0, 1);
        assertEquals("Y176 is still the HAAS era", Map.of("haas", 11, "haas_e", 1),
                tally(cvs.typesFor(176)));
        assertEquals("and Y182 still the TADS", Map.of("tads", 11, "tads_e", 1),
                tally(cvs.typesFor(182)));
    }

    /** This is the CVS bug the lines exist to fix: at its own service year it flew AAS. */
    @Test
    public void theCvsFliesAasInTheYearItEnteredService() {
        assertEquals(Map.of("aas", 11, "aas_e", 1),
                tally(complement("kzinti-attack", 11, 0, 1).typesFor(170)));
    }

    @Test
    public void nothingIsAvailableBeforeTheLineBegins() {
        assertTrue("the Kzinti line starts in Y164",
                complement("kzinti-attack", 11, 0, 1).typesFor(150).isEmpty());
    }

    // ---------------------------------------------------------------- the fallback

    /**
     * The Hydran RN, from ONE declaration. In Y170 the line has all three roles; in Y134 only
     * the Stinger-1 existed, so the attack and EW slots fall back to it.
     */
    @Test
    public void rolesWithNoTypeThisEraFallBackToTheStandardFighter() {
        FighterComplement rn = complement("hydran-stinger", 6, 2, 1);

        assertEquals("Y170: the printed RN+ complement",
                Map.of("stinger2", 6, "stingerh", 2, "stinger_e", 1), tally(rn.typesFor(170)));
        assertEquals("Y134: nine Stinger-1s, no EW fighter yet",
                Map.of("stinger1", 9), tally(rn.typesFor(134)));
    }

    @Test
    public void theEraSaysWhetherARoleIsItsOwnOrABorrowedStandard() {
        ShuttleCatalog.LineEra early = ShuttleCatalog.eraFor("hydran-stinger", 134);
        assertFalse("Y134 has no EW fighter of its own", early.hasOwnTypeFor("ew"));
        assertEquals("so the EW slot borrows the standard", "stinger1", early.typeFor("ew"));

        ShuttleCatalog.LineEra late = ShuttleCatalog.eraFor("hydran-stinger", 170);
        assertTrue(late.hasOwnTypeFor("ew"));
        assertEquals("stinger_e", late.typeFor("ew"));
    }

    /**
     * A carrier with spaces dedicated to attack fighters, in a year before any attack fighter
     * exists, flies STANDARD fighters in them.
     * <p>
     * The Kzinti line is the live case: the DAS arrives in Y172, so a carrier declaring two
     * attack slots has two AAS in them in Y170 and two DAS from Y172 — without the ship file
     * knowing anything about either date. Before the DAS this was only testable hypothetically.
     */
    /** The attack role changes hands in Y183: the DASC takes over from the DAS. */
    @Test
    public void theAttackRoleAdvancesOnItsOwnSchedule() {
        FighterComplement cv = complement("kzinti-attack", 9, 2, 1);

        assertEquals("Y180: DAS still, with TADS standards",
                Map.of("tads", 9, "das", 2, "tads_e", 1), tally(cv.typesFor(180)));
        assertEquals("Y183: DASC, and the standards become TADSC",
                Map.of("tadsc", 9, "dasc", 2, "tadsc_e", 1), tally(cv.typesFor(183)));
    }

    @Test
    public void attackSlotsFlyStandardFightersUntilAnAttackFighterExists() {
        FighterComplement cv = complement("kzinti-attack", 9, 2, 1);

        assertEquals("Y170: no DAS yet, so the attack slots are AAS too",
                Map.of("aas", 11, "aas_e", 1), tally(cv.typesFor(170)));
        assertEquals("Y172: the DAS arrives and fills them",
                Map.of("aas", 9, "das", 2, "aas_e", 1), tally(cv.typesFor(172)));
        assertEquals("Y173: the standards become HAAS, the attack slots stay DAS",
                Map.of("haas", 9, "das", 2, "haas_e", 1), tally(cv.typesFor(173)));

        // The count never changes, whatever the era supplies.
        for (int year : new int[] { 170, 172, 173, 177, 180, 183 })
            assertEquals("twelve fighters in Y" + year, 12, cv.typesFor(year).size());
    }

    @Test
    public void aLineWithNoAttackRoleGivesStandardsInstead() {
        // The Kzinti line has no attack fighter in any era; asking for one yields the standard.
        assertEquals(Map.of("aas", 12), tally(complement("kzinti-attack", 10, 2, 0).typesFor(170)));
    }

    // ---------------------------------------------------------------- lookups and edges

    @Test
    public void anUnknownLineResolvesToNothingRatherThanThrowing() {
        assertNull(ShuttleCatalog.eraFor("klingon-nonexistent", 180));
        assertTrue(complement("klingon-nonexistent", 6, 0, 1).typesFor(180).isEmpty());
        assertNull(ShuttleCatalog.fighterFor("klingon-nonexistent", "superiority", 180));
    }

    /**
     * Year 0 means "no scenario year given", which happens for a ship built straight from the
     * library. The earliest era is a better answer than an empty bay.
     */
    @Test
    public void anUnspecifiedYearTakesTheEarliestEra() {
        assertEquals(Map.of("aas", 11, "aas_e", 1),
                tally(complement("kzinti-attack", 11, 0, 1).typesFor(0)));
    }

    /**
     * Both lines exist and have eras. Deliberately NOT an exact era count: this test pinned the
     * Kzinti line at five and broke the moment a Y172 era was added for the DAS, which is a line
     * growing as intended rather than a regression. What matters is that each line is populated
     * and that the eras below resolve to the right fighters.
     */
    @Test
    public void bothLinesAreCatalogued() {
        assertTrue(ShuttleCatalog.lineNames().containsAll(
                List.of("kzinti-attack", "hydran-stinger")));
        assertFalse(ShuttleCatalog.lineEras("kzinti-attack").isEmpty());
        assertFalse(ShuttleCatalog.lineEras("hydran-stinger").isEmpty());
    }

    @Test
    public void erasAreSortedByYearWhateverOrderTheFileUses() {
        List<ShuttleCatalog.LineEra> eras = ShuttleCatalog.lineEras("kzinti-attack");
        for (int i = 1; i < eras.size(); i++)
            assertTrue("era " + i + " must come after era " + (i - 1),
                    eras.get(i).from > eras.get(i - 1).from);
    }

    @Test
    public void everyTypeNamedByEveryLineIsACataloguedFighter() {
        for (String line : ShuttleCatalog.lineNames())
            for (ShuttleCatalog.LineEra era : ShuttleCatalog.lineEras(line))
                for (Map.Entry<String, String> role : era.roles().entrySet()) {
                    ShuttleCatalog.Entry e = ShuttleCatalog.get(role.getValue());
                    assertNotNull(line + " " + era + ": '" + role.getValue()
                            + "' is not in the catalogue", e);
                    assertEquals(line + " " + era + ": '" + role.getValue()
                            + "' must be a fighter", "fighter", e.kind);
                }
    }

    /**
     * A role, once a line has one, must appear in every later era.
     * <p>
     * This is the hazard of writing eras as whole rows: each row repeats every role, so adding a
     * new standard fighter means remembering to carry the attack and EW entries forward into it.
     * Forget one and that era's attack slots silently fall back to standard fighters — a wrong
     * complement with no error anywhere, because falling back is legitimate behaviour that
     * cannot tell an omission from an intention.
     * <p>
     * `attack: das` is currently repeated across four eras and `dasc` takes over in the fifth, so
     * there are already five chances to drop it. If a role ever genuinely should lapse, this test
     * is the place to say so out loud.
     */
    @Test
    public void noEraDropsARoleAnEarlierOneHad() {
        for (String line : ShuttleCatalog.lineNames()) {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (ShuttleCatalog.LineEra era : ShuttleCatalog.lineEras(line)) {
                for (String role : seen)
                    assertTrue(line + " era from Y" + era.from + " is missing the '" + role
                                    + "' role an earlier era had — its slots would quietly"
                                    + " fall back to standard fighters. Era is " + era,
                            era.hasOwnTypeFor(role));
                seen.addAll(era.roles().keySet());
            }
        }
    }

    /**
     * Each era must begin no earlier than the fighters it names became available.
     * <p>
     * A type MISSING from the catalogue is skipped rather than dereferenced, and that is not
     * laziness: {@link #everyTypeNamedByEveryLineIsACataloguedFighter} already owns "does it
     * exist at all" and names the offender. This test used to read {@code e.year} off a null
     * and die with "Cannot read field year because e is null", which told the author nothing
     * and buried the useful failure from the other test under a crash. One problem, one
     * message.
     */
    @Test
    public void noEraNamesAFighterThatDidNotExistYet() {
        for (String line : ShuttleCatalog.lineNames())
            for (ShuttleCatalog.LineEra era : ShuttleCatalog.lineEras(line))
                for (Map.Entry<String, String> role : era.roles().entrySet()) {
                    ShuttleCatalog.Entry e = ShuttleCatalog.get(role.getValue());
                    if (e == null)
                        continue;
                    assertTrue(line + " starts at Y" + era.from + " but " + role.getValue()
                            + " is Y" + e.year, e.year <= era.from);
                }
    }

    // ---------------------------------------------------------------- seating in a bay

    private ShuttleBay bayWithAdminShuttles(int admins, int fighterSpaces) {
        ShuttleBay bay = new ShuttleBay(null);
        for (int i = 0; i < admins; i++)
            bay.addShuttle(ShuttleBay.buildShuttle("admin", "Admin-" + (i + 1)), 0);
        for (int i = 0; i < fighterSpaces; i++)
            bay.addEmptySpace();
        return bay;
    }

    private List<String> fighterClassNames(ShuttleBay bay) {
        return bay.getInventory().stream()
                .filter(s -> s instanceof Fighter)
                .map(Shuttle::getCatalogType)
                .collect(Collectors.toList());
    }

    @Test
    public void seatingFillsTheBayAndLeavesAdminShuttlesAlone() {
        ShuttleBay bay = bayWithAdminShuttles(3, 12);
        complement("kzinti-attack", 11, 0, 1).applyTo(bay, 173, "");

        assertEquals("bay size is unchanged", 15, bay.getTotalSpaces());
        List<String> fighters = fighterClassNames(bay);
        assertEquals(12, fighters.size());
        assertEquals(11, fighters.stream().filter("haas"::equals).count());
        assertEquals(1, fighters.stream().filter("haas_e"::equals).count());

        long admins = bay.getInventory().stream()
                .filter(s -> !(s instanceof Fighter)).count();
        assertEquals("the three admin shuttles survived", 3, admins);
    }

    /**
     * Re-seating is what the scenario year does to a ship already built from its own file, so it
     * must replace rather than stack — this ran twice for every carrier in a scenario.
     */
    @Test
    public void reSeatingReplacesRatherThanStacks() {
        ShuttleBay bay = bayWithAdminShuttles(3, 12);
        FighterComplement cvs = complement("kzinti-attack", 11, 0, 1);

        cvs.applyTo(bay, 170, "");    // as built: AAS
        cvs.applyTo(bay, 183, "");    // scenario year: TADSC

        assertEquals("still 15 spaces, not 27", 15, bay.getTotalSpaces());
        List<String> fighters = fighterClassNames(bay);
        assertEquals(12, fighters.size());
        assertEquals("no AAS left behind", 0, fighters.stream().filter("aas"::equals).count());
        assertEquals(11, fighters.stream().filter("tadsc"::equals).count());
        assertEquals(1, fighters.stream().filter("tadsc_e"::equals).count());
    }

    @Test
    public void seatedFightersAreNamedFromTheirDesignation() {
        ShuttleBay bay = bayWithAdminShuttles(0, 3);
        complement("kzinti-attack", 2, 0, 1).applyTo(bay, 177, "KHS Watchful ");

        List<String> names = new ArrayList<>();
        for (Shuttle s : bay.getInventory())
            if (s instanceof Fighter)
                names.add(s.getName());
        assertTrue(names.toString(), names.contains("KHS Watchful TAAS-1"));
        assertTrue(names.toString(), names.contains("KHS Watchful TAAS-2"));
        assertTrue(names.toString(), names.contains("KHS Watchful TAAS-E-1"));
    }

    /** A complement the line cannot resolve must leave the bay untouched, not empty it. */
    @Test
    public void anUnresolvableComplementChangesNothing() {
        ShuttleBay bay = bayWithAdminShuttles(2, 2);
        complement("kzinti-attack", 2, 0, 0).applyTo(bay, 170, "");
        assertEquals(2, fighterClassNames(bay).size());

        complement("kzinti-attack", 2, 0, 0).applyTo(bay, 150, "");  // before the line begins
        assertEquals("the Y170 fighters are still aboard", 2, fighterClassNames(bay).size());
    }

    @Test
    public void theComplementReportsItsOwnShape() {
        FighterComplement rn = complement("hydran-stinger", 6, 2, 1);
        assertEquals(9, rn.total());
        assertEquals(1, rn.ewCount());
        assertEquals(6, rn.countOf("superiority"));
        assertEquals("the Stinger-H is hellbore-armed, so the RN+ declares it as ASSAULT",
                2, rn.countOf("assault"));
        assertEquals("and carries no plain attack fighters", 0, rn.countOf("attack"));
        assertEquals(0, rn.countOf("nonexistent-role"));
    }
}
