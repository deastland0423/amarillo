package com.sfb.objects;

import com.sfb.scenario.FleetValidator;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * No hull in the game may be free.
 *
 * <h2>Why this exists</h2>
 * The Kzinti CD and the Klingon D6D were. Both are scouts, and {@link FleetValidator#costOf}
 * buys a scout at its ECONOMIC value (G24.35, cited by S8.11) rather than its combat BPV — so a
 * scout whose economic value comes back 0 is a 113-point cruiser you can put in a fleet for
 * nothing. The fleet builder showed it plainly, a lone {@code 0} in the price column, and it had
 * been there as long as the catalogue had.
 *
 * <p>The cause was a primitive. {@code ShipSpec.epv} is an {@code int}, so a hull that omits the
 * key gets 0, and {@code toMap} asserted that 0 over the Ship constructor's
 * {@code values.get("epv") == null ? battlePointValue : …} — a fallback that reads exactly right
 * and could never fire. 67 hulls declare a split value and were fine; the two that let it default
 * were not. A hull listing one BPV means the two values are equal, which is the owner's reading
 * and what the constructor always intended.
 *
 * <h2>What to assert</h2>
 * The cost, not the field. Asserting "every scout declares an epv" would force 38 hulls to
 * restate a number they are entitled to leave out, and would have been the wrong fix — see
 * {@code feedback_derived_vs_stated}: where a rule DERIVES a number the data may also STATE,
 * core must not refuse the stated one, nor demand it.
 */
public class ShipCostTest {

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    @Test
    public void noHullCostsNothing() {
        List<String> free = new ArrayList<>();
        int checked = 0;

        for (ShipSpec spec : ShipLibrary.all()) {
            Ship ship = ShipLibrary.createShip(spec);
            checked++;
            int cost = FleetValidator.costOf(ship);
            if (cost <= 0)
                free.add(spec.faction + "/" + spec.type + " costs " + cost
                        + " (bpv " + spec.bpv + (FleetValidator.isScout(ship)
                            ? ", and it is a SCOUT so costOf uses its economic value" : "")
                        + ")");
        }

        assertTrue("there should be hulls to price", checked > 300);
        String indent = System.lineSeparator() + "  ";
        assertTrue("a hull can be bought for nothing:" + indent + String.join(indent, free),
                free.isEmpty());
    }

    /**
     * A scout that lists one BPV is bought at it.
     * <p>
     * The two hulls the bug was found on, pinned by name. Both are scouts, neither declares an
     * {@code epv}, and both must cost their combat BPV rather than 0.
     */
    @Test
    public void aScoutWithNoSplitValueIsBoughtAtItsBpv() {
        for (String[] hull : new String[][] { { "Kzinti", "CD", "113" }, { "Klingon", "D6D", "113" } }) {
            ShipSpec spec = ShipLibrary.get(hull[0], hull[1]);
            assertTrue(hull[0] + "/" + hull[1] + " should be in the library", spec != null);
            Ship ship = ShipLibrary.createShip(spec);
            assertTrue(hull[1] + " should be a scout for this test to mean anything",
                    FleetValidator.isScout(ship));
            assertEquals(hull[1] + " lists one BPV, so economic equals combat",
                    Integer.parseInt(hull[2]), FleetValidator.costOf(ship));
        }
    }

    /** And a scout that DOES declare a split value still buys at the economic one. */
    @Test
    public void aScoutWithASplitValueIsBoughtAtTheEconomicOne() {
        ShipSpec spec = ShipLibrary.get("Federation", "SC");
        assertTrue("fixture: the Federation SC should be in the library", spec != null);
        assertTrue("fixture: it must declare a split value for this to test anything",
                spec.epv > 0 && spec.epv != spec.bpv);

        Ship ship = ShipLibrary.createShip(spec);
        assertEquals("G24.35: bought at the economic value, not the combat one",
                spec.epv, FleetValidator.costOf(ship));
    }
}
