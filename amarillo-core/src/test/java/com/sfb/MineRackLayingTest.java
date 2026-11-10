package com.sfb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.properties.Location;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.properties.ForceCost;
import com.sfb.properties.MineRole;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.MineRack;

/**
 * (M9.21) Laying a mine out of a rack, and the rate that splits on the ship rather than the box.
 *
 * <blockquote>M9.21: "Ships can lay one mine from each mine rack during each turn. <b>Only
 * minesweepers and minelayers use these rates</b> to lay mines. Other ships converted (S7.0) to
 * carry mine racks drop mines through the shuttle hatch at the rate of one per turn per shuttle
 * bay, regardless of the number of racks in the bay."</blockquote>
 */
public class MineRackLayingTest {

    private Game game;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        game = new Game();
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
    }

    /** A hull with mine racks, loaded with NSMs, on the map and able to act. */
    private Ship layer(int boxes, int nsmsPerBox, MineRole role) {
        Map<String, Object> spec = new HashMap<>(RomulanShips.getRomKr());
        spec.put("mineracks", boxes);
        Ship ship = new Ship();
        ship.init(spec);
        ship.setName("Romulan rack ship");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        ship.setMineRole(role);
        for (MineRack rack : ship.getMineRacks())
            rack.loadLarge(nsmsPerBox);
        game.getShips().add(ship);
        return ship;
    }

    // ---------------------------------------------------------------- drawing from the rack

    /** A mine laid from a rack comes out of the rack, not out of the loose NSM allocation. */
    @Test
    public void layingDrawsFromTheRackAndNotFromTheLooseAllocation() {
        Ship ship = layer(2, 4, MineRole.MINELAYER);
        ship.setNuclearSpaceMines(5); // a loose allocation sitting alongside

        Game.ActionResult result = game.dropMine(ship, "NSM");

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals("M9.12 gives rack mines no reloads, so rack stock is spent first",
                7, ship.getRackMineCount());
        assertEquals("the loose allocation is untouched", 5, ship.getNuclearSpaceMines());
    }

    /** With no racks at all, the old loose-allocation path still works exactly as before. */
    @Test
    public void aHullWithoutRacksStillLaysFromItsLooseAllocation() {
        Ship ship = layer(0, 0, MineRole.NONE);
        ship.setNuclearSpaceMines(2);

        assertTrue(game.dropMine(ship, "NSM").isSuccess());
        assertEquals(1, ship.getNuclearSpaceMines());
    }

    // ---------------------------------------------------------------- M9.21, the per-rack rate

    /**
     * A minelayer lays one mine from EACH rack in a turn — so two racks is two mines, and the
     * shuttle bay is never touched.
     */
    @Test
    public void aMinelayerLaysOneMinePerRackPerTurn() {
        Ship ship = layer(2, 4, MineRole.MINELAYER);

        assertTrue("first rack", game.dropMine(ship, "NSM").isSuccess());
        assertTrue("second rack, same turn, no hatch involved",
                game.dropMine(ship, "NSM").isSuccess());

        Game.ActionResult third = game.dropMine(ship, "NSM");
        assertFalse("but not a third: both racks have fired this turn", third.isSuccess());
        assertTrue("and the refusal cites the rate: " + third.getMessage(),
                third.getMessage().contains("M9.21"));
        assertEquals("six NSMs left of eight", 6, ship.getRackMineCount());
    }

    /** And the racks come free again next turn. */
    @Test
    public void theRacksAreFreeAgainTheFollowingTurn() {
        Ship ship = layer(1, 4, MineRole.MINELAYER);
        assertTrue(game.dropMine(ship, "NSM").isSuccess());
        assertFalse("spent for this turn", game.dropMine(ship, "NSM").isSuccess());

        // The turn is DERIVED from the absolute impulse, so a turn passes by advancing the
        // clock 32 impulses. nextImpulse() is the door TurnTracker's javadoc opens for tests.
        int turnBefore = game.getClock().getTurn();
        while (game.getClock().getTurn() == turnBefore)
            game.getClock().nextImpulse();

        assertTrue("M9.21 is a per-TURN rate", game.dropMine(ship, "NSM").isSuccess());
    }

    /**
     * (M9.16) A hull with no declared role is NOT a minesweeper, so the hatch is the bottleneck
     * and its two racks buy it no extra mines.
     *
     * <p>The owner ruled this for the Romulan large Q-ship on 2026-10-19: it is neither a
     * minesweeper (M2.78) nor a minelayer (M2.79), and M9.21 says "only minesweepers and
     * minelayers use these rates". M9.16 backs it up — an S7.0 conversion is "not considered a
     * minesweeper for any purpose".
     */
    @Test
    public void aConvertedHullIsLimitedByTheHatchNotByItsRackCount() {
        Ship ship = layer(2, 4, MineRole.NONE);

        assertTrue("the first goes out through the bay", game.dropMine(ship, "NSM").isSuccess());

        Game.ActionResult second = game.dropMine(ship, "NSM");
        assertFalse("the bay is used for the impulse, so the second is refused even though a"
                + " second loaded rack is sitting right there", second.isSuccess());
        assertTrue("and the refusal is the HATCH's, not M9.21's: " + second.getMessage(),
                second.getMessage().contains("shuttle bay"));
    }

    /**
     * (M9.21) The converted hull's limit is per TURN, not per hatch cooldown — and that is a
     * different number.
     *
     * <p>"Other ships converted (S7.0) to carry mine racks drop mines through the shuttle hatch
     * at the rate of <b>one per turn per shuttle bay</b>, regardless of the number of racks in
     * the bay." The bay's own cooldown is two impulses, so without a per-turn tally a one-bay
     * hull could lay sixteen mines in a turn with every other check satisfied. Found by reading
     * the rule against the code rather than by a failing test, which is why this one exists.
     */
    @Test
    public void aConvertedHullLaysOnePerTurnPerBayHoweverLongTheTurnIs() {
        Ship ship = layer(2, 4, MineRole.NONE);
        int bays = ship.getShuttles().getBays().size();
        assertEquals("fixture: the hull has one bay, so the limit is one mine a turn", 1, bays);

        assertTrue("the first", game.dropMine(ship, "NSM").isSuccess());

        // Let the hatch cool down completely, several times over. The bay is willing; the rule
        // is not.
        for (int i = 0; i < 12; i++) {
            game.getClock().nextImpulse();
            Game.ActionResult again = game.dropMine(ship, "NSM");
            assertFalse("impulse " + i + ": the hatch has cooled but the TURN has not turned",
                    again.isSuccess());
            assertTrue("and it is M9.21 refusing, not the hatch: " + again.getMessage(),
                    again.getMessage().contains("M9.21"));
        }
        assertEquals("exactly one mine left the ship", 7, ship.getRackMineCount());
    }

    /** And a second bay buys a second mine, since the rule counts bays. */
    @Test
    public void aSecondBayWouldBuyASecondMine() {
        Ship ship = layer(2, 4, MineRole.NONE);
        assertTrue(game.dropMine(ship, "NSM").isSuccess());
        assertEquals("one laid against a one-bay allowance", 1, ship.rackMinesLaidOn(
                game.getClock().getTurn()));

        // The tally is per turn, so next turn starts clean.
        int turnBefore = game.getClock().getTurn();
        while (game.getClock().getTurn() == turnBefore)
            game.getClock().nextImpulse();

        assertEquals("a new turn, a clean tally", 0,
                ship.rackMinesLaidOn(game.getClock().getTurn()));
        assertTrue(game.dropMine(ship, "NSM").isSuccess());
    }

    /** An empty rack is passed over rather than refusing a ship that still has loose stock. */
    @Test
    public void anEmptyRackDoesNotBlockTheLooseAllocation() {
        Ship ship = layer(1, 0, MineRole.MINELAYER); // a rack, but nothing in it
        ship.setNuclearSpaceMines(1);

        assertTrue("no rack held an NSM, so the loose one is laid",
                game.dropMine(ship, "NSM").isSuccess());
        assertEquals(0, ship.getNuclearSpaceMines());
    }

    /** (M9.13) A destroyed rack holds nothing and is skipped. */
    @Test
    public void aDestroyedRackIsNotDrawnFrom() {
        Ship ship = layer(2, 4, MineRole.MINELAYER);
        ship.getMineRacks().get(0).destroy();

        assertEquals("four mines went with the box", 4, ship.getRackMineCount());
        assertTrue(game.dropMine(ship, "NSM").isSuccess());
        assertEquals("from the surviving rack", 3, ship.getRackMineCount());

        assertFalse("and only one rack is left to fire, so no second mine this turn",
                game.dropMine(ship, "NSM").isSuccess());
    }

    // ---------------------------------------------------------------- the purse (M9.17)

    /**
     * (M9.17) Rack mines are charged to the FORCE total, never to the 20% option cap.
     *
     * <blockquote>M9.17: "mine purchases by minesweepers are not restricted to a percentage of
     * the BPV (M6.31). Essentially, the cost of mines in mine racks is part of the overall force
     * total, not a part of the cost of the minesweeper."</blockquote>
     *
     * <p>Asserted on the loadout's own two figures, because those are the two purses: Annex #6
     * prices an NSM at 8, so four of them is 32 against the force total and nothing at all
     * against the budget this ship's BPV allows.
     */
    @Test
    public void rackMinesArePricedToTheForceTotalAndNotToTheOptionBudget() {
        com.sfb.scenario.CoiLoadout loadout = new com.sfb.scenario.CoiLoadout();
        loadout.rackNsms = 4;

        assertEquals("Annex #6: an NSM is 8 points", 32, loadout.rackMineCost(), 1e-9);
        assertEquals("M9.17: and none of it is a Commander's Option",
                0, loadout.totalCost(180), 1e-9);
    }

    /** A small mine is 4 either way, and a dummy is charged as one — M9.11 gives none free. */
    @Test
    public void smallRackMinesCostFourAndDummiesAreNotFree() {
        com.sfb.scenario.CoiLoadout loadout = new com.sfb.scenario.CoiLoadout();
        loadout.rackTBombs = 2;
        loadout.rackDummyTBombs = 1;

        assertEquals("M9.11 withdraws M3.224's free dummies for rack mines",
                12, loadout.rackMineCost(), 1e-9);
        assertEquals(0, loadout.totalCost(180), 1e-9);
    }

    /** And the charge lands on the force-total member rather than anywhere near the cap. */
    @Test
    public void theChargeLandsOnTheRackMinesForceCost() {
        Ship ship = layer(2, 0, MineRole.NONE);
        double optionBasisBefore = com.sfb.scenario.CoiBudget.effectiveAdjustedCombatBpv(ship);

        ship.setForceCost(ForceCost.RACK_MINES, 64); // eight NSMs, a full pair of racks

        assertEquals("the quartermaster pays",
                64, ship.getForceCost(ForceCost.RACK_MINES), 1e-9);
        assertEquals("S3.211: and the option basis has not moved",
                optionBasisBefore,
                com.sfb.scenario.CoiBudget.effectiveAdjustedCombatBpv(ship), 1e-9);
        assertEquals("S2.14: nor is it part of what killing her is worth",
                0, ship.getGabpvForceCost(), 1e-9);
    }
}
