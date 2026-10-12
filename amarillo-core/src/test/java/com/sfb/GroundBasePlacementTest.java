package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.utilities.MapUtils;

/**
 * Siting a ground base on a planet's face (R1.14, P2.62) — ground bases, slice 4.
 *
 * <p>The owner's model, and it is the whole design: "a base that is on the B side of a planet
 * should behave as if it were a ship pointing in direction B". Everything a ground base needs
 * follows from being in the right hex with the right facing, so this slice is placement and
 * nothing else.
 *
 * <p>What it leans on, all of it already built: arcs are computed against a unit's own facing, so
 * R1.14's {@code FH} weapons cover the hemisphere away from the planet without a rule of their own;
 * {@code losBlocked} is P2.322 and blocks the far side; and nothing moves at speed zero.
 */
public class GroundBasePlacementTest {

    private Game game;
    private Terrain planet;

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        game = new Game();
        planet = new Terrain(TerrainType.PLANET, 20, 20, 1);
        game.addTerrain(planet);
    }

    private Ship groundBase(String type) {
        ShipSpec spec = ShipLibrary.get("Neutral", type);
        assertNotNull("fixture: " + type + " should be in the library", spec);
        Ship base = ShipLibrary.createShip(spec);
        base.setName(type + "-1");
        return base;
    }

    /** Side N is direction (N-1)*4+1, the same mapping deployment headings use. */
    @Test
    public void theBaseFacesTheSideItStandsOn() {
        int[][] expected = { {1, 1}, {2, 5}, {3, 9}, {4, 13}, {5, 17}, {6, 21} };
        for (int[] pair : expected) {
            Ship base = groundBase("GBD1");
            Game.ActionResult r = game.placeGroundBase(base, planet, pair[0]);

            assertTrue(r.getMessage(), r.isSuccess());
            assertEquals("face " + pair[0] + " looks in direction " + pair[1],
                    pair[1], base.getFacing());
        }
    }

    /** A planet fills every hex within its radius, so the face hex is radius steps out. */
    @Test
    public void theBaseStandsOnThePlanetsSurface() {
        Ship base = groundBase("GBD1");
        game.placeGroundBase(base, planet, 2);

        Location expected = MapUtils.getAdjacentHex(planet.getLocation(), 5,
                game.getMapCols(), game.getMapRows());
        assertEquals("one step out for a radius-1 planet", expected, base.getLocation());
        assertTrue("and that hex is planetary surface", game.isPlanetSurfaceHex(base.getLocation()));
    }

    /** A radius-zero planet has only its own hex, so the base stands in it. */
    @Test
    public void aRadiusZeroPlanetPutsTheBaseInItsOwnHex() {
        Terrain moon = new Terrain(TerrainType.PLANET, 30, 30, 0);
        game.addTerrain(moon);
        Ship base = groundBase("GBD1");

        game.placeGroundBase(base, moon, 4);

        assertEquals(moon.getLocation(), base.getLocation());
    }

    /** It is in the game and it never moves — speed zero is all that takes. */
    @Test
    public void theBaseJoinsTheGameAndStaysPut() {
        Ship base = groundBase("GBDD");
        game.placeGroundBase(base, planet, 3);

        assertTrue("it is on the board", game.getShips().contains(base));
        assertEquals("at rest", 0, base.getSpeed());
        assertFalse("and never due to move", base.movesThisImpulse(1));
        assertFalse(base.movesThisImpulse(16));
        assertFalse(base.movesThisImpulse(32));
    }

    /**
     * The payoff: the planet hides the far side without anyone writing a rule for it. P2.322 says
     * surface must lie STRICTLY between, so the base's own hex does not block its own fire.
     */
    @Test
    public void thePlanetBlocksTheFarSideButNotTheNearOne() {
        Ship base = groundBase("GBD1");
        game.placeGroundBase(base, planet, 2);           // direction 5 from the centre

        Location outward = base.getLocation();
        for (int i = 0; i < 4; i++)
            outward = MapUtils.getAdjacentHex(outward, 5, game.getMapCols(), game.getMapRows());
        Location opposite = planet.getLocation();
        for (int i = 0; i < 5; i++)
            opposite = MapUtils.getAdjacentHex(opposite, 17, game.getMapCols(), game.getMapRows());

        assertFalse("it can see out past its own face",
                game.losBlocked(base.getLocation(), outward));
        assertTrue("but not through the planet to the far side",
                game.losBlocked(base.getLocation(), opposite));
    }

    @Test
    public void aShipCannotBeSitedAsAGroundBase() {
        ShipSpec spec = ShipLibrary.get("Klingon", "D6");
        Ship cruiser = ShipLibrary.createShip(spec);
        cruiser.setName("IKS Mobile");

        Game.ActionResult r = game.placeGroundBase(cruiser, planet, 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("not a base"));
    }

    @Test
    public void thereAreOnlySixFaces() {
        Game.ActionResult r = game.placeGroundBase(groundBase("GBD1"), planet, 7);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("six faces"));
    }
}
