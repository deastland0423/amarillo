package com.sfb.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.systemgroups.Energy;
import com.sfb.systemgroups.ShuttleBay;

/**
 * A player may fire, adjust EW for, and launch from his own CRAFT, not only his own ships.
 *
 * <h2>The bug, reported in play on 2026-10-10</h2>
 * The Hydran player tried to fire a Stinger and was told "Cannot fire another player's unit:
 * HMS Concept-Stinger2-1" — about his own fighter, off his own carrier.
 *
 * <p>Three declaration handlers checked ownership by scanning the player's list of SHIP names. No
 * craft is in that list and none ever was, so a fighter could not fire, could not have its EW
 * adjusted, and could not launch a drone off its rails (J1.31), under any naming scheme. The
 * fighter rename that preceded the report only changed which name appeared in the refusal.
 *
 * <h2>Why this file is separate from {@link BayFighterOwnershipTest}</h2>
 * That one proves {@code ownsShip} answers correctly for a craft. It always did. What was missing
 * is that these three handlers never ASKED it — so a test of the helper could not have caught
 * this, and did not. These go through {@code executeAction}, above the gate.
 *
 * <p>That is the third time this exact shape has bitten: the pod-EW declaration (J4.961) in
 * October, a ready-check before it, and now this. See the standing note about testing an action
 * at the layer that gates it.
 *
 * <h2>The trap in writing it</h2>
 * {@code ownsShip} lets EVERYBODY through while no ship has an owner — "dev/solo mode". A test
 * that builds ships and forgets {@code setOwner} therefore passes whatever the handler does. The
 * owners below are the point of the fixture, and {@link #theHarnessActuallyAssignsOwners} exists
 * so that stays true.
 */
class CraftDeclarationOwnershipTest {

    private static final String MINE  = "mine-token";
    private static final String YOURS = "yours-token";

    private GameSession session;
    private Game game;
    private Ship carrier;
    private Ship enemyCarrier;

    @BeforeEach
    void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        session = new GameSession("game-1", MINE, "Alice");
        session.addPlayer(YOURS, "Bob");
        game = session.getGame();

        Player alice = new Player();
        alice.setName("Alice");
        alice.setTeamName("Kzinti");
        Player bob = new Player();
        bob.setName("Bob");
        bob.setTeamName("Klingon");

        carrier      = carrierAt(alice, "KHS Watchful", 10, 10);
        enemyCarrier = carrierAt(bob,   "IKS Opposite", 12, 10);

        session.getPlayers().get(MINE).setCorePlayer(alice);
        session.getPlayers().get(YOURS).setCorePlayer(bob);

        game.startTurn();
        game.submitAllocation(carrier, allocationFor(carrier));
        game.submitAllocation(enemyCarrier, allocationFor(enemyCarrier));
        for (int guard = 0; guard < 400
                && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase(),
                "fixture: the declaration round only opens in Direct Fire");
    }

    private Ship carrierAt(Player owner, String name, int col, int row) throws Exception {
        Ship ship = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cvs.json")));
        ship.setName(name);
        ship.setOwner(owner);                 // NOT optional — see the class comment
        owner.getPlayerUnits().add(ship);
        ship.setLocation(new Location(col, row));
        ship.setFacing(1);
        game.getShips().add(ship);
        return ship;
    }

    private Energy allocationFor(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        return e;
    }

    private static String aFighterOn(Ship ship) {
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (Shuttle craft : bay.getInventory())
                if (craft instanceof Fighter f)
                    return f.getName();
        throw new IllegalStateException("fixture: " + ship.getName() + " has no fighter aboard");
    }

    private ActionRequest fireOrderFrom(String token, String unitName) {
        ActionRequest req = new ActionRequest();
        req.setType("COMMIT_FIRE_DECLARATION");
        req.setPlayerToken(token);
        ActionRequest.FireOrder order = new ActionRequest.FireOrder();
        order.setShipName(unitName);
        req.setFireOrders(List.of(order));
        return req;
    }

    private ActionRequest call(String token) {
        ActionRequest req = new ActionRequest();
        req.setType("CALL_FIRE_DECLARATION");
        req.setPlayerToken(token);
        return req;
    }

    /** Guards the fixture itself: without owners, ownsShip waves everyone through. */
    @Test
    void theHarnessActuallyAssignsOwners() {
        assertNotNull(carrier.getOwner(), "a test that forgets this passes vacuously");
        assertNotNull(enemyCarrier.getOwner());
        assertNotEquals(carrier.getOwner(), enemyCarrier.getOwner());
    }

    // -------------------------------------------------------------------------

    @Test
    void myOwnFighterIsNotAnotherPlayersUnit() {
        session.executeAction(call(MINE));
        ActionResult result = session.executeAction(fireOrderFrom(MINE, aFighterOn(carrier)));

        assertFalse(String.valueOf(result.getMessage()).contains("another player's unit"),
                "a Hydran firing his own Stinger was told it belonged to someone else: "
                        + result.getMessage());
    }

    @Test
    void myOwnSHIPStillPasses() {
        session.executeAction(call(MINE));
        ActionResult result = session.executeAction(fireOrderFrom(MINE, carrier.getName()));

        assertFalse(String.valueOf(result.getMessage()).contains("another player's unit"),
                "the case that always worked must keep working: " + result.getMessage());
    }

    /** The gate still has to close. Ownership of a craft is ownership of its carrier, no wider. */
    @Test
    void anotherPlayersFighterIsStillRefused() {
        session.executeAction(call(MINE));
        ActionResult result = session.executeAction(fireOrderFrom(MINE, aFighterOn(enemyCarrier)));

        assertFalse(result.isSuccess(), "firing someone else's fighter must be refused");
        assertTrue(String.valueOf(result.getMessage()).contains("another player's unit"),
                "and refused for the right reason: " + result.getMessage());
    }

    @Test
    void anotherPlayersSHIPIsStillRefused() {
        session.executeAction(call(MINE));
        ActionResult result = session.executeAction(fireOrderFrom(MINE, enemyCarrier.getName()));

        assertFalse(result.isSuccess());
        assertTrue(String.valueOf(result.getMessage()).contains("another player's unit"),
                result.getMessage());
    }

    @Test
    void aNameNobodyAnswersToIsRefused() {
        session.executeAction(call(MINE));
        ActionResult result = session.executeAction(fireOrderFrom(MINE, "Nothing Called This"));

        assertFalse(result.isSuccess(), "an unknown unit is not ownable");
    }
}
