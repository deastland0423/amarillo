package com.sfb.dto;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * What a shuttle sitting in a bay reports about itself.
 *
 * The branches that fill this in are ordered ROLE first, capability second, and that order
 * is the whole point of this test. A scatter pack built from an admin shuttle keeps the
 * admin catalogue type — conversions deliberately remember what they were built from — and
 * canBecomeWildWeasel() reads the catalogue (J3.18). So the pack answers TRUE to the weasel
 * question, and when that branch came first the pack reported a charge count and never its
 * payload. The launch list needs a payload, so a perfectly good scatter pack could not be
 * launched at all, with nothing to say why.
 */
public class BayShuttleDtoTest {

    private Game game;
    private Ship klingon;
    private ShuttleBay bay;

    @Before
    public void setUp() {
        game = new Game();

        Player empire = new Player();
        empire.setTeamName("Klingon");

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Vengeance");
        klingon.setLocation(new Location(10, 10));
        klingon.setOwner(empire);
        game.getShips().add(klingon);

        bay = klingon.getShuttles().getBays().get(0);
        assertFalse("fixture needs a bay space", bay.getSpaces().isEmpty());
    }

    private GameStateDto.ShuttleInBayDto inBay(String name) {
        GameStateDto dto = new GameStateDto(game, "Klingon");
        for (GameStateDto.MapObjectDto o : dto.mapObjects) {
            if (!(o instanceof GameStateDto.ShipDto))
                continue;
            for (GameStateDto.ShuttleBayDto bd : ((GameStateDto.ShipDto) o).shuttleBays)
                for (GameStateDto.ShuttleInBayDto sd : bd.shuttles)
                    if (name.equals(sd.name))
                        return sd;
        }
        return null;
    }

    @Test
    public void aScatterPackReportsItsPayload() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("IKV Vengeance-Admin-1");
        pack.addDrone(new Drone(DroneType.TypeI));
        pack.addDrone(new Drone(DroneType.TypeI));
        bay.getSpaces().get(0).setShuttle(pack);

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-Admin-1");

        assertNotNull("the pack should be in the bay listing", sd);
        assertEquals("scatterpack", sd.type);
        assertNotNull("a pack with no payload cannot be launched, so this must be sent",
                sd.payload);
        assertEquals(2, sd.payload.size());
        assertNull("and it is a pack, not a weasel waiting to be charged",
                sd.wwChargeCount);
    }

    /**
     * The trap in one assertion: the pack still ANSWERS yes to the weasel question, because
     * it is an admin shuttle underneath and J3.18 admits those. Reporting it as one is the
     * mistake.
     */
    @Test
    public void aPackStillQualifiesAsAWeaselButIsNotReportedAsOne() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("IKV Vengeance-Admin-1");
        pack.addDrone(new Drone(DroneType.TypeI));
        bay.getSpaces().get(0).setShuttle(pack);

        assertTrue("an admin-built pack is still a non-fighter shuttle (J3.18)",
                pack.canBecomeWildWeasel());

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-Admin-1");
        assertFalse("but it is already spoken for", sd.payload.isEmpty());
    }

    @Test
    public void aSuicideShuttleReportsItsArming() {
        SuicideShuttle ss = new SuicideShuttle(new AdminShuttle());
        ss.setName("IKV Vengeance-Admin-1");
        ss.arm(2);
        bay.getSpaces().get(0).setShuttle(ss);

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-Admin-1");

        assertNotNull(sd);
        assertEquals("suicide", sd.type);
        assertTrue("arming progress is what a suicide shuttle reports",
                sd.armingTurnsComplete > 0);
    }

    @Test
    public void aPlainAdminShuttleStillReportsItsWeaselCharge() {
        AdminShuttle admin = new AdminShuttle();
        admin.setName("IKV Vengeance-Admin-2");
        admin.incrementWwCharge();
        bay.getSpaces().get(0).setShuttle(admin);

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-Admin-2");

        assertNotNull(sd);
        assertEquals("admin", sd.type);
        assertEquals("the branch that was stealing the pack's still works for a shuttle",
                Integer.valueOf(1), sd.wwChargeCount);
    }

    /**
     * A launched pack seen by its OWNER. The enemy's view of the same pack goes through
     * ShuttleDto and reads properly; this one takes its own DTO, which carried what the
     * pack is DOING and had dropped what it IS — so a player's own pack showed
     * "Faction: ?  From: ?" and no hull at all, while the enemy could see everything.
     */
    @Test
    public void anOwnScatterPackOnTheMapSaysWhereItCameFrom() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("IKV Vengeance-Admin-1");
        pack.setOwner(klingon.getOwner());
        pack.setParentShipName(klingon.getName());
        pack.setController(klingon);
        pack.setLocation(new Location(10, 11));
        pack.addDrone(new Drone(DroneType.TypeI));
        pack.setCurrentHull(pack.getHull() - 2);
        game.getSeekers().add(pack);

        GameStateDto.ScatterPackDto dto = null;
        for (GameStateDto.MapObjectDto o : new GameStateDto(game, "Klingon").mapObjects)
            if (o instanceof GameStateDto.ScatterPackDto && "IKV Vengeance-Admin-1".equals(o.name))
                dto = (GameStateDto.ScatterPackDto) o;

        assertNotNull("its owner sees it as the pack it is", dto);
        assertEquals("IKV Vengeance", dto.parentShipName);
        assertEquals("and its damage, which nothing showed", 2, dto.damageTaken);
        assertTrue(dto.maxHull > 0);
        assertEquals(dto.maxHull - 2, dto.hull);
    }

    /** Every prepared shuttle names its role, which is what keeps it out of the launch list. */
    @Test
    public void preparedShuttlesNameTheirRole() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("IKV Vengeance-Admin-1");
        pack.addDrone(new Drone(DroneType.TypeI));
        bay.getSpaces().get(0).setShuttle(pack);

        assertEquals("scatterpack", inBay("IKV Vengeance-Admin-1").specialRole);

        AdminShuttle charged = new AdminShuttle();
        charged.setName("IKV Vengeance-Admin-2");
        charged.incrementWwCharge();
        bay.getSpaces().get(0).setShuttle(charged);
        assertEquals("Wild Weasel", inBay("IKV Vengeance-Admin-2").specialRole);

        AdminShuttle plain = new AdminShuttle();
        plain.setName("IKV Vengeance-Admin-3");
        bay.getSpaces().get(0).setShuttle(plain);
        assertNull("an ordinary shuttle is spoken for by nobody",
                inBay("IKV Vengeance-Admin-3").specialRole);
    }

    /**
     * A fighter is not a weasel candidate, and the DTO has to say so rather than say zero.
     * <p>
     * J3.18 admits the shuttles the catalogue marks, and a Stinger is not one. The Java side
     * always knew — it only fills these fields when canBecomeWildWeasel() — but the fields
     * were primitives, so Jackson sent "wwChargeCount: 0" on every fighter in the bay and a
     * client asking "can this be charged?" got an answer meaning "yes, at zero". The hangar
     * panel duly offered a charge-weasel checkbox on nine Stingers (owner, 2026-09-27).
     */
    @Test
    public void aFighterIsNotAWeaselWaitingToBeCharged() throws Exception {
        com.sfb.objects.Ship rn = com.sfb.objects.ShipLibrary.createShip(
                com.sfb.objects.ShipSpec.fromJson(
                        new java.io.File("../data/factions/hydran/rn.json")));
        rn.setName("HMS Loyalty");
        rn.setLocation(new com.sfb.properties.Location(10, 10));
        com.sfb.Game game = new com.sfb.Game();
        game.getShips().add(rn);

        GameStateDto dto = new GameStateDto(game, null);
        int fighters = 0;
        for (GameStateDto.MapObjectDto o : dto.mapObjects) {
            if (!(o instanceof GameStateDto.ShipDto sd) || sd.shuttleBays == null)
                continue;
            for (GameStateDto.ShuttleBayDto bd : sd.shuttleBays)
                for (GameStateDto.ShuttleInBayDto in : bd.shuttles) {
                    if (!in.type.startsWith("stinger"))
                        continue;
                    fighters++;
                    assertNull(in.name + " cannot be charged as a weasel, and 0 is not the way"
                            + " to say so", in.wwChargeCount);
                    assertNull(in.name + ": nor is false", in.wwReady);
                }
        }
        assertEquals("the Ranger's nine Stingers", 9, fighters);
    }

    // -------------------------------------------------------------------------
    // What a fighter is carrying (J4.82) — for the hangar row and its tooltip
    // -------------------------------------------------------------------------

    @Test
    public void aFighterReportsEveryRailAndWhatIsInIt() {
        com.sfb.objects.shuttles.Fighter taas = com.sfb.objects.shuttles.CataloguedFighter.of("taas");
        taas.setName("IKV Vengeance-TAAS-1");
        bay.getSpaces().get(0).setShuttle(taas);

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-TAAS-1");

        assertNotNull(sd);
        assertNotNull("a drone fighter has to be able to say what it carries", sd.rails);
        assertEquals("a TAAS has four rails, two standard and two light", 4, sd.rails.size());
        for (GameStateDto.FighterRailDto r : sd.rails) {
            assertNotNull("the tooltip names the rail SIZE even when empty", r.railType);
            assertNull("fighters are built empty (J4.8223)", r.drone);
        }
    }

    @Test
    public void aLoadedRailNamesItsDroneAndItsSize() {
        com.sfb.objects.shuttles.Fighter taas = com.sfb.objects.shuttles.CataloguedFighter.of("taas");
        taas.setName("IKV Vengeance-TAAS-2");
        com.sfb.systemgroups.ShuttleSpace box = bay.getSpaces().get(0);
        box.setShuttle(taas);
        // Its own box armed it, which is the only way a fighter gets loaded (J4.881).
        com.sfb.systemgroups.FighterArming.load(box, taas, 8);
        bay.getSpaces().get(0).setShuttle(taas);

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-TAAS-2");

        assertNotNull(sd);
        long loaded = sd.rails.stream().filter(r -> r.drone != null).count();
        assertTrue("the box's ready rack should have armed it", loaded > 0);
        for (GameStateDto.FighterRailDto r : sd.rails) {
            if (r.drone == null)
                continue;
            assertNotNull("the row totals the load from this", r.spaces);
            assertTrue(r.spaces > 0);
        }
    }

    @Test
    public void aShuttleWithNoRailsSendsNoRailList() {
        AdminShuttle admin = new AdminShuttle();
        admin.setName("IKV Vengeance-Admin-9");
        bay.getSpaces().get(0).setShuttle(admin);

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-Admin-9");

        assertNotNull(sd);
        assertNull("null, not an empty list: \"carries no drones\" and \"carries drones and"
                + " is empty\" are different facts and the row shows them differently",
                sd.rails);
    }

    // -------------------------------------------------------------------------
    // Who may be loaded as a scatter pack (FD7.11)
    // -------------------------------------------------------------------------

    /**
     * An UNCONVERTED admin shuttle has to report its pack capacity, because loading it is
     * how it becomes a pack. This was the bug that made scatter packs unloadable on a Kzinti
     * CVL+: maxDroneSpaces was a primitive int set only inside the ScatterPack branch, so an
     * admin shuttle sent 0, the client's `?? 6` never fired (0 being neither null nor
     * undefined), and the picker computed room for nothing and disabled every button.
     */
    @Test
    public void anUnconvertedAdminShuttleReportsThePackCapacityItWouldHave() {
        AdminShuttle admin = new AdminShuttle();
        admin.setName("IKV Vengeance-Admin-7");
        bay.getSpaces().get(0).setShuttle(admin);

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-Admin-7");

        assertNotNull(sd);
        assertNotNull("an admin shuttle is exactly the craft you load to MAKE a pack,"
                + " so it must say how much it holds", sd.maxDroneSpaces);
        assertEquals("FD7.21 gives an admin shuttle six spaces",
                Integer.valueOf(6), sd.maxDroneSpaces);
    }

    /** FD7.11: fighters qualify too, and at their own smaller capacity. */
    @Test
    public void aFighterReportsItsOwnSmallerPackCapacity() {
        com.sfb.objects.shuttles.Fighter aas = com.sfb.objects.shuttles.CataloguedFighter.of("aas");
        aas.setName("IKV Vengeance-AAS-1");
        bay.getSpaces().get(0).setShuttle(aas);

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-AAS-1");

        assertNotNull(sd);
        assertEquals("an AAS carries two spaces, not an admin shuttle's six",
                Integer.valueOf(2), sd.maxDroneSpaces);
    }

    /**
     * And a craft that may NOT be a pack says nothing at all, rather than saying zero — the
     * distinction the primitive could not draw. A GAS may weasel but not scatter-pack.
     */
    @Test
    public void aCraftThatCannotBeAPackSendsNoCapacity() {
        com.sfb.objects.shuttles.GASShuttle gas = new com.sfb.objects.shuttles.GASShuttle();
        gas.setName("IKV Vengeance-GAS-1");
        bay.getSpaces().get(0).setShuttle(gas);

        GameStateDto.ShuttleInBayDto sd = inBay("IKV Vengeance-GAS-1");

        assertNotNull(sd);
        assertNull("null is how the DTO says \"not eligible\"; 0 reads as a full pack",
                sd.maxDroneSpaces);
    }
}
