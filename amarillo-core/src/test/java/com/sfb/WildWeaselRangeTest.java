package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * J3.13: a Wild Weasel only diverts seekers while within 35 hexes of the ship
 * it protects — beyond that it is voided. Checked when the Movement phase
 * resolves, catching separation from either side moving.
 */
public class WildWeaselRangeTest {

    private Game game;
    private Ship fed;
    private Ship klingon;
    private WildWeaselShuttle ww;

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(1, 16));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(20, 30));
        klingon.setFacing(1);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();

        game.submitAllocation(fed,     makeAllocation(fed));
        game.submitAllocation(klingon, makeAllocation(klingon));
    }

    private Energy makeAllocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(0.0);
        return e;
    }

    /** Place an active WW protecting fed at the given hex, drifting at speed 0. */
    private void placeWildWeasel(int col, int row) {
        ww = new WildWeaselShuttle(fed);
        ww.setName("Enterprise-WW");
        ww.setParentShipName(fed.getName());
        ww.setLocation(new Location(col, row));
        ww.setSpeed(0);
        game.getActiveShuttles().add(ww);
        fed.setActiveWildWeasel(ww);
    }

    @Test
    public void wildWeasel_atExactly35Hexes_survives() {
        placeWildWeasel(36, 16); // same row as fed (1,16) → range 35

        game.advancePhase(); // Movement resolves; shuttle pass runs the J3.13 check

        assertNotNull("WW at exactly 35 hexes must remain active", fed.getActiveWildWeasel());
        assertTrue(game.getActiveShuttles().contains(ww));
    }

    @Test
    public void wildWeasel_beyond35Hexes_isVoided() {
        placeWildWeasel(37, 16); // range 36 from fed

        Game.ActionResult r = game.advancePhase();

        assertNull("WW beyond 35 hexes must be voided (J3.13)", fed.getActiveWildWeasel());
        assertFalse(game.getActiveShuttles().contains(ww));
        assertTrue("Log must explain the void: " + r.getMessage(),
                r.getMessage().contains("voided (J3.13)"));
    }

    // -------------------------------------------------------------------------
    // J3.21 — a destroyed weasel explodes; it is neither removed nor voided
    // -------------------------------------------------------------------------

    @Test
    public void directFireKill_startsExplosionPeriod_notRemoval() {
        placeWildWeasel(3, 16);
        ww.setSpeed(4); // launched drifting — must stop dead when destroyed (J3.21)

        String msg = game.applyDamageToUnit(6, ww, 0); // hull 6 → dead

        assertTrue("Log must announce the explosion: " + msg, msg.contains("exploding"));
        assertTrue("Destroyed WW enters its explosion period", ww.isExploding());
        assertTrue("Destroyed WW stays on the map (J3.21)",
                game.getActiveShuttles().contains(ww));
        assertNotNull("NOT voided — ECM continues through the explosion (J3.2111/J3.232)",
                fed.getActiveWildWeasel());
        assertEquals("Destroyed WW ceases to move (J3.21)", 0, ww.getSpeed());
    }

    @Test
    public void explodingWeasel_cannotBeKilledAgain() {
        placeWildWeasel(3, 16);
        game.applyDamageToUnit(6, ww, 0);
        assertTrue(ww.isExploding());

        String msg = game.applyDamageToUnit(10, ww, 0);

        assertTrue("Second kill is a no-op: " + msg, msg.contains("already destroyed"));
        assertTrue(game.getActiveShuttles().contains(ww));
    }

    @Test
    public void explosionPeriod_transitionsToPostExplosion() {
        placeWildWeasel(3, 16);
        game.applyDamageToUnit(6, ww, 0);

        // Advance well past the 4-impulse explosion window; the seeker pass
        // transitions exploding weasels each time the Movement phase resolves
        for (int guard = 0; guard < 40 && !ww.isPostExplosion(); guard++)
            game.advancePhase();

        assertTrue("Explosion must give way to the ionized-radiation pocket (J3.212)",
                ww.isPostExplosion());
        assertFalse(ww.isExploding());
    }
}
