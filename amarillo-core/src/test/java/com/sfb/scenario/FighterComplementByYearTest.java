package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;

/**
 * A carrier re-equips with the year, end to end through {@link ScenarioLoader} — plus a guard on
 * the ship data itself, because the error this replaces was a hand-authored one.
 * <p>
 * {@code cvs.json} declared HAAS fighters against a service year of Y170, three years before the
 * HAAS existed. Nothing caught it: a complement listed literally is right for at most one era,
 * and no test compared the two dates. Both halves are covered here — the resolution that makes
 * the complement follow the year, and the guard that fails the build if a literal list ever
 * names a fighter from the future again.
 */
public class FighterComplementByYearTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    // ---------------------------------------------------------------- end to end

    private Ship carrier(String faction, String type, int year) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.mapCols = 42;
        spec.mapRows = 32;
        spec.year = year;
        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = faction;
        side.name = faction;
        side.ships = new ArrayList<>();
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.type = type;
        setup.shipName = "Test " + type;
        setup.startHex = "1016";
        setup.startHeading = "C";
        side.ships.add(setup);
        spec.sides = new ArrayList<>(List.of(side));

        for (List<Ship> ships : ScenarioLoader.loadShips(spec))
            for (Ship s : ships)
                if (setup.shipName.equals(s.getName()))
                    return s;
        throw new AssertionError(faction + "/" + type + " did not load");
    }

    private Map<String, Integer> fightersOf(Ship ship) {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (Shuttle s : ship.getShuttles().getAllShuttles())
            if (s instanceof Fighter)
                tally.merge(s.getCatalogType(), 1, Integer::sum);
        return tally;
    }

    /** The whole point: one ship file, five correct complements. */
    @Test
    public void theKzintiCvsReEquipsWithTheYear() {
        assertEquals("Y170 — the year it entered service",
                Map.of("aas", 11, "aas_e", 1), fightersOf(carrier("Kzinti", "CVS", 170)));
        assertEquals("Y173 — HAAS",
                Map.of("haas", 11, "haas_e", 1), fightersOf(carrier("Kzinti", "CVS", 173)));
        assertEquals("Y177 — TAAS",
                Map.of("taas", 11, "taas_e", 1), fightersOf(carrier("Kzinti", "CVS", 177)));
        assertEquals("Y180 — TADS",
                Map.of("tads", 11, "tads_e", 1), fightersOf(carrier("Kzinti", "CVS", 180)));
        assertEquals("Y183 — TADSC",
                Map.of("tadsc", 11, "tadsc_e", 1), fightersOf(carrier("Kzinti", "CVS", 183)));
    }

    @Test
    public void theBaySizeDoesNotChangeWithTheEra() {
        int spaces = 0;
        for (com.sfb.systemgroups.ShuttleBay b : carrier("Kzinti", "CVS", 170).getShuttles().getBays())
            spaces += b.getTotalSpaces();
        int later = 0;
        for (com.sfb.systemgroups.ShuttleBay b : carrier("Kzinti", "CVS", 183).getShuttles().getBays())
            later += b.getTotalSpaces();
        assertEquals("re-equipping must not grow the bay", spaces, later);
        assertEquals("three admin shuttles plus twelve fighters", 15, spaces);
    }

    @Test
    public void theHydranRnPlusGetsItsMixedComplement() {
        assertEquals(Map.of("stinger2", 6, "stingerh", 2, "stinger_e", 1),
                fightersOf(carrier("Hydran", "RN+", 170)));
    }

    /** The fallback, through the loader: before the Stinger-E, all nine are Stinger-1s. */
    @Test
    public void theHydranRnFliesStingerOnesInItsOwnEra() {
        assertEquals(Map.of("stinger1", 9), fightersOf(carrier("Hydran", "RN", 134)));
    }

    @Test
    public void aCarrierWithNoEwFighterStillGetsNone() {
        // The CVE carries six fighters — under J4.463's eight, so no EW fighter.
        Map<String, Integer> cve = fightersOf(carrier("Kzinti", "CVE", 170));
        assertEquals(Map.of("aas", 6), cve);
    }

    /**
     * The fighters' BPV feeds the carrier's Commander's Option budget (S3.211), so re-equipping
     * moves the budget — which is correct, and is the maintenance burden that per-era ship files
     * would have imposed by hand.
     */
    @Test
    public void reEquippingMovesTheOptionBudget() {
        int early = CoiBudget.carriedFighterBpv(carrier("Kzinti", "CVS", 170));
        int late = CoiBudget.carriedFighterBpv(carrier("Kzinti", "CVS", 183));
        assertEquals("11 AAS at 6 plus an AAS-E at 8", 11 * 6 + 8, early);
        assertEquals("11 TADSC at 12 plus a TADSC-E at 14", 11 * 12 + 14, late);
        assertTrue("the budget must follow the fighters",
                CoiBudget.allowanceFor(carrier("Kzinti", "CVS", 183))
                        > CoiBudget.allowanceFor(carrier("Kzinti", "CVS", 170)));
    }

    // ---------------------------------------------------------------- the data guard

    /**
     * No ship file may list a fighter that did not exist in its service year.
     * <p>
     * Only LITERAL lists are checked: a role-based complement cannot breach this, because the
     * year is what chooses the type. Most factions still list literally, which is exactly where
     * the next such error would appear.
     */
    @Test
    public void noShipListsAFighterFromItsOwnFuture() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> problems = new ArrayList<>();

        File root = new File("../data/factions");
        File[] factions = root.listFiles(File::isDirectory);
        assertNotNull("no faction directories under " + root.getAbsolutePath(), factions);

        for (File faction : factions) {
            File[] files = faction.listFiles(f -> f.getName().endsWith(".json"));
            if (files == null)
                continue;
            for (File file : files) {
                JsonNode ship = mapper.readTree(file);
                int serviceYear = ship.path("serviceYear").asInt(0);
                if (serviceYear <= 0)
                    continue;
                for (JsonNode bay : ship.path("shuttleBays")) {
                    JsonNode list = bay.isArray() ? bay : bay.path("shuttles");
                    for (JsonNode typeNode : list) {
                        ShuttleCatalog.Entry e = ShuttleCatalog.get(typeNode.asText());
                        if (e == null || !"fighter".equals(e.kind))
                            continue;
                        if (e.year > serviceYear)
                            problems.add(faction.getName() + "/" + file.getName()
                                    + ": serviceYear Y" + serviceYear + " but " + e.type
                                    + " is Y" + e.year);
                    }
                }
            }
        }
        assertTrue("fighters predating their own ship:\n  " + String.join("\n  ", problems),
                problems.isEmpty());
    }

    /** Every role-based complement must name a line the catalogue knows. */
    @Test
    public void everyDeclaredFighterLineExists() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> problems = new ArrayList<>();
        int declared = 0;

        for (File faction : new File("../data/factions").listFiles(File::isDirectory)) {
            File[] files = faction.listFiles(f -> f.getName().endsWith(".json"));
            if (files == null)
                continue;
            for (File file : files) {
                JsonNode ship = mapper.readTree(file);
                for (JsonNode bay : ship.path("shuttleBays")) {
                    JsonNode fighters = bay.path("fighters");
                    if (fighters.isMissingNode() || fighters.isNull())
                        continue;
                    declared++;
                    String line = fighters.path("line").asText(null);
                    if (line == null || ShuttleCatalog.lineEras(line).isEmpty())
                        problems.add(faction.getName() + "/" + file.getName()
                                + ": unknown fighter line '" + line + "'");
                }
            }
        }
        assertTrue("at least one carrier should use a line by now", declared > 0);
        assertTrue(String.join("\n  ", problems), problems.isEmpty());
    }
}
