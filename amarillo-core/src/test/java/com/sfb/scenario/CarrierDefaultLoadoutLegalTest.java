package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;

/**
 * Every hull in the data must be legal under FD10.622 and FD10.632 as it comes.
 *
 * <h2>Why this is a test over the library and not a fixture</h2>
 * A ship's standard drone load is not a player's choice — FD2.31 issues it with the hull, and
 * FD2.45 includes it in the BPV. So a hull whose STOCK fit breaches an availability cap is a
 * contradiction: the player is refused a fleet for a loadout they never picked and cannot
 * change. Nothing short of walking the library can catch that, because it is a property of the
 * data rather than of the rules engine.
 *
 * <p>It has already happened once, and in the worst possible way: it was invisible until
 * FD10.641's fighter half was counted, and then it made EVERY drone carrier in the game illegal
 * at once. {@code DroneRail} stocked a SPECIAL rail with a type-III, which is Limited in every
 * year (FD10.631), so a Klingon C8VK arrived carrying 32 spaces of Limited against the 13 its
 * 20% allows. FD10.646 is explicit that special rails "do not form an exception allowing
 * additional limited availability drones to be purchased" and that "many of the initial drones
 * in the ready racks for these rails may be of general or restricted types".
 *
 * <p>Two years, chosen for what changes between them: Y170 sits after medium speed went General
 * (FD10.65) and before fast speed exists, Y180 after fast went General. A year in which the
 * year's own speed is Restricted would put every ship over its cap by issue, which is a
 * question about {@code applyDroneSpeed} rather than about the data.
 */
public class CarrierDefaultLoadoutLegalTest {

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    @Test
    public void everyHullsStandardDroneLoadIsLegalInItsYears() {
        List<String> breaches = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            for (int year : new int[] { 170, 180 }) {
                Ship ship;
                try {
                    ship = ShipLibrary.createShip(spec);
                    ScenarioLoader.outfitForYear(ship, spec.faction, year, spec);
                } catch (RuntimeException e) {
                    continue;       // a hull that cannot be built in that year is not this test's
                }
                for (String breach : DroneAvailabilityLimit.violations(ship, year))
                    breaches.add("Y" + year + " " + spec.faction + " " + spec.type
                            + ": " + breach);
            }
        assertTrue("a hull's STANDARD drone load must not breach a cap the player cannot get"
                + " under (FD2.31, FD10.646):\n  " + String.join("\n  ", breaches),
                breaches.isEmpty());
    }

    /**
     * And the gauge's breakdown has to be a part of the whole, or the hull's share of it is
     * negative.
     *
     * <p>{@code fighterSpaces} summed BOTH pools at first, so a Klingon C8VK reported 88 spaces
     * of fighters against a basis of 64 and the tooltip offered the player minus twenty-four
     * spaces of hull racks.
     */
    @Test
    public void theFighterShareIsNeverMoreThanTheWhole() {
        for (ShipSpec spec : ShipLibrary.all()) {
            Ship ship;
            try {
                ship = ShipLibrary.createShip(spec);
                ScenarioLoader.outfitForYear(ship, spec.faction, 180, spec);
            } catch (RuntimeException e) {
                continue;
            }
            double counted = DroneAvailabilityLimit.countedSpaces(ship);
            double fromFighters = DroneAvailabilityLimit.fighterSpaces(ship);
            assertTrue(spec.faction + " " + spec.type + ": " + fromFighters
                    + " of the squadron against a basis of " + counted,
                    fromFighters <= counted + 1e-9);
        }
    }

    /**
     * The hull that found it: a Klingon C8VK's basis is entirely its squadron, because the
     * conversion strips all seven of the C8's drone racks.
     */
    @Test
    public void aKlingonCarriersBasisIsAllSquadronAndAllGeneral() {
        ShipSpec spec = ShipLibrary.get("Klingon", "C8VK");
        org.junit.Assume.assumeTrue("the library must know the C8VK", spec != null);
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, "Klingon", 180, spec);

        assertEquals("no hull racks at all", DroneAvailabilityLimit.fighterSpaces(ship),
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("and nothing issued with it is Limited (FD10.646)", 0.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, com.sfb.objects.Availability.LIMITED,
                        180), 0.0001);
        assertTrue(DroneAvailabilityLimit.violations(ship, 180).toString(),
                DroneAvailabilityLimit.violations(ship, 180).isEmpty());
    }
}
