package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.sfb.dto.GameStateDto;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.scenario.ScenarioSpec;

/**
 * Saving a whole battle and loading it back — slice D3 of the saved-game work.
 *
 * <h2>What a save is, and what it therefore is not</h2>
 * A scenario plus accumulated damage. The hulls, their refits and year upgrades, the terrain and
 * the map are rebuilt by {@code setupFromScenario}; the save overlays what the battle did. So a
 * load is two steps, and this test performs them in that order — build from the same scenario,
 * then apply the state.
 *
 * <h2>The witness</h2>
 * {@link DtoDiff} compares the whole {@code GameStateDto} graph before and after, the same way
 * {@link ShipStateRoundTripTest} compares one ship's. Anything the client can see and the save
 * cannot carry is reported by name. {@link #KNOWN_GAPS} holds what is not converted yet and is
 * checked in BOTH directions, so a gap that starts surviving is reported as stale rather than
 * sitting there claiming work that is already done.
 *
 * <p>It remains a broad net and not a complete one: a value the DTO never exposes cannot be
 * witnessed here. Those get assertions of their own below.
 */
public class GameStateRoundTripTest {

    /**
     * The DTO graph to compare, with the units CREATED IN PLAY removed.
     *
     * <h2>Why filtering beats a tolerated gap</h2>
     * Seekers, mines and launched shuttles are made during the battle, so unlike ships the
     * scenario cannot rebuild them and the save will have to describe them outright — slice D3b.
     * The first draft of this test tolerated the whole {@code mapObjects} field instead, and that
     * was far too coarse: every ship lives in {@code mapObjects} too, so
     * {@code mapObjects[3].shields[0].current} was being excused by the seeker gap. A save could
     * have lost every shield on every ship and this test would have passed.
     *
     * <p>Removing just the in-play classes leaves the ships, terrain and objectives inside
     * {@code mapObjects} fully compared.
     *
     * <p>DRONES AND PLASMA TORPEDOES ARE NO LONGER FILTERED — slice D3b saves them, so they are
     * compared like everything else and {@link #aDroneInFlightSurvivesASave} pins them by name.
     * What remains filtered is the craft-based units: a shuttle, a suicide shuttle, a
     * scatter-pack and a wild weasel all wrap a shuttle created from a bay, and restoring one
     * means restoring the bay too. Mines are still unconverted as well.
     */
    private static GameStateDto comparable(Game game) {
        GameStateDto dto = new GameStateDto(game);
        dto.mapObjects = dto.mapObjects.stream()
                .filter(object -> !(object instanceof GameStateDto.ShuttleDto
                        || object instanceof GameStateDto.SuicideShuttleDto
                        || object instanceof GameStateDto.ScatterPackDto
                        || object instanceof GameStateDto.WildWeaselDto
                        || object instanceof GameStateDto.MineDto))
                .toList();
        return dto;
    }

    // ---------------------------------------------------------------- the round trip

    /**
     * Groups not converted yet, keyed by a PATH FRAGMENT rather than a top-level field name.
     *
     * The granularity is the lesson. Keying on the top-level name meant {@code "mapObjects"},
     * which excused every ship field as well — a save could have lost every shield on every ship
     * and this test would have passed. A fragment like {@code ".droneRacks"} excuses only what it
     * names, and everything either side of it stays checked. Switching to fragments is what
     * surfaced both of the entries below, which the coarse version had been hiding.
     */
    private static final Map<String, String> KNOWN_GAPS = Map.of(
            ".droneRacks", "DroneRack contents and settled mode are not saved yet (slice D3c)",
            ".controlUsed", "ControlSpaces is not converted yet (slice D3c)");

    /** Everything the scenario rebuilds, plus everything the battle did to it. */
    @Test
    public void aBattleSurvivesASaveAndReload() {
        Game before = battleInProgress();
        Game after = roundTrip(before);

        List<String> unexpected = new ArrayList<>();
        for (String difference : DtoDiff.between(comparable(before), comparable(after)))
            if (KNOWN_GAPS.keySet().stream().noneMatch(difference::contains))
                unexpected.add(difference);

        assertTrue("these did not survive a save and reload, and no slice claims them:\n  "
                + String.join("\n  ", unexpected), unexpected.isEmpty());
    }

    /** And the gap list cannot outlive the gaps: every fragment must still match something. */
    @Test
    public void theGapListDoesNotOutliveTheGaps() {
        Game before = battleInProgress();
        List<String> differences =
                DtoDiff.between(comparable(before), comparable(roundTrip(before)));

        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> gap : KNOWN_GAPS.entrySet())
            if (differences.stream().noneMatch(d -> d.contains(gap.getKey())))
                stale.add(gap.getKey() + " — " + gap.getValue());

        assertTrue("these now survive; delete them from KNOWN_GAPS:\n  "
                + String.join("\n  ", stale), stale.isEmpty());
    }

    // ---------------------------------------------------------------- seekers in flight

    /**
     * A weapon in flight is CREATED by the save rather than overlaid onto something the scenario
     * rebuilt, which is what makes seekers a different problem from ships.
     *
     * This test used to assert the opposite — that a drone was lost — as a deliberate pin on the
     * gap. Slice D3b flipped it, which is the whole value of pinning a gap as an assertion
     * instead of as a tolerated field: the day the work lands, the test tells you.
     */
    @Test
    public void aDroneInFlightSurvivesASave() {
        Game before = battleInProgress();
        assertEquals("fixture: one drone in flight", 1, before.getSeekers().size());
        Seeker launched = before.getSeekers().get(0);

        Game after = roundTrip(before);

        assertEquals("the drone must still be there", 1, after.getSeekers().size());
        Seeker restored = after.getSeekers().get(0);
        assertEquals(launched.getSeekerType(), restored.getSeekerType());
        assertEquals("and in the same hex",
                ((com.sfb.objects.Unit) launched).getLocation(),
                ((com.sfb.objects.Unit) restored).getLocation());
        assertEquals("with the same endurance left",
                launched.getEndurance(), restored.getEndurance());
    }

    /**
     * Its references must point at the RESTORED units, not dangle or point at the saved ones —
     * the reason seekers are revived in the second pass.
     */
    @Test
    public void aDronesTargetAndControllerPointAtTheRestoredShips() {
        Game before = battleInProgress();

        Game after = roundTrip(before);
        Seeker restored = after.getSeekers().get(0);

        assertSame("the target must be the restored Hood",
                shipNamed(after, "USS Hood"), restored.getTarget());
        assertSame("and the controller the restored Blade",
                shipNamed(after, "IKV Blade"),
                ((com.sfb.objects.Drone) restored).getController());
    }

    /** Warhead and flight details come from the type, but a drone keeps what it launched with. */
    @Test
    public void aDroneKeepsItsTypeAndWarhead() {
        Game before = battleInProgress();
        com.sfb.objects.Drone launched = (com.sfb.objects.Drone) before.getSeekers().get(0);
        int warhead = launched.impact();
        int speed = launched.getSpeed();

        com.sfb.objects.Drone restored =
                (com.sfb.objects.Drone) roundTrip(before).getSeekers().get(0);

        assertEquals(launched.getDroneType(), restored.getDroneType());
        assertEquals("a restored drone must hit as hard as the one that was saved",
                warhead, restored.impact());
        assertEquals(speed, restored.getSpeed());
    }

    /**
     * F4.11 counts DISTINCT hexes adjacent to the target, so the hexes are saved rather than a
     * count. Restoring a bare count of two would let the next re-entry of an already-counted hex
     * detonate the weapon, which the rule does not allow.
     */
    @Test
    public void aBallisticCourseKeepsWhichHexesItHasEntered() {
        Game before = battleInProgress();
        com.sfb.objects.Drone drone = (com.sfb.objects.Drone) before.getSeekers().get(0);
        com.sfb.properties.Location aim = new com.sfb.properties.Location(20, 20);
        com.sfb.objects.BallisticCourse course = new com.sfb.objects.BallisticCourse(aim);
        drone.setTarget(null);
        drone.setBallisticCourse(course);
        course.noteHexEntered(com.sfb.utilities.MapUtils.getAdjacentHex(aim, 1, 42, 32));
        course.noteHexEntered(com.sfb.utilities.MapUtils.getAdjacentHex(aim, 5, 42, 32));
        assertEquals("fixture: two distinct adjacent hexes", 2,
                course.getAdjacentHexesEntered());

        com.sfb.objects.Drone restored =
                (com.sfb.objects.Drone) roundTrip(before).getSeekers().get(0);

        assertNotNull("the course itself must survive", restored.getBallisticCourse());
        assertEquals(aim, restored.getBallisticCourse().getTargetHex());
        assertEquals("both hexes, not a count", 2,
                restored.getBallisticCourse().getAdjacentHexesEntered());
        assertFalse(restored.getBallisticCourse().isEvading());
    }

    /** An evading course (F4.11) must not come back still approaching. */
    @Test
    public void anEvadingBallisticCourseStaysEvading() {
        Game before = battleInProgress();
        com.sfb.objects.Drone drone = (com.sfb.objects.Drone) before.getSeekers().get(0);
        com.sfb.properties.Location aim = new com.sfb.properties.Location(20, 20);
        com.sfb.objects.BallisticCourse course = new com.sfb.objects.BallisticCourse(aim);
        drone.setTarget(null);
        drone.setBallisticCourse(course);
        course.noteHexEntered(aim);
        assertTrue("fixture: entering the target hex starts evasion", course.isEvading());

        com.sfb.objects.Drone restored =
                (com.sfb.objects.Drone) roundTrip(before).getSeekers().get(0);

        assertTrue(restored.getBallisticCourse().isEvading());
    }

    /**
     * An orphaned drone is a REAL state, not a load failure: F4.12 and the drone-control rules
     * both produce one, and it keeps flying on its last instructions. So an unresolvable
     * reference is left null rather than reported.
     */
    @Test
    public void anOrphanedDroneLoadsWithoutComplaint() {
        Game before = battleInProgress();
        ((com.sfb.objects.Drone) before.getSeekers().get(0)).setController(null);
        Map<String, Object> saved = before.exportState();

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());
        List<String> problems = rebuilt.applyState(saved);

        assertEquals("losing a controller is lawful, not an error", List.of(), problems);
        assertEquals(1, rebuilt.getSeekers().size());
        assertNull(((com.sfb.objects.Drone) rebuilt.getSeekers().get(0)).getController());
    }

    /**
     * A craft-based seeker cannot be rebuilt yet, and must be REPORTED rather than quietly
     * ceasing to exist. This is what the partial exportState on those classes is for.
     */
    @Test
    public void aSeekerKindThisBuildCannotRebuildIsReported() {
        Game before = battleInProgress();
        Map<String, Object> saved = before.exportState();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seekers = (List<Map<String, Object>>) saved.get("seekers");
        seekers.get(0).put(Seeker.SAVE_KIND, "SCATTER_PACK");
        seekers.get(0).put("name", "Scatter-1");

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());
        List<String> problems = rebuilt.applyState(saved);

        assertTrue("the player must be told something was lost: " + problems,
                problems.stream().anyMatch(p -> p.contains("Scatter-1")));
        assertTrue(rebuilt.getSeekers().isEmpty());
    }

    /** A save naming a drone type this build does not have is reported, not flown. */
    @Test
    public void anUnknownDroneTypeIsReported() {
        Game before = battleInProgress();
        Map<String, Object> saved = before.exportState();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seekers = (List<Map<String, Object>>) saved.get("seekers");
        seekers.get(0).put("droneType", "TypeNotAThing");

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());

        assertFalse(rebuilt.applyState(saved).isEmpty());
        assertTrue(rebuilt.getSeekers().isEmpty());
    }

    // ---------------------------------------------------------------- the clock

    /**
     * One number is the whole clock, and getting it wrong moves the battle in time. A turn
     * restored one short would replay a turn both players had already fought.
     */
    @Test
    public void theClockComesBackWhereItWas() {
        Game before = battleInProgress();
        int impulse = before.getClock().getImpulse();
        int turn = before.getClock().getTurn();
        assertTrue("fixture: the battle should be past turn 0", turn >= 1);

        Game after = roundTrip(before);

        assertEquals(impulse, after.getClock().getImpulse());
        assertEquals(turn, after.getClock().getTurn());
        assertEquals(before.getClock().getLocalImpulse(), after.getClock().getLocalImpulse());
    }

    // ---------------------------------------------------------------- cross-references

    /**
     * The reason a load needs two passes. A lock-on is a reference to another unit, so it can
     * only be resolved once every unit exists — and it persists across turns (D6.12), so losing
     * it would hand every ship a free re-acquisition.
     */
    @Test
    public void lockOnsAreRestoredBetweenTheRightUnits() {
        Game before = battleInProgress();
        Ship attacker = shipNamed(before, "USS Hood");
        Ship victim = shipNamed(before, "IKV Blade");
        attacker.addLockOn(victim);

        Game after = roundTrip(before);

        Ship restored = shipNamed(after, "USS Hood");
        assertEquals("exactly one lock-on should come back", 1, restored.getLockOns().size());
        assertSame("and it must point at the restored IKV Blade, not the saved one",
                shipNamed(after, "IKV Blade"), restored.getLockOns().iterator().next());
    }

    /**
     * A lock resting on a tractor beam alone is NOT one the sensors earned (D6.627, G7.97), and
     * the restore order is what keeps them apart: addLockOn deliberately clears a beam-only mark,
     * so applying the two sets the other way round would promote every beam-granted lock.
     */
    @Test
    public void aBeamOnlyLockDoesNotComeBackAsAnEarnedOne() {
        Game before = battleInProgress();
        Ship attacker = shipNamed(before, "USS Hood");
        attacker.addTractorLockOn(shipNamed(before, "IKV Blade"));
        assertTrue("fixture", attacker.isTractorOnlyLockOn(shipNamed(before, "IKV Blade")));

        Game after = roundTrip(before);

        Ship restored = shipNamed(after, "USS Hood");
        assertTrue("the lock itself survives",
                restored.hasLockOn(shipNamed(after, "IKV Blade")));
        assertTrue("and it is still only a beam's",
                restored.isTractorOnlyLockOn(shipNamed(after, "IKV Blade")));
    }

    // ---------------------------------------------------------------- ships

    @Test
    public void everyShipsOwnDamageComesBack() {
        Game before = battleInProgress();
        int[] hoodShields = shipNamed(before, "USS Hood").getShields().getShieldValues().clone();
        int[] bladeShields = shipNamed(before, "IKV Blade").getShields().getShieldValues().clone();

        Game after = roundTrip(before);

        assertArrayEquals(hoodShields,
                shipNamed(after, "USS Hood").getShields().getShieldValues());
        assertArrayEquals(bladeShields,
                shipNamed(after, "IKV Blade").getShields().getShieldValues());
    }

    /** Damage must land on the right ship. Matching by name is what guarantees it. */
    @Test
    public void damageDoesNotLandOnTheWrongShip() {
        Game before = battleInProgress();
        Ship blade = shipNamed(before, "IKV Blade");
        // Shield 3, not shield 1: the fixture LOWERS the Blade's first shield, and damage aimed
        // at a lowered shield passes through to the internals instead of reducing it — so the
        // first draft of this test was asserting against a number that never moved.
        blade.getShields().damageShield(3, blade.getShields().getShieldStrength(3));
        int hoodShield3 = shipNamed(before, "USS Hood").getShields().getShieldStrength(3);

        Game after = roundTrip(before);

        assertEquals("the Blade's flattened shield", 0,
                shipNamed(after, "IKV Blade").getShields().getShieldStrength(3));
        assertEquals("the Hood's must be untouched", hoodShield3,
                shipNamed(after, "USS Hood").getShields().getShieldStrength(3));
    }

    @Test
    public void ownershipAndTeamsComeBack() {
        Game before = battleInProgress();
        Ship hood = shipNamed(before, "USS Hood");
        assertNotNull("fixture: the scenario should have given it an owner", hood.getOwner());
        String team = hood.getOwner().getTeamName();

        Ship restored = shipNamed(roundTrip(before), "USS Hood");

        assertNotNull("a ship with no owner answers to nobody", restored.getOwner());
        assertEquals(team, restored.getOwner().getTeamName());
    }

    /** A hull the battle already killed must not come back fighting. */
    @Test
    public void aDestroyedShipStaysDestroyed() {
        Game before = battleInProgress();
        Ship doomed = shipNamed(before, "IKV Blade");
        before.getShips().remove(doomed);
        before.getDestroyedShips().add(doomed);

        Game after = roundTrip(before);

        assertTrue("it must not be back among the living",
                after.getShips().stream().noneMatch(s -> "IKV Blade".equals(s.getName())));
        assertTrue("and it must still count for victory points",
                after.getDestroyedShips().stream().anyMatch(s -> "IKV Blade".equals(s.getName())));
    }

    // ---------------------------------------------------------------- what a load reports

    @Test
    public void acleanLoadReportsNoProblems() {
        Game before = battleInProgress();
        Map<String, Object> saved = before.exportState();

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());

        assertEquals(List.of(), rebuilt.applyState(saved));
    }

    /** A save naming a ship the scenario no longer has must say so rather than fail silently. */
    @Test
    public void aSaveThatNamesAnUnknownShipSaysSo() {
        Game before = battleInProgress();
        Map<String, Object> saved = before.exportState();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ships = (List<Map<String, Object>>) saved.get("ships");
        ships.get(0).put("name", "USS Nonexistent");

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());
        List<String> problems = rebuilt.applyState(saved);

        assertTrue("should report both sides of the mismatch: " + problems,
                problems.stream().anyMatch(p -> p.contains("USS Nonexistent"))
                        && problems.stream().anyMatch(p -> p.contains("not in the save")));
    }

    /** A save from a newer build is reported, not silently half-applied. */
    @Test
    public void aSaveFromTheFutureIsReported() {
        Game before = battleInProgress();
        Map<String, Object> saved = before.exportState();
        saved.put("formatVersion", Game.SAVE_FORMAT_VERSION + 1);

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());

        assertTrue(rebuilt.applyState(saved).stream().anyMatch(p -> p.contains("newer version")));
    }

    @Test
    public void noSavedStateAtAllIsReported() {
        Game game = new Game();
        assertFalse(game.applyState(null).isEmpty());
    }

    // ---------------------------------------------------------------- helpers

    /** Two ships, a few hexes apart, with the battle already a turn and a bit old. */
    private static ScenarioSpec spec() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Save fixture";
        spec.year = 180;
        spec.mapCols = 42;
        spec.mapRows = 32;

        ScenarioSpec.SideSpec federation = new ScenarioSpec.SideSpec();
        federation.faction = "Federation";
        federation.name = "Starfleet";
        federation.ships = new ArrayList<>(List.of(ship("Federation", "CA", "USS Hood", "1010")));

        ScenarioSpec.SideSpec klingon = new ScenarioSpec.SideSpec();
        klingon.faction = "Klingon";
        klingon.name = "Empire";
        klingon.ships = new ArrayList<>(List.of(ship("Klingon", "D7", "IKV Blade", "1016")));

        spec.sides = new ArrayList<>(List.of(federation, klingon));
        return spec;
    }

    private static ScenarioSpec.ShipSetup ship(String faction, String type, String name,
            String hex) {
        ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
        setup.faction = faction;
        setup.type = type;
        setup.shipName = name;
        setup.startHex = hex;
        setup.startHeading = "A";
        return setup;
    }

    /**
     * A battle that has actually been fought in a little, so the save has something to carry.
     *
     * The clock is advanced past turn 0 deliberately: a save that restored the turn number one
     * short would replay a turn both players had already played, and a fixture sitting at turn 0
     * could never show it.
     */
    private static Game battleInProgress() {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        Game game = new Game();
        game.setupFromScenario(spec());

        for (int i = 0; i < 40; i++)
            game.getClock().nextImpulse();

        Ship hood = shipNamed(game, "USS Hood");
        Ship blade = shipNamed(game, "IKV Blade");
        hood.getShields().damageShield(2, 5);
        hood.getHullBoxes().damageFhull();
        blade.getShields().damageShield(5, 4);
        blade.getShields().lowerShield(1);
        blade.getCrew().setAvailableCrewUnits(blade.getCrew().getAvailableCrewUnits() - 2);

        // Ownership is NOT something setupFromScenario does — the server establishes it when a
        // host assigns ships (GameSession.assignShip), so the fixture has to stand in for that.
        // Worth knowing: a scenario alone produces ships that answer to nobody.
        own(game, "Starfleet", "Captain Dan", com.sfb.properties.Faction.Federation, hood);
        own(game, "Empire", "Captain Dave", com.sfb.properties.Faction.Klingon, blade);

        // A drone in flight, because seekers persist across turns and are the whole reason
        // mapObjects is still a gap. Without one the gap is invisible and the net proves nothing
        // — the same trap that made the ship-level fixture look satisfied twice over.
        // The KLINGON launches it: a Federation CA carries no drone racks, which is a fact about
        // the factions rather than about this test.
        com.sfb.weapons.DroneRack rack = blade.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.DroneRack)
                .map(w -> (com.sfb.weapons.DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("fixture: the D7 has racks"));
        // A drone may only be launched in the Activity phase, so walk the phase machine round to
        // it rather than reaching past the rule.
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
        blade.setActiveFireControl(true);
        blade.addLockOn(hood);
        Game.ActionResult launched = game.launchDrone(blade, hood, rack);
        assertTrue("fixture: the drone must actually get away, or the seeker gap is invisible "
                + "and the net proves nothing — " + launched.getMessage(), launched.isSuccess());
        assertFalse("fixture: there should be a seeker in flight", game.getSeekers().isEmpty());
        blade.clearLockOns();   // the lock was only to make the launch legal

        return game;
    }

    /** Stand in for the server's ship assignment, which is what creates ownership. */
    private static void own(Game game, String team, String name,
            com.sfb.properties.Faction faction, Ship... owned) {
        Player player = new Player();
        player.setName(name);
        player.setTeamName(team);
        player.setFaction(faction);
        for (Ship ship : owned) {
            player.getPlayerUnits().add(ship);
            ship.setOwner(player);
        }
        game.getPlayers().add(player);
    }

    /** Export, rebuild from the same scenario, apply. The order is the format. */
    private static Game roundTrip(Game original) {
        Map<String, Object> saved = original.exportState();
        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());
        rebuilt.applyState(saved);
        return rebuilt;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship ship : game.getShips())
            if (name.equals(ship.getName()))
                return ship;
        throw new AssertionError("no ship called " + name + " in "
                + game.getShips().stream().map(Ship::getName).toList());
    }
}
