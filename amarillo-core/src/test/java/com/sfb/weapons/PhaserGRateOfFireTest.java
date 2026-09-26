package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.exceptions.WeaponUnarmedException;

/**
 * A gatling phaser fires four times a turn, and may spend all four on one impulse.
 * <p>
 * Owner's ruling 2026-09-26. Worth a test because the code that says so —
 * {@code setMinImpulseGap(0)} — reads like an omission: every other repeating weapon we have
 * sets a gap, and the ADD sets exactly 1 for the express purpose of preventing a second shot
 * in the same impulse. Someone tidying up would "fix" the zero, and nothing would fail.
 * <p>
 * The question came up while chasing a type-G drone rack that reported itself on cooldown
 * with rounds still aboard. That was a genuine bug — canFire() asks a rack about LAUNCHING —
 * and the Ph-G was checked for the same fault. It does not have it: its canFire means what
 * every caller assumes, and {@link Weapon#readyToFireAtTarget()} inherits it unchanged.
 */
public class PhaserGRateOfFireTest {

    private TurnTracker clock;
    private PhaserG phaser;

    @Before
    public void setUp() {
        clock = new TurnTracker();
        phaser = new PhaserG();
        phaser.setClock(clock);
        clock.nextImpulse();
    }

    @Test
    public void fourShotsFitInOneImpulse() throws Exception {
        int impulse = clock.getImpulse();

        for (int shot = 1; shot <= 4; shot++) {
            assertTrue("shot " + shot + " of four, same impulse", phaser.canFire());
            phaser.fire(5);
            assertEquals("the clock has not moved", impulse, clock.getImpulse());
        }

        assertFalse("and the fifth is refused", phaser.canFire());
    }

    @Test
    public void theFifthShotSaysWhyItIsRefused() throws Exception {
        for (int shot = 1; shot <= 4; shot++)
            phaser.fire(5);

        try {
            phaser.fire(5);
            fail("a fifth shot in a turn should be refused");
        } catch (WeaponUnarmedException expected) {
            assertTrue("the refusal used to describe a Ph-1's eight-impulse wait, which a "
                    + "gatling has never had: " + expected.getMessage(),
                    expected.getMessage().contains("all 4"));
        }
    }

    /** The shots belong to the turn, not to the impulse. */
    @Test
    public void theTurnRestoresThem() throws Exception {
        for (int shot = 1; shot <= 4; shot++)
            phaser.fire(5);
        assertFalse(phaser.canFire());

        phaser.cleanUp();

        assertTrue("a new turn, four shots again", phaser.canFire());
    }

    /**
     * J1.3321: a crippled fighter's Ph-G drops to one shot a turn. The cap moves; the
     * absence of a gap does not, since one shot cannot collide with itself.
     */
    @Test
    public void aCrippledFightersGatlingFiresOnce() throws Exception {
        phaser.reduceToPhaserThree();

        assertTrue(phaser.canFire());
        phaser.fire(5);

        assertFalse("reduced to a Ph-3's rate", phaser.canFire());
        assertTrue(phaser.isReducedToPhaserThree());
    }

    /** And the rack's confusion is not this weapon's: both questions agree here. */
    @Test
    public void readinessToFireIsJustCanFire() throws Exception {
        assertEquals(phaser.canFire(), phaser.readyToFireAtTarget());

        phaser.fire(5);
        assertEquals(phaser.canFire(), phaser.readyToFireAtTarget());

        for (int shot = 2; shot <= 4; shot++)
            phaser.fire(5);
        assertFalse(phaser.readyToFireAtTarget());
    }
}
