package com.sfb.objects.shuttles;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * Electronic warfare pods (J4.96).
 * <p>
 * A fighter's built-in two-and-two (J4.47) is all it ever has of its own; pods are how it
 * gets more, and by J4.965 they are the ONLY points it may lend to its squadron. So the
 * distinction is load-bearing even though both add to the same to-hit sum.
 * <p>
 * A pod is not free. J4.962: "An EWP replaces one drone carried by the fighter" — which is
 * what the SSDs show, an EW fighter with the same rails as the standard model, pods on
 * them and two fewer drones aboard. The only pods that do NOT cost a drone are the extras
 * of J4.9621, and those cost speed and dogfight rating instead.
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

    private static java.util.List<DroneRail> railsOf(Fighter f) {
        java.util.List<DroneRail> out = new java.util.ArrayList<>();
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                out.add(rail);
        return out;
    }

    // -------------------------------------------------------------------------
    // J4.962: a pod sits on a rail, in place of a drone
    // -------------------------------------------------------------------------

    @Test
    public void theEwFighterCarriesItsPodsOnItsRails() {
        Haas_E ew = ewFighter();

        assertEquals("the same two rails as a standard HAAS", 2, railsOf(ew).size());
        assertEquals("both carrying pods, as the SSD shows", 2, ew.railEwPods());
        for (DroneRail rail : railsOf(ew))
            assertTrue(rail.hasEwPod());
    }

    @Test
    public void aPodOnARailMeansNoDroneOnIt() {
        Aas aas = ordinary();
        DroneRail rail = railsOf(aas).get(0);
        rail.loadDrone(new Drone(DroneType.TypeI));

        Drone displaced = rail.fitEwPod();

        assertNotNull("the drone comes off (J4.962)", displaced);
        assertNull(rail.getDrone());
        assertTrue(rail.hasEwPod());
    }

    @Test
    public void aRailWithAPodRefusesADrone() {
        Aas aas = ordinary();
        DroneRail rail = railsOf(aas).get(0);
        rail.fitEwPod();

        try {
            rail.loadDrone(new Drone(DroneType.TypeI));
            fail("a rail holds one or the other");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("J4.962"));
        }
    }

    @Test
    public void takingThePodOffFreesTheRailAgain() {
        Aas aas = ordinary();
        DroneRail rail = railsOf(aas).get(0);
        rail.fitEwPod();

        assertTrue(rail.clearEwPod());
        rail.loadDrone(new Drone(DroneType.TypeI));

        assertNotNull(rail.getDrone());
    }

    // -------------------------------------------------------------------------
    // J4.964: how many
    // -------------------------------------------------------------------------

    @Test
    public void anOrdinaryFighterTakesTwoPodsAndNoMore() {
        Aas aas = ordinary();

        assertEquals(2, aas.maxEwPods());
        assertEquals("asking for four gets two", 2, aas.fitEwPods(4));
    }

    /**
     * An EW fighter's four (J4.964) come out as two on its rails and two slung extra —
     * which is exactly J4.9621's limit of two extras. The two rules meet at four.
     */
    @Test
    public void anEwFightersFourArePodsOnRailsPlusTheTwoExtras() {
        Haas_E ew = ewFighter();

        assertEquals(4, ew.maxEwPods());
        assertEquals("only two rails to hang them on", 2, ew.railEwPods());

        ew.setExtraEwPods(2);

        assertEquals(2, ew.getExtraEwPods());
        assertEquals("four all told, as J4.941 assumes", 4, ew.getEwPods());
    }

    // -------------------------------------------------------------------------
    // J4.961: two points each, split as you like
    // -------------------------------------------------------------------------

    @Test
    public void eachPodIsTwoPoints() {
        Haas_E ew = ewFighter();
        ew.setExtraEwPods(2);   // four pods, eight points

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
        aas.fitEwPods(2);   // four points

        assertFalse("three is not four", aas.allocatePodEw(2, 1));
        assertFalse("five is not four", aas.allocatePodEw(3, 2));
        assertTrue(aas.allocatePodEw(2, 2));
    }

    // -------------------------------------------------------------------------
    // J4.9621/J4.9622: the pods that cost speed instead of a drone
    // -------------------------------------------------------------------------

    @Test
    public void aPodOnARailIsFreeButAnExtraCostsSpeed() {
        Aas aas = ordinary();
        int clean = aas.effectiveMaxSpeed();

        aas.fitEwPods(2);
        assertEquals("pods that replaced drones are free (J4.962)",
                clean, aas.effectiveMaxSpeed());

        Aas other = ordinary();
        other.setExtraEwPods(2);
        assertEquals("a point each (J4.9621)", clean - 2, other.effectiveMaxSpeed());
    }

    @Test
    public void droppingAnExtraPodGivesTheSpeedBackAndLosesThePod() {
        Aas aas = ordinary();
        int clean = aas.effectiveMaxSpeed();
        aas.setExtraEwPods(1);
        assertEquals(clean - 1, aas.effectiveMaxSpeed());

        assertTrue(aas.dropExtraEwPod());

        assertEquals("speed back immediately (J4.9622)", clean, aas.effectiveMaxSpeed());
        assertEquals("and the pod is gone, not stowed", 0, aas.getEwPods());
        assertFalse("nothing left to drop", aas.dropExtraEwPod());
    }

    // -------------------------------------------------------------------------
    // J4.967 and J1.3322: when the pods stop
    // -------------------------------------------------------------------------

    @Test
    public void podsCanBeSwitchedOff() {
        Aas aas = ordinary();
        aas.fitEwPods(2);
        aas.allocatePodEw(4, 0);
        assertEquals(4, aas.getPodEcm());

        aas.setPodsActive(false);

        assertEquals("J4.967: off is off", 0, aas.getPodEcm());
        assertEquals("but the built-in two remain", 2, aas.totalOwnEcm());
    }

    /**
     * J1.3322: "EW systems (EW pods, MRS, SWAC) cease to function if the shuttle is
     * crippled. Built-in EW points continue to operate."
     */
    @Test
    public void cripplingTakesThePodsAndLeavesTheBuiltIn() {
        Aas aas = ordinary();
        aas.fitEwPods(2);
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
        ew.setExtraEwPods(2);
        ew.allocatePodEw(8, 0);   // eight pod points, all ECM

        assertEquals("plus two built-in would be ten; J4.91 stops at six",
                6, ew.totalOwnEcm());
        assertEquals("and the other track is untouched", 2, ew.totalOwnEccm());
    }

    @Test
    public void sixEachNotSixTotal() {
        Haas_E ew = ewFighter();
        ew.setExtraEwPods(2);
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

    // -------------------------------------------------------------------------
    // J4.2312: which rails will take one
    // -------------------------------------------------------------------------

    /**
     * "EW pods can be carried on standard drone rails, but cannot be carried on other
     * types of rails." Stricter than the space argument: a pod is one space so a
     * half-space LIGHT rail obviously cannot take one, but the rule bars a HEAVY rail
     * too, which has room to spare.
     */
    @Test
    public void onlyAStandardRailTakesAPod() {
        assertTrue(new com.sfb.weapons.DroneRail(
                DroneRail.DroneRailType.STANDARD).canCarryEwPod());
        assertFalse("half a space, and barred anyway",
                new com.sfb.weapons.DroneRail(
                        DroneRail.DroneRailType.LIGHT).canCarryEwPod());
        assertFalse("room to spare, and still barred (J4.2312)",
                new com.sfb.weapons.DroneRail(
                        DroneRail.DroneRailType.HEAVY).canCarryEwPod());
        assertFalse(new com.sfb.weapons.DroneRail(
                DroneRail.DroneRailType.SPECIAL).canCarryEwPod());
    }

    @Test
    public void aLightRailRefusesAPodOutright() {
        com.sfb.weapons.DroneRail light =
                new com.sfb.weapons.DroneRail(DroneRail.DroneRailType.LIGHT);
        light.setDesignator("C");

        try {
            light.fitEwPod();
            fail("a light rail cannot carry an EW pod");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("J4.2312"));
        }
    }

    /** A TAAS has two standard rails and two light: only the standard pair take pods. */
    @Test
    public void aMixedRailFighterHangsPodsOnItsStandardRailsOnly() {
        Taas taas = new Taas();
        taas.setName("TAAS-1");

        assertEquals("four rails in all", 4, railsOf(taas).size());
        assertEquals("but only two will take a pod", 2, taas.fitEwPods(4));

        for (DroneRail rail : railsOf(taas))
            if (rail.hasEwPod())
                assertEquals(DroneRail.DroneRailType.STANDARD, rail.getRailType());
    }
}
