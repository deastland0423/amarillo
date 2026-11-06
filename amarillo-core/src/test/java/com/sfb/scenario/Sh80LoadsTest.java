package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;

/**
 * SH80 "Dictator's Diplomat" loads and sets up: a published scenario end to end.
 *
 * <p>Written to answer a question rather than to guard a rule — the owner asked how close we are
 * to playing the published scenarios, and reading the loader could not settle it. This one is a
 * good probe because it is the most demanding file in {@code data/scenarios}: THREE sides, two of
 * them allied, a floating map, per-side destruction directions AND edges, a named character in a
 * bridge box, and five paragraphs of prose rules.
 *
 * <p>What it asserts is deliberately only the SETUP. Everything the scenario says in prose —
 * the alliance, the Tholian territory below the xx30 row, Vak Kaleen, the conditional
 * disengagement — is displayed to players and enforced by them, and this test does not pretend
 * otherwise. See the scenario section of BACKLOG.md for which of those a rule could enforce.
 */
public class Sh80LoadsTest {

    private static ScenarioSpec spec;

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        spec = new ObjectMapper().readValue(new File("../data/scenarios/sh80.json"), ScenarioSpec.class);
    }

    @Test
    public void theFileParsesIntoASpec() {
        assertEquals("SH80", spec.id);
        assertEquals(171, spec.year);
        assertEquals("three sides, not two", 3, spec.numPlayers);
        assertEquals(3, spec.sides.size());
    }

    /** The ships are real hulls from the library, at their printed stations. */
    @Test
    public void everySideBuildsItsShips() {
        List<List<Ship>> bySide = ScenarioLoader.loadShips(spec);

        assertEquals("one list per side", 3, bySide.size());
        for (List<Ship> side : bySide)
            assertEquals("each side fields exactly one ship in SH80", 1, side.size());

        Ship lexington = bySide.get(0).get(0);
        Ship dictator  = bySide.get(1).get(0);
        Ship centaurii = bySide.get(2).get(0);

        assertEquals("Lexington", lexington.getName());
        assertEquals("Dictator", dictator.getName());
        assertEquals("Centaurii", centaurii.getName());

        for (Ship s : new Ship[] {lexington, dictator, centaurii})
            assertNotNull(s.getName() + " should be placed on the map", s.getLocation());
    }

    /**
     * The per-side disengagement restrictions are STRUCTURED, not prose, and
     * {@code DisengagementResolver} reads them. This is the part of SH80 that is genuinely
     * enforced today.
     */
    @Test
    public void thePerSideDisengagementRestrictionsSurviveTheLoad() {
        ScenarioSpec.SideSpec klingon = spec.sides.get(1);
        ScenarioSpec.SideSpec romulan = spec.sides.get(2);

        assertEquals("the Klingon is destroyed leaving A, D or F",
                java.util.Set.of("A", "D", "F"), klingon.destructionDirections);
        assertEquals("and the Romulan off three edges",
                java.util.Set.of("TOP", "BOTTOM", "LEFT"), romulan.destructionEdges);
    }

    /** The prose rules reach the player, which is how they get enforced at all today. */
    @Test
    public void theSpecialRulesAreCarriedForThePlayersToRead() {
        assertEquals("all five paragraphs", 5, spec.specialRules.size());
        assertTrue("including the alliance, which nothing in code enforces",
                spec.specialRules.stream().anyMatch(r -> r.startsWith("ALLIANCE:")));
        assertTrue("and Vak Kaleen, who is not modelled at all",
                spec.specialRules.stream().anyMatch(r -> r.contains("VAK KALEEN")));
    }

    /**
     * Two fields in this file have NO consumer in core, and saying so here is the point: a test
     * that passes while the behaviour is missing would be worse than no test.
     *
     * <p>{@code mapType} is FLOATING — SH80's map floats with the ships and the scenario tracks
     * how far the xx30 row has shifted. Nothing reads the field. {@code lengthCondition} is the
     * same. Both are recorded in BACKLOG.md; when either is built, assert the behaviour here
     * instead of the field.
     */
    @Test
    public void theFieldsWithNoConsumerAreStillCarried() {
        assertEquals("FLOATING", spec.mapType);
        assertEquals("STANDARD", spec.lengthCondition);
        assertEquals("MODIFIED", spec.victoryConditions.type);
        assertNotNull("S2.201's terms are free text, not a calculation",
                spec.victoryConditions.notes);
    }
}
