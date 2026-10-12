package com.sfb.scenario;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;

/**
 * Siting a ground base from a scenario file (R1.14, P2.62).
 *
 * <h2>Why the scenario names a PLANET and not a hex</h2>
 * Every other unit in a scenario states a {@code startHex}. A ground base does not, and that is
 * deliberate: its hex is the planet's hex, so stating it as well would say the same thing twice
 * and the two can silently disagree. The first time somebody nudges a planet one hex while
 * tuning a scenario, the bases would stay behind in empty space — and because every ground-base
 * rule keys off the face (bombardment eligibility under P2.713, the firing arcs, the counter
 * suppression on the map), they would look placed and behave like nothing in particular.
 * <p>
 * Naming the planet leaves one source of truth, and it mirrors the engine: {@code
 * placeGroundBase} takes a planet and a face and has never taken a hex.
 */
public class GroundBaseSitingTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    /** A one-planet, one-base scenario. The planet is a class-M world: one hex (P2.21). */
    private ScenarioSpec spec(String planetName, String faceOnBase, String planetOnBase,
            String terrainType) {
        ScenarioSpec s = new ScenarioSpec();
        s.name = "Siting";
        s.year = 180;
        s.mapCols = 42;
        s.mapRows = 32;

        ScenarioSpec.TerrainSetup planet = new ScenarioSpec.TerrainSetup();
        planet.type = terrainType;
        planet.hex  = "2116";
        planet.name = planetName;
        s.terrain = new ArrayList<>(List.of(planet));

        ScenarioSpec.ShipSetup base = new ScenarioSpec.ShipSetup();
        base.type       = "GBD1";
        base.shipName   = "Nivram Battery";
        base.planet     = planetOnBase;
        base.planetFace = faceOnBase;

        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = "Neutral";
        side.name    = "Defenders";
        side.ships   = new ArrayList<>(List.of(base));
        s.sides = new ArrayList<>(List.of(side));
        return s;
    }

    private Game run(ScenarioSpec s) {
        Game game = new Game();
        game.setupFromScenario(s);
        return game;
    }

    private Ship baseIn(Game game) {
        return game.getShips().stream()
                .filter(sh -> "Nivram Battery".equals(sh.getName()))
                .findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- the happy path

    @Test
    public void aScenarioCanSiteAGroundBaseOnAPlanetFace() {
        Game game = run(spec("Nivram", "B", "Nivram", "PLANET"));

        Ship base = baseIn(game);
        assertNotNull("the base should be in the game", base);
        assertEquals("face B (P2.62)", Integer.valueOf(2), base.getPlanetFace());
        assertEquals("B looks in direction 5, as a heading of B would",
                5, base.getFacing());
        assertEquals("it stands in the planet's own hex — a class-M planet is one hex (P2.21)",
                game.getTerrain().get(0).getLocation(), base.getLocation());
        assertEquals("and it does not move", 0, base.getSpeed());
    }

    /** Lower case, and the numeric form, mean the same thing. */
    @Test
    public void theFaceMayBeWrittenAsALetterOrANumber() {
        assertEquals(1, ScenarioLoader.parsePlanetFace("A"));
        assertEquals(6, ScenarioLoader.parsePlanetFace("f"));
        assertEquals(3, ScenarioLoader.parsePlanetFace("3"));
        assertEquals("not a face", 0, ScenarioLoader.parsePlanetFace("G"));
        assertEquals("not a face", 0, ScenarioLoader.parsePlanetFace("0"));
        assertEquals("not a face", 0, ScenarioLoader.parsePlanetFace(null));
    }

    /** All six, and all of them in the planet's one hex, told apart only by the face. */
    @Test
    public void allSixFacesCanBeOccupied() {
        ScenarioSpec s = spec("Nivram", "A", "Nivram", "PLANET");
        s.sides.get(0).ships.clear();
        for (int face = 1; face <= 6; face++) {
            ScenarioSpec.ShipSetup b = new ScenarioSpec.ShipSetup();
            b.type       = "GBD1";
            b.shipName   = "Battery-" + (char) ('A' + face - 1);
            b.planet     = "Nivram";
            b.planetFace = String.valueOf((char) ('A' + face - 1));
            s.sides.get(0).ships.add(b);
        }

        Game game = run(s);

        for (int face = 1; face <= 6; face++) {
            String name = "Battery-" + (char) ('A' + face - 1);
            Ship b = game.getShips().stream().filter(sh -> name.equals(sh.getName()))
                    .findFirst().orElse(null);
            assertNotNull(name + " should be sited", b);
            assertEquals(Integer.valueOf(face), b.getPlanetFace());
            assertEquals("all six share the planet's hex",
                    game.getTerrain().get(0).getLocation(), b.getLocation());
        }
    }

    // ---------------------------------------------------------------- refusals

    /**
     * Left OFF the map rather than dropped into space. A base placed without a face would look
     * present and behave like nothing in particular, which is the harder failure to notice.
     */
    @Test
    public void aBaseNamingAPlanetThatIsNotThereIsOmitted() {
        Game game = run(spec("Nivram", "B", "Atlantis", "PLANET"));

        assertNull("no planet called Atlantis, so no base", baseIn(game));
    }

    @Test
    public void aBaseWithAnUnreadableFaceIsOmitted() {
        Game game = run(spec("Nivram", "Q", "Nivram", "PLANET"));

        assertNull(baseIn(game));
    }

    /** P2.222: a gas giant has no surface to build on, and the refusal comes from core. */
    @Test
    public void aBaseOnAGasGiantIsOmitted() {
        Game game = run(spec("Jupiter", "B", "Jupiter", "GAS_GIANT"));

        assertNull(baseIn(game));
    }

    // ---------------------------------------------------------------- it is a real ground base

    /**
     * The point of all of it: a scenario-placed base is a ground base to every rule, not just a
     * ship that happens to be standing on a planet. P2.713 only recognises one by its face.
     */
    @Test
    public void aScenarioPlacedBaseCanBeBombarded() {
        Game game = run(spec("Nivram", "A", "Nivram", "PLANET"));
        Ship base = baseIn(game);
        assertNotNull(base);

        Ship kzinti = new Ship();
        kzinti.init(com.sfb.samples.KzintiShips.getKzinBC());
        kzinti.setName("KHS Quasar");
        kzinti.setFacing(1);
        kzinti.setActiveFireControl(true);
        com.sfb.properties.Location hex = base.getLocation();
        kzinti.setLocation(new com.sfb.properties.Location(hex.getX(), hex.getY() - 3));
        game.getShips().add(kzinti);

        com.sfb.weapons.DroneRack rack = kzinti.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.DroneRack)
                .map(w -> (com.sfb.weapons.DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("the BC should carry racks"));

        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        Game.ActionResult r = game.launchDroneGroundAttack(kzinti, base, rack);

        assertTrue(r.getMessage(), r.isSuccess());
    }
}
