package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game.ActionResult;
import com.sfb.objects.BallisticCourse;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.KzintiShips;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.DroneRack;

/**
 * Ballistic targeting (F4.0): a seeking weapon aimed at a HEX rather than a unit.
 * <p>
 * "That is, it is not aimed at a target unit but in a specific direction or at a specific
 * (vacant) point in space." The weapon pursues the hex by the most nearly direct route (F2.2),
 * and on arrival evades it (C1.3223) instead of exploding — because a ballistic weapon "will
 * normally never explode" (F4.21). The uses the rule gives are detecting a minefield, clearing
 * a path through asteroids, saturating defences, and deception.
 * <p>
 * The thing under test is really the SEAM. Before this, every mover asked a seeker for its
 * target UNIT and took that unit's location; four movers, four copies, and a hex is not a unit.
 * They ask {@link com.sfb.objects.Seeker#destination()} instead, and a ballistic weapon answers
 * with its hex while a pursuing one answers with its target's current hex. Nothing downstream
 * knows which kind it is moving.
 */
public class BallisticTargetingTest {

    private Game game;
    private Ship kzinti;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();

        kzinti = new Ship();
        kzinti.init(KzintiShips.getKzinBC());
        kzinti.setName("KHS Quasar");
        kzinti.setLocation(new Location(10, 20));
        kzinti.setFacing(1);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(10, 10));
        klingon.setFacing(13);

        game.getShips().add(kzinti);
        game.getShips().add(klingon);

        kzinti.setActiveFireControl(true);

        game.advancePhase();
        assertEquals("fixture: launches happen in the Activity phase",
                Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    private DroneRack rack() {
        return kzinti.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(w -> (DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("the BC should carry racks"));
    }

    /** Launch ballistically at a hex, and hand back the drone that went out. */
    private Drone launchAt(Location hex) {
        int before = game.getSeekers().size();
        ActionResult r = game.launchDroneBallistic(kzinti, hex, rack());
        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("exactly one seeker launched", before + 1, game.getSeekers().size());
        Drone drone = (Drone) game.getSeekers().get(game.getSeekers().size() - 1);
        drone.setSpeed(32);   // moves every impulse, so the test can count hexes
        return drone;
    }

    private int totalShields(Ship ship) {
        int total = 0;
        for (int s = 1; s <= ship.getShields().getShieldCount(); s++)
            total += ship.getShields().getShieldStrength(s);
        return total;
    }

    /** Run one whole impulse, which moves the seekers once. */
    private void impulse() {
        for (int i = 0; i < 20; i++) {
            boolean wasMovement = game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT;
            game.advancePhase();
            if (wasMovement)
                return;
        }
        fail("fixture: never completed a movement phase");
    }

    // ------------------------------------------------------------------ the seam

    /** The whole point: one question, two kinds of answer, and no caller the wiser. */
    @Test
    public void destinationIsTheHexWhenBallisticAndTheTargetWhenNot() {
        Drone pursuing = new Drone(DroneType.TypeI);
        pursuing.setTarget(klingon);
        assertEquals("a pursuing drone steers for its target's CURRENT hex",
                klingon.getLocation(), pursuing.destination());

        klingon.setLocation(new Location(11, 11));
        assertEquals("and keeps up when the target moves",
                new Location(11, 11), pursuing.destination());

        Drone ballistic = new Drone(DroneType.TypeI);
        ballistic.setBallisticCourse(new BallisticCourse(new Location(4, 4)));
        assertTrue(ballistic.isBallistic());
        assertNull("a ballistic weapon has no target unit (F4.12)", ballistic.getTarget());
        assertEquals(new Location(4, 4), ballistic.destination());
    }

    /** A drone with neither is going nowhere, and the movers destroy it. */
    @Test
    public void destinationIsNullForAnOrphan() {
        Drone orphan = new Drone(DroneType.TypeI);
        assertNull(orphan.destination());
        assertFalse(orphan.isBallistic());
    }

    // ------------------------------------------------------------------ flight

    @Test
    public void aBallisticDroneFliesToItsHex() {
        Location aim = new Location(10, 16);        // four hexes up-range
        Drone drone = launchAt(aim);

        for (int i = 0; i < 4; i++)
            impulse();

        assertEquals("it pursued the hex by the most direct route (F4.11/F2.2)",
                aim, drone.getLocation());
    }

    /**
     * F4.21/F4.412. The hex is occupied by an enemy ship, which is exactly the case the rule
     * is about: a ballistic weapon can "never hit ships or bases in space".
     */
    @Test
    public void itDoesNotExplodeOnAShipStandingInThatHex() {
        klingon.setLocation(new Location(10, 16));
        int shieldsBefore = totalShields(klingon);

        Drone drone = launchAt(new Location(10, 16));
        for (int i = 0; i < 5; i++)
            impulse();

        assertEquals("the ship took nothing (F4.412)", shieldsBefore, totalShields(klingon));
        assertTrue("and the drone is still flying (F4.21)", game.getSeekers().contains(drone));
    }

    /**
     * F4.11: on reaching the hex it "evades" it (C1.3223) — which means getting further away
     * every impulse, not circling back for another pass.
     */
    @Test
    public void onReachingTheHexItEvadesAndKeepsGoing() {
        Location aim = new Location(10, 16);
        Drone drone = launchAt(aim);

        for (int i = 0; i < 4; i++)
            impulse();
        assertEquals(aim, drone.getLocation());
        assertTrue("arrival starts the evasion", drone.getBallisticCourse().isEvading());

        int previous = 0;
        for (int i = 0; i < 3; i++) {
            impulse();
            int range = MapUtils.getRange(drone.getLocation(), aim);
            assertTrue("impulse " + i + ": evasion must open the range (C1.3223), was "
                    + previous + " now " + range, range > previous);
            previous = range;
        }
    }

    // ------------------------------------------------------------------ what it does not need

    /**
     * F4.12: "released from control immediately on launch and never count against the control
     * limit of the launching unit."
     */
    @Test
    public void itTakesNoControlChannel() {
        int before = kzinti.getControlledSeekers().size();

        Drone drone = launchAt(new Location(10, 16));

        assertEquals("no channel spent (F4.12)", before, kzinti.getControlledSeekers().size());
        assertNull("and nobody is guiding it", drone.getController());
    }

    /**
     * D6.121 wants a lock-on to the target; there is no target. D19.224 settles it: "a ship on
     * active or passive fire control could use ballistic targeting (F4.0) to launch seeking
     * weapons."
     */
    @Test
    public void itNeedsNoLockOn() {
        assertFalse("fixture: no lock-on to anything", kzinti.hasLockOn(klingon));

        assertTrue(game.launchDroneBallistic(kzinti, new Location(10, 16), rack()).isSuccess());
    }

    /** D19.22: and it works under passive fire control, where a guided drone could not go. */
    @Test
    public void itWorksUnderPassiveFireControl() {
        kzinti.setActiveFireControl(false);

        ActionResult r = game.launchDroneBallistic(kzinti, new Location(10, 16), rack());

        assertTrue(r.getMessage(), r.isSuccess());
    }

    /** It has no guidance to lose, so the orphan sweep must not take it. */
    @Test
    public void itIsNeverOrphaned() {
        Drone drone = launchAt(new Location(10, 16));

        for (int i = 0; i < 6; i++)
            impulse();

        assertTrue("a ballistic drone is uncontrolled by design, not by fault (F4.12)",
                game.getSeekers().contains(drone));
    }

    // ------------------------------------------------------------------ refusals

    @Test
    public void refusesAHexOffTheMap() {
        ActionResult r = game.launchDroneBallistic(kzinti, new Location(0, 0), rack());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("F4.11"));
    }

    /** G7.943: a tractored ship may launch only at the holding ship, which is not a hex. */
    @Test
    public void refusesWhileTractored() {
        klingon.setLocation(new Location(10, 19));            // adjacent, so it can grab
        klingon.setActiveFireControl(true);
        klingon.addLockOn(kzinti);                            // G7.412
        ActionResult grab = game.establishTractor(klingon, "KHS Quasar", 1);
        assertTrue("fixture: " + grab.getMessage(), grab.isSuccess());
        // The grab opens an auction (G7.4); the defender declining to outbid settles it.
        game.submitNegativeTractorBid(kzinti, 0);
        assertTrue("fixture: the Kzinti must actually be held", kzinti.isTractored());

        ActionResult r = game.launchDroneBallistic(kzinti, new Location(10, 16), rack());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("G7.943"));
    }

    // ------------------------------------------------------------------ F4.11's anti-loop clause

    /**
     * "If the ballistic unit enters a third hex adjacent to the target hex without having
     * entered the target hex, it will begin evading the target hex from that point."
     */
    @Test
    public void threeAdjacentHexesBeginTheEvasion() {
        BallisticCourse course = new BallisticCourse(new Location(10, 10));

        // Neighbours come from MapUtils, never from a hand-written (x+1, y): the grid is
        // offset and column-parity-sensitive, so "the hex to the upper right" is not the
        // same arithmetic in an odd column as in an even one.
        Location centre = new Location(10, 10);
        assertFalse(course.noteHexEntered(MapUtils.getAdjacentHex(centre, 1)));
        assertFalse(course.noteHexEntered(MapUtils.getAdjacentHex(centre, 5)));
        assertFalse("two is not three", course.isEvading());

        assertTrue(course.noteHexEntered(MapUtils.getAdjacentHex(centre, 9)));
        assertTrue(course.isEvading());
    }

    /** A SET of hexes, not a tally: the same hex three times is one hex. */
    @Test
    public void theSameAdjacentHexThreeTimesIsNotThreeHexes() {
        BallisticCourse course = new BallisticCourse(new Location(10, 10));

        Location neighbour = MapUtils.getAdjacentHex(new Location(10, 10), 1);
        for (int i = 0; i < 3; i++)
            course.noteHexEntered(neighbour);

        assertFalse(course.isEvading());
        assertEquals(1, course.getAdjacentHexesEntered());
    }

    /** Distant hexes are not adjacent and must not count towards the three. */
    @Test
    public void onlyAdjacentHexesCount() {
        BallisticCourse course = new BallisticCourse(new Location(10, 10));

        course.noteHexEntered(new Location(10, 5));
        course.noteHexEntered(new Location(2, 2));
        course.noteHexEntered(new Location(20, 20));

        assertFalse(course.isEvading());
        assertEquals(0, course.getAdjacentHexesEntered());
    }

    /** Arriving is the ordinary way it ends, and it ends there even from an adjacent hex. */
    @Test
    public void enteringTheTargetHexBeginsTheEvasion() {
        BallisticCourse course = new BallisticCourse(new Location(10, 10));

        course.noteHexEntered(MapUtils.getAdjacentHex(new Location(10, 10), 1));
        assertTrue(course.noteHexEntered(new Location(10, 10)));
        assertTrue(course.isEvading());
    }

    @Test
    public void aCourseMustNameAHex() {
        try {
            new BallisticCourse(null);
            fail("a ballistic course with no hex is not a course (F4.11)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("F4.11"));
        }
    }
}
