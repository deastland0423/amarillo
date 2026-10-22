package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.PPD;

/**
 * A PPD firing run inside a running game (E11.30): one trigger, four impulses.
 *
 * <h2>What this covers that the weapon's own tests cannot</h2>
 * {@code PpdTest} drives the device by hand. Here the ENGINE drives it: the run is remembered
 * across impulses nobody fired on, each pulse works out its own range and facing shield, and
 * the damage arrives as a splash volley that resolves ahead of ordinary fire. That persistence
 * is the whole weapon, and it lives in the phase loop rather than in the weapon.
 */
public class PpdFiringRunTest {

    private Game game;
    private Ship firer;
    private Ship target;
    private PPD ppd;

    @Before
    public void setUp() {
        game = new Game();
        game.getClock().nextImpulse();

        firer = new Ship();
        firer.init(FederationShips.getFedCa());
        firer.setName("ISC Pulsar");
        firer.setLocation(new Location(10, 10));
        firer.setFacing(1);

        target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("USS Enterprise");
        // Ten hexes away: inside the PPD's best band and well outside its dead zone.
        target.setLocation(new Location(10, 20));
        target.setFacing(1);

        game.getShips().add(firer);
        game.getShips().add(target);

        ppd = new PPD();
        ppd.setDesignator("1");
        firer.getWeapons().addWeapon(ppd);
        ppd.arm(4);
        ppd.arm(4);

        firer.setActiveFireControl(true);
        firer.addLockOn(target);
        toDirectFire();
    }

    /** Advance until the direct-fire phase, which is where a PPD is fired (E11.31). */
    private void toDirectFire() {
        for (int i = 0; i < 24 && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; i++)
            game.advancePhase();
        assertEquals("fixture: should have reached the direct-fire phase",
                Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());
    }

    /** Walk forward one whole impulse, back to the direct-fire phase. */
    private void nextImpulse() {
        game.advancePhase();
        toDirectFire();
    }

    // ------------------------------------------------------------------ the run persists

    /**
     * THE POINT OF THE WEAPON. Firing once leaves three pulses owed, and the engine delivers
     * them on later impulses without anyone firing again.
     */
    @Test
    public void oneTriggerPulsesAcrossFourImpulses() {
        Game.ActionResult fired = game.firePpd(firer, ppd, target);

        assertTrue(fired.getMessage(), fired.isSuccess());
        assertEquals("three pulses still owed", 3, ppd.pulsesRemaining());
        assertEquals("and the game is tracking the run", 1, game.activePpdRunCount());

        nextImpulse();
        assertEquals(2, ppd.pulsesRemaining());
        nextImpulse();
        assertEquals(1, ppd.pulsesRemaining());
        nextImpulse();
        assertEquals("the run is spent", 0, ppd.pulsesRemaining());
        assertFalse(ppd.isPulsing());
        assertEquals("and the game has let it go", 0, game.activePpdRunCount());
    }

    /** A pulse that scores arrives as a SPLASH volley — three shields from one pulse. */
    @Test
    public void ascoringPulseQueuesASplashVolley() {
        // Fire repeatedly until a wave-lock takes, so the test does not hang on 2d6.
        for (int attempt = 0; attempt < 50 && game.getPendingVolleys().isEmpty(); attempt++) {
            if (!ppd.isPulsing()) {
                ppd.arm(4);
                ppd.arm(4);
                game.firePpd(firer, ppd, target);
            } else {
                nextImpulse();
            }
        }
        assertFalse("fixture: 50 runs without a single scoring pulse",
                game.getPendingVolleys().isEmpty());

        Game.PendingVolley volley = game.getPendingVolleys().get(0);
        assertTrue("a PPD pulse is a splash volley (E11.35)", volley.isSplash());
        assertEquals("1+4+1 at range 10 spreads over three shields", 3, volley.shieldDamage.size());
        assertEquals("and totals the chart's six", 6, volley.totalDamage);
    }

    // ------------------------------------------------------------------ E11.15

    /** A PPD cannot be fired on passive fire control at all (E11.152). */
    @Test
    public void itCannotBeFiredOnPassiveFireControl() {
        firer.setActiveFireControl(false);

        Game.ActionResult result = game.firePpd(firer, ppd, target);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("passive"));
        assertFalse("and no run started", ppd.isPulsing());
    }

    /** Nor without a lock-on (E11.15). */
    @Test
    public void itCannotBeFiredWithoutALockOn() {
        firer.removeLockOn(target);

        Game.ActionResult result = game.firePpd(firer, ppd, target);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("lock-on"));
    }

    /**
     * E11.15 requires both "for the entire time that the weapon is pulsing" — so losing the
     * lock-on mid-run ends it, rather than merely costing the wave-lock. The weapon cannot
     * pulse at all without one.
     */
    @Test
    public void losingTheLockOnMidRunEndsIt() {
        game.firePpd(firer, ppd, target);
        assertEquals(3, ppd.pulsesRemaining());

        firer.removeLockOn(target);
        nextImpulse();

        assertFalse("the run is over", ppd.isPulsing());
        assertEquals(0, game.activePpdRunCount());
    }

    /** Dropping to passive mid-run ends it for the same reason (E11.152). */
    @Test
    public void droppingToPassiveMidRunEndsIt() {
        game.firePpd(firer, ppd, target);

        firer.setActiveFireControl(false);
        nextImpulse();

        assertFalse(ppd.isPulsing());
    }

    // ------------------------------------------------------------------ E11.23 / E11.312

    /** One firing at a time: a device already pulsing cannot be fired again (E11.23). */
    @Test
    public void aDeviceAlreadyPulsingCannotFireAgain() {
        game.firePpd(firer, ppd, target);

        Game.ActionResult second = game.firePpd(firer, ppd, target);

        assertFalse(second.isSuccess());
        assertTrue(second.getMessage(), second.getMessage().contains("already firing"));
    }

    /** E11.312: not at a true range of three or less, whatever else is true. */
    @Test
    public void itCannotBeFiredInsideRangeFour() {
        target.setLocation(new Location(10, 12));   // two hexes

        Game.ActionResult result = game.firePpd(firer, ppd, target);

        assertFalse(result.isSuccess());
        assertFalse(ppd.isPulsing());
    }
}
