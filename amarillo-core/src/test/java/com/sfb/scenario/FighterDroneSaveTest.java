package com.sfb.scenario;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.PayloadModule;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.DroneStore;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;

/**
 * A carrier's purchased drones survive a save: what each slot WANTS, and what the hold HAS.
 *
 * <h2>Two gaps, both in the same half of the format</h2>
 * The save's rule is that the hull's shape is rebuilt from the scenario and the battle's
 * drawdown comes from the state. A Commander's Option purchase (S3.2) sits awkwardly across
 * that line, because it changes what the ship IS after the hull has spoken and before the
 * battle starts -- and nothing replays it on load, since {@code SavedGame.restore} calls the
 * one-argument {@code setupFromScenario} and a {@code ScenarioSpec} has nowhere to record a COI.
 *
 * <ul>
 *   <li>A ready rack's DESIGN -- the drone each slot wants back (FD2.446, J4.8222) -- was not
 *       written at all, so a rack bought around an ECM drone reloaded its fighter with a plain
 *       one, and {@code slotsMissing} asked the hold for the wrong drone.</li>
 *   <li>The hold's RESERVE was not written either: {@code DroneStore.exportState} saved only
 *       how much of it had been given over to plasma. A reloaded carrier therefore got its
 *       whole magazine back, special drones included -- free ammunition, which is the most
 *       directly exploitable thing a save can get wrong (the same fault
 *       {@code DroneRackSaveTest} closed for ship racks).</li>
 * </ul>
 */
public class FighterDroneSaveTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    // ------------------------------------------------------------------ fixture

    /** A Kzinti CVS as a fleet buys it: TADS fighters, two special rails each. */
    private static Ship cvs() {
        FleetSpec spec = new FleetSpec();
        spec.year = 180;
        spec.budget = 4000;
        spec.factions.add("Kzinti");
        FleetSpec.ShipEntry entry = new FleetSpec.ShipEntry();
        entry.faction = "Kzinti";
        entry.type = "CVS";
        spec.ships.add(entry);

        FleetLoader.Resolution r = FleetLoader.resolve(spec);
        assertTrue("fixture: a Kzinti CVS should resolve -- " + r.unknown(), r.isComplete());
        return r.ships().get(0);
    }

    private static ScenarioSpec year(int y) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = y;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        return spec;
    }

    private static List<ShuttleSpace> boxesOf(Ship ship) {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            boxes.addAll(bay.getSpaces());
        return boxes;
    }

    /** The box holding the fighter named {@code name}. */
    private static ShuttleSpace boxNamed(Ship ship, String name) {
        for (ShuttleSpace box : boxesOf(ship)) {
            Shuttle s = box.getShuttle();
            if (s != null && name.equals(s.getName()))
                return box;
        }
        throw new AssertionError("fixture needs a fighter called " + name);
    }

    /** The first fighter aboard, whatever the reseat happened to call it. */
    private static String firstFighterName(Ship ship) {
        for (ShuttleSpace box : boxesOf(ship))
            if (box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter f)
                return f.getName();
        throw new AssertionError("fixture: a CVS should carry fighters");
    }

    /**
     * Buy one ECM drone onto the last rail of one fighter, the way the COI endpoint does.
     *
     * <p>The LAST rail on a TADS is a special rail, which is where FD10.646 expects a bought
     * drone to go. Nulls stand for the rails the player left alone -- the addressing is
     * positional, so they cannot be dropped.
     */
    private static Ship withEcmOnOneFighter() {
        Ship ship = cvs();
        String fighter = firstFighterName(ship);
        int rails = boxNamed(ship, fighter).getReadyRack().design().size();

        List<DroneLoad> loads = new ArrayList<>();
        for (int i = 0; i < rails - 1; i++)
            loads.add(null);
        loads.add(DroneLoad.parse("TypeI+ECM"));

        CoiLoadout loadout = new CoiLoadout();
        loadout.fighterLoadouts.put(fighter, loads);
        ScenarioLoader.applyCoi(ship, loadout, year(180));
        return ship;
    }

    /** Rebuild the hull exactly as a load does -- from the spec, with no COI -- and overlay. */
    private static Ship roundTrip(Ship ship) {
        Map<String, Object> state = ship.exportState();
        Ship restored = cvs();
        restored.applyState(state);
        return restored;
    }

    private static boolean isEcm(Drone d) {
        return d != null && d.getSpec() != null
                && d.getSpec().payload().contains(PayloadModule.ECM);
    }

    private static long ecmCount(List<Drone> drones) {
        return drones.stream().filter(FighterDroneSaveTest::isEcm).count();
    }

    // ------------------------------------------------------------------ the design

    @Test
    public void theFixtureReallyBuysAnEcmDrone() {
        Ship ship = withEcmOnOneFighter();
        String fighter = firstFighterName(ship);

        assertEquals("fixture: exactly one design slot should want an ECM drone",
                1, ecmCount(boxNamed(ship, fighter).getReadyRack().design()));
    }

    @Test
    public void aReadyRackDesignedAroundAnEcmDroneStillWantsOneAfterASave() {
        Ship ship = withEcmOnOneFighter();
        String fighter = firstFighterName(ship);

        List<Drone> design = boxNamed(roundTrip(ship), fighter).getReadyRack().design();

        assertEquals("the slot that was bought an ECM drone came back wanting a plain one",
                1, ecmCount(design));
    }

    /**
     * The whole design, slot for slot -- not just the bought one. S2.11's year speeds are in
     * here too, so this is also the mandatory refit crossing the save.
     */
    @Test
    public void everySlotComesBackWantingWhatItWanted() {
        Ship ship = withEcmOnOneFighter();
        String fighter = firstFighterName(ship);
        List<Drone> before = boxNamed(ship, fighter).getReadyRack().design();

        List<Drone> after = boxNamed(roundTrip(ship), fighter).getReadyRack().design();

        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++)
            assertEquals("slot " + i, before.get(i).getSpec(), after.get(i).getSpec());
    }

    /**
     * A rack that was never bought anything must come back unchanged rather than empty: most
     * fighters on most carriers are in this case, and a design wiped to nothing would make
     * {@code slotsMissing} report a full rack as needing nothing at all.
     */
    @Test
    public void anUntouchedRackKeepsItsOwnDesign() {
        Ship ship = withEcmOnOneFighter();
        ShuttleSpace plainBox = null;
        for (ShuttleSpace box : boxesOf(ship))
            if (box.getReadyRack() != null && !box.getReadyRack().design().isEmpty()
                    && ecmCount(box.getReadyRack().design()) == 0) {
                plainBox = box;
                break;
            }
        assertNotNull("fixture: some other fighter should have a plain design", plainBox);
        String name = plainBox.getShuttle().getName();
        List<Drone> before = plainBox.getReadyRack().design();

        List<Drone> after = boxNamed(roundTrip(ship), name).getReadyRack().design();

        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++)
            assertEquals("slot " + i, before.get(i).getSpec(), after.get(i).getSpec());
    }

    // ------------------------------------------------------------------ the rails

    /** Every drone rail on the fighter in {@code box}, in the order the weapons list holds them. */
    private static List<com.sfb.weapons.DroneRail> railsOf(ShuttleSpace box) {
        List<com.sfb.weapons.DroneRail> rails = new ArrayList<>();
        Shuttle craft = box.getShuttle();
        if (craft == null)
            return rails;
        for (com.sfb.weapons.Weapon w : craft.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.DroneRail rail)
                rails.add(rail);
        return rails;
    }

    /**
     * A RAIL's design drone, which is a second record of the same purchase and reverts
     * separately.
     *
     * <p>{@code DroneRail} extends {@code DroneRack} and inherited its save, which carries the
     * drone actually ON the rail but nothing about what the rail is designed around -- so the
     * field fell back to {@code defaultDroneFor(type)}, the frame's baseline. That is wrong in
     * two ways at once after a load: S2.11's mandatory year speed is undone, and FD10.646's
     * bought drone with it.
     *
     * <p>It bites in battle rather than at setup, which is why it is worth saving. The last
     * resort in {@code FighterArming} loads {@code new Drone(rail.getDesignDrone())} onto an
     * empty rail, so a fighter rearmed AFTER a load flew a slow baseline drone on a hull whose
     * year had retired it.
     */
    @Test
    public void everyRailComesBackDesignedAroundTheDroneItWasDesignedAround() {
        Ship ship = withEcmOnOneFighter();
        String fighter = firstFighterName(ship);
        List<com.sfb.weapons.DroneRail> before = railsOf(boxNamed(ship, fighter));
        assertFalse("fixture: a TADS should have drone rails", before.isEmpty());

        List<com.sfb.weapons.DroneRail> after = railsOf(boxNamed(roundTrip(ship), fighter));

        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++)
            assertEquals("rail " + i, before.get(i).getDesignDrone(),
                    after.get(i).getDesignDrone());
    }

    /**
     * The S2.11 half on its own, stated as a fact about the year rather than about equality:
     * in Y180 a standard rail is designed around a FAST type-I, and a save that reverted to the
     * frame's baseline would hand back the slow one the hull was drawn with.
     */
    @Test
    public void aStandardRailStillWantsTheYearsFastDroneAfterASave() {
        Ship ship = withEcmOnOneFighter();
        String fighter = firstFighterName(ship);
        com.sfb.objects.DroneType wanted = null;
        for (com.sfb.weapons.DroneRail rail : railsOf(boxNamed(ship, fighter)))
            if (rail.getDesignDrone() == com.sfb.objects.DroneType.TypeIF) {
                wanted = rail.getDesignDrone();
                break;
            }
        assertNotNull("fixture: S2.11 should have refitted a rail to a fast type-I by Y180",
                wanted);

        boolean found = false;
        for (com.sfb.weapons.DroneRail rail : railsOf(boxNamed(roundTrip(ship), fighter)))
            if (rail.getDesignDrone() == com.sfb.objects.DroneType.TypeIF)
                found = true;

        assertTrue("the year's mandatory drone speed was undone by the save", found);
    }

    /**
     * The drones actually ON the rails, which is the free-ammunition half of the same gap.
     *
     * <p>A fighter is rebuilt from its catalogue row ARMED, so one that had spent four of its
     * six drones got them back. The same fault {@code DroneRackSaveTest} closed for ship racks
     * and {@code DroneStore} closed for the carrier's hold -- a fighter's rails were the third
     * place it lived, because {@code Shuttle.exportState} wrote no weapon state at all.
     */
    @Test
    public void aSpentRailStaysSpentAcrossASave() {
        Ship ship = withEcmOnOneFighter();
        String fighter = firstFighterName(ship);
        List<com.sfb.weapons.DroneRail> rails = railsOf(boxNamed(ship, fighter));

        // Arm them all, then spend two, so the test can tell "spent" from "never loaded".
        for (com.sfb.weapons.DroneRail rail : rails)
            if (rail.getDesignDrone() != null)
                rail.loadDrone(new Drone(rail.getDesignDrone()));
        List<com.sfb.weapons.DroneRail> loaded = new ArrayList<>();
        for (com.sfb.weapons.DroneRail rail : rails)
            if (rail.getDrone() != null)
                loaded.add(rail);
        assertTrue("fixture: at least three rails should be armable", loaded.size() >= 3);
        loaded.get(0).setAmmo(new ArrayList<>());
        loaded.get(1).setAmmo(new ArrayList<>());
        int left = loaded.size() - 2;

        List<com.sfb.weapons.DroneRail> after = railsOf(boxNamed(roundTrip(ship), fighter));
        int stillLoaded = 0;
        for (com.sfb.weapons.DroneRail rail : after)
            if (rail.getDrone() != null)
                stillLoaded++;

        assertEquals("a fighter got its spent drones back", left, stillLoaded);
    }

    /** FD10.2 on a rail: a bought ECM drone riding one is still an ECM drone after a save. */
    @Test
    public void aComposedDroneOnARailKeepsItsComposition() {
        Ship ship = withEcmOnOneFighter();
        String fighter = firstFighterName(ship);
        com.sfb.weapons.DroneRail special = null;
        for (com.sfb.weapons.DroneRail rail : railsOf(boxNamed(ship, fighter)))
            if (rail.accepts(DroneLoad.parse("TypeI+ECM").build())) {
                special = rail;
                break;
            }
        assertNotNull("fixture: some rail should take a one-space ECM drone", special);
        special.setAmmo(new ArrayList<>(List.of(DroneLoad.parse("TypeI+ECM").build())));
        int index = railsOf(boxNamed(ship, fighter)).indexOf(special);

        com.sfb.weapons.DroneRail back =
                railsOf(boxNamed(roundTrip(ship), fighter)).get(index);

        assertNotNull("the rail came back empty", back.getDrone());
        assertTrue("the ECM drone on the rail came back plain", isEcm(back.getDrone()));
    }

    // ------------------------------------------------------------------ the hold

    /**
     * FD2.45 stocks the hold PROPORTIONALLY to the loading, and the proportion is per set of
     * reloads: the rulebook's own example caps a Klingon D7B at "up to two type-I-ECM" only
     * "assuming a single set". A Kzinti CVS has room for more than one set of its squadron's
     * loading, so one ECM drone bought onto one rail stocks more than one ECM reload. An
     * assertion of exactly one here was asserting a guess about the capacity, not the rule --
     * which is what this test caught when it was first written.
     */
    @Test
    public void theFixtureReallyStocksAnEcmReload() {
        DroneStore hold = withEcmOnOneFighter().getShuttles().getDroneStore();

        assertNotNull("fixture: a CVS should have a drone store", hold);
        assertTrue("FD2.45: a bought ECM drone should be reflected in the hold",
                ecmCount(hold.contents()) >= 1);
    }

    @Test
    public void theHoldComesBackHoldingTheEcmReloadsItWasStockedWith() {
        Ship ship = withEcmOnOneFighter();
        long stocked = ecmCount(ship.getShuttles().getDroneStore().contents());
        assertTrue("fixture: there should be an ECM reload to lose", stocked >= 1);

        DroneStore hold = roundTrip(ship).getShuttles().getDroneStore();

        assertEquals("the ECM reloads the fleet paid for came back as plain drones",
                stocked, ecmCount(hold.contents()));
    }

    /**
     * FD2.45's reloads are the ammunition a carrier spends. A save that handed them all back
     * would be free drones, which is what {@code DroneRackSaveTest} closed for ship racks.
     */
    @Test
    public void drawingTheHoldDownIsNotUndoneByASave() {
        Ship ship = withEcmOnOneFighter();
        DroneStore hold = ship.getShuttles().getDroneStore();
        int stocked = hold.contents().size();
        assertTrue("fixture: the hold should start with drones in it", stocked >= 4);
        for (int i = 0; i < 3; i++)
            assertNotNull("fixture: the hold should issue a drone", hold.take());
        assertEquals(stocked - 3, hold.contents().size());

        DroneStore back = roundTrip(ship).getShuttles().getDroneStore();

        assertEquals("a reloaded carrier got its spent magazine back",
                stocked - 3, back.contents().size());
    }

    // ------------------------------------- what an older save, or a changed hull, must not do

    /**
     * A save written before either key existed: neither the design nor the hold may be wiped.
     *
     * <p>The two defaults go in OPPOSITE directions, and both are the forgiving reading of a
     * file that simply does not say. An absent design leaves the hull's, because a rack with no
     * design reloads nothing. An absent reserve leaves the hull's full hold, because a battle
     * restored with no drones at all is a worse misreading than one restored with too many.
     */
    @Test
    public void aSaveFromBeforeTheseKeysLeavesTheHullsOwnAnswers() {
        Ship ship = withEcmOnOneFighter();
        String fighter = firstFighterName(ship);
        Map<String, Object> state = ship.exportState();
        stripKeys(state, "design", "reserve");

        Ship restored = cvs();
        restored.applyState(state);

        assertFalse("the design was wiped by a save that never mentioned it",
                boxNamed(restored, fighter).getReadyRack().design().isEmpty());
        assertFalse("the hold was emptied by a save that never mentioned it",
                restored.getShuttles().getDroneStore().contents().isEmpty());
    }

    /**
     * A hull edited between sessions, which {@code SavedGame}'s own javadoc names as the one
     * thing an embedded scenario cannot make self-contained.
     *
     * <p>A saved design longer than the rack the hull now builds must not lengthen it: the slot
     * count is one per rail, and extra slots would be more drones than {@code spaces()} has
     * room for. Overlaid slot by slot, a stale save changes compositions and never the shape.
     */
    @Test
    public void aSavedDesignCannotChangeTheShapeOfTheRack() {
        Ship ship = withEcmOnOneFighter();
        String fighter = firstFighterName(ship);
        Map<String, Object> state = ship.exportState();
        int slots = boxNamed(ship, fighter).getReadyRack().design().size();
        int lengthened = lengthenOneDesign(state, slots);
        assertEquals("fixture: the design should have grown by two slots", slots + 2, lengthened);

        Ship restored = cvs();
        restored.applyState(state);

        assertEquals("a stale save added slots the hull does not have",
                slots, boxNamed(restored, fighter).getReadyRack().design().size());
    }

    /** Remove every occurrence of each key, at any depth. */
    @SuppressWarnings("unchecked")
    private static void stripKeys(Object node, String... keys) {
        if (node instanceof Map<?, ?> map) {
            for (String key : keys)
                ((Map<String, Object>) map).remove(key);
            for (Object child : map.values())
                stripKeys(child, keys);
        } else if (node instanceof List<?> list) {
            for (Object child : list)
                stripKeys(child, keys);
        }
    }

    /**
     * Append two slots to the first saved design of {@code slots} length, and say how long it
     * ended up -- so the test fails loudly if the shape it meant to corrupt was not there.
     */
    @SuppressWarnings("unchecked")
    private static int lengthenOneDesign(Object node, int slots) {
        if (node instanceof Map<?, ?> map) {
            Object design = map.get("design");
            if (design instanceof List<?> list && list.size() == slots) {
                List<Object> longer = new ArrayList<>(list);
                longer.add("TypeIV");
                longer.add("TypeIV");
                ((Map<String, Object>) map).put("design", longer);
                return longer.size();
            }
            for (Object child : map.values()) {
                int n = lengthenOneDesign(child, slots);
                if (n > 0) return n;
            }
        } else if (node instanceof List<?> list) {
            for (Object child : list) {
                int n = lengthenOneDesign(child, slots);
                if (n > 0) return n;
            }
        }
        return 0;
    }

    @Test
    public void anEmptiedHoldStaysEmpty() {
        Ship ship = withEcmOnOneFighter();
        DroneStore hold = ship.getShuttles().getDroneStore();
        while (hold.take() != null) {
            // spend it all
        }
        assertTrue(hold.isEmpty());

        assertTrue("an empty hold refilled itself across the save",
                roundTrip(ship).getShuttles().getDroneStore().isEmpty());
    }
}
