package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.sfb.scenario.ScenarioLoader;
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * R3.55, the Klingon D5I: the D5 hull issued to the Internal Security Forces.
 *
 * <p>Its sheet says plainly what that costs — "THE D5I ASSIGNED TO THE INTERNAL SECURITY FORCES
 * HAD INFERIOR FIRE CONTROL TO THOSE SHIPS IN THE DEEP SPACE FLEET" — and three concrete things
 * carry it. All three are what a copy of the D5 gets wrong, which is exactly how this hull was
 * entered, so each one is pinned here:
 *
 * <ul>
 *   <li>the FORWARD phasers are phaser-2s, where the D5 mounts phaser-1s;</li>
 *   <li>the disruptors are range 22, where the D5's are range 30;</li>
 *   <li>no DERFACS, which the D5 declares.</li>
 * </ul>
 *
 * <p>The owner caught the range and the DERFACS while reading the sheet alongside; the draft had
 * both copied from the D5. A test is cheaper than reading the sheet again.
 */
public class KlingonD5iTest {

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private Ship d5i(int year) {
        ShipSpec spec = ShipLibrary.get("Klingon", "D5I");
        assertNotNull("the D5I should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, "Klingon", year, spec);
        return ship;
    }

    /** The whole hull in one assertion each, against the D5 it is NOT. */
    @Test
    public void itsFireControlIsTheInferiorKind() {
        Ship ship = d5i(175);

        int disruptors = 0;
        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof Disruptor) {
                disruptors++;
                assertEquals("R3.55: the ISF disruptors are range 22, not the D5's 30",
                        22, ((Disruptor) w).getDisruptorRange());
            }
        }
        assertEquals("fixture: four disruptors", 4, disruptors);

        assertFalse("the D5I has no DERFACS, where the D5 does", ship.hasDerfacs());
        assertFalse("and no UIM either", ship.hasUim());
    }

    /** The forward pair are phaser-2s. The D5's are phaser-1s; this is the visible downgrade. */
    @Test
    public void itsForwardPhasersArePhaser2s() {
        Ship ship = d5i(175);

        int phaser1s = 0;
        for (Weapon w : ship.getWeapons().getPhaserList())
            if ("Phaser1".equals(w.getClass().getSimpleName()))
                phaser1s++;

        assertEquals("no phaser-1 anywhere on this hull", 0, phaser1s);
    }

    /**
     * "SHIP HAD TYPE-A DRONE RACKS (ONE RELOAD) UNTIL THE Y175 REFIT, WHICH CONVERTED THESE TO
     * TYPE-B DRONE RACKS (2 RELOADS)."
     */
    @Test
    public void theY175RefitConvertsItsRacksToTypeB() {
        int racks = 0;
        for (Weapon w : d5i(176).getWeapons().getDroneList()) {
            if (w instanceof DroneRack) {
                racks++;
                assertEquals("Y175 converts the type-A racks",
                        DroneRack.DroneRackType.TYPE_B, ((DroneRack) w).getRackType());
            }
        }
        assertEquals("fixture: two racks", 2, racks);
    }

    /** "ANTI-DRONES ALWAYS HAD 12 ROUNDS", so it is born with twelve and never converts up. */
    @Test
    public void itsAntiDronesStartWithTwelveRounds() {
        Ship early = d5i(175);
        int adds = 0;
        for (Weapon w : early.getWeapons().fetchAllWeapons()) {
            if ("ADD".equals(w.getClass().getSimpleName())) {
                adds++;
                assertEquals("twelve rounds from the start", 12, ((com.sfb.weapons.ADD) w).getCapacity());
            }
        }
        assertEquals("fixture: the hull carries two ADDs", 2, adds);
    }

    /** An internal-security ship does not carry a boarding force: 36 crew, 6 parties. */
    @Test
    public void itCarriesASmallerComplementThanTheD5() {
        Ship ship = d5i(175);
        assertEquals(36, ship.getCrew().getAvailableCrewUnits());
        assertEquals(6, ship.getCrew().getAvailableBoardingParties());
    }
}
