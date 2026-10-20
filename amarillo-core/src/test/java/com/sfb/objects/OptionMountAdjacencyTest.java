package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * G15.4: which option mounts sit next to which, declared as runs on the hull.
 *
 * <h2>Why it is declared and not derived</h2>
 * Adjacency is PRINTED on the SSDs — the Orion Free Traitor's sheet says outright "OPTION MOUNTS
 * ARE NOT 'ADJACENT CENTERLINE' MOUNTS" — so it is stated data. It cannot be inferred from how
 * many mounts of a position a hull carries, and the CA and SLV are the proof: both show two
 * CENTERLINE mounts, the CA's are adjacent and the SLV's are not.
 *
 * <h2>Runs, not a per-mount list</h2>
 * A run states the fact once, so there is no symmetry to keep. Adjacency means CONSECUTIVE
 * within a run, which is what lets a three-mount row behave correctly: in {@code ["A","B","C"]}
 * the pairs A-B and B-C are adjacent while A-C is not, because B lies between them.
 *
 * <h2>Independent of position</h2>
 * Owner, 2026-10-20: "You can have adjacent centerline or even adjacent wing mounts." Two wing
 * mounts in the SAME wing are a run; one in each wing are both WING and not adjacent. This model
 * says nothing about position, which {@code OptionCatalogEntry.positions} already filters.
 */
public class OptionMountAdjacencyTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static Ship hull(String faction, String type) {
        ShipSpec spec = ShipLibrary.get(faction, type);
        assertNotNull("fixture: " + faction + " " + type + " should be in the library", spec);
        return ShipLibrary.createShip(spec);
    }

    // ---------------------------------------------------------------- the real hulls

    /** The CA's two centerline mounts ARE adjacent, which is what unblocks multi-mount weapons. */
    @Test
    public void theCaDeclaresItsCenterlinePairAsARun() {
        Ship ca = hull("Orion", "CA");

        assertEquals(List.of(List.of("A", "B")), ca.getAdjacentMountRuns());
        assertTrue("A and B are neighbours", ca.mountsAreAdjacent("A", "B"));
        assertTrue("and adjacency is symmetric however it is asked",
                ca.mountsAreAdjacent("B", "A"));
    }

    /**
     * THE CASE THAT FORCED THIS MODEL: the SLV also shows two centerline mounts, and they are
     * NOT adjacent (owner, 2026-10-20). Counting centerline mounts would have got this wrong.
     */
    @Test
    public void theSlvHasTwoCenterlineMountsThatAreNotAdjacent() {
        Ship slv = hull("Orion", "SLV");

        long centerline = slv.getOptionMounts().stream()
                .filter(m -> m.getPosition() == OptionMount.Position.CENTERLINE)
                .count();
        assertEquals("fixture: it carries two centerline mounts, like the CA", 2, centerline);
        assertTrue("but declares no runs", slv.getAdjacentMountRuns().isEmpty());
        assertFalse("so they are not adjacent, and no two-mount weapon may go on it",
                slv.mountsAreAdjacent("A", "B"));
    }

    /**
     * THE FIRST REAL ROW OF THREE: the Battle Raider's A, B and C are one run of adjacent
     * centerline mounts, so it is the only hull where the ends-are-not-adjacent rule is a
     * statement about real data rather than a testbed.
     */
    @Test
    public void theBrCarriesAThreeMountCenterlineRun() {
        Ship br = hull("Orion", "BR");

        assertEquals(List.of(List.of("A", "B", "C")), br.getAdjacentMountRuns());
        assertTrue(br.mountsAreAdjacent("A", "B"));
        assertTrue(br.mountsAreAdjacent("B", "C"));
        assertFalse("A and C are two apart, so no two-mount weapon may span them",
                br.mountsAreAdjacent("A", "C"));
        assertFalse("and the wing mounts stand alone", br.mountsAreAdjacent("D", "E"));
    }

    /** Every other hull in the game declares nothing, and is therefore free of all this. */
    @Test
    public void ahullThatDeclaresNoRunsHasNoAdjacency() {
        Ship fed = hull("Federation", "CA");

        assertTrue(fed.getAdjacentMountRuns().isEmpty());
        assertFalse(fed.mountsAreAdjacent("A", "B"));
    }

    // ---------------------------------------------------------------- the run semantics

    /** A run is built from a values map, so these exercise the parse as well as the rule. */
    private static Ship withRuns(List<List<String>> runs) {
        java.util.Map<String, Object> values =
                new java.util.HashMap<>(com.sfb.samples.FederationShips.getFedCa());
        values.put("adjacentmounts", runs);
        Ship ship = new Ship();
        ship.init(values);
        ship.setName("Testbed");
        return ship;
    }

    /**
     * A row of three: the middle mount neighbours both ends, and the ends do NOT neighbour each
     * other. This is the whole reason a run is ordered rather than a set.
     */
    @Test
    public void inARowOfThreeTheEndsAreNotAdjacent() {
        Ship ship = withRuns(List.of(List.of("A", "B", "C")));

        assertTrue("A-B are consecutive", ship.mountsAreAdjacent("A", "B"));
        assertTrue("B-C are consecutive", ship.mountsAreAdjacent("B", "C"));
        assertFalse("A-C are NOT: B lies between them", ship.mountsAreAdjacent("A", "C"));
    }

    /** Two separate runs do not bleed into each other — the owner's two-wing case. */
    @Test
    public void mountsInDifferentRunsAreNotAdjacent() {
        Ship ship = withRuns(List.of(List.of("A", "B"), List.of("C", "D")));

        assertTrue(ship.mountsAreAdjacent("A", "B"));
        assertTrue(ship.mountsAreAdjacent("C", "D"));
        assertFalse("A is in the right wing and C in the left", ship.mountsAreAdjacent("A", "C"));
        assertFalse(ship.mountsAreAdjacent("B", "C"));
    }

    /** A mount is never its own neighbour, and an unknown name is simply not adjacent. */
    @Test
    public void degenerateAsksAreRefusedRatherThanThrowing() {
        Ship ship = withRuns(List.of(List.of("A", "B")));

        assertFalse(ship.mountsAreAdjacent("A", "A"));
        assertFalse(ship.mountsAreAdjacent("A", "Z"));
        assertFalse(ship.mountsAreAdjacent(null, "B"));
        assertFalse(ship.mountsAreAdjacent("A", null));
    }

    /** A run of one says nothing and is dropped, so it cannot masquerade as adjacency. */
    @Test
    public void aRunOfOneIsDiscarded() {
        Ship ship = withRuns(List.of(List.of("A"), List.of("B", "C")));

        assertEquals("only the real run survives", 1, ship.getAdjacentMountRuns().size());
        assertTrue(ship.mountsAreAdjacent("B", "C"));
    }

    // ---------------------------------------------------------------- the data holds together

    /**
     * Every designator a run names must be a mount the hull actually has.
     *
     * A typo would otherwise sit in the data declaring two mounts adjacent where one of them
     * does not exist, which no test of the rule itself would ever notice.
     */
    @Test
    public void everyRunNamesMountsThatExist() {
        List<String> wrong = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.adjacentMounts == null)
                continue;
            List<String> have = new ArrayList<>();
            if (spec.optionMounts != null)
                for (ShipSpec.OptionMountSpec m : spec.optionMounts)
                    have.add(m.designator);
            for (List<String> run : spec.adjacentMounts)
                for (String name : run)
                    if (!have.contains(name))
                        wrong.add(spec.faction + "/" + spec.type + " says mount " + name
                                + " is adjacent to something, but its mounts are " + have);
        }
        assertTrue(String.join("; ", wrong), wrong.isEmpty());
    }

    /** And no mount may appear in two runs, which would make its neighbours ambiguous. */
    @Test
    public void noMountAppearsInTwoRuns() {
        List<String> wrong = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.adjacentMounts == null)
                continue;
            List<String> seen = new ArrayList<>();
            for (List<String> run : spec.adjacentMounts)
                for (String name : run) {
                    if (seen.contains(name))
                        wrong.add(spec.faction + "/" + spec.type + ": mount " + name
                                + " appears in more than one run");
                    seen.add(name);
                }
        }
        assertTrue(String.join("; ", wrong), wrong.isEmpty());
    }
}
