package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.DirectFire;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Firing a volley that contains something which is not a direct-fire weapon.
 * <p>
 * {@code fireWeapons} dispatches by weapon type and ended with an unguarded cast to
 * {@link DirectFire}, so anything that reached the bottom of that chain took the whole
 * request down with a ClassCastException. A drone rack gets there easily: a rack is a
 * Launcher, and {@code fetchAllWeapons()} returns racks alongside phasers and torpedoes,
 * so the server can resolve one by name and hand it over.
 * <p>
 * A rules engine should never throw on a selection it merely dislikes, so core skips what
 * it cannot fire and says so. The guard tests the CAPABILITY rather than the class: when
 * a G-rack can fire as an ADD (FD3.7), it will be a DirectFire in that mode and the guard
 * will stop applying to it without being edited.
 */
public class FireNonDirectFireWeaponTest {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(KlingonShips.getD7());   // a D7 carries drone racks
        fed.setName("IKS Fury");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setActiveFireControl(true);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(FederationShips.getFedCa());
        klingon.setName("USS Enterprise");
        klingon.setLocation(new Location(10, 8));
        klingon.setFacing(13);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
    }

    private Weapon firstDroneRack() {
        for (Weapon w : fed.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.DroneRack)
                return w;
        return null;
    }

    @Test
    public void aDroneRackIsReachableFromTheWeaponListAtAll() {
        // The precondition for the bug: racks are in the same list as everything else, so
        // nothing stops one being named in a fire order. If this ever stops being true the
        // tests below are proving nothing.
        Weapon rack = firstDroneRack();
        assertNotNull("a D7 should carry a drone rack, and it should be in fetchAllWeapons()",
                rack);
        assertFalse("a rack launches seekers; it is not a direct-fire weapon",
                rack instanceof DirectFire);
    }

    @Test
    public void firingADroneRackAtAShipDoesNotThrow() {
        Weapon rack = firstDroneRack();
        List<Weapon> volley = new ArrayList<>();
        volley.add(rack);

        String log = game.fireWeapons(fed, klingon, volley, 2, 2, 1, false, true);

        assertNotNull(log);
        assertTrue("it should say why the rack did nothing, rather than throwing: " + log,
                log.contains(rack.getName()));
    }

    @Test
    public void theRestOfTheVolleyStillFires() {
        // The interesting case: a rack mixed in with real weapons must not cost the player
        // the whole volley, either by exception or by silent abandonment.
        Weapon rack = firstDroneRack();
        Weapon phaser = null;
        for (Weapon w : fed.getWeapons().fetchAllWeapons())
            if (w instanceof DirectFire) { phaser = w; break; }
        assertNotNull("expected some direct-fire weapon on a D7", phaser);

        List<Weapon> volley = new ArrayList<>();
        volley.add(rack);
        volley.add(phaser);

        String log = game.fireWeapons(fed, klingon, volley, 2, 2, 1, false, true);

        assertTrue("the rack is reported: " + log, log.contains(rack.getName()));
        assertTrue("and the phaser still fired: " + log, log.contains(phaser.getName()));
    }
}
