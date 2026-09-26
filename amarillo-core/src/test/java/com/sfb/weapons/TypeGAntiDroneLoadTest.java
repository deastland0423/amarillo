package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack.DroneRackType;

/**
 * Type-G, slice 1: anti-drone rounds are ammunition, and they share the rack's four spaces.
 * <p>
 * FD3.70: "The G rack can carry four spaces of drones, and it is equipped with targeting
 * system for anti-drones (E5.0). Each anti-drone takes 1/2 space." So one magazine holds a
 * mixture, four spaces between them, an anti-drone costing the same half space a dogfight
 * drone does.
 * <p>
 * Anti-drones are deliberately NOT drones in the ammo list. {@link DroneType} carries
 * endurance, speed, hull and self-guidance, none of which an anti-drone round has, and
 * {@link DroneRack#launch(int)} would fly one as a seeker. They are counted separately, with
 * {@link DroneRack#spacesUsed()} the one place that adds the two kinds together.
 * <p>
 * Firing is slice 2; nothing here fires.
 */
public class TypeGAntiDroneLoadTest {

    private static final int Y175 = 175;

    private DroneRack typeG() {
        return new DroneRack(DroneRackType.TYPE_G);
    }

    // ---------------------------------------------------------------- who may carry them

    @Test
    public void onlyATypeGCarriesAntiDrones() {
        assertTrue("FD3.70: the G rack is the one with the targeting system",
                typeG().acceptsAntiDrones());

        assertFalse(new DroneRack(DroneRackType.TYPE_A).acceptsAntiDrones());
        assertFalse("FD3.4 says so outright for the D",
                new DroneRack(DroneRackType.TYPE_D).acceptsAntiDrones());
        assertFalse("a starbase's anti-drones are their own launcher (FD3.86)",
                new DroneRack(DroneRackType.TYPE_H).acceptsAntiDrones());

        assertEquals("and loading one into the wrong rack does nothing",
                0, new DroneRack(DroneRackType.TYPE_A).loadAntiDrones(2, Y175));
    }

    /** FD3.72: "Anti-drones are not available prior to Y140." */
    @Test
    public void thereAreNoAntiDronesBeforeY140() {
        assertEquals(0, typeG().loadAntiDrones(2, 139));
        assertEquals("Y140 is the year they arrive", 2, typeG().loadAntiDrones(2, 140));
    }

    // ---------------------------------------------------------------- the shared magazine

    @Test
    public void anAntiDroneTakesHalfASpace() {
        DroneRack rack = typeG();

        assertEquals(8, rack.loadAntiDrones(8, Y175));

        assertEquals("eight at half a space fills the rack", 4.0, rack.spacesUsed(), 1e-9);
        assertEquals(0.0, rack.spacesFree(), 1e-9);
    }

    @Test
    public void aNinthAntiDroneWillNotFit() {
        DroneRack rack = typeG();
        rack.loadAntiDrones(8, Y175);

        assertEquals("the rack is full", 0, rack.loadAntiDrones(1, Y175));
        assertEquals(8, rack.getAddAmmo());
    }

    /**
     * The point of the slice: one magazine, two kinds of round. FD3.72's own example is a
     * Federation ship on the Klingon front carrying "two, four, or even six anti-drones on
     * the rack" alongside its drones.
     */
    @Test
    public void dronesAndAntiDronesShareTheFourSpaces() {
        DroneRack rack = typeG();
        rack.getAmmo().add(new Drone(DroneType.TypeI));    // one full space
        rack.getAmmo().add(new Drone(DroneType.TypeI));    // two

        assertEquals(2.0, rack.spacesUsed(), 1e-9);
        assertEquals(2.0, rack.spacesFree(), 1e-9);

        assertEquals("four anti-drones fill what is left", 4, rack.loadAntiDrones(4, Y175));
        assertEquals(4.0, rack.spacesUsed(), 1e-9);
        assertEquals("and a fifth does not fit", 0, rack.loadAntiDrones(1, Y175));
    }

    @Test
    public void aDogfightDroneAndAnAntiDroneCostTheSame() {
        DroneRack withDogfight = typeG();
        withDogfight.getAmmo().add(new Drone(DroneType.TypeVI));

        DroneRack withAntiDrone = typeG();
        withAntiDrone.loadAntiDrones(1, Y175);

        assertEquals(withDogfight.spacesUsed(), withAntiDrone.spacesUsed(), 1e-9);
    }

    // ---------------------------------------------------------------- consequences

    /** A rack holding only anti-drones is loaded, whatever the drone list says. */
    @Test
    public void aRackOfAntiDronesIsNotEmpty() {
        DroneRack rack = typeG();
        assertTrue("nothing in it yet", rack.isEmpty());

        rack.loadAntiDrones(1, Y175);

        assertFalse("it has a round in it", rack.isEmpty());
    }

    /** Refit it away from a type-G and the targeting system goes with it. */
    @Test
    public void aRefitAwayFromTypeGLosesTheAntiDrones() {
        DroneRack rack = typeG();
        rack.loadAntiDrones(4, Y175);

        rack.upgradeRackType(DroneRackType.TYPE_B);

        assertEquals(0, rack.getAddAmmo());
        assertFalse(rack.acceptsAntiDrones());
    }
}
