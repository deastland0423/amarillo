package com.sfb;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * A drone rack comes back holding what it was holding.
 *
 * <h2>The gap this closes, and why it was the one that mattered</h2>
 * None of the rack's contents used to be saved, so a reloaded battle rebuilt every rack from the
 * hull — FULL, with every reload set restored. A player who had emptied a rack over a turn simply
 * got the drones back, which is the most directly exploitable thing a save can get wrong: not a
 * crash, not a visible error, just free ammunition.
 *
 * <p>It surfaced in a playtest as an inconsistency rather than as free drones: a rack that looked
 * reloaded while still showing cooldown. That is exactly what half-saved state looks like —
 * {@code lastImpulseFired} is saved by {@code Weapon}, so the cooldown crossed the save while the
 * ammunition did not.
 */
public class DroneRackSaveTest {

    private Ship ship;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        ship = new Ship();
        ship.init(KlingonShips.getD7());
        ship.setName("IKV Blade");
    }

    private DroneRack rack() {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r)
                return r;
        throw new AssertionError("fixture: the D7 should carry a drone rack");
    }

    /** Export this ship, build a fresh one from the same hull, import. */
    private Ship roundTrip() {
        Map<String, Object> state = ship.exportState();
        Ship restored = new Ship();
        restored.init(KlingonShips.getD7());
        restored.applyState(state);
        return restored;
    }

    private static DroneRack rackOf(Ship s) {
        for (Weapon w : s.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r)
                return r;
        throw new AssertionError("no rack on the restored ship");
    }

    // ---------------------------------------------------------------- the free ammunition

    /** THE TEST THIS EXISTS FOR: drones spent in a battle must stay spent. */
    @Test
    public void dronesFiredInTheBattleDoNotComeBack() {
        DroneRack before = rack();
        int full = before.getAmmo().size();
        assertTrue("fixture: a rack should start loaded", full > 0);

        before.getAmmo().remove(0);
        before.getAmmo().remove(0);
        int left = before.getAmmo().size();
        assertEquals(full - 2, left);

        DroneRack after = rackOf(roundTrip());

        assertEquals("a reloaded battle must not refill the rack", left, after.getAmmo().size());
        assertNotEquals("and certainly must not restore it to full", full, after.getAmmo().size());
    }

    /** An EMPTY rack is the sharpest case: it must not come back loaded. */
    @Test
    public void anEmptiedRackComesBackEmpty() {
        rack().getAmmo().clear();

        assertTrue("an empty rack that reloads itself is free ammunition",
                rackOf(roundTrip()).getAmmo().isEmpty());
    }

    /** Reload sets are ammunition too — spending them must stick (FD2.442). */
    @Test
    public void spentReloadSetsStaySpent() {
        DroneRack before = rack();
        int sets = before.getNumberOfReloads();
        assertTrue("fixture: a type-C rack has reloads", sets > 0);

        before.getReloads().remove(0);

        DroneRack after = rackOf(roundTrip());
        assertEquals(sets - 1, after.getReloads().size());
        assertEquals("the mirrored count must agree with the list",
                after.getReloads().size(), after.getNumberOfReloads());
    }

    // ---------------------------------------------------------------- what is in the rack

    /** The rack keeps WHICH drones it held, not merely how many. */
    @Test
    public void theRackKeepsTheTypesItWasHolding() {
        DroneRack before = rack();
        com.sfb.objects.DroneType expected = before.getAmmo().get(0).getDroneType();

        DroneRack after = rackOf(roundTrip());

        assertEquals("a type-IV must not come back as something else",
                expected, after.getAmmo().get(0).getDroneType());
    }

    // ---------------------------------------------------------------- per-turn state

    /**
     * FD3.71: a type-G rack's mode is settled by its first shot of the turn and holds for the
     * rest of it. Restoring UNDECIDED would hand the player a choice they had already spent.
     */
    @Test
    public void aSettledRackModeIsNotHandedBack() {
        // Settled the way the game settles it — by firing — rather than through a setter
        // written for the test, so this exercises the real path FD3.71 describes.
        rack().recordLaunch();
        assertEquals("fixture", DroneRack.RackMode.DRONE, rack().getModeThisTurn());

        assertEquals("a settled mode must not come back UNDECIDED",
                DroneRack.RackMode.DRONE, rackOf(roundTrip()).getModeThisTurn());
    }

    /** A rack being reloaded cannot fire, and a save must not clear that. */
    @Test
    public void aRackReloadingThisTurnStaysBlocked() {
        rack().stagePendingReload(new java.util.ArrayList<>(), 0);
        assertTrue("fixture", rack().isReloadingThisTurn());

        assertTrue("a reloading rack that comes back ready is a free shot",
                rackOf(roundTrip()).isReloadingThisTurn());
    }

    // ---------------------------------------------------------------- what is rebuilt, not saved

    /**
     * The rack's TYPE and capacity are what the hull mounts — a property of the ship and of the
     * year's refits (R1.7) — so they come from the data, not the save. Pinned because saving them
     * as well would let a save and the ship data disagree about what the rack is.
     */
    @Test
    public void theRackTypeComesFromTheHullAndNotTheSave() {
        DroneRack before = rack();
        DroneRack after = rackOf(roundTrip());

        assertEquals(before.getRackType(), after.getRackType());
        assertFalse("the type is not written into the save",
                before.exportState().containsKey("rackType"));
    }
}
