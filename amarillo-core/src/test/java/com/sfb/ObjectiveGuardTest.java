package com.sfb;

import com.sfb.objects.Objective;
import com.sfb.objects.Ship;
import com.sfb.properties.BoardingPartyQuality;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Posting a boarding party to guard a carried object or individual (D7.83).
 *
 * <h2>A general rule, not SH47's</h2>
 * D7.83 reads "a player may designate some of his boarding parties to be guarding specific
 * INDIVIDUALS OR OBJECTS", and a box on the SSD is only the commonest kind of one. SH47.476
 * then leans on it outright — "If the Stasis Box is aboard one ship, the other ship may attempt
 * a hit-and-run raid to seize the box (it can be guarded)" — but nothing there is particular to
 * that scenario. A defecting admiral, a crate of contraband and a diplomatic pouch all want the
 * same post, and get it from the same code.
 *
 * <h2>Guarded through the ordinary action</h2>
 * There is no new action and no new phase rule: {@code assignGuard} parses an
 * {@code OBJECTIVE:<name>} target code the way it already parses {@code WEAPON:} and
 * {@code TRACTOR:}, so Energy-Allocation timing (D7.83), the roster accounting, the
 * one-per-object limit (D7.833), release on capture (D7.834) and the harsher raid chart
 * (D7.831) all come for free.
 */
public class ObjectiveGuardTest {

    // ---------------------------------------------------------------- posting

    @Test
    public void aBoardingPartyCanGuardACarriedObject() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        int before = fed.getGuardPosts().totalPosted();

        Game.ActionResult result = game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals(before + 1, fed.getGuardPosts().totalPosted());
        assertTrue(fed.getGuardPosts().postedObjectives().contains("Stasis Box"));
    }

    /** (D7.833) "No more than one boarding party can be assigned to guard one object." */
    @Test
    public void onlyOneGuardPerObject() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        assertTrue(game.assignGuard(fed, "OBJECTIVE:Stasis Box", false).isSuccess());

        Game.ActionResult second = game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);
        assertFalse(second.isSuccess());
        assertTrue("the refusal should cite the rule: " + second.getMessage(),
                second.getMessage().contains("D7.833"));
    }

    /**
     * A guard goes on what this ship is CARRYING. Not a box in somebody else's hold, and not
     * one still lying in an asteroid hex — a marine cannot stand over either.
     */
    @Test
    public void onlyWhatThisShipIsCarryingCanBeGuarded() {
        Game game = gameWithCarriedBox();
        Ship blade = shipNamed(game, "IKV Saber");

        assertFalse("the Klingon does not have it",
                game.assignGuard(blade, "OBJECTIVE:Stasis Box", false).isSuccess());

        Objective free = new Objective("Loose Canister", 14, 14);
        game.addObjective(free);
        assertFalse("and a box on the map is aboard nobody",
                game.assignGuard(shipNamed(game, "USS Enterprise"),
                        "OBJECTIVE:Loose Canister", false).isSuccess());
    }

    @Test
    public void anUnknownObjectiveIsRefused() {
        Game game = gameWithCarriedBox();
        assertFalse(game.assignGuard(shipNamed(game, "USS Enterprise"),
                "OBJECTIVE:Nonexistent Thing", false).isSuccess());
    }

    /** (D7.83) Guards are posted during Energy Allocation, like every other guard. */
    @Test
    public void guardsArePostedInEnergyAllocationOnly() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        submitAllocations(game);          // the round closes; impulses begin

        Game.ActionResult result = game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("Energy Allocation"));
    }

    // A commando post is not tested here: quality handling is entirely in the shared
    // assign/roster path, which the other target types already cover, and a Fed CA carries no
    // commando squads to post without reaching past the data to invent one.

    @Test
    public void aGuardCanBeWithdrawn() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        int before = fed.getGuardPosts().totalPosted();
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        assertTrue(game.removeGuard(fed, "OBJECTIVE:Stasis Box").isSuccess());
        assertEquals("the boarding party is back on the roster",
                before, fed.getGuardPosts().totalPosted());
        assertTrue(fed.getGuardPosts().postedObjectives().isEmpty());
    }

    // ---------------------------------------------------------------- the raid (D7.831)

    /**
     * (D7.831) A raid against a guarded object meets the guard, exactly as one against a
     * guarded compartment does — the rule names "a guarded object, person, or compartment" in
     * one breath.
     */
    @Test
    public void aRaidOnAGuardedObjectMeetsTheGuard() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        assertNotNull("the guard must be found by the raid path",
                fed.getGuardPosts().intercept(SystemTarget.forObjective("Stasis Box")));
        assertNull("and an unguarded object must not be",
                fed.getGuardPosts().intercept(SystemTarget.forObjective("Something Else")));
    }

    /** (D7.834) "When the ship is captured, all guards are transferred to boarding party status." */
    @Test
    public void captureReturnsAnObjectGuardToThePool() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        assertEquals("the object guard is among those released",
                1, fed.getGuardPosts().releaseAll());
        assertTrue(fed.getGuardPosts().postedObjectives().isEmpty());
    }

    // ---------------------------------------------------------------- raidable (D7.825)

    /**
     * (D7.825) "Non-SSD items and special abilities ... cannot be attacked by hit and run raids
     * UNLESS THE RULES SPECIFICALLY ALLOW IT."
     *
     * <p>So the default is refusal and a scenario grants the exception, which is also how
     * D7.824 puts it: "This will normally involve items specified by a given scenario." A box
     * nobody has made raidable does not appear in an enemy's target list at all — not greyed
     * out, not refused on submission: absent, because as far as D7.825 is concerned there is
     * nothing there to raid.
     */
    @Test
    public void anObjectIsNotARaidTargetUnlessTheScenarioSaysSo() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");

        assertFalse("the default is D7.825's refusal", named(game, "Stasis Box").isRaidable());
        assertTrue("so it is not offered to a raider",
                game.getTargetableSystems(fed).stream().noneMatch(
                        st -> st.getType() == SystemTarget.Type.OBJECTIVE));
    }

    /** Granted, and it joins the enemy's target list beside the hardware. */
    @Test
    public void araidableObjectAppearsInTheTargetList() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        named(game, "Stasis Box").setRaidCapture(true);

        assertTrue("the raider should see the prize among the systems",
                game.getTargetableSystems(fed).stream().anyMatch(
                        st -> st.getType() == SystemTarget.Type.OBJECTIVE
                                && "Stasis Box".equals(st.getObjectiveName())));
    }

    /** And only where it actually is: a raid lists the systems of the ship holding it. */
    @Test
    public void theObjectIsOfferedOnlyOnTheShipCarryingIt() {
        Game game = gameWithCarriedBox();
        named(game, "Stasis Box").setRaidCapture(true);

        assertTrue("the Klingon is not carrying it, so nothing to seize there",
                game.getTargetableSystems(shipNamed(game, "IKV Saber")).stream().noneMatch(
                        st -> st.getType() == SystemTarget.Type.OBJECTIVE));
    }

    /** A free box on the map is nobody's to raid — a raid boards a SHIP. */
    @Test
    public void aFreeObjectIsNotARaidTargetAtAll() {
        Game game = gameWithCarriedBox();
        Objective free = new Objective("Loose Canister", 14, 14);
        free.setRaidCapture(true);
        game.addObjective(free);

        for (Ship s : game.getShips())
            assertTrue("a box in open space is in no ship's target list",
                    game.getTargetableSystems(s).stream().noneMatch(
                            st -> "Loose Canister".equals(st.getObjectiveName())));
    }

    /** The code the pad sends resolves back to the same target, and only when raidable. */
    @Test
    public void theWireCodeNamesTheObject() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        named(game, "Stasis Box").setRaidCapture(true);

        SystemTarget parsed = game.parseRaidTargetCode(fed, "OBJECTIVE:Stasis Box");
        assertNotNull(parsed);
        assertEquals(SystemTarget.Type.OBJECTIVE, parsed.getType());
        assertEquals("Stasis Box", parsed.getObjectiveName());
    }

    // ---------------------------------------------------------------- the raid itself (D7.824)

    /**
     * (D7.824) The chart is reused and RE-READ, and the re-reading is where the whole thing
     * lives: "the result 'System destroyed and BP returned safely' is read 'target captured and
     * BP returned safely.' The result 'System and BP destroyed' is applied AS STATED."
     *
     * <h2>Why this test runs the raid instead of calling the mapping</h2>
     * The size-class work earlier today taught the lesson the hard way: four tests of the
     * PREDICATE all passed with the behaviour deleted. Listing the box as a target proves
     * nothing about what happens when somebody takes the shot, so this drives real raids until
     * both outcomes have come up, and checks the state after each.
     *
     * <p>The dice are not seeded, so the loop keeps raiding until it has seen a capture and a
     * destruction. Each outcome is a 1-in-6 row, so a few dozen attempts suffice; the assertion
     * at the end is that both were actually reached, which is what stops a silent no-op from
     * passing as success.
     */
    @Test
    public void aRaidEitherTakesTheBoxOrDestroysIt() {
        boolean sawCapture = false;
        boolean sawDestroyed = false;

        for (int attempt = 0; attempt < 400 && !(sawCapture && sawDestroyed); attempt++) {
            Game game = gameReadyToRaid();
            Ship raider = shipNamed(game, "IKV Saber");
            Ship holder = shipNamed(game, "USS Enterprise");

            Game.ActionResult result = game.performHitAndRun(raider, holder,
                    java.util.List.of(SystemTarget.forObjective("Stasis Box")));
            if (!result.isSuccess())
                throw new AssertionError("fixture: the raid must get away — "
                        + result.getMessage());

            Objective box = game.getObjectives().stream()
                    .filter(o -> "Stasis Box".equals(o.getName())).findFirst().orElse(null);

            if (box == null) {
                sawDestroyed = true;
                assertTrue("a destroyed box must say so: " + result.getMessage(),
                        result.getMessage().contains("DESTROYED"));
            } else if (box.getCarrier() == raider) {
                sawCapture = true;
                assertNull("a captured box is in a hold, not in a hex", box.getLocation());
                assertTrue("a capture must say so: " + result.getMessage(),
                        result.getMessage().contains("CAPTURED"));
            } else {
                assertSame("otherwise it has not moved", holder, box.getCarrier());
            }
        }

        assertTrue("no raid ever captured the box — the D7.824 re-reading is not wired up",
                sawCapture);
        assertTrue("no raid ever destroyed it — 'applied as stated' is not wired up",
                sawDestroyed);
    }

    /**
     * A captured box starts its study again from nothing. Whoever carried it off learned
     * nothing in the process, so SH47.474's two turns are two turns for the new owner too.
     */
    @Test
    public void aCapturedBoxBringsNoStudyProgressWithIt() {
        for (int attempt = 0; attempt < 400; attempt++) {
            Game game = gameReadyToRaid();
            Ship raider = shipNamed(game, "IKV Saber");
            Ship holder = shipNamed(game, "USS Enterprise");
            Objective box = named(game, "Stasis Box");
            box.setStudy(Objective.StudyMode.TURNS, 2);
            box.creditStudy("USS Enterprise", 2);

            game.performHitAndRun(raider, holder,
                    java.util.List.of(SystemTarget.forObjective("Stasis Box")));

            Objective after = game.getObjectives().stream()
                    .filter(o -> "Stasis Box".equals(o.getName())).findFirst().orElse(null);
            if (after != null && after.getCarrier() == raider) {
                assertEquals("the raider has learned nothing about it",
                        0, after.studyProgressFor("IKV Saber"));
                assertEquals("and the previous owner's work goes with the box",
                        0, after.studyProgressFor("USS Enterprise"));
                return;
            }
        }
        throw new AssertionError("no capture occurred in 400 attempts");
    }

    /** A ship holding nothing raidable offers no such target, so no raid can be aimed at one. */
    @Test
    public void aRaidCannotBeAimedAtAnUnraidableBox() {
        Game game = gameReadyToRaid();
        named(game, "Stasis Box").setRaidCapture(false);

        assertNull("D7.825 refuses it, so the code must not resolve",
                game.parseRaidTargetCode(shipNamed(game, "USS Enterprise"),
                        "OBJECTIVE:Stasis Box"));
    }

    // ---------------------------------------------------------------- intent (D7.85/D7.827)

    /**
     * (D7.85) The two attempts are independent, and the rule is why: "Hit and run raids can
     * DESTROY, BUT NOT CAPTURE, a cloaking device or a DERFACS or UIM fire control system." A
     * scenario can therefore offer one, the other, both, or neither — and a VIP a side wants
     * dead is the mirror image of a box a side wants taken.
     */
    @Test
    public void aScenarioCanPermitWreckingWithoutSeizing() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        Objective vip = named(game, "Stasis Box");
        vip.setRaidCapture(false);
        vip.setRaidDestroy(true);

        java.util.List<SystemTarget> offered = game.getTargetableSystems(fed).stream()
                .filter(st -> st.getType() == SystemTarget.Type.OBJECTIVE).toList();

        assertEquals("one row, for the one permitted attempt", 1, offered.size());
        assertEquals(SystemTarget.RaidIntent.DESTROY, offered.get(0).getRaidIntent());
    }

    /** Both permitted, and the raider picks — so the pad gets a row for each. */
    @Test
    public void bothAttemptsAreOfferedSeparatelyWhenBothArePermitted() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        Objective vip = named(game, "Stasis Box");
        vip.setRaidCapture(true);
        vip.setRaidDestroy(true);

        java.util.Set<SystemTarget.RaidIntent> intents = game.getTargetableSystems(fed).stream()
                .filter(st -> st.getType() == SystemTarget.Type.OBJECTIVE)
                .map(SystemTarget::getRaidIntent)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals("the choice is the raider's (D7.824/D7.827), so both must be selectable",
                java.util.Set.of(SystemTarget.RaidIntent.CAPTURE,
                        SystemTarget.RaidIntent.DESTROY), intents);
    }

    /**
     * And the gate is PER INTENT on the server. A request naming the forbidden one of the two
     * must fail even though the other is allowed — which a target list alone cannot enforce,
     * since nothing stops a request naming a row the pad never offered.
     */
    @Test
    public void theForbiddenAttemptIsRefusedEvenWhenTheOtherIsAllowed() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        Objective box = named(game, "Stasis Box");
        box.setRaidCapture(true);
        box.setRaidDestroy(false);

        assertNotNull("seizing is granted",
                game.parseRaidTargetCode(fed, "OBJECTIVE:CAPTURE:Stasis Box"));
        assertNull("wrecking is not, and must not resolve",
                game.parseRaidTargetCode(fed, "OBJECTIVE:DESTROY:Stasis Box"));
    }

    /** A bare code still means CAPTURE — what a guard post uses, and the older wire form. */
    @Test
    public void aBareCodeStillMeansCapture() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        named(game, "Stasis Box").setRaidCapture(true);

        SystemTarget parsed = game.parseRaidTargetCode(fed, "OBJECTIVE:Stasis Box");
        assertNotNull(parsed);
        assertEquals(SystemTarget.RaidIntent.CAPTURE, parsed.getRaidIntent());
    }

    /**
     * The intent is read from the FRONT, never the end, because an objective's name is free
     * text that may contain a colon of its own.
     */
    @Test
    public void anObjectiveNameMayContainAColon() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        Objective odd = new Objective("Crate 7: Do Not Open", 10, 10);
        odd.setRaidDestroy(true);
        odd.setCarrier(fed);
        odd.setLocation(null);
        game.addObjective(odd);

        SystemTarget parsed =
                game.parseRaidTargetCode(fed, "OBJECTIVE:DESTROY:Crate 7: Do Not Open");
        assertNotNull("the name must survive its own colon", parsed);
        assertEquals("Crate 7: Do Not Open", parsed.getObjectiveName());
        assertEquals(SystemTarget.RaidIntent.DESTROY, parsed.getRaidIntent());
    }

    /**
     * (D7.827) A kill raid finishes the job on the row that would have handed the thing over,
     * and the party walks away — so wrecking is the EASIER of the two attempts: two rows of
     * the chart destroy, only one captures.
     */
    @Test
    public void aKillRaidDestroysOnTheRowThatWouldHaveCaptured() {
        boolean sawDestroyedWithPartySafe = false;

        for (int attempt = 0; attempt < 400 && !sawDestroyedWithPartySafe; attempt++) {
            Game game = gameReadyToRaid();
            Objective vip = named(game, "Stasis Box");
            vip.setRaidCapture(false);
            vip.setRaidDestroy(true);
            Ship raider = shipNamed(game, "IKV Saber");
            Ship holder = shipNamed(game, "USS Enterprise");

            Game.ActionResult result = game.performHitAndRun(raider, holder,
                    java.util.List.of(SystemTarget.forObjective("Stasis Box",
                            SystemTarget.RaidIntent.DESTROY)));
            assertTrue(result.getMessage(), result.isSuccess());

            boolean gone = game.getObjectives().stream()
                    .noneMatch(o -> "Stasis Box".equals(o.getName()));
            if (gone && result.getMessage().contains("raiding party returned"))
                sawDestroyedWithPartySafe = true;

            assertFalse("a kill raid must never hand the thing over",
                    game.getObjectives().stream().anyMatch(
                            o -> "Stasis Box".equals(o.getName()) && o.getCarrier() == raider));
        }

        assertTrue("no kill raid ever succeeded — the DESTROY intent is not wired up",
                sawDestroyedWithPartySafe);
    }

    // ---------------------------------------------------------------- stranded guards

    /**
     * A marine must not be left standing over an empty compartment.
     *
     * <p>A box leaves a hold four ways — beamed out, jettisoned, stolen, or gone down with the
     * ship — and the post is reconciled by ONE sweep at the turn boundary rather than by a
     * release call on each of those paths, because a call that has to be written at every site
     * is one that gets forgotten at the fifth. The boundary is also D7.83's own answer: posts
     * "cannot be changed until the next turn", so one lapses when the next turn's assignments
     * are made rather than the instant its charge is carried off.
     */
    @Test
    public void aGuardWhoseChargeHasGoneIsReturnedAtTheTurnBoundary() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        int roster = fed.getGuardPosts().totalPosted();
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);
        assertEquals(roster + 1, fed.getGuardPosts().totalPosted());

        // Jettisoned into space: the hold is empty and the post means nothing.
        named(game, "Stasis Box").setCarrier(null);
        named(game, "Stasis Box").setLocation(fed.getLocation());

        runToTurnEnd(game);

        assertTrue("the post should be gone", fed.getGuardPosts().postedObjectives().isEmpty());
        assertEquals("and the boarding party back on the roster",
                roster, fed.getGuardPosts().totalPosted());
    }

    /** A guard over something still in the hold is left exactly where it is. */
    @Test
    public void aGuardStillWithItsChargeStaysPosted() {
        Game game = gameWithCarriedBox();
        Ship fed = shipNamed(game, "USS Enterprise");
        game.assignGuard(fed, "OBJECTIVE:Stasis Box", false);

        runToTurnEnd(game);

        assertTrue("nothing left the hold, so nothing is released",
                fed.getGuardPosts().postedObjectives().contains("Stasis Box"));
    }

    // ---------------------------------------------------------------- helpers

    private static Game gameWithCarriedBox() {
        Game game = new Game();

        Player fedPlayer = new Player();
        fedPlayer.setName("Captain Dan");
        fedPlayer.setTeamName("Federation");
        Player kliPlayer = new Player();
        kliPlayer.setName("Captain Dave");
        kliPlayer.setTeamName("Klingons");
        game.getPlayers().add(fedPlayer);
        game.getPlayers().add(kliPlayer);

        Ship fed = ship(FederationShips.getFedCa(), "USS Enterprise", 10, 10, fedPlayer);
        Ship blade = ship(KlingonShips.getD7(), "IKV Saber", 20, 20, kliPlayer);
        game.getShips().add(fed);
        game.getShips().add(blade);

        // Aboard from the start, so the test is about the guard and not about the beam.
        Objective box = new Objective("Stasis Box", 10, 10);
        box.getAllowedRetrieval().add(com.sfb.properties.RetrievalMethod.TRANSPORTER);
        box.setCargoSpaces(2.0);
        box.setCarrier(fed);
        box.setLocation(null);
        game.addObjective(box);

        game.startTurn();    // guards are posted during Energy Allocation (D7.83)
        return game;
    }

    /**
     * The Klingon adjacent to the Federation ship, in the Activity phase, with a boarding party
     * and the transporter energy to send it — everything D7.8 asks for before a raid.
     */
    private static Game gameReadyToRaid() {
        Game game = gameWithCarriedBox();
        named(game, "Stasis Box").setRaidCapture(true);

        Ship raider = shipNamed(game, "IKV Saber");
        Ship holder = shipNamed(game, "USS Enterprise");
        raider.setLocation(new Location(10, 11));      // range 1

        submitAllocations(game);
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        raider.setActiveFireControl(true);
        raider.addLockOn(holder);
        raider.getTransporters().bankEnergy(4.0);
        raider.getCrew().setAvailableBoardingParties(4);
        // A raid cannot beam through a live shield, and the TARGET'S shield is not the
        // raider's to drop — so the fixture has to open the hull the way a battle would.
        for (int shield = 1; shield <= 6; shield++)
            holder.getShields().lowerShield(shield);
        return game;
    }

    private static void submitAllocations(Game game) {
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            game.submitAllocation(s, e);
        }
    }

    private static void runToTurnEnd(Game game) {
        submitAllocations(game);
        while (game.getCurrentImpulse() != 32
                || game.getCurrentPhase() != Game.ImpulsePhase.END_OF_IMPULSE)
            game.advancePhase();
        game.endTurn();
    }

    private static Ship ship(Map<String, Object> spec, String name, int x, int y, Player owner) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        s.setSpeedPreviousTurn(0);
        s.setSpeedTwoTurnsAgo(0);
        s.setOwner(owner);
        owner.getPlayerUnits().add(s);
        return s;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("no ship called " + name);
    }

    private static Objective named(Game game, String name) {
        for (Objective o : game.getObjectives())
            if (name.equals(o.getName()))
                return o;
        throw new AssertionError("no objective called " + name);
    }

    @SuppressWarnings("unused")
    private static BoardingPartyQuality unused() { return BoardingPartyQuality.NORMAL; }
}
