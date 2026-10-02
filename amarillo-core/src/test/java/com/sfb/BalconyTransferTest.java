package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.systemgroups.ShuttleBay;

/**
 * J1.53 balcony and track, slice two: moving craft between a bay and its balcony, at the J1.50
 * rate.
 * <p>
 * The rule is one sentence with two halves pulling opposite ways: "Movement from this outside
 * track to and from the hangar bay is limited by (J1.50), but any number (up to the ship's
 * limit) may be landed on or launched from this balcony during a given impulse." This slice is
 * the limited half. The free half — launching and landing from the balcony — is slice three.
 * <p>
 * J1.532 spells out what "limited by J1.50" costs: "the rate in (J1.50) includes all launch/land
 * and bay/balcony operations; i.e., a given bay cannot land a shuttle and move another one to
 * the balcony during the same two-impulse cycle." Claiming the same hatch a launch claims gets
 * that for nothing, which is the whole reason the transfer is written this way.
 */
public class BalconyTransferTest {

    private Game game;
    private Ship cva;
    private Player fed;

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        game = new Game();

        fed = new Player();
        fed.setTeamName("Federation");

        cva = ShipLibrary.createShip(ShipLibrary.get("Federation", "CVA"));
        cva.setName("USS Enterprise");
        cva.setLocation(new Location(10, 10));
        cva.setFacing(1);
        cva.setOwner(fed);
        cva.setActiveFireControl(true);
        game.getShips().add(cva);

        game.startTurn();
        com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
        e.setLifeSupport(cva.getLifeSupportCost());
        e.setFireControl(cva.getFireControlCost());
        game.submitAllocation(cva, e);
        toActivity();
    }

    private void toActivity() {
        for (int i = 0; i < 400; i++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY)
                return;
            game.advancePhase();
        }
        fail("never reached an Activity phase");
    }

    /**
     * Advance far enough for the bay's hatch to be free again. TWO impulses, not one: J1.50
     * allows one operation per two impulses, which is what {@code LAUNCH_COOLDOWN} encodes.
     * This test class originally advanced one and had three tests fail for it.
     */
    private void afterHatchCooldown() {
        nextImpulse();
        nextImpulse();
    }

    private void nextImpulse() {
        int was = game.getAbsoluteImpulse();
        for (int i = 0; i < 40 && game.getAbsoluteImpulse() == was; i++)
            game.advancePhase();
        assertNotEquals("fixture: the impulse must advance", was, game.getAbsoluteImpulse());
        toActivity();
    }

    /** A bay that has a balcony, and the craft sitting in it. */
    private ShuttleBay fighterBay() {
        for (ShuttleBay b : cva.getShuttles().getBays())
            if (b.hasBalcony())
                return b;
        throw new AssertionError("fixture: the CVA should have a bay with a balcony");
    }

    private ShuttleBay adminBay() {
        for (ShuttleBay b : cva.getShuttles().getBays())
            if (!b.hasBalcony())
                return b;
        throw new AssertionError("fixture: the CVA should have a bay without one");
    }

    private String firstCraftIn(ShuttleBay bay) {
        assertFalse("fixture: the bay should hold something", bay.getInventory().isEmpty());
        return bay.getInventory().get(0).getName();
    }

    // ---------------------------------------------------------------- the move out

    @Test
    public void aCraftMovesFromItsBayOntoItsBalcony() {
        ShuttleBay bay = fighterBay();
        String name = firstCraftIn(bay);
        int inside = bay.getInventory().size();

        Game.ActionResult r = game.moveToBalcony(cva, name);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("one fewer inside", inside - 1, bay.getInventory().size());
        assertEquals("and one outside", 1, bay.getBalcony().size());
        assertEquals(name, bay.getBalcony().get(0).getName());
        assertEquals("its box is free again", 5, bay.balconyFree());
    }

    @Test
    public void aBayWithNoBalconyRefusesTheMove() {
        String name = firstCraftIn(adminBay());

        Game.ActionResult r = game.moveToBalcony(cva, name);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("no balcony"));
    }

    @Test
    public void anUnknownCraftIsRefused() {
        Game.ActionResult r = game.moveToBalcony(cva, "Nothing-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("No shuttle called"));
    }

    /** Each balcony belongs to a specific bay (J1.532), so six is six — not twelve. */
    @Test
    public void aBalconyFillsAndThenRefuses() {
        ShuttleBay bay = fighterBay();

        for (int i = 0; i < 6; i++) {
            String name = firstCraftIn(bay);
            assertTrue("move " + (i + 1), game.moveToBalcony(cva, name).isSuccess());
            afterHatchCooldown();       // each move costs the hatch
        }
        assertEquals(0, bay.balconyFree());

        Game.ActionResult seventh = game.moveToBalcony(cva, firstCraftIn(bay));

        assertFalse(seventh.isSuccess());
        assertTrue(seventh.getMessage(), seventh.getMessage().contains("occupied"));
    }

    // ---------------------------------------------------------------- the rate (J1.532)

    /**
     * The heart of the slice. A bay/balcony move claims the same hatch a launch or a recovery
     * claims, so a second move in the same two-impulse cycle has nothing left to claim.
     */
    @Test
    public void aSecondMoveInTheSameCycleIsRefused() {
        ShuttleBay bay = fighterBay();
        assertTrue(game.moveToBalcony(cva, firstCraftIn(bay)).isSuccess());

        Game.ActionResult second = game.moveToBalcony(cva, firstCraftIn(bay));

        assertFalse(second.isSuccess());
        assertTrue(second.getMessage(), second.getMessage().contains("J1.532"));
        assertEquals("and nothing moved", 1, bay.getBalcony().size());
    }

    /** J1.532 names the case outright: a bay cannot land one and move another out together. */
    @Test
    public void aMoveOutAndALaunchCompeteForTheSameHatch() {
        ShuttleBay bay = fighterBay();
        Shuttle toLaunch = bay.getInventory().get(0);

        assertTrue(game.launchShuttle(cva, bay, toLaunch, 6, 1).isSuccess());

        Game.ActionResult move = game.moveToBalcony(cva, firstCraftIn(bay));

        assertFalse("the launch took the hatch", move.isSuccess());
        assertTrue(move.getMessage(), move.getMessage().contains("J1.532"));
    }

    /**
     * J1.50's rate is one operation per TWO impulses, so the hatch is not free on the very next
     * one. Both halves asserted, because "the next impulse" was my own first assumption and it
     * was wrong — a test that only checked the recovery would have passed either way.
     */
    @Test
    public void theHatchComesBackAfterTwoImpulsesNotOne() {
        ShuttleBay bay = fighterBay();
        assertTrue(game.moveToBalcony(cva, firstCraftIn(bay)).isSuccess());

        nextImpulse();
        assertFalse("one impulse is not enough (J1.50)",
                game.moveToBalcony(cva, firstCraftIn(bay)).isSuccess());

        nextImpulse();
        assertTrue("two impulses frees it",
                game.moveToBalcony(cva, firstCraftIn(bay)).isSuccess());
        assertEquals(2, bay.getBalcony().size());
    }

    /**
     * A tunnel deck's two hatches work independently (J1.58), so such a bay could move two
     * craft out in one impulse. The CVA is not one, which is what makes this worth asserting:
     * the rate belongs to the HATCH, not to the bay.
     */
    @Test
    public void aTunnelDeckGetsTwoMovesPerImpulse() {
        ShuttleBay bay = fighterBay();
        bay.setHatchCount(ShuttleBay.TUNNEL_DECK_HATCHES);

        assertTrue(game.moveToBalcony(cva, firstCraftIn(bay)).isSuccess());
        assertTrue("the second hatch is still free",
                game.moveToBalcony(cva, firstCraftIn(bay)).isSuccess());

        Game.ActionResult third = game.moveToBalcony(cva, firstCraftIn(bay));
        assertFalse("but only two", third.isSuccess());
    }

    // ---------------------------------------------------------------- the move back in

    @Test
    public void aCraftComesBackInsideAndCostsAHatch() {
        ShuttleBay bay = fighterBay();
        String name = firstCraftIn(bay);
        assertTrue(game.moveToBalcony(cva, name).isSuccess());
        afterHatchCooldown();

        Game.ActionResult back = game.moveFromBalcony(cva, name);

        assertTrue(back.getMessage(), back.isSuccess());
        assertEquals("nothing left outside", 0, bay.getBalcony().size());
        assertTrue("and it is in a box again", bay.getInventory().stream()
                .anyMatch(sh -> sh.getName().equals(name)));

        Game.ActionResult again = game.moveToBalcony(cva, name);
        assertFalse("coming in spent the hatch too", again.isSuccess());
    }

    @Test
    public void aCraftNotOnAnyBalconyCannotComeIn() {
        Game.ActionResult r = game.moveFromBalcony(cva, firstCraftIn(fighterBay()));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("on any balcony"));
    }

    // ---------------------------------------------------------------- J1.534

    /** "Scatter-packs can be held on the balcony; suicide shuttles and wild weasels cannot." */
    @Test
    public void aSuicideShuttleCannotBeHeldOutside() {
        ShuttleBay bay = fighterBay();
        Shuttle victim = bay.getInventory().get(0);
        com.sfb.objects.shuttles.SuicideShuttle ss =
                new com.sfb.objects.shuttles.SuicideShuttle(victim);
        ss.setName("Boom-1");
        ss.setOwner(fed);
        bay.replaceShuttle(victim, ss);

        Game.ActionResult r = game.moveToBalcony(cva, "Boom-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("J1.534"));
    }

    @Test
    public void aWildWeaselCannotBeHeldOutside() {
        ShuttleBay bay = fighterBay();
        Shuttle victim = bay.getInventory().get(0);
        com.sfb.objects.shuttles.WildWeaselShuttle ww =
                new com.sfb.objects.shuttles.WildWeaselShuttle(cva, victim);
        ww.setName("Weasel-1");
        ww.setOwner(fed);
        bay.replaceShuttle(victim, ww);

        Game.ActionResult r = game.moveToBalcony(cva, "Weasel-1");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("J1.534"));
    }

    /** A scatter pack MAY be parked — the rule allows it explicitly. */
    @Test
    public void aScatterPackMayBeHeldOutside() {
        ShuttleBay bay = fighterBay();
        Shuttle victim = bay.getInventory().get(0);
        com.sfb.objects.shuttles.ScatterPack sp = new com.sfb.objects.shuttles.ScatterPack(victim);
        sp.setName("Pack-1");
        sp.setOwner(fed);
        bay.replaceShuttle(victim, sp);

        Game.ActionResult r = game.moveToBalcony(cva, "Pack-1");

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(bay.getBalcony().stream().anyMatch(sh -> sh.getName().equals("Pack-1")));
    }

    // ---------------------------------------------------------------- phase

    @Test
    public void transfersHappenInTheActivityPhase() {
        ShuttleBay bay = fighterBay();
        String name = firstCraftIn(bay);
        game.advancePhase();                    // out of ACTIVITY
        assertNotEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());

        Game.ActionResult r = game.moveToBalcony(cva, name);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("Activity phase"));
    }
}
