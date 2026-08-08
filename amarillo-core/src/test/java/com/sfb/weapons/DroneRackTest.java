package com.sfb.weapons;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.utilities.ArcUtils;
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
        assertEquals(1, typeB.getNumberOfReloads());

        DroneRack typeD = new DroneRack(DroneRackType.TYPE_D);
        assertEquals(12, typeD.getSpaces());
        assertEquals(2, typeD.getNumberOfReloads());
    }

    @Test
    public void typeConstructorSetsFullArcs() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_F);
        assertEquals(ArcUtils.FULL, rack.getArcs());
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

    // --- Reload staging (two-phase reload) ---

    @Test
    public void stagePendingReload_blocksRackFromFiring() {
        DroneRack rack = rackWithReload();
        List<Drone> reloadSet = rack.getReloads().get(0);
        rack.stagePendingReload(reloadSet);
        assertFalse("Rack should be unable to fire while reloading", rack.canFire());
        assertTrue(rack.isReloadingThisTurn());
    }

    @Test
    public void stagePendingReload_doesNotMoveAmmoYet() {
        DroneRack rack = rackWithReload();
        List<Drone> reloadSet = rack.getReloads().get(0);
        int reloadCountBefore = rack.getReloads().size();
        rack.stagePendingReload(reloadSet);
        // Reloads still intact — drones haven't moved yet
        assertEquals(reloadCountBefore, rack.getReloads().size());
        assertEquals(reloadSet, rack.getPendingReloadSet());
    }

    @Test
    public void completePendingReload_functionalRack_movesAmmoAndConsumesReload() {
        DroneRack rack = rackWithReload();
        List<Drone> reloadSet = rack.getReloads().get(0);
        int reloadCountBefore = rack.getReloads().size();
        rack.stagePendingReload(reloadSet);

        rack.completePendingReload(); // 8C — rack survived

        assertEquals("Ammo should now contain the reloaded drones",
                reloadSet.size(), rack.getAmmo().size());
        assertEquals("Reload set should be consumed",
                reloadCountBefore - 1, rack.getReloads().size());
        assertNull("Pending set should be cleared", rack.getPendingReloadSet());
    }

    @Test
    public void completePendingReload_destroyedRack_returnsDronesToReloads() {
        DroneRack rack = rackWithReload();
        List<Drone> reloadSet = rack.getReloads().get(0);
        int reloadCountBefore = rack.getReloads().size();
        rack.stagePendingReload(reloadSet);

        rack.damage(); // rack destroyed during the turn
        rack.completePendingReload(); // 8C — rack did not survive

        assertEquals("Reload set should be returned, not consumed",
                reloadCountBefore, rack.getReloads().size());
        assertTrue("Returned set should be back in reloads",
                rack.getReloads().contains(reloadSet));
        assertNull("Pending set should be cleared", rack.getPendingReloadSet());
    }

    @Test
    public void cleanUp_completesReloadAndClearsFlag() {
        DroneRack rack = rackWithReload();
        List<Drone> reloadSet = rack.getReloads().get(0);
        rack.stagePendingReload(reloadSet);

        rack.cleanUp(); // simulates end-of-turn

        assertFalse("Reloading flag should be cleared after cleanUp", rack.isReloadingThisTurn());
        assertNull("Pending set should be cleared after cleanUp", rack.getPendingReloadSet());
        assertEquals("Ammo should contain reloaded drones", reloadSet.size(), rack.getAmmo().size());
    }

    @Test
    public void completePendingReload_noPendingSet_isNoOp() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_A);
        rack.completePendingReload(); // should not throw
        assertTrue(rack.isEmpty());
    }

    // --- Helpers ---

    private DroneRack rackWithReload() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_A);
        List<Drone> initial = new ArrayList<>();
        initial.add(new Drone(com.sfb.objects.DroneType.TypeI));
        initial.add(new Drone(com.sfb.objects.DroneType.TypeI));
        rack.setAmmo(initial); // also builds reload sets
        return rack;
    }
}
