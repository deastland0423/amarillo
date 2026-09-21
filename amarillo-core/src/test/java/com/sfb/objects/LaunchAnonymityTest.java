package com.sfb.objects;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.Player;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A launched shuttle's NAME must not betray what kind of shuttle it is.
 * <p>
 * An opponent watching a shuttle leave a bay should not be able to tell an admin shuttle
 * from a suicide shuttle from a scatter pack — that uncertainty is the whole point of the
 * things, and the DTO works hard to preserve it, rendering an enemy's unreleased pack as
 * a plain shuttle. All of which is undone if the name says "Suicide-1".
 * <p>
 * The name reports the CRAFT: "IKS Fury-Admin-2". That is a visible property — a GAS is
 * plainly not an admin shuttle — so it hides nothing that was hidden. What it must never
 * report is the ROLE, and three shuttles of one type in three different roles read alike.
 * <p>
 * The existing redaction tests cannot catch a regression here, because they build their
 * shuttles by hand and choose the names themselves. They prove the redaction works GIVEN
 * a uniform name; this proves the launch paths actually produce one. That gap is not
 * hypothetical — the same pattern hid a launched scatter pack having no owner at all.
 */
public class LaunchAnonymityTest {

    private Game game;
    private com.sfb.objects.Ship launcher;
    private com.sfb.objects.Ship target;
    private com.sfb.systemgroups.ShuttleBay bay;

    @Before
    public void setUp() {
        game = new Game();
        Player klingon = new Player();
        klingon.setTeamName("Klingon");

        launcher = new com.sfb.objects.Ship();
        launcher.init(KlingonShips.getD7());
        launcher.setName("IKS Fury");
        launcher.setLocation(new Location(10, 10));
        launcher.setFacing(1);
        launcher.setOwner(klingon);
        launcher.setSpeedPreviousTurn(31);
        launcher.setSpeedTwoTurnsAgo(31);
        launcher.setActiveFireControl(true);

        target = new com.sfb.objects.Ship();
        target.init(FederationShips.getFedCa());
        target.setName("USS Enterprise");
        target.setLocation(new Location(10, 14));
        target.setFacing(13);
        target.setSpeedPreviousTurn(31);
        target.setSpeedTwoTurnsAgo(31);

        game.getShips().add(launcher);
        game.getShips().add(target);

        bay = launcher.getShuttles().getBays().get(0);

        game.startTurn();
        game.submitAllocation(launcher, allocation(launcher));
        game.submitAllocation(target, allocation(target));
        launcher.addLockOn(target);
    }

    private com.sfb.systemgroups.Energy allocation(com.sfb.objects.Ship s) {
        com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
        e.setLifeSupport(s.getLifeSupportCost());
        e.setFireControl(s.getFireControlCost());
        e.setActivateShields(s.getActiveShieldCost());
        e.setWarpMovement(0.0);
        return e;
    }

    /** Advance until this bay will accept another launch (one every two impulses). */
    private void readyToLaunch() {
        for (int guard = 0; guard < 400; guard++) {
            if (game.canLaunchThisPhase() && bay.canLaunch(game.getAbsoluteImpulse()))
                return;
            game.advancePhase();
        }
        fail("bay never became ready to launch");
    }

    /**
     * Put a shuttle into the bay. getInventory() builds a fresh list on every call, so
     * adding to it changes nothing — a shuttle lives in a ShuttleSpace.
     */
    private void putInBay(com.sfb.objects.shuttles.Shuttle shuttle) {
        // A D7's bay comes stocked, so take a space rather than looking for a free one —
        // what is under test is the naming, not bay capacity.
        assertFalse("the bay needs at least one space", bay.getSpaces().isEmpty());
        bay.getSpaces().get(0).setShuttle(shuttle);
    }

    /** Every launch from an admin shuttle reads alike, whatever role it is playing. */
    private void assertReadsAsAdminShuttle(String name, String whatItReallyIs) {
        assertTrue(whatItReallyIs + " was named \"" + name + "\" — all of these were built"
                        + " from ADMIN shuttles, so all must read \"<Ship>-Admin-<n>\": the"
                        + " name says what the craft IS and never which role it is playing",
                name.matches("^IKS Fury-Admin-[0-9]+$"));
    }

    @Test
    public void anAdminShuttleASuicideShuttleAndAScatterPackAreNamedAlike() {
        // Plain shuttle.
        AdminShuttle admin = new AdminShuttle();
        admin.setName("Galileo");           // a name of its own, which launch overwrites
        putInBay(admin);
        readyToLaunch();
        ActionResult r1 = game.launchShuttle(launcher, bay, admin, 4, 1);
        assertTrue(r1.getMessage(), r1.isSuccess());
        assertReadsAsAdminShuttle(admin.getName(), "an admin shuttle");

        // Suicide shuttle, fully armed.
        SuicideShuttle suicide = new SuicideShuttle(new AdminShuttle());
        for (int i = 0; i < 3; i++)
            suicide.arm(1);
        assertTrue("fixture needs it armed", suicide.isFullyArmed());
        putInBay(suicide);
        readyToLaunch();
        ActionResult r2 = game.launchSuicideShuttle(launcher, bay, suicide, target, 1, 6);
        assertTrue(r2.getMessage(), r2.isSuccess());
        assertReadsAsAdminShuttle(suicide.getName(), "a suicide shuttle");

        // Scatter pack, loaded.
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        for (int i = 0; i < 4; i++)
            pack.addDrone(new Drone(DroneType.TypeI));
        putInBay(pack);
        readyToLaunch();
        ActionResult r3 = game.launchScatterPack(launcher, bay, pack, target, 1, 6);
        assertTrue(r3.getMessage(), r3.isSuccess());
        assertReadsAsAdminShuttle(pack.getName(), "a scatter pack");

        // And no two of them collide, or orders could not tell them apart.
        assertNotEquals(admin.getName(), suicide.getName());
        assertNotEquals(suicide.getName(), pack.getName());
        assertNotEquals(admin.getName(), pack.getName());
    }

    @Test
    public void aFighterKeepsItsOwnName() {
        // Deliberately different: a fighter is visibly a fighter, so anonymising it would
        // only make a player's own squadron harder to tell apart for no gain.
        com.sfb.objects.shuttles.Stinger1 fighter = new com.sfb.objects.shuttles.Stinger1();
        fighter.setName("Alpha 1");
        putInBay(fighter);
        readyToLaunch();

        ActionResult r = game.launchShuttle(launcher, bay, fighter, 6, 1);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("a fighter keeps the name it was given", "Alpha 1", fighter.getName());
    }

    /**
     * The type follows the craft, so a GAS-built pack does not masquerade as an admin
     * shuttle — and equally does not announce that it is a scatter pack.
     */
    @Test
    public void theNameFollowsTheCraftNotTheRole() {
        ScatterPack fromGas = new ScatterPack(new com.sfb.objects.shuttles.GASShuttle());
        putInBay(fromGas);
        for (int i = 0; i < 4; i++)
            fromGas.addDrone(new Drone(DroneType.TypeI));
        readyToLaunch();

        ActionResult r = game.launchScatterPack(launcher, bay, fromGas, target, 1, 6);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("a GAS-built pack should read as a GAS: " + fromGas.getName(),
                fromGas.getName().matches("^IKS Fury-GAS-[0-9]+$"));
        assertFalse("and must not say what it is carrying",
                fromGas.getName().toLowerCase().contains("scatter"));
    }
}
