package com.sfb.dto;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * How the reload stockpile reaches a client: ONCE, for the ship.
 *
 * <h2>The bug this shape exists to prevent</h2>
 * The DTO reported a reload pool per rack. That was honest while each rack held its own drones, but
 * FD2.422 makes the stockpile the ship's — "not directly associated with any particular rack and can
 * be loaded onto any rack on the ship" — so once every rack drew from the whole ship, a two-rack hull
 * advertised the same drones twice. The client capped each rack's picker at the pool it was shown, so
 * a player could ask both racks for the same four drones; the server's {@code take()} is
 * authoritative, so rack 1 got them and rack 2 silently came up short of what the screen had offered.
 *
 * <p>So this pins the shape rather than the arithmetic: one list at ship level, nothing but
 * anti-drones per rack. A count can be re-derived; a duplicated pool cannot be made safe downstream.
 */
public class ReloadPoolDtoTest {

    private Game game;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();

        Player klingonPlayer = new Player();
        klingonPlayer.setTeamName("Klingons");
        Player fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(10, 10));
        klingon.setOwner(klingonPlayer);
        game.getShips().add(klingon);

        Ship fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Testbed");
        fed.setLocation(new Location(12, 10));
        fed.setOwner(fedPlayer);
        game.getShips().add(fed);
    }

    private GameStateDto.ShipDto shipIn(GameStateDto dto, String name) {
        for (GameStateDto.MapObjectDto o : dto.mapObjects)
            if (name.equals(o.name) && o instanceof GameStateDto.ShipDto s)
                return s;
        fail("no ship " + name + " in the view");
        return null;
    }

    private int rackCount() {
        int n = 0;
        for (Weapon w : klingon.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack)
                n++;
        return n;
    }

    /** The owner sees the stockpile, counted by type, once. */
    @Test
    public void theOwnerGetsOnePoolForTheShip() {
        int held = klingon.reloadStockpile().held().size();
        assertTrue("fixture: the D7 stocks reloads", held > 0);
        assertTrue("fixture: and has more than one rack", rackCount() >= 2);

        GameStateDto.ShipDto dto = shipIn(new GameStateDto(game, "Klingons"), "IKV Saber");

        assertNotNull("the ship reports its stockpile", dto.reloadPool);
        int counted = 0;
        for (GameStateDto.ReloadPoolEntryDto e : dto.reloadPool)
            counted += e.count;
        assertEquals("every drone, once", held, counted);
    }

    /**
     * And the racks do not each report it again. Stated as "no rack names a DRONE type" rather than
     * "the rack pool is empty", because a type-G legitimately keeps something there.
     */
    @Test
    public void noRackRepeatsTheShipsDrones() {
        GameStateDto.ShipDto dto = shipIn(new GameStateDto(game, "Klingons"), "IKV Saber");

        assertFalse("fixture: there are racks to check", dto.droneRacks.isEmpty());
        for (GameStateDto.DroneRackDto rack : dto.droneRacks) {
            assertNotNull(rack.reloadPool);
            for (GameStateDto.ReloadPoolEntryDto e : rack.reloadPool)
                assertEquals(rack.name + " reports a drone the ship already reported",
                        DroneRack.ANTI_DRONE_POOL_KEY, e.droneType);
        }
    }

    /**
     * FD3.72's anti-drone set stays with its rack. An ADD round is not a Drone, it can only be loaded
     * into the rack that holds it, and FD2.42 spends it against the same two spaces a drone would —
     * so it is the one thing that did NOT move to the ship, and the dialog has to show both.
     */
    @Test
    public void aTypeGKeepsItsAntiDroneReserveOnTheRack() {
        List<DroneRack> gRacks = new ArrayList<>();
        for (Weapon w : klingon.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack && rack.acceptsAntiDrones())
                gRacks.add(rack);

        // The D7 carries no type-G, so make one say so: the claim is about the DTO, not the hull.
        DroneRack typeG = new DroneRack(DroneRack.DroneRackType.TYPE_G);
        typeG.setDesignator("Rack 9");
        klingon.getWeapons().addWeapon(typeG);
        typeG.setAddReloads(4);
        assertTrue("fixture: a type-G accepts anti-drones", typeG.acceptsAntiDrones());

        GameStateDto.ShipDto dto = shipIn(new GameStateDto(game, "Klingons"), "IKV Saber");

        GameStateDto.DroneRackDto reported = null;
        for (GameStateDto.DroneRackDto r : dto.droneRacks)
            if (r.name.equals(typeG.getName()))
                reported = r;
        assertNotNull("the type-G is in the view", reported);
        assertEquals("its reserve, and only its reserve", 1, reported.reloadPool.size());
        assertEquals(DroneRack.ANTI_DRONE_POOL_KEY, reported.reloadPool.get(0).droneType);
        assertEquals(4, reported.reloadPool.get(0).count);
        assertEquals("half a space each (FD2.42)",
                DroneRack.ANTI_DRONE_SPACE, reported.reloadPool.get(0).rackSize, 0.001);

        // And the pre-existing racks were not given one.
        assertTrue("fixture: the D7's own racks are not type-G", gRacks.isEmpty());
    }

    /**
     * An enemy gets none of it. G4.233: "the drones aboard a ship... cannot be determined", and how
     * many reloads are left is how many more launches a ship has in it — the same secret as the
     * carrier's drone store and the cargo boxes' contents, both of which are already withheld.
     */
    @Test
    public void anEnemySeesNoStockpileAtAll() {
        GameStateDto.ShipDto enemyView = shipIn(new GameStateDto(game, "Federation"), "IKV Saber");

        assertNull("the stockpile is not an enemy's to count", enemyView.reloadPool);
        assertTrue("nor are the racks' contents",
                enemyView.droneRacks == null || enemyView.droneRacks.isEmpty());
    }
}
