package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Crew;
import com.sfb.systemgroups.Energy;
import com.sfb.utilities.MapUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * P3.2 / P2.223 terrain collision for shuttles and fighters.
 * <p>
 * Ships have rolled for asteroid and ring collisions since the terrain work landed, but
 * shuttles and fighters never did: collision was wired per unit family by hand and the
 * helper was typed to {@code Ship}, so the compiler could not object to the families that
 * had no call at all. Every way a shuttle enters a hex is covered here - forward, a turn
 * (which changes facing and then moves a hex), and a sideslip.
 * <p>
 * Damage is rolled, so the single-run tests assert that the check HAPPENED (the log line
 * is the observable) while {@link #fighterCrossingAField_actuallyLosesHull()} asserts that
 * damage reaches the hull. Mind the speed brackets: below speed 7 every entry on the
 * asteroid table is zero (P3.2), which is why the damage test flies a Stinger at 12
 * rather than an admin shuttle capped at 6.
 */
public class ShuttleTerrainCollisionTest {

    private Game game;
    private Player fedPlayer;

    @Before
    public void setUp() {
        fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");
        game = freshGame();
    }

    /** A game with one parked ship, already allocated so the impulse engine runs. */
    private Game freshGame() {
        Game g = new Game();
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName("USS Enterprise");
        s.setLocation(new Location(20, 20)); // parked well away from the field
        s.setFacing(1);
        s.setOwner(fedPlayer);
        s.setSpeedPreviousTurn(31);
        s.setSpeedTwoTurnsAgo(31);
        g.getShips().add(s);

        g.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(s.getLifeSupportCost());
        e.setFireControl(s.getFireControlCost());
        e.setActivateShields(s.getActiveShieldCost());
        e.setWarpMovement(0.0); // the ship stays put so it never blocks a shuttle impulse
        g.submitAllocation(s, e);
        return g;
    }

    private Stinger1 fighterIn(Game g, int col, int row, int speed) {
        Stinger1 f = new Stinger1();
        f.setName("Alpha 1");
        f.setOwner(fedPlayer);
        f.setLocation(new Location(col, row));
        f.setFacing(1);
        f.setSpeed(speed);
        g.getActiveShuttles().add(f);
        return f;
    }

    /** Advance to a MOVEMENT phase on an impulse this shuttle is actually allowed to move. */
    private void advanceUntilCanMove(Game g, Shuttle s) {
        for (int guard = 0; guard < 600; guard++) {
            if (g.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT
                    && g.canMoveShuttleThisImpulse(s))
                return;
            g.advancePhase();
        }
        fail(s.getName() + " never got a movement impulse");
    }

    /** Asteroids in every hex around {@code loc}, so any maneuver lands in one. */
    private void ringAsteroidsAround(Game g, Location loc) {
        for (int dir : new int[] { 1, 5, 9, 13, 17, 21 }) {
            Location n = MapUtils.getAdjacentHex(loc, dir, g.getMapCols(), g.getMapRows());
            if (n != null)
                g.addTerrain(new Terrain(TerrainType.ASTEROID, n.getX(), n.getY()));
        }
    }

    // ---------------------------------------------------------------- the paths

    @Test
    public void shuttleMovingForward_intoAsteroidHex_rollsCollision() {
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));
        Stinger1 f = fighterIn(game, 10, 10, 12);
        advanceUntilCanMove(game, f);

        Game.ActionResult r = game.moveShuttleForward(f);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("A shuttle entering an asteroid hex must roll for collision (P3.2): "
                + r.getMessage(), r.getMessage().contains("enters asteroid hex"));
    }

    @Test
    public void shuttleTurning_intoAsteroidHex_rollsCollision() {
        Stinger1 f = fighterIn(game, 10, 20, 12);
        // Turn mode at speed 12 is 2 hexes, so it must fly two before it may turn.
        for (int i = 0; i < 2; i++) {
            advanceUntilCanMove(game, f);
            assertTrue(game.moveShuttleForward(f).isSuccess());
        }
        Location before = f.getLocation();
        ringAsteroidsAround(game, before);
        advanceUntilCanMove(game, f);

        Game.ActionResult r = game.turnShuttleLeft(f);

        assertTrue("the turn should have been allowed: " + r.getMessage(), r.isSuccess());
        assertNotEquals("a turn moves the shuttle a hex", before, f.getLocation());
        assertTrue("A turn enters a new hex, so it must roll for collision (P3.2): "
                + r.getMessage(), r.getMessage().contains("enters asteroid hex"));
    }

    @Test
    public void shuttleSideslipping_intoAsteroidHex_rollsCollision() {
        Stinger1 f = fighterIn(game, 10, 20, 12);
        advanceUntilCanMove(game, f);
        assertTrue(game.moveShuttleForward(f).isSuccess()); // a slip needs a move first
        Location before = f.getLocation();
        ringAsteroidsAround(game, before);
        advanceUntilCanMove(game, f);

        Game.ActionResult r = game.sideslipShuttleLeft(f);

        assertTrue("the sideslip should have been allowed: " + r.getMessage(), r.isSuccess());
        assertNotEquals("a sideslip moves the shuttle a hex", before, f.getLocation());
        assertTrue("A sideslip enters a new hex, so it must roll for collision (P3.2): "
                + r.getMessage(), r.getMessage().contains("enters asteroid hex"));
    }

    // ---------------------------------------------------------------- the damage

    /**
     * A log line alone would be satisfied by a roll whose damage went nowhere. At speed 12
     * the asteroid table can do 2, 6 or 10 points, so across many crossings some must
     * actually reach the hull.
     */
    @Test
    public void fighterCrossingAField_actuallyLosesHull() {
        boolean sawRealDamage = false;

        for (int trial = 0; trial < 60 && !sawRealDamage; trial++) {
            Game g = freshGame();
            g.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));
            Stinger1 f = fighterIn(g, 10, 10, 12);
            advanceUntilCanMove(g, f);

            int hullBefore = f.getCurrentHull();
            g.moveShuttleForward(f);
            if (f.getCurrentHull() < hullBefore)
                sawRealDamage = true;
        }

        assertTrue("Across 60 crossings at speed 12 a fighter must sometimes take real hull "
                + "damage - a collision that only writes a log line is not implemented",
                sawRealDamage);
    }

    /**
     * P3.2 brackets damage by speed, and below speed 7 every entry on the asteroid table
     * is zero. An admin shuttle is capped at 6, so it can cross a field all day. Pinned
     * here so the harmless bracket reads as the rule rather than as missing wiring.
     */
    @Test
    public void slowShuttle_rollsButTakesNoDamage() {
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));
        AdminShuttle s = new AdminShuttle();
        s.setName("Galileo");
        s.setOwner(fedPlayer);
        s.setLocation(new Location(10, 10));
        s.setFacing(1);
        s.setSpeed(6);
        game.getActiveShuttles().add(s);
        advanceUntilCanMove(game, s);

        int hullBefore = s.getCurrentHull();
        Game.ActionResult r = game.moveShuttleForward(s);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("the roll still happens: " + r.getMessage(),
                r.getMessage().contains("enters asteroid hex"));
        assertTrue("speed 6 is the harmless bracket (P3.2): " + r.getMessage(),
                r.getMessage().contains("no damage"));
        assertEquals("no hull lost below speed 7", hullBefore, s.getCurrentHull());
    }

    // ------------------------------------------------- the exceptions to "speed 6 is safe"

    /**
     * G21.142: "admin shuttle pilots are always treated as good" - so the crew quality of
     * the ship that launched a shuttle must make no difference to its collision roll. The
     * crew-quality rules are written about ships; a shuttle does not inherit poor OR
     * outstanding from its mother.
     * <p>
     * Measured the same way as any other rolled mechanic here, but asserting an
     * equivalence rather than a gap. It still catches the inheritance being reintroduced:
     * that would cost the shuttle its C11.21 die-shift under a poor crew and open a gap of
     * about 250, far outside the band below. At speed 20 the asteroid column is
     * {0,0,3,6,10,15}, so a nimble shuttle escapes damage on a raw 1-3 and one that had
     * lost the shift only on a raw 1-2 - 750 against 500 over {@value #ROLLS} rolls.
     */
    @Test
    public void shuttlePilotsAreAlwaysGood_regardlessOfTheMotherShipsCrew() {
        int fromNormalCrewedShip = unscathedTows(Crew.CrewQuality.NORMAL);
        int fromPoorCrewedShip = unscathedTows(Crew.CrewQuality.POOR);

        assertTrue("G21.142: an admin shuttle's pilot is always good, so its mother ship's "
                        + "crew must not change the roll - but over " + ROLLS + " tows a "
                        + "shuttle off a normal-crewed ship escaped damage "
                        + fromNormalCrewedShip + " times against " + fromPoorCrewedShip
                        + " off a poor-crewed one",
                Math.abs(fromNormalCrewedShip - fromPoorCrewedShip) < 100);
    }

    private static final int ROLLS = 1500;

    /**
     * How many of {@value #ROLLS} tows through an asteroid hex an admin shuttle launched by
     * a ship of this crew quality comes through undamaged. Towed at speed 20, because under
     * its own power it is capped at 6 and would never take damage at all (P3.2), and a
     * fresh shuttle per roll so no carried hull damage or J1.33 crippling leaks between
     * samples.
     */
    private int unscathedTows(Crew.CrewQuality quality) {
        Game g = freshGame();
        g.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));
        Ship mother = g.getShips().get(0);
        mother.getCrew().setCrewQuality(quality);

        int unscathed = 0;
        for (int i = 0; i < ROLLS; i++) {
            AdminShuttle s = new AdminShuttle();
            s.setName("Galileo " + i);
            s.setOwner(fedPlayer);
            s.setLocation(new Location(10, 9)); // where a tow would have left it
            s.setFacing(1);
            s.setSpeed(6);
            s.setParentShipName(mother.getName()); // launched by that ship
            g.getActiveShuttles().add(s);

            if (g.applyTerrainCollision(s, 20).contains("no damage"))
                unscathed++;
        }
        return unscathed;
    }

    /**
     * The other exception: an admin shuttle is capped at speed 6 and so is safe under its
     * OWN power, but a tractor beam can haul it through a field at the towing ship's speed.
     * P3.2 brackets by the speed the hex is entered at, which J1.6223 states outright for a
     * recovery tow - so being towed fast is not the same as being slow.
     */
    @Test
    public void slowShuttleTowedFast_isNoLongerInTheHarmlessBracket() {
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 9));
        AdminShuttle s = new AdminShuttle();
        s.setName("Galileo");
        s.setOwner(fedPlayer);
        s.setLocation(new Location(10, 9)); // already in the rocks, as a tow would leave it
        s.setFacing(1);
        s.setSpeed(6); // its own maximum
        game.getActiveShuttles().add(s);

        // Rolled as though entering at speed 20, the way a tow would deliver it.
        String line = game.applyTerrainCollision(s, 20);

        assertTrue("the roll happened: " + line, line.contains("enters asteroid hex"));
        assertTrue("it is bracketed at the TOW's speed, not its own 6: " + line,
                line.contains("(speed 20"));
    }
}
