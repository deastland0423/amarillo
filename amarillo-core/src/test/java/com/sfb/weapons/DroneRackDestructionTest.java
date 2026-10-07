package com.sfb.weapons;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * What a destroyed drone rack takes with it, and what it does not.
 *
 * <h2>Two piles of drones, inches apart, with opposite rules</h2>
 * <ul>
 *   <li><b>FD2.441</b>: "any drones on a rack are <b>destroyed</b> when the rack is destroyed."</li>
 *   <li><b>FD2.423</b>: reload drones are "stored in various locations around the ship and are
 *       considered destroyed with the last Excess Damage box" — and "if all drone racks are
 *       destroyed and then one or more are repaired, the repaired racks can load <b>the remaining
 *       reload drones</b>".</li>
 * </ul>
 * So the magazine burns and the stockpile does not. Getting that pairing the wrong way round in
 * either direction is a ship that either fights on with ordnance it should have lost, or loses
 * reloads it should still have.
 *
 * <h2>What was wrong</h2>
 * {@link Weapon#damage()} only cleared {@code functional}, and {@link DroneRack} did not override
 * it. A wrecked rack kept every drone in it: the owner's panel went on listing them (the DTO reads
 * {@code getAmmo()}), and a repair handed the rack back fully loaded with ordnance FD2.441 had
 * destroyed. Found while implementing FD2.423's half of the pairing, 2026-10-07.
 */
public class DroneRackDestructionTest {

    private Ship ship;

    @Before
    public void setUp() {
        ship = new Ship();
        ship.init(KlingonShips.getD7());
        ship.setName("IKS Wreck");
    }

    private List<DroneRack> racks() {
        List<DroneRack> out = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                out.add(rack);
        return out;
    }

    /** FD2.441, plainly. */
    @Test
    public void aDestroyedRackLosesTheDronesInIt() {
        DroneRack rack = racks().get(0);
        assertFalse("fixture: the rack sails loaded", rack.getAmmo().isEmpty());

        rack.damage();

        assertFalse(rack.isFunctional());
        assertTrue("FD2.441: any drones on a rack are destroyed with it",
                rack.getAmmo().isEmpty());
        // isEmpty(), not canFire(): a rack's canFire answers the RATE question (shots this turn,
        // the minimum impulse gap) and leaves "functional" to the caller that selects weapons.
        assertTrue("and it is empty, so there is nothing to launch", rack.isEmpty());
    }

    /**
     * The loaded anti-drone rounds go too. A type-G holds them in the same magazine (FD3.7), and
     * "any drones on a rack" means what is in it.
     * <p>
     * The reserve BEHIND the rack is a different pile: {@code addReloads} is reload storage by
     * FD2.423's reckoning — the rule names "Drone and ADD reloads" together — and waits for the last
     * Excess Damage box instead.
     */
    @Test
    public void aDestroyedTypeGLosesItsLoadedRoundsButNotItsReserve() {
        DroneRack typeG = new DroneRack(DroneRack.DroneRackType.TYPE_G);
        typeG.setDesignator("Rack 9");
        List<Drone> load = new ArrayList<>();
        for (int i = 0; i < typeG.getSpaces(); i++)
            load.add(new Drone(DroneType.TypeI));
        typeG.setAmmo(load);
        typeG.setAddAmmo(4);
        typeG.setAddReloads(4);
        assertTrue("fixture: loaded with both", typeG.getAddAmmo() > 0 && !typeG.getAmmo().isEmpty());

        typeG.damage();

        assertTrue("the drones in it are gone", typeG.getAmmo().isEmpty());
        assertEquals("and the rounds in it with them", 0, typeG.getAddAmmo());
        assertEquals("but the reserve behind it is reload storage (FD2.423)",
                4, typeG.getAddReloads());
    }

    /**
     * FD2.423's half: the ship's stockpile is untouched by the loss of every rack, and a repaired
     * rack comes back EMPTY and loads from it.
     * <p>
     * This is the end-to-end shape of the two rules together, which is why it is worth one test even
     * though each half is asserted above.
     */
    @Test
    public void theStockpileSurvivesAndReloadsARepairedRack() {
        int stock = ship.reloadStockpile().held().size();
        assertTrue("fixture: reloads aboard", stock > 0);

        for (DroneRack rack : racks())
            rack.damage();

        assertEquals("the reloads are elsewhere on the ship (FD2.423)",
                stock, ship.reloadStockpile().held().size());

        DroneRack repaired = racks().get(0);
        repaired.repair();

        assertTrue("a repaired rack is empty, not restocked with what it lost",
                repaired.getAmmo().isEmpty());
        DroneType type = ship.reloadStockpile().held().get(0).getDroneType();
        assertEquals("and it loads from the surviving stockpile",
                1, ship.reloadStockpile().takeForRack(type, 1).size());
    }

    /**
     * Destroying a rack does not reduce what the ship can keep in reload storage, because FD2.442
     * measures "the capacity of the ship's ORIGINAL drone racks". Asserted here as well as in
     * {@code ReloadStockpileStorageTest} because this is where someone fixing FD2.441 would be
     * tempted to clear the reload sets too.
     */
    @Test
    public void destroyingARackDoesNotShrinkTheReloadStorage() {
        double capacity = ship.reloadStockpile().capacitySpaces();
        assertTrue(capacity > 0);

        for (DroneRack rack : racks())
            rack.damage();

        assertEquals(capacity, ship.reloadStockpile().capacitySpaces(), 0.001);
    }
}
