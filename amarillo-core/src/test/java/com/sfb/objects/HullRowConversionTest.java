package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.Weapon;

/**
 * (Annex #7E, D4.351, G28.0) The two hull rows of the DAMAGE CONVERSION CHART.
 *
 * <blockquote>Annex #7E: "Forward Hull ... C Hull, <b>Repair, Barracks</b>." and "Aft Hull ... C
 * Hull, <b>Repair, Barracks</b>. Shuttles on Balconies (J1.531) must be hit first."</blockquote>
 *
 * <blockquote>D4.351: "Barracks (G28.0) and Repair (G17.0) are also destroyed by hull hits. If a
 * ship has either of the acceptable types of hull and one of the systems listed on Annex #7E,
 * <b>the hit must be scored on one of them</b>."</blockquote>
 *
 * <blockquote>G28.2: "Barracks can be destroyed by F Hull or A Hull damage points. If all normal
 * hull boxes of one (or both) type(s) are destroyed but undestroyed barracks boxes remain, hull
 * hits <b>must</b> be scored on barracks."</blockquote>
 *
 * <h2>Why this matters most on the bases</h2>
 * Nine Base Stations carry thirty repair boxes each against ten hull boxes. Until repair boxes
 * were modelled, a hull hit on a base had ten places to land where its SSD prints forty, and the
 * owner had no decision to make. One hull in the game has barracks (the Federation CMC, three
 * boxes), and those were being dropped on the floor by {@code ShipSpec.toMap} as well.
 */
public class HullRowConversionTest {

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    // ---------------------------------------------------------------- nothing changes on a CA

    /**
     * A hull with nothing but hull boxes still resolves silently, which is most hulls in the game.
     *
     * <p>Kept first because it is the regression that matters for everyone else: adding two box
     * kinds to this row must not start a dialog on every hull hit ever scored.
     */
    @Test
    public void aHullWithOnlyHullBoxesIsStillNotAsked() {
        Ship ca = plainHull();

        assertEquals(List.of("fhull"), ca.dacChoiceOptionsForTest("fhull"));
        assertEquals(List.of("ahull"), ca.dacChoiceOptionsForTest("ahull"));
        assertFalse(ca.requiresPlayerChoiceForTest("fhull"));
        assertFalse(ca.requiresPlayerChoiceForTest("ahull"));

        int fhull = ca.getHullBoxes().getAvailableFhull();
        assertEquals("fhull HIT", ca.applySystemHitForTest("fhull"));
        assertEquals(fhull - 1, ca.getHullBoxes().getAvailableFhull());
    }

    /**
     * And C hull still substitutes once the named type is gone, with the same label.
     *
     * <p>Built by hand because the Federation CA has no C hull at all - {@code ca.json} is
     * {@code fhull: 12, ahull: 4} and nothing else. Worth recording, because the first version of
     * this test assumed the CA would fall back to C hull and read the {@code null} it got as a
     * regression when it was the right answer: with no F hull and no C hull left, a forward hull
     * point can be scored nowhere and the DAC advances.
     */
    @Test
    public void cHullStillSubstitutesWhenTheNamedTypeRunsOut() {
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("fhull", 1);
        spec.put("chull", 2);
        Ship ship = new Ship();
        ship.init(spec);
        ship.setName("USS Unified");

        assertEquals("the F hull box goes first", "fhull HIT",
                ship.applySystemHitForTest("fhull"));
        assertEquals("then C hull substitutes", "chull (fhull exhausted) HIT",
                ship.applySystemHitForTest("fhull"));
    }

    /** With neither acceptable type of hull box left, the row is empty and the DAC advances. */
    @Test
    public void withNoHullBoxOfEitherTypeTheForwardRowIsEmpty() {
        Ship ca = plainHull();
        while (ca.getHullBoxes().getAvailableFhull() > 0)
            ca.getHullBoxes().damageFhull();
        assertEquals("fixture: ca.json declares no C hull", 0,
                ca.getHullBoxes().getAvailableChull());

        assertTrue(ca.dacChoiceOptionsForTest("fhull").isEmpty());
        assertNull(ca.applySystemHitForTest("fhull"));
    }

    // ---------------------------------------------------------------- repair boxes

    /** (D4.351) A Base Station's hull row is a real decision: ten hull boxes or thirty repair. */
    @Test
    public void aBaseStationChoosesBetweenHullAndRepair() throws Exception {
        Ship bs = ShipLibrary.createShip(ShipLibrary.get("Federation", "BS"));
        bs.setName("Starbase Echo");

        assertEquals(List.of("fhull", "repair"), bs.dacChoiceOptionsForTest("fhull"));
        assertEquals(List.of("ahull", "repair"), bs.dacChoiceOptionsForTest("ahull"));
        assertTrue(bs.requiresPlayerChoiceForTest("fhull"));

        String label = bs.applyDacChoiceHit("fhull", "repair", null);

        assertEquals("repair HIT (G17.0) [chosen]", label);
        assertEquals(29, bs.getHullBoxes().getAvailableRepair());
        assertEquals("the hull boxes are untouched", 10,
                bs.getHullBoxes().getAvailableChull());
    }

    /** (D4.351) And once the hull boxes are gone the hit MUST land on repair. */
    @Test
    public void withTheHullGoneTheHitMustLandOnRepair() throws Exception {
        Ship roc = ShipLibrary.createShip(ShipLibrary.get("Romulan", "ROC"));
        roc.setName("RIS Consul");
        assertEquals("roc.json: four repair boxes", 4, roc.getHullBoxes().getAvailableRepair());

        while (roc.getHullBoxes().getAvailableFhull() > 0)
            roc.getHullBoxes().damageFhull();

        assertEquals("no hull box can take a forward hit any more",
                List.of("repair"), roc.dacChoiceOptionsForTest("fhull"));
        assertFalse("one candidate, so no dialog", roc.requiresPlayerChoiceForTest("fhull"));
        assertEquals("repair HIT (G17.0)", roc.applySystemHitForTest("fhull"));
        assertEquals(3, roc.getHullBoxes().getAvailableRepair());
    }

    // ---------------------------------------------------------------- barracks (G28.0)

    /** (G28.2) The Federation CMC's three barracks boxes are hull-row candidates. */
    @Test
    public void theCmcChoosesBetweenHullAndBarracks() throws Exception {
        Ship cmc = commandoCruiser();

        assertEquals(List.of("fhull", "barracks"), cmc.dacChoiceOptionsForTest("fhull"));
        assertTrue(cmc.requiresPlayerChoiceForTest("fhull"));
    }

    /**
     * (G28.33) "Any boarding parties in a destroyed barracks box are destroyed."
     *
     * <p>The CMC quarters eight parties (G28.32: all non-guards are in the barracks) across three
     * boxes, so an even spread is two a box and the destroyed box costs two. The spread is a
     * DEFAULT and not a derivation — see {@code destroyOneBarracksBox} and the backlog, because
     * G28.32 lets the owner divide them as he likes and there is no UI for that.
     */
    @Test
    public void destroyingABarracksBoxKillsTheBoardingPartiesInIt() throws Exception {
        Ship cmc = commandoCruiser();
        assertEquals("cmc.json: eight boarding parties", 8,
                cmc.getCrew().getAvailableBoardingParties());
        assertEquals("all eight are quartered, none on guard", 8,
                cmc.boardingPartiesInBarracks());

        String label = cmc.applyDacChoiceHit("fhull", "barracks", null);

        assertNotNull(label);
        assertTrue("the log says what died and cites the rule: " + label,
                label.contains("2 boarding parties lost") && label.contains("G28.33"));
        assertEquals("one box gone", 2, cmc.getHullBoxes().getAvailableBarracks());
        assertEquals("and two parties with it", 6,
                cmc.getCrew().getAvailableBoardingParties());
    }

    /** An empty barracks box costs a box and nobody. */
    @Test
    public void anEmptyBarracksBoxCostsNobody() throws Exception {
        Ship cmc = commandoCruiser();
        cmc.getCrew().setAvailableBoardingParties(0);

        String label = cmc.applyDacChoiceHit("fhull", "barracks", null);

        assertEquals("barracks HIT (empty, G28.0) [chosen]", label);
        assertEquals(2, cmc.getHullBoxes().getAvailableBarracks());
    }

    /**
     * (G28.32) Guards are not in the barracks, so they do not die with a box.
     *
     * <p>"All boarding parties ... are assumed to be in the barracks <b>unless assigned as
     * guards</b> (D7.83)."
     *
     * <p>Honoured by the ROSTER rather than by arithmetic, which is the trap this pins:
     * {@code GuardPosts.assign} pulls the party off the roster while it is posted (D7.834), so
     * {@code getAvailableBoardingParties()} has already dropped it. The obvious-looking
     * {@code available - totalPosted()} removes each guard twice, and this test fails if anyone
     * writes it.
     */
    @Test
    public void guardsAreNotQuarteredInTheBarracks() throws Exception {
        Ship cmc = commandoCruiser();
        int posted = postSomeGuards(cmc);
        assertTrue("fixture: some parties are on guard duty", posted > 0);

        assertEquals("a posted guard is already off the roster, so it must not be"
                + " subtracted a second time",
                8 - posted, cmc.boardingPartiesInBarracks());
        assertEquals("which is exactly what the roster says", 8 - posted,
                cmc.getCrew().getAvailableBoardingParties());
    }

    /**
     * (G28.32) And the surplus over ten a box is loose in the hull, not quartered.
     *
     * <p>"Any surplus marine squads are considered to be distributed around the ship as they
     * would be on a ship with no barracks."
     */
    @Test
    public void theSurplusOverTenABoxIsNotQuartered() throws Exception {
        Ship cmc = commandoCruiser();
        cmc.getCrew().setAvailableBoardingParties(40); // three boxes hold thirty

        assertEquals("ten a box is the cap, and the other ten are loose",
                30, cmc.boardingPartiesInBarracks());
    }

    // ---------------------------------------------------------------- J1.531 still comes first

    /**
     * (Annex #7E) "Shuttles on Balconies (J1.531) must be hit first." So while anything is parked
     * the row is the balcony and nothing else — not one candidate among several.
     */
    @Test
    public void aParkedCraftPreemptsTheWholeAftRow() throws Exception {
        Ship cva = ShipLibrary.createShip(ShipLibrary.get("Federation", "CVA"));
        cva.setName("USS Enterprise");
        cva.getHullBoxes().init(hullWith(10, 4, 2)); // hull, repair AND barracks, all available
        ShuttleBay bay = cva.getShuttles().getBays().stream()
                .filter(ShuttleBay::hasBalcony).findFirst().orElseThrow();
        com.sfb.objects.shuttles.AdminShuttle craft = new com.sfb.objects.shuttles.AdminShuttle();
        craft.setName("Alpha");
        assertTrue(bay.park(craft));

        List<String> aft = cva.dacChoiceOptionsForTest("ahull");

        assertEquals("the balcony is the whole row while it has something on it", 1, aft.size());
        assertTrue(aft.get(0), aft.get(0).contains(":balcony:Alpha"));
        assertFalse("one candidate, so still no dialog (J1.531: not an option)",
                cva.requiresPlayerChoiceForTest("ahull"));

        String label = cva.applySystemHitForTest("ahull");
        assertEquals("balcony HIT (Alpha destroyed, J1.531)", label);

        assertEquals("and with the balcony bare the row is itself again",
                List.of("ahull", "repair", "barracks"), cva.dacChoiceOptionsForTest("ahull"));
    }

    /** The FORWARD row never sees the balcony: J1.531 names the rear hull only. */
    @Test
    public void theForwardRowIgnoresTheBalcony() throws Exception {
        Ship cva = ShipLibrary.createShip(ShipLibrary.get("Federation", "CVA"));
        cva.setName("USS Enterprise");
        ShuttleBay bay = cva.getShuttles().getBays().stream()
                .filter(ShuttleBay::hasBalcony).findFirst().orElseThrow();
        com.sfb.objects.shuttles.AdminShuttle craft = new com.sfb.objects.shuttles.AdminShuttle();
        craft.setName("Alpha");
        assertTrue(bay.park(craft));

        assertEquals(List.of("fhull"), cva.dacChoiceOptionsForTest("fhull"));
        assertNull("and a balcony string is not the forward row's to apply",
                cva.applyDacChoiceHit("fhull", "bay:0:balcony:Alpha", null));
        assertTrue("Alpha is still parked", bay.isParked(craft));
    }

    // ---------------------------------------------------------------- through the real resolver

    /**
     * The regression: a hull-row pick of "repair" must not be swallowed by the balcony branch.
     *
     * <p>{@code DamageResolver} keys a whole branch on the dacType {@code "ahull"}/{@code
     * "afthull"} and hands the chosen option to {@code applyBalconyDacChoice}, which returns null
     * for anything that is not a parked craft. Without the guard, choosing a repair box logged
     * "no parked craft matched repair" and the damage point vanished — the defender's hull hit
     * simply did not happen. Only reachable through the resolver, which is why this test fires a
     * real volley.
     */
    @Test
    public void choosingARepairBoxOnTheAftRowResolvesThroughTheResolver() {
        for (int attempt = 0; attempt < 120; attempt++) {
            Game game = volleyUntilPaused();
            Ship target = shipNamed(game, "Target");

            for (int step = 0; step < 80
                    && game.getCurrentPhase() == Game.ImpulsePhase.DAC_CHOICE; step++) {
                Game.PendingDacChoice pending = game.getPendingDacChoices().get(0);
                boolean aftRow = "ahull".equals(pending.dacType)
                        || "afthull".equals(pending.dacType);

                if (aftRow && pending.options.contains("repair")) {
                    int repairBefore = target.getHullBoxes().getAvailableRepair();

                    Game.ActionResult r = game.submitDacChoice("repair");

                    assertTrue(r.getMessage(), r.isSuccess());
                    // Asserted on the LOG rather than on the box count, because submitDacChoice
                    // applies the pick and then goes on resolving the rest of the volley, which
                    // can perfectly well spend a second repair box on its way through. A tidy
                    // `repairBefore - 1` was flaky for exactly that reason - it failed on two of
                    // three runs with the engine behaving correctly.
                    assertTrue("the pick was routed to the repair box: " + r.getMessage(),
                            r.getMessage().contains("player chose repair")
                                    && r.getMessage().contains("repair HIT (G17.0)"));
                    assertFalse("the balcony branch must not have seen it, which is what used"
                            + " to swallow the point: " + r.getMessage(),
                            r.getMessage().contains("no parked craft matched"));
                    assertTrue("and a box really went",
                            target.getHullBoxes().getAvailableRepair() < repairBefore);
                    return;
                }
                game.submitDacChoice(pending.options.get(0));
            }
        }
        fail("120 volleys never produced an aft-hull DAC choice offering a repair box, so the"
                + " resolver routing this test exists for was never exercised. That is a broken"
                + " fixture, not a pass.");
    }

    // ---------------------------------------------------------------- fixtures

    private static Ship plainHull() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");
        return ship;
    }

    /** The one hull in the game with barracks boxes. */
    private static Ship commandoCruiser() throws Exception {
        Ship cmc = ShipLibrary.createShip(ShipLibrary.get("Federation", "CMC"));
        cmc.setName("USS Commando");
        assertEquals("fixture: cmc.json declares barracks: 3",
                3, cmc.getHullBoxes().getAvailableBarracks());
        return cmc;
    }

    private static Map<String, Object> hullWith(int chull, int repair, int barracks) {
        Map<String, Object> m = new HashMap<>();
        m.put("ahull", chull);
        m.put("fhull", chull);
        m.put("repair", repair);
        m.put("barracks", barracks);
        return m;
    }

    /** Post guards on this hull's weapons, and report how many actually went up. */
    private static int postSomeGuards(Ship ship) {
        int before = ship.getGuardPosts().totalPosted();
        Game game = new Game();
        game.getShips().add(ship);
        for (com.sfb.properties.SystemTarget t : game.getTargetableSystems(ship)) {
            if (t.getType() != com.sfb.properties.SystemTarget.Type.WEAPON)
                continue;
            ship.getGuardPosts().assign(t,
                    com.sfb.properties.BoardingPartyQuality.NORMAL);
            if (ship.getGuardPosts().totalPosted() - before >= 3)
                break;
        }
        return ship.getGuardPosts().totalPosted() - before;
    }

    /** A Federation CA with repair boxes, under fire. */
    private static Game volleyUntilPaused() {
        Game game = new Game();

        Ship attacker = new Ship();
        attacker.init(FederationShips.getFedCa());
        attacker.setName("Attacker");
        attacker.setLocation(new Location(10, 10));
        attacker.setFacing(1);
        attacker.setActiveFireControl(true);

        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("repair", 6);
        Ship target = new Ship();
        target.init(spec);
        target.setName("Target");
        target.setLocation(new Location(10, 11));
        target.setFacing(1);
        target.setActiveFireControl(true);
        for (int shield = 1; shield <= 6; shield++)
            target.getShields().setShieldValue(shield, 0);

        game.getShips().add(attacker);
        game.getShips().add(target);

        game.startTurn();
        for (Ship ship : new Ship[] { attacker, target }) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(ship.getLifeSupportCost());
            e.setFireControl(ship.getFireControlCost());
            e.setPhaserCapacitor(6);
            game.submitAllocation(ship, e);
        }
        for (int i = 0; i < 40 && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; i++)
            game.advancePhase();

        List<Weapon> fired = new ArrayList<>();
        for (Weapon w : attacker.getWeapons().getPhaserList())
            if (w.isFunctional())
                fired.add(w);
        game.fireWeapons(attacker, target, fired, 1, 1, 1);
        for (int i = 0; i < 12 && game.getCurrentPhase() != Game.ImpulsePhase.DAC_CHOICE; i++)
            game.advancePhase();
        return game;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("fixture: no ship called " + name);
    }
}
