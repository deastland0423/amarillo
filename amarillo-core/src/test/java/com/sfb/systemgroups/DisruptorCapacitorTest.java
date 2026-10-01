package com.sfb.systemgroups;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.Das;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.weapons.FighterDisruptor;
import com.sfb.weapons.Weapon;

/**
 * J4.84: the capacitor and reloading for a disruptor-armed fighter.
 * <p>
 * Straight from the rule:
 * <ul>
 *   <li><b>J4.841</b> "The fighters cannot rearm the disruptors themselves" — the box is the only
 *       supply, as J4.881 says for every fighter weapon.</li>
 *   <li><b>J4.842</b> "that shuttle box is equipped with a capacitor (not shown on the SSD) for
 *       two disruptor charges. Other shuttle boxes on the ship do not have these capacitors. The
 *       ship loads the capacitors (two points per charge; <b>both points must be provided on the
 *       same turn</b>)."</li>
 *   <li><b>J4.843</b> "Reloading a disruptor charge on a fighter is a single deck crew
 *       action."</li>
 *   <li><b>J4.845</b> "All disruptor-armed fighters (except as noted) can hold two charges for
 *       each disruptor."</li>
 * </ul>
 * The same-turn clause is the one that distinguishes this from the hellbore: J4.834 SPREADS a
 * hellbore charge's four points over two turns, while this one refuses to be split at all.
 */
public class DisruptorCapacitorTest {

    private ShuttleSpace box;
    private Das das;

    @Before
    public void seatADasInItsBox() {
        das = new Das();
        das.setName("DAS-1");
        box = new ShuttleSpace(das);
    }

    private FighterDisruptor gun() {
        for (Weapon w : das.getWeapons().fetchAllWeapons())
            if (w instanceof FighterDisruptor fd)
                return fd;
        throw new AssertionError("the DAS has no disruptor");
    }

    // ---------------------------------------------------------------- J4.842: the capacitor

    @Test
    public void theBoxHasADisruptorCapacitorForTwoCharges() {
        assertEquals(ShuttleSpace.CapacitorKind.DISRUPTOR, box.getCapacitorKind());
        assertEquals("J4.842: a capacitor for two disruptor charges",
                2, box.capacitorCapacity());
        assertEquals(2, ShuttleSpace.DISRUPTOR_CAPACITOR);
    }

    /**
     * The kind must be stored, not inferred from the capacity. Two is not one, so the old
     * {@code capacity == HELLBORE_CAPACITOR} test would have priced this as a fusion box: a point
     * a charge instead of two, and no rate limit.
     */
    @Test
    public void aChargeCostsTwoPointsNotOne() {
        assertEquals("J4.842: two points per charge", 2, box.powerPerCapacitorCharge());
        assertEquals(2, ShuttleSpace.POWER_PER_DISRUPTOR_CHARGE);
    }

    /** A box that never held a disruptor fighter has no such capacitor (J4.842). */
    @Test
    public void anOrdinaryBoxHasNoDisruptorCapacitor() {
        ShuttleSpace admin = new ShuttleSpace(ShuttleBay.buildShuttle("admin", "Shuttle-1"));
        assertEquals(ShuttleSpace.CapacitorKind.NONE, admin.getCapacitorKind());
        assertEquals(0, admin.capacitorCapacity());

        ShuttleSpace aasBox = new ShuttleSpace(ShuttleBay.buildShuttle("aas", "AAS-1"));
        assertEquals("a drone fighter's box has a ready rack, not a capacitor",
                ShuttleSpace.CapacitorKind.NONE, aasBox.getCapacitorKind());
        assertEquals(0, aasBox.capacitorCapacity());
    }

    // ---------------------------------------------------------------- J4.842: the same turn

    /**
     * The clause that makes this different from a hellbore. Offering one point must buy nothing
     * and bank nothing: J4.842 wants both points on the same turn, and a point left banked would
     * be exactly the half-payment the rule forbids.
     */
    @Test
    public void oneLonePointBuysNothingAndBanksNothing() {
        box.setCapacitorCharges(0);
        int taken = box.addCapacitorEnergy(1);

        assertEquals("the odd point is refused outright", 0, taken);
        assertEquals("nothing banked to carry into next turn", 0, box.getCapacitorEnergyBanked());
        assertEquals(0, box.getCapacitorCharges());
    }

    @Test
    public void twoPointsInOneTurnBuyAChargeOutright() {
        box.setCapacitorCharges(0);
        assertEquals(2, box.addCapacitorEnergy(2));
        assertEquals(1, box.getCapacitorCharges());
        assertEquals(0, box.getCapacitorEnergyBanked());
    }

    /** Three points buy one charge and the odd point is not taken — not banked, not wasted. */
    @Test
    public void anOddPointIsNotTakenAlongsideAWholeCharge() {
        box.setCapacitorCharges(0);
        assertEquals("only the even part is taken", 2, box.addCapacitorEnergy(3));
        assertEquals(1, box.getCapacitorCharges());
        assertEquals(0, box.getCapacitorEnergyBanked());
    }

    @Test
    public void fourPointsFillTheCapacitor() {
        box.setCapacitorCharges(0);
        assertEquals(4, box.addCapacitorEnergy(4));
        assertEquals("two charges, the capacitor's whole capacity", 2, box.getCapacitorCharges());
        assertEquals("and it wants no more", 0, box.capacitorPowerWanted());
    }

    @Test
    public void aFullCapacitorTakesNothing() {
        box.setCapacitorCharges(2);
        assertEquals(0, box.capacitorPowerWanted());
        assertEquals(0, box.addCapacitorEnergy(4));
    }

    @Test
    public void itAsksForTwoPointsPerMissingCharge() {
        box.setCapacitorCharges(0);
        assertEquals("two charges at two points each", 4, box.capacitorPowerWanted());
        box.setCapacitorCharges(1);
        assertEquals(2, box.capacitorPowerWanted());
    }

    /** There is no per-turn rate limit, unlike the hellbore's two (J4.834). */
    @Test
    public void thereIsNoPerTurnCeiling() {
        box.setCapacitorCharges(0);
        assertEquals("a disruptor box can be filled in one turn", 4, box.addCapacitorEnergy(4));
        assertEquals(2, box.getCapacitorCharges());
    }

    // ---------------------------------------------------------------- J4.845: what it holds

    @Test
    public void theDisruptorHoldsTwoCharges() {
        assertEquals("J4.845: two charges for each disruptor", 2, FighterDisruptor.FULL_CHARGES);
        assertEquals("and starts empty, armed from its box (J4.8223)",
                0, gun().getChargesRemaining());
    }

    // ---------------------------------------------------------------- J4.843: the deck crew

    /**
     * The DAS must register as needing arming. Before the disruptor was wired into
     * {@link FighterArming} it reported READY with an empty gun, because nothing counted the
     * weapon at all.
     */
    @Test
    public void aDasWithAnEmptyGunNeedsArming() {
        assertTrue("there is deck crew work to do on it", FighterArming.needsArming(das));
        assertEquals("no charges aboard yet", 0, FighterArming.chargesCarriedBy(das));
        assertTrue("and the work is counted",
                FighterArming.halfActionsOutstanding(das) > 0);
    }

    /**
     * J4.843: one whole deck crew action per charge, so half an action loads no disruptor charge.
     * <p>
     * Asserted on the GUN and the capacitor rather than on the job's own count, because a DAS
     * carries light drone rails too: half an action is enough for one of those, so the pass does
     * do something — just not this.
     */
    @Test
    public void halfAnActionWillNotLoadADisruptorCharge() {
        box.setCapacitorCharges(2);
        FighterArming.load(box, das, 1);

        assertEquals("a disruptor charge is one whole action (J4.843)",
                0, gun().getChargesRemaining());
        assertEquals("and the capacitor is untouched", 2, box.getCapacitorCharges());
    }

    @Test
    public void oneActionLoadsOneCharge() {
        box.setCapacitorCharges(2);
        FighterArming.Load load = FighterArming.load(box, das, FighterArming.HALF_ACTIONS_PER_ACTION);

        assertEquals(1, load.chargesLoaded());
        assertEquals(1, gun().getChargesRemaining());
        assertEquals("drawn from the box, not the ship (J4.841/J4.881)",
                1, box.getCapacitorCharges());
        assertTrue(load.note(), load.note().contains("J4.843"));
    }

    @Test
    public void twoActionsLoadBothCharges() {
        box.setCapacitorCharges(2);
        FighterArming.load(box, das, 2 * FighterArming.HALF_ACTIONS_PER_ACTION);

        assertEquals("the gun is full", 2, gun().getChargesRemaining());
        assertEquals("and the capacitor is empty", 0, box.getCapacitorCharges());
    }

    /** J4.841: the fighter cannot rearm itself — an empty box reloads nothing. */
    @Test
    public void anEmptyCapacitorReloadsNothing() {
        box.setCapacitorCharges(0);
        FighterArming.Load load = FighterArming.load(box, das, 4);

        assertEquals(0, load.chargesLoaded());
        assertEquals(0, gun().getChargesRemaining());
        assertNotNull("and it says so", load.note());
        assertTrue(load.note(), load.note().contains("J4.842"));
    }

    /**
     * A full gun draws nothing more. Again measured on the capacitor rather than the job count:
     * with the disruptor full the pass moves on to the DAS's drone rails, which is correct and
     * would otherwise look like the disruptor taking a third charge.
     */
    @Test
    public void aFullGunDrawsNoMoreFromTheCapacitor() {
        box.setCapacitorCharges(2);
        while (gun().loadCharge()) { /* fill it */ }
        assertEquals(FighterDisruptor.FULL_CHARGES, gun().getChargesRemaining());

        FighterArming.load(box, das, 4);

        assertEquals("the gun takes no more than its two (J4.845)",
                FighterDisruptor.FULL_CHARGES, gun().getChargesRemaining());
        assertEquals("and the capacitor keeps its charges", 2, box.getCapacitorCharges());
    }

    /**
     * The box starts the scenario full LESS whatever its fighter already holds (J4.886) — the
     * same invariant the fusion and drone boxes keep: fighter plus box is one load.
     */
    @Test
    public void theBoxStartsFullBecauseTheFighterStartsEmpty() {
        ShuttleSpace fresh = new ShuttleSpace(new Das());
        assertEquals("J4.886: full at the start of a scenario", 2, fresh.getCapacitorCharges());
    }

    /** J4.831's rule for capacitors: destroyed with the box. */
    @Test
    public void destroyingTheBoxDestroysTheCapacitor() {
        box.setCapacitorCharges(2);
        box.destroy();
        assertEquals(0, box.getCapacitorCharges());
        assertEquals(0, box.capacitorCapacity());
    }

    // ---------------------------------------------------------------- the DASC too

    @Test
    public void theDascGetsTheSameTreatment() {
        Shuttle dasc = ShuttleBay.buildShuttle("dasc", "DASC-1");
        ShuttleSpace dascBox = new ShuttleSpace(dasc);
        assertEquals(ShuttleSpace.CapacitorKind.DISRUPTOR, dascBox.getCapacitorKind());
        assertEquals(2, dascBox.capacitorCapacity());
        assertEquals(2, dascBox.powerPerCapacitorCharge());
    }
}
