package com.sfb.objects.shuttles;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Electronic warfare pods (J4.96).
 * <p>
 * A fighter's built-in two-and-two (J4.47) is all it ever has of its own; pods are how it
 * gets more, and — the part that matters for an EW fighter — they are the ONLY points it
 * may lend to its squadron (J4.965). So the distinction between built-in and pod points is
 * load-bearing even though both add to the same to-hit sum.
 */
public class EwPodTest {

    private static Aas ordinary() {
        Aas a = new Aas();
        a.setName("AAS-1");
        return a;
    }

    private static Haas_E ewFighter() {
        Haas_E e = new Haas_E();
        e.setName("HAAS-E-1");
        return e;
    }

    // -------------------------------------------------------------------------
    // J4.964: how many
    // -------------------------------------------------------------------------

    @Test
    public void anOrdinaryFighterTakesTwoPodsAndNoMore() {
        Aas aas = ordinary();

        assertEquals(2, aas.maxEwPods());
        assertEquals("asking for four gets two", 2, aas.setEwPods(4));
    }

    @Test
    public void anEwFighterTakesFour() {
        Haas_E ew = ewFighter();

        assertEquals("J4.964's exception for EWFs", 4, ew.maxEwPods());
        assertEquals(4, ew.setEwPods(4));
    }

    // -------------------------------------------------------------------------
    // J4.961: two points each, split as you like
    // -------------------------------------------------------------------------

    @Test
    public void eachPodIsTwoPoints() {
        Haas_E ew = ewFighter();
        ew.setEwPods(4);

        assertTrue("all eight as ECM", ew.allocatePodEw(8, 0));
        assertEquals(8, ew.getPodEcm());
        assertEquals(0, ew.getPodEccm());

        assertTrue("or split", ew.allocatePodEw(3, 5));
        assertEquals(3, ew.getPodEcm());
        assertEquals(5, ew.getPodEccm());
    }

    @Test
    public void anAllocationMustSpendExactlyWhatThePodsMake() {
        Aas aas = ordinary();
        aas.setEwPods(2);   // four points

        assertFalse("three is not four", aas.allocatePodEw(2, 1));
        assertFalse("five is not four", aas.allocatePodEw(3, 2));
        assertTrue(aas.allocatePodEw(2, 2));
    }

    // -------------------------------------------------------------------------
    // J4.9621/J4.9622: the pods that cost speed
    // -------------------------------------------------------------------------

    @Test
    public void anExtraPodCostsAPointOfSpeed() {
        Aas aas = ordinary();
        int clean = aas.effectiveMaxSpeed();
        aas.setEwPods(2);

        assertEquals("pods that replaced drones are free (J4.962)",
                clean, aas.effectiveMaxSpeed());

        aas.setExtraEwPods(2);

        assertEquals("a point each (J4.9621)", clean - 2, aas.effectiveMaxSpeed());
    }

    @Test
    public void droppingAnExtraPodGivesTheSpeedBackAndLosesThePod() {
        Aas aas = ordinary();
        int clean = aas.effectiveMaxSpeed();
        aas.setEwPods(2);
        aas.setExtraEwPods(1);
        assertEquals(clean - 1, aas.effectiveMaxSpeed());

        assertTrue(aas.dropExtraEwPod());

        assertEquals("speed back immediately (J4.9622)", clean, aas.effectiveMaxSpeed());
        assertEquals("and the pod is gone, not stowed", 1, aas.getEwPods());
        assertFalse("nothing left to drop", aas.dropExtraEwPod());
    }

    // -------------------------------------------------------------------------
    // J4.967 and J1.3322: when the pods stop
    // -------------------------------------------------------------------------

    @Test
    public void podsCanBeSwitchedOff() {
        Aas aas = ordinary();
        aas.setEwPods(2);
        aas.allocatePodEw(4, 0);
        assertEquals(4, aas.getPodEcm());

        aas.setPodsActive(false);

        assertEquals("J4.967: off is off", 0, aas.getPodEcm());
        assertEquals("but the built-in two remain", 2, aas.totalOwnEcm());
    }

    /**
     * J1.3322: "EW systems (EW pods, MRS, SWAC) cease to function if the shuttle is
     * crippled. Built-in EW points continue to operate." The clearest statement of why
     * built-in and pod points are not the same thing.
     */
    @Test
    public void cripplingTakesThePodsAndLeavesTheBuiltIn() {
        Aas aas = ordinary();
        aas.setEwPods(2);
        aas.allocatePodEw(4, 0);
        assertEquals(6, aas.totalOwnEcm());

        aas.setCurrentHull(1);
        aas.applyCripplingEffects();

        assertEquals("the pods are out", 0, aas.getPodEcm());
        assertEquals("J4.47's two keep working", 2, aas.totalOwnEcm());
        assertEquals(2, aas.totalOwnEccm());
    }

    // -------------------------------------------------------------------------
    // J4.91: the ceiling
    // -------------------------------------------------------------------------

    @Test
    public void aFighterCannotUseMoreThanSixOfEither() {
        Haas_E ew = ewFighter();
        ew.setEwPods(4);
        ew.allocatePodEw(8, 0);   // eight pod points, all ECM

        assertEquals("plus two built-in would be ten; J4.91 stops at six",
                6, ew.totalOwnEcm());
        assertEquals("and the other track is untouched", 2, ew.totalOwnEccm());
    }

    @Test
    public void sixEachNotSixTotal() {
        Haas_E ew = ewFighter();
        ew.setEwPods(4);
        ew.allocatePodEw(4, 4);

        assertEquals("J4.91 is explicit that it is six EACH", 6, ew.totalOwnEcm());
        assertEquals(6, ew.totalOwnEccm());
    }

    @Test
    public void aFighterWithNoPodsIsJustItsBuiltInTwo() {
        Aas aas = ordinary();

        assertEquals(0, aas.getEwPods());
        assertEquals(2, aas.totalOwnEcm());
        assertEquals(2, aas.totalOwnEccm());
    }
}
