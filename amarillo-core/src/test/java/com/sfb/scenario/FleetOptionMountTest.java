package com.sfb.scenario;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.OptionMount;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;

/**
 * A saved Orion fleet carries its option mounts, pays for them, and answers to its cartel.
 *
 * <h2>Why the fleet is the shipyard</h2>
 * G15.415: "Optional weapons cannot be changed without a shipyard overhaul taking several weeks
 * or months." So the loadout is bought with the ship and saved with the fleet, not chosen on the
 * morning of a battle — which also means the opponent can read it off the SSD like anyone
 * else's armament.
 *
 * <h2>And the cartel is on the force</h2>
 * G15.44: "The limits below apply to the entire Orion battle force, as otherwise no ship would
 * have enough option mounts to have a 10% availability weapon." A fleet IS that force, so the
 * quota is checked here — where, now that mounts are bought, there is finally something to
 * check. Before this it was reachable only at COI submission.
 */
public class FleetOptionMountTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static FleetSpec fleetOf(String cartel, FleetSpec.ShipEntry... entries) {
        FleetSpec spec = new FleetSpec();
        spec.name = "Test force";
        spec.year = 180;
        spec.budget = 2000;
        spec.cartel = cartel;
        spec.factions = java.util.List.of("Orion");
        spec.ships = new java.util.ArrayList<>(java.util.List.of(entries));
        spec.flagship = entries.length > 0 ? entries[0].name : null;
        return spec;
    }

    private static FleetSpec.ShipEntry orion(String type, String name, String... mountPairs) {
        FleetSpec.ShipEntry e = new FleetSpec.ShipEntry();
        e.faction = "Orion";
        e.type = type;
        e.name = name;
        for (int i = 0; i + 1 < mountPairs.length; i += 2)
            e.optionMounts.put(mountPairs[i], mountPairs[i + 1]);
        return e;
    }

    private static OptionMount mount(Ship ship, String designator) {
        for (OptionMount m : ship.getOptionMounts())
            if (m.getDesignator().equalsIgnoreCase(designator))
                return m;
        throw new AssertionError("fixture: no mount " + designator);
    }

    // ---------------------------------------------------------------- the loadout is the ship's

    /** A fleet's choices are aboard the moment it resolves, with no COI step in sight. */
    @Test
    public void aFleetsOptionMountsAreFittedWhenItResolves() {
        FleetSpec spec = fleetOf(null, orion("CA", "Hammerfield", "A", "Phaser-1", "C", "Phaser-3"));

        Ship ca = FleetLoader.resolve(spec).ships().get(0);

        assertEquals("Phaser-1", mount(ca, "A").getOptionName());
        assertEquals("Phaser-3", mount(ca, "C").getOptionName());
        assertTrue("and what it did not buy stays open", mount(ca, "B").isEmpty());
    }

    /**
     * G15.411: the mounts are part of the price, so the budget sees them. This is the hole that
     * let a force be declared legal and then grow.
     */
    @Test
    public void whatTheMountsCostCountsAgainstTheBudget() {
        double bare = FleetValidator.totalCost(
                FleetLoader.resolve(fleetOf(null, orion("CA", "Bare"))).ships());
        double armed = FleetValidator.totalCost(
                FleetLoader.resolve(fleetOf(null, orion("CA", "Armed", "A", "Disruptor-30"))).ships());

        assertEquals("a Disruptor-30 is +1 in Annex #8B", 1.0, armed - bare, 1e-9);
    }

    /** A fleet with no Orions and no cartel is untouched by any of this. */
    @Test
    public void aFleetThatBuysNoMountsIsUnchanged() {
        FleetSpec spec = fleetOf(null, orion("CA", "Plain"));

        assertTrue("no violation about mounts or cartels",
                FleetValidator.validate(FleetLoader.toFleet(spec)).stream()
                        .noneMatch(v -> "G15.44".equals(v.rule)));
    }


    // ---------------------------------------------------------------- unnamed refit combinations

    /**
     * S3.24: a refit combination nobody NAMED a hull for is still legal, and must still be
     * buyable. Sixteen of the seventeen Orion files declare their refits as switches and name no
     * variant, so OAKDISC could be ticked in the builder and never bought.
     */
    @Test
    public void aFleetCanTickARefitNobodyNamedAHullFor() {
        FleetSpec.ShipEntry entry = orion("CA", "Refitted");
        entry.refits.add("O");
        FleetSpec spec = fleetOf(null, entry);

        Ship refitted = FleetLoader.resolve(spec).ships().get(0);
        Ship plain = FleetLoader.resolve(fleetOf(null, orion("CA", "Plain"))).ships().get(0);

        assertEquals("OAKDISC doubles the sensor rating for seeking weapons (FD4.5)",
                plain.getSpecialFunctions().getControlLimit() * 2,
                refitted.getSpecialFunctions().getControlLimit());
    }

    /** And it is paid for: a refit raises the BPV, so the budget sees it (S3.24). */
    @Test
    public void aTickedRefitIsPaidFor() {
        FleetSpec.ShipEntry plain = orion("CA", "Plain");
        FleetSpec.ShipEntry refitted = orion("CA", "Refitted");
        refitted.refits.add("O");

        double bare = FleetValidator.costOf(FleetLoader.resolve(fleetOf(null, plain)).ships().get(0));
        double withRefit =
                FleetValidator.costOf(FleetLoader.resolve(fleetOf(null, refitted)).ships().get(0));

        assertEquals("the CA's OAKDISC refit is +15", 15.0, withRefit - bare, 1e-9);
    }

    /** A hull bought by its NAMED variant type carries no codes and is untouched by this. */
    @Test
    public void anamedVariantStillWorksUnchanged() {
        FleetSpec spec = fleetOf(null, orion("CA", "ByName"));

        assertTrue("no refits ticked", spec.ships.get(0).refits.isEmpty());
        assertNotNull(FleetLoader.resolve(spec).ships().get(0));
    }

    // ---------------------------------------------------------------- the cartel quota

    /**
     * THE FORCE-WIDE RULE. Pharoh's home is the Federation; Klingon weapons are an operating-zone
     * item capped at 20% of the force's mounts. A CA has four mounts, so filling three of them
     * with Klingon disruptors is well past the cap and the fleet is illegal before it ever
     * reaches a battle.
     */
    @Test
    public void aCartelQuotaIsEnforcedWhenTheFleetIsValidated() {
        FleetSpec spec = fleetOf("Pharoh",
                orion("CA", "Overreach", "A", "Disruptor-30", "B", "Disruptor-30",
                        "C", "Disruptor-30"));

        java.util.List<FleetValidator.Violation> violations =
                FleetValidator.validate(FleetLoader.toFleet(spec));

        assertTrue("three of four mounts from the operating zone breaks the 20% cap",
                violations.stream().anyMatch(v -> "G15.44".equals(v.rule)
                        && v.severity == FleetValidator.Severity.ERROR));
    }

    /** The same ship under a cartel whose HOME makes those weapons unlimited is fine. */
    @Test
    public void theSameLoadoutIsLegalForACartelThatBuildsThem() {
        FleetSpec spec = fleetOf("Hamilcar",
                orion("CA", "AtHome", "A", "Disruptor-30", "B", "Disruptor-30",
                        "C", "Disruptor-30"));

        assertTrue("Hamilcar's home is Klingon space, so its disruptors are unlimited (G15.441)",
                FleetValidator.validate(FleetLoader.toFleet(spec)).stream()
                        .noneMatch(v -> "G15.44".equals(v.rule)));
    }

    /** No cartel declared means no quota — a force that has not said is not second-guessed. */
    @Test
    public void withoutACartelNoQuotaIsEnforced() {
        FleetSpec spec = fleetOf(null,
                orion("CA", "Unaligned", "A", "Disruptor-30", "B", "Disruptor-30",
                        "C", "Disruptor-30"));

        assertTrue(FleetValidator.validate(FleetLoader.toFleet(spec)).stream()
                .noneMatch(v -> "G15.44".equals(v.rule)));
    }

    /** An unknown cartel is advisory, not fatal: a typo should not make a fleet unplayable. */
    @Test
    public void anUnknownCartelWarnsRatherThanErrors() {
        FleetSpec spec = fleetOf("Nonesuch", orion("CA", "Mystery", "A", "Disruptor-30"));

        assertTrue(FleetValidator.validate(FleetLoader.toFleet(spec)).stream()
                .anyMatch(v -> "G15.44".equals(v.rule)
                        && v.severity == FleetValidator.Severity.ADVISORY));
    }
}
