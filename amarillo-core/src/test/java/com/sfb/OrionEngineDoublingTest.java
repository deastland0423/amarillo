package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.samples.OrionShips;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Orion engine doubling (G15.2): doubling a warp engine outputs 2x its boxes
 * this turn (bigger power budget), costs one engine box at end of turn, and —
 * while any warp is doubled — the stealth bonus is lost (G15.82). Only Orion
 * ships that can double are affected (G15.2/G15.28).
 */
public class OrionEngineDoublingTest {

    private Ship lr() {
        Ship s = new Ship();
        s.init(OrionShips.getLr()); // lwarp 5, rwarp 5, impulse 2
        return s;
    }

    @Test
    public void doublingBoostsThePowerBudget() {
        Ship lr = lr();
        int base = lr.getPowerSystems().getTotalAvailablePower();
        Energy e = new Energy();
        e.setDoubleLwarp(true);
        e.setDoubleRwarp(true);
        lr.allocateEnergy(e);
        assertEquals("both 5-box warp engines doubled → +10 power (G15.2)",
                base + 10, lr.getPowerSystems().getTotalAvailablePower());
    }

    @Test
    public void doublingLosesOneWarpBoxAtEndOfTurn() {
        Ship lr = lr();
        int warpBefore = lr.getPowerSystems().getRemainingWarp(); // 10 boxes
        Energy e = new Energy();
        e.setDoubleLwarp(true);
        e.setDoubleRwarp(true);
        lr.allocateEnergy(e);

        lr.resolveEngineDoublingDamage();

        assertEquals("size-4 loses exactly one warp box regardless (G15.213)",
                warpBefore - 1, lr.getPowerSystems().getRemainingWarp());
        assertFalse("doubling cleared for next turn",
                lr.getPowerSystems().isAnyEngineDoubled());
    }

    @Test
    public void stealthLostWhileWarpDoubled() {
        Ship lr = lr();
        assertEquals(2, lr.getStealthEcm());
        Energy e = new Energy();
        e.setDoubleLwarp(true);
        lr.allocateEnergy(e);
        assertEquals("stealth bonus lost while a warp engine is doubled (G15.82)",
                0, lr.getStealthEcm());
    }

    @Test
    public void impulseOnlyDoubling_keepsStealth() {
        Ship lr = lr();
        Energy e = new Energy();
        e.setDoubleImpulse(true); // impulse only — no warp doubled
        lr.allocateEnergy(e);
        assertEquals("stealth is only lost for WARP doubling (G15.82)", 2, lr.getStealthEcm());
    }

    @Test
    public void nonOrionShipCannotDouble() {
        Ship fed = new Ship();
        fed.init(FederationShips.getFedCa());
        int base = fed.getPowerSystems().getTotalAvailablePower();
        Energy e = new Energy();
        e.setDoubleLwarp(true);
        e.setDoubleRwarp(true);
        fed.allocateEnergy(e);
        assertFalse(fed.canDoubleEngines());
        assertEquals("non-Orion cannot double its engines (G15.2)",
                base, fed.getPowerSystems().getTotalAvailablePower());
    }
}
