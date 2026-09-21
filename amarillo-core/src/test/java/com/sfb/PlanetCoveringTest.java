package com.sfb;

import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Which planet covers a given hex (P2.222 footprints).
 * <p>
 * Needed because firing at a planet means clicking it, and a gas giant is several hexes
 * across — a player aims at whatever part is under the cursor, which is rarely the middle.
 * The existing lookup matched the CENTRE hex only, so every other hex of a giant was, as
 * far as the code was concerned, empty space.
 */
public class PlanetCoveringTest {

    private Game game;

    @Before
    public void setUp() {
        game = new Game();
    }

    private Terrain planet(int col, int row, int radius, TerrainType type) {
        Terrain t = new Terrain(type, col, row, radius);
        game.addTerrain(t);
        return t;
    }

    @Test
    public void aSingleHexPlanetIsFoundAtItsOwnHex() {
        Terrain p = planet(10, 10, 0, TerrainType.PLANET);

        assertSame(p, game.planetCovering(new Location(10, 10)));
        assertNull("and nowhere else", game.planetCovering(new Location(11, 10)));
    }

    /** The case that matters: a giant is aimed at from anywhere on its body. */
    @Test
    public void aGasGiantIsFoundFromAnyHexOfItsBody() {
        Terrain giant = planet(20, 20, 2, TerrainType.GAS_GIANT);

        assertSame("centre", giant, game.planetCovering(new Location(20, 20)));
        assertSame("one out", giant, game.planetCovering(new Location(21, 20)));
        assertSame("at the rim", giant, game.planetCovering(new Location(22, 20)));
        assertNull("but not beyond it", game.planetCovering(new Location(23, 20)));
    }

    @Test
    public void emptySpaceCoversNothing() {
        planet(10, 10, 1, TerrainType.PLANET);

        assertNull(game.planetCovering(new Location(30, 30)));
        assertNull("and a null hex is not an error", game.planetCovering(null));
    }

    /**
     * An asteroid is not a planet, however much it is terrain — firing into one is a
     * different rule (P3.25 clearing a path, not P2.311 bombardment), and the action picks
     * between them on this answer.
     */
    @Test
    public void anAsteroidHexIsNotAPlanet() {
        planet(15, 15, 0, TerrainType.ASTEROID);

        assertNull(game.planetCovering(new Location(15, 15)));
    }
}
