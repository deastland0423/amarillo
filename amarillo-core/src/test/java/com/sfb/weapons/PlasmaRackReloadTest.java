package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.objects.Ship;
import com.sfb.systemgroups.PlasmaReloadStore;

/**
 * FP10.3: reloading a plasma rack, from a pool the whole ship shares.
 *
 * <h2>The two rules this rests on</h2>
 * <ul>
 *   <li><b>FP10.31</b>: "A given rack can use reloads nominally assigned to another rack." A
 *       rack's reload sets say what the SHIP carries, not what that rack owns — which is the
 *       point of {@link PlasmaReloadStore}, and the thing a per-rack counter got wrong.</li>
 *   <li><b>FD2.421</b>, reached through FP10.3's "essentially the same as ... drone racks": up
 *       to two spaces a turn, plotted in advance, the rack out of action for the whole turn,
 *       and "the reload rate cannot be increased". A type-D is one space (FP9.21), so a rack
 *       emptied of all four needs two turns.</li>
 * </ul>
 *
 * <h2>What this had to be written against</h2>
 * Before it, nothing consumed {@code reloadSets} and nothing refilled {@code torpedoes}: a rack
 * fired four torpedoes and was spent for the rest of the game, while the UI reported reload
 * sets that could never be used.
 */
public class PlasmaRackReloadTest {

    private Ship ship;
    private TurnTracker clock;
    private PlasmaRack rackOne;
    private PlasmaRack rackTwo;

    @Before
    public void setUp() {
        clock = new TurnTracker();
        ship = new Ship();
        java.util.Map<String, Object> values =
                new java.util.HashMap<>(com.sfb.samples.FederationShips.getFedCa());
        // A fighter drone store on the hull, so Ship ties the two holds together itself (FP10.31)
        // rather than a test reaching in — plasmaReloadStore() re-ties on every call, so a
        // hand-attached store would be overwritten by the next one, as it was when this was
        // first written.
        values.put("dronestoragespaces", 20);
        ship.init(values);
        ship.setName("Testbed");
        rackOne = mount("PL-D 1");
        rackTwo = mount("PL-D 2");
    }

    private PlasmaRack mount(String designator) {
        PlasmaRack rack = new PlasmaRack();
        rack.setClock(clock);
        rack.setDesignator(designator);
        rack.setOwningShip(ship);
        ship.getWeapons().addWeapon(rack);
        return rack;
    }

    /**
     * Fire {@code count} torpedoes off in defensive mode, which is the only way to empty a rack
     * quickly — FP10.212 allows one per impulse, so the clock has to move between them.
     */
    private void fireOff(PlasmaRack rack, int count) {
        rack.activate(rack.activationEnergyWanted());
        for (int i = 0; i < count; i++) {
            assertNotNull("fixture: torpedo " + (i + 1) + " should launch",
                    rack.launch(PlasmaRack.RackMode.DEFENSIVE));
            clock.nextImpulse();
        }
    }

    /**
     * Fire {@code count} torpedoes off and then roll into a fresh turn.
     *
     * <p>The roll is not tidiness: FD2.421 lets a rack reload only "provided that the rack is
     * not fired during the entire turn", so a rack that has just emptied itself cannot plot a
     * reload until the next turn. Writing these fixtures without it is what proved the rule was
     * being enforced — five tests failed because they tried to fire and reload on one turn.
     */
    private void spend(PlasmaRack rack, int count) {
        fireOff(rack, count);
        endTurn();
        advanceToNextTurn();
    }

    /** Walk the clock into the next turn; a turn is 32 impulses and the tracker counts them. */
    private void advanceToNextTurn() {
        int turn = clock.getTurn();
        while (clock.getTurn() == turn)
            clock.nextImpulse();
    }

    /** End the turn for both racks, which is where a plotted reload arrives (8C). */
    private void endTurn() {
        rackOne.cleanUp();
        rackTwo.cleanUp();
    }

    /**
     * The ship's fighter supply, with the two holds tied together as Ship does it. Asking for
     * the rack hold is what ties them, so this goes through that accessor on purpose.
     */
    private com.sfb.systemgroups.DroneStore fighterSupply() {
        ship.plasmaReloadStore();
        com.sfb.systemgroups.DroneStore store = ship.getShuttles().getDroneStore();
        assertNotNull("fixture: the hull declares drone storage spaces", store);
        return store;
    }

    /** Plasma racks count against the ship's pool; nothing else on this hull does. */
    private int poolCapacity() {
        return ship.plasmaReloadStore().capacity();
    }

    // ---------------------------------------------------------------- the pool

    /** FP10.31: four per set, summed across every rack — one supply, not two private ones. */
    @Test
    public void thePoolIsTheSumOfEveryRacksSets() {
        assertEquals(2 * PlasmaRack.TORPEDOES_PER_RELOAD_SET, poolCapacity());
        assertEquals(poolCapacity(), ship.plasmaReloadStore().held());
    }

    /**
     * THE RULE THIS CLASS EXISTS FOR. Rack one empties itself and reloads twice, drawing four
     * torpedoes — more than the single set "nominally assigned" to it — because the supply
     * belongs to the ship. A per-rack counter would have cut it off after the first set.
     */
    @Test
    public void oneRackMayDrawOnAnotherRacksNominalSet() {
        spend(rackOne, 4);

        for (int turn = 0; turn < 2; turn++) {
            assertTrue("turn " + turn, rackOne.stagePendingReload() > 0);
            endTurn();
        }

        assertEquals("four torpedoes drawn, two sets' worth", 4, rackOne.getTorpedoes());
        assertEquals(poolCapacity() - 4, ship.plasmaReloadStore().held());
        assertEquals("rack two never fired and still holds its own four",
                PlasmaRack.CAPACITY, rackTwo.getTorpedoes());
    }

    /** The Y175 refit raises the supply, and capacity is derived so it notices. */
    @Test
    public void theY175RefitRaisesThePool() {
        int before = poolCapacity();

        rackOne.applyY175Refit();
        rackTwo.applyY175Refit();

        assertEquals("two sets each instead of one", before * 2, poolCapacity());
    }

    /** And a destroyed rack takes its share of the hold with it. */
    @Test
    public void aDestroyedRackLowersTheSupply() {
        int before = poolCapacity();

        rackTwo.damage();

        assertEquals(before - PlasmaRack.TORPEDOES_PER_RELOAD_SET, poolCapacity());
    }

    // ---------------------------------------------------------------- the rate

    /** FD2.421: two a turn, however empty the rack is and however full the hold. */
    @Test
    public void onlyTwoTorpedoesArriveInATurn() {
        spend(rackOne, 4);

        rackOne.stagePendingReload();
        endTurn();

        assertEquals(PlasmaRack.TORPEDOES_PER_TURN_RELOADED, rackOne.getTorpedoes());
    }

    /** A rack with one space free takes one, not two — the cap is a maximum, not a quota. */
    @Test
    public void aRackOnlyTakesWhatItHasRoomFor() {
        spend(rackOne, 1);

        rackOne.stagePendingReload();
        endTurn();

        assertEquals(PlasmaRack.CAPACITY, rackOne.getTorpedoes());
        assertEquals("and only one torpedo left the hold",
                poolCapacity() - 1, ship.plasmaReloadStore().held());
    }

    // ---------------------------------------------------------------- when it may be plotted

    /** FP10.23: a reloading rack cannot fire in either mode. */
    @Test
    public void aReloadingRackCannotFire() {
        spend(rackOne, 2);

        assertTrue("fixture: a fresh turn, so it may plot", rackOne.stagePendingReload() > 0);
        String refusal = rackOne.launchRefusal(PlasmaRack.RackMode.OFFENSIVE);

        assertNotNull("a rack out of action for the turn may not launch", refusal);
        assertTrue(refusal, refusal.contains("FP10.23"));
    }

    /** FD2.421: plotted in advance, so a rack that has already fired has missed its turn. */
    @Test
    public void aRackThatHasFiredCannotPlotAReload() {
        fireOff(rackOne, 1);

        assertNotNull(rackOne.reloadRefusal());
        assertEquals(0, rackOne.stagePendingReload());
        assertFalse(rackOne.isReloadingThisTurn());
    }

    /** A full rack has nothing to do. */
    @Test
    public void aFullRackIsRefused() {
        assertNotNull(rackOne.reloadRefusal());
        assertEquals(0, rackOne.stagePendingReload());
    }

    /** An empty hold refuses too, rather than quietly loading nothing. */
    @Test
    public void anEmptyHoldIsRefused() {
        spend(rackOne, 4);
        PlasmaReloadStore store = ship.plasmaReloadStore();
        store.take(store.held());

        assertNotNull(rackOne.reloadRefusal());
        assertEquals(0, rackOne.stagePendingReload());
    }

    // ---------------------------------------------------------------- what arrives

    /**
     * FP10.32 and FP9.22: a reloaded torpedo arrives INACTIVE and costs half a point to
     * activate. A reload refills the ammunition, never the readiness.
     */
    @Test
    public void reloadedTorpedoesArriveInactive() {
        spend(rackOne, 4);
        assertEquals("fixture: everything aboard was spent", 0, rackOne.getActiveTorpedoes());

        rackOne.stagePendingReload();
        endTurn();

        assertEquals("two more torpedoes", 2, rackOne.getTorpedoes());
        assertEquals("and none of them armed", 0, rackOne.getActiveTorpedoes());
    }

    /** A rack destroyed during the turn loads nothing — and spends nothing from the hold. */
    @Test
    public void aDestroyedRackLoadsNothingAndSpendsNothing() {
        spend(rackOne, 4);
        rackOne.stagePendingReload();

        rackOne.damage();
        int heldBefore = ship.plasmaReloadStore().held();
        endTurn();

        assertEquals("the torpedoes stayed in the hold", heldBefore,
                ship.plasmaReloadStore().held());
        assertEquals(0, rackOne.getTorpedoes());
    }

    /**
     * FP10.31's hold survives a save, which it must: a drawn-down pool that came back full
     * would hand a ship free torpedoes at every reload.
     *
     * <p>Only what has been DRAWN is written, and capacity rebuilds from the racks on the far
     * side. The rack destroyed before the save is the proof: its share of the supply is still
     * missing afterwards, where a saved capacity would have restored it.
     */
    @Test
    public void thePlasmaHoldSurvivesASave() {
        ship.plasmaReloadStore().take(3);
        rackTwo.damage();
        int heldBefore = ship.plasmaReloadStore().held();
        assertEquals("fixture: one rack's worth, less the three drawn", 1, heldBefore);

        Ship restored = new Ship();
        restored.init(new java.util.HashMap<>(com.sfb.samples.FederationShips.getFedCa()));
        PlasmaRack one = new PlasmaRack();
        one.setDesignator("PL-D 1");
        one.setOwningShip(restored);
        restored.getWeapons().addWeapon(one);
        PlasmaRack two = new PlasmaRack();
        two.setDesignator("PL-D 2");
        two.setOwningShip(restored);
        restored.getWeapons().addWeapon(two);
        two.damage();
        restored.applyState(ship.exportState());

        assertEquals("the torpedoes already spent are still spent",
                heldBefore, restored.plasmaReloadStore().held());
    }

    // ---------------------------------------------------------------- FP10.31 interchangeability

    /**
     * FP10.31: "Plasma-Ds stored for use by fighters are interchangeable with those stored for
     * use by plasma racks." Owner's reading, 2026-10-20: each draws on its OWN store first and
     * reaches for the other only when its own is dry. So this is a fallback, not a merged pool,
     * and nobody is asked to choose.
     */
    @Test
    public void aRackFallsBackOnTheFightersSupplyOnlyWhenItsOwnHoldIsDry() {
        com.sfb.systemgroups.DroneStore fighters = fighterSupply();
        fighters.stockPlasmaDs(4);

        // Own hold first: empty it, and the fighters' supply is untouched.
        PlasmaReloadStore hold = ship.plasmaReloadStore();
        int own = hold.held();
        assertEquals("fixture: the racks have their own supply", own, hold.take(own));
        assertEquals("the fighters were not touched", 4, fighters.plasmaDCount());

        // Now it is dry, so the next draw reaches across.
        assertEquals(2, hold.take(2));
        assertEquals("two came out of the fighters' supply", 2, fighters.plasmaDCount());
    }

    /** And the other way: a fighter's supply falls back on the racks' hold. */
    @Test
    public void theFightersSupplyFallsBackOnTheRackHold() {
        com.sfb.systemgroups.DroneStore fighters = fighterSupply();
        assertEquals("fixture: the fighters have nothing of their own", 0,
                fighters.plasmaDCount());

        int rackHeldBefore = ship.plasmaReloadStore().held();
        assertTrue("a torpedo comes from the racks' hold",
                fighters.takePlasma(com.sfb.properties.PlasmaType.D));

        assertEquals(rackHeldBefore - 1, ship.plasmaReloadStore().held());
    }

    /**
     * The fallback must not bounce. Both stores implement the supply interface and hold each
     * other, so a draw neither can serve has to end rather than ricochet — which is why a
     * fallback is only ever asked through the own-stock-only method.
     */
    @Test
    public void twoEmptyStoresDoNotBounceTheDrawBetweenThem() {
        com.sfb.systemgroups.DroneStore fighters = fighterSupply();
        PlasmaReloadStore hold = ship.plasmaReloadStore();
        hold.take(hold.held());                         // both now empty

        assertEquals("nothing to be had, and it says so rather than hanging", 0, hold.take(2));
        assertFalse(fighters.takePlasma(com.sfb.properties.PlasmaType.D));
    }

    /** A type-K has no rack hold to borrow from: the rack holds type-Ds only (FP10.11). */
    @Test
    public void onlyTheTypeDFallsBack() {
        com.sfb.systemgroups.DroneStore fighters = fighterSupply();

        assertFalse("a type-K cannot come out of a plasma rack's hold",
                fighters.takePlasma(com.sfb.properties.PlasmaType.K));
        assertEquals("and the hold is untouched", ship.plasmaReloadStore().capacity(),
                ship.plasmaReloadStore().held());
    }

    /** Two racks plotting against a hold that can only fill one: the hold is never overdrawn. */
    @Test
    public void thePoolIsNeverOverdrawn() {
        fireOff(rackOne, 4);
        fireOff(rackTwo, 4);
        endTurn();
        advanceToNextTurn();
        PlasmaReloadStore store = ship.plasmaReloadStore();
        store.take(store.held() - 1);           // leave exactly one torpedo

        rackOne.stagePendingReload();
        rackTwo.stagePendingReload();
        endTurn();

        assertEquals("one torpedo between them", 1,
                rackOne.getTorpedoes() + rackTwo.getTorpedoes());
        assertEquals(0, store.held());
    }
}
