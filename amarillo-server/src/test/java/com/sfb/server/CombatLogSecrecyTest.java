package com.sfb.server;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.DroneRack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared combat log is a second view of the game, and it must keep the same secrets the
 * map view does.
 *
 * Everything on the map goes through GameStateDto, where redactForEnemy decides what an
 * opponent may see and a drift-guard test fails the build on any unclassified field. The log
 * has no such gate: it is free text, appended from thirty-odd places, and a message written
 * for the ACTOR reaches everyone the moment someone passes it to appendCombatLog.
 *
 * Two have escaped that way in playtests — a drone's remaining hull after it was fired on,
 * and then "IKV Ambush launched TypeIVM drone at USS Enterprise", which hands over for free
 * what G4.2 says costs a lab to identify.
 *
 * So this is the log's drift guard: play the actions that carry secrets and assert the words
 * never appear. It cannot enumerate fields the way the DTO guard does, so it enumerates
 * SECRETS instead — the vocabulary that must not show up however the sentence is phrased.
 */
class CombatLogSecrecyTest {

    private GameSession session;
    private Game game;
    private Ship fed;
    private Ship klingon;
    private String host;
    private String p2;

    @BeforeEach
    void setUp() {
        GameSessionService service = new GameSessionService();
        session = service.createSession("Alice");
        host = session.getPlayers().keySet().iterator().next();
        p2 = service.joinSession(session.getId(), "Bob");
        game = session.getGame();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(10, 8));
        klingon.setFacing(13);

        game.getShips().add(fed);
        game.getShips().add(klingon);

        Player alice = new Player();
        alice.setName("Alice");
        alice.getPlayerUnits().add(fed);
        session.getPlayers().get(host).setCorePlayer(alice);
        Player bob = new Player();
        bob.setName("Bob");
        bob.getPlayerUnits().add(klingon);
        session.getPlayers().get(p2).setCorePlayer(bob);

        game.startTurn();
        game.submitAllocation(fed, allocationFor(fed));
        game.submitAllocation(klingon, allocationFor(klingon));
        advanceTo(Game.ImpulsePhase.ACTIVITY);

        klingon.setActiveFireControl(true);
        klingon.addLockOn(fed);
    }

    private Energy allocationFor(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(4.0);
        return e;
    }

    private void advanceTo(Game.ImpulsePhase phase) {
        for (int guard = 0; guard < 400 && game.getCurrentPhase() != phase; guard++)
            game.advancePhase();
        assertEquals(phase, game.getCurrentPhase());
    }

    // -------------------------------------------------------------------------

    /**
     * Words the shared log may never contain.
     *
     * Drone types are what a lab identification buys (G4.231/G4.233); a plasma's type and
     * whether it is a bluff are explicitly NOT bought even then (G4.232); and a shuttle's
     * role is never disclosed, which is the whole reason a suicide shuttle and a scatterpack
     * leave a bay looking identical.
     *
     * A wild weasel is deliberately absent from this list: J3.0 makes it public at launch,
     * its interference announcing it, and the log says so on purpose.
     */
    private static List<String> secretsIn(String log) {
        List<String> found = new ArrayList<>();
        for (DroneType dt : DroneType.values())
            if (log.contains(dt.name()))
                found.add("drone type " + dt.name());
        for (String word : new String[] { "pseudo", "Pseudo", "suicide", "Suicide",
                                          "scatterpack", "Scatterpack", "scatter pack" })
            if (log.contains(word))
                found.add("\"" + word + "\"");
        return found;
    }

    private String drain() {
        return String.join(" | ", session.drainCombatLog());
    }

    private String firstRackName() {
        return klingon.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(com.sfb.weapons.Weapon::getName)
                .findFirst().orElseThrow();
    }

    private ActionRequest request(String type, String token) {
        ActionRequest r = new ActionRequest();
        r.setType(type);
        r.setPlayerToken(token);
        return r;
    }

    // -------------------------------------------------------------------------

    /** A drone launched as a direct action. */
    @Test
    void aDirectDroneLaunchNamesNoType() {
        ActionRequest req = request("LAUNCH_DRONE", p2);
        req.setShipName("IKV Saber");
        req.setTargetName("USS Enterprise");
        req.setWeaponNames(List.of(firstRackName()));
        req.setRange(0);          // drone index rides the range field
        req.setFacing(13);
        assertTrue(session.executeAction(req).isSuccess());

        String log = drain();
        // Secrecy first: it is the assertion this class exists for, and a leak should be
        // reported as a leak rather than as the redacted line having gone missing.
        assertEquals(List.of(), secretsIn(log), "log leaked: " + log);
        assertTrue(log.contains("launched a drone"), "the launch is public: " + log);
    }

    /** And through the declaration round, which is how launches actually happen now. */
    @Test
    void aDeclaredDroneLaunchNamesNoType() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", p2));

        ActionRequest commit = request("COMMIT_ACTIVITY_DECLARATION", p2);
        ActionRequest.ActivityOrder o = new ActionRequest.ActivityOrder();
        o.setKind("DRONE");
        o.setShipName("IKV Saber");
        o.setTargetName("USS Enterprise");
        o.setWeaponName(firstRackName());
        o.setFacing(13);
        commit.setActivityOrders(List.of(o));
        assertTrue(session.executeAction(commit).isSuccess());
        assertTrue(session.executeAction(request("PASS_ACTIVITY_DECLARATION", host)).isSuccess());

        String log = drain();
        // Secrecy first: it is the assertion this class exists for, and a leak should be
        // reported as a leak rather than as the redacted line having gone missing.
        assertEquals(List.of(), secretsIn(log), "log leaked: " + log);
        assertTrue(log.contains("launched a drone"), "the launch is public: " + log);
    }

    /** A shuttle leaves a bay without saying what it is for. */
    @Test
    void aShuttleLaunchNamesNoRole() {
        String bayShuttle = klingon.getShuttles().getBays().get(0).getInventory().get(0).getName();
        ActionRequest req = request("LAUNCH_SHUTTLE", p2);
        req.setShipName("IKV Saber");
        req.setAction(bayShuttle);
        req.setSpeed(4);
        req.setRange(13);         // facing rides the range field
        assertTrue(session.executeAction(req).isSuccess());

        String log = drain();
        assertEquals(List.of(), secretsIn(log), "log leaked: " + log);
        assertTrue(log.contains("launched a shuttle") || log.contains("launched a fighter"),
                "the launch is public: " + log);
    }

    /**
     * The other playtest leak, kept from coming back: firing on a drone must not report what
     * is left of it. Hull is REVEALED only on identification, and a shot is not one.
     */
    @Test
    void firingOnADroneDoesNotReportItsCondition() {
        Drone drone = new Drone(DroneType.TypeIV);
        drone.setName("IKV Saber-Drone-1");
        drone.setLocation(new Location(10, 9));
        drone.setFacing(1);
        drone.setController(klingon);
        game.getSeekers().add(drone);
        drain();                                   // clear the setup chatter

        advanceTo(Game.ImpulsePhase.DIRECT_FIRE);
        fed.setActiveFireControl(true);
        fed.addLockOn(drone);

        ActionRequest call = request("CALL_FIRE_DECLARATION", host);
        session.executeAction(call);
        ActionRequest commit = request("COMMIT_FIRE_DECLARATION", host);
        ActionRequest.FireOrder fo = new ActionRequest.FireOrder();
        fo.setShipName("USS Enterprise");
        fo.setTargetName("IKV Saber-Drone-1");
        fo.setWeaponNames(List.of("Phaser1-1"));
        fo.setRange(1);
        fo.setAdjustedRange(1);
        fo.setDirectFire(true);
        commit.setFireOrders(List.of(fo));
        session.executeAction(commit);
        session.executeAction(request("PASS_FIRE_DECLARATION", p2));

        String log = drain();
        assertEquals(List.of(), secretsIn(log), "log leaked: " + log);
        assertFalse(log.contains("hull"), "a shot is not an identification: " + log);
    }
}
