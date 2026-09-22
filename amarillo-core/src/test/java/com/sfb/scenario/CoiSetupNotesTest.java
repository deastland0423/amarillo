package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * What happens when a Captain's Option Item cannot be applied.
 *
 * It used to print to System.err and stop there, so the setup quietly came out different
 * from the one chosen and the first sign of it was an option missing mid-battle. A scatter
 * pack was the case that found this: the drones for it have to come out of the ship's own
 * racks, and if none of the chosen type is there the pack is built empty — which the launch
 * action refuses, since a pack needs a payload.
 * <p>
 * Worse after the prepared-shuttle rule: an empty pack cannot launch as a pack AND cannot
 * launch as an ordinary shuttle either, so that bay space was dead for the whole battle.
 */
public class CoiSetupNotesTest {

    private Ship ship;
    private ScenarioSpec spec;

    @Before
    public void setUp() {
        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");

        // A spec that names this ship at a weapon status allowing a special shuttle.
        spec = new ScenarioSpec();
        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.shipName = "USS Enterprise";
        setup.weaponStatus = 3;
        side.ships = List.of(setup);
        spec.sides = List.of(side);
    }

    private Shuttle firstShuttle() {
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (Shuttle s : bay.getInventory())
                return s;
        throw new AssertionError("fixture needs a shuttle in a bay");
    }

    private CoiLoadout packOf(String shuttleName, com.sfb.objects.DroneType type) {
        CoiLoadout loadout = new CoiLoadout();
        CoiLoadout.SpecialShuttlePrep prep = new CoiLoadout.SpecialShuttlePrep();
        prep.shuttleName = shuttleName;
        prep.type = "scatterpack";
        prep.drones = List.of(type);
        loadout.specialShuttlePrep.add(prep);
        return loadout;
    }

    /**
     * A Federation CA carries no drone racks, so there is nothing to fill a pack from. The
     * shuttle must stay a shuttle, and the player must be told why.
     */
    @Test
    public void aPackWithNoDronesLeavesTheShuttleAlone() {
        Shuttle before = firstShuttle();
        assertFalse("fixture: a plain shuttle to begin with", before instanceof ScatterPack);

        ScenarioLoader.applyCoi(ship, packOf(before.getName(), com.sfb.objects.DroneType.TypeI), spec);

        assertFalse("an empty pack can neither launch nor revert, so it must not be made",
                firstShuttle() instanceof ScatterPack);
        assertTrue("and it is still an ordinary shuttle, free to launch",
                firstShuttle() instanceof AdminShuttle);
    }

    @Test
    public void thePlayerIsToldWhyTheirScatterPackIsMissing() {
        ScenarioLoader.applyCoi(ship,
                packOf(firstShuttle().getName(), com.sfb.objects.DroneType.TypeI), spec);

        assertFalse("a setup that could not be applied must say so", ship.getSetupNotes().isEmpty());
        assertTrue("and say which shuttle: " + ship.getSetupNotes(),
                ship.getSetupNotes().stream().anyMatch(n -> n.contains("scatterpack")));
    }

    @Test
    public void aShuttleThatDoesNotExistIsReported() {
        ScenarioLoader.applyCoi(ship, packOf("No Such Shuttle", com.sfb.objects.DroneType.TypeI), spec);

        assertTrue("naming a shuttle that is not aboard: " + ship.getSetupNotes(),
                ship.getSetupNotes().stream().anyMatch(n -> n.contains("No Such Shuttle")));
    }

    /** Nothing to report when nothing was asked for. */
    @Test
    public void aShipWithNoCoiHasNothingToSay() {
        ScenarioLoader.applyCoi(ship, new CoiLoadout(), spec);

        assertTrue(ship.getSetupNotes().toString(), ship.getSetupNotes().isEmpty());
    }

    /**
     * Setup notes outlive the turn boundary, unlike allocation notes: they are decided
     * before the battle, and clearing them at turn start would wipe them in the moment
     * before anyone could read them.
     */
    @Test
    public void setupNotesSurviveTheStartOfTheTurn() {
        ScenarioLoader.applyCoi(ship,
                packOf(firstShuttle().getName(), com.sfb.objects.DroneType.TypeI), spec);
        int before = ship.getSetupNotes().size();
        assertTrue(before > 0);

        ship.startTurn();

        assertEquals("still there when the player looks", before, ship.getSetupNotes().size());
    }
}
