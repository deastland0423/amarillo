package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.KzintiShips;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.DroneRack;

/**
 * A unit may only face one of six directions — 1, 5, 9, 13, 17, 21 — and a seeking weapon is
 * no exception (owner's ruling 2026-09-21).
 * <p>
 * All three launch paths broke this the same way: the facing came from MapUtils.getBearing,
 * which answers in TWENTY-FOUR directions. A drone launched at a target on bearing 3 was
 * created facing 3, which nothing in the game can face. And a named direction was not checked
 * at all, so any of the 24 went through.
 * <p>
 * The distinction worth holding on to: a BEARING is where something lies, in 24 directions; a
 * FACING is where something points, in six. Converting one to the other is a snap, not a cast.
 */
public class SeekerFacingTest {

    private Game game;
    private Ship kzinti;
    private Ship target;

    @Before
    public void setUp() {
        game = new Game();

        kzinti = new Ship();
        kzinti.init(KzintiShips.getKzinBC());
        kzinti.setName("KHS Quasar");
        kzinti.setLocation(new Location(10, 10));
        kzinti.setFacing(1);

        target = new Ship();
        target.init(KlingonShips.getD7());
        target.setName("IKV Saber");
        target.setLocation(new Location(10, 6));
        target.setFacing(13);

        game.getShips().add(kzinti);
        game.getShips().add(target);

        kzinti.setActiveFireControl(true);
        kzinti.addLockOn(target);

        game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    private DroneRack rack() {
        return kzinti.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(w -> (DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("the BC should carry racks"));
    }

    private ActionResult launchOn(int facing) {
        DroneRack r = rack();
        return game.launchDrone(kzinti, target, r, r.getAmmo().get(0), facing);
    }

    // ---------------------------------------------------------------- the conversion

    @Test
    public void theSixFacings_areTheSixHexSides() {
        assertArrayEquals(new int[] { 1, 5, 9, 13, 17, 21 }, MapUtils.FACINGS);
        for (int f : MapUtils.FACINGS)
            assertTrue("facing " + f, MapUtils.isFacing(f));
    }

    @Test
    public void everyOtherDirection_isNotAFacing() {
        for (int d = 1; d <= 24; d++) {
            boolean cardinal = d == 1 || d == 5 || d == 9 || d == 13 || d == 17 || d == 21;
            assertEquals("direction " + d, cardinal, MapUtils.isFacing(d));
        }
    }

    @Test
    public void aBearingSnapsToTheNearestFacing() {
        assertEquals(1, MapUtils.snapToFacing(1));
        assertEquals(1, MapUtils.snapToFacing(2));
        assertEquals(5, MapUtils.snapToFacing(4));
        assertEquals(5, MapUtils.snapToFacing(6));
        assertEquals(21, MapUtils.snapToFacing(20));
        assertEquals(21, MapUtils.snapToFacing(22));
    }

    /** Halfway between two facings, the lower wins — including across the wrap at 24/1. */
    @Test
    public void aBearingExactlyBetweenTwoFacings_takesTheLower() {
        assertEquals(1, MapUtils.snapToFacing(3));    // between 1 and 5
        assertEquals(5, MapUtils.snapToFacing(7));    // between 5 and 9
        assertEquals(1, MapUtils.snapToFacing(23));   // between 21 and 1, wrapping
    }

    @Test
    public void thereIsNothingToSnapWithoutABearing() {
        assertEquals("same hex: no bearing exists", 0, MapUtils.snapToFacing(0));
    }

    // ---------------------------------------------------------------- at launch

    @Test
    public void aNamedDirectionThatIsNotAFacing_isRefused() {
        assertFalse("premise: 3 is not a facing", MapUtils.isFacing(3));

        ActionResult r = launchOn(3);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("not one of the six"));
    }

    @Test
    public void aNamedFacingIsAccepted() {
        assertTrue("premise: 1 is a facing", MapUtils.isFacing(1));

        ActionResult r = launchOn(1);

        assertTrue(r.getMessage(), r.isSuccess());
    }

    /**
     * The one that was silently wrong: with no direction named, the facing came straight from
     * the bearing. Whatever hex the target is in, what launches must face one of six.
     */
    @Test
    public void withNoDirectionNamed_theLaunchedSeekerStillFacesOneOfSix() {
        for (int col = 9; col <= 12; col++) {
            for (int row = 7; row <= 9; row++) {
                setUp();                                   // a fresh rack and game each time
                target.setLocation(new Location(col, row));

                ActionResult r = launchOn(0);
                if (!r.isSuccess())
                    continue;                              // outside an arc; not this test's business

                int facing = game.getSeekers().stream()
                        .filter(s -> s instanceof com.sfb.objects.Drone)
                        .map(s -> ((com.sfb.objects.Drone) s).getFacing())
                        .findFirst().orElseThrow(() -> new AssertionError("no drone launched"));
                assertTrue("a drone launched at <" + col + "|" + row + "> faces " + facing
                        + ", which is not one of the six", MapUtils.isFacing(facing));
            }
        }
    }
}
