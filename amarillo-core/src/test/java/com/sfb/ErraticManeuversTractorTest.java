package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;

/**
 * C10.24: "EM cannot be conducted while the unit is ... held by a tractor beam (G7.92)."
 * <p>
 * A unit under tow is not manoeuvring; it is being moved. The rule has two directions and both
 * matter:
 * <ul>
 *   <li>a held unit may not BEGIN EM — caught where the announcement is refused;</li>
 *   <li>a unit already using EM that is THEN grabbed stops — which no announcement can catch,
 *       because being tractored is not something the victim declares.</li>
 * </ul>
 * C10.24 lists two further conditions this engine cannot ask about: a unit in a WEB (G10.57),
 * there being no web casters, and one DOCKED to another ship (C13.923).
 */
public class ErraticManeuversTractorTest {

    private Game game;
    private Ship ship;
    private Ship tug;

    @Before
    public void setUp() {
        game = new Game();

        Player fed = new Player();
        fed.setTeamName("Federation");
        Player klingon = new Player();
        klingon.setTeamName("Klingon");

        ship = new Ship();
        ship.init(com.sfb.samples.FederationShips.getFedCa());
        ship.setName("USS Evader");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        ship.setOwner(fed);
        game.getShips().add(ship);

        tug = new Ship();
        tug.init(com.sfb.samples.KlingonShips.getD7());
        tug.setName("IKV Grip");
        tug.setLocation(new Location(10, 11));
        tug.setFacing(13);
        tug.setOwner(klingon);
        game.getShips().add(tug);

        game.startTurn();
        // EM is bought at allocation (C10.11); without that the refusal would be that one.
        for (Ship s : game.getShips()) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setErraticManuvers(s.getPerformanceData().getErraticCost());
            game.submitAllocation(s, e);
        }
        assertTrue("the fixture must have paid for EM", ship.hasPaidForEm());
    }

    /** Run to the end of the impulse, where an announcement comes into force (Stage 6E). */
    private void finishTheImpulse() {
        for (int i = 0; i < 8 && game.getCurrentPhase() != Game.ImpulsePhase.END_OF_IMPULSE; i++)
            game.advancePhase();
        game.advancePhase();
    }

    // ---------------------------------------------------------------- may not begin

    @Test
    public void aTractoredShipMayNotBeginEm() {
        ship.applyTractor(tug);
        assertTrue("fixture must actually be held", ship.isTractored());

        Game.ActionResult result = game.announceErraticManeuvers(ship, true);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("C10.24"));
        assertTrue(result.getMessage(), result.getMessage().contains("tractor"));
        assertFalse("and it is not manoeuvring", ship.isUsingEm());
    }

    /** The refusal names C10.24 rather than the energy clause — it DID pay for EM. */
    @Test
    public void theRefusalIsTheTractorNotTheEnergy() {
        ship.applyTractor(tug);
        String message = game.announceErraticManeuvers(ship, true).getMessage();

        assertFalse("it paid at allocation, so C10.11 is not the reason: " + message,
                message.contains("C10.11"));
    }

    @Test
    public void anUnheldShipMayBeginEmPerfectlyWell() {
        assertFalse(ship.isTractored());

        Game.ActionResult result = game.announceErraticManeuvers(ship, true);

        assertTrue(result.getMessage(), result.isSuccess());
        finishTheImpulse();
        assertTrue("in force at the end of the impulse (C10.311)", ship.isUsingEm());
    }

    // ---------------------------------------------------------------- and must stop

    /**
     * The direction an announcement cannot cover: already manoeuvring, then grabbed. Nothing the
     * victim declares, so Stage 6E has to notice.
     */
    @Test
    public void beingGrabbedWhileManoeuvringStopsEm() {
        assertTrue(game.announceErraticManeuvers(ship, true).isSuccess());
        finishTheImpulse();
        assertTrue("fixture: it is manoeuvring", ship.isUsingEm());

        ship.applyTractor(tug);
        finishTheImpulse();

        assertFalse("held, so no longer conducting EM (C10.24)", ship.isUsingEm());
    }

    /** And C10.32 still bars a restart in the same turn, whatever ended it. */
    @Test
    public void itMayNotSimplyRestartOnceReleased() {
        assertTrue(game.announceErraticManeuvers(ship, true).isSuccess());
        finishTheImpulse();
        ship.applyTractor(tug);
        finishTheImpulse();
        assertFalse(ship.isUsingEm());

        ship.releaseTractor();
        Game.ActionResult again = game.announceErraticManeuvers(ship, true);

        assertFalse("C10.31/C10.32: one start per turn", again.isSuccess());
        assertTrue(again.getMessage(), again.getMessage().contains("C10.31"));
    }

    /** A ship not under EM at all is untouched by the sweep — no spurious log, no state change. */
    @Test
    public void aHeldShipThatWasNeverManoeuvringIsUnaffected() {
        ship.applyTractor(tug);
        finishTheImpulse();

        assertFalse(ship.isUsingEm());
        assertFalse("and it has not burned its once-per-turn start",
                ship.hasStartedEmThisTurn());
    }
}
