package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Aas;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.objects.shuttles.StingerH;
import com.sfb.weapons.FighterFusion;
import com.sfb.weapons.FighterHellbore;
import com.sfb.weapons.Weapon;

/**
 * What arming a fighter costs a deck crew, and the invariant that keeps the books straight.
 * <p>
 * The costs are wanted twice over — by the end-of-turn rearm pass, which spends the crews a
 * ship has loose, and by the pre-game weapon status setup, which spends two turns' work per
 * crew (S4.12) capped at four actions on any one fighter. Two budgets, one price list. This
 * pins the price list, so the second caller cannot quietly invent its own.
 */
public class FighterArmingTest {

    // -------------------------------------------------------------------------
    // The price list (J4.82, J4.833, J4.834)
    // -------------------------------------------------------------------------

    @Test
    public void everyFighterWeCarryIsPricedByItsLoad() {
        // Four fusion charges at half an action each (J4.833).
        assertEquals("a Stinger-1 is two actions", 2,
                FighterArming.actionsToFullyArm(new Stinger1()));
        // One hellbore charge, one whole action (J4.834).
        assertEquals("a Stinger-H is one", 1, FighterArming.actionsToFullyArm(new StingerH()));
        // Two standard drone rails, one action a space (J4.82).
        assertEquals("an AAS is two", 2, FighterArming.actionsToFullyArm(new Aas()));
    }

    @Test
    public void theBudgetIsWhatBitesAFighterWithABigLoad() {
        // Nothing we carry today needs more than two actions, so this is the guard for the
        // advanced fighters that will: two crews reach four actions only by working both of
        // the two turns S4.12 allows (J4.8172 caps them at two crews per box).
        int stinger = FighterArming.actionsToFullyArm(new Stinger1());
        assertTrue("no fighter in the catalogue yet exceeds the two actions one crew-turn pair"
                + " delivers", stinger <= 2);

        Stinger1 half = new Stinger1();
        fusionsOf(half).get(0).drainCharges();
        assertEquals("two charges missing is one action of work", 2,
                FighterArming.halfActionsOutstanding(half));
        assertEquals("a full fighter costs nothing", 0,
                FighterArming.halfActionsOutstanding(new Stinger1()));
    }

    @Test
    public void aBudgetShortOfAWholeActionBuysNoHellboreCharge() throws Exception {
        StingerH sh = new StingerH();
        sh.setName("StingerH-1");
        ShuttleSpace box = new ShuttleSpace(sh);
        hellboreOf(sh).fireDirect(5);
        box.setCapacitorCharges(1);

        // J4.8174: an action that cannot be completed earns nothing, so half of one is wasted
        // rather than banked.
        FighterArming.Load half = FighterArming.load(box, sh, 1);
        assertNull("no work was attempted", half.note());
        assertTrue(hellboreOf(sh).isSpent());
        assertEquals("and the charge is still in the box", 1, box.getCapacitorCharges());

        FighterArming.Load whole = FighterArming.load(box, sh, 2);
        assertFalse("a whole action reloads it", hellboreOf(sh).isSpent());
        assertEquals(2, whole.halfActionsUsed());
    }

    // -------------------------------------------------------------------------
    // The invariant
    // -------------------------------------------------------------------------

    @Test
    public void aBoxAndItsFighterAccountForExactlyOneCapacitorAtSetup() throws Exception {
        Ship rn = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rn.json")));

        int boxes = 0;
        for (ShuttleBay bay : rn.getShuttles().getBays()) {
            for (ShuttleSpace box : bay.getSpaces()) {
                if (box.capacitorCapacity() == 0)
                    continue;
                boxes++;
                assertEquals("box " + boxes + ": what the fighter holds plus what is left in"
                        + " the box is one capacitor, no more and no less (J4.886)",
                        box.capacitorCapacity(),
                        box.getCapacitorCharges()
                                + FighterArming.chargesCarriedBy(box.getShuttle()));
            }
        }
        assertEquals("nine fighter boxes on a Ranger", 9, boxes);
    }

    @Test
    public void disarmingAFighterPutsItsChargesBackInTheBox() {
        ShuttleSpace box = new ShuttleSpace(named(new Stinger1(), "Stinger-1"));
        assertEquals("armed at build, so one reload left behind", 4, box.getCapacitorCharges());

        int returned = FighterArming.disarm(box, box.getShuttle());

        assertEquals(4, returned);
        assertEquals("the charges went home rather than nowhere (S4.10)",
                8, box.getCapacitorCharges());
        assertEquals(0, FighterArming.chargesCarriedBy(box.getShuttle()));
        assertEquals("and the invariant still holds", box.capacitorCapacity(),
                box.getCapacitorCharges() + FighterArming.chargesCarriedBy(box.getShuttle()));
    }

    @Test
    public void disarmingAHellboreFighterFillsItsOneChargeBox() {
        ShuttleSpace box = new ShuttleSpace(named(new StingerH(), "StingerH-1"));
        assertEquals("the armed fighter is carrying the box's only charge",
                0, box.getCapacitorCharges());

        assertEquals(1, FighterArming.disarm(box, box.getShuttle()));

        assertEquals(1, box.getCapacitorCharges());
        assertTrue("no charge aboard", hellboreOf(box.getShuttle()).isSpent());
    }

    @Test
    public void aDroneArmedFighterIsLeftAloneUntilItsReadyRackExists() {
        ShuttleSpace box = new ShuttleSpace(named(new Aas(), "AAS-1"));
        assertEquals("no capacitor behind a drone fighter", 0, box.capacitorCapacity());

        // J4.82 moves drones stores → ready rack → fighter, and we model neither. Both
        // directions refuse rather than inventing or discarding drones: dropping them is how
        // an untracked supply becomes an infinite one.
        assertNull(FighterArming.load(box, box.getShuttle(), 4).note());
        assertEquals(0, FighterArming.disarm(box, box.getShuttle()));
        assertEquals("its rails are still loaded", 2, loadedRails(box.getShuttle()));
    }

    // -------------------------------------------------------------------------

    private static Shuttle named(Shuttle s, String name) {
        s.setName(name);
        return s;
    }

    private static java.util.List<FighterFusion> fusionsOf(Shuttle f) {
        java.util.List<FighterFusion> out = new java.util.ArrayList<>();
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof FighterFusion ff)
                out.add(ff);
        return out;
    }

    private static FighterHellbore hellboreOf(Shuttle f) {
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof FighterHellbore hb)
                return hb;
        return null;
    }

    private static int loadedRails(Shuttle f) {
        int loaded = 0;
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.DroneRail rail && rail.getDrone() != null)
                loaded++;
        return loaded;
    }
}
