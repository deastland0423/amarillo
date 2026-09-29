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
    // Erratic Maneuvers (C10.11/C10.12) — the allocation line that makes EM reachable
    // -------------------------------------------------------------------------

    @Test
    void erraticManeuvers_atTheFullPrice_isBought() {
        ActionRequest req = allocate("USS Enterprise");
        req.setErraticManeuvers(fed.getPerformanceData().getErraticCost());

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
        assertTrue(fed.hasPaidForEm(), "paying the full cost must buy the right to announce EM");
    }

    @Test
    void erraticManeuvers_shortOfThePrice_isRefusedRatherThanWasted() {
        // C10.11 is a flat price. Accepting a partial payment would silently burn the
        // energy and still leave the ship unable to announce EM.
        ActionRequest req = allocate("USS Enterprise");
        req.setErraticManeuvers(fed.getPerformanceData().getErraticCost() - 1);

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("C10.11"), result.getMessage());
        assertFalse(fed.hasPaidForEm());
    }

    @Test
    void notBuyingIt_leavesEmUnavailable() {
        ActionResult result = session.executeAction(allocate("USS Enterprise"));

        assertTrue(result.isSuccess(), result.getMessage());
        assertFalse(fed.hasPaidForEm());
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
    void ecmPlusEccm_overGenerationLimit_isRefused() {
        ActionRequest req = allocate("USS Enterprise");
        req.setEcm(4);
        req.setEccm(4);

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("generation limit"), result.getMessage());
    }

    /** D6.310 caps generated EW at six even when the sensor track would allow more circuits. */
    @Test
    void ecmPlusEccm_atSixPointLimit_isAccepted() {
        ActionRequest req = allocate("USS Enterprise");
        req.setEcm(4);
        req.setEccm(2);

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
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

    // -------------------------------------------------------------------------
    // Photon arming dial (E4.21/E4.411) — energy, not a mode
    // -------------------------------------------------------------------------

    /** The name of the Federation ship's first photon tube. */
    private String photonName() {
        for (com.sfb.weapons.Weapon w : fed.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.Photon)
                return w.getName();
        throw new IllegalStateException("FedCA has no photon");
    }

    @Test
    void photonDial_sixPoints_isAccepted() {
        ActionRequest req = allocate("USS Enterprise");
        req.setPhotonArming(java.util.Map.of(photonName(), 6.0));

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
    }

    /** E4.21: the two-point standard charge is mandatory; less buys no arming turn. */
    @Test
    void photonDial_underTwoPoints_isRefused() {
        ActionRequest req = allocate("USS Enterprise");
        req.setPhotonArming(java.util.Map.of(photonName(), 1.0));

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("two points"), result.getMessage());
    }

    /** E4.41: two standard plus four of overload is the most a turn can take. */
    @Test
    void photonDial_overSixPoints_isRefused() {
        ActionRequest req = allocate("USS Enterprise");
        req.setPhotonArming(java.util.Map.of(photonName(), 7.0));

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("at most"), result.getMessage());
    }

    /** Zero is "do not arm" and must be accepted — it discharges the tube (E4.21/E1.24). */
    @Test
    void photonDial_zero_isAcceptedAsDischarge() {
        ActionRequest req = allocate("USS Enterprise");
        req.setPhotonArming(java.util.Map.of(photonName(), 0.0));

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
    }

    /** E4.31: a proximity fuse rides along with the arming and costs nothing. */
    @Test
    void photonDial_twoPointsWithProximity_isAccepted() {
        ActionRequest req = allocate("USS Enterprise");
        req.setPhotonArming(java.util.Map.of(photonName(), 2.0));
        req.setWeaponArming(java.util.Map.of(photonName(), "PROX"));

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
    }

    /** E4.34: proximity and overload cannot be combined. */
    @Test
    void photonDial_proximityWithOverloadEnergy_isRefused() {
        ActionRequest req = allocate("USS Enterprise");
        req.setPhotonArming(java.util.Map.of(photonName(), 4.0));
        req.setWeaponArming(java.util.Map.of(photonName(), "PROX"));

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("proximity"), result.getMessage());
    }

    /** E4.412: a loaded tube is dialled hold-plus-overload, so five is legal on a standard one. */
    @Test
    void photonDial_loadedTube_acceptsHoldPlusOverload() {
        com.sfb.weapons.Photon p = (com.sfb.weapons.Photon) fed.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.Photon).findFirst().orElseThrow();
        p.armWithEnergy(2);
        p.armWithEnergy(2);          // loaded, standard: holds for 1

        ActionRequest req = allocate("USS Enterprise");
        req.setPhotonArming(java.util.Map.of(p.getName(), 5.0));

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
    }

    /** Below the holding cost is not an allocation it can accept (E4.22). */
    @Test
    void photonDial_loadedTube_belowTheHold_isRefused() throws Exception {
        com.sfb.weapons.Photon p = (com.sfb.weapons.Photon) fed.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.Photon).findFirst().orElseThrow();
        p.armWithEnergy(2);
        p.armWithEnergy(2);
        p.holdAndOverload(3);        // now an overload, holding costs 2

        ActionRequest req = allocate("USS Enterprise");
        req.setPhotonArming(java.util.Map.of(p.getName(), 1.0));

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("holding"), result.getMessage());
    }

    /** E4.41: hold plus the overload it can still take, and no more. */
    @Test
    void photonDial_loadedTube_overTheRemainingAllowance_isRefused() {
        com.sfb.weapons.Photon p = (com.sfb.weapons.Photon) fed.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.Photon).findFirst().orElseThrow();
        p.armWithEnergy(2);
        p.armWithEnergy(2);

        ActionRequest req = allocate("USS Enterprise");
        req.setPhotonArming(java.util.Map.of(p.getName(), 6.0));   // 1 hold + 5 of overload

        ActionResult result = session.executeAction(req);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("at most"), result.getMessage());
    }

    // -------------------------------------------------------------------------
    // Deck crews (J4.81) — one pool, two jobs
    // -------------------------------------------------------------------------

    /**
     * Loading a scatter pack has to BOOK the deck crews it uses (FD7.22).
     * <p>
     * The count was kept in a local, so nothing outside this method ever learned the crews
     * were busy. That was invisible until fighter rearming arrived (J4.83), which spends the
     * same pool at end of turn: a carrier could load two packs AND rearm its whole squadron
     * with the crews it had once. J4.81 gives a ship one set of deck crews for every job.
     */
    @Test
    void scatterPackLoading_booksTheDeckCrewsItUses() {
        Ship kzinti = new Ship();
        kzinti.init(com.sfb.samples.KzintiShips.getKzinBC());
        kzinti.setName("KSS Bloodclaw");
        kzinti.setLocation(new Location(20, 12));
        kzinti.setFacing(1);
        game.getShips().add(kzinti);
        game.startTurn();

        // Turn one of its admin shuttles into a pack, the way LOAD_SCATTER_PACK would.
        com.sfb.systemgroups.ShuttleBay bay = kzinti.getShuttles().getBays().get(0);
        com.sfb.objects.shuttles.Shuttle admin = bay.getInventory().get(0);
        com.sfb.objects.shuttles.ScatterPack pack = new com.sfb.objects.shuttles.ScatterPack(admin);
        pack.setName("Pack-1");
        assertTrue(bay.replaceShuttle(admin, pack), "the pack should take the admin's box");

        int crewsBefore = kzinti.getCrew().getAvailableDeckCrews();
        assertEquals(2, crewsBefore, "J4.814: two deck crews on a ship that is not a carrier");

        ActionRequest req = allocate("KSS Bloodclaw");
        req.setScatterPackLoading(java.util.Map.of("Pack-1", java.util.Map.of("TypeI", 2)));

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(2, pack.getPendingSpaces(), "two drone spaces went into the pack");
        assertEquals(0, kzinti.getCrew().getAvailableDeckCrews(),
                "both crews spent the turn loading, so neither is free to rearm a fighter");
    }

    /**
     * J4.8172 caps a shuttle box at two deck crews, and a crew action moves one drone space
     * (FD7.22), so two spaces reach a pack in a turn however many crews are idle. Asking for
     * three gets two.
     * <p>
     * And the third drone must still EXIST. The pull loop took the drone out of the rack and
     * then offered it to the pack, so the moment anything refused one it was dropped on the
     * floor — a limit enforced by destroying ordnance. It is offered first now.
     */
    @Test
    void scatterPackLoading_stopsAtTwoSpacesAndKeepsTheDroneItCannotLoad() {
        Ship kzinti = new Ship();
        kzinti.init(com.sfb.samples.KzintiShips.getKzinBC());
        kzinti.setName("KSS Ripper");
        kzinti.setLocation(new Location(21, 12));
        kzinti.setFacing(1);
        game.getShips().add(kzinti);
        game.startTurn();

        com.sfb.systemgroups.ShuttleBay bay = kzinti.getShuttles().getBays().get(0);
        com.sfb.objects.shuttles.Shuttle admin = bay.getInventory().get(0);
        com.sfb.objects.shuttles.ScatterPack pack =
                new com.sfb.objects.shuttles.ScatterPack(admin);
        pack.setName("Pack-2");
        assertTrue(bay.replaceShuttle(admin, pack));

        // A carrier's worth of deck crews, so the CREW budget is not what stops the loading.
        // With the BC's own two, the two limits coincide at two spaces and the per-turn rule
        // is never reached — which is exactly how a carrier with nine crews slipped past it.
        kzinti.getCrew().addDeckCrews(7);
        assertTrue(kzinti.getCrew().getAvailableDeckCrews() >= 4,
                "the crews must not be the binding limit here");

        int dronesBefore = reloadDronesOn(kzinti);
        assertTrue(dronesBefore >= 3, "fixture needs reloads to draw from");

        ActionRequest req = allocate("KSS Ripper");
        req.setScatterPackLoading(java.util.Map.of("Pack-2", java.util.Map.of("TypeI", 3)));

        ActionResult result = session.executeAction(req);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(2.0, pack.getPendingSpaces(),
                "two spaces is a turn's work for one box (J4.8172)");
        assertEquals(dronesBefore - 2, reloadDronesOn(kzinti),
                "exactly two drones left the racks; the third was never taken out");
    }

    /** Every drone sitting in any of this ship's rack reload sets. */
    private static int reloadDronesOn(Ship ship) {
        int n = 0;
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.DroneRack rack)
                for (java.util.List<com.sfb.objects.Drone> set : rack.getReloads())
                    n += set.size();
        return n;
    }

    // -------------------------------------------------------------------------
    // Squadron EW lending (J4.93/J4.931) — a carrier's SECOND EW pool
    // -------------------------------------------------------------------------

    /**
     * The wire carries per-squadron pools through to the squadrons themselves. Pinned at this
     * tier because it is where this project's rule limits have drifted before: the CAPABLE
     * check and J4.931's point limit deliberately live in core, so a translation that dropped
     * the field would show up as a carrier that silently lends nothing.
     */
    @Test
    void squadronEw_reachesTheSquadron() {
        Ship carrier = new Ship();
        carrier.init(com.sfb.samples.KzintiShips.getKzinBC());
        carrier.setName("KHS Longsword");
        carrier.setLocation(new com.sfb.properties.Location(10, 10));
        carrier.setFacing(1);
        carrier.setCarrierClass(com.sfb.properties.CarrierClass.CAPABLE);
        game.getShips().add(carrier);

        com.sfb.objects.Squadron squadron =
                new com.sfb.objects.Squadron("Gold", carrier);
        carrier.getShuttles().getSquadrons().add(squadron);

        ActionRequest req = allocate("KHS Longsword");
        req.setSquadronEw(java.util.Map.of("Gold", java.util.Map.of("ecm", 4, "eccm", 2)));
        assertTrue(session.executeAction(req).isSuccess());
        carrier.startTurn();   // where an allocation is applied (J4.931 generates it there)

        assertEquals(4, squadron.getCarrierEcm());
        assertEquals(2, squadron.getCarrierEccm());
    }

    /** J4.931/J4.6: a casual carrier's declaration is dropped, not honoured. */
    @Test
    void squadronEw_isRefusedToACasualCarrier() {
        Ship casual = new Ship();
        casual.init(com.sfb.samples.KzintiShips.getKzinBC());
        casual.setName("KHS Casual");
        casual.setLocation(new com.sfb.properties.Location(12, 10));
        casual.setFacing(1);
        casual.setCarrierClass(com.sfb.properties.CarrierClass.CASUAL);
        game.getShips().add(casual);

        com.sfb.objects.Squadron squadron = new com.sfb.objects.Squadron("Gold", casual);
        casual.getShuttles().getSquadrons().add(squadron);

        ActionRequest req = allocate("KHS Casual");
        req.setSquadronEw(java.util.Map.of("Gold", java.util.Map.of("ecm", 4, "eccm", 2)));
        assertTrue(session.executeAction(req).isSuccess());
        casual.startTurn();

        assertFalse(squadron.hasCarrierEw(), "J4.931 excludes casual carriers");
    }

    /** An omitted field leaves the pools alone rather than clearing or inventing anything. */
    @Test
    void squadronEw_isOptional() {
        ActionRequest req = allocate("USS Enterprise");
        assertTrue(session.executeAction(req).isSuccess());
    }
}
