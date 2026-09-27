package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.objects.shuttles.StingerH;
import com.sfb.weapons.FighterFusion;
import com.sfb.weapons.FighterHellbore;
import com.sfb.weapons.Weapon;

/**
 * Hydran fighter rearming (J4.83).
 * <p>
 * Before this, a Stinger that spent its charges was spent for the rest of the battle: a Hydran
 * carrier's strength only ever fell, which left the faction playable only as a one-alpha-strike
 * fleet. The rules run on deck crew actions of 32 consecutive impulses (J4.8171); we run the
 * ordinary case, which the arithmetic makes exactly one turn — see
 * {@link ShuttleBay#rearmFighters(int, int)}.
 */
public class FighterRearmTest {

    /** A bay of n boxes, each holding a fresh Stinger-1, seated before the game began. */
    private static ShuttleBay bayOfStingers(int n) {
        ShuttleBay bay = new ShuttleBay(null);
        for (int i = 0; i < n; i++) {
            Stinger1 s = new Stinger1();
            s.setName("Stinger-" + (i + 1));
            bay.addSpace(new ShuttleSpace(s));
        }
        return bay;
    }

    private static List<FighterFusion> fusionsOf(Shuttle f) {
        List<FighterFusion> out = new ArrayList<>();
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof FighterFusion ff)
                out.add(ff);
        return out;
    }

    private static void spendEverything(Shuttle f) {
        for (FighterFusion ff : fusionsOf(f))
            ff.drainCharges();
    }

    private static int chargesOn(Shuttle f) {
        int total = 0;
        for (FighterFusion ff : fusionsOf(f))
            total += ff.getChargesRemaining();
        return total;
    }

    // -------------------------------------------------------------------------
    // The fighter box capacitor (J4.831)
    // -------------------------------------------------------------------------

    @Test
    public void aFusionBoxHoldsTwoCompleteReloads() {
        ShuttleSpace box = bayOfStingers(1).getSpaces().get(0);

        assertEquals("eight charges is two reloads for a two-weapon Stinger (J4.831)",
                8, box.capacitorCapacity());
    }

    @Test
    public void aFighterThatStartsArmedTookItsChargesOutOfItsOwnBox() {
        // A freshly built Stinger-1 is armed: two charges in each of two fusions.
        ShuttleSpace armed = bayOfStingers(1).getSpaces().get(0);
        assertEquals(4, chargesOn(armed.getShuttle()));
        assertEquals("J4.886 starts the box full, and the four charges the fighter is already"
                + " holding came from it — one reload left behind, not two",
                4, armed.getCapacitorCharges());

        // A fighter that starts UNARMED, which is what Weapon Status will mean for most of
        // them (J4.8224), leaves its box full.
        Stinger1 unarmed = new Stinger1();
        unarmed.setName("Unarmed");
        spendEverything(unarmed);
        ShuttleSpace box = new ShuttleSpace(unarmed);
        assertEquals("nothing was drawn, so nothing is missing", 8, box.getCapacitorCharges());
    }

    @Test
    public void anArmedHellboreFighterLeavesItsBoxEmpty() {
        StingerH sh = new StingerH();
        sh.setName("StingerH-1");
        ShuttleSpace box = new ShuttleSpace(sh);

        assertEquals("a hellbore box holds one charge (J4.834)", 1, box.capacitorCapacity());
        assertEquals("and the armed fighter is carrying it", 0, box.getCapacitorCharges());
    }

    @Test
    public void aStingerThatSatAllTurnComesBackFullyLoaded() {
        ShuttleBay bay = bayOfStingers(1);
        ShuttleSpace box = bay.getSpaces().get(0);
        spendEverything(box.getShuttle());

        ShuttleBay.RearmResult result = bay.rearmFighters(2, 2);

        assertEquals("both fusions back to two charges (J4.833)", 4, chargesOn(box.getShuttle()));
        assertEquals("the reload the armed fighter left behind, now spent too (J4.881)",
                0, box.getCapacitorCharges());
        assertEquals("two deck crews, two half-actions each", 2, result.crewsUsed());
        assertEquals(1, result.log().size());
    }

    @Test
    public void aToppedUpBoxIsDryAfterTwoReloads() {
        ShuttleBay bay = bayOfStingers(1);
        ShuttleSpace box = bay.getSpaces().get(0);
        box.setCapacitorCharges(8);   // as if the ship had paid to fill it (J4.832)

        spendEverything(box.getShuttle());
        bay.rearmFighters(2, 2);
        spendEverything(box.getShuttle());
        bay.rearmFighters(3, 2);
        assertEquals("that was the second of the two reloads", 0, box.getCapacitorCharges());

        spendEverything(box.getShuttle());
        ShuttleBay.RearmResult third = bay.rearmFighters(4, 2);

        assertEquals("nothing left to load with — the ship must pay to refill it (J4.832)",
                0, chargesOn(box.getShuttle()));
        assertEquals("and no crew was spent on an impossible job", 0, third.crewsUsed());
        assertTrue("the empty capacitor is reported: " + third.log(),
                third.log().get(0).contains("capacitor empty"));
    }

    @Test
    public void aFighterThatArrivedThisTurnIsNotTouched() {
        ShuttleBay bay = new ShuttleBay(null);
        bay.addSpace(new ShuttleSpace());
        Stinger1 landed = new Stinger1();
        landed.setName("Returning");
        spendEverything(landed);

        bay.addShuttle(landed, 3);

        assertTrue("nothing is done to a fighter that only just landed (J4.8174)",
                bay.rearmFighters(3, 2).log().isEmpty());
        assertEquals(0, chargesOn(landed));

        bay.rearmFighters(4, 2);
        assertEquals("it sits out the turn it landed and is rearmed on the next",
                4, chargesOn(landed));
    }

    @Test
    public void twoDeckCrewsFinishOneStingerAndNoMore() {
        ShuttleBay bay = bayOfStingers(2);
        for (ShuttleSpace box : bay.getSpaces())
            spendEverything(box.getShuttle());

        ShuttleBay.RearmResult result = bay.rearmFighters(2, 2);

        assertEquals(4, chargesOn(bay.getSpaces().get(0).getShuttle()));
        assertEquals("a full Stinger reload is two actions, and a ship of two crews has"
                + " nothing left for the second fighter (J4.833)",
                0, chargesOn(bay.getSpaces().get(1).getShuttle()));
        assertEquals(2, result.crewsUsed());
    }

    @Test
    public void aHalfEmptyStingerLeavesACrewForTheNextFighter() {
        ShuttleBay bay = bayOfStingers(2);
        fusionsOf(bay.getSpaces().get(0).getShuttle()).get(0).drainCharges();  // two short
        spendEverything(bay.getSpaces().get(1).getShuttle());                  // four short

        ShuttleBay.RearmResult result = bay.rearmFighters(2, 2);

        assertEquals("one crew covers two charges", 4,
                chargesOn(bay.getSpaces().get(0).getShuttle()));
        assertEquals("the crew that was left got two charges into the next one", 2,
                chargesOn(bay.getSpaces().get(1).getShuttle()));
        assertEquals(2, result.crewsUsed());
    }

    // -------------------------------------------------------------------------
    // Hellbore boxes (J4.834)
    // -------------------------------------------------------------------------

    @Test
    public void aHellboreBoxHoldsOneChargeAndReloadingItTakesOneCrew() throws Exception {
        ShuttleBay bay = new ShuttleBay(null);
        StingerH sh = new StingerH();
        sh.setName("StingerH-1");
        bay.addSpace(new ShuttleSpace(sh));
        ShuttleSpace box = bay.getSpaces().get(0);
        assertEquals("a hellbore box carries one charge, not a fusion capacitor (J4.834)",
                1, box.capacitorCapacity());

        FighterHellbore hb = null;
        for (Weapon w : sh.getWeapons().fetchAllWeapons())
            if (w instanceof FighterHellbore h)
                hb = h;
        assertNotNull("a Stinger-H carries a hellbore", hb);
        hb.fireDirect(5);
        assertTrue("fired once, and spent until reloaded", hb.isSpent());

        // It started armed, so its box was empty before it even fired: there is nothing for
        // the deck crew to fetch until the ship has paid for a charge.
        ShuttleBay.RearmResult dry = bay.rearmFighters(2, 2);
        assertTrue("the empty box is reported rather than quietly ignored: " + dry.log(),
                dry.log().get(0).contains("capacitor empty"));
        assertTrue(hb.isSpent());
        assertEquals("and no crew was spent on it", 0, dry.crewsUsed());

        // Two points on each of two turns (J4.834).
        box.addCapacitorEnergy(2);
        box.addCapacitorEnergy(2);
        assertEquals(1, box.getCapacitorCharges());

        ShuttleBay.RearmResult loaded = bay.rearmFighters(4, 2);
        assertFalse("reloaded from the box's own capacitor (J4.881)", hb.isSpent());
        assertEquals("a hellbore charge is one whole action, so one crew (J4.834)",
                1, loaded.crewsUsed());
        assertEquals("which empties the box again", 0, box.getCapacitorCharges());
    }

    @Test
    public void aDestroyedBoxTakesItsCapacitorWithIt() {
        ShuttleBay bay = bayOfStingers(1);
        ShuttleSpace box = bay.getSpaces().get(0);

        box.destroy();

        assertEquals("J4.831: these capacitors are destroyed with the fighter box",
                0, box.getCapacitorCharges());
        assertEquals(0, box.capacitorCapacity());
    }

    // -------------------------------------------------------------------------
    // Refilling the capacitors from ship power (J4.832)
    // -------------------------------------------------------------------------

    @Test
    public void theShipBuysChargesBackIntoTheBoxAtAPointEach() {
        ShuttleBay bay = bayOfStingers(1);
        ShuttleSpace box = bay.getSpaces().get(0);
        box.setCapacitorCharges(0);

        assertEquals("eight points would refill a fusion box (J4.832)", 8,
                box.capacitorPowerWanted());
        assertEquals("took what was offered", 3, box.addCapacitorEnergy(3));
        assertEquals(3, box.getCapacitorCharges());
        assertEquals(5, box.capacitorPowerWanted());

        assertEquals("a full box takes nothing more", 5, box.addCapacitorEnergy(9));
        assertEquals(8, box.getCapacitorCharges());
        assertEquals(0, box.capacitorPowerWanted());
    }

    @Test
    public void aHellboreChargeCostsTwoPointsOnEachOfTwoTurns() {
        ShuttleBay bay = new ShuttleBay(null);
        StingerH sh = new StingerH();
        sh.setName("StingerH-1");
        bay.addSpace(new ShuttleSpace(sh));
        ShuttleSpace box = bay.getSpaces().get(0);   // armed fighter, so the box starts empty

        assertEquals("J4.834 takes two points a turn and no more", 2, box.capacitorPowerWanted());
        assertEquals("a bigger allocation cannot rush it", 2, box.addCapacitorEnergy(4));
        assertEquals("the first turn's points buy no charge", 0, box.getCapacitorCharges());
        assertEquals(2, box.getCapacitorEnergyBanked());

        assertEquals("two more are owed on the second turn", 2, box.capacitorPowerWanted());
        box.addCapacitorEnergy(2);
        assertEquals("four points over two turns, and the charge is there",
                1, box.getCapacitorCharges());
        assertEquals(0, box.getCapacitorEnergyBanked());
        assertEquals("a full hellbore box wants nothing", 0, box.capacitorPowerWanted());
    }

    @Test
    public void theAllocationFillsOneBoxBeforeStartingTheNext() {
        Shuttles group = new Shuttles(null);
        ShuttleBay bay = bayOfStingers(2);
        for (ShuttleSpace box : bay.getSpaces())
            box.setCapacitorCharges(0);
        group.getBays().add(bay);

        assertEquals("two empty fusion boxes want sixteen points", 16,
                group.capacitorPowerWanted());
        assertEquals(10, group.rechargeCapacitors(10));

        assertEquals("a player paying for charges wants one fighter able to fly a full"
                + " sortie, not two half-loaded ones",
                8, bay.getSpaces().get(0).getCapacitorCharges());
        assertEquals(2, bay.getSpaces().get(1).getCapacitorCharges());
    }

    @Test
    public void nothingIsSpentOnBoxesThatAreAlreadyFull() {
        Shuttles group = new Shuttles(null);
        ShuttleBay bay = bayOfStingers(2);
        for (ShuttleSpace box : bay.getSpaces())
            box.setCapacitorCharges(8);
        group.getBays().add(bay);

        assertEquals("nothing left to buy", 0, group.capacitorPowerWanted());
        assertEquals(0, group.rechargeCapacitors(8));
    }

    @Test
    public void anArmedSquadronStillHasOneReloadToBuyBack() {
        Shuttles group = new Shuttles(null);
        group.getBays().add(bayOfStingers(2));   // both fighters armed from their own boxes

        assertEquals("each box gave away four of its eight (J4.886), so eight points buy"
                + " the pair of them back up", 8, group.capacitorPowerWanted());
    }

    // -------------------------------------------------------------------------
    // The end-of-turn pass on a real ship
    // -------------------------------------------------------------------------

    @Test
    public void theEnergyAllocationLineReachesTheFighterBoxes() throws Exception {
        Ship rn = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rn.json")));
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.capacitorCapacity() > 0) {
                    box.setCapacitorCharges(0);
                    boxes.add(box);
                }
        assertEquals("nine fighter boxes, each emptied", 9, boxes.size());
        assertEquals("nine empty fusion boxes want 72 points (J4.832)",
                72, rn.getShuttles().capacitorPowerWanted());

        Energy allocation = new Energy();
        allocation.setFighterCapacitors(9);
        rn.allocateEnergy(allocation);
        rn.startTurn();

        assertEquals("the first box took a full eight", 8, boxes.get(0).getCapacitorCharges());
        assertEquals("and the ninth point started the next one",
                1, boxes.get(1).getCapacitorCharges());
        assertEquals(63, rn.getShuttles().capacitorPowerWanted());
    }

    @Test
    public void aHydranCarrierRearmsItsFightersAtTheTurnBoundary() throws Exception {
        Ship rn = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rn.json")));
        assertEquals("J4.81: a carrier's deck crews come from Annex #7G — one per fighter",
                9, rn.getCrew().getDeckCrews());

        Game game = new Game();
        game.getShips().add(rn);
        game.startTurn();               // attaches the clock the rearm pass reads
        game.getClock().nextImpulse();  // and put it on the first impulse of turn 0

        List<Shuttle> stingers = new ArrayList<>();
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() instanceof Stinger1 s) {
                    spendEverything(s);
                    stingers.add(s);
                }
        assertEquals("the Ranger carries nine Stingers", 9, stingers.size());

        rn.cleanUp();

        int rearmed = 0;
        for (Shuttle s : stingers)
            if (chargesOn(s) == 4)
                rearmed++;
        assertEquals("nine deck crews, two per fighter — four Stingers get a full reload",
                4, rearmed);
        assertFalse("and the ship can say what its deck crews did (J4.8175)",
                rn.getShuttles().getLastRearmLog().isEmpty());
    }
}
