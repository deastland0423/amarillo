package com.sfb.scenario;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.FighterComplement;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.systemgroups.ReadyRack;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;

/**
 * Choosing which fighter a casual carrier's facilities serve, as a Commander's Option (J4.621).
 *
 * <h2>Why it is a COI choice and not ship data</h2>
 * J4.621: "the fighters on the carrier will determine what type of ready racks are on the escort."
 * A literal fighter type would be wrong in a ship FILE — wrong in every year but one — and is
 * exactly right in a Commander's Option, which is made for one scenario with the year settled. So
 * the hull declares a line and the player picks a model the line actually fields that year.
 *
 * <h2>The ordering this has to survive</h2>
 * The two halves arrive at different times. Facilities are fitted when the ship is BUILT, from the
 * line's standard fighter, because nothing then knows what the player will choose; {@code applyCoi}
 * runs afterwards. So a choice has to REPLACE what the first pass derived, which is what
 * {@code refitFacilities} is for — and getting that wrong would leave the choice silently ignored,
 * the ship servicing the default and the player believing otherwise.
 */
public class FighterFacilityCoiTest {

    private ShipSpec k5dSpec;

    @Before
    public void load() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        k5dSpec = ShipLibrary.get("Romulan", "K5D");
        assertNotNull("fixture: the K5D should be in the library", k5dSpec);
    }

    /** A scenario spec carrying only what applyCoi reads from it. */
    private static ScenarioSpec specFor(int year) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = year;
        return spec;
    }

    /** The K5D as a scenario builds it: facilities fitted from the line's standard fighter. */
    private Ship builtFor(int year) {
        Ship ship = ShipLibrary.createShip(k5dSpec);
        FighterComplement.reseat(ship, year);
        return ship;
    }

    private static List<ShuttleSpace> boxesOf(Ship ship) {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            boxes.addAll(bay.getSpaces());
        return boxes;
    }

    private static String servedType(Ship ship) {
        for (ShuttleSpace box : boxesOf(ship))
            if (box.getReadyRack() != null)
                return box.getReadyRack().getServesFighterType();
        return null;
    }

    // ---------------------------------------------------------------- what is offered

    /**
     * The models offered are the ones the line fields that year — and they MOVE with it, which is
     * the whole reason the choice is made per scenario rather than written into the hull.
     */
    @Test
    public void theModelsOfferedFollowTheYear() {
        Map<String, String> y173 = FighterComplement.facilityModelsFor("romulan-gladiator", 173);
        Map<String, String> y183 = FighterComplement.facilityModelsFor("romulan-gladiator", 183);

        assertEquals("gsf", y173.get("superiority"));
        assertEquals("gd", y183.get("superiority"));
        assertNotEquals("a different list each era", y173, y183);
    }

    /**
     * Only models a facility can be built FOR. J4.898: "Electronic warfare pods can be added to
     * any fighters in any fighter box... or in a non-fighter box" — so a pure EW fighter has no
     * facility of its own, and offering it would equip a ship with nothing while it declared two.
     * <p>
     * Derived rather than a rule about EW, which is the point: the Y183 G-D-E carries two plasma-D
     * rails and IS offered, while the Y173 G-SF-E carries pods and a phaser and is not.
     */
    @Test
    public void onlyModelsThatNeedAFacilityAreOffered() {
        Map<String, String> y173 = FighterComplement.facilityModelsFor("romulan-gladiator", 173);
        assertFalse("the G-SF-E needs no facility (J4.898)", y173.containsValue("gsf_e"));
        assertFalse(ShuttleSpace.needsFighterFacility("gsf_e"));

        Map<String, String> y183 = FighterComplement.facilityModelsFor("romulan-gladiator", 183);
        assertTrue("but the G-D-E carries plasma-D rails", y183.containsValue("gd_e"));
        assertTrue(ShuttleSpace.needsFighterFacility("gd_e"));
    }

    /** A line that has not begun offers nothing rather than throwing. */
    @Test
    public void aLineThatHasNotBegunOffersNothing() {
        assertTrue(FighterComplement.facilityModelsFor("klingon-zegurnii", 160).isEmpty());
        assertTrue(FighterComplement.facilityModelsFor("not-a-line", 180).isEmpty());
    }

    // ---------------------------------------------------------------- applying the choice

    /** With no choice, the ship keeps the line's standard fighter — nothing changes. */
    @Test
    public void noChoiceLeavesTheStandardFighter() {
        Ship ship = builtFor(173);
        assertEquals("gsf", servedType(ship));

        ScenarioLoader.applyCoi(ship, new CoiLoadout(), specFor(173));

        assertEquals("gsf", servedType(ship));
    }

    /**
     * A choice REPLACES what the build derived. The assertion that matters: the facilities were
     * already fitted for the G-SF when this ran, so a first-fitting that refused to overwrite
     * would have left them that way and ignored the player.
     */
    @Test
    public void aChoiceReplacesTheFacilitiesAlreadyFitted() {
        Ship ship = builtFor(183);
        assertEquals("fixture: built for the standard fighter", "gd", servedType(ship));

        CoiLoadout loadout = new CoiLoadout();
        loadout.fighterFacilityType = "gd_e";
        ScenarioLoader.applyCoi(ship, loadout, specFor(183));

        assertEquals("gd_e", servedType(ship));
        assertEquals("and both boxes, not just the first", 2, boxesWithFacilities(ship));
    }

    /**
     * Choosing a model whose weapon lives in a capacitor rather than a rack swaps the KIND of
     * facility, not just its contents (J4.73). The Gladiator-2's plasma-F is rearmed from a stasis
     * box, so the rack goes away entirely.
     */
    @Test
    public void choosingAPlasmaFFighterSwapsTheRackForAStasisBox() {
        Ship ship = builtFor(173);
        assertNotNull("fixture: a plasma-D rack to begin with", servedType(ship));

        CoiLoadout loadout = new CoiLoadout();
        loadout.fighterFacilityType = "g2";
        ScenarioLoader.applyCoi(ship, loadout, specFor(173));

        for (ShuttleSpace box : boxesOf(ship)) {
            assertNull("the plasma-D rack is gone", box.getReadyRack());
            assertEquals(ShuttleSpace.CapacitorKind.PLASMA_F, box.getCapacitorKind());
            assertEquals("full on arrival (J4.886)", 1, box.getCapacitorCharges());
        }
    }

    /**
     * A choice the line does not field that year falls back to the standard fighter rather than
     * stripping the ship — the refit clears before it fits, so a bad choice could otherwise leave
     * the boxes empty, which is worse than ignoring it.
     */
    @Test
    public void anUnavailableChoiceFallsBackRatherThanStrippingTheShip() {
        Ship ship = builtFor(173);

        CoiLoadout loadout = new CoiLoadout();
        loadout.fighterFacilityType = "f14";        // Federation, not in the Gladiator line
        ScenarioLoader.applyCoi(ship, loadout, specFor(173));

        assertEquals("gsf", servedType(ship));
        assertEquals(2, boxesWithFacilities(ship));
    }

    /** A carrier's own boxes are never touched by this: they are equipped from their occupants. */
    @Test
    public void aRealCarriersBoxesAreLeftAlone() throws Exception {
        Ship krv = ShipLibrary.createShip(ShipLibrary.get("Romulan", "KRV"));
        FighterComplement.reseat(krv, 173);
        int before = boxesWithFacilities(krv);
        assertTrue("fixture: the KRV equips its own boxes", before > 0);

        CoiLoadout loadout = new CoiLoadout();
        loadout.fighterFacilityType = "g2";
        ScenarioLoader.applyCoi(krv, loadout, specFor(173));

        assertEquals("unchanged — no bay declares facilities", before, boxesWithFacilities(krv));
    }

    private static int boxesWithFacilities(Ship ship) {
        int n = 0;
        for (ShuttleSpace box : boxesOf(ship))
            if (box.getReadyRack() != null
                    || box.getCapacitorKind() != ShuttleSpace.CapacitorKind.NONE)
                n++;
        return n;
    }

    /** Unused import guard: ReadyRack is referenced through servedType's return value. */
    @Test
    public void theRackIsAPlasmaRackForARomulanEscort() {
        Ship ship = builtFor(173);
        for (ShuttleSpace box : boxesOf(ship)) {
            ReadyRack rack = box.getReadyRack();
            if (rack != null)
                assertTrue("type-D torpedoes, not drones", rack.isPlasmaD());
        }
    }
}
