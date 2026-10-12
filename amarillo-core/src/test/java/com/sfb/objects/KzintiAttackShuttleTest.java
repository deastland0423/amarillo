package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.scenario.ScenarioLoader;

/**
 * The Kzinti AS, and the Master Fighter Chart's "No drone control" column.
 *
 * <p>The chart (Annex, R5.0 Kzinti Hegemony Fighters) lists the AS beside the AAS:
 * <pre>
 *   AS    1  8  1xP-3-FA  2xI   8  No drone control   5  161  2
 *   AAS   1  8  1xP-3-FA  2xI   8  —                  6  164  2
 * </pre>
 * Identical but for one point of BPV, three years, and whether it can guide what it launches.
 *
 * <p>The AS was missing, and its absence had a visible consequence: the Kzinti DDV enters service
 * in Y163, but the earliest fighter in the data was the AAS at Y164, so a DDV fielded in its own
 * service year had no air group. That stayed hidden while an unfillable complement kept whatever
 * the hull was built with, and surfaced the moment that fallback was corrected to seat shuttles
 * (see {@link DiplomaticFighterByYearTest}). The owner identified the missing craft; the chart
 * supplied its numbers.
 */
public class KzintiAttackShuttleTest {

    @Before
    public void load() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private Ship ddv(int year) {
        ShipSpec spec = ShipLibrary.get("Kzinti", "DDV");
        assertNotNull("fixture: the DDV should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, "Kzinti", year, spec);
        return ship;
    }

    private Map<String, Integer> aboard(Ship ship) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Shuttle s : ship.getShuttles().getAllShuttles())
            counts.merge(s.getCatalogType(), 1, Integer::sum);
        return counts;
    }

    @Test
    public void theAsIsCataloguedFromY161() {
        ShuttleCatalog.Entry as = ShuttleCatalog.get("as");
        assertNotNull("the AS should be in the catalogue", as);
        assertEquals(161, as.year);
        assertEquals(5, as.bpv);
        assertEquals(8, as.speed);
        assertEquals(8, as.hull);
        assertFalse("the chart says 'No drone control'", as.controlsDrones);
    }

    /** The reason it was missing mattered: a carrier with no air group in its own era. */
    @Test
    public void theDdvHasAnAirGroupInItsOwnServiceYear() {
        ShipSpec spec = ShipLibrary.get("Kzinti", "DDV");
        Map<String, Integer> counts = aboard(ddv(spec.serviceYear));

        assertEquals("the DDV should seat twelve AS in Y" + spec.serviceYear + ", got " + counts,
                Integer.valueOf(12), counts.get("as"));
    }

    /** And the line still hands over to the AAS when the AAS arrives. */
    @Test
    public void theAasSupersedesItInY164() {
        assertEquals("Y163 flies the AS", Integer.valueOf(12), aboard(ddv(163)).get("as"));
        assertNull("Y164 does not", aboard(ddv(164)).get("as"));
        assertEquals("Y164 flies the AAS", Integer.valueOf(11), aboard(ddv(164)).get("aas"));
    }

    /**
     * The clause the flag exists for. An AS launches its drones and then cannot steer them; an
     * AAS, with the same two standard rails, guides two.
     */
    @Test
    public void anAsGuidesNothingWhileAnAasGuidesTwo() {
        assertEquals("AS: no drone control", 0, firstFighterCapacity(ddv(163)));
        assertEquals("AAS: two channels from two standard rails (J4.25)",
                2, firstFighterCapacity(ddv(164)));
    }

    private int firstFighterCapacity(Ship ship) {
        for (Shuttle s : ship.getShuttles().getAllShuttles())
            if (s instanceof Fighter f)
                return f.getControlCapacity();
        fail("fixture: the DDV should be carrying fighters");
        return -1;
    }
}
