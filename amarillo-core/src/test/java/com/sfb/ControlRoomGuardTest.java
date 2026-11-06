package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.BoardingPartyQuality;
import com.sfb.properties.SystemTarget;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * (D7.8371) Guards on control rooms.
 *
 * <p>These were missing. The guard system covered weapons, tractors, engines, sensor and
 * scanner tracks, and the fungible pools — but {@code SystemTarget.Type} had no entry for a
 * control room at all, so the bridge of every ship in the game was unguardable and, for the
 * same reason, could not be the target of a hit-and-run raid either.
 *
 * <p>The rule is specific about the granularity, and it is NOT per box:
 * "A single boarding party, assigned as a guard, is sufficient to protect all connected
 * control boxes of a single type. For example, if there are two connected bridge boxes, one
 * guard covers both. Different types of control boxes (security, flag, aux, emer, bridge)
 * can never be protected by a single guard."
 *
 * <p>So a control room is a GROUP post, the same shape as a warp engine — which also means
 * (D7.8375) applies to it: combat damage only rolls the guard's casualty when the entire
 * group is gone, not when one box of it is lost.
 */
public class ControlRoomGuardTest {

    private Game game;
    private Ship fed;

    @Before
    public void setUp() {
        game = new Game();
        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        game.getShips().add(fed);
    }

    private List<String> targetLabels(Ship ship) {
        return game.getTargetableSystems(ship).stream()
                .map(SystemTarget::getDisplayName)
                .collect(Collectors.toList());
    }

    private SystemTarget target(Ship ship, SystemTarget.Type type) {
        return game.getTargetableSystems(ship).stream()
                .filter(t -> t.getType() == type)
                .findFirst().orElse(null);
    }

    // -------------------------------------------------------------------------
    // The rooms exist as targets at all
    // -------------------------------------------------------------------------

    /**
     * The same enumeration feeds the raid picker and the guard-posting picker, so a room
     * missing here was invisible to both. That is why this was one bug and not two.
     */
    @Test
    public void controlRoomsAreOfferedAsTargets() {
        List<String> labels = targetLabels(fed);

        assertTrue(labels.toString(), labels.contains("Bridge"));
        assertTrue(labels.toString(), labels.contains("Emergency Bridge"));
        assertTrue(labels.toString(), labels.contains("Auxiliary Control"));
    }

    /** A room the hull does not have is not offered — a Fed CA has no security station. */
    @Test
    public void aRoomTheShipDoesNotHaveIsNotOffered() {
        assertEquals("fixture: a Fed CA has no security station",
                0, fed.getControlSpaces().getSecurity());

        assertFalse(targetLabels(fed).contains("Security Station"));
    }

    /**
     * A Klingon D7 is the mirror case, and it is the one that would have hidden a mistake:
     * (G6.12) merges the flag bridge into the regular bridge on Klingon hulls, so the D7 has
     * a security station and NO flag bridge. If the enumeration were keyed off anything but
     * the hull's own boxes, one of these two would come out wrong.
     */
    @Test
    public void theKlingonHullOffersSecurityAndNotFlag() {
        Ship d7 = new Ship();
        d7.init(KlingonShips.getD7());
        d7.setName("IKV Devastator");
        game.getShips().add(d7);

        List<String> labels = targetLabels(d7);

        assertEquals("fixture: no flag bridge on a D7 (G6.12)", 0, d7.getControlSpaces().getFlag());
        assertFalse(labels.toString(), labels.contains("Flag Bridge"));
        assertTrue(labels.toString(), labels.contains("Security Station"));
    }

    // -------------------------------------------------------------------------
    // Posting
    // -------------------------------------------------------------------------

    @Test
    public void aBoardingPartyCanBePostedToTheBridge() {
        SystemTarget bridge = target(fed, SystemTarget.Type.BRIDGE);
        assertNotNull("fixture: the bridge should be a target", bridge);

        String err = fed.getGuardPosts().assign(bridge, BoardingPartyQuality.NORMAL);

        assertNull(err, err);
        assertTrue(fed.getGuardPosts().isGuarded(SystemTarget.Type.BRIDGE));
    }

    /** (D7.833) one guard per post — a second on the same room is refused. */
    @Test
    public void aSecondGuardOnTheSameRoomIsRefused() {
        SystemTarget bridge = target(fed, SystemTarget.Type.BRIDGE);
        assertNull(fed.getGuardPosts().assign(bridge, BoardingPartyQuality.NORMAL));

        String err = fed.getGuardPosts().assign(bridge, BoardingPartyQuality.NORMAL);

        assertNotNull("a room already has its guard", err);
        assertTrue(err, err.contains("Already guarded"));
    }

    /**
     * The heart of (D7.8371): one guard covers every connected box of its own type, but
     * never reaches a different type. Guarding the bridge leaves auxiliary control open.
     */
    @Test
    public void aGuardOnOneRoomTypeDoesNotCoverAnother() {
        assertNull(fed.getGuardPosts().assign(target(fed, SystemTarget.Type.BRIDGE),
                BoardingPartyQuality.NORMAL));

        assertTrue(fed.getGuardPosts().isGuarded(SystemTarget.Type.BRIDGE));
        assertFalse("different types can never share a guard (D7.8371)",
                fed.getGuardPosts().isGuarded(SystemTarget.Type.AUXCON));
    }

    /** Released like any other post, and the boarding party comes back to the roster. */
    @Test
    public void releasingTheRoomGuardReturnsTheBoardingParty() {
        int before = fed.getCrew().getFriendlyTroops().normal;
        SystemTarget bridge = target(fed, SystemTarget.Type.BRIDGE);
        assertNull(fed.getGuardPosts().assign(bridge, BoardingPartyQuality.NORMAL));
        assertEquals("posting takes one off the roster (D7.834)",
                before - 1, fed.getCrew().getFriendlyTroops().normal);

        assertNull(fed.getGuardPosts().release(bridge));

        assertFalse(fed.getGuardPosts().isGuarded(SystemTarget.Type.BRIDGE));
        assertEquals(before, fed.getCrew().getFriendlyTroops().normal);
    }

    // -------------------------------------------------------------------------
    // (D7.8375) the group survives while any box of it does
    // -------------------------------------------------------------------------

    /**
     * A bridge guard posted on a two-box bridge does not roll casualties when one box is
     * lost: (D7.8375) holds the roll for combat damage until the whole group is destroyed.
     * Asserted through {@code reconcileAfterDamage} — the sweep {@code DamageResolver} runs
     * after every resolution — because that is where it would go wrong.
     */
    @Test
    public void losingOneBoxOfTheGroupDoesNotTouchTheGuard() {
        int boxes = fed.getControlSpaces().getBridge();
        assertTrue("fixture: this test needs more than one bridge box", boxes > 1);
        assertNull(fed.getGuardPosts().assign(target(fed, SystemTarget.Type.BRIDGE),
                BoardingPartyQuality.NORMAL));

        assertTrue(fed.getControlSpaces().damageBridge());
        List<String> log = fed.getGuardPosts().reconcileAfterDamage();

        assertTrue("no casualty while a box of the group survives: " + log, log.isEmpty());
        assertTrue(fed.getGuardPosts().isGuarded(SystemTarget.Type.BRIDGE));
    }

    /** Once the last box of the group is gone the guard is resolved (D7.832/D7.8375). */
    @Test
    public void losingTheWholeGroupResolvesTheGuard() {
        assertNull(fed.getGuardPosts().assign(target(fed, SystemTarget.Type.BRIDGE),
                BoardingPartyQuality.NORMAL));

        while (fed.getControlSpaces().getAvailableBridge() > 0)
            fed.getControlSpaces().damageBridge();
        List<String> log = fed.getGuardPosts().reconcileAfterDamage();

        assertEquals("exactly one guard resolved: " + log, 1, log.size());
        assertFalse(fed.getGuardPosts().isGuarded(SystemTarget.Type.BRIDGE));
    }

    // -------------------------------------------------------------------------
    // Raiding
    // -------------------------------------------------------------------------

    /**
     * The other half of the pairing: a room that can be guarded must be one a raid can
     * reach, or the guard protects nothing. (D7.823) bars only damage-control and excess-
     * damage boxes, and (D7.827) has the raider name "a given SSD box or room".
     */
    @Test
    public void aRaidCanDestroyABridgeBox() {
        int before = fed.getControlSpaces().getAvailableBridge();

        assertTrue(fed.getControlSpaces().damageBridge());

        assertEquals(before - 1, fed.getControlSpaces().getAvailableBridge());
    }

    /** The wire code round-trips, so a client can name the room (shared with raids). */
    @Test
    public void theRoomResolvesFromItsWireCode() {
        SystemTarget parsed = game.parseRaidTargetCode(fed, "AUXCON");

        assertNotNull("AUXCON should resolve to a target", parsed);
        assertEquals(SystemTarget.Type.AUXCON, parsed.getType());
    }
}
