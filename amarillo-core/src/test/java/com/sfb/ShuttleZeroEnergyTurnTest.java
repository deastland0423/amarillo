package com.sfb;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * (C5.13) The shuttle half of the zero-energy turn.
 *
 * <h2>The rule</h2>
 * "A shuttle which does not move under its own power for an entire turn may make a zero energy
 * turn of up to 180° on the last impulse of that turn." The ship half of the same clause gets
 * 60° and is tested in {@link TacticalManeuverTest}; this is deliberately a separate mechanism,
 * because the two ask different questions and get different answers:
 *
 * <ul>
 *   <li>A SHIP is judged on its allocation form — no power to any movement function. A craft has
 *       no allocation form, so it is judged on its SPEED, which is what the rule actually asks
 *       about ("did not move under its own power"). The owner's call, and it is the better fit:
 *       speed 0 is exactly the condition, where for a ship speed 0 can also mean it paid for
 *       movement and sat still anyway.</li>
 *   <li>A ship turns 60°. A craft turns "up to 180°", which on a hex grid reaches all six
 *       facings — so there is no angle to check at all, only that it stood still.</li>
 * </ul>
 *
 * <p>Speed is read from {@code getSpeed()} and not {@code getCurrentSpeed()}, because that is
 * the field {@code ShuttleMover} consults to decide whether a craft moves this impulse. The two
 * can disagree: J1.6223 gives a craft under tractor recovery the HOLDING ship's speed, and a
 * craft being dragged at speed 0 is not moving under its own power — so the rule allows its
 * turn, and a test of both fields would wrongly refuse it.
 */
public class ShuttleZeroEnergyTurnTest {

    /** The whole clause: a parked craft turns on the last impulse, free. */
    @Test
    public void aStationaryShuttleTurnsOnImpulse32() {
        Game game = battleAtImpulse(32, 0);
        AdminShuttle shuttle = shuttleIn(game);

        ActionResult result = game.performShuttleZeroEnergyTurn(shuttle, 5);

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals(5, shuttle.getFacing());
    }

    /**
     * "Up to 180°" reaches every facing on a hex grid, so the full reversal is legal — and is
     * the case worth asserting, since it is what separates this from the ship's 60°.
     */
    @Test
    public void theTurnMayBeAFullHundredAndEighty() {
        Game game = battleAtImpulse(32, 0);
        AdminShuttle shuttle = shuttleIn(game);
        assertEquals("fixture: starts facing A", 1, shuttle.getFacing());

        assertTrue(game.performShuttleZeroEnergyTurn(shuttle, 13).isSuccess());
        assertEquals("facing D is 180° from facing A", 13, shuttle.getFacing());
    }

    /** And 120°, to show nothing in between is being rejected. */
    @Test
    public void theTurnMayBeOneHundredAndTwenty() {
        Game game = battleAtImpulse(32, 0);

        assertTrue(game.performShuttleZeroEnergyTurn(shuttleIn(game), 9).isSuccess());
        assertEquals(9, shuttleIn(game).getFacing());
    }

    /** "the last impulse of that turn" — not any impulse before it. */
    @Test
    public void itIsRefusedBeforeImpulse32() {
        Game game = battleAtImpulse(20, 0);
        AdminShuttle shuttle = shuttleIn(game);

        ActionResult early = game.performShuttleZeroEnergyTurn(shuttle, 5);

        assertFalse(early.isSuccess());
        assertTrue("the refusal names the rule: " + early.getMessage(),
                early.getMessage().contains("C5.13"));
        assertEquals("and leaves the facing alone", 1, shuttle.getFacing());
    }

    /**
     * A craft that moved under its own power has no free turn.
     *
     * <p>The MESSAGE is asserted, not just the refusal, and that is the point of this case.
     * {@code isZeroEnergyTurnAvailable()} tests the speed as well, so deleting the explicit
     * check in {@code ShuttleMover} still refuses — it just refuses with the wrong reason,
     * telling a player who never turned that he has already turned. Proven by mutation: with
     * only {@code assertFalse} here, removing that check broke nothing.
     */
    @Test
    public void aMovingShuttleHasNoFreeTurn() {
        Game game = battleAtImpulse(32, 6);
        AdminShuttle shuttle = shuttleIn(game);

        ActionResult refused = game.performShuttleZeroEnergyTurn(shuttle, 5);

        assertFalse("speed 6 is movement under its own power (C5.13)", refused.isSuccess());
        assertTrue("and says so, rather than blaming a turn it never made: " + refused.getMessage(),
                refused.getMessage().contains("moved under its own power"));
        assertEquals(1, shuttle.getFacing());
    }

    /** One per turn. */
    @Test
    public void onlyOneFreeTurnPerTurn() {
        Game game = battleAtImpulse(32, 0);
        AdminShuttle shuttle = shuttleIn(game);

        assertTrue(game.performShuttleZeroEnergyTurn(shuttle, 5).isSuccess());
        ActionResult second = game.performShuttleZeroEnergyTurn(shuttle, 9);

        assertFalse(second.isSuccess());
        assertEquals("the second request leaves the first turn's facing", 5, shuttle.getFacing());
    }

    /** A facing that is not one of the six bearings is refused rather than silently rounded. */
    @Test
    public void aFacingThatIsNotABearingIsRefused() {
        Game game = battleAtImpulse(32, 0);
        AdminShuttle shuttle = shuttleIn(game);

        assertFalse(game.performShuttleZeroEnergyTurn(shuttle, 3).isSuccess());
        assertEquals(1, shuttle.getFacing());
    }

    /** Turning to the facing it already has is not a turn, and must not spend the free one. */
    @Test
    public void turningToTheFacingItAlreadyHasIsRefusedAndCostsNothing() {
        Game game = battleAtImpulse(32, 0);
        AdminShuttle shuttle = shuttleIn(game);

        assertFalse(game.performShuttleZeroEnergyTurn(shuttle, 1).isSuccess());

        assertTrue("the free turn is still there", shuttle.isZeroEnergyTurnAvailable());
        assertTrue(game.performShuttleZeroEnergyTurn(shuttle, 5).isSuccess());
    }

    // ---------------------------------------------------------------- fighters

    /**
     * C5.13 says "a shuttle" and does not exclude fighters, so a parked fighter gets the free
     * turn as well as its once-a-turn HET (C6.42). They are different things: the HET is a
     * maneuver a fighter can make at speed, this is what a stationary craft does with
     * compressed gas.
     */
    @Test
    public void aParkedFighterGetsItToo() {
        Game game = battleAtImpulse(32, 0);
        Fighter fighter = fighterIn(game);

        assertTrue(game.performShuttleZeroEnergyTurn(fighter, 5).isSuccess());
        assertEquals(5, fighter.getFacing());
    }

    /**
     * And it comes back at the turn boundary.
     *
     * <p>This is the case that fails if {@code Fighter.startTurn()} ever loses its
     * {@code super.startTurn()} call — which is not hypothetical. That method existed for
     * months with NOTHING calling it, and every per-turn counter it clears was cleared never:
     * J4.241's two drones a turn became two a game. The flag now lives on {@code Shuttle}, one
     * level up, so a fighter only sees the reset through that super call.
     */
    @Test
    public void theFightersFreeTurnComesBackNextTurn() {
        Game game = battleAtImpulse(32, 0);
        Fighter fighter = fighterIn(game);
        assertTrue(game.performShuttleZeroEnergyTurn(fighter, 5).isSuccess());
        assertFalse("spent", fighter.isZeroEnergyTurnAvailable());

        fighter.startTurn();

        assertTrue("a new turn, a new free turn (C5.13)", fighter.isZeroEnergyTurnAvailable());
    }

    /** The same for a plain shuttle, which gets the reset directly. */
    @Test
    public void aShuttlesFreeTurnComesBackNextTurn() {
        Game game = battleAtImpulse(32, 0);
        AdminShuttle shuttle = shuttleIn(game);
        assertTrue(game.performShuttleZeroEnergyTurn(shuttle, 5).isSuccess());

        shuttle.startTurn();

        assertTrue(shuttle.isZeroEnergyTurnAvailable());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Two ships, one admin shuttle and one fighter at the given speed, parked in the Movement
     * phase of the given impulse.
     */
    private static Game battleAtImpulse(int impulse, int craftSpeed) {
        Game game = new Game();

        Player fed = new Player();
        fed.setName("Captain Dan");
        fed.setTeamName("Federation");
        Player kli = new Player();
        kli.setName("Captain Dave");
        kli.setTeamName("Klingons");
        game.getPlayers().add(fed);
        game.getPlayers().add(kli);

        game.getShips().add(ship(FederationShips.getFedCa(), "USS Xerxes", 10, 10, fed));
        game.getShips().add(ship(KlingonShips.getD7(), "KHS Mystic", 20, 20, kli));

        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            game.submitAllocation(s, e);
        }

        AdminShuttle shuttle = new AdminShuttle();
        shuttle.setName("Xerxes-Shuttle-1");
        shuttle.setParentShipName("USS Xerxes");
        shuttle.setOwner(fed);
        shuttle.setLocation(new Location(10, 11));
        shuttle.setFacing(1);
        shuttle.setSpeed(craftSpeed);
        game.getActiveShuttles().add(shuttle);

        Fighter fighter = CataloguedFighter.of("f14");
        fighter.setName("Xerxes-F-1");
        fighter.setOwner(fed);
        fighter.setLocation(new Location(10, 12));
        fighter.setFacing(1);
        fighter.setSpeed(craftSpeed);
        game.getActiveShuttles().add(fighter);

        while (game.getCurrentImpulse() != impulse
                || game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        return game;
    }

    private static AdminShuttle shuttleIn(Game game) {
        for (com.sfb.objects.shuttles.Shuttle s : game.getActiveShuttles())
            if (s instanceof AdminShuttle)
                return (AdminShuttle) s;
        throw new AssertionError("no admin shuttle");
    }

    private static Fighter fighterIn(Game game) {
        for (com.sfb.objects.shuttles.Shuttle s : game.getActiveShuttles())
            if (s instanceof Fighter)
                return (Fighter) s;
        throw new AssertionError("no fighter");
    }

    private static Ship ship(Map<String, Object> spec, String name, int x, int y, Player owner) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        s.setSpeedPreviousTurn(0);
        s.setSpeedTwoTurnsAgo(0);
        s.setOwner(owner);
        owner.getPlayerUnits().add(s);
        return s;
    }
}
