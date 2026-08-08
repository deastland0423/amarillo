package com.sfb;

import com.sfb.objects.OptionCatalogEntry;
import com.sfb.objects.OptionMountCatalog;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * The Annex #8B option-mount catalog (G15.4) loads from JSON and its constraint
 * columns decode the annex symbols correctly. This pins the reference data so a
 * loadout validator (slice 2) has a trustworthy source, and so the chart itself
 * isn't silently lost or corrupted.
 */
public class OptionMountCatalogTest {

    private OptionMountCatalog catalog() throws Exception {
        return OptionMountCatalog.fromJson(new File("../data/reference/orion_option_mounts.json"));
    }

    @Test
    public void chartLoads() throws Exception {
        OptionMountCatalog c = catalog();
        assertNotNull("catalog parsed", c);
        assertTrue("main chart is substantial", c.all().size() >= 60);
        assertNotNull("provenance recorded", c.source);
    }

    @Test
    public void defaults_applyToAPlainWeapon() throws Exception {
        OptionCatalogEntry ph1 = catalog().get("Phaser-1");
        assertNotNull(ph1);
        assertEquals(0.0, ph1.cost, 1e-9);
        assertTrue(ph1.available);
        assertEquals("no size bar by default", 6, ph1.maxSizeClass);
        assertEquals("single mount by default", 1, ph1.mountsRequired);
        assertTrue("either position by default", ph1.allowedInPosition("WING"));
        assertTrue(ph1.allowedInPosition("CENTERLINE"));
    }

    @Test
    public void fractionalAndNegativeCosts_areCaptured() throws Exception {
        OptionMountCatalog c = catalog();
        assertEquals(-0.25, c.get("Phaser-2").cost, 1e-9);
        assertEquals(-0.5, c.get("Phaser-3").cost, 1e-9);
        assertEquals(0.25, c.get("Quantum Cannon").cost, 1e-9);
        assertEquals(-1.0, c.get("Atomic Missiles").cost, 1e-9);
    }

    @Test
    public void sizeBar_dagger_barsSize4AndSmaller() throws Exception {
        OptionCatalogEntry d30 = catalog().get("Disruptor-30");
        assertEquals(3, d30.maxSizeClass);
        assertFalse("‡ weapon barred on the size-4 LR", d30.allowedOnSizeClass(4));
        assertTrue("allowed on a size-3 hull", d30.allowedOnSizeClass(3));
    }

    @Test
    public void wingBar_delta_isCenterlineOnly() throws Exception {
        OptionCatalogEntry hellbore = catalog().get("Hellbore");
        assertEquals(Arrays.asList("CENTERLINE"), hellbore.positions);
        assertFalse(hellbore.allowedInPosition("WING"));
        assertEquals("Hellbore is also ‡", 3, hellbore.maxSizeClass);
    }

    @Test
    public void multiMount_asterisk_needsTwoMounts() throws Exception {
        OptionMountCatalog c = catalog();
        assertEquals(2, c.get("PPD").mountsRequired);
        assertEquals(2, c.get("Plasma-S Torp (No Swivel)").mountsRequired);
        assertEquals(2, c.get("ESG").mountsRequired);
    }

    @Test
    public void neverWeapons_areUnavailable() throws Exception {
        OptionMountCatalog c = catalog();
        assertFalse(c.get("Death Bolt Rack").available);
        assertFalse(c.get("Mauler").available);
        assertFalse(c.get("Plasma-R").available);
    }

    @Test
    public void yearGating_isRecorded() throws Exception {
        OptionMountCatalog c = catalog();
        assertEquals(140, c.get("ADD (6 round)").yearAvailable);
        assertEquals(165, c.get("PPD").yearAvailable);
        assertEquals(170, c.get("Plasma-S Torp (Swivel)").yearAvailable);
    }

    @Test
    public void weaponOrigins_universalVsFactionSpecific() throws Exception {
        OptionMountCatalog c = catalog();
        // Universal — no origin, exempt from the cartel quota.
        assertTrue("phasers are universal", c.get("Phaser-1").isUniversal());
        assertTrue("tractors are universal", c.get("Tractor Beam").isUniversal());
        // Faction-specific origins (G15.44).
        assertFalse(c.get("Phaser-G").isUniversal());
        assertEquals(java.util.List.of("Hydran"), c.get("Phaser-G").empires);
        assertEquals(java.util.List.of("Federation"), c.get("Photon Torpedo").empires);
        assertEquals(java.util.List.of("Klingon", "Kzinti", "Lyran"), c.get("Disruptor-30").empires);
        assertTrue(c.get("Plasma-S Torp (Swivel)").empires.contains("Gorn"));
    }
}
