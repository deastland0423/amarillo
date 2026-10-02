package com.sfb.weapons;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;

/**
 * FD3.72, the type-G rack's reloads and the Y175 refit that adds one.
 * <p>
 * "Type-G drone racks have two sets of reloads, one of which is entirely anti-drones and the
 * other of which is identical to whatever is loaded in the rack itself. When the type-G was
 * given a third set of reloads in Y175, that set was identical to the loading of the rack."
 * <p>
 * So a type-G holds, in engine terms, a list of DRONE reload sets plus a count of anti-drone
 * rounds — the ADD set is a count because an anti-drone is not a {@code Drone}. One drone set
 * and eight ADDs before the refit; two drone sets and eight ADDs after it.
 * <p>
 * Both halves were broken, and in a way that looked right from the outside. The refit is
 * written in ship files as an upgrade to TYPE_G carrying {@code extraReloads: 1}, so
 * {@code upgradeRackType} was being called with the type the rack already had — and everything
 * in that method exists to discard ammunition that no longer fits a DIFFERENT rack. It reset
 * the count to the type's base and emptied the loaded sets; {@code addReloadSets} then raised
 * a number without building anything. A refitted rack advertised three reloads and held none,
 * which no test noticed because the advertised figure was the believable one.
 */
public class TypeGReloadRefitTest {

    /** A type-G loaded with four type-I drones, as a Klingon D5E's racks are. */
    private DroneRack loadedTypeG() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_G);
        List<Drone> ammo = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            ammo.add(new Drone(DroneType.TypeI));
        rack.setAmmo(ammo);
        return rack;
    }

    /** FD3.72 before the refit: one drone set, one full set of anti-drones. */
    @Test
    public void aTypeGStartsWithOneDroneSetAndOneOfAntiDrones() {
        DroneRack rack = loadedTypeG();

        assertEquals("one reload set identical to the loading", 1, rack.getReloads().size());
        assertEquals("and a second set that is entirely anti-drones",
                rack.fullAntiDroneSet(), rack.getAddReloads());
        assertEquals(4, rack.getAmmo().size());
    }

    /**
     * The Y175 refit. The new set is "identical to the loading of the rack", so it must be
     * real drones of the loaded type — not merely a larger number.
     */
    @Test
    public void theY175RefitAddsASecondDroneSetMatchingTheLoading() {
        DroneRack rack = loadedTypeG();

        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_G);   // how ship files write it
        rack.addReloadSets(1);

        assertEquals("two drone sets after the refit", 2, rack.getReloads().size());
        assertEquals("the anti-drone set is untouched",
                rack.fullAntiDroneSet(), rack.getAddReloads());
        for (List<Drone> set : rack.getReloads()) {
            assertEquals("each set mirrors the four loaded drones", 4, set.size());
            for (Drone d : set)
                assertEquals(DroneType.TypeI, d.getDroneType());
        }
    }

    /** The count must not outrun the contents — that divergence was the whole bug. */
    @Test
    public void theReportedCountMatchesWhatIsActuallyThere() {
        DroneRack rack = loadedTypeG();
        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_G);
        rack.addReloadSets(1);

        assertEquals("what it says it has is what it has",
                rack.getReloads().size(), rack.getNumberOfReloads());
    }

    /** Upgrading to the SAME type changes nothing at all on its own. */
    @Test
    public void anUpgradeToTheSameTypeIsANoOp() {
        DroneRack rack = loadedTypeG();
        int sets = rack.getReloads().size();
        int adds = rack.getAddReloads();
        int ammo = rack.getAmmo().size();

        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_G);

        assertEquals(sets, rack.getReloads().size());
        assertEquals(adds, rack.getAddReloads());
        assertEquals(ammo, rack.getAmmo().size());
    }

    /**
     * A REAL change of type still discards what no longer fits, which is what that code is
     * for. A type-A has no anti-drone targeting system (FD3.70), so the ADDs go with the refit.
     */
    @Test
    public void arealChangeOfTypeStillClearsWhatNoLongerFits() {
        DroneRack rack = loadedTypeG();
        assertTrue("fixture: it starts with anti-drones aboard", rack.getAddReloads() > 0);

        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_A);

        assertEquals(DroneRack.DroneRackType.TYPE_A, rack.getRackType());
        assertEquals("a type-A cannot fire anti-drones, so they are gone",
                0, rack.getAddReloads());
        assertTrue("and the old reload sets went with the refit", rack.getReloads().isEmpty());
    }

    /** An empty rack takes the count and waits: setAmmo builds the sets when loading arrives. */
    @Test
    public void anUnloadedRackTakesTheCountWithoutInventingDrones() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_G);

        rack.addReloadSets(1);

        assertTrue("nothing loaded, so nothing to mirror", rack.getReloads().isEmpty());
    }
}
