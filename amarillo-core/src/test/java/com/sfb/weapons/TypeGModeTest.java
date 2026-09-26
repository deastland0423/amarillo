package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.TurnTracker;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack.DroneRackType;
import com.sfb.weapons.DroneRack.RackMode;

/**
 * Type-G, slice 2: the two modes, and the fact that nobody declares them (FD3.71).
 * <p>
 * "The rack can carry a mixture of types and can operate in either of two modes (drone or
 * anti-drone) on a given turn. If fired in the anti-drone mode, it cannot fire normal drones
 * that turn... <b>The decision as to which mode to use is made the first time (each turn) it
 * is fired.</b>"
 * <p>
 * So there is no toggle and no allocation step. The rack is undecided until something leaves
 * it, and that first shot settles the turn. The rates differ sharply: a drone once per turn,
 * an anti-drone once per impulse (owner's ruling 2026-09-25).
 * <p>
 * The two are tied together by one clause — "ADD fire counts as a drone launch event for this
 * purpose" — so anti-drone fire keeps the quarter-turn gap running against the NEXT turn's
 * drone launch, which is the case {@link #firingAntiDronesLateHoldsUpNextTurnsDrone()} pins.
 */
public class TypeGModeTest {

    private static final int Y175 = 175;

    private TurnTracker clock;
    private DroneRack rack;

    @Before
    public void setUp() {
        clock = new TurnTracker();
        rack = new DroneRack(DroneRackType.TYPE_G);
        rack.setClock(clock);
        rack.getAmmo().add(new Drone(DroneType.TypeI));     // one drone, one space
        rack.loadAntiDrones(4, Y175);                        // and four anti-drones, two spaces
        impulses(9);                                         // clear the opening gap
    }

    private void impulses(int n) {
        for (int i = 0; i < n; i++)
            clock.nextImpulse();
    }

    private void endTurn() {
        rack.cleanUp();
    }

    // ---------------------------------------------------------------- undecided

    @Test
    public void aFreshRackWillDoEither() {
        assertEquals(RackMode.UNDECIDED, rack.getModeThisTurn());
        assertTrue("a drone may go", rack.canFire());
        assertTrue("or an anti-drone", rack.canFireAntiDrone());
    }

    // ---------------------------------------------------------------- the first shot decides

    @Test
    public void launchingADroneClosesOffAntiDronesForTheTurn() {
        rack.recordLaunch();

        assertEquals(RackMode.DRONE, rack.getModeThisTurn());
        assertFalse("FD3.71: the mode is settled for the turn", rack.canFireAntiDrone());
    }

    @Test
    public void firingAnAntiDroneClosesOffDronesForTheTurn() {
        assertTrue(rack.recordAntiDroneFire());

        assertEquals(RackMode.ANTI_DRONE, rack.getModeThisTurn());
        assertFalse("\"it cannot fire normal drones that turn\"", rack.canFire());
    }

    @Test
    public void theChoiceIsMadeAfreshEachTurn() {
        rack.recordAntiDroneFire();
        assertEquals(RackMode.ANTI_DRONE, rack.getModeThisTurn());

        endTurn();

        assertEquals("a new turn finds it undecided again",
                RackMode.UNDECIDED, rack.getModeThisTurn());
    }

    // ---------------------------------------------------------------- the rates

    @Test
    public void droneModeLaunchesOnceATurn() {
        assertTrue(rack.canFire());
        rack.recordLaunch();

        impulses(20);
        assertFalse("one drone per turn, however long you wait", rack.canFire());
    }

    @Test
    public void antiDroneModeFiresEveryImpulse() {
        int fired = 0;
        for (int i = 0; i < 4; i++) {
            impulses(1);
            if (rack.recordAntiDroneFire())
                fired++;
        }

        assertEquals("one round per impulse, four impulses running", 4, fired);
        assertEquals("and the rack is out of rounds", 0, rack.getAddAmmo());
    }

    @Test
    public void butNotTwiceInTheSameImpulse() {
        assertTrue(rack.recordAntiDroneFire());
        assertFalse("one per impulse, not two", rack.canFireAntiDrone());

        impulses(1);
        assertTrue("and free again on the next", rack.canFireAntiDrone());
    }

    @Test
    public void anEmptyRackFiresNoAntiDrones() {
        while (rack.recordAntiDroneFire())
            impulses(1);

        assertEquals(0, rack.getAddAmmo());
        assertFalse(rack.canFireAntiDrone());
    }

    // ---------------------------------------------------------------- where they meet

    /**
     * FD3.71: "ADD fire counts as a drone launch event for this purpose." Anti-drone fire is
     * free of the quarter-turn gap among itself, but it keeps that gap running against a
     * drone launch — and the gap spans the turn boundary, so a rack that fired anti-drones to
     * the very end of a turn cannot open the next one with a drone.
     */
    @Test
    public void firingAntiDronesLateHoldsUpNextTurnsDrone() {
        impulses(22);                       // out to the end of the turn
        assertTrue(rack.recordAntiDroneFire());

        endTurn();
        impulses(1);

        assertEquals("undecided, so the mode is not what blocks it",
                RackMode.UNDECIDED, rack.getModeThisTurn());
        assertFalse("the quarter-turn gap is still running from the anti-drone",
                rack.canFire());

        impulses(7);
        assertTrue("eight impulses after that round, the drone may go", rack.canFire());
    }

    /** Anti-drones, by contrast, carry straight across the boundary with no delay. */
    @Test
    public void antiDronesContinueAcrossTheTurnBoundary() {
        impulses(22);
        assertTrue(rack.recordAntiDroneFire());

        endTurn();
        impulses(1);

        assertTrue("FD3.71: it could continue to fire \"with no delay\"",
                rack.canFireAntiDrone());
    }

    // ---------------------------------------------------------------- other racks unaffected

    @Test
    public void anOrdinaryRackHasNoModeAndNoAntiDrones() {
        DroneRack typeA = new DroneRack(DroneRackType.TYPE_A);
        typeA.setClock(clock);
        typeA.getAmmo().add(new Drone(DroneType.TypeI));

        assertFalse(typeA.canFireAntiDrone());
        assertFalse("nothing to fire, so nothing is recorded", typeA.recordAntiDroneFire());

        typeA.recordLaunch();
        assertEquals("its mode is set, but it was never going to do anything else",
                RackMode.DRONE, typeA.getModeThisTurn());
    }
}
