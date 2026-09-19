package com.sfb.objects;

import com.sfb.objects.shuttles.Aas;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.GASShuttle;
import com.sfb.objects.shuttles.Haas;
import com.sfb.objects.shuttles.HTSShuttle;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.objects.shuttles.SuicideShuttle;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Which shuttles may take which special role — J3.18 Wild Weasels, FD7.11 scatter packs,
 * J2.222 suicide shuttles.
 * <p>
 * Three lists, no two the same, which is the whole reason they are data rather than class
 * overrides:
 * <ul>
 *   <li>J3.18 — any non-fighter shuttle may weasel; fighters never (J4.41)</li>
 *   <li>FD7.11 — admin, MRS, MLS, MSS and FIGHTERS may be scatter packs</li>
 *   <li>J2.222 — admin, MSS, MRS, SWAC and MLS may be suicide shuttles; fighters, HTS and
 *       GAS may not</li>
 * </ul>
 * So a GAS may weasel and nothing else, a fighter may ONLY be a scatter pack, and a SWAC
 * may weasel and suicide but not scatter-pack. No list can be derived from another or from
 * the hierarchy; side by side as columns, each can be read against its rule.
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

    /**
     * FD7.11 admits fighters as scatter packs, but FD7.211 says one "carries up to its
     * normal load of drones" — so eligibility follows the drones it actually has. A Hydran
     * stinger is fusion-armed and carries none, which makes it ineligible in practice
     * despite FD7.11; a Kzinti attack shuttle carries two rails and qualifies.
     */
    @Test
    public void aFighterQualifiesOnlyIfItCarriesDrones() {
        assertFalse("a fusion-armed stinger has no drones to scatter (FD7.211)",
                new Stinger1().canBecomeScatterPack());
        assertTrue("a Kzinti attack shuttle carries two rails, so it qualifies",
                new Aas().canBecomeScatterPack());
    }

    /**
     * FD7.211 ties a fighter's scatter-pack capacity to its NORMAL drone load, so the
     * catalogue figure and the rails the class actually builds must agree. Two numbers in
     * two places is how the pack capacity came to ignore the catalogue in the first place.
     */
    @Test
    public void aFightersCapacityMatchesTheRailsItActuallyCarries() {
        assertEquals("a Kzinti AAS builds two drone rails, so it scatters two (FD7.211)",
                droneRails(new Aas()), new Aas().scatterPackSpaces());
        assertEquals(droneRails(new Haas()), new Haas().scatterPackSpaces());
        assertEquals("and a stinger builds none",
                droneRails(new Stinger1()), new Stinger1().scatterPackSpaces());
    }

    /** How many drone rails this fighter actually carries. */
    private int droneRails(com.sfb.objects.shuttles.Shuttle s) {
        int n = 0;
        for (com.sfb.weapons.Weapon w : s.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.DroneRail)
                n++;
        return n;
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
    public void noTwoListsAreTheSameList() {
        // Stated as its own assertion because a future refactor that collapses them into
        // one "special shuttle" flag would pass every other test in this file.
        assertNotEquals("a GAS may weasel but not scatter-pack",
                new GASShuttle().canBecomeWildWeasel(),
                new GASShuttle().canBecomeScatterPack());
        assertNotEquals("a drone-armed fighter is the other way round",
                new Aas().canBecomeWildWeasel(),
                new Aas().canBecomeScatterPack());
    }

    // ---------------------------------------------------------------- J2.222, suicide

    @Test
    public void anAdminShuttleMayBeASuicideShuttle() {
        assertTrue("J2.222 lists admin shuttles", new AdminShuttle().canBecomeSuicide());
    }

    @Test
    public void aGasOrHtsMayNotBeASuicideShuttle() {
        // J2.222 bars both by name — and both MAY weasel under J3.18, so this is the
        // second place the lists diverge.
        assertFalse("J2.222 bars ground assault shuttles", new GASShuttle().canBecomeSuicide());
        assertFalse("J2.222 bars heavy transport shuttles", new HTSShuttle().canBecomeSuicide());
    }

    @Test
    public void aFighterMayNotBeASuicideShuttle() {
        // Yet a drone-armed fighter MAY be a scatter pack — the inversion runs both ways.
        assertFalse("J2.222 bars fighters", new Aas().canBecomeSuicide());
        assertTrue("but FD7.11 admits them as scatter packs", new Aas().canBecomeScatterPack());
    }

    @Test
    public void allThreeListsDiffer() {
        // A GAS: weasel yes, suicide no, scatter pack no.
        GASShuttle gas = new GASShuttle();
        assertTrue(gas.canBecomeWildWeasel());
        assertFalse(gas.canBecomeSuicide());
        assertFalse(gas.canBecomeScatterPack());

        // A drone-armed fighter: barred from two roles, admitted to the third.
        Aas fighter = new Aas();
        assertFalse(fighter.canBecomeWildWeasel());
        assertFalse(fighter.canBecomeSuicide());
        assertTrue(fighter.canBecomeScatterPack());
    }

    // ---------------------------------------------------------------- FD7.21, capacity

    /**
     * FD7.21: "An admin shuttle used as an SP carries up to six spaces of drones. Other
     * types of shuttles could carry more or fewer as provided in their rules."
     * <p>
     * The capacity has to follow the shuttle the pack was built from. It did not: ScatterPack
     * held its own maxDroneSpaces of six, setMaxDroneSpaces was never called by anything, and
     * the catalogue column was read by nobody — so every pack carried six whatever it was.
     */
    @Test
    public void aPacksCapacityComesFromTheShuttleItWasBuiltFrom() {
        assertEquals("an admin shuttle's six (FD7.21)",
                new AdminShuttle().scatterPackSpaces(),
                new ScatterPack(new AdminShuttle()).getMaxDroneSpaces());
    }

    @Test
    public void aPackCannotBeLoadedBeyondThatCapacity() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        int capacity = pack.getMaxDroneSpaces();

        int loaded = 0;
        for (int i = 0; i < capacity + 4; i++)
            if (pack.addDrone(new Drone(DroneType.TypeI)))
                loaded++;

        assertTrue("a Type-I is one space, so it should take exactly the capacity",
                loaded <= capacity);
        assertTrue("and it should accept at least something", loaded > 0);
    }

    /**
     * The guard that matters for the next shuttle added. FD7.21 names MRS at eight spaces;
     * when MRS arrives, giving it scatterPackSize 8 must be enough on its own — nothing
     * should need editing in ScatterPack.
     */
    @Test
    public void capacityIsWhateverTheCatalogueSays_notAConstant() {
        ShuttleCatalog.Entry admin = ShuttleCatalog.get("admin");
        assertNotNull(admin);
        assertEquals("the class must not be carrying its own separate number",
                admin.scatterPackSize,
                new ScatterPack(new AdminShuttle()).getMaxDroneSpaces());
    }

    /**
     * The discriminating case. Every type in the catalogue today carries six spaces or none,
     * so a pack that ignored its base entirely would still read six and every other
     * assertion here would pass. This stands in for FD7.21's MRS at eight.
     */
    @Test
    public void capacityFollowsTheBase_evenWhenItIsNotSix() {
        AdminShuttle roomy = new AdminShuttle() {
            @Override public int scatterPackSpaces() { return 8; }
        };

        assertEquals("the pack must take its capacity FROM the shuttle, not assume six",
                8, new ScatterPack(roomy).getMaxDroneSpaces());
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
