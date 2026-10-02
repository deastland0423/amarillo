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
 * J1.53 balcony and track, slice three: launching FROM the balcony, which is free and
 * unlimited.
 * <p>
 * This is the other half of the sentence slice two implemented: "Movement from this outside
 * track to and from the hangar bay is limited by (J1.50), <b>but any number (up to the ship's
 * limit) may be landed on or launched from this balcony during a given impulse.</b>"
 * <p>
 * That is the entire reason a balcony is worth using. A carrier that parks six fighters over
 * several impulses — one hatch each, the slow half of the rule — can then put all six into
 * space in a single impulse instead of needing twelve impulses to cycle them out one at a
 * time. The cost is paid up front, and {@link BalconyPositionTest} records the owner's ruling
 * on the risk it buys: one rear-hull damage point destroys a parked shuttle outright.
 * <p>
 * The implementation is deliberately in {@link ShuttleBay#launch} and
 * {@link ShuttleBay#canLaunch(Shuttle, int)} rather than in the nine launch methods on
 * {@code LaunchCoordinator}. The bay owns both the balcony and the hatch counter, so asking it
 * first is what makes every launch path — plain shuttle, fighter, scatter pack — correct at
 * once. These tests go through {@code game.launchShuttle} to prove that actually happened.
 * <p>
 * Landing straight onto the balcony is here too, and it is the half I first guessed wrong.
 * J1.532 settles it outright: "A ship can land shuttles (J1.6) on the balcony at any speed
 * (and by any method) that it could land them in the hangar" — and J1.53's headline puts
 * landings in the same free, unlimited clause as launches. So a carrier can recover a whole
 * strike group in one impulse, not merely launch one.
 */
public class BalconyLaunchTest {

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

    private void nextImpulse() {
        int was = game.getAbsoluteImpulse();
        for (int i = 0; i < 40 && game.getAbsoluteImpulse() == was; i++)
            game.advancePhase();
        assertNotEquals("fixture: the impulse must advance", was, game.getAbsoluteImpulse());
        toActivity();
    }

    /** Two impulses, because J1.50's rate is one operation per two (see BalconyTransferTest). */
    private void afterHatchCooldown() {
        nextImpulse();
        nextImpulse();
    }

    private ShuttleBay fighterBay() {
        for (ShuttleBay b : cva.getShuttles().getBays())
            if (b.hasBalcony())
                return b;
        throw new AssertionError("fixture: the CVA should have a bay with a balcony");
    }

    /**
     * Park n craft from the bay, spending a hatch and waiting out the cooldown for each — the
     * slow half of the rule, done honestly rather than by calling {@code bay.park} directly,
     * so these tests start from a position a player could actually have reached.
     */
    private java.util.List<Shuttle> park(ShuttleBay bay, int n) {
        java.util.List<Shuttle> parked = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = bay.getInventory().get(0).getName();
            Game.ActionResult r = game.moveToBalcony(cva, name);
            assertTrue("fixture, parking " + (i + 1) + ": " + r.getMessage(), r.isSuccess());
            parked.add(bay.getBalcony().get(bay.getBalcony().size() - 1));
            afterHatchCooldown();
        }
        return parked;
    }

    private Game.ActionResult launch(ShuttleBay bay, Shuttle craft) {
        return game.launchShuttle(cva, bay, craft, 6, 1);
    }

    // ---------------------------------------------------------------- free

    @Test
    public void aParkedCraftLaunchesWithoutSpendingAHatch() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);
        int hatchesBefore = bay.getAvailableHatchCount(game.getAbsoluteImpulse());
        assertTrue("fixture: a hatch should be free to begin with", hatchesBefore > 0);

        Game.ActionResult r = launch(bay, craft);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("the launch cost no hatch (J1.53)",
                hatchesBefore, bay.getAvailableHatchCount(game.getAbsoluteImpulse()));
        assertTrue("and the bay can still do a hatch operation",
                bay.canLaunch(game.getAbsoluteImpulse()));
    }

    /**
     * The sharpest form of "free": a bay whose hatch is already spent this cycle still launches
     * off its balcony. If the implementation had merely made the parked launch cheap rather
     * than hatch-independent, this is the test that would fail.
     */
    @Test
    public void aSpentHatchDoesNotStopAParkedLaunch() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);

        // Spend the hatch on something else entirely: move a second craft out.
        assertTrue(game.moveToBalcony(cva, bay.getInventory().get(0).getName()).isSuccess());
        assertFalse("fixture: the hatch is now spent", bay.canLaunch(game.getAbsoluteImpulse()));

        Game.ActionResult r = launch(bay, craft);

        assertTrue("a parked craft does not queue for the hatch: " + r.getMessage(),
                r.isSuccess());
    }

    // ---------------------------------------------------------------- unlimited

    /**
     * "Any number ... may be ... launched from this balcony during a given impulse." All six of
     * the CVA's positions empty into space on one impulse — the payoff for the six hatches
     * spent filling them.
     */
    @Test
    public void everyParkedCraftCanLaunchInTheSameImpulse() {
        ShuttleBay bay = fighterBay();
        java.util.List<Shuttle> parked = park(bay, 6);
        assertEquals("fixture: a full balcony", 6, bay.getBalcony().size());

        int impulse = game.getAbsoluteImpulse();
        for (Shuttle craft : parked) {
            Game.ActionResult r = launch(bay, craft);
            assertTrue(craft.getName() + ": " + r.getMessage(), r.isSuccess());
        }

        assertEquals("all six went in one impulse", impulse, game.getAbsoluteImpulse());
        assertTrue("the balcony is empty", bay.getBalcony().isEmpty());
        assertEquals("and all six are in space", 6, game.getActiveShuttles().size());
    }

    /**
     * The contrast that makes the rule worth implementing: the same six craft launched from
     * INSIDE the bay get one away and then stop, because that is the J1.50 rate.
     */
    @Test
    public void sixCraftInsideTheBayCannotDoTheSame() {
        ShuttleBay bay = fighterBay();
        java.util.List<Shuttle> inside = new java.util.ArrayList<>(bay.getInventory());

        assertTrue(launch(bay, inside.get(0)).isSuccess());

        Game.ActionResult second = launch(bay, inside.get(1));
        assertFalse("the hatch is spent — one operation per two impulses (J1.50)",
                second.isSuccess());
        assertEquals("only the first got away", 1, game.getActiveShuttles().size());
    }

    // ---------------------------------------------------------------- bookkeeping

    @Test
    public void launchingFreesThePositionAndTakesNoShuttleBox() {
        ShuttleBay bay = fighterBay();
        int spaces = bay.getTotalSpaces();
        Shuttle craft = park(bay, 1).get(0);
        int emptyBoxes = bay.getEmptySpaceCount();

        assertTrue(launch(bay, craft).isSuccess());

        assertEquals("the position is free again", 6, bay.balconyFree());
        assertFalse(bay.isParked(craft));
        assertEquals("no box was involved", emptyBoxes, bay.getEmptySpaceCount());
        assertEquals("and no space appeared or vanished", spaces, bay.getTotalSpaces());
    }

    /** It reaches space properly: on the map, owned, and named like any launched craft. */
    @Test
    public void aCraftLaunchedFromTheBalconyIsPutIntoPlay() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);

        assertTrue(launch(bay, craft).isSuccess());

        assertTrue("in play", game.getActiveShuttles().contains(craft));
        assertEquals("in the carrier's hex", cva.getLocation(), craft.getLocation());
        assertEquals(fed, craft.getOwner());
        assertEquals("USS Enterprise", craft.getParentShipName());
    }

    /**
     * A parked craft belongs to its OWN bay's balcony (J1.532), and the launch must honour
     * that: asking the other fighter bay to launch it finds nothing to launch.
     */
    @Test
    public void anotherBayCannotLaunchWhatIsParkedOnThisOne() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);

        ShuttleBay other = null;
        for (ShuttleBay b : cva.getShuttles().getBays())
            if (b != bay && b.hasBalcony())
                other = b;
        assertNotNull("fixture: the CVA has two bays with balconies", other);

        Game.ActionResult r = launch(other, craft);

        assertFalse("it is not on that bay's balcony", r.isSuccess());
        assertTrue("and it is still parked where it was", bay.isParked(craft));
    }

    // ------------------------------------------------- the lookup that gates it

    /**
     * Where this feature would have been unreachable in play. A parked craft is deliberately
     * absent from {@link ShuttleBay#getInventory()}, which is what every launch lookup used to
     * scan — so the server could not have found it by name, and a free launch nobody can ask
     * for is not a feature. {@link ShuttleBay#launchableInventory()} is what those lookups ask
     * now, and this is its contract.
     */
    @Test
    public void theLaunchableInventorySeesInsideAndOutside() {
        ShuttleBay bay = fighterBay();
        int inside = bay.getInventory().size();
        Shuttle craft = park(bay, 1).get(0);

        assertEquals("gone from the inventory", inside - 1, bay.getInventory().size());
        assertEquals("but still launchable", inside, bay.launchableInventory().size());
        assertTrue(bay.launchableInventory().contains(craft));
        assertFalse(bay.getInventory().contains(craft));
    }

    /** The returned list is a copy — writing to it must not park or unpark anything. */
    @Test
    public void theLaunchableInventoryIsACopy() {
        ShuttleBay bay = fighterBay();
        park(bay, 1);

        bay.launchableInventory().clear();

        assertEquals("the balcony is untouched", 1, bay.getBalcony().size());
        assertFalse("and so are the boxes", bay.getInventory().isEmpty());
    }

    // ------------------------------------------------- what is NOT free

    /**
     * A HANGAR landing still costs a hatch — that half was always right, and it is the contrast
     * that makes a balcony landing worth asking for.
     */
    @Test
    public void aHangarLandingStillCostsAHatch() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);
        assertTrue(launch(bay, craft).isSuccess());
        craft.setSpeed(0);

        assertTrue("fixture: a hatch is free", bay.canLaunch(game.getAbsoluteImpulse()));
        Game.ActionResult r = game.landShuttle(cva, craft.getName());
        assertTrue(r.getMessage(), r.isSuccess());

        assertFalse("the landing spent a hatch (J1.50)",
                bay.canLaunch(game.getAbsoluteImpulse()));
    }

    // ------------------------------------------------- landing ON the balcony (J1.532)

    @Test
    public void aCraftCanLandStraightOntoTheBalconyWithoutAHatch() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);
        assertTrue(launch(bay, craft).isSuccess());
        craft.setSpeed(0);
        int hatchesBefore = bay.getAvailableHatchCount(game.getAbsoluteImpulse());

        Game.ActionResult r = game.landOnBalcony(cva, craft.getName());

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("it is parked outside", bay.isParked(craft));
        assertFalse("and off the map", game.getActiveShuttles().contains(craft));
        assertNull(craft.getLocation());
        assertEquals("no hatch was spent (J1.53)",
                hatchesBefore, bay.getAvailableHatchCount(game.getAbsoluteImpulse()));
        assertFalse("it took no shuttle box either", bay.getInventory().contains(craft));
    }

    /** "any number ... may be landed on ... during a given impulse" — the recovery payoff. */
    @Test
    public void everyCraftCanLandOnTheBalconyInTheSameImpulse() {
        ShuttleBay bay = fighterBay();
        java.util.List<Shuttle> parked = park(bay, 6);
        int impulse = game.getAbsoluteImpulse();
        for (Shuttle craft : parked)
            assertTrue(launch(bay, craft).isSuccess());
        for (Shuttle craft : parked)
            craft.setSpeed(0);
        assertEquals("fixture: all six launched on one impulse", impulse,
                game.getAbsoluteImpulse());

        for (Shuttle craft : parked) {
            Game.ActionResult r = game.landOnBalcony(cva, craft.getName());
            assertTrue(craft.getName() + ": " + r.getMessage(), r.isSuccess());
        }

        assertEquals("all six came back in the same impulse", impulse,
                game.getAbsoluteImpulse());
        assertEquals(6, bay.getBalcony().size());
        assertTrue("nothing left in space", game.getActiveShuttles().isEmpty());
    }

    /** A spent hatch is no obstacle either — a balcony landing never consults it. */
    @Test
    public void aSpentHatchDoesNotStopABalconyLanding() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);
        assertTrue(launch(bay, craft).isSuccess());
        craft.setSpeed(0);

        // Spend the hatch moving another craft out.
        assertTrue(game.moveToBalcony(cva, bay.getInventory().get(0).getName()).isSuccess());
        assertFalse("fixture: the hatch is spent", bay.canLaunch(game.getAbsoluteImpulse()));

        Game.ActionResult r = game.landOnBalcony(cva, craft.getName());

        assertTrue(r.getMessage(), r.isSuccess());
    }

    /**
     * "At any speed ... that it could land them in the hangar" — the J1.61 conditions still
     * apply, so a carrier outrunning its own fighter cannot collect it. Shared with the hangar
     * landing through one eligibility check, so the two cannot drift apart.
     */
    @Test
    public void theLandingConditionsStillApplyToTheBalcony() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);
        assertTrue(launch(bay, craft).isSuccess());
        craft.setSpeed(2);
        cva.setSpeed(10);

        Game.ActionResult r = game.landOnBalcony(cva, craft.getName());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("faster"));
        assertTrue("still in space", game.getActiveShuttles().contains(craft));
    }

    /**
     * EVERY position on the ship, not just the nearest bay's. A landing craft is choosing a
     * place on the hull, so any bay with a free position will take it — which is why filling
     * one of the CVA's two balconies is not enough, as the first version of this test found
     * out. Parked directly here: the J1.50 transfer rate is slice two's subject, and twelve
     * hatch cycles would eat most of a turn.
     */
    @Test
    public void aFullShipRefusesTheLanding() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);
        assertTrue(launch(bay, craft).isSuccess());
        craft.setSpeed(0);

        int n = 0;
        for (ShuttleBay b : cva.getShuttles().getBays())
            while (b.balconyFree() > 0) {
                com.sfb.objects.shuttles.AdminShuttle filler =
                        new com.sfb.objects.shuttles.AdminShuttle();
                filler.setName("Filler-" + (++n));
                assertTrue(b.park(filler));
            }
        assertEquals("fixture: all twelve positions taken", 12, cva.parkedCraft().size());

        Game.ActionResult r = game.landOnBalcony(cva, craft.getName());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("occupied"));
        assertTrue("still in space", game.getActiveShuttles().contains(craft));
    }

    /** A ship with no balcony says so, rather than silently landing the craft in a bay. */
    @Test
    public void aShipWithNoBalconyRefusesTheLanding() throws Exception {
        Ship ca = ShipLibrary.createShip(ShipLibrary.get("Federation", "CA"));
        ca.setName("USS Constitution");
        ca.setLocation(cva.getLocation());
        ca.setOwner(fed);
        game.getShips().add(ca);
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, 1).get(0);
        assertTrue(launch(bay, craft).isSuccess());
        craft.setSpeed(0);

        Game.ActionResult r = game.landOnBalcony(ca, craft.getName());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("no balcony"));
    }

    /** And moving a craft back INSIDE still costs one too — slice two's half of the rule. */
    @Test
    public void comingBackInsideIsStillLimited() {
        ShuttleBay bay = fighterBay();
        park(bay, 2);
        String name = bay.getBalcony().get(0).getName();

        assertTrue(game.moveFromBalcony(cva, name).isSuccess());
        Game.ActionResult second = game.moveFromBalcony(cva, bay.getBalcony().get(0).getName());

        assertFalse("inbound transfers are limited by J1.50, unlike launches",
                second.isSuccess());
    }
}
