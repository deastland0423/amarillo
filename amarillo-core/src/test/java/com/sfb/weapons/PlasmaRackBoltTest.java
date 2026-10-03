package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Ship;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;

/**
 * The plasma rack's second "means" (FP10.22) — the bolt — and the restrictions that come with
 * each mode, including the three that belong to the SHIP rather than the rack (FP10.24).
 *
 * <h2>Why some of this is on Ship</h2>
 * FP10.24 opens with the reason: "Due to fire control restrictions, a ship with one or more
 * plasma racks is under the following limitations." A rack cannot enforce a limit it cannot see
 * the other racks breaking, so FP10.241 and FP10.242 live on {@link Ship} and are tested here
 * beside the rack rules they interact with.
 */
public class PlasmaRackBoltTest {

    private PlasmaRack rack;
    private TurnTracker clock;

    private static final int CRUISER = 3;    // size-4 or larger, so FP10.241 applies
    private static final int FIGHTER = 6;    // size-5 or smaller, so defensive mode reaches it

    @Before
    public void setUp() {
        clock = new TurnTracker();
        rack = new PlasmaRack();
        rack.setClock(clock);
        rack.setDesignator("1");
        rack.activate(rack.activationEnergyWanted());
        // fire() no longer defaults a mode - that silent DEFENSIVE default is what let a rack
        // bolt a cruiser through the ordinary fire path (see PlasmaRackBoltFirePathTest). Every
        // test here is about the BOLT rather than about the declaration, so the fixture declares
        // one; the tests that care about the mode itself set their own.
        rack.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);
    }

    /** Declare the mode a bolt needs, for a rack this test made itself. */
    private static PlasmaRack ready(PlasmaRack r, TurnTracker clock, String designator) {
        r.setClock(clock);
        r.setDesignator(designator);
        r.activate(r.activationEnergyWanted());
        r.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);
        return r;
    }

    private void advanceTo(int absoluteImpulse) {
        while (clock.getImpulse() < absoluteImpulse)
            clock.nextImpulse();
    }

    // ---------------------------------------------------------------- damage (FP8.43)

    /**
     * FP8.43: "equal to one-half of the warhead strength of the corresponding plasma torpedo
     * (S-bolt = S-torpedo) at the true range to the target."
     * <p>
     * Asserted against a type-D torpedo walked to the same range rather than against a literal,
     * so the bolt and the seeker can never disagree about the warhead — FP8.43 defines one in
     * terms of the other, and a copied number would be free to drift.
     */
    @Test
    public void aBoltDoesHalfTheTypeDWarheadAtTheTrueRange() throws Exception {
        int range = 4;
        PlasmaTorpedo reference = new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
        for (int i = 0; i < range; i++)
            reference.incrementDistance();
        int expected = reference.getCurrentStrength() / 2;

        // The die is unseeded, so a single shot that missed would assert nothing. Fired until a
        // hit lands - FP8.42 needs a 1-4 at this range, so forty tries miss every time with
        // probability (1/3)^40 - and the test fails if no hit ever arrives rather than passing
        // on an empty set of observations.
        java.util.Set<Integer> hits = new java.util.TreeSet<>();
        for (int attempt = 0; attempt < 40; attempt++) {
            PlasmaRack fresh = ready(new PlasmaRack(), clock, "x");
            int damage = fresh.fire(range);
            if (damage > 0)
                hits.add(damage);
        }

        assertFalse("no bolt hit in forty attempts, so nothing was checked", hits.isEmpty());
        assertEquals("every hit is half the type-D warhead at the true range",
                java.util.Set.of(expected), hits);
    }

    /**
     * FP8.42 bases the to-hit on EFFECTIVE range while FP8.43 bases the damage on TRUE range, so
     * the two-argument form must not pass one where the other belongs. A scanner makes a bolt
     * harder to land, not weaker when it lands.
     * <p>
     * Driven at range 0 against an adjusted range of 30, where the to-hit is hardest (a 1 on
     * FP8.42's chart) and the warhead is at its strongest: a hit therefore proves the damage came
     * from the TRUE range, since the adjusted range's warhead is zero and would have thrown.
     */
    @Test
    public void theScannerWeakensTheToHitAndNotTheWarhead() throws Exception {
        advanceTo(5);

        // At range 30 a type-D warhead is 0, so reading the damage off the ADJUSTED range would
        // throw TargetOutOfRangeException. Reaching the assertion at all is the proof; what the
        // die then did is a separate matter.
        int damage = rack.fire(0, 30);     // point blank, seen as thirty hexes away

        PlasmaTorpedo pointBlank = new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
        assertEquals("fixture: a type-D is at full strength at range 0",
                10, pointBlank.getCurrentStrength());
        assertTrue("a miss, or the point-blank bolt - never the adjusted range's zero",
                damage == 0 || damage == pointBlank.getCurrentStrength() / 2);

        // And the other way round, to show the adjusted range is not simply ignored: at an
        // adjusted range beyond the chart there is no to-hit number at all, so it must throw
        // even though the TRUE range is point blank.
        PlasmaRack other = ready(new PlasmaRack(), clock, "y");
        try {
            other.fire(0, 99);
            fail("an adjusted range off the FP8.42 chart has no to-hit number");
        } catch (TargetOutOfRangeException expected) {
            assertEquals("and nothing was spent", 4, other.getTorpedoes());
        }
    }

    /** Beyond the type-D's reach there is nothing to bolt with. */
    @Test
    public void aBoltBeyondTheWarheadsRangeIsRefused() throws Exception {
        advanceTo(5);
        try {
            rack.fire(20);
            fail("a type-D warhead is zero by range 16");
        } catch (TargetOutOfRangeException expected) {
            assertEquals("and no torpedo was spent", 4, rack.getTorpedoes());
        }
    }

    // ---------------------------------------------------------------- one bolt a turn (FP10.221)

    /**
     * FP10.221: "In either mode, the rack can fire a maximum of one torpedo per turn as a plasma
     * bolt."
     * <p>
     * The one limit the firing rates do not already imply. In defensive mode the rack may fire on
     * every impulse, so without this it could bolt four times in four impulses.
     */
    @Test
    public void aRackBoltsOnlyOncePerTurnEvenInDefensiveMode() throws Exception {
        advanceTo(5);
        rack.declareBoltMode(PlasmaRack.RackMode.DEFENSIVE);   // this test is about defensive mode
        rack.fire(3);                                  // the turn's bolt, in defensive mode

        clock.nextImpulse();   // a new impulse, so the per-impulse rate is satisfied
        String refusal = rack.boltRefusal(PlasmaRack.RackMode.DEFENSIVE);

        assertNotNull("a second bolt in the same turn", refusal);
        assertTrue(refusal, refusal.contains("FP10.221"));
        assertNull("but a SEEKING launch is still fine",
                rack.launchRefusal(PlasmaRack.RackMode.DEFENSIVE));
    }

    /** A new turn returns the bolt, like every other per-turn allowance. */
    @Test
    public void theBoltComesBackNextTurn() throws Exception {
        advanceTo(5);
        rack.fire(3);
        assertEquals(1, rack.getBoltsThisTurn());

        advanceTo(40);
        rack.cleanUp();

        assertEquals(0, rack.getBoltsThisTurn());
        assertNull(rack.boltRefusal(PlasmaRack.RackMode.OFFENSIVE));
    }

    /**
     * FP10.22 makes bolt and seeking two means of using one torpedo, so everything that refuses a
     * launch refuses a bolt. Asserted on the activation gate, which is the one a player meets
     * first at setup.
     */
    @Test
    public void aBoltIsRefusedForEveryReasonALaunchIs() {
        PlasmaRack cold = new PlasmaRack();
        cold.setClock(clock);
        cold.setDesignator("2");          // nothing activated

        String refusal = cold.boltRefusal(PlasmaRack.RackMode.DEFENSIVE);

        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains("FP9.22"));
    }

    /**
     * {@code fire} ENFORCES the rules rather than trusting the caller to have asked
     * {@link PlasmaRack#boltRefusal} first.
     * <p>
     * This is the gap that writing these tests exposed: the first version of {@code fire} spent a
     * torpedo and rolled the die with no checks at all, so any caller reaching it directly got a
     * free bolt past the activation gate, the rates and FP10.221 - and {@code activeTorpedoes}
     * could go negative. {@code DroneRack.fire} guards itself the same way.
     */
    @Test
    public void fireRefusesWhatBoltRefusalWouldHaveRefused() throws Exception {
        advanceTo(5);
        rack.declareBoltMode(PlasmaRack.RackMode.DEFENSIVE);
        rack.fire(3);                       // the turn's one bolt (FP10.221)
        clock.nextImpulse();

        try {
            // Declared afresh: a declaration is consumed by the shot it was made for, so this
            // must be refused by FP10.221 rather than for want of a mode. Defensive, which the
            // rack is now committed to, so the mode itself cannot be the reason either.
            rack.declareBoltMode(PlasmaRack.RackMode.DEFENSIVE);
            rack.fire(3);
            fail("fire must not bypass FP10.221");
        } catch (com.sfb.exceptions.WeaponUnarmedException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FP10.221"));
        }
        assertEquals("and nothing more was spent", 3, rack.getTorpedoes());
        assertEquals("nor driven negative", 3, rack.getActiveTorpedoes());
    }

    /**
     * FP10.21: "a bolt is a firing", so it settles the mode for the turn exactly as a launch
     * does. Without this a rack could bolt and then still choose offensive mode afterwards.
     */
    @Test
    public void aBoltSettlesTheModeForTheTurn() throws Exception {
        advanceTo(5);
        assertEquals(PlasmaRack.RackMode.UNDECIDED, rack.getModeThisTurn());

        rack.bolt(PlasmaRack.RackMode.DEFENSIVE, 3, 3);

        assertEquals(PlasmaRack.RackMode.DEFENSIVE, rack.getModeThisTurn());
        String refusal = rack.launchRefusal(PlasmaRack.RackMode.OFFENSIVE);
        assertNotNull("offensive mode is closed for the turn", refusal);
        assertTrue(refusal, refusal.contains("FP10.21"));
    }

    // ---------------------------------------------------------------- what each mode may engage

    /**
     * FP10.212: "Plasma racks may fire in this mode at size-5 and smaller targets within an
     * effective range of six hexes from the firing ship."
     * <p>
     * Both halves, and both are what defensive mode PAYS for its rate.
     */
    @Test
    public void defensiveModeReachesSixHexesAndSmallTargetsOnly() {
        assertNull("a fighter at four hexes",
                rack.targetRefusal(PlasmaRack.RackMode.DEFENSIVE, FIGHTER, 4));

        String tooBig = rack.targetRefusal(PlasmaRack.RackMode.DEFENSIVE, CRUISER, 4);
        assertNotNull("a cruiser is size-4 or larger", tooBig);
        assertTrue(tooBig, tooBig.contains("FP10.212"));

        String tooFar = rack.targetRefusal(PlasmaRack.RackMode.DEFENSIVE, FIGHTER, 7);
        assertNotNull("seven hexes", tooFar);
        assertTrue(tooFar, tooFar.contains("FP10.212"));

        assertNull("six is inside it",
                rack.targetRefusal(PlasmaRack.RackMode.DEFENSIVE, FIGHTER, 6));
    }

    /**
     * FP10.211: offensive mode "can engage any target in its arc within the other rules of the
     * game... there are no restrictions as to target type or range other than the capabilities of
     * the weapon itself and (FP10.24)."
     */
    @Test
    public void offensiveModeRestrictsNeitherSizeNorRange() {
        assertNull(rack.targetRefusal(PlasmaRack.RackMode.OFFENSIVE, CRUISER, 12));
        assertNull(rack.targetRefusal(PlasmaRack.RackMode.OFFENSIVE, FIGHTER, 1));
    }

    // ---------------------------------------------------------------- per SHIP (FP10.24)

    /**
     * FP10.241: "A ship armed with plasma racks may not fire more than one type-D plasma bolt at
     * a size-4 or larger target during any given turn. This restriction is per firing ship, not
     * per rack... not one bolt each at four different targets, or one bolt each from two or more
     * racks at a target."
     * <p>
     * So neither spreading the bolts across targets nor across racks helps, and the test asserts
     * the limit without reference to either — which is the point of counting on the ship.
     */
    @Test
    public void aShipBoltsOneTypeDAtALargeTargetPerTurn() {
        Ship ship = new Ship();
        ship.setName("KRV");
        int turn = 1;

        assertNull(ship.plasmaRackBoltRefusal(turn, CRUISER));
        ship.recordPlasmaRackBolt(turn, CRUISER);

        String refusal = ship.plasmaRackBoltRefusal(turn, CRUISER);
        assertNotNull("a second large-target bolt", refusal);
        assertTrue(refusal, refusal.contains("FP10.241"));

        // A DIFFERENT large target is the same refusal - the rule is per ship, per turn.
        assertNotNull("a different cruiser is no better",
                ship.plasmaRackBoltRefusal(turn, 2));
    }

    /** FP10.241 covers "size-4 or larger" and nothing else: small targets are not counted. */
    @Test
    public void boltsAtFightersAreNotLimitedByTheShip() {
        Ship ship = new Ship();
        ship.setName("KRV");
        int turn = 1;

        ship.recordPlasmaRackBolt(turn, FIGHTER);
        ship.recordPlasmaRackBolt(turn, FIGHTER);

        assertNull("still free to bolt a fighter", ship.plasmaRackBoltRefusal(turn, FIGHTER));
        assertNull("and the large-target allowance is untouched",
                ship.plasmaRackBoltRefusal(turn, CRUISER));
    }

    /**
     * The limit belongs to a TURN, and is stamped with it rather than cleared by anything.
     * <p>
     * Game's own comment records what the alternative costs: a counter whose reset was never
     * called turned "J4.241's two drones a turn" into two a game. A turn-stamped count cannot
     * fail that way, because a count belonging to another turn reads as zero.
     */
    @Test
    public void theLargeTargetBoltReturnsNextTurn() {
        Ship ship = new Ship();
        ship.setName("KRV");

        ship.recordPlasmaRackBolt(1, CRUISER);
        assertNotNull(ship.plasmaRackBoltRefusal(1, CRUISER));

        assertNull("turn 2 is a fresh allowance", ship.plasmaRackBoltRefusal(2, CRUISER));
    }

    /**
     * FP10.242: "A ship armed with plasma racks may not use more than two of those racks in
     * offensive mode during a given turn."
     */
    @Test
    public void aShipPutsTwoRacksIntoOffensiveModePerTurn() {
        Ship ship = new Ship();
        ship.setName("Gorn BC");
        int turn = 1;

        ship.recordPlasmaRackOffensive(turn, "1");
        ship.recordPlasmaRackOffensive(turn, "2");
        assertEquals(2, ship.plasmaRacksInOffensiveMode(turn));

        String refusal = ship.plasmaRackOffensiveRefusal(turn, "3");
        assertNotNull("a third rack", refusal);
        assertTrue(refusal, refusal.contains("FP10.242"));
    }

    /**
     * Asking again about a rack that is already offensive never refuses it. FP10.242 limits how
     * many DIFFERENT racks go offensive, so a rack that has committed has already spent its
     * place and must not be told it cannot have the place it holds.
     */
    @Test
    public void aRackAlreadyInOffensiveModeIsNotRefusedItsOwnPlace() {
        Ship ship = new Ship();
        ship.setName("Gorn BC");
        int turn = 1;

        ship.recordPlasmaRackOffensive(turn, "1");
        ship.recordPlasmaRackOffensive(turn, "2");

        assertNull("rack 1 is already one of the two", ship.plasmaRackOffensiveRefusal(turn, "1"));
        assertNotNull("but rack 3 is not", ship.plasmaRackOffensiveRefusal(turn, "3"));
    }

    /** And the pair of places comes back with the turn (FP10.21 lets modes change then too). */
    @Test
    public void theOffensivePlacesReturnNextTurn() {
        Ship ship = new Ship();
        ship.setName("Gorn BC");

        ship.recordPlasmaRackOffensive(1, "1");
        ship.recordPlasmaRackOffensive(1, "2");
        assertNotNull(ship.plasmaRackOffensiveRefusal(1, "3"));

        assertEquals("nothing carried over", 0, ship.plasmaRacksInOffensiveMode(2));
        assertNull(ship.plasmaRackOffensiveRefusal(2, "3"));
    }
}
