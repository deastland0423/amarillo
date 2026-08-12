package com.sfb.weapons;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.WeaponFactory;
import com.sfb.samples.FederationShips;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Scout function channels (G24.0), Slice 1: a channel is a non-firing system that dies
 * on the DAC hit of the weapon it replaced (G24.17), must be powered to operate (G24.14),
 * and is blinded for 32 impulses when the ship fires a blinding weapon (G24.13).
 */
public class ScoutChannelTest {

    @Test
    public void channelIsNonFiring_andCarriesTheReplacedWeaponHitLocation() {
        ScoutChannel c = new ScoutChannel();
        c.setDacHitLocaiton("torp");
        assertFalse("a channel never fires", c.canFire());
        assertFalse("and cannot blind another channel", c.blindsScoutChannels());
        assertEquals("dies on the replaced weapon's DAC hit (G24.17)", "torp", c.getDacHitLocaiton());
    }

    @Test
    public void blindingLasts32Impulses() {
        ScoutChannel c = new ScoutChannel();
        c.blind(10);
        assertTrue("still blinded 31 impulses later", c.isBlinded(41));
        assertFalse("clear 32 impulses later (G24.13)", c.isBlinded(42));
        assertEquals(42, c.getBlindedUntilImpulse());
    }

    @Test
    public void reblindingAnAlreadyBlindedChannel_extendsFromItsRecoveryPoint() {
        ScoutChannel c = new ScoutChannel();
        c.blind(10);  // until 42
        c.blind(20);  // G24.131: extend from 42, not from 20
        assertEquals(74, c.getBlindedUntilImpulse());
    }

    @Test
    public void operational_requiresFunctionalPoweredAndUnblinded() {
        ScoutChannel c = new ScoutChannel();
        assertFalse("unpowered → not operational", c.isOperational(5));
        c.setPowered(true);
        assertTrue(c.isOperational(5));
        c.blind(5);
        assertFalse("blinded → not operational", c.isOperational(5));
        assertTrue("operational once the blinding lifts", c.isOperational(40));
        c.damage();
        assertFalse("destroyed → not operational", c.isOperational(40));
    }

    @Test
    public void weaponFactory_buildsScoutChannelWithItsHitLocation() {
        ShipSpec.WeaponSpec ws = new ShipSpec.WeaponSpec();
        ws.type = "ScoutChannel";
        ws.designator = "1";
        ws.dacHitLocation = "phaser";
        var w = WeaponFactory.build(ws, List.of("FULL"));
        assertTrue(w instanceof ScoutChannel);
        assertEquals("phaser", w.getDacHitLocaiton());
    }

    @Test
    public void phaser3AndPhaser1_blindingFlags() {
        assertTrue("a phaser-1 blinds (G24.1342)", new Phaser1().blindsScoutChannels());
        assertFalse("a phaser-3 does not (G24.1341)", new Phaser3().blindsScoutChannels());
    }

    // --- Ship-level blind selection (G24.131) ---

    private Ship scoutWith(int channels) {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        for (int i = 1; i <= channels; i++) {
            ScoutChannel c = new ScoutChannel();
            c.setDesignator(String.valueOf(i));
            c.setDacHitLocaiton("torp");
            c.setPowered(true);
            ship.getWeapons().addWeapon(c);
        }
        return ship;
    }

    @Test
    public void blindOne_blindsAnUnblindedPoweredChannel() {
        Ship ship = scoutWith(2);
        ScoutChannel blinded = ship.blindOneScoutChannel(5);
        assertNotNull(blinded);
        assertTrue(blinded.isBlinded(5));
        assertEquals("only one channel blinded", 1,
                ship.getScoutChannels().stream().filter(c -> !c.isBlinded(5)).count());
    }

    @Test
    public void blindOne_skipsUnpoweredChannels() {
        Ship ship = scoutWith(1);
        ship.getScoutChannels().get(0).setPowered(false);
        assertNull("no powered channel → nothing blinds (G24.13)", ship.blindOneScoutChannel(5));
    }

    @Test
    public void blindOne_extendsTheEarliestRecoveringWhenAllAreBlinded() {
        Ship ship = scoutWith(2);
        ship.getScoutChannels().get(0).blind(5);  // until 37
        ship.getScoutChannels().get(1).blind(10); // until 42
        ship.blindOneScoutChannel(20);            // all blinded → extend #0 (earliest)
        assertEquals("earliest-recovering extended 37 → 69", 69,
                ship.getScoutChannels().get(0).getBlindedUntilImpulse());
        assertEquals("the other untouched", 42, ship.getScoutChannels().get(1).getBlindedUntilImpulse());
    }

    @Test
    public void bases_doNotBlindTheirOwnChannels() {
        Ship ship = scoutWith(1);
        ship.setBase(true);
        assertNull("G24.135", ship.blindOneScoutChannel(5));
    }

    @Test
    public void energyAllocation_powersChannelsAndSetsTheShipEwPool() {
        Ship ship = scoutWith(2);
        ship.getScoutChannels().forEach(c -> c.setPowered(false)); // start clean

        com.sfb.systemgroups.Energy alloc = new com.sfb.systemgroups.Energy();
        alloc.setPoweredChannels(java.util.Arrays.asList("1", "2"));
        alloc.setScoutEwPoints(9); // the scout generates a ship-level pool of 9 EW (G24.211)
        ship.allocateEnergy(alloc);
        ship.startTurn(); // applies the allocation

        assertTrue("channel 1 powered", ship.getScoutChannels().get(0).isPowered());
        assertTrue("channel 2 powered", ship.getScoutChannels().get(1).isPowered());
        assertEquals("ship-level EW pool set from allocation", 9, ship.getScoutEwPool());
    }

    @Test
    public void aShipWithNoChannels_generatesNoLendingPool() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa()); // no scout channels
        com.sfb.systemgroups.Energy alloc = new com.sfb.systemgroups.Energy();
        alloc.setScoutEwPoints(6);
        ship.allocateEnergy(alloc);
        ship.startTurn();
        assertEquals("no channels → no lending pool", 0, ship.getScoutEwPool());
    }

    @Test
    public void startTurn_clearsLastTurnsLendAssignments() {
        Ship ship = scoutWith(1);
        ship.getScoutChannels().get(0).setLend("Friend", 4, 0);
        com.sfb.systemgroups.Energy alloc = new com.sfb.systemgroups.Energy();
        alloc.setPoweredChannels(java.util.Collections.singletonList("1"));
        alloc.setScoutEwPoints(6);
        ship.allocateEnergy(alloc);
        ship.startTurn();
        assertNull("a new turn's pool can't be spent by last turn's lend",
                ship.getScoutChannels().get(0).getLendTarget());
    }

    @Test
    public void dacHitOnTheReplacedLocation_destroysTheChannel() {
        Ship ship = scoutWith(1);
        ScoutChannel c = ship.getScoutChannels().get(0);
        assertTrue(c.isFunctional());
        ship.applyDacChoiceHit("torp", c.getName(), null); // "torp" = the disruptor it replaced (G24.17)
        assertFalse("a torp hit destroys the channel", c.isFunctional());
    }
}
