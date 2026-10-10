package com.sfb.scenario;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Shuttle;

/**
 * No two units in a fleet may share a name.
 *
 * <h2>Why a name is not decoration</h2>
 * A name is this game's ADDRESS. {@code movableNow} is a list of names, the client selects by
 * name, and every action resolves its subject by name. Two units answering to one name is
 * therefore not a cosmetic clash — it is two objects at one address, and the symptom is that
 * neither can be reached.
 *
 * <p>Found in play 2026-10-09 and reported by the owner, who diagnosed it from the symptom
 * alone: "Waiting for Stinger2-1", and Stinger2-1 would not move. Two Hydran carriers in one
 * fleet had each named their fighters Stinger2-1, Stinger2-2, Stinger2-3. The counter behind
 * those numbers is deliberately per-ship, so that a complement spread over three bays runs
 * straight through rather than restarting in each — correct within a carrier, and silent the
 * moment there are two.
 *
 * <p>Drones never had the problem because they carry their launcher's name (IKS Viper-Drone-1),
 * and neither did the SHIPS, because {@code FleetLoader} already uniquifies those and says why.
 * The reasoning simply never reached the craft aboard them.
 *
 * <h2>Why it walks real fleets rather than a fixture</h2>
 * The same reason {@link com.sfb.objects.ShipLibrary}'s integrity guard walks the registry: a
 * fixture only proves what its author thought to build. This takes every carrier in the library,
 * puts TWO of it in one fleet — the arrangement that produced the bug — and insists the result
 * can still address every unit it contains.
 */
public class FleetUnitNameTest {

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    /** Every named thing a fleet puts in play: the ships, and the craft in their bays. */
    private static List<String> unitNames(List<Ship> ships) {
        List<String> names = new ArrayList<>();
        for (Ship ship : ships) {
            names.add(ship.getName());
            if (ship.getShuttles() == null)
                continue;
            for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays())
                for (com.sfb.systemgroups.ShuttleSpace space : bay.getSpaces()) {
                    Shuttle craft = space.getShuttle();
                    if (craft != null)
                        names.add(craft.getName());
                }
        }
        return names;
    }

    private static List<String> duplicatesIn(List<String> names) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String n : names)
            counts.merge(n, 1, Integer::sum);
        List<String> dupes = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet())
            if (e.getValue() > 1)
                dupes.add(e.getKey() + " x" + e.getValue());
        return dupes;
    }

    /** Build a fleet of the given hull, repeated, at a late year so complements are full. */
    private static List<Ship> fleetOf(ShipSpec spec, int copies, int year) {
        FleetSpec fleet = new FleetSpec();
        fleet.budget = 100000;        // this test is about names, not points
        fleet.year = year;
        fleet.factions.add(spec.faction);
        for (int i = 0; i < copies; i++) {
            FleetSpec.ShipEntry entry = new FleetSpec.ShipEntry();
            entry.faction = spec.faction;
            entry.type = spec.type;
            fleet.ships.add(entry);
        }
        return FleetLoader.resolve(fleet).ships();
    }

    // -------------------------------------------------------------------------

    /**
     * The reported case, as reported: two carriers of one class, and every unit addressable.
     * <p>
     * Run against every hull in the library that seats fighters, because the bug was not about
     * the Hydran — it was about the counter, which every carrier shares.
     */
    @Test
    public void twoCarriersOfOneClassDoNotShareFighterNames() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.shuttleBays == null || spec.shuttleBays.isEmpty())
                continue;
            List<Ship> ships;
            try {
                ships = fleetOf(spec, 2, 183);
            } catch (RuntimeException e) {
                continue;   // a hull the fleet rules refuse in pairs is not this test's business
            }
            if (ships.size() < 2)
                continue;
            List<String> dupes = duplicatesIn(unitNames(ships));
            checked++;
            if (!dupes.isEmpty())
                problems.add(spec.faction + "/" + spec.type + ": " + dupes);
        }
        assertTrue("there should be hulls with bays to check", checked > 50);
        assertTrue("two of one class put units in play under the same name. A name is the ADDRESS"
                + " an action resolves by, so this is two objects at one address and neither can"
                + " be reached:\n  " + String.join("\n  ", problems), problems.isEmpty());
    }

    /**
     * And a mixed fleet, which is how the bug actually reached play — a Hydran Concept beside a
     * Hydran Vengeance, different hulls flying the same fighter line.
     */
    @Test
    public void differentHullsFlyingTheSameFightersDoNotCollide() {
        List<ShipSpec> carriers = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            if ("Hydran".equalsIgnoreCase(spec.faction)
                    && spec.shuttleBays != null && !spec.shuttleBays.isEmpty())
                carriers.add(spec);
        assertTrue("fixture: the Hydrans should have hulls with bays", carriers.size() >= 2);

        FleetSpec fleet = new FleetSpec();
        fleet.budget = 100000;
        fleet.year = 183;
        fleet.factions.add("Hydran");
        for (ShipSpec spec : carriers.subList(0, Math.min(4, carriers.size()))) {
            FleetSpec.ShipEntry entry = new FleetSpec.ShipEntry();
            entry.faction = spec.faction;
            entry.type = spec.type;
            fleet.ships.add(entry);
        }

        List<String> dupes = duplicatesIn(unitNames(FleetLoader.resolve(fleet).ships()));
        assertTrue("different Hydran hulls seat the same fighter line and collided: " + dupes,
                dupes.isEmpty());
    }
}
