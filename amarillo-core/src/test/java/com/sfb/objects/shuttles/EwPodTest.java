package com.sfb.objects.shuttles;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;

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

    private static Fighter ordinary() {
        Fighter a = CataloguedFighter.of("aas");
        a.setName("AAS-1");
        return a;
    }

    private static Fighter ewFighter() {
        Fighter e = CataloguedFighter.of("haas_e");
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
        Fighter ew = ewFighter();

        assertEquals("the same two rails as a standard HAAS", 2, railsOf(ew).size());
        assertEquals("both carrying pods, as the SSD shows", 2, ew.railEwPods());
        for (DroneRail rail : railsOf(ew))
            assertTrue(rail.hasEwPod());
    }

    @Test
    public void aPodOnARailMeansNoDroneOnIt() {
        Fighter aas = ordinary();
        DroneRail rail = railsOf(aas).get(0);
        rail.loadDrone(new Drone(DroneType.TypeI));

        Drone displaced = rail.fitEwPod();

        assertNotNull("the drone comes off (J4.962)", displaced);
        assertNull(rail.getDrone());
        assertTrue(rail.hasEwPod());
    }

    @Test
    public void aRailWithAPodRefusesADrone() {
        Fighter aas = ordinary();
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
        Fighter aas = ordinary();
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
        Fighter aas = ordinary();

        assertEquals(2, aas.maxEwPods());
        assertEquals("asking for four gets two", 2, aas.fitEwPods(4));
    }

    /**
     * A HAAS-E has only two standard rails, so reaching J4.964's four means two on the
     * rails and two slung extra. That is this airframe's arithmetic, NOT a general rule:
     * a fighter with four standard rails carries all four on them — see below.
     */
    @Test
    public void aTwoRailEwFighterReachesFourOnlyWithExtras() {
        Fighter ew = ewFighter();

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
        Fighter ew = ewFighter();
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
        Fighter aas = ordinary();
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
        Fighter aas = ordinary();
        int clean = aas.effectiveMaxSpeed();

        aas.fitEwPods(2);
        assertEquals("pods that replaced drones are free (J4.962)",
                clean, aas.effectiveMaxSpeed());

        Fighter other = ordinary();
        other.setExtraEwPods(2);
        assertEquals("a point each (J4.9621)", clean - 2, other.effectiveMaxSpeed());
    }

    @Test
    public void droppingAnExtraPodGivesTheSpeedBackAndLosesThePod() {
        Fighter aas = ordinary();
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
        Fighter aas = ordinary();
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
        Fighter aas = ordinary();
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
        Fighter ew = ewFighter();
        ew.setExtraEwPods(2);
        ew.allocatePodEw(8, 0);   // eight pod points, all ECM

        assertEquals("plus two built-in would be ten; J4.91 stops at six",
                6, ew.totalOwnEcm());
        assertEquals("and the other track is untouched", 2, ew.totalOwnEccm());
    }

    @Test
    public void sixEachNotSixTotal() {
        Fighter ew = ewFighter();
        ew.setExtraEwPods(2);
        ew.allocatePodEw(4, 4);

        assertEquals("J4.91 is explicit that it is six EACH", 6, ew.totalOwnEcm());
        assertEquals(6, ew.totalOwnEccm());
    }

    @Test
    public void aFighterWithNoPodsIsJustItsBuiltInTwo() {
        Fighter aas = ordinary();

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
        Fighter taas = CataloguedFighter.of("taas");
        taas.setName("TAAS-1");

        assertEquals("four rails in all", 4, railsOf(taas).size());
        assertEquals("but only two will take a pod", 2, taas.fitEwPods(4));

        for (DroneRail rail : railsOf(taas))
            if (rail.hasEwPod())
                assertEquals(DroneRail.DroneRailType.STANDARD, rail.getRailType());
    }

    /**
     * The general case, which the HAAS-E's two rails hide: a fighter with four standard
     * rails carries J4.964's full four ON them. No extras, so no speed or dogfight
     * penalty (J4.9621) — the cost is four drones instead.
     */
    @Test
    public void aFourRailEwFighterCarriesAllFourOnItsRails() {
        Fighter wide = new Fighter() {
            {
                setTwoSeater(true);          // J4.964's exception: four pods
                setMaxSpeed(12);
                for (char tag = 'A'; tag < 'E'; tag++) {
                    DroneRail rail = new DroneRail(DroneRail.DroneRailType.STANDARD);
                    rail.setDesignator(String.valueOf(tag));
                    getWeapons().addWeapon(rail);
                }
            }
        };
        int clean = wide.effectiveMaxSpeed();

        assertEquals("four standard rails", 4, railsOf(wide).size());
        assertEquals("all four pods go on rails", 4, wide.fitEwPods(4));
        assertEquals(4, wide.getEwPods());
        assertEquals("none are extras", 0, wide.getExtraEwPods());
        assertEquals("so no speed is given up (J4.9621 never applies)",
                clean, wide.effectiveMaxSpeed());
        assertEquals("eight points, the most J4.941 allows",
                8, wide.getEwPods() * Fighter.POINTS_PER_EW_POD);
    }

    /** J4.964 still holds an ORDINARY fighter to two, however many rails it has. */
    @Test
    public void railsDoNotRaiseAnOrdinaryFightersLimit() {
        Fighter wide = new Fighter() {
            {
                setMaxSpeed(12);             // not a two-seater
                for (char tag = 'A'; tag < 'E'; tag++) {
                    DroneRail rail = new DroneRail(DroneRail.DroneRailType.STANDARD);
                    rail.setDesignator(String.valueOf(tag));
                    getWeapons().addWeapon(rail);
                }
            }
        };

        assertEquals(4, railsOf(wide).size());
        assertEquals("J4.964: two for an ordinary fighter", 2, wide.fitEwPods(4));
    }

    /**
     * A podded rail carries no drone, so the box behind it stocks no reload for one (J4.822,
     * J4.962). A HAAS-E with both rails podded needs no ready rack at all — and previously
     * got one holding two Type-Is it could never load, charging its carrier two spaces of
     * Annex #7G stores for drones nobody could reach.
     */
    @Test
    public void aPoddedRailGetsNoReadyRackReload() {
        Fighter ewf = CataloguedFighter.of("haas_e");
        assertEquals("both rails podded", 2, ewf.getEwPods());
        assertNull("nothing left to reload",
                com.sfb.systemgroups.ReadyRack.forFighter(ewf));

        Fighter plain = CataloguedFighter.of("haas");
        com.sfb.systemgroups.ReadyRack rack =
                com.sfb.systemgroups.ReadyRack.forFighter(plain);
        assertNotNull("the drone-armed model still gets one", rack);
        assertEquals("one reload per rail (J4.822)", 2, rack.capacity());
    }

    /** Take a pod off and the rail wants its reload back. */
    @Test
    public void clearingThePodRestoresTheReload() {
        Fighter ewf = CataloguedFighter.of("haas_e");
        for (com.sfb.weapons.Weapon w : ewf.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                rail.clearEwPod();

        com.sfb.systemgroups.ReadyRack rack =
                com.sfb.systemgroups.ReadyRack.forFighter(ewf);
        assertNotNull(rack);
        assertEquals(2, rack.capacity());
    }

    // -------------------------------------------------------------------------
    // A purpose-built EW fighter (R1.F7): the Hydran Stinger-E
    // -------------------------------------------------------------------------

    /**
     * R1.F7: the Hydrans, ISC and Tholians "built a specific EW fighter which was the only
     * type they used" and do not convert a standard fighter. A Stinger-E's two fusion beams
     * were replaced by two EW pods at the factory.
     */
    @Test
    public void aStingerEsPodsArePartOfTheAirframe() {
        Fighter ewf = CataloguedFighter.of("stinger_e");

        assertEquals("two, per the SSD", 2, ewf.getEwPods());
        assertEquals("permanently fitted (J4.964)", 2, ewf.getFixedEwPods());
        assertEquals("nothing on a rail, because it has none", 0, ewf.railEwPods());
        assertEquals("and nothing slung extra", 0, ewf.getExtraEwPods());
        assertTrue("an EWF is a two-seat fighter (F3.222)", ewf.isTwoSeater());
    }

    /**
     * The pods cost it no speed, which is what rules out J4.9621's "extra" pods — their
     * whole signature is a point of speed and dogfight rating apiece. The SSD prints 15,
     * the same as the Stinger-2 this derives from.
     */
    @Test
    public void aStingerEIsNoSlowerThanTheStingerItDerivesFrom() {
        assertEquals(CataloguedFighter.of("stinger2").effectiveMaxSpeed(),
                CataloguedFighter.of("stinger_e").effectiveMaxSpeed());
        assertEquals(15, CataloguedFighter.of("stinger_e").effectiveMaxSpeed());
    }

    /**
     * The bug this airframe would have exposed. Pods were modelled as living on standard
     * drone rails (J4.962) — true of a Kzinti HAAS-E, and impossible for a Hydran fighter,
     * which has no rails at all. A Stinger-E built with {@code fitEwPods(2)} would have
     * come out with nothing and lent its squadron nothing.
     */
    @Test
    public void railMountingCannotWorkOnAFighterWithNoRails() {
        Fighter ewf = CataloguedFighter.of("stinger_e");
        assertTrue("no rails to hang anything on", railsOf(ewf).isEmpty());
        assertEquals("so the J4.962 route fits none", 0, ewf.fitEwPods(2));
        assertEquals("and the permanent pair is untouched", 2, ewf.getEwPods());
    }

    /** J4.961: two pods, four points — all of them lendable, since none are built-in EW. */
    @Test
    public void aStingerEGeneratesFourPodPoints() {
        Fighter ewf = CataloguedFighter.of("stinger_e");

        assertEquals(4, ewf.getEwPods() * Fighter.POINTS_PER_EW_POD);
        assertEquals("split evenly until declared otherwise (J4.961)", 2, ewf.getPodEcm());
        assertEquals(2, ewf.getPodEccm());
        assertEquals("2 built-in (J4.47) + 2 from the pods", 4, ewf.totalOwnEcm());
    }

    /**
     * J1.3322 draws the line between built-in POINTS and built-in PODS: "EW systems (EW
     * pods, MRS, SWAC) cease to function if the shuttle is crippled. Built-in EW points
     * continue to operate." Permanent or not, a pod is a pod.
     */
    @Test
    public void cripplingTakesEvenPermanentPods() {
        Fighter ewf = CataloguedFighter.of("stinger_e");
        ewf.applyCripplingEffects();                       // J1.33

        assertEquals("the pods stop", 0, ewf.getPodEcm());
        assertEquals("J4.47's two-and-two survives", 2, ewf.totalOwnEcm());
        assertEquals(2, ewf.totalOwnEccm());
        assertFalse("and there is nothing left to lend (J4.965)", ewf.podsWorking());
    }

    /**
     * J4.964 lets an EWF carry four "including any built-in", so a Stinger-E's two leave
     * room for two J4.9621 extras — at a point of speed each, which is the trade.
     */
    @Test
    public void theFixedPairLeavesRoomForTwoExtras() {
        Fighter ewf = CataloguedFighter.of("stinger_e");
        int clean = ewf.effectiveMaxSpeed();

        assertEquals(2, ewf.setExtraEwPods(2));
        assertEquals("J4.964's four, reached the long way", 4, ewf.getEwPods());
        assertEquals("J4.9621 charges a point of speed each", clean - 2,
                ewf.effectiveMaxSpeed());
        assertEquals("eight points now", 8, ewf.getEwPods() * Fighter.POINTS_PER_EW_POD);
    }

    /** No rails, no drones, so no reload behind it either (J4.822). */
    @Test
    public void aStingerEsBoxStocksNoDrones() {
        assertNull(com.sfb.systemgroups.ReadyRack.forFighter(CataloguedFighter.of("stinger_e")));
    }

    /**
     * A Stinger-E reads READY, because it is. It has nothing to arm, which is not the same as
     * being unarmed — showing it blank beside its green-dotted squadron-mates in the launch pad
     * said the opposite of the truth.
     */
    @Test
    public void aFighterWithNothingToArmReadsReady() {
        assertEquals("READY", com.sfb.systemgroups.FighterArming.armingState(CataloguedFighter.of("stinger_e")));
        assertFalse("and still gives a deck crew no work",
                com.sfb.systemgroups.FighterArming.needsArming(CataloguedFighter.of("stinger_e")));
    }

    /** An empty drone fighter is a different thing entirely, and must keep saying so. */
    @Test
    public void anUnarmedDroneFighterStillReadsEmpty() {
        assertEquals("EMPTY", com.sfb.systemgroups.FighterArming.armingState(CataloguedFighter.of("haas")));
    }

    /**
     * Null stays reserved for craft the question does not reach. An admin shuttle is not
     * "unarmed" — it is not a combat craft, and a green dot on one would be noise.
     */
    @Test
    public void aNonFighterStillHasNoArmingStateAtAll() {
        assertNull(com.sfb.systemgroups.FighterArming.armingState(new AdminShuttle()));
        assertNull(com.sfb.systemgroups.FighterArming.armingState(null));
    }

    // -------------------------------------------------------------------------
    // Arming a fighter whose rails carry pods (J4.962)
    // -------------------------------------------------------------------------

    /**
     * The crash this found in playtest. Picking the HAAS-E in the Commander's Options
     * "Fighters Ready" list threw IllegalStateException out of the submit endpoint: the
     * arming arithmetic counted its two podded rails as work to do, so it looked armable,
     * so armFully asked a podded rail to take a drone — and DroneRail.loadDrone refuses
     * rather than quietly drop the pod.
     */
    @Test
    public void armingAHaasEDoesNotThrow() {
        Fighter ewf = CataloguedFighter.of("haas_e");
        assertEquals("both rails carry pods", 2, ewf.railEwPods());

        com.sfb.systemgroups.FighterArming.armFully(ewf);   // must not throw

        assertEquals("and the pods are still there", 2, ewf.getEwPods());
        assertEquals("with no drone smuggled aboard", 0,
                com.sfb.systemgroups.FighterArming.dronesCarriedBy(ewf));
    }

    /**
     * The reason it was offered at all. A podded rail is not work for a deck crew, so a HAAS-E
     * with both rails podded has nothing to arm — the same answer as a Stinger-E, reached by a
     * different route.
     */
    @Test
    public void aFullyPoddedHaasEHasNothingToArm() {
        Fighter ewf = CataloguedFighter.of("haas_e");

        assertEquals(0, com.sfb.systemgroups.FighterArming.halfActionsToFullyArm(ewf));
        assertFalse(com.sfb.systemgroups.FighterArming.needsArming(ewf));
        assertEquals("so it too reads READY", "READY",
                com.sfb.systemgroups.FighterArming.armingState(ewf));
    }

    /** A rail freed of its pod is a rail to arm again, and the figures follow. */
    @Test
    public void clearingAPodMakesTheRailArmableAgain() {
        Fighter ewf = CataloguedFighter.of("haas_e");
        for (com.sfb.weapons.Weapon w : ewf.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                rail.clearEwPod();

        assertTrue(com.sfb.systemgroups.FighterArming.needsArming(ewf));
        com.sfb.systemgroups.FighterArming.armFully(ewf);
        assertEquals("two rails, two drones", 2,
                com.sfb.systemgroups.FighterArming.dronesCarriedBy(ewf));
    }

    /** A partly podded fighter arms the rails it still has free, and only those. */
    @Test
    public void onlyTheUnpoddedRailsGetDrones() {
        Fighter fighter = CataloguedFighter.of("haas");
        railsOf(fighter).get(0).fitEwPod();

        assertEquals("one rail of two is work", 1, fighter.getEwPods());
        com.sfb.systemgroups.FighterArming.armFully(fighter);
        assertEquals("the free rail only", 1,
                com.sfb.systemgroups.FighterArming.dronesCarriedBy(fighter));
    }
}
