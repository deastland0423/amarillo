package com.sfb.objects.shuttles;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.shuttles.CataloguedFighter;

/**
 * How fast a scatter pack can be filled (J4.8172, FD7.22).
 * <p>
 * Two limits that are easy to conflate: how much a pack HOLDS, which is FD7.21 and belongs
 * to the type it was built from, and how much its deck crews can move in ONE TURN, which is
 * J4.8172 — two crews to a shuttle box, one drone space each. A six-space admin shuttle can
 * therefore hold six and still take three turns to fill, and that is the constraint that
 * makes a scatter pack something a captain plans rather than produces on demand.
 */
public class ScatterPackLoadingTest {

    private static ScatterPack emptyPack() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("Pack-1");
        return pack;
    }

    @Test
    public void anAdminShuttleStillHoldsSixSpacesInTotal() {
        assertEquals("FD7.21, and not the same question as how fast it fills",
                6, emptyPack().getMaxDroneSpaces());
    }

    @Test
    public void onlyTwoSpacesGoAboardInOneTurn() {
        ScatterPack pack = emptyPack();

        assertTrue(pack.addPendingDrone(new Drone(DroneType.TypeI)));
        assertTrue(pack.addPendingDrone(new Drone(DroneType.TypeI)));
        assertFalse("J4.8172: two crews to a box, so two actions, so two spaces",
                pack.addPendingDrone(new Drone(DroneType.TypeI)));
        assertEquals(2.0, pack.getPendingSpaces(), 0.001);
    }

    /**
     * Spaces, not drones. A type-VI is half a space, so a turn's work is four of them — the
     * limit counts what the crews HANDLE, and the rule prices the handling by size.
     */
    @Test
    public void theLimitIsSpacesSoFourDogfightDronesFit() {
        ScatterPack pack = emptyPack();

        for (int i = 0; i < 4; i++)
            assertTrue("type-VI is half a space, so four is two spaces",
                    pack.addPendingDrone(new Drone(DroneType.TypeVI)));
        assertFalse("and a fifth is past the two",
                pack.addPendingDrone(new Drone(DroneType.TypeVI)));
        assertEquals(2.0, pack.getPendingSpaces(), 0.001);
    }

    /** A heavy drone is two spaces, so it is a whole turn's work on its own. */
    @Test
    public void oneHeavyDroneIsAWholeTurn() {
        ScatterPack pack = emptyPack();

        assertTrue(pack.addPendingDrone(new Drone(DroneType.TypeIV)));
        assertFalse("two spaces already spent",
                pack.addPendingDrone(new Drone(DroneType.TypeVI)));
    }

    @Test
    public void theTurnsWorkResetsWhenTheTurnEnds() {
        ScatterPack pack = emptyPack();
        pack.addPendingDrone(new Drone(DroneType.TypeI));
        pack.addPendingDrone(new Drone(DroneType.TypeI));

        pack.applyPendingPayload();   // end of turn

        assertEquals("what was staged is aboard now", 2.0, pack.getPayloadSpaces(), 0.001);
        assertEquals(0.0, pack.getPendingSpaces(), 0.001);
        assertTrue("and the crews get a fresh turn's work",
                pack.addPendingDrone(new Drone(DroneType.TypeI)));
    }

    /** Three turns to fill a six-space pack, and the capacity still stops it at six. */
    @Test
    public void aFullAdminShuttlePackTakesThreeTurns() {
        ScatterPack pack = emptyPack();

        for (int turn = 1; turn <= 3; turn++) {
            assertTrue("turn " + turn, pack.addPendingDrone(new Drone(DroneType.TypeI)));
            assertTrue("turn " + turn, pack.addPendingDrone(new Drone(DroneType.TypeI)));
            pack.applyPendingPayload();
        }

        assertEquals(6.0, pack.getPayloadSpaces(), 0.001);
        assertFalse("FD7.21 caps it at six however many turns are spent",
                pack.addPendingDrone(new Drone(DroneType.TypeI)));
    }

    /** A fighter packs at its own smaller capacity, and the per-turn limit still applies. */
    @Test
    public void aFighterPacksAtItsOwnCapacity() {
        ScatterPack pack = new ScatterPack(CataloguedFighter.of("aas"));
        pack.setName("Pack-2");

        assertEquals("FD7.11: an AAS carries two spaces", 2, pack.getMaxDroneSpaces());
        assertTrue(pack.addPendingDrone(new Drone(DroneType.TypeI)));
        assertTrue(pack.addPendingDrone(new Drone(DroneType.TypeI)));
        assertFalse(pack.addPendingDrone(new Drone(DroneType.TypeI)));
    }
}
