package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.samples.OrionShips;
import com.sfb.scenario.CoiLoadout;
import com.sfb.scenario.ScenarioLoader;
import com.sfb.scenario.ScenarioSpec;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Orion option mounts flow through the pre-game COI setup (G15.4): a
 * CoiLoadout.optionMounts selection is realized by ScenarioLoader.applyCoi,
 * equipping the weapon and adjusting effective BPV. Illegal picks are skipped
 * (logged), never thrown, so one bad choice can't abort ship setup.
 */
public class CoiOptionMountTest {

    private Ship lr() {
        Ship s = new Ship();
        s.init(OrionShips.getLr());
        return s;
    }

    @Test
    public void applyCoi_equipsAChosenOption() {
        Ship lr = lr();
        CoiLoadout loadout = new CoiLoadout();
        loadout.optionMounts.put("B", "Phaser-3"); // wing B

        ScenarioLoader.applyCoi(lr, loadout, new ScenarioSpec());

        assertEquals("Ph-3 equipped alongside the 3 base phasers",
                4, lr.getWeapons().getPhaserList().size());
        assertEquals("effective BPV reflects the -0.5 delta", 67.5, lr.getEffectiveBpv(), 1e-9);
        assertFalse("mount B filled", lr.getOptionMounts().get(1).isEmpty());
    }

    @Test
    public void applyCoi_skipsAnIllegalOption_withoutThrowing() {
        Ship lr = lr();
        CoiLoadout loadout = new CoiLoadout();
        loadout.optionMounts.put("A", "Disruptor-30"); // ‡ — barred on the size-4 LR

        ScenarioLoader.applyCoi(lr, loadout, new ScenarioSpec()); // must not throw

        assertTrue("mount A stays empty after the illegal pick",
                lr.getOptionMounts().get(0).isEmpty());
        assertEquals("BPV unchanged", 68.0, lr.getEffectiveBpv(), 1e-9);
    }
}
