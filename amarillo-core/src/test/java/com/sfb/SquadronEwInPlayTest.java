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
import com.sfb.objects.shuttles.Haas;
import com.sfb.objects.shuttles.Haas_E;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.systemgroups.ShuttleBay;

/**
 * Squadron EW lending as a real game actually reaches it (J4.93).
 * <p>
 * Everything else about lending is tested against hand-built squadrons, which proves the rule
 * and not the wiring. This starts from a ship file — the Kzinti CVS, eleven HAAS and one
 * HAAS-E — lets the carrier organise its own squadrons at init (J4.461), launches two fighters
 * through the ordinary launch path, and checks the points arrive.
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
    }

    /** J4.461: the carrier sorts its own fighters out, and the EW fighter lands in a squadron. */
    @Test
    public void theCarrierOrganisesItsOwnSquadronsFromItsShipFile() {
        Haas_E ewf = null;
        for (Squadron squadron : carrier.getShuttles().getSquadrons())
            for (Fighter f : squadron.getFighters())
                if (f instanceof Haas_E found)
                    ewf = found;

        assertNotNull("the CVS's HAAS-E should be in a squadron", ewf);
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

        Haas_E ewf = launch(Haas_E.class);
        Haas wingman = launch(Haas.class);

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

        Haas_E ewf = launch(Haas_E.class);
        Haas wingman = launch(Haas.class);
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

        Haas_E ewf = launch(Haas_E.class);
        Haas wingman = launch(Haas.class);
        assertFalse(game.lentEwTo(wingman).isNothing());

        game.getActiveShuttles().remove(ewf);
        ewf.setLocation(null);                      // back aboard, off the map

        assertTrue("nothing is lending any more", game.lentEwTo(wingman).isNothing());
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
}
