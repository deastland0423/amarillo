package com.sfb.server;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /launch-targets: what a ship may send a seeking weapon at.
 * <p>
 * Simpler than its direct-fire twin, but not arc-free: a drone rack sends one any way it
 * likes, while a plasma launcher has an arc, and what that arc constrains is the launch
 * DIRECTION (FP1.3) — the bearing to the target, when no direction is named. So a target
 * astern is still a candidate, with no launcher able to send one there.
 * <p>
 * What is also worth pinning is the split between the two target-dependent rules: the
 * tractor restriction (G7.943) is unconditional and so removes candidates, while the lock-on
 * requirement (D6.121) has an exception for a self-guiding drone under passive fire control
 * (D19.221) and so is only REPORTED. Filtering on the lock-on would hide a legal launch.
 */
class LaunchTargetsEndpointTest {

    private GameController controller;
    private GameSession session;
    private Game game;
    private Ship fed;
    private Ship klingon;
    private String gameId;
    private String HOST;
    private String P2;

    @BeforeEach
    void setUp() {
        GameSessionService service = new GameSessionService();
        controller = new GameController(service, null);

        session = service.createSession("Alice");
        gameId  = session.getId();
        HOST    = session.getPlayers().keySet().iterator().next();
        P2      = service.joinSession(gameId, "Bob");
        game    = session.getGame();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(10, 6));
        klingon.setFacing(13);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);

        Player alice = new Player();
        alice.setName("Alice");
        alice.getPlayerUnits().add(fed);
        session.getPlayers().get(HOST).setCorePlayer(alice);
        Player bob = new Player();
        bob.setName("Bob");
        bob.getPlayerUnits().add(klingon);
        session.getPlayers().get(P2).setCorePlayer(bob);

        game.startTurn();
        game.submitAllocation(fed, allocationFor(fed));
        game.submitAllocation(klingon, allocationFor(klingon));
    }

    private Energy allocationFor(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(4.0);
        return e;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> targetsFor(String token, String attacker) {
        Object body = controller.getLaunchTargets(gameId, token, attacker).getBody();
        assertNotNull(body, "endpoint returned no body");
        return (List<Map<String, Object>>) body;
    }

    private Map<String, Object> row(List<Map<String, Object>> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.get("name"))).findFirst().orElse(null);
    }

    // -------------------------------------------------------------------------

    @Test
    void anEnemyIsListedWithItsRangeAndLockOnState() {
        Map<String, Object> saber = row(targetsFor(HOST, "USS Enterprise"), "IKV Saber");

        assertNotNull(saber);
        assertEquals("SHIP", saber.get("kind"));
        assertEquals(4, saber.get("range"));
        assertNotNull(saber.get("hasLockOn"), "the lock-on fact is reported, not filtered on");
    }

    /**
     * A target astern is still a CANDIDATE — a drone rack has no arc and will send one
     * anywhere. What it is not is a plasma target: the Enterprise carries no launchers at
     * all, and the point of the field is that a ship which does would show an empty list
     * here. See plasmaLaunchers_listOnlyThoseThatCanSendOneThatWay.
     */
    @Test
    void aTargetAsternIsStillACandidateForADrone() {
        klingon.setLocation(new Location(10, 14));   // astern of a ship facing 1

        Map<String, Object> saber = row(targetsFor(HOST, "USS Enterprise"), "IKV Saber");

        assertNotNull(saber, "a drone does not need the target in an arc");
    }

    /**
     * A plasma launcher does have an arc, and it constrains the launch DIRECTION (FP1.3).
     * The Romulan KR's Plasma-G launchers are set to direction 1 only — dead ahead — so a
     * target ahead lists them and a target astern lists none, while remaining a candidate
     * because a drone could still be sent.
     */
    @Test
    void plasmaLaunchers_listOnlyThoseThatCanSendOneThatWay() {
        Ship romulan = new Ship();
        romulan.init(com.sfb.samples.RomulanShips.getRomKr());
        romulan.setName("IRW Gauntlet");
        romulan.setLocation(new Location(10, 10));
        romulan.setFacing(1);
        game.getShips().add(romulan);
        session.getPlayers().get(HOST).getCorePlayer().getPlayerUnits().add(romulan);

        klingon.setLocation(new Location(10, 6));     // dead ahead
        @SuppressWarnings("unchecked")
        List<String> ahead = (List<String>)
                row(targetsFor(HOST, "IRW Gauntlet"), "IKV Saber").get("plasmaLaunchers");
        assertFalse(ahead.isEmpty(), "forward launchers bear on something dead ahead");

        klingon.setLocation(new Location(10, 14));    // dead astern
        Map<String, Object> astern = row(targetsFor(HOST, "IRW Gauntlet"), "IKV Saber");
        assertNotNull(astern, "still a candidate — a drone could go");
        @SuppressWarnings("unchecked")
        List<String> behind = (List<String>) astern.get("plasmaLaunchers");
        assertTrue(behind.isEmpty(), "but no launcher can send one backwards: " + behind);
    }

    @Test
    void ownAndFriendlyShipsAreNotCandidates() {
        Ship kongo = new Ship();
        kongo.init(FederationShips.getFedCa());
        kongo.setName("USS Kongo");
        kongo.setLocation(new Location(10, 9));
        kongo.setFacing(1);
        game.getShips().add(kongo);
        session.getPlayers().get(HOST).getCorePlayer().getPlayerUnits().add(kongo);

        List<Map<String, Object>> rows = targetsFor(HOST, "USS Enterprise");

        assertNull(row(rows, "USS Enterprise"), "not at itself");
        assertNull(row(rows, "USS Kongo"), "and not at its own fleet");
        assertNotNull(row(rows, "IKV Saber"));
    }

    /**
     * G7.943: held in a tractor beam, the holder is the only thing a seeking weapon may be
     * sent at. Unconditional, so everything else stops being a candidate.
     */
    @Test
    void aTractoredShipMayOnlyLaunchAtItsHolder() {
        Ship kali = new Ship();
        kali.init(KlingonShips.getD7());
        kali.setName("IKV Kali");
        kali.setLocation(new Location(11, 10));
        kali.setFacing(13);
        game.getShips().add(kali);
        session.getPlayers().get(P2).getCorePlayer().getPlayerUnits().add(kali);

        assertNotNull(row(targetsFor(HOST, "USS Enterprise"), "IKV Kali"),
                "a candidate before the beam");

        // A real link through the tractor group. establishTractor would open a G7.42 auction
        // that only resolves once the defender answers, which is a lot of ceremony for a
        // test about which targets are offered.
        assertTrue(kali.getTractors().linkUnit(fed, game.getAbsoluteImpulse()));
        assertTrue(fed.isTractored(), "the Enterprise is held");

        List<Map<String, Object>> held = targetsFor(HOST, "USS Enterprise");
        assertNotNull(row(held, "IKV Kali"), "the holder stays");
        assertNull(row(held, "IKV Saber"), "everything else goes (G7.943)");
    }

    @Test
    void unknownShipIsRefused() {
        assertEquals(400, controller.getLaunchTargets(gameId, HOST, "USS Nowhere")
                .getStatusCode().value());
    }

    @Test
    void aShipOffTheMapHasNoTargets() {
        fed.setLocation(null);

        assertTrue(targetsFor(HOST, "USS Enterprise").isEmpty());
    }

    /** The row carries range and lock-on and nothing about the target's insides. */
    @Test
    void rowCarriesNothingHidden() {
        Map<String, Object> saber = row(targetsFor(HOST, "USS Enterprise"), "IKV Saber");

        assertNotNull(saber);
        for (String forbidden : List.of("hull", "maxHull", "targetName", "droneType",
                                        "warheadDamage", "endurance", "armed", "shields")) {
            assertFalse(saber.containsKey(forbidden),
                    "the candidate row must not carry " + forbidden);
        }
    }
}
