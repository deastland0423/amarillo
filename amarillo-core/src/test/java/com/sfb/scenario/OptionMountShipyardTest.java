package com.sfb.scenario;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.OptionMount;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;

/**
 * G15.4: who fills an Orion's option mounts, and what it costs.
 *
 * <h2>The rule the design rests on</h2>
 * G15.415: "Optional weapons cannot be changed without a shipyard overhaul taking several weeks
 * or months." A loadout is therefore part of what the ship IS, bought with it — not a choice
 * made on the morning of a battle after seeing the opposition.
 *
 * <h2>So: whoever bought the ship is the shipyard</h2>
 * <ul>
 *   <li>the FLEET, when a side brought its own;</li>
 *   <li>the SCENARIO, when the scenario listed the ship;</li>
 *   <li>the PLAYER at COI, when neither said — a scenario leaving a mount empty has delegated
 *       the shipyard, which is a legitimate thing to write.</li>
 * </ul>
 * Nothing arbitrates between them. A filled mount is refused by {@code validate}, so the later
 * hand simply cannot overwrite the earlier one, and a scenario may fill some and leave the rest.
 *
 * <h2>And it is part of the price</h2>
 * G15.411: a mount "adjusts the BPV of the ship by the stated amount", on both values for a
 * divided BPV. A budget that read the bare hull let a force grow after being declared legal.
 */
public class OptionMountShipyardTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static ScenarioSpec.ShipSetup setup(String type, String name) {
        ScenarioSpec.ShipSetup s = new ScenarioSpec.ShipSetup();
        s.type = type;
        s.shipName = name;
        s.startHex = "0505";
        s.startHeading = "A";
        s.startSpeed = 0;
        return s;
    }

    private static Ship build(ScenarioSpec.ShipSetup setup, int year) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = year;
        spec.sides = new java.util.ArrayList<>();
        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = "Orion";
        side.name = "Pirates";
        side.ships = java.util.List.of(setup);
        spec.sides.add(side);

        for (java.util.List<Ship> ships : ScenarioLoader.loadShips(spec))
            for (Ship ship : ships)
                if (setup.shipName.equals(ship.getName()))
                    return ship;
        throw new AssertionError("fixture: " + setup.shipName + " was not built");
    }

    private static OptionMount mount(Ship ship, String designator) {
        for (OptionMount m : ship.getOptionMounts())
            if (m.getDesignator().equalsIgnoreCase(designator))
                return m;
        throw new AssertionError("fixture: no mount " + designator);
    }

    // ---------------------------------------------------------------- the scenario as shipyard

    /** A scenario that names a loadout gets it, with no COI step involved at all. */
    @Test
    public void aScenarioCanFixAnOrionsLoadout() {
        ScenarioSpec.ShipSetup setup = setup("CA", "Hammerfield");
        setup.optionMounts = new java.util.LinkedHashMap<>(java.util.Map.of("C", "Phaser-1"));

        Ship ca = build(setup, 180);

        assertFalse("the scenario's choice is aboard", mount(ca, "C").isEmpty());
        assertEquals("Phaser-1", mount(ca, "C").getOptionName());
    }

    /** What it does not name stays empty, for the COI step to offer. */
    @Test
    public void whatTheScenarioLeavesOpenStaysOpen() {
        ScenarioSpec.ShipSetup setup = setup("CA", "Hammerfield");
        setup.optionMounts = new java.util.LinkedHashMap<>(java.util.Map.of("C", "Phaser-1"));

        Ship ca = build(setup, 180);

        assertTrue("A was not spoken for", mount(ca, "A").isEmpty());
        assertTrue(mount(ca, "B").isEmpty());
    }

    /**
     * THE PRECEDENCE, and it needs no arbiter. A COI submission for a mount the scenario has already
     * filled is refused by the validator, so the shipyard's choice stands.
     */
    @Test
    public void aCoiSubmissionCannotOverwriteWhatTheShipyardFitted() {
        ScenarioSpec.ShipSetup setup = setup("CA", "Hammerfield");
        setup.optionMounts = new java.util.LinkedHashMap<>(java.util.Map.of("C", "Phaser-1"));
        Ship ca = build(setup, 180);

        CoiLoadout loadout = new CoiLoadout();
        loadout.optionMounts.put("C", "Phaser-3");
        loadout.optionMounts.put("D", "Phaser-3");
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = 180;
        ScenarioLoader.applyCoi(ca, loadout, spec);

        assertEquals("the scenario's weapon is still there", "Phaser-1",
                mount(ca, "C").getOptionName());
        assertEquals("and the mount it left open was filled", "Phaser-3",
                mount(ca, "D").getOptionName());
    }

    /** An illegal choice is noted and skipped, not thrown: one bad entry must not lose a battle. */
    @Test
    public void anIllegalChoiceIsNotedRatherThanFatal() {
        ScenarioSpec.ShipSetup setup = setup("CA", "Hammerfield");
        setup.optionMounts = new java.util.LinkedHashMap<>(
                java.util.Map.of("A", "Disruptor-30", "Z", "Phaser-1"));

        Ship ca = build(setup, 180);

        assertFalse("the ship was still built", ca.getSetupNotes().isEmpty());
        assertTrue("and the legal half of the order went in", mount(ca, "A").getOptionName() != null);
    }

    // ---------------------------------------------------------------- what it costs

    /**
     * G15.411: the mount is part of the price. A fleet validated on the bare hull could grow
     * after being declared legal, which is the hole this closes.
     */
    @Test
    public void afilledMountIsPartOfWhatTheShipCosts() {
        ScenarioSpec.ShipSetup bare = setup("CA", "Bare");
        ScenarioSpec.ShipSetup armed = setup("CA", "Armed");
        armed.optionMounts = new java.util.LinkedHashMap<>(java.util.Map.of("A", "Disruptor-30"));

        double bareCost = FleetValidator.costOf(build(bare, 180));
        double armedCost = FleetValidator.costOf(build(armed, 180));

        assertTrue("a Disruptor-30 costs a point (Annex #8B), so the armed ship costs more",
                armedCost > bareCost);
        assertEquals(1.0, armedCost - bareCost, 1e-9);
    }

    /** The deltas are fractional, so the cost must not be rounded per ship. */
    @Test
    public void afractionalDeltaSurvivesTheSum() {
        ScenarioSpec.ShipSetup cheap = setup("CA", "Thrifty");
        cheap.optionMounts = new java.util.LinkedHashMap<>(java.util.Map.of("A", "Phaser-2"));

        double cost = FleetValidator.costOf(build(cheap, 180));

        assertEquals("a Phaser-2 is -0.25 and the ship is cheaper for it",
                -0.25, cost - FleetValidator.costOf(build(setup("CA", "Plain"), 180)), 1e-9);
    }
}
