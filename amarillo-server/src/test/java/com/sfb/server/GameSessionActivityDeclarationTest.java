package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The launch declaration round (Annex #2, Impulse Activity Segment).
 * <p>
 * It exists for two reasons. Launches used to resolve the instant a player clicked, so their
 * order was whatever the players happened to click — and Annex #2 fixes it: seeking weapons
 * are stage 6B6, shuttles 6B8. And a launch of several things used to be several requests,
 * so a refusal halfway left the earlier ones away with no way back.
 * <p>
 * It is deliberately independent of the fire round: separate state, separate actions, and a
 * test here that neither disturbs the other.
 */
class GameSessionActivityDeclarationTest {

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
        klingon.setLocation(new Location(10, 8));
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
        advanceTo(Game.ImpulsePhase.ACTIVITY);
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

    private ActionRequest request(String type, String token) {
        ActionRequest req = new ActionRequest();
        req.setType(type);
        req.setPlayerToken(token);
        return req;
    }

    private ActionRequest.ActivityOrder order(String kind, String ship) {
        ActionRequest.ActivityOrder o = new ActionRequest.ActivityOrder();
        o.setKind(kind);
        o.setShipName(ship);
        return o;
    }

    // -------------------------------------------------------------------------
    // The call
    // -------------------------------------------------------------------------

    @Test
    void call_opensTheRound_andIsRefusedOutsideTheActivitySegment() {
        assertTrue(session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST)).isSuccess());
        assertTrue(session.isActivityDeclarationOpen());

        advanceTo(Game.ImpulsePhase.DIRECT_FIRE);
        ActionResult wrongPhase = session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));
        assertFalse(wrongPhase.isSuccess());
        assertTrue(wrongPhase.getMessage().contains("Activity segment"), wrongPhase.getMessage());
    }

    @Test
    void secondCall_whileOpen_isRefused() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));

        ActionResult again = session.executeAction(request("CALL_ACTIVITY_DECLARATION", P2));

        assertFalse(again.isSuccess());
        assertTrue(again.getMessage().contains("already open"), again.getMessage());
    }

    /** The two rounds are independent: opening one must not open or spend the other. */
    @Test
    void theLaunchRoundDoesNotDisturbTheFireRound() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));

        assertTrue(session.isActivityDeclarationOpen());
        assertFalse(session.isFireDeclarationOpen(), "the fire round is a different round");
        assertFalse(session.isFireDeclarationSpent());
    }

    // -------------------------------------------------------------------------
    // Sealing
    // -------------------------------------------------------------------------

    @Test
    void commit_forAnotherPlayersShip_isRefused() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));

        ActionRequest commit = request("COMMIT_ACTIVITY_DECLARATION", HOST);
        commit.setActivityOrders(List.of(order("DRONE", "IKV Saber")));
        ActionResult res = session.executeAction(commit);

        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("another player's unit"), res.getMessage());
    }

    @Test
    void commit_isSealed_untilEveryoneAnswers() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));

        assertTrue(session.executeAction(request("PASS_ACTIVITY_DECLARATION", HOST)).isSuccess());
        assertTrue(session.isActivityDeclarationOpen(), "still waiting for Bob");
        assertFalse(session.isActivityDeclarationSpent());

        ActionResult twice = session.executeAction(request("PASS_ACTIVITY_DECLARATION", HOST));
        assertFalse(twice.isSuccess());
        assertTrue(twice.getMessage().contains("sealed"), twice.getMessage());

        assertTrue(session.executeAction(request("PASS_ACTIVITY_DECLARATION", P2)).isSuccess());
        assertFalse(session.isActivityDeclarationOpen());
        assertTrue(session.isActivityDeclarationSpent());
    }

    @Test
    void advancingWithoutAnswering_saysSo() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));

        ActionResult advance = session.executeAction(request("ADVANCE_PHASE", P2));

        assertFalse(advance.isSuccess());
        assertTrue(advance.getMessage().contains("called for launches"), advance.getMessage());
    }

    @Test
    void advancingAfterSealing_readsAsWaiting_notAsAnError() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));
        session.executeAction(request("PASS_ACTIVITY_DECLARATION", HOST));

        ActionResult advance = session.executeAction(request("ADVANCE_PHASE", HOST));

        assertTrue(advance.isSuccess(), advance.getMessage());
        assertTrue(advance.getMessage().startsWith("WAITING:"), advance.getMessage());
    }

    // -------------------------------------------------------------------------
    // The reveal, in Annex #2 order
    // -------------------------------------------------------------------------

    /**
     * The ordering the round exists for: seeking weapons are stage 6B6 and shuttles 6B8, so
     * in one impulse the drones are away BEFORE a weasel goes up — however the two players
     * happened to draft them. Bob's weasel is committed first here, deliberately.
     */
    @Test
    void seekingWeaponsResolveBeforeShuttles() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));

        ActionRequest bob = request("COMMIT_ACTIVITY_DECLARATION", P2);
        ActionRequest.ActivityOrder weasel = order("WEASEL", "IKV Saber");
        weasel.setShuttleName("IKV Saber-Admin-1");
        weasel.setFacing(1);
        weasel.setSpeed(0);
        bob.setActivityOrders(List.of(weasel));
        assertTrue(session.executeAction(bob).isSuccess());

        ActionRequest alice = request("COMMIT_ACTIVITY_DECLARATION", HOST);
        ActionRequest.ActivityOrder drone = order("DRONE", "USS Enterprise");
        drone.setTargetName("IKV Saber");
        drone.setWeaponName("no-such-rack");     // it will fizzle, and say why
        alice.setActivityOrders(List.of(drone));
        assertTrue(session.executeAction(alice).isSuccess());

        assertFalse(session.isActivityDeclarationOpen(), "everyone answered, so it resolved");
        String log = String.join("\n", session.drainCombatLog());
        assertTrue(log.contains("Launch declaration resolves"), log);

        int dronePos  = log.indexOf("rack");           // the drone order's refusal
        int weaselPos = log.indexOf("Admin-1");        // the weasel order's outcome
        assertTrue(dronePos >= 0 && weaselPos >= 0, log);
        assertTrue(dronePos < weaselPos,
                "6B6 before 6B8: the seeking weapon is dealt with first\n" + log);
    }

    /**
     * A heading belongs to the launch, not to the round.
     *
     * One impulse can send an admin shuttle one way and two plasmas two others, so every
     * order on the wire carries its own facing and the reveal must keep them apart. The pad
     * used to hold a single facing and apply it to whatever you sent next, which made this
     * look like a UI preference rather than what it is — part of each order.
     *
     * Two racks, two directions, one commit: both drones leave on their own heading.
     */
    @Test
    void eachOrderKeepsItsOwnHeading() {
        klingon.setActiveFireControl(true);
        klingon.addLockOn(fed);

        List<String> racks = klingon.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.DroneRack)
                .map(com.sfb.weapons.Weapon::getName)
                .toList();
        assertEquals(2, racks.size(), "premise: the D7 carries two racks");

        session.executeAction(request("CALL_ACTIVITY_DECLARATION", P2));

        // The Fed lies on bearing 13, so a seeker can leave on 9, 13 or 17 and still see it.
        ActionRequest.ActivityOrder first = order("DRONE", "IKV Saber");
        first.setTargetName("USS Enterprise");
        first.setWeaponName(racks.get(0));
        first.setFacing(9);

        ActionRequest.ActivityOrder second = order("DRONE", "IKV Saber");
        second.setTargetName("USS Enterprise");
        second.setWeaponName(racks.get(1));
        second.setFacing(17);

        ActionRequest commit = request("COMMIT_ACTIVITY_DECLARATION", P2);
        commit.setActivityOrders(List.of(first, second));
        assertTrue(session.executeAction(commit).isSuccess());
        assertTrue(session.executeAction(request("PASS_ACTIVITY_DECLARATION", HOST)).isSuccess());

        List<Integer> facings = game.getSeekers().stream()
                .filter(sk -> sk instanceof com.sfb.objects.Drone)
                .map(sk -> ((com.sfb.objects.Drone) sk).getFacing())
                .sorted()
                .toList();
        assertEquals(List.of(9, 17), facings,
                "both drones launched, each on the heading its own order named: "
                + String.join(" | ", session.drainCombatLog()));
    }

    /**
     * An order that turns out to be illegal fizzles with its reason and leaves the rest of
     * the round alone. This is what replaces the old loop of one request per launch, which
     * stopped at the first failure and left everything before it already away.
     */
    @Test
    void anIllegalOrderFizzles_withoutRejectingTheRound() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));

        ActionRequest commit = request("COMMIT_ACTIVITY_DECLARATION", HOST);
        ActionRequest.ActivityOrder bad = order("DRONE", "USS Enterprise");
        bad.setTargetName("IKV Saber");
        bad.setWeaponName("no-such-rack");
        ActionRequest.ActivityOrder alsoBad = order("PLASMA", "USS Enterprise");
        alsoBad.setTargetName("IKV Saber");
        alsoBad.setWeaponName("no-such-launcher");
        commit.setActivityOrders(List.of(bad, alsoBad));

        assertTrue(session.executeAction(commit).isSuccess(), "the COMMIT itself is accepted");
        session.executeAction(request("PASS_ACTIVITY_DECLARATION", P2));

        String log = String.join("\n", session.drainCombatLog());
        assertTrue(log.contains("Drone rack not found"), log);
        assertTrue(log.contains("Plasma launcher not found"), log);
    }

    @Test
    void nextImpulse_allowsANewCall() {
        session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST));
        session.executeAction(request("PASS_ACTIVITY_DECLARATION", HOST));
        session.executeAction(request("PASS_ACTIVITY_DECLARATION", P2));
        assertTrue(session.isActivityDeclarationSpent());

        int impulse = game.getAbsoluteImpulse();
        for (int guard = 0; guard < 50 && !(game.getAbsoluteImpulse() > impulse
                && game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY); guard++)
            game.advancePhase();

        assertFalse(session.isActivityDeclarationSpent(), "a new impulse, a new round");
        assertTrue(session.executeAction(request("CALL_ACTIVITY_DECLARATION", HOST)).isSuccess());
    }
}
