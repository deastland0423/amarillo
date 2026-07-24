package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * D6.37 EW gate on tractors and transporters: per-action die + net ECM shift,
 * over six fails; friendly units (D6.373), tractor-linked pairs (G7.412), and
 * zero-shift matchups never roll. Plus G7.412's automatic lock-on while a
 * tractor link is attached.
 */
public class D637Test {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();
        fed = buildShip(FederationShips.getFedCa(), "USS Enterprise", 10, 10);
        klingon = buildShip(KlingonShips.getD7(), "IKV Saber", 11, 10);
        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
    }

    private Ship buildShip(java.util.Map<String, Object> spec, String name, int col, int row) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(col, row));
        s.setFacing(1);
        s.setSpeedPreviousTurn(31);
        s.setSpeedTwoTurnsAgo(31);
        return s;
    }

    // -------------------------------------------------------------------------
    // Shift computation (D6.34 via d637Shift)
    // -------------------------------------------------------------------------

    @Test
    public void shift_targetEcmThroughChart() {
        klingon.setEcmAllocated(6);
        assertEquals(2, game.d637Shift(fed, klingon)); // ⌊√6⌋
        klingon.setEcmAllocated(3);
        assertEquals(1, game.d637Shift(fed, klingon)); // ⌊√3⌋
    }

    @Test
    public void shift_attackerEccmCancels_whenFcActive() {
        klingon.setEcmAllocated(6);
        fed.setEccmAllocated(6);
        fed.setActiveFireControl(true);
        assertEquals(0, game.d637Shift(fed, klingon));
    }

    @Test
    public void shift_eccmInert_underPassiveFc() {
        klingon.setEcmAllocated(6);
        fed.setEccmAllocated(6);
        fed.setActiveFireControl(false); // D6.32: ECCM needs active FC
        assertEquals(2, game.d637Shift(fed, klingon));
    }

    @Test
    public void shift_zeroBetweenFriendlies() {
        // D6.373/D6.3146: same-team actions ignore generated EW
        Player owner = new Player();
        owner.setTeamName("Alpha");
        fed.setOwner(owner);
        klingon.setOwner(owner);
        klingon.setEcmAllocated(6);
        assertEquals(0, game.d637Shift(fed, klingon));
    }

    @Test
    public void shift_zeroAcrossTractorLink() {
        // G7.412: attached beam = automatic lock-on, both directions
        klingon.setEcmAllocated(6);
        fed.getTractors().initForTurn(5);
        fed.getTractors().linkUnit(klingon);
        assertEquals("holder acting on held unit", 0, game.d637Shift(fed, klingon));
        fed.setEcmAllocated(6);
        assertEquals("held unit acting on holder", 0, game.d637Shift(klingon, fed));
    }

    @Test
    public void roll_notNeededAtShiftZero() {
        assertNull("no ECM → a d6 cannot exceed 6 → no roll", game.rollD637(fed, klingon, "Test"));
    }

    // -------------------------------------------------------------------------
    // Tractor grab through jamming (D6.372 consequences)
    // -------------------------------------------------------------------------

    @Test
    public void jammedTractorGrab_burnsBidAndBeam_cleanGrabOpensAuction() {
        // ECM 6 → shift 2 → the grab fails on a die of 5-6 (1/3). Run fresh
        // duels until both outcomes are observed; 120 tries make missing
        // either astronomically unlikely.
        boolean sawJam = false, sawGrab = false;
        for (int i = 0; i < 120 && !(sawJam && sawGrab); i++) {
            setUp(); // fresh game, ships, turn
            klingon.setEcmAllocated(6);
            fed.getTractors().initForTurn(5);
            fed.addLockOn(klingon);
            fed.setActiveFireControl(true);
            int beamsBefore = fed.getTractors().getBeamsAvailableThisTurn();

            Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 2);
            assertTrue(r.getMessage(), r.isSuccess());

            if (r.getMessage().contains("cannot achieve lock")) {
                sawJam = true;
                assertTrue(r.getMessage().contains("D6.372"));
                assertEquals("bid energy burned (range 1 → ×1)",
                        3, fed.getTractors().getRemainingTractorEnergy());
                assertEquals("beam expended for the turn",
                        beamsBefore - 1, fed.getTractors().getBeamsAvailableThisTurn());
                assertNull("defender is never asked", game.getPendingTractorAuction());
            } else {
                sawGrab = true;
                assertTrue("lock roll logged on success too", r.getMessage().contains("lock achieved (D6.372)"));
                assertTrue(r.getMessage().contains("awaiting defender"));
                assertNotNull(game.getPendingTractorAuction());
                assertEquals("no energy spent until the auction resolves",
                        5, fed.getTractors().getRemainingTractorEnergy());
            }
        }
        assertTrue("both outcomes observed", sawJam && sawGrab);
    }

    // -------------------------------------------------------------------------
    // G7.412 — automatic lock-on while a tractor link is attached
    // -------------------------------------------------------------------------

    @Test
    public void turnStartLockOn_automaticAcrossTractorLink_evenWithDeadSensors() {
        // Destroy fed's sensor track (rating → 0): a normal roll can never
        // succeed, so any lock-on must come from the G7.412 automatic grant
        while (fed.getSpecialFunctions().getSensor() > 0)
            fed.getSpecialFunctions().damageSensor();
        fed.getTractors().initForTurn(5);
        fed.getTractors().linkUnit(klingon);

        game.submitAllocation(fed, makeAllocation(fed));
        game.submitAllocation(klingon, makeAllocation(klingon));

        assertTrue("sensor 0, but the beam makes lock-on automatic (G7.412)",
                fed.hasLockOn(klingon));
    }

    @Test
    public void turnStartLockOn_deadSensorsAndNoLink_neverLocks() {
        while (fed.getSpecialFunctions().getSensor() > 0)
            fed.getSpecialFunctions().damageSensor();

        game.submitAllocation(fed, makeAllocation(fed));
        game.submitAllocation(klingon, makeAllocation(klingon));

        assertFalse("control: sensor 0 without a link cannot lock", fed.hasLockOn(klingon));
    }

    private Energy makeAllocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(4.0);
        return e;
    }
}
