package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * (G28.33) Boarding parties quartered in a barracks box are exempt from internal-damage
 * casualties — the quality that makes a troop ship a troop ship.
 *
 * <blockquote>G28.33: "Any boarding parties in a destroyed barracks box are destroyed.
 * <b>Boarding parties in a barracks box are not subject to casualties under (G9.2) and
 * (D7.21)</b>, and hits on barracks boxes do not count for these purposes."</blockquote>
 *
 * <blockquote>G28.32: "All boarding parties (a.k.a. marine squads) on board a ship with barracks
 * are assumed to be in the barracks <b>unless assigned as guards</b> (D7.83). ... up to the limit
 * of ten per barracks box. <b>Any surplus marine squads are considered to be distributed around
 * the ship</b> as they would be on a ship with no barracks."</blockquote>
 *
 * <h2>This is not a marginal carve-out</h2>
 * Shelter is the DEFAULT, not something the owner assigns: every non-guard party on a hull with
 * barracks is quartered automatically. The Federation CMC declares {@code barracks: 3} and
 * {@code boardingParties: 8}, and three boxes hold thirty, so its entire marine complement is
 * sheltered with room for twenty-two more. Without this rule the one hull in the game built to
 * carry marines loses them to generic internal damage like any freighter.
 *
 * <h2>Only the D7.21 half needs code</h2>
 * G28.33 names G9.2 as well, but (G9.21) kills CREW UNITS — "every tenth internal damage point
 * kills one crew unit" — and our implementation of it only ever touches
 * {@code getAvailableCrewUnits()}. It cannot reach a boarding party, so the G9.2 half of the
 * exemption is satisfied by construction. (D7.21) is the rule that draws on parties, and that is
 * what these tests pin.
 *
 * <p>The second half of G28.33 — "hits on barracks boxes do not count for these purposes" — is
 * deliberately NOT built; see the backlog for why.
 */
public class BarracksCasualtyExemptionTest {

    /** (D7.21) The log line this rule must suppress. */
    private static final String D7_21_LINE = "(D7.21)";

    /** Enough internal damage to clear D7.21's four ignored events with room to spare. */
    private static final int ENOUGH_FOR_CASUALTIES = 50;

    /**
     * Points driven at the barracks fixtures: comfortably past D7.21's fifth casualty event, and
     * comfortably short of the 500 C hull boxes {@link #deepHulledCommandoCruiser} carries.
     *
     * <p>Without the exemption this is four casualty events, so four parties, which is why the
     * sheltered assertion is not vacuous.
     */
    private static final int POINTS_DRIVEN = 100;

    /** A complement comfortably under the CMC's thirty berths, so nobody is exposed. */
    private static final int ALL_SHELTERED = 20;

    /**
     * Points driven at the surplus fixture: enough internal damage for D7.21 to generate more
     * casualty events (about fifteen) than the ten parties over the barracks' capacity, so the
     * exemption is the only thing that can stop the eleventh.
     */
    private static final int POINTS_FOR_MORE_EVENTS_THAN_SURPLUS = 250;

    /** Casualty events needed before the surplus fixture proves anything: more than ten. */
    private static final int ENOUGH_FOR_ELEVEN_EVENTS = 150;

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    // ------------------------------------------------------------------ the arithmetic directly

    /**
     * (G28.32) The exposed count is the parties beyond the barracks' capacity, and nothing else.
     *
     * <p>Driven through the accessor rather than the damage loop because the capacity cliff is
     * exact arithmetic and the damage loop is unseeded dice.
     */
    @Test
    public void exposedCountIsThePartiesBeyondCapacity() {
        Ship cmc = commandoCruiser(); // three barracks boxes, so thirty berths

        cmc.getCrew().setAvailableBoardingParties(20);
        assertEquals("under capacity, everybody is sheltered",
                20, cmc.boardingPartiesInBarracks());
        assertEquals(0, cmc.exposedBoardingParties());

        cmc.getCrew().setAvailableBoardingParties(30);
        assertEquals("exactly at capacity, still everybody",
                30, cmc.boardingPartiesInBarracks());
        assertEquals(0, cmc.exposedBoardingParties());

        cmc.getCrew().setAvailableBoardingParties(40);
        assertEquals("thirty sheltered", 30, cmc.boardingPartiesInBarracks());
        assertEquals("ten over capacity are loose in the hull (G28.32)",
                10, cmc.exposedBoardingParties());
    }

    /**
     * The Federation CMC as the data actually declares it, which is two parties over capacity.
     *
     * <p>Asserts the RELATIONSHIP and nothing else, on purpose. {@code cmc.json} carried eight
     * parties while this rule was being built and was raised to thirty-two against three boxes'
     * thirty berths, which turned the one hull with barracks from entirely sheltered into one
     * running a small surplus — and broke five tests that had hard-coded the old figure. Pinning
     * the new figure here would just queue up the same breakage, so the complement is read, not
     * asserted. Every test that needs a specific number sets it itself.
     */
    @Test
    public void everyPartyIsEitherShelteredOrExposed() {
        Ship cmc = commandoCruiser();
        int total = cmc.getCrew().getAvailableBoardingParties();
        int berths = cmc.getHullBoxes().getAvailableBarracks()
                * Ship.BOARDING_PARTIES_PER_BARRACKS;

        assertTrue("fixture: the CMC must carry some parties", total > 0);
        assertEquals("sheltered and exposed partition the complement: never both, never neither",
                total, cmc.boardingPartiesInBarracks() + cmc.exposedBoardingParties());
        assertEquals("shelter is the complement capped at the berths (G28.32)",
                Math.min(total, berths), cmc.boardingPartiesInBarracks());
        assertEquals("and the exposed are exactly the surplus over those berths",
                Math.max(0, total - berths), cmc.exposedBoardingParties());
    }

    /** A hull with no barracks shelters nobody, which is every hull but one. */
    @Test
    public void aHullWithNoBarracksExposesEveryParty() {
        Ship ca = ShipLibrary.createShip(ShipLibrary.get("Federation", "CA"));
        ca.setName("USS Unsheltered");

        assertEquals("fixture: ca.json declares no barracks",
                0, ca.getHullBoxes().getAvailableBarracks());
        assertEquals(ca.getCrew().getAvailableBoardingParties(), ca.exposedBoardingParties());
        assertTrue("fixture: the CA must carry some parties for this to mean anything",
                ca.exposedBoardingParties() > 0);
    }

    /**
     * Destroying a barracks box shrinks the shelter, and exposes whoever no longer fits.
     *
     * <p>The shelter is the BOX, not a flag on the party, so {@code boardingPartiesInBarracks}
     * reads {@code getAvailableBarracks()} — the SURVIVING count — every time it is asked. Shoot
     * a box away and ten more parties are loose in the hull, taking their chances the ordinary
     * way, which falls out of the model rather than needing a rule of its own.
     *
     * <p>Loaded over capacity on purpose. The first version of this test started from the CMC's
     * own eight parties and destroyed all three boxes, which killed every party and left it
     * asserting {@code 0 == 0}: a mutant that read the ORIGINAL box count, so that destroyed
     * barracks went on sheltering people, passed it cleanly. Survivors are what make the
     * shrinking shelter observable.
     */
    @Test
    public void destroyingABarracksBoxExposesWhoeverNoLongerFits() {
        Ship cmc = commandoCruiser();
        cmc.getCrew().setAvailableBoardingParties(40); // three boxes hold thirty; ten are loose
        assertEquals(30, cmc.boardingPartiesInBarracks());
        assertEquals(10, cmc.exposedBoardingParties());

        cmc.applyDacChoiceHit("fhull", "barracks", null);

        assertEquals("one box gone", 2, cmc.getHullBoxes().getAvailableBarracks());
        assertEquals("ten parties died with it (G28.33 first sentence)",
                30, cmc.getCrew().getAvailableBoardingParties());
        assertEquals("two boxes shelter twenty, not thirty",
                20, cmc.boardingPartiesInBarracks());
        assertEquals("so ten of the survivors are now exposed",
                10, cmc.exposedBoardingParties());

        while (cmc.getHullBoxes().getAvailableBarracks() > 0)
            cmc.applyDacChoiceHit("fhull", "barracks", null);

        int left = cmc.getCrew().getAvailableBoardingParties();
        assertTrue("parties survived the loss of every box", left > 0);
        assertEquals("with no barracks left, nobody is sheltered",
                0, cmc.boardingPartiesInBarracks());
        assertEquals("and every survivor is exposed", left, cmc.exposedBoardingParties());
    }

    // ------------------------------------------------------------------ through the damage loop

    /**
     * The whole point: pump internal damage through a commando cruiser and it keeps its marines.
     *
     * <p>Barracks is never chosen at a DAC pause, so the shelter stays intact for the length of
     * the run and the first sentence of G28.33 cannot be what saves the parties — only the
     * exemption can.
     */
    @Test
    public void shelteredPartiesAreNeverTakenByInternalDamage() {
        Ship cmc = deepHulledCommandoCruiser();
        int before = cmc.getCrew().getAvailableBoardingParties();
        assertEquals("fixture: every party must start sheltered for this to mean anything",
                0, cmc.exposedBoardingParties());

        List<String> log = pumpDamageAvoidingBarracks(cmc, POINTS_DRIVEN);

        assertDroveEnoughDamage(cmc);
        assertEquals("fixture: the barracks must have survived the run",
                3, cmc.getHullBoxes().getAvailableBarracks());
        assertEquals("every party was sheltered, so D7.21 may take none: " + d7_21Lines(log),
                before, cmc.getCrew().getAvailableBoardingParties());
        assertTrue("no D7.21 casualty should have been logged, got: " + d7_21Lines(log),
                d7_21Lines(log).isEmpty());
    }

    /**
     * The control, without which the test above proves nothing.
     *
     * <p>Same instrument on a hull with no barracks: here D7.21 must bite. A first draft of the
     * sheltered test passed on a fixture that never reached the fifth casualty event, which is a
     * green test asserting nothing.
     */
    @Test
    public void aShipWithNoBarracksStillLosesPartiesNormally() {
        Ship ca = ShipLibrary.createShip(ShipLibrary.get("Federation", "CA"));
        ca.setName("USS Unsheltered");
        int before = ca.getCrew().getAvailableBoardingParties();

        List<String> log = pumpDamageAvoidingBarracks(ca, 400);

        assertDroveEnoughDamage(ca);
        assertTrue("D7.21 must still take parties from a hull with no barracks",
                ca.getCrew().getAvailableBoardingParties() < before);
        assertTrue("and must say so in the log", !d7_21Lines(log).isEmpty());
    }

    /**
     * (G28.32) The surplus beyond capacity is exposed, D7.21 takes it, and then it stops.
     *
     * <p>Driven hard enough that D7.21 generates MORE casualty events than there are exposed
     * parties — fifteen events against ten exposed — because anything less does not
     * discriminate. At a hundred points this test passed with the exemption removed: five
     * casualties out of forty parties is under the surplus either way, so the bound was true for
     * the wrong reason. The exemption only shows up once the casualties would have to start
     * eating into the barracks.
     */
    @Test
    public void surplusBeyondBarracksCapacityIsStillExposedAndNoFurther() {
        Ship cmc = deepHulledCommandoCruiser();
        cmc.getCrew().setAvailableBoardingParties(40); // thirty shelter, ten do not
        assertEquals(30, cmc.boardingPartiesInBarracks());
        assertEquals(10, cmc.exposedBoardingParties());

        pumpDamageAvoidingBarracks(cmc, POINTS_FOR_MORE_EVENTS_THAN_SURPLUS);

        assertDroveEnoughEventsToExceedTheSurplus(cmc);
        assertEquals("fixture: the barracks must have survived the run",
                3, cmc.getHullBoxes().getAvailableBarracks());
        assertEquals("the ten loose parties are all gone", 10, 40 - cmc.getCrew()
                .getAvailableBoardingParties());
        assertEquals("and the thirty in the barracks are untouched, though the casualty events"
                + " outnumbered the surplus", 30, cmc.getCrew().getAvailableBoardingParties());
        assertEquals("with the barracks still full, nobody is exposed any more",
                0, cmc.exposedBoardingParties());
    }

    /**
     * Pins a PRE-EXISTING divergence this rule makes visible, rather than quietly changing it.
     *
     * <blockquote>D7.21: "The BP to be lost is selected by the owning player and can be any of
     * his own BPs <b>(including guards)</b> except one captured by the enemy."</blockquote>
     *
     * So a guard IS eligible for an internal-damage casualty. But {@code GuardPosts.assign} takes
     * the party off the roster while it is posted (D7.834), and D7.21 draws from
     * {@code getAvailableBoardingParties()} — so a posted guard cannot currently be chosen, on
     * any hull, barracks or not. That predates this commit and is recorded in the backlog; it is
     * pinned here because the barracks exemption is what makes it reachable on a troop ship,
     * where guards would otherwise be the only parties left to take.
     */
    @Test
    public void aPostedGuardIsCurrentlyOutOfReachOfD7_21() {
        Ship cmc = commandoCruiser();
        cmc.getCrew().setAvailableBoardingParties(ALL_SHELTERED); // under the thirty berths

        int posted = postOneGuard(cmc);
        assertEquals("fixture: this test needs a guard posted", 1, posted);

        assertEquals("the guard left the roster (D7.834)",
                ALL_SHELTERED - 1, cmc.getCrew().getAvailableBoardingParties());
        assertEquals("the rest are all quartered",
                ALL_SHELTERED - 1, cmc.boardingPartiesInBarracks());
        assertEquals("so the pool D7.21 draws from holds nobody at all - not even the guard,"
                + " whom D7.21 explicitly makes eligible",
                0, cmc.exposedBoardingParties());
    }

    // ---------------------------------------------------------------------------------- helpers

    /**
     * Apply damage to completion, answering DAC choices without ever picking the barracks.
     *
     * <p>{@code applyInternalDamage} is INTERRUPTIBLE: a point needing an owner's pick stops the
     * loop and hands back {@code remainingBleed}. Taking the first option blindly would let a
     * hull hit land on a barracks box, and then parties lost with the box (G28.33's FIRST
     * sentence) would be indistinguishable from parties lost to D7.21 — the test would pass for
     * the wrong reason, or fail for one.
     */
    private static List<String> pumpDamageAvoidingBarracks(Ship target, int points) {
        List<String> log = new ArrayList<>();
        Ship.DamageResult result = target.applyInternalDamage(points, null);
        log.addAll(result.log);
        while (result.choiceRequired) {
            String chosen = pickAnythingButBarracks(result.options);
            target.applyDacChoiceHit(result.choiceType, chosen, null);
            if (result.remainingBleed <= 0)
                break;
            result = target.applyInternalDamage(result.remainingBleed, null);
            log.addAll(result.log);
        }
        return log;
    }

    private static String pickAnythingButBarracks(List<String> options) {
        if (options == null || options.isEmpty())
            return null;
        for (String o : options)
            if (!"barracks".equals(o))
                return o;
        return options.get(0); // barracks was the only candidate; the fixtures avoid this
    }

    private static List<String> d7_21Lines(List<String> log) {
        List<String> hits = new ArrayList<>();
        for (String line : log)
            if (line.contains(D7_21_LINE))
                hits.add(line);
        return hits;
    }

    /**
     * Fail loudly if the run never got far enough for D7.21 to have fired at all.
     *
     * <p>D7.21 ignores the first four casualty events, so fifty internal points is the floor for
     * any of this to mean anything. A fixture that dies at thirty makes every assertion above
     * vacuously true.
     */
    private static void assertDroveEnoughEventsToExceedTheSurplus(Ship target) {
        int scored = target.internalDamagePointsForTest();
        if (scored < ENOUGH_FOR_ELEVEN_EVENTS)
            fail("broken fixture, not a pass: " + scored + " internal points is fewer than the "
                    + ENOUGH_FOR_ELEVEN_EVENTS + " needed for D7.21 to want more casualties than"
                    + " the surplus can supply");
    }

    private static void assertDroveEnoughDamage(Ship target) {
        int scored = target.internalDamagePointsForTest();
        if (scored < ENOUGH_FOR_CASUALTIES)
            fail("broken fixture, not a pass: only " + scored + " internal points were scored, and"
                    + " D7.21 needs " + ENOUGH_FOR_CASUALTIES + " before its fifth event");
    }

    private static Ship commandoCruiser() {
        Ship cmc = ShipLibrary.createShip(ShipLibrary.get("Federation", "CMC"));
        cmc.setName("USS Commando");
        assertEquals("fixture: cmc.json declares barracks: 3",
                3, cmc.getHullBoxes().getAvailableBarracks());
        return cmc;
    }

    /**
     * A commando cruiser with a hull deep enough that its barracks survive the run.
     *
     * <p>Needed because of (G28.2): "If all normal hull boxes of one (or both) type(s) are
     * destroyed but undestroyed barracks boxes remain, hull hits <b>must</b> be scored on
     * barracks." Once the hull runs out, barracks is the ONLY candidate on the row, so no choice
     * is offered and {@code pickAnythingButBarracks} never gets asked. The real CMC has nine C
     * hull boxes, which fifty internal points strip, and the first draft of these tests ended
     * with all three barracks destroyed — correct behaviour, useless fixture, because parties
     * lost WITH a box (G28.33's first sentence) are not what is being measured here.
     *
     * <p>The library spec is copied through {@code toInitMap} rather than mutated, so deepening
     * this hull cannot leak into another test's CMC.
     */
    private static Ship deepHulledCommandoCruiser() {
        java.util.Map<String, Object> values =
                ShipLibrary.get("Federation", "CMC").toInitMap();
        // (G28.2): more C hull than the run can possibly spend, so barracks never become the
        // sole candidate on the row. A probe with 200 ran out at 307 internals and the
        // barracks auto-resolved; POINTS_DRIVEN cannot consume more than itself.
        values.put("chull", 500);
        Ship cmc = new Ship();
        cmc.init(values);
        cmc.setName("USS Commando");
        assertEquals("fixture: still three barracks boxes",
                3, cmc.getHullBoxes().getAvailableBarracks());

        // Stated here rather than taken from cmc.json, which has already changed its complement
        // once mid-build (eight parties, then thirty-two). Twenty is under the thirty berths, so
        // this fixture means "every party sheltered" no matter what the data later says - and
        // that is the premise the exemption is measured against.
        cmc.getCrew().setAvailableBoardingParties(ALL_SHELTERED);
        assertEquals(0, cmc.exposedBoardingParties());
        return cmc;
    }

    /** (D7.83) Post exactly one guard, which takes that party off the roster (D7.834). */
    private static int postOneGuard(Ship ship) {
        int before = ship.getGuardPosts().totalPosted();
        com.sfb.Game game = new com.sfb.Game();
        game.getShips().add(ship);
        for (com.sfb.properties.SystemTarget t : game.getTargetableSystems(ship)) {
            if (t.getType() != com.sfb.properties.SystemTarget.Type.WEAPON)
                continue;
            ship.getGuardPosts().assign(t, com.sfb.properties.BoardingPartyQuality.NORMAL);
            if (ship.getGuardPosts().totalPosted() - before >= 1)
                break;
        }
        return ship.getGuardPosts().totalPosted() - before;
    }
}
