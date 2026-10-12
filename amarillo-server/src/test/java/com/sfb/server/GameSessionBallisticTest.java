package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.KzintiShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The LAUNCH_SEEKER_BALLISTIC action: the wire layer for F4.0 and P2.713.
 *
 * <h2>Why one action and not two</h2>
 * A ballistic shot at a bare hex and a bombardment of a ground base differ by one thing — whether
 * a target was NAMED — and they are offered from the same chooser because the rules offer both
 * from the same hex. F4.21 gives four reasons to fire at an empty hex (minefield detection,
 * clearing asteroids, saturating defences, deception) and P2.713 makes the same hex a bombardment
 * if a base is standing in it. Splitting them into two actions would have made the client decide
 * which rule applied before the server had seen the request.
 *
 * <h2>What this layer owes the rules</h2>
 * Nothing of its own: P2.713's four-hex limit and its active-fire-control requirement live in core
 * and are tested there. What it owes is faithful ROUTING — the right weapon, the right door — and
 * a combat log that does not leak. The log line is as vague as LAUNCH_DRONE's for the same reason:
 * what went out and where it was aimed are the firing player's business.
 */
class GameSessionBallisticTest {

    private static final String HOST = "token-host";

    private GameSession session;
    private Game game;
    private Ship kzinti;
    private Ship base;

    @BeforeEach
    void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");

        session = new GameSession("game-1", HOST, "Alice");
        game = session.getGame();

        Terrain planet = new Terrain(TerrainType.PLANET, 20, 20, 1);
        game.addTerrain(planet);

        base = ShipLibrary.createShip(ShipLibrary.get("Neutral", "GBD1"));
        base.setName("Ground Battery");
        assertTrue(game.placeGroundBase(base, planet, 1).isSuccess());

        kzinti = new Ship();
        kzinti.init(KzintiShips.getKzinBC());
        kzinti.setName("KHS Quasar");
        kzinti.setFacing(1);
        Location bhex = base.getLocation();
        kzinti.setLocation(new Location(bhex.getX(), bhex.getY() - 3));   // range 3
        game.getShips().add(kzinti);

        game.startTurn();
        game.submitAllocation(kzinti, allocation(kzinti));
        game.submitAllocation(base, allocation(base));
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        kzinti.setActiveFireControl(true);
        session.drainCombatLog();
    }

    private static Energy allocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        return e;
    }

    private DroneRack rack() {
        for (Weapon w : kzinti.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r)
                return r;
        throw new AssertionError("fixture: the BC should carry racks");
    }

    private ActionRequest ballistic(String targetName) {
        ActionRequest req = new ActionRequest();
        req.setType("LAUNCH_SEEKER_BALLISTIC");
        req.setShipName("KHS Quasar");
        req.setPlayerToken(HOST);
        req.setWeaponNames(List.of(rack().getName()));
        req.setRange(0);                                  // drone index, as LAUNCH_DRONE does
        req.setHexCol(base.getLocation().getX());
        req.setHexRow(base.getLocation().getY());
        if (targetName != null)
            req.setTargetName(targetName);
        return req;
    }

    // ---------------------------------------------------------------- routing

    @Test
    void aHexWithNoNamedTarget_isAnOrdinaryBallisticShot() {
        ActionResult r = session.executeAction(ballistic(null));

        assertTrue(r.isSuccess(), r.getMessage());
        var seeker = game.getSeekers().get(game.getSeekers().size() - 1);
        assertTrue(seeker.isBallistic());
        assertFalse(seeker.getBallisticCourse().isGroundAttack(),
                "a bare hex is not a ground target — it will evade, not explode (F4.21)");
    }

    @Test
    void namingTheBase_makesItABombardment() {
        ActionResult r = session.executeAction(ballistic("Ground Battery"));

        assertTrue(r.isSuccess(), r.getMessage());
        var seeker = game.getSeekers().get(game.getSeekers().size() - 1);
        assertTrue(seeker.getBallisticCourse().isGroundAttack(),
                "it will explode against the base (P2.713)");
        assertSame(base, seeker.getBallisticCourse().getGroundTarget());
    }

    // ---------------------------------------------------------------- the refusals reach the wire

    /** Core owns the rule; what matters here is that its refusal is not swallowed. */
    @Test
    void theFourHexLimitIsReportedToTheClient() {
        Location bhex = base.getLocation();
        kzinti.setLocation(new Location(bhex.getX(), bhex.getY() - 6));

        ActionResult r = session.executeAction(ballistic("Ground Battery"));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("P2.713"), r.getMessage());
    }

    @Test
    void passiveFireControlIsReportedToTheClient() {
        kzinti.setActiveFireControl(false);

        ActionResult r = session.executeAction(ballistic("Ground Battery"));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("passive fire control"), r.getMessage());
    }

    @Test
    void aMissingHexIsRefused() {
        ActionRequest req = ballistic(null);
        req.setHexCol(0);
        req.setHexRow(0);

        ActionResult r = session.executeAction(req);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("No target hex"), r.getMessage());
    }

    @Test
    void anUnknownTargetIsRefusedRatherThanTreatedAsAHexShot() {
        ActionResult r = session.executeAction(ballistic("No Such Base"));

        assertFalse(r.isSuccess(), "silently firing at the hex instead would be a different order");
        assertTrue(r.getMessage().contains("Target not found"), r.getMessage());
    }

    @Test
    void aWeaponThatCannotSeek_isRefused() {
        ActionRequest req = ballistic(null);
        String phaser = kzinti.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w.getName().toLowerCase().contains("phaser"))
                .map(Weapon::getName).findFirst().orElse(null);
        assertNotNull(phaser, "fixture: the BC should carry phasers");
        req.setWeaponNames(List.of(phaser));

        ActionResult r = session.executeAction(req);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("not a seeking weapon"), r.getMessage());
    }

    // ---------------------------------------------------------------- secrecy

    /**
     * The same redaction LAUNCH_DRONE uses. A bombardment is a committing move and the hex it is
     * aimed at is the firing player's business until it arrives.
     */
    @Test
    void theCombatLogDoesNotSayWhatWentOutOrWhereItWasAimed() {
        assertTrue(session.executeAction(ballistic("Ground Battery")).isSuccess());

        String log = String.join("\n", session.drainCombatLog());

        assertTrue(log.contains("KHS Quasar"), log);
        assertFalse(log.contains("Ground Battery"), "the target must not be named: " + log);
        assertFalse(log.toLowerCase().contains("drone"), "nor the weapon type: " + log);
    }
}
