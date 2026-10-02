package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.AegisLevel;
import com.sfb.properties.Location;

/**
 * D13.3: a full aegis system identifying incoming seeking weapons.
 * <p>
 * "Ships with a full aegis capability have a limited ability to determine the type of incoming
 * seeking weapon independent of the lab procedure (G4.22). They may make six 'attempts' per
 * turn, each directed at a specific individual seeking weapon." (D13.31)
 * <p>
 * It sets the SAME identified flag that labs (G4.2) and scout special sensors (G24.25) set,
 * because D13.34 says the aegis system "produces the same information" — a third way to earn
 * one result rather than a second notion of being identified. D13.33 keeps them independent
 * the other way: no lab is spent, and labs are not used by aegis.
 * <p>
 * The dice are supplied through a package-private seam, as the lab tests do. Without it
 * nothing distinguishes the chart from luck: at range 4 a success and a failure are both
 * ordinary results, and a test that rolled freely would assert whichever it happened to get.
 */
public class AegisIdentifyTest {

    private Game game;
    private Ship escort;
    private Ship enemy;
    private Drone drone;

    @Before
    public void setUp() throws Exception {
        com.sfb.objects.ShuttleCatalog.loadDefault("../data");
        game = new Game();

        Player kzin = new Player();
        kzin.setTeamName("Kzinti");
        Player klingon = new Player();
        klingon.setTeamName("Klingon");

        escort = new Ship();
        escort.init(com.sfb.samples.KzintiShips.getKzinBC());
        escort.setName("KHS Guardian");
        escort.setLocation(new Location(10, 10));
        escort.setFacing(1);
        escort.setOwner(kzin);
        escort.setActiveFireControl(true);
        escort.setAegisFitted(AegisLevel.FULL);
        game.getShips().add(escort);

        enemy = new Ship();
        enemy.init(com.sfb.samples.KlingonShips.getD7());
        enemy.setName("IKV Saber");
        enemy.setLocation(new Location(10, 2));
        enemy.setOwner(klingon);
        game.getShips().add(enemy);

        // The clock does not run until allocation is in. Without this the impulse sits at
        // zero, "the next impulse" never arrives, and the per-impulse cap can never reset —
        // which is exactly how the first version of this fixture failed.
        game.startTurn();
        for (Ship s : game.getShips()) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            game.submitAllocation(s, e);
        }

        toActivity();
        drone = droneAt(2, "Incoming-1");
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
     * An enemy drone {@code hexes} ahead — the controller decides whose it is.
     * <p>
     * Replaces any seeker already going by that name, so a test that crosses an impulse can
     * simply put it back: crossing runs the movement phase, and a drone with no target of its
     * own does not survive it. D13.321's repeat bonus keys on the NAME, so a replacement with
     * the same name is the same contact as far as the rule is concerned.
     */
    private Drone droneAt(int hexes, String name) {
        game.getSeekers().removeIf(sk -> ((com.sfb.objects.Marker) sk).getName().equals(name));
        Drone d = new Drone(DroneType.TypeI);
        d.setName(name);
        d.setLocation(new Location(10, 10 - hexes));
        d.setController(enemy);
        game.getSeekers().add(d);
        return d;
    }

    private Game.ActionResult attempt(String target, int die) {
        return game.identifyWithAegis(escort, target, die);
    }

    // ---------------------------------------------------------------- the chart

    /** D13.31's table, read straight off the rule: 0-3 automatic, 4 on 1-4, 5 on 1-3, 6 on 1. */
    @Test
    public void theChartMatchesTheRule() {
        for (int range = 0; range <= 3; range++)
            assertEquals("range " + range + " is automatic", 6, Ship.aegisIdentifyNeeds(range));
        assertEquals(4, Ship.aegisIdentifyNeeds(4));
        assertEquals(3, Ship.aegisIdentifyNeeds(5));
        assertEquals(1, Ship.aegisIdentifyNeeds(6));
        assertEquals("7+ not allowed", -1, Ship.aegisIdentifyNeeds(7));
    }

    /** Inside three hexes nothing can fail, so even a six identifies. */
    @Test
    public void insideThreeHexesItIsAutomatic() {
        assertFalse("fixture: not identified yet", drone.isIdentified());

        Game.ActionResult r = attempt("Incoming-1", 6);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("IDENTIFIED"));
        assertTrue("the same flag labs and scout sensors set", drone.isIdentified());
    }

    /** At range 5 a three identifies and a four does not. */
    @Test
    public void theChartBitesAtTheEdges() {
        Drone far = droneAt(5, "Incoming-5");

        assertFalse("a four misses at range five", attempt("Incoming-5", 4)
                .getMessage().contains("IDENTIFIED"));
        assertFalse(far.isIdentified());

        assertTrue("a three catches it", attempt("Incoming-5", 3)
                .getMessage().contains("IDENTIFIED"));
        assertTrue(far.isIdentified());
    }

    /** "7+ not allowed" is a refusal, not a roll — it must not spend an attempt. */
    @Test
    public void beyondSixHexesTheAttemptIsRefused() {
        droneAt(7, "Far-1");
        int before = escort.aegisIdAttemptsLeftThisTurn(game.getClock().getTurn());

        Game.ActionResult r = attempt("Far-1", 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("D13.31"));
        assertEquals("a refusal is not an attempt", before,
                escort.aegisIdAttemptsLeftThisTurn(game.getClock().getTurn()));
    }

    // ---------------------------------------------------------------- the budgets

    /** D13.32: four per impulse, whatever the turn's allowance still holds. */
    @Test
    public void fourAttemptsPerImpulse() {
        for (int i = 1; i <= 4; i++)
            assertTrue("attempt " + i, attempt("Incoming-1", 6).isSuccess());

        Game.ActionResult fifth = attempt("Incoming-1", 6);

        assertFalse(fifth.isSuccess());
        assertTrue(fifth.getMessage(), fifth.getMessage().contains("D13.32"));
    }

    /** D13.31: and six per turn, which outlasts a single impulse's four. */
    @Test
    public void sixAttemptsPerTurn() {
        int turn = game.getClock().getTurn();
        for (int i = 0; i < 4; i++)
            attempt("Incoming-1", 6);
        assertEquals(2, escort.aegisIdAttemptsLeftThisTurn(turn));

        nextImpulse();
        assertEquals("the impulse cap resets", 4,
                escort.aegisIdAttemptsLeftThisImpulse(game.getAbsoluteImpulse()));
        assertEquals("the turn cap does not", 2,
                escort.aegisIdAttemptsLeftThisTurn(turn));

        attempt("Incoming-1", 6);
        attempt("Incoming-1", 6);
        Game.ActionResult seventh = attempt("Incoming-1", 6);

        assertFalse(seventh.isSuccess());
        assertTrue(seventh.getMessage(), seventh.getMessage().contains("D13.31"));
    }

    private void nextImpulse() {
        int was = game.getAbsoluteImpulse();
        for (int i = 0; i < 40 && game.getAbsoluteImpulse() == was; i++)
            game.advancePhase();
        assertNotEquals("fixture: the impulse must actually advance",
                was, game.getAbsoluteImpulse());
        toActivity();
        escort.setActiveFireControl(true);
        drone = droneAt(2, "Incoming-1");
    }

    // ---------------------------------------------------------------- D13.321 / D13.322

    /**
     * D13.321: "If made at the same seeking weapon as the immediately previous attempt (by the
     * same ship), reduce the die roll by one." At range 6 only a one identifies, so the −1
     * turns a two into a success and is visible with no other change.
     */
    @Test
    public void repeatingATargetIsOneEasier() {
        Drone edge = droneAt(6, "Edge-1");

        assertFalse("a two fails at range six on the first look",
                attempt("Edge-1", 2).getMessage().contains("IDENTIFIED"));
        assertFalse(edge.isIdentified());

        nextImpulse();
        edge = droneAt(6, "Edge-1");                // put it back, still six hexes out

        Game.ActionResult second = attempt("Edge-1", 2);

        assertTrue(second.getMessage(), second.getMessage().contains("D13.321"));
        assertTrue("the same two now succeeds", second.getMessage().contains("IDENTIFIED"));
        assertTrue(edge.isIdentified());
    }

    /**
     * D13.322: "Attempts during the same impulse are all rolled simultaneously and do not
     * count as 'previous' to each other." So a second look in the SAME impulse gets no bonus.
     */
    @Test
    public void attemptsInOneImpulseDoNotHelpEachOther() {
        droneAt(6, "Edge-2");

        attempt("Edge-2", 2);
        Game.ActionResult same = attempt("Edge-2", 2);

        assertFalse("no repeat bonus within one impulse",
                same.getMessage().contains("D13.321"));
        assertFalse(same.getMessage().contains("IDENTIFIED"));
    }

    /** A different seeker breaks the chain, since it is the IMMEDIATELY previous that counts. */
    @Test
    public void adifferentTargetBreaksTheChain() {
        droneAt(6, "Edge-3");
        droneAt(2, "Near-1");

        attempt("Edge-3", 2);
        nextImpulse();
        droneAt(2, "Near-1");
        attempt("Near-1", 6);          // a different seeker, and the last of that impulse
        nextImpulse();
        droneAt(6, "Edge-3");

        Game.ActionResult r = attempt("Edge-3", 2);

        assertFalse("Near-1 was the immediately previous attempt, not Edge-3",
                r.getMessage().contains("D13.321"));
    }

    // ---------------------------------------------------------------- the gates

    /** D13.35 / D13.412: a limited system cannot do this at all. */
    @Test
    public void limitedAegisCannotIdentify() {
        escort.setAegisFitted(AegisLevel.LIMITED);

        Game.ActionResult r = attempt("Incoming-1", 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("D13.35"));
    }


    /** D13.524: and none of it works without active fire control. */
    @Test
    public void withoutActiveFireControlThereIsNoIdentification() {
        escort.setActiveFireControl(false);

        Game.ActionResult r = attempt("Incoming-1", 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("D13.524"));
    }

    /** You cannot identify your own seekers. */
    @Test
    public void aFriendlySeekerIsNotATarget() {
        Drone mine = droneAt(2, "Mine-1");
        mine.setController(escort);

        Game.ActionResult r = attempt("Mine-1", 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("friendly"));
    }

    /** D13.33: aegis spends no lab, and the lab budget is untouched by it. */
    @Test
    public void identifyingWithAegisSpendsNoLab() {
        int labsBefore = escort.getLabs().availableLabs(game.getAbsoluteImpulse());

        assertTrue(attempt("Incoming-1", 6).isSuccess());

        assertEquals("labs are not used by the aegis system (D13.33)",
                labsBefore, escort.getLabs().availableLabs(game.getAbsoluteImpulse()));
    }
}
