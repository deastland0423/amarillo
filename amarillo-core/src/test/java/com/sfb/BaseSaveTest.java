package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.scenario.ScenarioSpec;

/**
 * Bases survive a save — both kinds, which fail differently if they do not.
 *
 * <h2>Why they are worth their own test</h2>
 * A base is a {@link Ship}, so it rides the ship save path and most of this is already covered by
 * {@link GameStateRoundTripTest}. Two things are not.
 *
 * <p>A GROUND BASE is the only unit whose position is not restored from the save at all. R1.14
 * has it name a PLANET and a FACE rather than a hex, and {@code setupFromScenario} re-sites it by
 * walking out from the planet's centre — so its hex, its heading AND its face all come back by
 * RECONSTRUCTION. Ship.exportState does write {@code planetFace}, but on this path nothing reads
 * it: deleting that line leaves every test here passing, which is how this comment came to say so.
 *
 * <p>That is the right design — move a planet while tuning a scenario and its bases move with it
 * — but it makes the position assertions below weaker than they look, and the weakness is worth
 * naming. They cannot catch a save that loses a base's position, because the save was never
 * carrying it. What they do catch is a rebuild that sites it differently from the original, and
 * {@link #aGroundBaseWhosePlanetIsMissingIsReported} covers the case where it cannot site it at
 * all — the one way this reconstruction can actually fail.
 *
 * <p>A base also has no turn mode and cannot move (C3.7, unbuilt), so an error in its heading or
 * speed is one the battle can never work out of: a ship that comes back facing the wrong way
 * turns around, and a base facing the wrong way has its arcs wrong for the rest of the game.
 */
public class BaseSaveTest {

    private static final String PLANET = "Nivram";

    // ---------------------------------------------------------------- the scenario

    private static ScenarioSpec spec() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Base save fixture";
        spec.year = 180;
        spec.mapCols = 42;
        spec.mapRows = 32;

        ScenarioSpec.TerrainSetup planet = new ScenarioSpec.TerrainSetup();
        planet.type = "PLANET";
        planet.hex = "2020";
        planet.name = PLANET;
        spec.terrain = new ArrayList<>(List.of(planet));

        ScenarioSpec.SideSpec defenders = new ScenarioSpec.SideSpec();
        defenders.faction = "Federation";
        defenders.name = "Starfleet";
        defenders.ships = new ArrayList<>(List.of(
                sited("Neutral", "GBD2", "Nivram Battery", "C"),
                hex("Federation", "BS", "Starbase Vega", "1010")));

        ScenarioSpec.SideSpec attackers = new ScenarioSpec.SideSpec();
        attackers.faction = "Klingon";
        attackers.name = "Empire";
        attackers.ships = new ArrayList<>(List.of(hex("Klingon", "D7", "IKV Blade", "1016")));

        spec.sides = new ArrayList<>(List.of(defenders, attackers));
        return spec;
    }

    private static ScenarioSpec.ShipSetup hex(String faction, String type, String name, String at) {
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.faction = faction;
        setup.type = type;
        setup.shipName = name;
        setup.startHex = at;
        setup.startHeading = "A";
        return setup;
    }

    /** R1.14: a ground base names its planet and face, never a hex. */
    private static ScenarioSpec.ShipSetup sited(String faction, String type, String name,
            String face) {
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.faction = faction;
        setup.type = type;
        setup.shipName = name;
        setup.planet = PLANET;
        setup.planetFace = face;
        return setup;
    }

    private static Game battleInProgress() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        Game game = new Game();
        game.setupFromScenario(spec());
        for (int i = 0; i < 40; i++)
            game.getClock().nextImpulse();
        return game;
    }

    private static Game roundTrip(Game original) {
        Map<String, Object> saved = original.exportState();
        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());
        rebuilt.applyState(saved);
        return rebuilt;
    }

    private static Ship named(Game game, String name) {
        for (Ship ship : game.getShips())
            if (name.equals(ship.getName()))
                return ship;
        throw new AssertionError(name + " is not in this game");
    }

    // ---------------------------------------------------------------- the fixture itself

    @Test
    public void theFixtureSitesTheBaseOnThePlanet() {
        Game game = battleInProgress();
        Ship battery = named(game, "Nivram Battery");
        assertTrue("fixture: a GBD2 should be a base", battery.isBase());
        assertNotNull("fixture: it should be on the map", battery.getLocation());
        assertEquals("fixture: face C is the third face", Integer.valueOf(3),
                battery.getPlanetFace());
    }

    // ---------------------------------------------------------------- ground bases

    /**
     * Its hex, heading and face all come back — all three RECONSTRUCTED from the scenario rather
     * than read out of the save. See the class comment: this passes with planetFace deleted from
     * Ship.exportState.
     */
    @Test
    public void aGroundBaseComesBackWhereItStood() {
        Game before = battleInProgress();
        Ship battery = named(before, "Nivram Battery");

        Ship after = named(roundTrip(before), "Nivram Battery");

        assertEquals("the hex is rebuilt from the planet and the face",
                battery.getLocation(), after.getLocation());
        assertEquals("and a base cannot turn, so its heading must be exact (C3.7)",
                battery.getFacing(), after.getFacing());
        assertEquals(battery.getPlanetFace(), after.getPlanetFace());
        assertEquals("a base never moves", 0, after.getSpeed());
    }

    /** Damage to a base is damage like any other, and must not be repaired by a reload. */
    @Test
    public void aBatteredGroundBaseStaysBattered() {
        Game before = battleInProgress();
        Ship battery = named(before, "Nivram Battery");
        battery.getShields().damageShield(1, 4);
        battery.getHullBoxes().damageFhull();
        int shield = battery.getShields().getShieldStrength(1);
        int hull = battery.getHullBoxes().getAvailableFhull();

        Ship after = named(roundTrip(before), "Nivram Battery");

        assertEquals("the shield must stay down", shield,
                after.getShields().getShieldStrength(1));
        assertEquals("and the hull boxes stay gone", hull, after.getHullBoxes().getAvailableFhull());
    }

    /**
     * A base that was destroyed stays destroyed.
     *
     * Worth pinning separately for a ground base, because it is the one unit the scenario rebuild
     * puts back by SITING rather than by placing at a saved hex — a destroyed base that slipped
     * past the retirement step would reappear standing on its planet, fully armed.
     */
    @Test
    public void aDestroyedGroundBaseDoesNotReappearOnItsPlanet() {
        Game before = battleInProgress();
        Ship doomed = named(before, "Nivram Battery");
        before.getShips().remove(doomed);
        before.getDestroyedShips().add(doomed);

        Game after = roundTrip(before);

        for (Ship ship : after.getShips())
            assertNotEquals("the battery was destroyed and must not come back",
                    "Nivram Battery", ship.getName());
        assertTrue("and it must be counted among the dead for victory points",
                after.getDestroyedShips().stream()
                        .anyMatch(s -> "Nivram Battery".equals(s.getName())));
    }

    /**
     * The one way the reconstruction can fail, and it must be REPORTED.
     *
     * A ground base is sited by matching its scenario entry's planet NAME against the terrain.
     * If that name does not match — a scenario edited after the save was taken, a renamed planet
     * — {@code siteGroundBase} refuses rather than dropping the base into space, so the base is
     * never enrolled and the save finds nobody of that name to overlay. The player has to be
     * told: a battery that quietly stopped existing would look like the enemy had killed it.
     */
    @Test
    public void aGroundBaseWhosePlanetIsMissingIsReported() {
        Game before = battleInProgress();
        Map<String, Object> saved = before.exportState();

        ScenarioSpec renamed = spec();
        renamed.terrain.get(0).name = "Somewhere Else";
        Game rebuilt = new Game();
        rebuilt.setupFromScenario(renamed);
        List<String> problems = rebuilt.applyState(saved);

        assertTrue("the player must be told the battery is gone: " + problems,
                problems.stream().anyMatch(p -> p.contains("Nivram Battery")));
        assertTrue("and it must not be standing in space",
                rebuilt.getShips().stream()
                        .noneMatch(s -> "Nivram Battery".equals(s.getName())));
    }

    // ---------------------------------------------------------------- base stations

    /** An orbital base is an ordinary ship for the save's purposes, and this says so out loud. */
    @Test
    public void anOrbitalBaseStationSurvivesWithItsDamage() {
        Game before = battleInProgress();
        Ship starbase = named(before, "Starbase Vega");
        assertTrue("fixture: a BS is a base", starbase.isBase());
        starbase.getShields().damageShield(4, 7);
        int shield = starbase.getShields().getShieldStrength(4);

        Ship after = named(roundTrip(before), "Starbase Vega");

        assertEquals(starbase.getLocation(), after.getLocation());
        assertEquals(shield, after.getShields().getShieldStrength(4));
        assertNull("a station has no planet face", after.getPlanetFace());
    }

    // ---------------------------------------------------------------- the whole save

    /** And the save as a whole loads clean, with nothing to report about either base. */
    @Test
    public void aBattleWithBasesLoadsWithoutComplaint() {
        Game before = battleInProgress();
        Map<String, Object> saved = before.exportState();

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());
        List<String> problems = rebuilt.applyState(saved);

        assertTrue("a clean save must report nothing: " + problems, problems.isEmpty());
    }
}
