package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * The terrain charts and a ship's place on them, for the player aid.
 *
 * <h2>Why the server answers this and the client does not</h2>
 * The CHARTS are printed data; which cell a ship reads is four rules deep — effective speed
 * (C2.451, so EM is already folded in), nimbleness (C11.21), a poor crew cancelling that
 * (C11.33), and fire control (D6.628). The frontend already carries 485 lines of hand-mirrored
 * weapon tables with a standing note to keep them in step, and two mine numbers that disagreed
 * with the rulebook turned up in a single playtest on 2026-11-07. One definition of a chart.
 */
public class TerrainChartAidTest {

    /** The charts are served as COPIES: a caller must not be able to edit the rules. */
    @Test
    public void theChartsAreHandedOutAsCopies() {
        int[][] first = Game.asteroidDamageChart();
        first[5][3] = 999;

        assertEquals("the chart must not be mutable through the accessor",
                30, Game.asteroidDamageChart()[5][3]);
        assertEquals("and the ring chart likewise", 15, Game.ringDamageChart()[5][3]);
    }

    /**
     * (P3.2) The slowest column is all zeros, which is the single most useful fact on the chart:
     * creeping through an asteroid field is FREE, and the cliff is at seven. That is now doubly
     * worth knowing, since a mine also triggers automatically at seven (M2.40).
     */
    @Test
    public void theSlowColumnIsFree() {
        for (int die = 0; die < 6; die++)
            assertEquals("die " + (die + 1) + " at speed 1-6", 0,
                    Game.asteroidDamageChart()[die][0]);
    }

    @Test
    public void speedPicksTheColumn() {
        Game game = gameWithShip();
        Ship hood = game.getShips().get(0);

        hood.setSpeed(6);
        assertEquals(0, game.terrainChartPosition(hood).speedColumn());
        hood.setSpeed(7);
        assertEquals("seven is the cliff", 1, game.terrainChartPosition(hood).speedColumn());
        hood.setSpeed(14);
        assertEquals(1, game.terrainChartPosition(hood).speedColumn());
        hood.setSpeed(15);
        assertEquals(2, game.terrainChartPosition(hood).speedColumn());
        hood.setSpeed(26);
        assertEquals(3, game.terrainChartPosition(hood).speedColumn());
        hood.setSpeed(31);
        assertEquals("and the top column does not run off the end",
                3, game.terrainChartPosition(hood).speedColumn());
    }

    /**
     * (D6.628) A ship on PASSIVE fire control is clumsier in a field, and the aid must say so —
     * it is the modifier a player is most likely to forget, because it is set for other reasons
     * entirely and then changes what the rocks do.
     */
    @Test
    public void passiveFireControlShowsAsAPenalty() {
        Game game = gameWithShip();
        Ship hood = game.getShips().get(0);
        hood.setSpeed(8);

        hood.setActiveFireControl(false);
        Game.TerrainChartPosition passive = game.terrainChartPosition(hood);
        assertEquals("+1 to the die is worse", 1, passive.dieShift());
        assertTrue("and it is named: " + passive.modifiers(),
                passive.modifiers().stream().anyMatch(m -> m.contains("D6.628")));

        hood.setActiveFireControl(true);
        Game.TerrainChartPosition active = game.terrainChartPosition(hood);
        assertEquals(0, active.dieShift());
        assertEquals(java.util.List.of("no modifiers"), active.modifiers());
    }

    /**
     * The shift is ASKED of {@code collisionDie} rather than re-derived, so the aid cannot
     * describe a roll the engine would not make. Checked against the real method across the
     * whole die range.
     */
    @Test
    public void theAidAgreesWithTheRollItDescribes() {
        Game game = gameWithShip();
        Ship hood = game.getShips().get(0);
        hood.setSpeed(8);
        hood.setActiveFireControl(false);

        int shift = game.terrainChartPosition(hood).dieShift();
        for (int raw = 1; raw <= 6; raw++) {
            int engine = Game.collisionDie(raw, hood.isNimble(), null, true);
            int aid = Math.max(1, Math.min(6, raw + shift));
            assertEquals("raw die " + raw, engine, aid);
        }
    }

    /** (C2.451) The column reads EFFECTIVE speed, so EM is already in it. */
    @Test
    public void theColumnReadsEffectiveSpeedNotPlotted() {
        Game game = gameWithShip();
        Ship hood = game.getShips().get(0);
        hood.setSpeed(2);

        assertEquals("plotted two is the slow column",
                0, game.terrainChartPosition(hood).speedColumn());
        assertEquals(2, game.terrainChartPosition(hood).effectiveSpeed());
    }

    /** No ship named, no position — the bare charts are still useful to read. */
    @Test
    public void withNoShipThereIsNoPosition() {
        assertNull(gameWithShip().terrainChartPosition(null));
    }

    /** The column headings match the chart's width, or the table renders ragged. */
    @Test
    public void theHeadingsMatchTheCharts() {
        assertEquals(Game.TERRAIN_SPEED_COLUMNS.length, Game.asteroidDamageChart()[0].length);
        assertEquals(Game.TERRAIN_SPEED_COLUMNS.length, Game.ringDamageChart()[0].length);
        assertEquals(6, Game.asteroidDamageChart().length);
        assertEquals(6, Game.ringDamageChart().length);
    }

    /** (P2.223) Rings are lighter than rocks at low speed, which is the point of two charts. */
    @Test
    public void ringsAreGentlerThanAsteroids() {
        int[][] rock = Game.asteroidDamageChart();
        int[][] ring = Game.ringDamageChart();
        for (int die = 0; die < 6; die++)
            for (int col = 0; col < rock[die].length; col++)
                assertTrue("ring damage must never exceed asteroid damage at die "
                                + (die + 1) + " column " + col,
                        ring[die][col] <= rock[die][col]);
    }

    private static Game gameWithShip() {
        Game game = new Game();
        Player fed = new Player();
        fed.setName("Captain Dan");
        fed.setTeamName("Federation");
        game.getPlayers().add(fed);

        Ship hood = new Ship();
        hood.init(FederationShips.getFedCa());
        hood.setName("USS Hood");
        hood.setLocation(new Location(10, 10));
        hood.setFacing(1);
        hood.setOwner(fed);
        hood.setActiveFireControl(true);
        game.getShips().add(hood);
        return game;
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> unused() { return null; }
}
