package com.sfb.weapons;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * G24.1342: a phaser-G blinds a scout channel only when it fires MORE THAN ONCE in one impulse.
 *
 * <p>The full clause: "Weapons which DO blind channels include everything not listed above...
 * <b>If a phaser-G fires more than once in an impulse, it will blind a channel (and will blind a
 * channel every impulse it does so)</b>."
 *
 * <h2>Why the single shot is exempt</h2>
 * G24.1341 exempts the phaser-3, and a phaser-G is a gatling of phaser-3 shots. If one shot blinded,
 * G24.1342's sentence would say nothing that "everything not listed above" had not already said — it
 * earns its place only by marking where a gatling STARTS blinding. Owner confirmed 2026-10-05: "A
 * Phaser-G fired once in an impulse will not blind a channel. So you can, over 4 separate impulses
 * in a turn, fire all the Phaser-G shots without blinding the channel."
 *
 * <h2>What was wrong</h2>
 * {@code PhaserG extends VariableDamageWeapon}, not {@link Phaser3}, so it never picked up that
 * exemption and inherited {@code Weapon}'s default of true — blinding on every single shot. A
 * gatling-armed scout blinded itself for no reason, and since gatlings are deliberately given no
 * inter-shot gap, it could lose four channels in a turn where it should lose none.
 */
public class GatlingBlindingTest {

    private Game game;
    private Ship ship;
    private PhaserG gatling;

    @Before
    public void setUp() {
        game = new Game();
        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Testbed");
        gatling = new PhaserG();
        gatling.setDesignator("G1");
        ship.getWeapons().addWeapon(gatling);
        game.getShips().add(ship);

        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setPhaserCapacitor(6.0);   // a gatling spends capacitor a shot; four plus headroom
        game.submitAllocation(ship, e);
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; guard++)
            game.advancePhase();
    }

    /** Before it fires at all, nothing has happened and nothing is blinded. */
    @Test
    public void anUnfiredGatlingBlindsNothing() {
        assertEquals(0, gatling.getShotsThisImpulse());
        assertFalse(gatling.blindsScoutChannels());
    }

    /** One shot in an impulse: exempt, like the phaser-3 it is built from. */
    @Test
    public void oneShotInAnImpulseDoesNotBlind() throws Exception {
        gatling.fire(5);
        assertEquals(1, gatling.getShotsThisImpulse());
        assertFalse("G24.1342: a single gatling shot is exempt", gatling.blindsScoutChannels());
    }

    /** A second shot in the SAME impulse blinds, and so does every one after it. */
    @Test
    public void asecondShotInTheSameImpulseBlinds() throws Exception {
        gatling.fire(5);
        assertFalse(gatling.blindsScoutChannels());

        gatling.fire(5);
        assertEquals(2, gatling.getShotsThisImpulse());
        assertTrue("G24.1342: more than once in an impulse blinds", gatling.blindsScoutChannels());

        gatling.fire(5);
        assertEquals(3, gatling.getShotsThisImpulse());
        assertTrue("and it stays blinding for the rest of the impulse",
                gatling.blindsScoutChannels());
    }

    /**
     * The whole point of the rule, and the owner's example: four shots across four separate
     * impulses, no blinding at any of them. The counter has to reset on the impulse change by
     * itself, since nothing calls it between shots.
     */
    @Test
    public void allFourShotsAcrossFourImpulsesNeverBlind() throws Exception {
        for (int shot = 1; shot <= 4; shot++) {
            assertTrue("fixture: the gatling should still have shot " + shot, gatling.canFire());
            gatling.fire(5);
            assertEquals("one shot on this impulse only", 1, gatling.getShotsThisImpulse());
            assertFalse("shot " + shot + " of 4, alone in its impulse, must not blind",
                    gatling.blindsScoutChannels());
            if (shot < 4)
                advanceOneImpulse();
        }
        assertEquals("fixture: all four spent", 4, gatling.getShotsThisTurn());
    }

    /** And a gatling that doubled up on an EARLIER impulse is clean again on the next one. */
    @Test
    public void blindingDoesNotCarryIntoTheNextImpulse() throws Exception {
        gatling.fire(5);
        gatling.fire(5);
        assertTrue(gatling.blindsScoutChannels());

        advanceOneImpulse();
        assertEquals("a new impulse, nothing fired on it yet", 0, gatling.getShotsThisImpulse());
        assertFalse("G24.1342 is per impulse, not sticky", gatling.blindsScoutChannels());

        gatling.fire(5);
        assertFalse("and one shot on the new impulse is still exempt",
                gatling.blindsScoutChannels());
    }

    /** The exemption is the gatling's alone — a phaser-1 blinds on its first shot (G24.1342). */
    @Test
    public void aPhaser1BlindsOnItsFirstShot() {
        Phaser1 ph1 = new Phaser1();
        assertTrue("G24.1341 exempts only the phaser-3, not larger phasers",
                ph1.blindsScoutChannels());
        assertFalse("and the phaser-3 is exempt", new Phaser3().blindsScoutChannels());
    }

    private void advanceOneImpulse() {
        int from = game.getAbsoluteImpulse();
        for (int guard = 0; guard < 40 && game.getAbsoluteImpulse() == from; guard++)
            game.advancePhase();
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; guard++)
            game.advancePhase();
    }
}
