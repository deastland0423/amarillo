package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.EwBreakdown;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The natural ECM sources of D6.3143, and where each one attaches.
 * <p>
 * Asteroids (P3.33) and rings (P2.223) were already counted along the line of fire. Two
 * more join them, and they attach differently, which is the whole reason to be careful:
 * an atmosphere hex (P2.51) jams fire that CROSSES it, so it belongs on the line, while
 * ground clutter (P2.52) is two points for shooting at a planet's surface at all, which
 * is a property of the target and would be nonsense to count per hex.
 * <p>
 * Both land in {@link EwBreakdown#natural()}, which matters beyond bookkeeping: natural is
 * the one source a friendly unit cannot ignore (D6.3146), so a tractor reaching through
 * an atmosphere has to burn through it too.
 */
public class NaturalEcmSourcesTest {

    private Game game;
    private Ship fed;

    @Before
    public void setUp() {
        game = new Game();
        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(21, 30));
        fed.setFacing(1);
        fed.setActiveFireControl(true);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed);
    }

    /**
     * A gas giant big enough to have an atmosphere ring: radius 3 or more splits into a
     * solid interior and a pure-atmosphere outer ring (P2.222).
     */
    private Terrain bigGiantAt(int col, int row, int radius) {
        Terrain giant = new Terrain(TerrainType.GAS_GIANT, col, row, radius);
        game.addTerrain(giant);
        return giant;
    }

    // ---------------------------------------------------------------- P2.51 atmosphere

    @Test
    public void aGiantThisSizeActuallyHasAtmosphereHexes() {
        // Guards the tests below: if the footprint had no atmosphere ring they would pass
        // for the wrong reason, proving only that nothing was in the way.
        bigGiantAt(21, 16, 4);

        boolean any = false;
        for (int col = 15; col <= 27 && !any; col++)
            for (int row = 10; row <= 22 && !any; row++)
                if (game.isPlanetAtmosphereHex(new Location(col, row)))
                    any = true;

        assertTrue("a radius-4 giant must have a pure-atmosphere outer ring (P2.222)", any);
    }

    @Test
    public void fireCrossingAnAtmosphereHexPicksUpNaturalEcm() {
        bigGiantAt(21, 16, 4);

        // Find an atmosphere hex and shoot from just outside it, straight through.
        Location atmo = null;
        for (int row = 10; row <= 22 && atmo == null; row++)
            if (game.isPlanetAtmosphereHex(new Location(21, row)))
                atmo = new Location(21, row);
        assertNotNull("expected an atmosphere hex on that column", atmo);

        int ecm = game.terrainEcmAlongLine(new Location(21, atmo.getY() + 1), atmo);

        assertTrue("P2.51: an atmosphere hex is worth a point of natural ECM — got " + ecm,
                ecm >= 1);
    }

    @Test
    public void openSpaceIsStillFree() {
        assertEquals("nothing out there to jam with",
                0, game.terrainEcmAlongLine(new Location(5, 5), new Location(8, 5)));
    }

    // ---------------------------------------------------------------- P2.52 ground clutter

    @Test
    public void firingAtAPlanetSurfacePicksUpTwoPointsOfClutter() {
        Terrain planet = new Terrain(TerrainType.PLANET, 21, 16);
        game.addTerrain(planet);

        EwBreakdown ew = game.ewAgainst(fed, planet);

        assertEquals("P2.52: two points of ground clutter for shooting at the surface",
                2, ew.natural());
        assertEquals("and it is natural, not any other source", 2, ew.total());
    }

    @Test
    public void groundClutterIsNaturalSoAFriendlyUnitCannotIgnoreIt() {
        Terrain planet = new Terrain(TerrainType.PLANET, 21, 16);
        game.addTerrain(planet);

        // D6.3146 exempts generated, built-in and lent — never natural.
        assertEquals(2, game.ewAgainst(fed, planet).totalFriendly());
    }

    @Test
    public void aShipIsNotAPlanetAndPicksUpNoClutter() {
        Ship other = new Ship();
        other.init(FederationShips.getFedCa());
        other.setName("USS Hood");
        other.setLocation(new Location(21, 28));
        game.getShips().add(other);

        assertEquals("ground clutter belongs to planets, not to whatever is near one",
                0, game.ewAgainst(fed, other).natural());
    }

    /**
     * Ground clutter used to be a local variable inside bombardPlanet, so nothing else
     * firing at a planet saw it and it never reached the natural bucket. It is now on the
     * target, which is why the two points show up here without bombarding anything.
     */
    @Test
    public void clutterReachesTheBreakdownWithoutGoingThroughBombardment() {
        Terrain giant = bigGiantAt(21, 16, 4);

        EwBreakdown ew = game.ewAgainst(fed, giant);

        assertTrue("two points of clutter, plus whatever the line crosses — got "
                + ew.describe(), ew.natural() >= 2);
        assertTrue("and it is all natural", ew.describe().contains("natural"));
    }

    // ---------------------------------------------------------------- it is pairwise

    /**
     * The same target, two shooters, two different answers — which is why natural ECM can
     * never be a number on a ship's own panel. One fires across a belt of asteroids and one
     * has a clear line; P3.33 counts a point per asteroid hex between them, so the belt is
     * worth something to the first and nothing to the second.
     */
    @Test
    public void theSameTargetPresentsDifferentEcmToDifferentShooters() {
        Ship target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("Target");
        target.setLocation(new Location(21, 10));
        target.setFacing(1);
        game.getShips().add(target);

        // A belt between the target and one shooter only.
        for (int row = 11; row <= 14; row++)
            game.addTerrain(new Terrain(TerrainType.ASTEROID, 21, row));

        Ship throughTheRocks = new Ship();
        throughTheRocks.init(FederationShips.getFedCa());
        throughTheRocks.setName("Through the rocks");
        throughTheRocks.setLocation(new Location(21, 16));
        throughTheRocks.setFacing(1);
        throughTheRocks.setActiveFireControl(true);
        game.getShips().add(throughTheRocks);

        Ship clearLine = new Ship();
        clearLine.init(FederationShips.getFedCa());
        clearLine.setName("Clear line");
        clearLine.setLocation(new Location(28, 10));   // along the row, no belt between
        clearLine.setFacing(1);
        clearLine.setActiveFireControl(true);
        game.getShips().add(clearLine);

        int obstructed = game.ewAgainst(throughTheRocks, target).natural();
        int clear      = game.ewAgainst(clearLine, target).natural();

        assertTrue("four asteroid hexes on the line must count for something — got "
                + obstructed, obstructed >= 4);
        assertEquals("and nothing at all on a clear line", 0, clear);
        assertNotEquals("so there is no single figure to show on the target's own panel",
                obstructed, clear);
    }

    @Test
    public void theBreakdownNamesTheTerrainSoAPlayerCanSeeWhy() {
        Ship target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("Target");
        target.setLocation(new Location(21, 10));
        game.getShips().add(target);
        for (int row = 11; row <= 13; row++)
            game.addTerrain(new Terrain(TerrainType.ASTEROID, 21, row));

        String described = game.ewAgainst(fed, target).describe();

        assertTrue("the player should be told it is the terrain, not the target's own EW: "
                + described, described.contains("natural"));
    }
}
