package com.sfb.weapons;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack.DroneRackType;

/**
 * Loading a rack (FD2.42): two spaces a turn, and for a type-G either kind.
 * <p>
 * "Each rack may load up to 2 spaces per turn" — and a type-G's two spaces can be any mix of
 * drones at a space each and anti-drones at half a space, so a turn buys two drones, or four
 * anti-drones, or one drone and two rounds.
 * <p>
 * Deck crews are NOT what reloading a rack costs (owner's correction 2026-09-26; the server
 * already cited FD2.421/FD7.25 for it). They ARE what loading a SCATTERPACK costs — two
 * spaces of drones a turn for a ship with the usual two crews — which is a different system
 * and correctly built elsewhere.
 * <p>
 * The other half of this class is a bug the correction turned up: loading used to REPLACE
 * the rack's contents. Firing removes drones one at a time, so a half-empty rack topped up
 * by two spaces ended the turn holding only those two, with whatever was still aboard thrown
 * away.
 */
public class TypeGReloadFlowTest {

    private static final int Y175 = 175;

    /** A type-G with two drones aboard, two free spaces, and a reserve of both kinds. */
    private DroneRack loadedTypeG() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_G);
        rack.setAmmo(new ArrayList<>(List.of(
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI))));
        return rack;
    }

    // ---------------------------------------------------------------- loading adds

    @Test
    public void loadingAddsToWhatIsAboardRatherThanReplacingIt() {
        DroneRack rack = loadedTypeG();
        assertEquals(2, rack.getAmmo().size());

        rack.stagePendingReload(new ArrayList<>(List.of(new Drone(DroneType.TypeI))));
        rack.completePendingReload();

        assertEquals("the drone joins the two already there", 3, rack.getAmmo().size());
    }

    /** What will not fit stays in reserve rather than being lost. */
    @Test
    public void whatWillNotFitGoesBackToReserve() {
        DroneRack rack = loadedTypeG();       // two spaces free

        rack.stagePendingReload(new ArrayList<>(List.of(
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI),
                new Drone(DroneType.TypeI))));
        rack.completePendingReload();

        assertEquals("only two fitted", 4, rack.getAmmo().size());
        assertEquals("the rack is full", 4.0, rack.spacesUsed(), 1e-9);
        assertTrue("and the third is still in reserve somewhere",
                rack.getReloads().stream().anyMatch(set -> !set.isEmpty()));
    }

    // ---------------------------------------------------------------- either kind

    @Test
    public void thoseTwoSpacesMayBeSpentOnAntiDrones() {
        DroneRack rack = loadedTypeG();
        int reserveBefore = rack.getAddReloads();

        rack.stagePendingReload(new ArrayList<>(), 4);   // four rounds, two spaces
        rack.completePendingReload();

        assertEquals("four anti-drones aboard", 4, rack.getAddAmmo());
        assertEquals("drawn from the reserve", reserveBefore - 4, rack.getAddReloads());
        assertEquals("and the rack is full", 4.0, rack.spacesUsed(), 1e-9);
    }

    @Test
    public void orOnAMixOfBoth() {
        DroneRack rack = loadedTypeG();

        rack.stagePendingReload(new ArrayList<>(List.of(new Drone(DroneType.TypeI))), 2);
        rack.completePendingReload();

        assertEquals("one drone loaded", 3, rack.getAmmo().size());
        assertEquals("two rounds loaded", 2, rack.getAddAmmo());
        assertEquals("one space plus two halves", 4.0, rack.spacesUsed(), 1e-9);
    }

    /** More rounds than there is room for: the rest stay in reserve. */
    @Test
    public void antiDronesThatWillNotFitStayInReserve() {
        DroneRack rack = loadedTypeG();
        int reserveBefore = rack.getAddReloads();

        rack.stagePendingReload(new ArrayList<>(), 8);   // four spaces' worth, two free

        rack.completePendingReload();

        assertEquals("only what fits is loaded", 4, rack.getAddAmmo());
        assertEquals("the rest is untouched", reserveBefore - 4, rack.getAddReloads());
    }

    /** A rack that is not a type-G has no anti-drone reserve to spend. */
    @Test
    public void anOrdinaryRackLoadsNoAntiDrones() {
        DroneRack typeA = new DroneRack(DroneRackType.TYPE_A);
        typeA.setAmmo(new ArrayList<>(List.of(new Drone(DroneType.TypeI))));

        typeA.stagePendingReload(new ArrayList<>(), 4);
        typeA.completePendingReload();

        assertEquals(0, typeA.getAddAmmo());
    }

    // ---------------------------------------------------------------- still blocks firing

    @Test
    public void aRackBeingLoadedCannotFire() {
        DroneRack rack = loadedTypeG();
        rack.loadAntiDrones(2, Y175);

        rack.stagePendingReload(new ArrayList<>(), 2);

        assertFalse("FD2.42: a rack cannot fire while loading", rack.canFire());
        assertFalse("nor as an anti-drone launcher", rack.canFireAntiDrone());
    }
}
