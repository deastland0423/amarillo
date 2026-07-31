package com.sfb;

import com.sfb.objects.OptionMount;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.properties.Faction;
import com.sfb.samples.OrionShips;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Orion Light Raider (R8.7) ship data — the first Orion ship. Verifies the new
 * Orion fields (stealth bonus G15.8, option mounts G15.4) load correctly, and
 * that the sample builder and the JSON file agree.
 */
public class OrionLrTest {

    private void assertLr(Ship lr) {
        assertEquals(Faction.Orion, lr.getFaction());
        assertEquals("LR", lr.getHullType());
        assertEquals(68, lr.getBattlePointValue());
        assertEquals(4, lr.getSizeClass());
        assertTrue("LR is nimble (C11)", lr.isNimble());
        assertEquals("Orion Stealth Bonus (G15.8)", 2, lr.getStealthBonus());
        assertNotNull("LR has a cloaking device", lr.getCloakingDevice());
        assertEquals("three Ph-1s (Type-III chart is just the down-fire reference)",
                3, lr.getWeapons().getPhaserList().size());

        List<OptionMount> mounts = lr.getOptionMounts();
        assertEquals("three OPT mounts (G15.4)", 3, mounts.size());
        assertEquals(OptionMount.Position.CENTERLINE, mounts.get(0).getPosition());
        assertEquals("A", mounts.get(0).getDesignator());
        assertEquals(OptionMount.Position.WING, mounts.get(1).getPosition());
        assertEquals(OptionMount.Position.WING, mounts.get(2).getPosition());
        assertTrue("mounts start empty (filled at setup)", mounts.get(0).isEmpty());
    }

    @Test
    public void sampleBuilder_buildsTheLr() {
        Ship lr = new Ship();
        lr.init(OrionShips.getLr());
        assertLr(lr);
    }

    @Test
    public void json_loadsAndMatchesTheSampleBuilder() throws Exception {
        ShipSpec spec = ShipSpec.fromJson(new File("../data/factions/orion/lr.json"));
        assertNotNull("lr.json parsed", spec);
        Ship lr = ShipLibrary.createShip(spec);
        assertLr(lr);
    }
}
