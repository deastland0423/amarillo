package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.Terrain;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * D6.3146 — which ECM sources count when a D6.37 system (tractor, transporter, SFG) is
 * used on a FRIENDLY unit.
 * <p>
 * A friendly unit ignores GENERATED (D6.3141), BUILT-IN (D6.3142) and RECEIVED FROM
 * LENDING (D6.3144) ECM, but NOT NATURAL SOURCES (D6.3143) or OFFENSIVE ECM received from
 * an enemy scout (D6.3145). Asteroids are a natural source (P3.33), so tractoring your own
 * shuttle out of an asteroid field is harder than tractoring it out of open space — which
 * is the case that started this: the whole method used to return 0 for any non-ship target
 * and for any friendly ship, so it was free.
 */
public class FriendlyEwShiftTest {

    private Game game;
    private Ship klingonA;
    private Ship klingonB;
    private Player klingon;
    private Player federation;

    @Before
    public void setUp() {
        game = new Game();
        klingon = new Player();
        klingon.setTeamName("Klingon");
        federation = new Player();
        federation.setTeamName("Federation");

        klingonA = ship("IKS Fury", 10, 11, klingon, KlingonShips.getD7());
        klingonB = ship("IKS Barbarous", 10, 12, klingon, KlingonShips.getD7());
    }

    private Ship ship(String name, int col, int row, Player owner,
            java.util.Map<String, Object> spec) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(col, row));
        s.setFacing(1);
        s.setOwner(owner);
        s.setSpeedPreviousTurn(31);
        s.setSpeedTwoTurnsAgo(31);
        s.setActiveFireControl(true);
        game.getShips().add(s);
        return s;
    }

    /** A friendly shuttle sitting in an asteroid hex, one hex from the would-be tractoring ship. */
    private AdminShuttle friendlyShuttleInTheRocks() {
        game.addTerrain(new Terrain(TerrainType.ASTEROID, 10, 10));
        AdminShuttle sh = new AdminShuttle();
        sh.setName("IKS Fury-Shuttle-1");
        sh.setOwner(klingon);
        sh.setLocation(new Location(10, 10));
        sh.setFacing(1);
        game.getActiveShuttles().add(sh);
        return sh;
    }

    // ---------------------------------------------------------------- natural sources

    @Test
    public void tractoringYourOwnShuttleOutOfAnAsteroidHex_takesAnEcmShift() {
        AdminShuttle shuttle = friendlyShuttleInTheRocks();

        int shift = game.d637Shift(klingonA, shuttle);

        assertTrue("D6.3143/P3.33: asteroids are a natural source and a friendly unit does "
                + "NOT ignore them (D6.3146) — got shift " + shift, shift >= 1);
    }

    @Test
    public void inOpenSpace_thereIsNoShiftAtAll() {
        AdminShuttle sh = new AdminShuttle();
        sh.setName("IKS Fury-Shuttle-1");
        sh.setOwner(klingon);
        sh.setLocation(new Location(10, 10));
        game.getActiveShuttles().add(sh);

        assertEquals("nothing to burn through — no roll needed (D6.372)",
                0, game.d637Shift(klingonA, sh));
    }

    // ---------------------------------------------------------------- ignored sources

    @Test
    public void aFriendlyShipsOwnGeneratedEcmIsIgnored() {
        klingonB.setEcmAllocated(6);   // D6.3141 generated

        assertEquals("D6.3146: a friendly unit's generated ECM does not obstruct you",
                0, game.d637Shift(klingonA, klingonB));
    }

    @Test
    public void aFriendlyShipsLentEcmIsIgnored() {
        klingonB.addLentEw(6, 0);      // D6.3144 received from lending

        assertEquals("D6.3146: lent ECM is ignored between friendly units",
                0, game.d637Shift(klingonA, klingonB));
    }

    @Test
    public void aFriendlyFightersBuiltInEcmIsIgnored() {
        Stinger1 f = new Stinger1();   // two points built in (J4.47, D6.3142)
        f.setName("Alpha 1");
        f.setOwner(klingon);
        f.setLocation(new Location(10, 10));
        game.getActiveShuttles().add(f);

        assertEquals("D6.3146: built-in ECM is ignored between friendly units",
                0, game.d637Shift(klingonA, f));
    }

    // ---------------------------------------------------------------- offensive ECM

    @Test
    public void offensiveEwFromAnEnemyScoutCountsEvenAgainstAFriendlyTarget() {
        // D6.3145: the jamming is on the ACTOR (G24.219), degrading its own systems, so it
        // applies whatever it points the beam at.
        klingonA.addOffensiveEw(4);

        int shift = game.d637Shift(klingonA, klingonB);

        assertTrue("D6.3146 does NOT exempt offensive ECM — got " + shift, shift >= 1);
    }

    @Test
    public void theActorsEccmBurnsThroughIt() {
        klingonA.addOffensiveEw(4);
        klingonA.setEccmAllocated(4);  // D6.34 step 4: ECCM matching the ECM means no effect

        assertEquals("ECCM equal to the ECM leaves no shift (D6.34 step 4)",
                0, game.d637Shift(klingonA, klingonB));
    }

    // ---------------------------------------------------------------- the enemy side

    @Test
    public void anEnemyShipsOwnEcmStillCounts() {
        Ship fed = ship("USS Enterprise", 10, 10, federation, FederationShips.getFedCa());
        fed.setEcmAllocated(6);

        int shift = game.d637Shift(klingonA, fed);

        assertTrue("an enemy's generated ECM is emphatically not ignored — got " + shift,
                shift >= 1);
    }

    @Test
    public void anEnemyFightersBuiltInEcmCounts() {
        Stinger1 f = new Stinger1();   // J4.47: two points built in
        f.setName("Scratch One");
        f.setOwner(federation);
        f.setLocation(new Location(10, 10));
        game.getActiveShuttles().add(f);

        assertTrue("D6.3142/D6.393: an enemy fighter's built-in ECM counts",
                game.d637Shift(klingonA, f) >= 1);
    }

    // ---------------------------------------------------------------- the roll itself

    /**
     * D6.372, and the user's worked case: a shift of 1 means rolling 1d6+1 and failing only
     * on a total of 7. The lock-on is not lost — another beam could try.
     */
    @Test
    public void aShiftOfOneProducesARollThatFailsOnlyOnASix() {
        AdminShuttle shuttle = friendlyShuttleInTheRocks();
        assertEquals("one asteroid hex on the line = 1 point of natural ECM (P3.33)",
                1, game.d637Shift(klingonA, shuttle));

        boolean sawBlocked = false;
        boolean sawAllowed = false;
        for (int i = 0; i < 200 && !(sawBlocked && sawAllowed); i++) {
            Game.D637Result r = game.rollD637(klingonA, shuttle, "Tractor");
            assertNotNull("a non-zero shift must produce a roll", r);
            if (r.blocked) sawBlocked = true; else sawAllowed = true;
        }
        assertTrue("with +1 a six must sometimes block the attempt", sawBlocked);
        assertTrue("and anything lower must sometimes allow it", sawAllowed);
    }

    @Test
    public void anAttachedBeamNeedsNoFurtherRoll() {
        // G7.412 / D6.371: once attached, lock-on is automatic in both directions.
        AdminShuttle shuttle = friendlyShuttleInTheRocks();
        klingonA.getTractors().initForTurn(5, game.getAbsoluteImpulse());
        klingonA.getTractors().linkUnit(shuttle, game.getAbsoluteImpulse());

        assertEquals("an attached tractor is not re-acquired every impulse (G7.412)",
                0, game.d637Shift(klingonA, shuttle));
    }

    // ---------------------------------------------------------------- D6.392 lending cap

    /**
     * D6.3144/D6.392: six points of ECM from ALL outside lending sources combined, not six
     * from each. A Wild Weasel lends its six to the ship that launched it like a small
     * scout channel (J3.23), so a ship already receiving six from a scout gains nothing
     * further from a weasel — the code used to cap scout lending at six and then add a flat
     * six on top, giving twelve.
     */
    @Test
    public void scoutLendingAndAWeaselShareTheSixPointCeiling() {
        klingonB.addLentEw(6, 0);
        assertEquals("six from the scout", 6, klingonB.getLentEcmTotal());

        com.sfb.objects.shuttles.WildWeaselShuttle ww =
                new com.sfb.objects.shuttles.WildWeaselShuttle(klingonB);
        klingonB.setActiveWildWeasel(ww);

        assertEquals("a weasel on top of full scout lending is still six, not twelve (D6.392)",
                6, klingonB.getLentEcmTotal());
    }

    @Test
    public void aWeaselAloneLendsItsSix() {
        com.sfb.objects.shuttles.WildWeaselShuttle ww =
                new com.sfb.objects.shuttles.WildWeaselShuttle(klingonB);
        klingonB.setActiveWildWeasel(ww);

        assertEquals("J3.23: the weasel lends six to the ship that launched it",
                6, klingonB.getLentEcmTotal());
    }

    /**
     * And because it is lent ECM, it finally counts against direct fire — which never saw
     * it, making a weaselled ship harder to tractor than to shoot at.
     */
    @Test
    public void aWeaselsEcmNowCountsAgainstEnemyDirectFire() {
        Ship fed = ship("USS Enterprise", 10, 9, federation, FederationShips.getFedCa());
        int before = game.fireEcmShift(fed, klingonB);

        com.sfb.objects.shuttles.WildWeaselShuttle ww =
                new com.sfb.objects.shuttles.WildWeaselShuttle(klingonB);
        klingonB.setActiveWildWeasel(ww);

        assertTrue("a weasel must degrade incoming direct fire (D6.3144): " + before
                        + " -> " + game.fireEcmShift(fed, klingonB),
                game.fireEcmShift(fed, klingonB) > before);
    }
}
