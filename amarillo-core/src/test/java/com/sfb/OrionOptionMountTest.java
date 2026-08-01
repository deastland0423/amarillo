package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.samples.OrionShips;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.Weapon;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Orion option mounts (G15.4) — Slice 1: a weapon pinned into a mount (by a
 * hand-authored scenario/ship JSON) actually fires, taking the mount's arc,
 * and its Annex #8B cost delta flows into the ship's effective BPV. No
 * placement validation yet — a pinned loadout is trusted.
 */
public class OrionOptionMountTest {

    /** Load the real LR spec and pin a Phaser-2 (Annex #8B: -0.25 BPV) into centerline mount A. */
    private Ship lrWithPinnedPhaser2() throws Exception {
        ShipSpec spec = ShipSpec.fromJson(new File("../data/factions/orion/lr.json"));
        ShipSpec.WeaponSpec ph2 = new ShipSpec.WeaponSpec();
        ph2.type = "Phaser2";
        ph2.designator = "OPT-A";
        spec.optionMounts.get(0).weapon = ph2;   // mount A is CENTERLINE, arc FA
        spec.optionMounts.get(0).bpvCost = -0.25; // Annex #8B Phaser-2
        return ShipLibrary.createShip(spec);
    }

    @Test
    public void pinnedWeapon_joinsTheActiveWeaponsAndFiresFromTheMountArc() throws Exception {
        Ship lr = lrWithPinnedPhaser2();

        Phaser2 pinned = null;
        for (Weapon w : lr.getWeapons().fetchAllWeapons()) {
            if (w instanceof Phaser2) { pinned = (Phaser2) w; break; }
        }
        assertNotNull("pinned Phaser-2 was added to the ship's active weapons", pinned);
        assertEquals("option weapon fires in the mount's arc, not its own (G15.4)",
                "FA", pinned.getArcLabel());
        assertEquals("base 3 Ph-1 + pinned Ph-2 = 4 phasers",
                4, lr.getWeapons().getPhaserList().size());
    }

    @Test
    public void effectiveBpv_foldsInTheOptionCost() throws Exception {
        Ship lr = lrWithPinnedPhaser2();
        assertEquals("base hull BPV unchanged", 68, lr.getBpv());
        assertEquals("effective BPV = 68 + (-0.25) (Annex #8B, decimal)",
                67.75, lr.getEffectiveBpv(), 1e-9);
    }

    @Test
    public void emptyMounts_effectiveBpvEqualsBase() {
        Ship lr = new Ship();
        lr.init(OrionShips.getLr()); // all mounts empty
        assertEquals(68.0, lr.getEffectiveBpv(), 1e-9);
    }
}
