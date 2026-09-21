package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Offensive EW (G24.219): a scout channel jams an enemy unit's fire control, adding to the
 * effective ECM of everything it shoots at. Needs active FC + a lock-on within 15 hexes
 * (G24.2191); the target is a ship/base/PF, not a seeker/shuttle (G24.2192). ECM only
 * (G24.2195). Caps: 6/channel, 6/scout total (G24.219), 6 on the enemy from all sources
 * (D6.3145). Drawn from the EW pool; raising it costs fresh points (dropped points lost).
 */
public class OffensiveEwTest {

    private Ship scout(Game game, String name, int x, int y) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setActiveFireControl(true);
        s.setScoutEwPool(6);
        ScoutChannel c1 = new ScoutChannel();
        c1.setDesignator("1");
        c1.setDacHitLocaiton("torp");
        c1.setPowered(true);
        s.getWeapons().addWeapon(c1);
        ScoutChannel c2 = new ScoutChannel();
        c2.setDesignator("2");
        c2.setDacHitLocaiton("torp");
        c2.setPowered(true);
        s.getWeapons().addWeapon(c2);
        game.getShips().add(s);
        return s;
    }

    private Ship enemy(Game game, String name, int x, int y) {
        Ship e = new Ship();
        e.init(KlingonShips.getD7());
        e.setName(name);
        e.setLocation(new Location(x, y));
        game.getShips().add(e);
        return e;
    }

    @Test
    public void jamsAnEnemy_withActiveFcAndLockOn() {
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        Ship foe = enemy(game, "Foe", 10, 12);
        scout.addLockOn(foe);

        Game.ActionResult r = game.assignOffensiveEw(scout, "1", "Foe", 4);
        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("4 O-EW jams the enemy's fire", 4, foe.getOffensiveEw());
    }

    @Test
    public void withoutLockOn_jammingIsParkedNotApplied() {
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        Ship foe = enemy(game, "Foe", 10, 12); // no lock-on

        Game.ActionResult r = game.assignOffensiveEw(scout, "1", "Foe", 4);
        assertTrue(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("inactive"));
        assertEquals("no jamming without a lock-on (G24.2191)", 0, foe.getOffensiveEw());
    }

    @Test
    public void degradesTheEnemysDirectFire() {
        // The jammed enemy shooting a target sees that target's ECM raised by the O-EW.
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        Ship foe = enemy(game, "Foe", 10, 12);
        Ship victim = new Ship();
        victim.init(FederationShips.getFedCa());
        victim.setName("Victim");
        victim.setLocation(new Location(10, 14));
        game.getShips().add(victim);
        scout.addLockOn(foe);

        int before = game.fireEcmShift(foe, victim);
        game.assignOffensiveEw(scout, "1", "Foe", 4); // +4 ECM → +2 shift (floor sqrt 4)
        int after = game.fireEcmShift(foe, victim);

        assertEquals("O-EW raises the enemy's fire shift", before + 2, after);
    }

    @Test
    public void aFriendlyTarget_isRejected() {
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        Player teamA = new Player();
        teamA.setTeamName("A");
        scout.setOwner(teamA);
        Ship ally = enemy(game, "Ally", 10, 12);
        ally.setOwner(teamA);
        scout.addLockOn(ally);

        Game.ActionResult r = game.assignOffensiveEw(scout, "1", "Ally", 4);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("friendly"));
    }

    @Test
    public void aSeekerTarget_isRejected() {
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("Drone-1");
        drone.setLocation(new Location(10, 12));
        game.getSeekers().add(drone);

        Game.ActionResult r = game.assignOffensiveEw(scout, "1", "Drone-1", 4);
        assertFalse("O-EW can't target a seeker (G24.2192)", r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("No enemy ship"));
    }

    @Test
    public void refusesBeyondFifteenHexes() {
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        Ship foe = enemy(game, "Foe", 10, 26); // range 16
        scout.addLockOn(foe);

        Game.ActionResult r = game.assignOffensiveEw(scout, "1", "Foe", 4);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("out of range"));
    }

    @Test
    public void moreThanSixOnOneChannel_isRefused() {
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        Ship foe = enemy(game, "Foe", 10, 12);
        scout.addLockOn(foe);

        Game.ActionResult r = game.assignOffensiveEw(scout, "1", "Foe", 7);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("at most 6"));
    }

    @Test
    public void aScoutCanLendAtMostSixOEwTotal() {
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        scout.setScoutEwPool(12);
        Ship foe1 = enemy(game, "Foe1", 10, 12);
        Ship foe2 = enemy(game, "Foe2", 10, 8);
        scout.addLockOn(foe1);
        scout.addLockOn(foe2);

        assertTrue(game.assignOffensiveEw(scout, "1", "Foe1", 4).isSuccess());
        Game.ActionResult r = game.assignOffensiveEw(scout, "2", "Foe2", 4); // 4+4 = 8 > 6
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("6 O-EW total"));
    }

    @Test
    public void enemyTakesAtMostSixOEwFromAllSources() {
        Game game = new Game();
        Ship scoutA = scout(game, "ScoutA", 10, 10);
        Ship scoutB = scout(game, "ScoutB", 10, 14);
        Ship foe = enemy(game, "Foe", 10, 12);
        scoutA.addLockOn(foe);
        scoutB.addLockOn(foe);

        assertTrue(game.assignOffensiveEw(scoutA, "1", "Foe", 4).isSuccess());
        assertTrue(game.assignOffensiveEw(scoutB, "1", "Foe", 4).isSuccess());
        assertEquals("clamped to 6 from all sources (D6.3145)", 6, foe.getOffensiveEw());
    }

    @Test
    public void raisingJamming_costsFreshPool_droppingIsLost() {
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        scout.setScoutEwPool(9);
        Ship foe = enemy(game, "Foe", 10, 12);
        scout.addLockOn(foe);

        assertTrue(game.assignOffensiveEw(scout, "1", "Foe", 4).isSuccess());
        assertEquals(5, scout.getScoutEwRemaining());
        assertTrue(game.assignOffensiveEw(scout, "1", "Foe", 6).isSuccess()); // +2 more
        assertEquals(6, foe.getOffensiveEw());
        assertEquals(3, scout.getScoutEwRemaining());
        // drop back to 2, then the dropped points don't refund
        assertTrue(game.assignOffensiveEw(scout, "1", "Foe", 2).isSuccess());
        assertEquals(3, scout.getScoutEwRemaining());
    }

    @Test
    public void anOffensiveEwChannel_cannotAlsoLend() {
        Game game = new Game();
        Ship scout = scout(game, "Scout", 10, 10);
        Ship foe = enemy(game, "Foe", 10, 12);
        scout.addLockOn(foe);

        assertTrue(game.assignOffensiveEw(scout, "1", "Foe", 4).isSuccess());
        Game.ActionResult r = game.assignChannelLend(scout, "1", "Scout", 2, 0);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("offensive EW"));
    }
}
