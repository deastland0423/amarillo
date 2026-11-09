package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.BattleStatus;
import com.sfb.properties.DisengagementMethod;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.scenario.ScenarioSpec;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * (C7.0) A scenario saying WHICH means of disengagement each side may use.
 *
 * <h2>Why it was needed</h2>
 * "There are four means of disengagement: acceleration, separation, sublight evasion, and
 * automatic." A published scenario routinely allows only some of them — a ship told to run for
 * a border may not simply outrun the battle — and until this existed a scenario could say
 * nothing at all about it. The engine offered every method it had built, to everyone.
 *
 * <p>The direction half is a separate shape from what was already there, and the distinction is
 * the whole point. {@code destructionDirections} says which ways out KILL the ship.
 * {@code accelDisengageDirections} says which ways out EXIST. SH47.41's "the Kzinti can only
 * disengage from the 01xx map edge" is the second kind — a refusal, not a death sentence — and
 * expressing it with the kill-list, the only way available before, states a different rule and
 * a harsher one.
 *
 * <h2>The default carries every scenario written before the field</h2>
 * A side that names no methods may use all of them. That is asserted first and deliberately:
 * there are authored scenarios and saved games in {@code data/} that say nothing here, and if
 * absent meant "none" every one of them would quietly become a battle nobody can leave. The
 * same reasoning as R1.7's acceleration flags, which default permissive for the same reason.
 */
public class ScenarioDisengagementTest {

    // ---------------------------------------------------------------- the default

    /** Say nothing, get everything — the clause that protects every existing scenario. */
    @Test
    public void aSideThatSaysNothingMayUseEveryMethod() {
        Game game = battle(side -> { });
        Ship fed = shipNamed(game, "USS Enterprise");

        for (DisengagementMethod method : DisengagementMethod.values())
            assertTrue(method + " must be permitted when the scenario is silent",
                    game.sideMayDisengageBy(fed, method));
        assertTrue("and no direction restriction either",
                game.allowedAccelDirections(fed).isEmpty());
    }

    /**
     * A ship with no owner is unrestricted. Not a nicety: a great many fixtures and a neutral
     * freighter have no player, and there is no side to read a restriction from.
     */
    @Test
    public void aShipWithNoOwnerIsUnrestricted() {
        Game game = battle(side -> side.disengagement = Set.of(DisengagementMethod.SEPARATION));
        Ship orphan = new Ship();
        orphan.init(FederationShips.getFedCa());
        orphan.setName("Nobody's");

        assertTrue(game.sideMayDisengageBy(orphan, DisengagementMethod.ACCELERATION));
        assertTrue(game.sideMayDisengageBy(null, DisengagementMethod.MAP_EDGE));
    }

    // ---------------------------------------------------------------- denying a method

    /**
     * (C7.1) Denied the method, a ship that has met every other condition is never OFFERED the
     * disengagement — tested at the queue, not on the predicate. A predicate that answers
     * correctly while nothing asks it is how this project has twice shipped an unreachable rule.
     */
    @Test
    public void denyingAccelerationKeepsTheShipOutOfTheQueue() {
        Game game = battle(side -> side.disengagement =
                Set.of(DisengagementMethod.MAP_EDGE, DisengagementMethod.SEPARATION));
        Ship fed = shipNamed(game, "USS Enterprise");
        runFullThrottleTurn(game, fed);

        assertEquals("fixture: it IS at its ceiling, so only the scenario can be keeping it out",
                fed.getAccelerationCeilingThisTurn(), fed.getSpeed());
        assertFalse("C7.0: this side may not disengage by acceleration",
                game.getPendingAccelDisengage().contains(fed));
    }

    /** And permitted, the same ship IS offered it — or the test above proves nothing. */
    @Test
    public void permittingAccelerationStillOffersIt() {
        Game game = battle(side -> side.disengagement =
                Set.of(DisengagementMethod.ACCELERATION));
        Ship fed = shipNamed(game, "USS Enterprise");
        runFullThrottleTurn(game, fed);

        assertTrue("the only method it has, and it must still work",
                game.getPendingAccelDisengage().contains(fed));
    }

    /** (C7.2) Separation, denied. */
    @Test
    public void denyingSeparationRefusesIt() {
        Game game = battle(side -> side.disengagement = Set.of(DisengagementMethod.ACCELERATION));
        Ship fed = shipNamed(game, "USS Enterprise");
        clearTheArea(game, fed);

        assertFalse(game.canDisengageBySeparation(fed));
        Game.ActionResult refused = game.disengageBySeparation(fed);

        assertFalse(refused.isSuccess());
        assertFalse(fed.isDisengaged());
    }

    /** Permitted, in the same otherwise-identical position, it works. */
    @Test
    public void permittingSeparationStillAllowsIt() {
        Game game = battle(side -> side.disengagement = Set.of(DisengagementMethod.SEPARATION));
        Ship fed = shipNamed(game, "USS Enterprise");
        clearTheArea(game, fed);

        assertTrue(game.canDisengageBySeparation(fed));
        assertTrue(game.disengageBySeparation(fed).isSuccess());
        assertTrue(fed.isDisengaged());
    }

    /**
     * The map edge, denied — and this is the case where the two kinds of restriction had to be
     * kept apart. The move is REFUSED and the ship is still on the map. It is not destroyed:
     * destruction is what {@code destructionEdges} means, and a scenario saying "you must fight
     * this out" must not become one saying "and die if you try to leave".
     */
    @Test
    public void denyingTheMapEdgeRefusesTheMoveRatherThanDestroyingTheShip() {
        Game game = battle(side -> side.disengagement =
                Set.of(DisengagementMethod.ACCELERATION, DisengagementMethod.SEPARATION));
        Ship fed = shipNamed(game, "USS Enterprise");
        atTheTopEdge(game, fed);

        Game.ActionResult result = game.moveForward(fed);

        assertFalse(result.getMessage(), result.isSuccess());
        assertTrue("the refusal says why: " + result.getMessage(),
                result.getMessage().contains("cannot leave the map"));
        assertFalse("and emphatically does NOT kill it", fed.isDisengaged());
        assertEquals(BattleStatus.ACTIVE, fed.getBattleStatus());
        assertNotNull("still on the map", fed.getLocation());
        assertEquals(new Location(10, 1), fed.getLocation());
    }

    /** Permitted, the same move takes the ship off the board as it always did. */
    @Test
    public void permittingTheMapEdgeStillLetsTheShipLeave() {
        Game game = battle(side -> side.disengagement = Set.of(DisengagementMethod.MAP_EDGE));
        Ship fed = shipNamed(game, "USS Enterprise");
        atTheTopEdge(game, fed);

        Game.ActionResult result = game.moveForward(fed);

        assertTrue(result.getMessage(), result.isSuccess());
        assertTrue(fed.isDisengaged());
        assertNull(fed.getLocation());
    }

    // ---------------------------------------------------------------- directions

    /**
     * (C7.1) A permit-list offers only the directions it names. The ship faces A; the scenario
     * allows only D; so there is no offer, and no destruction either.
     */
    @Test
    public void aPermitListOffersOnlyTheDirectionsItNames() {
        Game game = battle(side -> side.accelDisengageDirections = Set.of("D"));
        Ship fed = shipNamed(game, "USS Enterprise");
        assertEquals("fixture: facing A", 1, fed.getFacing());
        runFullThrottleTurn(game, fed);

        assertFalse("A is not on the permit-list, so this is not a way out",
                game.getPendingAccelDisengage().contains(fed));
        assertEquals("and it is still here, unharmed", BattleStatus.ACTIVE, fed.getBattleStatus());
    }

    /** Facing a direction the list DOES name, the offer appears. */
    @Test
    public void aShipFacingAPermittedDirectionIsOffered() {
        Game game = battle(side -> side.accelDisengageDirections = Set.of("A", "D"));
        Ship fed = shipNamed(game, "USS Enterprise");
        runFullThrottleTurn(game, fed);

        assertTrue(game.getPendingAccelDisengage().contains(fed));
        assertTrue(game.confirmAccelDisengage(fed, true).isSuccess());
        assertTrue(fed.isDisengaged());
    }

    /**
     * Both lists at once, on the same direction. The destruction check runs first, so the ship
     * dies — which is the trap the scenario laid, and the reason the two fields had to stay
     * separate rather than one being expressed in terms of the other.
     */
    @Test
    public void aDirectionBothPermittedAndLethalStillKills() {
        Game game = battle(side -> {
            side.accelDisengageDirections = Set.of("A");
            side.destructionDirections = Set.of("A");
        });
        Ship fed = shipNamed(game, "USS Enterprise");
        runFullThrottleTurn(game, fed);
        assertTrue("fixture: it is offered, because A is permitted",
                game.getPendingAccelDisengage().contains(fed));

        game.confirmAccelDisengage(fed, true);

        assertEquals("but A is also a destruction zone, and that wins",
                BattleStatus.DESTROYED, fed.getBattleStatus());
        assertFalse(fed.isDisengaged());
    }

    // ---------------------------------------------------------------- per side, not per battle

    /**
     * Each side gets its OWN permit-list, and they are enforced independently.
     *
     * <p>The owner's condition on the whole feature: "so long as we are able to have separate
     * accelDisengageDirections for each side in the battle". Both fields live on
     * {@code SideSpec} rather than on the scenario, so that is the shape — but every other
     * direction case here restricts one side only, which means a map keyed wrongly, or a
     * last-side-wins bug in the load loop, would pass all of them. This is the case that fails.
     *
     * <p>Two ships, both facing A, both at full throttle. The Federation may leave heading A;
     * the Klingons may leave heading D. One offer, not two, and not none.
     */
    @Test
    public void eachSideGetsItsOwnPermittedDirections() {
        Game game = twoRestrictedSides(Set.of("A"), Set.of("D"));
        Ship fed = shipNamed(game, "USS Enterprise");
        Ship kli = shipNamed(game, "IKV Saber");
        assertEquals("fixture: both face A", fed.getFacing(), kli.getFacing());

        runFullThrottleTurnForAll(game);

        assertTrue("the Federation may leave heading A",
                game.getPendingAccelDisengage().contains(fed));
        assertFalse("the Klingons may only leave heading D, and are facing A",
                game.getPendingAccelDisengage().contains(kli));
    }

    /** And the other way round, so neither side is merely benefiting from being listed first. */
    @Test
    public void theSameIsTrueWithTheSidesReversed() {
        Game game = twoRestrictedSides(Set.of("D"), Set.of("A"));
        Ship fed = shipNamed(game, "USS Enterprise");
        Ship kli = shipNamed(game, "IKV Saber");

        runFullThrottleTurnForAll(game);

        assertFalse(game.getPendingAccelDisengage().contains(fed));
        assertTrue(game.getPendingAccelDisengage().contains(kli));
    }

    /** The same independence for the METHOD list: one side grounded, the other not. */
    @Test
    public void eachSideGetsItsOwnPermittedMethods() {
        ScenarioSpec spec = twoSides();
        spec.sides.get(0).disengagement = Set.of(DisengagementMethod.SEPARATION);
        spec.sides.get(1).disengagement = Set.of(DisengagementMethod.ACCELERATION);
        Game game = load(spec);
        Ship fed = shipNamed(game, "USS Enterprise");
        Ship kli = shipNamed(game, "IKV Saber");

        assertFalse(game.sideMayDisengageBy(fed, DisengagementMethod.ACCELERATION));
        assertTrue(game.sideMayDisengageBy(kli, DisengagementMethod.ACCELERATION));
        assertTrue(game.sideMayDisengageBy(fed, DisengagementMethod.SEPARATION));
        assertFalse(game.sideMayDisengageBy(kli, DisengagementMethod.SEPARATION));
    }

    /** A side restricted beside one that said nothing leaves the silent one unrestricted. */
    @Test
    public void restrictingOneSideDoesNotRestrictTheOther() {
        ScenarioSpec spec = twoSides();
        spec.sides.get(0).disengagement = Set.of(DisengagementMethod.SEPARATION);
        spec.sides.get(0).accelDisengageDirections = Set.of("D");
        Game game = load(spec);
        Ship kli = shipNamed(game, "IKV Saber");

        for (DisengagementMethod method : DisengagementMethod.values())
            assertTrue("the Klingon side said nothing and keeps " + method,
                    game.sideMayDisengageBy(kli, method));
        assertTrue("and has no direction restriction of its own",
                game.allowedAccelDirections(kli).isEmpty());
    }

    // ---------------------------------------------------------------- the data file

    /**
     * A method name the engine does not have fails to LOAD, loudly. The alternative — a lenient
     * mapper reading it as null — would let a scenario permit sublight evasion (C7.3, unbuilt)
     * and have the engine silently ignore the rule, which is worse than refusing the file.
     */
    @Test
    public void anUnknownMethodNameFailsToLoad() throws IOException {
        File f = File.createTempFile("scenario-bad-disengage", ".json");
        f.deleteOnExit();
        Files.write(f.toPath(), ("{\"name\":\"Bad\",\"sides\":[{\"name\":\"A\","
                + "\"disengagement\":[\"TELEPORTATION\"]}]}").getBytes(StandardCharsets.UTF_8));

        try {
            ScenarioSpec.fromJson(f);
            fail("an unknown means of disengagement must not load silently");
        } catch (IOException expected) {
            assertTrue("the error should name the offending value: " + expected.getMessage(),
                    expected.getMessage().contains("TELEPORTATION"));
        }
    }

    /** The three built methods all parse from the file, by name. */
    @Test
    public void theBuiltMethodNamesAllLoad() throws IOException {
        File f = File.createTempFile("scenario-disengage", ".json");
        f.deleteOnExit();
        Files.write(f.toPath(), ("{\"name\":\"Fine\",\"sides\":[{\"name\":\"A\","
                + "\"disengagement\":[\"ACCELERATION\",\"MAP_EDGE\",\"SEPARATION\"],"
                + "\"accelDisengageDirections\":[\"A\",\"B\"]}]}")
                .getBytes(StandardCharsets.UTF_8));

        ScenarioSpec spec = ScenarioSpec.fromJson(f);

        assertEquals(3, spec.sides.get(0).disengagement.size());
        assertTrue(spec.sides.get(0).disengagement.contains(DisengagementMethod.MAP_EDGE));
        assertEquals(Set.of("A", "B"), spec.sides.get(0).accelDisengageDirections);
    }

    // ---------------------------------------------------------------- helpers

    /** Run a whole turn at full throttle, which is what C7.11 asks of a disengaging ship. */
    private static void runFullThrottleTurn(Game game, Ship fed) {
        // A slow history caps acceleration low enough that full throttle reaches the ceiling.
        fed.setSpeedPreviousTurn(10);
        fed.setSpeedTwoTurnsAgo(10);
        for (Ship s : game.getShips())
            game.submitAllocation(s, allocation(s, s == fed ? 30.0 : 0.0));
        game.endTurn();
    }

    private static Energy allocation(Ship ship, double warp) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setWarpMovement(warp);
        return e;
    }

    /** C7.2 wants no enemy within 50 hexes and no seeker chasing. */
    private static void clearTheArea(Game game, Ship fed) {
        fed.setLocation(new Location(1, 1));
        for (Ship s : game.getShips())
            if (s != fed)
                s.setLocation(new Location(42, 32));
    }

    /** One hex short of the top edge, facing A, with the speed to cross it. */
    private static void atTheTopEdge(Game game, Ship fed) {
        for (Ship s : game.getShips())
            game.submitAllocation(s, allocation(s, s == fed ? 8.0 : 0.0));
        fed.setLocation(new Location(10, 1));
        fed.setFacing(1);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT
                || !game.canMoveThisImpulse(fed))
            game.advancePhase();
    }

    /** Run the turn at full throttle for EVERY ship, so one side's result is not the fixture. */
    private static void runFullThrottleTurnForAll(Game game) {
        for (Ship s : game.getShips()) {
            s.setSpeedPreviousTurn(10);
            s.setSpeedTwoTurnsAgo(10);
        }
        for (Ship s : game.getShips())
            game.submitAllocation(s, allocation(s, 30.0));
        game.endTurn();
    }

    /** Two sides, each with its own C7.1 permit-list. */
    private static Game twoRestrictedSides(Set<String> fedDirs, Set<String> kliDirs) {
        ScenarioSpec spec = twoSides();
        spec.sides.get(0).accelDisengageDirections = fedDirs;
        spec.sides.get(1).accelDisengageDirections = kliDirs;
        return load(spec);
    }

    /**
     * Two sides, one ship each, with the Federation side handed to the given mutator so each
     * test states only the restriction it is about.
     */
    private static Game battle(java.util.function.Consumer<ScenarioSpec.SideSpec> federation) {
        ScenarioSpec spec = twoSides();
        federation.accept(spec.sides.get(0));
        return load(spec);
    }

    /** The bare two-side scenario both helpers above start from. */
    private static ScenarioSpec twoSides() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Disengagement rules";
        spec.sides = new ArrayList<>();

        ScenarioSpec.SideSpec fed = new ScenarioSpec.SideSpec();
        fed.name = "Federation";
        fed.faction = "Federation";
        spec.sides.add(fed);

        ScenarioSpec.SideSpec kli = new ScenarioSpec.SideSpec();
        kli.name = "Klingons";
        kli.faction = "Klingon";
        spec.sides.add(kli);
        return spec;
    }

    private static Game load(ScenarioSpec spec) {
        Game game = new Game();
        game.setupFromScenario(spec, List.of(
                List.of(ship(FederationShips.getFedCa(), "USS Enterprise", 10, 10, "Federation")),
                List.of(ship(KlingonShips.getD7(), "IKV Saber", 11, 10, "Klingons"))),
                Map.of());
        return game;
    }

    private static Ship ship(Map<String, Object> data, String name, int x, int y, String team) {
        Ship s = new Ship();
        s.init(data);
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        s.setSpeedPreviousTurn(31);
        s.setSpeedTwoTurnsAgo(31);
        Player owner = new Player();
        owner.setName(team);
        owner.setTeamName(team);   // what sideMayDisengageBy reads
        s.setOwner(owner);
        owner.getPlayerUnits().add(s);
        return s;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("no ship called " + name);
    }
}
