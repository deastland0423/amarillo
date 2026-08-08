package com.sfb;

import com.sfb.objects.OptionMountCatalog;
import com.sfb.objects.OptionMountLoadout;
import com.sfb.objects.Ship;
import com.sfb.samples.OrionShips;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Slice 2 of Orion option mounts (G15.4): validate + equip a player's choice
 * against the Annex #8B catalog. The LR (size-4, Y129, one centerline mount A +
 * two wings B/C) exercises every rejection path and a successful wing equip.
 */
public class OptionMountLoadoutTest {

    private Ship lr;
    private OptionMountCatalog catalog;

    @Before
    public void setUp() throws Exception {
        lr = new Ship();
        lr.init(OrionShips.getLr());
        catalog = OptionMountCatalog.fromJson(new File("../data/reference/orion_option_mounts.json"));
    }

    @Test
    public void equip_intoWingMount_firesAndAdjustsBpv() {
        Weapon w = OptionMountLoadout.equip(lr, catalog, "B", "Phaser-3"); // wing B, arc LS

        assertEquals("fires in the mount's arc", "LS", w.getArcLabel());
        assertTrue("registered in the ship's weapons", lr.getWeapons().fetchAllWeapons().contains(w));
        assertEquals("3 base Ph-1 + equipped Ph-3", 4, lr.getWeapons().getPhaserList().size());
        assertEquals("effective BPV folds in the -0.5 cost", 67.5, lr.getEffectiveBpv(), 1e-9);
        assertFalse("mount B now filled", lr.getOptionMounts().get(1).isEmpty());
    }

    @Test
    public void reject_sizeBarredHeavy_onSize4Hull() {
        // Disruptor-30 is ‡ (barred on size-4-or-smaller); LR is size 4.
        String reason = OptionMountLoadout.validate(lr, catalog, "A", "Disruptor-30");
        assertNotNull(reason);
        assertTrue(reason, reason.contains("size-4"));
    }

    @Test
    public void reject_centerlineOnlyWeapon_inAWingMount() {
        // Hellbore is Δ (no wing mounts). Try it in wing B.
        String reason = OptionMountLoadout.validate(lr, catalog, "B", "Hellbore");
        assertNotNull(reason);
        assertTrue(reason, reason.contains("WING"));
    }

    @Test
    public void reject_weaponFromTheFuture() {
        // ADD is not available until Y140; the LR is Y129.
        String reason = OptionMountLoadout.validate(lr, catalog, "A", "ADD (6 round)");
        assertNotNull(reason);
        assertTrue(reason, reason.contains("Y140"));
    }

    @Test
    public void reject_multiMountWeapon_forNow() {
        // Plasma-G needs two adjacent centerline mounts (* ), unsupported yet.
        String reason = OptionMountLoadout.validate(lr, catalog, "A", "Plasma-G Torp (No Swivel)");
        assertNotNull(reason);
        assertTrue(reason, reason.contains("adjacent centerline"));
    }

    @Test
    public void reject_neverWeapon() {
        String reason = OptionMountLoadout.validate(lr, catalog, "A", "Mauler");
        assertNotNull(reason);
        assertTrue(reason, reason.contains("not available"));
    }

    @Test
    public void reject_unimplementedOption() {
        // Ion Cannon is a legal Orion option but has no weapon class yet.
        String reason = OptionMountLoadout.validate(lr, catalog, "A", "Ion Cannon");
        assertNotNull(reason);
        assertTrue(reason, reason.contains("no weapon implementation"));
    }

    @Test
    public void reject_alreadyFilledMount() {
        OptionMountLoadout.equip(lr, catalog, "A", "Phaser-1");
        String reason = OptionMountLoadout.validate(lr, catalog, "A", "Fusion Beam");
        assertNotNull(reason);
        assertTrue(reason, reason.contains("already filled"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void equip_illegalChoice_throws() {
        OptionMountLoadout.equip(lr, catalog, "B", "Hellbore"); // Δ into a wing
    }
}
