package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import com.sfb.scenario.ScenarioLoader;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * The Kzinti large Q-ship's Y175 upgrades (R1.7).
 *
 * <p>The SSD states them as a history rather than as an option: "ship had type-A drone racks (one
 * reload) until the Y175 refit, which converted these to type-C drone racks (2 reloads)", and
 * "ADDs had 6 rounds before Y175 refit". So the hull carries the pre-refit fit and anything
 * fielded from Y175 has the upgrade — nobody chooses it.
 *
 * <p>That is why this belongs in {@code y175Upgrades} and NOT in {@code refits}. The two are
 * different mechanisms for different things: a {@code refits} entry is a named variant somebody
 * selects by code, while {@code y175Upgrades} is applied automatically by
 * {@code ScenarioLoader.applyYearUpgrades}, reached through {@link ScenarioLoader#outfitForYear}, to every ship in a scenario of Y175 or later. Writing
 * this as a refit would have left a Y180 Q-ship with its type-A racks unless a player happened to
 * ask for a refit code, and would have charged the +4 a second time on top of the automatic one.
 *
 * <p>The ADDs are deliberately NOT listed. ADD_6 to ADD_12 is universal at Y175, applied before
 * any per-ship block, so naming them would restate the default rather than change it — and the
 * comment in {@code applyYearUpgrades} records three hulls broken by exactly that confusion,
 * including a Klingon G2 that "declared a refit COST and nothing else, so it paid for an upgrade
 * it never received".
 */
public class KzintiQShipRefitTest {

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static ShipSpec spec() {
        ShipSpec s = ShipLibrary.get("Kzinti", "L-Q");
        assertNotNull("the library should hold the Kzinti large Q-ship", s);
        return s;
    }

    private static Ship qshipAt(int year) {
        ShipSpec s = spec();
        Ship ship = ShipLibrary.createShip(s);
        ScenarioLoader.outfitForYear(ship, "Kzinti", year, s);
        return ship;
    }

    private static DroneRack rack(Ship ship, String designator) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r && designator.equals(w.getDesignator()))
                return r;
        throw new AssertionError("no rack " + designator);
    }

    private static ADD add(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof ADD a)
                return a;
        throw new AssertionError("no ADD");
    }

    @Test
    public void beforeY175ItFightsWithWhatItWasBuiltWith() {
        Ship early = qshipAt(170);

        assertEquals(DroneRack.DroneRackType.TYPE_A, rack(early, "Rack 1").getRackType());
        assertEquals(DroneRack.DroneRackType.TYPE_A, rack(early, "Rack 2").getRackType());
        assertEquals(ADD.AddType.ADD_6, add(early).getAddType());
    }

    @Test
    public void fromY175BothRacksAreTypeC() {
        Ship late = qshipAt(175);

        assertEquals("both racks, not just the first (R1.7)",
                DroneRack.DroneRackType.TYPE_C, rack(late, "Rack 1").getRackType());
        assertEquals(DroneRack.DroneRackType.TYPE_C, rack(late, "Rack 2").getRackType());
    }

    /** Universal at Y175, which is why the ship's own block leaves {@code adds} empty. */
    @Test
    public void theAddGainsItsRoundsWithoutBeingListed() {
        assertTrue("the hull's block deliberately names no ADDs",
                spec().y175Upgrades.adds.isEmpty());

        assertEquals(ADD.AddType.ADD_12, add(qshipAt(175)).getAddType());
    }

    /** The sheet prices it: "Y175 REFIT = +4", and a refit's cost counts against both ratings. */
    @Test
    public void theUpgradeCostsFourPoints() {
        int asBuilt = qshipAt(170).getBattlePointValue();

        assertEquals(asBuilt + 4, qshipAt(175).getBattlePointValue());
    }

    /**
     * It changes what the weapons hold, never how many there are — and it is not a named variant,
     * so the hull declares no refit codes at all.
     */
    @Test
    public void thereIsNoRefitCodeForThis() {
        assertNull("this is a year upgrade, not a selectable refit", spec().refits);
    }

    // ---------------------------------------------------------------- the small Q-ship

    private static Ship smallQshipAt(int year) {
        ShipSpec s = ShipLibrary.get("Kzinti", "S-Q");
        assertNotNull("the library should hold the Kzinti small Q-ship", s);
        Ship ship = ShipLibrary.createShip(s);
        ScenarioLoader.outfitForYear(ship, "Kzinti", year, s);
        return ship;
    }

    /** Same history, same mechanism, a smaller bill: the S-Q sheet prices it at +2. */
    @Test
    public void theSmallQShipUpgradesItsRacksToo() {
        Ship early = smallQshipAt(170);
        assertEquals(DroneRack.DroneRackType.TYPE_A, rack(early, "Rack 1").getRackType());
        assertEquals(DroneRack.DroneRackType.TYPE_A, rack(early, "Rack 2").getRackType());

        Ship late = smallQshipAt(175);
        assertEquals(DroneRack.DroneRackType.TYPE_C, rack(late, "Rack 1").getRackType());
        assertEquals(DroneRack.DroneRackType.TYPE_C, rack(late, "Rack 2").getRackType());
        assertEquals(early.getBattlePointValue() + 2, late.getBattlePointValue());
    }

    /**
     * The S-Q carries no ADD at all, where the L-Q carries one. Worth pinning because the two
     * sheets are otherwise so alike that the smaller one is easy to fill in from the larger.
     */
    @Test
    public void theSmallQShipHasNoAdd() {
        for (Weapon w : smallQshipAt(175).getWeapons().fetchAllWeapons())
            assertFalse("the S-Q sheet has no ADD and no anti-drone table", w instanceof ADD);
    }
}
