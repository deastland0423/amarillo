package com.sfb.server;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.properties.AegisLevel;
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
 * Aegis fire reaches a player: the extra firings (D13.0) as a server action, and the eligible
 * targets as a narrowing of the question {@code /fire-targets} already answers.
 *
 * <h2>Why aegis is not part of the sealed fire declaration</h2>
 * D13.11 resolves aegis fire as it happens, and D13.14 makes the FIRST aegis firing coincide with
 * the ship's ordinary volley — so by the time {@code AEGIS_PULSE} is used, firing one is already
 * spent inside the sealed orders and what remains are the EXTRAS: three on a full system, one on a
 * limited. That is why {@code aegisPulsesRemaining} is a different number from {@code aegisFirings},
 * and why a pad driven by the allowance would offer a shot that does not exist.
 *
 * <h2>The trap in D13.21, which these tests exist to pin</h2>
 * "Size-6 and smaller" counts the SFB way: class 1 is a starbase and the numbers grow as the hull
 * shrinks. So eligible means size class <b>6 or higher</b>, and the rule excludes everything bigger
 * — a cruiser at class 3 obviously, but also a PF at class 5, which reads like it should qualify.
 */
class AegisPulseEndpointTest {

    private GameController controller;
    private GameSession session;
    private Game game;
    private Ship aegisShip;
    private Ship enemyCruiser;
    private CataloguedFighter fighter;
    private String gameId;
    private String HOST;
    private String P2;

    @BeforeEach
    void setUp() {
        GameSessionService service = new GameSessionService();
        controller = new GameController(service, null);
        session = service.createSession("Alice");
        gameId = session.getId();
        HOST = session.getPlayers().keySet().iterator().next();
        P2 = service.joinSession(gameId, "Bob");
        game = session.getGame();

        // A Fed CA standing in for an escort. The hull is not the point — D13.0 makes aegis a
        // property of the HULL and never of being an escort, so a test that reached for a real
        // escort would be asserting the DATA, which ShipLibrary guards elsewhere.
        aegisShip = new Ship();
        aegisShip.init(FederationShips.getFedCa());
        aegisShip.setName("USS Enterprise");
        aegisShip.setLocation(new Location(10, 10));
        aegisShip.setFacing(1);
        aegisShip.setAegisFitted(AegisLevel.FULL);

        enemyCruiser = new Ship();
        enemyCruiser.init(KlingonShips.getD7());
        enemyCruiser.setName("IKV Saber");
        enemyCruiser.setLocation(new Location(10, 8));   // two hexes dead ahead
        enemyCruiser.setFacing(13);

        game.getShips().add(aegisShip);
        game.getShips().add(enemyCruiser);

        // Something aegis may actually shoot: a fighter is size class 6, two hexes out.
        fighter = CataloguedFighter.of("haas");
        fighter.setName("HAAS-1");
        fighter.setLocation(new Location(10, 9));
        fighter.setFacing(13);
        game.getActiveShuttles().add(fighter);

        Player alice = new Player();
        alice.setName("Alice");
        alice.getPlayerUnits().add(aegisShip);
        session.getPlayers().get(HOST).setCorePlayer(alice);
        Player bob = new Player();
        bob.setName("Bob");
        bob.getPlayerUnits().add(enemyCruiser);
        bob.getPlayerUnits().add(fighter);
        session.getPlayers().get(P2).setCorePlayer(bob);

        game.startTurn();
        game.submitAllocation(aegisShip, allocationFor(aegisShip));
        game.submitAllocation(enemyCruiser, allocationFor(enemyCruiser));

        // D13.23 needs both: fire control running and a lock-on to the thing being shot at.
        aegisShip.setActiveFireControl(true);
        aegisShip.addLockOn(fighter);
        aegisShip.addLockOn(enemyCruiser);
    }

    private Energy allocationFor(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        return e;
    }

    private void reachDirectFire() {
        for (int guard = 0; guard < 24
                && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase(),
                "fixture: aegis fires in the Direct Fire phase (D13.11)");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> aegisTargets(String token, String attacker) {
        Object body = controller.getFireTargets(gameId, token, attacker, true).getBody();
        assertInstanceOf(List.class, body, "unexpected body: " + body);
        return (List<Map<String, Object>>) body;
    }

    // ------------------------------------------------------------------ eligible targets

    /**
     * The whole point of the filter: the fighter is offered and the cruiser beside it is not, even
     * though both are in range, both are bearing and both are the same enemy's.
     */
    @Test
    void aegisOffersTheFighterAndNotTheCruiser() {
        reachDirectFire();
        List<String> names = aegisTargets(HOST, "USS Enterprise").stream()
                .map(r -> String.valueOf(r.get("name"))).toList();

        assertTrue(names.contains("HAAS-1"), "a size class 6 fighter two hexes out: " + names);
        assertFalse(names.contains("IKV Saber"),
                "D13.21 bars anything bigger than size class 6, and a D7 is class 3: " + names);
    }

    /** And without the flag the ordinary question still answers the cruiser, so the filter is real. */
    @Test
    void theUnfilteredQuestionStillOffersTheCruiser() {
        reachDirectFire();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> all =
                (List<Map<String, Object>>) controller.getFireTargets(gameId, HOST,
                        "USS Enterprise", false).getBody();
        List<String> names = all.stream().map(r -> String.valueOf(r.get("name"))).toList();

        assertTrue(names.contains("IKV Saber"),
                "fixture: the cruiser is an ordinary target, so its absence above is the FILTER"
                        + " and not the fixture: " + names);
    }

    /** D13.23: no aegis without fire control running, so the list empties. */
    @Test
    void aegisOffersNothingWithoutFireControl() {
        reachDirectFire();
        aegisShip.setActiveFireControl(false);

        assertTrue(aegisTargets(HOST, "USS Enterprise").isEmpty(),
                "D13.23 requires active fire control");
    }

    /** And nothing at all on a hull with no aegis fitted, however small the target. */
    @Test
    void aShipWithoutAegisOffersNothing() {
        reachDirectFire();
        aegisShip.setAegisFitted(AegisLevel.NONE);

        assertTrue(aegisTargets(HOST, "USS Enterprise").isEmpty(),
                "a hull with no aegis has no aegis targets");
    }

    // ------------------------------------------------------------------ the pulse

    private ActionRequest pulse(String token, String target, List<String> weapons) {
        ActionRequest r = new ActionRequest();
        r.setType("AEGIS_PULSE");
        r.setPlayerToken(token);
        r.setShipName("USS Enterprise");
        r.setTargetName(target);
        r.setWeaponNames(weapons);
        return r;
    }

    private String firstPhaserName() {
        return aegisShip.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w.getType().startsWith("Phaser"))
                .map(com.sfb.weapons.Weapon::getName)
                .findFirst().orElseThrow();
    }

    /** A pulse at a legal target succeeds and spends one of the extra firings. */
    @Test
    void aPulseFiresAndSpendsAnExtraFiring() {
        reachDirectFire();
        int before = aegisShip.aegisPulsesRemaining(game.getAbsoluteImpulse());
        assertEquals(3, before, "a FULL system adds three extras to the ordinary firing (D13.14)");

        Game.ActionResult r = session.executeAction(pulse(HOST, "HAAS-1",
                List.of(firstPhaserName())));

        assertTrue(r.isSuccess(), "pulse refused: " + r.getMessage());
        assertEquals(before - 1, aegisShip.aegisPulsesRemaining(game.getAbsoluteImpulse()),
                "the firing should be spent");
    }

    /** D13.21 again, this time as a refusal rather than an omission, naming the rule. */
    @Test
    void aPulseAtACruiserIsRefusedByRule() {
        reachDirectFire();
        Game.ActionResult r = session.executeAction(pulse(HOST, "IKV Saber",
                List.of(firstPhaserName())));

        assertFalse(r.isSuccess(), "a D7 is size class 3 and aegis cannot engage it");
        assertTrue(r.getMessage().contains("D13.21") || r.getMessage().contains("D13.23"),
                "the refusal should name the rule: " + r.getMessage());
    }

    /** Spending every extra firing closes the pad; D13.142's cap is a counter, and it holds. */
    @Test
    void theExtraFiringsRunOut() {
        reachDirectFire();
        String phaser = firstPhaserName();
        for (int shot = 1; shot <= 3; shot++) {
            Game.ActionResult r = session.executeAction(pulse(HOST, "HAAS-1", List.of(phaser)));
            assertTrue(r.isSuccess(), "pulse " + shot + " refused: " + r.getMessage());
        }
        assertEquals(0, aegisShip.aegisPulsesRemaining(game.getAbsoluteImpulse()));

        Game.ActionResult fourth = session.executeAction(pulse(HOST, "HAAS-1", List.of(phaser)));
        assertFalse(fourth.isSuccess(), "a fourth extra firing does not exist (D13.142)");
        assertTrue(fourth.getMessage().contains("D13.142"),
                "the refusal should name the rule: " + fourth.getMessage());
    }

    /** A weapon the ship does not have is an error, not a quietly smaller pulse. */
    @Test
    void anUnknownWeaponIsRefusedRatherThanDropped() {
        reachDirectFire();
        Game.ActionResult r = session.executeAction(pulse(HOST, "HAAS-1",
                List.of("Phaser9-99")));

        assertFalse(r.isSuccess(), "an unknown weapon must not fire a smaller pulse silently");
        assertTrue(r.getMessage().contains("Phaser9-99"),
                "the refusal should name it: " + r.getMessage());
    }

    /** Outside the Direct Fire phase there is no aegis fire at all (D13.11). */
    @Test
    void aPulseOutsideDirectFireIsRefused() {
        // startTurn leaves the game before Direct Fire; do not advance into it.
        Game.ActionResult r = session.executeAction(pulse(HOST, "HAAS-1",
                List.of(firstPhaserName())));

        assertFalse(r.isSuccess(), "aegis fires in the Direct Fire phase");
        assertTrue(r.getMessage().toLowerCase().contains("direct fire"),
                "the refusal should say when: " + r.getMessage());
    }

    // ------------------------------------------------- D13.141/D13.142 through the session

    /**
     * The skip is an ACTION a player can take, not just a pad that closes.
     * <p>
     * D13.141 sequences the force, so until every aegis ship has fired or given up the firing it is
     * on, none of them moves to the next. A player who does not want to shoot with one escort must
     * therefore be able to say so through the session, or the other escort is stalled with no way
     * out. Tested here rather than only in core because the pad's button is what has to reach it.
     */
    @Test
    void aPlayerCanSkipAnAegisFiring() {
        reachDirectFire();
        int before = aegisShip.aegisPulsesRemaining(game.getAbsoluteImpulse());
        assertTrue(before > 0, "fixture: there is a firing to give up");

        ActionRequest req = new ActionRequest();
        req.setType("AEGIS_SKIP");
        req.setPlayerToken(HOST);
        req.setShipName("USS Enterprise");
        Game.ActionResult r = session.executeAction(req);

        assertTrue(r.isSuccess(), r.getMessage());
        assertTrue(r.getMessage().contains("D13.142"),
                "the log should name the rule: " + r.getMessage());
        assertEquals(before - 1, aegisShip.aegisPulsesRemaining(game.getAbsoluteImpulse()),
                "a skipped firing is spent, not banked (D13.142)");
    }

    /** An unknown ship is refused by name rather than throwing. */
    @Test
    void skippingWithNoSuchShipIsRefused() {
        reachDirectFire();
        ActionRequest req = new ActionRequest();
        req.setType("AEGIS_SKIP");
        req.setPlayerToken(HOST);
        req.setShipName("USS Nowhere");

        Game.ActionResult r = session.executeAction(req);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("not found"), r.getMessage());
    }
}
