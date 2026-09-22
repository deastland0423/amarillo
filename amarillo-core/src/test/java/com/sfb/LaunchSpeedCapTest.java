package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.ShuttleBay;

/**
 * A shuttle launches at anything up to ITS OWN maximum speed, and a wild weasel or suicide
 * shuttle is bounded by the speed of the shuttle it was built from (owner's ruling
 * 2026-09-21) — not by a figure written into the launch code.
 * <p>
 * The three launch paths had three different answers. The plain shuttle capped at
 * effectiveMaxSpeed, the suicide shuttle at getMaxSpeed (so a point of speed committed to
 * erratic maneuvers was ignored, C10.13), and the weasel at a hardcoded 6.
 * <p>
 * The 6 was accidentally right: every non-fighter shuttle in the catalogue moves at 6, and
 * J3.18 admits only non-fighters, so nothing in the game today is misjudged by it. What it
 * was not is a statement of the rule, and the moment a faster non-fighter shuttle is added it
 * would have silently capped it. Erratic maneuvers are what make the difference testable now.
 */
public class LaunchSpeedCapTest {

    private Game game;
    private Ship ship;
    private Ship target;
    private ShuttleBay bay;

    @Before
    public void setUp() {
        game = new Game();

        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);

        target = new Ship();
        target.init(KlingonShips.getD7());
        target.setName("IKV Saber");
        target.setLocation(new Location(10, 8));
        target.setFacing(13);

        game.getShips().add(ship);
        game.getShips().add(target);

        ship.setActiveFireControl(true);
        ship.addLockOn(target);          // a suicide shuttle launch wants one (D6.121)

        bay = ship.getShuttles().getBays().get(0);
        game.advancePhase();                        // MOVEMENT -> ACTIVITY
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    private Shuttle putInBay(Shuttle s) {
        s.setName("SH-1");
        bay.replaceShuttle(bay.getInventory().get(0), s);
        return s;
    }

    /** The one shuttle now in space. Launching renames it, so the name is no way to find it. */
    private Shuttle launched() {
        assertEquals("exactly one shuttle should be out", 1, game.getActiveShuttles().size());
        return game.getActiveShuttles().get(0);
    }

    /** The premise: every non-fighter shuttle in the catalogue moves at six. */
    @Test
    public void anAdminShuttleMovesAtSix() {
        assertEquals(6, new AdminShuttle().getMaxSpeed());
    }

    @Test
    public void aShuttleCannotLaunchAboveItsOwnMaximum() {
        Shuttle s = putInBay(new AdminShuttle());

        ActionResult r = game.launchShuttle(ship, bay, s, 20, 1);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("capped at its own maximum, not the 20 requested", 6, launched().getSpeed());
    }

    /**
     * C10.13: a point of speed committed to erratic maneuvers comes off the maximum, and the
     * commitment binds for the whole turn. So the cap is five, and asking for six gets five.
     */
    @Test
    public void erraticManeuversTakeAPointOffAShuttlesCap() {
        Shuttle s = putInBay(new AdminShuttle());
        s.commitEmSpeed();
        assertEquals(5, s.effectiveMaxSpeed());

        ActionResult r = game.launchShuttle(ship, bay, s, 6, 1);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(5, launched().getSpeed());
    }

    /**
     * The suicide shuttle used to be capped by its raw maximum, so this launch went out at
     * six while an ordinary shuttle in the same state was held to five.
     */
    @Test
    public void aSuicideShuttleIsBoundedTheSameWay() {
        SuicideShuttle ss = new SuicideShuttle(new AdminShuttle());
        putInBay(ss);
        ss.arm(3);
        ss.arm(3);
        ss.arm(3);
        ss.commitEmSpeed();
        assertEquals(5, ss.effectiveMaxSpeed());

        ActionResult r = game.launchSuicideShuttle(ship, bay, ss, target, 1, 6);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("the same cap an ordinary shuttle gets", 5, ss.getSpeed());
    }

    /**
     * And the weasel, which used to be held to a 6 written into the launch code — a figure
     * that knew nothing about the shuttle being charged.
     */
    @Test
    public void aWildWeaselIsBoundedByTheShuttleItWasBuiltFrom() {
        AdminShuttle base = new AdminShuttle();
        putInBay(base);
        base.commitEmSpeed();
        assertEquals(5, base.effectiveMaxSpeed());
        base.incrementWwCharge();                  // two impulses of charge make it ready
        base.incrementWwCharge();
        assertTrue("premise: charged and ready", base.isWwReady());
        ship.setSpeed(0);                           // J3.131: launch needs speed 4 or less

        ActionResult r = game.launchWildWeasel(ship, "SH-1", 1, 6);

        assertTrue(r.getMessage(), r.isSuccess());
        WildWeaselShuttle ww = (WildWeaselShuttle) game.getActiveShuttles().stream()
                .filter(s -> s instanceof WildWeaselShuttle)
                .findFirst().orElseThrow(() -> new AssertionError("no weasel launched"));
        assertEquals("the charged shuttle's cap, not a number in the launch code",
                5, ww.getSpeed());
    }
}
