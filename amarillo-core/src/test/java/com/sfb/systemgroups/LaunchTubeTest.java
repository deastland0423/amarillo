package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;

/**
 * Launch tubes (J1.54) and what they will and will not pass.
 * <p>
 * A tube is not a second hatch, and the rule is careful about the difference: a tube launches
 * fighters only (J1.542), cannot recover anything (J1.541), and sits alongside a standard
 * hatch that does both (J1.543). That is why a tunnel deck's second door is counted as a
 * hatch rather than as another tube — see {@link TunnelDeckTest}.
 */
public class LaunchTubeTest {

    private static ShuttleBay bayWith(int tubes, Shuttle... craft) {
        ShuttleBay bay = new ShuttleBay(null);
        for (Shuttle s : craft)
            bay.addSpace(new ShuttleSpace(s));
        bay.setLaunchTubeCount(tubes);
        return bay;
    }

    private static Fighter stinger(int n) {
        Fighter s = CataloguedFighter.of("stinger1");
        s.setName("Stinger-" + n);
        return s;
    }

    private static AdminShuttle admin(int n) {
        AdminShuttle a = new AdminShuttle();
        a.setName("Admin-" + n);
        return a;
    }

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

    @Test
    public void everyTubeAndTheDoorAllGoOnTheSameImpulse() {
        ShuttleBay bay = bayWith(3, stinger(1), stinger(2), stinger(3), stinger(4),
                stinger(5));

        assertEquals("three tubes plus the standard hatch (J1.543)",
                4, launchedInOneImpulse(bay, 10));
    }

    /** J1.541: each tube is its own two-impulse clock, like the hatch. */
    @Test
    public void eachTubeServesItsOwnTwoImpulses() {
        ShuttleBay bay = bayWith(2, stinger(1), stinger(2), stinger(3), stinger(4),
                stinger(5), stinger(6));

        assertEquals(3, launchedInOneImpulse(bay, 10));   // two tubes and the door
        assertEquals("everything used, so nothing next impulse",
                0, launchedInOneImpulse(bay, 11));
        assertEquals("and all of it back two impulses on",
                3, launchedInOneImpulse(bay, 12));
    }

    /**
     * J1.542: an administrative shuttle cannot go through a tube, so a bay of three tubes
     * still puts out exactly one of them — through the door, because nothing else takes it.
     */
    @Test
    public void anAdminShuttleWillNotFitATubeHoweverManyAreFree() {
        ShuttleBay bay = bayWith(3, admin(1), admin(2), admin(3));

        assertEquals("one, through the standard hatch", 1, launchedInOneImpulse(bay, 10));
    }

    /** The mix a real Hydran bay holds: the fighters take the tubes, the shuttle the door. */
    @Test
    public void fightersTakeTheTubesAndLeaveTheDoorForTheShuttle() throws Exception {
        Ship rn = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rn.json")));
        ShuttleBay bay = rn.getShuttles().getBays().get(0);

        assertEquals(3, bay.getLaunchTubeCount());
        assertEquals("three Stingers through the tubes and one shuttle through the door",
                4, launchedInOneImpulse(bay, 10));
    }

    /**
     * J1.543: "Recovery can only be conducted through the standard shuttle bay hatch." A
     * bay whose tubes are all free still cannot land anything once its door is spent.
     */
    @Test
    public void tubesCannotRecoverSoASpentDoorStopsLandings() {
        ShuttleBay bay = bayWith(3, stinger(1));

        bay.markUsed(10);   // something landed, or the door was otherwise used
        assertEquals("the door is spent", 0, bay.getAvailableHatchCount(10));
        assertEquals("but the tubes are not", 3, bay.getAvailableTubeCount(10));
        assertFalse("and a recovery may not borrow one (J1.541)", bay.claimHatch(10));
    }

    /** With the door spent, a fighter can still go out — a tube launches, it just cannot land. */
    @Test
    public void aSpentDoorDoesNotStopATubeLaunching() {
        ShuttleBay bay = bayWith(3, stinger(1), stinger(2), stinger(3), stinger(4));

        bay.markUsed(10);
        assertTrue("a fighter still has three tubes", bay.canLaunch(stinger(9), 10));
        assertEquals("three tubes go, the fourth fighter has no door to use",
                3, launchedInOneImpulse(bay, 10));
    }
}
