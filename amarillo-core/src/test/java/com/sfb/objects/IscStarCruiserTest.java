package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.weapons.PPD;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

/**
 * R13.6, the ISC Star Cruiser — the first hull in the tree to carry a PPD.
 *
 * <h2>What makes this ship worth its own test</h2>
 * Its sheet draws the FULLY REFITTED ship at 185 points and prices each refit as a deduction,
 * so the hull in the data is the Y160 original: eight phaser-1s and two plasma-G, and nothing
 * else. The ISC upgraded their ships as the years passed, and the four refits walk that history
 * back up to the printed ship. A reader who took the drawing for the base hull would build a
 * Y160 cruiser with weapons that did not exist yet.
 */
public class IscStarCruiserTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static Ship refitted(String... codes) {
        ShipSpec base = ShipLibrary.get("ISC", "CA");
        assertNotNull("fixture: the ISC CA should be in the library", base);
        return ShipLibrary.createShip(
                codes.length == 0 ? base : RefitResolver.apply(base, List.of(codes)));
    }

    private static long count(Ship ship, Class<?> type) {
        return ship.getWeapons().fetchAllWeapons().stream().filter(type::isInstance).count();
    }

    private static Weapon byDesignator(Ship ship, String designator) {
        return ship.getWeapons().fetchAllWeapons().stream()
                .filter(w -> designator.equals(w.getDesignator()))
                .findFirst().orElseThrow(() -> new AssertionError("no weapon " + designator));
    }

    // ------------------------------------------------------------------ the Y160 hull

    /** Phaser-1s and plasma-G, as the ISC first built her. */
    @Test
    public void theOriginalHullCarriesPhaserOnesAndPlasmaG() {
        Ship original = refitted();

        assertEquals(8, count(original, com.sfb.weapons.Phaser1.class));
        assertEquals("no phaser-3s before the Y165 refit",
                0, count(original, com.sfb.weapons.Phaser3.class));
        assertEquals("no PPD before the Y168 refit", 0, count(original, PPD.class));
        assertEquals("two launchers, and they are plasma-G",
                2, count(original, PlasmaLauncher.class));
        assertEquals(com.sfb.properties.PlasmaType.G,
                ((PlasmaLauncher) byDesignator(original, "B")).getLauncherType());
    }

    // ------------------------------------------------------------------ the refits

    /** The PPD refit is what makes this the ISC's signature hull (E11.0). */
    @Test
    public void thePpdRefitFitsAPulsarDevice() {
        Ship withPpd = refitted("P");

        assertEquals(1, count(withPpd, PPD.class));
        Weapon ppd = byDesignator(withPpd, "A");
        assertTrue(ppd instanceof PPD);
        assertEquals("the sheet prints PPD-FA", "FA", ppd.getArcLabel());
    }

    /**
     * The PL-S refit CONVERTS the two launchers rather than adding any — "without the PL-S
     * refit, plasma-S torpedoes 'B' and 'C' are plasma-G torpedoes".
     */
    @Test
    public void theplasmaSRefitConvertsRatherThanAdds() {
        Ship upgraded = refitted("S");

        assertEquals("still two launchers", 2, count(upgraded, PlasmaLauncher.class));
        assertEquals(com.sfb.properties.PlasmaType.S,
                ((PlasmaLauncher) byDesignator(upgraded, "B")).getLauncherType());
        assertEquals(com.sfb.properties.PlasmaType.S,
                ((PlasmaLauncher) byDesignator(upgraded, "C")).getLauncherType());
    }

    /**
     * THE ORDERING TRAP. The PL-S conversion is scoped to designators B and C, so ticking both
     * plasma refits cannot turn the rear-firing plasma-F into plasma-S. A swap keyed on weapon
     * TYPE alone would have done exactly that, and only in the combination where both are on.
     */
    @Test
    public void theplasmaSRefitCannotReachThePlasmaF() {
        Ship both = refitted("S", "F");

        assertEquals("two plasma-S and six plasma-F", 8, count(both, PlasmaLauncher.class));
        for (String rear : new String[] { "D", "E", "F", "G", "H", "J" })
            assertEquals("rear-firing " + rear + " stays a plasma-F",
                    com.sfb.properties.PlasmaType.F,
                    ((PlasmaLauncher) byDesignator(both, rear)).getLauncherType());
    }

    /**
     * R13.1C's rear-firing torpedoes, identified by the arcs D2.35 gives them. Those arcs are
     * the whole reason they launch in "direction three or five" and cannot take swivels, so
     * getting them wrong would quietly make them ordinary forward plasma.
     */
    @Test
    public void theRearFiringPlasmaBearAstern() {
        Ship armed = refitted("F");

        for (String left : new String[] { "D", "E", "F" })
            assertEquals("L + LR", byDesignator(armed, left).getArcLabel());
        for (String right : new String[] { "G", "H", "J" })
            assertEquals("R + RR", byDesignator(armed, right).getArcLabel());
    }

    /** The PH-3 refit brings six, three to each side. */
    @Test
    public void thePhaserThreeRefitBringsSix() {
        Ship upgraded = refitted("3");

        assertEquals(6, count(upgraded, com.sfb.weapons.Phaser3.class));
        assertEquals("LS", byDesignator(upgraded, "9").getArcLabel());
        assertEquals("RS", byDesignator(upgraded, "12").getArcLabel());
    }

    // ------------------------------------------------------------------ the price

    /**
     * All four refits reach the point value the sheet prints. The deductions are the sheet's
     * own: -6 without PH-3, -15 without the PPD, -10 without PL-S, -12 without PL-F.
     */
    @Test
    public void thefullyRefittedShipIsTheShipOnTheSheet() {
        ShipSpec base = ShipLibrary.get("ISC", "CA");
        ShipSpec full = RefitResolver.apply(base, List.of("3", "P", "S", "F"));

        assertEquals("the Y160 hull", 142, base.bpv);
        assertEquals("the ship the sheet draws", 185, full.bpv);
    }

    /** And she is size class 3, which is the smallest hull E11.16 allows a PPD on. */
    @Test
    public void sheIsJustLargeEnoughToCarryAPpd() {
        assertEquals(3, ShipLibrary.get("ISC", "CA").sizeClass);
    }
}
