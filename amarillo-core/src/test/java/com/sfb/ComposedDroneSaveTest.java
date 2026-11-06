package com.sfb;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.DroneType;
import com.sfb.objects.PayloadModule;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * A drone comes back made of what it was made of.
 *
 * <h2>The gap this closes</h2>
 * {@code Drone.exportState} wrote the drone's TYPE and the numbers its composition had already
 * produced — {@code warheadDamage}, {@code rackSize}, {@code hull} — and nothing about the
 * composition itself. {@code fromState} then rebuilt through {@code new Drone(type)}, so every
 * drone a player had composed came back plain.
 *
 * <p>Worse than losing the drone: the restored drone was a chimera. An ECM drone's zero warhead
 * and its rack size survived in the saved numbers, so what came back was a standard type-I that
 * did no damage and had no ECM — wrong in two directions, and silent in both, because
 * {@code getSpec()} is what every FD10.4x effect reads and the type was still a type-I.
 *
 * <h2>Why it is fixed here and only here</h2>
 * A drone in a rack, in a reload set, in a fighter's ready rack, in a carrier's hold and in
 * flight are all the same object written through the same {@code exportState}. One field in one
 * place carries the composition for all of them.
 */
public class ComposedDroneSaveTest {

    private Ship ship;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        ship = new Ship();
        ship.init(KlingonShips.getD7());
        ship.setName("IKV Blade");
    }

    private static DroneRack rackOf(Ship s) {
        for (Weapon w : s.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r)
                return r;
        throw new AssertionError("fixture: the D7 should carry a drone rack");
    }

    private Ship roundTrip() {
        Map<String, Object> state = ship.exportState();
        Ship restored = new Ship();
        restored.init(KlingonShips.getD7());
        restored.applyState(state);
        return restored;
    }

    /** FD10.44: an ECM drone is a frame carrying an ECM module where the warhead would be. */
    private static Drone ecmDrone() {
        return DroneLoad.parse("TypeI+ECM").build();
    }

    // ---------------------------------------------------------------- the composition itself

    @Test
    public void anEcmDroneInARackIsStillAnEcmDroneAfterASave() {
        rackOf(ship).setAmmo(new java.util.ArrayList<>(List.of(ecmDrone())));

        Drone back = rackOf(roundTrip()).getAmmo().get(0);

        assertNotNull("the drone kept no composition at all", back.getSpec());
        assertTrue("an ECM drone came back without its ECM module",
                back.getSpec().payload().contains(PayloadModule.ECM));
    }

    @Test
    public void theWholeSpecSurvives() {
        Drone ecm = ecmDrone();
        rackOf(ship).setAmmo(new java.util.ArrayList<>(List.of(ecm)));

        Drone back = rackOf(roundTrip()).getAmmo().get(0);

        assertEquals("a DroneSpec is a record: the restored one should be equal, not similar",
                ecm.getSpec(), back.getSpec());
    }

    /**
     * FD12.12's armored type-IV: three armor modules and one half warhead, which exercises
     * repeated modules and a payload that is not one token.
     */
    @Test
    public void anArmoredTypeIvKeepsAllThreeArmorModules() {
        Drone armored = DroneLoad.parse(
                "TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF").build();
        rackOf(ship).setAmmo(new java.util.ArrayList<>(List.of(armored)));

        Drone back = rackOf(roundTrip()).getAmmo().get(0);

        assertEquals(armored.getSpec(), back.getSpec());
        assertEquals("three armor modules, not one",
                3, back.getSpec().payload().stream().filter(PayloadModule::isArmor).count());
    }

    /** A reload set is written through the same path, so it must come back the same way. */
    @Test
    public void aComposedDroneInAReloadSetSurvivesToo() {
        DroneRack rack = rackOf(ship);
        rack.setAmmo(new java.util.ArrayList<>(List.of(DroneLoad.of(DroneType.TypeI).build())));
        rack.getReloads().clear();
        rack.getReloads().add(new java.util.ArrayList<>(List.of(ecmDrone())));

        DroneRack back = rackOf(roundTrip());

        assertTrue("the reload set lost its ECM module",
                back.getReloads().get(0).get(0).getSpec().payload().contains(PayloadModule.ECM));
    }

    // ---------------------------------------------------------------- the plain case

    @Test
    public void aPlainDroneIsUnaffected() {
        Drone plain = DroneLoad.of(DroneType.TypeI).build();
        rackOf(ship).setAmmo(new java.util.ArrayList<>(List.of(plain)));

        Drone back = rackOf(roundTrip()).getAmmo().get(0);

        assertEquals(DroneType.TypeI, back.getDroneType());
        assertEquals(plain.getSpec(), back.getSpec());
    }

    /**
     * An unmodified drone writes its basis name and nothing else, which is the same token the
     * old {@code droneType} key held. That is what lets a save written before this change read
     * back, and a save written after it be read by a build without it.
     */
    @Test
    public void aPlainDroneWritesTheSameTokenTheTypeKeyAlwaysHeld() {
        Drone plain = DroneLoad.of(DroneType.TypeI).build();
        Map<String, Object> state = plain.exportState();

        assertEquals("TypeI", state.get("droneType"));
        assertEquals("TypeI", state.get("load"));
    }

    @Test
    public void aComposedDroneWritesItsCompositionInTheLoadKey() {
        Map<String, Object> state = ecmDrone().exportState();

        assertEquals("the type key still names the catalogue drone, for an older reader",
                "TypeI", state.get("droneType"));
        assertEquals("TypeI+ECM", state.get("load"));
    }

    /**
     * A save from before the {@code load} key existed, or one naming a module this build does
     * not have: the drone is still a drone of its type rather than nothing at all.
     */
    @Test
    public void aSaveWithNoLoadKeyStillReadsAsItsType() {
        Map<String, Object> state = ecmDrone().exportState();
        state.remove("load");

        Drone back = Drone.fromState(state, null, null, null);

        assertNotNull(back);
        assertEquals(DroneType.TypeI, back.getDroneType());
    }

    @Test
    public void aLoadNamingSomethingThisBuildLacksFallsBackToTheType() {
        Map<String, Object> state = ecmDrone().exportState();
        state.put("load", "TypeI+PHLOGISTON_WARHEAD");

        Drone back = Drone.fromState(state, null, null, null);

        assertNotNull("an unreadable composition must not lose the drone", back);
        assertEquals(DroneType.TypeI, back.getDroneType());
    }
}
