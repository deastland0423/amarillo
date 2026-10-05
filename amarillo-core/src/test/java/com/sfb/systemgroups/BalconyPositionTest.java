package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Shuttle;

/**
 * J1.53 balcony and track, slice one: a bay can park craft outside, and a balcony position is
 * NOT a shuttle box.
 * <p>
 * That second half is the design decision the whole feature rests on, so it is pinned here
 * rather than left as a comment. A shuttle BOX is destroyed on an "any weapon" hit (J1.414),
 * chain reacts (J1.415), and owns a ready rack and a fighter capacitor. A balcony position has
 * none of it — the owner's rulings, 2026-10-02: a position cannot be destroyed, damage reaches
 * the parked SHUTTLES as rear-hull hits, and with nothing parked the balcony does not interact
 * with damage at all.
 * <p>
 * The practical consequence, and why these tests matter more than they look: nothing that walks
 * {@code getSpaces()} or {@code getInventory()} should ever see a parked craft. That separation
 * is what lets every existing damage, arming and deck-crew path stay correct without being told
 * balconies exist.
 * <p>
 * NOT YET IMPLEMENTED, by design: transfers at the J1.50 hatch rate (slice 2), launching and
 * landing from the balcony (slice 3), rear-hull destruction and the speed-31 rule (slice 4),
 * and the bars on firing and deck-crew work (slice 5). {@link ShuttleBay#park} enforces
 * capacity and nothing else; the rules belong to the caller that can refuse with a reason.
 */
public class BalconyPositionTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    private static Shuttle shuttle(String name) {
        AdminShuttle s = new AdminShuttle();
        s.setName(name);
        return s;
    }

    private static ShuttleBay bayWithBalcony(int positions) {
        ShuttleBay bay = new ShuttleBay(null);
        bay.setBalconyPositions(positions);
        return bay;
    }

    // ---------------------------------------------------------------- the capacity

    @Test
    public void mostBaysHaveNoBalconyAtAll() {
        ShuttleBay plain = new ShuttleBay(null);

        assertFalse(plain.hasBalcony());
        assertEquals(0, plain.getBalconyPositions());
        assertEquals(0, plain.balconyFree());
        assertFalse("nothing can be parked on a bay with no balcony",
                plain.park(shuttle("Shuttle-1")));
    }

    @Test
    public void aBalconyParksUpToItsPositionsAndNoMore() {
        ShuttleBay bay = bayWithBalcony(3);

        assertTrue(bay.hasBalcony());
        assertEquals(3, bay.balconyFree());

        assertTrue(bay.park(shuttle("A")));
        assertTrue(bay.park(shuttle("B")));
        assertTrue(bay.park(shuttle("C")));
        assertEquals(0, bay.balconyFree());

        assertFalse("the fourth has nowhere to go", bay.park(shuttle("D")));
        assertEquals(3, bay.getBalcony().size());
    }

    @Test
    public void unparkingFreesThePosition() {
        ShuttleBay bay = bayWithBalcony(2);
        Shuttle a = shuttle("A");
        bay.park(a);
        assertEquals(1, bay.balconyFree());

        assertTrue(bay.unpark(a));

        assertEquals(2, bay.balconyFree());
        assertFalse(bay.isParked(a));
        assertFalse("and unparking it twice does nothing", bay.unpark(a));
    }

    @Test
    public void thesameCraftCannotOccupyTwoPositions() {
        ShuttleBay bay = bayWithBalcony(3);
        Shuttle a = shuttle("A");

        assertTrue(bay.park(a));
        assertFalse(bay.park(a));
        assertEquals(1, bay.getBalcony().size());
    }

    /** Capacity is fixed: a position cannot be destroyed, so the number never falls. */
    @Test
    public void positionsCannotBeLost() {
        ShuttleBay bay = bayWithBalcony(3);
        bay.park(shuttle("A"));

        // Whatever happens to the bay's BOXES, the balcony's positions are unchanged — there is
        // deliberately no API to destroy one, which is the point of this assertion.
        assertEquals(3, bay.getBalconyPositions());
    }

    // ------------------------------------------------- a position is not a shuttle box

    /**
     * The separation the whole design rests on. Parking a craft must not create a space, must
     * not appear in the bay's inventory, and must not change any box count — or every damage,
     * arming and deck-crew path that walks the spaces would silently start seeing it.
     */
    @Test
    public void parkedCraftAreInvisibleToTheBaysSpaces() {
        ShuttleBay bay = bayWithBalcony(3);
        bay.addSpace(new ShuttleSpace(shuttle("Inside-1")));

        int spaces = bay.getTotalSpaces();
        int empty = bay.getEmptySpaceCount();
        int inventory = bay.getInventory().size();

        assertTrue(bay.park(shuttle("Outside-1")));

        assertEquals("parking creates no space", spaces, bay.getTotalSpaces());
        assertEquals("and occupies none", empty, bay.getEmptySpaceCount());
        assertEquals("and the bay's inventory is what is INSIDE it",
                inventory, bay.getInventory().size());
        assertEquals("the parked craft is only on the balcony", 1, bay.getBalcony().size());
    }

    /** The list is read-only to callers: parking goes through park() so capacity is enforced. */
    @Test
    public void theBalconyListCannotBeWrittenToDirectly() {
        ShuttleBay bay = bayWithBalcony(1);
        try {
            bay.getBalcony().add(shuttle("Sneaky"));
            fail("the balcony list must not be modifiable from outside");
        } catch (UnsupportedOperationException expected) {
            assertTrue(bay.getBalcony().isEmpty());
        }
    }

    // ---------------------------------------------------------------- the data

    /**
     * J1.53's note names the ships; these two are in the data. Per BAY, which the CVA is the
     * argument for: "Each Size-1 Fighter bay has six balcony positions: Total of 12" — so its
     * two fighter bays have six each and its admin bay has none.
     */
    @Test
    public void theFederationCvaHasSixOnEachFighterBayAndNoneOnItsAdminBay() {
        Ship cva = ShipLibrary.createShip(ShipLibrary.get("Federation", "CVA"));

        assertEquals("three bays", 3, cva.getShuttles().getBays().size());

        int withBalcony = 0, total = 0;
        for (ShuttleBay bay : cva.getShuttles().getBays()) {
            if (!bay.hasBalcony())
                continue;
            withBalcony++;
            total += bay.getBalconyPositions();
            assertEquals("each fighter bay has six", 6, bay.getBalconyPositions());
        }

        assertEquals("only the two fighter bays", 2, withBalcony);
        assertEquals("total of 12, as the SSD says", 12, total);
    }

    /** Gorn BC: "Each bay has three balcony positions: Total of six". */
    @Test
    public void theGornBcHasThreeOnEachOfTwoBays() {
        Ship bc = ShipLibrary.createShip(ShipLibrary.get("Gorn", "BC"));

        assertEquals(2, bc.getShuttles().getBays().size());
        int total = 0;
        for (ShuttleBay bay : bc.getShuttles().getBays()) {
            assertEquals(3, bay.getBalconyPositions());
            total += bay.getBalconyPositions();
        }
        assertEquals(6, total);
    }

    /**
     * The balcony is a GORN trait, fleet-wide. "On Gorn ships this is usually on the wings"
     * is J1.53's own aside, and the Gorn are the faction known for the system: every hull in
     * their list has one on every bay, from the SC up to the DN.
     * <p>
     * Asserted over the whole faction rather than ship by ship, so a new Gorn hull that
     * forgets its balcony fails here instead of simply being unable to do something the rest
     * of the fleet can.
     */
    @Test
    public void everyGornShipHasABalconyOnEveryBay() {
        java.util.Set<String> missing = new java.util.TreeSet<>();
        int checked = 0;
        for (ShipSpec spec : ShipLibrary.all()) {
            if (!"Gorn".equals(spec.faction))
                continue;
            checked++;
            for (ShuttleBay bay : ShipLibrary.createShip(spec).getShuttles().getBays())
                if (!bay.hasBalcony())
                    missing.add(spec.type);
        }

        assertTrue("fixture: the Gorn list should not be empty", checked >= 10);
        assertTrue("Gorn hulls with a bay that has no balcony: " + missing, missing.isEmpty());
    }

    /**
     * And OUTSIDE the Gorn it stays rare. The default must be zero, so a stray
     * {@code balconyPositions} on some other faction's hull — or a key landing in the wrong bay —
     * fails here rather than quietly granting a ship a system it does not have.
     *
     * <h2>A roster on purpose, so adding one takes a decision</h2>
     * This list is deliberately a roster rather than an invariant, which means every new balcony
     * hull fails this test once. That is the point: a balcony is rare enough that a human should
     * confirm the SSD gives it one, and it caught the Klingon C8V on the day it was added
     * (2026-10-04). The owner's reason is why that one belongs here, and it is the sort of thing
     * only the sheet can tell you:
     *
     * <blockquote>"This ship is strange. The two bays are stacked on top of each other. The upper
     * bay uses tunnels to launch. The lower bay is close enough to the deck that it has
     * balconies."</blockquote>
     *
     * So the asymmetry — four balcony positions on one fighter bay and none on the other, where
     * the Federation CVA has six on each — is the geometry of the ship, not an omission. It is
     * also why {@code balconyPositions} is per bay and not per ship.
     */
    @Test
    public void outsideTheGornOnlyTheFederationCvaAndKlingonC8vHaveOne() {
        java.util.Set<String> withBalcony = new java.util.TreeSet<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if ("Gorn".equals(spec.faction))
                continue;
            for (ShuttleBay bay : ShipLibrary.createShip(spec).getShuttles().getBays())
                if (bay.hasBalcony())
                    withBalcony.add(spec.faction + " " + spec.type);
        }

        assertEquals("only the hulls whose SSDs give them one",
                java.util.Set.of("Federation CVA", "Klingon C8V"), withBalcony);
    }

    /**
     * The field has to survive {@code toInitMap}, which is the bridge from the ship file to the
     * bay — and it is the step a new bay key is most likely to be dropped at. Worse here than
     * elsewhere: the object form of a bay is only used when something forces it, so a bay with
     * a balcony and NOTHING else would fall back to the plain list and lose it silently.
     */
    @Test
    public void aBalconySurvivesWhenItIsTheOnlyThingTheBayDeclares() throws Exception {
        String json = "{\"faction\":\"Federation\",\"type\":\"T1\",\"turnMode\":\"A\","
                + "\"shuttleBays\":[{\"shuttles\":[\"admin\"],\"balconyPositions\":4}]}";
        ShipSpec spec = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, ShipSpec.class);

        assertEquals("parsed off the file", 4, spec.shuttleBays.get(0).balconyPositions);

        Ship ship = new Ship();
        ship.init(spec.toInitMap());

        assertEquals("and reached the bay", 4,
                ship.getShuttles().getBays().get(0).getBalconyPositions());
    }
}
