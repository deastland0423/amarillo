package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.SpaceMine;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * J1.621 special recovery procedure: a friendly shuttle held in the ship's
 * tractor beam shuts down (J1.622) and is pulled one hex closer per impulse,
 * boarding through the hatch on arrival (or holding at Range 0 — J1.6213).
 * Releasing the tractor cancels the procedure (J1.6221). Pulled shuttles can
 * trigger mines (J1.6223).
 */
public class RecoveryTest {

    private Game game;
    private Ship fed;
    private Ship klingon;
    private Player fedPlayer;

    @Before
    public void setUp() {
        game = new Game();

        fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");
        Player klingonPlayer = new Player();
        klingonPlayer.setTeamName("Klingons");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setOwner(fedPlayer);
        fed.setActiveFireControl(true);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(30, 20));
        klingon.setFacing(1);
        klingon.setOwner(klingonPlayer);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();

        // Speed 0 both — recovery works regardless
        game.submitAllocation(fed,     makeAllocation(fed));
        game.submitAllocation(klingon, makeAllocation(klingon));
    }

    private Energy makeAllocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(0.0);
        return e;
    }

    private void advanceToActivity() {
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    /** Launch a stock shuttle, park it at the given hex, and tractor it. */
    private Shuttle launchAndHoldAt(int col, int row) {
        advanceToActivity();
        ShuttleBay bay = fed.getShuttles().getBays().get(0);
        Shuttle shuttle = bay.getInventory().get(0);
        assertTrue(game.launchShuttle(fed, bay, shuttle, 0, 1).isSuccess());
        shuttle.setLocation(new Location(col, row));
        fed.getTractors().initForTurn(6);
        fed.getPowerSystems().setBatteryPower(0);
        fed.addLockOn(shuttle);
        assertTrue("Tractor must attach: ",
                game.establishTractor(fed, shuttle.getName(), 1).isSuccess());
        return shuttle;
    }

    /** Full phase cycles until the shuttle is aboard or the guard trips. */
    private void runImpulses(int maxPhases) {
        for (int i = 0; i < maxPhases; i++)
            game.advancePhase();
    }

    // -------------------------------------------------------------------------

    @Test
    public void recovery_pullsOneHexPerImpulseAndBoards() {
        Shuttle shuttle = launchAndHoldAt(10, 7); // range 3 from fed at (10,10)
        ShuttleBay bay = fed.getShuttles().getBays().get(0);
        int freeBefore = bay.getEmptySpaceCount();

        Game.ActionResult r = game.beginShuttleRecovery(fed, shuttle.getName());
        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(shuttle.isBeingRecovered());

        // ~3 pulls + hatch cycle: give it 6 impulses of phases
        runImpulses(24);

        assertFalse("Shuttle must be aboard", game.getActiveShuttles().contains(shuttle));
        assertTrue("Back in the bay", bay.getInventory().contains(shuttle));
        assertEquals(freeBefore - 1, bay.getEmptySpaceCount());
        assertFalse(shuttle.isBeingRecovered());
        assertFalse("Link released on boarding", shuttle.isTractored());
        assertNull(shuttle.getLocation());
    }

    @Test
    public void recovery_requiresExistingTractorLink() {
        advanceToActivity();
        ShuttleBay bay = fed.getShuttles().getBays().get(0);
        Shuttle shuttle = bay.getInventory().get(0);
        assertTrue(game.launchShuttle(fed, bay, shuttle, 0, 1).isSuccess());

        Game.ActionResult r = game.beginShuttleRecovery(fed, shuttle.getName());

        assertFalse("Recovery without a tractor must be refused", r.isSuccess());
        assertTrue(r.getMessage().contains("tractor"));
    }

    @Test
    public void recovery_shutsDownTheShuttle() {
        Shuttle shuttle = launchAndHoldAt(10, 7);
        assertTrue(game.beginShuttleRecovery(fed, shuttle.getName()).isSuccess());

        assertFalse("Recovered shuttle is not movable (J1.622)",
                game.canMoveShuttleThisImpulse(shuttle));
        Game.ActionResult chaff = game.dropChaff(shuttle);
        assertFalse("Recovered shuttle cannot drop chaff (J1.622)", chaff.isSuccess());
        assertTrue(chaff.getMessage().contains("J1.622"));
    }

    @Test
    public void recovery_cancelledByReleasingTractor() {
        Shuttle shuttle = launchAndHoldAt(10, 7);
        assertTrue(game.beginShuttleRecovery(fed, shuttle.getName()).isSuccess());

        assertTrue(game.releaseTractor(fed, shuttle.getName()).isSuccess());

        assertFalse("Release ends the procedure (J1.6221)", shuttle.isBeingRecovered());
        Location before = shuttle.getLocation();
        runImpulses(8);
        assertEquals("No further pulls after cancel", before, shuttle.getLocation());
        assertTrue(game.getActiveShuttles().contains(shuttle));
    }

    @Test
    public void recovery_holdsAtRangeZeroWhenHatchBusy() {
        Shuttle shuttle = launchAndHoldAt(10, 9); // range 1 — arrives on first pull
        assertTrue(game.beginShuttleRecovery(fed, shuttle.getName()).isSuccess());

        // Jam the hatch right before the arrival impulse
        ShuttleBay bay = fed.getShuttles().getBays().get(0);
        bay.markUsed(game.getAbsoluteImpulse());

        runImpulses(4); // one impulse: pull into hex, hatch busy → hold
        assertTrue("Held at Range 0 while the hatch cycles (J1.6213)",
                game.getActiveShuttles().contains(shuttle));
        assertEquals(fed.getLocation(), shuttle.getLocation());
        assertTrue(shuttle.isBeingRecovered());

        runImpulses(8); // hatch ready — aboard
        assertFalse(game.getActiveShuttles().contains(shuttle));
        assertTrue(bay.getInventory().contains(shuttle));
    }

    @Test
    public void recovery_pulledShuttleCanTriggerMines() {
        Shuttle shuttle = launchAndHoldAt(10, 7);
        // Armed T-bomb beside the pull path: shuttle (10,7)→(10,8) enters range 1
        SpaceMine mine = SpaceMine.createTBomb(klingon, 0, true, false);
        mine.setLocation(new Location(10, 9));
        mine.tryActivate(2, 9);
        game.getMines().add(mine);

        assertTrue(game.beginShuttleRecovery(fed, shuttle.getName()).isSuccess());
        // The pull happens at the NEXT Movement resolution — cycle one impulse
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < 4; i++)
            all.append(game.advancePhase().getMessage()).append('\n');

        assertTrue("Mine must roll detection against the pulled shuttle (J1.6223): " + all,
                all.toString().contains("tBomb detection"));
    }
}
