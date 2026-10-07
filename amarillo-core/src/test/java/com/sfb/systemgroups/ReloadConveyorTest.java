package com.sfb.systemgroups;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * FD2.4421's conveyor, FD2.442's overfull pile, and FD2.423's destruction.
 *
 * <h2>The conveyor</h2>
 * <b>FD2.4421</b>: "If a one-space drone from the reload storage is loaded onto a drone rack, a
 * one-space drone from cargo storage is automatically moved into the opening created in reload
 * storage." <b>FD2.4423</b>: "This applies to every rack on the ship."
 *
 * <p>So a ship with cargo boxes keeps reloading long after its one set of reloads would have run
 * out — which is the whole reason the drone cruisers carry 200 spaces in cargo, and it had no
 * implementation at all: {@code CargoDroneStore.draw()} existed with nothing calling it.
 */
public class ReloadConveyorTest {

    private Ship ship;

    /** A D7 with cargo boxes full of spare drones, which the sample hull does not have. */
    @Before
    public void setUp() {
        Map<String, Object> spec = KlingonShips.getD7();
        spec.put("cargo", 4);                        // four cargo boxes, as the drone cruisers carry
        spec.put("cargodronespacesperbox", 50);      // FD2.445's rate
        ship = new Ship();
        ship.init(spec);
        ship.setName("IKS Conveyor");
        assertNotNull("fixture: the hull must have cargo boxes to fill",
                ship.getCargoDroneStore());
        assertTrue("fixture: and they start full (FD2.445)",
                ship.getCargoDroneStore().spacesHeld() > 0);
    }

    private List<DroneRack> racks() {
        List<DroneRack> out = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                out.add(rack);
        return out;
    }

    private DroneType stockedType() {
        return ship.reloadStockpile().held().get(0).getDroneType();
    }

    // ------------------------------------------------------------------ FD2.4421, the conveyor

    /**
     * The rule itself: a drone goes onto a rack, a cargo drone comes up behind it, and the pile is
     * full again.
     */
    @Test
    public void aRackReloadPullsACargoDroneUp() {
        ReloadStockpile pile = ship.reloadStockpile();
        double capacity = pile.capacitySpaces();
        assertEquals("fixture: the pile starts full", capacity, pile.spacesHeld(), 0.001);
        int cargoBefore = ship.getCargoDroneStore().spacesHeld();

        List<Drone> taken = pile.takeForRack(stockedType(), 1);

        assertEquals(1, taken.size());
        assertEquals("the opening was filled (FD2.4421)", capacity, pile.spacesHeld(), 0.001);
        assertEquals("and it was filled from cargo",
                cargoBefore - taken.get(0).getRackSize(),
                ship.getCargoDroneStore().spacesHeld(), 0.001);
    }

    /**
     * It is a RACK reload that moves cargo, not any draw on the pile. FD2.4421 says "loaded onto a
     * drone rack"; a scatter pack being filled (FD7.22) and the Commander's Option draw are not
     * that, and routing all three through one method would top a ship's reloads up every time it
     * loaded a pack.
     */
    @Test
    public void fillingAScatterPackDoesNotPullCargoUp() {
        ReloadStockpile pile = ship.reloadStockpile();
        int cargoBefore = ship.getCargoDroneStore().spacesHeld();
        double heldBefore = pile.spacesHeld();

        List<Drone> taken = pile.take(stockedType(), 2);     // the pack-loading path

        assertEquals(2, taken.size());
        assertEquals("nothing came up from cargo",
                cargoBefore, ship.getCargoDroneStore().spacesHeld());
        assertTrue("so the pile is genuinely down", pile.spacesHeld() < heldBefore);
    }

    /**
     * Like for like, which is one of the choices FD2.4422 offers: "he can choose whether or not the
     * drone spaces coming out of the cargo boxes are a single drone (type-IV or type-IIIXX) or two
     * drones (two type-Is...)". A two-space drone leaving draws two spaces and a two-space drone
     * comes up. Letting the player pick the other option is FD2.4422 proper and is not built.
     */
    @Test
    public void aTwoSpaceDroneDrawsTwoSpacesOfCargo() {
        ReloadStockpile pile = ship.reloadStockpile();
        // Put a two-space drone in the pile to draw on, in place of a one-space one.
        List<Drone> singles = pile.take(stockedType(), 2);
        assertEquals("fixture: two one-space drones out to make room", 2, singles.size());
        assertEquals("fixture: the stocked drone is one space",
                1.0, singles.get(0).getRackSize(), 0.001);
        Drone big = new Drone(DroneType.TypeIV);
        assertEquals("fixture: a type-IV is two spaces", 2.0, big.getRackSize(), 0.001);
        assertTrue("fixture: and the two-space drone goes in", pile.put(big));
        int cargoBefore = ship.getCargoDroneStore().spacesHeld();
        double heldBefore = pile.spacesHeld();

        List<Drone> taken = pile.takeForRack(DroneType.TypeIV, 1);

        assertEquals(1, taken.size());
        assertEquals("two spaces came out of the boxes",
                cargoBefore - 2, ship.getCargoDroneStore().spacesHeld());
        assertEquals("and two spaces went back into the pile",
                heldBefore, pile.spacesHeld(), 0.001);
        assertEquals("as one two-space drone", 2.0,
                pile.held().stream().filter(d -> d.getDroneType() == DroneType.TypeIV)
                        .findFirst().orElseThrow().getRackSize(), 0.001);
    }

    /** Empty boxes move nothing, and nothing breaks. */
    @Test
    public void anEmptyHoldMovesNothing() {
        ReloadStockpile pile = ship.reloadStockpile();
        ship.getCargoDroneStore().draw(ship.getCargoDroneStore().spacesHeld());
        assertTrue(ship.getCargoDroneStore().isEmpty());
        double heldBefore = pile.spacesHeld();

        assertEquals(1, pile.takeForRack(stockedType(), 1).size());

        assertEquals("the pile is down by the drone and nothing replaced it",
                heldBefore - 1, pile.spacesHeld(), 0.001);
    }

    /** And a hull with no cargo boxes at all just runs its pile down. */
    @Test
    public void aShipWithNoCargoBoxesJustEmpties() {
        Ship bare = new Ship();
        bare.init(KlingonShips.getD7());
        assertNull("fixture: no cargo drone store", bare.getCargoDroneStore());

        ReloadStockpile pile = bare.reloadStockpile();
        double heldBefore = pile.spacesHeld();
        assertEquals(1, pile.takeForRack(pile.held().get(0).getDroneType(), 1).size());

        assertEquals(heldBefore - 1, pile.spacesHeld(), 0.001);
    }

    // ------------------------------------------------- FD2.442: a pile above its own capacity

    /**
     * The owner's worked example, 2026-10-06, and the reason the conveyor is bounded by capacity
     * rather than by the opening.
     *
     * <p>FD2.442: "Extra drones purchased under (S3.2) can be added to this type of storage <b>in
     * excess of its capacity</b> (but do not increase its capacity)." So a ship with eight spaces of
     * capacity that has crammed in three more holds eleven — and nothing comes up from cargo until
     * at least four spaces have gone out to the racks and the pile is under eight again.
     *
     * <p>Stepped one drone at a time, because the claim is about WHEN the conveyor starts, not that
     * it eventually does. The purchasing itself has no entry point yet (FD10.647 is deferred), so
     * the overfull pile is built the way the racks author reloads.
     */
    @Test
    public void anOverfullPileStallsTheConveyorUntilItIsUnderCapacity() {
        ReloadStockpile pile = ship.reloadStockpile();
        double capacity = pile.capacitySpaces();
        DroneType type = stockedType();
        pile.held();                                   // settle the pile before adding to it

        // Three spaces above capacity, as three purchased one-space drones would be.
        for (int i = 0; i < 3; i++)
            racks().get(0).getReloads().get(0).add(new Drone(type));
        assertEquals("fixture: overfull by three", capacity + 3, pile.spacesHeld(), 0.001);

        int cargoBefore = ship.getCargoDroneStore().spacesHeld();
        for (int drawn = 1; drawn <= 3; drawn++) {
            assertEquals(1, pile.takeForRack(type, 1).size());
            assertEquals("still at or above capacity after " + drawn + ", so cargo has not moved",
                    cargoBefore, ship.getCargoDroneStore().spacesHeld());
            assertEquals(capacity + 3 - drawn, pile.spacesHeld(), 0.001);
        }

        // The fourth takes it under capacity, and now the boxes feed it.
        assertEquals(1, pile.takeForRack(type, 1).size());
        assertEquals("the fourth space is the one cargo replaces",
                cargoBefore - 1, ship.getCargoDroneStore().spacesHeld());
        assertEquals("back to exactly capacity", capacity, pile.spacesHeld(), 0.001);
    }

    /**
     * A reload refused for being over FD2.421's two-space budget must cost nothing — and with the
     * conveyor in play, putting the drone back means sending its cargo replacement down again.
     * <p>
     * Without that reversal the pile is already full when the drone comes back, {@code put} refuses
     * it over capacity, and the drone is destroyed to enforce a limit. That is the bug the
     * take-then-offer ordering was rewritten to avoid once already.
     */
    @Test
    public void aRefusedReloadPutsBackBothTheDroneAndTheCargoSpace() {
        ReloadStockpile pile = ship.reloadStockpile();
        double capacity = pile.capacitySpaces();
        int cargoBefore = ship.getCargoDroneStore().spacesHeld();

        Drone drawn = pile.takeForRack(stockedType(), 1).get(0);
        assertEquals("the conveyor has already fired", capacity, pile.spacesHeld(), 0.001);
        assertEquals(cargoBefore - 1, ship.getCargoDroneStore().spacesHeld());

        assertTrue("the put-back is accepted", pile.putBackFromRack(drawn));

        assertEquals("nothing destroyed to enforce a limit", capacity, pile.spacesHeld(), 0.001);
        assertEquals("and the cargo space went back in the box",
                cargoBefore, ship.getCargoDroneStore().spacesHeld());
    }

    // --------------------------------------------- FD2.423: the last Excess Damage box takes it

    /**
     * FD2.423: "Drone and ADD reloads (other than those in cargo boxes) are stored in various
     * locations around the ship and are considered destroyed with the last Excess Damage box."
     *
     * <p>Three claims in one rule, all asserted: the drones go, the <b>ADD</b> reserve goes with them
     * (the rule names it), and the <b>cargo boxes are spared</b> by name.
     */
    @Test
    public void theLastExcessDamageBoxTakesTheReloadsAndTheAddsButNotCargo() {
        DroneRack typeG = new DroneRack(DroneRack.DroneRackType.TYPE_G);
        typeG.setDesignator("Rack 9");
        ship.getWeapons().addWeapon(typeG);
        typeG.setAddReloads(4);

        ReloadStockpile pile = ship.reloadStockpile();
        assertTrue("fixture: there are reloads to lose", pile.spacesHeld() > 0);
        int cargoBefore = ship.getCargoDroneStore().spacesHeld();

        double lost = pile.destroyWithLastExcessDamageBox();

        assertTrue("it reports what was lost, for the log", lost > 0);
        assertTrue("the drones are gone", pile.held().isEmpty());
        assertEquals("the ADD reserve went with them (FD2.423 names it)", 0, typeG.getAddReloads());
        assertEquals("the cargo boxes are spared by name",
                cargoBefore, ship.getCargoDroneStore().spacesHeld());
        assertTrue(pile.isDestroyed());
    }

    /**
     * And cargo cannot quietly regrow it. FD2.442 has this storage "automatically refilled from the
     * drones in cargo boxes", so without stopping the conveyor a ship with full boxes would rebuild
     * its reloads out of the wreckage. FD2.423's next sentence is about loading "the <b>remaining</b>
     * reload drones" — after this there are none.
     */
    @Test
    public void destroyedStorageIsNotRefilledFromCargo() {
        ReloadStockpile pile = ship.reloadStockpile();
        DroneType type = stockedType();
        pile.destroyWithLastExcessDamageBox();
        int cargoBefore = ship.getCargoDroneStore().spacesHeld();

        assertTrue("nothing to take", pile.takeForRack(type, 2).isEmpty());
        assertTrue("and nothing to put", pile.held().isEmpty());
        assertFalse("a put-back has nowhere to go either", pile.put(new Drone(type)));
        assertEquals("the boxes are untouched", cargoBefore,
                ship.getCargoDroneStore().spacesHeld());
    }

    /**
     * The trigger, at the layer that fires it: internal damage deep enough to burn through every
     * Excess Damage box.
     * <p>
     * Tested through {@code applyInternalDamage} rather than by calling the stockpile, because the
     * question is whether the hook is reached at all — and the ship must still be ALIVE when it is.
     * FD2.423 fires on the last box; the box that destroys the ship is the one after it, so there is
     * a real window in which a ship fights on with full racks and nothing behind them.
     */
    @Test
    public void internalDamageThroughTheLastExcessBoxDestroysTheReloads() {
        ReloadStockpile pile = ship.reloadStockpile();
        assertTrue("fixture: reloads aboard", pile.spacesHeld() > 0);
        int boxes = ship.getSpecialFunctions().getOriginalExcessDamage();
        assertTrue("fixture: the hull has excess damage boxes", boxes > 0);

        // Enough to strip the ship and spend every excess box, but the ship survives the last one.
        for (int volley = 0; volley < 40 && ship.getSpecialFunctions().getExcessDamage() > 0; volley++)
            ship.applyInternalDamage(25);

        assertEquals("fixture: every excess damage box is gone",
                0, ship.getSpecialFunctions().getExcessDamage());
        assertTrue("the reload storage went with the last of them (FD2.423)", pile.isDestroyed());
        assertTrue(pile.held().isEmpty());
    }
}
