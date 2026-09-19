package com.sfb;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Labs identifying a shuttle, not only a seeker.
 * <p>
 * An enemy suicide shuttle and an unreleased scatter pack reach their enemy as plain
 * shuttles — the DTO is careful about that, and it is the whole value of the things — so
 * the shuttle-looking contact drifting toward you is exactly what a lab is for. The
 * scout-channel path (G24.25) already allowed it; the lab path searched only the seeker
 * list, so a genuine shuttle could not be found at all, and a suicide shuttle, though
 * findable by name, was never offered as a target.
 * <p>
 * The rolls are pinned by geometry rather than a die seam: identification needs a die
 * strictly greater than the range, so range 0 always succeeds and range 6 never can. That
 * is a real property of the mechanic, not a fixture trick.
 */
public class LabIdentifyShuttleTest {

    private Game game;
    private Ship fed;
    private Ship klingon;
    private Player federation;
    private Player empire;

    @Before
    public void setUp() {
        game = new Game();

        federation = new Player();
        federation.setTeamName("Federation");
        empire = new Player();
        empire.setTeamName("Klingon");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setOwner(federation);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKS Fury");
        klingon.setLocation(new Location(10, 14));
        klingon.setFacing(13);
        klingon.setOwner(empire);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
        game.submitAllocation(fed, allocation(fed));
        game.submitAllocation(klingon, allocation(klingon));

        for (int guard = 0; guard < 400 && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        assertEquals("fixture needs the Activity phase", Game.ImpulsePhase.ACTIVITY,
                game.getCurrentPhase());
    }

    private Energy allocation(Ship s) {
        Energy e = new Energy();
        e.setLifeSupport(s.getLifeSupportCost());
        e.setFireControl(s.getFireControlCost());
        e.setActivateShields(s.getActiveShieldCost());
        e.setWarpMovement(0.0);
        return e;
    }

    /** An enemy shuttle on the map, in the given hex, owned by the Klingons. */
    private Shuttle enemyShuttleAt(int col, int row) {
        AdminShuttle s = new AdminShuttle();
        s.setName("IKS Fury-Admin-1");
        s.setOwner(empire);
        s.setParentShipName(klingon.getName());
        s.setLocation(new Location(col, row));
        game.getActiveShuttles().add(s);
        return s;
    }

    /** An enemy suicide shuttle: a shuttle, a seeker, and aimed at us. */
    private SuicideShuttle enemySuicideShuttleAt(int col, int row) {
        SuicideShuttle ss = new SuicideShuttle(new AdminShuttle());
        ss.setName("IKS Fury-Admin-2");
        ss.setOwner(empire);
        ss.setController(klingon);
        ss.setTarget(fed);
        ss.setLocation(new Location(col, row));
        game.getSeekers().add(ss);
        return ss;
    }

    private ActionResult identify(Shuttle s) {
        return game.identifySeekers(fed, Collections.singletonList(s.getName()));
    }

    // ---------------------------------------------------------------- shuttles

    @Test
    public void aLabIdentifiesAnEnemyShuttleInTheSameHex() {
        Shuttle s = enemyShuttleAt(10, 10);   // range 0: any die beats it

        ActionResult r = identify(s);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("a shuttle was findable by the lab path: " + r.getMessage(),
                r.getMessage().contains("IDENTIFIED"));
        assertTrue("and it is now known to be a shuttle", s.isIdentified());
    }

    /**
     * G4.233: what an identification reveals about a shuttle is whether it is following a
     * seeking course. For a genuine shuttle that is the negative answer, and the log says
     * so in those terms rather than claiming more than the rule gives.
     */
    @Test
    public void theLogSaysWhetherItIsOnASeekingCourse() {
        Shuttle s = enemyShuttleAt(10, 10);

        String msg = identify(s).getMessage();

        assertTrue("a plain shuttle is not seeking, and that is the answer bought: " + msg,
                msg.contains("not on a seeking course"));
    }

    /**
     * And for one that IS seeking, the target comes with it (G4.233, via G4.231). This is
     * the whole reason a player spends a lab on a shuttle-looking contact.
     */
    @Test
    public void identifyingASeekingShuttleRevealsItsTarget() {
        SuicideShuttle ss = enemySuicideShuttleAt(10, 10);

        String msg = game.identifySeekers(fed, Collections.singletonList(ss.getName()))
                .getMessage();

        assertTrue("it should report the seeking course: " + msg,
                msg.contains("on a seeking course"));
        assertTrue("and the target it is aimed at: " + msg,
                msg.contains(fed.getName()));
    }

    /**
     * The negative that G4.233 is explicit about: "but not if it is carrying drones or a
     * suicide bomb." A suicide shuttle and a loaded scatter pack must read alike, so the
     * log must not name either payload.
     */
    @Test
    public void theLogNeverNamesThePayload() {
        SuicideShuttle ss = enemySuicideShuttleAt(10, 10);

        String msg = game.identifySeekers(fed, Collections.singletonList(ss.getName()))
                .getMessage().toLowerCase();

        assertFalse("a lab cannot tell a bomb from a bellyful of drones (G4.233): " + msg,
                msg.contains("suicide") || msg.contains("warhead") || msg.contains("bomb"));
        assertFalse(msg.contains("scatter") || msg.contains("drones"));
    }

    @Test
    public void aShuttleBeyondRange5CannotBeIdentified() {
        // Six hexes away: the die can never exceed the range, so this is not a lucky roll.
        Shuttle s = enemyShuttleAt(10, 16);

        ActionResult r = identify(s);

        assertTrue(r.getMessage(), r.isSuccess());   // the attempt itself was legal
        assertFalse("range 6 is beyond what a lab can resolve: " + r.getMessage(),
                s.isIdentified());
        assertTrue(r.getMessage(), r.getMessage().contains("FAILED"));
    }

    @Test
    public void aFriendlyShuttleCannotBeIdentified() {
        Shuttle mine = enemyShuttleAt(10, 10);
        mine.setOwner(federation);          // now it is one of ours
        int labsBefore = fed.getLabs().getAvailableLab();

        ActionResult r = identify(mine);

        assertFalse("nothing to learn about our own shuttle", mine.isIdentified());
        assertTrue(r.getMessage(), r.getMessage().contains("friendly"));
        assertEquals("and no lab should have been spent on it",
                labsBefore, fed.getLabs().getAvailableLab());
    }

    /**
     * The case that motivates all of this. A suicide shuttle IS a seeker, so the lab path
     * could always find it by name — but its enemy sees a plain shuttle, and while the UI
     * could not offer shuttles as targets nobody could supply that name.
     */
    @Test
    public void aSuicideShuttleIsIdentifiableToo() {
        SuicideShuttle ss = enemySuicideShuttleAt(10, 10);

        ActionResult r = game.identifySeekers(fed, Collections.singletonList(ss.getName()));

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("a suicide shuttle identifies as the seeker it is: " + r.getMessage(),
                ss.isIdentified());
        assertTrue("and it reports the seeking course a shuttle identification buys",
                r.getMessage().contains("on a seeking course"));
    }

    // ---------------------------------------------------------------- seekers still work

    @Test
    public void aDroneIsStillIdentifiable() {
        // The seeker path is what this method was for; extending it must not break it.
        Drone d = new Drone(DroneType.TypeI);
        d.setName("IKS Fury-Drone-1");
        d.setController(klingon);
        d.setLocation(new Location(10, 10));
        game.getSeekers().add(d);

        ActionResult r = game.identifySeekers(fed, Collections.singletonList(d.getName()));

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), d.isIdentified());
    }

    // ---------------------------------------------------------------- G4.21 prohibitions

    /**
     * G4.21: the procedure "cannot be used by ... one using Erratic Maneuvers (C10.52)".
     * EM was not modelled when the lab path was written, so nothing here checked it.
     */
    @Test
    public void aShipUsingErraticManeuversCannotIdentify() {
        Shuttle s = enemyShuttleAt(10, 10);   // range 0: would otherwise always succeed
        fed.announceEm(true, game.getAbsoluteImpulse());
        fed.applyEmAnnouncement(game.getAbsoluteImpulse());   // stage 6E brings it into force
        assertTrue("fixture needs EM in force", fed.isUsingEm());
        int labsBefore = fed.getLabs().getAvailableLab();

        ActionResult r = identify(s);

        assertFalse("G4.21 bars it: " + r.getMessage(), r.isSuccess());
        assertFalse(s.isIdentified());
        assertEquals("and no lab is spent on a refused attempt",
                labsBefore, fed.getLabs().getAvailableLab());
    }

    /**
     * G4.21: "Each lab box on board a ship, if it (the lab) is undertaking no other action
     * on that turn, can make one attempt to identify a seeking weapon."
     * <p>
     * A scout channel assigned to IDENTIFY holds a lab for the whole turn (G24.251) but
     * does not decrement the count, because it may make four attempts with it. So the two
     * identification paths were spending the same box twice: this path read the raw
     * availableLab figure, which still included boxes a channel was using.
     */
    @Test
    public void aLabHeldByAScoutChannelCannotAlsoIdentifyHere() {
        fed.getLabs().init(java.util.Map.of("lab", 1));   // exactly one box to fight over

        com.sfb.weapons.ScoutChannel ch = new com.sfb.weapons.ScoutChannel();
        ch.setDesignator("1");
        ch.setDacHitLocaiton("torp");
        ch.setPowered(true);
        fed.getWeapons().addWeapon(ch);
        ch.setTurnFunction(com.sfb.weapons.ScoutChannel.Function.IDENTIFY);  // takes the lab

        Shuttle s = enemyShuttleAt(10, 10);   // range 0: would otherwise always succeed
        ActionResult r = identify(s);

        assertFalse("the ship's only lab is already working (G4.21): " + r.getMessage(),
                r.isSuccess());
        assertFalse(s.isIdentified());
    }

    /** With a second box free, the same channel assignment no longer blocks the attempt. */
    @Test
    public void aSecondLabIsStillFreeToIdentify() {
        fed.getLabs().init(java.util.Map.of("lab", 2));

        com.sfb.weapons.ScoutChannel ch = new com.sfb.weapons.ScoutChannel();
        ch.setDesignator("1");
        ch.setDacHitLocaiton("torp");
        ch.setPowered(true);
        fed.getWeapons().addWeapon(ch);
        ch.setTurnFunction(com.sfb.weapons.ScoutChannel.Function.IDENTIFY);

        Shuttle s = enemyShuttleAt(10, 10);
        ActionResult r = identify(s);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("one box for the channel, one for this attempt", s.isIdentified());
    }

    // ---------------------------------------------------------------- G4.22, several labs

    /**
     * G4.22: "announces how many of his labs will try to identify that unit ... rolls a
     * single die for each lab making the identification attempt, and if ANY of the results
     * is greater than the range ... the attempt is successful."
     * <p>
     * Any ONE of them. Scripted dice, because with real ones this test and a
     * last-die-wins implementation would agree most of the time.
     */
    @Test
    public void anyOneOfSeveralDiceCarriesTheAttempt() {
        Shuttle s = enemyShuttleAt(10, 15);          // range 5: only a 6 beats it
        assertEquals(5, com.sfb.utilities.MapUtils.getRange(fed, s));

        // Three labs on the one contact. The winning die is in the middle, so neither
        // "the first roll decides" nor "the last roll decides" would pass.
        ActionResult r = game.identifySeekers(fed,
                java.util.Arrays.asList(s.getName(), s.getName(), s.getName()),
                new int[] { 2, 6, 1 });

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("one die of the three beat the range (G4.22): " + r.getMessage(),
                s.isIdentified());
    }

    @Test
    public void whenNoDieBeatsTheRangeTheAttemptFails() {
        Shuttle s = enemyShuttleAt(10, 15);          // range 5

        ActionResult r = game.identifySeekers(fed,
                java.util.Arrays.asList(s.getName(), s.getName(), s.getName()),
                new int[] { 5, 3, 5 });

        assertTrue(r.getMessage(), r.isSuccess());   // the attempt was legal
        assertFalse("none of them beat range 5", s.isIdentified());
    }

    /** Every lab committed is spent, winner or not — they all made the attempt. */
    @Test
    public void everyLabCommittedIsSpent() {
        Shuttle s = enemyShuttleAt(10, 10);
        int before = fed.getLabs().getAvailableLab();
        assertTrue("fixture needs at least three labs", before >= 3);

        game.identifySeekers(fed,
                java.util.Arrays.asList(s.getName(), s.getName(), s.getName()),
                new int[] { 6, 6, 6 });   // succeeds on the first, and still spends three

        assertEquals("three labs were committed, so three are gone",
                before - 3, fed.getLabs().getAvailableLab());
    }

    /**
     * Three labs on one contact is ONE attempt with three dice, not three attempts. The
     * log has to read that way or a player cannot tell what they bought.
     */
    @Test
    public void severalLabsOnOneContactAreOneAttempt() {
        Shuttle s = enemyShuttleAt(10, 10);

        String msg = game.identifySeekers(fed,
                java.util.Arrays.asList(s.getName(), s.getName(), s.getName()),
                new int[] { 1, 2, 3 }).getMessage();

        int mentions = msg.split(java.util.regex.Pattern.quote(s.getName()), -1).length - 1;
        assertEquals("one line for the contact, however many labs were on it: " + msg,
                1, mentions);
        assertTrue("and it should say how many labs and show every die: " + msg,
                msg.contains("3 labs, dice 1, 2, 3"));
    }

    /** A single lab keeps the singular wording the rest of the log uses. */
    @Test
    public void oneLabStillReadsAsOneDie() {
        Shuttle s = enemyShuttleAt(10, 10);

        String msg = identify(s).getMessage();

        assertTrue("singular wording for a single lab: " + msg, msg.contains("(die "));
    }

    @Test
    public void committingMoreLabsThanTheShipHasIsRefused() {
        Shuttle s = enemyShuttleAt(10, 10);
        int labs = fed.getLabs().getAvailableLab();
        java.util.List<String> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < labs + 1; i++)
            tooMany.add(s.getName());

        ActionResult r = game.identifySeekers(fed, tooMany);

        assertFalse("cannot commit more labs than the ship has: " + r.getMessage(),
                r.isSuccess());
        assertEquals("and a refused attempt spends none",
                labs, fed.getLabs().getAvailableLab());
    }

    /** Labs can still be spread across different contacts, one attempt each. */
    @Test
    public void labsCanBeSplitBetweenContacts() {
        Shuttle a = enemyShuttleAt(10, 10);
        SuicideShuttle b = enemySuicideShuttleAt(10, 11);

        String msg = game.identifySeekers(fed,
                java.util.Arrays.asList(a.getName(), b.getName()),
                new int[] { 6, 6 }).getMessage();

        assertTrue("both were attempted: " + msg, a.isIdentified() && b.isIdentified());
        assertTrue(msg.contains(a.getName()) && msg.contains(b.getName()));
    }

    @Test
    public void anUnknownNameIsReportedNotFound() {
        ActionResult r = game.identifySeekers(fed, Collections.singletonList("Nothing At All"));

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("not found"));
    }
}
