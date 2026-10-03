package com.sfb.systemgroups;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * Type-D torpedoes through the carrier's stores (J4.825), which is what made a plasma-D
 * fighter flyable rather than merely armed-at-setup.
 * <p>
 * J4.825: "The rearming and storage rules for drones are used for type-D plasma torpedoes,
 * with the exception that type-Ds require activation energy (FP9.22). A type-D torpedo is the
 * same size as a one-space drone."
 *
 * <h2>What was broken, and why nothing noticed</h2>
 * A {@code PLASMA_D} rail has no design DRONE - there is no ordinary drone load for a fighter
 * J4.825 bars drones from - and three systems read exactly that field to decide what to do.
 * So the Romulan KRV came off its ship file with:
 * <ul>
 *   <li>no ready rack on any Gladiator-F box ({@code ReadyRack.forFighter} skips rails with
 *       no design drone),</li>
 *   <li>an EMPTY hold - sixty declared spaces, nothing in them, because the stocking pattern
 *       is read off the racks' designs,</li>
 *   <li>and {@code needsArming} false, so a deck crew found no work and the squadron never
 *       appeared in the hangar's list at all.</li>
 * </ul>
 * Every guard passed. Five fighters that could never load a torpedo looked exactly like five
 * fighters that were ready.
 *
 * <h2>The shape of the fix</h2>
 * Torpedoes are held as COUNTS, in the same hold the drones use. FP9.18 denies them variants -
 * "Pl-Ds, like other plasmas, do not have guidance options, different speeds, or warhead
 * modules as drones do" - so one type-D is any other and there is nothing to distinguish. That
 * is why there are no {@code Drone} objects standing in for torpedoes anywhere: a count is the
 * whole truth, and it is a fraction of the machinery the drone path needs.
 */
public class PlasmaDStoresTest {

    private Ship krv;

    @Before
    public void loadTheCarrier() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        krv = ShipLibrary.createShip(ShipLibrary.get("Romulan", "KRV"));
    }

    /** The bay holding the Gladiator-F squadron, and one of its boxes. */
    private ShuttleSpace gladiatorFBox() {
        for (ShuttleBay bay : krv.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() != null && "gf".equals(box.getShuttle().getCatalogType()))
                    return box;
        throw new AssertionError("fixture: the KRV should carry Gladiator-Fs");
    }

    private static java.util.List<DroneRail> plasmaRailsOf(Fighter f) {
        java.util.List<DroneRail> rails = new java.util.ArrayList<>();
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.isPlasmaD())
                rails.add(rail);
        return rails;
    }

    // ---------------------------------------------------------------- the hold

    /**
     * The hold is STOCKED, which it was not. One pool shared with the drones: J4.7 gives a
     * carrier one holding measured in spaces, and J4.825 makes a torpedo one space of it.
     */
    @Test
    public void theHoldIsStockedWithTorpedoes() {
        DroneStore store = krv.getShuttles().getDroneStore();

        assertNotNull("the KRV declares drone storage", store);
        assertEquals("sixty spaces, from Annex #7G", 60.0, store.capacitySpaces(), 0.001);

        // FIFTY in the hold, not sixty. J4.72 counts what is already forward against the
        // declared total: the five Gladiator-F boxes hold two torpedoes apiece, so ten of the
        // sixty are racked and fifty are in the hold. Stocking to capacity instead put seventy
        // spaces of torpedoes in a sixty-space ship.
        assertEquals("fifty in the hold", 50, store.plasmaDCount());
        assertEquals("ten of them already racked", 10.0,
                krv.getShuttles().spacesCommittedForward(), 0.001);
        assertEquals("sixty all told", 60.0,
                store.spacesHeld() + krv.getShuttles().spacesCommittedForward(), 0.001);
    }

    /**
     * And it does not read as empty, which is what hid the problem from the deck crews: a hold
     * counting only its drones answered "empty" beside sixty torpedoes, so the REFILL job was
     * never offered.
     */
    @Test
    public void aHoldOfTorpedoesIsNotEmpty() {
        assertFalse(krv.getShuttles().getDroneStore().isEmpty());
    }

    // ---------------------------------------------------------------- the box's rack

    /** J4.822 and J4.825 together: the box gets a rack, one torpedo per rail. */
    @Test
    public void everyGladiatorFBoxHasAPlasmaRackOfTwo() {
        int boxes = 0;
        for (ShuttleBay bay : krv.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces()) {
                if (box.getShuttle() == null
                        || !"gf".equals(box.getShuttle().getCatalogType()))
                    continue;
                boxes++;
                ReadyRack rack = box.getReadyRack();
                assertNotNull("a G-F box needs a rack (J4.825)", rack);
                assertTrue(rack.isPlasmaD());
                assertEquals("one torpedo per rail", 2, rack.capacity());
                assertEquals("full at scenario start (J4.886)", 2, rack.count());
                assertEquals("two spaces of the ship's stores", 2.0, rack.spaces(), 0.001);
            }
        assertEquals("the KRV's five Gladiator-Fs", 5, boxes);
    }

    /** The EW variant carries no rails, so its box gets no plasma rack. */
    @Test
    public void theEwFighterBoxGetsNoPlasmaRack() {
        for (ShuttleBay bay : krv.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() != null
                        && "gf_e".equals(box.getShuttle().getCatalogType())) {
                    ReadyRack rack = box.getReadyRack();
                    assertTrue("a G-FE has nothing to rack",
                            rack == null || !rack.isPlasmaD());
                    return;
                }
        fail("fixture: the KRV should carry a G-FE");
    }

    // ---------------------------------------------------------------- the deck crew

    /** The symptom that made this a bug: a loaded carrier whose crews had nothing to do. */
    @Test
    public void aGladiatorFGivesADeckCrewWork() {
        Fighter gf = (Fighter) gladiatorFBox().getShuttle();

        assertTrue("built empty (J4.8223), so there is work (J4.825)",
                FighterArming.needsArming(gf));
        assertEquals("two torpedoes, an action each", 2 * FighterArming.HALF_ACTIONS_PER_ACTION,
                FighterArming.halfActionsToFullyArm(gf));
    }

    /**
     * Rack to rails, one deck crew action per torpedo (J4.825 through J4.82). And the torpedoes
     * arrive INERT: FP9.22's half point is the ship's to pay, not a crew's to spend.
     */
    @Test
    public void aDeckCrewMovesTorpedoesFromRackToRails() {
        ShuttleSpace box = gladiatorFBox();
        Fighter gf = (Fighter) box.getShuttle();
        for (DroneRail rail : plasmaRailsOf(gf))
            assertFalse("fixture: a fighter is built empty", rail.isLoaded());

        FighterArming.Load load = FighterArming.load(box, gf,
                2 * FighterArming.HALF_ACTIONS_PER_ACTION);

        assertEquals("two whole actions", 2 * FighterArming.HALF_ACTIONS_PER_ACTION,
                load.halfActionsUsed());
        assertTrue(load.note(), load.note().contains("J4.825"));
        assertTrue("the crew says what it has not done", load.note().contains("FP9.22"));
        assertEquals("the rack gave up both", 0, box.getReadyRack().count());
        for (DroneRail rail : plasmaRailsOf(gf)) {
            assertTrue("loaded", rail.isLoaded());
            assertFalse("but inert until the ship pays (FP9.22)", rail.isTorpedoActivated());
        }
    }

    /** Half an action buys nothing: a torpedo is a whole action (J4.8174). */
    @Test
    public void halfAnActionLoadsNoTorpedo() {
        ShuttleSpace box = gladiatorFBox();
        Fighter gf = (Fighter) box.getShuttle();

        assertEquals(0, FighterArming.load(box, gf, 1).halfActionsUsed());

        assertEquals("the rack keeps them", 2, box.getReadyRack().count());
    }

    // ---------------------------------------------------------------- the refill

    /** Hold to rack, which is what lets a squadron fly more than one sortie. */
    @Test
    public void aDeckCrewRefillsTheRackFromTheHold() {
        ShuttleSpace box = gladiatorFBox();
        Fighter gf = (Fighter) box.getShuttle();
        DroneStore store = krv.getShuttles().getDroneStore();
        FighterArming.load(box, gf, 2 * FighterArming.HALF_ACTIONS_PER_ACTION);
        assertEquals("fixture: the rack is spent", 0, box.getReadyRack().count());
        int held = store.plasmaDCount();

        FighterArming.Load refill = FighterArming.refill(box, store,
                2 * FighterArming.HALF_ACTIONS_PER_ACTION);

        assertEquals(2 * FighterArming.HALF_ACTIONS_PER_ACTION, refill.halfActionsUsed());
        assertEquals("rack full again", 2, box.getReadyRack().count());
        assertEquals("two fewer in the hold", held - 2, store.plasmaDCount());
        assertTrue(refill.note(), refill.note().contains("J4.825"));
    }

    /** The hangar must OFFER the job, which is the half the crew-wanted gate decides. */
    @Test
    public void theHangarOffersTheRefillJob() {
        ShuttleSpace box = gladiatorFBox();
        Fighter gf = (Fighter) box.getShuttle();
        FighterArming.load(box, gf, 2 * FighterArming.HALF_ACTIONS_PER_ACTION);

        int crews = Shuttles.crewsWantedFor(CrewTask.REFILL, box, gf,
                krv.getShuttles().getDroneStore());

        assertTrue("a spent plasma rack beside a full hold is a job (J4.821)", crews > 0);
    }

    /** An exhausted hold says so rather than conjuring torpedoes. */
    @Test
    public void anEmptyHoldRefillsNothing() {
        ShuttleSpace box = gladiatorFBox();
        Fighter gf = (Fighter) box.getShuttle();
        DroneStore store = krv.getShuttles().getDroneStore();
        FighterArming.load(box, gf, 2 * FighterArming.HALF_ACTIONS_PER_ACTION);
        while (store.takePlasmaD()) { /* empty the hold */ }

        FighterArming.Load refill = FighterArming.refill(box, store,
                2 * FighterArming.HALF_ACTIONS_PER_ACTION);

        assertEquals(0, refill.halfActionsUsed());
        assertEquals(0, box.getReadyRack().count());
        assertTrue(refill.note(), refill.note().contains("J4.825"));
    }

    // ---------------------------------------------------------------- the whole cycle

    /**
     * Hold to rack to rails to activated and launchable, on a real hull. The chain this slice
     * exists for: before it, a Gladiator-F could be armed only by a test reaching in.
     */
    @Test
    public void theWholeCycleRunsOnTheKrv() {
        ShuttleSpace box = gladiatorFBox();
        Fighter gf = (Fighter) box.getShuttle();
        DroneStore store = krv.getShuttles().getDroneStore();

        // 1. a crew arms the fighter from its box
        FighterArming.load(box, gf, 2 * FighterArming.HALF_ACTIONS_PER_ACTION);
        // 2. the ship pays FP9.22
        assertEquals("half a point per torpedo", 1.0,
                krv.getShuttles().plasmaDActivationWanted(), 0.001);
        assertEquals(1.0, krv.getShuttles().activatePlasmaDs(1.0), 0.001);
        // 3. both are ready to fire
        for (DroneRail rail : plasmaRailsOf(gf))
            assertTrue("launchable", rail.canLaunchTorpedo());
        // 4. and the box is restocked for the next sortie
        FighterArming.refill(box, store, 2 * FighterArming.HALF_ACTIONS_PER_ACTION);
        assertEquals(2, box.getReadyRack().count());

        assertEquals("the refill drew two out of the hold", 48, store.plasmaDCount());
    }
}
