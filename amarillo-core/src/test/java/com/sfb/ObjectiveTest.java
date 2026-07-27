package com.sfb;

import com.sfb.objects.Objective;
import com.sfb.objects.Ship;
import com.sfb.properties.BattleStatus;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.properties.RetrievalMethod;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Objective visible-capture MVP: free/carried lifecycle, retrieval-method
 * gating, range + fire-control preconditions, and drop-on-destruction
 * (survives → free in the hex; else annihilated).
 */
public class ObjectiveTest {

    private Game game;
    private Ship fed;

    @Before
    public void setUp() {
        game = new Game();
        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed);
    }

    private Objective addObjective(String name, int col, int row, RetrievalMethod... methods) {
        Objective o = new Objective(name, col, row);
        for (RetrievalMethod m : methods)
            o.getAllowedRetrieval().add(m);
        game.addObjective(o);
        return o;
    }

    private void toActivityPhase() {
        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(fed.getLifeSupportCost());
        e.setFireControl(fed.getFireControlCost());
        e.setActivateShields(fed.getActiveShieldCost());
        e.setWarpMovement(0.0);
        game.submitAllocation(fed, e);
        for (int guard = 0; guard < 20 && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    // -------------------------------------------------------------------------
    // Lifecycle + pickup
    // -------------------------------------------------------------------------

    @Test
    public void newObjective_isFree() {
        Objective o = addObjective("Stasis Box", 11, 10, RetrievalMethod.TRANSPORTER);
        assertTrue(o.isFree());
        assertEquals(new Location(11, 10), o.getEffectiveLocation());
    }

    @Test
    public void transporterPickup_succeedsInRangeWithFc() {
        Objective o = addObjective("Stasis Box", 12, 10, RetrievalMethod.TRANSPORTER);
        toActivityPhase();
        fed.setActiveFireControl(true);

        Game.ActionResult r = game.pickUpObjective(fed, "Stasis Box", RetrievalMethod.TRANSPORTER);
        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(o.isCarried());
        assertEquals(fed, o.getCarrier());
        // Carried objective's effective location tracks the carrier
        assertEquals(fed.getLocation(), o.getEffectiveLocation());
    }

    @Test
    public void pickup_wrongMethod_refused() {
        addObjective("Canister", 12, 10, RetrievalMethod.TRACTOR); // tractor-only
        toActivityPhase();
        fed.setActiveFireControl(true);

        Game.ActionResult r = game.pickUpObjective(fed, "Canister", RetrievalMethod.TRANSPORTER);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("cannot be retrieved by TRANSPORTER"));
    }

    @Test
    public void pickup_outOfRange_refused() {
        addObjective("Far Box", 20, 10, RetrievalMethod.TRANSPORTER); // 10 hexes > 5
        toActivityPhase();
        fed.setActiveFireControl(true);

        Game.ActionResult r = game.pickUpObjective(fed, "Far Box", RetrievalMethod.TRANSPORTER);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("out of"));
    }

    @Test
    public void pickup_withoutFireControl_refused() {
        addObjective("Box", 12, 10, RetrievalMethod.TRANSPORTER);
        toActivityPhase();
        fed.setActiveFireControl(false);

        Game.ActionResult r = game.pickUpObjective(fed, "Box", RetrievalMethod.TRANSPORTER);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("fire control"));
    }

    @Test
    public void pickup_alreadyCarried_refused() {
        addObjective("Box", 12, 10, RetrievalMethod.TRANSPORTER);
        toActivityPhase();
        fed.setActiveFireControl(true);
        assertTrue(game.pickUpObjective(fed, "Box", RetrievalMethod.TRANSPORTER).isSuccess());

        Game.ActionResult again = game.pickUpObjective(fed, "Box", RetrievalMethod.TRANSPORTER);
        assertFalse(again.isSuccess());
        assertTrue(again.getMessage().contains("already aboard"));
    }

    @Test
    public void pickup_onlyDuringActivity() {
        addObjective("Box", 12, 10, RetrievalMethod.TRANSPORTER);
        fed.setActiveFireControl(true);
        // still in pre-turn / not Activity
        Game.ActionResult r = game.pickUpObjective(fed, "Box", RetrievalMethod.TRANSPORTER);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("Activity phase"));
    }

    // -------------------------------------------------------------------------
    // Drop on carrier destruction
    // -------------------------------------------------------------------------

    @Test
    public void survivingObjective_dropsFreeWhenCarrierDestroyed() {
        Objective o = addObjective("Stasis Box", 12, 10, RetrievalMethod.TRANSPORTER);
        o.setSurvivesCarrierDestruction(true);
        toActivityPhase();
        fed.setActiveFireControl(true);
        assertTrue(game.pickUpObjective(fed, "Stasis Box", RetrievalMethod.TRANSPORTER).isSuccess());
        Location deathHex = fed.getLocation();

        fed.setBattleStatus(BattleStatus.DESTROYED);
        game.cleanupDestroyedShips();

        assertTrue("box survives the explosion (SH47.475)", game.getObjectives().contains(o));
        assertTrue(o.isFree());
        assertEquals("box drifts free in the death hex", deathHex, o.getLocation());
    }

    // -------------------------------------------------------------------------
    // Ownership: live possession (mutable) vs secured (permanent)
    // -------------------------------------------------------------------------

    private Player owner(String team, Faction faction) {
        Player p = new Player();
        p.setTeamName(team);
        p.setFaction(faction);
        return p;
    }

    @Test
    public void freeObjective_hasNoOwner() {
        Objective o = addObjective("Box", 11, 10, RetrievalMethod.TRANSPORTER);
        assertNull(o.getCurrentOwner());
    }

    @Test
    public void carriedObjective_currentOwnerIsCarrierOwner_andChangesWhenStolen() {
        Player fed = owner("Federation", Faction.Federation);
        fed.getPlayerUnits().add(this.fed);
        this.fed.setOwner(fed);
        Objective o = addObjective("Box", 12, 10, RetrievalMethod.TRANSPORTER);
        toActivityPhase();
        this.fed.setActiveFireControl(true);
        assertTrue(game.pickUpObjective(this.fed, "Box", RetrievalMethod.TRANSPORTER).isSuccess());
        assertEquals("live possession follows the carrier's owner", fed, o.getCurrentOwner());

        // "Stolen": a Klingon takes it — just re-carry, possession follows
        Ship klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        Player kli = owner("Klingon", Faction.Klingon);
        klingon.setOwner(kli);
        o.setCarrier(klingon);
        assertEquals("possession changed hands to the new carrier", kli, o.getCurrentOwner());
    }

    @Test
    public void disengagingBySeparation_securesObjectivePermanently() {
        Player fed = owner("Federation", Faction.Federation);
        this.fed.setOwner(fed);
        // Lone ship on an empty map → separation conditions met
        Objective o = addObjective("Box", 12, 10, RetrievalMethod.TRANSPORTER);
        toActivityPhase();
        this.fed.setActiveFireControl(true);
        assertTrue(game.pickUpObjective(this.fed, "Box", RetrievalMethod.TRANSPORTER).isSuccess());

        Game.ActionResult r = game.disengageBySeparation(this.fed);
        assertTrue(r.getMessage(), r.isSuccess());

        assertTrue("carried off the map → secured", o.isSecured());
        assertEquals("permanent owner is the disengaging player", fed, o.getSecuredBy());
        assertEquals(fed, o.getCurrentOwner());
        assertNull("out of play — no map location", o.getLocation());
        assertFalse(o.isFree());
        assertFalse(o.isCarried());
    }

    @Test
    public void securedObjective_cannotBePickedUpAgain() {
        Player fed = owner("Federation", Faction.Federation);
        this.fed.setOwner(fed);
        Objective o = addObjective("Box", 12, 10, RetrievalMethod.TRANSPORTER);
        o.setSecuredBy(fed);
        o.setCarrier(null);
        toActivityPhase();
        this.fed.setActiveFireControl(true);

        Game.ActionResult r = game.pickUpObjective(this.fed, "Box", RetrievalMethod.TRANSPORTER);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("out of play"));
    }

    @Test
    public void nonSurvivingObjective_annihilatedWithCarrier() {
        Objective o = addObjective("Canister", 12, 10, RetrievalMethod.TRACTOR);
        o.setSurvivesCarrierDestruction(false);
        toActivityPhase();
        fed.setActiveFireControl(true);
        assertTrue(game.pickUpObjective(fed, "Canister", RetrievalMethod.TRACTOR).isSuccess());

        fed.setBattleStatus(BattleStatus.DESTROYED);
        game.cleanupDestroyedShips();

        assertFalse("canister annihilated with its carrier (SH35.454)", game.getObjectives().contains(o));
    }
}
