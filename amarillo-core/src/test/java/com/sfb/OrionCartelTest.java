package com.sfb;

import com.sfb.objects.OrionCartel;
import com.sfb.objects.OrionCartelTable;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * The Orion cartel territory table (G15.44) loads and its access tiers resolve
 * correctly — the foundation the fleet-level cartel quota will build on.
 */
public class OrionCartelTest {

    private OrionCartelTable table() throws Exception {
        return OrionCartelTable.fromJson(new File("../data/reference/orion_cartels.json"));
    }

    @Test
    public void allElevenCartelsLoad() throws Exception {
        assertEquals("11 cartels (G15.44)", 11, table().all().size());
    }

    @Test
    public void accessTiers_resolveByTerritory() throws Exception {
        OrionCartel hamilcar = table().get("Hamilcar"); // home Klingon, ops Hydran/Federation
        assertEquals(OrionCartel.Access.HOME, hamilcar.accessFor("Klingon"));
        assertEquals(OrionCartel.Access.OPERATING, hamilcar.accessFor("Federation"));
        assertEquals(OrionCartel.Access.OPERATING, hamilcar.accessFor("Hydran"));
        assertEquals(OrionCartel.Access.OUTSIDE, hamilcar.accessFor("Romulan"));
    }

    @Test
    public void multiEmpireWeapon_takesTheBestTier() throws Exception {
        OrionCartel hamilcar = table().get("Hamilcar");
        // A weapon made in both Gorn (outside) and Klingon (home) → home.
        assertEquals(OrionCartel.Access.HOME,
                hamilcar.accessForAny(Arrays.asList("Gorn", "Klingon")));
        // Federation (operating) + Romulan (outside) → operating.
        assertEquals(OrionCartel.Access.OPERATING,
                hamilcar.accessForAny(Arrays.asList("Romulan", "Federation")));
        // All outside → outside.
        assertEquals(OrionCartel.Access.OUTSIDE,
                hamilcar.accessForAny(Arrays.asList("Gorn", "ISC")));
    }

    @Test
    public void caseInsensitiveLookup() throws Exception {
        assertNotNull(table().get("lion's heart"));
    }

    // ------------------------------------------------------- G15.442, the Cluster Cartel

    /**
     * G15.442: the Cluster "treats Kzinti, Lyran, and Klingon space as 'Home' territory (90% of
     * weapons) and can select the other 10% from all other allowed weapons."
     *
     * <p>Filed for a long time as an OPERATING zone, which capped those three at 20% and made a
     * legal Cluster fleet read illegal. They are home empires, and the zone is empty.
     */
    @Test
    public void theClusterIsAtHomeInFourEmpires() throws Exception {
        OrionCartel cluster = table().get("Cluster");

        for (String empire : Arrays.asList("WYN", "Kzinti", "Lyran", "Klingon"))
            assertEquals(empire + " is home territory for the Cluster (G15.442)",
                    OrionCartel.Access.HOME, cluster.accessFor(empire));
        assertTrue("and it has no operating zone — the other 10% comes from anywhere",
                cluster.operatingZone.isEmpty());
    }

    /** Permissive where the rule says so and nowhere else: an unnamed empire is still outside. */
    @Test
    public void theClusterIsStillAStrangerEverywhereElse() throws Exception {
        assertEquals(OrionCartel.Access.OUTSIDE, table().get("Cluster").accessFor("Hydran"));
    }

    /**
     * A multi-empire home must not leak. Pharoh's home is the Federation and the Klingons are
     * its operating zone; nothing about the Cluster may change that.
     */
    @Test
    public void aMultiEmpireHomeLeavesOtherCartelsAlone() throws Exception {
        OrionCartel pharoh = table().get("Pharoh");

        assertEquals(OrionCartel.Access.HOME, pharoh.accessFor("Federation"));
        assertEquals(OrionCartel.Access.OPERATING, pharoh.accessFor("Klingon"));
        assertEquals(OrionCartel.Access.OUTSIDE, pharoh.accessFor("WYN"));
    }
}
