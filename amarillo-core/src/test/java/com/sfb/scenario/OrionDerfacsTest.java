package com.sfb.scenario;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.OptionMountCatalog;
import com.sfb.objects.OptionMountLoadout;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.weapons.Disruptor;

/**
 * Annex #8B: "Orion disruptors with ranges more than 22 include DERFACS from Y168."
 *
 * <h2>A year rule, not a refit and not a declared field</h2>
 * It behaves like {@code applyFusionHolding} or the ESG capacitors: automatic at a year, free,
 * and conditional on what the ship is carrying. It could not be a declared
 * {@code auxiliary.derfacs} on the hull, because whether an Orion qualifies depends on what its
 * owner buys into an option mount at setup — declaring it would hand DERFACS to an Orion who
 * bought phasers.
 *
 * <h2>Range, not a list of models</h2>
 * The rule is encoded as "more than 22", so the Disruptor-40 qualifies by the same clause as
 * the Disruptor-30 and the Disruptor-22 does not. The BR's and CR's sheets phrase it as "a range
 * of 30 or more", which picks the same set out of the models that exist; the annex's wording is
 * the general one, so it is the one tested.
 */
public class OrionDerfacsTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static Ship outfitted(String faction, String type, int year) {
        ShipSpec spec = ShipLibrary.get(faction, type);
        assertNotNull("fixture: " + faction + " " + type + " should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ScenarioLoader.outfitForYear(ship, faction, year, spec);
        return ship;
    }

    private static Ship orionWithMountedDisruptor(String option, int year) {
        Ship br = outfitted("Orion", "BR", year);
        assertNull("fixture: no Orion hull declares a disruptor, so it starts without DERFACS",
                br.getDerfacs());
        OptionMountLoadout.equip(br, OptionMountCatalog.loadDefault(), "A", option, year);
        return br;
    }

    // ---------------------------------------------------------------- the grant

    /** The headline case: buy the long disruptor, get the DERFACS, pay nothing. */
    @Test
    public void aDisruptor30BoughtIntoAMountBringsDerfacs() {
        Ship br = orionWithMountedDisruptor("Disruptor-30", 168);

        assertNotNull("Annex #8B grants it from Y168", br.getDerfacs());
        assertTrue(br.getDerfacs().isFunctional());
    }

    /** And the 40 qualifies by the same clause, which is why the test is a range and not a list. */
    @Test
    public void aDisruptor40BringsItToo() {
        Ship br = orionWithMountedDisruptor("Disruptor-40", 168);

        assertNotNull(br.getDerfacs());
    }

    /** "MORE than 22" — so the 22 itself does not, and that is the boundary worth pinning. */
    @Test
    public void aDisruptor22DoesNot() {
        Ship br = orionWithMountedDisruptor("Disruptor-22", 168);

        assertNull("22 is not more than 22", br.getDerfacs());
    }

    /** Nor does a shorter one. */
    @Test
    public void aDisruptor15DoesNot() {
        Ship br = orionWithMountedDisruptor("Disruptor-15", 168);

        assertNull(br.getDerfacs());
    }

    // ---------------------------------------------------------------- the year

    /** A year early and the same purchase brings nothing. */
    @Test
    public void theSamePurchaseInY167BringsNothing() {
        Ship br = orionWithMountedDisruptor("Disruptor-30", 167);

        assertNull("the grant starts in Y168", br.getDerfacs());
    }

    // ---------------------------------------------------------------- whose rule it is

    /**
     * It is the ORION entry in the annex. A Klingon flying its own disruptors in Y168 does not
     * get a free DERFACS out of this rule — if a Klingon hull has one, its own sheet says so.
     */
    @Test
    public void aKlingonWithLongDisruptorsGetsNothingFromThisRule() {
        Ship d7 = outfitted("Klingon", "D7", 168);

        boolean hasLongDisruptor = false;
        for (com.sfb.weapons.Weapon w : d7.getWeapons().fetchAllWeapons())
            if (w instanceof Disruptor d && d.getDisruptorRange() > Ship.DERFACS_DISRUPTOR_RANGE)
                hasLongDisruptor = true;
        assertTrue("fixture: the D7 carries disruptors longer than 22", hasLongDisruptor);
        assertNull("fixture: and starts WITHOUT a DERFACS, or this test proves nothing",
                d7.getDerfacs());

        assertFalse("the rule is Orion's", d7.applyOrionDerfacs(168));
    }

    // ---------------------------------------------------------------- the other door

    /**
     * The year pass covers a disruptor declared in a hull's own weapons. No Orion hull does that
     * today — every one of them buys into a mount — so this exercises the path directly rather
     * than letting it rot unseen behind the door that currently does all the work.
     */
    @Test
    public void aHullDeclaredDisruptorIsCaughtByTheYearPass() {
        Ship slv = outfitted("Orion", "SLV", 168);
        assertNull("fixture: it declares no disruptor", slv.getDerfacs());

        slv.getWeapons().addWeapon(new Disruptor(30));

        assertTrue(slv.applyOrionDerfacs(168));
        assertNotNull(slv.getDerfacs());
    }

    /** Asking twice does not build a second one. */
    @Test
    public void theGrantIsIdempotent() {
        Ship br = orionWithMountedDisruptor("Disruptor-30", 168);
        com.sfb.systemgroups.DERFACS first = br.getDerfacs();

        assertFalse("already installed", br.applyOrionDerfacs(168));
        assertSame(first, br.getDerfacs());
    }

    /**
     * And it never resurrects one that has been shot off. DERFACS is destroyed permanently by a
     * hit-and-run raid (D6.5x); a later call must not quietly undo that.
     */
    @Test
    public void itDoesNotRebuildADerfacsThatWasShotOff() {
        Ship br = orionWithMountedDisruptor("Disruptor-30", 168);
        br.getDerfacs().damage();

        br.applyOrionDerfacs(170);

        assertFalse("still destroyed", br.getDerfacs().isFunctional());
    }
}
