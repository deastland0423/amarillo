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
 * The LAUNCH_PLASMA_RACK action: the wire layer for FP10.0's seeking launch.
 *
 * <h2>What this layer owes the rules</h2>
 * Two things core cannot do for it. The MODE arrives as a string and has to be a real one —
 * FP10.21 makes firing the declaration, so a missing or unrecognised mode must be refused rather
 * than defaulted, since picking for the player either spends one of the ship's two offensive places
 * (FP10.242) or quietly gives up the reach the other mode would have had. And the public combat log
 * must not say which mode was used: a seeking torpedo at a small target is legal in either, so
 * naming it would tell an opponent what the shot itself does not.
 */
class GameSessionPlasmaRackTest {

    private static final String HOST = "token-host";

    private GameSession session;
    private Game game;
    private Ship k5d;
    private Ship enemy;

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

        enemy = new Ship();
        enemy.init(FederationShips.getFedCa());
        enemy.setName("USS Enterprise");
        enemy.setLocation(new Location(7, 11));   // off the port side, three hexes
        enemy.setFacing(13);
        game.getShips().add(enemy);

        game.startTurn();
        game.submitAllocation(k5d, allocation(k5d));
        game.submitAllocation(enemy, allocation(enemy));
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();

        // FP10.25 WS-III, so nothing here is refused for want of FP9.22's half point.
        for (PlasmaRack rack : racks())
            rack.applyWeaponStatus(3);
        session.drainCombatLog();
    }

    private List<PlasmaRack> racks() {
        List<PlasmaRack> list = new ArrayList<>();
        for (Weapon w : k5d.getWeapons().fetchAllWeapons())
            if (w instanceof PlasmaRack rack)
                list.add(rack);
        return list;
    }

    /** A rack bearing to port, where the target is. */
    private PlasmaRack portRack() {
        for (PlasmaRack rack : racks())
            if ("LS".equals(rack.getArcLabel()))
                return rack;
        throw new AssertionError("fixture: the K5D should have a port rack");
    }

    private ActionRequest launch(String rackName, String mode) {
        ActionRequest req = new ActionRequest();
        req.setType("LAUNCH_PLASMA_RACK");
        req.setShipName("RIS Nemesis");
        req.setTargetName("USS Enterprise");
        req.setPlayerToken(HOST);
        req.setWeaponNames(List.of(rackName));
        req.setPlasmaRackMode(mode);
        return req;
    }

    private static Energy allocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        return e;
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    void offensiveLaunch_sendsATorpedoAndRedactsTheLogLine() {
        PlasmaRack rack = portRack();

        ActionResult r = session.executeAction(launch(rack.getName(), "OFFENSIVE"));

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(3, rack.getTorpedoes(), "one torpedo away");
        assertEquals(PlasmaRack.RackMode.OFFENSIVE, rack.getModeThisTurn());

        // The private response may name the mode; the shared line must not.
        List<String> log = session.drainCombatLog();
        assertEquals(1, log.size());
        assertEquals("RIS Nemesis launched a plasma torpedo", log.get(0));
        assertFalse(log.get(0).toLowerCase().contains("offensive"), log.get(0));
        assertFalse(log.get(0).toLowerCase().contains("defensive"), log.get(0));
    }

    /**
     * Case does not matter on the wire; the mode is a name, not a token to match exactly.
     * <p>
     * Asserted with "offensive" rather than "defensive" because the target is a CRUISER, and
     * defensive mode cannot engage one (FP10.212). The first version of this test used the
     * lowercase defensive and failed on the rules refusal - which actually proved the parsing
     * worked, just not what the test claimed to check.
     */
    @Test
    void theModeIsAcceptedInAnyCase() {
        ActionResult r = session.executeAction(launch(portRack().getName(), "offensive"));

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(PlasmaRack.RackMode.OFFENSIVE, portRack().getModeThisTurn());
    }

    // ---------------------------------------------------------------- what this layer refuses

    /**
     * FP10.21: the mode is the player's declaration, so an omitted one is refused rather than
     * chosen for them. Defaulting would silently spend an FP10.242 place or give up FP10.211's
     * reach, and a client that forgot the field would never find out.
     */
    @Test
    void aLaunchWithNoMode_isRefused() {
        ActionRequest req = launch(portRack().getName(), null);

        ActionResult r = session.executeAction(req);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("FP10.21"), r.getMessage());
        assertEquals(4, portRack().getTorpedoes(), "nothing was spent");
    }

    @Test
    void aLaunchWithAnUnknownMode_isRefused() {
        ActionResult r = session.executeAction(launch(portRack().getName(), "AGGRESSIVE"));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("FP10.21"), r.getMessage());
    }

    /** UNDECIDED is a real enum value and still not a choice the rules recognise. */
    @Test
    void aLaunchDeclaringUndecided_isRefused() {
        ActionResult r = session.executeAction(launch(portRack().getName(), "UNDECIDED"));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("FP10.21"), r.getMessage());
    }

    @Test
    void aLaunchNamingNoRack_isRefused() {
        ActionRequest req = launch(portRack().getName(), "DEFENSIVE");
        req.setWeaponNames(List.of());

        ActionResult r = session.executeAction(req);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("No plasma rack"), r.getMessage());
    }

    /** A weapon that is not a rack is not a rack, even on a ship that has some. */
    @Test
    void aLaunchNamingAPhaser_isRefused() {
        ActionResult r = session.executeAction(launch("Phaser1-1", "DEFENSIVE"));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("Plasma rack not found"), r.getMessage());
    }

    /**
     * And the rules reach the wire: a cruiser is size-4 or larger, so defensive mode cannot engage
     * it (FP10.212) and the refusal arrives through the action rather than only in core.
     */
    @Test
    void defensiveModeAgainstACruiser_isRefusedThroughTheAction() {
        ActionResult r = session.executeAction(launch(portRack().getName(), "DEFENSIVE"));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("FP10.212"), r.getMessage());
    }
}
