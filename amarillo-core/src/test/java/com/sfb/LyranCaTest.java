package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.properties.Faction;
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.ESG;
import com.sfb.weapons.Weapon;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Lyran CA (Azure) — the first ESG-carrying ship. Verifies the JSON loads and
 * its two ESG boxes build as real {@link ESG} generators (G23.0/.11).
 */
public class LyranCaTest {

    @Test
    public void ca_loadsWithTwoEsgGenerators() throws Exception {
        ShipSpec spec = ShipSpec.fromJson(new File("../data/factions/lyran/ca.json"));
        assertNotNull("ca.json parsed", spec);
        Ship ca = ShipLibrary.createShip(spec);

        assertEquals(Faction.Lyran, ca.getFaction());
        assertEquals("CA", ca.getHullType());
        assertEquals(3, ca.getSizeClass());

        long esgs = ca.getWeapons().fetchAllWeapons().stream().filter(w -> w instanceof ESG).count();
        assertEquals("two ESG generators (G23.11)", 2, esgs);

        long disruptors = ca.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof Disruptor).count();
        assertEquals("four disruptors alongside the ESGs", 4, disruptors);

        for (Weapon w : ca.getWeapons().fetchAllWeapons()) {
            if (w instanceof ESG) {
                assertTrue("a fresh ESG starts inactive", !((ESG) w).isActive());
            }
        }
    }
}
