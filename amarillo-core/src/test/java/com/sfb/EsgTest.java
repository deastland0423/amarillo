package com.sfb;

import com.sfb.objects.ShipSpec;
import com.sfb.objects.WeaponFactory;
import com.sfb.weapons.Esg;
import com.sfb.weapons.Weapon;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * ESG generator mechanics (G23.0) at the weapon level: the strength chart
 * (G23.42), energy accumulation (G23.211/.22), activation (G23.3), strength
 * depletion (G23.511), and the 32-impulse lifespan (G23.32).
 */
public class EsgTest {

    @Test
    public void strengthChart_matchesG2342() {
        assertEquals(20, Esg.strengthFor(0, 5)); // radius 0, full energy
        assertEquals(4,  Esg.strengthFor(1, 1));
        assertEquals(10, Esg.strengthFor(2, 3));
        assertEquals(15, Esg.strengthFor(3, 5)); // biggest radius, weakest for the energy
        assertEquals(0,  Esg.strengthFor(0, 0)); // no energy → no field
        assertEquals(0,  Esg.strengthFor(4, 3)); // radius out of range
    }

    @Test
    public void energyAccumulates_cappedAtFive() {
        Esg esg = new Esg();
        esg.addEnergy(3);
        esg.addEnergy(4);
        assertEquals("capped at MAX_ENERGY (G23.22)", 5, esg.getStoredEnergy());
    }

    @Test
    public void activate_formsFieldAndReleasesEnergy() {
        Esg esg = new Esg();
        esg.setStoredEnergy(3);

        esg.activate(2, 10);

        assertTrue(esg.isActive());
        assertEquals(2, esg.getRadius());
        assertEquals("strength = chart[radius 2][energy 3]", 10, esg.getStrength());
        assertEquals("all energy released on activation (G23.222)", 0, esg.getStoredEnergy());
    }

    @Test
    public void activate_withNoEnergy_formsNoField() {
        Esg esg = new Esg();
        esg.activate(1, 5);
        assertFalse(esg.isActive());
        assertEquals(0, esg.getStrength());
    }

    @Test
    public void absorbDamage_depletesAndCollapsesAtZero() {
        Esg esg = new Esg();
        esg.setStoredEnergy(5);
        esg.activate(0, 0); // strength 20

        esg.absorbDamage(8);
        assertTrue(esg.isActive());
        assertEquals(12, esg.getStrength());

        esg.absorbDamage(15); // more than remaining
        assertFalse("field collapses when strength hits 0 (G23.511)", esg.isActive());
        assertEquals(0, esg.getStrength());
    }

    @Test
    public void field_expiresAfter32Impulses() {
        Esg esg = new Esg();
        esg.setStoredEnergy(2);
        esg.activate(1, 100);

        assertFalse(esg.isExpired(131)); // 31 impulses elapsed
        assertTrue(esg.isExpired(132));  // 32 impulses elapsed (G23.32)
    }

    @Test
    public void weaponFactory_buildsEsgFromRecipe() {
        ShipSpec.WeaponSpec ws = new ShipSpec.WeaponSpec();
        ws.type = "Esg";
        ws.designator = "A";
        Weapon w = WeaponFactory.build(ws, List.of("FULL"));
        assertTrue("ship JSON / option mounts can build an ESG", w instanceof Esg);
    }

    @Test
    public void destroyedGenerator_collapsesTheField() {
        Esg esg = new Esg();
        esg.setStoredEnergy(3);
        esg.activate(1, 0);
        assertTrue(esg.isActive());

        esg.damage(); // 'drone' DAC hit destroys the ESG box (G23.14)
        assertFalse("a destroyed generator's field collapses immediately", esg.isActive());
    }
}
