package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.properties.AegisLevel;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * D13.141: the four aegis firings belong to the FORCE, not to each ship on its own.
 *
 * <h2>The rule</h2>
 * "Even with two or more aegis-equipped ships in the scenario, there are only four firings, so
 * those two ships would operate simultaneously (in each case) <b>even if firing at different
 * targets</b>. Thus the first firings of all aegis ships must be announced (simultaneously with
 * non-aegis weapons) and then resolved, then the second (aegis) firing is announced and resolved,
 * and so on."
 *
 * <h2>What it actually takes away</h2>
 * Not secrecy — look-ahead inside one player's own force. An opposing aegis ship never contends,
 * since a ship does not shoot its own seekers, so the two are always engaging different things.
 * But two escorts over one convoy are a real choice, and firing one, watching it land and then
 * deciding the other is precisely the advantage this rule removes. Firing ONE needs nothing: it is
 * the sealed volley every ship already fires together (D13.14).
 *
 * <h2>Which is why the skip had to become an act</h2>
 * D13.142 lets a ship "skip one of the four firings". While nothing sequenced the force, skipping
 * was simply declining to spend one and needed no code. Now the rest of the force waits at this
 * firing until every aegis ship has fired or given it up — so a player who does not want to shoot
 * with one escort has to be able to SAY so, or the other is stalled with no way out.
 */
public class AegisFiringSequenceTest {

    private Game game;
    private Ship escortA;
    private Ship escortB;
    private CataloguedFighter targetA;
    private CataloguedFighter targetB;

    @Before
    public void setUp() {
        game = new Game();

        Player us = new Player();
        us.setName("Alice");
        us.setTeamName("Federation");
        Player them = new Player();
        them.setName("Bob");
        them.setTeamName("Klingon");

        escortA = aegisShip("USS Alpha", new Location(10, 10), us);
        escortB = aegisShip("USS Bravo", new Location(12, 10), us);

        // Something each may legally shoot: a size class 6 fighter, one hex out (D13.21).
        targetA = enemyFighter("HAAS-A", new Location(10, 9), them);
        targetB = enemyFighter("HAAS-B", new Location(12, 9), them);

        game.startTurn();
        game.submitAllocation(escortA, allocationFor(escortA));
        game.submitAllocation(escortB, allocationFor(escortB));
        escortA.setActiveFireControl(true);
        escortB.setActiveFireControl(true);
        escortA.addLockOn(targetA);
        escortA.addLockOn(targetB);
        escortB.addLockOn(targetA);
        escortB.addLockOn(targetB);

        reachDirectFire();
    }

    private Ship aegisShip(String name, Location where, Player owner) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName(name);
        s.setLocation(where);
        s.setFacing(1);
        s.setAegisFitted(AegisLevel.FULL);
        s.setOwner(owner);
        game.getShips().add(s);
        return s;
    }

    private CataloguedFighter enemyFighter(String name, Location where, Player owner) {
        CataloguedFighter f = CataloguedFighter.of("haas");
        f.setName(name);
        f.setLocation(where);
        f.setFacing(13);
        f.setOwner(owner);
        game.getActiveShuttles().add(f);
        return f;
    }

    private Energy allocationFor(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        return e;
    }

    private void reachDirectFire() {
        for (int guard = 0; guard < 24
                && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; guard++)
            game.advancePhase();
        assertEquals("fixture: aegis fires in Direct Fire (D13.11)",
                Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());
    }

    /** One phaser that bears, which is all a pulse needs. */
    private List<Weapon> aPhaserOf(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w.getType() != null && w.getType().startsWith("Phaser") && w.isFunctional())
                return List.of(w);
        fail("fixture: " + ship.getName() + " should carry a phaser");
        return List.of();
    }

    private Game.ActionResult pulse(Ship ship, CataloguedFighter at) {
        return game.fireAegisPulse(ship, at, aPhaserOf(ship));
    }

    // ------------------------------------------------------------------ the sequence

    /** Both ships start on firing 2, firing 1 having gone with the sealed volley (D13.14). */
    @Test
    public void bothShipsStartOnTheSecondFiring() {
        int now = game.getAbsoluteImpulse();
        assertEquals(2, escortA.aegisNextFiring(now));
        assertEquals(2, escortB.aegisNextFiring(now));
        assertEquals("a full system has three extras", 3, escortA.aegisPulsesRemaining(now));
    }

    /**
     * The rule, and the whole point of the slice: Alpha may not run ahead to firing 3 while Bravo
     * is still on firing 2.
     */
    @Test
    public void aShipCannotRunAheadOfItsConsort() {
        assertTrue(pulse(escortA, targetA).isSuccess());        // Alpha takes firing 2

        Game.ActionResult second = pulse(escortA, targetA);

        assertFalse("Alpha would be on firing 3 while Bravo has not taken firing 2",
                second.isSuccess());
        assertTrue("the refusal should name the rule and the ship: " + second.getMessage(),
                second.getMessage().contains("D13.141")
                        && second.getMessage().contains("USS Bravo"));
    }

    /** And once the consort has caught up, it may. */
    @Test
    public void onceTheConsortHasFiredTheNextFiringOpens() {
        assertTrue(pulse(escortA, targetA).isSuccess());        // Alpha, firing 2
        assertTrue(pulse(escortB, targetB).isSuccess());        // Bravo, firing 2

        int now = game.getAbsoluteImpulse();
        assertEquals(3, escortA.aegisNextFiring(now));
        assertEquals(3, escortB.aegisNextFiring(now));
        assertTrue("the force has moved on together", pulse(escortA, targetA).isSuccess());
    }

    /**
     * Different targets make no difference. D13.141 says so in as many words — "even if firing at
     * different targets" — and this is the clause a reader is most likely to assume away, since
     * two ships shooting two drones feel independent.
     */
    @Test
    public void firingAtDifferentTargetsDoesNotFreeTheSequence() {
        assertTrue(pulse(escortA, targetA).isSuccess());

        assertFalse("still the same sequence, different target or not",
                pulse(escortA, targetB).isSuccess());
    }

    /** A lone aegis ship waits for nobody. */
    @Test
    public void oneShipAloneFiresFreely() {
        game.getShips().remove(escortB);

        assertTrue(pulse(escortA, targetA).isSuccess());
        assertTrue(pulse(escortA, targetA).isSuccess());
        assertTrue(pulse(escortA, targetA).isSuccess());
        assertEquals("all three extras spent (D13.14)",
                0, escortA.aegisPulsesRemaining(game.getAbsoluteImpulse()));
    }

    /**
     * An ENEMY aegis ship is not part of this force's sequence. The owner's ruling, and the reason
     * it costs no fidelity: an opposing aegis ship never contends, because a ship does not shoot
     * its own seekers, so the two are always engaging different things and neither has a decision
     * inside the other's firing.
     */
    @Test
    public void anEnemyAegisShipIsNotPartOfTheSequence() {
        Player them = targetA.getOwner();
        Ship theirEscort = new Ship();
        theirEscort.init(KlingonShips.getD7());
        theirEscort.setName("IKV Charlie");
        theirEscort.setLocation(new Location(14, 10));
        theirEscort.setFacing(13);
        theirEscort.setAegisFitted(AegisLevel.FULL);
        theirEscort.setOwner(them);
        game.getShips().add(theirEscort);
        theirEscort.setActiveFireControl(true);

        game.getShips().remove(escortB);        // leave only one ship on our side

        assertTrue("their escort does not hold ours up", pulse(escortA, targetA).isSuccess());
        assertTrue(pulse(escortA, targetA).isSuccess());
    }

    // ---------------------------------------------------------------------- D13.142's skip

    /** A skip clears the ship from the current firing, so the force can move on. */
    @Test
    public void aSkipLetsTheOtherShipProceed() {
        assertTrue(pulse(escortA, targetA).isSuccess());        // Alpha, firing 2
        assertFalse("Bravo is holding Alpha at firing 2", pulse(escortA, targetA).isSuccess());

        assertTrue(game.skipAegisFiring(escortB).isSuccess());

        assertTrue("Bravo gave up firing 2, so Alpha may take firing 3",
                pulse(escortA, targetA).isSuccess());
    }

    /**
     * And the skipped firing is GONE, not banked: D13.142 says it "cannot be made up after the
     * fourth firing". A ship that skips one has two left, not three.
     */
    @Test
    public void aSkippedFiringCannotBeMadeUp() {
        int now = game.getAbsoluteImpulse();
        assertEquals(3, escortB.aegisPulsesRemaining(now));

        assertTrue(game.skipAegisFiring(escortB).isSuccess());

        assertEquals("the opportunity is spent, not saved", 2, escortB.aegisPulsesRemaining(now));
        assertEquals(3, escortB.aegisNextFiring(now));
    }

    /** Nothing left to skip is a refusal rather than a silent no-op. */
    @Test
    public void skippingWithNothingLeftIsRefused() {
        for (int i = 0; i < 3; i++)
            assertTrue(game.skipAegisFiring(escortB).isSuccess());

        Game.ActionResult r = game.skipAegisFiring(escortB);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("D13.142"));
    }

    /**
     * A ship that cannot fire at all does not hold the force up: no aegis, or no fire control
     * operating (D13.23). Otherwise an escort with a shot-out aegis would freeze its consort for
     * the rest of the battle with no way for the player to clear it.
     */
    @Test
    public void aShipThatCannotFireDoesNotBlock() {
        escortB.setActiveFireControl(false);
        assertEquals("fixture: Bravo's aegis is not operating",
                0, escortB.aegisPulsesRemaining(game.getAbsoluteImpulse()));

        assertTrue(pulse(escortA, targetA).isSuccess());
        assertTrue("and it is not waiting on a ship that can never go",
                pulse(escortA, targetA).isSuccess());

        escortB.setActiveFireControl(true);
        escortB.setAegisFitted(AegisLevel.NONE);
        assertTrue(pulse(escortA, targetA).isSuccess());
    }

    /**
     * A limited system takes part in the sequence for the one extra firing it has, then stops
     * blocking (D13.411: two firings, so one extra).
     */
    @Test
    public void aLimitedSystemBlocksOnlyForItsOneExtraFiring() {
        escortB.setAegisFitted(AegisLevel.LIMITED);
        int now = game.getAbsoluteImpulse();
        assertEquals("a limited system has one extra", 1, escortB.aegisPulsesRemaining(now));

        assertTrue(pulse(escortA, targetA).isSuccess());
        assertFalse("Bravo still has firing 2 to take", pulse(escortA, targetA).isSuccess());

        assertTrue(pulse(escortB, targetB).isSuccess());

        assertEquals(0, escortB.aegisPulsesRemaining(now));
        assertTrue("spent out, it holds nobody up", pulse(escortA, targetA).isSuccess());
        assertTrue(pulse(escortA, targetA).isSuccess());
    }
}
