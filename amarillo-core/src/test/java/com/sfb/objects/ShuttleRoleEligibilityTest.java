package com.sfb.objects;

import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.GASShuttle;
import com.sfb.objects.shuttles.HTSShuttle;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.objects.shuttles.SuicideShuttle;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Which shuttles may take which special role — J3.18 for Wild Weasels, FD7.11 for scatter
 * packs.
 * <p>
 * The two lists are partly inverted, which is the whole reason they are data rather than
 * class overrides. J3.18: any non-fighter shuttle may weasel, fighters never. FD7.11: only
 * admin, MRS, MLS and MSS shuttles AND fighters may be scatter packs — so a GAS may weasel
 * but not scatter-pack, and a fighter the reverse. Neither list can be derived from the
 * other, or from the hierarchy; side by side as columns, each can be read against its rule.
 * <p>
 * These assertions are the rules, not the current behaviour. If one fails, check the book
 * before changing the number.
 */
public class ShuttleRoleEligibilityTest {

    @Before
    public void loadCatalogue() throws Exception {
        if (!ShuttleCatalog.isLoaded())
            ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    // ---------------------------------------------------------------- J3.18, weasels

    @Test
    public void anyNonFighterShuttleMayWeasel() {
        assertTrue("an admin shuttle may (J3.18)", new AdminShuttle().canBecomeWildWeasel());
        assertTrue("a GAS may — it is a non-fighter shuttle (J3.18)",
                new GASShuttle().canBecomeWildWeasel());
        assertTrue("and so may an HTS, which could not before this was data (J3.18)",
                new HTSShuttle().canBecomeWildWeasel());
    }

    @Test
    public void aFighterMayNotWeasel() {
        assertFalse("J4.41 bars fighters outright", new Stinger1().canBecomeWildWeasel());
    }

    // ---------------------------------------------------------------- FD7.11, scatter packs

    @Test
    public void anAdminShuttleMayBeAScatterPack() {
        assertTrue("FD7.11 lists admin shuttles", new AdminShuttle().canBecomeScatterPack());
        assertTrue("with room for drones", new AdminShuttle().scatterPackSpaces() > 0);
    }

    @Test
    public void aFighterMayBeAScatterPack() {
        // The inversion that makes this data rather than a hierarchy: a fighter cannot
        // weasel but CAN be a scatter pack (FD7.11, via FD7.44).
        assertTrue("FD7.11 admits fighters", new Stinger1().canBecomeScatterPack());
    }

    @Test
    public void aGasOrHtsMayNotBeAScatterPack() {
        // Neither appears in FD7.11's list, though both may weasel under J3.18 — which is
        // exactly why one list cannot be derived from the other.
        assertFalse("a GAS is not a qualified SP shuttle (FD7.11)",
                new GASShuttle().canBecomeScatterPack());
        assertFalse("nor is an HTS (FD7.11)", new HTSShuttle().canBecomeScatterPack());
    }

    @Test
    public void theTwoListsAreNotTheSameList() {
        // Stated as its own assertion because a future refactor that collapses them into
        // one "special shuttle" flag would pass every other test in this file.
        assertNotEquals("a GAS may weasel but not scatter-pack",
                new GASShuttle().canBecomeWildWeasel(),
                new GASShuttle().canBecomeScatterPack());
        assertNotEquals("a fighter is the other way round",
                new Stinger1().canBecomeWildWeasel(),
                new Stinger1().canBecomeScatterPack());
    }

    // ---------------------------------------------------------------- conversions remember

    @Test
    public void aConvertedShuttleRemembersWhatItWasBuiltFrom() {
        // Without this a GAS-turned-scatter-pack could not be named or labelled honestly,
        // because the class is ScatterPack and the original type is gone.
        ScatterPack fromGas = new ScatterPack(new GASShuttle());
        assertEquals("gas", fromGas.getCatalogType());

        SuicideShuttle fromAdmin = new SuicideShuttle(new AdminShuttle());
        assertEquals("admin", fromAdmin.getCatalogType());
    }

    @Test
    public void everyShuttleKnowsItsCatalogueType() {
        // A null key silently makes a shuttle ineligible for every role, so it would look
        // like a rules decision rather than a missing constructor line.
        assertEquals("admin", new AdminShuttle().getCatalogType());
        assertEquals("gas", new GASShuttle().getCatalogType());
        assertEquals("hts", new HTSShuttle().getCatalogType());
        assertEquals("stinger1", new Stinger1().getCatalogType());
    }

    @Test
    public void everyCatalogueKeyUsedByAClassActuallyExists() {
        for (String key : new String[] { "admin", "gas", "hts", "stinger1" })
            assertNotNull("the class names a catalogue type that the JSON does not define: "
                    + key, ShuttleCatalog.get(key));
    }
}
