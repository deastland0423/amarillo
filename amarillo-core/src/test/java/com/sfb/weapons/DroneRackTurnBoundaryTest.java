package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack.DroneRackType;

/**
 * The quarter-turn gap between launches does not end when the turn does.
 * <p>
 * FD3.0 states it for every rack — "no drone rack can fire two drones within 1/4 turn of each
 * other, EVEN IF ON DIFFERENT TURNS" — and the rule is repeated for the type-C and type-E,
 * then spelled out for the type-G at FD3.71: the mandatory delay "includes the last firing on
 * one turn and the first firing on the next".
 * <p>
 * Our clock counts impulses absolutely, so the arithmetic in {@link Weapon#canFire()} spanned
 * turns correctly on its own. {@link Weapon#cleanUp()} then defeated it, resetting the
 * timestamp at every turn boundary with a javadoc saying exactly that. For ordinary weapons
 * that reset is right — a phaser fired on impulse 30 must not be locked out of the first
 * eight impulses of the next turn — so the fix belongs to the rack, not to Weapon.
 * <p>
 * The practical effect: a type-C rack (two per turn, twelve-impulse gap) could fire twice at
 * the end of one turn and twice at the start of the next, four launches across a boundary
 * that permits one.
 */
public class DroneRackTurnBoundaryTest {

    private TurnTracker clock;
    private DroneRack rack;

    @Before
    public void setUp() {
        clock = new TurnTracker();
        rack = new DroneRack(DroneRackType.TYPE_A);
        rack.setClock(clock);
        for (int i = 0; i < 4; i++)
            rack.getAmmo().add(new Drone(DroneType.TypeI));
    }

    /** Advance the absolute clock by n impulses. */
    private void impulses(int n) {
        for (int i = 0; i < n; i++)
            clock.nextImpulse();
    }

    /** End of turn: what Game does to every weapon at 8C. */
    private void endTurn() {
        rack.cleanUp();
    }

    // -------------------------------------------------------------------------

    @Test
    public void aRackThatLaunchedOnTheLastImpulseCannotLaunchOnTheFirst() {
        impulses(32);
        assertTrue("premise: a fresh rack may launch", rack.canFire());
        rack.recordLaunch();

        endTurn();
        impulses(1);                      // impulse 1 of the next turn

        assertFalse("FD3.0: the quarter-turn gap holds across the turn boundary",
                rack.canFire());
    }

    /** And it is the gap that blocks it, not the turn: eight impulses on, it may launch. */
    @Test
    public void onceTheGapHasPassedTheRackLaunchesAgain() {
        impulses(32);
        rack.recordLaunch();
        endTurn();

        impulses(7);
        assertFalse("seven impulses is not a quarter turn", rack.canFire());

        impulses(1);
        assertTrue("eight impulses after the launch, the rack is free", rack.canFire());
    }

    /** The per-turn shot counter still resets, or the rack would fire once per game. */
    @Test
    public void theShotCounterStillResetsWithTheTurn() {
        impulses(32);
        rack.recordLaunch();
        assertFalse("a type-A rack launches once per turn", rack.canFire());

        endTurn();
        impulses(8);

        assertTrue("a new turn restores the launch, once the gap has passed",
                rack.canFire());
    }

    /**
     * AN ORDINARY WEAPON IS HELD BY THE SAME GAP, and this test used to assert the opposite.
     *
     * It read: "a phaser carries the same default eight-impulse gap, and nothing in the rules
     * keeps it waiting into the next turn — so the fix had to live on the rack rather than on
     * Weapon." That is backwards. E1.50: "No weapon may be fired twice within a period of
     * one-fourth of a turn... if a specific phaser were fired during Impulse #29 of one turn, it
     * could not be fired again before Impulse #5 of the next turn... It is intended to eliminate
     * the unrealistic tactic of firing a 'full broadside' on Impulse #32 of one turn and then
     * repeating it on Impulse #1 of the next."
     *
     * <p>The rule's worked example is a PHASER — the very case this test licensed. Written on a
     * mistaken reading, it then locked the mistake in: FD3.0 says the same thing again for racks,
     * so giving the rack its own fix looked like the whole job and the general rule stayed
     * missing. Found in a playtest, where a disruptor fired on Turn 1 Impulse 30 was offered
     * again on Turn 2 Impulse 1.
     *
     * <p>{@link FiringRateAcrossTurnsTest} covers E1.50 and E1.52 properly; this stays because
     * this file is where the wrong belief lived, and a corrected test is worth more here than a
     * deleted one.
     */
    @Test
    public void anOrdinaryWeaponIsHeldByTheGapIntoTheNewTurnToo() {
        Phaser1 phaser = new Phaser1();
        phaser.setClock(clock);

        impulses(32);
        phaser.setLastImpulseFired(clock.getImpulse());   // as registerFire would

        phaser.cleanUp();
        impulses(1);

        assertFalse("impulse #32 then impulse #1 is the exact broadside E1.50 forbids",
                phaser.canFire());

        impulses(7);
        assertTrue("eight impulses after firing, it is free again", phaser.canFire());
    }
}
