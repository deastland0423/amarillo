package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * D6.5: the UIM did not exist before Y165, so a ship in an earlier scenario has none — and costs
 * five BPV less for not having it.
 *
 * <p>"The UIM became available (to the Klingons) about Y165", restated as "available Y165 and
 * later"; YD6.5 adds that it is absent from the Early Years (Y80–Y120) entirely.
 *
 * <h2>Why the data is right and the loader was wrong</h2>
 * The Klingon D7N (Y137) and D7C (Y143) both declare a UIM, which looks like an anachronism and is
 * not: they are long-lived hulls whose SSDs print the module because they carry one in any scenario
 * from Y165 on. The owner read the D7N's sheet — it "was shown with 1x UIM in the SSD, but there's
 * a note stating that prior to Y165 it had no UIM and the BPV is 5 less".
 *
 * <p>So the module belongs to the YEAR, not the hull, and the file declaring the modern state with
 * the loader taking it away is the same shape as E7.5 fusion holding.
 */
public class UimAvailabilityTest {

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    /** One Klingon hull through the real loader at a scenario year. */
    private Ship klingon(String type, int year) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = year;

        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.type = type;
        setup.shipName = "IKS " + type;
        setup.startHex = "0510";
        setup.startHeading = "A";
        setup.weaponStatus = 3;

        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = "Klingon";
        side.ships = List.of(setup);
        spec.sides = List.of(side);

        List<List<Ship>> sides = ScenarioLoader.loadShips(spec);
        assertEquals("fixture: " + type + " should have loaded", 1, sides.get(0).size());
        return sides.get(0).get(0);
    }

    // ------------------------------------------------------------------ the year gate

    @Test
    public void theD7nHasNoUimBeforeY165AndCostsFiveLess() {
        Ship early = klingon("D7N", 150);
        assertFalse("D6.5: the UIM did not exist in Y150", early.hasUim());
        assertTrue(early.getUims().isEmpty());
        assertEquals("the owner's SSD note: five BPV less",
                119 - 5, early.getBattlePointValue());
    }

    @Test
    public void theSameD7nHasItFromY165() {
        Ship later = klingon("D7N", 165);
        assertTrue("Y165 is the year it arrives", later.hasUim());
        assertEquals(1, later.getUims().size());
        assertEquals(119, later.getBattlePointValue());
    }

    @Test
    public void y164IsStillTooEarly() {
        assertFalse(klingon("D7N", 164).hasUim());
        assertTrue(klingon("D7N", 165).hasUim());
    }

    /**
     * NB the "later" year is Y170, not Y180. The D7C carries a {@code y175Upgrades} refitCost of 3
     * for its ADD conversion, so a Y180 D7C is 139 rather than 136 — two year-gated adjustments on
     * one hull, and picking Y180 here measured both at once. Keep the two gates apart in tests.
     */
    @Test
    public void theD7cIsTheOtherAffectedHull() {
        Ship early = klingon("D7C", 150);
        assertFalse(early.hasUim());
        assertEquals(136 - 5, early.getBattlePointValue());

        Ship later = klingon("D7C", 170);
        assertTrue(later.hasUim());
        assertEquals(136, later.getBattlePointValue());
    }

    /** And both gates on one hull compose: pre-Y165 loses the UIM, post-Y175 adds the refit. */
    @Test
    public void theUimGateAndTheY175RefitComposeOnTheD7c() {
        assertEquals("Y150: no UIM, no refit", 136 - 5, klingon("D7C", 150).getBattlePointValue());
        assertEquals("Y170: UIM, no refit",    136,     klingon("D7C", 170).getBattlePointValue());
        assertEquals("Y180: UIM and refit",    136 + 3, klingon("D7C", 180).getBattlePointValue());
    }

    /**
     * The count and the objects must go together: {@code Ship.hasUim} reads the SpecialFunctions
     * count while {@code getActiveUim} walks the UIM list, so stripping one and not the other
     * leaves a ship that claims a UIM it cannot use, or uses one it does not claim.
     */
    @Test
    public void theCountAndTheObjectsAgreeAfterRemoval() {
        Ship early = klingon("D7N", 150);
        assertFalse(early.hasUim());
        assertTrue(early.getUims().isEmpty());
        assertNull("nothing to find on any impulse", early.getActiveUim(7));
    }

    /** A year of zero is "no date yet" and must leave the ship as its file built it. */
    @Test
    public void anUndatedScenarioLeavesTheShipAlone() {
        Ship undated = klingon("D7N", 0);
        assertTrue("a catalogue listing with no date shows the modern ship", undated.hasUim());
        assertEquals(119, undated.getBattlePointValue());
    }

    /** A hull that never had one is untouched, and is not refunded anything. */
    @Test
    public void aHullWithNoUimIsUnchangedAndUnrefunded() {
        Ship d7 = klingon("D7", 150);
        assertFalse(d7.hasUim());
        assertEquals(ShipLibrary.get("Klingon", "D7").bpv, d7.getBattlePointValue());
    }

    // ------------------------------------------------------------------ across the data

    /**
     * Every hull declaring a UIM, at a year before Y165: none may keep it, and each must be
     * refunded exactly five a module. An invariant over {@code ShipLibrary} rather than a list of
     * hull codes, so a newly entered early hull is covered the moment its file lands.
     */
    @Test
    public void noHullAnywhereKeepsAUimBeforeY165() {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (ShipSpec spec : ShipLibrary.all()) {
            Ship modern = ShipLibrary.createShip(spec);
            int uims = modern.getUims().size();
            if (uims == 0)
                continue;
            checked++;

            Ship early = ShipLibrary.createShip(spec);
            ScenarioLoader.applyUimAvailability(early, 150);
            String who = spec.faction + "/" + spec.type;
            if (early.hasUim() || !early.getUims().isEmpty())
                wrong.add(who + " keeps a UIM in Y150 (D6.5)");
            if (early.getBattlePointValue() != spec.bpv - uims * 5)
                wrong.add(who + " should refund " + (uims * 5) + " BPV, went from "
                        + spec.bpv + " to " + early.getBattlePointValue());
        }
        assertTrue("the library should hold some UIM hulls to check", checked > 0);
        assertEquals(String.join("\n  ", wrong), List.of(), wrong);
    }

    /**
     * And the hulls whose own service year predates Y165 are the only ones where this can be seen
     * in a legal scenario — you cannot field a ship before it exists. Recorded so the scope of the
     * gate is written down rather than rediscovered.
     */
    @Test
    public void onlyTwoHullsPredateTheUimTheyDeclare() {
        List<String> early = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            if (!ShipLibrary.createShip(spec).getUims().isEmpty() && spec.serviceYear < 165)
                early.add(spec.faction + "/" + spec.type + " (Y" + spec.serviceYear + ")");
        early.sort(null);
        assertEquals(List.of("Klingon/D7C (Y143)", "Klingon/D7N (Y137)"), early);
    }
}
