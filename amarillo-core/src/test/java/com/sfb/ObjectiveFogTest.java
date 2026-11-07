package com.sfb;

import com.sfb.dto.GameStateDto;
import com.sfb.objects.Objective;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.properties.RetrievalMethod;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.scenario.ScenarioSpec;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * A face-down counter (SH47.46/.471): eighteen asteroid counters are laid out, two of them are
 * Stasis Boxes, and four counters were taken off the stack unseen — so neither player knows
 * whether two boxes are out there, one, or none.
 *
 * <h2>What makes this different from every other secret here</h2>
 * It is SYMMETRIC. A drone's type is hidden from the enemy and known to its owner, which is what
 * {@code GameStateDto.hiddenFrom(viewerTeam, owner)} says; a counter on the table belongs to
 * nobody. So there is no viewer to test against, and the per-player redaction machinery takes no
 * part — which makes this the SMALLER job of the two, not the larger.
 *
 * <h2>And it hides by OMISSION, which nothing did before</h2>
 * {@code MapObjectPrivacyTest} named omission as a mechanism and recorded that nothing used it.
 * This is the first thing that does, and it has to be: a masked {@code ObjectiveDto} would still
 * announce that there is something in this hex, and WHERE the boxes are is the whole of what
 * SH47 keeps back. Masking the fields would have left the scenario given away with every field
 * correctly redacted.
 */
public class ObjectiveFogTest {

    // ------------------------------------------------------------------ the default

    /**
     * Revealed by default, and this is the load-bearing one. SH35's probe canisters, SH50's
     * landing parties and every objective in every scenario written before fog existed sit on
     * the table in plain sight. A default of hidden would have made all of them disappear —
     * which is the kind of change that looks like the feature working.
     */
    @Test
    public void anObjectiveIsRevealedUnlessAScenarioSaysOtherwise() {
        assertTrue(new Objective("Probe Canister", 10, 10).isRevealed());
    }

    @Test
    public void aScenarioCanLayACounterFaceDown() {
        Game game = new Game();
        game.setupFromScenario(specWith(objective("Stasis Box", "1012", true),
                objective("Spare Parts", "1014", false)));

        assertFalse("hidden: true means face down", named(game, "Stasis Box").isRevealed());
        assertTrue("and it does not leak onto its neighbour",
                named(game, "Spare Parts").isRevealed());
    }

    /** Hiding is setup-only; the way back is one-way (SH47.473). */
    @Test
    public void revealingIsOneWayAndGlobal() {
        Objective box = new Objective("Stasis Box", 10, 10);
        box.hide();
        assertFalse(box.isRevealed());

        box.reveal();
        assertTrue(box.isRevealed());
        box.reveal();
        assertTrue("revealing twice is not an un-reveal", box.isRevealed());
    }

    // ------------------------------------------------------------------ omission

    /**
     * Hidden from BOTH players, which is the point. Asserting one side would have passed on an
     * owner-keyed implementation that happened to pick a team.
     */
    @Test
    public void anUnrevealedCounterIsNotSentToEitherPlayer() {
        Game game = twoShipGame();
        hiddenBoxAt(game, 14, 14);

        assertNull("the Federation must not see it", objectiveIn(game, "Federation"));
        assertNull("and neither must the Klingons", objectiveIn(game, "Klingons"));
    }

    /** It is left OUT, not sent blank: a blank entry still says a hex is worth visiting. */
    @Test
    public void omissionMeansOmission() {
        Game game = twoShipGame();
        hiddenBoxAt(game, 14, 14);

        for (GameStateDto.MapObjectDto o : new GameStateDto(game, "Federation").mapObjects)
            assertFalse("no ObjectiveDto of any kind may reach the client",
                    o instanceof GameStateDto.ObjectiveDto);
    }

    /** A revealed one is ordinary and public, exactly as before this existed. */
    @Test
    public void aRevealedCounterIsSentToEverybody() {
        Game game = twoShipGame();
        Objective box = hiddenBoxAt(game, 14, 14);
        box.reveal();

        assertNotNull(objectiveIn(game, "Federation"));
        assertNotNull(objectiveIn(game, "Klingons"));
        assertEquals("<14|14>", objectiveIn(game, "Federation").location);
    }

    /**
     * The omniscient snapshot still sees it. That is the dev and solo view, documented as such
     * on the constructor, and it sees every other secret in the game too — a face-down counter
     * is not the place to start making an exception.
     */
    @Test
    public void theOmniscientViewSeesEverything() {
        Game game = twoShipGame();
        hiddenBoxAt(game, 14, 14);

        assertNotNull("viewerTeam null means see everything",
                objectiveIn(game, null));
    }

    // ------------------------------------------------------------------ the reveal

    /**
     * SH47.471: examine it by entering the hex. Swept at the end of MOVEMENT, so it covers a
     * ship that starts the scenario standing on a counter too.
     */
    @Test
    public void aShipInTheHexTurnsTheCounterOver() {
        Game game = runningGame();
        Objective box = hiddenBoxAt(game, 10, 10);   // the Federation CA's own hex
        assertFalse("fixture: it starts face down", box.isRevealed());

        advancePastMovement(game);

        assertTrue("the ship is in the hex; the counter is face up", box.isRevealed());
        assertNotNull("and now it reaches the client", objectiveIn(game, "Federation"));
    }

    /**
     * SH47.473 — the counter stays face up, so the player who did NOT examine it learns what is
     * there as well. That is the rule rather than a simplification: the counter is physically
     * turned over on the table in front of both players.
     */
    @Test
    public void theOtherPlayerSeesItToo() {
        Game game = runningGame();
        hiddenBoxAt(game, 10, 10);   // only the Federation ship is here

        advancePastMovement(game);

        assertNotNull("the Klingon player sees it too, having done nothing",
                objectiveIn(game, "Klingons"));
    }

    /** A counter nobody is standing on stays face down. */
    @Test
    public void anEmptyHexRevealsNothing() {
        Game game = runningGame();
        Objective box = hiddenBoxAt(game, 30, 5);

        advancePastMovement(game);

        assertFalse(box.isRevealed());
    }

    /** And the impulse log says so, because a reveal is a thing that happened. */
    @Test
    public void theRevealIsLogged() {
        Game game = runningGame();
        hiddenBoxAt(game, 10, 10);

        advancePastMovement(game);

        assertTrue("the log should name the finder and the find: " + game.getLastSeekerLog(),
                game.getLastSeekerLog().stream().anyMatch(
                        l -> l.contains("USS Enterprise") && l.contains("Stasis Box")));
    }

    // ------------------------------------------------------------------ not addressable

    /**
     * A hidden counter answers an action exactly as a non-existent one does, down to the
     * wording.
     *
     * <h2>Why the message has to be identical</h2>
     * A distinct refusal — "you have not found that yet" — is a confirmation. A player who
     * suspects a box is in 1512 names it, gets a different error from the one a made-up name
     * gets, and has their answer without moving a ship. The whole secret is WHERE, so any reply
     * that distinguishes "not there" from "not yet" gives it away.
     */
    @Test
    public void ahiddenCounterCannotBeBeamedAndSaysNothingAboutWhy() {
        Game game = runningGame();
        hiddenBoxAt(game, 10, 10);
        Ship fed = shipNamed(game, "USS Enterprise");

        Game.ActionResult onHidden =
                game.pickUpObjective(fed, "Stasis Box", RetrievalMethod.TRANSPORTER);
        Game.ActionResult onNonsense =
                game.pickUpObjective(fed, "Nonexistent Thing", RetrievalMethod.TRANSPORTER);

        assertFalse(onHidden.isSuccess());
        assertFalse(onNonsense.isSuccess());
        assertEquals("the two refusals must be indistinguishable but for the name",
                onNonsense.getMessage().replace("Nonexistent Thing", "Stasis Box"),
                onHidden.getMessage());
    }

    /** The same for a tractor beam, for the same reason (SH35.452 catches a free canister). */
    @Test
    public void aHiddenCounterCannotBeTractoredEither() {
        Game game = runningGame();
        hiddenBoxAt(game, 10, 11);
        Ship fed = shipNamed(game, "USS Enterprise");
        fed.setActiveFireControl(true);
        fed.getTractors().initForTurn(4, game.getAbsoluteImpulse());

        Game.ActionResult onHidden = game.establishTractor(fed, "Stasis Box", 1);
        Game.ActionResult onNonsense = game.establishTractor(fed, "Nonexistent Thing", 1);

        assertFalse(onHidden.isSuccess());
        assertEquals("a beam must not confirm the hex either",
                onNonsense.getMessage().replace("Nonexistent Thing", "Stasis Box"),
                onHidden.getMessage());
    }

    /** Once found, it behaves like any other objective. */
    @Test
    public void onceRevealedItCanBeBeamedAboard() {
        Game game = runningGame();
        Objective box = hiddenBoxAt(game, 10, 10);
        Ship fed = shipNamed(game, "USS Enterprise");

        advancePastMovement(game);
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        fed.setActiveFireControl(true);
        fed.getTransporters().bankEnergy(1.0);
        Game.ActionResult result =
                game.pickUpObjective(fed, "Stasis Box", RetrievalMethod.TRANSPORTER);

        assertTrue("a found box is an ordinary objective: " + result.getMessage(),
                result.isSuccess());
        assertSame(fed, box.getCarrier());
    }

    // ------------------------------------------------------------------ across a save

    /** Found stays found — otherwise a reload hands back every box the players worked for. */
    @Test
    public void aRevealedCounterIsStillRevealedAfterAReload() {
        ScenarioSpec spec = specWith(objective("Stasis Box", "1012", true));
        Game before = new Game();
        before.setupFromScenario(spec);
        named(before, "Stasis Box").reveal();

        Game after = reload(before, spec);
        assertTrue("the examination must survive the save",
                named(after, "Stasis Box").isRevealed());
    }

    /** And unfound stays unfound, or a reload gives the scenario away. */
    @Test
    public void anUnrevealedCounterIsStillHiddenAfterAReload() {
        ScenarioSpec spec = specWith(objective("Stasis Box", "1012", true));
        Game before = new Game();
        before.setupFromScenario(spec);

        Game after = reload(before, spec);
        assertFalse("a reload must not turn the counter over",
                named(after, "Stasis Box").isRevealed());
    }

    /**
     * A save written before fog existed carries no {@code revealed} key, and the default has to
     * come from the SCENARIO rather than from a constant. A hardcoded {@code true} would
     * uncover every box in an older SH47 save; a hardcoded {@code false} would hide every probe
     * canister in an older SH35. Neither is right for both, and the scenario already knows.
     */
    @Test
    public void aPreFogSaveTakesItsAnswerFromTheScenario() {
        ScenarioSpec spec = specWith(objective("Stasis Box", "1012", true),
                objective("Spare Parts", "1014", false));
        Game before = new Game();
        before.setupFromScenario(spec);

        Map<String, Object> saved = before.exportState();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) saved.get("objectives");
        for (Map<String, Object> entry : entries)
            entry.remove("revealed");   // as an older build would have written it

        Game after = new Game();
        after.setupFromScenario(spec);
        after.applyState(saved);

        assertFalse("the hidden one stays hidden", named(after, "Stasis Box").isRevealed());
        assertTrue("and the visible one stays visible", named(after, "Spare Parts").isRevealed());
    }

    // ------------------------------------------------------------------ helpers

    private static Objective hiddenBoxAt(Game game, int x, int y) {
        Objective box = new Objective("Stasis Box", x, y);
        box.getAllowedRetrieval().add(RetrievalMethod.TRANSPORTER);
        box.getAllowedRetrieval().add(RetrievalMethod.TRACTOR);
        box.setCargoSpaces(2.0);
        box.hide();
        game.addObjective(box);
        return box;
    }

    private static GameStateDto.ObjectiveDto objectiveIn(Game game, String viewerTeam) {
        for (GameStateDto.MapObjectDto o : new GameStateDto(game, viewerTeam).mapObjects)
            if (o instanceof GameStateDto.ObjectiveDto dto)
                return dto;
        return null;
    }

    /** Two owned ships, no turn started — enough for a DTO. */
    private static Game twoShipGame() {
        Game game = new Game();

        Player fedPlayer = new Player();
        fedPlayer.setName("Captain Dan");
        fedPlayer.setTeamName("Federation");
        Player klingonPlayer = new Player();
        klingonPlayer.setName("Captain Dave");
        klingonPlayer.setTeamName("Klingons");
        game.getPlayers().add(fedPlayer);
        game.getPlayers().add(klingonPlayer);

        game.getShips().add(ship(FederationShips.getFedCa(), "USS Enterprise", 10, 10, fedPlayer));
        game.getShips().add(ship(KlingonShips.getD7(), "IKV Saber", 20, 20, klingonPlayer));
        return game;
    }

    /** The same, driven to the MOVEMENT phase of impulse 1. */
    private static Game runningGame() {
        Game game = twoShipGame();
        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            game.submitAllocation(s, e);
        }
        return game;
    }

    /** The sweep runs on the way OUT of MOVEMENT, once all movement for the impulse is in. */
    private static void advancePastMovement(Game game) {
        while (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT)
            game.advancePhase();
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

    private static Game reload(Game before, ScenarioSpec spec) {
        Map<String, Object> saved = before.exportState();
        Game after = new Game();
        after.setupFromScenario(spec);
        after.applyState(saved);
        return after;
    }

    private static ScenarioSpec.ObjectiveSetup objective(String name, String hex, boolean hidden) {
        ScenarioSpec.ObjectiveSetup setup = new ScenarioSpec.ObjectiveSetup();
        setup.name = name;
        setup.hex = hex;
        setup.hidden = hidden;
        setup.retrieval = new ArrayList<>(List.of("TRANSPORTER"));
        return setup;
    }

    private static ScenarioSpec specWith(ScenarioSpec.ObjectiveSetup... objectives) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.name = "Fog fixture";
        spec.year = 152;
        spec.mapCols = 42;
        spec.mapRows = 30;
        spec.objectives = new ArrayList<>(List.of(objectives));
        spec.sides = new ArrayList<>();
        return spec;
    }

    private static Objective named(Game game, String name) {
        for (Objective o : game.getObjectives())
            if (name.equals(o.getName()))
                return o;
        throw new AssertionError("no objective called " + name + " in "
                + game.getObjectives().stream().map(Objective::getName).toList());
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("no ship called " + name);
    }
}
