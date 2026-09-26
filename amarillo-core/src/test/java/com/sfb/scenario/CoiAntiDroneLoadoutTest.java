package com.sfb.scenario;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.DroneRack;

/**
 * Type-G, slice 4: the player chooses the anti-drone mix before the battle.
 * <p>
 * FD3.70: "The unique nature of type-G drone racks means that their loading must always be
 * planned (FD2.421)." Nothing reloads an anti-drone into a G rack in play — FD3.70 is
 * explicit that it does NOT auto-reload the way an ADD rack does (E5.74) — so what the
 * player picks here is what the ship fights with.
 * <p>
 * FD3.72 gives the shape of the choice: "A Federation ship on the Klingon front might have
 * two, four, or even six anti-drones on the rack... the Federation player may select the
 * actual load at his own discretion."
 */
public class CoiAntiDroneLoadoutTest {

    private Ship ship;
    private ScenarioSpec spec;

    @Before
    public void setUp() {
        ship = new Ship();
        ship.init(FederationShips.getFedOcl());   // carries a type-G rack
        ship.setName("USS Ocelot");

        spec = new ScenarioSpec();
        spec.year = 175;
        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.shipName = "USS Ocelot";
        setup.weaponStatus = 3;
        side.ships = List.of(setup);
        spec.sides = List.of(side);
    }

    private DroneRack rack() {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack)
                return (DroneRack) w;
        throw new AssertionError("fixture needs a drone rack");
    }

    /** A loadout that leaves the rack's drones alone and only names anti-drones. */
    private CoiLoadout loadoutOf(int antiDrones, DroneType... drones) {
        CoiLoadout out = new CoiLoadout();
        if (drones.length > 0)
            out.droneRackLoadouts.put(0, List.of(drones));
        if (antiDrones > 0)
            out.antiDroneLoadouts.put(0, antiDrones);
        return out;
    }

    /**
     * A loadout where the player emptied the rack to make room, which is what the dialog
     * sends as an explicit empty list. Distinct from naming no drones at all, which means
     * "leave what is aboard alone".
     */
    private CoiLoadout emptiedRack(int antiDrones) {
        CoiLoadout out = new CoiLoadout();
        out.droneRackLoadouts.put(0, List.of());
        out.antiDroneLoadouts.put(0, antiDrones);
        return out;
    }

    // ---------------------------------------------------------------- the choice lands

    @Test
    public void aTypeGIsLoadedWithTheAntiDronesAsked() {
        assertTrue("premise: the OCL's rack is a type-G", rack().acceptsAntiDrones());

        // The OCL arrives with four Type-I drones filling all four spaces, so the player
        // has to clear the rack to make room — which is the choice FD3.72 describes.
        ScenarioLoader.applyCoi(ship, emptiedRack(6), spec);

        assertEquals("six anti-drones, as FD3.72's Klingon-front example has it",
                6, rack().getAddAmmo());
        assertEquals("three of the four spaces", 3.0, rack().spacesUsed(), 1e-9);
    }

    /** The mix FD3.72 describes: drones in the rack with a couple of anti-drones beside them. */
    @Test
    public void dronesAndAntiDronesLoadTogether() {
        ScenarioLoader.applyCoi(ship, loadoutOf(2, DroneType.TypeI, DroneType.TypeIV), spec);

        assertEquals(2, rack().getAddAmmo());
        assertEquals("a Type-I and a Type-IV", 2, rack().getAmmo().size());
        assertEquals("1 + 2 + two halves", 4.0, rack().spacesUsed(), 1e-9);
    }

    /** A rack may be given anti-drones and no drones at all. */
    @Test
    public void aRackMayBeAllAntiDrones() {
        ScenarioLoader.applyCoi(ship, emptiedRack(8), spec);

        assertEquals(8, rack().getAddAmmo());
        assertTrue("no drones aboard", rack().getAmmo().isEmpty());
        assertFalse("but the rack is loaded", rack().isEmpty());
    }

    /**
     * Found in a playtest the moment the feature met a real ship: a rack arrives from
     * WeaponFactory already full of Type-I drones, and a player who asks for anti-drones
     * without touching the drone list sends no droneRackLoadouts entry for that rack. The
     * loader replaced the ammo with an empty list anyway and stripped the rack bare.
     */
    @Test
    public void askingOnlyForAntiDronesKeepsTheDronesTheRackCameWith() {
        rack().setAmmo(new java.util.ArrayList<>(List.of(
                new com.sfb.objects.Drone(DroneType.TypeI),
                new com.sfb.objects.Drone(DroneType.TypeI))));
        assertEquals(2, rack().getAmmo().size());

        ScenarioLoader.applyCoi(ship, loadoutOf(4), spec);

        assertEquals("the drones it came with are still aboard", 2, rack().getAmmo().size());
        assertEquals("and the anti-drones are in beside them", 4, rack().getAddAmmo());
        assertEquals("two spaces of drones, two of anti-drones",
                4.0, rack().spacesUsed(), 1e-9);
    }

    /** And the shared budget is measured against those drones, not against an empty rack. */
    @Test
    public void theBudgetCountsDronesTheLoadoutDidNotMention() {
        rack().setAmmo(new java.util.ArrayList<>(List.of(
                new com.sfb.objects.Drone(DroneType.TypeI),
                new com.sfb.objects.Drone(DroneType.TypeI),
                new com.sfb.objects.Drone(DroneType.TypeI))));

        ScenarioLoader.applyCoi(ship, loadoutOf(4), spec);   // 3 + 2 spaces = 5 in a 4-rack

        assertEquals("refused: it would not fit beside the drones already there",
                0, rack().getAddAmmo());
        assertEquals(3, rack().getAmmo().size());
    }

    // ---------------------------------------------------------------- and is bounded

    /**
     * One magazine, one budget (FD3.70). Two full-space drones plus six anti-drones is five
     * spaces in a four-space rack, so the whole loadout is refused rather than half-applied.
     */
    @Test
    public void aLoadoutThatOverflowsTheSharedMagazineIsRefused() {
        ScenarioLoader.applyCoi(ship,
                loadoutOf(6, DroneType.TypeI, DroneType.TypeIV), spec);

        assertEquals("nothing was loaded", 0, rack().getAddAmmo());
        assertTrue(ship.getSetupNotes().stream().anyMatch(n -> n.contains("exceeds rack size")));
    }

    @Test
    public void thereAreNoAntiDronesBeforeY140() {
        spec.year = 139;

        ScenarioLoader.applyCoi(ship, loadoutOf(4), spec);

        assertEquals(0, rack().getAddAmmo());
        assertTrue("and the player is told why",
                ship.getSetupNotes().stream().anyMatch(n -> n.contains("Y140")));
    }

    @Test
    public void anOrdinaryRackIsToldItCannotCarryThem() {
        Ship ca = new Ship();
        ca.init(com.sfb.samples.KlingonShips.getD7());   // type-F racks, no ADD targeting
        ca.setName("IKV Saber");
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.shipName = "IKV Saber";
        setup.weaponStatus = 3;
        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.ships = List.of(setup);
        spec.sides = List.of(side);

        ScenarioLoader.applyCoi(ca, loadoutOf(4), spec);

        for (com.sfb.weapons.Weapon w : ca.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack)
                assertEquals(0, ((DroneRack) w).getAddAmmo());
        assertTrue("and the player is told why",
                ca.getSetupNotes().stream().anyMatch(n -> n.contains("FD3.70")));
    }
}
