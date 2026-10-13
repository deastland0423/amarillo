package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The Kzinti large Q-ship's Y175 refit (R1.7).
 *
 * <p>The SSD states it as a history rather than as an upgrade: "ship had type-A drone racks (one
 * reload) until the Y175 refit, which converted these to type-C drone racks (2 reloads)", and
 * "ADDs had 6 rounds before Y175 refit". So the HULL carries the pre-refit fit and the refit is
 * what a ship fielded from Y175 has.
 *
 * <p>Worth a test of its own because this is the first refit in the data that changes a weapon's
 * PROPERTIES rather than adding or replacing weapons — a rack stays a rack and an ADD stays an
 * ADD, and only what they hold changes. A refit that silently did nothing would leave a Y180
 * Q-ship fighting with one reload and six rounds, and nothing else would notice.
 */
public class KzintiQShipRefitTest {

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private ShipSpec qship() {
        ShipSpec spec = ShipLibrary.get("Kzinti", "L-Q");
        assertNotNull("the library should hold the Kzinti large Q-ship", spec);
        return spec;
    }

    private static String rackType(ShipSpec spec, String designator) {
        return spec.weapons.stream()
                .filter(w -> "DroneRack".equals(w.type) && designator.equals(w.designator))
                .map(w -> String.valueOf(w.rackType))
                .findFirst().orElseThrow(() -> new AssertionError("no rack " + designator));
    }

    private static String addType(ShipSpec spec) {
        return spec.weapons.stream()
                .filter(w -> "ADD".equals(w.type))
                .map(w -> String.valueOf(w.addType))
                .findFirst().orElseThrow(() -> new AssertionError("no ADD"));
    }

    @Test
    public void theHullCarriesThePreRefitFit() {
        ShipSpec base = qship();

        assertEquals("TYPE_A", rackType(base, "Rack 1"));
        assertEquals("TYPE_A", rackType(base, "Rack 2"));
        assertEquals("ADD_6", addType(base));
    }

    @Test
    public void theY175RefitConvertsBothRacksAndTheAdd() {
        ShipSpec refitted = RefitResolver.apply(qship(), List.of("75"));

        assertEquals("both racks, not just the first (R1.7)",
                "TYPE_C", rackType(refitted, "Rack 1"));
        assertEquals("TYPE_C", rackType(refitted, "Rack 2"));
        assertEquals("the ADD goes from six rounds to twelve",
                "ADD_12", addType(refitted));
    }

    /** The sheet prices it: "Y175 REFIT = +4". */
    @Test
    public void theRefitCostsFourPoints() {
        ShipSpec base = qship();
        ShipSpec refitted = RefitResolver.apply(base, List.of("75"));

        assertEquals(base.bpv + 4, refitted.bpv);
    }

    /** It changes what the weapons hold, never how many of them there are. */
    @Test
    public void theRefitAddsNoWeapons() {
        ShipSpec base = qship();
        ShipSpec refitted = RefitResolver.apply(base, List.of("75"));

        assertEquals(base.weapons.size(), refitted.weapons.size());
    }
}
