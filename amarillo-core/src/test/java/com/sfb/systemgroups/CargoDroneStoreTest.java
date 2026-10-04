package com.sfb.systemgroups;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * FD2.445: spare drones in a ship's cargo boxes.
 *
 * <p>"Some drone-armed ships have cargo boxes to store extra drones. Unless otherwise specified a
 * cargo box will hold 50 spaces of spare drones. It does not have them automatically, however,
 * unless specified in the ship description. <b>These drones are lost when the cargo boxes are
 * destroyed.</b>"
 *
 * <p>FD2.423 draws the line this whole thing turns on: "Drone and ADD reloads <b>(other than those
 * in cargo boxes)</b> are ... considered destroyed with the last Excess Damage box." Everything
 * else survives until the ship dies; cargo drones die with their boxes, and cargo is the first
 * item on one DAC column.
 */
public class CargoDroneStoreTest {

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    // ------------------------------------------------------------------ the store itself

    @Test
    public void fourBoxesAtFiftyIsTwoHundredSpaces() {
        CargoDroneStore store = new CargoDroneStore(4, 50);
        assertEquals(200, store.capacitySpaces());
        assertEquals("declaring the store IS the ship description saying so (FD2.445)",
                200, store.spacesHeld());
    }

    @Test
    public void losingABoxCostsThatBoxesWorth() {
        CargoDroneStore store = new CargoDroneStore(4, 50);
        assertEquals(50, store.loseOneBox());
        assertEquals(150, store.capacitySpaces());
        assertEquals(150, store.spacesHeld());
    }

    /**
     * The reason capacity and held are separate numbers. A ship that has already drawn its cargo
     * drones up into reload storage must not be charged for them twice — those are safe behind
     * FD2.423 now, and the box that died was partly empty.
     */
    @Test
    public void aHoldAlreadyDrawnDownLosesNothingUntilTheBoxesCannotAccountForIt() {
        CargoDroneStore store = new CargoDroneStore(4, 50);
        assertEquals(60, store.draw(60));
        assertEquals(140, store.spacesHeld());

        assertEquals("capacity 200 -> 150 still covers the 140 held", 0, store.loseOneBox());
        assertEquals(150, store.capacitySpaces());
        assertEquals(140, store.spacesHeld());

        assertEquals("capacity 150 -> 100 cannot, so 40 spill", 40, store.loseOneBox());
        assertEquals(100, store.capacitySpaces());
        assertEquals(100, store.spacesHeld());
    }

    @Test
    public void everyBoxGoneMeansEveryDroneGone() {
        CargoDroneStore store = new CargoDroneStore(2, 50);
        store.loseOneBox();
        store.loseOneBox();
        assertEquals(0, store.capacitySpaces());
        assertEquals(0, store.spacesHeld());
        assertTrue(store.isEmpty());
        assertEquals("and it cannot go negative", 0, store.loseOneBox());
    }

    @Test
    public void drawingNeverOverdrawsAndRestoringNeverOverfills() {
        CargoDroneStore store = new CargoDroneStore(1, 50);
        assertEquals("asked for 80, had 50", 50, store.draw(80));
        assertEquals(0, store.spacesHeld());
        assertEquals("asked to put 80 back into a 50-space hold", 50, store.restore(80));
        assertEquals(50, store.spacesHeld());
        assertEquals(0, store.restore(10));
    }

    @Test
    public void theRateIsDataNotFifty() {
        // FD2.445's "unless otherwise specified" — some ship will eventually say otherwise.
        CargoDroneStore store = new CargoDroneStore(3, 20);
        assertEquals(60, store.capacitySpaces());
        assertEquals(20, store.loseOneBox());
    }

    // ------------------------------------------------------------------ on a real hull

    private Ship hull(String faction, String type) {
        ShipSpec spec = ShipLibrary.get(faction, type);
        assertNotNull(faction + "/" + type + " should be in the library", spec);
        return ShipLibrary.createShip(spec);
    }

    @Test
    public void theD5dSailsWithTwoHundredSpacesInFourBoxes() {
        Ship d5d = hull("Klingon", "D5D");
        CargoDroneStore store = d5d.getCargoDroneStore();
        assertNotNull("the D5D's SSD says it carries them", store);
        assertEquals(4, d5d.getHullBoxes().getAvailableCargo());
        assertEquals(200, store.capacitySpaces());
        assertEquals(200, store.spacesHeld());
    }

    @Test
    public void theKzintiDroneFrigatesCarryOneHundred() {
        for (String type : new String[] { "DF", "DF+", "SDF", "SDF+" }) {
            Ship ship = hull("Kzinti", type);
            CargoDroneStore store = ship.getCargoDroneStore();
            assertNotNull(type + " should declare cargo drones", store);
            assertEquals(type + " has two cargo boxes", 100, store.capacitySpaces());
        }
    }

    /**
     * The wiring, through the public damage path rather than the private per-system dispatch.
     * <p>
     * The DAC is rolled, so which box takes each point is not ours to choose — but the INVARIANT
     * is: with no repairs, capacity is always the surviving boxes times the rate. If the hook in
     * the cargo case were missing, capacity would sit at 200 while the boxes burned down, and this
     * fails. An invariant rather than a fixed expectation, because the roll decides the route.
     *
     * @return the ship, once at least one cargo box has gone
     */
    private Ship d5dWithACargoBoxDestroyed() {
        Ship d5d = hull("Klingon", "D5D");
        CargoDroneStore store = d5d.getCargoDroneStore();
        int rate = store.getSpacesPerBox();
        List<String> log = new ArrayList<>();

        for (int round = 0; round < 60 && d5d.getHullBoxes().getAvailableCargo() == 4; round++) {
            log.addAll(d5d.applyInternalDamage(5));
            assertEquals("capacity must track the surviving boxes at every step",
                    d5d.getHullBoxes().getAvailableCargo() * rate, store.capacitySpaces());
            assertTrue("held can never exceed capacity", store.spacesHeld() <= store.capacitySpaces());
        }
        assertTrue("fixture: 300 points of internals should have reached a cargo box; log=" + log,
                d5d.getHullBoxes().getAvailableCargo() < 4);
        return d5d;
    }

    @Test
    public void cargoDronesDieWithTheirBoxesThroughTheRealDamagePath() {
        Ship d5d = d5dWithACargoBoxDestroyed();
        CargoDroneStore store = d5d.getCargoDroneStore();
        assertEquals(d5d.getHullBoxes().getAvailableCargo() * 50, store.capacitySpaces());
        assertTrue("some drones are gone", store.capacitySpaces() < 200);
    }

    /**
     * <b>The case that decided the design.</b> Capacity is NOT recomputed from the live box count,
     * because {@code repairCargo} can bring a box back and FD2.445 says the drones "are lost". A
     * repaired cargo box is empty volume, not recovered drones — a derived figure would silently
     * hand the 50 spaces back.
     */
    @Test
    public void repairingACargoBoxDoesNotBringItsDronesBack() {
        Ship d5d = d5dWithACargoBoxDestroyed();
        CargoDroneStore store = d5d.getCargoDroneStore();
        int afterDamage = store.capacitySpaces();
        int boxesLeft = d5d.getHullBoxes().getAvailableCargo();

        assertTrue("the box itself is repairable", d5d.getHullBoxes().repairCargo(1));
        assertEquals("the box is back", boxesLeft + 1, d5d.getHullBoxes().getAvailableCargo());
        assertEquals("but the drones are not (FD2.445)", afterDamage, store.capacitySpaces());
        assertTrue("and a derived capacity would have said " + (boxesLeft + 1) * 50,
                store.capacitySpaces() < (boxesLeft + 1) * 50);
    }

    // ------------------------------------------------------------------ guards on the data

    /**
     * The field may only appear on a hull that has somewhere to put the drones. A declaration on a
     * hull with no cargo boxes would build a zero-capacity store and read as working.
     */
    @Test
    public void onlyHullsWithCargoBoxesDeclareCargoDrones() {
        List<String> wrong = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.cargoDroneSpacesPerBox == null)
                continue;
            int boxes = spec.hullBoxes == null ? 0 : spec.hullBoxes.cargo;
            if (boxes <= 0)
                wrong.add(spec.faction + " " + spec.type
                        + " declares cargo drones but has no cargo boxes");
            if (spec.cargoDroneSpacesPerBox <= 0)
                wrong.add(spec.faction + " " + spec.type
                        + " declares a cargo drone rate of " + spec.cargoDroneSpacesPerBox);
        }
        assertEquals(String.join("\n  ", wrong), List.of(), wrong);
    }

    /**
     * FD2.443 and FD2.445 are different pools and no hull should claim both through the same
     * field. {@code droneStorageSpaces} is the carrier's FIGHTER supply (J4.7); cargo drones feed
     * the ship's own racks. The D5D was declared with the former and got a 200-space fighter
     * supply that stocked itself with nothing, because it has no fighters to stock for.
     */
    @Test
    public void noHullUsesTheFighterSupplyFieldForCargoDrones() {
        List<String> suspect = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.droneStorageSpaces <= 0)
                continue;
            int boxes = spec.hullBoxes == null ? 0 : spec.hullBoxes.cargo;
            if (boxes > 0 && Math.abs(spec.droneStorageSpaces - boxes * 50.0) < 0.001)
                suspect.add(spec.faction + " " + spec.type + " declares droneStorageSpaces "
                        + (int) spec.droneStorageSpaces + ", which is exactly its " + boxes
                        + " cargo boxes at 50 — did it mean cargoDroneSpacesPerBox?");
        }
        assertEquals(String.join("\n  ", suspect), List.of(), suspect);
    }
}
