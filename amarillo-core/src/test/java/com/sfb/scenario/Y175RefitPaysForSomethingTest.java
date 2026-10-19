package com.sfb.scenario;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaRack;
import com.sfb.weapons.Weapon;

/**
 * A hull that CHARGES for its Y175 refit must actually receive one.
 *
 * <h2>The bug this closes, which has now happened twice</h2>
 * {@code y175Upgrades} may declare a {@code refitCost} and nothing else, which reads as
 * perfectly fine in a ship file. Whether that is correct depends on machinery nowhere near the
 * file: {@code ScenarioLoader.applyYearUpgrades} applies the ADD_6 to ADD_12 upgrade and
 * FP10.312's plasma rack reloads UNIVERSALLY, but drone rack upgrades come from a per-faction
 * switch with cases for Federation, Klingon and Kzinti only. Everything else falls through
 * {@code default: break}.
 *
 * <p>So a cost-only block is right for a Klingon D6V, whose two ADD_6 mounts are upgraded for
 * free, and wrong for a NEUTRAL armed freighter, which inherits nothing and would pay for racks
 * that stayed type-A. The loader's own comment records the first instance — the Klingon G2
 * "declared a refit COST and nothing else, so it paid for an upgrade it never received" — and
 * the second was the two armed freighters, found on 2026-10-19 by reading the loader rather than
 * by anything failing.
 *
 * <h2>Why it runs the real loader instead of restating the rules</h2>
 * The obvious test would re-implement "what should fire for this faction", and then there would
 * be two records of one fact, drifting apart. Instead this builds the hull, snapshots the systems
 * a Y175 refit can touch, calls {@code applyYearUpgrades} at year 175, and asserts something
 * moved. Whatever the loader does is the oracle.
 *
 * <p><b>BPV is deliberately NOT part of the snapshot.</b> {@code refitCost} is added to the
 * battle and economic ratings whenever it is non-zero, so counting that as "something changed"
 * would make this test pass for every hull it is meant to catch.
 */
public class Y175RefitPaysForSomethingTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    /** The state a Y175 refit is able to alter — the systems only, never the point value. */
    private static String systemsFingerprint(Ship ship) {
        List<String> parts = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack rack)
                parts.add("rack:" + rack.getDesignator() + "=" + rack.getRackType()
                        + "/" + rack.droneReloadSets());
            else if (w instanceof ADD add)
                parts.add("add:" + add.getDesignator() + "=" + add.getAddType());
            else if (w instanceof PlasmaRack pr)
                parts.add("prack:" + pr.getDesignator() + "=" + pr.getReloadSets());
        }
        java.util.Collections.sort(parts);
        return String.join(",", parts);
    }

    @Test
    public void everyHullThatChargesForAY175RefitReceivesOne() {
        List<String> paidForNothing = new ArrayList<>();
        int charged = 0;

        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.y175Upgrades == null || spec.y175Upgrades.refitCost == 0)
                continue;
            charged++;

            Ship ship = ShipLibrary.createShip(spec);
            String before = systemsFingerprint(ship);
            ScenarioLoader.applyYearUpgrades(ship, spec.faction, 175, spec);
            String after = systemsFingerprint(ship);

            if (before.equals(after))
                paidForNothing.add(spec.faction + "/" + spec.type + " charges "
                        + spec.y175Upgrades.refitCost + " BPV and nothing changes — its racks are"
                        + " not named and its faction has no default, so name each rack in"
                        + " y175Upgrades.racks or drop the cost");
        }

        assertTrue("the data should hold some hulls that charge for a Y175 refit — if this is"
                + " zero the loop found nothing and the test proves nothing", charged > 5);
        String indent = System.lineSeparator() + "  ";
        assertTrue("a Y175 refit must change something about the ship:" + indent
                + String.join(indent, paidForNothing), paidForNothing.isEmpty());
    }

    /**
     * And the converse, so the guard above cannot be satisfied by the point value alone: a hull
     * with NO refit block is untouched by its own faction default only when it has nothing the
     * default applies to. Pinned on the Klingon D6V, whose cost-only block is CORRECT — the two
     * ADD_6 mounts are upgraded universally, which is what it is paying for.
     */
    @Test
    public void theKlingonD6VIsPaidUpBecauseItsAddsAreUpgraded() {
        ShipSpec spec = ShipLibrary.get("Klingon", "D6V");
        assertNotNull("fixture: the D6V should be in the library", spec);
        assertNotNull("fixture: and should declare a Y175 block", spec.y175Upgrades);
        assertTrue("fixture: a cost-only block, which is the shape under test",
                spec.y175Upgrades.racks.isEmpty());

        Ship ship = ShipLibrary.createShip(spec);
        long add6Before = ship.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof ADD)
                .filter(w -> ((ADD) w).getAddType() == ADD.AddType.ADD_6)
                .count();
        assertTrue("fixture: the D6V should carry ADD_6 mounts to upgrade", add6Before > 0);

        ScenarioLoader.applyYearUpgrades(ship, "Klingon", 175, spec);

        assertEquals("every ADD_6 should have become an ADD_12, with no block asking for it",
                0, ship.getWeapons().fetchAllWeapons().stream()
                        .filter(w -> w instanceof ADD)
                        .filter(w -> ((ADD) w).getAddType() == ADD.AddType.ADD_6)
                        .count());
    }
}
