package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.ESG;
import com.sfb.weapons.Weapon;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ESG against Erratic Maneuvers (C10.521, G23.313, G23.314).
 *
 * <h2>Why this is not a guard</h2>
 * C10.521 lists ESGs among the systems "a unit using EM cannot operate", which reads like one
 * more {@code emActionBlock} door. It is not, and the two clauses it cites say why:
 *
 * <ul>
 *   <li><b>G23.314:</b> "A ship that is using Erratic Maneuvers cannot have an active ESG field.
 *       The field CAN be announced while under EM, but the ship must cease EM before the field
 *       is released or the field is voided and the power is lost."</li>
 *   <li><b>G23.313:</b> names EM among the conditions that drop a field INVOLUNTARILY, and
 *       requires the drop be announced "when the causal condition takes effect".</li>
 * </ul>
 *
 * So nothing is ever refused. Announcing under EM is legal; an ACTIVE field is the casualty when
 * EM comes into force; and an announcement that matures while EM is still running is voided with
 * its energy. Three different outcomes at three different moments, and a guard written at the
 * announcement would get all three wrong.
 *
 * <p>This gap opened by itself: the EM audit of 2026-10-19 recorded ESGs as "unbuilt here so
 * they cost nothing yet", and ESGs then landed on 26 hulls. Found 2026-11-09 by re-auditing the
 * clause rather than trusting that entry.
 */
public class EsgErraticManeuversTest {

    /**
     * (G23.314) EM comes into force and the field it finds up is dropped. The ship is not
     * stopped from manoeuvring — C10.311 timing holds, so the drop lands at the END of the
     * announcing impulse, with EM itself.
     */
    @Test
    public void startingEmDropsAnActiveField() {
        Game game = battle();
        Ship ship = shipIn(game);
        ESG esg = esgOf(ship);
        esg.activate(2, game.getAbsoluteImpulse());
        assertTrue("fixture: a field is up", esg.isActive());

        assertTrue(game.announceErraticManeuvers(ship, true).isSuccess());
        runUntilEmInForce(game, ship);

        assertFalse("a ship using EM cannot hold a field (G23.314)", esg.isActive());
    }

    /** And the drop arms the reactivation lockout, like any other drop (G23.323). */
    @Test
    public void theDroppedFieldCannotSimplyBeRaisedAgain() {
        Game game = battle();
        Ship ship = shipIn(game);
        ESG esg = esgOf(ship);
        esg.activate(2, game.getAbsoluteImpulse());
        assertTrue(game.announceErraticManeuvers(ship, true).isSuccess());
        runUntilEmInForce(game, ship);

        // The isActive assertion is load-bearing, not decoration. canAnnounce() is also false
        // while a field is UP, so without it this passed on the unfixed code for the wrong
        // reason — proven by mutation. With the field provably down, a refusal here can only
        // be the lockout.
        assertFalse("fixture: the field is down", esg.isActive());
        assertFalse("the field counts as dropped, not as never having existed (G23.323)",
                esg.canAnnounce(game.getAbsoluteImpulse()));
    }

    /**
     * (G23.314) "The field can be announced while under EM" — in so many words. A guard on the
     * announcement would be the obvious implementation and would contradict the rule.
     */
    @Test
    public void anEsgMayBeAnnouncedWhileUnderEm() {
        Game game = battle();
        Ship ship = shipIn(game);
        assertTrue(game.announceErraticManeuvers(ship, true).isSuccess());
        runUntilEmInForce(game, ship);
        advanceToActivity(game);

        Game.ActionResult announced = game.announceEsg(ship, "A", 2, 3);

        assertTrue(announced.getMessage(), announced.isSuccess());
        assertTrue(esgOf(ship).isAnnounced());
    }

    /** Nor does EM coming into force cancel an announcement already standing. */
    @Test
    public void startingEmDoesNotCancelAPendingAnnouncement() {
        Game game = battle();
        Ship ship = shipIn(game);
        advanceToActivity(game);
        assertTrue(game.announceEsg(ship, "A", 2, 3).isSuccess());

        assertTrue(game.announceErraticManeuvers(ship, true).isSuccess());
        runUntilEmInForce(game, ship);

        assertTrue("only the RELEASE is barred, not the announcement (G23.314)",
                esgOf(ship).isAnnounced());
    }

    /**
     * The punishment clause: the release moment arrives with EM still running, so the field is
     * voided and the power is lost.
     */
    @Test
    public void aReleaseThatMaturesUnderEmIsVoidedAndThePowerIsLost() {
        Game game = battle();
        Ship ship = shipIn(game);
        ESG esg = esgOf(ship);
        assertTrue(game.announceErraticManeuvers(ship, true).isSuccess());
        runUntilEmInForce(game, ship);
        advanceToActivity(game);
        assertTrue(game.announceEsg(ship, "A", 2, 3).isSuccess());
        int releaseAt = esg.getReleaseImpulse();

        runPastImpulse(game, releaseAt);

        assertTrue("fixture: EM is still in force at the release", ship.isUsingEm());
        assertFalse("no field forms (G23.314)", esg.isActive());
        assertFalse("and the announcement is spent, not left standing", esg.isAnnounced());
        assertEquals("the power is lost, not kept (G23.314)", 0, esg.getStoredEnergy());
    }

    /**
     * And the other half of the same sentence: cease EM before the release and the field forms
     * normally. Without this, an implementation that simply voided every release would pass the
     * test above and be wrong — which is the whole point of "the ship must cease EM BEFORE the
     * field is released".
     */
    @Test
    public void ceasingEmBeforeTheReleaseLetsTheFieldForm() {
        Game game = battle();
        Ship ship = shipIn(game);
        ESG esg = esgOf(ship);
        assertTrue(game.announceErraticManeuvers(ship, true).isSuccess());
        runUntilEmInForce(game, ship);
        advanceToActivity(game);
        assertTrue(game.announceEsg(ship, "A", 2, 3).isSuccess());
        int releaseAt = esg.getReleaseImpulse();

        assertTrue(game.announceErraticManeuvers(ship, false).isSuccess());
        runUntilEmCeases(game, ship);
        assertTrue("fixture: the release must still be ahead of us",
                game.getAbsoluteImpulse() < releaseAt);

        runPastImpulse(game, releaseAt);

        assertTrue("EM was ceased in time, so the field forms (G23.314)", esg.isActive());
        assertTrue("with real strength behind it", esg.getStrength() > 0);
    }

    /** A ship NOT using EM is untouched by any of this — the control case. */
    @Test
    public void aShipNotUsingEmKeepsItsField() {
        Game game = battle();
        Ship ship = shipIn(game);
        ESG esg = esgOf(ship);
        esg.activate(2, game.getAbsoluteImpulse());

        for (int i = 0; i < 12; i++)
            game.advancePhase();

        assertTrue("nothing drops a field but the rules that say so", esg.isActive());
    }

    // ---------------------------------------------------------------- helpers

    /** Advance phases until the announced EM is in force (Stage 6E, C10.311). */
    private static void runUntilEmInForce(Game game, Ship ship) {
        for (int guard = 0; guard < 200; guard++) {
            if (ship.isUsingEm())
                return;
            game.advancePhase();
        }
        fail("EM never came into force");
    }

    private static void runUntilEmCeases(Game game, Ship ship) {
        for (int guard = 0; guard < 200; guard++) {
            if (!ship.isUsingEm())
                return;
            game.advancePhase();
        }
        fail("EM never ceased");
    }

    /** Advance until the clock is past the given absolute impulse, so a release has resolved. */
    private static void runPastImpulse(Game game, int impulse) {
        for (int guard = 0; guard < 400; guard++) {
            if (game.getAbsoluteImpulse() > impulse)
                return;
            game.advancePhase();
        }
        fail("never reached impulse " + impulse);
    }

    private static void advanceToActivity(Game game) {
        for (int guard = 0; guard < 80; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY)
                return;
            game.advancePhase();
        }
        fail("never reached ACTIVITY");
    }

    /**
     * One stationary ship carrying an ESG with three points stored, having paid for Erratic
     * Maneuvers. The hull is a Federation CA with an ESG bolted on, exactly as
     * {@code EsgCloakTest} does it: this is about C10.52 and G23.31, and no clause here turns
     * on whose ESG it is.
     */
    private static Game battle() {
        Game game = new Game();

        Player lyran = new Player();
        lyran.setName("Captain Dan");
        lyran.setTeamName("Lyrans");
        game.getPlayers().add(lyran);

        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("LCS Prowler");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        ship.setSpeedPreviousTurn(0);
        ship.setSpeedTwoTurnsAgo(0);
        ship.setOwner(lyran);
        lyran.getPlayerUnits().add(ship);

        ESG esg = new ESG();
        esg.setDesignator("A");
        ship.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(3);

        game.getShips().add(ship);

        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setErraticManuvers(ship.getPerformanceData().getErraticCost());
        game.submitAllocation(ship, e);
        return game;
    }

    private static Ship shipIn(Game game) {
        return game.getShips().get(0);
    }

    private static ESG esgOf(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof ESG)
                return (ESG) w;
        throw new AssertionError("no ESG on " + ship.getName());
    }
}
