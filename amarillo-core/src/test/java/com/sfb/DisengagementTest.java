package com.sfb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.BattleStatus;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Behavioral tests for the C7.0 disengagement family, built after the
 * DisengagementResolver extraction (which moved never-tested code).
 *
 * C7.2 separation: no enemy within 50 hexes, no seeker targeting the ship.
 * C7.1 acceleration: queued at endTurn() when at max acceleration speed with
 * enough warp; the player confirms YES/NO before the next turn's EA starts;
 * exiting via a team's destruction direction destroys the ship instead.
 */
public class DisengagementTest {

    private Game game;
    private Ship fed;     // FedCA, facing 1 (direction A)
    private Ship klingon; // D7

    @Before
    public void setUp() {
        game = new Game();

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
        klingon.setLocation(new Location(11, 10));
        klingon.setFacing(1);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
    }

    private Energy makeAllocation(Ship ship, double warpMovement) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warpMovement);
        return e;
    }

    private void allocate(double fedWarp, double klingonWarp) {
        game.submitAllocation(fed,     makeAllocation(fed,     fedWarp));
        game.submitAllocation(klingon, makeAllocation(klingon, klingonWarp));
    }

    // -------------------------------------------------------------------------
    // C7.2 — Disengagement by separation
    // -------------------------------------------------------------------------

    @Test
    public void separation_succeedsWhenNoEnemyWithin50Hexes() {
        fed.setLocation(new Location(1, 1));
        klingon.setLocation(new Location(42, 32)); // opposite map corner, range > 50

        assertTrue(game.canDisengageBySeparation(fed));
        Game.ActionResult r = game.disengageBySeparation(fed);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(fed.isDisengaged());
        assertNull(fed.getLocation());
    }

    @Test
    public void separation_blockedByEnemyWithin50Hexes() {
        // Default setup: klingon adjacent
        assertFalse(game.canDisengageBySeparation(fed));

        Game.ActionResult r = game.disengageBySeparation(fed);

        assertFalse(r.isSuccess());
        assertFalse(fed.isDisengaged());
    }

    @Test
    public void separation_blockedBySeekerTargetingShip() {
        fed.setLocation(new Location(1, 1));
        klingon.setLocation(new Location(42, 32)); // no enemy in range...

        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("Drone-1");
        drone.setLocation(new Location(5, 5));
        drone.setTarget(fed); // ...but a seeker is still chasing the ship
        game.getSeekers().add(drone);

        assertFalse("An in-flight seeker targeting the ship blocks separation (C7.2)",
                game.canDisengageBySeparation(fed));
    }

    @Test
    public void separation_alreadyDisengagedShipCannotDisengageAgain() {
        fed.setLocation(new Location(1, 1));
        klingon.setLocation(new Location(42, 32));
        assertTrue(game.disengageBySeparation(fed).isSuccess());

        assertFalse(game.canDisengageBySeparation(fed));
        assertFalse(game.disengageBySeparation(fed).isSuccess());
    }

    // -------------------------------------------------------------------------
    // C7.1 — Disengagement by acceleration
    // -------------------------------------------------------------------------

    /**
     * Make fed eligible: slow prior-turn history caps acceleration low enough
     * that full throttle reaches the cap, then run the turn-end check.
     */
    private void makeFedEligibleAndEndTurn() {
        fed.setSpeedPreviousTurn(10);
        fed.setSpeedTwoTurnsAgo(10);
        allocate(30.0, 0.0); // clamped to max acceleration speed
        game.endTurn();
    }

    @Test
    public void accel_shipAtMaxAccelerationIsQueuedAtEndTurn() {
        makeFedEligibleAndEndTurn();

        assertTrue("Fed must be queued for C7.1 confirmation",
                game.getPendingAccelDisengage().contains(fed));
        assertFalse("Speed-0 klingon must not be queued",
                game.getPendingAccelDisengage().contains(klingon));
        assertFalse("Next turn's EA is deferred until the player answers",
                game.isAwaitingAllocation());
    }

    @Test
    public void accel_decline_shipStaysAndNextTurnBegins() {
        makeFedEligibleAndEndTurn();

        Game.ActionResult r = game.confirmAccelDisengage(fed, false);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage().contains("remained"));
        assertFalse(fed.isDisengaged());
        assertNotNull(fed.getLocation());
        assertTrue("Once the queue empties, the next turn's EA begins",
                game.isAwaitingAllocation());
    }

    @Test
    public void accel_confirmTowardSafeDirection_disengages() {
        makeFedEligibleAndEndTurn();

        Game.ActionResult r = game.confirmAccelDisengage(fed, true);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(fed.isDisengaged());
        assertNull(fed.getLocation());
        assertTrue(game.isAwaitingAllocation());
    }

    @Test
    public void accel_unqueuedShipCannotConfirm() {
        makeFedEligibleAndEndTurn();

        Game.ActionResult r = game.confirmAccelDisengage(klingon, true);

        assertFalse(r.isSuccess());
        assertFalse(klingon.isDisengaged());
    }

    // -------------------------------------------------------------------------
    // Destruction directions — resolver-level (the map is only populated via
    // scenario setup in production, so we construct the resolver directly)
    // -------------------------------------------------------------------------

    @Test
    public void accel_confirmIntoDestructionDirection_destroysShip() {
        Player owner = new Player();
        owner.setTeamName("Aggressors");
        fed.setOwner(owner);
        fed.setFacing(1); // facing 1 → exit direction "A"

        Map<String, Set<String>> destructionDirs = new HashMap<>();
        destructionDirs.put("Aggressors", new HashSet<>(Set.of("A")));

        TractorResolver tractors = new TractorResolver(game, game.getShips(),
                game.getSeekers(), game.getActiveShuttles(), new HashMap<>());
        List<Ship> destroyed = new ArrayList<>();
        DisengagementResolver resolver = new DisengagementResolver(game, game.getShips(),
                game.getSeekers(), destroyed, new ArrayList<>(), destructionDirs, tractors);

        String msg = resolver.resolveAccelDisengage(fed, true);

        assertTrue("Log must report the destruction zone: " + msg,
                msg.contains("destruction zone"));
        assertEquals(BattleStatus.DESTROYED, fed.getBattleStatus());
        assertNull(fed.getLocation());
        assertFalse("Destroyed ship leaves the game's ships list",
                game.getShips().contains(fed));
        assertTrue(destroyed.contains(fed));
    }
}
