package com.sfb.systemgroups;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.shuttles.Aas;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.Taas;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * A fighter whose rails are not all the same size (J4.82, FD7.211).
 * <p>
 * The Kzinti TAAS carries two standard rails and two light ones: four drones in three spaces.
 * Every fighter before it had standard rails alone, which hid two assumptions — that a rack
 * could be stocked from one drone type, and that the work of loading could be priced off the
 * rail. Both are wrong here, and loading a TAAS threw an IllegalArgumentException because a
 * Type-I does not fit a light rail at all.
 * <p>
 * The owner's ruling on the price (2026-09-27): a drone's own size decides the work, so a
 * Type-VI in a standard rail is half an action, not a whole one.
 */
public class MixedRailFighterTest {

    private static java.util.List<DroneRail> railsOf(Shuttle f) {
        java.util.List<DroneRail> rails = new java.util.ArrayList<>();
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                rails.add(rail);
        return rails;
    }

    private static Taas taas() {
        Taas t = new Taas();
        t.setName("TAAS-1");
        return t;
    }

    @Test
    public void itCarriesFourDronesInThreeSpaces() {
        Taas t = taas();

        assertEquals("four rails", 4, railsOf(t).size());
        double spaces = railsOf(t).stream().mapToDouble(r -> r.getRailType().capacity).sum();
        assertEquals("three spaces, which is what FD7.211 measures", 3.0, spaces, 0.001);
        assertEquals(3, t.scatterPackSpaces());
    }

    @Test
    public void eachRailIsStockedWithSomethingItCanActuallyTake() {
        ShuttleSpace box = new ShuttleSpace(taas());
        ReadyRack rack = box.getReadyRack();

        assertNotNull(rack);
        assertEquals("one drone per rail (J4.8222)", 4, rack.capacity());
        assertEquals("and the rack holds three spaces of them", 3.0, rack.spaces(), 0.001);

        long light = rack.contents().stream()
                .filter(d -> d.getDroneType() == DroneType.TypeVI).count();
        assertEquals("a Type-I would not fit the light rails, so two Type-VIs are stocked",
                2, light);
    }

    @Test
    public void loadingFillsEveryRailWithADroneItFits() {
        ShuttleSpace box = new ShuttleSpace(taas());
        Shuttle t = box.getShuttle();

        // Three spaces at two half-actions each: six, which is three whole actions.
        assertEquals(6, FighterArming.halfActionsToFullyArm(t));
        FighterArming.Load load = FighterArming.load(box, t, 6);

        assertEquals("all four rails", 4, load.chargesLoaded());
        for (DroneRail rail : railsOf(t)) {
            Drone d = rail.getDrone();
            assertNotNull("every rail got one", d);
            assertTrue(rail.getRailType() + " was given a " + d.getDroneType()
                    + ", which does not fit", rail.accepts(d));
        }
        assertTrue("and the rack is empty", box.getReadyRack().isEmpty());
    }

    @Test
    public void aTaasIsTheFirstFighterTooBigForOneTurnsCrewWork() {
        // J4.8172 allows two crews on a fighter and each does one action in a turn, so two
        // actions a turn is the ceiling — and a TAAS wants three.
        assertEquals(3, FighterArming.actionsToFullyArm(taas()));
        assertEquals("an AAS still fits in one turn", 2,
                FighterArming.actionsToFullyArm(new Aas()));
    }

    @Test
    public void aHalfSpaceDroneIsHalfAnActionWhereverItGoes() {
        // The owner's ruling: the drone decides, not the rail. A Type-VI in a STANDARD rail
        // is half an action's work, so a TAAS loaded entirely with dogfight drones turns
        // round in two actions rather than three.
        ShuttleSpace box = new ShuttleSpace(taas());
        Shuttle t = box.getShuttle();
        for (DroneRail rail : railsOf(t))
            rail.setDesignDrone(DroneType.TypeVI);
        ShuttleSpace lightBox = new ShuttleSpace(t);   // a fresh box, stocked from those rails

        assertEquals("four half-spaces is two actions", 4,
                FighterArming.halfActionsToFullyArm(t));

        FighterArming.Load load = FighterArming.load(lightBox, t, 4);
        assertEquals(4, load.chargesLoaded());
        assertEquals("and it cost four half-actions, not six", 4, load.halfActionsUsed());
    }

    @Test
    public void aBudgetShortOfTheNextDroneLeavesItInTheRack() {
        ShuttleSpace box = new ShuttleSpace(taas());
        Shuttle t = box.getShuttle();

        // One action: enough for one standard drone (2 half-actions) and no more.
        FighterArming.Load load = FighterArming.load(box, t, 2);

        assertEquals(1, load.chargesLoaded());
        assertEquals("J4.8174 gives no credit for an action that did not finish",
                2, load.halfActionsUsed());
        assertEquals("the rest are still in the rack, not dropped", 3,
                box.getReadyRack().count());
    }
}
