package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * EW lending via scout channels (G24.21): a scout generates EW and lends ECM/ECCM to
 * another ship (needs a lock-on to it, G24.218) or to itself (G24.28, ECM only per
 * G24.283). The lent EW only flows while the channel is operational (powered, unblinded,
 * undamaged, G24.13/.14) and adds to the recipient's effective ECM/ECCM.
 */
public class EwLendingTest {

    private Ship scoutWithChannel(Game game, String name, int x, int y) {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName(name);
        ship.setLocation(new Location(x, y));
        ScoutChannel c = new ScoutChannel();
        c.setDesignator("1");
        c.setDacHitLocaiton("torp");
        c.setPowered(true);
        ship.getWeapons().addWeapon(c);
        game.getShips().add(ship);
        return ship;
    }

    private ScoutChannel channelOf(Ship ship) {
        return ship.getScoutChannels().get(0);
    }

    private Ship plainShip(Game game, String name, int x, int y) {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName(name);
        ship.setLocation(new Location(x, y));
        game.getShips().add(ship);
        return ship;
    }

    @Test
    public void channelLendsEcmToAFriend_withLockOn() {
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 11, 10);
        channelOf(scout).setLend("Friend", 5, 0);
        scout.addLockOn(friend); // G24.218

        game.resolveChannelLends();

        assertEquals("5 ECM lent to the friend", 5, friend.getLentEcm());
        assertEquals(0, friend.getLentEccm());
    }

    @Test
    public void noLockOn_noLend() {
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 11, 10);
        channelOf(scout).setLend("Friend", 5, 0);
        // no lock-on

        game.resolveChannelLends();

        assertEquals("no lock-on → no lend (G24.218)", 0, friend.getLentEcm());
    }

    @Test
    public void blindedOrUnpoweredChannel_lendsNothing() {
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 11, 10);
        channelOf(scout).setLend("Friend", 5, 0);
        scout.addLockOn(friend);

        channelOf(scout).blind(0); // blinded (impulse 0)
        game.resolveChannelLends();
        assertEquals("blinded channel lends nothing (G24.13)", 0, friend.getLentEcm());

        channelOf(scout).setPowered(false);
        game.resolveChannelLends();
        assertEquals("unpowered channel lends nothing (G24.14)", 0, friend.getLentEcm());
    }

    @Test
    public void selfProtection_lendsEcmOnly_noLockOnNeeded() {
        Game game = new Game();
        Ship scout = scoutWithChannel(game, "Scout", 10, 10);
        channelOf(scout).setLend("Scout", 4, 3); // lending to itself (G24.28)

        game.resolveChannelLends();

        assertEquals("self-protection ECM applies without a lock-on (G24.28)", 4, scout.getLentEcm());
        assertEquals("a scout cannot lend ECCM to itself (G24.283)", 0, scout.getLentEccm());
    }

    @Test
    public void lentEcmRaisesTheTargetsEffectiveEcm() {
        // A cloaked ship with lent ECM is harder to keep a lock on: the retention
        // probability drops by the extra EW shift (D6.34 / G13.331).
        Game game = new Game();
        Ship attacker = plainShip(game, "Hunter", 10, 10);
        attacker.setActiveFireControl(true);
        Ship cloaked = plainShip(game, "Ghost", 10, 12); // range 2

        int baseline = game.retentionProbability(attacker, cloaked);
        cloaked.addLentEw(4, 0); // +4 ECM → sqrt(4) = +2 EW shift against the hunter
        int withLend = game.retentionProbability(attacker, cloaked);

        assertEquals("lent ECM feeds the EW shift", baseline - 2, withLend);
    }
}
