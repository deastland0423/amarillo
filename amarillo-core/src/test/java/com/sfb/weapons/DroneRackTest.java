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

    /**
     * The reload count is a property of the RACK TYPE, not of the ship (owner's reading of
     * the SSDs, 2026-09-25). Type-G is the only type a ship file may add to, because FD3.72
     * gives it a third set at the Y175 refit.
     */
    @Test
    public void typeConstructorSetsSpacesAndReloads() {
        DroneRack typeA = new DroneRack(DroneRackType.TYPE_A);
        assertEquals(4, typeA.getSpaces());
        assertEquals(1, typeA.getNumberOfReloads());

        DroneRack typeB = new DroneRack(DroneRackType.TYPE_B);
        assertEquals(6, typeB.getSpaces());
        assertEquals("a B rack always carries two", 2, typeB.getNumberOfReloads());

        DroneRack typeC = new DroneRack(DroneRackType.TYPE_C);
        assertEquals(4, typeC.getSpaces());
        assertEquals("and so does a C", 2, typeC.getNumberOfReloads());

        DroneRack typeG = new DroneRack(DroneRackType.TYPE_G);
        assertEquals(4, typeG.getSpaces());
        assertEquals("FD3.72: two sets, one of them all anti-drones",
                2, typeG.getNumberOfReloads());

        // FD3.43: "While type-D (and type-H) drone racks do not have formal reloads..." The
        // owner's framing (2026-10-05) is why: a type-D is one launcher with three magazines of
        // four (FD3.4), and "the three magazines represent the reload capacity except all the
        // drones are ready to launch". So the 12 spaces already ARE the reloads, and the two sets
        // this used to assert counted the same drones a second time. FD2.4424 says where its
        // refills come from instead — straight from the cargo boxes, which "are the reload
        // storage for such racks".
        DroneRack typeD = new DroneRack(DroneRackType.TYPE_D);
        assertEquals("three magazines of four (FD3.4)", 12, typeD.getSpaces());
        assertEquals("FD3.43: a type-D has no formal reloads", 0, typeD.getNumberOfReloads());

        DroneRack typeH = new DroneRack(DroneRackType.TYPE_H);
        assertEquals("five magazines (FD3.8)", 20, typeH.getSpaces());
        assertEquals("FD3.43 names the type-H with the type-D", 0, typeH.getNumberOfReloads());
    }

    /**
     * FD3.41 / FD3.81: a magazine launcher draws "one drone from one magazine on each turn", which
     * is one per MAGAZINE and not one per rack — so three for a type-D and five for a type-H,
     * except that "the launcher cannot fire two drones within one-quarter turn even if from
     * different magazines" allows only four launches in 32 impulses.
     *
     * <p>That cap is the argument for the reading. Taken as one drone per rack per turn, the
     * quarter-turn sentence would add nothing to the turn limit it sits beside; taken per magazine
     * it does real work, and the five-magazine type-H is where it bites. Owner confirmed
     * 2026-10-05. Before this, the Kzinti TGT fired a third of its rate.
     *
     * <p>Both counts are stand-ins for the live magazine count and must be derived once FD3.42's
     * per-magazine damage exists — a type-D down to one magazine launches once.
     */
    @Test
    public void aMagazineLauncherFiresOncePerMagazineCappedByTheQuarterTurnGap() {
        DroneRack typeD = new DroneRack(DroneRackType.TYPE_D);
        assertEquals("FD3.41: one per magazine, three magazines", 3, typeD.getMaxShotsPerTurn());
        assertEquals("FD3.0: a quarter turn between launches", 8, typeD.getMinImpulseGap());

        DroneRack typeH = new DroneRack(DroneRackType.TYPE_H);
        assertEquals("FD3.81: five magazines, but the quarter-turn gap allows only four launches"
                + " in a 32-impulse turn", 4, typeH.getMaxShotsPerTurn());
        assertEquals(8, typeH.getMinImpulseGap());

        // The ordinary racks are untouched: a type-A is one launch a turn, and the gap is what
        // every rack already shared.
        DroneRack typeA = new DroneRack(DroneRackType.TYPE_A);
        assertEquals("a type-A launches once a turn", 1, typeA.getMaxShotsPerTurn());
        assertEquals(8, typeA.getMinImpulseGap());
    }

    /** An upgrade re-reads the table, so a refit cannot leave the old type's count behind. */
    @Test
    public void upgradingARackTakesTheNewTypesReloadCount() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_A);
        assertEquals(1, rack.getNumberOfReloads());

        rack.upgradeRackType(DroneRackType.TYPE_B);

        assertEquals(6, rack.getSpaces());
        assertEquals("the B refit brings the B rack's two sets with it",
                2, rack.getNumberOfReloads());
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

        // FD2.42 loads INTO the rack. The fixture holds two drones in a four-space rack,
        // so a two-space load leaves four aboard — it does not replace the two that
        // were already there, which is what the old expectation of "reloadSet.size()"
        // was quietly describing.
        assertEquals("the reloaded drones join the ones aboard",
                4, rack.getAmmo().size());
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
        assertEquals("the reloaded drones join the ones aboard", 4, rack.getAmmo().size());
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
