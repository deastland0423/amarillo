package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.sfb.scenario.ScenarioLoader;
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * R3.52, the Klingon D5F: the D5 hull rebuilt to kill fighters and drones.
 *
 * <p>The trade is the whole ship. It gives up BOTH drone racks and mounts FOUR anti-drone racks
 * where the D5 carries two of each — everything it fires at a seeking weapon, it fires twice as
 * much of.
 *
 * <p>Read as the OPPOSITE of the D5I, which was entered the same day off the facing page. The two
 * are the deep-space fit and the internal-security fit of one hull, and copying either into the
 * other goes wrong in both directions:
 *
 * <table>
 *   <tr><th></th><th>D5F (this)</th><th>D5I</th></tr>
 *   <tr><td>forward phasers</td><td>phaser-1</td><td>phaser-2</td></tr>
 *   <tr><td>disruptor range</td><td>30</td><td>22</td></tr>
 *   <tr><td>UIM / DERFACS</td><td>both</td><td>neither</td></tr>
 * </table>
 *
 * <p>So {@link KlingonD5iTest} and this class assert the same four facts with opposite answers.
 * That is deliberate: a future edit that "tidies" one hull into the other fails on both sides at
 * once, which is a much louder noise than one quiet wrong number.
 */
public class KlingonD5fTest {

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private Ship d5f(int year) {
        ShipSpec spec = ShipLibrary.get("Klingon", "D5F");
        assertNotNull("the D5F should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, "Klingon", year, spec);
        return ship;
    }

    /** Four ADDs and no drone rack: the reason the hull exists. */
    @Test
    public void itTradesBothDroneRacksForTwoMoreAntiDroneRacks() {
        Ship ship = d5f(172);

        int adds = 0, racks = 0;
        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if ("ADD".equals(w.getClass().getSimpleName()))
                adds++;
            if (w instanceof DroneRack)
                racks++;
        }

        assertEquals("four anti-drone racks, where the D5 has two", 4, adds);
        assertEquals("and no drone rack at all, where the D5 has two", 0, racks);
    }

    /** The deep-space fit: full-range disruptors and the fire control to use them. */
    @Test
    public void itHasTheFireControlTheD5iLacks() {
        Ship ship = d5f(172);

        int disruptors = 0;
        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof Disruptor) {
                disruptors++;
                assertEquals("R3.52: range 30, the full table — the D5I's stop at 22",
                        30, ((Disruptor) w).getDisruptorRange());
            }
        }
        assertEquals("fixture: four disruptors", 4, disruptors);

        assertTrue("the data table says 1 x UIM STANDARD", ship.hasUim());
        assertTrue("and the disruptor table prints a DERFACS row", ship.hasDerfacs());
    }

    /** Forward phasers are phaser-1s here. On the D5I they are phaser-2s. */
    @Test
    public void itsForwardPhasersArePhaser1s() {
        int phaser1s = 0;
        for (Weapon w : d5f(172).getWeapons().getPhaserList())
            if ("Phaser1".equals(w.getClass().getSimpleName()))
                phaser1s++;

        assertEquals("the forward pair, before any K refit", 2, phaser1s);
    }

    /**
     * "PHASER-2s MARKED K ARE CHANGED TO PHASER-1s ON THE K REFIT" — the two wing mounts, as on
     * the D5 and D5N. After it the hull carries four phaser-1s and no phaser-2.
     */
    @Test
    public void theKRefitConvertsTheWingPhasers() {
        ShipSpec refitted = ShipLibrary.get("Klingon", "D5FK");
        assertNotNull("the K variant should be registered", refitted);
        Ship ship = ShipLibrary.createShip(refitted);

        int phaser1s = 0, phaser2s = 0;
        for (Weapon w : ship.getWeapons().getPhaserList()) {
            if ("Phaser1".equals(w.getClass().getSimpleName()))
                phaser1s++;
            if ("Phaser2".equals(w.getClass().getSimpleName()))
                phaser2s++;
        }

        assertEquals("the forward pair plus both wings", 4, phaser1s);
        assertEquals("nothing left for the refit to convert", 0, phaser2s);
        assertEquals("and it costs two points", 110 + 2, refitted.bpv);
    }

    /** Full D5 complement, unlike the D5I's reduced one. */
    @Test
    public void itCarriesTheD5sFullComplement() {
        Ship ship = d5f(172);
        assertEquals(40, ship.getCrew().getAvailableCrewUnits());
        assertEquals(8, ship.getCrew().getAvailableBoardingParties());
    }
}
