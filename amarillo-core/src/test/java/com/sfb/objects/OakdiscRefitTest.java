package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * FD4.5 OAKDISC, and which half of it the engine actually honours.
 *
 * <h2>The rule, in two parts</h2>
 * "Some Orion ships are configured for long-range drone bombardment or for short-ranged
 * anti-fighter work. These have sufficient tracking capacity to <b>fire every rack</b> (at its
 * maximum rate) every turn and to <b>control a number of seeking weapons equal to double the
 * current sensor rating</b>."
 *
 * <ul>
 * <li><b>The control half is LIVE</b>, and that is what this file pins. It needed no new code:
 *     {@code SpecialFunctions.getControlLimit} has always read
 *     {@code sensor[available] * controlModifier}, so a refit setting {@code controlMod} to 2.0
 *     is literally "double the current sensor rating" rather than an approximation of it.</li>
 * <li><b>The launch half is NOT built</b>, and neither is the FD4.4 cap it lifts — no rack-firing
 *     limit exists anywhere in the engine. Every Orion currently fires every rack, so the game
 *     behaves as though all of them had OAKDISC. See BACKLOG.md; building the BASE cap is the
 *     first step, and the refit then clears it.</li>
 * </ul>
 *
 * <p>Pinned here because the live half is easy to break from a distance: it depends on a data
 * value in a refit reaching a multiplier three classes away, with nothing in between that would
 * look wrong if the chain broke.
 */
public class OakdiscRefitTest {

    private ShipSpec sal;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        sal = ShipLibrary.get("Orion", "SAL");
        assertNotNull("fixture: the Orion Salvage Cruiser should be in the library", sal);
    }

    private static int controlLimit(ShipSpec spec) {
        return ShipLibrary.createShip(spec).getSpecialFunctions().getControlLimit();
    }

    /** The hull without the refit controls seekers equal to its sensor rating (controlMod 1.0). */
    @Test
    public void withoutOakdiscControlEqualsTheSensorRating() {
        Ship ship = ShipLibrary.createShip(sal);
        int sensor = ship.getSpecialFunctions().getSensor();

        assertTrue("fixture: the SAL should have a sensor rating to double", sensor > 0);
        assertEquals("an ordinary Orion controls seekers equal to its sensor rating",
                sensor, ship.getSpecialFunctions().getControlLimit());
    }

    /**
     * THE TEST THIS FILE EXISTS FOR: with OAKDISC, control is DOUBLE the sensor rating.
     *
     * Not "bigger" — exactly double, because FD4.5 says double and a multiplier that drifted to
     * 1.5 or 3.0 would still pass a looser assertion.
     */
    @Test
    public void oakdiscDoublesSeekerControl() {
        ShipSpec refitted = RefitResolver.apply(sal, List.of("O"));
        int sensor = ShipLibrary.createShip(sal).getSpecialFunctions().getSensor();

        assertEquals("FD4.5: \"double the current sensor rating\"",
                sensor * 2, controlLimit(refitted));
        assertEquals("and that is twice what the hull manages without it",
                controlLimit(sal) * 2, controlLimit(refitted));
    }

    /**
     * The refit must not quietly change anything else about the ship's control spaces.
     *
     * A refit sets fields by name through reflection, so a typo or a widened block would land
     * silently — the bridge is checked because it sits beside controlMod in the same map.
     */
    @Test
    public void oakdiscTouchesNothingButTheMultiplier() {
        Ship plain = ShipLibrary.createShip(sal);
        Ship fitted = ShipLibrary.createShip(RefitResolver.apply(sal, List.of("O")));

        assertEquals("the bridge is not part of this refit",
                plain.getControlSpaces().getBridge(), fitted.getControlSpaces().getBridge());
        assertEquals("nor is the sensor track itself — OAKDISC multiplies it, never raises it",
                plain.getSpecialFunctions().getSensor(),
                fitted.getSpecialFunctions().getSensor());
    }

    /**
     * FD4.5's own aside: "An Orion ship could further increase its seeking weapon control
     * abilities by installing a special sensor in an option mount and using (G24.24)."
     *
     * That is already modelled — {@code getControlLimit} adds {@code scoutControlBonus} on top of
     * the sensor × multiplier product, so the two stack without either knowing about the other.
     */
    @Test
    public void aScoutChannelBonusStacksOnTopOfOakdisc() {
        Ship fitted = ShipLibrary.createShip(RefitResolver.apply(sal, List.of("O")));
        int doubled = fitted.getSpecialFunctions().getControlLimit();

        fitted.getSpecialFunctions().setScoutControlBonus(6);

        assertEquals("G24.24's bonus is added to the doubled figure, not multiplied by it",
                doubled + 6, fitted.getSpecialFunctions().getControlLimit());
    }
}
