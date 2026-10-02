package com.sfb;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.AegisLevel;
import com.sfb.properties.Location;
import com.sfb.weapons.Weapon;

/**
 * Aegis slice two: the extra firings (D13.13, D13.14, D13.142, D13.22, D13.143).
 * <p>
 * The first of the four firings is the ordinary volley every ship already takes alongside
 * everybody else's fire (D13.14), so what is built here is only the EXTRA ones — three for a
 * full system, one for a limited one. They resolve as they are ordered rather than through the
 * sealed declaration round, which is exactly what D13.11 describes: aegis "can fire weapons
 * individually, judge the results, and then fire more, all on the same step of the same
 * impulse". That is safe precisely because D13.21 confines aegis to size class 6 and smaller,
 * so every legal target is a seeker or shuttle with no shields to reinforce, no DAC choice, and
 * no decision for its owner to make.
 * <p>
 * NOT REACHABLE IN PLAY YET: there is no server action and no UI, so this is core only. Said
 * plainly because a feature that passes its tests and cannot be used has caught this project
 * twice before.
 */
public class AegisPulseTest {

    private Game game;
    private Ship escort;
    private Drone drone;

    @Before
    public void setUp() throws Exception {
        com.sfb.objects.ShuttleCatalog.loadDefault("../data");
        game = new Game();

        Player kzin = new Player();
        kzin.setTeamName("Kzinti");

        escort = new Ship();
        escort.init(com.sfb.samples.KzintiShips.getKzinBC());
        escort.setName("KHS Guardian");
        escort.setLocation(new Location(10, 10));
        escort.setFacing(1);
        escort.setOwner(kzin);
        escort.setActiveFireControl(true);
        escort.setAegisFitted(AegisLevel.FULL);
        game.getShips().add(escort);

        // The clock does not run until allocation is in, and phasers will not fire without
        // capacitor energy behind them — without this the impulse sits at zero and every
        // shot reports "no capacitor energy", which is how the first version of this fixture
        // quietly tested nothing.
        game.startTurn();
        com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
        e.setLifeSupport(escort.getLifeSupportCost());
        e.setFireControl(escort.getFireControlCost());
        e.setPhaserCapacitor(6.0);   // enough charge for several shots, as PlanetBombardmentTest does
        game.submitAllocation(escort, e);

        // The drone is placed AFTER the clock reaches Direct Fire, not before: advancing runs
        // the movement phase, which moves seekers — one set up earlier walks out from under
        // its own lock-on before the test begins.
        toDirectFire();

        drone = new Drone(DroneType.TypeI);
        drone.setName("Incoming-1");
        drone.setLocation(new Location(10, 8));     // two hexes ahead, well inside six
        game.getSeekers().add(drone);
        escort.addLockOn(drone);
    }

    /** Re-seat the drone beside the escort after the clock has moved on. */
    private void reseatDrone() {
        drone.setLocation(new Location(10, 8));
        escort.addLockOn(drone);
        escort.setActiveFireControl(true);   // a new turn would have dropped it
    }

    private void toDirectFire() {
        for (int i = 0; i < 400; i++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.DIRECT_FIRE)
                return;
            game.advancePhase();
        }
        fail("never reached a Direct Fire phase");
    }

    /**
     * The nth phaser-3, as a one-weapon volley.
     * <p>
     * A different weapon per firing on purpose. D13.143 is explicit that aegis gives a weapon
     * more OPPORTUNITIES and not a higher rate, so a phaser that has fired is done for the
     * turn whether or not aegis is involved — reusing one would test the rate limit rather
     * than the pulse budget. An escort would use its anti-drone racks here; the sample BC has
     * none, and D13.22 lets aegis control any direct-fire weapon in any case.
     */
    private List<Weapon> phaser(int n) {
        List<Weapon> all = escort.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.Phaser3)
                .map(w -> (Weapon) w)
                .toList();
        assertTrue("fixture: the BC should carry several phaser-3s", all.size() > n);
        return List.of(all.get(n));
    }

    private int pulsesFired = 0;

    /**
     * One extra firing: a fresh weapon at a fresh drone.
     * <p>
     * Fresh BOTH, and the drone is the subtle one. A phaser that hits destroys a type-I drone
     * outright, so firing every pulse at the same target made the budget tests depend on
     * MISSING — they passed alone and failed in the full suite the first time the dice went
     * the other way. A test of how many firings a ship gets must not also be a test of its
     * marksmanship.
     */
    private Game.ActionResult pulse() {
        return game.fireAegisPulse(escort, freshDrone(), phaser(pulsesFired++));
    }

    private int droneSerial = 0;

    private Drone freshDrone() {
        Drone d = new Drone(DroneType.TypeI);
        d.setName("Incoming-" + (++droneSerial));
        d.setLocation(new Location(10, 8));
        game.getSeekers().add(d);
        escort.addLockOn(d);
        return d;
    }

    // ---------------------------------------------------------------- the budget

    /** D13.14 less the first firing: a full system has three EXTRA, a limited one has one. */
    @Test
    public void theExtraFiringsAreThreeForFullAndOneForLimited() {
        int now = game.getAbsoluteImpulse();
        assertEquals(3, escort.aegisPulsesRemaining(now));

        escort.setAegisFitted(AegisLevel.LIMITED);
        assertEquals(1, escort.aegisPulsesRemaining(now));

        escort.setAegisFitted(AegisLevel.NONE);
        assertEquals(0, escort.aegisPulsesRemaining(now));
    }

    /** D13.142: having spent them, a ship "cannot make it up after the fourth firing". */
    @Test
    public void theFourthFiringIsTheLast() {
        assertTrue(pulse().isSuccess());
        assertTrue(pulse().isSuccess());
        assertTrue(pulse().isSuccess());

        Game.ActionResult fourth = pulse();

        assertFalse(fourth.isSuccess());
        assertTrue(fourth.getMessage(), fourth.getMessage().contains("D13.142"));
        assertEquals(0, escort.aegisPulsesRemaining(game.getAbsoluteImpulse()));
    }

    /** And the budget is per impulse, so it comes back on the next one. */
    @Test
    public void theBudgetRefillsEachImpulse() {
        pulse(); pulse(); pulse();
        assertEquals(0, escort.aegisPulsesRemaining(game.getAbsoluteImpulse()));

        int wasImpulse = game.getAbsoluteImpulse();
        for (int i = 0; i < 20 && game.getAbsoluteImpulse() == wasImpulse; i++)
            game.advancePhase();
        toDirectFire();
        reseatDrone();

        assertEquals("a fresh impulse, a fresh three", 3,
                escort.aegisPulsesRemaining(game.getAbsoluteImpulse()));
    }

    /** D13.142 again: skipping is simply not spending, and costs nothing. */
    @Test
    public void skippingAFiringIsFree() {
        assertEquals(3, escort.aegisPulsesRemaining(game.getAbsoluteImpulse()));
        // ... the player does nothing at all ...
        assertEquals("not firing has spent nothing", 3,
                escort.aegisPulsesRemaining(game.getAbsoluteImpulse()));
        assertTrue(pulse().isSuccess());
        assertEquals(2, escort.aegisPulsesRemaining(game.getAbsoluteImpulse()));
    }

    // ---------------------------------------------------------------- the gates

    @Test
    public void aShipWithoutAegisGetsNoExtraFirings() {
        escort.setAegisFitted(AegisLevel.NONE);

        Game.ActionResult r = pulse();

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("D13.23"));
    }

    /** D13.524: no active fire control, no aegis — whatever is fitted. */
    @Test
    public void withoutActiveFireControlThereAreNoFirings() {
        escort.setActiveFireControl(false);

        assertEquals(0, escort.aegisPulsesRemaining(game.getAbsoluteImpulse()));
        assertFalse(pulse().isSuccess());
    }

    /** D13.21: a ship is not a legal aegis target however close it is. */
    @Test
    public void aegisWillNotFireAtAShip() {
        Ship cruiser = new Ship();
        cruiser.init(com.sfb.samples.KlingonShips.getD7());
        cruiser.setName("IKV Saber");
        cruiser.setLocation(new Location(10, 9));
        game.getShips().add(cruiser);
        escort.addLockOn(cruiser);

        Game.ActionResult r = game.fireAegisPulse(escort, cruiser, phaser(0));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("D13.21"));
    }

    @Test
    public void aegisOnlyFiresInTheDirectFirePhase() {
        game.advancePhase();   // out of DIRECT_FIRE
        assertNotEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());

        Game.ActionResult r = pulse();

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("Direct Fire"));
    }

    // ---------------------------------------------------------------- D13.22

    /**
     * "Any non-aegis use of a given weapon cannot take place on the same impulse as the weapon
     * is fired under aegis control."
     * <p>
     * The rule's own example is a phaser-G, and that is the case that matters: with four shots
     * a turn its ordinary rate limit would happily let it fire again in the same impulse, so
     * this is the only thing stopping it. A once-per-turn weapon never gets near the test.
     */
    @Test
    public void aWeaponCannotFireBothWaysInOneImpulse() {
        List<Weapon> gun = phaser(0);

        // An ordinary shot first — the sealed volley's pulse one. At its OWN drone, because
        // a hit destroys the thing: D13.22 is about the weapon, not the target, so the aegis
        // attempt must have something alive to aim at or it fails the wrong test.
        game.fireWeapons(escort, freshDrone(), gun, 2, 2, 0);

        Game.ActionResult r = game.fireAegisPulse(escort, freshDrone(), gun);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("D13.22"));
    }

    /** And the bar lifts with the impulse, not with the turn. */
    @Test
    public void theExclusivityIsPerImpulse() {
        List<Weapon> gun = phaser(0);
        game.fireWeapons(escort, freshDrone(), gun, 2, 2, 0);

        int wasImpulse = game.getAbsoluteImpulse();
        for (int i = 0; i < 20 && game.getAbsoluteImpulse() == wasImpulse; i++)
            game.advancePhase();
        toDirectFire();
        escort.setActiveFireControl(true);   // a new turn would have dropped it

        Game.ActionResult r = game.fireAegisPulse(escort, freshDrone(), gun);

        assertFalse("a new impulse, so D13.22 no longer bars it: " + r.getMessage(),
                r.getMessage().contains("D13.22"));
    }
}
