package com.sfb;

import com.sfb.objects.CartelQuota;
import com.sfb.objects.OptionMountCatalog;
import com.sfb.objects.OptionMountLoadout;
import com.sfb.objects.OrionCartel;
import com.sfb.objects.OrionCartelTable;
import com.sfb.objects.Ship;
import com.sfb.samples.OrionShips;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The Orion cartel fleet-quota (G15.44). A 2-LR fleet has 6 option mounts, so
 * (rounded to nearest, independent pools) the caps are 1 operating-zone and 1
 * outside weapon. Home-empire and universal weapons are unlimited. Cartel here
 * is Hamilcar: home Klingon, operating Hydran + Federation.
 */
public class CartelQuotaTest {

    private OptionMountCatalog catalog;
    private OrionCartel hamilcar;
    private Ship a, b;

    @Before
    public void setUp() throws Exception {
        catalog  = OptionMountCatalog.fromJson(new File("../data/reference/orion_option_mounts.json"));
        hamilcar = OrionCartelTable.fromJson(new File("../data/reference/orion_cartels.json")).get("Hamilcar");
        a = new Ship(); a.init(OrionShips.getLr());
        b = new Ship(); b.init(OrionShips.getLr());
    }

    private CartelQuota.Result evaluate() {
        return CartelQuota.evaluate(List.of(a, b), hamilcar, catalog);
    }

    @Test
    public void capsAreDerivedFromTotalMounts() {
        CartelQuota.Result r = evaluate();
        assertEquals("2 LRs × 3 mounts", 6, r.totalMounts);
        assertEquals("round(6 × 0.20)", 1, r.operatingCap);
        assertEquals("round(6 × 0.10)", 1, r.outsideCap);
    }

    @Test
    public void withinQuota_oneOperating_oneOutside_restHomeAndUniversal() {
        OptionMountLoadout.equip(a, catalog, "A", "Disruptor-22");  // Klingon = home (free)
        OptionMountLoadout.equip(a, catalog, "B", "Photon Torpedo"); // Federation = operating
        OptionMountLoadout.equip(a, catalog, "C", "Phaser-1");       // universal (free)
        OptionMountLoadout.equip(b, catalog, "A", "Drone Rack C");   // Kzinti = outside

        CartelQuota.Result r = evaluate();
        assertEquals(1, r.operatingUsed);
        assertEquals(1, r.outsideUsed);
        assertTrue(r.violations.toString(), r.withinQuota);
    }

    @Test
    public void tooManyOperatingZoneWeapons_violates() {
        OptionMountLoadout.equip(a, catalog, "A", "Photon Torpedo"); // Federation = operating
        OptionMountLoadout.equip(a, catalog, "B", "Fusion Beam");    // Hydran = operating

        CartelQuota.Result r = evaluate();
        assertEquals(2, r.operatingUsed);
        assertFalse(r.withinQuota);
        assertTrue(r.violations.get(0).contains("operating-zone"));
    }

    @Test
    public void tooManyOutsideWeapons_violates() {
        OptionMountLoadout.equip(a, catalog, "A", "Drone Rack C");            // Kzinti = outside
        OptionMountLoadout.equip(a, catalog, "B", "Plasma-F Torp (No Swivel)"); // Gorn/Rom/ISC = outside

        CartelQuota.Result r = evaluate();
        assertEquals(2, r.outsideUsed);
        assertFalse(r.withinQuota);
        assertTrue(r.violations.get(0).contains("outside"));
    }

    @Test
    public void homeEmpireWeapons_areUnlimited() {
        // Fill every mount with a home-empire (Klingon) disruptor — still legal.
        for (Ship s : List.of(a, b)) {
            OptionMountLoadout.equip(s, catalog, "A", "Disruptor-22");
            OptionMountLoadout.equip(s, catalog, "B", "Disruptor-22");
            OptionMountLoadout.equip(s, catalog, "C", "Disruptor-22");
        }
        CartelQuota.Result r = evaluate();
        assertEquals(0, r.operatingUsed);
        assertEquals(0, r.outsideUsed);
        assertTrue(r.withinQuota);
    }

    @Test
    public void noCartel_meansNoEnforcement() {
        OptionMountLoadout.equip(a, catalog, "A", "Drone Rack C");
        OptionMountLoadout.equip(a, catalog, "B", "Plasma-F Torp (No Swivel)");
        CartelQuota.Result r = CartelQuota.evaluate(List.of(a, b), null, catalog);
        assertTrue("null cartel → unconstrained", r.withinQuota);
    }
}
