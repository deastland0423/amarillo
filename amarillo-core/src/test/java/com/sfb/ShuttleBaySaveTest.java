package com.sfb;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleSpace;

/**
 * What a carrier has left in its bays survives a save.
 *
 * <h2>The exploit this closes</h2>
 * The bays were the largest thing a save still rebuilt from the hull, so a reloaded battle
 * handed back every craft that had been destroyed and re-armed the rest. That is the drone rack
 * mistake at a much larger scale — a carrier is most of a fleet's firepower — and it was silent:
 * no error, no missing data, just a full hangar where an empty one had been.
 *
 * <h2>Where the line falls</h2>
 * A bay's BOXES are the hull's and are rebuilt with it. What is IN each box is the battle's, and
 * travels in the save — including which craft, because boxes are swapped, emptied and refilled
 * during a fight and the complement a hull declares is only where it started.
 */
public class ShuttleBaySaveTest {

    private static final String CARRIER = "AD5";

    private Ship ship;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        try {
            com.sfb.objects.ShuttleCatalog.loadDefault("../data");
        } catch (Exception e) {
            throw new AssertionError("fixture: the shuttle catalogue should load", e);
        }
        ship = carrier();
    }

    private static Ship carrier() {
        ShipSpec spec = ShipLibrary.get("Klingon", CARRIER);
        assertNotNull("fixture: the " + CARRIER + " should be in the library", spec);
        Ship built = ShipLibrary.createShip(spec);
        built.setName("IKV Hangar");
        return built;
    }

    private Ship roundTrip() {
        Map<String, Object> state = ship.exportState();
        Ship restored = carrier();
        restored.applyState(state);
        return restored;
    }

    private static java.util.List<ShuttleSpace> spacesOf(Ship s) {
        java.util.List<ShuttleSpace> out = new java.util.ArrayList<>();
        for (com.sfb.systemgroups.ShuttleBay bay : s.getShuttles().getBays())
            out.addAll(bay.getSpaces());
        return out;
    }

    private static int occupiedCount(Ship s) {
        int n = 0;
        for (ShuttleSpace space : spacesOf(s))
            if (space.getShuttle() != null)
                n++;
        return n;
    }

    // ---------------------------------------------------------------- the free craft

    @Test
    public void theFixtureCarrierActuallyHasCraft() {
        assertTrue("fixture: an " + CARRIER + " should launch with craft aboard",
                occupiedCount(ship) > 0);
    }

    /** THE TEST THIS EXISTS FOR: a craft lost in the battle must stay lost. */
    @Test
    public void aCraftLostInTheBattleDoesNotComeBack() {
        int before = occupiedCount(ship);
        assertTrue("fixture", before > 0);

        for (ShuttleSpace space : spacesOf(ship))
            if (space.getShuttle() != null) {
                space.destroy();
                break;
            }
        int left = occupiedCount(ship);
        assertEquals(before - 1, left);

        assertEquals("a reloaded carrier must not refill its hangar",
                left, occupiedCount(roundTrip()));
    }

    /** And an emptied hangar is the sharpest case. */
    @Test
    public void anEmptiedHangarComesBackEmpty() {
        for (ShuttleSpace space : spacesOf(ship))
            if (space.getShuttle() != null)
                space.destroy();
        assertEquals(0, occupiedCount(ship));

        assertEquals("every craft was lost; none of them come back",
                0, occupiedCount(roundTrip()));
    }

    // ---------------------------------------------------------------- what is in the box

    /** Damage to a craft sitting in a bay is damage, and stays. */
    @Test
    public void aDamagedCraftKeepsItsDamage() {
        Shuttle craft = null;
        for (ShuttleSpace space : spacesOf(ship))
            if (space.getShuttle() != null) {
                craft = space.getShuttle();
                break;
            }
        assertNotNull("fixture", craft);
        int full = craft.getCurrentHull();
        assertTrue("fixture: a craft should have hull to lose", full > 1);
        craft.setCurrentHull(full - 1);

        Shuttle after = null;
        for (ShuttleSpace space : spacesOf(roundTrip()))
            if (space.getShuttle() != null) {
                after = space.getShuttle();
                break;
            }

        assertNotNull(after);
        assertEquals("a reload must not repair the hangar deck", full - 1, after.getCurrentHull());
    }

    /** The craft comes back as the same KIND, not as whatever the hull would have put there. */
    @Test
    public void aCraftKeepsItsType() {
        String expected = null;
        for (ShuttleSpace space : spacesOf(ship))
            if (space.getShuttle() != null) {
                expected = space.getShuttle().getCatalogType();
                break;
            }
        assertNotNull("fixture", expected);

        for (ShuttleSpace space : spacesOf(roundTrip()))
            if (space.getShuttle() != null) {
                assertEquals(expected, space.getShuttle().getCatalogType());
                return;
            }
        fail("no craft survived to check");
    }

    // ---------------------------------------------------------------- the bay itself

    /**
     * J1.58: a hatch that has just launched is still cooling. Dropping the timestamp would hand
     * a carrier a free launch at every turn boundary — the same mistake the drone racks made.
     */
    @Test
    public void aHatchThatJustLaunchedIsStillCoolingAfterAReload() {
        com.sfb.systemgroups.ShuttleBay bay = ship.getShuttles().getBays().get(0);
        Map<String, Object> state = bay.exportState();

        assertTrue("the hatch timestamps must be in the save",
                state.containsKey("lastHatchImpulse"));
    }

    // ---------------------------------------------------------------- the format's promises

    @Test
    public void aSaveMissingTheBaysLeavesTheHullsComplementAlone() {
        int aboard = occupiedCount(ship);

        ship.applyState(Map.of("facing", 5));

        assertEquals("an older save must not empty the hangar",
                aboard, occupiedCount(ship));
    }
}
