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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The /fire-targets endpoint: one call answers "what can this ship shoot?" for a whole ship,
 * so the Fire Orders pad can list targets by name instead of making the player find them on
 * a crowded map.
 * <p>
 * What is worth pinning is that the endpoint decides candidacy — the client used to, in
 * canBeFireTarget, which quietly carried J3.21's exploding-weasel exception — and that the
 * figures it reports are the same ones /fire-options gives for a single pair, because both
 * now call one method.
 */
class FireTargetsEndpointTest {

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

        // Through the service, so the controller finds the session by id the way a real
        // request does.
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
        klingon.setLocation(new Location(10, 8));   // two hexes dead ahead of facing 1
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
        Object body = controller.getFireTargets(gameId, token, attacker).getBody();
        assertNotNull(body, "endpoint returned no body");
        return (List<Map<String, Object>>) body;
    }

    private Map<String, Object> row(List<Map<String, Object>> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.get("name"))).findFirst().orElse(null);
    }

    // -------------------------------------------------------------------------

    @Test
    void enemyInArc_isListedWithItsFiguresResolved() {
        Map<String, Object> saber = row(targetsFor(HOST, "USS Enterprise"), "IKV Saber");

        assertNotNull(saber, "an enemy two hexes dead ahead should be a candidate");
        assertEquals("SHIP", saber.get("kind"));
        assertEquals(2, saber.get("range"));
        assertFalse(((List<?>) saber.get("weaponsInArc")).isEmpty(), "weapons should bear");
        // The shield the shot would hit — the pad shows it so the player never computes it.
        int shield = (Integer) saber.get("shieldNumber");
        assertTrue(shield >= 1 && shield <= 6, "shield number resolved: " + shield);
    }

    /** The figures must match /fire-options exactly: both call one method now. */
    @Test
    void agreesWithFireOptionsForTheSamePair() {
        Map<String, Object> fromList = row(targetsFor(HOST, "USS Enterprise"), "IKV Saber");
        @SuppressWarnings("unchecked")
        Map<String, Object> single = (Map<String, Object>)
                controller.getFireOptions(gameId, HOST, "USS Enterprise", "IKV Saber").getBody();

        assertNotNull(single);
        for (String key : List.of("range", "adjustedRange", "shieldNumber", "weaponsInArc",
                                  "hasLockOn", "ecmPoints", "eccm", "ecmShift")) {
            assertEquals(single.get(key), fromList.get(key), "disagreement on " + key);
        }
    }

    @Test
    void ownShip_isNeverACandidate() {
        assertNull(row(targetsFor(HOST, "USS Enterprise"), "USS Enterprise"),
                "a ship cannot fire at itself");
        assertNull(row(targetsFor(P2, "IKV Saber"), "IKV Saber"));
        // And each side sees only the other's ships.
        assertNotNull(row(targetsFor(P2, "IKV Saber"), "USS Enterprise"));
    }

    /**
     * The one that actually exercises ownership rather than the self check: a SECOND ship of
     * Alice's, sitting in the Enterprise's arc, must not be offered. Written after a
     * perturbation showed the test above passes with the ownership filter disabled, because
     * "not myself" was doing all the work.
     */
    @Test
    void friendlyShip_isNeverACandidate() {
        Ship kongo = new Ship();
        kongo.init(FederationShips.getFedCa());
        kongo.setName("USS Kongo");
        kongo.setLocation(new Location(10, 9));     // between the two, well in arc
        kongo.setFacing(1);
        kongo.setSpeedPreviousTurn(31);
        kongo.setSpeedTwoTurnsAgo(31);
        game.getShips().add(kongo);
        session.getPlayers().get(HOST).getCorePlayer().getPlayerUnits().add(kongo);

        List<Map<String, Object>> rows = targetsFor(HOST, "USS Enterprise");

        assertNull(row(rows, "USS Kongo"), "you do not shoot your own fleet");
        assertNotNull(row(rows, "IKV Saber"), "the enemy is still there");
        // And Bob may shoot it, so it is excluded by ownership and not by anything else.
        assertNotNull(row(targetsFor(P2, "IKV Saber"), "USS Kongo"));
    }

    /**
     * fetchAllBearingWeapons tests range as well as arc, so a ship far enough away has no
     * weapon that bears and is not offered. The pad should never list a target every weapon
     * would refuse.
     * <p>
     * It takes 80 hexes to get there: a Ph-1 is modelled with a 75-hex maximum, so at any
     * plausible battle range a phaser always "bears" — it simply does no damage off the end
     * of its chart.
     */
    @Test
    void targetNoWeaponBearsOn_isExcluded() {
        klingon.setLocation(new Location(10, 90));   // range 80, past a Ph-1's 75

        assertNull(row(targetsFor(HOST, "USS Enterprise"), "IKV Saber"),
                "nothing bears at that range, so it is not a candidate");
    }

    /**
     * P2.321: a planet between the two blocks the shot, so the target is not offered. The
     * endpoint asks game.losBlocked — the same call DamageResolver.fireWeapons makes — rather
     * than working the geometry out a second time.
     */
    @Test
    void targetBehindAPlanet_isExcluded() {
        assertNotNull(row(targetsFor(HOST, "USS Enterprise"), "IKV Saber"),
                "visible before the planet exists");

        game.addTerrain(new com.sfb.objects.Terrain(
                com.sfb.properties.TerrainType.PLANET, 10, 9));

        assertNull(row(targetsFor(HOST, "USS Enterprise"), "IKV Saber"),
                "a planet in the way means no line of sight (P2.321)");
    }

    /**
     * A drone rack bears like anything else, but resolveFire refuses it — it is a Launcher,
     * not a direct-fire weapon. Offering one and then refusing it at the reveal is worse than
     * not offering it, so the row filters on the same predicate the refusal uses.
     * <p>
     * A plasma launcher is NOT caught by this: it implements DirectFire and fires as a bolt.
     */
    @Test
    void weaponsThatCannotFireDirectly_areNotOffered() {
        List<String> notDirectFire = klingon.getWeapons().fetchAllWeapons().stream()
                .filter(w -> !(w instanceof com.sfb.weapons.DirectFire))
                .map(com.sfb.weapons.Weapon::getName)
                .toList();
        assumeTrue(!notDirectFire.isEmpty(),
                "the D7 needs a launcher for this to prove anything");

        for (Map<String, Object> row : targetsFor(P2, "IKV Saber")) {
            @SuppressWarnings("unchecked")
            List<String> bearing = (List<String>) row.get("weaponsInArc");
            for (String name : notDirectFire)
                assertFalse(bearing.contains(name),
                        "offered " + name + ", which could never fire");
        }
    }

    // -------------------------------------------------------------------------
    // "Closing on": an inference from the board, never a disclosure
    // -------------------------------------------------------------------------

    /** A drone in the Klingon's hands, placed and pointed by the test. */
    private com.sfb.objects.Drone drone(String name, int col, int row, int facing) {
        com.sfb.objects.Drone d = new com.sfb.objects.Drone(com.sfb.objects.DroneType.TypeI);
        d.setName(name);
        d.setLocation(new Location(col, row));
        d.setFacing(facing);
        d.setController(klingon);
        d.setSeekerType(com.sfb.objects.Seeker.SeekerType.DRONE);
        game.getSeekers().add(d);
        return d;
    }

    @Test
    void seekerPointedAtMyShip_isReportedAsClosingOnIt() {
        // Two hexes north of the Enterprise (10,10) and pointed south, straight down at it.
        drone("IKV Saber-Drone-1", 10, 8, 13);

        Map<String, Object> row = row(targetsFor(HOST, "USS Enterprise"), "IKV Saber-Drone-1");

        assertNotNull(row);
        assertEquals("DRONE", row.get("kind"));
        assertEquals("USS Enterprise", row.get("closingOn"));
    }

    @Test
    void seekerPointedAway_isClosingOnNothingOfMine() {
        drone("IKV Saber-Drone-2", 10, 8, 1);   // same hex, pointed north, away from me

        Map<String, Object> row = row(targetsFor(HOST, "USS Enterprise"), "IKV Saber-Drone-2");

        assertNotNull(row);
        assertNull(row.get("closingOn"), "pointed away, so it is closing on nothing of mine");
    }

    /**
     * The inference must stay an inference. A drone's real target is hidden until it is
     * identified (G4.2), and the row must not carry it under any name — closingOn is computed
     * from position and facing, which are public.
     */
    @Test
    void closingOn_neverLeaksTheSeekersRealTarget() {
        com.sfb.objects.Drone d = drone("IKV Saber-Drone-3", 10, 8, 13);
        d.setTarget(fed);                        // it really is after the Enterprise

        Map<String, Object> row = row(targetsFor(HOST, "USS Enterprise"), "IKV Saber-Drone-3");

        assertNotNull(row);
        assertFalse(row.containsKey("targetName"), "the real target is not the pad's to know");
        assertFalse(row.containsKey("isIdentified"));
    }

    @Test
    void attackerOffTheMap_hasNoTargets() {
        fed.setLocation(null);

        assertTrue(targetsFor(HOST, "USS Enterprise").isEmpty());
    }

    @Test
    void unknownAttacker_isRefused() {
        assertEquals(400, controller.getFireTargets(gameId, HOST, "USS Nowhere")
                .getStatusCode().value());
    }

    /**
     * Privacy: the row carries only what positions and facings already make public — range,
     * the shield facing the shooter, which of the attacker's own weapons bear. Nothing about
     * the target's insides, and in particular nothing that identification (G4.2) is supposed
     * to be the way to learn.
     */
    @Test
    void rowCarriesNothingHidden() {
        Map<String, Object> saber = row(targetsFor(HOST, "USS Enterprise"), "IKV Saber");

        assertNotNull(saber);
        for (String forbidden : List.of("hull", "maxHull", "targetName", "droneType",
                                        "warheadDamage", "endurance", "armed")) {
            assertFalse(saber.containsKey(forbidden),
                    "the candidate row must not carry " + forbidden);
        }
    }
}
