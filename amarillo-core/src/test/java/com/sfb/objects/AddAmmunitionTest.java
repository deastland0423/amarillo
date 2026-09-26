package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.sfb.samples.KlingonShips;
import com.sfb.weapons.ADD;
import com.sfb.weapons.Weapon;

/**
 * What an ADD carries when it is built — and why none of it is written in a ship file.
 * <p>
 * Two numbers, and E5.71 fixes both. The STARTING LOAD is the rack: an ADD_6 launches with
 * six and an ADD_12 with twelve. The RESERVE is "two complete sets of reloads for the rack",
 * for every ship equipped with one — the rule's own illustration being the Y175 refit taking
 * a ship from twelve reloads to 24, "a function of the larger rack". So the reserve is twice
 * the capacity, and the capacity comes from the type. A ship file has nothing to add.
 * <p>
 * It used to try. Every ship carried a field called "shots", set to 6 or 12, which restated
 * what addType already said and read as the starting load. It was neither: WeaponFactory
 * passed it as the constructor's reload-set count, so a JSON-loaded ADD_12 stowed twelve
 * sets — 144 rounds — where the sample builders gave two. Nothing compared the two sources,
 * so they drifted in silence.
 * <p>
 * The field is gone and the number lives with the weapon as {@link ADD#RELOAD_SETS}. These
 * tests hold the rule in place from both directions: the arithmetic E5.71 specifies, and the
 * agreement between a ship built from JSON and the same ship built from a sample.
 */
public class AddAmmunitionTest {

    private static ADD firstAdd(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof ADD)
                return (ADD) w;
        throw new AssertionError(ship.getName() + " should carry an ADD");
    }

    private Ship fromJson(String path) throws Exception {
        ShipSpec spec = ShipSpec.fromJson(new File(path));
        assertNotNull(path + " parsed", spec);
        return ShipLibrary.createShip(spec);
    }

    /** The rack is the load: whatever the type holds, it starts full. */
    @Test
    public void anAddStartsWithAFullRack() throws Exception {
        ADD twelve = firstAdd(fromJson("../data/factions/klingon/d5.json"));
        assertEquals(ADD.AddType.ADD_12, twelve.getAddType());
        assertEquals("an ADD_12 launches with twelve", 12, twelve.getShots());

        ADD six = firstAdd(fromJson("../data/factions/kzinti/cvl+.json"));
        assertEquals(ADD.AddType.ADD_6, six.getAddType());
        assertEquals("an ADD_6 launches with six", 6, six.getShots());
    }

    /**
     * E5.71, in the terms the rule uses: two complete SETS, so the reserve tracks the rack
     * rather than being a number of its own. Twelve for the small rack, 24 for the large —
     * the two figures the rule names when describing the Y175 refit.
     */
    @Test
    public void everyAddStowsTwoCompleteSets() throws Exception {
        assertEquals("E5.71: two complete sets", 2, ADD.RELOAD_SETS);

        assertEquals("two sets of six", 12,
                new ADD(ADD.AddType.ADD_6).getReloadsAvailable());
        assertEquals("two sets of twelve", 24,
                new ADD(ADD.AddType.ADD_12).getReloadsAvailable());
    }

    /**
     * The drift this class exists to catch. Before the fix the JSON D5 answered 144 and the
     * sample D5 answered 24; they are the same ship and must say the same thing.
     */
    @Test
    public void aJsonShipAndItsSampleTwinCarryTheSameReserve() throws Exception {
        Ship fromFile = fromJson("../data/factions/klingon/d5.json");

        Ship fromSample = new Ship();
        fromSample.init(KlingonShips.getD5());

        assertEquals("the same ship from two sources must stow the same ammunition",
                firstAdd(fromSample).getReloadsAvailable(),
                firstAdd(fromFile).getReloadsAvailable());
        assertEquals("two sets of twelve", 24, firstAdd(fromFile).getReloadsAvailable());
    }

    /**
     * And the reason it cannot drift again: there is no longer anything to disagree about.
     * A ship file names the rack and stops, so two files describing the same rack cannot
     * describe different ammunition.
     */
    @Test
    public void aShipFileNamesTheRackAndNothingElse() throws Exception {
        String d5 = new String(java.nio.file.Files.readAllBytes(
                new File("../data/factions/klingon/d5.json").toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

        assertTrue("premise: the D5 still carries ADDs", d5.contains("\"addType\": \"ADD_12\""));
        assertFalse("a reserve in a ship file would be a second source of truth",
                d5.contains("\"reloads\""));
        assertFalse("and so would the field it replaced", d5.contains("\"shots\""));
    }
}
