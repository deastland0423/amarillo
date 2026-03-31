package com.sfb.weapons;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.weapons.DroneRack.DroneRackType;

public class DroneRackTest {

    // --- Construction ---

    @Test
    public void defaultConstructorSetsTypeAndDacLocation() {
        DroneRack rack = new DroneRack();
        assertEquals("drone", rack.getDacHitLocaiton());
        assertEquals("Drone", rack.getType());
    }

    @Test
    public void typeConstructorSetsSpacesAndReloads() {
        DroneRack typeA = new DroneRack(DroneRackType.TYPE_A);
        assertEquals(4, typeA.getSpaces());
        assertEquals(1, typeA.getNumberOfReloads());

        DroneRack typeB = new DroneRack(DroneRackType.TYPE_B);
        assertEquals(6, typeB.getSpaces());
        assertEquals(2, typeB.getNumberOfReloads());

        DroneRack typeD = new DroneRack(DroneRackType.TYPE_D);
        assertEquals(12, typeD.getSpaces());
        assertEquals(2, typeD.getNumberOfReloads());
    }

    @Test
    public void typeConstructorSetsFullArcs() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_F);
        assertEquals(24, rack.getArcs().length);
    }

    // --- Ammo ---

    @Test
    public void newRackIsEmpty() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_A);
        assertTrue(rack.isEmpty());
        assertEquals(0, rack.getAmmo().size());
    }

    @Test
    public void setAmmoAndRetrieve() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_A);
        List<Drone> drones = new ArrayList<>();
        drones.add(new Drone());
        drones.add(new Drone());
        rack.setAmmo(drones);

        assertFalse(rack.isEmpty());
        assertEquals(2, rack.getAmmo().size());
    }

    @Test
    public void launchReturnsDrone() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_A);
        List<Drone> drones = new ArrayList<>();
        drones.add(new Drone());
        rack.setAmmo(drones);

        Drone launched = rack.launch(0);
        assertNotNull(launched);
    }

    // --- Damage ---

    @Test
    public void functionalByDefault() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_A);
        assertTrue(rack.isFunctional());
    }

    @Test
    public void damageRendersNonFunctional() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_A);
        rack.damage();
        assertFalse(rack.isFunctional());
    }

    @Test
    public void repairRestoresFunctionality() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_A);
        rack.damage();
        rack.repair();
        assertTrue(rack.isFunctional());
    }

    // --- Name ---

    @Test
    public void getNameCombinesTypeAndDesignator() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_F);
        rack.setDesignator("Rack 1");
        assertEquals("Drone-Rack 1", rack.getName());
    }
}
