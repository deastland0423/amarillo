package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.Squadron;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Haas;
import com.sfb.objects.shuttles.Haas_E;
import com.sfb.properties.EwLoan;
import com.sfb.properties.Location;
import com.sfb.weapons.DroneRail;

/**
 * An EW fighter lending its pod points to its squadron (J4.92, J4.93).
 * <p>
 * This is the whole reason an EWF exists. It gives up its entire drone armament for pods
 * (J4.962) and then hands what they generate to every squadron-mate in reach — J4.93: "Each
 * of the fighters receives all of the loaned points from the loaning unit." Not a share
 * each; all of them, each.
 * <p>
 * J4.965 draws the line that makes the trade meaningful: only POD points may be lent, never
 * J4.47's built-in two-and-two. An EWF that has dropped its pods is just a two-seat fighter.
 */
public class SquadronEwLendingTest {

    private Game game;
    private Ship carrier;
    private Ship enemy;
    private Squadron squadron;
    private Haas_E ewf;
    private Haas wingman;

    @Before
    public void setUp() {
        game = new Game();
        Player kzinti = new Player();
        kzinti.setTeamName("Kzinti");
        Player fed = new Player();
        fed.setTeamName("Federation");

        carrier = new Ship();
        carrier.init(com.sfb.samples.KzintiShips.getKzinBC());
        carrier.setName("KHS Longsword");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        carrier.setOwner(kzinti);
        game.getShips().add(carrier);

        enemy = new Ship();
        enemy.init(com.sfb.samples.FederationShips.getFedCa());
        enemy.setName("USS Attacker");
        enemy.setLocation(new Location(10, 20));
        enemy.setFacing(13);
        enemy.setOwner(fed);
        enemy.setActiveFireControl(true);
        game.getShips().add(enemy);

        squadron = new Squadron("Gold", carrier);

        ewf = new Haas_E();
        ewf.setName("HAAS-E");
        ewf.setOwner(kzinti);
        ewf.setLocation(new Location(10, 12));
        ewf.setFacing(13);

        wingman = new Haas();
        wingman.setName("HAAS-1");
        wingman.setOwner(kzinti);
        wingman.setLocation(new Location(10, 13));
        wingman.setFacing(13);

        game.getActiveShuttles().add(ewf);
        game.getActiveShuttles().add(wingman);
        assertNull(squadron.add(ewf));
        assertNull(squadron.add(wingman));

        game.startTurn();
        sweepLockOns();       // so the wingman can see the EWF (J4.921)
    }

    // -------------------------------------------------------------------------
    // The loan itself
    // -------------------------------------------------------------------------

    @Test
    public void aWingmanReceivesEverythingTheEwfsPodsMake() {
        assertNull(game.designateLentEwSource(wingman, ewf));

        // Two pods, four points, split evenly under J4.961.
        EwLoan loan = game.lentEwTo(wingman);
        assertEquals(2, loan.ecm());
        assertEquals(2, loan.eccm());
        assertEquals("J4.941 counts the combined total", 4, loan.combined());
    }

    /** J4.965: "not the built-in points, can be lent". An EWF with no pods lends nothing. */
    @Test
    public void builtInPointsAreNotLendable() {
        for (com.sfb.weapons.Weapon w : ewf.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                rail.clearEwPod();
        assertEquals(0, ewf.getEwPods());
        assertNull(game.designateLentEwSource(wingman, ewf));

        assertTrue("its J4.47 two-and-two stays home", game.lentEwTo(wingman).isNothing());
        assertEquals("but the EWF still has them itself", 2, ewf.totalOwnEcm());
    }

    /** J4.965: the EWF uses its pod points AND lends them — not one or the other. */
    @Test
    public void theEwfKeepsUsingWhatItLends() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        assertFalse(game.lentEwTo(wingman).isNothing());

        assertEquals("2 built-in (J4.47) + 2 from its own pods", 4, ewf.totalOwnEcm());
        assertEquals(4, game.ewAgainst(enemy, ewf).total());
    }

    /**
     * An ordinary fighter lends nothing, however many pods someone bolted to it — J4.965 gives
     * the power to an EWF, and R1.F7/J4.43 is what makes one.
     * <p>
     * Tested in a squadron with no EW fighter in it, since a squadron that HAS one would lend
     * automatically and mask the question.
     */
    @Test
    public void onlyAnEwFighterLends() {
        Haas podded = new Haas();
        podded.setName("HAAS-P");
        podded.setOwner(wingman.getOwner());
        podded.setLocation(new Location(10, 13));
        game.getActiveShuttles().add(podded);
        assertEquals("J4.964 allows an ordinary fighter two", 2, podded.fitEwPods(2));

        Haas mate = new Haas();
        mate.setName("HAAS-M");
        mate.setOwner(wingman.getOwner());
        mate.setLocation(new Location(10, 13));
        game.getActiveShuttles().add(mate);

        Squadron noEwf = new Squadron("Plain", carrier);
        assertNull(noEwf.add(podded));
        assertNull(noEwf.add(mate));
        sweepLockOns();

        assertNull("no EW fighter, so nothing lends at all", game.lentEwSourceOf(mate));
        assertTrue(game.lentEwTo(mate).isNothing());
        assertNotNull("and it cannot be made a source by naming it",
                game.designateLentEwSource(mate, podded));
    }

    // -------------------------------------------------------------------------
    // J4.921: who qualifies
    // -------------------------------------------------------------------------

    @Test
    public void threeHexesIsTheLimit() {
        wingman.setLocation(new Location(10, 15));       // three hexes away
        sweepLockOns();
        assertNull(game.designateLentEwSource(wingman, ewf));
        assertFalse(game.lentEwTo(wingman).isNothing());

        wingman.setLocation(new Location(10, 16));       // four
        assertTrue("J4.921 says three hexes", game.lentEwTo(wingman).isNothing());
    }

    /** J4.921: "an UNCRIPPLED EWF". The pods stop working anyway (J1.3322). */
    @Test
    public void aCrippledEwfLendsNothing() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        assertFalse(game.lentEwTo(wingman).isNothing());

        ewf.applyCripplingEffects();   // J1.33
        assertTrue(ewf.isCrippled());
        assertTrue(game.lentEwTo(wingman).isNothing());
    }

    /** J4.923: "A crippled shuttle CAN receive lent EW (J1.333)." The other way round. */
    @Test
    public void aCrippledWingmanStillReceives() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        wingman.applyCripplingEffects();   // J1.33
        assertTrue(wingman.isCrippled());

        assertFalse("the recipient's condition is not J4.921's business",
                game.lentEwTo(wingman).isNothing());
    }

    /** J4.921: "from its squadron". A fighter outside it gets nothing, however close. */
    @Test
    public void lendingDoesNotReachOutsideTheSquadron() {
        Haas stranger = new Haas();
        stranger.setName("HAAS-9");
        stranger.setOwner(wingman.getOwner());
        stranger.setLocation(new Location(10, 12));       // same hex as the EWF
        game.getActiveShuttles().add(stranger);
        sweepLockOns();

        assertNotNull(game.designateLentEwSource(stranger, ewf));
        assertTrue(game.lentEwTo(stranger).isNothing());
        assertFalse(game.ewLendingCandidates(stranger).contains(ewf));
    }

    /** J4.921: the RECIPIENT must hold the lock-on — the reverse of G24.218's scout. */
    @Test
    public void theRecipientNeedsTheLockOn() {
        assertTrue(wingman.hasLockOn(ewf));
        assertNull(game.designateLentEwSource(wingman, ewf));
        assertFalse(game.lentEwTo(wingman).isNothing());

        wingman.getLockOns().remove(ewf);
        assertTrue(game.lentEwTo(wingman).isNothing());
    }

    /** J4.967: the EWF can switch its pods off, and the squadron loses the loan with it. */
    @Test
    public void switchingThePodsOffEndsTheLoan() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        ewf.setPodsActive(false);

        assertTrue(game.lentEwTo(wingman).isNothing());
    }

    // -------------------------------------------------------------------------
    // J4.922: one source, changed every eight impulses
    // -------------------------------------------------------------------------

    /**
     * J4.922: "It CANNOT change just because the present source became unavailable but
     * would have to continue 'receiving' from that unit (even though it could not use the
     * points)." So an out-of-range EWF still occupies the slot.
     */
    @Test
    public void anUnusableSourceStillOccupiesTheSlot() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        wingman.setLocation(new Location(10, 20));        // out of J4.921 range

        assertTrue("no points", game.lentEwTo(wingman).isNothing());
        assertSame("but still the designated source", ewf, wingman.getLentEwSource());
    }

    /** J4.922: a first source is not a change — J1.343 allows it straight off the deck. */
    @Test
    public void aFirstSourceCostsNoWait() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        assertSame(ewf, wingman.getLentEwSource());
    }

    @Test
    public void swappingSourcesWaitsOutEightImpulses() {
        Haas_E second = new Haas_E();
        second.setName("HAAS-E2");
        second.setOwner(wingman.getOwner());
        second.setLocation(new Location(10, 11));
        game.getActiveShuttles().add(second);
        // J4.463 allows one EWF per squadron, so the second sits in a squadron of its own —
        // which is exactly why J4.921 will refuse it. The point under test is the CLOCK, so
        // check that the refusal names the interval and not the squadron.
        String refusal = game.designateLentEwSource(wingman, ewf);
        assertNull(refusal);

        String no = game.designateLentEwSource(wingman, second);
        assertNotNull(no);
        assertTrue("should be the eight-impulse rule, not eligibility: " + no,
                no.contains("J4.922"));
        assertSame(ewf, wingman.getLentEwSource());
    }

    // -------------------------------------------------------------------------
    // J4.91 and G24.2174: the ceilings
    // -------------------------------------------------------------------------

    /**
     * G24.2174: "a fighter cannot receive more than four points of ECM or four points of
     * ECCM." A four-pod EWF that puts all eight points into ECM still only lends four.
     */
    @Test
    public void noMoreThanFourPointsAreEverReceived() {
        Fighter wide = fourPodEwf();
        Squadron big = new Squadron("Wide", carrier);
        assertNull(big.add(wide));
        squadron.remove(wingman);
        assertNull(big.add(wingman));
        sweepLockOns();

        assertTrue("J4.961 permits an all-ECM declaration", wide.allocatePodEw(8, 0));
        assertNull(game.designateLentEwSource(wingman, wide));

        EwLoan loan = game.lentEwTo(wingman);
        assertEquals("G24.2174 caps it at four", 4, loan.ecm());
        assertEquals(0, loan.eccm());
    }

    /**
     * The J4.93 example, played out. Fighter #2 is an ordinary fighter carrying two pods of
     * its own and receiving four points from the EWF: "fighter #2 has eight points of ECM
     * and ECCM, but can only use six of each."
     */
    @Test
    public void theRulebooksOwnExample() {
        Fighter wide = fourPodEwf();                       // the example's fighter #1
        assertTrue(wide.allocatePodEw(4, 4));              // one ECM and one ECCM per pod

        Haas two = new Haas();                             // the example's fighter #2
        two.setName("HAAS-2");
        two.setOwner(wingman.getOwner());
        two.setLocation(new Location(10, 13));
        game.getActiveShuttles().add(two);
        assertEquals(2, two.fitEwPods(2));
        assertTrue(two.allocatePodEw(2, 2));

        Squadron big = new Squadron("Wide", carrier);
        assertNull(big.add(wide));
        assertNull(big.add(two));
        sweepLockOns();
        assertNull(game.designateLentEwSource(two, wide));

        assertEquals("all four points of ECM reach it", 4, game.lentEwTo(two).ecm());

        com.sfb.properties.EwBreakdown ew = game.ewAgainst(enemy, two);
        assertEquals("2 built-in + 2 of its own pods (J4.47, J4.96)", 4, ew.builtIn());
        assertEquals("and 2 of the 4 lent, which is all J4.91 leaves room for", 2, ew.lent());
        assertEquals("eight points held to six (J4.91)", 6, ew.total());
        assertEquals("and the same on the other side", 6, game.eccmOf(two));

        // The EWF itself, for completeness: "six points each (two built-in and four from
        // the pods)".
        assertEquals(6, game.ewAgainst(enemy, wide).total());
        assertEquals(6, game.eccmOf(wide));
    }

    /** The loan is real EW, so it shows up as a die shift on fire against the fighter. */
    @Test
    public void theLoanIsFeltInCombat() {
        // Baseline with nothing lending: out of the squadron, so its own two points and no more.
        squadron.remove(wingman);
        assertNull(game.lentEwSourceOf(wingman));
        assertEquals("2 built-in alone is +1 (D6.34 Step 5)",
                1, game.fireEcmShift(enemy, wingman));

        Fighter wide = fourPodEwf();
        assertTrue(wide.allocatePodEw(4, 4));
        Squadron big = new Squadron("Wide", carrier);
        assertNull(big.add(wide));
        assertNull(big.add(wingman));
        sweepLockOns();

        assertEquals("2 built-in + 4 lent = 6 points", 6, game.ewAgainst(enemy, wingman).total());
        assertEquals("six points is +2 on the die", 2, game.fireEcmShift(enemy, wingman));
    }

    /** J4.93: all of the points, to every qualifying fighter — not divided between them. */
    @Test
    public void everyWingmanGetsTheWholeLoan() {
        Haas third = new Haas();
        third.setName("HAAS-3");
        third.setOwner(wingman.getOwner());
        third.setLocation(new Location(11, 12));
        game.getActiveShuttles().add(third);
        assertNull(squadron.add(third));
        sweepLockOns();

        assertNull(game.designateLentEwSource(wingman, ewf));
        assertNull(game.designateLentEwSource(third, ewf));

        assertEquals(2, game.lentEwTo(wingman).ecm());
        assertEquals("not a point less for the second one", 2, game.lentEwTo(third).ecm());
    }

    /** An EWF that lands is no longer a source at all (as against merely unusable). */
    @Test
    public void recoveringTheEwfClearsTheDesignation() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        game.orphanSeekersOf(ewf);

        assertNull(wingman.getLentEwSource());
    }

    /**
     * A fighter with no drone rails still receives (J4.921).
     * <p>
     * The lock-on sweep used to skip any fighter that could guide no seeking weapons, on the
     * grounds that nothing it did needed a lock-on. J4.921 made that false: a fusion Stinger
     * flying wing on its squadron EWF needs the lock-on for the EW, not for a drone.
     */
    @Test
    public void aFighterWithNoRailsStillReceives() {
        com.sfb.objects.shuttles.Stinger1 stinger = new com.sfb.objects.shuttles.Stinger1();
        stinger.setName("Stinger1");
        stinger.setOwner(wingman.getOwner());
        stinger.setLocation(new Location(10, 13));
        stinger.setFacing(13);
        game.getActiveShuttles().add(stinger);
        assertEquals("no rails, so nothing to guide (J4.25)", 0, stinger.getControlCapacity());
        squadron.remove(wingman);
        assertNull(squadron.add(stinger));
        sweepLockOns();

        assertTrue("it must still acquire lock-ons (D6.11)", stinger.hasLockOn(ewf));
        assertNull(game.designateLentEwSource(stinger, ewf));
        assertEquals(2, game.lentEwTo(stinger).ecm());
    }

    /**
     * A Hydran squadron, end to end (R1.F7 + J4.93).
     * <p>
     * The Hydrans took the other route: rather than converting a fighter by hanging pods on
     * its drone rails (J4.962, the Kzinti way), they built the Stinger-E as its own airframe
     * with the pods permanent and the fusion beams gone. The lending machinery should not
     * care which route the points came by — J4.965 asks only that they came from PODS.
     */
    @Test
    public void aHydranEwFighterLendsJustTheSame() {
        com.sfb.objects.shuttles.Stinger_E hydranEwf =
                new com.sfb.objects.shuttles.Stinger_E();
        hydranEwf.setName("Stinger-E");
        hydranEwf.setOwner(wingman.getOwner());
        hydranEwf.setLocation(new Location(10, 12));
        hydranEwf.setFacing(13);

        com.sfb.objects.shuttles.Stinger2 stinger = new com.sfb.objects.shuttles.Stinger2();
        stinger.setName("Stinger-2");
        stinger.setOwner(wingman.getOwner());
        stinger.setLocation(new Location(10, 13));
        stinger.setFacing(13);

        game.getActiveShuttles().add(hydranEwf);
        game.getActiveShuttles().add(stinger);
        Squadron hydran = new Squadron("Hydran", carrier);
        assertNull(hydran.add(hydranEwf));
        assertNull(hydran.add(stinger));
        sweepLockOns();

        assertNull(game.designateLentEwSource(stinger, hydranEwf));
        EwLoan loan = game.lentEwTo(stinger);
        assertEquals("two permanent pods, four points, split evenly", 2, loan.ecm());
        assertEquals(2, loan.eccm());

        com.sfb.properties.EwBreakdown ew = game.ewAgainst(enemy, stinger);
        assertEquals("J4.47's two", 2, ew.builtIn());
        assertEquals("plus the Stinger-E's two", 2, ew.lent());
        assertEquals(4, ew.total());
    }

    /** J4.965 again, from the Hydran end: crippling the EWF ends its squadron's loan. */
    @Test
    public void aCrippledStingerELendsNothing() {
        com.sfb.objects.shuttles.Stinger_E hydranEwf =
                new com.sfb.objects.shuttles.Stinger_E();
        hydranEwf.setName("Stinger-E");
        hydranEwf.setOwner(wingman.getOwner());
        hydranEwf.setLocation(new Location(10, 12));
        game.getActiveShuttles().add(hydranEwf);

        com.sfb.objects.shuttles.Stinger2 stinger = new com.sfb.objects.shuttles.Stinger2();
        stinger.setName("Stinger-2");
        stinger.setOwner(wingman.getOwner());
        stinger.setLocation(new Location(10, 13));
        game.getActiveShuttles().add(stinger);

        Squadron hydran = new Squadron("Hydran", carrier);
        assertNull(hydran.add(hydranEwf));
        assertNull(hydran.add(stinger));
        sweepLockOns();
        assertNull(game.designateLentEwSource(stinger, hydranEwf));
        assertFalse(game.lentEwTo(stinger).isNothing());

        hydranEwf.applyCripplingEffects();       // J1.33 / J1.3322
        assertTrue(game.lentEwTo(stinger).isNothing());
    }

    // -------------------------------------------------------------------------
    // J4.93: the EWF lends to the squadron, and nobody has to ask
    // -------------------------------------------------------------------------

    /**
     * The point of the whole feature. J4.93: "A given carrier, EWF, MRS, or SWAC can loan the
     * points it is generating to all fighters (of a designated squadron) that are within the
     * appropriate distance and otherwise qualify." The designation belongs to the LENDER — an
     * EWF lends to its squadron, and a squadron-mate needs no instruction to benefit.
     * <p>
     * Before this, nothing in the game ever designated a source, so the lending machinery was
     * live in the fire path and permanently answering nothing.
     */
    @Test
    public void aSquadronMateReceivesWithoutBeingTold() {
        assertNull("nobody designated anything", wingman.getLentEwSource());

        assertSame("its squadron's EW fighter, found on its own",
                ewf, game.lentEwSourceOf(wingman));
        assertEquals(2, game.lentEwTo(wingman).ecm());
        assertEquals(2, game.lentEwTo(wingman).eccm());
        assertEquals("and it is felt in combat: 2 built-in + 2 lent",
                4, game.ewAgainst(enemy, wingman).total());
    }

    /** Every qualifying mate, not just the first (J4.93: each receives ALL of the points). */
    @Test
    public void thereIsNoQueueForIt() {
        Haas third = new Haas();
        third.setName("HAAS-3");
        third.setOwner(wingman.getOwner());
        third.setLocation(new Location(11, 12));
        game.getActiveShuttles().add(third);
        assertNull(squadron.add(third));
        sweepLockOns();

        assertEquals(2, game.lentEwTo(wingman).ecm());
        assertEquals(2, game.lentEwTo(third).ecm());
    }

    /** A fighter in no squadron is nobody's business (J4.92 groups them for a reason). */
    @Test
    public void aFighterOutsideASquadronReceivesNothing() {
        squadron.remove(wingman);

        assertNull(game.lentEwSourceOf(wingman));
        assertTrue(game.lentEwTo(wingman).isNothing());
    }

    /** The EWF does not lend to itself; J4.965 has it USE its pod points directly instead. */
    @Test
    public void theEwFighterIsNotItsOwnSource() {
        assertNull(game.lentEwSourceOf(ewf));
        assertTrue(game.lentEwTo(ewf).isNothing());
        assertEquals("it just has them: 2 built-in + 2 pods", 4, ewf.totalOwnEcm());
    }

    /**
     * An implicit source has no J4.922 stickiness, because nothing was declared to be held to:
     * fly out of range and the points simply stop, and come back when the formation closes up.
     */
    @Test
    public void anImplicitSourceComesAndGoesWithTheFormation() {
        assertFalse(game.lentEwTo(wingman).isNothing());

        wingman.setLocation(new Location(10, 17));        // five hexes out
        assertTrue(game.lentEwTo(wingman).isNothing());
        assertNull("and nothing is holding it to a dead source", wingman.getLentEwSource());

        wingman.setLocation(new Location(10, 13));        // back in formation
        assertFalse("the loan resumes", game.lentEwTo(wingman).isNothing());
    }

    /** A declared source still wins, which is how carrier lending will be chosen (J4.922). */
    @Test
    public void anExplicitDesignationOverridesTheSquadronDefault() {
        Fighter wide = fourPodEwf();
        Squadron big = new Squadron("Wide", carrier);
        assertNull(big.add(wide));
        squadron.remove(wingman);
        assertNull(big.add(wingman));
        sweepLockOns();
        assertTrue(wide.allocatePodEw(8, 0));

        assertSame("implicitly, its new squadron's EWF", wide, game.lentEwSourceOf(wingman));
        assertNull(game.designateLentEwSource(wingman, wide));
        assertSame("and now explicitly the same one", wide, wingman.getLentEwSource());
        assertEquals(4, game.lentEwTo(wingman).ecm());
    }

    /**
     * Give every fighter its lock-ons (D6.11, automatic at J1.31's sensor rating six).
     * <p>
     * The turn-start sweep lives in {@code beginImpulses}, which a test only reaches by
     * submitting an allocation for every ship. Nothing here is about allocation, so the
     * sweep is invoked directly — and it has to be invoked at all, because J4.921 makes a
     * lock-on a precondition of receiving lent EW.
     */
    private void sweepLockOns() {
        for (com.sfb.objects.shuttles.Shuttle craft : game.getActiveShuttles())
            if (craft instanceof Fighter f)
                game.acquireFighterLockOns(f);
    }

    /** A four-standard-rail EW fighter, which J4.964 lets carry the full four pods. */
    private Fighter fourPodEwf() {
        Fighter wide = new Fighter() {
            {
                setCatalogType("test_ewf");
                setTwoSeater(true);
                setMaxSpeed(12);
                setHull(11);
                setCrippledHull(8);
                for (char tag = 'A'; tag < 'E'; tag++) {
                    DroneRail rail = new DroneRail(DroneRail.DroneRailType.STANDARD);
                    rail.setDesignator(String.valueOf(tag));
                    getWeapons().addWeapon(rail);
                }
                fitEwPods(4);
            }
        };
        wide.setName("EWF-WIDE");
        wide.setOwner(wingman.getOwner());
        wide.setLocation(new Location(10, 12));
        wide.setFacing(13);
        game.getActiveShuttles().add(wide);
        return wide;
    }
}
