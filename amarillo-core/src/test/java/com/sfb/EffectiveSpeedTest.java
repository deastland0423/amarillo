package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.Location;

/**
 * C2.45 EFFECTIVE SPEED — "the actual rate at which the unit is moving through space."
 * <p>
 * C2.43: practical speed + the cost of Erratic Maneuvers + terrain-induced movement. The terrain
 * term is black holes, nebulae and webs, none of which exist here. So in this engine effective
 * speed differs from practical speed exactly when a unit is manoeuvring erratically.
 * <p>
 * C2.451 says what it is for: "mines, asteroids, rings, dust, recovering shuttles and fighters,
 * destroying objects (e.g., shuttles) by towing them at high speed, collisions with small moons,
 * docking, and web damage." Mines and asteroids/rings are the two whose systems exist.
 * <p>
 * The costs differ by unit and so do the two rules that read them: C10.11 six for a ship, C10.12
 * three if nimble, C10.13 one for a shuttle — and for ASTEROIDS a shuttle takes the next speed
 * COLUMN instead of adding its one point (C10.45's note), which is harsher at every speed that is
 * not already on a column boundary.
 */
public class EffectiveSpeedTest {

    private Game game;
    private Ship ship;

    @Before
    public void setUp() throws Exception {
        com.sfb.objects.ShuttleCatalog.loadDefault("../data");
        game = new Game();

        Player fed = new Player();
        fed.setTeamName("Federation");

        ship = new Ship();
        ship.init(com.sfb.samples.FederationShips.getFedCa());
        ship.setName("USS Evader");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        ship.setOwner(fed);
        game.getShips().add(ship);

        game.startTurn();
        com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setErraticManuvers(ship.getPerformanceData().getErraticCost());
        game.submitAllocation(ship, e);
    }

    private void beginEm(Ship s) {
        assertTrue(game.announceErraticManeuvers(s, true).isSuccess());
        for (int i = 0; i < 8 && game.getCurrentPhase() != Game.ImpulsePhase.END_OF_IMPULSE; i++)
            game.advancePhase();
        game.advancePhase();
        assertTrue("fixture: EM should be in force", s.isUsingEm());
    }

    // ---------------------------------------------------------------- the number itself

    @Test
    public void withoutEmItIsSimplyThePracticalSpeed() {
        ship.setSpeed(12);
        assertFalse(ship.isUsingEm());
        assertEquals(12, ship.effectiveSpeed());
    }

    /** C10.11: six movement points for an ordinary ship. */
    @Test
    public void aShipUnderEmAddsItsSixPoints() {
        ship.setSpeed(12);
        beginEm(ship);

        assertEquals(6.0, ship.emMovementCost(), 0.001);
        assertEquals("twelve through space plus six of thrashing about", 18,
                ship.effectiveSpeed());
    }

    /** C10.13/C10.463: one point for a shuttle, and it is added back for effective speed. */
    @Test
    public void aFighterAddsItsOnePoint() {
        Fighter f = CataloguedFighter.of("stinger1");
        f.setName("Stinger-1");
        f.setSpeed(11);

        assertEquals(1.0, f.emMovementCost(), 0.001);
        assertEquals("not manoeuvring yet", 11, f.effectiveSpeed());

        f.announceEm(true, game.getAbsoluteImpulse());
        f.applyEmAnnouncement(game.getAbsoluteImpulse());
        assertTrue(f.isUsingEm());

        assertEquals("flying one hex slower, but signalling as though it were not",
                12, f.effectiveSpeed());
    }

    /** A drone cannot manoeuvre erratically at all (C10.17), so its speeds are the same. */
    @Test
    public void aSeekerHasNoEmCostAndNoDifference() {
        com.sfb.objects.Drone drone = new com.sfb.objects.Drone(com.sfb.objects.DroneType.TypeI);
        assertEquals(0.0, drone.emMovementCost(), 0.001);
        assertEquals(drone.getSpeed(), drone.effectiveSpeed());
    }

    /**
     * C10.24 again: held by a tractor, the unit is paying for EM but not manoeuvring — so its
     * effective speed drops back to its practical one while it is held.
     */
    @Test
    public void aTractoredShipLosesTheEmPartOfItsEffectiveSpeed() {
        ship.setSpeed(12);
        beginEm(ship);
        assertEquals(18, ship.effectiveSpeed());

        Ship tug = new Ship();
        tug.init(com.sfb.samples.KlingonShips.getD7());
        tug.setName("IKV Grip");
        tug.setLocation(new Location(10, 11));
        game.getShips().add(tug);
        ship.applyTractor(tug);

        assertEquals("not manoeuvring while held", 12, ship.effectiveSpeed());

        ship.releaseTractor();
        assertEquals("and manoeuvring again once released", 18, ship.effectiveSpeed());
    }

    // ---------------------------------------------------------------- mines (C10.46)

    /**
     * C10.461: "A non-nimble ship using EM will always trigger a mine ... if its speed is greater
     * than zero (because the six points of EM movement energy are added to speed for this
     * purpose)."
     * <p>
     * No rule of its own was needed: {@code SpaceMine.detectsUnit} already triggers
     * unconditionally at six or more, and six is exactly what EM adds.
     */
    @Test
    public void aShipUnderEmAlwaysTripsAMine() {
        com.sfb.objects.SpaceMine mine =
                com.sfb.objects.SpaceMine.createTBomb(ship, 0, true, false);
        mine.tryActivate(3, 5);   // M3.223: armed two impulses on, with the layer clear of it
        assertTrue("fixture: an unarmed mine detects nothing at all", mine.isActive());
        ship.setSpeed(1);

        // M3.22 detects on a roll GREATER than the speed, so creeping is the dangerous thing:
        // at a practical speed of one, only a roll of one gets the ship past.
        assertFalse("a one saves it at practical speed one",
                mine.detectsUnit(ship.effectiveSpeed(), 1));

        beginEm(ship);

        assertEquals(7, ship.effectiveSpeed());
        for (int roll = 1; roll <= 6; roll++)
            assertTrue("no die roll saves it: " + roll,
                    mine.detectsUnit(ship.effectiveSpeed(), roll));
    }

    /**
     * And the same thing through the engine, because the number is only worth having if the mine
     * sweep is the thing that reads it.
     * <p>
     * The arithmetic is what makes this deterministic. At a practical speed of five a mine detects
     * on a roll GREATER than five, so only a six trips it — one trial in six. Add EM's six points
     * and the effective speed is eleven, past the auto-detection threshold, so nothing saves the
     * ship. Every trial must detonate; reading practical speed instead would need a run of sixes.
     */
    @Test
    public void theMineSweepReadsEffectiveSpeed() {
        int trials = 8;
        for (int trial = 0; trial < trials; trial++)
            assertTrue("trial " + trial + ": a ship manoeuvring erratically always trips a mine"
                    + " (C10.461)", oneApproachDetonatesTheMine());
    }

    /**
     * One ship, under EM at speed five, creeping out of a hex with an armed mine in it. Returns
     * whether the mine went off. A fresh Game each time: the mine is consumed when it detonates.
     */
    private boolean oneApproachDetonatesTheMine() {
        Game g = new Game();

        Player fed = new Player();
        fed.setTeamName("Federation");

        Ship s = new Ship();
        s.init(com.sfb.samples.FederationShips.getFedCa());
        s.setName("USS Creeper");
        s.setLocation(new Location(10, 10));
        s.setFacing(1);
        s.setOwner(fed);
        g.getShips().add(s);

        g.startTurn();
        com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
        e.setLifeSupport(s.getLifeSupportCost());
        e.setFireControl(s.getFireControlCost());
        e.setErraticManuvers(s.getPerformanceData().getErraticCost());
        e.setWarpMovement(5);
        g.submitAllocation(s, e);
        s.setSpeed(5);

        assertTrue(g.announceErraticManeuvers(s, true).isSuccess());
        for (int i = 0; i < 8 && g.getCurrentPhase() != Game.ImpulsePhase.END_OF_IMPULSE; i++)
            g.advancePhase();
        g.advancePhase();
        assertTrue("fixture: EM in force", s.isEmEffective());

        // In its own hex, so no hex geometry is needed: moving out still leaves it within the
        // mine's range-1 detection zone, which is all M2.5 asks.
        com.sfb.objects.SpaceMine mine =
                com.sfb.objects.SpaceMine.createTBomb(s, 0, true, false);
        mine.setLocation(new Location(10, 10));
        mine.tryActivate(g.getAbsoluteImpulse() + 2, 9);
        assertTrue("fixture: the mine must be armed", mine.isActive());
        g.getMines().add(mine);

        // Walk impulses until it actually moves; speed five does not move every impulse.
        for (int i = 0; i < 64; i++) {
            if (g.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT)
                g.moveForward(s);
            g.advancePhase();
            if (g.getMines().isEmpty())
                return true;
            if (!new Location(10, 10).equals(s.getLocation())
                    && g.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT)
                return false;   // it has moved and the sweep has run without detonating
        }
        fail("fixture: the ship never moved");
        return false;
    }

    // ---------------------------------------------------------------- asteroids (C10.45)

    /** A ship's six points move it up P3.2's speed columns, which is what the brackets are for. */
    @Test
    public void aShipUnderEmTakesAsteroidDamageForItsEffectiveSpeed() {
        ship.setSpeed(4);
        beginEm(ship);

        assertEquals("four becomes ten, which is a column up", 10, ship.effectiveSpeed());
    }

    /**
     * C10.45's note: a SHUTTLE "uses the next higher column" rather than adding its point.
     * <p>
     * The asteroid table makes the difference stark. P3.2's first column — speeds one to six — is
     * ALL ZEROS, so a fighter at speed four is simply immune; the second column reaches ten. And
     * adding a shuttle's single point to four leaves it in that same first column, so an add would
     * change nothing at all. The column bump is the rule, and it is the difference between immune
     * and vulnerable.
     */
    @Test
    public void aFighterUnderEmTakesTheNextAsteroidColumn() throws Exception {
        game.addTerrain(new com.sfb.objects.Terrain(
                com.sfb.properties.TerrainType.ASTEROID, 10, 9));

        Fighter f = CataloguedFighter.of("stinger1");
        f.setName("Stinger-1");
        f.setLocation(new Location(10, 9));
        f.setSpeed(4);
        game.getActiveShuttles().add(f);

        assertEquals("not manoeuvring yet", 4, f.effectiveSpeed());

        // Not manoeuvring: the first column is all zeros, so it cannot be hurt at this speed.
        int worstWithoutEm = 0;
        for (int i = 0; i < 200; i++) {
            f.setCurrentHull(f.getHull());
            int before = f.getCurrentHull();
            game.applyTerrainCollision(f);
            worstWithoutEm = Math.max(worstWithoutEm, before - f.getCurrentHull());
        }
        assertEquals("P3.2's first column is all zeros", 0, worstWithoutEm);

        // Manoeuvring: read on the next column up, which reaches ten.
        f.announceEm(true, game.getAbsoluteImpulse());
        f.applyEmAnnouncement(game.getAbsoluteImpulse());
        assertTrue(f.isEmEffective());
        assertEquals("its one point, which on its own would not leave the first column",
                5, f.effectiveSpeed());

        int worstWithEm = 0;
        for (int i = 0; i < 200; i++) {
            f.setCurrentHull(f.getHull());
            int before = f.getCurrentHull();
            game.applyTerrainCollision(f);
            worstWithEm = Math.max(worstWithEm, before - f.getCurrentHull());
        }
        assertTrue("manoeuvring in an asteroid field must now be able to hurt it, got "
                + worstWithEm, worstWithEm > 0);
    }
}
