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
}
