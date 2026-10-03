package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.properties.PlasmaType;

/**
 * The plasma rack (FP10.0) — four type-D torpedoes, half a point each to activate, and two
 * firing rates chosen by a mode the rack settles the first time it fires each turn.
 *
 * <h2>What the modes are for</h2>
 * FP10.0 says what the weapon is: "intended primarily for defense against massed fighter and
 * drone attacks, but has a supplementary offensive capability." The rates follow from that —
 * FP10.211 gives offensive mode one torpedo a turn with a quarter-turn gap, FP10.212 gives
 * defensive mode one per impulse with no gap at all. Everything here is one of those two.
 */
public class PlasmaRackTest {

    private PlasmaRack rack;
    private TurnTracker clock;

    @Before
    public void setUp() {
        clock = new TurnTracker();
        rack = new PlasmaRack();
        rack.setClock(clock);
        rack.setDesignator("1");
    }

    /** Walk the clock to an absolute impulse, since the gap rules are measured in them. */
    private void advanceTo(int absoluteImpulse) {
        while (clock.getImpulse() < absoluteImpulse)
            clock.nextImpulse();
    }

    /** Pay for everything aboard, so a test about rates is not refused for activation. */
    private void activateAll() {
        rack.activate(rack.activationEnergyWanted());
    }

    // ---------------------------------------------------------------- what it holds

    /**
     * FP10.1: "holding four one-space type-D plasma torpedoes", and FP10.312 gives it "one set
     * of reloads (four torpedoes)" to begin with. Racks start full for the same reason drone
     * racks and fighter ready racks do (J4.886).
     */
    @Test
    public void itStartsWithFourTorpedoesAndOneReloadSet() {
        assertEquals(4, PlasmaRack.CAPACITY);
        assertEquals(4, rack.getTorpedoes());
        assertTrue(rack.isFull());
        assertEquals("FP10.312: one set to begin with", 1, rack.getReloadSets());
        assertEquals("FP9.21: a torpedo is one space", 4.0, rack.spacesHeld(), 0.001);
    }

    /**
     * FP10.14: "there can be no larger rack for this weapon (such as the type-B drone rack) on
     * non-bases. The ammunition (four torpedoes per rack) cannot be increased." A hard ceiling,
     * not a default a ship file could raise.
     */
    @Test
    public void itWillNotHoldAFifthTorpedo() {
        assertFalse(rack.addTorpedo());
        assertEquals(4, rack.getTorpedoes());
    }

    /**
     * FP10.16: "Plasma racks are destroyed on 'torpedo' hits. (A change from an earlier
     * edition.)" The clause that keeps it off the drone column of the DAC, and the reason this
     * is not a DroneRack.
     * <p>
     * Asserted against the string Ship's own DAC resolution matches on rather than a literal of
     * this test's choosing, because the key is the whole mechanism: a weapon whose location
     * matches no column cannot be selected by any hit, so a misspelling does not weaken the
     * rule, it makes the rack INVULNERABLE. This was "torpedo" first, which reads correctly and
     * matched nothing.
     */
    @Test
    public void itIsDestroyedOnTorpedoHitsNotDroneHits() {
        assertEquals("torp", rack.getDacHitLocaiton());
        assertNotEquals("not the drone column", "drone", rack.getDacHitLocaiton());
    }

    /**
     * And it has a PLACE in that column — FP10.16: "See (D4.3222) and Annex #7E for priority of
     * damage."
     * <p>
     * Both halves of the DAC wiring are keyed on strings, and a wrong string fails silently in
     * opposite directions: the wrong hit LOCATION makes the rack unselectable, while a type that
     * matches no {@code torpPriority} case falls to {@code Integer.MAX_VALUE} and the rack is
     * damaged after every other torpedo system aboard. Annex #7E already listed it at 69, so
     * this asserts the two agree rather than asserting the number.
     */
    @Test
    public void itHasItsAnnex7EPlaceInTheTorpedoColumn() {
        int priority = com.sfb.utilities.DacPriority.torpPriority(rack);

        assertNotEquals("type matches no torpPriority case, so it would be damaged last",
                Integer.MAX_VALUE, priority);
        assertEquals("Annex #7E", 69, priority);
    }

    // ---------------------------------------------------------------- activation (FP9.22)

    /**
     * FP9.22: "they can be activated, which requires 1/2 of an energy point (reserve or
     * allocated) per torpedo. The weapon cannot be launched until it has been activated."
     * FP10.32 confirms the rack pays the same figure as a fighter does.
     */
    @Test
    public void aTorpedoCannotLaunchUntilItIsActivated() {
        String refusal = rack.launchRefusal(PlasmaRack.RackMode.DEFENSIVE);

        assertNotNull("full rack, nothing paid for", refusal);
        assertTrue(refusal, refusal.contains("FP9.22"));
        assertNull("and nothing comes out", rack.launch(PlasmaRack.RackMode.DEFENSIVE));
        assertEquals("the torpedo stays aboard", 4, rack.getTorpedoes());
    }

    /** Half a point each, so a full rack of four wants two points. */
    @Test
    public void activatingAFullRackCostsTwoPoints() {
        assertEquals(0.5, PlasmaRack.ACTIVATION_ENERGY, 0.001);
        assertEquals(2.0, rack.activationEnergyWanted(), 0.001);

        assertEquals("spent", 2.0, rack.activate(2.0), 0.001);
        assertEquals(4, rack.getActiveTorpedoes());
        assertEquals("nothing left to pay for", 0.0, rack.activationEnergyWanted(), 0.001);
    }

    /**
     * A half point buys a whole torpedo and a quarter buys nothing. The leftover is RETURNED
     * rather than banked, because a half-activated torpedo does not exist — there is no state
     * between inactive and active for the rule to describe.
     */
    @Test
    public void partialEnergyBuysWholeTorpedoesOnly() {
        assertEquals("one torpedo's worth", 0.5, rack.activate(0.9), 0.001);
        assertEquals(1, rack.getActiveTorpedoes());

        assertEquals("not enough for another", 0.0, rack.activate(0.4), 0.001);
        assertEquals(1, rack.getActiveTorpedoes());
    }

    /** It never charges for more than it holds. */
    @Test
    public void itTakesNoEnergyForTorpedoesItDoesNotHave() {
        activateAll();
        rack.launch(PlasmaRack.RackMode.DEFENSIVE);   // three left, all active

        assertEquals("nothing inactive aboard", 0.0, rack.activationEnergyWanted(), 0.001);
        assertEquals("so it takes nothing", 0.0, rack.activate(5.0), 0.001);
    }

    /**
     * FP10.25 WEAPON STATUS: "Status II — one torpedo per rack is active. Status III — all
     * torpedoes on racks are active." Status 0 and I leave them inactive.
     */
    @Test
    public void weaponStatusDecidesHowManyStartActive() {
        rack.applyWeaponStatus(1);
        assertEquals("status I: inactive", 0, rack.getActiveTorpedoes());

        rack.applyWeaponStatus(2);
        assertEquals("status II: one per rack", 1, rack.getActiveTorpedoes());

        rack.applyWeaponStatus(3);
        assertEquals("status III: all of them", 4, rack.getActiveTorpedoes());
    }

    // ---------------------------------------------------------------- offensive mode

    /**
     * FP10.211: "The rack can fire one torpedo per turn, during any impulse of the turn."
     * One, and the second is refused.
     */
    @Test
    public void offensiveModeFiresOnceATurn() {
        activateAll();
        advanceTo(10);

        PlasmaTorpedo first = rack.launch(PlasmaRack.RackMode.OFFENSIVE);
        assertNotNull(first);
        assertEquals("a type-D", PlasmaType.D, first.getPlasmaType());
        assertEquals(3, rack.getTorpedoes());

        advanceTo(20);   // well past any gap, so only the per-turn limit can refuse
        String refusal = rack.launchRefusal(PlasmaRack.RackMode.OFFENSIVE);
        assertNotNull("a second in the same turn", refusal);
        assertTrue(refusal, refusal.contains("FP10.211"));
        assertEquals("nothing left the rack", 3, rack.getTorpedoes());
    }

    /**
     * FP10.211's other half: "but not within 1/4 turn of a torpedo fired in either mode during
     * the previous turn."
     * <p>
     * The per-turn count alone cannot catch this — a new turn clears it — so the gap is measured
     * from the firing and has to survive the boundary. {@code Weapon.cleanUp} clears the
     * inherited timestamp precisely so an ordinary weapon starts each turn free, which is why
     * this needs its own test rather than trusting the base class.
     */
    @Test
    public void theOffensiveGapSurvivesTheTurnBoundary() {
        activateAll();
        advanceTo(30);
        assertNotNull(rack.launch(PlasmaRack.RackMode.OFFENSIVE));

        advanceTo(33);           // a new turn, three impulses after the shot
        rack.cleanUp();          // the turn boundary, as Weapons.cleanUp drives it

        assertEquals("the count did reset", 0, rack.getFiredThisTurn());
        String refusal = rack.launchRefusal(PlasmaRack.RackMode.OFFENSIVE);
        assertNotNull("still inside the quarter turn", refusal);
        assertTrue(refusal, refusal.contains("quarter turn"));

        advanceTo(38);           // eight impulses after the shot
        assertNull("free again", rack.launchRefusal(PlasmaRack.RackMode.OFFENSIVE));
    }

    // ---------------------------------------------------------------- defensive mode

    /**
     * FP10.212: "there is no limit on the firing rate (other than ammunition and one shot per
     * impulse) or on how long after a previous firing the weapon can be used."
     * <p>
     * Which is the weapon's whole purpose: four torpedoes can go on four consecutive impulses
     * at a drone wave, where offensive mode would have sent one all turn.
     */
    @Test
    public void defensiveModeFiresOncePerImpulseWithNoGap() {
        activateAll();
        advanceTo(5);

        for (int i = 0; i < 4; i++) {
            assertNotNull("impulse " + clock.getImpulse(),
                    rack.launch(PlasmaRack.RackMode.DEFENSIVE));
            clock.nextImpulse();
        }

        assertTrue("all four away on consecutive impulses", rack.isEmpty());
    }

    /** One SHOT per impulse, though — the single limit defensive mode keeps. */
    @Test
    public void defensiveModeStillOnlyFiresOncePerImpulse() {
        activateAll();
        advanceTo(5);
        assertNotNull(rack.launch(PlasmaRack.RackMode.DEFENSIVE));

        String refusal = rack.launchRefusal(PlasmaRack.RackMode.DEFENSIVE);
        assertNotNull("twice in one impulse", refusal);
        assertTrue(refusal, refusal.contains("FP10.212"));
    }

    // ---------------------------------------------------------------- the mode itself

    /**
     * FP10.21: "The decision on which mode to use is made at the point of the first firing of a
     * given plasma rack during a given turn. The rack operates in the selected mode for the
     * remainder of the turn."
     * <p>
     * Nobody declares it in advance, which is why there is no setter: firing in a mode IS the
     * declaration. Having fired defensively, the rack cannot turn offensive mid-turn.
     */
    @Test
    public void theFirstShotOfTheTurnSettlesTheMode() {
        activateAll();
        advanceTo(5);
        assertEquals("undecided until it fires",
                PlasmaRack.RackMode.UNDECIDED, rack.getModeThisTurn());

        rack.launch(PlasmaRack.RackMode.DEFENSIVE);
        assertEquals(PlasmaRack.RackMode.DEFENSIVE, rack.getModeThisTurn());

        clock.nextImpulse();
        String refusal = rack.launchRefusal(PlasmaRack.RackMode.OFFENSIVE);
        assertNotNull("changing mode mid-turn", refusal);
        assertTrue(refusal, refusal.contains("FP10.21"));
    }

    /** "...but it can change modes when first fired during the next turn" (FP10.21). */
    @Test
    public void aNewTurnFindsTheRackUndecidedAgain() {
        activateAll();
        advanceTo(5);
        rack.launch(PlasmaRack.RackMode.DEFENSIVE);

        advanceTo(40);       // next turn, and past the offensive gap
        rack.cleanUp();

        assertEquals(PlasmaRack.RackMode.UNDECIDED, rack.getModeThisTurn());
        assertNull("offensive is open again", rack.launchRefusal(PlasmaRack.RackMode.OFFENSIVE));
    }

    /** A mode has to be named: there is no default the rules would recognise. */
    @Test
    public void itRefusesToFireWithoutAMode() {
        activateAll();
        String refusal = rack.launchRefusal(PlasmaRack.RackMode.UNDECIDED);

        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains("FP10.21"));
        assertNull(rack.launch(PlasmaRack.RackMode.UNDECIDED));
    }

    // ---------------------------------------------------------------- reloading and refits

    /** FP10.23: a rack being reloaded "cannot be fired in either offensive or defensive mode". */
    @Test
    public void aReloadingRackFiresInNeitherMode() {
        activateAll();
        advanceTo(5);
        rack.setReloadingThisTurn(true);

        for (PlasmaRack.RackMode mode
                : new PlasmaRack.RackMode[]{PlasmaRack.RackMode.OFFENSIVE,
                                            PlasmaRack.RackMode.DEFENSIVE}) {
            String refusal = rack.launchRefusal(mode);
            assertNotNull(mode.name(), refusal);
            assertTrue(refusal, refusal.contains("FP10.23"));
        }
    }

    /**
     * FP10.312: "Along with the Y175 drone rack refits, each plasma rack has two sets of
     * reloads; there is no extra cost for this."
     * <p>
     * Not faction-specific, unlike the Y175 refits it travels with — the rule is about the
     * weapon, so a Gorn, ISC, Romulan or Orion rack (FP10.15) all get the second set.
     */
    @Test
    public void theY175RefitGivesASecondSetOfReloads() {
        assertEquals(1, rack.getReloadSets());

        rack.applyY175Refit();

        assertEquals(2, rack.getReloadSets());
        assertEquals("four torpedoes to a set", 4, PlasmaRack.TORPEDOES_PER_RELOAD_SET);
    }

    /** Applying it twice does not keep adding sets. */
    @Test
    public void theRefitIsNotCumulative() {
        rack.applyY175Refit();
        rack.applyY175Refit();

        assertEquals(2, rack.getReloadSets());
    }

    /** An empty rack launches nothing, whatever mode is asked for. */
    @Test
    public void anEmptyRackLaunchesNothing() {
        activateAll();
        advanceTo(5);
        for (int i = 0; i < 4; i++) {
            rack.launch(PlasmaRack.RackMode.DEFENSIVE);
            clock.nextImpulse();
        }
        assertTrue(rack.isEmpty());

        String refusal = rack.launchRefusal(PlasmaRack.RackMode.DEFENSIVE);
        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains("empty"));
    }
}
