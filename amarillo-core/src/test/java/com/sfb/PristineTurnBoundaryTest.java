package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.scenario.SavedGame;
import com.sfb.scenario.ScenarioSpec;
import com.sfb.systemgroups.Energy;

/**
 * The one moment a save may be taken from: the start of a turn, before any player has submitted
 * an Energy Allocation Form.
 *
 * <h2>The bug these were written for</h2>
 * The save gate read {@code isAwaitingAllocation()}, which stays true from the first form of the
 * round to the last. {@code exportState()} carries neither the allocation queue nor the flag, so
 * a save taken halfway through reloaded with EVERY ship queued again — and the forms already
 * submitted were silently discarded. Reproduced before the fix, with a two-ship game:
 *
 * <pre>
 *   after ONE allocation:  awaiting=true  queue=1  head=IKV Blade  hood warp=6.0
 *   RESTORED:              awaiting=true  queue=2  head=USS Hood   hood warp=0.0
 *   restore problems: []
 * </pre>
 *
 * A declared self-destruct (D5.1) went the same way, {@code selfDestructDeclared} back to false,
 * and {@code restore()} reported no problems in either case. That is the shape of bug this
 * project cares most about: not a crash, but a quiet wrong answer.
 *
 * <p>The fix refuses the save rather than serialising a half-finished round — see
 * {@link Game#isPristineTurnBoundary()} for why that is the right trade.
 */
public class PristineTurnBoundaryTest {

    // ---------------------------------------------------------------- fixtures

    private static ScenarioSpec twoShips() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Pristine fixture";
        spec.year = 180;
        spec.mapCols = 42;
        spec.mapRows = 32;

        ScenarioSpec.SideSpec fed = new ScenarioSpec.SideSpec();
        fed.faction = "Federation";
        fed.name = "Starfleet";
        fed.ships = new ArrayList<>(List.of(ship("Federation", "CA", "USS Hood", "1010")));

        ScenarioSpec.SideSpec kli = new ScenarioSpec.SideSpec();
        kli.faction = "Klingon";
        kli.name = "Empire";
        kli.ships = new ArrayList<>(List.of(ship("Klingon", "D7", "IKV Blade", "1016")));

        spec.sides = new ArrayList<>(List.of(fed, kli));
        return spec;
    }

    private static ScenarioSpec.ShipSetup ship(String faction, String type, String name, String hex) {
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.faction = faction;
        setup.type = type;
        setup.shipName = name;
        setup.startHex = hex;
        setup.startHeading = "A";
        return setup;
    }

    private static Game game(ScenarioSpec spec) {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        Game game = new Game();
        game.setupFromScenario(spec);   // ends in startTurn()
        return game;
    }

    private static Ship named(Game game, String name) {
        return game.getShips().stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("fixture: no ship " + name));
    }

    // ---------------------------------------------------------------- the window

    /** Accepted: the start of a turn, nobody has decided anything. */
    @Test
    public void aFreshTurnIsAPristineBoundary() {
        Game game = game(twoShips());

        assertTrue("the turn has opened for allocation", game.isAwaitingAllocation());
        assertEquals("both ships are queued", 2, game.getAllocationQueue().size());
        assertTrue(game.isPristineTurnBoundary());
    }

    /** Refused: one of two ships has allocated. This is the case that lost a player's form. */
    @Test
    public void oneShipHavingAllocatedClosesTheWindow() {
        Game game = game(twoShips());
        Ship hood = named(game, "USS Hood");

        Energy e = new Energy();
        e.setWarpMovement(6);
        game.submitAllocation(hood, e);

        assertTrue("the round is still open for the other ship", game.isAwaitingAllocation());
        assertEquals(1, game.getAllocationQueue().size());
        assertFalse("but a save here would discard the Hood's form",
                game.isPristineTurnBoundary());
    }

    /**
     * Refused: a self-destruct declaration (D5.1) is submitted as an empty form, so it closes
     * the window like any other. It is the costlier of the two losses — the declaration simply
     * vanished and the ship did not explode.
     */
    @Test
    public void aSelfDestructDeclarationClosesTheWindow() {
        Game game = game(twoShips());
        Ship blade = named(game, "IKV Blade");
        // (D5.51) a healthy ship may not self-destruct; this one is down to its last crew unit.
        blade.getCrew().setAvailableCrewUnits(1);

        Game.ActionResult declared = game.declareSelfDestruct(blade);

        assertTrue(declared.getMessage(), declared.isSuccess());
        assertTrue(blade.isSelfDestructDeclared());
        assertFalse("a save here would lose the declaration", game.isPristineTurnBoundary());
    }

    /** Refused: once the last form is in the turn is under way, which was already refused. */
    @Test
    public void theWindowIsClosedOnceTheTurnIsUnderWay() {
        Game game = game(twoShips());
        for (Ship s : new ArrayList<>(game.getShips()))
            game.submitAllocation(s, s.buildAutoAllocation());

        assertFalse(game.isAwaitingAllocation());
        assertFalse(game.isPristineTurnBoundary());
    }

    /** And it opens again at the next turn, so a save is available once per turn. */
    @Test
    public void theWindowReopensOnTheNextTurn() {
        Game game = game(twoShips());
        game.submitAllocation(named(game, "USS Hood"), new Energy());
        assertFalse(game.isPristineTurnBoundary());

        game.startTurn();

        assertTrue("a new turn is a new window", game.isPristineTurnBoundary());
    }

    // ---------------------------------------------------------------- the trap

    /**
     * The non-obvious half, and the reason this is a flag rather than a queue-size comparison.
     *
     * <p>{@code startTurn()} auto-submits for every unit whose allocation holds no choice — a
     * planet's phaser battery has nothing for a player to decide, and six of them meant six
     * identical forms a turn. So the queue is ALREADY shorter than the ship list before anybody
     * has chosen anything, and "queue.size() == ships.size()" would refuse the save at the very
     * moment it is safe. Here the base allocates itself and the window stays open.
     */
    @Test
    public void unitsThatAllocateThemselvesDoNotCloseTheWindow() {
        ScenarioSpec spec = twoShips();

        ScenarioSpec.TerrainSetup planet = new ScenarioSpec.TerrainSetup();
        planet.type = "PLANET";
        planet.hex = "2116";
        planet.name = "Nivram";
        spec.terrain = new ArrayList<>(List.of(planet));

        ScenarioSpec.ShipSetup base = new ScenarioSpec.ShipSetup();
        base.faction = "Neutral";
        base.type = "GBD1";
        base.shipName = "Nivram Battery";
        base.planet = "Nivram";
        base.planetFace = "B";
        ScenarioSpec.SideSpec defenders = new ScenarioSpec.SideSpec();
        defenders.faction = "Neutral";
        defenders.name = "Defenders";
        defenders.ships = new ArrayList<>(List.of(base));
        spec.sides.add(defenders);

        Game game = game(spec);

        Ship battery = named(game, "Nivram Battery");
        assertTrue("fixture: this base's allocation holds no choice (R1.14)",
                battery.hasNoEnergyAllocationChoice());
        assertEquals("three units, but the base filled its own form in",
                3, game.getShips().size());
        assertEquals("so only the two ships are queued", 2, game.getAllocationQueue().size());

        assertTrue("an automatic form is not a player's decision",
                game.isPristineTurnBoundary());
    }

    // ---------------------------------------------------------------- the round trip

    /**
     * What a save can be taken from is what it loads into: {@code setupFromScenario} ends in
     * {@code startTurn()}, so a restored battle lands on a pristine boundary and can be saved
     * again at once. Without this the gate could be self-inconsistent — refusing to re-save a
     * game it had just loaded.
     */
    @Test
    public void aRestoredBattleLandsOnAPristineBoundary() throws Exception {
        Game before = game(twoShips());
        String json = SavedGame.of(before, twoShips(), "pristine").toJsonString();

        SavedGame.Restored restored = SavedGame.fromJsonString(json).restore();

        assertEquals("a clean save should load clean", List.of(), restored.problems());
        assertTrue(restored.game().isPristineTurnBoundary());
    }
}
