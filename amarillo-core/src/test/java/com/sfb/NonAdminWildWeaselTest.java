package com.sfb;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.GASShuttle;
import com.sfb.objects.shuttles.HTSShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * J3.18: any non-fighter shuttle may be charged and launched as a Wild Weasel.
 * <p>
 * The eligibility answer has been right for a while — {@code canBecomeWildWeasel()} reads
 * the catalogue — but nothing downstream believed it. The charging state lived on
 * AdminShuttle, so every place that used it tested for that class first, and a GAS or an
 * HTS was refused however loudly the rules and the data agreed it qualified. Worse, once
 * the gates were changed to ask the capability, the casts beside them stayed: a GAS would
 * have passed the test and then thrown ClassCastException.
 * <p>
 * So this exercises the whole path — charge, ready, launch, and the resulting weasel —
 * with a shuttle that is NOT an admin shuttle. ShuttleRoleEligibilityTest covers the
 * eligibility answers themselves; nothing there would notice a downstream refusal.
 */
public class NonAdminWildWeaselTest {

    private Game game;
    private Ship launcher;
    private ShuttleBay bay;

    @Before
    public void setUp() {
        game = new Game();

        launcher = new Ship();
        launcher.init(KlingonShips.getD7());
        launcher.setName("IKS Fury");
        launcher.setLocation(new Location(10, 10));
        launcher.setFacing(1);
        launcher.setSpeedPreviousTurn(31);
        launcher.setSpeedTwoTurnsAgo(31);
        game.getShips().add(launcher);

        bay = launcher.getShuttles().getBays().get(0);

        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(launcher.getLifeSupportCost());
        e.setFireControl(launcher.getFireControlCost());
        e.setActivateShields(launcher.getActiveShieldCost());
        e.setWarpMovement(0.0);   // speed 0, so the J3.131 maneuver-rate cap is not in play
        game.submitAllocation(launcher, e);
    }

    /** Put a named, fully charged shuttle into the bay. */
    private Shuttle charged(Shuttle s, String name) {
        s.setName(name);
        assertFalse("the bay needs a space", bay.getSpaces().isEmpty());
        bay.getSpaces().get(0).setShuttle(s);
        s.incrementWwCharge();
        s.incrementWwCharge();
        return s;
    }

    /** Advance until this bay will accept a launch (one every two impulses). */
    private void readyToLaunch() {
        for (int guard = 0; guard < 400; guard++) {
            if (game.canLaunchThisPhase() && bay.canLaunch(game.getAbsoluteImpulse()))
                return;
            game.advancePhase();
        }
        fail("bay never became ready to launch");
    }

    // ---------------------------------------------------------------- charging

    @Test
    public void aGasShuttleCanBeChargedToReady() {
        // J3.12's two turns of charging. This state used to live on AdminShuttle, which is
        // why a GAS could not reach it at all.
        GASShuttle gas = new GASShuttle();
        assertFalse("not charged yet", gas.isWwReady());
        gas.incrementWwCharge();
        assertFalse("one turn is not enough (J3.12)", gas.isWwReady());
        gas.incrementWwCharge();
        assertTrue("two turns of charging makes it ready (J3.12)", gas.isWwReady());
    }

    @Test
    public void chargeStopsAtReadyAndResets() {
        HTSShuttle hts = new HTSShuttle();
        for (int i = 0; i < 5; i++)
            hts.incrementWwCharge();
        assertEquals("charge is capped at the two turns J3.12 asks for",
                2, hts.getWwChargeCount());
        hts.resetWwCharge();
        assertEquals("and a turn without charging loses it", 0, hts.getWwChargeCount());
        assertFalse(hts.isWwReady());
    }

    // ---------------------------------------------------------------- launching

    @Test
    public void aChargedGasShuttleLaunchesAsAWildWeasel() {
        charged(new GASShuttle(), "IKS Fury-GAS-1");
        readyToLaunch();

        ActionResult r = game.launchWildWeasel(launcher, "IKS Fury-GAS-1", 1, 0);

        assertTrue("J3.18 admits any non-fighter shuttle: " + r.getMessage(), r.isSuccess());
        assertNotNull("and the ship should now have an active weasel",
                launcher.getActiveWildWeasel());
    }

    @Test
    public void aChargedHtsShuttleLaunchesAsAWildWeasel() {
        charged(new HTSShuttle(), "IKS Fury-HTS-1");
        readyToLaunch();

        ActionResult r = game.launchWildWeasel(launcher, "IKS Fury-HTS-1", 1, 0);

        assertTrue(r.getMessage(), r.isSuccess());
        assertNotNull(launcher.getActiveWildWeasel());
    }

    /**
     * The discriminating assertion. An HTS has twice an admin shuttle's hull, so a weasel
     * that quietly assumed an admin shuttle would still launch and still pass every test
     * above — it would just be the wrong craft, six hull instead of twelve, easier to
     * shoot down than the shuttle the player actually spent.
     */
    @Test
    public void theWeaselKeepsTheHullOfTheShuttleItWasBuiltFrom() {
        HTSShuttle base = new HTSShuttle();
        int baseHull = base.getHull();
        assertNotEquals("fixture is only meaningful if an HTS differs from an admin shuttle",
                new AdminShuttle().getHull(), baseHull);

        charged(base, "IKS Fury-HTS-1");
        readyToLaunch();
        assertTrue(game.launchWildWeasel(launcher, "IKS Fury-HTS-1", 1, 0).isSuccess());

        WildWeaselShuttle ww = launcher.getActiveWildWeasel();
        assertEquals("the weasel IS that HTS, so it keeps its hull (J3.18)",
                baseHull, ww.getHull());
    }

    /** And the name still reports the craft, not the role (see LaunchAnonymityTest). */
    @Test
    public void aGasBuiltWeaselIsNamedForTheCraft() {
        charged(new GASShuttle(), "IKS Fury-GAS-1");
        readyToLaunch();
        assertTrue(game.launchWildWeasel(launcher, "IKS Fury-GAS-1", 1, 0).isSuccess());

        String name = launcher.getActiveWildWeasel().getName();
        assertTrue("a GAS-built weasel should read as a GAS: " + name,
                name.matches("^IKS Fury-GAS-[0-9]+$"));
        assertFalse("and must not announce itself", name.toLowerCase().contains("weasel"));
    }

    // ---------------------------------------------------------------- fighters still barred

    @Test
    public void aFighterCannotBeChargedOrLaunched() {
        // J4.41 bars fighters, and opening the gate must not have opened it for them.
        Stinger1 fighter = new Stinger1();
        assertFalse("J4.41", fighter.canBecomeWildWeasel());

        charged(fighter, "Alpha 1");   // charge it anyway, to prove the launch path refuses
        readyToLaunch();

        ActionResult r = game.launchWildWeasel(launcher, "Alpha 1", 1, 0);

        assertFalse("a fighter must not launch as a weasel (J4.41): " + r.getMessage(),
                r.isSuccess());
        assertNull(launcher.getActiveWildWeasel());
    }
}
