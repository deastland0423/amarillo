package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.properties.LandingPhase;
import com.sfb.properties.Location;
import com.sfb.properties.PersonnelType;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Cargo gate #1 (SH50.46): a shuttle landed on a planet side loads/unloads
 * personnel between that hex side's surface holding and its own hold, bounded
 * by hold capacity (J2.211).
 */
public class ShuttlePlanetCargoTest {

    private Game game;
    private Ship fed;
    private Terrain planet;
    private AdminShuttle shuttle;

    @Before
    public void setUp() {
        game = new Game();
        Player fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(20, 20));
        fed.setFacing(1);
        fed.setOwner(fedPlayer);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed);

        planet = new Terrain(TerrainType.PLANET, 10, 7);
        game.addTerrain(planet);
        planet.getSideManifest(4).addCrew(3); // three survey teams on side D

        // A shuttle already landed on side D of the planet
        shuttle = new AdminShuttle();
        shuttle.setName("Galileo");
        shuttle.setOwner(fedPlayer);
        shuttle.setLocation(new Location(10, 7));
        shuttle.setLandingPhase(LandingPhase.LANDED);
        shuttle.setLandedHexSide(4);
        game.getActiveShuttles().add(shuttle);

        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(fed.getLifeSupportCost());
        e.setFireControl(fed.getFireControlCost());
        e.setActivateShields(fed.getActiveShieldCost());
        e.setWarpMovement(0.0);
        game.submitAllocation(fed, e);
    }

    private void advanceToActivity() {
        for (int g = 0; g < 40 && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; g++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    @Test
    public void load_fillsTheHoldFromTheLandedSide() {
        advanceToActivity();
        Game.ActionResult r = game.loadPersonnelFromPlanet(shuttle, PersonnelType.CREW_UNIT, 3);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("admin shuttle holds one crew unit (J2.211)", 1, shuttle.getHold().getCrew());
        assertEquals("two teams left on side D", 2, planet.getSideManifest(4).getCrew());
        assertEquals(0, shuttle.personnelSpacesFree());
    }

    @Test
    public void unload_putsThemBackOnTheSurface() {
        advanceToActivity();
        game.loadPersonnelFromPlanet(shuttle, PersonnelType.CREW_UNIT, 3); // hold now has 1

        Game.ActionResult r = game.unloadPersonnelToPlanet(shuttle, PersonnelType.CREW_UNIT, 5);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(0, shuttle.getHold().getCrew());
        assertEquals("all three back on the surface", 3, planet.getSideManifest(4).getCrew());
    }

    @Test
    public void load_refusedWhenNotLanded() {
        shuttle.setLandingPhase(LandingPhase.DESCENDING);
        advanceToActivity();
        Game.ActionResult r = game.loadPersonnelFromPlanet(shuttle, PersonnelType.CREW_UNIT, 1);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("landed"));
    }

    @Test
    public void load_refusedOutsideActivityPhase() {
        // Right after allocation the game is in MOVEMENT of impulse 1.
        Game.ActionResult r = game.loadPersonnelFromPlanet(shuttle, PersonnelType.CREW_UNIT, 1);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("Activity phase"));
    }

    @Test
    public void load_refusedWhenLandedSideHasNoOne() {
        shuttle.setLandedHexSide(3); // side C — the crew are on D
        advanceToActivity();
        Game.ActionResult r = game.loadPersonnelFromPlanet(shuttle, PersonnelType.CREW_UNIT, 1);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("Nothing loaded"));
    }
}
