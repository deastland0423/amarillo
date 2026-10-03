package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.PlasmaRack;
import com.sfb.weapons.Weapon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A plasma rack reached from the UI's two paths: the sealed launch declaration (6B6) and the
 * fire declaration.
 *
 * <h2>Why this test exists at the declaration layer</h2>
 * The rack has been workable in core and through the single-action endpoint for a while, and was
 * still unreachable from a browser — nothing listed it in the launch pad, and bolting meant
 * hand-crafting a shotModes map. The gap was entirely in the layers ABOVE the action: the launch
 * declaration had no PLASMA_RACK kind, and {@code plasmaLaunchersBearing} filtered on
 * {@code instanceof PlasmaLauncher}, so a rack never appeared as bearing on anything.
 *
 * <p>That is the shape of gap {@code feedback_test_at_the_gating_layer} records twice: a feature
 * passing every test and being unreachable in play because the tests entered below the layer that
 * gates it.
 */
class PlasmaRackDeclarationTest {

    private static final String HOST = "token-host";

    private GameSession session;
    private Game game;
    private Ship k5d;
    private Ship cruiser;

    @BeforeEach
    void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");

        session = new GameSession("game-1", HOST, "Alice");
        game = session.getGame();

        k5d = ShipLibrary.createShip(ShipLibrary.get("Romulan", "K5D"));
        k5d.setName("RIS Nemesis");
        k5d.setLocation(new Location(10, 10));
        k5d.setFacing(1);
        game.getShips().add(k5d);

        cruiser = new Ship();
        cruiser.init(FederationShips.getFedCa());
        cruiser.setName("USS Enterprise");
        cruiser.setLocation(new Location(7, 11));      // three hexes off the port side
        cruiser.setFacing(13);
        game.getShips().add(cruiser);

        game.startTurn();
        game.submitAllocation(k5d, allocation(k5d));
        game.submitAllocation(cruiser, allocation(cruiser));
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();

        for (PlasmaRack rack : racks())
            rack.applyWeaponStatus(3);                 // FP10.25 WS-III
        k5d.addLockOn(cruiser);

        // The declaration round checks OWNERSHIP, which a single action does not - so the fixture
        // has to say whose ship the K5D is, and then open the round. Both are part of the layer
        // being tested: a browser reaches a launch only through a called declaration round.
        com.sfb.Player alice = new com.sfb.Player();
        alice.setTeamName("Romulan");
        alice.getPlayerUnits().add(k5d);
        session.getPlayers().get(HOST).setCorePlayer(alice);
        session.executeAction(call("CALL_ACTIVITY_DECLARATION"));
        session.drainCombatLog();
    }

    private ActionRequest call(String type) {
        ActionRequest req = new ActionRequest();
        req.setType(type);
        req.setPlayerToken(HOST);
        return req;
    }

    private static Energy allocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        return e;
    }

    private List<PlasmaRack> racks() {
        List<PlasmaRack> list = new ArrayList<>();
        for (Weapon w : k5d.getWeapons().fetchAllWeapons())
            if (w instanceof PlasmaRack rack)
                list.add(rack);
        return list;
    }

    private PlasmaRack portRack() {
        for (PlasmaRack rack : racks())
            if ("LS".equals(rack.getArcLabel()))
                return rack;
        throw new AssertionError("fixture: the K5D should have a port rack");
    }

    private int torpedoesInSpace() {
        int n = 0;
        for (com.sfb.objects.Seeker s : game.getSeekers())
            if (s instanceof com.sfb.objects.PlasmaTorpedo)
                n++;
        return n;
    }

    /** A sealed launch declaration carrying one plasma-rack order. */
    private ActionRequest declaration(String rackName, String mode) {
        ActionRequest.ActivityOrder order = new ActionRequest.ActivityOrder();
        order.setKind("PLASMA_RACK");
        order.setShipName("RIS Nemesis");
        order.setTargetName("USS Enterprise");
        order.setWeaponName(rackName);
        order.setPlasmaRackMode(mode);

        ActionRequest req = new ActionRequest();
        req.setType("COMMIT_ACTIVITY_DECLARATION");
        req.setPlayerToken(HOST);
        req.setActivityOrders(List.of(order));
        return req;
    }

    // ---------------------------------------------------------------- the launch path

    /**
     * An offensive launch, declared and resolved. The cruiser is size class 3, so only offensive
     * mode can engage it at all (FP10.211 against FP10.212).
     */
    @Test
    void anOffensiveOrderSendsATorpedo() {
        PlasmaRack rack = portRack();

        assertTrue(session.executeAction(declaration(rack.getName(), "OFFENSIVE")).isSuccess());

        assertEquals(1, torpedoesInSpace(), "a type-D is away");
        assertEquals(3, rack.getTorpedoes(), "and one left the rack");
        assertEquals(PlasmaRack.RackMode.OFFENSIVE, rack.getModeThisTurn());
    }

    /**
     * FP10.21: the mode travels with the sealed order and is not defaulted. An order that names
     * none is refused, which is the backstop behind the pad's disabled button.
     */
    @Test
    void anOrderWithNoModeIsRefused() {
        PlasmaRack rack = portRack();

        // The COMMIT succeeds: a sealed declaration takes the plan and the orders are validated
        // when the round resolves. So the thing to assert is the OUTCOME, not the commit.
        assertTrue(session.executeAction(declaration(rack.getName(), null)).isSuccess());

        assertEquals(0, torpedoesInSpace());
        assertEquals(4, rack.getTorpedoes(), "nothing spent");
        assertEquals(PlasmaRack.RackMode.UNDECIDED, rack.getModeThisTurn(),
                "nor committed to a mode nobody chose");
        // And the owner is told why. The round resolves on the LAST commit, so there is no
        // response left to carry the reason - resolveActivityRound appends it to the log instead,
        // which is the only way a fizzled order could be noticed at all.
        assertTrue(String.join(" ", session.drainCombatLog()).contains("FP10.21"),
                "the refusal names the rule");
    }

    /**
     * FP10.212 reaches the declaration too: defensive mode engages "size-5 and smaller targets",
     * and a cruiser is size class 3. The pad disables the button; this is what happens if an
     * order gets there anyway.
     */
    @Test
    void aDefensiveOrderAgainstACruiserIsRefused() {
        PlasmaRack rack = portRack();

        assertTrue(session.executeAction(declaration(rack.getName(), "DEFENSIVE")).isSuccess());

        assertEquals(0, torpedoesInSpace());
        assertEquals(4, rack.getTorpedoes());
        assertTrue(String.join(" ", session.drainCombatLog()).contains("FP10.212"),
                "and the reason reaches the owner");
    }

    /** The public log says a torpedo went, never which mode sent it. */
    @Test
    void theSharedLogDoesNotRevealTheMode() {
        session.executeAction(declaration(portRack().getName(), "OFFENSIVE"));

        List<String> log = session.drainCombatLog();
        assertFalse(log.isEmpty(), "something was announced");
        String joined = String.join(" | ", log).toLowerCase();
        assertFalse(joined.contains("offensive"), joined);
        assertFalse(joined.contains("defensive"), joined);
    }
}
