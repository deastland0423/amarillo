package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;

public class PlasmaLauncherTest {

    @Before
    public void setUp() {
        TurnTracker.reset();
    }

    // -------------------------------------------------------------------------
    // Plasma-F arming
    // -------------------------------------------------------------------------

    @Test
    public void plasmaFArmsAfterThreeTurns() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        assertTrue(launcher.arm(1));
        assertFalse(launcher.isArmed());
        assertTrue(launcher.arm(1));
        assertFalse(launcher.isArmed());
        assertTrue(launcher.arm(3));
        assertTrue(launcher.isArmed());
        assertEquals(PlasmaType.F, launcher.getPlasmaType());
    }

    @Test
    public void plasmaFWrongEnergyRejected() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        assertFalse(launcher.arm(2)); // wrong first-turn energy
        assertEquals(0, launcher.getArmingTurn());
    }

    @Test
    public void plasmaFRollingMode() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        launcher.arm(1);
        launcher.arm(1);
        assertTrue(launcher.arm(1)); // 1 instead of 3 → rolling
        assertFalse(launcher.isArmed());
        assertTrue(launcher.arm(3)); // next turn finishes it
        assertTrue(launcher.isArmed());
    }

    @Test
    public void plasmaFResetClearsState() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        launcher.arm(1);
        launcher.arm(1);
        launcher.arm(3);
        launcher.reset();
        assertFalse(launcher.isArmed());
        assertEquals(0, launcher.getArmingTurn());
        assertNull(launcher.getPlasmaType());
    }

    // -------------------------------------------------------------------------
    // Plasma-G arming
    // -------------------------------------------------------------------------

    @Test
    public void plasmaGArmsAfterThreeTurns() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.G);
        assertTrue(launcher.arm(2));
        assertFalse(launcher.isArmed());
        assertTrue(launcher.arm(2));
        assertFalse(launcher.isArmed());
        assertTrue(launcher.arm(3));
        assertTrue(launcher.isArmed());
        assertEquals(PlasmaType.G, launcher.getPlasmaType());
    }

    @Test
    public void plasmaGCanArmFInside() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.G);
        assertTrue(launcher.arm(1)); // start as F
        assertEquals(PlasmaType.F, launcher.getPlasmaType());
        assertTrue(launcher.arm(1));
        assertTrue(launcher.arm(3));
        assertTrue(launcher.isArmed());
        assertEquals(PlasmaType.F, launcher.getPlasmaType());
    }

    @Test
    public void plasmaGEnveloping() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.G);
        launcher.arm(2);
        launcher.arm(2);
        assertTrue(launcher.arm(6)); // double cost = enveloping
        assertTrue(launcher.isArmed());
        assertEquals(WeaponArmingType.OVERLOAD, launcher.getArmingType());
    }

    @Test
    public void plasmaGRollingMode() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.G);
        launcher.arm(2);
        launcher.arm(2);
        assertTrue(launcher.arm(2)); // rolling
        assertFalse(launcher.isArmed());
        assertTrue(launcher.arm(3));
        assertTrue(launcher.isArmed());
    }

    // -------------------------------------------------------------------------
    // Launch
    // -------------------------------------------------------------------------

    @Test
    public void launchReturnsArmedTorpedo() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        launcher.arm(1); launcher.arm(1); launcher.arm(3);
        PlasmaTorpedo torp = launcher.launch();
        assertNotNull(torp);
        assertEquals(PlasmaType.F, torp.getPlasmaType());
    }

    @Test
    public void launchResetsLauncher() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        launcher.arm(1); launcher.arm(1); launcher.arm(3);
        launcher.launch();
        assertFalse(launcher.isArmed());
        assertEquals(0, launcher.getArmingTurn());
    }

    @Test
    public void launchUnarmedReturnsNull() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        assertNull(launcher.launch());
    }

    // -------------------------------------------------------------------------
    // Bolt fire
    // -------------------------------------------------------------------------

    @Test
    public void boltFireUnarmedThrows() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        try {
            launcher.fire(5);
            fail("Expected WeaponUnarmedException");
        } catch (WeaponUnarmedException e) {
            // expected
        } catch (TargetOutOfRangeException e) {
            fail("Wrong exception");
        }
    }

    @Test
    public void boltFireOutOfRangeThrows() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        launcher.arm(1); launcher.arm(1); launcher.arm(3);
        try {
            launcher.fire(31); // beyond bolt chart
            fail("Expected TargetOutOfRangeException");
        } catch (TargetOutOfRangeException e) {
            // expected
        } catch (WeaponUnarmedException e) {
            fail("Wrong exception");
        }
    }

    @Test
    public void boltFireReturnsDamageOrZero() throws Exception {
        // Run enough times to see both hits and misses aren't exceptions
        for (int trial = 0; trial < 20; trial++) {
            PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
            launcher.arm(1); launcher.arm(1); launcher.arm(3);
            int dmg = launcher.fire(0); // range 0, can't miss on chart (hit on 1-4)
            // damage = half of full strength at range 0; just verify non-negative
            assertTrue(dmg >= 0);
        }
    }

    @Test
    public void boltFireResetsLauncher() throws Exception {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        launcher.arm(1); launcher.arm(1); launcher.arm(3);
        launcher.fire(0);
        assertFalse(launcher.isArmed());
    }

    // -------------------------------------------------------------------------
    // Pseudo plasma
    // -------------------------------------------------------------------------

    @Test
    public void pseudoReadyByDefault() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.G);
        assertTrue(launcher.isPseudoPlasmaReady());
        assertTrue(launcher.canLaunchPseudo());
    }

    @Test
    public void pseudoLaunchReturnsCorrectType() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.G);
        PlasmaTorpedo pseudo = launcher.launchPseudo();
        assertNotNull(pseudo);
        assertTrue(pseudo.isPseudoPlasma());
        assertEquals(PlasmaType.G, pseudo.getPlasmaType());
    }

    @Test
    public void pseudoOnlyOncePerGame() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.G);
        assertNotNull(launcher.launchPseudo());
        assertFalse(launcher.isPseudoPlasmaReady());
        assertFalse(launcher.canLaunchPseudo());
        assertNull(launcher.launchPseudo());
    }

    @Test
    public void pseudoAndRealCannotFireSameImpulse() throws Exception {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        launcher.arm(1); launcher.arm(1); launcher.arm(3);
        launcher.fire(0); // real bolt fires this impulse
        // pseudo should now be blocked
        assertFalse(launcher.canLaunchPseudo());
    }
}
