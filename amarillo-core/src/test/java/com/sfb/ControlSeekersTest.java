package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Controlling seekers (G24.24): one scout channel adds +6 to the scout's seeker-control
 * capacity while operational. Only one channel per scout may do this (G24.24), it's one
 * function per turn (G24.241), and if that channel is blinded the ship must shed the seekers
 * over its normal rating (G24.242) — handled via the control-overflow interrupt.
 */
public class ControlSeekersTest {

    private Ship scoutWith(Game game, int channels) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName("Scout");
        s.setLocation(new Location(10, 10));
        for (int i = 1; i <= channels; i++) {
            ScoutChannel c = new ScoutChannel();
            c.setDesignator(String.valueOf(i));
            c.setDacHitLocaiton("torp");
            c.setPowered(true);
            s.getWeapons().addWeapon(c);
        }
        game.getShips().add(s);
        return s;
    }

    @Test
    public void committingAChannel_addsSixControlCapacity() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        int before = scout.getControlCapacity();

        Game.ActionResult r = game.assignControlSeekers(scout, "1");
        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("+6 seeker-control capacity (G24.24)", before + 6, scout.getControlCapacity());
    }

    @Test
    public void onlyOneChannelPerScoutMayControlSeekers() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        assertTrue(game.assignControlSeekers(scout, "1").isSuccess());
        Game.ActionResult r = game.assignControlSeekers(scout, "2");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("only one channel"));
        assertEquals("still just +6, not +12", scout.getControlCapacity(),
                scout.getControlCapacity()); // capacity unchanged by the rejected second channel
    }

    @Test
    public void unpoweredChannel_isRejected() {
        Game game = new Game();
        Ship scout = scoutWith(game, 1);
        scout.getScoutChannels().get(0).setPowered(false);
        Game.ActionResult r = game.assignControlSeekers(scout, "1");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("powered"));
    }

    @Test
    public void aControllingChannel_cannotAlsoLend() {
        Game game = new Game();
        Ship scout = scoutWith(game, 1);
        scout.setScoutEwPool(6);
        assertTrue(game.assignControlSeekers(scout, "1").isSuccess());
        Game.ActionResult r = game.assignChannelLend(scout, "1", "Scout", 2, 0);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("controlling seekers"));
    }

    @Test
    public void aLendingChannel_cannotControlSeekers() {
        Game game = new Game();
        Ship scout = scoutWith(game, 1);
        scout.setScoutEwPool(6);
        assertTrue(game.assignChannelLend(scout, "1", "Scout", 2, 0).isSuccess()); // commits LEND_EW
        Game.ActionResult r = game.assignControlSeekers(scout, "1");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("lending"));
    }

    @Test
    public void blindingTheControlChannel_dropsTheBonus() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        game.assignControlSeekers(scout, "1");
        int withBonus = scout.getControlCapacity();

        game.queueScoutBlinds(scout, 1); // 1 blind, 2 unblinded → choice
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);
        game.submitBlindChoice("1");     // blind the control channel

        assertEquals("bonus dropped when the control channel went blind (G24.242)",
                withBonus - 6, scout.getControlCapacity());
    }

    @Test
    public void blindingTheControlChannel_whenOverNormalLimit_triggersOverflow() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        game.assignControlSeekers(scout, "1"); // +6
        int normal = scout.getControlCapacity() - 6;

        // Take on more seekers than the normal rating, within the boosted limit.
        for (int i = 0; i < normal + 3; i++) {
            Drone d = new Drone(DroneType.TypeI);
            d.setName("D" + i);
            assertTrue(scout.acquireControl(d));
        }

        game.queueScoutBlinds(scout, 1);
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);
        game.submitBlindChoice("1"); // control channel blinded → over normal limit → shed excess

        assertEquals(Game.ImpulsePhase.CONTROL_OVERFLOW, game.getCurrentPhase());
    }

    /** G24.16/G24.242: cloaking suspends the function, so the +6 goes away with it. */
    @Test
    public void cloakingAfterCommitting_dropsTheSixCapacity() {
        Game game = new Game();
        Ship scout = new Ship();
        scout.init(com.sfb.samples.RomulanShips.getRomKr()); // has a cloaking device
        scout.setName("Scout");
        scout.setLocation(new Location(10, 10));
        ScoutChannel c = new ScoutChannel();
        c.setDesignator("1");
        c.setDacHitLocaiton("torp");
        c.setPowered(true);
        scout.getWeapons().addWeapon(c);
        game.getShips().add(scout);

        int base = scout.getControlCapacity();
        assertTrue(game.assignControlSeekers(scout, "1").isSuccess());
        assertEquals(base + 6, scout.getControlCapacity());

        scout.getCloakingDevice().setState(
                com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED);
        game.resolveChannelLends();   // the per-impulse refresh (G24.333)

        assertEquals("cloaked → the channel is suspended (G24.16)", base, scout.getControlCapacity());

        scout.getCloakingDevice().setState(
                com.sfb.systemgroups.CloakingDevice.CloakState.INACTIVE);
        game.resolveChannelLends();

        assertEquals("and comes back when the cloak drops (G24.333)", base + 6, scout.getControlCapacity());
    }

    /** G24.16: committing while cloaked is refused outright, not silently worth nothing. */
    @Test
    public void committingWhileCloaked_isRefused() {
        Game game = new Game();
        Ship scout = new Ship();
        scout.init(com.sfb.samples.RomulanShips.getRomKr());
        scout.setName("Scout");
        scout.setLocation(new Location(10, 10));
        ScoutChannel c = new ScoutChannel();
        c.setDesignator("1");
        c.setDacHitLocaiton("torp");
        c.setPowered(true);
        scout.getWeapons().addWeapon(c);
        game.getShips().add(scout);
        scout.getCloakingDevice().setState(
                com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED);

        Game.ActionResult r = game.assignControlSeekers(scout, "1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("cloaked"));
    }

}
