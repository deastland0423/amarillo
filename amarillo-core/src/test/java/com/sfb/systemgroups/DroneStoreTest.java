package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Aas;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * The carrier's supply of spare drones, and the trip from the hold to the ready rack (J4.7,
 * J4.82).
 * <p>
 * Until now a ready rack was the whole of a drone fighter's supply: one reload, and when it
 * was gone the fighter was done for the scenario. J4.7 gives the Kzinti CV 150 "spaces" of
 * spare drones for its fighters, and J4.821 prices the trip out of the hold at one action a
 * space — the same as the trip from the rack onto the fighter, so a type-I costs two actions
 * to get from storage onto a rail.
 * <p>
 * The rule that shapes the design is J4.72: drones in the racks or on the fighters "count as
 * part of the ship's storage". The 150 is a TOTAL, not a reserve behind the racks, so nothing
 * here may create a drone — every transfer is a hand-off.
 */
public class DroneStoreTest {

    private Ship cv;

    @Before
    public void setUp() throws Exception {
        cv = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cv.json")));
        cv.setName("KHS Sabre");
    }

    private Shuttles shuttles() {
        return cv.getShuttles();
    }

    private List<ShuttleSpace> fighterBoxes() {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : shuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getReadyRack() != null)
                    boxes.add(box);
        return boxes;
    }

    private static List<DroneRail> railsOf(Shuttle f) {
        List<DroneRail> rails = new ArrayList<>();
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                rails.add(rail);
        return rails;
    }

    /** Every drone the ship owns, wherever it is sitting — hold, racks, or rails. */
    private double allSpacesAboard() {
        DroneStore store = shuttles().getDroneStore();
        return (store == null ? 0 : store.spacesHeld()) + shuttles().spacesCommittedForward();
    }

    private String boxIdOf(ShuttleSpace box) {
        for (int b = 0; b < shuttles().getBays().size(); b++) {
            List<ShuttleSpace> spaces = shuttles().getBays().get(b).getSpaces();
            for (int i = 0; i < spaces.size(); i++)
                if (spaces.get(i) == box)
                    return Shuttles.boxId(b, i);
        }
        throw new IllegalStateException("box is not in this ship");
    }

    /** Post one job and run the end-of-turn pass, as a real turn does both. */
    private ShuttleBay.RearmResult postAndRun(ShuttleSpace box, CrewTask task, int crews,
            int turn) {
        Map<String, Integer> order = new LinkedHashMap<>();
        order.put(task.keyFor(boxIdOf(box)), crews);
        shuttles().postDeckCrews(12, order);
        ShuttleBay bay = shuttles().getBays().get(0);
        return bay.rearmFighters(turn, shuttles().getDroneStore());
    }

    // -------------------------------------------------------------------------
    // What the ship starts with (J4.7, J4.72)
    // -------------------------------------------------------------------------

    @Test
    public void theCvHoldsTheHundredAndFiftySpacesTheRuleGivesIt() {
        DroneStore store = shuttles().getDroneStore();

        assertNotNull("J4.7 names the Kzinti CV specifically", store);
        assertEquals(150.0, store.capacitySpaces(), 0.001);
    }

    @Test
    public void theRacksAndTheFightersComeOutOfThatTotalRatherThanOnTopOfIt() {
        DroneStore store = shuttles().getDroneStore();
        double forward = shuttles().spacesCommittedForward();

        assertTrue("twelve AAS start with full racks (J4.886), so some is already forward",
                forward > 0);
        assertEquals("J4.72: what is in the racks and on the fighters IS part of the 150",
                150.0, store.spacesHeld() + forward, 0.001);
    }

    @Test
    public void theHoldIsStockedWithWhatTheFightersCanActuallyLoad() {
        DroneStore store = shuttles().getDroneStore();
        ShuttleSpace box = fighterBoxes().get(0);

        assertTrue("an AAS box", box.getShuttle() instanceof Aas);
        for (Drone d : store.contents())
            assertEquals("stocked around the rails the ship actually has",
                    DroneType.TypeI, d.getDroneType());
    }

    @Test
    public void aShipThatDeclaresNoStorageHasNoSupply() throws Exception {
        Ship ca = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/federation/ca.json")));

        assertNull("no Annex #7G line means no supply, not an invented one",
                ca.getShuttles().getDroneStore());
    }

    // -------------------------------------------------------------------------
    // The trip out of the hold (J4.82, J4.821)
    // -------------------------------------------------------------------------

    @Test
    public void aSpentRackIsRefilledFromTheHold() {
        ShuttleSpace box = fighterBoxes().get(0);
        ReadyRack rack = box.getReadyRack();
        while (!rack.isEmpty())
            rack.take();
        double heldBefore = shuttles().getDroneStore().spacesHeld();

        ShuttleBay.RearmResult result = postAndRun(box, CrewTask.REFILL, 2, 2);

        assertEquals("an AAS rack holds two type-Is", 2, rack.count());
        assertEquals("and they came out of the hold, not out of nowhere",
                heldBefore - 2.0, shuttles().getDroneStore().spacesHeld(), 0.001);
        assertFalse(result.log().isEmpty());
    }

    @Test
    public void aSpaceCostsAnAction() {
        ShuttleSpace box = fighterBoxes().get(0);
        ReadyRack rack = box.getReadyRack();
        while (!rack.isEmpty())
            rack.take();

        // One crew is one action, and J4.821 makes a type-I one action a space.
        postAndRun(box, CrewTask.REFILL, 1, 2);

        assertEquals("one crew moves one space of drones in a turn (J4.821)", 1, rack.count());
    }

    @Test
    public void theRackIsRefilledWhileItsFighterIsAway() {
        ShuttleSpace box = fighterBoxes().get(0);
        ReadyRack rack = box.getReadyRack();
        while (!rack.isEmpty())
            rack.take();
        box.setShuttle(null);   // the fighter has launched

        postAndRun(box, CrewTask.REFILL, 2, 2);

        assertEquals("J4.8223: the crews reload the racks while the fighters are on their"
                + " mission, which is the whole point of the job", 2, rack.count());
    }

    @Test
    public void anEmptyHoldCannotRefillAnything() {
        DroneStore store = shuttles().getDroneStore();
        while (!store.isEmpty())
            store.take();
        ShuttleSpace box = fighterBoxes().get(0);
        ReadyRack rack = box.getReadyRack();
        while (!rack.isEmpty())
            rack.take();

        postAndRun(box, CrewTask.REFILL, 2, 2);

        assertEquals("a supply that runs out is the point of tracking it", 0, rack.count());
    }

    // -------------------------------------------------------------------------
    // J4.8172: the rack is not filled and drawn from at once
    // -------------------------------------------------------------------------

    @Test
    public void aRackIsNotFilledAndDrawnFromInTheSameTurn() {
        ShuttleSpace box = fighterBoxes().get(0);

        assertTrue(box.postCrews(CrewTask.LOAD, 2));
        assertFalse("J4.8172: not simultaneously loaded and providing drones for the fighter",
                box.postCrews(CrewTask.REFILL, 2));
        assertEquals(0, box.getCrews(CrewTask.REFILL));

        box.clearCrews();
        assertTrue("and it is refused the other way round too",
                box.postCrews(CrewTask.REFILL, 2));
        assertFalse(box.postCrews(CrewTask.LOAD, 2));
    }

    @Test
    public void refillingIsNotOfferedAsAJobWhileLoadingIsPosted() {
        ShuttleSpace box = fighterBoxes().get(0);
        ReadyRack rack = box.getReadyRack();
        while (!rack.isEmpty())
            rack.take();
        box.postCrews(CrewTask.LOAD, 1);

        assertEquals("a job the rule forbids is not on the panel either", 0,
                Shuttles.crewsWantedFor(CrewTask.REFILL, box, box.getShuttle(),
                        shuttles().getDroneStore()));
    }

    @Test
    public void refillingHasItsOwnPairOfCrewsBesideTheTwoWorkingTheFighter() {
        ShuttleSpace box = fighterBoxes().get(0);
        // A fighter with damage to mend keeps two crews busy; the rack is a separate job, and
        // J4.8172 says "two MORE deck crews can load the ready rack in that box".
        Shuttle fighter = box.getShuttle();
        fighter.setCurrentHull(fighter.getHull() - 3);
        box.getReadyRack().take();   // leave a slot so there is something to refill

        Map<String, Integer> order = new LinkedHashMap<>();
        order.put(CrewTask.REPAIR.keyFor(boxIdOf(box)), 2);
        order.put(CrewTask.REFILL.keyFor(boxIdOf(box)), 2);
        shuttles().postDeckCrews(12, order);

        assertEquals("two on the fighter", 2, box.getCrews(CrewTask.REPAIR));
        assertEquals("and two more on the rack, not two shared between them",
                2, box.getCrews(CrewTask.REFILL));
    }

    // -------------------------------------------------------------------------
    // Conservation
    // -------------------------------------------------------------------------

    @Test
    public void dronesAreMovedAroundTheShipAndNeverCreated() {
        double atStart = allSpacesAboard();
        ShuttleSpace box = fighterBoxes().get(0);

        // Arm the fighter from its rack, then refill the rack from the hold — the full
        // journey J4.82 describes, in both of its legs.
        postAndRun(box, CrewTask.LOAD, 2, 2);
        assertEquals("the fighter is armed", 2, railsOf(box.getShuttle()).stream()
                .filter(r -> r.getDrone() != null).count());
        box.clearCrews();
        postAndRun(box, CrewTask.REFILL, 2, 3);

        assertEquals("the ship owns exactly what it owned; the drones only moved",
                atStart, allSpacesAboard(), 0.001);
    }
}
