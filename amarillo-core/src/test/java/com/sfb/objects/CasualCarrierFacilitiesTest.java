package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.properties.CarrierClass;
import com.sfb.systemgroups.ReadyRack;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;

/**
 * Fighter service facilities on a ship that carries no fighters — the casual carrier (J4.62).
 *
 * <h2>What the rules say, and what had to change</h2>
 * J4.62: "Some ships have ready racks for fighters, and may even carry one or two, but are not
 * 'carriers' within the context of the rules. These ships are known as 'casual carriers'. Examples
 * include most carrier escorts, the Hydran Pegasus and Gendarme, and many WYN ships."
 *
 * <p>A rack used to be DERIVED FROM THE FIGHTER IN THE BOX, so a ship with no fighters could not
 * have one at all. Three pieces close that:
 * <ul>
 *   <li>the bay declares how many of its boxes can SERVICE a fighter — a count of BOXES, because
 *       J4.822 puts the fitting in a box and J4.831 destroys it with the box, so an escort's
 *       facilities take damage exactly as a carrier's do. G33.43 shows the numbers differ ("four
 *       shuttle boxes with two ready racks"), so it cannot be inferred;</li>
 *   <li>the bay names a LINE and no counts, because J4.621 makes the carrier's fighters decide
 *       what the racks serve and a literal fighter type would be wrong in every year but one;</li>
 *   <li>the player picks the actual MODEL as a Commander's Option, which is sound there precisely
 *       because the scenario year is settled by then.</li>
 * </ul>
 *
 * <h2>Why it is FACILITIES and not just racks</h2>
 * J4.89 licenses one name for all of them: ready racks and storage boxes "can be included in the
 * general term 'ready rack' or 'fighter facility' or 'weapons charge storage facility' or
 * 'capacitor' for purposes of these rules." A ready rack is one of the four species. J4.73: "Federation ships have a
 * 'photon freezer' to supply photon torpedoes for their A-10 attack shuttles; Romulan, ISC, and
 * Gorn ships have stasis boxes to store extra plasma-F torpedoes. Hydran ships have facilities to
 * store charges for fusion beams and hellbores." Which one a box gets depends entirely on what its
 * fighter carries — so an escort supporting a plasma-F fighter needs a capacitor and has no rack.
 */
public class CasualCarrierFacilitiesTest {

    private ShipSpec k5dSpec;

    @Before
    public void load() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        k5dSpec = ShipLibrary.get("Romulan", "K5D");
        assertNotNull("fixture: the K5D should be in the library", k5dSpec);
    }

    /** The K5D as a scenario would build it: a year, and optionally a chosen fighter model. */
    private Ship k5d(int year, String chosenModel) {
        Ship ship = ShipLibrary.createShip(k5dSpec);
        ship.setFighterFacilityType(chosenModel);
        FighterComplement.reseat(ship, year);
        return ship;
    }

    private static List<ShuttleSpace> boxesOf(Ship ship) {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            boxes.addAll(bay.getSpaces());
        return boxes;
    }

    // ---------------------------------------------------------------- the declaration

    /**
     * J4.62: the K5D is a casual carrier — racks, no fighters. Both halves asserted, because the
     * interesting state is precisely "has the apparatus, carries nobody".
     */
    @Test
    public void theK5dIsACasualCarrierWithNoFightersOfItsOwn() {
        Ship ship = k5d(173, null);

        assertEquals(CarrierClass.CASUAL, ship.getCarrierClass());
        assertFalse("not a carrier for S4.1's provisions", ship.getCarrierClass().isCarrier());

        for (ShuttleSpace box : boxesOf(ship))
            assertFalse("no fighter aboard",
                    box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter);
        assertEquals("two boxes declared with racks", 2,
                ship.getShuttles().getBays().get(0).getFighterFacilities());
    }

    /** And it has the racks, which is the thing that was impossible before. */
    @Test
    public void itsBoxesHaveReadyRacksDespiteHoldingAdminShuttles() {
        Ship ship = k5d(173, null);

        int racked = 0;
        for (ShuttleSpace box : boxesOf(ship)) {
            if (box.getReadyRack() == null)
                continue;
            racked++;
            assertEquals("the box still holds its admin shuttle",
                    "admin", box.getShuttle().getCatalogType());
        }
        assertEquals(2, racked);
    }

    // ---------------------------------------------------------------- J4.621, resolved by year

    /**
     * J4.621: "the fighters on the carrier will determine what type of ready racks are on the
     * escort, and this will in turn determine the numbers of drones held in the racks."
     * <p>
     * With no choice made, the line's standard fighter for the year — and it MOVES with the year,
     * which is the whole reason the escort names a line rather than a type. The Gladiator line
     * goes G-F, G-SF, G-FSF, Glad-D, and the capacity moves with it: the Glad-D carries four
     * plasma-D rails where the G-F carried two.
     */
    @Test
    public void theRackFollowsTheLineAndTheYear() {
        assertEquals("gf", servedType(k5d(165, null)));
        assertEquals("gsf", servedType(k5d(173, null)));
        assertEquals("gd", servedType(k5d(183, null)));

        assertEquals("a G-F's two rails", 2, rackOf(k5d(165, null)).capacity());
        assertEquals("a Glad-D's four", 4, rackOf(k5d(183, null)).capacity());
    }

    /** Romulan racks hold type-D torpedoes, not drones (FP9.21, J4.825). */
    @Test
    public void aRomulanEscortsRacksHoldTypeDTorpedoes() {
        ReadyRack rack = rackOf(k5d(173, null));

        assertTrue("a plasma rack, not a drone rack", rack.isPlasmaD());
        assertEquals("full on arrival (J4.886)", rack.capacity(), rack.count());
    }

    /** Racks start full: J4.886 opens a scenario with the stores forward (J4.8223). */
    @Test
    public void theRacksStartFull() {
        for (ShuttleSpace box : boxesOf(k5d(173, null)))
            if (box.getReadyRack() != null)
                assertTrue(box.getReadyRack().isFull());
    }

    // ---------------------------------------------------------------- the player's choice

    /** A chosen model the line does field that year is honoured. */
    @Test
    public void aChosenModelIsUsed() {
        // The Y183 era offers the Glad-D as superiority and the G-3K as assault; choosing the
        // EW variant gets its racks instead of the default.
        Ship ship = k5d(183, "gd_e");

        assertEquals("gd_e", servedType(ship));
    }

    /**
     * A choice the line does not field that year falls back to the standard fighter rather than
     * leaving the escort unequipped.
     * <p>
     * Validated rather than trusted because a stored COI choice outlives the scenario it was made
     * for: a Federation fighter named on a Romulan escort, or a model retired two eras ago, must
     * not strand the ship with empty boxes.
     */
    @Test
    public void aModelTheLineNeverFieldsFallsBackToTheStandardFighter() {
        assertEquals("an F-14 is not in the Gladiator line", "gsf", servedType(k5d(173, "f14")));
        assertEquals("nor is a retired era's fighter", "gd", servedType(k5d(183, "gf")));
        assertEquals("nor is nonsense", "gsf", servedType(k5d(173, "not-a-fighter")));
    }

    // ---------------------------------------------------------------- J4.73's other facilities

    /**
     * J4.73: a plasma-F fighter is rearmed from a "stasis box" in the shuttle box, not from a
     * ready rack. So an escort supporting the Gladiator-2 gets a PLASMA_F capacitor and no rack —
     * and equipping only racks left it with nothing at all, which is what this pins.
     */
    @Test
    public void anEscortSupportingAPlasmaFFighterGetsAStasisBoxAndNoRack() {
        Ship ship = k5d(173, "g2");

        int equipped = 0;
        for (ShuttleSpace box : boxesOf(ship)) {
            if (box.getCapacitorKind() == ShuttleSpace.CapacitorKind.NONE)
                continue;
            equipped++;
            assertEquals(ShuttleSpace.CapacitorKind.PLASMA_F, box.getCapacitorKind());
            assertEquals("J4.862: one torpedo", 1, box.capacitorCapacity());
            assertEquals("full on arrival (J4.886)", 1, box.getCapacitorCharges());
            assertNull("a plasma-F fighter has no ready rack", box.getReadyRack());
        }
        assertEquals("both declared boxes equipped", 2, equipped);
    }

    // ---------------------------------------------------------------- damage

    /**
     * J4.831: the rack is destroyed with its box. The reason the declaration counts BOXES rather
     * than racks — put the rack anywhere else and it would need its own answer on the DAC.
     */
    @Test
    public void aDestroyedBoxTakesItsRackWithIt() {
        Ship ship = k5d(173, null);
        ShuttleSpace box = boxesOf(ship).get(0);
        assertNotNull(box.getReadyRack());

        box.destroy();

        assertTrue(box.isDestroyed());
        assertFalse("and cannot acquire another", box.fitFacilitiesFor("gsf"));
    }

    // ---------------------------------------------------------------- helpers

    private static ReadyRack rackOf(Ship ship) {
        for (ShuttleSpace box : boxesOf(ship))
            if (box.getReadyRack() != null)
                return box.getReadyRack();
        throw new AssertionError("no ready rack on " + ship.getName());
    }

    private static String servedType(Ship ship) {
        return rackOf(ship).getServesFighterType();
    }
}
