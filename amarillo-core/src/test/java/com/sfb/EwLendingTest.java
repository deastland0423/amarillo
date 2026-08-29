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
        ship.setActiveFireControl(true); // lending to others needs active FC (G24.161)
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

    /** G24.2181: a recipient more than fifteen hexes away gets nothing until it closes. */
    @Test
    public void friendBeyondFifteenHexes_getsNoLend_untilItClosesBack() {
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 31, 10); // 21 hexes off
        channelOf(scout).setLend("Friend", 5, 0);
        scout.addLockOn(friend);

        game.resolveChannelLends();
        assertEquals("out of range → no lend (G24.2181)", 0, friend.getLentEcm());

        // Suspension is not cancellation: closing back inside fifteen hexes resumes it (G24.333).
        friend.setLocation(new Location(12, 10));
        game.resolveChannelLends();
        assertEquals("back in range → lend resumes (G24.333)", 5, friend.getLentEcm());
    }

    /** D6.3144 (cited by G24.216): a unit receives at most six ECM from lending, however
     *  many channels aim at it — one channel is capped at six on its own (G24.2112). */
    @Test
    public void twoChannelsLendingEcm_recipientCapsAtSix() {
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 11, 10);
        ScoutChannel second = new ScoutChannel();
        second.setDesignator("2");
        second.setDacHitLocaiton("torp");
        second.setPowered(true);
        scout.getWeapons().addWeapon(second);
        scout.addLockOn(friend);

        channelOf(scout).setLend("Friend", 6, 0);
        second.setLend("Friend", 6, 0);
        game.resolveChannelLends();

        assertEquals("12 ECM lent but only 6 received (D6.3144)", 6, friend.getLentEcm());
    }

    /** G24.16/G13.515: a cloaked scout may not lend to others, but self-protection
     *  (G24.28) is explicitly exempt. */
    @Test
    public void cloakedScout_cannotLendToOthers_butStillSelfProtects() {
        Game game = new Game();
        Ship scout = new Ship();
        scout.init(com.sfb.samples.RomulanShips.getRomKr()); // has a cloaking device
        scout.setName("Scout");
        scout.setLocation(new Location(10, 10));
        scout.setActiveFireControl(true);
        ScoutChannel toFriend = new ScoutChannel();
        toFriend.setDesignator("1");
        toFriend.setDacHitLocaiton("torp");
        toFriend.setPowered(true);
        ScoutChannel toSelf = new ScoutChannel();
        toSelf.setDesignator("2");
        toSelf.setDacHitLocaiton("torp");
        toSelf.setPowered(true);
        scout.getWeapons().addWeapon(toFriend);
        scout.getWeapons().addWeapon(toSelf);
        game.getShips().add(scout);
        Ship friend = plainShip(game, "Friend", 11, 10);
        scout.addLockOn(friend);
        toFriend.setLend("Friend", 5, 0);
        toSelf.setLend("Scout", 4, 0);

        scout.getCloakingDevice().setState(
                com.sfb.systemgroups.CloakingDevice.CloakState.FULLY_CLOAKED);
        game.resolveChannelLends();

        assertEquals("cloaked → no lending to others (G24.16)", 0, friend.getLentEcm());
        assertEquals("self-protection survives the cloak (G24.28)", 4, scout.getLentEcm());
    }

    /** G24.16/J3.403: an operating Wild Weasel bars every channel, self-protection included. */
    @Test
    public void operatingWildWeasel_blocksEvenSelfProtection() {
        Game game = new Game();
        Ship scout = scoutWithChannel(game, "Scout", 10, 10);
        channelOf(scout).setLend("Scout", 4, 0);

        game.resolveChannelLends();
        assertEquals("baseline self-protection", 4, scout.getLentEcm());

        scout.setActiveWildWeasel(new com.sfb.objects.shuttles.WildWeaselShuttle(scout));
        game.resolveChannelLends();
        assertEquals("WW blocks all channel use (G24.16)", 0, scout.getLentEcm());
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

    @Test
    public void assignChannelLend_appliesAnyEcmEccmSplitWithinTheCapAndLockOn() {
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 11, 10);
        scout.setScoutEwPool(6);
        scout.addLockOn(friend);

        Game.ActionResult r = game.assignChannelLend(scout, "1", "Friend", 4, 2); // 4+2 = 6 (cap)
        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(4, friend.getLentEcm());
        assertEquals(2, friend.getLentEccm());
    }

    @Test
    public void assignChannelLend_rejectsMoreThanSixOnOneChannel() {
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 11, 10);
        scout.setScoutEwPool(12);
        scout.addLockOn(friend);

        Game.ActionResult r = game.assignChannelLend(scout, "1", "Friend", 5, 3); // 8 > 6 (G24.2112)
        assertFalse("a channel lends at most 6", r.isSuccess());
        assertEquals("nothing applied", 0, friend.getLentEcm());
    }

    @Test
    public void assignChannelLend_rejectsMoreThanTheGeneratedPool() {
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 11, 10);
        scout.setScoutEwPool(3); // only generated 3 EW
        scout.addLockOn(friend);

        Game.ActionResult r = game.assignChannelLend(scout, "1", "Friend", 5, 0); // 5 > pool 3 (G24.2111)
        assertFalse("can't lend more than the scout generated", r.isSuccess());
        assertEquals(0, friend.getLentEcm());
    }

    @Test
    public void assignChannelLend_selfDropsEccm_andZeroClears() {
        Game game = new Game();
        Ship scout = scoutWithChannel(game, "Scout", 10, 10);
        scout.setScoutEwPool(6);

        Game.ActionResult r = game.assignChannelLend(scout, "1", "Scout", 4, 3); // self (G24.28)
        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("self ECM applies", 4, scout.getLentEcm());
        assertEquals("no ECCM to self (G24.283)", 0, scout.getLentEccm());

        game.assignChannelLend(scout, "1", "Scout", 0, 0); // clear
        assertEquals("0/0 clears the lend", 0, scout.getLentEcm());
        assertNull(scout.getScoutChannels().get(0).getLendTarget());
    }

    @Test
    public void onlyOneChannelMaySelfProtect() {
        // G24.282: a scout can self-protect with only one channel.
        Game game = new Game();
        Ship scout = scoutWithChannel(game, "Scout", 10, 10); // channel "1"
        ScoutChannel c2 = new ScoutChannel();
        c2.setDesignator("2");
        c2.setDacHitLocaiton("torp");
        c2.setPowered(true);
        scout.getWeapons().addWeapon(c2);
        scout.setScoutEwPool(12);

        assertTrue(game.assignChannelLend(scout, "1", "Scout", 4, 0).isSuccess());
        Game.ActionResult r = game.assignChannelLend(scout, "2", "Scout", 3, 0);
        assertFalse("a second self-protection channel is refused (G24.282)", r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("self-protect"));
    }

    @Test
    public void goingPassiveFcSuspendsALendToAnotherShip() {
        // Lending EW to another unit needs active fire control + a lock-on (G24.161). Going
        // passive drops both (D6.62), so the lend suspends (G24.333). Self-protection (G24.28)
        // would continue — it needs neither.
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10); // active FC
        Ship friend = plainShip(game, "Friend", 11, 10);
        scout.setScoutEwPool(6);
        scout.addLockOn(friend);

        assertTrue(game.assignChannelLend(scout, "1", "Friend", 4, 0).isSuccess());
        assertEquals("lends with active FC + lock-on", 4, friend.getLentEcm());

        scout.setActiveFireControl(false); // passive → drops FC and the lock-on (D6.62)
        game.resolveChannelLends();
        assertEquals("lend suspends without active FC (G24.161/.333)", 0, friend.getLentEcm());
    }

    @Test
    public void poolIsSharedAcrossChannels_secondChannelCannotOverdrawIt() {
        // Two channels draw from ONE ship pool (G24.2111): after channel 1 takes 4 of 6,
        // channel 2 can only lend the 2 that remain — and two channels can pile EW onto
        // the same unit (G24.2112, up to D6.3144's per-unit limit).
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10); // channel "1"
        ScoutChannel c2 = new ScoutChannel();
        c2.setDesignator("2");
        c2.setDacHitLocaiton("torp");
        c2.setPowered(true);
        scout.getWeapons().addWeapon(c2);
        Ship friend = plainShip(game, "Friend", 11, 10);
        scout.setScoutEwPool(6);
        scout.addLockOn(friend);

        assertTrue(game.assignChannelLend(scout, "1", "Friend", 4, 0).isSuccess()); // 2 left
        assertFalse("channel 2 can't overdraw the shared pool",
                game.assignChannelLend(scout, "2", "Friend", 4, 0).isSuccess());
        assertTrue(game.assignChannelLend(scout, "2", "Friend", 2, 0).isSuccess()); // fits
        assertEquals("both channels stack onto the friend: 4 + 2", 6, friend.getLentEcm());
        assertEquals("pool fully spent", 0, scout.getScoutEwRemaining());
    }

    @Test
    public void reapportioning_dropsTheOldPointsAndSpendsFreshOnes() {
        // 3 ECM/3 ECCM → 6 ECM/0 ECCM: the 3 ECCM are lost, and 3 fresh ECM are drawn
        // from the pool (G24.2122). Net: 9 points consumed for a channel showing 6 ECM.
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 11, 10);
        scout.setScoutEwPool(9);
        scout.addLockOn(friend);

        assertTrue(game.assignChannelLend(scout, "1", "Friend", 3, 3).isSuccess());
        assertEquals("6 committed, 3 left", 3, scout.getScoutEwRemaining());

        assertTrue(game.assignChannelLend(scout, "1", "Friend", 6, 0).isSuccess());
        assertEquals("channel now lends 6 ECM", 6, friend.getLentEcm());
        assertEquals("no ECCM", 0, friend.getLentEccm());
        assertEquals("pool exhausted — 3 dropped, 3 fresh spent", 0, scout.getScoutEwRemaining());
    }

    @Test
    public void reducingALend_doesNotRefundThePool() {
        Game game = new Game();
        Ship scout  = scoutWithChannel(game, "Scout", 10, 10);
        Ship friend = plainShip(game, "Friend", 11, 10);
        scout.setScoutEwPool(6);
        scout.addLockOn(friend);

        game.assignChannelLend(scout, "1", "Friend", 6, 0); // remaining 0
        game.assignChannelLend(scout, "1", "Friend", 2, 0); // drop 4 (lost, no refund)
        assertEquals("channel lends 2 now", 2, friend.getLentEcm());
        assertEquals("the 4 dropped points do not come back", 0, scout.getScoutEwRemaining());

        // ...so trying to climb back to 6 is refused (nothing left to draw).
        assertFalse("cannot re-add dropped points (G24.2122)",
                game.assignChannelLend(scout, "1", "Friend", 6, 0).isSuccess());
        assertEquals("stays at 2", 2, friend.getLentEcm());
    }

    @Test
    public void retargetingAChannel_dropsOldPointsAndDrawsTheNewLendFresh() {
        Game game = new Game();
        Ship scout = scoutWithChannel(game, "Scout", 10, 10);
        Ship a = plainShip(game, "Aye", 11, 10);
        Ship b = plainShip(game, "Bee", 9, 10);
        scout.setScoutEwPool(5);
        scout.addLockOn(a);
        scout.addLockOn(b);

        game.assignChannelLend(scout, "1", "Aye", 3, 0); // remaining 2
        // Move to Bee: the 3 lent to Aye are dropped (a scout can't shift EW, G24.2123);
        // 2 to Bee must be drawn fresh — only 2 remain, so this just fits.
        assertTrue(game.assignChannelLend(scout, "1", "Bee", 2, 0).isSuccess());
        assertEquals("Aye no longer receives EW", 0, a.getLentEcm());
        assertEquals("Bee gets 2", 2, b.getLentEcm());
        assertEquals("pool exhausted (3 to Aye lost, 2 fresh to Bee)", 0, scout.getScoutEwRemaining());
    }

    @Test
    public void lentEwCountsInTractorTransporterAttempts() {
        // Lent EW is part of a ship's total EW (D6.373), so it factors into the D6.34
        // tractor/transporter shift (D6.372) — both the actor's and the target's.
        Game game = new Game();
        Ship actor  = plainShip(game, "Grabber", 10, 10);
        Ship target = plainShip(game, "Prey", 10, 12); // range 2, enemy
        actor.setActiveFireControl(true);
        Player a = new Player(); a.setTeamName("A"); actor.setOwner(a);
        Player b = new Player(); b.setTeamName("B"); target.setOwner(b);

        assertEquals("no EW → no shift", 0, game.d637Shift(actor, target));

        target.addLentEw(4, 0); // the prey is protected by 4 lent ECM
        assertEquals("lent ECM raises the tractor/transporter shift", 2, game.d637Shift(actor, target));

        actor.addLentEw(0, 4);  // the grabber gets 4 lent ECCM back
        assertEquals("lent ECCM cancels it", 0, game.d637Shift(actor, target));
    }
}
