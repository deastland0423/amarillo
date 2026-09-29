package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.Squadron;
import com.sfb.objects.shuttles.Haas;
import com.sfb.objects.shuttles.Haas_E;
import com.sfb.properties.Location;

/**
 * Declaring how an EW fighter splits its pod points (J4.961).
 * <p>
 * "Each EWP can provide two points of either ECM or ECCM, or one of each. This is determined
 * secretly and simultaneously (B2.4) and announced in the Sensor Lock-On Phase of each turn."
 * <p>
 * Because a pod may split itself one and one, every distribution of twice the pod count is
 * reachable — 4/0, 3/1, 2/2, 1/3, 0/4 for two pods — so the only thing worth enforcing is the
 * total. The choice matters because J4.965 has the same points serve twice: the EWF uses them
 * itself AND lends them, so declaring all-ECM makes a whole squadron harder to hit at the cost
 * of every point of ECCM it might have shot with.
 */
public class PodEwDeclarationTest {

    private Game game;
    private Ship carrier;
    private Ship enemy;
    private Haas_E ewf;
    private Haas wingman;

    @Before
    public void setUp() {
        game = new Game();
        Player kzinti = new Player();
        kzinti.setTeamName("Kzinti");
        Player fed = new Player();
        fed.setTeamName("Federation");

        carrier = new Ship();
        carrier.init(com.sfb.samples.KzintiShips.getKzinBC());
        carrier.setName("KHS Longsword");
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

        ewf = new Haas_E();
        ewf.setName("HAAS-E");
        ewf.setOwner(kzinti);
        ewf.setLocation(new Location(10, 12));
        ewf.setFacing(13);

        wingman = new Haas();
        wingman.setName("HAAS-1");
        wingman.setOwner(kzinti);
        wingman.setLocation(new Location(10, 13));
        wingman.setFacing(13);

        game.getActiveShuttles().add(ewf);
        game.getActiveShuttles().add(wingman);
        Squadron squadron = new Squadron("Gold", carrier);
        assertNull(squadron.add(ewf));
        assertNull(squadron.add(wingman));

        game.startTurn();
        for (com.sfb.objects.shuttles.Shuttle craft : game.getActiveShuttles())
            if (craft instanceof com.sfb.objects.shuttles.Fighter f)
                game.acquireFighterLockOns(f);
    }

    // -------------------------------------------------------------------------
    // The declaration
    // -------------------------------------------------------------------------

    @Test
    public void anUndeclaredEwFighterFliesAnEvenSplit() {
        assertFalse("nobody has said anything yet", ewf.isPodEwDeclaredThisTurn());
        assertEquals(2, ewf.getPodEcm());
        assertEquals(2, ewf.getPodEccm());
    }

    /** Every split of four is legal, because each pod may divide itself one and one. */
    @Test
    public void anySplitOfTheTotalIsLegal() {
        assertNull(game.declarePodEw(ewf, 4, 0));
        assertEquals(4, ewf.getPodEcm());
        assertEquals(0, ewf.getPodEccm());
        assertTrue(ewf.isPodEwDeclaredThisTurn());
    }

    @Test
    public void theTotalMustBeSpentExactly() {
        String tooFew = game.declarePodEw(ewf, 1, 1);
        assertNotNull(tooFew);
        assertTrue("should say what it wanted: " + tooFew, tooFew.contains("4 points"));
        assertFalse("and nothing was declared", ewf.isPodEwDeclaredThisTurn());

        assertNotNull(game.declarePodEw(ewf, 3, 3));
        assertNotNull("nor may a share be negative", game.declarePodEw(ewf, -1, 5));
        assertEquals("still the default", 2, ewf.getPodEcm());
    }

    /** J4.961: once per turn. A second thought is not on offer. */
    @Test
    public void onlyOneDeclarationPerTurn() {
        assertNull(game.declarePodEw(ewf, 4, 0));

        String again = game.declarePodEw(ewf, 0, 4);
        assertNotNull(again);
        assertTrue("should cite the rule: " + again, again.contains("J4.961"));
        assertEquals("and the first declaration stands", 4, ewf.getPodEcm());
    }

    /** A fighter with no pods has nothing to declare. */
    @Test
    public void aFighterWithoutPodsCannotDeclare() {
        String no = game.declarePodEw(wingman, 2, 2);
        assertNotNull(no);
        assertTrue("should say why: " + no, no.contains("no EW pods"));
    }

    // -------------------------------------------------------------------------
    // The window
    // -------------------------------------------------------------------------

    /**
     * J4.961 puts the declaration in the Sensor Lock-On Phase at the head of a turn. Ours is
     * the allocation window that runs into it — the same moment a ship declares its own EW
     * (D6.310), which is what makes B2.4's "simultaneously" mean anything.
     */
    @Test
    public void theWindowClosesWhenTheTurnGetsUnderWay() {
        assertTrue("the window is open at turn start", game.isAwaitingAllocation());
        assertNull(game.declarePodEw(ewf, 3, 1));

        closeTheWindow();

        String late = game.declarePodEw(ewf, 0, 4);
        assertNotNull(late);
        assertTrue("should name the phase: " + late,
                late.contains("Sensor Lock-On Phase"));
    }

    /** J4.961: "each turn" — a declaration does not carry over into the next one. */
    @Test
    public void theDeclarationLapsesAtTheNextTurn() {
        assertNull(game.declarePodEw(ewf, 4, 0));
        assertEquals(4, ewf.getPodEcm());

        game.startTurn();

        assertFalse(ewf.isPodEwDeclaredThisTurn());
        assertEquals("back to the even default, not last turn's choice", 2, ewf.getPodEcm());
        assertEquals(2, ewf.getPodEccm());
    }

    /** And a fighter still in its bay has its declaration lapse too, in case it launches. */
    @Test
    public void aBayFightersDeclarationLapsesAsWell() {
        Haas_E inBay = null;
        for (com.sfb.systemgroups.ShuttleBay bay : carrier.getShuttles().getBays())
            for (com.sfb.objects.shuttles.Shuttle craft : bay.getInventory())
                if (inBay == null && craft instanceof Haas_E found)
                    inBay = found;
        if (inBay == null)
            return;      // this carrier keeps no EW fighter; nothing to prove here

        assertTrue(inBay.allocatePodEw(4, 0));
        assertTrue(inBay.isPodEwDeclaredThisTurn());

        game.startTurn();

        assertFalse("swept in the bay as well", inBay.isPodEwDeclaredThisTurn());
        assertEquals(2, inBay.getPodEcm());
    }

    // -------------------------------------------------------------------------
    // What the choice actually buys (J4.965)
    // -------------------------------------------------------------------------

    /**
     * One declaration, two jobs. J4.965: the pod points are "used by the EWF in addition to its
     * built-in EW" AND are the only points it may lend — so all-ECM protects the whole squadron
     * and leaves the EWF nothing extra to shoot through.
     */
    @Test
    public void allEcmProtectsTheSquadronAndCostsItsEccm() {
        assertNull(game.declarePodEw(ewf, 4, 0));

        assertEquals("the wingman gets all four (J4.93), capped at G24.2174's four",
                4, game.lentEwTo(wingman).ecm());
        assertEquals(0, game.lentEwTo(wingman).eccm());
        assertEquals("2 built-in + 4 lent, held to J4.91's six",
                6, game.ewAgainst(enemy, wingman).total());
        assertEquals("and the wingman's own ECCM is untouched by any of it",
                2, game.eccmOf(wingman));

        assertEquals("the EWF itself uses the same four", 6, ewf.totalOwnEcm());
        assertEquals("having declared none as ECCM, it has only J4.47's two",
                2, game.eccmOf(ewf));
    }

    /** The opposite declaration, to show the trade is real and symmetrical. */
    @Test
    public void allEccmArmsTheSquadronsFireControlInstead() {
        assertNull(game.declarePodEw(ewf, 0, 4));

        assertEquals(0, game.lentEwTo(wingman).ecm());
        assertEquals(4, game.lentEwTo(wingman).eccm());
        assertEquals("no help hiding: just its own two (J4.47)",
                2, game.ewAgainst(enemy, wingman).total());
        assertEquals("but six points of ECCM to shoot through (J4.91)",
                6, game.eccmOf(wingman));
    }

    // -------------------------------------------------------------------------
    // J4.967: the off switch
    // -------------------------------------------------------------------------

    @Test
    public void thePodsCanBeSwitchedOffAndTheLoanGoesWithThem() {
        assertNull(game.declarePodEw(ewf, 4, 0));
        assertFalse(game.lentEwTo(wingman).isNothing());

        assertNull(game.setFighterPodsActive(ewf, false));

        assertTrue("nothing lent (J4.965)", game.lentEwTo(wingman).isNothing());
        assertEquals("nor kept: the EWF is back to J4.47's two", 2, ewf.totalOwnEcm());
    }

    /** J4.967 allows it in ANY Lock-On Stage, so unlike the split it is not turn-start only. */
    @Test
    public void theOffSwitchWorksMidTurn() {
        closeTheWindow();
        assertNotNull("the split is closed by now", game.declarePodEw(ewf, 0, 4));

        assertNull("but the switch is not", game.setFighterPodsActive(ewf, false));
        assertTrue(game.lentEwTo(wingman).isNothing());
        assertNull("and back on again", game.setFighterPodsActive(ewf, true));
        assertFalse(game.lentEwTo(wingman).isNothing());
    }

    @Test
    public void aFighterWithoutPodsHasNoSwitch() {
        assertNotNull(game.setFighterPodsActive(wingman, false));
    }

    /** Get past the allocation window by giving every ship its keep-the-lights-on minimum. */
    private void closeTheWindow() {
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
}
