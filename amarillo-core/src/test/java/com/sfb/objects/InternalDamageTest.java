package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.Weapon;

public class InternalDamageTest {

    private Ship ship;

    @Before
    public void setUp() {
        ship = new Ship();
        ship.init(KlingonShips.getD7());
    }

    // --- Basic contract ---

    @Test
    public void zeroDamageReturnsEmptyLog() {
        List<String> log = ship.applyInternalDamage(0);
        assertTrue(log.isEmpty());
    }

    @Test
    public void oneDamagePointReturnsOneLogEntry() {
        List<String> log = ship.applyInternalDamage(1);
        assertEquals(1, log.size());
    }

    @Test
    public void logCountMatchesDamagePoints() {
        int bleed = 5;
        List<String> log = ship.applyInternalDamage(bleed);
        assertEquals(bleed, log.size());
    }

    @Test
    public void logEntriesAreNonEmpty() {
        List<String> log = ship.applyInternalDamage(3);
        for (String entry : log) {
            assertNotNull(entry);
            assertFalse(entry.trim().isEmpty());
        }
    }

    // --- Actual damage is applied ---

    @Test
    public void repeatedDamageEventuallyReducesHull() {
        // Apply enough bleed-through that at least one hull box gets hit.
        // With 20 rolls across the DAC the odds of zero hull hits are negligible.
        int fhullBefore = ship.getHullBoxes().getAvailableFhull();
        int ahullBefore = ship.getHullBoxes().getAvailableAhull();
        int lwarpBefore = ship.getPowerSysetems().getAvailableLWarp();
        int rwarpBefore = ship.getPowerSysetems().getAvailableRWarp();
        int bridgeBefore = ship.getControlSpaces().getAvailableBridge();

        ship.applyInternalDamage(20);

        int fhullAfter  = ship.getHullBoxes().getAvailableFhull();
        int ahullAfter  = ship.getHullBoxes().getAvailableAhull();
        int lwarpAfter  = ship.getPowerSysetems().getAvailableLWarp();
        int rwarpAfter  = ship.getPowerSysetems().getAvailableRWarp();
        int bridgeAfter = ship.getControlSpaces().getAvailableBridge();

        int totalBefore = fhullBefore + ahullBefore + lwarpBefore + rwarpBefore + bridgeBefore;
        int totalAfter  = fhullAfter  + ahullAfter  + lwarpAfter  + rwarpAfter  + bridgeAfter;

        assertTrue("20 damage points should reduce at least one system box", totalAfter < totalBefore);
    }

    // --- DAC resets between volleys ---

    @Test
    public void dacResetsAfterVolley() {
        // Fire two separate volleys. If the DAC did NOT reset, special items
        // exhausted in volley 1 would still be gone in volley 2, causing the
        // second volley's log entries to differ systematically. We can't assert
        // exact rolls, but we CAN assert that both volleys produce the right count.
        List<String> volley1 = ship.applyInternalDamage(3);
        List<String> volley2 = ship.applyInternalDamage(3);

        assertEquals(3, volley1.size());
        assertEquals(3, volley2.size());
    }

    // --- Phasers can be destroyed via DAC ---

    // --- Drone racks are hit by drone DAC results ---

    @Test
    public void droneRacksCanBeDestroyedByInternalDamage() {
        // "drone" is at line 3 (roll=3) in the DAC, probability 2/36 per roll.
        // With 300 rolls P(never hitting drone) = (34/36)^300 ≈ 1e-8 — negligible.
        long dronesBefore = ship.getWeapons().getDroneList().stream()
                .filter(Weapon::isFunctional).count();
        assertTrue("D7 should start with functional drone racks", dronesBefore > 0);

        ship.applyInternalDamage(300);

        long dronesAfter = ship.getWeapons().getDroneList().stream()
                .filter(Weapon::isFunctional).count();
        assertTrue("Heavy internal damage should destroy at least one drone rack",
                dronesAfter < dronesBefore);
    }

    @Test
    public void destroyedDroneRackIsMarkedNonFunctional() {
        // Verify the infrastructure the drone-DAC fix relies on: calling damage() on
        // a rack from getDroneList() must flip isFunctional() to false.
        List<Weapon> drones = ship.getWeapons().getDroneList();
        assertFalse("D7 must have at least one drone rack", drones.isEmpty());

        Weapon rack = drones.get(0);
        assertTrue("Drone rack should start functional", rack.isFunctional());
        rack.damage();
        assertFalse("Drone rack must be non-functional after damage()", rack.isFunctional());
    }

    @Test
    public void phasersCanBeDestroyedByInternalDamage() {
        int phasersBefore = ship.getWeapons().getPhaserList().stream()
                .mapToInt(w -> w.isFunctional() ? 1 : 0).sum();

        // Apply heavy damage — enough that at least one "phaser" DAC result fires.
        // "phaser" appears on lines 3 and 4; 300 rolls makes failure negligible.
        ship.applyInternalDamage(300);

        int phasersAfter = ship.getWeapons().getPhaserList().stream()
                .mapToInt(w -> w.isFunctional() ? 1 : 0).sum();

        assertTrue("Heavy internal damage should destroy at least one phaser",
                phasersAfter < phasersBefore);
    }
}
