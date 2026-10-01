package com.sfb;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.Squadron;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.systemgroups.ShuttleBay;

/**
 * Squadron EW lending as a real game actually reaches it (J4.93).
 * <p>
 * Everything else about lending is tested against hand-built squadrons, which proves the rule
 * and not the wiring. This starts from a ship file — the Kzinti CVS, eleven fighters and one EW
 * fighter — lets the carrier organise its own squadrons at init (J4.461), launches two fighters
 * through the ordinary launch path, and checks the points arrive.
 * <p>
 * Which MODEL those are is decided by the year (J4.4), so nothing here names one: built from its
 * own file the CVS flies the AAS it entered service with in Y170, and a Y173 scenario re-equips
 * it with the HAAS. The craft is found by carrying pods, not by its class.
 * <p>
 * That chain is the thing worth guarding, because until the squadron default was added it was
 * broken at the far end in the quietest possible way: the lending machinery was consulted on
 * every shot and permanently answered nothing, since nothing in a real game ever designated a
 * source. Every unit test passed throughout.
 */
public class SquadronEwInPlayTest {

    private Game game;
    private Ship carrier;
    private Ship enemy;

    @Before
    public void setUp() throws Exception {
        game = new Game();
        Player kzinti = new Player();
        kzinti.setTeamName("Kzinti");
        Player fed = new Player();
        fed.setTeamName("Federation");

        carrier = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cvs.json")));
        carrier.setName("KHS Watchful");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        carrier.setOwner(kzinti);
        game.getShips().add(carrier);

        enemy = new Ship();
        enemy.init(com.sfb.samples.FederationShips.getFedCa());
        enemy.setName("USS Attacker");
        enemy.setLocation(new Location(10, 20));
        enemy.setFacing(13);
        enemy.setOwner(fed);
        enemy.setActiveFireControl(true);
        game.getShips().add(enemy);

        game.startTurn();
        // The impulse clock does not run until allocation is in, and J1.343's eight impulses
        // are measured on it — without this every wait below would spin at impulse zero.
        for (Ship ship : game.getShips()) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(ship.getLifeSupportCost());
            e.setFireControl(ship.getFireControlCost());
            e.setActivateShields(ship.getActiveShieldCost());
            e.setWarpMovement(0.0);
            game.submitAllocation(ship, e);
        }
        assertFalse("the turn should be under way", game.isAwaitingAllocation());
    }

    /** J4.461: the carrier sorts its own fighters out, and the EW fighter lands in a squadron. */
    @Test
    public void theCarrierOrganisesItsOwnSquadronsFromItsShipFile() {
        Fighter ewf = null;
        for (Squadron squadron : carrier.getShuttles().getSquadrons())
            for (Fighter f : squadron.getFighters())
                if (f.getEwPods() > 0)
                    ewf = f;

        assertNotNull("the CVS's EW fighter should be in a squadron", ewf);
        assertNotNull(ewf.getSquadron());
        assertTrue("and in one big enough to hold it (J4.463)",
                ewf.getSquadron().largeEnoughForEwFighter());
        assertEquals("with pods on both rails, as built", 2, ewf.getEwPods());
    }

    /**
     * The whole chain: launch the EW fighter and one of its squadron-mates, and the mate is
     * receiving four points of EW without anybody having asked for it.
     */
    @Test
    public void aLaunchedWingmanIsLentEwByItsLaunchedEwFighter() {
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        Fighter ewf = launchEwFighter();
        Fighter wingman = launchWingman();
        serveTheLendingDelay(ewf);

        assertNotNull("they must be squadron-mates for J4.921", wingman.getSquadron());
        assertTrue("and the launch must not have broken that",
                wingman.sharesSquadronWith(ewf));
        assertTrue("the wingman needs a lock-on to it (J4.921)", wingman.hasLockOn(ewf));

        assertSame("found without being designated", ewf, game.lentEwSourceOf(wingman));
        com.sfb.properties.EwLoan loan = game.lentEwTo(wingman);
        assertEquals("two pods, four points, split evenly (J4.961)", 2, loan.ecm());
        assertEquals(2, loan.eccm());

        com.sfb.properties.EwBreakdown ew = game.ewAgainst(enemy, wingman);
        assertEquals("J4.47's two", 2, ew.builtIn());
        assertEquals("and two lent (J4.93)", 2, ew.lent());
        assertEquals("four points is +2 on the die (D6.34 Step 5)",
                2, game.fireEcmShift(enemy, wingman));
    }

    /**
     * And it is worth something: a wingman flying with its EW fighter is harder to hit than one
     * flying without. This is the difference the EWF buys with its entire drone armament.
     */
    @Test
    public void flyingWithTheEwFighterIsBetterThanFlyingWithout() {
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        Fighter ewf = launchEwFighter();
        Fighter wingman = launchWingman();
        serveTheLendingDelay(ewf);
        int withEwf = game.ewAgainst(enemy, wingman).total();

        // Break formation: past J4.921's three hexes the loan lapses.
        wingman.setLocation(new Location(10, 18));
        int alone = game.ewAgainst(enemy, wingman).total();

        assertEquals(4, withEwf);
        assertEquals("its own two points and nothing else", 2, alone);
        assertTrue("so the EWF is worth two points of ECM to every mate in reach",
                withEwf > alone);
        assertNotNull(ewf);
    }

    /** Recovering the EW fighter takes the loan away with it — it is no longer on the map. */
    @Test
    public void landingTheEwFighterEndsTheSquadronsLoan() {
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        Fighter ewf = launchEwFighter();
        Fighter wingman = launchWingman();
        serveTheLendingDelay(ewf);
        assertFalse(game.lentEwTo(wingman).isNothing());

        game.getActiveShuttles().remove(ewf);
        ewf.setLocation(null);                      // back aboard, off the map

        assertTrue("nothing is lending any more", game.lentEwTo(wingman).isNothing());
    }

    /**
     * J1.343: "A shuttle cannot loan EW points ... for 1/4 turn (eight impulses) after its most
     * recent launch." The same wait its phasers serve under J1.342 — so an EW fighter scrambled
     * into a fight protects nobody until it has been out a quarter turn.
     */
    @Test
    public void aFreshlyLaunchedEwFighterLendsNothingForEightImpulses() {
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        Fighter ewf = launchEwFighter();
        Fighter wingman = launchWingman();

        assertTrue("in range and in squadron, but just launched",
                game.lentEwTo(wingman).isNothing());
        assertNull("so nothing counts as its source yet", game.lentEwSourceOf(wingman));
        assertEquals("its own two points and no more (J4.47)",
                2, game.ewAgainst(enemy, wingman).total());

        int toGo = ewf.impulsesUntilEwLending(game.getAbsoluteImpulse());
        assertTrue("there should be a wait to serve: " + toGo, toGo > 0);
        serveTheLendingDelay(ewf);   // waits, then settles lock-ons and J4.922 designations

        assertFalse("and now it lends", game.lentEwTo(wingman).isNothing());
        assertSame(ewf, game.lentEwSourceOf(wingman));
    }

    /**
     * The other half of J1.343, and the reason it is not simply a launch delay: "A shuttle can
     * RECEIVE EW lending immediately upon launch." A fighter joining a formation whose EW
     * fighter has been up a while is covered from the moment it clears the bay.
     */
    @Test
    public void aFreshlyLaunchedWingmanReceivesAtOnce() {
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        Fighter ewf = launchEwFighter();
        serveTheLendingDelay(ewf);

        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
        Fighter latecomer = launchWingman();
        latecomer.setLocation(ewf.getLocation());
        game.acquireFighterLockOns(latecomer);

        assertEquals("no wait to receive (J1.343)", 2, game.lentEwTo(latecomer).ecm());
        assertEquals(2, game.lentEwTo(latecomer).eccm());
        assertEquals("covered from the moment it clears the bay",
                4, game.ewAgainst(enemy, latecomer).total());
    }

    /**
     * The readout the owner asked for: "You should be able to click on any ship or fighter and
     * see how much ECM/ECCM it has." All of it public, and none of it re-derived in the client.
     */
    @Test
    public void theDtoCarriesTheWholeEwPicture() {
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
        Fighter ewf = launchEwFighter();
        Fighter wingman = launchWingman();
        serveTheLendingDelay(ewf);

        com.sfb.dto.GameStateDto.ShuttleDto lender = shuttleDto(ewf.getName());
        assertEquals("2 built-in + 2 of its own pods (J4.965)", Integer.valueOf(4),
                lender.ecmTotal);
        assertEquals(Integer.valueOf(4), lender.eccmTotal);
        assertEquals(Integer.valueOf(2), lender.ewPods);
        assertEquals(Integer.valueOf(2), lender.podEcm);
        assertEquals("nobody declared, so the even default (J4.961)",
                Boolean.FALSE, lender.podEwDeclared);
        assertEquals(Boolean.TRUE, lender.podsActive);
        assertEquals("its wait is served (J1.343)", Integer.valueOf(0),
                lender.ewLendDelayRemaining);
        assertNotNull("and it knows its squadron (J4.46)", lender.squadronName);

        com.sfb.dto.GameStateDto.ShuttleDto receiver = shuttleDto(wingman.getName());
        assertEquals("2 built-in + 2 lent", Integer.valueOf(4), receiver.ecmTotal);
        assertEquals(Integer.valueOf(2), receiver.lentEcm);
        assertEquals(ewf.getName(), receiver.lentEwSourceName);
        assertNull("it carries no pods of its own", receiver.ewPods);
        assertTrue("and the sources read plainly: " + receiver.ecmSources,
                receiver.ecmSources.contains("built-in")
                        && receiver.ecmSources.contains("lent from " + ewf.getName()));
    }

    /** A craft with no EW of its own says nothing, rather than claiming zero. */
    @Test
    public void anAdminShuttleCarriesNoEwFieldsAtAll() {
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
        com.sfb.objects.shuttles.Shuttle admin = null;
        for (ShuttleBay bay : carrier.getShuttles().getBays())
            for (Shuttle craft : bay.getInventory())
                if (admin == null && craft instanceof com.sfb.objects.shuttles.AdminShuttle a) {
                    admin = a;
                    Game.ActionResult r = game.launchShuttle(carrier, bay, a, 6, 13);
                    assertTrue(r.getMessage(), r.isSuccess());
                }
        assertNotNull("fixture needs an admin shuttle", admin);

        com.sfb.dto.GameStateDto.ShuttleDto dto = shuttleDto(admin.getName());
        assertNull("J4.47 reaches only fighters", dto.ecmTotal);
        assertNull(dto.eccmTotal);
        assertNull(dto.ecmSources);
        assertNull(dto.ewPods);
    }

    /**
     * J4.921's three hexes, watched from the outside. A formation that strings out stops being
     * covered, so the panel has to show the distance and say which clause bit — the loan going
     * quietly to nothing is the least useful thing it could do.
     */
    @Test
    public void aFighterOutOfRangeStillShowsWhereItsEwFighterIs() {
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
        Fighter ewf = launchEwFighter();
        Fighter wingman = launchWingman();
        serveTheLendingDelay(ewf);

        com.sfb.dto.GameStateDto.ShuttleDto close = shuttleDto(wingman.getName());
        assertEquals(ewf.getName(), close.ewLenderName);
        assertEquals("J4.921's limit, sent rather than hardcoded",
                Integer.valueOf(3), close.ewLendRangeLimit);
        assertTrue("in range: " + close.ewLenderRange,
                close.ewLenderRange <= close.ewLendRangeLimit);
        assertNull("nothing to explain while it is working", close.ewLendRefusal);

        // Break formation.
        wingman.setLocation(new Location(10, 18));

        com.sfb.dto.GameStateDto.ShuttleDto far = shuttleDto(wingman.getName());
        assertNull("no loan now", far.lentEcm);
        assertEquals("but the EW fighter is still findable", ewf.getName(), far.ewLenderName);
        assertTrue("and the distance is shown: " + far.ewLenderRange,
                far.ewLenderRange > far.ewLendRangeLimit);
        assertNotNull(far.ewLendRefusal);
        assertTrue("the reason should name the range and the rule: " + far.ewLendRefusal,
                far.ewLendRefusal.contains("hexes away")
                        && far.ewLendRefusal.contains("J4.921"));
        assertEquals("and its own two points remain", Integer.valueOf(2), far.ecmTotal);
    }

    /** The other clauses explain themselves too, so a player is never left guessing. */
    @Test
    public void theRefusalNamesWhicheverClauseBit() {
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
        Fighter ewf = launchEwFighter();
        Fighter wingman = launchWingman();

        String justLaunched = shuttleDto(wingman.getName()).ewLendRefusal;
        assertNotNull(justLaunched);
        assertTrue("J1.343's countdown: " + justLaunched,
                justLaunched.contains("J1.343") && justLaunched.contains("impulse"));

        serveTheLendingDelay(ewf);
        ewf.applyCripplingEffects();
        String crippled = shuttleDto(wingman.getName()).ewLendRefusal;
        assertTrue("J4.921's uncrippled clause: " + crippled, crippled.contains("crippled"));
    }

    private com.sfb.dto.GameStateDto.ShuttleDto shuttleDto(String name) {
        com.sfb.dto.GameStateDto state = new com.sfb.dto.GameStateDto(game, "Kzinti");
        for (com.sfb.dto.GameStateDto.MapObjectDto o : state.mapObjects)
            if (o instanceof com.sfb.dto.GameStateDto.ShuttleDto sd && name.equals(sd.name))
                return sd;
        throw new AssertionError(name + " not in the DTO");
    }

    /** Serve out J1.343's quarter turn for a craft that has just launched. */
    private void serveTheLendingDelay(com.sfb.objects.shuttles.Shuttle craft) {
        waitImpulses(craft.impulsesUntilEwLending(game.getAbsoluteImpulse()));
        for (com.sfb.objects.shuttles.Shuttle s : game.getActiveShuttles())
            if (s instanceof Fighter f)
                game.acquireFighterLockOns(f);
    }

    /** Advance until the absolute impulse has moved on by {@code n}. Bounded, never hangs. */
    private void waitImpulses(int n) {
        if (n <= 0)
            return;
        int target = game.getAbsoluteImpulse() + n;
        for (int guard = 0; guard < 2000; guard++) {
            if (game.getAbsoluteImpulse() >= target)
                return;
            game.advancePhase();
        }
        fail("impulse clock never reached " + target + " (stuck at "
                + game.getAbsoluteImpulse() + ", phase " + game.getCurrentPhase() + ")");
    }

    /** Launch the first craft of this type out of whichever bay holds one. */
    @SuppressWarnings("unchecked")
    private <T extends Fighter> T launch(Class<T> type) {
        for (ShuttleBay bay : carrier.getShuttles().getBays())
            for (Shuttle craft : bay.getInventory())
                if (type.isInstance(craft)) {
                    Game.ActionResult r = game.launchShuttle(carrier, bay, craft, 8, 13);
                    assertTrue("launching " + type.getSimpleName() + ": " + r.getMessage(),
                            r.isSuccess());
                    return (T) craft;
                }
        throw new AssertionError("no " + type.getSimpleName() + " aboard the CVS");
    }

    /**
     * Launch the carrier's EW fighter, whichever model that happens to be.
     * <p>
     * By capability rather than by class, because which fighter a CVS carries is decided by the
     * YEAR (J4.4): built from its own file it flies the AAS it entered service with in Y170, and
     * a Y173 scenario re-equips it with the HAAS. This test is about lending, not about models,
     * so it asks for the craft with pods and lets the era supply it. Naming Haas_E here is what
     * made these tests fail the moment the complement started following the year — and the data
     * they were written against had the wrong era's fighters aboard.
     */
    private Fighter launchEwFighter() {
        for (ShuttleBay bay : carrier.getShuttles().getBays())
            for (Shuttle craft : bay.getInventory())
                if (craft instanceof Fighter f && f.getEwPods() > 0) {
                    Game.ActionResult r = game.launchShuttle(carrier, bay, craft, 8, 13);
                    assertTrue("launching the EW fighter: " + r.getMessage(), r.isSuccess());
                    return f;
                }
        throw new AssertionError("no EW fighter aboard the CVS");
    }

    /** Launch an ordinary squadron-mate — a fighter with no pods, whatever model the era flies. */
    private Fighter launchWingman() {
        for (ShuttleBay bay : carrier.getShuttles().getBays())
            for (Shuttle craft : bay.getInventory())
                if (craft instanceof Fighter f && f.getEwPods() == 0) {
                    Game.ActionResult r = game.launchShuttle(carrier, bay, craft, 8, 13);
                    assertTrue("launching a wingman: " + r.getMessage(), r.isSuccess());
                    return f;
                }
        throw new AssertionError("no ordinary fighter aboard the CVS");
    }
}
