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
        // What goes is what no longer FITS. The rack itself is replaced, so it arrives loaded
        // with the single reload set a type-A carries (FD3.6: the B-refit has type-F racks
        // "replaced with standard type-A racks"), not stripped of reloads altogether.
        //
        // This line used to assert the opposite, and it was the same fault this class's own
        // header describes: "A refitted rack advertised three reloads and held none, which no
        // test noticed because the advertised figure was the believable one." That was fixed for
        // the type-G's no-op refit by returning early, and left open for every rack that really
        // did change type -- with this assertion written down as though it were the rule.
        assertEquals("a type-A carries one reload set, and it is a real one",
                1, rack.getReloads().size());
        assertEquals("identical to whatever is loaded in the rack (FD3.72)",
                rack.getAmmo().size(), rack.getReloads().get(0).size());
    }

    /** An empty rack takes the count and waits: setAmmo builds the sets when loading arrives. */
    @Test
    public void anUnloadedRackTakesTheCountWithoutInventingDrones() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_G);

        rack.addReloadSets(1);

        assertTrue("nothing loaded, so nothing to mirror", rack.getReloads().isEmpty());
    }

    // ------------------------------------------------------------ a rack that GROWS

    /**
     * The half nobody had written down: a refit that makes the rack BIGGER.
     *
     * <p>FD3.6 calls it a replacement -- Klingon ships with the B-refit "have their type-F drone
     * racks replaced with standard type-A racks (often with type-B after the Y175 refits)" -- and
     * a replacement arrives loaded, as every rack in the game does. The old code only ever
     * trimmed, so a Kzinti BC's Y175 refit turned two four-space racks into six-space type-Bs
     * and left four drones in each: a third of the magazine empty on every drone ship with a
     * rack refit, visible in the Commander's Options dialog as a six-space rack carrying four.
     */
    @Test
    public void aRefitToABiggerRackArrivesFullyLoaded() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        List<Drone> ammo = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            ammo.add(new Drone(DroneType.TypeI));
        rack.setAmmo(ammo);
        assertEquals(4, rack.getSpaces());

        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_B);

        assertEquals("FD3.2: the larger B rack has a capacity of six spaces", 6, rack.getSpaces());
        assertEquals("and it is carrying them", 6, rack.getAmmo().size());
        for (Drone d : rack.getAmmo())
            assertEquals("filled with what it already held (FD2.41)",
                    DroneType.TypeI, d.getDroneType());
    }

    /** And its reload sets are rebuilt to match, at the new size (FD3.72). */
    @Test
    public void theReloadSetsOfAGrownRackMatchItsNewLoading() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        List<Drone> ammo = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            ammo.add(new Drone(DroneType.TypeI));
        rack.setAmmo(ammo);

        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_B);

        assertEquals("a type-B carries two sets", 2, rack.getReloads().size());
        for (List<Drone> set : rack.getReloads())
            assertEquals("each identical to the loading", 6, set.size());
        assertEquals("and the advertised figure agrees with what is held",
                rack.getReloads().size(), rack.getNumberOfReloads());
    }

    /**
     * A rack that holds a MIX keeps it, because FD2.41 leaves the loading to the player -- "Drones
     * may be mixed in drone racks as the players may elect" -- and a refit is not the moment to
     * change their mind for them. The top-up copies what is there.
     */
    @Test
    public void aGrownRackIsToppedUpWithWhatItWasCarrying() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack.setAmmo(new ArrayList<>(List.of(
                new Drone(DroneType.TypeIV), new Drone(DroneType.TypeIM))));

        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_B);

        assertEquals("two spaces of type-IV, then four one-space drones", 5,
                rack.getAmmo().size());
        assertEquals(DroneType.TypeIV, rack.getAmmo().get(0).getDroneType());
        for (int i = 1; i < 5; i++)
            assertEquals("topped up with the type-IM it was carrying",
                    DroneType.TypeIM, rack.getAmmo().get(i).getDroneType());
    }

    /** A rack that SHRINKS still loses what no longer fits, which is what the trim was for. */
    @Test
    public void aRefitToASmallerRackStillLosesTheOverflow() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_B);
        List<Drone> ammo = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            ammo.add(new Drone(DroneType.TypeI));
        rack.setAmmo(ammo);

        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_A);

        assertEquals(4, rack.getSpaces());
        assertEquals("two drones no longer fit", 4, rack.getAmmo().size());
    }
}
