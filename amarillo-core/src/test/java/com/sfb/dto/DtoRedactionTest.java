package com.sfb.dto;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Per-viewer redaction of hidden information (2026-07-13 ruling): enemy
 * seeking shuttles render as plain shuttles until identified, plasma keeps
 * type/pseudo/target secret until identified, drone types stay unknown,
 * enemy bay contents and rack loadouts are not sent at all — and Wild
 * Weasels are public the moment they launch ("pinging away 'I'm a ship!'").
 */
public class DtoRedactionTest {

    private Game game;
    private Ship fed;
    private Ship klingon;
    private Player fedPlayer;
    private Player klingonPlayer;

    @Before
    public void setUp() {
        game = new Game();

        fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");
        klingonPlayer = new Player();
        klingonPlayer.setTeamName("Klingons");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setOwner(fedPlayer);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(20, 10));
        klingon.setOwner(klingonPlayer);

        game.getShips().add(fed);
        game.getShips().add(klingon);
    }

    private GameStateDto.MapObjectDto find(GameStateDto dto, String name) {
        return dto.mapObjects.stream()
                .filter(o -> name.equals(o.name))
                .findFirst().orElse(null);
    }

    private SuicideShuttle klingonSuicideShuttle() {
        SuicideShuttle ss = new SuicideShuttle(new AdminShuttle());
        ss.setName("IKV Saber-Shuttle-1");
        ss.setLocation(new Location(18, 10));
        ss.setOwner(klingonPlayer);
        ss.setTarget(fed);
        ss.setController(klingon);
        game.getSeekers().add(ss);
        return ss;
    }

    // -------------------------------------------------------------------------
    // Seeking shuttles
    // -------------------------------------------------------------------------

    @Test
    public void enemySuicideShuttle_rendersAsPlainShuttle() {
        klingonSuicideShuttle();

        GameStateDto fedView = new GameStateDto(game, "Federation");
        GameStateDto.MapObjectDto obj = find(fedView, "IKV Saber-Shuttle-1");

        assertNotNull(obj);
        assertFalse("Enemy must not see the suicide-shuttle identity",
                obj instanceof GameStateDto.SuicideShuttleDto);
        assertTrue(obj instanceof GameStateDto.ShuttleDto);
    }

    @Test
    public void ownSuicideShuttle_rendersFully() {
        klingonSuicideShuttle();

        GameStateDto klingonView = new GameStateDto(game, "Klingons");

        assertTrue(find(klingonView, "IKV Saber-Shuttle-1")
                instanceof GameStateDto.SuicideShuttleDto);
    }

    @Test
    public void identifiedEnemySuicideShuttle_isRevealed() {
        SuicideShuttle ss = klingonSuicideShuttle();
        ss.identify(); // lab identification (SeekerControl)

        GameStateDto fedView = new GameStateDto(game, "Federation");

        assertTrue(find(fedView, "IKV Saber-Shuttle-1")
                instanceof GameStateDto.SuicideShuttleDto);
    }

    @Test
    public void enemyUnreleasedScatterPack_rendersAsPlainShuttle() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("IKV Saber-Shuttle-2");
        pack.setLocation(new Location(18, 11));
        pack.setOwner(klingonPlayer);
        pack.addDrone(new Drone(DroneType.TypeI));
        game.getSeekers().add(pack);

        GameStateDto fedView = new GameStateDto(game, "Federation");
        GameStateDto klingonView = new GameStateDto(game, "Klingons");

        assertFalse(find(fedView, "IKV Saber-Shuttle-2") instanceof GameStateDto.ScatterPackDto);
        assertTrue(find(klingonView, "IKV Saber-Shuttle-2") instanceof GameStateDto.ScatterPackDto);
    }

    @Test
    public void wildWeasel_isPublicToTheEnemy() {
        WildWeaselShuttle ww = new WildWeaselShuttle(klingon);
        ww.setName("IKV Saber-Shuttle-3");
        ww.setLocation(new Location(20, 11));
        ww.setOwner(klingonPlayer);
        game.getActiveShuttles().add(ww);

        GameStateDto fedView = new GameStateDto(game, "Federation");

        assertTrue("A weasel announces itself at launch",
                find(fedView, "IKV Saber-Shuttle-3") instanceof GameStateDto.WildWeaselDto);
    }

    // -------------------------------------------------------------------------
    // Plasma and drones
    // -------------------------------------------------------------------------

    @Test
    public void enemyPseudoPlasma_readsAsRealAndTypeless() {
        PlasmaTorpedo torp = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
        torp.setName("IKV Saber-Plasma-1");
        torp.setLocation(new Location(19, 10));
        torp.setPseudoPlasma(true);
        torp.setController(klingon);
        torp.setTarget(fed);
        game.getSeekers().add(torp);

        GameStateDto fedView = new GameStateDto(game, "Federation");
        GameStateDto.PlasmaTorpedoDto dto =
                (GameStateDto.PlasmaTorpedoDto) find(fedView, "IKV Saber-Plasma-1");

        assertFalse("Pseudo status must be hidden (FP1.4)", dto.pseudo);
        assertEquals("Type must be hidden until identified", "?", dto.plasmaType);
        assertNull(dto.targetName);

        GameStateDto klingonView = new GameStateDto(game, "Klingons");
        GameStateDto.PlasmaTorpedoDto own =
                (GameStateDto.PlasmaTorpedoDto) find(klingonView, "IKV Saber-Plasma-1");
        assertTrue("Owner sees the truth", own.pseudo);
        assertEquals("G", own.plasmaType);
    }

    @Test
    public void enemyDrone_typeAndWarheadHidden() {
        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("IKV Saber-Drone-1");
        drone.setLocation(new Location(18, 12));
        drone.setController(klingon);
        drone.setTarget(fed);
        game.getSeekers().add(drone);

        GameStateDto fedView = new GameStateDto(game, "Federation");
        GameStateDto.DroneDto dto = (GameStateDto.DroneDto) find(fedView, "IKV Saber-Drone-1");
        assertEquals("?", dto.droneType);
        assertEquals(0, dto.warheadDamage);

        GameStateDto klingonView = new GameStateDto(game, "Klingons");
        GameStateDto.DroneDto own = (GameStateDto.DroneDto) find(klingonView, "IKV Saber-Drone-1");
        assertEquals("TypeI", own.droneType);
    }

    // -------------------------------------------------------------------------
    // Ship secrets and the omniscient view
    // -------------------------------------------------------------------------

    @Test
    public void enemyShip_baysAndRackLoadoutsHidden() {
        GameStateDto fedView = new GameStateDto(game, "Federation");
        GameStateDto.ShipDto enemy = (GameStateDto.ShipDto) find(fedView, "IKV Saber");
        GameStateDto.ShipDto own = (GameStateDto.ShipDto) find(fedView, "USS Enterprise");

        assertTrue("Enemy bay contents must not be sent", enemy.shuttleBays.isEmpty());
        assertTrue("Enemy rack loadouts must not be sent", enemy.droneRacks.isEmpty());
        assertFalse("Own bays fully visible", own.shuttleBays.isEmpty());
    }

    @Test
    public void activeEsgField_strengthIsPublic_butStoredEnergyIsSecret() {
        // G23.46: an active field's size and strength are known to every player. The
        // generator's stored/allocated energy stays the owner's secret (G23.311).
        com.sfb.weapons.ESG esg = new com.sfb.weapons.ESG();
        esg.setDesignator("A");
        esg.setHasCapacitor(true);
        fed.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(7);
        esg.activate(2, 0); // releases 5 → strength chart[2][5] = 17; capacitor keeps 2
        assertEquals(17, esg.getStrength());
        assertEquals(2, esg.getStoredEnergy());

        GameStateDto.WeaponDto enemy = esgOf((GameStateDto.ShipDto)
                find(new GameStateDto(game, "Klingons"), "USS Enterprise"));
        assertTrue(enemy.esgActive);
        assertEquals("strength is public (G23.46)", 17, enemy.esgStrength);
        assertEquals("radius is public (G23.46)", 2, enemy.esgRadius);
        assertEquals("stored energy stays secret (G23.311)", 0, enemy.esgStoredEnergy);

        GameStateDto.WeaponDto owner = esgOf((GameStateDto.ShipDto)
                find(new GameStateDto(game, "Federation"), "USS Enterprise"));
        assertEquals("owner sees its own stored energy", 2, owner.esgStoredEnergy);
        assertEquals(17, owner.esgStrength);
    }

    private GameStateDto.WeaponDto esgOf(GameStateDto.ShipDto ship) {
        for (GameStateDto.WeaponDto w : ship.weapons)
            if (w.esg) return w;
        return null;
    }

    @Test
    public void omniscientView_seesEverything() {
        klingonSuicideShuttle();

        GameStateDto solo = new GameStateDto(game);

        assertTrue(find(solo, "IKV Saber-Shuttle-1") instanceof GameStateDto.SuicideShuttleDto);
        GameStateDto.ShipDto klingonDto = (GameStateDto.ShipDto) find(solo, "IKV Saber");
        assertFalse(klingonDto.shuttleBays.isEmpty());
    }
}
