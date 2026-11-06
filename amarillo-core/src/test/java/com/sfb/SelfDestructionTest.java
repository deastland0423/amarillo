package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.OrionShips;
import com.sfb.systemgroups.Energy;
import com.sfb.utilities.MapUtils;

/**
 * (D5.1)/(D5.51) Self-destruction as a player action.
 *
 * <p>The gate is the interesting half. (D5.51) will not let a healthy ship blow itself up, and
 * says why in its own last sentence: "This restriction cannot be violated simply for the purpose
 * of destroying nearby enemy units or for any other purpose." Without that, self-destruction is
 * a weapon — drive a frigate into a fleet and set it off — and the explosion we just built would
 * make it a good one.
 */
public class SelfDestructionTest {

    private static final Location GROUND_ZERO = new Location(10, 10);
    private static final Location NEXT_DOOR   = new Location(10, 11);

    private Game game;
    private Player red;
    private Player blue;

    @Before
    public void setUp() {
        game = new Game();
        red  = new Player();
        blue = new Player();
        assertEquals("fixture: the hexes are adjacent",
                1, MapUtils.getRange(GROUND_ZERO, NEXT_DOOR));
    }

    private Ship ship(java.util.Map<String, Object> spec, String name, Location where, Player owner) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(where);
        s.setFacing(1);
        s.setOwner(owner);
        game.getShips().add(s);
        return s;
    }

    private Ship fed(String name, Location where, Player owner) {
        return ship(FederationShips.getFedCa(), name, where, owner);
    }

    /** Strip a ship down to the two crew units (D5.51) allows a self-destruct at. */
    private Ship gutted(String name, Location where, Player owner) {
        Ship s = fed(name, where, owner);
        s.getCrew().setAvailableCrewUnits(2);
        return s;
    }

    /** (D5.1): a self-destructing ship makes no entries at all. */
    private Energy nothing() {
        return new Energy();
    }

    /** What an ordinary ship files, including (D3.32) for its shields. */
    private Energy paying(Ship s) {
        Energy e = new Energy();
        e.setLifeSupport(s.getLifeSupportCost());
        e.setFireControl(s.getFireControlCost());
        e.setActivateShields(s.getActiveShieldCost());
        return e;
    }

    // ------------------------------------------------------------------ the gate

    /**
     * A healthy ship may not. This is the assertion that keeps the feature honest — everything
     * else here is plumbing, and this is the rule.
     */
    @Test
    public void aHealthyShipMayNotSelfDestruct() {
        Ship doomed = fed("Lexington", GROUND_ZERO, red);
        fed("Yorktown", NEXT_DOOR, red);
        game.startTurn();

        Game.ActionResult result = game.declareSelfDestruct(doomed);

        assertFalse("a full crew means no", result.isSuccess());
        assertTrue("and the refusal should quote the rule, not just say no",
                result.getMessage().contains("D5.51"));
        assertFalse(doomed.isSelfDestructDeclared());
    }

    /** All but two crew units dead or evacuated: the rule proper. */
    @Test
    public void aShipStrippedToTwoCrewUnitsMay() {
        Ship doomed = gutted("Lexington", GROUND_ZERO, red);
        fed("Yorktown", NEXT_DOOR, red);
        game.startTurn();

        assertTrue(game.declareSelfDestruct(doomed).isSuccess());
        assertTrue(doomed.isSelfDestructDeclared());
    }

    /** "...if there are more enemy than friendly boarding parties on board." */
    @Test
    public void aShipBeingOverrunMayEvenAtFullCrew() {
        Ship doomed = fed("Lexington", GROUND_ZERO, red);
        fed("Yorktown", NEXT_DOOR, red);
        doomed.addEnemyBoardingParties(doomed.getCrew().getAvailableBoardingParties() + 1);
        game.startTurn();

        assertTrue("losing the boarding fight is losing the ship",
                game.declareSelfDestruct(doomed).isSuccess());
    }

    /** "...or if there are no other friendly ships remaining in play." */
    @Test
    public void theLastShipOfItsSideMayEvenAtFullCrew() {
        Ship doomed = fed("Lexington", GROUND_ZERO, red);
        fed("Klingon", NEXT_DOOR, blue);     // an ENEMY is not a friend
        game.startTurn();

        assertTrue("nothing left to save the crew for",
                game.declareSelfDestruct(doomed).isSuccess());
    }

    /** (G15.1): an Orion does not surrender a hull, whatever shape the crew is in. */
    @Test
    public void anOrionIgnoresTheRestrictionEntirely() {
        Ship raider = ship(OrionShips.getLr(), "Raider", GROUND_ZERO, red);
        fed("Consort", NEXT_DOOR, red);
        game.startTurn();

        assertTrue("G15.1 exempts the Orions", game.declareSelfDestruct(raider).isSuccess());
    }

    /** (D5.1) is a start-of-turn declaration, not something to reach for mid-battle. */
    @Test
    public void itCannotBeDeclaredOnceTheTurnIsRunning() {
        Ship doomed = gutted("Lexington", GROUND_ZERO, red);
        Ship other  = fed("Yorktown", NEXT_DOOR, red);
        game.startTurn();
        game.submitAllocation(doomed, nothing());
        game.submitAllocation(other, nothing());   // impulses have begun

        Game.ActionResult result = game.declareSelfDestruct(doomed);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("start of a turn"));
    }

    // ------------------------------------------------------------------ the resolution

    /**
     * The whole point: it goes off before anything moves, and the blast is (D5.12)'s — the same
     * code a ship destroyed by weapons goes through, which is what the rule says to use.
     */
    @Test
    public void itExplodesBeforeTheFirstImpulseAndDamagesWhatIsNear() {
        Ship doomed = gutted("Lexington", GROUND_ZERO, red);
        Ship victim = fed("Yorktown", NEXT_DOOR, red);
        int before = victim.getShields().getShieldStrength(1);

        game.startTurn();
        assertTrue(game.declareSelfDestruct(doomed).isSuccess());
        // Declaring files the empty form, so this is the last allocation outstanding.
        game.submitAllocation(victim, paying(victim));

        assertFalse("the ship is gone", game.getShips().contains(doomed));
        assertEquals("and its 18 points landed on the facing shield",
                before - 18, victim.getShields().getShieldStrength(1));
    }

    /**
     * Declaring files the Energy Allocation Form, because (D5.1) says the ship makes no entries
     * and can do nothing else. Without that the turn would stall forever waiting on a form the
     * rule forbids.
     */
    @Test
    public void decliningToAllocateDoesNotStallTheTurn() {
        Ship doomed = gutted("Lexington", GROUND_ZERO, red);
        game.startTurn();

        assertTrue("fixture: it is the only ship, so it is the only allocation",
                game.getAllocationQueue().contains(doomed));
        assertTrue(game.declareSelfDestruct(doomed).isSuccess());

        assertTrue("its form is in", game.getAllocationQueue().isEmpty());
        assertFalse("and the turn ran on without it", game.getShips().contains(doomed));
    }

    /** A ship cannot be marked twice, which would otherwise submit two allocations for it. */
    @Test
    public void itCannotBeDeclaredTwice() {
        Ship doomed = gutted("Lexington", GROUND_ZERO, red);
        fed("Yorktown", NEXT_DOOR, red);
        game.startTurn();

        assertTrue(game.declareSelfDestruct(doomed).isSuccess());
        assertFalse("the second attempt is refused",
                game.declareSelfDestruct(doomed).isSuccess());
    }
}
