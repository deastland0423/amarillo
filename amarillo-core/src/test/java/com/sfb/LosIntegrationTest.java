package com.sfb;

import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.TerrainType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Planet LOS integration (P2.32x): no lock-on through a planet at turn start,
 * fire refused through surface, movement-boundary sweep losing and regaining
 * lock-ons (with the same-step passing exemption), and self-guided seekers
 * acquiring the planet when their target hides behind it (P2.33).
 *
 * Geometry: (10,10)→(14,10) runs through the CENTER of (12,10) — blocked;
 * (10,8)→(14,10) passes clear above it; (11,10)→(13,10) rides exactly along
 * (12,10)'s edge — legal (P2.321). Note (10,9)→(14,10) is NOT a graze: it
 * crosses the edge point (36,20) and dips into the interior — blocked.
 */
public class LosIntegrationTest {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();
        fed = buildShip(FederationShips.getFedCa(), "USS Enterprise", 10, 8);
        klingon = buildShip(KlingonShips.getD7(), "IKV Saber", 14, 10);
        game.getShips().add(fed);
        game.getShips().add(klingon);
    }

    private Ship buildShip(java.util.Map<String, Object> spec, String name, int col, int row) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(col, row));
        s.setFacing(1);
        s.setSpeedPreviousTurn(31);
        s.setSpeedTwoTurnsAgo(31);
        return s;
    }

    private void startWithAllocations() {
        game.startTurn();
        for (Ship s : new Ship[] { fed, klingon }) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            e.setWarpMovement(4.0);
            game.submitAllocation(s, e);
        }
    }

    private void advanceToNextMovement() {
        game.advancePhase(); // leave current MOVEMENT
        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.MOVEMENT, game.getCurrentPhase());
    }

    private static List<Weapon> singlePhaser() {
        Phaser1 ph = new Phaser1();
        ph.setArcs(ArcUtils.FH);
        ph.setDesignator("1");
        return java.util.Collections.singletonList(ph);
    }

    // -------------------------------------------------------------------------
    // Turn start and fire
    // -------------------------------------------------------------------------

    @Test
    public void turnStart_noLockOnThroughPlanet() {
        fed.setLocation(new Location(10, 10)); // blocked line through (12,10)
        game.addTerrain(new Terrain(TerrainType.PLANET, 12, 10));
        startWithAllocations();

        assertFalse("sensor 6 is automatic — only the planet explains no lock",
                fed.hasLockOn(klingon));
        assertFalse(klingon.hasLockOn(fed));
    }

    @Test
    public void turnStart_edgeGrazeStillLocks() {
        // (11,10)→(13,10) rides exactly along (12,10)'s edge — legal (P2.321)
        fed.setLocation(new Location(11, 10));
        klingon.setLocation(new Location(13, 10));
        game.addTerrain(new Terrain(TerrainType.PLANET, 12, 10));
        startWithAllocations();

        assertTrue(fed.hasLockOn(klingon));
        assertTrue(klingon.hasLockOn(fed));
    }

    @Test
    public void directFire_refusedThroughPlanet() {
        fed.setLocation(new Location(10, 10));
        game.addTerrain(new Terrain(TerrainType.PLANET, 12, 10));
        startWithAllocations();
        for (int guard = 0; guard < 10
                && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; guard++)
            game.advancePhase();

        String result = game.fireWeapons(fed, klingon, singlePhaser(), 4, 4, 1);
        assertTrue(result, result.contains("P2.321"));
        assertTrue("no volley queued through a planet", game.getPendingVolleys().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Movement-boundary sweep
    // -------------------------------------------------------------------------

    @Test
    public void movingBehindPlanet_losesLockOnAtPhaseBoundary() {
        game.addTerrain(new Terrain(TerrainType.PLANET, 12, 10));
        startWithAllocations();
        assertTrue(fed.hasLockOn(klingon)); // clear line — locked at turn start

        fed.setLocation(new Location(10, 10)); // now blocked through center
        Game.ActionResult r = game.advancePhase(); // end of movement — sweep runs

        assertFalse("lock lost when the planet comes between (P2.322)", fed.hasLockOn(klingon));
        assertFalse(klingon.hasLockOn(fed));
        assertTrue(r.getMessage(), r.getMessage().contains("P2.322"));
    }

    @Test
    public void clearingThePlanet_reacquiresNextBoundary() {
        game.addTerrain(new Terrain(TerrainType.PLANET, 12, 10));
        startWithAllocations();
        fed.setLocation(new Location(10, 10));
        game.advancePhase(); // lock lost
        assertFalse(fed.hasLockOn(klingon));

        advanceToNextMovement();
        fed.setLocation(new Location(10, 8)); // back to the clear line
        game.advancePhase(); // sweep: obstacle passed → re-acquire (sensor 6)

        assertTrue("re-acquired after clearing the planet (P2.322)", fed.hasLockOn(klingon));
        assertTrue(klingon.hasLockOn(fed));
    }

    @Test
    public void sameStepFlicker_neverLosesLock() {
        game.addTerrain(new Terrain(TerrainType.PLANET, 12, 10));
        startWithAllocations();
        assertTrue(fed.hasLockOn(klingon));

        // Passes behind the planet and out again within one movement phase:
        // LOS holds at both boundaries → no loss (P2.322 exemption)
        fed.setLocation(new Location(10, 10));
        fed.setLocation(new Location(10, 8));
        game.advancePhase();

        assertTrue(fed.hasLockOn(klingon));
    }

    // -------------------------------------------------------------------------
    // Self-guided seekers (P2.33)
    // -------------------------------------------------------------------------

    @Test
    public void selfGuidedPlasma_acquiresThePlanet() {
        // Target far enough that one impulse of plasma movement stays blocked
        klingon.setLocation(new Location(18, 10));
        game.addTerrain(new Terrain(TerrainType.PLANET, 14, 10));
        fed.setLocation(new Location(10, 14)); // clear of the action
        startWithAllocations();

        PlasmaTorpedo torp = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
        torp.setName("Doomed-Plasma");
        torp.setLocation(new Location(10, 10));
        torp.setTarget(klingon);
        torp.setSeekerType(Seeker.SeekerType.PLASMA);
        game.getSeekers().add(torp);

        Game.ActionResult r = game.advancePhase(); // movement sweep

        assertFalse("torpedo removed — it struck the planet (P2.33)",
                game.getSeekers().contains(torp));
        assertTrue(r.getMessage(), r.getMessage().contains("P2.33"));
    }
}
