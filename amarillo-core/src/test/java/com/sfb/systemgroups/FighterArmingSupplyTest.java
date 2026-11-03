package com.sfb.systemgroups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * Where a fighter's drones come from when the weapon status arms it (J4.8224, J4.882).
 *
 * <blockquote>J4.8224: "At WS-III the fighters are loaded but <b>their weapons were taken from
 * the ready racks</b>, which have not been refilled."</blockquote>
 *
 * <h2>They were not taken; they were invented</h2>
 * {@code FighterArming.armFully} filled each rail with
 * {@code new Drone(rail.getDesignDrone())} — a fresh drone of the rail's design type, which is
 * the frame's BASELINE — and {@code ShuttleSpace.armOccupantFully} then balanced the books by
 * subtracting a count from the rack. The count came out right and the drones did not.
 *
 * <p>So a Y183 Kzinti CVS had its ready racks refitted to speed-32 type-IFs by
 * {@code ScenarioLoader.applyDroneSpeed} and then launched its whole squadron carrying speed-8
 * type-Is: the substitution threw the refit away. That is the same obsolete-drone defect
 * {@code DroneSpeedYearTest} closed for the ship's own racks, surviving one layer further down.
 * It showed up as a pricing oddity first — a Y183 CVS owed 80 at WS-0, 90 at WS-2 and 32 at
 * WS-3, because WS-2 is the one status that goes through the deck-crew budget and moves the
 * real drone across.
 *
 * <h2>And the books only balanced from a FULL rack</h2>
 * The reconciliation topped the rack back up from the same design types before subtracting, so
 * a PART-DRAWN rack gained drones: three in the rack and a fighter wanting six came out as six
 * on the rails and none in the rack. Six for three. At setup that never fires, because J4.8223
 * rests a carrier with its racks full — after a sortie it would.
 *
 * @see FighterArming#armFully(ShuttleSpace, Shuttle)
 */
public class FighterArmingSupplyTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.loadDefault("../data");
    }

    // ---------------------------------------------------------------- helpers

    /** A box holding a fresh fighter, with the ready rack its occupant implies (J4.822). */
    private static ShuttleSpace boxOf(String catalogueType) {
        Fighter f = CataloguedFighter.of(catalogueType);
        f.setName(catalogueType + "-1");
        ShuttleSpace box = new ShuttleSpace(f);
        assertNotNull("fixture: " + catalogueType + " should get a ready rack",
                box.getReadyRack());
        return box;
    }

    private static List<DroneRail> railsOf(Shuttle fighter) {
        List<DroneRail> rails = new ArrayList<>();
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                rails.add(rail);
        return rails;
    }

    private static List<Drone> dronesOnRailsOf(Shuttle fighter) {
        List<Drone> drones = new ArrayList<>();
        for (DroneRail rail : railsOf(fighter))
            if (rail.getDrone() != null)
                drones.add(rail.getDrone());
        return drones;
    }

    /** Everything in the box and on its fighter — what the carrier owns in that box. */
    private static int dronesInBox(ShuttleSpace box) {
        return box.getReadyRack().count() + dronesOnRailsOf(box.getShuttle()).size();
    }

    /** Stand in for the year's refit: swap the rack's stock for fast drones (FD2.224). */
    private static void refitRackToFast(ShuttleSpace box) {
        box.getReadyRack().refit(d -> new Drone(DroneType.TypeIF));
        for (Drone d : box.getReadyRack().contents())
            assertEquals("fixture: the rack now holds fast drones", 32, d.getSpeed());
    }

    // ---------------------------------------------------------------- the drones themselves

    /**
     * The headline: whatever the refit put in the rack is what the fighter launches with.
     * <p>
     * An AAS has two standard rails, so its rack holds two type-Is. Refit them to type-IFs the
     * way a Y180-and-later scenario does, arm the box, and the SAME two drones are on the rails.
     */
    @Test
    public void aFighterIsArmedWithTheVeryDronesFromItsReadyRack() {
        ShuttleSpace box = boxOf("aas");
        refitRackToFast(box);
        List<Drone> wereInTheRack = new ArrayList<>(box.getReadyRack().contents());
        assertEquals("fixture: two standard rails, two drones", 2, wereInTheRack.size());

        box.armOccupantFully();

        List<Drone> onTheRails = dronesOnRailsOf(box.getShuttle());
        assertEquals("both rails loaded", 2, onTheRails.size());
        for (Drone flying : onTheRails) {
            assertEquals("the year's speed reached the fighter (FD2.224)", 32, flying.getSpeed());
            boolean sameObject = false;
            for (Drone stored : wereInTheRack)
                sameObject |= stored == flying;
            assertTrue("it is the rack's own drone, not a copy of the rail's design type",
                    sameObject);
        }
    }

    /**
     * The same statement from the failing side, in case the identity check above is ever
     * loosened: a design drone is a type-I-SLOW, so this is the assertion that would have
     * caught the bug on its own.
     */
    @Test
    public void aFighterDoesNotLaunchWithItsFramesBaselineDrones() {
        ShuttleSpace box = boxOf("aas");
        refitRackToFast(box);

        box.armOccupantFully();

        for (Drone flying : dronesOnRailsOf(box.getShuttle()))
            assertFalse("a speed-8 type-I is what the RAIL is designed around, not what the"
                    + " carrier is carrying", flying.getSpeed() == 8);
    }

    /** J4.8224's second clause: "...which have not been refilled." */
    @Test
    public void theReadyRackIsNotRefilledAfterwards() {
        ShuttleSpace box = boxOf("aas");

        box.armOccupantFully();

        assertTrue("the rack paid for the loading and stays empty",
                box.getReadyRack().isEmpty());
    }

    // ---------------------------------------------------------------- supply

    /**
     * J4.882: "The ship cannot reload the fighter directly, but must reload the storage
     * capacitor." The rack is the only source, so an empty one leaves the rail empty rather
     * than conjuring a drone to fill it.
     */
    @Test
    public void anEmptyReadyRackLeavesTheRailEmpty() {
        ShuttleSpace box = boxOf("aas");
        while (box.getReadyRack().take() != null) {
            // a squadron that has already flown
        }

        box.armOccupantFully();

        assertEquals("nothing to arm it with, so nothing on the rails",
                0, dronesOnRailsOf(box.getShuttle()).size());
        assertEquals("and nothing was invented", 0, dronesInBox(box));
    }

    /**
     * Arming moves drones; it never makes them. A part-drawn rack is the case the old
     * reconciliation got wrong, because it topped the rack back up from the rails' design types
     * before subtracting what the fighter now carried.
     */
    @Test
    public void armingNeverCreatesDrones() {
        ShuttleSpace box = boxOf("tadsc");          // six rails, so a big rack to go short of
        int full = box.getReadyRack().count();
        assertEquals("fixture: a TADSC loading is six", 6, full);

        assertNotNull("fixture: draw the rack down", box.getReadyRack().take());
        assertNotNull("fixture: and again", box.getReadyRack().take());
        int aboard = dronesInBox(box);
        assertEquals("fixture: four left in this box", 4, aboard);

        box.armOccupantFully();

        assertEquals("four is what it had and four is what it has",
                aboard, dronesInBox(box));
        assertEquals("all four forward onto the rails", 4,
                dronesOnRailsOf(box.getShuttle()).size());
        assertTrue("with nothing left behind", box.getReadyRack().isEmpty());
    }

    /**
     * J4.8225: a rail is never handed a drone it cannot carry — "the ready rack held a type-I
     * drone in a position that corresponded to a type-VI drone rail, that drone could not be
     * loaded."
     *
     * <p>Free, because {@link ReadyRack#takeFor} does the choosing. A TAAS has two standard
     * rails and two light ones; a rack of nothing but type-Is can fill the standard pair and
     * must leave the light pair alone.
     */
    @Test
    public void aRailIsNeverHandedADroneItCannotCarry() {
        ShuttleSpace box = boxOf("taas");
        box.getReadyRack().refit(d -> new Drone(DroneType.TypeI));   // all one space

        box.armOccupantFully();

        int loaded = 0;
        for (DroneRail rail : railsOf(box.getShuttle())) {
            if (rail.getDrone() == null)
                continue;
            loaded++;
            assertTrue("a one-space type-I cannot ride a half-space light rail",
                    rail.getRailType() != DroneRail.DroneRailType.LIGHT);
        }
        assertEquals("the two standard rails, and only those", 2, loaded);
    }

    // ---------------------------------------------------------------- what did not change

    /**
     * The no-box overload keeps its old behaviour, which is the right behaviour for it: a craft
     * nobody is holding stores for has nothing to draw from, so the rail's design type is the
     * only answer available. Tests and in-flight fixtures depend on it.
     */
    @Test
    public void aFighterWithNoBoxStillArmsFromItsRailsOwnDesign() {
        Fighter loose = CataloguedFighter.of("aas");

        FighterArming.armFully(loose);

        List<Drone> onTheRails = dronesOnRailsOf(loose);
        assertEquals("both rails loaded", 2, onTheRails.size());
        for (Drone d : onTheRails)
            assertEquals("a standard rail is designed around a type-I",
                    DroneType.TypeI, d.getDroneType());
    }

    /**
     * Plasma still reconciles by count rather than moving objects, and that stays correct:
     * FP9.18 gives a type-D no variants — "no guidance options, different speeds, or warhead
     * modules as drones do" — so one torpedo is any other and the substitution loses nothing.
     *
     * <p>A Romulan Gladiator-F: two plasma-D rails, so a rack of two torpedoes and a count that
     * has to come back to zero.
     */
    @Test
    public void aPlasmaFightersTorpedoesStillBalanceByCount() {
        ShuttleSpace box = boxOf("gf");
        assertTrue("fixture: a plasma-D rack", box.getReadyRack().isPlasmaD());
        assertEquals("fixture: one torpedo per rail", 2, box.getReadyRack().count());

        box.armOccupantFully();

        int armed = 0;
        for (DroneRail rail : railsOf(box.getShuttle())) {
            assertNull("J4.825: no drones on a plasma-armed fighter", rail.getDrone());
            if (rail.getTorpedo() != null)
                armed++;
        }
        assertEquals("both rails carry a torpedo", 2, armed);
        assertEquals("drawn from the box, which is now empty", 0, box.getReadyRack().count());
    }
}
