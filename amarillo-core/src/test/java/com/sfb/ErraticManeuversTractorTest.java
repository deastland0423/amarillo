package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;

/**
 * C10.24: "EM cannot be conducted while the unit is ... held by a tractor beam (G7.92)."
 * <p>
 * The commitment is SUSPENDED, not cancelled, and that distinction is the rule (owner, 2026-10-01):
 * a held unit is still spending the EM energy — it went at allocation and is gone — but it is not
 * manoeuvring, so C10.41's four points of ECM lapse while the tractor holds it. The moment the
 * tractor releases, the effect returns. Nothing is announced in either direction.
 * <p>
 * Cancelling instead would be wrong twice over: the unit would lose the energy for nothing, and
 * C10.32's one-start-per-turn would stop it resuming after release. That was this test's first
 * shape and it was wrong.
 * <p>
 * Because the rule is about the EFFECT, one check covers both timings — grabbed while manoeuvring,
 * or announcing while already held — rather than a refusal at announce time and a cancellation
 * later. Hence {@code Unit.isEmEffective()} beside {@code isUsingEm()}.
 * <p>
 * C10.24 names two further conditions this engine cannot ask about: being in a WEB (G10.57, no web
 * casters) and being DOCKED to another ship (C13.923).
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
        // EM is bought at allocation (C10.11), and the energy is gone whatever happens next.
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

    private void beginEm() {
        assertTrue(game.announceErraticManeuvers(ship, true).isSuccess());
        finishTheImpulse();
        assertTrue("fixture: EM should be in force", ship.isUsingEm());
    }

    // ---------------------------------------------------------------- suspend and resume

    @Test
    public void aTractorSuspendsTheEffectButNotTheCommitment() {
        beginEm();
        assertTrue("manoeuvring and effective", ship.isEmEffective());

        ship.applyTractor(tug);

        assertTrue("the commitment stands — the energy is spent either way", ship.isUsingEm());
        assertFalse("but the ECM effect lapses while held (C10.24)", ship.isEmEffective());
    }

    @Test
    public void releasingTheTractorBringsTheEffectBack() {
        beginEm();
        ship.applyTractor(tug);
        assertFalse(ship.isEmEffective());

        ship.releaseTractor();

        assertTrue("the moment the tractor releases, the effect returns",
                ship.isEmEffective());
        assertTrue(ship.isUsingEm());
    }

    /** No announcement is involved, so nothing is consumed and nothing must be re-declared. */
    @Test
    public void noAnnouncementIsNeededToResume() {
        beginEm();
        ship.applyTractor(tug);
        finishTheImpulse();
        ship.releaseTractor();
        finishTheImpulse();

        assertTrue("still manoeuvring, with no second announcement", ship.isEmEffective());
    }

    /**
     * The reason suspension matters rather than cancellation: C10.32 allows one start per turn, so
     * a cancelled EM could not resume at all and the energy would be wasted.
     */
    @Test
    public void cancellingWouldHaveCostTheTurnsOneStart() {
        beginEm();
        assertTrue("the start is spent — this is why it must not be cancelled",
                ship.hasStartedEmThisTurn());

        ship.applyTractor(tug);
        ship.releaseTractor();

        assertTrue("so resuming must not need a fresh start", ship.isEmEffective());
        assertFalse("and a fresh announcement would indeed be refused",
                game.announceErraticManeuvers(ship, true).isSuccess());
    }

    // ---------------------------------------------------------------- the ECM it suspends

    /** C10.41/.413: four natural points against fire aimed at the EM unit — while effective. */
    @Test
    public void theFourPointsLapseWhileHeldAndReturnAfter() {
        beginEm();
        int manoeuvring = game.ewAgainst(tug, ship).total();

        ship.applyTractor(tug);
        int held = game.ewAgainst(tug, ship).total();

        ship.releaseTractor();
        int released = game.ewAgainst(tug, ship).total();

        assertEquals("C10.41's four points are gone while held", manoeuvring - 4, held);
        assertEquals("and back on release", manoeuvring, released);
    }

    /** C10.414: and the penalty on the EM unit's OWN fire lapses with it. */
    @Test
    public void itsOwnFirePenaltyLapsesTooAndReturns() {
        beginEm();
        int manoeuvring = game.ewAgainst(ship, tug).total();

        ship.applyTractor(tug);
        int held = game.ewAgainst(ship, tug).total();

        ship.releaseTractor();

        assertEquals("the EM unit's own fire is no longer degraded while held",
                manoeuvring - 4, held);
        assertEquals("and is again once released", manoeuvring,
                game.ewAgainst(ship, tug).total());
    }

    /** A ship not manoeuvring at all is unaffected by any of this. */
    @Test
    public void aHeldShipThatWasNeverManoeuvringIsUnaffected() {
        ship.applyTractor(tug);
        finishTheImpulse();

        assertFalse(ship.isUsingEm());
        assertFalse(ship.isEmEffective());
        assertFalse("and it has not burned its once-per-turn start",
                ship.hasStartedEmThisTurn());
    }
}
