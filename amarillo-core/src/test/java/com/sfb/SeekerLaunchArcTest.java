package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.KzintiShips;
import com.sfb.utilities.ArcUtils;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.DroneRack;

/**
 * A launched seeker must be able to see where it is going: with the facing it is launched on,
 * the target has to lie inside the SEEKER's own forward arc (FA, directions 21-5).
 * <p>
 * The third of three constraints on a launch, and the one that had no code at all. The other
 * two are about the launcher — a plasma tube's firing arc limits what it may target, and its
 * launch directions limit which way it can throw one. This one is about the seeker, and for a
 * drone rack it is the ONLY bound: a Kzinti rack has no arc set, so it launches in any
 * direction, and nothing else would stop a drone being sent away from its target.
 * <p>
 * It bites only when a direction is named. A launch with no direction aims straight at the
 * target, which puts it at relative bearing 1 — trivially inside its own forward arc.
 */
public class SeekerLaunchArcTest {

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
        target.setLocation(new Location(10, 6));     // four hexes dead ahead
        target.setFacing(13);

        game.getShips().add(kzinti);
        game.getShips().add(target);

        // A drone launch wants a lock-on unless the drone is self-guiding under passive fire
        // control (D6.121 / D19.221); this ship has neither, so it is given both.
        kzinti.setActiveFireControl(true);
        kzinti.addLockOn(target);

        game.advancePhase();                          // MOVEMENT -> ACTIVITY
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

    /**
     * The premise this whole class rests on: the rack itself constrains nothing. Its arc is
     * all twenty-four directions — how "no restriction" is spelled — so the seeker's own
     * forward arc is the only thing bounding where a drone may be sent.
     */
    @Test
    public void theRackHasNoArcOfItsOwn() {
        int allDirections = 0;
        for (int d = 1; d <= 24; d++)
            allDirections |= 1 << (d - 1);

        assertEquals("a Kzinti rack launches any way it likes", allDirections, rack().getArcs());
    }

    @Test
    public void noDirectionGiven_aimsAtTheTarget_andIsFine() {
        ActionResult res = launchOn(0);

        assertTrue(res.getMessage(), res.isSuccess());
    }

    /** Dead at the target: relative bearing 1, the middle of its forward arc. */
    @Test
    public void launchedStraightAtTheTarget_isFine() {
        int bearing = MapUtils.getBearing(kzinti, target);

        ActionResult res = launchOn(bearing);

        assertTrue(res.getMessage(), res.isSuccess());
    }

    /**
     * Launched away from the target. Nothing else in the game refuses this — the rack has no
     * arc — so without the seeker's own forward arc a drone could be sent off backwards and
     * still be expected to track.
     */
    @Test
    public void launchedAwayFromTheTarget_isRefused() {
        int bearing = MapUtils.getBearing(kzinti, target);
        int away = ((bearing + 11) % 24) + 1;        // twelve directions round: dead opposite

        int relative = MapUtils.getRelativeBearing(bearing, away);
        assertFalse("premise: the target is outside the seeker's forward arc",
                ArcUtils.inArc(relative, ArcUtils.FA));

        ActionResult res = launchOn(away);

        assertFalse("a drone cannot be launched away from its target", res.isSuccess());
        assertTrue(res.getMessage(), res.getMessage().contains("forward arc"));
    }

    /**
     * The edge of the arc is inside it. FA spans nine directions, so four either side of the
     * launch direction still tracks — this is the boundary the refusal must not overreach to.
     */
    @Test
    public void theEdgeOfTheForwardArc_isStillLegal() {
        int bearing = MapUtils.getBearing(kzinti, target);
        int edge = ((bearing + 3) % 24) + 1;         // four directions off

        int relative = MapUtils.getRelativeBearing(bearing, edge);
        assertTrue("premise: four off is still inside FA",
                ArcUtils.inArc(relative, ArcUtils.FA));

        ActionResult res = launchOn(edge);

        assertTrue("the edge of the arc is inside it: " + res.getMessage(), res.isSuccess());
    }
}
