package com.sfb.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.systemgroups.Energy;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.PPD;

/**
 * Firing a PPD (E11.0) from the sealed fire declaration, which is the only way anything fires.
 *
 * <h2>The trap this closes</h2>
 * A PPD implements {@code DirectFire}, so a declaration naming one sailed straight into an
 * ordinary {@code FireCommand}: it fired ONCE, scored on a single shield, and never registered
 * the run that gives it its other three pulses. Accepted, plausible, and wrong. It is not an
 * ordinary shot — E11.31 fires it "before any other fire by that side", E11.332 resolves its
 * damage as its own splash volley, and E11.30 carries it over the next three impulses — so it
 * leaves the volley and goes through its own path.
 */
class GameSessionPpdDeclarationTest {

    private static final String HOST = "token-host";
    private static final String P2   = "token-p2";

    private GameSession session;
    private Game game;
    private Ship isc;
    private Ship klingon;
    private PPD ppd;

    @BeforeEach
    void setUp() {
        session = new GameSession("game-ppd", HOST, "Alice");
        session.addPlayer(P2, "Bob");
        game = session.getGame();

        isc = new Ship();
        isc.init(com.sfb.samples.FederationShips.getFedCa());   // a hull to hang the device on
        isc.setName("CSS Arbiter");
        isc.setLocation(new Location(10, 10));
        isc.setFacing(1);
        isc.setSpeedPreviousTurn(31);
        isc.setSpeedTwoTurnsAgo(31);

        ppd = new PPD();
        ppd.setDesignator("1");
        ppd.setArcs(com.sfb.utilities.ArcUtils.calculateMask(List.of("FA")));
        isc.getWeapons().addWeapon(ppd);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        // Eight hexes dead ahead: outside the PPD's myopic zone (E11.312) and in its best band.
        klingon.setLocation(new Location(10, 2));
        klingon.setFacing(13);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(isc);
        game.getShips().add(klingon);

        Player alice = new Player();
        alice.setName("Alice");
        alice.getPlayerUnits().add(isc);
        isc.setOwner(alice);
        session.getPlayers().get(HOST).setCorePlayer(alice);

        Player bob = new Player();
        bob.setName("Bob");
        bob.getPlayerUnits().add(klingon);
        klingon.setOwner(bob);
        session.getPlayers().get(P2).setCorePlayer(bob);

        game.startTurn();
        game.submitAllocation(isc, allocation(isc));
        game.submitAllocation(klingon, allocation(klingon));
        advanceToDirectFire();

        ppd.setArmed(true);
        isc.setActiveFireControl(true);
        isc.addLockOn(klingon);
    }

    private Energy allocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(4.0);
        return e;
    }

    private void advanceToDirectFire() {
        for (int i = 0; i < 24 && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; i++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());
    }

    private ActionRequest request(String type, String token) {
        ActionRequest r = new ActionRequest();
        r.setType(type);
        r.setPlayerToken(token);
        return r;
    }

    private void declarePpdAt(String targetName) {
        session.executeAction(request("CALL_FIRE_DECLARATION", HOST));
        ActionRequest commit = request("COMMIT_FIRE_DECLARATION", HOST);
        ActionRequest.FireOrder order = new ActionRequest.FireOrder();
        order.setShipName("CSS Arbiter");
        order.setTargetName(targetName);
        order.setWeaponNames(List.of(ppd.getName()));
        order.setRange(8);
        order.setAdjustedRange(8);
        order.setShieldNumber(1);
        commit.setFireOrders(List.of(order));
        assertTrue(session.executeAction(commit).isSuccess());
        assertTrue(session.executeAction(request("PASS_FIRE_DECLARATION", P2)).isSuccess());
    }

    // ------------------------------------------------------------------ the run starts

    /**
     * THE POINT. A declared PPD starts a RUN — pulses owed on later impulses — rather than
     * firing once as part of the volley. Before it was routed out, this passed through
     * FireCommand and left nothing behind.
     */
    @Test
    void adeclaredPpdStartsAFiringRun() {
        declarePpdAt("IKV Saber");

        assertTrue(ppd.isPulsing(), "the run should be under way");
        assertEquals(3, ppd.pulsesRemaining(), "one pulse spent firing, three owed");
        assertEquals(1, game.activePpdRunCount(), "and the game should be tracking it");
    }

    /** The charge is spent by firing, as E11.23 requires — a run is not a loaded weapon. */
    @Test
    void thechargeIsSpentByTheDeclaration() {
        declarePpdAt("IKV Saber");

        assertFalse(ppd.isArmed());
    }

    /**
     * Fire control is checked at the reveal, not taken on trust from the declaration: a player
     * may order a PPD and then lose the lock-on before orders resolve (E11.15).
     */
    @Test
    void aPpdWithoutALockOnIsRefusedAtTheReveal() {
        isc.removeLockOn(klingon);

        declarePpdAt("IKV Saber");

        assertFalse(ppd.isPulsing(), "no run should have started");
        assertEquals(0, game.activePpdRunCount());
    }
}
