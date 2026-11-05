package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.systemgroups.ReadyRack;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;
import com.sfb.weapons.DroneRail;

/**
 * A rail's DESIGN follows the year's drones, as its load does (S2.11).
 *
 * <h2>The divergence</h2>
 * {@code ScenarioLoader.refitFighterDrones} swapped every drone in a ready rack and on a rail
 * for the year's engine, and left the rail's {@code designDrone} alone. So a Kzinti TADS in
 * Y180 held six type-I-FASTs in a rack whose design said type-I, and the two never agreed
 * again.
 *
 * <h2>What that broke</h2>
 * {@link ReadyRack#slotsMissing()} is a multiset difference — the design less what is held —
 * and {@code TypeIF} does not remove a {@code TypeI} slot. So <b>a full rack reported every one
 * of its slots missing</b>, on every carrier in every year with a speed upgrade, which is all
 * of them. Two callers believed it:
 * <ul>
 *   <li>{@code Shuttles.refillHalfActionsWanted} sizes a deck crew's refill job from it, so
 *       crews were assigned work on racks with nothing to do.</li>
 *   <li>{@code FighterArming.refillFromStores} asks the hold for the slot it is short of and
 *       falls back to "take anything" when the hold has no such drone. Asking for a type-I in
 *       a hold full of type-I-FASTs always fell through — defeating the guard that keeps a
 *       one-space drone from being fetched for a light rail.</li>
 * </ul>
 *
 * <p>Found from the Commander's Options panel, where it showed as the fighters the weapon
 * status had NOT armed reporting the baseline drone while their armed squadron-mates reported
 * the year's: the armed ones answer from the rail, the rest from the rack, and only the rack
 * side went through the broken match.
 */
public class ReadyRackDesignRefitTest {

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

    // ------------------------------------------------------------------ the design moves

    /**
     * Y180 flies fast drones, so that is what the rail is set up to carry — not the type-I-slow
     * the frame was drawn around.
     */
    @Test
    public void theRailsDesignFollowsTheYearsSpeed() {
        Ship ship = bought("Kzinti", "CVS", 180);
        int checked = 0;
        for (ShuttleSpace box : boxesOf(ship)) {
            if (!(box.getShuttle() instanceof Fighter f))
                continue;
            for (DroneRail rail : ReadyRack.stockedRails(f)) {
                DroneType design = rail.getDesignDrone();
                assertTrue(rail.getDesignator() + " is still designed around " + design,
                        design == DroneType.TypeIF || design == DroneType.TypeVIF);
                checked++;
            }
        }
        assertTrue("fixture: the squadron has rails to check", checked > 0);
    }

    /** And the rack built from those rails says the same thing its contents do (J4.8222). */
    @Test
    public void aRacksDesignAndItsContentsAgree() {
        Ship ship = bought("Kzinti", "CVS", 180);
        int checked = 0;
        for (ShuttleSpace box : boxesOf(ship)) {
            ReadyRack rack = box.getReadyRack();
            if (rack == null || rack.carriesPlasma() || !rack.isFull())
                continue;
            List<Drone> held = rack.contents();
            for (int i = 0; i < held.size(); i++) {
                assertEquals("slot " + i + " of " + box.getShuttle().getName(),
                        rack.design().get(i).getDroneType(), held.get(i).getDroneType());
                checked++;
            }
        }
        assertTrue("fixture: at least one full rack", checked > 0);
    }

    // ------------------------------------------------------------------ and the gap closes

    /**
     * The defect itself: a full rack is short of nothing.
     *
     * <p>This is the assertion that fails without the fix, on all twelve boxes at once — every
     * slot of every full rack was reported missing.
     */
    @Test
    public void aFullRackIsNotShortOfAnything() {
        Ship ship = bought("Kzinti", "CVS", 180);
        int checked = 0;
        for (ShuttleSpace box : boxesOf(ship)) {
            ReadyRack rack = box.getReadyRack();
            if (rack == null || rack.carriesPlasma() || !rack.isFull())
                continue;
            assertEquals(box.getShuttle().getName()
                            + "'s rack is full and reports it is short of " + rack.slotsMissing(),
                    List.of(), rack.slotsMissing());
            checked++;
        }
        assertTrue("fixture: at least one full rack", checked > 0);
    }

    /**
     * And a rack that really IS short asks for the right drone, so the hold can serve it.
     *
     * <p>{@code FighterArming.refillFromStores} takes {@code store.take(want)} and falls back to
     * {@code store.take()} — anything at all — when the hold has no such drone. With the design
     * naming a drone the year has retired, that fallback was the ONLY path: a light rail's
     * half-space slot could be filled with a one-space type-I, which is the exact trip
     * {@code slotsMissing}'s "largest first" ordering exists to prevent.
     */
    @Test
    public void aPartEmptyRackAsksForTheDroneTheHoldActuallyHolds() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxesOf(ship).stream()
                .filter(b -> b.getReadyRack() != null && !b.getReadyRack().carriesPlasma()
                        && b.getReadyRack().isFull())
                .findFirst().orElseThrow(() -> new AssertionError("fixture needs a full rack"));
        ReadyRack rack = box.getReadyRack();

        Drone taken = rack.take();
        assertEquals("one slot open", 1, rack.slotsMissing().size());
        assertEquals("and it asks for what came out of it",
                taken.getDroneType(), rack.slotsMissing().get(0).getDroneType());
    }

    /**
     * J4.62's casual carrier goes stale the same way, and has no rails to be rebuilt from.
     *
     * <p>A Kzinti DWE has two ready racks stocked for a carrier's TADSC and no fighters of its
     * own — its boxes hold administrative shuttles. Its rack contents were refitted with
     * everyone else's, so its design diverged exactly as a carrier's did, and a full rack
     * reported every slot missing.
     *
     * <p>This is the case that decided HOW the design is refitted. The first attempt re-read
     * the design from the fighter's rails, which an escort does not have: it would have wiped
     * these racks to nothing. Refitting each slot where it stands needs no occupant.
     */
    @Test
    public void anEscortsReadyRacksAreRefittedToo() {
        Ship dwe = bought("Kzinti", "DWE", 183);

        int checked = 0;
        for (ShuttleSpace box : boxesOf(dwe)) {
            ReadyRack rack = box.getReadyRack();
            if (rack == null || rack.carriesPlasma())
                continue;
            assertTrue("fixture: J4.62 — a rack, and no fighter in the box",
                    !(box.getShuttle() instanceof Fighter));
            assertTrue("fixture: the escort's rack is full", rack.isFull());
            assertEquals("a full rack is short of nothing, escort or carrier",
                    List.of(), rack.slotsMissing());
            for (Drone slot : rack.design())
                assertTrue("Y183 flies fast drones, even in an escort's hold: "
                                + slot.getDroneType(),
                        slot.getSpec().propulsion().speed >= 32 || slot.getRackSize() < 1.0);
            checked++;
        }
        assertTrue("fixture: the DWE has ready racks", checked > 0);
    }

    /**
     * A year with nothing to upgrade is left alone, which is what says the fix is a REFIT and
     * not a rewrite: in Y166 a type-I-slow is still the standard drone.
     */
    @Test
    public void aYearWithNoUpgradeLeavesTheDesignWhereItWas() {
        Ship ship = bought("Kzinti", "CVS", 166);
        for (ShuttleSpace box : boxesOf(ship)) {
            if (!(box.getShuttle() instanceof Fighter f))
                continue;
            for (DroneRail rail : ReadyRack.stockedRails(f))
                assertTrue("Y166 should still be flying the baseline, not "
                                + rail.getDesignDrone(),
                        rail.getDesignDrone() == DroneType.TypeI
                                || rail.getDesignDrone() == DroneType.TypeVI);
        }
    }

    /**
     * The scenario's own speed cap narrows the design as it narrows the load, or the two part
     * company again one layer down.
     */
    @Test
    public void aCappedScenarioCapsTheDesignToo() {
        ShipLibrary.loadAllSpecs("../data/factions");
        com.sfb.objects.ShipSpec spec = ShipLibrary.get("Kzinti", "CVS");
        org.junit.Assume.assumeTrue(spec != null);
        Ship ship = ShipLibrary.createShip(spec);
        ship.setName("Capped");
        // Y180 flies fast drones; a cap of 20 holds the squadron to medium.
        ScenarioLoader.outfitForYear(ship, "Kzinti", 180, spec, 20);

        for (ShuttleSpace box : boxesOf(ship)) {
            if (!(box.getShuttle() instanceof Fighter f))
                continue;
            for (DroneRail rail : ReadyRack.stockedRails(f))
                assertTrue("a capped scenario should not design around a fast drone: "
                                + rail.getDesignDrone(),
                        rail.getDesignDrone().spec.propulsion().speed <= 20);
        }
    }
}
