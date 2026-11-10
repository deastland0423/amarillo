package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.samples.FederationShips;

/**
 * (D4.40, D4.36, Annex #7E) The excess damage row, and the destruction guard that hangs off it.
 *
 * <blockquote>D4.40 ACT OF DESTRUCTION: "When there are no 'excess damage' boxes remaining, and one
 * additional excess damage hit is scored on the ship, the ship is destroyed. <b>This final excess
 * damage hit cannot be scored as excess damage so long as there is some other system (only: cargo,
 * repair, mine rack) on which an excess damage hit can be scored by Annex #7E.</b>"</blockquote>
 *
 * <blockquote>D4.36 CARGO: "If undestroyed cargo spaces exist on a ship, excess damage hits may be
 * scored against them <b>at the option of the owning player</b>. Also see (D4.40)."</blockquote>
 *
 * <h2>What was wrong</h2>
 * Running out of excess damage boxes was treated as the end of the ship. The rule says running out
 * of the whole ROW is. Every hull with cargo boxes therefore died early, and the error sat in two
 * places at once: the exhausted-chart branch and the walked-the-whole-column branch, which both
 * reach destruction and neither of which consulted cargo. A Base Station died forty points early,
 * on ten cargo boxes and thirty repair boxes it should have spent first.
 */
public class ExcessDamageRowTest {

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    // ---------------------------------------------------------------- the row

    /** (Annex #7E) "Excess Damage ....... Cargo, Repair, Mine Rack." Plus the track itself. */
    @Test
    public void theExcessRowOffersTheTrackThenCargoThenRepair() throws Exception {
        Ship bs = ShipLibrary.createShip(ShipLibrary.get("Federation", "BS"));
        bs.setName("Starbase Echo");

        assertEquals("the track goes first: it is the default and the historical behaviour",
                List.of("excess", "cargo", "repair"), bs.dacChoiceOptionsForTest("excess"));
        assertTrue("D4.36 makes it the owner's option while they have somewhere else to put it",
                bs.requiresPlayerChoiceForTest("excess"));
    }

    /**
     * A warship with no cargo, repair or racks has only its excess track, so nothing is asked.
     *
     * <p>Which is why this bug survived: on most hulls in the game D4.40 changes nothing at all.
     */
    @Test
    public void aWarshipWithNothingElseOnTheRowIsNotAsked() {
        Ship ca = plainHull();

        assertEquals(List.of("excess"), ca.dacChoiceOptionsForTest("excess"));
        assertFalse(ca.requiresPlayerChoiceForTest("excess"));
    }

    // ---------------------------------------------------------------- the forced order

    /**
     * (D4.40) The track empties first, and then the row keeps absorbing: cargo, repair, racks.
     *
     * <p>Asserted on a hull built for the purpose so the counts are small enough to read. The old
     * behaviour is the third assertion: the fourth call returned null and the caller killed the
     * ship, with four boxes still undestroyed on the sheet.
     */
    @Test
    public void onceTheTrackIsEmptyTheRowKeepsAbsorbing() {
        Ship ship = hullWith(2, 1, 1, 1); // 2 excess, 1 cargo, 1 repair, 1 rack

        assertTrue("excess box 1", ship.applySystemHitForTest("excess").contains("excess damage"));
        assertTrue("excess box 2", ship.applySystemHitForTest("excess").contains("excess damage"));
        assertEquals("the track is empty", 0, ship.getSpecialFunctions().getExcessDamage());

        assertEquals("and the row is NOT: cargo takes the next one (D4.40)",
                "cargo HIT", ship.applySystemHitForTest("excess"));
        assertEquals("repair HIT (G17.0)", ship.applySystemHitForTest("excess"));
        assertNotNull("then the rack", ship.applySystemHitForTest("excess"));

        assertTrue("every box on the row is spent", ship.dacChoiceOptionsForTest("excess").isEmpty());
        assertNull("only NOW does the row refuse", ship.applySystemHitForTest("excess"));
        assertFalse("and refusing is not destroying — that is the caller's call (C3.14)",
                ship.isDestroyed());
    }

    /**
     * (D4.36) Choosing cargo leaves the excess track untouched, which is the point of the option.
     *
     * <p>An excess damage box is the countdown to the ship exploding and crippling it outright
     * (S2.41 C); a cargo box is a crate. A player given the choice will not always spend the box
     * the auto-resolver picks for them.
     */
    @Test
    public void choosingCargoSparesTheExcessTrack() throws Exception {
        Ship bs = ShipLibrary.createShip(ShipLibrary.get("Federation", "BS"));
        bs.setName("Starbase Echo");
        int track = bs.getSpecialFunctions().getExcessDamage();

        String label = bs.applyDacChoiceHit("excess", "cargo", null);

        assertEquals("cargo HIT [chosen]", label);
        assertEquals("the crate took it", 9, bs.getHullBoxes().getAvailableCargo());
        assertEquals("and the track is intact", track,
                bs.getSpecialFunctions().getExcessDamage());
    }

    // ---------------------------------------------------------------- the destruction guard

    /**
     * (D4.40) The invariant, and the assertion this whole class exists for: a ship is never
     * destroyed while any box on the excess row survives.
     *
     * <p>Checked at the one moment it can be checked — the instant the engine declares the ship
     * dead — and driven through the real DAC with real dice. That makes it dice-PROOF rather than
     * dice-dependent: whatever route the chart takes, the row must be empty at the end of it. Run
     * the same hull against the old code and it dies with cargo, repair boxes and a mine rack
     * still undestroyed.
     */
    @Test
    public void aShipIsNeverDestroyedWhileTheRowStillHasABox() {
        for (int attempt = 0; attempt < 5; attempt++) {
            Ship ship = hullWith(2, 5, 3, 1);
            int points = 0;
            while (!ship.isDestroyed() && points < 2000) {
                ship.applyInternalDamage(1);
                points++;
            }
            assertTrue("attempt " + attempt + ": the ship did not die at all", ship.isDestroyed());
            assertTrue("attempt " + attempt + " — D4.40: destroyed after " + points
                    + " points with boxes still on the row: "
                    + ship.dacChoiceOptionsForTest("excess"),
                    ship.dacChoiceOptionsForTest("excess").isEmpty());
        }
    }

    /**
     * And the visible effect: the boxes buy real points of life.
     *
     * <p>Deliberately a WIDE margin on an absurd number of cargo boxes rather than an exact
     * difference, because points-to-destruction is not deterministic — measured spread on this
     * hull is roughly 108 to 140 for the same fixture, so a tidy {@code without + 10} assertion
     * passes or fails on the dice. A hundred boxes against a spread of thirty is a gap no run can
     * close by luck, which is what makes this worth asserting at all.
     */
    @Test
    public void theRowBuysRealPointsOfLife() {
        int withBoxes = pointsToDestroy(hullWith(6, 100, 0, 0));
        int without   = pointsToDestroy(hullWith(6, 0, 0, 0));

        assertTrue("a hundred cargo boxes must be worth far more than the dice noise:"
                + " with=" + withBoxes + " without=" + without,
                withBoxes > without + 50);
    }

    /**
     * And when the row really is empty, the ship dies and says which rule killed it.
     *
     * <p>The log line matters more than it looks: "excess damage — SHIP DESTROYED" was true of a
     * ship with forty undestroyed boxes, and nothing in the log said otherwise.
     */
    @Test
    public void whenTheRowIsEmptyTheShipDiesAndTheLogCitesTheRule() {
        Ship ship = hullWith(1, 1, 0, 0);
        List<String> log = new java.util.ArrayList<>();

        for (int i = 0; i < 400 && !ship.isDestroyed(); i++)
            log.addAll(ship.applyInternalDamage(1));

        assertTrue("the ship did die", ship.isDestroyed());
        assertTrue("and the killing line cites D4.40: " + lastLine(log),
                lastLine(log).contains("SHIP DESTROYED") && lastLine(log).contains("D4.40"));
        assertTrue("with nothing left on the row",
                ship.dacChoiceOptionsForTest("excess").isEmpty());
    }

    // ---------------------------------------------------------------- fixtures

    /**
     * Drive one hull to destruction a single point at a time, and report how many points it took.
     *
     * <p>Single points on purpose. A multi-point call resolves the whole volley inside one DAC
     * pass, so the count would come back as "one call" and say nothing.
     */
    private static int pointsToDestroy(Ship ship) {
        for (int points = 1; points <= 2000; points++) {
            ship.applyInternalDamage(1);
            if (ship.isDestroyed())
                return points;
        }
        throw new AssertionError(ship.getName() + " would not die in 2000 points");
    }

    /** A Federation CA, which has no cargo, no repair boxes and no mine racks. */
    private static Ship plainHull() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");
        return ship;
    }

    /** A CA with the excess track and the three conversion-row box kinds set to order. */
    private static Ship hullWith(int excess, int cargo, int repair, int racks) {
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("excess", excess);
        spec.put("cargo", cargo);
        spec.put("repair", repair);
        spec.put("mineracks", racks);
        Ship ship = new Ship();
        ship.init(spec);
        ship.setName("USS Testbed");
        return ship;
    }

    private static String lastLine(List<String> log) {
        return log.isEmpty() ? "" : log.get(log.size() - 1);
    }
}
