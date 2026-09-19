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

    /**
     * G4.233: "A successful attempt reveals if the shuttle is manned or unmanned and if it
     * is following a seeking course ... but not if it is carrying drones or a suicide
     * bomb."
     * <p>
     * This test used to assert the opposite — that identification revealed the suicide
     * shuttle outright, warhead and arming turns and all. It was wrong against the book,
     * and it held the wrong behaviour in place. What a lab buys is that the thing is on a
     * seeking course and what it is aimed at; whether the bang comes from a bomb or from a
     * bellyful of drones is exactly what stays hidden.
     */
    @Test
    public void identifiedEnemySuicideShuttle_revealsItsCourseButNotItsBomb() {
        SuicideShuttle ss = klingonSuicideShuttle();
        ss.identify(); // lab identification (G4.2)

        GameStateDto fedView = new GameStateDto(game, "Federation");
        GameStateDto.MapObjectDto obj = find(fedView, "IKV Saber-Shuttle-1");

        assertFalse("identification must not reveal the suicide bomb (G4.233)",
                obj instanceof GameStateDto.SuicideShuttleDto);
        assertTrue("it stays a plain shuttle to the enemy", obj instanceof GameStateDto.ShuttleDto);

        GameStateDto.ShuttleDto sd = (GameStateDto.ShuttleDto) obj;
        assertTrue("but the enemy now knows it was identified", sd.isIdentified);
        assertTrue("and that it is on a seeking course (G4.233)", sd.seekingCourse);
        assertEquals("with its target, as for a drone (G4.231)",
                "USS Enterprise", sd.seekingTargetName);
    }

    /**
     * The other half of the same rule, and the reason it matters: an identified suicide
     * shuttle and an identified scatter pack must be indistinguishable. If either one
     * revealed its payload, the bluff between them would be over.
     */
    @Test
    public void anIdentifiedPackAndAnIdentifiedSuicideShuttleReadAlike() {
        SuicideShuttle ss = klingonSuicideShuttle();
        ss.identify();

        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("IKV Saber-Shuttle-3");
        pack.setLocation(new Location(18, 12));
        pack.setOwner(klingonPlayer);
        pack.setTarget(fed);
        pack.setController(klingon);
        pack.addDrone(new Drone(DroneType.TypeI));
        game.getSeekers().add(pack);
        pack.identify();

        GameStateDto fedView = new GameStateDto(game, "Federation");
        GameStateDto.MapObjectDto a = find(fedView, "IKV Saber-Shuttle-1");
        GameStateDto.MapObjectDto b = find(fedView, "IKV Saber-Shuttle-3");

        assertEquals("the two must arrive as the same kind of object",
                a.getClass(), b.getClass());
        assertTrue(a instanceof GameStateDto.ShuttleDto);

        GameStateDto.ShuttleDto sa = (GameStateDto.ShuttleDto) a;
        GameStateDto.ShuttleDto sb = (GameStateDto.ShuttleDto) b;
        assertEquals("both report a seeking course", sa.seekingCourse, sb.seekingCourse);
        assertEquals("both report the same target", sa.seekingTargetName, sb.seekingTargetName);
    }

    /**
     * G4.233's other clause: manning is revealed, and only once identified. It is sent as
     * a Boolean because FALSE is the informative value here — a primitive would report
     * every unidentified shuttle on the map as unmanned.
     */
    @Test
    public void manningIsRevealedOnlyByIdentification() {
        SuicideShuttle ss = klingonSuicideShuttle();

        GameStateDto.ShuttleDto before = (GameStateDto.ShuttleDto)
                find(new GameStateDto(game, "Federation"), "IKV Saber-Shuttle-1");
        assertNull("nothing is known about its crew yet", before.manned);

        ss.identify();

        GameStateDto.ShuttleDto after = (GameStateDto.ShuttleDto)
                find(new GameStateDto(game, "Federation"), "IKV Saber-Shuttle-1");
        assertNotNull(after.manned);
        assertFalse("a suicide shuttle flies empty (G4.233)", after.manned);
    }

    @Test
    public void anIdentifiedPlainShuttleIsReportedManned() {
        com.sfb.objects.shuttles.AdminShuttle admin = new com.sfb.objects.shuttles.AdminShuttle();
        admin.setName("IKV Saber-Shuttle-9");
        admin.setLocation(new Location(18, 14));
        admin.setOwner(klingonPlayer);
        game.getActiveShuttles().add(admin);
        admin.identify();

        GameStateDto.ShuttleDto dto = (GameStateDto.ShuttleDto)
                find(new GameStateDto(game, "Federation"), "IKV Saber-Shuttle-9");

        assertEquals(Boolean.TRUE, dto.manned);
        assertFalse("and it is not on a seeking course", dto.seekingCourse);
    }

    /** Releasing the drones is what makes a pack public — not being identified. */
    @Test
    public void aReleasedPackIsPublic() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("IKV Saber-Shuttle-4");
        pack.setLocation(new Location(18, 13));
        pack.setOwner(klingonPlayer);
        pack.addDrone(new Drone(DroneType.TypeI));
        game.getSeekers().add(pack);
        pack.release();

        GameStateDto fedView = new GameStateDto(game, "Federation");

        assertTrue("everyone saw the drones come out",
                find(fedView, "IKV Saber-Shuttle-4") instanceof GameStateDto.ScatterPackDto);
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
        assertEquals("Type must be hidden from the enemy", "?", dto.plasmaType);
        assertNull(dto.targetName);

        GameStateDto klingonView = new GameStateDto(game, "Klingons");
        GameStateDto.PlasmaTorpedoDto own =
                (GameStateDto.PlasmaTorpedoDto) find(klingonView, "IKV Saber-Plasma-1");
        assertTrue("Owner sees the truth", own.pseudo);
        assertEquals("G", own.plasmaType);
    }

    /**
     * G4.232: "Labs can only reveal the target of a plasma torpedo ... Note that players
     * cannot distinguish between plasma torpedoes and pseudo-plasma torpedoes."
     * <p>
     * So identification buys the target and stops. It used to open the whole record: an
     * identified torpedo handed the enemy its type AND its pseudo status, which is the one
     * fact a pseudo exists to keep. Nothing caught it because nothing tested an identified
     * plasma at all — the existing test only ever looked at an unidentified one.
     */
    @Test
    public void identifiedEnemyPlasma_revealsItsTargetAndNothingElse() {
        PlasmaTorpedo torp = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
        torp.setName("IKV Saber-Plasma-2");
        torp.setLocation(new Location(19, 11));
        torp.setPseudoPlasma(true);
        torp.setController(klingon);
        torp.setTarget(fed);
        game.getSeekers().add(torp);
        torp.identify();

        GameStateDto.PlasmaTorpedoDto dto = (GameStateDto.PlasmaTorpedoDto)
                find(new GameStateDto(game, "Federation"), "IKV Saber-Plasma-2");

        assertEquals("the target is what a lab buys (G4.232)", "USS Enterprise", dto.targetName);
        assertFalse("a pseudo stays indistinguishable even after identification (G4.232)",
                dto.pseudo);
        assertEquals("and the type is not part of what is revealed (G4.232)",
                "?", dto.plasmaType);
        assertTrue("strength is always known either way (FP1.32)", dto.currentStrength >= 0);
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

    /**
     * Every shuttle reports its weapons, not just fighters.
     * <p>
     * An admin shuttle builds itself a 360-degree Ph-3, and core and the fire-options
     * endpoint have always been willing to fire it — but the DTO populated `weapons` only
     * for a Fighter, so the client never learned the shuttle was armed. The UI picks a
     * shuttle as an attacker on `weapons.length > 0`, so the phaser was unreachable from
     * inside a game: the capability existed at every level except the one that offers it.
     */
    @Test
    public void everyShuttleReportsItsWeapons_notJustFighters() {
        com.sfb.objects.shuttles.AdminShuttle admin = new com.sfb.objects.shuttles.AdminShuttle();
        admin.setName("USS Enterprise-Shuttle-1");
        admin.setLocation(new Location(12, 12));
        admin.setOwner(fedPlayer);
        game.getActiveShuttles().add(admin);

        GameStateDto view = new GameStateDto(game, "Federation");
        Object obj = find(view, "USS Enterprise-Shuttle-1");

        assertTrue("expected a shuttle DTO", obj instanceof GameStateDto.ShuttleDto);
        GameStateDto.ShuttleDto dto = (GameStateDto.ShuttleDto) obj;
        assertNotNull("an admin shuttle's Ph-3 must reach the client", dto.weapons);
        assertFalse("...and not be an empty list", dto.weapons.isEmpty());
        assertFalse("but it is not a fighter, whatever it is carrying", dto.isFighter);
    }

    @Test
    public void aFighterIsFlaggedAsOne() {
        // The type label used to be inferred from "has weapons", which stops working the
        // moment every shuttle reports its phaser.
        com.sfb.objects.shuttles.Stinger1 f = new com.sfb.objects.shuttles.Stinger1();
        f.setName("Alpha 1");
        f.setLocation(new Location(13, 13));
        f.setOwner(fedPlayer);
        game.getActiveShuttles().add(f);

        GameStateDto view = new GameStateDto(game, "Federation");
        GameStateDto.ShuttleDto dto = (GameStateDto.ShuttleDto) find(view, "Alpha 1");

        assertTrue("a Stinger is a fighter", dto.isFighter);
    }

    /**
     * A ship's TOTAL ECM, including a Wild Weasel's six points.
     * <p>
     * The panel summed allocated + lent and stopped, so a weasel's contribution (J3.23)
     * and an Orion's built-in stealth (G15.8) were invisible — the player shooting at the
     * ship first learned of them from the dice roll. EW strength is public by rule (D6.32
     * has it announced in the lock-on segment), so there is nothing to withhold.
     */
    @Test
    public void ecmTotalCountsAWeaselsContribution() {
        fed.setEcmAllocated(2);
        GameStateDto before = new GameStateDto(game, "Federation");
        GameStateDto.ShipDto dtoBefore = (GameStateDto.ShipDto) find(before, fed.getName());
        assertEquals("just the two it generated", 2, dtoBefore.ecmTotal);

        com.sfb.objects.shuttles.WildWeaselShuttle ww =
                new com.sfb.objects.shuttles.WildWeaselShuttle(fed);
        fed.setActiveWildWeasel(ww);

        GameStateDto after = new GameStateDto(game, "Federation");
        GameStateDto.ShipDto dtoAfter = (GameStateDto.ShipDto) find(after, fed.getName());

        assertEquals("2 generated + 6 from the weasel", 8, dtoAfter.ecmTotal);
        assertNotNull("and it must say where they came from", dtoAfter.ecmSources);
        assertTrue(dtoAfter.ecmSources, dtoAfter.ecmSources.contains("lent"));
    }

    @Test
    public void ecmSourcesNamesOnlyWhatContributes() {
        fed.setEcmAllocated(3);

        GameStateDto view = new GameStateDto(game, "Federation");
        GameStateDto.ShipDto dto = (GameStateDto.ShipDto) find(view, fed.getName());

        assertEquals("3 generated", dto.ecmSources);
    }
}
