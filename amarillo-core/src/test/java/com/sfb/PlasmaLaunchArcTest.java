package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.RomulanShips;
import com.sfb.weapons.PlasmaLauncher;

/**
 * A plasma launcher is bounded two different ways, and they are not the same arc.
 * <p>
 * Its FIRING arc limits what it may target. Its LAUNCH DIRECTIONS limit which way the tube
 * can throw one, and on real ships that is narrower: the Romulan KR's Plasma-G launches
 * straight ahead only, yet may target anything in FA.
 * <p>
 * Both were wrong before. The target-in-arc test did not exist at all, so a forward launcher
 * could target something dead astern; and conflating the two would have been worse, ruling
 * out most of the FA arc because the tube only throws one way.
 */
public class PlasmaLaunchArcTest {

    private Game game;
    private Ship romulan;
    private Ship target;

    @Before
    public void setUp() {
        game = new Game();

        romulan = new Ship();
        romulan.init(RomulanShips.getRomKr());
        romulan.setName("IRW Gauntlet");
        romulan.setLocation(new Location(10, 10));
        romulan.setFacing(1);                       // pointing "north" up the column

        target = new Ship();
        target.init(KlingonShips.getD7());
        target.setName("IKV Saber");
        target.setFacing(13);

        game.getShips().add(romulan);
        game.getShips().add(target);

        game.advancePhase();                        // MOVEMENT -> ACTIVITY
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    private PlasmaLauncher launcher() {
        return romulan.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof PlasmaLauncher)
                .map(w -> (PlasmaLauncher) w)
                .findFirst().orElseThrow(() -> new AssertionError("the KR should carry plasma"));
    }

    /** Ahead of a forward launcher, with no direction given: the bearing serves. */
    @Test
    public void launchAhead_succeeds() {
        target.setLocation(new Location(10, 6));    // four hexes dead ahead

        ActionResult r = game.launchPlasma(romulan, target, launcher(), false, 0);

        assertTrue(r.getMessage(), r.isSuccess());
    }

    /**
     * The missing rule: a target outside the FIRING arc cannot be targeted at all. There was
     * no such test before, so this launch succeeded.
     */
    @Test
    public void targetAstern_isRefused_asOutsideTheFiringArc() {
        target.setLocation(new Location(10, 14));   // four hexes astern, outside FA

        ActionResult r = game.launchPlasma(romulan, target, launcher(), false, 0);

        assertFalse("a forward launcher cannot target something behind it", r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("cannot target"));
    }

    /**
     * And the distinction that matters: a target inside FA but NOT dead ahead is legal, even
     * though the tube itself launches in direction 1 only. Judging the target by the launch
     * directions would refuse this, which is the mistake this test exists to prevent.
     */
    @Test
    public void targetInsideTheArcButNotDeadAhead_isLegal() {
        target.setLocation(new Location(12, 7));

        int bearing = com.sfb.utilities.MapUtils.getBearing(romulan, target);
        int relative = com.sfb.utilities.MapUtils.getRelativeBearing(bearing, romulan.getFacing());
        assertTrue("premise: the hex is inside FA",
                com.sfb.utilities.ArcUtils.inArc(relative, com.sfb.utilities.ArcUtils.FA));
        assertNotEquals("premise: and it is not dead ahead", 1, relative);
        assertFalse("premise: so the tube cannot throw one straight at it",
                com.sfb.utilities.ArcUtils.inArc(relative, com.sfb.utilities.ArcUtils.of(1)));

        ActionResult r = game.launchPlasma(romulan, target, launcher(), false, 0);

        assertTrue("a target in the firing arc is legal however the tube points: "
                + r.getMessage(), r.isSuccess());
    }

    /**
     * A named launch direction the tube cannot use is refused, separately and with its own
     * message — the target here is perfectly legal.
     */
    @Test
    public void explicitLaunchDirectionTheTubeCannotUse_isRefused() {
        target.setLocation(new Location(10, 6));    // dead ahead, a fine target

        ActionResult r = game.launchPlasma(romulan, target, launcher(), false, 13);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("cannot launch in direction"));
    }

    /**
     * Same hex, so there is no bearing to test (MapUtils returns 0). The arc check has
     * nothing to judge and must not invent a refusal out of it.
     */
    @Test
    public void sameHex_hasNoBearing_andIsNotRefusedForArc() {
        target.setLocation(new Location(10, 10));

        ActionResult r = game.launchPlasma(romulan, target, launcher(), false, 0);

        assertFalse("if it fails, it is not for want of an arc: " + r.getMessage(),
                !r.isSuccess() && r.getMessage().contains("cannot target"));
    }
}
