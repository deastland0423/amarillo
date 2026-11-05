package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.PayloadModule;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.systemgroups.ReadyRack;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * A reload is of the same kind as the loading it serves, free (FD2.45, FD2.452).
 *
 * <blockquote>FD2.45: "The drones in storage are proportional to those on the racks/fighters.
 * They can be of a less expensive, but cannot be of a more expensive, type than the drones in
 * the loading paid for. For example, if a Klingon D7B with two type-A drone racks had six
 * type-I and two type-I-ECM drones in its racks, then the reloads (assuming a single set) could
 * include <b>up to two type-I-ECM</b> and the rest type-I."</blockquote>
 *
 * <h2>What was lost, and in two places at once</h2>
 * Both storage mechanisms built their contents from a {@code DroneType} and threw the
 * composition away:
 * <ul>
 *   <li>{@code DroneRack.setAmmo} mirrors the loading into its reload sets with
 *       {@code new Drone(d.getDroneType())}, so a rack bought ECM drones reloaded with plain
 *       ones.</li>
 *   <li>{@code DroneStore.stock} dealt round a list of types, so a carrier's hold held nothing
 *       but baseline drones however its squadron was armed.</li>
 * </ul>
 *
 * <p>So a player bought two ECM drones, flew them once, and could never have another — though
 * FD2.45 gives them free. Mirroring the LOADING rather than its types takes the entitlement up
 * by construction and can never exceed it, which is the whole of the proportional rule.
 */
public class ReloadCompositionTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

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

    private static ScenarioSpec year(int y) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = y;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        spec.commanderOptions.budgetPercent = 100;   // the budget is not what these test
        return spec;
    }

    private static List<ShuttleSpace> boxesOf(Ship ship) {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            boxes.addAll(bay.getSpaces());
        return boxes;
    }

    private static DroneRack firstRackOf(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack && !(w instanceof com.sfb.weapons.DroneRail))
                return rack;
        throw new AssertionError("fixture needs a drone rack");
    }

    private static boolean hasEcm(Drone d) {
        return d != null && d.getSpec() != null
                && d.getSpec().allModules().contains(PayloadModule.ECM);
    }

    private static int ecmCount(List<Drone> drones) {
        int n = 0;
        for (Drone d : drones)
            if (hasEcm(d))
                n++;
        return n;
    }

    // ------------------------------------------------------------------ a ship's own racks

    /**
     * FD2.45's own worked example, in the shape the data gives it: a rack loaded with ECM
     * drones reloads with ECM drones, in the same proportion and at no extra cost.
     */
    @Test
    public void aRacksReloadsMirrorWhatWasLoadedIntoIt() {
        Ship ship = bought("Kzinti", "BC", 180);
        DroneRack rack = firstRackOf(ship);
        org.junit.Assume.assumeTrue("fixture: the rack has reloads behind it",
                rack.getNumberOfReloads() > 0);

        List<Drone> load = new ArrayList<>();
        load.add(DroneLoad.parse("TypeIF+ECM").build());
        for (int i = 1; i < rack.getSpaces(); i++)
            load.add(DroneLoad.parse("TypeIF").build());
        rack.setAmmo(load);

        assertEquals("fixture: one ECM drone in the rack", 1, ecmCount(rack.getAmmo()));
        for (List<Drone> set : rack.getReloads())
            assertEquals("a reload set mirrors the loading (FD2.45)",
                    1, ecmCount(set));
    }

    /** And a reload is a COPY: the hold does not hand back the drone already on the rack. */
    @Test
    public void aReloadIsACopyAndNotTheSameDrone() {
        Ship ship = bought("Kzinti", "BC", 180);
        DroneRack rack = firstRackOf(ship);
        org.junit.Assume.assumeTrue(rack.getNumberOfReloads() > 0);

        List<Drone> load = new ArrayList<>();
        load.add(DroneLoad.parse("TypeIF+ECM").build());
        rack.setAmmo(load);

        for (List<Drone> set : rack.getReloads())
            for (Drone spare : set)
                for (Drone loaded : rack.getAmmo())
                    assertFalse("a reload sharing an object with the rack would arm a"
                                    + " fighter with a drone it is already carrying",
                            spare == loaded);
    }

    // ------------------------------------------------------------------ a carrier's hold

    /**
     * The gap the fighter loadout surface opened: a squadron buys ECM drones and the hold is
     * owed ECM drones to reload them with.
     *
     * <p>The hold is stocked during {@code outfitForYear}, which is before a player has bought
     * anything, so {@code applyCoi} re-stocks it from the loading the squadron now flies.
     */
    @Test
    public void aCarriersHoldFollowsWhatItsSquadronActuallyFlies() {
        Ship ship = bought("Kzinti", "CVS", 180);
        org.junit.Assume.assumeTrue("fixture: the carrier has a hold",
                ship.getShuttles().getDroneStore() != null);
        assertEquals("fixture: as issued, nothing in the hold is special",
                0, ecmCount(ship.getShuttles().getDroneStore().contents()));

        // Every fighter's first standard rail given an ECM drone.
        CoiLoadout loadout = new CoiLoadout();
        for (ShuttleSpace box : boxesOf(ship)) {
            if (!(box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter f))
                continue;
            List<com.sfb.weapons.DroneRail> rails = ReadyRack.stockedRails(f);
            if (rails.isEmpty())
                continue;
            List<DroneLoad> rows = new ArrayList<>();
            rows.add(DroneLoad.parse("TypeIF+ECM"));
            loadout.fighterLoadouts.put(f.getName(), rows);
        }
        assertFalse("fixture: somebody was given one", loadout.fighterLoadouts.isEmpty());

        ScenarioLoader.applyCoi(ship, loadout, year(180));

        assertTrue("the squadron is carrying ECM drones: "
                        + String.join(" | ", ship.getSetupNotes()),
                ecmCount(ship.getShuttles().getDroneStore().contents()) > 0);
    }

    /**
     * And proportionally. FD2.45 caps the entitlement at what the loading holds, and dealing
     * round the loading gives exactly that — one rail in six is an ECM drone, so roughly one
     * space in six of the hold is, and never the whole hold.
     */
    @Test
    public void theHoldIsProportionalAndNotSimplyFullOfTheExpensiveOne() {
        Ship ship = bought("Kzinti", "CVS", 180);
        org.junit.Assume.assumeTrue(ship.getShuttles().getDroneStore() != null);

        CoiLoadout loadout = new CoiLoadout();
        for (ShuttleSpace box : boxesOf(ship)) {
            if (!(box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter f))
                continue;
            if (ReadyRack.stockedRails(f).isEmpty())
                continue;
            loadout.fighterLoadouts.put(f.getName(),
                    List.of(DroneLoad.parse("TypeIF+ECM")));
        }
        ScenarioLoader.applyCoi(ship, loadout, year(180));

        List<Drone> hold = ship.getShuttles().getDroneStore().contents();
        assertTrue("fixture: the hold has something in it", hold.size() > 6);
        int ecm = ecmCount(hold);
        assertTrue("the hold should carry some ECM drones", ecm > 0);
        assertTrue("but nothing like all of them — one rail in six was changed, and "
                        + ecm + " of " + hold.size() + " is out of proportion",
                ecm * 2 < hold.size());
    }

    /** A carrier nobody has given special drones is stocked exactly as it always was. */
    @Test
    public void anUntouchedCarriersHoldIsUnchanged() {
        Ship ship = bought("Kzinti", "CVS", 180);
        org.junit.Assume.assumeTrue(ship.getShuttles().getDroneStore() != null);
        int before = ship.getShuttles().getDroneStore().count();

        ScenarioLoader.applyCoi(ship, new CoiLoadout(), year(180));

        assertEquals("re-stocking an unchanged squadron changes nothing",
                before, ship.getShuttles().getDroneStore().count());
        assertEquals(0, ecmCount(ship.getShuttles().getDroneStore().contents()));
    }

    /**
     * The hold answers the slot that asked, and does not over-issue the special one.
     *
     * <p>This is the owner's rule as a contract on one method. Asking by TYPE could not
     * express it — a type-I-ECM and a plain type-I are both a {@code TypeIF} — so a rack
     * short of one ECM drone and three plain ones was handed FOUR ECM drones, which is the one
     * thing FD2.45 forbids outright: storage cannot be "of a more expensive type than the
     * drones in the loading paid for".
     *
     * <p>The ECM slot asks FIRST, which is the order that tells an exact match from a type
     * match: a hold choosing the plainest of the right type would hand it the plain drone and
     * leave the one that was bought sitting in storage.
     */
    @Test
    public void theHoldAnswersTheSlotThatAskedAndDoesNotOverIssue() {
        com.sfb.systemgroups.DroneStore hold = new com.sfb.systemgroups.DroneStore(10);
        hold.put(DroneLoad.parse("TypeIF").build());
        hold.put(DroneLoad.parse("TypeIF+ECM").build());

        Drone forEcmSlot = hold.take(DroneLoad.parse("TypeIF+ECM").build());
        assertTrue("the ECM slot gets what was bought for it", hasEcm(forEcmSlot));

        Drone forPlainSlot = hold.take(DroneLoad.parse("TypeIF").build());
        assertFalse("and the plain slot gets a plain drone", hasEcm(forPlainSlot));
        assertTrue("the hold is empty now", hold.isEmpty());
    }

    /**
     * With nothing exact left, FD2.452's lower-cost stand-in fills the slot rather than none
     * — and it is the PLAINEST that goes, so the scarce drones stay in the hold for the
     * slots that actually want them.
     */
    @Test
    public void aSlotWithNoExactReloadTakesThePlainestLawfulSubstitute() {
        com.sfb.systemgroups.DroneStore hold = new com.sfb.systemgroups.DroneStore(10);
        // The ECM drone goes in FIRST, so a hold that merely kept whichever it scanned first
        // would spend it here. The two cost the same (see the test below), so insertion order
        // is the only thing left for a cost comparison to be decided by.
        hold.put(DroneLoad.parse("TypeIF+ECM").build());
        hold.put(DroneLoad.parse("TypeIF").build());

        // A probe drone: nothing in the hold matches it, so a substitute is owed.
        Drone issued = hold.take(DroneLoad.parse("TypeIF+PROBE").build());
        assertFalse("the plain drone stands in, not the ECM one another slot needs",
                hasEcm(issued));
        assertEquals(com.sfb.objects.DroneType.TypeIF, issued.getDroneType());
        assertEquals("and the ECM drone is still in the hold", 1, ecmCount(hold.contents()));

        assertNull("nothing at all when the type is not there either",
                hold.take(DroneLoad.parse("TypeIVF").build()));
    }

    /**
     * A part-empty rack asks for what it is actually short of, not for the first slot whose
     * TYPE happens to match.
     *
     * <p>{@code slotsMissing} is a multiset difference, and a plain drone sitting in a rack
     * designed for one ECM drone and five plain ones must strike a PLAIN slot. Striking the
     * ECM slot because the types agree leaves the rack asking for a plain drone it already
     * has, and the ECM drone it is missing never comes back.
     */
    @Test
    public void aPartEmptyRackAsksForWhatItIsShortOfAndNotMerelyForItsType() {
        Ship ship = bought("Kzinti", "CVS", 180);
        CoiLoadout loadout = new CoiLoadout();
        for (ShuttleSpace box : boxesOf(ship)) {
            if (!(box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter f))
                continue;
            if (ReadyRack.stockedRails(f).isEmpty())
                continue;
            loadout.fighterLoadouts.put(f.getName(), List.of(DroneLoad.parse("TypeIF+ECM")));
        }
        ScenarioLoader.applyCoi(ship, loadout, year(180));

        ShuttleSpace box = boxesOf(ship).stream()
                .filter(b -> b.getReadyRack() != null
                        && ecmCount(b.getReadyRack().contents()) == 1)
                .findFirst().orElseThrow(() -> new AssertionError("fixture: nobody got one"));
        ReadyRack rack = box.getReadyRack();
        assertEquals("fixture: the DESIGN names the ECM slot too", 1, ecmCount(rack.design()));

        // Take the ECM drone out and nothing else: the rack is short of exactly that.
        int before = rack.count();
        rack.refit(held -> hasEcm(held) ? null : held);
        List<Drone> kept = new ArrayList<>();
        while (rack.count() > 0) {
            Drone one = rack.take();
            if (!hasEcm(one))
                kept.add(one);
        }
        for (Drone one : kept)
            rack.put(one);
        assertEquals("fixture: exactly one drone came out", before - 1, rack.count());

        List<Drone> missing = rack.slotsMissing();
        assertEquals("one slot open", 1, missing.size());
        assertTrue("and it is the ECM slot, not a plain one of the same type",
                hasEcm(missing.get(0)));
    }

    /**
     * Why a slot has to ask by COMPOSITION and not by type or by price, as an executable fact.
     *
     * <p>Two near-misses are recorded here. Asking by TYPE cannot tell these drones apart at
     * all: both are a {@code TypeIF}. And ordering by point cost — the obvious way to prefer
     * the special one — is a coin flip, because FD10.5 prices an ECM module at 0.50, exactly
     * what the explosive warhead it replaces costs. Only the composition separates them, which
     * is why {@code ReadyRack.design} holds drones rather than types.
     */
    @Test
    public void anEcmDroneCostsNoMoreThanThePlainOneItReplaces() {
        Drone plain = DroneLoad.parse("TypeIF").build();
        Drone ecm = DroneLoad.parse("TypeIF+ECM").build();

        assertEquals("FD10.5: the module replaces the warhead at the same price",
                plain.getSpec().pointCost(), ecm.getSpec().pointCost(), 0.0001);
        assertEquals("but it is Restricted, and that is what makes it special (FD10.62)",
                com.sfb.objects.Availability.RESTRICTED,
                ecm.getSpec().allModules().get(0).availability);
    }

    /**
     * The owner's rule, end to end: a Type-I-ECM in the ready rack means Type-I-ECM
     * replacements in the reloads.
     *
     * <blockquote>Owner's ruling, 2026-11-05: "I believe that if you have a Type-IM ECM drone
     * in the ready rack, there are replacement Type-IM ECM drones in reloads."</blockquote>
     *
     * <p>Which is FD2.45's own worked example read the simple way, and it settles the
     * ambiguity FD2.452 leaves open — the rules PERMIT storage to differ from the rack ("a MW
     * drone might be in the rack while an ECM drone was in the reload storage"), and this game
     * does not model that. Reloads match. No declaration surface, and no per-position
     * composition record; see the class javadoc for what that would have cost.
     *
     * <p>Pinned end to end because no single piece of it is the answer: the hold is stocked
     * from the loading, the fighter is armed from its rack, the rack asks the hold for what it
     * is short of, and the hold issues the scarcest match. Any one of those four could be
     * changed in isolation and still look right.
     */
    @Test
    public void aFighterThatFliesAnEcmDroneIsReloadedWithAnEcmDrone() {
        Ship ship = bought("Kzinti", "CVS", 180);
        org.junit.Assume.assumeTrue(ship.getShuttles().getDroneStore() != null);

        CoiLoadout loadout = new CoiLoadout();
        for (ShuttleSpace box : boxesOf(ship)) {
            if (!(box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter f))
                continue;
            if (ReadyRack.stockedRails(f).isEmpty())
                continue;
            loadout.fighterLoadouts.put(f.getName(), List.of(DroneLoad.parse("TypeIF+ECM")));
        }
        ScenarioLoader.applyCoi(ship, loadout, year(180));

        ShuttleSpace box = boxesOf(ship).stream()
                .filter(b -> b.getReadyRack() != null && ecmCount(b.getReadyRack().loading(
                        b.getShuttle())) > 0)
                .findFirst().orElseThrow(() -> new AssertionError("fixture: nobody got one"));
        ReadyRack rack = box.getReadyRack();

        // The fighter flies it off: arm, then launch everything it carries.
        box.armOccupantFully();
        for (com.sfb.weapons.DroneRail rail : ReadyRack.stockedRails(box.getShuttle()))
            rail.getAmmo().clear();
        assertEquals("fixture: the rack is empty and the drones are gone",
                0, rack.count());

        // And the deck crews refill it from the hold (J4.821).
        com.sfb.systemgroups.FighterArming.refill(box, ship.getShuttles().getDroneStore(), 99);

        assertTrue("fixture: the rack was refilled at all", rack.count() > 0);
        assertEquals("an ECM drone went back where an ECM drone came from",
                1, ecmCount(rack.contents()));
    }

    // ------------------------------------------------------------------ the shared reading

    /**
     * {@code ReadyRack.loading} reads the RAIL before the rack, because arming moves a drone
     * from one to the other (J4.8224) and neither place alone is the loading.
     */
    @Test
    public void theLoadingIsReadFromWhereverTheDroneHappensToBe() {
        Ship ship = bought("Kzinti", "CVS", 180);
        ShuttleSpace box = boxesOf(ship).stream()
                .filter(b -> b.getReadyRack() != null && !b.getReadyRack().carriesPlasma())
                .findFirst().orElseThrow(() -> new AssertionError("fixture needs a rack"));

        ReadyRack rack = box.getReadyRack();

        // An ECM drone in the rack, which is where one sits before a fighter is armed.
        rack.refit(held -> hasEcm(held) ? held
                : ecmCount(rack.contents()) == 0 ? DroneLoad.parse("TypeIF+ECM").build() : held);
        assertEquals("fixture: one ECM drone, in the rack", 1, ecmCount(rack.contents()));
        assertEquals("and the loading sees it there",
                1, ecmCount(rack.loading(box.getShuttle())));

        // Arming moves it to a RAIL (J4.8224). The loading must follow it, or the composition
        // is invisible the moment a fighter is ready -- which is most of them at WS-2, and is
        // what the hold is stocked from.
        box.armOccupantFully();
        assertEquals("fixture: the rack handed its drones over", 0, ecmCount(rack.contents()));
        assertEquals("the loading is the same loading, wherever the drone now sits",
                1, ecmCount(rack.loading(box.getShuttle())));
        assertEquals("and still one entry per design slot",
                rack.design().size(), rack.loading(box.getShuttle()).size());
    }
}
