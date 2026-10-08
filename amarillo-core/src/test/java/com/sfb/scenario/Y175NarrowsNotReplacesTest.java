package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * A hull's {@code y175Upgrades} block NARROWS the faction default; it does not replace it.
 *
 * <h2>What it used to do, and what that cost</h2>
 * The loader applied the block and <b>returned</b>, so the faction defaults never ran. A block that
 * named only some of a ship's racks silently lost the rest, and three hulls were wrong because of
 * it:
 * <ul>
 *   <li>The Federation <b>NCD</b> and <b>NCD+</b> named their four type-A racks and not their two
 *       type-G ones, so the Federation default that gives a type-G its third reload set (FD3.72)
 *       never happened. Both fought every Y175 battle two reload sets short.</li>
 *   <li>The Klingon <b>G2</b> declared a refit COST and nothing else, so it paid four points for a
 *       rack upgrade it never received.</li>
 * </ul>
 * Both shapes read as obviously fine in the file, which is what made them expensive. The owner's
 * verdict on changing it: "I think that would make the upgrades safer."
 *
 * <h2>The ordering, which is what makes an override an override</h2>
 * The block's named racks are applied first and then excluded from the default. Without the
 * exclusion a Kzinti hull naming a rack TYPE_B would have the default turn it into a TYPE_C
 * afterwards, and the file would be telling the truth about a ship the loader disagreed with.
 */
public class Y175NarrowsNotReplacesTest {

    @BeforeClass
    public static void load() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static Ship inYear(String faction, String type, int year) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = year;
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.type = type;
        setup.shipName = type + " under test";
        setup.startHex = "0510";
        setup.startHeading = "A";
        setup.weaponStatus = 3;
        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = faction;
        side.ships = List.of(setup);
        spec.sides = List.of(side);
        List<List<Ship>> sides = ScenarioLoader.loadShips(spec);
        assertEquals("fixture: " + faction + "/" + type + " should load", 1, sides.get(0).size());
        return sides.get(0).get(0);
    }

    private static List<DroneRack> racks(Ship ship) {
        List<DroneRack> out = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                out.add(rack);
        return out;
    }

    /**
     * The G2 declares a refit COST and nothing else, and still gets the Klingon rack upgrade.
     * <p>
     * This is the case that was wrong. Its block names no racks at all, so under the old
     * replace-everything rule the type-A racks stayed type-A while the four points were charged.
     */
    @Test
    public void aBlockWithOnlyACostStillGetsTheFactionDefault() {
        ShipSpec spec = ShipLibrary.get("Klingon", "G2");
        assertNotNull("fixture: the G2 should be in the library", spec);
        assertNotNull("fixture: and declare a y175 block", spec.y175Upgrades);
        assertTrue("fixture: whose racks list is empty, which is the whole point",
                spec.y175Upgrades.racks.isEmpty());

        for (DroneRack rack : racks(inYear("Klingon", "G2", 174)))
            assertEquals("before Y175 the racks are untouched",
                    DroneRack.DroneRackType.TYPE_A, rack.getRackType());

        List<DroneRack> after = racks(inYear("Klingon", "G2", 175));
        assertFalse("fixture: the G2 carries drone racks", after.isEmpty());
        for (DroneRack rack : after)
            assertEquals("the Klingon default upgrades type-A to type-B even though the block"
                            + " does not mention racks",
                    DroneRack.DroneRackType.TYPE_B, rack.getRackType());
    }

    /** And the cost is still charged, which is why the block exists at all. */
    @Test
    public void theRefitCostIsStillCharged() {
        int before = inYear("Klingon", "G2", 174).getBattlePointValue();
        int after = inYear("Klingon", "G2", 175).getBattlePointValue();

        assertEquals("the G2's SSD charges four points for the Y175 refit", before + 4, after);
    }

    /**
     * A NAMED rack is the block's to decide, and the default must leave it alone.
     * <p>
     * The Federation NCD names its four type-A racks as becoming type-B — which the Federation
     * default would NOT have done, since that default only touches type-G racks. So the block is
     * doing real work here and the two must compose rather than fight.
     */
    @Test
    public void theBlockWinsForTheRacksItNames() {
        List<DroneRack> after = racks(inYear("Federation", "NCD", 175));
        assertFalse(after.isEmpty());

        for (DroneRack rack : after) {
            String d = rack.getDesignator();
            if (d.endsWith("1") || d.endsWith("2") || d.endsWith("3") || d.endsWith("4"))
                assertEquals(d + " is named by the block as type-B",
                        DroneRack.DroneRackType.TYPE_B, rack.getRackType());
        }
    }

    /**
     * And the type-G racks the NCD's block also names get their third reload set (FD3.72) — the two
     * sets they lost for as long as declaring a block skipped the default.
     */
    @Test
    public void theNcdsTypeGRacksGetTheirThirdReloadSet() {
        List<DroneRack> before = racks(inYear("Federation", "NCD", 174));
        List<DroneRack> after = racks(inYear("Federation", "NCD", 175));

        int gBefore = 0, gAfter = 0;
        for (DroneRack r : before)
            if (r.getRackType() == DroneRack.DroneRackType.TYPE_G) gBefore += r.droneReloadSets();
        for (DroneRack r : after)
            if (r.getRackType() == DroneRack.DroneRackType.TYPE_G) gAfter += r.droneReloadSets();

        assertTrue("fixture: the NCD carries type-G racks", gBefore > 0);
        assertEquals("two type-G racks, one extra set each (FD3.72)", gBefore + 2, gAfter);
    }

    /**
     * The composition that the F5J asked about: a y175 block carried by a REFIT, narrowing the
     * faction default, on a hull the refit has already modified.
     *
     * <p>The F5 and F5J both start with a type-F rack. Their B refits set it to type-A and each
     * carries a y175 block — which until 2026-10-08 had to restate "Rack 1 becomes type-B", because
     * declaring the block skipped the Klingon default that does exactly that. Both blocks are now
     * just a {@code refitCost}, and this is what proves dropping the restatement changed nothing:
     * the refit makes the rack type-A, the default takes it to type-B, and the cost is still paid.
     *
     * <p>Worth its own test because it is a different path from the G2's: there the block sits on
     * the hull, here it arrives with a refit, so the refit must be applied before the year rules
     * see the ship at all.
     */
    @Test
    public void aRefitsOwnY175BlockAlsoNarrowsTheDefault() {
        for (String type : List.of("F5B", "F5JB")) {
            List<DroneRack> before = racks(inYear("Klingon", type, 174));
            assertEquals("fixture: " + type + " carries one rack", 1, before.size());
            assertEquals(type + "'s B refit should leave a type-A rack before Y175",
                    DroneRack.DroneRackType.TYPE_A, before.get(0).getRackType());

            List<DroneRack> after = racks(inYear("Klingon", type, 175));
            assertEquals(type + "'s rack should reach type-B from the faction default, with no"
                            + " entry in the block saying so",
                    DroneRack.DroneRackType.TYPE_B, after.get(0).getRackType());
        }

        // And the costs their SSDs charge are still charged: 3 for the F5, 1 for the F5J.
        assertEquals(3, inYear("Klingon", "F5B", 175).getBattlePointValue()
                - inYear("Klingon", "F5B", 174).getBattlePointValue());
        assertEquals(1, inYear("Klingon", "F5JB", 175).getBattlePointValue()
                - inYear("Klingon", "F5JB", 174).getBattlePointValue());
    }
}
