package com.sfb.systemgroups;

import com.sfb.objects.Ship;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Transporter boxes: the cycle they follow, and naming one for a hit-and-run raid.
 * <p>
 * A transporter is used one box at a time, and that box is unavailable until the next turn
 * or eight impulses later, whichever is longer — the same rule laboratories follow
 * (G4.451). Which box is which matters: a raid picks one deliberately (D7.835), and the
 * damage chart gives up a different one.
 */
public class TransporterBoxTest {

    private static final int T1 = 5;    // turn 1, impulse 5
    private static final int T1_LATE = 30;
    private static final int T2 = 45;   // turn 2, impulse 13 — past the delay

    private Transporters trans;

    @Before
    public void setUp() {
        trans = new Transporters(new Ship());
        trans.init(Map.of("trans", 4));
        trans.bankEnergy(4.0);          // energy enough that the boxes are the only limit
    }

    // ---------------------------------------------------------------- the cycle

    @Test
    public void eachUseConsumesOneBox() {
        assertEquals(4, trans.availableUses(T1));
        assertTrue(trans.useTransporter(T1));
        assertEquals("one box spent", 3, trans.availableUses(T1));
    }

    @Test
    public void aShipCannotUseMoreBoxesThanItHas() {
        for (int i = 0; i < 4; i++)
            assertTrue("use " + (i + 1), trans.useTransporter(T1));

        assertFalse("there is no fifth box, whatever the energy", trans.useTransporter(T1));
        assertTrue("and the energy is still there", trans.getBankedEnergy() > 0);
    }

    @Test
    public void aBoxUsedLateIsStillCoolingEarlyNextTurn() {
        trans.useTransporter(T1_LATE);

        assertEquals("three of four free, the fourth still cooling",
                3, trans.availableUses(33));      // turn 2, impulse 1
        assertEquals("free again eight impulses on", 4, trans.availableUses(T1_LATE + 8));
    }

    @Test
    public void usesMadeThisTurnAreCounted() {
        trans.useTransporter(T1);
        trans.useTransporter(T1);

        assertEquals(2, trans.usesMadeThisTurn(T1));
        assertEquals("and the count is per turn", 0, trans.usesMadeThisTurn(T2));
    }

    // ---------------------------------------------------------------- naming a box

    @Test
    public void boxesAreDescribedByWhatTheyAreDoing() {
        trans.useTransporter(T1);

        List<String> described = trans.describeBoxes(T1);

        assertEquals(4, described.size());
        assertTrue(described.get(0), described.get(0).contains("#1"));
        assertTrue("the spent one says so: " + described.get(0),
                described.get(0).contains("used this turn"));
        assertTrue("and an untouched one says that: " + described.get(1),
                described.get(1).contains("unused"));
    }

    @Test
    public void aBoxUsedLastTurnReadsAsCoolingDown() {
        trans.useTransporter(T1_LATE);

        String box1 = trans.describeBoxes(33).get(0);   // turn 2, impulse 1

        assertTrue("not 'used this turn' — it was last turn: " + box1,
                box1.contains("cooling down"));
    }

    /**
     * The point of naming one. A raider picks an UNUSED box, because a box already spent
     * this turn costs its owner almost nothing — which is exactly why the damage chart
     * gives those up first. The two must not pick the same box.
     */
    @Test
    public void aRaidTakesTheBoxItNamedNotTheOneTheOwnerWouldGiveUp() {
        trans.useTransporter(T1);                       // box 1 is now spent
        assertEquals(TransportersState.USED, stateOf(1, T1));

        assertTrue("the raider names an untouched box", trans.destroyBox(2));

        assertEquals("one box gone", 3, trans.getAvailableTrans());
        assertFalse("and box 2 is the one that went", trans.boxNumbers().contains(2));
        assertTrue("the spent box survives, which is the attacker's whole point",
                trans.boxNumbers().contains(1));
    }

    @Test
    public void damageGivesUpTheSpentBoxInstead() {
        trans.useTransporter(T1);                       // box 1 spent

        assertTrue(trans.damage());

        assertFalse("the defender loses the box that had already done its work",
                trans.boxNumbers().contains(1));
        assertEquals(3, trans.getAvailableTrans());
    }

    /**
     * Numbers are identity, not position. Destroy a box in the middle and the others keep
     * their numbers, or a raid aimed at "#4" would hit whatever slid into that slot.
     */
    @Test
    public void numbersSurviveTheirNeighboursBeingDestroyed() {
        assertTrue(trans.destroyBox(2));

        assertEquals(List.of(1, 3, 4), trans.boxNumbers());
        assertTrue("and #4 is still #4", trans.destroyBox(4));
        assertEquals(List.of(1, 3), trans.boxNumbers());
    }

    @Test
    public void destroyingABoxThatIsNotThereChangesNothing() {
        assertTrue(trans.destroyBox(2));
        assertFalse("already gone", trans.destroyBox(2));
        assertFalse("never existed", trans.destroyBox(99));
        assertEquals(3, trans.getAvailableTrans());
    }

    // A tiny local enum so the assertion above reads in terms of the rule, not an index.
    private enum TransportersState { USED, FREE }

    private TransportersState stateOf(int number, int impulse) {
        return trans.describeBoxes(impulse).stream()
            .filter(d -> d.startsWith("Transporter #" + number + " "))
            .findFirst()
            .map(d -> d.contains("unused") ? TransportersState.FREE : TransportersState.USED)
            .orElseThrow(() -> new AssertionError("no box #" + number));
    }
}
