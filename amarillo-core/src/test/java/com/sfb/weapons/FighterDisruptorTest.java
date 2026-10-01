package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;

/**
 * The disruptor a Kzinti DAS carries (J4.4) — a heavy weapon on a fighter, which means charges
 * from its box (J4.833) instead of energy to arm with, exactly as the Hydran Stingers' fusions do.
 * <p>
 * Owner's specification: max range 10 "just like all other fighter weapons", the standard
 * disruptor hit and damage charts, and standard mode only — never overloaded. The charts are NOT
 * truncated to match the shorter reach, so what the weapon does at range 10 is what a ship's
 * disruptor does at range 10; only how far it can shoot is cut.
 */
public class FighterDisruptorTest {

    private FighterDisruptor loaded() {
        FighterDisruptor fd = new FighterDisruptor();
        while (fd.loadCharge()) { /* fill it */ }
        return fd;
    }

    // ---------------------------------------------------------------- charges

    /**
     * Built EMPTY, like the fighter fusion. A fighter born armed is ammunition from nowhere: the
     * charges come out of its own box and weapon status decides who starts ready (S4.10-S4.13).
     */
    @Test
    public void itIsBuiltUnloaded() {
        FighterDisruptor fd = new FighterDisruptor();
        assertEquals(0, fd.getChargesRemaining());
        assertFalse("nothing to fire", fd.canFire());
        assertEquals(FighterDisruptor.FULL_CHARGES, fd.chargesMissing());
    }

    @Test
    public void chargesLoadOneAtATimeAndStopWhenFull() {
        FighterDisruptor fd = new FighterDisruptor();
        for (int i = 1; i <= FighterDisruptor.FULL_CHARGES; i++) {
            assertTrue("charge " + i + " should load", fd.loadCharge());
            assertEquals(i, fd.getChargesRemaining());
        }
        assertFalse("and no more than full", fd.loadCharge());
        assertEquals(0, fd.chargesMissing());
    }

    @Test
    public void firingSpendsACharge() throws Exception {
        FighterDisruptor fd = loaded();
        int before = fd.getChargesRemaining();
        fd.fire(5);
        assertEquals(before - 1, fd.getChargesRemaining());
    }

    /** Hit or miss, the shot was taken — a miss must not refund the charge. */
    @Test
    public void aMissStillCostsTheCharge() throws Exception {
        // Range 10 needs a 4 or less, so roughly a third of shots miss; over many weapons the
        // charge must come off every time regardless of the roll.
        for (int i = 0; i < 40; i++) {
            FighterDisruptor fd = loaded();
            int damage = fd.fire(10);
            assertEquals("charge spent whatever the roll gave (" + damage + ")",
                    FighterDisruptor.FULL_CHARGES - 1, fd.getChargesRemaining());
        }
    }

    /**
     * "Just like any disruptor, it can only fire once per turn (or with 8 impulses)." So having
     * a second charge is not a second shot now — it is a second shot a quarter turn later.
     */
    @Test
    public void twoChargesAreNotTwoShotsAtOnce() throws Exception {
        FighterDisruptor fd = loaded();
        assertEquals(8, FighterDisruptor.IMPULSE_GAP);
        assertEquals(8, fd.getMinImpulseGap());

        fd.fire(5);
        assertEquals("the charge is still there", 1, fd.getChargesRemaining());
        assertFalse("but the weapon is not ready again yet", fd.canFire());
        assertThrows("and firing it now is refused as a cooldown, not as empty",
                WeaponUnarmedException.class, () -> fd.fire(5));
    }

    @Test
    public void anEmptyWeaponRefusesToFire() {
        FighterDisruptor fd = new FighterDisruptor();
        assertThrows(WeaponUnarmedException.class, () -> fd.fire(3));
    }

    /** J1.3324: crippling the fighter empties what it was holding. */
    @Test
    public void cripplingDrainsIt() {
        FighterDisruptor fd = loaded();
        fd.drainCharges();
        assertEquals(0, fd.getChargesRemaining());
        assertFalse(fd.canFire());
    }

    // ---------------------------------------------------------------- reach

    @Test
    public void itReachesTenAndNoFurther() {
        FighterDisruptor fd = loaded();
        assertEquals(10, FighterDisruptor.MAX_RANGE);
        assertEquals(10, fd.getMaxRange());
        assertThrows("range 11 is beyond a fighter weapon",
                TargetOutOfRangeException.class, () -> fd.fire(11));
    }

    /** A disruptor has no range-0 column; a ship's starts at 1 and so does this. */
    @Test
    public void itCannotFireAtRangeZero() {
        FighterDisruptor fd = loaded();
        assertEquals(1, fd.getMinRange());
        assertThrows(TargetOutOfRangeException.class, () -> fd.fire(0));
    }

    @Test
    public void everyRangeFromOneToTenIsLegal() throws Exception {
        for (int range = 1; range <= 10; range++) {
            FighterDisruptor fd = loaded();
            int damage = fd.fire(range);
            assertTrue("range " + range + " gave " + damage, damage >= 0);
        }
    }

    // ---------------------------------------------------------------- the charts

    /**
     * The charts are the ship's, unshortened. The reach is cut to 10 but a hit at range 10 does
     * what a ship's disruptor does at range 10 — truncating the charts as well would have made
     * the fighter's shot weaker than the rule says.
     */
    @Test
    public void itUsesTheStandardDisruptorCharts() {
        FighterDisruptor fd = new FighterDisruptor();
        assertArrayEquals("hit chart is the standard one",
                Disruptor.standardHitChart(), fd.getHitChart());

        // Spot-checks against the published disruptor: 5 or less at range 1, 4 at ranges 3-15.
        assertEquals(5, fd.getHitChart()[1]);
        assertEquals(4, fd.getHitChart()[10]);
        assertEquals("5 damage at range 1", 5, Disruptor.standardDamageChart()[1]);
        assertEquals("3 at range 10", 3, Disruptor.standardDamageChart()[10]);
    }

    /** Every result is either a clean miss or the chart's damage for that range. */
    @Test
    public void damageIsTheChartValueOrNothing() throws Exception {
        for (int range = 1; range <= 10; range++) {
            int expected = Disruptor.standardDamageChart()[range];
            for (int i = 0; i < 20; i++) {
                FighterDisruptor fd = loaded();
                int damage = fd.fire(range);
                assertTrue("range " + range + " gave " + damage
                                + ", expected 0 or " + expected,
                        damage == 0 || damage == expected);
            }
        }
    }

    // ---------------------------------------------------------------- not a heavy weapon

    /**
     * It is NOT a HeavyWeapon, and that is the point of the class: a HeavyWeapon is armed with
     * energy over turns, and a fighter has no reactor to arm anything with. Charges replace all
     * of that, which is also why there is no overload — overloading is an energy decision.
     */
    @Test
    public void itIsNotAnEnergyArmedWeapon() {
        FighterDisruptor fd = new FighterDisruptor();
        assertFalse("must not be a HeavyWeapon", fd instanceof HeavyWeapon);
        assertTrue("but it is direct fire", fd instanceof DirectFire);
    }

    /** It is hit on 'torp' DAC results, as the ship disruptor and the fighter fusion are. */
    @Test
    public void itSitsInTheTorpedoDacColumn() {
        assertEquals("torp", new FighterDisruptor().getDacHitLocaiton());
    }

    // ---------------------------------------------------------------- on the DAS

    @Test
    public void theDasCarriesOneOfThemForwards() {
        Fighter das = CataloguedFighter.of("das");
        long disruptors = das.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof FighterDisruptor).count();
        assertEquals("one fighter disruptor", 1, disruptors);

        FighterDisruptor fd = das.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof FighterDisruptor)
                .map(w -> (FighterDisruptor) w)
                .findFirst().orElseThrow();
        assertEquals("built empty, armed from its box", 0, fd.getChargesRemaining());
        assertEquals("FA, like the rest of its armament", "FA", fd.getArcLabel());
    }

    /** The DAS is a fighter like any other: speed, hull and BPV as catalogued. */
    @Test
    public void theDasIsBuiltToItsCatalogueEntry() {
        Fighter das = CataloguedFighter.of("das");
        assertEquals(10, das.getMaxSpeed());
        assertEquals(10, das.getHull());
        assertEquals(10, das.getBpv());
        assertEquals("das", das.getCatalogType());
    }
}
