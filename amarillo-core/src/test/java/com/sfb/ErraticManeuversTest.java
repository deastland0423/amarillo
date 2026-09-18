package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.EwBreakdown;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.OrionShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Erratic Maneuvers (C10.0), slice A — the trade.
 * <p>
 * EM is what a smaller unit does when it would rather not be hit than hit back: minor but
 * sharp random changes around a base course. The bargain is four points of ECM against
 * everything shooting at you (C10.41), paid for with four points against your own fire
 * (C10.414), a Turn Mode a hex longer and a worse HET roll (C10.55), and six hexes' worth
 * of movement energy (C10.11).
 * <p>
 * The timing is the part worth pinning. EM is announced in the Final Movement Actions
 * Stage but comes into force only in the Post-Combat Segment at the END of that impulse
 * (C10.311) — so a ship announcing EM and then being fired on during that same impulse
 * gets nothing for it. The rulebook's own D6.33 example turns on exactly that.
 */
public class ErraticManeuversTest {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();
        fed = ship("USS Enterprise", 10, 10, FederationShips.getFedCa());
        klingon = ship("IKS Fury", 10, 14, KlingonShips.getD7());
        game.startTurn();
    }

    private Ship ship(String name, int col, int row, java.util.Map<String, Object> spec) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(col, row));
        s.setFacing(1);
        s.setSpeed(12);
        s.setSpeedPreviousTurn(31);
        s.setSpeedTwoTurnsAgo(31);
        s.setActiveFireControl(true);
        game.getShips().add(s);
        return s;
    }

    /** Allocate for every ship, paying EM for {@code payer} if given. */
    private void allocateAll(Ship payer) {
        for (Ship s : new java.util.ArrayList<>(game.getShips())) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            e.setWarpMovement(0.0);
            if (s == payer)
                e.setErraticManuvers(s.getPerformanceData().getErraticCost());
            game.submitAllocation(s, e);
        }
    }

    /** Announce EM and carry it through to the end of the impulse, where it takes effect. */
    private void startEmAndLetItTakeEffect(Ship s) {
        Game.ActionResult r = game.announceErraticManeuvers(s, true);
        assertTrue(r.getMessage(), r.isSuccess());
        int announcedOn = game.getAbsoluteImpulse();
        for (int guard = 0; guard < 40 && !s.isUsingEm(); guard++)
            game.advancePhase();
        assertTrue("EM should be in force by the end of the impulse it was announced on",
                s.isUsingEm());
        // It comes into force at Stage 6E of the announcing impulse. advancePhase() runs 6E
        // and rolls the clock in one call, so the state first READS true on the next
        // impulse; what matters for the rule is that it took no longer than that. The
        // during-the-impulse half is pinned by announcingDoesNotStartIt_theEndOfTheImpulseDoes.
        assertTrue("EM must be in force by the end of the impulse it was announced on, not "
                        + "some impulses later (C10.311)",
                game.getAbsoluteImpulse() <= announcedOn + 1);
    }

    // ---------------------------------------------------------------- the cost (C10.11/12)

    @Test
    public void costIsSixHexesOfMovementForANormalShipAndThreeForANimbleOne() {
        // C10.11: six hexes' worth. C10.12: three for a nimble ship (C11.23).
        assertEquals("a move-cost-1 cruiser pays six (C10.11)",
                6.0, fed.getPerformanceData().getErraticCost(), 0.0001);

        Ship orion = new Ship();
        orion.init(OrionShips.getLr());
        assertTrue("the Orion LR is a nimble ship", orion.isNimble());
        assertEquals("half as much for a nimble ship (C10.12)",
                orion.getPerformanceData().getMovementCost() * 3,
                orion.getPerformanceData().getErraticCost(), 0.0001);
    }

    @Test
    public void emCannotBeAnnouncedWithoutPayingForIt() {
        allocateAll(null);   // nobody bought EM

        Game.ActionResult r = game.announceErraticManeuvers(fed, true);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("C10.11"));
    }

    // ---------------------------------------------------------------- the timing (C10.311)

    @Test
    public void announcingDoesNotStartIt_theEndOfTheImpulseDoes() {
        allocateAll(fed);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();

        assertTrue(game.announceErraticManeuvers(fed, true).isSuccess());

        assertFalse("C10.311: announcing in the Final Movement Actions Stage does not start "
                + "it — the Post-Combat Segment does", fed.isUsingEm());
        assertEquals("so a ship fired on during its announcing impulse gets no ECM for it",
                0, game.ewAgainst(klingon, fed).natural());
    }

    @Test
    public void itIsInForceByTheEndOfThatImpulse() {
        allocateAll(fed);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        startEmAndLetItTakeEffect(fed);
    }

    @Test
    public void havingStoppedItCannotBeRestartedThisTurn() {
        allocateAll(fed);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        startEmAndLetItTakeEffect(fed);

        assertTrue(game.announceErraticManeuvers(fed, false).isSuccess());
        for (int guard = 0; guard < 40 && fed.isUsingEm(); guard++)
            game.advancePhase();
        assertFalse("it stops at the end of the impulse announced (C10.32)", fed.isUsingEm());

        Game.ActionResult again = game.announceErraticManeuvers(fed, true);
        assertFalse(again.isSuccess());
        assertTrue(again.getMessage(), again.getMessage().contains("C10.31"));
    }

    // ---------------------------------------------------------------- the ECM (C10.41)

    @Test
    public void emGivesFourPointsOfNaturalEcmAgainstFireAimedAtIt() {
        allocateAll(fed);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        startEmAndLetItTakeEffect(fed);

        EwBreakdown ew = game.ewAgainst(klingon, fed);

        assertEquals("C10.41: four points", 4, ew.natural());
        assertEquals("C10.412: counted as a NATURAL source, not generated or lent",
                4, ew.total());
        assertEquals("C10.42: four points is a +2 die shift", 2, Game.netEcmShift(4));
    }

    @Test
    public void thatEcmIsNaturalSoEvenAFriendlyActionFeelsIt() {
        // C10.412 puts EM's ECM in the one bucket D6.3146 refuses to exempt.
        allocateAll(fed);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        startEmAndLetItTakeEffect(fed);

        assertEquals(4, game.ewAgainst(klingon, fed).totalFriendly());
    }

    @Test
    public void emAlsoDegradesTheFireOfTheShipDoingIt() {
        allocateAll(fed);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        startEmAndLetItTakeEffect(fed);

        // C10.414: "This ECM is ALSO applied to direct-fire weapons fired BY the unit using
        // EM" — the maneuvers are too violent to shoot accurately through.
        assertEquals("the EM ship's own fire is degraded too",
                4, game.ewAgainst(fed, klingon).natural());
    }

    @Test
    public void eccmCanBurnThroughIt() {
        // C10.411: as with most other forms of ECM, this can be offset by ECCM.
        allocateAll(fed);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        startEmAndLetItTakeEffect(fed);

        klingon.setEccmAllocated(4);

        assertEquals("four points of ECCM answers four points of EM (D6.34 step 4)",
                0, game.fireEcmShift(klingon, fed));
    }

    // ---------------------------------------------------------------- the handling (C10.55)

    @Test
    public void emLengthensTheTurnModeByOneHex() {
        allocateAll(fed);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        // Measured AFTER allocation: Turn Mode is a function of speed, and allocation is
        // what settles the speed. Reading it beforehand compares two different speeds.
        int before = fed.getTurnHexes();

        startEmAndLetItTakeEffect(fed);

        assertEquals("C10.55: one hex longer, and the Turn Category is unchanged",
                before + 1, fed.getTurnHexes());
    }

    @Test
    public void aNimbleShipIsExemptFromTheTurnModePenalty() {
        Ship orion = new Ship();
        orion.init(OrionShips.getLr());
        orion.setName("Fast Eddie");
        orion.setLocation(new Location(14, 10));
        orion.setFacing(1);
        orion.setSpeed(12);
        orion.setSpeedPreviousTurn(31);
        orion.setSpeedTwoTurnsAgo(31);
        orion.setActiveFireControl(true);
        game.getShips().add(orion);

        allocateAll(orion);
        while (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
        int before = orion.getTurnHexes();   // after allocation, so speed is settled

        startEmAndLetItTakeEffect(orion);

        assertEquals("C10.55: nimble units are exempt from the Turn Mode penalty",
                before, orion.getTurnHexes());
    }

    @Test
    public void aShuttleCountsAsNimbleForTheseExemptions() {
        // C11.1: all shuttlecraft and fighters are nimble unless a rule says otherwise.
        assertTrue(new com.sfb.objects.shuttles.AdminShuttle().isNimbleUnit());
        assertTrue(new com.sfb.objects.shuttles.Stinger1().isNimbleUnit());
        assertFalse("a seeking weapon is not nimble",
                new com.sfb.objects.Drone(com.sfb.objects.DroneType.TypeI).isNimbleUnit());
    }
}
