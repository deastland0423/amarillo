package com.sfb;

import com.sfb.weapons.ESG;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ESG capacitor mechanics (G23.24/.242): a capacitor holds up to 7 points and
 * releases a chosen 1–5, keeping the remainder; a plain generator holds 5 and
 * dumps all of it. Energy can be added to a capacitor even while the field is up
 * (G23.243), but not to a plain generator with an active field.
 */
public class EsgCapacitorTest {

    private ESG capacitor() {
        ESG esg = new ESG();
        esg.setHasCapacitor(true);
        return esg;
    }

    @Test
    public void capacitorHoldsSeven_plainHoldsFive() {
        ESG cap = capacitor();
        assertEquals(7, cap.maxStorage());
        cap.addEnergy(9);
        assertEquals("capped at 7 (G23.242)", 7, cap.getStoredEnergy());

        ESG plain = new ESG();
        assertEquals(5, plain.maxStorage());
        plain.addEnergy(9);
        assertEquals("capped at 5 (G23.22)", 5, plain.getStoredEnergy());
    }

    @Test
    public void partialRelease_keepsRemainderInCapacitor() {
        ESG cap = capacitor();
        cap.setStoredEnergy(7);
        cap.announce(1, 4, 10);   // release 4 of the 7 at radius 1
        cap.release(14);

        assertTrue(cap.isActive());
        assertEquals("strength = chart[radius 1][energy 4] (G23.42)", 15, cap.getStrength());
        assertEquals("unspent energy stays in the capacitor (G23.242)", 3, cap.getStoredEnergy());
    }

    @Test
    public void maxReleasable_isCappedAtFive() {
        ESG cap = capacitor();
        cap.setStoredEnergy(7);
        assertEquals("a single release uses at most 5 (G23.42)", 5, cap.maxReleasable());
    }

    @Test
    public void plainGenerator_dumpsAllEnergy() {
        ESG plain = new ESG();
        plain.setStoredEnergy(5);
        plain.announce(2, 10);    // no-amount overload → release all
        plain.release(14);

        assertTrue(plain.isActive());
        assertEquals(17, plain.getStrength()); // chart[radius 2][energy 5]
        assertEquals("plain generator retains nothing (G23.223)", 0, plain.getStoredEnergy());
    }

    @Test
    public void capacitorCanChargeWhileFieldIsActive() {
        ESG cap = capacitor();
        cap.setStoredEnergy(7);
        cap.activate(0, 5);       // release 5, keep 2 in the capacitor
        assertTrue(cap.isActive());
        assertEquals(2, cap.getStoredEnergy());

        cap.addEnergy(3);         // G23.243: energy can be added to the capacitor mid-field
        assertEquals(5, cap.getStoredEnergy());
    }

    @Test
    public void plainGenerator_ignoresChargeWhileActive() {
        ESG plain = new ESG();
        plain.setStoredEnergy(5);
        plain.activate(0, 5);     // dumps all; field up
        assertTrue(plain.isActive());
        assertEquals(0, plain.getStoredEnergy());

        plain.addEnergy(3);       // G23.243: cannot store in an active no-capacitor ESG
        assertEquals(0, plain.getStoredEnergy());
    }
}
