package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.OrionShips;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ALLOCATE request-translation layer in GameSession — the
 * validations that live between the wire format and Game.submitAllocation.
 * This layer had zero coverage until a stale per-beam cap on tractor energy
 * (pre-G7.6 model) survived two rules revisions and surfaced in live play;
 * these tests pin the corrected behaviors so drift has something to fail.
 */
class GameSessionAllocateTest {

    private static final String HOST = "token-host";

    private GameSession session;
    private Game game;
    private Ship fed;
    private Ship klingon;

    @BeforeEach
    void setUp() {
        session = new GameSession("game-1", HOST, "Alice");
        game = session.getGame();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(30, 20));
        klingon.setFacing(1);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
    }

    private ActionRequest allocate(String shipName) {
        ActionRequest req = new ActionRequest();
        req.setType("ALLOCATE");
        req.setShipName(shipName);
        req.setPlayerToken(HOST);
        req.setSpeed(0);
        req.setShieldMode("ACTIVE");
        return req;
    }

    // -------------------------------------------------------------------------
    // Tractor pool (G7.15 / G7.6) — the bug that motivated this suite
    // -------------------------------------------------------------------------

    @Test
    void tractorEnergy_beyondBeamCount_isAccepted() {
        // 3 beams; a single range-3 grab costs 3 energy per effective point
        ActionRequest req = allocate("USS Enterprise");
        req.setTractorEnergy(6);

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(6, fed.getTractors().getTotalTractorEnergy());
    }

    @Test
    void tractorEnergy_withNoFunctionalBeams_isRefused() {
        fed.getTractors().destroyBeam(1);
        fed.getTractors().destroyBeam(2);
        fed.getTractors().destroyBeam(3);
        ActionRequest req = allocate("USS Enterprise");
        req.setTractorEnergy(2);

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("no functional tractor beams"),
                result.getMessage());
    }

    // -------------------------------------------------------------------------
    // Other translation-layer validations, pinned against drift
    // -------------------------------------------------------------------------

    @Test
    void speed_beyondWarpCapacity_isRefused() {
        ActionRequest req = allocate("USS Enterprise");
        req.setSpeed(30);
        req.setHetEnergy(20); // movement + HET reserve must fit in the warp engines

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("HET reserve"), result.getMessage());
    }

    @Test
    void ecmPlusEccm_overSensorRating_isRefused() {
        ActionRequest req = allocate("USS Enterprise");
        req.setEcm(4);
        req.setEccm(4);

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("sensor rating"), result.getMessage());
    }

    @Test
    void ecmEccm_validMix_landsOnShipCircuits() {
        ActionRequest req = allocate("USS Enterprise");
        req.setEcm(3);
        req.setEccm(2);

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(3, fed.getEcmAllocated());
        assertEquals(2, fed.getEccmAllocated());
    }

    @Test
    void ecmAllocation_needingLockedCircuitFlip_isRefused() {
        // Commit all six circuits to ECCM one impulse before EA — they are
        // mode-locked for 8 impulses (D6.312/D6.316), so an all-ECM allocation
        // cannot be satisfied yet
        assertNull(fed.allocateEw(6, 0, 1));
        assertNull(fed.adjustEw(0, 6, 1));

        ActionRequest req = allocate("USS Enterprise");
        req.setEcm(6);

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("D6.316"), result.getMessage());
    }

    @Test
    void warpTacticalManeuver_whileMoving_isRefused() {
        ActionRequest req = allocate("USS Enterprise");
        req.setSpeed(5);
        req.setWarpTacticalTurns(1);

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Tactical"), result.getMessage());
    }

    @Test
    void plainAllocation_succeeds() {
        ActionResult result = session.executeAction(allocate("USS Enterprise"));
        assertTrue(result.isSuccess(), result.getMessage());
    }

    @Test
    void allocation_forUnknownShip_isRefused() {
        ActionResult result = session.executeAction(allocate("USS Nonexistent"));
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not found"), result.getMessage());
    }

    // -------------------------------------------------------------------------
    // Phaser capacitor — partial recharge (E1.x)
    // -------------------------------------------------------------------------

    @Test
    void capacitorCharge_partialAmount_isApplied() throws Exception {
        com.sfb.systemgroups.Weapons w = fed.getWeapons();
        w.drainPhaserCapacitor(w.getPhaserCapacitorEnergy()); // empty it — guaranteed room
        assertTrue(w.getAvailablePhaserCapacitor() >= 1.5, "fed has capacitor capacity");

        ActionRequest req = allocate("USS Enterprise");
        req.setCapacitorCharge(1.5);
        session.executeAction(req);
        session.executeAction(allocate("IKV Saber")); // both allocated → impulses begin, charge applies

        assertEquals(1.5, w.getPhaserCapacitorEnergy(), 1e-9,
                "only the requested partial amount is charged");
    }

    @Test
    void capacitorCharge_overRequest_isClampedToCapacity() throws Exception {
        com.sfb.systemgroups.Weapons w = fed.getWeapons();
        w.drainPhaserCapacitor(w.getPhaserCapacitorEnergy());
        double max = w.getAvailablePhaserCapacitor();

        ActionRequest req = allocate("USS Enterprise");
        req.setCapacitorCharge(999.0); // more than the capacitor can hold
        session.executeAction(req);
        session.executeAction(allocate("IKV Saber"));

        assertEquals(max, w.getPhaserCapacitorEnergy(), 1e-9,
                "over-request fills to capacity, not beyond (no lost charge)");
    }

    // -------------------------------------------------------------------------
    // Engine doubling (G15.2) — Orion warships only (G15.28)
    // -------------------------------------------------------------------------

    @Test
    void engineDoubling_byNonOrion_isRefused() {
        ActionRequest req = allocate("USS Enterprise");
        req.setDoubleLwarp(true);

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("double its engines"), result.getMessage());
    }

    @Test
    void engineDoubling_byOrion_isAccepted() {
        Ship orion = new Ship();
        orion.init(OrionShips.getLr());
        orion.setName("Lady Luck");
        orion.setLocation(new Location(15, 15));
        orion.setFacing(1);
        game.getShips().add(orion);
        game.startTurn();

        ActionRequest req = allocate("Lady Luck");
        req.setDoubleLwarp(true);
        req.setDoubleRwarp(true);

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
        assertTrue(orion.getPowerSystems().isAnyEngineDoubled());
    }
}
