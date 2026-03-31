package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.samples.SampleShips;

public class ArmorTest {

    private Ship ship;

    @Before
    public void setUp() {
        ship = new Ship();
        ship.init(SampleShips.getFedOcl());
    }

    // --- Initial value ---

    @Test
    public void armorInitialisedFromSpec() {
        assertEquals(6, ship.getArmor());
    }

    // --- Bleed less than armor ---

    @Test
    public void bleedBelowArmorProducesNoLogEntries() {
        List<String> log = ship.applyInternalDamage(3);
        assertTrue("Bleed fully absorbed by armor — no DAC rolls expected", log.isEmpty());
    }

    @Test
    public void bleedBelowArmorReducesArmorByBleedAmount() {
        ship.applyInternalDamage(3);
        assertEquals(3, ship.getArmor());
    }

    // --- Bleed exactly equal to armor ---

    @Test
    public void bleedEqualToArmorProducesNoLogEntries() {
        List<String> log = ship.applyInternalDamage(6);
        assertTrue("Bleed exactly absorbed by armor — no DAC rolls expected", log.isEmpty());
    }

    @Test
    public void bleedEqualToArmorDepletesArmorToZero() {
        ship.applyInternalDamage(6);
        assertEquals(0, ship.getArmor());
    }

    // --- Bleed greater than armor ---

    @Test
    public void bleedExceedingArmorProducesLogEntriesForExcess() {
        // 6 armor + 4 excess = 4 DAC rolls
        List<String> log = ship.applyInternalDamage(10);
        assertEquals(4, log.size());
    }

    @Test
    public void bleedExceedingArmorDepletesArmorToZero() {
        ship.applyInternalDamage(10);
        assertEquals(0, ship.getArmor());
    }

    // --- Armor stays at zero after depletion ---

    @Test
    public void secondHitAfterDepletionGoesFullyToDac() {
        ship.applyInternalDamage(6);   // depletes armor
        assertEquals(0, ship.getArmor());

        List<String> log = ship.applyInternalDamage(4);
        assertEquals("All 4 bleed points should reach DAC after armor is gone", 4, log.size());
        assertEquals(0, ship.getArmor());  // still 0, not negative
    }
}
