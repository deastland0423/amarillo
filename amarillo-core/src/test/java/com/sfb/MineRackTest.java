package com.sfb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.MineRole;
import com.sfb.systemgroups.MineRack;

/**
 * (M9.11, M9.21) Mine storage boxes: what a box holds, and how fast the ship may empty it.
 *
 * <p>The capacity rule and the rate rule are deliberately tested apart, because they live apart:
 * M9.11's "four large mines, two small substituted for one large" is a property of the BOX, while
 * M9.21's rate is a property of the SHIP and splits minesweepers and minelayers from everybody
 * else.
 */
public class MineRackTest {

    // ---------------------------------------------------------------- M9.11 capacity

    /** "Each such box holds four large mines." */
    @Test
    public void aBoxHoldsFourLargeMines() {
        MineRack rack = new MineRack();

        assertEquals("four went in", 4, rack.loadLarge(4));
        assertEquals(4, rack.getLargeMines());
        assertFalse("and a fifth does not fit", rack.hasRoomForLarge());
        assertEquals("the fifth is refused rather than squeezed in", 0, rack.loadLarge(1));
    }

    /** "Two small mines can be substituted for one large one" — so eight T-bombs fill a box. */
    @Test
    public void orEightSmallMines() {
        MineRack rack = new MineRack();

        assertEquals(8, rack.loadSmall(8));
        assertEquals(8, rack.getSmallMines());
        assertEquals("full", 4, rack.spaceUsed());
        assertEquals("a ninth is refused", 0, rack.loadSmall(1));
    }

    /** And any mix, because capacity is counted in slots rather than in mines. */
    @Test
    public void orAnyMixThatFitsTheSlots() {
        MineRack rack = new MineRack();
        rack.loadLarge(3);
        assertEquals("one slot left, so two smalls fit in it", 2, rack.loadSmall(2));

        assertEquals(4, rack.spaceUsed());
        assertEquals(0, rack.spaceFree());
        assertEquals("three large and two small is five mines in four slots",
                5, rack.getTotalMines());
    }

    /**
     * A lone small mine takes a WHOLE slot, and its partner cradle is still open.
     *
     * <p>This is the arithmetic that a mine count rather than a slot count gets wrong: after one
     * T-bomb the box has three free slots for large mines, not three and a half.
     */
    @Test
    public void aLoneSmallMineOccupiesAWholeSlotButLeavesItsPartnerCradleOpen() {
        MineRack rack = new MineRack();
        rack.loadSmall(1);

        assertEquals("rounded up to a whole slot", 1, rack.spaceUsed());
        assertEquals(3, rack.spaceFree());
        assertTrue("the partner cradle is open", rack.hasRoomForSmall());
        assertTrue("and three large mines still fit", rack.hasRoomForLarge());

        rack.loadSmall(1);
        assertEquals("the second small shares the slot, using no more space",
                1, rack.spaceUsed());
    }

    /** A rack full of large mines has no half-slot, so no small mine fits either. */
    @Test
    public void aFullRackHasRoomForNeitherKind() {
        MineRack rack = new MineRack();
        rack.loadLarge(4);

        assertFalse(rack.hasRoomForLarge());
        assertFalse("no half-slot anywhere, so not even a small one", rack.hasRoomForSmall());
    }

    /** (M9.11) Dummies are never free here, so they take real space and must be loaded. */
    @Test
    public void dummiesTakeSpaceLikeAnyOtherSmallMine() {
        MineRack rack = new MineRack();
        rack.loadSmallDummy(8);

        assertEquals(8, rack.getSmallDummyMines());
        assertEquals("full of dummies", 0, rack.spaceFree());
        assertEquals("M9.11 removes M3.224's free dummies, so these were bought",
                8, rack.getTotalMines());
    }

    // ---------------------------------------------------------------- laying from a box

    /** Taking a mine out reduces the right pile and leaves the others alone. */
    @Test
    public void takingOneSpendsOnlyThatKind() {
        MineRack rack = new MineRack();
        rack.loadLarge(2);
        rack.loadSmall(2);
        rack.loadSmallDummy(2);

        assertTrue(rack.takeOne(true, false, 1));
        assertEquals(1, rack.getLargeMines());
        assertEquals("smalls untouched", 2, rack.getSmallMines());
        assertEquals("dummies untouched", 2, rack.getSmallDummyMines());

        assertTrue("a dummy comes from the dummy pile", rack.takeOne(false, true, 1));
        assertEquals(1, rack.getSmallDummyMines());
        assertEquals("not from the real smalls", 2, rack.getSmallMines());
    }

    /** An empty pile refuses and changes nothing, rather than going negative. */
    @Test
    public void takingWhatIsNotThereFails() {
        MineRack rack = new MineRack();
        rack.loadSmall(1);

        assertFalse("no large mines aboard", rack.takeOne(true, false, 1));
        assertEquals(1, rack.getTotalMines());
    }

    /** (M9.21) The rack records the turn it fired, which is what the rate check reads. */
    @Test
    public void aRackRemembersTheTurnItLastFired() {
        MineRack rack = new MineRack();
        rack.loadLarge(4);

        assertEquals("nothing laid yet", -1, rack.getLastLaidTurn());
        rack.takeOne(true, false, 3);

        assertTrue(rack.laidThisTurn(3));
        assertFalse("and is free again next turn", rack.laidThisTurn(4));
    }

    // ---------------------------------------------------------------- M9.13 damage

    /**
     * (M9.13) "The mines on a destroyed rack are also destroyed (but will not detonate)."
     *
     * <p>The parenthesis is the part worth a test: four NSMs going up inside the hull would be
     * the end of the ship, and the rule says plainly that it is not what happens.
     */
    @Test
    public void adestroyedRackLosesItsMinesWithoutDetonating() {
        MineRack rack = new MineRack();
        rack.loadLarge(4);

        rack.destroy();

        assertTrue(rack.isDestroyed());
        assertEquals("the mines are gone", 0, rack.getTotalMines());
        assertEquals("and the box holds nothing more", 0, rack.spaceFree());
        assertFalse(rack.isUsable(1));
    }

    // ---------------------------------------------------------------- M9.19 out of service

    /** (M9.19) "The rack must be taken out of service for the entire turn." */
    @Test
    public void anOutOfServiceRackIsUnusableForThatTurnOnly() {
        MineRack rack = new MineRack();
        rack.loadLarge(1);
        rack.takeOutOfService(5);

        assertTrue(rack.isOutOfService(5));
        assertFalse(rack.isUsable(5));
        assertTrue("but fine the next turn", rack.isUsable(6));
    }

    // ---------------------------------------------------------------- the hull

    /**
     * (M9.11) The hull declares BOXES, and they arrive empty (M2.76).
     *
     * <p>Built by injecting the key rather than from a sample builder, because the large Q-ship
     * has none — {@code RomulanShips} carries the KR and the WE. What proves the REAL file
     * reaches the spec is {@code theDataFileStillDeclaresTwoRacks} below, which reads
     * {@code lq.json}, plus {@code ShipJsonKeyGuardTest}, which now validates the key against
     * {@code ShipSpec} instead of its old parked note.
     */
    @Test
    public void aHullDeclaringTwoRackBoxesGetsTwoEmptyRacks() {
        Ship lq = withMineRacks(2);

        assertEquals("auxiliary.mineRacks: 2 is RACKS, not mines", 2, lq.getMineRacks().size());
        assertTrue(lq.hasMineRacks());
        assertEquals("M2.76: the mines are not in its BPV, so it starts empty",
                0, lq.getRackMineCount());
        for (MineRack rack : lq.getMineRacks())
            assertEquals("each box holds four large mines", 4, rack.spaceFree());
    }

    /**
     * (M2.76) And the real data file still says two — the claim the fixture above stands in for.
     *
     * <p>Read from disk on purpose. The hull is the only one in the game with mine racks, so if
     * this key is ever dropped or renamed the whole system becomes unreachable with every unit
     * test still green.
     */
    @Test
    public void theDataFileStillDeclaresTwoRacks() throws Exception {
        java.io.File file = new java.io.File("../data/factions/romulan/lq.json");
        assertTrue("the Romulan large Q-ship data file: " + file.getAbsolutePath(), file.exists());

        com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(file);

        assertEquals("M2.76: two mine racks, each holding four NSMs",
                2, root.path("auxiliary").path("mineRacks").asInt(-1));
        assertEquals("M2.76: and the mines are NOT in its BPV, so no bundled NSM either",
                0, root.path("auxiliary").path("nuclearSpaceMines").asInt(0));
    }

    /** A hull with mine racks, assembled by hand since no sample builder has one. */
    private static Ship withMineRacks(int boxes) {
        java.util.Map<String, Object> spec =
                new java.util.HashMap<>(com.sfb.samples.RomulanShips.getRomKr());
        spec.put("mineracks", boxes);
        Ship ship = new Ship();
        ship.init(spec);
        ship.setName("Romulan minelayer");
        return ship;
    }

    /** Almost every hull has none, and must not grow one by accident. */
    @Test
    public void anOrdinaryHullHasNoRacks() {
        Ship ca = new Ship();
        ca.init(com.sfb.samples.FederationShips.getFedCa());

        assertFalse(ca.hasMineRacks());
        assertTrue(ca.getMineRacks().isEmpty());
    }

    /**
     * (M9.21) The role decides the rate, and no hull in the data claims one.
     *
     * <p>Recorded as a test rather than a comment because it is the reachability question: the
     * one-mine-per-rack path cannot be exercised in a real battle until a minesweeper or
     * minelayer is entered. If one ever is, this test is where the claim stops being true, and
     * it should then be rewritten rather than deleted.
     */
    @Test
    public void aHullWithNoDeclaredRoleLaysAtTheBayRateNotThePerRackRate() {
        Ship lq = withMineRacks(2);

        assertEquals("neither a minesweeper (M2.78) nor a minelayer (M2.79)",
                MineRole.NONE, lq.getMineRole());
        assertFalse("M9.21: so the shuttle hatch is the bottleneck, not the racks",
                lq.laysOneMinePerRack());
    }

    /** And the role, where set, does switch the rate on. */
    @Test
    public void aMinelayerLaysOneMinePerRack() {
        Ship layer = withMineRacks(2);
        layer.setMineRole(MineRole.MINELAYER);

        assertTrue(layer.laysOneMinePerRack());

        layer.setMineRole(MineRole.MINESWEEPER);
        assertTrue("M9.21 names the two together", layer.laysOneMinePerRack());
    }
}
