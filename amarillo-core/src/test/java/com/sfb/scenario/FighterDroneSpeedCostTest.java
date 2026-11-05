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
import com.sfb.objects.ShipSpec;
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
 * <h2>The loading is the READY RACK, and the fighters are not counted</h2>
 * Owner's ruling, 2026-11-03: "Ready racks have one full load for the fighter they serve.
 * Carriers (even casual carriers) pay for speed upgrades of drones in these ready racks. We do
 * not want to count fighters in addition to this. After all, any drones on fighters at the start
 * of a scenario came out of the ready racks, which would be empty."
 *
 * <p>So the charge reads each box's rack, at its full {@link ReadyRack#design()}, and never looks
 * at a fighter. The first attempt read the rack's CONTENTS plus the fighters' rails instead, on
 * the argument that a drone leaving the rack lands on a rail so the union is invariant —
 * {@link #theChargeDoesNotMoveWithTheWeaponStatus} is the test that shows it is not, and why.
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

    /** Every drone sitting in a ready rack aboard. */
    private static List<Drone> rackDronesOf(Ship ship) {
        List<Drone> drones = new ArrayList<>();
        for (ReadyRack rack : racksOf(ship))
            drones.addAll(rack.contents());
        return drones;
    }

    /** Every slot the ship's ready racks were built with — the loading FD2.45 charges for. */
    private static int designSlotsOf(Ship ship) {
        int slots = 0;
        for (ReadyRack rack : racksOf(ship))
            slots += rack.design().size();
        return slots;
    }

    // ------------------------------------------------------------------ the charge

    /**
     * The Y170 CVS, where the arithmetic is small enough to do by hand.
     *
     * <p>Eleven AAS boxes, two type-I rails apiece: twenty-two slots. Medium speed is General
     * from Y167 (FD10.65) and FD2.223 prices it at half a point, so the squadron owes eleven.
     * The hull's four type-A racks of four owe eight more by FD2.454's own worked example.
     * Nineteen, and it used to be eight.
     *
     * <p>The AAS-E has no ready rack at all: J4.962's two EW pods replace both its rails, so
     * there is no slot to stock and nothing to charge (eleven racks on fifteen boxes).
     */
    @Test
    public void aCarriersFightersPayForTheirOwnLoading() {
        Ship cvs = bought("Kzinti", "CVS", 170);

        assertEquals("fixture: eleven AAS boxes", 11, racksOf(cvs).size());
        assertEquals("fixture: two type-I slots each", 22, designSlotsOf(cvs));
        assertEquals("8 for the ship's racks + 11 for the squadron", 19, cvs.getDroneSpeedBpv());
    }

    /**
     * The squadron's share, isolated: destroy every fighter box and re-price. J4.831 takes a
     * box's stores with it, so what is left is the hull's own four racks — and the difference is
     * what the fighters were getting free.
     */
    @Test
    public void losingTheBoxesLeavesOnlyTheHullsOwnBill() {
        Ship cvs = bought("Kzinti", "CVS", 170);
        int withSquadron = cvs.getDroneSpeedBpv();

        for (ShuttleSpace box : boxesOf(cvs))
            box.destroy();
        ScenarioLoader.chargeDroneSpeed(cvs, 170);

        assertEquals("FD2.454's sixteen medium drones at half a point", 8, cvs.getDroneSpeedBpv());
        assertEquals("so the squadron was eleven of the nineteen",
                11, withSquadron - cvs.getDroneSpeedBpv());
    }

    /**
     * A Y183 CVS, which is the case that makes the rule worth having: twelve TADSC boxes of six
     * rails, seventy slots, and fast speed is a whole point each (FD2.224).
     *
     * <p>Seventy for the squadron on top of twenty for the hull's refitted racks. The carrier
     * is a 175-point hull that now owes ninety for its drones alone.
     */
    @Test
    public void aLateCarriersSquadronIsTheLargerHalfOfTheBill() {
        Ship cvs = bought("Kzinti", "CVS", 183);

        assertEquals("fixture: twelve boxes with racks", 12, racksOf(cvs).size());
        assertEquals("fixture: seventy slots across them", 70, designSlotsOf(cvs));
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

    // --------------------------------------------- the loading, wherever the loading has got to

    /**
     * The charge is the rack's FULL load whether the rack is holding it or not. Strip every rack
     * to the walls — which is what arming the whole squadron does — and the bill does not move.
     *
     * <p>This is the owner's ruling made testable, and it is why the charge reads
     * {@link ReadyRack#design()} rather than {@link ReadyRack#contents()}: "any drones on
     * fighters at the start of a scenario came out of the ready racks, which would be empty."
     */
    @Test
    public void theChargeIsOneFullLoadingWhetherTheRackIsHoldingItOrNot() {
        Ship cvs = bought("Kzinti", "CVS", 170);
        int full = cvs.getDroneSpeedBpv();

        for (ReadyRack rack : racksOf(cvs))
            while (rack.take() != null) {
                // every drone forward onto the squadron
            }
        for (ReadyRack rack : racksOf(cvs))
            assertTrue("fixture: the racks should now be empty", rack.isEmpty());
        ScenarioLoader.chargeDroneSpeed(cvs, 170);

        assertEquals("the loading is paid for once, wherever it has got to",
                full, cvs.getDroneSpeedBpv());
        assertEquals("8 for the ship's racks + 11 for the squadron", 19, cvs.getDroneSpeedBpv());
    }

    /**
     * And nothing is added for the fighters, which is the other half of the ruling: "we do not
     * want to count fighters in addition to this."
     *
     * <p>Loading the rails while the racks stay full is not a contrived state — it is exactly
     * what {@code FighterArming.armOccupantFully} does, and the reason this assertion matters
     * rather than merely restating the one above.
     *
     * <p>The rails are loaded with a copy of the drone the rack is holding, not with
     * {@code new Drone(rail.getDesignDrone())}. That matters: a design drone is a type-I-SLOW,
     * which costs nothing at any date, so a rail loaded that way would leave the bill unmoved
     * under the wrong model too and this test would pass for no reason. A copy of the refitted
     * type-IM costs half a point, so counting the rails would plainly double the squadron.
     */
    @Test
    public void dronesOnTheFightersThemselvesAreNotChargedForAgain() {
        Ship cvs = bought("Kzinti", "CVS", 170);
        int racksAlone = cvs.getDroneSpeedBpv();

        int loaded = 0;
        for (ShuttleSpace box : boxesOf(cvs)) {
            ReadyRack rack = box.getReadyRack();
            if (rack == null || box.getShuttle() == null)
                continue;
            for (Weapon w : box.getShuttle().getWeapons().fetchAllWeapons()) {
                if (!(w instanceof DroneRail rail) || rail.isLoaded()
                        || rail.getDesignDrone() == null || rail.hasEwPod())
                    continue;   // J4.962: a podded rail carries no drone
                Drone forward = rack.takeFor(rail);
                assertNotNull("fixture: the rack should be able to arm its own fighter", forward);
                rail.loadDrone(forward);
                // And the deck crew puts an identical one back, so both places hold a loading.
                assertTrue("fixture: the rack should take a replacement",
                        rack.put(new Drone(forward.getDroneType())));
                loaded++;
            }
        }
        assertEquals("fixture: twenty-two rails loaded", 22, loaded);
        assertEquals("fixture: and the racks are full again", 22, rackDronesOf(cvs).size());
        for (Drone d : rackDronesOf(cvs))
            assertEquals("fixture: a replacement that is NOT free to upgrade", 20, d.getSpeed());
        ScenarioLoader.chargeDroneSpeed(cvs, 170);

        assertEquals("a fighter is not a second rack to pay for",
                racksAlone, cvs.getDroneSpeedBpv());
    }

    /**
     * The regression that condemned the first implementation. S2.11 charges "based on the date
     * of the scenario", so the bill is a function of the hull and the year — never of how much
     * of the squadron happens to be bolted up when the invoice is written.
     *
     * <p>It was. Reading the rack's contents plus the fighters' rails gave a Y183 CVS <b>80 at
     * WS-0, 90 at WS-2 and 32 at WS-3</b>, because {@code FighterArming.armOccupantFully} loads
     * a rail with {@code new Drone(rail.getDesignDrone())} — a fresh drone at the frame's
     * baseline — rather than with the refitted drone sitting in the box. At WS-3 the whole
     * squadron is armed, every drone on it is a speed-8 type-I, and the carrier was being
     * invoiced for the slow drones it had been handed by mistake.
     *
     * <p><b>That arming defect is still live and is NOT what this test guards.</b> It is a real
     * rules bug — a Y183 TADSC flies speed-8 drones in play — and it belongs in
     * {@code FighterArming}, not here. What this pins is that the BILL no longer moves with it,
     * so fixing it cannot silently reprice a fleet and failing to fix it cannot discount one.
     */
    @Test
    public void theChargeDoesNotMoveWithTheWeaponStatus() {
        for (int weaponStatus : new int[] { 0, 1, 2, 3 }) {
            Ship cvs = bought("Kzinti", "CVS", 183);
            ScenarioLoader.applyWeaponStatus(cvs, weaponStatus);
            ScenarioLoader.chargeDroneSpeed(cvs, 183);

            assertEquals("WS-" + weaponStatus + " must cost what every other status costs",
                    90, cvs.getDroneSpeedBpv());
        }
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

    /**
     * A scenario's drone cap has to reach the ready racks too.
     *
     * <p>nivram_assault is the scenario that found this shape the first time — Y180 with a cap
     * of 20, its racks filled with speed-32 drones the picker had no row for. A carrier billed
     * for fast drones in a medium-speed scenario is the same mistake with the sign flipped.
     *
     * <p>Built in ONE pass with the cap, which is the only way a scenario ever builds a ship:
     * {@code ScenarioLoader.buildShip} calls {@code outfitForYear} once, with the scenario's
     * cap, and a fleet bought into a battle is rebuilt from the library the same way.
     */
    @Test
    public void theScenariosDroneCapReachesAReadyRacksBill() {
        ShipSpec spec = ShipLibrary.get("Kzinti", "CVS");
        org.junit.Assume.assumeTrue("the library must know the CVS", spec != null);
        Ship capped = ShipLibrary.createShip(spec);
        capped.setName("Capped");
        ScenarioLoader.outfitForYear(capped, "Kzinti", 183, spec, 20);

        assertEquals("fixture: uncapped, the same hull bills 90",
                90, bought("Kzinti", "CVS", 183).getDroneSpeedBpv());
        assertTrue("a capped scenario bills less than an uncapped one: "
                + capped.getDroneSpeedBpv(), capped.getDroneSpeedBpv() < 90);
        assertEquals("medium speed throughout, FD2.223 at half a point a space",
                45, capped.getDroneSpeedBpv());
    }

    /**
     * And a cap applied to a ship that is ALREADY fitted cannot lower it, which is a property
     * of the refit rather than of the charge: {@code upgradeOne} only ever raises a speed.
     *
     * <h2>This used to be true of the hull's racks and false of the squadron</h2>
     * The old version of the test above did exactly this — built uncapped, then charged with a
     * cap — and got 55: the hull's racks priced what they were actually carrying (fast, 20)
     * while the ready racks re-priced from a DESIGN still naming the frame's type-I-slow, so
     * the cap appeared to bite on the squadron alone. The design now follows the refit
     * ({@link ReadyRack#redesignFrom}), so both halves of the ship answer the same way.
     *
     * <p>Nothing in production does this: a scenario builds a ship once. It is pinned because
     * the inconsistency is what the divergence looked like from the outside, and a future
     * change that reintroduced it would show up here first.
     */
    @Test
    public void aCapAppliedAfterwardsCannotLowerAShipAlreadyFitted() {
        Ship fitted = bought("Kzinti", "CVS", 183);
        int uncapped = fitted.getDroneSpeedBpv();
        ScenarioLoader.applyDroneSpeed(fitted, 183, 20);

        assertEquals("the drones are already aboard; a cap does not take them off",
                uncapped, fitted.getDroneSpeedBpv());
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
        assertEquals("fixture: a TADSC loading apiece", 12, designSlotsOf(dwe));
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
