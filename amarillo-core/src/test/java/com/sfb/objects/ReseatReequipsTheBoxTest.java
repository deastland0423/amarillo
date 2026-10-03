package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.DroneStore;
import com.sfb.systemgroups.ReadyRack;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;

/**
 * A fighter box services the fighter it is HOLDING, not the one that happened to sit in it
 * first (J4.822, J4.831, J4.72).
 *
 * <h2>What this guards, and why it is a sweep rather than a case</h2>
 * {@code ShuttleSpace.setShuttle} learns a box's capacitor and ready rack from its first
 * occupant and then keeps them, deliberately: the SSD marks the BOX, so a Stinger that
 * launches has to leave its charges behind rather than carry them away. Right in play - and
 * wrong at setup, because {@code FighterComplement.reseat} is the whole point of
 * {@code fighterLines} and it changes which fighter a box holds.
 *
 * <p>Before {@code ShuttleSpace.reequipFor} existed this was wrong on <b>393 boxes across
 * fifteen hulls and five factions</b>, every one of which looked like a working carrier:
 * <ul>
 *   <li>a Kzinti CV reseated to Y180 flies TADS with six rails apiece and kept the AAS's
 *       two-drone rack, reloading a third of what the squadron carries;</li>
 *   <li>a Federation CVA reseated back to Y167 kept the A-10's PHOTON capacitor under an F-4
 *       that has no photon, so a deck crew found work charging a torpedo nothing could
 *       fire;</li>
 *   <li>a Hydran RN+ box that should bank eight points of fusion charge (J4.831) banked one,
 *       because a hellbore Stinger sat there first.</li>
 * </ul>
 *
 * <p>A sweep over every hull and every era, rather than a test per case, because the bug is
 * not in any one ship - it is in the seam between "the box remembers" and "the year decides",
 * and any new line or carrier can land on it. The assertion is the INVARIANT: a reseated box
 * must be equipped exactly as a fresh box for the same occupant would be. That phrasing is
 * what makes it survive new data, since it never names a fighter, a year or a capacity.
 */
public class ReseatReequipsTheBoxTest {

    @Before
    public void load() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    /** Every year any line declares an era for - the years that can change a bay's contents. */
    private static Set<Integer> eraYears() {
        Set<Integer> years = new TreeSet<>();
        for (String line : ShuttleCatalog.lineNames())
            for (ShuttleCatalog.LineEra era : ShuttleCatalog.lineEras(line))
                years.add(era.from);
        return years;
    }

    private static boolean carriesAComplement(Ship ship) {
        if (ship.getShuttles() == null)
            return false;
        for (ShuttleBay bay : ship.getShuttles().getBays())
            if (bay.getFighterComplement() != null)
                return true;
        return false;
    }

    private static String capacitorOf(ShuttleSpace box) {
        return box.getCapacitorKind() + "/" + box.capacitorCapacity();
    }

    private static String rackOf(ShuttleSpace box) {
        ReadyRack rack = box.getReadyRack();
        if (rack == null)
            return "none";
        return (rack.isPlasmaD() ? "plasmaD" : "drone") + " x" + rack.capacity();
    }

    @Test
    public void everyReseatedBoxIsEquippedForTheFighterItHolds() {
        List<String> wrong = new ArrayList<>();

        for (ShipSpec spec : ShipLibrary.all()) {
            for (int year : eraYears()) {
                Ship ship = ShipLibrary.createShip(spec);
                if (!carriesAComplement(ship))
                    continue;
                FighterComplement.reseat(ship, year);

                for (ShuttleBay bay : ship.getShuttles().getBays()) {
                    for (ShuttleSpace box : bay.getSpaces()) {
                        Shuttle occupant = box.getShuttle();
                        if (!(occupant instanceof Fighter))
                            continue;

                        // A box built fresh around this very fighter: the right answer by
                        // construction, and the same code path the ship's own first seating
                        // used, so the invariant cannot drift away from what it compares to.
                        ShuttleSpace reference = new ShuttleSpace(
                                ShuttleBay.buildShuttle(occupant.getCatalogType(), "reference"));

                        if (!capacitorOf(box).equals(capacitorOf(reference))
                                || !rackOf(box).equals(rackOf(reference)))
                            wrong.add(spec.faction + " " + spec.type + " in Y" + year
                                    + ", box holding " + occupant.getCatalogType()
                                    + ": capacitor " + capacitorOf(box)
                                    + " (should be " + capacitorOf(reference) + ")"
                                    + ", rack " + rackOf(box)
                                    + " (should be " + rackOf(reference) + ")");
                    }
                }
            }
        }

        assertEquals("a reseated fighter box must be equipped for the fighter it is holding, "
                + "not the one seated at the ship's service year:\n  "
                + String.join("\n  ", wrong), List.of(), wrong);
    }

    /**
     * J4.72: drones and torpedoes in the ready racks "count as part of the ship's storage", so
     * the declared figure is a TOTAL and what sits forward comes out of it. Re-seating changes
     * how much is forward, which means the hold has to be recomputed - and it can be wrong in
     * either direction. The KRV at Y183 overcommitted by ten spaces.
     */
    @Test
    public void noReseatedCarrierHoldsMoreThanItDeclares() {
        List<String> wrong = new ArrayList<>();

        for (ShipSpec spec : ShipLibrary.all()) {
            for (int year : eraYears()) {
                Ship ship = ShipLibrary.createShip(spec);
                if (!carriesAComplement(ship))
                    continue;
                FighterComplement.reseat(ship, year);

                DroneStore store = ship.getShuttles().getDroneStore();
                if (store == null)
                    continue;
                double forward = ship.getShuttles().spacesCommittedForward();
                double total = store.spacesHeld() + forward;
                if (total > store.capacitySpaces() + 0.001)
                    wrong.add(spec.faction + " " + spec.type + " in Y" + year + ": "
                            + store.spacesHeld() + " in the hold + " + forward
                            + " forward = " + total
                            + ", over a declared " + store.capacitySpaces());
            }
        }

        assertEquals("J4.72 counts what is racked against the ship's declared storage:\n  "
                + String.join("\n  ", wrong), List.of(), wrong);
    }

    /**
     * The other direction, and the one that looks healthiest: a carrier whose fighters need
     * ammunition, that declares storage for it, and whose hold is EMPTY. The Warhawk did
     * exactly this at Y183 - five G-3Ks with two plasma-D rails each, fifty declared spaces,
     * nothing in them, so each fighter fired the two torpedoes in its box and was finished for
     * the scenario. Nothing was broken at its service year, where the Gladiator-1 it was built
     * around carries a plasma-F and has no rails to stock for at all.
     */
    @Test
    public void aCarrierWhoseFightersNeedAmmunitionStocksSome() {
        List<String> wrong = new ArrayList<>();

        for (ShipSpec spec : ShipLibrary.all()) {
            for (int year : eraYears()) {
                Ship ship = ShipLibrary.createShip(spec);
                if (!carriesAComplement(ship))
                    continue;
                FighterComplement.reseat(ship, year);

                DroneStore store = ship.getShuttles().getDroneStore();
                if (store == null || store.capacitySpaces() <= 0)
                    continue;

                // Does anything aboard actually reload from the hold?
                boolean needsStores = false;
                for (ShuttleBay bay : ship.getShuttles().getBays())
                    for (ShuttleSpace box : bay.getSpaces())
                        if (box.getReadyRack() != null)
                            needsStores = true;

                if (needsStores && store.isEmpty())
                    wrong.add(spec.faction + " " + spec.type + " in Y" + year
                            + ": declares " + store.capacitySpaces()
                            + " spaces of storage, its fighters have ready racks, "
                            + "and the hold is empty");
            }
        }

        assertEquals("a carrier that declares storage and has fighters to reload must stock it:"
                + "\n  " + String.join("\n  ", wrong), List.of(), wrong);
    }
}
