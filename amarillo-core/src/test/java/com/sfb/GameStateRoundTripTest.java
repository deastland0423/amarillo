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
    /** Delegates to the shared helper, which also normalises the unordered collections. */
    private static GameStateDto comparable(Game game) {
        return DtoDiff.comparable(game);
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
    private static final Map<String, String> KNOWN_GAPS = Map.of();

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
     * A seeker kind with no loader is REPORTED rather than quietly ceasing to exist.
     *
     * This test used to name SCATTER_PACK as the example, with a comment saying craft-based
     * seekers could not be rebuilt yet. Both halves have expired — they rebuild now — so the
     * example is a kind that genuinely does not exist. The guarantee it protects is unchanged
     * and is the one that matters: whatever this build cannot load, the player hears about.
     */
    @Test
    public void aSeekerKindThisBuildCannotRebuildIsReported() {
        Game before = battleInProgress();
        Map<String, Object> saved = before.exportState();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> seekers = (List<Map<String, Object>>) saved.get("seekers");
        seekers.get(0).put(Seeker.SAVE_KIND, "MONGOOSE_MISSILE");
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

    /**
     * A ship can hold a lock on an incoming DRONE — {@code lockOns} is a Set&lt;Unit&gt;, not a
     * set of ships — and that is the lock a player most wants back, being the one their point
     * defence was about to use.
     *
     * This is why pass two revives seekers BEFORE resolving lock-ons. The first draft did it the
     * other way round and silently dropped every lock held on a weapon in flight; a drone that
     * does not exist yet cannot be pointed at.
     */
    @Test
    public void aLockHeldOnAWeaponInFlightComesBack() {
        Game before = battleInProgress();
        Ship hood = shipNamed(before, "USS Hood");
        Seeker incoming = before.getSeekers().get(0);
        hood.addLockOn((com.sfb.objects.Unit) incoming);
        String droneName = ((com.sfb.objects.Unit) incoming).getName();

        Game after = roundTrip(before);

        Ship restoredHood = shipNamed(after, "USS Hood");
        com.sfb.objects.Unit restoredDrone = (com.sfb.objects.Unit) after.getSeekers().get(0);
        assertEquals("fixture sanity: the drone should keep its name", droneName,
                restoredDrone.getName());
        assertTrue("the lock on the drone must survive, not just the locks on ships",
                restoredHood.hasLockOn(restoredDrone));
    }

    /**
     * A seeker can be aimed at ANOTHER SEEKER — a drone fired at an incoming drone is ordinary
     * anti-drone fire — and that target has to survive a save.
     *
     * Reported from a playtest: "the CA+ launched a drone at one of the Klingon drones, but it no
     * longer shows that the drone is a target, nor any target". The revival resolved target names
     * against the SHIPS only, so a seeker chasing a seeker came back flying at nothing. And even
     * with seekers in the map it would not have been enough: built in one pass, the first weapon
     * revived could never point at the last. Hence two passes — build them all, then resolve.
     *
     * <p>Third time this shape has appeared in the save format, after lock-ons held on a drone
     * and the ordering between those two steps. Anything that resolves a unit by name has to see
     * every unit, and has to run after they all exist.
     */
    @Test
    public void aDroneAimedAtAnotherDroneKeepsItsTarget() {
        Game before = battleInProgress();
        com.sfb.objects.Drone incoming = (com.sfb.objects.Drone) before.getSeekers().get(0);

        // A second drone, from the Federation ship, chasing the first one.
        com.sfb.objects.Drone interceptor =
                new com.sfb.objects.Drone(incoming.getDroneType());
        interceptor.setName("USS Hood-Drone-1");
        interceptor.setLocation(new com.sfb.properties.Location(10, 12));
        interceptor.setFacing(1);
        interceptor.setSpeed(32);
        interceptor.setTarget(incoming);
        interceptor.setController(shipNamed(before, "USS Hood"));
        interceptor.setLaunchImpulse(before.getClock().getImpulse());
        before.getSeekers().add(interceptor);

        Game after = roundTrip(before);

        assertEquals(2, after.getSeekers().size());
        Seeker restoredInterceptor = after.getSeekers().stream()
                .filter(sk -> sk instanceof com.sfb.objects.Unit u
                        && "USS Hood-Drone-1".equals(u.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("the interceptor was lost"));

        assertNotNull("a drone chasing a drone must not come back chasing nothing",
                restoredInterceptor.getTarget());
        assertEquals("and it must be the OTHER restored seeker, not a ship",
                ((com.sfb.objects.Unit) incoming).getName(),
                restoredInterceptor.getTarget().getName());
        assertTrue("pointing at the restored object, not the saved one",
                after.getSeekers().contains(restoredInterceptor.getTarget()));
    }

    /**
     * A tractor link (G7.0) is a reference between two units, so it resolves in the second pass
     * like a lock-on and a seeker's target.
     *
     * A link that came back dangling would leave the two units disagreeing about their own
     * situation: the holder still showing a beam in use, the held ship free to move away.
     *
     * <p>This is the FOURTH kind of saved reference, after lock-ons, seeker targets and seeker
     * controllers — and the first one not to need its own bespoke lookup, because they now all
     * resolve through one map of every unit. The three before it were each written separately
     * and each got it wrong in the same way.
     */
    @Test
    public void aTractorLinkIsReattachedToTheRestoredShip() {
        Game before = battleInProgress();
        Ship hood = shipNamed(before, "USS Hood");
        Ship blade = shipNamed(before, "IKV Blade");
        assertTrue("fixture: the Hood should get a beam onto the Blade",
                hood.getTractors().linkUnit(blade, before.getClock().getImpulse()));

        Game after = roundTrip(before);

        Ship restoredHood = shipNamed(after, "USS Hood");
        Ship restoredBlade = shipNamed(after, "IKV Blade");
        com.sfb.objects.Tractorable held = null;
        for (com.sfb.systemgroups.TractorBeam beam : restoredHood.getTractors().getBeams())
            if (beam.getHeldUnit() != null)
                held = beam.getHeldUnit();

        assertNotNull("the link must survive the save", held);
        assertSame("and point at the RESTORED Blade, not the saved one", restoredBlade, held);
    }

    // ---------------------------------------------------------------- craft in flight

    /**
     * A launched craft used to vanish on reload, SILENTLY.
     *
     * {@code activeShuttles} was not in the save at all, and unlike a scatter-pack nothing
     * reported it: only craft-based SEEKERS went through the reporting path, so an ordinary
     * fighter simply ceased to exist with nothing said.
     */
    @Test
    public void aCraftInFlightSurvivesASave() {
        Game before = battleInProgress();
        com.sfb.objects.shuttles.Shuttle craft =
                com.sfb.systemgroups.ShuttleBay.buildShuttle("admin", "Hood-Shuttle-1");
        craft.setLocation(new com.sfb.properties.Location(11, 11));
        craft.setFacing(5);
        craft.setCurrentHull(craft.getCurrentHull() - 1);
        before.getActiveShuttles().add(craft);

        Game after = roundTrip(before);

        assertEquals("the craft must still be in flight", 1, after.getActiveShuttles().size());
        com.sfb.objects.shuttles.Shuttle restored = after.getActiveShuttles().get(0);
        assertEquals("Hood-Shuttle-1", restored.getName());
        assertEquals("admin", restored.getCatalogType());
        assertEquals("and keep the damage it had taken",
                craft.getCurrentHull(), restored.getCurrentHull());
        assertEquals(craft.getLocation(), restored.getLocation());
    }

    /**
     * A wild weasel is an admin shuttle PLAYING A PART (J3.0), so its catalogue type is "admin"
     * and the type alone would rebuild an ordinary shuttle with no weasel in it. The saved role
     * is what keeps it a weasel, and its parent ship is final on the class — so it has to be
     * constructed with the parent rather than have one set afterwards.
     */
    @Test
    public void aWildWeaselComesBackAsAWeaselAndKeepsItsShip() {
        Game before = battleInProgress();
        Ship hood = shipNamed(before, "USS Hood");
        com.sfb.objects.shuttles.WildWeaselShuttle weasel =
                new com.sfb.objects.shuttles.WildWeaselShuttle(hood);
        weasel.setName("Hood-Weasel-1");
        weasel.setLocation(new com.sfb.properties.Location(10, 11));
        before.getActiveShuttles().add(weasel);

        Game after = roundTrip(before);

        assertEquals(1, after.getActiveShuttles().size());
        com.sfb.objects.shuttles.Shuttle restored = after.getActiveShuttles().get(0);
        assertTrue("it must still be a weasel, not the admin shuttle it is built from",
                restored instanceof com.sfb.objects.shuttles.WildWeaselShuttle);
        assertSame("and belong to the RESTORED Hood", shipNamed(after, "USS Hood"),
                ((com.sfb.objects.shuttles.WildWeaselShuttle) restored).getParentShip());
    }

    /** A craft this build cannot rebuild is REPORTED, never dropped in silence. */
    @Test
    public void aCraftOfAnUnknownTypeIsReported() {
        Game before = battleInProgress();
        com.sfb.objects.shuttles.Shuttle craft =
                com.sfb.systemgroups.ShuttleBay.buildShuttle("admin", "Hood-Shuttle-1");
        before.getActiveShuttles().add(craft);
        Map<String, Object> saved = before.exportState();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> craftStates =
                (List<Map<String, Object>>) saved.get("activeShuttles");
        craftStates.get(0).put("catalogType", "notacraft");

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());
        List<String> problems = rebuilt.applyState(saved);

        assertTrue("the player must be told: " + problems,
                problems.stream().anyMatch(p -> p.contains("Hood-Shuttle-1")));
        assertTrue(rebuilt.getActiveShuttles().isEmpty());
    }

    // ---------------------------------------------------------------- craft-based seekers

    /** An admin shuttle converted to a suicide shuttle and sent off, added to the battle. */
    private static com.sfb.objects.shuttles.SuicideShuttle suicideShuttleInFlight(Game game,
            String name, int armingTurns) {
        try {
            com.sfb.objects.ShuttleCatalog.loadDefault("../data");
        } catch (Exception e) {
            throw new AssertionError("fixture: the shuttle catalogue should load", e);
        }
        com.sfb.objects.shuttles.Shuttle base =
                com.sfb.systemgroups.ShuttleBay.buildShuttle("admin", name);
        com.sfb.objects.shuttles.SuicideShuttle shuttle =
                new com.sfb.objects.shuttles.SuicideShuttle(base);
        shuttle.setLocation(new com.sfb.properties.Location(11, 11));
        shuttle.setFacing(1);
        for (int i = 0; i < armingTurns; i++)
            assertTrue("fixture: arming turn " + (i + 1) + " should be accepted",
                    shuttle.arm(3));
        game.getSeekers().add(shuttle);
        return shuttle;
    }

    private static <T> T seekerNamed(Game game, String name, Class<T> kind) {
        for (Seeker seeker : game.getSeekers())
            if (seeker instanceof com.sfb.objects.Unit unit && name.equals(unit.getName())
                    && kind.isInstance(seeker))
                return kind.cast(seeker);
        throw new AssertionError(name + " did not survive the save as a " + kind.getSimpleName());
    }

    /**
     * THE TEST THIS SLICE EXISTS FOR: a craft-based seeker used to be REPORTED and dropped.
     *
     * Both kinds wrap a craft, and the old reason for not rebuilding them — that it would mean
     * rebuilding the bay the craft came from — expired when Shuttle.fromState arrived.
     */
    @Test
    public void aSuicideShuttleInFlightSurvivesASave() {
        Game before = battleInProgress();
        com.sfb.objects.shuttles.SuicideShuttle sent =
                suicideShuttleInFlight(before, "Hood-Suicide-1", 1);

        Game after = roundTrip(before);

        com.sfb.objects.shuttles.SuicideShuttle restored =
                seekerNamed(after, "Hood-Suicide-1",
                        com.sfb.objects.shuttles.SuicideShuttle.class);
        assertEquals("it must still be in the same hex", sent.getLocation(),
                restored.getLocation());
        assertEquals("and remember what it was built from (J3.18)",
                sent.getCatalogType(), restored.getCatalogType());
    }

    /**
     * J3.0: a suicide shuttle arms over three turns, and the arming state IS the weapon.
     *
     * Dropping it would turn a loaded weapon back into a bus — or, the other way round, let a
     * half-armed shuttle detonate for a full warhead.
     */
    @Test
    public void aSuicideShuttleKeepsHowFarItIsArmed() {
        Game before = battleInProgress();
        com.sfb.objects.shuttles.SuicideShuttle sent =
                suicideShuttleInFlight(before, "Hood-Suicide-1", 2);
        assertEquals("fixture: two turns of arming", 2, sent.getArmingTurnsComplete());
        assertFalse("fixture: and therefore not yet fully armed", sent.isFullyArmed());
        int energy = sent.getTotalEnergy();
        assertTrue("fixture: it should have energy in it", energy > 0);

        com.sfb.objects.shuttles.SuicideShuttle restored =
                seekerNamed(roundTrip(before), "Hood-Suicide-1",
                        com.sfb.objects.shuttles.SuicideShuttle.class);

        assertEquals("the arming turns must survive", 2, restored.getArmingTurnsComplete());
        assertEquals("and the energy put into it", energy, restored.getTotalEnergy());
        assertFalse("so it is still not fully armed", restored.isFullyArmed());
    }

    /** A fully armed shuttle owes its hold cost each turn, and a reload must not forgive it. */
    @Test
    public void aFullyArmedSuicideShuttleStillOwesItsHold() {
        Game before = battleInProgress();
        com.sfb.objects.shuttles.SuicideShuttle sent =
                suicideShuttleInFlight(before, "Hood-Suicide-1", 3);
        assertTrue("fixture: three turns means fully armed", sent.isFullyArmed());
        sent.payHold();
        assertTrue("fixture: the hold is paid for this turn", sent.isUpkeepPaid());

        assertTrue("a reload must not un-pay the hold",
                seekerNamed(roundTrip(before), "Hood-Suicide-1",
                        com.sfb.objects.shuttles.SuicideShuttle.class).isUpkeepPaid());
    }

    /**
     * A scatter-pack's PAYLOAD is ammunition, and the save used to write only a count.
     *
     * Six type-I drones and six type-VI drones are both "6", so a count would have rebuilt the
     * pack with whatever the default happened to be. FD7.21 even makes the pack's spaces depend
     * on what is loaded, so the drones have to travel as drones.
     */
    @Test
    public void aScatterPackKeepsTheDronesAboardIt() {
        Game before = battleInProgress();
        com.sfb.objects.Drone sample = (com.sfb.objects.Drone) before.getSeekers().get(0);
        com.sfb.objects.shuttles.Shuttle base =
                com.sfb.systemgroups.ShuttleBay.buildShuttle("admin", "Hood-Scatter-1");
        com.sfb.objects.shuttles.ScatterPack pack =
                new com.sfb.objects.shuttles.ScatterPack(base);
        pack.setLocation(new com.sfb.properties.Location(11, 12));
        for (int i = 0; i < 3; i++)
            assertTrue("fixture: drone " + i + " should load",
                    pack.addDrone(new com.sfb.objects.Drone(sample.getDroneType())));
        before.getSeekers().add(pack);

        com.sfb.objects.shuttles.ScatterPack restored =
                seekerNamed(roundTrip(before), "Hood-Scatter-1",
                        com.sfb.objects.shuttles.ScatterPack.class);

        assertEquals("all three drones must still be aboard", 3, restored.getPayload().size());
        assertEquals("and be the drones that were loaded, not a default",
                sample.getDroneType(), restored.getPayload().get(0).getDroneType());
    }

    /**
     * FD7.3: a drone loaded this turn is available the NEXT one, so the pending payload is a
     * separate list. Collapsing the two would hand the player a pack ready to release early.
     */
    @Test
    public void aScatterPacksPendingPayloadStaysPending() {
        Game before = battleInProgress();
        com.sfb.objects.Drone sample = (com.sfb.objects.Drone) before.getSeekers().get(0);
        com.sfb.objects.shuttles.ScatterPack pack = new com.sfb.objects.shuttles.ScatterPack(
                com.sfb.systemgroups.ShuttleBay.buildShuttle("admin", "Hood-Scatter-1"));
        pack.setLocation(new com.sfb.properties.Location(11, 12));
        assertTrue("fixture: it should load as pending",
                pack.addPendingDrone(new com.sfb.objects.Drone(sample.getDroneType())));
        before.getSeekers().add(pack);

        com.sfb.objects.shuttles.ScatterPack restored =
                seekerNamed(roundTrip(before), "Hood-Scatter-1",
                        com.sfb.objects.shuttles.ScatterPack.class);

        assertEquals("the pending drone must still be pending",
                1, restored.getPendingPayload().size());
        assertTrue("and must not have become available early",
                restored.getPayload().isEmpty());
    }

    /**
     * And its references resolve like any other seeker's.
     *
     * This one would have broken quietly: the controller was restored through a chain of
     * instanceof tests that knew only Drone and PlasmaTorpedo, so the moment these kinds became
     * restorable they would have come back unguided. It goes through the Seeker interface now.
     */
    @Test
    public void aCraftBasedSeekerKeepsItsTargetAndController() {
        Game before = battleInProgress();
        com.sfb.objects.shuttles.SuicideShuttle sent =
                suicideShuttleInFlight(before, "Hood-Suicide-1", 1);
        sent.setTarget(shipNamed(before, "IKV Blade"));
        sent.setController(shipNamed(before, "USS Hood"));

        Game after = roundTrip(before);
        com.sfb.objects.shuttles.SuicideShuttle restored =
                seekerNamed(after, "Hood-Suicide-1",
                        com.sfb.objects.shuttles.SuicideShuttle.class);

        assertSame("the target must be the restored Blade",
                shipNamed(after, "IKV Blade"), restored.getTarget());
        assertSame("and the controller the restored Hood",
                shipNamed(after, "USS Hood"), restored.getController());
    }

    // ---------------------------------------------------------------- seeker control

    /**
     * A ship keeps guiding the drone it launched (D6.11), and keeps the channel it spent.
     *
     * The LIST is what travels; {@code controlUsed} is a count kept in lockstep with it and is
     * rebuilt rather than saved, because two records of one fact is how a weapon's firing
     * history came to contradict itself. So this asserts both halves: the channel is spent, and
     * it is spent on the RESTORED drone rather than on a name that no longer means anything.
     */
    @Test
    public void aShipKeepsGuidingTheDroneItLaunched() {
        Game before = battleInProgress();
        Ship blade = shipNamed(before, "IKV Blade");
        assertEquals("fixture: the Blade should be guiding its drone", 1,
                blade.getSpecialFunctions().getControlUsed());

        Game after = roundTrip(before);
        Ship restoredBlade = shipNamed(after, "IKV Blade");

        assertEquals("the channel must still be spent (J4.25 counts these)",
                1, restoredBlade.getSpecialFunctions().getControlUsed());
        assertEquals(1, restoredBlade.getSpecialFunctions().getControlledSeekers().size());
        assertSame("and on the RESTORED drone",
                after.getSeekers().get(0),
                restoredBlade.getSpecialFunctions().getControlledSeekers().get(0));
    }

    /**
     * A ship that loses its sensors mid-battle must come back having lost them.
     *
     * Pinned here as well as in ShipSystemsSaveTest because this is the level where it bites:
     * the sensor rating sets the control limit, so a reload that repaired the track would also
     * quietly restore channels the battle had taken away.
     */
    @Test
    public void aShipDoesNotGetItsSensorsBack() {
        Game before = battleInProgress();
        Ship hood = shipNamed(before, "USS Hood");
        int full = hood.getSpecialFunctions().getSensor();
        while (hood.getSpecialFunctions().getSensor() == full
                && hood.getSpecialFunctions().damageSensor()) { /* keep hitting it */ }
        int hurt = hood.getSpecialFunctions().getSensor();
        assertTrue("fixture: the rating should have dropped", hurt < full);

        assertEquals("the sensors must stay shot out", hurt,
                shipNamed(roundTrip(before), "USS Hood").getSpecialFunctions().getSensor());
    }

    // ---------------------------------------------------------------- mines

    private static com.sfb.objects.SpaceMine theMine(Game game) {
        assertEquals("fixture: one mine on the map", 1, game.getMines().size());
        return game.getMines().get(0);
    }

    /**
     * THE TEST THIS SLICE EXISTS FOR: a laid minefield used to vanish, in SILENCE.
     *
     * {@code Game.mines} never reached {@code exportState}, so a player who spent the energy and
     * the impulses to lay a field came back to open space with nothing said, and the enemy
     * sailed through it. Every other loss in this format either survives or reports itself.
     */
    @Test
    public void aLaidMineIsStillOnTheMapAfterAReload() {
        Game before = battleInProgress();
        com.sfb.objects.SpaceMine laid = theMine(before);

        com.sfb.objects.SpaceMine restored = theMine(roundTrip(before));

        assertEquals("it must be in the same hex", laid.getLocation(), restored.getLocation());
        assertEquals(laid.getMineType(), restored.getMineType());
        assertEquals("and laid the same way, because that decides how it arms (M2.34/M3.223)",
                laid.getPlacementMethod(), restored.getPlacementMethod());
        assertEquals(laid.getPlacedOnImpulse(), restored.getPlacedOnImpulse());
    }

    /**
     * M3.223: a transporter mine arms two impulses after it is laid, and the arming state is the
     * difference between a mine that goes off and one that does not.
     *
     * Dropping it would be wrong in both directions. An armed mine coming back unarmed gives the
     * enemy a free pass through the hex; an unarmed one coming back armed detonates under a ship
     * that was entitled to be there.
     */
    @Test
    public void anArmedMineComesBackArmed() {
        Game before = battleInProgress();
        com.sfb.objects.SpaceMine laid = theMine(before);
        laid.tryActivate(laid.getPlacedOnImpulse() + 2, 9);
        assertTrue("fixture: it should have armed", laid.isActive());

        assertTrue("an armed mine must still be armed", theMine(roundTrip(before)).isActive());
    }

    @Test
    public void anUnarmedMineComesBackUnarmed() {
        Game before = battleInProgress();
        assertFalse("fixture: freshly laid, so not yet armed", theMine(before).isActive());

        assertFalse("and a reload must not arm it early (M3.223)",
                theMine(roundTrip(before)).isActive());
    }

    /**
     * M3.224: a dummy is a bluff, and the bluff has to survive the save.
     *
     * A dummy that came back REAL would detonate where the player had spent nothing and intended
     * nothing; a real one that came back a dummy would be a weapon quietly disarmed. The save
     * holds the truth because it is a server-side file, and MineDto still shows a client only
     * active and revealed, so reloading leaks nothing about which it is.
     */
    @Test
    public void aDummyStaysADummyAndKeepsItsCover() {
        Game before = battleInProgress();
        before.getMines().clear();
        Ship hood = shipNamed(before, "USS Hood");
        com.sfb.objects.SpaceMine dummy = com.sfb.objects.SpaceMine.createTBomb(
                hood, before.getClock().getImpulse(), false, false);
        dummy.setLocation(new com.sfb.properties.Location(10, 13));
        before.getMines().add(dummy);
        assertFalse("fixture: it is a dummy", dummy.isReal());
        assertFalse("fixture: and has not been found out", dummy.isRevealed());

        com.sfb.objects.SpaceMine restored = theMine(roundTrip(before));

        assertFalse("a dummy must not come back live", restored.isReal());
        assertFalse("and its cover must hold", restored.isRevealed());
    }

    /** Once a dummy has been exposed it stays exposed — the bluff does not reset. */
    @Test
    public void anExposedDummyStaysExposed() {
        Game before = battleInProgress();
        before.getMines().clear();
        com.sfb.objects.SpaceMine dummy = com.sfb.objects.SpaceMine.createTBomb(
                shipNamed(before, "USS Hood"), before.getClock().getImpulse(), false, false);
        dummy.setLocation(new com.sfb.properties.Location(10, 13));
        dummy.reveal();
        before.getMines().add(dummy);

        assertTrue("a reload must not restore the bluff",
                theMine(roundTrip(before)).isRevealed());
    }

    /** Its layer is a reference like any other, and must point at the RESTORED ship. */
    @Test
    public void aMineKnowsWhichRestoredShipLaidIt() {
        Game before = battleInProgress();
        assertNotNull("fixture: the Hood laid it", theMine(before).getLayingShip());

        Game after = roundTrip(before);

        assertSame("the layer must be the restored Hood, not the saved one",
                shipNamed(after, "USS Hood"), theMine(after).getLayingShip());
    }

    /**
     * A minefield OUTLIVES the ship that laid it, and that is not a fault in the save.
     *
     * The layer is left null and nothing is reported, because MineResolver already reads a null
     * layer as a range of Integer.MAX_VALUE — "not nearby" — which is exactly right when that
     * ship is dead. M2.34 then lets a dropped mine arm, which is the correct outcome.
     */
    @Test
    public void aMineOutlivesTheShipThatLaidIt() {
        Game before = battleInProgress();
        Ship hood = shipNamed(before, "USS Hood");
        assertSame("fixture", hood, theMine(before).getLayingShip());
        before.getShips().remove(hood);
        before.getDestroyedShips().add(hood);

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());
        List<String> problems = rebuilt.applyState(before.exportState());

        assertEquals("the mine must still be there", 1, rebuilt.getMines().size());
        assertNull("with no layer, which is lawful",
                rebuilt.getMines().get(0).getLayingShip());
        assertTrue("and nothing to report about it: " + problems,
                problems.stream().noneMatch(p -> p.toLowerCase().contains("mine")));
    }

    /** A mine of a type this build does not have is REPORTED, never dropped in silence. */
    @Test
    public void aMineOfAnUnknownTypeIsReported() {
        Game before = battleInProgress();
        Map<String, Object> saved = before.exportState();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mineStates = (List<Map<String, Object>>) saved.get("mines");
        assertEquals("fixture: the mine must be IN the save", 1, mineStates.size());
        mineStates.get(0).put("mineType", "ANTIMATTER_CHARGE");

        Game rebuilt = new Game();
        rebuilt.setupFromScenario(spec());
        List<String> problems = rebuilt.applyState(saved);

        assertTrue("the player must be told: " + problems,
                problems.stream().anyMatch(p -> p.contains("ANTIMATTER_CHARGE")));
        assertTrue(rebuilt.getMines().isEmpty());
    }

    /**
     * A ship's stock of UNLAID transporter bombs is ammunition, and the hull declares it.
     *
     * So a save that left it out rebuilt the stock from the JSON and handed back every bomb the
     * battle had spent — the drone-rack mistake in a smaller package. Found by the fixture above
     * laying a mine: it had three, spent one, and the reload reported two against zero.
     */
    @Test
    public void aShipDoesNotGetItsSpentTransporterBombsBack() {
        Game before = battleInProgress();
        Ship hood = shipNamed(before, "USS Hood");
        int left = hood.getTBombs();
        assertEquals("fixture: it should have spent one of three", 2, left);
        hood.setDummyTBombs(1);

        Ship after = shipNamed(roundTrip(before), "USS Hood");

        assertEquals("the spent bomb must stay spent", left, after.getTBombs());
        assertEquals("and the dummies too, because M3.224 makes the count the secret",
                1, after.getDummyTBombs());
    }

    // ------------------------------------------------- what a craft in flight is holding

    /**
     * A fighter in flight, already in the game, with the catalogue loaded for it.
     *
     * An F-18 rather than a stinger, because the stinger carries no drone rails and a fighter
     * with no rails has a control capacity of zero by J4.25 — a correct refusal that would have
     * made the control tests below untestable rather than failing.
     */
    private static com.sfb.objects.shuttles.Fighter fighterInFlight(Game game, String name) {
        try {
            com.sfb.objects.ShuttleCatalog.loadDefault("../data");
        } catch (Exception e) {
            throw new AssertionError("fixture: the shuttle catalogue should load", e);
        }
        com.sfb.objects.shuttles.Fighter fighter =
                com.sfb.objects.shuttles.CataloguedFighter.of("f18");
        fighter.setName(name);
        fighter.setLocation(new com.sfb.properties.Location(11, 11));
        fighter.setFacing(1);
        game.getActiveShuttles().add(fighter);
        return fighter;
    }

    private static com.sfb.objects.shuttles.Fighter restoredFighter(Game game, String name) {
        for (com.sfb.objects.shuttles.Shuttle craft : game.getActiveShuttles())
            if (name.equals(craft.getName())
                    && craft instanceof com.sfb.objects.shuttles.Fighter fighter)
                return fighter;
        throw new AssertionError(name + " did not survive the save");
    }

    /**
     * A fighter holds a lock-on of its own (D6.121), and it must come back pointing at the
     * RESTORED ship rather than dangling.
     *
     * The names were written into the save by slice 1 and read by nothing, so a fighter returned
     * from a reload unable to fire at the ship it had been locked onto all battle.
     */
    @Test
    public void aFightersLockOnPointsAtTheRestoredShip() {
        Game before = battleInProgress();
        com.sfb.objects.shuttles.Fighter fighter = fighterInFlight(before, "Hood-F18-1");
        fighter.addLockOn(shipNamed(before, "IKV Blade"));

        Game after = roundTrip(before);

        com.sfb.objects.shuttles.Fighter restored = restoredFighter(after, "Hood-F18-1");
        assertEquals("the lock must survive the save", 1, restored.getLockOns().size());
        assertSame("and point at the RESTORED Blade, not the saved one",
                shipNamed(after, "IKV Blade"), restored.getLockOns().iterator().next());
    }

    /**
     * A drone flown by a fighter (J4.222) keeps its pilot, and the pilot keeps the channel.
     *
     * <h2>Both halves were broken, for different reasons</h2>
     * The drone lost its controller because of ORDER: slice 2 revived craft after the seekers
     * had already resolved their references, so the fighter did not exist at the moment the
     * drone went looking for it. The name was in the save; there was nothing of that name to
     * find. The fighter lost the channel because nothing read its saved list back at all.
     */
    @Test
    public void aDroneFlownByAFighterKeepsItsPilotAndTheFighterKeepsTheChannel() {
        Game before = battleInProgress();
        com.sfb.objects.shuttles.Fighter fighter = fighterInFlight(before, "Hood-F18-1");
        com.sfb.objects.Drone flown = droneFrom(before, "Hood-F18-1-Drone-1");
        flown.setController(fighter);
        assertTrue("fixture: the fighter should have a channel free",
                fighter.acquireControl(flown));

        Game after = roundTrip(before);

        com.sfb.objects.shuttles.Fighter restored = restoredFighter(after, "Hood-F18-1");
        com.sfb.objects.Drone restoredDrone = droneNamed(after, "Hood-F18-1-Drone-1");
        assertSame("the drone must be flown by the RESTORED fighter",
                restored, restoredDrone.getController());
        assertEquals("and the fighter must still be spending that channel (J4.25)",
                1, restored.getControlledSeekers().size());
        assertSame(restoredDrone, restored.getControlledSeekers().get(0));
    }

    /**
     * A SELF-GUIDING drone names the fighter as its controller but spends none of its channels,
     * because the launch path short-circuits past {@code acquireControl} entirely.
     *
     * That asymmetry is why the save carries both records instead of deriving the fighter's list
     * from the seekers: deriving it would hand back a channel that was never spent, and J4.25's
     * capacity is the whole point of counting them.
     */
    @Test
    public void aSelfGuidingDroneDoesNotGiveTheFighterBackAChannel() {
        Game before = battleInProgress();
        com.sfb.objects.shuttles.Fighter fighter = fighterInFlight(before, "Hood-F18-1");
        com.sfb.objects.Drone loose = droneFrom(before, "Hood-F18-1-Drone-2");
        loose.setDroneType(com.sfb.objects.DroneType.TypeIII);
        loose.setController(fighter);
        assertEquals("fixture: no channel is spent on a self-guiding drone",
                0, fighter.getControlledSeekers().size());

        Game after = roundTrip(before);

        com.sfb.objects.shuttles.Fighter restored = restoredFighter(after, "Hood-F18-1");
        assertSame("it still answers to the fighter",
                restored, droneNamed(after, "Hood-F18-1-Drone-2").getController());
        assertEquals("but the fighter gets no channel back",
                0, restored.getControlledSeekers().size());
    }

    /** A drone in flight, built like the fixture's, added to the game. */
    private static com.sfb.objects.Drone droneFrom(Game game, String name) {
        com.sfb.objects.Drone existing = (com.sfb.objects.Drone) game.getSeekers().get(0);
        com.sfb.objects.Drone drone = new com.sfb.objects.Drone(existing.getDroneType());
        drone.setName(name);
        drone.setLocation(new com.sfb.properties.Location(11, 12));
        drone.setFacing(1);
        drone.setSpeed(32);
        drone.setTarget(shipNamed(game, "IKV Blade"));
        drone.setLaunchImpulse(game.getClock().getImpulse());
        game.getSeekers().add(drone);
        return drone;
    }

    private static com.sfb.objects.Drone droneNamed(Game game, String name) {
        for (Seeker seeker : game.getSeekers())
            if (seeker instanceof com.sfb.objects.Drone drone && name.equals(drone.getName()))
                return drone;
        throw new AssertionError(name + " did not survive the save");
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

        // And a T-bomb on the map, for the same reason: mines persist across turns, and until
        // this they were not in the save AT ALL. Laid through the real transporter path rather
        // than constructed, so the fixture cannot quietly create a state the game cannot.
        hood.getTransporters().bankEnergy(1.0);
        hood.setTBombs(3);
        Game.ActionResult laid = game.placeTBomb(hood,
                new com.sfb.properties.Location(10, 12), true);
        assertTrue("fixture: the T-bomb must actually be laid, or the mine gap is invisible — "
                + laid.getMessage(), laid.isSuccess());
        assertEquals("fixture: there should be a mine on the map", 1, game.getMines().size());

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
