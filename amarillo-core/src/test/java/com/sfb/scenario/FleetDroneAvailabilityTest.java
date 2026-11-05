package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.ShipLibrary;

/**
 * FD10.622/FD10.632 at the layer that REFUSES: a fleet may not be built over the drone caps.
 *
 * <h2>Why here and not only in {@code DroneAvailabilityLimitTest}</h2>
 * That suite proves the arithmetic on ships whose racks were filled by hand. This one buys the
 * drones the way a player does — as Commander's Options recorded in a saved fleet — and asks
 * {@link FleetValidator} for a verdict. Three things have to hold at once for that to work, and
 * only the first of them is the cap itself: {@code FleetLoader.resolve} has to apply the saved
 * choices before validating, the breach has to arrive as an ERROR rather than a note, and it has
 * to name the ship so the roster row can be marked.
 *
 * <p>The same gap swallowed G15.44's cartel quota for two revisions: a rule that is implemented,
 * tested and never consulted from the place a player meets it.
 *
 * <h2>The fixture is the rulebook's own ship</h2>
 * FD10.643's exception talks about "a type-G rack on a non-Kzinti or non-carrier ship", and the
 * Federation FFG is exactly that: one type-G rack of four spaces, no relaxed cap to its name. Four
 * spaces is also where the rounding is interesting — 10% of four rounds to none, so one Limited
 * drone is already too many, while 25% of four is one.
 */
public class FleetDroneAvailabilityTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    // ---------------------------------------------------------------- fixtures

    private static FleetSpec.ShipEntry ffg(String name) {
        FleetSpec.ShipEntry e = new FleetSpec.ShipEntry();
        e.faction = "Federation";
        e.type = "FFG";
        e.name = name;
        return e;
    }

    private static FleetSpec fleetAt(int year, FleetSpec.ShipEntry... entries) {
        FleetSpec spec = new FleetSpec();
        spec.name = "Test force";
        spec.year = year;
        spec.budget = 2000;        // large enough that S8.11 is never the complaint
        spec.factions = List.of("Federation");
        spec.ships = new ArrayList<>(List.of(entries));
        spec.flagship = entries.length > 0 ? entries[0].name : null;
        return spec;
    }

    /** Buy this rack-0 loadout for the ship, as the COI dialog would record it. */
    private static FleetSpec.ShipEntry bought(String name, String... drones) {
        FleetSpec.ShipEntry e = ffg(name);
        e.coi = new CoiChoices();
        e.coi.droneRackLoadouts.put("0", List.of(drones));
        return e;
    }

    private static List<FleetValidator.Violation> judge(FleetSpec spec) {
        return FleetValidator.validate(FleetLoader.toFleet(spec));
    }

    private static List<FleetValidator.Violation> errorsFor(FleetSpec spec, String rule) {
        List<FleetValidator.Violation> out = new ArrayList<>();
        for (FleetValidator.Violation v : judge(spec))
            if (v.isError() && rule.equals(v.rule))
                out.add(v);
        return out;
    }

    // ---------------------------------------------------------------- the refusal

    /**
     * One type-III in a four-space rack. The type-III frame is Limited in every year (FD10.631)
     * and 10% of four spaces rounds to none (FD10.641), so the fleet is illegal.
     */
    @Test
    public void aFleetThatBoughtOverTheLimitedCapIsRefused() {
        FleetSpec spec = fleetAt(180,
                bought("USS Over", "TypeIII", "TypeI", "TypeI", "TypeI"));

        List<FleetValidator.Violation> breaches = errorsFor(spec, "FD10.632");
        assertFalse("the type-III must be refused: " + judge(spec), breaches.isEmpty());
        assertEquals("and the roster row must know which ship it was",
                "USS Over", breaches.get(0).shipName);
        assertFalse("so the fleet as a whole is not legal",
                FleetValidator.isLegal(judge(spec)));
    }

    /** And the same hull carrying nothing special is clean. */
    @Test
    public void anOrdinaryLoadoutIsLegal() {
        FleetSpec spec = fleetAt(180,
                bought("USS Ordinary", "TypeI", "TypeI", "TypeI", "TypeI"));
        assertTrue("nothing to refuse: " + judge(spec),
                errorsFor(spec, "FD10.632").isEmpty() && errorsFor(spec, "FD10.622").isEmpty());
    }

    /**
     * A fleet that bought no options at all is judged on what the year fits it with (S2.11), and
     * that is never a breach — the standard load is type-Is.
     */
    @Test
    public void aFleetThatBoughtNothingIsLegal() {
        FleetSpec spec = fleetAt(180, ffg("USS Standard"));
        assertTrue("a default loadout cannot breach a cap: " + judge(spec),
                errorsFor(spec, "FD10.632").isEmpty() && errorsFor(spec, "FD10.622").isEmpty());
    }

    /**
     * The year decides, not the drone. Medium speed is Restricted in Y166 and General in Y167
     * (FD10.65), so four type-IMs are four Restricted spaces against an allowance of one in the
     * first year and unremarkable in the second.
     *
     * <p>This is the clause that makes the refusal worth having at build time specifically: the
     * fleet records the year it was assembled for (S8.13), so the shipyard knows which answer
     * applies while the player is standing in it.
     */
    @Test
    public void theSameFleetIsRefusedInOneYearAndAllowedInTheNext() {
        String[] load = { "TypeIM", "TypeIM", "TypeIM", "TypeIM" };

        assertFalse("Y166: medium speed is Restricted and 25% of four is one",
                errorsFor(fleetAt(166, bought("USS Early", load)), "FD10.622").isEmpty());
        assertTrue("Y167: medium speed is General, so no cap applies",
                errorsFor(fleetAt(167, bought("USS Later", load)), "FD10.622").isEmpty());
    }

    /**
     * FD10.643's exception, through the whole fleet path: "a type-G rack on a non-Kzinti or
     * non-carrier ship might have one restricted availability drone space and three spaces of
     * type-VI or ADDs or a combination of the two."
     *
     * <p>An ECM drone (Restricted) and six type-VIs is the rule's own loadout on the rule's own
     * ship. It must be buildable, and until the type-G exception was implemented it was not: the
     * type-VIs supplied no denominator, so the ship had one counted space, an allowance of none,
     * and the ECM drone was a breach of FD10.622.
     */
    @Test
    public void theRulebooksTypeGExampleCanActuallyBeBought() {
        FleetSpec spec = fleetAt(180, bought("USS Example",
                "TypeI+ECM", "TypeVI", "TypeVI", "TypeVI", "TypeVI", "TypeVI", "TypeVI"));

        assertTrue("FD10.643's own example must be a legal purchase: " + judge(spec),
                errorsFor(spec, "FD10.622").isEmpty() && errorsFor(spec, "FD10.632").isEmpty());
    }

    /**
     * Scenario load still only NOTES the same breach, which is the asymmetry on purpose.
     *
     * <p>{@code ScenarioLoader.applyCoi} says why: a scenario or fleet written under one year's
     * rules may be played under another, and losing a battle over one over-specialised drone load
     * helps nobody. The shipyard is the place to say no, because that is where the year, the
     * budget and the racks are all in front of the player at once (FD10.642).
     */
    @Test
    public void scenarioLoadStillPlaysOnWithANote() {
        com.sfb.objects.Ship ship = FleetLoader.resolve(
                fleetAt(180, bought("USS Noted", "TypeIII", "TypeI", "TypeI", "TypeI")))
                .ships().get(0);

        String notes = String.join(" | ", ship.getSetupNotes());
        assertTrue("applyCoi should have left the note it always did, but said: " + notes,
                notes.contains("FD10.632"));
    }
}
