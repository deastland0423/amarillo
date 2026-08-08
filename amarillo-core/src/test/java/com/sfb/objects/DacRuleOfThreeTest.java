package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.weapons.ADD;
import com.sfb.weapons.Fusion;
import com.sfb.weapons.Hellbore;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.Photon;

/**
 * Tests for the rule-of-3 DAC choice enforcement (D4.3221–3).
 *
 * Phaser: per-volley (group resets at start of each fresh damage chain).
 * Torpedo / Drone: cumulative over the entire scenario.
 *
 * Ships are built programmatically with minimal weapons — no full init()
 * required since these tests call applyDacChoiceHit and dacChoiceOptionsForTest
 * directly without touching the DAC or damage resolution machinery.
 */
public class DacRuleOfThreeTest {

    // Phaser ship: one Ph1 (best, priority 8) + three Ph3s (worst, priority 23)
    private Ship phaserShip;
    private static final String PH1  = "Phaser1-A";
    private static final String PH3A = "Phaser3-1";
    private static final String PH3B = "Phaser3-2";
    private static final String PH3C = "Phaser3-3";

    // Torp ship: one Photon (best, priority 29) + three Fusions (worst torp, priority 61)
    private Ship torpShip;
    private static final String PHOTON  = "Photon-A";
    private static final String FUSE1   = "Fusion-1";
    private static final String FUSE2   = "Fusion-2";
    private static final String FUSE3   = "Fusion-3";

    // Drone ship: one Hellbore (best drone, priority 12) + three ADDs (priority 57)
    private Ship droneShip;
    private static final String HELLBORE = "Hellbore-A";
    private static final String ADD1     = "ADD-1";
    private static final String ADD2     = "ADD-2";
    private static final String ADD3     = "ADD-3";

    @Before
    public void setUp() {
        phaserShip = buildPhaserShip();
        torpShip   = buildTorpShip();
        droneShip  = buildDroneShip();
    }

    // -------------------------------------------------------------------------
    // Phaser rule of 3 (D4.3221) — per-volley
    // -------------------------------------------------------------------------

    @Test
    public void phaser_position0_allTypesOffered() {
        List<String> opts = phaserShip.dacChoiceOptionsForTest("phaser");
        assertTrue(opts.contains(PH1));
        assertTrue(opts.contains(PH3A));
        assertEquals(4, opts.size());
    }

    @Test
    public void phaser_position1_allTypesStillOffered() {
        phaserShip.applyDacChoiceHit("phaser", PH3A, null);
        List<String> opts = phaserShip.dacChoiceOptionsForTest("phaser");
        assertTrue("Best type still offered at position 1", opts.contains(PH1));
        assertTrue(opts.contains(PH3B));
        assertEquals(3, opts.size());
    }

    @Test
    public void phaser_position2_restrictedToBestWhenNoBestTaken() {
        // Positions 0 and 1 took non-best (Ph3) — position 2 must force Ph1
        phaserShip.applyDacChoiceHit("phaser", PH3A, null);
        phaserShip.applyDacChoiceHit("phaser", PH3B, null);
        List<String> opts = phaserShip.dacChoiceOptionsForTest("phaser");
        assertEquals("Only best type offered at 3rd hit when none taken", 1, opts.size());
        assertEquals(PH1, opts.get(0));
    }

    @Test
    public void phaser_position2_unrestrictedWhenBestAlreadyTaken() {
        // Best type taken at position 0 — position 2 is free choice among survivors
        phaserShip.applyDacChoiceHit("phaser", PH1,  null);
        phaserShip.applyDacChoiceHit("phaser", PH3A, null);
        List<String> opts = phaserShip.dacChoiceOptionsForTest("phaser");
        // Ph1 is destroyed; Ph3B and Ph3C remain — both valid
        assertEquals(2, opts.size());
        assertTrue(opts.contains(PH3B));
        assertTrue(opts.contains(PH3C));
    }

    @Test
    public void phaser_groupResets_afterThirdHit() {
        // Complete a full group: Ph3, Ph3, Ph1 (forced)
        phaserShip.applyDacChoiceHit("phaser", PH3A, null);
        phaserShip.applyDacChoiceHit("phaser", PH3B, null);
        phaserShip.applyDacChoiceHit("phaser", PH1,  null);
        // Back to position 0 — only Ph3C remains and is a valid choice
        List<String> opts = phaserShip.dacChoiceOptionsForTest("phaser");
        assertEquals(1, opts.size());
        assertEquals(PH3C, opts.get(0));
    }

    @Test
    public void phaser_groupResets_atStartOfNewVolley() {
        // Advance to position 1 within a volley
        phaserShip.applyDacChoiceHit("phaser", PH3A, null);
        // Simulate new volley starting (resolveInternalDamage calls this for fresh damage)
        phaserShip.resetPhaserDacGroup();
        // Now at position 0 — take two more non-best hits
        phaserShip.applyDacChoiceHit("phaser", PH3B, null);
        phaserShip.applyDacChoiceHit("phaser", PH3C, null);
        // Position 2 in the new group — must take best
        List<String> opts = phaserShip.dacChoiceOptionsForTest("phaser");
        assertEquals(1, opts.size());
        assertEquals(PH1, opts.get(0));
    }

    // -------------------------------------------------------------------------
    // Torpedo rule of 3 (D4.3222) — cumulative across entire scenario
    // -------------------------------------------------------------------------

    @Test
    public void torp_position0_allTypesOffered() {
        List<String> opts = torpShip.dacChoiceOptionsForTest("torp");
        assertTrue(opts.contains(PHOTON));
        assertTrue(opts.contains(FUSE1));
        assertEquals(4, opts.size());
    }

    @Test
    public void torp_position2_restrictedToBestWhenNoBestTaken() {
        torpShip.applyDacChoiceHit("torp", FUSE1, null);
        torpShip.applyDacChoiceHit("torp", FUSE2, null);
        List<String> opts = torpShip.dacChoiceOptionsForTest("torp");
        assertEquals(1, opts.size());
        assertEquals(PHOTON, opts.get(0));
    }

    @Test
    public void torp_groupResets_afterThirdHit() {
        torpShip.applyDacChoiceHit("torp", FUSE1,  null);
        torpShip.applyDacChoiceHit("torp", FUSE2,  null);
        torpShip.applyDacChoiceHit("torp", PHOTON, null); // forced; completes group
        // New group at position 0 — only FUSE3 remains, freely chooseable
        List<String> opts = torpShip.dacChoiceOptionsForTest("torp");
        assertEquals(1, opts.size());
        assertEquals(FUSE3, opts.get(0));
    }

    @Test
    public void torp_position2_persistsAcrossVolleys() {
        // Two hits in "volley 1"
        torpShip.applyDacChoiceHit("torp", FUSE1, null);
        torpShip.applyDacChoiceHit("torp", FUSE2, null);
        // resetPhaserDacGroup() would NOT be called for torp — group persists
        // Third hit (from "volley 2") is still restricted
        List<String> opts = torpShip.dacChoiceOptionsForTest("torp");
        assertEquals("Torp group persists across volleys", 1, opts.size());
        assertEquals(PHOTON, opts.get(0));
    }

    // -------------------------------------------------------------------------
    // Drone rule of 3 (D4.3223) — cumulative across entire scenario
    // -------------------------------------------------------------------------

    @Test
    public void drone_position0_allTypesOffered() {
        List<String> opts = droneShip.dacChoiceOptionsForTest("drone");
        assertTrue(opts.contains(HELLBORE));
        assertTrue(opts.contains(ADD1));
        assertEquals(4, opts.size());
    }

    @Test
    public void drone_position2_restrictedToBestWhenNoBestTaken() {
        droneShip.applyDacChoiceHit("drone", ADD1, null);
        droneShip.applyDacChoiceHit("drone", ADD2, null);
        List<String> opts = droneShip.dacChoiceOptionsForTest("drone");
        assertEquals(1, opts.size());
        assertEquals(HELLBORE, opts.get(0));
    }

    @Test
    public void drone_groupResets_afterThirdHit() {
        droneShip.applyDacChoiceHit("drone", ADD1,     null);
        droneShip.applyDacChoiceHit("drone", ADD2,     null);
        droneShip.applyDacChoiceHit("drone", HELLBORE, null); // forced; completes group
        List<String> opts = droneShip.dacChoiceOptionsForTest("drone");
        assertEquals(1, opts.size());
        assertEquals(ADD3, opts.get(0));
    }

    @Test
    public void drone_position2_persistsAcrossVolleys() {
        droneShip.applyDacChoiceHit("drone", ADD1, null);
        droneShip.applyDacChoiceHit("drone", ADD2, null);
        List<String> opts = droneShip.dacChoiceOptionsForTest("drone");
        assertEquals("Drone group persists across volleys", 1, opts.size());
        assertEquals(HELLBORE, opts.get(0));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Ship buildPhaserShip() {
        Ship ship = new Ship();
        Phaser1 ph1 = new Phaser1(); ph1.setDesignator("A");
        Phaser3 ph3a = new Phaser3(); ph3a.setDesignator("1");
        Phaser3 ph3b = new Phaser3(); ph3b.setDesignator("2");
        Phaser3 ph3c = new Phaser3(); ph3c.setDesignator("3");
        ship.getWeapons().addWeapon(ph1);
        ship.getWeapons().addWeapon(ph3a);
        ship.getWeapons().addWeapon(ph3b);
        ship.getWeapons().addWeapon(ph3c);
        return ship;
    }

    private static Ship buildTorpShip() {
        Ship ship = new Ship();
        Photon photon = new Photon(); photon.setDesignator("A");
        Fusion fuse1  = new Fusion(); fuse1.setDesignator("1");
        Fusion fuse2  = new Fusion(); fuse2.setDesignator("2");
        Fusion fuse3  = new Fusion(); fuse3.setDesignator("3");
        ship.getWeapons().addWeapon(photon);
        ship.getWeapons().addWeapon(fuse1);
        ship.getWeapons().addWeapon(fuse2);
        ship.getWeapons().addWeapon(fuse3);
        return ship;
    }

    private static Ship buildDroneShip() {
        Ship ship = new Ship();
        Hellbore hellbore = new Hellbore(); hellbore.setDesignator("A");
        ADD add1 = new ADD(ADD.AddType.ADD_6, 6); add1.setDesignator("1");
        ADD add2 = new ADD(ADD.AddType.ADD_6, 6); add2.setDesignator("2");
        ADD add3 = new ADD(ADD.AddType.ADD_6, 6); add3.setDesignator("3");
        ship.getWeapons().addWeapon(hellbore);
        ship.getWeapons().addWeapon(add1);
        ship.getWeapons().addWeapon(add2);
        ship.getWeapons().addWeapon(add3);
        return ship;
    }
}
