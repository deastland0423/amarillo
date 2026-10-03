package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;

/**
 * J1.53 balcony and track, slice four: what damage does to parked craft, and what going too
 * fast does to them.
 * <p>
 * J1.531, read carefully, because three of its clauses each cut against an instinct:
 * <blockquote>
 * "If the ship takes damage while shuttles are on the outside balcony, each 'rear hull' damage
 * point destroys one shuttle (<b>instead of one hull box</b>), <b>but no chain reactions
 * (D12.0) will occur</b>. <b>This is not an option</b>; the damage point must be scored on the
 * shuttles if any are on the balcony."
 * </blockquote>
 * <ul>
 * <li><b>Instead of</b> — the hull box survives untouched and the craft is the hit. Note
 * which way this cuts, because the first version of this comment had it backwards: parked
 * craft ARE ablative protection for the rear hull. Six points against a full balcony kill six
 * shuttles and no hull boxes, where six points against a bare one take six boxes. The ship
 * lives longer; what it spends is a whole shuttle per point, and the protection ends the
 * moment the balcony is empty.</li>
 * <li><b>No chain reactions</b> — the opposite of a shuttle BOX, where an armed occupant takes
 * another box with it and sprays a point into the ship (D12.10). An armed fighter on the
 * balcony just dies.</li>
 * <li><b>Not an option</b> — the owner cannot elect to take it on the hull to save a fighter.
 * The only decision is WHICH craft, which is the owner's as with any other DAC pick, and only
 * when more than one is parked.</li>
 * </ul>
 * And J1.533: "Shuttles can remain on the balcony at any speed up to 31. Any shuttles on the
 * balcony when the ship disengages by acceleration (i.e., exceeds a speed of 31) are
 * destroyed." Note what the rule does NOT penalise: merely moving fast. Speed 31 is fine, and
 * so is launching at it (J1.532).
 * <p>
 * This class lives in {@code com.sfb.objects} with the other DAC tests because that is where
 * the rule is implemented — in the DAC loop's own substitution, not bolted on afterwards.
 */
public class BalconyDamageTest {

    private Ship cva;

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        cva = ShipLibrary.createShip(ShipLibrary.get("Federation", "CVA"));
        cva.setName("USS Enterprise");
    }

    private ShuttleBay fighterBay() {
        for (ShuttleBay b : cva.getShuttles().getBays())
            if (b.hasBalcony())
                return b;
        throw new AssertionError("fixture: the CVA should have a bay with a balcony");
    }

    private ShuttleBay secondFighterBay() {
        boolean seenFirst = false;
        for (ShuttleBay b : cva.getShuttles().getBays())
            if (b.hasBalcony()) {
                if (seenFirst)
                    return b;
                seenFirst = true;
            }
        throw new AssertionError("fixture: the CVA should have two bays with balconies");
    }

    private int bayIndexOf(ShuttleBay bay) {
        return cva.getShuttles().getBays().indexOf(bay);
    }

    /** Park directly: slice two already covers how craft get there at the hatch rate. */
    private Shuttle park(ShuttleBay bay, String name) {
        AdminShuttle s = new AdminShuttle();
        s.setName(name);
        assertTrue("fixture: parking " + name, bay.park(s));
        return s;
    }

    private int ahullLeft() {
        return cva.getHullBoxes().getAvailableAhull();
    }

    // ------------------------------------------------- instead of a hull box

    @Test
    public void aRearHullPointDestroysTheOneParkedCraftAndSparesTheHullBox() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, "Alpha");
        int hullBefore = ahullLeft();

        String label = cva.applySystemHitForTest("ahull");

        assertNotNull("the hit resolved", label);
        assertTrue(label, label.contains("Alpha"));
        assertTrue(label, label.contains("J1.531"));
        assertFalse("the craft is gone", bay.isParked(craft));
        assertEquals("'instead of one hull box' — the hull is untouched", hullBefore, ahullLeft());
        assertEquals("and the position is free again", 6, bay.balconyFree());
    }

    /**
     * What bounds the protection: it lasts exactly as long as there is something parked. Once
     * the balcony is bare, rear hull points eat hull boxes again at the normal rate, so a
     * player who stacked the balcony has bought the hull some time at the price of the
     * squadron rather than bought anything permanently.
     */
    @Test
    public void onceTheBalconyIsEmptyTheHullTakesItAgain() {
        ShuttleBay bay = fighterBay();
        park(bay, "Alpha");
        int hullBefore = ahullLeft();

        cva.applySystemHitForTest("ahull");       // kills Alpha, hull untouched
        assertEquals(hullBefore, ahullLeft());

        String second = cva.applySystemHitForTest("ahull");

        assertNotNull(second);
        assertTrue(second, second.contains("HIT"));
        assertFalse("this one was not a balcony hit", second.contains("J1.531"));
        assertEquals("now a hull box is spent", hullBefore - 1, ahullLeft());
    }

    /** With nothing parked, a balcony does not interact with damage at all. */
    @Test
    public void anEmptyBalconyChangesNothing() {
        int hullBefore = ahullLeft();
        assertTrue("fixture: the CVA has balconies", fighterBay().hasBalcony());
        assertTrue("fixture: nothing parked", cva.parkedCraft().isEmpty());

        String label = cva.applySystemHitForTest("ahull");

        assertEquals("an ordinary rear hull hit", hullBefore - 1, ahullLeft());
        assertFalse(label, label.contains("J1.531"));
    }

    /** "afthull" is the same DAC entry under its other name, and must behave identically. */
    @Test
    public void theAftHullSpellingBehavesTheSame() {
        ShuttleBay bay = fighterBay();
        park(bay, "Alpha");
        int hullBefore = ahullLeft();

        String label = cva.applySystemHitForTest("afthull");

        assertTrue(label, label.contains("Alpha"));
        assertEquals(hullBefore, ahullLeft());
    }

    /**
     * A FORWARD hull point is untouched by any of this. J1.531 names the rear hull only — the
     * balcony is at the back (on Gorn ships, on the wings).
     */
    @Test
    public void aForwardHullPointIgnoresTheBalconyEntirely() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, "Alpha");
        int fhullBefore = cva.getHullBoxes().getAvailableFhull();

        String label = cva.applySystemHitForTest("fhull");

        assertTrue("a forward hull box was spent",
                cva.getHullBoxes().getAvailableFhull() < fhullBefore);
        assertTrue("and the craft is untouched", bay.isParked(craft));
        assertFalse(label, label.contains("J1.531"));
    }

    // ------------------------------------------------- not an option, but WHICH is a choice

    /**
     * One craft parked: nothing to decide, so the DAC must not stop and ask. This follows the
     * tractor precedent — ask only when every beam is held — and it is the owner's ruling that
     * the pick matters only when more than one craft is out there.
     */
    @Test
    public void oneParkedCraftIsResolvedWithoutAskingTheOwner() {
        boolean sawTheCraftDie = false;
        for (int attempt = 0; attempt < 60; attempt++) {
            Ship ship = ShipLibrary.createShip(ShipLibrary.get("Federation", "CVA"));
            ShuttleBay bay = null;
            for (ShuttleBay b : ship.getShuttles().getBays())
                if (b.hasBalcony()) {
                    bay = b;
                    break;
                }
            AdminShuttle only = new AdminShuttle();
            only.setName("Alpha");
            bay.park(only);

            Ship.DamageResult r = ship.applyInternalDamage(6, null);

            assertFalse("a lone parked craft is never put to the owner as a question",
                    r.choiceRequired && "ahull".equals(r.choiceType));
            if (!bay.isParked(only))
                sawTheCraftDie = true;
        }
        assertTrue("60 volleys of 6 points should have hit the rear hull at least once",
                sawTheCraftDie);
    }

    /** More than one: the owner picks, exactly as for a shuttle box or a held tractor beam. */
    @Test
    public void twoParkedCraftOfferTheOwnerAChoice() {
        ShuttleBay bay = fighterBay();
        park(bay, "Alpha");
        park(bay, "Bravo");
        int b = bayIndexOf(bay);

        List<String> options = cva.dacChoiceOptionsForTest("ahull");

        assertEquals(2, options.size());
        assertTrue(options.toString(), options.contains("bay:" + b + ":balcony:Alpha"));
        assertTrue(options.toString(), options.contains("bay:" + b + ":balcony:Bravo"));
    }

    /** Craft on different bays' balconies are all candidates — the hit is on the ship. */
    @Test
    public void bothBalconiesAreOfferedTogether() {
        park(fighterBay(), "Alpha");
        park(secondFighterBay(), "Bravo");

        List<String> options = cva.dacChoiceOptionsForTest("ahull");

        assertEquals(2, options.size());
        assertEquals("addressed per bay", 2,
                options.stream().map(o -> o.split(":")[1]).distinct().count());
    }

    /**
     * The round trip that matters: an option string this class produced, handed back, destroys
     * that craft and no other. Both halves live on Ship so the format is never known in two
     * places — which is exactly the kind of split that rots.
     */
    @Test
    public void theOwnersPickDestroysThatCraftAndNoOther() {
        ShuttleBay bay = fighterBay();
        Shuttle alpha = park(bay, "Alpha");
        Shuttle bravo = park(bay, "Bravo");
        int hullBefore = ahullLeft();

        List<String> options = cva.dacChoiceOptionsForTest("ahull");
        String pickBravo = options.stream().filter(o -> o.endsWith(":Bravo")).findFirst().get();
        String killed = cva.applyBalconyDacChoice(pickBravo);

        assertEquals("Bravo", killed);
        assertTrue("Alpha survives", bay.isParked(alpha));
        assertFalse("Bravo is destroyed", bay.isParked(bravo));
        assertEquals("and the hull box is still spared", hullBefore, ahullLeft());
    }

    /** A pick naming a craft that is not parked must change nothing, not throw. */
    @Test
    public void aStalePickIsRefusedWithoutDamage() {
        ShuttleBay bay = fighterBay();
        park(bay, "Alpha");

        assertNull(cva.applyBalconyDacChoice("bay:1:balcony:Ghost"));
        assertNull("and a malformed one too", cva.applyBalconyDacChoice("bay:1:space:0"));
        assertEquals("nothing was destroyed", 1, cva.parkedCraft().size());
    }

    /**
     * The whole thing through the real DAC loop rather than its parts: with two craft parked,
     * a volley big enough to roll a rear hull hit must PAUSE for the owner, and the pause must
     * name this rule's options.
     * <p>
     * The DAC roll is random, so this takes a fresh ship per attempt and gives up after
     * enough tries to make a miss vanishingly unlikely rather than asserting on one roll.
     */
    @Test
    public void theDacLoopPausesForTheOwnerWhenTwoAreParked() throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            Ship ship = ShipLibrary.createShip(ShipLibrary.get("Federation", "CVA"));
            ship.setName("USS Enterprise");
            ShuttleBay bay = null;
            for (ShuttleBay b : ship.getShuttles().getBays())
                if (b.hasBalcony()) {
                    bay = b;
                    break;
                }
            AdminShuttle a = new AdminShuttle();
            a.setName("Alpha");
            AdminShuttle b2 = new AdminShuttle();
            b2.setName("Bravo");
            bay.park(a);
            bay.park(b2);

            Ship.DamageResult r = ship.applyInternalDamage(6, null);
            if (!r.choiceRequired || !"ahull".equals(r.choiceType))
                continue;

            assertFalse("the options are the parked craft", r.options.isEmpty());
            for (String option : r.options)
                assertTrue(option, option.contains(":balcony:"));
            assertEquals("both craft are still alive while the owner decides",
                    2, ship.parkedCraft().size());
            return;
        }
        fail("60 volleys of 6 internal points never produced a rear hull hit — "
                + "either the DAC changed or the balcony substitution is not wired in");
    }

    // ------------------------------------------------- no chain reaction

    /**
     * J1.531's flat statement: "no chain reactions (D12.0) will occur". An ARMED craft is the
     * case that would otherwise take a second box with it and spray a point into the ship, as
     * an armed occupant of a shuttle BOX does (D12.10). On the balcony it just dies.
     */
    @Test
    public void anArmedCraftOnTheBalconyDoesNotChainReact() {
        ShuttleBay bay = fighterBay();

        // A real armed craft off the CVA's own complement — an assault fighter carries a
        // FighterPhoton, which isArmed() counts (a functional non-phaser weapon).
        Shuttle armed = null;
        for (Shuttle s : bay.getInventory())
            if (s.isArmed()) {
                armed = s;
                break;
            }
        assertNotNull("fixture: the CVA should embark something armed", armed);
        assertTrue(bay.replaceShuttle(armed, null));
        assertTrue(bay.park(armed));

        int hullBefore = ahullLeft();
        int boxesBefore = bay.getRemainingSpaces();
        int emptyBefore = bay.getEmptySpaceCount();

        String label = cva.applySystemHitForTest("ahull");

        assertTrue(label, label.contains(armed.getName()));
        assertEquals("no second box taken", boxesBefore, bay.getRemainingSpaces());
        assertEquals("no box destroyed at all", emptyBefore, bay.getEmptySpaceCount());
        assertEquals("and no stray point into the hull", hullBefore, ahullLeft());
    }

    // ------------------------------------------------- J1.533, speed

    /** Speed 31 is explicitly fine: nothing may touch the balcony for going fast. */
    @Test
    public void speedThirtyOneIsNoThreatToParkedCraft() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay, "Alpha");

        cva.setSpeed(31);

        assertTrue("J1.533 allows any speed up to 31", bay.isParked(craft));
        assertEquals(1, cva.parkedCraft().size());
    }

    /** Exceeding 31 — disengagement by acceleration — strips every balcony on the ship. */
    @Test
    public void disengagingByAccelerationDestroysEveryParkedCraft() {
        ShuttleBay first = fighterBay();
        ShuttleBay second = secondFighterBay();
        park(first, "Alpha");
        park(first, "Bravo");
        park(second, "Charlie");
        Shuttle inside = first.getInventory().get(0);

        List<String> log = cva.stripBalconiesForAcceleration();

        assertEquals("all three are lost", 3, log.size());
        assertTrue(log.toString(), log.get(0).contains("J1.533"));
        assertTrue("both balconies are empty", cva.parkedCraft().isEmpty());
        assertTrue("craft INSIDE the bay are perfectly safe",
                first.getInventory().contains(inside));
    }

    @Test
    public void aShipWithNothingParkedLosesNothingOnDisengaging() {
        assertTrue(cva.stripBalconiesForAcceleration().isEmpty());
    }
}
