package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;

/**
 * C10.5's prohibitions on the systems that have them: transporters, tractor beams and mines.
 *
 * <h2>Why these three, and why together</h2>
 * C10.51 and C10.52 are a LIST of bans across unrelated systems, and the way to get it wrong is
 * to write a separate EM test into each feature as that feature comes up. Three of the list were
 * already honoured — C10.511 launching, C10.512 guiding seekers, C10.516's chaff carve-out — and
 * two more, scout channels and labs, cite the parent C10.52. These are the rest of what is
 * reachable: transporters and tractor beams from C10.521, and mines from C10.514.
 *
 * <p>The clauses this build cannot reach are not tested because the systems do not exist: web and
 * web casters (C10.513), stasis field generators, maulers and ESGs (C10.521), satellite ships
 * (C10.515).
 *
 * <h2>The two carve-outs matter as much as the bans</h2>
 * C10.525 allows negative tractor energy under EM "and this use is not degraded in any way", and
 * nothing in C10.5 stops a ship RELEASING a tractor it already holds. Both are asserted here, so
 * that a later tidy-up which collapses them into the ban fails a test instead of quietly taking
 * a legal option away from a player.
 *
 * <h2>A rulebook misdirection worth knowing</h2>
 * M2.11 sends the reader to "(C10.53)" for the EM mine prohibition. C10.53 is DOCKING. The mine
 * clause is C10.514: "A unit using EM cannot detect, lay, or sweep a mine."
 */
public class EmRestrictionsTest {

    private Game game;
    private Ship ship;
    private Ship other;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        game = new Game();

        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        ship.getTransporters().bankEnergy(1.0);
        ship.setTBombs(3);
        game.getShips().add(ship);

        other = new Ship();
        other.init(FederationShips.getFedCa());
        other.setName("USS Hood");
        other.setLocation(new Location(10, 11));
        other.setFacing(1);
        game.getShips().add(other);

        // Every precondition these actions have, met — because a test that asserts EM refuses
        // an action the fixture could not perform ANYWAY proves nothing. A mutation run made the
        // point: with the guard disabled, three of these were still refused, for an active
        // shield and for having no parties assigned. The refusal has to be EM's or it is not
        // evidence.
        ship.setActiveFireControl(true);
        ship.addLockOn(other);                              // G7.412 tractors, and transporters
        ship.getTransporters().bankEnergy(5.0);
        other.getShields().lowerShield(game.getShieldNumber(ship, other));  // G8.21

        // Transporter and mine actions are Activity-phase only, so walk the machine round to it
        // rather than reaching past the rule.
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
    }

    /**
     * Put the ship into EM through the real path: C10.311's announcement, then Stage 6E bringing
     * it into force. Going straight at the field would let a test pass against a state the game
     * cannot actually reach.
     */
    private void startEm() {
        int impulse = game.getAbsoluteImpulse();
        ship.announceEm(true, impulse);
        assertTrue("fixture: the announcement should come into force at Stage 6E",
                ship.applyEmAnnouncement(impulse));
        assertTrue("fixture: the ship should be conducting EM", ship.isUsingEm());
    }

    // ---------------------------------------------------------------- the fixture is honest

    /**
     * Each action must SUCCEED before EM is declared, or a test asserting the refusal proves
     * nothing — it would pass just as well against a ship that could never do it anyway.
     */
    @Test
    public void theFixtureCanDoAllOfThisBeforeEmIsDeclared() {
        assertTrue("fixture: a T-bomb should be layable",
                game.placeTBomb(ship, new Location(10, 12), true, 1).isSuccess());
        // Once, into a variable: establishTractor opens an AUCTION (G7.42), so calling it twice
        // to build an assertion message makes the second call fail on the first one's state.
        Game.ActionResult tractor = game.establishTractor(ship, "USS Hood", 1);
        assertTrue("fixture: a tractor should be establishable — " + tractor.getMessage(),
                tractor.isSuccess());

        Game.ActionResult boarding = game.performBoardingAction(ship, other, 1, 0);
        assertTrue("fixture: boarding must be POSSIBLE, or its EM test proves nothing — "
                + boarding.getMessage(), boarding.isSuccess());

        Game.ActionResult raid = game.performHitAndRun(ship, other, oneRaidTarget());
        assertTrue("fixture: a raid must be possible too — " + raid.getMessage(),
                raid.isSuccess());

        // Crew transport is NOT checked here: a ship has only so many transporters per impulse
        // and the three above have spent them. It proves itself inside its own test instead.
    }

    // ---------------------------------------------------------------- C10.514 mines

    /** C10.514: a unit using EM cannot lay a mine, by transporter. */
    @Test
    public void emBarsLayingATBombByTransporter() {
        startEm();

        Game.ActionResult result = game.placeTBomb(ship, new Location(10, 12), true, 1);

        assertFalse(result.isSuccess());
        assertTrue("the refusal must name the rule so a player can look it up: "
                + result.getMessage(), result.getMessage().contains("C10.514"));
        assertTrue(result.getMessage().contains("Erratic Maneuvers"));
        assertTrue("and no mine may reach the map", game.getMines().isEmpty());
    }

    /**
     * C10.514 again, on the path a mine RACK will use.
     *
     * M9.0 lays a rack mine "in the hex occupied by the ship", which is this method, so the
     * guard is in place before the racks arrive rather than bolted on afterwards.
     */
    @Test
    public void emBarsDroppingAMineFromTheBay() {
        startEm();

        Game.ActionResult result = game.dropMine(ship, "TBOMB");

        assertFalse(result.isSuccess());
        assertTrue("the refusal must name C10.514: " + result.getMessage(),
                result.getMessage().contains("C10.514"));
        assertTrue(game.getMines().isEmpty());
    }

    // ---------------------------------------------------------------- C10.521 transporters

    /**
     * C10.521 bars TRANSPORTERS as a system, which is why the guard is not written against
     * mines: a ban on mine-laying alone would leave boarding parties, hit-and-run raids and
     * cargo retrieval using transporters a ship is not allowed to operate.
     */
    @Test
    public void emBarsBoardingParties() {
        startEm();

        Game.ActionResult result = game.performBoardingAction(ship, other, 2, 0);

        assertFalse(result.isSuccess());
        assertTrue("the refusal must name C10.521: " + result.getMessage(),
                result.getMessage().contains("C10.521"));
    }

    @Test
    public void emBarsHitAndRunRaids() {
        startEm();

        Game.ActionResult result = game.performHitAndRun(ship, other, oneRaidTarget());

        assertFalse(result.isSuccess());
        assertTrue("the refusal must name C10.521: " + result.getMessage(),
                result.getMessage().contains("C10.521"));
    }

    @Test
    public void emBarsTransportingCrew() {
        assertTrue("fixture: it must work first, or the refusal below is not EM's doing",
                game.transportCrew(ship, other, 1).isSuccess());
        startEm();

        Game.ActionResult result = game.transportCrew(ship, other, 1);

        assertFalse(result.isSuccess());
        assertTrue("the refusal must name C10.521: " + result.getMessage(),
                result.getMessage().contains("C10.521"));
    }

    /** One system to raid, so a raid is refused for EM rather than for having no orders. */
    private static java.util.List<com.sfb.properties.SystemTarget> oneRaidTarget() {
        return java.util.List.of(new com.sfb.properties.SystemTarget(
                com.sfb.properties.SystemTarget.Type.SENSORS,
                com.sfb.properties.SystemTarget.Type.SENSORS.name()));
    }

    // ---------------------------------------------------------------- C10.521 tractor beams

    @Test
    public void emBarsEstablishingATractor() {
        startEm();

        Game.ActionResult result = game.establishTractor(ship, "USS Hood", 1);

        assertFalse(result.isSuccess());
        assertTrue("the refusal must name C10.521: " + result.getMessage(),
                result.getMessage().contains("C10.521"));
    }

    // Rotation is also barred (G7.42 rotation is operating the beam), but it is only legal in
    // the INITIAL_ACTIVITY phase (G7.7), which needs a turn-start fixture with links already in
    // place. That test lives with the other rotation tests, in TractorTest.

    // ---------------------------------------------------------------- C10.415 fire control

    /**
     * C10.415: EM does not shorten passive fire control, it REMOVES it.
     *
     * "If the fire control is not active, it would be considered inactive (D6.614) rather than
     * passive", and D19.25 adds that such a ship "cannot fire or launch any weapons while using
     * PFC". So the difference is between firing out to range 5 (D19.23) and not firing at all —
     * which is why the fixture fires successfully FIRST. A refusal from a ship that could not
     * have fired anyway is no evidence.
     */
    @Test
    public void emLeavesFireControlInactiveSoTheShipCannotFireAtAll() {
        ship.setActiveFireControl(false);
        assertFalse("fixture: no active fire control", ship.isActiveFireControl());
        assertTrue("fixture: so it is PASSIVE, which D19.23 lets fire inside range 5",
                ship.isPassiveFireControl());
        while (game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE)
            game.advancePhase();

        java.util.List<com.sfb.weapons.Weapon> guns = new java.util.ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.PhaserWeapon && w.isFunctional()) {
                guns.add(w);
                break;
            }
        assertFalse("fixture: the ship needs a phaser to fire", guns.isEmpty());

        String before = game.fireWeapons(ship, other, guns, 1, 1, 1);
        assertFalse("fixture: it must be able to fire at range 1 BEFORE EM, or the refusal below"
                + " proves nothing — " + before, before.contains("cannot fire"));

        startEm();

        assertFalse("EM makes it inactive, not passive", ship.isPassiveFireControl());
        String after = game.fireWeapons(ship, other, guns, 1, 1, 1);
        assertTrue("an inactive ship cannot fire at any range, and the refusal must name the"
                + " rule: " + after, after.contains("C10.415"));
    }

    /** With ACTIVE fire control, EM costs the ship nothing here — C10.415 is about PFC only. */
    @Test
    public void emDoesNotTouchActiveFireControl() {
        ship.setActiveFireControl(true);
        startEm();

        assertFalse("active is not passive, so the predicate says no",
                ship.isPassiveFireControl());
        assertTrue("but the ship still HAS fire control", ship.isActiveFireControl());
    }

    // ---------------------------------------------------------------- the carve-outs

    /**
     * C10.525: "A ship can use negative tractor energy (G7.35) while performing EM, and this use
     * is not degraded in any way."
     *
     * Resisting a tractor is not operating one. Pinned so that a later tidy-up folding this into
     * the C10.521 ban fails here rather than silently removing a defence the rule grants.
     */
    @Test
    public void emDoesNotBarNegativeTractorEnergy() {
        Game.ActionResult result = game.submitNegativeTractorBid(ship, 2);
        String withoutEm = result.getMessage();

        startEm();
        Game.ActionResult underEm = game.submitNegativeTractorBid(ship, 2);

        assertFalse("C10.525 is explicit — EM must not be the reason this is refused: "
                + underEm.getMessage(),
                underEm.getMessage() != null
                        && underEm.getMessage().contains("Erratic Maneuvers"));
        assertEquals("and the outcome must be exactly what it was without EM, since C10.525 says"
                + " the use is not degraded in any way", withoutEm, underEm.getMessage());
    }

    /**
     * Nothing in C10.5 stops a ship LETTING GO of a tractor it already holds, and a ship that
     * could not release would be trapped by its own manoeuvres.
     */
    @Test
    public void emDoesNotBarReleasingATractor() {
        // G7.42: establishing opens an auction and the link is not live until the defender
        // answers. A nil bid settles it, which is what makes this a real link to let go of.
        assertTrue("fixture: the bid should be accepted",
                game.establishTractor(ship, "USS Hood", 1).isSuccess());
        game.submitNegativeTractorBid(other, 0);
        assertTrue("fixture: the link must actually exist before releasing it means anything",
                ship.getTractors().getTractoredUnits().contains(other));
        startEm();

        Game.ActionResult result = game.releaseTractor(ship, "USS Hood");

        assertTrue("a ship must always be able to let go: " + result.getMessage(),
                result.isSuccess());
    }

    // ---------------------------------------------------------------- not under EM

    /** And none of this touches a ship that is not manoeuvring. */
    @Test
    public void aShipNotUsingEmIsUnaffected() {
        assertFalse("fixture", ship.isUsingEm());

        assertTrue(game.placeTBomb(ship, new Location(10, 12), true, 1).isSuccess());
        assertEquals(1, game.getMines().size());
        assertTrue(game.establishTractor(ship, "USS Hood", 1).isSuccess());
    }
}
