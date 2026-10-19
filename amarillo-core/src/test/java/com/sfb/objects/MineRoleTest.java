package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.properties.MineRole;
import com.sfb.samples.FederationShips;

/**
 * M9.21's laying rate turns on whether a ship is a dedicated mine-warfare vessel.
 *
 * <h2>The rule</h2>
 * "Ships can lay one mine from each mine rack during each turn. Only minesweepers and minelayers
 * use these rates to lay mines. Other ships converted (S7.0) to carry mine racks drop mines
 * through the shuttle hatch at the rate of one per turn per shuttle bay, regardless of the number
 * of racks in the bay."
 *
 * <h2>What is DATA-DRIVEN here and what is only RULE-DRIVEN</h2>
 * Honest labelling, because the two are not equally strong. The NONE case is data-driven: the
 * Romulan large Q-ship is the only hull in the game carrying mine racks and it is neither a
 * minesweeper (M2.78) nor a minelayer (M2.79) — separate Annex entries from its own M2.76 — so
 * it lays at the bay rate, and {@link #theRomulanLargeQShipLaysAtTheBayRate} pins the real hull.
 *
 * <p>The per-rack cases are RULE-driven only. No minelayer or minesweeper hull has been entered
 * yet — R1.12's minelaying freighters are unbuilt — so those tests construct the role rather than
 * reading it off a sheet. They are worth having because the rule is known and the branch is the
 * one the rack code will ask, but nothing in the shipped data exercises them, and this comment
 * exists so that is not mistaken for coverage.
 */
public class MineRoleTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    /** A ship built from values, so a role can be set without a hull declaring one. */
    private static Ship withRole(String role) {
        Map<String, Object> values = new HashMap<>(FederationShips.getFedCa());
        if (role != null)
            values.put("minerole", role);
        Ship ship = new Ship();
        ship.init(values);
        ship.setName("Testbed");
        return ship;
    }

    // ---------------------------------------------------------------- the default

    @Test
    public void aShipThatSaysNothingIsNeither() {
        Ship ship = withRole(null);

        assertEquals(MineRole.NONE, ship.getMineRole());
        assertFalse("and so lays at the bay rate", ship.laysOneMinePerRack());
    }

    /**
     * THE DATA-DRIVEN CASE: the only hull in the game with mine racks lays at the BAY rate.
     *
     * Owner's ruling, 2026-10-19. M2.76 gives the large Q-ship two racks of four NSMs, but M9.21
     * grants the per-rack rate to minesweepers and minelayers only, and M2.78/M2.79 are separate
     * entries from M2.76. M9.16 supports it from the other side: a ship converted under S7.0 to
     * carry mines in the shuttle bay "is not considered a minesweeper for any purpose".
     */
    @Test
    public void theRomulanLargeQShipLaysAtTheBayRate() {
        ShipSpec spec = ShipLibrary.get("Romulan", "L-Q");
        assertNotNull("fixture: the Romulan large Q-ship should be in the library", spec);
        Ship qship = ShipLibrary.createShip(spec);

        assertEquals("it is neither a minesweeper nor a minelayer",
                MineRole.NONE, qship.getMineRole());
        assertFalse("so its two racks are inventory and the bay is the bottleneck —"
                + " one mine per turn, not two", qship.laysOneMinePerRack());
    }

    /** And it is the only hull carrying racks, which is why it is the only one pinned. */
    @Test
    public void noHullInTheDataDeclaresAMineRoleYet() {
        for (ShipSpec spec : ShipLibrary.all())
            assertNull("if a hull now declares a mine role, give it a test of its own and"
                    + " revisit this one: " + spec.faction + "/" + spec.type, spec.mineRole);
    }

    // ---------------------------------------------------------------- the per-rack roles

    @Test
    public void aMinelayerLaysOneMinePerRack() {
        Ship layer = withRole("MINELAYER");

        assertEquals(MineRole.MINELAYER, layer.getMineRole());
        assertTrue("M9.21 grants a minelayer the per-rack rate", layer.laysOneMinePerRack());
    }

    @Test
    public void aMinesweeperLaysOneMinePerRackToo() {
        Ship sweeper = withRole("MINESWEEPER");

        assertEquals(MineRole.MINESWEEPER, sweeper.getMineRole());
        assertTrue("M9.21 names the two roles together", sweeper.laysOneMinePerRack());
    }

    // ---------------------------------------------------------------- parsing

    /** The file speaks the rulebook's word; case and spacing should not matter. */
    @Test
    public void theRoleIsParsedLeniently() {
        assertEquals(MineRole.MINELAYER, MineRole.from("minelayer"));
        assertEquals(MineRole.MINESWEEPER, MineRole.from("  MineSweeper "));
        assertEquals(MineRole.MINELAYER, MineRole.from(MineRole.MINELAYER));
    }

    /**
     * An unrecognised role reads as NONE rather than throwing, matching CarrierClass.
     *
     * The consequence is worth stating: a typo costs a ship the per-rack rate silently. That is
     * the lesser evil — the alternative is a parse failure that takes down the whole library —
     * and it is caught by {@link com.sfb.objects.ShipJsonKeyGuardTest}'s sibling guards plus the
     * fact that a hull declaring this at all is rare enough to notice.
     */
    @Test
    public void anUnknownRoleIsNeitherRatherThanAnError() {
        assertEquals(MineRole.NONE, MineRole.from("MINE_BOAT"));
        assertEquals(MineRole.NONE, MineRole.from(""));
        assertEquals(MineRole.NONE, MineRole.from(null));
        assertFalse(withRole("MINE_BOAT").laysOneMinePerRack());
    }
}
