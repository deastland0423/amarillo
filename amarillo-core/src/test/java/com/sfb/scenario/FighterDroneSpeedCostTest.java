package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.systemgroups.ReadyRack;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * FD2.45's other half: a drone-armed fighter is a launch rack, and somebody pays for its load.
 *
 * <blockquote>FD2.45: "The cost of improved drones (FD2.22) is paid only for a single loading of
 * each launch rack <b>and/or fighter</b>; the reloads (and drones stored on a carrier for use by
 * its fighters) are presumed to be of the same type without additional cost... <b>Fighters are
 * equivalent to drone racks for the purpose of loading ready racks.</b>"</blockquote>
 *
 * <p>{@code chargeDroneSpeed} walked only the ship's own weapons, so a Kzinti CVS sailed into a
 * Y183 battle with seventy fast drones aboard its squadron and paid nothing for them, while the
 * frigate beside it paid a point each for sixteen. The hull's four racks were billed; the twelve
 * fighters in its bays were free. S2.11 makes the charge mandatory — "ships are required to pay
 * these charges based on the date of the scenario" — so this was not an option nobody had taken.
 *
 * <h2>And the drones were obsolete as well as free</h2>
 * The same walk did the year's REFIT, so the ready racks kept the type-I-slows
 * {@link ReadyRack#forFighter} stocks them with. A Y183 carrier's squadron flew speed-8 drones
 * at ships moving 31 — the exact defect {@link DroneSpeedYearTest} was written to close for
 * ship racks, surviving intact one layer down.
 *
 * <h2>Where the loading is counted</h2>
 * In the box's ready rack and on the rails of the fighter in that box, together. J4.8223 has a
 * carrier at rest with "racks full, fighters unloaded" and {@code applyFighterWeaponStatus} then
 * moves part of that forward onto the rails, so neither place alone is the loading and the union
 * of the two always is.
 *
 * @see ScenarioLoader#chargeDroneSpeed
 */
public class FighterDroneSpeedCostTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    /** One ship bought into a fleet dated {@code year}, outfitted exactly as a battle would. */
    private static Ship bought(String faction, String type, int year) {
        FleetSpec spec = new FleetSpec();
        spec.year = year;
        spec.budget = 4000;
        spec.factions.add(faction);
        FleetSpec.ShipEntry entry = new FleetSpec.ShipEntry();
        entry.faction = faction;
        entry.type = type;
        spec.ships.add(entry);

        FleetLoader.Resolution r = FleetLoader.resolve(spec);
        assertTrue("fixture: " + type + " should resolve — " + r.unknown(), r.isComplete());
        return r.ships().get(0);
    }

    private static List<ShuttleSpace> boxesOf(Ship ship) {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            boxes.addAll(bay.getSpaces());
        return boxes;
    }

    private static List<ReadyRack> racksOf(Ship ship) {
        List<ReadyRack> racks = new ArrayList<>();
        for (ShuttleSpace box : boxesOf(ship))
            if (box.getReadyRack() != null)
                racks.add(box.getReadyRack());
        return racks;
    }

    /** Every drone in every ready rack aboard. */
    private static List<Drone> rackDronesOf(Ship ship) {
        List<Drone> drones = new ArrayList<>();
        for (ReadyRack rack : racksOf(ship))
            drones.addAll(rack.contents());
        return drones;
    }

    // ------------------------------------------------------------------ the charge

    /**
     * The Y170 CVS, where the arithmetic is small enough to do by hand.
     *
     * <p>Eleven AAS boxes, two type-I rails apiece: twenty-two drones. Medium speed is General
     * from Y167 (FD10.65) and FD2.223 prices it at half a point, so the squadron owes eleven.
     * The hull's four type-A racks of four owe eight more by FD2.454's own worked example.
     * Nineteen, and it used to be eight.
     */
    @Test
    public void aCarriersFightersPayForTheirOwnLoading() {
        Ship cvs = bought("Kzinti", "CVS", 170);

        assertEquals("fixture: eleven AAS boxes", 11, racksOf(cvs).size());
        assertEquals("fixture: two type-I rails each", 22, rackDronesOf(cvs).size());
        assertEquals("8 for the ship's racks + 11 for the squadron", 19, cvs.getDroneSpeedBpv());
    }

    /**
     * The squadron's share, isolated: empty every ready rack and re-price. What is left is the
     * hull's own four racks, and the difference is what the fighters were getting free.
     */
    @Test
    public void emptyingTheReadyRacksLeavesOnlyTheHullsOwnBill() {
        Ship cvs = bought("Kzinti", "CVS", 170);
        int withSquadron = cvs.getDroneSpeedBpv();

        for (ReadyRack rack : racksOf(cvs))
            while (rack.take() != null) {
                // strip it to the walls
            }
        ScenarioLoader.chargeDroneSpeed(cvs, 170);

        assertEquals("FD2.454's sixteen medium drones at half a point", 8, cvs.getDroneSpeedBpv());
        assertEquals("so the squadron was eleven of the nineteen",
                11, withSquadron - cvs.getDroneSpeedBpv());
    }

    /**
     * A Y183 CVS, which is the case that makes the rule worth having: twelve TADSC boxes of six
     * rails, seventy drones, and fast speed is a whole point each (FD2.224).
     *
     * <p>Seventy for the squadron on top of twenty for the hull's refitted racks. The carrier
     * is a 175-point hull that now owes ninety for its drones alone.
     */
    @Test
    public void aLateCarriersSquadronIsTheLargerHalfOfTheBill() {
        Ship cvs = bought("Kzinti", "CVS", 183);

        assertEquals("fixture: twelve boxes with racks", 12, racksOf(cvs).size());
        assertEquals("fixture: seventy drones across them", 70, rackDronesOf(cvs).size());
        assertEquals("20 for the hull's racks + 70 for the squadron", 90, cvs.getDroneSpeedBpv());
        assertTrue("and the squadron's fighters are charged for on top of it",
                FleetValidator.costOf(cvs) > cvs.getBpv() + 90);
    }

    /**
     * FD2.45's free half, proved by the figure above rather than by a second assertion: the
     * carrier's hold (J4.7) holds eighty more spaces of the same fast drones, and the bill is
     * ninety rather than a hundred and seventy. "The reloads (and drones stored on a carrier
     * for use by its fighters) are presumed to be of the same type without additional cost."
     *
     * <p>Same type, which is the part that is easy to drop: the hold is REFITTED with the racks
     * and charged for none of it. A squadron whose reloads flew slower than its first sortie
     * would be a worse bug than the free drones this closes.
     */
    @Test
    public void theHoldFliesTheYearsDronesAndIsNotCharged() {
        Ship cvs = bought("Kzinti", "CVS", 183);
        assertNotNull("fixture: a CVS declares a drone store", cvs.getShuttles().getDroneStore());

        List<Drone> hold = cvs.getShuttles().getDroneStore().contents();
        assertTrue("fixture: the hold is stocked", hold.size() > 50);
        for (Drone d : hold)
            assertEquals("a reload is of the same type as the loading (FD2.45)", 32, d.getSpeed());

        assertEquals("and none of those spaces are billed", 90, cvs.getDroneSpeedBpv());
    }

    // ------------------------------------------------------------------ where it sits

    /**
     * The loading is charged once, wherever it happens to be. Arm the fighters from their boxes —
     * which is what {@code applyFighterWeaponStatus} does at weapon status 2 and 3 — and the
     * drones move from the ready rack onto the rails without changing hands or changing the bill.
     *
     * <p>This is why the charge reads both places rather than the rack alone. Counting the rack
     * would have made a carrier cheaper the more of its squadron was ready to fly.
     */
    @Test
    public void armingTheFightersDoesNotChangeWhatTheyCost() {
        Ship cvs = bought("Kzinti", "CVS", 170);
        int unloaded = cvs.getDroneSpeedBpv();

        int moved = 0;
        for (ShuttleSpace box : boxesOf(cvs)) {
            if (box.getReadyRack() == null || box.getShuttle() == null)
                continue;
            for (Weapon w : box.getShuttle().getWeapons().fetchAllWeapons()) {
                if (!(w instanceof DroneRail rail) || rail.isLoaded())
                    continue;
                Drone d = box.getReadyRack().takeFor(rail);
                if (d != null) {
                    rail.loadDrone(d);
                    moved++;
                }
            }
        }
        assertEquals("fixture: twenty-two drones should move onto rails", 22, moved);
        ScenarioLoader.chargeDroneSpeed(cvs, 170);
        for (ReadyRack rack : racksOf(cvs))
            assertTrue("fixture: the racks should now be empty", rack.isEmpty());

        assertEquals("the same loading, in a different place", unloaded, cvs.getDroneSpeedBpv());
        // Stated absolutely as well, because "unchanged" is also true of charging nothing at
        // either end — which is exactly what this method did before, and the assertion above
        // passed against it.
        assertEquals("8 for the ship's racks + 11 now on the rails", 19, cvs.getDroneSpeedBpv());
    }

    /** J4.831: a box's stores die with it, so a carrier stops paying for them. */
    @Test
    public void aDestroyedBoxIsNotChargedFor() {
        Ship cvs = bought("Kzinti", "CVS", 170);
        int whole = cvs.getDroneSpeedBpv();

        for (ShuttleSpace box : boxesOf(cvs))
            if (box.getReadyRack() != null) {
                box.destroy();
                break;
            }
        ScenarioLoader.chargeDroneSpeed(cvs, 170);

        assertEquals("one AAS box of two medium drones", 1, whole - cvs.getDroneSpeedBpv());
    }

    // ------------------------------------------------------------------ the refit

    /**
     * The other half of the gap: the racks held what {@link ReadyRack#forFighter} stocked them
     * with, which is the frame's baseline. A Y183 squadron was carrying speed-8 type-Is.
     */
    @Test
    public void aSquadronFliesTheYearsDronesRatherThanItsFramesBaseline() {
        Ship cvs = bought("Kzinti", "CVS", 183);

        List<Drone> drones = rackDronesOf(cvs);
        assertTrue("fixture: there are drones to check", drones.size() > 1);
        for (Drone d : drones)
            assertEquals("fast speed is General by Y183 (FD2.224)", 32, d.getSpeed());
    }

    // ------------------------------------------------------------------ J4.621

    /**
     * A casual carrier — an escort with ready racks and no fighter aboard to use them.
     *
     * <blockquote>J4.621: "Drones in the racks are slow unless speed upgrades are paid for. Pay
     * the cost of speed upgrades for one loading of the ready racks (FD2.45); the rest are held
     * in 'fighter storage' as per (FD2.443)."</blockquote>
     *
     * <p>FD2.45 says the same from its end — an escort computes the cost "as if they were
     * carrying fighters". The per-box walk gets this for nothing, which is the argument for
     * walking boxes rather than fighters: a Kzinti DWE has two TADSC racks and not one fighter.
     */
    @Test
    public void aCasualCarrierPaysForOneLoadingOfItsReadyRacks() {
        Ship dwe = bought("Kzinti", "DWE", 183);

        assertEquals("fixture: two fighter facilities", 2, racksOf(dwe).size());
        assertEquals("fixture: a TADSC loading apiece", 12, rackDronesOf(dwe).size());
        for (ShuttleSpace box : boxesOf(dwe))
            if (box.getReadyRack() != null)
                assertTrue("fixture: J4.62 — racks, but no fighter in the box",
                        !(box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter));

        assertEquals("16 for the hull's racks + 12 for the two ready racks",
                28, dwe.getDroneSpeedBpv());
    }

    /**
     * And the same escort when the player NAMES the fighter its facilities serve, which J4.621
     * leaves to them: "the fighters on the carrier will determine what type of ready racks are
     * on the escort."
     *
     * <p>That choice is a Commander's Option, so it lands in {@code applyCoi} — long after
     * {@code applyDroneSpeed} has run and will not run again. {@link ReadyRack#forType} stocks
     * the rebuilt racks at the frame's baseline, so without a refit at that point the escort
     * picks its facilities and the drones in them silently become obsolete, free of charge.
     */
    @Test
    public void choosingFacilitiesInTheOptionsStepStillBuysTheYearsDrones() {
        Ship dwe = bought("Kzinti", "DWE", 183);

        ScenarioSpec spec = new ScenarioSpec();
        spec.year = 183;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        CoiLoadout loadout = new CoiLoadout();
        loadout.fighterFacilityType = "tads";
        ScenarioLoader.applyCoi(dwe, loadout, spec);

        List<Drone> drones = rackDronesOf(dwe);
        assertTrue("fixture: the TADS racks were refitted", drones.size() > 1);
        for (Drone d : drones)
            assertEquals("a rebuilt rack is stocked at the year's speed too", 32, d.getSpeed());
        assertTrue("and charged for: " + dwe.getDroneSpeedBpv(),
                dwe.getDroneSpeedBpv() >= 16 + drones.size());
    }

    // ------------------------------------------------------------------ no regression

    /**
     * A ship with no bays is untouched, which is FD2.454's worked example and the whole of
     * {@link DroneSpeedYearTest}. The new walk must add zero where there is nothing to walk.
     */
    @Test
    public void aShipWithNoFightersPaysExactlyWhatItDidBefore() {
        Ship cs = bought("Kzinti", "CS", 183);

        assertEquals("fixture: no ready racks aboard", 0, racksOf(cs).size());
        assertEquals("its twenty refitted rack drones at a point each", 20, cs.getDroneSpeedBpv());
    }
}
