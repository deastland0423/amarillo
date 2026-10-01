package com.sfb.server;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.objects.Squadron;
import com.sfb.objects.shuttles.Haas;
import com.sfb.objects.shuttles.Haas_E;
import com.sfb.properties.Location;
import com.sfb.samples.KzintiShips;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The request-translation layer for an EW fighter's pod declaration (J4.961, J4.967).
 * <p>
 * This tier is where rule limits silently drift — a pre-G7.6 tractor cap survived two
 * revisions in exactly this file — so the wire is pinned to the Game call rather than left to
 * be exercised only through a browser. What is checked here is the translation and the request
 * validation; the rule limits themselves belong to core and are tested there.
 */
class GameSessionPodEwTest {

    private static final String HOST = "token-host";

    private GameSession session;
    private Game game;
    private Haas_E ewf;
    private Haas wingman;

    @BeforeEach
    void setUp() {
        session = new GameSession("game-1", HOST, "Alice");
        game = session.getGame();

        Ship carrier = new Ship();
        carrier.init(KzintiShips.getKzinBC());
        carrier.setName("KHS Longsword");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        game.getShips().add(carrier);

        ewf = new Haas_E();
        ewf.setName("HAAS-E");
        ewf.setLocation(new Location(10, 12));
        ewf.setFacing(13);

        wingman = new Haas();
        wingman.setName("HAAS-1");
        wingman.setLocation(new Location(10, 13));
        wingman.setFacing(13);

        game.getActiveShuttles().add(ewf);
        game.getActiveShuttles().add(wingman);
        Squadron squadron = new Squadron("Gold", carrier);
        assertNull(squadron.add(ewf));
        assertNull(squadron.add(wingman));

        game.startTurn();
    }

    private ActionRequest declare(String fighter, Integer ecm, Integer eccm) {
        ActionRequest req = new ActionRequest();
        req.setType("DECLARE_POD_EW");
        req.setPlayerToken(HOST);
        req.setShipName(fighter);
        req.setPodEcm(ecm);
        req.setPodEccm(eccm);
        return req;
    }

    private ActionRequest switchPods(String fighter, Boolean on) {
        ActionRequest req = new ActionRequest();
        req.setType("SET_FIGHTER_PODS_ACTIVE");
        req.setPlayerToken(HOST);
        req.setShipName(fighter);
        req.setPodsActive(on);
        return req;
    }

    // -------------------------------------------------------------------------
    // DECLARE_POD_EW
    // -------------------------------------------------------------------------

    @Test
    void aDeclarationReachesTheFighter() {
        Game.ActionResult r = session.executeAction(declare("HAAS-E", 4, 0));

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(4, ewf.getPodEcm());
        assertEquals(0, ewf.getPodEccm());
        assertTrue(ewf.isPodEwDeclaredThisTurn());
        assertTrue(r.getMessage().contains("J4.961"), "should cite the rule: " + r.getMessage());
    }

    /** And the squadron feels it — the same points are lent (J4.965). */
    @Test
    void theSquadronGetsWhatWasDeclared() {
        // J4.921 wants the lock-on, and J4.922 wants a recorded source. The designation sweep
        // that would normally set it runs inside Game's impulse loop, which this wire-level test
        // never enters — so it is set directly. What is under test is the translation layer.
        wingman.addLockOn(ewf);
        wingman.setLentEwSource(ewf, 0);
        assertTrue(session.executeAction(declare("HAAS-E", 4, 0)).isSuccess());

        assertEquals(4, game.lentEwTo(wingman).ecm());
        assertEquals(0, game.lentEwTo(wingman).eccm());
    }

    /**
     * Both halves are required. Boxed on the request for exactly this reason: a primitive would
     * have made an omitted split arrive as a legal 0/0 declaration and quietly throw away four
     * points of EW.
     */
    @Test
    void anIncompleteRequestIsRefusedRatherThanReadAsZero() {
        Game.ActionResult r = session.executeAction(declare("HAAS-E", 4, null));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("required"), r.getMessage());
        assertFalse(ewf.isPodEwDeclaredThisTurn(), "nothing should have been declared");
    }

    /** The core rule still applies through the wire: the total must be spent exactly. */
    @Test
    void aSplitThatDoesNotAddUpIsRefused() {
        Game.ActionResult r = session.executeAction(declare("HAAS-E", 1, 1));

        assertFalse(r.isSuccess());
        assertFalse(ewf.isPodEwDeclaredThisTurn());
    }

    @Test
    void aFighterWithNoPodsIsRefused() {
        assertFalse(session.executeAction(declare("HAAS-1", 2, 2)).isSuccess());
    }

    @Test
    void anUnknownFighterIsRefused() {
        Game.ActionResult r = session.executeAction(declare("HAAS-9", 2, 2));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("not found"), r.getMessage());
    }

    // -------------------------------------------------------------------------
    // SET_FIGHTER_PODS_ACTIVE
    // -------------------------------------------------------------------------

    @Test
    void theOffSwitchReachesTheFighter() {
        // J4.921 wants the lock-on, and J4.922 wants a recorded source. The designation sweep
        // that would normally set it runs inside Game's impulse loop, which this wire-level test
        // never enters — so it is set directly. What is under test is the translation layer.
        wingman.addLockOn(ewf);
        wingman.setLentEwSource(ewf, 0);
        assertFalse(game.lentEwTo(wingman).isNothing());

        Game.ActionResult r = session.executeAction(switchPods("HAAS-E", false));

        assertTrue(r.isSuccess(), r.getMessage());
        assertFalse(ewf.arePodsActive());
        assertTrue(game.lentEwTo(wingman).isNothing(), "the loan goes with them (J4.965)");
        assertTrue(r.getMessage().contains("J4.967"), r.getMessage());
    }

    @Test
    void theSwitchGoesBackOnAgain() {
        assertTrue(session.executeAction(switchPods("HAAS-E", false)).isSuccess());
        assertTrue(session.executeAction(switchPods("HAAS-E", true)).isSuccess());

        assertTrue(ewf.arePodsActive());
    }

    /** Boxed for the same reason: an omitted flag must not read as "switch them off". */
    @Test
    void anOmittedFlagIsRefusedRatherThanReadAsOff() {
        Game.ActionResult r = session.executeAction(switchPods("HAAS-E", null));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("required"), r.getMessage());
        assertTrue(ewf.arePodsActive(), "the pods should be untouched");
    }

    @Test
    void aFighterWithNoPodsHasNoSwitch() {
        assertFalse(session.executeAction(switchPods("HAAS-1", false)).isSuccess());
    }

    /**
     * J4.961 declares the split at the head of the turn, when an EW fighter is usually still
     * in its bay — so the lookup has to find it there. Searching only the map refused the
     * declaration in exactly the situation the rule is written for.
     */
    @Test
    void anEwFighterStillInItsBayCanBeDeclaredFor() throws Exception {
        com.sfb.objects.Ship cvs = com.sfb.objects.ShipLibrary.createShip(
                com.sfb.objects.ShipSpec.fromJson(
                        new java.io.File("../data/factions/kzinti/cvs.json")));
        cvs.setName("KHS Watchful");
        cvs.setLocation(new Location(14, 10));
        cvs.setFacing(1);
        game.getShips().add(cvs);

        // Found by carrying pods, not by class: which EW fighter a CVS has aboard is decided by
        // the year (J4.4), so built from its own file at Y170 it is an AAS-E and only a Y173
        // scenario makes it a HAAS-E. This test is about declaring a pod split, not about models.
        com.sfb.objects.shuttles.Fighter inBay = null;
        for (com.sfb.systemgroups.ShuttleBay bay : cvs.getShuttles().getBays())
            for (com.sfb.objects.shuttles.Shuttle craft : bay.getInventory())
                if (inBay == null && craft instanceof com.sfb.objects.shuttles.Fighter f
                        && f.getEwPods() > 0)
                    inBay = f;
        assertNotNull(inBay, "the CVS should keep an EW fighter");

        Game.ActionResult r = session.executeAction(declare(inBay.getName(), 4, 0));

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(4, inBay.getPodEcm());
        assertTrue(inBay.isPodEwDeclaredThisTurn());
    }

    // -------------------------------------------------------------------------
    // DESIGNATE_EW_SOURCE (J4.922)
    // -------------------------------------------------------------------------

    /** J4.921 requires the recipient to hold a lock-on before any source will qualify. */
    private void readyToReceive() {
        wingman.addLockOn(ewf);
        wingman.setLentEwSource(null, 0);
    }

    private ActionRequest designate(String fighter, String source) {
        ActionRequest req = new ActionRequest();
        req.setType("DESIGNATE_EW_SOURCE");
        req.setPlayerToken(HOST);
        req.setShipName(fighter);
        req.setEwSourceName(source);
        return req;
    }

    @Test
    void aPlayerCanNameTheSource() {
        readyToReceive();

        Game.ActionResult r = session.executeAction(designate("HAAS-1", "HAAS-E"));

        assertTrue(r.isSuccess(), r.getMessage());
        assertSame(ewf, wingman.getLentEwSource());
        assertFalse(wingman.isLentEwSourceProvisional(),
                "a named source is the player's commitment, so J4.922's clock starts");
        assertTrue(r.getMessage().contains("J4.922"), r.getMessage());
    }

    /** And can stop receiving, which the empty name means. */
    @Test
    void anEmptyNameStopsReceiving() {
        readyToReceive();
        assertTrue(session.executeAction(designate("HAAS-1", "")).isSuccess());

        assertNull(wingman.getLentEwSource());
    }

    /** J4.921: a unit that cannot lend right now cannot be named. */
    @Test
    void anIneligibleSourceIsRefused() {
        readyToReceive();
        Game.ActionResult r = session.executeAction(designate("HAAS-1", "KHS Longsword"));

        assertFalse(r.isSuccess(), "the BC is no carrier of this fighter's");
        assertTrue(r.getMessage().contains("J4.921"), r.getMessage());
    }

    /**
     * J4.922's clock, once the player has committed: "It can change the source it is receiving
     * from every eight impulses."
     */
    @Test
    void aSecondChangeWaitsOutTheInterval() {
        readyToReceive();
        assertTrue(session.executeAction(designate("HAAS-1", "HAAS-E")).isSuccess());

        Game.ActionResult again = session.executeAction(designate("HAAS-1", ""));

        assertFalse(again.isSuccess());
        assertTrue(again.getMessage().contains("J4.922"), again.getMessage());
        assertSame(ewf, wingman.getLentEwSource(), "and it stays where it was");
    }

    @Test
    void anUnknownFighterIsRefusedForDesignationToo() {
        assertFalse(session.executeAction(designate("HAAS-9", "HAAS-E")).isSuccess());
    }
}
