package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Shuttle;

/**
 * Tunnel decks: a bay with a door at each end (J1.58).
 * <p>
 * "Each hatch operates independently at the full rate in (J1.50)." The Kzinti CV, CVS, CVL,
 * MCV and CVE are built this way, as is the Federation CVS — so those carriers put two craft
 * out on one impulse where a one-hatch bay manages one every two.
 * <p>
 * Counted as HATCHES and not as launch tubes, which is the distinction that makes this its
 * own thing: a tube cannot recover a shuttle (J1.541) and will not pass an administrative
 * shuttle or a heavy fighter (J1.542). A second door has neither limit.
 * <p>
 * This was reported as a cooldown bug — a carrier launching only on even impulses, as though
 * the clock ran on impulse parity instead of resetting on use. It did reset on use; the bay
 * simply had one hatch when the ship has two.
 */
public class TunnelDeckTest {

    private static ShuttleBay bayOf(String path) throws Exception {
        Ship ship = ShipLibrary.createShip(ShipSpec.fromJson(new File(path)));
        return ship.getShuttles().getBays().get(0);
    }

    private static ShuttleBay plainBay(int fighters) {
        ShuttleBay bay = new ShuttleBay(null);
        for (int i = 0; i < fighters; i++) {
            com.sfb.objects.shuttles.Fighter aas = com.sfb.objects.shuttles.CataloguedFighter.of("aas");
            aas.setName("AAS-" + (i + 1));
            bay.addSpace(new ShuttleSpace(aas));
        }
        return bay;
    }

    /** How many craft this bay can put out on one impulse. */
    private static int launchedInOneImpulse(ShuttleBay bay, int impulse) {
        int out = 0;
        while (true) {
            Shuttle next = null;
            for (Shuttle s : bay.getInventory())
                if (next == null && bay.canLaunch(s, impulse))
                    next = s;
            if (next == null)
                break;
            bay.launch(next, 6, 1, impulse);
            out++;
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // The hatch, not the bay, is what J1.50 limits
    // -------------------------------------------------------------------------

    @Test
    public void anOrdinaryBayHasOneHatch() {
        assertEquals(1, plainBay(4).getHatchCount());
    }

    @Test
    public void aTunnelDeckHasTwo() throws Exception {
        assertEquals("J1.58, and the Kzinti CV is one of the ships named",
                2, bayOf("../data/factions/kzinti/cv.json").getHatchCount());
    }

    @Test
    public void everyKzintiCarrierDeclaredATunnelGetsBothHatches() throws Exception {
        for (String hull : new String[] { "cv", "cvl", "cvl+", "cve", "cve+", "cvs" })
            assertEquals(hull + " is a tunnel deck (J1.58)", 2,
                    bayOf("../data/factions/kzinti/" + hull + ".json").getHatchCount());
    }

    @Test
    public void twoHatchesPutTwoCraftOutOnTheSameImpulse() {
        ShuttleBay bay = plainBay(6);
        bay.setHatchCount(ShuttleBay.TUNNEL_DECK_HATCHES);

        assertEquals("both doors work at once (J1.58)", 2, launchedInOneImpulse(bay, 10));
    }

    @Test
    public void aOneHatchBayStillManagesOnlyOne() {
        assertEquals("J1.50 unchanged where there is one door",
                1, launchedInOneImpulse(plainBay(6), 10));
    }

    /**
     * The point of "independently": both hatches cool for two impulses from their OWN use,
     * so the bay is shut the impulse after and open again the impulse after that.
     */
    @Test
    public void eachHatchServesItsOwnTwoImpulses() {
        ShuttleBay bay = plainBay(8);
        bay.setHatchCount(2);

        assertEquals(2, launchedInOneImpulse(bay, 10));
        assertFalse("both doors used, so none free next impulse", bay.canLaunch(11));
        assertTrue("and both free again two impulses on", bay.canLaunch(12));
        assertEquals(2, bay.getAvailableHatchCount(12));
    }

    /** Using one door leaves the other free the same impulse. */
    @Test
    public void usingOneHatchDoesNotShutTheOther() {
        ShuttleBay bay = plainBay(4);
        bay.setHatchCount(2);

        assertTrue(bay.claimHatch(10));
        assertEquals("one still open", 1, bay.getAvailableHatchCount(10));
        assertTrue(bay.claimHatch(10));
        assertEquals(0, bay.getAvailableHatchCount(10));
        assertFalse("and no third door to claim", bay.claimHatch(10));
    }

    /**
     * J1.50 counts a recovery against a hatch exactly as it counts a launch, so a tunnel
     * deck can land one craft and launch another on the same impulse — and then neither.
     */
    @Test
    public void aRecoveryAndALaunchCanShareATunnelDeckButNotAThird() {
        ShuttleBay bay = plainBay(4);
        bay.setHatchCount(2);

        bay.markUsed(10);                       // a recovery takes one door
        assertEquals(1, bay.getAvailableHatchCount(10));
        assertEquals("the other door still launches", 1, launchedInOneImpulse(bay, 10));
        assertFalse(bay.canLaunch(10));
    }

    /**
     * A hatch is not a tube. A tunnel deck must still pass an admin shuttle, which J1.542
     * bars from a launch tube — so the second door cannot be modelled as a second tube.
     */
    @Test
    public void bothHatchesWillPassAnAdminShuttle() {
        ShuttleBay bay = new ShuttleBay(null);
        for (int i = 0; i < 3; i++) {
            AdminShuttle admin = new AdminShuttle();
            admin.setName("Admin-" + (i + 1));
            bay.addSpace(new ShuttleSpace(admin));
        }
        bay.setHatchCount(2);

        assertEquals("two admin shuttles out at once, which tubes would refuse",
                2, launchedInOneImpulse(bay, 10));
    }

    /** A Hydran bay keeps its tubes and gains no second door: it is not a tunnel deck. */
    @Test
    public void aHydranBayIsUnchanged() throws Exception {
        ShuttleBay bay = bayOf("../data/factions/hydran/rn.json");

        assertEquals("one door", 1, bay.getHatchCount());
        assertEquals("three tubes (J1.54)", 3, bay.getLaunchTubeCount());
        assertEquals("three tubes and the door, all on one impulse",
                4, launchedInOneImpulse(bay, 10));
    }

    /**
     * The two launch systems compared at SHIP scale, which is the only scale a player cares
     * about and the one that is easy to misread off a single bay.
     * <p>
     * A Ranger has THREE bays of three tubes and a door; a Kzinti CV has ONE bay of two
     * doors. So the Ranger puts its whole group up in an impulse or two while the CV, even
     * with both hatches working, needs a dozen impulses for twelve fighters. That gap is the
     * rules' own — J1.54 tubes against J1.58 doors — and not an artefact of this code, but
     * it is large enough to be worth having written down.
     */
    @Test
    public void aRangerEmptiesItsBaysWhileACarrierIsStillStarting() throws Exception {
        Ship ranger = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rn.json")));
        Ship cv = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cv.json")));

        assertEquals("a Ranger has three bays", 3, ranger.getShuttles().getBays().size());
        assertEquals("a CV has one", 1, cv.getShuttles().getBays().size());

        assertEquals("three bays, tubes and doors together", 10, shipWideInOneImpulse(ranger));
        assertEquals("one bay, two doors (J1.58)", 2, shipWideInOneImpulse(cv));
    }

    /** Everything this ship can put out on one impulse, across every bay it has. */
    private static int shipWideInOneImpulse(Ship ship) {
        int total = 0;
        for (ShuttleBay bay : ship.getShuttles().getBays())
            total += launchedInOneImpulse(bay, 10);
        return total;
    }
}
