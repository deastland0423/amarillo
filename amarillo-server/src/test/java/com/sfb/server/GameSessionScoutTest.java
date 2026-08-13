package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the scout-channel request-translation layer in GameSession — the ALLOCATE
 * wiring of the ship-level EW pool (G24.211) and the LEND_EW action (G24.21). This tier
 * had zero coverage and, per the project's own history, silently drifts when a rule
 * changes; these pin the wire → Game.assignChannelLend path so drift has something to fail.
 */
class GameSessionScoutTest {

    private static final String HOST = "token-host";

    private GameSession session;
    private Game game;
    private Ship scout;
    private Ship friend;

    @BeforeEach
    void setUp() {
        session = new GameSession("game-1", HOST, "Alice");
        game = session.getGame();

        scout = new Ship();
        scout.init(FederationShips.getFedCa());
        scout.setName("USS De Gama");
        scout.setLocation(new Location(10, 10));
        scout.setFacing(1);
        addChannel(scout, "1");
        addChannel(scout, "2");

        friend = new Ship();
        friend.init(FederationShips.getFedCa());
        friend.setName("USS Friend");
        friend.setLocation(new Location(12, 10));
        friend.setFacing(1);

        game.getShips().add(scout);
        game.getShips().add(friend);
        game.startTurn();
    }

    private void addChannel(Ship ship, String designator) {
        ScoutChannel c = new ScoutChannel();
        c.setDesignator(designator);
        c.setDacHitLocaiton("torp");
        ship.getWeapons().addWeapon(c);
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

    /** Allocate both ships (scout gets the pool + powered channels) so beginImpulses() runs. */
    private void allocateBoth(int scoutEwPoints) {
        ActionRequest a = allocate("USS De Gama");
        a.setScoutEwPoints(scoutEwPoints);
        a.setPoweredChannels(Arrays.asList("1", "2"));
        assertTrue(session.executeAction(a).isSuccess());
        assertTrue(session.executeAction(allocate("USS Friend")).isSuccess());
    }

    private ActionRequest lend(String channel, String target, int ecm, int eccm) {
        ActionRequest req = new ActionRequest();
        req.setType("LEND_EW");
        req.setShipName("USS De Gama");
        req.setPlayerToken(HOST);
        req.setChannelDesignator(channel);
        req.setLendTarget(target);
        req.setLendEcm(ecm);
        req.setLendEccm(eccm);
        return req;
    }

    // -------------------------------------------------------------------------
    // ALLOCATE — the ship-level EW pool (G24.211)
    // -------------------------------------------------------------------------

    @Test
    void allocate_setsTheShipEwPool_andPowersChannels() {
        allocateBoth(6);
        assertEquals(6, scout.getScoutEwPool(), "pool comes from the request");
        assertEquals(6, scout.getScoutEwRemaining(), "all available before any lend");
        assertTrue(scout.getScoutChannels().get(0).isPowered());
        assertTrue(scout.getScoutChannels().get(1).isPowered());
    }

    // -------------------------------------------------------------------------
    // LEND_EW — the aiming action (G24.21)
    // -------------------------------------------------------------------------

    @Test
    void lendEw_toSelf_appliesEcmOnly_droppingEccm() {
        allocateBoth(6);
        ActionResult r = session.executeAction(lend("1", "USS De Gama", 4, 3));
        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(4, scout.getLentEcm());
        assertEquals(0, scout.getLentEccm(), "no ECCM to self (G24.283)");
    }

    @Test
    void lendEw_toFriendlyWithLockOn_appliesBothKinds() {
        allocateBoth(6);
        scout.setActiveFireControl(true); // lending to others needs active FC (G24.161)
        scout.addLockOn(friend); // G24.218
        ActionResult r = session.executeAction(lend("1", "USS Friend", 3, 2));
        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(3, friend.getLentEcm());
        assertEquals(2, friend.getLentEccm());
    }

    @Test
    void lendEw_overTheGeneratedPool_isRefused() {
        allocateBoth(3);
        ActionResult r = session.executeAction(lend("1", "USS De Gama", 5, 0));
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("EW points left"), r.getMessage());
    }

    @Test
    void lendEw_overSixOnOneChannel_isRefused() {
        allocateBoth(12);
        ActionResult r = session.executeAction(lend("1", "USS De Gama", 7, 0));
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("at most 6"), r.getMessage());
    }

    @Test
    void lendEw_zeroClearsAnExistingLend() {
        allocateBoth(6);
        assertTrue(session.executeAction(lend("1", "USS De Gama", 4, 0)).isSuccess());
        assertEquals(4, scout.getLentEcm());
        assertTrue(session.executeAction(lend("1", "USS De Gama", 0, 0)).isSuccess());
        assertEquals(0, scout.getLentEcm(), "0/0 clears the lend");
    }

    // -------------------------------------------------------------------------
    // BREAK_LOCKON — breaking an enemy drone's lock-on (G24.22)
    // -------------------------------------------------------------------------

    private com.sfb.objects.Drone addEnemyDrone(String name, int x, int y) {
        com.sfb.objects.Drone d = new com.sfb.objects.Drone(com.sfb.objects.DroneType.TypeI);
        d.setName(name);
        d.setLocation(new Location(x, y));
        game.getSeekers().add(d);
        return d;
    }

    private ActionRequest breakLockOn(String channel, String drone) {
        ActionRequest req = new ActionRequest();
        req.setType("BREAK_LOCKON");
        req.setShipName("USS De Gama");
        req.setPlayerToken(HOST);
        req.setChannelDesignator(channel);
        req.setTargetName(drone);
        return req;
    }

    @Test
    void breakLockOn_withValidSetup_resolvesTheAttempt() {
        allocateBoth(0); // channels powered; no lending pool needed here
        scout.setActiveFireControl(true);
        com.sfb.objects.Drone drone = addEnemyDrone("Drone-1", 11, 10);
        scout.addLockOn(drone);

        ActionResult r = session.executeAction(breakLockOn("1", "Drone-1"));
        assertTrue(r.isSuccess(), r.getMessage()); // the attempt resolved (broke or failed)
    }

    @Test
    void breakLockOn_withoutLockOn_isRefused() {
        allocateBoth(0);
        scout.setActiveFireControl(true);
        addEnemyDrone("Drone-1", 11, 10); // no lock-on to it

        ActionResult r = session.executeAction(breakLockOn("1", "Drone-1"));
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("lock-on"), r.getMessage());
    }

    // -------------------------------------------------------------------------
    // IDENTIFY_SEEKER — identifying an enemy seeker with a channel + lab (G24.25)
    // -------------------------------------------------------------------------

    private ActionRequest identify(String channel, String seeker) {
        ActionRequest req = new ActionRequest();
        req.setType("IDENTIFY_SEEKER");
        req.setShipName("USS De Gama");
        req.setPlayerToken(HOST);
        req.setChannelDesignator(channel);
        req.setTargetName(seeker);
        return req;
    }

    @Test
    void identifySeeker_withValidSetup_resolvesTheAttempt() {
        allocateBoth(0);
        scout.setActiveFireControl(true);
        scout.getLabs().init(java.util.Map.of("lab", 2));
        com.sfb.objects.Drone drone = addEnemyDrone("Drone-1", 11, 10);
        scout.addLockOn(drone);

        ActionResult r = session.executeAction(identify("1", "Drone-1"));
        assertTrue(r.isSuccess(), r.getMessage()); // resolved (identified or failed)
    }

    @Test
    void identifySeeker_withoutALab_isRefused() {
        allocateBoth(0);
        scout.setActiveFireControl(true);
        scout.getLabs().init(java.util.Map.of("lab", 0)); // no labs
        com.sfb.objects.Drone drone = addEnemyDrone("Drone-1", 11, 10);
        scout.addLockOn(drone);

        ActionResult r = session.executeAction(identify("1", "Drone-1"));
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("lab"), r.getMessage());
    }
}
