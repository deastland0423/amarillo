package com.sfb;

import com.sfb.objects.Objective;
import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.RetrievalMethod;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * SH35.452: a probe canister is retrieved with the real systems — caught in a
 * tractor beam (subject to the D6.37 ring/asteroid natural-ECM roll, since the
 * inert canister has no EW of its own) and drawn aboard over impulses with the
 * J1.621 rotation procedure, counting as a shuttle landing. No bespoke pickup
 * verb is involved.
 */
public class ObjectiveRecoveryTest {

    private Game game;
    private Ship fed;

    @Before
    public void setUp() {
        game = new Game();
        Player fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setOwner(fedPlayer);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed);

        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(fed.getLifeSupportCost());
        e.setFireControl(fed.getFireControlCost());
        e.setActivateShields(fed.getActiveShieldCost());
        e.setWarpMovement(0.0);
        game.submitAllocation(fed, e);
    }

    private void advanceToActivity() {
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    private Objective addCanister(int col, int row) {
        Objective o = new Objective("Probe Canister", col, row);
        o.getAllowedRetrieval().add(RetrievalMethod.TRACTOR);
        o.setSurvivesCarrierDestruction(false); // annihilated with carrier (SH35.454)
        game.addObjective(o);
        return o;
    }

    // -------------------------------------------------------------------------
    // The SH35.452 payoff: the ring's natural ECM degrades the tractor lock,
    // even though a canister carries no EW of its own.
    // -------------------------------------------------------------------------

    @Test
    public void canisterTractorLock_isDegradedByTerrainEcmAlongLine() {
        Objective o = addCanister(13, 10); // range 3 from fed at (10,10)
        fed.setActiveFireControl(true);

        assertEquals("open space — a bare d6 can't miss, so no D6.37 roll",
                0, game.d637Shift(fed, o));

        // Two asteroid hexes on the line of fire → 2 natural ECM (P3.33)
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 11, 10));
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 12, 10));

        assertTrue("terrain ECM now shifts the canister's tractor lock roll (SH35.452)",
                game.d637Shift(fed, o) > 0);
    }

    // -------------------------------------------------------------------------
    // Full J1.621 recovery: tractor → declare recovery → pulled aboard.
    // -------------------------------------------------------------------------

    @Test
    public void tractorThenRecover_bringsCanisterAboard() {
        Objective o = addCanister(10, 9); // range 1 from fed at (10,10)
        advanceToActivity();
        fed.setActiveFireControl(true);
        fed.getTractors().initForTurn(6);
        fed.getPowerSystems().setBatteryPower(0);

        // Tractor the canister — non-ship target resolves immediately (G7.5)
        assertTrue(game.establishTractor(fed, "Probe Canister", 1).isSuccess());
        assertEquals(fed, o.getTractoringUnit());
        assertTrue("still free on the map until actually recovered", o.isFree());

        // Declare the J1.621 rotation recovery
        assertTrue(game.beginObjectiveRecovery(fed, "Probe Canister").isSuccess());
        assertTrue(o.isBeingRecovered());

        // Run impulses — pulled to the ship's hex and brought aboard
        for (int i = 0; i < 24; i++)
            game.advancePhase();

        assertTrue("canister carried after J1.621 recovery", o.isCarried());
        assertEquals(fed, o.getCarrier());
        assertFalse(o.isBeingRecovered());
        assertNull("out of hex — location derives from the carrier", o.getLocation());
    }

    @Test
    public void recovery_requiresTractorFirst() {
        addCanister(10, 9);
        advanceToActivity();

        Game.ActionResult r = game.beginObjectiveRecovery(fed, "Probe Canister");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("tractor beam first"));
    }

    @Test
    public void releasingBeam_endsRecovery_leavingCanisterFree() {
        Objective o = addCanister(10, 9);
        advanceToActivity();
        fed.setActiveFireControl(true);
        fed.getTractors().initForTurn(6);
        fed.getPowerSystems().setBatteryPower(0);
        assertTrue(game.establishTractor(fed, "Probe Canister", 1).isSuccess());
        assertTrue(game.beginObjectiveRecovery(fed, "Probe Canister").isSuccess());

        fed.getTractors().releaseTractor(o); // J1.6221

        assertFalse(o.isBeingRecovered());
        assertNull(o.getTractoringUnit());
        assertTrue("still free, back to a plain map canister", o.isFree());
    }
}
