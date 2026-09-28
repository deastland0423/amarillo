package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.sfb.objects.shuttles.Aas;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Haas_E;
import com.sfb.systemgroups.Shuttles;

/**
 * Squadron organisation (J4.46).
 * <p>
 * Not decoration: three rules turn on membership and none can be stated without it —
 * J4.221 (who may accept a transfer of control), J4.93 (EW is lent to a squadron, not to a
 * fighter), and J4.463 (how many EW fighters a carrier may field at all).
 */
public class SquadronTest {

    private static Shuttles carrierAt(String path) throws Exception {
        Ship ship = ShipLibrary.createShip(ShipSpec.fromJson(new File(path)));
        return ship.getShuttles();
    }

    private static Aas aas(String name) {
        Aas a = new Aas();
        a.setName(name);
        return a;
    }

    // -------------------------------------------------------------------------
    // J4.461: the minimum number of squadrons
    // -------------------------------------------------------------------------

    @Test
    public void twelveFightersMakeOneSquadronNotTwo() throws Exception {
        Shuttles cv = carrierAt("../data/factions/kzinti/cv.json");

        assertEquals("J4.461 says the MINIMUM number", 1, cv.getSquadrons().size());
        assertEquals(12, cv.getSquadrons().get(0).size());
    }

    @Test
    public void everyFighterLandsInASquadron() throws Exception {
        Shuttles cv = carrierAt("../data/factions/kzinti/cv.json");

        int placed = 0;
        for (Squadron sq : cv.getSquadrons())
            for (Fighter f : sq.getFighters()) {
                assertSame("and knows which one it is in", sq, f.getSquadron());
                placed++;
            }
        assertEquals(12, placed);
    }

    @Test
    public void aShipWithNoFightersHasNoSquadrons() throws Exception {
        Shuttles ca = carrierAt("../data/factions/federation/ca.json");

        assertTrue(ca.getSquadrons().isEmpty());
        assertEquals(0, ca.getDesignedFighterComplement());
    }

    // -------------------------------------------------------------------------
    // J4.462: what a squadron will hold
    // -------------------------------------------------------------------------

    @Test
    public void aSquadronTakesTwelveAndRefusesTheThirteenth() {
        Squadron sq = new Squadron("Alpha", null);
        for (int i = 1; i <= 12; i++)
            assertNull("fighter " + i, sq.add(aas("AAS-" + i)));

        String why = sq.add(aas("AAS-13"));

        assertNotNull(why);
        assertTrue(why, why.contains("J4.462"));
        assertEquals(12, sq.size());
    }

    @Test
    public void aFighterCannotJoinTwice() {
        Squadron sq = new Squadron("Alpha", null);
        Aas one = aas("AAS-1");
        sq.add(one);

        assertNotNull(sq.add(one));
        assertEquals(1, sq.size());
    }

    @Test
    public void leavingClearsTheBackReference() {
        Squadron sq = new Squadron("Alpha", null);
        Aas one = aas("AAS-1");
        sq.add(one);

        assertTrue(sq.remove(one));
        assertNull(one.getSquadron());
        assertFalse(sq.contains(one));
    }

    // -------------------------------------------------------------------------
    // J4.463: EW fighters
    // -------------------------------------------------------------------------

    @Test
    public void onlyOneEwFighterToASquadron() {
        Squadron sq = new Squadron("Alpha", null);
        Haas_E first = new Haas_E();
        first.setName("HAAS-E-1");
        Haas_E second = new Haas_E();
        second.setName("HAAS-E-2");

        assertNull(sq.add(first));
        String why = sq.add(second);

        assertNotNull(why);
        assertTrue(why, why.contains("J4.463"));
    }

    /**
     * The carrier-wide allowance, which is set by the complement the ship was DESIGNED
     * for: under eight none, under sixteen one, sixteen to twenty-four two, more three.
     */
    @Test
    public void theCarrierAllowanceFollowsTheDesignedComplement() throws Exception {
        Shuttles cvs = carrierAt("../data/factions/kzinti/cvs.json");

        assertEquals(12, cvs.getDesignedFighterComplement());
        assertEquals("twelve fighters is one EW fighter (J4.463)",
                1, cvs.allowedEwFighters());
        assertEquals("and the CVS carries exactly one", 1, cvs.ewFightersAboard());
    }

    @Test
    public void aSixFighterCarrierMayHaveNoEwFighterAtAll() throws Exception {
        Shuttles cve = carrierAt("../data/factions/kzinti/cve.json");

        assertEquals(6, cve.getDesignedFighterComplement());
        assertEquals("under eight fighters, none (J4.463)", 0, cve.allowedEwFighters());
    }

    @Test
    public void theAllowanceSurvivesLosses() throws Exception {
        Shuttles cvs = carrierAt("../data/factions/kzinti/cvs.json");
        int before = cvs.allowedEwFighters();

        // Shoot away most of the complement.
        for (com.sfb.systemgroups.ShuttleBay bay : cvs.getBays())
            for (int i = 0; i < 9 && i < bay.getSpaces().size(); i++)
                bay.getSpaces().get(i).destroy();

        assertEquals("J4.463: the entitlement is the DESIGN, not what survives",
                before, cvs.allowedEwFighters());
    }

    @Test
    public void theEwFighterIsNotCrowdedOutOfItsOwnSquadron() throws Exception {
        Shuttles cvs = carrierAt("../data/factions/kzinti/cvs.json");

        int ewSquadrons = 0;
        for (Squadron sq : cvs.getSquadrons()) {
            assertTrue("no squadron may hold two (J4.463)", sq.ewFighterCount() <= 1);
            if (sq.ewFighterCount() == 1)
                ewSquadrons++;
        }
        assertEquals("the CVS's one EW fighter is placed", 1, ewSquadrons);
    }

    // -------------------------------------------------------------------------
    // J4.461: one carrier's fighters
    // -------------------------------------------------------------------------

    @Test
    public void aSquadronWillNotTakeAnotherCarriersFighter() throws Exception {
        Ship carrier = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cv.json")));
        carrier.setName("KHS Sabre");
        Squadron sq = new Squadron("Alpha", carrier);

        Aas stranger = aas("AAS-X");
        stranger.setParentShipName("KHS Someone Else");

        String why = sq.add(stranger);

        assertNotNull(why);
        assertTrue(why, why.contains("J4.461"));
    }
}
