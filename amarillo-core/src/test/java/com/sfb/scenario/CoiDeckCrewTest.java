package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;

/**
 * Buying deck crews as a Commander's Option (S3.2, J4.816).
 * <p>
 * Half a point each, four at most, and only a fully capable carrier may hire them. What they
 * buy is turnaround: rearming spends one crew per two fusion charges (J4.833), so a Ranger's
 * nine crews reload four Stingers a turn and thirteen reload six. Two points for a third of a
 * squadron per turn is the sharpest thing a Hydran commander can do with a COI budget.
 */
public class CoiDeckCrewTest {

    private static final File FACTIONS = new File("../data/factions");

    private static Ship shipAt(String path) throws Exception {
        return ShipLibrary.createShip(ShipSpec.fromJson(new File(FACTIONS, path)));
    }

    /** A scenario with a generous budget, so nothing is refused for the wrong reason. */
    private static ScenarioSpec generousScenario() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        spec.commanderOptions.budgetPercent = 20;
        return spec;
    }

    @Test
    public void aCapableCarrierHiresThemAtHalfAPointEach() throws Exception {
        Ship rn = shipAt("hydran/rn.json");
        assertEquals(9, rn.getCrew().getDeckCrews());

        CoiLoadout loadout = new CoiLoadout();
        loadout.extraDeckCrews = 4;
        assertEquals("four crews for two points", 2.0, loadout.totalCost(), 0.001);

        ScenarioLoader.applyCoi(rn, loadout, generousScenario());

        assertEquals("nine plus four", 13, rn.getCrew().getDeckCrews());
        assertEquals("and they are on duty from turn one",
                13, rn.getCrew().getAvailableDeckCrews());
        assertTrue("no complaint: " + rn.getSetupNotes(), rn.getSetupNotes().isEmpty());
    }

    @Test
    public void fourIsTheLimit() throws Exception {
        Ship rn = shipAt("hydran/rn.json");
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraDeckCrews = 9;

        ScenarioLoader.applyCoi(rn, loadout, generousScenario());

        assertEquals("S3.2 stops at four however many are asked for",
                13, rn.getCrew().getDeckCrews());
    }

    @Test
    public void aShipThatIsNoCarrierMayNotHireThem() throws Exception {
        Ship ca = shipAt("federation/ca.json");
        int before = ca.getCrew().getDeckCrews();

        CoiLoadout loadout = new CoiLoadout();
        loadout.extraDeckCrews = 2;
        ScenarioLoader.applyCoi(ca, loadout, generousScenario());

        assertEquals("J4.816: not for a ship with no fighters", before,
                ca.getCrew().getDeckCrews());
        assertTrue("and it is told why: " + ca.getSetupNotes(),
                ca.getSetupNotes().stream().anyMatch(n -> n.contains("J4.816")));
    }

    @Test
    public void thePurchaseIsRefusedRatherThanCharged() throws Exception {
        // The refusal must not eat the budget: a player who asks for something they cannot
        // have should still be able to spend those points on boarding parties.
        Ship ca = shipAt("federation/ca.json");
        int bpsBefore = ca.getCrew().getFriendlyTroops().normal;

        CoiLoadout loadout = new CoiLoadout();
        loadout.extraDeckCrews = 4;
        loadout.extraBoardingParties = 10;
        ScenarioLoader.applyCoi(ca, loadout, generousScenario());

        assertEquals("the boarding parties still arrived",
                bpsBefore + 10, ca.getCrew().getFriendlyTroops().normal);
    }

    @Test
    public void aTightBudgetBuysWhatItCanAndSaysSo() throws Exception {
        Ship sq = shipAt("hydran/sq.json");   // a 2-fighter capable carrier, small BPV
        ScenarioSpec mean = new ScenarioSpec();
        mean.commanderOptions = new ScenarioSpec.CommanderOptions();
        mean.commanderOptions.budgetPercent = 0;

        CoiLoadout loadout = new CoiLoadout();
        loadout.extraDeckCrews = 4;
        ScenarioLoader.applyCoi(sq, loadout, mean);

        assertEquals("nothing bought on no budget", 2, sq.getCrew().getDeckCrews());
        assertTrue("and it says why: " + sq.getSetupNotes(),
                sq.getSetupNotes().stream().anyMatch(n -> n.contains("over budget")));
    }

    @Test
    public void theCrewsBoughtAreTheCrewsThatRearm() throws Exception {
        // The point of the purchase, end to end: four more crews reload two more Stingers.
        Ship lean = shipAt("hydran/rn.json");
        Ship bought = shipAt("hydran/rn.json");
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraDeckCrews = 4;
        ScenarioLoader.applyCoi(bought, loadout, generousScenario());

        assertEquals("nine crews, two per Stinger", 4, fullyRearmable(lean));
        assertEquals("thirteen crews, two per Stinger", 6, fullyRearmable(bought));
    }

    /** How many Stingers a turn's deck crews could put back to full (J4.833). */
    private static int fullyRearmable(Ship ship) {
        return ship.getCrew().getDeckCrews() / 2;
    }
}
