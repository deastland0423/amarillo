package com.sfb.scenario;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Aas;
import com.sfb.systemgroups.ShuttleBay;

/**
 * S3.2/S3.211: what a ship may spend on Commander's Option items, and what the percentage is
 * taken OF.
 * <p>
 * The rule computes the budget from the "Effective Adjusted Combat BPV" — "the ship, its refits,
 * and its fighters". Three places computed it from the bare hull instead, so every carrier was
 * given a budget a third too small, and no test caught it: the whole suite passed both before and
 * after the fix, because nothing had ever pinned a carrier's allowance. These are those pins.
 */
public class CoiBudgetTest {

    /** A Kzinti BC: hull 128, two admin shuttles and no fighters. */
    private Ship kzintiBc() {
        Ship s = new Ship();
        s.init(com.sfb.samples.KzintiShips.getKzinBC());
        s.setName("KHS Longsword");
        return s;
    }

    /** Puts n Kzinti AAS fighters (6 BPV each) into the ship's first bay. */
    private void addFighters(Ship ship, int n) {
        ShuttleBay bay = ship.getShuttles().getBays().get(0);
        for (int i = 0; i < n; i++) {
            Aas f = new Aas();
            f.setName("AAS " + (i + 1));
            bay.addShuttle(f, 0);
        }
    }

    // ---------------------------------------------------------------- the basis

    @Test
    public void aShipWithNoFightersIsBudgetedOnItsHullAlone() {
        Ship bc = kzintiBc();
        assertEquals("no fighters aboard", 0, CoiBudget.carriedFighterBpv(bc));
        assertEquals(128.0, CoiBudget.effectiveAdjustedCombatBpv(bc), 0.001);
        assertEquals("20% of 128 is 25.6, floored", 25.0, CoiBudget.allowanceFor(bc), 0.001);
    }

    /**
     * The bug, in the terms the rulebook uses. A carrier's hull BPV does NOT include its
     * fighters, so leaving them out of the basis shrinks the budget by their whole value.
     */
    @Test
    public void fightersCountTowardsTheBasis() {
        Ship cv = kzintiBc();
        addFighters(cv, 12);

        assertEquals("twelve AAS at 6 apiece", 72, CoiBudget.carriedFighterBpv(cv));
        assertEquals("128 hull + 72 fighters", 200.0,
                CoiBudget.effectiveAdjustedCombatBpv(cv), 0.001);
        assertEquals("20% of 200", 40.0, CoiBudget.allowanceFor(cv), 0.001);

        // What it would have been on the hull alone — the figure this replaces.
        assertEquals(25.0, Math.floor(cv.getBpv() * 20 / 100.0), 0.001);
    }

    /** Admin shuttles are not fighters and are not bought with points. */
    @Test
    public void adminShuttlesAddNothing() {
        Ship bc = kzintiBc();
        assertFalse("the sample carries admin shuttles",
                bc.getShuttles().getAllShuttles().isEmpty());
        assertEquals(0, CoiBudget.carriedFighterBpv(bc));
    }

    @Test
    public void theBudgetIsFlooredNotRounded() {
        Ship cv = kzintiBc();
        addFighters(cv, 1);   // 128 + 6 = 134; 20% = 26.8
        assertEquals(26.0, CoiBudget.allowanceFor(cv), 0.001);
    }

    @Test
    public void aScenarioMayChangeThePercentage() {
        Ship bc = kzintiBc();
        assertEquals("10% of 128", 12.0, CoiBudget.allowanceFor(bc, 10), 0.001);
        assertEquals("50% of 128", 64.0, CoiBudget.allowanceFor(bc, 50), 0.001);
        assertEquals("nothing at all", 0.0, CoiBudget.allowanceFor(bc, 0), 0.001);
    }

    @Test
    public void theStandardShareIsTwentyPercent() {
        assertEquals(20, CoiBudget.DEFAULT_PERCENT);
        Ship bc = kzintiBc();
        assertEquals(CoiBudget.allowanceFor(bc, 20), CoiBudget.allowanceFor(bc), 0.001);
    }

    @Test
    public void nullsAreToleratedRatherThanThrown() {
        assertEquals(0, CoiBudget.carriedFighterBpv(null));
        assertEquals(0.0, CoiBudget.effectiveAdjustedCombatBpv(null), 0.001);
        assertNull(CoiBudget.refusalFor(null, new CoiLoadout(), 20));
        assertNull(CoiBudget.refusalFor(kzintiBc(), null, 20));
    }

    // ---------------------------------------------------------------- the refusal

    @Test
    public void aLoadoutInsideTheBudgetIsNotRefused() {
        Ship bc = kzintiBc();                 // allowance 25
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraTBombs = 6;              // 24
        assertEquals(24.0, loadout.totalCost(), 0.001);
        assertNull(CoiBudget.refusalFor(bc, loadout, 20));
    }

    @Test
    public void spendingExactlyTheBudgetIsAllowed() {
        Ship bc = kzintiBc();                 // allowance 25
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraTBombs = 6;              // 24
        loadout.extraBoardingParties = 2;     // 1 → 25 exactly
        assertEquals(25.0, loadout.totalCost(), 0.001);
        assertNull("the limit is inclusive", CoiBudget.refusalFor(bc, loadout, 20));
    }

    @Test
    public void goingOverIsRefusedAndTheMessageSaysByHowMuch() {
        Ship bc = kzintiBc();                 // allowance 25
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraTBombs = 7;              // 28

        String refusal = CoiBudget.refusalFor(bc, loadout, 20);
        assertNotNull(refusal);
        assertTrue("names the ship: " + refusal, refusal.contains("KHS Longsword"));
        assertTrue("says what it costs: " + refusal, refusal.contains("28"));
        assertTrue("says what was allowed: " + refusal, refusal.contains("25"));
        assertTrue("cites the rule: " + refusal, refusal.contains("S3.2"));
    }

    /**
     * The point of counting fighters: a loadout a carrier can afford would have been refused on
     * the hull-only basis. Same ship, same loadout, different answer.
     */
    @Test
    public void aCarrierCanAffordWhatTheHullAloneCouldNot() {
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraTBombs = 7;              // 28

        Ship bare = kzintiBc();
        assertNotNull("25 of budget cannot buy 28", CoiBudget.refusalFor(bare, loadout, 20));

        Ship cv = kzintiBc();
        addFighters(cv, 12);                  // basis 200, allowance 40
        assertNull("40 of budget can", CoiBudget.refusalFor(cv, loadout, 20));
    }

    @Test
    public void everyCostedItemCountsTowardsTheRefusal() {
        Ship bc = kzintiBc();                 // allowance 25
        CoiLoadout loadout = new CoiLoadout();
        loadout.extraBoardingParties = 10;    // 5.0
        loadout.convertBpToCommando = 2;      // 1.0
        loadout.extraCommandoSquads = 2;      // 2.0
        loadout.extraDeckCrews = 4;           // 2.0
        loadout.extraTBombs = 4;              // 16.0
        assertEquals(26.0, loadout.totalCost(), 0.001);
        assertNotNull("26 over an allowance of 25", CoiBudget.refusalFor(bc, loadout, 20));
    }

    /** An empty loadout costs nothing and is always affordable, budget or no budget. */
    @Test
    public void anEmptyLoadoutIsAlwaysAffordable() {
        Ship bc = kzintiBc();
        assertEquals(0.0, new CoiLoadout().totalCost(), 0.001);
        assertNull(CoiBudget.refusalFor(bc, new CoiLoadout(), 0));
    }

    // ---------------------------------------------------------------- the old callers agree

    /**
     * FleetValidator.coiAllowance and the budget class must not drift apart again — that is what
     * went wrong the first time, with the fighter sum sitting two methods above the allowance
     * that ignored it.
     */
    @Test
    public void fleetValidatorAgreesWithTheBudgetClass() {
        Ship cv = kzintiBc();
        addFighters(cv, 12);
        assertEquals(CoiBudget.allowanceFor(cv, 20), FleetValidator.coiAllowance(cv), 0.001);
        assertEquals(CoiBudget.carriedFighterBpv(cv), FleetValidator.carriedFighterBpv(cv));
    }
}
