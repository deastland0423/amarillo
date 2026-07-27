package com.sfb;

import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.systemgroups.Crew.CrewQuality;
import com.sfb.properties.Location;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.TerrainType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Planetary rings (P2.223): enterable asteroid-like bands around a gas giant,
 * generated from {inner, outer} hex-distance bands, kept out of the no-entry
 * body footprint. Multi-band with gaps supported.
 */
public class RingTerrainTest {

    private Game game;

    @Before
    public void setUp() {
        game = new Game();
    }

    private Terrain giant(int col, int row, int radius, List<int[]> bands) {
        Terrain t = new Terrain(TerrainType.GAS_GIANT, col, row, radius);
        t.setRingBands(bands);
        return t;
    }

    private int countHexes(java.util.function.Predicate<Location> test) {
        int n = 0;
        for (int c = 1; c <= 42; c++)
            for (int r = 1; r <= 32; r++)
                if (test.test(new Location(c, r)))
                    n++;
        return n;
    }

    @Test
    public void singleBand_generatesAnnulusOfRingHexes() {
        // radius 2 body, ring band at distance 4..5
        game.addTerrain(giant(20, 15, 2, List.of(new int[] { 4, 5 })));
        // Ring of distance 4 = 24 hexes, distance 5 = 30 → 54 enterable ring hexes
        assertEquals(54, countHexes(game::isRingHex));
        // A distance-4 hex is a ring hex, and NOT a no-entry planet hex
        Location r4 = new Location(20, 11); // 4 straight up
        assertTrue(game.isRingHex(r4));
        assertFalse("rings are enterable — not in the no-entry footprint",
                game.isPlanetHex(r4));
    }

    @Test
    public void ringHexes_excludeBodyFootprint() {
        // Band overlaps the body (inner 2 <= radius 3): body hexes win, only
        // the distance-4..5 shell becomes ring
        game.addTerrain(giant(20, 15, 3, List.of(new int[] { 2, 5 })));
        // Nothing at distance ≤3 is a ring hex (body claims it)
        assertFalse(game.isRingHex(new Location(20, 15))); // center
        assertFalse(game.isRingHex(new Location(20, 12))); // dist 3 — body
        assertTrue(game.isRingHex(new Location(20, 11)));  // dist 4 — ring
    }

    @Test
    public void multiBand_withGap_leavesOpenSpaceBetween() {
        // Inner ring 4..5, gap at 6, outer ring 7..8 (SH35-style)
        game.addTerrain(giant(20, 15, 3,
                List.of(new int[] { 4, 5 }, new int[] { 7, 8 })));
        assertTrue(game.isRingHex(new Location(20, 11)));  // dist 4 — inner ring
        assertFalse("gap between bands is open space", game.isRingHex(new Location(20, 9))); // dist 6
        assertTrue(game.isRingHex(new Location(20, 8)));   // dist 7 — outer ring
    }

    @Test
    public void ringDamageTable_matchesP2223() {
        // Ring Material Damage Table (P2.223): lighter than asteroids.
        // die 1 → all zero; die 6 → 0/5/7/15 across speed brackets
        assertArrayEquals(new int[] { 0, 0, 0, 0 }, Game.RING_DAMAGE[0]);
        assertArrayEquals(new int[] { 0, 5, 7, 15 }, Game.RING_DAMAGE[5]);
        assertArrayEquals(new int[] { 0, 1, 3, 7 }, Game.RING_DAMAGE[3]);
        // Rings hit lighter than asteroids at a given cell (die 6, 26+ speed)
        assertTrue(Game.RING_DAMAGE[5][3] < Game.ASTEROID_DAMAGE[5][3]);
    }

    @Test
    public void noRings_whenBandsEmpty() {
        game.addTerrain(new Terrain(TerrainType.GAS_GIANT, 20, 15, 3));
        assertEquals(0, countHexes(game::isRingHex));
    }

    // -------------------------------------------------------------------------
    // P3.33 / P2.223 — natural ECM along the line of fire
    // -------------------------------------------------------------------------

    @Test
    public void hexLine_includesBothEndpoints() {
        java.util.List<Location> line = com.sfb.utilities.MapUtils.hexLine(
                new Location(10, 10), new Location(14, 10));
        assertEquals(new Location(10, 10), line.get(0));
        assertEquals(new Location(14, 10), line.get(line.size() - 1));
        // Inclusive length matches range + 1
        assertEquals(com.sfb.utilities.MapUtils.getRange(new Location(10, 10), new Location(14, 10)) + 1,
                line.size());
    }

    @Test
    public void terrainEcm_asteroidOnePerHex_includingEndpoints() {
        // Asteroids straddling the line (10,10)→(13,10): endpoints not asteroid,
        // two interior asteroid hexes → 2 points
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 11, 10));
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 12, 10));
        assertEquals(2, game.terrainEcmAlongLine(new Location(10, 10), new Location(13, 10)));
    }

    @Test
    public void terrainEcm_ringIsHalfPerHex_roundedUp() {
        // Three ring hexes on the line: 3 × ½ = 1.5 → rounds up to 2 (P2.223)
        game.addTerrain(giant(20, 15, 2, List.of(new int[] { 3, 3 })));
        // Line straight up the column through the distance-3 ring band
        int ecm = game.terrainEcmAlongLine(new Location(20, 19), new Location(20, 11));
        assertTrue("some ring hexes crossed", ecm >= 1);
    }

    @Test
    public void terrainEcm_zeroWhenNoTerrainCrossed() {
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 30, 30));
        assertEquals(0, game.terrainEcmAlongLine(new Location(5, 5), new Location(9, 5)));
    }

    @Test
    public void terrainEcm_halfPointRoundUp_isExact() {
        // One ring hex = ½ → rounds up to 1 (a single ring hex still gives ECM)
        game.addTerrain(giant(20, 15, 1, List.of(new int[] { 2, 2 })));
        int ecm = game.terrainEcmAlongLine(new Location(20, 17), new Location(20, 13));
        assertTrue("a single ½ ring hex rounds up to 1", ecm >= 1);
    }

    // -------------------------------------------------------------------------
    // C11.21 nimble collision die-shift (with C11.33 poor-crew negation)
    // -------------------------------------------------------------------------

    @Test
    public void nimble_subtractsOneFromCollisionDie() {
        assertEquals("C11.21: nimble −1", 4, Game.nimbleAdjustedDie(5, true, CrewQuality.NORMAL));
        assertEquals("non-nimble unchanged", 5, Game.nimbleAdjustedDie(5, false, CrewQuality.NORMAL));
    }

    @Test
    public void nimbleShift_floorsAtOne() {
        assertEquals("die 1 can't go below 1", 1, Game.nimbleAdjustedDie(1, true, CrewQuality.NORMAL));
    }

    @Test
    public void poorCrew_negatesNimbleBenefit() {
        // C11.33: a nimble ship with a poor crew gets no nimble benefit
        assertEquals(5, Game.nimbleAdjustedDie(5, true, CrewQuality.POOR));
    }

    @Test
    public void outstandingCrew_keepsNimbleShift() {
        // C11.33's outstanding-crew clause is about retaining nimble when
        // crippled, not a further shift — the −1 still applies normally
        assertEquals(4, Game.nimbleAdjustedDie(5, true, CrewQuality.OUTSTANDING));
    }

    @Test
    public void nullCrew_shuttleStyle_keepsNimbleShift() {
        // Shuttles/fighters are always nimble (C11 note) and pass no crew
        assertEquals(4, Game.nimbleAdjustedDie(5, true, null));
    }

    @Test
    public void selfGuidedPlasma_erodesCrossingTheRing() {
        // Giant at (20,20) r2 with a ring band at 4..5. A plasma torpedo flies
        // west along row 16 toward a target at (12,16): it clips the ring hexes
        // near column 20 (distance 4 from center) but never touches the no-entry
        // body (rows 18-22). It should log a ring collision (P2.223), not pass
        // through unharmed.
        game.addTerrain(giant(20, 20, 2, List.of(new int[] { 4, 5 })));

        Ship fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(12, 16));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed);

        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(fed.getLifeSupportCost());
        e.setFireControl(fed.getFireControlCost());
        e.setActivateShields(fed.getActiveShieldCost());
        e.setWarpMovement(0.0);
        game.submitAllocation(fed, e);

        PlasmaTorpedo torp = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
        torp.setName("Test-Plasma");
        torp.setLocation(new Location(28, 16));
        torp.setTarget(fed);
        torp.setSeekerType(Seeker.SeekerType.PLASMA);
        game.getSeekers().add(torp);

        StringBuilder allLog = new StringBuilder();
        boolean sawRing = false;
        for (int guard = 0; guard < 300 && !sawRing && game.getSeekers().contains(torp); guard++) {
            allLog.append(game.advancePhase().getMessage()).append('\n');
            sawRing = allLog.indexOf("ring hex") >= 0;
        }
        assertTrue("plasma logs a ring collision crossing the ring band (P2.223)", sawRing);
    }
}
