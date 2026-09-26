package com.sfb.weapons;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack.DroneRackType;

/**
 * Type-G, slice 5: what a type-G carries in reserve (FD3.72).
 * <p>
 * "Type-G drone racks have two sets of reloads, <b>one of which is entirely anti-drones</b>
 * and the other of which is identical to whatever is loaded in the rack itself. When the
 * type-G was given a third set of reloads in Y175, that set was identical to the loading of
 * the rack."
 * <p>
 * Every other rack reloads with copies of its own load, which is what setAmmo built for all
 * of them. A type-G does not: one of its sets is a full magazine of anti-drone rounds, and it
 * is the set that matters, because FD3.70 is explicit that a type-G does NOT reload
 * anti-drones automatically the way an ADD rack does (E5.74). Run the rack dry and that
 * reserve is the only way to shoot again.
 * <p>
 * The reserve is counted in rounds rather than held as a list, for the same reason an
 * anti-drone is not in the ammo list: it is not a Drone, and nothing should be able to launch
 * one as a seeker.
 */
public class TypeGReloadSetsTest {

    private static final int Y175 = 175;

    private DroneRack typeG() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_G);
        rack.setAmmo(new ArrayList<>(List.of(
                new Drone(DroneType.TypeI),
                new Drone(DroneType.TypeI))));
        return rack;
    }

    // ---------------------------------------------------------------- the composition

    @Test
    public void oneOfTheTwoSetsIsEntirelyAntiDrones() {
        DroneRack rack = typeG();

        // Two sets, of two kinds. getReloads() counts only the DRONE sets, which is
        // what the reload pool and the deck-crew cost are about; the anti-drone set is
        // rounds, and counted as rounds.
        assertEquals("one set mirrors the rack", 1, rack.getReloads().size());
        assertEquals("the other is a full magazine of anti-drones",
                8, rack.getAddReloads());
        assertEquals("FD3.72: two sets between them", 2, rack.getReloads().size() + 1);
    }

    @Test
    public void theMirroredSetMatchesWhatIsInTheRack() {
        DroneRack rack = typeG();

        List<Drone> mirrored = rack.getReloads().get(0);
        assertEquals(2, mirrored.size());
        for (Drone d : mirrored)
            assertEquals(DroneType.TypeI, d.getDroneType());
    }

    /** FD3.72's own figure: "eight ADDs in the second reload" — four spaces at half each. */
    @Test
    public void aFullSetIsEightRounds() {
        assertEquals(8, typeG().fullAntiDroneSet());
    }

    /** The Y175 refit adds a set that matches the rack, not another of anti-drones. */
    @Test
    public void theY175RefitAddsAMirroredSet() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_G);
        rack.addReloadSets(1);                       // the Federation refit
        rack.setAmmo(new ArrayList<>(List.of(new Drone(DroneType.TypeIV))));

        assertEquals("two mirrored sets now", 2, rack.getReloads().size());
        assertEquals("still one set of anti-drones", 8, rack.getAddReloads());
    }

    /**
     * "If a Federation player chose to start with the rack fully loaded with ADDs, then all
     * of his reloads would be ADDs" — the mirrored set mirrors an empty drone list, so the
     * reserve is anti-drones and nothing else.
     */
    @Test
    public void aRackOfAntiDronesHasNothingElseInReserve() {
        DroneRack rack = new DroneRack(DroneRackType.TYPE_G);
        rack.setAmmo(new ArrayList<>());
        rack.loadAntiDrones(8, Y175);

        assertTrue("no drones to mirror", rack.getReloads().isEmpty());
        assertEquals(8, rack.getAddReloads());
    }

    // ---------------------------------------------------------------- everything else is unchanged

    @Test
    public void anOrdinaryRackStillReloadsWithCopiesOfItself() {
        DroneRack typeB = new DroneRack(DroneRackType.TYPE_B);
        typeB.setAmmo(new ArrayList<>(List.of(
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI))));

        assertEquals("B carries two sets, both drones", 2, typeB.getReloads().size());
        assertEquals("and no anti-drones at all", 0, typeB.getAddReloads());
    }

    @Test
    public void aTypeARackIsUntouched() {
        DroneRack typeA = new DroneRack(DroneRackType.TYPE_A);
        typeA.setAmmo(new ArrayList<>(List.of(new Drone(DroneType.TypeI))));

        assertEquals(1, typeA.getReloads().size());
        assertEquals(0, typeA.getAddReloads());
    }
}
