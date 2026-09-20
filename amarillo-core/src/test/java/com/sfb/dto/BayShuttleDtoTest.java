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
        assertEquals("and it is a pack, not a weasel waiting to be charged",
                0, sd.wwChargeCount);
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
                1, sd.wwChargeCount);
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

        assertEquals("scatter pack", inBay("IKV Vengeance-Admin-1").specialRole);

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
}
