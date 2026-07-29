package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Planet bombardment (P2.311 / P2.525): direct fire at a planet's surface
 * accumulates damage on the chosen hex side (and the total). The side must be
 * one the attacker can see (P2.52), and firing eats the +2 ground-clutter ECM.
 */
public class PlanetBombardmentTest {

    private Game game;
    private Ship fed;
    private Terrain planet;

    @Before
    public void setUp() {
        game = new Game();
        Player fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(11, 10));
        fed.setFacing(1); // north — toward the planet
        fed.setOwner(fedPlayer);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed);

        planet = new Terrain(TerrainType.PLANET, 11, 8); // two hexes north of the ship
        game.addTerrain(planet);

        game.startTurn();
        Energy e = new Energy();
        e.setLifeSupport(fed.getLifeSupportCost());
        e.setFireControl(fed.getFireControlCost());
        e.setActivateShields(fed.getActiveShieldCost());
        e.setPhaserCapacitor(6.0); // charge phasers so they can fire (within the CA's capacitor)
        e.setWarpMovement(0.0);
        game.submitAllocation(fed, e);
    }

    private void advanceToDirectFire() {
        for (int guard = 0; guard < 400
                && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.DIRECT_FIRE, game.getCurrentPhase());
    }

    // ---- Terrain damage accumulator (deterministic) ------------------------

    @Test
    public void terrain_tracksTotalAndPerSideDamage() {
        Terrain p = new Terrain(TerrainType.PLANET, 5, 5);
        p.addDamage(4, 30);
        p.addDamage(4, 20);
        p.addDamage(2, 10);
        assertEquals("total is the sum across sides", 60, p.getTotalDamage());
        assertEquals(50, p.getDamageOnSide(4));
        assertEquals(10, p.getDamageOnSide(2));
        assertEquals(0, p.getDamageOnSide(1));
        p.addDamage(3, -5); // non-positive ignored
        assertEquals(60, p.getTotalDamage());
    }

    // ---- Bombardment resolution --------------------------------------------

    @Test
    public void bombard_dealsDamageToTheChosenVisibleSide() {
        advanceToDirectFire();
        // fed is south of the planet, so it sees the C/D/E sides; D (4) faces it.
        List<Weapon> phasers = fed.getWeapons().getPhaserList();

        Game.ActionResult r = game.bombardPlanet(fed, planet, 4, phasers);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("some damage was dealt: " + r.getMessage(), planet.getTotalDamage() > 0);
        assertEquals("all of it landed on the chosen side",
                planet.getTotalDamage(), planet.getDamageOnSide(4));
    }

    @Test
    public void bombard_refusesASideTheAttackerCannotSee() {
        advanceToDirectFire();
        // Side A (north) faces away from a ship to the south — no LOS.
        Game.ActionResult r = game.bombardPlanet(fed, planet, 1, fed.getWeapons().getPhaserList());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("line of sight"));
        assertEquals(0, planet.getTotalDamage());
    }

    @Test
    public void bombard_spreadsAcrossMultipleVisibleSides() {
        // From the south, sides C(3)/D(4)/E(5) are all visible — fire one fore
        // phaser at each of two of them, working toward SH63's "3 different sides".
        // (Both fore phasers bear on the planet directly ahead.)
        advanceToDirectFire();
        List<Weapon> phasers = fed.getWeapons().getPhaserList();
        List<Weapon> groupD = phasers.subList(0, 1); // fore phaser #1
        List<Weapon> groupC = phasers.subList(1, 2); // fore phaser #2

        assertTrue(game.bombardPlanet(fed, planet, 4, groupD).isSuccess());
        assertTrue(game.bombardPlanet(fed, planet, 3, groupC).isSuccess());

        assertTrue("side D took damage", planet.getDamageOnSide(4) > 0);
        assertTrue("side C took damage", planet.getDamageOnSide(3) > 0);
        assertEquals("total is the sum across the two sides",
                planet.getDamageOnSide(4) + planet.getDamageOnSide(3), planet.getTotalDamage());
    }

    @Test
    public void bombard_skipsWeaponsThatCannotBear() {
        // Face the ship away from the planet: its fore phasers cannot bear on a
        // target directly behind it, so those shots are skipped (arc check).
        fed.setFacing(13); // south; the planet is due north (to the rear)
        advanceToDirectFire();

        Game.ActionResult r = game.bombardPlanet(fed, planet, 4, fed.getWeapons().getPhaserList());

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("fore phasers cannot bear on a planet behind the ship: " + r.getMessage(),
                r.getMessage().contains("cannot bear"));
    }

    @Test
    public void bombard_onlyDuringDirectFire() {
        // Right after allocation the game is in MOVEMENT of impulse 1.
        Game.ActionResult r = game.bombardPlanet(fed, planet, 4, fed.getWeapons().getPhaserList());
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("Direct Fire"));
    }
}
