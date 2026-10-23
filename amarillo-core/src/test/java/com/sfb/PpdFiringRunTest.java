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

    // ---- E11.312 end to end: the firer's scanner reaches the wave-lock roll ----

    /**
     * A DAMAGED SCANNER REACHES THE WAVE-LOCK, which is the wiring rather than the weapon.
     *
     * <p>{@code PpdTest} proves the device keeps the two ranges apart when it is handed both.
     * Nothing there proves anyone HANDS it both - and for as long as the PPD existed, nobody
     * did: it resolved outside the volley and computed a bare true range, so a scout's scanner
     * did nothing to a PPD that it does to every other weapon in the game. This is the test
     * that would have caught that, and it belongs at the layer that gates the behaviour.
     *
     * <p>The target sits 38 hexes out, inside the PPD's reach and in its weakest band. With the
     * firer's scanner track damaged to its last box the adjusted range is 47, off the end of
     * the chart, so no pulse can ever score. A resolver passing the true range instead would
     * need a 4 or less, about one pulse in six.
     *
     * <h2>Why FORTY runs, and why the first pulse is asserted separately</h2>
     * Not padding. The first pulse is resolved inside {@code firePpd} and the rest by the phase
     * loop, which are two different call sites, and an earlier draft of this test checked only
     * the state at the END of each run over five runs. That caught the later-pulse site but let
     * the FIRST-pulse one be reverted with nothing failing - a mutation sweep said so. Five runs
     * give the first-pulse mutant five rolls to survive, which it does three times in five.
     * Forty rolls leave it under a thousandth. The passing case is unaffected either way:
     * against a hit number of zero a correct weapon scores nothing on every roll of the dice,
     * so this test is deterministic in the direction that matters and merely thorough in the
     * other.
     */
    @Test
    public void aDamagedScannerIsFeltByTheWaveLock() {
        target.setLocation(new Location(10, 48));   // 38 hexes: in reach, weakest band
        while (firer.getSpecialFunctions().canDamageScanner())
            firer.getSpecialFunctions().damageScanner();
        assertEquals("fixture: the Fed CA scanner track ends at 9", 9, firer.getScanner());

        for (int run = 0; run < 40; run++) {
            ppd.arm(4);
            ppd.arm(4);
            game.firePpd(firer, ppd, target);
            assertFalse("run " + run + ": the FIRST pulse, resolved inside firePpd, may not lock",
                    ppd.isWaveLocked());
            for (int pulse = 0; pulse < 3; pulse++) {
                nextImpulse();
                assertFalse("run " + run + ": a later pulse, resolved by the phase loop, may not lock",
                        ppd.isWaveLocked());
            }
        }
    }

    // ---- E11.49: erratic manoeuvres and the wave-lock's control channel ----

    /**
     * E11.49: "the (C10.512) restriction on guiding seeking weapons does not apply to
     * maintaining the wave-lock."
     *
     * <h2>This was a live bug, not a missing feature</h2>
     * C10.512 is modelled as a control capacity of ZERO under EM, and E11.151 has a pulsing PPD
     * occupying a seeking-weapon channel counted by the same {@code getControlUsed} that
     * {@code checkControlOverflow} tests on every impulse boundary. Nothing exempted the PPD, so
     * a ship going to erratic manoeuvres mid-run had capacity 0 against a used count of 1 and
     * the next boundary dragged its owner into CONTROL_OVERFLOW to shed the channel its own
     * wave-lock was holding - the precise opposite of the rule.
     *
     * <p>The symptom is worth naming because it does not look like a rules bug from the outside:
     * the game simply stops in an interrupt phase and asks the player to give up a seeker they
     * have not got.
     */
    @Test
    public void erraticManeuversDoNotCostAPulsingPpdItsChannel() {
        game.firePpd(firer, ppd, target);
        assertTrue("fixture: a run should be under way", ppd.isPulsing());
        assertEquals("fixture: the run holds a control channel (E11.151)",
                1, firer.getControlUsed());

        int now = game.getAbsoluteImpulse();
        firer.announceEm(true, now);
        firer.applyEmAnnouncement(now);
        assertTrue("fixture: the firer must actually be on EM", firer.isUsingEm());

        assertEquals("EM leaves room for the run's own channel and nothing more",
                1, firer.getControlCapacity());

        // The impulse boundary runs checkControlOverflow(). nextImpulse() asserts its way to
        // DIRECT_FIRE, so a ship queued into CONTROL_OVERFLOW fails here rather than silently.
        nextImpulse();

        assertTrue("E11.49: EM must not end a firing run already under way", ppd.isPulsing());
        assertEquals("and the channel is still held", 1, firer.getControlUsed());
    }

    /**
     * The other half of C10.512 still bites: EM takes a ship's SEEKERS, and only the wave-lock
     * is spared. A capacity of "however many PPDs are pulsing" says both things at once, which
     * is why the fix is a capacity rather than an exception inside the overflow sweep.
     */
    @Test
    public void emStillStripsEverythingThatIsNotTheWaveLock() {
        game.firePpd(firer, ppd, target);

        int now = game.getAbsoluteImpulse();
        firer.announceEm(true, now);
        firer.applyEmAnnouncement(now);

        assertEquals("one pulsing PPD, so room for one channel", 1, firer.getControlCapacity());
        assertEquals("fixture: and that is exactly what the run holds",
                1, firer.getControlUsed());

        // With the run over, EM leaves nothing at all - C10.512 in full.
        ppd.shutDown();
        assertFalse("fixture: the run has ended", ppd.isPulsing());
        assertEquals("no run, no exemption: EM zeroes the ship's channels",
                0, firer.getControlCapacity());
    }

    // ---- E11.475: a cloaked ship may not fire a PPD ----

    /** A cloak-capable ship carrying an armed PPD, locked on and ready to fire. */
    private Ship cloakCapableFirer(PPD device) {
        Ship rom = new Ship();
        rom.init(com.sfb.samples.RomulanShips.getRomKr());
        rom.setName("Cloaked Firer");
        rom.setLocation(new Location(10, 10));
        rom.setFacing(1);
        game.getShips().add(rom);

        device.setDesignator("9");
        rom.getWeapons().addWeapon(device);
        device.arm(4);
        device.arm(4);
        rom.setActiveFireControl(true);
        rom.addLockOn(target);
        return rom;
    }

    /**
     * E11.475: "Ships cannot fire PPDs while cloaked, cloaking, or uncloaking (G13.513)."
     *
     * <h2>A second gate the PPD missed by resolving outside the volley</h2>
     * {@code DamageResolver} asks {@code cloakActionBlock} before any volley; the PPD is routed
     * out of the volley and never met it, so a fully cloaked ship could open a firing run. A
     * probe found it - the first assertion printed {@code success=true} - rather than anyone
     * noticing by reading. That is now two gates missed by the same route, D6.21's scanner
     * adjustment being the other, which is the argument for checking this method against the
     * volley whenever the volley gains a check.
     *
     * <p>All three states, because the rule names all three and
     * {@code CloakingDevice.isRestrictingActions} happens to answer for exactly them: a test
     * that only tried FULLY_CLOAKED would not notice a gate narrowed to it later.
     */
    @Test
    public void aCloakedCloakingOrUncloakingShipCannotFireAPpd() {
        for (com.sfb.systemgroups.CloakingDevice.CloakState state : new
                com.sfb.systemgroups.CloakingDevice.CloakState[] {
                com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED,
                com.sfb.systemgroups.CloakingDevice.CloakState.FADING_OUT,
                com.sfb.systemgroups.CloakingDevice.CloakState.FADING_IN }) {
            PPD hidden = new PPD();
            Ship rom = cloakCapableFirer(hidden);
            rom.getCloakingDevice().setState(state);

            Game.ActionResult r = game.firePpd(rom, hidden, target);
            assertFalse("E11.475: no PPD fire while " + state, r.isSuccess());
            assertFalse("and no run may have been started", hidden.isPulsing());
            assertTrue("the charge is not spent on a refused shot", hidden.isArmed());

            game.getShips().remove(rom);
        }
    }

    // ---- E11.472: the cloak bonus reaches the wave-lock roll ----

    /**
     * E11.472: "Effective range is used for wave-lock; true range is used to determine damage
     * scored (G13.341)." Effective range is the whole of D6.21 + D6.123 + D6.211, so a cloak
     * pushes a PPD away exactly as it pushes away everything else.
     *
     * <h2>Why this needed its own test rather than trusting the call</h2>
     * The PPD computed only D6.21's scanner term, which made it the one weapon in the game a
     * cloak did not move. Nothing failed when it did: a wave-lock is a dice roll, and a weapon
     * rolling at the wrong range still hits most of the time.
     *
     * <h2>The construction, which is the same lever as the scanner test</h2>
     * True range 38 sits in the PPD's weakest band at a hit number of four. A fully cloaked
     * target adds D6.211's five, carrying the effective range to 43 — off the end of the chart,
     * hit number zero, unwinnable on every roll of the dice. Drop the cloak term and the same
     * shot needs a four or less and lands about one time in six, which forty runs turn into a
     * certainty. Deterministic where it passes, overwhelming where it should fail.
     *
     * <p>Only the FIRST pulse of each run is examined, deliberately: it is resolved inside
     * {@code firePpd}, immediately after the lock-on is granted, which keeps the test clear of
     * everything a cloak does to lock-on retention over later impulses. That belongs to G13 and
     * has its own tests.
     */
    @Test
    public void aCloakPushesAPpdAwayAsItPushesAwayEverythingElse() {
        Ship romTarget = new Ship();
        romTarget.init(com.sfb.samples.RomulanShips.getRomKr());
        romTarget.setName("Ghost");
        romTarget.setLocation(new Location(10, 48));   // true range 38
        romTarget.setFacing(1);
        game.getShips().add(romTarget);
        romTarget.getCloakingDevice().setState(
                com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED);

        assertEquals("fixture: the scanner must contribute nothing here", 0, firer.getScanner());

        // E11.472 fires at a cloaked ship WITH a lock-on, and the lock matters to the sum:
        // without it D6.123 doubles the true range first and the effective range is 81, not 43.
        // Asserting before granting the lock is how this test first read 81, which is a fair
        // demonstration that all three terms are live.
        firer.addLockOn(romTarget);
        assertEquals("fixture: locked on, so 38 + 5 and no doubling",
                43, game.getEffectiveRange(firer, romTarget));

        for (int run = 0; run < 40; run++) {
            firer.addLockOn(romTarget);
            ppd.arm(4);
            ppd.arm(4);
            game.firePpd(firer, ppd, romTarget);
            assertFalse("run " + run + ": effective range 43 cannot win a wave-lock",
                    ppd.isWaveLocked());
            ppd.shutDown();
        }
    }

    /**
     * E11.472: "the damage is then reduced by (G13.37) ... Any reduction is taken first from
     * the splash elements."
     *
     * <h2>The ordering is not cosmetic</h2>
     * At a true range of ten a pulse is 4 on the facing shield and 1 on each neighbour. A
     * half-damage roll removes three points. Taken from the splash first, both neighbours are
     * spared entirely and the centre keeps 3. Taken proportionally, or from the centre first,
     * the same pulse lands on different shields and can breach one that should have held.
     *
     * <h2>Deterministic despite a random die, by asserting the invariant instead of a value</h2>
     * G13.37 rolls one die per pulse and every outcome is allowed, so the test cannot name the
     * damage. What it CAN name is the shape every outcome must have: either nothing was taken
     * and all three shields carry the chart's 4+1+1, or something was taken and the neighbours
     * went first — so a spread that still has three entries must total six. An implementation
     * that drained proportionally, or from the centre, breaks that for every die but 1 and 2.
     *
     * <p>Also exercises E11.472's two ranges at once: effective range 15 (true 10 plus the
     * cloak's five) wins the wave-lock on an eight, while the damage comes off true range 10.
     */
    @Test
    public void g13_37ReductionComesOffTheSplashBeforeTheCentre() {
        Ship romTarget = new Ship();
        romTarget.init(com.sfb.samples.RomulanShips.getRomKr());
        romTarget.setName("Ghost");
        romTarget.setLocation(new Location(10, 20));   // true range 10
        romTarget.setFacing(1);
        game.getShips().add(romTarget);
        romTarget.getCloakingDevice().setState(
                com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED);
        firer.addLockOn(romTarget);

        assertEquals("fixture: true 10 plus the cloak's five", 15,
                game.getEffectiveRange(firer, romTarget));

        int full = 0;
        int reduced = 0;
        for (int attempt = 0; attempt < 400 && (full == 0 || reduced == 0); attempt++) {
            // The pending list is an unmodifiable view, so the newest volley is read by
            // index rather than the list being cleared between attempts.
            int before = game.getPendingVolleys().size();
            firer.addLockOn(romTarget);
            ppd.arm(4);
            ppd.arm(4);
            game.firePpd(firer, ppd, romTarget);
            ppd.shutDown();

            if (game.getPendingVolleys().size() == before)
                continue;   // no wave-lock this pulse

            java.util.Map<Integer, Integer> spread =
                    game.getPendingVolleys().get(game.getPendingVolleys().size() - 1).shieldDamage;
            int total = spread.values().stream().mapToInt(Integer::intValue).sum();

            if (spread.size() == 3) {
                assertEquals("three shields still carrying damage means nothing was taken, "
                        + "so the chart's 4+1+1 must be intact: " + spread, 6, total);
                full++;
            } else {
                assertTrue("a reduced pulse keeps only the facing shield, the splash having "
                        + "gone first: " + spread, spread.size() == 1);
                assertTrue("and the centre keeps less than the chart's four: " + spread,
                        total < 4);
                reduced++;
            }
        }
        assertTrue("fixture: never saw an unreduced pulse in 400 tries", full > 0);
        assertTrue("fixture: never saw a reduced pulse in 400 tries", reduced > 0);
    }

    // ---- E11.473: a fully cloaked target cannot be HELD ----

    /**
     * E11.473: "The PPD cannot hold a wave-lock on a fully cloaked ship. The PPD must roll for
     * a new wave-lock on each pulse even if a wave-lock was achieved on a previous pulse. This
     * is an exception to (E11.331)."
     *
     * <h2>Deterministic in BOTH directions, which took arranging</h2>
     * "It rolls again" is awkward to assert, because a fresh roll often succeeds and looks
     * exactly like a lock that was held. So the second pulse is made IMPOSSIBLE to win: the
     * firer's scanner track is damaged to its last box and the target put at a true range of
     * 38, which carries the adjusted range to 47 — off the end of the chart, hit number zero.
     *
     * <ul>
     * <li>A held lock (E11.331, the old behaviour) scores without rolling, so the lock survives
     *     and {@code isWaveLocked()} stays true.</li>
     * <li>E11.473 drops it, the roll happens, the roll cannot win, and the lock is gone.</li>
     * </ul>
     * No dice decide which, so the test neither flakes nor passes by luck.
     */
    @Test
    public void aFullyCloakedTargetCannotBeHeldInAWaveLock() {
        Ship romTarget = new Ship();
        romTarget.init(com.sfb.samples.RomulanShips.getRomKr());
        romTarget.setName("Fading Hawk");
        romTarget.setLocation(new Location(10, 20));   // 10 hexes: the PPD's best band
        romTarget.setFacing(1);
        game.getShips().add(romTarget);
        firer.addLockOn(romTarget);

        // Get a wave-lock the easy way, at a hit number of 9.
        for (int attempt = 0; attempt < 50 && !ppd.isWaveLocked(); attempt++) {
            if (!ppd.isPulsing()) {
                ppd.arm(4);
                ppd.arm(4);
                game.firePpd(firer, ppd, romTarget);
            } else {
                nextImpulse();
            }
        }
        assertTrue("fixture: 50 runs without a wave-lock", ppd.isWaveLocked());
        assertTrue("fixture: pulses must remain for the test to mean anything", ppd.isPulsing());

        // Now make the next pulse unwinnable, and the target fully cloaked.
        while (firer.getSpecialFunctions().canDamageScanner())
            firer.getSpecialFunctions().damageScanner();
        romTarget.setLocation(new Location(10, 48));   // true 38, adjusted 47: off the chart
        romTarget.getCloakingDevice().setState(
                com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED);
        firer.addLockOn(romTarget);                    // E11.472: fired at with a lock-on

        nextImpulse();

        assertFalse("E11.473: the lock may not be held against a fully cloaked ship",
                ppd.isWaveLocked());
    }

    /**
     * And the exception is scoped to FULLY cloaked. E11.471 lets a PPD "maintain a wave-lock on
     * a ship that is cloaking until the end of the fade period", so a target part-way through
     * its fade keeps the lock that holds it — the same unwinnable pulse that proves the lock is
     * dropped above must leave it standing here.
     */
    @Test
    public void aFadingTargetKeepsTheWaveLockThatHoldsIt() {
        Ship romTarget = new Ship();
        romTarget.init(com.sfb.samples.RomulanShips.getRomKr());
        romTarget.setName("Fading Hawk");
        romTarget.setLocation(new Location(10, 20));
        romTarget.setFacing(1);
        game.getShips().add(romTarget);
        firer.addLockOn(romTarget);

        for (int attempt = 0; attempt < 50 && !ppd.isWaveLocked(); attempt++) {
            if (!ppd.isPulsing()) {
                ppd.arm(4);
                ppd.arm(4);
                game.firePpd(firer, ppd, romTarget);
            } else {
                nextImpulse();
            }
        }
        assertTrue("fixture: 50 runs without a wave-lock", ppd.isWaveLocked());
        assertTrue("fixture: pulses must remain", ppd.isPulsing());

        while (firer.getSpecialFunctions().canDamageScanner())
            firer.getSpecialFunctions().damageScanner();
        romTarget.setLocation(new Location(10, 48));
        romTarget.getCloakingDevice().setState(
                com.sfb.systemgroups.CloakingDevice.CloakState.FADING_OUT);
        firer.addLockOn(romTarget);

        nextImpulse();

        assertTrue("E11.471: a fade does not cost the lock its persistence",
                ppd.isWaveLocked());
    }

    /** With the cloak INACTIVE the same ship fires normally, so the gate is the cloak. */
    @Test
    public void anUncloakedShipFiresItsPpdNormally() {
        PPD open = new PPD();
        Ship rom = cloakCapableFirer(open);
        rom.getCloakingDevice().setState(
                com.sfb.systemgroups.CloakingDevice.CloakState.INACTIVE);

        assertTrue("a ship with its cloak off fires normally",
                game.firePpd(rom, open, target).isSuccess());
        assertTrue("and the run is under way", open.isPulsing());
    }
}
