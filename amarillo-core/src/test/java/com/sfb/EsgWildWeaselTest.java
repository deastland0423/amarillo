package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.ESG;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * G23.48 (J3.46): using an ESG is something a decoy could never do, so announcing an
 * ESG release voids the ship's own active Wild Weasel.
 */
public class EsgWildWeaselTest {

    private Game game;
    private Ship ship;
    private ESG  esg;

    @Before
    public void setUp() {
        game = new Game();
        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("Sphere");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        esg = new ESG();
        esg.setDesignator("A");
        ship.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(3);
        game.getShips().add(ship);
    }

    private void advanceToActivity() {
        for (int guard = 0; guard < 80; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY) return;
            game.advancePhase();
        }
        fail("never reached ACTIVITY");
    }

    @Test
    public void announcingAnEsg_voidsTheShipsWildWeasel() {
        WildWeaselShuttle ww = new WildWeaselShuttle(ship);
        ww.setName("Sphere-WW");
        ww.setLocation(new Location(10, 10));
        game.getActiveShuttles().add(ww);
        ship.setActiveWildWeasel(ww);
        assertTrue(ship.hasActiveWildWeasel());

        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(0.0);
        game.startTurn();
        game.submitAllocation(ship, e);
        advanceToActivity();

        Game.ActionResult r = game.announceEsg(ship, "A", 1, 0);

        assertTrue("announcement still succeeds", r.isSuccess());
        assertTrue("log notes the void", r.getMessage().contains("Wild Weasel voided"));
        assertFalse("the Wild Weasel is gone (G23.48)", ship.hasActiveWildWeasel());
        assertTrue("the ESG announcement stands", esg.isAnnounced());
    }
}
