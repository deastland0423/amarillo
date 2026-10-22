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

    // ------------------------------------------------------------------ E11.151

    /**
     * "Each active PPD (those still pulsing) counts against the limit of seeking weapons being
     * controlled by the ship (F3.216)... A PPD is not, in any way, a seeking weapon other than
     * requiring a seeking weapon control channel."
     *
     * <p>A real cost of firing: a heavy ISC hull pulsing four devices has four fewer channels
     * for drones, which is the whole point of the clause.
     */
    @Test
    public void apulsingPpdHoldsAControlChannel() {
        int before = firer.getControlUsed();

        game.firePpd(firer, ppd, target);

        assertEquals("the run holds a channel while it lasts", before + 1, firer.getControlUsed());
    }

    /** And gives it back when the run ends, without anything having to remember to. */
    @Test
    public void thechannelComesBackWhenTheRunEnds() {
        int before = firer.getControlUsed();
        game.firePpd(firer, ppd, target);

        nextImpulse();
        nextImpulse();
        nextImpulse();

        assertFalse("fixture: the run should be over", ppd.isPulsing());
        assertEquals(before, firer.getControlUsed());
    }

    /** Fill the hull's channels with seeking weapons it is guiding. */
    private java.util.List<com.sfb.objects.Drone> fillChannelsWithSeekers() {
        java.util.List<com.sfb.objects.Drone> drones = new java.util.ArrayList<>();
        while (firer.getControlUsed() < firer.getControlCapacity()) {
            com.sfb.objects.Drone drone = new com.sfb.objects.Drone();
            drone.setName("Drone " + drones.size());
            assertTrue("fixture: the hull should take another seeker",
                    firer.acquireControl(drone));
            drones.add(drone);
        }
        return drones;
    }

    /**
     * A device cannot be fired with every channel already spoken for.
     *
     * <p>Refused rather than allowed to overflow — and NOT because there is nothing to give up.
     * A ship guiding six plasma torpedoes has six things it could give up. It is refused
     * because the overflow procedure is F3.532's INVOLUNTARY transfer, which would make the
     * voluntary act of firing destroy a weapon the player never chose to let go.
     */
    @Test
    public void itCannotBeFiredWithNoChannelFree() {
        fillChannelsWithSeekers();

        Game.ActionResult result = game.firePpd(firer, ppd, target);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("E11.151"));
        assertTrue("and it says what to do about it: " + result.getMessage(),
                result.getMessage().contains("Release or transfer"));
    }

    /**
     * THE WAY OUT. Making room is the player's decision, and once they have made it the device
     * fires. Without this the clause would be a dead end for any ship already guiding a full
     * load of seeking weapons — which, for an ISC hull throwing plasma, is the normal case.
     */
    @Test
    public void releasingASeekerMakesRoomForThePpd() {
        java.util.List<com.sfb.objects.Drone> seekers = fillChannelsWithSeekers();
        assertFalse("fixture: no room to start with",
                game.firePpd(firer, ppd, target).isSuccess());

        firer.releaseControl(seekers.get(0));

        assertTrue("with a channel freed, the device fires",
                game.firePpd(firer, ppd, target).isSuccess());
        assertTrue(ppd.isPulsing());
    }

    // ------------------------------------------------------------------ E11.546

    /**
     * "The line of fire is blocked by a planet (P2.32) or other major object... In this case
     * the wave locks onto the object and will damage it (if it can be damaged in the scenario
     * rules) unless firing is stopped."
     *
     * <p>NOT a break. E11.54's other clauses end the run; this one drags it onto the
     * obstruction and keeps pulsing. The beam has not lost anything — it has found something
     * nearer, and the only way out is to shut the device down (E11.362).
     */
    @Test
    public void aplanetInTheWayDragsTheWaveLockOntoItself() {
        game.firePpd(firer, ppd, target);
        assertEquals("fixture: a run is under way", 3, ppd.pulsesRemaining());

        // A planet squarely between them: firer at (10,10), target at (10,20).
        com.sfb.objects.Terrain planet = new com.sfb.objects.Terrain(
                com.sfb.properties.TerrainType.PLANET, 10, 15, 1);
        game.addTerrain(planet);

        nextImpulse();

        assertTrue("the run continues against the obstruction, not against nothing",
                ppd.isPulsing() || planet.getTotalDamage() > 0);
        assertEquals("and the game is still tracking it", 1, game.activePpdRunCount());
    }

    /** A scoring pulse damages the body it was dragged onto — centre element only (E11.353). */
    @Test
    public void adraggedRunDamagesThePlanet() {
        com.sfb.objects.Terrain planet = new com.sfb.objects.Terrain(
                com.sfb.properties.TerrainType.PLANET, 10, 15, 1);

        for (int attempt = 0; attempt < 60 && planet.getTotalDamage() == 0; attempt++) {
            if (!ppd.isPulsing()) {
                // The lock-on has to be re-established each time, because the planet blocks
                // line of sight and LockOnResolver drops the lock for it (P2.322). Without
                // this the loop cannot retry at all: every firePpd after the first is refused
                // for want of a lock-on, and the whole test rides on one run's dice.
                firer.addLockOn(target);
                ppd.arm(4);
                ppd.arm(4);
                assertTrue("fixture: the device should fire",
                        game.firePpd(firer, ppd, target).isSuccess());
                if (game.getTerrain().isEmpty())
                    game.addTerrain(planet);
            } else {
                nextImpulse();
            }
        }

        assertTrue("fixture: 50 runs without the planet taking a pulse",
                planet.getTotalDamage() > 0);
        assertEquals("a planet ignores the splash and takes the centre element (E11.353)",
                4, planet.getTotalDamage() % 100);
    }

    /** Shutting down is the stated way out of an involuntary transfer (E11.546, E11.362). */
    @Test
    public void theWayOutOfADraggedRunIsToStopFiring() {
        game.firePpd(firer, ppd, target);
        game.addTerrain(new com.sfb.objects.Terrain(
                com.sfb.properties.TerrainType.PLANET, 10, 15, 1));
        nextImpulse();

        ppd.shutDown();

        assertFalse(ppd.isPulsing());
    }
}
